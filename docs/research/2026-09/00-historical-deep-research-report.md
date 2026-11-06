> Historical research, rebranded for project continuity. The current two-audience direction and revised Android comparison are in [the updated report](01-android-market-research.md). Architecture, score weights and insurance emphasis below are not approved product specifications.

# DrivePerf research and implementation plan

## Executive summary

TD MyAdvantage is not a standalone Android app anymore; it is a usage-based insurance module inside the broader TD Insurance mobile app. Public TD materials show a very clear product shape: enrolment tied to a TD auto policy, activation from the in-app dashboard, always-on background operation once permissions are granted, per-trip review and deletion, a projected premium view after enough trips, and a scoring model driven by driving events such as braking, acceleration, cornering, speed, distracted driving, time of day, and trip usage patterns. TD also states that the program can decrease or increase premiums by up to 30%, gives a one-time 10% activation discount, personalizes premium six months into the policy term, and requires at least 30 trips for the first premium adjustment. citeturn39view0turn41view2turn40view0

For your purpose, the most important conclusion is this: **the publicly knowable part of TD MyAdvantage is the product surface, data model, and operating constraints, not its proprietary source code or backend internals**. That is enough to derive a high-confidence architecture pattern for DrivePerf: a phone-first telematics app with a lightweight trip detector, a high-accuracy trip tracking session, local sensor buffering, explainable event detection, optional cloud sync, and a scoring layer that turns raw signals into user-facing insights. The Android platform itself strongly nudges you toward this shape because of modern rules around activity recognition, background location, foreground service types, and battery limits. citeturn39view0turn36view3turn35view5turn35view6turn35view4

If you want DrivePerf to feel “TD MyAdvantage-like” but not be constrained by insurance pricing logic, the best product framing is: **DrivePerf is a local-first, explainable driving analytics app that scores safety, smoothness, efficiency, and trip quality from start point to end point using only the phone’s sensors and location stack**. That gives you a clean MVP, avoids pretending that a phone can directly measure engine health or fuel burn, and still leaves room later for insurer-style pricing, fleet analytics, or OBD integration. This conclusion is consistent with TD’s phone-sensor-based design and with the broader smartphone telematics literature, which shows that GNSS and IMU data are already sufficient for useful event detection and driving behavior analysis. citeturn39view0turn25view0turn25view2

## What public evidence reveals about TD MyAdvantage

The observable structure of TD MyAdvantage is unusually well documented by TD itself. The module sits inside the TD Insurance app, which also handles claims, coverage, proof of insurance, billing, and payments. TD’s program page says users enrol by contacting TD, then download the TD Insurance app, find TD MyAdvantage on the dashboard, and activate it there. TD also says the experience is designed to be “simple and easy-to-find” inside the app, which strongly suggests MyAdvantage is implemented as a feature module within a larger authenticated insurance container rather than as an isolated utility tool. citeturn39view0turn40view0

TD’s scoring model is also public at the behavioral level. The program page and FAQ say trip ratings are affected by braking, acceleration, cornering, speed, distracted driving, time of day, distance travelled, highway driving, and trip length. TD further explains that each trip gets a rating from 0 to 5 stars, that projected premium becomes visible after 30 trips, and that the overall score uses confirmed trips from the past 12 months. It also notes that nighttime driving and longer drives can reduce score, and that distraction is specifically broken into hands-free calls, handheld calls, and direct screen usage, with direct screen usage penalized most severely. citeturn10view5turn10view2turn39view0

The trip review mechanics are especially important because they reveal how TD handles false positives. TD gives users 7 days to remove trips where they were not the driver, 30 days to inspect trip details and see which events affected a specific trip rating, and a toggle on a MyAdvantage preferences page to turn tracking off. TD also states that each enrolled driver must use their own login on their own phone, and that if multiple drivers share one phone, the recorded data will attach to the logged-in driver only. That tells you TD treats trip attribution as imperfect and relies on a mix of automatic detection plus human correction. citeturn10view0turn10view2turn10view3turn10view4

