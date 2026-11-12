# DrivePerf — instructions for coding agents

These conventions apply to **every coding agent** that works in this repository: Claude Code sessions, Codex,
GitHub Copilot, and any other assistant. DrivePerf owns this file; edit it here. (It was once generated from the
`agentic-workflow` harness; DrivePerf was detached from that harness on 2026-11-11, DRIVE-210. Never run
`agentic-workflow/install/sync-instructions.sh` against this repository: it would overwrite these files.)

## Identifiers & systems

- **Jira:** `https://thekeval.atlassian.net`, project **DrivePerf**, key **`DRIVE`**,
  agile **board id 2**. Ticket keys look like `DRIVE-12`.
- **Jira statuses (only these four):** `To Do` (transition id 11) → `In Progress` (21) →
  `In Review` (31) → `Done` (41). Transitions are any-to-any.
- **Design flag:** the Jira label **`design-needed`** marks tickets requiring design work.
- **Pilot product repo:** `theKeval/DrivePerf` (GitHub, public) — monorepo: Kotlin
  Multiplatform core, SwiftUI iOS app, Jetpack Compose Android app, Ktor backend and Astro
  website. Default branch: `main`.
- **Product identity:** **DrivePerf**, domain **`driveperf.com`**, app ID
  **`com.driveperf.app`** on both stores (dev builds `com.driveperf.app.dev`); Kotlin packages `com.driveperf.*`.
- **Who decides:** Keval (they/them) approves every merge and anything outward-facing or irreversible. Work is
  coordinated from one place (Keval, or the coordinating Claude Code session), which assigns tickets, merges in
  order and keeps Jira up to date.

## Universal rules

1. **Jira key everywhere.** Any GitHub Issue, PR, branch, or commit related to a ticket MUST
   carry the Jira key (format `DRIVE-<number>`) — issues/PRs in the **title**, commits in the
   **message**, PRs also in the **description**. Avoid apostrophes in PR titles.
2. **Jira is the source of truth** for work state. There is no automation that moves tickets: the coordinator moves
   them (In Progress when work starts, Done after merge) and comments the outcome. Agents without Jira access report
   their outcome in the PR description instead.
3. **The human's instruction wins.** If Keval redirects you, act on it and record the outcome in the PR or ticket.
4. **Never loop.** If you find yourself repeating a failed approach twice, stop and report what failed instead of
   retrying indefinitely.
5. **Stay in scope.** Only work on the ticket and files you were assigned. Other agents often work in parallel; never
   start unrelated work, refactors, or "improvements" outside the ticket's scope.
6. **Never merge your own PR, push workflow changes, or commit secrets.** Workflow files under `.github/workflows/`
   are pushed by Keval.

## DrivePerf monorepo (this repository)

One repository holds the shared core, both apps, the server and the website. Layout and dependency rules are
fixed by `docs/architecture/adr/ADR-002-monorepo-modules-and-clean-architecture.md`; read it before adding a
module or a dependency edge. Path-specific instruction files in `.github/instructions/` add the per-area rules.

