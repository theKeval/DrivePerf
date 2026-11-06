# DrivePerf brand: "Night Drive"

**Jira:** DRIVE-99 · **Author:** A6 (design system) · **Date:** 6 Nov 2026 · **Status:** proposal for Gate 3
**Binding inputs:** [PRD v1](../../product/PRD-v1.md) (Gate 2), [A2 strategy](../../product/A2-strategy-positioning.md) §2, §4 and §6, [Gate 1 decisions](../../product/gate-1-decisions.md).
**Companion files:** [tokens.json](tokens.json) · [motion.md](motion.md) · [components.md](components.md) · [accessibility.md](accessibility.md) · [screen-map.md](screen-map.md) · [preview.html](preview.html) · `assets/`

---

## 1. The idea in one paragraph

DrivePerf is what a good drive *feels* like at 9 pm on an empty Lakeshore Road: dark, calm, one clean line of light. The interface is the night (deep ink surfaces), the instruments are the dashboard glow (one luminous mint, **Glide**), and quiet pride is the streetlight (**Sodium** gold, used only for route records). The hero visual is never a speedometer. It is **your own drive**: a route glyph drawn in light and a G-dot gliding around the friction circle. Excitement comes from craft and composure, never from speed.

| Principle | What it means on screen | What it rules out |
|---|---|---|
| **Composure over adrenaline** | Soft springs, long draw-ons, one accent colour, generous dark space | Redlines, gauges, flames, confetti, "🔥" |
| **Your data is the art** | Route glyphs, G-G clouds and trace sparklines are the illustrations | Stock photos of cars at speed, motion blur |
| **Honest instruments** | Every number has provenance. Estimated values are dashed and marked "≈" | False precision, decimal places beyond the stated accuracy (F-DQ-05) |
| **Quiet pride** | Gold appears once, on a record, with one shimmer and a double haptic | Casino effects, badges for speed or max G (SAF-04) |
| **Calm while moving** | The in-drive surface is black, static and huge-target | Anything that animates or invites a glance while driving (SAF-01/02) |

## 2. Name treatment

- **DrivePerf**: one word, capital D and capital P. Never "Drive Perf", "Driveperf", "DRIVEPERF" or "DP" in user-facing copy. ("DP" is allowed only as a code prefix.)
- Store name (A2): `DrivePerf: Drive Tracker`. Taglines: **Smooth is a skill.** (lead) · **Every drive, decoded.** (support). Both lines end with a full stop. They are A/B tested (F-WEB-01), so the lockups ship in both variants.
- **Wordmark:** wide, semibold sans with tight, even rhythm: Google Sans Flex, `wdth` 112, `wght` 650, tracking +1%. The concept SVGs set it as live text. **Production:** outline it from Google Sans Flex or Roboto Flex (both SIL OFL 1.1, which allows use in a logo) and hand-tune the `f` terminal and the `P` bowl. **Do not outline SF Pro:** Apple's font licence does not allow it in logos.
- Trademark: "DrivePerf" and "Smooth is a skill." get a preliminary USPTO/CIPO search after development (Gate 2, OQ-19). Treat the identity as provisional until then; the marks below contain no letters, so they survive a rename.

## 3. Logo concepts

