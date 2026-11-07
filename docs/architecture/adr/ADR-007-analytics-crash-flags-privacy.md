# ADR-007: Analytics, crash reporting, feature flags and privacy-preserving defaults

- **Status:** Proposed (for Gate 4)
- **Date:** 2026-11-06
- **Deciders:** Keval Langalia (PO); A10; counsel for OQ-02
- **Related:** PRD F-ANA-01…05, F-PRV-06, F-ONB-08, §3.0.4, §6.4–6.6, OQ-02, OQ-13; [ADR-004](ADR-004-backend-v1-and-migration-seam.md)

## Context and problem statement

We need product analytics (WDD, activation, retention), capture-quality telemetry (Q1–Q14), crash/ANR reporting and remote kill switches, while keeping the privacy baseline: **no location, addresses, route/car names, speeds, G values or free text in analytics, logs or crash reports**; analytics and crash reporting by explicit choice (F-ONB-08); no ad IDs; a cookie-less website.

## Decision drivers

Privacy baseline and Law 25 privacy-by-default · cost ($0) · Spark-compatible · one event schema on both platforms · CI-enforceable rules.

## Considered options

1. **Firebase Analytics + Crashlytics + Remote Config** (native SDKs behind ports), opt-in, with a typed event dictionary.
2. PostHog / Amplitude / TelemetryDeck + Sentry + a flags service.
3. Self-hosted analytics (Umami/PostHog) — needs a server we don't run yet.

## Gate 4 research update (6 Nov 2026): proposed revision, pending Keval's choice

Keval asked for research into analytics that collect the minimum and work for publishing in almost every country. The result is [research/analytics-crash-privacy.md](../research/analytics-crash-privacy.md). It recommends changing the chosen option. **Option 1 below stays the decision of record until Keval picks one.** The consent rules, event dictionary, logging rules and website analytics apply to either option.

| | Option 1 (current) | Proposed revision ("counts, not people") |
|---|---|---|
| Usage analytics | Firebase Analytics SDK on the device | No analytics SDK. The app sends ~23 typed events to our own `POST /v1/events` (one Ktor adapter in `commonMain`). The API strips the IP and User-Agent, rounds timestamps to the day and forwards to **PostHog Cloud EU** (anonymous events, no person profiles, GeoIP off) |
| Identifier | Persistent app-instance ID ("Reset analytics ID" in Settings) | **None.** WDD, activation and retention are computed on the device and sent as summary events (`week_summary`, `activation_reached`) |
| Location from IP | Google Analytics always derives a coarse location from the IP | None (the relay drops the IP before forwarding) |
| Crash reporting | Crashlytics. iOS Kotlin/Native crashes need a community hook; no official KMP support | **Sentry, EU region**, official KMP SDK, `sendDefaultPii = false`, no user set, allow-listed tags. Crashlytics is the fallback |
| Users who decline | Nothing | Xcode Organizer/MetricKit and Play Console Android vitals (aggregate, from the OS) |
| Cost at ~25k DAU | $0 | PostHog free to 1M events/month, then about US$60/month at 25k DAU with 50% opt-in; Sentry free to start, then about US$26–29/month (estimates in the research) |
| VPS exit | Stays on Firebase | The relay writes to Postgres instead of PostHog (no app release); GlitchTip, which accepts Sentry events, replaces Sentry. Self-hosted Sentry needs 16 GB RAM and doesn't fit KVM 2 |
| Store forms | Declares the Firebase IDs and diagnostics | Smaller: no identifiers, "not linked to you" usage and diagnostics |

