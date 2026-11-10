---
applyTo: "shared/**"
---

# Kotlin Multiplatform core rules (DrivePerf `shared/`)

Read `docs/architecture/adr/ADR-002-monorepo-modules-and-clean-architecture.md` and `docs/architecture/guardrails.md` first.

- **Layers and edges are fixed** by the ADR-002 table and enforced in CI by `assertModuleGraph` (allow-list in the
  root `build.gradle.kts`), Konsist (`shared/testing`) and detekt `ForbiddenImport`. A new module edge needs an ADR
  amendment, never a rule change. Use `implementation`, not `api`, except where `:shared:ios-export` re-exports.
- **Pure layers stay pure:** `foundation`, `domain`, `pipeline` and `presentation` import no SQLDelight, Ktor, Wire,
  kotlinx.serialization, Firebase, RevenueCat, `platform.*` or `android.*`. `pipeline` has no I/O and no ambient clock
  (inject `Clock`). Platform code belongs in `androidMain` / `iosMain` actuals or in the apps.
- **Ports speak domain types only.** Ports implemented in Swift must not use `suspend` or `Flow` in their signature;
  use callback-style `*Bridge` protocols in `iosMain` and wrap them in Kotlin (ADR-002 section 3).
- **Errors:** `Outcome` / `DomainError`, never free text that could carry location. Catch narrow exception types at
  adapter boundaries; no bare `catch (e: Exception)` (detekt fails it); always rethrow `CancellationException`.
- **Public API:** `explicitApi()` is on, so every public declaration needs a visibility modifier and an explicit type.
  Keep the public surface minimal; Swift sees whatever `:shared:ios-export` exports.
- **Tests:** `commonTest` with `kotlin-test`, fakes from `:shared:testing`. At least 90 % line coverage on `domain`
  and `pipeline` (`./gradlew koverVerify`). Recorded-drive fixtures are synthetic or privacy-trimmed and start with a
  `# privacy-trimmed` header.
- **Before pushing:** `./gradlew assertModuleGraph detekt jvmTest testAndroidHostTest koverVerify`
  (`iosSimulatorArm64Test` needs an iOS simulator runtime and runs in CI).
