# A3: DrivePerf platform feasibility (iOS 26/27 and Android 15–17)

| | |
|---|---|
| **Research date** | 6 November 2026 |
| **Author** | Agent A3 (kickoff plan, Phase 1) |
| **Scope** | iOS background capture and sensors, Live Activities, Android 15–17 deltas, store policy, battery, KMP feasibility, Simulator-only development, Firebase plans, parity matrix |
| **Builds on** | [`2026-09/android-feasibility-evidence.md`](../2026-09/android-feasibility-evidence.md). Android permissions, Data Safety basics and test design are covered there and not repeated here. |
| **Status** | This is research input, not a specification. Nothing here has been tested on a device yet. |

**Evidence labels used in this document**

- **[Official]**: Apple, Google, Firebase or JetBrains documentation or policy.
- **[DTS]**: a reply from an Apple Developer Technical Support or WWDR engineer on the Apple Developer Forums. It is useful, but it is not documentation.
- **[Field]**: developer reports, vendor documentation or vendor benchmarks. Treat these as anecdotal.
- **[Estimate]**: my own estimate. The derivation is always shown.

Source IDs in square brackets (for example [A9]) point to the **Sources** list at the end. Every source was accessed on 2026-11-06.

---

## One-page summary

**Verdict: feasible, with two platform-level compromises on iOS that the product must design around.** The v1 promise of "zero-effort capture with a live in-trip surface" holds on Android and in iOS manual mode. On iOS automatic mode it holds only in a degraded form.

**What holds**

- **iOS automatic trip detection is achievable with public APIs.** It needs "Always" location. The app is woken by significant-location-change (SLC), region (`CLMonitor`) or visit events. It then starts continuous location, which keeps the app awake. While the app is awake, `CMDeviceMotion` keeps delivering at 50 Hz. Apple documents that SLC and region monitoring relaunch a terminated app [A5][A8]. An Apple DTS engineer confirmed in August 2026 that motion delivery in the background depends only on the app being kept awake, for example by location [F2].
- **Android automatic mode works through established mechanisms.** An Activity Recognition transition is one of the documented exemptions for starting a foreground service (FGS) from the background. With `ACCESS_BACKGROUND_LOCATION`, that FGS gets location "all the time" [G7]. Live Update notifications fit the in-trip surface [G12].
- **KMP core plus native UI holds up.** Sensors, background lifecycle, Live Activities and Live Updates, and Firebase SDKs are native on both platforms on any stack. The state machine, signal processing, scoring, storage schema and sync logic can be shared. Swift interop is the main friction: Swift export is only Alpha in Kotlin 2.4.0 [K1], so production should use Objective-C export plus SKIE, with the Kotlin version pinned.

**What degrades (design around these)**

1. **iOS cannot start a Live Activity locally for an automatically detected trip.** A DTS engineer stated in March 2026 that a Live Activity cannot be started from a background context such as a `CLLocationManager` wake. The only routes are a user-initiated `LiveActivityIntent` or an APNs push-to-start [F3][A21]. Push-to-start needs a paid Apple account, a server (Cloud Functions requires Blaze) and network access. That conflicts with local-first.
2. **iOS loses the start of every automatic trip.** SLC fires after about 500 m and at most every 5 minutes [A5]. One vendor says its SDK misses "the first 300 to 500 meters" of each trip [T1]. `CMSensorRecorder` (50 Hz accelerometer data, recorded even while the app is suspended or terminated, kept for 3 days [A16]) plus motion-activity history can partly backfill the gap. The GNSS route for the gap cannot be recovered.
3. **iOS behaviour after force-quit is uncertain.** Apple DTS said in March 2026 that SLC "will relaunch the app despite a force quit" [F1]. A developer reported in 2026 that this no longer happens on iOS 26.4.2 [F6]. I could not confirm that report against an Apple source. Low Power Mode turns off Background App Refresh [A24], and its effect on SLC wakes is undocumented.
4. **Android auto mode is at the mercy of OEM battery managers.** dontkillmyapp rates Samsung and Xiaomi 5/5 (worst) and lists "no known solution on dev end" for Samsung [T6]. Since Android 15, a user force-stop cancels all of an app's PendingIntents, including transition registrations [G9].

**Policy check.** The **27 January 2027 Google Play change** is confirmed by the 29 October 2026 policy announcement and the Policy Deadlines table [G1][G2]:

- Apps must use the minimum location scope.
- For apps targeting **API 37+**, the **location button is mandatory** for one-time or infrequent location requests.
- Apps that use `ACCESS_FINE_LOCATION` must file a Play Console **declaration** explaining why coarse location or the location button is insufficient.
- **Geofencing is removed as an approved FGS use case**. Apps must use the Geofence API instead.

None of this blocks DrivePerf: continuous trip recording is not a one-time request. It does add a third declaration, alongside background location and the FGS declaration.

**Battery [Estimate].** About **2–4 %/h with the screen off** and **7–15 %/h with the screen on** on a mid-tier phone, for 1 Hz GPS plus 50 Hz IMU. Section 6 shows the derivation. The plan's target of 4–6 %/h is realistic only when the screen is off. On Android, a manual wake lock held throughout a 2-hour or longer daily drive would also trip the Play "excessive partial wake locks" vital [G15].

**Firebase Spark is enough for prototyping, not for v1 backup.** Cloud Storage has needed Blaze since **3 February 2026** [FB2]. Cloud Functions and Phone Auth are Blaze-only. Auth, Firestore, Hosting, FCM, Crashlytics, App Check and Remote Config are available at no cost on Spark [FB1]. On iOS, FCM, Sign in with Apple and App Attest need the paid Apple Developer Program [A28].

**Simulator-only development is narrower than it looks.** The Simulator has no Core Motion hardware [A31]. However, a **free Personal Team can install on a real iPhone with the Background Modes capability** (profiles expire after 7 days, up to 3 devices) [A28][A29]. **The riskiest spike (iOS background capture) therefore does not have to wait for the $99 account.**

**Decisions the product owner must make**

1. **Live surface for iOS automatic trips:**
   - (a) accept "no Live Activity for auto trips, notification only";
   - (b) fund push-to-start (paid Apple account + Blaze + network dependency); or
   - (c) a one-tap Control Center control or widget that starts the trip through a `LiveActivityIntent`.

   I recommend (a) + (c) for v1 and evaluating (b) later.
2. **Honest capture promise:** "auto-detects most drives. The first few hundred metres may be estimated. Force-quitting pauses auto-detect."
3. **When to buy the Apple Developer account:** after Spike 1, before work on Sign in with Apple, push, App Check or TestFlight.
4. **When to upgrade to Blaze:** when cloud backup of trip files is built, or if push-to-start is chosen.
5. **Battery target wording:** state it as "screen off".

---

## 1. iOS automatic trip detection

### 1.1 Building blocks

| Mechanism | Wakes a suspended or terminated app? | Authorization | Power and latency (documented) | Role in DrivePerf |
|---|---|---|---|---|
| **Significant location change (SLC)**, `startMonitoringSignificantLocationChanges()` | **Yes.** "the system automatically relaunches the app into the background if a new event arrives" [A5] | Always (in practice, because the app is not in use when the event fires) | Uses cellular and Wi-Fi, not GPS [A7]. Fires when the device "moves 500 meters or more"; "should not expect notifications more frequently than once every five minutes" [A5] | **Primary wake trigger** |
| **Region monitoring**, `CLMonitor` (iOS 17+) or legacy `CLCircularRegion` | **Yes**, for region events [A8] | Always | Latency is not specified on the pages I fetched. After a `CLMonitor` relaunch, the app must recreate the monitor with the same identifier [Field] | **Secondary wake.** Put a region around the last parked location (where the previous trip ended), so the app wakes on exit |
| **Visits**, `startMonitoringVisits()` | **Yes** [A8] | Always | "Most power-efficient". Delivers data "at a later time" [A7] | **Not a start trigger.** Use it to confirm parking and trip end, and to recover trips missed while the app was asleep |
| **Motion activity**, `CMMotionActivityManager` | **No.** No Core Motion API wakes an app [F2] | Motion & Fitness (`NSMotionUsageDescription`; the app crashes without it) [A11] | `automotive` and `confidence` properties. `automotive` and `stationary` can both be true at a red light [A12] | **Confirmation and backfill.** On wake, query recent history to find when "automotive" began |
| **`CLLocationUpdate.liveUpdates`** (iOS 17+) | Core Location "will recover your app… by launching it in the background" if the app is terminated while updates are active [A9] | When In Use with a session, or Always | Auto-pauses when stationary (`stationary`) and resumes automatically [A9] | **The in-trip location stream** (`.automotiveNavigation` configuration) |
| **`CLBackgroundActivitySession`** (iOS 17+) | Rejoins an existing session if it is recreated immediately after relaunch [A9] | Lets a **When-In-Use** app receive updates in the background, with the blue indicator [A2] | Must be created in the foreground [A1] | **Manual trips** for users who decline Always |
| **`CLServiceSession`** (iOS 18+) | Must be recreated "immediately upon launch in the background" [A1] | Declares the goal: `.whenInUse` or `.always` | Since iOS 18, "Always authorization will only be effective when you hold one of these, and you can only start holding one when your app is in the foreground" [A10] | **Required** for auto mode with the modern APIs |
| Legacy `startUpdatingLocation()` + `allowsBackgroundLocationUpdates = true` | Keeps the app running in the background. Needs `UIBackgroundModes: location`, and setting the flag without that mode is "a fatal error" [A6] | When In Use or Always | Standard GPS service [A7] | An alternative in-trip stream. It is the classic telematics pattern after an SLC wake |

