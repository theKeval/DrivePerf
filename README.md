# DrivePerf

A local-first, explainable driving analytics app that scores safety, smoothness, efficiency, and trip quality from start point to end point using only the phone's sensors and location stack.

## Product identity

- **Product:** DrivePerf
- **Domain:** [driveperf.com](https://driveperf.com) (registered project domain; website deployment is separate).
- **Repository:** [theKeval/DrivePerf](https://github.com/theKeval/DrivePerf).
- **Android namespace and release application ID:** `com.driveperf`.
- **Debug application ID:** `com.driveperf.debug`.
- **Packages:** `com.driveperf` and its subpackages.

DrivePerf is being researched for two audiences: driving enthusiasts who want detailed
performance metrics, and everyday or new drivers who want understandable feedback and
coaching. Early versions will focus on driving analytics without insurance integrations
or insurance discount claims. The current implementation remains an early prototype;
research will define the next specifications.

### Android identity migration

The new application ID installs as a separate Android app with its own private storage.
Existing installations under the previous ID do not upgrade in place, and their trips,
preferences, permissions, and backups do not transfer automatically. The new Room
database is `driveperf.db`; the schema remains at version 1 and DataStore continues to
use `settings.preferences_pb` in the new app's sandbox. No cross-app data migration has
been implemented. Preserve any existing installation with data that needs to be retained
until an explicit export/import approach is designed.

The manifest's provider authority continues to derive from `${applicationId}`. This
project currently declares no website App Links, custom URL scheme, or domain backend;
registering the domain alone does not configure those integrations.

## Architecture

DrivePerf follows Google's recommended Android architecture with unidirectional data flow:

```
UI Layer (Compose + ViewModel)
    ↕ UI State (StateFlow)
Domain Layer (Scoring calculators, domain models)
    ↕ Repository interface
Data Layer (Room database, DAOs, Mappers)
    ↕ Android Platform APIs
Platform (FusedLocationProvider, ActivityRecognition, SensorManager)
```

### Module structure

```
app/src/main/java/com/driveperf/
├── DrivePerfApp.kt              – Hilt application class + WorkManager init
├── MainActivity.kt               – Single activity, Compose entry point
├── di/
│   ├── AppModule.kt              – Repository bindings
│   └── DatabaseModule.kt         – Room database and DAO providers
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt        – Room database definition
│   │   ├── dao/                  – TripSession, LocationPoint, MotionWindow, DrivingEvent, TripScore DAOs
│   │   └── entity/               – Room @Entity data classes
│   └── repository/
│       ├── TripRepository.kt     – Interface
│       └── TripRepositoryImpl.kt – Room-backed implementation with domain mappers
├── domain/
│   ├── model/                    – TripSession, DrivingEvent, TripScore, EventType, TripState
│   └── scoring/
│       ├── SafetyScoreCalculator.kt
│       ├── SmoothnessScoreCalculator.kt
│       ├── EfficiencyScoreCalculator.kt
│       └── TripQualityScoreCalculator.kt
├── feature/
│   ├── onboarding/               – 3-page onboarding flow
│   ├── permissions/              – Per-permission educational UI
│   ├── trips/                    – Trip list + trip detail (score breakdown, events, delete/passenger)
│   ├── insights/                 – 30-day trend view and coaching tips
│   └── settings/                 – Auto-detect toggle, cloud sync toggle, privacy info
├── service/
│   ├── TripTrackingService.kt    – Foreground service: fused location + IMU + event detection
│   └── ActivityTransitionReceiver.kt – Activity Recognition → trip start/stop
├── navigation/
│   └── DrivePerfNavigation.kt   – NavHost with all routes
└── ui/theme/                     – Material 3 colour, typography, theme
```

## Scoring model

All scores are in the range [0, 100] where 100 is best.

| Score | What it measures |
|---|---|
| **Safety** | Speeding, hard braking, harsh acceleration, aggressive cornering, phone distraction. Night driving and long trips act as context multipliers. |
| **Smoothness** | Jerk, abrupt acceleration/deceleration, aggressive cornering harshness, excessive stop-start patterns. |
| **Efficiency** | Behavioural proxy: idling time, overspeed duration, repeated harsh acceleration/braking, stop-start cycles. |
| **Trip Quality** | Weighted aggregate (Safety 40 %, Smoothness 30 %, Efficiency 30 %) with signal-confidence adjustment. Displayed as 0–5 ★ in 0.5-star steps. |

## Tech stack

| Component | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| DI | Hilt |
| Local DB | Room |
| Background work | WorkManager |
| Trip detection | Activity Recognition Transition API |
| Location | Fused Location Provider |
| Motion sensors | Android SensorManager (accelerometer + gyroscope) |
| Preferences | DataStore Preferences |
| Permissions UI | Accompanist Permissions |

## Setup

1. Clone the repository.
2. Open in Android Studio Hedgehog or later.
3. Sync Gradle.
4. Run on a device or emulator with Google Play Services (required for Activity Recognition and Fused Location).

> **Note:** Background location (`ACCESS_BACKGROUND_LOCATION`) is requested separately after the user grants foreground location, in compliance with Google Play policy. Auto-detection (the feature that depends on background location) can be disabled in Settings.

## Privacy design

- **Trip-only collection by default.** Location and motion data are only recorded while a trip is active.
- **Local-first.** All data is stored on-device in Room. Cloud sync is an opt-in setting (backend not yet implemented).
- **Transparent recording.** A persistent foreground notification is shown whenever a trip is being recorded.
- **User control.** Users can delete any trip, mark any trip as passenger, and toggle auto-detection off at any time.

## Roadmap

- [ ] Discovery & calibration spike (collect labeled drives, tune thresholds)
- [x] MVP trip lifecycle (this PR)
- [ ] ML-based event detectors (hard braking transformer model)
- [ ] Map display on trip detail screen
- [ ] Cloud sync backend + WorkManager upload worker
- [ ] OBD integration for real efficiency metrics
- [ ] Play Store privacy declaration + beta hardening
