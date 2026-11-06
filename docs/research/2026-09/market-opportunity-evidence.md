# Additional market and trust evidence

Research date: 17 September 2026. Official source descriptions are developer claims unless independently validated. No APK installation or live-road testing was performed. URLs were retrieved through web search/open; public pages and cached storefront views can change.

## Small standalone Android comparator

**Rudi — Live Driving Assistant**, package `com.sukhralia.tomodrive`, has a public [Google Play listing](https://play.google.com/store/apps/details?id=com.sukhralia.tomodrive&hl=en_US). The retrieved page showed 50+ downloads and an update date of 8 June 2025; no useful user-review sample was visible. Its listing advertises manually started sessions, motion/GPS-based scoring, event timestamps, route history, local storage, and no account/ads. A live avatar reacts to driving. The stated history limit is 50 drives. Phone-use detection and dependable unattended recording were not verified. Published fixed thresholds are implementation claims, not validated safety boundaries. Its description associates a happy avatar with high speed, which merits a safety-focused design critique, not an inference about actual user behavior.

This is a small direct comparator, not evidence of broad adoption. It demonstrates that on-device scoring and playful interfaces are already offered in the category. The review and installation gaps need hands-on follow-up.

## Watchlist: do not count these as publicly available alternatives

**Roadsy:** the [official site](https://roadsy.app/) explicitly says private Android testing. It claims local encrypted trip storage, manual/automatic recording, braking/acceleration/cornering/smoothness feedback, confidence, coaching, export, and private backup. Its imagery uses example trips. It should be monitored as an emerging competitor, but no general public installation or usable Android review base was verified. Its proposed positioning overlaps strongly with a private driving journal, so that positioning is not proven unique.

**AI Drive Assistant / Oonak:** the [official page](https://drivingassistant.oonak.ai/) describes motion/GPS scoring, local history, voice feedback, and a free/paid model, but explicitly says “Coming soon to Google Play.” A direct store-page fetch did not succeed. Do not count the listed price as an active, verified commercial offer or the app as generally released. The page is a positioning signal only.

## Documented scoring and data-trust issues

[DriveScore’s official score explanation](https://help.drivescore.com/hc/en-us/articles/360020657620-How-can-I-improve-my-score) lists motion, distraction, and speeding inputs. It acknowledges that road matching can confuse a highway with an adjacent road. That is stronger evidence for a real map-matching limitation than a review alone. Its published event thresholds are not suitable as universal DrivePerf thresholds; calibration and ground truth remain necessary.

The [FTC’s January 2026 GM/OnStar final-order announcement](https://www.ftc.gov/news-events/news/press-releases/2026/01/ftc-finalizes-order-settling-allegations-gm-onstar-collected-sold-geolocation-data-without-consumers) concerns allegations of inadequate notice and consent for collection and sharing of driving/location data. It establishes the importance of purpose transparency and control; it is not evidence that all driving apps sell data or that its respondent-specific order is a universal rule for DrivePerf.

The [Canadian privacy regulators’ Tim Hortons investigation](https://www.priv.gc.ca/en/opc-actions-and-decisions/investigations/investigations-into-businesses/2022/pipeda-2022-001/) found excessive location collection and misleading consent. Research implication: system permission is not a complete explanation of purpose, extent, recipients, or retention. Do not promise trip-only collection if pre-trip detection or SDK behavior collects location outside a trip.

## UI foundation

[Android’s accessibility guidance](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility) supports scalable text, sufficient contrast, semantic labels, and at least 48 dp touch targets. DrivePerf’s proposed modern interface should make uncertain data, correction actions, and recording state understandable, as well as looking contemporary. These are design recommendations; this desk research did not establish that all competitor interfaces are visually outdated.