The operational constraints are equally revealing. TD says the app must run in the background to record trips automatically, that around 95% of drives are typically captured, and that missed trips can happen when the phone is not in the car, the battery is low, power-saving or battery optimization is enabled, GPS quality is poor, the app is outdated, or the user is logged in on more than one device. TD also says the app uses only a small amount of battery during normal recording, but recommends at least 20% battery charge and warns that very long trips increase GPS battery impact. citeturn41view1turn41view2

The data collection footprint is broader than just GPS and acceleration. TD says the program collects GPS and trip distance, acceleration/speeding/braking/cornering derived from GPS or accelerometer data, distracted driving data, time of day, app on/off state, battery level and charging status, Bluetooth and Wi‑Fi connection information, internet connectivity type, and mobile device identifiers and device details. The Google Play listing adds that the app may share location and personal information with third parties, may collect several data categories, encrypts data in transit, and allows users to request deletion. A legal note on TD’s program page also says projected outcomes are based on a sample of approximately 28,000 driving scores using Canadian driving data acquired from a third party, which suggests external calibration data, even though TD does not publicly identify the third-party provider in the materials reviewed. citeturn39view0turn40view0turn37view0

## What TD MyAdvantage likely looks like under the hood

Because TD discloses behavior rather than code, the best way to understand its architecture is by inference from what the product must do reliably. A module that activates from a dashboard, tracks trips in the background, records motion and location, explains event-level scoring, allows trip correction, and feeds premium projections almost certainly has at least five layers: an authenticated insurance app shell, a telematics feature module, a trip-detection subsystem, a local data store and sync queue, and a server-side scoring and account integration service. That inference follows directly from TD’s described user flows and from Android’s API model for activity recognition, fused location tracking, background limits, and structured foreground location work. citeturn39view0turn10view0turn36view3turn36view0turn35view5turn35view4

A high-confidence inferred flow for TD looks like this:

**dashboard activation → permission onboarding → lightweight “am I in a vehicle?” detector → active trip session with higher-accuracy location and motion capture → local event extraction and buffering → upload/sync → backend score normalization → dashboard, star rating, and premium projection**

That pattern aligns with Android’s own recommendations. Google’s Activity Recognition Transition API is explicitly described as the preferred way to detect activity changes, including entering and exiting a vehicle, because it improves accuracy and uses less power. For location, Android recommends the fused location provider, which manages underlying location sources while letting the app request updates at the needed balance of accuracy and battery use. Android also now requires explicit foreground service typing for long-running foreground work, and background location remains tightly restricted to apps whose core functionality truly depends on it. citeturn36view3turn36view2turn36view0turn36view1turn35view4turn35view5turn35view6

One more inference is worth making carefully: TD’s rating logic is probably **not** a purely on-device scoring engine. That is because the public product includes premium personalization tied to policy state, 12-month history, projected pricing, milestone dates, and program eligibility rules such as inactivity removal and minimum trip counts. Those are much easier to govern consistently if the final score state, adjustment logic, and policy linkage live server-side, even if some preprocessing happens locally on device. TD’s own pages point to this hybrid model by combining phone-captured telemetry with policy administration, premium adjustments, and third-party-acquired calibration data. citeturn39view0turn41view2

For DrivePerf, this is useful because you do **not** need to replicate insurer complexity on day one. The right lesson from TD is not “build premium logic first.” The right lesson is “build a reliable trip lifecycle first, then an explainable scoring model, then cloud sync and policy logic only if the business case needs it.” TD’s surface area shows that user trust depends on accurate trip capture, understandable per-trip feedback, and easy correction of false positives. citeturn39view0turn10view0turn10view2turn41view2

## The recommended architecture for DrivePerf

DrivePerf should be designed as a **local-first Android telematics app** with optional cloud sync. On Android, the cleanest implementation is a layered architecture with unidirectional data flow, screen-level state holders, repository-based data access, and a clear separation between UI, domain logic, and data sources. Google’s current architecture guidance explicitly recommends this style because it improves robustness, testability, and scalability. citeturn35view0turn35view1turn32search0turn32search14

At the Android client level, I would structure DrivePerf like this:

### App shell and feature modules

Use a standard app shell for onboarding, permissions, settings, privacy controls, and trip history, then a dedicated telematics feature module for trip lifecycle and scoring. Compose fits well here because Android’s Compose guidance explicitly maps well to unidirectional data flow and state-holder patterns. A `ViewModel` per major screen should expose immutable UI state and route actions into domain use cases. citeturn32search12turn32search10turn32search16

