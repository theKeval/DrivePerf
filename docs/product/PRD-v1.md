# DrivePerf v1.0 — Product Requirements Document

| | |
|---|---|
| **Product** | DrivePerf (driveperf.com) · iOS + Android · Canada + US · English |
| **Version** | PRD v1.0, draft for the Gate 2 review |
| **Jira** | DRIVE-13 |
| **Author** | Agent A4 (product management) · 6 November 2026 |
| **Status** | **Approved at Gate 2 on 6 Nov 2026** with the decisions below. It is the source for the Jira backlog (A5). |
| **Binding inputs** | [Gate 1 decisions](gate-1-decisions.md) and the [decisions log](decisions.md). This PRD does not change any of them. Where I think one needs another look, it is listed in §9 (Open questions). |
| **Other inputs** | [A1 market refresh](../research/2026-11/A1-market-refresh.md) (incl. §8a Mileon) · [A2 strategy and positioning](A2-strategy-positioning.md) · [A3 platform feasibility](../research/2026-11/A3-platform-feasibility.md) · [kickoff plan](../planning/DrivePerf-v1-kickoff-plan.md) §2–3 · [Sep 2026 research pack](../research/2026-09/README.md) |

---

## Gate 2 decisions (6 Nov 2026), which override anything below

| Topic | Decision |
|---|---|
| **Timeline (OQ-17)** | **Full scope is kept, and the target launch is January 2027, or earlier if ready.** The build is AI-agent driven, so phases are compressed: prototypes in mid–late Nov 2026, Spike 1 and foundations in late Nov, feature build in Dec, a short Founding Drivers beta in early Jan, launch in Jan 2027. The P1 slip order in §7.3 still applies if needed. The launch event is called **"Launch"** (the "Season Opener" name and spring timing are replaced). |
| **Replay older than 30 days (OQ-06)** | **Pro**, as written in F-RPL-01 ("locked, not lost" preview). |
| **Trends on Free (OQ-07)** | As written: Free gets the weekly view with a 4-week line; month, year, per-car and per-route trends are Pro. |
| **Pro Preview (OQ-08)** | **14 days** for everyone. Beta and internal testers get **free or extended Pro** through promotional entitlements (RevenueCat) for as long as testing needs. |
| **iOS "Show live drive" control (OQ-24)** | Kept, designed never to invite use while moving (aimed at passengers and pre-departure), as proposed. |
| **Language (OQ-01)** | **English only** for v1 and the foreseeable future. No FR-CA localisation is planned. |
| **Trademark check (OQ-19)** | An AI-assisted preliminary search of USPTO and CIPO for "DrivePerf" and "Smooth is a skill." is scheduled as the **last task, after development**. It is not legal clearance. |
| **Jira backlog** | Created directly from this PRD without a separate tree review. Changes are handled during development. |

---

## One-page summary

**What we are building.** DrivePerf records your drives automatically from the phone alone and turns each one into a short **Drive Story**, a **Trip Replay** with evidence pins, and a **Smoothness** score measured against *your own* baseline and routes. Weekly recaps and Drive Wrapped turn the habit into something worth sharing. It is local-first: no account is needed, and nothing about your trips leaves the phone unless you turn on Pro cloud backup.

**Who it is for.** The hero is the **daily-driven enthusiast** (A2 persona E1, "Marcus"): someone who loves their car and drives it most days. The **EV efficiency optimiser** (I1) is the bridge persona. The data tinkerer (E2) gets transparency and export; the new adult driver (I2) gets a gentle coach tone. v1 is **18+ only**.

**Why we win.** Smoothness scores now exist elsewhere (SpeedRank, Open Road, Top Driver, OVERDRIVE). No surveyed app combines **explained, baseline-relative coaching**, **Strava-grade recaps**, **privacy-first data and sharing**, and **native iOS surfaces with Android parity** (A1 Table 7). Capture reliability is the #1 complaint in competitor reviews (17 of 50), so capture is our first P0 and our first development ticket.

**Positioning.** *"Smooth is a skill."* Support line: *"Every drive, decoded."* Both are A/B tested on the waitlist.

**v1.0 scope (17 epics, 126 features, 117 screens).** Progressive onboarding · manual + automatic capture with honest limits · iOS Live Activity (manual trips), Control Center control and widgets · Android Live Update · data quality and provenance labels · Drive Story · **Trip Replay and Analysis (P0)** · Smoothness, events and one coaching tip per trip · history, stats and trends · Routes and route records · Garage · share cards and the Ride-along pass · optional account and Pro cloud backup · Privacy Centre, export and deletion · Pro Preview and paywall · local notifications, weekly recap and Wrapped · Settings and Health Check · website and waitlist · analytics and crash reporting.

**Money.** Free is generous (recording, Story, scores, weekly recap, core Wrapped, sharing, summaries forever, 30-day Analysis, 2 routes, 1 car, full data download). **Pro** is US$6.99/mo or US$39.99/yr (CA$8.99 / CA$49.99), with a founding first year at US$29.99 / CA$39.99. Every user gets a **14-day no-card Pro Preview**, then a **day-15 "Your first two weeks, decoded"** paywall. No weekly plan and no lifetime plan.

**How we measure.** North star: **Weekly Decoded Drivers** (≥2 good-quality trips and ≥1 Story, recap or analysis viewed in the week). Activation: always-on capture granted plus the first Story viewed within 72 h. Retention targets D1 35%, D7 20%, D30 12%. Capture targets (provisional — confirmed by Spike 1): ≥95% of drives captured, median iOS auto-start ≤400 m from the parking spot, false trips ≤3%, ≤4–5 %/h battery with the screen off, crash-free sessions ≥99.5%.

**Not in v1.** Insurance, OBD/hardware, track or lap timing, 0–60, friends, feeds, leaderboards, live location sharing, CarPlay/Android Auto, Apple Watch, push-to-start Live Activities, a web dashboard, ML detectors, crash detection, phone-distraction scoring, and any energy or fuel *measurement* claim.

**Order of work.** Gate 2 (this PRD + Jira) → screen-by-screen prototypes for every screen in §4 (Gate 3) → **Spike 1: iOS capture test app benchmarked against Mileon** → monorepo scaffold → shared core → app shells → backend → website → feature build → short Founding Drivers beta → **launch in January 2027 (or earlier)**.

**Top risks.** (1) iOS auto-capture (force-quit, reboot, Low Power Mode, the lost first few hundred metres). (2) Android OEM battery killers. (3) Store review of background location (three Play declarations, Apple 2.5.4). (4) Scope: 96 P0 features against a 6–10 week build phase. (5) The compressed January 2027 timeline.

---

## 1. Problem, vision, positioning, users and non-goals

### 1.1 Problem

| Who | Problem today | Evidence |
|---|---|---|
| Enthusiasts who drive daily | Their commute feels like dead time. The apps that track drives either sell speed (top speed, speed leaderboards) or look like mileage loggers. | A2 §2 market frame; A1 Table 1 |
| Everyone who tries a drive tracker | Trips are missed, auto-tracking silently stops, or data is lost when accounts or subscriptions change. | A1 Table 4: 17 of 50 reviews mention capture or durability, 13 negatively |
| Everyone | Paywalls arrive before any value. Some apps ask for ratings before first use. | A1: 10 of 10 price mentions negative; Enroad 7 of 10 |
| Drivers who want to improve | Scores are a number with no reason. Some penalise necessary braking. | A1 §5 and §9; Open Road reviews |
| Privacy-aware drivers | Most apps sync to the cloud by default, and several declare "Data Used to Track You". | A1 Table 1 privacy labels |

### 1.2 Vision

**Every drive, decoded — and smooth driving becomes a skill you can see yourself getting better at.** DrivePerf is the calm co-pilot (A2 §6): an instructor's eye, a friend's warmth and an engineer's honesty. It never rewards speed, never interrupts a drive, and never hides your own data from you.

### 1.3 Positioning (binding, Gate 1 #3)

- **Lead line:** *Smooth is a skill.*
- **Support line:** *Every drive, decoded.*
- **Proof points:** automatic recording · a Smoothness score you can improve against your own baseline · Trip Replay with evidence · recaps worth sharing · private by design (no account needed, no tracking, nothing sold to insurers).
- **Store metadata (A2 §2, for A20 to finalise):** name `DrivePerf: Drive Tracker`; App Store subtitle `Smoothness score & drive recap`; Play short description `Auto-record every drive. See your smoothness, weekly recaps and route records.` "Auto-record" stays only if Spike 1 confirms the capture targets in §2.4.
- **Test:** the waitlist A/B tests both lines as the lead (F-WEB-01). Store copy is locked only after ≥ 4 weeks of data or ≥ 1,000 visitors per variant, whichever comes first.

### 1.4 Target users

