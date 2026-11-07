# ADR-008: CI/CD, signing and environments — GitHub Actions + Fastlane, two Firebase projects

- **Status:** Proposed (for Gate 4)
- **Date:** 2026-11-06
- **Deciders:** Keval Langalia (PO); A10
- **Related:** [ADR-002](ADR-002-monorepo-modules-and-clean-architecture.md), [ADR-004](ADR-004-backend-v1-and-migration-seam.md), [ADR-009](ADR-009-web-stack.md), AGENTS.md (Jira key in commits/PRs), PRD F-ANA-03 (staged-rollout halt)

## Context and problem statement

One monorepo builds a KMP core, an Android app, an iOS app (Kotlin/Native + Xcode), a Ktor server image and an Astro site. The repository **`theKeval/DrivePerf` is public**. We need PR checks, distribution to TestFlight and Play internal testing, deploys for web and API, signing, and dev/staging/prod separation, at minimal cost and effort.

## Decision drivers

Cost · one pipeline for agents to read and fix · secrets safety in a public repo · macOS capacity for Kotlin/Native · reproducible signing.

## Considered options

1. **GitHub Actions for everything + Fastlane** (match for iOS signing, supply for Play).
2. GitHub Actions for shared/Android/web/server + **Xcode Cloud** for iOS (25 compute hours/month included with the Apple Developer Program).
3. Bitrise / Codemagic.

## Decision outcome

**Chosen option: 1.** Standard GitHub-hosted runners are **free for public repositories**, so macOS minutes cost nothing (US$0.062/min for macOS if the repo ever goes private); one system keeps workflows in one place. Xcode Cloud stays a fallback if macOS queues become a bottleneck or the repo goes private.

### Pipelines (`.github/workflows/`)

| Workflow | Trigger | Runner | Steps |
|---|---|---|---|
| `shared.yml` | PR touching `shared/**` | ubuntu + macos | `assertModuleGraph`, detekt, Konsist, `jvmTest`/`androidUnitTest`, `iosSimulatorArm64Test`, coverage gate (≥ 90 % domain + pipeline), SQLDelight `verifyMigrations` |
| `android.yml` | PR touching `apps/android/**` or `shared/**` | ubuntu | lint, unit, Roborazzi screenshots, assemble `devDebug` |
| `ios.yml` | PR touching `apps/ios/**` or `shared/**` | macos | build framework, `xcodebuild test` (unit + snapshot) on simulator |
| `backend.yml` | PR touching `backend/**` or `shared/api-contract/**` | ubuntu | server tests with Firebase emulators, rules tests, OpenAPI lint + contract tests; on `main`: build image → Artifact Registry → deploy Cloud Run `dev`; on tag `api-v*`: deploy `prod` |
| `web.yml` | PR touching `web/**` | ubuntu | build, Lighthouse CI (≥ 95 budgets, F-WEB-01), deploy a **Hosting preview channel** per PR with a comment link; on `main`: deploy live |
| `release-android.yml` | tag `v*` | ubuntu | `bundleProdRelease`, upload R8 mapping to Sentry (Gradle plugin), Fastlane `supply` to the **internal** track; promotion to production is manual with staged rollout |
| `release-ios.yml` | tag `v*` | macos | Fastlane `match` (read-only) + `gym` + `pilot` to TestFlight; dSYMs (app and shared framework) to Sentry with `sentry-cli`; App Store release is manual with phased release |
| `nightly.yml` | schedule | macos + ubuntu | Maestro smoke flows on simulator/emulator; dependency audit |

Rules: Jira key in every PR title and commit (AGENTS.md); `dorny/paths-filter` to skip unaffected jobs; Gradle configuration and build caches plus Konan cache; Renovate for dependencies, with Kotlin bumps held until SKIE supports them.

### Signing and secrets (public repo)

- **No long-lived cloud keys in GitHub.** Deploys to Google Cloud/Firebase use **Workload Identity Federation** (OIDC) bound to `theKeval/DrivePerf` and protected branches/tags.
- Secrets live in GitHub **Environments** `dev` and `prod` (including `SENTRY_AUTH_TOKEN` for symbol uploads); `prod` requires Keval's approval. Workflows never use `pull_request_target`; fork PRs get no secrets.
- iOS: Fastlane `match` storing encrypted certificates/profiles in a **private** repo; App Store Connect API key in the `prod` environment. Android: upload key in the `prod` environment; Play App Signing holds the app signing key.
- `GoogleService-Info.plist` / `google-services.json` for **prod** are injected at build time, not committed (not secret, but keeps abuse surface down). All Google API keys are restricted by bundle ID / package + SHA-1; App Check enforced on the API.
- **No real location data in the repo.** Recorded-drive fixtures (A12, A19) are synthetic or privacy-trimmed (start/end removed, coordinates shifted) before commit; a CI check rejects GPX/CSV fixtures without a `# privacy-trimmed` header.

### Environments

| Environment | Firebase project | Backend | App builds | Web |
|---|---|---|---|---|
| **local** | Emulator Suite (Auth, Firestore, Hosting) + an S3-compatible emulator for trip files (R2 stand-in) | Ktor run locally against emulators | `devDebug` / `Dev` scheme | `astro dev` |
| **dev / staging** | **`driveperf-dev`** (Spark until Phase 6, then Blaze with a US$5 alert) | Cloud Run `api` in dev | internal TestFlight group, Play internal track, `com.driveperf.app.dev` | Hosting preview channels |
| **prod** | **`driveperf-prod`** (display name **"DrivePerf"**) | Cloud Run `api` in prod, `api.driveperf.com` | App Store / Play production | `driveperf.com` |

Staging is the dev project plus release-candidate builds; a separate `driveperf-staging` project is added only for the VPS migration rehearsal ([ADR-010](ADR-010-firebase-now-vs-vps.md)). Project IDs are permanent; confirm them when Keval creates the projects. Bundle/application ID: **`com.driveperf.app`** on both platforms (Android registered in Play Console; confirmed at Gate 4), dev builds `com.driveperf.app.dev`. Kotlin namespaces stay `com.driveperf.*`. Register the same ID in App Store Connect.

### Versioning and release safety

SemVer marketing versions; build number = CI run number; tags `v1.2.3` for apps, `api-v*` for the server, `web` deploys from `main`. Staged rollout (Play) and phased release (App Store) are halted if crash-free sessions drop below 99.5 % on ≥ 1,000 sessions (F-ANA-03 AC3).

### Consequences

- Good: $0 CI while the repo is public; Workload Identity Federation means no JSON key can leak.
- Bad: public repo means workflow and fixture hygiene matter (enforced above). If the repo goes private, macOS minutes become the main CI cost (≈ US$1.90 per 30-min iOS build) and Xcode Cloud's 25 free hours become attractive.

### Confirmation

A13 lands all PR workflows green on the scaffold; A17 proves WIF deploys to dev; first TestFlight and Play internal builds come from tags.

## More information

Sources (accessed 6 Nov 2026): GitHub Actions billing — standard runners free in public repos (https://docs.github.com/en/billing/concepts/product-billing/github-actions); runner prices, macOS US$0.062/min (https://docs.github.com/en/billing/reference/actions-runner-pricing); Xcode Cloud 25 compute hours included (https://developer.apple.com/xcode-cloud/get-started).
