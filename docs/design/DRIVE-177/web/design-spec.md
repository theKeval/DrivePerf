# DRIVE-177 · web design spec: F-WEB-04 · Account deletion request page

**Agent:** A9 (web prototype) · **Prototype story:** DES-31 [DRIVE-132](https://thekeval.atlassian.net/browse/DRIVE-132) · **Date:** 6 Nov 2026 · **Status:** ready for Gate 3 review
**Inputs:** PRD v1 §3.16 and §4 · A2 §2, §6 · brand.md, components.md, motion.md, accessibility.md · fixtures `prototypes/shared/data.js`
**Artifacts:** [`mockup.html`](mockup.html) (contact sheet: live 1440 and 390 iframes + full-page captures) · [`mockup.webp`](mockup.webp) · full-page shots in the shared gallery [`docs/design/screens/web/`](../../screens/web/) · prototype [`prototypes/web/`](../../../../prototypes/web/README.md)

## 1. Purpose
Let someone without the app request deletion of their DrivePerf account and cloud data (Google Play account-deletion web resource), explain the in-app route, and say what is deleted, what is kept and why.

## 2. Screens and states
| ID | Page | Prototype | States (`#ID/state`) |
|---|---|---|---|
| `S-WEB-07` | Account deletion request | [`pages/delete-account.html`](../../../../prototypes/web/pages/delete-account.html) | `form` · `formErr` · `emailSent` · `verified` |

Screenshots (shared gallery, WebP): `../../screens/web/<S-ID>--1440.webp` (desktop) and `<S-ID>--390.webp` (mobile, 2×) for the first state; other states as `<S-ID>-<state>--<width>.webp`. Shots use `?shot=1`, so every animation is captured in its final state.

## 3. Sections and components
`DocHero` naming DrivePerf and the developer ("DrivePerf is made by Keval Langalia, an individual developer in Ontario, Canada") → two columns: left `DeletionRequestForm` (email + Apple private-relay hint + "Send confirmation link" + subscription note) and "Or delete it in the app" (3 steps: Profile → Privacy Centre → Delete account → choose local data); right card "What is deleted" (account, cloud backup, Ride-along records, RevenueCat customer and crash data where possible) and "What is kept, and why" (store purchase records, a record of the request, anonymous totals). Mobile: stacked.

## 4. Copy
- Emailed: "Check your email. If there's a DrivePerf account for {email}, we've sent a link to confirm the deletion. It expires in 24 hours." (neutral, no account enumeration).
- Verified: "Deletion confirmed. We're deleting your account and cloud data now. It's usually done within 7 days and always within 30. We'll email you when it's complete."

## 5. States and behaviour
| State | Rendering |
|---|---|
| Form | Empty email field |
| Form Err | Inline error "That email doesn't look right. Check for a typo." with icon; focus on the field |
| Email sent | Status panel (role=status) with the address; link back to the form |
| Verified / done | Confirmation + what happens to phone data and subscriptions + back link |

## 6. Motion
State panels fade in 8 px / 320 ms. RM: instant.

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
- Title "Delete your DrivePerf account · DrivePerf"; canonical `/delete-account` (this URL goes into the Play Console Data Safety form).

## 10. Analytics (cookie-less; suppressed under GPC or DNT)
| Event | When |
|---|---|
| `web_deletion_requested` | Valid request submitted (F-WEB-04) |

## 11. Astro port notes
- Astro page under `src/pages/`; components as listed in [`prototypes/web/README.md`](../../../../prototypes/web/README.md#components-astro-port-map). Header/footer become static components (the prototype injects them with JS only because `file://` can't fetch partials).
- Tokens: generate `src/styles/tokens.css` from `docs/design/system/tokens.json` **without** the Google Fonts `@import` (self-host Google Sans Flex / Roboto Flex as WOFF2 if licensed for the web build, otherwise keep the system stack).
- Drop the prototype panel, the `#S-WEB-ID/state` router and `?shot=1`.
- Lighthouse mobile targets ≥ 95 in all four categories (F-WEB-01.2): no client JS except small islands, no web fonts by default, SVG only.
- Verification needs a backend (email link + cascade delete via Cloud Functions, Blaze). Until then the form can create a request document and a person processes it within the 30-day promise.
## 12. Deviations and open items
- "Ride-along pass records" are a proposal; the deletion-log period stays a placeholder.
