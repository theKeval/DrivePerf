# DrivePerf Android (Jetpack Compose)

Native Compose app on the shared Kotlin core. Minimum Android 10 (minSdk 29), targetSdk 36. Material 3 Expressive,
"Night Drive" theme built from `docs/design/system/tokens.json`.

## What works today (DRIVE-72)

- Four tabs (Drives, Routes, Insights, Garage) on a short navigation bar, Profile as an avatar, and an M3 Expressive
  floating toolbar on the Drives tab with the capture status and Start.
- Onboarding with permissions in this order: fine location, then "Allow all the time" as its own step, then activity
  recognition, then notifications; then battery guidance and opt-in consent (usage statistics, crash reports; both off).
- Manual start and stop: a foreground service (type `location`) with a Live Update notification (Android 16 promoted
  `ProgressStyle`; a standard ongoing notification below), a locked in-drive screen, a confirmed Stop, and the trip list.
- Adapters implementing DRIVE-62's shared ports (`LocationSource`, `MotionSource`, `ActivitySource`, `TripFileStore`,
  `Clock`): Fused Location, `SensorManager` (linear acceleration, gravity, gyroscope, game rotation vector, batched to
  the sink) and Activity Recognition updates with a confidence bucket (the transition API is reserved for the background
  wake path, not built yet), with the spike 1 capture rules (stale samples dropped, gaps measured only while moving,
  recording starts at once, sensor callbacks on one thread).
- Crash reporting is Sentry (EU) behind consent, never Crashlytics (ADR-007). See "Crash reporting" below.

**Interim code (replaced when DRIVE-62's recorder and storage land):** `InterimTripRecorder`, `TripIndex`, `R0Lines` (text
R0 format), `LiveTripStats`, and `app/.../bridge`. They are marked `INTERIM (DRIVE-72)`. The adapters and `FileTripStore`
are final: the core's `PlatformBindings` takes them as they are. Failures the callback ports cannot return (missing
permission or sensor) go to `CaptureFailureBus` as `DomainError.Capture`.
Scores, Stories, routes and processed trips do not exist yet: trip rows say "Recorded".

## Run it

With an emulator running or a phone attached, and `ANDROID_HOME` pointing at the Android SDK:

```bash
./gradlew :apps:android:app:installDevDebug     # installs "DrivePerf Dev" (com.driveperf.app.dev)
```

Needs a JDK 21 to run detekt (detekt does not read JDK 25 bytecode); Gradle itself starts on 17+. Or open the repository
root in Android Studio and run the `apps.android.app` configuration.

To feed a drive to an emulator without a car, send synthetic fixes (never real coordinates):

```bash
for i in $(seq 0 30); do adb emu geo fix $(python3 -c "print(-30.0 + $i*0.00015)") 0.0; sleep 1; done
```

## Modules (ADR-002 section 1)

| Module | What |
|---|---|
| `app` | Composition root: Koin wiring, navigation, bridges from feature interfaces to platform adapters; flavors `dev` (`com.driveperf.app.dev`) and `prod` (`com.driveperf.app`) |
| `designsystem` | Theme, tokens (generated), type, shapes, icons, small components |
| `platform` | Sensors, foreground service, Live Update notification, consent store, Sentry adapter |
| `feature-capture` | Capture state holder, floating toolbar, locked in-drive screen |
| `feature-onboarding` | Onboarding flow and permission requests |
| `feature-drives` | Drives home and All drives (trip list) |
| `feature-account` | Profile and privacy switches |
| `feature-routes`, `feature-insights`, `feature-garage` | Tab roots, empty states until their tickets |
| `feature-replay`, `feature-privacy`, `feature-paywall`, `feature-settings` | Empty until their tickets |

Features see `:shared:presentation`, `:shared:domain`, `:shared:foundation` and `designsystem` only; `assertModuleGraph` fails
CI on any other edge. Features declare the interfaces they need (`CaptureController`, `TripListSource`) and the app
implements them.

## Design tokens

`designsystem/src/main/kotlin/.../DrivePerfTokens.kt` is generated. After a change to `docs/design/system/tokens.json`:

```bash
python3 apps/android/designsystem/tools/generate_tokens.py
```

The brand typeface (Google Sans Flex) is not bundled yet; the system sans stands in. Material 3 is pinned to 1.5.0-rc01
because the Expressive APIs are internal in 1.4.0 (see `gradle/libs.versions.toml`).

## Crash reporting

Sentry starts only after the user opts in, and `Sentry.close()` plus deleting cached envelopes runs on opt-out. The DSN is
injected at build time and never committed: pass `-Pdriveperf.sentryDsn=<dsn>` or set `DRIVEPERF_SENTRY_DSN`. With no
DSN the reporter is a no-op and Profile says crash reporting is not set up in this build. Firebase Analytics and Remote
Config are not wired yet.

## Checks

```bash
./gradlew :apps:android:app:detekt :apps:android:app:lintDevDebug :apps:android:app:testDevDebugUnitTest :apps:android:app:assembleDevDebug
```

`.github/workflows/android.yml` runs these on every PR touching `apps/android/**` or `shared/**`. The app's `detekt` and
`testDevDebugUnitTest` tasks depend on every Android library module's, so one command covers all of them. Compose UI tests
run on the JVM with Robolectric. The real-drive checklist is `docs/verification/DRIVE-72/android-manual-drive-test.md`.
