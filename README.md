# DrivePerf

**Every drive, decoded.** DrivePerf is a phone-based driving analytics app for iOS and Android. It is built for everyday driving enthusiasts and for drivers who want smoother, more efficient driving.

[driveperf.com](https://driveperf.com) · [Jira DRIVE](https://thekeval.atlassian.net/jira/software/projects/DRIVE/summary) · App ID `com.driveperf.app`

## Status

**Sprint 2 · Foundations.** The cross-platform v1 is specified (PRD, design, ADRs). The monorepo scaffold is in place: the shared Kotlin core, both apps (each showing "Hello DrivePerf" from shared code), the Ktor server skeleton and CI. Feature work builds on it. See the [v1 kickoff plan](docs/planning/DrivePerf-v1-kickoff-plan.md) for phases and gates.

## Run the apps

One command per app. Prerequisites: a JDK 17+ on the PATH (Gradle uses a JDK 21 toolchain), and `ANDROID_HOME` for Android; Xcode 26+ and `jq` for iOS.

| App | Command | Details |
|---|---|---|
| Android (Compose) | `./gradlew :apps:android:app:installDevDebug` (emulator running or phone attached) | [apps/android](apps/android/README.md) |
| iOS (SwiftUI) | `tools/run-ios.sh` (boots a simulator, builds without code signing, launches) | [apps/ios](apps/ios/README.md) |
| Website | `node /opt/homebrew/lib/node_modules/npm/bin/npm-cli.js ci --prefix web && node /opt/homebrew/lib/node_modules/npm/bin/npm-cli.js run dev --prefix web` (plain `npm` elsewhere) | [web](web/README.md) |
| Server | `./gradlew :backend:api:run` (then `http://localhost:8080/healthz`) | [backend/api](backend/api/README.md) |

Checks before a PR: `./gradlew assertModuleGraph detekt jvmTest testAndroidHostTest koverVerify` for the shared core ([guardrails](docs/architecture/guardrails.md)), and the commands in each app's README.

## Repository layout

| Path | Purpose |
|---|---|
| `shared/` | Kotlin Multiplatform core: `foundation`, `domain`, `pipeline`, `presentation`, `data/{local,remote,sync}`, `api-contract`, `di`, `ios-export` (the iOS framework), `testing` |
| `apps/ios/` | SwiftUI app: Xcode project plus local Swift packages |
| `apps/android/` | Compose app: `app`, `designsystem`, `platform`, one `feature-*` module per PRD epic |
| `backend/api/` | Kotlin/Ktor server; `backend/openapi/v1.yaml` is its HTTP contract; `backend/firebase/` holds Firebase config and Firestore rules; `backend/ops/` is reserved for the VPS migration |
| `web/` | The Astro website, [driveperf.com](https://driveperf.com) |
| `build-logic/`, `gradle/`, `config/`, `tools/` | Gradle convention plugins, version catalog, detekt config, CI and helper scripts |
| `docs/planning/` | Kickoff plan, phase gates, agent prompts |
| `docs/research/` | Market, competitor and platform research |
| `docs/product/` | PRD, strategy, decisions log, Jira map |
| `docs/design/` | Design system, motion language, per-ticket design artifacts (`docs/design/<JIRA-KEY>/`) |
| `docs/architecture/` | Overview, ADRs, data model, pipeline spec, [guardrails](docs/architecture/guardrails.md), [CI/CD status](docs/architecture/ci-cd.md) |
| `docs/verification/` | Evidence that a ticket's acceptance criteria were met (screenshots) |
| `prototypes/` | Clickable HTML/CSS/JS prototypes: `ios/`, `android/`, `web/` |
| `legacy/android-prototype/` | The frozen Android-only prototype. Reference only; also tagged `android-prototype-v0` |

## Conventions

Every branch, commit, issue and PR carries a Jira key (`DRIVE-<n>`). See [AGENTS.md](AGENTS.md).
