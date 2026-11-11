# SyncRepository, `BlobStore` and `AccountService`: the Firebase-backed v1 adapter contract (DRIVE-76)

**Audience:** whoever binds the shared core's ports on iOS and Android (DRIVE-62 `:shared:data:remote` and `:shared:di`, DRIVE-67, DRIVE-72). **Source of truth:** [ADR-004](adr/ADR-004-backend-v1-and-migration-seam.md), [data-model.md §0.3 and §4](data-model.md), `backend/openapi/v1.yaml`. If this file disagrees with them, they win.

## 0. The one-paragraph version

The "Firebase adapter" is **not a Firebase SDK adapter**. The apps never link the Firestore or Cloud Storage SDKs (there is no official Firebase KMP SDK, and a second offline cache would fight SQLDelight). The v1 adapter is **one Ktor client in `commonMain` that speaks the versioned `/v1` HTTP API** at `https://api.driveperf.com`; the server behind it happens to be Firebase today (Cloud Run, Firestore, R2) and may be Postgres later with **zero client change** ([migration-seam.md](migration-seam.md)). What stays SDK-direct on the device is only Auth (the ID token), App Check (the attestation token), Analytics, Remote Config, Sentry and RevenueCat.

## 1. Ports and where they bind

| Port (shared) | v1 adapter | Module | Native help |
|---|---|---|---|
| `AuthGateway` | Firebase Auth SDK; exposes `idToken(forceRefresh)` and `reauthenticate()` | iOS `DPPlatform`, Android `platform` | Apple / Google / email link flows |
| `AppIntegrity` | Firebase App Check; `suspend fun token(): String` | same | App Attest (needs the paid Apple account; debug provider until then), Play Integrity |
| `SyncRepository` | `HttpSyncRepository` over Ktor | `:shared:data:remote` | none |
| `BlobStore` (prepare, commit, redirect) + `BlobTransfer` (bytes) | Ktor for prepare and commit; native uploader for the bytes | shared + native | `URLSession` background upload; `WorkManager` with `OkHttp` |
| `AccountService` | `HttpAccountService` over Ktor | `:shared:data:remote` | none |
| `EntitlementRepository` | RevenueCat purchases-kmp plus `GET /v1/entitlement` | `:shared:data:remote` | store sheet |

Koin wires them in `:shared:di`. The domain and presentation layers never import `io.ktor.*` or `com.google.firebase.*` (detekt `ForbiddenImport` and Konsist enforce it). `:shared:data:remote` may depend on `:shared:api-contract` only for the DTOs.

## 2. HTTP rules every call follows

| Rule | Detail |
|---|---|
| Base URL | one constant, `https://api.driveperf.com`. Never a `*.run.app` or `*.googleapis.com` host |
| Headers | `Authorization: Bearer <ID token>`, `X-Firebase-AppCheck: <token>`, `DP-Contract: 1`, `DP-App: ios/1.0.0 (123)` |
| Token refresh | a `401 unauthenticated` triggers **one** `idToken(forceRefresh = true)` and one retry; `401 reauth_required` surfaces as `NeedsRecentSignIn` for the deletion screen; a second 401 signs the user out of sync (local data untouched) |
| App Check | attach the token on every call. A `403 app_check_failed` is not retried; report it as `IntegrityFailed` |
| Retry | exponential backoff with jitter (1 s, 2 s, 4 s … 5 min), honouring `Retry-After`, for network errors, `429` and `5xx` only. `4xx` other than 401 and 429 are never retried unless the error body says `retryable: true` |
| Idempotency | every mutation carries a UUIDv7 `mutationId`; re-sending is always safe (server keeps it 14 days; `merge` is idempotent anyway) |
| Unknown fields | ignored (`ignoreUnknownKeys = true`); additive server changes never break an old app |
| Time | all instants are UTC epoch milliseconds; the device distrusts its own clock if the `Date` header differs by more than 24 hours (data-model §4.4) |
| Free users | send **nothing** but the account record and consents. The adapter refuses to enqueue trip-resource mutations unless `EntitlementRepository` reports a writable backup window; a network test proves it (ADR-004 "Confirmation") |
| Large bodies | never through the API: trip files use presigned URLs (the Hosting proxy has a 60 s timeout) |

## 3. `SyncRepository`

```kotlin
interface SyncRepository {
    suspend fun changeMarker(): ChangeMarker
    suspend fun push(deviceId: DeviceId, backupEpoch: Int, mutations: List<Mutation>): PushOutcome
    suspend fun pull(resource: ResourceType, cursor: Cursor?, limit: Int = 500): ChangePage
}
```

(`backupEpoch` is added to the illustrative signature of data-model §0.3 because the push request carries it.)

| Method | HTTP | Contract |
|---|---|---|
| `changeMarker()` | `GET /v1/sync/changes` | One cheap read. Returns `backupEpoch` and the latest opaque cursor per resource. The sync engine pulls only resources whose marker differs from the stored cursor |
| `pull(resource, cursor, limit)` | `GET /v1/sync/{resource}?after=<cursor>&limit=` | Records in server order, tombstones included. `Cursor` is an **opaque string** (`v1:<rev>` today); never parse, compare or construct it. The caller merges the page and stores `next` in one SQLite transaction. `410 cursor_expired` (older than the 90-day tombstone window) becomes `CursorExpired`, which starts the full reconcile of data-model §4.5 |
| `push(deviceId, backupEpoch, mutations)` | `POST /v1/sync/push` | At most **100 mutations or 2 MB** per call; the caller chunks. One `MutationResult` per mutation, in order. `409` means the cloud copy was wiped or purged (`backupEpoch` stale): surface `EpochChanged`, re-read `GET /v1/entitlement`, and re-upload only if the user still has a writable backup |

