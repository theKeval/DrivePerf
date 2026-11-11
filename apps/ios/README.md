# DrivePerf iOS (SwiftUI)

Native SwiftUI app shell (DRIVE-67): onboarding with permission primers, the four-tab Liquid Glass shell, Core
Location and Core Motion capture, manual Start and Stop with a Live Activity, automatic trips from the parked-region
wake, and the trip list. Minimum iOS 26.0, iPhone only. It calls the shared Kotlin core through the static framework
`DrivePerfShared` ([ADR-002](../../docs/architecture/adr/ADR-002-monorepo-modules-and-clean-architecture.md) section 8).

## Run it

**Simulator (CI and anyone without a device):**

```bash
tools/run-ios.sh            # boots an iPhone simulator, builds without code signing, installs and launches
```

Needs Xcode 26+, an iOS simulator runtime (`xcodebuild -downloadPlatform iOS` if none is installed), `jq`, and a JDK
17+. Gradle (the repo's wrapper) builds the Kotlin framework for you.

**Your iPhone (free Personal Team), temporary until the Apple Developer account exists (DRIVE-42):**

1. Copy `Config/Local.xcconfig.example` to `Config/Local.xcconfig` (gitignored) and fill in your Team ID and a bundle
   ID of your own, for example `com.thekeval.driveperf.dev`. The widget extension becomes `<that ID>.widgets`.
2. Build and install:

   ```bash
   xcodebuild -project apps/ios/DrivePerf.xcodeproj -scheme DrivePerf -configuration Debug \
     -destination 'id=<device UDID>' -derivedDataPath build/DerivedData -allowProvisioningUpdates build
   xcrun devicectl device install app --device <device UDID> build/DerivedData/Build/Products/Debug-iphoneos/DrivePerf.app
   ```

3. On the phone, trust the developer profile once (Settings › General › VPN & Device Management), then **launch
   DrivePerf from the Home Screen**, not from Xcode or `devicectl`, so background behaviour is real.

Rules for the override:

- **Never sign `com.driveperf.*` with a Personal Team.** It can claim those IDs and block the paid account. That is
  why `Local.xcconfig` must set both the bundle ID and the team, and why Release builds ignore the local team and stay
  on `com.driveperf.app`. The launch build is `com.driveperf.app`; the override disappears with DRIVE-42.
- Personal Team profiles expire after 7 days: reinstall before then. Each new bundle ID uses one of the team's 10 App
  IDs per week.
- `xcrun devicectl device list devices` gives the UDID.

## Signing and identity

| Build | Bundle ID | Signing |
|---|---|---|
| Debug, committed config (CI, simulator) | `com.driveperf.app.dev` | none (`CODE_SIGN_IDENTITY = -`) |
| Debug with `Config/Local.xcconfig` | your override, e.g. `com.thekeval.driveperf.dev` | your Personal Team |
| Release | `com.driveperf.app` | none until DRIVE-42 (the release lane sets it) |

Build settings live in `Config/DrivePerf.xcconfig`. It also carries `DP_SENTRY_DSN` (empty: crash reporting is a stub
until the Sentry EU project exists, ADR-007) and the flags `DP_FEATURE_SIGN_IN_WITH_APPLE`, `DP_FEATURE_PUSH` and
`DP_FEATURE_TESTFLIGHT`, all `NO` until the Apple Developer account exists. Never commit a DSN or a Team ID.

## Layout

| Path | What |
|---|---|
| `DrivePerf.xcodeproj` | Targets `DrivePerf` (app), `DrivePerfWidgets` (Live Activity extension) and `DrivePerfTests`. Source folders are synchronized: **new files under `DrivePerf/`, `DrivePerfWidgets/`, `Shared/` and `DrivePerfTests/` are picked up without editing the project file.** |
| `DrivePerf/` | App target and composition root (`Composition/AppEnvironment.swift`). The only code that imports the Kotlin framework |
| `DrivePerf/CoreBridge/` | The DPPlatform adapters as the Kotlin ports from `:shared:domain` (`LocationSource`, `MotionSource`, `ActivitySource`, `PowerState`, `ParkedRegionMonitor`, `TripFileStore`, `Clock`): conversion only, ready for `PlatformBindings` |
| `DrivePerf/Interim/` | **Temporary** Swift implementation of the `TripRecorder` contract and a trip index of raw recorded rows, so the shell records end to end before the shared recorder lands. It runs on the same ports and writes R0 bytes through `TripFileStore`. Replaced by DRIVE-62's `TripRecorder` and `TripRepository` behind the same `TripRecording` / `TripHistory` protocols |
| `DrivePerf/SelfTest/` | On-device self-test of the trip path (Spike 1 change 10) |
| `DrivePerfWidgets/` | Live Activity for manual trips (Lock Screen and Dynamic Island). Links `DPDesignSystem` only, never the Kotlin framework |
| `Shared/` | `RecordingActivityAttributes` and `StopTripIntent`, compiled into the app and the extension |
| `Packages/DPDesignSystem` | Tokens from `docs/design/system/tokens.json` (colours, spacing, radii, type, DP Numerals) and components; SVG icons copied from the design assets |
| `Packages/DPFeatures` | Screens and view models: `Onboarding`, `App` (shell), `Capture`, `Drives`, `Profile` |
| `Packages/DPPlatform` | Apple-framework adapters: `CoreLocationSource`, `WakeTriggerMonitor` (CLMonitor parked region, significant change, Visits), `LocationServiceSessions` (CLServiceSession), `CoreMotionSource` (with `CMSensorRecorder` history), `CoreMotionActivitySource`, `SystemPowerState`, `FileTripStore` (byte store, gzip), `SystemClock`, permissions, notifications, crash-report stub |
| `Packages/DPSharedBridge` | The Swift seam: Swift mirrors of the shared capture types and ports (same names and fields as `com.driveperf.domain.capture`, with a `Bridge` suffix on the ports), `TripRecording` (the `TripRecorder` contract), `TripHistory` and the app ports. DPPlatform and DPFeatures cannot link the Kotlin framework, so they use these |
| `Config/` | xcconfig, Info.plist fragments, `Local.xcconfig.example` |
| `Scripts/build-shared-framework.sh` | Run Script phase: `./gradlew :shared:ios-export:embedAndSignAppleFrameworkForXcode` |

Dependency rules (ADR-002 section 2): `DPFeatures` sees `DPDesignSystem` and `DPSharedBridge` only; never
`DPPlatform`, Firebase or RevenueCat. Only the app target imports `DrivePerfShared`.

## Capture: what the shell does

- **Shared ports:** every adapter implements the Swift mirror of a `:shared:domain` port, and `CoreBridge/` wraps it
  in the Kotlin protocol. They follow the Kotlin contract: callable from any thread, batched sinks (motion in batches
  of 25), `FileResult` codes instead of errors, one monotonic clock (`CLOCK_MONOTONIC_RAW`, which keeps counting
  during sleep; Core Location and Core Motion timestamps are converted to it).
- **Launch order (ADR-002 §5):** `didFinishLaunching` checks that trip files are readable (a protected sentinel file,
  not `isProtectedDataAvailable`), holds the `CLServiceSession` (Always when automatic trips are on), arms the wake
  triggers, then calls the recorder's `onLaunch()`, which recovers an interrupted trip.
- **Wake triggers:** the parked-location `CLMonitor` region (named `parkedSpot`, alphanumeric) is the primary trigger,
  re-armed at every trip end and every launch; significant change is the fallback; Visits only place the first parking
  spot. Wakes older than 5 minutes (fixes) or 15 minutes (region events) are dropped.
- **Recording starts at the wake**, GNSS (`liveUpdates` plus the legacy keep-alive) and 50 Hz device motion, and the
  candidate's samples are kept when it is confirmed. Motion callbacks are `@Sendable` and run on their own queue.
- **Manual trips** start from the accessory's Start button and show a Live Activity. **Automatic trips never start a
  local Live Activity** (it fails in the background); they post a passive "Recording your drive" notification.
- Trip files live under `Application Support/Capture/trips/<id>/`: R0 and R1 `completeUnlessOpen` (written while
  locked), R0 excluded from backup, gzipped when the trip ends. Until the shared R0 codec lands, R0 holds an interim
  CSV capture log. Nothing is uploaded, and the trip rows hold no coordinates.

## Tests

```bash
xcodebuild test -project DrivePerf.xcodeproj -scheme DrivePerf -destination 'platform=iOS Simulator,name=<an iPhone>' CODE_SIGNING_ALLOWED=NO
```

The same command with `-destination 'id=<device UDID>' -allowProvisioningUpdates` runs the tests on your iPhone with
the local override. `.github/workflows/ios.yml` runs them on a simulator and keeps a screenshot of the launched app.

**On-device self-test:** Profile › Diagnostics › Self-test, or launch with the argument from a Mac:

```bash
xcrun devicectl device process launch --device <UDID> --terminate-existing <bundle ID> -- -selfTest
xcrun devicectl device copy from --device <UDID> --domain-type appDataContainer --domain-identifier <bundle ID> --source Documents/self-test.txt --destination self-test.txt
```

It checks storage, the shared core ports through the Kotlin types (clock, a `TripFileStore` round trip with gzip and
SHA-256, power, motion capability), the monitor name, permissions, the stale-wake filter, a GPS fix, 50 Hz motion off
the main thread and activity history (all three through the Kotlin sinks), a synthetic wake (records from the wake,
then rejects and deletes) and a manual start/stop, in a scratch folder. The report holds counts and timings only. The real-drive checklist is
[MANUAL-DRIVE-TEST.md](MANUAL-DRIVE-TEST.md).
