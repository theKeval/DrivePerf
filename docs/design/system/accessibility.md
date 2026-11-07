# DrivePerf accessibility and driving-safety UI rules

**Jira:** DRIVE-99 · A6 · 6 Nov 2026. This implements PRD §6.1 (SAF-01 to SAF-10) and §6.8, plus F-STY-06. It is binding on A7, A8, A15 and A16. A screen is not "ready" until it passes the checklist in §9.

## 1. Baseline

| Requirement | Target |
|---|---|
| Text contrast | ≥ 4.5:1 (body), ≥ 3:1 (≥ 24 px regular or ≥ 19 px bold). Every `text`/`text2` token pair passes on its surfaces in both themes. `text3` (3.9:1 dark) is for non-essential meta only |
| Non-text contrast | ≥ 3:1 for icons, pins, chart lines, focus rings and control borders (data colours were checked: brand.md §5.2) |
| Text size | iOS Dynamic Type through **AX5**; Android font scale to **200%** (PRD §6.8). Nothing truncates; layouts reflow |
| Touch targets | **≥ 44 × 44 pt (iOS) / 48 × 48 dp (Android)**. In-drive (S-DRV-01, S-DRV-05) **≥ 64**, with the Stop and Passenger buttons 96 tall and full width |
| Screen readers | VoiceOver and TalkBack labels for **every** control, chart and map. Charts carry a text summary |
| Colour | Never the only signal (§4) |
| Motion | A reduced-motion variant for every animated element (motion.md §5) |
| Sound | DrivePerf plays no sound. Haptics are optional reinforcement, never the only feedback |

## 2. Dynamic Type and large fonts

Prototype hook: `[data-text-size="l"]` (1.18×) and `[data-text-size="xl"]` (**2.0×**, the PRD "XL" state) scale every `--dp-font-*`. DP Numerals scale by `--dp-num-scale` (1.1× / 1.3×) because they are already large.

| Component | At XL (≥ AX3 / ≥ 175%) |
|---|---|
| Tab bar | iOS: the system shows the large-content viewer on long-press, and labels stay. Android: labels stay, with up to 2 lines |
| Stat tiles | The grid goes from 3 → 2 → 1 column. The value stays on one line; the label wraps above |
| Trip card | The glyph thumbnail moves above the text. Stats wrap to two lines. The score badge moves to the trailing top |
| Score ring | The ring grows to fit the number (hero number ≤ 1.3×). The comparison chip moves below and wraps |
| Synced timeline | Lanes grow 1.5×. Cursor bubbles move into a readout row above the chart. The legend wraps |
| Replay | Readouts stack vertically in the top capsule. The speed selector becomes a menu ("Speed: Auto") |
| Story frames | The copy block grows upward and the stage art shrinks to 40% height. Frames auto-advance at 1.5× duration. No text over art without a scrim |
| Sheets | Open at the large detent. The evidence-sheet trace stays 96 px; the facts grid goes to 1 column |
| Paywall | Plan cards stack. Legal text stays fully visible and scrolls, never shrinks |
| Live Activity / widgets | The system caps them. Our layouts use `ViewThatFits` (iOS) and Glance `SizeMode.Responsive` (Android) to drop the car name first, then the distance |

Units never wrap away from their number (non-breaking thin space). Hyphenation is off for numbers and place names.

## 3. Screen-reader labels (VoiceOver / TalkBack)

**Pattern:** *what it is* + *the value with units spelled out* + *how it compares*, then the action. Say "kilometres per hour", not "km/h"; "0.42 g" is read as "0.42 g". Signed deltas: "3 above your usual", "2 below your usual" (never "minus").

