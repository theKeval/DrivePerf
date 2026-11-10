# Trip files on Cloudflare R2: layout, presigned URLs, lifecycle (DRIVE-76)

**Status:** designed and partly built, **not live**: no R2 account, bucket or token exists, and nothing here deploys. Decided on 6 Nov 2026 ([decisions log](../product/decisions.md), [ADR-004](adr/ADR-004-backend-v1-and-migration-seam.md) "Trip-file storage"). This replaces the "Storage rules and layout" item of prompt A17: there are **no Firebase Storage rules**, because R2 is not a Firebase product.

**What is built:** the HTTP contract (`backend/openapi/v1.yaml`: `blobs:prepare`, `blobs:commit`, `blobs/{key}`), and in `backend/api` the use cases `PrepareBlobUpload` and `CommitBlobUpload`, the `BlobKey` parser, and `S3PresignedUrlSigner`, which implements AWS Signature V4 query presigning with no SDK and is tested against the worked example in the AWS documentation. **What is not:** the live adapters (the R2 `ObjectStore`, Secret Manager, the Firestore-backed `UploadLedger`) and the Ktor routes with the token middleware. They arrive with the first account feature in Phase 6 and need the R2 credentials Keval creates ([setup](../launch/firebase-backend-setup.md) §R2).

## 1. Object layout

```text
r2://driveperf-trips-prod/            (dev: driveperf-trips-dev)
  u/{accountId}/t/{tripId}/r1.v{formatVersion}.{sha256[0:16]}.pb.gz    R1 analysis file, content-addressed, immutable
  u/{accountId}/t/{tripId}/r0.v{formatVersion}.{sha256[0:16]}.pb.gz    reserved: the R0 sensor log never leaves the phone in v1
  x/{accountId}/{exportId}.zip                                         reserved for server-built exports (not used in v1, see §6)
```

- `accountId` is the opaque account ID (Firebase UID today, ADR-004 rule 2), `tripId` a UUIDv7 as lower-case text. Keys hold identifiers and a hash only: no names, dates or places.
- The same keys work on any S3-compatible store, so the VPS migration leaves the bucket where it is ([migration-seam.md](migration-seam.md)).
- Reprocessing a trip writes a **new** key and the old object is deleted after the trip document points at the new one.
- The server parses every key with one regex (`BlobKey`, the same pattern as the OpenAPI `BlobKey` schema) before it signs anything: a key with `..`, an extra `/`, upper-case hex or the wrong shape is refused, and the account segment must equal the caller's account.

**Object metadata** (signed into the upload URL, so the device cannot vary it): `Content-Type: application/x-protobuf`, `x-amz-meta-sha256` (full hex), `x-amz-meta-tripid`, `x-amz-meta-formatversion`. No `Content-Encoding: gzip`: the file is stored as opaque bytes and the client decompresses.

## 2. Flows

```text
Upload (device, background)                     API                                   R2
 1  POST /v1/blobs:prepare {key, sha256, size}  → checks (below), HEAD key ─────────────→
                                                ← exists? → {exists:true}: skip to step 5
                                                else mint presigned PUT (15 min), remember (key, sha256, size)
 2  ← {uploadUrl, headers, expiresAt}
 3  PUT uploadUrl with exactly those headers ────────────────────────────────────────────→ stored
 4  POST /v1/blobs:commit {key}                 → HEAD key; size and x-amz-meta-sha256 must match what step 1 announced
 5  POST /v1/sync/push {trip mutation with blobs:[{key,sha256}]}  → HEAD again; the trip document never references a missing object

Download (restore, read-only while LAPSED)
 1  GET /v1/blobs/{key}  → ownership + readUntil check → 302 to a presigned GET (15 min)
 2  device follows the redirect without the Authorization header, verifies sha256 of the bytes (the key carries the first 16 hex digits)
```

