# Research: product analytics, crash diagnostics and privacy for DrivePerf

- **Date:** 6 Nov 2026 · **Author:** research for A10 / ADR-007 · **Status:** input to Gate 4. **Outcome (7 Nov 2026):** Keval chose Firebase Analytics + Remote Config with **Sentry (EU) for crashes**; the PostHog relay was not adopted. See ADR-007
- **Question:** what is the best, industry-standard way for DrivePerf to collect (a) product usage analytics and (b) crash and performance diagnostics, with the least privacy and compliance friction worldwide?
- **Read with:** [ADR-007](../adr/ADR-007-analytics-crash-flags-privacy.md), [ADR-004](../adr/ADR-004-backend-v1-and-migration-seam.md), [ADR-010](../adr/ADR-010-firebase-now-vs-vps.md), PRD §3.0.4, §6.4–6.6, F-ONB-08, F-PRV-01/06, F-ANA-01…05, OQ-02.
- **Not legal advice.** Items marked **[unverified]** could not be confirmed from a primary source on 6 Nov 2026. Counsel should confirm the consent rules before launch (PRD OQ-02).

Acronyms used throughout: KMP (Kotlin Multiplatform), SDK (software development kit), DAU (daily active users), ATT (Apple's App Tracking Transparency), GDPR (EU General Data Protection Regulation), ePrivacy (EU ePrivacy Directive, Art. 5(3) covers storing or reading data on a device), WDD (Weekly Decoded Drivers, the north-star metric).

---

## 1. Recommendation

Treat analytics as **counts, not people**. The app sends a small, typed set of events with **no persistent identifier** and no location, only after the user opts in. Per-user metrics such as WDD, activation and retention are worked out **on the device**, which then sends a summary event. Events go to **our own API (`POST /v1/events`)**, never straight to a vendor. At launch the API passes them to **PostHog Cloud EU**, which gives us dashboards for free up to 1 million events a month. Later, the same endpoint can write to Postgres on the virtual private server (VPS) without an app release. For crashes, use **Sentry (EU region)** through its official KMP SDK. Back it up with the free, built-in **Xcode Organizer/MetricKit** and **Play Console Android vitals**, which still work when users decline. Firebase Analytics and Crashlytics remain the free fallback. They are the industry default, but they conflict with three of our rules: Google Analytics always derives location from the IP address, every event carries a persistent app-instance ID, and Crashlytics does not officially support KMP on iOS.

- **Analytics:** first-party relay `/v1/events` → PostHog Cloud EU (anonymous events, no person profiles, IP and GeoIP off). No analytics SDK in the app; the `Analytics` port is one Ktor implementation in `commonMain`. **Exit:** point the relay at Postgres on the VPS.
- **Crash and performance:** Sentry KMP SDK, EU data region, `sendDefaultPii = false`, no user set, IP storage off, allow-listed tags. Baseline without consent: Xcode Organizer and Android vitals. **Fallback:** Crashlytics with a Kotlin/Native exception hook. **Exit:** GlitchTip on the VPS (Sentry-protocol compatible).
- **Consent:** **one global policy: opt-in, nothing pre-selected**, with separate "Share usage statistics" and "Send crash reports" choices. It is the same in every country, and refusing is as easy as accepting.
- **Never collected:** coordinates or anything derived from location (including IP-derived country or city), addresses, route, car or place names, speeds, G values, free text, timestamps finer than the day, advertising IDs (IDFA, the iOS advertising ID; AAID, the Android one), the iOS vendor ID (IDFV), a persistent analytics ID, account or user IDs in analytics or crash reports, contacts, and device fingerprints.

## 2. Event taxonomy guidance

**Rules.** Event names are `snake_case` from `docs/analytics/events.yaml`. Properties are enums, booleans or the buckets below, never free strings. The **relay** strips the IP and User-Agent, rounds every timestamp down to the UTC day, and assigns a random ID for each batch (`$process_person_profile: false`). That way even the time of receipt cannot show when a trip happened. Device model and OS build appear only in `capture_quality` (needed for OEM capture bugs).

**Global properties** (every event): `platform` (ios/android), `os_major`, `app_version`, `plan_state` (free/preview/pro/ridealong), `capture_mode` (auto/manual_only), `days_since_install` (exact 0–30, then `31-60`, `61-90`, `91-180`, `181+`), `schema_version`.

**Buckets:** `duration_bucket` <5, 5–15, 15–30, 30–60, 60–120, >120 min · `distance_bucket` <2, 2–10, 10–25, 25–50, 50–100, >100 km · `score_bucket` <50, 50–59, 60–69, 70–79, 80–89, 90–100 · `hours_since_install_bucket` <1, 1–24, 24–72, >72 · `trip_age_bucket` <1 d, 1–7 d, 8–30 d, >30 d · `count_bucket` 0, 1, 2–3, 4–6, 7–10, 11+ · `part_of_day` night/morning/afternoon/evening (used only where the PRD asked for an hour).

| # | Event | When | Allowed properties (besides globals) | Measures |
|---|---|---|---|---|
| 1 | `install_first_open` | first launch after consent | `install_week` (ISO week) | retention denominators |
| 2 | `app_active_day` | once per calendar day the app is used | none | D1/D7/D30 retention via `days_since_install` |
| 3 | `onboarding_step_viewed` | each onboarding screen | `step` (enum of S-ONB IDs) | onboarding funnel (aggregate) |
| 4 | `permission_result` | after each system prompt | `perm` (location_when_in_use/location_always/precise/motion/notifications), `result` (granted/denied/limited) | capture permission grant rate |
| 5 | `onboarding_completed` | end of onboarding | `auto_on`, `precise`, `motion`, `notifications` (bool) | activation input |
| 6 | `analytics_consent_set` | consent screen, only if usage = on | `crash` (bool), `surface` (onboarding/privacy_centre) | opt-in share |
| 7 | `first_trip_recorded` | first finalised driver trip | `mode` (auto/manual), `hours_since_install_bucket`, `quality` | time to first value |
| 8 | `trip_finalised` | each finalised trip | `mode`, `trigger` (enum), `quality` (good/usable/poor/unscorable), `duration_bucket`, `distance_bucket`, `score_bucket` | capture success, score spread |
| 9 | `capture_quality` | each finalised trip | `wake_trigger`, `start_latency_m_bucket` (<100, 100–200, 200–400, 400–800, >800), `start_latency_s_bucket`, `imu_delivery_pct_bucket`, `gnss_coverage_pct_bucket`, `end_delay_bucket`, `battery_pct_per_h_bucket`, `screen_on_bucket`, `os_build`, `device_model`, `thermal_flag`, `low_power_flag` | Q1–Q14 capture targets |
| 10 | `trip_corrected` | user changes trip type | `to` (passenger/transit/bike/walk/other) | false-trip rate |
| 11 | `missed_trip_report` | answer to the missed-trip prompt | `answer` (yes/no), `part_of_day` | missed-trip rate |
| 12 | `story_opened` | Drive Story shown | `source` (notification/list/auto), `completed` (bool) | Story open rate |
| 13 | `activation_reached` | always-on capture granted and first Story viewed | `hours_since_install_bucket` | activation (≤72 h) |
| 14 | `replay_opened` | Trip Replay opened | `source`, `trip_age_bucket` | replay use (P0) |
| 15 | `analysis_opened` | Analysis view opened | `source` | data-tinkerer use |
| 16 | `share_created` | share sheet completed | `style` (enum), `format` (image/video/link), `has_map` (bool) — never the destination | share rate |
| 17 | `ridealong_event` | pass created or redeemed | `action` (created/redeemed/reward_granted) | referral loop |
| 18 | `paywall_viewed` | paywall shown | `trigger` (enum), `offer` (none/preview/founding/intro) | paywall reach |
| 19 | `purchase_completed` | store confirms purchase | `product` (monthly/annual/founding), `offer`, `days_since_install` (global) | conversion (revenue itself comes from RevenueCat) |
| 20 | `pro_preview_ended` | Pro Preview ends | `outcome` (converted/lapsed) | preview conversion |
| 21 | `notification_opened` | local notification tapped | `type` (enum) | recap and Story re-engagement |
| 22 | `week_summary` | first app use after an ISO week closes | `iso_week`, `wdd` (bool), `good_trips_bucket`, `stories_bucket` | **WDD** without a user ID |
| 23 | `privacy_action` | Privacy Centre action done | `action` (download/export/delete_trips/delete_account/backup_on/backup_off) | privacy feature use |

**Volume budget.** At about 6 events per opted-in user per day and 50% opt-in (an assumption, not a measurement), volume is about 0.3 M events/month at 3k DAU, 0.9 M at 10k and 2.3 M at 25k. The PRD lists about 150 event names. Ship this core set first, and add others only through the dictionary review (the CI gate in ADR-007).

## 3. Consent and the first-run flow

### Rules by region (analytics and crash reports)

| Regime | Is consent needed before the first event? | Notes |
|---|---|---|
| EU/EEA (GDPR + ePrivacy) | **Yes.** The European Data Protection Board (EDPB) Guidelines 2/2023 (v2, Oct 2024) say Art. 5(3) covers apps reading or sending device data, not just cookies. Crash reporting is unlikely to count as "strictly necessary". | France's data protection authority (CNIL) exempts some first-party audience measurement from consent, but only under strict conditions and only in France. Do not rely on it. |
| UK (UK GDPR + PECR, the Privacy and Electronic Communications Regulations) | **Not for analytics** since the Data (Use and Access) Act 2025 (core changes in force 5 Feb 2026): statistical analytics may run on opt-out with clear information. Crash reports: treat like analytics **[unverified]**. | Opt-in also satisfies this. |
| Canada: PIPEDA (federal privacy law) | Implied consent can be enough for non-sensitive data, but separate, meaningful choices are best practice (the Office of the Privacy Commissioner, OPC). | |
| Canada: Quebec Law 25 | **Yes, in practice.** Under s. 9.1, privacy settings must default to the highest confidentiality. Under s. 8.1, functions that identify, locate or profile a person must be off by default; the regulator (CAI) reads this as needing express consent. | The App Store or Play country is "CA" for all of Canada, so we cannot tell Quebec apart without location. **All of Canada must therefore be opt-in.** |
| US (CCPA/CPRA, ~20 state laws, Washington MHMDA) | **No** for analytics through a service provider with notice (no "sale" or "sharing"). Precise geolocation is sensitive data, but it is never in analytics. | Washington's My Health My Data Act (MHMDA) covers location only when it could show a visit for health services. Not relevant if analytics has no location; counsel to confirm. |
| Brazil (LGPD) | **No**; the regulator (ANPD) points to legitimate interest for audience analytics, with a documented assessment. | Its guidance covers website cookies; applying it to apps is our extrapolation. |
| India (DPDP Act 2023, Rules notified 13 Nov 2025) | **Yes from 13 May 2027**, when the consent and notice rules apply. Analytics is not one of the Act's "legitimate uses". | DPDP notice: possible duty to offer the notice in the Eighth Schedule languages **[unverified]**. |
| Australia (Privacy Act) | **No**; give notice under the Australian Privacy Principles (APPs). | The Tranche 2 exposure draft (Aug 2026) may add a "fair and reasonable" test; watch it. |
| Japan (APPI + Telecommunications Business Act) | **No**, but the "external transmission" rule may require publishing which data goes to which recipient **[unverified whether DrivePerf is a covered service]**. | Cheap fix: list the destinations in the privacy policy. |
| South Korea (PIPA) | Consent-centric law; **yes** to be safe. | See §6 for the location-law problem. |
| China mainland (PIPL) | Separate consent, plus cross-border transfer rules. | Not launching (§6). |

### Global vs per-region policy

| | Single global opt-in (recommended) | Per region (opt-out in US, UK, BR, AU, JP; opt-in elsewhere) |
|---|---|---|
| Compliance | Meets the strictest regime everywhere, including Quebec and India from May 2027 | Needs the storefront country (not location), which is wrong for travellers and VPN users |
| Data volume | Lower (opted-in share only) | Perhaps 1.5–2× more events in opt-out markets **[estimate]** |
| Build and test | One flow | Two flows, region rules, and tests per region |
| Store forms | Play "optional" = yes; label unchanged | Same |
| Brand ("private by design") | Consistent | Harder to explain |

**Recommendation: single global opt-in.** Review it three months after launch. If the opted-in share is below about 25% and the dashboards stop being useful, ask counsel about opt-out for the US and UK only.

### First-run flow (S-ONB-13, F-ONB-08)

1. S-ONB-13 comes **at the end of onboarding**, after the permission steps, so onboarding events can be sent if the user allows them.
2. There are two toggles, **both off**, each with one sentence: "Share usage statistics: counts of which features are used, with no ID and no location" and "Send crash reports: technical details when the app crashes". Buttons: **Allow both**, **Continue with my choices**. Both have equal visual weight.
3. Before the choice, events are held **in memory only**; they are sent if usage is allowed and dropped if not. They are never written to disk before consent, because storing data on the device for analytics is itself covered by ePrivacy.
4. Sentry is **initialised only after crash consent** is read at launch. Turning the toggle on mid-session calls `init`; turning it off calls `close()` and deletes cached crash envelopes.
5. Do not ask again. Beta testers give separate full-telemetry consent (ADR-007).

### Mapping to the Privacy Centre (F-PRV-01, F-PRV-06)

| Control (S-PRV-07) | Default | Effect |
|---|---|---|
| Share usage statistics | Off | On: the queue sends to `/v1/events`. Off: the queue is emptied and the network test expects zero calls (F-PRV-06 AC2). |
| Send crash reports | Off | Sentry is initialised or closed, and the on-disk crash cache is cleared. |
| ~~Reset analytics ID~~ | n/a | **Remove.** There is no ID to reset. Replace with the line "Statistics carry no identifier, so they can't be linked to you." |
| Data map (S-PRV-01) | | Add: "Apple or Google may also share crash statistics with us if you allowed it in your phone's settings." |

**Measured without consent:** crash rate and ANR (Application Not Responding) rate from Organizer and Android vitals (users who opted in at system level); installs, retention and sessions from App Store Connect and Play Console; subscriptions from RevenueCat (needed to provide the service). Use these as the **halt signal for staged rollouts** (F-ANA-03 AC3), because Sentry sees only opted-in users.

## 4. Store disclosures (draft for the recommended setup)

These rows cover analytics and diagnostics only. Keep the other PRD §6.4 rows (backup location, sensor data, email/name/user ID, purchases) as they are.

### Apple privacy nutrition label

| Data type | Purpose | Linked to user? | Used to track? | Source |
|---|---|---|---|---|
| Usage Data › Product Interaction | Analytics | No | No | `/v1/events` |
| Diagnostics › Other Diagnostic Data | Analytics, App Functionality | No | No | `capture_quality` |
| Diagnostics › Crash Data | App Functionality | No | No | Sentry |
| Diagnostics › Performance Data | App Functionality | No | No | Sentry (app start, hangs), only if tracing is on |
| Identifiers › Device ID | App Functionality | No | No | **Only if** Firebase Remote Config/App Check stay (they send the Firebase installation ID) **[check]** |

- Optional data must still be declared: Apple says data collected on an ongoing basis after a permission request must be disclosed.
- **"Not linked" holds only if** we never attach an account ID, never set a Sentry user, and never join these datasets with account data. Write that into ADR-007 as a rule.
- **ATT:** not needed. Nothing is joined with other companies' data for advertising, and nothing goes to data brokers. Do not link `AdSupport` or `AppTrackingTransparency`.
- **Privacy manifest (`PrivacyInfo.xcprivacy`):** `NSPrivacyTracking = false`, no tracking domains, and collected-data types as in the table. Required-reason APIs likely used: UserDefaults `CA92.1`, file timestamps `C617.1`, system boot time `35F9.1`, disk space `E174.1` (check space before writing trip files). **[Codes from memory; verify in Xcode's privacy report.]** Also scan the Kotlin/Native shared framework, which can call file-timestamp APIs. Sentry Cocoa ≥ 8.25 ships its own manifest.

### Google Play Data safety

| Category › type | Collected | Shared | Optional | Purposes | Ephemeral |
|---|---|---|---|---|---|
| App activity › App interactions | Yes | No (service provider) | **Yes** | Analytics | No |
| App info and performance › Crash logs | Yes | No | **Yes** | App functionality, Analytics | No |
| App info and performance › Diagnostics | Yes | No | **Yes** | App functionality, Analytics | No |
| Device or other IDs | Yes | No | Yes for Sentry's random installation ID; **No** if Firebase installation ID (Remote Config/App Check) stays | App functionality | No |
| Location (approximate or precise) | Not for analytics or diagnostics | — | — | — | — |

- All data is encrypted in transit. "Optional" is allowed because every user in every region can opt in (Play's rule).
- Transfers to PostHog and Sentry acting for us count as **service providers**, so they are not "sharing".

## 5. Comparison tables

Prices and limits are from vendor pages on 6 Nov 2026 unless marked. Volume assumptions are in §2.

### 5a. Analytics: cost, residency, KMP fit, lock-in

| Option | Free tier | Cost at ~25k DAU (≈2.3 M events/mo) | Data residency | KMP fit | Lock-in / migration |
|---|---|---|---|---|---|
| **Firebase / Google Analytics** | Unlimited, $0 | $0 | Google global facilities | Two native SDKs; no official KMP SDK (GitLive is unofficial) | Medium: raw data only via BigQuery export (sandbox on Spark, 1 M events/day export cap); the event schema is ours |
| **TelemetryDeck** | 50k events/mo for accounts created after 1 Jul 2026 (100k for older ones) | Paid tiers at 1.5 M / 2.5 M / 5 M; **prices not published in text [unverified]** | EU (Germany) per vendor | Swift SDK + Kotlin SDK (Android/JVM, JitPack); no KMP target; plain HTTP possible | Low–medium; no self-hosting |
| **PostHog Cloud EU** (recommended sink) | 1 M events/mo | ≈ $50 (1–2 M at $0.00005) + ≈ $10 → **≈ $60/mo** | EU (Frankfurt) | Via our relay: one Ktor client in `commonMain`. Native SDKs exist but are not needed | Low: the relay owns the endpoint; sink is swapped server-side |
| PostHog self-hosted | Free software | VPS | ours | same | **Not on KVM 2:** needs 4 vCPU and 16 GB RAM; self-hosting is "officially unsupported" |
| **Aptabase** | 20k events/mo | $40 (2 M) to $75 (5 M) | EU or US | Swift and Kotlin (Android) SDKs; no KMP; HTTP possible | Low: AGPL-3.0 server, self-hostable (ClickHouse; hardware not documented) |
| Plausible / Umami | Paid / self-host | — | EU / ours | **No official app SDK**; Events API or `/api/send` only | Low, but web-centric metrics, no app features |
| Mixpanel / Amplitude | Mixpanel 1 M events/mo; Amplitude **conflicting figures (10k–50k MTU) [unverified]** | Over the free tier, enterprise pricing | US default, EU available | Native SDKs, no official KMP | High (proprietary querying) |
| **DIY `/v1/events` → Postgres** | n/a | ≈ $0 extra on the VPS | ours (VPS region) | Same relay client | None; but we build dashboards (Metabase/Grafana) and run them |

### 5b. Analytics: privacy, ATT, labels, consent

| Option | Default data, and can it be turned off? | ATT tracking? | Apple label | Play Data safety | Consent (EU / Quebec / elsewhere) |
|---|---|---|---|---|---|
| Firebase / GA | App-instance ID always; **location derived from masked IP** (city can be turned off per region, country remains); auto events (first_open, session_start, screen_view, in-app purchase); Signals, ads and data sharing are console switches | No, if Signals, ads linking and IDFA are off; yes if they are turned on | Product Interaction + Device ID + **Coarse Location** (likely) | App interactions, Device or other IDs, Approximate location (likely) | Opt-in / opt-in / notice. Google Analytics terms apply, not the Firebase processing terms |
| TelemetryDeck | Random user ID, salted and hashed twice; device and OS metadata | No | Product Interaction, not linked (vendor's position) | App interactions | Vendor says no consent needed; under ePrivacy a stored ID still needs it in the EU → opt-in |
| PostHog via relay | Only what we send; relay removes IP and UA; EU orgs default to IP capture off; GeoIP enrichment can be disabled | No | Product Interaction, Other Diagnostic Data, not linked | App interactions, Diagnostics | Opt-in / opt-in / notice |
| Aptabase | No device IDs; **server hashes IP + User-Agent daily** and derives country from IP | No, but the IP+UA hash is close to Apple's fingerprinting rule | Product Interaction + Coarse Location | App interactions, Approximate location | Vendor says no banner needed; same ePrivacy caution |
| Plausible / Umami | Same daily IP+UA hash pattern | Same caution | + Coarse Location | + Approximate location | Same |
| Mixpanel / Amplitude | Device ID, IP→geo by default; both can be disabled | No unless sent to ad networks | Product Interaction, Device ID, Coarse Location | Same | Opt-in / opt-in / notice |
| DIY | Whatever we send | No | Product Interaction | App interactions | Same as PostHog via relay; lowest residual risk |

### 5c. Crash and performance

| Option | Free tier | Default data, can it be turned off? | Residency | KMP fit | Labels | Lock-in |
|---|---|---|---|---|---|---|
| **Firebase Crashlytics** | Unlimited, $0 | Stack trace, app state, device metadata, Crashlytics installation UUID, Firebase installation ID; 90-day retention | Global (Google) | **No official KMP support.** Kotlin crashes on iOS show unreadable traces (firebase-ios-sdk #15512, open as of Jun 2026); workaround is a manual `unhandledExceptionHook` → `FIRExceptionModel`, or Touchlab's CrashKiOS **[current compatibility unverified]** | Crash Data, Device ID (installation); not linked | Medium; ties to Firebase |
| **Sentry Cloud** (recommended) | Developer: 5k errors/mo, 1 user, 30-day lookback; Team $26/mo (annual) or $29/mo for 50k | Stack trace, device/OS, breadcrumbs, installation ID; `sendDefaultPii` off ⇒ no IP or headers from the SDK; turn on "prevent storing IP" on the server; data scrubbing | **EU (Frankfurt) or US, on all plans** | **Official KMP SDK** (Gradle plugin adds it to `commonMain`, installs Sentry Cocoa through SwiftPM; min iOS 15 from 0.28.0) | Crash Data (+ Performance Data); not linked | Low: open protocol; GlitchTip accepts Sentry SDK events |
| Sentry self-hosted | Free software | same | ours | same | same | **Not realistic on 8 GB**: minimum 4 cores and 16 GB RAM + 16 GB swap (32 GB recommended) |
| GlitchTip (self-hosted exit) | Free software | same SDKs | ours | same | same | Runs on 256–512 MB + Postgres; **iOS dSYM symbolication support unverified** |
| Bugsnag (SmartBear Insight Hub) | 7.5k events/mo, 1 user | Standard crash data | US; EU **[unverified]** | Through CrashKiOS | Crash Data | Medium |
| Embrace | 1 M sessions/**year** | Full session telemetry (heavier than we need) | US **[unverified]** | KMP **[unverified]** | Crash, Performance, Product Interaction | Medium; background sessions are billed, which hurts a capture app |
| **Xcode Organizer / MetricKit** | Free, no SDK | Apple collects from users who opted in to share with developers; app-side MetricKit payloads are unsymbolicated | Apple | Native API; Organizer symbolicates with the shared framework's dSYM | **None** if we only use Organizer; declare it if the app uploads MetricKit payloads | None |
| **Play Console Android vitals** | Free, no SDK | Users who agreed to share usage data; bad-behaviour thresholds: crash 1.09%, ANR 0.47% | Google | n/a | None | None |

## 6. Country notes

| Market | Launch? | Why |
|---|---|---|
| EU/EEA, UK, Switzerland | Yes | Global opt-in covers ePrivacy and UK PECR. EU→US transfers: Sentry and PostHog in the EU; Firebase backup relies on the EU–US Data Privacy Framework (Google is certified). |
| Canada incl. Quebec | Yes | Opt-in everywhere in Canada (Quebec can't be told apart). Law 25 privacy impact assessment (PIA) already planned for backup; add the analytics relay and Sentry to it. |
| US | Yes | No sale or sharing; precise location only for backup and with consent (PRD §6.6). MHMDA risk is low; do not infer health or set geofences at health facilities. |
| Brazil, Australia, Japan | Yes | Notice-based regimes; our opt-in goes further. Japan: list analytics and crash recipients in the policy. |
| India | Yes | Opt-in already meets DPDP consent from 13 May 2027. Recheck the notice-language rule before that date. |
| **South Korea** | **Hold at first** | The Location Information Act appears to require a location-based service (LBS) notification to the Korea Communications Commission (KCC), including for foreign apps; it has been enforced against Apple, Google and Uber **[foreign-app details unverified]**. Launch after Korean counsel confirms. |
| **China mainland** | **No** | Apple requires an Internet Content Provider (ICP) filing number, which generally needs a Chinese entity or local hosting. App filing with MIIT (the Ministry of Industry and Information Technology). PIPL separate consent and cross-border rules. Mapping rules (ADR-005 maps). Google Play is not available. **Not realistic for a solo Canadian developer.** |
| Russia | No | Data localisation law and payment restrictions **[not researched in depth]**. |
| Turkey | Yes, but check | KVKK (Turkey's data protection law): standard contracts must be notified within 5 business days (2024 regime); foreign controllers may need VERBIS (data controller registry) registration **[unverified for us]**. |
| Sanctioned markets | No | The stores don't serve them anyway. |

## 7. Suggested changes to ADR-007 (do not edit the ADR here)

- **Analytics sink:** replace "Firebase Analytics" with a **first-party relay `POST /v1/events` → PostHog Cloud EU** (anonymous events, `$process_person_profile: false`, GeoIP and IP capture off). Remove the analytics SDK from both apps. Implement the `Analytics` port once in `commonMain`. Add an exit trigger: move the sink to Postgres on the VPS when the forecast passes 1.5 M events/month or the PostHog bill passes US$50/month, or at the ADR-010 migration.
- **No persistent analytics identifier:** compute WDD, activation and retention on the device (`week_summary`, `activation_reached`, `app_active_day`). The relay rounds timestamps to the UTC day. Drop "Reset analytics ID" / `resetAnalyticsData()` from F-PRV-06. Change PRD §3.0.4 from "finer than the hour" to "finer than the day" (`missed_trip_report.hour_bucket` → `part_of_day`). Start with the ~23-event core set in §2.
- **Crash reporting:** replace Crashlytics with **Sentry (EU region) via the official KMP SDK**: `sendDefaultPii = false`, server-side IP storage off, no `setUser`, tag allow-list (`in_trip`, `capture_mode`, `trip_state`, `db_state`), tracing sampled low or off. Upload dSYMs (debug symbol files) and R8 mapping files in CI. Budget US$0 at launch and the Team plan (~US$26–29/month) near 25k DAU. Note that self-hosted Sentry does not fit KVM 2; the exit is GlitchTip, and the fallback is Crashlytics with the Kotlin/Native hook.
- **Consent and measurability:** **global opt-in, both toggles unticked**, with equal-weight buttons and Sentry initialised only after consent. Use **Xcode Organizer, Android vitals, store analytics and RevenueCat** as the consent-free baseline for crash-free rate, rollout halts (F-ANA-03 AC3) and conversion. Remove the Firebase-specific Info.plist/manifest flags.
- **Feature flags:** consider serving flags as a static, signed JSON from `/v1/config` (no identifier) instead of Remote Config. If Remote Config or App Check stay, declare the Firebase installation ID (Device ID, App Functionality, not linked, **not optional**) on both store forms.
- **Disclosures and availability:** adopt the §4 label and Data safety rows, privacy-manifest entries and the "never link analytics or crash data to accounts" rule. Exclude China mainland (and Russia); hold South Korea until counsel confirms the location-law position.

## 8. Sources (all accessed 6 Nov 2026)

- Firebase pricing: https://firebase.google.com/pricing
- Firebase privacy and data processing (retention, locations, DPF): https://firebase.google.com/support/privacy
- Firebase App Store data collection guide: https://firebase.google.com/docs/ios/app-store-data-collection
- Firebase Play data disclosure guide: https://firebase.google.com/docs/android/play-data-disclosure
- Google Analytics for Firebase data collection: https://support.google.com/analytics/answer/10285841
- GA4 granular location and device data: https://support.google.com/analytics/answer/12002752
- Consent mode for apps: https://support.google.com/analytics/answer/13802165
- Firebase BigQuery export (Spark sandbox): https://support.google.com/firebase/answer/6318765
- GA4 data retention (secondary): https://usercentrics.com/knowledge/ga4-data-retention/
- Crashlytics KMP issue #15512: https://github.com/firebase/firebase-ios-sdk/issues/15512
- CrashKiOS: https://github.com/touchlab/CrashKiOS
- TelemetryDeck pricing update (Jul 2026): https://telemetrydeck.com/blog/pricing-update-2026/
- TelemetryDeck vs Firebase: https://telemetrydeck.com/telemetrydeck-vs-google-firebase-analytics/
- TelemetryDeck glossary (hashing): https://telemetrydeck.com/docs/glossary/
- TelemetryDeck Kotlin SDK: https://github.com/TelemetryDeck/KotlinSDK
- PostHog product analytics pricing: https://posthog.com/docs/product-analytics/pricing
- PostHog capture API: https://posthog.com/docs/api/capture
- PostHog data storage and IP controls: https://posthog.com/docs/privacy/data-storage
- PostHog self-hosting: https://posthog.com/docs/self-host
- Aptabase pricing: https://aptabase.com/pricing
- Aptabase repository: https://github.com/aptabase/aptabase
- Aptabase privacy policy and about page: https://aptabase.com/legal/privacy · https://aptabase.com/about
- Aptabase self-hosting: https://github.com/aptabase/self-hosting
- Plausible mobile events discussion: https://github.com/plausible/analytics/discussions/677
- Umami for mobile apps (blog, secondary): https://codeskraps.com/posts/2026/umami_mobile_analytics/
- Mixpanel / Amplitude free tiers (secondary): https://www.costbench.com/compare/mixpanel-vs-amplitude/ · https://amplitude.com/en-us/blog/amplitude-free-platform
- Sentry pricing: https://sentry.io/pricing/
- Sentry self-hosted requirements: https://develop.sentry.dev/self-hosted/
- Sentry KMP SDK: https://docs.sentry.io/platforms/kotlin/guides/kotlin-multiplatform/
- Sentry EU data region: https://blog.sentry.io/sentrys-eu-data-region-now-in-early-access
- GlitchTip install requirements: https://glitchtip.com/documentation/install
- Bugsnag pricing: https://bugsnag.com/pricing · secondary: https://cubeapm.com/blog/bugsnag-pricing-and-review/
- Embrace pricing: https://embrace.io/pricing
- Android vitals: https://developer.android.com/topic/performance/vitals
- MetricKit and Organizer (secondary): https://www.luciq.ai/blog/what-metrickit-means-for-in-process-crash-reporting
- Apple user privacy and data use (ATT, tracking): https://developer.apple.com/app-store/user-privacy-and-data-use/
- Apple app privacy details: https://developer.apple.com/app-store/app-privacy-details/
- Apple required-reason APIs: https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api
- Apple third-party SDK requirements: https://developer.apple.com/news/?id=r1henawx
- Google Play Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- EDPB Guidelines 2/2023 on Art. 5(3) ePrivacy (v2): https://www.edpb.europa.eu/documents/530_en
- CNIL analytics exemption: https://www.cnil.fr/en/sheet-ndeg16-use-analytics-your-websites-and-applications
- UK Data (Use and Access) Act commencement (secondary): https://bratby.law/data-use-and-access-act-2025-commencement/ · https://www.didomi.io/blog/data-use-and-access-act-duaa-2025
- Quebec Law 25 ss. 8.1 and 9.1: https://www.mccarthy.ca/en/insights/blogs/techlex/quebecs-law-25-and-cookies-not-so-cookie-cutter
- India DPDP Rules 2025 phases: https://ca.hoganlovells.com/en/publications/indias-digital-personal-data-protection-act-2023-brought-into-force- · https://yourstory.com/2025/11/digital-personal-data-protection-dpdp-rules-2025-rollout-over-18-months
- Brazil ANPD cookie guide: https://www.mattosfilho.com.br/en/unico/brazilian-data-protection/
- Washington MHMDA: https://natlawreview.com/article/washington-s-my-health-my-data-act-what-types-data-are-regulated-under-washington-s
- Australia Tranche 2 exposure draft: https://www.hsfkramer.com/en_US/insights/2026-09/the-draft-tranche-2-privacy-act-reforms-whats-there-whats-new-and-whats-missing
- Japan external transmission rule: https://iapp.org/news/a/taking-stock-of-japans-external-data-transmission-rule/
- South Korea PIPA and Location Information Act: https://www.dlapiperdataprotection.com/index.html?c=KR&t=law · https://digitalpolicyalert.org/event/20667-issued-communications-commission-ruling-following-investigation-into-apples-compliance-with-location-information-protection-laws · https://gpg-pdf.chambers.com/view/158325293/395
- China app and ICP filing: https://techinsights.linklaters.com/post/102j72l/china-the-new-app-filing-regime-keeps-mobile-apps-under-tight-scrutiny
- Turkey KVKK standard contracts: https://thelens.slaughterandmay.com/post/102jxqd/navigating-turkiyes-updated-international-data-transfer-rules-what-you-need-to
