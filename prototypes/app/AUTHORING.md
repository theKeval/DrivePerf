# Authoring screens for the DrivePerf prototype engine

**Jira:** DRIVE-32 (v1 prototypes). **Read this file, then the design docs** in `docs/design/system/`
(`brand.md`, `components.md`, `motion.md`, `accessibility.md`, `screen-map.md`, `fixtures.md`) and the PRD §4 row
for each screen you build. iOS and Android share one UX, so **you write each screen once**. The engine renders it as
iOS 26 Liquid Glass or Android 16 Material 3 Expressive from `data-platform`.

The exemplars set the quality bar. Copy their patterns:
- `screens/wp-b.js`: **S-NAV-01** (shell) and **S-HOME-01** (Drives tab, 11 states)
- `screens/wp-c.js`: **S-TRP-01** (trip detail, 9 states)

Run them: `prototypes/ios/index.html#S-HOME-01/newStoryCard`, `prototypes/android/index.html#S-TRP-01/Lock`.

---

## 1. Files and ownership

```
prototypes/
  shared/            tokens.css · tokens.js (DP_TOKENS) · data.js (DP_DATA) · icons.js (DP_ICONS) · motion.js (DP_MOTION)   ← read-only
  app/core/          manifest.js (generated) · registry.js · gate.js · ui.js (DP_UI) · shell.js · router.js · app.js · app.css ← read-only
  app/screens/       wp-a.js … wp-f.js + wp-a.css … wp-f.css                                                          ← YOU edit only your WP pair
  ios/index.html · android/index.html   (already load every file above, in order)
  tools/             gen-manifest.mjs · check.mjs · export.mjs · cdp.mjs
```

- **Edit only your own `wp-x.js` and `wp-x.css`.** Do not edit `core/`, `shared/` or another WP's file. In wp-b.js and wp-c.js the exemplars sit between `EXEMPLAR` banner comments. The WP owner may extend them (for example by adding states) but must keep them working.
- If you need a helper that `DP_UI` lacks, write it as a local function inside your file's IIFE. Build it from the `ui-*` classes and tokens so it inherits both platforms. Mention it in your report, because it could become a core primitive later.
- Missing tokens: add a scoped custom property in your `wp-x.css` and note it in your report. Never edit `tokens.css`.
- No build step, no npm, no network. Write classic scripts (no `import`), because they must work from `file://`. Don't `fetch()` local files.
- The screen IDs, names, PRD states, platforms, WP, presentation, entry points and Jira stories all come from `core/manifest.js`. The generator reads PRD §4, screen-map §4–§5 and jira-map. Never hand-edit it; re-run `node prototypes/tools/gen-manifest.mjs`.
- An unregistered ID still renders, as a tidy "Not built yet: S-XXX-NN · name" placeholder taken from the manifest. A placeholder still gets the shell chrome and (for sheets) its parent.

## 2. Registering a screen: `DP.screen(id, def)`

