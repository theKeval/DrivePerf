# Architecture guardrails

How ADR-002 section 2 is enforced. CI fails on a violation; review is the fourth line of defence, not the first.

| # | Guardrail | Where | Catches | Runs |
|---|---|---|---|---|
| 1 | Gradle module boundaries | `implementation` instead of `api` in module builds | Compile-time leaks of transitive types | every build |
| 2 | **`assertModuleGraph`** (`com.jraska.module.graph.assertion`) | allow-list in the root `build.gradle.kts`, mirrors the ADR-002 table | Any module-to-module edge that is not listed (for example `:shared:pipeline` → `:shared:presentation`) | `shared.yml`, `android.yml` via the root build |
| 3 | **Konsist** import rules | `shared/testing/src/jvmTest/.../architecture/ArchitectureRules.kt` | Pure layers importing SDKs or platform packages, presentation importing data, pipeline using an ambient clock, Android features importing data or platform, server layers pointing the wrong way | `shared.yml` (`:shared:testing:jvmTest`) |
| 4 | **detekt** | `config/detekt/detekt.yml` | `ForbiddenImport` (SDKs and platform packages in pure layers), bare `catch (e: Exception)`, swallowed exceptions | `shared.yml`, `android.yml`, `backend.yml` |
| 5 | Review | @seniorDev | A new edge needs an ADR-002 amendment | every PR |

## Proof that the rules fire (ADR-002 "Confirmation")

A rule that silently stops working is worse than none, so each one is proven:

- `tools/verify-guardrails.sh` copies the repository to a temporary folder, injects an illegal module edge and a domain class that imports Ktor and catches `Exception`, and requires `assertModuleGraph`, detekt (`ForbiddenImport`, `TooGenericExceptionCaught`) and Konsist (`domainStaysPure`) to fail for the right reason. CI job: `shared.yml` → "Guardrails fire on violations". Your working tree is never touched.
- `ArchitectureRulesSelfTest` runs every Konsist rule against `shared/testing/src/jvmTest/resources/violations/` (one deliberately broken file per rule) and fails if any rule reports nothing. Those files are test resources: never compiled, skipped by the real scan and by detekt.
- `tools/ci/test-check-fixture-headers.sh` does the same for the GPX/CSV privacy-header check (`repo-checks.yml`).

## Changing a rule

Edit the ADR first (an amendment), then the allow-list in `build.gradle.kts`, the Konsist rules and the detekt config together. Never add an exclusion to make a change pass.
