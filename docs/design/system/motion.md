# DrivePerf motion language

**Jira:** DRIVE-99 · A6 · 6 Nov 2026. Values are in [tokens.json](tokens.json) (`motion`, `haptics`). The CSS is in [`prototypes/shared/tokens.css`](../../../prototypes/shared/tokens.css), and the JS runtime is [`prototypes/shared/motion.js`](../../../prototypes/shared/motion.js) (`DP_MOTION`).

> **Feel:** a well-sorted car on a smooth road. Movement has weight and settles without fuss. Nothing snaps, nothing wobbles twice, and nothing moves while you drive.

## 1. Principles

1. **Physical, not timed.** Anything a finger can touch or interrupt uses a **spring**, so it keeps its velocity when retargeted. Durations are only for choreographed, non-interactive sequences such as draw-ons, count-ups and Story frames.
2. **One hero motion per screen.** Story frame 2 has the route, frame 4 has the ring, Replay has the marker. Everything else supports it with quiet fades and small rises (8 px).
3. **Motion explains cause.** The ring sweeps *past* the old record marker, the route lines *merge* when a route is discovered, and the pin *pulses* as the marker passes. Decoration that explains nothing is cut.
4. **Platform-native feel, shared choreography.** Timelines (what moves, in what order) are identical on both platforms. The *springs* are native: iOS uses SwiftUI `spring(duration:bounce:)` values, and Android uses the M3 Expressive `MotionScheme.expressive()` tokens, which are bouncier by design.
5. **Never while driving.** During an active driver trip the only motion is the recording dot's 10-second pulse when the in-drive screen appears or is touched (§3.2). There are no transitions to content, because content is blocked (SAF-01/02, F-CAP-07).
6. **Reduced motion is a first-class variant**, not a degraded one (§5).

## 2. Tokens

### 2.1 Springs

Physics: mass 1, stiffness *k*, damping ratio ζ. In CSS, each spring is baked into a `linear()` easing plus a settle duration (to within 0.2%).
Usage: `transition: transform var(--dp-spring-default-duration) var(--dp-spring-default);` or `DP_MOTION.spring({preset:'default', …})` / `DP_MOTION.animate(el, kf, {preset:'fast'})`.

| Token | iOS (SwiftUI) | ζ / k | Settle · overshoot | Android (Compose M3E) | ζ / k | Settle · overshoot | Use |
|---|---|---|---|---|---|---|---|
| `spring.fast` | `.spring(duration: 0.25, bounce: 0.2)` | 0.80 / 632 | 319 ms · 1.5% | `fastSpatialSpec()` | 0.6 / 800 | 347 ms · 9.5% | Press feedback, chips, toggles, pin select, scrubber thumb |
| `spring.default` | `.spring(duration: 0.42, bounce: 0.15)` | 0.85 / 224 | 538 ms · 0.6% | `defaultSpatialSpec()` | 0.8 / 380 | 411 ms · 1.5% | Cards, layout changes, accessory/toolbar, chip pop |
| `spring.slow` | `.spring(duration: 0.6, bounce: 0.1)` | 0.90 / 110 | 553 ms · 0% | `slowSpatialSpec()` | 0.8 / 200 | 567 ms · 1.5% | Hero moves: trip-start expansion, Wrapped chapters |
| `spring.effects` | `.spring(duration: 0.35, bounce: 0)` (≈ `.smooth`) | 1.0 / 322 | 471 ms · 0% | `defaultEffectsSpec()` | 1.0 / 1600 | 211 ms · 0% | Opacity, colour, blur. Never overshoots |
| `spring.effectsFast` | — | — | — | `fastEffectsSpec()` | 1.0 / 3800 | 137 ms | Ripple and state layers |
| `spring.effectsSlow` | — | — | — | `slowEffectsSpec()` | 1.0 / 800 | 299 ms | Large fades |
| `spring.bouncy` | `.spring(duration: 0.5, bounce: 0.32)` | 0.68 / 158 | 763 ms · 5.4% | `spring(0.5f, 400f)` | 0.5 / 400 | 612 ms · 16% | **Rare** celebration: record marker flip, archetype card flip, Welcome to Pro |
| `spring.sheet` | system sheet (`.presentationDetents`) | 0.92 / 158 | 502 ms | `spring(0.9f, 380f)` | 0.9 / 380 | 297 ms | Sheets, paywall cover |
| `shared.gMeter` | custom (§3.5) | 0.55 / 120 | 1041 ms · 12.6% | same | | | The G-dot follower in the Story |
| `shared.scrubber` | custom | 1.0 / 900 | 282 ms · 0% | same | | | Marker/G-dot catch-up while scrubbing (data must not overshoot) |
| `shared.marker` | custom | 1.0 / 260 | 524 ms · 0% | same | | | Replay camera follow |
| `shared.pinPop` | `.spring(duration: 0.28, bounce: 0.55)` | 0.45 / 520 | 539 ms · 20% | `spring(0.45f, 520f)` | | | Event pins appearing on the map |

