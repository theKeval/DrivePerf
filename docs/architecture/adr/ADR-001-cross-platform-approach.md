# ADR-001: Cross-platform approach — KMP shared core with native SwiftUI and Compose UI

- **Status:** Proposed (for Gate 4)
- **Date:** 2026-11-06
- **Deciders:** Keval Langalia (product owner); A10 (architecture)
- **Related:** [ADR-002](ADR-002-monorepo-modules-and-clean-architecture.md) (modules), [A3 §7](../../research/2026-11/A3-platform-feasibility.md) (KMP feasibility), kickoff plan §3

## Context and problem statement

DrivePerf v1 ships on iOS and Android on the same day (target January 2027, built by one developer with AI agents). The hard part of the product is not the UI: it is the trip state machine, 50–100 Hz signal processing, event detection, Smoothness scoring, route matching, local storage and sync. Those must produce **the same score for the same drive on both platforms**. The UI must feel native: real Liquid Glass on iOS 26+, Material 3 Expressive on Android, Live Activities, Live Updates, widgets and controls. Background sensor capture is platform-native on every stack (A3 §7.1).

## Decision drivers

1. Liquid Glass / M3 Expressive fidelity (Gate 1 design direction; A6 "Night Drive").
2. Motion smoothness (120 Hz, system springs).
3. Background sensor reliability (capture is the #1 competitor complaint, PRD one-page summary).
4. Code sharing and **algorithm parity** across platforms.
5. AI-agent productivity (AGENTS.md already contains a KMP playbook).
6. Hiring (if the team ever grows).
7. Build complexity for a solo developer.

## Considered options

1. **KMP shared core + native UI** (SwiftUI on iOS, Jetpack Compose + M3E on Android).
2. **Compose Multiplatform** (KMP core + shared Compose UI on both platforms).
3. **Flutter.**
4. **React Native / Expo.**
5. **Fully native ×2** (Swift and Kotlin, nothing shared).

## Decision outcome

**Chosen option: 1, KMP shared core + native SwiftUI / Compose UI**, because it is the only option that scores top marks on all three product-critical drivers (fidelity, motion, background capture) while still sharing the algorithm once, which guarantees score parity.

### Scoring (1 = poor, 5 = best; judgement scores, not measurements)

| Criterion (weight) | 1. KMP + native UI | 2. Compose MP | 3. Flutter | 4. RN / Expo | 5. Native ×2 |
|---|---|---|---|---|---|
| Liquid Glass / M3E fidelity (20) | 5 | 2 (no system Liquid Glass on iOS) | 2 | 3 | 5 |
| Motion smoothness (10) | 5 | 4 | 4 | 3 | 5 |
| Background sensor reliability (25) | 5 | 5 | 3 (native code anyway, plus a bridge) | 2 (100 Hz in native modules plus JS) | 5 |
| Code sharing / algorithm parity (15) | 4 (≈ 50–60 % of hard logic, A3 §7.1) | 5 | 5 | 4 | 1 (two DSP implementations to keep identical) |
| AI-agent productivity (15) | 4 | 4 | 4 | 5 | 3 (everything written twice) |
| Hiring (5) | 3 | 3 | 4 | 5 | 4 |
| Build complexity (10) | 2 (Kotlin/Native + Xcode) | 3 | 4 | 3 | 4 |
| **Weighted score (of 5)** | **4.30** | 3.85 | 3.50 | 3.30 | 3.95 |

### Consequences

- Good: one implementation of the pipeline and scoring (A12) is unit-tested once in `commonTest` and runs bit-for-bit the same on both platforms.
- Good: platform surfaces (ActivityKit, WidgetKit, Controls, Live Updates, Glance, FGS) are first-class, with no plugin lag.
- Good: matches the AGENTS.md KMP playbook, so @dev agents need no new conventions.
- Bad: two UI codebases (SwiftUI and Compose). Mitigated by shared presenters (UiState and intents live in Kotlin, [ADR-002](ADR-002-monorepo-modules-and-clean-architecture.md)) so each native screen is a thin view.
- Bad: Swift interop friction. Swift export is Alpha in Kotlin 2.4 (A3 §7.2). We use Objective-C export plus **SKIE** and pin Kotlin to a SKIE-supported version ([ADR-002](ADR-002-monorepo-modules-and-clean-architecture.md)).
- Bad: Kotlin/Native link times and macOS CI runners ([ADR-008](ADR-008-ci-cd-and-environments.md)).
- **Minimum OS (resolves PRD OQ-21):** iOS **26.0+** (Liquid Glass, `CLServiceSession`, `BGContinuedProcessingTask`); Android **10+ (minSdk 29)**, targetSdk 36, moving to 37 when Play requires it. Kotlin/Native's own iOS floor (15.0) is below ours.
- Fallback: if Swift interop blocks the iOS shell for more than one week in A15, the shared core stays and iOS calls it through a thinner, hand-written Swift facade. Switching away from native UI is not a fallback.

### Confirmation

- A13 scaffold: both apps run "Hello DrivePerf" calling shared code, in CI.
- A14: ≥ 90 % unit coverage on `:shared:domain` and `:shared:pipeline` (PRD §7.2).
- Spike 5 (A3): measure Kotlin framework load time on an iOS background relaunch. Core Location sessions are recreated in Swift **before** the Kotlin runtime and Koin start (A3 §7.3 #4).

## Pros and cons of the other options

- **Compose Multiplatform:** stable on iOS since 1.8.0 (May 2025, A3 [K3]), and would share the UI too. It cannot render the system Liquid Glass material or adopt new iOS system components on day one, which the design direction depends on.
- **Flutter:** good productivity and sharing. It draws its own pixels, so Liquid Glass is imitated rather than real, and background capture still needs native code on both platforms behind platform channels.
- **React Native / Expo:** strongest agent productivity (TypeScript) and hiring. Sensor processing at 50–100 Hz in the background must live in native modules anyway, so we would pay for JS *and* native, plus a bridge on the hot path.
- **Native ×2:** best fidelity, no interop. Two implementations of the DSP and scoring will drift, and a score that differs by platform breaks the "baseline-relative Smoothness" promise and fairness guardrails (F-SCO-07).

## More information

Sources (accessed 6 Nov 2026): A3 §7 and its Kotlin sources [K1]–[K5]; SKIE releases, 0.10.15 adds Kotlin 2.4.20 support (https://github.com/touchlab/SKIE/releases).
