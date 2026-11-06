# DrivePerf — Android insurance-telematics benchmark evidence

Research date: 2026-09-17. Purpose: bounded functional and UX comparison for an independent driving-coaching app, not an insurance product. This is desk research, not an installed-app test or legal analysis.

## Evidence boundaries

- These products are adjacent benchmarks: useful driving measurement/coaching is bundled with insurance eligibility, pricing and servicing. They do not establish demand or willingness to pay for a standalone coaching product.
- All evidence below comes from official product/FAQ pages and developer Google Play listings, including public reviews displayed on those listings. Public reviews report users' experiences; their diagnoses and accusations are not established facts.
- The 13 review examples below are a purposive convenience sample surfaced by Google Play, spanning 2024–2026. They are not a random sample, a prevalence estimate, or a comparison of defect rates. Positive/mixed evidence is included where available. Individual stars were not reliably exposed, so none are assigned.
- Store values are **listing snapshots retrieved via web on 2026-09-17**. Search/cache/localization can affect results. They are not independently live-verified device-install counts, active-user estimates, or feature-specific ratings. Page headers and device-specific review sections sometimes have different totals. Locale parameters also did not consistently change the footer locale.
- No screenshot-based aesthetic audit or hands-on onboarding was performed. UI recommendations below are hypotheses about clearer information and recovery flows, not claims that every competitor lacks those flows.
- Current pages can coexist with obsolete program pages. In particular, avoid treating legacy TD or pre-August-2018 belairdirect documentation as current policy.

## Current product benchmark

### TD MyAdvantage inside TD Insurance

