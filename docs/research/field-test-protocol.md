# DrivePerf field-test and calibration protocol

This protocol implements A19 for the Founding Drivers beta. It measures whether the v1
apps reliably record ordinary drives and whether the shared pipeline can distinguish
vehicle motion from phone movement and bad GNSS. Use the absolute PRD targets. The
9 November decision scoped Spike 1 to a short iOS data check and removed the Mileon
comparison. Its one-device results are preliminary. [A19 kickoff prompt](../planning/DrivePerf-v1-kickoff-plan.md#a19--field-test--calibration-protocol--research-model--phase-6) ·
[Decisions log](../product/decisions.md) · [Spike 1 results](2026-11/spike-1-ios-capture-results.md)

## Cohort and safety

Run an internal rehearsal, then recruit **10–20 adult Founding Drivers in Canada and the
US** for the labelled A19 fieldwork. Distribute the candidate through TestFlight and
Google Play closed testing. The A19 group contributes to the PRD's wider beta release
readout. Use consenting internal testers or loaners to cover device and route gaps.

| Coverage | Assignment |
|---|---|
| iOS | Current non-Pro reference iPhone and an older supported iPhone. Cover iOS 26.x and a newer supported version. |
| Android | Pixel A-series reference, Samsung Galaxy A-series, Xiaomi/Redmi and OnePlus. Cover an older supported Android release and a recent one. Record model, OS/vendor build, battery policy and Health Check state. |
| OEM background capture | On Samsung, Xiaomi and OnePlus, repeat a locked-screen route after comparable idle time with factory battery management and with the DrivePerf reliability guide applied. Keep those results distinct. Use Pixel as the comparison device and report OnePlus separately because the PRD assigns it no OEM-specific capture target. |
| Vehicle and placement | Use ordinary vehicles with varied ride characteristics. Run fixed portrait and landscape mounts, a secured console or cupholder cradle, a pocket and a secured bag. Record orientation and location in the cabin. Repeat a familiar route with the same driver and mount, and run two secured phones on the same drive. Change placement only while parked. |

**Testers never touch the app while driving, including at red lights and in traffic.**
Set up the phone and any logger before departure. Use manual Start, Stop, notifications,
controls and the diary only when safely parked. A passenger observer can record passive
observations independently. Check locked content, passenger controls and interruption
paths in a parked, bench or simulated session.

**Never provoke dangerous manoeuvres on public roads.** Drive normally and obey traffic
laws. Label manoeuvres that occur during normal driving. If events are scarce, use
synthetic replay or a supervised closed course with a qualified operator and a separate
safety briefing. Keep closed-course results identifiable as such in the analysis. Skip
any run that cannot be completed safely.

## Route and scenario matrix

Start the external fieldwork after an internal dry run exercises consent, clock
alignment, export, labelling and deletion on both platforms. Have each tester log at
least seven consecutive days and aim for ten ordinary drives plus the non-driving
sessions they can safely fit into their routine. Assign the following cases across the
group and track completed counts by route, platform, OEM and placement. Use neutral
route codes in analysis and fixtures. Record ordinary beta use and scripted stress
runs separately. Use the ordinary-use cohort for prevalence metrics such as Q9.

| Route or scenario | What to run and observe |
|---|---|
| City | Stop-and-go, low-speed departures, turns and normal longer traffic stops. Check wake and confirm latency, false splits, impacts from ordinary bumps, and parked finalisation. |
| Highway | Normal merge, steady cruise, exit and parking. Include a screen-off battery run of at least 60 minutes. Check rate back-off and resume. |
| Rural | Open sky, ordinary curves, sparse stops and rough surfaces where safe. Check alignment and event discrimination. Include a weak-network run and check local finalisation. |
| Tunnel or garage | Use an existing legal route with a natural GNSS dropout and recovery. Where available, cover short and long gaps and underground parking. Replay unavailable durations synthetically. Check dashed estimates, labelled gaps, absence of unsupported event pins and honest distance. |
| Non-driving | Log independent walks, runs, conventional bikes, e-bikes, buses, trains, streetcars and passenger rides. Include a walk to a parked car before a real drive, and overnight armed idle. Record each session even if the app stays quiet. |
| Permissions and lifecycle | Run ordinary auto trips with green Health Check and manual trips with foreground permission. While parked, exercise downgraded permissions, manual Start/Stop and timeout, pause/resume, force-stop or force-quit, reboot/first unlock and re-arm. Record trigger source and watchdog/Health Check status. Use internal bench runs for interrupted writes, storage and thermal limits, and simulated long-trip boundaries. Log intentional faults separately from organic stability. |

Check F-CAP behaviour on the candidate build. Confirm that automatic wake needs no tap,
recording surfaces stay passive, temporary traffic stops resume the same trip, and
finalisation survives backgrounding. Inspect the durable pre-interruption writes,
recovery gap, estimated-start provenance and passenger exclusion after parking. Check
F-DQ behaviour against the labels: alignment and re-alignment, phone-moved exclusions,
fix gating, gap rendering, badge and detail, and the distinction between observed,
derived and estimated values. The estimated start stays out of scoring. Log missed
trips independently in the diary and verify the missed-trip check when the feature is
available. Use a parked or bench test for deliberate phone movement. [PRD F-CAP and F-DQ](../product/PRD-v1.md#32-epic-cap--trip-capture-manual-automatic-live-surfaces) ·
[Pipeline and scoring](../architecture/pipeline-and-scoring.md) · [Decisions log](../product/decisions.md)

## Ground truth and labelling

1. **Before departure, while parked.** Assign a participant code and a run ID. Record
   route class, planned role (driver or passenger), capture mode, placement, app and
   pipeline versions, Health Check, charging and power settings, and optional odometer
   start. Record the time offset between the phone and any reference logger before
   starting. Secure all equipment. Auto runs start armed with the screen locked. For
   manual runs, log the Start tap time and position while still parked.
2. **Collect an independent reference.** The minimum is a parked before/after diary
   with actual role or travel mode, departure and parking time, approximate distance,
   route class, stops, gaps, whether the mount shifted and any naturally observed
   event. The tester can record an odometer or use a separate GNSS logger for distance
   and timing. Optional read-only OBD speed can corroborate longitudinal changes
   through GNSS gaps. Use a securely mounted, time-synced vehicle-frame IMU for
   quantitative longitudinal/lateral G and yaw checks. A diary or 1 Hz GNSS alone
   cannot establish sub-second event peaks. Record each reference's coverage and
   limitations. Keep unneeded OBD fields such as VIN out of the export.
3. **After arrival, while parked.** Record actual motion-start and parking times
   and the odometer end before looking at DrivePerf results. Note clock offset again
   and mark uncertain times as such. Log every run, including app misses and no-trip
   controls. Stop manual recording and do any passenger marking or content checks
   only when parked. Submit the diary and any approved export via the private intake.
   For timing and recovery cases, retain the consented wake, candidate outcome, first
   fix, confirmation, last durable write, finalisation and Story-ready markers where
   available. Use internal instrumented builds to fill diagnostic gaps. Keep their
   results associated with that build.
4. **Align and label in the restricted workspace.** Join diary, app and optional
   reference streams by run ID. Use monotonic elapsed time and the recorded clock
   offsets. Record synchronization uncertainty and leave uncovered intervals unknown.
   Label the true session class, movement start/end, temporary stops, screen-off and
   permission intervals, tunnel or poor GNSS, impacts, phone moves and interruptions.
   For brake, acceleration and corner events, record type, onset, peak, end,
   evidence source and confidence. Before reviewing app detections, use the reference
   signals and a fixed written rubric for genuine, sustained events, ordinary motion
   and bumps. Start the reference rubric from the A12 event definitions in §4.1
   (0.30 g braking, 0.28 g acceleration and 0.36 g cornering at entry, with their
   duration and motion checks). Apply it to the independent vehicle-frame evidence
   and freeze it before testing candidate configs. Label vertical road impacts and
   phone handling separately from vehicle events. Mark diary-only events with
   insufficient timing or corroboration as uncertain. [A12 event definitions](../architecture/pipeline-and-scoring.md#41-thresholds)
5. **Review and freeze labels.** A second reviewer checks event positives,
   hard negatives, ambiguous spans and disagreement cases against independent
   evidence. Resolve disagreements before unblinding the app output or keep them
   unknown. Freeze the label revision and record exclusions and eligible kilometres
   before matching. Keep only spans with reliable positive and negative reference
   coverage in the event accuracy denominator. Keep whole runs eligible for other
   metrics where their evidence supports it. Log the reviewed run, label revision
   and consent state privately.
6. **Reconcile capture.** Match app trips to diary sessions using the run ID or
   device, timeline overlap and available independent reference. A split still
   counts as one captured drive and one split drive. Log duplicates, merges, short
   discards, misses and unmatched records separately. A diary entry is still required
   when the app has no trip to export. Keep uncertain matches visible and resolve
   them or report their count before claiming a pass.

## Metrics and release readout

For each rate, report the numerator and denominator, coverage and a 95% interval
where applicable. Account for repeated runs by the same participant when estimating
uncertainty. For latency, publish sample count, median and p90. Show cohort,
app build, platform, OEM and reliability-guide state, with route and placement
breakdowns where the counts are adequate. Pool counts across runs only for the
cohort roll-up. Mark missing or sparse evidence explicitly and collect more observations
if it cannot support a release decision. [PRD quality targets](../product/PRD-v1.md#24-quality-targets)

| ID | Measurement and target |
|---|---|
| Q1, Q4 | Of diary-confirmed car drives ≥ 1 km with auto armed and green Health Check, count recorded drives (Q1) and misses (Q4 = 1 − Q1). iOS and Pixel ≥ 95% capture / ≤ 5% missed. Samsung and Xiaomi with the guide applied ≥ 90% / ≤ 10%. Show driver and passenger counts, and report degraded permissions and deliberate interruptions alongside the eligible cohort. |
| Q2 | Straight-line distance from the PRD parking anchor (previous trip endpoint, iOS Visit or Android last-known location) to the first observed fix with horizontal accuracy ≤ 20 m. Also report seconds from the motion-activity start and cross-check against the diary or reference. Show missing anchor/fix counts and retain those drives in Q1/Q4. iOS median ≤ 400 m, p90 ≤ 1,000 m. Android median ≤ 250 m, p90 ≤ 600 m. |
| Q3 | Time and straight-line distance from parked Start tap to the first recorded fix. Targets are ≤ 10 s and ≤ 50 m on each platform. Count starts with no fix as failures. |
| Q5 | Walking/running diary sessions that create a trip ÷ all eligible walk/run sessions. ≤ 0.5%. |
| Q6 | Bike and e-bike diary rides that create an unflagged trip ÷ all eligible bike/e-bike rides. ≤ 5%. |
| Q7 | Recorded transit rides flagged for one-tap correction ÷ all recorded transit rides. ≥ 50%. Report by bus, train and streetcar, and verify correction after travel. |
| Q8 | Passenger car trips recorded ÷ passenger car trips in the diary. Target 100%. Verify marking in ≤ 2 taps and exclusion from every score, baseline and record. |
| Q9 | Recorded non-driving trips other than passenger trips ÷ all recorded trips, using the classification before tester corrections. ≤ 3%. Show counts by class and any unresolved classifications. |
| Q10 | Elapsed time from actual parking to the app ending the trip, using the time the end action fired. The stored trip timestamp is backdated. p90 ≤ 5 min. Drives split into multiple app trips ÷ eligible drives ≤ 3%. |
| Q11 | Percentage-point drop ÷ elapsed recording hours on each reference device. Run screen off, unplugged, without debugger, for ≥ 60 min at 15–30 °C ambient. Log start/end battery, ambient conditions and power mode. iPhone reference ≤ 5 %/h, mid-tier Android reference ≤ 4 %/h, supported-device ceiling 6 %/h. |
| Q12 | Battery percentage-point drop over a 24 h armed run with no trip, unplugged. ≤ 2 %/day. Record OS battery use and whether GNSS remained off outside candidates/trips. |
| Q13 | Delivered unique IMU samples and GNSS fixes divided by expected samples from elapsed recording time at nominal 50 Hz and 1 Hz respectively. Target ≥ 98% IMU and ≥ 95% GNSS outside tunnels, without a debugger. Show tunnel intervals and rate back-off separately, with delivery at the requested rate also reported. |
| Q14 | Zero confirmed trips lost. Recover ≥ 99.9% of crash/system-kill interruptions with all previously written data. Verify the recovered/interrupted state and missing spans against the pre-kill persisted checkpoint, including runs that resume then finalise. Report controlled injections alongside organic interruptions, with counts and sample limitations. |
| Q15 | Sessions without a crash ÷ observed sessions, by platform and build. ≥ 99.5%. Use consented Sentry stability data and report observation coverage. Cross-check against Xcode Organizer and Android vitals. |
| Q16 | Users with no observed crash ÷ observed active users in the same seven-day window, by platform. ≥ 99.0%. Use an approved aggregate stability source. Report unavailable if the source cannot provide the denominator without adding a persistent identifier. |
| Q17 | Play Console user-perceived rates for the closed-test build. ANR < 0.47%, crash < 1.09%, using Play's daily active device/user denominator. Missing or delayed Play data leaves Q17 pending. |
| Q18 | Time from trip end to Story ready, p90 ≤ 10 s. Time from opening to first meaningful paint, < 3 s. Measure on both platforms, including degraded stories, while parked. |
| Q19 | Time from cold launch to usable Home, p90 ≤ 2 s on both reference platforms. |
| Q20 | Per event type, precision ≥ 85% and recall ≥ 75% on frozen labels. Report counts and rates per 100 km as below. |
| Q21 | On iOS, time from a background relaunch to the first DB write ≤ 2 s. Use internal locked-device traces. |

For Q20, pair same-type detections and reference labels one-to-one within **±1 s
of peak** on the synchronized timeline, choosing the closest peak among eligible
unmatched pairs. Freeze the tolerance before inspecting results. Only score spans
with adequate timing and positive and negative reference coverage. A matched pair
is a true positive (TP), an unmatched detection a false positive (FP), and an
unmatched confirmed event a false negative (FN). Report precision `TP / (TP + FP)`,
recall `TP / (TP + FN)` and `100 × TP, FP or FN / eligible reference km` for each
type and placement. Use the same reference kilometres for the baseline and
candidate. Report rates with zero denominators as unavailable. Show app-excluded
intervals and their coverage so exclusions cannot silently improve accuracy.

On the same labelled drives, report the median absolute Smoothness difference for
repeat commutes and simultaneous paired phones (≤ 5 points each), vehicle-frame error
against the reference IMU (≤ 0.03 g RMS), and the share of mounted trips rated Unscorable
(≤ 5%). Compare 25 Hz and 50 Hz outputs on the same R0 input (median absolute score
difference ≤ 1 point and ≥ 95% event concordance). Use one-to-one type/peak matching
and divide the number matched by the number of events in the union of both outputs.
[Pipeline field-validation targets](../architecture/pipeline-and-scoring.md#85-field-validation-a19)

Run the release readout on the intended build for **at least one week**. The PRD
requires Q1, Q2, Q4, Q9, Q11, Q14, Q15 and Q17 to meet target on the beta cohort.
Track the other PRD targets and open gaps in the same readout. If a new build changes
capture or scoring, re-run the affected field cases and collect a fresh gate window
on the candidate. The beta lead records coverage, each gate's measured value and
pass/fail/pending status, any regression and its owner, and the recommended release
decision. The release owner records the decision alongside the §7.3 Play
declarations, Apple review materials and counsel sign-off. [PRD release plan](../product/PRD-v1.md#73-v10-mvp--january-2027) ·
[Play Console Android vitals](https://support.google.com/googleplay/android-developer/answer/9844486)

## From labelled drives to fixtures and threshold changes

1. **Establish the baseline.** Record the build SHA, config defaults, data and label
   revisions, and frozen metric rules. Assign tuning and holdout groups before
   sweeping values. Keep all runs from a participant, repeated route or simultaneous
   phone pair together across the split. Use the tuning set to diagnose failures and
   reserve the holdout for validation. Keep field performance measurements in the
   restricted workspace because endpoint trimming removes the evidence for start and
   stop latency.
2. **Select a regression slice.** Pick the smallest consented, reviewed span that
   preserves the failure and enough surrounding context. Prefer R0 for capture,
   alignment or filtering and R1 for downstream scoring when supported by the replay
   harness. Use synthetic equivalents for start/end, rare or identifying cases.
   The current fixture directory contains only synthetic drives. [Drive fixtures](../../shared/testing/fixtures/drives) ·
   [Fixture format](../architecture/pipeline-and-scoring.md#82-fixture-format)
3. **Trim and transform in private.** Remove at least the first and last **1,000 m**
   of the original drive and omit any sensitive interior segment. Crop all channels
   and labels to the same window and rebase to elapsed milliseconds. In GPX, omit
   original timestamp elements and identifying metadata. Drop wall-clock
   dates, precise places, tester/device IDs, original metadata and free-text notes
   that can identify a person or place. Rotate the retained track about its centroid,
   apply the same rotation to the GNSS course with wraparound, then translate the
   track into latitude 0–5°, longitude −30 to −25°. Keep the transform parameters
   private. Preserve meaningful gaps, speed and timing. Discard any slice whose
   transformation destroys the test case or whose road shape can still be linked to
   the original trip. [ADR-008](../architecture/adr/ADR-008-ci-cd-and-environments.md) ·
   [Pipeline fixture spec](../architecture/pipeline-and-scoring.md#82-fixture-format)
4. **Package and review.** Use a neutral `rec-…` ID, `dpfx: 1`,
   `source: recorded-trimmed`, level, platform, IMU rate, trim distance and
   transform method. Use at most 10 min and 2 MB per fixture file. CSV line 1 must
   be exactly `# privacy-trimmed`. GPX carries the same marker in an XML comment
   `<!-- # privacy-trimmed -->` in the first five lines so it stays valid XML.
   Use CSV for pipeline replay. Pair it with `<id>.golden.json` containing expected
   spans, badge, relevant events or metrics, versions and tolerances. Check the
   parser/replay path consumes the data being asserted. A second reviewer inspects
   all payloads, labels, filenames and metadata for re-identification, verifies
   per-run publication consent and checks the regression against the reference.
   Run `tools/ci/check-fixture-headers.sh`. Publish only an approved pair under
   `shared/testing/fixtures/drives/`. Use synthetic replay if consent or the
   re-identification review precludes publication.
5. **Tune one related group at a time.** Replay baseline and candidate on identical
   inputs and save before/after counts, latency tails, precision/recall, quality
   exclusions, battery and any device regressions. Change a threshold only with
   evidence for the mechanism and a check for the trade-off. A wake or sensor-policy
   change also needs a real-device rerun because replay cannot reproduce OS wakeups,
   OEM background management or battery use.

   | Provisional group | Evidence for a proposed change |
   |---|---|
   | P-01–P-05, P-24 | Compare wake → first fix → confirm/reject with Q1/Q2/Q4 and walk, bike and transit controls (Q5–Q9). Use `CaptureConfig` for capture fields such as `confirmSpeedMps`, `confirmHoldS`, activity confirmation, `goodFixHAccM`, reject/timeout and `parkedRadiusM`. Keep the Q2 measurement gate at 20 m independent of confirmation. Assess the P-24 transit/cycling rules in their owning stage. |
   | P-06–P-09 | Compare `autoStopS`, stationary/resume gates, `manualStopS` and `resumeAfterKillS` with traffic-stop splits, end latency, manual timeout and recovery integrity (Q10/Q14). |
   | P-10–P-17, P-21 | Compare `CaptureConfig` rate and stillness settings and the signal stage's fix, gap, lag, backfill, alignment and motion-exclusion rules against Q11–Q13, tunnel/garage labels, reference G and 25/50 Hz agreement. |
   | P-18–P-20, P-22–P-25 | Evaluate scoring, event, route, baseline and classification settings in their owning stages. Use Q20, false/missed events per 100 km, paired-phone and repeat-commute agreement, score distribution and eligibility checks. Keep weakly supported values provisional. |

6. **Validate, version and ship a follow-up.** Evaluate the chosen candidate on the
   untouched holdout and rerun affected platform scenarios. If holdout results drive
   another change, reserve fresh data for validation. Log the P-number, owning field,
   units, old/new value, rationale, sample and pass/fail deltas in the follow-up PR
   and record the decision in `docs/product/decisions.md`. Update the immutable
   defaults in [CaptureConfig.kt](../../shared/pipeline/src/commonMain/kotlin/com/driveperf/pipeline/capture/CaptureConfig.kt)
   or the owning signal/scoring config. Update the spec, bump the pipeline or
   scoring version under §0.6 and regenerate affected goldens in the same change.
   Keep calibration values compiled in and immutable. Verify deterministic replay twice and compare JVM and iOS simulator results under
   the spec tolerances. Existing trips keep their stored version unless re-scoring
   is opted into. If the candidate regresses a gate, keep the previous defaults and
   investigate before making another proposal. [Versioning and determinism](../architecture/pipeline-and-scoring.md#05-determinism)

## Consent and data handling

**Before collection**, the beta lead and Keval Langalia, DrivePerf's person
responsible for personal information, approve the tester notice, storage, access and
deletion process. Have counsel confirm the applicable Québec and federal obligations,
the consent wording and any Québec language requirements before enrolling testers.
Review the actual data flows, including external references, processors and remote
access. Where Québec's private-sector act as amended by Law 25 applies, assess the
project under §3.3 and
complete the §17 privacy impact assessment before any transfer or handling outside
Québec. This includes Québec data accessed in Ontario or by a foreign processor.
Proceed with an applicable transfer only after the assessment establishes adequate
protection and the required written agreement is in place. Apply PIPEDA as applicable,
including to commercial transfers across provincial or national borders.
[Québec private-sector privacy act](https://www.legisquebec.gouv.qc.ca/en/pdf/cs/P-39.1.pdf) ·
[OPC PIPEDA requirements](https://www.priv.gc.ca/en/privacy-topics/privacy-laws-in-canada/the-personal-information-protection-and-electronic-documents-act-pipeda/pipeda_brief/)

- **Obtain express, documented consent.** In plain language, state the calibration
  purpose, precise location and motion fields, diary and optional GNSS/OBD/IMU data,
  recipients and processing locations, retention, route re-identification risk and
  participant rights. Explain automatic background location and driving profiling
  before activation, and how to enable, pause or turn off capture. Keep automatic
  capture off until the tester enables it. Keep unticked, purpose-specific choices
  for research upload, optional instrumentation, diagnostics and public fixture
  reuse. Obtain separate per-run opt-in before publishing a recorded derivative.
  Explain that public Git history, clones and forks may retain a copy. Record the
  notice version, choices, date and withdrawal status. A tester can skip a route,
  withhold an export or withdraw. For instrumented passenger runs, secure the
  participating driver's consent too. [OPC meaningful consent guidance](https://www.priv.gc.ca/en/privacy-topics/privacy-for-businesses/appropriate-handling-of-personal-information/collecting-personal-information-and-consent/consent/gl_omc_201805/) ·
  [Québec private-sector privacy act, §§8–8.1, 9.1 and 14](https://www.legisquebec.gouv.qc.ca/en/pdf/cs/P-39.1.pdf)
- **Minimize access and collection.** Use app-private local recording and upload
  only selected, consented runs via the approved encrypted intake. Use TLS in
  transit, encrypted storage, named least-privilege accounts and access logs.
  Store the contact/consent ledger separately from coded runs. Use approved storage
  for the working copies and restrict raw data to the people who need it for
  labelling and privacy review. Omit exact routes, timestamps, identifiers and raw
  exports from Git, Jira, chat, email and crash/analytics payloads. Report aggregates
  and suppress small identifying breakdowns. Treat trimmed traces as potentially
  identifiable until the privacy review clears their proposed use. [PRD §6.5](../product/PRD-v1.md#65-canada-pipeda-quebec-law-25-casl) ·
  [Québec anonymization regulation](https://www.publicationsduquebec.gouv.qc.ca/fileadmin/gazette/pdf_encrypte/lois_reglements/2024A/106829.pdf)
- **Set and enforce research retention.** The proposed study schedule is to delete
  raw research uploads, reference logs and identifiable working copies once
  validation and any deletion request are complete, within 30 days after the
  beta closes unless legally required to retain them longer. Publish the approved
  schedule before consent. Cover exports, temporary copies and backups in the
  deletion plan and log completion. Keep only approved public fixtures, aggregate
  results and the minimal restricted consent and deletion audit records for their
  documented retention periods. Tester-controlled on-device trips follow the
  product's retention policy. [Decisions log](../product/decisions.md) ·
  [PIPEDA Schedule 1](https://laws-lois.justice.gc.ca/eng/acts/p-8.6/FullText.html)
- **Handle participant requests and incidents.** Testers can contact Keval at
  `privacy@driveperf.com` for access, correction, applicable portability, withdrawal
  or deletion. Verify identity, stop further research collection/use for the
  withdrawn purpose, remove affected unpublished material from the dataset and
  document the response and limits on recalling published copies. For a suspected
  leak, contain it, notify Keval promptly and record the incident. Notify the CAI
  and affected people when the Québec risk-of-serious-injury threshold applies.
  Under PIPEDA, report to the OPC and notify affected individuals when there is a
  real risk of significant harm, and keep the required breach records.
  [Québec private-sector privacy act, §§3.5–3.8](https://www.legisquebec.gouv.qc.ca/en/pdf/cs/P-39.1.pdf) ·
  [PIPEDA §§10.1–10.3 and Schedule 1](https://laws-lois.justice.gc.ca/eng/acts/p-8.6/FullText.html)