**Checks in `blobs:prepare`, in this order:** key shape and hash prefix match → content type is `application/x-protobuf` → size 1 byte to 16 MiB (an R1 file is about 0.25 MB per trip) → the account segment is the caller's → the account is not `deleting` → backup is `ACTIVE` and `writeUntil` is in the future. Each failure has a distinct code (`validation`, `too_large`, `forbidden`, `account_deleting`, `backup_not_allowed`), proven by `BlobUploadsTest`.

**Integrity, honestly stated.** R2 does not check the bytes against `sha256` unless a checksum header is signed, and we do not rely on that. The server verifies the **size** and the **recorded hash metadata** by HEAD; the **device verifies the hash on restore**; the key carries the first 16 hex digits, so a wrong object under a key is detected. A device that lies about its own file only corrupts its own backup.

## 3. Signing (`S3PresignedUrlSigner`)

- SigV4 query-string presign, service `s3`, region `auto`, **path-style** addressing: `https://<R2_ACCOUNT_ID>.r2.cloudflarestorage.com/<bucket>/<key>`. Payload is `UNSIGNED-PAYLOAD`. TTL 900 s (a 4G upload of 0.3 MB needs seconds; the limit mainly bounds replay).
- **Signed headers (PUT):** `host`, `content-type`, `content-length`, `x-amz-meta-sha256`, `x-amz-meta-tripid`, `x-amz-meta-formatversion`. The API returns the exact header values; the store rejects a request that sends different ones.
- Credentials are constructor arguments read from Secret Manager at start-up; `Credentials.toString()` masks the secret, and the signer never logs a URL.
- **Verify on the first live R2 test (cannot be done without credentials):** that R2 accepts `content-length` and the three `x-amz-meta-*` headers as signed headers on a presigned PUT. If it rejects `content-length`, drop it from `PrepareBlobUpload.uploadHeaders`; the commit step's HEAD still enforces the size. Record the result in this file.

## 4. Bucket and token settings (Keval, at the Blaze step)

| Setting | Value |
|---|---|
| Buckets | `driveperf-trips-dev` and `driveperf-trips-prod`, location hint **ENAM**, storage class Standard |
| Public access | **Off.** Do not enable the `r2.dev` URL and do not connect a custom domain |
| CORS | none: the apps upload with native HTTP clients, not browsers |
| Lifecycle rules | abort incomplete multipart uploads after 1 day; delete objects with prefix `x/` after 7 days |
| API token | one per environment, permission **Object Read & Write**, scoped to that one bucket, no IP filter (Cloud Run egress is not fixed). Account-level tokens are never used |
| Where the secret lives | Secret Manager (`R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`) in each Firebase/GCP project; the account ID and bucket name are plain Cloud Run environment variables. Nothing goes into GitHub except, if wanted, the non-secret variables |

## 5. Deletion

- **User deletes a trip or all trips:** `DELETE /v1/blobs/{key}` or `POST /v1/backup:wipe`; the purge job removes objects 7 days after the tombstone (the decisions log's safety window).
- **Account deletion:** step 2 of the cascade lists and deletes every object under `u/{accountId}/` and `x/{accountId}/` in batches of 1,000, then the verification sweep lists both prefixes again and must find nothing before the Auth user and the account record are removed. The trailing slash in the prefixes keeps account `ab` apart from `abc` (tested). See `DeleteAccountCascade`.
- **Orphans:** objects uploaded but never referenced are removed by the daily sweep after 24 hours.

## 6. Export bundle

Exports are generated **on the device** (data-model.md 4.12; PRD F-PRV-02/03): nothing is sent to a server. The `x/{accountId}/` prefix and its 7-day lifecycle rule are reserved in case a server-built export is ever added; v1 has no endpoint for it. Account deletion already covers the prefix.

## 7. Local and CI testing

No credentials are needed for anything built so far: the signer is pure computation, tested against the AWS-published vector (`S3PresignedUrlSignerTest`) and for header binding, and the use cases run against in-memory fakes. When the live `ObjectStore` adapter is written, run the same contract tests against an S3-compatible emulator in `backend.yml` (ADR-008 "local").
