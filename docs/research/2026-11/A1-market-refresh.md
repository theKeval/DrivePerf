# A1 — DrivePerf v1 market refresh (iOS + Android, US + CA)

**Agent:** A1 (mobile product research) · **Date:** 6 November 2026 · **All sources accessed 2026-11-06 unless stated**
**Extends:** [`2026-09/01-android-market-research.md`](../2026-09/01-android-market-research.md) and its appendices (Android-only, 17 Sep 2026). Facts already recorded there are not repeated. Only changes and new iOS evidence appear here.

---

## Executive summary

**1. iOS is where the 2026 entrants are competing, and the field is crowded.** At least eleven iPhone driving-tracker or driving-score apps launched or were renamed between Nov 2025 and Jul 2026 (Table 1). The largest by rating volume is **Enroad** (formerly TripRank; 4.8★ from 6,737 US ratings). Next comes **Open Road** (4.7★, 826 US ratings), which is now on iOS v6.0.3 while its Android listing has not changed since 29 Aug 2026. The Android benchmark **DrivingTracker** still has no iOS app, and neither does **DriveSidekick** (its site has an iPhone waitlist). We found no **Velox – GPS Speedometer** listing in the US or CA store on 6 Nov 2026 [S1–S20].

**2. "Smoothness" is no longer unclaimed.** SpeedRank sells a *Smoothness* and *Finesse* score. Open Road shows a smoothness stat. Top Driver offers a 0–100 smoothness score. OVERDRIVE (iOS, released Jul 2026) markets a *Flow* score with the line "Scored on smooth. Not speed." It has no speed leaderboard, stores data on the device by default and trims share posters. That is the positioning closest to DrivePerf's (vendor claims, [S12][S13]). OVERDRIVE is still tiny (1 US rating) and starts recording manually. None of these apps documents an **explainable, baseline-relative coaching loop** (why the score moved, where, and one thing to practise next).

**3. Platform-native features are now baseline on iOS.** Open Road, MileIQ, Rovy, OVERDRIVE, TripRank (Digitalstalk) and DriveTracker all advertise a **Live Activity** or Dynamic Island view. Rovy, Open Road, Harry's, PACE, Stride and Driversnote advertise CarPlay and/or Apple Watch. A *weekly* recap exists (Open Road on Android, TripRank and DOWNFORCE on Android). We did not verify an **annual or monthly "Wrapped"-style recap** from any driving competitor. That is the clearest gap left by Strava-style recap patterns (Table 6).

**4. Reviews are dominated by capture reliability, paywalls and UI friction.** We coded 50 recent reviews across six cross-platform competitors (Table 4). In that sample, 17 mention capture or data durability (13 of them negatively), 17 mention UI (9 negatively) and 10 mention price (all 10 negative). Only 2 mention coaching and none mention battery. A separate sample of Enroad's 10 most recent US reviews has 7 price complaints. Among them are pre-use paywalls, a "spin-the-wheel" discount screen, and paid subscriptions lost when TripRank was renamed to Enroad. Two apps' reviewers say they were asked to rate the app before they could use it, so headline ratings of 2026 entrants are a weak signal.

**5. Privacy labels separate competitors.** Enroad, MileIQ, PACE, Drive Sense Pro and DriveTracker each declare *Data Used to Track You*. Open Road declares data used for third-party advertising. SpeedRank, Rovy and Top Driver declare "not linked" data or none at all. A "no tracking, minimal linked data" label is achievable and visible on the store page (self-reported labels, Table 1).

**White space (Table 7):** no surveyed product combines all four of these: **(a)** smoothness coaching that is explained and baseline-relative, **(b)** Strava-grade weekly, monthly and annual recaps, **(c)** a privacy-first data model with privacy-trimmed sharing, and **(d)** native Live Activity, widget, CarPlay and Watch support with **iOS + Android parity**. The nearest partial matches are OVERDRIVE (b and d missing), Rovy (a missing, speed leaderboards), and TripRank on Android (Android-only, leaderboard and speed HUD).

**Top 3 PRD implications:** (1) Make automatic capture, durable local storage and recovery P0, with a published missed-trip target. (2) Define Smoothness as explainable, event-linked and fair across vehicles, because "smooth score" alone is already copied. (3) Ship a generous free core with no paywall or rating prompt before the first recap. **Decisions for the product owner** are listed in §9.

---

## 1. Evidence boundaries

- This is desk research. No app was installed, purchased or tested on the road. Listing features, privacy labels and pricing are **developer claims**. Apple privacy labels are self-reported and not verified by Apple.
- iOS metadata (bundle ID, version, last-update date, US and CA rating counts, price) comes from Apple's public iTunes Lookup API for the `us` and `ca` storefronts. Feature claims come from the App Store web listing. Update dates are UTC.
- Play data comes from the public US web listing (`hl=en_US&gl=US`). Play web pages expose about three reviews per app and download *bands* only. **Canadian Play availability was not verified.**
- "Not mentioned" means the listing does not state it. It is **unverified, not absent**.
- Third-party estimates (AppGoblin, MWM, GummySearch) come from undisclosed models. They are labelled **low confidence** and must not be treated as store facts.
- Open Road's privacy policy and website returned HTTP 429 and then a Vercel bot checkpoint on 6 Nov 2026. We did not try to get around it. The 12 Sep 2026 policy reading from the Sep pack still stands, **unverified for changes**.

---

## 2. Table 1 — iOS App Store landscape (US + CA), 15 core rows + benchmarks

Legend: ✓ = claimed in listing or release notes · ◐ = partial or ambiguous · — = not mentioned (unverified). The rating column shows US average and count, with the CA count in brackets. "Updated" is the current version's release date.