### Trip detection and active tracking

Use the Activity Recognition Transition API to detect likely `IN_VEHICLE` entry and exit states. That API is the preferred way to do activity recognition and is directly suited for “entered vehicle / exited vehicle” scenarios. Once a likely trip starts, switch into an active trip session that uses the fused location provider for frequent location updates and samples motion sensors such as accelerometer and gyroscope through the sensor framework. The `ACTIVITY_RECOGNITION` permission is a dangerous permission from API 29 onward, so it needs a proper runtime flow. citeturn36view3turn36view2turn36view0turn36view1turn36view4turn36view5turn36view6

### On-device persistence and sync

Store trip sessions, event windows, score breakdowns, and sync status locally with Room. Google recommends Room for structured local data and offline caching. Use WorkManager for deferred, guaranteed uploads, diagnostics sync, and model/config refresh, because WorkManager persists scheduled work and reschedules it after reboot. Keep the foreground service only for the period where there is an active, user-noticeable trip being tracked; Android 14+ requires correct foreground service typing and permissions, and background location should be requested only if tracking is genuinely core to the app. citeturn35view2turn35view3turn35view4turn35view5turn35view6

### Cloud and enrichment layer

Make the backend optional in MVP but first-class in the design. When present, the backend should ingest trip summaries and selected derived features, not raw sensor streams forever. It should perform map matching, posted-speed enrichment, long-horizon scoring, cohort calibration, and cross-device account history. If you need posted speed limits, do not hardcode assumptions: wrap the provider behind an interface. Google’s Roads API can return posted speed limits, but Google explicitly warns that speed-limit data is not real-time and may be estimated, incomplete, or outdated depending on region. citeturn35view7

A practical entity model would include `TripSession`, `LocationPoint`, `MotionWindow`, `DrivingEvent`, `TripScore`, `DailySummary`, `PermissionState`, and `SyncEnvelope`. That model is simple enough for a prototype but scales to insurer-grade or fleet-grade use later.

## Scoring and analytics model for DrivePerf

The best way to design DrivePerf is to keep the first scoring model **explainable**. TD’s public model already shows what users understand intuitively: harsh braking, harsh acceleration, sharp cornering, speeding, distracted phone use, nighttime driving, and overly long trips. Smartphone telematics research supports the same direction. A major Google research paper found that a Transformer-based hard-braking detector using smartphone kinematics substantially outperformed simple GPS-speed or accelerometer heuristics, while a systematic review of smartphone-based distracted-driving research found that GNSS and IMU data are the most common foundations for this kind of monitoring. citeturn39view0turn10view5turn10view2turn25view0turn25view2

For DrivePerf, I recommend four user-facing scores instead of one insurer-style score:

**Safety Score**  
This should be the primary score. It should weight overspeed relative to posted speed, hard braking frequency, harsh acceleration, aggressive cornering, and distracted phone interaction during motion. Night driving and long continuous trip duration should act as context multipliers rather than direct punishments, because they raise risk exposure rather than always reflecting poor driving. This is close to TD’s public logic, but framed as coaching rather than underwriting. citeturn10view5turn10view2turn39view0

**Smoothness Score**  
This should focus on jerk, abrupt longitudinal acceleration changes, abrupt lateral force events, stop-start harshness, and steering oscillation proxies from gyroscope data. It is useful because users often respond better to “drive more smoothly” than to abstract risk terminology. Android’s motion sensor stack is enough to support this. citeturn36view5turn36view4

**Efficiency Score**  
With a phone-only app, efficiency must be treated as a **behavioral proxy**, not as literal fuel measurement. Build it from idling time, overspeed time, repeated hard acceleration, repeated hard braking, and unnecessary stop-go patterns. If you later add OBD or EV APIs, you can upgrade this to real energy consumption. Until then, be transparent that this is a driving-efficiency estimate based on behavior patterns rather than direct vehicle telemetry.

**Trip Quality Score**  
This is your “overall drive from start to end” score. It can combine route continuity, trip completion quality, signal confidence, anomaly penalties, passenger-mode corrections, and the other three scores into a single session grade. This is the score most similar to TD’s trip stars, but you should show the breakdown clearly so users can understand why a trip scored the way it did. TD’s public trip-review model is a very strong precedent for explainable telemetry. citeturn10view0turn39view0