```js
(function () {
  const { screen } = window.DP;
  screen('S-RTE-02', {
    name: 'Route detail',                     // default: manifest name
    wp: 'D',
    platforms: ['ios', 'android'],            // default: manifest (iOS-only / Android-only rows already set)
    presentation: 'push',                     // 'root'|'push'|'sheet'|'full'|'dialog'|'sys'|'live', or {ios:'sheet', android:'push'}
    tab: 'routes',                            // 'drives'|'routes'|'insights'|'garage'|'profile'|null (default: manifest)
    over: 'S-RTE-01',                         // sheets/dialogs only: parent rendered underneath on a deep link
                                              //   string 'S-ID[/state][?k=v]' | {id,state,params} | (ctx) => either
                                              //   default: first manifest entry point that exists on this platform
    detent: 'medium',                         // sheets: 'auto' (fit content, default) | 'small' (36%) | 'medium' (54%) | 'large'
                                              //   (XL text forces 'large' unless 'auto') · or (ctx) => detent
    dismissible: true,                        // sheets/dialogs: scrim tap + drag-down close (false = must act)
    states: [ { id: 'default', label: 'Def' }, { id: 'newRecord', label: 'new record' }, 'fewRuns', { id: 'Lock', label: 'Lock (inactive route on Free)' } ],
                                              // MUST cover every PRD state in the manifest (check.mjs fails otherwise).
                                              // Strings are allowed. Extra states are fine (e.g. 'alwaysLost').
    stateAliases: { record: 'newRecord' },    // optional extra names → state id
    stateParams: {                            // fixture params applied per state (below explicit deep-link params)
      default: { route: 'r_commute' },
      fewRuns: { route: 'r_gym' },
      Lock:    { route: 'r_gym', ent: 'free' },
    },
    chrome: (ctx) => ({ accessory: 'recording' }),   // optional overrides, see §6. Object or function.
    statusBar: 'light',                       // optional: 'light' (white status-bar text) | 'dark' | (ctx) => …
    initialScroll: (ctx) => 0,                // optional: scrollTop after render (e.g. a "minimised on scroll" state)
    backTo: 'S-RTE-01',                       // optional: where Back goes when the screen was deep-linked with no history
    variants: ['xl'],                         // default: manifest (xl/rm/screenReader); 'xl' makes export render --xl.png
    render(ctx) { return ctx.ui.page(/* … */); }, // REQUIRED. Returns an HTML string (or a Node). Must be pure: no side effects.
    mount(el, ctx) { /* motion, listeners */ return () => {/* cleanup */}; },  // optional
    actions: {                                // optional: handlers for data-action="name" (see §4)
      pick: (ctx, arg, el, ev) => { ctx.store.sel = arg; ctx.update(); },
    },
    spec: { /* REQUIRED for export, see §9 */ },
  });
})();
```

**State naming** (screen-map §2.3): the PRD "States to prototype" values in camelCase. `Def` becomes `default`. The common
abbreviations keep their capitals: `Lock`, `Part`, `Err`, `Load`, `Empty`, `Deny`, `Prev`, `Off`. `XL`, `RM` and `Dark` are not states; they are
the global params `text=xl`, `rm=1` and `theme=light`. A leading digit gets an `s` prefix (`s3rdRoute`). The manifest already
lists the exact IDs for your screens (see the catalog panel or `DP.meta('S-ID').states`). An unknown state falls back to `default`.

## 3. The `ctx` object (render, mount and actions all receive it)

| Field | Meaning |
|---|---|
| `id`, `state`, `name` | Screen ID, resolved state ID, display name |
| `params` | Merged params: globals < `stateParams[state]` < explicit deep-link/nav params. `params.trip`, `.route`, `.car`, `.event`, `.ent`, `.live`, `.health`, `.capture`, `.theme` … |
| `platform`, `ios`, `android` | `'ios'`/`'android'` and booleans. **Prefer CSS** (`[data-platform="android"] …`) for cosmetic differences; branch in JS only for real structural or copy differences |
| `theme` (`'dark'`/`'light'`), `xl`, `textSize`, `rm`, `shot` | Display settings. `shot` = export screenshot mode (no animations) |
| `ent`, `entState`, `isPro` | Entitlement key (`pro`, `proMonthly`, `preview`, `previewEnding`, `rideAlong`, `free`), its object from `DP_DATA.entitlement.states`, and `isPro` |
| `pres`, `tab`, `tabRoot` | Resolved presentation, tab and that tab's root screen ID |
| `capture` | `{mode: ready|manual|paused|attention|denied|recording, live, health, attention:boolean, lastCheckIn}` from the params (§6) |
| `data` | Fixture helpers (§8) |
| `D` | `window.DP_DATA` (raw fixtures) |
| `ui` | `window.DP_UI` (§5) · `icons` = `DP_ICONS` · `motion` = `DP_MOTION` |
| `size` | `{w, h, top, bottom}` device CSS size + safe areas (iOS 402×874, 62/34 · Android 412×915, 40/24) |
| `store` | Per-entry scratch object that persists while the entry is on the stack (selections, toggles) |
| `nav(target, params?, opts?)` | Navigate through the D1 gate. `target` = `'S-ID'` or `'S-ID/state'`. Params may include globals (`{ent:'free'}`). `opts.from` = Element for the zoom / container-transform source |
| `back()` | Pop (sheet dismiss, push back, or the fallback parent when deep-linked) |
| `goTab(name)` | Switch tab (`'drives'|'routes'|'insights'|'garage'`) |
| `openSheet(id, state?, params?)` | Same as `nav` for sheets/dialogs |
| `setState(state)` | Re-render this screen in another state (cross-fade) |
| `update()` | Re-render this screen in place, keeping its scroll position (after changing `ctx.store`) |
| `setGlobal(key, value)` | Change a global (`theme`, `ent`, `live`, `capture`, `health`…) and re-render every layer. `''` clears it |
| `toast(text, {icon, action, kind, duration})` | iOS glass capsule at the top / Android snackbar at the bottom |
| `haptic(name)` | `DP_MOTION.haptic` (motion.md §6 names only). The prototype shows it as an annotation; it is suppressed while driving |