| Element | Label (example) | Traits / actions |
|---|---|---|
| Score ring | "Smoothness 84. 3 above your usual on The Commute." Record: "…New route record, beating 89 from 14 October." | Static text. Merged with the chip |
| Delta chip (alone) | "3 above your usual on The Commute" | — |
| Stat tile | "Distance, 24.6 kilometres. Derived." Estimated: "Estimated start distance, about 0.3 kilometres." | Long-press action: "Show how this was measured" |
| Quality badge | "Good data. Phone mounted, 50 hertz." | Button → "Opens data quality details" |
| Trip card | "Home to Work, Thursday morning, 5 November. 37.9 kilometres, 33 minutes. Smoothness 91, 7 above your usual. New route record. Good data." | Button. Custom actions: Share, Replay, Delete |
| Event pin | "Hard braking at Spadina and Front, 8:14 am. Peak 0.42 g." Needed: "…Marked as needed, not counted." | Button → evidence sheet. Rotor (iOS) / headings (Android): "Events" |
| **Map (Replay)** | Container: "Map of your drive, 37.9 kilometres from Home to Work. 3 events, 4 stops." Pins are a navigable list in time order, followed by stops and then max speed | iOS: `accessibilityElements` ordered by time. Android: `traversalIndex` by time |
| **Replay scrubber** | "Replay position, 8 minutes 12 seconds of 33 minutes. Speed 92 kilometres per hour." | **Adjustable**: swipe up/down moves ±30 s. Custom actions **"Next event" / "Previous event" / "Next stop"** (iOS `accessibilityCustomActions`, Android `customActions`); `progressBarRangeInfo` on Android |
| Play / pause | "Play replay" / "Pause replay", "Speed: Auto" | Toggle |
| **Synced timeline** | Summary: "Timeline, 33 minutes. Speed peaked at 106 kilometres per hour on the QEW. Longitudinal G stayed within 0.2 g except 3 braking events, the largest 0.45 g on the Gardiner. Lateral G stayed within 0.15 g." | iOS: `accessibilityChartDescriptor` (**Audio Graph**: one series per channel). Android: **"View as table"** action → an accessible table (time, speed, long-G, lat-G at 30 s steps + every event row) |
| **G-G circle** | "Friction circle. Most inputs stayed within 0.2 g. Peaks: braking 0.42 g, acceleration 0.31 g, cornering 0.18 g." | Chart descriptor (scatter, x lateral, y longitudinal) on iOS |
| Evidence trace | "Speed and G from 10 seconds before to 10 seconds after. Speed fell from 48 to 0 kilometres per hour over 4 seconds. Braking peaked at 0.42 g." | — |
| Route glyph | "Route shape for The Commute" (decorative inside a labelled card: hidden) | — |
| Dot strip (route runs) | "10 drives on The Commute. Record 91 on 5 November. Latest 86 on 3 November." | Adjustable: swipe through runs |
| **Story frame** | The **full copy of the frame** as one element, e.g. "Smoothness 84. 3 above your usual on this route." | Auto-advance **pauses while a frame has screen-reader focus** (F-STY-06.1). Actions: Next frame, Previous frame, Close |
| G-dot replay (Story f3) | "Your inputs, replayed. Mostly gentle, with one firm stop near the end." (an A12 summary sentence) | — |
| Weekly strip | "This week: 9 drives over 6 days, 212 kilometres, 4 hours 40 minutes. Average Smoothness 82, up 3." | — |
| In-drive screen | "Recording. 24 minutes, 12.4 kilometres, GTI." Buttons: "Stop recording", "I'm a passenger" | Timer updates are **not announced** (`.updatesFrequently` without a live region / `liveRegion = None`) |
| Live Activity / Dynamic Island | "DrivePerf recording, 24 minutes, 12.4 kilometres." Stop: "Stop recording" | — |
| Health-check row | "Location, Always. Needs attention. Fix" | Button |
| Locked panel | "Replay for drives older than 30 days is part of Pro. See plans." | Button |

**Focus order:** title → primary value (score) → comparison → supporting stats → actions. Sheets move focus to their title on open and return focus to the invoking control on close. Story, recap and Wrapped trap focus inside the player until closed.

## 4. Colour-independent meaning

