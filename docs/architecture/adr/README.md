# ADRs

Architecture decision records in [MADR](https://adr.github.io/madr/) format, named `ADR-NNN-short-title.md`.

Start with the [architecture overview](../overview.md) (C4 context and container diagrams, module dependencies, open questions).

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](ADR-001-cross-platform-approach.md) | Kotlin Multiplatform shared core with native SwiftUI and Compose (M3 Expressive) UI; iOS 26+, Android 10+ | Proposed (Gate 4) |
| [ADR-002](ADR-002-monorepo-modules-and-clean-architecture.md) | One monorepo, one root Gradle build; clean-architecture modules with CI-enforced dependency rules; shared UDF presenters; Koin; `Outcome`/`DomainError`; Kotlin 2.4.20 + SKIE; testing pyramid | Proposed (Gate 4) |
| [ADR-003](ADR-003-local-storage-trip-files-retention.md) | SQLDelight on device; trip files as chunked protobuf (R0 capture log on device, R1 analysis file backed up, gzip at finalisation); retention as decided on 6 Nov 2026 | Proposed (Gate 4) |
| [ADR-004](ADR-004-backend-v1-and-migration-seam.md) | Firebase backend behind our own OpenAPI `/v1` API on Cloud Run; SDK-direct only for Auth, App Check, Analytics, Crashlytics, Remote Config, FCM and RevenueCat; portability rules; cost model | Proposed (Gate 4) |
| [ADR-005](ADR-005-maps.md) | MapKit on iOS, Google Maps SDK (Maps Compose) on Android; no paid map APIs | Proposed (Gate 4) |
| [ADR-006](ADR-006-payments-and-entitlements.md) | RevenueCat with one `pro` entitlement; app user ID = account ID (Firebase UID, kept forever); local Preview with a server-granted backup window; Ride-along as a promotional entitlement; webhook mirror drives backup and the 30-day retention clock | Proposed (Gate 4) |
| [ADR-007](ADR-007-analytics-crash-flags-privacy.md) | Firebase Analytics, Crashlytics and Remote Config, opt-in and ad-ID-free; typed event dictionary enforced in CI; cookie-less website analytics | Proposed (Gate 4) |
| [ADR-008](ADR-008-ci-cd-and-environments.md) | GitHub Actions + Fastlane (free while the repo is public); Workload Identity Federation; `driveperf-dev` and `driveperf-prod` projects | Proposed (Gate 4) |
| [ADR-009](ADR-009-web-stack.md) | Astro static site on Firebase Hosting at `driveperf.com`; Namecheap is registrar/DNS only | Proposed (Gate 4) |
| [ADR-010](ADR-010-firebase-now-vs-vps.md) | Stay on Firebase now; do not buy a VPS yet; migrate the server side when measurable triggers fire, with a zero-downtime runbook | Proposed (Gate 4) |

To change a decision, add a new ADR that supersedes the old one and update both statuses.