`DP.session.pending` holds the target the D1 gate held back (S-DRV-05 opens it on "stationary"). `DP.session.gateReason` says why.

## 4. Interactions: data attributes (prefer them to listeners)

Every `DP_UI` function accepts these options and turns them into attributes. In raw HTML, write the attributes directly.
The router handles clicks, Enter and Space for you.

| Option → attribute | Effect |
|---|---|
| `nav: 'S-ID[/state]'` → `data-nav` | `ctx.nav(...)`. Add `params: {trip: t.id}` → `data-params="trip=…"` |
| `zoom: true` → `data-zoom` | The element is the source for the iOS zoom / Android container transform |
| `back: true` → `data-back` | `ctx.back()` |
| `tab: 'routes'` → `data-tab` | Switch tab |
| `state: 'empty'` → `data-set-state` | `ctx.setState('empty')` |
| `action: 'name', arg` → `data-action`, `data-arg` | Calls `def.actions.name(ctx, arg, el, ev)`. Return `false` to stop the nav/state attributes on the same element |
| `toast: 'Saved'` → `data-toast` | Shows a toast |
| `haptic: 'tap'` → `data-haptic` | Fires a haptic annotation |
| `label: '…'` → `aria-label` | Accessible name (always set it for icon-only controls) |
| `disabled: true` → `aria-disabled` | Ignored by the router, dimmed by CSS |
| `data-dismiss` | Closes the overlay (the scrims already have it) |
| `menu: 'id'` (on `iconButton`) → `data-menu` | Toggles `ui.menu('id', items)` |

Built-in behaviour: sheet drag-to-dismiss (grabber and header), scrim tap, the "…" menu, the accessory **Start** (begins a
manual trip with `live=manual` and expands into S-DRV-01), long-press handled by your own `mount` (see S-TRP-01's
provenance toast), and large-title collapse plus tab-bar minimise on scroll (automatic for any `page()`).

## 5. `DP_UI` reference (`ctx.ui`). Every function returns an HTML string

Layout and bars
- `page(content, {large, nav, fixed, overlay, bg, cls})`: **the root of every root/push/full screen.** It scrolls under the status bar and chrome and ends with the right bottom space.
  - `large: {title, subtitle, trailing:[html]}` is a tab-root large title (iOS 34 bold, Android LargeFlexibleTopAppBar). It collapses into a compact bar on scroll.
  - `nav: {title, subtitle, trailing:[html], back:false, close:true, transparent, medium:false}` is a fixed detail bar. On iOS: inline title + subtitle, glass back. On Android: MediumFlexibleTopAppBar, with the title block placed in the content.
  - `fixed`: HTML outside the scroller (floating controls, `menu`). `overlay`: HTML above everything (e.g. `sysPermission`).
- `largeTitle(title, o)`, `navBar(o)`, `mediumTitle(title, sub)`: the pieces `page()` uses.
- `pad(html)` (16 margins) · `stack([html], gap)` · `spacer(h)` · `section(title, html, {style:'title'|'caption', meta, action:{text, nav…}, footer})` · `card(html, {hero, tint:'car-carmine'|css, pad:false, nav…, cls, style})`

