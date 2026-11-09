---
applyTo: "apps/android/**"
---

# Android / Kotlin / Jetpack Compose rules (DrivePerf `apps/android/`)

Read `apps/android/README.md` and ADR-002 sections 2 to 5 first. minSdk 29, targetSdk 36, Kotlin, Material 3 Expressive.
The frozen prototype in `legacy/android-prototype/` is reference only: never edit it or copy its architecture.

- **Modules:** `app` (composition root), `designsystem`, `platform` (sensors, foreground service, WorkManager,
  Firebase adapters implementing shared ports) and one `feature-<epic>` per PRD epic. Features see
  `:shared:presentation`, `:shared:domain`, `:shared:foundation` and `designsystem` only; never `data/*`, `platform` or
  Firebase. `assertModuleGraph` fails CI on any other edge.
- **Kotlin idioms:** `val`, data classes, sealed interfaces for state, no `!!`, explicit nullability.
- **Compose:** stateless composables with state hoisted to a `ViewModel` that owns a shared presenter on
  `viewModelScope` and exposes `StateFlow`; collect with `collectAsStateWithLifecycle()`. Material 3 components, theme
  tokens instead of hardcoded dp/sp/colors, `@Preview` for every screen-level composable, strings in `strings.xml`.
- **Coroutines:** structured concurrency only (`viewModelScope`, `lifecycleScope`); no `GlobalScope`; main-safe
  repositories.
- **Design:** `docs/design/<JIRA-KEY>/android/` and `docs/design/screens/android/` are the source of truth; copy the SVG
  assets into resources.
- **Gradle:** versions only in `gradle/libs.versions.toml`; shared conventions live in `build-logic/`. Lint is
  `warningsAsErrors`; justify any `disable` in a comment.
- **Tests:** JUnit + coroutines-test for view models; Compose UI tests and Roborazzi screenshots for key screens once
  they exist. Run `./gradlew :apps:android:app:lintDevDebug :apps:android:app:assembleDevDebug detekt` before pushing.
