# The migration seam: what must stay portable, and how we prove it (DRIVE-76)

[ADR-010](adr/ADR-010-firebase-now-vs-vps.md) decides *when* to leave Firebase and gives the runbook (M0 to M8); [ADR-004](adr/ADR-004-backend-v1-and-migration-seam.md) decides *how the code is shaped* so that leaving is a server-side project of about 2 to 4 weeks with no app release. This file is the working checklist for people writing backend code: what the seam is, which pieces are Firebase-specific, and which tests keep the seam honest.

## 1. The seam in one picture

```text
 apps (iOS, Android)                 backend/api (Ktor, one Docker image)                    Firebase today          VPS later
 ───────────────────                 ───────────────────────────────────                    ──────────────          ─────────
 Ktor client ── https://api.driveperf.com/v1 ──►  routes ─► application (use cases) ─► ports ◄─ adapters
 (shared:data:remote)                                                                         ├ SyncStore/UserStore      Firestore     Postgres
 Firebase Auth SDK ── ID token ──────────────────►  issuer-agnostic JWT check                 ├ ObjectStore + BlobSigner R2 (S3 API)   R2 (unchanged)
 App Check SDK ── attestation token ─────────────►  App Check JWKS check                      ├ IdentityAdmin            Firebase Auth Firebase Auth (kept)
                                                                                              ├ AccountStore, requests   Firestore     Postgres
                                                                                              └ SubscriberDirectory      RevenueCat    RevenueCat
```

Only the rightmost two columns change. The apps know one host name; trip files stay in R2 and keep their keys; the same Docker image runs on Cloud Run and on the VPS, configured with different adapters.

## 2. Ports as built (DRIVE-76) and the adapters they need

| Port (`backend/api/.../ports`) | Used by | Firebase-era adapter (Phase 6) | VPS-era adapter |
|---|---|---|---|
| `ObjectStore` (head, list, delete) | blobs, deletion cascade, orphan sweep | S3 API against R2 | the same R2 adapter |
| `BlobSigner` | `PrepareBlobUpload`, downloads | `S3PresignedUrlSigner` (built, tested) | the same class (any S3-compatible store) |
| `UploadLedger` | prepare to commit | Firestore `accounts/{a}/uploads/{key}` with a TTL field | Postgres table |
| `AccountStore` | every request (status, backup window), deletion | Firestore `accounts/{a}` | `dp.accounts` |
| `DeletionRequestStore` | `RequestAccountDeletion`, `RunDeletionJob` | Firestore `deletionRequests/*` with leases | `dp.deletion_requests`, `SELECT … FOR UPDATE SKIP LOCKED` |
| `UserDataStore` | deletion cascade | recursive delete of `users/{a}` with `BulkWriter` | `DELETE … WHERE account_id = ?` per table |
| `SubscriberDirectory`, `RideAlongPasses` | deletion cascade | RevenueCat REST, Firestore | unchanged, Postgres |
| `IdentityAdmin` | deletion cascade | Firebase Admin Auth | Firebase Admin Auth (kept) or the new IdP |
| `Clock` | everything | system | system |

Still to come with Phase 6 (same pattern): `SyncStore`, `EntitlementStore`, `EntitlementProvider` (RevenueCat), `Mailer`, `TokenVerifier`.

## 3. Rules that keep the seam intact (review checklist)

1. **No Firebase type above a port.** `com.google.firebase.*` and `com.google.cloud.firestore.*` appear only in `adapters/`. Use cases and routes see plain Kotlin types.
2. **IDs are opaque.** Account IDs are strings of at most 128 URL-safe characters and are never parsed; entity IDs are UUIDv7 made on the device; no Firestore auto-IDs. `AccountIds.isValid` is the only assumption.
3. **Timestamps are UTC epoch milliseconds in code and on the wire** (Firestore `Timestamp` and Postgres `timestamptz` are adapter details).
4. **Shapes are portable:** no `GeoPoint`, no `DocumentReference`, no array-contains in core flows; nested maps are flattened or opaque JSON.
5. **The change cursor is the per-account `rev`**, opaque to clients, assigned inside the same transaction as the write. Never a timestamp.
6. **Every server action is an HTTP endpoint or a scheduled HTTP job.** No Cloud Functions, no Firestore or Auth triggers, no `onDelete`: they do not port ([ADR-004](adr/ADR-004-backend-v1-and-migration-seam.md) "Not used"). The deletion cascade is `POST /v1/jobs/deletion` for this reason.
7. **Auth is issuer-agnostic:** ID tokens are checked against a configured list of trusted issuers and JWKS URLs; route handlers never mention Firebase. The account ID is the Firebase UID kept forever, mapped by `auth_identities (issuer, subject) → accountId`.
8. **Blob keys are content-addressed and hold no personal data;** presigned URLs are the only way a device touches the bucket; the API returns the URL **and** the exact headers.
9. **Rules deny every client access** to Firestore except the website waitlist; the API's service account is the only reader. Tests: `backend/firebase/tests/firestore.deny-all.test.ts`.
10. **Additive API changes only inside `/v1`;** a breaking change ships as `/v2` and `/v1` lives at least 6 months.

## 4. Proving it: the tests that gate a migration

| Test | Where | Proves |
|---|---|---|
| Rules unit tests (Emulator) | `backend/firebase/tests/` | clients cannot reach any document; waitlist is create-only and validated |
| Use-case tests against fakes | `backend/api/src/test` (built: cascade, blobs, signer) | business rules depend on ports only |
| **Store contract suite** (Phase 6) | `backend/api/src/test`, abstract class run against each store | `SyncStore`, `AccountStore` and friends behave identically on Firestore and Postgres: monotonic `rev`, no skipped late commit, `merge` commutative / associative / idempotent, tombstones absorb, cursor expiry after 90 days, idempotent `mutationId` replay |
| OpenAPI lint and DTO tests | `backend.yml`, `shared/api-contract` | the contract and the DTOs agree |
| Deletion cascade test on a populated store | Phase 6 integration | step order, resume after a crash, verification sweep, no cross-account effect (built against fakes) |
| Rehearsal on staging (M0 to M5) | before cut-over, ADR-010 preconditions | dual write, backfill, hash of sorted `(id, rev)` pairs, shadow reads, rollback |

ADR-010's preconditions apply unchanged: the Postgres and S3 adapters pass the same contract suite; a full rehearsal including rollback; off-box backups with a restore drill; monitoring and paging; privacy policy and PIA updated first; not in December and not within four weeks of a major app release.

## 5. What does not move

Firebase Auth, Analytics, Remote Config, App Check and FCM stay on Firebase after a VPS move (ADR-010); Sentry (EU) and RevenueCat are independent of the backend; trip files stay on R2 (so the ADR-010 trigger T2 and the blob copy disappear); the website can stay on Firebase Hosting as a hot standby. The Firestore waitlist is the one client-facing Firestore use and can move to `POST /v1/waitlist` when the API is live (optional).
