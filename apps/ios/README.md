# DrivePerf iOS (SwiftUI)

Native SwiftUI app. It calls the shared Kotlin core through the static framework `DrivePerfShared` ([ADR-002](../../docs/architecture/adr/ADR-002-monorepo-modules-and-clean-architecture.md) section 8). Minimum iOS 26.0, iPhone only. Today it shows one "Hello DrivePerf" screen whose text comes from `DrivePerfCore.hello()` in Kotlin.

## Run it (one command)

```bash
tools/run-ios.sh            # boots an iPhone simulator, builds without code signing, installs and launches
```

Needs Xcode 26+, an iOS simulator runtime (`xcodebuild -downloadPlatform iOS` if none is installed), `jq`, and a JDK 17+. Gradle (the repo's wrapper) builds the Kotlin framework for you. Or open `DrivePerf.xcodeproj` in Xcode and press Run on a simulator.

## Signing: simulator only, on purpose

The project builds for the simulator with **no development team and no code signing** (`CODE_SIGN_IDENTITY = -`). Do **not** pick a Personal Team for `com.driveperf.app` or `com.driveperf.app.dev` in Xcode: registering the IDs under a free team can block them when the paid Apple Developer account needs them. Device builds and TestFlight signing arrive with DRIVE-67 and the release lane ([ADR-008](../../docs/architecture/adr/ADR-008-ci-cd-and-environments.md)).

## Layout

| Path | What |
|---|---|
| `DrivePerf.xcodeproj` | App target `DrivePerf` and unit-test target `DrivePerfTests`. Source folders are synchronized: **new files under `DrivePerf/` and `DrivePerfTests/` are picked up without editing the project file.** Debug builds are `com.driveperf.app.dev` ("DrivePerf Dev"), Release is `com.driveperf.app`. |
| `DrivePerf/` | App target: entry point and the only code that imports the Kotlin framework (`SharedCoreHelloProvider.swift`) |
| `Packages/DPDesignSystem` | Design tokens and components |
| `Packages/DPFeatures` | Screens and view models, one folder per PRD epic |
| `Packages/DPPlatform` | Apple-framework adapters implementing shared ports (empty until DRIVE-67) |
| `Packages/DPSharedBridge` | Plain Swift types and protocols between features and the Kotlin core |
| `Scripts/build-shared-framework.sh` | Run Script phase: `./gradlew :shared:ios-export:embedAndSignAppleFrameworkForXcode` |

Dependency rules (ADR-002 section 2): `DPFeatures` sees `DPDesignSystem` and `DPSharedBridge` only; never `DPPlatform`, Firebase or RevenueCat. Only the app target imports `DrivePerfShared`, so the framework is always built (Run Script phase) before any Swift that needs it.

## Tests

```bash
xcodebuild test -project DrivePerf.xcodeproj -scheme DrivePerf -destination 'platform=iOS Simulator,name=<an iPhone>' CODE_SIGNING_ALLOWED=NO
```

`.github/workflows/ios.yml` runs this on a macOS runner and keeps a screenshot of the launched app.
