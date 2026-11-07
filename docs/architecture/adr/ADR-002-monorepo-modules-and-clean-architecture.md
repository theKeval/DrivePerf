# ADR-002: Monorepo layout, build tooling and clean-architecture module rules

- **Status:** Proposed (for Gate 4)
- **Date:** 2026-11-06
- **Deciders:** Keval Langalia (PO); A10
- **Related:** [ADR-001](ADR-001-cross-platform-approach.md), [ADR-003](ADR-003-local-storage-trip-files-retention.md), [ADR-004](ADR-004-backend-v1-and-migration-seam.md), [overview](../overview.md), AGENTS.md "Clean architecture"

## Context and problem statement

One repository must hold the KMP core, two native apps, the backend, the website, prototypes and docs, and be worked on by AI agents that follow AGENTS.md. Keval asked for **clean architecture across apps and backend** that scales and survives a backend migration. We need concrete module boundaries, allowed dependency directions and how they are enforced, the presentation pattern, DI, error types, Swift interop tooling and a testing pyramid.

## Decision drivers

Clean dependency direction (domain knows nothing about Firebase, SQLite, HTTP or UI) · one build for shared + Android + server · fast agent orientation · Swift ergonomics · enforceable in CI, not by convention.

## Considered options

- **Repo:** (A) one monorepo with a single root Gradle build, Xcode project with local SPM packages; (B) separate repos per app/backend; (C) monorepo with independent Gradle builds per folder.
- **iOS framework integration:** (A) Gradle direct integration (`embedAndSignAppleFrameworkForXcode`); (B) prebuilt XCFramework via SPM `binaryTarget` (KMMBridge); (C) CocoaPods.
- **Swift interop:** (A) Objective-C export + SKIE; (B) Swift export (Alpha in 2.4); (C) KMP-NativeCoroutines.
- **Local DB:** see [ADR-003](ADR-003-local-storage-trip-files-retention.md).
- **Presentation:** (A) shared Kotlin presenters (UDF) wrapped by thin native view models; (B) fully native view models per platform; (C) shared UI (rejected in ADR-001).
- **DI:** (A) Koin in shared + Android; (B) Hilt (Android-only) + manual DI in shared; (C) manual constructor DI only.

## Decision outcome

**Chosen:** monorepo **A** with one root Gradle build; iOS direct integration **A** with a static framework; **Objective-C export + SKIE** pinned with Kotlin; **shared presenters (A)**; **Koin (A)** with constructor injection and no Koin API visible to Swift.

### 1. Repository layout

```
/                              settings.gradle.kts (includes shared, apps/android, backend/api), gradle/libs.versions.toml
├── shared/                    KMP (commonMain, androidMain, iosMain; jvm for api-contract)
│   ├── foundation/            Outcome, DomainError, Ids (UUIDv7), units, Clock, Logger port
│   ├── domain/                entities, value objects, ports (interfaces), use cases
│   ├── pipeline/              trip state machine, DSP, detectors, scoring, route matching (A12) — pure
│   ├── data/local/            SQLDelight DB, trip-file codec (Wire protobuf), local repositories
│   ├── data/remote/           Ktor API client, DTO↔domain mappers, RevenueCat (purchases-kmp) adapter
│   ├── data/sync/             upload queue, change-feed cursor, backoff, conflict application
│   ├── api-contract/          @Serializable DTOs for /v1 (common + jvm; shared with backend/api)
│   ├── presentation/          presenters (State/Intent/Effect), formatters
│   ├── di/                    Koin modules + `DrivePerfCore` facade (startup, platform bindings)
│   ├── ios-export/            umbrella static framework "DrivePerfShared" + SKIE
│   └── testing/               fakes for every port, fixtures, TestClock (test-only)
├── apps/
│   ├── android/  app/ · designsystem/ · feature-<epic>/ · platform/ (sensors, FGS, WorkManager, Firebase adapters, Live Update, Glance)
│   └── ios/      DrivePerf.xcodeproj (App, Widgets+LiveActivity+Controls extension)
│                 Packages/ DPDesignSystem · DPFeatures · DPPlatform · DPSharedBridge
├── backend/
│   ├── api/                   Kotlin/Ktor server (Docker image), its own clean layers (§6)
│   ├── openapi/v1.yaml        the HTTP contract (spec-first, ADR-004)
│   ├── firebase/              firebase.json, firestore.rules, storage.rules, indexes, emulator config
│   └── ops/                   (later) VPS compose files and runbooks (ADR-010)
├── web/                       Astro site (ADR-009)
├── prototypes/  docs/  legacy/android-prototype/
```