Controls
- `button(label, {kind:'filled'|'tonal'|'glass'|'text'|'outline'|'destructive', size:'l'|'m'|'s', icon, trailingIcon, block, disabled, …act})`. Filled = Glide prominent (iOS glassProminent / M3 filled with shape morph).
- `actionButton(label, {icon, primary, locked, disabled, …act})`: the icon-over-label action tiles. Wrap them in `<div class="ui-actrow" style="--n:4">`.
- `iconButton(name, {glass, tonal, svg, size, badge, menu, …act})` · `backButton()` · `menu(id, [{text, icon, destructive, trailing, …act} | '-'])`
- `chip(label, {kind:'up'|'down'|'same'|'record'|'q'|'outline'|'filter'|'plain'|'pro', icon, html, selected, …act})`
- `deltaChip(smoothness|number, {short, record:false})`: ▲ Glide better / ▼ neutral lower (never red) / ● same / gold record / calibrating dots
- `segmented([labels | {id,label,icon,state}], {selected, action})`: iOS glass segmented / Android connected button group. The click calls `actions[action](ctx, id)`.
- `toggle(on, {action, arg, label})` · `row({…trailing:'toggle', on})` · `textField({label, value, placeholder, helper, error, focused, readonly})` · `stepper(value, {unit, action})` · `radios(options, selectedId, {action})`
- `list([row(...)])` + `row({icon, iconTone:'warn'|'critical'|'neutral', lead, title, titleHtml, sub, subHtml, value, trailing:'chevron'|'toggle'|'lock'|html, destructive, …act})`. iOS gets an inset-grouped list; Android gets M3E segmented ListItems.
- `avatar({initial, badge})` (opens S-ACC-01) · `previewBadge(ent, {day})` (opens S-SUB-01) · `proBadge()` · `carChip(vehicle | 'passenger' | null)` · `carDot(vehicle)`

Data display
- `num(value, unit, {est})`: DP Numerals with the unit at 50%. **Every number goes through `num`, `stat` or the ring.**
- `stat({label, value, unit, unitSpoken, prov:'observed'|'derived'|'estimated', sub, est, unavailable})` · `stats([..], {cols})`. Speed tiles use the same neutral style and are never first.
- `ring(score, size, {base, rec, recNew, stroke, label, calibrating, unscorable, loading, aria, shape:false})`: the Smoothness ring (Android adds the Cookie9Sided container). Animate it with `ui.anim.rings(el)`.
- `scoreBadge(trip)` · `tripRow(trip, {ent, isNew, cars, nav})`: the trip card row (zooms into S-TRP-01). Put rows in `<div class="ui-card is-flush">`.
- `qualityBadge(badge, {size:'compact'|'full'|'glyph', detail, text, glass, …act})` · `qualityDetail(trip)` · `provenance(kind)`
- `glyph(points, size, {color, width})`: the share-safe route glyph
- `map(trip, {w, h, fluid, pad, insetTop, insetBottom, pins, stops, maxPin, marker:t, others, est, startEnd, select:eventId, pinAction, pinSize, dim, attribution, radius, overlay, nav, params, label, cls})`: the plain-canvas map from `trip.track`. It draws the estimated start dashed, gaps dotted and excluded spans dimmed, with pins by shape, and always shows **"© OpenStreetMap contributors"** (keep `attribution` on).
- `timeline(trip, w, {cursor:0..1, xl, label})` · `gg(trip, size, {dot:t|false, label})` · `evidenceTrace(event, w, h)` · `scrubber(trip, w, frac)` · `sparkline(values, {w,h,color,zero,peak,area,dash,label})`
- `eventRow(event, {tripId, time})` · `tipCard({text, event, tripId, label})`

States and feedback
- `banner({kind:'info'|'attention'|'critical'|'passenger'|'positive', icon, title, text, html, action:{text, …act}})`
- `empty({art|icon, title, text, action:{text,…}, secondary:{text,…}})` · `artMerge(size)` · `skeleton('lines'|'tripRow'|'card'|'stats'|'block', n)` · `error({title, text, actionText, action})` · `loader({size, label})` (iOS Loop / Android M3E shape loader)
- `lockPanel(innerHtml, {text, cta, nav:'S-SUB-03/variant', params, compact})`: the user's **own** content blurred 14 px + lock + value line + See plans

