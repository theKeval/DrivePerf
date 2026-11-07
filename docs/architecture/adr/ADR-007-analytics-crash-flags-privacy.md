# ADR-007: Analytics, crash reporting, feature flags and privacy-preserving defaults

- **Status:** Accepted at Gate 4 (7 Nov 2026); counsel confirms the consent wording (OQ-02)
- **Date:** 2026-11-06, decision revised 2026-11-07
- **Deciders:** Keval Langalia (PO); A10; counsel for OQ-02
- **Related:** PRD F-ANA-01…05, F-PRV-06, F-ONB-08, §3.0.4, §6.4–6.6, OQ-02, OQ-13; [ADR-004](ADR-004-backend-v1-and-migration-seam.md); [research/analytics-crash-privacy.md](../research/analytics-crash-privacy.md)

## Context and problem statement

We need product analytics (WDD, activation, retention), capture-quality telemetry (Q1–Q14), crash/ANR reporting and remote kill switches, while keeping the privacy baseline: **no location, addresses, route/car names, speeds, G values or free text in analytics, logs or crash reports**; analytics and crash reporting by explicit choice (F-ONB-08); no ad IDs; a cookie-less website.

## Decision drivers

Privacy baseline and Law 25 privacy-by-default · cost ($0 at launch) · Spark-compatible · one event schema on both platforms · crash reports that work for the shared Kotlin code on iOS · CI-enforceable rules.

## Considered options

1. **Firebase Analytics + Crashlytics + Remote Config** (native SDKs behind ports), opt-in, with a typed event dictionary.
2. **Firebase Analytics + Remote Config, with Sentry for crashes.**
3. "Counts, not people": a first-party `POST /v1/events` relay to PostHog Cloud EU with no persistent ID, plus Sentry for crashes (the research recommendation).
4. Firebase Analytics and PostHog side by side, plus Sentry.
5. Self-hosted analytics (Umami/PostHog), which needs a server we don't run yet.

## Decision outcome

**Chosen option: 2, Firebase Analytics + Remote Config, with Sentry (EU region) for crash and error reporting.** Keval chose it at Gate 4 on 7 Nov 2026, after the [privacy research](../research/analytics-crash-privacy.md).

- **Analytics stays on Firebase:** free at any scale, the industry default, and Firebase is already in the app for Auth, App Check and Remote Config.
- **Crashes move to Sentry:** its official Kotlin Multiplatform SDK reports crashes from the shared Kotlin code on iOS with proper stack traces. Crashlytics needs an unofficial hook for that. Sentry offers an EU data region on every plan, and it is independent of Firebase, so the VPS move (ADR-010) does not touch it.
- **Not chosen:**
  - Option 1: Kotlin/Native crashes on iOS rely on a community add-on.
  - Option 3: needs a relay we would build and run, and PostHog costs about US$60 a month near 25k DAU.
  - Option 4: two analytics tools double the consent, store declarations and test surface, and give two different numbers for the same metric.
  - Option 5: needs a server.
- **One crash reporter only.** Crashlytics is not linked. Two crash handlers compete for the same signal and exception hooks and lose reports.

All four integrations sit behind the `Analytics`, `CrashReporter` and `FeatureFlags` ports, so a vendor can be swapped later without touching domain code. If we later want identifier-free analytics, option 3 can replace the `Analytics` adapter without an app-wide change.

### Consent and defaults (resolves PRD OQ-02 for architecture; counsel confirms)