| # | App (bundle ID / App Store ID) · developer | Price / IAP (US) | Rating US (CA count) · Updated · Released | Auto-detect | Live Activity | Replay | Scores | Share features | Privacy label summary (self-reported) | Source (acc. 2026-11-06) |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | **Open Road: Drive Tracker GPS** (`RazpeCarlson.OpenRoad` / 6755834573) · OPENROAD LLC | Free; Pro $2.99/wk, $9.99/mo, $39.99/yr | 4.69★ / 826 (CA 106) · v6.0.3, 2026-11-04 · rel. 2026-01-10 | ✓ (shipped as beta opt-in in 4.1.6; Oct 2026 review says "Always" location is now required) | ✓ Lock Screen (5.0.8); Dynamic Island pause (4.1.6) | ◐ route drawn | Casual-mode driving score (braking/accel); smoothness stat (5.0) | Share cards, live friends map, convoys with voice and recap; CarPlay ✓ | No "Used to Track". **Linked:** Health & Fitness, Purchases, Location, Contact, User Content, IDs, Usage, Diagnostics. **Not linked:** Purchases, Device ID, Usage for *third-party advertising* | [S1][S2] |
| 2 | **Enroad – Drive Tracker** (ex-TripRank; `io.uros.triprank` / 6757245082) · 11X AppStudio d.o.o. Beograd | Free; Pro $6.99/wk to $57.99/yr (9 SKUs) | 4.82★ / 6,737 (CA 970) · v4.5, 2026-10-29 · rel. 2026-01-06 | ✓ | — | — | None named; stats for accel, braking, cornering, lane changes, G | Shareable trip image; leaderboards/clubs (per reviews) | **Used to Track:** IDs, Usage. **Not linked:** Device ID, product interaction (ads/marketing, analytics) | [S3][S4] |
| 3 | **SpeedRank – Trip Tracker** (`com.speedrank.app` / 6761128659) · Wendong Song | Free; Pro $8.99/mo, $39.99/yr; 7-day trial (v1.9.3) | 4.56★ / 9 (CA 2) · v1.9.11, 2026-11-03 · rel. 2026-04-07 | ◐ background recording; auto-start not stated | — | — (route map) | **Finesse Score, Smoothness**, idle time, brakes/km | Stats card with route map | **Not linked only:** Location, User ID | [S5] |
| 4 | **TripRank: GPS Speedometer** (`Digitalstalk.Triprank` / 6760617806) · Digitalstalk Pvt Ltd | Free; Pro $4.99, $29.99/yr; Drive Suite $9.99/mo, $79.99/yr | 4.67★ / 12 (CA 2) · v1.18, 2026-11-03 · rel. 2026-03-19 | ✓ | ✓ Lock Screen + DI | — | None named; G-force/behaviour stats; "AI" car-mod feature | Trip cards, profile link | **Linked:** purchases, name, photos, user content, IDs, usage. **Not linked:** location, advertising data (*third-party advertising*). Listing also says "No ads". | [S6] |
| 5 | **Rovy: Drive Tracker & Stats** (`com.HinrichB.drivy` / 6760185139) · Hinrich Bomsdorf | Free; $4.99–$12.99/mo, $24.99–$49.99/yr, $99.99 lifetime | 5.0★ / 7 (CA 4) · v1.5, 2026-10-30 · rel. 2026-03-23 · **iOS 26+** | ✓ Auto Record | ✓ (redesigned in 1.5) | ✓ animated replay with overlays | "Driving style" gauge; accel/brake/corner; 0–100 timer | Share cards; live friends; leaderboards (top speed, distance); CSV/VBO export; CarPlay ✓, Watch ✓ | **Not linked only:** purchases, coarse location, photos, IDs, usage, diagnostics | [S7] |
| 6 | **OVERDRIVE: Road Tracker & Map** (`com.grep.overdrive` / 6790023849) · Othmane Zaidy | Free; $3.99/wk, $9.99/mo, $34.99/yr, $29.99 lifetime | 5.0★ / 1 (CA 0) · v2.0.6, 2026-11-04 · rel. 2026-07-23 | ✗ manual Start (site); hands-free afterwards | ✓ Lock Screen + DI (shows Flow) | ◐ results animation | **Flow** (smoothness; hidden while moving); passenger mode not scored | 9:16 posters omitting first/last 300 m; crews; convoy off by default | No tracking ("no tracking SDKs"). **Linked:** purchases, precise location, email/name, user content, user ID, usage. **Not linked:** user ID | [S12][S13] |
| 7 | **PACE – Drive with friends** (`com.bergeritsolutions.pace` / 6761122094) · Emanuel Berger | Free; Pro $4.99–$5.99/mo, $24.99–$39.99/yr | 5.0★ / 4 (CA 2) · v1.4.4, 2026-10-10 · rel. 2026-04-07 | ◐ | — | — | "Driving insights" | Social feed auto-shares drives (1.0.5); groups; CarPlay ✓, Watch ✓ | **Used to Track:** Purchases, IDs. **Linked:** contact, location, user content | [S8] |
| 8 | **DriveTracker – Driving Log** (`com.ryu.DriveTracker` / 6754501628) · Ryunosuke Shibuya | Free; $2.99/mo, $7.99/yr, $14.99 lifetime | 3.86★ / 7 (CA 2) · v1.20.0, 2026-10-28 · rel. 2025-12-01 · **iOS 26+** | ◐ Shortcuts/Bluetooth automation | ✓ | ◐ slider syncs graphs and map | — | CSV/KML; widgets ✓; iCloud sync | **Used to Track:** Usage. **Not linked:** usage/ad data, location, crash | [S9] |
| 9 | **ZenRoad: Drive Score & Tracker** (`com.telematicssdk.zenroad` / 1563218393) · Damoov Pte Ltd | Free | 2.85★ / 13 (CA 3) · v2.6.2, 2026-10-24 · rel. 2021-04-21 | ✓ | — | — | "Driving scores" (unnamed) | Leaderboard with friends/family | **Not linked:** Health & Fitness, location, contact, crash | [S10] |
| 10 | **Drive Sense Pro** (`com.talnirapps.drivesense` / 6754228533) · Tal Nir | Free, contains ads | no US ratings (CA 1) · v1.0, 2025-11-10 (no update in ~12 months) | ✓ | — | — | Events only (hard brake, accel, corner); handheld-phone detection | — | **Used to Track:** IDs, Usage. **Linked:** Device ID, ad data. **Not linked:** precise location | [S11] |
| 11 | **RaceChrono Pro** (`com.racechrono.pro` / 1129429340) · RaceChrono Oy | **$19.99 US / $24.99 CA** | 4.71★ / 184 (CA 24) · v10.3.1, 2026-10-29 | ◐ laps automatic, session manual | — | ✓ synced graph/map/video playback | — (lap/sector/optimal lap) | ODS/NMEA/VBO/CSV/GPX; video overlays; external GPS/OBD | **Linked:** purchases, email/name, IDs. **Not linked:** precise location, diagnostics | [S14] |
| 12 | **Harry's LapTimer Petrolhead** (`com.harrys.LapTimerPro` / 338257907) · Harald Schlangmann | **$19.99 US / $24.99 CA**; Grand Prix upgrade $9.99 (Rookie $8.99 US / $11.99 CA) | 3.83★ / 153 (CA 17) · v26.0.0, 2026-10-27 | ◐ same-session detection | — | ◐ video playback | — (Hall of Fame) | Video/YouTube, VBO/CSV/SQL; CarPlay ✓ (2025); watchOS-compatible | **Linked:** precise location, name, address, user content, user ID | [S15][S16] |
| 13 | **TrackAddict** (`com.RaceRenderLLC.TrackAddictFree` / 632355692) · HP Tuners LLC | Free; Pro upgrade $8.99 | 4.52★ / 515 (CA 65) · v5.0.5, 2026-07-31 | — | — | ◐ lap comparison/video | — | RaceRender export; external GPS/OBD | **Linked:** precise location, other data | [S17] |
| 14 | **MileIQ** (`com.mobiledatalabs.goiq` / 578830929) · MileIQ Inc. (© Bending Spoons) — *capture benchmark* | Free capped; $13.99/mo, $139.99/yr | 4.76★ / 115,849 (CA 9,980) · v5.66.4, 2026-10-28 | ✓ background | ✓ Lock Screen + DI (5.54.0) | — | — | IRS reports; Siri Shortcuts; voice stop/delete | **Used to Track:** Usage. **Linked:** financial, location, contact, content, IDs, usage, diagnostics | [S18] |
| 15 | **Mileage Tracker by Driversnote** (`com.driversnote.driversnote` / 924418916) · Driversnote ApS — *capture benchmark* | Free; $14/mo, $150/yr, $167.99/yr with iBeacon | 4.78★ / 39,357 (CA 13,307) · v4.30.6, 2026-10-28 | ✓ speed-triggered; optional iBeacon | — | — | — | PDF/Excel reports; **CarPlay + Android Auto added Oct 2026** | **Linked:** fitness, location, contact, IDs, usage, diagnostics. **Not linked:** crash | [S19] |
| B1 | **Stride** (`com.stridehealth.StrideDrive` / 1041591359) · Stride Health — *US-only capture benchmark* | Free | 4.84★ / 99,140 · v26.10.1, 2026-10-23 · **not in CA store** | ✓ | ✓ (CarPlay/Watch) | — | — | Tax reports; CarPlay ✓, Watch ✓ | **Linked:** location, email, content, user ID, usage, crash. **Not linked:** Device ID | [S20] |
| B2 | **Top Driver.** (`com.YZ.TopDriver` / 6760957005) · Yan Zhao | Free; $1.99/mo, $12.99/yr | 0 ratings · v1.0, 2026-03-29 (no updates) | — | — | — | **0–100 smoothness** (accel, brake, corner, steering, consistency); tips after each trip | Leaderboards | **Data Not Collected** | [S21] |
| B3 | **Mileage Tracker by DriveStats** (`com.bijokmind.DriveStats` / 6755319883) · Wan Lutfi Wan Md Hatta | Free; $4.99/mo to $149.99 lifetime | 4.25★ / 4 · v2.3.0, 2026-09-27 | ✓ | — | ✓ step through events | Fuel/cost estimates | CSV/JSON/GPS export; widgets | **Not linked only:** Device ID, usage, diagnostics | [S22] |

