# DrivePerf — expanded Android indie-app evidence

Research date: **17 September 2026**. This appendix expands the first research pass around the founder's clarified audiences: **driving enthusiasts who want rich performance data**, and **everyday/new drivers who want efficiency and coaching**. Insurance products remain feature and metric benchmarks, not target positioning. No iPhone evaluation was performed.

**The changed conclusion:** rich telemetry, local storage, attractive maps, trip editing and even AI-branded coaching already appear in independent Android products. DrivePerf needs to validate a better combination of measurement quality, understandable feedback, useful depth and privacy. A new visual style alone is insufficient differentiation.

## Evidence boundaries

- Public Play listings establish discoverability, not successful installation or onboarding for a Canadian account/device. Availability and prices must be checked on target devices before a product decision.
- This is desk research, without installed-app testing, subscription purchases or accuracy measurements. Feature descriptions are developer claims. Missing public evidence means **unverified**, not **absent**.
- Dates, ratings and feature descriptions can differ between cached/localized Play pages. The newer discoverable description is identified where material; reviews can predate it.
- Review examples are selected anecdotes, not a representative sample, defect rate or proof that a problem remains unresolved. No store reviews were invented where Google Play did not expose them.
- “Indie” is used as a discovery category for smaller consumer products, not an audited statement about ownership or funding. Zenroad and AutoBeacon are telematics-vendor showcases/products and belong in a separate supplier benchmark group.

## 1. Requested products and exact identities

