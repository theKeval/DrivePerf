/* WP-F · Notifications, recaps, Wrapped & settings — screen definitions (DRIVE-32). Contract: prototypes/app/AUTHORING.md.
   Register each screen with DP.screen('S-XXX-NN', {...}). Only the WP-F owner edits this file. */
(function () {
  const { screen } = window.DP;
  const D = window.DP_DATA, I = window.DP_ICONS, M = window.DP_MOTION;

  /* ==========================================================================================================
     Local helpers (candidates for core primitives — listed in the WP-F report)
     ========================================================================================================== */
  const still = (ctx, el) => ctx.shot || ctx.rm || M.reduced(el);
  const ROUND = (n, d = 1) => Math.round(n * 10 ** d) / 10 ** d;

  /** OS status bar drawn inside an OS-surface mock, so its clock can match the scene (the shell clock is fixed at 9:41 / 7:30).
      Use with chrome: { statusBar: false }. iOS Lock Screen shows no clock in the bar (the big clock carries it). */
  function osBar(ctx, time) {
    const sig = ctx.ios
      ? '<svg width="18" height="12" viewBox="0 0 18 12" aria-hidden="true"><g fill="currentColor"><rect x="0" y="7.5" width="3" height="4.5" rx="1"/><rect x="5" y="5" width="3" height="7" rx="1"/><rect x="10" y="2.5" width="3" height="9.5" rx="1"/><rect x="15" y="0" width="3" height="12" rx="1"/></g></svg><svg width="16" height="12" viewBox="0 0 16 12" aria-hidden="true"><path d="M8 11.5L5.6 8.9a3.4 3.4 0 0 1 4.8 0zM3.4 6.7a6.5 6.5 0 0 1 9.2 0l-1.3 1.4a4.6 4.6 0 0 0-6.6 0zM1.2 4.4a9.6 9.6 0 0 1 13.6 0l-1.3 1.3a7.8 7.8 0 0 0-11 0z" fill="currentColor"/></svg><span class="wf-osbar-bat"><i></i></span>'
      : '<svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M8 13.5L.6 5.2a10.6 10.6 0 0 1 14.8 0z" fill="currentColor"/></svg><svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M1.5 14.5h13v-13z" fill="currentColor"/></svg><svg width="10" height="16" viewBox="0 0 10 16" aria-hidden="true"><rect x="1" y="2" width="8" height="13" rx="1.6" fill="none" stroke="currentColor" stroke-width="1.4"/><rect x="2.6" y="5.5" width="4.8" height="8" rx=".6" fill="currentColor"/><rect x="3.5" y=".5" width="3" height="1.6" rx=".5" fill="currentColor"/></svg>';
    return `<div class="wf-osbar" aria-hidden="true"><span>${ctx.ios ? '' : ctx.ui.esc(time || '')}</span><span class="wf-osbar-i">${sig}</span></div>`;
  }

  /** Health status glyph (components.md §17): green = filled check disc · amber = triangle · red = octagon with "!". Shape carries the state. */
  function hcGlyph(status, size = 22) {
    const lbl = { green: 'OK', amber: 'Worth knowing', red: 'Needs attention', paused: 'Paused' }[status] || 'OK';
    if (status === 'red') return `<svg class="wf-hcg is-red" width="${size}" height="${size}" viewBox="0 0 20 20" role="img" aria-label="${lbl}"><path d="M6.5 1.5h7l5 5v7l-5 5h-7l-5-5v-7z" fill="var(--dp-critical)"/><path d="M10 5.5v5.5" stroke="#fff" stroke-width="2" stroke-linecap="round"/><circle cx="10" cy="14.3" r="1.2" fill="#fff"/></svg>`;
    if (status === 'amber') return `<svg class="wf-hcg is-amber" width="${size}" height="${size}" viewBox="0 0 20 20" role="img" aria-label="${lbl}"><path d="M10 1.8l8.7 15.4H1.3z" fill="var(--dp-warning)" stroke="var(--dp-warning)" stroke-width="1.2" stroke-linejoin="round"/><path d="M10 7.2v4.6" stroke="#1A1200" stroke-width="1.9" stroke-linecap="round"/><circle cx="10" cy="14.4" r="1.1" fill="#1A1200"/></svg>`;
    return `<svg class="wf-hcg is-green" width="${size}" height="${size}" viewBox="0 0 20 20" role="img" aria-label="${lbl}"><circle cx="10" cy="10" r="9" fill="var(--dp-data-quality-good)"/><path d="M6 10.3l2.6 2.6L14 7.5" stroke="var(--dp-bg-grouped)" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
  }

  /** Centred alert / M3 dialog over a push screen (the engine only centres dialogs for 'dialog' presentations). */
  const overDialog = (ctx, o) => `<div class="wf-dlg"><div class="wf-dlg-scrim" data-action="${o.dismiss || 'closeDlg'}" aria-hidden="true"></div><div class="wf-dlg-box">${ctx.ui.dialog(o)}</div></div>`;

  /** Non-interactive OS Settings mock opened by a "Fix" / "Open Settings" action. Tap anywhere returns to DrivePerf. */
  function sysPage(ctx, kind) {
    const { ui } = ctx, ios = ctx.ios;
    const P = {
      location: ios
        ? { title: 'Location', back: 'DrivePerf', groups: [{ header: 'Allow location access', rows: [{ label: 'Never' }, { label: 'Ask Next Time Or When I Share' }, { label: 'While Using the App', check: true }, { label: 'Always', hi: true }], footer: 'DrivePerf uses your location in the background only to record the route of drives it detects.' }, { rows: [{ label: 'Precise Location', toggle: true }] }] }
        : { title: 'Location permission', groups: [{ header: 'Location access for this app', rows: [{ label: 'Allow all the time', radio: true, hi: true }, { label: 'Allow only while using the app', radio: true, on: true }, { label: 'Ask every time', radio: true }, { label: 'Don’t allow', radio: true }] }, { rows: [{ label: 'Use precise location', toggle: true }] }] },
      notifications: ios
        ? { title: 'Notifications', back: 'DrivePerf', groups: [{ rows: [{ label: 'Allow Notifications', toggle: false, hi: true }] }, { header: 'Alerts', rows: [{ label: 'Lock Screen' }, { label: 'Notification Centre' }, { label: 'Banners' }] }] }
        : { title: 'Notifications', groups: [{ rows: [{ label: 'All DrivePerf notifications', toggle: false, hi: true }] }, { header: 'Categories', rows: [{ label: 'Drive decoded', toggle: false }, { label: 'Routes and records', toggle: false }, { label: 'Weekly recap and Wrapped', toggle: false }, { label: 'Capture problems', toggle: false }, { label: 'Recording', sub: 'Shows while a drive records', toggle: true }] }] },
      motion: ios
        ? { title: 'Motion & Fitness', back: 'Privacy & Security', groups: [{ rows: [{ label: 'Fitness Tracking', toggle: true }] }, { header: 'Allow access', rows: [{ label: 'DrivePerf', toggle: false, hi: true }] }] }
        : { title: 'Physical activity permission', groups: [{ header: 'Physical activity access for this app', rows: [{ label: 'Allow', radio: true, hi: true }, { label: 'Don’t allow', radio: true, on: true }] }] },
      app: { title: 'DrivePerf', back: 'Apps', groups: [{ header: 'Allow DrivePerf to access', rows: [{ label: 'Location', value: 'Always' }, { label: 'Motion & Fitness', toggle: true }, { label: 'Notifications', value: 'Banners, Sounds' }, { label: 'Live Activities', toggle: true }, { label: 'Background App Refresh', toggle: false, hi: true }] }] },
      liveActivities: { title: 'DrivePerf', back: 'Apps', groups: [{ header: 'Allow DrivePerf to access', rows: [{ label: 'Location', value: 'Always' }, { label: 'Live Activities', toggle: false, hi: true }, { label: 'Background App Refresh', toggle: true }] }] },
      battery: { title: 'App battery usage', groups: [{ header: 'DrivePerf', rows: [{ label: 'Unrestricted', sub: 'Allow battery usage in background without restrictions', radio: true, hi: true }, { label: 'Optimized', sub: 'Optimise based on your usage', radio: true, on: true }, { label: 'Restricted', radio: true }] }] },
    }[kind] || null;
    if (!P) return '';
    return `<div class="wf-sys ui-press" data-action="closeSys" role="button" tabindex="0" aria-label="${ui.esc(`${ios ? 'iOS' : 'Android'} Settings mock: ${P.title}. Tap to return to DrivePerf.`)}">${ui.sysSettings(P)}<span class="wf-sys-back">${ui.icon(ios ? 'chevronLeft' : 'arrowBack', 14)}Tap to return to DrivePerf</span></div>`;
  }

  /* ==========================================================================================================
     S-NTF-01 · Notification catalogue (all templates) — OS Lock Screen (iOS) / notification shade (Android)
     ========================================================================================================== */
  const N = Object.fromEntries(D.notifications.map((n) => [n.id, n]));
  const CHANNEL = { decoded: 'Drive decoded', routes: 'Routes and records', recap: 'Weekly recap and Wrapped', capture: 'Capture problems', plan: 'Account and plan', recording: 'Recording' };

  /** One catalogue entry per state: the scene (date/time on the surface), the notification and where a tap goes (all via the D1 gate). */
  function ntfScene(ctx) {
    const { data } = ctx;
    const tHome = data.trip('t20261105-1745');
    const decoded = { title: 'Drive decoded.', body: `${tHome.label} · Smoothness ${tHome.smoothness.score}` }; // template "{label} · Smoothness {n}" (F-STY-02)
    const S = {
      default: { date: 'Thursday 5 November', dateA: 'Thu, 5 Nov', time: '6:19', n: decoded, channel: CHANNEL.decoded, nav: 'S-STY-01', params: { trip: tHome.id }, acts: [['Watch Story', 'S-STY-01', { trip: tHome.id }]], level: 'Active' },
      firstStoryReady: { date: 'Monday 28 September', dateA: 'Mon, 28 Sep', time: '6:53', n: N.n_first, channel: CHANNEL.decoded, nav: 'S-STY-01/firstDrive', params: { trip: 't20260928-1842' }, acts: [['Watch Story', 'S-STY-01/firstDrive', { trip: 't20260928-1842' }]], level: 'Active' },
      s2DrivesCoalesced: { date: 'Wednesday 4 November', dateA: 'Wed, 4 Nov', time: '8:04', n: N.n_coalesced, channel: CHANNEL.decoded, nav: 'S-HOME-01', params: {}, acts: [['See drives', 'S-HOME-01', {}]], level: 'Active' },
      routeDiscovered: { date: 'Wednesday 4 November', dateA: 'Wed, 4 Nov', time: '8:05', n: N.n_route, channel: CHANNEL.routes, nav: 'S-RTE-03', params: { route: 'r_cand_gym_home' }, acts: [['Name it', 'S-RTE-03', { route: 'r_cand_gym_home' }], ['Not now', null]], level: 'Passive' },
      routeRecord: { date: 'Thursday 5 November', dateA: 'Thu, 5 Nov', time: '8:14', n: N.n_record, channel: CHANNEL.routes, nav: 'S-STY-01/newRouteRecord', params: { trip: 't20261105-0742' }, acts: [['Watch Story', 'S-STY-01/newRouteRecord', { trip: 't20261105-0742' }], ['See route', 'S-RTE-02', { route: 'r_commute' }]], level: 'Active' },
      weeklyRecap: { date: 'Sunday 1 November', dateA: 'Sun, 1 Nov', time: '6:00', n: N.n_week, channel: CHANNEL.recap, nav: 'S-RCP-01', params: { recap: 'w2026-10-26' }, acts: [['Play recap', 'S-RCP-01', { recap: 'w2026-10-26' }]], level: 'Passive' },
      monthlyWrapped: { date: 'Sunday 1 November', dateA: 'Sun, 1 Nov', time: '10:00', n: N.n_wrapped, channel: CHANNEL.recap, nav: 'S-WRP-01', params: {}, acts: [['Open Wrapped', 'S-WRP-01', {}]], level: 'Passive' },
      captureProblem: { date: 'Friday 6 November', dateA: 'Fri, 6 Nov', time: '5:02', n: ctx.ios ? N.n_capture : { ...N.n_capture, body: 'Location access changed to “While using the app”. Two taps to fix it.' }, channel: CHANNEL.capture, nav: 'S-SET-05/red', params: { health: 'alwaysLost' }, acts: [['Fix it', 'S-SET-05/red', { health: 'alwaysLost' }]], level: 'Time Sensitive' },
      missedTripCheck: { date: 'Friday 6 November', dateA: 'Fri, 6 Nov', time: '6:12', n: N.n_missed, channel: CHANNEL.capture, nav: 'S-NTF-02', params: {}, acts: [['Yes, I drove', 'S-NTF-02', {}], ['I was a passenger', 'S-NTF-02/iWasAPassenger', {}]], level: 'Passive' },
      previewEndsIn2Days: { date: 'Saturday 10 October', dateA: 'Sat, 10 Oct', time: '10:00', n: N.n_preview, channel: CHANNEL.plan, nav: 'S-SUB-01/day13', params: { ent: 'previewEnding' }, acts: [['See what Pro keeps', 'S-SUB-01/day13', { ent: 'previewEnding' }]], level: 'Passive' },
      lapsedOnce: { date: 'Friday 27 November', dateA: 'Fri, 27 Nov', time: '10:00', n: N.n_lapsed, channel: CHANNEL.plan, nav: 'S-HOME-01', params: {}, acts: [], level: 'Passive' },
    };
    S.heldWhileDriving = { date: 'Sunday 1 November', dateA: 'Sun, 1 Nov', time: '6:22', n: N.n_week, channel: CHANNEL.recap, held: true };
    S.lockScreenAndExpandedRenderings = { ...S.default, expanded: true };
    return S[ctx.state] || S.default;
  }

  /** Notification card in the OS style (uses the engine's .ui-notif look; adds tap targets for body and actions). */
  function notif(ctx, sc, o = {}) {
    const { ui } = ctx, n = sc.n;
    const tap = sc.nav ? ui.act({ nav: sc.nav, params: sc.params, label: `DrivePerf notification. ${n.title} ${n.body}` }) : `role="group" aria-label="${ui.esc(`DrivePerf notification. ${n.title} ${n.body}`)}"`;
    const acts = o.expanded && sc.acts && sc.acts.length ? `<div class="ui-notif-acts wf-n-acts">${sc.acts.map(([t, nav, params]) => `<span class="ui-press" ${nav ? ui.act({ nav, params }) : ui.act({ toast: 'Dismissed' })}>${ui.esc(t)}</span>`).join('')}</div>` : '';
    const rich = o.expanded && o.rich ? `<div class="wf-n-rich">${o.rich}</div>` : '';
    const time = o.time || 'now';
    if (ctx.ios) return `<div class="ui-notif wf-n ${o.expanded ? 'is-expanded' : ''} ${o.cls || ''}" ${tap}><span class="ui-notif-app">${I.loop({ size: 22, color: '#5BE7C4', dot: '#fff' })}</span><div class="ui-notif-b"><div class="ui-notif-h"><b>${ui.esc(n.title)}</b><span>${ui.esc(time)}</span></div><p>${ui.esc(n.body)}</p></div>${rich}${acts}</div>`;
    return `<div class="ui-notif wf-n ${o.expanded ? 'is-expanded' : ''} ${o.cls || ''}" ${tap}><div class="ui-notif-h"><span class="ui-notif-app">${I.loop({ size: 14, mono: true, color: 'currentColor' })}</span><span>DrivePerf · ${ui.esc(sc.channel)} · ${ui.esc(time)}</span>${o.chip ? `<em>${ui.esc(o.chip)}</em>` : `<span class="wf-n-chev">${ui.icon(o.expanded ? 'up' : 'down', 16)}</span>`}</div><b>${ui.esc(n.title)}</b><p>${ui.esc(n.body)}</p>${rich}${acts}</div>`;
  }

  /** Rich (expanded / long-press) preview for Drive decoded: share-safe glyph + ring only (no map, no addresses: F-STY-02). */
  function richDecoded(ctx, t) {
    const v = ctx.data.vehicle(t.vehicleId);
    return `<div class="wf-n-art" style="--wash:var(--dp-car-${v.colour})">${ctx.ui.glyph(t.glyph, 88, { color: '#5BE7C4', width: 3.4 })}<div data-theme="dark">${ctx.ui.ring(t.smoothness.score, 92, { base: t.smoothness.baseline, stroke: 8, label: false, aria: `Smoothness ${t.smoothness.score}` })}</div><span class="wf-n-art-k">${ctx.ui.esc(t.partOfDay)} · ${ctx.ui.esc(v.nickname)}</span></div>`;
  }

  /** Android notification shade with a scene date (the engine's ui.shade() date is fixed to Fri 6 Nov). */
  function shadeAt(ctx, content, sc, o = {}) {
    const tiles = ['Internet', 'Bluetooth', 'Do not disturb', 'Flashlight'].map((t) => `<span class="ui-qs ui-qs--off"><span><b>${t}</b></span></span>`).slice(0, o.tile ? 3 : 4).join('');
    return `<div class="ui-os ui-shade wf-shade"><div class="ui-wall"></div><div class="ui-shade-p"><div class="ui-shade-time">${ctx.ui.esc(sc.time)}<small>${ctx.ui.esc(sc.dateA)}</small></div><div class="ui-shade-tiles">${o.tile || ''}${tiles}</div><div class="wf-shade-k">Notifications</div><div class="ui-shade-n">${content}</div><div class="wf-shade-f"><span>Manage</span><span>Clear all</span></div></div></div>`;
  }

  function ntfRender(ctx) {
    const { ui, data } = ctx, st = ctx.state;
    const sc = ntfScene(ctx);
    const tag = (txt) => `<span class="wf-tag">${ui.esc(txt)}</span>`;
    // ---- held while a drive records: the recording surface shows, the recap waits ----
    if (st === 'heldWhileDriving') {
      const live = data.live('auto');
      const heldCard = `<div class="wf-held" role="note" aria-label="Held: ${ui.esc(sc.n.title)} ${ui.esc(sc.n.body)}. Delivered when you’ve parked."><span class="wf-held-k">${ui.icon('pause', 13)}Held until you’ve parked</span>${notif(ctx, { ...sc, nav: null }, { cls: 'is-ghost', time: 'waiting' })}</div>`;
      if (ctx.ios) return ui.lockScreen(`${heldCard}${ui.liveActivity(live)}`, { date: sc.date, time: sc.time }) + osBar(ctx) + tag('iOS Lock Screen · mock · a drive is recording');
      const ongoing = `<div class="ui-notif wf-n is-ongoing" role="group" aria-label="Recording your drive, ${Math.round(live.elapsedS / 60)} minutes, ${live.distanceKm} kilometres"><div class="ui-notif-h"><span class="ui-notif-app">${I.loop({ size: 14, mono: true, color: 'currentColor' })}</span><span>DrivePerf · ${CHANNEL.recording}</span><em>${Math.round(live.elapsedS / 60)} min</em></div><b>${ui.esc(N.n_recording.title)}</b><p>${ui.num(Math.round(live.elapsedS / 60), 'min')} · ${ui.num(live.distanceKm, 'km')} · ${ui.esc(data.vehicle(live.vehicleId).nickname)}</p><div class="ui-notif-acts"><span>${ui.icon('stop', 14)} Stop</span><span>I’m a passenger</span></div></div>`;
      return shadeAt(ctx, ongoing + heldCard, sc, { tile: ui.qsTile({ state: 'recording', sub: data.clock(live.elapsedS) }) }) + osBar(ctx, sc.time) + tag('Android shade · mock · a drive is recording');
    }
    // ---- Lock Screen and expanded renderings ----
    if (st === 'lockScreenAndExpandedRenderings') {
      const t = data.trip('t20261105-1745');
      if (ctx.ios) {
        const stack = `<div class="wf-group" aria-label="3 DrivePerf notifications">${notif(ctx, sc, { time: 'now' })}<i class="wf-group-b" aria-hidden="true"></i><i class="wf-group-b is-2" aria-hidden="true"></i></div>`;
        const expanded = `<div class="wf-lp" role="dialog" aria-label="Expanded notification">${notif(ctx, sc, { expanded: true, rich: richDecoded(ctx, t), time: 'now' })}</div>`;
        return `<div class="wf-lsx">${ui.lockScreen(stack, { date: sc.date, time: sc.time })}<div class="wf-lsx-dim" aria-hidden="true"></div>${expanded}</div>` + osBar(ctx) + tag('iOS · long-press expanded over the Lock Screen · mock');
      }
      const rec = ntfSceneFor(ctx, 'routeRecord'), wk = ntfSceneFor(ctx, 'weeklyRecap');
      const list = `${notif(ctx, sc, { expanded: true, rich: richDecoded(ctx, t), time: 'now' })}${notif(ctx, rec, { time: '10 h' })}${notif(ctx, wk, { time: '4 d' })}`;
      return `<div class="wf-als">${ui.lockScreen(list, { date: sc.dateA, time: sc.time })}</div>` + osBar(ctx, sc.time) + tag('Android Lock Screen · expanded + collapsed · mock');
    }
    // ---- one template on its surface ----
    if (ctx.ios) return ui.lockScreen(notif(ctx, sc, { time: 'now' }), { date: sc.date, time: sc.time }) + osBar(ctx) + tag(`iOS Lock Screen · ${sc.level} · mock`);
    return shadeAt(ctx, notif(ctx, sc, { expanded: true, time: 'now' }), sc) + osBar(ctx, sc.time) + tag(`Android shade · channel “${sc.channel}” · mock`);
  }
  const ntfSceneFor = (ctx, state) => ntfScene({ ...ctx, state });

  screen('S-NTF-01', {
    name: 'Notification catalogue (all templates)',
    wp: 'F', platforms: ['ios', 'android'], presentation: 'live', tab: null,
    states: [
      { id: 'default', label: 'Drive decoded' },
      { id: 'firstStoryReady', label: 'First Story ready' },
      { id: 's2DrivesCoalesced', label: '2 drives coalesced' },
      { id: 'routeDiscovered', label: 'Route discovered' },
      { id: 'routeRecord', label: 'Route record (replaces Drive decoded)' },
      { id: 'weeklyRecap', label: 'Weekly recap' },
      { id: 'monthlyWrapped', label: 'Monthly Wrapped' },
      { id: 'captureProblem', label: 'Capture problem' },
      { id: 'missedTripCheck', label: 'Missed-trip check' },
      { id: 'previewEndsIn2Days', label: 'Preview ends in 2 days' },
      { id: 'lapsedOnce', label: 'Lapsed once (sent once ever)' },
      { id: 'lockScreenAndExpandedRenderings', label: 'Lock Screen and expanded renderings' },
      { id: 'heldWhileDriving', label: 'Held while a drive records (extra)' },
    ],
    chrome: { statusBar: false },
    statusBar: (ctx) => (ctx.theme === 'light' ? 'dark' : 'light'),
    render: ntfRender,
    mount(el, ctx) {
      const n = el.querySelector('.wf-lp .wf-n, .wf-n:not(.is-ghost):not(.is-ongoing)');
      if (!n || still(ctx, el)) return;
      // Hero: the notification arrives (iOS drops in from the stack top · Android slides in); the expanded preview scales from the stack.
      if (el.querySelector('.wf-lp')) M.animate(el.querySelector('.wf-lp'), [{ opacity: 0, transform: 'scale(.92)' }, { opacity: 1, transform: 'scale(1)' }], { preset: 'default', delay: 120 });
      else M.animate(n, [{ opacity: 0, transform: ctx.ios ? 'translateY(-14px) scale(.96)' : 'translateY(-10px)' }, { opacity: 1, transform: 'none' }], { preset: 'default', delay: 150 });
      ctx.ui.anim.rings(el, { haptics: false });
    },
    spec: {
      purpose: 'Every local notification DrivePerf sends, rendered on the real OS surface (iOS Lock Screen, Android notification shade / Lock Screen) with fixture copy, so native developers can match title, body, category/channel, actions and tap targets. All are local (no push server) and none arrives while a driver trip records (F-NTF-01).',
      layout: [
        'iOS: Lock Screen mock (wallpaper, date, 104 pt clock, flashlight/camera). The notification sits at the bottom of the stack: 38 pt app icon (The Loop on night), bold title (sentence case, full stop), body, relative time. Long-press expanded rendering = dimmed, blurred Lock Screen + the expanded platter with a rich preview (share-safe route glyph + Smoothness ring, part of day · car) and action buttons.',
        'Android: notification shade mock (clock + date, Quick Settings tiles, “Notifications” header, Manage / Clear all). Each notification: small mono Loop icon · “DrivePerf · {channel} · now” · expand chevron, title (Title Medium), body (Body Medium), action buttons as tonal pills when expanded.',
        'Android Lock Screen rendering: the expanded Drive decoded card on top, then collapsed Route record and Weekly recap cards.',
        'Held while driving (extra state): the recording surface (iOS Live Activity · Android ongoing “Recording your drive” notification with the minutes chip and the QS tile in “Recording”) plus a ghosted card “Held until you’ve parked” showing what will be delivered after the trip.',
        'A small black “· mock” tag marks every frame as a non-interactive OS surface (only the notification itself and its actions are tappable in the prototype).',
      ],
      components: ['lockScreen', 'notification', 'shade', 'qsTile', 'liveActivity', 'ring', 'glyph'],
      interactions: [
        'Every tap goes through the D1 gate (screen-map §2.2): with a driver trip active it lands on S-DRV-01; with moving=1, Story/Recap/Wrapped/Paywall targets wait on S-DRV-05.',
        'Drive decoded → S-STY-01 (trip). First Story ready → S-STY-01/firstDrive. 2 drives decoded → S-HOME-01 (both new Story cards). Looks like a regular → S-RTE-03 (route candidate) · action “Not now” dismisses. New route record → S-STY-01/newRouteRecord · action “See route” → S-RTE-02.',
        'Your week, decoded. → S-RCP-01 (recap w2026-10-26). October, wrapped. → S-WRP-01. Recording is paused. → S-SET-05/red (Always lost). Did you drive around 5 pm? → S-NTF-02 (actions: Yes, I drove · I was a passenger → S-NTF-02/iWasAPassenger). Pro Preview ends in 2 days → S-SUB-01/day13. Recording is still on. → opens the app (S-HOME-01).',
        'iOS: tap = open, long-press = expanded with actions, swipe left = Options/Clear. Android: tap = open, chevron = expand, swipe = dismiss; channel settings from long-press.',
      ],
      motion: [
        'Arrival: iOS platter drops 14 pt and scales 0.96 → 1 (spring.default); Android card slides down 10 dp and fades in (spring.default). The expanded preview scales 0.92 → 1 from the stack. The ring in the rich preview runs the ring reveal without haptics.',
        'No haptics are ever attached to notifications (motion.md §6). RM: notifications appear in place (220 ms cross-fade).',
      ],
      a11y: [
        'Each notification is one element read as “DrivePerf notification. Drive decoded. Work → Home · Smoothness 85.”; actions are separate buttons.',
        'Rich preview ring: “Smoothness 85”. Glyph is decorative (aria-hidden).',
        'Copy never relies on colour; titles are short and complete sentences so they survive truncation at the largest text sizes (the OS truncates the body, never the title).',
        'Held notifications are never announced during a trip.',
      ],
      platformDiffs: {
        ios: 'UNNotificationCategory per type with UNNotificationAction buttons; interruption level: Drive decoded / First Story / Route record = active, Capture problem = timeSensitive (only if the user lets capture problems through quiet hours), the rest passive; threadIdentifier groups DrivePerf notifications; a Notification Content Extension draws the rich preview (glyph + ring, never a map).',
        android: 'One NotificationChannel per type: Drive decoded · Routes and records · Weekly recap and Wrapped · Capture problems · Account and plan (default importance) · Recording (low, ongoing, Live Update). Actions via NotificationCompat.Action; expanded rich preview via a custom BigPicture-style layout; capture problems use CATEGORY_ERROR, recording CATEGORY_PROGRESS.',
      },
      analytics: ['notification_sent {type}', 'notification_held {type, reason: trip_active|quiet_hours}', 'notification_opened {type, action}', 'notification_dismissed {type}'],
      copy: {
        'Drive decoded': 'Drive decoded. · {route label} · Smoothness {n}  (e.g. “Work → Home · Smoothness 85”)',
        'First Story': 'Your first Drive Story is ready. · Two minutes well spent.',
        Coalesced: '2 drives decoded. · Home → Gym · Gym → Home',
        'Route discovered': 'Looks like a regular. · Name the route you’ve now driven 3 times?',
        'Route record': 'New route record on The Commute. · 91, beating 89 from 14 Oct.',
        'Weekly recap': 'Your week, decoded. · 5 drives · Smoothness 87 (+2)',
        'Monthly Wrapped': 'October, wrapped. · You’re The Metronome this month.',
        'Capture problem': 'Recording is paused. · Location access changed to “While Using” (Android: “While using the app”). Two taps to fix it.',
        'Missed trip': 'Did you drive around 5 pm? · We may have missed it. Tell us and we’ll tune detection.',
        'Preview ending': 'Your Pro Preview ends in 2 days. · Your trips stay yours either way.',
        Lapsed: 'Recording is still on. · We’ll tell you when the next drive is decoded.',
        Held: 'Held until you’ve parked',
        Actions: 'Watch Story · See drives · Name it · Not now · See route · Play recap · Open Wrapped · Fix it · Yes, I drove · I was a passenger · See what Pro keeps',
      },
      freePro: 'All notifications are Free. The Preview-ending notice is the only one that mentions the plan, and it never shows a price. No notification links to a paywall except Preview ending → S-SUB-01 (status sheet, not a paywall).',
      data: 'Copy from DP_DATA.notifications (n_first, n_coalesced, n_route, n_record, n_week, n_wrapped, n_capture, n_missed, n_preview, n_lapsed, n_recording). Default Drive decoded is filled from t20261105-1745 with the F-STY-02 template, because t20261105-0742 set a record and its decoded notice is replaced by the record notice (F-NTF-06.2). Scene times are derived from trip end times (5 Nov 18:18 → 6:19; 28 Sep 18:52 → 6:53; 4 Nov 08:03 → 8:04; 5 Nov 08:13 → 8:14), recap deliveredAt (Sun 18:00), Preview day 13 (10 Oct). Monthly Wrapped is shown at 10:00 per F-NTF-04 (fixture wrapped.monthly.deliveredAt says 09:00). The lapsed date (27 Nov) is illustrative: 21 days without opening. Held state uses liveTrip.states.auto (21 min · 17.2 km · GTI).',
      icons: ['pause', 'stop', 'up', 'down'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ==========================================================================================================
     Settings (S-SET-01 … S-SET-09) · push screens under Profile (no tab bar)
     ========================================================================================================== */
  const SET_COMMON_A11Y = 'XL text: rows grow to two or more lines and trailing values move under the title; nothing truncates. Every row is ≥ 52 pt (iOS) / 56 dp (Android).';
  const toggleAct = (ctx, key, fallback) => { const cur = ctx.store[key] ?? fallback; ctx.store[key] = !cur; ctx.haptic(cur ? 'toggleOff' : 'toggleOn'); ctx.update(); return !cur; };

  // ---------- Health Check data ----------
  const HC_TEXT = (ios) => ({
    location: { green: 'Yes', red: ios ? 'While Using only' : 'While using the app only' },
    precise: { green: 'On', red: 'Off' },
    motion: { green: 'Allowed', red: 'Off' },
    notifications: { green: 'Allowed', amber: 'Off', red: 'Off' },
    bgRefresh: { green: 'On', red: 'Off for DrivePerf' },
    lowPower: { green: 'Off', amber: 'On' },
    liveActivities: { green: 'On', amber: 'Off' },
    autoDetect: { green: 'On', amber: 'Paused', red: 'No check-in for 24 h' },
    storage: { green: 'Plenty of space', amber: 'Less than 500 MB free' },
    batteryOpt: { green: 'Yes', amber: 'Optimized', red: 'Restricted' },
    oemSleep: { green: 'Yes', red: 'In Sleeping apps' },
    batterySaver: { green: 'Off', amber: 'On' },
  });
  const HC_DETAIL = {
    notifications: 'Recording works. You won’t hear when a drive is decoded.',
    lowPower: 'Low Power Mode is on. Drives may start a little later.',
    batterySaver: 'Battery Saver is on. Drives may start a little later.',
    autoDetect: 'Paused until 8:00 tomorrow. You can still tap Start.',
  };
  /** Health for the screen: DP_DATA preset (engine helper) + the iOS force-quit case (watchdog copy, F-CAP-14) that has no preset. */
  function hcData(ctx) {
    const preset = ctx.params.health || 'allGood';
    if (preset === 'backgroundKilled') {
      if (ctx.android) return { ...ctx.data.health('android', 'oemKiller'), key: 'oemKiller' };
      const h = ctx.data.health('ios', 'allGood');
      h.summary = D.notifications.find((n) => n.id === 'n_watchdog').title.replace('.', '') + '.';
      h.items = h.items.map((it) => (it.id === 'autoDetect' ? { ...it, status: 'red', detail: 'DrivePerf hasn’t checked in for 24 h, usually after a force-quit. Open it once to switch auto-detect back on.' } : it));
      h.lastCheckIn = '2026-11-05T18:20:00-05:00';
      return { ...h, key: 'backgroundKilled' };
    }
    const h = ctx.data.health(ctx.platform, preset);
    // a preset that targets only the other platform (oemKiller) falls back to all good
    h.items = h.items.map((it) => ({ ...it, detail: it.detail || (it.status !== 'green' ? HC_DETAIL[it.id] || null : null) }));
    return { ...h, key: preset };
  }
  const hcWorst = (h) => (h.items.some((i) => i.status === 'red') ? 'red' : h.items.some((i) => i.status === 'amber') ? 'amber' : 'green');

  /** Where each Fix goes (system Settings deep link mocks, in-app sheets and guides). */
  function hcFix(ctx, it) {
    const ios = ctx.ios;
    const map = {
      location: { text: ios ? 'Open Settings' : 'Open settings', action: 'openSys', arg: 'location' },
      precise: { text: 'Fix', nav: 'S-ONB-11' },
      motion: { text: ios ? 'Open Settings' : 'Open settings', action: 'openSys', arg: 'motion' },
      notifications: { text: ios ? 'Open Settings' : 'Open settings', action: 'openSys', arg: 'notifications' },
      bgRefresh: { text: 'Open Settings', action: 'openSys', arg: 'app' },
      liveActivities: { text: 'Open Settings', action: 'openSys', arg: 'liveActivities' },
      lowPower: { text: 'How it affects recording', nav: 'S-SET-06/iOSVersion' },
      batterySaver: { text: 'How it affects recording', nav: 'S-SET-06' },
      batteryOpt: { text: 'Open battery settings', action: 'openSys', arg: 'battery' },
      oemSleep: { text: 'Show me how', nav: 'S-SET-06' },
      autoDetect: it.status === 'red' ? { text: 'Switch back on', action: 'rearm' } : { text: 'Resume', action: 'resume' },
      storage: { text: 'Manage storage', nav: 'S-SET-07/lowStorage' },
    };
    return map[it.id] || { text: 'Fix', toast: 'Opens Settings' };
  }

  function hcHeader(ctx, h) {
    const { ui, data } = ctx, worst = hcWorst(h), bad = h.items.filter((i) => i.status !== 'green');
    const line = worst === 'green' ? h.promise : (bad.find((i) => i.status === 'red') || bad[0]).detail || h.promise;
    const meta = `<div class="wf-hc-meta"><span>${ui.icon('clock', 15)}Last check-in <b>${ui.esc(data.ago(h.lastCheckIn))}</b></span><span>${ui.icon('drives', 15)}Last trip <b>${ui.esc(data.ago(h.lastTrip))}</b></span></div>`;
    const aria = `${h.summary} ${line} Last check-in ${data.ago(h.lastCheckIn)}. Last trip ${data.ago(h.lastTrip)}.`;
    return `<div class="wf-hc-head is-${worst}" role="group" aria-label="${ui.esc(aria)}"><div class="wf-hc-head-t">${hcGlyph(worst, 44)}<div><b>${ui.esc(h.summary)}</b><p>${ui.esc(line)}</p></div></div>${meta}</div>`;
  }

  function hcRow(ctx, it) {
    const { ui } = ctx, T = HC_TEXT(ctx.ios)[it.id] || {};
    const state = T[it.status] || (it.status === 'green' ? 'OK' : 'Check');
    if (it.status === 'green') return ui.row({ lead: `<span class="wf-hc-ic">${hcGlyph('green', 20)}</span>`, title: it.label, value: ui.esc(state), cls: 'wf-hc-row is-green' });
    const f = hcFix(ctx, it);
    const btn = ui.button(f.text, { kind: it.status === 'red' ? 'filled' : 'tonal', size: 's', nav: f.nav, action: f.action, arg: f.arg, toast: f.toast, params: f.nav === 'S-SET-07/lowStorage' ? null : f.params, label: `${f.text}: ${it.label}` });
    return `<div role="listitem" class="ui-row has-sub wf-hc-row is-${it.status}"><span class="wf-hc-ic">${hcGlyph(it.status, 20)}</span><div class="ui-row-t"><b>${ui.esc(it.label)} <em>· ${ui.esc(state)}</em></b>${it.detail ? `<span>${ui.esc(it.detail)}</span>` : ''}<div class="wf-hc-fix">${btn}</div></div></div>`;
  }

  // ---------- S-SET-01 Settings ----------
  screen('S-SET-01', {
    name: 'Settings',
    wp: 'F', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile',
    states: [{ id: 'default', label: 'Def' }],
    backTo: 'S-ACC-01',
    render(ctx) {
      const { ui, data } = ctx;
      const h = hcData(ctx), worst = hcWorst(h);
      const manual = ctx.capture.mode === 'manual';
      const units = ctx.store.units || (D.user.units.distance === 'km' ? 'Metric' : 'Imperial');
      const ap = { system: 'System', light: 'Light', dark: 'Dark' }[ctx.params.theme || D.user.appearance] || 'System';
      const notifOn = Object.entries(D.user.notifications).filter(([k, v]) => k !== 'quietHours' && v).length;
      const blocks = [
        ui.list([ui.row({ lead: `<span class="wf-hc-ic">${hcGlyph(worst, 24)}</span>`, title: 'Health Check', sub: h.summary, nav: 'S-SET-05', label: `Health Check. ${h.summary}` })]),
        ui.section('Recording', ui.list([
          ui.row({ icon: 'record', title: 'Recording', sub: manual ? 'Manual only · tap Start when you set off' : 'Automatic trips on · default car GTI', nav: 'S-SET-03' }),
          ui.row({ icon: 'battery', title: 'Reliability guide', sub: ctx.ios ? 'Background App Refresh, Low Power Mode, force-quit' : 'Keep Samsung from putting DrivePerf to sleep', nav: 'S-SET-06' }),
        ]), { style: 'caption' }),
        ui.section('Preferences', ui.list([
          ui.row({ icon: 'speed', title: 'Units and display', value: ui.esc(`${units} · ${ap}`), nav: 'S-SET-02' }),
          ui.row({ icon: 'bell', title: 'Notifications', value: ui.esc(`${notifOn} of 6 on`), sub: 'Quiet 10:00 pm – 7:00 am', nav: 'S-SET-04' }),
        ]), { style: 'caption' }),
        ui.section('Your data', ui.list([
          ui.row({ icon: 'shield', title: 'Privacy Centre', sub: 'Export, delete, analytics choices', nav: 'S-PRV-01' }),
          ui.row({ icon: 'layers', title: 'Storage', value: ui.esc(`${D.privacy.dataSize.mb} MB`), nav: 'S-SET-07' }),
        ]), { style: 'caption' }),
        ui.section('Help', ui.list([
          ui.row({ icon: 'edit', title: 'Report a problem', nav: 'S-SET-09' }),
          ui.row({ icon: 'info', title: 'About, legal and support', nav: 'S-SET-08' }),
        ]), { style: 'caption', footer: `DrivePerf ${ui.esc(D.meta.version)} · scoring ${ui.esc(data.trip('t20261105-1745').smoothness.algorithm.replace(/ \(.*\)/, ''))}` }),
      ];
      return ui.page(`<div class="wf-set">${blocks.map((b) => `<div class="wf-set-b" data-rise>${b}</div>`).join('')}</div>`, { nav: { title: 'Settings' } });
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '[data-rise]', { stagger: 30 }); },
    spec: {
      purpose: 'One place for capture, units, notifications, data and help (F-SET-01). Health Check sits on top, so capture problems are never more than one tap away.',
      layout: [
        'Top bar: iOS inline title “Settings” with glass back (to Profile) · Android MediumFlexibleTopAppBar “Settings” with the title block in content.',
        'Health Check row first (its own group): status glyph (shape = state) + “Health Check” + the summary line (“Automatic trips are ready.” / “Auto-detect needs attention.”).',
        'Recording: Recording (auto/manual + default car) → S-SET-03 · Reliability guide → S-SET-06.',
        'Preferences: Units and display (value “Metric · System”) → S-SET-02 · Notifications (value “6 of 6 on”, quiet hours line) → S-SET-04.',
        'Your data: Privacy Centre → S-PRV-01 · Storage (value “186 MB”) → S-SET-07.',
        'Help: Report a problem → S-SET-09 · About, legal and support → S-SET-08. Footer: “DrivePerf 1.0.0 · scoring smooth-v0.3”.',
        'iOS: inset-grouped lists (radius 24 container, 52 pt rows, 16 margins, caption headers). Android: M3E segmented ListItems on surfaceContainer, 56/72 dp rows, 28 dp section gaps.',
      ],
      components: ['navBar', 'list', 'row', 'section'],
      interactions: ['Each row pushes its screen (iOS push slide · Android shared axis X). Back → Profile (S-ACC-01). Health Check row → S-SET-05; it turns into the red octagon + “Auto-detect needs attention.” when any check is red.'],
      motion: ['Groups rise 8 px with a 30 ms stagger (spring.default). RM: no rise.'],
      a11y: ['Health Check row: “Health Check. Automatic trips are ready.” (the glyph has its own label: OK / Worth knowing / Needs attention).', 'Rows with values read “Units and display, Metric · System”.', SET_COMMON_A11Y],
      platformDiffs: { ios: 'NavigationStack inside the Profile sheet; List(.insetGrouped) with SF-style row icons.', android: 'Full-screen destination with MediumFlexibleTopAppBar and predictive back; ListItem groups with 4 dp inner / 28 dp outer corners (M3E segmented lists).' },
      analytics: ['settings_opened {source}', 'setting_section_opened {section}'],
      copy: { Title: 'Settings', 'Health Check sub': '{health summary}', Recording: 'Automatic trips on · default car GTI | Manual only · tap Start when you set off', Footer: 'DrivePerf 1.0.0 · scoring smooth-v0.3' },
      freePro: 'Identical on every plan. No Pro upsell in Settings.',
      data: 'Health summary from DP_DATA.health presets (global health param). Units from user.units, appearance from user.appearance / theme param, notification count from user.notifications, storage from privacy.dataSize.mb, version from meta.version, algorithm from trips[].smoothness.algorithm.',
      icons: ['record', 'battery', 'speed', 'bell', 'shield', 'layers', 'edit', 'info', 'chevronRight'],
    },
  });

  // ---------- S-SET-02 Units and display ----------
  const KM_MI = 0.621371, M_FT = 3.28084;
  screen('S-SET-02', {
    name: 'Units and display',
    wp: 'F', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile',
    states: [{ id: 'default', label: 'Metric' }, { id: 'imperial', label: 'Imperial' }, { id: 'appearance', label: 'Appearance (Dark chosen)' }],
    backTo: 'S-SET-01',
    render(ctx) {
      const { ui, data } = ctx;
      const units = ctx.store.units || (ctx.state === 'imperial' ? 'imperial' : 'metric');
      const imp = units === 'imperial';
      const ap = ctx.store.ap || (ctx.state === 'appearance' ? 'dark' : ctx.params.theme || 'system');
      const t = data.trip('t20261105-0742');
      const dist = imp ? `${ROUND(t.distanceKm * KM_MI)}` : data.km(t.distanceKm), du = imp ? 'mi' : 'km';
      const climb = imp ? Math.round(t.elevGainM * M_FT) : t.elevGainM, cu = imp ? 'ft' : 'm';
      const preview = ui.card(`<div class="wf-u-prev" aria-label="${ui.esc(`Preview: ${t.label}, ${dist} ${imp ? 'miles' : 'kilometres'}, climb ${climb} ${imp ? 'feet' : 'metres'}`)}"><span class="wf-k">Preview</span><b>${ui.esc(t.label)}</b><div class="wf-u-prev-n">${ui.num(dist, du)}<span class="wf-dot">·</span>${ui.num(Math.round(t.durationS / 60), 'min')}<span class="wf-dot">·</span>${ui.num(`↗ ${climb}`, cu)}</div></div>`);
      const rows = [
        ['Distance', imp ? 'Miles (mi)' : 'Kilometres (km)'],
        ['Speed', imp ? 'Miles per hour (mph)' : 'Kilometres per hour (km/h)'],
        ['Elevation', imp ? 'Feet (ft)' : 'Metres (m)'],
        ['G-force', 'g, always'],
      ].map(([k, v]) => ui.row({ title: k, value: ui.esc(v) }));
      const apOpts = [{ id: 'system', label: 'System', sub: 'Follows your phone’s setting' }, { id: 'light', label: 'Light' }, { id: 'dark', label: 'Dark', sub: 'Night Drive. DrivePerf is designed dark-first' }];
      const apList = ctx.ios
        ? ui.list(apOpts.map((o) => ui.row({ title: o.label, sub: o.sub, action: 'appearance', arg: o.id, trailing: o.id === ap ? `<span class="wf-check">${ui.icon('check', 20)}</span>` : '', label: `${o.label}${o.id === ap ? ', selected' : ''}` })))
        : ui.radios(apOpts, ap, { action: 'appearance' });
      const blocks = [
        ui.section('Units', `<div class="ui-pad">${ui.segmented([{ id: 'metric', label: 'Metric' }, { id: 'imperial', label: 'Imperial' }], { selected: units, action: 'units' })}</div><div class="wf-gap"></div>${ui.list(rows)}`, { style: 'caption', footer: 'Default for Canada is metric. Changes apply everywhere at once: screens, share cards and export headers. Exports keep a units row.' }),
        `<div class="ui-pad">${preview}</div>`,
        `<div id="wf-ap" class="wf-anchor"></div>` + ui.section('Appearance', apList, { style: 'caption', footer: 'Share cards always use the night style, whatever you pick here.' }),
        ctx.android ? ui.section('', ui.list([ui.row({ icon: 'sparkle', title: 'Use wallpaper colours', sub: 'Tints DrivePerf with your Material You palette. Glide stays the accent for scores.', trailing: 'toggle', on: !!ctx.store.dyn, action: 'dyn' })]), { style: 'caption' }) : '',
        ui.section('Text size', ui.list([ui.row({ icon: 'info', title: 'Follows your system text size', sub: ctx.ios ? 'Change it in Settings › Display & Brightness › Text Size' : 'Change it in Settings › Display › Font size' })]), { style: 'caption' }),
      ];
      return ui.page(`<div class="wf-set">${blocks.filter(Boolean).map((b) => `<div class="wf-set-b" data-rise>${b}</div>`).join('')}</div>`, { nav: { title: 'Units and display' } });
    },
    initialScroll: (ctx) => (ctx.state === 'appearance' ? (ctx.ios ? 360 : 420) : 0),
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '[data-rise]', { stagger: 30 });
      const n = el.querySelector('.wf-u-prev-n');
      if (n && ctx.store.flash && !still(ctx, el)) M.animate(n, [{ opacity: 0.2, transform: 'translateY(6px)' }, { opacity: 1, transform: 'none' }], { preset: 'default' });
      ctx.store.flash = false;
    },
    actions: {
      units: (ctx, id) => { if ((ctx.store.units || (ctx.state === 'imperial' ? 'imperial' : 'metric')) === id) return false; ctx.store.units = id; ctx.store.flash = true; ctx.update(); ctx.toast(id === 'imperial' ? 'Units: imperial (mi, mph, ft)' : 'Units: metric (km, km/h, m)', { icon: 'check' }); return false; },
      appearance: (ctx, id) => { ctx.store.ap = id; ctx.haptic('selection'); ctx.setGlobal('theme', id === 'system' ? '' : id); return false; },
      dyn: (ctx) => { toggleAct(ctx, 'dyn', false); return false; },
    },
    spec: {
      purpose: 'Units (metric/imperial, G always in g) and appearance (System/Light/Dark), applied immediately everywhere (F-SET-01.1, .2, .4).',
      layout: [
        'Units: segmented control (iOS glass Picker(.segmented) · Android connected ButtonGroup) Metric | Imperial, then a read-only list: Distance · Speed · Elevation · G-force (“g, always”). Footer: regional default + “applies everywhere”.',
        'Preview card: a real drive (Home → Work) in the chosen units: 37.9 km · 30 min · ↗ climb in m, or 23.5 mi · 30 min · ↗ ft.',
        'Appearance: iOS list with a trailing checkmark · Android RadioButton list: System (“Follows your phone’s setting”) · Light · Dark (“Night Drive. DrivePerf is designed dark-first”). Footer: share cards always use the night style.',
        'Android only: “Use wallpaper colours” switch (dynamic colour, brand.md §5.4).',
        'Text size: info row pointing to the system setting (no in-app text-size slider; Dynamic Type / font scale up to 200%).',
      ],
      components: ['navBar', 'segmented', 'list', 'row', 'radios', 'toggle', 'card', 'num', 'section'],
      interactions: ['Metric/Imperial → instant re-render + toast “Units: imperial (mi, mph, ft)”; selection haptic.', 'Appearance option → applies at once (the prototype sets the global theme; System clears it). Selection haptic.', 'Wallpaper colours switch (Android) → toggleOn/toggleOff haptic.', 'Back → S-SET-01.'],
      motion: ['Preview numbers rise 6 px and fade in when units change (spring.default). Appearance change: whole app cross-fades (220 ms, system behaviour). RM: instant.'],
      a11y: ['Segmented control is a tablist: “Metric, tab, 1 of 2, selected”.', 'Preview card is one element: “Preview: Home → Work, 37.9 kilometres, climb 41 metres”.', 'Appearance options announce “Dark, selected”.', SET_COMMON_A11Y],
      platformDiffs: { ios: 'Picker(.segmented) in glass; appearance as a checkmark list; no wallpaper-colour option.', android: 'SingleChoiceSegmentedButtonRow → M3E connected ButtonGroup; RadioButton rows; “Use wallpaper colours” Switch with thumb icon.' },
      analytics: ['setting_changed {key: units|appearance|dynamic_colour}'],
      copy: { Footer: 'Default for Canada is metric. Changes apply everywhere at once: screens, share cards and export headers. Exports keep a units row.', 'G-force': 'g, always', Dark: 'Night Drive. DrivePerf is designed dark-first', 'Appearance footer': 'Share cards always use the night style, whatever you pick here.' },
      freePro: 'Free.',
      data: 'Defaults from user.units (km, km/h, m) and user.appearance (system). Preview uses t20261105-0742 (37.9 km, 30 min, elevGainM); imperial values are converted in the prototype (× 0.621371, × 3.28084).',
      icons: ['check', 'sparkle', 'info'],
    },
  });

  // ---------- S-SET-03 Recording settings ----------
  screen('S-SET-03', {
    name: 'Recording settings',
    wp: 'F', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile',
    states: [{ id: 'default', label: 'Auto on' }, { id: 'autoOff', label: 'Auto off' }, { id: 'batterySaverRecording', label: 'Battery saver recording on' }, { id: 'defaultCar', label: 'Default car picker' }],
    stateParams: { autoOff: { capture: 'manual' } },
    backTo: 'S-SET-01',
    render(ctx) {
      const { ui, data } = ctx;
      const auto = ctx.store.auto ?? ctx.capture.mode !== 'manual';
      const saver = ctx.store.saver ?? (ctx.state === 'batterySaverRecording' || D.user.capture.batterySaverRecording);
      const carOpen = ctx.store.carOpen ?? ctx.state === 'defaultCar';
      const defId = ctx.store.car || D.user.capture.defaultVehicleId;
      const h = hcData(ctx), worst = hcWorst(h);
      const car = data.vehicle(defId);
      const autoRows = [
        ui.row({ icon: 'drives', title: 'Automatic trips', sub: auto ? 'DrivePerf notices when you set off and stops when you park' : 'Off. Drives record only when you tap Start', trailing: 'toggle', on: auto, action: 'auto' }),
        auto ? ui.row({ lead: `<span class="wf-hc-ic">${hcGlyph(worst, 20)}</span>`, title: worst === 'green' ? 'Auto-detect on · ready' : h.summary.replace(/\.$/, ''), sub: `Last check-in ${data.ago(h.lastCheckIn)}`, nav: 'S-SET-05' }) : '',
        auto ? ui.row({ icon: 'pause', title: 'Pause auto-detect', sub: 'For an hour, until tomorrow, or until you turn it back on', nav: 'S-HOME-03' }) : '',
      ];
      const cars = data.cars();
      const carPicker = carOpen ? ui.list(cars.map((v) => {
        const locked = !ctx.isPro && v.id !== D.entitlement.states.free.activeVehicleId;
        return ui.row({ lead: `<span class="ui-radio ${v.id === defId ? 'is-on' : ''}"></span>`, title: `${v.nickname} · ${v.year} ${v.make} ${v.model}`, sub: v.powertrain === 'ev' ? 'Electric' : `Petrol · ${v.transmission}`, trailing: locked ? 'lock' : ui.carDot(v), ...(locked ? { nav: 'S-SUB-03/s2ndCar' } : { action: 'car', arg: v.id }), label: `${v.nickname}${v.id === defId ? ', default' : ''}${locked ? ', Pro' : ''}` });
      }).concat([ui.row({ icon: 'garage', title: 'Manage cars in Garage', nav: 'S-GAR-01' })])) : '';
      const blocks = [
        !auto ? `<div class="ui-pad">${ui.banner({ kind: 'info', icon: 'drives', title: 'Manual mode', text: ctx.ios ? 'Tap Start in the app, Control Center or the widget when you set off.' : 'Tap Start in the app, the Quick Settings tile or the widget when you set off.' })}</div>` : '',
        ui.section('Capture', ui.list(autoRows), { style: 'caption', footer: auto ? 'Auto-detects most drives. The first few hundred metres may be estimated. Force-quitting pauses auto-detect.' : '' }),
        ui.section('Default car', ui.list([ui.row({ lead: `<span class="ui-row-ic is-neutral">${ui.icon('car', 20)}</span>`, title: car.nickname, sub: 'New drives are assigned to this car. Change any drive later.', value: '', trailing: `<span class="ui-row-tr">${ui.carDot(car)}${ui.icon(carOpen ? 'chevronDown' : 'chevronRight', 16)}</span>`, action: 'carOpen', label: `Default car, ${car.nickname}. ${carOpen ? 'Collapse' : 'Change'}` })]) + (carOpen ? `<div class="wf-gap"></div>${carPicker}` : ''), { style: 'caption', footer: !ctx.isPro && carOpen ? 'On Free, one car is active. Your other car’s drives stay, locked, not lost.' : '' }),
        ui.section('Battery', ui.list([
          ui.row({ icon: 'battery', title: 'Battery saver recording', sub: 'Records motion at 25 Hz on every drive. Uses less battery; braking and cornering detail is a little coarser.', trailing: 'toggle', on: saver, action: 'saver' }),
          saver ? ui.row({ lead: `<span class="ui-row-ic is-neutral">${ctx.icons.quality('Usable', { size: 18 })}</span>`, title: 'Drives may show “Usable” data', sub: 'Scores stay fair. The data-quality detail says “25 Hz”.', nav: 'S-DQ-02' }) : '',
        ]), { style: 'caption', footer: 'DrivePerf already drops to 25 Hz on its own when your phone is hot, low on battery or in Low Power Mode.' }),
        ui.section('', ui.list([ui.row({ icon: 'health', title: 'Health Check', nav: 'S-SET-05' }), ui.row({ icon: 'info', title: 'How capture works', nav: 'S-ONB-05' })]), { style: 'caption' }),
      ];
      return ui.page(`<div class="wf-set">${blocks.filter(Boolean).map((b) => `<div class="wf-set-b" data-rise>${b}</div>`).join('')}</div>`, { nav: { title: 'Recording' } });
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '[data-rise]', { stagger: 30 }); },
    actions: {
      auto: (ctx) => { const on = toggleAct(ctx, 'auto', ctx.capture.mode !== 'manual'); ctx.setGlobal('capture', on ? '' : 'manual'); ctx.toast(on ? 'Automatic trips on' : 'Manual mode: tap Start when you set off', { icon: on ? 'check' : 'drives' }); return false; },
      saver: (ctx) => { toggleAct(ctx, 'saver', ctx.state === 'batterySaverRecording'); return false; },
      carOpen: (ctx) => { ctx.store.carOpen = !(ctx.store.carOpen ?? ctx.state === 'defaultCar'); ctx.update(); return false; },
      car: (ctx, id) => { ctx.store.car = id; ctx.haptic('selection'); ctx.update(); ctx.toast(`New drives go to the ${ctx.data.vehicle(id).nickname}`, { icon: 'car' }); return false; },
    },
    spec: {
      purpose: 'Automatic trips on/off, default car, battery saver recording, and links to Health Check and Pause (F-SET-01.3, F-CAP-16).',
      layout: [
        'Manual-mode banner (auto off): “Manual mode. Tap Start in the app, Control Center or the widget when you set off.” (Android: Quick Settings tile).',
        'Capture: “Automatic trips” switch row with a one-line explanation · when on: status row (health glyph + “Auto-detect on · ready” + last check-in) → S-SET-05 · “Pause auto-detect” → S-HOME-03. Footer: the honest capture promise.',
        'Default car: row with the car name, colour dot and chevron; expands into a radio list of cars (GTI, Ioniq) + “Manage cars in Garage” → S-GAR-01. On Free, the second car shows PRO + lock and opens S-SUB-03/s2ndCar.',
        'Battery: “Battery saver recording” switch (IMU 25 Hz always) with its trade-off in plain words; when on, a “Drives may show Usable data” row → S-DQ-02. Footer: automatic 25 Hz back-off (hot, low battery, Low Power Mode).',
        'Links: Health Check → S-SET-05 · How capture works → S-ONB-05.',
      ],
      components: ['navBar', 'banner', 'list', 'row', 'toggle', 'carDot', 'section'],
      interactions: [
        'Automatic trips switch → toggles auto-detect (prototype: sets the global capture=manual so Home and the accessory show Manual mode) + toast + toggle haptic.',
        'Status row → S-SET-05 · Pause → S-HOME-03 sheet · Default car row → expands/collapses the picker; car radio → sets default + toast; locked car → S-SUB-03/s2ndCar; Manage cars → S-GAR-01.',
        'Battery saver switch → on/off; “Usable data” row → S-DQ-02.',
      ],
      motion: ['Groups rise 8 px, 30 ms stagger (spring.default). The car picker expands with spring.default height (native List animation). RM: instant.'],
      a11y: ['Switch rows read “Automatic trips, on, switch”. Status row: “Auto-detect on · ready. Last check-in 18 min ago.”', 'Car rows: “GTI, default” / “Ioniq, Pro” (lock).', SET_COMMON_A11Y],
      platformDiffs: { ios: 'Toggle rows with Glide tint; “Control Center or the widget” wording.', android: 'M3 Switch with thumb icon; “Quick Settings tile or the widget” wording; RadioButton car picker.' },
      analytics: ['setting_changed {key: auto_detect|default_car|battery_saver_recording}', 'auto_paused {source: settings}'],
      copy: { 'Auto on sub': 'DrivePerf notices when you set off and stops when you park', 'Auto off sub': 'Off. Drives record only when you tap Start', 'Battery saver': 'Records motion at 25 Hz on every drive. Uses less battery; braking and cornering detail is a little coarser.', 'Battery footer': 'DrivePerf already drops to 25 Hz on its own when your phone is hot, low on battery or in Low Power Mode.', 'Free car footer': 'On Free, one car is active. Your other car’s drives stay, locked, not lost.' },
      freePro: 'Free: one active car; the second is shown with a PRO lock (S-SUB-03/s2ndCar). Pro/Preview: any car can be the default.',
      data: 'user.capture {autoDetect on, defaultVehicleId gti, batterySaverRecording false}; vehicles[]; health preset for the status row; entitlement.states.free.activeVehicleId = gti.',
      icons: ['drives', 'pause', 'car', 'garage', 'battery', 'health', 'info', 'lock', 'chevronRight', 'chevronDown'],
    },
  });

  // ---------- S-SET-04 Notifications and quiet hours ----------
  const NTF_TYPES = [
    { id: 'driveDecoded', title: 'Drive decoded', sub: 'One per drive, after you park. Drives close together become one.' },
    { id: 'routes', title: 'Routes and records', sub: 'New route records and “Looks like a regular”.' },
    { id: 'weeklyRecap', title: 'Weekly recap', sub: 'Sundays at 6 pm, only in weeks you drove.' },
    { id: 'wrapped', title: 'Monthly Wrapped', sub: 'The 1st of each month.' },
    { id: 'captureProblems', title: 'Capture problems', sub: 'When recording can’t work, with a quick fix. At most once a week per problem.' },
    { id: 'lifecycle', title: 'Account and plan', sub: 'Pro Preview ending, and one reminder if you haven’t opened DrivePerf in 3 weeks.' },
  ];
  screen('S-SET-04', {
    name: 'Notifications and quiet hours',
    wp: 'F', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile',
    states: [{ id: 'default', label: 'Def' }, { id: 'typeOff', label: 'Type off (Monthly Wrapped)' }, { id: 'systemNotificationsDenied', label: 'System notifications denied' }],
    backTo: 'S-SET-01',
    render(ctx) {
      const { ui } = ctx;
      const denied = ctx.state === 'systemNotificationsDenied' && !ctx.store.allowed;
      const on = (id) => ctx.store['n_' + id] ?? (ctx.state === 'typeOff' && id === 'wrapped' ? false : D.user.notifications[id]);
      const brk = ctx.store.brk ?? false;
      const rows = NTF_TYPES.map((t) => ui.row({ title: t.title, sub: on(t.id) ? t.sub : 'Off. You won’t get these. Turn back on any time.', trailing: 'toggle', on: on(t.id) && !denied, action: denied ? 'deniedTap' : 'type', arg: t.id, cls: denied ? 'is-dim' : '' }));
      const blocks = [
        denied ? `<div class="ui-pad">${ui.banner({ kind: 'attention', icon: 'bell', title: 'Notifications are off for DrivePerf', text: 'Recording still works. You won’t hear when a drive is decoded or when something needs fixing.', action: { text: ctx.ios ? 'Open Settings' : 'Open settings', action: 'openSys', arg: 'notifications' } })}</div>` : '',
        ui.section('Send me', ui.list(rows), { style: 'caption', footer: 'At most one notification per drive and one recap a week. No streaks, no “we miss you”.' }),
        ui.section('Quiet hours', ui.list([
          ui.row({ icon: 'clock', title: 'From', value: '10:00 pm', action: 'qh', cls: denied ? 'is-dim' : '' }),
          ui.row({ icon: 'clock', title: 'To', value: '7:00 am', action: 'qh', cls: denied ? 'is-dim' : '' }),
          ui.row({ icon: 'warn', title: 'Let capture problems through', sub: 'If recording stops working at night, tell me right away.', trailing: 'toggle', on: brk, action: denied ? 'deniedTap' : 'brk', cls: denied ? 'is-dim' : '' }),
        ]), { style: 'caption', footer: 'Anything due in quiet hours waits until 7:00 am.' }),
        ui.section('While you drive', ui.list([ui.row({ icon: 'shield', title: 'Held until you’ve parked', sub: 'Nothing but the recording notice appears while a drive is recording. Everything else waits.' })]), { style: 'caption' }),
        ui.section('', ui.list([ui.row({ icon: 'gear', title: ctx.ios ? 'Notification settings in iOS' : 'Notification categories in Android settings', sub: ctx.ios ? 'Banners, sounds, Lock Screen' : 'Each type is its own category', action: 'openSys', arg: 'notifications', trailing: 'chevron' })]), { style: 'caption' }),
      ];
      return ui.page(`<div class="wf-set">${blocks.filter(Boolean).map((b) => `<div class="wf-set-b" data-rise>${b}</div>`).join('')}</div>`, { nav: { title: 'Notifications' }, overlay: ctx.store.sys ? sysPage(ctx, ctx.store.sys) : '' });
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '[data-rise]', { stagger: 30 }); },
    actions: {
      type: (ctx, id) => { const cur = ctx.store['n_' + id] ?? (ctx.state === 'typeOff' && id === 'wrapped' ? false : D.user.notifications[id]); ctx.store['n_' + id] = !cur; ctx.haptic(cur ? 'toggleOff' : 'toggleOn'); ctx.update(); return false; },
      brk: (ctx) => { toggleAct(ctx, 'brk', false); return false; },
      qh: (ctx) => { ctx.toast('Opens a time picker', { icon: 'clock' }); return false; },
      deniedTap: (ctx) => { ctx.toast('Turn on notifications in Settings first', { icon: 'bell' }); return false; },
      openSys: (ctx, k) => { ctx.store.sys = k; ctx.update(); return false; },
      closeSys: (ctx) => { ctx.store.sys = null; if (ctx.state === 'systemNotificationsDenied') ctx.store.allowed = false; ctx.update(); return false; },
    },
    spec: {
      purpose: 'Few, useful notifications that never arrive while driving: per-type switches, quiet hours and the system permission state (F-NTF-01).',
      layout: [
        'Denied banner (system notifications off): attention banner “Notifications are off for DrivePerf” + “Recording still works…” + Open Settings.',
        '“Send me”: six switch rows with a one-line promise each — Drive decoded · Routes and records · Weekly recap · Monthly Wrapped · Capture problems · Account and plan. A switched-off type reads “Off. You won’t get these. Turn back on any time.” Footer: caps + “No streaks, no ‘we miss you’.”',
        'Quiet hours: From 10:00 pm · To 7:00 am (time pickers) · “Let capture problems through” switch. Footer: “Anything due in quiet hours waits until 7:00 am.”',
        '“While you drive”: info row “Held until you’ve parked”.',
        'System link row: iOS “Notification settings in iOS” · Android “Notification categories in Android settings” → OS Settings mock.',
        'Denied: all switches dim and show off; tapping one explains that the system permission comes first.',
      ],
      components: ['navBar', 'banner', 'list', 'row', 'toggle', 'section', 'sysSettings'],
      interactions: [
        'Type switch → on/off (toggle haptics). F-NTF-01.4: a type switched off is never sent again.',
        'From/To → native time picker (prototype: toast). Capture-problems-through switch → on/off.',
        'Open Settings / system row → OS Settings mock (iOS Settings › DrivePerf › Notifications with “Allow Notifications” highlighted · Android App notifications with the main switch highlighted). Tap the mock to return.',
      ],
      motion: ['Groups rise 8 px, 30 ms stagger. RM: none.'],
      a11y: ['Each switch row reads its title, state and promise: “Weekly recap, on. Sundays at 6 pm, only in weeks you drove.”', 'Denied switches are announced “dimmed” and the banner is read first.', SET_COMMON_A11Y],
      platformDiffs: { ios: 'In-app switches map to UNNotificationCategory filtering inside the app (iOS has no per-category system switches); “Notification settings in iOS” opens UIApplication.openNotificationSettingsURLString.', android: 'Each in-app switch mirrors a NotificationChannel (users can also change channels in system settings; the app reads channel importance on resume). The system row opens Settings.ACTION_APP_NOTIFICATION_SETTINGS.' },
      analytics: ['notification_type_disabled {type}', 'notification_type_enabled {type}', 'quiet_hours_changed', 'notification_settings_opened {source}'],
      copy: Object.fromEntries([...NTF_TYPES.map((t) => [t.title, t.sub]), ['Off', 'Off. You won’t get these. Turn back on any time.'], ['Caps footer', 'At most one notification per drive and one recap a week. No streaks, no “we miss you”.'], ['Quiet footer', 'Anything due in quiet hours waits until 7:00 am.'], ['Held', 'Held until you’ve parked. Nothing but the recording notice appears while a drive is recording. Everything else waits.'], ['Denied', 'Notifications are off for DrivePerf. Recording still works. You won’t hear when a drive is decoded or when something needs fixing.']]),
      freePro: 'Free. No plan-dependent types.',
      data: 'user.notifications (all six on) and quietHours 22:00–07:00. typeOff switches Monthly Wrapped off.',
      icons: ['bell', 'clock', 'warn', 'shield', 'gear'],
    },
  });

  // ---------- S-SET-05 Health Check ----------
  screen('S-SET-05', {
    name: 'Health Check',
    wp: 'F', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile',
    states: [
      { id: 'default', label: 'All green' },
      { id: 'amber', label: 'Amber (Low Power Mode / Battery Saver)' },
      { id: 'red', label: 'Red (Always lost)' },
      { id: 'paused', label: 'Paused' },
      { id: 'storageLow', label: 'Storage low' },
      { id: 'motionOff', label: 'Red: motion + Background App Refresh off (extra)' },
      { id: 'notificationsOff', label: 'Amber: notifications off (extra)' },
      { id: 'backgroundKilled', label: 'Red: Android OEM sleep / iOS force-quit (extra)' },
    ],
    stateAliases: { alwaysLost: 'red', lowPower: 'amber', oemKiller: 'backgroundKilled' },
    stateParams: { amber: { health: 'lowPower' }, red: { health: 'alwaysLost' }, paused: { health: 'paused' }, storageLow: { health: 'storageLow' }, motionOff: { health: 'motionOff' }, notificationsOff: { health: 'notificationsOff' }, backgroundKilled: { health: 'backgroundKilled' } },
    backTo: 'S-SET-01',
    render(ctx) {
      const { ui } = ctx;
      let h = hcData(ctx);
      if (ctx.store.fixed) h = { ...h, summary: ctx.data.health(ctx.platform, 'allGood').summary, items: h.items.map((i) => ({ ...i, status: 'green', detail: null })) };
      const red = h.items.filter((i) => i.status === 'red'), amber = h.items.filter((i) => i.status === 'amber'), green = h.items.filter((i) => i.status === 'green');
      const blocks = [
        `<div class="ui-pad">${hcHeader(ctx, h)}</div>`,
        red.length ? ui.section(`Needs attention · ${red.length}`, ui.list(red.map((i) => hcRow(ctx, i)), { cls: 'wf-hc-list' }), { style: 'caption' }) : '',
        amber.length ? ui.section('Worth knowing', ui.list(amber.map((i) => hcRow(ctx, i)), { cls: 'wf-hc-list' }), { style: 'caption' }) : '',
        ui.section(red.length || amber.length ? `Working · ${green.length} of ${h.items.length}` : `All ${green.length} checks pass`, ui.list(green.map((i) => hcRow(ctx, i)), { cls: 'wf-hc-list' }), { style: 'caption', footer: 'Checks run on this phone, each time you open DrivePerf and when it wakes for a drive.' }),
        ui.section('Help', ui.list([
          ui.row({ icon: 'drives', title: 'Report a missed drive', sub: 'Tell us about a drive DrivePerf didn’t record', nav: 'S-NTF-02' }),
          ui.row({ icon: 'battery', title: 'Reliability guide', sub: ctx.ios ? 'Background App Refresh, Low Power Mode, force-quit' : 'Samsung, Xiaomi and other brands', nav: ctx.ios ? 'S-SET-06/iOSVersion' : 'S-SET-06' }),
          ui.row({ icon: 'check', title: 'Test my setup', sub: 'Confirms auto-detect is armed', nav: 'S-SET-06/testMySetupPassFail' }),
        ]), { style: 'caption' }),
      ];
      return ui.page(`<div class="wf-set">${blocks.filter(Boolean).map((b) => `<div class="wf-set-b" data-rise>${b}</div>`).join('')}</div>`, { nav: { title: 'Health Check' }, overlay: ctx.store.sys ? sysPage(ctx, ctx.store.sys) : '' });
    },
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '[data-rise]', { stagger: 30 });
      const g = el.querySelector('.wf-hc-head .wf-hcg');
      if (g && !still(ctx, el)) M.animate(g, [{ opacity: 0, transform: 'scale(.6) rotate(-20deg)' }, { opacity: 1, transform: 'none' }], { preset: 'bouncy', delay: 60 });
      if (el.querySelector('.wf-hc-head.is-red') && !ctx.store.warned) { ctx.store.warned = true; ctx.haptic('warning'); }
    },
    actions: {
      openSys: (ctx, k) => { ctx.store.sys = k; ctx.update(); return false; },
      closeSys: (ctx) => { ctx.store.sys = null; ctx.store.fixed = true; ctx.update(); ctx.toast('Fixed. Automatic trips are ready.', { icon: 'check' }); return false; },
      resume: (ctx) => { ctx.store.fixed = true; ctx.update(); ctx.toast('Auto-detect resumed', { icon: 'check' }); return false; },
      rearm: (ctx) => { ctx.store.fixed = true; ctx.update(); ctx.toast('Auto-detect is back on · checked in just now', { icon: 'check' }); return false; },
    },
    spec: {
      purpose: 'One screen that says whether automatic capture will work and how to fix it, with green/amber/red items and a fix action each (F-SET-02, F-NTF-02, F-CAP-14, F-CAP-16, F-DQ-07).',
      layout: [
        'Summary header card: large status glyph (44, shape = state) + summary (“Automatic trips are ready.” / “Recording works, with one thing to know.” / “Auto-detect needs attention.” / “Auto-detect is paused until 8:00 tomorrow.” / “Storage is getting low.”) + one line (the honest capture promise when green, else the worst item’s detail) + meta row “Last check-in 18 min ago · Last trip 7 h ago”. Tint: critical container (red), warning container (amber), surface (green).',
        '“Needs attention · n” (red) then “Worth knowing” (amber): rows with glyph · label · state (“Location: Always · While Using only”) · detail line · fix button (filled for red, tonal for amber).',
        '“Working · n of 9” / “All 9 checks pass”: green rows with glyph · label · state value. Footer: where checks run.',
        'Help: Report a missed drive → S-NTF-02 · Reliability guide → S-SET-06 · Test my setup → S-SET-06/testMySetupPassFail.',
        'iOS items: Location: Always · Precise Location · Motion & Fitness · Notifications · Background App Refresh · Low Power Mode · Live Activities · Auto-detect · Storage. Android: Location: Allow all the time · Precise location · Physical activity · Notifications · Battery: Unrestricted · Samsung: Never sleeping apps · Battery Saver · Auto-detect · Storage (DP_DATA.health.{ios,android}.items).',
      ],
      components: ['navBar', 'list', 'row', 'button', 'section', 'sysSettings'],
      interactions: [
        'Location / Motion / Notifications / Background App Refresh / Live Activities / Battery → system Settings deep link (prototype: OS Settings mock with the target row highlighted; tap to return, the check re-runs and turns green within 1 s, F-SET-02.4).',
        'Precise location → S-ONB-11 · Low Power Mode / Battery Saver “How it affects recording” → S-SET-06 · OEM sleep “Show me how” → S-SET-06 (Samsung) · Storage → S-SET-07/lowStorage · Auto-detect paused → Resume (toast) · iOS no check-in (force-quit) → “Switch back on” re-arms (toast).',
        'Entry points: S-SET-01 · Home “needs attention” banner/chip · S-HOME-02 · S-LIVE-10 · the capture-problem notification (S-NTF-01). The Settings deep link from a red notification is the only thing allowed after a driver trip ends while still in the car (it opens after the trip, screen-map §2.2).',
      ],
      motion: ['Header glyph pops in (scale 0.6 → 1, −20° → 0°, spring.bouncy); groups rise 8 px, 30 ms stagger. Haptic `warning` once when a red item is found. RM: no pop or rise (haptic kept).'],
      a11y: [
        'Header is one element: “Auto-detect needs attention. Location access changed to While Using. Automatic trips are paused. Two taps to fix it. Last check-in 18 min ago. Last trip 7 h ago.”',
        'Each row: glyph label (“Needs attention” / “Worth knowing” / “OK”) + label + state + detail; the fix button reads “Open Settings: Location: Always”.',
        'State is carried by shape and words (octagon ! / triangle / check disc), never colour alone.',
        SET_COMMON_A11Y,
      ],
      platformDiffs: {
        ios: 'Checks: CLAuthorizationStatus .authorizedAlways, accuracyAuthorization, CMMotionActivityManager authorization, UNAuthorizationStatus, backgroundRefreshStatus, isLowPowerModeEnabled, ActivityAuthorizationInfo().areActivitiesEnabled, watchdog check-in, free disk. Fix = UIApplication.openSettingsURLString.',
        android: 'Checks: ACCESS_BACKGROUND_LOCATION, ACCESS_FINE_LOCATION, ACTIVITY_RECOGNITION, POST_NOTIFICATIONS / channel state, isIgnoringBatteryOptimizations, OEM sleeping-apps heuristic (Samsung), isPowerSaveMode, transition registration, free disk. Fix = ACTION_APPLICATION_DETAILS_SETTINGS / ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.',
      },
      analytics: ['health_check_opened {source, reds, ambers}', 'health_fix_tapped {item}', 'capture_problem_fixed {problem, hours_bucket}'],
      copy: {
        Green: 'Automatic trips are ready. · Auto-detects most drives. The first few hundred metres may be estimated. Force-quitting pauses auto-detect.',
        Amber: 'Recording works, with one thing to know. · Low Power Mode is on. Drives may start a little later.',
        Red: 'Auto-detect needs attention. · Location access changed to “While Using”. Automatic trips are paused. Two taps to fix it.',
        Paused: 'Auto-detect is paused until 8:00 tomorrow. · Paused until 8:00 tomorrow. You can still tap Start. · Resume',
        'Storage low': 'Storage is getting low. · Less than 500 MB free. New drives still record; detailed data may be trimmed. · Manage storage',
        'iOS force-quit': 'Auto-detect may be paused. · DrivePerf hasn’t checked in for 24 h, usually after a force-quit. Open it once to switch auto-detect back on.',
        'Android OEM': 'Samsung put DrivePerf in “Sleeping apps”. Drives won’t start on their own. · Show me how',
        Fixes: 'Open Settings · Fix · How it affects recording · Open battery settings · Show me how · Resume · Switch back on · Manage storage',
      },
      freePro: 'Free. No upsell on this screen.',
      data: 'DP_DATA.health.{ios,android}.items + presets: allGood (default), lowPower (amber), alwaysLost (red), paused, storageLow, motionOff, notificationsOff, oemKiller (Android). iOS “backgroundKilled” has no preset: it is built from allGood + the watchdog copy (n_watchdog, F-CAP-14) with autoDetect red and last check-in 5 Nov 18:20. Detail lines for amber items without fixture detail come from components.md §17 copy. Last check-in / last trip are relative to today 19:30.',
      icons: ['clock', 'drives', 'battery', 'check', 'chevronRight'],
    },
  });

  // ---------- S-SET-06 Reliability guide ----------
  const OEM = {
    samsung: { name: 'Samsung', sub: 'One UI 6 and 7', steps: [
      ['Never sleeping apps', 'Settings › Battery › Background usage limits › Never sleeping apps › add DrivePerf.', 'Open Battery settings'],
      ['Battery: Unrestricted', 'Settings › Apps › DrivePerf › Battery › Unrestricted.', 'Open app battery'],
      ['Don’t put unused apps to sleep', 'Settings › Battery › Background usage limits › turn off “Put unused apps to sleep”.', null],
    ] },
    xiaomi: { name: 'Xiaomi, Redmi, POCO', sub: 'HyperOS and MIUI', steps: [
      ['Autostart on', 'Settings › Apps › Manage apps › DrivePerf › Autostart.', 'Open app settings'],
      ['Battery saver: No restrictions', 'Settings › Apps › Manage apps › DrivePerf › Battery saver › No restrictions.', 'Open battery saver'],
      ['Lock DrivePerf in Recents', 'Open Recents, long-press DrivePerf and tap the lock, so clearing apps doesn’t close it.', null],
    ] },
    other: { name: 'Google Pixel and others', sub: 'OnePlus, Oppo, Realme, Vivo, Honor follow similar steps', steps: [
      ['Battery: Unrestricted', 'Settings › Apps › DrivePerf › App battery usage › Unrestricted.', 'Open app battery'],
      ['Keep permissions if unused', 'Settings › Apps › DrivePerf › turn off “Pause app activity if unused”.', 'Open app settings'],
      ['Don’t force-stop DrivePerf', 'Force stop pauses auto-detect until you open DrivePerf again.', null],
    ] },
  };
  const IOS_GUIDE = [
    ['Background App Refresh on', 'Settings › General › Background App Refresh: on, and on for DrivePerf. Without it, DrivePerf can’t wake for a drive.', 'Open Settings'],
    ['Low Power Mode delays starts', 'With Low Power Mode on, drives still record but may start a little later. The first part is estimated.', null],
    ['Don’t force-quit DrivePerf', 'Swiping DrivePerf away in the app switcher pauses auto-detect until you open it again. Leaving it in the background costs almost no battery.', null],
    ['Live Activities on', 'Settings › DrivePerf › Live Activities, so you can see and stop a recording from the Lock Screen.', 'Open Settings'],
    ['Location: Always, Precise on', 'Settings › DrivePerf › Location › Always, with Precise Location on.', 'Open Settings'],
  ];
  screen('S-SET-06', {
    name: 'Reliability guide',
    wp: 'F', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile',
    states: [
      { id: 'default', label: 'Per OEM: Samsung (Android) · iOS guide (iOS)' },
      { id: 'testMySetupPassFail', label: '“Test my setup” pass' },
      { id: 'iOSVersion', label: 'iOS version (BAR / LPM / force-quit / Live Activities)' },
      { id: 'xiaomi', label: 'Per OEM: Xiaomi (extra, Android)' },
      { id: 'otherOem', label: 'Per OEM: other (extra, Android)' },
      { id: 'testFail', label: '“Test my setup” fail (extra)' },
    ],
    backTo: 'S-SET-05',
    render(ctx) {
      const { ui } = ctx, st = ctx.state;
      const test = ctx.store.test || (st === 'testMySetupPassFail' ? 'pass' : st === 'testFail' ? 'fail' : null);
      const oem = ctx.store.oem || (st === 'xiaomi' ? 'xiaomi' : st === 'otherOem' ? 'other' : 'samsung');
      const iosGuide = ctx.ios;
      const steps = iosGuide ? IOS_GUIDE : OEM[oem].steps;
      const stepHtml = steps.map(([t, d, cta], i) => `<div class="wf-rel-step" role="listitem"><span class="wf-rel-n" aria-hidden="true">${i + 1}</span><div><b>${ui.esc(t)}</b><p>${ui.esc(d)}</p>${cta ? ui.button(cta, { kind: 'tonal', size: 's', icon: 'gear', action: 'openSys', arg: iosGuide ? (i === 0 ? 'app' : i === 3 ? 'liveActivities' : 'location') : 'battery' }) : ''}</div></div>`).join('');
      const intro = iosGuide
        ? `<div class="wf-rel-hero"><span class="wf-rel-hero-ic">${ui.icon('phone', 26)}</span><div><b>Keep auto-detect working on iPhone</b><p>iOS decides when apps can run in the background. These settings let DrivePerf wake when you set off.</p></div></div>`
        : `<div class="wf-rel-hero"><span class="wf-rel-hero-ic">${ui.icon('battery', 26)}</span><div><b>Keep ${ui.esc(OEM[oem].name.split(',')[0])} from putting DrivePerf to sleep</b><p>Some phones stop apps in the background to save battery. Three settings keep auto-detect alive. DrivePerf itself uses almost no battery until you drive.</p></div></div>`;
      const picker = iosGuide ? '' : `<div class="ui-pad">${ui.segmented([{ id: 'samsung', label: 'Samsung' }, { id: 'xiaomi', label: 'Xiaomi' }, { id: 'other', label: 'Other' }], { selected: oem, action: 'oem' })}<p class="wf-rel-det">${oem === 'samsung' ? 'Detected: Samsung Galaxy · One UI' : ui.esc(OEM[oem].sub)}</p></div>`;
      let testCard;
      if (test === 'running') testCard = ui.card(`<div class="wf-rel-test">${ui.loader({ size: 36, label: 'Testing your setup' })}<div><b>Testing your setup…</b><p>Checking triggers, permissions and battery settings.</p></div></div>`);
      else if (test === 'pass') testCard = ui.card(`<div class="wf-rel-test is-pass" role="status">${hcGlyph('green', 36)}<div><b>Auto-detect is armed.</b><p>${ctx.ios ? 'Location, motion and Background App Refresh are set. DrivePerf will wake when you set off.' : 'Activity transitions and geofences are registered, and DrivePerf isn’t restricted. It will wake when you set off.'}</p><span class="wf-rel-when">Checked just now</span></div></div>`, { cls: 'wf-rel-res' });
      else if (test === 'fail') testCard = ui.card(`<div class="wf-rel-test is-fail" role="status">${hcGlyph('red', 36)}<div><b>One thing is still blocking auto-detect.</b><p>${ctx.ios ? 'Background App Refresh is off for DrivePerf. Turn it on in step 1, then test again.' : 'DrivePerf is still in Sleeping apps. Do step 1, then test again.'}</p>${ui.button('Test again', { kind: 'tonal', size: 's', icon: 'refresh', action: 'test' })}</div></div>`, { cls: 'wf-rel-res' });
      else testCard = '';
      const blocks = [
        `<div class="ui-pad">${intro}</div>`,
        picker,
        ui.section(iosGuide ? 'On this iPhone' : OEM[oem].name, `<div class="ui-pad"><div class="ui-card wf-rel-steps" role="list">${stepHtml}</div></div>`, { style: 'caption' }),
        `<div class="ui-pad wf-rel-tb">${testCard}${test === 'running' ? '' : ui.button(test ? 'Test again' : 'Test my setup', { kind: test ? 'tonal' : 'filled', size: 'l', block: true, icon: 'check', action: 'test' })}</div>`,
        ui.section('', ui.list([ui.row({ icon: 'health', title: 'Health Check', nav: 'S-SET-05' })]), { style: 'caption', footer: 'This guide is updated without an app release when phone makers change their settings.' }),
      ];
      return ui.page(`<div class="wf-set">${blocks.filter(Boolean).map((b) => `<div class="wf-set-b" data-rise>${b}</div>`).join('')}</div>`, { nav: { title: 'Reliability guide' }, overlay: ctx.store.sys ? sysPage(ctx, ctx.store.sys) : '' });
    },
    initialScroll: (ctx) => (['testMySetupPassFail', 'testFail'].includes(ctx.state) ? 9999 : 0),
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '[data-rise]', { stagger: 30 });
      const res = el.querySelector('.wf-rel-res');
      if (res && ctx.store.justTested && !still(ctx, el)) { ctx.ui.anim.pop(res); ctx.haptic(res.querySelector('.is-fail') ? 'warning' : 'success'); }
      ctx.store.justTested = false;
      if (ctx.store.test !== 'running') return;
      const tm = setTimeout(() => { ctx.store.test = ctx.state === 'testFail' ? 'fail' : 'pass'; ctx.store.justTested = true; ctx.update(); }, ctx.shot ? 0 : 1600);
      return () => clearTimeout(tm);
    },
    actions: {
      oem: (ctx, id) => { ctx.store.oem = id; ctx.store.test = null; ctx.update(); return false; },
      test: (ctx) => { ctx.store.test = 'running'; ctx.update(); return false; },
      openSys: (ctx, k) => { ctx.store.sys = k; ctx.update(); return false; },
      closeSys: (ctx) => { ctx.store.sys = null; ctx.update(); return false; },
    },
    spec: {
      purpose: 'Brand-specific steps that keep auto-detect alive on phones that kill background apps, plus “Test my setup” to confirm triggers are registered (F-SET-03, F-ONB-07). iOS gets its own page for Background App Refresh, Low Power Mode, force-quit and Live Activities.',
      layout: [
        'Hero: glyph tile (battery / phone) + title (“Keep Samsung from putting DrivePerf to sleep” / “Keep auto-detect working on iPhone”) + one honest paragraph.',
        'Android: brand picker (connected ButtonGroup Samsung | Xiaomi | Other) with “Detected: Samsung Galaxy · One UI”.',
        'Numbered steps card: number disc (Glide container) · step title · settings path with › separators · optional tonal “Open …” deep-link button.',
        '“Test my setup” (filled, large, full width). Result card: running (loader + “Testing your setup…”), pass (check disc + “Auto-detect is armed.” + what was checked + “Checked just now”), fail (octagon + the blocking item + “Test again”).',
        'Health Check link; footer: content updates via Remote Config.',
      ],
      components: ['navBar', 'segmented', 'card', 'button', 'loader', 'list', 'row', 'section', 'sysSettings'],
      interactions: ['Brand picker → swaps steps (selection haptic). Open … buttons → OEM/system settings deep link (prototype: OS Settings mock; tap to return).', 'Test my setup → running 1.6 s → pass or fail card (success / warning haptic). Test again → re-runs.', 'Health Check → S-SET-05.'],
      motion: ['Result card pops in (scale 0.86 → 1, spring.default) with success or warning haptic; loader = iOS Loop / Android M3E shape-morph indicator. RM: result appears in place.'],
      a11y: ['Steps are a list: “Step 1 of 3. Never sleeping apps. Settings, Battery, Background usage limits, Never sleeping apps, add DrivePerf.” (› read as commas).', 'Result card is a status region: “Auto-detect is armed.”', SET_COMMON_A11Y],
      platformDiffs: { ios: 'One page (no brand picker): Background App Refresh, Low Power Mode, force-quit, Live Activities, Location Always + Precise. Deep links: openSettingsURLString.', android: 'Brand pages (Samsung, Xiaomi/Redmi/POCO, Google and others; OnePlus, Oppo/Realme, Vivo, Huawei/Honor bundled too); detection via Build.MANUFACTURER; deep links per OEM intent where available, else ACTION_APPLICATION_DETAILS_SETTINGS. “Test my setup” checks ActivityRecognition transition + geofence registration, isIgnoringBatteryOptimizations and standby bucket.' },
      analytics: ['reliability_guide_viewed {oem}', 'reliability_step_opened {oem, step}', 'reliability_test_run {result: pass|fail}'],
      copy: { 'Samsung steps': OEM.samsung.steps.map((s) => s[0] + ': ' + s[1]).join(' / '), 'Xiaomi steps': OEM.xiaomi.steps.map((s) => s[0] + ': ' + s[1]).join(' / '), 'Other steps': OEM.other.steps.map((s) => s[0] + ': ' + s[1]).join(' / '), 'iOS steps': IOS_GUIDE.map((s) => s[0] + ': ' + s[1]).join(' / '), Pass: 'Auto-detect is armed.', Fail: 'One thing is still blocking auto-detect.' },
      freePro: 'Free.',
      data: 'Brand detection uses the Samsung OEM item (health.android.items oemSleep “Samsung: Never sleeping apps”). Step content is bundled copy (F-SET-03.3); setting paths follow One UI 6/7, HyperOS and Android 16 Pixel.',
      icons: ['battery', 'phone', 'gear', 'check', 'refresh', 'health'],
    },
  });

  // ---------- S-SET-07 Storage ----------
  screen('S-SET-07', {
    name: 'Storage',
    wp: 'F', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile',
    states: [{ id: 'default', label: 'Def' }, { id: 'lowStorage', label: 'Low storage' }, { id: 'removeRawDataConfirm', label: 'Remove raw data confirm' }],
    backTo: 'S-SET-01',
    render(ctx) {
      const { ui } = ctx, ds = D.privacy.dataSize;
      const confirm = ctx.store.confirm ?? ctx.state === 'removeRawDataConfirm';
      const months = ctx.store.months || 6;
      const removed = !!ctx.store.removed;
      const raw = removed ? 0 : ds.rawMb, shapes = 29, sums = ds.mb - ds.rawMb - shapes; // split of the 35 MB non-raw is illustrative (spec.data)
      const total = raw + shapes + sums;
      const seg = [['raw', 'Detailed sensor data', raw, 'Analysis, event traces, Replay detail'], ['shapes', 'Route shapes', shapes, 'Maps and Replay paths'], ['sums', 'Summaries, scores and Stories', sums, 'Kept for every drive, forever']];
      const bar = `<div class="wf-sto-bar" role="img" aria-label="${ui.esc(`DrivePerf uses ${total} MB: ${seg.map((s) => `${s[1]} ${s[2]} MB`).join(', ')}.`)}">${seg.map((s) => `<i class="is-${s[0]}" style="flex:${Math.max(s[2], 0.0001)}"></i>`).join('')}</div>`;
      const legend = seg.map((s) => `<div class="wf-sto-l"><i class="is-${s[0]}"></i><div><b>${ui.esc(s[1])}</b><span>${ui.esc(s[3])}</span></div>${ui.num(s[2], 'MB')}</div>`).join('');
      const blocks = [
        ctx.state === 'lowStorage' ? `<div class="ui-pad">${ui.banner({ kind: 'attention', icon: 'layers', title: `Your ${ctx.ios ? 'iPhone' : 'phone'} is low on space`, text: D.health.presets.storageLow.detail.storage, action: { text: 'Free up space', action: 'ask' } })}</div>` : '',
        `<div class="ui-pad">${ui.card(`<div class="wf-sto-h"><span class="wf-k">DrivePerf on this phone</span><div class="wf-sto-n">${ui.num(total, 'MB')}</div><span class="wf-sto-sub">${ds.trips} drives · Free space on your phone is managed by ${ctx.ios ? 'iOS' : 'Android'}</span></div>${bar}<div class="wf-sto-ls">${legend}</div>`, { hero: true })}</div>`,
        ui.section('Detailed sensor data', `<div class="ui-pad"><p class="wf-note">The raw motion and GPS samples behind Analysis and event traces. Removing them keeps every summary, score, route shape and Story.</p></div><div class="ui-pad">${ui.segmented([6, 12, 24].map((m) => ({ id: String(m), label: `${m} months` })), { selected: String(months), action: 'months' })}</div><div class="ui-pad wf-sto-act">${ui.button(`Remove detail older than ${months} months`, { kind: 'destructive', block: true, icon: 'trash', action: 'ask' })}<p class="wf-note">${removed ? 'Done. Affected drives show “Detailed data removed”.' : 'Your oldest drive is from 28 September, so nothing is that old yet.'}</p></div>`, { style: 'caption' }),
        ui.section('More', ui.list([
          ui.row({ icon: 'trash', title: 'Delete drives or all data', sub: 'Choose what to delete', nav: 'S-PRV-05' }),
          ui.row({ icon: 'cloud', title: 'Cloud backup', value: 'Off', nav: 'S-PRV-01' }),
        ]), { style: 'caption', footer: 'New drives are stored at about 5 MB per hour of driving.' }),
      ];
      const dlg = confirm ? overDialog(ctx, { title: `Remove detailed data older than ${months} months?`, text: 'Those drives keep their summary, score, route shape and Story. Analysis and event traces for them go away. This can’t be undone.', icon: 'trash', actions: [{ text: 'Cancel', kind: 'cancel', action: 'closeDlg' }, { text: 'Remove', kind: 'destructive', action: 'remove' }] }) : '';
      return ui.page(`<div class="wf-set">${blocks.filter(Boolean).map((b) => `<div class="wf-set-b" data-rise>${b}</div>`).join('')}</div>`, { nav: { title: 'Storage' }, overlay: dlg });
    },
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '[data-rise]', { stagger: 30 });
      if (!still(ctx, el)) el.querySelectorAll('.wf-sto-bar i').forEach((b, i) => M.animate(b, [{ transform: 'scaleX(0)' }, { transform: 'scaleX(1)' }], { preset: 'slow', delay: 120 + i * 90 }));
      const d = el.querySelector('.wf-dlg-box'); if (d && !still(ctx, el)) M.animate(d, [{ opacity: 0, transform: 'scale(1.08)' }, { opacity: 1, transform: 'none' }], { preset: 'fast' });
    },
    actions: {
      months: (ctx, m) => { ctx.store.months = +m; ctx.update(); return false; },
      ask: (ctx) => { ctx.store.confirm = true; ctx.haptic('warning'); ctx.update(); return false; },
      closeDlg: (ctx) => { ctx.store.confirm = false; ctx.update(); return false; },
      remove: (ctx) => { ctx.store.confirm = false; ctx.store.removed = true; ctx.update(); ctx.toast('Detailed data removed. Summaries and Stories stay.', { icon: 'check' }); return false; },
    },
    spec: {
      purpose: 'Show and control how much space DrivePerf uses without losing history: summaries, route shapes and raw sensor data, with “remove detail older than 6/12/24 months” (F-SET-04).',
      layout: [
        'Low-storage banner (attention): “Your iPhone is low on space” + “Less than 500 MB free. New drives still record; detailed data may be trimmed.” + Free up space.',
        'Hero card: caption “DrivePerf on this phone”, total in DP Numerals 48 (“186 MB”), “30 drives”, a 12 px stacked bar (raw = Glide, route shapes = data-route 60%, summaries = text-3) and a 3-row legend with MB values.',
        '“Detailed sensor data”: one-paragraph explanation, a 6 | 12 | 24 months segmented control, destructive full-width button “Remove detail older than 6 months”, and an honest note (“Your oldest drive is from 28 September, so nothing is that old yet.”).',
        'More: Delete drives or all data → S-PRV-05 · Cloud backup (Off) → S-PRV-01. Footer: ≈ 5 MB per hour of driving.',
        'Confirm: iOS alert / M3 dialog (with trash icon on Android) “Remove detailed data older than 6 months?” · body · Cancel | Remove (destructive).',
      ],
      components: ['navBar', 'banner', 'card', 'num', 'segmented', 'button', 'dialog', 'list', 'row', 'section'],
      interactions: ['Months segmented → updates the button label (selection haptic).', 'Remove … / Free up space → confirm dialog (warning haptic) · Cancel or scrim → closes · Remove → raw data deleted, bar animates to the new size, toast “Detailed data removed. Summaries and Stories stay.”; affected trips show S-TRP-01/detailedDataRemoved.', 'Delete drives → S-PRV-05 · Backup → S-PRV-01.'],
      motion: ['Bar segments grow from the left (scaleX 0 → 1, spring.slow, 90 ms stagger). Dialog scales 1.08 → 1 (spring.fast). RM: no growth.'],
      a11y: ['Bar is an image with a summary: “DrivePerf uses 186 MB: Detailed sensor data 151 MB, Route shapes 29 MB, Summaries, scores and Stories 6 MB.”', 'The destructive button and dialog use the destructive role; focus moves to Cancel first.', SET_COMMON_A11Y],
      platformDiffs: { ios: 'UIAlertController-style alert (Cancel left, Remove red right). Glass segmented control.', android: 'M3 AlertDialog with icon, TextButtons (Remove in error colour); connected ButtonGroup for months.' },
      analytics: ['storage_viewed {size_bucket}', 'raw_data_removed {months}'],
      copy: { Explanation: 'The raw motion and GPS samples behind Analysis and event traces. Removing them keeps every summary, score, route shape and Story.', 'Nothing old': 'Your oldest drive is from 28 September, so nothing is that old yet.', Confirm: 'Remove detailed data older than {6} months? · Those drives keep their summary, score, route shape and Story. Analysis and event traces for them go away. This can’t be undone.', Toast: 'Detailed data removed. Summaries and Stories stay.' },
      freePro: 'Free. Backup row shows the backup state (Pro feature, opens the Privacy Centre).',
      data: 'privacy.dataSize {trips 30, mb 186, rawMb 151}. The 35 MB that is not raw is split 29 MB route shapes / 6 MB summaries for illustration (no fixture split). Low-storage copy from health.presets.storageLow.detail.storage. Oldest drive 28 Sep (trips[]).',
      icons: ['layers', 'trash', 'cloud'],
    },
  });

  // ---------- S-SET-08 About, legal and support ----------
  screen('S-SET-08', {
    name: 'About, legal and support',
    wp: 'F', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile',
    states: [{ id: 'default', label: 'Def' }],
    backTo: 'S-SET-01',
    render(ctx) {
      const { ui, data } = ctx;
      const algo = data.trip('t20261105-1745').smoothness.algorithm.replace(/ \(.*\)/, '');
      const web = (t, path) => ui.row({ icon: 'export', title: t, sub: `driveperf.com/${path}`, toast: `Opens driveperf.com/${path} in an in-app browser`, trailing: 'chevron' });
      const blocks = [
        `<div class="wf-abt-hero"><span class="wf-abt-logo">${I.loop({ size: 54, color: 'var(--dp-accent)', dot: 'var(--dp-text)' })}</span><b>DrivePerf</b><span>Smooth is a skill.</span></div>`,
        ui.list([ui.row({ title: 'Version', value: `${ui.esc(D.meta.version)} (1)` }), ui.row({ title: 'Scoring algorithm', value: ui.esc(algo) })]),
        ui.section('Legal', ui.list([web('Privacy Policy', 'privacy'), web('Terms of Use', 'terms'), ui.row({ icon: 'info', title: 'Open-source licences', toast: 'Opens the licence list', trailing: 'chevron' })]), { style: 'caption' }),
        ui.section('Support', ui.list([
          web('Help centre', 'help'),
          ui.row({ icon: 'bell', title: 'Contact support', sub: 'support@driveperf.com', toast: 'Opens Mail to support@driveperf.com', trailing: 'chevron' }),
          ui.row({ icon: 'shield', title: 'Privacy officer', sub: 'privacy@driveperf.com', toast: 'Opens Mail to privacy@driveperf.com', trailing: 'chevron' }),
          ui.row({ icon: 'edit', title: 'Report a problem', nav: 'S-SET-09' }),
        ]), { style: 'caption', footer: 'Map data © OpenStreetMap contributors (ODbL). Elevation data: Open-Meteo (CC BY 4.0).' }),
      ];
      return ui.page(`<div class="wf-set">${blocks.map((b) => `<div class="wf-set-b" data-rise>${b}</div>`).join('')}</div>`, { nav: { title: 'About' } });
    },
    mount(el, ctx) { const l = el.querySelector('.wf-abt-logo'); if (l && !still(ctx, el)) M.animate(l, [{ opacity: 0, transform: 'rotate(-90deg) scale(.7)' }, { opacity: 1, transform: 'none' }], { preset: 'slow' }); ctx.ui.anim.rise(el, '[data-rise]', { stagger: 30, delay: 60 }); },
    spec: {
      purpose: 'Version, scoring algorithm version, legal documents, open-source licences, support and the privacy officer contact (F-SET-05).',
      layout: ['Hero: The Loop mark 54 (Glide + text-colour dot), “DrivePerf” (title 2), tagline “Smooth is a skill.”', 'Version 1.0.0 (1) · Scoring algorithm smooth-v0.3 (values trailing).', 'Legal: Privacy Policy · Terms of Use (in-app browser, URL shown as subtitle) · Open-source licences.', 'Support: Help centre (S-WEB-05) · Contact support (email) · Privacy officer (email) · Report a problem → S-SET-09. Footer: OSM + Open-Meteo attribution.'],
      components: ['navBar', 'list', 'row', 'section'],
      interactions: ['Privacy Policy / Terms / Help → SFSafariViewController (iOS) / Custom Tab (Android) at driveperf.com (S-WEB-03/04/05) — prototype toast.', 'Contact support / Privacy officer → mail composer.', 'Open-source licences → licence list (native: iOS acknowledgements page, Android OSS Licenses plugin).', 'Report a problem → S-SET-09.'],
      motion: ['The Loop turns in (−90° → 0°, scale 0.7 → 1, spring.slow) once; groups rise. RM: static.'],
      a11y: ['Logo is decorative; “DrivePerf, Smooth is a skill.” is read as one heading.', 'External links announce “link, opens in browser”.', SET_COMMON_A11Y],
      platformDiffs: { ios: 'SFSafariViewController for web pages; MFMailComposeViewController for email.', android: 'Custom Tabs for web pages; ACTION_SENDTO mailto: for email; OSS licences activity.' },
      analytics: ['about_opened', 'support_contacted {channel: email}', 'legal_viewed {doc}'],
      copy: { Tagline: 'Smooth is a skill.', Footer: 'Map data © OpenStreetMap contributors (ODbL). Elevation data: Open-Meteo (CC BY 4.0).' },
      freePro: 'Free.',
      data: 'meta.version 1.0.0; build (1) is a placeholder; algorithm from trips[].smoothness.algorithm (smooth-v0.3, placeholder A12). Email addresses are placeholders on the driveperf.com domain (confirm with A10).',
      icons: ['export', 'info', 'bell', 'shield', 'edit'],
      assets: ['logo-a-loop.svg'],
    },
  });

  // ---------- S-SET-09 Report a problem ----------
  screen('S-SET-09', {
    name: 'Report a problem',
    wp: 'F', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile',
    states: [{ id: 'default', label: 'Def' }, { id: 'diagnosticsPreview', label: 'Diagnostics preview' }, { id: 'tripAttachWarning', label: 'Trip attach warning' }, { id: 'sent', label: 'Sent' }, { id: 'Off', label: 'Off (offline)' }],
    backTo: 'S-SET-01',
    render(ctx) {
      const { ui, data } = ctx, st = ctx.state;
      if ((ctx.store.sent ?? st === 'sent')) {
        return ui.page(`<div class="wf-rpt-done">${ui.empty({ art: `<div class="wf-rpt-ok">${hcGlyph('green', 64)}</div>`, title: ctx.store.queued ? 'Saved. It sends when you’re back online.' : 'Report sent. Thank you.', text: 'We read every report. If you added an email, we’ll reply there, usually within two working days.', action: { text: 'Done', back: true } })}</div>`, { nav: { title: 'Report a problem' } });
      }
      const preview = ctx.store.preview ?? st === 'diagnosticsPreview';
      const diag = ctx.store.diag ?? (st === 'diagnosticsPreview' || st === 'tripAttachWarning');
      const tripId = ctx.store.trip ?? (st === 'tripAttachWarning' ? 't20261020-0740' : null);
      const offline = st === 'Off';
      const cat = ctx.store.cat || (st === 'default' ? null : st === 'tripAttachWarning' ? 'score' : 'missed');
      const text = st === 'default' && !ctx.store.typed ? '' : st === 'tripAttachWarning' ? 'The hard braking at Spadina & King was a light turning red. I marked it as needed but the score still feels low.' : 'Tuesday’s drive home didn’t record until I was on the highway.';
      const h = data.health(ctx.platform, 'allGood');
      const perm = h.items.filter((i) => !['storage', 'autoDetect'].includes(i.id)).map((i) => `${i.label.replace(/:.*/, '')} ${({ location: ctx.ios ? 'Always' : 'All the time', lowPower: 'off', batterySaver: 'off', batteryOpt: 'Unrestricted', oemSleep: 'not sleeping' })[i.id] || 'on'}`).join(' · ');
      const week = data.trips((t) => new Date(t.start) > new Date(data.today.getTime() - 7 * 864e5)).map((t) => `${data.date(t.end)} ${data.time(t.end)}  trip saved (${t.source})`);
      const diagBox = `<div class="wf-rpt-pre" role="region" aria-label="Diagnostics preview"><div><b>App</b><span>DrivePerf ${ui.esc(D.meta.version)} · ${ui.esc(data.trip('t20261105-1745').smoothness.algorithm.replace(/ \(.*\)/, ''))}</span></div><div><b>Device</b><span>${ctx.ios ? 'iPhone 17 Pro · iOS 26.1' : 'Samsung Galaxy S25 · Android 16 (One UI 8)'}</span></div><div><b>Permissions</b><span>${ui.esc(perm)}</span></div><div><b>Capture checks · 7 days</b><span>${['6 Nov 7:12 pm  check-in ok', ...week.slice(0, 4)].map(ui.esc).join('<br>')}<br>… ${Math.max(0, week.length - 4) + 11} more</span></div><div><b>App logs</b><span>1,284 lines · locations removed</span></div><p>Never included: your routes, places, maps or trip data.</p></div>`;
      const t = tripId ? data.trip(tripId) : null;
      const blocks = [
        offline ? `<div class="ui-pad">${ui.banner({ kind: 'info', icon: 'cloud', title: 'You’re offline', text: 'Write your report now. It’s saved on this phone and sends when you’re back online.' })}</div>` : '',
        `<div class="ui-pad wf-rpt-cats" role="radiogroup" aria-label="What is it about?">${[['missed', 'Missed a drive'], ['score', 'Score seems wrong'], ['crash', 'App crashed'], ['other', 'Something else']].map(([id, l]) => ui.chip(l, { kind: 'filter', selected: cat === id, action: 'cat', arg: id, icon: cat === id ? 'check' : null })).join('')}</div>`,
        `<div class="ui-pad wf-rpt-f">${ui.textField({ label: 'What happened?', value: text, placeholder: 'What did you expect, and what happened instead?', helper: 'Please don’t include addresses or other personal details.' })}${ui.textField({ label: 'Email for a reply (optional)', value: '', placeholder: 'you@example.com' })}</div>`,
        ui.section('Attach', ui.list([
          ui.row({ icon: 'info', title: 'Include diagnostics', sub: 'App logs without locations, device model, OS, permission states and the last 7 days of capture checks.', trailing: 'toggle', on: diag, action: 'diag' }),
          diag ? ui.row({ icon: preview ? 'chevronDown' : 'chevronRight', title: preview ? 'Hide preview' : 'Preview exactly what’s sent', action: 'preview', trailing: '' }) : '',
        ]) + (diag && preview ? `<div class="ui-pad">${diagBox}</div>` : '') + `<div class="wf-gap"></div>` + ui.list([
          t ? ui.row({ lead: `<span class="wf-rpt-glyph">${ui.glyph(t.glyph, 36)}</span>`, title: `${t.label}`, sub: `${t.partOfDay} · ${data.date(t.start)} · ${data.km(t.distanceKm)} km`, trailing: `<span class="ui-press wf-rpt-x" ${ui.act({ action: 'untrip', label: 'Remove attached trip' })}>${ui.icon('close', 16)}</span>` }) : ui.row({ icon: 'plus', title: 'Attach a trip for debugging', sub: 'Optional. You choose the drive.', action: 'pickTrip', trailing: 'chevron' }),
        ]) + (t ? `<div class="ui-pad wf-rpt-warn">${ui.banner({ kind: 'attention', icon: 'pin', title: 'This drive includes precise location', text: 'Its full route, with where it started and ended, goes to DrivePerf support. Only attach it if you’re comfortable sharing that.' })}</div>` : ''), { style: 'caption', footer: 'You choose what’s included, every time. Nothing is sent until you tap Send.' }),
        `<div class="ui-pad wf-rpt-send">${ui.button(offline ? 'Save and send when online' : 'Send report', { kind: 'filled', size: 'l', block: true, icon: offline ? 'cloud' : 'export', action: 'send' })}</div>`,
      ];
      return ui.page(`<div class="wf-set">${blocks.filter(Boolean).map((b) => `<div class="wf-set-b" data-rise>${b}</div>`).join('')}</div>`, { nav: { title: 'Report a problem', trailing: [ui.button(offline ? 'Save' : 'Send', { kind: ctx.ios ? 'glass' : 'text', size: 's', action: 'send', cls: 'wf-rpt-navsend' })] } });
    },
    initialScroll: (ctx) => (ctx.state === 'tripAttachWarning' ? 330 : ctx.state === 'diagnosticsPreview' ? 280 : 0),
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '[data-rise]', { stagger: 30 });
      const ok = el.querySelector('.wf-rpt-ok');
      if (ok && !still(ctx, el)) { M.animate(ok, [{ opacity: 0, transform: 'scale(.5)' }, { opacity: 1, transform: 'none' }], { preset: 'bouncy' }); }
      const input = el.querySelector('.ui-field-in');
      const onIn = () => { ctx.store.typed = true; };
      if (input) input.addEventListener('input', onIn);
      return () => input && input.removeEventListener('input', onIn);
    },
    actions: {
      cat: (ctx, id) => { ctx.store.cat = id; ctx.haptic('selection'); ctx.update(); return false; },
      diag: (ctx) => { toggleAct(ctx, 'diag', ['diagnosticsPreview', 'tripAttachWarning'].includes(ctx.state)); return false; },
      preview: (ctx) => { ctx.store.preview = !(ctx.store.preview ?? ctx.state === 'diagnosticsPreview'); ctx.update(); return false; },
      pickTrip: (ctx) => { ctx.store.trip = 't20261020-0740'; ctx.haptic('selection'); ctx.update(); ctx.toast('Attached: Tuesday morning, Home → Work', { icon: 'check' }); return false; },
      untrip: (ctx) => { ctx.store.trip = null; ctx.update(); return false; },
      send: (ctx) => { ctx.store.sent = true; ctx.store.queued = ctx.state === 'Off'; ctx.haptic('success'); ctx.update(); return false; },
    },
    spec: {
      purpose: 'Report a bug with only the diagnostics the user chooses, previewed exactly, and an explicit choice (with a precise-location warning) to attach a trip (F-SET-06).',
      layout: [
        'Offline banner (Off): “You’re offline. Write your report now. It’s saved on this phone and sends when you’re back online.”',
        'Topic filter chips (single choice): Missed a drive · Score seems wrong · App crashed · Something else.',
        'Text field “What happened?” (iOS rounded field / Android OutlinedTextField, multi-line) with helper “Please don’t include addresses or other personal details.” · optional reply email field.',
        'Attach: “Include diagnostics” switch with exactly what it contains · “Preview exactly what’s sent” disclosure → monospace preview card (App, Device, Permissions, Capture checks · 7 days, App logs — locations removed; “Never included: your routes, places, maps or trip data.”).',
        '“Attach a trip for debugging” row → trip picker; once chosen: trip row (glyph, label, part of day · date · km, remove ×) + attention banner “This drive includes precise location…”.',
        'Footer: per-report consent line. Full-width filled “Send report” (offline: “Save and send when online”); also a Send button in the top bar.',
        'Sent: centred empty state — large check disc · “Report sent. Thank you.” · reply promise · Done.',
      ],
      components: ['navBar', 'banner', 'chip', 'textField', 'list', 'row', 'toggle', 'glyph', 'button', 'empty', 'section'],
      interactions: ['Chip → single select (selection haptic). Diagnostics switch → shows/hides the preview row. Preview → expands the exact payload.', 'Attach a trip → native trip picker (prototype: attaches t20261020-0740 + toast) → warning banner; × removes it.', 'Send / Save → sent state (success haptic); offline queues it. Done → back to S-SET-01 or S-SET-08.'],
      motion: ['Groups rise. Sent: check disc springs in (scale 0.5 → 1, spring.bouncy) + success haptic. RM: static.'],
      a11y: ['Chips are a radio group “What is it about?”. The text field label stays visible above the field.', 'Preview is a labelled region read line by line. The trip warning banner is announced when the trip is attached.', SET_COMMON_A11Y],
      platformDiffs: { ios: 'Top-bar Send as a glass text button; MFMailComposeViewController or the support endpoint (A10).', android: 'Top-app-bar Send TextButton; FilterChips; OutlinedTextField; WorkManager queues offline reports.' },
      analytics: ['problem_reported {has_diagnostics, has_trip, topic, offline}'],
      copy: { Helper: 'Please don’t include addresses or other personal details.', Diagnostics: 'App logs without locations, device model, OS, permission states and the last 7 days of capture checks.', 'Never included': 'Never included: your routes, places, maps or trip data.', 'Trip warning': 'This drive includes precise location. Its full route, with where it started and ended, goes to DrivePerf support. Only attach it if you’re comfortable sharing that.', Consent: 'You choose what’s included, every time. Nothing is sent until you tap Send.', Sent: 'Report sent. Thank you. We read every report. If you added an email, we’ll reply there, usually within two working days.', Offline: 'You’re offline. Write your report now. It’s saved on this phone and sends when you’re back online.' },
      freePro: 'Free.',
      data: 'Permissions from health.{platform}.items (allGood); capture checks derived from trips in the last 7 days (end time + source); device model is a placeholder; attached trip t20261020-0740 (GPS gap + needed brake at Spadina & King).',
      icons: ['info', 'chevronRight', 'chevronDown', 'plus', 'close', 'pin', 'cloud', 'export'],
    },
  });

  /* ==========================================================================================================
     Players: S-RCP-01 weekly recap · S-WRP-01 monthly Wrapped · S-WRP-02 annual Drive Wrapped
     A local story-player primitive (components.md §23, motion.md §3.8): segment progress, close, tap left/right,
     hold to pause, swipe-free (prototype), auto-advance that pauses on focus. Always night (cards never use light).
     ========================================================================================================== */
  const WASH = { glide: 'var(--dp-car-glide)', lucid: 'var(--dp-car-lucid)', violet: 'var(--dp-car-violet)', cobalt: 'var(--dp-car-cobalt)', carmine: 'var(--dp-car-carmine)', glacier: 'var(--dp-car-glacier)', lime: 'var(--dp-car-lime)', graphite: 'var(--dp-car-graphite)', night: 'transparent' };
  const plyIndex = (ctx, P) => {
    const f = ctx.store.f;
    const i = f && f.st === ctx.state && f.key === P.key ? f.i : P.start;
    return Math.max(0, Math.min(P.frames.length - 1, i));
  };

  function playerRender(ctx, P) {
    const { ui } = ctx;
    const i = plyIndex(ctx, P), f = P.frames[i], n = P.frames.length;
    const segs = P.frames.map((_, k) => `<i class="${k < i ? 'is-done' : k === i ? 'is-on' : ''}"><b></b></i>`).join('');
    const aria = `${P.title}. Frame ${i + 1} of ${n}. ${f.aria || [f.kicker, f.head, f.body].filter(Boolean).join(' ')}`;
    const copy = `<div class="wf-ply-copy">${f.kicker ? `<span class="wf-ply-k">${ui.esc(f.kicker)}</span>` : ''}${f.head ? `<h2>${f.headHtml || ui.esc(f.head)}</h2>` : ''}${f.body ? `<p>${f.bodyHtml || ui.esc(f.body)}</p>` : ''}${f.extra || ''}</div>`;
    return `<div class="wf-ply ${ctx.shot ? 'is-still' : ''}" data-theme="dark" data-frame="${ui.esc(f.id)}" style="--wash:${f.wash || WASH.glide};--dur:${P.dur || 5200}ms">
      <div class="wf-ply-bg" aria-hidden="true"></div>
      <div class="wf-ply-top"><div class="wf-ply-prog" aria-hidden="true">${segs}</div>
        <div class="wf-ply-hd"><span class="wf-ply-t">${I.loop({ size: 22, color: '#5BE7C4', dot: '#fff' })}<span><b>${ui.esc(P.title)}</b>${P.sub ? `<small>${ui.esc(P.sub)}</small>` : ''}</span></span>${ui.iconButton('close', { back: true, label: `Close ${P.title}` })}</div></div>
      <div class="wf-ply-stage" role="group" tabindex="0" aria-label="${ui.esc(aria)}" aria-roledescription="frame">
        <div class="wf-ply-art" data-hero="${ui.esc(f.hero || '')}">${f.art || ''}</div>${copy}
      </div>
      <div class="wf-ply-acts">${f.acts || ''}</div>
      <span class="wf-sr ui-press" ${ui.act({ action: 'prev', label: 'Previous frame' })}></span><span class="wf-sr ui-press" ${ui.act({ action: 'next', label: 'Next frame' })}></span>
    </div>`;
  }

  function playerGo(ctx, P, d) {
    const i = plyIndex(ctx, P), j = Math.max(0, Math.min(P.frames.length - 1, i + d));
    if (j === i) return false;
    ctx.store.f = { st: ctx.state, key: P.key, i: j, dir: d };
    ctx.update();
    return true;
  }

  /** Frame hero animations (one per frame type, motion.md §3.8). Final state is the static markup, so RM / shot need nothing. */
  function frameHero(el, ctx) {
    const art = el.querySelector('.wf-ply-art'); if (!art) return;
    const kind = art.dataset.hero, A = (x, k, o) => x && M.animate(x, k, o);
    const q = (s) => art.querySelectorAll(s);
    if (kind === 'strip') q('.wf-ws-d').forEach((d, di) => d.querySelectorAll('.wf-ws-dot').forEach((x, j) => A(x, [{ opacity: 0, transform: 'translateY(-28px) scale(.4)' }, { opacity: 1, transform: 'none' }], { preset: 'bouncy', delay: 240 + di * 110 + j * 60 })));
    if (kind === 'trend') { const p = art.querySelector('.wf-tr-line'); if (p) M.drawOn(p, { duration: 900, delay: 200 }); q('.wf-tr-pt').forEach((x, k, all) => A(x, [{ opacity: 0, transform: k === all.length - 1 ? 'translateY(-36px)' : 'scale(0)' }, { opacity: 1, transform: 'none' }], { preset: k === all.length - 1 ? 'bouncy' : 'default', delay: k === all.length - 1 ? 1000 : 200 + k * 200 })); }
    if (kind === 'flip') { A(art.querySelector('.wf-flip'), [{ opacity: 0.3, transform: 'perspective(900px) rotateY(100deg) scale(.9)' }, { opacity: 1, transform: 'perspective(900px) rotateY(0) scale(1)' }], { preset: 'bouncy', delay: 180 }); ctx.ui.anim.rings(art, { haptics: false }); if (art.querySelector('.wf-arch')) setTimeout(() => ctx.haptic('success'), 700); }
    if (kind === 'meter') q('.wf-mt-bar i').forEach((x, k) => A(x, [{ transform: 'scaleY(0)' }, { transform: 'scaleY(1)' }], { preset: 'slow', delay: 260 + k * 160 }));
    if (kind === 'hbars') q('.wf-gb-bar i').forEach((x, k) => A(x, [{ transform: 'scaleX(0)' }, { transform: 'scaleX(1)' }], { preset: 'slow', delay: 260 + k * 160 }));
    if (kind === 'assemble') q('[data-piece]').forEach((x, k) => A(x, [{ opacity: 0, transform: `translate(${(k % 2 ? 1 : -1) * 40}px, ${60 + k * 20}px) scale(.8)` }, { opacity: 1, transform: 'none' }], { preset: 'slow', delay: 160 + k * 90 }));
    if (kind === 'odo') { const cols = [...q('.wf-odo-s')]; cols.forEach((x, k) => A(x, [{ transform: 'translateY(0)' }, { transform: `translateY(${-x.dataset.d}em)` }], { preset: 'slow', delay: 200 + (cols.length - 1 - k) * 40 })); }
    if (kind === 'clock') { const s = art.querySelector('.wf-clk-sweep'); if (s) A(s, [{ strokeDashoffset: s.dataset.c }, { strokeDashoffset: 0 }], { duration: 1200, easing: 'cubic-bezier(.16,1,.3,1)', delay: 150 }); A(art.querySelector('.wf-clk-hi'), [{ opacity: 0, transform: 'scale(.6)' }, { opacity: 1, transform: 'none' }], { preset: 'fast', delay: 1250 }); }
    if (kind === 'glyph') { art.querySelectorAll('.ui-glyph path').forEach((p) => M.drawOn(p, { duration: 1100, delay: 150 })); ctx.ui.anim.rings(art, { haptics: false }); }
    if (kind === 'rings') ctx.ui.anim.rings(art, { haptics: false });
    if (kind === 'dots') q('.wf-fd i.is-on').forEach((x, k) => A(x, [{ opacity: 0, transform: 'scale(0)' }, { opacity: 1, transform: 'none' }], { preset: 'bouncy', delay: 220 + k * 55 }));
    if (kind === 'fan') q('.wf-fan > *').forEach((x, k) => A(x, [{ opacity: 0, transform: 'translateY(40px) rotate(0deg)' }, { opacity: 1, transform: x.dataset.t }], { preset: 'default', delay: 200 + k * 70 }));
    if (kind === 'road') { const p = art.querySelector('.wf-road path'); if (p) M.drawOn(p, { duration: 1400, delay: 100 }); q('.wf-road-g').forEach((x, k) => A(x, [{ opacity: 0, transform: 'scale(.6)' }, { opacity: 1, transform: 'none' }], { preset: 'default', delay: 900 + k * 120 })); }
    if (kind === 'car') A(art.querySelector('svg'), [{ opacity: 0, transform: 'translateY(16px)' }, { opacity: 1, transform: 'none' }], { preset: 'slow', delay: 150 });
  }

  function playerMount(el, ctx, P) {
    const ply = el.querySelector('.wf-ply'); if (!ply) return;
    const stage = ply.querySelector('.wf-ply-stage');
    const i = plyIndex(ctx, P), last = i === P.frames.length - 1;
    // entrance: wash morph + copy line rise (Wrapped chapters rise 24 px; recap copy 12 px) + the frame's hero
    if (ctx.shot) { /* static */ } else if (ctx.rm || M.reduced(el)) { stage.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 220, fill: 'both' }); }
    else {
      M.animate(ply.querySelector('.wf-ply-bg'), [{ opacity: 0.35 }, { opacity: 1 }], { preset: 'effects' });
      M.animate(ply.querySelector('.wf-ply-art'), [{ opacity: 0, transform: `translateY(${P.rise || 24}px)` }, { opacity: 1, transform: 'none' }], { preset: 'slow' });
      ply.querySelectorAll('.wf-ply-copy > *').forEach((x, k) => M.animate(x, [{ opacity: 0, transform: 'translateY(12px)' }, { opacity: 1, transform: 'none' }], { preset: 'default', delay: 120 + k * 60 }));
      const acts = ply.querySelector('.wf-ply-acts'); if (acts && acts.children.length) M.animate(acts, [{ opacity: 0, transform: 'translateY(12px)' }, { opacity: 1, transform: 'none' }], { preset: 'default', delay: 420 });
      frameHero(el, ctx);
    }
    // tap left third = back, elsewhere = next (controls inside the frame keep their own action)
    const onClick = (e) => {
      if (e.target.closest('[data-nav],[data-action],[data-back],[data-toast],.ui-btn,.ui-iconbtn,.wf-hot')) return;
      if (held) { held = false; return; }
      const r = ply.getBoundingClientRect();
      playerGo(ctx, P, e.clientX - r.left < r.width / 3 ? -1 : 1);
    };
    const onKey = (e) => { if (e.key === 'ArrowRight') { playerGo(ctx, P, 1); e.preventDefault(); } if (e.key === 'ArrowLeft') { playerGo(ctx, P, -1); e.preventDefault(); } };
    // auto-advance (never in shot mode, never past the last frame, paused while held or focused)
    let timer = 0, started = 0, remaining = P.dur || 5200, paused = false, held = false, holdT = 0;
    const run = () => { if (ctx.shot || last || paused) return; started = Date.now(); timer = setTimeout(() => playerGo(ctx, P, 1), remaining); };
    const pause = () => { if (paused) return; paused = true; clearTimeout(timer); remaining -= Date.now() - started; ply.classList.add('is-paused'); };
    const resume = () => { if (!paused) return; paused = false; ply.classList.remove('is-paused'); run(); };
    const down = (e) => { if (e.target.closest('.ui-btn,.ui-iconbtn,.wf-hot')) return; holdT = setTimeout(() => { held = true; pause(); }, 260); };
    const up = () => { clearTimeout(holdT); if (held) resume(); };
    const fin = () => pause(), fout = () => resume();
    ply.addEventListener('click', onClick); ply.addEventListener('keydown', onKey);
    ply.addEventListener('pointerdown', down); ply.addEventListener('pointerup', up); ply.addEventListener('pointercancel', up);
    stage.addEventListener('focusin', fin); stage.addEventListener('focusout', fout);
    if (ctx.shot || last) ply.classList.add('is-ended');
    run();
    return () => { clearTimeout(timer); clearTimeout(holdT); ply.removeEventListener('click', onClick); ply.removeEventListener('keydown', onKey); ply.removeEventListener('pointerdown', down); ply.removeEventListener('pointerup', up); ply.removeEventListener('pointercancel', up); stage.removeEventListener('focusin', fin); stage.removeEventListener('focusout', fout); };
  }

  const PLAYER_ACTIONS = (P) => ({
    next: (ctx) => { playerGo(ctx, P(ctx), 1); return false; },
    prev: (ctx) => { playerGo(ctx, P(ctx), -1); return false; },
  });

  // ---------- art helpers (all share-safe: no maps, no speed, no G, no places on cards) ----------
  function odometer(ctx, value, unit) {
    const chars = String(value).split('');
    const cols = chars.map((c) => (/\d/.test(c) ? `<span class="wf-odo-c"><span class="wf-odo-s" data-d="${c}" style="transform:translateY(${-c}em)">${'0123456789'.split('').map((d) => `<i>${d}</i>`).join('')}</span></span>` : `<span class="wf-odo-p">${ctx.ui.esc(c)}</span>`)).join('');
    return `<div class="wf-odo" role="img" aria-label="${ctx.ui.esc(`${value} ${unit === 'km' ? 'kilometres' : unit}`)}"><span class="wf-odo-n">${cols}</span>${unit ? `<span class="wf-odo-u">${ctx.ui.esc(unit)}</span>` : ''}</div>`;
  }
  function weekStrip(ctx, r, o = {}) {
    const DOW = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    const cols = (r.days || DOW.map((d) => ({ dow: d, drives: [] }))).map((d, k) => {
      const dots = d.drives.map((x) => { const s = Math.round(Math.min(30, 11 + Math.sqrt(x.km) * 2.6) * (o.scale || 1)); return `<span class="wf-ws-dot" style="width:${s}px;height:${s}px" title="${x.km} km"></span>`; }).join('');
      return `<div class="wf-ws-d ${d.drives.length ? '' : 'is-none'}"><div class="wf-ws-col">${dots || '<span class="wf-ws-none"></span>'}</div><b>${ctx.ui.esc((d.dow || DOW[k]).slice(0, o.short ? 1 : 3))}</b>${o.dates && d.date ? `<small>${+d.date.slice(8)}</small>` : ''}</div>`;
    }).join('');
    const sum = (r.days || []).filter((d) => d.drives.length).map((d) => `${d.dow} ${d.drives.length}`).join(', ');
    return `<div class="wf-ws ${o.cls || ''}" role="img" aria-label="${ctx.ui.esc(`Drives by day, dot size by distance: ${sum || 'none'}`)}">${cols}</div>`;
  }
  function trendChart(ctx, pts, o = {}) {
    const W = 320, H = 170, pad = 26, lo = Math.min(...pts.map((p) => p.v)) - 4, hi = Math.max(...pts.map((p) => p.v)) + 3;
    const x = (k) => (pts.length === 1 ? W / 2 : pad + (k * (W - 2 * pad)) / (pts.length - 1)), y = (v) => H - 34 - ((v - lo) / (hi - lo || 1)) * (H - 70);
    const d = pts.map((p, k) => `${k ? 'L' : 'M'}${x(k).toFixed(1)} ${y(p.v).toFixed(1)}`).join('');
    const marks = pts.map((p, k) => { const lastP = k === pts.length - 1; return `<g class="wf-tr-pt"><circle cx="${x(k)}" cy="${y(p.v)}" r="${lastP ? 9 : 5}" fill="${lastP ? '#5BE7C4' : '#0A1420'}" stroke="#5BE7C4" stroke-width="${lastP ? 0 : 2.5}"/><text x="${x(k)}" y="${y(p.v) - (lastP ? 18 : 13)}" text-anchor="middle" class="wf-tr-v ${lastP ? 'is-last' : ''}">${p.v}</text>${o.ends && k && !lastP ? '' : `<text x="${x(k)}" y="${H - 8}" text-anchor="middle" class="wf-tr-x">${ctx.ui.esc(p.l)}</text>`}</g>`; }).join('');
    const calib = o.calibrating ? [1, 2, 3].map((k) => `<circle cx="${W / 2 + k * 46}" cy="${y(pts[0].v)}" r="5" fill="none" stroke="#ffffff40" stroke-width="2" stroke-dasharray="2 3"/>`).join('') + `<path d="M${W / 2 + 12} ${y(pts[0].v)}H${W / 2 + 130}" stroke="#ffffff30" stroke-width="2" stroke-dasharray="2 6"/>` : '';
    return `<svg class="wf-tr" viewBox="0 0 ${W} ${H}" width="100%" role="img" aria-label="${ctx.ui.esc(o.label || `Weekly average Smoothness: ${pts.map((p) => `${p.l} ${p.v}`).join(', ')}`)}"><path d="M${pad} ${H - 34}H${W - pad}" stroke="#ffffff1f" stroke-width="1"/>${pts.length > 1 ? `<path class="wf-tr-line" d="${d}" fill="none" stroke="#5BE7C4" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>` : ''}${calib}${marks}</svg>`;
  }
  /** Drive card for "drive of the week/month/year": share-safe glyph + ring + part of day · car. */
  function driveCard(ctx, t, o = {}) {
    const v = ctx.data.vehicle(t.vehicleId), s = t.smoothness;
    const rec = o.record && s.record && s.record.isNew;
    return `<div class="wf-flip wf-dcard wf-hot ui-press" style="--car:var(--dp-car-${v.colour})" ${ctx.ui.act({ nav: 'S-TRP-01', params: { trip: t.id }, label: `Open this drive: ${t.partOfDay}, Smoothness ${s.score}` })}>${ctx.ui.glyph(t.glyph, 132, { color: '#5BE7C4', width: 4 })}<div class="wf-dcard-r">${ctx.ui.ring(s.score, 104, { base: s.baseline, rec: rec ? s.record.previous.score : null, recNew: rec, stroke: 9, label: false, aria: `Smoothness ${s.score}` })}</div><span class="wf-dcard-k">${ctx.ui.esc(t.partOfDay)} · ${ctx.ui.esc(v.nickname)}</span>${rec ? `<span class="wf-dcard-rec">${I.svg('rosette', { size: 13 })}New route record</span>` : ''}<span class="wf-dcard-a">Open this drive ${ctx.ui.icon('chevronRight', 14)}</span></div>`;
  }
  function archetypeArt(id, s = 150) {
    if (id === 'metronome') { const beats = [0, 1, 2, 3, 4].map((k) => 15 + k * 30); return `<svg width="${s}" height="${s}" viewBox="0 0 150 150" aria-hidden="true"><path d="M0 92 ${beats.map((x) => `Q ${x} 40 ${x + 15} 92`).join(' ')}" fill="none" stroke="#5BE7C4" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>${beats.map((x) => `<circle cx="${x}" cy="66" r="5" fill="#F2F4F7"/>`).join('')}<path d="M8 112h134" stroke="#ffffff40" stroke-width="3" stroke-linecap="round" stroke-dasharray="1 29" stroke-dashoffset="-7"/></svg>`; }
    if (id === 'glider') return `<svg width="${s}" height="${s}" viewBox="0 0 150 150" aria-hidden="true"><path d="M10 104 C 40 104, 48 64, 78 64 S 118 40, 140 36" fill="none" stroke="#5BE7C4" stroke-width="5" stroke-linecap="round"/><path d="M10 118 C 40 118, 52 84, 80 84 S 120 62, 140 58" fill="none" stroke="#5BE7C4" stroke-width="3" stroke-linecap="round" opacity=".35"/><circle cx="140" cy="36" r="8" fill="#F2F4F7"/></svg>`;
    return `<svg width="${s}" height="${s}" viewBox="0 0 150 150" aria-hidden="true">${I.loop({ size: 150, color: '#5BE7C4', dot: '#fff' })}</svg>`;
  }
  function archetypeCard(ctx, a, stats) {
    return `<div class="wf-flip wf-arch" role="img" aria-label="${ctx.ui.esc(`Archetype card: ${a.name}. ${stats.map((s) => `${s[1]} ${s[0]}`).join(', ')}.`)}"><div class="wf-arch-art">${archetypeArt(a.id, 136)}</div><b>${ctx.ui.esc(a.name)}</b><div class="wf-arch-st">${stats.map(([l, v]) => `<span>${ctx.ui.num(v)}<small>${ctx.ui.esc(l)}</small></span>`).join('')}</div><span class="wf-arch-mk">${I.loop({ size: 14, color: '#5BE7C4', dot: '#fff' })}DrivePerf</span></div>`;
  }
  const parkedCar = () => `<svg width="220" height="150" viewBox="0 0 220 150" aria-hidden="true"><rect x="150" y="18" width="40" height="40" rx="9" fill="none" stroke="#5BE7C4" stroke-width="3" opacity=".8"/><text x="170" y="47" text-anchor="middle" fill="#5BE7C4" style="font:700 26px var(--dp-family-display)">P</text><path d="M170 58v24" stroke="#5BE7C4" stroke-width="3" opacity=".6"/><path d="M24 112h172" stroke="#ffffff30" stroke-width="2" stroke-linecap="round"/><path d="M40 104v-16c0-6 4-10 10-11l16-3 14-14c4-4 9-6 15-6h28c7 0 12 3 16 8l11 13 18 3c6 1 10 6 10 12v14c0 3-2 5-5 5H45c-3 0-5-2-5-5z" fill="none" stroke="#F2F4F7" stroke-width="3" stroke-linejoin="round"/><circle cx="72" cy="108" r="11" fill="#0A1420" stroke="#F2F4F7" stroke-width="3"/><circle cx="148" cy="108" r="11" fill="#0A1420" stroke="#F2F4F7" stroke-width="3"/><path d="M84 72h52" stroke="#ffffff40" stroke-width="2"/><text x="54" y="58" fill="#ffffff60" style="font:600 13px var(--dp-family-text)">z</text><text x="64" y="46" fill="#ffffff40" style="font:600 10px var(--dp-family-text)">z</text></svg>`;

  // ======================= S-RCP-01 · Weekly recap player =======================
  function recapFor(ctx) {
    const st = ctx.state;
    if (st === 'emptyWeek') return { empty: true, ...D.recapExamples.emptyWeek };
    const id = ctx.params.recap || (st === 'trendDown' ? 'w2026-11-02' : st === 'calibrating' ? 'w2026-09-28' : 'w2026-10-26');
    return ctx.data.recap(id) || ctx.data.latestCompleteRecap();
  }
  function recapPlayer(ctx) {
    const { ui, data } = ctx, r = recapFor(ctx);
    const range = r.empty ? 'This week' : `${data.date(r.weekStart + 'T12:00:00-05:00')} – ${data.date(r.weekEnd + 'T12:00:00-05:00')}`;
    const done = ui.button('Done', { kind: 'glass', size: 'l', back: true, cls: 'wf-hot' });
    if (r.empty) return { key: 'rcp-empty', title: 'Your week', sub: range, start: 0, frames: [{ id: 'empty', wash: WASH.graphite, hero: 'car', art: `<div class="wf-empty-art">${parkedCar()}</div>`, kicker: 'Your week, decoded.', head: 'No drives this week.', body: 'Your car got a rest.', acts: done, aria: 'No drives this week. Your car got a rest. No recap notification is sent for an empty week.' }] };
    const frames = [];
    frames.push({ id: 'strip', wash: WASH.glide, hero: 'strip', art: weekStrip(ctx, r, { dates: !ctx.xl, short: ctx.xl, scale: 1.4, cls: 'is-big' }), kicker: 'Your week, decoded.', head: `${r.drives} drives · ${data.km(r.km)} km · ${r.durationText}`, body: `Monday ${+r.weekStart.slice(8)} to Sunday ${+r.weekEnd.slice(8)} ${new Date(r.weekEnd + 'T12:00:00-05:00').toLocaleDateString('en-GB', { month: 'long' })}.` });
    // W2 trend
    const pts = r.trend4w.map((w) => ({ l: data.date(w.weekStart + 'T12:00:00-05:00'), v: w.avg }));
    let head, body, bodyHtml, extra = '';
    if (r.calibrating || r.delta == null) { head = `Week average ${r.avgSmoothness}.`; body = 'Your first week, so there’s no trend yet. Next Sunday’s recap compares with this one.'; }
    else if (r.delta > 0) { head = `Week average ${r.avgSmoothness}, up ${r.delta}.`; body = `Last week: ${r.avgSmoothness - r.delta}.`; }
    else if (r.delta === 0) { head = `Week average ${r.avgSmoothness}, level with last week.`; body = 'Steady.'; }
    else {
      head = `Week average ${r.avgSmoothness}.`;
      const ex = (r.downExplanation || '').replace(/\s*Here.s why →\s*$/, '');
      const dayName = (ex.match(/(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)/) || [])[1];
      const day = r.days.find((d) => dayName && dayName.startsWith(d.dow));
      const worst = day && day.drives.slice().sort((a, b) => a.score - b.score)[0];
      body = ex; bodyHtml = ui.esc(ex);
      extra = worst ? `<span class="wf-why wf-hot ui-press" ${ui.act({ nav: 'S-TRP-01', params: { trip: worst.tripId }, label: `Here’s why: open ${dayName}’s drive` })}>Here’s why ${ui.icon('chevronRight', 15)}</span>` : '';
    }
    const dchip = r.delta == null ? `<span class="wf-pchip">${[0, 1, 2].map((k) => `<i class="${k < 1 ? 'on' : ''}"></i>`).join('')}Calibrating</span>` : `<span class="wf-pchip is-${r.delta > 0 ? 'up' : r.delta < 0 ? 'down' : 'same'}"><b aria-hidden="true">${r.delta > 0 ? '▲' : r.delta < 0 ? '▼' : '●'}</b>${r.delta === 0 ? 'Level' : `${data.signed(r.delta)} vs last week`}</span>`;
    frames.push({ id: 'trend', wash: WASH.lucid, hero: 'trend', art: `<div class="wf-trw">${trendChart(ctx, pts, { calibrating: r.delta == null })}${dchip}</div>`, kicker: 'Four-week trend', head, body, bodyHtml, extra });
    // W3 drive of the week
    const dw = r.driveOfWeek, t = data.trip(dw.tripId), v = data.vehicle(t.vehicleId);
    frames.push({ id: 'dow', wash: `var(--dp-car-${v.colour})`, hero: 'flip', art: driveCard(ctx, t), kicker: 'Drive of the week', head: `${dw.partOfDay}, ${dw.routeName || dw.label}, ${dw.score}.`, body: '' });
    // W4 focus (only with ≥ 5 scorable trips, F-SCO-10)
    if (r.focus) {
      const f = r.focus, labels = ['Two weeks ago', 'Last week', 'This week'];
      const meter = `<div class="wf-mt" role="img" aria-label="${ui.esc(`Gentler stops, share of drives: ${f.meter3w.map((x, k) => `${labels[k]} ${Math.round(x * 100)}%`).join(', ')}`)}">${f.meter3w.map((x, k) => `<div class="wf-mt-c ${k === 2 ? 'is-now' : ''}"><div class="wf-mt-bar"><i style="height:${Math.max(4, Math.round(x * 100))}%"></i></div><b>${Math.round(x * 100)}%</b><small>${labels[k]}</small></div>`).join('')}</div>`;
      frames.push({ id: 'focus', wash: WASH.violet, hero: 'meter', art: meter, kicker: 'Your focus', head: `${f.habit.charAt(0).toUpperCase() + f.habit.slice(1)}.`, body: `Gentler in ${f.gentler} of ${f.of} drives this week.`, extra: `<span class="wf-why wf-hot ui-press" ${ui.act({ nav: 'S-SCO-05', label: 'About your focus' })}>About your focus ${ui.icon('chevronRight', 15)}</span>`, aria: `Your focus: ${f.habit}. Gentler in ${f.gentler} of ${f.of} drives this week.` });
    }
    // W5 share
    const card = `<div class="wf-wcard" role="img" aria-label="${ui.esc(`Week card: ${r.drives} drives, average Smoothness ${r.avgSmoothness}${r.delta ? `, ${r.delta > 0 ? 'up' : 'down'} ${Math.abs(r.delta)}` : ''}. No places.`)}"><span class="wf-wcard-k" data-piece>My week</span><div data-piece>${weekStrip(ctx, r, { short: true, scale: 0.72, cls: 'is-mini' })}</div><div class="wf-wcard-n" data-piece>${ui.num(r.avgSmoothness)}${r.delta ? `<em class="is-${r.delta > 0 ? 'up' : 'down'}">${r.delta > 0 ? '▲' : '▼'} ${Math.abs(r.delta)}</em>` : ''}</div><span class="wf-wcard-l" data-piece>Average Smoothness · ${r.drives} drives</span><span class="wf-wcard-mk" data-piece>${I.loop({ size: 14, color: '#5BE7C4', dot: '#fff' })}DrivePerf</span></div>`;
    frames.push({ id: 'share', wash: WASH.glide, hero: 'assemble', art: card, kicker: 'Share your week', head: 'See you next Sunday.', body: '', acts: `${ui.button('Share your week', { kind: 'filled', size: 'l', icon: 'share', nav: 'S-SHR-01/weekStrip', params: { recap: r.id }, cls: 'wf-hot' })}${done}` });
    const start = { trendDown: 1, calibrating: 1, w2: 1, w3: 2, w4: 3, w5: frames.length - 1 }[ctx.state] ?? 0;
    return { key: 'rcp-' + r.id, title: 'Your week', sub: range, start: Math.min(start, frames.length - 1), frames, dur: 4200, rise: 12 };
  }

  screen('S-RCP-01', {
    name: 'Weekly recap player',
    wp: 'F', platforms: ['ios', 'android'], presentation: 'full', tab: 'insights',
    states: [
      { id: 'default', label: 'Def (W1–W5) · W1 week strip' },
      { id: 'trendDown', label: 'Trend down (W2, explain without blame)' },
      { id: 'emptyWeek', label: 'Empty week' },
      { id: 'calibrating', label: 'Calibrating (first week, W2)' },
      { id: 'w2', label: 'W2 trend (extra)' }, { id: 'w3', label: 'W3 drive of the week (extra)' }, { id: 'w4', label: 'W4 focus habit (extra)' }, { id: 'w5', label: 'W5 share (extra)' },
    ],
    stateAliases: { Empty: 'emptyWeek', W1: 'default', W2: 'w2', W3: 'w3', W4: 'w4', W5: 'w5' },
    statusBar: 'light', backTo: 'S-STA-01',
    chrome: { tabBar: false },
    render: (ctx) => playerRender(ctx, recapPlayer(ctx)),
    mount: (el, ctx) => playerMount(el, ctx, recapPlayer(ctx)),
    actions: PLAYER_ACTIONS(recapPlayer),
    spec: {
      purpose: 'A short weekly recap every Sunday at 18:00 (≈ 20 s): week strip, 4-week trend, drive of the week, one focus habit and a share card, generated on the device (F-NTF-03, F-SCO-10, F-SHR-04, A2 §3.3).',
      layout: [
        'Full-screen player, always night (gradient night sky + a 30–40% wash per frame: Glide W1/W5, Lucid W2, the car colour W3, Violet W4). Status bar light.',
        'Top: segment progress (3 px bars, 4 gap, one per frame; done = white 90%, current fills linearly) · header row: Loop + “Your week” + range (“26 Oct – 1 Nov”) · close (glass circle iOS / icon button Android).',
        'Stage: hero art centred in the upper 55%, copy block bottom-left (kicker caption caps · storyHeadline 28/34 · storyBody 20/25, max 3 + 2 lines; XL: copy grows upward, art shrinks to 40%).',
        'W1: 7 day columns (Mon–Sun + date), one Glide dot per drive sized by distance; empty days show a dashed stub. Copy: “5 drives · 171.2 km · 2 h 43 min”.',
        'W2: 4-week line with values, this week’s point larger and Glide; delta capsule (▲ +2 vs last week · ▼ −1 neutral · ● Level · calibrating dots). Down: “Week average 86.” + “Down 1. Wednesday’s drive pulled it down.” + “Here’s why ›” → that drive.',
        'W3: drive card (share-safe glyph, ring, part of day · car, “Open this drive ›”) that flips in. Copy: “Sunday morning, Home → Port Credit, 90.”',
        'W4 (≥ 5 scorable drives only): 3-week meter (bars + %), “Your focus: braking into stops.” + “Gentler in 1 of 5 drives this week.” + “About your focus ›” → S-SCO-05. No streak language.',
        'W5: week share card (4:5, night): “My week”, mini strip, average + ▲/▼ delta, “Average Smoothness · 5 drives”, Loop mark. Buttons: Share your week (filled) → S-SHR-01/weekStrip · Done (glass).',
        'Empty week: parked-car line art, “No drives this week. Your car got a rest.”, Done. No notification is sent for an empty week.',
      ],
      components: ['iconButton', 'button', 'ring', 'glyph', 'num'],
      interactions: [
        'Tap right two-thirds = next, left third = previous, press and hold = pause, arrow keys on hardware keyboards; close / Done = dismiss (back to S-STA-01 or the notification origin). Swipe down to exit natively (interactive scale 0.9 + corner radius; gestureEnd haptic).',
        'Auto-advance every 4.2 s; stops on the last frame; pauses on hold and whenever VoiceOver/TalkBack focuses the frame (F-STY-06.1).',
        '“Here’s why ›” → S-TRP-01 (the lowest-scoring drive on the named day). Drive card → S-TRP-01. “About your focus ›” → S-SCO-05. Share → S-SHR-01/weekStrip (recap param).',
        'D1 gate: opened from the notification while moving → S-DRV-05, which opens the recap once parked.',
      ],
      motion: [
        'Frame change (motion.md §3.8): wash morphs (spring.effects), art rises 12 px (spring.slow), copy lines rise 12 px with a 60 ms stagger (spring.default).',
        'W1 dots land day by day (drop 28 px + scale, spring.bouncy, 110 ms per day). W2 line redraws (900 ms ease.drawOn) and this week’s point drops in last (spring.bouncy). W3 card flips in (rotateY 100° → 0, spring.bouncy) with the ring reveal. W4 bars fill upward (spring.slow, 160 ms stagger). W5 card pieces fly into their slots (spring.slow, 90 ms stagger; motion.md §3.7), success haptic when ready.',
        'RM: 220 ms cross-fades, final values, no flips. Haptics kept (no ring ticks).',
      ],
      a11y: [
        'Each frame is one focusable element whose label is the full copy, e.g. “Your week. Frame 2 of 5. Four-week trend. Week average 87, up 2. Last week: 85.”; focus pauses auto-advance.',
        'Hidden “Previous frame” / “Next frame” buttons for switch control and screen readers; charts have text summaries (“Drives by day, dot size by distance: Tue 2, Thu 2, Sun 1”).',
        'Delta uses ▲/▼/● glyph + words; down is neutral grey, never red.',
        'XL: copy reflows (no truncation), art scales down; the share buttons stack.',
      ],
      platformDiffs: { ios: 'fullScreenCover with a TabView(.page) or custom pager; glass close button; Share uses the system share sheet via S-SHR-01.', android: 'Full-screen destination (edge-to-edge, light status icons); HorizontalPager; M3 icon button close; predictive back = dismiss.' },
      analytics: ['recap_opened {source: notification|insights}', 'recap_frame_viewed {frame}', 'recap_completed', 'recap_shared'],
      copy: {
        W1: '{n} drives · {km} km · {duration}  (5 drives · 171.2 km · 2 h 43 min)',
        'W2 up': 'Week average 87, up 2. · Last week: 85.',
        'W2 down': 'Week average 86. · Down 1. Wednesday’s drive pulled it down. · Here’s why',
        'W2 level': 'Week average 85, level with last week. · Steady.',
        'W2 calibrating': 'Week average 78. · Your first week, so there’s no trend yet. Next Sunday’s recap compares with this one.',
        W3: 'Drive of the week · {part of day}, {route name or label}, {score}.',
        W4: 'Your focus · Braking into stops. · Gentler in {x} of {y} drives this week.',
        W5: 'Share your week · See you next Sunday. · Share your week · Done',
        Empty: 'No drives this week. · Your car got a rest.',
      },
      freePro: 'Free (the weekly recap and its share card are free forever). Pro adds card styles in S-SHR-01 only. No paywall in the recap.',
      data: 'Default + W2–W5: recaps w2026-10-26 (latest complete, matches the n_week notification). trendDown: w2026-11-02 (the current week as it will be delivered on Sun 8 Nov; down 1, “Wednesday’s drive”; Here’s why → t20261104-0650, the lower of Wednesday’s two drives). calibrating: w2026-09-28 (no delta, no focus, so 4 frames). Empty: recapExamples.emptyWeek. Dot size = 11 + 2.6·√km px (cap 30).',
      icons: ['close', 'share', 'chevronRight', 'rosette'],
      assets: ['logo-a-loop.svg'],
    },
  });

  // ======================= S-WRP-01 · Monthly Wrapped player =======================
  function monthlyPlayer(ctx) {
    const { ui, data } = ctx, m = D.wrapped.monthly, C = Object.fromEntries(m.chapters.map((c) => [c.id, c]));
    const done = ui.button('Done', { kind: 'glass', size: 'l', back: true, cls: 'wf-hot' });
    const arch = D.wrapped.archetypes.find((a) => a.id === C.archetype.archetype);
    if (ctx.state === 'seasonOpenerVariant') {
      const pr = D.previewRecap, sm = data.trip(pr.smoothest.tripId), named = D.routes.filter((r) => r.named && r.active && !r.freeLocked).slice(0, 2);
      const frames = [
        { id: 'l1', wash: WASH.glide, hero: 'road', art: `<div class="wf-road">${I.loop({ size: 120, color: '#5BE7C4', dot: '#fff' })}</div>`, kicker: 'Launch edition', head: 'Your first two weeks, wrapped.', body: `${data.date(pr.period.from + 'T12:00:00-04:00')} – ${data.date(pr.period.to + 'T12:00:00-04:00')}` },
        { id: 'l2', wash: WASH.lucid, hero: 'odo', art: odometer(ctx, data.km(pr.km), 'km'), kicker: 'Two weeks', head: `${pr.drives} drives · ${data.km(pr.km)} km`, body: 'Enough for DrivePerf to learn your usual.' },
        { id: 'l3', wash: `var(--dp-car-${data.vehicle(sm.vehicleId).colour})`, hero: 'flip', art: driveCard(ctx, sm), kicker: 'Smoothest drive', head: `${sm.partOfDay}, ${sm.smoothness.score}.`, body: `${data.km(sm.distanceKm)} km in the ${data.vehicle(sm.vehicleId).nickname}.` },
        { id: 'l4', wash: WASH.cobalt, hero: 'road', art: `<div class="wf-road wf-routes">${named.map((r) => `<span class="wf-road-g">${ui.glyph(r.glyph, 96, { color: '#5BE7C4', width: 4 })}<small>${ui.esc(r.name)}</small></span>`).join('')}</div>`, kicker: 'Routes found', head: `${pr.routesFound} routes found.`, body: named.map((r) => r.name).join(' and ') + '.' },
        { id: 'l5', wash: WASH.glide, hero: 'flip', art: archetypeCard(ctx, { id: 'loop', name: 'Archetype soon' }, [['drives', pr.drives], ['routes', pr.routesFound]]), kicker: 'Next', head: 'See you on the 1st.', body: 'Your first full month comes with an archetype.', acts: `${ui.button('Share', { kind: 'filled', size: 'l', icon: 'share', nav: 'S-SHR-01', params: { trip: sm.id }, cls: 'wf-hot' })}${done}` },
      ];
      return { key: 'wrp-launch', title: 'Launch, wrapped', sub: 'Your first two weeks', start: 0, frames, dur: 5200 };
    }
    const tDom = data.trip(C.driveOfMonth.tripId), vDom = data.vehicle(tDom.vehicleId);
    const weeks = C.trend.weeks, wkLabels = D.recaps.filter((r) => r.weekStart.startsWith('2026-10')).map((r) => data.date(r.weekStart + 'T12:00:00-04:00')).reverse();
    const gentle = +C.focus.sub.match(/(\d+) of/)[1], total = +C.focus.sub.match(/of (\d+)/)[1];
    const route = D.routes.find((r) => r.id === 'r_commute'), oct = route.trend.filter((p) => p.date.startsWith('2026-10'));
    const proInner = `<div class="wf-pro"><div class="wf-pro-h">${ui.glyph(route.glyph, 64, { color: '#5BE7C4', width: 3.4 })}<div><b>${ui.esc(route.name)}</b><small>${oct.length} runs in October</small></div></div>${trendChart(ctx, oct.map((p) => ({ l: data.date(p.date + 'T12:00:00-04:00'), v: p.score })), { ends: true, label: `The Commute, October runs: ${oct.map((p) => p.score).join(', ')}` })}</div>`;
    const proArt = ctx.isPro ? proInner : ui.lockPanel(proInner, { text: 'Per-route chapters come with Pro. Your Wrapped stays free.', nav: 'S-SUB-03/trends', cls: 'wf-hot wf-pro-lock' });
    const shareActs = `${ui.button('Share', { kind: 'filled', size: 'l', icon: 'share', nav: 'S-SHR-01/archetype', params: { wrapped: m.id }, cls: 'wf-hot' })}${done}`;
    const frames = [
      { id: 'totals', wash: WASH.lucid, hero: 'odo', art: `${odometer(ctx, C.totals.line.match(/([\d,.]+) km/)[1], 'km')}<span class="wf-odo-sub">${ui.esc(C.totals.line.split(' · ')[0])}</span>`, kicker: m.notification.title, head: C.totals.line, body: C.totals.sub + '.' },
      { id: 'trend', wash: WASH.glide, hero: 'trend', art: `<div class="wf-trw">${trendChart(ctx, weeks.map((v, k) => ({ l: wkLabels[k] || `Week ${k + 1}`, v })))}</div>`, kicker: 'Week by week', head: `${C.trend.line}.`, body: `From ${weeks[0]} in the first week to ${weeks[weeks.length - 1]} in the last.` },
      { id: 'driveOfMonth', wash: `var(--dp-car-${vDom.colour})`, hero: 'flip', art: driveCard(ctx, tDom), kicker: 'Smoothest drive', head: C.driveOfMonth.line.replace(/^Smoothest drive: /, '') + '.', body: '' },
      { id: 'focus', wash: WASH.violet, hero: 'dots', art: `<div class="wf-fd" role="img" aria-label="${ui.esc(`${gentle} of ${total} drives had gentler stops`)}">${Array.from({ length: total }, (_, k) => `<i class="${k < gentle ? 'is-on' : ''}"></i>`).join('')}</div>`, kicker: 'Your focus', head: C.focus.line, body: C.focus.sub },
      { id: 'archetype', wash: WASH.cobalt, hero: 'flip', art: archetypeCard(ctx, { id: arch.id, name: arch.name }, [['drives', +C.totals.line.match(/^(\d+)/)[1]], ['avg Smoothness', +C.trend.line.match(/(\d+)$/)[1]]]), kicker: 'Your October archetype', head: C.archetype.line, body: C.archetype.sub, acts: shareActs },
      { id: 'proRoutes', wash: WASH.cobalt, hero: ctx.isPro ? 'trend' : '', art: proArt, kicker: 'Pro chapter · route deep dive', head: C.proRoutes.line + '.', body: ctx.isPro ? 'Your October runs of The Commute, oldest to newest.' : 'The summary is yours either way.', acts: done },
    ];
    const start = { trend: 1, driveOfMonth: 2, focus: 3, archetype: 4, proExtraChapter: 5, proChapterLocked: 5 }[ctx.state] ?? 0;
    return { key: 'wrp-m-' + ctx.isPro, title: m.notification.title.replace(/\.$/, ''), sub: m.period, start, frames, dur: 5200 };
  }

  screen('S-WRP-01', {
    name: 'Monthly Wrapped player',
    wp: 'F', platforms: ['ios', 'android'], presentation: 'full', tab: 'insights',
    states: [
      { id: 'default', label: 'Def · totals' },
      { id: 'seasonOpenerVariant', label: 'Launch variant (Gate 2 renamed “Season Opener” → “Launch”)' },
      { id: 'proExtraChapter', label: 'Pro extra chapter (Pro)' },
      { id: 'proChapterLocked', label: 'Pro chapter on Free (extra)' },
      { id: 'trend', label: 'Trend (extra)' }, { id: 'driveOfMonth', label: 'Smoothest drive (extra)' }, { id: 'focus', label: 'Focus (extra)' }, { id: 'archetype', label: 'Archetype flip (extra)' },
    ],
    stateAliases: { launchVariant: 'seasonOpenerVariant' },
    stateParams: { proChapterLocked: { ent: 'free' } },
    statusBar: 'light', backTo: 'S-STA-01',
    chrome: { tabBar: false },
    render: (ctx) => playerRender(ctx, monthlyPlayer(ctx)),
    mount: (el, ctx) => playerMount(el, ctx, monthlyPlayer(ctx)),
    actions: PLAYER_ACTIONS(monthlyPlayer),
    spec: {
      purpose: 'A monthly mini-Wrapped on the 1st (10:00 local, ≥ 4 driver trips): totals, trend, best drive, focus and an archetype, a share moment before December (F-NTF-04, F-SHR-04). Core is Free; Pro adds a per-route chapter.',
      layout: [
        'Same player frame as the recap (components.md §23): segments, Loop + “October, wrapped” + “October 2026”, close; night stage with a per-chapter wash (Lucid · Glide · car colour · Violet · Cobalt).',
        'Totals: odometer “760 km” (DP Numerals 72, tabular columns) + “19 drives” · copy “19 drives · 760 km” / “11.3 hours behind the wheel.” (neutral about volume).',
        'Trend: 4 weekly points (81, 85, 85, 87) with week labels · “Average Smoothness 84.” / “From 81 in the first week to 87 in the last.”',
        'Smoothest drive: flip card (glyph, ring 89, part of day · car) · “14 Oct, The Commute, 89.” (user-named route only).',
        'Focus: 19 dots, 9 filled · “Your focus: braking into stops.” / “Gentler in 9 of 19 drives.”',
        'Archetype (share hero): card that flips in — archetype line art, “The Metronome”, two stats (19 drives · 84 avg Smoothness), Loop mark · copy “You’re The Metronome.” / “The same calm rhythm, drive after drive.” · Share (filled) → S-SHR-01/archetype · Done.',
        'Pro chapter: route deep dive (The Commute glyph + October runs line) · “The Commute: 79 → 87.” On Free: the same chapter blurred 14 px with lock + “Per-route chapters come with Pro. Your Wrapped stays free.” + See plans → S-SUB-03.',
        'Launch variant (once, 14 days after install in the launch month): “Your first two weeks, wrapped.” · 8 drives · 316.2 km · smoothest drive (Saturday midday, 84) · 2 routes found (The Commute and The Commute home) · “See you on the 1st.”',
      ],
      components: ['iconButton', 'button', 'ring', 'glyph', 'num', 'lockPanel'],
      interactions: ['Tap right/left, hold to pause, auto-advance 5.2 s (pauses on screen-reader focus), close/Done → back. Drive card → S-TRP-01. Share → S-SHR-01/archetype (Archetype card, no places). Pro chapter lock → S-SUB-03/trends (never during a trip: D1 gate).'],
      motion: [
        'Chapter change (650 ms, motion.md §3.8): incoming chapter rises 24 px → 0 (spring.slow) while the wash morphs (spring.effects); copy lines rise 12 px, 60 ms stagger.',
        'Odometer roll: each digit column rolls 0 → value, right to left, 40 ms stagger (spring.slow). Trend line redraws (900 ms). Smoothest drive: card flip + ring reveal. Focus dots pop in order (spring.bouncy, 55 ms). Archetype card flip (rotateY 100° → 0, spring.bouncy) + success haptic.',
        'RM: 220 ms cross-fades, final numbers, no flips or rolls.',
      ],
      a11y: ['Each chapter is one element: “October, wrapped. Frame 5 of 6. Your October archetype. You’re The Metronome. The same calm rhythm, drive after drive.”', 'Odometer reads “760 kilometres”; focus dots read “9 of 19 drives had gentler stops”.', 'XL: copy grows upward and art shrinks; nothing truncates.'],
      platformDiffs: { ios: 'fullScreenCover pager; glass close and Done; numerals SF Pro Expanded.', android: 'Full-screen destination; HorizontalPager; Google Sans Flex wdth 120 numerals; tonal Done.' },
      analytics: ['wrapped_opened {type: monthly, variant: default|launch}', 'wrapped_frame_viewed {frame}', 'wrapped_completed', 'wrapped_shared {card: archetype}', 'locked_content_viewed {type: wrapped_pro_chapter}'],
      copy: Object.fromEntries([['Notification', 'October, wrapped. · You’re The Metronome this month.'], ...D.wrapped.monthly.chapters.map((c) => [c.id, `${c.line}${c.sub ? ' · ' + c.sub : ''}`]), ['Free lock', 'Per-route chapters come with Pro. Your Wrapped stays free.'], ['Launch', 'Your first two weeks, wrapped. · 8 drives · 316.2 km · 2 routes found. · See you on the 1st. Your first full month comes with an archetype.']]),
      freePro: 'Free: chapters 1–5 and the Archetype share card. Pro/Preview: + the per-route chapter (and Pro card styles in S-SHR-01). Free sees the Pro chapter locked with its own data blurred (proChapterLocked).',
      data: 'wrapped.monthly (October 2026: chapters totals, trend, driveOfMonth t20261014-0744, focus, archetype metronome, proRoutes). Week labels from recaps weekStart in October. Pro chapter line from routes r_commute.trend (October runs). Launch variant from previewRecap (8 drives, 316.2 km, smoothest t20261010-1340, routesFound 2) — Gate 2 renamed the “Season Opener” to “Launch”.',
      icons: ['close', 'share', 'lock', 'chevronRight'],
      assets: ['logo-a-loop.svg'],
    },
  });

  // ======================= S-WRP-02 · Annual Drive Wrapped player =======================
  function annualPlayer(ctx) {
    const { ui, data } = ctx, A = D.wrapped.annual, F = Object.fromEntries(A.frames.map((f) => [f.n, f]));
    const st = ctx.state, single = st === 'singleCar', skipHabit = st === 'habitFrameSkipped';
    const done = ui.button('Done', { kind: 'glass', size: 'l', back: true, cls: 'wf-hot' });
    const hero = data.trip(F[5].tripId), glider = D.wrapped.archetypes.find((a) => a.id === F[4].archetype);
    const route = D.routes.find((r) => r.id === 'r_commute');
    const first = route.trend[0], best = route.trend.reduce((a, b) => (b.score > a.score ? b : a));
    const gti = data.vehicle('gti'), ion = data.vehicle('ioniq'), split = D.wrapped.monthly.carSplit;
    const clock = (() => {
      const R = 62, C = 2 * Math.PI * R, h = 7;
      const ticks = Array.from({ length: 12 }, (_, k) => { const a = (k / 12) * 2 * Math.PI - Math.PI / 2; return `<line x1="${80 + 70 * Math.cos(a)}" y1="${80 + 70 * Math.sin(a)}" x2="${80 + 76 * Math.cos(a)}" y2="${80 + 76 * Math.sin(a)}" stroke="#ffffff50" stroke-width="2"/>`; }).join('');
      const a0 = ((h - 0.5) / 12) * 2 * Math.PI - Math.PI / 2, a1 = ((h + 0.5) / 12) * 2 * Math.PI - Math.PI / 2;
      const wedge = `M80 80 L${80 + 78 * Math.cos(a0)} ${80 + 78 * Math.sin(a0)} A78 78 0 0 1 ${80 + 78 * Math.cos(a1)} ${80 + 78 * Math.sin(a1)}Z`;
      return `<svg class="wf-clk" width="200" height="200" viewBox="0 0 160 160" role="img" aria-label="Clock face: most-driven hour 7 am"><circle cx="80" cy="80" r="${R}" fill="none" stroke="#ffffff14" stroke-width="10"/><circle class="wf-clk-sweep" cx="80" cy="80" r="${R}" fill="none" stroke="#7FA7D9" stroke-opacity=".55" stroke-width="10" stroke-dasharray="${C.toFixed(1)}" stroke-dashoffset="0" data-c="${C.toFixed(1)}" transform="rotate(-90 80 80)"/>${ticks}<path class="wf-clk-hi" d="${wedge}" fill="#5BE7C4" opacity=".9" style="transform-origin:80px 80px"/><circle cx="80" cy="80" r="40" fill="#0A1420"/><text x="80" y="88" text-anchor="middle" fill="#F2F4F7" style="font:650 24px var(--dp-family-numeric)">7 am</text></svg>`;
    })();
    const garage = single
      ? `<div class="wf-gb"><div class="wf-gb-one" style="--car:var(--dp-car-${gti.colour})">${ui.icon('car', 56)}<b>${ui.esc(gti.nickname)}</b><small>${gti.year} ${ui.esc(gti.make)} ${ui.esc(gti.model)}</small></div></div>`
      : `<div class="wf-gb" role="img" aria-label="${ui.esc(split.map((s) => `${data.vehicle(s.vehicleId).nickname} ${s.pct}%`).join(', '))}">${split.map((s) => { const v = data.vehicle(s.vehicleId); return `<div class="wf-gb-r"><span>${ui.carDot(v)}<b>${ui.esc(v.nickname)}</b></span><div class="wf-gb-bar"><i style="width:${s.pct}%;background:var(--dp-car-${v.colour})"></i></div><em>${s.pct}%</em></div>`; }).join('')}</div>`;
    const frame7 = single
      ? { id: 'f7', wash: `var(--dp-car-${gti.colour})`, hero: 'hbars', art: garage, kicker: 'Your garage', head: `Every drive this year was in the ${gti.nickname}.`, body: 'One car, one baseline.' }
      : { id: 'f7', wash: `var(--dp-car-${gti.colour})`, hero: ctx.isPro ? 'hbars' : '', art: ctx.isPro ? garage : ui.lockPanel(garage, { text: 'Garage chapters come with Pro. Your Wrapped stays free.', nav: 'S-SUB-03/s2ndCar', cls: 'wf-hot wf-pro-lock' }), kicker: 'Pro chapter · your garage', head: F[7].line, body: F[7].sub };
    const fan = `<div class="wf-fan">${[
      [`<div class="wf-mini">${ui.glyph(hero.glyph, 70, { color: '#5BE7C4', width: 3.6 })}<div data-theme="dark">${ui.ring(hero.smoothness.score, 52, { stroke: 5, label: false })}</div><small>${ui.esc(hero.partOfDay.split(' ')[0])} · ${ui.esc(gti.nickname)}</small></div>`, 'rotate(-8deg) translate(-8px, 10px)'],
      [`<div class="wf-mini">${archetypeArt('glider', 84)}<b>${ui.esc(glider.name)}</b></div>`, 'rotate(0deg)'],
      [`<div class="wf-mini">${weekStrip(ctx, D.recaps[1], { short: true, scale: 0.5, cls: 'is-mini' })}<b class="ui-num">${D.recaps[1].avgSmoothness}</b><small>My week</small></div>`, 'rotate(8deg) translate(8px, 10px)'],
    ].map(([h, t]) => `<div class="wf-fan-c" data-t="${t}" style="transform:${t}">${h}${`<span class="wf-mini-mk">${I.loop({ size: 11, color: '#5BE7C4', dot: '#fff' })}</span>`}</div>`).join('')}</div>`;
    const routeGlyphs = D.routes.filter((r) => r.named).slice(0, 3).map((r, k) => `<span class="wf-road-g" style="--k:${k}">${ui.glyph(r.glyph, 64, { color: '#5BE7C4', width: 3.4 })}</span>`).join('');
    const frames = [
      { id: 'f1', wash: WASH.night, hero: 'road', art: `<div class="wf-road"><svg viewBox="0 0 320 90" width="100%" height="90" aria-hidden="true"><path d="M-10 70 C 60 70, 90 20, 160 30 S 260 70, 330 24" fill="none" stroke="#5BE7C4" stroke-width="4" stroke-linecap="round"/></svg><div class="wf-road-gs">${routeGlyphs}</div></div>`, kicker: 'Drive Wrapped', head: F[1].line, body: 'Your year on the road, in nine frames.' },
      { id: 'f2', wash: WASH.lucid, hero: 'odo', art: odometer(ctx, F[2].line.match(/([\d,]+) km/)[1], 'km'), kicker: 'Distance', head: F[2].line, bodyHtml: `${ui.esc(F[2].sub)} <span class="wf-est">${ui.provenance('estimated', 12)}Estimated</span>`, body: `${F[2].sub} Estimated.` },
      { id: 'f3', wash: WASH.violet, hero: 'clock', art: clock, kicker: 'Time', head: F[3].line, body: F[3].sub },
      { id: 'f4', wash: WASH.glide, hero: 'flip', art: archetypeCard(ctx, { id: glider.id, name: glider.name }, [['drives', D.trips.filter((t) => t.type === 'driver').length], ['km', F[2].line.match(/([\d,]+) km/)[1]]]), kicker: 'Your 2026 archetype', head: F[4].line, body: F[4].sub },
      { id: 'f5', wash: `var(--dp-car-${gti.colour})`, hero: 'flip', art: driveCard(ctx, hero, { record: true }), kicker: 'Smoothest drive of the year', head: F[5].line.replace(/^Smoothest drive of the year: /, ''), body: '' },
      { id: 'f6', wash: WASH.glacier, hero: 'rings', art: `<div class="wf-ba"><div>${ui.ring(first.score, 108, { stroke: 9, label: false, aria: `September ${first.score}` })}<small>Sep</small></div><span class="wf-ba-ar">${ui.icon('chevronRight', 28)}</span><div>${ui.ring(best.score, 132, { stroke: 11, label: false, aria: `November ${best.score}` })}<small>Nov</small></div></div>`, kicker: 'Most improved', head: F[6].line.replace(/^Most improved: /, '').replace(/ \(.*\)\.?$/, '.'), body: 'September to November, on your most-driven route.' },
      frame7,
      { id: 'f8', wash: WASH.lime, hero: 'meter', art: `<div class="wf-mt" role="img" aria-label="Abrupt stops: September 100%, November 63%"><div class="wf-mt-c"><div class="wf-mt-bar is-ref"><i style="height:100%"></i></div><b>Sep</b><small>abrupt stops</small></div><div class="wf-mt-c is-now"><div class="wf-mt-bar"><i style="height:63%"></i></div><b>Nov</b><small>−37%</small></div></div>`, kicker: 'Habit mastered', head: 'Gentler stops.', body: F[8].line.replace(/^Habit mastered: gentler stops\. /, '') },
      { id: 'f9', wash: WASH.glide, hero: 'fan', art: fan, kicker: 'That’s 2026', head: F[9].line, body: 'Route shape, archetype or your week. No places, no speed.', acts: `${ui.button('Share your year', { kind: 'filled', size: 'l', icon: 'share', nav: 'S-SHR-01/archetype', params: { wrapped: A.id }, cls: 'wf-hot' })}${done}` },
    ].filter((f) => !(skipHabit && f.id === 'f8'));
    const idx = (id) => Math.max(0, frames.findIndex((f) => f.id === id));
    const start = { singleCar: idx('f7'), habitFrameSkipped: idx('f9'), proChapters: idx('f7'), proChaptersLocked: idx('f7'), f2: 1, f3: 2, f4: 3, f5: 4, f6: 5, f8: idx('f8'), f9: idx('f9') }[st] ?? 0;
    return { key: `wrp-a-${st}-${ctx.isPro}`, title: 'Drive Wrapped', sub: A.period, start, frames, dur: 5200 };
  }

  screen('S-WRP-02', {
    name: 'Annual Drive Wrapped player',
    wp: 'F', platforms: ['ios', 'android'], presentation: 'full', tab: 'insights',
    states: [
      { id: 'default', label: 'Def (frames 1–9) · frame 1' },
      { id: 'singleCar', label: 'Single car (frame 7 variant)' },
      { id: 'habitFrameSkipped', label: 'Habit frame skipped (8 frames)' },
      { id: 'proChapters', label: 'Pro chapters (frame 7, Pro)' },
      { id: 'proChaptersLocked', label: 'Pro chapter on Free (extra)' },
      { id: 'f2', label: 'Frame 2 odometer (extra)' }, { id: 'f3', label: 'Frame 3 clock (extra)' }, { id: 'f4', label: 'Frame 4 archetype (extra)' }, { id: 'f5', label: 'Frame 5 smoothest drive (extra)' }, { id: 'f6', label: 'Frame 6 most improved (extra)' }, { id: 'f8', label: 'Frame 8 habit (extra)' }, { id: 'f9', label: 'Frame 9 share (extra)' },
    ],
    stateParams: { proChaptersLocked: { ent: 'free' } },
    statusBar: 'light', backTo: 'S-STA-01',
    chrome: { tabBar: false },
    render: (ctx) => playerRender(ctx, annualPlayer(ctx)),
    mount: (el, ctx) => playerMount(el, ctx, annualPlayer(ctx)),
    actions: PLAYER_ACTIONS(annualPlayer),
    spec: {
      purpose: 'The driving year told as a celebratory story from 1 December (≥ 10 driver trips across ≥ 30 days): km, hours, archetype, smoothest drive, most improved route, garage split, habit mastered (only if statistically real) and share cards — never speed, max G, other users or praise for volume (F-NTF-05, F-SHR-04, A2 §3.4).',
      layout: [
        'Player frame as S-RCP-01: 9 segments (8 when the habit frame is skipped), Loop + “Drive Wrapped” + “2026”, close; night stage with chapter washes.',
        'F1 “2026, decoded.”: a Glide road line crosses the black screen, then the top route glyphs assemble.',
        'F2 odometer “991 km” + “That’s about 1.8× Toronto to Montréal.” with an “Estimated” provenance chip (vetted comparison list).',
        'F3 clock face: hour ring sweeps, the 7 am wedge lifts out · “15 hours behind the wheel.” / “Your most-driven hour: 7 am.”',
        'F4 archetype flip card (share hero): Glider line art, “The Glider”, drives + km · “You’re The Glider.” / “Few abrupt inputs, all year.”',
        'F5 smoothest drive card (glyph, ring 91 with the gold record notch, “Route record”) · “5 Nov, The Commute, 91.”',
        'F6 before/after rings 76 → 91 (Sep → Nov) · “The Commute, 76 → 91.”',
        'F7 garage bars (Pro): GTI 75% · Ioniq 25% · “The GTI did 75% of the driving.” / “The Ioniq did the road trip.” Free: blurred + lock + “Garage chapters come with Pro. Your Wrapped stays free.” Single car: car tile + “Every drive this year was in the GTI.” (free).',
        'F8 habit meter Sep 100% vs Nov 63% · “Gentler stops.” / “37% fewer abrupt stops than in September.” — shown only when the change is statistically significant, otherwise skipped.',
        'F9 three cards fan out (route glyph, archetype, week) · “Share your year.” · Share your year → S-SHR-01/archetype · Done.',
      ],
      components: ['iconButton', 'button', 'ring', 'glyph', 'num', 'provenance', 'carDot', 'lockPanel'],
      interactions: ['Tap right/left, hold to pause, auto-advance 5.2 s (stops on F9; pauses on screen-reader focus), close/Done → back. F5 card → S-TRP-01. F7 lock → S-SUB-03/s2ndCar. Share → S-SHR-01 (glyph default, archetype, week; no places).'],
      motion: [
        'Chapter change 650 ms (outgoing scale 0.92 + fade 250 ms ease.emphasizedAccelerate natively; incoming rise 24 px, spring.slow; wash morph spring.effects).',
        'F1 road draw-on 1400 ms then glyphs scale in (120 ms stagger). F2 odometer roll right-to-left (40 ms). F3 conic sweep 1200 ms (ease.countUp) then the 7 am wedge lifts (spring.fast). F4 card flip (spring.bouncy) + one success haptic. F5 glyph draw-on + ring reveal. F6 ring reveals. F7 bars grow (spring.slow). F8 bars fill. F9 cards fan to ±8° (spring.default, 70 ms stagger).',
        'RM: 220 ms cross-fades; final values; no flips, rolls or fans.',
      ],
      a11y: ['Each frame is one element with its full copy (“Drive Wrapped. Frame 2 of 9. Distance. You drove 991 km. That’s about 1.8× Toronto to Montréal. Estimated.”). Auto-advance pauses on focus.', 'Clock: “Clock face: most-driven hour 7 am”. Garage: “GTI 75%, Ioniq 25%”.', 'No colour-only meaning: record = rosette + words; Estimated = dashed provenance glyph + word.', 'XL: copy reflows; art shrinks to 40%.'],
      platformDiffs: { ios: 'fullScreenCover pager; glass close/Done; SF Pro Expanded numerals in the odometer.', android: 'Full-screen destination; HorizontalPager; Google Sans Flex wdth 120 numerals; predictive back dismisses.' },
      analytics: ['wrapped_opened {type: annual}', 'wrapped_frame_viewed {frame}', 'wrapped_completed', 'wrapped_shared {card: glyph|archetype|week}', 'locked_content_viewed {type: wrapped_garage}'],
      copy: Object.fromEntries([...D.wrapped.annual.frames.map((f) => [`F${f.n}`, `${f.line}${f.sub ? ' · ' + f.sub : ''}`]), ['F7 single car', 'Every drive this year was in the GTI. · One car, one baseline.'], ['F7 Free lock', 'Garage chapters come with Pro. Your Wrapped stays free.'], ['F9 body', 'Route shape, archetype or your week. No places, no speed.']]),
      freePro: 'Free: frames 1–6, 8, 9 and the single-car frame. Pro/Preview: + the garage split (F7) and per-route chapters; Pro card styles in S-SHR-01. Free sees F7 blurred with a lock.',
      data: 'wrapped.annual (sample “2026, decoded.” computed from the fixture Sep 28 – Nov 6; real annual ships by 1 Dec 2027, F-NTF-05). F2 comparison provenance estimated. F5 trip t20261105-0742. F6 from routes r_commute.trend (76 on 29 Sep → 91 on 5 Nov). F7 split from wrapped.monthly.carSplit (75/25). F8 meter derived from “37% fewer” (Sep = 100%, Nov = 63%). Archetype card stats: driver trips count and annual km.',
      icons: ['close', 'share', 'car', 'lock', 'chevronRight', 'rosette'],
      assets: ['logo-a-loop.svg'],
    },
  });
})();
