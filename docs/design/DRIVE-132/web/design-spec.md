# DRIVE-132 · DES-31 · Web prototype: driveperf.com pages (11)

**Agent:** A9 · **Date:** 6 Nov 2026 · **Status:** ready for Gate 3 review
**Prototype:** [`prototypes/web/`](../../../../prototypes/web/README.md) (open `index.html`) · **Contact sheet:** [`mockup.html`](mockup.html) · [`mockup.webp`](mockup.webp) · full-page captures in [`screens/`](screens/)

All eleven S-WEB screens are built as a static, componentised site that will be ported to Astro on Firebase Hosting. Per-story specs:

| Story | Feature | Screens | Spec |
|---|---|---|---|
| DRIVE-169 | F-WEB-01 Landing + A/B | S-WEB-01, S-WEB-11 | [web/design-spec.md](../../DRIVE-169/web/design-spec.md) |
| DRIVE-171 | F-WEB-02 Waitlist | S-WEB-01 form, S-WEB-02 | [web/design-spec.md](../../DRIVE-171/web/design-spec.md) |
| DRIVE-174 | F-WEB-03 Legal pages | S-WEB-03, S-WEB-04 | [web/design-spec.md](../../DRIVE-174/web/design-spec.md) |
| DRIVE-177 | F-WEB-04 Account deletion | S-WEB-07 | [web/design-spec.md](../../DRIVE-177/web/design-spec.md) |
| DRIVE-69 | F-PRV-05 (web resource) | S-WEB-07 | [web/design-spec.md](../../DRIVE-69/web/design-spec.md) |
| DRIVE-179 | F-WEB-05 Support + explainer | S-WEB-05, S-WEB-06 | [web/design-spec.md](../../DRIVE-179/web/design-spec.md) |
| DRIVE-43 | F-ONB-03 (web explainer) | S-WEB-06 | [web/design-spec.md](../../DRIVE-43/web/design-spec.md) |
| DRIVE-181 | F-WEB-06 Ride-along landing | S-WEB-08 | [web/design-spec.md](../../DRIVE-181/web/design-spec.md) |
| DRIVE-199 | F-SHR-05 (web landing) | S-WEB-08 | [web/design-spec.md](../../DRIVE-199/web/design-spec.md) |
| DRIVE-183 | F-WEB-07 Email preferences | S-WEB-09 | [web/design-spec.md](../../DRIVE-183/web/design-spec.md) |
| DRIVE-188 | F-WEB-08 Press kit | S-WEB-10 | [web/design-spec.md](../../DRIVE-188/web/design-spec.md) |

## Design direction
Night Drive, dark by default with a full light peer. Large expressive display type (system SF Pro / Google Sans Flex when installed, with expanded tabular numerals for scores), one accent (Glide mint), Sodium gold only on the route record and the founding badge. The hero illustration is a real fixture drive (the QEW commute with its new route record of 91), drawn on in light with its ends trimmed for privacy. App screens are HTML/CSS device mockups for both iPhone and Android, built from the shared fixtures. Motion: route draw-on, ring reveal with the record notch flipping gold, a scroll-driven Story card stack, quiet reveals; every one has a reduced-motion state.

## Global rules applied
No speed or max-G as an achievement; no streaks; no leaderboards; no insurance or energy claims; honest capture promise and "Private by design" on the landing page; no cookies, no trackers, GPC/DNT honoured; WCAG 2.2 AA; neutral store badges; "© OpenStreetMap contributors" on every map visual; no network requests.

## Components
See the Astro port map in [`prototypes/web/README.md`](../../../../prototypes/web/README.md#components-astro-port-map).