**No iOS listing found (checked 2026-11-06):** **DrivingTracker** (the site describes Android only) [S23]. **DriveSidekick** (the site says Android only, with an iPhone waitlist) [S24]. **Velox – GPS Speedometer** (Solarity AI; App ID 6761004156; a third-party page records a listing first released on 11 Apr 2026): the iTunes Lookup API returns no result for US, CA, GB, DE or AU, and the US and CA web listings return 404. Whether it was removed or region-restricted is **unverified** [S25]. **DOWNFORCE**: no iOS listing surfaced in search; **unverified**.

**Name collision warning:** "TripRank" now covers three products: Enroad (formerly TripRank, iOS, 11X), TripRank: GPS Speedometer (iOS, Digitalstalk) and TripRank: Driver Score & Trips (Android, Eon Developers). They are not one cross-platform product.

---

## 3. Table 2 — Android re-check: top 6 vs the 17 Sep 2026 baseline

| App (package) | Sep 2026 baseline (from the 2026-09 pack) | Now (US Play, 2026-11-06) | Change signal for DrivePerf | Source (acc. 2026-11-06) |
|---|---|---|---|---|
| **Driving Tracker: GPS Trip Log** (`com.richardblaha.drivingtracker`) | Listing updated 17 Jul 2026; policy dated 7 May 2026 | Updated **25 Oct 2026** (v1.5.1: fixes for auto-tracking stopping in the background or not resuming after restart). 5K+ downloads; no public rating shown. The listing still says "100% offline", yet Data safety says it *may share* financial info, app activity and performance data, and that "Data can't be deleted". The policy is still dated 7 May 2026 and still describes manual-start-only collection. AppGoblin estimate: about 5.3K installs, ~434 MAU (low confidence). | Background auto-capture is hard even for a focused developer. The listing's "offline" claim and its Data safety entry still disagree. **Still no iOS app.** | [S26][S27][S28] |
| **Open Road: Drive Tracker** (`com.mikita.openroad`) | New listing 29 Aug 2026; policy 12 Sep 2026 | Listing unchanged since **29 Aug 2026**. 4.3★ from 190 reviews, 10K+. New 1★ review (16 Oct 2026) reports no tracking at all and no way to contact support. Meanwhile iOS shipped v5 to v6.0.3 (4 Nov 2026). AppGoblin: Android about 13K installs and 1.5K MAU vs iOS about 68K and 5K MAU (low confidence). | The competitor is **iOS-first**; its Android version lags. Parity is a real differentiator. | [S29][S1][S30][S31] |
| **Zenroad** (`com.telematicssdk.zenroad`) | 2.2★ / 52 reviews, 5K+, updated 22 Jul 2026 | 2.4★ / 55 reviews, 5K+, updated **27 Oct 2026**. On 7 Sep 2026 the developer replied to a missing-trip complaint, suggesting a sync or display issue. iOS v2.6.2 (24 Oct 2026). | It is still a vendor demo. Its reviews remain a useful source of failure cases (passenger and train trips counted, hidden trips lost). | [S32][S10] |
| **AutoBeacon** (`com.sensight.beaconapp`) | Updated 4 Sep 2026 | Updated **6 Nov 2026** ("Bug fixes and optimizations"). 5K+; no rating shown. Data safety: nothing shared; collects location, personal info and two other types. Description unchanged (phone/beacon modes, 20 s crash countdown, optional SMS alert). iOS: **unverified**. | No material feature change. | [S33] |
| **TripRank** (`com.trippulse.triprank`) | "TripRank: GPS Driving Tracker", 10xeon/Muhammad Ahmad, 1K+, updated 10 Sep 2026 | Renamed **"TripRank: Driver Score & Trips"**; developer label is now **Eon Developers** (same Lahore address). **5K+**, updated **4 Nov 2026**. New claims: 0–100 score, eco score, personalised tips, **weekly reports**, 3D event replay, PDF export, CRA/IRS mileage log, Drive Together, **Ghost Mode**, **automatic cropping of start/destination on shared routes**, leaderboard opt-out. Data safety: location only, nothing shared. The "Rated 4.8★ by thousands" claim is **unverified**, because the Play page shows no rating. | This is the fastest-moving Android rival and the closest to DrivePerf's coaching and privacy-sharing ideas, although it pairs them with leaderboards and a speed HUD. | [S34] |
| **DOWNFORCE** (`de.downforce.app`) | Updated 20 Aug 2026; Pro €9/mo or €79/yr, Full €19/mo or €159/yr | Unchanged (**20 Aug 2026**); 100+ downloads; ads + IAP. Data safety: shares personal info and photos/videos. | Stalled. It remains a pricing anchor only. | [S35] |

