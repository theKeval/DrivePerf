# ADR-003: Local storage (SQLDelight), raw trip file format (chunked protobuf) and retention

- **Status:** Proposed (for Gate 4)
- **Date:** 2026-11-06
- **Deciders:** Keval Langalia (PO); A10
- **Related:** [data-model.md](../data-model.md) (A11: entity fields, DDL, `.proto` schema), [pipeline-and-scoring.md](../pipeline-and-scoring.md) (A12), decisions log 2026-11-06 "Data retention"

## Context and problem statement

DrivePerf is local-first: every Free feature works without an account and no trip data leaves the phone unless a Pro user turns on backup (F-ACC-01, F-ACC-03). The device holds structured data (trips, summaries, events, scores, routes, cars, settings, consents) and high-rate raw sensor data (≈ 10–20 MB/h uncompressed at 50 Hz IMU + 1 Hz GNSS, A3 §2; ≤ 5 MB/h stored target, PRD A-7). Recording happens while the phone is locked and the app may be killed at any time (F-CAP-06). The same files must be backed up, exported (F-PRV-02) and stay readable for years.

## Decision drivers

Stability on Kotlin/Native today · SQL-first schema that maps cleanly to Postgres · crash-safe append while locked · compact, versioned, language-neutral raw format · simple deletion · retention rules already decided by Keval.

## Considered options

- **Database:** (A) SQLDelight 2.x; (B) Room 3 KMP (`androidx.room3`); (C) a key-value/document store.
- **Raw trip format:** (A) chunked, length-delimited protobuf (Wire), gzip at finalisation; (B) NDJSON + gzip; (C) raw samples in SQLite tables; (D) Parquet/Arrow.

## Decision outcome

### Database: **SQLDelight 2.x** (A)