A good MVP scoring workflow would be:

- use activity transitions to open a candidate trip;
- confirm with movement + location consistency;
- sample fused location and IMU during the active session;
- derive event windows locally;
- classify events with rule-based thresholds first;
- persist event explanations for trip review;
- upload summaries for later calibration and ML improvement.

That order matters. Rule-based scoring gets you to production faster and makes early QA easier. Then, once you have labeled trip windows, you can move individual detectors such as hard braking or distraction into ML models. Google’s hard-braking research is a strong argument for that second step, not for starting with it. citeturn25view0

## Privacy, compliance, and Play Store readiness

Because DrivePerf will process sensitive mobility data, privacy cannot be an afterthought. In Canada, PIPEDA sets the ground rules for private-sector organizations collecting, using, and disclosing personal information in commercial activities, and it is built on principles including accountability, identifying purposes, consent, limiting collection, limiting use and retention, safeguards, openness, and individual access. The Office of the Privacy Commissioner also notes that Quebec has its own substantially similar private-sector privacy law, which matters if the app operates there. citeturn38view0turn38view1

The most important product implication is **data minimization with explicit, contextual consent**. After the Tim Hortons location-tracking investigation, the Canadian privacy regulator explicitly warned businesses to collect only the information they legitimately need, only when they need it, and to ask for express consent before collecting sensitive granular geolocation data. It also highlighted how over-collection of location data can create disproportionate privacy loss relative to business value. For DrivePerf, that means you should only ask for background location if automatic trip detection is a core, obvious feature, and you should give users a visible in-app tracking switch, clear retention rules, export/delete controls, and a prominent explanation of what is collected during a trip and why. citeturn38view3turn38view2turn35view5turn35view6

Google Play adds another layer of compliance. All Play-published apps must complete the Data safety form, declare how data is collected and handled, disclose what is collected or shared by third-party SDKs, provide a privacy policy, and keep the declarations complete and accurate. Android’s own background-location guidance warns that Play restricts background location to apps that genuinely need it for core functionality, and that compliance with technical best practices does not guarantee Play approval on its own. citeturn37view0turn35view5

The practical privacy design for DrivePerf should therefore be:

- **trip-only collection by default**, with optional continuous auto-detection only after informed opt-in;
- **on-device preprocessing first**, uploading derived events and trip summaries unless the user explicitly enables cloud history or diagnostics;
- **clear deletion controls**, including delete trip, delete account history, and request full backend erasure where applicable;
- **tracking transparency**, including a persistent notification during active trip sessions and an obvious settings page;
- **SDK discipline**, because any third-party SDK behavior must also be disclosed in the Play data-safety response. citeturn37view0turn35view4turn35view5

## Implementation roadmap and a Copilot-ready build brief

The fastest route to a credible DrivePerf product is a phased rollout.

### Recommended roadmap

**Discovery and calibration spike**  
Build an internal-only prototype that logs activity transitions, fused location points, accelerometer windows, gyroscope windows, and battery state. Use it to collect labeled sample drives across city roads, highways, daytime, nighttime, passenger rides, and poor GPS conditions. This phase is about thresholds, false starts, and battery impact—not polished UI.

**MVP trip lifecycle**  
Implement onboarding, permission flow, trip start/end detection, active trip tracking, local Room storage, trip list, trip detail screen, and a simple rule-based Safety Score. Include a foreground notification during active tracking and a review/delete flow for misattributed trips. This gives you the “TD-like” core without requiring underwriting logic.

**Explainable scoring and insights**  
Add event breakdowns for braking, acceleration, cornering, speed, phone interaction, night driving, and long-trip context. Add trend views such as “last 7 days” and “last 30 trips.” Show score changes transparently.

**Cloud sync and calibration backend**  
Add account sign-in, WorkManager-based uploads, config download, remote threshold tuning, model versioning, and map/speed-limit enrichment behind provider interfaces. Keep the app usable offline.

**Beta hardening and compliance**  
Run battery tests, trip-capture accuracy tests, OEM battery-optimization tests, poor-connectivity tests, and Play Console privacy declaration checks. Finalize the privacy policy, deletion workflows, and consent copy.