**Result handling (drives the outbox, data-model §4.6):** `applied`, `noop`, `merged` delete the outbox row (adopt the returned `record` on `merged`); a retryable error backs off; `rejected` with `retryable: false` (`validation`, `backup_not_allowed`, `blob_missing`, `too_large`) quarantines the row and surfaces it in diagnostics. `backup_not_allowed` also moves the UI to the lapsed or off state.

**Ordering and consistency the adapter may rely on:** records within one pull are in commit order for the account; a cursor never skips a late commit (the server assigns a per-account `rev` under per-account serialisation); a tombstone absorbs every later edit (no resurrection); `merge` is commutative, associative and idempotent (per-field last-writer-wins on hybrid logical clocks). The adapter relies on none of this beyond what the contract states, so the Postgres store can replace Firestore unnoticed.

**What the adapter must not do:** open a Firestore or Storage SDK; use Firestore listeners or any push channel; use auto-generated IDs (IDs are UUIDv7 made on the device); store anything in a "Firebase-shaped" type (`GeoPoint`, `DocumentReference`); assume the account ID's format.

## 4. `BlobStore` (trip files)

```kotlin
interface BlobStore {
    suspend fun put(key: BlobKey, file: LocalPath, sha256: String, sizeBytes: Long): BlobRef
    suspend fun get(key: BlobKey, into: LocalPath): BlobRef
    suspend fun delete(key: BlobKey)
}
```

`put`: compute the key `u/{accountId}/t/{tripId}/r1.v{formatVersion}.{sha256[0:16]}.pb.gz` from the file's hash, call `POST /v1/blobs:prepare`; on `exists: true` return; otherwise hand `{uploadUrl, headers}` to the native `BlobTransfer` and send **exactly those headers** (they are signed; do not add `Authorization`, `Content-Encoding`, or anything else that is signed). Then `POST /v1/blobs:commit`. Presigned URLs live 15 minutes: if the OS defers a background upload past that, call `prepare` again. A trip mutation that references the file is queued **only after `put` returns**, so the trip document never points at a missing object.
`get`: `GET /v1/blobs/{key}` answers `302`; follow it **without** the `Authorization` header (the URL is its own credential), write to a temp file, verify the SHA-256 against the key's 16 hex digits and the stored `sha256`, then move into place. Allowed while the backup is readable, including the 30 days after Pro ends.
`delete`: `DELETE /v1/blobs/{key}`; always allowed, idempotent.
Presigned URLs are secrets for 15 minutes: never log them or attach them to Sentry events.

## 5. `AccountService`

| Call | HTTP | Notes |
|---|---|---|
| `requestDeletion(reason)` | `POST /v1/account:delete` | after `AuthGateway.reauthenticate()`; the ID token's `auth_time` must be within 5 minutes. `202` with `{requestId, completeBy}`; from then on every other call for the account answers `403 account_deleting`, so the app signs out of sync and shows the receipt |
| `deletionStatus()` | inferred, no endpoint | any call answers `403 account_deleting` until the cascade finishes (within 24 hours), then `401` because the Auth user is gone. If the UI needs more than that, add `GET /v1/account:deletion-status` to the contract (the one call allowed for a `deleting` account) |
| Wipe cloud copy | `POST /v1/backup:wipe` | "Delete cloud copy" and "Delete all trips"; the account stays |

## 6. Error mapping

| HTTP / code | Domain error | UI |
|---|---|---|
| network, timeout, 5xx | `Retryable` | silent retry; sync status "waiting" |
| 401 `unauthenticated` | refresh then `SignedOut` | prompt to sign in again |
| 401 `reauth_required` | `NeedsRecentSignIn` | re-authentication sheet |
| 403 `backup_not_allowed` | `BackupNotAllowed` | lapsed / upgrade state (S-ACC-04) |
| 403 `account_deleting` | `AccountDeleting` | deletion receipt |
| 403 `app_check_failed` | `IntegrityFailed` | "Update or reinstall from the store" in diagnostics, no retry |
| 409 stale epoch | `EpochChanged` | re-read entitlement |
| 410 `cursor_expired` | `CursorExpired` | full reconcile |
| 413 `too_large` | `Rejected` | quarantine |
| 429 | `Retryable` | back off per `Retry-After` |

## 7. Testing the adapter

1. **Fixture tests (no network):** Ktor `MockEngine` replays JSON from `backend/openapi/v1.yaml` examples; every error row above has a case. These live with `:shared:data:remote`.
2. **Contract suite:** one `SyncStore`-level suite (server side, Phase 6) runs against every store (Firestore, later Postgres); the client suite proves the adapter against the **same** OpenAPI examples. A change to `v1.yaml` fails the DTO tests in `shared/api-contract` until both agree.
3. **Emulator integration (Phase 6, `backend.yml`):** the Ktor server against the Firebase emulators, driven by the shared client on the JVM: push, pull, tombstone, wipe, epoch change, deletion, cursor expiry.
4. **No-leak test:** a Free user's session produces no trip-resource request.

Open item for DRIVE-62: `shared/api-contract` currently holds only `HealthResponse`; the DTOs for sync, blobs, entitlement and deletion are generated or hand-written from `backend/openapi/v1.yaml` in that module (this ticket did not touch `shared/`).