### 1.2 The "Always" authorization flow (iOS 18 and later)

- **Info.plist.** `NSLocationWhenInUseUsageDescription` and `NSLocationAlwaysAndWhenInUseUsageDescription` must both be present before any request. Otherwise "authorization requests fail immediately" [A8].
- **Always is a second step.** "If your app already has When in Use authorization, you can make a separate request for Always authorization later. However, you can make the request **only once**." [A8] If the user declines, the only path afterwards is Settings.
- **Always only works while a session is held.** Since iOS 18, Always is effective only while the app holds a `CLServiceSession(.always)`. That session can only be started in the foreground. If the app is Always-authorized, `liveUpdates` and `CLMonitor.events` "won't yield results when it is not in use" unless a foreground-started session "asserts that continued interest" [A10].
- **Core Location keeps the session alive only briefly.** It tracks sessions across termination "for only a few seconds after the app is launched again". The app must re-take its sessions immediately in `didFinishLaunchingWithOptions` [A10][A9].
- **Recommended UX** (progressive, which matches the plan's "progressive permissions"):
  1. Request When In Use at the first manual trip.
  2. Offer "Automatic trips" as an explicit toggle. When the user turns it on, explain background use ("even when the app is closed"), then request Always once. Apple's note: "For Always authorization, inform the user that location updates arrive in the background." [A1]
  3. If the user declines, keep manual mode with `CLBackgroundActivitySession` and show a persistent "Auto-detect off" state with a Settings deep link.
- **Precise location** is required for road-level events. Use `CLServiceSession(authorization:fullAccuracyPurposeKey:)` [A3]. If the user chooses approximate location, degrade to "route only, no event placement", as the September research recommends for Android.
- **Open question for Spike 1.** The WWDC24 statement about sessions covers the "modern" APIs. It is not documented whether legacy `CLLocationManager` SLC and `startUpdatingLocation` also need a held `.always` session on iOS 26/27. To be safe, hold one anyway.

### 1.3 Recommended iOS auto-trip architecture

1. **Armed state (no trip):**
   - SLC on;
   - a `CLMonitor` circular region around the last trip's end point;
   - a `CLServiceSession(.always)` recreated at every launch;
   - `CMSensorRecorder.recordAccelerometer(forDuration: 43_200)`, re-armed at every wake. 43,200 s (12 h) is the documented maximum [A16].
2. **On wake** (SLC or region exit). The DTS engineer says this window "is usually limited to about 10 seconds" [F1].
   - Immediately recreate the sessions.
   - Start `liveUpdates(.automotiveNavigation)`, or `startUpdatingLocation` with background updates allowed. A running location stream keeps the app awake [A6].
   - Query `CMMotionActivityManager` history for the last 15 minutes.
3. **Confirm driving:**
   - Example rule: speed above 25 km/h for 20 s, or "automotive" at medium or high confidence plus speed above 10 km/h. These thresholds are placeholders for A19 calibration.
   - If driving is not confirmed within about 3 minutes, stop GPS and return to armed state.
4. **Record:**
   - `CMDeviceMotion` at 50 Hz on one `CMMotionManager` [A13];
   - GPS at 1 Hz;
   - stream to disk every few seconds. Section 2 has the file protection details.
5. **Backfill the trip start:**
   - Set the trip start time from the motion-activity history.
   - Pull 50 Hz accelerometer data for the gap from `CMSensorRecorder`. It contains accelerometer data only: no gyroscope and no attitude.
   - Draw the route segment for the gap as estimated (from the parked point to the first fix) and label it "estimated".
6. **End the trip:**
   - Use `liveUpdates` `stationary` plus a stop timer. DriveQuant's default is 4 minutes, configurable from 2 to 8 [T2].
   - Finalise scoring while the app is still awake, then stop GPS.
   - Re-arm the parked-location region.

### 1.4 How fast can recording start after the car moves?

| Trigger | What the docs say | [Estimate] time to the first in-app GPS fix |
|---|---|---|
| SLC | No earlier than about 500 m of movement, and no more often than every 5 min [A5] | At 30–50 km/h, 500 m takes **36–60 s** (500 m ÷ 8.3–13.9 m/s). Add a few seconds for wake-up and a warm GNSS fix. Typical range: **40–90 s, or 0.5–1 km**. It is worse when the network is poor ("much more likely to deliver… timely" with network [A5]) |
| Region exit (parked-location region) | Latency not documented on the pages I fetched | Unknown. It depends on region radius and on cell or Wi-Fi density. **Measure it in Spike 1** |
| Motion activity | No wake path [F2] | Not applicable. Used for backfill only |
| **Vendor field data** | DriveQuant: the OS "needs a short time window to wake the app and confirm a motorised trip, so the first 300 to 500 meters of each trip are not captured" (9 Oct 2025) [T1] | Consistent with the SLC estimate above |

**How existing apps do it (documented):**

- **DriveQuant DriveKit.** Estimates whether a trip is "probably underway", then "turns on the GPS sensor". It cancels trips whose speed does not match a motor vehicle, records at 1 Hz, and uses a 4-minute stop timeout [T2]. Its benchmark reports trip detection above 97% for most SDKs tested and below 3% battery per day [T1]. This is a vendor benchmark.
- **Dawarich** (30 Oct 2026). Uses SLC for a "battery saver" mode that requires Always and Background App Refresh, and `liveUpdates` with a background session for continuous tracking. It says continuous tracking is relaunched after a memory kill, but not after a force-quit or reboot [T3]. Unofficial.
- **Life360 (Android).** Measured about 1 minute of transition latency in the 2018 Transition API beta [T4]. This is old and from one app.

### 1.5 Force-quit, reboot, Low Power Mode and other edge states

| State | Evidence | Expected behaviour | Product handling |
|---|---|---|---|
| **User force-quit** (swiping the app away) | **Sources conflict.** Archived Apple doc: "The only way to have your app relaunched automatically is to use region monitoring or the significant-change location service." [F5] DTS, March 2026: SLC "will relaunch the app despite a force quit… technically this is an off-label use" [F1]. Developer report (2026): SLC did **not** relaunch a force-quit app over a 30 km drive on iOS 26.4.2 [F6]. I could not open that thread or find an Apple acknowledgement. `liveUpdates` sessions are not relaunched after a force-quit [T3, unofficial] | **Treat it as unreliable** until Spike 1 measures it on iOS 26.x and 27.x | Use the watchdog pattern from DTS [F2]: schedule a local notification for the future and push it back at every wake. If the app stops waking, the user sees "Auto-detect is paused. Open DrivePerf to resume". In onboarding, explain that force-quitting pauses auto-detect |
| **Reboot** | Default file protection is "complete until first user authentication". Files are inaccessible "until the first time the user unlocks the device" [A25]. Whether SLC or region wakes resume after a reboot is not documented on the pages I fetched | Before the first unlock, the app cannot open its database even if woken. After the first unlock, wakes are expected to resume. **Unverified, measure in Spike 1** | Keep a small "pending wake" log at protection level `none`, containing timestamps only and no location. Reconcile with Visits and motion history after unlock |
| **Low Power Mode** | Apple: LPM pauses "discretionary and background activities" [A23] and "Turns off Background App Refresh" [A24]. Dawarich states that its SLC mode requires Background App Refresh [T3] (unofficial) | Already-running continuous location is probably unaffected. **SLC wakes may be suppressed**, and BGTasks will not run. Unverified | Observe `NSProcessInfoPowerStateDidChange`. Lower the IMU rate to 25 Hz. Warn in the app: "Low Power Mode may delay automatic trips" |
| **Background App Refresh off** (per app or globally) | Same dependency as above [T3] | Auto-detect may silently fail | Detect `UIApplication.backgroundRefreshStatus` and show a health check |
| **Location downgraded to While Using** | Always is required for wakes [A8][A10] | Auto mode is impossible. Manual mode works through `CLBackgroundActivitySession` [A2] | Health-check banner |
| **System memory termination during a trip** | Core Location relaunches the app and resumes queued updates if the APIs are restarted on launch [A1][A9] | Short gap | Write to disk continuously. Mark a gap in the trip rather than interpolating it, as the September doc recommends |

---

## 2. iOS sensors in the background

- **Delivery depends on being awake.** DTS, August 2026: "there isn't any direct requirement that you use CoreLocation, as what matters is that your app is 'awake in the background'… the system is designed to work this way." [F2]
  - The same thread includes a developer measurement on iOS 26.5.2: 1,366 of about 1,373 expected `CMDeviceMotion` callbacks (99.5%) arrived with a background location session, and 2 of about 1,286 arrived without one [F2].
  - Note that with the Xcode debugger attached the app is never suspended. **Measure without the debugger.**
- **Legitimacy.** DTS: "which background categories your app can use is determined by the functionality you provide to the user, NOT what would be most helpful/useful to your app's implementation" [F2]. DrivePerf records the route, so the location background mode is legitimate. Guideline 2.5.4 lists location as an intended purpose [A26].
- **Rates.** The maximum device-motion frequency "is hardware dependent, but is usually at least 100 Hz". Apple's own example uses 50 Hz [A14].
  - "Create only one CMMotionManager object for your app", because multiple instances affect sensor rates [A13].
  - Use `.xArbitraryCorrectedZVertical`, which does not depend on the magnetometer, so metal mounts are not a problem [A14].
- **Batching.** iPhone has no public hardware FIFO for `CMDeviceMotion`. Two alternatives:
  - **`CMSensorRecorder`**: 50 Hz accelerometer data, up to 12 h per call. It records "even if your app is suspended or terminated" and keeps data "for up to three days" [A16]. DTS is "a HUGE fan of CMSensorRecorder, even for 'real time' analysis" and recommends polling `accelerometerData(from:to:)` at a fixed cadence [F2].
  - **Hybrid:** `CMSensorRecorder` as the gap-free accelerometer record, plus `CMDeviceMotion` for gyroscope, attitude and gravity. Its battery cost when re-armed all day is **unknown** and must be measured.
  - `CMBatchedSensorManager` lists iOS in its availability metadata, but the page I fetched does not confirm iPhone support or say what is required [A17]. Do not plan on it.
- **Memory and CPU limits.** I found **no published numeric CPU or memory limit** for apps running in the background for location. Design defensively:
  - Process incrementally.
  - Keep in-memory ring buffers to about 60 s.
  - Stream to disk.
  - Expect jetsam terminations and recover from them (§1.5).
- **Data volume [Estimate].** One 50 Hz sample of accelerometer (3), gyroscope (3), gravity (3), quaternion (4) and timestamp (1) is 14 values: 112 B as float64 or 56 B as float32. At 50 Hz that is 2.8–5.6 KB/s, or **about 10–20 MB per hour uncompressed**. 1 Hz GNSS adds under 0.5 MB/h. This fits locally. Cloud backup should upload compressed and downsampled files, or summaries.
- **File protection (pitfall).** Trips record while the phone is locked, so trip files must **not** use `.complete`. Apple's own example: "a fitness app might use the complete unless open protection level on a file that it uses to log location events in the background." [A25] Use `.completeUnlessOpen` for trip logs and the default level for the SQLite database. Check how the SQLDelight or Room driver sets file attributes.
- **Post-trip processing:**
  - **Do the scoring during the trip and at trip end, while location still keeps the app awake.** Then stop GPS. This avoids depending on scheduling.
  - `BGAppRefreshTask` gets up to about 30 s and its scheduling "is HEAVILY driven by actual app usage patterns" [A19][F2].
  - `BGProcessingTask` is scheduled when the system decides, for example overnight while charging [A19]. Use it for re-scoring after algorithm updates, backups and database maintenance.
  - **`BGContinuedProcessingTask` (iOS 26+)** "starts in the foreground", shows progress in a system Live Activity, and can be cancelled by the user or terminated under resource pressure [A18]. It suits user-initiated exports or "re-analyse all trips", not automatic post-trip work.
  - The BGTask debug launch commands "work only on devices" [A20].

---

## 3. Live Activities and the Dynamic Island for an in-progress trip

| Constraint | Detail |
|---|---|
| **Duration** | Active for up to 8 h, then ended by the system. Stays on the Lock Screen for up to 4 more hours (12 h maximum) [A21]. Long road trips need an end-and-restart strategy, and the restart can only happen in the foreground |
| **Payload size** | Static plus dynamic data must not exceed **4 KB** combined, including push payloads. Images must not exceed the presentation size. Content taller than 160 pt may be truncated [A21] |
| **No location or network inside the activity** | "a Live Activity can't access the network or receive location updates". The app must update it [A21] |
| **Local updates** | The app can "update or end a Live Activity from your app while it runs in the background" [A21]. During a trip, the location background mode keeps the app running, so **local updates work for the whole trip** |
| **Starting one** | "your app needs to be in the foreground to start a Live Activity". The background exception is a `LiveActivityIntent` [A21]. DTS, March 2026: "it is not possible to programmatically initiate a Live Activity from a background execution context, such as a CLLocationManager wakeup, using local APIs." Only a system-run intent (widget, control, Shortcut, Siri) or APNs can start one [F3] |
| **Push updates** | There is a per-hour budget. Priority `10` counts against it. Priority `5` does not, and "you can send a low-priority ActivityKit push notification that doesn't count toward the budget". `NSSupportsLiveActivitiesFrequentUpdates` raises the budget, but users can turn frequent updates off (`frequentPushesEnabled`) [A22] |
| **Push-to-start** | Supported from iOS 17.2. The token comes from `pushToStartTokenUpdates`. Broadcast pushes cannot start an activity [A22]. Requires APNs, so a **paid account** |
| **Local update budget** | Apple publishes **no numeric limit** for local updates (none on the pages I fetched). [Estimate] Update on meaningful change, at most every 5–15 s. Render elapsed time with `Text(timerInterval:)` so the system animates it without updates |
| **Live Activity as a background keep-alive** | **Sources conflict.** WWDC23: "LiveActivity is the best way to enable background location updates" [A9]. A June 2025 developer report says location stopped about 10 s after backgrounding despite a visible Live Activity, and continued once a `CLBackgroundActivitySession` was held [F4]. **Always hold the session explicitly.** |

**Implications for DrivePerf**

- **Manual trip:** start the Live Activity on the tap and update it locally. This has full parity with the Android Live Update.
- **Automatic trip:** you cannot start a Live Activity locally. The options are:
  - **(a)** No Live Activity; the blue location indicator plus a local notification instead. Note that Time Sensitive notifications need the paid capability [A28].
  - **(b)** Push-to-start: on wake, call the backend, which sends an APNs push-to-start. This needs a paid account, a server (Cloud Functions on Blaze, or another server) and network access at the trip start, and its latency is unknown.
  - **(c)** Promote a **Control Center control or a Lock Screen widget** ("Start drive") that runs a `LiveActivityIntent`. That is one tap, and it is user-initiated.

  The product owner must choose (summary, decision 1).

---

## 4. Android 15–17 deltas (on top of the September research)

| Topic | What changed or applies | Impact and mitigation |
|---|---|---|
| **FGS `location` type** | Android 14+ targets: declare the type and `FOREGROUND_SERVICE_LOCATION`. At creation time the OS checks the permission, so a while-in-use permission fails if the app is in the background [G6][G7] | For auto trips, start the FGS from the transition or geofence `PendingIntent`. That is an exemption from the background-start ban, and **with `ACCESS_BACKGROUND_LOCATION` the service "can access location information all the time"** [G7] |
| **Background-start exemptions** | These include activity-recognition transitions, geofencing events, exact alarms for user-requested actions, high-priority FCM (can be downgraded), BOOT_COMPLETED, battery-optimization exemption, and `SYSTEM_ALERT_WINDOW` (Android 15+: only with a visible overlay) [G7][G8] | Use transitions plus a parked-location geofence as the two auto-start paths |
| **Android 15** | `BOOT_COMPLETED` cannot launch `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection` or `microphone` FGS. **`location` is not on that list** [G8]. `dataSync` and `mediaProcessing` have a 6 h per 24 h limit [G8]. **Force-stop now cancels all PendingIntents.** `BOOT_COMPLETED` is delivered when the app leaves the stopped state. There is a new `ApplicationStartInfo.wasForceStopped()` [G9] | Re-register transitions and geofences on `BOOT_COMPLETED` and on the first launch after a force-stop. Do not upload with `dataSync` FGS. Use WorkManager or user-initiated data transfer jobs |
| **Android 16** | Jobs started from an FGS (WorkManager included) follow normal quotas [G6]. Live Updates arrive (`ProgressStyle`) [G12] | Do not trigger uploads from inside the trip FGS and expect them to run immediately |
| **Android 17** | All apps: memory limits based on device RAM (exit reason `MemoryLimiter:AnonSwap`) [G11]. API 37 targets: background audio hardening (relevant for voice cues), mandatory local-network permission, background activity launch (BAL) hardening, certificate transparency on by default [G10] | Keep in-memory sensor buffers bounded. Play this summer required target API 36 for new apps and updates from **31 Aug 2026** [G17]. No date for an API 37 requirement was on the page I fetched |
| **Activity Recognition reliability** | Official: "The latency of event detection might vary by device." Transitions are supported for IN_VEHICLE, ON_BICYCLE, RUNNING, STILL and WALKING [G13]. Google publishes no accuracy or latency figures. Life360's 2018 beta measured about 1 min [T4]. **The Awareness API is deprecated and may be turned down as early as January 2027** [G18] | Do not use Awareness. Use transitions plus a parked geofence plus optional Bluetooth car connection. Measure latency per OEM in the device matrix |
| **Live Update notifications** | Styles allowed: Standard, BigText, Call, **ProgressStyle**, **MetricStyle**. Requires `POST_PROMOTED_NOTIFICATIONS`, `setRequestPromotedOngoing`, ongoing, a content title, no custom `RemoteViews`, not colorized, channel above `IMPORTANCE_MIN`. Users can demote it, and "OEMs can enforce additional criteria". Appropriate uses include "active navigation". "Most Live Updates should represent activities that are explicitly triggered by the user" [G12] | **Manual trip: a clean fit.** Auto trip: a grey zone, because it is not user-triggered. Show it only after the trip is confirmed, with a Stop action, and never repost one that was dismissed [G12]. A `MetricStyle` with distance, duration and smoothness fits well. The status chip shows short text (up to 7 characters) [G12] |
| **Play, 27 Jan 2027 (verified)** | Minimum location scope. **The location button is mandatory for one-time or infrequent requests (API 37+)**. **An `ACCESS_FINE_LOCATION` declaration** must explain why coarse location or the button is insufficient. **Geofencing is removed as an approved FGS use case**; use the Geofence API [G1][G2][G3] | DrivePerf needs fine location continuously, so it files the fine-location declaration (road-level event placement). Implement parked-car geofences with the Geofence API, never as an FGS. This is consistent with the September doc's "policy preview" note [T9], and the geofencing-FGS removal is new |
| **Play wake-lock vital** | Excessive means at least 2 h of non-exempt partial wake locks in 24 h, in more than 5% of sessions over 28 days. It can affect store visibility. Wake locks from the location, audio and user-initiated job APIs are exempt [G15]. Enforcement began 1 March 2026 [G16] (via search) | Do **not** hold a manual `PARTIAL_WAKE_LOCK` for the whole trip. Use sensor FIFO batching (`maxReportLatencyUs`) and location batching (`setMaxUpdateDelayMillis` [G14]). Test whether heavy commuters exceed 2 h/day |
| **OEM battery killers** | Samsung and Xiaomi are both rated 5/5. Samsung: "Put unused apps to sleep" (about 3 days), sleeping and deep-sleeping lists, "No known solution on dev end". A 2024 note says Samsung committed to honouring FGS for API 34 targets on One UI 6+, which is unverified [T6]. Xiaomi: Autostart, "No restrictions" battery saver, lock in recents [T6]. Samsung provides a deep link to the sleeping-apps screens (`com.samsung.android.lool`) [T7] | Show a "Reliability check" screen with OEM-specific guidance and deep links. Detect missed trips (an IN_VEHICLE transition seen later with no trip recorded) and prompt the user. Read `ApplicationExitInfo` for kills. Battery-optimization exemption is a valid FGS start exemption [G7], but check Play's acceptable-use rules before requesting `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |

---

## 5. Store review risk

### 5.1 Apple

| Guideline | Text (short quotes) [A26] | DrivePerf risk and mitigation |
|---|---|---|
| **2.5.4** Background services | "may only use background services for their intended purposes: VoIP, audio playback, location…" | **Low–medium.** Location is the real feature. Never use location only to keep the IMU alive [F2]. In review notes, state that background location records the route of automatically detected drives, and attach a demo video (preparation advice, not a documented requirement) |
| **2.4.2** Power | "should not rapidly drain battery, generate excessive heat" | **Medium.** A phone mounted in the sun while charging and running GPS can run hot. Provide thermal back-off (§6) |
| **5.1.1(i)–(ii)** | Privacy policy in App Store Connect metadata and in the app. Consent even for "anonymous" data. "Paid functionality must not be dependent on or require a user to grant access to this data" | **Pro gating:** do not make Pro features depend on granting Always location. Manual mode must remain fully usable |
| **5.1.1(v)** | "If your app supports account creation, you must also offer account deletion within the app." | Required for sign-in and backup |
| **5.1.2** | Consent before sharing. No repurposing without consent | Any future research-data donation needs separate consent |
| **5.1.5** Location | "only when it is directly relevant… notify and obtain consent before collecting, transmitting, or using location data" | Consent screen before cloud backup of routes |
| **1.4.5** Physical harm | Apps should not urge users "to use their devices in a way that risks physical harm" | **Driving safety:** no interaction required while moving, a locked in-drive UI, no speed celebration (also guideline 1.4.4 on "excessive speed") |
| **4.8** Login services | Offering Google sign-in requires an equivalent login option that limits data to name and email and allows a private email | Ship Sign in with Apple alongside Google. **SIWA needs the paid account** [A28] |

### 5.2 Google Play: three declarations

1. **Background location declaration** [G4]:
   - one feature only;
   - a video of "30 seconds or less" showing the feature activated while the app is not in use, plus the disclosure and the runtime prompt.
   - The prominent disclosure must use the word "location", say "when the app is closed" or "not in use", list all features that use it, and appear **before** the runtime prompt and in the listing.

   Play's own text lists "monitoring when a user starts or stops driving" as functionality outside the app, which needs background access rather than foreground only [G4]. Approval is still discretionary.
2. **FGS declaration** [G5]: a video per FGS feature showing "the steps the user needs to take in your app in order to trigger the feature". Location use cases include "vehicle activity tracking". **Risk:** for automatic trips, the video should show the user enabling "Automatic trips", then a drive starting on its own.
3. **Fine-location declaration** (from 27 Jan 2027) [G1][G3]: justify precise location (event placement on the road; speed corroboration).

### 5.3 Data Safety form and Privacy Nutrition Label

- **On-device-only processing is not "collection" on either store.**
  - Apple: "Data that is processed only on device is not 'collected'" [A27].
  - Play: the definition is in the September doc.
- **Labels describe the app, not a single user.** If any user can enable cloud backup, the data it uploads must be declared.
- **Apple's optional-disclosure exemption needs all of its criteria, including that collection is "infrequent" and that the user "affirmatively chooses… each time"** [A27]. Continuous route backup does not qualify.

| Data | Leaves the device when | Apple label (expected) | Play Data Safety (expected) |
|---|---|---|---|
| Precise location (routes) | Cloud backup on | Precise Location: App Functionality, linked to the user | Location > Precise: collected, optional, not shared, encrypted in transit [FB5] |
| Email, name, user ID | Sign-in | Contact info and User ID: linked | Personal info and App IDs: collected |
| Crash logs, device metadata, installation UUID | Crashlytics (always on, unless DrivePerf makes it opt-in) | Crash Data, Diagnostics, possibly Device ID [FB4] | App info and performance, Device IDs [FB5] |
| FCM token, installation ID | Push enabled | Device ID [FB4] | Device or other IDs [FB5] |
| Motion and sensor data | Only if raw trips are backed up | "Other data" or sensor data, as applicable | "App activity" or other, as applicable |

**Never put locations in Crashlytics logs or custom keys.** They would become collected location data [FB4][FB5].

### 5.4 Driving-safety expectations

- **No required interaction while moving.**
  - Auto start, auto stop.
  - While speed is above a low threshold (for example 10 km/h, a placeholder), the in-drive screen shows a large passive display with only "Stop" and an "I'm a passenger" escape.
  - Corrections happen after the trip.
- **Live Activity and Live Update content stays glanceable.** Android's guidance is "brief, timely" [G12].
- **No alerts during the drive.** No speed or "hard brake" alerts while moving; feedback comes after the trip. This also avoids guidelines 1.4.4 and 1.4.5.
- **Share cards never show top speed.** This is already in the plan.

---

## 6. Battery

### 6.1 Evidence

- **[Official] Android:** higher accuracy, frequency and lower latency each cost more battery. Background location is throttled to "a few times an hour" [G14]. Google publishes no %/h numbers.
- **[Official] Apple:** SLC and Visits are low-power, and standard location "uses the most power" [A7]. Apple publishes no %/h numbers.
- **[Field] GNSS only, 1 Hz:** 232 mJ per update (single-frequency Redmi Note 7) and 318 mJ (dual-frequency Mi 8), measured outdoors with the baseline subtracted. At 1 Hz that is **about 232–318 mW** [T5]. These are 2019 devices; current chipsets may differ.
- **[Field] Vendor figures** (method unknown):
  - Otozen: about 5% for a 1-hour trip [T8].
  - GreenRoad: about 10%/h in trip, under 1%/h out of trip [T8].
  - DriveQuant benchmark: under 3% per day on average for most SDKs, including idle time [T1].

### 6.2 [Estimate] Mid-tier phone, 1 Hz GPS plus 50 Hz IMU

**Assumptions:**

- Battery about 5,000 mAh × 3.85 V nominal ≈ **19 Wh**. This is a typical mid-tier Android spec. iPhones are smaller, about 13–17 Wh assumed, so drain is proportionally about 15–40% higher.
- IMU, CPU wake-ups, SQLite writes and fusion add about **50–200 mW** (assumption; no authoritative source found).

| Scenario | Power budget | %/h on 19 Wh | %/h on 15 Wh |
|---|---|---|---|
| Screen off, phone cool | GNSS 230–320 mW + IMU/CPU/IO 50–200 mW ≈ **0.28–0.52 W** | **1.5–2.7%** | 1.9–3.5% |
| Screen off, with overheads (cellular idle, Live Activity or notification redraws, other apps) | Add about 0.1 W (assumption) | **≈ 2–3.5%** | ≈ 2.5–4.5% |
| Screen on, app or navigation visible | Display 1–2 W at car brightness (assumption) | **≈ 7–13%** | ≈ 9–17% |

**Conclusions:**

- The plan's target of 4–6 %/h is **plausible screen-off** and **not achievable screen-on**. Phrase it as "≤ 4–6 %/h with the screen off".
- Idle drain while armed is dominated by trigger APIs (SLC, transitions, geofences), which are cheap. `CMSensorRecorder` re-armed all day is an **unknown cost** that Spike 2 must measure.

### 6.3 Duty-cycling strategies

1. **No GNSS outside trips.** Use only triggers: SLC, region and visits on iOS; transitions and geofences on Android. DriveKit takes the same approach [T2].
2. **Confirm before committing.** If speed or activity does not confirm driving within about 3 minutes, shut GPS off.
3. **Auto-pause when stationary.** Use `liveUpdates` `stationary` [A9] on iOS, and STILL transitions plus speed on Android. During long stops, drop IMU to 10 Hz or pause it.
4. **Batch, do not wake.**
   - Android: sensor FIFO `maxReportLatencyUs` of a few seconds, and location `setMaxUpdateDelayMillis` [G14]. This also keeps the app clear of the wake-lock vital [G15].
   - iOS: consider `CMSensorRecorder` polling for the accelerometer [F2], with `CMDeviceMotion` only where gyroscope or attitude is needed.
5. **Adaptive IMU rate.** 50 Hz while moving. 25 Hz on steady highway cruise, or when Low Power Mode or Battery Saver is on, or when the thermal state is `.serious` or worse. Smoothness metrics should be validated at 25 Hz as a fallback (A19).
6. **Minimal screen.** A dark, low-update in-drive screen. Encourage screen-off driving: the Live Activity or Live Update is the in-trip surface.
7. **Process at trip end, upload later.** No network during trips. Uploads go through WorkManager or `BGProcessingTask` when the phone is on Wi-Fi and charging.

---

## 7. KMP feasibility

### 7.1 What can be shared and what must be native

| Layer | Shared (KMP `commonMain`) | Native on each platform (required) |
|---|---|---|
| Trip lifecycle | **Trip state machine**: armed, candidate, recording, paused, ending, finalised, interrupted. Pure and event-driven | The *triggers*: SLC, `CLMonitor`, visits, motion activity / Activity Transition, geofences, BOOT_COMPLETED, force-stop detection |
| Background execution | Nothing | `CLServiceSession`, `CLBackgroundActivitySession`, `allowsBackgroundLocationUpdates`, FGS `location`, re-registration, watchdog notifications |
| Sensor acquisition | Data contracts (sample structs, units, monotonic clock) | `CMMotionManager`, `CMSensorRecorder`, `CLLocationUpdate` / `SensorManager`, Fused Location |
| Signal processing | **Orientation alignment, filtering, resampling, event detectors, Smoothness and Efficiency-proxy scoring, data-quality states** | Nothing |
| Storage | **Schema, DAOs, migrations, trip-file format** (protobuf or NDJSON) | Driver setup, file paths, **file-protection attributes** (iOS) and Direct Boot awareness (Android) |
| Sync | **`SyncRepository` interface, conflict rules, upload queue** | Firebase SDK calls. **No official Firebase KMP SDK exists**; the community GitLive SDK is not Google's. Recommendation: native Firebase SDKs behind the shared interface |
| UI and surfaces | View models and presentation logic (optional) | SwiftUI with Liquid Glass, Compose with M3 Expressive, **ActivityKit**, **Live Updates**, widgets, controls and tiles, permission UX |

**Verdict: confirmed.** The sensor and background layer must be native on each platform. About 50–60% of the hard logic (state machine, DSP, scoring, storage, sync rules) is shareable [Estimate, by module count in the plan's stack]. Native UI stays the right choice: Compose Multiplatform for iOS has been stable since 1.8.0 (May 2025) [K3], but it cannot render system Liquid Glass. The plan's reason holds.

### 7.2 Toolchain status (6 Nov 2026)

| Item | Status |
|---|---|
| Kotlin **2.4.0** (released 14 Jul 2026) | Swift export is **Alpha**. Suspend functions map to Swift `async`, and Flow maps to `AsyncSequence` in Swift export. CMS garbage collector is now the default. Minimum iOS target raised to **15.0**. The Kotlin/Native compiler supports **Xcode 26.4** [K1] |
| Roadmap (Aug 2026) | "Swift Export: from Alpha to Beta". Next update Feb 2027 [K2] |
| SKIE (Touchlab) | Production-proven, but its official docs list compatibility only up to 2.0.0, which looks stale. Community Slack shows Kotlin 2.4.x support lagging releases. As of Sep 2026, people were still asking for 2.4.20 support [K5]. **Pin Kotlin to a SKIE-supported version**, or evaluate KMP-NativeCoroutines as a fallback |
| Storage | SQLDelight **2.4.0** native driver [K4]. Room for KMP: the current docs show **`room3` 3.1.0-alpha03** with `BundledSQLiteDriver` recommended [G19]. **Recommendation: SQLDelight**, the plan's default, for stability |

### 7.3 Interop pitfalls and mitigations

1. **High-rate callbacks crossing the bridge.** Calling Kotlin for each sample at 50–100 Hz allocates bridged objects and can contend with the GC.
   - Buffer samples in Swift into contiguous arrays.
   - Hand Kotlin about 1 s batches (for example a `KotlinFloatArray` or a `NSData`-backed buffer).
   - Run DSP on a background dispatcher, never on the Core Motion callback queue.
2. **Coroutines to async.** Without Swift export (Alpha), suspend functions appear as completion handlers in Objective-C export, and Flows are not iterable. SKIE gives `async` and `AsyncSequence`. Define cancellation explicitly: when a Swift `Task` is cancelled, the Kotlin job must also be cancelled.
3. **Threading.** Core Location `liveUpdates` is an async sequence and Core Motion uses an `OperationQueue`. Keep the Kotlin state machine single-threaded (a confined dispatcher). Do not touch SQLite from the motion queue.
4. **Background cold start.** After a Core Location relaunch the app has seconds ([A10]: sessions are tracked "only a few seconds"; SLC launches about 10 s [F1]). **Recreate the Core Location sessions in Swift first, before initialising Koin, SQLDelight or the Kotlin/Native runtime.** Measure Kotlin framework load time in Spike 5.
5. **File protection and Direct Boot.** iOS default "until first unlock" [A25], and Android credential-encrypted storage, mean the database is unavailable before the first unlock after a reboot. Handle "DB unavailable" as a state in the shared state machine.
6. **Framework size and build time.** Kotlin/Native release linking is heavy. 2.4 reduced peak link memory [K1]. CI needs macOS runners (the plan already includes this).
7. **Firebase.** Keep Firebase out of `commonMain`. A native implementation of `SyncRepository` avoids depending on a community SDK and keeps App Check and Auth integration first-party.

---

## 8. Simulator-only iOS development before the paid account

| Capability | Simulator | **Free Personal Team on a real iPhone** | Needs paid ADP |
|---|---|---|---|
| SwiftUI and Liquid Glass UI, Swift Charts, MapKit | Yes | Yes | No |
| KMP framework integration, unit tests | Yes | Yes | No |
| Simulated location: GPX replay in tests ("replay a journey… updating the simulated location, elevation, and velocity") [A30]; Simulator Features > Location (for example Freeway Drive); `simctl location` | Yes | Not applicable (real GPS) | No |
| Background location mode | Partial. The debugger prevents suspension [F2], so suspension and termination behaviour is not realistic | **Yes.** Background modes is available to the free "Apple Developer" tier [A28] | No |
| **Core Motion** (accelerometer, gyroscope, motion activity, `CMSensorRecorder`) | **No.** "Motion support (accelerometer and gyroscope)" is unsupported (archived guide) [A31] | **Yes** | No |
| SLC, region and visit wakes; force-quit and reboot behaviour; battery and thermal | Not meaningful | **Yes** | No |
| Live Activities (local start, update, end; Dynamic Island) | Yes (no capability is required) | Yes | No |
| Live Activity **push** (push-to-start, updates) | APNs-sandbox push to the Simulator is supported on Apple silicon or T2 Macs with macOS 13+ [A22], but it needs APNs credentials | Needs APNs credentials | **Yes** (Push Notifications capability [A28]) |
| Local notifications; simulated push payloads (`simctl push` or dragging an `.apns` file) | Yes | Yes | No |
| BGTaskScheduler debug launch | **No.** The debug functions "work only on devices" [A20] | Yes | No |
| App Groups (sharing between widget or Live Activity and the app), Keychain sharing, Data Protection, HealthKit | Yes | Yes (free tier) [A28] | No |
| **Push Notifications (FCM via APNs), Sign in with Apple, App Attest (App Check), Time Sensitive notifications, Associated Domains, iCloud, In-App Purchase** | Not applicable | **No** [A28] | **Yes** |
| TestFlight, App Store Connect, App Store distribution | Not applicable | No | **Yes** [A29] |

**Free Personal Team limits:**

- "up to 10 App IDs, which expire after 7 days";
- "up to 3 devices, which expire after 7 days";
- "up to 3 apps per device" [A29].

Re-sign weekly during field tests.

**Implication.** Simulator plus a free Personal Team covers Phase-2 prototypes and **Spikes 1, 2 and 5 on real hardware**. Buy the paid account before Firebase Auth with Sign in with Apple, FCM, App Check (App Attest; use the debug provider until then) and TestFlight.

---

## 9. Firebase: Spark vs Blaze (official pricing page, 6 Nov 2026)

| Service | On Spark? | No-cost limits and notes | DrivePerf need |
|---|---|---|---|
| **Authentication** | Yes | 50K MAU. SAML/OIDC 50 MAU. **Phone Auth not on Spark** [FB1] | Apple, Google and email sign-in: OK. Sign in with Apple needs the paid Apple account |
| **Cloud Firestore** | Yes | Standard edition: 1 GiB stored, 10 GiB/month egress, 20K writes/day, 50K reads/day, 20K deletes/day [FB1] | Trip summaries and user docs. [Estimate] At about 3 writes per trip and 2 trips per user per day, 20K writes/day ≈ 3,300 daily syncing users |
| **Hosting** | Yes | 10 GB stored, 360 MB/day transfer [FB1] | Astro site and waitlist: OK |
| **Cloud Storage** | **No** | Not on Spark. Since **3 Feb 2026**, Spark projects "won't have access to any Cloud Storage buckets (including default buckets)" [FB2]. Blaze: new `*.firebasestorage.app` buckets follow Google Cloud "Always Free" pricing in US-CENTRAL1, US-EAST1 and US-WEST1 [FB2] | **Trip-file backup needs Blaze** |
| **Cloud Functions** | **No** | Blaze only [FB1] | Weekly recap jobs and push-to-start need Blaze |
| **App Check** | Yes | No cost, but subject to provider quotas. Play Integrity Standard: 10,000 calls/day. reCAPTCHA Enterprise: 10,000 assessments/month free [FB1][FB3] | OK. iOS App Attest needs the paid Apple account |
| **Cloud Messaging (FCM)** | Yes | No limits listed [FB1] | Android OK. iOS needs an APNs key (paid account) |
| **Crashlytics** | Yes | No limits listed [FB1] | OK. Keep locations out of logs |
| Remote Config | Yes | 100,000 fetches/day per project, from 1 Sep 2026 [FB1] | OK |

**Correction to the kickoff plan.** The plan's backend row is correct that Storage and Functions need Blaze. Also note that **Phone Auth** is Blaze-only, and that the Storage requirement has applied since Feb 2026 even for zero usage [FB2].

---

## 10. Parity matrix (user-facing promise × platform)

Same = equivalent experience. Degraded = works with a visible limitation. Impossible = cannot be delivered on that platform with public APIs.

| # | v1 promise (from the kickoff plan) | iOS 26/27 | Android 15–17 | Notes |
|---|---|---|---|---|
| 1 | One-tap manual start and stop | **Same** | **Same** | iOS: `CLBackgroundActivitySession` if the user declined Always [A2]. Android: FGS started from the visible UI |
| 2 | Recording continues screen-off and locked | **Same** | **Same*** | *Except where an OEM kills the app (Samsung or Xiaomi settings) [T6] |
| 3 | Automatic trip detection | **Degraded** | **Degraded** | iOS: needs Always and Background App Refresh (LPM risk); force-quit is uncertain [F1][F6]. Android: OEM killers; force-stop cancels registrations [G9]; transition latency varies by device [G13] |
| 4 | Capture the full trip from the first metre (auto mode) | **Degraded** (GNSS gap of about 0.3–1 km; accelerometer backfill possible [A16]) | **Degraded** (about 1 min transition latency [T4]; accelerometer and route gap) | Label the start segment "estimated" on both platforms |
| 5 | Live in-trip surface: **manual** trip | **Same** (Live Activity and Dynamic Island) | **Same** (Live Update, ProgressStyle or MetricStyle) | 8 h Live Activity cap on iOS [A21] |
| 6 | Live in-trip surface: **automatic** trip | **Degraded**: local start is impossible [F3]. Push-to-start is possible with paid account, server and network [A22] | **Degraded/Same**: allowed technically. Policy expects "user-initiated" [G12] | Product owner decision 1 |
| 7 | Auto-detect survives force-quit or force-stop | **Degraded** (SLC may relaunch [F1], disputed on iOS 26 [F6]) | **Impossible** until the user reopens the app (stopped state [G9]) | Watchdog notification on iOS; `wasForceStopped()` messaging on Android |
| 8 | Auto-detect survives reboot | **Degraded** (no database before first unlock [A25]; wake behaviour unverified) | **Same** after re-registration on `BOOT_COMPLETED` (FGS `location` allowed [G8]) | Android also needs credential-encrypted storage, so the first trip after a reboot waits for unlock |
| 9 | 50 Hz IMU in the background | **Same** while location runs [F2] | **Same** in the FGS (Android 9+ suppresses background sensors without one; Sep doc) | Use `CMSensorRecorder` for gap-free accelerometer data on iOS |
| 10 | Battery ≤ 4–6 %/h | **Degraded** (smaller batteries, so higher %) | **Same/Degraded** | Screen-off only [Estimate §6] |
| 11 | Smoothness, Efficiency-proxy and Events scores | **Same** | **Same** | Shared KMP code |
| 12 | Drive Story, Analysis, Routes, personal bests | **Same** | **Same** | Native UI, shared data |
| 13 | Not-driver or passenger marking | **Same** | **Same** | After the trip |
| 14 | Sign in with Apple and Google | **Same** (paid account) | **Same** (SIWA via web flow on Android) | Guideline 4.8 [A26] |
| 15 | Optional cloud backup | **Same** (Blaze for Storage) | **Same** (Blaze) | [FB2] |
| 16 | Export and delete (GPX/CSV, account deletion) | **Same** | **Same** | Required by guideline 5.1.1(v) and Play's account-deletion policy |
| 17 | Weekly recap push | **Same** (paid account, APNs; Functions on Blaze, or a local notification) | **Same** | A *local* notification needs no server or Blaze |
| 18 | One-tap start from the system UI | **Same** (Control Center control or widget via `LiveActivityIntent` [A21]) | **Same** (Quick Settings tile or widget) | Mitigates row 6 |
| 19 | Locked in-drive UI, no interaction while moving | **Same** | **Same** | App design |
| 20 | Behaviour in Low Power Mode or Battery Saver | **Degraded** (Background App Refresh off [A24]) | **Degraded** (Battery Saver throttles; OEM-specific) | Show a health-check banner |

No promise is **impossible on both** platforms. The only "impossible" cell (row 7, Android after force-stop) is an OS rule that users expect.

---

## Risks and spikes (the 5 riskiest assumptions)

| # | Assumption | Why it is risky | 1–2 day spike to prove it | Pass criteria (proposed) |
|---|---|---|---|---|
| **1** | **iOS auto-detect starts recording reliably within about 1 km on iOS 26.x and 27.x, including after force-quit and reboot** | SLC latency of at least 500 m and at most once per 5 min [A5]. Force-quit evidence conflicts [F1][F6]. The iOS 18 `CLServiceSession(.always)` rule [A10] may apply to legacy APIs. LPM and Background App Refresh effects are undocumented | Free Personal Team on 2 iPhones (one iOS 26.x, one 27.x). A Swift-only harness: SLC + `CLMonitor` parked-region + Visits, recreated `CLServiceSession(.always)`, `liveUpdates` vs legacy `startUpdatingLocation` arms, watchdog notification, a wake log with protection `none`. Drive 10+ short trips covering: normal; app force-quit; after reboot (unlocked and not); LPM on; Background App Refresh off | ≥ 9/10 normal trips detected; median missed distance ≤ 800 m; force-quit, reboot and LPM outcomes documented with OS build numbers |
| **2** | **1 Hz GPS + 50 Hz IMU in the background stays within about 4–6 %/h screen-off on mid-tier devices, without thermal throttling** | Only estimates and vendor claims exist (§6). iPhone batteries are smaller. `CMSensorRecorder` all-day cost is unknown | Same harness plus an Android FGS harness. Run 60 min of locked recording at 50 Hz and at 25 Hz, with and without the screen on, on an iPhone (non-Pro) plus a Pixel A-series plus a Galaxy A-series. Also a 24 h armed-idle run with `CMSensorRecorder` re-armed. Log battery %, `thermalState`, delivered sample rate, CPU % | Screen-off ≤ 6 %/h; armed idle ≤ 2 %/day; ≥ 98% of expected samples delivered |
| **3** | **Android auto mode survives Samsung and Xiaomi power management and Android 15 force-stop semantics, without tripping the wake-lock vital** | OEM ratings of 5/5 [T6]; PendingIntents cancelled on force-stop [G9]; the 2 h/day wake-lock threshold [G15] | Update the legacy prototype's transition plus FGS path: add a parked-location geofence (Geofence API, not an FGS; per the Jan 2027 policy [G2]), FIFO sensor batching with no manual wake lock, `BOOT_COMPLETED` and post-force-stop re-registration. Leave it on a Galaxy (One UI 8) and a Xiaomi (HyperOS) for 3–5 days of normal use, default settings first, then the OEM guidance applied | Detection rate with default vs guided settings; zero non-exempt wake-lock time during trips; re-registration verified after reboot and force-stop |
| **4** | **Both stores approve automatic background location for DrivePerf's feature set** | Play needs three declarations (background location [G4], FGS [G5], fine location from Jan 2027 [G3]) and approval is discretionary. Apple 2.5.4 and 5.1.1 [A26] | Draft the prominent-disclosure screen, listing copy, privacy-policy location section, a ≤ 30 s background-location video and the FGS video. Submit the existing Android prototype (repackaged under a test app ID) to a Play **closed testing** track with the declarations, to get a policy decision before v1. Prepare App Review notes and a demo video for iOS later (paid account) | Play decision received; any rejection reasons fed into the PRD before the Gate 2 review |
| **5** | **The KMP core ingests 50–100 Hz streams from Swift with acceptable CPU and GC behaviour and initialises fast enough on a background relaunch, with a stable Swift interop toolchain** | Swift export is Alpha [K1]; SKIE lags Kotlin releases [K5]; background relaunch windows are about 10 s [F1][A10] | A minimal KMP module (SQLDelight 2.4 + an orientation filter + Smoothness stub), exported with SKIE at the newest SKIE-supported Kotlin. Swift feeds 1 s batches at 100 Hz for 30 min. Measure CPU, memory, GC pauses, and time from `didFinishLaunching` (background relaunch) to the first DB write. Also try Swift export (Alpha) for comparison | ≤ 5% CPU at 100 Hz on an A-series non-Pro iPhone; relaunch to first write ≤ 2 s; no Kotlin version blocker |

**Lower-ranked risks to keep on the register:**

- the push-to-start round trip, if decision 1(b) is chosen;
- Live Update promotion on Samsung's own surfaces ("OEMs can enforce additional criteria" [G12]);
- the 8 h Live Activity cap on long road trips;
- Firestore write quotas at scale (§9).

---

## Sources

All sources were accessed on **2026-11-06**. Publication or update dates are given where the page shows one.

### Apple: documentation and guidelines
- [A1] Handling location updates in the background. https://developer.apple.com/documentation/corelocation/handling-location-updates-in-the-background
- [A2] `CLBackgroundActivitySession` (iOS 17.0+). https://developer.apple.com/documentation/corelocation/clbackgroundactivitysession-3mzv3
- [A3] `CLServiceSession` (iOS 18.0+). https://developer.apple.com/documentation/corelocation/clservicesession-2ddhd
- [A4] `CLLocationUpdate` (iOS 17.0+). https://developer.apple.com/documentation/corelocation/cllocationupdate
- [A5] `startMonitoringSignificantLocationChanges()`. https://developer.apple.com/documentation/corelocation/cllocationmanager/startmonitoringsignificantlocationchanges()
- [A6] `allowsBackgroundLocationUpdates`. https://developer.apple.com/documentation/corelocation/cllocationmanager/allowsbackgroundlocationupdates
- [A7] Getting the current location of a device. https://developer.apple.com/documentation/corelocation/getting-the-current-location-of-a-device
- [A8] Requesting authorization to use location services. https://developer.apple.com/documentation/corelocation/requesting-authorization-to-use-location-services
- [A9] WWDC23, "Discover streamlined location updates" (session 10180, June 2023). https://developer.apple.com/videos/play/wwdc2023/10180/
- [A10] WWDC24, "What's new in location authorization" (session 10212, June 2024). https://developer.apple.com/videos/play/wwdc2024/10212/
- [A11] `CMMotionActivityManager`. https://developer.apple.com/documentation/coremotion/cmmotionactivitymanager
- [A12] `CMMotionActivity`. https://developer.apple.com/documentation/coremotion/cmmotionactivity
- [A13] `CMMotionManager`. https://developer.apple.com/documentation/coremotion/cmmotionmanager
- [A14] Getting processed device-motion data. https://developer.apple.com/documentation/coremotion/getting-processed-device-motion-data
- [A15] `CMSensorRecorder` (iOS 9.0+). https://developer.apple.com/documentation/coremotion/cmsensorrecorder
- [A16] `recordAccelerometer(forDuration:)`. https://developer.apple.com/documentation/coremotion/cmsensorrecorder/recordaccelerometer(forduration:)
- [A17] `CMBatchedSensorManager`. https://developer.apple.com/documentation/coremotion/cmbatchedsensormanager
- [A18] `BGContinuedProcessingTask` (iOS 26.0+). https://developer.apple.com/documentation/backgroundtasks/bgcontinuedprocessingtask
- [A19] Choosing background strategies for your app. https://developer.apple.com/documentation/backgroundtasks/choosing-background-strategies-for-your-app
- [A20] Starting and terminating tasks during development. https://developer.apple.com/documentation/backgroundtasks/starting-and-terminating-tasks-during-development
- [A21] Displaying live data with Live Activities. https://developer.apple.com/documentation/activitykit/displaying-live-data-with-live-activities
- [A22] Starting and updating Live Activities with ActivityKit push notifications. https://developer.apple.com/documentation/activitykit/starting-and-updating-live-activities-with-activitykit-push-notifications
- [A23] `ProcessInfo.isLowPowerModeEnabled`. https://developer.apple.com/documentation/foundation/processinfo/islowpowermodeenabled
- [A24] Apple Support, "Use Low Power Mode to save battery life on your iPhone". https://support.apple.com/en-us/101604
- [A25] Encrypting your app's files. https://developer.apple.com/documentation/uikit/encrypting-your-app-s-files
- [A26] App Review Guidelines (2.4.2, 2.5.4, 1.4.4, 1.4.5, 4.8, 5.1.1, 5.1.2, 5.1.5). https://developer.apple.com/app-store/review/guidelines/
- [A27] App privacy details on the App Store. https://developer.apple.com/app-store/app-privacy-details/
- [A28] Supported capabilities (iOS), table with ADP, ADEP and free "Apple Developer" columns. https://developer.apple.com/help/account/reference/supported-capabilities-ios
- [A29] Compare memberships (Personal Team limits). https://developer.apple.com/support/compare-memberships/
- [A30] Simulating location in tests. https://developer.apple.com/documentation/xcode/simulating-location-in-tests
- [A31] *Archived* Simulator User Guide, "Testing and Debugging in Simulator" (the list of unsupported hardware dates from the iOS 8 era; verify on device). https://developer.apple.com/library/archive/documentation/IDEs/Conceptual/iOS_Simulator_Guide/TestingontheiOSSimulator/TestingontheiOSSimulator.html

### Apple Developer Forums (DTS and developer reports)
- [F1] "Significant change or restart app without location UIBackgroundModes key", thread 818370, March 2026. DTS reply (Argun Tekant): SLC relaunch despite force-quit; about 10 s launch window. https://developer.apple.com/forums/thread/818370
- [F2] "Does background CMDeviceMotion delivery depend on an active Core Location session?", thread 841001, August 2026. DTS replies (Kevin Elliott, Quinn). https://developer.apple.com/forums/thread/841001
- [F3] "Can't create a Live Activity from background", thread 818467, March 2026. DTS reply (Albert Pascual). https://developer.apple.com/forums/thread/818467
- [F4] "Live Activity does not work as a proxy for CLBackgroundActivitySession", thread 788647, June 2025. No replies. https://developer.apple.com/forums/thread/788647
- [F5] "Do user-terminated apps relaunch automatically for location changes?", thread 701377, February 2022. Quotes the archived Location Awareness guide. https://developer.apple.com/forums/thread/701377
- [F6] A developer report that SLC and region monitoring no longer relaunch force-quit apps on iOS 26.4.2. It surfaced through search and the Core Location tag listing, and is quoted by [T3]. **I could not open the thread or confirm it against an Apple source.** https://developer.apple.com/forums/tags/core-location

### Google: Android Developers and Play policy
- [G1] Play Console Help, "Policy announcement: 29 October 2026". https://support.google.com/googleplay/android-developer/answer/18100766?hl=en-GB
- [G2] Play Console Help, "Policy Deadlines" table (27 Jan 2027 entries: location, FGS geofencing removal, contacts). https://support.google.com/googleplay/android-developer/table/12921780
- [G3] Play Console Help, Permissions and APIs that Access Sensitive Information: Location Permissions (effective 27 Jan 2027). https://support.google.com/googleplay/android-developer/answer/16909972?hl=en
- [G4] Play Console Help, "Requesting access to location in the background". https://support.google.com/googleplay/android-developer/answer/9799150?hl=en
- [G5] Play Console Help, "Understanding Google Play's Foreground Service requirements". https://support.google.com/googleplay/android-developer/answer/13392821?hl=en
- [G6] Foreground service changes (Android 9–16). https://developer.android.com/develop/background-work/services/fgs/changes
- [G7] Restrictions on starting a foreground service from the background. https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- [G8] Android 15 behavior changes: apps targeting Android 15. https://developer.android.com/about/versions/15/behavior-changes-15
- [G9] Android 15 behavior changes: all apps (package stopped state). https://developer.android.com/about/versions/15/behavior-changes-all
- [G10] Android 17 behavior changes: apps targeting Android 17. https://developer.android.com/about/versions/17/behavior-changes-17
- [G11] Android 17 behavior changes: all apps. https://developer.android.com/about/versions/17/behavior-changes-all
- [G12] Live Updates (notifications). https://developer.android.com/develop/ui/views/notifications/live-update
- [G13] Detect when users start or end an activity (Activity Recognition Transition API). https://developer.android.com/develop/sensors-and-location/location/transitions
- [G14] Optimize location for battery. https://developer.android.com/develop/sensors-and-location/location/battery
- [G15] Excessive partial wake locks (Android vitals). https://developer.android.com/topic/performance/vitals/excessive-wakelock
- [G16] Android Developers Blog, "Battery Technical Quality Enforcement is Here" (March 2026). Seen through search results only. https://android-developers.googleblog.com/2026/03/battery-technical-quality-enforcement.html
- [G17] Meet Google Play's target API level requirement. https://developer.android.com/google/play/requirements/target-sdk
- [G18] Google Play services reference, Awareness `DetectedActivityResult` (deprecation and turn-down "as early as January 2027"). Seen through search results only. https://developers.google.com/android/reference/com/google/android/gms/awareness/snapshot/DetectedActivityResult
- [G19] Room for Kotlin Multiplatform. https://developer.android.com/kotlin/multiplatform/room

### Firebase
- [FB1] Firebase pricing (Spark vs Blaze table; Remote Config pricing effective 1 Sep 2026). https://firebase.google.com/pricing
- [FB2] FAQ, Cloud Storage for Firebase changes announced September 2024 (Blaze requirement effective 3 Feb 2026). https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024
- [FB3] Firebase App Check overview (provider quotas). https://firebase.google.com/docs/app-check
- [FB4] Firebase data collection for App Store privacy labels (iOS). https://firebase.google.com/docs/ios/app-store-data-collection
- [FB5] Firebase data disclosure for Google Play's Data safety section. https://firebase.google.com/docs/android/play-data-disclosure

### Kotlin and JetBrains
- [K1] What's new in Kotlin 2.4.0 (released 14 July 2026). https://kotlinlang.org/docs/whatsnew24.html
- [K2] Kotlin roadmap (last modified August 2026; next update February 2027). https://kotlinlang.org/docs/roadmap.html
- [K3] JetBrains blog, "Compose Multiplatform 1.8.0 Released: Compose Multiplatform for iOS Is Stable and Production-Ready" (May 2025). Seen through search results. https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/
- [K4] SQLDelight, Multiplatform SQLite (version 2.4.0 shown). https://sqldelight.github.io/sqldelight/latest/multiplatform_sqlite/
- [K5] SKIE introduction (stale compatibility statement) and kotlinlang Slack #touchlab-tools threads (May and Sep 2026, community). https://skie.touchlab.co/intro and https://slack-chats.kotlinlang.org/t/34424949/any-word-on-when-skie-will-add-support-for-kotlin-2-4-20-off

### Third-party, field and vendor sources (anecdotal)
- [T1] DriveQuant, "Connected Insurance: How to benchmark a best-in-class telematics SDK?" (9 Oct 2025, vendor benchmark). https://blog.drivequant.com/connected-insurance-how-to-benchmarking-best-telematics-sdk
- [T2] DriveQuant DriveKit docs, "Trip recording lifecycle" (no date shown). https://docs.drivequant.com/get-started-drivekit/trip-recording-lifecycle
- [T3] Dawarich, "Does Location Tracking Keep Working After You Force Quit the App?" (30 Oct 2026). https://dawarich.app/blog/location-tracking-after-force-quit
- [T4] Life360 Engineering, "Beta testing Google's new Activity Transition API" (around 2018). https://medium.com/life360-engineering/beta-testing-googles-new-activity-transition-api-c9c418d4b553
- [T5] Karki and Won, "Characterizing Power Consumption of Dual-Frequency GNSS of a Smartphone" (IEEE GLOBECOM 2020; arXiv v3, 23 Mar 2021). https://arxiv.org/html/1910.13041v3
- [T6] dontkillmyapp.com, Samsung (most recent entry 07/2024) and Xiaomi (no date). https://dontkillmyapp.com/samsung and https://dontkillmyapp.com/xiaomi
- [T7] Samsung Developers, App management (sleeping-apps deep link). Seen through search results. https://developer.samsung.com/mobile/app-management.html
- [T8] Otozen FAQ, "How much battery does Otozen app consume" (https://otozen.helpshift.com/hc/en/4-otozen-production/faq/19-how-much-battery-does-otozen-app-consume), and a GreenRoad datasheet (https://greenroadhelpcenter.com/_files/ugd/823bc7_c9852b852c6d44688a9f9cc1e1b07df9.pdf). Vendor claims, method unknown. Both seen through search results only.
- [T9] Internal: `docs/research/2026-09/android-feasibility-evidence.md` (17 Sep 2026).

**Where official and unofficial sources disagree, this document follows the official source and records the conflict.** The conflicts noted:

- force-quit relaunch: [F1] and [F5] against [F6];
- Live Activity as a location keep-alive: [A9] against [F4];
- SKIE compatibility: [K5] official page against Slack.
