# DRIVE-174 · web design spec: F-WEB-03 · Legal pages

**Agent:** A9 (web prototype) · **Prototype story:** DES-31 [DRIVE-132](https://thekeval.atlassian.net/browse/DRIVE-132) · **Date:** 6 Nov 2026 · **Status:** ready for Gate 3 review
**Inputs:** PRD v1 §3.16 and §4 · A2 §2, §6 · brand.md, components.md, motion.md, accessibility.md · fixtures `prototypes/shared/data.js`
**Artifacts:** [`mockup.html`](mockup.html) (contact sheet: live 1440 and 390 iframes + full-page captures) · [`mockup.webp`](mockup.webp) · [`screens/`](screens/) · prototype [`prototypes/web/`](../../../../prototypes/web/README.md)

## 1. Purpose
Publish a clear privacy policy and terms of use, linked from both stores and the app (S-SET-08, F-ONB-02). Both are drafts written from the PRD and flagged for counsel.

## 2. Screens and states
| ID | Page | Prototype | States (`#ID/state`) |
|---|---|---|---|
| `S-WEB-03` | Privacy policy | [`pages/privacy.html`](../../../../prototypes/web/pages/privacy.html) | `default` (draft flagged for counsel) |
| `S-WEB-04` | Terms of use | [`pages/terms.html`](../../../../prototypes/web/pages/terms.html) | `default` (draft) |

Screenshots: `screens/<S-ID>--1440.png` (desktop, scale 1) and `screens/<S-ID>--390.png` (mobile, scale 2) for the first state; other states as `screens/<S-ID>-<state>--<width>.png`.

## 3. Sections and components
Shared layout: `DocHero` (breadcrumb, h1, lede, version chips "Version 0.9 · Draft · 6 Nov 2026", `DraftBanner` in warning colours) → `Toc` (sticky on desktop, a card on mobile, highlights the section in view) → `Prose`. Unconfirmed facts are marked inline with `.placeholder` (developer legal name, privacy officer, mailing address, Android map SDK, email provider, retention periods).

**S-WEB-03 sections:** the short version (4 cards: local-first, backup optional, never sold or shared, yours to take or delete) · who we are · what we collect and when it leaves your phone (table) · location and motion data · how we use it · what we never do · service providers (Firebase, RevenueCat, App Store / Google Play, Apple MapKit / Google Maps SDK or MapLibre, email provider) · where it's stored (Firebase US; transfer outside Quebec; PIA) · retention · your rights (all users; Canada and Quebec; US states; how to request, 30/45 days) · GPC and DNT (CalOPPA) · cookies (none) · website and waitlist (CASL) · 18+ · security · changes and versions · contact and privacy officer (Law 25 person in charge).

**S-WEB-04 sections:** safety callout first ("Never interact with DrivePerf while you're driving.") · agreement and 18+ · safe use · what DrivePerf is and isn't (not insurance, not a safety system, no energy claims) · your account and data · Pro, Preview and payments (prices, founding offer, renewal, cancel in store, refunds via stores, "locked, not lost") · Ride-along passes · acceptable use (no racing; no track mode) · IP and OSM credit · accuracy (honest capture promise) · liability (counsel) · ending · changes and governing law (Ontario; consumer-law carve-outs) · contact.

## 4. Copy
Every PRD F-WEB-03.1 topic is covered: PIPEDA, Law 25 (privacy officer, transfers outside Quebec, rights), US state rights with "we don't sell or share", CalOPPA, location and motion data, Firebase/RevenueCat/map providers, retention, cookies (none), children (under 18 not allowed), contact. Terms (F-WEB-03.2): 18+, no-insurance purpose, safe use, subscription terms.

## 5. States and behaviour
Single default state each. Version and date are shown at the top and bottom (F-WEB-03.3); the app records the accepted version.

## 6. Motion
TOC highlight follows scroll (IntersectionObserver). No other motion.

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
- Titles "Privacy policy · DrivePerf" / "Terms of use · DrivePerf"; canonical `/privacy`, `/terms`; indexable (CalOPPA requires a conspicuous link).

## 10. Analytics (cookie-less; suppressed under GPC or DNT)
| Event | Props |
|---|---|
| `page_view` | `path` |

## 11. Astro port notes
- Astro page under `src/pages/`; components as listed in [`prototypes/web/README.md`](../../../../prototypes/web/README.md#components-astro-port-map). Header/footer become static components (the prototype injects them with JS only because `file://` can't fetch partials).
- Tokens: generate `src/styles/tokens.css` from `docs/design/system/tokens.json` **without** the Google Fonts `@import` (self-host Google Sans Flex / Roboto Flex as WOFF2 if licensed for the web build, otherwise keep the system stack).
- Drop the prototype panel, the `#S-WEB-ID/state` router and `?shot=1`.
- Lighthouse mobile targets ≥ 95 in all four categories (F-WEB-01.2): no client JS except small islands, no web fonts by default, SVG only.
- Store the documents as Markdown content collections with `version` and `date` front matter; keep previous versions at `/privacy/v0-9` etc.
## 12. Deviations and open items
- Retention periods for crash data, waitlist emails and deletion records are not decided in the PRD (OQ-09 proposes 90 days for lapsed backup). They are shown as placeholders.