### 2.2 Durations and easings (choreography only)

| Token | Value | | Token | Value |
|---|---|---|---|---|
| `instant` / `micro` | 90 / 150 ms | | `ease.standard` | `cubic-bezier(0.2, 0, 0, 1)` (M3 standard) |
| `short` / `medium` | 220 / 320 ms | | `ease.emphasizedDecelerate` | `cubic-bezier(0.05, 0.7, 0.1, 1)` (enter) |
| `long` / `xlong` | 450 / 700 ms | | `ease.emphasizedAccelerate` | `cubic-bezier(0.3, 0, 0.8, 0.15)` (exit) |
| `routeDrawOn` | 1800 ms | | `ease.glide` | `cubic-bezier(0.32, 0.72, 0, 1)` (house ease-out, iOS-sheet-like) |
| `ringReveal` / `countUp` | 1400 / 1200 ms | | `ease.drawOn` | `cubic-bezier(0.65, 0, 0.35, 1)` (symmetric in-out for strokes) |
| `gReplay` | 3000 ms | | `ease.countUp` | `cubic-bezier(0.16, 1, 0.3, 1)` (fast start, long settle) |
| `shareBuild` | 900 ms | | `ease.breathe` | `cubic-bezier(0.37, 0, 0.63, 1)` |
| `storyFrame` | 2500–3500 ms | | Staggers | list 30 · chips 40 · Story lines 60 · share layers 90 · Wrapped tiles 70 ms |
| `wrappedChapter` / `paywallPresent` | 650 / 520 ms | | `livePulse` / `pinPulse` / `recordShimmer` | 2400 / 600 / 900 ms |

All durations scale by `--dp-motion` (1, or 0 when reduced): `calc(var(--dp-dur-long) * var(--dp-motion))`.

## 3. Signature transitions

Each entry gives the timeline (t = 0 at the trigger), the web values for the prototypes, the native equivalents, the haptic and the reduced-motion (RM) fallback.

### 3.1 Trip start (manual): Start → in-drive screen (S-HOME-01 → S-DRV-01)

| t (ms) | What happens | Web | SwiftUI | Compose |
|---|---|---|---|---|
| 0 | Press: the Start control scales to 0.96. Haptic `confirm` fires on release | `spring.fast` | `.scaleEffect` + `.sensoryFeedback(.impact(weight: .medium), trigger:)` | `Modifier.graphicsLayer` + shape morph round → `buttonPressed` (12 dp) |
| 0–120 | The label cross-fades "Start drive" → "Starting…" | `spring.effects` | `.contentTransition(.opacity)` | `AnimatedContent(fadeIn+fadeOut)` |
| 120–640 | **Expansion.** The iOS bottom accessory, or the Android floating toolbar, grows into the full-screen in-drive surface. The background goes to `--dp-drive-bg` (#000). The Loop mark scales from 20 → 120 px into the recording ring | `spring.slow` on a FLIP container; background `spring.effects` | `matchedGeometryEffect` (accessory ↔ ring) inside `.fullScreenCover` with `.transition(.identity)` | Container transform: `SharedTransitionLayout` + `sharedBounds()` with `slowSpatialSpec()` |
| 640–1000 | Elapsed time, distance and car chip rise 8 px and fade in, staggered 60 ms | `spring.default` | `.transition(.move(edge: .bottom).combined(with: .opacity))` | `AnimatedVisibility(slideInVertically + fadeIn)` |
| 1000+ | **The screen goes still** (§3.2 rules). The Live Activity / Live Update appears using system motion | — | ActivityKit (system) | system |

- **Auto trip:** there is no theatre. If the app is foregrounded mid-trip, S-DRV-01 appears with a 220 ms cross-fade.
- **Trip end** (Stop → S-DRV-02 sheet → S-DRV-04 "Decoding your drive"): the ring's trail retracts (reverse draw, 450 ms), then the decoding loader runs (iOS: the Loop's dot orbits the trail at 1.6 s per revolution, linear; Android: M3E `ContainedLoadingIndicator` shape morph). When the drive is decoded, the loader's ring *becomes* the Story's progress bar (shared element, `spring.default`).
- **RM:** cut to S-DRV-01 with a 220 ms cross-fade. The loader is static with "Decoding… 3 of 4 steps".

