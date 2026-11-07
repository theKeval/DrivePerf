# DRIVE-181 · web design spec: F-WEB-06 · Ride-along landing and app links

**Agent:** A9 (web prototype) · **Prototype story:** DES-31 [DRIVE-132](https://thekeval.atlassian.net/browse/DRIVE-132) · **Date:** 6 Nov 2026 · **Status:** ready for Gate 3 review
**Inputs:** PRD v1 §3.16 and §4 · A2 §2, §6 · brand.md, components.md, motion.md, accessibility.md · fixtures `prototypes/shared/data.js`
**Artifacts:** [`mockup.html`](mockup.html) (contact sheet: live 1440 and 390 iframes + full-page captures) · [`mockup.webp`](mockup.webp) · full-page shots in the shared gallery [`docs/design/screens/web/`](../../screens/web/) · prototype [`prototypes/web/`](../../../../prototypes/web/README.md)

## 1. Purpose
When a Ride-along pass link is opened on a device without the app, explain the pass (30 days of Pro instead of 14), point to the right store, and keep the pass for first open. On desktop, hand off to the phone with a QR code. Handle used or expired passes kindly.

## 2. Screens and states
| ID | Page | Prototype | States (`#ID/state`) |
|---|---|---|---|
| `S-WEB-08` | Ride-along landing | [`pages/ride-along.html`](../../../../prototypes/web/pages/ride-along.html) | `default` (mobile below 900 px, desktop QR above) · `mobile` · `desktop` · `invalid` |

Screenshots (shared gallery, WebP): `../../screens/web/<S-ID>--1440.webp` (desktop) and `<S-ID>--390.webp` (mobile, 2×) for the first state; other states as `<S-ID>-<state>--<width>.webp`. Shots use `?shot=1`, so every animation is captured in its final state.

## 3. Sections and components
`RideAlongLanding` hero: eyebrow "Ride-along pass", h1 "{Priya} sent you 30 days of Pro." (sender name from `entitlement.states.rideAlong.from`), lede · **mobile:** neutral store placeholders + "Install DrivePerf from this phone and your pass is applied the first time you open the app." · **desktop:** `QrCode` card "Open it on your phone" + short URL · `PassCard` (30 days of Pro, what's included, from Priya; dashed tear line) · privacy note "Priya only sees “Your friend is set up”. Nothing about your drives, ever." · three value cards. **Invalid:** h1 "This pass has already been used." + 14-day Preview fallback + waitlist CTA.

## 4. Copy
Rewards are never tied to distance, drives, ratings or reviews (F-SHR-05.4); the page doesn't mention the sender's reward at all.

## 5. States and behaviour
| State | Rendering |
|---|---|
| Mobile without app | Store placeholders, deferred-link note |
| Desktop | QR + URL |
| Invalid pass | Kind explanation + Preview fallback |
| With the app installed | Not a web state: the universal link / app link opens S-SHR-04 directly |

## 6. Motion
State views fade in. RM: static.

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
- `noindex` (pass URLs are personal); OG title "A Ride-along pass for you".

## 10. Analytics (cookie-less; suppressed under GPC or DNT)
| Event | Props |
|---|---|
| `ridealong_link_opened` | `has_app: false, mode: mobile/desktop/invalid` |

## 11. Astro port notes
- Astro page under `src/pages/`; components as listed in [`prototypes/web/README.md`](../../../../prototypes/web/README.md#components-astro-port-map). Header/footer become static components (the prototype injects them with JS only because `file://` can't fetch partials).
- Tokens: generate `src/styles/tokens.css` from `docs/design/system/tokens.json` **without** the Google Fonts `@import` (self-host Google Sans Flex / Roboto Flex as WOFF2 if licensed for the web build, otherwise keep the system stack).
- Drop the prototype panel, the `#S-WEB-ID/state` router and `?shot=1`.
- Lighthouse mobile targets ≥ 95 in all four categories (F-WEB-01.2): no client JS except small islands, no web fonts by default, SVG only.
- The prototype QR is a deterministic, non-scannable pattern; generate a real QR at build/edge time. Deploy `/.well-known/apple-app-site-association` and `/.well-known/assetlinks.json` (F-WEB-06.2). The link carries an opaque code only.
## 12. Deviations and open items
- Pass expiry (60 days) is a placeholder; the PRD doesn't define it.
