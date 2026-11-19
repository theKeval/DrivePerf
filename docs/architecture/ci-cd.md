# CI/CD as built (ADR-008)

What `.github/workflows/` does today, and what is deliberately inert. ADR-008 is the design; this file is the status. All runners are GitHub-hosted and free for this public repository. No workflow uses `pull_request_target`, none holds a long-lived cloud key (Google access will use Workload Identity Federation), and fork pull requests never see secrets.

| Workflow | Trigger (paths) | What it does | State |
|---|---|---|---|
| `shared.yml` | PR / `main`: `shared/**`, build files, `config/**` | `assertModuleGraph`, detekt, Konsist, JVM and Android host tests, Kover gate (≥ 90 % on `:shared:domain`), iOS simulator tests, and the guardrails-fire proof | active |
| `android.yml` | `apps/android/**`, `shared/**`, build files | detekt, lint (`warningsAsErrors`), unit tests, `assembleDevDebug`, APK artifact | active. Roborazzi arrives with the first screens (DRIVE-72) |
| `ios.yml` | `apps/ios/**`, `shared/**`, build files | `xcodebuild test` on a simulator (no code signing), launches the app and uploads a screenshot | active, simulator only |
| `backend.yml` | `backend/api/**`, `backend/openapi/**`, `shared/api-contract/**` | server tests, OpenAPI lint, Docker image build (never pushed from a PR) | tests active. **Cloud Run deploy (`dev` on `main`, `prod` on tag `api-v*`) is inert** |
| `web.yml` | `web/**`, `backend/firebase/**` | build, Lighthouse, Firestore rules tests (the rules, deny-all, index and config guards of DRIVE-76 run in the same `npm test`), preview channel per PR, **live deploy on `main`** | active, unchanged by DRIVE-59 and DRIVE-76 |
| `repo-checks.yml` | every PR | rejects GPX/CSV files without a `# privacy-trimmed` header | active |
| `nightly.yml` | schedule 06:17 UTC | `npm audit` (high or critical) and Maestro smoke flows | audit active. Maestro passes with a notice until `.maestro/*.yaml` exists |
| `release-android.yml` | tag `v*`, manual run; PRs touching `fastlane/**` or the workflow | signed `bundleProdRelease` → artifact (AAB + R8 mapping) → Fastlane `android internal` (`supply`) to the Play **internal** track. PRs only load the lanes | **built, off** until Keval adds the secrets and sets `RELEASE_ANDROID_ENABLED` (DRIVE-218) |
| `release-ios.yml` | tag `v*`, manual run; PRs touching `fastlane/**` or the workflow | Fastlane `ios beta`: `match` read-only → `gym` → dSYMs to Sentry (when configured) → `pilot` to TestFlight. PRs only load the lanes | **built, off** until Keval adds the secrets and sets `RELEASE_IOS_ENABLED` (DRIVE-218) |
| ~~`jira-sync.yml`, `review-router.yml`~~ | — | Jira sync and Copilot review routing from the `agentic-workflow` harness | **removed** (DRIVE-210, 2026-11-11). Jira is updated by the coordinator; PR review is the Codex review bot. The `JIRA_*`, `SLACK_WEBHOOK_*` and `AGENT_PAT` repository secrets are kept, unused, for a future harness |

Differences from ADR-008's table: path filters use GitHub's native `paths:` instead of `dorny/paths-filter` (no third-party action needed); the Firestore rules tests stay in `web.yml`, which already owns `backend/firebase/`; Roborazzi, the OpenAPI contract tests and the server tests against Firebase emulators join when their code exists (DRIVE-72, DRIVE-76). SQLDelight's `verifySqlDelightMigration` runs in `shared.yml` since DRIVE-206.

**Firestore indexes are not deployed by CI.** `web.yml` deploys hosting and `firestore:rules` only. After a change to
`backend/firebase/firestore.indexes.json` is merged, Keval deploys the indexes by hand, `dev` first and then `prod`,
with `firebase deploy --only firestore:indexes --project <dev|prod>` (on Keval's Mac:
`node /opt/homebrew/lib/node_modules/npm/bin/npx-cli.js firebase deploy --only firestore:indexes --project dev`). See
[firebase-backend-setup.md A4](../launch/firebase-backend-setup.md).

## What is inert, why, and how to switch it on

| Piece | Waiting for | Switch |
|---|---|---|
| Cloud Run deploy in `backend.yml` | Blaze upgrade, Artifact Registry, Workload Identity Federation (Phase 6, DRIVE-76) | repository variable `CLOUD_RUN_DEPLOY_ENABLED=true` plus `GCP_WIF_PROVIDER`, `GCP_DEPLOY_SA`, `GCP_PROJECT_ID`, `GCP_REGION` in the `dev` and `prod` environments. The steps are a sketch, untested until DRIVE-76 proves WIF |
| `release-android.yml` | Keval's upload keystore, Play service account and first manual Play upload ([runbook](../launch/release-runbook.md) Part C). The Sentry R8 mapping upload joins with crash reporting (DRIVE-196) | repository variable `RELEASE_ANDROID_ENABLED=true` |
| `release-ios.yml` | Keval's App Store Connect API key, `match` repository and distribution certificate ([apple-developer-setup.md](../launch/apple-developer-setup.md) B1 to B4, [runbook](../launch/release-runbook.md) Part A). The dSYM upload is skipped with a warning until `SENTRY_AUTH_TOKEN` exists | repository variable `RELEASE_IOS_ENABLED=true` |
| Maestro | the first flows in `.maestro/` | add the emulator and simulator jobs next to the flow check in `nightly.yml` |

Both release workflows run in the `prod` environment, which requires Keval's approval. Until the repository variable is set, pushing a `v*` tag only produces a skipped run.

## Release lanes (DRIVE-218)

The lanes live in `fastlane/Fastfile`, with their own `fastlane/Gemfile` and lockfile (Ruby 4.0, Fastlane 2.242.0;
`ruby/setup-ruby` is pinned by commit because those jobs hold `prod` secrets). The [release runbook](../launch/release-runbook.md)
has every secret and variable, the steps Keval takes to create them, how to cut a release and what failures mean.

- **What ships:** only `com.driveperf.app` (Release / `prodRelease`) goes to TestFlight and Play internal testing. The
  dev app stays a device-only build; the runbook ("What goes to beta") gives the reason. This narrows ADR-008's
  environment table, which also lists the dev app on the internal tracks.
- **Versions:** marketing version from the tag `v1.2.3` (or the manual run's input); build number and version code
  are the workflow's run number.
- **Signing:** iOS with `match` read-only, applied to the two shipping targets in the CI checkout only (the committed
  project stays unsigned). Android with the upload key from `prod`, through a Gradle signing config that exists only
  when `DRIVEPERF_UPLOAD_KEYSTORE` is set, so local and pull-request release builds stay unsigned.
- **One deviation from ADR-008:** the Play upload uses a service-account JSON key, because `supply` cannot use Workload
  Identity Federation. The key has no Google Cloud role, only "Release apps to testing tracks" on this app, and lives
  only in `prod` (runbook C3).
- **Symbols:** iOS dSYMs go to Sentry with a pinned `sentry-cli` and are kept as a run artifact for 90 days; the Android
  R8 mapping goes to Play with each upload (for Android vitals) and is kept as an artifact. Sentry's own R8 mapping
  upload arrives with crash reporting (DRIVE-196).

Nothing in `.github/workflows/` stores a secret. The release jobs read theirs from the `prod` environment at run time,
and the pull-request job of each release workflow reads none.