### 3.2 Live recording pulse

| Surface | Behaviour |
|---|---|
| **S-DRV-01 (in-drive)** | A 12 px Glide dot with a halo. The halo `scale 1 → 1.9`, `opacity 0.5 → 0` over 900 ms `ease.glide`, triggered **once per clock second**, and it runs only for **10 s** after the screen appears or after any touch. After that the dot holds still at 100% and only the elapsed time and distance text update, ≤ 1 per second (F-CAP-07.5). The animation is compositor-only (transform and opacity). Web: `@keyframes dp-live-breathe` with `animation-iteration-count: 10`. |
| S-NAV-01 recording banner (passenger trip, or a driver trip after parking while still finalising) | Continuous `breathe` at `livePulse` 2400 ms: halo scale 1 → 1.9, opacity 0.9 → 0 |
| iOS Live Activity / Dynamic Island | A **static** dot plus system-driven `Text(timerInterval:)`. No custom animation: ActivityKit doesn't allow it, and we wouldn't want it |
| Android Live Update | A static status chip ("24 min", ≤ 7 characters). No animation |
| RM | A static dot everywhere |

### 3.3 Route draw-on (Story frame 2, trip detail map reveal, route discovered)

| t (ms) | What happens |
|---|---|
| 0–900 | The map camera eases to fit the route (`ease.glide`, 900 ms). The map is muted to 70% while the line draws |
| 0–1800 | The route stroke draws from start to end (`stroke-dashoffset: L → 0`, `ease.drawOn`, 1800 ms). A 6 px glow head rides the leading tip. The first and last 400–800 m are already drawn faded (`--dp-data-route-faded`), and the estimated start segment is drawn dashed |
| 300–1500 | Distance and duration count up (`ease.countUp`, 1200 ms, tabular numerals) |
| 1800–2100 | The glow head dissolves and the line settles to full Glide (`spring.effects`) |

- **Route discovered (R1):** two route lines, 14 px apart, slide together (`spring.default`) and merge into one line with a 300 ms glow, then the "Looks like a regular" chips pop in (stagger 40 ms, `spring.fast`).
- Web: `DP_MOTION.drawOn(pathEl, {duration: 1800})`. SwiftUI: `Path.trim(from: 0, to: p)` with `.animation(.timingCurve(0.65, 0, 0.35, 1, duration: 1.8), value: p)`; MapKit `MapCameraPosition` change in `withAnimation(.smooth(duration: 0.9))`. Compose: `PathMeasure.getSegment(0f, len * p, dst)` with `animateFloatAsState(tween(1800, easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)))`; `cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(b, 48), 900)`.
- **RM:** the route appears complete with a 220 ms fade, and the numbers are set without counting.

### 3.4 Smoothness ring reveal (Story frame 4, S-TRP-01, S-SCO-01)

