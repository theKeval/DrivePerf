# Decisions log

| Date | Decision | Source |
|---|---|---|
| 2026-11-06 | Restart v1 as a cross-platform product (iOS + Android). The Android code is frozen as `legacy/android-prototype/` and tagged `android-prototype-v0`. | DRIVE-11 |
| 2026-11-06 | Sequence: PRD → Jira tickets → prototypes approved → development. | Keval |
| 2026-11-06 | Hosting and backend: a new Firebase project **"DrivePerf"**, starting on the free Spark plan. Namecheap is only the domain registrar. The custom domain will be connected to Firebase Hosting later. | Keval |
| 2026-11-06 | A Google Play Console account exists. The Apple Developer account will be bought later, before iOS device and TestFlight work. | Keval |
| 2026-11-06 | **Gate 1 approved**: all 15 recommendations in `gate-1-decisions.md`, with #14 changed and #16–17 added. Summary: hero user = daily-driven enthusiast; hero metric = baseline-relative Smoothness; "Smooth is a skill." / "Every drive, decoded."; US$6.99/mo or US$39.99/yr (CA$8.99/CA$49.99) with a founding first year at US$29.99; 14-day no-card Pro Preview; generous Free tier (Wrapped free, summaries kept forever); local-first with an optional account; share cards + referral only; binding safety rules (no live score, no speed on share cards, no streaks). | Keval, DRIVE-12 |
| 2026-11-06 | **iOS capture test app (Spike 1)** is the first development ticket, benchmarked against Mileon. There is no spike before the PRD, tickets and prototypes. | Keval |
| 2026-11-06 | **Trip replay is P0**: map replay with speed, max speed, stops, elevation, and pins for hard braking, acceleration and cornering, each linked to its evidence. Speed is neutral in personal replay and never appears on share cards, records or leaderboards. | Keval |
| 2026-11-06 | **Prototype-first delivery**: full screen-by-screen HTML/CSS/JS prototypes (iOS, Android, web), mapped to Jira stories and stored in `docs/design/<JIRA-KEY>/`, are linked from the tickets before development starts. | Keval |
| 2026-11-06 | **Gate 2 approved**: the PRD v1 with full scope. **Target launch January 2027, or earlier** (AI-driven build, compressed phases). Replay older than 30 days stays Pro. Free gets the weekly view with a 4-week trend. 14-day Pro Preview, with free or extended Pro for beta and internal testers. The iOS "Show live drive" control is kept. **English only.** An AI-assisted trademark preliminary search ("DrivePerf", "Smooth is a skill.") is the last task, after development. The Jira backlog is created directly, without a tree review; changes are handled during development. | Keval, DRIVE-13 |
