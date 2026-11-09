# CI/CD as built (ADR-008)

What `.github/workflows/` does today, and what is deliberately inert. ADR-008 is the design; this file is the status. All runners are GitHub-hosted and free for this public repository. No workflow uses `pull_request_target`, none holds a long-lived cloud key (Google access will use Workload Identity Federation), and fork pull requests never see secrets.

| Workflow | Trigger (paths) | What it does | State |
|---|---|---|---|
| `shared.yml` | PR / `main`: `shared/**`, build files, `config/**` | `assertModuleGraph`, detekt, Konsist, JVM and Android host tests, Kover gate (≥ 90 % on `:shared:domain`), iOS simulator tests, and the guardrails-fire proof | active |
| `android.yml` | `apps/android/**`, `shared/**`, build files | detekt, lint (`warningsAsErrors`), unit tests, `assembleDevDebug`, APK artifact | active. Roborazzi arrives with the first screens (DRIVE-72) |
| `ios.yml` | `apps/ios/**`, `shared/**`, build files | `xcodebuild test` on a simulator (no code signing), launches the app and uploads a screenshot | active, simulator only |
| `backend.yml` | `backend/api/**`, `backend/openapi/**`, `shared/api-contract/**` | server tests, OpenAPI lint, Docker image build (never pushed from a PR) | tests active. **Cloud Run deploy (`dev` on `main`, `prod` on tag `api-v*`) is inert** |
| `web.yml` | `web/**`, `backend/firebase/**` | build, Lighthouse, Firestore rules tests, preview channel per PR, **live deploy on `main`** | active, unchanged by DRIVE-59 |
| `repo-checks.yml` | every PR | rejects GPX/CSV files without a `# privacy-trimmed` header | active |
| `nightly.yml` | schedule 06:17 UTC | `npm audit` (high or critical) and Maestro smoke flows | audit active. Maestro passes with a notice until `.maestro/*.yaml` exists |
| `release-android.yml` | tag `v*` | builds `bundleProdRelease` | **inert** |
| `release-ios.yml` | tag `v*` | TestFlight upload | **inert** (fails on purpose if enabled early) |
| `jira-sync.yml`, `review-router.yml` | as before | Jira and review automation | unchanged |

Differences from ADR-008's table: path filters use GitHub's native `paths:` instead of `dorny/paths-filter` (no third-party action needed); the Firestore rules tests stay in `web.yml`, which already owns `backend/firebase/`; SQLDelight `verifyMigrations`, Roborazzi, the OpenAPI contract tests and the server tests against Firebase emulators join when their code exists (DRIVE-62, DRIVE-72, DRIVE-76).

## What is inert, why, and how to switch it on

| Piece | Waiting for | Switch |
|---|---|---|
| Cloud Run deploy in `backend.yml` | Blaze upgrade, Artifact Registry, Workload Identity Federation (Phase 6, DRIVE-76) | repository variable `CLOUD_RUN_DEPLOY_ENABLED=true` plus `GCP_WIF_PROVIDER`, `GCP_DEPLOY_SA`, `GCP_PROJECT_ID`, `GCP_REGION` in the `dev` and `prod` environments. The steps are a sketch, untested until DRIVE-76 proves WIF |
| `release-android.yml` | Play upload key, service account, Fastlane `supply`, Sentry mapping upload (DRIVE-72) | repository variable `RELEASE_ANDROID_ENABLED=true` |
| `release-ios.yml` | Paid Apple Developer account, App Store Connect API key, Fastlane `match` repository, dSYM upload (DRIVE-67) | repository variable `RELEASE_IOS_ENABLED=true`, after replacing its placeholder step |
| Maestro | the first flows in `.maestro/` | add the emulator and simulator jobs next to the flow check in `nightly.yml` |

Both release workflows run in the `prod` environment, which requires Keval's approval. This repository variable gate means pushing a `v*` tag today only produces a skipped run. No secret is stored, read or required by anything in this folder.