Overlays (the engine wraps sheet and dialog presentations; you render the inside)
- **Sheet screens** return content only. The engine adds the scrim, the glass (iOS) or ModalBottomSheet (Android) container, the grabber and drag-to-dismiss. Start with `sheetHeader(title, {sub, icon, leading, close:false})`.
- **Dialog screens** return `dialog({title, text, icon, body, stacked, actions:[{text, kind:'default'|'cancel'|'destructive'|'primary', …act}]})`: an iOS alert or an M3 dialog. The engine centres it over the dimmed parent.
- **System permission mocks** (non-interactive, PRD §4 note): `sysPermission('location'|'always'|'motion'|'notifications', {title, text, buttons, highlight:index})`. Pass it as `page(..., {overlay: ui.sysPermission('location', {highlight: 1})})` in the primer's "system prompt mock" state. `sysSettings({title, back, groups:[{header, rows:[{label, sub, value, radio, on, toggle, check, hi}], footer}]})` gives an OS Settings page (S-ONB-09, S-SET-05 deep links).

OS surfaces (presentation `'live'`, never gated): `lockScreen(html, {time, date})`, `homeScreen(widgetsHtml, {icons})`,
`notification({title, body, time, channel, actions, chip, held, expanded})`, `liveActivity(live, {mode:'recording'|'gps'|'ending'|'cap'})`,
`island(live, 'compact'|'minimal'|'expanded'|'ending')` (set `chrome: {island:false}` so the hardware island hides),
`widget(html, {kind:'small'|'medium'|'circular'|'rectangular'|'glance', label})`, `qsTile({state:'idle'|'recording'|'unavailable', label, sub})`,
`shade(notificationsHtml, {tile})` (Android notification shade). `live = ctx.data.live('manual'|'auto'|'gpsSearching'|'lowBattery'|'passenger'|'cap8h')`.
Live surfaces show **only elapsed time, distance and car** (SAF-02).

Misc: `icon(name, size, label)` covers DP_ICONS plus `chevronLeft arrowBack moreVert search minus up down clock pin warn refresh sparkle`. Also `esc(text)` (**escape all fixture text you interpolate**), `act(opts)`, `cls(...)`, `qs(obj)`, `cookie(n, R, cx, cy, amp)` (M3E cookie path), and `TOKENS` (component → token notes used by export; name your `spec.components` after these functions).

## 6. Shell chrome, capture status, status bar

The shell owns the status bar, Dynamic Island / punch-hole, home indicator, tab bar, capture accessory / floating toolbar and
toasts. **Screens never draw them.** It picks the topmost visible `root`/`push` layer and shows:
- **Tab bar** when that screen has a tab other than `profile`. The iOS floating glass bar sits on push screens too; Android shows the NavigationBar.
- **Capture status:** on iOS, the accessory on every tabbed screen. On Android, the floating toolbar on the **Drives tab root only**. Its mode comes from `ctx.capture.mode`, which reads these params: `live=<state>` → `recording`, a `health` preset with a red item → `attention`, `capture=paused|manual|denied`, otherwise `ready`. Taps go to S-HOME-02 (ready), S-DRV-01 (recording), S-SET-05 (attention) and S-ONB-16 (denied); Start begins a trip.
- Override with `chrome: {tabBar, tab, accessory: false|true|'ready'|'manual'|'paused'|'attention'|'denied'|'recording'|'processing', minimised, island:false, statusBar:false, statusChip:'24 min'}`. `statusChip` is the Android Live Update status-bar chip.
- Full, live, sheet and dialog screens cover the chrome. Immersive push screens (Replay, Analysis) should set `chrome: {tabBar: false}`.
- Status-bar text defaults to the theme. Set `statusBar: 'light'` on black or dark-art screens (in-drive, Story, Wrapped, lock screen).
- The avatar dot comes from `ui.avatar({badge: ctx.capture.attention})`, and the Preview badge from `ui.previewBadge(ctx.ent)` when `ent` is preview, previewEnding or rideAlong.

## 7. Routing, globals and the D1 gate

