# DrivePerf components

**Jira:** DRIVE-99 · A6 · 6 Nov 2026. This is the shared inventory that the iOS (A7), Android (A8) and native (A15/A16) builds use. Tokens are referenced by their CSS names from [`prototypes/shared/tokens.css`](../../../prototypes/shared/tokens.css); motion IDs refer to [motion.md](motion.md) and accessibility rules to [accessibility.md](accessibility.md). Each component is shown rendered in [preview.html](preview.html).

**How to read this file.** Each component has the same **anatomy, states and behaviour on both platforms** (shared UX) and a **platform rendering** (cosmetics). If an iOS and an Android cell disagree on *behaviour*, that is a bug in this document; tell A6.

**Global rules** (they apply to every component):
- Touch targets ≥ 44 pt / 48 dp; in-drive ≥ 64, and the S-DRV-01 primary buttons are 96 tall.
- Every number uses DP Numerals (`.dp-num`, tabular) with units at 50% size.
- Colour is never the only signal: every state has a glyph or text too.
- Speed is neutral text, always: never coloured, never a gauge, never animated.
- Every chart has a text summary for VoiceOver/TalkBack (accessibility.md §3).
- Platform shells may swap UI glyphs (SF Symbols / Material Symbols Rounded), but **never** the event-pin, quality or provenance glyphs (`DP_ICONS`).

---

## 0. Platform primitives (what "glass" and "expressive" mean here)

| Primitive | iOS 26/27 Liquid Glass | Android M3 Expressive |
|---|---|---|
| Floating chrome (tab bar, toolbars, playback bar) | `.glassEffect(.regular)` capsule. Web: `.dp-glass`: `--dp-glass-fill` + `backdrop-filter: var(--dp-glass-blur)` (blur 24, saturate 1.8) + 0.5 px `--dp-glass-stroke` + inset top highlight `--dp-elev-glass-current` | Tonal surface `--md-sys-color-surface-container-high` (no blur), `--dp-elev-3` shadow, fully rounded `--md-sys-shape-corner-full` |
| Over media (maps, Story art) | `.glassEffect(.clear)` + a 35% dimming layer under any text | `surface` at 92% + scrim |
| Content cards | Opaque `--dp-surface`, radius `--dp-radius-card` (24, continuous corners), no shadow in dark, `--dp-elev-1` in light | `--md-sys-color-surface-container`, radius 16 (`card`) or 28 (`cardHero`), tonal elevation |
| Primary button | `.buttonStyle(.glassProminent)` tinted Glide, capsule, 50 pt tall (large) / 44 | Filled `Button` with `ButtonDefaults.shapes()` (round → square morph on press), 56 dp (large) / 40 (medium) |
| Secondary | `.buttonStyle(.glass)` | `FilledTonalButton` |
| Tertiary | Plain text button in `--dp-accent-text` | `TextButton` |
| Destructive | `.glass` with a `--dp-critical` label (or `.destructive` role in menus) | `TextButton` / `FilledTonalButton` with `error` colours |
| Segmented choice | Glass segmented `Picker(.segmented)` | **Connected button group** (`ButtonGroup`) with selection morph |
| Sheet | `.sheet` with detents `[.fraction(0.32), .medium, .large]`; glass background at partial detents | `ModalBottomSheet` with drag handle, `cardHero` top radius 28 |
| Menu | `Menu` (glass) | `DropdownMenu` (M3E rounded, 16 dp) |
| Dynamic colour | n/a (app accent is fixed Glide) | Optional "Use wallpaper colours" (brand.md §5.4); `--md-sys-color-*` only |

---

## 1. App shell (S-NAV-01)

**Anatomy:** ① tab destinations ② capture-status control (iOS bottom accessory / Android floating toolbar) ③ profile avatar in the top-trailing corner of every tab root ④ badges.

| Element | iOS | Android |
|---|---|---|
| Tabs: **Drives · Routes · Insights · Garage** (screen-map.md §1) | **Floating glass tab bar**, inset 16 from the edges and 21 from the bottom, height 62, 4 items (icon + label), selected item = Glide icon and label inside a glass "lens" capsule. `.tabBarMinimizeBehavior(.onScrollDown)` → a compact 44 pt capsule showing only the selected icon | **Navigation bar** (M3E short bar, 64 dp), 4 items, active indicator pill 56 × 32 `secondaryContainer`, labels always visible |
| Capture status | **`.tabViewBottomAccessory`** glass capsule above the tab bar (48 pt): leading Loop/live dot, status text ("Auto-detect on · ready"), trailing **Start** button (glass prominent, Glide). When minimised, it folds inline next to the compact tab bar | **Horizontal floating toolbar** (M3E, 64 dp, vibrant `primaryContainer`) docked 16 dp above the nav bar on the **Drives tab only**: status text + **Start** FAB-style button (56 dp, Glide). It hides on scroll down |
| Profile | 36 pt circular avatar (initial on `--dp-surface-3`) as a glass toolbar item, top-trailing. It opens **Profile as a sheet** (large detent) | 40 dp avatar in a `Cookie12Sided` shape in the top app bar trailing slot. It opens Profile full-screen (shared axis X) |
| Badges | A dot on the avatar = "needs attention" (Health Check red); "PRO PREVIEW · 9" capsule beside the avatar | Same semantics: M3 `Badge` on the avatar; Preview chip in the top bar |