- **Opt-in, nothing pre-selected**, separately for "Share usage statistics" and "Send crash reports" (F-ONB-08, F-PRV-06). The same opt-in applies in every country. It is required anyway in the EU, in all of Canada (Quebec users can't be told apart) and in India from May 2027.
- **Firebase Analytics** collection is **disabled at build time** and enabled at runtime only after consent: iOS `FIREBASE_ANALYTICS_COLLECTION_ENABLED = NO` in Info.plist; Android `firebase_analytics_collection_enabled = false` manifest metadata.
- **Sentry is initialised only after crash consent** is read at launch. Turning the toggle on mid-session calls `Sentry.init`; turning it off calls `Sentry.close()` and deletes the cached crash envelopes on disk.
- **No advertising identifiers:** Android `google_analytics_adid_collection_enabled = false` and the `AD_ID` permission removed with `tools:node="remove"`; iOS does not link AdSupport or AppTrackingTransparency. Google Signals and ad personalisation off in the console; Consent Mode: `analytics_storage` granted only with consent, `ad_storage`, `ad_user_data`, `ad_personalization` always denied.
- "Reset analytics ID" calls `resetAnalyticsData()`; turning statistics off stops all analytics network calls (network test, F-ANA-01 AC3).
- WDD and funnels are measured on the **opted-in cohort**, with the opted-in share shown on the dashboard (F-ANA-04 AC2); beta testers consent to full telemetry. Whether a consent-free, identifier-free aggregate count is allowed is an open counsel question, not assumed.
- **Measured without consent:** crash and ANR rates from Xcode Organizer and Play Console Android vitals (users who agreed at system level to share with developers), installs and retention from App Store Connect and Play Console, and subscriptions from RevenueCat. These are the cross-check for staged-rollout halts (F-ANA-03 AC3), because Sentry sees only opted-in users. The data map (S-PRV-01) says Apple or Google may share crash statistics with us if the user allowed it in their phone's settings.

### Event dictionary and enforcement

- Single source: `docs/analytics/events.yaml` (name, properties with types/buckets, owner, feature ID). A Gradle task generates a sealed `AnalyticsEvent` hierarchy in `:shared:domain`; the `Analytics` port accepts only those types. **CI fails** on an undeclared event or property (F-ANA-01 AC1).
- Property types are enums, booleans and **bucketed** numbers only (`items_bucket`, `ms_bucket`); no free strings. A unit test feeds every event through a payload scanner that rejects numeric lat/lon patterns and coordinate-like pairs (F-ANA-01 AC2).
- `capture_quality` (F-ANA-02) is computed in the shared pipeline and carries metres-from-start, percentages and flags, never coordinates.
- Firebase Analytics data retention is set to **2 months**, the minimum, for user-level data.

### Logging and crash reports (Sentry)

- A `Logger` port with levels; release builds keep warnings/errors only. Messages are constant templates plus typed, allow-listed fields; `Location`, `LocationSample`, `Trip` and `Route` types have `toString()` overridden to redact, and a detekt/SwiftLint rule forbids passing them to the logger.
- **SDK:** Sentry Kotlin Multiplatform SDK in `commonMain`, behind `CrashReporter`. It wraps the native Android and Cocoa SDKs, so ANRs (Android) and app hangs (iOS) are reported too.
- **SDK options:**
  - EU-region DSN (Frankfurt);
  - `sendDefaultPii = false`, and `setUser` is never called;
  - screenshots, view hierarchy and Session Replay off;
  - performance tracing off at launch (`tracesSampleRate = 0`), so no performance data is declared;
  - release health (session tracking) on, so crash-free sessions can be measured for F-ANA-03.
- **Scrubbing:** `beforeSend` and `beforeBreadcrumb` run the same coordinate scanner as analytics and drop matching fields. Tags come from an allow-list only: `in_trip`, `capture_mode`, `trip_state`, `db_state`, `app_build`. Breadcrumbs carry screen IDs and state-machine transitions, not data. `in_trip=true` tags capture-time crashes (F-ANA-03 AC2).
- **Sentry organisation settings:** EU data region (chosen at sign-up, it can't be changed later); "Prevent storing of IP addresses" on; the server-side data scrubber on, with an extra rule for coordinate-like values; spike protection on; a per-key rate limit so a crash loop can't use up the monthly quota.
- **Symbols:** R8 mapping files (Sentry Gradle plugin) and dSYMs, including the shared framework's dSYM (`sentry-cli debug-files upload`), are uploaded in CI ([ADR-008](ADR-008-ci-cd-and-environments.md)). Source-context upload stays off. `SENTRY_AUTH_TOKEN` is a GitHub Environment secret in `prod`. The DSN is not secret but is injected at build time like the Firebase config files.
- **Retention and cost:** the Developer plan is free (5,000 errors a month, one user, 30-day retention). Move to the Team plan (about US$26 a month billed annually or US$29 monthly: 50,000 errors and 90-day retention) when more people need access or the quota is reached; expected near 25k DAU (estimates in the research).
- **Account deletion:** Sentry reports are never linked to an account, so there is nothing to delete by user; they expire with the plan's retention.
- Server logs (Cloud Run): request ID, route template, status, latency, user ID hash; never bodies. Log retention: Cloud Logging default. The API does not send to Sentry at launch.

### Feature flags

- Firebase **Remote Config** behind `FeatureFlags`, typed keys (`flag_map_card_enabled`, `flag_ridealong_enabled`, `flag_live_update_promotion`, `min_supported_build`, copy variants). Bundled defaults ship in the app; fetch interval 12 h; apply on next launch (F-ANA-05).
- **Scoring thresholds are never remote** (F-ANA-05 AC2): algorithm changes ship as a versioned release (A12).

### Store declarations

- **Apple:** Usage Data (Product Interaction) for Analytics, and Crash Data for App Functionality, both **not linked** and not tracking. Performance Data is not declared while Sentry tracing is off. `PrivacyInfo.xcprivacy`: `NSPrivacyTracking = false`, no tracking domains, collected-data types as above; the required-reason API codes are confirmed with Xcode's privacy report.
- **Play Data Safety:** App activity (App interactions) and App info and performance (Crash logs, Diagnostics), optional, not shared; Device or other IDs: yes (the Firebase app-instance and installation IDs, and Sentry's random installation ID).
- "Not linked" holds only if we never attach an account ID to Firebase Analytics or Sentry and never join these datasets with account data. This is a rule, checked in review.
- Sentry and Google act as our service providers, so these transfers are not "sharing" under Play's definition.

### Website analytics (cookie-less)

- No Firebase/Google Analytics on the website (it sets cookies). Page views via **Cloudflare Web Analytics** (free, cookie-less, no fingerprinting), loaded only when the browser sends neither Global Privacy Control nor Do Not Track. The A/B variant is assigned per page load without storage and recorded with waitlist sign-ups. If counsel prefers no third-party script at all, replace it with a first-party beacon to `POST /v1/web/events` once the API exists.
- Waitlist bot protection (resolves PRD OQ-13): honeypot field + minimum time-to-submit + Firestore rules that validate fields and size; **no reCAPTCHA** (it can set Google cookies). Escalate to App Check with reCAPTCHA Enterprise only if spam is observed, and say so in the cookie notice.
- Sentry is not used on the website.

### Launch regions

- No launch in China mainland or Russia (Firebase and Sentry are not reliably reachable there, and local data rules conflict with this design).
- Hold South Korea until counsel confirms whether its location-information law notice applies to a foreign app.

### Consequences

- Good: $0 at launch; one event dictionary for both platforms; crash reports work for shared Kotlin code on both platforms; privacy rules are tested, not just documented; crash reporting is independent of where the backend runs.
- Bad: two providers in the privacy policy and store forms (Google and Sentry); Sentry's free plan has one user and 30-day retention.
- Bad: opt-in analytics and crash reports under-count; accepted and made visible on dashboards, with Organizer and Android vitals as the consent-free cross-check.
- Bad: Firebase Analytics keeps a persistent app-instance ID ("Reset analytics ID" in Settings) and Google derives a coarse location from the IP. Accepted: it is opt-in, never linked to the account, and retention is 2 months.
- Bad: BigQuery export (for custom dashboards, F-ANA-04) needs Blaze; Firebase console reports are enough until then.
- **VPS exit:** Firebase Analytics and Remote Config stay on Firebase (ADR-010). Sentry Cloud stays as is; GlitchTip (accepts Sentry events) is the self-hosted option if ever needed, but its iOS dSYM symbolication is unverified. Self-hosted Sentry needs 16 GB of RAM and does not fit the planned VPS.

### Confirmation

- Network tests: statistics off ⇒ zero analytics requests; crash reports off ⇒ Sentry not initialised and zero requests to the Sentry ingest host.
- Payload scanner in CI for analytics events and for Sentry `beforeSend` and `beforeBreadcrumb`.
- A test crash from shared Kotlin code on iOS arrives in Sentry symbolicated, before beta.
- Manual Firebase DebugView audit and a Sentry event review before beta.
- Play Data Safety and Apple privacy label drafts (A11 §5) match the SDK configuration.

## More information

Sources (accessed 6 Nov 2026): Firebase pricing (Analytics, Remote Config 100,000 fetches/day free from 1 Sep 2026) (https://firebase.google.com/pricing); Firebase data disclosure guides for the App Store and Play (A3 [FB4], [FB5]); Sentry pricing (https://sentry.io/pricing/); Sentry KMP SDK (https://docs.sentry.io/platforms/kotlin/guides/kotlin-multiplatform/); Sentry EU data region (https://blog.sentry.io/sentrys-eu-data-region-now-in-early-access); Sentry self-hosted requirements (https://develop.sentry.dev/self-hosted/); GlitchTip install requirements (https://glitchtip.com/documentation/install). Full comparison and country notes: [research/analytics-crash-privacy.md](../research/analytics-crash-privacy.md).