- Schema lives in `.sq` files under `shared/data/local`, migrations in `.sqm` with `verifyMigrations = true` in CI. Coroutines extensions for `Flow` queries.
- Drivers: Android driver; native driver on iOS. The DB file uses the platform default protection (iOS "until first user authentication"; Android credential-encrypted storage). "DB unavailable before first unlock" is a handled state (`DomainError.Storage.Unavailable`, A3 §7.3 #5).
- All primary keys are **UUIDv7** stored as 16-byte BLOB or canonical TEXT (A11 picks one, consistently), generated on device with `kotlin.uuid.Uuid.generateV7()` (stdlib, monotonic within a process). Timestamps are UTC epoch milliseconds (`INTEGER`) plus an IANA zone ID where local time matters. Units are SI and in the column name (`distance_m`, `duration_ms`, `speed_mps`).
- Why not Room 3: it became stable only on 1 Jul 2026 (3.0.0; 3.0.1 on 29 Jul) under a new `androidx.room3` package. It is a credible option, but SQLDelight is the A3 recommendation, has the longer Kotlin/Native track record, and its SQL-first `.sq` files are written in the same mental model as the Postgres DDL A11 must produce.

### Trip files: **chunked protobuf, gzip at finalisation** (A)

- Two tiers per trip in app-private storage ([data-model.md §2.4–2.6](../data-model.md) owns names, channels and quantisation): **R0 capture log** (device frame, 50 Hz IMU + every GNSS fix, ≈ 3–5 MB/h gzip; **never uploaded in v1**, used for reprocessing and capture diagnostics) and **R1 analysis file** (vehicle frame, 25 Hz + 1 Hz GNSS, ≈ 0.6 MB/h gzip; powers Replay/Analysis and is what Pro backup uploads). Both use the container below.
- Layout: a `TripFileHeader` message (magic `DPTR`, `schema_version`, trip ID, device/OS/app version, sensor rates, clock reference: monotonic origin + UTC wall-clock pair) followed by **length-delimited `Chunk` messages**, each ≈ 1 s of columnar samples (packed `float`/`sint64` arrays: IMU, GNSS, activity, quality flags). A truncated last chunk is detectable and dropped on recovery, so a crash loses at most the unflushed second.
- Codec: **Wire** (Square) generating Kotlin for `commonMain`; the `.proto` files live in `shared/data/local/src/commonMain/proto/trip/v1/` and are the published, documented portable schema (included in "Download all my data", F-PRV-02).
- Writes: buffered in memory ≤ 60 s (A3 §2), appended per chunk, `fsync` every ~5 s. iOS file protection **`.completeUnlessOpen`** for trip files (they are written while locked, A3 §2); Android: credential-encrypted storage, with a Direct-Boot-aware buffer only if A16 proves it needed.
- Finalisation: R0 is closed and gzip-compressed; R1 is written once processing ends, gzip-compressed (RFC 1952, readable by any language), hashed (SHA-256), and that exact byte stream is what backup uploads to a content-addressed key. Combined stored size stays under the ≤ 5 MB/h target (PRD A-7).
- Versioning: readers support **every** past `schema_version` forever (exports and restores of old backups). New fields are additive (protobuf rules); a breaking change is a new major schema with a converter.
- User-facing open formats are separate: GPX 1.1, CSV and JSON exports (F-PRV-02, F-PRV-03) are generated from the DB and the trip file.

### Retention (binding, from Keval's 6 Nov 2026 decision)

| Data | Where | Kept until | Mechanism |
|---|---|---|---|
| Trips, summaries, routes, cars, scores (all tiers) | device | the user deletes them | no automatic deletion; F-PRV-04 deletes trip/range/all within 30 s |
| R1 analysis files | device | the user deletes them | F-SET-04 lets the user remove detailed data while keeping summaries |
| R0 capture logs | device | the user deletes them | **No automatic pruning (Gate 4, 6 Nov 2026).** Removed with the trip or by F-SET-04; Settings shows the R0 size; under 100 MB free space EC-STO stops new R0 capture but keeps GNSS and summaries |
| Pro cloud backup (incl. Preview and Ride-along) | Firestore + Cloudflare R2 (trip files) via the API | the user deletes them or the account | device deletion propagates as a tombstone; cloud delete within 24 h |
| Cloud copy after Pro ends | cloud | **30 days**, then deleted | server stores `backupRetentionUntil = proEndedAt + 30 d`; reminders at 30, 7 and 1 days left; daily retention sweep deletes ([ADR-006](ADR-006-payments-and-entitlements.md)); re-subscribing cancels deletion; local data untouched |
| Free signed-in user | cloud | until account deletion | only the account record (user ID, auth identities, consents, entitlement mirror); **no trip data** |
| Deleted cloud objects | R2, delayed delete | purged ≤ 7 days after deletion | the purge job deletes an object 7 days after its tombstone (protects against our own bugs; R2 has no bucket soft delete we rely on); disclosed in the privacy policy |
| Privacy zones | device; Pro backup | the user deletes them | backed up with places since Gate 4 (6 Nov 2026); never in analytics |

### Consequences

- Good: raw data never bloats the database; deleting a trip is one row cascade plus one file.
- Good: the backup blob is byte-identical to the local R1 file, so restore is a download and an integrity check (SHA-256 recorded at finalisation).
- Bad: two persistence mechanisms (DB + files) need a consistency check at startup (orphan files, rows without files). A11 specifies it.
- Bad: protobuf is not human-readable; mitigated by CSV/GPX/JSON exports and the published `.proto`.

### Confirmation

Migration tests (`verifyMigrations`), a crash-truncation test for the R0 log, round-trip tests for every `schema_version`, and a network-traffic test proving a never-signed-in user sends no trip data (F-ACC-01 AC1).

## Pros and cons of the other options

- **NDJSON.gz:** readable, trivially portable; 3–5× larger before compression, slow to parse at 50 Hz × hours on device, float text round-trip issues.
- **Raw samples in SQLite:** one store; millions of rows per week, slow deletes and VACUUM, larger backups.
- **Parquet/Arrow:** great for analytics; no mature Kotlin/Native writer, poor for crash-safe append.
- **Key-value/document stores:** no relational queries for history, routes and stats.

## More information

Sources (accessed 6 Nov 2026): Room 3 release notes (https://developer.android.com/jetpack/androidx/releases/room3) and Android Developers Blog "Room 3.0" (Mar 2026); SQLDelight multiplatform docs (A3 [K4]); Kotlin `Uuid.generateV7` API (https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.uuid/-uuid/-companion/generate-v7.html); Apple file protection guidance (A3 [A25]).