| | Concept | File | Idea | Strengths | Risks |
|---|---|---|---|---|---|
| **A ★** | **The Loop** (recommended) | [`logo-a-loop.svg`](assets/logo-a-loop.svg), [`-mono`](assets/logo-a-loop-mono.svg), [`-on-light`](assets/logo-a-loop-on-light.svg) | A 270° trail that fades in clockwise, ending in a round head, followed by a **G-dot** floating in the gap ahead of it. | It is three product truths at once: the **friction circle** with the G-dot replay (the signature visual, Story frame 3), the **Smoothness ring** (frame 4) and a **route loop**. It animates naturally: the trail draws on and the dot glides (motion.md §2.4). It reads at 16 px and needs no letters. A2 already calls the share-card mark "the loop". | It is a near-circle, so it is close to a "loading spinner" at very small sizes. Mitigation: the dot always sits *off* the stroke (34° ahead, the same radius), and the trail always fades. A spinner is uniform. |
| B | **The Line** | [`logo-b-line.svg`](assets/logo-b-line.svg) | A single clothoid racing line through one corner over a road "shadow", with the apex dot on the inside. | Enthusiast-literate ("the line", "apex"), elegant, and strongly directional. | It reads as "racing" and can drift toward track culture, which v1 explicitly avoids (no track mode, SAF-08). It is weak as a ring or score element. |
| C | **The Trace** | [`logo-c-trace.svg`](assets/logo-c-trace.svg) | A letter D whose stem is a raw, jagged signal that resolves into a smooth bowl: "every drive, decoded". | It literally illustrates the support line and works well as a monogram. | It is letter-bound (it breaks if the name changes after the trademark search), it is noisy at 16 px, and the jagged half reads as "bad driving" next to scores. |

