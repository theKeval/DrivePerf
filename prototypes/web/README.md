# driveperf.com prototype (A9 · DES-31 · DRIVE-132)

The informational website and waitlist, screens **S-WEB-01 … S-WEB-11** (PRD §3.16 and §4). Static HTML/CSS/JS with no build step, no network requests, no CDNs and no external fonts. It works when opened from `file://`. It will be ported to **Astro on Firebase Hosting (Spark)**, so every section is a self-contained, named component (see [Components](#components-astro-port-map)).

## Open it

Open `prototypes/web/index.html` in a browser. A **Prototype** pill (bottom-right) lists every state of the current page and links to all pages. It is not part of the production site, and `?shot=1` hides it.

| ID | Page | File | States (`#ID/state`) |
|---|---|---|---|
| S-WEB-01 | Home (landing + waitlist) | `index.html` | `default` (= A) · `variantA` · `variantB` · `formErr` · `submitting` · `duplicate` · `networkErr`. With no hash the A/B variant is random on each visit, as in production |
| S-WEB-02 | Waitlist confirmation | `pages/waitlist-confirmed.html` | `default` (iPhone) · `android` (also `?platform=android`) |
| S-WEB-03 | Privacy policy | `pages/privacy.html` | `default` (draft flagged for counsel) |
| S-WEB-04 | Terms of use | `pages/terms.html` | `default` (draft) |
| S-WEB-05 | Support and FAQ | `pages/support.html` | `default` (with a live FAQ filter) |
| S-WEB-06 | How DrivePerf records | `pages/how-it-records.html` | `default` (detects the platform) · `ios` · `android` |
| S-WEB-07 | Account deletion request | `pages/delete-account.html` | `form` · `formErr` · `emailSent` · `verified` |
| S-WEB-08 | Ride-along landing | `pages/ride-along.html` | `default` (mobile under 900 px wide, QR on desktop) · `mobile` · `desktop` · `invalid` |
| S-WEB-09 | Email preferences / unsubscribe | `pages/email-preferences.html` | `unsubscribed` · `err` |
| S-WEB-10 | Press kit | `pages/press.html` | `default` |
| S-WEB-11 | 404 | `404.html` | `default` (Firebase Hosting serves `/404.html`) |

Any S-WEB ID on `index.html` forwards to its page, so `index.html#S-WEB-07/verified` works. Other flags: `?theme=light` (Day Drive theme; the footer also has a Night/Day toggle), `?rm=1` (forced reduced motion), `?shot=1` (static final-state render for screenshots), `?region=CA` (pricing).

**Waitlist mock triggers:** `marcus@example.com` returns "You're already on the list" (idempotent duplicate), and `error@example.com` returns a network error. Any other valid submission goes to S-WEB-02. Nothing is sent anywhere; analytics events are logged to the console and `window.dpwEvents`, and are suppressed when Global Privacy Control or Do Not Track is on.

## Files

```
index.html  404.html  pages/*.html
css/site.css        tokens (copied from tokens.json; shared/tokens.css @imports Google Fonts, so it isn't loaded) + all component styles
js/site.js          header/footer/badges partials, the Loop mark, state router, prototype panel, reveal-on-scroll, GPC-aware analytics stub, form helpers
js/mockups.js       data-driven visuals from DP_DATA: route lines, ScoreRing, GGCircle, DevicePhone, app screens, Story frames
js/home.js          S-WEB-01: A/B, hero route draw-on, Story card stack, ring reveal, evidence, routes, recap, share card, pricing
js/waitlist.js      WaitlistForm validation and states
js/pages.js         S-WEB-02 … 11 behaviours (tabs, FAQ filter, deletion flow, ride-along, unsubscribe, press, 404)
assets/brand/       copies of the A6 brand SVGs (favicon, Loop, lockups, wordmark)
tools/export-web.mjs  headless Chrome exporter (DevTools protocol, no npm deps)
```

Shared, read-only inputs: `../shared/tokens.js`, `../shared/data.js` (the fixture world: Marcus, the GTI, The Commute's new route record of 91), `../shared/motion.js` (`DP_MOTION`).

## Exporting the design artifacts

```
node prototypes/web/tools/export-web.mjs            # all page shots + every story's mockup.html / mockup.png
node prototypes/web/tools/export-web.mjs shots S-WEB-07
node prototypes/web/tools/export-web.mjs sheets
```
Output goes to `docs/design/<story key>/web/`: `screens/<S-ID>[-<state>]--1440.png` (scale 1) and `--390.png` (scale 2), `mockup.html` (contact sheet with live iframes at 1440 and 390, plus full-page captures) and `mockup.png`. Then run `node prototypes/tools/pack.mjs --force` (PNGs are git-ignored): it moves the shots into the shared WebP gallery `docs/design/screens/web/`, converts `mockup.png` to `mockup.webp` and rewrites the links. `--force` is needed after a re-export so changed web shots are re-encoded rather than reused. The story → screen map is in `STORIES` at the top of the script.

## Components (Astro port map)

Each row is one `.astro` component. `data-component` attributes in the HTML mark where each one starts.

| Component | Props | Used on | Notes for the port |
|---|---|---|---|
| `SiteHeader` | `solid?: boolean` | all | Sticky glass bar, nav, CTA, mobile disclosure menu (Esc closes). Becomes static HTML plus a tiny island for the menu |
| `SiteFooter` | — | all | Columns, privacy line, store badges, Night/Day toggle (island) |
| `LoopMark` | `size, glow?` | header, footer, status pages, share card | Port `loop()` from site.js, or inline `assets/brand/*.svg` |
| `StoreBadges` | — | footer, CTA, ride-along | Neutral "Coming to the App Store / Google Play" placeholders. Swap for the official badges only when listings exist |
| `Hero` | `variant: 'A' \| 'B'` | S-WEB-01 | Pick the variant per request with an edge-free approach: Astro builds both and a 200-byte inline script picks one at random before paint (no cookies, no storage); record it in the form and analytics |
| `RouteLine` | `tripId, box, rotate, trim` | hero, evidence, 404 | **Precompute the SVG path at build time** from `data.js` (only the `d` string ships). Ends trimmed 650 / 520 m. Draw-on is a client island |
| `DevicePhone` | `platform: 'ios' \| 'android', width` + slot | hero, press | Pure CSS frame; screens are slots |
| `StoryFrame` / `StoryStack` | `frames[]`, `dwell = 3400` | S-WEB-01, press | Auto-cycling stack is an island (see Motion); static row of frames for RM / no-JS |
| `ScoreRing` | `score, baseline, recordPrev, isNewRecord, spring?` | hero, score section, Story, share card | SVG; reveal follows motion.md §3.4; `spring` uses `DP_MOTION.spring` (ζ 0.78, k 16) |
| `GGCircle` | `points, envelope` | Story frame 3 | Precompute the cloud; G-dot replay is an island |
| `ProofStrip`, `ScoreSection`, `ReplaySection`, `RouteRecordSection`, `RecapSection`, `ShareCard` | — | S-WEB-01 | Static, with reveal-on-scroll |
| `CapturePromise` | `platform?` | S-WEB-01, S-WEB-06 | Binding copy (Gate 1 #10, F-ONB-03) |
| `PrivacyPromise` | — | S-WEB-01 | Data map + never list |
| `Pricing`, `FreeProTable` | `region: 'US' \| 'CA'` | S-WEB-01 | Prices from one constants file shared with store metadata; region toggle is an island |
| `WaitlistForm` | `source` | S-WEB-01 | Island. Production: Firestore create-only collection, honeypot, rate limit, CASL consent record `{at, textVersion, sourcePage}`, variant. Without JS the form must post gracefully (F-WEB-01 edge case) |
| `FaqList` | `groups[]` | S-WEB-01, S-WEB-05 | `<details>`; works without JS. Emit `FAQPage` JSON-LD |
| `CtaBand` | — | S-WEB-01, S-WEB-05, S-WEB-10 | |
| `DocHero`, `Toc`, `DraftBanner`, `Prose` | `version, date, draft` | S-WEB-03/04/05/06/07/10 | Legal pages from Markdown content collections with `version` and `date` front matter |
| `StatusPanel` | `kind` | S-WEB-02, 07 states, 09 | |
| `DeletionRequestForm` | — | S-WEB-07 | Island. Neutral confirmation (no account enumeration), email-link verification |
| `RideAlongLanding`, `PassCard`, `QrCode` | `code, from` | S-WEB-08 | Real QR generated at build or edge time (the prototype draws a non-scannable pattern). `/.well-known/apple-app-site-association` and `assetlinks.json` deploy alongside |
| `NotFound` | — | S-WEB-11 | |

## Motion (v2) for the Astro port

Six moments, one hero motion per section. All of them animate only `transform`, `opacity` or SVG stroke, start from an IntersectionObserver, pause when off screen or when `document.hidden`, and fall back to the final static state under `prefers-reduced-motion`, `?rm=1` and `?shot=1`. Each is a small `client:visible` island over static SVG/HTML; port the code named here.

| Motion component | Code today | What it does | Trigger · loop |
|---|---|---|---|
| `LoopTrace` | `traceLoop()` in js/site.js | The header Loop draws its six trail sub-arcs in sequence, then the G-dot pops (pin-pop spring) | Load, once |
| `HeroReplay` | `drawHero()` + `replay` in js/home.js | Route draw-on → replay marker with a bright trail glides the visible window of the route (the window is computed so it never runs behind the copy mask or the phones); entering the cleanest stretch lifts it and shows a chip | Load; loops every ≈ 15 s; paused off screen |
| `StoryStack` (auto) | Story stack block in js/home.js | 3.4 s per frame with a filling progress segment; fly-out/fly-in with the card transition; frame effects (route draw-on, G-dot replay, spring ring); step buttons; Pause/Play + hover pause | ≥ 45 % in view; loops; paused off screen |
| `ScoreRing spring` | `animateRing({spring:true})` in js/mockups.js | Damped spring count-up; arc may overshoot 1–2 %, number clamps to the score; record notch fills as it passes | 35 % in view, once |
| `DotStripFill` | `routes()` in js/home.js | Track fills, runs pop in as the fill reaches them, gold record lands last with a two-beat ring pulse | 40 % in view, once |
| `WeekDots` | `recap()` in js/home.js | Drive dots drop into their days with the pin-pop spring, 150 ms apart | 40 % in view, once |

Spring easings are CSS `linear()` curves copied into `css/site.css` (`--spring-pin-pop`, `--spring-default`, `--spring-bouncy`) from `shared/tokens.css`. Review tools in `tools/export-web.mjs`: `timeline <url> <prefix> <w> <#target|y> <ms…>` (mid-animation captures) and `perf <url> <w>` (rAF frame pacing, CLS and long tasks while scrolling the page; last run: p95 16.7 ms, 0 frames over 33 ms, CLS 0 at 390/768/1440).

Proto-only (drop in the port): the prototype panel, the hash state router, `?shot=1`.

## Rules this prototype follows

Night Drive brand (dark default, Glide mint as the one accent, Sodium gold only on route records and the founding badge); no speed or max-G shown as an achievement; no streaks, leaderboards or "beat your time"; map visuals carry "© OpenStreetMap contributors"; WCAG 2.2 AA contrast, visible focus, keyboard-operable menu, tabs, FAQ and forms; `prefers-reduced-motion` and `?rm=1` replace every animation with a static or 220 ms cross-fade state; tested at 390, 768 and 1440 px.
