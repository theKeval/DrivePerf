# DRIVE-7 — About Screen Design Spec

**Platform:** Android · Jetpack Compose · Material 3 (dark theme, automotive-stats aesthetic)
**Deliverable frame:** phone, portrait, 1080×2400 (20:9)

## 1. Layout

```
┌───────────────────────────────┐
│  ←   About                    │  Top app bar (center-aligned title), 168px tall
│                                │
│                                │
│          ◎  (logo, 200x200)   │  Hero block, centered, top margin 96px
│          DrivePerf           │  Headline
│          v2.4.1 (Build 218)   │  Label chip
│                                │
│  Track every drive. See your  │  Body copy, centered, max width 760px
│  speed, distance, routes and  │
│  efficiency trends over time. │
│                                │
│  ────────────────────────     │  full-bleed divider, 1px, 64px side margin
│                                │
│  ⎔  View source on GitHub   › │  Link row card, 96px tall
│                                │
│  © 2026 DrivePerf             │  Footer caption, centered
│  driveperf.com                │
└───────────────────────────────┘
```

- Screen margins: 48px left/right (≈16dp @3x density).
- Vertical rhythm on 24px (8dp) grid: hero top gap 96px, logo→name 32px,
  name→version 12px, version→description 40px, description→divider 64px,
  divider→link row 32px, link row→footer 56px.
- Single primary "action" on screen = the GitHub link row (list item, not a
  button) — no FAB needed on an informational screen.

## 2. Color tokens (M3 roles, dark scheme)

| Role | Hex |
|---|---|
| surface (background) | `#0E1116` |
| surface-container | `#161B22` |
| surface-container-high | `#1E2530` |
| surface-container-highest | `#252D3A` |
| on-surface | `#E3E6EA` |
| on-surface-variant | `#A6ADB8` |
| outline | `#3A414C` |
| outline-variant | `#232933` |
| primary | `#00E5B3` |
| on-primary | `#00382A` |
| primary-container | `#00513C` |
| on-primary-container | `#6FF6D0` |
| secondary (accent, sparing) | `#FF7A45` |
| error | `#FFB4AB` |

Dark-first per DrivePerf convention; contrast on-surface vs surface = 13.6:1 (AA/AAA pass).

## 3. Typography (M3 type scale, Roboto/system-ui stack)

| Style | Size / Line height | Weight | Usage |
|---|---|---|---|
| Title Large | 28sp/36 | 500 | Top app bar "About" |
| Headline Small | 32sp/40 | 600 | "DrivePerf" app name |
| Label Large | 16sp/22 | 500 | Version chip text |
| Body Large | 18sp/28 | 400 | Description paragraph |
| Body Medium | 15sp/22 | 500 | Link row label |
| Label Small | 13sp/18 | 400 | Footer caption |

Max two weights per screen: Regular (400) + Medium/Semibold (500/600).

## 4. Components

- **Top app bar (center-aligned):** transparent on surface, back icon button
  (24px, `on-surface`), title centered, 4dp elevation via subtle bottom hairline
  (`outline-variant`) only when content scrolls — flat here since content fits.
- **App logo mark:** 200×200 circular tile, `primary-container` fill with
  gradient gauge icon (see `assets/logo-mark.svg`), 24dp elevation via soft glow
  (`primary` at 12% opacity blur), corner = full (circle, per M3 shape scale for
  large icon container).
- **Version chip:** M3 assist chip, `surface-container-high` fill, `outline`
  1px border, 8dp corner radius, `on-surface-variant` text, horizontal padding 16px.
- **Description text:** centered, `on-surface-variant`, max-width 760px, no card.
- **Divider:** 1px `outline-variant`, inset 0 (full width within margins).
- **Link row (list item):** `surface-container` fill, 16dp corner radius,
  leading GitHub icon (28px) in `on-surface`, label "View source on GitHub"
  in `on-surface`, trailing chevron in `on-surface-variant`. States:
  - **Resting:** `surface-container` (#161B22).
  - **Hover/focus (TV/desktop input):** `surface-container-high` (#1E2530) + 1px `outline` ring.
  - **Pressed:** `surface-container-highest` (#252D3A), state-layer 12% `on-surface`.
- **Footer caption:** centered, `on-surface-variant`, Label Small.

## 5. States

- **Default (shown in mockup):** all data loaded, link row resting.
- **Pressed link row:** background steps to `surface-container-highest`; ripple
  omitted in static mockup but color specified above for @dev implementation.
- **No dynamic/error states** — About screen is static content, no loading/error UI needed.

## 6. Assets delivered

- `assets/logo-mark.svg` — app logo (gauge/speedometer mark), 120×120 viewBox.
- `assets/icon-back-arrow.svg` — top app bar back icon, 24×24.
- `assets/icon-github.svg` — GitHub mark for link row, 24×24 viewBox (rendered 28px).
- `assets/icon-chevron-right.svg` — link row trailing chevron, 24×24.