**States:** default · **recording banner** (the accessory/toolbar turns live: pulsing dot + "Recording · 14 min · 9.6 km"; tapping it opens S-DRV-01) · **needs attention** (status text "Auto-detect needs attention" in `--dp-warning` + chevron → S-SET-05) · **paused** ("Paused until 8:00" + Resume) · **manual only** ("Manual mode" + Start) · **Preview badge** · **minimised on scroll** (iOS) / toolbar hidden (Android).
**Never** shows speed, score or record pace while recording (SAF-02).

## 2. Top bars and screen headers

| | iOS | Android |
|---|---|---|
| Tab roots | Large title (`.toolbarTitleDisplayMode(.large)`, `screenTitle` 34/41 bold), glass toolbar buttons top-trailing (avatar, filter, +) | `LargeFlexibleTopAppBar`: title `headlineLargeEmphasized` + optional subtitle; collapses to 64 dp on scroll |
| Detail screens (Trip, Route, Car) | Inline title + subtitle (`.navigationSubtitle` on iOS 26), glass back button, trailing `Menu` (…) | `MediumFlexibleTopAppBar` with title + subtitle (part of day), navigation icon, overflow |
| Immersive (Story, Replay, Wrapped, paywall) | No bar. Floating glass close and more buttons over the content | No bar. Floating tonal icon buttons (48 dp) |

## 3. Score ring (Smoothness)

