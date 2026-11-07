# ADR-009: Web stack — Astro static site on Firebase Hosting, `driveperf.com` via Namecheap DNS

- **Status:** Proposed (for Gate 4)
- **Date:** 2026-11-06
- **Deciders:** Keval Langalia (PO); A10
- **Related:** PRD §3.16 (F-WEB-01…08), decisions log ("Namecheap is only the domain registrar"), [ADR-004](ADR-004-backend-v1-and-migration-seam.md), [ADR-007](ADR-007-analytics-crash-flags-privacy.md), A9 web prototype (`prototypes/web/`)

## Context and problem statement

v1 needs a fast, cookie-less marketing site (landing with A/B positioning test, waitlist, legal pages, account-deletion request page, support/FAQ, Ride-along landing, email preferences, press kit, app-link files) live in Phase 5, ≥ 4 weeks before store copy is locked. No web account dashboard in v1. **Binding:** hosting is Firebase; `driveperf.com` is registered at Namecheap, which is **registrar and DNS only — no Namecheap hosting**.

## Decision drivers

Lighthouse ≥ 95 on mobile · no cookies · Spark-compatible at launch · preview per PR · portable output (plain static files) · smooth, restrained motion (Gate 3).

## Considered options

1. **Astro (static output) on Firebase Hosting.**
2. Next.js on Firebase App Hosting (Blaze, server runtime).
3. Astro on Namecheap shared hosting (rejected by the binding decision).

## Decision outcome

**Chosen option: 1, Astro static site on Firebase Hosting**, deployed from GitHub Actions with a preview channel per PR ([ADR-008](ADR-008-ci-cd-and-environments.md)).

### Details

- **Build:** Astro, `output: 'static'`, zero client JS by default; islands only for the waitlist form and motion. Motion with CSS and the Web Animations API, honouring `prefers-reduced-motion`. Images as AVIF/WebP with explicit sizes; **first-load budget ≤ 500 KB** (Spark Hosting allows 360 MB/day of transfer, ≈ 700 first visits/day; Blaze charges US$0.15/GB beyond).
- **Hosting sites in the prod project:** `driveperf` (website) and `driveperf-api` (rewrites `**` to the Cloud Run API, [ADR-004](ADR-004-backend-v1-and-migration-seam.md)).
- **Domain connection (A18 runbook):** in Namecheap *Advanced DNS*: the Firebase **TXT** ownership record (keep it permanently; Hosting needs it to renew certificates), **A** records for `@` with the IPs the console shows, `www` → redirect to the apex in `firebase.json`, a CNAME/A set for `api`, and CAA records only if the console asks. SSL is provisioned automatically, usually within a few hours (up to 24 h). Remove Namecheap's default parking records first.
- **Email DNS** stays at Namecheap: MX, SPF, DKIM and DMARC for the mailbox provider (recommended: Zoho Mail, decisions log 6 Nov 2026) serving privacy@, support@ and hello@. They coexist with the Firebase Hosting A and TXT records at the apex.
- **App links:** `/.well-known/apple-app-site-association` and `/.well-known/assetlinks.json` served with `Content-Type: application/json` via `firebase.json` headers; Ride-along links at `driveperf.com/r/{code}` (F-WEB-06).
- **Email-link sign-in:** Firebase Dynamic Links shut down on 25 Aug 2025; email links now use a Hosting domain. Use **`driveperf.com`** as `ActionCodeSettings.linkDomain` and add it to Associated Domains / app-link intent filters (A17 to configure).
- **Waitlist (F-WEB-02):** Firestore Web SDK (`firebase/firestore/lite`) writing to a create-only collection under validating rules (Spark). Without JavaScript the form shows a "JavaScript is needed to join — or email us" fallback; once the API exists, the form can post to `POST /v1/waitlist` and work without JS.
- **Headers:** strict CSP (self + Firebase + the analytics beacon), HSTS, `Referrer-Policy: strict-origin-when-cross-origin`, no cookies. Analytics per [ADR-007](ADR-007-analytics-crash-flags-privacy.md).
- **404:** Astro `404.html` (S-WEB-11).
- **Upgrade trigger to Blaze for the site:** before any launch or press push expected to exceed ~700 visits/day; on Spark the quota is a hard cap.
- **Later dashboard (v1.1+):** a static, client-rendered app on the same Hosting calling `api.driveperf.com/v1` with the user's ID token. No SSR, so no App Hosting lock-in and nothing to port when the backend moves.

### Consequences

- Good: $0 on Spark, global CDN, automatic TLS, preview links per PR; `dist/` is plain files that any server (Caddy on a VPS) can serve.
- Bad: no server-side form handling until the API exists; JS-less waitlist sign-up waits for it.

### Confirmation

Lighthouse CI ≥ 95 on all four categories; `driveperf.com` and `www` serve over HTTPS; AASA and assetlinks validate with Apple's and Google's tools; a waitlist write is visible in Firestore and duplicates are rejected.

## More information

Sources (accessed 6 Nov 2026): Firebase Hosting custom domains — TXT verification kept permanently, A records, SSL provisioning (https://firebase.google.com/docs/hosting/custom-domain); Firebase pricing, Hosting 10 GB stored and 360 MB/day free, then US$0.026/GB and US$0.15/GB (https://firebase.google.com/pricing); email-link sign-in migration from Dynamic Links, `linkDomain` (https://firebase.google.com/docs/auth/ios/email-link-migration).
