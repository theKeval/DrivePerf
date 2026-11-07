# ADR-004: Backend v1 on Firebase, behind our own versioned HTTP API (the migration seam)

- **Status:** Proposed (for Gate 4)
- **Date:** 2026-11-06
- **Deciders:** Keval Langalia (PO); A10
- **Related:** [ADR-002](ADR-002-monorepo-modules-and-clean-architecture.md) (ports), [ADR-003](ADR-003-local-storage-trip-files-retention.md) (files, retention), [ADR-006](ADR-006-payments-and-entitlements.md), [ADR-010](ADR-010-firebase-now-vs-vps.md) (when and how to leave Firebase), [data-model.md](../data-model.md) (A11: collections, DDL, sync protocol)

## Context and problem statement

Binding (Keval, 6 Nov 2026): v1 runs on a **new Firebase project "DrivePerf"**, starting on **Spark** and upgrading to **Blaze** only when a feature needs it (Cloud Run, scheduled jobs). Auth, database, server-side APIs and hosting stay on Firebase for now. After launch, when user numbers justify it, the **whole server side moves to a self-managed VPS** (Postgres + S3-compatible storage + API) **without a client rewrite**.

The only server features in v1 are sign-in, Pro backup/restore, account deletion (with cascade), the 30-day post-Pro retention sweep, Pro Preview and Ride-along grants, RevenueCat webhooks and the website waitlist (PRD A-4). Free users upload no trip data.

The key question: **do the apps talk to Firebase SDKs directly (behind adapters), or to our own versioned HTTP API from day one?**

## Decision drivers

Migration cost to a VPS (client releases, old app versions in the field) · build cost before January 2027 · Spark vs Blaze timing · security (authorisation in one place) · one shared implementation instead of two native ones (there is no official Firebase KMP SDK, A3 §7.1) · local-first (the device DB is the source of truth anyway).

## Considered options

