# Gate 1 — decisions for approval

**Status:** awaiting Keval's approval · **Prepared:** 6 Nov 2026 · **Jira:** DRIVE-12

These decisions consolidate the Phase 1 outputs: [A1 market refresh](../research/2026-11/A1-market-refresh.md), [A2 strategy](A2-strategy-positioning.md) and [A3 feasibility](../research/2026-11/A3-platform-feasibility.md). Duplicates across the three are merged. Once you approve them (or change any), they move to `decisions.md` and become binding inputs for the PRD (A4).

## What Phase 1 changed in our thinking
- **Smoothness is no longer unclaimed.** SpeedRank, Open Road, Top Driver and OVERDRIVE ("Flow", "scored on smooth, not speed") all show some form of smoothness score. Our edge has to be *explained, baseline-relative coaching* plus Strava-grade recaps, privacy and iOS/Android parity. No surveyed app does all four (A1).
- **Capture reliability is the top complaint** in competitor reviews: 17 of 50 sampled reviews mention it. Paywalls are second: 10 of 50, all negative (A1).
- **iOS has two hard limits.** It cannot start a Live Activity locally for an automatically detected trip, and the first ~300–1,000 m of each automatic trip is lost. Neither breaks the product, but the copy and UX must be honest about both (A3).
- **KMP + native UI holds up.** Use SKIE for Swift interop, and keep Firebase native behind `SyncRepository` (A3).
- **The free Firebase plan covers prototyping and the website.** Cloud trip backup needs Blaze (A3).

## Decisions

| # | Decision | Recommendation | Alternative |
|---|---|---|---|
| 1 | **Hero user** | The **daily-driven enthusiast**: loves their car *and* drives it most days. The EV efficiency optimiser is the bridge persona. | The broader "everyday enthusiast" |
| 2 | **Hero metric** | **Smoothness**, always shown against your own baseline and routes. It is never shown live while driving and never rewards skipping a needed stop. Keep the plain name "Smoothness": it is searchable, and OVERDRIVE already uses "Flow". | A branded metric name |
| 3 | **Positioning** | Lead line **"Smooth is a skill."** with **"Every drive, decoded."** as support. A/B test both on the waitlist. | Recap-led: "Strava-grade recaps for your daily drive" |
| 4 | **Pricing** | **US$6.99/mo · US$39.99/yr**; **CA$8.99/mo · CA$49.99/yr**. Founding-member first year US$29.99 / CA$39.99. No weekly plan, and no lifetime plan at launch. | US$4.99 / US$34.99 (original plan) |
| 5 | **Trial** | **14-day "Pro Preview" reverse trial with no card**, then a "first two weeks, decoded" paywall on day 15 | A 7-day store trial at the first Route comparison |
| 6 | **Free vs Pro** | **Free:** recording, Drive Story, scores, weekly recap, **Wrapped (core)**, sharing, all trip summaries forever ("locked, not lost"), 30-day Analysis, 2 routes, 1 car, full data download. **Pro:** full-history analysis, unlimited routes plus ghost comparison, trends, GPX/CSV export, cloud backup, unlimited cars. | Original split (Wrapped in Pro, 30-day history) |
| 7 | **Data model default** | **Local-first, account optional.** Signing in is needed only for cloud backup and multi-device use. | Account plus sync by default |
| 8 | **Social in v1** | **Share cards (route shape, no map, trimmed ends) + "Ride-along pass" referral only.** No friends, feeds or leaderboards in v1. | Friends and leaderboards in v1 |
| 9 | **iOS live surface for auto trips** | A **notification plus a one-tap Control Center control or widget** that starts a Live Activity. Push-to-start is evaluated later, because it needs the paid Apple account, Blaze and network. | Fund push-to-start in v1 |
| 10 | **Honest capture promise** | "Auto-detects most drives. The first few hundred metres may be estimated. Force-quitting pauses auto-detect." The battery target is stated as **screen off** (estimate: 2–4 %/h). | — |
| 11 | **Metrics** | North star **Weekly Decoded Drivers**: ≥2 good-quality trips **and** ≥1 Story, recap or analysis viewed in the week. Activation: **always-on capture granted + first Story viewed within 72 h**. Targets: D1 35%, D7 20%, D30 12%. | ≥3 trips/week; first Story within 48 h |
| 12 | **Binding safety rules** | No live score or interaction while moving. No speed or max-G on share cards. **No driving streaks.** Records never reward rolling stops. No speed leaderboards. | — |
| 13 | **Accounts and plans timing** | Apple Developer account: buy it before Sign in with Apple, push, App Attest and TestFlight work. The free Personal Team is enough for an iOS capture spike on a real iPhone. Blaze: upgrade when cloud backup is built. | — |
| 14 | **Feasibility spike before Gate 3?** | **Allow one throwaway spike**: iOS background auto-capture on a real iPhone, 1–2 days, not product code. It is the biggest risk to the PRD's core promise. | Wait until after the prototypes are approved |
| 15 | **Launch window and market** | **Spring 2027 "Season Opener"**, Canada + US, English. Get legal advice on French-language requirements for Quebec before the CA launch. | Soft launch Feb 2027 |