Feature modules on Android are per PRD epic (`feature-capture`, `feature-drives`, `feature-replay`, `feature-routes`, `feature-insights`, `feature-garage`, `feature-account`, `feature-privacy`, `feature-paywall`, `feature-settings`, `feature-onboarding`). On iOS, `DPFeatures` holds one folder per epic; split into more SPM targets only when build time demands it.

### 2. Layers and allowed dependencies

The arrow means "may depend on". Anything not listed is forbidden.

| Module | May depend on | Must never import |
|---|---|---|
| `:shared:foundation` | Kotlin stdlib, kotlinx-coroutines-core, kotlinx-datetime | everything else |
| `:shared:domain` | foundation | SQLDelight, Ktor, Wire, kotlinx.serialization, Firebase, RevenueCat, `platform.*`, `android.*` |
| `:shared:pipeline` | foundation, domain (entities only) | same as domain; no I/O, no clocks except injected `Clock` |
| `:shared:presentation` | foundation, domain | data/*, api-contract, any SDK |
| `:shared:api-contract` | kotlinx.serialization | domain (mapping happens in data/remote) |
| `:shared:data:local` | foundation, domain, SQLDelight, Wire, Okio | remote, presentation |
| `:shared:data:remote` | foundation, domain, api-contract, Ktor, purchases-kmp | local, presentation |
| `:shared:data:sync` | foundation, domain, data:local, data:remote | presentation |
| `:shared:di` | all shared modules, Koin | — (composition root only) |
| Android `feature-*` | presentation, domain, foundation, designsystem | data/*, platform, Firebase |
| Android `platform` | domain, foundation (implements ports) | presentation, feature-* |
| Android `app` | everything (composition root) | — |
| iOS `DPFeatures` | DPDesignSystem, DPSharedBridge (presentation + domain types) | DPPlatform, Firebase, RevenueCat |
| iOS `DPPlatform` | DPSharedBridge (domain ports), Apple frameworks, Firebase iOS SDK | DPFeatures |
| iOS widget/Live Activity extension | DPDesignSystem, App Group snapshot types | the Kotlin framework (extension memory limits) |
| `backend/api` layers | see §6 | — |

**Enforcement (CI fails on violation):**
1. **Compile-time:** Gradle module boundaries (`implementation`, not `api`, except `foundation` and `domain` re-exported by `ios-export`). SPM targets can only `import` declared dependencies.
2. **Graph assertion:** the `com.jraska.module.graph.assertion` Gradle plugin with an explicit allow-list mirroring the table above (`./gradlew assertModuleGraph` in every PR).
3. **Import rules:** Konsist tests in `shared/testing` (for example "classes in `..domain..` do not import `com.google.firebase`, `io.ktor`, `app.cash.sqldelight`"; "presenters do not reference `..data..`") plus detekt `ForbiddenImport`.
4. **Review:** @seniorDev checks new edges against this ADR; a new edge requires an ADR amendment.

### 3. Ports (interfaces in `:shared:domain`)

Backend-facing (the seams the apps code against; adapters in [ADR-004](ADR-004-backend-v1-and-migration-seam.md)):
`AuthGateway` · `AccountService` · `SyncRepository` · `BlobStore` · `EntitlementRepository` · `FeatureFlags` (Remote Config) · `Analytics` · `CrashReporter` · `AppIntegrity` (App Check tokens) · `PushMessaging` (reserved; v1 uses local notifications only).

Device-facing: `LocationSource` · `MotionSource` · `ActivitySource` · `TripFileStore` · `TripRepository` / `RouteRepository` / `CarRepository` / `SettingsRepository` · `BackgroundScheduler` · `BlobTransfer` (native background upload/download) · `NotificationScheduler` · `LiveSurface` (Live Activity / Live Update) · `Geocoder` · `SecureStore` · `Connectivity` · `PowerState` · `Clock` · `Logger`.

Rules: ports speak domain types only (no DTOs, no SDK types). **Ports implemented in Swift** must not use `suspend` or `Flow` in their signature (SKIE helps Swift *call* Kotlin coroutines, not *implement* them). Swift implements small callback-style `*Bridge` protocols defined in `iosMain`; Kotlin adapters wrap them into suspend functions with `suspendCancellableCoroutine` and propagate cancellation.

### 4. Presentation: UDF with shared presenters

- `Presenter<S, I, E>` in `:shared:presentation`: `val state: StateFlow<S>`, `fun dispatch(intent: I)`, `val effects: Flow<E>` (one-shot navigation, toasts). State is an immutable data class. Presenters call use cases only.
- **Android:** a `ViewModel` per screen owns its presenter on `viewModelScope`; composables are stateless and collect with `collectAsStateWithLifecycle()`.
- **iOS:** an `@Observable @MainActor` view model owns the presenter, observes `state` with SKIE's `AsyncSequence` in a `.task {}` tied to the view lifetime, and cancels the presenter scope on deinit.
- Shared presenters are required for data-heavy screens (Drives list, Trip detail, Replay/Analysis timeline, Routes, Insights, Garage, Privacy Centre, Backup status, Paywall eligibility). Platform-only flows (permission onboarding, Live Activity/Update, widgets, system settings deep-links) use native view models calling use cases directly.
- Navigation, strings, accessibility and motion are native. Formatting of numbers and units lives in `presentation` so both platforms show the same values.

### 5. DI and startup

- **Koin 4.x** modules in `:shared:di` and Android. Constructor injection everywhere; no `KoinComponent`/`inject()` inside domain, pipeline or presentation classes.
- `DrivePerfCore.start(platform: PlatformBindings)` is the only entry point. `PlatformBindings` is a Kotlin interface whose members are the natively implemented ports (Swift supplies its adapters at app launch; Android supplies them from `platform`). Swift never sees Koin types.
- **Background relaunch order (iOS):** recreate Core Location sessions in Swift first, buffer samples, then start `DrivePerfCore` (A3 §7.3 #4).

### 6. Backend API module layers (`backend/api`)

`routes` (Ktor, DTO validation, auth) → `application` (use cases: SyncChanges, IssueUploadTicket, DeleteAccount, GrantPreview, ApplyRevenueCatEvent, RetentionSweep) → `ports` (`UserStore`, `SyncStore`, `BlobSigner`, `EntitlementStore`, `IdentityVerifier`, `IntegrityVerifier`, `EmailSender`, `Clock`) ← `adapters` (Firestore, GCS signer, JWT/JWKS verifiers now; Postgres and S3 signer at migration). Same Gradle graph-assertion rules apply. The server depends on `:shared:api-contract` (jvm) so DTOs are shared, and on nothing else in `shared/`.

### 7. Errors and results

- `sealed interface Outcome<out T> { data class Success<T>(val value: T); data class Failure(val error: DomainError) }` in `foundation`, with `map`, `flatMap`, `getOrElse`.
- `sealed interface DomainError` with families: `Network` (Offline, Timeout, Server(status)), `Auth` (NotSignedIn, ReauthRequired, Revoked), `Entitlement` (NotEntitled), `Storage` (DiskFull, Unavailable — before first unlock, Corrupt), `Sync` (Conflict, VersionTooOld), `Capture` (PermissionDenied(kind), SensorUnavailable), `Validation` (field, reason), `Unexpected` (correlationId). No free text that could carry location.
- Exceptions are for programmer errors only. Adapters catch SDK/IO exceptions **at the boundary** and map them; `CancellationException` is always rethrown (helper `catchingCancellable {}`; a detekt rule forbids bare `catch (e: Exception)` in shared code).
- Swift handles errors with SKIE's exhaustive `onEnum(of:)`.

### 8. Swift interop and build tooling

- **Kotlin 2.4.20 + SKIE 0.10.15** (SKIE's release notes add 2.4.20 support, Sep 2026). Kotlin upgrades wait for a SKIE release that supports them (Renovate rule). Fallback: KMP-NativeCoroutines. Re-evaluate Swift export when it reaches Beta (Kotlin roadmap).
- `:shared:ios-export` builds a **static** framework `DrivePerfShared`, exporting only `foundation`, `domain`, `presentation` and the `di` facade. Data modules stay internal.
- Xcode integration: a Run Script build phase calls `./gradlew :shared:ios-export:embedAndSignAppleFrameworkForXcode`. No CocoaPods. Swift packages are local SPM packages; third-party iOS SDKs (Firebase, RevenueCat via purchases-kmp's own linking) come through SPM.
- Gradle: version catalog, configuration cache and build cache on; JDK 21 toolchain.

### 9. Testing pyramid

| Level | Where | Tools | Gate |
|---|---|---|---|
| Unit (most) | `domain`, `pipeline`, `presentation`, `foundation` `commonTest` | kotlin-test, Turbine, `shared/testing` fakes, synthetic signal fixtures (A12) | ≥ 90 % line coverage on domain + pipeline (PRD §7.2) |
| Golden / fixture | `pipeline` | recorded-drive fixtures (privacy-trimmed, see ADR-008) → golden scores | any score change needs a versioned algorithm bump |
| Integration | `data/*`, `backend/api` | SQLDelight JVM driver + iOS simulator native driver; migration verification; Ktor MockEngine; server with Firestore/Auth emulators | green on every PR |
| Contract | `api-contract` ↔ `openapi/v1.yaml` ↔ server | OpenAPI validation of JSON fixtures and of server responses; **repository contract suite** run against every `SyncStore` adapter (Firestore now, Postgres at migration) | green on every PR |
| UI | Android features, iOS features | Compose UI tests + Roborazzi screenshots; XCTest/Swift Testing + swift-snapshot-testing for key screens | key screens per epic |
| End-to-end (few) | both apps | Maestro smoke flows (onboarding, manual trip, Story, paywall) on emulator/simulator | nightly + before release |
| Field | devices | A13a Spike 1, A19 field-test protocol | release gates PRD §2.4 |

### Consequences

- Good: the domain and pipeline compile without any SDK, so moving from Firebase to a VPS touches adapters only.
- Good: violations fail CI instead of relying on review.
- Bad: more modules mean more Gradle configuration; mitigated by convention plugins in `build-logic/`.
- Bad: SKIE couples the Kotlin version to a third-party release cadence (it lagged ~2 months for 2.4.20).

### Confirmation

A13 delivers the skeleton with `assertModuleGraph`, Konsist and detekt rules wired into CI and a failing-on-purpose test proving each rule fires.

## More information

Sources (accessed 6 Nov 2026): SKIE releases (https://github.com/touchlab/SKIE/releases) and kotlinlang Slack thread on 2.4.20 support (https://slack-chats.kotlinlang.org/t/34424949/any-word-on-when-skie-will-add-support-for-kotlin-2-4-20-off); Kotlin 2.4.0 what's new and roadmap (A3 [K1], [K2]); RevenueCat KMP SDK 3.0 removes manual iOS linking (https://www.revenuecat.com/release/integrate-kotlin-multiplatform-without-manual-ios-linking-2026-05-07); Koin 4.2.0 release (March 2026, via mirror listing).