1. **SDK-direct:** Firestore and Cloud Storage SDKs on each device, behind `SyncRepository`/`BlobStore` adapters written natively twice; security rules carry authorisation; Cloud Functions triggers for cascades.
2. **Own versioned HTTP API from day one:** an OpenAPI-specified `/v1` API on **Cloud Run** (Blaze) in front of Firestore and Cloud Storage; apps call it from shared Kotlin (Ktor); trip files move through presigned URLs.
3. **GitLive Firebase KMP SDK** in `commonMain` (community, not Google's).
4. **Firebase Data Connect** (Postgres on Cloud SQL with generated client SDKs).

### Migration-cost analysis

| | 1. SDK-direct | 2. Own API | 3. GitLive | 4. Data Connect |
|---|---|---|---|---|
| Extra build cost now | none | **+1–2 weeks** (server skeleton, auth middleware, ~12 endpoints, emulator tests) | low | medium (GraphQL schema, connectors) |
| Plan needed | Spark for Firestore; Blaze still needed for Storage, cascade, webhooks | Blaze from the first account feature (needed anyway for Storage/cascade/webhooks) | as 1 | Blaze; Cloud SQL from ~US$9.37/mo after a 3-month trial |
| Client code | two native adapters (Swift + Kotlin) per port | **one** Ktor client in `commonMain` | one, but community-maintained | generated SDKs per platform |
| What migration touches | both apps' adapters, a store release, forced upgrade, a Firestore↔Postgres bridge kept alive for old versions for months; rules rewritten as server code | **server only**: Postgres + S3 adapters, data copy, DNS switch; no app release needed | as 1 | clients again (Data Connect SDKs are product-specific) |
| Estimated migration effort | 3–5 weeks + long tail of old versions | **1–2 weeks** + rehearsal | 3–5 weeks | 2–4 weeks |
| Authorisation | security rules (Firestore-specific language) | one place, Kotlin, unit-tested | rules | Data Connect auth directives |

## Decision outcome

**Chosen option: 2, our own versioned HTTP API from day one**, because the backend features that need a server already require Blaze, the extra build cost is small, sync logic is written once in shared Kotlin, and a later move to a VPS becomes a server-side change with **zero client changes**.

### What stays SDK-direct, and why

| Concern | SDK on device | Why it is not behind our API | Migration impact |
|---|---|---|---|
| Sign-in | Firebase Auth (Apple, Google, email link) behind `AuthGateway` | sign-in needs native SDK flows; only the **ID token** crosses to our API | optional: Auth may stay on Firebase indefinitely ([ADR-010](ADR-010-firebase-now-vs-vps.md)); a swap is one adapter per platform |
| Device integrity | Firebase App Check (App Attest, Play Integrity) behind `AppIntegrity` | attestation is a device API; our API verifies the token via JWKS | none; can keep working after migration |
| Purchases | RevenueCat SDK behind `EntitlementRepository` | purchases happen in the store sheet | none ([ADR-006](ADR-006-payments-and-entitlements.md)) |
| Analytics, crashes, flags | Firebase Analytics, Crashlytics, Remote Config behind `Analytics`, `CrashReporter`, `FeatureFlags` | device telemetry and config, no user records in our DB, free at any scale we expect | none required ([ADR-007](ADR-007-analytics-crash-flags-privacy.md)) |
| Push (later) | FCM behind `PushMessaging` | needs device tokens from the OS SDK | the API stores tokens; sending moves with the server |
| Website waitlist | Firestore Web SDK, create-only rules (Spark) | the site goes live in Phase 5 before Blaze/API exist | move to `POST /v1/waitlist` once the API is live (optional) |

Everything that reads or writes **user data** (account record, synced entities, trip files, deletion, entitlement mirror, grants) goes through the API.

### Firebase services by phase

| Phase | Plan | Services |
|---|---|---|
| Phase 5 (foundations, website) | **Spark** | Auth (configured; SIWA needs the paid Apple account), Firestore (waitlist only), Hosting (website), Analytics, Crashlytics, Remote Config, App Check (debug provider until the paid Apple account) |
| Phase 6 start (first account/backup feature) | **Blaze** with budget alerts at US$5, 25 and 50 | + **Cloud Run** service `api` (Kotlin/Ktor container), **Cloudflare R2** bucket for trip files (outside Firebase, see below), **Cloud Scheduler** (2 jobs, inside the 3 free: `daily` = 30-day purge, lapse reminders, RevenueCat reconcile, orphan-blob sweep; `deletion` = hourly account-deletion cascade), Secret Manager, Artifact Registry |
| Later, optional | Blaze | BigQuery export for dashboards (F-ANA-04), FCM sending |

**Not used:** Cloud Functions event triggers (Firestore/Auth `onDelete`, etc.) because they do not port to a VPS; every server action is an explicit API endpoint or a scheduled HTTP job. Phone Auth (billed per SMS). Password sign-in.

**Region:** Firestore (Native mode, Standard edition) and Cloud Run in **`us-central1`** (Hosting→Cloud Run rewrites are fastest there). R2 buckets use the **ENAM** (eastern North America) location hint. The Firestore location cannot be changed later. This matches PRD F-ACC-03 ("Firebase, US region"); the Law 25 PIA covers the transfer.

### API shape (contract: `backend/openapi/v1.yaml`, spec-first; A17 writes it, A11 supplies entity schemas)

- Base URL **`https://api.driveperf.com/v1`** — a second Firebase Hosting site connected to `api.driveperf.com` that rewrites `**` to the Cloud Run service. Clients never see `*.run.app` or `*.googleapis.com` host names in config. (Hosting proxies have a 60 s timeout; large bodies never pass through it because trip files use presigned URLs.)
- Headers: `Authorization: Bearer <ID token>`, `X-Firebase-AppCheck: <token>`, `DP-Contract: 1`, `DP-App: ios/1.0.0 (123)`; every push mutation carries a UUIDv7 `mutationId` as its idempotency key.
- Endpoints: the resource contract is **[data-model.md §4.2](../data-model.md)** (A11): `GET /v1/sync/changes` · `GET /v1/sync/{resource}?after=` · `POST /v1/sync/push` · `POST /v1/blobs:prepare` → presigned PUT · `POST /v1/blobs:commit` · `GET /v1/blobs/{key}` → presigned GET · `DELETE /v1/blobs/{key}` · `GET /v1/entitlement` · `POST /v1/entitlement/preview:claim` · `POST /v1/backup:wipe` · `POST /v1/account:delete`. This ADR adds the server-side routes: `POST /v1/account/deletion-requests` (web, email-verified, F-WEB-04) · Ride-along validation (F-SHR-05) · `POST /v1/webhooks/revenuecat` · `POST /v1/jobs/{daily|deletion}` (Cloud Scheduler OIDC only) · `POST /v1/waitlist` (later).
- Server verification is **issuer-agnostic**: ID tokens are checked as JWTs against a configured list of trusted issuers + JWKS URLs (today `https://securetoken.google.com/<projectId>`); App Check tokens against the App Check JWKS. No Firebase-only auth code in route handlers.
- Versioning: additive changes only within `/v1`; a breaking change ships `/v2` while `/v1` keeps working ≥ 6 months; Remote Config `min_supported_build` forces upgrades only as a last resort.
- Cloud Run settings: min instances 0, max 3 (cost and abuse ceiling), 512 MiB, startup CPU boost, request timeout 60 s. Firestore and Storage security rules **deny all client access** (except the waitlist create-only rule); the API uses a service account.

### Client seams (ports from ADR-002) and their v1 adapters

| Port | v1 adapter | Lives in |
|---|---|---|
| `AuthGateway` | Firebase Auth iOS / Android SDK | `DPPlatform` / Android `platform` |
| `AccountService`, `SyncRepository` | Ktor client → `/v1` | `:shared:data:remote` |
| `BlobStore` (prepare, commit) + `BlobTransfer` (bytes) | Ktor for prepare/commit; `URLSession` background upload / WorkManager for bytes to the presigned URL | shared + native |
| `EntitlementRepository` | RevenueCat purchases-kmp | `:shared:data:remote` |
| `AppIntegrity`, `Analytics`, `CrashReporter`, `FeatureFlags` | Firebase SDKs | native platform modules |

### Data portability rules (binding for A11 and A17)

1. **IDs:** every entity has a **UUIDv7** generated on the device (or server for server-only records). Firestore document ID = that UUID string. **Never use Firestore auto-IDs** or Firebase UIDs as entity keys.
2. **Accounts:** the account ID is the **Firebase UID, treated as an opaque string (≤ 128 chars) and kept forever**, including after migration (A11 P3). It is also the RevenueCat app user ID (PRD F-ACC-02 AC3). The server keeps an `auth_identities` mapping `(issuer, subject)` → `accountId`; after any identity-provider change, existing users map to their old `accountId` and new users get a server-minted opaque ID. Data is never re-keyed. Code never parses or assumes the format of an account ID.
3. **Timestamps:** UTC. Firestore `Timestamp` ↔ Postgres `timestamptz` ↔ API ISO-8601 with `Z`. Local wall time is derived from UTC + stored IANA zone. `createdAt`, `updatedAt` (server-assigned), `deletedAt` (tombstone) and `rev` (integer, incremented per write) on every synced record.
4. **Shapes:** no Firestore-only types: no `DocumentReference` (store IDs), no `GeoPoint` (store `lat`/`lon` doubles if any location is ever stored outside trip files), no reliance on array-contains queries for core flows. Nested maps are either flattened to columns or treated as opaque `jsonb`.
5. **Schema versioning:** each document carries `schemaVersion`; API DTOs are versioned by URL; trip files by header `schema_version` ([ADR-003](ADR-003-local-storage-trip-files-retention.md)). The server upgrades old documents lazily on read and in a backfill job.
6. **Change feed:** incremental sync uses a **per-account monotonic change counter** (`rev`, assigned by the API in the same transaction as the write). The cursor is the last `rev` the client has seen, so it works identically in Firestore and Postgres. A timestamp cursor (`updatedAt`, `id`) was rejected: in Postgres, a slow transaction can commit an earlier timestamp after a client has already read past it, and that write is skipped. Details are in [data-model.md](../data-model.md) §4. No Firestore listeners in clients.
7. **Firestore → Postgres mapping** (full DDL and the entity-by-entity table: [data-model.md §3.6–3.8](../data-model.md)):

| Firestore | Postgres |
|---|---|
| `users/{accountId}` + `accounts/{accountId}` (server-owned) | `dp.accounts(id text pk, …, entitlement, backup window)` + `dp.auth_identities(issuer, subject, account_id)` |
| `users/{accountId}/{resource}/{id}` (settings, devices, consents, vehicles, places, routes, trips) | one table per resource: `account_id text fk`, `id uuid pk`, envelope columns, index on the change cursor |
| arrays embedded in a document (e.g. trip events) | child table (`dp.trip_events`) when queried, `jsonb` otherwise |
| blob registry | `dp.blobs(key, account_id, trip_id, sha256, size_bytes, state, …)` |
| `waitlist/{id}`, deletion requests | same-named tables |

8. **Blob layout** (identical in R2 and any other S3-compatible store; [data-model.md §3.4, §3.7](../data-model.md)): `u/{accountId}/t/{tripId}/r1.v{formatVersion}.{sha256[0:16]}.pb.gz`, content-addressed and immutable. Keys contain opaque IDs and hashes only (no names, dates or places). Uploads are a single presigned `PUT` (an R1 file is ≈ 0.6 MB per driving hour), S3 presigned URLs (SigV4) work the same on R2, B2 or a self-hosted store; the API returns the URL **and** the exact headers to send. On a VPS use a managed S3-compatible bucket (or Garage/SeaweedFS); MinIO's community edition stopped publishing binaries in Dec 2025 (A11 [MINIO]).
9. **Auth portability:** sign-in is Apple, Google and email link only, so **no password hashes exist to migrate** (if a password provider is ever added, record the project's scrypt parameters from the console at that time). Apple and Google identities are re-linkable on any OIDC server by provider subject (`providerUserInfo.rawId` in `firebase auth:export`); email-link users re-verify by email. Details and the token-exchange cut-over are in [ADR-010](ADR-010-firebase-now-vs-vps.md).

### Cost model on Firebase (month 12 at a steady DAU; [Estimate])

Assumptions (aligned with [data-model.md](../data-model.md) §2.6 and §7): MAU = 2.5 × DAU; 40 % of MAU signed in; **25 % of DAU have backup on** (Pro, Preview or Ride-along); 2 trips per backup user per day of ≈ 25 min; only the **R1 analysis file is uploaded, ≈ 0.6 MB per driving hour → ≈ 0.25 MB per trip** (the full-rate R0 log never leaves the phone); ≈ 10 KB of Firestore data and ≈ 4 writes per trip; 8 API calls/day per backup user and 1 per other signed-in user; website 500 / 1,500 / 5,000 visits/day at 0.8 MB. Prices: us-central1 list prices (sources below).

| Line item (per month) | 1k DAU | 5k DAU | 20k DAU |
|---|---|---|---|
| Backup users / trips uploaded per month | 250 / 15,000 | 1,250 / 75,000 | 5,000 / 300,000 |
| Cloudflare R2 stock after 12 months (US$0.015/GB-mo, 10 GB, 1M writes and 10M reads a month free, no egress fees) | 45 GB → **$0.5** | 225 GB → **$3.2** | 900 GB → **$13.4** |
| Firestore (20K writes / 50K reads per day free, then $0.09 / $0.03 per 100K; storage 1 GiB free, then ≈ $0.15–0.18/GiB-mo) | 2K writes/day, 1.8 GB → **$0.2** | 10K/day, 9 GB → **$1.4** | 40K/day, 36 GB → **≈ $7** |
| Cloud Run (2M requests, 180K vCPU-s, 360K GiB-s free per month) | 65K req → $0 | 320K req → $0 | 1.3M req, ≈ 260K vCPU-s → **$2–10** |
| Hosting transfer, website + API proxy (360 MB/day free, then $0.15/GB) | $0.2 | $3.8 | $16.4 |
| Auth (50K MAU free), Analytics, Crashlytics, App Check, Remote Config (100K fetches/day free), Scheduler (3 jobs free) | $0 | $0 | $0 |
| Artifact Registry, Secret Manager, logging | $0.5 | $0.5 | $1 |
| **Total** | **≈ $2** | **≈ $9** | **≈ $40–50** |

Sensitivity: trip-file storage grows every month (retention is "until the user deletes"); if A12 needs R1 at 50 Hz (≈ 1.2 MB/h) the R2 line doubles. The website, not the app, is the largest line at 20k DAU. A11's backend-only figure (US$25–30/mo at 20k DAU) excludes website hosting and Cloud Run. RevenueCat (free under US$2,500 monthly tracked revenue, then 1 %) and the email provider are the same on any backend and excluded. Quota watch-points: Play Integrity Standard is 10,000 calls/day by default (request an increase before ~10k Android DAU); a Remote Config minimum fetch interval of 12 h keeps fetches under 100K/day.

### Trip-file storage: Cloudflare R2 (Gate 4, 6 Nov 2026)

Keval asked whether R2 is free and easy to migrate; it is the better fit, so trip files go to **Cloudflare R2 from day one** and everything else stays on Firebase.

- **Why:** 10 GB a month free, then US$0.015/GB-month; **no egress fees** (restores and any later move are free; leaving Cloud Storage would cost ≈ US$0.12/GB); S3-compatible, so the VPS migration (ADR-010) keeps trip files where they are and needs no blob copy.
- **How:** buckets `driveperf-trips-prod` and `driveperf-trips-dev`, ENAM location hint, public access off. The API holds a bucket-scoped R2 API token (Object Read & Write) in Secret Manager and mints **S3 SigV4 presigned** `PUT`/`GET` URLs (15 minutes) after its ownership, entitlement and size checks. Devices never hold Cloudflare credentials. Local and CI tests use an S3-compatible emulator; the contract suite runs against it.
- **Deletion:** R2 has no bucket soft delete we rely on, so the purge job removes an object 7 days after its tombstone (same safety window as before).
- **Costs:** Cloudflare asks for a payment method to enable R2, even inside the free tier. Blaze is still needed at Phase 6 for Cloud Run and Cloud Scheduler.
- **Privacy:** Cloudflare becomes a second processor for backup data; the privacy policy and the Law 25 PIA name it.

### Consequences

- Good: VPS migration is a server-side project ([ADR-010](ADR-010-firebase-now-vs-vps.md)); clients only ever know `api.driveperf.com`.
- Good: authorisation, the Pro check for uploads, the deletion cascade and the retention clock live in one tested Kotlin codebase that runs unchanged as a Docker container on Cloud Run today and on the VPS later.
- Good: no Firestore SDK on devices means no second offline cache competing with SQLDelight.
- Bad: Blaze is required at the start of Phase 6 (it would be for Storage anyway). A budget alert does not cap spend; Cloud Run `max-instances` and App Check enforcement are the real limits.
- Bad: JVM cold starts (1–3 s) on the first call after idle. Acceptable: uploads run in the background; sign-in setup and restore show progress.
- Bad: we own the sync protocol (A11) instead of Firestore's; we would own it anyway because the device DB is the source of truth.

### Confirmation

A17 delivers: `openapi/v1.yaml`; the Ktor service with emulator-backed integration tests; rules that deny client access (rules unit tests); a contract test suite for `SyncStore` that any future adapter must pass; a network test proving Free users send no trip data.

## More information

Sources (accessed 6 Nov 2026): Firebase pricing, Spark vs Blaze (https://firebase.google.com/pricing); Cloud Storage for Firebase changes (https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024); Firestore pricing (https://cloud.google.com/firestore/pricing); Cloud Storage pricing (https://cloud.google.com/storage/pricing; Standard us-central1 US$0.020/GB-mo confirmed via secondary 2026 sources); Cloud Run pricing (https://cloud.google.com/run/pricing); Hosting → Cloud Run rewrites and the 60 s timeout (https://firebase.google.com/docs/hosting/cloud-run); Firebase Auth import/export (https://firebase.google.com/docs/cli/auth); RevenueCat pricing (https://www.revenuecat.com/pricing); A3 §7.1 and §9.
