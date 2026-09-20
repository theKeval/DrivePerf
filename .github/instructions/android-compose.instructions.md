---
applyTo: "app/**"
---

# Android / Kotlin / Jetpack Compose rules (DrivePerf)

- **Kotlin idioms:** prefer `val`, data classes, sealed interfaces for state, extension
  functions over utility classes; no `!!` — handle nullability explicitly.
- **Compose:** stateless composables with state hoisted to a ViewModel; `@Preview` for every
  new screen-level composable; use Material 3 components; no hardcoded dp/sp where a theme
  token exists; strings in `strings.xml`, never inline.
- **Architecture:** MVVM — UI (Compose) → ViewModel (StateFlow) → repository. Follow the
  existing package structure; one feature per package.
- **Coroutines:** structured concurrency only (`viewModelScope`/`lifecycleScope`); no
  `GlobalScope`; main-safe repositories (`withContext(Dispatchers.IO)` at the data layer).
- **Testing:** unit-test ViewModels and repositories (JUnit + coroutines-test); UI tests only
  where the ticket requires them.
- **Gradle:** version catalog (`libs.versions.toml`) for all dependencies; no hardcoded
  versions in build files.
