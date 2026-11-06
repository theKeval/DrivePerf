# DrivePerf v1 — Kickoff Plan & Agent Prompts

**Prepared:** 1 November 2026 · **Updated:** 6 November 2026 (Phase 0 done; hosting and account decisions recorded below)

> **Decisions on 6 Nov 2026** (see also `docs/product/decisions.md`)
> - **Order is strict:** PRD → Jira tickets → prototypes approved → development. Architecture docs (Phase 4) may be written alongside prototyping, but no application code is written before Gate 3.
> - **Hosting and backend:** a new Firebase project **"DrivePerf"** on the free **Spark** plan. Firebase Hosting serves the website. Namecheap remains only the domain registrar, and the custom domain is connected to Firebase Hosting later.
> - **Accounts:** a Google Play Console account exists. The Apple Developer account is bought later. Until then, iOS work runs on the simulator; TestFlight, device builds with background capabilities, and Sign in with Apple wait for it.
**Product:** DrivePerf · **Domain:** driveperf.com · **Jira:** [DRIVE](https://thekeval.atlassian.net/jira/software/projects/DRIVE/summary) · **Repo:** [theKeval/DrivePerf](https://github.com/theKeval/DrivePerf)

---

## 1. Where things stand today

| Area | Finding |
|---|---|
| **Local workspace** | `DrivePerf/` (outer, not a git repo) holds `resources/` (research) and `DrivePerf/` (the Android git repo). A second worktree exists at `../copilot-worktrees/DrivePerf` on branch `copilot/drive-8-allow-users-to-choose-units` (already merged). |
| **Git sync** | *Resolved in Phase 0 (DRIVE-11).* The uncommitted DriveStats → DrivePerf rebrand (79 files) was merged in PR #11 and tagged `android-prototype-v0`. |
| **Stale branches** | *Resolved in Phase 0.* Six merged branches and the stale `copilot-worktrees` checkout were removed; only `main` remains. |
| **App** | An Android-only prototype: Kotlin, Compose, Material 3, Hilt, Room, WorkManager, Activity Recognition, Fused Location, and a foreground service. It has rule-based Safety, Smoothness, Efficiency and Trip Quality scores, onboarding, permissions, trips, a stats dashboard, settings (units), About, and 49 unit tests. There is no backend, no map and no iOS app. |
| **Research** | `resources/research/drive-perf/2026-09-17/` sets out a two-audience direction (enthusiasts + improvers), covers 20+ competitors, Android feasibility and privacy (PIPEDA, Play policy), and identifies Cosworth PDR as the long-term inspiration. It lists iOS research as **not done**. Score weights and feature tiers are **not approved**. |
| **Jira DRIVE** | Only 6 issues: Epic DRIVE-5 "App information & settings" (In Progress) → DRIVE-6/7/8 Done; Epic DRIVE-9 "Driving Insights & Analytics" (To Do) → DRIVE-10 Done. There is no PRD and no v1 backlog. |
| **Agentic workflow** | `AGENTS.md` / `.github/` define @PM, @dev, @seniorDev and @Designer agents, Jira-key rules, `docs/design/<KEY>/` artifacts, and `jira-sync` / `review-router` GitHub Actions. Keep these. They already include KMP and SwiftUI playbooks. |
| **driveperf.com** | A **Namecheap parking page** that 302-redirects to `www`. There is no site yet. The site will be hosted on Firebase Hosting, and DNS will later point there from Namecheap. |

**Conclusion:** keep the research, the Jira project, the GitHub repo and the agent workflow. Retire the Android code as a reference prototype. The v1 product is a fresh, cross-platform build.

---

## 2. My product analysis — who v1 is for

### 2.1 The wedge
The research names two audiences. A v1 that serves both equally will serve neither well. My recommendation is **one hero audience, one hero metric**:

- **Hero user (v1): the everyday driving enthusiast.** They are 22–45, own a car they care about (GTI, Civic Si/Type R, WRX, Miata, Mustang, a Tesla/Ioniq/EV), drive daily, love data and visuals, and share on Reddit, Instagram and Discord car communities. They already use Strava, Whoop or Apple Fitness, so they expect *beautiful recaps and progress*. Today they tolerate dated-looking tracker apps.
- **Secondary user (v1): the self-improver.** This includes efficiency-minded and EV range-conscious drivers and new adult drivers. They come through the *same* data in a simpler "coach" view.
- **Hero metric: Smoothness.** It is the one score both audiences value. Enthusiasts read it as car control and improvers read it as comfort and efficiency. It is **safe to gamify**, unlike speed or acceleration times on public roads, which carry App Store, liability and ethics risk. Efficiency is presented as a behavioural proxy and never as litres or kWh.

**Positioning line (to test):** *"Every drive, decoded."* — Strava-grade recaps for your daily drive, with no speed contests.

### 2.2 What makes users install, stay and share
| Loop | v1 mechanism |
|---|---|
| **Zero-effort capture** | Auto trip detection plus a one-tap manual start, a Live Activity / Dynamic Island on iOS and a Live Update notification on Android. If capture is unreliable, nothing else matters. This was the #1 complaint across competitor reviews. |
| **"Wow" moment after the first drive** | An animated **Drive Story**: route draw-on, a G-meter replay, the smoothness reveal, one highlight and one tip. The target is under 3 seconds to first meaningful paint. |
| **Depth on demand** | The **Analysis** view, with a map, a synchronized speed/longitudinal/lateral-G timeline, a G-G friction circle, event evidence and a data-quality badge. "One drive, two depths" comes from the research. |
| **Progress** | **Routes**: repeated drives (such as the commute) are grouped automatically, so users see "Smoothest commute this month", weekly trends and smoothness personal bests. |
| **Share (growth loop)** | Privacy-safe **share cards** (the start and end of the route are trimmed and the stats are stylised) built for IG Stories and Reddit. Speed is never the hero stat. |
| **Habit** | A weekly recap push and a monthly "Drive Wrapped". |
| **Trust** | Local-first storage, an explicit privacy centre, delete anything, export (GPX/CSV), and honest "observed / derived / estimated" labels. |

### 2.3 Proposed v1.0 scope (for the PRD to confirm)
**In:** onboarding with progressive permissions · manual + automatic recording · mount/calibration + data-quality state · Drive Story · Analysis view · trip history & stats dashboard · Smoothness / Efficiency-proxy / Events scores · Routes & personal bests · per-trip coaching tip · Garage (multi-vehicle) · not-driver/passenger marking · share cards · Sign in with Apple/Google · optional cloud backup · export & delete · units/settings · weekly recap · landing page + waitlist.

**Out of v1 (roadmap):** insurance anything · OBD-II/BLE hardware · track/lap timing and 0-100 timing · leaderboards and social feed · crash detection · phone-distraction scoring (needs its own accuracy and privacy study) · CarPlay/Android Auto · web dashboard (v1.1) · ML detectors.

**Business model (to validate):** freemium. **Free** includes unlimited recording, the Drive Story, 30-day history and basic stats. **DrivePerf Pro** (target about $4.99/mo or $34.99/yr) adds full history, the Analysis workspace, Routes comparison, exports, cloud backup and Wrapped. Launch geography: **Canada + US** (English), with metric and imperial units.

**North-star metric:** weekly active recorders (users with ≥3 recorded trips in a week). **Activation:** first trip recorded and its Story viewed within 48 h of install.

### 2.4 Biggest risks (each has an owner agent below)
1. **iOS background capture reliability** (CoreMotion automotive detection, significant-change/visit wakeups, `CLServiceSession`). This is a different problem from Android and must be validated early (A3).
2. **Battery drain**. The target is ≤ 4–6 %/hour of recording on mid-tier devices. This is to be verified in field tests.
3. **Store review**: Apple guideline 2.5.4 (background location justification), the Google Play background-location declaration, and the **Jan 2027 Play location-policy change** flagged in the existing research.
4. **Driving-safety liability**: no interaction is required while moving, the UI locks while driving, and the product never celebrates speed.
5. **Phone-in-car motion noise** (pocket, cupholder): orientation correction and "unscorable" states.

---

## 3. Recommended tech stack (default for A10 to challenge or confirm)

| Layer | Recommendation | Why |
|---|---|---|
| **Mobile architecture** | **Kotlin Multiplatform (KMP)** shared core + **fully native UI** | You want *true* Liquid Glass on iOS and Material 3 Expressive on Android, with motion that feels native. Only native UI delivers both. The hard part of this app (the trip state machine, signal processing at 50–100 Hz, scoring, sync and the DB) is shared once in Kotlin. Sensors and background execution are platform-native on every stack anyway. This also matches the KMP playbook already in `AGENTS.md`. |
| iOS UI | **SwiftUI** (iOS 26+ target, Liquid Glass via `glassEffect` / system components), ActivityKit Live Activities, WidgetKit, MapKit, Swift Charts | Platform-native glass, Dynamic Island and smooth 120 Hz motion |
| Android UI | **Jetpack Compose + Material 3 Expressive** (motion physics, shape morphing, dynamic color), Live Update notifications, Glance widgets, Google Maps Compose / MapLibre | The current Android design language |
| Shared core | Kotlin 2.x, Coroutines/Flow, **SQLDelight** (or Room KMP), Ktor client, kotlinx.serialization, Koin | Proven KMP stack; the scoring logic is unit-tested once |
| Platform sensors | iOS: CoreLocation (`CLLocationUpdate`, `CLServiceSession`), CoreMotion (`CMDeviceMotion`, `CMMotionActivityManager`). Android: Fused Location, Activity Recognition Transition API, SensorManager, FGS `location` type | |
| Maps | MapKit (iOS) + Google Maps SDK (Android) for v1. Evaluate MapLibre + a vector tile provider for one shared style and cost control | |
| **Backend v1** | **Firebase project "DrivePerf"**, starting on **Spark (free)**: Auth (Apple/Google/email), **Firestore for trip summaries and user documents**, Hosting, FCM, Crashlytics, Analytics, Remote Config, App Check. **Needs the Blaze plan:** Cloud Storage (compressed trip files) and Cloud Functions (TypeScript jobs). | Spark covers prototyping, the website and the waitlist, which writes directly to Firestore under create-only security rules. Upgrade to **Blaze** (pay-as-you-go, which keeps the same no-cost quotas) with a budget alert of about $5 only when cloud backup or Functions are built (A17). A few thousand DAU should stay near the free quotas. |
| **Migration seam** | Clients talk to Firebase only through a `SyncRepository` interface in the KMP core. Raw trips are stored as **portable files** (protobuf or gzip NDJSON, versioned schema), not as Firestore documents. User IDs are Firebase UIDs that a self-hosted auth server (such as Supabase or Keycloak) can import. Each Firestore collection is documented as a SQL table in the data-model ADR. | Moving to a Hostinger VPS (Postgres + MinIO/S3 + a Ktor or Node API) is then a data copy plus a new repository implementation, with no client rewrite. **A VPS is not needed for v1.** |
| Web | **Astro** static site (landing, privacy, terms, support, waitlist) on **Firebase Hosting** (Spark), deployed from GitHub Actions. `driveperf.com` is connected later through Namecheap DNS records. The v1.1 account dashboard can use Next.js on Firebase App Hosting, which needs Blaze. | Free plan, global CDN, automatic SSL, preview channels for each PR |
| Payments | RevenueCat (StoreKit 2 + Play Billing) | One entitlement model across both stores |
| Repo | Monorepo: `shared/` (KMP), `apps/ios/`, `apps/android/`, `backend/` (Firebase), `web/` (Astro), `prototypes/`, `docs/` | One place for agents; Jira-key automation keeps working |
| CI/CD | GitHub Actions (shared tests, Android build, web deploy) + Xcode Cloud or a macOS runner with Fastlane for TestFlight and Play internal testing | |

**Alternatives considered:** Flutter (it cannot render real Liquid Glass and its platform-specific cosmetics fight the framework) and React Native/Expo (it now has glass/native-tab support, but background sensor processing at 100 Hz would still have to live in native modules, so you would pay for JS plus native). Both are valid fallbacks if the team skills change.

---

## 4. Execution plan

Each **phase gate** needs your approval before the next phase starts. Prompts in the same phase marked ∥ can run in parallel.

| Phase | Agents | Output | Rough duration |
|---|---|---|---|
| **0. Housekeeping** ✅ done 6 Nov (DRIVE-11) | — | Rebrand committed, legacy tagged, monorepo layout, `docs/` folders | 1 day |
| **1. Discovery** | A1 ∥ A2 ∥ A3 | Market refresh incl. iOS, personas/positioning/pricing, iOS+Android feasibility | 3–5 days |
| **2. PRD & backlog** | A4 → A5 | `docs/product/PRD-v1.md`, Jira epics and stories | 2–3 days |
| **3. Design & prototypes** | A6 → (A7 ∥ A8 ∥ A9) | Design system + motion language; iOS, Android and web HTML prototypes | 1–2 weeks |
| **4. Architecture** (documents only; can overlap phase 3) | A10 ∥ A11 ∥ A12 | ADRs, data model + migration plan, sensor/scoring algorithm spec | 1 week |
| **5. Foundations build** | A13 → (A14 ∥ A15 ∥ A16 ∥ A17 ∥ A18) | Monorepo + CI, shared core, app shells, Firebase, live landing page | 3–4 weeks |
| **6. Feature build → field test → beta** | @PM/@dev workflow from Jira tickets + A19 field-test protocol | TestFlight + Play internal testing, calibration data | 6–10 weeks |
| **7. Launch readiness** | A20 | Store listings, privacy policy, data-safety forms, ASO | 1 week |

### Phase 0 — housekeeping ✅ done 6 Nov 2026 ([DRIVE-11](https://thekeval.atlassian.net/browse/DRIVE-11))
1. The pending rebrand was committed and merged ([PR #11](https://github.com/theKeval/DrivePerf/pull/11)).
2. The result was tagged `android-prototype-v0`.
3. Six merged branches and the `copilot-worktrees/DrivePerf` worktree were removed.
4. Monorepo layout on `main`: the old app is in `legacy/android-prototype/`, and `docs/{research,product,design,architecture,planning}/` and `prototypes/` were added.
5. The Sep 2026 research was copied into `docs/research/2026-09/`.

**Follow-up for A13:** `AGENTS.md`, `.github/copilot-instructions.md` and `.github/instructions/android-compose.instructions.md` (`applyTo: "app/**"`) are generated from the `agentic-workflow` masters. They still describe the Android-only repo. Update the masters and re-sync when the monorepo code folders are created.

### Model guidance
- **Creative and strategy** (A2, A6, A7–A9 visual direction, naming and copy): the most capable creative model available, such as **Claude Opus 5.5** or **Claude Fable 5.1**, or your preferred frontier model.
- **Research** (A1, A3, A19): a frontier model with web search and deep-research tools (Opus 5.5 with deep research).
- **Architecture and hard algorithms** (A10–A12, A14): **Claude Opus 5.5**.
- **Bulk implementation** (A13, A15–A18, ticket work): **Claude Sonnet 5.5** or Opus 5.5 in Claude Code or your Copilot coding agent, with reviews by an Opus-class @seniorDev.

---

## 5. Agent prompts (copy-paste ready)

> Every prompt assumes the agent can read the repository `theKeval/DrivePerf` after Phase 0. If an agent runs locally, the workspace is `/Users/keval/data/projects/DrivePerf/`. Shared rules for every agent: cite sources with dates; mark vendor claims as claims; never invent statistics; write outputs only to the stated path; carry the Jira key in commits once tickets exist; read `AGENTS.md`.

---

### A1 — Market & competitor refresh (iOS + Android) · *Research model* · Phase 1 ∥

```
You are a senior mobile product researcher. Product: DrivePerf (driveperf.com), a phone-only driving
analytics app for iOS and Android. Target: everyday driving enthusiasts (hero) and self-improvers
(efficiency/EV-range/new adult drivers). Launch markets: Canada + US.

Read first: docs/research/2026-09/01-android-market-research.md and its evidence appendices.
That research is Android-only and dated Sep 2026. Your job is to EXTEND it, not repeat it.

Tasks:
1. iOS App Store landscape (US + CA): find the 15 most relevant driving-analytics / trip-tracker /
   driving-coach apps (e.g. SpeedRank, Drive Sense, Open Road, DrivingTracker, RaceChrono, Harry's,
   DriveSidekick, Zenroad, Velox, mileage trackers that do auto-detection). For each: bundle ID,
   developer, price/IAP, rating & count, last update, auto-detect?, live activity?, replay?, scores,
   share features, privacy label summary.
2. Re-check the top 6 Android competitors for changes since Sep 2026.
3. Review mining: sample ~50 recent reviews (mixed stars) for the top 6 cross-platform competitors.
   Code them: capture reliability, battery, accuracy, UI/design, coaching usefulness, privacy,
   price, sharing. Report counts within your sample only.
4. Adjacent inspiration: what Strava, Whoop, Apple Fitness, Oura, Spotify Wrapped do for
   recaps/progress/sharing that a driving app could adopt (no clones; patterns only).
5. Market sizing signals: enthusiast community sizes (subreddits, forums, Discords), EV owner
   counts CA/US, any public download estimates for competitors (label source + confidence).
6. Gap matrix: features × competitors, highlight white space for "Strava-grade recaps +
   smoothness coaching + privacy-first + platform-native design".

Output: docs/research/2026-11/A1-market-refresh.md (exec summary ≤ 1 page first, then tables,
then sources). Done when every competitor row has a source URL and access date.
```

### A2 — Personas, positioning, pricing & growth loops · *Creative/strategy model* · Phase 1 ∥

```
You are a product strategist and brand thinker with consumer-app experience (Strava, Duolingo,
Whoop-level retention design). Product: DrivePerf — "Every drive, decoded." (working line).

Read: docs/research/2026-09/01-android-market-research.md, 02-next-phase-research-plan.md,
and section 2 of docs/planning/DrivePerf-v1-kickoff-plan.md (hero user = everyday enthusiast,
hero metric = Smoothness, no speed glorification, no insurance in v1).

Deliver:
1. 4 personas (2 enthusiast sub-types, 2 improver sub-types) with JTBD, triggers, anxieties,
   "moment of delight", and what would make each uninstall.
2. Positioning: 3 candidate value propositions + taglines, a recommended one, and the
   one-sentence App Store subtitle (≤30 chars) and Play short description (≤80 chars).
3. Signature experiences: define the Drive Story, Routes/personal bests, weekly recap and
   Drive Wrapped as narrative storyboards (frame by frame, what animates, what copy says).
4. Growth loops: share-card concepts (privacy-safe), referral idea, community seeding plan for
   r/cars-type communities, launch-day plan. Flag anything that could encourage unsafe driving.
5. Monetization: free vs Pro split, price points for CA/US, trial strategy, paywall moment.
   Justify with comparable apps' public pricing.
6. Brand voice guide (tone, 10 do/don't copy examples, notification copy samples).
7. Metrics: north star, activation, D1/D7/D30 retention targets with benchmark rationale.

Output: docs/product/A2-strategy-positioning.md. Be opinionated; give one recommendation per
decision plus the runner-up.
```

### A3 — iOS + Android technical feasibility (background, sensors, store policy) · *Research model* · Phase 1 ∥

```
You are a principal mobile engineer specialising in location/sensor apps on iOS 26/27 and
Android 15–17. Product: DrivePerf — records car trips from phone GPS + IMU, auto-detects
trips, computes smoothness/efficiency proxies/events, local-first.

Read: docs/research/2026-09/android-feasibility-evidence.md (Android done, iOS not).

Answer with official documentation links (Apple Developer, Android Developers, App Review
Guidelines, Google Play policy), dated:
1. iOS automatic trip detection: CMMotionActivityManager automotive confidence, significant
   location change, visits, region monitoring, CLLocationUpdate/CLServiceSession/
   CLBackgroundActivitySession, "Always" authorization flow, how fast can recording start after
   the car moves, what happens after force-quit / reboot / Low Power Mode. Cite how existing
   apps achieve it if documented.
2. iOS sensors in background: CMDeviceMotion rates while backgrounded with active location,
   batching, memory/CPU limits, BGTaskScheduler for post-trip processing.
3. Live Activities / Dynamic Island for in-progress trip: update limits, push vs local updates.
4. Android 15–17 deltas: FGS type location rules, background start restrictions, Activity
   Recognition reliability, Live Update (progress) notifications, the Jan 2027 Play location
   policy change, OEM battery killers (Samsung/Xiaomi) mitigations.
5. Store review risk: Apple 2.5.4 / 5.1.1 background location justification, Play background
   location declaration + video, Data Safety / Privacy Nutrition label implications, driving
   safety (no interaction while moving) expectations.
6. Battery: realistic %/hour estimates for GPS 1 Hz + IMU 50 Hz on mid-tier devices, and
   duty-cycling strategies.
7. KMP feasibility: confirm the sensor/background layer must be native per platform and what
   can be shared (state machine, filters, scoring, storage, sync). Note any KMP/Swift interop
   pitfalls (coroutines↔async, SQLDelight drivers, background threads).
8. A parity matrix: each user-facing promise × iOS × Android → same / degraded / impossible.

Output: docs/research/2026-11/A3-platform-feasibility.md + a 1-page "risks & spikes" list with
the 5 riskiest assumptions and a proposed 1-2 day spike to prove each.
```

---
**🚦 Gate 1 (you):** review A1–A3. Confirm the hero user, the hero metric, the v1 scope and pricing.

---

### A4 — PRD v1 author · *Opus 5.5* · Phase 2

```
You are a senior product manager. Write the Product Requirements Document for DrivePerf v1.0
(iOS + Android, Canada + US).

Inputs (read all): docs/research/2026-09/*, docs/research/2026-11/A1-market-refresh.md,
docs/product/A2-strategy-positioning.md, docs/research/2026-11/A3-platform-feasibility.md,
docs/planning/DrivePerf-v1-kickoff-plan.md, and any Gate-1 decisions recorded in
docs/product/decisions.md.

PRD structure:
1. Problem, vision, positioning, target users (personas by reference), non-goals.
2. Success metrics (north star, activation, retention, quality: capture success rate,
   false-trip rate, battery/hour, crash-free sessions ≥ 99.5%).
3. Feature list grouped into epics. For each feature: user story, priority (P0/P1/P2),
   acceptance criteria (Given/When/Then), platform notes (iOS/Android differences),
   analytics events, edge cases (no GPS, tunnel, phone moved, passenger, low battery,
   permission revoked), privacy notes, Free vs Pro.
   Epics to cover at minimum: Onboarding & Permissions; Trip Capture (manual/auto/live
   activity); Data Quality & Calibration; Drive Story; Analysis Workspace; Scores & Coaching;
   History & Stats; Routes & Personal Bests; Garage; Sharing; Account & Cloud Backup;
   Privacy Centre, Export & Deletion; Subscription & Paywall; Notifications & Recaps;
   Settings; Web landing & waitlist; Analytics & Crash reporting.
4. Metric provenance table: every number shown to users labelled observed / derived /
   estimated, with its formula owner (A12 will specify algorithms).
5. Safety & compliance requirements (no interaction while moving, store policies, PIPEDA,
   US state privacy laws, age 18+ for v1).
6. Release plan: v1.0 (MVP), v1.1, v1.2 and a "later" bucket (OBD, track mode, web dashboard,
   leaderboards, CarPlay/Android Auto, ML detectors).
7. Open questions with owner and due date.

Output: docs/product/PRD-v1.md. Keep it implementable: every P0 must be testable.
```

### A5 — Jira backlog builder (DRIVE) · *Sonnet 5.5 with Atlassian MCP* · Phase 2 (after PRD approved)

```
You are the @PM agent. Using the Atlassian MCP, populate Jira project DRIVE
(https://thekeval.atlassian.net, key DRIVE, board 2, statuses To Do/In Progress/In Review/Done)
from docs/product/PRD-v1.md.

Rules:
- First, list existing issues. Close or relabel legacy ones: epics DRIVE-5 and DRIVE-9 and their
  children belong to the Android prototype. Add the label `legacy-prototype` and a comment
  "superseded by v1 PRD". Do not delete them.
- Create one Epic per PRD epic, prefixed "v1 · ". Create Stories under each with: summary,
  description (user story + context + PRD section link), acceptance criteria as a checklist,
  labels (`ios`, `android`, `shared`, `backend`, `web`, `design-needed` where UI work exists),
  priority mapped from P0/P1/P2, and fixVersion `v1.0` / `v1.1`.
- Create foundation Tasks for Phase 5 (monorepo, CI, KMP core, iOS shell, Android shell,
  Firebase project, landing page) under an Epic "v1 · Platform foundations".
- Create design Tasks (label design-needed) for each prototype screen group.
- Link dependencies with "blocks" links.
- Before creating anything, print the full proposed tree (epics → stories) and STOP for human
  approval. After approval, create the tickets and write docs/product/jira-map.md (PRD section →
  Jira keys).
```

---
**🚦 Gate 2 (you):** approve the PRD and the Jira tree.

---

### A6 — Brand identity, design system & motion language · *Top creative model* · Phase 3

```
You are a principal product designer who has shipped award-winning iOS and Android apps.
Create DrivePerf's design foundation. The UX is shared across platforms; the cosmetics follow
each platform: iOS 26/27 Liquid Glass + HIG, Android Material 3 Expressive.

Read: docs/product/PRD-v1.md, docs/product/A2-strategy-positioning.md.

Deliver in docs/design/system/:
1. brand.md — name treatment, logo concepts (3, as SVG in assets/), app icon concepts for iOS
   (incl. the layered/tinted/clear icon variants Apple requires) and Android (adaptive +
   themed monochrome), colour story (night-drive dark-first, high-contrast data colours that
   are colour-blind safe), typography (SF Pro / Roboto Flex + a numeric display face for stats).
2. tokens.json — design tokens (colour light/dark, spacing, radii, elevation, type scale, motion
   durations/easings/springs) with a platform-mapping table: token → SwiftUI value →
   Compose M3 Expressive value.
3. motion.md — the motion language: signature transitions (trip start, live recording pulse,
   route draw-on, score reveal, G-meter physics, share-card build), spring parameters per
   platform, reduced-motion fallbacks, 120 Hz considerations, a haptics map.
4. components.md — inventory with anatomy + states: score ring, stat tile, G-G circle,
   synced timeline chart, event chip, data-quality badge, trip card, route card, share card,
   paywall, permission primer, empty/loading/error states. For each: iOS rendering
   (glass material, tab bar, toolbars) vs Android rendering (expressive shapes, FAB menu,
   dynamic colour) side by side.
5. accessibility.md — Dynamic Type/large fonts, VoiceOver/TalkBack labels for charts,
   colour-independent meaning, minimum 44pt/48dp targets, driving-safety UI rules.
6. screen-map.md — complete screen list and navigation graph for v1 (tabs: Drives, Routes,
   Stats/Insights, Garage, Profile — validate or improve), plus the in-drive locked state.

Be bold and specific: this is the reference for three prototype agents working in parallel.
```

### A7 — iOS prototype (HTML/CSS/JS, Liquid Glass) · *Top creative model + strong front-end coder* · Phase 3 ∥

```
You are a senior iOS designer-engineer. Build a high-fidelity, clickable iPhone prototype of
DrivePerf v1 in plain HTML/CSS/JS (no build step; one index.html + css/ + js/ + assets/;
GSAP or Motion One via CDN allowed; MapLibre GL via CDN with a free style or a static map
image for maps).

Read: docs/design/system/* (tokens, motion, components, screen-map) and docs/product/PRD-v1.md.

Requirements:
- iPhone 17 Pro frame (402×874 pt) with Dynamic Island; light + dark mode toggle.
- Faithful iOS 26/27 Liquid Glass: translucent glass tab bar that floats and minimises on
  scroll, glass toolbars/sheets, lensing/refraction approximation via backdrop-filter + SVG
  displacement, specular edge highlights, content scrolling under glass. Use the SF Pro
  font stack.
- Flows (all clickable, realistic fake data in js/data.js):
  1. Onboarding (3 cinematic screens) → permission primers (Location Always, Motion, Notifications).
  2. Home/Drives: "ready to record" state with data-quality status; manual Start.
  3. Live Activity + Dynamic Island mock while recording, and the locked in-drive screen.
  4. Drive Story: animated route draw-on, G-meter replay, smoothness ring reveal, one highlight, one tip.
  5. Analysis: map + synced scrubbable timeline (speed, long-G, lat-G), G-G circle, event detail sheet.
  6. Routes & personal bests; Stats dashboard; Garage; Share card builder; Paywall; Privacy Centre; Settings.
  7. Empty, loading, partial-data and permission-denied states.
- 60fps animations, prefers-reduced-motion respected, haptic moments annotated.
- A small "annotations" overlay toggle explaining interaction and motion specs for developers.

Output: prototypes/ios/ (open index.html directly). Add prototypes/ios/README.md listing every
screen and the PRD story it covers. Take screenshots of key screens into
prototypes/ios/screens/.
```

### A8 — Android prototype (HTML/CSS/JS, Material 3 Expressive) · *Top creative model + strong front-end coder* · Phase 3 ∥

```
You are a senior Android designer-engineer. Build a high-fidelity, clickable Android prototype
of DrivePerf v1 in plain HTML/CSS/JS (no build step; same structure and the SAME js/data.js
fixtures as prototypes/ios/ — coordinate by copying it, do not diverge the data).

Read: docs/design/system/* and docs/product/PRD-v1.md.

Requirements:
- Pixel 10 Pro-class frame (412×915 dp), gesture nav bar, status bar; light/dark + a dynamic
  colour picker (seed colour → tonal palette, M3 algorithm via @material/material-color-utilities CDN).
- Faithful Material 3 Expressive: expressive shape scale + shape morphing (e.g. score badge
  morphs between shapes), spring-based motion (spatial vs effects springs), floating toolbar,
  FAB menu, button groups, loading indicator shapes, flexible top app bars, emphasised type.
  Use Roboto Flex / Google Sans-like stack.
- Same flows as the iOS prototype (onboarding, permissions incl. Android's separate
  background-location step, ready state, Live Update notification + ongoing notification while
  recording, Drive Story, Analysis, Routes, Stats, Garage, Share, Paywall, Privacy, Settings, states).
- Predictive back gesture animation mock; edge-to-edge layout.
- Same annotations overlay concept.

Output: prototypes/android/ with README.md (screen → PRD story) and screens/ screenshots.
```

### A9 — Web prototype + landing page content · *Creative model* · Phase 3 ∥

```
You are a senior web designer and conversion copywriter. Prototype driveperf.com in plain
HTML/CSS/JS (static, deployable to Firebase Hosting).

Read: docs/product/A2-strategy-positioning.md, docs/design/system/*, and the iOS/Android
prototype folders for screens to showcase.

Pages: Home (hero with motion: a route drawing itself into a smoothness score; phone mockups
showing both iOS and Android variants; how it works in 3 steps; feature sections for Drive
Story, Analysis, Routes, Privacy; FAQ; waitlist form), Privacy Policy (draft, flagged for legal
review), Terms (draft), Support/Contact, Press kit placeholder, 404.
Requirements: Lighthouse ≥ 95 for performance/accessibility/SEO, responsive to 360 px, dark
mode, Open Graph/Twitter cards, schema.org SoftwareApplication, no third-party trackers by
default (cookie-less analytics placeholder, e.g. Plausible), waitlist form posts to a
placeholder endpoint documented for A18.

Output: prototypes/web/ + README.md. Copy must avoid insurance claims and speed glorification.
```

---
**🚦 Gate 3 (you):** pick the final visual direction. Optionally run 5–8 user tests on the prototypes (see the research plan in `02-next-phase-research-plan.md` §4).

---

### A10 — Architecture & tech-stack ADRs · *Opus 5.5* · Phase 4 ∥

```
You are a principal software architect. Decide and document DrivePerf's v1 architecture for
iOS + Android + web + backend, built to serve a few thousand DAU on Firebase and migrate to a
self-hosted VPS (e.g. Hostinger: Postgres + S3-compatible storage + API) without a client
rewrite.

Read: docs/planning/DrivePerf-v1-kickoff-plan.md §3 (default recommendation: KMP shared core +
SwiftUI + Compose M3 Expressive + Firebase + Astro), docs/research/2026-11/A3-platform-feasibility.md,
docs/product/PRD-v1.md, AGENTS.md.

Write ADRs in docs/architecture/adr/ (MADR format), each with options, decision, consequences:
ADR-001 cross-platform approach (KMP+native UI vs Flutter vs RN/Expo vs fully native ×2) —
  score on: Liquid Glass/M3E fidelity, motion smoothness, background sensor reliability, code
  sharing %, AI-agent productivity, hiring, build complexity.
ADR-002 monorepo layout & build tooling (Gradle, Xcode/SPM, KMP framework export, SKIE or
  equivalent for Swift interop).
ADR-003 local storage (SQLDelight vs Room KMP), raw trip file format (protobuf vs NDJSON.gz),
  retention.
ADR-004 backend v1 (Firebase services list) and the migration seam: SyncRepository interface,
  portable data formats, auth portability, Firestore→Postgres mapping, cost model at 1k/5k/20k DAU.
ADR-005 maps provider(s) and cost.
ADR-006 payments (RevenueCat) & entitlements.
ADR-007 analytics, crash reporting, feature flags, privacy-preserving defaults.
ADR-008 CI/CD (GitHub Actions, macOS runners/Xcode Cloud, Fastlane, signing, environments
  dev/staging/prod).
ADR-009 web stack (Astro on Namecheap now; dashboard later).
Also write docs/architecture/overview.md with C4 context + container diagrams (Mermaid) and a
module dependency diagram.
```

### A11 — Data model, sync & privacy data-flow · *Opus 5.5* · Phase 4 ∥

```
You are a senior backend/data engineer. Design DrivePerf's data model and sync.

Read: docs/product/PRD-v1.md, docs/architecture/adr/* (if present), A3 feasibility.

Deliver docs/architecture/data-model.md:
1. Domain entities (User, Device, Vehicle, Trip, TripSegment, LocationSample, MotionSample,
   DrivingEvent, Score, Route, RouteMatch, PersonalBest, Recap, Entitlement, Consent) with
   fields, types, units, provenance flag.
2. On-device schema (SQLDelight DDL) and raw trip file schema (versioned protobuf or NDJSON).
3. Cloud schema in Firestore AND the equivalent Postgres DDL (proves migration path), Cloud
   Storage layout, security rules draft, indexes.
4. Sync protocol: what syncs (summaries vs raw), conflict rules, offline-first, idempotency,
   backoff, account deletion cascade, export format (GPX, CSV, JSON).
5. Privacy data-flow map: every datum → where it goes → retention → user control; draft answers
   for Apple Privacy Nutrition Label and Google Play Data Safety.
6. Size & cost estimates per trip and per active user/month on Firebase.
7. A migration runbook outline: Firebase → VPS (Postgres + MinIO + API), zero-downtime steps.
```

### A12 — Sensor pipeline & scoring algorithm spec · *Opus 5.5* · Phase 4 ∥

```
You are a telematics/signal-processing engineer. Specify DrivePerf's trip pipeline so it can
be implemented once in Kotlin Multiplatform (platform code only supplies samples).

Read: docs/research/2026-09/android-feasibility-evidence.md, A3 feasibility, PRD metric
provenance table, and the legacy Android scoring code (tag android-prototype-v0,
app/src/main/java/com/driveperf/domain/scoring/) as reference only — its weights are NOT
requirements.

Specify in docs/architecture/pipeline-and-scoring.md:
1. Trip state machine (idle → candidate → recording → paused → finishing → processed), with
   triggers per platform and timeouts; false-trip and passenger handling.
2. Signal processing: timestamp alignment, GPS filtering (accuracy gates, Kalman or
   complementary filter), phone-to-vehicle frame alignment (gravity removal, yaw alignment from
   GPS heading), detection of the phone being handled/moved, resampling, gap handling.
3. Derived metrics with formulas & units: distance, moving time, speed stats, longitudinal/
   lateral acceleration, jerk, G-G envelope, stop-start count, speed consistency.
4. Event detectors (hard brake, harsh accel, sharp corner, …): rule-based thresholds with
   hysteresis and minimum durations, confidence, evidence window stored.
5. Smoothness score (hero), Efficiency proxy, event summary: formulas, normalisation by
   context (city/highway via speed profile), confidence and "unscorable" rules. Never reward speed.
6. Coaching tip selection rules (one tip per trip, deterministic, explainable).
7. Route matching for "Routes" (start/end clustering + path similarity, e.g. Fréchet on
   simplified polylines) and personal-best logic.
8. Test strategy: synthetic signal fixtures, recorded-drive fixtures, golden outputs, and the
   field-validation metrics (precision/recall of events per 100 km, battery %/h).
Include pseudocode and a fixture format the shared module's tests will use.
```

---
**🚦 Gate 4 (you):** approve the ADRs. This is especially important for ADR-001 (KMP) and ADR-004 (Firebase + migration seam).

---

### A13 — Monorepo scaffold & CI · *Sonnet 5.5 (Claude Code)* · Phase 5 (first)

```
Jira: <foundation ticket key>. Implement the monorepo described in docs/architecture/adr/ADR-002
on branch <KEY>-monorepo-scaffold in theKeval/DrivePerf.

- shared/ (KMP: commonMain, androidMain, iosMain; tests), apps/android/ (Compose, depends on
  shared), apps/ios/ (Xcode project, SwiftUI, consumes shared as XCFramework/SPM via the ADR's
  tooling), backend/ (Firebase project config, functions/ TypeScript), web/ (Astro), prototypes/,
  docs/.
- Each app shows a single "Hello DrivePerf" screen that calls one shared function.
- GitHub Actions: shared unit tests, Android assembleDebug + lint, iOS build on macOS runner,
  web build; keep existing jira-sync.yml and review-router.yml working.
- Update AGENTS.md / .github/copilot-instructions.md / instructions files for the new layout
  (path-specific instructions for shared/, apps/ios/, apps/android/, web/).
- README with one-command setup per app (remember: on Keval's Mac, npm runs via
  `node /opt/homebrew/lib/node_modules/npm/bin/npm-cli.js`).
Done when CI is green and both apps run on simulator/emulator. Open a draft PR with the Jira key.
```

### A14 — Shared KMP core (state machine, pipeline, scoring) · *Opus 5.5* · Phase 5 ∥

```
Jira: <keys>. Implement docs/architecture/pipeline-and-scoring.md in shared/ (commonMain), with
interfaces that platform code implements (LocationSource, MotionSource, ActivitySource, Clock,
TripFileStore). Include SQLDelight schema from docs/architecture/data-model.md, repositories,
TripRecorder state machine, signal pipeline, detectors, Smoothness/Efficiency scoring,
coaching tip selection, route matching. ≥ 90% unit-test coverage on domain logic using the
fixture format in the spec; add 3 synthetic drive fixtures (city, highway, mixed with a tunnel gap).
No platform UI. Draft PR per epic-sized slice.
```

### A15 — iOS app shell (SwiftUI, Liquid Glass) · *Sonnet 5.5 / Opus 5.5* · Phase 5 ∥

```
Jira: <keys>. In apps/ios/, build the iOS app shell per docs/design/system/* and
prototypes/ios/ (the visual source of truth): navigation/tab structure with native Liquid Glass
components, design tokens mapped to SwiftUI, onboarding + permission primers, platform adapters
(CoreLocation + CoreMotion implementations of the shared interfaces, CLServiceSession,
background modes), ActivityKit Live Activity for recording, manual start/stop recording end-to-end
writing to the shared core, trip list showing processed trips. Follow AGENTS.md iOS playbook
(MVVM, @Observable, async/await). Include XCTest for view models and a short manual test script
for a real drive. Draft PR.
Until the Apple Developer account exists: develop and test on the iOS Simulator (use simulated
location routes from GPX files for trips; feed recorded motion fixtures through a debug MotionSource).
Keep Sign in with Apple, push and TestFlight behind a build flag.
```

### A16 — Android app shell (Compose, M3 Expressive) · *Sonnet 5.5 / Opus 5.5* · Phase 5 ∥

```
Jira: <keys>. In apps/android/, build the Android app shell per docs/design/system/* and
prototypes/android/: M3 Expressive theme from tokens (dynamic colour optional), navigation,
onboarding + permission flow (fine → background location as separate step, activity
recognition, notifications), platform adapters (Fused Location, Activity Recognition Transition,
SensorManager) implementing the shared interfaces, foreground service type=location with a Live
Update notification, manual start/stop end-to-end into the shared core, trip list. You may
reference tag android-prototype-v0 for patterns but do not copy its scoring. Hilt or Koin per
ADR. Unit + UI tests; manual drive test script. Draft PR.
```

### A17 — Firebase backend v1 · *Sonnet 5.5* · Phase 5 ∥

```
Jira: <keys>. In backend/, set up the Firebase project "DrivePerf" per ADR-004 and
docs/architecture/data-model.md. It starts on the free Spark plan.
Spark scope: Auth (Google, email link; add Apple once the Apple Developer account exists),
Firestore rules + indexes, App Check, Hosting config, and the website waitlist as a create-only
Firestore collection (validated by rules, App Check enforced).
Blaze scope (prepare the code, but deploy only after Keval upgrades the plan and sets a budget
alert): Storage rules and layout, plus Cloud Functions (TypeScript) for the account deletion
cascade, export bundle generation and weekly recap computation.
Use one project with Emulator Suite for development; add a separate prod project only if ADR-004
says so. Emulator suite config + rules unit tests. Implement the shared SyncRepository's Firebase adapter
contract doc so iOS/Android can bind it. Document monthly cost estimate at 1k/5k DAU and the
migration seam. Never commit secrets; use GitHub environments.
```

### A18 — Landing page build & Firebase Hosting deploy · *Sonnet 5.5* · Phase 5 ∥

```
Jira: <key>. Convert prototypes/web/ into an Astro static site in web/. Wire the waitlist form
to the Firestore waitlist collection (A17) with spam protection (honeypot, App Check, rules-based
field validation). Add privacy/terms pages, sitemap, robots, OG images and cookie-less analytics.
Deploy to Firebase Hosting in project "DrivePerf" with FirebaseExtended/action-hosting-deploy:
a preview channel per PR and the live channel on merge to main (secret
FIREBASE_SERVICE_ACCOUNT_DRIVEPERF, set by Keval). Add
/.well-known/apple-app-site-association and /.well-known/assetlinks.json placeholders for future
universal/app links. Write docs/launch/custom-domain.md with the exact steps to connect
driveperf.com and www in the Firebase console and the DNS records to add at Namecheap (replacing
the parking-page records). Do NOT change DNS yourself.
```

---

### A19 — Field-test & calibration protocol · *Research model* · Phase 6

```
Design a safe, consented field-test protocol for DrivePerf beta (internal + 10–20 testers,
CA/US, iOS + Android device mix). Cover routes (city/highway/rural/tunnel), phone placements,
reference instrumentation (optional GNSS logger/OBD for ground truth), labelling workflow,
metrics (capture success, start latency, false trips, event precision/recall per 100 km,
battery %/h, crash-free rate), data handling/consent, and a threshold-tuning loop back into
shared/ fixtures. Testers never interact with the app while driving; no provoked dangerous
manoeuvres on public roads. Output docs/research/field-test-protocol.md.
```

### A20 — Store launch readiness · *Creative model + Sonnet 5.5* · Phase 7

```
Prepare App Store Connect and Google Play Console launch packages: app name/subtitle/keywords
(ASO research for CA/US), descriptions, screenshot storyboards (from prototypes), preview video
script, privacy nutrition label + Data Safety answers (from data-model privacy map), background
location justification text + demo video script, review notes, age rating, support URL,
privacy policy final draft (flag for legal review), and a launch checklist. Output
docs/launch/.
```

---

## 6. Immediate next steps for you
1. ✅ Phase 0 is done.
2. **Approve or redirect the wedge** in §2: enthusiast hero + Smoothness hero metric.
3. Start **A1, A2 and A3 in parallel** (Phase 1), then go through Gate 1 → A4 PRD → A5 Jira → Gate 2 → prototypes → Gate 3.
4. Create the Firebase project **"DrivePerf"** (Spark) in the Firebase console before Phase 5. Prototypes don't need it.
5. Buy the **Apple Developer** account ($99/yr) before iOS device testing and TestFlight in Phase 6. The Play Console account is already in place.
