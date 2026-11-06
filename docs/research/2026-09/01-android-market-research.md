# DrivePerf — Android market research

**17 September 2026 · Phase 1, revised for the confirmed audiences**  
**Product:** DrivePerf · **Domain:** driveperf.com · **Android identity:** com.driveperf

## Start here: the revised direction

DrivePerf serves **two audiences from the beginning**:

| Audience | The job to solve | What they should get from a drive |
|---|---|---|
| Driving/car enthusiasts, experienced users and data enthusiasts | Understand the drive in depth, inspect performance and compare sessions. | Rich statistics, synchronized graphs/maps, event evidence, data quality and useful exports. An enthusiast can be interested in ordinary road trips without racing. |
| Drivers seeking efficiency or improvement, including new drivers | Understand what went well, what could improve and how to practice. | A clear trip explanation, defensible efficiency indicators, progress and a small number of actionable coaching suggestions. |

**Proposed product principle: one recorded drive, two levels of explanation.** A concise coaching view and an expandable analysis view should use the same underlying evidence. Users can switch depth; they should not have to choose between two disconnected products.

Insurance products are **metric, feature and operating-constraint benchmarks only**. Early versions have no insurer branding, premium/discount claims, integrations or insurer-specific user journey. Insurance incentives are not the primary motivation to validate. This corrects the emphasis of the first research pass.

The wider research changes the competitive conclusion: independent apps already offer detailed sensor analytics, polished trip replay, exports, local storage and advertised AI coaching. The opportunity is to prove a better combination of **measurement quality, useful depth, understandable coaching and predictable privacy**. None of these is assumed unique merely because DrivePerf offers it.

## The Android products that matter most now

Public listings confirm discoverability, not Canadian installation eligibility or independently measured accuracy. Official descriptions are vendor claims. Pricing, enabled features and hardware requirements need hands-on confirmation. This is an expanded search, not a claim to have found every Android app.

### The four requested products