| t (ms) | What happens | Haptic |
|---|---|---|
| 0–150 | The ring track (`--dp-fill`) fades in | — |
| 150–1550 | The arc sweeps 0 → score/100 (`ease.countUp`, 1400 ms). The number counts up **locked to the arc** (one driver value) | `tick` (light, intensity 0.5) at every 10 points: 8 ticks for 84 |
| 400–700 | The **baseline notch** (a 2 px tick in `text2` at the baseline value) scales 0.6 → 1 (`spring.fast`) | — |
| 1550 | Landing: the number weight steps 600 → 700 for 120 ms | `tap` |
| 1600–2000 | The comparison chip ("+3 vs your usual on The Commute") pops: scale 0.86 → 1, opacity 0 → 1 (`spring.default`) | — |
| **Record variant** | The old-record notch is gold-outlined. As the arc passes it, the notch **flips** to solid gold (`rotateY 180°`, `spring.bouncy`). One shimmer runs along the arc (`recordShimmer` 900 ms, linear gradient sweep), then the chip reads "New route record." | `doubleSuccess` |
| Calibrating variant | The arc sweeps only to the score, and the notch is replaced by "Calibrating · 2 of 3" | `tap` only |

- **Android extra:** the score badge on trip cards and S-TRP-01 is an M3E `Cookie9Sided` shape. During the count it **morphs to a circle**, and on landing it morphs back (`Morph(Cookie9Sided, Circle)`, `defaultSpatialSpec()`).
- **iOS:** draw the arc with `Circle().trim(from: 0, to: p)` and a custom `Animatable` `CountingText(value: p * score)`. Use `.contentTransition(.numericText(value:))` only for later in-place updates, such as a recompute after "Was it needed?".
- **Recompute** (a needed brake is marked, F-SCO-05, ≤ 1 s): the arc tweens from the old to the new value with `spring.default`, and the digits roll (`numericText`).
- **RM:** the ring is drawn full and the number is static. The chip fades in over 220 ms. A single `tap` haptic remains.

### 3.5 G-meter physics (Story frame 3, Analysis G-G, Replay mini G-dot)

- **Model:** a damped spring follower pulls the dot toward the current sample: `a = −k(x − target) − 2ζ√k·v`. In the Story, ζ 0.55, k 120 (`shared.gMeter`), so the dot has mass. It leans into a brake and settles after it, the way the body does. In Analysis and Replay (data views) the follower is `shared.scrubber` (ζ 1, k 900), which is critically damped: **the dot never shows a value the car didn't reach.** The numeric readout always shows the exact sample, never the follower.
- **Trail:** the last 1.2 s of follower positions, drawn as a polyline that fades from opacity 0 → 0.8 and tapers in width 2 → 6 px toward the head. Story frame 3 compresses the whole drive into **3 s** (`gReplay`): samples are resampled to that timeline and the follower smooths the result. When the dot stops, the full G-G cloud fades in beneath it (30%) and the envelope draws on (400 ms).
- **Geometry:** concentric rings at 0.1 g steps, with 0.3 g and 0.5 g labelled; axes are lateral (x, right +) and longitudinal (y, acceleration up, braking down). The scale caps at 0.6 g, beyond which the dot clamps to the rim with a notch. Max G is never displayed as a badge (SAF-04).
- **Frame pacing:** drive the follower from the display clock (CADisplayLink at `preferredFrameRateRange(80…120)`, Compose `withFrameNanos`, web `requestAnimationFrame` with dt), never from the sample clock.
- Web: `const f = DP_MOTION.follower({preset}); f.target(latG, lonG); f.step(dt)`.
- **RM:** a static G-G plot with the full trail and no moving dot (F-STY-06.2).

### 3.6 Replay marker, event pins and pin pop (S-RPL-01)