**Other Android adoption bands (US Play, 2026-11-06):** RaceChrono 500K+ (4.3★/4.61K) and RaceChrono Pro 10K+. TrackAddict 100K+ (4.6★/3.89K, updated 28 Jul 2026). Harry's Petrolhead 10K+ and Rookie 10K+. MileIQ 1M+ (4.5★/73.3K). Driversnote 1M+ (4.7★/38.9K). Stride 1M+ (3.0★/20.9K) [S36].

---

## 4. Table 3 — Review sample design and limits

| Item | Detail |
|---|---|
| Competitors | The six cross-platform competitors (listed on both stores) most relevant to DrivePerf, as capture or analytics benchmarks: **Open Road, Zenroad, RaceChrono (Pro), TrackAddict, MileIQ, Driversnote** |
| Sampling rule | The most recent written reviews per app, taken in date order without filtering by stars. iOS reviews come from Apple's public customer-review RSS (US; CA where noted). Android reviews are all reviews visible on the US Play web page (3 per app). |
| Size | **n = 50.** Open Road 14 (10 iOS-US, 1 iOS-CA, 3 Play), RaceChrono 9, TrackAddict 8, MileIQ 8, Driversnote 8, Zenroad 3 (Play; iOS has no written reviews) |
| Date range | 2025-12-20 to 2026-11-04, plus one Zenroad review from 2024-01-01 (one of only three visible on Play; kept and flagged) |
| Stars | 1★ 15 · 2★ 6 · 3★ 6 · 4★ 9 · 5★ 14 |
| Coding | One coder (A1). Multiple codes allowed per review, each counted once. Each code is marked positive or negative. "Other" covers stability, onboarding, support, hardware pairing and feature requests. Two reviews had no codable content. |
| Supplementary | Enroad (iOS-only, so excluded from the main sample): the 10 most recent US reviews (2026-09-02 to 2026-10-29), reported separately |
| Limitations | Not representative. 43 of 50 are iOS-US, because Play exposes few reviews. Recency slices over-weight recent incidents: TrackAddict's 5.0 rewrite and MileIQ's price rise both happened shortly before or during the window. Reviewers self-select. There was no second coder. Counts describe **this sample only** and are not prevalence. |

## 5. Table 4 — Coded review counts (sample n = 50)

| Code | Reviews mentioning | Positive | Negative | Where concentrated | Example (paraphrased; quotes under 15 words) |
|---|---|---|---|---|---|
| **Capture reliability / data durability** | **17** | 4 | **13** | TrackAddict 4, Open Road 4, Driversnote 6 (4 positive) | Open Road user lost 3k miles and 300 drives after an account issue (2026-05-17). TrackAddict sessions "failed with zero data" (2026-09-14). Driversnote: an update silently turned auto-tracking off (2026-10-19). |
| Battery / heat | **0** | 0 | 0 | — | None in the sample. *Outside the sample:* an Open Road review (2026-03-13) reports overheating with CarPlay, and a TripRank (Digitalstalk) review (2026-08-25) reports heavy battery use. |
| Accuracy (speed, G, timing, scoring calibration) | 9 | 5 | 4 | RaceChrono positive; TrackAddict/Open Road negative | Open Road Android: max speed shown as about 9 mph on a motorway (2026-04-05). TrackAddict: G meter wrong after an update (2026-06-12). |
| UI / design | **17** | 8 | 9 | RaceChrono 6 (mostly negative), Open Road 6 (mixed) | RaceChrono: "5 stars for the features, 1 for UI", with small controls that are hard to use with gloves (2025-12-20). |
| Coaching usefulness | 2 | 1 | 1 | Zenroad | Zenroad: behaviour feedback felt accurate (2026-01-18) vs passenger trips hurting the score (2024). *Outside the sample:* three Open Road reviews (Feb–Mar 2026) say the score penalised necessary or gentle braking. |
| Privacy / permissions | 2 | 0 | 2 | Open Road, Driversnote | Open Road now requires "Always" location, and the reviewer worries about passenger trips (2026-10-25). |
| Price / paywall | **10** | 0 | **10** | MileIQ 5 | MileIQ: price went from about $60/yr to $140/yr (2026-11-02). RaceChrono "lacks ~half the features" expected for $20 (2026-02-23). |
| Sharing / export | 7 | 4 | 3 | Mixed | TrackAddict needs paid RaceRender to get data off the device (2026-07-18). Open Road: the convoy/friend map is broken (2026-04-28). |
| Other (stability, onboarding, support, hardware, requests) | 11 | — | — | Open Road, TrackAddict | Open Road: sign-in and onboarding crashes, CarPlay requests. TrackAddict: no support response for two months (2026-06-02). |