### Copilot-ready build brief

Use the following as the brief you paste into Copilot or another coding agent:

```text
Build an Android app called DrivePerf.

Product goal:
DrivePerf should detect vehicle trips from start point to end point and score each trip for safety, smoothness, efficiency, and overall trip quality using smartphone data only. The app must feel production-grade, explainable, privacy-aware, and battery-conscious.

Primary user stories:
- A user can onboard and understand exactly what data is used.
- A user can grant permissions incrementally and see why each permission is needed.
- The app can automatically detect a likely vehicle trip.
- During an active trip, the app records location + motion data and computes event-level driving analytics.
- After the trip, the app shows a trip summary, event breakdown, route map, and score breakdown.
- The user can mark a trip as passenger / not driver and delete it.
- The user can view trends over time and see actionable coaching tips.

Technical requirements:
- Kotlin
- Jetpack Compose UI
- ViewModel + unidirectional data flow
- Repository-based data layer
- Hilt for DI
- Room for local persistence
- WorkManager for deferred sync
- Google Play Services Activity Recognition Transition API for trip detection
- Fused Location Provider for active trip tracking
- SensorManager for accelerometer + gyroscope
- Foreground service with correct location service type only during active trips
- Modular architecture with feature packages:
  onboarding
  permissions
  telematics
  trips
  insights
  settings
  sync

MVP scoring:
- Safety Score from speeding, hard braking, harsh acceleration, aggressive cornering, and phone-use distraction
- Smoothness Score from jerk and abrupt motion changes
- Efficiency Score as a proxy based on idling, overspeed time, and repeated aggressive acceleration/braking
- Trip Quality Score as weighted aggregate + signal confidence

Important product constraints:
- Local-first design
- Works offline for recorded trips
- Sync is optional and can be added behind interfaces
- Explain every score with human-readable reasons
- Include privacy settings and delete-trip flow
- Make false positives easy to correct
- Build comprehensive logging and test hooks for calibration

Implementation tasks:
1. Create project skeleton and architecture
2. Add permissions flow with educational UI
3. Add trip detection state machine
4. Add active trip foreground tracking service
5. Add Room entities and DAOs
6. Add trip repository and local persistence
7. Add event detectors for braking, acceleration, cornering, speed, and phone interaction
8. Add trip summary UI and history UI
9. Add trend analytics UI
10. Add unit tests for scoring and trip state transitions
11. Add instrumentation tests for permission and service flow
12. Add provider abstractions for future backend and speed-limit enrichment

Deliverables:
- Clean Android Studio project
- Readme with setup and architecture notes
- Testable local MVP first
- Feature flags/interfaces for future backend sync and remote scoring
```

This brief is intentionally local-first, so Copilot can implement a meaningful app without getting blocked on backend decisions.

## Open questions and limitations

The research above is high confidence on **product behavior** and **Android constraints**, but not on TD’s proprietary code stack. TD’s public pages explain what the program does, what it collects, and how users interact with it; they do not disclose the exact mobile framework, telematics SDK vendor, cloud topology, or model stack used internally. The architecture section in this report should therefore be read as a strong inference from public evidence rather than a reverse-engineered fact. citeturn39view0turn40view0turn41view2

Before implementation begins, you should settle four decisions that will materially affect scope:

- whether DrivePerf is a **consumer coaching app** or an **insurance/fleet scoring app**;
- whether “efficiency” will remain a **phone-only proxy** or later include OBD/CAN/EV integrations;
- which **posted-speed provider** and regions you want to support, because provider coverage and accuracy vary; and
- what your **retention, deletion, and regional privacy scope** will be, especially if you plan to launch in Canada with always-on trip detection. citeturn35view7turn38view0turn38view3turn37view0

If you want the highest chance of a successful first release, build DrivePerf as a **transparent, explainable, local-first telematics app** first. That is the part of TD MyAdvantage that is easiest to learn from, the part users actually touch, and the part the Android platform most directly constrains. Everything beyond that—pricing, underwriting, insurer integration, large-scale ML calibration—should come only after the trip lifecycle and trust model are solid. citeturn39view0turn36view3turn35view5turn37view0