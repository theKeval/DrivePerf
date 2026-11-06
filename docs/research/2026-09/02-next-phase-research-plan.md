# DrivePerf — next research phase

**Revised 17 September 2026.** The audiences are already established: (1) enthusiasts who want detailed drive-performance evidence and (2) drivers seeking efficiency, coaching or improvement. Research should refine their needs and priorities. Insurance programs are comparison material, not the initial value proposition.

This document proposes future research. No interviews, road/track studies, paid subscriptions or implementation tickets were initiated by this update. Product specifications follow the research gates below.

## Phase 2: validate the two experiences on Android

### 1. Recruit distinct subgroups

Proposed discovery sample: 16–20 adult drivers, divided approximately equally between the two audiences. Within enthusiasts, distinguish ordinary-road analytics users from track/autocross participants. Within coaching, include new drivers and experienced drivers who care about comfort/efficiency. Add 3–5 instructors or coaches for expert perspective, including a motorsport coach if closed-course work is researched.

These are practical qualitative sample suggestions, not statistically representative market estimates. Include different Android manufacturers/price tiers, urban/rural routes, ICE/EV vehicles and levels of technical comfort. Do not assume every enthusiast wants racing features or every new driver wants a score. Teen participation would require a separately designed consent/privacy study; start with adults.

Interview around real behavior:

- Show the last drive or session you wanted to understand. Which question were you trying to answer?
- Which metrics do you already inspect, export or pay for? Which ones change what you do next?
- What does “efficient” mean to you: fuel/energy, smoothness, comfort, time, or consistency?
- Show a useful coaching suggestion and an unhelpful one. What made the difference?
- How much setup, mounting or external hardware is acceptable? How often do you forget to record?
- When do you want a simple answer, and when do you want the underlying traces?
- What would make you distrust a result? How would you expect missing or uncertain data to appear?
- What recording, backup, diagnostic and sharing behavior do you expect after each permission choice?

Discuss willingness to pay through current purchases and concrete trade-offs among free, one-time and subscription alternatives. Do not use insurance-discount interest as the demand test.

Deliverables: separate jobs-to-be-done, must-have metrics, tolerated setup, evidence of recurring use and reasons not to use the product. Preserve disagreements between subgroups instead of averaging them into a generic persona.

### 2. Benchmark a manageable set of installed apps

Primary shortlist: **DrivingTracker, Open Road, Zenroad and AutoBeacon**. Add **RaceChrono or Lap Legend** for expert analysis, **DriveSidekick** for learner feedback, and optionally **Zero Lap or TripRank** as a bridge. Install only where independently eligible; record unavailable enrollment rather than bypassing it. Insurer onboarding is not the central test.

Record exact package, version, country, device/OS, account eligibility, paid tier, checkout price, hardware and date. Compare the same tasks:

1. Start a useful manual recording, then understand automatic recording setup and permission consequences.
2. Diagnose sensor/mount/GPS readiness and recover a partial or interrupted session.
3. Review the drive promptly after completion, before choosing to share or export anything.
4. Find a specific event, inspect the underlying speed/motion and explain the result.
5. Compare two similar drives or laps and identify a meaningful difference.
6. Move between a concise explanation and detailed graphs without losing context.
7. Export usable data with timestamps, units and source information.
8. Correct role/context, delete a trip, inspect backup and confirm public presence/sharing settings.

During ordinary driving, use a passenger/researcher for observations; do not operate the apps at the wheel or provoke dangerous events. Deliberate acceleration/braking and timing experiments belong in a suitable controlled environment. Video, external hardware and track attendance require separate logistics when that study is scheduled.

Deliverables: reproducible task results, permission/price matrix, screenshots, readiness/recovery comparison, and verified gaps. A marketing description is not an accuracy measurement.

### 3. Expand review analysis with a declared sample

For mature shortlisted apps, collect roughly 50–100 accessible recent Android reviews per product if permitted and practical. Keep sparse products sparse. Sample across star levels; separate recent versions from historical incidents and record developer responses. Do not silently substitute vendor-selected testimonials for independently retrieved reviews.

Suggested codes: capture, timing/speed validity, calibration, hardware compatibility, expert controls, coaching usefulness, export, recovery, battery/heat, permissions/privacy, price and accessibility. Deduplicate repeat entries across the existing appendices; for example, the positive Zenroad Rehman R. review appears in both research passes. Code a subset independently to check interpretation.