| Element | Motion |
|---|---|
| **Marker** | A 14 px white dot with a 3 px Glide ring and a 40° heading wedge. Position = Catmull-Rom interpolation of the 1 Hz rows at display time *t* (display-only, F-RPL-01). Playback: `t += dt × rate` (Auto = the whole trip in ~60 s; 1×, 10×, 30×). |
| **Camera** | "Lazy follow": the camera only re-centres when the marker leaves the central 60% box, using `shared.marker` (ζ 1, no overshoot, to avoid nausea). Pinch or drag hands control to the user until they tap "Re-centre". |
| **Pins on load** | In time order, staggered 40 ms: scale 0 → 1 and rise 6 px → 0 with `shared.pinPop`. Needed (hollow) pins pop with no overshoot. |
| **Pin pulse** | When the marker passes an event: a ring at the pin scales 1 → 2.4 and fades 0.7 → 0 over 600 ms `ease.glide`, **once**. No sound and no haptic (F-RPL-03.2). |
| **Pin tap** | The pin scales 1 → 1.18 (`spring.fast`) and other pins dim to 50%. Playback pauses, the map centres on the event (`shared.marker`) and the evidence sheet opens at the medium detent. Haptic `tap`. |
| **Scrubber** | While dragging, the thumb grows 1 → 1.35 (`spring.fast`). Within 8 px of an event tick it snaps, with haptic `selection`. The marker and readouts follow with `shared.scrubber` and must update within ≤ 100 ms (F-RPL-01.3). On release, the previous play state resumes. |
| **Max-speed pin** | **No animation, no haptic, no pulse**: speed-neutral rule. It appears with the route, not with the event pins. |
| **GPS gap** | The marker fades out (150 ms) at the gap start and reappears at the gap end. A "No GPS" chip slides in (`spring.default`) and stays until the gap ends. Estimated start: "Estimated start" chip. Excluded span: "Phone moved — not scored" chip. |
| **RM** | Pins appear without the pop. The pulse is replaced by a 600 ms outline highlight with no scale. The camera jumps instead of gliding. The marker still moves when the user presses Play, because that is user-initiated content. |

### 3.7 Share-card build (S-SHR-01, recap W5)

| t (ms) | Layer |
|---|---|
| 0 | The card surface scales 0.94 → 1 and fades in (`spring.default`) |
| 90 | The route glyph draws on (600 ms, `ease.drawOn`), or for the Ring style the ring sweeps (600 ms, `ease.countUp`) |
| 180 | Car chip and part of day rise 8 px (`spring.default`) |
| 270 | Smoothness number counts up (600 ms) |
| 360 | The DrivePerf mark fades in (`spring.effects`). Total ≈ 900 ms (`shareBuild`). Haptic `success` when it is ready |

- **Style switch** (Glyph · Ring · Week · Archetype · Map): cross-dissolve plus a ±6° `rotateY` tilt in the swipe direction (`spring.default`). Haptic `selection`.
- **Format switch** (9:16 ↔ 4:5): the card container morphs its aspect ratio (`spring.default`) and the content re-lays out inside. On Android this is a connected **button group**; on iOS, a glass segmented control.
- **Weekly recap W5:** "the card assembles from the frames": the week strip, trend line and drive-of-the-week tile fly from their frame positions into the card slots (shared elements, `spring.slow`, stagger 90 ms).
- **RM:** cross-fades only. The card renders complete.

### 3.8 Story, recap and Wrapped chapter transitions (S-STY-01, S-RCP-01, S-WRP-01/02)

- **Player chrome:** segment progress bars at the top fill linearly for the frame duration (2.5–3.5 s). Tap right = next, tap left = back, press and hold = pause, swipe down = exit. The exit is interactive: the content scales to 0.9 and the corner radius grows with drag progress, then commits or springs back with `spring.default`.
- **Drive Story frame change:** the outgoing frame fades out; the incoming copy rises 12 px, line by line (stagger 60 ms, `spring.default`), while the frame's own hero animation (route, G-dot, ring) runs.
- **Wrapped chapter change** (650 ms): the outgoing chapter scales 1 → 0.92 and fades (`ease.emphasizedAccelerate`, 250 ms). The incoming chapter rises 24 px → 0 and fades in (`spring.slow`). The background colour morphs between chapter washes (`spring.effects`).
- **Odometer roll** (frame 2): each digit column rolls from 0 to its value with `spring.default`, right to left, stagger 40 ms, with tabular numerals.
- **Clock face** (frame 3): a conic sweep over 1200 ms (`ease.countUp`), with the most-driven hour lifting out (`spring.fast`).
- **Archetype card flip** (frame 4, the share hero): `rotateY` 180° (`spring.bouncy`) and one shimmer. Haptic `success`.
- **Cards fan** (frame 9): three cards fan out ±8° (`spring.default`, stagger 70 ms).
- **RM:** 220 ms cross-fades. The odometer and clock show final values, there are no flips, and auto-advance pauses whenever VoiceOver or TalkBack focuses a frame (F-STY-06.1).

