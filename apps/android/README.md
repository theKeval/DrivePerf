# DrivePerf Android (Jetpack Compose)

Native Compose app that calls the shared Kotlin core directly. Minimum Android 10 (minSdk 29), targetSdk 36. Today it shows one "Hello DrivePerf" screen whose text comes from `DrivePerfCore.hello()`.

## Run it (one command)

With an emulator running or a phone attached, and `ANDROID_HOME` pointing at the Android SDK:

```bash
./gradlew :apps:android:app:installDevDebug     # installs "DrivePerf Dev" (com.driveperf.app.dev)
```

Needs a JDK 17+ to start Gradle (the build itself uses a JDK 21 toolchain, downloaded if missing). Or open the repository root in Android Studio and run the `apps.android.app` configuration.

## Modules (ADR-002 section 1)

| Module | What |
|---|---|
| `app` | Composition root: entry point, flavors `dev` (`com.driveperf.app.dev`) and `prod` (`com.driveperf.app`) |
| `designsystem` | "Night Drive" theme and components |
| `platform` | Sensors, foreground service, WorkManager, Firebase adapters implementing shared ports (empty until DRIVE-72) |
| `feature-<epic>` | One module per PRD epic: capture, drives, replay, routes, insights, garage, account, privacy, paywall, settings, onboarding (empty until the feature tickets) |

Features see `:shared:presentation`, `:shared:domain`, `:shared:foundation` and `designsystem` only; `assertModuleGraph` fails CI on any other edge.

## Checks

```bash
./gradlew :apps:android:app:lintDevDebug :apps:android:app:assembleDevDebug
```

`.github/workflows/android.yml` runs detekt, lint, unit tests and `assembleDevDebug` on every PR touching `apps/android/**` or `shared/**`.
