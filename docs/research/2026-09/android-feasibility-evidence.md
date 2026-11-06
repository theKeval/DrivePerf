# DrivePerf — Android feasibility, permissions and privacy evidence

Research date: 17 September 2026. Scope: phone-only Android driving-performance research, with Canadian privacy considerations. This is research input, not a final specification, permission manifest, validated algorithm, legal opinion or production-readiness claim. Facts below cite official platform/policy documentation or original research; proposed experiments and product implications are explicitly identified as such.

## Findings that should shape product research

1. **Phone-only measurement is feasible; trustworthy scoring is the research problem.** A Google-authored 2022 study trained a braking detector using concurrent phone and vehicle sensor measurements and reported 0.83 PR-AUC. That is evidence for one detector on its study distribution, not 83% universal accuracy, a DrivePerf benchmark or validation of an overall driver score. [Original research](https://research.google/pubs/smartphone-based-hard-braking-events-detection-at-scale-for-road-safety-services/)
2. **User-started and automatically detected trips are different Android products.** A location foreground service started while the app is visible can continue after the screen turns off. Automatic start must satisfy both foreground-service launch rules and location-access rules. An activity-transition event can address the first without satisfying the second. [Location access](https://developer.android.com/develop/sensors-and-location/location/permissions), [background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
3. **“In vehicle” does not establish “driver.”** Google's activity class identifies the device's activity, not its seat or who holds it. Driver/passenger attribution, shared cars and transit require independent validation and a correction experience. [DetectedActivity](https://developers.google.com/android/reference/com/google/android/gms/location/DetectedActivity)
4. **Phone use should initially be described as an estimate of handling or interaction.** Ordinary app touch callbacks, usage events and cellular call state describe different, incomplete signals; none alone establishes that the driver was distracted. See the phone-use section below.
5. **Raw accelerometer axes are phone axes.** Gravity removal, phone orientation and alignment to vehicle motion must precede interpretation of longitudinal/lateral acceleration. A phone sliding in a cup holder can produce a large event without the car making a corresponding maneuver. [Android motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion), [original orientation study](https://www.mdpi.com/1424-8220/18/8/2624)

## Data and sensors to investigate

“Core candidate” means needed for the proposed reliable combined measurement approach, not an already selected minimum-device specification. Every optional signal should justify its incremental value and battery/privacy cost.

| Signal | Research role | Limits and proposed handling |
|---|---|---|
| Accelerometer, three axes, m/s² — core candidate | Acceleration/braking, lateral motion, road-impact and handling features | Includes gravity; phone-frame data cannot directly be called vehicle acceleration. Keep timestamps and measured sampling intervals. Compare raw-plus-fusion with Android linear acceleration. [Motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion) |
| Gyroscope, three axes, rad/s — strongly preferred | Rotation, cornering and phone movement; orientation fusion | Gyro measures the phone, not steering angle. Investigate a lower-confidence capability tier if missing. [Motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion) |
| Gravity / rotation vector / game rotation vector — derived candidates | Remove gravity and track orientation | These may be software/composite sensors. Game rotation vector avoids magnetic-field dependence but does not give a stable north heading. Device implementation differences require testing. [Position sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position) |
| Precise fused location or GNSS — core candidate | Route, distance, trip boundaries and corroborating speed/bearing | Save fix age, provider and available uncertainty fields, not coordinates alone. Approximate permission is unsuitable for road-level event placement; provide history/demo and clearly degraded functionality if precise access is denied. [Permissions](https://developer.android.com/develop/sensors-and-location/location/permissions), [Location API](https://developer.android.com/reference/android/location/Location) |
| Speed and bearing in location fixes — core candidate | Speed changes and direction of travel | Check `hasSpeed`, `hasBearing` and corresponding accuracy flags. Reported speed can incorporate Doppler and be better than differentiating noisy positions. Bearing is direction of travel, not phone compass orientation. [Location API](https://developer.android.com/reference/android/location/Location) |
| Monotonic timestamps / quality diagnostics — core candidate | Synchronize streams, identify dropped data and distinguish uncertain events | Use elapsed-realtime timestamps for interval comparisons; wall time can change. Save phone model, OS, sensor availability, sample gaps and permission state for consented quality analysis. [Location timing](https://developer.android.com/reference/android/location/Location) |
| Activity transition — optional for manual trips, likely useful for automatic mode | Low-power candidate vehicle-entry/exit trigger | Requires Google Play services API and Activity Recognition permission; has no driver identity output. Research trigger latency and missed short trips. [Transition API](https://developer.android.com/develop/sensors-and-location/location/transitions) |
| Magnetometer — optional | Heading/orientation assistance | Metal, magnetic mounts and calibration are failure modes to test; avoid making it a single source of truth. [Position sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position) |
| GNSS altitude / barometer — optional | Hill/grade-context experiments | Location altitude is optional and has its own uncertainty; WGS84 altitude and mean-sea-level altitude differ. Pressure sensors are not universally present. Do not make altitude required for basic driving feedback. [Location API](https://developer.android.com/reference/android/location/Location), [environment sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_environment) |
| Screen/keyguard state, phone motion, own-app interactions — optional | Handling and interaction hypotheses | Screen on may be navigation; movement may be passenger handling, mount vibration or a loose phone. Estimate with uncertainty; do not imply observation of every tap in other apps. [Input API scope](https://developer.android.com/develop/ui/views/touch-and-input/input-events) |
| Paired-car Bluetooth — optional | Vehicle association and trip-start corroboration | Connection indicates a nearby/connected car, not driver identity. Requires feature-specific permissions; do not require a dongle for the phone-only product. [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions) |
| Road class / posted speed limits — external optional data | Contextualize speed and maneuvers | Research map licensing, coverage, matching confidence, update frequency and variable/school/construction limits. GPS provides speed, not the applicable legal speed limit. This is a research implication, not a selected provider. |
| Battery / charging / thermal and app health — supporting diagnostics | Quantify operating cost and missing-trip causes | Proposed engineering telemetry; collect minimally and keep raw trip locations out of crash logs. |

The platform does not guarantee particular sensors on every device. Inspect capabilities at runtime. Android 9+ suppresses ordinary background sensor delivery, so active trip capture needs foreground execution. On Android 12+ target levels, ordinary accelerometer/gyro listeners are capped at 200 Hz unless the high-sampling permission is declared; direct channels have separate limits. These caps are not sampling-rate recommendations. [Sensor availability and restrictions](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)

## Behaviors: observation versus interpretation

The following is a proposed evaluation framework, not validated classification logic.

| Requested behavior | Candidate observable evidence | Important ambiguity |
|---|---|---|
| Hard braking | Vehicle-aligned deceleration sustained over a window, corroborated by speed change | Avoiding a collision can require a hard brake. A valid detection does not prove poor judgment. |
| Hard acceleration | Sustained longitudinal acceleration plus speed trajectory | Phone pickup, steep grade, vibration and vehicle type can confound a fixed threshold. |
| Harsh cornering | Lateral acceleration with heading/yaw change and speed context | Roundabouts, road camber, slip roads and phone rotation can cause misleading interpretations. |
| Phone handling | Rotation/motion distinct from car motion with interaction context | Handling is not necessarily by the driver. A mounted phone can be used without being moved. |
| Speeding | Reliable speed compared with reliable, time-appropriate road-limit data | Wrong road match, ramps, parallel roads or stale limits can falsely penalize a driver. |
| Overall drive performance | Explainable aggregation of supported events and observation coverage | Weighting, exposure normalization, context and uncertainty require validation; a smooth trip is not proof of safe driving. |

Research implication: show event evidence, confidence and a reasoned correction path; support “insufficient data” and “not my driving” outcomes. Avoid a definitive safe/unsafe label until evidence supports the claim. Phone-only motion/location cannot directly measure following distance, eyes on road, lane discipline, signaling, intoxication or responsibility for an emergency maneuver. Those would require additional instrumentation and separate validation.

## Android permission and access matrix

| Capability | Permission or access mechanism | Research recommendation |
|---|---|---|
| Trip route / speed context | `ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION`, runtime | Explain precise access at trip setup; accommodate approximate/denied state without silently fabricating detailed scores. Request fine/coarse together where required by current Android flow. [Location access](https://developer.android.com/develop/sensors-and-location/location/permissions) |
| Active trip with screen off | Manifest `FOREGROUND_SERVICE`; `location` service type; `FOREGROUND_SERVICE_LOCATION` for target 34+ | Start from visible UI for manual mode; stop when the trip purpose ends. This is separate from background-location access. [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types) |
| Automatic detection / background location | `ACCESS_BACKGROUND_LOCATION`, Android 10+; separate staged consent flow | Investigate only with an explicit automatic-trips feature and Play justification. Android 11+ uses a settings flow for “all the time”; background precision cannot exceed foreground precision. [Background permission](https://developer.android.com/develop/sensors-and-location/location/permissions/background) |
| Vehicle activity transitions | `ACTIVITY_RECOGNITION`, runtime on Android 10+ | Optional for manual recording. Legacy Play services permission applies for older devices if supported. [Official codelab](https://developer.android.com/codelabs/activity-recognition-transition) |
| Ordinary accelerometer / gyro / rotation data | No ordinary runtime motion permission | Activity Recognition is not a general permission for reading accelerometer/gyro. Runtime availability and foreground restrictions still apply. More than 200 Hz listener access needs manifest `HIGH_SAMPLING_RATE_SENSORS`; no demonstrated need yet. [Sensor overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview) |
| Trip recording/review notifications | `POST_NOTIFICATIONS`, runtime on Android 13+ | Explain status and controls. Grant is not technically required to start an FGS, but an FGS notification must still be supplied. If denied, FGS notices appear in Task Manager rather than the drawer. [Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission) |
| Optional car connection | Android 12+ `BLUETOOTH_CONNECT`; `BLUETOOTH_SCAN` only if scanning | Nearby Devices prompt. Earlier Android scans may require location/background access. Do not copy `neverForLocation` flags without verifying actual use. [Bluetooth access](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions) |
| Other-app usage history | `PACKAGE_USAGE_STATS` plus user-granted Usage Access in Settings | Special access, not a normal runtime prompt. Research separately; unnecessary for basic motion scoring. Avoid collecting app inventory when a coarse local interaction signal suffices. [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager) |
| Cellular call state | `READ_PHONE_STATE`, runtime | Optional separate hypothesis; do not require for the core. Telephony call states do not cover all VoIP use or establish handset holder/attention. [CallStateListener](https://developer.android.com/reference/android/telephony/TelephonyCallback.CallStateListener) |
| Accessibility service | User-enabled accessibility access plus Play declarations | Do not make the default solution for phone-use tracking. A general driving app is not an accessibility tool; non-tool uses require disclosures, affirmative consent and policy review. [Accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en) |

Proposed exclusion from the core research scope: contacts, SMS/message content, call logs, microphone, camera, broad storage and all-package visibility. None is required to investigate acceleration/braking/cornering. Call logs/SMS and broad package visibility carry separate Play restrictions; they must not be requested merely to make the score appear more comprehensive. [Sensitive API policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en)

### Background execution: test two distinct operating modes

**Manual recording hypothesis:** the user starts a trip while the app is visible; the app records through an appropriately declared location FGS and ends recording at the end of the user-requested trip. This can retain location access when minimized or screen-off using foreground location permission. Play also requires this foreground-only FGS use to continue a user-initiated action and end when that purpose completes. [Android foreground location](https://developer.android.com/develop/sensors-and-location/location/permissions), [current Play policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en)

**Automatic recording hypothesis:** Activity Recognition transitions or an appropriate supported trigger propose a trip. Android 12+ restricts background FGS starts; transitions are an exception, but location's while-in-use rule remains a separate gate. With background location granted, an eligible background start can access location. Test the complete permission/trigger/OS combination rather than interpreting either grant as permission to run continuously. [FGS restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [location FGS prerequisites](https://developer.android.com/develop/background-work/services/fgs/service-types)

Android 13+ lets users stop the entire FGS-running app in Task Manager without a callback. Design recovery to mark an interrupted trip, not fill the missing period with a perfect score. [User stopping](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)

Android 15's new time limits apply to specified types such as `dataSync` and `mediaProcessing`; do not incorrectly apply a blanket six-hour limit to location tracking. Android 16 subjects jobs launched from an FGS, including WorkManager jobs, to their normal quotas. Research upload/reprocessing separately from ongoing sensor capture. [Version changes](https://developer.android.com/develop/background-work/services/fgs/changes)

Android 17/API 37 is already represented in the official documentation reviewed. A new session-based location button is relevant to future permission UX, but Play's expanded location policy is explicitly dated **27 January 2027** in the policy preview. Do not present it as already enforced on this research date or assume a one-time/session button replaces ongoing trip-tracking access. Recheck exact target SDK and policies during specification work. [Location button](https://developer.android.com/guide/topics/permissions/private-alternatives/location-button), [dated policy preview](https://support.google.com/googleplay/android-developer/answer/16909972?hl=en)

## Phone-use measurement needs its own feasibility gate

- Own-app input APIs expose events in the application's views/window. They are not a general feed of every cross-app tap. [Input events](https://developer.android.com/develop/ui/views/touch-and-input/input-events)
- UsageStats can reveal package usage/events after special user approval. A foreground navigation app is not proof of active touch, driver handling or cognitive distraction. Treat those distinctions as inference limits. [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
- Telephony callbacks report cellular call state for a subscription and require `READ_PHONE_STATE`; they are not a universal all-app calling monitor. [Call state](https://developer.android.com/reference/android/telephony/TelephonyCallback.CallStateListener)
- Accessibility can expose more events, but a driving app cannot truthfully claim `isAccessibilityTool` simply to bypass disclosure. Feasibility includes privacy cost, approval risk and user willingness, not only technical access. [Play accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)

Proposed research decision: evaluate a privacy-minimized handling estimate first, clearly separate handling from screen-on time and call time, and permit event correction. Do not promise complete phone-use detection or driver attribution before a labeled test demonstrates it.

## Privacy and distribution implications

Canada is a relevant initial lens because the idea references TD Insurance, not a confirmed launch-market decision. Determine applicable federal/provincial and other launch-market requirements in the next privacy workstream.

The OPC's meaningful-consent guidance requires clear explanation of the information collected, recipients, purposes and material risks/consequences, with choice for nonessential processing and support for withdrawal. Its mobile guidance emphasizes collecting only data needed for identified present purposes, securing it and deleting it when no longer needed. A system permission prompt is only one part of an understandable consent experience. [OPC consent](https://www.priv.gc.ca/en/privacy-topics/privacy-laws-in-canada/the-personal-information-protection-and-electronic-documents-act-pipeda/p_principle/principles/p_consent/), [OPC mobile guidance](https://www.priv.gc.ca/media/1979/gd_app_201210_e.pdf)

Proposed product/privacy hypotheses to test:

1. Local signal processing by default; separate optional account/sync and optional research-data donation from basic trip recording.
2. No collection of message content, precise cross-app activity history or contacts for ordinary driving feedback.
3. User-visible recording state, easy pause, delete-trip, driver/passenger correction and controls for route-history retention.
4. Separate retention periods for raw sensor windows, precise route history, summaries and diagnostics. Raw traces should not be retained indefinitely just because future ML work might use them.
5. Encrypt local sensitive data and transmission as appropriate; minimize staff access; separate identity from trip IDs; redact locations from logs; include SDKs, backups and export flows in data mapping.
6. Any shared route should support start/end-area hiding. Treat this as exposure reduction, not anonymization: route patterns can remain revealing.
7. Confirm whether novice/teen drivers are a target segment before designing age and consent flows. Family tracking/sharing is a separate purpose with different expectations from self-coaching.

For Play background-location review, automatic trip recording must be a meaningful core feature clearly reflected in the listing. The submission needs a declaration, demonstration video, prominent in-app disclosure before the location prompt, and privacy policy. Approval is not guaranteed because competing apps have similar features. [Background-location review](https://support.google.com/googleplay/android-developer/answer/9799150?hl=en)

Data Safety treats transmission off-device as collection, including by embedded SDKs. Solely on-device processing is outside that collection definition, but remains sensitive processing to explain. Pseudonymous transmission is still reportable; provider and sharing classifications require checking the actual data flow. A “no data shared” store label is not proof that nothing leaves the device. [Data Safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)

If account creation is offered, Play requires an in-app deletion path and an external web resource to request deletion of the account and associated data. This must include relevant service-provider deletion handling, with any justified retention explained. [Account deletion](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en)

## Research experiments before feature specifications

The following are proposed experiments; numerical settings are starting comparisons, not claims of optimality.

**Sampling/battery experiment:** compare 25, 50 and 100 Hz inertial capture and roughly 1 Hz versus slower/adaptive location during confirmed driving. Measure actual delivered intervals and battery energy over repeatable trips plus 24-hour idle standby. Separate screen/navigation/charging conditions. Avoid constant high-accuracy GPS while waiting for a trip. Android documents accuracy, frequency and delivery latency as battery tradeoffs; ordinary background location may be throttled to only a few updates per hour. [Battery guidance](https://developer.android.com/develop/sensors-and-location/location/battery)

**Detection experiment:** compare simple, interpretable baselines with fusion/model alternatives against a common labeled dataset. Capture reference vehicle speed/acceleration or calibrated reference IMU/GNSS and time-synchronized event annotations. A phone-only customer product may still use extra instrumentation for validation. Google’s braking study demonstrates the value of concurrent vehicle ground truth. [Original study](https://research.google/pubs/smartphone-based-hard-braking-events-detection-at-scale-for-road-safety-services/)

**Dataset caution:** STRIDE includes road anomalies as well as driving styles, useful for testing confounds, and reports a roughly 60–99 Hz observed sampling range. It involved three drivers; it is a research seed, not representative validation across countries, devices and placements. Review licenses and consent before reuse. [Original dataset paper](https://www.nature.com/articles/s41597-024-04193-0)

| Test dimension | Minimum research coverage | Proposed result to report |
|---|---|---|
| Phone placement | Fixed mount, pocket, bag, cup holder; placement changes | Event precision/recall by placement; unscorable fraction |
| Vehicle/road | Small/large vehicles, EV regenerative braking, rough roads, hills, ramps, roundabouts | False event rate per driving hour/100 km and context |
| Location | Urban canyon, tunnels, parking garages, rural loss, approximate permission | Route completeness, start delay, quality-gated exclusions |
| Role | Driver, passenger, public transit, rideshare, shared vehicles | Attribution errors and correction burden; do not merge into detector accuracy |
| Device/runtime | Several manufacturers and price tiers, current OS levels, battery saver, screen-off, revocation, force-stop/reboot | Missed/partial trip rate, cold-start latency, battery and recovery |
| Phone interaction | Navigation visible, hands-free call, phone repositioned, passenger handling, mounted taps | Separate handling/interaction false positives and false negatives |
| Model generalization | Held-out drivers, phones, cars, routes and days | Precision/recall and calibration outside training conditions |
| Trust/UX | Explain event, correct driver role, pause, delete and understand permission use | Task completion, comprehension, willingness to keep tracking |

Use event-level precision/recall, false alarms per exposure, trip completeness, score stability and battery cost; overall classification accuracy alone can hide a detector that misses rare harsh events. Establish operating thresholds with ground truth and user tolerance. Only then decide whether a POC may score an event, flag it for review or omit it. Validation should use controlled environments for deliberate extreme maneuvers; ordinary-road research must not ask participants to create dangerous events.

## Recommended next research phase

Run **Android user validation plus a device-based competitor benchmark and technical feasibility study** before committing POC specifications. Its deliverables should be: a labeled review taxonomy checked through interviews; hands-on competitor task comparisons; a permissions/consent comprehension study; a device/placement test dataset; detector/attribution/battery results with confidence intervals; and a privacy/data-flow map. Then conduct the separate iOS market/platform study requested by the founder, before fixing cross-platform promises.

Open decisions: target driver segment and launch countries; whether routine mounting is acceptable; manual versus automatic recording expectations; acceptable missed-trip/false-event rates; whether scores aim at smoothness, self-coaching or validated crash-risk prediction; how much raw data users would consent to retain; willingness to pay without insurance rewards. These determine the later POC scope.
