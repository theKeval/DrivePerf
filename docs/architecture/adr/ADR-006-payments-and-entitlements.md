# ADR-006: Payments and entitlements — RevenueCat, one `pro` entitlement, server mirror

- **Status:** Proposed (for Gate 4)
- **Date:** 2026-11-06
- **Deciders:** Keval Langalia (PO); A10
- **Related:** PRD §3.13 (F-SUB-01…08), F-ACC-02 AC3, F-SHR-05 (Ride-along), decisions log (prices, 14-day Preview, retention); [ADR-004](ADR-004-backend-v1-and-migration-seam.md)

## Context and problem statement

Pro is sold through StoreKit 2 and Play Billing (US$6.99/mo, US$39.99/yr; CA$8.99/CA$49.99; founding first year US$29.99/CA$39.99; no weekly, no lifetime). Every user gets a **14-day no-card Pro Preview**; beta testers get free or extended Pro; Ride-along passes grant Pro to friends. Purchases must work **without an account** and be restorable. The server must know who is Pro to authorise backup uploads and to run the 30-day post-Pro retention clock.

## Decision drivers

One entitlement model on both stores · works anonymously (local-first) · server-verifiable · no card for the Preview · portable across a backend migration · solo-developer effort.

## Considered options

1. **RevenueCat** (SDK + dashboard + webhooks + REST API).
2. Direct StoreKit 2 + Play Billing with our own receipt validation server.
3. Adapty / Qonversion (similar to RevenueCat).

## Decision outcome

**Chosen option: 1, RevenueCat**, already named in the PRD; it removes receipt validation, gives promotional entitlements for Preview/beta/Ride-along and is free until US$2,500 monthly tracked revenue (then 1 %).

### Model

- **One entitlement: `pro`.** Products `pro_monthly`, `pro_annual` in one subscription group "DrivePerf Pro" (iOS) / one subscription with two base plans (Play). Offering `default` (annual pre-selected, F-SUB-01). The founding offer uses Apple offer codes and Play promo codes or developer-determined offers (PRD OQ-10, A20/A17 finalise).
- **App user ID:** RevenueCat anonymous ID until sign-in; on sign-in `logIn(accountId)`, where the account ID is the Firebase UID treated as an opaque string that is kept forever, even after an identity-provider change (PRD F-ACC-02 AC3; [ADR-004](ADR-004-backend-v1-and-migration-seam.md) rule 2). Anonymous purchases are aliased to it.
- **Client:** `EntitlementRepository` port with a RevenueCat **purchases-kmp 3.x** adapter in `:shared:data:remote` (3.0 removed manual iOS linking). Cached `CustomerInfo` makes the check offline-safe. **Recording, Story, scores and every Free feature never wait on an entitlement call**; only Pro screens consult it. Paywall UI is native (A6 design), not RevenueCat Paywalls.
- **Pro Preview (14 days, no card):** a **local** entitlement computed on the device from the install date (F-SUB-02), so a never-signed-in user needs no network call. For **backup**, a signed-in Preview user's app calls `POST /v1/entitlement/preview:claim` once; the server grants a RevenueCat **promotional entitlement** ending at `min(installAt + 14 d, now + 14 d)`, at most once per account and once per app-generated device ID, so Preview backups have a real server-side window ([data-model.md §4.7](../data-model.md)). Reinstall abuse gains at most 14 days of Preview and is accepted; no hardware identifiers are collected.
- **Beta/internal testers:** promotional entitlements from the RevenueCat dashboard for as long as testing needs (Gate 2).
- **Ride-along (F-SHR-05):** validated and granted server-side through the RevenueCat REST API by `POST /v1/ridealong/...`; codes are opaque.
- **Server mirror:** `POST /v1/webhooks/revenuecat` (shared-secret auth) deduplicates events and updates the account's entitlement and backup window (`ACTIVE` with `writeUntil = expiresAt`; on expiry or refund `LAPSED`, keeping the cloud copy readable for 30 days). Blob prepare and push require an open write window; a daily reconcile job re-checks accounts against the RevenueCat REST API so a lost webhook cannot open or close backup wrongly (data-model.md §4.7, §5).
- **Lapse flow (binding retention):** backup stops at expiry; the app shows reminders at 30, 7 and 1 days left (local notifications scheduled from `GET /v1/entitlement`); the daily purge job deletes cloud trips and blobs when the 30 days end; re-subscribing cancels deletion. Local data is untouched.
- **Account deletion:** the cascade calls RevenueCat's delete-customer API (F-PRV-05) and tells users with an active store subscription to cancel it in the store.

### Consequences

- Good: no receipt server; one entitlement across stores; webhooks make the server authoritative for backup.
- Good: RevenueCat is independent of Firebase, so the VPS migration only re-points the webhook URL (same `api.driveperf.com`).
- Bad: a third party sees purchase data (declared as a service provider in the privacy policy).
- Bad: the Preview backup window needs the API (Blaze); the Preview itself works offline and without an account.

### Confirmation

Sandbox purchase → `pro` active within 5 s (F-SUB-01 AC3); restore on a second device; webhook replay test; lapse test with a shortened retention window in the dev project.

## Pros and cons of the other options

- **Direct StoreKit/Play Billing:** no fee; we would build and secure receipt validation, renewals, grace periods and promo grants ourselves, on two stores.
- **Adapty/Qonversion:** comparable features; no reason to diverge from the PRD and the team's existing plan.

## More information

Sources (accessed 6 Nov 2026): RevenueCat pricing (https://www.revenuecat.com/pricing; free to US$2,500 MTR, then 1 %; third-party sources disagree on whether the 1 % applies to the whole amount); RevenueCat KMP SDK 3.0 (https://www.revenuecat.com/release/integrate-kotlin-multiplatform-without-manual-ios-linking-2026-05-07).