### 3.9 Paywall presentation (S-SUB-02 day 15, S-SUB-04 plans, S-SUB-03 lock sheet)

- **Never interrupts.** The cover appears on app open only, when not in a trip and not after a worse-than-usual Story (F-SUB-08).
- **Present:** iOS uses `.fullScreenCover` (system) or a `spring.sheet` slide-up in the prototype. Android uses a full-screen dialog sliding up with `spring.sheet` plus a scrim fade (`spring.effects`). Duration ≈ `paywallPresent` 520 ms.
- **Day-15 recap:** three mini frames (drives, smoothest drive, routes found), 2.5 s each, using the Story player. Then the plans rise in (stagger 70 ms, `spring.default`), with annual pre-selected: its selection ring draws on over 240 ms.
- **Equal weight (F-SUB-03.2):** "Continue with Free" and the purchase button enter **at the same moment with identical motion**. Never a pulsing CTA, a shimmer on the buy button, a countdown or a delayed close control.
- **Plan selection:** the selection ring slides between plan cards (`matchedGeometryEffect` / `sharedBounds`, `spring.fast`). Haptic `selection`.
- **Contextual lock (S-SUB-03):** the user's own content blurs to 14 px over 320 ms (`spring.effects`), and the lock sheet rises at the medium detent.
- **Welcome to Pro (S-SUB-05):** the only time the Loop **closes**. The G-dot travels across the gap and the ring completes (700 ms, `spring.slow`), holds for 400 ms, then reopens as the dot moves ahead again. Haptic `success`.
- **Dismiss:** a swipe down or "Not now" reverses the presentation with `spring.effects`.
- **RM:** cross-fades, and the Loop appears closed, then open, with no travel.

### 3.10 Shell and navigation motion

| Pattern | iOS | Android |
|---|---|---|
| Push | System push (prototype: incoming `translateX 100% → 0`, the previous page drifts −30% and dims, `spring.default`) | M3 **shared axis X** (incoming +30 dp → 0 and fade, outgoing → −30 dp, `defaultSpatialSpec()`) |
| Card → detail (trip card, route card, story card) | **Zoom transition:** `.navigationTransition(.zoom(sourceID:in:))` + `.matchedTransitionSource` | **Container transform** (`sharedBounds`, `slowSpatialSpec()`) |
| Back | Interactive edge swipe (system) | **Predictive back:** the page scales to 0.9 with the gesture, corners grow to 32 dp and the edge shifts 8 dp; on release it commits (`defaultSpatialSpec()`) or springs back |
| Tab bar on scroll | `.tabBarMinimizeBehavior(.onScrollDown)`: the floating glass bar shrinks to a compact capsule and the bottom accessory folds inline (`spring.default`) | The navigation bar stays. The floating toolbar slides out on scroll down (150 ms, `ease.emphasizedAccelerate`) and back in on scroll up (`fastSpatialSpec()`) |
| Top bar | Large title collapses to inline (system, scroll-linked) | `LargeFlexibleTopAppBar` collapses, linked to scroll (no spring: it follows the finger) |
| Sheets | Detents snap with the system spring; glass background | `ModalBottomSheet` with drag handle; `spring.sheet` |
| Press | Glass "interactive" highlight, scale 0.97 (`spring.fast`) | Ripple plus **shape morph** (round → `buttonPressed` radius, `fastSpatialSpec()`) |
| Loading | The Loop: trail redraws and the dot orbits (1.6 s/rev). Skeletons breathe 0.55 ↔ 1 (1.2 s) | M3E `LoadingIndicator` (7-shape morph). Skeletons breathe 0.55 ↔ 1 |
| Toggle and chips | `spring.fast`, plus `selection` haptic | `fastSpatialSpec()`, `ToggleOn`/`ToggleOff` haptic |

## 4. 120 Hz considerations

