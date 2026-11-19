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

**Your iPhone (the paid DrivePerf team, DRIVE-42):**

1. Copy `Config/Local.xcconfig.example` to `Config/Local.xcconfig` (gitignored) and set `DEVELOPMENT_TEAM` to the
   DrivePerf team's ID (developer.apple.com/account › Membership details). Leave the Personal Team lines commented
   out. The Debug build then signs as `com.driveperf.app.dev`, the widget extension as `com.driveperf.app.dev.widgets`,
   both with the App Group `group.com.driveperf.app`, under automatic signing.
2. Build and install:

   ```bash
   xcodebuild -project apps/ios/DrivePerf.xcodeproj -scheme DrivePerf -configuration Debug \
     -destination 'id=<device UDID>' -derivedDataPath build/DerivedData -allowProvisioningUpdates build
   xcrun devicectl device install app --device <device UDID> build/DerivedData/Build/Products/Debug-iphoneos/DrivePerf.app
   ```

   `-allowProvisioningUpdates` lets Xcode create or refresh the development profiles. It needs the Apple ID that
   belongs to the team signed in under Xcode › Settings › Accounts. A new iPhone must be registered first: Xcode does
   it when you pick the phone as the run destination, or add it under Devices in the developer portal.
3. **Launch DrivePerf from the Home Screen**, not from Xcode or `devicectl`, so background behaviour is real.

What the paid team has registered (Certificates, Identifiers & Profiles):