**Recommendation: A, The Loop.** It is the only concept that is also a product component. The same geometry is the app icon, the share-card watermark, the loading indicator on iOS, the Live Activity leading glyph and the Wrapped opening frame. B is kept as an illustration motif (the "line" stroke under the Story's cleanest-stretch frame). C is retired.

### 3.1 Construction of The Loop

| Property | Value |
|---|---|
| Trail | Arc of radius *r*, from −348° to −78° (screen angles, 0° = 3 o'clock, clockwise), so 270° of travel ending at about 12:30 |
| Stroke | 0.33 *r*, butt-joined, round head cap |
| Fade | Opacity 0.03 → 1.0 along the trail, easing *f*¹·⁵ (six chord-aligned gradient sub-arcs; see `tools/gen-brand-assets.py`) |
| G-dot | Diameter 1.22 × stroke, centred on the same radius, **34° ahead** of the trail head |
| Colours | Trail Glide `#5BE7C4` (on dark) or `#079777` (on light). Dot white (on dark) or ink `#0B0E13` (on light). Mono: one colour, no fade |
| Clear space | 2 × the dot diameter on every side |
| Minimum size | 24 px colour · 16 px mono (favicon, Android status bar, notification small icon) |
| Lockups | [`lockup-horizontal.svg`](assets/lockup-horizontal.svg) (mark + wordmark + "Smooth is a skill.") · [`lockup-stacked.svg`](assets/lockup-stacked.svg) (+ "Every drive, decoded.") · [`wordmark.svg`](assets/wordmark.svg) / [`wordmark-on-light.svg`](assets/wordmark-on-light.svg) |

**Don't:** rotate the mark (the dot always leads clockwise at about 1:30), close the gap, put the dot on the stroke, recolour the trail with data colours, add a speed needle, or animate it on a loop on any in-drive surface.

## 4. App icons

There is one master geometry: The Loop centred on a 1024 canvas, *r* = 300, stroke 104.

### 4.1 iOS 26/27 (Icon Composer, `.icon` bundle)

| Appearance | File | Notes |
|---|---|---|
| Layer 0: background | [`icon-ios-layer-0-background.svg`](assets/icon-ios-layer-0-background.svg) | "Night sky" radial gradient `#17344C → #0A1420 → #05070A`, with a soft Glide halo at 16%. Glass: off. |
| Layer 1: trail | [`icon-ios-layer-1-trail.svg`](assets/icon-ios-layer-1-trail.svg) | Glass on, specular on, neutral shadow, translucency 0.1 |
| Layer 2: G-dot | [`icon-ios-layer-2-dot.svg`](assets/icon-ios-layer-2-dot.svg) | The highest layer, so it gets the most parallax and specular lift. Glass on, translucency 0. |
| **Default** | [`icon-ios-default.svg`](assets/icon-ios-default.svg) | Composite preview (with glow). |
| **Dark** | [`icon-ios-dark.svg`](assets/icon-ios-dark.svg) | Background `#000`, trail brightened to `#6FF2D0`, with a stronger glow so the mark holds on OLED black. |
| **Tinted** | [`icon-ios-tinted.svg`](assets/icon-ios-tinted.svg) | A greyscale source (white mark on black). The system applies the user's tint. The fade is kept because it reads as luminance. |
| **Clear** (light and dark) | [`icon-ios-clear.svg`](assets/icon-ios-clear.svg) | White mark at 92% over the wallpaper (shown over a sample), with a specular arc at 10–11 o'clock. The background layer is hidden in Clear. |
| All four together | [`icon-ios-appearances-preview.svg`](assets/icon-ios-appearances-preview.svg) | Review sheet. |

Keep the mark inside the 824 px icon grid: the dot's outer edge sits at 363 px from the centre (the grid radius is 412 px). Don't put text in the icon and don't add a separate glass effect: Icon Composer adds glass to layers 1 and 2.

### 4.2 Android (adaptive + themed)

| Layer | File | Spec |
|---|---|---|
| Background | [`icon-android-background.svg`](assets/icon-android-background.svg) | 108 × 108 dp, the same night-sky gradient |
| Foreground | [`icon-android-foreground.svg`](assets/icon-android-foreground.svg) | 108 × 108 dp canvas. The mark's outer edge, dot included, stays inside the **66 dp safe zone** (*r* 22.5 dp, stroke 8 dp) |
| Monochrome (themed icon) | [`icon-android-monochrome.svg`](assets/icon-android-monochrome.svg) | Solid single colour, no fade (the launcher tints it from the wallpaper) |
| Notification small icon | [`icon-android-notification.svg`](assets/icon-android-notification.svg) | 24 dp, alpha only. Also used as the Live Update / ongoing-notification icon |
| Preview | [`icon-android-adaptive-preview.svg`](assets/icon-android-adaptive-preview.svg) | Circle, squircle and rounded-square masks, plus themed light and dark |

Web: [`favicon.svg`](assets/favicon.svg).

## 5. Colour story: Night Drive

Dark is the **default** theme (`meta.defaultTheme = "dark"`). Light ("Day Drive") is a full peer, not an afterthought: every screen is prototyped in both (PRD §4). All values are in [tokens.json](tokens.json) and [`prototypes/shared/tokens.css`](../../../prototypes/shared/tokens.css).

### 5.1 Brand palette

| Role | Name | Dark | Light | Use |
|---|---|---|---|---|
| Night (background) | Ink 50 / 0 | `#080B10` (grouped) · `#000` (iOS base, in-drive) | `#F2F3F6` / `#FFFFFF` | App canvas. The in-drive screen is always pure black (OLED, low distraction). |
| Surfaces | Ink 100–300 | `#131822` · `#1B222D` · `#262E3B` | `#FFFFFF` · `#F2F3F6` · `#E4E7EC` | Cards, nested fills, pressed states. On Android these map to the M3 `surfaceContainer*` roles (`#151A21 → #2A3038`). |
| **Glide** (accent) | Mint | `#5BE7C4` (12.8:1 on night) | `#079777` fill · `#06775E` text (5.5:1 on white) | The one accent: primary actions, the Smoothness ring, the route line, "live" recording, Pro |
| **Sodium** (record) | Gold | `#FFC24B` | `#9A6300` | **Route records only**, plus the founding-member badge. Never used for speed, never for warnings. |
| Signal | Red / amber | `#FF6B6B` / `#F5B648` | `#C62828` / `#9A5F00` | Health Check red/amber, destructive actions. **Never used for a worse-than-usual score.** |

Change semantics, following A2's "explain, never blame": **better** = Glide + ▲ + "+n"; **worse** = neutral text-2 + ▼ + "−n" (a real minus sign). There is no red for a lower score, ever.

### 5.2 Data colours (fixed and colour-blind safe)

These never follow Android dynamic colour or iOS accent tint (`color.data.*`). They were tuned against Machado (2009) protan, deutan and tritan simulations. Minimum ΔE₇₆ between any two colours in a set, under every simulation, was **≥ 20**, and channel lines also differ in **dash pattern** in charts (see components.md §5).

| Channel / meaning | Dark | Light | Shape / pattern partner | Why this hue |
|---|---|---|---|---|
| **Speed** | `#D6DCE4` | `#404A58` | Solid 1.5 px line, **neutral** | **Speed-neutral rule (PRD §3.5):** no colour scale, gauge or redline. Speed is "just text" |
| **Longitudinal G** | `#5AB8F5` sky | `#0A6AB8` | Solid 2 px | Blue/orange is the most robust CVD pair |
| **Lateral G** | `#F5A142` amber | `#B25A00` | Dashed 2 px (6/3) | Opposite of long-G in every CVD type |
| Elevation | `#B8A48A` terrain | `#8A7660` | **Area fill** at 20% under the scrubber, never a line | Earthy; distinct by form, not just hue |
| **Hard braking** | `#FF7A66` | `#A21F16` | **Rounded square** pin, chevron-down + bar glyph | "Brake-light" family |
| **Hard acceleration** | `#E58BD0` | `#A33A8C` | **Circle** pin, chevron-up + bar glyph | Kept away from long-G blue so pins never blend with the trace |
| **Hard cornering** | `#EAD54E` | `#9A8A00` | **Diamond** pin, curved-arrow glyph | Lightness separates it from braking for deutans |
| Data quality: Good | `#5FDB9C` | `#1E9E62` | Filled disc + check | — |
| Usable | `#8FB8FF` | `#2F5DB8` | Half-filled disc | — |
| Limited | `#F5B648` | `#9A5F00` | Triangle + "!" | — |
| Unscorable | `#8C95A3` | `#7A8392` | Slashed ring | — |
| Estimated (provenance) | Channel colour at 60% | same | **Dashed 4/4**, "≈" prefix | F-DQ-05: "visually distinct everywhere" |
| Excluded span | Hatch 45°, 6 px, `#C9CED6` at 16% | `#3A4351` at 14% | Hatched band + label "Phone moved — not scored" | F-RPL-08 |
| GPS gap | Line break + dotted connector `#7A8392` | same | "No GPS" label | F-RPL-08 |

All data text labels use `text`/`text2`, never the data colour, so 4.5:1 holds. Data colours are ≥ 3:1 as graphics on their surfaces; in light mode the corner yellow `#9A8A00` (3.5:1), Good `#1E9E62` (3.4:1) and Unscorable `#7A8392` (3.8:1) are the tightest, and each always carries its glyph and a text label. Measured minimum ΔE: events 27.8 dark / 24.1 light · channels 25.8 / 26.3 · quality 21.1 / 22.8.

### 5.3 Car colours (Garage)

`carmine #E5484D` (the GTI, "Kings Red") · `ember` · `sodium` · `lime` · `glide` · `glacier` · `lucid #7FA7D9` (the Ioniq 5, "Lucid Blue") · `cobalt` · `violet` · `graphite` · `pearl` · `onyx`. A car colour tints its Story background (12% wash over the night) and its car chip. It never colours data.

### 5.4 Android dynamic colour

Offered as **Settings → Units and display → "Use wallpaper colours"** (S-SET-02), **off by default**. The night palette *is* the brand, and data legibility depends on neutral surfaces. When it is on, only the `--md-sys-color-*` roles are regenerated from the wallpaper seed. Data, quality, event, record and car colours stay fixed. The A8 prototype's seed picker exercises this.

## 6. Typography

| | iOS | Android |
|---|---|---|
| Text | SF Pro Text (`-apple-system`), HIG text styles, Dynamic Type | **Google Sans Flex** (OFL) → Google Sans Text → Roboto Flex → Roboto, M3 Expressive type scale, font scale to 200% |
| Display | SF Pro Display, automatic optical size | Google Sans Flex (opsz automatic), **Emphasized** styles for titles (M3 Expressive) |
| **DP Numerals** (stats, scores) | **SF Pro Expanded Semibold**: `.fontWidth(.expanded)` + `.monospacedDigit()`. CSS: `font-stretch: 125%` | **Google Sans Flex `wdth` 120, `wght` 600, `ROND` 0** + `tnum`. Roboto Flex `wdth` 125 as fallback |

Why an expanded numeral? Wide, square-shouldered figures read as *instrument* (a dashboard odometer) without being a speedometer. They are calm at large sizes and stay legible at a glance. **Tabular figures always**, so numbers don't jitter while a ring counts up or a scrubber moves.

| Numeric style | Size / line | Use |
|---|---|---|
| `num.hero` | 72/72, −1.5 tracking | The Smoothness score in the ring (Story frame 4, Smoothness breakdown) |
| `num.xl` | 48/52 | Trip-detail score, weekly average |
| `num.l` | 34/38 | Stat tiles (km, min) |
| `num.m` | 24/28 | Replay readouts, route record |
| `num.s` | 17/22 | Inline stats in cards |
| `num.xs` | 13/16 | Chart axes, chips |

Rules: units at 50% size, weight 500, text-2 (`24.6` **km**). Precision follows A12's accuracy: score as an integer, distance to 0.1 km, G to 2 decimals, speed as an integer, elevation as an integer in metres, duration in minutes ("31 min", "1 h 12 min"). Signed deltas use a real minus "−". Estimated numbers get "≈" and 60% opacity.

Shared **type roles** (components are specified in roles, and each platform maps them in `tokens.json → type.roles`): `screenTitle`, `sheetTitle`, `sectionTitle`, `cardTitle`, `body`, `bodySecondary`, `meta`, `caption`, `label`, `button`, `storyHeadline`, `storyBody`.

## 7. Imagery and illustration

- **Route glyphs** are the primary illustration: the user's own route shape, trimmed by a randomised 400–800 m at each end, not geo-referenced and drawn in Glide on night (`trip.glyph` in the fixtures).
- **The G-G cloud** (friction circle) is the secondary illustration: a dot cloud with an envelope and the moving G-dot.
- **Car silhouettes:** generic outlines by body style (hatch, sedan, crossover, SUV, coupe, wagon, pickup, EV variants). No manufacturer logos or badges.
- **Archetype art** (Wrapped): abstract line studies built from the user's data, for example The Glider (one long smooth arc) or The Metronome (evenly spaced ticks). No characters and no "speed demon" imagery (A2 §3.4).
- **Never:** speedometers, tachometers, motion blur, tyre smoke, a phone in a hand inside a moving car (SAF-09), maps showing home and work end points on anything shareable.

## 8. Voice in one line

The calm co-pilot: an instructor's eye, a friend's warmth, an engineer's honesty (A2 §6). UI copy is sentence case, with no exclamation marks in system copy, and at most one dry joke per screen. Words to use and avoid are listed in A2 §6. Components reference that list; they don't restate it.

## 9. Asset index

All SVGs in `assets/` are generated by [`tools/gen-brand-assets.py`](tools/gen-brand-assets.py) and can be regenerated.
`logo-a-loop.svg` · `logo-a-loop-mono.svg` · `logo-a-loop-on-light.svg` · `logo-b-line.svg` · `logo-c-trace.svg` · `wordmark.svg` · `wordmark-on-light.svg` · `lockup-horizontal.svg` · `lockup-stacked.svg` · `icon-ios-default.svg` · `icon-ios-dark.svg` · `icon-ios-tinted.svg` · `icon-ios-clear.svg` · `icon-ios-layer-0-background.svg` · `icon-ios-layer-1-trail.svg` · `icon-ios-layer-2-dot.svg` · `icon-ios-appearances-preview.svg` · `icon-android-background.svg` · `icon-android-foreground.svg` · `icon-android-monochrome.svg` · `icon-android-notification.svg` · `icon-android-adaptive-preview.svg` · `favicon.svg`
UI and pin icons for the prototypes are in [`prototypes/shared/icons.js`](../../../prototypes/shared/icons.js) (`DP_ICONS`).
