---
applyTo: "apps/ios/**"
---

# iOS / SwiftUI rules (DrivePerf `apps/ios/`)

Read `apps/ios/README.md` and ADR-002 sections 2 to 5 first. Minimum iOS 26, iPhone only, Swift 6 language mode.

- **Structure:** the app target (`DrivePerf/`) is the composition root and the only code that imports the Kotlin
  framework `DrivePerfShared`. Local SPM packages: `DPDesignSystem`, `DPFeatures` (one folder per PRD epic),
  `DPPlatform` (Apple-framework and Firebase adapters), `DPSharedBridge` (Swift types and protocols over the Kotlin core).
  `DPFeatures` sees `DPDesignSystem` and `DPSharedBridge` only, never `DPPlatform`, Firebase or RevenueCat. Widget and
  Live Activity extensions must not link the Kotlin framework (memory limits).
- **The Xcode project uses synchronized folders:** add Swift files under `DrivePerf/`, `DrivePerfTests/` or a package;
  do not hand-edit `project.pbxproj` for that. Changing build settings, targets or phases is rare: do it in Xcode.
- **MVVM:** `@Observable @MainActor` view models own the state, views are thin and declarative, observe shared
  presenters with SKIE's `AsyncSequence` in a `.task {}` and cancel on deinit. Use `async/await`, no completion-handler
  pyramids; value types by default; follow Apple's HIG; real Liquid Glass components, no imitation.
- **Design:** `docs/design/<JIRA-KEY>/ios/` and `docs/design/screens/ios/` are the source of truth; take colors,
  spacing and type verbatim from `design-spec.md`; use the SVG assets, never recreate icons.
- **Signing:** the committed config is unsigned (`CODE_SIGNING_ALLOWED=NO` in CI). Device runs use the gitignored
  `apps/ios/Config/Local.xcconfig`, which must override the bundle ID and the team together (README "Your iPhone");
  never sign `com.driveperf.*` with a Personal Team, and never commit a Team ID or DSN.
- **Tests:** XCTest or Swift Testing for view models and bridges; `tools/run-ios.sh` runs the app on a simulator.
