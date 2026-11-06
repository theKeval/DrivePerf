# Jira map: PRD v1 → DRIVE

Generated on 6 Nov 2026 from [PRD-v1.md](PRD-v1.md) by A5. Board: [DRIVE](https://thekeval.atlassian.net/jira/software/projects/DRIVE/boards/2). Every v1 issue carries the label `v1`. The legacy prototype issues (DRIVE-5 to DRIVE-10) are labelled `legacy-prototype`.

**Conventions.** Stories are named `F-<EPIC>-NN · title` after the PRD feature IDs. Priority: P0 → Highest, P1 → High, P2 → Medium. Release labels: `v1.0`, `v1.1`, `v1.2`. Platform labels: `ios`, `android`, `shared`, `backend`, `web`. Stories with UI are labelled `design-needed` and have a **Design** section (pending prototype → ready). Design artifacts go in `docs/design/<story key>/{ios,android,web}/`.

## Epics

| Epic | Key |
|---|---|
| v1 · Onboarding & Permissions | [DRIVE-14](https://thekeval.atlassian.net/browse/DRIVE-14) |
| v1 · Trip Capture | [DRIVE-15](https://thekeval.atlassian.net/browse/DRIVE-15) |
| v1 · Data Quality & Calibration | [DRIVE-16](https://thekeval.atlassian.net/browse/DRIVE-16) |
| v1 · Drive Story | [DRIVE-17](https://thekeval.atlassian.net/browse/DRIVE-17) |
| v1 · Trip Replay & Analysis | [DRIVE-18](https://thekeval.atlassian.net/browse/DRIVE-18) |
| v1 · Scores & Coaching | [DRIVE-19](https://thekeval.atlassian.net/browse/DRIVE-19) |
| v1 · History & Stats | [DRIVE-20](https://thekeval.atlassian.net/browse/DRIVE-20) |
| v1 · Routes & Route Records | [DRIVE-21](https://thekeval.atlassian.net/browse/DRIVE-21) |
| v1 · Garage | [DRIVE-22](https://thekeval.atlassian.net/browse/DRIVE-22) |
| v1 · Sharing & Referral | [DRIVE-23](https://thekeval.atlassian.net/browse/DRIVE-23) |
| v1 · Account & Cloud Backup | [DRIVE-24](https://thekeval.atlassian.net/browse/DRIVE-24) |
| v1 · Privacy Centre, Export & Deletion | [DRIVE-25](https://thekeval.atlassian.net/browse/DRIVE-25) |
| v1 · Subscription, Pro Preview & Paywall | [DRIVE-26](https://thekeval.atlassian.net/browse/DRIVE-26) |
| v1 · Notifications, Weekly Recap & Drive Wrapped | [DRIVE-27](https://thekeval.atlassian.net/browse/DRIVE-27) |
| v1 · Settings & Health Check | [DRIVE-28](https://thekeval.atlassian.net/browse/DRIVE-28) |
| v1 · Website & Waitlist | [DRIVE-29](https://thekeval.atlassian.net/browse/DRIVE-29) |
| v1 · Analytics & Crash Reporting | [DRIVE-30](https://thekeval.atlassian.net/browse/DRIVE-30) |
| v1 · Platform foundations | [DRIVE-31](https://thekeval.atlassian.net/browse/DRIVE-31) |
| v1 · Prototypes | [DRIVE-32](https://thekeval.atlassian.net/browse/DRIVE-32) |

## Foundation tasks (in order)

| # | Task | Key |
|---|---|---|
| FND-01 | Spike 1: iOS capture test app benchmarked against Mileon | [DRIVE-36](https://thekeval.atlassian.net/browse/DRIVE-36) |
| FND-02 | Accounts and services setup (Keval) | [DRIVE-42](https://thekeval.atlassian.net/browse/DRIVE-42) |
| FND-03 | Architecture ADRs and overview | [DRIVE-46](https://thekeval.atlassian.net/browse/DRIVE-46) |
| FND-04 | Data model, sync and privacy data flow | [DRIVE-51](https://thekeval.atlassian.net/browse/DRIVE-51) |
| FND-05 | Sensor pipeline and scoring specification | [DRIVE-56](https://thekeval.atlassian.net/browse/DRIVE-56) |
| FND-06 | Monorepo scaffold and CI | [DRIVE-59](https://thekeval.atlassian.net/browse/DRIVE-59) |
| FND-07 | Shared KMP core: state machine, pipeline, scoring, storage | [DRIVE-62](https://thekeval.atlassian.net/browse/DRIVE-62) |
| FND-08 | iOS app shell (SwiftUI, Liquid Glass) | [DRIVE-67](https://thekeval.atlassian.net/browse/DRIVE-67) |
| FND-09 | Android app shell (Compose, Material 3 Expressive) | [DRIVE-72](https://thekeval.atlassian.net/browse/DRIVE-72) |
| FND-10 | Firebase backend v1 | [DRIVE-76](https://thekeval.atlassian.net/browse/DRIVE-76) |
| FND-11 | Website on Firebase Hosting | [DRIVE-79](https://thekeval.atlassian.net/browse/DRIVE-79) |
| FND-12 | Field-test and calibration protocol | [DRIVE-83](https://thekeval.atlassian.net/browse/DRIVE-83) |
| FND-13 | Store launch readiness | [DRIVE-89](https://thekeval.atlassian.net/browse/DRIVE-89) |
| FND-14 | Trademark preliminary search (last, after development) | [DRIVE-91](https://thekeval.atlassian.net/browse/DRIVE-91) |

## Prototype and design tasks

| # | Task | Key |
|---|---|---|
| DES-00 | Design system, brand and motion language (A6) | [DRIVE-99](https://thekeval.atlassian.net/browse/DRIVE-99) |
| DES-01 | iOS prototype: History & Stats screens (7) | [DRIVE-103](https://thekeval.atlassian.net/browse/DRIVE-103) |
| DES-02 | Android prototype: History & Stats screens (7) | [DRIVE-107](https://thekeval.atlassian.net/browse/DRIVE-107) |
| DES-03 | iOS prototype: Onboarding & Permissions screens (14) | [DRIVE-112](https://thekeval.atlassian.net/browse/DRIVE-112) |
| DES-04 | Android prototype: Onboarding & Permissions screens (16) | [DRIVE-118](https://thekeval.atlassian.net/browse/DRIVE-118) |
| DES-05 | iOS prototype: Trip Capture screens (14) | [DRIVE-123](https://thekeval.atlassian.net/browse/DRIVE-123) |
| DES-06 | Android prototype: Trip Capture screens (14) | [DRIVE-129](https://thekeval.atlassian.net/browse/DRIVE-129) |
| DES-07 | iOS prototype: Drive Story screens (1) | [DRIVE-134](https://thekeval.atlassian.net/browse/DRIVE-134) |
| DES-08 | Android prototype: Drive Story screens (1) | [DRIVE-138](https://thekeval.atlassian.net/browse/DRIVE-138) |
| DES-09 | iOS prototype: Trip Replay & Analysis screens (9) | [DRIVE-143](https://thekeval.atlassian.net/browse/DRIVE-143) |
| DES-10 | Android prototype: Trip Replay & Analysis screens (9) | [DRIVE-147](https://thekeval.atlassian.net/browse/DRIVE-147) |
| DES-11 | iOS prototype: Data Quality & Calibration screens (3) | [DRIVE-35](https://thekeval.atlassian.net/browse/DRIVE-35) |
| DES-12 | Android prototype: Data Quality & Calibration screens (3) | [DRIVE-40](https://thekeval.atlassian.net/browse/DRIVE-40) |
| DES-13 | iOS prototype: Scores & Coaching screens (6) | [DRIVE-44](https://thekeval.atlassian.net/browse/DRIVE-44) |
| DES-14 | Android prototype: Scores & Coaching screens (6) | [DRIVE-48](https://thekeval.atlassian.net/browse/DRIVE-48) |
| DES-15 | iOS prototype: Routes & Route Records screens (5) | [DRIVE-52](https://thekeval.atlassian.net/browse/DRIVE-52) |
| DES-16 | Android prototype: Routes & Route Records screens (5) | [DRIVE-58](https://thekeval.atlassian.net/browse/DRIVE-58) |
| DES-17 | iOS prototype: Garage screens (3) | [DRIVE-63](https://thekeval.atlassian.net/browse/DRIVE-63) |
| DES-18 | Android prototype: Garage screens (3) | [DRIVE-68](https://thekeval.atlassian.net/browse/DRIVE-68) |
| DES-19 | iOS prototype: Sharing & Referral screens (4) | [DRIVE-73](https://thekeval.atlassian.net/browse/DRIVE-73) |
| DES-20 | Android prototype: Sharing & Referral screens (4) | [DRIVE-78](https://thekeval.atlassian.net/browse/DRIVE-78) |
| DES-21 | iOS prototype: Account & Cloud Backup screens (7) | [DRIVE-84](https://thekeval.atlassian.net/browse/DRIVE-84) |
| DES-22 | Android prototype: Account & Cloud Backup screens (7) | [DRIVE-88](https://thekeval.atlassian.net/browse/DRIVE-88) |
| DES-23 | iOS prototype: Privacy Centre, Export & Deletion screens (7) | [DRIVE-94](https://thekeval.atlassian.net/browse/DRIVE-94) |
| DES-24 | Android prototype: Privacy Centre, Export & Deletion screens (7) | [DRIVE-98](https://thekeval.atlassian.net/browse/DRIVE-98) |
| DES-25 | iOS prototype: Subscription, Pro Preview & Paywall screens (7) | [DRIVE-104](https://thekeval.atlassian.net/browse/DRIVE-104) |
| DES-26 | Android prototype: Subscription, Pro Preview & Paywall screens (7) | [DRIVE-110](https://thekeval.atlassian.net/browse/DRIVE-110) |
| DES-27 | iOS prototype: Notifications, Weekly Recap & Drive Wrapped screens (4) | [DRIVE-114](https://thekeval.atlassian.net/browse/DRIVE-114) |
| DES-28 | Android prototype: Notifications, Weekly Recap & Drive Wrapped screens (4) | [DRIVE-119](https://thekeval.atlassian.net/browse/DRIVE-119) |
| DES-29 | iOS prototype: Settings & Health Check screens (9) | [DRIVE-122](https://thekeval.atlassian.net/browse/DRIVE-122) |
| DES-30 | Android prototype: Settings & Health Check screens (9) | [DRIVE-127](https://thekeval.atlassian.net/browse/DRIVE-127) |
| DES-31 | Web prototype: driveperf.com pages (11) | [DRIVE-132](https://thekeval.atlassian.net/browse/DRIVE-132) |
| DES-32 | Gate 3: prototype review and design-ready check | [DRIVE-136](https://thekeval.atlassian.net/browse/DRIVE-136) |

## Stories (PRD features)

| Feature | Title | Priority | Screens | Key |
|---|---|---|---|---|
| F-ONB-01 | Intro and positioning | P0 | S-ONB-01, S-ONB-02, S-ONB-03 | [DRIVE-33](https://thekeval.atlassian.net/browse/DRIVE-33) |
| F-ONB-02 | Age confirmation and terms | P0 | S-ONB-04 | [DRIVE-38](https://thekeval.atlassian.net/browse/DRIVE-38) |
| F-ONB-03 | Honest capture promise | P0 | S-ONB-05 | [DRIVE-43](https://thekeval.atlassian.net/browse/DRIVE-43) |
| F-ONB-04 | Progressive location permissions | P0 | S-ONB-06, S-ONB-08, S-ONB-09, S-ONB-11, S-ONB-16 | [DRIVE-49](https://thekeval.atlassian.net/browse/DRIVE-49) |
| F-ONB-05 | Motion and activity permission | P0 | S-ONB-07 | [DRIVE-54](https://thekeval.atlassian.net/browse/DRIVE-54) |
| F-ONB-06 | Notification permission | P0 | S-ONB-10 | [DRIVE-57](https://thekeval.atlassian.net/browse/DRIVE-57) |
| F-ONB-07 | Android battery and reliability step | P0 | S-ONB-12, S-SET-06 | [DRIVE-65](https://thekeval.atlassian.net/browse/DRIVE-65) |
| F-ONB-08 | Help improve DrivePerf (analytics and crash choice) | P0 | S-ONB-13, S-PRV-07 | [DRIVE-70](https://thekeval.atlassian.net/browse/DRIVE-70) |
| F-ONB-09 | Add your first car | P1 | S-ONB-14, S-GAR-03 | [DRIVE-74](https://thekeval.atlassian.net/browse/DRIVE-74) |
| F-ONB-10 | Ready screen and quick-start surfaces | P1 | S-ONB-15 | [DRIVE-80](https://thekeval.atlassian.net/browse/DRIVE-80) |
| F-CAP-01 | Manual start and stop | P0 | S-HOME-01, S-DRV-01, S-DRV-02, S-DRV-04, S-LIVE-01, S-LIVE-02, S-LIVE-05, S-LIVE-06 | [DRIVE-85](https://thekeval.atlassian.net/browse/DRIVE-85) |
| F-CAP-02 | Automatic trip start | P0 | S-HOME-01, S-HOME-02, S-LIVE-09, S-DRV-01 | [DRIVE-90](https://thekeval.atlassian.net/browse/DRIVE-90) |
| F-CAP-03 | Automatic trip end and finalisation | P0 | S-DRV-04, S-LIVE-09 | [DRIVE-95](https://thekeval.atlassian.net/browse/DRIVE-95) |
| F-CAP-04 | Non-driving rejection (walking, running, cycling, transit) | P0 | S-TRP-01, S-TRP-02, S-STY-01 | [DRIVE-100](https://thekeval.atlassian.net/browse/DRIVE-100) |
| F-CAP-05 | Start backfill and the estimated start segment | P0 | S-RPL-01, S-TRP-01, S-DQ-01 | [DRIVE-106](https://thekeval.atlassian.net/browse/DRIVE-106) |
| F-CAP-06 | Durable recording and crash recovery | P0 | S-TRP-01, S-HOME-01 | [DRIVE-109](https://thekeval.atlassian.net/browse/DRIVE-109) |
| F-CAP-07 | In-drive locked screen (no interaction while moving) | P0 | S-DRV-01, S-DRV-05 | [DRIVE-115](https://thekeval.atlassian.net/browse/DRIVE-115) |
| F-CAP-08 | Passenger escape | P0 | S-DRV-01, S-DRV-03 | [DRIVE-120](https://thekeval.atlassian.net/browse/DRIVE-120) |
| F-CAP-09 | iOS Live Activity and Dynamic Island | P0 | S-LIVE-01, S-LIVE-02 | [DRIVE-128](https://thekeval.atlassian.net/browse/DRIVE-128) |
| F-CAP-10 | iOS Control Center control and widgets | P0 | S-LIVE-03, S-LIVE-04 | [DRIVE-133](https://thekeval.atlassian.net/browse/DRIVE-133) |
| F-CAP-11 | Auto-trip recording notification | P0 | S-LIVE-09 | [DRIVE-137](https://thekeval.atlassian.net/browse/DRIVE-137) |
| F-CAP-12 | Android Live Update and ongoing notification | P0 | S-LIVE-05, S-LIVE-06 | [DRIVE-142](https://thekeval.atlassian.net/browse/DRIVE-142) |
| F-CAP-13 | Android Quick Settings tile and widget | P1 | S-LIVE-07, S-LIVE-08 | [DRIVE-146](https://thekeval.atlassian.net/browse/DRIVE-146) |
| F-CAP-14 | Auto-detect watchdog and re-arming | P0 | S-LIVE-10, S-HOME-01, S-SET-05 | [DRIVE-150](https://thekeval.atlassian.net/browse/DRIVE-150) |
| F-CAP-15 | Pause auto-detect | P1 | S-HOME-03 | [DRIVE-152](https://thekeval.atlassian.net/browse/DRIVE-152) |
| F-CAP-16 | Battery and thermal management | P0 | S-SET-03, S-SET-05 | [DRIVE-155](https://thekeval.atlassian.net/browse/DRIVE-155) |
| F-CAP-17 | Long and short trip handling | P1 | S-TRP-01, S-LIVE-01 | [DRIVE-157](https://thekeval.atlassian.net/browse/DRIVE-157) |
| F-DQ-01 | Phone-to-vehicle orientation alignment | P0 | S-DQ-01, S-STY-01 | [DRIVE-160](https://thekeval.atlassian.net/browse/DRIVE-160) |
| F-DQ-02 | Phone-movement detection and segment exclusion | P0 | S-DQ-01, S-STY-01, S-RPL-01 | [DRIVE-164](https://thekeval.atlassian.net/browse/DRIVE-164) |
| F-DQ-03 | GPS quality gating, gaps and tunnels | P0 | S-RPL-01, S-ANL-01, S-DQ-01 | [DRIVE-167](https://thekeval.atlassian.net/browse/DRIVE-167) |
| F-DQ-04 | Data-quality badge and detail | P0 | S-DQ-01, S-TRP-01, S-STY-01, S-RPL-01, S-ANL-01 | [DRIVE-170](https://thekeval.atlassian.net/browse/DRIVE-170) |
| F-DQ-05 | Provenance labels (observed / derived / estimated) | P0 | S-DQ-01, S-SCO-02, S-RPL-02, S-ANL-01, S-TRP-01, S-PRV-04 | [DRIVE-172](https://thekeval.atlassian.net/browse/DRIVE-172) |
| F-DQ-06 | Phone placement guide | P1 | S-DQ-02 | [DRIVE-176](https://thekeval.atlassian.net/browse/DRIVE-176) |
| F-DQ-07 | Missed-trip check and capture feedback | P1 | S-NTF-02, S-SET-05 | [DRIVE-178](https://thekeval.atlassian.net/browse/DRIVE-178) |
| F-STY-01 | Drive Story generation and player | P0 | S-STY-01, S-DRV-04 | [DRIVE-182](https://thekeval.atlassian.net/browse/DRIVE-182) |
| F-STY-02 | "Drive decoded." notification | P0 | S-NTF-01 | [DRIVE-184](https://thekeval.atlassian.net/browse/DRIVE-184) |
| F-STY-03 | First Story and baseline frame | P0 | S-STY-01 | [DRIVE-187](https://thekeval.atlassian.net/browse/DRIVE-187) |
| F-STY-04 | Degraded and unscorable Story variants | P0 | S-STY-01 | [DRIVE-191](https://thekeval.atlassian.net/browse/DRIVE-191) |
| F-STY-05 | Story held while moving | P0 | S-DRV-05 | [DRIVE-194](https://thekeval.atlassian.net/browse/DRIVE-194) |
| F-STY-06 | Story accessibility and reduced motion | P0 | S-STY-01, S-RCP-01, S-WRP-01, S-WRP-02 | [DRIVE-197](https://thekeval.atlassian.net/browse/DRIVE-197) |
| F-RPL-01 | Animated map replay | P0 | S-RPL-01, S-TRP-01 | [DRIVE-200](https://thekeval.atlassian.net/browse/DRIVE-200) |
| F-RPL-02 | Replay readouts: current speed, max speed, stops, elevation | P0 | S-RPL-01, S-RPL-03 | [DRIVE-203](https://thekeval.atlassian.net/browse/DRIVE-203) |
| F-RPL-03 | Event pins on the map | P0 | S-RPL-01, S-ANL-01 | [DRIVE-34](https://thekeval.atlassian.net/browse/DRIVE-34) |
| F-RPL-04 | Event evidence sheet | P0 | S-RPL-02, S-SCO-03, S-SCO-04 | [DRIVE-39](https://thekeval.atlassian.net/browse/DRIVE-39) |
| F-RPL-05 | Analysis: synced timeline | P0 | S-ANL-01, S-ANL-03 | [DRIVE-45](https://thekeval.atlassian.net/browse/DRIVE-45) |
| F-RPL-06 | G-G circle | P0 | S-ANL-01, S-ANL-02 | [DRIVE-50](https://thekeval.atlassian.net/browse/DRIVE-50) |
| F-RPL-07 | Trip detail and stats | P0 | S-TRP-01 | [DRIVE-55](https://thekeval.atlassian.net/browse/DRIVE-55) |
| F-RPL-08 | Data-quality overlay in Replay and Analysis | P0 | S-RPL-01, S-ANL-01, S-DQ-01 | [DRIVE-61](https://thekeval.atlassian.net/browse/DRIVE-61) |
| F-RPL-09 | Range statistics and advanced channels | P1 | S-ANL-04, S-ANL-03 | [DRIVE-66](https://thekeval.atlassian.net/browse/DRIVE-66) |
| F-RPL-10 | Replay layers and filters | P2 | S-RPL-04 | [DRIVE-71](https://thekeval.atlassian.net/browse/DRIVE-71) |
| F-SCO-01 | Smoothness score (baseline-relative) | P0 | S-SCO-01, S-STY-01, S-TRP-01 | [DRIVE-77](https://thekeval.atlassian.net/browse/DRIVE-77) |
| F-SCO-02 | Baseline and calibrating period | P0 | S-SCO-01, S-STY-01, S-STA-01 | [DRIVE-82](https://thekeval.atlassian.net/browse/DRIVE-82) |
| F-SCO-03 | Smoothness contributors | P0 | S-SCO-01 | [DRIVE-87](https://thekeval.atlassian.net/browse/DRIVE-87) |
| F-SCO-04 | Event detection (hard braking, hard acceleration, hard cornering) | P0 | S-RPL-01, S-RPL-02, S-TRP-01 | [DRIVE-93](https://thekeval.atlassian.net/browse/DRIVE-93) |
| F-SCO-05 | "Was it needed?" necessary-event exclusion | P0 | S-SCO-03, S-RPL-02, S-STY-01 | [DRIVE-97](https://thekeval.atlassian.net/browse/DRIVE-97) |
| F-SCO-06 | One coaching tip per trip | P0 | S-SCO-04, S-STY-01, S-RPL-02, S-TRP-01 | [DRIVE-102](https://thekeval.atlassian.net/browse/DRIVE-102) |
| F-SCO-07 | Score fairness guardrails | P0 | S-SCO-02 | [DRIVE-108](https://thekeval.atlassian.net/browse/DRIVE-108) |
| F-SCO-08 | "How scores work" explainer | P0 | S-SCO-02 | [DRIVE-113](https://thekeval.atlassian.net/browse/DRIVE-113) |
| F-SCO-09 | Efficiency habits (proxy) | P1 | S-SCO-06, S-TRP-01 | [DRIVE-117](https://thekeval.atlassian.net/browse/DRIVE-117) |
| F-SCO-10 | Weekly focus habit | P1 | S-SCO-05, S-RCP-01 | [DRIVE-124](https://thekeval.atlassian.net/browse/DRIVE-124) |
| F-HIS-01 | Drives list | P0 | S-HIS-01, S-HIS-02, S-NAV-01 | [DRIVE-126](https://thekeval.atlassian.net/browse/DRIVE-126) |
| F-HIS-02 | Trip edit and corrections | P0 | S-TRP-02, S-TRP-03, S-STY-01 | [DRIVE-131](https://thekeval.atlassian.net/browse/DRIVE-131) |
| F-HIS-03 | "Locked, not lost" history | P0 | S-HIS-01, S-TRP-01, S-SUB-03 | [DRIVE-139](https://thekeval.atlassian.net/browse/DRIVE-139) |
| F-HIS-04 | Insights: weekly stats | P0 | S-STA-01 | [DRIVE-141](https://thekeval.atlassian.net/browse/DRIVE-141) |
| F-HIS-05 | Pro trends | P0 | S-STA-01, S-STA-02 | [DRIVE-145](https://thekeval.atlassian.net/browse/DRIVE-145) |
| F-HIS-06 | Merge and split trips | P2 | S-TRP-02 | [DRIVE-149](https://thekeval.atlassian.net/browse/DRIVE-149) |
| F-RTE-01 | Route detection and naming | P0 | S-RTE-03, S-STY-01, S-NTF-01 | [DRIVE-151](https://thekeval.atlassian.net/browse/DRIVE-151) |
| F-RTE-02 | Routes list and route detail | P0 | S-RTE-01, S-RTE-02, S-NAV-01 | [DRIVE-154](https://thekeval.atlassian.net/browse/DRIVE-154) |
| F-RTE-03 | Route records (Smoothness only) | P0 | S-RTE-02, S-STY-01, S-NTF-01 | [DRIVE-159](https://thekeval.atlassian.net/browse/DRIVE-159) |
| F-RTE-04 | Ghost comparison and "where you gained" | P0 | S-RTE-04, S-SUB-03 | [DRIVE-162](https://thekeval.atlassian.net/browse/DRIVE-162) |
| F-RTE-05 | Free two-route limit | P0 | S-RTE-01, S-RTE-05, S-SUB-03 | [DRIVE-165](https://thekeval.atlassian.net/browse/DRIVE-165) |
| F-RTE-06 | Route management | P1 | S-RTE-05 | [DRIVE-168](https://thekeval.atlassian.net/browse/DRIVE-168) |
| F-GAR-01 | Garage and car profiles | P0 | S-GAR-01, S-GAR-02, S-GAR-03, S-NAV-01 | [DRIVE-173](https://thekeval.atlassian.net/browse/DRIVE-173) |
| F-GAR-02 | Per-trip car assignment | P0 | S-TRP-02, S-STY-01, S-GAR-01 | [DRIVE-175](https://thekeval.atlassian.net/browse/DRIVE-175) |
| F-GAR-03 | Per-car stats | P1 | S-GAR-02 | [DRIVE-180](https://thekeval.atlassian.net/browse/DRIVE-180) |
| F-GAR-04 | Bluetooth car auto-assignment and start trigger | P2 | S-GAR-03 | [DRIVE-185](https://thekeval.atlassian.net/browse/DRIVE-185) |
| F-SHR-01 | Share card builder | P0 | S-SHR-01, S-STY-01, S-TRP-01 | [DRIVE-186](https://thekeval.atlassian.net/browse/DRIVE-186) |
| F-SHR-02 | Privacy trimming and metadata stripping | P0 | S-SHR-01, S-PRV-02 | [DRIVE-189](https://thekeval.atlassian.net/browse/DRIVE-189) |
| F-SHR-03 | Opt-in map card | P1 | S-SHR-01, S-SHR-02 | [DRIVE-192](https://thekeval.atlassian.net/browse/DRIVE-192) |
| F-SHR-04 | Recap and Wrapped share cards | P1 | S-SHR-01, S-RCP-01, S-WRP-01, S-WRP-02 | [DRIVE-195](https://thekeval.atlassian.net/browse/DRIVE-195) |
| F-SHR-05 | Ride-along pass referral | P1 | S-SHR-03, S-SHR-04, S-WEB-08 | [DRIVE-199](https://thekeval.atlassian.net/browse/DRIVE-199) |
| F-ACC-01 | Local-first, no account required | P0 | S-ACC-01, S-NAV-01 | [DRIVE-202](https://thekeval.atlassian.net/browse/DRIVE-202) |
| F-ACC-02 | Optional sign-in (Apple, Google, email link) | P0 | S-ACC-02, S-ACC-03 | [DRIVE-204](https://thekeval.atlassian.net/browse/DRIVE-204) |
| F-ACC-03 | Cloud backup | P0 | S-ACC-04, S-ACC-05, S-SUB-03 | [DRIVE-205](https://thekeval.atlassian.net/browse/DRIVE-205) |
| F-ACC-04 | Restore and multi-device | P0 | S-ACC-06 | [DRIVE-37](https://thekeval.atlassian.net/browse/DRIVE-37) |
| F-ACC-05 | Sign out | P1 | S-ACC-07 | [DRIVE-41](https://thekeval.atlassian.net/browse/DRIVE-41) |
| F-PRV-01 | Privacy Centre | P0 | S-PRV-01 | [DRIVE-47](https://thekeval.atlassian.net/browse/DRIVE-47) |
| F-PRV-02 | Download all my data | P0 | S-PRV-03 | [DRIVE-53](https://thekeval.atlassian.net/browse/DRIVE-53) |
| F-PRV-03 | Per-trip GPX/CSV export | P0 | S-PRV-04, S-TRP-01 | [DRIVE-60](https://thekeval.atlassian.net/browse/DRIVE-60) |
| F-PRV-04 | Delete trips and all local data | P0 | S-PRV-05, S-TRP-03 | [DRIVE-64](https://thekeval.atlassian.net/browse/DRIVE-64) |
| F-PRV-05 | Delete account and cloud data | P0 | S-PRV-06, S-WEB-07 | [DRIVE-69](https://thekeval.atlassian.net/browse/DRIVE-69) |
| F-PRV-06 | Analytics and diagnostics controls | P0 | S-PRV-07, S-ONB-13 | [DRIVE-75](https://thekeval.atlassian.net/browse/DRIVE-75) |
| F-PRV-07 | Privacy zones | P1 | S-PRV-02, S-SHR-01 | [DRIVE-81](https://thekeval.atlassian.net/browse/DRIVE-81) |
| F-SUB-01 | Plans and entitlements | P0 | S-SUB-04, S-SUB-05 | [DRIVE-86](https://thekeval.atlassian.net/browse/DRIVE-86) |
| F-SUB-02 | 14-day Pro Preview (no card) | P0 | S-SUB-01, S-HOME-01 | [DRIVE-92](https://thekeval.atlassian.net/browse/DRIVE-92) |
| F-SUB-03 | Day-15 paywall: "Your first two weeks, decoded" | P0 | S-SUB-02, S-SUB-04, S-SUB-07 | [DRIVE-96](https://thekeval.atlassian.net/browse/DRIVE-96) |
| F-SUB-04 | Contextual locks with the user's own data | P0 | S-SUB-03 | [DRIVE-101](https://thekeval.atlassian.net/browse/DRIVE-101) |
| F-SUB-05 | Founding-member first-year offer | P0 | S-SUB-02, S-SUB-04 | [DRIVE-105](https://thekeval.atlassian.net/browse/DRIVE-105) |
| F-SUB-06 | Manage subscription and restore purchases | P0 | S-SUB-06 | [DRIVE-111](https://thekeval.atlassian.net/browse/DRIVE-111) |
| F-SUB-07 | Preview ended / "You're on Free" state | P0 | S-SUB-07 | [DRIVE-116](https://thekeval.atlassian.net/browse/DRIVE-116) |
| F-SUB-08 | Paywall guardrails | P0 | S-SUB-02, S-SUB-03, S-SUB-04 | [DRIVE-121](https://thekeval.atlassian.net/browse/DRIVE-121) |
| F-NTF-01 | Notification framework | P0 | S-NTF-01, S-SET-04 | [DRIVE-125](https://thekeval.atlassian.net/browse/DRIVE-125) |
| F-NTF-02 | Capture-problem notifications | P0 | S-NTF-01, S-SET-05 | [DRIVE-130](https://thekeval.atlassian.net/browse/DRIVE-130) |
| F-NTF-03 | Weekly recap | P0 | S-RCP-01, S-NTF-01 | [DRIVE-135](https://thekeval.atlassian.net/browse/DRIVE-135) |
| F-NTF-04 | Monthly Wrapped | P1 | S-WRP-01, S-SHR-01 | [DRIVE-140](https://thekeval.atlassian.net/browse/DRIVE-140) |
| F-NTF-05 | Annual Drive Wrapped | P1 | S-WRP-02, S-SHR-01 | [DRIVE-144](https://thekeval.atlassian.net/browse/DRIVE-144) |
| F-NTF-06 | Lifecycle notifications | P1 | S-NTF-01 | [DRIVE-148](https://thekeval.atlassian.net/browse/DRIVE-148) |
| F-SET-01 | Settings, units and display | P0 | S-SET-01, S-SET-02, S-SET-03 | [DRIVE-153](https://thekeval.atlassian.net/browse/DRIVE-153) |
| F-SET-02 | Health Check | P0 | S-SET-05, S-HOME-01, S-HOME-02 | [DRIVE-156](https://thekeval.atlassian.net/browse/DRIVE-156) |
| F-SET-03 | Reliability guide | P0 | S-SET-06, S-ONB-12 | [DRIVE-158](https://thekeval.atlassian.net/browse/DRIVE-158) |
| F-SET-04 | Storage management | P1 | S-SET-07 | [DRIVE-161](https://thekeval.atlassian.net/browse/DRIVE-161) |
| F-SET-05 | About, legal and support | P0 | S-SET-08 | [DRIVE-163](https://thekeval.atlassian.net/browse/DRIVE-163) |
| F-SET-06 | Report a problem | P1 | S-SET-09 | [DRIVE-166](https://thekeval.atlassian.net/browse/DRIVE-166) |
| F-WEB-01 | Landing page with positioning A/B test | P0 | S-WEB-01, S-WEB-11 | [DRIVE-169](https://thekeval.atlassian.net/browse/DRIVE-169) |
| F-WEB-02 | Waitlist | P0 | S-WEB-01, S-WEB-02 | [DRIVE-171](https://thekeval.atlassian.net/browse/DRIVE-171) |
| F-WEB-03 | Legal pages | P0 | S-WEB-03, S-WEB-04 | [DRIVE-174](https://thekeval.atlassian.net/browse/DRIVE-174) |
| F-WEB-04 | Account deletion request page | P0 | S-WEB-07 | [DRIVE-177](https://thekeval.atlassian.net/browse/DRIVE-177) |
| F-WEB-05 | Support, FAQ and "How DrivePerf records" | P1 | S-WEB-05, S-WEB-06 | [DRIVE-179](https://thekeval.atlassian.net/browse/DRIVE-179) |
| F-WEB-06 | Ride-along landing and app links | P1 | S-WEB-08 | [DRIVE-181](https://thekeval.atlassian.net/browse/DRIVE-181) |
| F-WEB-07 | Email preferences and unsubscribe | P0 | S-WEB-09 | [DRIVE-183](https://thekeval.atlassian.net/browse/DRIVE-183) |
| F-WEB-08 | Press kit | P2 | S-WEB-10 | [DRIVE-188](https://thekeval.atlassian.net/browse/DRIVE-188) |
| F-ANA-01 | Product analytics and event taxonomy | P0 | S-ONB-13, S-PRV-07 | [DRIVE-190](https://thekeval.atlassian.net/browse/DRIVE-190) |
| F-ANA-02 | Capture-quality telemetry | P0 |  | [DRIVE-193](https://thekeval.atlassian.net/browse/DRIVE-193) |
| F-ANA-03 | Crash and ANR reporting | P0 | S-ONB-13, S-PRV-07 | [DRIVE-196](https://thekeval.atlassian.net/browse/DRIVE-196) |
| F-ANA-04 | Metrics dashboards | P1 |  | [DRIVE-198](https://thekeval.atlassian.net/browse/DRIVE-198) |
| F-ANA-05 | Remote config and kill switches | P1 |  | [DRIVE-201](https://thekeval.atlassian.net/browse/DRIVE-201) |

## Screens → stories

Use this table to find which stories each prototype screen's artifacts belong to.

| Screen | Name | Platforms | Stories |
|---|---|---|---|
| S-NAV-01 | App shell and tab bar (Drives, Routes, Insights, Garage, Profile — A6 to validate) | Both (iOS floating glass tab bar; Android navigation bar) | [DRIVE-126](https://thekeval.atlassian.net/browse/DRIVE-126), [DRIVE-154](https://thekeval.atlassian.net/browse/DRIVE-154), [DRIVE-173](https://thekeval.atlassian.net/browse/DRIVE-173), [DRIVE-202](https://thekeval.atlassian.net/browse/DRIVE-202), [DRIVE-141](https://thekeval.atlassian.net/browse/DRIVE-141) |
| S-ONB-01 | Welcome: "Smooth is a skill." | Both | [DRIVE-33](https://thekeval.atlassian.net/browse/DRIVE-33) |
| S-ONB-02 | Intro: "Every drive, decoded." (Story preview) | Both | [DRIVE-33](https://thekeval.atlassian.net/browse/DRIVE-33) |
| S-ONB-03 | Intro: Private by design + Pro Preview included | Both | [DRIVE-33](https://thekeval.atlassian.net/browse/DRIVE-33) |
| S-ONB-04 | Age (18+) and terms | Both | [DRIVE-38](https://thekeval.atlassian.net/browse/DRIVE-38) |
| S-ONB-05 | How capture works (honest promise) | Both (copy differs: force-quit vs force-stop, OEM note on Android) | [DRIVE-43](https://thekeval.atlassian.net/browse/DRIVE-43) |
| S-ONB-06 | Location primer (While Using) | Both | [DRIVE-49](https://thekeval.atlassian.net/browse/DRIVE-49) |
| S-ONB-07 | Motion & activity primer | Both (iOS "Motion & Fitness", Android "Physical activity") | [DRIVE-54](https://thekeval.atlassian.net/browse/DRIVE-54) |
| S-ONB-08 | Automatic trips opt-in + background-location disclosure | Both (Android version is the Play prominent disclosure) | [DRIVE-49](https://thekeval.atlassian.net/browse/DRIVE-49) |
| S-ONB-09 | "Allow all the time" settings step | **Android-only** (separate background-location step) | [DRIVE-49](https://thekeval.atlassian.net/browse/DRIVE-49) |
| S-ONB-10 | Notifications primer | Both (Android 13+ prompt; older Android skips) | [DRIVE-57](https://thekeval.atlassian.net/browse/DRIVE-57) |
| S-ONB-11 | Precise location sheet | Both (iOS precise toggle; Android approximate vs precise) | [DRIVE-49](https://thekeval.atlassian.net/browse/DRIVE-49) |
| S-ONB-12 | Battery and reliability step | **Android-only** | [DRIVE-65](https://thekeval.atlassian.net/browse/DRIVE-65), [DRIVE-158](https://thekeval.atlassian.net/browse/DRIVE-158) |
| S-ONB-13 | Help improve DrivePerf (usage statistics, crash reports) | Both | [DRIVE-70](https://thekeval.atlassian.net/browse/DRIVE-70), [DRIVE-75](https://thekeval.atlassian.net/browse/DRIVE-75), [DRIVE-190](https://thekeval.atlassian.net/browse/DRIVE-190), [DRIVE-196](https://thekeval.atlassian.net/browse/DRIVE-196) |
| S-ONB-14 | Add your car | Both | [DRIVE-74](https://thekeval.atlassian.net/browse/DRIVE-74), [DRIVE-173](https://thekeval.atlassian.net/browse/DRIVE-173) |
| S-ONB-15 | You're ready (quick-start surfaces) | Both (iOS: Control Center control + widget tip; Android: Quick Settings tile + widget tip) | [DRIVE-80](https://thekeval.atlassian.net/browse/DRIVE-80) |
| S-ONB-16 | Permission denied / manual-mode sheet | Both | [DRIVE-49](https://thekeval.atlassian.net/browse/DRIVE-49) |
| S-HOME-01 | Home (Drives tab): ready to record | Both | [DRIVE-85](https://thekeval.atlassian.net/browse/DRIVE-85), [DRIVE-90](https://thekeval.atlassian.net/browse/DRIVE-90), [DRIVE-109](https://thekeval.atlassian.net/browse/DRIVE-109), [DRIVE-150](https://thekeval.atlassian.net/browse/DRIVE-150), [DRIVE-156](https://thekeval.atlassian.net/browse/DRIVE-156), [DRIVE-92](https://thekeval.atlassian.net/browse/DRIVE-92) |
| S-HOME-02 | Capture status sheet | Both | [DRIVE-90](https://thekeval.atlassian.net/browse/DRIVE-90), [DRIVE-156](https://thekeval.atlassian.net/browse/DRIVE-156) |
| S-HOME-03 | Pause auto-detect sheet | Both | [DRIVE-152](https://thekeval.atlassian.net/browse/DRIVE-152) |
| S-DRV-01 | In-drive locked screen | Both | [DRIVE-85](https://thekeval.atlassian.net/browse/DRIVE-85), [DRIVE-90](https://thekeval.atlassian.net/browse/DRIVE-90), [DRIVE-115](https://thekeval.atlassian.net/browse/DRIVE-115), [DRIVE-120](https://thekeval.atlassian.net/browse/DRIVE-120) |
| S-DRV-02 | End trip sheet (Stop confirmation) | Both | [DRIVE-85](https://thekeval.atlassian.net/browse/DRIVE-85) |
| S-DRV-03 | "I'm a passenger" sheet | Both | [DRIVE-120](https://thekeval.atlassian.net/browse/DRIVE-120) |
| S-DRV-04 | "Decoding your drive" (processing) | Both | [DRIVE-85](https://thekeval.atlassian.net/browse/DRIVE-85), [DRIVE-95](https://thekeval.atlassian.net/browse/DRIVE-95), [DRIVE-182](https://thekeval.atlassian.net/browse/DRIVE-182) |
| S-DRV-05 | "Your Story will be ready when you've parked" | Both | [DRIVE-115](https://thekeval.atlassian.net/browse/DRIVE-115), [DRIVE-194](https://thekeval.atlassian.net/browse/DRIVE-194) |
| S-LIVE-01 | Live Activity, Lock Screen | **iOS-only** | [DRIVE-85](https://thekeval.atlassian.net/browse/DRIVE-85), [DRIVE-128](https://thekeval.atlassian.net/browse/DRIVE-128), [DRIVE-157](https://thekeval.atlassian.net/browse/DRIVE-157) |
| S-LIVE-02 | Live Activity, Dynamic Island | **iOS-only** | [DRIVE-85](https://thekeval.atlassian.net/browse/DRIVE-85), [DRIVE-128](https://thekeval.atlassian.net/browse/DRIVE-128) |
| S-LIVE-03 | Control Center control | **iOS-only** | [DRIVE-133](https://thekeval.atlassian.net/browse/DRIVE-133) |
| S-LIVE-04 | Lock Screen and Home Screen widgets | **iOS-only** (accessory circular/rectangular; small; medium) | [DRIVE-133](https://thekeval.atlassian.net/browse/DRIVE-133) |
| S-LIVE-05 | Live Update notification (promoted) | **Android-only** (Android 16+) | [DRIVE-85](https://thekeval.atlassian.net/browse/DRIVE-85), [DRIVE-142](https://thekeval.atlassian.net/browse/DRIVE-142) |
| S-LIVE-06 | Standard ongoing recording notification | **Android-only** | [DRIVE-85](https://thekeval.atlassian.net/browse/DRIVE-85), [DRIVE-137](https://thekeval.atlassian.net/browse/DRIVE-137), [DRIVE-142](https://thekeval.atlassian.net/browse/DRIVE-142) |
| S-LIVE-07 | Quick Settings tile | **Android-only** | [DRIVE-146](https://thekeval.atlassian.net/browse/DRIVE-146) |
| S-LIVE-08 | Home-screen widget (Glance) | **Android-only** | [DRIVE-146](https://thekeval.atlassian.net/browse/DRIVE-146) |
| S-LIVE-09 | Auto-trip "Recording your drive" notification | Both (iOS passive local notification; on Android this is the FGS notification) | [DRIVE-90](https://thekeval.atlassian.net/browse/DRIVE-90), [DRIVE-95](https://thekeval.atlassian.net/browse/DRIVE-95), [DRIVE-137](https://thekeval.atlassian.net/browse/DRIVE-137) |
| S-LIVE-10 | "Auto-detect may be paused" watchdog notification | Both | [DRIVE-150](https://thekeval.atlassian.net/browse/DRIVE-150) |
| S-STY-01 | Drive Story player (frames 1–7) | Both | [DRIVE-182](https://thekeval.atlassian.net/browse/DRIVE-182), [DRIVE-187](https://thekeval.atlassian.net/browse/DRIVE-187), [DRIVE-191](https://thekeval.atlassian.net/browse/DRIVE-191), [DRIVE-197](https://thekeval.atlassian.net/browse/DRIVE-197), [DRIVE-77](https://thekeval.atlassian.net/browse/DRIVE-77), [DRIVE-82](https://thekeval.atlassian.net/browse/DRIVE-82), [DRIVE-97](https://thekeval.atlassian.net/browse/DRIVE-97), [DRIVE-102](https://thekeval.atlassian.net/browse/DRIVE-102), [DRIVE-151](https://thekeval.atlassian.net/browse/DRIVE-151), [DRIVE-159](https://thekeval.atlassian.net/browse/DRIVE-159), [DRIVE-131](https://thekeval.atlassian.net/browse/DRIVE-131), [DRIVE-175](https://thekeval.atlassian.net/browse/DRIVE-175), [DRIVE-100](https://thekeval.atlassian.net/browse/DRIVE-100), [DRIVE-164](https://thekeval.atlassian.net/browse/DRIVE-164), [DRIVE-170](https://thekeval.atlassian.net/browse/DRIVE-170), [DRIVE-186](https://thekeval.atlassian.net/browse/DRIVE-186), [DRIVE-160](https://thekeval.atlassian.net/browse/DRIVE-160) |
| S-TRP-01 | Trip detail | Both | [DRIVE-55](https://thekeval.atlassian.net/browse/DRIVE-55), [DRIVE-100](https://thekeval.atlassian.net/browse/DRIVE-100), [DRIVE-106](https://thekeval.atlassian.net/browse/DRIVE-106), [DRIVE-109](https://thekeval.atlassian.net/browse/DRIVE-109), [DRIVE-157](https://thekeval.atlassian.net/browse/DRIVE-157), [DRIVE-170](https://thekeval.atlassian.net/browse/DRIVE-170), [DRIVE-172](https://thekeval.atlassian.net/browse/DRIVE-172), [DRIVE-139](https://thekeval.atlassian.net/browse/DRIVE-139), [DRIVE-77](https://thekeval.atlassian.net/browse/DRIVE-77), [DRIVE-93](https://thekeval.atlassian.net/browse/DRIVE-93), [DRIVE-102](https://thekeval.atlassian.net/browse/DRIVE-102), [DRIVE-117](https://thekeval.atlassian.net/browse/DRIVE-117), [DRIVE-186](https://thekeval.atlassian.net/browse/DRIVE-186), [DRIVE-60](https://thekeval.atlassian.net/browse/DRIVE-60), [DRIVE-200](https://thekeval.atlassian.net/browse/DRIVE-200) |
| S-TRP-02 | Edit trip sheet | Both | [DRIVE-131](https://thekeval.atlassian.net/browse/DRIVE-131), [DRIVE-175](https://thekeval.atlassian.net/browse/DRIVE-175), [DRIVE-100](https://thekeval.atlassian.net/browse/DRIVE-100), [DRIVE-149](https://thekeval.atlassian.net/browse/DRIVE-149) |
| S-TRP-03 | Delete trip confirmation | Both | [DRIVE-131](https://thekeval.atlassian.net/browse/DRIVE-131), [DRIVE-64](https://thekeval.atlassian.net/browse/DRIVE-64) |
| S-RPL-01 | Trip Replay | Both (MapKit / Android map SDK) | [DRIVE-200](https://thekeval.atlassian.net/browse/DRIVE-200), [DRIVE-203](https://thekeval.atlassian.net/browse/DRIVE-203), [DRIVE-34](https://thekeval.atlassian.net/browse/DRIVE-34), [DRIVE-61](https://thekeval.atlassian.net/browse/DRIVE-61), [DRIVE-106](https://thekeval.atlassian.net/browse/DRIVE-106), [DRIVE-164](https://thekeval.atlassian.net/browse/DRIVE-164), [DRIVE-167](https://thekeval.atlassian.net/browse/DRIVE-167), [DRIVE-170](https://thekeval.atlassian.net/browse/DRIVE-170), [DRIVE-93](https://thekeval.atlassian.net/browse/DRIVE-93) |
| S-RPL-02 | Event evidence sheet | Both | [DRIVE-39](https://thekeval.atlassian.net/browse/DRIVE-39), [DRIVE-93](https://thekeval.atlassian.net/browse/DRIVE-93), [DRIVE-97](https://thekeval.atlassian.net/browse/DRIVE-97), [DRIVE-102](https://thekeval.atlassian.net/browse/DRIVE-102), [DRIVE-172](https://thekeval.atlassian.net/browse/DRIVE-172) |
| S-RPL-03 | Stop and max-speed pin popover | Both | [DRIVE-203](https://thekeval.atlassian.net/browse/DRIVE-203) |
| S-RPL-04 | Replay layers sheet | Both | [DRIVE-71](https://thekeval.atlassian.net/browse/DRIVE-71) |
| S-ANL-01 | Analysis workspace (map + synced timeline + G-G) | Both | [DRIVE-45](https://thekeval.atlassian.net/browse/DRIVE-45), [DRIVE-50](https://thekeval.atlassian.net/browse/DRIVE-50), [DRIVE-34](https://thekeval.atlassian.net/browse/DRIVE-34), [DRIVE-61](https://thekeval.atlassian.net/browse/DRIVE-61), [DRIVE-167](https://thekeval.atlassian.net/browse/DRIVE-167), [DRIVE-170](https://thekeval.atlassian.net/browse/DRIVE-170), [DRIVE-172](https://thekeval.atlassian.net/browse/DRIVE-172) |
| S-ANL-02 | G-G circle, expanded | Both | [DRIVE-50](https://thekeval.atlassian.net/browse/DRIVE-50) |
| S-ANL-03 | Channel picker | Both | [DRIVE-45](https://thekeval.atlassian.net/browse/DRIVE-45), [DRIVE-66](https://thekeval.atlassian.net/browse/DRIVE-66) |
| S-ANL-04 | Range statistics sheet | Both | [DRIVE-66](https://thekeval.atlassian.net/browse/DRIVE-66) |
| S-DQ-01 | Data-quality detail sheet | Both | [DRIVE-160](https://thekeval.atlassian.net/browse/DRIVE-160), [DRIVE-164](https://thekeval.atlassian.net/browse/DRIVE-164), [DRIVE-167](https://thekeval.atlassian.net/browse/DRIVE-167), [DRIVE-170](https://thekeval.atlassian.net/browse/DRIVE-170), [DRIVE-172](https://thekeval.atlassian.net/browse/DRIVE-172), [DRIVE-106](https://thekeval.atlassian.net/browse/DRIVE-106), [DRIVE-61](https://thekeval.atlassian.net/browse/DRIVE-61) |
| S-DQ-02 | Phone placement guide | Both | [DRIVE-176](https://thekeval.atlassian.net/browse/DRIVE-176) |
| S-SCO-01 | Smoothness breakdown | Both | [DRIVE-77](https://thekeval.atlassian.net/browse/DRIVE-77), [DRIVE-82](https://thekeval.atlassian.net/browse/DRIVE-82), [DRIVE-87](https://thekeval.atlassian.net/browse/DRIVE-87) |
| S-SCO-02 | How scores work | Both | [DRIVE-113](https://thekeval.atlassian.net/browse/DRIVE-113), [DRIVE-108](https://thekeval.atlassian.net/browse/DRIVE-108), [DRIVE-172](https://thekeval.atlassian.net/browse/DRIVE-172) |
| S-SCO-03 | "Was it needed?" sheet | Both | [DRIVE-97](https://thekeval.atlassian.net/browse/DRIVE-97), [DRIVE-39](https://thekeval.atlassian.net/browse/DRIVE-39) |
| S-SCO-04 | Coaching tip detail | Both | [DRIVE-102](https://thekeval.atlassian.net/browse/DRIVE-102), [DRIVE-39](https://thekeval.atlassian.net/browse/DRIVE-39) |
| S-SCO-05 | Weekly focus detail | Both | [DRIVE-124](https://thekeval.atlassian.net/browse/DRIVE-124) |
| S-SCO-06 | Efficiency habits detail | Both | [DRIVE-117](https://thekeval.atlassian.net/browse/DRIVE-117) |
| S-HIS-01 | Drives list | Both | [DRIVE-126](https://thekeval.atlassian.net/browse/DRIVE-126), [DRIVE-139](https://thekeval.atlassian.net/browse/DRIVE-139) |
| S-HIS-02 | Filter sheet | Both | [DRIVE-126](https://thekeval.atlassian.net/browse/DRIVE-126) |
| S-STA-01 | Insights (weekly stats) | Both | [DRIVE-141](https://thekeval.atlassian.net/browse/DRIVE-141), [DRIVE-145](https://thekeval.atlassian.net/browse/DRIVE-145), [DRIVE-82](https://thekeval.atlassian.net/browse/DRIVE-82) |
| S-STA-02 | Trend detail | Both | [DRIVE-145](https://thekeval.atlassian.net/browse/DRIVE-145) |
| S-RTE-01 | Routes list | Both | [DRIVE-154](https://thekeval.atlassian.net/browse/DRIVE-154), [DRIVE-165](https://thekeval.atlassian.net/browse/DRIVE-165) |
| S-RTE-02 | Route detail | Both | [DRIVE-154](https://thekeval.atlassian.net/browse/DRIVE-154), [DRIVE-159](https://thekeval.atlassian.net/browse/DRIVE-159) |
| S-RTE-03 | Name this route sheet | Both | [DRIVE-151](https://thekeval.atlassian.net/browse/DRIVE-151) |
| S-RTE-04 | Ghost comparison | Both | [DRIVE-162](https://thekeval.atlassian.net/browse/DRIVE-162) |
| S-RTE-05 | Route management sheet | Both | [DRIVE-165](https://thekeval.atlassian.net/browse/DRIVE-165), [DRIVE-168](https://thekeval.atlassian.net/browse/DRIVE-168) |
| S-GAR-01 | Garage | Both | [DRIVE-173](https://thekeval.atlassian.net/browse/DRIVE-173), [DRIVE-175](https://thekeval.atlassian.net/browse/DRIVE-175) |
| S-GAR-02 | Car detail | Both | [DRIVE-173](https://thekeval.atlassian.net/browse/DRIVE-173), [DRIVE-180](https://thekeval.atlassian.net/browse/DRIVE-180) |
| S-GAR-03 | Add / edit car | Both | [DRIVE-173](https://thekeval.atlassian.net/browse/DRIVE-173), [DRIVE-74](https://thekeval.atlassian.net/browse/DRIVE-74), [DRIVE-185](https://thekeval.atlassian.net/browse/DRIVE-185) |
| S-SHR-01 | Share card builder | Both (system share sheet) | [DRIVE-186](https://thekeval.atlassian.net/browse/DRIVE-186), [DRIVE-189](https://thekeval.atlassian.net/browse/DRIVE-189), [DRIVE-192](https://thekeval.atlassian.net/browse/DRIVE-192), [DRIVE-195](https://thekeval.atlassian.net/browse/DRIVE-195), [DRIVE-81](https://thekeval.atlassian.net/browse/DRIVE-81), [DRIVE-140](https://thekeval.atlassian.net/browse/DRIVE-140), [DRIVE-144](https://thekeval.atlassian.net/browse/DRIVE-144) |
| S-SHR-02 | Map card warning sheet | Both | [DRIVE-192](https://thekeval.atlassian.net/browse/DRIVE-192) |
| S-SHR-03 | Ride-along pass invite | Both | [DRIVE-199](https://thekeval.atlassian.net/browse/DRIVE-199) |
| S-SHR-04 | Ride-along welcome (redeemed) | Both | [DRIVE-199](https://thekeval.atlassian.net/browse/DRIVE-199) |
| S-ACC-01 | Profile tab | Both | [DRIVE-202](https://thekeval.atlassian.net/browse/DRIVE-202) |
| S-ACC-02 | Sign-in sheet | Both (Sign in with Apple native on iOS, web flow on Android) | [DRIVE-204](https://thekeval.atlassian.net/browse/DRIVE-204) |
| S-ACC-03 | Check your email (email link) | Both | [DRIVE-204](https://thekeval.atlassian.net/browse/DRIVE-204) |
| S-ACC-04 | Cloud backup settings | Both | [DRIVE-205](https://thekeval.atlassian.net/browse/DRIVE-205) |
| S-ACC-05 | Backup consent | Both | [DRIVE-205](https://thekeval.atlassian.net/browse/DRIVE-205) |
| S-ACC-06 | Restore from backup | Both | [DRIVE-37](https://thekeval.atlassian.net/browse/DRIVE-37) |
| S-ACC-07 | Sign out sheet | Both | [DRIVE-41](https://thekeval.atlassian.net/browse/DRIVE-41) |
| S-PRV-01 | Privacy Centre | Both | [DRIVE-47](https://thekeval.atlassian.net/browse/DRIVE-47) |
| S-PRV-02 | Privacy zones | Both | [DRIVE-81](https://thekeval.atlassian.net/browse/DRIVE-81), [DRIVE-189](https://thekeval.atlassian.net/browse/DRIVE-189) |
| S-PRV-03 | Download all my data | Both (iOS Files/share sheet; Android Storage Access Framework) | [DRIVE-53](https://thekeval.atlassian.net/browse/DRIVE-53) |
| S-PRV-04 | Trip export (GPX/CSV) | Both | [DRIVE-60](https://thekeval.atlassian.net/browse/DRIVE-60), [DRIVE-172](https://thekeval.atlassian.net/browse/DRIVE-172) |
| S-PRV-05 | Delete data options | Both | [DRIVE-64](https://thekeval.atlassian.net/browse/DRIVE-64) |
| S-PRV-06 | Delete account flow | Both | [DRIVE-69](https://thekeval.atlassian.net/browse/DRIVE-69) |
| S-PRV-07 | Analytics and diagnostics | Both | [DRIVE-75](https://thekeval.atlassian.net/browse/DRIVE-75), [DRIVE-70](https://thekeval.atlassian.net/browse/DRIVE-70), [DRIVE-190](https://thekeval.atlassian.net/browse/DRIVE-190), [DRIVE-196](https://thekeval.atlassian.net/browse/DRIVE-196) |
| S-SUB-01 | Pro Preview status sheet | Both | [DRIVE-92](https://thekeval.atlassian.net/browse/DRIVE-92) |
| S-SUB-02 | Day-15 "Your first two weeks, decoded" | Both | [DRIVE-96](https://thekeval.atlassian.net/browse/DRIVE-96), [DRIVE-105](https://thekeval.atlassian.net/browse/DRIVE-105), [DRIVE-121](https://thekeval.atlassian.net/browse/DRIVE-121) |
| S-SUB-03 | Contextual lock sheet | Both | [DRIVE-101](https://thekeval.atlassian.net/browse/DRIVE-101), [DRIVE-139](https://thekeval.atlassian.net/browse/DRIVE-139), [DRIVE-162](https://thekeval.atlassian.net/browse/DRIVE-162), [DRIVE-165](https://thekeval.atlassian.net/browse/DRIVE-165), [DRIVE-205](https://thekeval.atlassian.net/browse/DRIVE-205), [DRIVE-200](https://thekeval.atlassian.net/browse/DRIVE-200), [DRIVE-121](https://thekeval.atlassian.net/browse/DRIVE-121) |
| S-SUB-04 | Plans (paywall) | Both (StoreKit / Play Billing sheets mocked) | [DRIVE-86](https://thekeval.atlassian.net/browse/DRIVE-86), [DRIVE-105](https://thekeval.atlassian.net/browse/DRIVE-105), [DRIVE-121](https://thekeval.atlassian.net/browse/DRIVE-121), [DRIVE-96](https://thekeval.atlassian.net/browse/DRIVE-96) |
| S-SUB-05 | Welcome to Pro | Both | [DRIVE-86](https://thekeval.atlassian.net/browse/DRIVE-86) |
| S-SUB-06 | Manage subscription | Both | [DRIVE-111](https://thekeval.atlassian.net/browse/DRIVE-111) |
| S-SUB-07 | Preview ended / "You're on Free" | Both | [DRIVE-96](https://thekeval.atlassian.net/browse/DRIVE-96), [DRIVE-116](https://thekeval.atlassian.net/browse/DRIVE-116) |
| S-NTF-01 | Notification catalogue (all templates) | Both (iOS notification; Android channel notification) | [DRIVE-125](https://thekeval.atlassian.net/browse/DRIVE-125), [DRIVE-130](https://thekeval.atlassian.net/browse/DRIVE-130), [DRIVE-135](https://thekeval.atlassian.net/browse/DRIVE-135), [DRIVE-140](https://thekeval.atlassian.net/browse/DRIVE-140), [DRIVE-148](https://thekeval.atlassian.net/browse/DRIVE-148), [DRIVE-184](https://thekeval.atlassian.net/browse/DRIVE-184), [DRIVE-151](https://thekeval.atlassian.net/browse/DRIVE-151), [DRIVE-159](https://thekeval.atlassian.net/browse/DRIVE-159) |
| S-NTF-02 | Missed-trip check sheet | Both | [DRIVE-178](https://thekeval.atlassian.net/browse/DRIVE-178) |
| S-RCP-01 | Weekly recap player | Both | [DRIVE-135](https://thekeval.atlassian.net/browse/DRIVE-135), [DRIVE-124](https://thekeval.atlassian.net/browse/DRIVE-124), [DRIVE-195](https://thekeval.atlassian.net/browse/DRIVE-195), [DRIVE-197](https://thekeval.atlassian.net/browse/DRIVE-197) |
| S-WRP-01 | Monthly Wrapped player | Both | [DRIVE-140](https://thekeval.atlassian.net/browse/DRIVE-140), [DRIVE-195](https://thekeval.atlassian.net/browse/DRIVE-195), [DRIVE-197](https://thekeval.atlassian.net/browse/DRIVE-197) |
| S-WRP-02 | Annual Drive Wrapped player | Both | [DRIVE-144](https://thekeval.atlassian.net/browse/DRIVE-144), [DRIVE-195](https://thekeval.atlassian.net/browse/DRIVE-195), [DRIVE-197](https://thekeval.atlassian.net/browse/DRIVE-197) |
| S-SET-01 | Settings | Both | [DRIVE-153](https://thekeval.atlassian.net/browse/DRIVE-153) |
| S-SET-02 | Units and display | Both | [DRIVE-153](https://thekeval.atlassian.net/browse/DRIVE-153) |
| S-SET-03 | Recording settings | Both | [DRIVE-153](https://thekeval.atlassian.net/browse/DRIVE-153), [DRIVE-155](https://thekeval.atlassian.net/browse/DRIVE-155) |
| S-SET-04 | Notifications and quiet hours | Both | [DRIVE-125](https://thekeval.atlassian.net/browse/DRIVE-125) |
| S-SET-05 | Health Check | Both (iOS checks: Always, Background App Refresh, LPM, Live Activities; Android checks: all-the-time location, battery optimisation, OEM sleep) | [DRIVE-156](https://thekeval.atlassian.net/browse/DRIVE-156), [DRIVE-150](https://thekeval.atlassian.net/browse/DRIVE-150), [DRIVE-155](https://thekeval.atlassian.net/browse/DRIVE-155), [DRIVE-178](https://thekeval.atlassian.net/browse/DRIVE-178), [DRIVE-130](https://thekeval.atlassian.net/browse/DRIVE-130) |
| S-SET-06 | Reliability guide | Both (Android OEM pages; iOS BAR/LPM page) | [DRIVE-158](https://thekeval.atlassian.net/browse/DRIVE-158), [DRIVE-65](https://thekeval.atlassian.net/browse/DRIVE-65) |
| S-SET-07 | Storage | Both | [DRIVE-161](https://thekeval.atlassian.net/browse/DRIVE-161) |
| S-SET-08 | About, legal and support | Both | [DRIVE-163](https://thekeval.atlassian.net/browse/DRIVE-163) |
| S-SET-09 | Report a problem | Both | [DRIVE-166](https://thekeval.atlassian.net/browse/DRIVE-166) |
| S-WEB-01 | Home (landing + waitlist form) | Web | [DRIVE-169](https://thekeval.atlassian.net/browse/DRIVE-169), [DRIVE-171](https://thekeval.atlassian.net/browse/DRIVE-171) |
| S-WEB-02 | Waitlist confirmation | Web | [DRIVE-171](https://thekeval.atlassian.net/browse/DRIVE-171) |
| S-WEB-03 | Privacy policy | Web | [DRIVE-174](https://thekeval.atlassian.net/browse/DRIVE-174) |
| S-WEB-04 | Terms of use | Web | [DRIVE-174](https://thekeval.atlassian.net/browse/DRIVE-174) |
| S-WEB-05 | Support and FAQ | Web | [DRIVE-179](https://thekeval.atlassian.net/browse/DRIVE-179) |
| S-WEB-06 | How DrivePerf records (capture and battery explainer) | Web | [DRIVE-179](https://thekeval.atlassian.net/browse/DRIVE-179), [DRIVE-43](https://thekeval.atlassian.net/browse/DRIVE-43) |
| S-WEB-07 | Account deletion request | Web | [DRIVE-177](https://thekeval.atlassian.net/browse/DRIVE-177), [DRIVE-69](https://thekeval.atlassian.net/browse/DRIVE-69) |
| S-WEB-08 | Ride-along landing | Web | [DRIVE-181](https://thekeval.atlassian.net/browse/DRIVE-181), [DRIVE-199](https://thekeval.atlassian.net/browse/DRIVE-199) |
| S-WEB-09 | Email preferences / unsubscribe | Web | [DRIVE-183](https://thekeval.atlassian.net/browse/DRIVE-183) |
| S-WEB-10 | Press kit | Web | [DRIVE-188](https://thekeval.atlassian.net/browse/DRIVE-188) |
| S-WEB-11 | 404 | Web | [DRIVE-169](https://thekeval.atlassian.net/browse/DRIVE-169) |