| Requested name | Verified Android identity | Role, access and pricing evidence |
|---|---|---|
| **DrivingTrackerApp.com** | **Driving Tracker: GPS Trip Log**; developer DrivingTracker / Richard Bláha; `com.richardblaha.drivingtracker`. [Official site](https://drivingtrackerapp.com/), [Play](https://play.google.com/store/apps/details?hl=en_US&id=com.richardblaha.drivingtracker) | Direct enthusiast/data comparator. Public listing; free base, monthly/annual Premium and seven-day trial. Exact Canadian price unverified. |
| **OpenRoadApp** — likely intended match | **Open Road: Drive Tracker**; OpenRoad.App / Rasputin Carlson Phillips; policy identifies OpenRoad LLC; `com.mikita.openroad`; **openroaddrive.com**. [Play](https://play.google.com/store/apps/details?hl=en&id=com.mikita.openroad), [site](https://www.openroaddrive.com/) | Consumer enthusiast/social comparator. Free base; optional Pro subscription. Exact Android/Canadian price and gates unverified. [Subscription terms](https://www.openroaddrive.com/terms) |
| **Zenroad** | Damoov / DAMOOV PTE. LTD.; `com.telematicssdk.zenroad`. [Play](https://play.google.com/store/apps/details?id=com.telematicssdk.zenroad) | Free SDK reference app with public listing, already covered in the original evidence. Open app source does not itself settle commercial backend/SDK terms. [Product](https://damoov.com/telematics-app/) |
| **AutoBeacon** | SenSight Technologies / SENSIGHT TECHNOLOGIES PRIVATE LIMITED; `com.sensight.beaconapp`; **autowiz.in**. [Play](https://play.google.com/store/apps/details?hl=en&id=com.sensight.beaconapp) | Public app plus enterprise/SDK offering. Registration flow is documented; independent Canadian signup and consumer pricing remain unverified. Phone-only mode exists; branded BLE beacon optional. [Safety product](https://www.autowiz.in/roadsafety.html) |

**Name collisions:** CMT's **Openroad: Safety on the Road**, `com.gotruemotion.openroad`, is a different product centered on crash response and family safety, with a public listing updated 13 May 2026. It is not the OpenRoad.App enthusiast app. [CMT Play listing](https://play.google.com/store/apps/details?hl=en_US&id=com.gotruemotion.openroad). **DriveTracker** by iAlex5 (`com.ialex5.driveTracker`) and **DriveTrack** by Franko Prifti (`io.github.frankoprifti.drivetrack`) are also distinct from DrivingTrackerApp.com; see the adjacent-app table below. No definitive match to the literal domain `openroadapp.com` was established; retain both Openroad identities until the founder's installed package is known.

## 2. What the four requested products actually add

### Driving Tracker: GPS Trip Log

Its Play listing claims GPS, accelerometer, gyroscope, magnetometer and barometer capture; speed/altitude graphs; longitudinal/lateral driving forces and acceleration, braking and cornering events. It supports route coloring, period analytics, notes, categories, filters, pause/resume and trimming. Premium adds auto detection, heatmaps, ZIP/GPX transfer and unlimited categories. The claim of accuracy regardless of mounting needs controlled testing. The inspected English listing was updated 17 July 2026. [Play](https://play.google.com/store/apps/details?hl=en_US&id=com.richardblaha.drivingtracker)

The website promotes no-account manual recording and local trip storage. This is a strong direct benchmark for the “data nerd” audience rather than just a mileage logger. [Product](https://drivingtrackerapp.com/)

**Privacy detail:** the 7 May 2026 policy separates locally stored drive data from analytics, crash reporting and subscription processing through AptaBase, TelemetryDeck, PostHog and RevenueCat. Therefore “local trips” should not be paraphrased as “no network data of any kind.” Its manual-start-only policy wording does not fully explain the subsequently advertised automatic mode or expanded inertial collection; ask for clarification during hands-on research. The policy allows deleting local trips; the Play Data safety panel says data cannot be deleted, a distinction requiring explanation for service-provider data. [Policy](https://drivingtrackerapp.com/privacy-policy.html), [Play](https://play.google.com/store/apps/details?hl=en_US&id=com.richardblaha.drivingtracker)

**DrivePerf hypothesis:** compete on calibrated interpretation, selectable metric depth and repeatable comparisons. Raw sensor graphs, exports, notes and trip trimming are established features. Phone-distraction analysis, fuel measurement and a structured learner curriculum were not verified for this product.

### Open Road: Drive Tracker

The newer English Play page, updated **29 August 2026**, emphasizes HUD, replay/history, garage trends and smoothness, multi-vehicle records, convoys with voice, segments, sharing and an XP/mileage leaderboard. The older US cache still describes speed contests and Elo. Do not represent that older description as a synchronized current feature matrix. [Newer listing](https://play.google.com/store/apps/details?hl=en&id=com.mikita.openroad), [older US listing/reviews](https://play.google.com/store/apps/details?hl=en_US&id=com.mikita.openroad)

Its official site markets automatic recording and a strong social presentation. That already overlaps a modern enthusiast experience. The public subscription terms confirm Pro, but do not establish current Canadian Android pricing. [Site](https://www.openroaddrive.com/), [Terms](https://www.openroaddrive.com/terms)

**Privacy detail, policy updated 12 September 2026:** GPS, speed, heading, altitude and optional accelerometer/gyroscope data are recorded. Signed-in trips sync by default, including an **untrimmed raw-reading file** privately accessible to the account; sharing/heatmaps use trimmed routes. Android nearby-presence onboarding defaults to Anonymous, which broadcasts **exact location without identity** to nearby signed-in users; Hidden disables it. No-sale commitments and local-first recording therefore do not mean local-only storage or hidden live presence. The policy also describes on-device activity recognition for automatic recording. [Privacy policy](https://www.openroaddrive.com/privacy)

**DrivePerf hypothesis:** recording, backup, publication and nearby visibility should be separate, explicit choices. A saved private drive should not silently become public location presence. Modern styling, replay and share cards are competitive requirements to evaluate, not an unoccupied market position. No independently validated coaching effectiveness was established.

### Zenroad

Treat it as a working reference for Damoov's platform, not proof that a standalone consumer business succeeds. The product documents five behavior categories—acceleration, braking, cornering, speeding, distraction—plus safety/eco scores, tagging, automatic/manual/Bluetooth-triggered recording, configurable thresholds and data exports. The Android reference lists optional Bluetooth OBD connectivity. Supported platform configurations may differ from the public demo's enabled features. [Product and FAQ](https://damoov.com/telematics-app/)

Play confirms automatic background tracking, server processing and no mandatory vehicle device; the inspected update was 22 July 2026. It declares location/personal-data collection, device-ID sharing, transit encryption and deletion requests. [Play](https://play.google.com/store/apps/details?id=com.telematicssdk.zenroad)

**DrivePerf hypothesis:** event counts should link to the actual sample, confidence and user explanation, with distinct road/coaching goals. Configurable thresholds and eco scoring already exist; their understandable, validated use is the opportunity. Its public demo reviews are useful test cases, not an evaluation of every Damoov customer deployment.

### AutoBeacon

Play describes automatic phone-only recording using GPS, accelerometer and gyroscope; optional vendor BLE hardware; five behavior sub-scores; score out of 100; mapped event severity, trip history and weekly trends. It also advertises crash detection with a cancelable countdown. The English listing observed an update of 4 September 2026. [Play](https://play.google.com/store/apps/details?hl=en&id=com.sensight.beaconapp)

The employer-facing product adds eco scoring, contextual coaching content, driver/passenger and work/personal correction, Bluetooth vehicle association and voice alerts. These are supplier claims; verify which are enabled in an independent consumer installation. The optional beacon contains its own accelerometer; it is not merely a GPS tag. [Safety product](https://www.autowiz.in/roadsafety.html), [Hardware/SDK product](https://www.autowiz.in/autobeacon.html)

**Privacy and operating model:** the AutoBeacon-specific section of the 1 October 2023 policy collects registration/vehicle details and location/motion data; describes both local and India-based cloud storage; requires background location and physical activity access; and routes deletion requests by email. The FAQ says recording can work without mobile data and upload later. “In-app processing” therefore does not mean no cloud storage. [Policy, AutoBeacon section](https://www.autowiz.in/privacy.html), [FAQ](https://www.autowiz.in/faq_autobeacon_safety.html)

**DrivePerf hypothesis:** explain severity, trip inclusion and model context as carefully as recording status. Supplier battery claims should become measured test requirements: its FAQ gives about 10% for four hours on a 3,000 mAh phone, while product material gives less than 5% typical daily consumption under unspecified usage; neither is a controlled comparison. [FAQ](https://www.autowiz.in/faq_autobeacon_safety.html), [Product](https://www.autowiz.in/roadsafety.html)

## 3. Additional relevant Android products discovered

This is an expanded candidate set, **not every app in the market**. Each linked package had a public Play listing. Small install bands and recent listings indicate limited observable adoption, not poor quality. Advertised prices are not verified Canadian checkout prices.

| Product / developer / package | Relevant claims and business model | Why benchmark it / unresolved question |
|---|---|---|
| **PACE – Drive with friends**; Emanuel Berger; `com.bergeritsolutions.pace` | GPS trips/history, insights, groups/live positions, XP; ads plus Pro. Updated 26 Aug 2026, 500+ downloads. [Play](https://play.google.com/store/apps/details?id=com.bergeritsolutions.pace) | Site advertises rating, replay, privacy zones, AI analysis and track/drift mode; €3.99/month or €29.99/year. Confirm Android parity and region. Its comparisons with rivals are vendor marketing, not independent evidence. [Site/FAQ](https://pace-mobile.com/) |
| **TripRank: GPS Driving Tracker**; 10xeon / Muhammad Ahmad; `com.trippulse.triprank` | G-force/harsh events, score 0–100, AI insights, colored replay, background recording; purchases. Updated 10 Sep 2026, 1K+. [Play](https://play.google.com/store/apps/details?id=com.trippulse.triprank) | Direct proposed-audience overlap. Accuracy, coaching value, exact price and safeguards around advertised live challenges require testing. |
| **DOWNFORCE**; MDA Luca / MDA – MAGIC DESIGN AGENTS UG; `de.downforce.app` | Auto trips, acceleration times, quarter-mile, G-forces, aggression score, garage, exports and weekly AI coach. Published Pro €9/month or €79/year; Full €19/month or €159/year, seven-day trial. Updated 20 Aug 2026. [Play](https://play.google.com/store/apps/details?id=de.downforce.app) | Clear enthusiast segmentation and a higher pricing anchor; test whether analytics and coaching justify the gates. An “aggression” ranking is not a road-safety score. |
| **Zero Lap: GPS Speedometer OBD2**; Zoran Gojkovic; `com.fenix.zerolap` | Performance/lap timing, G-force, eco score, comparison, CSV/GPX/KML/PDF; external Bluetooth GPS and ELM327 OBD options. One-time Pro advertised. Updated 7 Sep 2026, 500+. [Play](https://play.google.com/store/apps/details?id=com.fenix.zerolap) | Strong phone-to-hardware expansion reference. Simultaneous “local/private” and Pro cloud-backup claims need precise separation; no measured accuracy established. |
| **OpenRank – Test your limit**; MrNiato Production / Guillaume Leleu; `fr.autosuivi.openrank` | GPS, 0–100 timing, G-force, smoothness/eco score, altitude, garage and community. Claims free without ads/purchases; manual-start GPS and French servers. Updated 29 Aug 2026, 50+. [Play](https://play.google.com/store/apps/details?hl=en-US&id=fr.autosuivi.openrank) | A free alternative with much of the proposed feature vocabulary; validate signal accuracy and commercial durability separately. |
| **DriveTracker**; iAlex5 / Alessandro Barbieri; `com.ialex5.driveTracker` | Route, speed charts, trip statistics, time comparisons, local history and offline GPS; contains ads. Updated 7 Aug 2026, 100+. [Play](https://play.google.com/store/apps/details?hl=en_US&id=com.ialex5.driveTracker) | Lightweight analytics/UI baseline. No verified harsh-event score or structured coaching in inspected description. |
| **DriveTrack**; Franko Prifti; `io.github.frankoprifti.drivetrack` | Automatic background routes/playback, stats, local storage and cloud backup; contains ads. Updated 18 Aug 2026. [Play](https://play.google.com/store/apps/details?hl=en_AU&id=io.github.frankoprifti.drivetrack) | Test hands-free capture and recovery; clarify cloud/local distinction. No verified full behavior-scoring model. |
| **DriveSidekick: UK Test Routes**; DriveSidekick / Arpit Mehra; `com.drivesidekick.app` | UK practice routes, syllabus progress, score and event replay. Free during beta; 100+ downloads, 4 Aug 2026 update. [Play](https://play.google.com/store/apps/details?hl=en&id=com.drivesidekick.app) | Already in the original report, now especially relevant to the learner audience. UK curriculum cannot be presumed applicable to Ontario; sensor score does not prove full examiner competence. |

For these smaller products, no usable dated Android review text surfaced in the inspected primary listings. This absence limits consumer-evidence depth and makes installed benchmarking more important. Broad phone-based performance claims must not be interpreted as equivalence to dedicated high-frequency GPS or vehicle-bus telemetry.

## 4. Public Android review examples and limits

All text below is paraphrased. Exact review permalinks were not surfaced; the listing, reviewer and date identify each example.

| Product / reviewer / date | Reported experience and research implication |
|---|---|
| Open Road — **Ethan Rapa-Marley**, 5 Apr 2026 | Liked features/no ads; reported motorway speed severely under-recorded. Developer asked whether phone or Android Auto was used. Test both capture paths. [Play](https://play.google.com/store/apps/details?hl=en_US&id=com.mikita.openroad) |
| Open Road — **Einoras M.**, 11 Jul 2026 | Reported segment-triggered crashes on 1.4.7. Predates the newer August listing; current reproducibility unknown. [Play](https://play.google.com/store/apps/details?hl=en_US&id=com.mikita.openroad) |
| Open Road — **Ernie St. George**, 18 Aug 2026 | Reported repeated crashes after two good weeks. Test long-running reliability/recovery. [Play](https://play.google.com/store/apps/details?hl=en_US&id=com.mikita.openroad) |
| Zenroad — **Jose Sanchez**, 8 Jun 2026 | Felt acceleration/braking expectations ignored necessary maneuvers. Test context explanations, not only threshold tuning. [Play](https://play.google.com/store/apps/details?id=com.telematicssdk.zenroad) |
| Zenroad — **Rehman R.**, 18 Jan 2026 | Valued visualization/mileage and perceived behavior detection as accurate. Positive experience, not measured accuracy. [Play](https://play.google.com/store/apps/details?id=com.telematicssdk.zenroad) |
| CMT Openroad — **Derek Matuszewski**, 28 Mar 2026 | Reported battery burden and wanted more alert controls. Different app from OpenRoad.App. [Play](https://play.google.com/store/apps/details?hl=en_US&id=com.gotruemotion.openroad) |
| CMT Openroad — **Kullawat Jaemsirikarn**, 27 Jun 2026 | Welcomed an operator call after phone fell during a turn. Demonstrates response experience, while also raising a phone-drop false-trigger test case. [Play](https://play.google.com/store/apps/details?hl=en_US&id=com.gotruemotion.openroad) |

**DrivingTracker review gap:** its site republishes positive Google Play testimonials attributed to C. M. G. (January 2026), A. I. (December 2025), Y. A. (February 2026), and M. M. (May 2026). They emphasize simplicity, polish and auto/export value. These are **vendor-selected, abbreviated testimonials**, not independently retrieved full Play reviews, and are not added to the verified review sample. [Site](https://drivingtrackerapp.com/)

**AutoBeacon review gap:** a third-party index surfaced complaints, but they were not verifiable in the inspected Play pages. They are excluded from the primary review evidence rather than treated as confirmed current defects.

## 5. Revised opportunities to validate

These are product hypotheses arising from the expanded landscape, not claims of unique features or an approved POC scope.

1. **Two views of one drive.** A concise coach summary for the everyday driver; expandable plots, distributions, event samples and exports for enthusiasts. Benchmark whether users can move between them without two disconnected products.
2. **Context-specific interpretation.** Keep everyday smoothness, efficiency indicators and closed-course performance analysis separate. A strong braking event can be necessary; a lap-time improvement does not imply a safer city drive. Avoid aggregating them into one unexplained “skill” number.
3. **Measured versus inferred metrics.** Label GPS speed and observed acceleration separately from inferred driver role, handling, eco scores and estimated fuel. Report sensor coverage, calibration, uncertainty and missing segments; measure whether these explanations increase trust.
4. **A coach that changes a subsequent drive.** After a trip, select one evidence-based practice goal and compare a later, similar route or context. Existing AI labels and generic tips make repeatable improvement, not the presence of an AI feature, the research question.
5. **Trustworthy capture and recovery.** Test automatic and explicit manual sessions, passenger/train cases, mounting changes, weak GNSS, Android Auto, phone calls, battery restrictions, long trips and crash recovery. Preserve incomplete trips with clear confidence instead of presenting a misleading complete score.
6. **Privacy users can predict.** Separate recording, local retention, backup, analytics, exports and public sharing. Default any future public presence to hidden; preview the exact route and data before sharing. Local-only storage and cloud restoration solve different user needs.
7. **Price against real alternatives.** Compare free, one-time purchase and subscription appetite within each audience. Charging for graphs or a generic score needs testing when smaller competitors already provide similar concepts at low or zero entry price.

**Suggested hands-on shortlist:** DrivingTracker, Open Road, Zenroad, AutoBeacon, DriveSidekick, and one bridge competitor such as Zero Lap or TripRank. Begin with the same Android devices, the same ordinary routes and a fixed evaluation sheet; add lawful closed-course sessions only when studying track analytics. Record app version, signup eligibility, Canadian checkout price, exact permissions, UI screenshots, storage/export behavior, processing latency, missing-trip rate and battery use. This provides a manageable next research phase before specifications.