Only report rates within the declared sample. Review anecdotes cannot establish market-wide defect rates, current reproducibility, market share or retention.

### 4. Test two views of the same drive

Use a concise coaching summary and a richer analysis view backed by the same sample data. Test with 6–8 participants per iteration, split across audiences; revise and run a second round if it resolves identified confusion. This is a prototype research proposal, not a production UI commitment.

Ask participants to explain a braking event, locate an acceleration trace, compare similar segments, understand uncertainty, identify one next practice goal, export data and explain what leaves the phone. Include incomplete trips, unavailable vehicle channels and a required defensive maneuver. Test large fonts, TalkBack, color-independent chart meaning and small displays.

Measures: task completion, time, comprehension, false conclusions, correction effort, confidence calibration and remembered next action. Establish targets from benchmark evidence before testing, rather than inventing thresholds after seeing results.

### Phase-two decision gate

Proceed when both audiences have a clear recurring job, the prototype helps them interpret a drive, and the proposed metric set offers worthwhile improvement over actual alternatives. Decide which advanced metrics belong in the first POC and which remain future work. If enthusiasts require hardware-grade timing from day one, or coaching users do not value the outputs, revise scope before implementation.

## Phase 3: measurement, battery and privacy feasibility

Design a bounded, consented study that measures actual delivered sampling, trip completeness, timing error, event precision/recall, false events per distance/hour, unscorable fraction, repeatability, battery drain, storage and network volume. Separate ordinary trips from controlled closed-course sessions.

Cover fixed mount/pocket/bag/cupholder, phone movement, different device tiers, ICE/EV regeneration, hills, rough roads, tunnels, weak GNSS, screen-off use, Android Auto, calls, permission revocation, battery saver, reboot/force-stop and Bluetooth loss. Use synchronized reference IMU/GNSS or supported vehicle data where needed. Extra validation instruments do not imply the customer must buy them.

Evaluate road-position alignment, posted-limit coverage and licensed provider cost separately from speed measurement. Investigate phone handling as its own accuracy/privacy question. Do not assume exact fuel/energy, pedal position or steering are available without suitable vehicle data. Compare phone-only timing with optional higher-rate receivers for future track features.

Split datasets by driver/device/vehicle/route to reduce leakage. Report performance by condition with uncertainty; rare-event accuracy alone can hide missed events. Establish score semantics and thresholds from ground truth and user tolerance. Research confidence displays rather than inventing a confidence percentage.

Map all data flows: raw sensors, routes, scores, account identity, map requests, SDK diagnostics, local/OS backup, optional cloud and exports. Test withdrawal/deletion and reconcile claims with actual traffic. Review current Android/Google Play and applicable privacy requirements before specifications. The [technical appendix](android-feasibility-evidence.md) provides detailed starting evidence, not a final manifest.

## Phase 4: iPhone research

Repeat market/review, sensor/background, phone-interaction, permission, energy and store-policy analysis specifically for iOS. Determine which user promises can be consistent across platforms. Android feasibility and a cross-platform vendor's marketing do not prove iPhone parity.

## Phase 5: commercial, scaling and future telemetry research

Compare revenue models within the two audiences and estimate actual map/storage/support costs from measured usage. Assess build-versus-buy SDK choices for explainability, ownership/export, offline behavior, quality, licensing and per-driver cost. Use scenarios rather than invented active-user forecasts.

Carry **Cosworth PDR/AliveDrive** into the future roadmap as inspiration for synchronized recording, review, comparison and coaching. Investigate format access and permitted imports before claiming compatibility. Optional GNSS/OBD/OEM channels should have explicit support matrices, provenance and channel-specific validation. City coaching and closed-course optimization require different interpretations; neither is evidence of equivalent performance to an in-vehicle recorder.

## Final synthesis, then POC specifications

Consolidate the findings into an evidence-to-decision register. Resolve launch geography, supported users/devices, mounting assumptions, manual/automatic behavior, metric provenance, score interpretation, phone-handling scope, paid value, cloud need and failure modes. Every proposed feature should connect to a user job, competitive evidence, feasibility, privacy cost and validation method.

Only then write the POC specification and implementation tickets on the existing **DrivePerf / DRIVE** board. Audit the current prototype against that specification; do not let its existing score weights become requirements by default.