Personas are defined in [A2 §1](A2-strategy-positioning.md#1-personas). This PRD designs every default for E1.

| Priority | Persona | What v1 gives them | Primary features |
|---|---|---|---|
| 1 (hero) | **E1 Daily-Driven Enthusiast** | Automatic capture, a fast Story, Trip Replay, Routes and route records, share cards | F-CAP-02, F-STY-01, F-RPL-01, F-RTE-03, F-SHR-01 |
| 2 (bridge) | **I1 EV Efficiency Optimiser** | Efficiency habits (proxy, never energy), Garage, trends | F-SCO-09, F-GAR-01, F-HIS-05 |
| 3 | **E2 Data Tinkerer** | Data-quality badge, provenance labels, Analysis, G-G circle, export that cannot be taken away | F-DQ-04, F-DQ-05, F-RPL-05, F-RPL-06, F-PRV-02/03 |
| 4 | **I2 New Adult Driver (18+)** | Calm coach tone, one tip that leads with what went well, nothing during the drive | F-SCO-06, F-SCO-10, F-CAP-07 |

### 1.5 Non-goals for v1 (say these publicly)

| Non-goal | Why | Revisit |
|---|---|---|
| Insurance features, insurer branding, premium claims, data sharing with insurers | Trust; A2 E1 anxiety "is this an insurance app in disguise?" | Never without a separate product decision |
| Friends, feeds, convoys, leaderboards, live location sharing | Gate 1 #8 and #12 | Later, if ever (§7) |
| Track mode, lap timing, 0–60/0–100 timing, max-G or top-speed achievements | Safety (Apple 1.4.4/1.4.5), Gate 1 #12 | Track mode "later", closed courses only |
| Any live score, speed, coaching or alert while moving | Gate 1 #2 and #12 | Never |
| Driving streaks of any kind | Gate 1 #12 | Never |
| OBD-II/BLE hardware, CarPlay, Android Auto, Apple Watch | Scope | Later (§7) |
| Push-to-start Live Activities for automatic trips | Gate 1 #9: needs the paid Apple account, Blaze and network | Later (§7) |
| Web account dashboard | Needs Blaze/App Hosting | v1.1+ at the earliest (§7) |
| ML detectors, automatic passenger detection, crash detection, phone-distraction scoring | Need their own accuracy and privacy studies (Sep research) | Later |
| Fuel, kWh or money *measurement* ("you saved 12%") | A phone measures behaviour, not energy (Sep research; A2 I1 copy rule) | Only with vehicle data (OBD) |
| Teen or parent monitoring | v1 is 18+ (A2 I2 guardrails) | Separate product |
| Weekly plan, lifetime plan, ads, ad or attribution SDKs, data sale | Gate 1 #4; privacy label target | Lifetime revisited at 6 months (A2 §5.2) |
| French (Quebec) or other localisation | English only (Gate 2) | Not planned |

---

## 2. Success metrics

### 2.1 North star: Weekly Decoded Drivers (WDD) — binding, Gate 1 #11

**Definition.** The number of users who, in a calendar week (Monday 00:00 to Sunday 23:59, device local time), had **≥ 2 recorded driver trips of "Good" or "Usable" quality** (F-DQ-04; passenger and not-a-drive trips excluded) **and** viewed **≥ 1 Drive Story, weekly recap or Analysis/Replay screen** for ≥ 3 s.

**Input metrics (the WDD tree):** auto-capture permission grant rate per platform · capture success rate (§2.4) · share of trips that are Good/Usable · Story open rate per trip · weekly recap open rate · share rate per Story · Pro conversion.

### 2.2 Activation — binding, Gate 1 #11

| Metric | Definition | Target |
|---|---|---|
| **Activation** | Always-on capture granted (iOS: Always location + Motion & Fitness; Android: background location + activity recognition) **and** the first Story viewed within **72 h** of first open | **≥ 45% of installs** (hypothesis; reset from beta data) |
| Habit signal | ≥ 3 Stories viewed in the first 7 days | Tracked, no target until beta |
| Permission funnel | Grant rate at each permission step (F-ONB-04/05/06) per platform | Tracked; biggest drop-off expected at background location |

### 2.3 Retention and business — binding targets, Gate 1 #11 (rest from A2 §7)

| Metric | Launch target | Stretch | Beta kill/fix line |
|---|---|---|---|
| D1 engaged retention (app opened in the foreground on day N) | **35%** | 45% | < 25% |
| D7 | **20%** | 25% | < 12% |
| D30 | **12%** | 15% | < 7% |
| Capture retention W4 (still recording trips in week 4) | ≥ 30% | 40% | < 20% |
| Pro conversion by day 35 (of installs) | 3% | 5% | < 1.5% |
| Day-15 paywall: "Continue with Free" share | Tracked (guardrail, not optimised against) | — | — |
| Notification opt-out in first 30 days | < 10% | — | > 20% |
| Refund rate | Tracked | — | — |

Report weekly retention (W1, W4) alongside day-N retention: driving apps follow driving days (A2 §7.3).

### 2.4 Quality targets

> **Capture targets are provisional — confirmed by Spike 1.** Spike 1 (the iOS capture test app, the first development ticket) measures them on real iPhones side by side with **Mileon**. A19's field-test protocol re-measures them on both platforms in the beta. Any change is recorded in `decisions.md` before the build phase relies on it.

**Measurement definitions**
- **Drive (ground truth):** a car trip ≥ 1 km where the phone owner was in the car, logged in the tester's diary (Spike 1, beta) or confirmed through the missed-trip check (F-DQ-07, production).
- **Parking spot:** the end point of the previous recorded trip, or the iOS Visit / Android last-known location when there is no previous trip.
- **Start latency (m):** straight-line distance from the parking spot to the **first recorded GNSS point with horizontal accuracy ≤ 20 m**. Estimated (backfilled) points do not count. Also reported in seconds from motion start (from motion-activity history).
- **Capture success rate:** recorded drives ÷ ground-truth drives, with healthy permissions (Health Check green).
- **Missed-trip %:** 1 − capture success rate.
- **False-trip %:** recorded trips that were not the user driving a car ÷ all recorded trips, reported per class.
- **Battery %/h (screen off):** battery percentage drop per hour of recording with the screen off, the phone not charging, at 15–30 °C ambient, measured over ≥ 60 min, on the reference devices in §8.

| # | Metric | iOS target | Android target | Spike 1 pass bar (iOS) | Status |
|---|---|---|---|---|---|
| Q1 | Capture success rate, automatic mode | **≥ 95%** | **≥ 95%** on Pixel; **≥ 90%** on Samsung/Xiaomi with the reliability guide applied | ≥ 9 of 10 normal drives; **not fewer drives than Mileon** on the same set | Provisional — confirmed by Spike 1 |
| Q2 | Start latency, automatic mode (m from parking spot) | Median **≤ 400 m**, p90 **≤ 1,000 m** | Median **≤ 250 m**, p90 **≤ 600 m** | Median ≤ 800 m (A3 pass bar) and **≤ Mileon's median + 150 m** | Provisional — confirmed by Spike 1 |
| Q3 | Start latency, manual mode | First recorded point ≤ 10 s after the tap and ≤ 50 m from the spot where the user tapped | Same | ≤ 10 s | Provisional — confirmed by Spike 1 |
| Q4 | Missed-trip % (drives ≥ 1 km, healthy permissions) | **≤ 5%** | **≤ 5%** (Pixel), ≤ 10% (Samsung/Xiaomi guided) | ≤ 1 of 10 | Provisional — confirmed by Spike 1 |
| Q5 | False trips: walking and running | ≤ 0.5% of walking/running sessions create a trip | Same | **0** trips from ≥ 10 walks/runs | Provisional — confirmed by Spike 1 |
| Q6 | False trips: cycling (incl. e-bikes) | ≤ 5% of rides create an unflagged trip | Same | ≤ 1 of 10 rides | Provisional — confirmed by Spike 1 |
| Q7 | False trips: transit (bus, train, streetcar) | ≥ 50% of recorded transit rides flagged "Was this transit?" for one-tap correction | Same | Baseline measured only | Provisional — confirmed by Spike 1 and A19 |
| Q8 | Passenger trips in a car | Not detectable from phone sensors in v1. 100% are recorded; marking takes ≤ 2 taps; marked trips are excluded from every score, baseline and record | Same | Marking flow works | Product rule |
| Q9 | Overall false-trip % (all non-driving classes except passenger) | **≤ 3%** of recorded trips | ≤ 3% | — | Provisional — confirmed by Spike 1 |
| Q10 | Trip end detection | Trip ends ≤ 5 min after parking (p90); one drive split into two ≤ 3% | Same | Measured | Provisional — confirmed by Spike 1 |
| Q11 | **Battery, recording, screen off** | **≤ 5 %/h** on the iPhone reference (smaller battery, A3 §6.2); ceiling 6 %/h on any supported iPhone | **≤ 4 %/h** on the mid-tier Android reference; ceiling 6 %/h | Measured over ≥ 60 min; compared with Mileon | Provisional — confirmed by Spike 1 (A3 estimate 2–4 %/h) |
| Q12 | Battery, armed idle (auto-detect on, no trip) | ≤ 2 % per day | ≤ 2 % per day | 24 h armed run | Provisional — confirmed by Spike 1/2 |
| Q13 | Sensor delivery while recording | ≥ 98% of expected 50 Hz IMU samples and ≥ 95% of expected 1 Hz GNSS fixes (outside tunnels) | Same | Measured without the debugger | Provisional — confirmed by Spike 1 |
| Q14 | Durability | **0 confirmed trips lost.** ≥ 99.9% of trips interrupted by a crash or system kill are recovered as "interrupted" with all data written before the kill | Same | Kill test | Product rule |
| Q15 | **Crash-free sessions** | **≥ 99.5%** | **≥ 99.5%** | — | Release gate |
| Q16 | Crash-free users (7-day) | ≥ 99.0% | ≥ 99.0% | — | Release gate |
| Q17 | Android vitals | — | User-perceived ANR rate < 0.47%, crash rate < 1.09% (Play "bad behaviour" thresholds) | — | Release gate |
| Q18 | Story ready | Story generated ≤ 10 s after trip end (p90); first meaningful paint < 3 s after opening (plan §2.2) | Same | — | Release gate |
| Q19 | Cold start to Home | ≤ 2 s (p90, reference devices) | ≤ 2 s | — | Release gate |
| Q20 | Event detection accuracy (hard braking, acceleration, cornering) | Precision ≥ 85%, recall ≥ 75% vs labelled field-test events | Same | — | Provisional — set by A12, measured by A19 |
| Q21 | Background relaunch to first DB write (iOS) | ≤ 2 s (A3 Spike 5) | — | — | Provisional |

**Release gates.** v1.0 ships only when Q1, Q2, Q4, Q9, Q11, Q14, Q15 and Q17 meet target on the beta cohort for at least one week.

---

## 3. Features by epic

### 3.0 Conventions used in this section

**3.0.1 IDs and priorities**
- Feature IDs are `F-<EPIC>-NN`. Screen IDs are `S-<AREA>-NN`, shared across iOS and Android (§4).
- **P0:** must ship in v1.0. A launch blocker. Every P0 acceptance criterion is testable.
- **P1:** should ship in v1.0. It can slip to v1.1 only with product-owner approval recorded in Jira.
- **P2:** v1.1 or later unless capacity allows.
- **Free / Pro:** "Free" means available to every user. "Pro" means paid, and also unlocked during the 14-day Pro Preview and by a Ride-along pass.

**3.0.2 Epic codes**

| Code | Epic | Code | Epic |
|---|---|---|---|
| ONB | Onboarding & Permissions | SHR | Sharing & Referral |
| CAP | Trip Capture (manual, auto, live surfaces) | ACC | Account & Cloud Backup |
| DQ | Data Quality & Calibration | PRV | Privacy Centre, Export & Deletion |
| STY | Drive Story | SUB | Subscription, Pro Preview & Paywall |
| RPL | Trip Replay & Analysis | NTF | Notifications, Weekly Recap & Drive Wrapped |
| SCO | Scores & Coaching | SET | Settings & Health Check |
| HIS | History & Stats | WEB | Website & Waitlist |
| RTE | Routes & Route Records | ANA | Analytics & Crash Reporting |
| GAR | Garage | | |

**3.0.3 Edge-case catalogue.** Each feature lists only the cases that change its behaviour. Every other case follows the default here.

| Code | Situation | Default handling (all features) |
|---|---|---|
| **EC-GPS** | No GNSS fix, or horizontal accuracy worse than the gate (placeholder 30 m) | Keep recording IMU. Mark the span "no position". Do not place events on the map. Distance counts valid fixes only. |
| **EC-TUN** | Tunnel, parking garage, urban canyon (a GNSS gap with continued motion) | Mark the gap. Never present interpolated positions as observed. A gap < 3 min may be drawn as a dashed "estimated" line. IMU-only segments are scored only if IMU quality is Good. |
| **EC-MOV** | Phone moved, handled or dropped during a trip | Detect the re-orientation (F-DQ-02). Exclude the span from scores and events. Re-align. Tell the user plainly in the Story. |
| **EC-PAS** | The user was a passenger | One-tap "I'm a passenger" during the trip (F-CAP-08) or after it (F-HIS-02). Passenger trips are excluded from every score, baseline, route record, recap score and WDD count. |
| **EC-BAT** | Battery ≤ 15% and not charging | IMU drops to 25 Hz. At ≤ 5% the trip is finalised and saved, and recording stops with a notice. Auto-detect re-arms when the battery is > 15% or charging. |
| **EC-PERM** | Permission revoked or downgraded (Always → While Using, precise → approximate, Motion off, notifications off, Android background location removed or auto-revoked) | Detected at the next launch or wake. Capture-problem notification (F-NTF-02). Health Check turns red (F-SET-02). Manual mode keeps working where the platform allows. |
| **EC-FQ** | iOS force-quit or Android force-stop | iOS: watchdog notification (F-CAP-14). Android: auto-detect is impossible until reopened; on the next launch `wasForceStopped()` triggers an explanation and re-registration. |
| **EC-RBT** | Device reboot | iOS: a timestamp-only wake log (protection `none`); the database opens after first unlock. Android: re-register on `BOOT_COMPLETED`; the first trip after a reboot waits for the first unlock (credential-encrypted storage). |
| **EC-LPM** | iOS Low Power Mode or Android Battery Saver | Banner "Low Power Mode may delay automatic trips". IMU 25 Hz. Manual trips unaffected. |
| **EC-OEM** | OEM battery manager kills or sleeps the app (Samsung, Xiaomi, others) | Reliability guide (F-SET-03). Missed-trip detection from later activity history or `ApplicationExitInfo`, then a prompt. |
| **EC-NET** | Offline | Everything works locally. Maps fall back to cached tiles or a route-only canvas. Backup is queued. |
| **EC-STO** | Low storage | Warn below 500 MB free. Below 100 MB, stop raw IMU recording but keep GNSS and summaries. Never crash or lose a trip. |
| **EC-HOT** | Thermal state serious or critical | IMU 25 Hz, then 10 Hz. GNSS continues (Apple guideline 2.4.2 on heat). |
| **EC-NOT** | Not a car (walking, running, cycling, transit) | Rejection and flagging rules in F-CAP-04. One-tap correction afterwards. |
| **EC-LEN** | Very short (< 500 m or < 2 min) or very long (> 8 h) trip | Short auto trips are discarded; short manual trips are kept with a "short trip" label. Long trips keep recording; the iOS Live Activity is restarted at the next foreground (F-CAP-17). |

**3.0.4 Analytics rules (apply to every event listed).** Event names are `snake_case`. Every event carries the global properties `platform`, `os_version`, `app_version`, `plan_state` (free / preview / pro / ridealong), `days_since_install`, `capture_mode` (auto / manual_only). **Events never contain coordinates, addresses, route or car names, speeds, G values, timestamps finer than the hour, or free text.** Distances and durations are bucketed unless the event is a capture-quality event (F-ANA-02), which may carry start latency in metres. Events are sent only if the user allowed usage analytics (F-PRV-06); crash reports follow their own toggle.

**3.0.5 Privacy baseline (applies to every feature).** On-device processing by default. Trips, routes, cars and scores stay on the device unless Pro cloud backup is on (F-ACC-03). No ad or attribution SDKs. No location in logs, crash reports or analytics. Precise location is used for event placement only and is never shared without an explicit per-share choice.

---

### 3.1 Epic ONB — Onboarding & Permissions

Goal: reach "always-on capture granted" with informed consent, without asking for anything before it is needed (A3 §1.2 progressive flow, Play prominent disclosure, Law 25 location-functions-off-by-default).

#### F-ONB-01 · Intro and positioning · P0
- **Story:** As a new user, I want to understand in 20 seconds what DrivePerf does and that it is private, so that I trust it enough to grant permissions.
- **Screens:** S-ONB-01, S-ONB-02, S-ONB-03
- **Acceptance criteria:**
  1. Given a first launch, when the app opens, then S-ONB-01 shows the lead line "Smooth is a skill." and the support line "Every drive, decoded." and no permission prompt is shown before S-ONB-05.
  2. Given S-ONB-03, when it is shown, then it states "No account needed. Your trips stay on your phone unless you turn on backup." and "Pro Preview included for 14 days, no card".
  3. Given any intro screen, when the user taps Skip, then they go to S-ONB-04 (the age and terms step cannot be skipped).
  4. Given Reduce Motion (iOS) or Remove animations (Android) is on, when intro screens animate, then cross-fades replace motion.
- **Platform notes:** iOS Liquid Glass, Android M3 Expressive (A6). Same copy.
- **Analytics:** `onboarding_started`, `onboarding_step_viewed {step}`, `onboarding_skipped {from_step}`
- **Edge cases:** none beyond default.
- **Privacy:** no data collected before S-ONB-13 choices, except crash reports if the platform default requires an opt-out (see F-ONB-08).
- **Free/Pro:** Free.

#### F-ONB-02 · Age confirmation and terms · P0
- **Story:** As DrivePerf, we need users to confirm they are 18+ and accept the terms and privacy policy, so that v1 stays an adults-only product.
- **Screens:** S-ONB-04
- **Acceptance criteria:**
  1. Given S-ONB-04, when the "I'm 18 or older" checkbox is unticked, then Continue is disabled.
  2. Given the user taps "I'm under 18", when the app responds, then it shows "DrivePerf is for drivers 18 and over for now" and stops onboarding; no data is stored except a local flag.
  3. Given the terms and privacy links, when tapped, then they open the current versions (S-WEB-03, S-WEB-04) in an in-app browser, and the accepted version number and date are stored locally.
- **Platform notes:** store age rating set per §6.6.
- **Analytics:** `age_gate_passed`, `age_gate_blocked`, `terms_accepted {terms_version}`
- **Edge cases:** none.
- **Privacy:** no date of birth is collected (self-attestation only).
- **Free/Pro:** Free.

#### F-ONB-03 · Honest capture promise · P0
- **Story:** As a new user, I want to know what automatic capture can and cannot do before I grant access, so that I am not surprised later.
- **Screens:** S-ONB-05
- **Acceptance criteria:**
  1. Given S-ONB-05, when shown, then it contains the binding copy (Gate 1 #10): "Auto-detects most drives. The first few hundred metres may be estimated. Force-quitting pauses auto-detect." and the battery line "About 2–4% battery per hour of driving with the screen off." (wording may be refined by A6; meaning may not).
  2. Given the screen, when shown on Android, then "Force-quitting" reads "Force-stopping the app" and adds "Some phone brands put apps to sleep. We'll help you check."
  3. Given the user taps "How it works", when the sheet opens, then it explains manual vs automatic trips and links to S-WEB-06.
- **Platform notes:** copy variants per platform as above.
- **Analytics:** `capture_promise_viewed`, `capture_promise_learn_more`
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-ONB-04 · Progressive location permissions · P0
- **Story:** As a new user, I want to grant location step by step with a clear reason at each step, so that I can choose automatic trips knowingly or stay with manual trips.
- **Screens:** S-ONB-06, S-ONB-08, S-ONB-09, S-ONB-11, S-ONB-16
- **Acceptance criteria:**
  1. Given S-ONB-06, when the user taps Continue, then the system While-Using/When-In-Use prompt appears; the primer screen is always shown before the system prompt.
  2. Given While Using is granted, when S-ONB-08 ("Automatic trips") is shown, then **automatic trips are off until the user switches them on** (Law 25 default), and the screen is a prominent disclosure that uses the word "location", says it is used "even when the app is closed or not in use", and names the feature (automatic trip recording) before any system prompt (Play background-location policy).
  3. Given iOS and the user turns automatic trips on, when they confirm, then the app requests Always exactly once; if declined, S-ONB-16 explains manual mode and offers a Settings deep link, and the app never re-requests in-app.
  4. Given Android 11+ and the user turns automatic trips on, when they confirm, then S-ONB-09 explains the separate "Allow all the time" settings step with a screenshot-style guide and deep-links to the app's location permission page; on return, the app detects the result within 1 s.
  5. Given the user chose approximate location, when S-ONB-11 appears, then it explains "Precise location places braking and cornering on the road" and offers the precise-location request (iOS `CLServiceSession` full-accuracy purpose key; Android fine permission); if refused, trips record as "route only, no event placement".
  6. Given any denial, when onboarding finishes, then Home shows "Auto-detect off" with a fix action (S-HOME-01), and manual trips work.
- **Platform notes:** iOS: When In Use at first, then Always (one request only, A3 §1.2); `NSLocationWhenInUseUsageDescription` and `NSLocationAlwaysAndWhenInUseUsageDescription` present. Android: `ACCESS_FINE_LOCATION` → `ACCESS_BACKGROUND_LOCATION` via Settings; Play requires the in-app disclosure to match the listing.
- **Analytics:** `permission_primer_viewed {perm}`, `permission_result {perm, result}`, `auto_trips_toggled {on}`, `precise_location_result {result}`
- **Edge cases:** EC-PERM (user later downgrades: F-NTF-02); Android permission auto-reset for unused apps: Health Check flags it.
- **Privacy:** purposes stated at each step (PIPEDA meaningful consent). Auto-detect off by default.
- **Free/Pro:** Free. Pro features never depend on granting Always (Apple 5.1.1).

#### F-ONB-05 · Motion and activity permission · P0
- **Story:** As a new user, I want to understand why motion access is needed, so that I allow it and get fewer false trips.
- **Screens:** S-ONB-07
- **Acceptance criteria:**
  1. Given S-ONB-07, when the user continues, then iOS shows the Motion & Fitness prompt and Android (API 29+) shows the Physical activity prompt.
  2. Given denial, when auto trips are on, then Health Check shows "Motion access off: more false trips and slower starts" and capture still runs on location alone.
  3. Given iOS, when the app builds, then `NSMotionUsageDescription` is present (the app crashes without it, A3 §1.1) — verified by a CI check.
- **Platform notes:** iOS `CMMotionActivityManager`; Android Activity Recognition Transition API (Awareness API not used: deprecated, A3 §4).
- **Analytics:** `permission_result {perm: motion}`
- **Edge cases:** EC-PERM.
- **Privacy:** activity data processed on the device only.
- **Free/Pro:** Free.

#### F-ONB-06 · Notification permission · P0
- **Story:** As a new user, I want to be told when my drive is decoded or capture needs attention, so that I don't miss value or trips.
- **Screens:** S-ONB-10
- **Acceptance criteria:**
  1. Given S-ONB-10, when shown, then it lists exactly what we send (drive decoded, capture problems, weekly recap) and "never while you drive".
  2. Given iOS or Android 13+, when the user continues, then the system prompt appears; if denied, Settings and Health Check show the impact, and the Android recording notification still appears (required for the foreground service).
- **Platform notes:** Android 16+ Live Update promotion needs `POST_PROMOTED_NOTIFICATIONS` declared (F-CAP-12).
- **Analytics:** `permission_result {perm: notifications}`
- **Edge cases:** EC-PERM.
- **Privacy:** local notifications only in v1 (no push token is created; §8).
- **Free/Pro:** Free.

#### F-ONB-07 · Android battery and reliability step · P0
- **Story:** As an Android user on a phone that puts apps to sleep, I want brand-specific steps, so that auto-detect keeps working.
- **Screens:** S-ONB-12, S-SET-06
- **Acceptance criteria:**
  1. Given an Android device whose manufacturer is Samsung, Xiaomi/Redmi/POCO, OnePlus, Oppo, Vivo, Huawei or Honor, when auto trips are on, then S-ONB-12 shows that brand's steps with deep links where available (e.g. Samsung sleeping-apps screen) and a "Done / Remind me later" choice.
  2. Given a Pixel or another brand not listed, when onboarding runs, then S-ONB-12 is skipped and the guide remains available in Health Check.
  3. Given the battery-optimisation exemption, when requested, then it is requested only if Play's acceptable-use rules allow it for this use case (§9 OQ-12); otherwise the step links to the system settings page.
- **Platform notes:** Android only. iOS equivalent guidance (Background App Refresh, Low Power Mode) lives in S-SET-06.
- **Analytics:** `reliability_guide_viewed {oem}`, `reliability_guide_done {oem}`
- **Edge cases:** EC-OEM.
- **Privacy:** reads manufacturer and model only, locally.
- **Free/Pro:** Free.

#### F-ONB-08 · Help improve DrivePerf (analytics and crash choice) · P0
- **Story:** As a privacy-aware user, I want to choose whether usage statistics and crash reports leave my phone, so that I stay in control.
- **Screens:** S-ONB-13, S-PRV-07
- **Acceptance criteria:**
  1. Given S-ONB-13, when shown, then "Share usage statistics" and "Send crash reports" are two separate choices, each explained in one sentence, and neither is pre-selected for usage statistics (default per §9 OQ-02).
  2. Given the user's choice, when they continue, then no analytics event from before the choice is sent (events are held in memory and dropped if declined).
  3. Given the choice, when changed later in S-PRV-07, then it takes effect within one app session.
- **Platform notes:** Firebase Analytics with advertising-ID collection disabled and no IDFA/AdSupport (§6.4).
- **Analytics:** `analytics_consent_set {usage, crash}` (sent only if usage = on).
- **Edge cases:** none.
- **Privacy:** see §6.4 label targets.
- **Free/Pro:** Free.

#### F-ONB-09 · Add your first car · P1
- **Story:** As an enthusiast, I want to add my car during setup, so that my first Story feels like mine.
- **Screens:** S-ONB-14, S-GAR-03
- **Acceptance criteria:**
  1. Given S-ONB-14, when the user enters a nickname (required, ≤ 24 chars), optional make/model/year and powertrain (petrol, diesel, hybrid, EV), and picks a colour, then the car becomes the default.
  2. Given the user taps Skip, when onboarding ends, then a car named "My car" is created silently.
- **Platform notes:** none.
- **Analytics:** `car_added {source: onboarding, powertrain}`
- **Edge cases:** none.
- **Privacy:** car data stays on the device; no VIN or plate is asked for.
- **Free/Pro:** Free (1 car).

#### F-ONB-10 · Ready screen and quick-start surfaces · P1
- **Story:** As a new user, I want to know what happens next and how to start a trip without opening the app, so that my first drive just works.
- **Screens:** S-ONB-15
- **Acceptance criteria:**
  1. Given auto trips are on, when S-ONB-15 shows, then it says "You're set. Just drive. Your first Story appears when you park."
  2. Given iOS, when shown, then it shows how to add the "Start drive" Control Center control and the widget (F-CAP-10); given Android, the Quick Settings tile and widget (F-CAP-13).
- **Platform notes:** platform variant per surface.
- **Analytics:** `onboarding_completed {auto_on, precise, motion, notifications}`
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** Free.

---

### 3.2 Epic CAP — Trip Capture (manual, automatic, live surfaces)

Goal: capture every drive with zero effort, never lose one, never ask for interaction while moving, and be honest about platform limits (A3 §1–4, Gate 1 #9–10, #14).

#### F-CAP-01 · Manual start and stop · P0
- **Story:** As a driver, I want to start and stop a trip with one tap before I set off, so that I can record a drive even with automatic trips off.
- **Screens:** S-HOME-01, S-DRV-01, S-DRV-02, S-DRV-04, S-LIVE-01, S-LIVE-02, S-LIVE-05, S-LIVE-06
- **Acceptance criteria:**
  1. Given Home with While-Using (or better) location, when the user taps Start, then recording begins and the first GNSS point is recorded within 10 s (Q3), the in-drive screen (S-DRV-01) appears, and the live surface starts (iOS Live Activity, Android Live Update).
  2. Given a manual trip, when the user taps Stop and confirms on S-DRV-02, then the trip is finalised, S-DRV-04 shows "Decoding your drive", and the Story is ready within 10 s (Q18).
  3. Given iOS with only While Using, when the screen locks during a manual trip, then recording continues (`CLBackgroundActivitySession`, blue indicator).
  4. Given a manual trip and no Stop, when the vehicle has been stationary for 15 min (placeholder), then the app ends the trip automatically and says so in the Story.
- **Platform notes:** iOS `CLBackgroundActivitySession` (created in the foreground) + `CLServiceSession`; Android FGS type `location` started from visible UI.
- **Analytics:** `trip_started {mode: manual, trigger: in_app|control|widget|tile}`, `trip_stopped {by: user|auto_timeout}`, `trip_finalised {quality, duration_bucket, distance_bucket}`
- **Edge cases:** EC-GPS (start waits for a fix but records IMU), EC-BAT, EC-STO, EC-HOT, EC-LEN (short manual trips kept), EC-PERM (no location at all: Start shows S-ONB-16).
- **Privacy:** recording state always visible (indicator + live surface).
- **Free/Pro:** Free.

#### F-CAP-02 · Automatic trip start · P0
- **Story:** As a daily driver, I want my drive to start recording on its own as soon as I pull away, so that I never have to remember.
- **Screens:** S-HOME-01, S-HOME-02, S-LIVE-09, S-DRV-01
- **Acceptance criteria:**
  1. Given auto trips on and healthy permissions, when the user drives ≥ 1 km from the parking spot, then a trip is recorded in ≥ 95% of drives (Q1) with median start latency ≤ 400 m on iOS and ≤ 250 m on Android (Q2). *Provisional — confirmed by Spike 1.*
  2. Given a wake (iOS SLC or parked-location region exit; Android activity transition or parked-location geofence exit), when the app starts location, then it confirms driving using the A12 rule (placeholder: speed > 25 km/h for 20 s, or automotive at medium/high confidence and speed > 10 km/h) and, if not confirmed within 3 min, stops GNSS and returns to armed.
  3. Given a confirmed trip, when it starts, then the auto-trip notification (F-CAP-11) is posted silently, and nothing requires a tap.
  4. Given the trip ends, when finalised, then a parked-location region/geofence (100–150 m, placeholder) is registered at the end point.
- **Platform notes:** **iOS:** armed state = SLC + `CLMonitor` circular condition at the last parking spot + `CLServiceSession(.always)` recreated at every launch before any Kotlin initialisation + `CMSensorRecorder` re-armed (12 h max); in-trip `CLLocationUpdate.liveUpdates(.automotiveNavigation)` or `startUpdatingLocation` with background updates (A3 §1.3). **Android:** Activity Recognition transition (IN_VEHICLE enter) and Geofence API exit (never an FGS for geofencing, Play 27 Jan 2027) start the `location` FGS, which is an allowed background-start exemption with `ACCESS_BACKGROUND_LOCATION` (A3 §4); optional car Bluetooth connection as an extra trigger is P2 (F-GAR-04).
- **Analytics:** `auto_wake {trigger}`, `auto_candidate {result: confirmed|rejected, reason}`, `trip_started {mode: auto, trigger}`, capture telemetry in F-ANA-02.
- **Edge cases:** EC-PERM (Always lost → manual only + F-NTF-02), EC-FQ, EC-RBT, EC-LPM (warn; iOS Background App Refresh off → Health Check), EC-OEM, EC-NOT, EC-GPS (confirmation by motion only is not enough: needs ≥ 1 fix ≤ 50 m), EC-BAT.
- **Privacy:** no GNSS outside trips; triggers only (A3 §6.3). Parking spot stored locally only.
- **Free/Pro:** Free.

#### F-CAP-03 · Automatic trip end and finalisation · P0
- **Story:** As a driver, I want the trip to end on its own when I park, so that my Story is waiting for me.
- **Screens:** S-DRV-04, S-LIVE-09
- **Acceptance criteria:**
  1. Given an active trip, when the vehicle is stationary (iOS `liveUpdates` stationary or speed < 2 km/h; Android STILL + speed) for the stop timer (placeholder 4 min, A12 tunes 2–8 min), then the trip ends with its end time set to when stopping began; p90 end ≤ 5 min after parking (Q10).
  2. Given a stop at traffic lights or in traffic, when the vehicle moves again before the timer, then the trip continues (one drive split into two ≤ 3%, Q10).
  3. Given trip end, when finalising, then scoring, events, route matching and the Story are computed **while the app is still awake** before GNSS stops (A3 §2), and the result is durable before the app can suspend.
- **Platform notes:** iOS: do not rely on BGTasks for post-trip work. Android: finalise inside the FGS, then stop it.
- **Analytics:** `trip_ended {by: stop_timer|user|battery|storage}`, `trip_finalise_ms {bucket}`
- **Edge cases:** EC-BAT (≤ 5% → finalise immediately), EC-TUN (stationary in an underground garage without GNSS → IMU stillness decides), EC-LEN.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-CAP-04 · Non-driving rejection (walking, running, cycling, transit) · P0
- **Story:** As a user, I want walks, runs and rides never to appear as drives, so that my history is clean and my scores are fair.
- **Screens:** S-TRP-01, S-TRP-02, S-STY-01
- **Acceptance criteria:**
  1. Given a walk or run with auto trips on, when the app wakes, then no trip is created (Q5: 0 of ≥ 10 in Spike 1).
  2. Given a cycling ride, when the motion history shows cycling or the speed profile matches cycling (A12), then no trip is created, or the trip is flagged "Was this a bike ride?" (Q6).
  3. Given a likely transit ride (A12 heuristic: stop pattern, rail-like straight segments, speed profile), when finalised, then the Story's first frame shows "Was this a bus or train?" with Yes/No; Yes removes it from scores and history (kept in a "Not drives" filter for 30 days, then deleted) (Q7).
  4. Given any rejected or flagged trip, when the user corrects it, then the correction is used locally to tune that user's thresholds where A12 allows.
- **Platform notes:** iOS `CMMotionActivity` (walking/running/cycling/automotive confidences); Android transitions ON_BICYCLE, RUNNING, WALKING.
- **Analytics:** `auto_candidate {result: rejected, reason: walking|running|cycling|speed}`, `trip_flagged_transit`, `trip_corrected {to: transit|bike|walk|passenger|other}`
- **Edge cases:** EC-PAS (indistinguishable from driving in v1; Q8), EC-NOT.
- **Privacy:** rejected candidates keep no location data after 24 h.
- **Free/Pro:** Free.

#### F-CAP-05 · Start backfill and the estimated start segment · P0
- **Story:** As a user, I want my trip to begin where I actually parked, labelled honestly, so that the route looks complete without pretending.
- **Screens:** S-RPL-01, S-TRP-01, S-DQ-01
- **Acceptance criteria:**
  1. Given an auto trip with a known parking spot, when finalised, then the trip starts at the parking spot, the segment to the first fix is drawn dashed and labelled "Estimated", and its distance is counted and marked estimated in provenance (§5).
  2. Given iOS, when backfilling, then the start time comes from motion-activity history and the 50 Hz accelerometer data for the gap comes from `CMSensorRecorder`; gyro and attitude are absent and the gap is excluded from cornering events.
  3. Given the estimated segment, when scoring, then no event in it is shown as a map pin, and it contributes to Smoothness only if A12 rules allow accelerometer-only scoring.
- **Platform notes:** iOS `CMSensorRecorder` (accelerometer only, 3-day retention). Android: accelerometer FIFO is not available before the FGS starts; gap is GNSS-estimated only.
- **Analytics:** `start_backfilled {gap_m_bucket, imu_backfill: bool}`
- **Edge cases:** EC-RBT (no parking spot after reboot: start at first fix, no estimate).
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-CAP-06 · Durable recording and crash recovery · P0
- **Story:** As a user, I want a crash, an OS kill or a dead battery never to lose my drive, so that I can trust the app.
- **Screens:** S-TRP-01, S-HOME-01
- **Acceptance criteria:**
  1. Given recording, when samples arrive, then they are appended to the trip file at least every 5 s; on iOS the trip file uses `.completeUnlessOpen` protection so it can be written while locked (A3 §2).
  2. Given the app is killed mid-trip (force-kill test, jetsam), when the app next launches or is relaunched by Core Location / the FGS, then the trip resumes if still driving, or is saved as "Interrupted" with all data up to the kill (Q14: ≥ 99.9%).
  3. Given an interrupted span, when presented, then it is shown as a gap and never filled with a score.
  4. Given an app update or schema migration, when the app launches, then no trip is lost and migration is tested with fixtures from every prior schema version.
- **Platform notes:** Android Direct Boot: no writes before first unlock after a reboot; iOS database default protection.
- **Analytics:** `trip_recovered {state: resumed|interrupted}`, `trip_file_write_error`
- **Edge cases:** EC-BAT, EC-STO, EC-RBT, EC-FQ.
- **Privacy:** trip files are in app-private storage, excluded from logs.
- **Free/Pro:** Free.

#### F-CAP-07 · In-drive locked screen (no interaction while moving) · P0
- **Story:** As a driver, I want the app to stay calm and passive while I drive, so that it never distracts me.
- **Screens:** S-DRV-01, S-DRV-05
- **Acceptance criteria:**
  1. Given an active trip and the app in the foreground, when the screen is visible, then it shows only: a recording indicator, elapsed time, distance, car name, a large **Stop** and a large **I'm a passenger** control; **no score, no speed, no events, no tips, no navigation to other screens.**
  2. Given an active trip, when the user tries to open a Story, Replay, Analysis, share or paywall (e.g. from a notification or deep link), then S-DRV-05 shows "Your Story will be ready when you've parked" and nothing else.
  3. Given an active trip, when any notification would be sent, then it is held (F-NTF-01) except the trip's own recording notification.
  4. Given the trip has ended but the phone is still in an automotive state or moving > 10 km/h (placeholder), when the user opens content, then S-DRV-05 is shown until stationary or not automotive for 60 s.
  5. Given the in-drive screen, when shown, then it uses a dark, low-update layout (≤ 1 redraw per second) and touch targets ≥ 64 pt/dp.
- **Platform notes:** same on both.
- **Analytics:** `in_drive_screen_viewed`, `content_blocked_while_moving {target}`
- **Edge cases:** EC-PAS (passenger unlocks via F-CAP-08).
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-CAP-08 · Passenger escape · P0
- **Story:** As a passenger, I want to mark that I'm not driving, so that the trip doesn't count against "my" driving.
- **Screens:** S-DRV-01, S-DRV-03
- **Acceptance criteria:**
  1. Given an active trip, when the user taps I'm a passenger, then S-DRV-03 offers "Keep the trip as a passenger trip" or "Don't save this trip", plus Cancel.
  2. Given "passenger", when confirmed, then scoring stops, the trip is saved without scores, and the content lock is lifted for this trip (the user is not driving).
  3. Given "Don't save", when confirmed, then recording stops and all samples are deleted within 1 min.
  4. Given a passenger-marked trip, when computing baselines, records, recaps and WDD, then it is excluded (EC-PAS).
- **Platform notes:** same.
- **Analytics:** `passenger_marked {when: during|after, keep: bool}`
- **Edge cases:** EC-PAS.
- **Privacy:** "Don't save" deletes data.
- **Free/Pro:** Free.

#### F-CAP-09 · iOS Live Activity and Dynamic Island · P0
- **Story:** As an iPhone user starting a drive, I want a quiet live status on the Lock Screen and Dynamic Island, so that I know it's recording without opening the app.
- **Screens:** S-LIVE-01, S-LIVE-02
- **Acceptance criteria:**
  1. Given a manual trip started in the app or via the control/widget, when it starts, then a Live Activity starts showing: recording state, elapsed time (rendered with `Text(timerInterval:)`), distance, car name, and a Stop button; **no speed and no score**.
  2. Given an active Live Activity, when distance changes by ≥ 0.1 km or 15 s pass, then it is updated locally (at most every 5 s); payload ≤ 4 KB.
  3. Given an automatic trip, when it starts in the background, then **no Live Activity is started** (A3 §3: not allowed locally); the auto-trip notification is used instead, and the control/widget can attach one (F-CAP-10).
  4. Given the trip ends, when finalised, then the Live Activity ends with "Drive decoded" and is dismissed after 15 min or on tap.
  5. Given 8 h of activity (system cap), when it ends, then recording continues and the notification remains (F-CAP-17).
- **Platform notes:** iOS only. Local updates only (no push). Dynamic Island compact, minimal and expanded layouts.
- **Analytics:** `live_activity_started {source}`, `live_activity_ended {reason}`, `live_activity_stop_tapped`
- **Edge cases:** EC-LEN, user disables Live Activities in Settings (fall back to notification; Health Check notes it).
- **Privacy:** no location on the Lock Screen (no map).
- **Free/Pro:** Free.

#### F-CAP-10 · iOS Control Center control and widgets · P0
- **Story:** As an iPhone user, I want a one-tap "Start drive" control and widget, so that I can start a recorded trip with its Live Activity without opening the app.
- **Screens:** S-LIVE-03, S-LIVE-04
- **Acceptance criteria:**
  1. Given the Control Center control or Lock Screen/Home Screen widget, when tapped while no trip is active, then a `LiveActivityIntent` starts a manual trip and its Live Activity without opening the app.
  2. Given an automatic trip is already recording, when the control is shown, then it reads "Show live drive"; tapping it attaches a Live Activity to the running trip. Copy and design never prompt use while driving.
  3. Given an active trip, when the control is shown, then it reads "Stop drive".
  4. Given the Home Screen widget (medium), when no trip is active, then it shows the last drive's Smoothness vs baseline and part of day, and a Start button.
- **Platform notes:** iOS only (WidgetKit, ControlWidget, App Intents). Android equivalent F-CAP-13.
- **Analytics:** `control_tapped {action}`, `widget_tapped {action, family}`
- **Edge cases:** EC-PERM (no location: the intent opens S-ONB-16).
- **Privacy:** widgets show no map or place names.
- **Free/Pro:** Free.

#### F-CAP-11 · Auto-trip recording notification · P0
- **Story:** As a user, I want a quiet notice that an automatic drive is being recorded, so that I know capture worked and can stop it later.
- **Screens:** S-LIVE-09
- **Acceptance criteria:**
  1. Given an automatic trip is confirmed, when it starts, then one notification "Recording your drive" is posted with no sound or vibration (iOS passive interruption level; Android low-importance channel unless promoted, F-CAP-12).
  2. Given the notification, when tapped, then the app opens to S-DRV-01 (never to content).
  3. Given the trip ends, when finalised, then the recording notification is replaced by the post-drive notification (F-STY-02) when allowed.
- **Platform notes:** iOS: Time Sensitive is not used (needs the paid capability; not needed). Android: this is the FGS notification.
- **Analytics:** `auto_trip_notification_posted`, `auto_trip_notification_tapped`
- **Edge cases:** notifications denied: iOS shows nothing (blue indicator only); Android FGS notice appears in Task Manager.
- **Privacy:** no map or place names in the notification.
- **Free/Pro:** Free.

#### F-CAP-12 · Android Live Update and ongoing notification · P0
- **Story:** As an Android user, I want a live status chip and notification while recording, so that I get the same glanceable status as iPhone users.
- **Screens:** S-LIVE-05, S-LIVE-06
- **Acceptance criteria:**
  1. Given a manual trip on Android 16+, when it starts, then a promoted ongoing notification (Live Update, `ProgressStyle` or `MetricStyle`) shows elapsed time and distance, a Stop action, and a status chip ≤ 7 characters (e.g. "24 min"); no speed, no score.
  2. Given an automatic trip, when confirmed, then the Live Update is shown only after confirmation, and if the user dismisses or demotes it, it is never re-promoted for that trip (A3 §4).
  3. Given Android < 16 or promotion unavailable, when recording, then a standard ongoing FGS notification (S-LIVE-06) with the same content is used.
  4. Given updates, when posted, then they are rate-limited to ≥ 5 s apart.
- **Platform notes:** Android only. Requires `POST_PROMOTED_NOTIFICATIONS`, `setRequestPromotedOngoing`, no custom RemoteViews, not colourised. OEMs can apply extra criteria.
- **Analytics:** `live_update_shown {promoted: bool}`, `live_update_demoted`
- **Edge cases:** EC-OEM (Samsung surfaces may not promote; fall back).
- **Privacy:** same as F-CAP-11.
- **Free/Pro:** Free.

#### F-CAP-13 · Android Quick Settings tile and widget · P1
- **Story:** As an Android user, I want a one-tap tile and widget to start or stop a drive.
- **Screens:** S-LIVE-07, S-LIVE-08
- **Acceptance criteria:**
  1. Given the Quick Settings tile, when tapped with no active trip, then a manual trip starts (FGS from a user action) and the tile shows "Recording".
  2. Given the Glance widget, when no trip is active, then it shows the last drive's Smoothness vs baseline and a Start button; during a trip, elapsed time and Stop.
- **Platform notes:** Android only.
- **Analytics:** `tile_tapped {action}`, `widget_tapped {action}`
- **Edge cases:** EC-PERM.
- **Privacy:** no map on the widget.
- **Free/Pro:** Free.

#### F-CAP-14 · Auto-detect watchdog and re-arming · P0
- **Story:** As a user, I want to be told if automatic capture has stopped, so that I never lose a week of drives silently.
- **Screens:** S-LIVE-10, S-HOME-01, S-SET-05
- **Acceptance criteria:**
  1. Given iOS auto mode, when the app is woken for any reason, then it reschedules a local "watchdog" notification 24 h ahead (DTS pattern, A3 §1.5); if it fires (no wake for 24 h), the user sees "Auto-detect may be paused. Open DrivePerf to keep it on." — at most once per 72 h.
  2. Given Android, when the app starts after a force-stop (`ApplicationStartInfo.wasForceStopped()`), then it re-registers transitions and geofences and shows a one-time card explaining that force-stopping pauses auto-detect.
  3. Given `BOOT_COMPLETED` (Android) or the first launch after a reboot (iOS), when received, then triggers are re-armed within 10 s of first unlock.
  4. Given any re-arm, when completed, then Health Check's "Last check-in" time updates.
- **Platform notes:** iOS force-quit behaviour on iOS 26.x/27.x is unverified (A3 §1.5); Spike 1 records it per OS build.
- **Analytics:** `watchdog_fired`, `watchdog_opened`, `rearmed {reason: boot|force_stop|launch}`
- **Edge cases:** EC-FQ, EC-RBT, EC-LPM.
- **Privacy:** watchdog log contains timestamps only.
- **Free/Pro:** Free.

#### F-CAP-15 · Pause auto-detect · P1
- **Story:** As a user lending my phone or wanting privacy, I want to pause automatic trips for a while, so that some drives are not recorded.
- **Screens:** S-HOME-03
- **Acceptance criteria:**
  1. Given S-HOME-03, when the user picks 1 hour, until tomorrow 06:00, or until I turn it back on, then no automatic trip starts during the pause, Home shows "Auto-detect paused until …", and a local notification confirms when it resumes.
  2. Given a pause, when it ends, then triggers are re-armed and the watchdog does not fire during the pause.
- **Platform notes:** same.
- **Analytics:** `auto_paused {duration}`, `auto_resumed {by: timer|user}`
- **Edge cases:** EC-RBT (pause survives reboot).
- **Privacy:** supports user control (PIPEDA, Law 25).
- **Free/Pro:** Free.

#### F-CAP-16 · Battery and thermal management · P0
- **Story:** As a user, I want recording to stay light on battery and back off when the phone is hot or low, so that DrivePerf never drains my phone.
- **Screens:** S-SET-03, S-SET-05
- **Acceptance criteria:**
  1. Given recording with the screen off on a reference device, when measured over ≥ 60 min, then drain is ≤ 5 %/h (iPhone) and ≤ 4 %/h (Android) (Q11). *Provisional — confirmed by Spike 1.*
  2. Given steady cruise, LPM/Battery Saver, battery ≤ 15%, or thermal state serious, when detected, then IMU drops to 25 Hz (10 Hz at critical) within 5 s and the trip's data-quality notes record it.
  3. Given Android, when recording, then no manual `PARTIAL_WAKE_LOCK` is held for the trip; sensors use FIFO batching (`maxReportLatencyUs`) and location batching (Play excessive-wake-lock vital, A3 §4).
  4. Given no trip, when armed, then GNSS is off (Q12 ≤ 2 %/day).
- **Platform notes:** iOS `ProcessInfo.thermalState`, `isLowPowerModeEnabled`; Android `PowerManager` thermal status.
- **Analytics:** `imu_rate_changed {to, reason}`, `battery_per_hour {bucket}` (F-ANA-02)
- **Edge cases:** EC-BAT, EC-LPM, EC-HOT.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-CAP-17 · Long and short trip handling · P1
- **Story:** As a road-tripper, I want multi-hour drives recorded completely, and as anyone, I want tiny hops not to clutter my history.
- **Screens:** S-TRP-01, S-LIVE-01
- **Acceptance criteria:**
  1. Given an iOS trip > 8 h, when the system ends the Live Activity, then recording continues and the next time the app is in the foreground (while not driving) a new Live Activity starts.
  2. Given an auto trip < 500 m or < 2 min, when finalised, then it is discarded; a manual trip of that size is kept with a "Short trip" label and no score.
  3. Given a trip > 12 h, when finalised, then it is split at the longest stop over 30 min (A12 rule).
- **Platform notes:** iOS 8 h Live Activity cap (A3 §3).
- **Analytics:** `trip_discarded_short`, `trip_split_long`
- **Edge cases:** EC-LEN.
- **Privacy:** discarded trips are deleted.
- **Free/Pro:** Free.

---

### 3.3 Epic DQ — Data Quality & Calibration

Goal: phone-only data is noisy. Be honest about it, exclude what we can't trust, and show the user why (A2 E2, Sep research "label every metric's provenance").

#### F-DQ-01 · Phone-to-vehicle orientation alignment · P0
- **Story:** As a user, I want accurate braking and cornering numbers wherever my phone sits, without a calibration ritual.
- **Screens:** S-DQ-01 (state shown), S-STY-01 (calibrating copy)
- **Acceptance criteria:**
  1. Given a new trip, when the first 60 s of good GNSS heading and IMU data exist, then the pipeline aligns phone axes to vehicle axes (gravity removal, yaw from GNSS heading; A12) without user action.
  2. Given the alignment has not converged, when scoring, then that span is marked "calibrating" and excluded from events.
  3. Given recorded fixtures with known mounts (A12 test set), when processed, then longitudinal/lateral G agree with reference within tolerances A12 sets.
- **Platform notes:** iOS `.xArbitraryCorrectedZVertical` (no magnetometer dependency); Android rotation-vector/gravity sensors.
- **Analytics:** `alignment_converged {seconds_bucket}`
- **Edge cases:** EC-MOV, EC-GPS (no heading → alignment waits).
- **Privacy:** on device.
- **Free/Pro:** Free.

#### F-DQ-02 · Phone-movement detection and segment exclusion · P0
- **Story:** As a user, I want moments when my phone slid or was picked up left out of my score, so that the score reflects my driving.
- **Screens:** S-DQ-01, S-STY-01, S-RPL-01
- **Acceptance criteria:**
  1. Given the phone is picked up, dropped or re-seated during a trip, when the pipeline detects re-orientation (A12), then the span (with margins) is excluded from Smoothness and events, and alignment restarts.
  2. Given ≥ 1 excluded span, when the Story shows, then it says e.g. "Your phone moved around for about 3 minutes, so we left that part out of the score."
  3. Given more than 50% of the trip is excluded, when finalised, then the trip is "Unscorable" (F-STY-04).
- **Platform notes:** same (shared core).
- **Analytics:** `segment_excluded {reason: phone_moved, minutes_bucket}`
- **Edge cases:** EC-MOV.
- **Privacy:** the app never claims the driver touched the phone (Sep research: handling ≠ distraction).
- **Free/Pro:** Free.

#### F-DQ-03 · GPS quality gating, gaps and tunnels · P0
- **Story:** As a user, I want tunnels and bad GPS handled honestly, so that I never see a fake straight line or a crazy number.
- **Screens:** S-RPL-01, S-ANL-01, S-DQ-01
- **Acceptance criteria:**
  1. Given fixes with accuracy worse than the gate, when processed, then they are excluded from distance, speed stats and event placement.
  2. Given a GNSS gap < 3 min with consistent motion, when drawn, then it is a dashed "Estimated" segment; ≥ 3 min is shown as a labelled gap.
  3. Given implausible values (e.g. speed jumps > A12 limit), when processed, then they are rejected before max-speed computation.
- **Platform notes:** store fix age, provider and accuracy fields with every fix (Sep research).
- **Analytics:** `gnss_gap {minutes_bucket}`
- **Edge cases:** EC-GPS, EC-TUN.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-DQ-04 · Data-quality badge and detail · P0
- **Story:** As a data-minded driver, I want a clear quality label on every trip, so that I know how much to trust its numbers.
- **Screens:** S-DQ-01, S-TRP-01, S-STY-01, S-RPL-01, S-ANL-01
- **Acceptance criteria:**
  1. Given a finalised trip, when shown, then it carries one badge: **Good**, **Usable**, **Limited** or **Unscorable** (A12 defines thresholds on coverage, sample delivery, alignment and exclusions).
  2. Given the user taps the badge, when S-DQ-01 opens, then it shows: phone state (mounted / loose / moved), delivered IMU rate (e.g. "50 Hz"), GNSS coverage %, excluded minutes with reasons, estimated start distance, and the provenance legend.
  3. Given WDD, when counted, then only Good and Usable trips count.
- **Platform notes:** same.
- **Analytics:** `quality_badge_viewed {badge}`, trip quality is a property of `trip_finalised`.
- **Edge cases:** all capture edge cases feed the badge.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-DQ-05 · Provenance labels (observed / derived / estimated) · P0
- **Story:** As a sceptical enthusiast, I want every number labelled by how we got it, so that I can trust the honest ones.
- **Screens:** S-DQ-01, S-SCO-02, S-RPL-02, S-ANL-01, S-TRP-01, S-PRV-04
- **Acceptance criteria:**
  1. Given any user-visible metric in §5, when the user long-presses (or taps the info icon), then its provenance (Observed, Derived or Estimated) and a one-line meaning are shown.
  2. Given estimated values (start segment, gap fill, Wrapped comparisons), when displayed, then they are visually distinct (A6 token) everywhere, including exports (a provenance column in CSV).
  3. Given precision, when numbers display, then decimals never exceed A12's stated accuracy (e.g. G to 2 decimals, distance to 0.1 km).
- **Platform notes:** same.
- **Analytics:** `provenance_viewed {metric}`
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-DQ-06 · Phone placement guide · P1
- **Story:** As a user, I want simple advice on where to put my phone, so that my data is Good more often.
- **Screens:** S-DQ-02
- **Acceptance criteria:**
  1. Given S-DQ-02 (from onboarding, Health Check or a Limited badge), when shown, then it recommends a fixed mount or a stable spot and explains that a phone in a pocket or bag gives Limited data. It never suggests handling the phone while driving.
  2. Given 3 of the last 5 trips are Limited due to a loose phone, when the next Story ends, then a single gentle card links to the guide (max once per 30 days).
- **Platform notes:** same.
- **Analytics:** `placement_guide_viewed {source}`
- **Edge cases:** EC-MOV.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-DQ-07 · Missed-trip check and capture feedback · P1
- **Story:** As a user, I want an easy way to say "you missed a drive", so that DrivePerf gets better and I trust it.
- **Screens:** S-NTF-02, S-SET-05
- **Acceptance criteria:**
  1. Given Android activity history shows IN_VEHICLE ≥ 5 min (or iOS motion history shows automotive ≥ 5 min) with no recorded trip, when detected, then a missed-trip check is offered ("Did you drive around 5 pm?") at most once per day, held until not driving.
  2. Given the user answers Yes/No, when submitted, then the answer is stored locally and, with usage analytics allowed, sent as `missed_trip_report` (no location).
  3. Given Health Check, when opened, then "Report a missed drive" is always available.
- **Platform notes:** iOS motion history up to 7 days; Android transitions history kept by the app.
- **Analytics:** `missed_trip_prompted`, `missed_trip_report {answer, hour_bucket}`
- **Edge cases:** EC-PAS (a passenger ride may trigger the prompt: "I was a passenger" answer).
- **Privacy:** no location sent.
- **Free/Pro:** Free.

---

### 3.4 Epic STY — Drive Story

Goal: the "wow" after every drive. A ~15 s animated Story (A2 §3.1 storyboard) that leads with what went well, then gives one tip.

#### F-STY-01 · Drive Story generation and player · P0
- **Story:** As a driver who just parked, I want a short, beautiful Story of my drive, so that my commute feels like part of the hobby.
- **Screens:** S-STY-01, S-DRV-04
- **Acceptance criteria:**
  1. Given a finalised driver trip, when generated, then the Story is ready ≤ 10 s after trip end (Q18) and has the A2 frames in order: (1) part of day, route label and car; (2) route draw-on with faded start/end, distance and duration; (3) G-dot replay on the friction circle with a tracking tick on the map; (4) Smoothness ring with the baseline comparison; (5) "Cleanest stretch"; (6) "One thing for next time" with a trace sparkline; (7) actions Explore the drive · Share · Done, plus the data-quality badge.
  2. Given the Story, when it plays, then frames auto-advance, tap advances, swipe down exits, and first meaningful paint is < 3 s.
  3. Given any frame, when shown, then **no speed or max speed appears** anywhere in the Story.
  4. Given frame 1, when shown, then a one-tap "I was a passenger" chip is available (F-HIS-02).
  5. Given "Explore the drive", when tapped, then it opens Trip Replay (S-RPL-01).
  6. Given a new route record or a newly matched route, when the Story plays, then the R1 ("Looks like a regular. Name this route?") or R3 ("New route record.") variant frames are used (F-RTE-01, F-RTE-03).
- **Platform notes:** native rendering per platform (SwiftUI/Compose, A6 motion). Shared data model from the KMP core.
- **Analytics:** `story_generated {ms_bucket, variant}`, `story_opened {source}`, `story_frame_viewed {frame}`, `story_completed`, `story_action {action}`
- **Edge cases:** EC-MOV, EC-GPS, EC-TUN (variants in F-STY-04), EC-PAS (no Story for passenger trips; a simple summary instead).
- **Privacy:** route labels use user-chosen names or "Home → Work" style labels only if the user named those places; never street addresses by default.
- **Free/Pro:** Free (every trip, forever).

#### F-STY-02 · "Drive decoded." notification · P0
- **Story:** As a driver, I want one notification after I park, so that I know my Story is waiting.
- **Screens:** S-NTF-01
- **Acceptance criteria:**
  1. Given a finalised driver trip, when the phone is no longer in a trip, then exactly one notification "**Drive decoded.** {route label} · Smoothness {n}" is posted (A2 §6), respecting quiet hours.
  2. Given several trips within 30 min, when finalised, then notifications are coalesced into one ("2 drives decoded").
  3. Given the notification, when tapped, then the Story opens (subject to F-STY-05).
- **Platform notes:** local notification on both platforms.
- **Analytics:** `notification_sent {type: drive_decoded}`, `notification_opened {type}`
- **Edge cases:** EC-PERM (notifications off: Home shows the new Story card).
- **Privacy:** no map, no addresses in the notification.
- **Free/Pro:** Free.

#### F-STY-03 · First Story and baseline frame · P0
- **Story:** As a new user, I want my first Story to explain that I am compared only with myself, so that a first score doesn't feel like a judgement.
- **Screens:** S-STY-01
- **Acceptance criteria:**
  1. Given the user's first scorable trip, when frame 4 plays, then it shows "Smoothness {n}: your baseline. Every drive from here is compared to you, not to anyone else."
  2. Given trips 2–3 (calibrating, F-SCO-02), when frame 4 plays, then it shows "Calibrating your baseline · {k} of 3".
  3. Given the first Story, when it ends, then a one-time card explains the Story → Replay → Routes loop (≤ 2 lines) and no paywall is shown.
- **Platform notes:** same.
- **Analytics:** `first_story_viewed {hours_since_install_bucket}` (activation input)
- **Edge cases:** first trip Unscorable → first-Story copy waits for the first scorable trip.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-STY-04 · Degraded and unscorable Story variants · P0
- **Story:** As a user, I want an honest Story even when data was poor, so that I trust the good ones.
- **Screens:** S-STY-01
- **Acceptance criteria:**
  1. Given a Limited trip, when the Story plays, then a frame says what was left out and why (A2 edge-case copy) and the ring shows "Score based on {x} of {y} min".
  2. Given an Unscorable trip, when the Story plays, then it shows "We couldn't score this one fairly ({reason}). Here's the route anyway." with frames 1–2 and 7 only, and no tip.
  3. Given a hard stop the user marks as needed in the Story, when confirmed (F-SCO-05), then the score recomputes within 1 s and the Story continues.
- **Platform notes:** same.
- **Analytics:** `story_variant {degraded|unscorable}`
- **Edge cases:** EC-MOV, EC-GPS, EC-TUN, EC-STO (no IMU: route-only Story).
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-STY-05 · Story held while moving · P0
- **Story:** As a driver, I want the app to refuse to play anything while I'm driving, so that I'm never tempted.
- **Screens:** S-DRV-05
- **Acceptance criteria:**
  1. Given an active trip or an automotive state/speed > 10 km/h (placeholder), when a Story, recap, Wrapped or Replay is opened from anywhere (notification, widget, deep link), then S-DRV-05 appears instead.
  2. Given S-DRV-05, when the phone becomes stationary/not automotive for 60 s, then the requested content opens automatically if the app is in the foreground.
  3. Given a passenger-marked active trip, when content is opened, then it is allowed.
- **Platform notes:** same.
- **Analytics:** `content_blocked_while_moving {target}`
- **Edge cases:** EC-PAS.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-STY-06 · Story accessibility and reduced motion · P0
- **Story:** As a user with a screen reader or motion sensitivity, I want the Story fully usable, so that I get the same value.
- **Screens:** S-STY-01, S-RCP-01, S-WRP-01, S-WRP-02
- **Acceptance criteria:**
  1. Given VoiceOver/TalkBack, when a frame is focused, then its full copy is read, including the score and the baseline comparison, and auto-advance pauses.
  2. Given Reduce Motion, when frames change, then cross-fades replace draw-ons and the G-dot replay becomes a static G-G plot with the trail.
  3. Given the largest Dynamic Type / font size, when frames render, then no text is truncated (layout reflows).
  4. Given colour, when meaning is shown (better/worse), then an icon or text also carries it.
- **Platform notes:** iOS Dynamic Type, Android font scale up to 200%.
- **Analytics:** `a11y_story_mode {screen_reader, reduce_motion}`
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** Free.

---

### 3.5 Epic RPL — Trip Replay & Analysis (P0, Gate 1 #16)

Goal: "one drive, two depths". Replay is the expected baseline (Mileon); DrivePerf goes further with **tappable event pins linked to evidence and coaching**. Speed is shown **neutrally** here and nowhere social (Gate 1 #12, #16).

**Speed-neutral display rule (applies to every RPL feature).** Speed is plain text in the neutral text colour. No gauge, no redline, no colour scale by speed, no animation, haptic or badge on max speed, and no ranking or comparison of speed against anything. Speed never appears on share cards, records, recaps, Wrapped, notifications, widgets or live surfaces.

#### F-RPL-01 · Animated map replay · P0
- **Story:** As an enthusiast, I want to watch my drive replay on a map with a moving marker, so that I can relive and understand it.
- **Screens:** S-RPL-01, S-TRP-01
- **Acceptance criteria:**
  1. Given a trip, when S-RPL-01 opens, then the map fits the route, the full route is drawn, and a marker sits at the start, paused.
  2. Given Play, when tapped, then the marker moves along the recorded path in time order; speeds **Auto** (whole trip in ~60 s, default), **1×**, **10×**, **30×**; Pause stops it in place.
  3. Given the scrubber, when dragged, then the marker and all readouts update within 100 ms (p90, reference devices), and releasing continues in the previous play state.
  4. Given playback, when measured on reference devices, then ≤ 5% of frames are dropped at 60 Hz (120 Hz where supported).
  5. Given no network and no cached tiles, when Replay opens, then the route, marker and pins render on a plain canvas with "Map unavailable offline" (EC-NET).
  6. Given an active trip (driver), when Replay is requested, then S-DRV-05 is shown (F-STY-05).
- **Platform notes:** iOS MapKit; Android Google Maps SDK or MapLibre (ADR-005, §8). Marker interpolation between 1 Hz fixes is display-only.
- **Analytics:** `replay_opened {source}`, `replay_played {speed}`, `replay_scrubbed`, `replay_completed`
- **Edge cases:** EC-GPS/EC-TUN (gaps drawn as gaps; the marker jumps a gap with a "No GPS" label), EC-NET, EC-STO (raw data removed by storage management → summary route only, F-SET-04).
- **Privacy:** map tiles are fetched by the platform map SDK; no trip data is sent to the map provider beyond tile requests (A10 to confirm per provider).
- **Free/Pro:** Free for trips in the last 30 days; older trips Pro (locked, not lost: S-SUB-03 with a blurred preview of the user's own route).

#### F-RPL-02 · Replay readouts: current speed, max speed, stops, elevation · P0
- **Story:** As a driver, I want to see my speed, the highest speed I reached, where I stopped and the elevation along the drive, so that the replay tells the whole story.
- **Screens:** S-RPL-01, S-RPL-03
- **Acceptance criteria:**
  1. Given playback or scrubbing, when the marker moves, then a readout shows the current speed at the marker position (user units), elapsed time and distance.
  2. Given the trip, when the replay loads, then **max speed reached** is shown as a stat and as a neutral pin at its location; tapping the pin opens S-RPL-03 with the value and time; it obeys the speed-neutral rule.
  3. Given stops ≥ 10 s (A12), when the replay loads, then each stop has a pin; tapping shows its duration and time of day (S-RPL-03); the stop count appears in the stats.
  4. Given elevation data, when shown, then a mini elevation profile sits under the scrubber with a cursor synced to the marker, plus total gain and loss.
  5. Given max speed was computed from fixes that fail the accuracy gate, when shown, then it is not used (F-DQ-03) and the next valid maximum is shown.
- **Platform notes:** elevation from GNSS altitude, barometer optional (A12 decides).
- **Analytics:** `replay_pin_tapped {type: max_speed|stop}`
- **Edge cases:** EC-GPS (no speed → readout "—"), EC-TUN.
- **Privacy:** max speed and speed readouts never leave the personal Replay and Analysis views (export excepted, at the user's request).
- **Free/Pro:** same window as F-RPL-01.

#### F-RPL-03 · Event pins on the map · P0
- **Story:** As a driver, I want pins where I braked hard, accelerated hard or cornered hard, so that I can see exactly where my inputs were abrupt.
- **Screens:** S-RPL-01, S-ANL-01
- **Acceptance criteria:**
  1. Given detected events (F-SCO-04), when Replay loads, then each event has a pin by type (hard braking, hard acceleration, hard cornering) with distinct icon **and** shape (not colour alone).
  2. Given playback, when the marker passes an event, then the pin pulses once (no sound or haptic).
  3. Given the timeline, when shown, then event ticks align with the scrubber at the same timestamps as the map pins.
  4. Given events marked "needed" (F-SCO-05), when shown, then their pins are hollow with a "Needed" label and they don't count in scores.
  5. Given events in estimated or excluded spans, when shown, then **no pins** are placed (F-CAP-05, F-DQ-02).
- **Platform notes:** same.
- **Analytics:** `event_pin_tapped {type, confidence}`
- **Edge cases:** EC-GPS (event without a good position → listed in the trip detail, not pinned), EC-MOV.
- **Privacy:** n/a.
- **Free/Pro:** same window as F-RPL-01.

#### F-RPL-04 · Event evidence sheet · P0
- **Story:** As a driver who tapped a pin, I want the evidence and a tip, so that I understand the event and what to try next time.
- **Screens:** S-RPL-02, S-SCO-03, S-SCO-04
- **Acceptance criteria:**
  1. Given a tapped event pin or timeline tick, when S-RPL-02 opens, then it shows the **speed trace and the G trace (longitudinal and lateral)** from 10 s before to 10 s after the event (A12 may tune the window), with event start, peak and end marked.
  2. Given the sheet, when shown, then it lists: event type, peak G (2 decimals), duration, speed before and after (neutral), confidence (High/Medium/Low), and provenance.
  3. Given the sheet, when shown, then it includes the **related coaching tip** for this event type and context (F-SCO-06); if the trip's single tip is about this event, it is labelled "This drive's tip".
  4. Given the sheet, when the user taps "Was it needed?", then F-SCO-05 runs; when they tap "Not accurate?", then a reason list (phone moved, pothole/speed bump, not me driving, other) records feedback locally and the event is excluded.
  5. Given the sheet is open during playback, when it opens, then playback pauses and the map centres on the event.
- **Platform notes:** iOS sheet with detents; Android modal bottom sheet. Charts: Swift Charts / Compose (A10).
- **Analytics:** `evidence_opened {type}`, `event_marked_needed {type}`, `event_reported_inaccurate {type, reason}`
- **Edge cases:** EC-MOV (if the event overlaps an excluded span it wouldn't exist), EC-STO (raw trace removed → sheet shows summary values and "Detailed trace not kept").
- **Privacy:** feedback stays on the device; aggregate counts only via analytics.
- **Free/Pro:** same window as F-RPL-01.

#### F-RPL-05 · Analysis: synced timeline · P0
- **Story:** As a data tinkerer, I want speed, longitudinal G and lateral G on one timeline synced with the map, so that I can study the drive.
- **Screens:** S-ANL-01, S-ANL-03
- **Acceptance criteria:**
  1. Given S-ANL-01, when opened, then it shows the map and a timeline with speed, longitudinal G and lateral G channels (elevation optional), sharing one time cursor.
  2. Given the user drags the cursor on the timeline, when it moves, then the map marker and the G-G dot update within 100 ms; tapping the map snaps the cursor to the nearest point.
  3. Given pinch/zoom on the timeline, when zoomed, then the x-axis shows time or distance (toggle) and event ticks remain aligned.
  4. Given S-ANL-03, when the user toggles channels, then the choice persists per user.
  5. Given landscape on phones, when rotated, then the timeline expands to full width.
- **Platform notes:** same.
- **Analytics:** `analysis_opened {source}`, `analysis_channel_toggled {channel}`, `analysis_axis {time|distance}`
- **Edge cases:** EC-GPS (speed channel gaps shown as breaks), EC-TUN, EC-MOV (excluded spans hatched).
- **Privacy:** n/a.
- **Free/Pro:** Free for the last 30 days; full history Pro (Gate 1 #6).

#### F-RPL-06 · G-G circle · P0
- **Story:** As an enthusiast, I want a friction-circle (G-G) plot of my drive, so that I can see how composed my inputs were.
- **Screens:** S-ANL-01, S-ANL-02
- **Acceptance criteria:**
  1. Given S-ANL-01, when shown, then a G-G plot shows all scorable samples as a density cloud plus the envelope, with the dot at the cursor time.
  2. Given S-ANL-02 (expanded), when opened, then rings at 0.1 g steps are labelled and the user can filter by braking/accel/cornering events.
  3. Given excluded spans, when plotted, then they are not shown.
- **Platform notes:** same.
- **Analytics:** `gg_expanded`
- **Edge cases:** EC-MOV, EC-STO.
- **Privacy:** max G is never on cards or in Wrapped (Gate 1 #12).
- **Free/Pro:** same as F-RPL-05.

#### F-RPL-07 · Trip detail and stats · P0
- **Story:** As a user, I want one place with a drive's summary, scores, events and actions, so that I can decide where to dig in.
- **Screens:** S-TRP-01
- **Acceptance criteria:**
  1. Given S-TRP-01, when opened, then it shows: map thumbnail, route label, car, part of day and date, distance, duration, moving time, stopped time, stop count, average moving speed, max speed (neutral), elevation gain/loss, Smoothness with baseline comparison and contributors, event counts by type, the trip's tip, the data-quality badge, and actions Replay, Analysis, Story, Share, Export (Pro), Edit.
  2. Given every number, when shown, then it has provenance on long-press (F-DQ-05).
  3. Given a Free user and a trip older than 30 days, when opened, then the summary is fully visible and Replay/Analysis show the Pro lock with a blurred preview (F-HIS-03).
  4. Given a passenger or not-a-drive trip, when opened, then scores are hidden and a banner explains why.
- **Platform notes:** same.
- **Analytics:** `trip_detail_opened {source, age_bucket}`
- **Edge cases:** EC-MOV, EC-GPS, EC-PAS, EC-STO.
- **Privacy:** n/a.
- **Free/Pro:** Free summary forever.

#### F-RPL-08 · Data-quality overlay in Replay and Analysis · P0
- **Story:** As a user, I want to see which parts were estimated, missing or excluded, so that I read the replay correctly.
- **Screens:** S-RPL-01, S-ANL-01, S-DQ-01
- **Acceptance criteria:**
  1. Given estimated, gap and excluded spans, when the replay or timeline renders, then they use distinct styles (dashed, broken, hatched) with a legend.
  2. Given the marker enters such a span, when playing, then a small label states it ("Estimated start", "No GPS", "Phone moved — not scored").
- **Platform notes:** same.
- **Analytics:** `quality_overlay_legend_opened`
- **Edge cases:** EC-GPS, EC-TUN, EC-MOV.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-RPL-09 · Range statistics and advanced channels · P1
- **Story:** As a data tinkerer, I want to select a stretch and see its stats, plus extra channels like jerk, so that I can analyse an on-ramp or a corner.
- **Screens:** S-ANL-04, S-ANL-03
- **Acceptance criteria:**
  1. Given the timeline, when the user drag-selects a range, then S-ANL-04 shows distance, duration, average and peak longitudinal/lateral G, jerk summary, and the Smoothness contribution for that range.
  2. Given S-ANL-03, when Pro, then jerk and sample-quality channels are available.
- **Platform notes:** same.
- **Analytics:** `analysis_range_selected`, `analysis_channel_toggled {channel: jerk|quality}`
- **Edge cases:** EC-MOV.
- **Privacy:** n/a.
- **Free/Pro:** Pro.

#### F-RPL-10 · Replay layers and filters · P2
- **Story:** As a user, I want to hide or show pin types and choose a map style, so that the replay is less busy.
- **Screens:** S-RPL-04
- **Acceptance criteria:**
  1. Given S-RPL-04, when the user toggles events, stops or max speed pins, or map style (standard, muted, satellite where supported), then the replay updates immediately and remembers the choice.
- **Platform notes:** satellite availability depends on the provider.
- **Analytics:** `replay_layer_toggled {layer}`
- **Edge cases:** EC-NET.
- **Privacy:** n/a.
- **Free/Pro:** Free.

---

### 3.6 Epic SCO — Scores & Coaching

Goal: Smoothness as a skill, measured fairly against *your own* baseline, explained by evidence, never live, never rewarding a skipped stop (Gate 1 #2, #12; A2 §3.2 safety flag). Formulas are A12's; this PRD sets intent and rules.

#### F-SCO-01 · Smoothness score (baseline-relative) · P0
- **Story:** As an enthusiast, I want a Smoothness score that tells me how this drive compares with my usual, so that I can see myself improving.
- **Screens:** S-SCO-01, S-STY-01, S-TRP-01
- **Acceptance criteria:**
  1. Given a scorable trip, when finalised, then it gets a Smoothness score 0–100 (integer) computed from jerk and abruptness of longitudinal and lateral inputs over scorable spans (A12), named "Smoothness" everywhere.
  2. Given the baseline exists (F-SCO-02), when shown, then the primary comparison is "{±n} vs your usual on {route}" if the trip matches a route, else "{±n} vs your usual {city|highway|mixed} drives".
  3. Given any comparison, when shown, then it is **only against the user's own drives**; no comparison with other users exists anywhere in v1.
  4. Given an active trip, when anything is displayed (app, Live Activity, Live Update, widget), then **no score is shown** (Gate 1 #2).
  5. Given the same recorded fixture processed twice, when scored, then the result is identical (determinism test).
- **Platform notes:** shared KMP core; platform UI only.
- **Analytics:** `score_viewed {surface}`; trip score bucket is a property of `trip_finalised`.
- **Edge cases:** EC-MOV, EC-GPS, EC-TUN, EC-PAS (no score).
- **Privacy:** scores stay on the device (backed up only with Pro backup).
- **Free/Pro:** Free.

#### F-SCO-02 · Baseline and calibrating period · P0
- **Story:** As a new user, I want the app to learn my normal before judging changes, so that early numbers aren't misleading.
- **Screens:** S-SCO-01, S-STY-01, S-STA-01
- **Acceptance criteria:**
  1. Given fewer than 3 scorable driver trips, when scores are shown, then comparisons read "Calibrating · {k} of 3" and no trend claims are made.
  2. Given ≥ 3 scorable trips, when computing the baseline, then it uses a robust rolling statistic (A12: e.g. median of the last 10 scorable trips in the same context or route).
  3. Given passenger, not-a-drive, Limited (A12 may allow) or Unscorable trips, when computing the baseline, then they are excluded.
- **Platform notes:** shared.
- **Analytics:** `baseline_established {trips}`
- **Edge cases:** EC-PAS.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-SCO-03 · Smoothness contributors · P0
- **Story:** As a driver, I want to know which inputs moved my score, so that I know what to work on.
- **Screens:** S-SCO-01
- **Acceptance criteria:**
  1. Given a scorable trip, when S-SCO-01 opens, then it shows four contributors — **Braking, Acceleration, Cornering, Consistency** — each as "Smoother than usual / Usual / Rougher than usual" vs the user's baseline, with a one-line reason linked to evidence (e.g. "2 abrupt stops on King St").
  2. Given a contributor, when tapped, then the related events are listed and each opens S-RPL-02.
- **Platform notes:** shared.
- **Analytics:** `contributor_tapped {contributor}`
- **Edge cases:** EC-MOV.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-SCO-04 · Event detection (hard braking, hard acceleration, hard cornering) · P0
- **Story:** As a driver, I want abrupt inputs detected consistently, so that pins, scores and tips are trustworthy.
- **Screens:** S-RPL-01, S-RPL-02, S-TRP-01
- **Acceptance criteria:**
  1. Given scorable spans, when processed, then events are detected with A12's rule-based thresholds, hysteresis and minimum durations (placeholder: hard braking ≤ −0.30 g for ≥ 0.4 s), each with type, start/peak/end, peak G, confidence and a stored evidence window.
  2. Given the A19 labelled field set, when evaluated, then precision ≥ 85% and recall ≥ 75% per type (Q20, provisional).
  3. Given thresholds, when changed, then the change ships as a new algorithm version; existing trips keep their version until re-scored (F-ANA-05 cannot change thresholds silently).
- **Platform notes:** shared.
- **Analytics:** `events_detected {type, count_bucket}` per trip.
- **Edge cases:** EC-MOV, EC-GPS (events still detected from IMU but not pinned), potholes/speed bumps (vertical-dominant spikes rejected, A12).
- **Privacy:** events never leave the device except with Pro backup or export.
- **Free/Pro:** Free.

#### F-SCO-05 · "Was it needed?" necessary-event exclusion · P0
- **Story:** As a driver who braked hard for a good reason, I want to mark it as needed, so that I'm never penalised for doing the right thing.
- **Screens:** S-SCO-03, S-RPL-02, S-STY-01
- **Acceptance criteria:**
  1. Given a hard braking, acceleration or cornering event, when the user taps "Was it needed?" → "Yes, it was needed", then the event is excluded from the score, the score recomputes within 1 s, and no further questions are asked.
  2. Given an excluded event, when the tip is chosen (F-SCO-06), then it is never about that event.
  3. Given the user changes their mind, when they tap "Count it again", then it is restored.
  4. Given route records (F-RTE-03), when eligibility is checked, then A12 may cap how many exclusions a record-setting run can have (rule documented in-app).
- **Platform notes:** same.
- **Analytics:** `event_marked_needed {type, source}`, `event_unmarked`
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-SCO-06 · One coaching tip per trip · P0
- **Story:** As a driver, I want one specific, evidence-backed thing to try next time, so that coaching is useful rather than nagging.
- **Screens:** S-SCO-04, S-STY-01, S-RPL-02, S-TRP-01
- **Acceptance criteria:**
  1. Given a scorable trip, when finalised, then **exactly one** tip is selected deterministically (A12 rules) from the tip library, tied to a place on the route and ≥ 1 event or span, phrased suggestively ("Next time, try…").
  2. Given the Story and trip detail, when the tip is shown, then it comes **after** a positive observation ("Cleanest stretch…").
  3. Given an Unscorable trip, when finalised, then no tip is given.
  4. Given the tip library at launch, when audited, then it has ≥ 12 tips across braking, acceleration, cornering, consistency and anticipation, all reviewed against the voice guide (A2 §6) and none mentioning speed targets.
- **Platform notes:** shared logic; copy localisable.
- **Analytics:** `tip_viewed {tip_id}`, `tip_evidence_opened {tip_id}`
- **Edge cases:** EC-PAS (no tip).
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-SCO-07 · Score fairness guardrails · P0
- **Story:** As DrivePerf, we must never reward a rolling stop, crawling or skipping a necessary stop, so that the product stays safe and fair.
- **Screens:** none (algorithm rules, verified by tests); surfaced in S-SCO-02
- **Acceptance criteria:**
  1. Given two synthetic fixtures — a complete gentle stop and a rolling stop through the same junction — when scored, then the complete stop scores **≥** the rolling stop.
  2. Given a fixture driven at an unusually slow pace for a route (A12 band), when scored, then it can't set a route record and the Story says "Unusual pace — not counted for records".
  3. Given scoring, when inputs include speed, then speed level never increases the score; only abruptness (jerk) and consistency do.
  4. Given the fixture suite, when CI runs, then all guardrail tests pass before a scoring version ships.
- **Platform notes:** shared.
- **Analytics:** none.
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** n/a.

#### F-SCO-08 · "How scores work" explainer · P0
- **Story:** As a sceptical user, I want a plain explanation of how Smoothness and events work and what a phone can't know, so that I trust the numbers.
- **Screens:** S-SCO-02
- **Acceptance criteria:**
  1. Given S-SCO-02, when opened from any score, then it explains: what Smoothness measures, the baseline idea, contributors, events and thresholds in plain words, provenance labels, data quality, that efficiency is a behaviour proxy, and that a smooth score does not certify safe driving.
  2. Given algorithm versions, when shown, then the current scoring version and last change date are listed.
- **Platform notes:** same.
- **Analytics:** `scores_explainer_viewed {source}`
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-SCO-09 · Efficiency habits (proxy) · P1
- **Story:** As an EV driver, I want to see whether my habits are range-friendly, without fake energy numbers, so that I can improve range honestly.
- **Screens:** S-SCO-06, S-TRP-01
- **Acceptance criteria:**
  1. Given a scorable trip, when finalised, then an "Efficiency habits" rating (Better than usual / Usual / Less efficient than usual) is derived from stop-start count per km, abrupt accelerations and speed consistency (A12).
  2. Given any copy, when shown, then it never states energy, fuel, kWh, litres, money or "% saved"; it uses the A2 I1 copy rule ("Smoother habits like these usually help range. Check your car's display for your number.").
- **Platform notes:** same.
- **Analytics:** `efficiency_viewed`
- **Edge cases:** EC-GPS.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-SCO-10 · Weekly focus habit · P1
- **Story:** As a driver who wants to improve, I want one habit to focus on for a few weeks and a check on progress, so that coaching has a follow-through.
- **Screens:** S-SCO-05, S-RCP-01
- **Acceptance criteria:**
  1. Given ≥ 5 scorable trips in a week, when the weekly recap is built, then one focus habit is chosen from evidence (e.g. "braking into stops") and shown with "Gentler in {x} of {y} drives this week".
  2. Given a focus, when it improves for 3 weeks or the user picks another, then it changes; never more than one focus at a time.
  3. Given the focus card, when shown, then it is **not** a streak and has no "don't break it" language (Gate 1 #12).
- **Platform notes:** same.
- **Analytics:** `focus_assigned {habit}`, `focus_changed {by}`
- **Edge cases:** EC-PAS.
- **Privacy:** n/a.
- **Free/Pro:** Free.

---

### 3.7 Epic HIS — History & Stats

#### F-HIS-01 · Drives list · P0
- **Story:** As a user, I want all my drives in one scrollable list, so that I can find any trip.
- **Screens:** S-HIS-01, S-HIS-02, S-NAV-01
- **Acceptance criteria:**
  1. Given trips exist, when S-HIS-01 opens, then trips appear newest first, grouped by week, each card showing route label, part of day and date, car, distance, duration, Smoothness vs baseline and the quality badge.
  2. Given 1,000 trips, when scrolling, then frame rate stays ≥ 55 fps on reference devices and the list opens in ≤ 1 s.
  3. Given S-HIS-02, when the user filters by car, route, quality or type (driver / passenger / not a drive), then the list updates and the filter is shown as a chip.
  4. Given no trips, when opened, then an empty state says "Your drives will appear here. Just drive." with the capture status.
- **Platform notes:** same.
- **Analytics:** `history_opened`, `history_filtered {filter}`
- **Edge cases:** EC-PAS (passenger trips greyed), interrupted trips labelled.
- **Privacy:** n/a.
- **Free/Pro:** Free (all summaries forever).

#### F-HIS-02 · Trip edit and corrections · P0
- **Story:** As a user, I want to fix a trip after the fact (I was a passenger, it was a bus, wrong car) or delete it, so that my history is accurate.
- **Screens:** S-TRP-02, S-TRP-03, S-STY-01
- **Acceptance criteria:**
  1. Given S-TRP-02, when the user sets the trip type to Passenger, Bus/train, Taxi/rideshare or Other (not driving), then scores, baselines, route matches, records and recaps recompute within 2 s, excluding it.
  2. Given S-TRP-02, when the user changes the car, then per-car stats update.
  3. Given Delete, when confirmed on S-TRP-03, then the trip and all its raw data are deleted from the device within 5 s, and from cloud backup at the next sync (≤ 24 h) (F-PRV-04).
  4. Given a correction, when made from the Story chip, then it takes ≤ 2 taps (Q8).
- **Platform notes:** same.
- **Analytics:** `trip_corrected {to}`, `trip_car_changed`, `trip_deleted {source}`
- **Edge cases:** EC-PAS.
- **Privacy:** deletion is real deletion, not hiding.
- **Free/Pro:** Free.

#### F-HIS-03 · "Locked, not lost" history · P0
- **Story:** As a Free user, I want my old trips to stay visible even when deep analysis is Pro, so that I never feel my data was taken away.
- **Screens:** S-HIS-01, S-TRP-01, S-SUB-03
- **Acceptance criteria:**
  1. Given a Free user, when viewing any trip of any age, then the full summary (S-TRP-01 stats, scores, Story) is available.
  2. Given a trip older than 30 days, when Replay or Analysis is tapped, then S-SUB-03 shows a blurred preview **of that trip's own route and timeline** with "Keep exploring every drive with Pro".
  3. Given the user later subscribes, when Pro activates, then all older trips unlock immediately with no data missing.
  4. Given storage management, when raw data is removed by the user, then the trip shows "Detailed data removed" instead of a lock.
- **Platform notes:** same.
- **Analytics:** `locked_content_viewed {type: replay|analysis, age_bucket}`
- **Edge cases:** EC-STO.
- **Privacy:** no data is deleted because of plan state.
- **Free/Pro:** Free summaries; Pro depth.

#### F-HIS-04 · Insights: weekly stats · P0
- **Story:** As a user, I want a simple weekly view of my driving and smoothness trend, so that I see progress at a glance.
- **Screens:** S-STA-01
- **Acceptance criteria:**
  1. Given S-STA-01, when opened, then it shows this week's drives, distance, time, average Smoothness vs last week, a 4-week trend line, contributor changes and the current focus.
  2. Given calibrating, when opened, then it shows "Calibrating" and no trend.
  3. Given Free, when the user taps Month, Year, Per car or Per route, then S-SUB-03 shows a blurred preview of their own data.
- **Platform notes:** same.
- **Analytics:** `insights_opened`, `insights_period_tapped {period}`
- **Edge cases:** EC-PAS (excluded).
- **Privacy:** n/a.
- **Free/Pro:** Free (weekly + 4-week trend). See §9 OQ-07.

#### F-HIS-05 · Pro trends · P0
- **Story:** As an EV owner or enthusiast, I want monthly, yearly, per-car and per-route trends, so that I see long-term change.
- **Screens:** S-STA-01, S-STA-02
- **Acceptance criteria:**
  1. Given Pro, when the user picks Month, Year, Per car or Per route, then S-STA-02 shows Smoothness, contributors, efficiency habits, distance and time over that period with the baseline band.
  2. Given a change claim (e.g. "38% fewer abrupt stops than in March"), when shown, then it appears only if A12's significance rule is met.
- **Platform notes:** same.
- **Analytics:** `trend_viewed {period, dimension}`
- **Edge cases:** EC-PAS.
- **Privacy:** n/a.
- **Free/Pro:** Pro (Gate 1 #6).

#### F-HIS-06 · Merge and split trips · P2
- **Story:** As a user whose drive was split by a long stop, I want to merge two trips (or split one), so that history matches reality.
- **Screens:** S-TRP-02
- **Acceptance criteria:**
  1. Given two consecutive trips < 30 min apart, when the user merges them, then one trip results with recomputed scores and the gap marked as a stop.
- **Platform notes:** same.
- **Analytics:** `trips_merged`, `trip_split`
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** Free.

---

### 3.8 Epic RTE — Routes & Route Records

Records are **Smoothness only, never duration** (A2 §3.2). No live record pace. A complete, gentle stop scores as well as not stopping (F-SCO-07).

#### F-RTE-01 · Route detection and naming · P0
- **Story:** As a commuter, I want my repeated drives grouped into a named route automatically, so that I can track progress on the drives I do most.
- **Screens:** S-RTE-03, S-STY-01, S-NTF-01
- **Acceptance criteria:**
  1. Given a second trip whose start and end cluster within the A12 radius (placeholder 200 m) and whose path is similar (A12 metric), when finalised, then a route candidate is created and the Story shows the merge frame "Looks like a regular. Name this route?" with chips Commute, Gym, School run, Custom.
  2. Given the candidate is not named, when a third matching trip is finalised, then one "Looks like a regular" notification is sent (F-NTF-06).
  3. Given a route, when named, then the default name is never a street address; unnamed routes are "Route 1, Route 2…".
  4. Given direction, when matching, then A→B and B→A are separate routes by default with an option to link them (P1, F-RTE-06).
- **Platform notes:** shared matching.
- **Analytics:** `route_candidate_created`, `route_named {preset|custom}`
- **Edge cases:** EC-GPS (trips with < 60% GNSS coverage don't seed routes), EC-PAS (excluded).
- **Privacy:** route names stay local (and in Pro backup).
- **Free/Pro:** Free (subject to the 2-route limit, F-RTE-05).

#### F-RTE-02 · Routes list and route detail · P0
- **Story:** As a user, I want a page per route showing every run and my record, so that I see my progress on it.
- **Screens:** S-RTE-01, S-RTE-02, S-NAV-01
- **Acceptance criteria:**
  1. Given S-RTE-01, when opened, then each route card shows its glyph, name, number of drives and the route record score with date.
  2. Given S-RTE-02, when opened, then it shows the route glyph, a dot strip of all runs (record highlighted), a Smoothness trend for the route, and each run's duration shown **neutrally** in a table (no deltas, no "faster").
  3. Given no routes, when S-RTE-01 opens, then it explains "Drive the same trip twice and it becomes a route".
- **Platform notes:** same.
- **Analytics:** `routes_opened`, `route_detail_opened`
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** Free (2 routes).

#### F-RTE-03 · Route records (Smoothness only) · P0
- **Story:** As an enthusiast, I want a route record for my smoothest run, so that I have quiet pride in getting better.
- **Screens:** S-RTE-02, S-STY-01, S-NTF-01
- **Acceptance criteria:**
  1. Given an eligible run (driver trip, Good/Usable, scorable, typical pace band, within the exclusions cap), when its Smoothness exceeds the route record, then it becomes the new record and Story frame 4 shows "New route record. {n} on {route}, beating {m} from {date}." with one shimmer and a double haptic — no confetti.
  2. Given a run outside the pace band, when it scores higher, then no record is set and the Story explains why (F-SCO-07).
  3. Given records, when displayed anywhere, then **duration, speed and time are never part of a record**.
  4. Given an active trip, when anything is displayed, then there is **no live record pace** or "on pace for a record".
- **Platform notes:** same.
- **Analytics:** `route_record_set {route_runs_bucket}`
- **Edge cases:** EC-PAS, EC-MOV.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-RTE-04 · Ghost comparison and "where you gained" · P0
- **Story:** As an enthusiast, I want to overlay today's run with my record and see where I gained or lost smoothness, so that I know exactly what to repeat.
- **Screens:** S-RTE-04, S-SUB-03
- **Acceptance criteria:**
  1. Given a route with ≥ 2 runs, when the user taps Compare on a run, then S-RTE-04 overlays the record run as a ghost line and shades distance-aligned segments by smoothness gained or lost.
  2. Given the comparison, when shown, then "Where you gained most: {segment}" and "Where you lost most" appear with evidence links; duration appears neutrally; no time delta is shown.
  3. Given a Free user after the Preview, when Compare is tapped, then S-SUB-03 shows a blurred preview of their own overlay.
- **Platform notes:** same.
- **Analytics:** `ghost_compare_opened {source}`
- **Edge cases:** EC-GPS (segments without alignment are greyed).
- **Privacy:** n/a.
- **Free/Pro:** Pro.

#### F-RTE-05 · Free two-route limit · P0
- **Story:** As a Free user, I want two full routes, and to choose which two, so that the Free tier stays useful.
- **Screens:** S-RTE-01, S-RTE-05, S-SUB-03
- **Acceptance criteria:**
  1. Given a Free user with 2 active routes, when a 3rd route is detected, then it is created and its runs are matched and recorded silently, but its page shows S-SUB-03 with a blurred preview.
  2. Given S-RTE-05, when the Free user swaps which 2 routes are active, then the change applies immediately (no data is lost).
  3. Given the Preview ends with > 2 routes, when Free starts, then the 2 routes with the most runs stay active and the user can change them.
- **Platform notes:** same.
- **Analytics:** `route_limit_hit`, `route_active_swapped`
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** Free 2, Pro unlimited.

#### F-RTE-06 · Route management · P1
- **Story:** As a user, I want to rename, merge, link directions, remove a route or unlink a trip from it.
- **Screens:** S-RTE-05
- **Acceptance criteria:**
  1. Given S-RTE-05, when the user renames, merges two routes, links A→B with B→A, removes a route or unlinks a trip, then records and trends recompute within 2 s and no trip is deleted.
- **Platform notes:** same.
- **Analytics:** `route_managed {action}`
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** Free.

---

### 3.9 Epic GAR — Garage

#### F-GAR-01 · Garage and car profiles · P0
- **Story:** As an enthusiast, I want my car in the app with its name and colour, so that Stories and cards feel like mine.
- **Screens:** S-GAR-01, S-GAR-02, S-GAR-03, S-NAV-01
- **Acceptance criteria:**
  1. Given S-GAR-03, when the user saves a car with nickname (required), optional make, model, year, powertrain and colour, then it appears in S-GAR-01 and its colour tints its Stories.
  2. Given a Free user with one car (after the Preview), when Add car is tapped, then S-SUB-03 explains unlimited cars in Pro; cars added during the Preview stay visible with their trips, and the user picks which one is active.
  3. Given a car is deleted, when confirmed, then its trips are kept and reassigned to "Unknown car" (the user is told).
- **Platform notes:** same; optional photo stored on the device only.
- **Analytics:** `car_added {source, powertrain}`, `car_deleted`
- **Edge cases:** none.
- **Privacy:** no VIN or plate.
- **Free/Pro:** Free 1 car, Pro unlimited.

#### F-GAR-02 · Per-trip car assignment · P0
- **Story:** As a two-car household, I want each trip assigned to the right car, so that stats and Stories are right.
- **Screens:** S-TRP-02, S-STY-01, S-GAR-01
- **Acceptance criteria:**
  1. Given a default car, when a trip records, then it is assigned to the default car.
  2. Given the user changes the car in S-TRP-02 or by tapping the car chip in Story frame 1, when saved, then the trip, its per-car stats and Story tint update.
- **Platform notes:** same.
- **Analytics:** `trip_car_changed {source}`
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** Free (Pro if > 1 active car).

#### F-GAR-03 · Per-car stats · P1
- **Story:** As an EV owner with a second car, I want stats per car, so that I can compare how I drive each.
- **Screens:** S-GAR-02
- **Acceptance criteria:**
  1. Given S-GAR-02, when opened, then it shows drives, distance, time, average Smoothness and efficiency habits for that car, and links to Pro trends per car.
- **Platform notes:** same.
- **Analytics:** `car_detail_opened`
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** Free basic; Pro trends.

#### F-GAR-04 · Bluetooth car auto-assignment and start trigger · P2
- **Story:** As a two-car owner, I want the app to know which car I'm in from its Bluetooth, so that I never reassign trips.
- **Screens:** S-GAR-03
- **Acceptance criteria:**
  1. Given a car paired with a car Bluetooth device in S-GAR-03, when that device connects during a trip, then the trip is assigned to that car; on Android, the connection may also start a trip candidate.
- **Platform notes:** iOS has no public API for classic Bluetooth connection events except audio route changes (A10 to assess); Android `BLUETOOTH_CONNECT` permission needed.
- **Analytics:** `bt_car_linked`
- **Edge cases:** EC-PERM.
- **Privacy:** stores only the device name/identifier locally.
- **Free/Pro:** Free.

---

### 3.10 Epic SHR — Sharing & Referral

Binding: share cards (route shape, no map, trimmed ends) and the Ride-along pass only. No friends, feeds or leaderboards (Gate 1 #8). **Never on a card:** speed, max G, 0–60, duration as an achievement, exact time or date, or start/end points (Gate 1 #12, A2 §4.1).

#### F-SHR-01 · Share card builder · P0
- **Story:** As an enthusiast, I want a beautiful, privacy-safe card of my drive for IG Stories, Reddit or Discord, so that I can show it off without exposing where I live.
- **Screens:** S-SHR-01, S-STY-01, S-TRP-01
- **Acceptance criteria:**
  1. Given a finished trip, when the user taps Share, then S-SHR-01 offers **Route glyph** (default: route shape, no basemap, no place names, Smoothness ring, car name, part of day) and **Ring** (no route at all) styles, in 9:16 and 4:5.
  2. Given any card, when rendered, then it contains none of: speed, max speed, G values, duration as an achievement, timestamps or dates finer than part of day, start or end points, street or place names. A CI snapshot test fails if any of these fields is bound to a card template.
  3. Given the share sheet, when the image is shared, then an alt-text string is offered for copy and a small DrivePerf mark is present (Pro: a more minimal mark, never none).
  4. Given an active trip, when Share is requested, then it is blocked (F-STY-05).
- **Platform notes:** native share sheet; Android share intents; iOS `ShareLink`.
- **Analytics:** `share_opened {source}`, `share_style_selected {style}`, `share_completed {style, format}` (destination not collected)
- **Edge cases:** EC-PAS (no card for passenger trips), Unscorable trips → Route glyph without a ring.
- **Privacy:** cards are generated on the device; nothing is uploaded.
- **Free/Pro:** Free (all privacy-safe styles); Pro extra styles and minimal mark.

#### F-SHR-02 · Privacy trimming and metadata stripping · P0
- **Story:** As a user, I want my start and end hidden and no hidden metadata in images, so that sharing can't reveal my home.
- **Screens:** S-SHR-01, S-PRV-02
- **Acceptance criteria:**
  1. Given a route-glyph or map card, when generated, then the start and end are trimmed by a random 400–800 m each (new random per share) and any part of the route inside a privacy zone (F-PRV-07) is also removed.
  2. Given the glyph, when rendered, then it is not geo-referenced (no coordinates, no north arrow, no scale).
  3. Given any exported image, when inspected, then EXIF/XMP/GPS metadata is absent (test with an EXIF reader).
  4. Given S-SHR-01, when a glyph is previewed, then it shows exactly what will be shared, with a note: "Distinctive route shapes can still be recognised. The Ring card shares no route at all."
- **Platform notes:** same.
- **Analytics:** none extra.
- **Edge cases:** trips shorter than 2 km → glyph style unavailable (trimming would remove most of it); Ring only.
- **Privacy:** core privacy control (A2 §4.1, Strava heatmap precedent).
- **Free/Pro:** Free.

#### F-SHR-03 · Opt-in map card · P1
- **Story:** As a user who wants to show the road I drove, I want a map card that I opt into each time.
- **Screens:** S-SHR-01, S-SHR-02
- **Acceptance criteria:**
  1. Given the Map style, when selected, then S-SHR-02 warns "This shows where you drove" and requires an explicit confirm **every time** (no "don't ask again").
  2. Given the map card, when rendered, then it uses a stylised basemap with place labels off and the same trimming as F-SHR-02.
- **Platform notes:** static map snapshot from the platform map SDK; provider terms to confirm (ADR-005).
- **Analytics:** `share_map_confirmed`
- **Edge cases:** EC-NET (map card unavailable offline).
- **Privacy:** opt-in per share.
- **Free/Pro:** Free.

#### F-SHR-04 · Recap and Wrapped share cards · P1
- **Story:** As a user, I want to share my week or my Wrapped archetype, so that I can show progress without locations.
- **Screens:** S-SHR-01, S-RCP-01, S-WRP-01, S-WRP-02
- **Acceptance criteria:**
  1. Given the weekly recap or Wrapped, when the user taps Share, then Week strip and Archetype cards are offered, containing no locations and only safe stats (Smoothness, drives count, archetype; distance totals allowed; no speed, G or durations as achievements).
  2. Given archetypes, when shown, then only the approved list is used (Glider, Metronome, Climber, Night Owl, Road-Tripper, Urbanist); never risk-glorifying names.
- **Platform notes:** same.
- **Analytics:** `share_completed {style: week|archetype}`
- **Edge cases:** none.
- **Privacy:** no locations.
- **Free/Pro:** Free; Pro extra card styles.

#### F-SHR-05 · Ride-along pass referral · P1
- **Story:** As a happy user, I want to give a friend 30 days of Pro, and get a month myself when they're up and running, so that sharing DrivePerf is rewarding.
- **Screens:** S-SHR-03, S-SHR-04, S-WEB-08
- **Acceptance criteria:**
  1. Given S-SHR-03 (from a share card or Settings), when the user creates a pass, then a link is generated; the sender needs no DrivePerf account (identified by their purchase/app user ID).
  2. Given a new user installs through the link, when they open the app for the first time, then S-SHR-04 confirms **30 days of Pro** (instead of the 14-day Preview).
  3. Given the friend **activates** (always-on capture granted + 3 Stories viewed), when validated by the backend, then the sender gets **1 month of Pro**, capped at **6 months per year**.
  4. Given rewards, when designed, then they never depend on distance, number of drives, ratings or reviews.
  5. Given the link is opened on a device without the app, when tapped, then S-WEB-08 shows store badges and keeps the pass for first open (deferred link; mechanism per A10).
- **Platform notes:** reward via RevenueCat promotional entitlements; validation requires a server function (Firebase **Blaze**, §8). iOS: Apple has no referral-specific rule confirmed (A2 §4.2); check before launch.
- **Analytics:** `ridealong_created`, `ridealong_link_opened`, `ridealong_redeemed`, `ridealong_reward_granted`
- **Edge cases:** EC-NET (redemption retried), abuse (same device redeeming twice → rejected by install/device check).
- **Privacy:** the friend's activity is reported to the sender only as "Your friend is set up"; no data about the friend's drives.
- **Free/Pro:** Free to send.

---

### 3.11 Epic ACC — Account & Cloud Backup

Binding: local-first, account optional; sign-in only for cloud backup and multi-device (Gate 1 #7). **Free users who sign in upload no trip data.**

#### F-ACC-01 · Local-first, no account required · P0
- **Story:** As a privacy-aware user, I want every Free feature to work without an account, so that I don't hand over personal data to use the app.
- **Screens:** S-ACC-01, S-NAV-01
- **Acceptance criteria:**
  1. Given a user who never signs in, when using every Free feature and the Pro Preview, then nothing requires an account, and no trip, route, car or score data leaves the device (verified by a network-traffic test).
  2. Given S-ACC-01, when not signed in, then it shows "Local mode: your data is on this phone" and the phone's OS backup status note (iOS iCloud device backup / Android Auto Backup as configured, A11).
  3. Given a purchase without an account, when made, then Pro works and can be restored with the store's restore-purchases (F-SUB-06).
- **Platform notes:** A11 decides whether app data is included in OS device backups (default: included, encrypted by the OS).
- **Analytics:** `profile_opened {signed_in: false}`
- **Edge cases:** EC-NET.
- **Privacy:** the core trust promise.
- **Free/Pro:** Free.

#### F-ACC-02 · Optional sign-in (Apple, Google, email link) · P0
- **Story:** As a user who wants backup or a second device, I want to sign in with Apple, Google or an email link.
- **Screens:** S-ACC-02, S-ACC-03
- **Acceptance criteria:**
  1. Given S-ACC-02, when shown, then Sign in with Apple, Google and email link are offered (Apple 4.8: Sign in with Apple must be offered alongside Google on iOS).
  2. Given email link, when requested, then S-ACC-03 says "Check your email" and the link signs the user in on the same device.
  3. Given a successful sign-in, when complete, then the purchase identity is linked to the account (RevenueCat app user ID = Firebase UID) so Pro follows the account.
  4. Given sign-in, when complete as a Free user, then no trip data is uploaded and the screen says so.
- **Platform notes:** Firebase Auth (Spark). Sign in with Apple needs the paid Apple account (§8); on Android it runs through the web flow. Email-link sign-in uses the Firebase Hosting link domain (A17 to confirm).
- **Analytics:** `sign_in_started {method}`, `sign_in_completed {method}`, `sign_in_failed {method, reason}`
- **Edge cases:** EC-NET.
- **Privacy:** only name (optional), email and UID; Apple private relay emails supported.
- **Free/Pro:** Free.

#### F-ACC-03 · Cloud backup · P0
- **Story:** As a Pro user, I want my trips backed up to the cloud, so that I never lose them if I lose or change my phone.
- **Screens:** S-ACC-04, S-ACC-05, S-SUB-03
- **Acceptance criteria:**
  1. Given Pro and signed in, when the user turns on backup, then S-ACC-05 first explains what leaves the device (routes with precise location, sensor data, scores, cars), where it is stored (Firebase in the US for records; Cloudflare R2 in North America for trip files), and that it can be deleted any time, and requires explicit consent.
  2. Given backup is on, when the phone is on Wi-Fi (default; "also on cellular" option) and not in a trip, then new trips upload within 24 h as compressed, versioned trip files (object storage, Cloudflare R2) plus summaries (Firestore), via WorkManager / `BGProcessingTask`; nothing uploads during a trip.
  3. Given S-ACC-04, when opened, then it shows last backup time, items pending, and storage used.
  4. Given a trip deleted on the device, when the next sync runs, then it is deleted from the cloud.
  5. Given Pro lapses, when detected, then backup stops, the cloud copy is retained for the period decided in §9 OQ-09 with in-app warnings, then deleted.
- **Platform notes:** **Requires Firebase Blaze** for the Cloud Run API and Cloud Scheduler (ADR-004); trip files are stored on Cloudflare R2 (decisions log, 6 Nov 2026). Clients talk to Firebase only through the shared `SyncRepository` (kickoff plan §3).
- **Analytics:** `backup_enabled`, `backup_disabled`, `backup_run {result, items_bucket}`
- **Edge cases:** EC-NET (queued), EC-STO, EC-BAT (upload only when charging or > 50%).
- **Privacy:** consent before transfer (Apple 5.1.5; PIPEDA; Law 25 transfer outside Quebec needs a PIA, §6.5). Encrypted in transit and at rest.
- **Free/Pro:** Pro.

#### F-ACC-04 · Restore and multi-device · P0
- **Story:** As a Pro user with a new phone, I want to sign in and get all my drives back.
- **Screens:** S-ACC-06
- **Acceptance criteria:**
  1. Given a new device and a signed-in Pro account with a backup, when the user taps Restore, then summaries appear within 60 s (for ≤ 1,000 trips on Wi-Fi) and trip files download in the background with progress.
  2. Given two devices on one account, when both record, then trips from both appear on both after sync; conflicts resolve by A11 rules (trips are immutable; edits last-writer-wins with a per-field timestamp).
  3. Given restore, when interrupted, then it resumes without duplicates (idempotent trip IDs).
- **Platform notes:** same.
- **Analytics:** `restore_started`, `restore_completed {trips_bucket, ms_bucket}`
- **Edge cases:** EC-NET, EC-STO (warn before download).
- **Privacy:** n/a.
- **Free/Pro:** Pro.

#### F-ACC-05 · Sign out · P1
- **Story:** As a user, I want to sign out and choose what happens to the data on this phone.
- **Screens:** S-ACC-07
- **Acceptance criteria:**
  1. Given S-ACC-07, when the user signs out, then they choose "Keep my drives on this phone" (default) or "Remove my drives from this phone"; cloud data is untouched.
- **Platform notes:** same.
- **Analytics:** `signed_out {keep_local}`
- **Edge cases:** pending uploads → warn before sign-out.
- **Privacy:** n/a.
- **Free/Pro:** Free.

---

### 3.12 Epic PRV — Privacy Centre, Export & Deletion

Privacy basics (delete, full data download) are **never** paywalled (A2 §5.3).

#### F-PRV-01 · Privacy Centre · P0
- **Story:** As a user, I want one place that shows what is stored where and lets me control it, so that I trust DrivePerf.
- **Screens:** S-PRV-01
- **Acceptance criteria:**
  1. Given S-PRV-01, when opened, then it shows a plain data map: on this phone (trips, routes, cars, scores), in the cloud (only if backup is on), crash reports and usage statistics (per the user's choice), and "never: sold, shared with insurers, used for ads".
  2. Given each item, when tapped, then it links to the control (backup, privacy zones, analytics, download, delete) and to the privacy policy section.
  3. Given the privacy officer contact (Law 25), when shown, then the title and contact address are listed.
- **Platform notes:** same.
- **Analytics:** `privacy_centre_opened`
- **Edge cases:** none.
- **Privacy:** PIPEDA openness; Law 25 transparency.
- **Free/Pro:** Free.

#### F-PRV-02 · Download all my data · P0
- **Story:** As a user, I want all my data in an open format, so that it's truly mine (and so I can leave).
- **Screens:** S-PRV-03
- **Acceptance criteria:**
  1. Given S-PRV-03, when the user taps Download, then a ZIP is generated on the device containing: trips (JSON summaries, events, scores), raw trip files in the documented portable schema (optional toggle "include raw sensor data"), routes, cars, settings, consents, and a README describing every field and unit.
  2. Given 500 trips without raw data, when generated, then it completes in ≤ 30 s on reference devices, with progress and the ability to continue in the background (iOS `BGContinuedProcessingTask` where available).
  3. Given a signed-in user, when generated, then account data (profile, consents, entitlement history) is included.
- **Platform notes:** share sheet / Files / Storage Access Framework.
- **Analytics:** `data_download_started {raw}`, `data_download_completed {size_bucket}`
- **Edge cases:** EC-STO (warn if insufficient space).
- **Privacy:** Law 25 portability; US state access/portability rights.
- **Free/Pro:** Free (Gate 1 #6).

#### F-PRV-03 · Per-trip GPX/CSV export · P0
- **Story:** As a data tinkerer, I want a trip as GPX and CSV with channels and provenance, so that I can analyse it in my own tools.
- **Screens:** S-PRV-04, S-TRP-01
- **Acceptance criteria:**
  1. Given S-PRV-04, when the user exports a trip as GPX, then a valid GPX 1.1 file with time, elevation and speed (extension) per point is produced and passes a GPX validator.
  2. Given CSV, when exported, then columns include timestamp (ISO 8601 UTC), lat, lon, GNSS accuracy, speed, long-G, lat-G, quality flags and a provenance column, with a header row and units.
  3. Given a date range, when exported (batch, P1 within this feature), then one ZIP holds one file per trip.
- **Platform notes:** same.
- **Analytics:** `trip_exported {format, batch}`
- **Edge cases:** EC-STO (raw removed → GNSS-only export, flagged).
- **Privacy:** export files contain precise location; the sheet says so.
- **Free/Pro:** Pro (Gate 1 #6).

#### F-PRV-04 · Delete trips and all local data · P0
- **Story:** As a user, I want to delete a trip, a range, or everything, so that I control my history.
- **Screens:** S-PRV-05, S-TRP-03
- **Acceptance criteria:**
  1. Given S-PRV-05, when the user deletes all trips (typed confirmation "DELETE"), then all trips, raw files, routes and derived data are removed from the device within 30 s, and from the cloud within 24 h if backup is on.
  2. Given "Delete trips older than…" (3/6/12 months), when confirmed, then matching trips are deleted and baselines recompute.
  3. Given deletion, when complete, then a confirmation states what was deleted and that it cannot be undone.
- **Platform notes:** same.
- **Analytics:** `data_deleted {scope}`
- **Edge cases:** pending uploads cancelled first.
- **Privacy:** real deletion (PIPEDA retention, Law 25, US state deletion rights).
- **Free/Pro:** Free.

#### F-PRV-05 · Delete account and cloud data · P0
- **Story:** As a signed-in user, I want to delete my account and all cloud data from inside the app.
- **Screens:** S-PRV-06, S-WEB-07
- **Acceptance criteria:**
  1. Given S-PRV-06, when the user starts deletion, then they re-authenticate, see what will be deleted (account, cloud trips, backup files, referral records), and confirm.
  2. Given confirmation, when processed, then the Auth user, Firestore documents and Storage files are deleted within 24 h (cascade, A17), local data is kept or removed per the user's choice, and a confirmation is shown.
  3. Given an active subscription, when deleting, then the app explains that the store subscription must be cancelled in the store and links to it (Apple guidance).
  4. Given a user without the app, when they visit S-WEB-07, then they can request deletion (Google Play account-deletion web-resource requirement).
- **Platform notes:** Apple 5.1.1(v); Play account-deletion policy. Robust cascade uses Cloud Functions (Blaze).
- **Analytics:** `account_deletion_requested`, `account_deleted` (anonymous count)
- **Edge cases:** EC-NET (queued with retry; user told).
- **Privacy:** includes service providers (RevenueCat customer deletion). Crash reports in Sentry are never linked to the account and expire with Sentry's retention.
- **Free/Pro:** Free.

#### F-PRV-06 · Analytics and diagnostics controls · P0
- **Story:** As a user, I want to change my analytics and crash-report choices any time.
- **Screens:** S-PRV-07, S-ONB-13
- **Acceptance criteria:**
  1. Given S-PRV-07, when the user toggles "Share usage statistics" or "Send crash reports", then collection stops or starts within the session, and "Reset analytics ID" deletes the pseudonymous ID.
  2. Given usage statistics off, when the app runs, then no analytics network calls are made (network test).
- **Platform notes:** Firebase Analytics `setAnalyticsCollectionEnabled`; Sentry `init` / `close()` for crash reports (ADR-007).
- **Analytics:** `analytics_consent_set` (only when turned on).
- **Edge cases:** none.
- **Privacy:** §6.4.
- **Free/Pro:** Free.

#### F-PRV-07 · Privacy zones · P1
- **Story:** As a user, I want to mark home and work as private zones, so that share cards never show the area around them.
- **Screens:** S-PRV-02, S-SHR-01
- **Acceptance criteria:**
  1. Given S-PRV-02, when the user adds a zone (from a recent parking spot or by moving a pin; radius 200/400/800 m), then shared cards remove any route part inside it, in addition to random trimming.
  2. Given zones, when stored, then they are kept on the device and, when Pro backup is on, included in the backup (same consent) so a restored phone keeps them; they never appear in analytics or on share cards. *(Changed at Gate 4, 6 Nov 2026.)*
- **Platform notes:** same.
- **Analytics:** `privacy_zone_added {radius}` (no location)
- **Edge cases:** none.
- **Privacy:** exposure reduction, not anonymisation (the UI says so).
- **Free/Pro:** Free.

---

### 3.13 Epic SUB — Subscription, Pro Preview & Paywall

Binding: US$6.99/mo · US$39.99/yr; CA$8.99/mo · CA$49.99/yr; founding first year US$29.99 / CA$39.99; no weekly and no lifetime plan (Gate 1 #4). 14-day no-card Pro Preview, then a day-15 paywall (Gate 1 #5). Free/Pro split per Gate 1 #6.

**Free vs Pro (binding summary)**

| Capability | Free | Pro |
|---|---|---|
| Recording (auto + manual), Drive Story, Smoothness/events/efficiency habits, coaching tip | ✅ | ✅ |
| Trip summaries | ✅ forever ("locked, not lost") | ✅ |
| Trip Replay and Analysis | Last 30 days | Full history + range stats and advanced channels |
| Routes and records | 2 active routes | Unlimited + ghost comparison and "where you gained" |
| Trends | Weekly + 4-week line | Month, year, per car, per route |
| Weekly recap; Drive Wrapped | ✅; core chapters | ✅; extra chapters and card styles |
| Share cards; Ride-along pass | ✅ | ✅ + extra styles, minimal mark |
| Garage | 1 car | Unlimited |
| Download all my data; delete anything | ✅ | ✅ |
| Per-trip GPX/CSV export | — | ✅ |
| Cloud backup and restore | — | ✅ |

#### F-SUB-01 · Plans and entitlements · P0
- **Story:** As a user who wants Pro, I want clear monthly and annual plans at fair local prices.
- **Screens:** S-SUB-04, S-SUB-05
- **Acceptance criteria:**
  1. Given the US storefront, when S-SUB-04 shows, then plans are US$6.99/month and US$39.99/year; given the Canadian storefront, CA$8.99 and CA$49.99; prices come from the store, never hard-coded.
  2. Given S-SUB-04, when shown, then **annual is pre-selected**, monthly is visible, and the screen shows price, period, auto-renewal, how to cancel, Terms and Privacy links and Restore purchases (Apple 3.1.2, Play subscriptions policy).
  3. Given a purchase, when complete, then the `pro` entitlement is active on that device within 5 s and S-SUB-05 confirms what's unlocked.
  4. Given the catalogue, when audited, then there is **no weekly and no lifetime product**.
- **Platform notes:** StoreKit 2 and Play Billing via RevenueCat; one subscription group "DrivePerf Pro". In-App Purchase needs the paid Apple account.
- **Analytics:** `paywall_viewed {source}`, `plan_selected {plan}`, `purchase_started`, `purchase_completed {plan, offer}`, `purchase_failed {reason}`
- **Edge cases:** EC-NET (purchase needs network; clear error), Ask to Buy / pending purchases.
- **Privacy:** RevenueCat as service provider; no tracking.
- **Free/Pro:** n/a.

#### F-SUB-02 · 14-day Pro Preview (no card) · P0
- **Story:** As a new user, I want two weeks of full Pro without a card, so that I can feel the value before deciding.
- **Screens:** S-SUB-01, S-HOME-01
- **Acceptance criteria:**
  1. Given onboarding completes, when Home first appears, then Pro is active for 14 × 24 h, with no store transaction and no card.
  2. Given the Preview, when Home or Profile shows, then a small status ("Pro Preview · day {n} of 14") opens S-SUB-01 listing what's included.
  3. Given the app is reinstalled on iOS, when the Preview was already used, then it is not restarted (first-open date kept in the Keychain); on Android, best effort (A10).
  4. Given a Ride-along redemption, when applied, then the Preview becomes 30 days (F-SHR-05).
- **Platform notes:** implemented as a local entitlement in the shared core (`isPro = store || ridealong || previewActive`); no server needed.
- **Analytics:** `preview_started`, `preview_day {n}` (days 1, 7, 13, 14)
- **Edge cases:** clock changes (use monotonic + first-open timestamp; ignore backward changes).
- **Privacy:** n/a.
- **Free/Pro:** Pro during the Preview.

#### F-SUB-03 · Day-15 paywall: "Your first two weeks, decoded" · P0
- **Story:** As a user at the end of the Preview, I want a recap of what Pro showed me and a fair choice, so that I decide with my own data.
- **Screens:** S-SUB-02, S-SUB-04, S-SUB-07
- **Acceptance criteria:**
  1. Given the Preview has ended and the user has ≥ 3 driver trips, when the app is next opened in the foreground (not in a trip), then S-SUB-02 plays a short recap of the 14 days (drives, smoothest drive, routes found, comparisons made) and ends with: "Pro showed you {r} routes and {c} comparisons. Keep it for {annual price}/yr. Your first year is {founding price}." (founding line only when eligible).
  2. Given S-SUB-02, when the choice appears, then **"Continue with Free" has equal visual weight** to the purchase button.
  3. Given fewer than 3 trips, when the Preview ends, then S-SUB-07 ("You're on Free — here's what stays") is shown instead (§9 OQ-08 on extending the Preview).
  4. Given S-SUB-02 has been shown once, when the user continues with Free, then it is never shown again automatically.
- **Platform notes:** same.
- **Analytics:** `day15_paywall_viewed`, `day15_paywall_choice {purchase|free}`
- **Edge cases:** first open after day 15 is during a trip → wait until parked (F-SUB-08).
- **Privacy:** recap computed on the device.
- **Free/Pro:** n/a.

#### F-SUB-04 · Contextual locks with the user's own data · P0
- **Story:** As a Free user, I want to see what Pro would show me on my own drive when I hit a limit, so that the upgrade is concrete.
- **Screens:** S-SUB-03
- **Acceptance criteria:**
  1. Given a Free user taps a Pro feature (compare with record, Replay/Analysis older than 30 days, 3rd route, 2nd active car, GPX/CSV export, Pro trends, cloud backup), when S-SUB-03 opens, then it shows a **blurred preview of that user's own data** for that feature (never a stock screenshot), one sentence of value, "See plans" and "Not now".
  2. Given "Not now", when tapped, then the user returns exactly where they were.
- **Platform notes:** same.
- **Analytics:** `lock_viewed {feature}`, `lock_cta {see_plans|not_now}`
- **Edge cases:** no data for that feature yet → feature description only.
- **Privacy:** n/a.
- **Free/Pro:** n/a.

#### F-SUB-05 · Founding-member first-year offer · P0
- **Story:** As an early believer (waitlist, beta, launch month), I want a founding price for my first year.
- **Screens:** S-SUB-02, S-SUB-04
- **Acceptance criteria:**
  1. Given an eligible user, when S-SUB-04 shows, then the annual plan shows "US$29.99 for your first year, then US$39.99/yr" (CA$39.99 then CA$49.99), using a store introductory "pay up front" offer or offer code.
  2. Given eligibility, when determined, then it covers: (a) everyone who installs during the first 30 days after launch; (b) waitlist and Founding Drivers beta members via codes (mechanism per §9 OQ-10).
  3. Given a user who already used an intro offer in the group, when eligibility is checked, then the store's rule applies (one per subscription group).
- **Platform notes:** Apple introductory offers and offer codes; Play introductory pricing / developer-determined offers.
- **Analytics:** `offer_shown {offer}`, `purchase_completed {offer: founding}`
- **Edge cases:** none.
- **Privacy:** waitlist email is not linked to the purchase identity unless the user signs in with the same email.
- **Free/Pro:** n/a.

#### F-SUB-06 · Manage subscription and restore purchases · P0
- **Story:** As a subscriber, I want to see my plan, renew date, and restore or manage it easily.
- **Screens:** S-SUB-06
- **Acceptance criteria:**
  1. Given S-SUB-06, when opened, then it shows plan, price, renewal date, source (Preview / Ride-along / store), and buttons Manage in App Store / Google Play and Restore purchases.
  2. Given Restore, when tapped on a fresh install, then an active store subscription restores Pro within 10 s.
- **Platform notes:** StoreKit `showManageSubscriptions`; Play subscription deep link.
- **Analytics:** `subscription_managed`, `restore_purchases {result}`
- **Edge cases:** EC-NET.
- **Privacy:** n/a.
- **Free/Pro:** n/a.

#### F-SUB-07 · Preview ended / "You're on Free" state · P0
- **Story:** As a user whose Preview ended, I want to know exactly what stays and what changes, so that nothing feels taken.
- **Screens:** S-SUB-07
- **Acceptance criteria:**
  1. Given the Preview ends, when S-SUB-07 shows, then it lists: what stays free (all summaries forever, Stories, scores, recaps, 30-day Replay/Analysis, 2 routes, 1 car, full data download) and what pauses (older analysis, extra routes and cars, comparisons, exports, backup) with "Locked, not lost."
  2. Given the user had > 2 routes or > 1 car, when Free starts, then S-SUB-07 lets them pick which stay active (defaults per F-RTE-05).
- **Platform notes:** same.
- **Analytics:** `preview_ended {trips_bucket}`
- **Edge cases:** none.
- **Privacy:** no data deleted.
- **Free/Pro:** n/a.

#### F-SUB-08 · Paywall guardrails · P0
- **Story:** As DrivePerf, we never show a paywall at a bad moment, so that monetisation doesn't cost trust.
- **Screens:** S-SUB-02, S-SUB-03, S-SUB-04
- **Acceptance criteria:**
  1. Given onboarding, when it runs, then no paywall or price is shown (only "Pro Preview included").
  2. Given an active trip or S-DRV-05 conditions, when a paywall would show, then it is suppressed.
  3. Given a drive whose Smoothness is below the user's baseline, when its Story ends, then no paywall or lock is shown on that Story's end card.
  4. Given notifications, when sent, then none contains a price or upgrade prompt except the day-13 "Preview ends in 2 days" notice.
  5. Given rating prompts, when used, then the system review prompt is requested only after ≥ 5 Stories viewed and never before the first weekly recap (A1 §9).
- **Platform notes:** `SKStoreReviewController` / Play In-App Review.
- **Analytics:** `paywall_suppressed {reason}`, `review_prompt_requested`
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** n/a.

---

### 3.14 Epic NTF — Notifications, Weekly Recap & Drive Wrapped

All v1 notifications are **local** (generated on the device): no push server, no APNs/FCM dependency for v1 features.

#### F-NTF-01 · Notification framework · P0
- **Story:** As a user, I want few, useful notifications that never arrive while I drive.
- **Screens:** S-NTF-01, S-SET-04
- **Acceptance criteria:**
  1. Given any notification other than the trip's own recording notice, when it would fire during an active trip, then it is held until the trip ends and the phone is parked.
  2. Given quiet hours (default 22:00–07:00, editable), when a notification would fire, then it is delayed to the end of quiet hours (capture-problem notices excepted if the user opts in).
  3. Given caps, when counted, then there is at most 1 post-drive notification per drive (coalesced), 1 recap per week, and no "we miss you" sequences; the lapsed notice is sent once ever.
  4. Given S-SET-04, when the user turns a type off, then it is never sent again.
- **Platform notes:** iOS notification categories; Android channels per type (users can also control channels in system settings).
- **Analytics:** `notification_sent {type}`, `notification_held {type, reason}`, `notification_opened {type}`, `notification_type_disabled {type}`
- **Edge cases:** EC-PERM (denied), time-zone and DST changes (schedule in local time).
- **Privacy:** no locations or place names in notification text except user-named routes.
- **Free/Pro:** Free.

#### F-NTF-02 · Capture-problem notifications · P0
- **Story:** As a user, I want to be told immediately when recording can't work, with a quick fix.
- **Screens:** S-NTF-01, S-SET-05
- **Acceptance criteria:**
  1. Given Always/background location is downgraded, precise location is lost, motion access is revoked, Background App Refresh is off, or notifications are off, when detected, then one notification (or an in-app banner if notifications are off) says e.g. "**Recording is paused.** Location access changed to 'While Using'. Two taps to fix it." and opens Health Check.
  2. Given the same problem persists, when re-detected, then the reminder is sent at most once per 7 days.
- **Platform notes:** detection at launch, wake and on `locationManagerDidChangeAuthorization` (iOS) / permission checks at transition receipt (Android).
- **Analytics:** `capture_problem_detected {problem}`, `capture_problem_fixed {problem, hours_bucket}`
- **Edge cases:** EC-PERM, EC-LPM, EC-OEM.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-NTF-03 · Weekly recap · P0
- **Story:** As a user, I want a short weekly recap every Sunday, so that I see progress and have something to share.
- **Screens:** S-RCP-01, S-NTF-01
- **Acceptance criteria:**
  1. Given ≥ 1 driver trip in the week, when it is Sunday 18:00 local (and not in a trip), then a notification "**Your week, decoded.** {n} drives · Smoothness {avg} ({±d})" is sent and S-RCP-01 plays frames W1–W5 (A2 §3.3): week strip, 4-week trend, drive of the week, focus habit, share.
  2. Given the trend went down, when W2 plays, then it explains without blame ("Thursday's drive pulled it down. Here's why →").
  3. Given an empty week, when the user opens recaps, then "No drives this week. Your car got a rest." is shown, and **no notification** is sent.
  4. Given the recap, when computed, then it is generated on the device with no server.
- **Platform notes:** local scheduling; recomputed if late trips arrive before opening.
- **Analytics:** `recap_generated {drives_bucket}`, `recap_opened`, `recap_completed`, `recap_shared`
- **Edge cases:** EC-PAS (excluded), DST.
- **Privacy:** on device.
- **Free/Pro:** Free.

#### F-NTF-04 · Monthly Wrapped · P1
- **Story:** As a user, I want a monthly mini-Wrapped with an archetype, so that I have a share moment before the year ends.
- **Screens:** S-WRP-01, S-SHR-01
- **Acceptance criteria:**
  1. Given ≥ 4 driver trips in the month, when the 1st of the next month arrives (10:00 local), then a notification "**{Month}, wrapped.** You're The {Archetype} this month." is sent and S-WRP-01 plays 5 frames (totals, trend, best drive, focus, archetype).
  2. Given the launch, when 14 days have passed since a user's install in the launch month, then a "Season Opener" variant is available once (A2 §4.4).
- **Platform notes:** on device.
- **Analytics:** `wrapped_opened {type: monthly}`, `wrapped_completed`, `wrapped_shared`
- **Edge cases:** none.
- **Privacy:** no locations on cards.
- **Free/Pro:** Free core; Pro extra chapters and styles.

#### F-NTF-05 · Annual Drive Wrapped · P1 (must ship by 1 Dec 2027)
- **Story:** As a user, I want my driving year told as a story in December, so that I celebrate a year of smooth driving.
- **Screens:** S-WRP-02, S-SHR-01
- **Acceptance criteria:**
  1. Given ≥ 10 driver trips across ≥ 30 days in the calendar year, when 1 December arrives, then S-WRP-02 is available with frames per A2 §3.4 (km, hours and most-driven hour, archetype, smoothest drive, most improved route, Garage split, habit mastered only if statistically real, share).
  2. Given comparisons ("about {n}× …"), when shown, then they come only from a vetted list and are labelled Estimated.
  3. Given any frame, when shown, then there is no speed, no max G, no other-user comparison, and no praise for driving volume.
- **Platform notes:** on device.
- **Analytics:** `wrapped_opened {type: annual}`, `wrapped_frame_viewed {frame}`, `wrapped_shared {card}`
- **Edge cases:** single car → single-car frame.
- **Privacy:** on device.
- **Free/Pro:** Free core; Pro extra chapters (per-route deep dives, card styles).

#### F-NTF-06 · Lifecycle notifications · P1
- **Story:** As a user, I want a few well-timed nudges (route discovered, new record, Preview ending), and nothing guilt-driven.
- **Screens:** S-NTF-01
- **Acceptance criteria:**
  1. Given the third unnamed matching trip, when finalised, then "**Looks like a regular.** Name the route you've now driven 3 times?" is sent once per route.
  2. Given a new route record, when set, then "**New route record on {route}.** {n}, beating {m} from {date}." replaces that drive's post-drive notification (no extra notification).
  3. Given day 13 of the Preview, when reached, then "**Your Pro Preview ends in 2 days.** Your trips stay yours either way." is sent.
  4. Given 21 days without opening the app while capture still works, when reached, then "**Recording is still on.** We'll tell you when the next drive is decoded." is sent **once ever**.
- **Platform notes:** same.
- **Analytics:** `notification_sent {type}`
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** Free.

---

### 3.15 Epic SET — Settings & Health Check

#### F-SET-01 · Settings, units and display · P0
- **Story:** As a user, I want to set units, appearance and recording preferences.
- **Screens:** S-SET-01, S-SET-02, S-SET-03
- **Acceptance criteria:**
  1. Given a Canadian storefront/locale, when first run, then units default to metric (km, km/h, m); US defaults to imperial (mi, mph, ft); G is always in g; the user can override in S-SET-02.
  2. Given a units change, when saved, then every screen, card and export header reflects it immediately (exports may keep SI with a units row; A11).
  3. Given S-SET-03, when opened, then it offers: Automatic trips on/off, default car, "Battery saver recording" (IMU 25 Hz always), and links to Health Check and Pause.
  4. Given appearance, when set to System/Light/Dark, then it applies immediately.
- **Platform notes:** iOS Settings bundle not used; in-app only.
- **Analytics:** `setting_changed {key}` (no values for free text)
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-SET-02 · Health Check · P0
- **Story:** As a user, I want one screen that tells me whether automatic capture will work and how to fix it.
- **Screens:** S-SET-05, S-HOME-01, S-HOME-02
- **Acceptance criteria:**
  1. Given S-SET-05, when opened, then it shows green/amber/red items with a fix action each: location (Always / all the time), precise location, motion/activity, notifications, Background App Refresh (iOS), Low Power Mode / Battery Saver, battery optimisation and OEM sleep lists (Android), Live Activities enabled (iOS), last check-in time, last trip time, auto-detect paused state, storage.
  2. Given any red item, when Home shows, then a single status chip ("Auto-detect needs attention") opens S-SET-05.
  3. Given all green, when shown, then it reads "Automatic trips are ready." with the honest capture promise in one line.
  4. Given iOS `backgroundRefreshStatus` or Android `isIgnoringBatteryOptimizations` changes, when the app is foregrounded, then the screen updates within 1 s.
- **Platform notes:** platform-specific checks.
- **Analytics:** `health_check_opened {source, reds, ambers}`, `health_fix_tapped {item}`
- **Edge cases:** EC-PERM, EC-LPM, EC-OEM, EC-FQ, EC-STO.
- **Privacy:** checks run locally.
- **Free/Pro:** Free.

#### F-SET-03 · Reliability guide · P0
- **Story:** As a user whose phone kills background apps, I want brand-specific steps, so that I can keep auto-detect alive.
- **Screens:** S-SET-06, S-ONB-12
- **Acceptance criteria:**
  1. Given Android, when S-SET-06 opens, then it shows the detected brand's steps (Samsung, Xiaomi/Redmi/POCO, OnePlus, Oppo/Realme, Vivo, Huawei/Honor, Google) with deep links where available and a "Test my setup" action that confirms triggers are registered.
  2. Given iOS, when opened, then it covers Background App Refresh, Low Power Mode, force-quit and Live Activities settings.
  3. Given content, when maintained, then it is served as bundled content updatable via Remote Config without an app release (F-ANA-05).
- **Platform notes:** platform variants (Android-heavy).
- **Analytics:** `reliability_guide_viewed {oem}`, `reliability_test_run {result}`
- **Edge cases:** EC-OEM.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-SET-04 · Storage management · P1
- **Story:** As a heavy driver, I want to see and control how much space DrivePerf uses, without losing my history.
- **Screens:** S-SET-07
- **Acceptance criteria:**
  1. Given S-SET-07, when opened, then it shows space used by summaries, route shapes and raw sensor data.
  2. Given "Remove detailed sensor data older than {6/12/24} months", when confirmed, then raw files are deleted while summaries, scores, route shapes and Stories remain; affected trips show "Detailed data removed" (F-HIS-03).
  3. Given processing, when a trip is finalised, then its stored size is ≤ 5 MB per hour of driving (target; A11 confirms format and downsampling).
- **Platform notes:** same.
- **Analytics:** `storage_viewed {size_bucket}`, `raw_data_removed {months}`
- **Edge cases:** EC-STO.
- **Privacy:** supports retention limits (PIPEDA principle 5).
- **Free/Pro:** Free.

#### F-SET-05 · About, legal and support · P0
- **Story:** As a user, I want the app version, legal documents, open-source licences and a way to get help.
- **Screens:** S-SET-08
- **Acceptance criteria:**
  1. Given S-SET-08, when opened, then it shows version/build, scoring algorithm version, Privacy Policy, Terms, Open-source licences, Contact support (email), the privacy officer contact, and a link to S-WEB-05.
- **Platform notes:** same.
- **Analytics:** `about_opened`, `support_contacted`
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** Free.

#### F-SET-06 · Report a problem · P1
- **Story:** As a beta tester or user, I want to report a bug with diagnostics I choose to include.
- **Screens:** S-SET-09
- **Acceptance criteria:**
  1. Given S-SET-09, when the user describes a problem, then they can optionally attach a diagnostics bundle (app logs without locations, device model, OS, permission states, last 7 days of capture-health events) and preview exactly what is sent.
  2. Given "Attach a trip for debugging", when chosen, then the user explicitly selects a trip and is told it contains precise location.
- **Platform notes:** email composer or a support endpoint (A10).
- **Analytics:** `problem_reported {has_diagnostics, has_trip}`
- **Edge cases:** EC-NET.
- **Privacy:** explicit, per-report consent.
- **Free/Pro:** Free.

---

### 3.16 Epic WEB — Website & Waitlist

Informational site plus a waitlist on Firebase Hosting (Spark); Astro static build (kickoff plan §3). No web account dashboard in v1.

#### F-WEB-01 · Landing page with positioning A/B test · P0
- **Story:** As a car enthusiast who saw a post, I want to understand DrivePerf quickly and join the waitlist.
- **Screens:** S-WEB-01, S-WEB-11
- **Acceptance criteria:**
  1. Given a visit, when the page loads, then each visit is randomly assigned variant A (lead "Smooth is a skill.", support "Every drive, decoded.") or B (lead "Every drive, decoded.", recap-led), without cookies or storage, and the variant is recorded with any waitlist sign-up and in cookie-less analytics.
  2. Given the page, when audited with Lighthouse (mobile), then Performance, Accessibility, Best Practices and SEO are each ≥ 95, it is usable at 360 px wide, and it supports dark mode.
  3. Given content, when reviewed, then it contains no insurance claims, no speed glorification, no energy-saving claims, and shows the honest capture promise and "Private by design" proof point; phone mockups show both iOS and Android.
  4. Given an unknown URL, when visited, then S-WEB-11 (404) is served with a link home.
  5. Given meta, when shared, then Open Graph/Twitter cards and schema.org `SoftwareApplication` are present.
- **Platform notes:** Astro on Firebase Hosting; preview channel per PR.
- **Analytics:** cookie-less page views and `waitlist_submitted {variant}`; honour Global Privacy Control and Do Not Track.
- **Edge cases:** JavaScript disabled → content readable; form posts gracefully.
- **Privacy:** no third-party trackers; no cookies by default (Law 25 cookie stance, §6.5).
- **Free/Pro:** n/a.

#### F-WEB-02 · Waitlist · P0
- **Story:** As an interested driver, I want to join the waitlist for my platform, so that I hear about the beta and the founding price.
- **Screens:** S-WEB-01, S-WEB-02
- **Acceptance criteria:**
  1. Given the form, when submitted, then it collects email (required), platform (iPhone / Android), country (Canada / US / Other), an **unticked** consent checkbox for launch emails (CASL express consent), and the A/B variant; nothing else.
  2. Given submission, when valid, then a document is created in a **create-only** Firestore collection (rules validate fields and size; no reads from the client), and S-WEB-02 confirms.
  3. Given spam, when submitted by bots, then a honeypot field and rate limiting reject it; App Check is used if it doesn't add cookies (§9 OQ-13).
  4. Given the consent record, when stored, then it includes timestamp, consent text version and source page (CASL proof of consent).
- **Platform notes:** Spark-compatible (no Functions). Sending confirmation or launch emails needs an email provider (§8).
- **Analytics:** `waitlist_submitted {variant, platform, country}`
- **Edge cases:** duplicate email → idempotent "You're already on the list".
- **Privacy:** email used only for DrivePerf launch and beta news; unsubscribe in every email (F-WEB-07).
- **Free/Pro:** n/a.

#### F-WEB-03 · Legal pages · P0
- **Story:** As a user or reviewer, I want a clear privacy policy and terms.
- **Screens:** S-WEB-03, S-WEB-04
- **Acceptance criteria:**
  1. Given S-WEB-03, when published, then it covers PIPEDA, Quebec Law 25 (privacy officer, transfers outside Quebec, rights), US state rights (access, deletion, correction, portability, opt-out of sale/sharing — "we don't sell or share"), CalOPPA, location and motion data, Firebase/RevenueCat/map providers as service providers, retention, cookies (none), children (not for under-18s), and contact; it is reviewed by counsel before launch.
  2. Given S-WEB-04, when published, then it states 18+, no-insurance purpose, safe-use terms (never interact while driving), and subscription terms.
  3. Given versions, when changed, then each page shows a version and date, and the app records accepted versions (F-ONB-02).
- **Platform notes:** linked from both store listings and App Store Connect metadata (Apple 5.1.1(i)).
- **Analytics:** page views only.
- **Edge cases:** none.
- **Privacy:** see §6.
- **Free/Pro:** n/a.

#### F-WEB-04 · Account deletion request page · P0
- **Story:** As a user without the app installed, I want to request deletion of my account and data on the web.
- **Screens:** S-WEB-07
- **Acceptance criteria:**
  1. Given S-WEB-07, when visited, then it names DrivePerf and the developer, explains the in-app steps, and offers a request form (email) that triggers a verified deletion (email-link verification), plus what is deleted and what is retained and why (Google Play requirement).
  2. Given a request, when verified, then deletion completes within 30 days (target 7) and the requester is told.
- **Platform notes:** the URL is entered in Play Console's Data Safety form.
- **Analytics:** `web_deletion_requested`
- **Edge cases:** no account for that email → same neutral confirmation (no account enumeration).
- **Privacy:** Play account-deletion policy.
- **Free/Pro:** n/a.

#### F-WEB-05 · Support, FAQ and "How DrivePerf records" · P1
- **Story:** As a user or store reviewer, I want plain answers about capture, battery, privacy and pricing.
- **Screens:** S-WEB-05, S-WEB-06
- **Acceptance criteria:**
  1. Given S-WEB-05, when visited, then it has an FAQ (capture, battery "screen off", force-quit, passenger trips, data, Pro, cancelling) and a contact address.
  2. Given S-WEB-06, when visited, then it explains manual vs automatic trips, the estimated start, the platform limits per iOS/Android, and the reliability guide; it is linked from the app (F-ONB-03) and used in review notes.
- **Platform notes:** static.
- **Analytics:** page views.
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** n/a.

#### F-WEB-06 · Ride-along landing and app links · P1
- **Story:** As a friend who received a Ride-along pass, I want the link to work whether or not I have the app.
- **Screens:** S-WEB-08
- **Acceptance criteria:**
  1. Given a pass link, when opened on a phone with the app, then it opens the app (universal link / app link) to S-SHR-04; without the app, S-WEB-08 explains the pass and links to the right store.
  2. Given `/.well-known/apple-app-site-association` and `/.well-known/assetlinks.json`, when deployed, then they validate with Apple's and Google's tools.
- **Platform notes:** Associated Domains needs the paid Apple account.
- **Analytics:** `ridealong_link_opened {has_app}`
- **Edge cases:** desktop visit → QR code.
- **Privacy:** the link carries an opaque code only.
- **Free/Pro:** n/a.

#### F-WEB-07 · Email preferences and unsubscribe · P0
- **Story:** As a waitlist member, I want to unsubscribe with one click.
- **Screens:** S-WEB-09
- **Acceptance criteria:**
  1. Given any marketing or waitlist email, when sent, then it contains the sender's identity, a mailing address and a one-click unsubscribe link (CASL, CAN-SPAM).
  2. Given an unsubscribe, when clicked, then S-WEB-09 confirms and it is honoured within 10 business days (target: immediately).
- **Platform notes:** depends on the email provider (§8).
- **Analytics:** `email_unsubscribed`
- **Edge cases:** none.
- **Privacy:** CASL.
- **Free/Pro:** n/a.

#### F-WEB-08 · Press kit · P2
- **Story:** As a journalist, I want logos, screenshots and *parked* car footage.
- **Screens:** S-WEB-10
- **Acceptance criteria:**
  1. Given S-WEB-10, when visited, then it offers the logo pack, screenshots, Story GIFs, the privacy one-pager and a contact; no footage shows a phone being used while the car moves (A2 launch video rule).
- **Platform notes:** static.
- **Analytics:** page views.
- **Edge cases:** none.
- **Privacy:** n/a.
- **Free/Pro:** n/a.

---

### 3.17 Epic ANA — Analytics & Crash Reporting

#### F-ANA-01 · Product analytics and event taxonomy · P0
- **Story:** As the product team, we need privacy-safe events to measure WDD, activation and retention.
- **Screens:** none (instrumentation); consent via S-ONB-13, S-PRV-07
- **Acceptance criteria:**
  1. Given the events listed in this PRD, when implemented, then each is in a single tracked event dictionary (name, properties, owner, feature ID) in `docs/`, and CI fails if code sends an undeclared event.
  2. Given any event, when inspected in a debug view, then it contains no coordinates, addresses, route/car names, speeds, G values or free text (§3.0.4); an automated test scans payloads for numeric lat/lon patterns.
  3. Given usage statistics are off, when the app runs, then zero analytics requests are made.
  4. Given the analytics SDK, when configured, then advertising-ID collection, Google Signals and ad personalisation are disabled, and no IDFA/AdSupport framework is linked.
- **Platform notes:** Firebase Analytics (Spark) unless ADR-007 picks another tool.
- **Analytics:** n/a.
- **Edge cases:** EC-NET (batched, dropped after 7 days offline).
- **Privacy:** §6.4 label targets.
- **Free/Pro:** n/a.

#### F-ANA-02 · Capture-quality telemetry · P0
- **Story:** As the team, we need start latency, missed trips, false trips and battery per hour from real use, so that we can prove and improve the capture promise.
- **Screens:** none
- **Acceptance criteria:**
  1. Given a finalised trip (and analytics on), when reported, then `capture_quality` carries: mode, wake trigger, start latency (m and s), estimated-start distance, IMU delivery %, GNSS coverage %, end-detection delay bucket, battery %/h bucket, screen-on share bucket, OS build, device model, thermal/LPM flags — and no location.
  2. Given corrections (passenger, transit, bike, walk) and missed-trip reports, when made, then they are counted to compute Q1–Q10 per platform, OS version and OEM.
  3. Given the beta, when reviewed weekly, then a dashboard (F-ANA-04) shows Q1–Q14 against targets.
- **Platform notes:** same schema on both platforms (shared core).
- **Analytics:** `capture_quality`, `missed_trip_report`, `trip_corrected`
- **Edge cases:** none.
- **Privacy:** metres from the parking spot are not location.
- **Free/Pro:** n/a.

#### F-ANA-03 · Crash and ANR reporting · P0
- **Story:** As the team, we need crash and ANR reports to hold crash-free sessions ≥ 99.5%.
- **Screens:** none; consent via S-ONB-13, S-PRV-07
- **Acceptance criteria:**
  1. Given crash reporting allowed, when a crash or ANR occurs, then a report with symbolicated stack, device, OS and app version reaches Sentry; tags, breadcrumbs and logs never contain locations (lint rule + review).
  2. Given background crashes during capture, when they occur, then they are tagged `in_trip=true` so capture regressions are visible.
  3. Given a release, when its crash-free sessions drop below 99.5% on ≥ 1,000 sessions, then staged rollout is halted (Play staged rollout; App Store phased release paused).
- **Platform notes:** Sentry KMP SDK, EU region, free Developer plan at launch (ADR-007); dSYM and R8 mapping upload in CI. Xcode Organizer and Android vitals are the consent-free cross-check for AC3.
- **Analytics:** n/a.
- **Edge cases:** EC-NET.
- **Privacy:** declared as Crash Data / Diagnostics, not linked, not used to track.
- **Free/Pro:** n/a.

#### F-ANA-04 · Metrics dashboards · P1
- **Story:** As the product owner, I want WDD, activation, retention, conversion and capture quality on one dashboard.
- **Screens:** none (internal)
- **Acceptance criteria:**
  1. Given analytics data, when the dashboard loads, then it shows WDD (weekly), activation funnel, D1/D7/D30 and W1/W4, capture retention, Pro conversion by day 35, notification opt-out, and Q1–Q14, each split by platform.
  2. Given usage statistics are opt-in, when WDD is reported, then the dashboard shows the opted-in share so numbers are read correctly (§9 OQ-02).
- **Platform notes:** BigQuery export needs Blaze; Firebase console reports work on Spark (A10).
- **Analytics:** n/a.
- **Edge cases:** none.
- **Privacy:** aggregate only.
- **Free/Pro:** n/a.

#### F-ANA-05 · Remote config and kill switches · P1
- **Story:** As the team, we need to switch features off or adjust copy and guides without a release, safely.
- **Screens:** none
- **Acceptance criteria:**
  1. Given Remote Config, when a flag is flipped (e.g. disable map card, Live Update promotion, Ride-along), then clients apply it on next launch.
  2. Given scoring thresholds, when changes are needed, then they ship as a versioned algorithm with release notes, never as a silent remote tweak (F-SCO-04).
- **Platform notes:** Firebase Remote Config (Spark, 100K fetches/day).
- **Analytics:** `remote_config_applied {version}`
- **Edge cases:** EC-NET (bundled defaults).
- **Privacy:** no user data in config requests beyond the SDK's own.
- **Free/Pro:** n/a.

---

### 3.18 Feature count by epic

| Epic | P0 | P1 | P2 | Total |
|---|---|---|---|---|
| ONB Onboarding & Permissions | 8 | 2 | 0 | 10 |
| CAP Trip Capture | 14 | 3 | 0 | 17 |
| DQ Data Quality & Calibration | 5 | 2 | 0 | 7 |
| STY Drive Story | 6 | 0 | 0 | 6 |
| RPL Trip Replay & Analysis | 8 | 1 | 1 | 10 |
| SCO Scores & Coaching | 8 | 2 | 0 | 10 |
| HIS History & Stats | 5 | 0 | 1 | 6 |
| RTE Routes & Route Records | 5 | 1 | 0 | 6 |
| GAR Garage | 2 | 1 | 1 | 4 |
| SHR Sharing & Referral | 2 | 3 | 0 | 5 |
| ACC Account & Cloud Backup | 4 | 1 | 0 | 5 |
| PRV Privacy Centre, Export & Deletion | 6 | 1 | 0 | 7 |
| SUB Subscription, Pro Preview & Paywall | 8 | 0 | 0 | 8 |
| NTF Notifications, Weekly Recap & Wrapped | 3 | 3 | 0 | 6 |
| SET Settings & Health Check | 4 | 2 | 0 | 6 |
| WEB Website & Waitlist | 5 | 2 | 1 | 8 |
| ANA Analytics & Crash Reporting | 3 | 2 | 0 | 5 |
| **Total** | **96** | **26** | **4** | **126** |

---

## 4. Screen inventory (appendix, required)

This table is the source for the Jira design tasks (A5) and the prototype checklist (A7, A8, A9; Gate 1 #17). **Every row needs prototype artifacts for each platform variant listed**, in `docs/design/<JIRA-KEY>/`, before development of the stories that use it.

**Notes**
- Screen IDs are shared across platforms. "Both" means one UX with platform cosmetics (iOS Liquid Glass, Android Material 3 Expressive).
- Common states abbreviated: **Def** default · **Empty** · **Load** loading/skeleton · **Err** error · **Part** partial data (gaps, estimated, excluded) · **Deny** permission denied · **Lock** Free vs Pro locked (blurred own-data preview) · **Prev** Pro Preview active · **Off** offline · **RM** reduced motion · **XL** largest text size · **Dark** dark mode. Every screen is prototyped in light and dark mode; **RM** and **XL** are listed where layout changes materially.
- System permission dialogs and OS settings pages are mocked in the prototypes as non-interactive frames so that the flows can be tested.
- **Screen priority** is the priority of the feature that needs the screen to exist. A P0 feature that only reuses a P1 screen (e.g. F-STY-06 accessibility on the Wrapped players) does not raise that screen to P0.

| Screen ID | Name | Epic | Feature IDs | States to prototype | Platform variants | Priority |
|---|---|---|---|---|---|---|
| S-NAV-01 | App shell and tab bar (Drives, Routes, Insights, Garage, Profile — A6 to validate) | HIS | F-HIS-01, F-RTE-02, F-GAR-01, F-ACC-01, F-HIS-04 | Def; recording banner; "needs attention" badge; Prev badge; tab bar minimised on scroll | Both (iOS floating glass tab bar; Android navigation bar) | P0 |
| S-ONB-01 | Welcome: "Smooth is a skill." | ONB | F-ONB-01 | Def; RM; XL | Both | P0 |
| S-ONB-02 | Intro: "Every drive, decoded." (Story preview) | ONB | F-ONB-01 | Def; RM | Both | P0 |
| S-ONB-03 | Intro: Private by design + Pro Preview included | ONB | F-ONB-01 | Def | Both | P0 |
| S-ONB-04 | Age (18+) and terms | ONB | F-ONB-02 | Def; checkbox unticked (Continue disabled); under-18 blocked | Both | P0 |
| S-ONB-05 | How capture works (honest promise) | ONB | F-ONB-03 | Def; "How it works" sheet open | Both (copy differs: force-quit vs force-stop, OEM note on Android) | P0 |
| S-ONB-06 | Location primer (While Using) | ONB | F-ONB-04 | Def; system prompt mock; denied → S-ONB-16 | Both | P0 |
| S-ONB-07 | Motion & activity primer | ONB | F-ONB-05 | Def; system prompt mock; denied | Both (iOS "Motion & Fitness", Android "Physical activity") | P0 |
| S-ONB-08 | Automatic trips opt-in + background-location disclosure | ONB | F-ONB-04 | Def (toggle off); toggle on → iOS Always prompt mock; declined | Both (Android version is the Play prominent disclosure) | P0 |
| S-ONB-09 | "Allow all the time" settings step | ONB | F-ONB-04 | Def; returned granted; returned not granted | **Android-only** (separate background-location step) | P0 |
| S-ONB-10 | Notifications primer | ONB | F-ONB-06 | Def; system prompt mock; denied | Both (Android 13+ prompt; older Android skips) | P0 |
| S-ONB-11 | Precise location sheet | ONB | F-ONB-04 | Def; refused → "route only" note | Both (iOS precise toggle; Android approximate vs precise) | P0 |
| S-ONB-12 | Battery and reliability step | ONB | F-ONB-07, F-SET-03 | Samsung; Xiaomi; other OEM; remind later | **Android-only** | P0 |
| S-ONB-13 | Help improve DrivePerf (usage statistics, crash reports) | ONB | F-ONB-08, F-PRV-06, F-ANA-01, F-ANA-03 | Def (usage unselected); both on; both off | Both | P0 |
| S-ONB-14 | Add your car | ONB | F-ONB-09, F-GAR-01 | Def; filled; skipped | Both | P1 |
| S-ONB-15 | You're ready (quick-start surfaces) | ONB | F-ONB-10 | Auto on; manual only | Both (iOS: Control Center control + widget tip; Android: Quick Settings tile + widget tip) | P1 |
| S-ONB-16 | Permission denied / manual-mode sheet | ONB | F-ONB-04 | Location denied; Always declined; Settings deep link | Both | P0 |
| S-HOME-01 | Home (Drives tab): ready to record | CAP | F-CAP-01, F-CAP-02, F-CAP-06, F-CAP-14, F-SET-02, F-SUB-02 | Def auto ready; manual only (auto off); needs attention; auto paused; recording in progress; processing a trip; new Story card; interrupted trip recovered; first run Empty; Prev day counter; Deny | Both | P0 |
| S-HOME-02 | Capture status sheet | CAP | F-CAP-02, F-SET-02 | All good; attention; last check-in time | Both | P0 |
| S-HOME-03 | Pause auto-detect sheet | CAP | F-CAP-15 | Def; paused until…; resume | Both | P1 |
| S-DRV-01 | In-drive locked screen | CAP | F-CAP-01, F-CAP-02, F-CAP-07, F-CAP-08 | Manual trip; auto trip; GPS searching; Low battery mode; passenger mode active | Both | P0 |
| S-DRV-02 | End trip sheet (Stop confirmation) | CAP | F-CAP-01 | Def; short-trip warning | Both | P0 |
| S-DRV-03 | "I'm a passenger" sheet | CAP | F-CAP-08 | Def; keep as passenger; don't save | Both | P0 |
| S-DRV-04 | "Decoding your drive" (processing) | CAP | F-CAP-01, F-CAP-03, F-STY-01 | Load; done → Story; Err (processing failed, retry) | Both | P0 |
| S-DRV-05 | "Your Story will be ready when you've parked" | CAP | F-CAP-07, F-STY-05 | Def; auto-opens when stationary | Both | P0 |
| S-LIVE-01 | Live Activity, Lock Screen | CAP | F-CAP-01, F-CAP-09, F-CAP-17 | Recording; GPS searching; ending / "Drive decoded"; 8 h cap ended | **iOS-only** | P0 |
| S-LIVE-02 | Live Activity, Dynamic Island | CAP | F-CAP-01, F-CAP-09 | Compact; minimal; expanded; ending | **iOS-only** | P0 |
| S-LIVE-03 | Control Center control | CAP | F-CAP-10 | "Start drive"; "Show live drive" (auto trip running); "Stop drive" | **iOS-only** | P0 |
| S-LIVE-04 | Lock Screen and Home Screen widgets | CAP | F-CAP-10 | Idle with last drive; recording; Empty (no drives yet); Deny | **iOS-only** (accessory circular/rectangular; small; medium) | P0 |
| S-LIVE-05 | Live Update notification (promoted) | CAP | F-CAP-01, F-CAP-12 | Manual recording; auto recording (after confirmation); status chip; ending | **Android-only** (Android 16+) | P0 |
| S-LIVE-06 | Standard ongoing recording notification | CAP | F-CAP-01, F-CAP-11, F-CAP-12 | Recording; demoted Live Update; notifications denied (Task Manager) | **Android-only** | P0 |
| S-LIVE-07 | Quick Settings tile | CAP | F-CAP-13 | Idle; recording; unavailable (no permission) | **Android-only** | P1 |
| S-LIVE-08 | Home-screen widget (Glance) | CAP | F-CAP-13 | Idle with last drive; recording; Empty | **Android-only** | P1 |
| S-LIVE-09 | Auto-trip "Recording your drive" notification | CAP | F-CAP-02, F-CAP-03, F-CAP-11 | Recording; replaced by "Drive decoded" | Both (iOS passive local notification; on Android this is the FGS notification) | P0 |
| S-LIVE-10 | "Auto-detect may be paused" watchdog notification | CAP | F-CAP-14 | Def; after force-stop (Android in-app card variant) | Both | P0 |
| S-STY-01 | Drive Story player (frames 1–7) | STY | F-STY-01, F-STY-03, F-STY-04, F-STY-06, F-SCO-01, F-SCO-02, F-SCO-05, F-SCO-06, F-RTE-01, F-RTE-03, F-HIS-02, F-GAR-02, F-CAP-04, F-DQ-02, F-DQ-04, F-SHR-01, F-DQ-01 | Def; first drive (baseline); calibrating (k of 3); route discovered (merge + name); new route record; degraded (Part); Unscorable; "Was it needed?" inline; passenger chip; transit question; Load; RM; XL; screen reader | Both | P0 |
| S-TRP-01 | Trip detail | RPL | F-RPL-07, F-CAP-04, F-CAP-05, F-CAP-06, F-CAP-17, F-DQ-04, F-DQ-05, F-HIS-03, F-SCO-01, F-SCO-04, F-SCO-06, F-SCO-09, F-SHR-01, F-PRV-03, F-RPL-01 | Def; Part (estimated start, gaps); Unscorable; passenger / not a drive; interrupted; short trip; Lock (older than 30 days, Free); detailed data removed; Load | Both | P0 |
| S-TRP-02 | Edit trip sheet | HIS | F-HIS-02, F-GAR-02, F-CAP-04, F-HIS-06 | Def; type picker (driver, passenger, bus/train, taxi, other); car picker; merge/split (P2) | Both | P0 |
| S-TRP-03 | Delete trip confirmation | HIS | F-HIS-02, F-PRV-04 | Def; with cloud backup note | Both | P0 |
| S-RPL-01 | Trip Replay | RPL | F-RPL-01, F-RPL-02, F-RPL-03, F-RPL-08, F-CAP-05, F-DQ-02, F-DQ-03, F-DQ-04, F-SCO-04 | Paused at start; playing (each speed); scrubbing; event pin pulse; GPS gap; estimated start; excluded span; no events; Off (no map tiles); map Load; Lock (Free, > 30 days); Err; RM; landscape | Both (MapKit / Android map SDK) | P0 |
| S-RPL-02 | Event evidence sheet | RPL | F-RPL-04, F-SCO-04, F-SCO-05, F-SCO-06, F-DQ-05 | Hard braking; hard acceleration; hard cornering; low confidence; marked needed; "Not accurate?" reasons; trace removed (Part); "This drive's tip" | Both | P0 |
| S-RPL-03 | Stop and max-speed pin popover | RPL | F-RPL-02 | Stop; max speed (neutral style) | Both | P0 |
| S-RPL-04 | Replay layers sheet | RPL | F-RPL-10 | Def; map style options | Both | P2 |
| S-ANL-01 | Analysis workspace (map + synced timeline + G-G) | RPL | F-RPL-05, F-RPL-06, F-RPL-03, F-RPL-08, F-DQ-03, F-DQ-04, F-DQ-05 | Def; cursor dragging; zoomed; time vs distance axis; Part (hatched/excluded); Lock (Free, > 30 days); Load; landscape; XL | Both | P0 |
| S-ANL-02 | G-G circle, expanded | RPL | F-RPL-06 | Def; filtered by event type; Part | Both | P0 |
| S-ANL-03 | Channel picker | RPL | F-RPL-05, F-RPL-09 | Free channels; Pro channels (jerk, quality) with Lock for Free | Both | P0 |
| S-ANL-04 | Range statistics sheet | RPL | F-RPL-09 | Def; Lock | Both | P1 |
| S-DQ-01 | Data-quality detail sheet | DQ | F-DQ-01, F-DQ-02, F-DQ-03, F-DQ-04, F-DQ-05, F-CAP-05, F-RPL-08 | Good; Usable; Limited; Unscorable; calibrating; estimated start; IMU 25 Hz (battery/thermal) | Both | P0 |
| S-DQ-02 | Phone placement guide | DQ | F-DQ-06 | Def; from Limited trip | Both | P1 |
| S-SCO-01 | Smoothness breakdown | SCO | F-SCO-01, F-SCO-02, F-SCO-03 | Def; calibrating; route vs context comparison; Unscorable | Both | P0 |
| S-SCO-02 | How scores work | SCO | F-SCO-08, F-SCO-07, F-DQ-05 | Def; XL | Both | P0 |
| S-SCO-03 | "Was it needed?" sheet | SCO | F-SCO-05, F-RPL-04 | Def; marked needed; count it again | Both | P0 |
| S-SCO-04 | Coaching tip detail | SCO | F-SCO-06, F-RPL-04 | Def; with evidence trace; no tip (Unscorable) | Both | P0 |
| S-SCO-05 | Weekly focus detail | SCO | F-SCO-10 | Def; improving; new focus | Both | P1 |
| S-SCO-06 | Efficiency habits detail | SCO | F-SCO-09 | Def; calibrating; EV car vs petrol car copy | Both | P1 |
| S-HIS-01 | Drives list | HIS | F-HIS-01, F-HIS-03 | Def; Empty; Load; filtered; passenger trips greyed; interrupted label; Lock indicators on older trips; 1,000-trip scroll | Both | P0 |
| S-HIS-02 | Filter sheet | HIS | F-HIS-01 | Def; filters applied | Both | P0 |
| S-STA-01 | Insights (weekly stats) | HIS | F-HIS-04, F-HIS-05, F-SCO-02 | Def; calibrating; Empty week; Lock on Month/Year/Car/Route | Both | P0 |
| S-STA-02 | Trend detail | HIS | F-HIS-05 | Month; Year; per car; per route; significance-gated claim | Both | P0 |
| S-RTE-01 | Routes list | RTE | F-RTE-02, F-RTE-05 | Def; Empty; candidate to name; Free limit with 3rd route locked | Both | P0 |
| S-RTE-02 | Route detail | RTE | F-RTE-02, F-RTE-03 | Def; new record; few runs (2); Lock (inactive route on Free) | Both | P0 |
| S-RTE-03 | Name this route sheet | RTE | F-RTE-01 | Preset chips; custom | Both | P0 |
| S-RTE-04 | Ghost comparison | RTE | F-RTE-04 | Def; where you gained/lost; Part (unaligned segments); Lock | Both | P0 |
| S-RTE-05 | Route management sheet | RTE | F-RTE-05, F-RTE-06 | Rename; merge; link directions; remove; choose active routes (Free) | Both | P0 |
| S-GAR-01 | Garage | GAR | F-GAR-01, F-GAR-02 | Def (1 car); multiple cars; Lock (add 2nd car on Free); Empty | Both | P0 |
| S-GAR-02 | Car detail | GAR | F-GAR-01, F-GAR-03 | Def; EV; no trips yet | Both | P0 |
| S-GAR-03 | Add / edit car | GAR | F-GAR-01, F-ONB-09, F-GAR-04 | Add; edit; delete confirm; Bluetooth link (P2) | Both | P0 |
| S-SHR-01 | Share card builder | SHR | F-SHR-01, F-SHR-02, F-SHR-03, F-SHR-04, F-PRV-07, F-NTF-04, F-NTF-05 | Route glyph; Ring; Week strip; Archetype; Map (opt-in); 9:16 and 4:5; short trip (Ring only); Pro styles; Load | Both (system share sheet) | P0 |
| S-SHR-02 | Map card warning sheet | SHR | F-SHR-03 | Def | Both | P1 |
| S-SHR-03 | Ride-along pass invite | SHR | F-SHR-05 | Def; rewards earned; cap reached | Both | P1 |
| S-SHR-04 | Ride-along welcome (redeemed) | SHR | F-SHR-05 | Valid pass (30 days); invalid/expired; already used | Both | P1 |
| S-ACC-01 | Profile tab | ACC | F-ACC-01 | Local mode; signed in Free; signed in Pro with backup; Prev | Both | P0 |
| S-ACC-02 | Sign-in sheet | ACC | F-ACC-02 | Def; Load; Err; Off | Both (Sign in with Apple native on iOS, web flow on Android) | P0 |
| S-ACC-03 | Check your email (email link) | ACC | F-ACC-02 | Sent; link expired | Both | P0 |
| S-ACC-04 | Cloud backup settings | ACC | F-ACC-03 | Off; on and up to date; pending; Wi-Fi only; Err; Lock (Free); lapsed Pro warning | Both | P0 |
| S-ACC-05 | Backup consent | ACC | F-ACC-03 | Def | Both | P0 |
| S-ACC-06 | Restore from backup | ACC | F-ACC-04 | In progress; done; Err; Off; low storage | Both | P0 |
| S-ACC-07 | Sign out sheet | ACC | F-ACC-05 | Keep local; remove local; pending uploads warning | Both | P1 |
| S-PRV-01 | Privacy Centre | PRV | F-PRV-01 | Local only; with backup; analytics on/off | Both | P0 |
| S-PRV-02 | Privacy zones | PRV | F-PRV-07, F-SHR-02 | Empty; with zones; adding (map pin + radius) | Both | P1 |
| S-PRV-03 | Download all my data | PRV | F-PRV-02 | Def; generating (progress); done; Err; low storage | Both (iOS Files/share sheet; Android Storage Access Framework) | P0 |
| S-PRV-04 | Trip export (GPX/CSV) | PRV | F-PRV-03, F-DQ-05 | Single trip; date range; Lock (Free); GNSS-only (raw removed) | Both | P0 |
| S-PRV-05 | Delete data options | PRV | F-PRV-04 | Delete all (typed confirm); delete older than…; done | Both | P0 |
| S-PRV-06 | Delete account flow | PRV | F-PRV-05 | Re-auth; summary of what is deleted; active-subscription note; done; Off (queued) | Both | P0 |
| S-PRV-07 | Analytics and diagnostics | PRV | F-PRV-06, F-ONB-08, F-ANA-01, F-ANA-03 | Def; both off; reset ID done | Both | P0 |
| S-SUB-01 | Pro Preview status sheet | SUB | F-SUB-02 | Day 1; day 13; Ride-along 30-day variant | Both | P0 |
| S-SUB-02 | Day-15 "Your first two weeks, decoded" | SUB | F-SUB-03, F-SUB-05, F-SUB-08 | Def (with founding price); not founding-eligible; RM | Both | P0 |
| S-SUB-03 | Contextual lock sheet | SUB | F-SUB-04, F-HIS-03, F-RTE-04, F-RTE-05, F-ACC-03, F-RPL-01, F-SUB-08 | Variants: compare with record; Replay/Analysis > 30 days; 3rd route; 2nd car; export; trends; backup; no data yet | Both | P0 |
| S-SUB-04 | Plans (paywall) | SUB | F-SUB-01, F-SUB-05, F-SUB-08, F-SUB-03 | US prices; CA prices; founding offer; Load; Err; Off; pending purchase | Both (StoreKit / Play Billing sheets mocked) | P0 |
| S-SUB-05 | Welcome to Pro | SUB | F-SUB-01 | Def | Both | P0 |
| S-SUB-06 | Manage subscription | SUB | F-SUB-06 | Pro monthly; Pro annual; Ride-along; Preview; restore result | Both | P0 |
| S-SUB-07 | Preview ended / "You're on Free" | SUB | F-SUB-03, F-SUB-07 | Def; choose active routes/car; low-usage variant | Both | P0 |
| S-NTF-01 | Notification catalogue (all templates) | NTF | F-NTF-01, F-NTF-02, F-NTF-03, F-NTF-04, F-NTF-06, F-STY-02, F-RTE-01, F-RTE-03 | Drive decoded; first Story ready; 2 drives coalesced; route discovered; route record; weekly recap; monthly Wrapped; capture problem; missed-trip check; Preview ends in 2 days; lapsed once; Lock Screen and expanded renderings | Both (iOS notification; Android channel notification) | P0 |
| S-NTF-02 | Missed-trip check sheet | DQ | F-DQ-07 | Prompt; thanks; "I was a passenger" | Both | P1 |
| S-RCP-01 | Weekly recap player | NTF | F-NTF-03, F-SCO-10, F-SHR-04, F-STY-06 | Def (W1–W5); trend down; Empty week; calibrating; RM; screen reader | Both | P0 |
| S-WRP-01 | Monthly Wrapped player | NTF | F-NTF-04, F-SHR-04, F-STY-06 | Def; Season Opener variant; Pro extra chapter; RM | Both | P1 |
| S-WRP-02 | Annual Drive Wrapped player | NTF | F-NTF-05, F-SHR-04, F-STY-06 | Def (frames 1–9); single car; habit frame skipped; Pro chapters; RM | Both | P1 |
| S-SET-01 | Settings | SET | F-SET-01 | Def | Both | P0 |
| S-SET-02 | Units and display | SET | F-SET-01 | Metric; imperial; appearance | Both | P0 |
| S-SET-03 | Recording settings | SET | F-SET-01, F-CAP-16 | Auto on; auto off; battery saver recording; default car | Both | P0 |
| S-SET-04 | Notifications and quiet hours | SET | F-NTF-01 | Def; type off; system notifications denied | Both | P0 |
| S-SET-05 | Health Check | SET | F-SET-02, F-CAP-14, F-CAP-16, F-DQ-07, F-NTF-02 | All green; amber (LPM, Battery Saver); red (Always lost, background refresh off, motion off, notifications off); paused; storage low | Both (iOS checks: Always, Background App Refresh, LPM, Live Activities; Android checks: all-the-time location, battery optimisation, OEM sleep) | P0 |
| S-SET-06 | Reliability guide | SET | F-SET-03, F-ONB-07 | Per OEM (Samsung, Xiaomi, other); "Test my setup" pass/fail; iOS version | Both (Android OEM pages; iOS BAR/LPM page) | P0 |
| S-SET-07 | Storage | SET | F-SET-04 | Def; low storage; remove raw data confirm | Both | P1 |
| S-SET-08 | About, legal and support | SET | F-SET-05 | Def | Both | P0 |
| S-SET-09 | Report a problem | SET | F-SET-06 | Def; diagnostics preview; trip attach warning; sent; Off | Both | P1 |
| S-WEB-01 | Home (landing + waitlist form) | WEB | F-WEB-01, F-WEB-02 | Variant A; variant B; form Def / validation Err / submitting / duplicate; desktop; 360 px mobile; dark | Web | P0 |
| S-WEB-02 | Waitlist confirmation | WEB | F-WEB-02 | Def | Web | P0 |
| S-WEB-03 | Privacy policy | WEB | F-WEB-03 | Def (draft flagged for counsel) | Web | P0 |
| S-WEB-04 | Terms of use | WEB | F-WEB-03 | Def (draft) | Web | P0 |
| S-WEB-05 | Support and FAQ | WEB | F-WEB-05 | Def | Web | P1 |
| S-WEB-06 | How DrivePerf records (capture and battery explainer) | WEB | F-WEB-05, F-ONB-03 | Def | Web | P0 |
| S-WEB-07 | Account deletion request | WEB | F-WEB-04, F-PRV-05 | Form; email sent; verified/done | Web | P0 |
| S-WEB-08 | Ride-along landing | WEB | F-WEB-06, F-SHR-05 | Mobile without app; desktop (QR); invalid pass | Web | P1 |
| S-WEB-09 | Email preferences / unsubscribe | WEB | F-WEB-07 | Unsubscribed; Err | Web | P0 |
| S-WEB-10 | Press kit | WEB | F-WEB-08 | Def | Web | P2 |
| S-WEB-11 | 404 | WEB | F-WEB-01 | Def | Web | P0 |

**Totals: 117 screens and sheets** — 106 app screens (S-NAV 1, S-ONB 16, S-HOME 3, S-DRV 5, S-LIVE 10, S-STY 1, S-TRP 3, S-RPL 4, S-ANL 4, S-DQ 2, S-SCO 6, S-HIS 2, S-STA 2, S-RTE 5, S-GAR 3, S-SHR 4, S-ACC 7, S-PRV 7, S-SUB 7, S-NTF 2, S-RCP 1, S-WRP 2, S-SET 9) and 11 web pages. Platform-specific rows: 4 iOS-only (S-LIVE-01 to 04) and 6 Android-only (S-ONB-09, S-ONB-12, S-LIVE-05 to 08); every other row is prototyped for both iOS and Android (or web). By priority: P0 94, P1 21, P2 2.

---

## 5. Metric provenance

Every number shown to users is labelled **Observed** (measured directly by a sensor or the OS), **Derived** (computed deterministically from observed data) or **Estimated** (inferred, modelled or filled where data is missing). A12 specifies the algorithms; this table gives the intent. "Formula owner" is who specifies it; the shared KMP core (A14) implements it.

| Metric (where shown) | Provenance | Intent (A12 specifies the formula) | Formula owner |
|---|---|---|---|
| Trip start / end time | Observed (Estimated when backfilled from motion history) | Start = first motion of the trip; end = when stopping began | A12 |
| Duration | Derived | End − start | A12 |
| Moving time / stopped time | Derived | Time above / below a low speed threshold | A12 |
| Distance | Derived (Estimated part for the backfilled start and gap fills) | Sum of filtered GNSS segments; estimated segments counted and flagged | A12 |
| Route shape (map, glyph) | Observed (Estimated segments dashed) | Filtered and simplified GNSS track | A12 |
| Current speed (Replay readout) | Observed | GNSS (Doppler) speed at the marker time, accuracy-gated | A12 |
| Max speed reached (Replay, Trip detail) | Derived | Maximum of filtered, accuracy-gated speed; outliers rejected | A12 |
| Average moving speed | Derived | Distance ÷ moving time | A12 |
| Stops (count, duration, pins) | Derived | Speed below threshold for ≥ 10 s (placeholder) | A12 |
| Elevation profile; gain / loss | Observed (GNSS altitude; barometer if used) → Derived (smoothed gain/loss) | Smoothed altitude; gain/loss with a noise dead-band | A12 |
| Longitudinal G / lateral G | Derived | IMU acceleration rotated into the vehicle frame, gravity removed, filtered | A12 |
| G-G cloud and envelope | Derived | Joint distribution of longitudinal and lateral G over scorable spans | A12 |
| Peak G of an event | Derived | Peak filtered G within the event window | A12 |
| Events (hard braking, acceleration, cornering) | Derived, with confidence | Rule-based thresholds, hysteresis, minimum duration | A12 |
| Event confidence (High/Medium/Low) | Estimated | Data quality and signal margin around the event | A12 |
| **Smoothness** (0–100) | Derived | Abruptness (jerk) and consistency of inputs over scorable spans; never increased by speed; a complete gentle stop ≥ a rolling stop | A12 |
| Smoothness vs your usual (± n) | Derived | Trip score − baseline (route or context) | A12 |
| Baseline | Derived | Robust rolling statistic of recent scorable trips (route or context) | A12 |
| Contributors (Braking, Acceleration, Cornering, Consistency) | Derived | Per-input sub-measures vs the user's baseline, banded | A12 |
| Efficiency habits | Estimated (behaviour proxy) | Stop-start per km, abrupt accelerations, speed consistency vs baseline; never energy | A12 |
| Coaching tip and "cleanest stretch" | Derived | Deterministic selection from evidence (A12 rules); cleanest stretch = longest span without abrupt inputs | A12 (rules), A2/A6 (copy) |
| Data-quality badge | Derived | Coverage, sample delivery, alignment and exclusions → Good/Usable/Limited/Unscorable | A12 |
| Delivered sample rate (e.g. "50 Hz") | Observed | Samples received ÷ time | A12 |
| GNSS coverage % / excluded minutes | Derived | Share of time with gated fixes; minutes excluded and why | A12 |
| Estimated start distance | Estimated | Parking spot → first gated fix | A12 |
| Route match and route record | Derived | Start/end clustering + path similarity; record = highest eligible Smoothness | A12 |
| "Where you gained / lost" | Derived | Distance-aligned segment Smoothness difference vs the record run | A12 |
| Weekly / monthly averages, trend lines | Derived | Mean of scorable trips per period; trend vs prior periods | A12 |
| Change claims ("38% fewer abrupt stops") | Derived (significance-gated) | Shown only if A12's statistical rule passes | A12 |
| Focus habit progress ("gentler in 7 of 9") | Derived | Count of trips improving on the focus measure | A12 |
| Wrapped totals (km, hours, most-driven hour, car split) | Derived | Sums and modes over driver trips | A12 |
| Wrapped comparisons ("about n× …") | Estimated | From a vetted list of reference distances | A2 (content), A12 (maths) |
| Archetype | Estimated (classification) | Rule-based traits from the year's or month's data | A12 (rules), A2 (names) |
| Part of day ("Tuesday evening") | Derived | Local time bucket of trip start | A12 |
| Battery used (Health Check, diagnostics only) | Observed (coarse) | OS battery level delta over the trip | A12 |
| Pro Preview days left; prices | Observed (store / clock) | Store price strings; first-open timestamp | A17 (entitlements) |

---

## 6. Safety and compliance requirements

### 6.1 Driving safety (binding, Gate 1 #12)

| ID | Requirement | Verified by |
|---|---|---|
| SAF-01 | **No interaction is required while moving.** Trips start and end automatically; manual trips are started before driving off. While a trip is active the app shows only S-DRV-01 (F-CAP-07). | UI test: every deep link during an active trip lands on S-DRV-01 or S-DRV-05 |
| SAF-02 | **No live score, speed, events, coaching, alerts or record pace while moving**, on any surface (app, Live Activity, Dynamic Island, Live Update, widgets, notifications). | Design review checklist on every live-surface screen; snapshot tests |
| SAF-03 | Notifications are held until the trip ends and the phone is parked (F-NTF-01). | Automated test with simulated trip |
| SAF-04 | **No speed or max G on share cards**, recaps, Wrapped, records, notifications or achievements. Speed appears only neutrally in personal Replay, Analysis and exports. | CI template tests (F-SHR-01); content review |
| SAF-05 | **No driving streaks** of any kind. | Content review |
| SAF-06 | Route records are Smoothness-only, never duration; records never reward rolling stops or unusual pace (F-SCO-07, F-RTE-03). | Fixture tests |
| SAF-07 | **No speed leaderboards**, friends, feeds or other-user comparisons in v1. | Scope review |
| SAF-08 | Copy follows the voice guide: no "fastest", "beat your time", "send it", "launch", risk-glorifying archetypes (A2 §6). | Copy review before each release |
| SAF-09 | Marketing and store assets never show a phone being used while the car moves (A2 launch video rule). | A20 review |
| SAF-10 | Counsel reviews the Replay speed display and share-card design for negligent-design risk before beta (A2 cites *Lemmon v. Snap*, 9th Cir. 2021). | §9 OQ-18 |

### 6.2 Apple App Store

| Guideline | Requirement for DrivePerf |
|---|---|
| **1.4.4** (no encouraging excessive speed) and **1.4.5** (no physical harm) | SAF-01 to SAF-09. No speed celebration anywhere. |
| **2.5.4** (background services for intended purposes) | Background location is used only to record the route of automatically detected drives. Never used just to keep the IMU running. Review notes explain auto-detect; a demo video is attached (A20). |
| **2.4.2** (battery, heat) | F-CAP-16 thermal and battery back-off; Q11–Q12 targets. |
| **3.1.1 / 3.1.2** (in-app purchase, auto-renewing subscriptions) | Pro sold only through IAP; paywall shows price, period, renewal and cancellation terms, Terms and Privacy links, Restore (F-SUB-01). |
| **4.8** (login services) | Sign in with Apple offered alongside Google (F-ACC-02). |
| **5.1.1(i)–(ii)** (privacy policy, consent) | Privacy policy in App Store Connect and in the app (F-SET-05, F-WEB-03). Consent before analytics (F-ONB-08). |
| **5.1.1 (paid functionality must not depend on granting data access)** | Pro never requires Always location; manual mode keeps every Pro feature usable. |
| **5.1.1(v)** (account deletion in app) | F-PRV-05. |
| **5.1.2** (data use and sharing) | No sharing; any future research-data donation needs separate consent. |
| **5.1.5** (location services) | Location used only for the trip feature; consent before cloud backup of routes (F-ACC-03). |
| Purpose strings | `NSLocationWhenInUseUsageDescription`, `NSLocationAlwaysAndWhenInUseUsageDescription`, `NSMotionUsageDescription` present and specific (CI check). |

### 6.3 Google Play

| Requirement | DrivePerf plan |
|---|---|
| **Background location declaration** | One feature: automatic trip recording. ≤ 30 s video showing the prominent disclosure (S-ONB-08), the runtime/settings flow (S-ONB-09) and a trip starting while the app is not in use. The disclosure uses "location", says "when the app is closed or not in use", and matches the store listing. |
| **Foreground service declaration** | FGS type `location`, use case "vehicle activity tracking"; video shows enabling automatic trips and a drive starting on its own, plus a manual start. |
| **Fine-location declaration (from 27 Jan 2027)** | Justify precise location: road-level placement of braking and cornering events and speed corroboration; coarse location or the location button is insufficient for continuous trip recording (A3 §4, Play policy announcement of 29 Oct 2026). Must be filed for any build submitted after 27 Jan 2027 — i.e. all beta and launch builds. |
| Geofencing | Parked-location geofences use the **Geofence API**, never an FGS (geofencing removed as an approved FGS use case on 27 Jan 2027). |
| Target API | Target API 36 (required for new apps and updates since 31 Aug 2026). |
| **Data Safety form** | Per §6.4. |
| **Account deletion** | In-app (F-PRV-05) **and** a web resource (S-WEB-07, F-WEB-04). |
| Subscriptions policy | Clear offer terms, easy cancellation (F-SUB-01, F-SUB-06). |
| Android vitals | Crash < 1.09%, ANR < 0.47%; no excessive partial wake locks (F-CAP-16). |
| Live Updates | Promoted only for real ongoing activity; auto-trip promotion only after confirmation and never re-promoted after dismissal (§9 OQ-05). |
| Pre-launch policy check | Submit an internal/closed-testing build with all three declarations early in Phase 6 to get a policy decision before launch (A3 Spike 4). |

### 6.4 Privacy label and Data Safety targets

Target: **no "Data Used to Track You"** on iOS; **no data shared** on Play; minimal "linked" data. On-device processing is not "collection" on either store (A3 §5.3). Labels describe the app for any user, so optional backup data must be declared.

| Data | Leaves the device when | Apple Privacy Nutrition Label (target) | Play Data Safety (target) |
|---|---|---|---|
| Precise location (routes) | Pro cloud backup on | Precise Location · App Functionality · **Linked** · not tracking | Location › Precise · collected · **optional** · not shared · encrypted in transit · deletable |
| Sensor/motion data (raw trips) | Pro cloud backup on | Other Data (or Sensor data) · App Functionality · Linked | App activity / Other · optional · not shared |
| Email, name, user ID | Sign-in | Contact Info, Identifiers (User ID) · App Functionality · Linked | Personal info (email, name), App IDs · optional |
| Purchase history | Subscription (via store and RevenueCat) | Purchases · App Functionality · Linked to the purchase identity | Financial info › Purchase history · collected · not shared (service provider) |
| Product interaction (usage statistics) | Usage statistics allowed | Usage Data · Analytics · **Not linked** | App activity › App interactions · optional · not shared |
| Crash logs, diagnostics, installation ID | Crash reports allowed | Crash Data, Performance Data · App Functionality · **Not linked** | App info and performance · optional · not shared |
| Waitlist email (website) | Website form | n/a (not the app) | n/a |
| Advertising ID / IDFA | **Never** | — | — |

Rules: no ad, attribution or data-broker SDKs; no locations in crash-report tags, breadcrumbs or logs; no location or route names in analytics; every SDK is reviewed against these targets before it is added (A10 ADR-007).

### 6.5 Canada: PIPEDA, Quebec Law 25, CASL

| Requirement | Implementation |
|---|---|
| PIPEDA accountability | Name a privacy officer; publish the contact (S-PRV-01, S-WEB-03). |
| Identifying purposes and **meaningful consent** | Purpose stated at each permission step and before backup; separate choices for non-essential processing (analytics, crash reports); withdrawal any time (F-ONB-04, F-ONB-08, F-PRV-06). |
| Limiting collection, use and retention | Local-first; triggers only outside trips; retention controls (F-SET-04, F-PRV-04); rejected trip candidates purged within 24 h. |
| Safeguards | OS encryption at rest, file protection on iOS, TLS in transit, Firestore/Storage security rules, App Check. |
| Individual access and correction | Download all my data (F-PRV-02); trip corrections (F-HIS-02). |
| Breach reporting | Breach-of-security-safeguards records and reporting process (to the OPC and individuals when there is a real risk of significant harm) documented before launch (A11). |
| **Quebec Law 25**: person in charge of protecting personal information | Same privacy officer; title and contact published. |
| Law 25: privacy by default; technology that can locate or profile a person | Automatic trips (location in the background) and scoring/archetypes (profiling) are explained before use, and **automatic trips are off until the user turns them on** (S-ONB-08). Sources differ on whether this rule sits in s. 8.1 or s. 9.1 and how far it reaches; counsel to confirm (web search 6 Nov 2026, McCarthy Tétrault and others). |
| Law 25: privacy impact assessment (PIA) | PIA before launching cloud backup, **including the transfer of personal information outside Quebec** (Firebase, US region; Cloudflare R2, North America). |
| Law 25: confidentiality incidents | Incident register and notification process (CAI and affected people where there is a risk of serious injury). |
| Law 25: portability, deletion | F-PRV-02 (structured, commonly used format), F-PRV-04/05. |
| Law 25: cookies | Website sets no cookies by default (F-WEB-01). |
| Charter of the French language (Quebec) | English only (Gate 2). Counsel may still advise before any Quebec-targeted marketing. |
| **CASL** (Canada's Anti-Spam Legislation) | Waitlist and launch emails only with express, unticked opt-in; consent records kept; sender identification and unsubscribe in every message, honoured within 10 business days (F-WEB-02, F-WEB-07). |

### 6.6 United States: state privacy laws and other rules

As of 6 Nov 2026, trackers count roughly 20 state comprehensive privacy laws in effect, with more taking effect in 2027 (counts vary by source). Most treat **precise geolocation as sensitive data** requiring opt-in consent (California instead gives a right to limit), and some now ban its sale outright (e.g. Virginia from 1 Jul 2026) (web search, 6 Nov 2026: Ketch, Enzuzo and Foley trackers). Many have applicability thresholds DrivePerf may not meet at launch; **we design to the strictest common denominator anyway**:

| Requirement | Implementation |
|---|---|
| Opt-in consent for sensitive data (precise geolocation) | Automatic trips are opt-in (S-ONB-08); location leaves the device only with explicit backup consent (S-ACC-05). |
| No sale, no "sharing" for cross-context behavioural advertising, no targeted advertising | No ad SDKs; no data sale ever; privacy policy says so plainly. |
| Consumer rights (access, deletion, correction, portability, opt-out) | In-app Privacy Centre (F-PRV-01 to 06) and the web deletion page (S-WEB-07); requests answered within 45 days. |
| Data protection assessment for processing sensitive data | Document one alongside the Law 25 PIA (A11). |
| Global Privacy Control | Honoured on the website (F-WEB-01). |
| CalOPPA | Conspicuous privacy policy link in the app and on the site. |
| CAN-SPAM | Unsubscribe and sender identification (F-WEB-07). |
| Children (COPPA) | Not directed to children; 18+ (§6.7). |

### 6.7 Age: 18+ for v1

- Self-attestation at onboarding (F-ONB-02) and in the Terms. No date of birth is collected.
- No features for teen drivers, parents or family monitoring.
- If DrivePerf learns a user is under 18, it deletes their account data and tells them.
- Store age ratings and any state app-store age-assurance obligations: §9 OQ-11.

### 6.8 Accessibility

- Apps: VoiceOver/TalkBack labels for every control and chart (charts have text summaries), Dynamic Type / font scale to 200%, touch targets ≥ 44 pt / 48 dp (≥ 64 in the in-drive screen), colour never the only signal, reduced-motion variants (F-STY-06).
- Website: WCAG 2.2 AA; Lighthouse Accessibility ≥ 95 (F-WEB-01).

---

## 7. Release plan

### 7.1 From today to development

| Phase | What | Gate | Target dates (indicative) |
|---|---|---|---|
| 2 | PRD (this document) → Jira epics, stories and design tasks (A5) | **Gate 2** ✅ | 6 Nov 2026 |
| 3 | Design system (A6) → screen-by-screen prototypes for every row of §4: iOS (A7), Android (A8), web (A9) | **Gate 3**: every UI story has "ready" design artifacts | mid – late Nov 2026 |
| 4 | Architecture docs (A10 ADRs, A11 data model, A12 pipeline and scoring), overlapping Phase 3 | **Gate 4** | Nov 2026 |
| 5 | Foundations (first development tickets, §7.2) | — | late Nov – early Dec 2026 |
| 6 | Feature build from Jira, field test (A19), **Founding Drivers beta** (200–300 testers, TestFlight + Play closed testing) | v1.0 release gates (§2.4) | Dec 2026 – early Jan 2027 |
| 7 | Launch readiness (A20): store listings, privacy labels, declarations, review notes | — | 1 week before launch |
| Launch | Both stores, same day (a Tuesday), CA + US, English | — | **January 2027 (or earlier)** |
| After launch | Trademark preliminary search (AI-assisted, USPTO + CIPO) for "DrivePerf" and "Smooth is a skill." | — | Last task |

The website and waitlist go live as soon as the web ticket is done (Phase 5) so the positioning A/B test has ≥ 4 weeks before store copy is locked.

### 7.2 First development tickets, in order

| # | Ticket | Done when |
|---|---|---|
| 1 | **Spike 1 — iOS capture test app, benchmarked against Mileon** (A13a): throwaway SwiftUI app per A3 §1.3–1.6 on a real iPhone with a free Personal Team; ≥ 10 drives side by side with Mileon, plus walks, runs, force-quit, reboot and Low Power Mode; results in `docs/research/2026-11/spike-1-ios-capture-results.md` | Q1–Q6, Q10–Q13 measured; PRD capture targets confirmed or a change proposed in `decisions.md`; force-quit/reboot/LPM outcomes recorded per iOS build |
| 2 | **Monorepo scaffold and CI** (A13) | `shared/`, `apps/ios/`, `apps/android/`, `backend/`, `web/` build in CI; both apps run "Hello DrivePerf" calling shared code |
| 3 | **Shared core** (A14): trip state machine, signal pipeline, detectors, Smoothness, tips, route matching, SQLDelight schema | ≥ 90% unit coverage on domain logic; fixture suite incl. F-SCO-07 guardrails passes |
| 4 | **App shells** (A15 iOS, A16 Android): navigation, tokens, onboarding and permissions, platform sensor adapters, manual trip end to end, Live Activity / Live Update | A manual trip records on device and appears in the list on both platforms |
| 5 | **Backend** (A17): Firebase Auth, Firestore rules, App Check, Hosting config, waitlist collection; Blaze-scope code prepared (Storage, Functions) | Emulator tests green; Spark scope deployed |
| 6 | **Website** (A18): Astro site from the A9 prototype, waitlist live, legal drafts, app-link files | Lighthouse ≥ 95; waitlist writes verified; preview channels per PR |

### 7.3 v1.0 (MVP) — January 2027

- **Contents:** all P0 features (96) and the P1 features the product owner keeps in scope (26 candidates).
- **Release gates:** §2.4 (Q1, Q2, Q4, Q9, Q11, Q14, Q15, Q17 on the beta cohort for at least one week, given the compressed January timeline); Play policy approval of the three declarations; Apple review notes and demo video ready; counsel sign-off on the privacy policy, terms and OQ-01/OQ-18.
- **P1 slip order if the build runs late** (the first listed slips first): F-CAP-13, F-RPL-09, F-SHR-03, F-SET-06, F-DQ-06, F-ONB-10, F-SHR-04, F-NTF-04, F-PRV-07, F-SET-04, F-SCO-10, F-SCO-09, F-GAR-03, F-RTE-06, F-ACC-05, F-NTF-06, F-WEB-05, F-WEB-06, F-SHR-05, F-DQ-07, F-ANA-04, F-ANA-05, F-CAP-15, F-CAP-17, F-ONB-09. (F-NTF-05 Annual Wrapped is planned for v1.2.)

### 7.4 v1.1 — early summer 2027 (≈ 6–8 weeks after launch)

- P1 features that slipped from v1.0.
- P2: Bluetooth car assignment (F-GAR-04), merge/split trips (F-HIS-06), replay layers (F-RPL-10), press kit (F-WEB-08).
- Club codes for car clubs (A2 §4.2 runner-up), anonymous aggregate "club recap" only — nothing that ranks members.
- Wrapped Pro extra chapters and card styles.
- Tuning from beta and launch data: detector thresholds, transit heuristics, Pro Preview variant A/B (RevenueCat Experiments).

### 7.5 v1.2 — autumn 2027 (must land before 1 Dec 2027)

- **Annual Drive Wrapped** (F-NTF-05) — renewal-season value proof for annual plans bought in spring.
- Scoring algorithm v2 from field-test and beta data, with opt-in re-scoring of history.
- Decision on a capped founders lifetime plan (A2 §5.2, at ~6 months after launch).
- Push-to-start Live Activity evaluation report (decision only; build is "later").

### 7.6 Later (not scheduled)

| Item | Precondition |
|---|---|
| OBD-II / BLE vehicle data (real energy, throttle, RPM) | Hardware research; provenance model extended |
| Track mode (closed courses only): laps, sectors | Separate safety and usability study; never on public roads |
| Web account dashboard | Blaze + Firebase App Hosting; v1.1+ at the earliest |
| Friends and leaderboards (if ever) | Safety review; never speed-based; never in v1 |
| CarPlay / Android Auto | Platform entitlement and driver-distraction guidelines |
| Apple Watch / Wear OS | — |
| Push-to-start Live Activities for automatic trips | Paid Apple account, Blaze, server, network at trip start (Gate 1 #9) |
| ML detectors (passenger, transit, phone handling), crash detection | Labelled data, accuracy and privacy study |
| Research data donation | Separate consent (Apple 5.1.2) |
| More languages and markets | Localisation, legal review |

---

## 8. Dependencies and assumptions

### 8.1 Dependencies

| Dependency | Needed for | Status | When |
|---|---|---|---|
| **Apple Developer Program** (US$99/yr) | Sign in with Apple, In-App Purchase, TestFlight, App Store, App Attest (App Check), Associated Domains (universal links), push (later) | Not bought (Gate 1 #13). A free Personal Team is enough for Spike 1 on a real iPhone (7-day profiles, 3 devices) | Buy after Spike 1, before the iOS shell needs SIWA/IAP/TestFlight (start of Phase 6 at the latest) |
| **Google Play Console** | Closed testing, declarations, Data Safety, launch | Exists | Policy pre-check early Phase 6 |
| **Firebase "DrivePerf" on Spark** | Auth, Firestore, Hosting (website, waitlist), Analytics, Remote Config, App Check (crash reports use **Sentry**, a separate free account: ADR-007) | **Not created yet**: Keval creates it on Spark | Before Phase 5 |
| **Firebase Blaze** (pay-as-you-go, budget alert ≈ US$5) | Cloud Run API and Cloud Scheduler (backup, account-deletion cascade, Ride-along reward validation; ADR-004), BigQuery export. Trip files go to Cloudflare R2 (free up to 10 GB) | Not yet (Gate 1 #13) | Before building F-ACC-03 / F-SHR-05 (§9 OQ-16) |
| **RevenueCat** | Entitlements across StoreKit 2 and Play Billing, offers, promotional entitlements, experiments | To set up | Phase 5 (backend) |
| **Map provider** | Replay, Analysis, map share card | iOS: MapKit. Android: Google Maps SDK or MapLibre + a tile provider (ADR-005) | Decided in Phase 4 |
| **Email provider** | Waitlist confirmation and launch emails (CASL-compliant) | Not chosen (§9 OQ-14) | Phase 5 |
| **Domain** driveperf.com | Website, app links, email links | Namecheap registrar; DNS to Firebase Hosting later (A18 runbook) | Phase 5 |
| **Legal counsel** | Privacy policy, terms, Law 25 PIA, Quebec French, age laws, trademark, Replay design review | To engage | Before Phase 6 beta |
| **Mileon** on the product owner's iPhone | Spike 1 benchmark | Available | Spike 1 |
| **Test devices** | Spike 1–5, field tests | See 8.2 | Phase 5 |

### 8.2 Assumptions

| # | Assumption | If wrong |
|---|---|---|
| A-1 | iOS 26.0+ (Liquid Glass) and Android 10+ (API 29) minimums; target API 36 (§9 OQ-21) | Wider support adds test cost and UI fallbacks |
| A-2 | Reference devices: iPhone non-Pro current generation (e.g. iPhone 17 or 16e) plus one older supported iPhone; Pixel A-series; Samsung Galaxy A-series; one Xiaomi/Redmi | Battery and capture targets are re-baselined |
| A-3 | All v1 notifications are local; no APNs/FCM push is needed for v1 features | Push needs the paid Apple account and APNs keys |
| A-4 | Weekly recap, Wrapped, Story, scoring and the Pro Preview run fully on the device; the only server features are sign-in, backup/restore, account deletion, Ride-along validation and the waitlist | More server features need Blaze and change the privacy labels |
| A-5 | Spark free quotas cover the website, waitlist, Auth and analytics through launch at a few thousand DAU (A3 §9) | Upgrade to Blaze earlier |
| A-6 | English only at launch, metric (CA) and imperial (US) units | See OQ-01 |
| A-7 | Raw trip data ≈ 10–20 MB/h uncompressed (A3 §2); ≤ 5 MB/h stored after processing (F-SET-04) | Storage management becomes P0 |
| A-8 | Personas, prices and retention targets are hypotheses (A2) to validate in interviews and the beta | Re-plan pricing or targets at a gate |
| A-9 | One developer-product owner plus AI agents (kickoff plan §4); phase durations as planned | §9 OQ-17 scope trade-off |

---

## 9. Open questions

Gate 1 decisions are not changed here. Where I think one is under strain, it is raised below for the product owner (PO = Keval).

| ID | Question | Why it matters / my proposal | Owner | Due phase |
|---|---|---|---|---|
| OQ-01 | ✅ *Resolved at Gate 2, see the top of this PRD.* **Quebec French.** What do the Charter of the French language rules require for the app, the store listings, the website and marketing aimed at Quebec? | Gate 1 #15 flags it. Options: FR-CA at launch, or launch in English with no Quebec-targeted marketing until FR-CA ships. Affects Phase 6 scope. | PO + counsel | Decision by end of Phase 5; confirmed before Phase 7 |
| OQ-02 | **Usage-statistics default.** Opt-in (nothing pre-selected) vs opt-out, under Law 25 privacy-by-default and US sensitive-data rules — and how WDD, activation and retention are measured if many users decline. | I specified an explicit choice with nothing pre-selected (F-ONB-08). WDD would then be measured on the opted-in cohort, with beta testers consenting to full telemetry. **Tension with Gate 1 #11** measurability. | Counsel + A10 (ADR-007) + PO | Phase 4 |
| OQ-03 | iOS behaviour after force-quit, reboot and in Low Power Mode on iOS 26.x/27.x | Sources conflict (A3 §1.5). May change the honest capture copy (Gate 1 #10). | A13a (Spike 1) | Phase 5 |
| OQ-04 | Do the provisional capture targets (Q1–Q13) hold? | Spike 1 on iOS; A19 field test on both platforms. Changes recorded in `decisions.md`. | A13a, A19 → PO | Phase 5 (iOS), Phase 6 (both) |
| OQ-05 | Is promoting an automatic trip as an Android Live Update acceptable under Play's "user-initiated" expectation? | Proposal: promote only after confirmation, never re-promote; fall back to a standard notification. Check in the closed-testing policy review. | A16 + A20 | Phase 6 |
| OQ-06 | ✅ *Resolved at Gate 2, see the top of this PRD.* **Replay for trips older than 30 days on Free.** I applied Gate 1 #6's "30-day Analysis" to Trip Replay too (Mileon's free tier is also 30 days). Should basic Replay stay free forever, with only Analysis gated? | Replay is P0 and a baseline expectation; gating it may feel like "lost" data. Interpretation, not a change. | PO | Gate 2 |
| OQ-07 | ✅ *Resolved at Gate 2, see the top of this PRD.* **"Trends" in Free.** Gate 1 #6 puts trends in Pro; A2 gives Free a weekly trend. I gave Free the weekly view with a 4-week line and Pro month/year/car/route. Confirm. | Interpretation of a binding decision. | PO | Gate 2 |
| OQ-08 | ✅ *Resolved at Gate 2, see the top of this PRD.* Pro Preview for low-usage users (< 3 trips in 14 days): end on day 15 anyway, or extend (e.g. until 5 trips or +14 days)? | Gate 1 #5 says 14 days; an extension would be a change, so it needs a PO decision or a beta A/B test. | PO + A2 | Gate 2 (or beta experiment) |
| OQ-09 | Cloud data retention after Pro lapses | ✅ **Resolved 6 Nov 2026 (Keval):** keep the cloud copy **30 days** after Pro ends, with reminders at 30, 7 and 1 days left, then delete it; local data is untouched. While Pro is active, the cloud copy is kept until the user deletes it. On-device data is kept until the user deletes it. | PO | Done |
| OQ-10 | How to limit the founding price to waitlist and beta members (Apple offer codes; Play developer-determined offers or promo codes) | Mechanism affects store setup and the waitlist email flow. | A20 + A17 | Phase 5 |
| OQ-11 | Store age ratings, and whether state app-store age-assurance laws (e.g. Texas, Utah) require us to read store age signals; is self-attestation enough for an 18+ product? | Compliance for the 18+ rule. | Counsel + A20 | Phase 6 |
| OQ-12 | May we request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` under Play's acceptable-use rules? | OEM reliability (EC-OEM). Otherwise link to settings only. | A16 (A3 Spike 3) | Phase 5 |
| OQ-13 | Waitlist bot protection: App Check with reCAPTCHA (may set Google cookies) vs honeypot + rules + rate limiting | Cookie-less promise on the website vs spam. | A18 + A10 | Phase 5 |
| OQ-14 | 🟡 **Partly resolved 6 Nov 2026:** mailboxes via Zoho Mail recommended (decisions log; Keval to confirm); the sending provider is chosen before Phase 6 email features. Email provider for waitlist confirmation and launch emails (needs Blaze + an extension, or a third-party service) | CASL compliance; Spark cannot send email. | PO + A18 | Phase 5 |
| OQ-15 | Android map provider and static map snapshots for the map share card (Google Maps SDK vs MapLibre + tiles; cost; terms) | Replay is P0; cost and licence for images users share. | A10 (ADR-005) | Phase 4 |
| OQ-16 | When to upgrade Firebase to Blaze | Needed for backup, deletion cascade, Ride-along validation and email. Proposal: at the start of Phase 6 with a US$5 budget alert. | PO | End of Phase 5 |
| OQ-17 | ✅ *Resolved at Gate 2, see the top of this PRD.* **Scope vs timeline.** 96 P0 features against a 6–10 week feature build. Accept a longer build (launch late May rather than April), or move items — some of which are binding Pro features (cloud backup, trends, export)? | Not a contradiction of Gate 1, but the binding Free/Pro split fixes much of the scope. A5's estimates will make this concrete. | PO | Gate 2 |
| OQ-18 | Counsel review of the Replay speed readout and max-speed pin (Gate 1 #16) and of share cards, given negligent-design precedent | Decision 16 stands; the question is whether the speed-neutral rule (§3.5) is a sufficient mitigation. | Counsel | Phase 6 (before beta) |
| OQ-19 | ✅ *Resolved at Gate 2, see the top of this PRD.* Trademark clearance for "DrivePerf" and "Smooth is a skill." | A2 flagged it before brand identity work. | PO | Phase 3 (before A6) |
| OQ-20 | Transit (bus/train) detection approach and an acceptable Q7 target | Phones can't reliably tell transit from driving; A12 heuristic + one-tap correction. | A12 + A19 | Phase 4 / Phase 6 |
| OQ-21 | ✅ **Resolved 6 Nov 2026 (Keval):** iOS 26+ and Android 10+ (minSdk 29), ADR-001. Minimum OS versions (proposed iOS 26+, Android 10+) | Market reach vs test cost and Liquid Glass. | A10 | Phase 4 |
| OQ-22 | Should "Was it needed?" apply to all event types (as specified) or braking only, and how many exclusions may a record-setting run have? | Fairness vs gaming one's own records. | A12 + A2 | Phase 4 |
| OQ-23 | ✅ **Resolved 6 Nov 2026 (Keval):** R1 analysis files are kept and backed up; the full-rate R0 log is not pruned automatically (kept until the user deletes it; Settings shows its size), data-model.md §2.6. Raw sensor data retention and stored size per hour | Storage growth (≈ 1.5 GB/yr raw for 1 h/day) vs "locked, not lost" Pro analysis of old trips. | A11 | Phase 4 |
| OQ-24 | ✅ *Resolved at Gate 2, see the top of this PRD.* **iOS live surface for automatic trips.** Gate 1 #9 specifies a notification plus a one-tap control/widget that starts a Live Activity. I implemented the control as "Show live drive", which attaches a Live Activity to a running auto trip. Is offering a tap during an auto trip compatible with Gate 1 #12 (no interaction while moving), or should the control only start trips before departure? | Mild tension between #9 and #12. Proposal: keep it, with copy and design that never prompt use while moving; passengers benefit. | A6 + PO | Phase 3 |
| OQ-25 | Criteria for the push-to-start evaluation (latency, cost, network dependency, privacy) | Planned for v1.2 as a decision. | A10 | v1.2 |
| OQ-26 | Apple and Google rules for referral rewards (Ride-along) | No referral-specific Apple rule was confirmed (A2 §4.2). | A20 | Phase 7 |

---

## Sources

Repository inputs (all read 6 Nov 2026): [gate-1-decisions.md](gate-1-decisions.md), [decisions.md](decisions.md), [A2-strategy-positioning.md](A2-strategy-positioning.md), [A1-market-refresh.md](../research/2026-11/A1-market-refresh.md), [A3-platform-feasibility.md](../research/2026-11/A3-platform-feasibility.md), [DrivePerf-v1-kickoff-plan.md](../planning/DrivePerf-v1-kickoff-plan.md), [2026-09 research pack](../research/2026-09/README.md) (notably `01-android-market-research.md` and `android-feasibility-evidence.md` for PIPEDA/OPC consent guidance and the Play account-deletion web-resource requirement).

Web checks (accessed 6 Nov 2026; secondary sources, to be confirmed by counsel):
- Quebec Law 25, location/profiling functions and privacy by default: [McCarthy Tétrault, "Quebec's Law 25 and cookies: not so cookie-cutter"](https://mccarthy.ca/en/insights/blogs/techlex/quebecs-law-25-and-cookies-not-so-cookie-cutter); [IAB Canada profiling transparency guidance (June 2022)](https://iabcanada.com/wp-content/uploads/2022/06/IAB-Canada_BPG-Que-Profiling-Transparency-Guidance_June-2022.pdf); [JD Supra, "Quebec's Law 25 and privacy by design"](https://jdsupra.com/legalnews/quebec-s-law-25-and-privacy-by-design-5-3667349).
- US state comprehensive privacy laws and precise geolocation: [Ketch, US privacy laws 2026](https://ketch.com/blog/posts/us-privacy-laws-2026); [Enzuzo, US state privacy laws 2026 tracker](https://www.enzuzo.com/blog/us-state-privacy-laws); [Foley, state privacy law comparison chart V16 (Jan 2026)](https://www.foley.com/wp-content/uploads/2026/01/U.S.-State-Comprehensive-Consumer-Privacy-Law-Comparison-Chart_V16.pdf); [Paul Hastings, new state privacy rules on sensitive data](https://www.paulhastings.com/insights/ph-privacy/new-state-privacy-rules-require-businesses-to-assess-high-risk-activities-and-regulate-disclosures-of-sensitive-data).

Platform and store facts (Apple guidelines, Play declarations including the 27 Jan 2027 fine-location change, Live Activity limits, Firebase Spark vs Blaze) are cited from A3 and its dated official sources.