Deep link: `index.html#<S-ID>[/<state>][?trip=…&route=…&car=…&event=…&ent=…&health=…&live=…&moving=1&capture=…&theme=light&text=l|xl&rm=1&rt=1&bare=1]`
- **Globals** persist across navigation: `theme, text, rm, rt, ent, live, health, moving, capture, bare, shot, hap`. Everything else (`trip`, `route`, `car`, `event`, your own keys) belongs to the entry.
- `bare=1` renders only the device at its exact CSS size, edge to edge (export screenshots). `shot=1` turns off all animations. You can put both in `?…` or `#…`.
- **Back stack:** push, sheet, full and dialog layers stack, and the browser Back button works. Opening a deep link to a sheet builds `over` (or the first manifest entry point) underneath it. A deep-linked push screen backs out to `backTo` or its tab root.
- **Transitions** (motion.md §3.10) are automatic. iOS push slides with the previous page drifting −30%; Android uses shared axis X. A `zoom` source gives the iOS zoom / Android container transform. Sheets use spring.sheet, and an iOS large detent recedes the parent. Full covers slide up (iOS) or fade-rise (Android). Dialogs scale in. Tabs cross-fade (iOS) or fade through (Android). RM reduces everything to 220 ms cross-fades.
- **D1 gate** (`core/gate.js`, screen-map §2.2) runs on every `nav`, deep link and catalog link:
  - With `live=<driver state>`, only S-DRV-01/02/03/05 are reachable; anything else lands on S-DRV-01 (the target goes to `DP.session.pending`).
  - With `moving=1`, Story, Replay, Analysis, Recap, Wrapped, Share and Paywall land on S-DRV-05.
  - With `live=passenger`, content is allowed and the shell shows the recording banner.
  - `live` surfaces are never gated.
  - To end a trip, call `ctx.nav('S-DRV-04', {live: ''})` (clearing `live` unlocks the app).

## 8. Fixtures: `ctx.data` (all copy and numbers come from `DP_DATA`; see fixtures.md)

`trip(id)`, `trips(filter?)` (newest first), `newestTrip(filter?)`, `newestScored()`, `route(id)`, `routes()`, `vehicle(id)`, `cars()`,
`recap(id?)`, `latestCompleteRecap()`, `entitlement(ent)`, `health(platform, preset)` → `{summary, promise, lastCheckIn, items:[{id,label,fix,status,detail}]}`,
`live(state)`, `isLocked(trip, ent)`, `weekSummary(asOf, weekStartIso?)`, and the formatters `km(n)`, `min(seconds)` ("33 min", "1 h 12 min"),
`clock(seconds)` ("24:18"), `date(iso)` ("5 Nov"), `time(iso)` ("5:45 pm"), `weekday(iso)`, `signed(n)` ("+3", "−2"), `ago(iso)`. `today` = Fri 6 Nov 2026 19:30.
Rules:
- Use `partOfDay` and `label` on cards and Stories, never timestamps.
- Share cards, live surfaces and notifications may only use the fields fixtures.md §8 allows.
- If you derive a number, say so in `spec.data`.
- Never invent a value that contradicts the fixtures.
- Copy: sentence case, no exclamation marks, a real minus "−", "vs your usual …" comparisons, no speed or G on social or live surfaces, no streaks.

## 9. `spec`: what export turns into `design-spec.md` (the developer's source of truth)

Write it as if a native developer will build from it alone. All fields are strings or arrays of strings.

```js
spec: {
  purpose: 'One sentence: why the screen exists (PRD story).',
  layout: ['Top to bottom: every region with sizes, radii and tokens…'],
  components: ['page', 'navBar', 'ring', 'stats', …],          // DP_UI function names → token table in the spec
  interactions: ['Element → target screen/state (gate notes) …'],
  motion: ['What moves, spring/duration token, haptic, RM fallback …'],
  a11y: ['VoiceOver/TalkBack labels (exact strings), focus order, XL reflow, colour-independent meaning …'],
  platformDiffs: { ios: '…', android: '…' },
  analytics: ['event_name {props}'],
  copy: { 'Key': 'Exact text' },
  freePro: 'Free vs Pro vs Preview behaviour, lock variants used.',
  data: 'Which fixtures each state uses; anything derived.',
  icons: ['drives', 'lock'],                                    // DP_ICONS / ui.icon names → assets/icon-*.svg
  assets: ['logo-a-loop.svg'],                                  // files copied from docs/design/system/assets
}
```
`check.mjs` warns when `purpose, layout, components, interactions, motion, a11y, platformDiffs, analytics, copy` are missing.