| Product and exact Android identity | Relevant benchmark | Implication for DrivePerf |
|---|---|---|
| **Driving Tracker: GPS Trip Log**, Richard Bláha, `com.richardblaha.drivingtracker` — [site](https://drivingtrackerapp.com/), [Play](https://play.google.com/store/apps/details?id=com.richardblaha.drivingtracker) | Sensor statistics, speed/altitude plots, driving forces, harsh events, route analysis and exports; premium automatic recording. | A close direct competitor for the detailed-analysis audience. Test interpretation and data validity, not only whether charts exist. |
| **Open Road: Drive Tracker**, OpenRoad.App, `com.mikita.openroad` — [site](https://www.openroaddrive.com/), [Play](https://play.google.com/store/apps/details?id=com.mikita.openroad&hl=en) | Trip replay, garage trends, smoothness and social/convoy features. | Benchmark modern enthusiast presentation; carefully separate capture, backup and public visibility. |
| **Zenroad**, Damoov, `com.telematicssdk.zenroad` — [product](https://damoov.com/telematics-app/), [Play](https://play.google.com/store/apps/details?id=com.telematicssdk.zenroad) | Broad behavior/eco scoring and several recording modes. | Strong SDK/reference benchmark; consumer demo and configurable commercial platform are different comparisons. |
| **AutoBeacon**, SenSight, `com.sensight.beaconapp` — [product](https://www.autowiz.in/roadsafety.html), [Play](https://play.google.com/store/apps/details?id=com.sensight.beaconapp&hl=en) | Automatic trips, behavior sub-scores, mapped events, trends and coaching; optional beacon. | Study phone-only measurement and correction flows; verify which supplier features work in independent consumer signup. |

**OpenRoad naming remains important:** [CMT's Openroad: Safety on the Road](https://play.google.com/store/apps/details?id=com.gotruemotion.openroad) is a separate crash-response/family product. The enthusiast app above is the likely intended match, so both are documented instead of silently conflating them.

### Additional indie and enthusiast competitors

| Group | Products worth studying | Established capabilities to compare |
|---|---|---|
| Everyday analytics/coaching overlap | [TripRank](https://play.google.com/store/apps/details?id=com.trippulse.triprank), [DOWNFORCE](https://play.google.com/store/apps/details?id=de.downforce.app), [Zero Lap](https://play.google.com/store/apps/details?id=com.fenix.zerolap), [OpenRank](https://play.google.com/store/apps/details?id=fr.autosuivi.openrank), [PACE](https://play.google.com/store/apps/details?id=com.bergeritsolutions.pace) | Various combinations of G-force, performance timing, smoothness/eco scores, replay, garage, exports and advertised automated analysis. Exact combinations and feature gates are in the appendix. |
| Learner coaching | [DriveSidekick](https://play.google.com/store/apps/details?id=com.drivesidekick.app) | Practice tracking, learner progress and event replay; UK syllabus relevance must not be assumed for Canada. |
| Lightweight drive journals | [DriveTracker/iAlex5](https://play.google.com/store/apps/details?id=com.ialex5.driveTracker), [DriveTrack/Franko Prifti](https://play.google.com/store/apps/details?id=io.github.frankoprifti.drivetrack) | Capture, route history and statistics; distinct products from DrivingTrackerApp.com. |
| Mature lap/telemetry apps | [RaceChrono](https://play.google.com/store/apps/details?id=com.racechrono.app), [TrackAddict](https://play.google.com/store/apps/details?id=com.hptuners.trackaddict), [Harry's LapTimer](https://play.google.com/store/apps/details?id=com.harrys.laptimerpro), [Lap Legend](https://play.google.com/store/apps/details?id=com.dauntlessdevices.laplegend) | Lap/sector comparison, graphs, video and optional GPS/vehicle telemetry; feature depth differs by edition. |
| Hardware-oriented performance | [dragy·Lap](https://play.google.com/store/apps/details?id=com.dragylap), [RaceBox](https://play.google.com/store/apps/details?id=pro.RaceBox.androidapp), [CircuitStorm](https://play.google.com/store/apps/details?id=com.petrel.circuitstorm) | More demanding timing/telemetry workflows; hardware is required for Dragy/RaceBox and other channels depend on supported external equipment. |
| Narrow performance/modeling tools | [GPS Race Timer](https://play.google.com/store/apps/details?id=com.thsolutions.Race2), [PerfExpert](https://play.google.com/store/apps/details?id=com.perfexpert), [RaceBuddy](https://play.google.com/store/apps/details?id=rs.mousecode.racebuddy) | Acceleration timing, modeled power or richer track analysis. RaceBuddy's phone-only Android behavior remains unresolved between official sources. |

Commercial models span free, ads, one-time purchases, subscriptions, SDK services and hardware purchases. Do not choose DrivePerf's pricing from app download counts or compare an app-only price with a hardware system's total cost. Exact observed prices, source dates and eligibility limits are in the [indie evidence](indie-driving-apps-expanded-evidence.md) and [enthusiast evidence](enthusiast-track-and-pdr-evidence.md).

The previous comparisons—including DriveSmart, Rudi, OtoZen, Life360, DriveScore, DriveWell Go and DriveQuant—remain useful, but move behind the more directly relevant field above. Insurance products remain in their [benchmark appendix](insurance-benchmarks-evidence.md). Prerelease Roadsy/Oonak entries remain a watchlist, not verified public alternatives.

## Metrics: depth without overstating what a phone knows

These are candidate research groups, **not approved features, paid tiers or a POC specification**.

| Group | Metrics to investigate | Critical qualification |
|---|---|---|
| Trip fundamentals | Distance, duration, moving/stopped time, route, speed distribution, altitude profile, recording completeness. | Define sampling, gaps and moving-time rules; uncertainty must accompany location-derived statistics. |
| Enthusiast motion analysis | Longitudinal/lateral acceleration, deceleration, jerk, G plots, turn profiles, distributions and event windows. | Correct gravity/orientation and distinguish phone movement from vehicle motion. Peak values are particularly sensitive to noise. |
| Comparable-drive analysis | Overlay equivalent route segments; compare speed/acceleration patterns, smoothness and consistency. | Align by location/distance/context; ordinary-road comparisons should not reward fastest arrival. |
| Coaching | What went well, one improvement focus, event explanation, later progress on a similar drive. | A detected hard brake does not establish poor judgment. Generic advice is not evidence of improvement. |
| Efficient-driving indicators | Smoothness, repeated acceleration/braking, stop/start patterns and speed consistency. | These are behavior proxies. GPS cannot directly establish engine idling, liters consumed or battery energy used. |
| Closed-course performance | Laps/sectors, deltas, repeatability, speed traces, video alignment, braking/turn comparisons. | Separate context and validation; phone timing must not imply dedicated GNSS accuracy. |
| Optional vehicle data | RPM, throttle, gear, braking input, steering, fuel/energy and temperatures where supported. | Requires compatible external/OEM data. Generic OBD availability does not guarantee every channel or useful sample rates. |

Label every metric's provenance: **observed, derived, modeled, user-supplied or unavailable**. Missing throttle data is not zero throttle. An “efficiency score” is not measured fuel economy. A lap-time gain and safer city driving are different outcomes; avoid one universal driver-skill score that blends them.

Technical grounding: [Android motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion), [Android Location API](https://developer.android.com/reference/android/location/Location), [OBD/CAN limits](https://www.petreldata.com/obd-ii-and-can-bus-extracting-performance-data-for-motorsports/). Detailed sensor research is retained in the [Android feasibility appendix](android-feasibility-evidence.md).

## What reviews and documented limitations suggest

Across the original and expanded evidence, reported problems include unreliable capture, inaccurate-looking values, calibration confusion, battery cost, lost sessions, hardware compatibility and difficult controls. Positive reviews value accessible graphs, exports, lap explanations and mobile review. These selected anecdotes do not establish prevalence or prove current defects.

| Evidence example | Design/research response |
|---|---|
| Open Road reviewer, 5 April 2026, reports understated motorway speed. | Compare capture sources and quantify error before presenting precise statistics. |
| TrackAddict reviewer, 25 September 2024, found line/speed/G data useful for understanding lap differences. | Connect each graph to a concrete question about the drive. |
| RaceChrono review edited 19 March 2026 improved after a GPS receiver change. | Show source quality and compatibility; distinguish sensor problems from scoring. |
| GPS Race Timer reviewer, 25 August 2026, reports implausible acceleration results. | Test repeatability against a reference; displayed decimal places are not accuracy. |
| PerfExpert review, 28 August 2026, reports a clipped control; developer says it was fixed. | Test large fonts/small screens; do not classify an acknowledged fix as an unresolved defect. |

Dated reviewer identifiers, sources, positive examples and developer responses are retained in the [indie review log](indie-driving-apps-expanded-evidence.md#4-public-android-review-examples-and-limits) and [enthusiast review log](enthusiast-track-and-pdr-evidence.md#dated-android-review-evidence). DrivingTracker's website testimonials are vendor-selected; usable independently retrieved Android reviews were not available for every small app.

## UI direction: coach summary with an analysis workspace beneath it

This is a proposal to test, not a completed visual audit or proof that competitors look old.

1. **Ready to record:** clear manual/automatic mode, location and sensor readiness, mount guidance where needed, and a visible data-quality state.
2. **After the drive:** a concise summary of what happened and one useful observation. Results should be available independently of sharing, exporting or editing a trip.
3. **Explore the evidence:** synchronized map, timeline and plots; selectable channels, units, filters and event details. Preserve expert controls without placing every metric on the opening screen.
4. **Compare and improve:** compare equivalent sessions, explain changes and distinguish road coaching from closed-course performance. A user can annotate context without rewriting raw measurements.
5. **Control the data:** recording, retention, optional backup, diagnostics, export and public sharing are separate choices.

Use accessible typography, chart labels/text alternatives, light/dark support and color-independent status. Android recommends at least 48 dp touch targets. A modern UI must remain usable at large font sizes and with TalkBack. [Android accessibility](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility).

Keep road recording quiet and usable with the screen off. Examine live track feedback only in its own usability and safety study. Do not put speed competitions or pressure to avoid necessary braking into the ordinary-road coaching experience.

## Sensors, permissions and privacy carried forward

**Core candidate signals:** precise location, accelerometer, gyroscope, orientation/gravity, synchronized timestamps and quality/coverage information. Activity recognition supports automatic trip candidates. Altitude, barometer, magnetometer and car Bluetooth are optional signals to justify through experiments. External GNSS/OBD are future options, not a prerequisite silently added to the phone-first proposition.

**Permission distinction:** a user-started trip can continue in an appropriately declared location foreground service; automatic background starts must satisfy both service-start restrictions and location-access rules. Ordinary accelerometer/gyroscope readings do not need the Activity Recognition runtime permission. Notifications, Bluetooth and any special-access features each have different rules. Phone-handling estimates do not establish that the driver touched the phone. [Background service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [complete permission matrix](android-feasibility-evidence.md#android-permission-and-access-matrix).

**Privacy direction:** process locally where useful, collect only justified signals, separate optional sync/research/diagnostics, provide explicit retention/export/deletion and keep public location sharing off by default. This is more precise than “no data sold.” For example, Open Road's policy distinguishes local recording from default signed-in raw-trip synchronization and anonymous nearby exact-location sharing. That illustrates why capture, backup and visibility controls need separate explanations. [Open Road policy](https://www.openroaddrive.com/privacy).

Maps, crash reporting, SDKs and Android backup must be included in the actual data-flow review. Canadian privacy obligations and Google Play rules still apply; recheck them at specification/release time. The [technical appendix](android-feasibility-evidence.md#privacy-and-distribution-implications) retains regulator and policy sources, including the distinction between current rules and the January 2027 location-policy preview.

## Future direction: learn from Cosworth PDR

Cosworth's **AliveDrive** recording system is branded **PDR** in GM vehicles and **DXR** in Dodge vehicles. Its ecosystem includes generation-specific desktop analysis tools and the **AliveDrive Race** Android companion. These products combine vehicle-integrated telemetry with video/GPS and are not equivalent to phone-only recording. [Cosworth analysis tools](https://www.cosworth.com/motorsport/support/alivedrive-data-analysis-tools/), [Android companion](https://play.google.com/store/apps/details?id=com.cosworth.alivedrive.race.phone.droid).

The future ambition to investigate is the workflow: **record → align signals and events → compare → explain → practice → evaluate progress**. For city driving, this means useful efficiency/smoothness coaching; for closed-course work, it may mean repeatable session and corner analysis. Optional imports, external sensors and vehicle integrations can expand what is measurable later. No PDR replacement, compatible import format, OEM partnership or equivalent accuracy is promised.

The [PDR and enthusiast appendix](enthusiast-track-and-pdr-evidence.md#cosworth-pdr-future-direction-comparator) separates Cosworth Toolbox, AliveDrive Desktop, AliveDrive Race and Pi Toolbox so future research starts from the right product generation.

## Recommended next research phase

Stay in discovery; do not turn this list into a feature specification yet. Research the **two confirmed audiences**, rather than revisiting whether insurance incentives should be the product.

- Interview everyday-road enthusiasts, track-day/data users, efficiency-focused drivers and new drivers as distinct subgroups.
- Benchmark DrivingTracker, Open Road, Zenroad and AutoBeacon, then select an advanced lap tool and a learner tool to test both ends of the intended experience.
- Test a common sample drive shown as a coach summary and as detailed analytics. Measure interpretation, discoverability and value, not only visual preference.
- Validate sensor accuracy, comparison repeatability, battery cost and privacy understanding before committing metric claims.
- Research iPhone separately later; then consolidate commercial/operational findings and write the POC specification.

The [next-phase plan](02-next-phase-research-plan.md) now uses these audiences and tasks. Feature priorities, paid tiers, detector thresholds, score weights and launch scope remain undecided.

## Optional supporting detail

- [Expanded indie-app evidence](indie-driving-apps-expanded-evidence.md): requested apps, neighboring products, privacy and review details.
- [Enthusiast, track and PDR evidence](enthusiast-track-and-pdr-evidence.md): richer telemetry, hardware, prices and review details.
- [Android feasibility evidence](android-feasibility-evidence.md): sensors, permissions, measurement limits and experiments.
- [Original competitor evidence](android-competitors-evidence.md) and [insurance benchmarks](insurance-benchmarks-evidence.md): retained supporting comparisons.
- [Additional market evidence](market-opportunity-evidence.md): Rudi, prerelease watchlist and trust precedents.
- [Rebrand and validation record](03-workspace-rename-note.md): current names, paths and change status.

This is sourced desk research. No app installation, paid enrollment, on-road study or measured accuracy comparison was performed in this phase. Source omissions are recorded as unverified capabilities, not absent features.
