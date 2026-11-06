# DRIVE-179 · web design spec: F-WEB-05 · Support, FAQ and "How DrivePerf records"

**Agent:** A9 (web prototype) · **Prototype story:** DES-31 [DRIVE-132](https://thekeval.atlassian.net/browse/DRIVE-132) · **Date:** 6 Nov 2026 · **Status:** ready for Gate 3 review
**Inputs:** PRD v1 §3.16 and §4 · A2 §2, §6 · brand.md, components.md, motion.md, accessibility.md · fixtures `prototypes/shared/data.js`
**Artifacts:** [`mockup.html`](mockup.html) (contact sheet: live 1440 and 390 iframes + full-page captures) · [`mockup.webp`](mockup.webp) · [`screens/`](screens/) · prototype [`prototypes/web/`](../../../../prototypes/web/README.md)

## 1. Purpose
Plain answers for users and store reviewers about capture, battery, privacy and pricing (S-WEB-05), and the capture and battery explainer linked from onboarding and used in review notes (S-WEB-06).

## 2. Screens and states
| ID | Page | Prototype | States (`#ID/state`) |
|---|---|---|---|
| `S-WEB-05` | Support and FAQ | [`pages/support.html`](../../../../prototypes/web/pages/support.html) | `default` |
| `S-WEB-06` | How DrivePerf records | [`pages/how-it-records.html`](../../../../prototypes/web/pages/how-it-records.html) | `ios` (= `default` on iPhone/desktop) · `android` |

Screenshots: `screens/<S-ID>--1440.png` (desktop, scale 1) and `screens/<S-ID>--390.png` (mobile, scale 2) for the first state; other states as `screens/<S-ID>-<state>--<width>.png`.

## 3. Sections and components
**S-WEB-05:** `DocHero` with a live FAQ search (filters questions, opens matches, announces the count, empty state with the support email) → `Toc` of topics → `FaqList` groups: Recording and capture (how auto works, estimated start, force-quit/force-stop, missed drive + Health Check, manual) · Battery (screen-off estimate, between drives) · Passenger trips · Scores and tips (Smoothness, needed stops, nothing while driving) · Your data and privacy (no account, not insurance, download/delete, share cards) · Pro and the Pro Preview (Free list, Preview, prices, what happens when Pro ends) · Cancelling and refunds (iPhone and Android paths) → contact band (support@ and privacy@).

**S-WEB-06:** `DocHero` with the binding promise (the force-quit verb switches per platform) → "Two ways to record" (Automatic, off until turned on, permission names per platform; Manual, Control Center / widget vs Quick Settings tile / widget, 15-minute auto-end) → "What happens on a drive" (5 steps, park to decoded) → "The estimated start" diagram (parking P, dashed ≈ 0.4 km estimated, first GPS fix, recorded) → "Honest limits, by platform" tabs with a limits table and a reliability checklist each → battery band "About 2–4% an hour of driving, with the screen off." + 3 facts → Health Check pointer.

## 4. Copy
Binding phrases reused verbatim: "Auto-detects most drives. The first few hundred metres may be estimated. Force-quitting pauses auto-detect." (Android: "Force-stopping the app" and "Some phone brands put apps to sleep.") · "About 2–4% battery per hour of driving with the screen off." · "Your trips stay on your phone unless you turn on backup."

## 5. States and behaviour
S-WEB-06 platform tabs are a WAI-ARIA tablist (arrow keys move, `aria-selected`, `hidden` panels); the default selects Android when the user agent is Android. Every platform-specific sentence on the page switches with the tab. S-WEB-05 FAQ is native `<details>` and works without JS.

## 6. Motion
Cards reveal with a fade/rise; FAQ plus icon rotates 45° (320 ms). RM: static.

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
- S-WEB-05 emits `FAQPage` JSON-LD; canonical `/support`. S-WEB-06 canonical `/how-it-records`, linked from S-ONB-05 "How it works" and from App Review notes.

## 10. Analytics (cookie-less; suppressed under GPC or DNT)
| Event | Props |
|---|---|
| `page_view` | `path` |

## 11. Astro port notes
- Astro page under `src/pages/`; components as listed in [`prototypes/web/README.md`](../../../../prototypes/web/README.md#components-astro-port-map). Header/footer become static components (the prototype injects them with JS only because `file://` can't fetch partials).
- Tokens: generate `src/styles/tokens.css` from `docs/design/system/tokens.json` **without** the Google Fonts `@import` (self-host Google Sans Flex / Roboto Flex as WOFF2 if licensed for the web build, otherwise keep the system stack).
- Drop the prototype panel, the `#S-WEB-ID/state` router and `?shot=1`.
- Lighthouse mobile targets ≥ 95 in all four categories (F-WEB-01.2): no client JS except small islands, no web fonts by default, SVG only.

## 12. Deviations and open items
- Platform-limit numbers are from A3 and the PRD (iOS ~300–1,000 m lost; Android target ≤ 250 m). Spike 1 may change them; the page is written so only the table needs editing.