## 10. Styling

- Put screen CSS in your `wp-x.css`, scoped to the layer attribute (`[data-screen="S-RTE-02"] .rte-hero { … }`). Use a short class prefix per screen. The `data-state` attribute is on the layer too.
- Use tokens only: `var(--dp-surface)`, `--dp-text-2`, `--dp-accent`, `--dp-radius-card`, `--md-sys-color-*` (Android), `--dp-data-*`. The engine adds:
  - `--app-bg`, `--card-bg`, `--tile-bg`, `--tile-bg-in`
  - `--safe-top`, `--safe-bottom`, `--chrome-b` (bottom clearance)
  - text sizes that scale with XL: `--fs-9 … --fs-20`; numerals: `--ns-13 … --ns-48`. Hand-written font sizes must use these or `calc(Npx * var(--dp-text-scale))`, never a bare `px`.
- Platform cosmetics: `[data-platform="android"] .x { … }`. Theme: `[data-theme="light"] .x`. XL: `[data-text-size="xl"] .x` (reflow, never truncate). RM: `[data-reduced-motion="true"]`.
- Grids that hold text need `grid-template-columns: minmax(0, 1fr)` (otherwise one long chip overflows the page). `check.mjs` warns on horizontal overflow.
- Glass is the `ui-glassy` class. It becomes an M3 tonal surface on Android automatically.
- Touch targets are ≥ 44 pt (iOS) / 48 dp (Android), and ≥ 64 in-drive. The S-DRV-01 buttons are 96 tall.

## 11. Motion

- In `mount`, use the engine helpers. They respect RM and the export's `shot` mode:
  - `ui.anim.rings(el)` (ring reveal + haptics)
  - `ui.anim.maps(el)` (route draw-on + pin pop)
  - `ui.anim.rise(el, selector, {stagger, delay})` (8 px rise)
  - `ui.anim.pop(el, delay)`
  - `ui.anim.countUp(el, to, opts)`
- Or use `DP_MOTION.animate(el, keyframes, {preset:'fast'|'default'|'slow'|'effects'|'bouncy'|'sheet'|'pinPop'})`, `.spring`, `.tween`, `.drawOn`, `.follower`.
- Before any custom animation, check `ctx.shot || ctx.motion.reduced(el)` and render the final state instead.
- One hero motion per screen. Nothing animates on in-drive surfaces except the 10 s live-dot pulse.
- Animate only `transform`, `opacity` and SVG stroke properties. No layout properties.

## 12. Test and export

```
open prototypes/ios/index.html#S-RTE-02/newRecord        # catalog: press c. Theme / XL / RM / entitlement / trip-in-progress / Health preset
node prototypes/tools/check.mjs --wp D                    # must print "No errors." before you hand off
node prototypes/tools/export.mjs --wp D                   # docs/design/<story>/<platform>/{screens/*.png, mockup.html, mockup.png, design-spec.md, assets/}
node prototypes/tools/export.mjs --screens S-RTE-02 --platform android --no-png   # fast: spec + contact sheet HTML only
```
Look at your PNGs (both platforms, dark and light, and XL where relevant) and compare them with
`docs/design/system/preview.html` and the exemplars.
**Definition of done for a screen:**
- Every PRD state is declared and renders without errors.
- Light and dark both work.
- XL reflows.
- RM is defined.
- Copy comes from the fixtures.
- The `spec` is complete.
- The accessibility.md §9 checklist holds.

## 13. Gotchas

- `render` must not touch the DOM or global state; it can run many times (theme toggles, export). Put side effects in `mount` or `actions`.
- Always `ui.esc()` fixture strings you interpolate into HTML. The `DP_UI` helpers escape their own arguments.
- Give each `menu` id and SVG gradient id a unique name. Use `ui.nid('x')` for generated ids.
- Platform-only screens (S-LIVE-01…04 iOS, S-ONB-09/12 and S-LIVE-05…08 Android) are pre-set in the manifest. Don't register them for the other platform.
- Paywalls, share prompts and rating prompts must never appear during a trip or right after a worse-than-usual Story (D12, F-SUB-08). Let the gate handle navigation, and don't auto-open them in `mount`.