- **Springs are time-based** (dt-integrated), so they look identical at 60, 90 and 120 Hz. Never step animations "per frame".
- Hot paths (the replay marker, G-dot follower, scrubber, ring count) render from the display clock: iOS `CADisplayLink` with `preferredFrameRateRange = CAFrameRateRange(minimum: 80, maximum: 120, preferred: 120)` (ProMotion needs `CADisableMinimumFrameDurationOnPhone = YES` in Info.plist); Android `withFrameNanos` / `Choreographer`, with `Surface.setFrameRate(120f)` for the replay map surface where supported.
- **Frame budget: 8.3 ms.** Animate only `transform`, `opacity` and shader uniforms. Pre-project the polyline into screen space once per camera change, not once per frame. The map marker is an annotation layer, not a re-rendered overlay.
- **Drop to 60 Hz** in Low Power Mode or Battery Saver (`ProcessInfo.isLowPowerModeEnabled` / `PowerManager.isPowerSaveMode`) and on thermal state ≥ serious. F-RPL-01.4: ≤ 5% dropped frames at 60 Hz, 120 Hz where supported.
- **In-drive surfaces never animate continuously** (≤ 1 content update per second), whatever the refresh rate.
- Web prototypes use `requestAnimationFrame` with real dt (`DP_MOTION`), so they behave correctly on 120 Hz MacBook displays.

## 5. Reduced motion

`@media (prefers-reduced-motion: reduce)` or `[data-reduced-motion="true"]` (the prototype toggle) sets `--dp-motion: 0`. Natively: iOS `@Environment(\.accessibilityReduceMotion)`; Android `Settings.Global.ANIMATOR_DURATION_SCALE == 0` or the "Remove animations" setting.

| Full motion | Reduced motion |
|---|---|
| Route draw-on | The complete route fades in (220 ms) |
| Ring sweep and count-up | Full ring and final number; the chip fades in |
| G-dot replay | Static G-G plot with trail (F-STY-06.2) |
| Pin pop and pulse | Pins appear in place; the pass gets a 600 ms outline highlight |
| Story / Wrapped slides, flips, odometer | 220 ms cross-fades; final values |
| Trip-start expansion | 220 ms cross-fade |
| Live pulse | Static dot |
| Zoom / container transform | Cross-fade |
| Parallax, tilt, shimmer, shape morph | Removed. The Android badge stays in its resting shape |
| Haptics | **Kept** (they are not motion), except the ring `tick`s, which collapse to one `tap` |

## 6. Haptics map

Shared vocabulary → native (`tokens.json → haptics`). In the prototypes, `DP_MOTION.haptic(name)` dispatches a `dp:haptic` event that the annotations overlay shows as a toast.

| Moment | Haptic | iOS | Android |
|---|---|---|---|
| Start drive (release) | `confirm` | `.sensoryFeedback(.impact(weight: .medium))` | `HapticFeedbackType.Confirm` |
| Stop / "I'm a passenger" confirm (the only haptic allowed during a trip) | `confirm` | same | same |
| Ring count-up, every 10 points | `tick` | `.impact(weight: .light, intensity: 0.5)` | `SegmentFrequentTick` |
| Ring lands | `tap` | `.impact(weight: .light)` | `ContextClick` |
| New route record | `doubleSuccess` | `.success`, then `.impact(.light)` at +120 ms | `Confirm`, then `SegmentTick` at +120 ms |
| Archetype flip, share card ready, Welcome to Pro, purchase done | `success` | `.success` | `Confirm` |
| Event pin tap, contributor row tap | `tap` | `.impact(weight: .light)` | `ContextClick` |
| Scrubber snaps to an event tick, sheet detent snap, segmented / button-group change, plan selection | `selection` | `.selection` | `SegmentTick` |
| Toggle on / off | `toggleOn` / `toggleOff` | `.selection` | `ToggleOn` / `ToggleOff` |
| Long-press for provenance | `longPress` | `.impact(weight: .medium)` | `LongPress` |
| Interactive dismiss commits (Story swipe down) | `gestureEnd` | `.impact(flexibility: .soft)` | `GestureEnd` |
| Health Check red item found, purchase failed | `warning` / `error` | `.warning` / `.error` | `Reject` |
| Typed-confirm delete ("DELETE") | `warning` on the destructive button | `.warning` | `Reject` |

**Never:** haptics during an active driver trip (besides the Stop/Passenger confirm), when the replay marker passes a pin (F-RPL-03.2), on max-speed pins, on notifications, or on a worse-than-usual score.