**[TD-P] [Android listing](https://play.google.com/store/apps/details?id=com.td.insurance&hl=en_CA).** Current listing integrates MyAdvantage in TD Insurance, offers driving scores and advice, and restricts the program to TD account holders in Ontario/Quebec. It explicitly describes possible discounts **and surcharges**. Snapshot: 4.4 overall, roughly 17K reviews, 500K+ downloads; update August 21, 2026. These are whole-insurance-app metrics.

Reviews shown on that listing, paraphrased:

| Review/date | Evidence relevant to DrivePerf |
|---|---|
| Robert Bilodeau, September 16, 2025 | Reports repeated authentication friction for trip review, unexpectedly severe scoring for a low-severity acceleration event, and battery drain. |
| Martha Cox, February 12, 2026 | Says a trip-viewing bug was resolved and support followed up; mostly fair scores and mostly working tracking, with occasional missing trips/oversensitivity. A developer troubleshooting reply is dated October 8, 2025, suggesting the visible review may have been edited. |
| Hunter Wu, August 31, 2026 | Reports worsening capture reliability and late trip starts. Developer response September 8, 2026 explains background detection/required permissions and points to digital chat. |

**[TD-F] [Current MyAdvantage FAQ](https://www.tdinsurance.com/products-services/auto-car-insurance/tips-advice/my-advantage-faq).** Trips receive 0–5 stars using acceleration, braking, cornering, speed, distraction and usage. Time, distance and highway travel also matter. Hands-free calls, handheld calls and screen interaction affect scores; passenger use of the driver's phone also counts negatively. Non-driver trips can be declined within seven days; trip-performance viewing lasts 30 days. Tracking can be paused.

The FAQ specifies the latest three Android/iOS versions, excludes tablets and certain other operating systems, and warns that low charge, power saving, GPS issues or multi-phone login can impair capture. It claims approximately 95% capture; this is a vendor claim, not an independently measured accuracy result. Mobile data is optional: sensor/GPS trips can upload later over Wi-Fi. Data categories include location, driving events, distraction, device ID, battery/charging and connectivity. The FAQ says calls are not recorded and limits stated sharing to its provider or legal requirements.

**Inference:** DrivePerf should clearly separate inferred handling events, phone-interaction evidence, trip exposure and an overall coaching trend. Do not silently turn an insurer's actuarial risk variables into a statement of driving skill. The consumer research should test whether a non-insurance relationship improves trust.

### Intact my Drive

**[IN-P] [Android listing](https://play.google.com/store/apps/details?id=com.intact&hl=en_CA).** my Drive is inside the Intact insurance app; safety insights accompany Crash Assist, Car Care and other insurance services. Snapshot: 3.8 overall, approximately 55.5K header reviews, 1M+ downloads; updated September 1, 2026. Whole-app metrics.

| Review/date | Evidence relevant to DrivePerf |
|---|---|
| Jerry G., November 11, 2024 | Likes route information; objects to sensitive braking/acceleration judgments during defensive maneuvers and reports missing trips. Developer reply November 12 emphasizes assessment across the evaluation period. Its numerical threshold wording is dimensionally unclear, so do not reuse it as an engineering threshold. |
| Sharjil Neshat, September 8, 2026 | Suspects an association with Android Auto disconnections. Causation is unproven. Developer response September 9 acknowledges the reported issue. |
| Kris PV, August 22, 2026 | Reports confusing access requirements, battery drain, incomplete recording and disputed phone/smoothness scoring. Developer response August 24 acknowledges battery/recording challenges and says feedback was escalated. |

The release notes mention localized myDrive FAQs addressing trip accuracy, speed/braking calculations and distraction.

**[IN-F] [Current my Drive FAQ](https://www.intact.ca/en/personal-insurance/online-services/my-drive/faq-mydrive-v4).** Feedback is grouped into Safe speed, Smoothness and Focus, with trip badges, 30-day trends, comparisons and tips. Smoothness includes acceleration, braking and sharp turns. Trip/passenger and passenger-phone-use corrections are allowed within 30 days. The FAQ explains that detection can omit the first roughly 300 metres. It estimates daily battery use at 3–6% and monthly data at 30–60 MB; these are vendor estimates, not benchmarks. It says trips are not recorded below 20% battery or in power-saving mode. Unlike TD, it requires a data plan. Intact identifies CMT, formerly TrueMotion, as its telematics provider, says calls are not recorded, and says enrolled drivers cannot view other drivers' trip details. Region-specific policy terms must be checked separately.

**Inference:** Badges, coaching and passenger corrections already exist in the market. DrivePerf's advantage must be clearer evidence and better usability/accuracy, not simply having these features. Put capture state, missing-data explanations and dispute resolution near each trip rather than relying on FAQs.

### belairdirect automerit — supporting Canadian benchmark

**[BE-A] [Current activation guide](https://www.belairdirect.com/en/user-guide/activate-automerit.html).** Available in Alberta, Ontario, Nova Scotia and Quebec; insurance enrollment required. The guide states Android 8.0+ and explicitly acknowledges sensor performance differences across phones. It uses GPS/motion data and always-on location; onboarding includes consent and phone verification. These minimums describe belairdirect, not a recommended DrivePerf support policy.

**[BE-P] [Current program page](https://www.belairdirect.com/en/app/automerit.html).** Three dimensions are speed, smoothness and focus; supporting benefits include safety, fuel-efficiency and eco-driving insights. It identifies CMT as a provider and explains that renewal premiums may decrease or increase. Marketing sections can vary by region and include duplicated/localized savings claims, so no single savings number is carried forward.

**[BE-F] [Official FAQ](https://toronto.belairdirect.com/en/faq.html).** Passenger-trip and passenger-phone-use corrections are available within 30 days. This provides a useful comparison with TD's stricter handling of passenger use of a driver's phone. The retrieved FAQ is on belairdirect's Toronto subdomain, so other regional terms require separate verification.

**Caution:** [The legacy FAQ](https://www.belairdirect.com/en/app/faq-automerit.html) explicitly applies to people who joined before August 12, 2018. Its old Android requirement, discount-only language and correction rules must not be presented as the current general program. No belairdirect Android review sample was collected in this bounded pass.

### State Farm Drive Safe & Save — U.S. benchmark with beacon

**[SF-M] [Official mobile program page](https://www.statefarm.com/customer-care/download-mobile-apps/drive-safe-and-save-mobile).** Uses the State Farm app plus a mailed Bluetooth beacon assigned to the car. It automatically captures trips and gives feedback for acceleration, braking, cornering, speed and distraction. This is a hardware-assisted benchmark, not evidence that phone-only attribution is solved.

**[SF-A] [Program eligibility](https://www.statefarm.com/insurance/auto/discounts/drive-safe-save).** Enrollment, compatible phone and setup are required. The page excludes California, Massachusetts and Rhode Island, with further state limitations. Recheck exact regional terms if benchmarking a specific U.S. market.

**[SF-F] [Program FAQ](https://www.statefarm.com/customer-care/faqs/drive-safe-save).** Trip maps and a 14-day scorecard provide feedback; the scorecard is not directly the vehicle's premium adjustment. Phone distraction requires vehicle movement, phone movement and screen-on state together. The FAQ acknowledges passenger use and an unsecured illuminated phone can trigger it. Android Auto and hands-free controls are not reflected unless the phone itself is manipulated. Posted-speed corrections can take up to 30 days. The phone eligibility list includes GPS, Bluetooth, accelerometer, gyroscope and camera features. Camera requirements belong to this program's workflow and should not be assumed necessary for phone-only driving metrics.

**[SF-P] [Current Android listing](https://play.google.com/store/apps/details?id=com.statefarm.pocketagent&hl=en_US).** Snapshot: 4.6 overall, roughly 486K header reviews, 10M+ downloads; updated September 9, 2026. These ratings include insurance servicing.

| Review/date | Evidence relevant to DrivePerf |
|---|---|
| HSpezy, December 19, 2024 | Reports intrusive notifications and battery use, and believes notifications are compulsory. Developer response December 20 explicitly says notifications are **not required** and suggests support. This is evidence of a user mental-model/communication failure, not proof of a notification requirement. |

**[SF-OLD] [Legacy standalone Android listing](https://play.google.com/store/apps/details?id=com.statefarm.dssm).** Says Drive Safe & Save moved into State Farm and records a May 14, 2025 update. Do not list the old standalone app as a separate actively distinct competitor or combine its historical reviews/ratings with the current app.

### Progressive Snapshot — U.S. benchmark

**[PR-PROD] [Official Snapshot overview](https://www.progressive.com/auto/discounts/snapshot/).** Offers either a mobile experience or OBD-II plug-in device, depending on eligibility. The mobile journey has dashboard, trip log, event map and personalized tips; maps mark braking, acceleration and phone-use events. Users can recategorize trips. Time and mileage are also relevant to the program. Cornering is not advertised in the reviewed feature description: classify it as **not verified**, not definitely absent. Phone-use rating varies by state; program availability is regional and excludes California.

**[PR-F] [Snapshot FAQ](https://www.progressive.com/auto/discounts/snapshot/snapshot-faq/).** Records how, when and how much people drive, plus handheld use in the mobile experience. Location is collected and may be used for underwriting even though the FAQ says it does not affect Snapshot results. The FAQ acknowledges incomplete trips and allows classification correction. It says the mobile option may be a poor fit for people routinely driving vehicles outside their policy. Its Android permission text includes legacy storage/location terminology, so do not copy it into a modern Android permission specification.

**[PR-P] [Progressive Android listing](https://play.google.com/store/apps/details?id=com.phonevalley.progressive).** Shows access to Snapshot progress within the broader app. Snapshot: 4.6 overall, approximately 265K header reviews, 10M+ downloads; updated September 1, 2026. Does not by itself establish which enrollment cohorts use integrated capture versus a separate app.

| Review/date | Evidence relevant to DrivePerf |
|---|---|
| Brad Sampson, August 31, 2026 | Reports receiving no-data warnings while the app displays trips and a high score. This is a perceived contradiction between capture, upload and program status. The claimed pricing motivation is not verified. No response was exposed for this review. |

**Inference:** Make recording, local persistence, upload and scoring distinct visible states. A route shown locally should not imply successful cloud ingestion. This matters even if DrivePerf avoids insurer eligibility rules.

### Allstate Drivewise — distinguish U.S. and Canada

**[AS-PROD] [U.S. Drivewise overview](https://www.allstate.com/drivewise).** Offers trip history/maps, coaching insights, parking location, family driving insights and crash detection. It discusses speed, sudden braking, phone activity and late-night exposure. Pricing can increase for risky patterns. Crash detection is a separate high-consequence capability and should not be implicitly promised by DrivePerf's event detection.

**[AS-F] [U.S. Drivewise FAQ](https://www.allstate.com/drivewise/drivewise-faq-asc).** Shows setup/ready/active status and uses speed relative to limits, braking, time and phone activity. It gives Android configuration guidance and warns against reinstalling before pending trips upload. These are useful existing UX patterns to benchmark, not missing competitor features.

**[AS-P] [Allstate Mobile Android listing](https://play.google.com/store/apps/details?id=com.allstate.view&hl=en_US).** Snapshot: 4.1 overall, about 126K header reviews, 10M+ downloads; updated September 10, 2026. Whole-app rating. Release notes describe a clearer Drivewise setup flow.

| Review/date | Evidence relevant to DrivePerf |
|---|---|
| Laurel Jones, December 20, 2024 | Reports heat, battery drain and missed trips despite troubleshooting. Developer response December 21 acknowledges work on battery consumption and recommends app/OS updates. |
| Sandman, February 18, 2026 | Likes the app overall but reports incorrect posted limits and uncertainty about the scoring consequences. Developer response the same day explains general performance scoring but does not resolve the reported map error in the visible reply. |

**[AC-P] [Drivewise Canada Android listing](https://play.google.com/store/apps/details?id=com.modusgo.drivewise&hl=en-CA).** Separate Allstate Canada/Modus app, not the U.S. product. Retrieved listing describes a six-month monitoring period and time/duration, hard braking and excessive speed. Snapshot is older: update August 20, 2025; header 4.3/~1.92K reviews and review section 4.2/~1.91K, 100K+ downloads. Preserve that inconsistency rather than claiming a precise current rating.

| Review/date | Evidence relevant to DrivePerf |
|---|---|
| Ariel Valdes, July 9, 2025 | Reports false overnight trips while stationary; describes support reassurance without an understandable explanation. |
| Joshua Chassé, July 6, 2025 | Reports partial/missing journeys and requests manual start/stop. |
| Emmanuel Sonier, July 7, 2025 | Reports unreliable trip access and confusion about when the discount applies. |

No developer responses were exposed for these three reviews. Do not transfer their complaints or feature set to the U.S. app.

**[AC-O] [Canada overview](https://www.allstate.ca/car-insurance/drivewise)** lists Alberta, Ontario, Quebec, New Brunswick and Nova Scotia. **[AC-B] [Official October 28, 2025 explainer](https://www.allstate.ca/resources/on-the-road/drivewise-might-be-your-new-best-friend)** describes six months/1,000 km, braking, driving time and a 125 km/h speeding threshold. This is not equivalent to checking every posted speed limit and must not be marketed as a general safe-speed standard.

## Product hypotheses to carry into validation

These are design/research recommendations inferred from the evidence, not measured competitor omissions or proof of technical feasibility.

1. **Trust comes before cosmetic modernization.** A clear score attached to a wrong trip still loses trust. Show trip completeness, sensor quality, event severity, estimated confidence, processing state and a simple correction path. Confidence must be calibrated in validation, not merely displayed as an invented percentage.
2. **Make the trip review the primary workflow.** A trip summary should explain what changed the result and where it occurred, show comparable recent behavior and offer one useful next-drive action. Map/event chips, a synchronized event timeline and a concise explanation panel are prototype candidates.
3. **Distinguish measured signals from inferred behavior.** Phone movement/screen activity is not proof the driver was looking away. Hard braking can be an appropriate evasive action. An unknown speed limit must remain unknown. Avoid absolute safe/unsafe labels for incomplete or ambiguous data.
4. **Separate control from context.** Present night travel, trip length and road type as exposure/context; evaluate controllable driving behavior separately. A shift worker should be able to understand a night trip without being told they lack driving skill.
5. **Use correction as part of learning.** Distinguish passenger trip, passenger phone use, transit, wrong map limit, phone movement and necessary defensive maneuver. Do not promise that user corrections validate the underlying event; use them as labeled feedback with an audit trail and explicit scoring policy.
6. **A useful privacy experience is part of the product.** Explain the specific data needed for each feature; provide clear recording/pause state, retention/export/deletion controls and explicit choices for cloud analysis. A standalone coaching business can avoid collecting insurer identifiers, but actual policy and architecture still need privacy review.
7. **Reliability should be visible and recoverable.** Distinguish permission disabled, battery-limited, offline, waiting for upload, analyzing and ready. A manual-start mode is worth testing; it does not by itself solve Android background restrictions or sensor validity.
8. **Research sustained motivation beyond discounts.** Insurer reviews are influenced by pricing stakes. Test whether progress, driving confidence, comfort, trip reflection or specific coaching goals retain people when no premium saving is available.

## Suggested next research tranche

Run a structured Android walkthrough and review-coding study of the direct standalone competitors plus 2–3 of these insurer benchmarks. Sample a predefined time window and rating distribution, include positive reviews, deduplicate edits and code issue frequency only within that documented sample. Record device/app/version/region and inspect actual onboarding, corrections, data deletion, offline behavior and score explanations. Then interview potential standalone users and test a DrivePerf prototype around trust, explanations and permissions. In parallel, validate sensing and background capture across representative Android hardware and phone placements; desk research cannot establish event accuracy.