| Meaning | Colour | Plus shape / glyph | Plus text |
|---|---|---|---|
| Hard braking / acceleration / cornering | Event colours | **Rounded square / circle / diamond** + chevron-down / chevron-up / curved-arrow glyphs | "Hard braking" etc. in sheets, chips and lists |
| Needed event | Event colour outline | **Hollow** shape | "Needed" |
| Better / lower than usual | Glide / neutral | ▲ / ▼ / ● | "+3" / "−2" / "Right on your usual" |
| Data quality | Four quality colours | Filled check / half disc / triangle ! / slashed ring | "Good / Usable / Limited / Unscorable" |
| Provenance | none (inherits) | Filled dot / ring with dot / **dashed ring** + "≈" prefix | "Observed / Derived / Estimated" on long-press |
| Estimated, gap and excluded spans | Channel colour | **Dashed / line break / 45° hatch** | "Estimated start" / "No GPS" / "Phone moved — not scored" |
| Chart channels | Neutral / sky / amber / terrain | Solid / solid 2 px / **dashed** / **area fill** | Legend + lane labels |
| Route record | Gold | Rosette glyph | "Route record" |
| Health Check | Green / amber / red | Check disc / triangle / **octagon with "!"** | "Ready" / "Needs attention" |
| Recording | Glide | Pulsing then static dot | "Recording" |
| Gained / lost (ghost) | Glide glow / neutral | Thick glow / thick plain / dotted (unaligned) | Callouts "Where you gained most" |
| Selected tab | Glide | iOS: the lens capsule; Android: the indicator pill | Label always visible |

iOS "Differentiate Without Colour" and Android high-contrast text need no extra mode: the patterns are always on. With **Increase Contrast** (iOS) or **high-contrast text** (Android), borders go to 1 px `--dp-outline` at 100%, and glass gets `--dp-glass-fill-strong`.

## 5. Reduced motion, transparency and other settings

- **Reduce Motion** → motion.md §5 (`[data-reduced-motion="true"]` in the prototypes).
- **Reduce Transparency** (iOS) → glass becomes opaque `--dp-surface-3` (`[data-reduced-transparency="true"]`). Android has no glass, so nothing changes.
- **Bold Text** (iOS) / **font weight adjustment** (Android 12+) → the SF and Google Sans Flex weights step up. DP Numerals cap at 700.
- **Smart Invert** → maps, route glyphs, share-card previews and car colours set `accessibilityIgnoresInvertColors`.
- **Dark / light** follow the system, with an override in S-SET-02.
- **Auto-advance** in Story, recap and Wrapped: tap-and-hold pauses; screen-reader focus pauses; "Auto-advance" can be switched off in S-SET-02 (it becomes tap to advance).

## 6. Touch targets and gestures

- Every tappable element is ≥ 44 pt / 48 dp, including pins (28 px visual, 44/48 hit area). When pins collide they **cluster** rather than shrinking their hit areas.
- Every gesture has a button equivalent. Swipe-down-to-close also has a close button. The scrubber has next/previous event buttons in the evidence sheet. Swipe actions on trip cards also exist in the overflow menu and as screen-reader actions.
- Avoid timing-based gestures. Long-press for provenance is always paired with a visible ⓘ in dense views (Analysis, trip detail).
- In-drive: two 96-tall buttons with 24 pt between them, nothing near the screen edges (to avoid stray touches on a mount), and no swipe gestures at all.

## 7. Driving-safety UI rules (binding)

These turn PRD §6.1 into checkable UI rules. A violation blocks a screen in design review.