**Supplementary Enroad sample (n = 10, US, 2026-09-02 to 2026-10-29):** 1★ ×7, 3★ ×2, 5★ ×1. Price/paywall 7, capture 3 (missed trips; auto-record will not stay off), sharing 1, UI 1. Older Enroad reviews describe a "spin the wheel" discount screen and subscriptions lost at the TripRank-to-Enroad rename (v4.2, 25 Aug 2026). This contrasts sharply with the 4.82★ aggregate. Reviews for both Open Road and Enroad report being asked to rate the app before using it [S4][S2].

**What this means for v1 (hypotheses):** reliability and durability come before features. A pre-value paywall is the most common reason for a 1★ review among 2026 entrants. Coaching is rarely praised or criticised, which suggests competitors have not yet made it valuable rather than proving users don't want it. Battery cost is under-represented in reviews, so measure it rather than infer it from complaints.

---

## 6. Table 5 — Market-sizing signals

| Signal | Value | Date / scope | Source | Confidence |
|---|---|---|---|---|
| r/cars | 7.4M members | updated 2026-11-05; global | GummySearch [S37] | Medium (third-party mirror of Reddit counts) |
| r/teslamotors · r/TeslaModel3 · r/TeslaModelY · r/teslacanada | 3.5M · 261k · 249k · 18k | 2026-11-01; global | GummySearch [S38] | Medium |
| r/electricvehicles | 452k | listed 2026-11-05 (other mirrors show 425–427k in Jun 2026) | GummySearch [S37][S39] | Medium |
| r/Cartalk · r/askcarguys | 898k · 311k | Nov 2026 | GummySearch [S40][S37] | Medium |
| r/Miata · r/GR86 | 254k · 49k | 2026-11-06 / 2026-10-25 | GummySearch [S40][S41] | Medium |
| r/CarTrackDays · r/Autocross | 42k · 29k | 2026-10-25 / 2026-10-22 | GummySearch [S41][S42] | Medium |
| r/driving (new/everyday drivers) | 108k | 2026-10-26; global, mixed | GummySearch [S43] | Medium |
| r/hypermiling (efficiency) | 7k | **stale: 2025-01-21** | GummySearch [S44] | Low |
| SCCA membership | "70,000+" members; annual membership passed 50,000 in Feb 2023, plus about 20,000 weekend memberships per year | Dec 2025 CEO quote; Feb 2023 club article | [S45][S46] | Medium (vendor/club claim) |
| Discord servers / brand forums (Miata.net, TMC, Bimmerpost) | **Unverified**: no reliable public member counts were found | — | [S47] | — |
| Canada BEVs on road | **488,612** (1.9% of registered vehicles) | 2024 stock | StatCan via Electric Autonomy [S48] | High |
| Canada new ZEV registrations | 9.5% of 1,866,714 new registrations in 2025; ZEVs −34.7% and BEVs −43.1% YoY; Q3 2025: 45,366 ZEVs | full-year 2025 | StatCan [S49][S50] | High |
| US EV stock | AFDC state table, Dec 2023: about 3.56M light-duty EVs (A1's sum of the state figures). Experian 2025 review: EVs = 1.96% of 277.1M vehicles in operation, ≈5.4M by A1's calculation | 2023 / 2025 | AFDC [S51]; Experian via search snippet (PDF text not machine-readable) [S52] | High (AFDC) / Medium-low (Experian derivation) |
| US EVs registered in 2025 | 1,511,549 (−4% YoY) | full-year 2025 | Alliance for Automotive Innovation, via search snippet (page timed out) [S53] | Medium-low |
| Competitor adoption (store bands) | Play: MileIQ / Driversnote / Stride 1M+; RaceChrono 500K+; TrackAddict 100K+; Open Road 10K+; DrivingTracker / Zenroad / AutoBeacon / TripRank 5K+; DOWNFORCE 100+ | 2026-11-06 | Google Play [S26][S29][S32][S33][S34][S35][S36] | High (as bands) |
| Competitor adoption (estimates) | Enroad about 812K installs / 64K MAU. Open Road iOS about 68K / 5K MAU, Android about 13K / 1.5K. DrivingTracker about 5.3K / 434. TrackAddict Android about 322K / 3.7K. SpeedRank "1k+"; Velox "<1k". RaceChrono claims "over 100000 active users" (vendor) | crawled 2026-11-03 to 11-06 | AppGoblin [S4b][S30][S31][S28][S54]; MWM [S55][S25]; App Store [S14] | **Low** (models undisclosed) |
| Detected SDKs (privacy context) | Enroad: AppsFlyer, Firebase, RevenueCat, Superwall, Mapbox. Open Road iOS: Firebase, PostHog, Mapbox, a Google ad-network SDK (AppGoblin also says "no ads") | scanned 2026-11-04 / 11-06 | AppGoblin [S4b][S30] | Low-medium |

**Reading the numbers:** the enthusiast community is large but global (r/cars alone has 7.4M members). The track or autocross niche is small (tens of thousands), which supports positioning DrivePerf on everyday driving rather than track use. EV owners are a real secondary segment: about 0.49M BEVs in Canada and about 3.6–5.4M EVs in the US. But Canadian EV sales fell sharply in 2025, so lead with EV range or efficiency as a feature, not as the hero audience.

---

## 7. Table 6 — Adjacent inspiration: recap, progress and sharing patterns (patterns only)

| Pattern | Who does it (evidence) | Driving adaptation for DrivePerf | Guardrail |
|---|---|---|---|
| Annual personal recap told as a story | Spotify Wrapped: 200M engaged users in 24 h and about 500M shares on day one (secondary report) [S56]. Strava Year in Sport: in-app only, Dec 8, 2025 to Jan 23, 2026, **subscribers only**, needs at least 3 activities [S57] | A "Driving Year" covering smoothest drive, most-improved route, new roads, winter/night km, and EV efficiency streaks | Never feature top speed. Decide whether it is free or paid (§9). |
| Eligibility before insight | Strava (at least 3 activities) [S57]. WHOOP (at least 5 days of data a week; 14 recoveries before the first weekly report) [S58]. Oura (up to two weeks to learn baselines) [S59] | Show a "calibrating" state until N drives establish a baseline. No trend claims before then. | Be transparent about the threshold. |
| Weekly assessment on a fixed day | WHOOP weekly report every Monday [S58] (a WHOOP forum thread says the monthly report moved to an email "Month in Review", undated [S60]) | A weekly Smoothness Review with one focus for next week | One recommendation per week, not ten. |
| Score with named contributors and plain-language bands | Oura Readiness: contributors judged against personal averages; Optimal/Good/Fair/Pay Attention [S59] | Smoothness broken into braking, acceleration, cornering and consistency, each shown against *your* baseline | Bands must not imply safety certification. |
| Personal baseline, not a global rank | Oura 14-day weighted balances [S59] | Compare a drive with similar drives on the same route or context | No public smoothness leaderboard in v1 (hypothesis). |
| Streaks, awards and 7-day friendly competitions | Apple Fitness sharing and competitions; awards for streaks and perfect weeks [S61] | Awards for consistency (a "smooth commute week") and improvement percentage | Never reward elapsed time or speed. |
| Privacy-trimmed maps by default | Strava hides a radius around start/end, a specific address, or the whole map; per-activity overrides (third-party guides; one study warns trimming endpoints alone can still leak locations) [S62][S63]. Competitors: OVERDRIVE (300 m), TripRank Android (auto-crop) | Share cards trimmed by default, home/work privacy zones, and a "hide map" option | Preview exactly what is shared. |
| Short auto-written drive summary | Strava Athlete Intelligence (subscriber AI summaries; critics say it lacks context) [S64][S65] | A one-sentence summary drawn only from measured events | No unverifiable advice. Label provenance. |
| Identity or persona and multiplayer recap | Wrapped "Clubs" personas and "Wrapped Party" (secondary report) [S56] | A driving-style persona built on smoothness traits | No risk-glorifying personas. |
| Vertical share format | Wrapped stories; OVERDRIVE 9:16 posters [S12] | Native 9:16 cards via the iOS/Android share sheet | No location precision beyond what the user chose. |

---

## 8. Table 7 — Gap matrix (features × competitors)

✓ = claimed · ◐ = partial · ✗ = explicitly not offered · — = not mentioned (unverified). Platforms: i = iOS, A = Android.

| Feature | Open Road (i+A) | Enroad (i) | SpeedRank (i) | Rovy (i) | OVERDRIVE (i) | TripRank Eon (A) | DrivingTracker (A) | Zenroad (i+A) | RaceChrono (i+A) | MileIQ / Driversnote (i+A) | **DrivePerf v1 target** |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Phone-only auto trip detection | ✓ | ✓ | ◐ | ✓ | ✗ manual | ✓ | ✓ (Premium) | ✓ | ◐ | ✓ | **✓** |
| Live Activity / widget (iOS) | ✓ | — | — | ✓ | ✓ | n/a | n/a | — | — | ✓ / — | **✓** |
| CarPlay / Watch | ✓ / — | — | — | ✓ / ✓ | — | n/a | n/a | — | — | — / ✓ CarPlay | ◐ (decide) |
| Route/event replay | ◐ | — | — | ✓ | ◐ | ✓ | ◐ | — | ✓ | — | **✓** |
| Smoothness as a named metric | ◐ stat | — | ✓ | ◐ gauge | ✓ Flow | ✗ (score/eco) | ✗ | ✗ | ✗ | ✗ | **✓ hero** |
| *Explained* smoothness (event-linked, baseline-relative) | — | — | — | — | — | ◐ tips | — | — | — | ✗ | **✓ (white space)** |
| Coaching: one practice goal plus follow-up check | — | — | — | — | — | ◐ tips/weekly | ✗ | ◐ | ✗ | ✗ | **✓ (white space)** |
| Weekly recap | ✓ (A) | — | — | ◐ | — | ✓ | ◐ period stats | — | ✗ | ◐ reports | **✓** |
| Monthly / annual "Wrapped"-style recap | — | — | — | — | — | — | — | — | ✗ | ✗ | **✓ (white space)** |
| Privacy-trimmed share card | ◐ crop | — | — | — | ✓ 300 m | ✓ auto-crop | n/a | — | n/a | n/a | **✓ default on** |
| No speed glorification | ✗ (top speed, speed traps) | ✗ | ✗ | ✗ (top-speed board) | ✓ | ✗ (HUD/boards) | ◐ neutral | ◐ | n/a (track) | ✓ | **✓** |
| No "Used to Track" on iOS label | ✓ (but 3rd-party ad purpose) | ✗ | ✓ | ✓ | ✓ | n/a | n/a | ✓ | ✓ | ✗ / ✓ | **✓ aim: minimal or none** |
| Local-first / works without account | ✗ (sync default) | ✗ | — | — | ✓ (on device by default) | ✗ | ✓ | ✗ | ✓ | ✗ | **decide (§9)** |
| Export (GPX/CSV) | — | — | — | ✓ | — | ✓ PDF | ✓ | ✓ | ✓ | ✓ | ◐ |
| iOS + Android parity | ◐ (Android lags) | ✗ | ✗ | ✗ | ✗ ("not yet") | ✗ | ✗ | ✓ | ✓ | ✓ | **✓** |

**White space:** among consumer driving apps, nobody combines **explained, baseline-relative smoothness coaching + Strava-grade weekly, monthly and annual recaps + a no-tracking, local-first data model with privacy-trimmed sharing + native iOS features with Android parity**. Each piece exists somewhere, but none of the apps surveyed has the combination. The positioning risk is **OVERDRIVE**, which is the closest in philosophy. Watch whether it adds auto-start, Android or coaching.

---

## 9. Implications for the v1 PRD and decisions for the product owner

**PRD implications**
1. **Capture and durability are P0.** Automatic start/stop, a Live Activity, recovery after a crash or reboot, protection against update or permission regressions, and no data loss when accounts or subscriptions change. Publish targets for missed trips and stopped trips (test in A3). Evidence: 13 negative capture mentions; DrivingTracker's Oct 2026 fixes; Driversnote's update that turned tracking off; Open Road and Enroad data and subscription losses.
2. **Smoothness must be explainable and fair, not just a number.** Competitors already sell a "smooth" score. DrivePerf's version needs contributors, event-linked evidence, context (passenger, train, necessary braking) and vehicle-agnostic fairness. Reviews already complain about scores that punish necessary braking.
3. **Monetisation without hostility.** No paywall or rating prompt before the first full drive and recap. Keep capture, Smoothness and the weekly recap in a free core. Paid depth could include history, comparisons, exports and the annual recap (pending the decision below). Evidence: 10 of 10 price mentions negative; Enroad 7 of 10; MileIQ's price-rise backlash.
4. **Native polish is the entry ticket on iOS.** A Live Activity is close to standard. CarPlay and Watch are common among stronger apps. Plan Android equivalents (ongoing notification, widgets, Android Auto) for parity.
5. **Make privacy visible.** Aim for an App Store label with no "Used to Track" entry and minimal "Linked" data. Default share cards to trimmed. Avoid attribution or ad SDKs in v1.

**Decisions the product owner must make**
- **D1 Recap paywall:** is the monthly or annual "Driving Year" recap free (growth and sharing loop, like Wrapped) or premium (Strava's 2025 model)?
- **D2 Account and data model:** local-first with optional sync, or account plus cloud sync by default? This choice drives data-loss risk, the privacy label and cross-device recovery.
- **D3 Social scope in v1:** share-out cards only, or an in-app friend graph or leaderboard? Competitors grow through convoys and leaderboards, but most of those rank speed, which conflicts with DrivePerf's no-speed-glorification principle.
- **D4 Hero-metric naming:** keep "Smoothness" (descriptive, but crowded: SpeedRank, Top Driver, Open Road) or brand it distinctly, as OVERDRIVE did with "Flow"?

---

## Sources

All accessed **2026-11-06**. iOS metadata is cross-checked against `https://itunes.apple.com/lookup?id=<ID>&country=us|ca`. iOS reviews come from `https://itunes.apple.com/<cc>/rss/customerreviews/page=1/id=<ID>/sortby=mostrecent/json`.

- [S1] Open Road (iOS) — https://apps.apple.com/us/app/6755834573 · lookup `id=6755834573`
- [S2] Open Road iOS reviews RSS (US, CA) — https://itunes.apple.com/us/rss/customerreviews/page=1/id=6755834573/sortby=mostrecent/json
- [S3] Enroad (iOS) — https://apps.apple.com/us/app/id6757245082 · lookup `id=6757245082`
- [S4] Enroad reviews RSS (US, CA) — https://itunes.apple.com/us/rss/customerreviews/page=1/id=6757245082/sortby=mostrecent/json · rename evidence: https://scout.appaloosa.io/en/apps/ios/io.uros.triprank/4.2
- [S4b] AppGoblin, Enroad — https://appgoblin.info/apps/6757245082 (estimates, low confidence)
- [S5] SpeedRank — https://apps.apple.com/us/app/id6761128659
- [S6] TripRank: GPS Speedometer (Digitalstalk) — https://apps.apple.com/us/app/id6760617806
- [S7] Rovy — https://apps.apple.com/app/id6760185139
- [S8] PACE — https://apps.apple.com/us/app/id6761122094
- [S9] DriveTracker – Driving Log — https://apps.apple.com/us/app/id6754501628
- [S10] ZenRoad (iOS) — https://apps.apple.com/us/app/id1563218393
- [S11] Drive Sense Pro — https://apps.apple.com/us/app/id6754228533
- [S12] OVERDRIVE website (vendor claims) — https://overdriveapps.com/
- [S13] OVERDRIVE (iOS) — https://apps.apple.com/us/app/id6790023849
- [S14] RaceChrono Pro (iOS) — https://apps.apple.com/us/app/racechrono-pro/id1129429340
- [S15] Harry's LapTimer Petrolhead — https://apps.apple.com/us/app/id338257907
- [S16] Harry's LapTimer Rookie lookup — https://itunes.apple.com/lookup?id=307688598&country=ca
- [S17] TrackAddict (iOS) — https://apps.apple.com/us/app/trackaddict/id632355692
- [S18] MileIQ (iOS) — https://apps.apple.com/us/app/id578830929
- [S19] Driversnote (iOS) — https://apps.apple.com/us/app/id924418916
- [S20] Stride (iOS) — https://apps.apple.com/us/app/id1041591359 (CA lookup returns no result)
- [S21] Top Driver. — https://apps.apple.com/us/app/top-driver/id6760957005
- [S22] DriveStats — https://apps.apple.com/us/app/id6755319883
- [S23] DrivingTracker website — https://drivingtrackerapp.com/
- [S24] DriveSidekick website — https://drivesidekick.pages.dev/
- [S25] Velox – GPS Speedometer: https://apps.apple.com/us/app/id6761004156 (404); third-party record https://mwm.ai/de/apps/velox-gps-speedometer/6761004156
- [S26] DrivingTracker (Play) — https://play.google.com/store/apps/details?id=com.richardblaha.drivingtracker&hl=en_US&gl=US
- [S27] DrivingTracker privacy policy (7 May 2026) — https://drivingtrackerapp.com/privacy-policy.html
- [S28] AppGoblin, DrivingTracker — https://appgoblin.info/apps/com.richardblaha.drivingtracker
- [S29] Open Road (Play) — https://play.google.com/store/apps/details?id=com.mikita.openroad&hl=en_US&gl=US
- [S30] AppGoblin, Open Road iOS — https://appgoblin.info/apps/6755834573
- [S31] AppGoblin, Open Road Android — https://appgoblin.info/apps/com.mikita.openroad
- [S32] Zenroad (Play) — https://play.google.com/store/apps/details?id=com.telematicssdk.zenroad&hl=en_US&gl=US
- [S33] AutoBeacon (Play) — https://play.google.com/store/apps/details?id=com.sensight.beaconapp&hl=en_US&gl=US
- [S34] TripRank (Play) — https://play.google.com/store/apps/details?id=com.trippulse.triprank&hl=en_US&gl=US
- [S35] DOWNFORCE (Play) — https://play.google.com/store/apps/details?id=de.downforce.app&hl=en_US&gl=US
- [S36] Play listings: https://play.google.com/store/apps/details?id=com.racechrono.app · …id=com.racechrono.pro · …id=com.hptuners.trackaddict · …id=com.harrys.laptimerpro · …id=com.harrys.laptimer · …id=com.mobiledatalabs.mileiq · …id=com.driversnote.driversnote · …id=com.stridehealth.drive (all `&hl=en_US&gl=US`)
- [S37] GummySearch r/cars — https://gummysearch.com/r/cars
- [S38] GummySearch r/teslamotors — https://gummysearch.com/r/teslamotors
- [S39] Subreddit size search (r/electricvehicles, Jun 2026 mirrors) — https://gummysearch.com/r/electricvehicles ; https://painonsocial.com/companies/rivian
- [S40] GummySearch r/Miata — https://gummysearch.com/r/Miata
- [S41] GummySearch r/CarTrackDays — https://gummysearch.com/r/CarTrackDays
- [S42] GummySearch r/Autocross — https://gummysearch.com/r/Autocross
- [S43] GummySearch r/driving — https://gummysearch.com/r/driving
- [S44] GummySearch r/hypermiling — https://gummysearch.com/r/hypermiling
- [S45] SCCA CEO interview (Dec 2025) — https://epartrade.com/industry-news/michael-cobb-president-ceo-of-scca-details-record-participation-grassroots-growth-and-future-7e91a368
- [S46] SCCA annual membership 50,000 — https://scca.com/articles/2017250-scca-annual-membership-50-000-and-counting
- [S47] Forum member-count search (no usable result) — https://miata.net/misc/history.html
- [S48] Electric Autonomy / StatCan 2024 BEV stock — https://electricautonomy.ca/data-trackers/2025-10-21/registrations-statscan-data-2024-battery-electric-vehicles/
- [S49] StatCan, New motor vehicle registrations Q4/full-year 2025 (2026-03-12) — https://www150.statcan.gc.ca/n1/daily-quotidien/260312/dq260312e-eng.htm
- [S50] StatCan Q3 2025 — https://www.statcan.gc.ca/o1/en/app/8837-new-motor-vehicle-registrations-third-quarter-2025 ; BEV/PHEV split: https://electricautonomy.ca/data-trackers/ev-sales-data/2025-12-16/statscan-q3-2025-ev-sales-canada/
- [S51] AFDC EV registrations by state (Dec 2023) — https://afdc.energy.gov/data/widgets/10962
- [S52] Experian EV 2025 year in review (PDF) — https://www.experian.com/content/dam/marketing/na/automotive/infrographic/EV-Infographic/electric-vehicles-2025-year-in-review.pdf
- [S53] Alliance for Automotive Innovation, Get Connected Q4 2025 — https://www.autosinnovate.org/posts/press-release/2025-q4-get-connected-press-release
- [S54] AppGoblin, TrackAddict Android — https://appgoblin.info/apps/com.hptuners.trackaddict
- [S55] MWM, SpeedRank — https://mwm.ai/apps/speedrank-trip-tracker/6761128659
- [S56] Spotify Wrapped 2025 engagement (secondary) — https://www.musicbusinessworldwide.com/spotify-wrapped-campaign-hit-200m-engaged-users-in-24-hours-a-19-yoy-increase ; https://www.ibtimes.co.uk/record-breaking-spotify-wrapped-2025-sees-200m-engage-500m-shares-day-1760640
- [S57] Strava support, Your Year in Sport — https://support.strava.com/hc/en-us/articles/22067973274509-Your-Year-in-Sport
- [S58] WHOOP Weekly Performance Assessment — https://whoop.com/thelocker/new-weekly-performance-assessment
- [S59] Oura Readiness Score support — https://support.ouraring.com/hc/en-us/articles/360057791533
- [S60] WHOOP community, Month in Review thread — https://www.community.whoop.com/t/new-month-in-review-is-a-huge-disappointment/9035
- [S61] Apple Watch User Guide, Activity sharing/competitions — https://support.apple.com/guide/watch/apd68a69f5c7
- [S62] Strava map visibility (third-party) — https://cdn.road.cc/content/tech-news/stravas-edit-map-visibility-gives-greater-privacy-control-285569
- [S63] Kaspersky, Strava privacy settings — https://www.kaspersky.com/blog/running-apps-privacy-settings-part2-strava/52409
- [S64] Strava Athlete Intelligence (PR Newswire) — https://prnewswire.com/news-releases/stravas-athlete-intelligence-translates-workout-data-into-simple-and-personalized-insights-302266680.html
- [S65] Cycling Weekly critique of Athlete Intelligence — https://cyclingweekly.com/news/strava-says-its-new-ai-feature-is-not-a-novelty-but-i-think-its-pointless
