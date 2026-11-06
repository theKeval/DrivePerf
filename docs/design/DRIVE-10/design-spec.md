# DRIVE-10 — Trip Summary Stats Dashboard — Design Spec

**Platform:** Android (Kotlin + Jetpack Compose) · **Design language:** Material 3
**Deliverable:** Phone, portrait, **1080×2400** (xxhdpi, density 3.0 → 360×800dp)
**Theme:** Dark-first (DrivePerf convention). Light theme tokens included below; both
must meet WCAG AA contrast.

## Files
- `mockup.html` — primary deliverable, **Populated** state, dark theme (screenshotted → `mockup.png`)
- `states/loading.html` — Loading (skeleton) state
- `states/no_trips.html` — No-trip empty state
- `states/partial_data.html` — Partial-data state
- `assets/*.svg` — all icons/illustrations, stroke-based, tintable via `currentColor`

## Navigation
"Stats" is a new **top-level bottom navigation** destination alongside Trips, Vehicles,
Settings (4 items). Selected state: pill-shaped indicator (`secondaryContainer`) behind
the icon, bold label in `onSurface`; unselected icons/labels in `onSurfaceVariant`.
Bottom nav height 80dp, icons 24dp inside a 40dp active pill (56×26 shown at 3x scale).

## Layout structure (top → bottom)
1. **Top app bar** (56dp/168px) — small center-aligned title "Stats" (Headline Small,
   24sp) + subtitle "All-time · Updated just now" (Body Large caption), info icon button
   top-right (tap → glossary of stat definitions).
2. **Overview** section label (Title Large, 22sp) + **2×2 grid of summary cards**:
   Total distance, Total trips, Total driving time, Average speed. 24dp gaps, 16dp screen
   margins, 28dp card corner radius, card surface `surfaceContainerLow`.
   - Each card: 56dp icon chip (`primaryContainer` bg, icon tinted `onPrimaryContainer`),
     value (Display-ish 44px/≈24sp scaled, weight 600), label (Body Large, `onSurfaceVariant`).
3. **Last 7 days** chart card — one card containing a 7-bar bar chart, one bar per local
   calendar day, oldest→newest left→right, **today is always the rightmost bar** and is
   visually distinguished (`primary` fill + bold "Today" label vs `secondaryContainer` for
   the other six). Zero-distance days render a fixed-minimum-height bar (never invisible)
   with "0.0" labeled above it. Value labels sit above each bar; day labels below.
4. **Personal records** section label + **2-up row**: Longest trip, Fastest avg speed.
   Each card: 52dp icon chip (`tertiaryContainer`/gold accent — trophy / bolt), title,
   large value, meta line (date · duration/distance secondary context).

## States
- **Populated** (`mockup.html`): all data present, as above.
- **Loading** (`states/loading.html`): every card/chart/records region replaced by
  shimmering skeleton blocks at identical bounds — no zero values shown, top bar title
  stays static so nav context never flashes empty.
- **No trips** (`states/no_trips.html`): centered illustration (`assets/il_no_trips.svg`),
  headline "No trips recorded yet", supporting copy, "Start a Trip" filled button. Cards/
  chart/records are omitted entirely (not shown as zeros) to avoid misleading the user.
- **Partial data** (`states/partial_data.html`): a dismissible inline banner
  (`errorContainer`/`onErrorContainer`, warning icon) — "Some recent trips are still
  syncing — totals may update shortly." Chart shows dashed/empty placeholder bars for
  days without synced data (no fabricated zero bars) while later days render normally.
  A record with insufficient data shows a neutral "Not enough synced data yet" message
  in place of a value instead of a misleading zero/blank.

## Color tokens

### Dark theme (primary mockup)
| Role | Hex |
|---|---|
| surface | `#121318` |
| surfaceContainerLow (cards) | `#1A1B21` |
| surfaceContainer (bottom nav bg) | `#1D1E25` |
| surfaceContainerHigh | `#262832` |
| onSurface | `#E3E2E9` |
| onSurfaceVariant | `#C5C6D0` |
| outline | `#8F909A` |
| outlineVariant (dividers) | `#45464F` |
| primary (today bar, active accents) | `#AEC6FF` |
| onPrimary | `#0A305F` |
| primaryContainer (stat icon chips) | `#284777` |
| onPrimaryContainer | `#D6E3FF` |
| secondary | `#C0C6DC` |
| secondaryContainer (chart bars, nav pill) | `#404659` |
| tertiary (gold record accent) | `#F0C674` |
| onTertiary | `#402D00` |
| tertiaryContainer (record icon chips) | `#5C4300` |
| onTertiaryContainer | `#FFDEA6` |
| errorContainer (partial-data banner) | `#5B2323` |
| onErrorContainer | `#FFDAD6` |
| error | `#FFB4AB` |