| Item | Value |
|---|---|
| App IDs | `com.driveperf.app`, `com.driveperf.app.widgets`, `com.driveperf.app.dev`, `com.driveperf.app.dev.widgets`, each with App Groups |
| App Group | `group.com.driveperf.app`, shared by all four (the F-CAP-10 widgets read the app's snapshot through it) |
| Not needed as capabilities | background location (`UIBackgroundModes` in Info.plist) and Live Activities (`NSSupportsLiveActivities`, updated locally, no push) |
| Not enabled yet | Sign in with Apple, Push Notifications: add them to the App IDs when their features are built (`DP_FEATURE_*` below) |

Development profiles last a year and renew automatically with `-allowProvisioningUpdates`; there is no 7-day expiry
any more. `xcrun devicectl list devices` gives the UDID.

**Fallback: a free Personal Team.** For someone without access to the paid team, or if the membership lapses.
`Local.xcconfig.example` has the three lines to uncomment, and they go together:

- **Never sign `com.driveperf.*` with a Personal Team.** A Personal Team can claim those IDs and block them for the paid
  team. The fallback therefore overrides `DP_APP_ID_DEBUG` with an ID of your own, for example `com.yourname.driveperf.dev`.
  The widget extension becomes `<that ID>.widgets`. Release builds ignore the local team and stay on `com.driveperf.app`.
- **No App Group on a Personal Team.** A free team cannot use `group.com.driveperf.app`, so the fallback points
  `DP_ENTITLEMENTS_APP` and `DP_ENTITLEMENTS_WIDGETS` at `Config/DrivePerf.personal-team.entitlements` and
  `Config/DrivePerfWidgets.personal-team.entitlements`, which hold no App Group. Without the group the shared snapshot
  has no container: the widget and the Control Center control show their empty state, the app skips writing the
  snapshot, and nothing crashes. Capture, the Live Activity and notifications work as normal.
- Trust the developer profile once on the phone (Settings › General › VPN & Device Management). Personal Team profiles
  expire after 7 days: reinstall before then. Each new bundle ID uses one of the team's 10 App IDs per week.

## Signing and identity

| Build | Bundle ID | Signing |
|---|---|---|
| Debug, committed config (CI, simulator) | `com.driveperf.app.dev` | none (`CODE_SIGN_IDENTITY = -`) |
| Debug with `Config/Local.xcconfig` (default) | `com.driveperf.app.dev` | the paid DrivePerf team, automatic |
| Debug with the Personal Team fallback | your override, e.g. `com.yourname.driveperf.dev` | your Personal Team |
| Release | `com.driveperf.app` | none in the project; the release lane (`release-ios.yml`, Fastlane `match`, ADR-008) signs it |

Build settings live in `Config/DrivePerf.xcconfig`. It also carries `DP_SENTRY_DSN` (empty: crash reporting is a stub
until the Sentry EU project exists, ADR-007) and the flags `DP_FEATURE_SIGN_IN_WITH_APPLE`, `DP_FEATURE_PUSH` and
`DP_FEATURE_TESTFLIGHT`. They stay `NO` until each feature is built: Sign in with Apple and push need their capability
on the App IDs first, and TestFlight needs the release lane. Never commit a DSN. The Team ID is not a secret, but it
stays in the gitignored `Local.xcconfig` so the committed config stays unsigned for CI and simulator runs.

## Layout

| Path | What |
|---|---|
| `DrivePerf.xcodeproj` | Targets `DrivePerf` (app), `DrivePerfWidgets` (Live Activity extension) and `DrivePerfTests`. Source folders are synchronized: **new files under `DrivePerf/`, `DrivePerfWidgets/`, `Shared/` and `DrivePerfTests/` are picked up without editing the project file.** |
| `DrivePerf/` | App target and composition root (`Composition/AppEnvironment.swift`). The only code that imports the Kotlin framework |
| `DrivePerf/CoreBridge/` | The DPPlatform adapters as the Kotlin ports from `:shared:domain` (`LocationSource`, `MotionSource`, `ActivitySource`, `PowerState`, `ParkedRegionMonitor`, `TripFileStore`, `Clock`), handed to `DrivePerfCore.start(platform:)` as `CorePlatformBindings`. `CoreTripRecorder` gives the features `TripRecording` / `TripHistory` over the core's `TripRecorder` and `TripRepository` and adds the iOS surfaces (Live Activity, auto-trip notification, wake assertion) |
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
  triggers, then opens the shared core off the main thread and calls `onLaunch()` to recover an interrupted trip.
  Wakes during startup are buffered until recovery has been queued. A UIKit background task covers startup and
  candidate confirmation; GPS drift cannot release it early. Re-arming an unchanged region preserves its pending event.
- **Wake triggers:** the parked-location `CLMonitor` region (named `parkedSpot`, alphanumeric) is the primary trigger,
  re-armed at every trip end and every launch; significant change is the fallback; Visits only place the first parking
  spot. Wakes older than 5 minutes (fixes) or 15 minutes (region events) are dropped.
- **Recorded accelerometer history** is renewed at launch, foreground and every wake while automatic trips are on,
  including wakes delivered while the process stays in the background. The OS records at most 12 hours per request;
  after a longer period without a wake, missing historical samples cannot be recovered retroactively.
- **Recording starts at the wake**, GNSS (`liveUpdates` plus the legacy keep-alive) and 50 Hz device motion, and the
  candidate's samples are kept when it is confirmed. Motion callbacks are `@Sendable` and run on their own queue.
- **Auto-detect health (F-CAP-02, F-CAP-14):** at launch and on every foreground the app reads Always location,
  Motion & Fitness, precise location and Background App Refresh (`AutoDetectReadiness`, DPFeatures) and tells the core
  through `setTriggersAvailable`. Home shows "Auto-detect on / off / needs attention" with a Fix button (the capture
  status sheet, which names every problem and opens Settings) and the accessory shows "Last check-in" from
  `RecorderStatus.lastCheckIn`.
- **Watchdog (F-CAP-14, EC-FQ):** iOS cannot see a force-quit, so the core schedules a local notice 24 h after each
  check-in (launch, wake, foreground), at most once per 72 h, through `NotificationWatchdog` (one request,
  fixed identifier, replaced each time). Tapping it opens the status sheet and tells the core
  (`onWatchdogOpened`). The first launch after a restart is reported to the core as `boot` (`BootDetector`).
- **Power (F-CAP-16):** `SystemPowerState` feeds battery level, charging, Low Power Mode and `ProcessInfo.thermalState`
  to the core; the core's IMU rate (50, 25 or 10 Hz) is applied by `CoreMotionSource.setRate`. iOS has no location
  rate control worth using (`mayBatchLocation` is Android only), so GNSS stays at 1 Hz while recording and is off
  while armed. The in-drive screen reports `setInDriveScreenVisible` as it shows and hides, so both platforms share one
  contract. Continuous location owns background execution after candidate confirmation.
- **Manual trips** start from the accessory's Start button and show a Live Activity. **Automatic trips never start a
  local Live Activity** (it fails in the background); they post a passive "Recording your drive" notification.
- Trip files live under `Application Support/Capture/trips/<id>/`: R0 and R1 `completeUnlessOpen` (written while
  locked), R0 excluded from backup, gzipped when the trip ends. The shared core writes both (protobuf, data-model.md §2.4) and the
  trip rows in its SQLite database. Nothing is uploaded.

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