| # | Rule | How it shows in the UI | PRD |
|---|---|---|---|
| D1 | **While a driver trip is active, the app shows only S-DRV-01.** Deep links, notification taps, widget taps and Control Center taps land on S-DRV-01 or S-DRV-05 | The shell checks `trip.active && !passenger` before every navigation. S-DRV-05 opens the requested content automatically after 60 s stationary | SAF-01, F-CAP-07, F-STY-05 |
| D2 | **No live score, speed, events, coaching, alerts or record pace on any surface while moving**: app, Live Activity, Dynamic Island, Live Update, widgets, notifications | Live surfaces show only elapsed time, distance and car (components.md §20). Snapshot tests per surface | SAF-02 |
| D3 | In-drive screen: black, ≤ 1 content update per second, targets ≥ 64 (96 for Stop/Passenger), no navigation, no map, no tab bar | components.md §19 | F-CAP-07.5 |
| D4 | **Notifications are held until parked**, except the trip's own recording notification | Notification templates have a `heldUntilParked` flag; the prototypes show a "held" badge in the catalogue | SAF-03, F-NTF-01 |
| D5 | **No speed or max G on share cards, recaps, Wrapped, records, notifications or achievements.** Speed appears only in personal Replay, Analysis, trip detail and export, as neutral text | Share-card templates have no speed or G fields (CI). Max speed is an outline pin with no animation | SAF-04, F-SHR-01.2 |
| D6 | **Speed-neutral styling** wherever speed appears: `text` colour, no gauge, no colour scale, no redline, no badge, no haptic, never the first stat | Stat tile and pin specs | PRD §3.5 |
| D7 | **No streaks** of any kind, no "don't break it", no consecutive-day counters. The weekly focus is "gentler in 7 of 9 drives", not a chain | Copy review; there is no streak component in the library | SAF-05, F-SCO-10.3 |
| D8 | **Route records are Smoothness-only.** Duration is shown neutrally in tables, never as a delta, a win or a sort key | Route card and ghost specs | SAF-06, F-RTE-03 |
| D9 | No leaderboards, friends, feeds or other-user comparisons. Every comparison is "vs your usual" | Delta chip copy is fixed to "vs your usual …" | SAF-07, F-SCO-01.3 |
| D10 | Voice guide: never "fastest", "beat your time", "send it", "launch", "crush" | Copy lint list in A2 §6 | SAF-08 |
| D11 | No marketing or store imagery of a phone being used in a moving car. Prototype annotations describe in-drive frames as "phone mounted, untouched" | — | SAF-09 |
| D12 | No paywall, share prompt, rating prompt or tip during a trip or on S-DRV-05 | The shell suppresses them; the prototypes show the suppression | F-SUB-08.2, F-SHR-01.4 |
| D13 | iOS "Show live drive" control copy is aimed at passengers and pre-departure, never at use while moving ("Show live drive" + the footnote "For passengers, or before you set off") | S-LIVE-03 | Gate 2 OQ-24 |
| D14 | The "I'm a passenger" escape is always one tap from S-DRV-01 and S-DRV-05 | components.md §19 | F-CAP-08 |

## 8. Cognitive load and tone

- **One tip per drive**, after a positive observation. Plain language, with no jargon without a gloss (for example, "G (how hard the car pushed you)" the first time).
- No time pressure: no countdowns, no "limited time", and Story auto-advance can be paused or switched off.
- Numbers are never more precise than A12's accuracy (brand.md §6).
- A worse-than-usual drive explains and never blames ("Down 2. Thursday's drive pulled it down. Here's why →"), and never ends in a paywall.

## 9. Per-screen checklist (prototype agents: put this in each `design-spec.md`)

- [ ] Light and dark both render, and contrast passes.
- [ ] The XL text state reflows with no truncation (`data-text-size="xl"`).
- [ ] The RM state is defined (`data-reduced-motion="true"`).
- [ ] Every control has a label; every chart and map has a summary sentence.
- [ ] Every target ≥ 44 pt / 48 dp (≥ 64 in-drive).
- [ ] No meaning by colour alone (pins, quality, deltas, spans).
- [ ] Speed is neutral wherever it appears and absent from social or live surfaces.
- [ ] No streak, leaderboard, speed or "beat your time" language.
- [ ] Behaviour is defined while a trip is active (blocked → S-DRV-05, or allowed for passengers).
- [ ] The haptics used are from motion.md §6 only.