### Light theme
| Role | Hex |
|---|---|
| surface | `#FEF7FF` |
| surfaceContainerLow | `#F7F2FA` |
| onSurface | `#1B1B1F` |
| onSurfaceVariant | `#45464F` |
| outline | `#767680` |
| primary | `#415F91` |
| onPrimary | `#FFFFFF` |
| primaryContainer | `#D6E3FF` |
| onPrimaryContainer | `#001B3D` |
| secondaryContainer | `#DBE2F9` |
| tertiaryContainer | `#FFDEA6` |
| onTertiaryContainer | `#291800` |
| errorContainer | `#FFDAD6` |
| onErrorContainer | `#410002` |
| error | `#BA1A1A` |

Seed color: `#4C6EF5` (indigo-blue), tuned into the roles above; accent gold (`tertiary`)
is reserved exclusively for the Personal Records surfaces.

## Typography (M3 scale, Roboto)
| Style | Size / Line | Weight | Usage |
|---|---|---|---|
| Headline Small | 24/32 | 500 | "Stats" top bar title |
| Title Large | 22/28 | 500 | Section labels ("Overview", "Personal records"), chart title |
| Title Medium | 16sp≈22px/24 | 500 | Card labels, record titles |
| Display-ish stat value | 44px/1.1 | 600 | Summary card values |
| Record value | 46px/1.1 | 600 | Personal record values |
| Body Large | 16sp≈22px/1.5 | 400 | Subtitle, record meta, empty-state copy |
| Body Medium | 14sp≈20px/1.4 | 400–500 | Chart day/value labels |
| Label Large | 14sp≈20px | 500–700 | Nav labels (bold when active) |

Max two weights per screen (Regular 400 / Medium-Bold 500–700). Support system font
scaling (design at 1x; verify no clipping up to 130% scale — cards use flexible height,
not fixed pixel clamps, in implementation).

## Spacing & shape
- Grid: 8dp base (rendered ×3 px at this density). Screen margins: 16dp (48px).
- Gaps: 8dp (24px) between grid cards; 12–16dp (32–48px) between sections.
- Corner radius: cards/chart/records 16dp large-component radius (28px at 3x); icon chips
  16dp (large) / 14dp (medium); nav active pill fully rounded (26px).
- Bottom nav height: 80dp (240px). Top app bar: 56dp (168px).
- Elevation: tonal (`surfaceContainerLow` vs `surface`), no drop shadows.

## Calculation & formatting rules (from acceptance criteria — for @dev)
- **Average speed** = *total recorded distance ÷ total recorded driving time* across all
  valid trips (not a mean of per-trip averages).
- Distance/speed follow the user's configured unit system (mi/mph shown; support km/kmh).
- Duration formatted compactly for scanning: `312h 40m` (drop seconds at this scale).
- 7-day chart always has exactly 7 labeled bars for the 7 local calendar days ending
  today; **today is the final (rightmost) bar**; zero-distance days still render a bar.
- Personal records exclude zero-duration/invalid trips; ties resolved deterministically
  (e.g., stable sort — earliest trip wins) per an implementation-defined, consistent rule.
- Empty/loading/partial states must never display fabricated `0` values that could be
  mistaken for real data — use the dedicated state treatments above instead.

## Accessibility
- All icon-only elements carry content descriptions (e.g., "Stats, selected", "Longest
  trip trophy icon").
- Text/background contrast ≥ 4.5:1 (verified against tokens above) in both themes.
- Chart bars are supplemented with numeric value + day labels — chart is not the sole
  carrier of information (screen-reader summary: "Wednesday, 0.0 miles").
- Layout uses flexible (Compose) spacing, not fixed pixel heights, so it tolerates system
  font scaling and common phone sizes (e.g., 360dp–430dp width) without clipping.
