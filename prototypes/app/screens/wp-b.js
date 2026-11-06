/* WP-B · Shell, capture & live surfaces — screen definitions (DRIVE-32). Contract: prototypes/app/AUTHORING.md.
   Register each screen with DP.screen('S-XXX-NN', {...}). Only the WP-B owner edits this file. */
(function () {
  const { screen } = window.DP;

  /* ==========================================================================================================
     EXEMPLAR · S-NAV-01 App shell + S-HOME-01 Home (Drives tab)          (written by the engine author; WP-B owns)
     The quality bar for every screen: fixture-true copy, both platforms, every PRD state, motion with RM, a11y
     labels, and a complete `spec` (export turns it into docs/design/<story>/<platform>/design-spec.md).
     ========================================================================================================== */
  const HOME_STATES = [
    { id: 'default', label: 'Def auto ready' },
    { id: 'manualOnly', label: 'Manual only (auto off)' },
    { id: 'needsAttention', label: 'Needs attention' },
    { id: 'autoPaused', label: 'Auto paused' },
    { id: 'recordingInProgress', label: 'Recording in progress (passenger trip: content allowed, banner live)' },
    { id: 'processingATrip', label: 'Processing a trip' },
    { id: 'newStoryCard', label: 'New Story card' },
    { id: 'interruptedTripRecovered', label: 'Interrupted trip recovered' },
    { id: 'firstRunEmpty', label: 'First run Empty (Preview day 1)' },
    { id: 'prevDayCounter', label: 'Prev day counter' },
    { id: 'Deny', label: 'Deny (location While Using / denied)' },
  ];
  const HOME_PARAMS = {
    manualOnly: { capture: 'manual' },
    needsAttention: { health: 'alwaysLost' },
    autoPaused: { capture: 'paused' },
    newStoryCard: { trip: 't20261105-0742' },
    interruptedTripRecovered: { trip: 't20261013-1742' },
    firstRunEmpty: { ent: 'preview' },
    prevDayCounter: { ent: 'preview' },
    Deny: { capture: 'denied' },
  };

  /** "This week" at a glance (computed from trips; matches recap w2026-11-02 for the default state). */
  function weekCard(ctx, asOf) {
    const { ui, data } = ctx;
    const w = data.weekSummary(asOf);
    const prev = data.D.recaps.find((r) => r.complete && r.weekEnd < w.weekStart.toISOString().slice(0, 10));
    const delta = w.avg != null && prev ? w.avg - prev.avgSmoothness : null;
    const today = new Date(asOf);
    const DOW = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
    const days = DOW.map((l, i) => {
      const d = new Date(w.weekStart); d.setDate(d.getDate() + i);
      const iso = d.toLocaleDateString('en-CA', { timeZone: 'America/Toronto' });
      const n = w.trips.filter((t) => t.start.slice(0, 10) === iso).length;
      const future = d > today, isToday = iso === today.toLocaleDateString('en-CA', { timeZone: 'America/Toronto' });
      return `<span class="hm-day ${future ? 'is-future' : ''} ${isToday ? 'is-today' : ''}"><span class="hm-day-b">${Array.from({ length: Math.min(n, 3) }, () => '<i></i>').join('')}</span><b>${l}</b></span>`;
    }).join('');
    const end = new Date(w.weekStart); end.setDate(end.getDate() + 6);
    const range = `${w.weekStart.getDate()}–${data.date(end.toISOString())}`;
    const dchip = delta == null ? '' : ui.chip('', { kind: delta > 0 ? 'up' : delta < 0 ? 'down' : 'same', html: `<b aria-hidden="true">${delta > 0 ? '▲' : delta < 0 ? '▼' : '●'}</b>${delta === 0 ? 'Level with last week' : `${data.signed(delta)} vs last week`}` });
    const aria = `This week: ${w.drives} drives, ${data.km(w.km)} kilometres, ${data.min(w.durationS)}. Average Smoothness ${w.avg}${delta ? `, ${Math.abs(delta)} ${delta > 0 ? 'up' : 'down'} on last week` : ''}.`;
    return ui.card(`<div class="hm-week" aria-label="${ui.esc(aria)}">
        <div class="hm-week-h"><b>This week</b><span>${range}</span>${ui.icon('chevronRight', 16)}</div>
        <div class="hm-week-b"><div><div class="hm-week-n">${ui.num(w.avg ?? '—')}</div><div class="hm-week-l">Average Smoothness</div><div style="margin-top:8px">${dchip}</div></div><div class="hm-days" aria-hidden="true">${days}</div></div>
        <div class="hm-week-f"><span>${ui.num(w.drives)} drives</span><i>·</i><span>${ui.num(data.km(w.km), 'km')}</span><i>·</i><span>${ui.esc(data.min(w.durationS))}</span></div></div>`, { tab: 'insights', label: aria, cls: 'hm-weekcard' });
  }

  /** New Story hero card (components.md §12 "new Story" large variant + §3 ring). */
  function storyCard(ctx, t) {
    const { ui, data } = ctx, s = t.smoothness, v = data.vehicle(t.vehicleId);
    const rec = s && s.record && s.record.isNew ? s.record.previous.score : null;
    const chips = s ? [s.record && s.record.isNew ? ui.chip('New route record', { kind: 'record', icon: ctx.icons.svg('rosette', { size: 14 }) }) : '', ui.deltaChip(s, { record: false, short: true })].join('') : ui.qualityBadge(t.quality.badge);
    const aria = `Drive decoded. ${t.label.replace('→', 'to')}, ${t.partOfDay}. Smoothness ${s ? s.score : 'not scored'}${s && s.delta ? `, ${Math.abs(s.delta)} above your usual` : ''}${rec ? '. New route record' : ''}. Watch Story.`;
    return ui.card(`<div class="hm-story">
        <div class="hm-story-t"><span class="hm-kick"><i></i>Drive decoded</span><b>${ui.esc(t.label)}</b><span class="hm-meta">${ui.esc(t.partOfDay)} · ${v ? ui.esc(v.nickname) : ''}</span><span class="hm-meta">${ui.num(t.distanceKm, 'km')} · ${ui.num(Math.round(t.durationS / 60), 'min')}</span>
          <div class="hm-chips">${chips}</div>
          ${ui.button('Watch Story', { kind: 'filled', size: 's', icon: 'story', nav: 'S-STY-01', params: { trip: t.id }, label: 'Watch Story', haptic: 'tap' })}</div>
        ${s ? ui.ring(s.score, 116, { base: s.baseline, rec, recNew: !!rec, stroke: 10, aria: `Smoothness ${s.score}` }) : ui.ring(null, 116, { unscorable: true })}
      </div>`, { hero: true, tint: `car-${v ? v.colour : 'glide'}`, nav: 'S-STY-01', params: { trip: t.id }, zoom: true, label: aria, cls: 'hm-storycard' });
  }

  function homeBody(ctx) {
    const { ui, data, state, params } = ctx;
    const blocks = [];
    const D = data.D;
    // ---- state banners (most important first) ----
    if (state === 'needsAttention') {
      const h = data.health(ctx.platform, params.health || 'alwaysLost');
      const bad = h.items.find((i) => i.status === 'red');
      blocks.push(ui.banner({ kind: 'critical', title: h.summary.replace(/\.$/, ''), text: (bad && bad.detail) || 'Automatic trips are paused.', action: { text: 'Fix', nav: 'S-SET-05', params: { health: params.health || 'alwaysLost' } } }));
    }
    if (state === 'autoPaused') blocks.push(ui.banner({ kind: 'info', icon: 'pause', title: 'Auto-detect paused until 8:00 tomorrow', text: 'No drives will start on their own. You can still tap Start.', action: { text: 'Resume', action: 'resume' } }));
    if (state === 'manualOnly') blocks.push(ui.banner({ kind: 'info', icon: 'drives', title: 'Manual mode', text: 'Automatic trips are off. Tap Start when you set off.', action: { text: 'Turn on', nav: 'S-SET-03' } }));
    if (state === 'Deny') blocks.push(ui.banner({ kind: 'attention', icon: 'gps', title: 'Automatic trips are off', text: 'Location is set to “While Using”. You can still start drives yourself.', action: { text: 'Fix', nav: 'S-ONB-16' } }));
    if (state === 'interruptedTripRecovered') {
      const t = data.trip(params.trip) || data.trip('t20261013-1742');
      const why = ctx.ios ? t.interrupted.reason : 'Android stopped DrivePerf in the background';
      blocks.push(ui.banner({ kind: 'attention', icon: 'shield', title: 'We saved your drive', text: `${t.partOfDay} · ${t.label}. ${why}. Everything before then is saved.`, action: { text: 'View', nav: 'S-TRP-01/interrupted', params: { trip: t.id } } }));
    }
    if (state === 'processingATrip') {
      const l = data.live('manual');
      blocks.push(ui.card(`<div class="hm-proc">${ui.loader({ size: 40, label: 'Decoding your drive' })}<div class="hm-proc-t"><b>Decoding your drive…</b><span>${ui.num(Math.round(l.elapsedS / 60), 'min')} · ${ui.num(l.distanceKm, 'km')} · building the replay</span>${ui.skeleton('lines', 2)}</div></div>`, { label: 'Decoding your drive. Building the replay.' }));
    }
    // ---- first run: empty state ----
    if (state === 'firstRunEmpty') {
      blocks.push(ui.card(ui.empty({ art: `<div class="hm-empty-art">${ctx.icons.loop({ size: 76, color: 'var(--dp-accent)', dot: 'var(--dp-text)' })}</div>`, title: 'Your drives will appear here.', text: 'Just drive. DrivePerf notices when you set off and decodes each drive when you park.', secondary: { text: 'How capture works', nav: 'S-ONB-05' } }), { hero: true }));
      blocks.push(ui.section('Start faster', ui.list([
        ui.row({ icon: ctx.ios ? 'gear' : 'bolt', title: ctx.ios ? 'Add “Start drive” to Control Center' : 'Add the Quick Settings tile', sub: 'One tap before you set off', nav: ctx.ios ? 'S-LIVE-03' : 'S-LIVE-07' }),
        ui.row({ icon: 'story', title: 'Add the DrivePerf widget', sub: 'Your last drive at a glance', nav: ctx.ios ? 'S-LIVE-04' : 'S-LIVE-08' }),
      ]), { style: 'caption' }));
      return blocks;
    }
    // ---- new Story card ----
    let asOf = data.today, storyTrip = null;
    if (state === 'newStoryCard') { storyTrip = data.trip(params.trip) || data.trip('t20261105-0742'); asOf = new Date(storyTrip.end); blocks.push(storyCard(ctx, storyTrip)); }
    // ---- this week ----
    blocks.push(weekCard(ctx, asOf));
    // ---- latest drives ----
    const list = D.trips.filter((t) => new Date(t.start) <= asOf).slice(0, 5);
    blocks.push(ui.section('Latest drives', `<div class="ui-card is-flush hm-trips" role="list">${list.map((t) => ui.tripRow(t, { ent: ctx.ent, isNew: storyTrip && t.id === storyTrip.id })).join('')}</div>`, { action: { text: 'All drives', nav: 'S-HIS-01' } }));
    return blocks;
  }

  /** o.pre = extra blocks above the body (S-LIVE-10 in-app card) · o.fixed = extra fixed html (its stand-in chrome). */
  function homeRender(ctx, o = {}) {
    const { ui } = ctx;
    const preview = ['preview', 'previewEnding', 'rideAlong'].includes(ctx.ent);
    const trailing = [preview ? ui.previewBadge(ctx.ent, ctx.state === 'firstRunEmpty' ? { day: 1 } : {}) : '', ui.avatar({ badge: ctx.capture.attention })];
    const blocks = [...(o.pre || []), ...homeBody(ctx)];
    return ui.page(`<div class="hm" data-state="${ctx.state}">${blocks.map((b, i) => `<div class="hm-b" data-rise style="--i:${i}">${b}</div>`).join('')}</div>`,
      { large: { title: 'Drives', subtitle: ctx.android ? 'Friday 6 November' : null, trailing }, fixed: o.fixed });
  }
  function homeMount(el, ctx) {
    ctx.ui.anim.rise(el, '[data-rise]', { stagger: 40 });
    ctx.ui.anim.rings(el);
    return accLongPress(el, ctx);
  }
  /** WP-B: long-press the capture accessory / floating toolbar → S-HOME-03 (pause sheet). The shell owns the element,
      so the listener is attached from the screen's mount and removed on cleanup. */
  function accLongPress(el, ctx) {
    const vp = el.closest('.dp-viewport');
    if (!vp) return undefined;
    let timer = null, fired = false;
    const target = (e) => e.target.closest && e.target.closest('.dp-acc:not(.is-recording)');
    const down = (e) => { if (!target(e) || e.target.closest('[data-start-drive],[data-action-shell]')) return; fired = false; timer = setTimeout(() => { fired = true; ctx.haptic('longPress'); ctx.nav(ctx.capture.mode === 'paused' ? 'S-HOME-03/resume' : 'S-HOME-03'); }, 520); };
    const cancel = () => clearTimeout(timer);
    const click = (e) => { if (fired && target(e)) { e.stopPropagation(); e.preventDefault(); fired = false; } };
    vp.addEventListener('pointerdown', down); vp.addEventListener('pointerup', cancel); vp.addEventListener('pointerleave', cancel); vp.addEventListener('click', click, true);
    return () => { cancel(); vp.removeEventListener('pointerdown', down); vp.removeEventListener('pointerup', cancel); vp.removeEventListener('pointerleave', cancel); vp.removeEventListener('click', click, true); };
  }
  const homeChrome = (ctx) => {
    const st = ctx.state;
    if (st === 'recordingInProgress' || st === 'recordingBanner') return { accessory: 'recording' };
    if (st === 'processingATrip') return { accessory: 'processing' };
    if (st === 'tabBarMinimisedOnScroll') return { minimised: true };
    return {};
  };
  const HOME_SPEC = {
    purpose: 'The Drives tab root: glanceable capture status, the newest decoded drive (Story card), this week at a glance and the latest drives. It never shows live speed, score or record pace while recording (SAF-02).',
    layout: [
      'Large title “Drives” (iOS 34/41 bold large title · Android LargeFlexibleTopAppBar 32/40 with the date as subtitle); trailing: Preview/Ride-along badge (when active) + profile avatar (dot = needs attention).',
      'State banner slot (needs attention · paused · manual · denied · interrupted recovered · processing), full width, 16 margins.',
      'New Story card (state newStoryCard): hero card (radius 32 iOS / 28 Android) tinted 18% with the car colour; kicker “● Drive decoded”, route label, part of day · car, distance · duration, record + delta chips, “Watch Story” filled small button; Smoothness ring 116 with baseline and record notches.',
      '“This week” card: average Smoothness (DP Numerals 48), delta vs last week (neutral when lower, never red), 7-day dot strip (Mon–Sun, ≤ 3 dots per day, future days dimmed), drives · km · time footer. Tap → Insights tab.',
      '“Latest drives” section (title + “All drives” → S-HIS-01): 5 trip cards in one card, newest first (components.md §12).',
      'Capture status: iOS tabViewBottomAccessory glass capsule (50 pt, 8 above the floating tab bar) · Android M3E floating toolbar (64 dp, primaryContainer, 16 above the navigation bar, Drives tab only). Start → S-DRV-01 with the trip-start expansion.',
    ],
    components: ['largeTitle', 'previewBadge', 'avatar', 'banner', 'card', 'ring', 'deltaChip', 'chip', 'button', 'tripRow', 'scoreBadge', 'qualityBadge', 'section', 'empty', 'loader', 'skeleton', 'list', 'row', 'tabBar', 'accessory'],
    interactions: [
      'Trip card → S-TRP-01 (zoom transition on iOS, container transform on Android).',
      'New Story card / “Watch Story” → S-STY-01 (D1 gate: held on S-DRV-05 while moving).',
      '“This week” card → Insights tab (S-STA-01). “All drives” → S-HIS-01.',
      'Accessory/toolbar body → S-HOME-02 capture status sheet; Start → S-DRV-01 (manual trip; gate locks the app to the in-drive screen); recording banner → S-DRV-01; needs attention → S-SET-05; denied → S-ONB-16.',
      'Avatar → S-ACC-01 (iOS sheet, Android full-screen). Preview badge → S-SUB-01.',
      'Banners: Fix → S-SET-05 / S-ONB-16 · Turn on → S-SET-03 · Resume → resumes auto-detect (toast) · View → S-TRP-01/interrupted.',
      'Scroll: large title collapses to an inline title; iOS tab bar minimises to a compact capsule with the accessory inline; Android toolbar slides away and returns on scroll up.',
    ],
    motion: [
      'Blocks rise 8 px and fade in, 40 ms stagger, spring.default (RM: 220 ms cross-fade).',
      'Story card ring: ring reveal (motion.md §3.4) 1400 ms count-up locked to the arc; record notch turns solid gold as the arc passes 89 (doubleSuccess haptic); RM: full ring, static number.',
      'Start: press scale 0.96 (spring.fast) + confirm haptic, then the accessory/toolbar expands into the in-drive surface (spring.slow container transform, motion.md §3.1).',
      'Recording banner dot: continuous livePulse breathe 2400 ms (passenger trip / finalising only); RM: static dot.',
      'Tab switch: iOS cross-fade + lens slides (spring.default); Android fade-through.',
    ],
    a11y: [
      'Trip card label: “Home to Work, Thursday morning, 5 November. 37.9 kilometres, 30 minutes. Smoothness 91. Good data.” (one element, button).',
      'This-week card is one element: “This week: 6 drives, 132.1 kilometres, 2 h 9 min. Average Smoothness 86, 1 down on last week.”',
      'Accessory: “Auto-detect on · ready. Last check-in 18 min ago” + separate “Start drive” button (≥ 44 pt / 48 dp).',
      'XL text: trip cards stack (glyph above), stats wrap; the accessory keeps one line with ellipsis and the full text in its label.',
      'Colour never carries meaning alone: ▲/▼/● glyphs on deltas, shapes on quality glyphs, the attention state has a triangle glyph and text.',
    ],
    platformDiffs: {
      ios: 'Floating Liquid Glass tab bar (inset 16, bottom 21, 62 tall) with lens selection; tabViewBottomAccessory carries capture status on every tab; glass avatar toolbar item opens Profile as a sheet; score badges are mini rings; large title collapses to the inline title with a scroll-edge fade.',
      android: 'NavigationBar (64 dp + gesture area) with 56×32 secondaryContainer indicator; capture status is an M3E floating toolbar on the Drives tab only (other tabs show a status chip when it needs attention); avatar in a Cookie12Sided shape opens Profile full-screen; score badges use Cookie9Sided; LargeFlexibleTopAppBar with date subtitle.',
    },
    analytics: ['home_viewed {state}', 'story_card_tapped {trip_age}', 'manual_start_tapped {source: accessory|toolbar}', 'capture_status_opened', 'history_opened {source: home}'],
    copy: {
      'Accessory ready': 'Auto-detect on · ready / Last check-in {n} min ago',
      'Accessory manual': 'Manual mode / Auto-detect is off',
      'Accessory paused': 'Auto-detect paused / Until 8:00 tomorrow · Resume',
      'Accessory attention': 'Auto-detect needs attention / Location is set to While Using',
      'Accessory recording': 'Recording · {min} min · {km} km / Passenger trip · tap to open',
      'Empty': 'Your drives will appear here. Just drive. DrivePerf notices when you set off and decodes each drive when you park.',
      'Interrupted': 'We saved your drive. {part of day} · {label}. {iOS ended DrivePerf in the background | Android stopped DrivePerf in the background}. Everything before then is saved.',
      'Story card kicker': 'Drive decoded',
    },
    freePro: 'Free: identical layout (summaries are free forever); trips older than 30 days show a small lock after the score for “Replay & Analysis in Pro”. Preview/Ride-along: “PRO PREVIEW · DAY n” / “RIDE-ALONG · DAY n” badge → S-SUB-01. No paywall is ever shown here during or right after a trip (D12, F-SUB-08).',
    data: 'This-week numbers are derived from DP_DATA.trips for the current week (they equal recaps[0]: 6 drives · 132.1 km · 2 h 9 min · 86, −1 vs 87). In newStoryCard the week is computed as of the Story trip’s end (Thu 5 Nov 08:13).',
    icons: ['drives', 'routes', 'insights', 'garage', 'record', 'story', 'chevronRight', 'rosette', 'lock', 'gps', 'shield', 'pause'],
    assets: ['logo-a-loop.svg'],
  };

  screen('S-HOME-01', {
    name: 'Home (Drives tab): ready to record',
    wp: 'B', platforms: ['ios', 'android'], presentation: 'root', tab: 'drives',
    states: HOME_STATES, stateParams: HOME_PARAMS,
    chrome: homeChrome,
    render: homeRender, mount: homeMount,
    actions: {
      resume: (ctx) => { ctx.toast('Auto-detect resumed', { icon: 'check' }); ctx.setState('default'); return false; },
    },
    spec: HOME_SPEC,
  });

  screen('S-NAV-01', {
    name: 'App shell and tab bar (4 tabs + avatar)',
    wp: 'B', platforms: ['ios', 'android'], presentation: 'root', tab: 'drives',
    states: [
      { id: 'default', label: 'Def' },
      { id: 'recordingBanner', label: 'Recording banner (passenger trip or finalising)' },
      { id: 'needsAttentionBadge', label: '“Needs attention” badge' },
      { id: 'prevBadge', label: 'Prev badge' },
      { id: 'tabBarMinimisedOnScroll', label: 'Tab bar minimised on scroll' },
    ],
    stateParams: { needsAttentionBadge: { health: 'alwaysLost' }, prevBadge: { ent: 'preview' } },
    chrome: homeChrome,
    initialScroll: (ctx) => (ctx.state === 'tabBarMinimisedOnScroll' ? 330 : 0),
    render(ctx) {
      const map = { recordingBanner: 'recordingInProgress', needsAttentionBadge: 'needsAttention', prevBadge: 'prevDayCounter' };
      return homeRender({ ...ctx, state: map[ctx.state] || 'default' });
    },
    mount: homeMount,
    spec: {
      purpose: 'The app shell (screen-map.md §1, validated): four tabs — Drives · Routes · Insights · Garage — with Profile moved to an avatar in the top-trailing corner of every tab root, and capture status always one glance away. Every other screen mounts inside it.',
      layout: [
        'Tab roots: Drives S-HOME-01 · Routes S-RTE-01 · Insights S-STA-01 · Garage S-GAR-01 (icons DP_ICONS drives/routes/insights/garage).',
        'iOS: floating Liquid Glass tab bar (inset 16, bottom 21, height 62, selected = Glide icon + label inside a glass lens) + tabViewBottomAccessory capture capsule (50 pt, 8 above the bar) on all tabs.',
        'Android: NavigationBar (64 dp + 24 dp gesture area, surfaceContainer, indicator 56×32 secondaryContainer, labels always visible) + M3E floating toolbar (64 dp, primaryContainer) docked 16 dp above it on the Drives tab only.',
        'Avatar top-trailing on every tab root: iOS 38 pt glass circle (initial) · Android 40 dp Cookie12Sided secondaryContainer. Badge dot = needs attention; Preview badge beside it.',
        'Minimised on scroll (iOS): the bar shrinks to a 54 pt capsule showing only the selected icon; the accessory folds inline to its right. Android: the toolbar slides out on scroll down and back on scroll up; the navigation bar stays.',
      ],
      components: ['tabBar', 'accessory', 'avatar', 'previewBadge', 'largeTitle'],
      interactions: [
        'Tab tap → that tab root (iOS cross-fade + lens spring · Android fade-through). Re-tapping the current tab scrolls to top.',
        'Avatar → S-ACC-01 (iOS sheet, large detent, own NavigationStack · Android full-screen, shared axis X, predictive back).',
        'Accessory / toolbar → S-HOME-02 (ready), S-DRV-01 (recording), S-SET-05 (attention); Start → S-DRV-01 (trip start).',
        'Every navigation passes the D1 gate (router → DP.gate): while a driver trip is active only S-DRV-01/02/03 are reachable; content opened while moving waits on S-DRV-05.',
      ],
      motion: ['Tab lens: spring.default slide between tabs · selection haptic.', 'Minimise on scroll: spring.default (iOS) · toolbar 150 ms emphasizedAccelerate out / spring.fast in (Android).', 'Recording banner: livePulse 2400 ms breathe on the dot; RM static.', 'Trip start: accessory/toolbar expands into S-DRV-01 (container transform, spring.slow).'],
      a11y: ['Tabs are a tablist: “Drives, tab, 1 of 4, selected”. Labels always visible on both platforms; iOS large-content viewer on long-press at AX sizes.', 'Avatar: “Profile, needs attention” when badged. Preview badge: “Pro Preview, day 9 of 14”.', 'The accessory never announces timer updates (no live region).'],
      platformDiffs: { ios: 'TabView + Tab, .tabBarMinimizeBehavior(.onScrollDown), .tabViewBottomAccessory { CaptureStatusAccessory() }; Profile is a glass toolbar item → sheet.', android: 'NavigationBar (short) always visible; HorizontalFloatingToolbar on Drives; avatar in the top app bar trailing slot → full-screen destination.' },
      analytics: ['tab_selected {tab}', 'profile_opened {source: avatar}', 'capture_status_opened {source: accessory|toolbar}'],
      copy: { Tabs: 'Drives · Routes · Insights · Garage', 'Recording banner': 'Recording · {min} min · {km} km', 'Needs attention': 'Auto-detect needs attention' },
      freePro: 'Same shell for every plan. Preview: “PRO PREVIEW · DAY n” badge beside the avatar.',
      icons: ['drives', 'routes', 'insights', 'garage', 'record', 'profile'],
      assets: ['logo-a-loop.svg'],
    },
  });
  /* ========================================== end exemplar ================================================ */

  /* ==========================================================================================================
     WP-B shared helpers (local to this IIFE; candidates for core primitives are listed in the WP report)
     ========================================================================================================== */
  const HERO_TRIP = 't20261105-0742';            // the "Drive decoded." notification fixture (n_decoded) and the new-record Story
  /** Live trip for a screen: ctx.params.live when it names a liveTrip state, else the fallback. Fields per fixtures.md §8. */
  const liveOf = (ctx, fb = 'manual') => ctx.data.live(ctx.params.live && ctx.D.liveTrip.states[ctx.params.live] ? ctx.params.live : fb);
  const carOf = (ctx, l) => (l && l.vehicleId ? ctx.data.vehicle(l.vehicleId) : null);
  const minsOf = (l) => Math.max(1, Math.round(l.elapsedS / 60));
  /** Wall-clock "5:46" (no am/pm, as the OS clocks show it) for iso + seconds. Derived, documented in each spec. */
  const wall = (iso, plus = 0) => new Date(new Date(iso).getTime() + plus * 1000).toLocaleTimeString('en-US', { timeZone: 'America/Toronto', hour: 'numeric', minute: '2-digit' }).replace(/\s?[AP]M$/, '');
  const ago = (ctx, iso) => ctx.data.ago(iso);

  /** Health status glyph (components.md §17): green = filled check disc · amber = triangle · red = octagon with "!". */
  function hGlyph(st, s = 22) {
    const lbl = { red: 'Needs fixing', amber: 'Worth knowing', green: 'OK', checking: 'Checking' }[st] || 'OK';
    if (st === 'red') return `<svg class="wb-hg is-red" width="${s}" height="${s}" viewBox="0 0 24 24" role="img" aria-label="${lbl}"><path d="M8.1 2h7.8L22 8.1v7.8L15.9 22H8.1L2 15.9V8.1z" fill="currentColor"/><path d="M12 6.8v6.4" stroke="var(--wb-hg-in)" stroke-width="2.4" stroke-linecap="round"/><circle cx="12" cy="17" r="1.45" fill="var(--wb-hg-in)"/></svg>`;
    if (st === 'amber') return `<svg class="wb-hg is-amber" width="${s}" height="${s}" viewBox="0 0 24 24" role="img" aria-label="${lbl}"><path d="M12 2.2c.6 0 1.1.3 1.4.8l9.2 16.3c.6 1.1-.2 2.4-1.4 2.4H2.8c-1.2 0-2-1.3-1.4-2.4L10.6 3c.3-.5.8-.8 1.4-.8z" fill="currentColor"/><path d="M12 8.4v5.6" stroke="var(--wb-hg-in)" stroke-width="2.3" stroke-linecap="round"/><circle cx="12" cy="17.4" r="1.35" fill="var(--wb-hg-in)"/></svg>`;
    return `<svg class="wb-hg is-green" width="${s}" height="${s}" viewBox="0 0 24 24" role="img" aria-label="${lbl}"><circle cx="12" cy="12" r="10.5" fill="currentColor"/><path d="M7.3 12.4l3.2 3.1 6.3-6.6" fill="none" stroke="var(--wb-hg-in)" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
  }
  /** Plain-language state text per Health Check item (status → words; colour never carries it alone). */
  const H_TEXT = {
    location: { green: 'Allowed', red: { ios: 'While Using only', android: 'While using the app only' } },
    precise: { green: 'On', red: 'Off' }, motion: { green: 'Allowed', red: 'Off' }, notifications: { green: 'Allowed', amber: 'Off' },
    bgRefresh: { green: 'On', red: 'Off' }, lowPower: { green: 'Off', amber: 'On' }, liveActivities: { green: 'On', amber: 'Off' },
    batteryOpt: { green: 'Unrestricted', amber: 'Optimized' }, oemSleep: { green: 'Not sleeping', red: 'In Sleeping apps' }, batterySaver: { green: 'Off', amber: 'On' },
    autoDetect: { green: 'On', amber: 'Paused until 8:00' }, storage: { green: 'Plenty of space', amber: 'Under 500 MB free' },
  };
  const hText = (ctx, it) => { const m = (H_TEXT[it.id] || {})[it.status] || (it.status === 'green' ? 'OK' : 'Check'); return typeof m === 'object' ? m[ctx.platform] : m; };

  /* ---------- OS frame pieces (non-interactive system chrome around our own live surfaces) ---------- */
  const SB_IOS = { sig: '<svg width="18" height="12" viewBox="0 0 18 12" aria-hidden="true"><g fill="currentColor"><rect x="0" y="7.5" width="3" height="4.5" rx="1"/><rect x="5" y="5" width="3" height="7" rx="1"/><rect x="10" y="2.5" width="3" height="9.5" rx="1"/><rect x="15" y="0" width="3" height="12" rx="1"/></g></svg>', wifi: '<svg width="16" height="12" viewBox="0 0 16 12" aria-hidden="true"><path d="M8 11.5L5.6 8.9a3.4 3.4 0 0 1 4.8 0zM3.4 6.7a6.5 6.5 0 0 1 9.2 0l-1.3 1.4a4.6 4.6 0 0 0-6.6 0zM1.2 4.4a9.6 9.6 0 0 1 13.6 0l-1.3 1.3a7.8 7.8 0 0 0-11 0z" fill="currentColor"/></svg>', bat: '<span class="dp-sb-bat"><i></i></span>' };
  const SB_AND = { wifi: '<svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M8 13.5L.6 5.2a10.6 10.6 0 0 1 14.8 0z" fill="currentColor"/></svg>', sig: '<svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M1.5 14.5h13v-13z" fill="currentColor"/></svg>', bat: '<svg width="10" height="16" viewBox="0 0 10 16" aria-hidden="true"><rect x="1" y="2" width="8" height="13" rx="1.6" fill="none" stroke="currentColor" stroke-width="1.4"/><rect x="2.6" y="5.5" width="4.8" height="8" rx=".6" fill="currentColor"/><rect x="3.5" y=".5" width="3" height="1.6" rx=".5" fill="currentColor"/></svg>' };
  /** Status bar drawn inside an OS frame (the shell's bar is hidden there because its clock is fixed at 9:41 / 7:30).
      o: {time, carrier (iOS lock screen), chip (Android Live Update status chip), notif (Android small icons), min (fewer right icons)} */
  function osBar(ctx, o = {}) {
    const { icons } = ctx;
    if (ctx.ios) return `<div class="os-sb" aria-hidden="true"><span class="os-sb-l">${o.carrier ? `<span class="os-carrier">${ctx.ui.esc(o.carrier)}</span>` : ctx.ui.esc(o.time || '9:41')}</span><span class="os-sb-r">${o.min ? '' : SB_IOS.sig}${SB_IOS.wifi}${SB_IOS.bat}</span></div>`;
    const chip = o.chip ? `<span class="os-chip ${o.chipHi ? 'is-hi' : ''}">${icons.loop({ size: 13, mono: true, color: 'currentColor' })}${ctx.ui.esc(o.chip)}</span>` : '';
    const notif = (o.notif || []).map((n) => `<span class="os-sb-ni">${n === 'loop' ? icons.loop({ size: 14, mono: true, color: 'currentColor' }) : ctx.ui.icon(n, 14)}</span>`).join('');
    return `<div class="os-sb" aria-hidden="true"><span class="os-sb-l"><b>${ctx.ui.esc(o.time || '7:30')}</b>${chip}${notif}</span><span class="os-sb-r">${SB_AND.wifi}${SB_AND.sig}${SB_AND.bat}</span></div>`;
  }
  const mockTag = (text) => `<span class="ui-sys-tag os-tag">${text}</span>`;
  const GL = {
    torch: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" aria-hidden="true"><path d="M7 3h10v3l-2.5 4v10.5h-5V10L7 6z"/><path d="M12 13v2.5"/></svg>',
    camera: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" aria-hidden="true"><path d="M3.5 8.5h3.2l1.7-2.5h7.2l1.7 2.5h3.2v10.5h-17z"/><circle cx="12" cy="13.3" r="3.4"/></svg>',
    plane: '<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M21 15.5v-1.8l-7.6-4.8V4a1.4 1.4 0 0 0-2.8 0v4.9L3 13.7v1.8l7.6-2.3v4.9l-2 1.5V21l3.4-1 3.4 1v-1.4l-2-1.5v-4.9z"/></svg>',
    cell: '<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="3" y="14" width="3" height="6" rx="1"/><rect x="8" y="11" width="3" height="9" rx="1"/><rect x="13" y="7.5" width="3" height="12.5" rx="1"/><rect x="18" y="4" width="3" height="16" rx="1"/></svg>',
    wifi: '<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 19.5l-3.3-3.6a4.7 4.7 0 0 1 6.6 0zM5.6 13.1a9 9 0 0 1 12.8 0l-1.9 1.9a6.3 6.3 0 0 0-9 0zM2.4 9.9a13.5 13.5 0 0 1 19.2 0l-1.9 1.9a10.8 10.8 0 0 0-15.4 0z"/></svg>',
    bt: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 7.5l10 9-5 4.5V3l5 4.5-10 9"/></svg>',
    moon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M20 14.6A8.5 8.5 0 0 1 9.4 4a8.5 8.5 0 1 0 10.6 10.6z"/></svg>',
    rotate: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="7.5" y="4" width="9" height="16" rx="2"/><path d="M3 12a9 9 0 0 1 3-6.7M21 12a9 9 0 0 1-3 6.7"/></svg>',
    timer: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="13" r="7.5"/><path d="M12 13V9M10 2.8h4"/></svg>',
    calc: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><rect x="5" y="3" width="14" height="18" rx="3"/><path d="M8.5 7.5h7M8.5 12h.01M12 12h.01M15.5 12h.01M8.5 16h.01M12 16h.01M15.5 16h.01"/></svg>',
    mirror: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4.5" width="13" height="9.5" rx="2"/><rect x="8" y="10" width="13" height="9.5" rx="2" fill="currentColor" fill-opacity=".25"/></svg>',
    sun: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"/></svg>',
    speaker: '<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M15.5 9a4.2 4.2 0 0 1 0 6M18 6.5a7.8 7.8 0 0 1 0 11" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
    power: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" aria-hidden="true"><path d="M12 3v8"/><path d="M6.6 6.5a8 8 0 1 0 10.8 0"/></svg>',
    fp: '<svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" aria-hidden="true"><path d="M6.2 5.6A8 8 0 0 1 18.8 8M4.6 10.5a8 8 0 0 1 .5-2.5M19.6 12.5c0 2-.3 3.9-.9 5.6M8.3 19.8A12 12 0 0 0 9.5 13a2.5 2.5 0 0 1 5 0c0 3.3-.6 6.3-1.7 8.6M7.4 10.1A5 5 0 0 1 17 12c0 1.7-.2 3.3-.5 4.8M5 15.8A15 15 0 0 0 6.6 13"/></svg>',
  };
  /** iOS lock screen with our own status bar + accurate bottom quick actions (flashlight · camera). */
  function iosLock(ctx, content, o = {}) {
    const acc = o.acc ? `<div class="os-ls-acc">${o.acc}</div>` : '';
    return `<div class="os-frame os-lock">${ctx.ui.lockScreen(content, { time: o.time, date: o.date })}${acc}${osBar(ctx, { carrier: 'Rogers' })}<div class="os-ls-foot" aria-hidden="true"><span>${GL.torch}</span><span>${GL.camera}</span></div><span class="os-homebar"></span>${mockTag(o.tag || 'Lock Screen · mock')}</div>`;
  }
  /** Android lock screen (Android 16): clock, notifications under it, shortcuts + fingerprint at the bottom. */
  function andLock(ctx, content, o = {}) {
    return `<div class="os-frame os-lock is-and">${ctx.ui.lockScreen(content, { time: o.time, date: o.date })}${osBar(ctx, { time: o.time, chip: o.chip, notif: o.notif })}<div class="os-ls-foot" aria-hidden="true"><span>${GL.torch}</span><i>${GL.fp}</i><span>${GL.camera}</span></div>${mockTag(o.tag || 'Lock screen · mock')}</div>`;
  }
  const APPS_IOS = [['Calendar', 210], ['Photos', 30], ['Camera', 220], ['Weather', 200], ['Clock', 0], ['Notes', 48], ['Maps', 120], ['Music', 350], ['Settings', 215], ['Wallet', 0], ['Podcasts', 280], ['Files', 205]];
  const APPS_AND = [['Clock', 210], ['Calendar', 220], ['Photos', 30], ['Maps', 120], ['Settings', 200], ['Files', 180], ['Camera', 0], ['Music', 330]];
  /** DrivePerf app icon (brand.md §4): the Loop on the Night gradient. */
  const appIcon = (ctx) => `<span class="os-app is-dp" aria-hidden="true">${ctx.icons.loop({ size: 38, color: '#5BE7C4', dot: '#fff' })}</span>`;
  const appTile = (ctx, name, h, dp) => `<span class="os-appt">${dp ? appIcon(ctx) : `<span class="os-app" style="--h:${h}"></span>`}<small>${ctx.ui.esc(name)}</small></span>`;
  /** iOS Home Screen (widgets first, then a page of labelled app icons, glass dock). */
  function iosHome(ctx, widgets, o = {}) {
    const apps = APPS_IOS.slice(0, o.apps ?? 8).map(([n, h], i) => appTile(ctx, i === (o.dpAt ?? 3) ? 'DrivePerf' : n, h, i === (o.dpAt ?? 3))).join('');
    return `<div class="os-frame os-home"><div class="ui-wall"></div><div class="os-hs-grid">${widgets}${apps}</div><div class="os-hs-search" aria-hidden="true">${ctx.ui.icon('search', 13)}Search</div><div class="os-hs-dock" aria-hidden="true">${[300, 120, 200, 35].map((h) => `<span class="os-app" style="--h:${h}"></span>`).join('')}</div>${osBar(ctx, { time: o.time, min: o.minBar })}<span class="os-homebar"></span>${mockTag(o.tag || 'Home Screen · mock')}</div>`;
  }
  /** Android home screen (Pixel launcher): At a Glance, widgets, labelled round icons, search bar, dock. */
  function andHome(ctx, widgets, o = {}) {
    const apps = APPS_AND.slice(0, o.apps ?? 8).map(([n, h], i) => appTile(ctx, i === (o.dpAt ?? 2) ? 'DrivePerf' : n, h, i === (o.dpAt ?? 2))).join('');
    return `<div class="os-frame os-home is-and"><div class="ui-wall"></div><div class="os-glance" aria-hidden="true"><b>${ctx.ui.esc(o.glance || 'Friday, 6 Nov')}</b></div><div class="os-hs-grid">${widgets}${apps}</div><div class="os-hs-dock" aria-hidden="true">${[0, 120, 200, 35].map((h) => `<span class="os-app" style="--h:${h}"></span>`).join('')}</div><div class="os-hs-search" aria-hidden="true">${ctx.ui.icon('search', 18)}<span></span></div>${osBar(ctx, { time: o.time, chip: o.chip, chipHi: o.chipHi, notif: o.notif })}${o.extra || ''}${mockTag(o.tag || 'Home screen · mock')}</div>`;
  }
  /** Android notification shade / Quick Settings (Android 16). o: {time, date, tiles:[html], full (expanded QS), notifs, silent, footer, overlay} */
  function andShade(ctx, o = {}) {
    const tiles = (o.tiles || []).map((t) => `<div class="os-qs-cell">${t}</div>`).join('');
    const bright = `<div class="os-qs-bright" aria-hidden="true"><span>${GL.sun}</span><i></i></div>`;
    const footer = o.full ? `<div class="os-qs-foot" aria-hidden="true">${o.footer || ''}<span class="os-qs-ic">${ctx.ui.icon('gear', 20)}</span><span class="os-qs-ic">${GL.power}</span></div>` : '';
    const notifs = o.notifs ? `<div class="os-shade-n">${o.notifs}</div>` : '';
    const silent = o.silent ? `<div class="os-shade-sec">Silent</div><div class="os-shade-n">${o.silent}</div>` : '';
    return `<div class="os-frame os-shade ${o.full ? 'is-full' : ''}"><div class="ui-wall"></div><div class="os-shade-scrim"></div><div class="os-shade-p"><div class="os-shade-h"><b>${ctx.ui.esc(o.time || '5:46')}</b><span>${ctx.ui.esc(o.date || 'Fri, 6 Nov')}</span></div>${o.full ? bright : ''}<div class="os-qs-grid">${tiles}</div>${o.full ? '<div class="os-qs-dots" aria-hidden="true"><i class="is-on"></i><i></i></div>' : ''}${footer}</div>${notifs}${silent}${o.overlay || ''}${osBar(ctx, { time: o.time || '5:46', chip: o.chip, notif: o.notif })}${mockTag(o.tag || 'Notification shade · mock')}</div>`;
  }
  /** A non-DrivePerf QS tile (Android 16 large tile). */
  const qsOther = (label, glyph, on, sub) => `<span class="ui-qs ${on ? 'ui-qs--recording os-qs-on' : 'ui-qs--off'}" aria-hidden="true"><i class="os-qs-g">${glyph}</i><span><b>${label}</b>${sub ? `<small>${sub}</small>` : ''}</span></span>`;
  const QS_DEFAULT = [['Internet', GL.wifi, true, 'Home Wi-Fi'], ['Bluetooth', GL.bt, true, 'On'], ['Flashlight', GL.torch, false, ''], ['Do Not Disturb', GL.moon, false, ''], ['Auto-rotate', GL.rotate, false, ''], ['Airplane mode', GL.plane, false, '']];

  /** Android notification card (our own surface) with real action buttons. o: {title, body, header, chip, actions:[{text, …act}], held, big, progress, nav, params, label, cls} */
  function andNotif(ctx, o = {}) {
    const { ui, icons } = ctx;
    const acts = (o.actions || []).map((a) => `<span class="os-n-act ui-press" ${ui.act(a)}>${a.icon ? ui.icon(a.icon, 16) : ''}${ui.esc(a.text)}</span>`).join('');
    const prog = o.progress ? `<div class="os-n-prog" aria-hidden="true"><i></i><span>${o.progress}</span></div>` : '';
    return `<div class="ui-notif os-n ${o.big ? 'is-big' : ''} ${o.cls || ''} ${o.nav ? 'ui-press' : ''}" ${ui.act({ nav: o.nav, params: o.params, label: o.label })}><div class="ui-notif-h"><span class="ui-notif-app">${icons.loop({ size: 14, mono: true, color: 'currentColor' })}</span><span>${ui.esc(o.header || 'DrivePerf · now')}</span>${o.chip ? `<em>${ui.esc(o.chip)}</em>` : ''}${ui.icon('chevronDown', 16)}</div><b>${ui.esc(o.title)}</b>${o.body ? `<p>${o.bodyHtml ? o.body : ui.esc(o.body)}</p>` : ''}${prog}${acts ? `<div class="os-n-acts">${acts}</div>` : ''}</div>`;
  }
  /** iOS notification card (our own surface). o: {title, body, time, nav, params, label, stack} */
  function iosNotif(ctx, o = {}) {
    const { ui, icons } = ctx;
    return `<div class="os-n-wrap ${o.stack ? 'has-stack' : ''}"><div class="ui-notif os-n ${o.nav ? 'ui-press' : ''}" ${ui.act({ nav: o.nav, params: o.params, label: o.label })}><span class="ui-notif-app">${icons.loop({ size: 22, color: '#5BE7C4', dot: '#fff' })}</span><div class="ui-notif-b"><div class="ui-notif-h"><b>${ui.esc(o.title)}</b><span>${ui.esc(o.time || 'now')}</span></div><p>${ui.esc(o.body || '')}</p></div></div></div>`;
  }

  /* ==========================================================================================================
     S-HOME-02 · Capture status sheet (medium)                         F-CAP-02 · F-SET-02 · components.md §17
     ========================================================================================================== */
  function hcRows(ctx, h, o = {}) {
    const { ui } = ctx;
    const pick = o.all ? h.items : h.items.filter((i) => i.status !== 'green' || ['location', 'motion', 'notifications', ctx.ios ? 'bgRefresh' : 'batteryOpt'].includes(i.id));
    const ordered = [...pick.filter((i) => i.status === 'red'), ...pick.filter((i) => i.status === 'amber'), ...pick.filter((i) => i.status === 'green')];
    return ordered.map((it) => {
      const bad = it.status !== 'green';
      const trail = bad ? ui.button('Fix', { kind: 'tonal', size: 's', nav: 'S-SET-05', params: { health: o.preset }, label: `Fix ${it.label}` }) : ui.icon('chevronRight', 16);
      return ui.row({ lead: `<span class="hc-g">${hGlyph(it.status, 22)}</span>`, title: it.label, sub: hText(ctx, it), trailing: bad ? trail : 'chevron', nav: bad ? null : 'S-SET-05', params: bad ? null : { health: o.preset }, cls: bad ? 'hc-row is-bad' : 'hc-row' });
    });
  }
  function hcCheckin(ctx, h, open) {
    const { ui, data } = ctx;
    const lastTrip = data.newestTrip();
    const inner = `<div class="hc-ci-cell"><span class="hc-k">Last check-in</span><b>${ui.esc(ago(ctx, h.lastCheckIn))}</b><span class="hc-s">${ui.esc(data.time(h.lastCheckIn))}</span></div><i class="hc-ci-sep"></i><div class="hc-ci-cell"><span class="hc-k">Last drive</span><b>${ui.esc(data.time(lastTrip.end))}</b><span class="hc-s">${ui.esc(lastTrip.partOfDay)}</span></div>${ui.icon(open ? 'chevronDown' : 'chevronRight', 16)}`;
    const aria = `Last check-in ${ago(ctx, h.lastCheckIn)}, at ${data.time(h.lastCheckIn)}. Last drive recorded at ${data.time(lastTrip.end)}, ${lastTrip.partOfDay}.`;
    const detail = open ? `<div class="hc-ci-more">
        <p>DrivePerf checks in each time your phone wakes it: a drive, a reboot, opening the app. Auto-detect re-arms every time.</p>
        <div class="hc-tl" role="list">
          <div role="listitem"><i class="is-on"></i><span><b>${ui.esc(data.time(h.lastCheckIn))} · checked in</b><small>Auto-detect armed · ${ui.esc(ago(ctx, h.lastCheckIn))}</small></span></div>
          <div role="listitem"><i></i><span><b>${ui.esc(data.time(lastTrip.end))} · drive recorded</b><small>${ui.esc(lastTrip.partOfDay)} · ${ui.num(data.km(lastTrip.distanceKm), 'km')}</small></span></div>
          <div role="listitem"><i class="is-future"></i><span><b>Only if nothing by ${ui.esc(data.time(new Date(new Date(h.lastCheckIn).getTime() + 864e5).toISOString()))} tomorrow</b><small>We’ll remind you to open DrivePerf once</small></span></div>
        </div></div>` : '';
    return `<div class="hc-ci ${open ? 'is-open' : ''} ui-press" ${ui.act({ state: open ? 'default' : 'lastCheckInTime', label: aria + (open ? ' Collapse.' : ' Show details.') })}><div class="hc-ci-row">${inner}</div></div>${detail}`;
  }
  screen('S-HOME-02', {
    name: 'Capture status sheet',
    wp: 'B', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'drives',
    detent: (ctx) => (ctx.state === 'lastCheckInTime' ? 'large' : 'medium'),
    over: (ctx) => (ctx.state === 'attention' ? { id: 'S-HOME-01', state: 'needsAttention', params: { health: 'alwaysLost' } } : 'S-HOME-01'),
    states: [{ id: 'default', label: 'All good' }, { id: 'attention', label: 'Attention (Always lost)' }, { id: 'lastCheckInTime', label: 'Last check-in time (expanded)' }],
    stateParams: { attention: { health: 'alwaysLost' } },
    render(ctx) {
      const { ui, data, state } = ctx;
      const hp = ctx.params.health && ctx.D.health.presets[ctx.params.health] ? ctx.params.health : null;
      const preset = state === 'attention' ? (hp && hp !== 'allGood' ? hp : 'alwaysLost') : hp || (ctx.params.capture === 'paused' ? 'paused' : 'allGood');
      const paused = preset === 'paused';
      const h = data.health(ctx.platform, preset);
      const reds = h.items.filter((i) => i.status === 'red'), ambers = h.items.filter((i) => i.status === 'amber');
      const ok = !reds.length;
      const lead = reds[0] || ambers[0];
      const line = !ok ? lead.detail || 'Automatic trips are paused.' : paused ? 'Drives only record when you tap Start.' : lead && lead.detail ? lead.detail : h.promise;
      const sum = `<div class="hc-sum ${ok ? 'is-ok' : 'is-bad'}" role="status">${hGlyph(ok ? (ambers.length ? 'amber' : 'green') : 'red', 36)}<div><b>${ui.esc(h.summary)}</b><p>${ui.esc(line)}</p></div></div>`;
      const total = h.items.length, good = h.items.filter((i) => i.status === 'green').length;
      const pauseRow = paused ? ui.row({ icon: 'play', title: 'Resume auto-detect', sub: 'Paused until 8:00 tomorrow', nav: 'S-HOME-03/resume' }) : ui.row({ icon: 'pause', title: 'Pause auto-detect', sub: 'For an hour, until tomorrow, or until you turn it back on', nav: 'S-HOME-03' });
      const body = [
        sum,
        hcCheckin(ctx, h, state === 'lastCheckInTime'),
        ui.section(ok ? `Checks · ${good} of ${total} OK` : `${reds.length} to fix`, ui.list([...hcRows(ctx, h, { preset }), ui.row({ icon: 'health', title: 'Open Health Check', sub: `All ${total} checks`, nav: 'S-SET-05', params: { health: preset } })]), { style: 'caption' }),
        ui.section('', ui.list([pauseRow]), { style: 'caption', footer: 'Checks run on this phone. Nothing is sent anywhere.' }),
        ok ? '' : `<div class="hc-cta">${ui.button('Fix it in Health Check', { kind: 'filled', size: 'l', block: true, nav: 'S-SET-05', params: { health: preset }, haptic: 'tap' })}</div>`,
      ];
      return `${ui.sheetHeader('Capture status', { sub: ok ? 'Automatic trips' : 'Automatic trips are paused' })}<div class="hc">${body.filter(Boolean).map((b) => `<div class="hc-b" data-rise>${b}</div>`).join('')}</div>`;
    },
    mount(el, ctx) {
      if (ctx.state === 'attention') ctx.haptic('warning');
      const g = el.querySelector('.hc-sum .wb-hg');
      ctx.ui.anim.pop(g, 120);
    },
    spec: {
      purpose: 'Answers “will my next drive be recorded?” in one glance from the capture accessory / floating toolbar: the Health Check summary, last check-in and last drive times, the few checks that matter, and a way to pause (F-CAP-02, F-SET-02). Fixes live in S-SET-05.',
      layout: [
        'Sheet at the medium detent (iOS glass sheet, radius 38 at partial detents · Android ModalBottomSheet, top radius 28, drag handle 32×4). The expanded check-in state moves to the large detent.',
        'Header “Capture status” + subtitle (“Automatic trips” / “Automatic trips are paused”); iOS glass close button, Android none (drag handle + scrim).',
        'Summary block: status glyph 36 (green check disc · amber triangle · red octagon) + summary in card-title weight + one-line promise or the red item’s detail (DP_DATA.health).',
        'Check-in card (tile radius 14 iOS / 12 Android, --tile-bg-in): two cells split by a hairline — “Last check-in 18 min ago · 7:12 pm” and “Last drive 12:27 pm · Friday midday” — trailing chevron. Tap expands it in place (state lastCheckInTime): explanation + a 3-step timeline (checked in · drive recorded · watchdog only if nothing by 7:12 pm tomorrow).',
        'Checks list (caption header “Checks · 9 of 9 OK” or “1 to fix”): red first, then amber, then the four core green checks (location, motion, notifications, Background App Refresh on iOS / Battery: Unrestricted on Android). Bad rows end in a tonal “Fix” button; good rows in a chevron. Last row “Open Health Check · All 9 checks”.',
        'Pause row (pause icon) → S-HOME-03, footer “Checks run on this phone. Nothing is sent anywhere.”',
        'Attention only: full-width filled “Fix it in Health Check” (52 / 56) at the bottom.',
      ],
      components: ['sheet', 'sheetHeader', 'list', 'row', 'button', 'section', 'healthGlyph (local hGlyph)'],
      interactions: [
        'Check-in card → toggles the expanded state (lastCheckInTime ↔ default), selection haptic.',
        'Any check row / “Open Health Check” / “Fix” / “Fix it in Health Check” → S-SET-05 (push, with the same health preset). Fix on a red Location row may deep-link straight to the OS Settings page (S-SET-05 handles it).',
        '“Pause auto-detect” → S-HOME-03 (sheet over this sheet on iOS; replaces it on Android).',
        'Dismiss: scrim tap, drag down, iOS close button. While a driver trip is active the sheet is unreachable (D1 gate → S-DRV-01); the accessory then opens S-DRV-01 instead.',
      ],
      motion: ['Sheet: spring.sheet up from the accessory/toolbar; detent change medium → large on expand uses the system detent spring.', 'Status glyph pops in (scale .86 → 1, spring.default) 120 ms after the sheet lands; sections rise 8 px, 30 ms stagger. RM: 220 ms cross-fade, no pop.', 'Haptic: warning when the sheet opens with a red item (motion.md §6); none otherwise.'],
      a11y: [
        'Summary is a status element: “Automatic trips are ready. Auto-detects most drives…” / “Auto-detect needs attention. Location access changed to While Using…”. The glyph has its own label (OK / Needs fixing / Worth knowing) so colour never carries the state.',
        'Check-in card is one button: “Last check-in 18 min ago, at 7:12 pm. Last drive recorded at 12:27 pm, Friday midday. Show details.”',
        'Rows read “Location: Always, Always” / “Location: Always, While Using only, Fix, button”. Focus order: close, summary, check-in, rows, pause, CTA.',
        'XL: the sheet opens at the large detent; check-in cells stack vertically; Fix buttons wrap under the text.',
      ],
      platformDiffs: {
        ios: 'Glass sheet (.presentationDetents([.medium, .large])), inset-grouped List rows 52 pt, SF-style glyphs; items from DP_DATA.health.ios (Background App Refresh, Live Activities, Low Power Mode).',
        android: 'ModalBottomSheet (surfaceContainerLow), M3E segmented ListItems 56 dp with TextButton/FilledTonalButton “Fix”; items from DP_DATA.health.android (Allow all the time, Battery: Unrestricted, Samsung sleeping apps, Battery Saver).',
      },
      analytics: ['capture_status_opened {source: accessory|toolbar|home, reds, ambers}', 'capture_status_checkin_expanded', 'health_fix_tapped {item, source: capture_status}', 'pause_sheet_opened {source: capture_status}'],
      copy: {
        'All good': 'Automatic trips are ready. · Auto-detects most drives. The first few hundred metres may be estimated. Force-quitting pauses auto-detect.',
        Attention: 'Auto-detect needs attention. · Location access changed to "While Using". Automatic trips are paused. Two taps to fix it.',
        'Check-in': 'Last check-in {18 min ago} · {7:12 pm} | Last drive {12:27 pm} · {Friday midday}',
        'Check-in detail': 'DrivePerf checks in each time your phone wakes it: a drive, a reboot, opening the app. Auto-detect re-arms every time. · Only if nothing by {7:12 pm} tomorrow — We’ll remind you to open DrivePerf once',
        Footer: 'Checks run on this phone. Nothing is sent anywhere.',
        CTA: 'Fix it in Health Check',
      },
      freePro: 'Identical on every plan (capture is Free forever). No upsell, badge or paywall on this sheet.',
      data: 'DP_DATA.health.{ios|android}.items with presets allGood (default, lastCheckInTime) and alwaysLost (attention; any health=<preset> param is honoured). Last check-in = presets.allGood.lastCheckIn (19:12 → “18 min ago” at 19:30); last drive = newest trip end (t20261106-1210, 12:27 pm, Friday midday). The watchdog time (check-in + 24 h) is derived.',
      icons: ['health', 'pause', 'chevronRight', 'chevronDown', 'close'],
      assets: [],
    },
  });

  /* ==========================================================================================================
     S-HOME-03 · Pause auto-detect sheet (small)                                              F-CAP-15 · P1
     ========================================================================================================== */
  const PAUSE_OPTS = [
    { id: 'hour', label: 'For 1 hour', sub: 'Until 8:30 pm', until: '8:30 pm', short: 'Until 8:30 pm' },
    { id: 'tomorrow', label: 'Until tomorrow morning', sub: 'Saturday, 8:00', until: '8:00 tomorrow', short: 'Until 8:00 tomorrow' },
    { id: 'manual', label: 'Until I turn it back on', sub: 'Manual drives still work', until: 'you turn it back on', short: 'Until you turn it back on' },
  ];
  screen('S-HOME-03', {
    name: 'Pause auto-detect sheet',
    wp: 'B', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'drives', detent: 'auto',
    over: (ctx) => (ctx.state === 'resume' ? { id: 'S-HOME-01', state: 'autoPaused', params: { capture: 'paused' } } : 'S-HOME-02'),
    states: [{ id: 'default', label: 'Def (choose how long)' }, { id: 'pausedUntil', label: 'Paused until… (confirmation)' }, { id: 'resume', label: 'Resume (opened while paused)' }],
    render(ctx) {
      const { ui, state, store } = ctx;
      const sel = PAUSE_OPTS.find((o) => o.id === (store.sel || 'tomorrow')) || PAUSE_OPTS[1];
      if (state === 'pausedUntil') {
        return `${ui.sheetHeader('Auto-detect paused', { close: false })}<div class="hp">
          <div class="hp-done" role="status"><span class="hp-done-ic">${ui.icon('pause', 26)}</span><div><b>${ui.esc(sel.short)}</b><p>No drives will start on their own. You can still tap Start, and we’ll let you know when it’s back on.</p></div></div>
          ${ui.button('Done', { kind: 'filled', size: 'l', block: true, action: 'done', haptic: 'confirm' })}
          ${ui.button('Undo', { kind: 'text', size: 'm', block: true, action: 'undo' })}</div>`;
      }
      if (state === 'resume') {
        return `${ui.sheetHeader('Auto-detect is paused', { sub: 'Until 8:00 tomorrow' })}<div class="hp">
          <p class="hp-t">While it’s paused, drives only record when you tap Start.</p>
          ${ui.button('Resume auto-detect', { kind: 'filled', size: 'l', block: true, icon: 'play', action: 'resume', haptic: 'confirm' })}
          ${ui.button('Keep paused', { kind: 'tonal', size: 'l', block: true, back: true })}</div>`;
      }
      return `${ui.sheetHeader('Pause auto-detect', { sub: 'No drives start on their own while paused' })}<div class="hp">
        ${ui.radios(PAUSE_OPTS.map((o) => ({ id: o.id, label: o.label, sub: o.sub })), sel.id, { action: 'pick', cls: 'hp-opts' })}
        <p class="hp-t">You can still record a drive with Start. Pausing doesn’t delete anything.</p>
        ${ui.button(`Pause ${sel.id === 'hour' ? 'for 1 hour' : sel.id === 'tomorrow' ? 'until tomorrow' : 'auto-detect'}`, { kind: 'filled', size: 'l', block: true, icon: 'pause', action: 'pause', haptic: 'confirm' })}</div>`;
    },
    mount(el, ctx) { if (ctx.state === 'pausedUntil') ctx.ui.anim.pop(el.querySelector('.hp-done-ic'), 60); },
    actions: {
      pick: (ctx, id) => { ctx.store.sel = id; ctx.update(); return false; },
      pause: (ctx) => { ctx.setState('pausedUntil'); return false; },
      undo: (ctx) => { ctx.setState('default'); return false; },
      done: (ctx) => { ctx.nav('S-HOME-01/autoPaused', { capture: 'paused' }); return false; },
      resume: (ctx) => { ctx.nav('S-HOME-01', { capture: '', health: '' }); ctx.toast('Auto-detect resumed', { icon: 'check' }); return false; },
    },
    spec: {
      purpose: 'Lets someone lending their phone, or wanting privacy, stop automatic trips for a while without turning them off (F-CAP-15). Manual Start keeps working.',
      layout: [
        'Sheet that fits its content (≈ the small detent: iOS .presentationDetents([.height(…)]) glass · Android ModalBottomSheet). Header “Pause auto-detect” + subtitle.',
        'Three radio rows (iOS inset list 52 pt · Android M3 radio ListItems 56 dp): For 1 hour (Until 8:30 pm) · Until tomorrow morning (Saturday, 8:00) · Until I turn it back on (Manual drives still work). Default selection: until tomorrow morning.',
        'Reassurance line (meta, text2): “You can still record a drive with Start. Pausing doesn’t delete anything.”',
        'Primary full-width filled button whose label follows the choice: “Pause until tomorrow” / “Pause for 1 hour” / “Pause auto-detect” (52 iOS / 56 Android).',
        'pausedUntil: pause glyph in a 56 tonal disc + “Until 8:00 tomorrow” + one line; Done (filled) + Undo (text).',
        'resume (opened from the paused accessory, the Home banner or a long-press): “Auto-detect is paused · Until 8:00 tomorrow”, Resume auto-detect (filled, play icon) + Keep paused (tonal).',
      ],
      components: ['sheet', 'sheetHeader', 'radios', 'button'],
      interactions: [
        'Radio row → selects (selection haptic) and relabels the button. Pause → pausedUntil (confirm haptic). Undo → back to the picker.',
        'Done → S-HOME-01/autoPaused with capture=paused (accessory: “Auto-detect paused · Until 8:00 tomorrow · Resume”; Android toolbar the same).',
        'Resume auto-detect → S-HOME-01 (capture cleared) + toast “Auto-detect resumed”. Keep paused → dismiss.',
        'Entry points: S-HOME-02 “Pause auto-detect” row · long-press on the iOS accessory / Android floating toolbar (520 ms, longPress haptic) · Android toolbar overflow. Not reachable during a driver trip (D1).',
      ],
      motion: ['Sheet spring.sheet; radio selection spring.fast; the confirmation glyph pops (scale .86 → 1, spring.default). RM: cross-fades only.', 'Haptics: selection on radio change, confirm on Pause/Resume.'],
      a11y: ['Radio group label “Pause auto-detect”; rows read “For 1 hour, until 8:30 pm, radio button, 1 of 3”.', 'Button label repeats the choice (“Pause until tomorrow”). Confirmation is a status element.', 'XL: the sheet grows to its content (large detent when needed); subtitles wrap.'],
      platformDiffs: { ios: 'Glass sheet, inset-grouped radio rows with checkmark-style radios, glassProminent primary.', android: 'ModalBottomSheet with drag handle, M3 RadioButton ListItems, filled button with shape morph; Undo is a TextButton.' },
      analytics: ['auto_paused {duration: 1h|until_morning|indefinite, source}', 'auto_pause_undone', 'auto_resumed {by: user|timer}'],
      copy: {
        Title: 'Pause auto-detect · No drives start on their own while paused',
        Options: 'For 1 hour (Until 8:30 pm) · Until tomorrow morning (Saturday, 8:00) · Until I turn it back on (Manual drives still work)',
        Reassurance: 'You can still record a drive with Start. Pausing doesn’t delete anything.',
        Confirmation: 'Until 8:00 tomorrow. No drives will start on their own. You can still tap Start, and we’ll let you know when it’s back on.',
        Resume: 'Auto-detect is paused · Until 8:00 tomorrow · While it’s paused, drives only record when you tap Start. · Resume auto-detect · Keep paused',
        Toast: 'Auto-detect resumed',
      },
      freePro: 'Same on every plan.',
      data: '“Until 8:00 tomorrow” = DP_DATA.health.presets.paused.pausedUntil (2026-11-07 08:00); 1 hour = today 19:30 + 60 min. Deviation: PRD F-CAP-15 lists “until tomorrow 06:00”; the prototype uses the fixture’s 8:00 so the sheet, Home banner, accessory and Health Check agree.',
      icons: ['pause', 'play', 'check', 'close'],
      assets: [],
    },
  });

  /* ==========================================================================================================
     In-drive (D1 locked state): S-DRV-01 · S-DRV-02 · S-DRV-03 · S-DRV-04 · S-DRV-05
     Pure black, ≤ 1 content update per second, targets ≥ 64 (Stop / passenger 96), no score, speed, map or tabs.
     Both platforms render it identically on purpose (components.md §19: calm over cosmetics).
     ========================================================================================================== */
  const DRV_LIVE = { default: 'manual', autoTrip: 'auto', gpsSearching: 'gpsSearching', lowBatteryMode: 'lowBattery', passengerModeActive: 'passenger' };
  const DRV_SPOKEN = (l, v) => `Recording. ${Math.floor(l.elapsedS / 60)} minutes${l.gps === 'searching' ? ', finding GPS' : `, ${l.distanceKm} kilometres`}${v ? `, ${v.nickname}` : l.passenger ? ', passenger' : ''}.`;
  /** The big in-drive button (96 tall, full width). kind: stop | outline | go */
  const drvBtn = (ctx, label, icon, kind, o = {}) => `<span class="drv-btn is-${kind} ui-press" ${ctx.ui.act({ label, ...o })}>${icon ? ctx.ui.icon(icon, 26) : ''}<span>${ctx.ui.esc(label)}</span></span>`;
  function drvCar(ctx, l) {
    const v = carOf(ctx, l);
    if (l.passenger) return `<span class="drv-car">${ctx.ui.icon('passenger', 18)}Passenger</span>`;
    return v ? `<span class="drv-car"><i style="background:var(--dp-car-${v.colour})"></i>${ctx.ui.esc(v.nickname)}</span>` : '';
  }

  screen('S-DRV-01', {
    name: 'In-drive locked screen',
    wp: 'B', platforms: ['ios', 'android'], presentation: 'full', tab: null,
    states: [
      { id: 'default', label: 'Manual trip' },
      { id: 'autoTrip', label: 'Auto trip (estimated start)' },
      { id: 'gpsSearching', label: 'GPS searching' },
      { id: 'lowBatteryMode', label: 'Low battery mode (Ioniq)' },
      { id: 'passengerModeActive', label: 'Passenger mode active (content unlocked)' },
    ],
    stateParams: { autoTrip: { live: 'auto' }, gpsSearching: { live: 'gpsSearching' }, lowBatteryMode: { live: 'lowBattery' }, passengerModeActive: { live: 'passenger' } },
    chrome: { tabBar: false, accessory: false },
    statusBar: 'light',
    render(ctx) {
      const { ui, data, state } = ctx;
      const l = liveOf(ctx, DRV_LIVE[state] || 'manual');
      const v = carOf(ctx, l), gps = l.gps === 'searching', passenger = !!l.passenger || state === 'passengerModeActive';
      const start = l.startedAt ? data.time(l.startedAt) : null;
      const src = passenger ? 'Marked as passenger · not scored' : l.source === 'auto' ? `Started automatically${start ? ` · ${start}` : ''}` : `Started by you${start ? ` · ${start}` : ''}`;
      const est = l.estimatedStartM ? `<span class="drv-est">${ui.provenance('estimated', 13)}First ${ui.num(l.estimatedStartM, 'm')} estimated</span>` : '';
      let status = '';
      if (gps) status = `<div class="drv-status" role="status">${ui.icon('gps', 22)}<span><b>Finding GPS…</b><small>Motion is already recording. Distance starts with the first fix.</small></span></div>`;
      else if (l.batterySaver) status = `<div class="drv-status" role="status">${ui.icon('battery', 22)}<span><b>Battery saver: recording at lower detail</b><small>Your route and Story are still saved.</small></span></div>`;
      else if (passenger) status = `<div class="drv-status is-pass" role="status">${ui.icon('passenger', 22)}<span><b>You’re a passenger, so DrivePerf is unlocked.</b><small>This trip won’t be scored or count toward your baseline.</small></span></div>`;
      const dist = gps ? `<b class="drv-dist is-na" aria-hidden="true">—</b>` : `<b class="drv-dist">${ui.esc(data.km(l.distanceKm))}<span>km</span></b>`;
      const acts = passenger
        ? drvBtn(ctx, 'Browse DrivePerf', 'drives', 'go', { nav: 'S-NAV-01/recordingBanner', params: { live: 'passenger' }, haptic: 'tap' }) + drvBtn(ctx, 'End trip', 'stop', 'stop', { nav: 'S-DRV-02', params: { live: 'passenger' } })
        : drvBtn(ctx, 'Stop', 'stop', 'stop', { nav: 'S-DRV-02' }) + drvBtn(ctx, 'I’m a passenger', 'passenger', 'outline', { nav: 'S-DRV-03' });
      return ui.page(`<div class="drv ${passenger ? 'is-passenger' : ''}" aria-label="${ui.esc(DRV_SPOKEN(l, v))}">
          <div class="drv-top" data-rise><span class="drv-rec"><i class="drv-dot" aria-hidden="true"></i><b>Recording</b></span>${drvCar(ctx, l)}</div>
          <p class="drv-src" data-rise>${ui.esc(src)}${est}</p>
          <div class="drv-nums" data-rise aria-hidden="true">
            <div class="drv-n"><span class="drv-k">Elapsed</span><b class="drv-time" data-clock="${l.elapsedS}">${ui.esc(data.clock(l.elapsedS))}</b></div>
            <div class="drv-n"><span class="drv-k">Distance</span>${dist}</div>
          </div>
          ${status ? `<div data-rise>${status}</div>` : ''}
          <div class="drv-acts">${acts}</div>
        </div>`, { cls: 'drv-page' });
    },
    mount(el, ctx) {
      if (ctx.shot) return undefined;
      if (ctx.state === 'default') ctx.ui.anim.rise(el, '[data-rise]', { stagger: 60, delay: 360 });
      const t = el.querySelector('[data-clock]');
      let s = +t.dataset.clock;
      const id = setInterval(() => { s += 1; t.textContent = ctx.data.clock(s); }, 1000);
      // any touch restarts the 10 s pulse (motion.md §3.2)
      const dot = el.querySelector('.drv-dot');
      const re = () => { dot.classList.remove('is-pulse'); void dot.offsetWidth; dot.classList.add('is-pulse'); };
      re(); el.addEventListener('pointerdown', re);
      return () => { clearInterval(id); el.removeEventListener('pointerdown', re); };
    },
    spec: {
      purpose: 'The only screen the app shows while a driver trip is active (D1, F-CAP-07): proof that it is recording — elapsed time, distance and car — plus two huge exits, Stop and I’m a passenger. Nothing else: no score, speed, events, tips, map, navigation or tab bar.',
      layout: [
        'Full-screen cover on --dp-drive-bg #000 on both platforms and in both themes (status bar light). Content is a single column with 24 side margins.',
        'Top row (safe top + 18): 12 px live dot (--wb-drv-live = Glide #5BE7C4) + “Recording” (600 24/28) · car chip trailing (car dot in the car colour + nickname, 600 17, #F2F4F7 on #ffffff14, capsule 40 tall) or a “Passenger” chip.',
        'Source line (meta 15/20, #A3AAB6): “Started by you · 5:31 pm” / “Started automatically · 5:31 pm” + “First 340 m estimated” with the estimated provenance glyph (auto trips) / “Marked as passenger · not scored”.',
        'Numbers block, vertically centred in the free space: label “Elapsed” (caps 13, #A3AAB6) over the timer in DP Numerals 96/96 (tabular, 125% stretch, scales with Dynamic Type up to the container width), then “Distance” over 9.6 + “km” at 50%. GPS searching shows “—”. Times ≥ 1 h render h:mm:ss.',
        'Status card when needed (radius 20, #ffffff10, 16 padding, 22 icon): Finding GPS… · Battery saver: recording at lower detail · You’re a passenger, so DrivePerf is unlocked.',
        'Bottom stack (safe bottom + 16, 12 gap): Stop = 96 tall, full width, radius 28, --dp-surface-3 (#262E3B) with a 26 stop glyph and 600 22 label · I’m a passenger = 96 tall, 1.5 px #3A4351 outline. Passenger mode: “Browse DrivePerf” (Glide filled, on-accent text) + “End trip”.',
      ],
      components: ['page', 'drvButton (local, 96 tall)', 'carChip (in-drive variant)', 'provenance', 'liveDot'],
      interactions: [
        'Stop → S-DRV-02 (sheet). I’m a passenger → S-DRV-03 (sheet). Both are the only destinations allowed by the D1 gate besides S-DRV-05.',
        'Passenger mode: Browse DrivePerf → S-NAV-01 recording banner (live=passenger, content unlocked); End trip → S-DRV-02 (passenger copy).',
        'Every other deep link, notification, widget, Control Center / QS tile and Live Activity tap lands here while a driver trip is active (DP.gate; the target waits in DP.session.pending and opens on S-DRV-05 rules after the trip).',
        'Auto end (stationary for the stop timer, F-CAP-03) → S-DRV-04 with no tap. There is no swipe, back gesture or tab bar.',
      ],
      motion: [
        'Manual start: the accessory (iOS) / floating toolbar (Android) expands into this screen (container transform, spring.slow), then the rows rise 8 px with a 60 ms stagger (spring.default) — motion.md §3.1. Auto trip: 220 ms cross-fade, no theatre.',
        'Live dot: halo scale 1 → 1.9, opacity .5 → 0, 900 ms ease.glide once per second for 10 s after appearing or after any touch, then still (§3.2). The timer updates once per second; nothing else moves. RM / shot: static dot.',
        'Haptics: none on this screen (confirm fires only on the Stop / passenger confirmation in the sheets).',
      ],
      a11y: [
        'One summary element: “Recording. 14 minutes, 9.6 kilometres, GTI.” — no live region, so the timer is never announced every second.',
        'Buttons: “Stop” and “I’m a passenger” (96 tall, ≥ 64 rule); order: summary → Stop → I’m a passenger.',
        'XL: numerals scale but are capped to the screen width (never truncated); the status card wraps; buttons keep 96 minimum height and grow with the label.',
        'Contrast: #F2F4F7 on #000 (19:1), #A3AAB6 on #000 (8.7:1). Meaning never by colour: the dot is paired with the word Recording.',
      ],
      platformDiffs: { ios: '.fullScreenCover with .interactiveDismissDisabled(); .persistentSystemOverlays(.hidden) not used (status bar stays for the recording indicator); Live Activity runs alongside (S-LIVE-01/02).', android: 'Full-screen destination with BackHandler consumed (back does nothing while driving); FLAG_KEEP_SCREEN_ON optional; the Live Update / FGS notification runs alongside (S-LIVE-05/06).' },
      analytics: ['in_drive_screen_viewed {mode: manual|auto, gps, battery_saver}', 'in_drive_stop_tapped', 'in_drive_passenger_tapped', 'content_blocked_while_moving {target} (from the gate)'],
      copy: {
        Title: 'Recording',
        Manual: 'Started by you · {5:31 pm}', Auto: 'Started automatically · {5:31 pm} · First {340} m estimated',
        GPS: 'Finding GPS… · Motion is already recording. Distance starts with the first fix.',
        'Low battery': 'Battery saver: recording at lower detail · Your route and Story are still saved.',
        Passenger: 'You’re a passenger, so DrivePerf is unlocked. · This trip won’t be scored or count toward your baseline.',
        Buttons: 'Stop · I’m a passenger · Browse DrivePerf · End trip',
      },
      freePro: 'Identical for every plan. No upsell, badge, paywall or share prompt can appear while this screen is up (D12).',
      data: 'DP_DATA.liveTrip.states: manual (default, 14:24 · 9.6 km · GTI) · auto (21:50 · 17.2 km · estimatedStartM 340) · gpsSearching (0:22, no distance) · lowBattery (38:00 · 31.4 km · Ioniq, batterySaver) · passenger (10:40 · 6.1 km). Start time = startedAt (5:31 pm). Only these fields are used (fixtures.md §8). The prototype timer ticks from elapsedS once per second.',
      icons: ['stop', 'passenger', 'gps', 'battery', 'drives'],
      assets: [],
    },
  });

  screen('S-DRV-02', {
    name: 'End trip sheet (Stop confirmation)',
    wp: 'B', platforms: ['ios', 'android'], presentation: 'sheet', tab: null, detent: 'auto',
    over: (ctx) => ({ id: 'S-DRV-01', state: ctx.params.live === 'passenger' ? 'passengerModeActive' : ctx.state === 'shortTripWarning' ? 'gpsSearching' : 'default' }),
    states: [{ id: 'default', label: 'Def' }, { id: 'shortTripWarning', label: 'Short-trip warning' }],
    stateParams: { shortTripWarning: { live: 'gpsSearching' } },
    statusBar: 'light',
    render(ctx) {
      const { ui, data, state } = ctx;
      const l = liveOf(ctx, state === 'shortTripWarning' ? 'gpsSearching' : 'manual');
      const v = carOf(ctx, l), passenger = !!l.passenger, short = state === 'shortTripWarning';
      const facts = `<div class="ds-facts" aria-label="${ui.esc(`${Math.floor(l.elapsedS / 60)} minutes ${l.elapsedS % 60} seconds, ${l.distanceKm} kilometres so far`)}"><span>${ui.num(data.clock(l.elapsedS))}</span><i>·</i><span>${ui.num(data.km(l.distanceKm), 'km')}</span>${v ? `<i>·</i><span class="ds-car"><b style="background:var(--dp-car-${v.colour})"></b>${ui.esc(v.nickname)}</span>` : passenger ? `<i>·</i><span>Passenger</span>` : ''}</div>`;
      const note = short
        ? ui.banner({ kind: 'info', icon: 'info', title: 'This drive is very short', text: 'Under 500 m or 2 minutes. It’s kept with a “Short trip” label, without a score.' })
        : `<p class="ds-t">${passenger ? 'It’s saved as a passenger trip, without a score.' : 'We’ll decode it now. Your Story will be ready when you’ve parked.'}</p>`;
      return `${ui.sheetHeader(passenger ? 'End this trip?' : 'End this drive?', { close: false })}<div class="ds">${facts}${note}
        <div class="ds-acts">${ui.button(short ? 'End anyway' : passenger ? 'End trip' : 'End drive', { kind: 'filled', size: 'l', block: true, icon: 'stop', action: 'end', haptic: 'confirm', cls: 'ds-btn' })}${ui.button('Keep recording', { kind: 'tonal', size: 'l', block: true, back: true, cls: 'ds-btn' })}</div></div>`;
    },
    actions: {
      end: (ctx) => {
        const l = liveOf(ctx, ctx.state === 'shortTripWarning' ? 'gpsSearching' : 'manual');
        if (l.passenger) { ctx.nav('S-HOME-01', { live: '', moving: '' }); ctx.toast('Passenger trip saved · not scored', { icon: 'passenger' }); }
        else if (ctx.state === 'shortTripWarning') { ctx.nav('S-HOME-01', { live: '', moving: '' }); ctx.toast('Short trip saved · too short to score', { icon: 'check' }); }
        else ctx.nav('S-DRV-04', { live: '', trip: HERO_TRIP }, { replace: true });
        return false;
      },
    },
    spec: {
      purpose: 'A deliberate second step before a manual trip ends, so a mis-tap on the in-drive Stop never ends a drive (F-CAP-01). It also explains short trips (F-CAP-17).',
      layout: [
        'Sheet over S-DRV-01, fitted to content (≈ small detent). Header “End this drive?” (passenger trip: “End this trip?”), no close button — the two buttons are the only exits besides drag/scrim.',
        'Facts row (DP Numerals 22, text2 separators): 14:24 · 9.6 km · ● GTI.',
        'One line (body, text2): “We’ll decode it now. Your Story will be ready when you’ve parked.” · short-trip: info banner “This drive is very short — Under 500 m or 2 minutes. It’s kept with a “Short trip” label, without a score.”',
        'Two full-width buttons, 64 tall (in-drive minimum), 12 gap: End drive (filled Glide, stop glyph) · Keep recording (tonal).',
      ],
      components: ['sheet', 'sheetHeader', 'banner', 'button', 'num'],
      interactions: [
        'End drive → S-DRV-04 (Decoding your drive), clearing live (the D1 gate unlocks); confirm haptic (the only haptic allowed during a trip).',
        'End anyway (short) → trip kept as “Short trip”, back to S-HOME-01 with a toast. End trip (passenger) → S-HOME-01, toast “Passenger trip saved · not scored”.',
        'Keep recording / scrim / drag down → back to S-DRV-01, nothing changes.',
      ],
      motion: ['spring.sheet in from the bottom over the black screen; buttons press-scale .97 (spring.fast). Then (motion.md §3.1 trip end) the S-DRV-01 dot’s trail retracts while S-DRV-04 cross-fades in. RM: cross-fade.'],
      a11y: ['Facts read as one: “14 minutes 24 seconds, 9.6 kilometres so far”. Buttons “End drive” / “Keep recording”, 64 tall.', 'Focus starts on the title, then Keep recording is the escape (Android back = Keep recording).', 'XL: facts wrap onto two lines; buttons grow with the label.'],
      platformDiffs: { ios: 'Glass sheet over the black in-drive screen, .presentationDetents([.height(…)]), glassProminent End drive.', android: 'ModalBottomSheet with drag handle; filled End drive with shape morph, FilledTonalButton Keep recording.' },
      analytics: ['trip_stop_confirm_viewed {short: bool, passenger: bool}', 'trip_stopped {by: user, short: bool}', 'trip_stop_cancelled'],
      copy: { Title: 'End this drive? / End this trip?', Line: 'We’ll decode it now. Your Story will be ready when you’ve parked.', Passenger: 'It’s saved as a passenger trip, without a score.', Short: 'This drive is very short · Under 500 m or 2 minutes. It’s kept with a “Short trip” label, without a score.', Buttons: 'End drive · End anyway · End trip · Keep recording' },
      freePro: 'Same for every plan.',
      data: 'Facts from DP_DATA.liveTrip.states.manual (default) and gpsSearching (short-trip: 0:22, 0.0 km — the only fixture under the 500 m / 2 min rule). End drive decodes the hero fixture t20261105-0742 (there is no finalised trip that matches the live fixture).',
      icons: ['stop', 'info', 'passenger', 'check'],
      assets: [],
    },
  });

  screen('S-DRV-03', {
    name: '“I’m a passenger” sheet',
    wp: 'B', platforms: ['ios', 'android'], presentation: 'sheet', tab: null, detent: 'auto',
    over: 'S-DRV-01',
    states: [{ id: 'default', label: 'Def' }, { id: 'keepAsPassenger', label: 'Keep as passenger (confirmed)' }, { id: 'dontSave', label: 'Don’t save (confirm delete)' }],
    statusBar: 'light',
    render(ctx) {
      const { ui, state } = ctx;
      if (state === 'keepAsPassenger') {
        return `${ui.sheetHeader('You’re a passenger', { close: false })}<div class="ds">
          <div class="ds-ok" role="status"><span class="ds-ok-ic">${ui.icon('passenger', 28)}</span><p>This trip won’t be scored or count toward your baseline, routes or recaps. Recording continues until the trip ends, and DrivePerf is unlocked.</p></div>
          <div class="ds-acts">${ui.button('Done', { kind: 'filled', size: 'l', block: true, action: 'keep', haptic: 'confirm', cls: 'ds-btn' })}</div></div>`;
      }
      if (state === 'dontSave') {
        return `${ui.sheetHeader('Don’t save this trip?', { close: false })}<div class="ds">
          <p class="ds-t">Recording stops now and everything from this trip is deleted within a minute. This can’t be undone.</p>
          <div class="ds-acts">${ui.button('Delete this trip', { kind: 'destructive', size: 'l', block: true, icon: 'trash', action: 'discard', haptic: 'confirm', cls: 'ds-btn' })}${ui.button('Keep recording', { kind: 'tonal', size: 'l', block: true, back: true, cls: 'ds-btn' })}</div></div>`;
      }
      const opt = (id, icon, title, sub, o) => `<div class="ds-opt ${o.danger ? 'is-danger' : ''} ui-press" ${ui.act({ action: id, label: `${title}. ${sub}` })}><span class="ds-opt-ic">${ui.icon(icon, 24)}</span><span class="ds-opt-t"><b>${title}</b><small>${sub}</small></span>${ui.icon('chevronRight', 18)}</div>`;
      return `${ui.sheetHeader('Not driving?', { sub: 'Only if someone else is at the wheel.', close: false })}<div class="ds">
        ${opt('pickKeep', 'passenger', 'Keep as a passenger trip', 'Saved without a score. DrivePerf unlocks.', {})}
        ${opt('pickDiscard', 'trash', 'Don’t save this trip', 'Recording stops and the data is deleted.', { danger: true })}
        <div class="ds-acts">${ui.button('Cancel', { kind: 'tonal', size: 'l', block: true, back: true, cls: 'ds-btn' })}</div></div>`;
    },
    actions: {
      pickKeep: (ctx) => { ctx.setState('keepAsPassenger'); return false; },
      pickDiscard: (ctx) => { ctx.setState('dontSave'); return false; },
      keep: (ctx) => { ctx.nav('S-NAV-01/recordingBanner', { live: 'passenger', moving: '' }); return false; },
      discard: (ctx) => { ctx.nav('S-HOME-01', { live: '', moving: '' }); ctx.toast('Trip not saved. Nothing was kept.', { icon: 'trash' }); return false; },
    },
    spec: {
      purpose: 'The one-tap escape for passengers (F-CAP-08, D14): mark the trip as not driven so it isn’t scored and the app unlocks, or throw the trip away.',
      layout: [
        'Sheet over S-DRV-01 or S-DRV-05, fitted to content. Header “Not driving?” + “Only if someone else is at the wheel.”',
        'Two option rows, 76 tall, radius 20, --tile-bg-in: passenger glyph in a 44 tonal disc + “Keep as a passenger trip / Saved without a score. DrivePerf unlocks.” · trash glyph (critical tint) + “Don’t save this trip / Recording stops and the data is deleted.” Trailing chevron.',
        'Cancel: full-width tonal button, 64 tall.',
        'keepAsPassenger: passenger glyph in a 56 disc + explanation + Done (filled, 64). dontSave: explanation + “Delete this trip” (destructive, trash glyph, 64) + Keep recording (tonal).',
      ],
      components: ['sheet', 'sheetHeader', 'button', 'optionRow (local)'],
      interactions: [
        'Keep as a passenger trip → keepAsPassenger; Done → S-NAV-01 recording banner with live=passenger (gate lets content through; shell shows “Recording · Passenger trip”).',
        'Don’t save this trip → dontSave (a second, explicit step because it deletes); Delete this trip → recording stops, samples deleted within 1 min, S-HOME-01 + toast “Trip not saved. Nothing was kept.” Keep recording → S-DRV-01.',
        'Cancel / scrim / drag → back to S-DRV-01 or S-DRV-05. Confirm haptic on Done and Delete only.',
      ],
      motion: ['spring.sheet; state changes cross-fade inside the sheet (180 ms) and the sheet height animates to fit (system detent spring). RM: cross-fade, no height spring.'],
      a11y: ['Option rows are buttons: “Keep as a passenger trip. Saved without a score. DrivePerf unlocks.”', 'Destructive copy says what is lost (“deleted within a minute. This can’t be undone.”); the delete button is not the first focus.', 'Targets ≥ 64 (in-drive). XL: rows grow; subtitles wrap.'],
      platformDiffs: { ios: 'Glass sheet; option rows as glass-on-glass tiles; destructive button uses the .destructive role.', android: 'ModalBottomSheet; option rows as M3E segmented ListItems on surfaceContainerHigh; destructive = FilledTonalButton with error colours.' },
      analytics: ['passenger_sheet_opened {from: in_drive|held}', 'passenger_marked {when: during, keep: true}', 'trip_discarded {by: passenger_sheet}'],
      copy: { Title: 'Not driving? · Only if someone else is at the wheel.', Keep: 'Keep as a passenger trip · Saved without a score. DrivePerf unlocks.', Discard: 'Don’t save this trip · Recording stops and the data is deleted.', Confirmed: 'You’re a passenger · This trip won’t be scored or count toward your baseline, routes or recaps. Recording continues until the trip ends, and DrivePerf is unlocked.', 'Delete confirm': 'Don’t save this trip? · Recording stops now and everything from this trip is deleted within a minute. This can’t be undone. · Delete this trip · Keep recording', Toast: 'Trip not saved. Nothing was kept.' },
      freePro: 'Same for every plan.',
      data: 'No trip data shown; the passenger outcome uses DP_DATA.liveTrip.states.passenger for the shell banner.',
      icons: ['passenger', 'trash', 'chevronRight'],
      assets: [],
    },
  });

  const DECODE_STEPS = ['Saving the recording', 'Lining up your phone with the car', 'Finding the moments that matter', 'Building your Story'];
  function openStory(ctx) {
    const trip = ctx.params.trip || HERO_TRIP;
    Promise.resolve(ctx.nav('S-HOME-01/newStoryCard', { trip })).then(() => ctx.nav('S-STY-01', { trip }));
  }
  screen('S-DRV-04', {
    name: '“Decoding your drive” (processing)',
    wp: 'B', platforms: ['ios', 'android'], presentation: 'full', tab: null,
    states: [{ id: 'default', label: 'Load (decoding, step 3 of 4)' }, { id: 'done', label: 'Done → Story' }, { id: 'Err', label: 'Err (processing failed, retry)' }],
    stateParams: { default: { trip: HERO_TRIP }, done: { trip: HERO_TRIP }, Err: { trip: HERO_TRIP } },
    chrome: { tabBar: false, accessory: false },
    statusBar: 'light',
    render(ctx) {
      const { ui, data, state, icons } = ctx;
      const t = data.trip(ctx.params.trip) || data.trip(HERO_TRIP), v = data.vehicle(t.vehicleId);
      const car = v ? `<span class="dd-car"><i style="background:var(--dp-car-${v.colour})"></i>${ui.esc(v.nickname)}</span>` : '';
      if (state === 'Err') {
        return ui.page(`<div class="dd is-err"><div class="dd-art"><span class="dd-err-ic">${ui.icon('warn', 44)}</span></div>
          <h1>Couldn’t decode this drive.</h1><p>Your recording is safe on this phone. We’ll try again on its own, or you can retry now.</p>
          <div class="dd-acts">${ui.button('Try again', { kind: 'filled', size: 'l', block: true, icon: 'refresh', state: 'default', haptic: 'tap' })}${ui.button('Back to Drives', { kind: 'text', size: 'm', block: true, action: 'home' })}</div></div>`, { cls: 'dd-page' });
      }
      if (state === 'done') {
        return ui.page(`<div class="dd is-done"><div class="dd-art"><span class="dd-ring is-done">${icons.loop({ size: 132, color: '#5BE7C4', dot: '#fff' })}<b class="dd-check">${ui.icon('check', 30)}</b></span></div>
          <span class="dd-kick"><i></i>Drive decoded</span><h1>${ui.esc(t.label)}</h1><p>${ui.esc(t.partOfDay)} · ${ui.num(data.km(t.distanceKm), 'km')} · ${ui.num(Math.round(t.durationS / 60), 'min')}</p>${car}
          <div class="dd-acts">${ui.button('Watch your Story', { kind: 'filled', size: 'l', block: true, icon: 'story', action: 'story', haptic: 'tap' })}${ui.button('Later', { kind: 'text', size: 'm', block: true, action: 'later' })}</div></div>`, { cls: 'dd-page' });
      }
      const k = 2; // static frame: 2 done, step 3 running (motion.md §5 RM copy “Decoding… 3 of 4 steps”)
      const steps = DECODE_STEPS.map((s, i) => `<li class="${i < k ? 'is-done' : i === k ? 'is-on' : ''}" data-step="${i}"><span class="dd-st-ic" aria-hidden="true">${i < k ? ui.icon('check', 14) : ''}</span>${ui.esc(s)}</li>`).join('');
      const loader = ctx.ios ? `<span class="dd-ring is-spin" role="progressbar" aria-label="Decoding your drive">${icons.loop({ size: 132, color: '#5BE7C4', dot: '#fff' })}</span>` : `<span class="dd-ring is-m3" role="progressbar" aria-label="Decoding your drive"><span class="dd-m3">${ui.loader({ size: 92, label: 'Decoding your drive' })}</span></span>`;
      return ui.page(`<div class="dd" aria-busy="true"><div class="dd-art">${loader}</div>
        <h1>Decoding your drive…</h1><p class="dd-sub" data-count>Step ${k + 1} of ${DECODE_STEPS.length}</p>${car}
        <ol class="dd-steps">${steps}</ol>
        <p class="dd-note">Usually under 10 seconds. You can leave; we’ll tell you when it’s ready.</p>
        <div class="dd-acts">${ui.button('Back to Drives', { kind: 'text', size: 'm', block: true, action: 'leave' })}</div></div>`, { cls: 'dd-page' });
    },
    mount(el, ctx) {
      if (ctx.shot) return undefined;
      const timers = [];
      if (ctx.state === 'default') {
        const lis = [...el.querySelectorAll('.dd-steps li')], cnt = el.querySelector('[data-count]');
        const set = (k) => { lis.forEach((li, i) => { li.className = i < k ? 'is-done' : i === k ? 'is-on' : ''; li.querySelector('.dd-st-ic').innerHTML = i < k ? ctx.ui.icon('check', 14) : ''; }); if (cnt) cnt.textContent = `Step ${Math.min(k + 1, lis.length)} of ${lis.length}`; };
        set(0);
        [700, 1400, 2200, 2900].forEach((ms, i) => timers.push(setTimeout(() => set(i + 1), ms)));
        timers.push(setTimeout(() => { ctx.store.auto = true; ctx.setState('done'); }, 3400));
      }
      if (ctx.state === 'done') {
        ctx.ui.anim.pop(el.querySelector('.dd-check'), 80);
        ctx.ui.anim.rise(el, '.dd-kick, .dd h1, .dd p, .dd-car', { stagger: 50, delay: 120 });
        if (ctx.store.auto) timers.push(setTimeout(() => openStory(ctx), 1500));
      }
      return () => timers.forEach(clearTimeout);
    },
    actions: {
      story: (ctx) => { openStory(ctx); return false; },
      later: (ctx) => { ctx.nav('S-HOME-01/newStoryCard', { trip: ctx.params.trip || HERO_TRIP }); return false; },
      leave: (ctx) => { ctx.nav('S-HOME-01/processingATrip'); return false; },
      home: (ctx) => { ctx.goTab('drives'); return false; },
    },
    spec: {
      purpose: 'Bridges Stop (or an auto trip ending with the app open) and the Story: the drive is finalised, aligned, scored and turned into a Story in ≤ 10 s (F-CAP-01, F-CAP-03, F-STY-01, Q18). It never shows a score itself.',
      layout: [
        'Full-screen cover, dark on both themes (#000 → Night gradient at 40% height), status bar light. Single centred column, 32 side margins.',
        'Loader 132: iOS = the Loop, its dot orbiting the trail at 1.6 s/rev (linear) · Android = M3E ContainedLoadingIndicator (7-shape morph) on a 132 primaryContainer disc.',
        'Title “Decoding your drive…” (story headline 28/34, #F2F4F7), sub “Step 3 of 4” (meta, #A3AAB6), car chip (car dot + nickname).',
        'Steps list (4 rows, 15/20): ✓ Saving the recording · ✓ Lining up your phone with the car · ● Finding the moments that matter · ○ Building your Story. Done rows #A3AAB6 with a Glide check; the running row is white with a Glide ring.',
        'Footnote “Usually under 10 seconds. You can leave; we’ll tell you when it’s ready.” + text button “Back to Drives”.',
        'done: the Loop with a Glide check badge, kicker “● Drive decoded”, route label (card title 24), part of day · km · min, car chip, “Watch your Story” (filled 52/56) + “Later”.',
        'Err: warning glyph in a 96 tonal disc, “Couldn’t decode this drive.”, reassurance, Try again (filled, refresh icon) + Back to Drives.',
      ],
      components: ['page', 'loader', 'button', 'num', 'carChip'],
      interactions: [
        'Load → done automatically when finalisation completes (prototype: 3.4 s), then the Story opens by itself 1.5 s later; tapping Watch your Story opens it at once. Story goes through the D1 gate: if the phone is still moving (> 10 km/h or automotive) it lands on S-DRV-05 instead.',
        'Later → S-HOME-01 with the new Story card. Back to Drives (while decoding) → S-HOME-01 “processing a trip” state; the “Drive decoded.” notification (S-NTF-01) follows if the app is backgrounded.',
        'Err: Try again → Load; Back to Drives → S-HOME-01 (the trip shows as “Couldn’t decode · Try again” there).',
        'In the prototype Done opens S-HOME-01/newStoryCard underneath, then S-STY-01, so closing the Story lands on Home.',
      ],
      motion: [
        'Entry from S-DRV-02: the in-drive dot’s trail retracts (450 ms), the decoding loader runs (iOS Loop orbit 1.6 s/rev · Android shape morph 1.4 s). Steps tick over with a check pop (spring.fast).',
        'Done: the loader ring becomes the Story progress bar (shared element, spring.default) — prototype: check badge pops, text rises 8 px (50 ms stagger).',
        'RM: static Loop, “Step 3 of 4” text only, cross-fade to done. Haptic tap on Watch your Story; none on auto-open.',
      ],
      a11y: ['Loader is a progressbar “Decoding your drive”; the step counter is announced when it changes (polite), not every frame.', 'Done reads “Drive decoded. Home → Work, Thursday morning, 37.9 kilometres, 30 minutes. Watch your Story, button.”', 'XL: steps wrap; buttons grow; loader stays 132.'],
      platformDiffs: { ios: '.fullScreenCover; the Loop loader (brand.md §3.1) animated with a TimelineView rotation; glassProminent primary.', android: 'Full-screen destination; ContainedLoadingIndicator (M3E) and filled button with shape morph; predictive back = Back to Drives.' },
      analytics: ['trip_finalise_ms {bucket}', 'decoding_screen_viewed {source: stop|auto_end}', 'story_opened {source: decoding, auto: bool}', 'decode_failed {reason}', 'decode_retry_tapped'],
      copy: { Load: 'Decoding your drive… · Step {3} of 4 · Saving the recording · Lining up your phone with the car · Finding the moments that matter · Building your Story', Note: 'Usually under 10 seconds. You can leave; we’ll tell you when it’s ready.', Done: 'Drive decoded · {Home → Work} · {Thursday morning} · {37.9 km} · {30 min} · Watch your Story · Later', Err: 'Couldn’t decode this drive. · Your recording is safe on this phone. We’ll try again on its own, or you can retry now. · Try again · Back to Drives' },
      freePro: 'Same for every plan (the Story is Free forever). No paywall or share prompt here or right after (D12).',
      data: 'Decodes trip param (default t20261105-0742, Home → Work, Thursday morning, 37.9 km, 30 min, GTI). The score is deliberately not shown: it is revealed by the Story’s ring frame.',
      icons: ['check', 'story', 'warn', 'refresh'],
      assets: ['logo-a-loop.svg'],
    },
  });

  const HELD_NAME = (id) => (/^S-STY/.test(id) ? 'Story' : /^S-RPL/.test(id) ? 'Replay' : /^S-ANL/.test(id) ? 'Analysis' : /^S-RCP/.test(id) ? 'weekly recap' : /^S-WRP/.test(id) ? 'Wrapped' : /^S-SHR/.test(id) ? 'share card' : null);
  screen('S-DRV-05', {
    name: '“Your Story will be ready when you’ve parked”',
    wp: 'B', platforms: ['ios', 'android'], presentation: 'full', tab: null,
    states: [{ id: 'default', label: 'Def (held while moving)' }, { id: 'autoOpensWhenStationary', label: 'Auto-opens when stationary' }],
    chrome: { tabBar: false, accessory: false },
    statusBar: 'light',
    render(ctx) {
      const { ui, icons, state } = ctx;
      const p = window.DP.session && DP.session.pending;
      const what = (p && HELD_NAME(p.id)) || 'Story';
      const title = what === 'Story' ? 'Your Story will be ready when you’ve parked.' : `Your ${what} will be ready when you’ve parked.`;
      if (state === 'autoOpensWhenStationary') {
        return ui.page(`<div class="drv drv5 is-open"><div class="drv5-mid"><span class="drv5-art is-open">${icons.loop({ size: 96, color: '#5BE7C4', dot: '#fff' })}<svg class="drv5-prog" viewBox="0 0 140 140" aria-hidden="true"><circle cx="70" cy="70" r="64" fill="none" stroke="#ffffff1f" stroke-width="4"/><circle cx="70" cy="70" r="64" fill="none" stroke="#5BE7C4" stroke-width="4" stroke-linecap="round" stroke-dasharray="402 402" transform="rotate(-90 70 70)"/></svg></span>
          <h1>You’ve parked.</h1><p>Opening your ${ui.esc(what)}…</p></div>
          <div class="drv-acts">${drvBtn(ctx, `Open ${what} now`, 'story', 'go', { action: 'open', haptic: 'tap' })}</div></div>`, { cls: 'drv-page' });
      }
      return ui.page(`<div class="drv drv5"><div class="drv5-mid"><span class="drv5-art">${icons.loop({ size: 96, color: '#5BE7C4', dot: '#fff' })}</span>
          <h1>${ui.esc(title)}</h1><p>It opens by itself after a minute stopped. Nothing to tap until then.</p></div>
          <div class="drv-acts">${drvBtn(ctx, 'I’m a passenger', 'passenger', 'outline', { nav: 'S-DRV-03' })}</div></div>`, { cls: 'drv-page' });
    },
    mount(el, ctx) {
      if (ctx.shot) return undefined;
      const timers = [];
      if (ctx.state === 'default' && (DP.session.pending || ctx.params.moving === '1')) timers.push(setTimeout(() => { ctx.store.auto = true; ctx.setState('autoOpensWhenStationary'); }, 9000));
      if (ctx.state === 'autoOpensWhenStationary') {
        const arc = el.querySelector('.drv5-prog circle:last-child');
        if (arc && !ctx.motion.reduced(el)) arc.animate([{ strokeDasharray: '300 402' }, { strokeDasharray: '402 402' }], { duration: 2200, easing: 'linear', fill: 'both' });
        if (ctx.store.auto || DP.session.pending) timers.push(setTimeout(() => openHeld(ctx), 2400));
      }
      return () => timers.forEach(clearTimeout);
    },
    actions: { open: (ctx) => { openHeld(ctx); return false; } },
    spec: {
      purpose: 'What anyone sees when they ask for a Story, Replay, Analysis, recap, Wrapped, share card or paywall while the phone is moving or a driver trip is active (F-CAP-07, F-STY-05, D1/D12). It refuses calmly and opens the content by itself once parked for 60 s.',
      layout: [
        'Full-screen cover, #000 on both themes, status bar light, no navigation. Content centred in the free space; bottom button stack like S-DRV-01.',
        'The Loop mark 96 (static, Glide) above the title “Your Story will be ready when you’ve parked.” (story headline 28/34, #F2F4F7, max 300 wide) and “It opens by itself after a minute stopped. Nothing to tap until then.” (body, #A3AAB6). When something other than a Story was held, the title names it (“Your Replay will be ready when you’ve parked.”); a held paywall or share prompt is never named — it shows the Story copy.',
        'Bottom: “I’m a passenger” 96 tall, outlined (D14).',
        'autoOpensWhenStationary: the Loop inside a 140 Glide progress ring that completes, “You’ve parked.” + “Opening your Story…”, and a 96 “Open Story now” filled button for an immediate open.',
      ],
      components: ['page', 'drvButton (local)', 'loop'],
      interactions: [
        'Reached only via the D1 gate (DP.gate: moving=1 + content target, or any content during a driver trip); the held target waits in DP.session.pending.',
        'Stationary / not automotive for 60 s → autoOpensWhenStationary → the held target opens automatically (prototype: 9 s simulated, then 2.4 s), with moving cleared. Open … now opens it immediately.',
        'I’m a passenger → S-DRV-03; “Keep as passenger” unlocks content right away.',
        'Paywalls (S-SUB-02/03/04) and share prompts held here are dropped, not reopened: S-SUB-02 waits for the next parked foreground open (D12).',
      ],
      motion: ['Nothing moves while held (in-drive rule). Stationary: the progress ring completes linearly (2.2 s), then the held content opens with its own transition. RM: no ring animation, instant open after the delay.', 'Haptic: none on auto-open; tap on “Open now”.'],
      a11y: ['Title is the first focus and reads in full. “I’m a passenger” button 96 tall.', 'The opening state announces “You’ve parked. Opening your Story.” once (polite).', 'XL: title wraps over 3–4 lines; the Loop shrinks to 72 before the text does.'],
      platformDiffs: { ios: '.fullScreenCover with interactive dismiss disabled.', android: 'Full-screen destination; system back is consumed while held.' },
      analytics: ['content_blocked_while_moving {target}', 'held_content_opened {target, by: auto|tap, wait_s}', 'passenger_marked {when: held}'],
      copy: { Title: 'Your Story will be ready when you’ve parked. / Your {Replay} will be ready when you’ve parked.', Line: 'It opens by itself after a minute stopped. Nothing to tap until then.', Opening: 'You’ve parked. · Opening your Story… · Open Story now', Button: 'I’m a passenger' },
      freePro: 'Same for every plan. A held paywall is never shown afterwards in this session (D12, F-SUB-08).',
      data: 'Default held target when none is pending: S-STY-01 for t20261105-0742 (the newest Story). Reads DP.session.pending / gateReason set by the gate.',
      icons: ['passenger', 'story'],
      assets: ['logo-a-loop.svg'],
    },
  });
  function openHeld(ctx) {
    const p = DP.session.pending;
    DP.session.pending = null; DP.session.gateReason = null;
    const target = p ? `${p.id}${p.state && p.state !== 'default' ? '/' + p.state : ''}` : 'S-STY-01';
    const params = { ...(p ? p.params : { trip: HERO_TRIP }) };
    if (p && /^S-SUB-0[234]$/.test(p.id)) { ctx.nav('S-HOME-01', { moving: '' }); return; }
    ctx.nav(target, { ...params, moving: '' }, { replace: true });
  }

  /* ==========================================================================================================
     Live surfaces (presentation 'live': OS frames, never gated). Recording status only: elapsed time, distance,
     car (SAF-02, D2, fixtures.md §8). No speed, score, events, record pace, map or place names while recording.
     The OS chrome is a non-interactive mock; our own surface inside it is tappable to show where it leads.
     ========================================================================================================== */
  const LIVE_CHROME = { statusBar: false };
  const OS_LIVE_SB = (ctx) => (ctx.theme === 'light' ? 'dark' : 'light');
  /** Stop button inside a Live Activity / island (LiveActivityIntent in the real app). */
  const wireStop = (html, ctx, action = 'stop') => html.replace(/<span class="ui-la-stop">/g, `<span class="ui-la-stop ui-press" ${ctx.ui.act({ action, label: 'Stop drive', haptic: 'confirm' })}>`);
  const liveSpoken = (l, v) => `DrivePerf, recording${v ? `, ${v.nickname}` : ''}. ${Math.floor(l.elapsedS / 60)} minutes${l.gps === 'searching' ? ', finding GPS' : `, ${l.distanceKm} kilometres`}.`;
  const COMMON_LIVE_A11Y = 'Live surfaces never announce the timer as it changes; each surface has one summary label (“DrivePerf, recording, GTI. 14 minutes, 9.6 kilometres.”) and a separate “Stop drive” button.';

  /* ---------- S-LIVE-01 · iOS Live Activity on the Lock Screen ---------- */
  screen('S-LIVE-01', {
    name: 'Live Activity, Lock Screen',
    wp: 'B', platforms: ['ios'], presentation: 'live', tab: null,
    states: [{ id: 'default', label: 'Recording' }, { id: 'gpsSearching', label: 'GPS searching' }, { id: 'endingDriveDecoded', label: 'Ending / “Drive decoded”' }, { id: 's8HCapEnded', label: '8 h cap ended (still recording)' }],
    stateParams: { gpsSearching: { live: 'gpsSearching' }, s8HCapEnded: { live: 'cap8h' } },
    chrome: LIVE_CHROME, statusBar: OS_LIVE_SB,
    render(ctx) {
      const { ui, state, D } = ctx;
      const key = state === 'gpsSearching' ? 'gpsSearching' : state === 's8HCapEnded' ? 'cap8h' : 'manual';
      const l = liveOf(ctx, key), v = carOf(ctx, l);
      const start = D.liveTrip.states.manual.startedAt;
      const mode = { gpsSearching: 'gps', endingDriveDecoded: 'ending', s8HCapEnded: 'cap' }[state] || 'recording';
      const scene = { default: [wall(start, l.elapsedS), 'Friday 6 November'], gpsSearching: [wall(start, 22), 'Friday 6 November'], endingDriveDecoded: [wall(start, 1244), 'Friday 6 November'], s8HCapEnded: [wall(start, 28800), 'Saturday 7 November'] }[state];
      const ending = state === 'endingDriveDecoded', cap = state === 's8HCapEnded';
      const label = ending ? 'DrivePerf. Drive decoded. Open to see your Story.' : cap ? 'DrivePerf. Still recording. Open DrivePerf to see it.' : liveSpoken(l, v);
      const la = `<div class="os-la ui-press" ${ui.act(ending ? { nav: 'S-STY-01', params: { trip: HERO_TRIP }, label } : { nav: 'S-DRV-01', params: { live: cap ? 'manual' : key }, label })}>${wireStop(ui.liveActivity(l, { mode }), ctx)}</div>`;
      const extra = cap ? iosNotif(ctx, { title: 'Recording your drive', body: ctx.D.notifications.find((n) => n.id === 'n_recording').body, time: '8h ago', nav: 'S-DRV-01', params: { live: 'manual' }, label: 'DrivePerf notification. Recording your drive.' }) : '';
      return iosLock(ctx, `${la}${extra}`, { time: scene[0], date: scene[1] });
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '.os-la, .os-n-wrap', { stagger: 70 }); },
    actions: { stop: (ctx) => { ctx.setState('endingDriveDecoded'); return false; } },
    spec: {
      purpose: 'A quiet, glanceable “it’s recording” on the Lock Screen for manual trips (F-CAP-09), with a Stop that works without unlocking. Status only: elapsed time, distance and car — never speed, score or record pace (SAF-02).',
      layout: [
        'iOS Lock Screen mock: carrier + status icons, lock glyph, date, 104 pt clock, Live Activity in the system slot above the bottom quick actions (flashlight · camera), home indicator.',
        'Live Activity (radius 26, activityBackgroundTint black 55% + system blur, 16 padding): row 1 = Loop glyph 22 (Glide, static) + “Recording · GTI” (600 15) + trailing Stop capsule (44 pt hit area, #ffffff2b, stop glyph 14 + “Stop”). Row 2 = timer “14:24” and “9.6 km” in DP Numerals 32/32, units at 65% opacity, 20 gap. No progress bar (a trip has no end point).',
        'GPS searching: “0:22” + “Finding GPS…” instead of distance. Ending: Loop + “Drive decoded.” + “Open to see your Story.” (stays 15 min, then dismissed). 8 h cap: Loop + “Still recording.” + “Open DrivePerf to see it.”, with the “Recording your drive” notification below it.',
      ],
      components: ['lockScreen', 'liveActivity', 'notification'],
      interactions: [
        'Tap the activity → opens DrivePerf on S-DRV-01 (D1: never to content). Ending → S-STY-01 for the decoded trip (gate → S-DRV-05 if still moving).',
        'Stop (LiveActivityIntent, no unlock needed) → ends the trip; the activity switches to “Drive decoded.” after finalisation (prototype: → ending state). Next app open shows S-DRV-04 / the Story.',
        'Started by: in-app Start, the Control Center control or the widget (S-LIVE-03/04). Never started for auto trips in the background (A3 §3); “Show live drive” attaches one.',
      ],
      motion: ['System-driven only: ActivityKit presents and updates the activity; the timer is Text(timerInterval:) and ticks itself. No custom animation, no pulsing dot (motion.md §3.2). Prototype: the activity rises 8 px on load. RM: none.'],
      a11y: [COMMON_LIVE_A11Y, 'Stop has its own label “Stop drive” and a 44 pt target.', 'Supports the Lock Screen’s large text: the numbers row wraps under the title at AX sizes.'],
      platformDiffs: { ios: 'ActivityKit Live Activity (ActivityConfiguration lock-screen presentation), local updates only (≤ every 5 s, distance step 0.1 km or 15 s), payload ≤ 4 KB, staleDate = now + 60 s.', android: 'n/a (iOS only). Android equivalent: S-LIVE-05 Live Update / S-LIVE-06 ongoing notification.' },
      analytics: ['live_activity_started {source: app|control|widget|show_live}', 'live_activity_stop_tapped', 'live_activity_ended {reason: decoded|cap_8h|user}'],
      copy: { Recording: 'Recording · {GTI} · {14:24} · {9.6} km · Stop', GPS: 'Recording · {GTI} · {0:22} · Finding GPS… · Stop', Ending: 'Drive decoded. · Open to see your Story.', Cap: 'Still recording. · Open DrivePerf to see it.', 'Cap notification': 'Recording your drive · Tap to open. Stop it any time after you park.' },
      freePro: 'Same for every plan.',
      data: 'ContentState = {startedAt, distanceKm, vehicleNickname, gpsState, phase}. Fixtures: liveTrip.states.manual (default), gpsSearching, cap8h. Lock Screen clock = manual.startedAt + elapsedS (5:46; 5:32 for GPS; Sat 1:31 for the 8 h cap); the ending clock (5:52) is illustrative.',
      icons: ['stop', 'lock'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ---------- S-LIVE-02 · Dynamic Island ---------- */
  screen('S-LIVE-02', {
    name: 'Live Activity, Dynamic Island',
    wp: 'B', platforms: ['ios'], presentation: 'live', tab: null,
    states: [{ id: 'default', label: 'Compact' }, { id: 'minimal', label: 'Minimal (another activity is running)' }, { id: 'expanded', label: 'Expanded (long-press)' }, { id: 'ending', label: 'Ending (“Drive decoded”)' }],
    chrome: { island: false, statusBar: false }, statusBar: OS_LIVE_SB,
    render(ctx) {
      const { ui, state, D } = ctx;
      const l = liveOf(ctx, 'manual'), v = carOf(ctx, l);
      const mode = state === 'default' ? 'compact' : state;
      const act = state === 'ending' ? { nav: 'S-STY-01', params: { trip: HERO_TRIP }, label: 'DrivePerf. Drive decoded.' } : { nav: 'S-DRV-01', params: { live: 'manual' }, label: liveSpoken(l, v) };
      const island = wireStop(ui.island(l, mode), ctx).replace('<div class="ui-island ', `<div ${ui.act(act)} data-island class="ui-press ui-island `);
      const time = wall(D.liveTrip.states.manual.startedAt, state === 'ending' ? 1244 : l.elapsedS);
      return `${iosHome(ctx, '', { time, minBar: true, apps: 12, dpAt: 5, tag: 'Dynamic Island · mock' })}${state === 'expanded' ? '<div class="os-dim" aria-hidden="true"></div>' : ''}${island}`;
    },
    mount(el, ctx) {
      const isl = el.querySelector('[data-island]');
      if (ctx.state === 'expanded' || ctx.state === 'default') {
        if (!ctx.shot && !ctx.motion.reduced(el)) ctx.motion.animate(isl, ctx.state === 'expanded' ? [{ transform: 'translateX(-50%) scale(.55, .3)', opacity: 0.4 }, { transform: 'translateX(-50%) scale(1)', opacity: 1 }] : [{ transform: 'translateX(-50%) scaleX(.6)' }, { transform: 'translateX(-50%) scaleX(1)' }], { preset: 'bouncy' });
      }
      if (ctx.state !== 'default' || ctx.shot) return undefined;
      let tm; const down = () => { tm = setTimeout(() => { ctx.haptic('longPress'); ctx.setState('expanded'); }, 480); }; const up = () => clearTimeout(tm);
      isl.addEventListener('pointerdown', down); isl.addEventListener('pointerup', up); isl.addEventListener('pointerleave', up);
      return () => { up(); isl.removeEventListener('pointerdown', down); isl.removeEventListener('pointerup', up); isl.removeEventListener('pointerleave', up); };
    },
    actions: { stop: (ctx) => { ctx.setState('ending'); return false; } },
    spec: {
      purpose: 'The same manual-trip Live Activity in the Dynamic Island while the phone is unlocked (F-CAP-09): a static Loop and a self-ticking timer, nothing that invites a look while driving.',
      layout: [
        'Compact (default): leading = Loop glyph 20 (Glide, static) · trailing = timer “14:24” (600 15 DP Numerals, Glide). Width ≈ 196, height 37, around the hardware island; the status bar keeps the time and battery.',
        'Minimal (another app’s activity owns the compact slot): a detached 37 pt circle with the Loop only.',
        'Expanded (long-press): 378 wide, radius 46, 18/20 padding. Leading “Recording” + car (GTI at 60%) · trailing timer 28 · bottom row: distance “9.6 km” (28, unit 60%) + Stop capsule. No centre content.',
        'Ending: compact with “Drive decoded ✓”, auto-dismissed after a few seconds.',
        'Background in the mock: an iOS Home Screen (labelled icons, dock); expanded dims it 20%.',
      ],
      components: ['island', 'homeScreen'],
      interactions: ['Tap (compact, minimal, expanded) → DrivePerf on S-DRV-01. Long-press compact → expanded (longPress haptic). Stop (expanded) → ends the trip → ending.', 'Ending tap → S-STY-01 through the D1 gate (S-DRV-05 while moving).'],
      motion: ['System morphs between compact, expanded and minimal (prototype: bouncy spring scale from the pill). The timer is Text(timerInterval:). No custom animation, no pulse. RM: system cross-fade.'],
      a11y: [COMMON_LIVE_A11Y, 'Compact reads “DrivePerf, recording, 14 minutes”. Expanded exposes “Stop drive”.'],
      platformDiffs: { ios: 'ActivityConfiguration dynamicIsland { DynamicIslandExpandedRegion(.leading/.trailing/.bottom) } compactLeading/compactTrailing/minimal; keylineTint Glide.', android: 'n/a (iOS only). Android shows the status-bar chip (S-LIVE-05 statusChip).' },
      analytics: ['live_activity_expanded', 'live_activity_tapped {presentation: compact|minimal|expanded}', 'live_activity_stop_tapped {presentation: expanded}'],
      copy: { Compact: '[Loop] {14:24}', Expanded: 'Recording · {GTI} · {14:24} · {9.6} km · Stop', Ending: 'Drive decoded ✓' },
      freePro: 'Same for every plan.',
      data: 'liveTrip.states.manual (14:24 · 9.6 km · GTI). Status-bar clock = startedAt + elapsedS (5:46).',
      icons: ['stop', 'check'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ---------- S-LIVE-03 · iOS Control Center control ---------- */
  function ccPanel(ctx, control) {
    const { ui } = ctx;
    const circ = (g, on) => `<span class="os-cc-c ${on ? 'is-on' : ''}">${g}</span>`;
    return `<div class="os-frame os-cc"><div class="ui-wall"></div><div class="os-cc-blur"></div>${osBar(ctx, { carrier: 'Rogers' })}
      <div class="os-cc-top" aria-hidden="true"><span>${ui.icon('plus', 18)}</span><span>${GL.power}</span></div>
      <div class="os-cc-grid">
        <div class="os-cc-m os-cc-conn" aria-hidden="true">${circ(GL.plane)}${circ(GL.cell, true)}${circ(GL.wifi, true)}${circ(GL.bt, true)}</div>
        <div class="os-cc-m os-cc-np" aria-hidden="true"><span class="os-cc-art"></span><b>Not Playing</b><span class="os-cc-np-c">${ui.icon('play', 22)}</span></div>
        <div class="os-cc-m os-cc-s1" aria-hidden="true">${GL.rotate}</div>
        <div class="os-cc-m os-cc-s2" aria-hidden="true">${GL.mirror}</div>
        <div class="os-cc-m os-cc-sl os-cc-br" aria-hidden="true"><i style="height:62%"></i>${GL.sun}</div>
        <div class="os-cc-m os-cc-sl os-cc-vol" aria-hidden="true"><i style="height:44%"></i>${GL.speaker}</div>
        <div class="os-cc-m os-cc-focus" aria-hidden="true"><span class="os-cc-c">${GL.moon}</span><b>Focus</b></div>
        <div class="os-cc-m is-round os-cc-r1" aria-hidden="true">${GL.torch}</div><div class="os-cc-m is-round os-cc-r2" aria-hidden="true">${GL.timer}</div><div class="os-cc-m is-round os-cc-r3" aria-hidden="true">${GL.calc}</div><div class="os-cc-m is-round os-cc-r4" aria-hidden="true">${GL.camera}</div>
        ${control}
      </div><span class="os-homebar"></span>${mockTag('Control Center · mock')}</div>`;
  }
  screen('S-LIVE-03', {
    name: 'Control Center control',
    wp: 'B', platforms: ['ios'], presentation: 'live', tab: null,
    states: [{ id: 'default', label: '“Start drive”' }, { id: 'showLiveDrive', label: '“Show live drive” (auto trip running)' }, { id: 'stopDrive', label: '“Stop drive” (trip with Live Activity)' }],
    stateParams: { showLiveDrive: { live: 'auto' }, stopDrive: { live: 'manual' } },
    chrome: LIVE_CHROME, statusBar: OS_LIVE_SB,
    render(ctx) {
      const { ui, icons, state } = ctx;
      const l = liveOf(ctx, state === 'showLiveDrive' ? 'auto' : 'manual');
      const C = {
        default: { title: 'Start drive', sub: 'DrivePerf', on: false, g: icons.loop({ size: 24, color: '#5BE7C4', dot: '#fff' }), act: { action: 'start', haptic: 'confirm' }, label: 'Start drive, DrivePerf' },
        showLiveDrive: { title: 'Show live drive', sub: 'For passengers, or before you set off', on: false, g: icons.loop({ size: 24, color: '#5BE7C4', dot: '#fff' }), act: { action: 'show', haptic: 'tap' }, label: 'Show live drive. For passengers, or before you set off.' },
        stopDrive: { title: 'Stop drive', sub: `Recording · ${minsOf(l)} min`, on: true, g: ui.icon('stop', 22), act: { action: 'stop', haptic: 'confirm' }, label: `Stop drive. Recording, ${minsOf(l)} minutes.` },
      }[state];
      const control = `<div class="os-cc-m os-cc-dp ${C.on ? 'is-on' : ''} ui-press" ${ui.act({ ...C.act, label: C.label })}><span class="os-cc-c">${C.g}</span><span class="os-cc-dp-t"><b>${ui.esc(C.title)}</b><small>${ui.esc(C.sub)}</small></span></div>`;
      return ccPanel(ctx, control);
    },
    mount(el, ctx) { ctx.ui.anim.pop(el.querySelector('.os-cc-dp'), 80); },
    actions: {
      start: (ctx) => { ctx.nav('S-LIVE-01', { live: 'manual' }); return false; },
      show: (ctx) => { ctx.nav('S-LIVE-01', { live: 'auto' }); return false; },
      stop: (ctx) => { ctx.nav('S-LIVE-03', { live: '' }); ctx.setState('default'); return false; },
    },
    spec: {
      purpose: 'One tap to start a recorded drive with its Live Activity before setting off, without opening the app (F-CAP-10). With an auto trip already running it offers “Show live drive” for passengers and pre-departure; during a trip it becomes “Stop drive”.',
      layout: [
        'iOS Control Center mock: blurred, dimmed wallpaper; connectivity 2×2, Now Playing 2×2, orientation + screen mirroring, brightness + volume sliders (1×2), Focus 2×1, flashlight · timer · calculator · camera (1×1 circles).',
        'DrivePerf control, medium size (2×1, 166×74, radius 26, glass #ffffff26): leading 46 circle with the symbol (the Loop in Glide; Stop glyph when active) + title (600 15) + subtitle (13, 60%). Active (stopDrive): circle filled Glide with a black stop glyph, tile tinted Glide 22%.',
        'Small (1×1) variant uses the symbol only, with the title as its accessibility label.',
      ],
      components: ['ControlWidgetButton (local mock)', 'controlCenter (local ccPanel)'],
      interactions: [
        'Start drive → LiveActivityIntent starts a manual trip and S-LIVE-01 without opening the app; with no location permission the intent opens S-ONB-16 instead (EC-PERM).',
        'Show live drive (auto trip running) → attaches a Live Activity to the running trip (S-LIVE-01 with the auto trip’s numbers). Copy never invites use while driving (D13).',
        'Stop drive → ends the trip (S-DRV-04 on next open; the control returns to Start drive). Haptics: confirm on Start/Stop (system).',
      ],
      motion: ['System Control Center motion only (the control’s symbol uses .contentTransition(.symbolEffect) on state change). Prototype: the control pops in once. RM: none.'],
      a11y: ['Labels: “Start drive, DrivePerf” · “Show live drive. For passengers, or before you set off.” · “Stop drive. Recording, 14 minutes.”', 'The value text never updates more than once a minute (no timer in Control Center).'],
      platformDiffs: { ios: 'ControlWidget (ControlWidgetButton + App Intent / LiveActivityIntent), kind com.driveperf.control.drive; ControlValueProvider supplies idle / autoRunning / recording.', android: 'n/a (iOS only). Android equivalent: S-LIVE-07 Quick Settings tile.' },
      analytics: ['control_tapped {action: start|show_live|stop}', 'trip_started {mode: manual, trigger: control}'],
      copy: { Start: 'Start drive · DrivePerf', 'Show live': 'Show live drive · For passengers, or before you set off', Stop: 'Stop drive · Recording · {14} min' },
      freePro: 'Same for every plan.',
      data: 'stopDrive uses liveTrip.states.manual (14 min); showLiveDrive reflects liveTrip.states.auto running in the background. No speed or score.',
      icons: ['stop', 'plus', 'play'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ---------- S-LIVE-04 · iOS Lock Screen + Home Screen widgets ---------- */
  function wgIdle(ctx, kind) {
    const { ui, data } = ctx;
    const t = data.trip(HERO_TRIP), s = t.smoothness;
    const chip = `<span class="wg-delta"><b aria-hidden="true">▲</b>${ui.esc(data.signed(s.delta))} vs usual</span>`;
    const nav = { nav: 'S-STY-01', params: { trip: t.id } };
    if (kind === 'small') return ui.widget(`<div class="wg-s ui-press" ${ui.act({ ...nav, label: `Last drive, ${t.partOfDay}. Smoothness ${s.score}, ${s.delta} above your usual.` })}><span class="wg-k">${ctx.icons.loop({ size: 14, color: '#5BE7C4', dot: 'currentColor' })}Last drive</span>${ui.ring(s.score, 72, { label: false, stroke: 7, base: s.baseline, shape: false })}<span class="wg-l">${ui.esc(t.partOfDay)}</span>${chip}</div>`, { kind: 'small', label: 'DrivePerf small widget' });
    return ui.widget(`<div class="wg-m"><div class="wg-m-l ui-press" ${ui.act({ ...nav, label: `Last drive, ${t.partOfDay}, GTI. Smoothness ${s.score}, ${s.delta} above your usual.` })}>${ui.ring(s.score, 112, { label: false, stroke: 10, base: s.baseline, shape: false })}<span class="wg-m-t"><span class="wg-k">${ctx.icons.loop({ size: 14, color: '#5BE7C4', dot: 'currentColor' })}Last drive</span><b>${ui.esc(t.partOfDay)}</b><span class="wg-l">${ui.esc(data.vehicle(t.vehicleId).nickname)} · ${ui.num(data.km(t.distanceKm), 'km')}</span>${chip}</span></div><span class="wg-go ui-press" ${ui.act({ action: 'start', label: 'Start drive', haptic: 'confirm' })}>${ui.icon('record', 16)}Start</span></div>`, { kind: 'medium', label: 'DrivePerf medium widget' });
  }
  function wgRec(ctx, kind, l) {
    const { ui, data } = ctx, v = carOf(ctx, l);
    const lbl = liveSpoken(l, v);
    if (kind === 'small') return ui.widget(`<div class="wg-s is-rec ui-press" ${ui.act({ nav: 'S-DRV-01', params: { live: 'manual' }, label: lbl })}><span class="wg-k"><i class="wg-dot"></i>Recording</span><b class="wg-time">${ui.esc(data.clock(l.elapsedS))}</b><span class="wg-l">${ui.num(data.km(l.distanceKm), 'km')} · ${ui.esc(v.nickname)}</span></div>`, { kind: 'small', label: 'DrivePerf small widget, recording' });
    return ui.widget(`<div class="wg-m is-rec"><div class="wg-m-l ui-press" ${ui.act({ nav: 'S-DRV-01', params: { live: 'manual' }, label: lbl })}><span class="wg-m-t"><span class="wg-k"><i class="wg-dot"></i>Recording · ${ui.esc(v.nickname)}</span><span class="wg-nums"><b class="wg-time">${ui.esc(data.clock(l.elapsedS))}</b><b class="wg-time is-km">${ui.esc(data.km(l.distanceKm))}<span>km</span></b></span></span></div><span class="wg-go is-stop ui-press" ${ui.act({ action: 'stop', label: 'Stop drive', haptic: 'confirm' })}>${ui.icon('stop', 16)}Stop</span></div>`, { kind: 'medium', label: 'DrivePerf medium widget, recording' });
  }
  function wgMsg(ctx, kind, o) {
    const { ui, icons } = ctx;
    const art = o.deny ? `<span class="wg-ic is-warn">${ui.icon('gps', 22)}</span>` : `<span class="wg-ic">${icons.loop({ size: 26, color: '#5BE7C4', dot: 'currentColor' })}</span>`;
    const act = o.deny ? { nav: 'S-ONB-16', label: `${o.title} ${o.text}` } : { action: 'start', label: `${o.title} ${o.text} Start drive.`, haptic: 'confirm' };
    if (kind === 'small') return ui.widget(`<div class="wg-s is-msg ui-press" ${ui.act(act)}>${art}<b>${ui.esc(o.title)}</b><span class="wg-l">${ui.esc(o.text)}</span></div>`, { kind: 'small' });
    return ui.widget(`<div class="wg-m is-msg"><div class="wg-m-l ui-press" ${ui.act(o.deny ? act : { nav: 'S-HOME-01/firstRunEmpty', label: `${o.title} ${o.text}` })}>${art}<span class="wg-m-t"><b>${ui.esc(o.title)}</b><span class="wg-l">${ui.esc(o.text)}</span></span></div>${o.deny ? `<span class="wg-go is-tonal ui-press" ${ui.act({ nav: 'S-ONB-16', label: 'Fix location' })}>Fix</span>` : `<span class="wg-go ui-press" ${ui.act({ action: 'start', label: 'Start drive', haptic: 'confirm' })}>${ui.icon('record', 16)}Start</span>`}</div>`, { kind: 'medium' });
  }
  function wgAccessories(ctx, rec, l) {
    const { ui, data, icons } = ctx;
    const t = data.trip(HERO_TRIP);
    const circ = rec ? `<span class="wg-acc-c">${icons.loop({ size: 18, mono: true, color: 'currentColor' })}<b>${minsOf(l)}m</b></span>` : `<span class="wg-acc-c">${icons.loop({ size: 18, mono: true, color: 'currentColor' })}<b>${t.smoothness.score}</b></span>`;
    const rect = rec ? `<span class="wg-acc-r"><b>${icons.loop({ size: 13, mono: true, color: 'currentColor' })}Recording · ${ui.esc(carOf(ctx, l).nickname)}</b><span>${ui.esc(data.clock(l.elapsedS))} · ${ui.esc(data.km(l.distanceKm))} km</span></span>` : `<span class="wg-acc-r"><b>${icons.loop({ size: 13, mono: true, color: 'currentColor' })}Last drive · ${t.smoothness.score}</b><span>${ui.esc(t.partOfDay)}</span></span>`;
    return `${ui.widget(circ, { kind: 'circular', label: rec ? `DrivePerf, recording, ${minsOf(l)} minutes` : `DrivePerf, last drive Smoothness ${t.smoothness.score}` })}${ui.widget(rect, { kind: 'rectangular', label: rec ? liveSpoken(l, carOf(ctx, l)) : `Last drive, Smoothness ${t.smoothness.score}, ${t.partOfDay}` })}`;
  }
  const WG_SCENE = { idle: ['8:20', 'Thursday 5 November'], rec: null, empty: ['6:12', 'Monday 28 September'], deny: ['7:30', 'Friday 6 November'] };
  screen('S-LIVE-04', {
    name: 'Lock Screen and Home Screen widgets',
    wp: 'B', platforms: ['ios'], presentation: 'live', tab: null,
    states: [
      { id: 'default', label: 'Idle with last drive (Home Screen: small + medium)' },
      { id: 'recording', label: 'Recording' },
      { id: 'Empty', label: 'Empty (no drives yet)' },
      { id: 'Deny', label: 'Deny (location needed)' },
      { id: 'lockScreen', label: 'Lock Screen accessories (circular + rectangular), idle' },
      { id: 'lockScreenRecording', label: 'Lock Screen accessories, recording' },
    ],
    stateParams: { recording: { live: 'manual' }, lockScreenRecording: { live: 'manual' } },
    chrome: LIVE_CHROME, statusBar: OS_LIVE_SB,
    render(ctx) {
      const { state, D } = ctx;
      const l = liveOf(ctx, 'manual');
      const recTime = wall(D.liveTrip.states.manual.startedAt, l.elapsedS);
      if (state === 'lockScreen' || state === 'lockScreenRecording') {
        const rec = state === 'lockScreenRecording';
        const la = rec ? `<div class="os-la">${ctx.ui.liveActivity(l, { mode: 'recording' })}</div>` : '';
        return iosLock(ctx, la, { time: rec ? recTime : WG_SCENE.idle[0], date: rec ? 'Friday 6 November' : WG_SCENE.idle[1], acc: wgAccessories(ctx, rec, l), tag: 'Lock Screen widgets · mock' });
      }
      let w, scene;
      if (state === 'recording') { w = wgRec(ctx, 'medium', l) + wgRec(ctx, 'small', l); scene = [recTime]; }
      else if (state === 'Empty') { const m = { title: 'No drives yet.', text: 'Just drive.' }; w = wgMsg(ctx, 'medium', m) + wgMsg(ctx, 'small', m); scene = WG_SCENE.empty; }
      else if (state === 'Deny') { const m = { title: 'Location needed.', text: 'Tap to fix.', deny: true }; w = wgMsg(ctx, 'medium', m) + wgMsg(ctx, 'small', m); scene = WG_SCENE.deny; }
      else { w = wgIdle(ctx, 'medium') + wgIdle(ctx, 'small'); scene = WG_SCENE.idle; }
      return iosHome(ctx, w, { time: scene[0], apps: 8, dpAt: 2, tag: 'Home Screen widgets · mock' });
    },
    mount(el, ctx) { ctx.ui.anim.rings(el, { haptics: false }); },
    actions: {
      start: (ctx) => { ctx.nav('S-LIVE-01', { live: 'manual' }); return false; },
      stop: (ctx) => { ctx.nav('S-LIVE-04', { live: '' }); ctx.setState('default'); return false; },
    },
    spec: {
      purpose: 'Start a drive in one tap and see the last drive at a glance from the Home Screen and Lock Screen (F-CAP-10). While a trip records they switch to status only (timer, distance, car).',
      layout: [
        'Home Screen mock: widgets first (medium spans the row, small 2×2 beside four labelled app icons incl. DrivePerf), search pill, glass dock.',
        'Medium (idle): 112 Smoothness ring (no label, baseline notch) · kicker “Last drive” with the Loop · “Thursday morning” (600 17) · “GTI · 37.9 km” · “▲ +7 vs usual” (Glide) · trailing Start capsule (Glide filled, record glyph, 40 tall). No route name and no map (privacy, F-CAP-10).',
        'Small (idle): kicker, 82 ring, part of day, delta. Recording: live dot + “Recording”, timer 34 DP Numerals, “9.6 km · GTI” (medium adds a Stop capsule). Empty: Loop + “No drives yet. Just drive.” + Start. Deny: GPS glyph in warning tint + “Location needed. Tap to fix.” + Fix.',
        'Lock Screen accessories under the clock: circular (Loop + last score “91”, or “14m” while recording) and rectangular (“Last drive · 91 / Thursday morning”, or “Recording · GTI / 14:24 · 9.6 km”), rendered in the system’s vibrant monochrome. Recording also shows the Live Activity.',
      ],
      components: ['widget', 'ring', 'homeScreen', 'lockScreen', 'liveActivity'],
      interactions: [
        'Start (medium, Empty) → LiveActivityIntent: starts a manual trip + S-LIVE-01 without opening the app. Stop (recording) → ends the trip.',
        'Last drive area → S-STY-01 for that trip if its Story is unwatched, else S-TRP-01 — through the D1 gate (S-DRV-05 while moving). Recording area → S-DRV-01.',
        'Empty body → S-HOME-01 first run. Deny → S-ONB-16 (manual mode sheet with the Settings deep link).',
      ],
      motion: ['WidgetKit timeline reloads only; .contentTransition(.numericText()) for the score/timer; no custom animation. Prototype: ring reveal on load (no haptics). RM: static.'],
      a11y: ['Each widget is one element plus its button: “Last drive, Thursday morning, GTI. Smoothness 91, 7 above your usual.” + “Start drive”.', 'Recording: “DrivePerf, recording, GTI. 14 minutes, 9.6 kilometres.”', 'Accessory widgets render with .widgetAccentable() so they stay legible in every Lock Screen tint.'],
      platformDiffs: { ios: 'WidgetKit: systemSmall, systemMedium, accessoryCircular, accessoryRectangular; Button(intent:) for Start/Stop (LiveActivityIntent); containerBackground for Liquid Glass / tinted Home Screens.', android: 'n/a (iOS only). Android equivalent: S-LIVE-08 Glance widget.' },
      analytics: ['widget_tapped {action: start|stop|open_last|fix, family: small|medium|circular|rectangular}', 'trip_started {mode: manual, trigger: widget}'],
      copy: { Idle: 'Last drive · {Thursday morning} · {GTI} · {37.9} km · ▲ +7 vs usual · Start', Rectangular: 'Last drive · {91} / {Thursday morning}', Recording: 'Recording · {GTI} · {14:24} · {9.6} km · Stop', Empty: 'No drives yet. Just drive. · Start', Deny: 'Location needed. Tap to fix. · Fix' },
      freePro: 'Same for every plan.',
      data: 'Idle widgets show t20261105-0742 (Thursday morning, 91, +7) with the scene set at Thu 5 Nov 8:20 so it is the last drive (at today 19:30 the newest trip is unscorable and the widget would show “Not scored · Friday midday”). Recording: liveTrip.states.manual. Empty uses the install date (28 Sep). The widget always switches to the recording layout while any trip is active, so a score is never on screen during a drive.',
      icons: ['record', 'stop', 'gps', 'lock'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ---------- S-LIVE-05 · Android 16 Live Update (promoted ongoing notification) ---------- */
  function liveUpdate(ctx, l, o = {}) {
    const { ui, data } = ctx, v = carOf(ctx, l);
    return andNotif(ctx, {
      big: true, cls: 'is-live', header: `DrivePerf · ${data.clock(l.elapsedS)}`, title: 'Recording your drive',
      body: `${o.auto ? 'Started automatically · ' : ''}${minsOf(l)} min · ${data.km(l.distanceKm)} km${v ? ` · ${v.nickname}` : ''}`,
      progress: 'No end point', nav: 'S-DRV-01', params: { live: l.key }, label: `${liveSpoken(l, v)} Live Update.`,
      actions: [{ text: 'Stop', icon: 'stop', action: 'stop', haptic: 'confirm', label: 'Stop drive' }],
    });
  }
  const drTile = (ctx, st, l) => ctx.ui.qsTile({ state: st, label: st === 'recording' ? 'Recording' : 'Start drive', sub: st === 'recording' ? ctx.data.clock(l.elapsedS) : st === 'unavailable' ? 'Location needed' : 'DrivePerf' });
  screen('S-LIVE-05', {
    name: 'Live Update notification (promoted)',
    wp: 'B', platforms: ['android'], presentation: 'live', tab: null,
    states: [{ id: 'default', label: 'Manual recording (shade)' }, { id: 'autoRecording', label: 'Auto recording, after confirmation (lock screen)' }, { id: 'statusChip', label: 'Status chip (home screen)' }, { id: 'ending', label: 'Ending (replaced by “Drive decoded.”)' }],
    stateParams: { default: { live: 'manual' }, autoRecording: { live: 'auto' }, statusChip: { live: 'manual' } },
    chrome: LIVE_CHROME, statusBar: OS_LIVE_SB,
    render(ctx) {
      const { ui, state, D } = ctx;
      const start = D.liveTrip.states.manual.startedAt;
      if (state === 'ending') {
        const n = D.notifications.find((x) => x.id === 'n_decoded');
        const card = andNotif(ctx, { title: n.title, body: n.body, header: 'DrivePerf · Drive decoded · now', nav: 'S-STY-01', params: { trip: n.tripId }, label: `${n.title} ${n.body}`, actions: [{ text: 'Watch Story', nav: 'S-STY-01', params: { trip: n.tripId } }] });
        return andShade(ctx, { time: '8:14', date: 'Thu, 5 Nov', tiles: [qsOther(...QS_DEFAULT[0]), qsOther(...QS_DEFAULT[1]), drTile(ctx, 'idle'), qsOther(...QS_DEFAULT[2])], notifs: card, notif: ['loop'], tag: 'Notification shade · mock · Live Update ended' });
      }
      const l = liveOf(ctx, state === 'autoRecording' ? 'auto' : 'manual');
      const chip = `${minsOf(l)} min`;
      const time = wall(start, l.elapsedS);
      if (state === 'autoRecording') return andLock(ctx, liveUpdate(ctx, l, { auto: true }), { time, date: 'Friday, 6 November', chip, tag: 'Lock screen · mock · promoted Live Update' });
      if (state === 'statusChip') {
        const tip = `<span class="os-chip-tip" aria-hidden="true">Status chip · ${chip.length} of 7 characters</span>`;
        return `${andHome(ctx, '', { time, chip, chipHi: true, apps: 8, extra: tip, glance: ' ', tag: 'Home screen · mock · Live Update status chip' })}<span class="os-chip-hit ui-press" ${ui.act({ state: 'default', label: `DrivePerf status chip, ${chip}. Opens the Live Update.` })}></span>`;
      }
      return andShade(ctx, { time, tiles: [qsOther(...QS_DEFAULT[0]), qsOther(...QS_DEFAULT[1]), drTile(ctx, 'recording', l), qsOther(...QS_DEFAULT[2])], notifs: liveUpdate(ctx, l), chip, tag: 'Notification shade · mock · promoted Live Update' });
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '.os-n', { stagger: 60 }); },
    actions: { stop: (ctx) => { ctx.nav('S-LIVE-05', { live: '' }); ctx.setState('ending'); return false; } },
    spec: {
      purpose: 'Android 16’s promoted ongoing notification gives Android users the same glanceable recording status as the iPhone Live Activity: a status-bar chip, a pinned card in the shade and on the lock screen, and Stop (F-CAP-12).',
      layout: [
        'Shade (default): Android 16 QS header (time, date), four large tiles (DrivePerf tile shows “Recording · 14:24”), then the Live Update pinned first: app icon + “DrivePerf · 14:24” (chronometer) · title “Recording your drive” (500 16) · “14 min · 9.6 km · GTI” · indeterminate ProgressStyle track (“No end point”) · Stop action (tonal pill, stop glyph).',
        'Status chip: primary pill in the status bar, ≤ 7 characters: “14 min” (Loop mono glyph + text). Shown in every state except ending.',
        'Auto recording: the same card on the lock screen, body prefixed “Started automatically · ”; only promoted after the trip is confirmed.',
        'Ending: chip gone; the card is replaced by the “Drive decoded.” notification (Drive decoded channel) with a Watch Story action.',
        'Not colourised, no custom RemoteViews (promotion rules); small icon = icon-android-notification.svg.',
      ],
      components: ['notification', 'shade', 'qsTile', 'lockScreen'],
      interactions: [
        'Tap the card or the chip → DrivePerf on S-DRV-01 (D1: never content). Stop → ends the trip (prototype → ending); the next open shows S-DRV-04 / the Story.',
        'Ending card / Watch Story → S-STY-01 through the gate. If the user dismisses or demotes the Live Update it is never re-promoted for that trip (falls back to S-LIVE-06).',
      ],
      motion: ['System only (chronometer ticks itself; updates rate-limited to ≥ 5 s apart). No animation in the chip (motion.md §3.2). Prototype: the card rises on load. RM: none.'],
      a11y: [COMMON_LIVE_A11Y, 'Chip content description: “DrivePerf recording, 14 minutes”. Stop action label “Stop drive”.'],
      platformDiffs: { ios: 'n/a (Android only). iOS equivalent: S-LIVE-01/02.', android: 'Notification.ProgressStyle (indeterminate, no end point) or MetricStyle; setRequestPromotedOngoing(true), POST_PROMOTED_NOTIFICATIONS, setShortCriticalText("14 min"), setUsesChronometer(true) with when = startedAt; FGS type location; channel “Recording” (low importance).' },
      analytics: ['live_update_shown {promoted: true, mode: manual|auto}', 'live_update_stop_tapped', 'live_update_demoted', 'live_update_chip_tapped'],
      copy: { Title: 'Recording your drive', Body: '{14} min · {9.6} km · {GTI}', Auto: 'Started automatically · {21} min · {17.2} km · {GTI}', Chip: '{14} min (≤ 7 characters)', Action: 'Stop', Ending: 'Drive decoded. · Home → Work · Smoothness 91 · Watch Story' },
      freePro: 'Same for every plan.',
      data: 'liveTrip.states.manual (default, statusChip) and auto (autoRecording). Clock = startedAt + elapsedS (5:46 / 5:53). Ending uses DP_DATA.notifications n_decoded (t20261105-0742, scene Thu 8:14) — the decoded notification may name the route and score because it is posted only after parking (held until parked, D4).',
      icons: ['stop', 'record', 'chevronDown'],
      assets: ['logo-a-loop.svg', 'icon-android-notification.svg'],
    },
  });

  /* ---------- S-LIVE-06 · Standard ongoing recording notification (FGS) ---------- */
  function fgsNotif(ctx, l, o = {}) {
    const { data } = ctx, v = carOf(ctx, l);
    return andNotif(ctx, { header: `DrivePerf · Recording · ${data.clock(l.elapsedS)}`, title: 'Recording your drive', body: `${o.auto ? 'Started automatically · ' : ''}${minsOf(l)} min · ${data.km(l.distanceKm)} km${v ? ` · ${v.nickname}` : ''}`, nav: 'S-DRV-01', params: { live: l.key }, label: `${liveSpoken(l, v)} Notification.`, actions: [{ text: 'Stop', icon: 'stop', action: 'stop', haptic: 'confirm', label: 'Stop drive' }] });
  }
  screen('S-LIVE-06', {
    name: 'Standard ongoing recording notification',
    wp: 'B', platforms: ['android'], presentation: 'live', tab: null,
    states: [{ id: 'default', label: 'Recording (Android < 16)' }, { id: 'demotedLiveUpdate', label: 'Demoted Live Update (Android 16)' }, { id: 'notificationsDenied', label: 'Notifications denied (Task Manager)' }],
    stateParams: { default: { live: 'manual' }, demotedLiveUpdate: { live: 'manual' }, notificationsDenied: { live: 'manual' } },
    chrome: LIVE_CHROME, statusBar: OS_LIVE_SB,
    render(ctx) {
      const { ui, state, D, data } = ctx;
      const l = liveOf(ctx, 'manual');
      const time = wall(D.liveTrip.states.manual.startedAt, l.elapsedS);
      const tiles = [qsOther(...QS_DEFAULT[0]), qsOther(...QS_DEFAULT[1]), drTile(ctx, 'recording', l), qsOther(...QS_DEFAULT[2])];
      if (state === 'notificationsDenied') {
        const dlg = `<div class="os-dlg-wrap"><div class="os-dlg" role="dialog" aria-label="Active apps"><b>Active apps</b><p>These apps are active and running, even when you’re not using them.</p>
          <div class="os-dlg-row">${appIcon(ctx)}<span><b>DrivePerf</b><small>${ui.esc(data.min(l.elapsedS))}</small></span><span class="os-n-act ui-press" ${ui.act({ action: 'stop', label: 'Stop DrivePerf recording', haptic: 'confirm' })}>Stop</span></div>
          <span class="os-dlg-close">Close</span></div></div>`;
        const tiles8 = [qsOther(...QS_DEFAULT[0]), qsOther(...QS_DEFAULT[1]), drTile(ctx, 'recording', l), qsOther(...QS_DEFAULT[2]), qsOther(...QS_DEFAULT[3]), qsOther(...QS_DEFAULT[4])];
        return andShade(ctx, { time, full: true, tiles: tiles8, footer: '<span class="os-qs-active is-hi">1 app is active</span>', overlay: dlg, tag: 'Quick Settings · mock · Task Manager (notifications off)' });
      }
      const card = fgsNotif(ctx, l);
      if (state === 'demotedLiveUpdate') return andShade(ctx, { time, tiles, notifs: card, notif: ['loop'], tag: 'Notification shade · mock · demoted Live Update (not re-promoted this trip)' });
      return andShade(ctx, { time, tiles, silent: card, notif: ['loop'], tag: 'Notification shade · mock · Android 15 and earlier' });
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '.os-n, .os-dlg', { stagger: 60 }); },
    actions: { stop: (ctx) => { ctx.nav('S-HOME-01', { live: '' }); ctx.toast('Drive ended. Decoding it now.', { icon: 'check' }); return false; } },
    spec: {
      purpose: 'The foreground-service notification that must exist while Android records (F-CAP-11/12): the same status as the Live Update in a quiet, low-importance notification, used below Android 16, when promotion is unavailable or after the user demotes it. With notifications denied, Android still lists the service in the Task Manager.',
      layout: [
        'Shade: QS header + 4 tiles (DrivePerf tile “Recording · 14:24”), then the “Silent” section (Android ≤ 15) holding the notification; demoted on 16 it sits in the normal list. Status bar: the small Loop icon after the clock, no chip.',
        'Card (surfaceContainerHigh, radius 26): app icon + “DrivePerf · Recording · 14:24” (chronometer) · “Recording your drive” · “14 min · 9.6 km · GTI” · Stop action. Small icon icon-android-notification.svg; not colourised.',
        'Notifications denied: full Quick Settings with the footer chip “1 app is active” highlighted, and the system Active apps dialog: DrivePerf (app icon) · “14 min” · Stop.',
      ],
      components: ['notification', 'shade', 'qsTile', 'activeAppsDialog (local mock)'],
      interactions: ['Tap → S-DRV-01. Stop (notification or Task Manager) → stops the FGS and ends the trip (prototype → S-HOME-01 + toast; the real app finalises in the service first, F-CAP-03).', 'A demoted Live Update is never re-promoted for this trip (F-CAP-12 AC2).'],
      motion: ['System only; updates ≥ 5 s apart; chronometer ticks itself. RM: n/a.'],
      a11y: [COMMON_LIVE_A11Y, 'Stop action “Stop drive”. The Task Manager row is the system’s own (“DrivePerf, 14 min, Stop”).'],
      platformDiffs: { ios: 'n/a (Android only). iOS shows nothing when notifications are denied (blue location indicator only).', android: 'NotificationCompat ongoing, foregroundServiceType=location, channel “Recording” IMPORTANCE_LOW, setOnlyAlertOnce, setUsesChronometer, addAction(Stop → PendingIntent to the service). On Android 13+ with POST_NOTIFICATIONS denied the FGS shows only in the Task Manager.' },
      analytics: ['fgs_notification_shown {promoted: false, reason: os_below_16|demoted|unavailable}', 'fgs_stop_tapped {from: notification|task_manager}'],
      copy: { Header: 'DrivePerf · Recording · {14:24}', Title: 'Recording your drive', Body: '{14} min · {9.6} km · {GTI}', Action: 'Stop', 'Task Manager': 'Active apps · DrivePerf · {14 min} · Stop', Toast: 'Drive ended. Decoding it now.' },
      freePro: 'Same for every plan.',
      data: 'liveTrip.states.manual (14:24 · 9.6 km · GTI); clock 5:46 = startedAt + elapsedS.',
      icons: ['stop', 'chevronDown', 'gear'],
      assets: ['logo-a-loop.svg', 'icon-android-notification.svg'],
    },
  });

  /* ---------- S-LIVE-07 · Android Quick Settings tile ---------- */
  screen('S-LIVE-07', {
    name: 'Quick Settings tile',
    wp: 'B', platforms: ['android'], presentation: 'live', tab: null,
    states: [{ id: 'default', label: 'Idle (“Start drive”)' }, { id: 'recording', label: 'Recording (active, timer)' }, { id: 'unavailable', label: 'Unavailable (no location permission)' }],
    stateParams: { recording: { live: 'manual' } },
    chrome: LIVE_CHROME, statusBar: OS_LIVE_SB,
    render(ctx) {
      const { ui, state, D } = ctx;
      const l = liveOf(ctx, 'manual');
      const st = state === 'recording' ? 'recording' : state === 'unavailable' ? 'unavailable' : 'idle';
      const tile = `<span class="os-tile-hit ui-press" ${ui.act(st === 'idle' ? { action: 'start', haptic: 'confirm', label: 'Start drive, DrivePerf tile' } : st === 'recording' ? { action: 'stop', haptic: 'confirm', label: `Recording, ${minsOf(l)} minutes. Double-tap to stop.` } : { nav: 'S-ONB-16', label: 'Start drive, unavailable. Location needed.' })}>${drTile(ctx, st, l)}</span>`;
      const T = QS_DEFAULT;
      const tiles = [qsOther(...T[0]), qsOther(...T[1]), tile, qsOther(...T[2]), qsOther(...T[3]), qsOther(...T[4]), qsOther(...T[5]), qsOther('Battery Saver', GL.power, false, '')];
      const time = st === 'recording' ? wall(D.liveTrip.states.manual.startedAt, l.elapsedS) : '5:31';
      return andShade(ctx, { time, full: true, tiles, footer: st === 'recording' ? '<span class="os-qs-active">1 app is active</span>' : '', chip: st === 'recording' ? `${minsOf(l)} min` : null, tag: 'Quick Settings · mock' });
    },
    mount(el, ctx) { ctx.ui.anim.pop(el.querySelector('.os-tile-hit .ui-qs'), 60); },
    actions: {
      start: (ctx) => { ctx.nav('S-LIVE-07/recording', { live: 'manual' }); return false; },
      stop: (ctx) => { ctx.nav('S-LIVE-07', { live: '' }); return false; },
    },
    spec: {
      purpose: 'One tap from Quick Settings to start or stop a drive before setting off, without opening the app (F-CAP-13).',
      layout: [
        'Android 16 Quick Settings mock (expanded): clock + date, brightness slider, 2-column large tiles (Internet, Bluetooth, DrivePerf, Flashlight, Do Not Disturb, Auto-rotate, Airplane mode, Battery Saver), page dots, footer (active apps · settings · power).',
        'DrivePerf tile (large, 68 tall, full radius): record glyph + “Start drive / DrivePerf” (inactive: surfaceContainerHighest) → “Recording / 14:24” (active: primary, on-primary text) → “Start drive / Location needed” (unavailable, 50% opacity).',
      ],
      components: ['qsTile', 'shade'],
      interactions: [
        'Idle tap → starts a manual trip (FGS from a user action), the tile turns active, the Live Update starts (S-LIVE-05). Recording tap → stops (S-DRV-04 on next open). Unavailable tap → opens DrivePerf on S-ONB-16 (manual-mode sheet with the Settings link).',
        'Long-press → DrivePerf Recording settings (S-SET-03) via ACTION_QS_TILE_PREFERENCES.',
      ],
      motion: ['System tile state transition only (shape/colour). RM: n/a.'],
      a11y: ['Tile label/state: “Start drive, DrivePerf, off” · “Recording, 14 minutes, on” · “Start drive, unavailable, Location needed”. Subtitle updates at most once a minute for screen readers.'],
      platformDiffs: { ios: 'n/a (Android only). iOS equivalent: S-LIVE-03 Control Center control.', android: 'TileService with Tile.STATE_INACTIVE / STATE_ACTIVE / STATE_UNAVAILABLE, subtitle via Tile.setSubtitle; requestListeningState on trip changes; startForegroundService from onClick (user action).' },
      analytics: ['tile_tapped {action: start|stop|unavailable}', 'trip_started {mode: manual, trigger: tile}'],
      copy: { Idle: 'Start drive · DrivePerf', Recording: 'Recording · {14:24}', Unavailable: 'Start drive · Location needed' },
      freePro: 'Same for every plan.',
      data: 'Recording uses liveTrip.states.manual (subtitle = elapsed time, status chip 14 min).',
      icons: ['record', 'gps', 'gear'],
      assets: [],
    },
  });

  /* ---------- S-LIVE-08 · Android Glance widget ---------- */
  function glance(ctx, kind) {
    const { ui, data, icons } = ctx;
    if (kind === 'recording') {
      const l = liveOf(ctx, 'manual'), v = carOf(ctx, l);
      return ui.widget(`<div class="wg-g is-rec"><div class="wg-g-main ui-press" ${ui.act({ nav: 'S-DRV-01', params: { live: 'manual' }, label: liveSpoken(l, v) })}><span class="wg-k"><i class="wg-dot"></i>Recording · ${ui.esc(v.nickname)}</span><span class="wg-nums"><b class="wg-time">${ui.esc(data.clock(l.elapsedS))}</b><b class="wg-time is-km">${ui.esc(data.km(l.distanceKm))}<span>km</span></b></span></div><span class="wg-go is-stop ui-press" ${ui.act({ action: 'stop', label: 'Stop drive', haptic: 'confirm' })}>${ui.icon('stop', 18)}Stop</span></div>`, { kind: 'glance', label: 'DrivePerf widget, recording' });
    }
    if (kind === 'Empty') return ui.widget(`<div class="wg-g is-msg"><div class="wg-g-main ui-press" ${ui.act({ nav: 'S-HOME-01/firstRunEmpty', label: 'No drives yet. Just drive.' })}><span class="wg-ic">${icons.loop({ size: 30, color: 'var(--md-sys-color-primary)', dot: 'currentColor' })}</span><b>No drives yet.</b><span class="wg-l">Just drive. Or tap Start before you set off.</span></div><span class="wg-go ui-press" ${ui.act({ action: 'start', label: 'Start drive', haptic: 'confirm' })}>${ui.icon('record', 18)}Start</span></div>`, { kind: 'glance', label: 'DrivePerf widget' });
    const t = data.trip(HERO_TRIP), s = t.smoothness;
    return ui.widget(`<div class="wg-g"><div class="wg-g-main ui-press" ${ui.act({ nav: 'S-STY-01', params: { trip: t.id }, label: `Last drive, ${t.partOfDay}, GTI. Smoothness ${s.score}, ${s.delta} above your usual.` })}>${ui.ring(s.score, 108, { label: false, stroke: 9, base: s.baseline })}<span class="wg-m-t"><span class="wg-k">${icons.loop({ size: 14, mono: true, color: 'currentColor' })}Last drive</span><b>${ui.esc(t.partOfDay)}</b><span class="wg-l">${ui.esc(data.vehicle(t.vehicleId).nickname)} · ${ui.num(data.km(t.distanceKm), 'km')}</span><span class="wg-delta"><b aria-hidden="true">▲</b>${ui.esc(data.signed(s.delta))} vs usual</span></span></div><span class="wg-go ui-press" ${ui.act({ action: 'start', label: 'Start drive', haptic: 'confirm' })}>${ui.icon('record', 18)}Start</span></div>`, { kind: 'glance', label: 'DrivePerf widget' });
  }
  screen('S-LIVE-08', {
    name: 'Home-screen widget (Glance)',
    wp: 'B', platforms: ['android'], presentation: 'live', tab: null,
    states: [{ id: 'default', label: 'Idle with last drive' }, { id: 'recording', label: 'Recording' }, { id: 'Empty', label: 'Empty' }],
    stateParams: { recording: { live: 'manual' } },
    chrome: LIVE_CHROME, statusBar: OS_LIVE_SB,
    render(ctx) {
      const { state, D } = ctx;
      const l = liveOf(ctx, 'manual');
      const scene = state === 'recording' ? [wall(D.liveTrip.states.manual.startedAt, l.elapsedS), 'Friday, 6 Nov'] : state === 'Empty' ? ['6:12', 'Monday, 28 Sep'] : ['8:20', 'Thursday, 5 Nov'];
      return andHome(ctx, glance(ctx, state), { time: scene[0], glance: scene[1], chip: state === 'recording' ? `${minsOf(l)} min` : null, apps: 8, tag: 'Home screen · mock · Glance widget' });
    },
    mount(el, ctx) { ctx.ui.anim.rings(el, { haptics: false }); },
    actions: {
      start: (ctx) => { ctx.nav('S-LIVE-05', { live: 'manual' }); return false; },
      stop: (ctx) => { ctx.nav('S-LIVE-08', { live: '' }); ctx.setState('default'); return false; },
    },
    spec: {
      purpose: 'The Android home-screen widget: the last drive at a glance and a one-tap Start; status only while recording (F-CAP-13).',
      layout: [
        'Pixel launcher mock: At a Glance date, the 4×2 Glance widget, labelled round app icons (incl. DrivePerf), dock, search bar.',
        'Widget (radius 28, surfaceContainer — the only DrivePerf surface allowed to follow dynamic colour; data colours stay fixed): 108 ring on the Cookie9Sided container (baseline notch) · “Last drive” kicker · “Thursday morning” (500 18) · “GTI · 37.9 km” · “▲ +7 vs usual” · Start (filled primary, 48 dp, record glyph) at the bottom right.',
        'Recording: “● Recording · GTI”, timer 40 + 9.6 km (DP Numerals), Stop (tonal). Empty: Loop 30 + “No drives yet.” + “Just drive. Or tap Start before you set off.” + Start.',
      ],
      components: ['widget', 'ring', 'homeScreen'],
      interactions: ['Start → manual trip from a user action (FGS) + the Live Update (S-LIVE-05). Stop → ends the trip.', 'Last drive area → S-STY-01 (unwatched) or S-TRP-01 through the D1 gate (S-DRV-05 while moving). Recording area → S-DRV-01. Empty → S-HOME-01 first run.'],
      motion: ['Glance updates only (no custom animation); the timer uses a Chronometer-backed text updated ≥ 60 s apart in the widget (the Live Update owns per-second time). Prototype: ring reveal on load. RM: static.'],
      a11y: ['Content descriptions: “Last drive, Thursday morning, GTI. Smoothness 91, 7 above your usual.” + “Start drive”. Recording: “DrivePerf, recording, GTI. 14 minutes, 9.6 kilometres.” + “Stop drive”.', 'Responsive sizes: 2×2 drops the car line; 4×1 shows ring + Start only.'],
      platformDiffs: { ios: 'n/a (Android only). iOS equivalent: S-LIVE-04.', android: 'Jetpack Glance (GlanceAppWidget, SizeMode.Responsive), GlanceTheme with dynamic colour for the surface, actionRunCallback for Start/Stop, actionStartActivity for the deep link (gated in-app).' },
      analytics: ['widget_tapped {action: start|stop|open_last, size}', 'trip_started {mode: manual, trigger: widget}'],
      copy: { Idle: 'Last drive · {Thursday morning} · {GTI} · {37.9} km · ▲ +7 vs usual · Start', Recording: 'Recording · {GTI} · {14:24} · {9.6} km · Stop', Empty: 'No drives yet. · Just drive. Or tap Start before you set off. · Start' },
      freePro: 'Same for every plan.',
      data: 'Idle: t20261105-0742 with the scene at Thu 5 Nov 8:20 (see S-LIVE-04). Recording: liveTrip.states.manual. Empty: install date 28 Sep.',
      icons: ['record', 'stop'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ---------- S-LIVE-09 · Auto-trip “Recording your drive” notification ---------- */
  screen('S-LIVE-09', {
    name: 'Auto-trip “Recording your drive” notification',
    wp: 'B', platforms: ['ios', 'android'], presentation: 'live', tab: null,
    states: [{ id: 'default', label: 'Recording' }, { id: 'replacedByDriveDecoded', label: 'Replaced by “Drive decoded.”' }],
    stateParams: { default: { live: 'auto' } },
    chrome: LIVE_CHROME, statusBar: OS_LIVE_SB,
    render(ctx) {
      const { state, D } = ctx;
      const rec = D.notifications.find((n) => n.id === 'n_recording'), dec = D.notifications.find((n) => n.id === 'n_decoded');
      const l = liveOf(ctx, 'auto');
      if (state === 'replacedByDriveDecoded') {
        const o = { title: dec.title, body: dec.body, nav: 'S-STY-01', params: { trip: dec.tripId }, label: `DrivePerf. ${dec.title} ${dec.body}` };
        return ctx.ios ? iosLock(ctx, iosNotif(ctx, { ...o, time: 'now' }), { time: '8:14', date: 'Thursday 5 November', tag: 'Lock Screen · mock · replaced on trip end' })
          : andLock(ctx, andNotif(ctx, { ...o, header: 'DrivePerf · Drive decoded · now', actions: [{ text: 'Watch Story', nav: 'S-STY-01', params: { trip: dec.tripId } }] }), { time: '8:14', date: 'Thursday, 5 November', notif: ['loop'], tag: 'Lock screen · mock · replaced on trip end' });
      }
      const time = wall(l.startedAt, l.elapsedS);
      if (ctx.ios) return iosLock(ctx, iosNotif(ctx, { title: rec.title, body: rec.body, time: `${minsOf(l)}m ago`, nav: 'S-DRV-01', params: { live: 'auto' }, label: `DrivePerf. ${rec.title}. ${rec.body}` }), { time, date: 'Friday 6 November', tag: 'Lock Screen · mock · passive notification' });
      return andLock(ctx, fgsNotif(ctx, l, { auto: true }), { time, date: 'Friday, 6 November', notif: ['loop'], tag: 'Lock screen · mock · foreground-service notification' });
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '.os-n', { stagger: 60 }); },
    actions: { stop: (ctx) => { ctx.setState('replacedByDriveDecoded'); return false; } },
    spec: {
      purpose: 'Tells the user, silently, that an automatic drive is being recorded, and is replaced by the post-drive notification when it ends (F-CAP-02/03/11). It never opens content (D1).',
      layout: [
        'iOS: Lock Screen mock with one passive notification (radius 24, system material): app icon (the Loop) · “Recording your drive” · “Tap to open. Stop it any time after you park.” · “21m ago”. No map, numbers or place names.',
        'Android: lock screen with the foreground-service notification: “DrivePerf · Recording · 21:50” · “Recording your drive” · “Started automatically · 21 min · 17.2 km · GTI” · Stop. On Android 16 it is promoted to the Live Update after confirmation (S-LIVE-05 autoRecording).',
        'Replaced: the same slot shows “Drive decoded.” · “Home → Work · Smoothness 91” (posted only once parked; Android adds Watch Story).',
      ],
      components: ['notification', 'lockScreen'],
      interactions: ['Tap “Recording your drive” → DrivePerf on S-DRV-01 (auto trip state), never to content. Android Stop → ends the trip (prototype → replaced).', 'Tap “Drive decoded.” → S-STY-01 for that trip through the gate (S-DRV-05 if still moving).'],
      motion: ['System presentation only; iOS interruption level .passive (no sound, no vibration, no wake); Android low-importance channel. RM: n/a.'],
      a11y: ['Notification reads “DrivePerf. Recording your drive. Tap to open. Stop it any time after you park.”', 'No haptic or sound (motion.md §6: never on notifications).'],
      platformDiffs: { ios: 'UNNotificationContent with interruptionLevel .passive, threadIdentifier = trip id; replaced by the Drive decoded notification using the same identifier (no Time Sensitive). With notifications denied nothing shows (blue location indicator only).', android: 'This is the FGS notification (channel “Recording”, IMPORTANCE_LOW, ongoing); on trip end the service posts the Drive decoded notification on its own channel and stops.' },
      analytics: ['auto_trip_notification_posted', 'auto_trip_notification_tapped', 'notification_sent {type: drive_decoded}'],
      copy: { Recording: 'Recording your drive · Tap to open. Stop it any time after you park.', 'Android body': 'Started automatically · {21} min · {17.2} km · {GTI}', Replaced: 'Drive decoded. · Home → Work · Smoothness 91' },
      freePro: 'Same for every plan.',
      data: 'DP_DATA.notifications n_recording + liveTrip.states.auto (clock 5:53 = startedAt + elapsedS, “21m ago”); replaced uses n_decoded (t20261105-0742, scene Thu 5 Nov 8:14).',
      icons: ['stop', 'chevronDown'],
      assets: ['logo-a-loop.svg', 'icon-android-notification.svg'],
    },
  });

  /* ---------- S-LIVE-10 · “Auto-detect may be paused” watchdog ---------- */
  screen('S-LIVE-10', {
    name: '“Auto-detect may be paused” watchdog notification',
    wp: 'B', platforms: ['ios', 'android'], presentation: 'live', tab: 'drives',
    states: [{ id: 'default', label: 'Def (watchdog notification)' }, { id: 'afterForceStop', label: 'After force-stop (in-app card; iOS: after the watchdog)' }],
    chrome: (ctx) => (ctx.state === 'afterForceStop' ? {} : LIVE_CHROME),
    statusBar: (ctx) => (ctx.state === 'afterForceStop' ? (ctx.theme === 'light' ? 'dark' : 'light') : OS_LIVE_SB(ctx)),
    render(ctx) {
      const { ui, D, data } = ctx;
      const n = D.notifications.find((x) => x.id === 'n_watchdog');
      const last = D.health.presets.allGood.lastCheckIn;
      if (ctx.state === 'afterForceStop') {
        const card = ui.card(`<div class="wd-card"><span class="wd-ic">${ui.icon('shield', 22)}</span><div class="wd-t"><b>Auto-detect is back on</b><p>${ctx.android ? 'DrivePerf was force-stopped, which pauses automatic trips until the app is opened again. Opening it switched auto-detect back on.' : 'It may have paused after DrivePerf was swiped away or the phone restarted. Opening the app switched it back on.'} Drives during the pause weren’t recorded.</p>
          <div class="wd-a">${ui.button('Got it', { kind: 'tonal', size: 's', action: 'gotIt' })}${ui.button('Why this happens', { kind: 'text', size: 's', nav: 'S-SET-06' })}</div></div></div>`, { cls: 'wd-cardwrap', label: 'Auto-detect is back on.' });
        const chrome = `<div class="dp-chrome wd-chrome">${DP.shell.accessoryHtml('ready', { ...ctx, capture: { ...ctx.capture, lastCheckIn: D.meta.today } }, ctx.platform)}${ui.tabBar('drives')}</div>`;
        return homeRender({ ...ctx, state: 'default' }, { pre: [card], fixed: chrome });
      }
      const fire = new Date(new Date(last).getTime() + 864e5).toISOString();
      const o = { title: n.title, body: n.body, nav: 'S-SET-05', label: `DrivePerf. ${n.title} ${n.body}` };
      if (ctx.ios) return iosLock(ctx, iosNotif(ctx, { ...o, time: 'now' }), { time: wall(fire), date: 'Saturday 7 November', tag: 'Lock Screen · mock · watchdog (24 h without a check-in)' });
      return andLock(ctx, andNotif(ctx, { ...o, header: 'DrivePerf · Capture problems · now', actions: [{ text: 'Open DrivePerf', nav: 'S-SET-05' }] }), { time: wall(fire), date: `Saturday, 7 November`, notif: ['loop'], tag: `Lock screen · mock · watchdog (no check-in since ${data.time(last)} Fri)` });
    },
    mount(el, ctx) { if (ctx.state === 'afterForceStop') { ctx.ui.anim.rise(el, '[data-rise]', { stagger: 40 }); return undefined; } ctx.ui.anim.rise(el, '.os-n', { stagger: 60 }); return undefined; },
    actions: { gotIt: (ctx) => { ctx.nav('S-HOME-01'); return false; } },
    spec: {
      purpose: 'Makes sure automatic capture never stops silently (F-CAP-14): iOS posts a pre-scheduled local notification if DrivePerf hasn’t been woken for 24 h; Android shows a one-time in-app card after a force-stop, once it has re-armed.',
      layout: [
        'Default: Lock Screen mock with one notification — “Auto-detect may be paused.” · “Open DrivePerf once to switch it back on.” (iOS system card; Android “DrivePerf · Capture problems · now” + “Open DrivePerf” action). Scene: Sat 7 Nov 7:12, 24 h after the last check-in.',
        'afterForceStop (in-app): S-HOME-01 with a one-time card above “This week”: shield glyph in a 40 tonal disc · “Auto-detect is back on” · explanation · Got it (tonal small) + Why this happens (text → S-SET-06 Reliability guide). The normal tab bar and capture status (“Auto-detect on · ready · Last check-in just now”) stay.',
      ],
      components: ['notification', 'lockScreen', 'card', 'button', 'largeTitle', 'tabBar', 'accessory'],
      interactions: ['Notification tap → S-SET-05 (Health Check), which re-arms on open and updates “Last check-in”. Android action “Open DrivePerf” → same.', 'Card: Got it → dismissed for good (prototype → S-HOME-01); Why this happens → S-SET-06.', 'Held until parked like every other notification (D4); at most once per 72 h; never during a pause (F-CAP-15).'],
      motion: ['Notification: system. Card: rises with the Home blocks (8 px, spring.default); dismiss collapses its height (spring.default). RM: cross-fade.'],
      a11y: ['Notification reads in full; the card is announced once when Home appears (polite), title first.', 'Buttons “Got it” / “Why this happens”, ≥ 44 pt / 48 dp.'],
      platformDiffs: { ios: 'UNCalendarNotificationTrigger rescheduled 24 h ahead on every wake (DTS pattern, A3 §1.5); category capture_problem; iOS can’t detect force-quit, so its card variant says “may have paused after DrivePerf was swiped away or the phone restarted”.', android: 'ApplicationStartInfo.wasForceStopped() on next start → re-register transitions + geofences, then show the card once; BOOT_COMPLETED re-arms within 10 s of first unlock. The watchdog notification uses the “Capture problems” channel.' },
      analytics: ['watchdog_fired', 'watchdog_opened', 'rearmed {reason: boot|force_stop|launch}', 'force_stop_card_viewed', 'force_stop_card_dismissed {action: got_it|why}'],
      copy: { Notification: 'Auto-detect may be paused. · Open DrivePerf once to switch it back on.', 'Card (Android)': 'Auto-detect is back on · DrivePerf was force-stopped, which pauses automatic trips until the app is opened again. Opening it switched auto-detect back on. Drives during the pause weren’t recorded.', 'Card (iOS)': 'Auto-detect is back on · It may have paused after DrivePerf was swiped away or the phone restarted. Opening the app switched it back on. Drives during the pause weren’t recorded.', Buttons: 'Got it · Why this happens' },
      freePro: 'Same for every plan.',
      data: 'DP_DATA.notifications n_watchdog; fire time = health.presets.allGood.lastCheckIn + 24 h (Sat 7 Nov 7:12, derived). The in-app variant reuses the S-HOME-01 default body (this week + latest drives).',
      icons: ['shield', 'chevronDown'],
      assets: ['logo-a-loop.svg', 'icon-android-notification.svg'],
    },
  });
})();