The revision also proposes:
- one global opt-in, nothing pre-selected, the same in every country. Opt-in is required anyway in the EU, all of Canada (Quebec users can't be told apart) and India from May 2027;
- feature flags served from `/v1/config`, or else declaring the Firebase installation ID on both store forms if Remote Config stays;
- no launch in China mainland or Russia, and holding South Korea until counsel confirms its location-information law rules (see the research's country notes).

Items the research could not verify: whether Korea's location-law notice applies to foreign apps; TelemetryDeck prices since July 2026; whether GlitchTip can symbolicate iOS crashes; the Apple required-reason API codes (confirm in Xcode).

**Recommendation (A10):** adopt the revision. It fits the "no location in analytics" rule better and keeps analytics behind our API like everything else. It costs nothing at launch, and the paid tiers start only well above launch scale. If Keval prefers $0 at every scale and the industry default, keep option 1 with its consent rules.

## Decision outcome

**Chosen option: 1** (decision of record; see the Gate 4 research update above for the proposed revision). Free on Spark, first-party with App Check and Crashlytics symbolication, and isolated behind the `Analytics`, `CrashReporter` and `FeatureFlags` ports so it can be swapped later without touching domain code.

### Consent and defaults (resolves PRD OQ-02 for architecture; counsel confirms)

- **Opt-in, nothing pre-selected**, separately for "Share usage statistics" and "Send crash reports" (F-ONB-08, F-PRV-06). Collection is **disabled at build time** and enabled at runtime only after consent: iOS `FIREBASE_ANALYTICS_COLLECTION_ENABLED = NO`, `FirebaseCrashlyticsCollectionEnabled = NO` in Info.plist; Android `firebase_analytics_collection_enabled = false`, `firebase_crashlytics_collection_enabled = false` manifest metadata.
- **No advertising identifiers:** Android `google_analytics_adid_collection_enabled = false` and the `AD_ID` permission removed with `tools:node="remove"`; iOS does not link AdSupport or AppTrackingTransparency. Google Signals and ad personalisation off in the console; Consent Mode: `analytics_storage` granted only with consent, `ad_storage`, `ad_user_data`, `ad_personalization` always denied.
- "Reset analytics ID" calls `resetAnalyticsData()`; turning statistics off stops all analytics network calls (network test, F-ANA-01 AC3).
- WDD and funnels are measured on the **opted-in cohort**, with the opted-in share shown on the dashboard (F-ANA-04 AC2); beta testers consent to full telemetry. Whether a consent-free, identifier-free aggregate count is allowed is an open counsel question, not assumed.

### Event dictionary and enforcement

- Single source: `docs/analytics/events.yaml` (name, properties with types/buckets, owner, feature ID). A Gradle task generates a sealed `AnalyticsEvent` hierarchy in `:shared:domain`; the `Analytics` port accepts only those types. **CI fails** on an undeclared event or property (F-ANA-01 AC1).
- Property types are enums, booleans and **bucketed** numbers only (`items_bucket`, `ms_bucket`); no free strings. A unit test feeds every event through a payload scanner that rejects numeric lat/lon patterns and coordinate-like pairs (F-ANA-01 AC2).
- `capture_quality` (F-ANA-02) is computed in the shared pipeline and carries metres-from-start, percentages and flags, never coordinates.

### Logging and crash reports

- A `Logger` port with levels; release builds keep warnings/errors only. Messages are constant templates plus typed, allow-listed fields; `Location`, `LocationSample`, `Trip` and `Route` types have `toString()` overridden to redact, and a detekt/SwiftLint rule forbids passing them to the logger.
- Crashlytics custom keys from an allow-list (`in_trip`, `capture_mode`, `trip_state`, `db_state`, `app_build`); breadcrumbs carry screen IDs and state-machine transitions, not data. `in_trip=true` tags capture-time crashes (F-ANA-03 AC2). dSYMs and R8 mapping files are uploaded in CI ([ADR-008](ADR-008-ci-cd-and-environments.md)).
- Server logs (Cloud Run): request ID, route template, status, latency, user ID hash; never bodies. Log retention: Cloud Logging default.

### Feature flags

- Firebase **Remote Config** behind `FeatureFlags`, typed keys (`flag_map_card_enabled`, `flag_ridealong_enabled`, `flag_live_update_promotion`, `min_supported_build`, copy variants). Bundled defaults ship in the app; fetch interval 12 h; apply on next launch (F-ANA-05).
- **Scoring thresholds are never remote** (F-ANA-05 AC2): algorithm changes ship as a versioned release (A12).

### Website analytics (cookie-less)

- No Firebase/Google Analytics on the website (it sets cookies). Page views via **Cloudflare Web Analytics** (free, cookie-less, no fingerprinting), loaded only when the browser sends neither Global Privacy Control nor Do Not Track. The A/B variant is assigned per page load without storage and recorded with waitlist sign-ups. If counsel prefers no third-party script at all, replace it with a first-party beacon to `POST /v1/web/events` once the API exists.
- Waitlist bot protection (resolves PRD OQ-13): honeypot field + minimum time-to-submit + Firestore rules that validate fields and size; **no reCAPTCHA** (it can set Google cookies). Escalate to App Check with reCAPTCHA Enterprise only if spam is observed, and say so in the cookie notice.

### Consequences

- Good: $0; one dictionary for both platforms; privacy rules are tested, not just documented.
- Bad: opt-in analytics under-counts; accepted and made visible on dashboards.
- Bad: BigQuery export (for custom dashboards, F-ANA-04) needs Blaze; Firebase console reports are enough until then.

### Confirmation

Network tests (stats off ⇒ zero analytics requests); payload scanner in CI; manual Debug View audit before beta; Play Data Safety and Apple privacy label drafts (A11 §5) match the SDK configuration.

## More information

Sources (accessed 6 Nov 2026): Firebase pricing (Analytics, Crashlytics, Remote Config 100,000 fetches/day free from 1 Sep 2026) (https://firebase.google.com/pricing); Firebase data disclosure guides for the App Store and Play (A3 [FB4], [FB5]).
