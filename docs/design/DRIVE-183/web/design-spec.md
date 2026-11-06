# DRIVE-183 · web design spec: F-WEB-07 · Email preferences and unsubscribe

**Agent:** A9 (web prototype) · **Prototype story:** DES-31 [DRIVE-132](https://thekeval.atlassian.net/browse/DRIVE-132) · **Date:** 6 Nov 2026 · **Status:** ready for Gate 3 review
**Inputs:** PRD v1 §3.16 and §4 · A2 §2, §6 · brand.md, components.md, motion.md, accessibility.md · fixtures `prototypes/shared/data.js`
**Artifacts:** [`mockup.html`](mockup.html) (contact sheet: live 1440 and 390 iframes + full-page captures) · [`mockup.webp`](mockup.webp) · [`screens/`](screens/) · prototype [`prototypes/web/`](../../../../prototypes/web/README.md)

## 1. Purpose
Confirm a one-click unsubscribe immediately (CASL, CAN-SPAM), offer an undo, and recover gracefully when the link is broken.

## 2. Screens and states
| ID | Page | Prototype | States (`#ID/state`) |
|---|---|---|---|
| `S-WEB-09` | Email preferences / unsubscribe | [`pages/email-preferences.html`](../../../../prototypes/web/pages/email-preferences.html) | `unsubscribed` · `err` |

Screenshots: `screens/<S-ID>--1440.png` (desktop, scale 1) and `screens/<S-ID>--390.png` (mobile, scale 2) for the first state; other states as `screens/<S-ID>-<state>--<width>.png`.

## 3. Sections and components
**Unsubscribed:** Loop glyph, h1 "You're unsubscribed.", the address, "It took effect straight away.", buttons Resubscribe (ghost) and Back to driveperf.com, two notes (no goodbye email; the address is kept only as unsubscribed, full removal via privacy@). **Err:** warning glyph, h1 "That link didn't work.", email field + Unsubscribe, reply-"unsubscribe" fallback and the 10-business-day promise. Footer line: sender identity and mailing address (F-WEB-07.1).

## 4. Copy
No guilt, no "we'll miss you" (A2 §6 rule 7).

## 5. States and behaviour
Submitting the err-state form with a valid email moves to `unsubscribed`; invalid shows an inline error. Resubscribe toggles to "You're back on the list" (express consent by action).

## 6. Motion
Loop glyph draw-on. RM: static.

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
- `noindex`.

## 10. Analytics (cookie-less; suppressed under GPC or DNT)
| Event | Props |
|---|---|
| `email_unsubscribed` | `via: link/form` |
| `email_resubscribed` | — |

## 11. Astro port notes
- Astro page under `src/pages/`; components as listed in [`prototypes/web/README.md`](../../../../prototypes/web/README.md#components-astro-port-map). Header/footer become static components (the prototype injects them with JS only because `file://` can't fetch partials).
- Tokens: generate `src/styles/tokens.css` from `docs/design/system/tokens.json` **without** the Google Fonts `@import` (self-host Google Sans Flex / Roboto Flex as WOFF2 if licensed for the web build, otherwise keep the system stack).
- Drop the prototype panel, the `#S-WEB-ID/state` router and `?shot=1`.
- Lighthouse mobile targets ≥ 95 in all four categories (F-WEB-01.2): no client JS except small islands, no web fonts by default, SVG only.
- The unsubscribe token handling depends on the email provider (PRD §8); the page should work as a GET with a signed token so it's truly one click.