| Path | What | Stack | Extra rules |
|---|---|---|---|
| `shared/` | KMP core: foundation, domain, pipeline, presentation, data/*, api-contract, di, ios-export, testing | Kotlin Multiplatform | `shared.instructions.md` |
| `apps/ios/` | iOS app (Xcode project, local SPM packages) | SwiftUI | `ios.instructions.md` |
| `apps/android/` | Android app (app, designsystem, platform, one `feature-*` per epic) | Jetpack Compose | `android.instructions.md` |
| `backend/api/`, `backend/openapi/` | Server and its HTTP contract | Kotlin / Ktor | `backend.instructions.md` |
| `backend/firebase/` | Firebase config and Firestore rules | npm | |
| `web/` | driveperf.com | Astro | `web.instructions.md` |
| `docs/`, `prototypes/` | Specs, ADRs, design artifacts, HTML prototypes | | |
| `legacy/android-prototype/` | Frozen Android-only prototype: reference only, never edit | | |
| `spikes/` | Throw-away experiments: not part of the Gradle build or CI | | |

- **One root Gradle build** (`./gradlew`, JDK 21 toolchain) covers `shared/`, `apps/android/` and `backend/api/`.
  iOS is an Xcode project that builds the Kotlin framework through its Run Script phase. Versions live in
  `gradle/libs.versions.toml`; Kotlin moves only together with SKIE.
- **Boundaries are enforced in CI**, not by review alone: `./gradlew assertModuleGraph detekt :shared:testing:jvmTest`
  (module graph, forbidden imports, Konsist). Never weaken a rule to get a change through; a new module edge needs an
  ADR-002 amendment first. See `docs/architecture/guardrails.md`.
- **Platform targets:** minimum iOS 26 and Android 10 (minSdk 29); app ID `com.driveperf.app` (dev
  `com.driveperf.app.dev`); Kotlin packages `com.driveperf.*`. Crash reports go to Sentry (EU); Crashlytics is never
  linked. Trip files go to Cloudflare R2, so there are no Firebase Storage rules.
- **The website deploys from `main`:** a push touching `web/**`, `backend/firebase/**` or
  `.github/workflows/web.yml` deploys driveperf.com to production. Leave those paths alone unless the ticket is about
  the website or Firebase config.
- **iOS builds are simulator-only for now.** Never pick a Personal Team for `com.driveperf.app` or
  `com.driveperf.app.dev`: it can block those IDs for the paid account.
- **No secrets and no real location data in the repo.** GPX/CSV fixtures must be synthetic or privacy-trimmed and carry
  a `# privacy-trimmed` header (CI rejects them otherwise). Release workflows are inert until their accounts exist
  (`docs/architecture/ci-cd.md`).
- **npm on Keval's Mac** is not on the PATH: run `node /opt/homebrew/lib/node_modules/npm/bin/npm-cli.js <args>`
  (`npx-cli.js` for npx) and install each project's own dependencies.

---

# Implementing a ticket

You implement one Jira ticket (or one assigned stream of tickets) as a senior software engineer. Your brief comes
from Keval or the coordinating session; the ticket's acceptance criteria are in `docs/product/PRD-v1.md` (search for
its `F-` code) and its designs in `docs/design/<JIRA-KEY>/`.

## MANDATORY: Jira key in everything

The issue/PR title carries a Jira key like `DRIVE-12`. You MUST:
- Include the Jira key in **every commit message** (e.g. `DRIVE-12: add trip list screen`).
- Include the Jira key in the **PR title AND the PR description**.
- If **you** create the branch (CLI/local contexts), name it `<key>-short-slug`
  (e.g. `DRIVE-12-trip-list`). (Cloud coding agent branches are platform-named — skip.)
- Never open a PR without it: the coordinator tracks and merges work by this key.

## Implementation rules

1. **Read the whole issue first** — description, acceptance criteria, attached designs.
   Implement exactly what's asked; nothing more. If the issue is ambiguous or infeasible,
   comment on the issue explaining what's unclear instead of guessing wildly.
2. **Check for design artifacts at `docs/design/<JIRA-KEY>/<ios|android|web>/` in the repo** (also linked
   from the issue's Design section when one exists). If present, they are the source of
   truth for UI work: `design-spec.md` lists every screen and state, with links to the exact target UI
   images in `docs/design/screens/<platform>/`, and the interactive prototype (`prototypes/<platform>/index.html#<SCREEN-ID>/<state>`)
   shows behaviour and motion; `mockup.webp` = a contact sheet of the story; `design-spec.md` = exact color
   tokens, spacing, and type scale — use these values verbatim, do not approximate;
   `assets/*.svg` = real assets — copy them into the app's resources and use them, do not
   recreate icons; `mockup.html` = reference implementation to consult for layout/CSS
   intent. Match the mockup as closely as the platform allows.
3. **Work like a senior engineer:** small focused commits, meaningful messages, tests for
   new logic, run existing tests/lint before opening the PR.
4. **Match the codebase.** Detect the project's stack and existing patterns first; follow
   them. Path-specific instruction files (`.github/instructions/`) override this file.
5. **PR description must include:** the Jira key, what changed, how it was verified
   (tests run, screenshots for UI), and any acceptance criterion you could NOT meet with
   the reason.
6. **Open the PR as a draft.**

## Clean architecture (all projects)

Layer every feature as **Presentation → Domain → Data**, dependencies pointing inward only:

- **Domain** (pure business logic): entities, use cases, repository *interfaces*. No
  framework, UI, or I/O imports. This layer is unit-tested the most.
- **Data**: repository *implementations*, remote/local data sources, DTOs + mappers.
  DTOs never leak past this layer — map to domain models at the boundary.
- **Presentation**: UI + state holders (ViewModel/store). Talks to domain use cases only;
  never calls data sources directly.
- **Unidirectional data flow:** UI emits events → state holder updates immutable state →
  UI re-renders. Single source of truth per screen.
- **Dependency injection** for wiring (constructor injection preferred); no service
  locators or globals.
- Small projects may collapse layers (e.g. skip use cases for pass-through calls) — keep
  the dependency direction even then.

## Universal design rules

- Single responsibility per class/function/component; composition over inheritance.
- Handle errors explicitly at boundaries (sealed results/typed errors); no silent catches.
- No dead code, no commented-out blocks, no TODOs without a Jira key.
- New dependencies require a stated justification in the PR description.
- Immutability by default; side effects isolated and named.

## Platform playbooks — detect the stack, apply the matching one

**Android (Kotlin + Jetpack Compose)** — follow official Android architecture guidance:
stateless composables, state hoisted to ViewModel exposing `StateFlow`/`UiState`;
Material 3; structured concurrency (`viewModelScope`, main-safe repositories); Room/Retrofit
(or as codebase dictates) behind repository interfaces; Hilt/Koin DI; version catalog for
dependencies; no `!!`.

**iOS (Swift + SwiftUI)** — MVVM with `@Observable`/`ObservableObject` state; protocol-based
domain boundaries; Swift concurrency (`async/await`, actors — no completion-handler
pyramids); value types (structs) by default; follow Apple HIG for UX behavior; XCTest for
domain/ViewModel logic.

**Kotlin Multiplatform** — share **domain, pipeline, presentation and data** in `shared/`
(`expect/actual` for platform code); keep UI native (Compose / SwiftUI) and thin; Ktor/SQLDelight-style
multiplatform libs in shared code; Koin for DI; unit tests live in the shared module. DrivePerf's module
layers and allowed dependencies are in ADR-002 (see "DrivePerf monorepo" above).

**Cross-platform JS (React Native)** — TypeScript strict; function components + hooks;
business logic in plain TS modules (testable without the renderer); navigation and platform
APIs behind adapters; avoid heavy re-renders (memoize deliberately, not everywhere).

**Web (React/Next.js/Vue)** — TypeScript strict everywhere; component-driven structure with
colocated tests/styles; server state via query libs (TanStack Query/SWR), UI state local
first, global stores (Zustand/Pinia) only when truly global; semantic HTML + keyboard
accessibility + WCAG AA contrast; optimize Core Web Vitals (code-splitting, image
optimization, SSR/SSG where the framework provides it).

## Review and delivery

The Codex review bot comments on every PR that is marked ready for review (it skips drafts).
1. Open the PR as a draft, get CI green, then mark it ready.
2. Read **ALL** unresolved review threads first and treat them as one prioritised batch.
3. You MAY decline a suggestion — reply in that thread with a concise technical rationale.
4. Push the fixes as commits on the same PR branch (Jira key in each commit message); never force-push.
5. Reply to each thread briefly (what changed / why declined).
6. Report the PR number, head SHA and anything still to check on a real device to the coordinator. Don't merge.

## Status updates

Nothing mirrors your PR activity to Jira or Slack automatically. Keep PR descriptions and comments professional and
informative like a real engineer's status update: what changed, how it was verified, and any acceptance criterion you
could not meet, with the reason. The coordinator copies the outcome to the Jira ticket.
