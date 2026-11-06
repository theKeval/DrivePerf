# DRIVE-169 · web design spec: F-WEB-01 · Landing page with positioning A/B test

**Agent:** A9 (web prototype) · **Prototype story:** DES-31 [DRIVE-132](https://thekeval.atlassian.net/browse/DRIVE-132) · **Date:** 6 Nov 2026 · **Status:** ready for Gate 3 review
**Inputs:** PRD v1 §3.16 and §4 · A2 §2, §6 · brand.md, components.md, motion.md, accessibility.md · fixtures `prototypes/shared/data.js`
**Artifacts:** [`mockup.html`](mockup.html) (contact sheet: live 1440 and 390 iframes + full-page captures) · [`mockup.webp`](mockup.webp) · [`screens/`](screens/) · prototype [`prototypes/web/`](../../../../prototypes/web/README.md)

## 1. Purpose
Explain DrivePerf to a car enthusiast in one screen and get them onto the waitlist, while A/B testing the lead line: **A** "Smooth is a skill." vs **B** "Every drive, decoded." (recap-led). Each visit is assigned at random without cookies or storage, and the variant is stored with any sign-up (F-WEB-01.1). Also covers the 404 page (S-WEB-11).

## 2. Screens and states
| ID | Page | Prototype | States (`#ID/state`) |
|---|---|---|---|
| `S-WEB-01` | Home (landing + waitlist) | [`index.html`](../../../../prototypes/web/index.html) | `variantA` (= `default`) · `variantB` · form states in DRIVE-171 |
| `S-WEB-11` | 404 | [`404.html`](../../../../prototypes/web/404.html) | `default` |

Screenshots: `screens/<S-ID>--1440.png` (desktop, scale 1) and `screens/<S-ID>--390.png` (mobile, scale 2) for the first state; other states as `screens/<S-ID>-<state>--<width>.png`.

## 3. Sections and components
| # | Component | Content | Notes |
|---|---|---|---|
| 1 | `SiteHeader` | Loop mark + wordmark, nav (How it works, Pricing, Privacy, Support), "Join the waitlist" | Transparent over the hero, glass after 8 px of scroll |
| 2 | `Hero` (A/B) | Eyebrow "Coming to iPhone and Android · early 2027", headline, lede, `WaitlistForm`, three trust chips, `DevicePhone` ×2 | iPhone shows Story frame 4 (Smoothness 91, new route record, beating 89 from 14 Oct); Android shows the weekly recap (87, ▲ +2). Both from fixtures. Background `RouteLine` = trip `t20261105-0742` (QEW + Gardiner), ends trimmed 650/520 m, faint other GTA trips as road texture, caption "Route geometry © OpenStreetMap contributors" |
| 3 | `ProofStrip` | Records automatically · Scored against you · Replay with evidence · Recaps worth sharing · Private by design | A2 proof points |
| 4 | `StoryStack` (`#how`) | "Park. Your drive is already decoded." + 6 steps; 6 Story frames from `trip.story.frames` | Scroll-driven sticky stack (420 vh track). RM / screenshot / no-JS: static row of six frames |
| 5 | `ScoreSection` | `ScoreRing` 91 with baseline notch 84 and gold record notch 89; four contributors from `smoothness.contributors` | "A full, gentle stop scores as well as not stopping" (SAF-06) |
| 6 | `ReplaySection` | Map canvas of `t20261020-0740` with shape-coded pins, evidence trace (long-G solid, lat-G dashed, no numbers), "Was it needed?" chips | Speed is not shown at all on the marketing page |
| 7 | `RouteRecordSection` | The Commute glyph, 10-run dot strip (record gold), "Route record 91 · 5 Nov", "Where you gained most: the Gardiner." | Records are Smoothness only; no durations shown |
| 8 | `RecapSection` + `ShareCard` | Week of 26 Oct: 5 drives, 87 ▲ +2, drive of the week; 4:5 route-glyph share card (Thursday morning · GTI · 91 · Route record) | Card follows F-SHR-01.2: no map, no place names, no speed, no time |
| 9 | `CapturePromise` | Binding honest-capture copy + 3 facts | Links to S-WEB-06 |
| 10 | `PrivacyPromise` (`#privacy`) | Data map (phone / cloud / diagnostics / never) + 3 promises | |
| 11 | `Pricing` + `FreeProTable` (`#pricing`) | US/CA toggle, 14-day Preview banner, Free / Pro Annual (featured, founding badge) / Pro Monthly, line-by-line table | Exactly PRD §3.13 binding summary |
| 12 | `FaqList` teaser | 4 questions, link to S-WEB-05 | |
| 13 | `CtaBand` | "Be a Founding Driver." + store placeholders | |
| 14 | `SiteFooter` | Product / Help / Legal, "No cookies. No trackers. We honour Global Privacy Control.", Night/Day toggle | |

**S-WEB-11 404:** solid header, an outlined "404" numeral, "Wrong turn." / "This page took a road we haven't mapped. Let's get you back on route.", buttons "Back to driveperf.com" and "Support and FAQ", three deep links. Background: the twisty Belfountain drive (`t20261010-1015`) drawn on and stopping at a G-dot, with the rest of the road dotted ("unmapped").

## 4. Copy
- **Variant A** headline "Smooth is a skill." · lede "**Every drive, decoded:** automatic recording, a Smoothness score you can improve, and recaps worth sharing. Private by design." (A2 §2 landing line, verbatim).
- **Variant B** (recap-led) headline "Every drive, decoded." · lede "Each drive becomes a 15-second Story, a replay with evidence and a **weekly recap worth sharing.** Recorded automatically. Private by design."
- Honest capture promise (Gate 1 #10, F-ONB-03): "Auto-detects most drives. The first few hundred metres may be estimated. Force-quitting pauses auto-detect." Battery: "2–4% battery an hour · Our estimate for driving with the screen off."
- Pricing (binding): US$6.99/month · US$39.99/year (US$3.33/month) · founding first year US$29.99; CA$8.99 · CA$49.99 (CA$4.17) · founding CA$39.99; "Every new driver starts with a 14-day Pro Preview. No card." "Two plans. No weekly plan, no lifetime plan, no ads." Founding eligibility: waitlist, beta and the first 30 days after launch (F-SUB-05).
- Waitlist consent (CASL, unticked): "Email me about the DrivePerf beta and launch, including the founding price. Unsubscribe in one click from any email." Consent text version `wl-consent-v1 (6 Nov 2026)`.
- Voice: sentence case, no exclamation marks, words from A2 §6 (smooth, composed, clean, decoded, record). Never: fastest, top speed, beat your time, send it, insurance claims, energy savings.

## 5. States and behaviour
Variant A/B only changes the headline, the lede, the page title and the `variant` field. Everything else is identical so the test measures the line, not the layout.

| State | Trigger | Rendering |
|---|---|---|
| Def | Page load | Empty fields; consent unticked; platform and country unselected |
| Validation Err (`formErr`) | Submit with problems | Error summary banner "Check 4 things below." (role=status), each field outlined in `--critical` with an icon + message, focus moves to the first invalid field; messages clear as the user fixes them |
| Submitting | Valid submit | Button shows a spinner and "Joining…", `aria-busy` on the form |
| Duplicate | Email already on the list (`marcus@example.com` in the mock) | Info banner "You're already on the list. Nothing else to do." Idempotent, no error styling (F-WEB-02 edge case) |
| Network error (`networkErr`, extra) | Write fails (`error@example.com`) | Error banner "We couldn't reach the list. Your details weren't saved." Fields keep their values |
| Success | Write succeeds | Navigate to S-WEB-02 with `?platform=&country=` |
| Bot | Honeypot `company` filled | Silently dropped; production adds rate limiting (F-WEB-02.3) |

Collected fields, and nothing else (F-WEB-02.1): email, platform (iPhone/Android), country (Canada/US/Other), consent (with timestamp, consent text version, source page), A/B variant.

## 6. Motion
| Element | Full motion | Reduced motion |
|---|---|---|
| Hero route | Draw-on 2.4 s `ease.drawOn` after 350 ms, glowing head rides the tip, then the cleanest stretch (QEW, 14.2 km) brightens and the G-dot pops at the end | Complete route, 220 ms fade |
| Devices | Rise 40 px + fade, 900 ms `ease.glide`, staggered 140 ms | Static |
| Hero ring | Arc sweeps 0 → 91 locked to the count-up (1.4 s `ease.countUp`); the gold record notch flips solid as the arc passes 89, one glow, no confetti (motion.md §3.4) | Full ring, final number |
| Story stack | Scroll-linked: active frame front, previous frames fly up and out, next frames fan behind (+26 px, +3.2°, dimmed). Frame 2 route draws on; frame 3 G-dot replays the friction circle with a spring follower; frame 4 ring reveal | Static row of frames |
| Reveal | Sections fade + rise 12 px, 700 ms | Shown immediately |
| Evidence trace | Long-G line draws on 1.4 s | Static |
| Dot strip / recap dots | Scale in, staggered 60–90 ms | Static |

One hero motion per section (motion.md §1.2). All animation is transform/opacity or SVG stroke; nothing loops forever (the needed-stop pulse runs 3 times).

## 7. Accessibility checklist (accessibility.md §9, web)
- [x] Dark (default) and light ("Day Drive", `?theme=light`) both render; text pairs meet WCAG 2.2 AA (`--text` 17.9:1, `--text-2` 8.6:1, `--text-3` 5.9:1 on `--bg`; Glide `#5BE7C4` 12.8:1 dark, `#06775E` 5.5:1 light).
- [x] Text zoom to 200% reflows (fluid `clamp()` type, no fixed heights on text containers).
- [x] Reduced motion: `prefers-reduced-motion` or `?rm=1` gives static final states or 220 ms fades (motion.md §5).
- [x] Every control has a visible label; focus ring 3 px Glide with 3 px offset; skip link; landmarks (`header`, `nav`, `main`, `footer`); one `h1` per page.
- [x] Targets ≥ 44 × 44 px (buttons 48–56 px tall, footer links 40 px rows with 15 px text plus spacing).
- [x] No meaning by colour alone: errors have an icon and text, deltas have ▲/▼ glyphs, pins differ by shape.
- [x] Speed and max G never appear as achievements; no streak, leaderboard, "fastest" or "beat your time" language (SAF-04/05/07/08).
- [x] Driving state: the website has no in-drive surface. Marketing shows no phone being used in a moving car (SAF-09).

## 8. Responsive behaviour
| Width | Layout |
|---|---|
| 390 (mobile, tested; usable from 360) | Single column, 20 px gutters, header collapses to a menu button with a disclosure panel, tables scroll horizontally only where unavoidable |
| 768 (tablet, tested) | 32 px gutters, two-column grids start where content allows, menu button still shown below 960 |
| 1440 (desktop, tested) | 1240 px max content width, 48 px gutters, full nav, split layouts, sticky table of contents on documents |

## 9. SEO and meta
- `<title>` "DrivePerf: Smooth is a skill." (variant B: "DrivePerf: Every drive, decoded."); meta description; canonical `https://driveperf.com/`; `theme-color` `#05070A`; SVG favicon.
- Open Graph + Twitter `summary_large_image` (`/og/home-1200x630.png` to be rendered from the hero at build time).
- JSON-LD `SoftwareApplication` with iOS/Android and the five offers (Free, US and CA monthly/annual) (F-WEB-01.5).
- `lang="en-CA"`; headings in order; real text (no text in images).

## 10. Analytics (cookie-less; suppressed under GPC or DNT)
| Event | Props | When |
|---|---|---|
| `page_view` | `path` | Load (cookie-less, aggregate) |
| `experiment_assigned` | `variant` | Random A/B assignment on a visit without an explicit state |
| `waitlist_validation_error` | — | Submit with invalid fields |
| `waitlist_submitted` | `variant, platform, country` | Successful write (F-WEB-02) |

No cookies, no local storage for the experiment; the variant travels in the form payload only.

## 11. Astro port notes
- Astro page under `src/pages/`; components as listed in [`prototypes/web/README.md`](../../../../prototypes/web/README.md#components-astro-port-map). Header/footer become static components (the prototype injects them with JS only because `file://` can't fetch partials).
- Tokens: generate `src/styles/tokens.css` from `docs/design/system/tokens.json` **without** the Google Fonts `@import` (self-host Google Sans Flex / Roboto Flex as WOFF2 if licensed for the web build, otherwise keep the system stack).
- Drop the prototype panel, the `#S-WEB-ID/state` router and `?shot=1`.
- Lighthouse mobile targets ≥ 95 in all four categories (F-WEB-01.2): no client JS except small islands, no web fonts by default, SVG only.
- A/B without cookies: build both hero variants into the page and pick one with a tiny inline script before first paint (`Math.random`), or split at the edge if Hosting rewrites allow it. Never persist the assignment.
- Precompute route paths at build time; ship ~3 KB of path data instead of the 880 KB fixture file.
## 12. Deviations and open items
- The PRD state list says "360 px mobile"; screenshots are at 390 (the requested review width). The layout was checked down to 360.
- Battery copy uses the binding F-ONB-03 wording ("2–4% … screen off"); the PRD summary's provisional "≤ 4–5 %/h" target is not shown publicly.
- Launch timing is shown as "early 2027" rather than "January 2027" so the site doesn't commit to a date before Spike 1.
