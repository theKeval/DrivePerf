# DRIVE-171 · web design spec: F-WEB-02 · Waitlist

**Agent:** A9 (web prototype) · **Prototype story:** DES-31 [DRIVE-132](https://thekeval.atlassian.net/browse/DRIVE-132) · **Date:** 6 Nov 2026 · **Status:** ready for Gate 3 review
**Inputs:** PRD v1 §3.16 and §4 · A2 §2, §6 · brand.md, components.md, motion.md, accessibility.md · fixtures `prototypes/shared/data.js`
**Artifacts:** [`mockup.html`](mockup.html) (contact sheet: live 1440 and 390 iframes + full-page captures) · [`mockup.webp`](mockup.webp) · [`screens/`](screens/) · prototype [`prototypes/web/`](../../../../prototypes/web/README.md)

## 1. Purpose
Collect the minimum needed to invite people to the right beta and send launch news: email, platform, country, CASL express consent (unticked) and the A/B variant. Confirm on S-WEB-02.

## 2. Screens and states
| ID | Page | Prototype | States (`#ID/state`) |
|---|---|---|---|
| `S-WEB-01` | Home: WaitlistForm | [`index.html`](../../../../prototypes/web/index.html) | `variantA` · `formErr` · `submitting` · `duplicate` · `networkErr` |
| `S-WEB-02` | Waitlist confirmation | [`pages/waitlist-confirmed.html`](../../../../prototypes/web/pages/waitlist-confirmed.html) | `default` (iPhone) · `android` |

Screenshots: `screens/<S-ID>--1440.png` (desktop, scale 1) and `screens/<S-ID>--390.png` (mobile, scale 2) for the first state; other states as `screens/<S-ID>-<state>--<width>.png`.

## 3. Sections and components
**WaitlistForm** (hero, `#waitlist`): title "Join the waitlist" + gold "Founding price" chip (founding badge is a permitted Sodium use) · error/info banner (live region) · Email · "Your phone" segmented radio (iPhone / Android) · "Country" segmented radio (Canada / US / Other) · consent checkbox (unticked) · hidden honeypot · hidden variant · primary button "Join the waitlist" · privacy line with link to S-WEB-03 §website.

**S-WEB-02 StatusPanel:** animated Loop glyph, eyebrow "Waitlist", h1 "You're on the list.", lede naming the chosen platform, three next steps (beta invite this winter on TestFlight / Google Play closed testing; launch email with the founding price US$29.99 / CA$39.99 and the 14-day Preview; nothing else, one-click unsubscribe), buttons "See how DrivePerf records" and "Copy link to driveperf.com" (plain link, no referral tracking), unsubscribe link to S-WEB-09. Side card "While you wait: Smooth is a skill." with three gentle habits.

## 4. Copy
- Waitlist consent (CASL, unticked): "Email me about the DrivePerf beta and launch, including the founding price. Unsubscribe in one click from any email." Consent text version `wl-consent-v1 (6 Nov 2026)`.
- Confirmation: "You're on the list." "Thanks for joining. We'll email you when the {iPhone|Android} beta opens, and again at launch with your founding price."
- Errors: "Enter your email address." · "That email doesn't look right. Check for a typo, like a missing “.com”." · "Choose iPhone or Android, so we invite you to the right beta." · "Choose your country." · "Tick the box so we can email you. That's the only thing we use your email for."

## 5. States and behaviour
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
Error banner and S-WEB-02 content drop in 8 px with a fade (320 ms `ease.glide`); the Loop glyph on S-WEB-02 draws its trail then pops the G-dot. RM: static.

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
- S-WEB-02 is `noindex`, title "You're on the list · DrivePerf".

## 10. Analytics (cookie-less; suppressed under GPC or DNT)
| Event | Props | When |
|---|---|---|
| `waitlist_validation_error` | — | Invalid submit |
| `waitlist_submitted` | `variant, platform, country` | Successful write |
| `waitlist_link_copied` | — | S-WEB-02 copy link |

## 11. Astro port notes
- Astro page under `src/pages/`; components as listed in [`prototypes/web/README.md`](../../../../prototypes/web/README.md#components-astro-port-map). Header/footer become static components (the prototype injects them with JS only because `file://` can't fetch partials).
- Tokens: generate `src/styles/tokens.css` from `docs/design/system/tokens.json` **without** the Google Fonts `@import` (self-host Google Sans Flex / Roboto Flex as WOFF2 if licensed for the web build, otherwise keep the system stack).
- Drop the prototype panel, the `#S-WEB-ID/state` router and `?shot=1`.
- Lighthouse mobile targets ≥ 95 in all four categories (F-WEB-01.2): no client JS except small islands, no web fonts by default, SVG only.
- Firestore rules: create-only collection `waitlist`, field allow-list and size limits, no client reads (F-WEB-02.2). Duplicate handling: document ID = hash(email) so a repeat write is a no-op that still returns "already on the list".
- Without JS the form must still post (F-WEB-01 edge case): an endpoint is needed (Hosting rewrite → a function needs Blaze); on Spark, render a `<noscript>` mailto fallback.
## 12. Deviations and open items
- Consent is required to submit, because the waitlist exists only to email people (CASL express consent, unticked). Flag for counsel.
- A network-error state was added (not in the PRD list) because a client-side Firestore write can fail.