**Used on:** S-STY-01 f4, S-TRP-01, S-SCO-01, S-HOME-01 (new Story card), S-SHR-01 (Ring card), S-RTE-02, widgets (Free/idle only).
**Anatomy:** ① track (`--dp-fill`, 10% of the diameter) ② arc (gradient `--dp-gradient-ring`, round caps, starting at 12 o'clock and running clockwise) ③ score (`--dp-num-hero` at ≥ 160 px diameter; `num-xl` at 96–159; `num-m` below 96) ④ label "Smoothness" (`caption`, `text2`) ⑤ **baseline notch** (2 px tick in `text2` at the baseline value) ⑥ **record notch** (gold `--dp-record`, outline = old record, solid = new record) ⑦ comparison chip below (§4).

| State | Rendering |
|---|---|
| Default | Arc at score/100. Notch at baseline. Chip "+3 vs your usual on The Commute" |
| First drive | No notch. Chip text: "Your baseline. Every drive from here is compared to you, not to anyone else." |
| Calibrating | Chip "Calibrating your baseline · 2 of 3" with a 3-dot progress row (●●○) |
| New record | The arc passes the gold record notch and the notch flips solid. Chip "New route record." with rosette glyph, `--dp-record-container` |
| Degraded (Limited) | Arc drawn; under the ring, "Score based on 31 of 34 min" + Limited quality glyph |
| Unscorable | Ring track only, dashed, with a "—" centre, label "Not scored" and the reason line. No arc |
| Passenger / not a drive | **The component is hidden.** The trip shows a banner instead (§12 states) |
| Loading | Track with a skeleton breathe. The number shows "··" |
| Recomputing (needed brake marked) | The arc tweens to the new value; the digits roll |
| Locked | Never: Smoothness is Free on every trip forever |

| | iOS | Android |
|---|---|---|
| Container | None (ring on the content), or a glass disc at the 96 px size inside cards | Ring sits on an M3E **`Cookie9Sided`** tonal shape (`primaryContainer` at 24%) that morphs to a circle during the count-up (motion.md §3.4) |
| Number | SF Pro Expanded Semibold | Google Sans Flex wdth 120 wght 600 |

**A11y:** "Smoothness 84. 3 above your usual on The Commute." One element; the chip is merged into it.

## 4. Comparison chip (delta chip)

**Anatomy:** ① direction glyph (▲ better / ▼ lower / ● same) ② signed value with a real minus ③ basis text ("vs your usual on {route}" / "vs your usual city drives").
**States:** better (Glide text and glyph on `--dp-accent-container` at 40%) · lower (`text2` on `--dp-fill`, **never red**) · same ("Right on your usual") · calibrating · record (gold). Height 28, radius chip (iOS capsule / Android 8 dp), `caption`/`labelLarge`.

## 5. Stat tile

**Used on:** S-TRP-01, S-STA-01, S-GAR-02, S-RPL-01 readouts, S-RTE-02, recap and Wrapped frames.
**Anatomy:** ① label (`caption`, `text2`, sentence case: "Distance") ② value (`num-l` in tiles, `num-m` in readouts) + unit ③ optional provenance glyph (top-trailing, 12 px, §10) ④ optional sub-line ("≈ 0.3 km estimated").
**Variants:** `tile` (grid of 2 or 3 columns, min 104 × 88, radius `--dp-radius-lg`/`large`) · `inline` (label + value in a row, for lists) · `readout` (Replay: on glass or tonal, fixed width so it doesn't jitter).
**States:** default · estimated (≈ prefix, 60% opacity, dashed underline) · unavailable ("—" + tooltip "No GPS here") · loading (skeleton bar) · locked (blurred own value + lock glyph; only for Pro trend tiles).
**Speed tiles** ("Max speed 103 km/h", "Speed 92 km/h"): exactly the same component in `text` colour. No accent, no icon, no emphasis, never first in the grid. The order is distance, duration, moving time, stops, avg moving speed, max speed, elevation gain/loss.
| | iOS | Android |
|---|---|---|
| Tile surface | `--dp-surface-2` inside a card, continuous corners 14 | `surfaceContainerHigh`, `medium` 12 dp |
| Readout (Replay) | Glass capsule group: three readouts separated by 0.5 px separators | Tonal floating toolbar segment |

## 6. G-G circle (friction circle)

**Used on:** S-STY-01 f3 (replay), S-ANL-01 (compact, 120–160 px), S-ANL-02 (expanded, full width), S-RPL-02 (event window).
**Anatomy:** ① rings every 0.1 g (`--dp-data-gg-ring`), 0.3 g and 0.5 g labelled (`num-xs`) ② axes: lateral (x, "Left ← → Right") and longitudinal (y, "Accel ↑ / Brake ↓"), labelled in text at the extremes ③ cloud: dots of r 1.6 at 30% `--dp-data-gg-cloud` (2 Hz scorable samples, `trip.gg.points`) ④ envelope: closed spline through `trip.gg.envelope.radiiG` (24 sectors) in `--dp-data-gg-envelope` 1.5 px ⑤ the G-dot (8 px `--dp-data-gg-dot`, with a halo) and its trail (1.2 s, tapering) ⑥ optional event markers in the window (pin glyphs at 12 px).
**States:** default · cursor-synced (Analysis) · filtered by event type (S-ANL-02: chips Braking / Accel / Cornering; non-matching dots drop to 8%) · Part (excluded spans removed; footnote "3 min left out: phone moved") · empty (Unscorable: grid only + "No scorable motion") · RM (static, no dot travel).
**Scale:** fixed ±0.6 g (it clamps to the rim with a notch). It never auto-scales, so drives stay comparable.
**A11y summary:** "Friction circle. Most of your inputs stayed within 0.2 g. Peaks: braking 0.42 g at Spadina & Front, cornering 0.18 g." The max-G value appears only in this personal view; it is never on cards or in Wrapped.

## 7. Synced timeline chart (Analysis)

**Used on:** S-ANL-01, S-RPL-02 (event window variant), S-RTE-04 (segment strip variant).
**Anatomy:**
① **channel lanes**, stacked, sharing one x-axis: Speed (40 px), Longitudinal G (64 px, ±0.6, zero line), Lateral G (64 px, ±0.6), optional Elevation (32 px area) and Pro channels (Jerk, Sample quality)
② **event lane** (20 px) with pin glyphs at 14 px, aligned to the map pins' timestamps
③ **cursor**: a 1.5 px vertical line in `--dp-data-cursor` across all lanes, plus a value bubble per lane (`num-xs`)
④ x-axis: time (`0:00 … 33:12`) or distance (`0 … 37.9 km`) toggle
⑤ **quality overlay**: estimated = dashed line at 60%; gap = line break plus a dotted connector and a "No GPS" label; excluded = hatched band (`.dp-hatch`) with a label
⑥ legend row: swatch **and pattern** for each channel (Speed solid neutral · Long-G solid sky · Lat-G dashed amber · Elevation fill)

| Interaction | Behaviour |
|---|---|
| Drag on the chart | Moves the cursor; the map marker and G-dot follow within 100 ms (F-RPL-05.2). Haptic `selection` when it snaps to an event tick |
| Tap the map | Snaps the cursor to the nearest point |
| Pinch | Zooms x (1× to 20×). Event ticks stay aligned. Double tap resets |
| Long-press and drag (Pro, P1) | Range selection → S-ANL-04 range stats sheet |
| Landscape | The timeline expands to full width and the map shrinks to a 30% inset |

**States:** default · cursor dragging · zoomed · time/distance axis · Part · Lock (Free > 30 days: S-SUB-03 over a blurred render of *this* trip's lanes) · Load (lane skeletons) · XL (lanes stack with 1.5× height; the legend wraps; bubbles move into a readout row above the chart).
| | iOS | Android |
|---|---|---|
| Chart engine | Swift Charts (`LineMark`, `AreaMark`, `RuleMark` cursor, `chartXSelection`) | Compose Canvas (or Vico), with the same marks |
| Channel picker (S-ANL-03) | Glass `Menu` with toggles; Pro channels show a lock glyph | `ModalBottomSheet` with `FilterChip`s; Pro chips show a lock |
| Axis/time toggle | Glass segmented "Time / Distance" | Connected button group |

## 8. Replay scrubber and playback bar (S-RPL-01)

**Anatomy:** ① play/pause (48 px, glass prominent / FAB-style) ② speed selector **Auto · 1× · 10× · 30×** ③ track: 4 px, with the played portion in Glide and the rest `--dp-fill` ④ **event ticks** (pin glyphs at 10 px above the track) and stop ticks (small capsules) ⑤ thumb (16 px; 22 while dragging) ⑥ elapsed / total (`num-xs`) ⑦ **elevation mini-profile** under the track (area fill, 24 px, cursor-synced; "↗ 312 m ↘ 298 m") ⑧ readouts above: Speed · Elapsed · Distance (§5 readout).
**States:** paused at start (thumb at 0, "Play your drive") · playing (each speed) · scrubbing · at an event (tick enlarged; the pin pulses on the map) · in a gap ("No GPS" chip; the speed readout shows "—") · estimated start (chip; the track segment is dashed) · excluded span (hatched track segment) · no events ("No abrupt inputs on this drive." in the event row) · Lock · Err ("Couldn't load this drive's detail. Try again.") · RM.

| | iOS | Android |
|---|---|---|
| Container | **Floating glass playback bar** (capsule, inset 16, above the home indicator). Speed selector = glass segmented control. Readouts in a separate glass capsule at the top | **M3E floating toolbar** (vibrant) with play as an attached FAB; speed selector = **connected button group** (selected segment morphs to a pill). Readouts in a tonal toolbar at the top |
| Layers (P2) | Glass toolbar button → S-RPL-04 sheet | Toolbar icon → `ModalBottomSheet` |

## 9. Event pin, event chip and evidence sheet

### 9.1 Event pin (map)
Shapes are **shared semantics** (`DP_ICONS.pin(type)`): **hard braking = rounded square** + chevron-down/bar glyph · **hard acceleration = circle** + chevron-up/bar · **hard cornering = diamond** + curved-arrow. 28 px on the map, 14 px on the timeline. Fill is the event colour with a 1.8 px white ring and a glyph in `--dp-data-pin-glyph`.
States: default · **needed** (hollow: no fill, 2.2 px event-colour stroke, glyph in event colour, plus a "Needed" label on selection) · selected (1.18×, halo ring, others dimmed to 50%) · pulsing (marker passing) · low confidence (default pin + a small "?" badge at the bottom-trailing corner; the sheet explains it) · cluster (≥ 3 within 24 px → a count bubble that expands on tap).
No pins in estimated or excluded spans (F-RPL-03.5). Events without a good position are listed in trip detail, not pinned.
**Stop pin:** a neutral capsule with a pause glyph, showing duration and time of day on tap (S-RPL-03). **Max-speed pin:** an outline triangle in neutral grey, with no animation and no emphasis (S-RPL-03 "neutral style").

### 9.2 Event chip
Used in trip detail lists, contributor rows, Story frame "Was it needed?", and S-SCO-03. Anatomy: pin glyph (16 px) + label ("Hard braking") + place ("Spadina & Front") + meta (`0.42 g · 1.4 s`, `num-xs`). States: default · needed (hollow glyph, "Needed", strikethrough on the g value) · low confidence ("Low confidence" tag) · disabled (excluded by "Not accurate?"). Height 44 minimum (it is tappable), radius `card` small.

### 9.3 Evidence sheet (S-RPL-02)
**Presentation:** iOS sheet at the medium detent (expandable to large), glass at the medium detent; Android `ModalBottomSheet` at half, expandable. Playback pauses and the map centres on the event.
**Anatomy (top to bottom):**
1. Header: pin glyph 24 + "Hard braking" (`sheetTitle`) + place and time of day ("Spadina & Front · 8:14 am")
2. **Trace**, −10 s … +10 s around the peak (`event.evidence`, 5 Hz): speed (neutral, top, 56 px) and longitudinal + lateral G (one 96 px lane, sky solid and amber dashed), with **start / peak / end markers** (vertical guides labelled "Start", "Peak", "End")
3. Facts grid: Peak `0.42 g` · Duration `1.4 s` · Speed before → after `48 → 0 km/h` (neutral) · Confidence `High` · provenance glyph "Derived"
4. **Tip card**: "This drive's tip" label (if `trip.tip.eventId === event.id`), else "Next time"
5. Actions: **"Was it needed?"** (secondary) → S-SCO-03 · **"Not accurate?"** (tertiary) → reasons list (Phone moved · Pothole or speed bump · Not me driving · Other)
**States:** hard braking · hard acceleration · hard cornering · low confidence (an explanatory line: "Your phone's signal was weaker here, so treat this one lightly.") · marked needed (banner "You marked this as needed. It doesn't count." + "Count it again") · "Not accurate?" open · trace removed ("Detailed trace not kept" + summary values) · this drive's tip.
**A11y:** "Hard braking at Spadina and Front, 8:14 am. Peak 0.42 g over 1.4 seconds, from 48 to 0 kilometres per hour. Confidence high."

## 10. Data-quality badge and provenance label

### 10.1 Data-quality badge (F-DQ-04)
Anatomy: glyph (`DP_ICONS.quality`) + label, plus an optional detail ("Good data · phone mounted · 50 Hz"). Sizes: compact (glyph + "Good", 24 tall) on cards; full (glyph + label + detail, 32 tall) on the Story end frame, trip detail and Replay.
| Badge | Glyph | Detail example |
|---|---|---|
| Good | Filled disc + check | "phone mounted · 50 Hz" |
| Usable | Half disc | "brief GPS gap downtown" / "sensors at 25 Hz (battery saver)" |
| Limited | Triangle with "!" | "phone moved for 3 min" |
| Unscorable | Slashed ring | "phone in a bag" |
Tap → S-DQ-01. iOS: a glass capsule on maps, plain on cards. Android: `AssistChip` with a leading glyph.

### 10.2 Provenance label (F-DQ-05)
Every user-visible number in PRD §5 supports **long-press** (and an ⓘ in dense views) to show a popover: glyph + "Derived" + a one-line meaning ("Calculated from measured data, the same way every time.") + the metric's own line from `DP_DATA.provenance`.
Glyphs: **Observed** = filled dot · **Derived** = ring with dot · **Estimated** = dashed ring. Estimated values are also styled inline (`.dp-est`: ≈ prefix, 60% opacity) and drawn dashed on maps and charts.
| | iOS | Android |
|---|---|---|
| Popover | `.popover` with `.presentationCompactAdaptation(.popover)`, glass | `RichTooltip` (M3) |
| Haptic | `longPress` | `LongPress` |

## 11. Car chip

Used on Story f1, trip card, trip detail and Garage. A colour dot (the car colour) + nickname ("GTI"), 28 tall, capsule (iOS) / `Pill` (Android). Tap in Story f1 → the car picker (F-GAR-02). States: default · unknown car ("Unknown car", dashed outline) · passenger (replaced by a "Passenger" chip with the passenger glyph).

## 12. Trip card (Drives list, Home)

**Anatomy:** ① route glyph thumbnail (56 px square, Glide on `--dp-surface-2`) ② title = label ("Home → Work") ③ meta = part of day · date ("Thursday morning · 5 Nov") ④ stats row (`num-s`): "37.9 km · 33 min" ⑤ **score badge** (`num-m` score + delta chip mini "+7") ⑥ quality glyph (compact) ⑦ car chip (only when the user has > 1 car) ⑧ trailing chevron.
**States:** default · **new Story** (a Glide dot plus a "New" label; on Home, the large "Your drive is decoded" variant with a hero ring) · **new record** (gold rosette next to the score) · **passenger / not a drive** (the whole card at 60% opacity, the score replaced by a "Passenger" chip; it doesn't count) · **interrupted** ("Interrupted" tag + note) · **Unscorable** ("Not scored" in place of the score) · **Limited** (quality glyph warning; the score is still shown) · **locked older trip** (Free > 30 days: summary fully visible, with a small lock glyph after the chevron for "Replay & Analysis in Pro". The summary is never blurred) · **processing** ("Decoding…" shimmer row) · skeleton.
| | iOS | Android |
|---|---|---|
| Container | `--dp-surface` card in an inset-grouped list (radius 24, continuous), rows separated by 0.5 px separators when grouped by week | `ListItem` inside a `surfaceContainer` card (16 dp) per week group; **the score badge uses the `Cookie9Sided` shape** |
| Swipe | Leading: Share. Trailing: Delete (→ S-TRP-03 confirm) | Long-press → contextual actions (M3E: Share, Edit, Delete) |

**A11y:** "Home to Work, Thursday morning, 5 November. 37.9 kilometres, 33 minutes. Smoothness 91, 7 above your usual. New route record. Good data."

## 13. Route card (Routes list) and route visuals

**Anatomy:** ① route glyph hero (`route.glyph`, 120 px tall, Glide on night) ② name ("The Commute") + direction pair ("Home → Work", with a ⇄ icon if linked) ③ "10 drives" ④ **route record** "91 · 5 Nov" with a gold rosette ⑤ dot strip (one dot per run, the record in gold, the latest outlined; up to the last 20).
**States:** default · **candidate to name** ("Route 4 · driven twice" + "Name it" button → S-RTE-03) · **Free limit** (third route: the glyph and dot strip are blurred, with a lock, "Track every route you drive with Pro" → S-SUB-03) · few runs (2: "One more drive to set a record") · inactive on Free (S-RTE-05 chooser) · empty list ("Drive the same trip twice and it becomes a route." + an illustration of two lines merging).
**Dot strip** (S-RTE-02): 10 px dots on a 4 px track, x = date, y = none; colour = neutral, record = gold, selected = Glide ring. **Durations appear only in the runs table, in neutral text, with no deltas and no sorting by duration** (F-RTE-02.2).
**Ghost comparison** (S-RTE-04): today's run in Glide 4 px, the record run as a ghost in `--dp-data-ghost` 3 px dashed 2/4; distance-aligned segments are shaded on today's line: gained = Glide glow, lost = neutral thick, same = default, unaligned = grey dotted. Callouts: "Where you gained most" / "Where you lost most" with evidence links. Durations are listed side by side neutrally ("33 min · 34 min"), never as a difference.

## 14. Share card (S-SHR-01, recap W5, Wrapped f9)

**Formats:** 9:16 (1080 × 1920) and 4:5 (1080 × 1350). Background: night gradient + car-colour wash at 12%; the light theme is not used for cards.
| Style | Content | Never |
|---|---|---|
| **Route glyph** (default) | Trimmed glyph (no basemap), Smoothness ring, car name, part of day | — |
| **Ring** | Big ring + score + route-record rosette if any + car silhouette | No route at all |
| **Week strip** | 7-day strip, week average + trend arrow | No locations |
| **Archetype** | Archetype art + two stats (drives, average Smoothness) | — |
| **Map** (P1, opt-in) | Stylised basemap, labels off, trimmed ends; warning sheet S-SHR-02 each time | — |
**Always:** the DrivePerf Loop mark + wordmark at bottom-centre (Pro: mark only, smaller; never none). Alt text is generated ("Route shape of a Thursday morning drive in my GTI. Smoothness 91.").
**Never on any card (CI-tested, F-SHR-01.2):** speed, max speed, G values, duration as an achievement, timestamps or dates finer than part of day, start or end points, street or place names.
Builder UI: a card preview (scaled to fit) with style chips below, format toggle (iOS segmented / Android button group), "Pro styles" row (lock for Free), then the share button → system share sheet. States: each style · 9:16 / 4:5 · short trip (Ring only) · Unscorable (glyph without ring) · Pro styles · Load (building animation, motion.md §3.7).

## 15. Paywall and lock sheet

### 15.1 Plans (S-SUB-04) and day-15 recap (S-SUB-02)
**Anatomy:** ① headline ("Your first two weeks, decoded." on S-SUB-02 / "DrivePerf Pro" on S-SUB-04) ② value list (from `entitlement.proFeatures`, 4 bullets max, each with a glyph) ③ **plan cards**: Annual (pre-selected): "CA$49.99/year", sub "CA$4.17/month", founding badge "First year CA$39.99" in gold; Monthly: "CA$8.99/month" ④ the purchase button ("Start Pro: CA$39.99 first year") **and "Continue with Free" with equal visual weight** (same size, same type style, stacked, the secondary style is not greyed) ⑤ legal block: renewal terms, how to cancel, Terms · Privacy · **Restore purchases** ⑥ close button (visible immediately).
**States:** US prices · CA prices · founding offer · not founding-eligible · Load (plan-card skeletons) · Err ("The store isn't responding. Your Preview and data aren't affected." + Retry) · Off ("You're offline. Purchases need a connection.") · pending purchase (Ask to Buy: "Waiting for approval").
**Never:** a countdown timer, strikethrough fake prices, a weekly or lifetime plan, a delayed close, a paywall during a trip, or one after a worse-than-usual Story (F-SUB-08).
| | iOS | Android |
|---|---|---|
| Presentation | `.fullScreenCover` (S-SUB-02) / sheet at the large detent (S-SUB-04); plan cards glass with a selected Glide ring; the StoreKit sheet is mocked | Full-screen dialog; plan cards `surfaceContainerHigh` with the selected one `primaryContainer` and its shape morphing to `extraLargeIncreased`; the Play Billing sheet is mocked |

### 15.2 Contextual lock sheet (S-SUB-03)
**Anatomy:** a blurred preview of **the user's own data** for the feature (14 px blur, 35% dim) at the top, with a lock glyph centred · one value sentence (`entitlement.lockSheets.*`) · "See plans" (primary) · "Not now" (secondary, returns exactly where they were).
Variants: compare with record · Replay/Analysis > 30 days · 3rd route · 2nd car · export · trends · backup · no data yet (illustration instead of a blur).
**Free-tier "locked, not lost" pattern:** summaries are never blurred. Only the Pro depth (Replay, Analysis, ghost, trends) is blurred, and only inside the lock sheet or the locked panel.

## 16. Permission primer (S-ONB-05 … S-ONB-12, S-ONB-16)

**Anatomy:** ① illustration (64–120 px, line art in Glide: location = route with a pin; motion = the G-dot; notifications = a "Drive decoded" card; battery = the Loop with a plug) ② title ("Record drives automatically") ③ why, in one or two sentences ("DrivePerf uses your location in the background only to record the route of drives it detects.") ④ what you get / what we never do (two short checklists: ✓ "Auto-detects most drives" · ✕ "Never sold, never shared with insurers") ⑤ the primary button that triggers the system prompt ("Continue") ⑥ secondary "Not now" or "Use manual mode" ⑦ footnote link "How it works".
**States:** default · system prompt mock (non-interactive frame over a dimmed primer) · granted (auto-advance with a check) · denied → S-ONB-16 (manual-mode sheet with a Settings deep link) · Android "Allow all the time" settings step (S-ONB-09: an annotated mock of the system Settings page with the option highlighted) · returned not granted.
The Android S-ONB-08 is the **Play prominent disclosure**: it says "location", "when the app is closed or not in use", and its toggle is off by default (Law 25).
| | iOS | Android |
|---|---|---|
| Layout | Full screen, illustration centred, buttons in a bottom glass container | Full screen edge to edge, buttons at the bottom: filled + text button |

## 17. Health-check row (S-SET-05, S-HOME-02)

**Anatomy:** ① status glyph: **green = filled check disc · amber = triangle · red = filled octagon with "!"** (shape carries the state) ② label ("Location: Always") ③ state text ("Allowed" / "While Using only") ④ fix action (trailing button "Fix", or a chevron → system settings / reliability guide).
**States:** green · amber (Low Power Mode, Battery Saver, notifications off) · red (Always lost, Background App Refresh off, motion off, Samsung sleeping apps) · checking (spinner) · paused (auto-detect paused, with a "Resume" button).
**Summary header:** "Automatic trips are ready." + the honest capture promise line, or "Auto-detect needs attention" (red count) · last check-in and last trip times (`meta`).
iOS rows: inset-grouped list, 52 pt minimum. Android: `ListItem` 56 dp minimum with a leading glyph and a trailing `TextButton`. Platform item sets are in `DP_DATA.health.{ios,android}.items`.

## 18. Coaching tip card and contributor row

- **Tip card:** "One thing for next time" (`caption`, `text2`) · tip text (`body`) · a trace sparkline (48 px; the event window's long-G with the peak marked) · "See the moment →" (opens S-RPL-02). It always follows a positive card ("Cleanest stretch…", F-SCO-06.2). There is no tip on Unscorable or passenger trips.
- **Contributor row** (S-SCO-01): label (Braking, Acceleration, Cornering, Consistency) · band chip "Smoother than usual" (▲ Glide) / "Usual" (●) / "Rougher than usual" (▼ neutral) · reason ("2 abrupt stops on the Gardiner") · chevron → the event list.

## 19. In-drive screen (S-DRV-01): a component, not a screen

Pure black (`--dp-drive-bg`), with ≤ 1 content update per second and no navigation.
**Anatomy:** ① live dot + "Recording" (`title2`) ② elapsed time (`num-xl`, tabular) and distance (`num-l`) ③ car chip (or "Passenger") ④ GPS state line ("Finding GPS…", "Battery saver: recording at lower detail") ⑤ **Stop** (96 tall, full width, `--dp-surface-3` with white text) ⑥ **I'm a passenger** (96 tall, outlined).
**Never:** speed, score, events, tips, maps, record pace or tab bar.
**States:** manual trip · auto trip ("Started automatically · first 340 m estimated") · GPS searching · low battery mode · passenger mode active (content unlocked: "You're a passenger. Your Story is ready to browse." + browse button).
Both platforms render it identically (it is deliberately not glass and not expressive: calm over cosmetics).

## 20. Live surfaces (S-LIVE-01 … 10)

All of them show **recording status only: elapsed time, distance and car. Never speed, score, events or record pace** (SAF-02). No map and no place names (F-CAP-09/11).

### 20.1 iOS Live Activity, Lock Screen (S-LIVE-01)
```
┌──────────────────────────────────────────────┐
│ [Loop] Recording · GTI                [Stop] │  ← Stop = LiveActivityIntent button (44 pt, glass)
│ 24:18            12.4 km                     │  ← Text(timerInterval:) + distance, DP Numerals
│ ▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁ (no progress bar: a trip has no end point) │
└──────────────────────────────────────────────┘
```
States: Recording · GPS searching ("Finding GPS…" instead of distance) · ending ("Drive decoded. Open to see your Story", stays 15 min) · 8 h cap ended ("Still recording. Open DrivePerf to see it.", since recording continues). Background `activityBackgroundTint(.black.opacity(0.55))`, Glide accent.

### 20.2 Dynamic Island (S-LIVE-02)
- **Compact:** leading = the Loop glyph (Glide, static), trailing = the timer `24:18` (tabular).
- **Minimal:** the Loop glyph only.
- **Expanded:** leading "Recording" + car · trailing timer · bottom: distance + a **Stop** button. There is no centre content.
- **Ending:** "Drive decoded" + check, auto-dismissed.

### 20.3 iOS Control Center control and widgets (S-LIVE-03/04)
Control: "Start drive" (Loop) / "Show live drive" (auto trip running; this attaches the Live Activity, designed for passengers and pre-departure, Gate 2) / "Stop drive". Widgets: accessory circular (Loop + last score), rectangular ("Last drive · 91 · Thursday morning"), small (ring 91 + "+7 vs usual"), medium (ring + last drive line + **Start** button). While recording: timer + Stop. Empty: "No drives yet. Just drive." Deny: "Location needed. Tap to fix."

### 20.4 Android Live Update and ongoing notification (S-LIVE-05/06/09)
- **Live Update (Android 16+, promoted):** `ProgressStyle` without a progress end point (or `MetricStyle`), not colourised, no custom RemoteViews. Status-bar **chip ≤ 7 characters**: "24 min". Content: "Recording your drive" · "24 min · 12.4 km · GTI" · action **Stop**. An auto trip is promoted only after confirmation, and never re-promoted after the user dismisses it.
- **Standard ongoing FGS notification** (< 16 or demoted): same content in a low-importance channel; small icon `icon-android-notification.svg`.
- Ending: replaced by "Drive decoded. Home → Work · Smoothness 91" (parked).

### 20.5 Android Quick Settings tile and Glance widget (S-LIVE-07/08)
Tile: "Start drive" (inactive state) → "Recording" (active, with the timer as subtitle) → unavailable ("Location needed"). Glance widget: last drive ring + "Thursday morning" + Start; during a trip, timer + Stop; Empty. It follows Material You widget theming: dynamic colour is allowed **for the widget surface only**, and data colours stay fixed.

### 20.6 Notifications (S-NTF-01)
Templates are in `DP_DATA.notifications`. Title in sentence case with a full stop ("Drive decoded."), body "{label} · Smoothness {n}". No emoji, no prices (except the day-13 Preview notice), no map. iOS: passive interruption level for recording, active for "Drive decoded"; held until parked. Android: channels Drive decoded / Routes and records / Weekly recap and Wrapped / Capture problems / Account and plan / Recording (low).

## 21. Empty, loading, error, offline and locked states (shared patterns)

| Pattern | Anatomy | Copy examples (voice: calm co-pilot) |
|---|---|---|
| **Empty** | Line illustration (Glide, 120 px; Android may set it inside an M3E `Arch` shape) · title · one sentence · optional primary action · the capture status | Drives: "Your drives will appear here. Just drive." · Routes: "Drive the same trip twice and it becomes a route." · Week: "No drives this week. Your car got a rest." · Garage: "Add the car you drive most." |
| **Loading** | Skeletons that mirror the final layout (`--dp-fill`, breathe 1.2 s); for the Story, the Loop loader; never a bare spinner on a blank screen. After 3 s, a progress line ("Decoding your drive… building the replay") | — |
| **Error** | Glyph (triangle) · what happened, in plain words · what stays safe · Retry | "Couldn't decode this drive. Your recording is safe. Try again." |
| **Offline** | Inline banner (not a blocking screen) · what still works | "Map unavailable offline. Your route, pins and data are all here." (the replay falls back to the plain-canvas map using `DP_DATA.util.trackPath`) |
| **Partial** | Content with a quality banner at the top + overlays (dashed / broken / hatched) | "Your phone moved around for about 3 minutes, so we left that part out of the score." |
| **Denied** | Inline card (Home) or sheet (S-ONB-16) with a Settings deep link and a manual-mode option | "Automatic trips are off. Location is set to 'While Using'. You can still start drives yourself." |
| **Locked** (Pro) | The user's own content blurred (14 px), lock glyph, value sentence, "See plans" / "Not now" | "Keep exploring every drive with Pro." |
| **Held while moving** (S-DRV-05) | Black, the Loop static, one line, no buttons except "I'm a passenger" | "Your Story will be ready when you've parked." |
| **Passenger / not a drive** | Banner on the trip: glyph + "Marked as passenger. It isn't scored and doesn't count toward your baseline, routes or recaps." + Change | — |

## 22. Lists, rows, form controls and banners

- **List row:** iOS inset-grouped (radius 24 container, 52 pt rows, 16 margin, separators inset 16); Android `ListItem` (56 dp single-line, 72 two-line) in `surfaceContainer` sections with 28 dp section gaps. Row anatomy: leading glyph (24) · title · supporting text · trailing value, toggle or chevron.
- **Toggle:** iOS `Toggle` with Glide tint; Android `Switch` (M3, with thumb icon).
- **Text field** (car name, email, typed "DELETE"): iOS rounded glass-free field (`--dp-surface-2`, radius 14); Android `OutlinedTextField` (shape `extraSmall` 4).
- **Banner (inline):** a 12 px-radius tonal card with glyph · text · action: info (neutral), attention (warning container), critical (critical container). The recording banner is in §1.
- **Toast / snackbar:** iOS a glass capsule at the top ("Marked as needed · Undo"); Android `Snackbar` at the bottom with an action.
- **Preview badge:** "PRO PREVIEW · DAY 9" (iOS glass capsule with Glide text; Android `AssistChip` with `primaryContainer`). Tap → S-SUB-01.
- **Pro badge:** "PRO" in Glide on `--dp-accent-container`, caption caps, letter-spacing 0.6. Never gold (gold means a record).

## 23. Story player frame (S-STY-01, S-RCP-01, S-WRP-*)

**Anatomy:** progress segments (top, 3 px, gap 4) · close (top-trailing) · **frame stage** (full bleed, night + car-colour wash) · copy block (`storyHeadline` + `storyBody`, bottom-left, max 3 lines + 2 lines) · a contextual chip (passenger chip on f1, car chip, "Was it needed?") · bottom actions on f7 ("Explore the drive" primary · Share · Done) + the quality badge.
The copy for every frame comes from `trip.story.frames` (fixtures.md). Variants: default · first drive · calibrating · route discovered (merge + name chips) · new route record · degraded · Unscorable (frames 1–2 and 7 only) · "Was it needed?" inline · passenger chip · transit question ("Was this transit? Bus · Train · Streetcar · No, I drove") · Load · RM · XL (copy block grows upward; the stage art shrinks to 40%) · screen reader (each frame is one element; auto-advance pauses on focus).
