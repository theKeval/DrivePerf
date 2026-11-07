/* WP-C · Story, Replay, Analysis, data quality & scores — screen definitions (DRIVE-32). Contract: prototypes/app/AUTHORING.md.
   Register each screen with DP.screen('S-XXX-NN', {...}). Only the WP-C owner edits this file. */
(function () {
  const { screen } = window.DP;

  /* ==========================================================================================================
     EXEMPLAR · S-TRP-01 Trip detail                                       (written by the engine author; WP-C owns)
     One place with a drive's summary, scores, events and actions (F-RPL-07). Free summaries forever; Pro depth
     locked with the user's own data blurred (F-HIS-03 "locked, not lost").
     ========================================================================================================== */
  const BAND = { smoother: ['up', '▲', 'Smoother than usual'], usual: ['same', '●', 'Usual'], rougher: ['down', '▼', 'Rougher than usual'] };

  function tripKind(ctx, t) {
    const st = ctx.state;
    const passenger = t.type === 'passenger' || st === 'passengerNotADrive';
    return {
      loading: st === 'Load',
      passenger,
      unscorable: !passenger && !t.smoothness,
      locked: st === 'Lock' || ctx.data.isLocked(t, ctx.ent),
      removed: st === 'detailedDataRemoved',
      interrupted: !!t.interrupted,
      short: st === 'shortTrip',
      gap: t.quality.spans.gap.length > 0,
      excluded: t.quality.spans.excluded.length > 0,
      estStart: t.quality.spans.estimated.length > 0 && t.source === 'auto',
    };
  }

  function banners(ctx, t, k) {
    const { ui, data } = ctx, out = [];
    if (k.passenger) out.push(ui.banner({ kind: 'passenger', icon: 'passenger', title: 'Marked as passenger', text: 'It isn’t scored and doesn’t count toward your baseline, routes or recaps.', action: { text: 'Change', nav: 'S-TRP-02/typePicker', params: { trip: t.id } } }));
    if (k.interrupted) out.push(ui.banner({ kind: 'attention', icon: 'shield', title: `Interrupted at ${data.time(t.interrupted.at)}`, text: `${ctx.ios ? t.interrupted.reason : 'Android stopped DrivePerf in the background'}. ${t.interrupted.note.replace('Recording was interrupted. ', '')} The missing part is shown as a gap, never scored.` }));
    if (k.unscorable) out.push(ui.banner({ kind: 'info', icon: ctx.icons.quality('Unscorable', { size: 20 }), title: 'We couldn’t score this one fairly', text: `${t.quality.reason}. Here’s the route and the stats anyway.`, action: { text: 'Why', nav: 'S-DQ-01/unscorable', params: { trip: t.id } } }));
    if (k.short) out.push(ui.banner({ kind: 'info', icon: 'info', title: `Short drive · ${t.distanceKm} km`, text: 'Short drives give the score less to work with, so expect it to move more from drive to drive.' }));
    if (!k.unscorable && !k.passenger && (k.gap || k.excluded)) {
      const g = t.quality.spans.gap[0];
      const parts = [];
      if (k.gap) parts.push(`${Math.round(g[1] - g[0])} s without GPS downtown (dotted on the map)`);
      if (k.excluded) parts.push(`${t.quality.excludedMin} min left out: the phone moved`);
      if (k.estStart) parts.push(`the first ≈ ${data.km(t.quality.estimatedStartM / 1000)} km is estimated (dashed)`);
      out.push(ui.banner({ kind: 'info', icon: ctx.icons.quality(t.quality.badge, { size: 20 }), title: `${t.quality.badge} data`, text: parts.join(' · ').replace(/^./, (c) => c.toUpperCase()) + '.', action: { text: 'Details', nav: 'S-DQ-01', params: { trip: t.id } } }));
    }
    if (k.removed) out.push(ui.banner({ kind: 'info', icon: 'layers', title: 'Detailed data removed', text: 'You removed the raw sensor data to save space. The summary, route and score stay; Analysis and event traces are no longer available.', action: { text: 'Storage', nav: 'S-SET-07' } }));
    return out;
  }

  function mapHero(ctx, t, k) {
    const { ui } = ctx;
    const overlay = `<div class="trp-map-top">${ui.qualityBadge(t.quality.badge, { glass: true, text: `${t.quality.badge} data`, nav: 'S-DQ-01', params: { trip: t.id }, label: `${t.quality.badge} data. Opens data quality details` })}${k.estStart ? ui.chip('Estimated start', { kind: 'q', icon: ui.provenance('estimated', 12), cls: 'ui-glassy' }) : ''}</div>
      <span class="trp-map-cta ui-glassy">${k.locked ? `${ui.icon('lock', 14)}Replay in Pro` : `${ui.icon('replay', 16)}Replay`}</span>`;
    return ui.map(t, { w: 370, h: 240, pad: 30, insetTop: 34, insetBottom: 30, fluid: true, overlay, nav: k.locked ? 'S-SUB-03/replayAnalysis30Days' : 'S-RPL-01', params: { trip: t.id }, cls: 'trp-map' });
  }

  function metaRow(ctx, t, k) {
    const { ui, data } = ctx, v = data.vehicle(t.vehicleId);
    const src = t.source === 'auto' ? 'Auto-detected' : `Started from ${t.sourceDetail || 'the app'}`;
    return `<div class="trp-meta">${k.passenger ? ui.carChip('passenger') : ui.carChip(v, { nav: 'S-TRP-02/carPicker', params: { trip: t.id } })}<span>${ui.esc(src)}</span><i>·</i><span>${data.time(t.start)}–${data.time(t.interrupted ? t.interrupted.at : t.end)}</span></div>`;
  }

  function scoreCard(ctx, t, k) {
    const { ui, data } = ctx;
    if (k.passenger) return '';
    if (k.unscorable) return ui.card(`<div class="trp-score">${ui.ring(null, 118, { unscorable: true, label: false })}<div class="trp-score-t"><span class="trp-k">Smoothness</span><b class="trp-ns">Not scored</b><p>${ui.esc(t.quality.reason)}.</p><span class="trp-link ui-press" ${ui.act({ nav: 'S-SCO-02' })}>${ui.icon('info', 15)}How scores work</span></div></div>`);
    const s = t.smoothness, rec = s.record;
    const recLine = rec && rec.isNew ? `<span class="trp-rec">${ctx.icons.svg('rosette', { size: 15 })}New route record · beat ${rec.previous.score} from ${data.date(rec.previous.date)}</span>` : '';
    const based = t.quality.excludedMin ? `<span class="trp-based">${ctx.icons.quality(t.quality.badge, { size: 13 })}Score based on ${t.quality.scoredMin} of ${t.quality.totalMin} min</span>` : '';
    const contrib = s.contributors.map((c) => { const b = BAND[c.band]; return `<div class="trp-contrib"><span class="trp-contrib-l">${ui.esc(c.label)}</span>${ui.chip('', { kind: b[0], html: `<b aria-hidden="true">${b[1]}</b>${b[2]}` })}<span class="trp-contrib-r">${ui.esc(c.reason)}</span></div>`; }).join('');
    const aria = `Smoothness ${s.score}. ${s.delta ? `${Math.abs(s.delta)} ${s.delta > 0 ? 'above' : 'below'} your usual` : 'Right on your usual'}${s.basis === 'route' ? ` on ${s.routeName}` : ''}.${rec && rec.isNew ? ` New route record, beating ${rec.previous.score} from ${data.date(rec.previous.date, { day: 'numeric', month: 'long' })}.` : ''}`;
    return ui.card(`<div class="trp-score" aria-label="${ui.esc(aria)}">${ui.ring(s.score, 118, { label: false, base: s.calibrating ? null : s.baseline, rec: rec && rec.isNew ? rec.previous.score : null, recNew: !!(rec && rec.isNew), calibrating: s.calibrating, stroke: 10, aria })}
        <div class="trp-score-t"><span class="trp-k">Smoothness</span>${ui.deltaChip(s, { record: false })}${recLine}${based}<span class="trp-link ui-press" ${ui.act({ nav: 'S-SCO-02', label: 'How scores work' })}>${ui.icon('info', 15)}How scores work</span></div></div>
      <div class="trp-contribs">${contrib}</div>
      <div class="trp-more ui-press" ${ui.act({ nav: 'S-SCO-01', params: { trip: t.id } })}>Smoothness breakdown${ui.icon('chevronRight', 15)}</div>`, { label: aria });
  }

  function actions(ctx, t, k) {
    const { ui } = ctx;
    const lockNav = { nav: 'S-SUB-03/replayAnalysis30Days', params: { trip: t.id } };
    const items = [
      ui.actionButton('Replay', { icon: 'replay', primary: !k.locked, locked: k.locked, ...(k.locked ? lockNav : { nav: 'S-RPL-01', params: { trip: t.id } }) }),
      ui.actionButton('Analysis', { icon: 'analysis', locked: k.locked, disabled: k.removed, ...(k.locked ? lockNav : k.removed ? { toast: 'Detailed data removed for this drive' } : { nav: 'S-ANL-01', params: { trip: t.id } }) }),
      k.passenger ? ui.actionButton('Edit', { icon: 'edit', nav: 'S-TRP-02', params: { trip: t.id } }) : ui.actionButton('Story', { icon: 'story', nav: 'S-STY-01', params: { trip: t.id } }),
      k.passenger ? '' : ui.actionButton('Share', { icon: 'share', nav: 'S-SHR-01', params: { trip: t.id } }),
    ].filter(Boolean);
    return `<div class="ui-actrow" style="--n:${items.length}">${items.join('')}</div>`;
  }

  function statTiles(ctx, t, k) {
    const { ui, data } = ctx, P = data.D.provenance.metrics;
    const est = k.estStart ? `≈ ${data.km(t.quality.estimatedStartM / 1000)} km estimated` : null;
    return ui.stats([
      { label: 'Distance', value: data.km(t.distanceKm), unit: 'km', unitSpoken: 'kilometres', prov: P.distance, sub: est },
      { label: 'Duration', value: Math.round(t.durationS / 60), unit: 'min', unitSpoken: 'minutes', prov: P.duration, sub: k.interrupted ? 'until interrupted' : null },
      { label: 'Moving time', value: Math.round(t.movingS / 60), unit: 'min', unitSpoken: 'minutes', prov: P.movingTime },
      { label: 'Stopped', value: Math.max(1, Math.round(t.stoppedS / 60)), unit: 'min', unitSpoken: 'minutes', prov: P.stoppedTime },
      { label: 'Stops', value: t.stopCount, prov: P.stops },
      { label: 'Avg moving', value: Math.round(t.avgMovingKmh), unit: 'km/h', unitSpoken: 'kilometres per hour', prov: P.avgMovingSpeed },
      { label: 'Max speed', value: t.maxKmh, unit: 'km/h', unitSpoken: 'kilometres per hour', prov: P.maxSpeed },
      { label: 'Climb', value: `↗ ${t.elevGainM}`, unit: 'm', unitSpoken: 'metres', prov: P.elevationGainLoss },
      { label: 'Descent', value: `↘ ${t.elevLossM}`, unit: 'm', unitSpoken: 'metres', prov: P.elevationGainLoss },
    ], { cols: 3 });
  }

  function eventsSection(ctx, t, k) {
    const { ui, data } = ctx;
    if (k.passenger) return '';
    if (k.unscorable) return ui.section('Events', `<div class="ui-pad"><p class="trp-note">Events aren’t detected when the phone moves around freely.</p></div>`, { style: 'caption' });
    const n = (type) => t.events.filter((e) => e.type === type).length;
    const chips = ['brake', 'accel', 'corner'].map((ty) => ui.chip('', { kind: 'plain', html: `${ctx.icons.pin(ty, { size: 16 })}${ctx.icons.eventLabel(ty).replace('Hard ', '').replace(/^./, (c) => c.toUpperCase())} <b class="ui-num">${n(ty)}</b>`, cls: 'trp-count' })).join('');
    const time = (e) => data.time(new Date(new Date(t.start).getTime() + e.t * 1000).toISOString());
    const body = t.events.length
      ? `<div class="ui-pad trp-counts">${chips}</div><div class="ui-card is-flush trp-events" role="list">${t.events.map((e) => ui.eventRow(e, { tripId: t.id, time: time(e) })).join('')}</div>`
      : `<div class="ui-card trp-none"><span>${ui.icon('check', 18)}</span><div><b>No abrupt inputs on this drive.</b><p>Nothing crossed the braking, acceleration or cornering thresholds.</p></div></div>`;
    return ui.section(`Events · ${t.events.length}`, body, { style: 'caption' });
  }

  function highlights(ctx, t, k) {
    const { ui } = ctx;
    if (k.passenger || k.unscorable) return '';
    const ev = t.tip && t.tip.eventId ? t.events.find((e) => e.id === t.tip.eventId) : null;
    const clean = t.cleanest ? ui.card(`<div class="trp-clean"><span>${ui.icon('sparkle', 18)}</span><div><span class="trp-k">Cleanest stretch</span><p>${ui.esc(t.cleanest.text)}</p></div></div>`) : '';
    const tip = t.tip ? ui.tipCard({ text: t.tip.text, event: k.removed ? null : ev, tripId: t.id }) : '';
    return ui.section('Highlights', `<div class="ui-pad ui-stack" style="gap:8px">${clean}${tip}</div>`, { style: 'caption' });
  }

  function details(ctx, t, k) {
    const { ui, data } = ctx;
    const route = t.routeId && data.route(t.routeId);
    const rows = [
      ui.row({ lead: `<span class="ui-row-ic is-neutral">${ctx.icons.quality(t.quality.badge, { size: 18 })}</span>`, title: `${t.quality.badge} data`, sub: ui.qualityDetail(t), nav: 'S-DQ-01', params: { trip: t.id } }),
      !k.passenger && !k.unscorable && t.efficiency ? ui.row({ icon: data.vehicle(t.vehicleId)?.powertrain === 'ev' ? 'bolt' : 'car', title: 'Efficiency habits', subHtml: true, sub: `<span class="dp-est">${ui.esc(t.efficiency.label)}</span> · estimated`, nav: 'S-SCO-06', params: { trip: t.id } }) : '',
      route ? ui.row({ icon: 'routes', title: route.named ? route.name : 'Unnamed route', sub: `${route.runCount} drives · record ${route.record ? route.record.score : '—'}`, nav: 'S-RTE-02', params: { route: route.id } }) : '',
    ];
    const manage = [
      ui.row({ icon: 'edit', title: 'Edit trip', sub: 'Trip type, car', nav: 'S-TRP-02', params: { trip: t.id } }),
      ui.row({ icon: 'export', title: 'Export GPX / CSV', trailing: ctx.isPro ? null : 'lock', ...(ctx.isPro ? { nav: 'S-PRV-04', params: { trip: t.id } } : { nav: 'S-SUB-03/export', params: { trip: t.id } }) }),
      ui.row({ icon: 'trash', iconTone: 'critical', title: 'Delete drive', destructive: true, nav: 'S-TRP-03', params: { trip: t.id }, trailing: '' }),
    ];
    return ui.section('Details', ui.list(rows), { style: 'caption' }) + ui.section('', ui.list(manage), { style: 'caption' });
  }

  function lockedDepth(ctx, t, k) {
    const { ui } = ctx;
    if (!k.locked) return '';
    const inner = `<div class="trp-lockin">${ui.map(t, { w: 340, h: 96, pad: 12, others: false, stops: false, attribution: false, radius: 14 })}${ui.timeline(t, 330, { cursor: 0.42 })}</div>`;
    return ui.section('Replay & Analysis', `<div class="ui-pad">${ui.lockPanel(inner, { text: ctx.D.entitlement.lockSheets.oldAnalysis, nav: 'S-SUB-03/replayAnalysis30Days', params: { trip: t.id } })}</div>`, { style: 'caption', footer: 'Your summary, score and Story stay free forever. Nothing is deleted because of your plan.' });
  }

  function loading(ctx) {
    const { ui } = ctx;
    return `<div class="trp" aria-busy="true"><div class="ui-pad">${ui.skeleton('block', 232)}</div><div class="ui-pad" style="margin-top:12px">${ui.card(`<div class="trp-score"><span class="ui-ring is-loading" style="width:124px;height:124px">${''}</span>${ui.skeleton('lines', 3)}</div>`)}</div>
      <div class="ui-pad" style="margin-top:12px">${ui.skeleton('stats', 6)}</div><div class="ui-pad" style="margin-top:16px">${ui.card(ui.skeleton('lines', 4))}</div><p class="trp-note" style="text-align:center;margin-top:16px">Loading this drive…</p></div>`;
  }

  screen('S-TRP-01', {
    name: 'Trip detail',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'push', tab: 'drives',
    states: [
      { id: 'default', label: 'Def' },
      { id: 'Part', label: 'Part (estimated start, gaps)' },
      { id: 'unscorable', label: 'Unscorable' },
      { id: 'passengerNotADrive', label: 'Passenger / not a drive' },
      { id: 'interrupted', label: 'Interrupted' },
      { id: 'shortTrip', label: 'Short trip' },
      { id: 'Lock', label: 'Lock (older than 30 days, Free)' },
      { id: 'detailedDataRemoved', label: 'Detailed data removed' },
      { id: 'Load', label: 'Load' },
    ],
    stateParams: {
      default: { trip: 't20261105-1745' },
      Part: { trip: 't20261020-0740' },
      unscorable: { trip: 't20261106-1210' },
      passengerNotADrive: { trip: 't20261031-1905' },
      interrupted: { trip: 't20261013-1742' },
      shortTrip: { trip: 't20261104-0755' },
      Lock: { trip: 't20261002-0745', ent: 'free' },
      detailedDataRemoved: { trip: 't20261002-0745' },
      Load: { trip: 't20261105-1745' },
    },
    render(ctx) {
      const { ui, data, params } = ctx;
      const t = data.trip(params.trip) || data.trip('t20261105-1745');
      const k = tripKind(ctx, t);
      const nav = { title: t.label, subtitle: `${t.partOfDay} · ${data.date(t.start)}`, trailing: [k.passenger ? '' : ui.iconButton('share', { nav: 'S-SHR-01', params: { trip: t.id }, label: 'Share' }), ui.iconButton('more', { svg: ui.icon(ctx.ios ? 'more' : 'moreVert', 22), menu: 'trp-menu', label: 'More' })] };
      const menu = ui.menu('trp-menu', [
        { text: 'Edit trip', icon: 'edit', nav: 'S-TRP-02', params: { trip: t.id } },
        { text: 'Export GPX / CSV', icon: 'export', trailing: ctx.isPro ? '' : ui.proBadge(), nav: ctx.isPro ? 'S-PRV-04' : 'S-SUB-03/export', params: { trip: t.id } },
        { text: 'Data quality', icon: 'info', nav: 'S-DQ-01', params: { trip: t.id } },
        '-',
        { text: 'Delete drive', icon: 'trash', destructive: true, nav: 'S-TRP-03', params: { trip: t.id } },
      ]);
      if (k.loading) return ui.page(loading(ctx), { nav: { title: t.label, subtitle: `${t.partOfDay} · ${data.date(t.start)}` } });
      const blocks = [
        ...banners(ctx, t, k).map((b) => `<div class="ui-pad">${b}</div>`),
        `<div class="ui-pad">${mapHero(ctx, t, k)}</div>`,
        `<div class="ui-pad">${metaRow(ctx, t, k)}</div>`,
        `<div class="ui-pad">${actions(ctx, t, k)}</div>`,
        lockedDepth(ctx, t, k),
        k.passenger ? '' : `<div class="ui-pad">${scoreCard(ctx, t, k)}</div>`,
        ui.section('Stats', `<div class="ui-pad">${statTiles(ctx, t, k)}</div>`, { style: 'caption', footer: `Long-press any number to see how it was measured. Speed is shown as plain text, never ranked.` }),
        eventsSection(ctx, t, k),
        highlights(ctx, t, k),
        details(ctx, t, k),
      ].filter(Boolean);
      return ui.page(`<div class="trp">${blocks.map((b) => `<div class="trp-b" data-rise>${b}</div>`).join('')}</div>`, { nav, fixed: menu });
    },
    mount(el, ctx) {
      ctx.ui.anim.maps(el);
      ctx.ui.anim.rings(el);
      ctx.ui.anim.rise(el, '.trp-b[data-rise]', { stagger: 30, delay: 80 });
      // long-press on a stat tile → provenance toast (F-DQ-05)
      let timer;
      const down = (e) => { const s = e.target.closest('.ui-stat'); if (!s) return; timer = setTimeout(() => { const p = s.dataset.prov; ctx.haptic('longPress'); ctx.toast(`${p.charAt(0).toUpperCase() + p.slice(1)} · ${ctx.D.provenance.legend[p]}`, { icon: 'info', duration: 3600 }); }, 450); };
      const up = () => clearTimeout(timer);
      el.addEventListener('pointerdown', down); el.addEventListener('pointerup', up); el.addEventListener('pointerleave', up);
      return () => { el.removeEventListener('pointerdown', down); el.removeEventListener('pointerup', up); };
    },
    spec: {
      purpose: 'One place with a drive’s summary, scores, events and actions, so the user can decide where to dig in (F-RPL-07). The summary is Free forever; Replay/Analysis older than 30 days are Pro and shown locked with this trip’s own data blurred (F-HIS-03).',
      layout: [
        'Top bar: iOS inline title (route label) + subtitle (part of day · date) with glass back, Share and “…” menu (Edit trip, Export GPX/CSV, Data quality, Delete) · Android MediumFlexibleTopAppBar (back, Share, overflow) with the title block in content that collapses into the bar on scroll.',
        'State banners (passenger, interrupted, unscorable, short trip, Part data, detailed data removed) directly under the bar.',
        'Map hero, full width minus 16 margins, 232 tall, radius = card (24 iOS / 16 Android): route in Glide over the plain-canvas map, estimated start dashed, GPS gap dotted, stop capsules, neutral max-speed triangle, event pins by shape; quality badge and “Estimated start” chips top-left (glass on iOS); “Replay” glass capsule bottom-left (“Replay in Pro” + lock when locked); “© OpenStreetMap contributors” bottom-right.',
        'Meta row: car chip (tap → car picker) · source (Auto-detected / Started from Widget, Control Center) · start–end time.',
        'Smoothness card: ring 124 (baseline notch, record notch) + comparison chip, record line (rosette, gold), “Score based on n of m min” when excluded, “How scores work”; four contributor rows (label · band chip ▲/●/▼ · reason); “Smoothness breakdown ›” → S-SCO-01.',
        'Action row directly under the meta row (4 equal buttons, 68 tall): Replay (primary) · Analysis · Story · Share. Locked: lock badges, both open S-SUB-03. Passenger: Replay · Analysis · Edit.',
        'Locked (Free > 30 days): “Replay & Analysis” lock panel — this trip’s synced timeline blurred 14 px + “Keep exploring every drive with Pro.” + See plans.',
        'Stats 3×3 tiles in PRD order: distance, duration, moving time, stopped, stops, avg moving speed, max speed (neutral, never first), climb, descent — each with its provenance glyph.',
        'Events · n: count chips by type (pin glyph + count) and event rows (pin 28 · “Hard braking” · place · time of day · 0.42 g · 1.4 s); “No abrupt inputs on this drive.” when there are none.',
        'Highlights: positive card first (“Cleanest stretch: …”), then the tip card (“One thing for next time”, long-G sparkline, “See the moment ›”).',
        'Details list: data quality (badge + detail) → S-DQ-01 · efficiency habits (≈ estimated) → S-SCO-06 · route (runs, record) → S-RTE-02. Manage list: Edit trip · Export GPX/CSV (Pro) · Delete drive (destructive).',
      ],
      components: ['navBar', 'menu', 'banner', 'map', 'qualityBadge', 'chip', 'card', 'ring', 'deltaChip', 'actionButton', 'lockPanel', 'timeline', 'stats', 'eventRow', 'tipCard', 'sparkline', 'list', 'row', 'section', 'skeleton'],
      interactions: [
        'Map / Replay → S-RPL-01 (Lock → S-SUB-03 replayAnalysis30Days). Analysis → S-ANL-01. Story → S-STY-01. Share (button or bar) → S-SHR-01. (Story/Replay/Analysis/Share pass the D1 gate → S-DRV-05 while moving.)',
        'Quality badge / Data quality row → S-DQ-01 · Smoothness card → S-SCO-01 · “How scores work” → S-SCO-02 · Event row → S-RPL-02 (event param) · Tip “See the moment” → S-RPL-02.',
        'Car chip → S-TRP-02/carPicker · Passenger “Change” → S-TRP-02/typePicker · Edit → S-TRP-02 · Export → S-PRV-04 (Pro) or S-SUB-03/export (Free) · Delete → S-TRP-03 dialog · Route row → S-RTE-02 · Efficiency → S-SCO-06.',
        '“…” opens a glass Menu (iOS) / DropdownMenu (Android). Long-press any stat tile → provenance popover (prototype: toast) with the glyph meaning, haptic longPress.',
      ],
      motion: [
        'Enter from a trip card: iOS zoom transition from the card, Android container transform (spring.slow).',
        'Map: route draw-on 1800 ms ease.drawOn; estimated/gap overlays, stops and max pin fade in at 1500 ms; event pins pop in time order from 1600 ms, 40 ms stagger, shared.pinPop. RM: the complete route fades in (220 ms), pins appear in place.',
        'Smoothness ring reveal (motion.md §3.4): 1400 ms count-up locked to the arc, baseline notch scales in, landing tap haptic; Android Cookie9Sided container rotates as the count runs. RM: final ring, static number.',
        'Sections rise 8 px, 30 ms stagger, spring.default. Menu scales in from the top-trailing corner (spring.fast).',
      ],
      a11y: [
        'Score card is one element: “Smoothness 85. 2 above your usual on The Commute home.” (+ “New route record, beating 89 from 14 October.”).',
        'Map label: “Map of your drive, 39 kilometres from Work to Home. 2 events, 2 stops.” Event pins are a time-ordered list; each event row reads “Hard braking at the Gardiner. Peak 0.4 g.”',
        'Stat tiles: “Distance, 39.0 kilometres. Derived.” with the long-press action “Show how this was measured”; ⓘ footer explains long-press.',
        'Speed tiles use the same neutral component (no colour, no emphasis, never first). Deltas use ▲/▼/● and “above/below your usual”, never red.',
        'XL: tiles go to 2 columns, the action row to 2×2, contributor reasons wrap; nothing truncates.',
      ],
      platformDiffs: {
        ios: 'Inline title + .navigationSubtitle, glass back/Share/“…” toolbar buttons, Menu for overflow; map chips and Replay capsule are glass; ring on content; zoom transition from the source card.',
        android: 'MediumFlexibleTopAppBar (title in content, collapses on scroll), overflow DropdownMenu; quality badge as AssistChip; ring on a Cookie9Sided primaryContainer shape; actions: filled primary (radius 28) + tonal surfaceContainerHigh buttons; list sections as M3E segmented ListItems; container transform from the card.',
      },
      analytics: ['trip_detail_opened {source, age_bucket}', 'locked_content_viewed {type: replay|analysis, age_bucket}', 'trip_action_tapped {action: replay|analysis|story|share|export|edit|delete}', 'provenance_viewed {metric}'],
      copy: {
        Passenger: 'Marked as passenger. It isn’t scored and doesn’t count toward your baseline, routes or recaps. · Change',
        Interrupted: 'Interrupted at {time}. {iOS ended DrivePerf in the background | Android stopped DrivePerf in the background}. Everything before then is saved. The missing part is shown as a gap, never scored.',
        Unscorable: 'We couldn’t score this one fairly. {quality.reason}. Here’s the route and the stats anyway.',
        Part: '{badge} data. {41} s without GPS downtown (dotted on the map) · the first ≈ 0.4 km is estimated (dashed).',
        'Short trip': 'Short drive · {km} km. Short drives give the score less to work with, so expect it to move more from drive to drive.',
        'Detailed data removed': 'You removed the raw sensor data to save space. The summary, route and score stay; Analysis and event traces are no longer available.',
        Lock: 'Keep exploring every drive with Pro. (entitlement.lockSheets.oldAnalysis) · Your summary, score and Story stay free forever. Nothing is deleted because of your plan.',
        'No events': 'No abrupt inputs on this drive. Nothing crossed the braking, acceleration or cornering thresholds.',
      },
      freePro: 'Free: everything on this screen is visible for every trip of any age. For trips older than 30 days Replay and Analysis show lock badges and open S-SUB-03 (replayAnalysis30Days) over a blurred render of this trip; Export shows a PRO badge and opens S-SUB-03/export. Pro/Preview/Ride-along: no locks.',
      data: 'Fixtures: default t20261105-1745 · Part t20261020-0740 (41 s gap, needed brake at Spadina & King) · unscorable t20261106-1210 · passenger t20261031-1905 · interrupted t20261013-1742 · short trip t20261104-0755 · Lock / detailed data removed t20261002-0745. Event time of day = trip.start + event.t (derived).',
      icons: ['replay', 'analysis', 'story', 'share', 'more', 'edit', 'export', 'trash', 'lock', 'info', 'routes', 'car', 'bolt', 'sparkle', 'passenger', 'shield', 'check', 'chevronRight', 'rosette'],
      assets: [],
    },
  });
  /* ========================================== end exemplar ================================================ */

  /* ==========================================================================================================
     WP-C shared helpers (local to this file; candidates for DP_UI, listed in the WP report)
     ========================================================================================================== */
  const U = window.DP_DATA.util;
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const cap1 = (s) => String(s || '').replace(/^./, (c) => c.toUpperCase());
  const still = (ctx, el) => !!(ctx.shot || window.DP.shot || ctx.motion.reduced(el));
  const tripOf = (ctx, fb) => ctx.data.trip(ctx.params.trip) || ctx.data.trip(fb);
  const evOf = (t, id) => (t.events || []).find((e) => e.id === id) || null;
  const tod = (ctx, t, sec) => ctx.data.time(new Date(new Date(t.start).getTime() + sec * 1000).toISOString());
  const plainLabel = (s) => String(s || '').replace('→', 'to').replace('&', 'and');
  /** Cumulative distance per track row (derived from lat/lng, scaled to trip.distanceKm). */
  const CUM = {};
  function cum(t) {
    if (CUM[t.id]) return CUM[t.id];
    const R = t.track.rows, km = new Array(R.length).fill(0);
    let d = 0, last = null;
    const hav = (a, b) => { const r = Math.PI / 180, dla = (b[1] - a[1]) * r, dln = (b[2] - a[2]) * r; const h = Math.sin(dla / 2) ** 2 + Math.cos(a[1] * r) * Math.cos(b[1] * r) * Math.sin(dln / 2) ** 2; return 12742 * Math.asin(Math.sqrt(h)); };
    R.forEach((r, i) => { if (r[1] != null) { if (last) d += hav(last, r); last = r; } km[i] = d; });
    const k = d ? t.distanceKm / d : 1;
    return (CUM[t.id] = { ts: R.map((r) => r[0]), km: km.map((x) => x * k) });
  }
  function kmAt(t, s) {
    const c = cum(t), ts = c.ts;
    if (s <= ts[0]) return 0;
    if (s >= ts[ts.length - 1]) return t.distanceKm;
    let lo = 0, hi = ts.length - 1;
    while (hi - lo > 1) { const m = (lo + hi) >> 1; if (ts[m] <= s) lo = m; else hi = m; }
    const f = (s - ts[lo]) / (ts[hi] - ts[lo] || 1);
    return c.km[lo] + (c.km[hi] - c.km[lo]) * f;
  }
  /** Which quality span the time falls in: 'estimated' | 'gap' | 'excluded' | null. */
  function spanAt(t, s) {
    const q = t.quality.spans;
    if (q.gap.some((g) => s >= g[0] && s <= g[1])) return 'gap';
    if (q.excluded.some((g) => s >= g[0] && s <= g[1])) return 'excluded';
    if (q.estimated.some((g) => s >= g[0] && s <= g[1])) return 'estimated';
    return null;
  }
  const SPAN_CHIP = { estimated: ['Estimated start', 'estimated'], gap: ['No GPS', 'gap'], excluded: ['Phone moved — not scored', 'excluded'] };
  /** The stack entry of a screen under the top layer (sheets refresh their parent after a change). */
  function underEntry(id) { const st = window.DP.router.stack; for (let i = st.length - 2; i >= 0; i--) if (st[i].id === id) return st[i]; return null; }
  function refreshUnder(id) { const e = underEntry(id); if (e && e.el) window.DP.router.refresh(e); }
  /** Map projection identical to DP_UI.map (same trackPath + insetTop shift). */
  function projector(t, w, h, pad, it = 0, ib = 0) { const pr = U.trackPath(t, w, h - it - ib, pad); return (la, ln) => { const [x, y] = pr.project(la, ln); return [x, y + it]; }; }
  /** Replay/Analysis marker (motion.md §3.6): white dot, Glide ring, 40° heading wedge. Absolutely positioned in px. */
  const markerHtml = (style) => `<span class="wc-mk" style="${style}" aria-hidden="true"><i class="wc-mk-h"></i><i class="wc-mk-w"></i><i class="wc-mk-d"></i></span>`;
  function markerPos(t, P, s) {
    const a = U.sampleAt(t, s);
    if (a.lat == null) return { gap: true, a };
    const [x, y] = P(a.lat, a.lng);
    const b = U.sampleAt(t, Math.min(t.durationS, s + 5)), c0 = U.sampleAt(t, Math.max(0, s - 5));
    let hd = 0;
    if (b.lat != null && c0.lat != null) { const [x1, y1] = P(c0.lat, c0.lng), [x2, y2] = P(b.lat, b.lng); hd = (Math.atan2(y2 - y1, x2 - x1) * 180) / Math.PI + 90; }
    return { x, y, hd, a };
  }
  const markerStyle = (m) => (m.gap ? 'opacity:0' : `transform:translate(${m.x.toFixed(1)}px,${m.y.toFixed(1)}px);--hd:${m.hd.toFixed(0)}deg`);
  function applyMarker(mk, m) { if (!mk) return; if (m.gap) { mk.style.opacity = '0'; return; } mk.style.opacity = '1'; mk.style.transform = `translate(${m.x.toFixed(1)}px,${m.y.toFixed(1)}px)`; mk.style.setProperty('--hd', `${m.hd.toFixed(0)}deg`); }
  /** Event-type wording from DP_ICONS (“Hard braking”) and short (“Braking”). */
  const evLabel = (ctx, ty) => ctx.icons.eventLabel(ty);
  const evShort = (ctx, ty) => cap1(ctx.icons.eventLabel(ty).replace('Hard ', ''));
  /** The tip for an event: this drive's tip when it is about this event, otherwise the library tip for its type (derived). */
  function tipFor(ctx, t, e) {
    if (!t.tip) return null;
    if (!e || t.tip.eventId === e.id) return { text: t.tip.text, mine: !!e, id: t.tip.id };
    const L = Object.fromEntries(ctx.D.tips.map((x) => [x.id, x.template]));
    const ev = ctx.data.vehicle(t.vehicleId)?.powertrain === 'ev';
    const place = e.place, road = e.road || e.place.replace(/^the /, '');
    let id;
    if (e.type === 'brake') id = ev ? 'regen' : /Gardiner|QEW|Lake Shore|Hwy/i.test(e.road || '') && !/&/.test(place) ? 'brake_traffic' : 'brake_light';
    else if (e.type === 'accel') id = /on-ramp/.test(place) ? 'accel_merge' : ev ? 'accel_ev' : 'accel_launch';
    else id = /&/.test(place) || /car park/.test(place) ? 'corner_entry' : 'corner_bend';
    const text = L[id].replace('{place}', id === 'accel_merge' ? place.replace(/ on-ramp$/, '') : place).replace('{road}', road.replace(/^the /, 'the '));
    return { text, mine: false, id };
  }
  /** Evidence trace with the Start/End labels dropped when they would collide with “Peak” (short events). */
  function traceHtml(ctx, e, w, h) {
    let svg = ctx.ui.evidenceTrace(e, w, h);
    if (!e.evidence) return svg;
    const m = e.evidence.markers, gap = Math.min(Math.abs(m.start), Math.abs(m.end)) / 20 * w;
    if (gap < 28) svg = svg.replace(/<text[^>]*>Start<\/text>/, '').replace(/<text[^>]*>End<\/text>/, '');
    return svg;
  }
  /** Friction circle (components.md §6) with filters, event markers, trail and dot. Fixed ±0.6 g. */
  const ggClass = (la, lo) => (Math.abs(la) > Math.abs(lo) ? 'corner' : lo < 0 ? 'brake' : 'accel');
  function ggSvg(ctx, t, s, o = {}) {
    const c = s / 2, R = s / 2 - (o.pad ?? 16), k = R / 0.6;
    const P = (la, lo) => { let x = la, y = lo; const m = Math.hypot(x, y); if (m > 0.6) { x *= 0.6 / m; y *= 0.6 / m; } return [c + x * k, c - y * k]; };
    const fs = (n) => `font:600 ${n}px var(--dp-family-text)`;
    let out = `<g class="gg-grid">`;
    for (let i = 1; i <= 6; i++) out += `<circle cx="${c}" cy="${c}" r="${((i / 10) * k).toFixed(1)}" fill="none" stroke="var(--dp-data-gg-ring)" stroke-width="${i === 3 || i === 5 ? 1.3 : 0.7}"/>`;
    out += `<line x1="${c - R}" x2="${c + R}" y1="${c}" y2="${c}" stroke="var(--dp-data-axis)"/><line y1="${c - R}" y2="${c + R}" x1="${c}" x2="${c}" stroke="var(--dp-data-axis)"/>`;
    const lab = (g) => { const r = g * k * 0.7071; return `<text x="${(c + r + 2).toFixed(1)}" y="${(c - r - 2).toFixed(1)}" fill="var(--dp-text-3)" style="font:600 ${o.full ? 10 : 9}px var(--dp-family-numeric);font-stretch:var(--dp-num-stretch)">${g.toFixed(1)} g</text>`; };
    (o.full ? [0.1, 0.2, 0.3, 0.4, 0.5] : [0.3, 0.5]).forEach((g) => (out += lab(g)));
    if (o.full) out += `<text x="${c}" y="11" text-anchor="middle" fill="var(--dp-text-2)" style="${fs(11)}">Accel ↑</text><text x="${c}" y="${s - 3}" text-anchor="middle" fill="var(--dp-text-2)" style="${fs(11)}">Brake ↓</text><text x="3" y="${c - 6}" fill="var(--dp-text-2)" style="${fs(11)}">← Left</text><text x="${s - 3}" y="${c - 6}" text-anchor="end" fill="var(--dp-text-2)" style="${fs(11)}">Right →</text>`;
    else out += `<text x="${c}" y="10" text-anchor="middle" fill="var(--dp-text-3)" style="${fs(9)};letter-spacing:.4px">ACCEL</text><text x="${c}" y="${s - 2}" text-anchor="middle" fill="var(--dp-text-3)" style="${fs(9)};letter-spacing:.4px">BRAKE</text>`;
    out += '</g>';
    if (!t.gg) return `<svg class="wc-gg" width="${s}" height="${s}" viewBox="0 0 ${s} ${s}" role="img" aria-label="No scorable motion">${out}<text x="${c}" y="${c - 10}" text-anchor="middle" fill="var(--dp-text-2)" style="${fs(12)}">No scorable motion</text></svg>`;
    let cloud = '';
    for (const [la, lo] of t.gg.points) { const [x, y] = P(la, lo); const on = !o.filter || ggClass(la, lo) === o.filter; cloud += `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${o.full ? 2 : 1.6}" fill="var(--dp-data-gg-cloud)" opacity="${on ? (o.full ? 0.38 : 0.32) : 0.06}"/>`; }
    let env = '';
    t.gg.envelope.radiiG.forEach((r, i) => { const a = ((i * 15 + 7.5) * Math.PI) / 180; env += `${i ? 'L' : 'M'}${(c + Math.cos(a) * r * k).toFixed(1)} ${(c - Math.sin(a) * r * k).toFixed(1)}`; });
    let evs = '';
    if (o.events) for (const e of t.events) { if (!e.evidence || (o.filter && o.filter !== e.type)) continue; const [x, y] = P(e.evidence.latG[50], e.evidence.lonG[50]); evs += `<g transform="translate(${(x - 8).toFixed(1)} ${(y - 8).toFixed(1)})">${ctx.icons.pin(e.type, { size: 16, needed: e.needed })}</g>`; }
    const trail = o.trail ? `<polyline class="gg-trail" points="${o.trail.map(([la, lo]) => P(la, lo).map((v) => v.toFixed(1)).join(',')).join(' ')}" fill="none" stroke="var(--dp-data-gg-dot)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" opacity=".55"/>` : '';
    const d = o.dot ? P(o.dot[0], o.dot[1]) : null;
    const dot = o.dot !== undefined ? `<g class="gg-dot" transform="translate(${d ? d[0].toFixed(1) : c} ${d ? d[1].toFixed(1) : c})" ${o.dot ? '' : 'opacity="0"'}><circle r="${o.full ? 11 : 8}" fill="var(--dp-data-gg-dot)" opacity=".22"/><circle r="${o.full ? 5.5 : 4.5}" fill="var(--dp-data-gg-dot)"/></g>` : '';
    return `<svg class="wc-gg" width="${s}" height="${s}" viewBox="0 0 ${s} ${s}" data-k="${k.toFixed(3)}" data-c="${c}" role="img" aria-label="${ctx.ui.esc(o.label || ggSummary(ctx, t))}">${out}<g class="gg-cloud">${cloud}</g><path class="gg-env" d="${env}Z" fill="none" stroke="var(--dp-data-gg-envelope)" stroke-width="1.6" stroke-linejoin="round"/>${evs}${trail}${dot}</svg>`;
  }
  /** "Most of your inputs stayed within 0.2 g. Peaks: …" (accessibility.md §3, derived from gg.points + events). */
  function ggSummary(ctx, t) {
    if (!t.gg) return 'Friction circle. No scorable motion on this drive.';
    const pts = t.gg.points, within = pts.filter(([a, b]) => Math.hypot(a, b) <= 0.2).length / pts.length;
    const peak = (ty) => t.events.filter((e) => e.type === ty).sort((a, b) => b.peakG - a.peakG)[0];
    const peaks = ['brake', 'accel', 'corner'].map((ty) => peak(ty)).filter(Boolean).map((e) => `${evShort(ctx, e.type).toLowerCase()} ${e.peakG.toFixed(2)} g at ${plainLabel(e.place)}`);
    return `Friction circle. ${Math.round(within * 100)}% of your inputs stayed within 0.2 g.${peaks.length ? ` Peaks: ${peaks.join(', ')}.` : ' No abrupt inputs.'}`;
  }
  /** Coverage strip: the whole drive as a bar with estimated (dashed), gap (broken) and excluded (hatched) spans. */
  function coverageStrip(ctx, t, o = {}) {
    const T = t.durationS, q = t.quality.spans, x = (s) => `${((s / T) * 100).toFixed(2)}%`;
    const seg = (cls, a, b, label) => `<i class="wc-cov-${cls}" style="left:${x(a)};width:max(4px, ${(((b - a) / T) * 100).toFixed(2)}%)" title="${ctx.ui.esc(label)}"></i>`;
    let bars = '';
    q.estimated.forEach((g) => (bars += seg('est', g[0], g[1], 'Estimated start')));
    q.gap.forEach((g) => (bars += seg('gap', g[0], g[1], 'No GPS')));
    q.excluded.forEach((g) => (bars += seg('exc', g[0], g[1], 'Phone moved — not scored')));
    if (o.calibrating) bars += seg('cal', 0, Math.min(T * 0.08, 40), 'Lining up');
    const aria = `Drive coverage, ${Math.round(T / 60)} minutes.${q.estimated.length ? ' Start estimated.' : ''}${q.gap.length ? ` ${Math.round(q.gap[0][1] - q.gap[0][0])} seconds without GPS.` : ''}${q.excluded.length ? ` ${t.quality.excludedMin} minutes left out.` : ''}`;
    return `<div class="wc-cov ${o.cls || ''}" role="img" aria-label="${ctx.ui.esc(aria)}"><div class="wc-cov-bar">${bars}</div><div class="wc-cov-ax"><span>0:00</span><span>${ctx.data.clock(T)}</span></div></div>`;
  }

  /* ==========================================================================================================
     S-STY-01 · Drive Story player (frames 1–7)                               F-STY-01/03/04/06 · F-SCO-05 · F-RTE-01/03
     Full-screen Instagram-style player. Every frame's copy comes from trip.story.frames (fixtures.md §3).
     ========================================================================================================== */
  const STY_STATES = [
    { id: 'default', label: 'Def (frame 1)' },
    { id: 'firstDrive', label: 'First drive (baseline)' },
    { id: 'calibrating', label: 'Calibrating (2 of 3)' },
    { id: 'routeDiscovered', label: 'Route discovered (merge + name)' },
    { id: 'newRouteRecord', label: 'New route record' },
    { id: 'degraded', label: 'Degraded (Part)' },
    { id: 'unscorable', label: 'Unscorable' },
    { id: 'wasItNeededInline', label: '“Was it needed?” inline' },
    { id: 'passengerChip', label: 'Passenger chip' },
    { id: 'transitQuestion', label: 'Transit question' },
    { id: 'Load', label: 'Load' },
    { id: 'f2Route', label: 'Frame 2 · route draw-on' },
    { id: 'f3Inputs', label: 'Frame 3 · G-dot replay' },
    { id: 'f4Ring', label: 'Frame 4 · Smoothness ring' },
    { id: 'f5Cleanest', label: 'Frame 5 · cleanest stretch' },
    { id: 'f6Tip', label: 'Frame 6 · one thing for next time' },
    { id: 'f7Actions', label: 'Frame 7 · actions' },
  ];
  const STY_PARAMS = {
    default: { trip: 't20261105-1745' }, firstDrive: { trip: 't20260928-1842' }, calibrating: { trip: 't20260929-0738' },
    routeDiscovered: { trip: 't20261002-0745' }, newRouteRecord: { trip: 't20261105-0742' }, degraded: { trip: 't20261027-1728' },
    unscorable: { trip: 't20261106-1210' }, wasItNeededInline: { trip: 't20261020-0740' }, passengerChip: { trip: 't20261031-1905' },
    transitQuestion: { trip: 't20261104-0650' }, Load: { trip: 't20261105-1745' },
    f2Route: { trip: 't20261105-1745' }, f3Inputs: { trip: 't20261105-1745' }, f4Ring: { trip: 't20261105-1745' }, f5Cleanest: { trip: 't20261105-1745' }, f6Tip: { trip: 't20261105-1745' }, f7Actions: { trip: 't20261105-1745' },
  };
  const STY_START = { firstDrive: 'f4', calibrating: 'f4', routeDiscovered: 'fRoute', newRouteRecord: 'f4', degraded: 'fDegraded', unscorable: 'fUnscorable', wasItNeededInline: 'fNeeded', f2Route: 'f2', f3Inputs: 'f3', f4Ring: 'f4', f5Cleanest: 'f5', f6Tip: 'f6', f7Actions: 'f7' };
  const STY_KIND = { f1: 'title', f2: 'route', f3: 'inputs', f4: 'ring', f5: 'cleanest', f6: 'tip', f7: 'end', fDegraded: 'degraded', fUnscorable: 'unscorable', fRoute: 'merge', fNeeded: 'needed' };
  /** Frame order: fixture key order, except Unscorable (1, 2, reason, 7: F-STY-04.2) and passenger (1, 2, 7: not your inputs). */
  function styFrames(t) {
    const keys = Object.keys(t.story.frames);
    if (t.story.variant === 'unscorable') return ['f1', 'f2', 'fUnscorable', 'f7'];
    if (t.type === 'passenger') return ['f1', 'f2', 'f7'];
    return keys;
  }
  const styDur = (ctx, k) => (k === 'f7' ? 0 : ['fRoute', 'fNeeded'].includes(k) || (k === 'f1' && ctx.state === 'transitQuestion') ? 6000 : ['f2', 'f3'].includes(k) ? 3500 : 3000) * (ctx.xl ? 1.5 : 1);

  function styCopy(o) {
    return `<div class="sty-copy">${o.kicker ? `<span class="sty-k">${o.kicker}</span>` : ''}${o.head ? `<h2 class="sty-h1">${o.head}</h2>` : ''}${o.body ? `<p class="sty-p">${o.body}</p>` : ''}${o.extra || ''}</div>`;
  }
  function styFrame(ctx, t, key) {
    const { ui, data, icons } = ctx, F = t.story.frames, f = F[key] || {}, esc = ui.esc, W = ctx.size.w, s = t.smoothness;
    const v = data.vehicle(t.vehicleId);
    switch (STY_KIND[key]) {
      case 'title': {
        const [pod, label] = f.title.split(' · ');
        const transit = ctx.state === 'transitQuestion' && !ctx.store.transitDone;
        const passenger = t.type === 'passenger';
        const chips = passenger
          ? ui.chip('', { kind: 'outline', cls: 'ui-glassy sty-pchip', html: `${ui.icon('passenger', 16)}Passenger · not scored<b>Change</b>`, nav: 'S-TRP-02/typePicker', params: { trip: t.id }, label: 'Marked as passenger, not scored. Change' })
          : `${v ? ui.carChip(v, { cls: 'ui-glassy', nav: 'S-TRP-02/carPicker', params: { trip: t.id }, label: `Car: ${v.nickname}. Change car` }) : ''}${ui.chip('I was a passenger', { icon: 'passenger', cls: 'ui-glassy', nav: 'S-TRP-02/typePicker', params: { trip: t.id }, label: 'I was a passenger' })}`;
        const q = transit ? `<div class="sty-q ui-glassy" role="group" aria-label="Was this a bus or train?"><b>Was this a bus or train?</b><span>It stopped and started like transit. If it was, we’ll leave it out of your scores.</span><div class="sty-chips">${['Bus', 'Train', 'Streetcar'].map((x) => ui.chip(x, { cls: 'ui-glassy', action: 'transit', arg: x })).join('')}${ui.chip('No, I drove', { kind: 'up', action: 'transit', arg: 'drove' })}</div></div>` : '';
        return {
          art: `<div class="sty-glyph">${ui.glyph(t.glyph, Math.min(250, W - 140), { width: 5, color: 'var(--dp-accent)' })}</div>`,
          copy: styCopy({ kicker: `<i></i>Drive decoded`, head: esc(pod), body: esc(label), extra: `${q}<div class="sty-chips">${chips}</div>${passenger ? `<p class="sty-note">Not scored, and it doesn’t count toward your baseline, routes or recaps.</p>` : ''}` }),
          aria: `${f.title}. ${v ? v.nickname : ''}.${passenger ? ' Marked as passenger, not scored.' : ''}${transit ? ' Was this a bus or train?' : ''}`,
        };
      }
      case 'route': {
        const mh = Math.round(Math.min(380, ctx.size.h * 0.43));
        return {
          art: ui.map(t, { w: W - 40, h: mh, pad: 34, pins: false, radius: 28, cls: 'sty-map', label: `Route of your drive, ${t.distanceKm} kilometres from ${t.from} to ${t.to}.` }),
          copy: styCopy({ kicker: 'The drive', head: `<span class="sty-big"><b class="ui-num" data-count="${f.distanceKm}" data-dec="1">${data.km(f.distanceKm)}</b><u>km</u></span><span class="sty-big"><b class="ui-num" data-count="${f.durationMin}">${f.durationMin}</b><u>min</u></span>` }),
          aria: `${f.line.replace('km', 'kilometres').replace('min', 'minutes')}.`,
        };
      }
      case 'inputs': {
        const e0 = t.events[0], at = e0 ? e0.t : t.durationS * 0.4;
        const trail = []; for (let k = 12; k >= 0; k--) { const a = U.sampleAt(t, at - k * 1.6); trail.push([a.latG || 0, a.lonG || 0]); }
        const a = U.sampleAt(t, at), gs = Math.min(260, W - 120);
        const P = projector(t, W - 40, 112, 14);
        const m = markerPos(t, P, at);
        const cnt = (ty) => t.events.filter((e) => e.type === ty).length;
        const firm = [['brake', 'firm stop'], ['accel', 'sharp start'], ['corner', 'hard corner']].filter(([ty]) => cnt(ty)).map(([ty, w]) => `${cnt(ty)} ${w}${cnt(ty) > 1 ? 's' : ''}`);
        return {
          art: `<div class="sty-gg"><div class="sty-gg-c">${ggSvg(ctx, t, gs, { dot: [a.latG || 0, a.lonG || 0], trail, label: 'G-dot replay' })}</div>${ui.map(t, { w: W - 40, h: 112, pad: 14, pins: false, stops: false, maxPin: false, others: false, radius: 20, attribution: false, cls: 'sty-mini', overlay: markerHtml(markerStyle(m)), label: 'Tracking your position along the route' })}</div>`,
          copy: styCopy({ kicker: 'Your inputs', head: esc(f.line), body: esc(f.sub) }),
          aria: `${f.line} ${firm.length ? `Mostly gentle, with ${firm.join(' and ')}.` : 'Gentle all the way.'}`,
        };
      }
      case 'ring': {
        const rec = s.record && s.record.isNew ? s.record : null;
        const route = t.routeId && data.route(t.routeId);
        const chips = rec || s.calibrating ? [ui.deltaChip(s, { record: false })] : [];
        if (rec) chips.unshift(ui.chip('New route record', { kind: 'record', icon: icons.svg('rosette', { size: 14 }), nav: 'S-RTE-02', params: { route: t.routeId }, label: `New route record on ${route ? route.name : 'this route'}. Opens the route` }));
        const based = f.basedOn ? `<span class="sty-based">${icons.quality(t.quality.badge, { size: 14 })}${esc(f.basedOn)}</span>` : '';
        const aria = `${f.line}. ${s.delta ? `${Math.abs(s.delta)} ${s.delta > 0 ? 'above' : 'below'} your usual${s.basis === 'route' ? ` on ${s.routeName}` : ''}.` : esc(f.compare)}${rec ? ` New route record, beating ${rec.previous.score} from ${data.date(rec.previous.date, { day: 'numeric', month: 'long' })}.` : ''}${f.basedOn ? ' ' + f.basedOn + '.' : ''}`;
        return {
          art: `<div class="sty-ring"><span class="ui-press" ${ui.act({ nav: 'S-SCO-01', params: { trip: t.id }, label: `${aria} Opens the Smoothness breakdown`, noRole: false })}>${ui.ring(s.score, Math.min(224, W - 150), { base: s.calibrating ? null : s.baseline, rec: rec ? rec.previous.score : null, recNew: !!rec, stroke: 15, aria })}</span><div class="sty-chips sty-ring-chips">${chips.join('')}</div>${based}</div>`,
          copy: styCopy({ kicker: s.calibrating && s.calibrating.k === 1 ? 'Your first score' : esc(s.basis === 'route' ? s.routeName : 'This drive'), head: esc(rec ? 'New route record.' : f.line), body: rec ? esc(f.record.replace(/^New route record\. /, '')) : s.calibrating ? (s.calibrating.k === 1 ? esc(f.compare) : 'We compare each drive with your own usual once we’ve seen three.') : `${ui.deltaChip(s, { record: false })}` }),
          aria,
        };
      }
      case 'cleanest': {
        const mh = Math.round(Math.min(360, ctx.size.h * 0.42)), mw = W - 40;
        const P = projector(t, mw, mh, 34);
        const pts = t.track.rows.filter((r) => r[0] >= t.cleanest.t0 && r[0] <= t.cleanest.t1 && r[1] != null).map((r) => P(r[1], r[2]).map((x) => x.toFixed(1)).join(' '));
        const ov = `<svg class="sty-clean" width="${mw}" height="${mh}" viewBox="0 0 ${mw} ${mh}" aria-hidden="true"><path d="M${pts.join('L')}" fill="none" stroke="var(--dp-accent)" stroke-width="14" stroke-linecap="round" stroke-linejoin="round" opacity=".22"/><path class="sty-clean-l" d="M${pts.join('L')}" fill="none" stroke="var(--dp-ref-glide-200)" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
        const [k, rest] = f.line.split(': ');
        return { art: ui.map(t, { w: mw, h: mh, pad: 34, pins: false, dim: true, radius: 28, overlay: ov, cls: 'sty-map', label: `Map highlighting ${rest}` }), copy: styCopy({ kicker: `${ui.icon('sparkle', 14)}${esc(k)}`, head: esc(cap1(rest)) }), aria: f.line };
      }
      case 'tip': {
        const e = t.tip.eventId ? evOf(t, t.tip.eventId) : null;
        const rest = cap1(f.line.replace(/^One thing for next time: /, ''));
        const art = e && e.evidence
          ? `<div class="sty-trace ui-glassy"><div class="sty-trace-h">${icons.pin(e.type, { size: 22 })}<b>${esc(evLabel(ctx, e.type))}</b><span>${esc(cap1(e.place))}</span></div>${traceHtml(ctx, e, W - 76, 128)}<div class="sty-trace-l"><span><i class="is-sp"></i>Speed</span><span><i class="is-lo"></i>Braking and acceleration</span></div></div>`
          : `<div class="sty-tipart">${icons.svg('pinStop', { size: 44 })}<span>${esc(t.tip.place)}</span></div>`;
        return {
          art,
          copy: styCopy({ kicker: 'One thing for next time', head: esc(rest), extra: `<div class="sty-chips">${e ? ui.chip('See the moment', { cls: 'ui-glassy', icon: 'replay', nav: 'S-RPL-02/thisDrivesTip', params: { trip: t.id, event: e.id } }) : ''}${ui.chip('More on this tip', { cls: 'ui-glassy', icon: 'info', nav: 'S-SCO-04', params: { trip: t.id } })}</div>` }),
          aria: f.line,
        };
      }
      case 'degraded':
        return {
          art: `<div class="sty-deg ui-glassy">${coverageStrip(ctx, t, { cls: 'is-story' })}<div class="sty-deg-l"><span><i class="wc-sw is-exc"></i>Left out · ${t.quality.excludedMin} min</span><span><i class="wc-sw is-est"></i>Estimated start</span></div></div>`,
          copy: styCopy({ kicker: 'About this drive', head: esc(f.line), extra: `<div class="sty-chips">${ui.qualityBadge(t.quality.badge, { text: `${t.quality.badge} data`, glass: true, nav: 'S-DQ-01/limited', params: { trip: t.id } })}</div>` }),
          aria: f.line,
        };
      case 'unscorable':
        return {
          art: `<div class="sty-ring">${ui.ring(null, Math.min(210, W - 160), { unscorable: true, aria: 'Not scored' })}</div>`,
          copy: styCopy({ kicker: 'Not scored', head: esc(f.line), extra: `<div class="sty-chips">${ui.qualityBadge('Unscorable', { text: 'Why not?', glass: true, nav: 'S-DQ-01/unscorable', params: { trip: t.id } })}${ui.chip('Where to put your phone', { cls: 'ui-glassy', icon: 'phone', nav: 'S-DQ-02/fromLimitedTrip', params: { trip: t.id } })}</div>` }),
          aria: f.line,
        };
      case 'merge': {
        const [a, b] = f.line.split('. ');
        const r = data.route(t.routeId);
        const chips = f.chips.map((c) => ui.chip(c, { cls: 'ui-glassy', nav: c.startsWith('Custom') ? 'S-RTE-03/custom' : 'S-RTE-03', params: { route: t.routeId, name: c.startsWith('Custom') ? '' : c }, label: `Name it ${c.replace('…', '')}` })).join('');
        return {
          art: `<svg class="sty-merge" width="${W - 60}" height="${Math.round((W - 60) * 0.62)}" viewBox="0 0 300 186" aria-hidden="true"><path class="sty-mg-a" d="M14 150C70 150 84 58 150 58s76 74 136 74" fill="none" stroke="var(--dp-accent)" stroke-width="6" stroke-linecap="round" opacity=".45"/><path class="sty-mg-b" d="M14 170C82 166 94 76 154 76s70 56 132 56" fill="none" stroke="var(--dp-accent)" stroke-width="6" stroke-linecap="round"/><circle cx="286" cy="132" r="9" fill="var(--dp-accent)"/><circle cx="14" cy="160" r="7" fill="none" stroke="var(--dp-accent)" stroke-width="3"/></svg>`,
          copy: styCopy({ kicker: `${ui.icon('routes', 14)}${data.trips((x) => x.routeId && x.routeId === t.routeId && x.start <= t.start).length || 2} drives, one route`, head: esc(a + '.'), body: esc(b), extra: `<div class="sty-chips">${chips}</div>` }),
          aria: `${f.line} Options: ${f.chips.join(', ')}.`,
        };
      }
      case 'needed': {
        const e = evOf(t, f.eventId);
        const [a, b] = f.line.split('. ');
        const done = ctx.store.needed;
        const body = done
          ? `<div class="sty-done ui-glassy">${ui.icon('check', 18)}<span><b>Marked as needed. It doesn’t count.</b>Your Smoothness is ${s.score} without it.</span></div>`
          : `<div class="sty-btns">${ui.button(f.answer, { kind: 'filled', size: 'm', action: 'needed', haptic: 'tap' })}${ui.button('No', { kind: 'glass', size: 'm', action: 'next' })}</div>${ui.chip('Not sure? See the evidence', { cls: 'ui-glassy', icon: 'info', nav: 'S-SCO-03', params: { trip: t.id, event: e.id } })}`;
        return {
          art: `<div class="sty-trace ui-glassy"><div class="sty-trace-h">${icons.pin(e.type, { size: 22, needed: done })}<b>${esc(evLabel(ctx, e.type))}</b><span>${esc(e.place)} · ${e.peakG.toFixed(2)} g</span></div>${traceHtml(ctx, e, W - 76, 128)}</div>`,
          copy: styCopy({ kicker: 'Quick question', head: esc(a + '.'), body: esc(b), extra: body }),
          aria: `${f.line}${done ? ' Marked as needed.' : ''}`,
        };
      }
      case 'end': {
        const first = t.story.variant === 'firstDrive';
        const passenger = t.type === 'passenger';
        const ring = s ? `<span class="sty-end-r">${ui.ring(s.score, 64, { label: false, stroke: 6, aria: `Smoothness ${s.score}` })}</span>` : t.smoothness === null && !passenger ? `<span class="sty-end-r">${ui.ring(null, 64, { unscorable: true, label: false, stroke: 6 })}</span>` : '';
        const card = `<div class="sty-end ui-glassy"><span class="sty-end-g">${ui.glyph(t.glyph, 52, { width: 3 })}</span><span class="sty-end-t"><b>${esc(t.label)}</b><span>${esc(t.partOfDay)}${v ? ` · ${esc(v.nickname)}` : ''}</span><span>${ui.num(data.km(t.distanceKm), 'km')} · ${ui.num(Math.round(t.durationS / 60), 'min')}</span></span>${ring}</div>`;
        const loop = first ? `<div class="sty-loop ui-glassy">${icons.loop({ size: 26, color: 'var(--dp-accent)', dot: 'var(--dp-text)' })}<span><b>That’s the loop.</b>Every drive gets a Story. Explore it in Replay, and repeat trips become Routes.</span></div>` : '';
        return {
          art: `<div class="sty-endart">${card}${ui.qualityBadge(t.quality.badge, { text: f.quality, glass: true, nav: 'S-DQ-01', params: { trip: t.id }, label: `${f.quality}. Opens data quality details` })}${loop}</div>`,
          copy: `<div class="sty-acts">${ui.button(f.actions[0], { kind: 'filled', size: 'l', block: true, icon: 'replay', nav: 'S-RPL-01', params: { trip: t.id } })}<div class="sty-acts-2">${passenger ? '' : ui.button(f.actions[1], { kind: 'glass', size: 'l', block: true, icon: 'share', nav: 'S-SHR-01', params: { trip: t.id } })}${ui.button(f.actions[2], { kind: 'glass', size: 'l', block: true, back: true })}</div></div>`,
          aria: `${t.label}, ${t.partOfDay}. ${f.quality}. Actions: ${f.actions.join(', ')}.`,
        };
      }
      default: return { art: '', copy: '', aria: '' };
    }
  }

  screen('S-STY-01', {
    name: 'Drive Story player (frames 1–7)',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'full', tab: 'drives',
    states: STY_STATES, stateParams: STY_PARAMS,
    stateAliases: { newRecord: 'newRouteRecord', record: 'newRouteRecord', limited: 'degraded', passenger: 'passengerChip', transit: 'transitQuestion' },
    statusBar: 'light',
    backTo: 'S-HOME-01',
    render(ctx) {
      const { ui, data, icons } = ctx;
      const t = tripOf(ctx, 't20261105-1745');
      const car = data.vehicle(t.vehicleId);
      const wash = `--car: var(--dp-car-${(car && car.colour) || 'glide'})`;
      const top = (segs) => `<div class="sty-top"><div class="sty-seg" aria-hidden="true">${segs}</div><div class="sty-hd">${icons.loop({ size: 22, color: 'var(--dp-accent)', dot: 'var(--dp-text)' })}<b>Drive Story</b><span>${ui.esc(t.partOfDay)}</span>${ui.iconButton('close', { back: true, label: 'Close Story', glass: true })}</div></div>`;
      if (ctx.state === 'Load') {
        return `<div class="ui-page sty is-load" data-theme="dark" style="${wash}"><div class="sty-bg"></div>${top('<i></i>'.repeat(7))}<div class="sty-loading" role="status" aria-label="Decoding your drive. Building the replay.">${ui.loader({ size: 64, label: 'Decoding your drive' })}<b>Decoding your drive…</b><span>Building the replay · ${ui.num(data.km(t.distanceKm), 'km')} · ${ui.num(Math.round(t.durationS / 60), 'min')}</span><div class="sty-load-sk">${ui.skeleton('lines', 3)}</div></div></div>`;
      }
      const keys = styFrames(t);
      const want = ctx.params.frame || STY_START[ctx.state] || 'f1';
      let cur = ctx.store.frame ?? Math.max(0, keys.indexOf(want));
      if (cur >= keys.length) cur = 0;
      const segs = keys.map((k, i) => `<i class="${i < cur ? 'is-done' : i === cur ? 'is-cur' : ''}"><b></b></i>`).join('');
      const frames = keys.map((k, i) => { const fr = styFrame(ctx, t, k); return `<section class="sty-f ${i === cur ? 'is-on' : ''}" data-k="${k}" data-kind="${STY_KIND[k]}" tabindex="${i === cur ? 0 : -1}" aria-roledescription="Story frame" aria-label="${ui.esc(`Frame ${i + 1} of ${keys.length}. ${fr.aria}`)}" ${i === cur ? '' : 'aria-hidden="true"'}><div class="sty-art">${fr.art}</div>${fr.copy}</section>`; }).join('');
      return `<div class="ui-page sty" data-theme="dark" data-cur="${cur}" style="${wash}"><div class="sty-bg"></div><div class="sty-zones"><span class="sty-z is-prev" ${ui.act({ action: 'prev', label: 'Previous frame' })}></span><span class="sty-z is-next" ${ui.act({ action: 'next', label: 'Next frame' })}></span></div><div class="sty-frames">${frames}</div>${top(segs)}</div>`;
    },
    mount(el, ctx) {
      const root = el.querySelector('.sty');
      const S = ctx.store, M = ctx.motion, ui = ctx.ui;
      if (!root) return;
      if (root.classList.contains('is-load')) {
        if (still(ctx, root)) return;
        const tm = setTimeout(() => ctx.setState('default'), 2600);
        return () => clearTimeout(tm);
      }
      const t = tripOf(ctx, 't20261105-1745');
      const frames = [...root.querySelectorAll('.sty-f')], segs = [...root.querySelectorAll('.sty-seg > i')];
      let cur = +root.dataset.cur, seg = null, paused = false, held = false, raf = 0, timers = [];
      const reduced = ctx.motion.reduced(root), shot = !!(ctx.shot || window.DP.shot);
      const clear = () => { timers.forEach(clearTimeout); timers = []; cancelAnimationFrame(raf); raf = 0; if (seg) { seg.cancel(); seg = null; } };
      const later = (fn, ms) => timers.push(setTimeout(fn, ms));
      function hero(f) {
        const kind = f.dataset.kind;
        if (shot) return;
        ui.anim.rise(f, '.sty-copy > *, .sty-acts > *', { stagger: 60 });
        if (reduced) { f.querySelectorAll('.sty-art').forEach((a) => a.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 220 })); return; }
        if (kind === 'title') { const p = f.querySelector('.ui-glyph path'); if (p) M.drawOn(p, { duration: 1100 }); }
        if (kind === 'route') { ui.anim.maps(f); f.querySelectorAll('[data-count]').forEach((n, i) => { const to = +n.dataset.count, dec = +(n.dataset.dec || 0); timers.push(setTimeout(() => M.countUp(n, to, { duration: 1200, format: (x) => x.toFixed(dec) }), 300 + i * 60)); n.textContent = (0).toFixed(dec); }); }
        if (kind === 'ring' || kind === 'unscorable') { ui.anim.rings(f); f.querySelectorAll('.sty-ring-chips > *, .sty-based').forEach((c, i) => M.animate(c, [{ opacity: 0, transform: 'scale(.86)' }, { opacity: 1, transform: 'scale(1)' }], { preset: 'default', delay: 1600 + i * 40 })); }
        if (kind === 'cleanest') { const p = f.querySelector('.sty-clean-l'); if (p) M.drawOn(p, { duration: 1200, delay: 250 }); }
        if (kind === 'tip' || kind === 'needed') f.querySelectorAll('.ui-trace path').forEach((p) => M.drawOn(p, { duration: 1100, delay: 200 }));
        if (kind === 'merge') { const a = f.querySelector('.sty-mg-a'), b = f.querySelector('.sty-mg-b'); if (a) { M.animate(a, [{ transform: 'translateY(-14px)' }, { transform: 'translateY(0)' }], { preset: 'default', delay: 200 }); M.animate(b, [{ transform: 'translateY(14px)' }, { transform: 'translateY(0)' }], { preset: 'default', delay: 200 }); } f.querySelectorAll('.sty-chips > *').forEach((c, i) => M.animate(c, [{ opacity: 0, transform: 'scale(.86)' }, { opacity: 1, transform: 'scale(1)' }], { preset: 'fast', delay: 700 + i * 40 })); }
        if (kind === 'inputs') gReplay(f);
      }
      /** Story frame 3 (motion.md §3.5): the whole drive in 3 s, gMeter follower with a 1.2 s tapering trail. */
      function gReplay(f) {
        const svg = f.querySelector('.wc-gg'); if (!svg || !t.gg) return;
        const k = +svg.dataset.k, c = +svg.dataset.c;
        const dot = svg.querySelector('.gg-dot'), trail = svg.querySelector('.gg-trail'), cloud = svg.querySelector('.gg-cloud'), env = svg.querySelector('.gg-env');
        const mk = f.querySelector('.wc-mk'), mini = f.querySelector('.sty-mini');
        const W = ctx.size.w - 40, P = projector(t, W, 112, 14);
        cloud.style.opacity = 0; env.style.opacity = 0;
        const fol = M.follower({ zeta: 0.55, k: 120 }); fol.jump(0, 0);
        const hist = []; let t0 = null, last = null;
        const dur = 3000 * (ctx.xl ? 1.5 : 1);
        const P2 = (la, lo) => { let x = la, y = lo; const m = Math.hypot(x, y); if (m > 0.6) { x *= 0.6 / m; y *= 0.6 / m; } return [c + x * k, c - y * k]; };
        const step = (now) => {
          if (paused) { last = now; raf = requestAnimationFrame(step); return; }
          t0 ??= now; const dt = last ? (now - last) / 1000 : 0; last = now;
          const p = Math.min(1, (now - t0) / dur), s = p * t.durationS, a = U.sampleAt(t, s);
          fol.target(a.latG || 0, a.lonG || 0); const st = fol.step(dt);
          hist.push([now, st.x, st.y]); while (hist.length && now - hist[0][0] > 1200) hist.shift();
          const [x, y] = P2(st.x, st.y); dot.setAttribute('transform', `translate(${x.toFixed(1)} ${y.toFixed(1)})`); dot.setAttribute('opacity', 1);
          trail.setAttribute('points', hist.map((h) => P2(h[1], h[2]).map((v) => v.toFixed(1)).join(',')).join(' '));
          if (mk && mini) applyMarker(mk, markerPos(t, P, s));
          if (p < 1) raf = requestAnimationFrame(step);
          else { cloud.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 400, fill: 'forwards' }); env.style.opacity = 1; M.drawOn(env, { duration: 400 }); }
        };
        raf = requestAnimationFrame(step);
      }
      function show(i) {
        clear();
        cur = clamp(i, 0, frames.length - 1); S.frame = cur;
        frames.forEach((f, j) => { f.classList.toggle('is-on', j === cur); f.tabIndex = j === cur ? 0 : -1; if (j === cur) f.removeAttribute('aria-hidden'); else f.setAttribute('aria-hidden', 'true'); });
        segs.forEach((s, j) => { s.className = j < cur ? 'is-done' : j === cur ? 'is-cur' : ''; s.firstChild.style.transform = ''; });
        const f = frames[cur];
        hero(f);
        const d = styDur(ctx, f.dataset.k);
        if (shot || !d) { if (!d && segs[cur]) segs[cur].className = 'is-done'; return; }
        const b = segs[cur].firstChild;
        seg = b.animate([{ transform: 'scaleX(0)' }, { transform: 'scaleX(1)' }], { duration: d, easing: 'linear', fill: 'forwards' });
        seg.onfinish = () => next();
        if (paused) seg.pause();
      }
      const next = () => { if (cur < frames.length - 1) show(cur + 1); };
      const prev = () => show(cur > 0 ? cur - 1 : 0);
      const pause = () => { paused = true; if (seg) seg.pause(); root.classList.add('is-paused'); };
      const resume = () => { paused = false; if (seg) seg.play(); root.classList.remove('is-paused'); };
      el._sty = { next, prev, pause, resume, held: () => { const h = held; held = false; return h; } };
      // press-and-hold pauses (F-STY-01.2); swipe down exits (motion.md §3.8)
      let holdT = 0, y0 = null, dy = 0;
      const down = (e) => { if (e.target.closest('.ui-press:not(.sty-z), .ui-chip, .ui-btn')) return; y0 = e.clientY; dy = 0; holdT = setTimeout(() => { held = true; pause(); }, 240); };
      const move = (e) => { if (y0 == null) return; dy = Math.max(0, e.clientY - y0); if (dy > 8) { clearTimeout(holdT); root.style.transform = `translateY(${dy * 0.5}px) scale(${1 - Math.min(dy, 300) / 1500})`; root.style.borderRadius = `${Math.min(40, dy / 4)}px`; } };
      const up = () => { clearTimeout(holdT); if (y0 == null) return; y0 = null; if (dy > 120) { ctx.haptic('gestureEnd'); ctx.back(); return; } if (dy > 8) { held = true; root.animate([{ transform: root.style.transform }, { transform: 'none' }], { duration: 300, easing: 'cubic-bezier(.2,0,0,1)' }); root.style.transform = ''; root.style.borderRadius = ''; } if (held && dy <= 8) resume(); };
      root.addEventListener('pointerdown', down); window.addEventListener('pointermove', move); window.addEventListener('pointerup', up);
      // screen reader focus pauses auto-advance (F-STY-06.1)
      const fin = (e) => { if (e.target.closest('.sty-f')) pause(); }, fout = () => resume();
      root.addEventListener('focusin', fin); root.addEventListener('focusout', fout);
      show(cur);
      return () => { clear(); clearTimeout(holdT); root.removeEventListener('pointerdown', down); window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); delete el._sty; };
    },
    actions: {
      next: (ctx) => { const s = ctx.entry.el._sty; if (s && !s.held()) s.next(); return false; },
      prev: (ctx) => { const s = ctx.entry.el._sty; if (s && !s.held()) s.prev(); return false; },
      needed: (ctx) => { ctx.store.needed = true; ctx.toast('Marked as needed · Smoothness recomputed', { icon: 'check', action: 'Undo' }); ctx.update(); return false; },
      transit: (ctx, arg) => {
        if (arg === 'drove') { ctx.store.transitDone = true; ctx.toast('Thanks. It stays a drive.', { icon: 'check' }); ctx.update(); return false; }
        ctx.toast(`Moved to Not drives (${arg.toLowerCase()}). It won’t count toward your scores.`, { icon: 'check', action: 'Undo' }); ctx.back(); return false;
      },
    },
    spec: {
      purpose: 'The “wow” after every drive (F-STY-01): a ~15 s full-screen Story that leads with what went well, scores the drive against the user’s own baseline, then gives exactly one tip. No speed or max speed appears in any frame (F-STY-01.3).',
      layout: [
        'Full-screen player, always on the night stage (`--dp-gradient-night-sky`) with a car-colour wash (radial 30% of `--dp-car-{colour}` top-trailing, 14% Glide bottom-leading). Status bar light. Dark tokens are forced inside the player in both themes.',
        'Top (safe-top + 4): progress segments (3 px, gap 4, white 26% track, white fill) · header row: Loop mark 22 + “Drive Story” (14/600) + part of day (meta, text2) + glass close button (44).',
        'Frame stage: art region (flex, centred) above a bottom-left copy block: kicker (13/600, accent-text) · `storyHeadline` (28/34 iOS bold, 28/36 Android) · `storyBody` (20/25, text2) · chip row. Bottom padding safe-bottom + 24.',
        'f1 title: route glyph 250 (share-safe trip.glyph, Glide 5 px) · kicker “● Drive decoded” · part of day (headline) · route label (body) · car chip + “I was a passenger” chip (glass).',
        'f2 route: plain-canvas map, full width − 40, ≤ 380 tall, radius 28, estimated start dashed, “© OpenStreetMap contributors” · two DP Numerals 48 (“39.0 km” “33 min”) counting up.',
        'f3 inputs: friction circle 260 (±0.6 g, rings at 0.1 g, 0.3/0.5 labelled) with the G-dot and a 1.2 s trail; under it a 112-tall mini map with the tracking tick (marker).',
        'f4 ring: Smoothness ring 224 (stroke 15, baseline notch, gold record notch) · comparison chip (and gold “New route record” chip) · “Score based on 31 of 34 min” with the quality glyph for Limited trips. Tap the ring → S-SCO-01.',
        'f5 cleanest stretch: the map with the route dimmed to 45% and the cleanest span drawn in Glide 200 with a 14 px glow · kicker “✦ Cleanest stretch”.',
        'f6 tip: glass trace card (event pin 22 + type + place, the −10…+10 s evidence trace, legend) · kicker “One thing for next time” · tip as headline · chips “See the moment” → S-RPL-02, “More on this tip” → S-SCO-04.',
        'Variant frames: fDegraded (coverage strip with the hatched excluded span) · fUnscorable (dashed ring “—”, Why not? → S-DQ-01) · fRoute (two Glide lines merging + chips Commute/Gym/School run/Custom… → S-RTE-03) · fNeeded (evidence trace + “Yes, it was needed” / “No”, inline confirmation).',
        'f7 end: glass summary card (glyph 52 · label · part of day · car · km · min · ring 64) · full quality badge (“Good data · phone mounted”) · first-drive loop card · actions: Explore the drive (filled large, block) · Share + Done (glass large, side by side). Passenger trips drop Share.',
        'Load: Loop loader 64 (iOS) / M3E shape loader (Android) + “Decoding your drive…” + skeleton lines; segments empty.',
      ],
      components: ['ring', 'deltaChip', 'chip', 'carChip', 'glyph', 'map', 'evidenceTrace', 'qualityBadge', 'button', 'iconButton', 'loader', 'skeleton', 'gg (local ggSvg)', 'coverageStrip (local)'],
      interactions: [
        'Tap the right 68% → next frame; tap the left 32% → previous; press and hold → pause (segment freezes); swipe down → exit (content scales to 0.9 and rounds its corners with the drag, commits past 120 pt with haptic gestureEnd); close button → back to the caller.',
        'f1: car chip → S-TRP-02/carPicker · “I was a passenger” → S-TRP-02/typePicker (≤ 2 taps, F-HIS-02.4) · transit question chips Bus/Train/Streetcar → moved to Not drives (toast + Undo, back) · “No, I drove” → dismisses the question.',
        'f4: ring → S-SCO-01 · “New route record” chip → S-RTE-02 · f6: “See the moment” → S-RPL-02/thisDrivesTip · “More on this tip” → S-SCO-04 · f5/fDegraded/fUnscorable badges → S-DQ-01 · placement chip → S-DQ-02.',
        'fRoute chips → S-RTE-03 (preset name) or S-RTE-03/custom · fNeeded “Yes, it was needed” → marks it inline, the score recomputes ≤ 1 s and the Story continues (F-STY-04.3); “No” → next frame; “Not sure?” → S-SCO-03.',
        'f7: Explore the drive → S-RPL-01 · Share → S-SHR-01 · Done → back. Quality badge → S-DQ-01. No paywall, share prompt or rating prompt ever follows a Story (D12, F-SUB-08).',
        'D1 gate: opened while moving or during a driver trip → S-DRV-05 (“Your Story will be ready when you’ve parked.”), which opens the Story once stationary for 60 s.',
        'Deep link a frame: `#S-STY-01?trip=…&frame=f4`.',
      ],
      motion: [
        'Segments fill linearly over the frame duration (3 s; 3.5 s for f2/f3; 6 s for question frames; ×1.5 at XL); f7 waits.',
        'Frame change: outgoing frame fades (220 ms); incoming copy rises 12 px line by line (60 ms stagger, spring.default) while the frame’s hero runs.',
        'Heroes: f1 glyph draw-on 1100 ms · f2 route draw-on 1800 ms (ease.drawOn) + numbers count up 1200 ms (ease.countUp) · f3 G-dot replay 3000 ms (shared.gMeter ζ 0.55 k 120, 1.2 s tapering trail, cloud fades in and the envelope draws on at the end) · f4 ring reveal 1400 ms with baseline notch, record notch flip + doubleSuccess haptic, chip pop at 1600 ms · f5 cleanest-stretch draw-on · f6 trace draw-on · fRoute lines slide 14 px together (spring.default) then chips pop (spring.fast, 40 ms stagger).',
        'RM: 220 ms cross-fades; route, ring and numbers appear complete; f3 shows the static G-G plot with the trail (F-STY-06.2); auto-advance continues (tap and hold still pause). Haptics kept except ring ticks.',
      ],
      a11y: [
        'Each frame is one focusable element whose label is the full frame copy, e.g. “Frame 4 of 7. Smoothness 85. 2 above your usual on The Commute home.” Focus pauses auto-advance (F-STY-06.1); actions Next frame / Previous frame / Close.',
        'f3 summary: “Your inputs, replayed. Mostly gentle, with 2 firm stops.” (derived from the trip’s events).',
        'Focus is trapped inside the player until closed. Tap zones are buttons “Previous frame” / “Next frame”.',
        'XL: the copy block grows upward and the art shrinks (zoom 0.62); frames run 1.5× longer; nothing truncates.',
        'Never colour alone: ▲/▼/● on chips, rosette on records, hatched + dashed spans in the coverage strip, glyphs on quality badges.',
      ],
      platformDiffs: {
        ios: 'fullScreenCover; glass chips and glass close button (`.glassEffect(.clear)` over art with a 35% dim); SF Pro Display Bold story headline; Loop loader for Load; buttons glassProminent (Explore) and glass (Share/Done).',
        android: 'Full-screen dialog destination with fade-rise; tonal surface-container-high chips (no blur); Google Sans Flex story styles (28/36 600, body 22/28); ring sits on the Cookie9Sided primaryContainer shape that rotates during the count; M3E loading indicator for Load; Explore = filled button with shape morph, Share/Done = tonal.',
      },
      analytics: ['story_opened {source}', 'story_frame_viewed {frame}', 'story_completed', 'story_action {action: explore|share|done}', 'story_variant {degraded|unscorable}', 'event_marked_needed {type, source: story}', 'trip_flagged_transit', 'trip_corrected {to: transit|passenger}', 'a11y_story_mode {screen_reader, reduce_motion}'],
      copy: {
        f1: '{partOfDay} · {label} (trip.story.frames.f1.title) · kicker “Drive decoded” · chips “{car}” · “I was a passenger”',
        f2: '{distance} km · {duration} min (frames.f2.line)',
        f3: 'Your inputs, replayed. / Every brake and turn in 3 seconds.',
        f4: 'Smoothness {n} · {compareText} · record: “New route record.” + “{n} on {route}, beating {m} from {date}.” · first drive: “Your baseline. Every drive from here is compared to you, not to anyone else.” · calibrating: “Calibrating your baseline · k of 3”',
        f5: 'Cleanest stretch: {km} km on {road} with no abrupt inputs.',
        f6: 'One thing for next time: {tip}',
        fDegraded: 'Your phone moved around for about 3 minutes, so we left that part out of the score.',
        fUnscorable: 'We couldn’t score this one fairly (phone in a bag). Here’s the route anyway.',
        fRoute: 'Looks like a regular. Name this route? · Commute · Gym · School run · Custom…',
        fNeeded: 'Hard stop at Spadina & King. Was it needed? · Yes, it was needed · No · done: “Marked as needed. It doesn’t count. Your Smoothness is 83 without it.”',
        Transit: 'Was this a bus or train? It stopped and started like transit. If it was, we’ll leave it out of your scores. · Bus · Train · Streetcar · No, I drove',
        Passenger: 'Passenger · not scored · Change — Not scored, and it doesn’t count toward your baseline, routes or recaps.',
        f7: 'Explore the drive · Share · Done · {badge} data · phone {state} · first drive: “That’s the loop. Every drive gets a Story. Explore it in Replay, and repeat trips become Routes.”',
        Load: 'Decoding your drive… Building the replay',
      },
      freePro: 'Free on every trip, forever (F-STY-01). Explore the drive opens S-RPL-01; on Free for a trip older than 30 days the replay shows its own lock (S-SUB-03 replayAnalysis30Days), never the Story. No paywall after any Story, and never after a worse-than-usual one (F-SUB-08).',
      data: 'Copy: trip.story.frames (exact). States → trips: default t20261105-1745 · firstDrive t20260928-1842 · calibrating t20260929-0738 (2 of 3) · routeDiscovered t20261002-0745 (fRoute) · newRouteRecord t20261105-0742 · degraded t20261027-1728 (fDegraded) · unscorable t20261106-1210 · wasItNeededInline t20261020-0740 (fNeeded, e3) · passengerChip t20261031-1905 · transitQuestion t20261104-0650 (no fixture trip is flagged as transit; the question is simulated on a stop-start trip). Frame order follows the fixture keys, except Unscorable (f1, f2, fUnscorable, f7 per F-STY-04.2; the fixture’s f3 is dropped) and passenger (f1, f2, f7: the G-dot replay would show someone else’s inputs). The f3 accessibility sentence and the G-dot path are derived from trip.track (lonG/latG) and trip.events.',
      icons: ['story', 'close', 'passenger', 'replay', 'share', 'routes', 'rosette', 'info', 'phone', 'check', 'sparkle', 'pinStop'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ==========================================================================================================
     S-RPL-01 · Trip Replay (immersive)                       F-RPL-01/02/03/08 · F-DQ-02/03/04 · benchmark: Mileon
     Full-bleed plain-canvas map, moving marker, glass readouts, playback bar with Auto/1×/10×/30× and a scrubber
     with event ticks + elevation. Playback, scrubbing and pin taps are live in mount() (no re-render per frame).
     ========================================================================================================== */
  const RPL_LAYERS = { events: true, stops: true, max: true, style: 'standard' };
  const RPL_SPEEDS = [{ id: 'auto', label: 'Auto' }, { id: '1', label: '1×' }, { id: '10', label: '10×' }, { id: '30', label: '30×' }];
  const rplRate = (t, sp) => (sp === 'auto' ? t.durationS / 60 : +sp);
  const RPL_STATES = [
    { id: 'default', label: 'Paused at start' }, { id: 'playing', label: 'Playing (each speed)' }, { id: 'scrubbing', label: 'Scrubbing' },
    { id: 'eventPinPulse', label: 'Event pin pulse' }, { id: 'gpsGap', label: 'GPS gap' }, { id: 'estimatedStart', label: 'Estimated start' },
    { id: 'excludedSpan', label: 'Excluded span' }, { id: 'noEvents', label: 'No events' }, { id: 'Off', label: 'Off (no map tiles)' },
    { id: 'mapLoad', label: 'Map Load' }, { id: 'Lock', label: 'Lock (Free, > 30 days)' }, { id: 'Err', label: 'Err' }, { id: 'landscape', label: 'Landscape' },
  ];
  const RPL_PARAMS = {
    default: { trip: 't20261105-1745' }, playing: { trip: 't20261105-1745' }, scrubbing: { trip: 't20261020-0740' }, eventPinPulse: { trip: 't20261105-1745' },
    gpsGap: { trip: 't20261020-0740' }, estimatedStart: { trip: 't20261105-0742' }, excludedSpan: { trip: 't20261027-1728' }, noEvents: { trip: 't20261105-0742' },
    Off: { trip: 't20261105-1745' }, mapLoad: { trip: 't20261105-1745' }, Lock: { trip: 't20261002-0745', ent: 'free' }, Err: { trip: 't20261105-1745' }, landscape: { trip: 't20261020-0740' },
  };
  function rplT0(ctx, t) {
    const T = t.durationS, q = t.quality.spans;
    switch (ctx.state) {
      case 'playing': return T * 0.36;
      case 'scrubbing': return T * 0.58;
      case 'eventPinPulse': return t.events[0] ? t.events[0].t + 0.4 : T * 0.3;
      case 'gpsGap': return q.gap[0] ? (q.gap[0][0] + q.gap[0][1]) / 2 : T * 0.5;
      case 'estimatedStart': return q.estimated[0] ? q.estimated[0][1] * 0.55 : 10;
      case 'excludedSpan': return q.excluded[0] ? (q.excluded[0][0] + q.excluded[0][1]) / 2 : T * 0.5;
      case 'noEvents': return T * 0.46;
      case 'Off': return T * 0.3;
      case 'landscape': return T * 0.64;
      default: return 0;
    }
  }
  function rplGeo(ctx) {
    const land = ctx.state === 'landscape';
    const W = land ? ctx.size.h : ctx.size.w, H = land ? ctx.size.w : ctx.size.h;
    const it = land ? 78 : ctx.size.top + 154 + (ctx.xl ? 96 : 0), ib = land ? 112 : ctx.size.bottom + 200 + (ctx.xl ? 40 : 0);
    const sw = land ? W - 120 - 36 - 48 - 228 - 24 : W - 32 - 28;
    return { land, W, H, it, ib, pad: land ? 44 : 30, sw: Math.round(sw) };
  }
  function rplScrubber(ctx, t, w, frac) {
    const x = (w * frac).toFixed(1);
    return ctx.ui.scrubber(t, w, frac).replace(/<\/svg>$/, `<line class="rpl-elc" x1="${x}" x2="${x}" y1="29" y2="48" stroke="var(--dp-data-cursor)" stroke-width="1.4"/></svg>`);
  }
  function rplReadouts(ctx, t, s) {
    const { ui, data } = ctx, a = U.sampleAt(t, s), noGps = a.lat == null || a.kmh == null;
    const cell = (k, v, unit, label) => `<div><b class="ui-num" data-r="${k}">${ui.esc(v)}${unit ? `<span class="ui-unit">${unit}</span>` : ''}</b><span>${label}</span></div>`;
    return `<div class="rpl-read ui-glassy" role="group" aria-label="Readouts">${cell('speed', noGps ? '—' : Math.round(a.kmh), noGps ? '' : 'km/h', 'Speed')}${cell('el', data.clock(s), '', 'Elapsed')}${cell('dist', data.km(kmAt(t, s)), 'km', 'Distance')}</div>`;
  }

  screen('S-RPL-01', {
    name: 'Trip Replay',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'push', tab: 'drives',
    states: RPL_STATES, stateParams: RPL_PARAMS,
    stateAliases: { paused: 'default', pulse: 'eventPinPulse', gap: 'gpsGap', offline: 'Off' },
    chrome: (ctx) => (ctx.state === 'landscape' ? { tabBar: false, statusBar: false, island: false } : { tabBar: false }),
    backTo: 'S-TRP-01',
    render(ctx) {
      const { ui, data, icons, state } = ctx, esc = ui.esc, S = ctx.store;
      const t = tripOf(ctx, 't20261105-1745'), g = rplGeo(ctx), T = t.durationS;
      const sel = ctx.params.event ? evOf(t, ctx.params.event) : null;
      const s0 = clamp(S.t ?? (ctx.params.at != null ? +ctx.params.at : sel ? sel.t : rplT0(ctx, t)), 0, T);
      const playing = S.playing ?? state === 'playing';
      const speed = S.speed || 'auto';
      const locked = state === 'Lock' || data.isLocked(t, ctx.ent);
      const plain = ['Off', 'mapLoad', 'Err'].includes(state);
      const P = projector(t, g.W, g.H, g.pad, g.it, g.ib);
      const L = RPL_LAYERS;
      const hide = [!L.events && 'rpl-hide-ev', !L.stops && 'rpl-hide-st', !L.max && 'rpl-hide-mx', `is-${L.style}`].filter(Boolean).join(' ');
      // hit targets (≥ 44 pt): events in time order, then stops, then max speed (accessibility.md §3 Map)
      let hits = '';
      t.events.forEach((e) => { const [x, y] = P(e.lat, e.lng); hits += `<span class="rpl-hit is-ev ${e.needed ? 'is-needed' : ''}" style="left:${x.toFixed(1)}px;top:${y.toFixed(1)}px" ${ui.act({ action: 'pin', arg: `e:${e.id}`, label: `${evLabel(ctx, e.type)} at ${plainLabel(e.place)}, ${tod(ctx, t, e.t)}. Peak ${e.peakG.toFixed(2)} g.${e.needed ? ' Marked as needed, not counted.' : ''}` })}><i class="rpl-pulse is-${e.type}"></i></span>`; });
      t.stops.slice(0, 6).forEach((st, i) => { const [x, y] = P(st.lat, st.lng); hits += `<span class="rpl-hit is-st" style="left:${(x - 2).toFixed(1)}px;top:${y.toFixed(1)}px" ${ui.act({ action: 'pin', arg: `s:${i}`, label: `Stop, ${st.durationS} seconds on ${plainLabel(st.road)}, ${tod(ctx, t, st.t)}` })}></span>`; });
      if (t.maxAt && t.maxAt.lat) { const [x, y] = P(t.maxAt.lat, t.maxAt.lng); hits += `<span class="rpl-hit is-mx" style="left:${x.toFixed(1)}px;top:${(y - 8).toFixed(1)}px" ${ui.act({ action: 'pin', arg: 'max', label: `Max speed, ${t.maxKmh} kilometres per hour` })}></span>`; }
      const mk = markerPos(t, P, s0);
      const overlay = `<div class="rpl-ov" style="width:${g.W}px;height:${g.H}px">${locked ? '' : hits}${markerHtml(markerStyle(mk))}</div>`;
      const mapLabel = `Map of your drive, ${t.distanceKm} kilometres from ${t.from} to ${t.to}. ${t.events.length} events, ${t.stops.length} stops.`;
      const map = ui.map(t, { w: g.W, h: g.H, pad: g.pad, insetTop: g.it, insetBottom: g.ib, radius: 0, others: !plain, select: sel ? sel.id : null, overlay, cls: `rpl-map ${hide} ${locked ? 'dp-locked' : ''}`, label: mapLabel });
      const sp = spanAt(t, s0);
      const chips = `${ui.qualityBadge(t.quality.badge, { glass: true, text: `${t.quality.badge}${t.quality.spans.gap.length ? ' · brief GPS gap' : t.quality.excludedMin ? ` · ${t.quality.excludedMin} min left out` : ''}`, nav: 'S-DQ-01', params: { trip: t.id }, label: `${t.quality.badge} data. Opens data quality details` })}<span class="ui-chip ui-chip--q ui-glassy rpl-span" ${sp ? '' : 'hidden'} role="status">${sp ? icons.provenance('estimated', { size: 12 }) : ''}<span>${sp ? SPAN_CHIP[sp][0] : ''}</span></span>`;
      const banner = state === 'Off' ? `<div class="rpl-note ui-glassy" role="status">${ui.icon('gps', 16)}<span><b>Map unavailable offline.</b> Your route, pins and data are all here.</span></div>`
        : state === 'mapLoad' ? `<div class="rpl-note ui-glassy is-load" role="status">${ui.loader({ size: 18, label: 'Loading map' })}<span>Loading map…</span></div>` : '';
      const top = `<div class="rpl-top"><div class="rpl-bar">${ui.backButton()}<div class="rpl-tt ui-glassy"><b>Replay</b><span>${esc(t.partOfDay)} · ${esc(t.label)}</span></div>${ui.iconButton('layers', { nav: 'S-RPL-04', params: { trip: t.id }, label: 'Map layers' })}${ui.iconButton('analysis', { nav: locked ? 'S-SUB-03/replayAnalysis30Days' : 'S-ANL-01', params: { trip: t.id }, label: 'Open Analysis' })}</div>${locked ? '' : rplReadouts(ctx, t, s0)}<div class="rpl-chips">${locked ? '' : chips}${banner}</div></div>`;
      const frac = s0 / T;
      const ev = t.events.length;
      const sum = ev ? `${ui.num(t.maxKmh, 'km/h')} max · ${ui.num(t.stopCount)} stop${t.stopCount === 1 ? '' : 's'} · ${ui.num(ev)} event${ev === 1 ? '' : 's'}` : `${ui.icon('check', 14)}No abrupt inputs on this drive. · ${ui.num(t.maxKmh, 'km/h')} max · ${ui.num(t.stopCount)} stops`;
      const speedCtl = ctx.xl ? ui.button(`Speed: ${RPL_SPEEDS.find((x) => x.id === speed).label}`, { kind: 'tonal', size: 's', action: 'speedCycle' }) : ui.segmented(RPL_SPEEDS, { selected: speed, action: 'speed', cls: 'rpl-speed' });
      const hint = s0 === 0 && !playing ? `<span class="rpl-hint ui-glassy">Play your drive</span>` : '';
      const bubble = `<span class="rpl-bub" style="left:${(frac * 100).toFixed(2)}%">${data.clock(s0)}</span>`;
      const play = `<div class="rpl-play ${ctx.ios ? 'ui-glassy' : ''}">${hint}<div class="rpl-ctl"><span class="rpl-pp ui-press" ${ui.act({ action: 'play', label: playing ? 'Pause replay' : 'Play replay', haptic: 'tap' })}>${ui.icon(playing ? 'pause' : 'play', 24)}</span>${speedCtl}</div><div class="rpl-scr ${state === 'scrubbing' ? 'is-scrub' : ''}" tabindex="0" aria-label="Replay position. Swipe up or down to move 30 seconds.">${bubble}${rplScrubber(ctx, t, g.sw, frac)}</div><div class="rpl-ax"><span>0:00</span><span>${data.clock(T)} · ↗ ${t.elevGainM} m ↘ ${t.elevLossM} m</span></div><div class="rpl-sum">${sum}</div></div>`;
      let over = '';
      if (locked) over = `<div class="rpl-lock"><div class="rpl-lock-c ui-glassy">${ui.icon('lock', 26)}<b>Your ${esc(data.date(t.start))} drive</b><p>Replay for drives older than 30 days is part of Pro. Nothing is deleted.</p><p class="is-v">${esc(ctx.D.entitlement.lockSheets.oldAnalysis)}</p>${ui.button('See plans', { kind: 'filled', block: true, nav: 'S-SUB-03/replayAnalysis30Days', params: { trip: t.id } })}${ui.button('Not now', { kind: 'text', block: true, back: true })}<small>Your summary, score and Story stay free forever.</small></div></div>`;
      if (state === 'Err') over = `<div class="rpl-lock"><div class="rpl-lock-c ui-glassy">${ui.error({ title: 'Couldn’t load this drive’s detail.', text: 'Your recording is safe. Try again.', action: { action: 'retry' } })}</div></div>`;
      const land = g.land ? ` style="width:${g.W}px;height:${g.H}px;left:${(ctx.size.w - g.W) / 2}px;top:${(ctx.size.h - g.H) / 2}px"` : '';
      return `<div class="ui-page rpl ${g.land ? 'is-land' : ''} ${state === 'mapLoad' ? 'is-tiles' : ''}" data-t="${s0.toFixed(2)}" data-playing="${playing ? 1 : 0}" data-static="${locked || state === 'Err' ? 1 : ''}"${land}>${map}${top}${locked || state === 'Err' ? '' : play}${over}</div>`;
    },
    mount(el, ctx) {
      const root = el.querySelector('.rpl');
      if (!root || root.dataset.static) return;
      const t = tripOf(ctx, 't20261105-1745'), g = rplGeo(ctx), T = t.durationS, S = ctx.store, data = ctx.data;
      const P = projector(t, g.W, g.H, g.pad, g.it, g.ib);
      const shot = !!(ctx.shot || window.DP.shot), reduced = ctx.motion.reduced(root);
      S.t = +root.dataset.t; S.playing = root.dataset.playing === '1'; S.speed = S.speed || 'auto';
      const $ = (s) => root.querySelector(s);
      const mk = $('.wc-mk'), thumb = $('.ui-scr-thumb'), played = $('.ui-scr-played'), elc = $('.rpl-elc'), bub = $('.rpl-bub'), scr = $('.rpl-scr'), chip = $('.rpl-span'), pp = $('.rpl-pp'), hint = $('.rpl-hint');
      const rSpeed = $('[data-r="speed"]'), rEl = $('[data-r="el"]'), rDist = $('[data-r="dist"]');
      let raf = 0, last = 0;
      function setT(s, o = {}) {
        const prev = S.t; s = clamp(s, 0, T); S.t = s;
        const m = markerPos(t, P, s); applyMarker(mk, m);
        const a = m.a, noGps = a.lat == null || a.kmh == null;
        if (rSpeed) rSpeed.innerHTML = noGps ? '—' : `${Math.round(a.kmh)}<span class="ui-unit">km/h</span>`;
        if (rEl) rEl.textContent = data.clock(s);
        if (rDist) rDist.innerHTML = `${data.km(kmAt(t, s))}<span class="ui-unit">km</span>`;
        const x = (g.sw * s) / T;
        if (thumb) thumb.setAttribute('cx', x.toFixed(1)); if (played) played.setAttribute('width', x.toFixed(1));
        if (elc) { elc.setAttribute('x1', x.toFixed(1)); elc.setAttribute('x2', x.toFixed(1)); }
        if (bub) { bub.style.left = `${((s / T) * 100).toFixed(2)}%`; bub.textContent = data.clock(s); }
        if (scr) scr.setAttribute('aria-valuetext', `${data.min(s)} of ${data.min(T)}.${noGps ? ' No GPS here.' : ` Speed ${Math.round(a.kmh)} kilometres per hour.`}`);
        const sp = spanAt(t, s);
        if (chip) { chip.hidden = !sp; if (sp) chip.lastChild.textContent = SPAN_CHIP[sp][0]; }
        if (o.playing) for (const e of t.events) if (prev < e.t && s >= e.t) pulse(e);
      }
      function pulse(e) {
        const r = root.querySelector(`.rpl-hit[data-arg="e:${e.id}"] .rpl-pulse`); if (!r) return;
        if (reduced) r.animate([{ opacity: 1, transform: 'scale(1.25)' }, { opacity: 0, transform: 'scale(1.25)' }], { duration: 600 });
        else r.animate([{ opacity: 0.75, transform: 'scale(1)' }, { opacity: 0, transform: 'scale(2.4)' }], { duration: 600, easing: 'cubic-bezier(.32,.72,0,1)' });
      }
      const icon = () => { if (pp) { pp.innerHTML = ctx.ui.icon(S.playing ? 'pause' : 'play', 24); pp.setAttribute('aria-label', S.playing ? 'Pause replay' : 'Play replay'); } };
      const step = (now) => { const dt = Math.min(0.1, (now - last) / 1000); last = now; setT(S.t + dt * rplRate(t, S.speed), { playing: true }); if (S.t >= T) { pause(); return; } raf = requestAnimationFrame(step); };
      function play() { if (S.t >= T - 0.5) setT(0); S.playing = true; icon(); if (hint) hint.remove(); if (shot) return; cancelAnimationFrame(raf); last = performance.now(); raf = requestAnimationFrame(step); }
      function pause() { S.playing = false; cancelAnimationFrame(raf); raf = 0; icon(); }
      function select(id) { root.querySelectorAll('.ui-mpin.is-event').forEach((p) => { const e = t.events[+p.dataset.i]; p.classList.toggle('is-dim', !!id && e.id !== id); p.classList.toggle('is-sel', e.id === id); }); }
      // scrubber: drag, snap to event ticks within 8 px (haptic selection), keyboard ±30 s
      let drag = null;
      const fracAt = (e) => { const r = scr.querySelector('svg').getBoundingClientRect(); return clamp((e.clientX - r.left) / r.width, 0, 1); };
      const sdown = (e) => { drag = { was: S.playing, snapped: null }; pause(); scr.classList.add('is-scrub'); scr.setPointerCapture?.(e.pointerId); smove(e); };
      const smove = (e) => { if (!drag) return; let s = fracAt(e) * T; const near = t.events.find((ev) => Math.abs(((ev.t - s) / T) * g.sw) < 8); if (near) { s = near.t; if (drag.snapped !== near.id) { ctx.haptic('selection'); drag.snapped = near.id; } } else drag.snapped = null; setT(s); };
      const sup = () => { if (!drag) return; scr.classList.remove('is-scrub'); const w = drag.was; drag = null; if (w) play(); };
      const skey = (e) => { const k = { ArrowRight: 30, ArrowUp: 30, ArrowLeft: -30, ArrowDown: -30 }[e.key]; if (k) { e.preventDefault(); setT(S.t + k); } if (e.key === 'Home') setT(0); if (e.key === 'End') setT(T); };
      if (scr) { scr.addEventListener('pointerdown', sdown); scr.addEventListener('pointermove', smove); scr.addEventListener('pointerup', sup); scr.addEventListener('pointercancel', sup); scr.addEventListener('keydown', skey); }
      const onScreen = (e) => { if (e.detail && e.detail.id === 'S-RPL-01') select(null); };
      document.addEventListener('dp:screen', onScreen);
      el._rpl = { toggle: () => (S.playing ? pause() : play()), pause, select, setT, get t() { return S.t; } };
      setT(S.t);
      if (!S.drawn && !shot) { ctx.ui.anim.maps(root); S.drawn = true; }
      if (S.playing && !shot) play();
      return () => { cancelAnimationFrame(raf); document.removeEventListener('dp:screen', onScreen); delete el._rpl; };
    },
    actions: {
      play: (ctx) => { ctx.entry.el._rpl?.toggle(); return false; },
      speed: (ctx, arg, el) => { ctx.store.speed = arg; el.parentNode.querySelectorAll('.ui-seg-i').forEach((x) => { const on = x === el; x.classList.toggle('is-on', on); x.setAttribute('aria-selected', on); }); return false; },
      speedCycle: (ctx) => { const i = RPL_SPEEDS.findIndex((x) => x.id === (ctx.store.speed || 'auto')); ctx.store.speed = RPL_SPEEDS[(i + 1) % 4].id; ctx.update(); return false; },
      pin: (ctx, arg) => {
        const c = ctx.entry.el._rpl; if (c) c.pause();
        const t = tripOf(ctx, 't20261105-1745'), [k, id] = arg.split(':');
        if (k === 'e') { c && c.select(id); ctx.haptic('tap'); ctx.nav('S-RPL-02', { trip: t.id, event: id }); }
        else if (k === 's') ctx.nav('S-RPL-03', { trip: t.id, stop: id });
        else ctx.nav('S-RPL-03/maxSpeed', { trip: t.id });
        return false;
      },
      retry: (ctx) => { ctx.toast('Loaded', { icon: 'check' }); ctx.setState('default'); return false; },
    },
    spec: {
      purpose: 'Watch the drive replay on a map with a moving marker, so the user can relive and understand it (F-RPL-01), with speed-neutral readouts, max speed, stops and elevation (F-RPL-02), tappable event pins by shape (F-RPL-03) and honest quality overlays (F-RPL-08). Benchmark: Mileon replay; DrivePerf adds evidence pins.',
      layout: [
        'Immersive push (no tab bar, no accessory). Full-bleed map (MapKit / Google Maps or MapLibre; prototype: plain canvas from trip.track) fitted to the route between the top cluster and the playback bar; “© OpenStreetMap contributors” bottom-right above the playback bar.',
        'Route: Glide 3.8 px on a 7.5 px casing · estimated start dashed 4/4 at 60% · GPS gap dotted (`--dp-data-gap-stroke` 1/6) · excluded span dotted grey · start ring and end dot · stop capsules · neutral outline max-speed triangle (no animation) · event pins 26 by shape (braking rounded square, acceleration circle, cornering diamond; needed = hollow).',
        'Marker: 16 px white dot with a 3 px Glide ring, 36 px halo at 20%, 18 px heading wedge (40°). Position = interpolated track sample at time t (display-only).',
        'Top cluster (safe-top + 4, 16 margins): bar = glass back (44) · glass title capsule “Replay” + part of day · label · glass Layers (→ S-RPL-04) · glass Analysis (→ S-ANL-01). Readouts capsule 56 tall: Speed · Elapsed · Distance (DP Numerals 24, units 50%, labels caption text2, 0.5 px separators) — speed is neutral text, never coloured. Chip row: quality badge (glass) + contextual span chip (“Estimated start” / “No GPS” / “Phone moved — not scored”).',
        'Playback bar (16 inset, above the home indicator; iOS glass radius 30 · Android floating toolbar radius 28): play/pause 48 (iOS glass prominent circle · Android FAB-style primaryContainer radius 16) + speed selector Auto · 1× · 10× · 30× (iOS glass segmented · Android connected button group) · scrubber (4 px track, played Glide, event ticks 12 px above, stop ticks, gap and estimated overlays, 16 px thumb → 22 while dragging, time bubble) · elevation mini-profile 24 px with a synced cursor + “↗ 118 m ↘ 103 m” · summary line “103 km/h max · 2 stops · 2 events” (neutral).',
        'Lock (Free, > 30 days): the trip’s own map blurred 14 px + centred glass card: lock · “Your 2 Oct drive” · “Replay for drives older than 30 days is part of Pro. Nothing is deleted.” · “Keep exploring every drive with Pro.” · See plans (filled) · Not now (text) · “Your summary, score and Story stay free forever.”',
        'Off: plain canvas (no road layer) + glass note “Map unavailable offline. Your route, pins and data are all here.” · map Load: route drawn over a breathing skeleton land + “Loading map…” · Err: centred glass error card with Try again.',
        'Landscape: map full-bleed, title/readouts in one top row, playback bar one row (play · speed · scrubber), 60 pt side insets for the island.',
      ],
      components: ['map', 'scrubber', 'segmented', 'qualityBadge', 'chip', 'iconButton', 'backButton', 'button', 'error', 'loader', 'markerHtml (local)', 'rplReadouts (local)'],
      interactions: [
        'Play/Pause toggles playback; t += dt × rate (Auto = whole trip in ~60 s, 1×, 10×, 30×); reaching the end pauses; Play at the end restarts.',
        'Speed selector changes rate immediately (haptic selection). XL: a single “Speed: Auto” button cycles speeds.',
        'Scrubber: drag (or tap) moves the marker and every readout in the same frame (≤ 100 ms, F-RPL-01.3); within 8 px of an event tick it snaps (haptic selection); release resumes the previous play state. Keyboard / VoiceOver adjustable: ±30 s.',
        'Event pin tap → pauses, selects the pin (1.18×, others dim to 50%), haptic tap, opens S-RPL-02 (medium detent) with trip + event. Stop pin → S-RPL-03 (stop) · max-speed pin → S-RPL-03/maxSpeed.',
        'Quality badge → S-DQ-01 · Layers → S-RPL-04 (changes apply immediately and persist) · Analysis → S-ANL-01 (Lock → S-SUB-03 replayAnalysis30Days) · Back → S-TRP-01 (or the Story).',
        'D1 gate: requested during a driver trip or while moving → S-DRV-05.',
      ],
      motion: [
        'Hero: the marker. It moves continuously from the display clock (rAF dt, 120 Hz capable); the heading wedge follows the path.',
        'On load: route draw-on 1800 ms, then event pins pop in time order (shared.pinPop, 40 ms stagger); the max-speed pin appears with the route, never animated.',
        'Pin pulse when the marker passes an event: ring scale 1 → 2.4, opacity 0.75 → 0, 600 ms ease.glide, once; no sound, no haptic (F-RPL-03.2).',
        'GPS gap: the marker hides at the gap and reappears after it; the “No GPS” chip appears and the speed readout shows “—”.',
        'Scrubbing: thumb grows to 22 (spring.fast); the time bubble rides above it.',
        'RM: no draw-on or pin pop (fade in); the pulse becomes a 600 ms outline highlight with no scale; the marker still moves because playback is user-initiated.',
      ],
      a11y: [
        'Map container: “Map of your drive, 39 kilometres from Work to Home. 2 events, 2 stops.” Pins are buttons in time order, then stops, then max speed: “Hard braking at the Gardiner, 5:56 pm. Peak 0.40 g.”',
        'Scrubber: “Replay position, 12 min of 33 min. Speed 92 kilometres per hour.” Adjustable ±30 s; custom actions Next event / Previous event / Next stop (native).',
        'Play/Pause: “Play replay” / “Pause replay”; speed segmented is a tab list (“Auto, selected”). Readouts are not a live region.',
        'Every pin hit area ≥ 44 pt / 48 dp; colliding pins cluster natively.',
        'Speed is plain text in `--dp-text`; max speed is an outline triangle with no emphasis (D5/D6).',
      ],
      platformDiffs: {
        ios: 'MapKit with `.mapStyle(.standard(emphasis: .muted))`; glass title capsule, readouts and playback bar (`.glassEffect(.regular)`), glass segmented speed control, glass prominent play button; marker as a MapKit annotation driven by CADisplayLink.',
        android: 'Google Maps SDK / MapLibre; top bar items as tonal icon buttons, readouts in a tonal toolbar (surface-container-high, radius 20); M3E floating toolbar for playback with an attached FAB-style play (primaryContainer, radius 16) and a connected button group for speed (selected segment morphs to a pill).',
      },
      analytics: ['replay_opened {source}', 'replay_played {speed}', 'replay_scrubbed', 'replay_completed', 'event_pin_tapped {type, confidence}', 'replay_pin_tapped {type: max_speed|stop}', 'quality_overlay_legend_opened', 'locked_content_viewed {type: replay, age_bucket}'],
      copy: { Title: 'Replay · {partOfDay} · {label}', Readouts: 'Speed · Elapsed · Distance', Hint: 'Play your drive', Spans: 'Estimated start · No GPS · Phone moved — not scored', 'No events': 'No abrupt inputs on this drive.', Offline: 'Map unavailable offline. Your route, pins and data are all here.', Loading: 'Loading map…', Error: 'Couldn’t load this drive’s detail. Your recording is safe. Try again.', Lock: 'Your {date} drive · Replay for drives older than 30 days is part of Pro. Nothing is deleted. · Keep exploring every drive with Pro. · See plans · Not now · Your summary, score and Story stay free forever.' },
      freePro: 'Free for drives in the last 30 days; older drives are Pro (Gate 2 OQ-06): the Lock state blurs this trip’s own map and links to S-SUB-03 replayAnalysis30Days. Pro, Preview and Ride-along: no lock.',
      data: 'States → trips: default/playing/eventPinPulse/Off/mapLoad/Err t20261105-1745 · scrubbing/gpsGap/landscape t20261020-0740 (gap 1899–1940 s, needed brake at Spadina & King) · estimatedStart/noEvents t20261105-0742 · excludedSpan t20261027-1728 · Lock t20261002-0745 (ent=free). Marker, readouts and elevation cursor are derived from trip.track via util.sampleAt; distance-so-far is derived by integrating track positions, scaled to trip.distanceKm.',
      icons: ['play', 'pause', 'layers', 'analysis', 'lock', 'gps', 'check', 'chevronLeft'],
      assets: [],
    },
  });

  /* ---------- S-RPL-02 · Event evidence sheet ---------- */
  const EVD_PARAMS = {
    default: { trip: 't20261105-1745', event: 'e1' }, hardAcceleration: { trip: 't20261027-1728', event: 'e2' }, hardCornering: { trip: 't20261010-1015', event: 'e4' },
    lowConfidence: { trip: 't20261022-0805', event: 'e2' }, markedNeeded: { trip: 't20261020-0740', event: 'e3' }, notAccurateReasons: { trip: 't20261105-1745', event: 'e1' },
    traceRemoved: { trip: 't20261002-0745', event: 'e1' }, thisDrivesTip: { trip: 't20261105-1745', event: 'e2' },
  };
  const REASONS = [{ id: 'moved', label: 'Phone moved' }, { id: 'bump', label: 'Pothole or speed bump' }, { id: 'notme', label: 'Not me driving' }, { id: 'other', label: 'Other' }];
  screen('S-RPL-02', {
    name: 'Event evidence sheet',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'drives', detent: 'auto',
    over: (ctx) => ({ id: 'S-RPL-01', params: { trip: tripOf(ctx, 't20261105-1745').id, event: ctx.params.event || 'e1' } }),
    states: [
      { id: 'default', label: 'Hard braking' }, { id: 'hardAcceleration', label: 'Hard acceleration' }, { id: 'hardCornering', label: 'Hard cornering' },
      { id: 'lowConfidence', label: 'Low confidence' }, { id: 'markedNeeded', label: 'Marked needed' }, { id: 'notAccurateReasons', label: '“Not accurate?” reasons' },
      { id: 'traceRemoved', label: 'Trace removed (Part)' }, { id: 'thisDrivesTip', label: '“This drive’s tip”' },
    ],
    stateParams: EVD_PARAMS,
    render(ctx) {
      const { ui, data, icons, state } = ctx, esc = ui.esc;
      const t = tripOf(ctx, 't20261105-1745');
      let e = evOf(t, ctx.params.event) || t.events[0];
      if (!e) return `${ui.sheetHeader('No events')}${ui.empty({ icon: 'check', title: 'No abrupt inputs on this drive.', text: 'Nothing crossed the braking, acceleration or cornering thresholds.' })}`;
      if (state === 'traceRemoved') e = { ...e, evidence: null };
      const needed = e.needed || ctx.store.needed;
      const i = t.events.indexOf(evOf(t, e.id)), n = t.events.length;
      const w = ctx.size.w - (ctx.ios ? 56 : 48);
      const sub = `${cap1(e.place)} · ${tod(ctx, t, e.t)}${e.direction ? ` · ${e.direction}` : ''}`;
      const head = ui.sheetHeader(evLabel(ctx, e.type), { icon: icons.pin(e.type, { size: 28, needed }), sub });
      const banners = [];
      if (e.confidence !== 'High') banners.push(ui.banner({ kind: 'info', icon: icons.provenance('estimated', { size: 18 }), title: `${e.confidence} confidence`, text: 'Your phone’s signal was weaker here, so treat this one lightly.' }));
      if (needed) banners.push(ui.banner({ kind: 'positive', icon: icons.pin(e.type, { size: 20, needed: true }), title: 'You marked this as needed.', text: 'It doesn’t count toward your score.', action: { text: 'Count it again', nav: 'S-SCO-03/countItAgain', params: { trip: t.id, event: e.id } } }));
      if (!e.evidence) banners.push(ui.banner({ kind: 'info', icon: 'layers', title: 'Detailed trace not kept', text: 'The raw sensor data for this drive was removed to save space. The summary values below stay.' }));
      const trace = e.evidence ? `<div class="evd-trace">${traceHtml(ctx, e, w - 24, 118)}<div class="evd-leg"><span><i class="is-sp"></i>Speed</span><span><i class="is-lo"></i>Long G</span><span><i class="is-la"></i>Lat G</span><span><i class="is-band is-${e.type}"></i>The event</span></div></div>` : '';
      const fact = (label, v, unit, prov, cls = '') => `<div class="evd-f ${cls}" aria-label="${esc(`${label}, ${v} ${unit === 'g' ? 'g' : unit === 's' ? 'seconds' : unit === 'km/h' ? 'kilometres per hour' : unit}. ${cap1(prov)}`)}"><span>${esc(label)}${icons.provenance(prov, { size: 10 })}</span><b class="ui-num">${esc(v)}${unit ? `<span class="ui-unit">${unit}</span>` : ''}</b></div>`;
      const facts = `<div class="evd-facts">${fact('Peak', e.peakG.toFixed(2), 'g', 'derived', needed ? 'is-struck' : '')}${fact('Duration', e.durationS, 's', 'derived')}${fact('Speed', `${e.speedBeforeKmh} → ${e.speedAfterKmh}`, 'km/h', 'observed')}${fact('Confidence', e.confidence, '', 'estimated')}</div>`;
      const tip = tipFor(ctx, t, e);
      const tipHtml = tip && !needed ? `<div class="evd-tip">${ui.tipCard({ text: tip.text, label: tip.mine ? 'This drive’s tip' : 'Next time' })}<span class="evd-more ui-press" ${ui.act({ nav: 'S-SCO-04', params: { trip: t.id, event: e.id } })}>Why this tip${ui.icon('chevronRight', 14)}</span></div>` : '';
      let acts;
      if (state === 'notAccurateReasons' || ctx.store.reasons) {
        const sel = ctx.store.reason;
        acts = `<div class="evd-reasons"><b>What went wrong?</b><span>We’ll leave this event out and use your answer to tune detection on this phone.</span>${ui.radios(REASONS, sel, { action: 'reason' })}<div class="evd-acts">${ui.button('Send', { kind: 'filled', block: true, action: 'report', disabled: !sel })}${ui.button('Cancel', { kind: 'text', block: true, action: 'reasons', arg: '0' })}</div></div>`;
      } else acts = `<div class="evd-acts">${needed ? '' : ui.button('Was it needed?', { kind: 'tonal', block: true, nav: 'S-SCO-03', params: { trip: t.id, event: e.id } })}${ui.button('Not accurate?', { kind: 'text', block: true, action: 'reasons', arg: '1' })}</div>`;
      const stepper = n > 1 ? `<div class="evd-step">${ui.iconButton('chevronLeft', { svg: ui.icon('chevronLeft', 20), glass: ctx.ios, tonal: ctx.android, action: 'step', arg: '-1', label: 'Previous event', disabled: i <= 0 })}<span>Event ${i + 1} of ${n}</span>${ui.iconButton('chevronRight', { svg: ui.icon('chevronRight', 20), glass: ctx.ios, tonal: ctx.android, action: 'step', arg: '1', label: 'Next event', disabled: i >= n - 1 })}</div>` : '';
      const aria = `${evLabel(ctx, e.type)} at ${plainLabel(e.place)}, ${tod(ctx, t, e.t)}. Peak ${e.peakG.toFixed(2)} g over ${e.durationS} seconds, from ${e.speedBeforeKmh} to ${e.speedAfterKmh} kilometres per hour. Confidence ${e.confidence.toLowerCase()}.`;
      return `<div class="evd" aria-label="${esc(aria)}">${head}${banners.map((b) => `<div class="evd-b">${b}</div>`).join('')}${trace}${facts}${tipHtml}${acts}${stepper}</div>`;
    },
    mount(el, ctx) {
      if (still(ctx, el)) return;
      el.querySelectorAll('.evd-trace .ui-trace path').forEach((p) => ctx.motion.drawOn(p, { duration: 700, delay: 120 }));
    },
    actions: {
      reasons: (ctx, arg) => { ctx.store.reasons = arg === '1'; if (arg !== '1' && ctx.state === 'notAccurateReasons') { ctx.setState('default'); return false; } ctx.update(); return false; },
      reason: (ctx, arg) => { ctx.store.reason = arg; ctx.haptic('selection'); ctx.update(); return false; },
      report: (ctx) => { if (!ctx.store.reason) return false; ctx.toast('Thanks. This event won’t count. Your answer stays on this phone.', { icon: 'check' }); ctx.back(); return false; },
      step: (ctx, arg) => {
        const t = tripOf(ctx, 't20261105-1745'); const i = t.events.findIndex((e) => e.id === (ctx.params.event || t.events[0].id)) + +arg;
        if (i < 0 || i >= t.events.length) return false;
        const id = t.events[i].id; const r = underEntry('S-RPL-01'); if (r && r.el && r.el._rpl) { r.el._rpl.select(id); r.el._rpl.setT(t.events[i].t); }
        ctx.haptic('selection'); ctx.nav('S-RPL-02', { trip: t.id, event: id }, { replace: true }); return false;
      },
    },
    spec: {
      purpose: 'After tapping a pin or tick, show the evidence (speed and G from 10 s before to 10 s after, with start/peak/end) and the related tip, so the user understands the event and what to try next time (F-RPL-04). It is also where “Was it needed?” and “Not accurate?” start (F-SCO-05).',
      layout: [
        'iOS sheet at the medium detent (glass), expandable to large; Android ModalBottomSheet half → full with drag handle. Playback pauses and the map centres on the event underneath.',
        'Header: event pin 28 (hollow when needed) + “Hard braking” (`sheetTitle`) · “{Place} · {time of day}” (meta) · iOS glass close.',
        'Banners as needed: low confidence (info, dashed-ring glyph) · marked needed (positive, hollow pin, “Count it again”) · trace removed.',
        'Trace card (surface-2 / surface-container-high, radius 18, 12 padding): speed (neutral, top) and longitudinal + lateral G (one lane, sky solid and amber dashed), start/peak/end guides, −10 s … +10 s labels, legend row with swatch + pattern.',
        'Facts grid 4 columns (2 at XL): Peak 0.40 g · Duration 1.7 s · Speed 67 → 28 km/h (neutral) · Confidence High; each label carries its provenance glyph (derived/observed/estimated). Peak is struck through when needed.',
        'Tip card: “This drive’s tip” when trip.tip.eventId is this event, else “Next time” with the library tip for this event type and place; “Why this tip ›” → S-SCO-04.',
        'Actions: Was it needed? (tonal, block) → S-SCO-03 · Not accurate? (text) → inline reasons (radios: Phone moved · Pothole or speed bump · Not me driving · Other) + Send / Cancel.',
        'Event stepper: ‹ Event 1 of 2 › (gesture-free alternative to the scrubber’s next/previous event, accessibility.md §6).',
      ],
      components: ['sheetHeader', 'banner', 'evidenceTrace', 'tipCard', 'button', 'radios', 'iconButton', 'provenance'],
      interactions: ['Was it needed? → S-SCO-03 (small sheet over this one) · Count it again → S-SCO-03/countItAgain.', 'Not accurate? → reasons list; Send → feedback stored locally, the event is excluded, toast, sheet closes. Cancel returns.', 'Why this tip → S-SCO-04.', '‹ / › → previous/next event (replaces this sheet, moves the replay marker and selection underneath).', 'Drag down / scrim tap / close → back to Replay, selection clears.'],
      motion: ['Sheet rises with spring.sheet; the trace lines draw on in 700 ms after the sheet settles; detent snaps use the system spring + haptic selection.', 'RM: the trace appears complete; the sheet cross-fades.'],
      a11y: ['Sheet label: “Hard braking at Spadina and Front, 8:14 am. Peak 0.42 g over 1.4 seconds, from 48 to 0 kilometres per hour. Confidence high.” Focus moves to the title on open and returns to the pin on close.', 'Trace summary: “Speed and G from 10 seconds before to 10 seconds after. Speed went from 67 to 28 kilometres per hour. Peak 0.40 g.”', 'Each fact reads label, value, units spelled out and provenance. Needed state is announced in words, not only by the hollow pin.', 'XL: facts go to 2 columns; the trace stays 118 tall; the sheet opens large.'],
      platformDiffs: { ios: 'Glass sheet at the medium detent; tonal-glass “Was it needed?” button; glass stepper buttons; close button in the header.', android: 'ModalBottomSheet (surface-container-low, radius 28, 32×4 handle); FilledTonalButton + TextButton; tonal icon buttons; no close button (drag or back).' },
      analytics: ['evidence_opened {type}', 'event_reported_inaccurate {type, reason}', 'tip_viewed {tip_id}'],
      copy: { 'Low confidence': 'Your phone’s signal was weaker here, so treat this one lightly.', Needed: 'You marked this as needed. It doesn’t count toward your score. · Count it again', 'Trace removed': 'Detailed trace not kept. The raw sensor data for this drive was removed to save space. The summary values below stay.', Tip: 'This drive’s tip | Next time', Actions: 'Was it needed? · Not accurate?', Reasons: 'What went wrong? We’ll leave this event out and use your answer to tune detection on this phone. · Phone moved · Pothole or speed bump · Not me driving · Other · Send · Cancel', Thanks: 'Thanks. This event won’t count. Your answer stays on this phone.' },
      freePro: 'Same window as Replay: Free for drives in the last 30 days, Pro for older ones (the sheet is only reachable from an unlocked replay).',
      data: 'States → events: default t20261105-1745/e1 (the Gardiner) · thisDrivesTip t20261105-1745/e2 (Kerr St, trip.tip.eventId) · hardAcceleration t20261027-1728/e2 · hardCornering t20261010-1015/e4 · lowConfidence t20261022-0805/e2 (Low) · markedNeeded t20261020-0740/e3 · notAccurateReasons t20261105-1745/e1 · traceRemoved t20261002-0745/e1 (evidence withheld to simulate storage trimming). “Next time” tips are filled from DP_DATA.tips templates with the event’s place/road (derived); time of day = trip.start + event.t.',
      icons: ['info', 'layers', 'chevronLeft', 'chevronRight', 'check', 'close'],
      assets: [],
    },
  });

  /* ---------- S-RPL-03 · Stop and max-speed pin popover ---------- */
  screen('S-RPL-03', {
    name: 'Stop and max-speed pin popover',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'drives', detent: 'auto',
    over: (ctx) => ({ id: 'S-RPL-01', params: { trip: tripOf(ctx, 't20261105-1745').id } }),
    states: [{ id: 'default', label: 'Stop' }, { id: 'maxSpeed', label: 'Max speed (neutral style)' }],
    stateParams: { default: { trip: 't20261105-1745', stop: '0' }, maxSpeed: { trip: 't20261105-1745' } },
    render(ctx) {
      const { ui, data, icons } = ctx, esc = ui.esc;
      const t = tripOf(ctx, 't20261105-1745');
      if (ctx.state === 'maxSpeed') {
        const m = t.maxAt;
        return `<div class="pop">${ui.sheetHeader('Max speed', { icon: `<span class="pop-ic">${icons.maxPin({ size: 22 })}</span>`, sub: `${data.clock(m.t)} into the drive · ${tod(ctx, t, m.t)}` })}
          <div class="pop-v" aria-label="Max speed ${t.maxKmh} kilometres per hour. Derived."><b class="ui-num">${t.maxKmh}<span class="ui-unit">km/h</span></b>${ui.provenance('derived', 12)}</div>
          <p class="pop-n">The highest speed with a good GPS fix on this drive. Shown as plain text for your own review; it never appears on share cards, records or recaps.</p>
          <div class="pop-acts">${ui.button('Jump to this moment', { kind: 'tonal', block: true, icon: 'replay', action: 'jump', arg: m.t })}</div></div>`;
      }
      const i = clamp(+(ctx.params.stop || 0), 0, t.stops.length - 1), st = t.stops[i];
      return `<div class="pop">${ui.sheetHeader('Stop', { icon: `<span class="pop-ic">${icons.stopPin({ size: 20 })}</span>`, sub: `${st.road} · ${tod(ctx, t, st.t)}` })}
        <div class="pop-v" aria-label="Stopped for ${st.durationS} seconds. Derived."><b class="ui-num">${st.durationS}<span class="ui-unit">s</span></b><span>stopped</span>${ui.provenance('derived', 12)}</div>
        ${ui.list([ui.row({ icon: 'clock', title: 'Into the drive', value: data.clock(st.t) }), ui.row({ icon: 'pinStop', title: 'Stop on this drive', value: `${i + 1} of ${t.stops.length}` })])}
        <p class="pop-n">Stops of 10 seconds or more get a pin. Stopping fully and gently never costs Smoothness.</p>
        <div class="pop-acts">${ui.button('Jump to this moment', { kind: 'tonal', block: true, icon: 'replay', action: 'jump', arg: st.t })}</div></div>`;
    },
    actions: { jump: (ctx, arg) => { const r = underEntry('S-RPL-01'); if (r) { r.store.t = +arg; r.store.playing = false; } ctx.back(); if (r && r.el && r.el._rpl) r.el._rpl.setT(+arg); return false; } },
    spec: {
      purpose: 'Show a stop’s duration and time of day, or the max speed reached, when its pin is tapped (F-RPL-02.2/.3). Max speed follows the speed-neutral rule.',
      layout: ['Small sheet (iOS `.presentationDetents([.fraction(0.32)])` with `.presentationCompactAdaptation(.popover)` on iPad; Android small ModalBottomSheet). Auto height.', 'Header: pin glyph (stop capsule / outline triangle) + title “Stop” / “Max speed” + “{road} · {time}” / “{elapsed} into the drive · {time}”.', 'Value: DP Numerals 48 in `--dp-text` (never accent, never coloured) + unit 50% + provenance glyph.', 'Stop: list rows Into the drive · Stop n of m. Max speed: one explanatory line.', 'Action: “Jump to this moment” (tonal, block) → moves the replay marker there and closes.'],
      components: ['sheetHeader', 'list', 'row', 'button', 'provenance'],
      interactions: ['Jump to this moment → back to S-RPL-01 with the marker at that time (paused).', 'Drag down / scrim / close → dismiss.'],
      motion: ['spring.sheet in/out; no animation, glow or haptic on the max-speed value (motion.md §3.6). RM: cross-fade.'],
      a11y: ['“Max speed 103 kilometres per hour. Derived.” · “Stopped for 34 seconds. Derived.”', 'The value is static text, not a live region; no emphasis traits.'],
      platformDiffs: { ios: 'Small glass sheet (popover on iPad).', android: 'Small ModalBottomSheet; FilledTonalButton.' },
      analytics: ['replay_pin_tapped {type: max_speed|stop}'],
      copy: { Stop: 'Stop · {road} · {time} · {n} s stopped · Stops of 10 seconds or more get a pin. Stopping fully and gently never costs Smoothness.', Max: 'Max speed · {elapsed} into the drive · {time} · The highest speed with a good GPS fix on this drive. Shown as plain text for your own review; it never appears on share cards, records or recaps.', Action: 'Jump to this moment' },
      freePro: 'Same window as Replay.',
      data: 'Stop: trip.stops[stop] (t20261105-1745 stop 0, Spadina Ave, 34 s). Max: trip.maxKmh + trip.maxAt.t. Time of day = trip.start + t (derived).',
      icons: ['clock', 'pinStop', 'replay'],
      assets: [],
    },
  });

  /* ---------- S-RPL-04 · Replay layers sheet ---------- */
  const STYLES = [{ id: 'standard', label: 'Standard' }, { id: 'muted', label: 'Muted' }, { id: 'satellite', label: 'Satellite' }];
  screen('S-RPL-04', {
    name: 'Replay layers sheet',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'drives', detent: 'auto',
    over: (ctx) => ({ id: 'S-RPL-01', params: { trip: tripOf(ctx, 't20261105-1745').id } }),
    states: [{ id: 'default', label: 'Def' }, { id: 'mapStyleOptions', label: 'Map style options' }],
    stateParams: { default: { trip: 't20261105-1745' }, mapStyleOptions: { trip: 't20261105-1745' } },
    render(ctx) {
      const { ui, icons } = ctx, L = RPL_LAYERS;
      const pins = `<span class="lay-pins">${['brake', 'accel', 'corner'].map((ty) => icons.pin(ty, { size: 20 })).join('')}</span>`;
      const rows = ui.list([
        ui.row({ lead: pins, title: 'Event pins', sub: 'Braking, acceleration, cornering', trailing: 'toggle', on: L.events, action: 'layer', arg: 'events' }),
        ui.row({ lead: `<span class="lay-ic">${icons.stopPin({ size: 18 })}</span>`, title: 'Stops', sub: '10 seconds or longer', trailing: 'toggle', on: L.stops, action: 'layer', arg: 'stops' }),
        ui.row({ lead: `<span class="lay-ic">${icons.maxPin({ size: 18 })}</span>`, title: 'Max speed', sub: 'Neutral outline pin', trailing: 'toggle', on: L.max, action: 'layer', arg: 'max' }),
      ]);
      const style = ctx.state === 'mapStyleOptions'
        ? `<div class="lay-styles" role="radiogroup" aria-label="Map style">${STYLES.map((s) => `<span class="lay-st ${L.style === s.id ? 'is-on' : ''} ui-press" role="radio" aria-checked="${L.style === s.id}" ${ui.act({ action: 'style', arg: s.id, haptic: 'selection', noRole: true })}><i class="lay-prev is-${s.id}"><svg viewBox="0 0 96 64" aria-hidden="true"><path d="M-4 50C20 44 30 20 52 24s30 22 48 14" fill="none" stroke="var(--dp-data-route)" stroke-width="4" stroke-linecap="round"/><path d="M10 -4L30 70M-4 18H100" stroke="var(--dp-data-map-road)" stroke-width="5"/></svg></i><b>${s.label}</b>${s.id === 'satellite' ? '<small>Where supported</small>' : ''}</span>`).join('')}</div>`
        : `<div class="lay-seg">${ui.segmented(STYLES, { selected: L.style, action: 'style' })}<span class="lay-link ui-press" ${ui.act({ state: 'mapStyleOptions' })}>Preview styles${ui.icon('chevronRight', 14)}</span></div>`;
      return `${ui.sheetHeader('Map layers', { sub: 'Changes apply right away and are remembered.' })}<div class="lay">${ui.section('Show on the map', rows, { style: 'caption' })}${ui.section('Map style', style, { style: 'caption', footer: 'Satellite depends on the map provider. Offline, the replay uses a plain map of your route.' })}</div>`;
    },
    actions: {
      layer: (ctx, arg) => { RPL_LAYERS[arg] = !RPL_LAYERS[arg]; ctx.haptic(RPL_LAYERS[arg] ? 'toggleOn' : 'toggleOff'); ctx.update(); refreshUnder('S-RPL-01'); return false; },
      style: (ctx, arg) => { RPL_LAYERS.style = arg; ctx.update(); refreshUnder('S-RPL-01'); return false; },
    },
    spec: {
      purpose: 'Hide or show pin types and choose a map style so the replay is less busy (F-RPL-10, P2). Choices apply immediately and persist.',
      layout: ['Medium sheet, auto height. Header “Map layers” + “Changes apply right away and are remembered.”', '“Show on the map” list: Event pins (three pin glyphs) · Stops (stop capsule) · Max speed (outline triangle), each with a toggle.', '“Map style”: glass segmented Standard · Muted · Satellite (Android connected button group) + “Preview styles ›”. mapStyleOptions: three 96×64 preview tiles (radio cards, selected ring in Glide), Satellite marked “Where supported”.', 'Footer: “Satellite depends on the map provider. Offline, the replay uses a plain map of your route.”'],
      components: ['sheetHeader', 'section', 'list', 'row', 'toggle', 'segmented'],
      interactions: ['Toggles hide/show the pin type on S-RPL-01 underneath immediately (and its hit targets); haptic toggleOn/Off.', 'Style segmented / tiles → map style switches underneath (muted = desaturated, satellite = darker imagery placeholder).', 'Preview styles → mapStyleOptions state.'],
      motion: ['Toggle thumb spring.fast; style change cross-fades the map (spring.effects). RM: instant.'],
      a11y: ['Rows: “Event pins, on, switch”. Style tiles are a radio group “Map style”. Tiles ≥ 44 pt.'],
      platformDiffs: { ios: 'Glass medium sheet; Picker(.segmented).', android: 'ModalBottomSheet; M3 Switch with thumb icon; connected ButtonGroup.' },
      analytics: ['replay_layer_toggled {layer}'],
      copy: { Title: 'Map layers', Sub: 'Changes apply right away and are remembered.', Rows: 'Event pins · Braking, acceleration, cornering / Stops · 10 seconds or longer / Max speed · Neutral outline pin', Styles: 'Standard · Muted · Satellite (Where supported)', Footer: 'Satellite depends on the map provider. Offline, the replay uses a plain map of your route.' },
      freePro: 'Free.',
      data: 'Prototype state lives in a module-level object (RPL_LAYERS); natively persist per user.',
      icons: ['chevronRight'],
      assets: [],
    },
  });

  /* ==========================================================================================================
     S-ANL-01 · Analysis workspace (map + synced timeline + G-G)          F-RPL-05/06/03/08 · F-DQ-03/04/05
     A local lane renderer (anlLanes) extends DP_UI.timeline with channel toggles, a distance axis, zoom,
     value bubbles and a range selection. One cursor drives the map marker, the bubbles and the G-dot.
     ========================================================================================================== */
  const ANL_CH = { speed: true, long: true, lat: true, elev: false, jerk: false, quality: false };
  const CHANNELS = [
    { id: 'speed', label: 'Speed', sub: 'Neutral, from GPS', idx: 3, h: 44, col: 'var(--dp-data-speed)', sw: 1.7, unit: 'km/h', fmt: (v) => Math.round(v), pro: false },
    { id: 'long', label: 'Longitudinal G', sub: 'Braking down, accelerating up', idx: 4, h: 64, col: 'var(--dp-data-long-g)', sw: 1.8, zero: true, unit: 'g', fmt: (v) => v.toFixed(2), pro: false },
    { id: 'lat', label: 'Lateral G', sub: 'Turning left and right', idx: 5, h: 64, col: 'var(--dp-data-lat-g)', sw: 1.6, dash: '6 3', zero: true, unit: 'g', fmt: (v) => v.toFixed(2), pro: false },
    { id: 'elev', label: 'Elevation', sub: 'Climbs and descents', idx: 6, h: 34, col: 'var(--dp-data-elevation)', sw: 1.2, area: true, unit: 'm', fmt: (v) => Math.round(v), pro: false },
    { id: 'jerk', label: 'Jerk', sub: 'How sharply G changes', idx: -1, h: 40, col: 'var(--dp-data-jerk)', sw: 1.4, zero: true, unit: 'g/s', fmt: (v) => v.toFixed(2), pro: true },
    { id: 'quality', label: 'Sample quality', sub: 'Observed, estimated, gaps, left out', idx: -2, h: 14, pro: true },
  ];
  const chRange = (t, c) => c.id === 'speed' ? [0, Math.max(120, Math.ceil(t.maxKmh / 20) * 20)] : c.id === 'elev' ? [t.elevMinM - 4, t.elevMaxM + 4] : c.id === 'jerk' ? [-0.3, 0.3] : [-0.6, 0.6];
  const chRangeText = (t, c) => { const [lo, hi] = chRange(t, c); return c.id === 'speed' ? `0–${hi} km/h` : c.id === 'elev' ? `${Math.round(lo)}–${Math.round(hi)} m` : c.id === 'jerk' ? '±0.3 g/s' : '±0.6 g'; };
  function jerkAt(t, s) { const a = U.sampleAt(t, s - 1), b = U.sampleAt(t, s + 1); return ((b.lonG || 0) - (a.lonG || 0)) / 2; }
  function chVal(t, c, s) { const a = U.sampleAt(t, s); if (c.id === 'jerk') return jerkAt(t, s); if (c.id === 'speed' && (a.lat == null || a.kmh == null)) return null; return a[['', '', '', 'kmh', 'lonG', 'latG', 'elevM'][c.idx]]; }
  function tAtKm(t, km) { const c = cum(t); let lo = 0, hi = c.km.length - 1; if (km <= 0) return 0; if (km >= c.km[hi]) return t.durationS; while (hi - lo > 1) { const m = (lo + hi) >> 1; if (c.km[m] <= km) lo = m; else hi = m; } const f = (km - c.km[lo]) / (c.km[hi] - c.km[lo] || 1); return c.ts[lo] + (c.ts[hi] - c.ts[lo]) * f; }
  /** View: {w, axis, zoom, off}. u = 0..1 along the axis; x = (u − off) × zoom × w. */
  function anlView(t, w, axis, zoom, centreS) {
    const uOf = (s) => (axis === 'distance' ? kmAt(t, s) / t.distanceKm : s / t.durationS);
    const off = zoom > 1 ? clamp(uOf(centreS) - 0.5 / zoom, 0, 1 - 1 / zoom) : 0;
    const x = (s) => (uOf(s) - off) * zoom * w;
    const sOfX = (px) => { const u = off + px / (zoom * w); return axis === 'distance' ? tAtKm(t, u * t.distanceKm) : u * t.durationS; };
    return { w, axis, zoom, off, uOf, x, sOfX };
  }
  function anlLanes(ctx, t, V, o = {}) {
    const { ui, icons, data } = ctx, w = V.w, k = ctx.xl ? 1.5 : 1, rows = t.track.rows, q = t.quality.spans, id = ui.nid('anl');
    const chans = CHANNELS.filter((c) => (o.channels || ANL_CH)[c.id] && (!c.pro || ctx.isPro));
    let y = 0, out = '', labels = '', lanes = [];
    for (const c of chans) {
      const h = c.h * (c.id === 'quality' ? 1 : k), top = y + 16;
      labels += `<text x="0" y="${y + 11}" fill="var(--dp-text-2)" style="font:600 10.5px var(--dp-family-text)">${c.label}</text>${c.id === 'quality' ? '' : `<text x="${w}" y="${y + 11}" text-anchor="end" fill="var(--dp-text-3)" style="font:600 9.5px var(--dp-family-numeric);font-stretch:var(--dp-num-stretch)">${chRangeText(t, c)}</text>`}`;
      if (c.id === 'quality') {
        const seg = (a, b, fill, extra = '') => `<rect x="${V.x(a).toFixed(1)}" y="${top}" width="${Math.max(2, V.x(b) - V.x(a)).toFixed(1)}" height="${h}" rx="2" fill="${fill}" ${extra}/>`;
        out += `<rect x="${V.x(0)}" y="${top}" width="${(V.x(t.durationS) - V.x(0)).toFixed(1)}" height="${h}" rx="3" fill="var(--dp-data-route)" opacity=".5"/>`;
        q.estimated.forEach((g) => (out += seg(g[0], g[1], `url(#${id}-dash)`)));
        q.gap.forEach((g) => (out += seg(g[0], g[1], 'var(--card-bg)', 'stroke="var(--dp-data-gap-stroke)" stroke-width="1.2"')));
        q.excluded.forEach((g) => (out += seg(g[0], g[1], `url(#${id}-hatch)`)));
      } else {
        const [lo, hi] = chRange(t, c), Y = (v) => top + h - ((clamp(v, lo, hi) - lo) / (hi - lo)) * h;
        if (c.zero) out += `<line x1="0" x2="${w}" y1="${Y(0).toFixed(1)}" y2="${Y(0).toFixed(1)}" stroke="var(--dp-data-axis)"/>`;
        out += `<line x1="0" x2="${w}" y1="${top + h}" y2="${top + h}" stroke="var(--dp-data-grid)"/>`;
        const est = q.estimated[0] ? q.estimated[0][1] : 0;
        let dm = '', de = '', pen = false, penE = false, prev = null;
        rows.forEach((r, i) => {
          const v = c.id === 'jerk' ? (i ? ((r[4] ?? 0) - (rows[i - 1][4] ?? 0)) / Math.max(1, r[0] - rows[i - 1][0]) : 0) : r[c.idx];
          if (v == null) { pen = false; penE = false; return; }
          const pt = `${V.x(r[0]).toFixed(1)} ${Y(v).toFixed(1)}`;
          if (r[0] <= est + 0.01) { de += `${penE ? 'L' : 'M'}${pt}`; penE = true; if (rows[i + 1] && rows[i + 1][0] > est) { dm += `M${pt}`; pen = true; } }
          else { dm += `${pen ? 'L' : 'M'}${pt}`; pen = true; }
          prev = pt;
        });
        if (c.area) out += `<path d="${dm}L${V.x(t.durationS).toFixed(1)} ${top + h}L${V.x(est).toFixed(1)} ${top + h}Z" fill="var(--dp-data-elevation-fill)"/>`;
        out += `<path d="${dm}" fill="none" stroke="${c.col}" stroke-width="${c.sw}" ${c.dash ? `stroke-dasharray="${c.dash}"` : ''} stroke-linejoin="round"/>`;
        if (de) out += `<path d="${de}" fill="none" stroke="${c.col}" stroke-width="${c.sw}" stroke-dasharray="4 4" opacity=".6"/>`;
      }
      lanes.push({ id: c.id, top, h });
      y += h + 24;
    }
    const H = y;
    // quality bands across every lane
    let bands = '';
    q.excluded.forEach((g) => { bands += `<rect x="${V.x(g[0]).toFixed(1)}" y="0" width="${(V.x(g[1]) - V.x(g[0])).toFixed(1)}" height="${H}" fill="url(#${id}-hatch)"/><text x="${(V.x(g[0]) + 4).toFixed(1)}" y="${H - 4}" fill="var(--dp-text-2)" style="font:600 9px var(--dp-family-text)">Phone moved — not scored</text>`; });
    q.gap.forEach((g) => { bands += `<rect x="${V.x(g[0]).toFixed(1)}" y="0" width="${Math.max(3, V.x(g[1]) - V.x(g[0])).toFixed(1)}" height="${H}" fill="var(--dp-fill)"/><text x="${(V.x(g[0]) - 3).toFixed(1)}" y="${H - 4}" text-anchor="end" fill="var(--dp-text-2)" style="font:600 9px var(--dp-family-text)">No GPS</text>`; });
    if (o.range) bands += `<rect class="anl-range" x="${V.x(o.range[0]).toFixed(1)}" y="0" width="${(V.x(o.range[1]) - V.x(o.range[0])).toFixed(1)}" height="${H}" fill="var(--dp-accent)" opacity=".14"/>`;
    // event lane
    let evs = '';
    for (const e of t.events) { const ex = V.x(e.t); if (ex < -8 || ex > w + 8) continue; evs += `<g transform="translate(${(ex - 8).toFixed(1)} ${H - 2})">${icons.pin(e.type, { size: 16, needed: e.needed })}</g>`; }
    const EH = H + 22;
    // axis ticks
    let ticks = '';
    for (let i = 0; i <= 4; i++) { const px = (i / 4) * w, s = V.sOfX(px); const lab = V.axis === 'distance' ? `${data.km(kmAt(t, s))} km` : data.clock(s); ticks += `<text x="${px.toFixed(1)}" y="${EH + 14}" text-anchor="${i === 0 ? 'start' : i === 4 ? 'end' : 'middle'}" fill="var(--dp-text-3)" style="font:600 9.5px var(--dp-family-numeric);font-stretch:var(--dp-num-stretch)">${lab}</text>`; }
    // cursor + bubbles
    const cs = o.cursor ?? 0, cx = V.x(cs);
    const bub = lanes.filter((l) => l.id !== 'quality').map((l) => { const c = CHANNELS.find((x) => x.id === l.id), v = chVal(t, c, cs); return `<text class="anl-bub" data-k="${l.id}" x="8" y="${l.top + 12}" fill="var(--dp-text)" style="font:650 11px var(--dp-family-numeric);font-stretch:var(--dp-num-stretch);paint-order:stroke;stroke:var(--card-bg);stroke-width:4px;stroke-linejoin:round">${v == null ? '—' : c.fmt(v)} ${v == null ? '' : c.unit}</text>`; }).join('');
    const cur = `<g class="anl-cur" transform="translate(${cx.toFixed(1)} 0)"><line x1="0" x2="0" y1="0" y2="${EH}" stroke="var(--dp-data-cursor)" stroke-width="1.5"/><circle class="anl-knob" cx="0" cy="${EH + 1}" r="5" fill="var(--dp-data-cursor)"/>${bub}</g>`;
    const defs = `<defs><clipPath id="${id}-c"><rect x="-2" y="-2" width="${w + 4}" height="${EH + 24}"/></clipPath><pattern id="${id}-hatch" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="2" height="6" fill="var(--dp-data-excluded-hatch)"/></pattern><pattern id="${id}-dash" width="8" height="4" patternUnits="userSpaceOnUse"><rect width="4" height="4" fill="var(--dp-data-route)"/></pattern></defs>`;
    const aria = `Timeline, ${Math.round(t.durationS / 60)} minutes${V.axis === 'distance' ? `, by distance over ${t.distanceKm} kilometres` : ''}. Speed peaked at ${t.maxKmh} kilometres per hour. ${t.events.length ? `${t.events.length} events, the largest ${Math.max(...t.events.map((e) => e.peakG)).toFixed(2)} g at ${plainLabel(t.events.slice().sort((a, b) => b.peakG - a.peakG)[0].place)}.` : 'No events.'}${q.excluded.length ? ` ${t.quality.excludedMin} minutes left out.` : ''}${q.gap.length ? ' One GPS gap.' : ''}`;
    return { html: `<svg class="anl-svg" width="${w}" height="${EH + 20}" viewBox="0 0 ${w} ${EH + 20}" role="img" aria-label="${ui.esc(aria)}" style="overflow:visible">${defs}<g clip-path="url(#${id}-c)">${bands}${out}${evs}${ticks}${cur}</g>${labels}</svg>`, lanes, EH, H };
  }
  const ANL_STATES = [
    { id: 'default', label: 'Def' }, { id: 'cursorDragging', label: 'Cursor dragging' }, { id: 'zoomed', label: 'Zoomed' }, { id: 'timeVsDistanceAxis', label: 'Time vs distance axis' },
    { id: 'Part', label: 'Part (hatched/excluded)' }, { id: 'Lock', label: 'Lock (Free, > 30 days)' }, { id: 'Load', label: 'Load' }, { id: 'landscape', label: 'Landscape' },
  ];
  const ANL_PARAMS = {
    default: { trip: 't20261105-1745' }, cursorDragging: { trip: 't20261020-0740' }, zoomed: { trip: 't20261105-1745' }, timeVsDistanceAxis: { trip: 't20261105-1745' },
    Part: { trip: 't20261027-1728' }, Lock: { trip: 't20261002-0745', ent: 'free' }, Load: { trip: 't20261105-1745' }, landscape: { trip: 't20261027-1728' },
  };
  function anlInit(ctx, t) {
    const st = ctx.state, S = ctx.store, e0 = t.events[0];
    const cursor = S.cursor ?? (ctx.params.at != null ? +ctx.params.at : st === 'cursorDragging' ? t.durationS * 0.36 : st === 'zoomed' && t.events[1] ? t.events[1].t : st === 'Part' || st === 'landscape' ? t.durationS * 0.36 : e0 ? e0.t : t.durationS * 0.4);
    return { cursor, axis: S.axis || (st === 'timeVsDistanceAxis' ? 'distance' : 'time'), zoom: S.zoom || (st === 'zoomed' ? 4 : 1) };
  }

  screen('S-ANL-01', {
    name: 'Analysis workspace (map + synced timeline + G-G)',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'push', tab: 'drives',
    states: ANL_STATES, stateParams: ANL_PARAMS,
    stateAliases: { dragging: 'cursorDragging', distance: 'timeVsDistanceAxis' },
    chrome: (ctx) => (ctx.state === 'landscape' ? { tabBar: false, statusBar: false, island: false } : { tabBar: false }),
    backTo: 'S-TRP-01',
    render(ctx) {
      const { ui, data, icons, state } = ctx, esc = ui.esc;
      const t = tripOf(ctx, 't20261105-1745');
      const land = state === 'landscape';
      const W = land ? Math.round((ctx.size.h - 120) * 0.7 - 14) : ctx.size.w - 32;
      const navO = { title: 'Analysis', subtitle: `${t.partOfDay} · ${t.label}`, trailing: [ui.iconButton('filter', { nav: 'S-ANL-03', params: { trip: t.id }, label: 'Channels' }), ui.iconButton('replay', { nav: 'S-RPL-01', params: { trip: t.id }, label: 'Open Replay' })] };
      const locked = state === 'Lock' || data.isLocked(t, ctx.ent);
      if (state === 'Load') {
        return ui.page(`<div class="anl" aria-busy="true"><div class="ui-pad">${ui.skeleton('block', 180)}</div><div class="ui-pad anl-sk">${[44, 64, 64].map((h) => `<span class="ui-skel" style="width:30%"></span><span class="ui-skel" style="height:${h}px;border-radius:10px"></span>`).join('')}</div><div class="ui-pad anl-sk2"><span class="ui-skel" style="width:150px;height:150px;border-radius:50%"></span>${ui.skeleton('lines', 3)}</div><p class="anl-loadt">${ui.loader({ size: 22 })}Preparing ${data.min(t.durationS)} of channels…</p></div>`, { nav: navO });
      }
      const I0 = anlInit(ctx, t);
      const V = anlView(t, W, I0.axis, I0.zoom, I0.cursor);
      const L = anlLanes(ctx, t, V, { cursor: I0.cursor });
      const mh = land ? 120 : 176, mw = land ? Math.round((ctx.size.h - 120) * 0.3) : W;
      const P = projector(t, mw, mh, 16);
      const mk = markerPos(t, P, I0.cursor);
      const map = ui.map(t, { w: mw, h: mh, pad: 16, others: true, pinSize: 20, cls: 'anl-map', overlay: `${markerHtml(markerStyle(mk))}<span class="anl-mapq">${ui.qualityBadge(t.quality.badge, { glass: true, text: t.quality.badge, nav: 'S-DQ-01', params: { trip: t.id } })}</span>`, label: `Map of your drive, ${t.distanceKm} kilometres. Tap to move the cursor.` });
      const a = U.sampleAt(t, I0.cursor);
      const ev = t.events;
      const hits = ev.map((e) => { const x = V.x(e.t); if (x < -8 || x > W + 8) return ''; return `<span class="anl-hit" style="left:${clamp(x, 24, W - 24).toFixed(1)}px;top:${(L.H + 6).toFixed(1)}px" ${ui.act({ nav: 'S-RPL-02', params: { trip: t.id, event: e.id }, label: `${evLabel(ctx, e.type)} at ${plainLabel(e.place)}, ${data.clock(e.t)} into the drive. Opens the evidence` })}></span>`; }).join('');
      const zoomCtl = `<span class="anl-zoom">${ui.iconButton('minus', { svg: ui.icon('minus', 18), glass: false, tonal: true, action: 'zoom', arg: '-1', label: 'Zoom out', disabled: I0.zoom <= 1 })}<b>${I0.zoom}×</b>${ui.iconButton('plus', { svg: ui.icon('plus', 18), glass: false, tonal: true, action: 'zoom', arg: '1', label: 'Zoom in', disabled: I0.zoom >= 16 })}</span>`;
      const tools = `<div class="anl-tools">${ui.segmented([{ id: 'time', label: 'Time' }, { id: 'distance', label: 'Distance' }], { selected: I0.axis, action: 'axis', cls: 'anl-axis' })}${zoomCtl}</div>`;
      const read = `<div class="anl-read" aria-hidden="true"><span><b data-r="time">${data.clock(I0.cursor)}</b> elapsed</span><i>·</i><span><b data-r="dist">${data.km(kmAt(t, I0.cursor))}</b> km</span><span class="anl-span" data-r="span" ${spanAt(t, I0.cursor) ? '' : 'hidden'}>${spanAt(t, I0.cursor) ? SPAN_CHIP[spanAt(t, I0.cursor)][0] : ''}</span></div>`;
      const legend = `<div class="anl-leg">${[ANL_CH.speed && '<span><i class="is-sp"></i>Speed</span>', ANL_CH.long && '<span><i class="is-lo"></i>Long G</span>', ANL_CH.lat && '<span><i class="is-la"></i>Lat G</span>', ANL_CH.elev && '<span><i class="is-el"></i>Elevation</span>', ANL_CH.jerk && ctx.isPro && '<span><i class="is-jk"></i>Jerk</span>', '<span><i class="wc-sw is-est"></i>Estimated</span>', t.quality.spans.gap.length && '<span><i class="wc-sw is-gap"></i>No GPS</span>', t.quality.spans.excluded.length && '<span><i class="wc-sw is-exc"></i>Not scored</span>'].filter(Boolean).join('')}</div>`;
      const chart = `<div class="anl-chart ${state === 'cursorDragging' ? 'is-drag' : ''}" data-zoom="${I0.zoom}">${L.html}${hits}</div>`;
      const s = t.smoothness;
      const tiles = ui.stats([
        s ? { label: 'Smoothness', value: s.score, prov: 'derived', sub: t.quality.excludedMin ? `${icons.quality(t.quality.badge, { size: 11 })} Based on ${t.quality.scoredMin} of ${t.quality.totalMin} min` : esc(s.compareText.replace(' vs your usual', ' vs usual')) } : { label: 'Smoothness', value: '—', unavailable: true },
        { label: 'Max speed', value: t.maxKmh, unit: 'km/h', unitSpoken: 'kilometres per hour', prov: 'derived' },
        { label: 'Events', value: ev.length, prov: 'derived', sub: ev.length ? ['brake', 'accel', 'corner'].map((ty) => { const n = ev.filter((e) => e.type === ty).length; return n ? `${icons.pin(ty, { size: 12 })}${n}` : ''; }).join(' ') : 'No abrupt inputs' },
      ], { cols: 1, cls: 'anl-tiles' });
      const gg = `<div class="anl-gg ui-card ui-press" ${ui.act({ nav: 'S-ANL-02', params: { trip: t.id }, zoom: true, label: `${ggSummary(ctx, t)} Opens the expanded friction circle` })}>${ggSvg(ctx, t, land ? 150 : 158, { dot: [a.latG || 0, a.lonG || 0] })}<span class="anl-gg-x">${ui.icon('chevronRight', 14)}</span></div>`;
      const range = `<div class="anl-rangebar ui-card ui-press" ${ui.act({ nav: 'S-ANL-04', params: { trip: t.id, t0: Math.round(t.cleanest.t0), t1: Math.round(t.cleanest.t1) } })}><span class="anl-rb-ic">${ui.icon('analysis', 18)}</span><span><b>Select a range</b><small>Long-press and drag on the timeline${ctx.isPro ? '' : ' · Pro'}</small></span>${ctx.isPro ? ui.icon('chevronRight', 16) : ui.proBadge()}</div>`;
      const body = locked
        ? `<div class="ui-pad">${ui.lockPanel(`<div class="anl-lockin">${map}${L.html}</div>`, { text: `Your ${data.date(t.start)} drive · ${ctx.D.entitlement.lockSheets.oldAnalysis}`, nav: 'S-SUB-03/replayAnalysis30Days', params: { trip: t.id } })}</div><p class="anl-foot">Analysis for drives older than 30 days is part of Pro. Your summary, score and Story stay free forever, and nothing is deleted.</p><div class="ui-pad">${ui.button('Back to the summary', { kind: 'tonal', block: true, nav: 'S-TRP-01', params: { trip: t.id } })}</div>`
        : land
          ? `<div class="anl-land"><div class="anl-land-l">${map}${gg}</div><div class="anl-land-r">${tools}${read}${chart}${legend}</div></div>`
          : `<div class="anl">${`<div class="ui-pad">${map}</div>`}<div class="ui-pad">${tools}</div><div class="ui-pad">${read}${ui.card(chart, { cls: 'anl-card' })}${legend}</div><div class="ui-pad anl-bottom">${gg}${tiles}</div><div class="ui-pad">${range}</div><p class="anl-foot">${ui.icon('info', 13)} Long-press any number to see how it was measured. Drag the timeline to move the cursor; pinch or use − / + to zoom.</p></div>`;
      if (land) return `<div class="ui-page anl-lpage" style="width:${ctx.size.h}px;height:${ctx.size.w}px;left:${(ctx.size.w - ctx.size.h) / 2}px;top:${(ctx.size.h - ctx.size.w) / 2}px"><div class="anl-lbar">${ui.backButton()}<b>Analysis</b><span>${esc(t.partOfDay)} · ${esc(t.label)}</span>${navO.trailing.join('')}</div>${body}</div>`;
      return ui.page(body, { nav: navO });
    },
    mount(el, ctx) {
      const chart = el.querySelector('.anl-chart'); if (!chart) return;
      const t = tripOf(ctx, 't20261105-1745'), S = ctx.store, data = ctx.data, land = ctx.state === 'landscape';
      const I0 = anlInit(ctx, t), W = land ? Math.round((ctx.size.h - 120) * 0.7 - 14) : ctx.size.w - 32;
      const V = anlView(t, W, I0.axis, I0.zoom, I0.cursor);
      const mh = land ? 120 : 176, mw = land ? Math.round((ctx.size.h - 120) * 0.3) : W, P = projector(t, mw, mh, 16);
      const svg = chart.querySelector('.anl-svg'), cur = svg.querySelector('.anl-cur'), mk = el.querySelector('.anl-map .wc-mk');
      const gsv = el.querySelector('.anl-gg .wc-gg'), gdot = gsv && gsv.querySelector('.gg-dot'), gk = gsv ? +gsv.dataset.k : 0, gc = gsv ? +gsv.dataset.c : 0;
      const rT = el.querySelector('[data-r="time"]'), rD = el.querySelector('[data-r="dist"]'), rS = el.querySelector('[data-r="span"]');
      S.cursor = I0.cursor;
      function setCursor(s) {
        s = clamp(s, 0, t.durationS); S.cursor = s;
        const x = V.x(s); cur.setAttribute('transform', `translate(${x.toFixed(1)} 0)`);
        const flip = x > W - 70;
        cur.querySelectorAll('.anl-bub').forEach((b) => { const c = CHANNELS.find((z) => z.id === b.dataset.k), v = chVal(t, c, s); b.textContent = v == null ? '—' : `${c.fmt(v)} ${c.unit}`; b.setAttribute('x', flip ? -8 : 8); b.setAttribute('text-anchor', flip ? 'end' : 'start'); });
        applyMarker(mk, markerPos(t, P, s));
        if (gdot) { const a = U.sampleAt(t, s); let la = a.latG || 0, lo = a.lonG || 0; const m = Math.hypot(la, lo); if (m > 0.6) { la *= 0.6 / m; lo *= 0.6 / m; } gdot.setAttribute('transform', `translate(${(gc + la * gk).toFixed(1)} ${(gc - lo * gk).toFixed(1)})`); }
        if (rT) rT.textContent = data.clock(s); if (rD) rD.textContent = data.km(kmAt(t, s));
        if (rS) { const sp = spanAt(t, s); rS.hidden = !sp; rS.textContent = sp ? SPAN_CHIP[sp][0] : ''; }
      }
      setCursor(S.cursor);
      // drag the cursor (≤ 100 ms sync), snap to event ticks within 8 px, long-press + drag = range (→ S-ANL-04)
      let drag = null, lp = 0;
      const sx = (e) => { const r = svg.getBoundingClientRect(); return ((e.clientX - r.left) / r.width) * W; };
      const down = (e) => { if (e.target.closest('.anl-hit')) return; drag = { snapped: null, range: null, x0: sx(e) }; chart.classList.add('is-drag'); chart.setPointerCapture?.(e.pointerId); move(e); lp = setTimeout(() => { if (drag) { drag.range = [V.sOfX(drag.x0), V.sOfX(drag.x0)]; ctx.haptic('longPress'); chart.classList.add('is-range'); } }, 450); };
      const move = (e) => {
        if (!drag) return; const px = sx(e); if (Math.abs(px - drag.x0) > 6 && !drag.range) clearTimeout(lp);
        let s = V.sOfX(clamp(px, 0, W)); const near = t.events.find((ev) => Math.abs(V.x(ev.t) - px) < 8);
        if (near) { s = near.t; if (drag.snapped !== near.id) { ctx.haptic('selection'); drag.snapped = near.id; } } else drag.snapped = null;
        if (drag.range) { drag.range[1] = s; let band = svg.querySelector('.anl-range'); if (!band) { band = document.createElementNS('http://www.w3.org/2000/svg', 'rect'); band.setAttribute('class', 'anl-range'); band.setAttribute('y', 0); band.setAttribute('height', svg.viewBox.baseVal.height - 20); band.setAttribute('fill', 'var(--dp-accent)'); band.setAttribute('opacity', '.16'); svg.querySelector('g').prepend(band); } const [a, b] = [Math.min(...drag.range), Math.max(...drag.range)]; band.setAttribute('x', V.x(a)); band.setAttribute('width', Math.max(2, V.x(b) - V.x(a))); }
        setCursor(s);
      };
      const up = () => { clearTimeout(lp); if (!drag) return; const r = drag.range; drag = null; chart.classList.remove('is-drag', 'is-range'); if (r && Math.abs(r[1] - r[0]) > 20) ctx.nav('S-ANL-04', { trip: t.id, t0: Math.round(Math.min(...r)), t1: Math.round(Math.max(...r)) }); };
      const dbl = () => { if (S.zoom > 1 || I0.zoom > 1) { S.zoom = 1; ctx.update(); } };
      chart.addEventListener('pointerdown', down); chart.addEventListener('pointermove', move); chart.addEventListener('pointerup', up); chart.addEventListener('pointercancel', up); chart.addEventListener('dblclick', dbl);
      // tap the map → snap the cursor to the nearest point
      const mapEl = el.querySelector('.anl-map');
      const mtap = (e) => { if (e.target.closest('.ui-chip')) return; const r = mapEl.getBoundingClientRect(), x = ((e.clientX - r.left) / r.width) * mw, y = ((e.clientY - r.top) / r.height) * mh; let best = null, bd = 1e9; for (const row of t.track.rows) { if (row[1] == null) continue; const [px, py] = P(row[1], row[2]); const d = (px - x) ** 2 + (py - y) ** 2; if (d < bd) { bd = d; best = row; } } if (best) { ctx.haptic('selection'); setCursor(best[0]); } };
      if (mapEl) mapEl.addEventListener('click', mtap);
      if (!still(ctx, el)) { svg.querySelectorAll('g > path[stroke]').forEach((p) => p.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 420, easing: 'ease-out' })); ctx.ui.anim.rise(el, '.anl > .ui-pad', { stagger: 30 }); }
      return () => { clearTimeout(lp); };
    },
    actions: {
      axis: (ctx, arg) => { ctx.store.axis = arg; ctx.update(); return false; },
      zoom: (ctx, arg) => { const z = (ctx.store.zoom || anlInit(ctx, tripOf(ctx, 't20261105-1745')).zoom); ctx.store.zoom = clamp(arg === '1' ? z * 2 : z / 2, 1, 16); ctx.haptic('selection'); ctx.update(); return false; },
    },
    spec: {
      purpose: 'Study the drive: speed, longitudinal G and lateral G on one timeline synced with the map and the G-G circle (F-RPL-05/06), with honest quality overlays (F-RPL-08). Free for the last 30 days; older drives are Pro (Gate 1 #6).',
      layout: [
        'Immersive push (no tab bar). iOS inline title “Analysis” + part of day · label, glass Channels (→ S-ANL-03) and Replay buttons · Android MediumFlexibleTopAppBar.',
        'Map card (full width − 32, 176 tall, radius card) with the cursor marker; quality badge (glass) top-left → S-DQ-01. Tap the map to snap the cursor.',
        'Toolbar: Time / Distance segmented (iOS glass segmented · Android connected button group) + zoom stepper (− 1× +, tonal 36).',
        'Cursor readout row: “12:04 elapsed · 9.8 km” + the span chip when the cursor is in an estimated, gap or excluded span.',
        'Timeline card: lanes share one x-axis — Speed 44 (neutral `--dp-data-speed`, 0–120 km/h), Longitudinal G 64 (±0.6, zero line, sky solid), Lateral G 64 (±0.6, amber dashed 6/3), optional Elevation 34 (area), Pro Jerk 40 and Sample quality 14 — each with a label (10.5/600 text2) and range (9.5 numerals text3); event lane (pins 16) aligned to the map pins; 5 x-axis ticks (time or km); cursor 1.5 px `--dp-data-cursor` with a value bubble per lane (numerals 11 with a card-colour halo).',
        'Quality overlay: estimated start dashed at 60% · GPS gap = line break + filled band + “No GPS” · excluded = 45° hatch band + “Phone moved — not scored”.',
        'Legend row (swatch + pattern): Speed · Long G · Lat G · (Elevation, Jerk) · Estimated · No GPS · Not scored.',
        'Bottom: G-G card 158 (cloud, envelope, cursor dot; tap → S-ANL-02 zoom) beside three stat tiles: Smoothness (with “Based on 31 of 34 min”), Max speed (neutral), Events (count by pin).',
        '“Select a range” card (Pro) → S-ANL-04; ⓘ footnote for long-press provenance and gestures.',
        'Lock: this trip’s own map + lanes blurred 14 px with lock + “Your 2 Oct drive · Keep exploring every drive with Pro.” + See plans, then “Back to the summary”. Load: skeletons that mirror the lanes + loader line. Landscape: timeline full width, map shrinks to a 30% inset with the G-G under it.',
      ],
      components: ['navBar', 'map', 'qualityBadge', 'segmented', 'iconButton', 'card', 'stats', 'lockPanel', 'skeleton', 'loader', 'proBadge', 'anlLanes (local timeline)', 'gg (local ggSvg)'],
      interactions: [
        'Drag on the timeline: the cursor follows the finger and the map marker, lane bubbles, readout row and G-dot update in the same frame (≤ 100 ms, F-RPL-05.2); within 8 px of an event tick it snaps (haptic selection).',
        'Tap the map → snaps the cursor to the nearest track point (haptic selection).',
        'Pinch (native) or − / + → zoom 1× … 16× around the cursor; event ticks stay aligned; double-tap resets.',
        'Time / Distance toggles the x-axis (persisted per user).',
        'Long-press 450 ms then drag → range band; release → S-ANL-04 with t0/t1 (Pro; Free sees the lock there).',
        'Event tick → S-RPL-02 · G-G card → S-ANL-02 (zoom / container transform) · Channels → S-ANL-03 (choice persists) · Replay → S-RPL-01 · quality badge → S-DQ-01 · Lock → S-SUB-03 replayAnalysis30Days.',
      ],
      motion: ['Hero: the cursor. It tracks the finger with no easing (data must not lag); the G-dot uses shared.scrubber (ζ 1, k 900) natively so it never overshoots a value the car didn’t reach.', 'Enter: lanes fade in 420 ms; sections rise 8 px (30 ms stagger).', 'Zoom / axis change: lanes cross-fade (spring.effects). RM: instant, no fades.'],
      a11y: [
        'Timeline summary: “Timeline, 33 minutes. Speed peaked at 103 kilometres per hour. 2 events, the largest 0.44 g at Kerr St.” iOS Audio Graph (`accessibilityChartDescriptor`, one series per channel); Android “View as table” action (time, speed, long-G, lat-G at 30 s steps + every event).',
        'Event ticks are buttons: “Hard braking at the Gardiner, 10:41 into the drive. Opens the evidence.” (44/48 hit area).',
        'G-G card: “Friction circle. 86% of your inputs stayed within 0.2 g. Peaks: braking 0.44 g at Kerr St.” Speed tiles are neutral and never first.',
        'XL: lanes grow 1.5×, the legend wraps, readouts stay in the row above the chart; tiles stack under the G-G.',
        'Patterns, not colour: dashed lat-G, hatched excluded, broken gap, dashed estimated; legend shows swatch + pattern.',
      ],
      platformDiffs: { ios: 'Swift Charts (LineMark / AreaMark / RuleMark cursor with `chartXSelection`), glass segmented and toolbar buttons, Audio Graph descriptor.', android: 'Compose Canvas (or Vico) with the same marks; connected ButtonGroup for Time/Distance; tonal icon buttons; MediumFlexibleTopAppBar; “View as table” custom accessibility action.' },
      analytics: ['analysis_opened {source}', 'analysis_axis {time|distance}', 'analysis_range_selected', 'gg_expanded', 'locked_content_viewed {type: analysis, age_bucket}', 'provenance_viewed {metric}'],
      copy: { Title: 'Analysis · {partOfDay} · {label}', Readout: '{clock} elapsed · {km} km', Lanes: 'Speed 0–120 km/h · Longitudinal G ±0.6 g · Lateral G ±0.6 g · Elevation · Jerk ±0.3 g/s · Sample quality', Overlays: 'No GPS · Phone moved — not scored', Range: 'Select a range · Long-press and drag on the timeline', Footnote: 'Long-press any number to see how it was measured. Drag the timeline to move the cursor; pinch or use − / + to zoom.', Lock: 'Your {date} drive · Keep exploring every drive with Pro. · Analysis for drives older than 30 days is part of Pro. Your summary, score and Story stay free forever, and nothing is deleted.', Load: 'Preparing {duration} of channels…' },
      freePro: 'Free: Analysis for drives in the last 30 days with Speed, Long G, Lat G and Elevation. Pro: every drive, plus Jerk and Sample quality channels and range statistics (F-RPL-09). Locked drives show their own blurred lanes (F-HIS-03).',
      data: 'States → trips: default/zoomed/timeVsDistanceAxis/Load t20261105-1745 · cursorDragging t20261020-0740 (cursor at 36%) · Part/landscape t20261027-1728 (excluded 1070–1254 s) · Lock t20261002-0745 (ent=free). Lane values are trip.track rows; jerk is derived (ΔlonG/Δt between rows); distance axis and the km readout are derived by integrating track positions (scaled to trip.distanceKm); G-G from trip.gg.',
      icons: ['filter', 'replay', 'analysis', 'minus', 'plus', 'chevronRight', 'info', 'lock'],
      assets: [],
    },
  });

  /* ---------- S-ANL-02 · G-G circle, expanded ---------- */
  screen('S-ANL-02', {
    name: 'G-G circle, expanded',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'full', tab: 'drives',
    states: [{ id: 'default', label: 'Def' }, { id: 'filteredByEventType', label: 'Filtered by event type' }, { id: 'Part', label: 'Part' }],
    stateParams: { default: { trip: 't20261105-1745' }, filteredByEventType: { trip: 't20261010-1015', filter: 'corner' }, Part: { trip: 't20261027-1728' } },
    render(ctx) {
      const { ui, data, icons } = ctx, esc = ui.esc;
      const t = tripOf(ctx, 't20261105-1745');
      const f = ctx.store.filter ?? ctx.params.filter ?? null;
      const s = Math.min(ctx.size.w - 40, 372);
      const opts = [{ id: 'all', label: 'All' }, { id: 'brake', label: 'Braking' }, { id: 'accel', label: 'Accel' }, { id: 'corner', label: 'Cornering' }];
      const chips = `<div class="gge-f" role="tablist" aria-label="Filter by input">${opts.map((o) => ui.chip(o.label, { kind: 'filter', selected: (f || 'all') === o.id, icon: o.id === 'all' ? null : icons.pin(o.id, { size: 16 }), action: 'filter', arg: o.id, haptic: 'selection' })).join('')}</div>`;
      const pts = t.gg ? t.gg.points : [];
      const share = (ty) => pts.filter(([a, b]) => ggClass(a, b) === ty && Math.hypot(a, b) > 0.05).length;
      const within = pts.length ? Math.round((pts.filter(([a, b]) => Math.hypot(a, b) <= 0.2).length / pts.length) * 100) : 0;
      const peaks = ['brake', 'accel', 'corner'].map((ty) => { const list = t.events.filter((e) => e.type === ty).sort((a, b) => b.peakG - a.peakG); const e = list[0]; return ui.row({ lead: icons.pin(ty, { size: 26, needed: e && e.needed }), title: evLabel(ctx, ty), sub: e ? `${list.length} on this drive · peak at ${cap1(e.place)}` : 'None on this drive', value: e ? `<b class="ui-num">${e.peakG.toFixed(2)}<span class="ui-unit">g</span></b>` : '—', ...(e ? { nav: 'S-RPL-02', params: { trip: t.id, event: e.id } } : {}), cls: f && f !== ty ? 'is-dim' : '' }); });
      const part = t.quality.spans.excluded.length ? `<div class="ui-pad">${ui.banner({ kind: 'info', icon: icons.quality(t.quality.badge, { size: 20 }), title: `${t.quality.excludedMin} min left out: phone moved`, text: 'Those moments aren’t plotted, so the circle only shows what we could measure fairly.', action: { text: 'Details', nav: 'S-DQ-01/limited', params: { trip: t.id } } })}</div>` : '';
      const body = `<div class="gge">${part}<div class="ui-pad">${ui.card(`<div class="gge-c">${ggSvg(ctx, t, s, { full: true, events: true, filter: f && f !== 'all' ? f : null, pad: 18 })}</div><div class="gge-k"><b class="ui-num">${within}<span class="ui-unit">%</span></b><span>of your inputs stayed within 0.2 g${f && f !== 'all' ? ` · showing ${opts.find((o) => o.id === f).label.toLowerCase()} (${share(f)} moments)` : ''}</span></div>`, { cls: 'gge-card' })}</div>
        <div class="ui-pad">${chips}</div>
        ${ui.section('Peaks on this drive', ui.list(peaks), { style: 'caption', footer: 'Peak G is for your own review. It never appears on share cards, records or Wrapped.' })}
        ${ui.section('How to read it', `<div class="ui-pad gge-how"><p>Each dot is a moment of the drive: up is accelerating, down is braking, left and right are turns. The outline is your envelope, the furthest you went in each direction.</p><p>Smoother drives stay close to the middle. The scale is fixed at ±0.6 g so drives stay comparable.</p></div>`, { style: 'caption' })}</div>`;
      return ui.page(body, { nav: { title: 'Friction circle', subtitle: `${t.partOfDay} · ${t.label}`, close: true } });
    },
    mount(el, ctx) {
      if (still(ctx, el)) return;
      const cloud = el.querySelector('.gg-cloud'), env = el.querySelector('.gg-env');
      if (cloud) cloud.animate([{ opacity: 0, transform: 'scale(.6)' }, { opacity: 1, transform: 'scale(1)' }], { duration: 520, easing: 'cubic-bezier(.16,1,.3,1)', transformOrigin: 'center' });
      if (cloud) cloud.style.transformOrigin = 'center'; if (cloud) cloud.style.transformBox = 'view-box';
      if (env) ctx.motion.drawOn(env, { duration: 600, delay: 300 });
    },
    actions: { filter: (ctx, arg) => { ctx.store.filter = arg === 'all' ? '' : arg; ctx.update(); return false; } },
    spec: {
      purpose: 'A full-screen friction circle (G-G plot) so an enthusiast can see how composed the inputs were, and filter by braking, acceleration or cornering (F-RPL-06.2).',
      layout: ['Full-screen cover (zoom from the G-G card on S-ANL-01); close button; title “Friction circle” + part of day · label.', 'Card with the G-G at full width − 40 (≤ 372): rings every 0.1 g all labelled (0.1–0.5 g), axes labelled “Accel ↑ · Brake ↓ · ← Left · Right →”, cloud dots r 2 at 38%, envelope 1.6 px, event pins 16 at their peaks; under it “86% of your inputs stayed within 0.2 g”.', 'Filter chips: All · Braking · Accel · Cornering (pin glyph in each); non-matching dots drop to 6%.', 'Part: info banner “3 min left out: phone moved” (excluded spans are not plotted).', '“Peaks on this drive” list: per type count, place of the peak and peak G (numerals) → S-RPL-02; footer on personal-only use of peak G.', '“How to read it” two short paragraphs.'],
      components: ['navBar', 'card', 'chip', 'banner', 'section', 'list', 'row', 'gg (local ggSvg)'],
      interactions: ['Filter chip → re-plots with the selected class emphasised (haptic selection).', 'Peak row → S-RPL-02 for that event.', 'Close / swipe down → back to S-ANL-01 (reverse zoom).', 'Part banner Details → S-DQ-01/limited.'],
      motion: ['Enter: the cloud grows from the centre (scale 0.6 → 1, 520 ms ease.countUp) and the envelope draws on (600 ms). Filter change cross-fades dots. RM: static.'],
      a11y: ['Chart descriptor (scatter, x lateral, y longitudinal): “Friction circle. 86% of your inputs stayed within 0.2 g. Peaks: braking 0.44 g at Kerr St.” Filter chips are a tab list.', 'Colour is not the only cue: pins keep their shapes; the dim state is also stated in the caption (“showing cornering, 31 moments”).'],
      platformDiffs: { ios: 'fullScreenCover with zoom transition from the card; glass close button; filter chips as glass capsules.', android: 'Full-screen dialog with container transform; FilterChips with leading pin glyphs; ListItems in a surface-container section.' },
      analytics: ['gg_expanded', 'gg_filter {type}'],
      copy: { Caption: '{n}% of your inputs stayed within 0.2 g', Filters: 'All · Braking · Accel · Cornering', Part: '{n} min left out: phone moved. Those moments aren’t plotted, so the circle only shows what we could measure fairly.', Footer: 'Peak G is for your own review. It never appears on share cards, records or Wrapped.', How: 'Each dot is a moment of the drive: up is accelerating, down is braking, left and right are turns. The outline is your envelope, the furthest you went in each direction. Smoother drives stay close to the middle. The scale is fixed at ±0.6 g so drives stay comparable.' },
      freePro: 'Same window as Analysis (Free ≤ 30 days, Pro older).',
      data: 'trip.gg.points / envelope; event markers at evidence[50] (the peak sample). “Within 0.2 g” and the filter classes (dominant axis per point) are derived. States: default t20261105-1745 · filteredByEventType t20261010-1015 (twisty drive, cornering) · Part t20261027-1728.',
      icons: ['close', 'chevronRight'],
      assets: [],
    },
  });

  /* ---------- S-ANL-03 · Channel picker ---------- */
  screen('S-ANL-03', {
    name: 'Channel picker',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'drives', detent: 'auto',
    over: (ctx) => ({ id: 'S-ANL-01', params: { trip: tripOf(ctx, 't20261105-1745').id } }),
    states: [{ id: 'default', label: 'Free channels' }, { id: 'proChannelsWithLockForFree', label: 'Pro channels (jerk, quality) with Lock for Free' }],
    stateParams: { default: { trip: 't20261105-1745' }, proChannelsWithLockForFree: { trip: 't20261105-1745', ent: 'free' } },
    render(ctx) {
      const { ui } = ctx;
      const sw = (c) => c.id === 'quality' ? '<i class="wc-sw is-exc"></i>' : `<i class="ch-sw is-${c.id}"></i>`;
      const row = (c) => {
        const locked = c.pro && !ctx.isPro;
        return ui.row({ lead: `<span class="ch-lead">${sw(c)}</span>`, titleHtml: `${ui.esc(c.label)}${c.pro ? ` ${ui.proBadge()}` : ''}`, title: c.label, sub: c.sub, ...(locked ? { trailing: `${ui.icon('lock', 15)}`, nav: 'S-SUB-03', params: { trip: ctx.params.trip }, label: `${c.label}, Pro. See plans` } : { trailing: 'toggle', on: ANL_CH[c.id], action: 'ch', arg: c.id }) });
      };
      return `${ui.sheetHeader('Channels', { sub: 'Choose what the timeline shows. Your choice is remembered.' })}<div class="ch">${ui.section('', ui.list(CHANNELS.filter((c) => !c.pro).map(row)), { style: 'caption' })}${ui.section('Pro channels', ui.list(CHANNELS.filter((c) => c.pro).map(row)), { style: 'caption', footer: ctx.isPro ? 'Jerk shows how sharply G changes; sample quality marks every observed, estimated, missing or left-out sample.' : 'Part of Pro, with range statistics. Your other channels stay free for drives in the last 30 days.' })}</div>`;
    },
    actions: { ch: (ctx, arg) => { const on = Object.entries(ANL_CH).filter(([k, v]) => v && k !== arg).length; if (ANL_CH[arg] && on === 0) { ctx.toast('Keep at least one channel on', { icon: 'info' }); return false; } ANL_CH[arg] = !ANL_CH[arg]; ctx.haptic(ANL_CH[arg] ? 'toggleOn' : 'toggleOff'); ctx.update(); refreshUnder('S-ANL-01'); return false; } },
    spec: {
      purpose: 'Toggle the timeline channels (F-RPL-05.4); the choice persists per user. Jerk and Sample quality are Pro (F-RPL-09.2).',
      layout: ['iOS: glass sheet styled as a compact menu (native: a glass `Menu` with toggles anchored to the Channels button) · Android: ModalBottomSheet with toggles (FilterChips are an equivalent).', 'Header “Channels” + “Choose what the timeline shows. Your choice is remembered.”', 'Free list: Speed · Longitudinal G · Lateral G · Elevation, each with a swatch that shows the line pattern (solid / dashed / area) + one-line description + toggle.', '“Pro channels”: Jerk · Sample quality with PRO badge; Free users see a lock instead of the toggle (row → S-SUB-03).'],
      components: ['sheetHeader', 'section', 'list', 'row', 'toggle', 'proBadge'],
      interactions: ['Toggle → the timeline underneath updates immediately (at least one channel must stay on: toast).', 'Locked Pro row → S-SUB-03 (default lock sheet; never during a trip).'],
      motion: ['Toggle spring.fast + haptic toggleOn/Off; lanes underneath cross-fade.'],
      a11y: ['“Lateral G, dashed amber line. Turning left and right. On, switch.” Locked: “Jerk, Pro. See plans, button.”'],
      platformDiffs: { ios: 'Native glass Menu with Toggle items; the prototype uses a compact glass sheet.', android: 'ModalBottomSheet with M3 Switches (thumb icons).' },
      analytics: ['analysis_channel_toggled {channel}'],
      copy: { Title: 'Channels', Sub: 'Choose what the timeline shows. Your choice is remembered.', Pro: 'Jerk · How sharply G changes / Sample quality · Observed, estimated, gaps, left out', 'Footer Free': 'Part of Pro, with range statistics. Your other channels stay free for drives in the last 30 days.', 'Footer Pro': 'Jerk shows how sharply G changes; sample quality marks every observed, estimated, missing or left-out sample.', 'Last channel': 'Keep at least one channel on' },
      freePro: 'Free: four channels. Pro/Preview/Ride-along: + Jerk and Sample quality. Lock variant: S-SUB-03 default.',
      data: 'Prototype state in a module-level object (ANL_CH); persist natively per user.',
      icons: ['lock'],
      assets: [],
    },
  });

  /* ---------- S-ANL-04 · Range statistics sheet ---------- */
  function rangeStats(t, t0, t1) {
    const rows = t.track.rows.filter((r) => r[0] >= t0 && r[0] <= t1);
    const lon = rows.map((r) => r[4]).filter((v) => v != null), lat = rows.map((r) => r[5]).filter((v) => v != null);
    let jk = 0; for (let i = 1; i < rows.length; i++) jk = Math.max(jk, Math.abs(((rows[i][4] ?? 0) - (rows[i - 1][4] ?? 0)) / Math.max(1, rows[i][0] - rows[i - 1][0])));
    const evs = t.events.filter((e) => e.t >= t0 && e.t <= t1);
    return { km: kmAt(t, t1) - kmAt(t, t0), dur: t1 - t0, brake: Math.min(0, ...lon), accel: Math.max(0, ...lon), lat: Math.max(0, ...lat.map(Math.abs)), jerk: jk, avgAbs: lon.length ? lon.reduce((a, v) => a + Math.abs(v), 0) / lon.length : 0, evs };
  }
  screen('S-ANL-04', {
    name: 'Range statistics sheet',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'drives', detent: 'auto',
    over: (ctx) => ({ id: 'S-ANL-01', params: { trip: tripOf(ctx, 't20261105-1745').id } }),
    states: [{ id: 'default', label: 'Def' }, { id: 'Lock', label: 'Lock' }],
    stateParams: { default: { trip: 't20261105-1745', t0: '643', t1: '1098' }, Lock: { trip: 't20261105-1745', t0: '643', t1: '1098', ent: 'free' } },
    render(ctx) {
      const { ui, data, icons } = ctx;
      const t = tripOf(ctx, 't20261105-1745');
      const t0 = clamp(+(ctx.params.t0 ?? t.cleanest.t0), 0, t.durationS), t1 = clamp(+(ctx.params.t1 ?? t.cleanest.t1), t0 + 1, t.durationS);
      const R = rangeStats(t, t0, t1);
      const locked = !ctx.isPro;
      const road = t.cleanest && Math.abs(t.cleanest.t0 - t0) < 5 && Math.abs(t.cleanest.t1 - t1) < 5 ? ` · ${t.cleanest.road}` : '';
      const V = anlView(t, ctx.size.w - (ctx.ios ? 56 : 48) - 24, 'time', 1, t0);
      const lanes = anlLanes(ctx, t, V, { cursor: t1, range: [t0, t1], channels: { speed: true, long: true } });
      const tiles = ui.stats([
        { label: 'Distance', value: data.km(R.km), unit: 'km', unitSpoken: 'kilometres', prov: 'derived' },
        { label: 'Duration', value: data.clock(R.dur), prov: 'derived' },
        { label: 'Peak braking', value: Math.abs(R.brake).toFixed(2), unit: 'g', prov: 'derived' },
        { label: 'Peak accel', value: R.accel.toFixed(2), unit: 'g', prov: 'derived' },
        { label: 'Peak lateral', value: R.lat.toFixed(2), unit: 'g', prov: 'derived' },
        { label: 'Peak jerk', value: R.jerk.toFixed(2), unit: 'g/s', prov: 'derived' },
      ], { cols: 3, cls: 'rng-tiles' });
      const tipEv = t.tip && R.evs.find((e) => e.id === t.tip.eventId);
      const contrib = R.evs.length
        ? ui.banner({ kind: 'info', icon: icons.pin(R.evs[0].type, { size: 20 }), title: `${R.evs.length} event${R.evs.length > 1 ? 's' : ''} in this range`, text: tipEv ? `Including the ${evLabel(ctx, tipEv.type).toLowerCase()} at ${tipEv.place}, which cost the most smoothness on this drive.` : 'These moments pulled the score down a little; everything else here was smooth.' })
        : ui.banner({ kind: 'positive', icon: 'sparkle', title: 'No abrupt inputs here', text: `This stretch helped your score. Average input ${R.avgAbs.toFixed(2)} g.` });
      const inner = `<div class="rng-lanes">${lanes.html}</div>${tiles}${contrib}`;
      const body = locked ? ui.lockPanel(inner, { text: 'Range statistics are part of Pro.', nav: 'S-SUB-03', params: { trip: t.id } }) : inner;
      return `${ui.sheetHeader('Selected range', { sub: `${data.clock(t0)}–${data.clock(t1)} into the drive${road}` })}<div class="rng">${body}<div class="rng-acts">${ui.button('Show in Replay', { kind: 'tonal', block: true, icon: 'replay', nav: 'S-RPL-01', params: { trip: t.id, at: Math.round(t0) } })}${ui.button('Clear selection', { kind: 'text', block: true, back: true })}</div></div>`;
    },
    spec: {
      purpose: 'Select a stretch (an on-ramp, a corner, a highway run) and see its own stats and Smoothness contribution (F-RPL-09, P1, Pro).',
      layout: ['Medium sheet over S-ANL-01, auto height. Header “Selected range” + “10:43–18:18 into the drive · Gardiner”.', 'Mini timeline (Speed + Long G) of the whole drive with the selected band in Glide 14%.', '3×2 stat tiles: Distance · Duration · Peak braking · Peak accel · Peak lateral · Peak jerk (each with provenance glyph).', 'Contribution banner: positive “No abrupt inputs here. This stretch helped your score.” or info “n events in this range …”.', 'Actions: Show in Replay (tonal, block) → S-RPL-01 at the range start · Clear selection (text).', 'Lock (Free): the same content blurred 14 px + lock + “Range statistics are part of Pro.” + See plans.'],
      components: ['sheetHeader', 'stats', 'banner', 'button', 'lockPanel', 'anlLanes (local)'],
      interactions: ['Show in Replay → S-RPL-01 with at = range start (paused there).', 'Clear selection / drag down → back to S-ANL-01.', 'Lock → S-SUB-03.'],
      motion: ['spring.sheet; the band fades in. RM: cross-fade.'],
      a11y: ['“Selected range, 10:43 to 18:18 into the drive. 9.5 kilometres. Peak braking 0.10 g.” Each tile reads label, value, units and provenance.'],
      platformDiffs: { ios: 'Glass medium sheet; tiles on `--dp-surface-2`.', android: 'ModalBottomSheet; tiles surface-container-high, radius 12.' },
      analytics: ['analysis_range_selected', 'locked_content_viewed {type: range}'],
      copy: { Title: 'Selected range', Positive: 'No abrupt inputs here. This stretch helped your score. Average input {g} g.', Events: '{n} events in this range · Including the hard braking at {place}, which cost the most smoothness on this drive.', Lock: 'Range statistics are part of Pro.', Actions: 'Show in Replay · Clear selection' },
      freePro: 'Pro (F-RPL-09). Free: Lock with this range’s own stats blurred → S-SUB-03.',
      data: 'Default range = trip.cleanest (t20261105-1745, 643–1098 s on the Gardiner). Every tile is derived from trip.track rows in [t0, t1]; distance integrates track positions; jerk = max |ΔlonG/Δt| between rows.',
      icons: ['replay', 'sparkle', 'lock'],
      assets: [],
    },
  });

  /* ==========================================================================================================
     S-DQ-01 · Data-quality detail sheet                                       F-DQ-01..05 · F-CAP-05 · F-RPL-08
     ========================================================================================================== */
  const DQ_LINE = {
    Good: 'Everything we needed, the whole way. Scores and events are as reliable as a phone gets.',
    Usable: 'Mostly solid. A few moments are estimated, and the score is still fair.',
    Limited: 'Your phone moved around for about 3 minutes, so we left that part out of the score.',
    Unscorable: 'We couldn’t score this one fairly. The route and stats are still here.',
  };
  screen('S-DQ-01', {
    name: 'Data-quality detail sheet',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'drives', detent: 'large',
    over: (ctx) => ({ id: 'S-TRP-01', params: { trip: tripOf(ctx, 't20261105-1745').id } }),
    states: [
      { id: 'default', label: 'Good' }, { id: 'usable', label: 'Usable' }, { id: 'limited', label: 'Limited' }, { id: 'unscorable', label: 'Unscorable' },
      { id: 'calibrating', label: 'Calibrating' }, { id: 'estimatedStart', label: 'Estimated start' }, { id: 'imu25Hz', label: 'IMU 25 Hz (battery/thermal)' },
    ],
    stateParams: { default: { trip: 't20261105-1745' }, usable: { trip: 't20261020-0740' }, limited: { trip: 't20261027-1728' }, unscorable: { trip: 't20261106-1210' }, calibrating: { trip: 't20260928-1842' }, estimatedStart: { trip: 't20261105-0742' }, imu25Hz: { trip: 't20261024-0930' } },
    render(ctx) {
      const { ui, data, icons, state } = ctx, esc = ui.esc;
      const t = tripOf(ctx, 't20261105-1745'), q = t.quality, P = ctx.D.provenance;
      const cal = state === 'calibrating';
      const gap = q.spans.gap[0], gapS = gap ? Math.round(gap[1] - gap[0]) : 0;
      const line = q.imuNote ? `${q.imuNote}. The score is fair, with a little less detail in short events.` : q.badge === 'Usable' && gap ? `Mostly solid, with ${gapS} s without GPS downtown. The score is fair; that stretch is drawn dotted.` : q.badge === 'Unscorable' ? `We couldn’t score this one fairly. ${q.reason}.` : DQ_LINE[q.badge];
      const hero = `<div class="dq-hero is-${q.badge.toLowerCase()}"><span class="dq-glyph">${icons.quality(q.badge, { size: 34 })}</span><div><b>${esc(q.badge)} data</b><p>${esc(line)}</p></div></div>`;
      const banners = [];
      if (cal) banners.push(ui.banner({ kind: 'info', icon: icons.provenance('estimated', { size: 18 }), title: 'Lining up your phone with the car', text: `The first ${q.alignment.replace(/\D+/g, '') || 40} s are used to work out which way your phone faces, with no setup from you. Events aren’t detected until it settles.` }));
      if (cal && t.smoothness && t.smoothness.calibrating) banners.push(ui.banner({ kind: 'info', icon: 'info', title: `Baseline calibrating · ${t.smoothness.calibrating.k} of ${t.smoothness.calibrating.of}`, text: 'Comparisons with your usual start after three scorable drives.' }));
      if (state === 'estimatedStart') banners.push(ui.banner({ kind: 'info', icon: icons.provenance('estimated', { size: 18 }), title: `The first ≈ ${data.km(q.estimatedStartM / 1000)} km is estimated`, text: 'Automatic trips start a moment after you set off. We fill in the start from your phone’s location history and draw it dashed. It’s never scored and never gets event pins.' }));
      const cov = `<div class="dq-cov">${coverageStrip(ctx, t, { calibrating: cal })}<div class="dq-leg"><span><i class="wc-sw is-obs"></i>Measured</span><span><i class="wc-sw is-est"></i>Estimated</span>${q.spans.gap.length ? '<span><i class="wc-sw is-gap"></i>No GPS</span>' : ''}${q.spans.excluded.length ? '<span><i class="wc-sw is-exc"></i>Left out</span>' : ''}${cal ? '<span><i class="wc-sw is-cal"></i>Lining up</span>' : ''}</div><p class="dq-scored">${t.smoothness || q.badge === 'Unscorable' ? `${q.badge === 'Unscorable' ? 'Scored 0' : `Scored ${q.scoredMin}`} of ${q.totalMin} min` : `${q.totalMin} min recorded`}</p></div>`;
      const pv = (k) => `<span class="dq-pv" title="${cap1(k)}" aria-label="${cap1(k)}">${icons.provenance(k, { size: 12 })}</span>`;
      const row = (icon, title, value, prov, sub, warn) => ui.row({ icon, iconTone: warn ? 'warn' : 'neutral', title, sub, value: `${esc(value)}${pv(prov)}` });
      const phone = { mounted: 'Mounted', loose: 'Loose', moved: 'Moved' }[q.phone] || cap1(q.phone);
      const rows = [
        row('phone', 'Phone', phone, 'derived', q.phone === 'mounted' ? null : q.reason, q.phone !== 'mounted'),
        row('bolt', 'Sensor rate', `${q.imuHz} Hz`, P.metrics.imuRate, q.imuNote ? q.imuNote : 'Motion sensors, as delivered by the phone', q.imuHz < 50),
        row('gps', 'GPS coverage', `${q.gnssCoveragePct}%`, P.metrics.gnssCoverage, gap ? `${gapS} s gap on ${t.events.find((e) => e.t > gap[0]) ? 'the way downtown' : 'the route'}` : 'Good fixes along the route', !!gap),
        row('analysis', 'Phone alignment', q.alignment, 'derived', q.alignment === 'Never settled' ? 'Orientation kept changing' : 'Worked out automatically', q.alignment === 'Never settled'),
        row('layers', 'Left out of the score', q.excludedMin ? `${q.excludedMin} min` : 'Nothing', 'derived', q.spans.excluded.length ? q.spans.excluded[0][2] : null, !!q.excludedMin),
        t.source === 'auto' ? row('pin', 'Estimated start', `≈ ${data.km(q.estimatedStartM / 1000)} km`, P.metrics.estimatedStart, 'Before auto-detect caught up') : row('pin', 'Estimated start', 'None', 'observed', `Started from ${t.sourceDetail || 'the app'}`),
      ];
      const legend = ui.list(['observed', 'derived', 'estimated'].map((k) => ui.row({ lead: `<span class="dq-pvl">${icons.provenance(k, { size: 18 })}</span>`, title: cap1(k), sub: P.legend[k] })));
      const guide = ['Limited', 'Unscorable'].includes(q.badge) || q.phone !== 'mounted';
      const links = ui.list([
        ui.row({ icon: 'phone', title: 'Where to put your phone', sub: guide ? 'A fixed mount gives Good data more often' : 'Placement tips', nav: guide ? 'S-DQ-02/fromLimitedTrip' : 'S-DQ-02', params: { trip: t.id } }),
        ui.row({ icon: 'info', title: 'How scores work', sub: 'Badges, provenance and fairness', nav: 'S-SCO-02' }),
      ]);
      return `${ui.sheetHeader(`${q.badge} data`, { icon: icons.quality(q.badge, { size: 24 }), sub: `${t.partOfDay} · ${t.label}` })}<div class="dq">${hero}${banners.join('')}${ui.section('This drive', cov, { style: 'caption' })}${ui.section('Details', ui.list(rows), { style: 'caption', footer: 'Long-press any number in the app to see whether it was measured, calculated or estimated.' })}${ui.section('What the labels mean', legend, { style: 'caption' })}${ui.section('', links, { style: 'caption' })}</div>`;
    },
    mount(el, ctx) {
      if (still(ctx, el)) return;
      el.querySelectorAll('.wc-cov-bar > i').forEach((b, i) => ctx.motion.animate(b, [{ opacity: 0, transform: 'scaleY(.2)' }, { opacity: 1, transform: 'scaleY(1)' }], { preset: 'default', delay: 200 + i * 60 }));
      const bar = el.querySelector('.wc-cov-bar'); if (bar) bar.animate([{ clipPath: 'inset(0 100% 0 0 round 7px)' }, { clipPath: 'inset(0 0 0 0 round 7px)' }], { duration: 700, easing: 'cubic-bezier(.32,.72,0,1)' });
    },
    spec: {
      purpose: 'Explain how much to trust a drive’s numbers (F-DQ-04.2): phone state, sensor rate, GPS coverage, alignment, what was left out and why, the estimated start, and the provenance legend (F-DQ-05).',
      layout: ['Large sheet (iOS `.large` detent, Android full-height ModalBottomSheet) over trip detail, Story, Replay or Analysis.', 'Header: quality glyph 24 + “{Badge} data” + part of day · label.', 'Hero card: glyph 34 in its quality colour on a 14% wash + one honest sentence per badge (Good / Usable / Limited / Unscorable, IMU-note and gap variants).', 'State banners: calibrating (alignment “Lining up your phone with the car” + baseline k of 3) · estimated start explanation.', '“This drive”: coverage strip (14 tall, radius 7) across the whole drive — measured solid, estimated dashed, no GPS outlined gap, left out 45° hatch, lining up ticks — with 0:00 / end labels, legend and “Scored 31 of 34 min”.', '“Details” list: Phone · Sensor rate · GPS coverage · Phone alignment · Left out of the score · Estimated start; each value carries its provenance glyph + word; warn tone when it lowered the badge.', '“What the labels mean”: Observed / Derived / Estimated with DP_DATA.provenance.legend.', 'Links: Where to put your phone → S-DQ-02 · How scores work → S-SCO-02.'],
      components: ['sheetHeader', 'banner', 'section', 'list', 'row', 'qualityBadge', 'provenance', 'coverageStrip (local)'],
      interactions: ['Where to put your phone → S-DQ-02 (fromLimitedTrip for Limited/Unscorable/loose-phone drives) · How scores work → S-SCO-02 · drag down / close → back.'],
      motion: ['Hero: the coverage strip reveals left to right (700 ms ease.glide) and its spans rise in (spring.default, 60 ms stagger). RM: static.'],
      a11y: ['Strip label: “Drive coverage, 33 minutes. Start estimated. 41 seconds without GPS.” Rows: “GPS coverage, 97 percent. Derived.”', 'Quality is never colour alone: glyph shape + the badge word everywhere. XL: rows wrap, values move under titles.'],
      platformDiffs: { ios: 'Large sheet with `--dp-bg-elevated`; inset-grouped lists; glass close.', android: 'Full-height ModalBottomSheet (surface-container-low); M3E segmented ListItems; no close button.' },
      analytics: ['quality_badge_viewed {badge}', 'placement_guide_viewed {source: dq}', 'scores_explainer_viewed {source: dq}'],
      copy: { Good: DQ_LINE.Good, Usable: 'Mostly solid, with {n} s without GPS downtown. The score is fair; that stretch is drawn dotted.', 'IMU 25 Hz': 'Battery saver: sensors at 25 Hz. The score is fair, with a little less detail in short events.', Limited: DQ_LINE.Limited, Unscorable: 'We couldn’t score this one fairly. {quality.reason}.', Calibrating: 'Lining up your phone with the car. The first 40 s are used to work out which way your phone faces, with no setup from you. Events aren’t detected until it settles.', 'Estimated start': 'The first ≈ {km} km is estimated. Automatic trips start a moment after you set off. We fill in the start from your phone’s location history and draw it dashed. It’s never scored and never gets event pins.' },
      freePro: 'Free on every trip.',
      data: 'trip.quality (badge, phone, imuHz, imuNote, gnssCoveragePct, alignment, estimatedStartM, spans, excludedMin, scoredMin, totalMin) + DP_DATA.provenance. States: Good t20261105-1745 · usable t20261020-0740 · limited t20261027-1728 · unscorable t20261106-1210 · calibrating t20260928-1842 (alignment span derived from quality.alignment) · estimatedStart t20261105-0742 · imu25Hz t20261024-0930.',
      icons: ['phone', 'bolt', 'gps', 'analysis', 'layers', 'pin', 'info'],
      assets: [],
    },
  });

  /* ---------- S-DQ-02 · Phone placement guide ---------- */
  screen('S-DQ-02', {
    name: 'Phone placement guide',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'push', tab: 'drives',
    states: [{ id: 'default', label: 'Def' }, { id: 'fromLimitedTrip', label: 'From Limited trip' }],
    stateParams: { fromLimitedTrip: { trip: 't20261027-1728' } },
    backTo: 'S-TRP-01',
    render(ctx) {
      const { ui, icons } = ctx;
      const t = ctx.state === 'fromLimitedTrip' ? tripOf(ctx, 't20261027-1728') : null;
      const art = `<svg class="pg-art" viewBox="0 0 320 170" aria-hidden="true"><path d="M10 150C60 40 260 40 310 150" fill="none" stroke="var(--dp-text-3)" stroke-width="2" opacity=".5"/><path d="M20 128H300" stroke="var(--dp-text-3)" stroke-width="2" stroke-linecap="round" opacity=".6"/><circle cx="74" cy="132" r="34" fill="none" stroke="var(--dp-text-3)" stroke-width="5" opacity=".55"/><circle cx="74" cy="132" r="8" fill="var(--dp-text-3)" opacity=".55"/><rect class="pg-mount" x="168" y="112" width="34" height="10" rx="4" fill="var(--dp-text-3)" opacity=".8"/><g class="pg-phone"><rect x="158" y="52" width="54" height="66" rx="10" fill="var(--card-bg)" stroke="var(--dp-accent)" stroke-width="3"/><circle cx="185" cy="85" r="9" fill="none" stroke="var(--dp-accent)" stroke-width="2.5"/><path d="M180 85l4 4 7-8" fill="none" stroke="var(--dp-accent)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></g></svg>`;
      const opt = (badge, title, text) => `<div class="pg-opt is-${badge.toLowerCase()}"><span class="pg-q">${icons.quality(badge, { size: 22 })}<b>${badge}</b></span><div><b>${title}</b><p>${text}</p></div></div>`;
      const rules = ui.list([
        ui.row({ icon: 'shield', title: 'Set it before you set off', sub: 'Never handle your phone while driving. DrivePerf needs nothing from you on the move.' }),
        ui.row({ icon: 'phone', title: 'Any way up works', sub: 'DrivePerf works out which way the phone faces on its own. No calibration.' }),
        ui.row({ icon: 'routes', title: 'Same spot, every drive', sub: 'It keeps your drives easy to compare with each other.' }),
      ]);
      const banner = t ? `<div class="ui-pad">${ui.banner({ kind: 'attention', icon: icons.quality(t.quality.badge, { size: 20 }), title: `Your ${t.partOfDay.toLowerCase()} drive was ${t.quality.badge}`, text: `${t.quality.reason}. A fixed spot keeps every minute in the score.`, action: { text: 'Details', nav: 'S-DQ-01/limited', params: { trip: t.id } } })}</div>` : '';
      const body = `<div class="pg">${banner}<div class="ui-pad">${ui.card(`<div class="pg-hero">${art}<b>A fixed spot gives the best data.</b><p>Your phone measures how the car moves. If the phone slides around, we can’t tell its movement from yours.</p></div>`, { hero: true })}</div>
        ${ui.section('Where it works', `<div class="ui-pad pg-opts">${opt('Good', 'A fixed mount', 'Dash, vent or windscreen mount. The phone never slides.')}${opt('Usable', 'A stable spot', 'A cupholder, tray or door pocket where it can’t move around.')}${opt('Limited', 'Loose or in a bag', 'On a seat, in a pocket or a bag. When it slides, we leave that part out of the score.')}</div>`, { style: 'caption' })}
        ${ui.section('Good to know', rules, { style: 'caption' })}<div class="ui-pad" style="margin-top:8px">${ui.button('Got it', { kind: 'filled', size: 'l', block: true, back: true })}</div></div>`;
      return ui.page(body, { nav: { title: 'Where to put your phone' } });
    },
    mount(el, ctx) { if (still(ctx, el)) return; const p = el.querySelector('.pg-phone'); if (p) ctx.motion.animate(p, [{ transform: 'translateY(-26px)', opacity: 0 }, { transform: 'translateY(0)', opacity: 1 }], { preset: 'bouncy', delay: 200 }); ctx.ui.anim.rise(el, '.pg-opt', { stagger: 40, delay: 300 }); },
    spec: {
      purpose: 'Simple advice on where to put the phone so data is Good more often (F-DQ-06). It never suggests handling the phone while driving.',
      layout: ['Push (Drives tab), title “Where to put your phone”.', 'fromLimitedTrip: attention banner “Your tuesday evening drive was Limited · Phone moved for about 3 minutes. A fixed spot keeps every minute in the score.” + Details → S-DQ-01.', 'Hero card: line illustration (dash, wheel, phone in a vent mount in Glide with a check) + “A fixed spot gives the best data.” + one sentence.', '“Where it works”: three option cards with the quality glyph + label (Good fixed mount · Usable stable spot · Limited loose or in a bag).', '“Good to know” list: Set it before you set off · Any way up works · Same spot, every drive.', 'Got it (filled, large, block) → back.'],
      components: ['navBar', 'banner', 'card', 'section', 'list', 'row', 'button', 'qualityBadge'],
      interactions: ['Got it / Back → caller (S-DQ-01, S-SET-06, Story placement chip).', 'Banner Details → S-DQ-01/limited.'],
      motion: ['Hero: the phone drops into the mount (spring.bouncy, once); option cards rise 8 px (40 ms stagger). RM: static.'],
      a11y: ['Illustration is decorative (hidden); every option is text with the badge word. XL: cards stack, text wraps.'],
      platformDiffs: { ios: 'Inline title, glass back; inset-grouped list.', android: 'MediumFlexibleTopAppBar; segmented ListItems; filled button with shape morph.' },
      analytics: ['placement_guide_viewed {source: dq|settings|story}'],
      copy: { Hero: 'A fixed spot gives the best data. Your phone measures how the car moves. If the phone slides around, we can’t tell its movement from yours.', Good: 'A fixed mount · Dash, vent or windscreen mount. The phone never slides.', Usable: 'A stable spot · A cupholder, tray or door pocket where it can’t move around.', Limited: 'Loose or in a bag · On a seat, in a pocket or a bag. When it slides, we leave that part out of the score.', Rules: 'Set it before you set off · Never handle your phone while driving. DrivePerf needs nothing from you on the move. / Any way up works · DrivePerf works out which way the phone faces on its own. No calibration. / Same spot, every drive · It keeps your drives easy to compare with each other.' },
      freePro: 'Free.',
      data: 'fromLimitedTrip uses t20261027-1728 (Limited, phone moved for about 3 minutes).',
      icons: ['shield', 'phone', 'routes'],
      assets: [],
    },
  });

  /* ==========================================================================================================
     S-SCO-01 · Smoothness breakdown                                                   F-SCO-01/02/03
     ========================================================================================================== */
  function routeRuns(ctx, t) {
    if (!t.routeId) return [];
    return ctx.data.trips((x) => x.routeId === t.routeId && x.smoothness && new Date(x.start) <= new Date(t.start)).reverse().slice(-8);
  }
  screen('S-SCO-01', {
    name: 'Smoothness breakdown',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'push', tab: 'drives',
    states: [{ id: 'default', label: 'Def' }, { id: 'calibrating', label: 'Calibrating' }, { id: 'routeVsContextComparison', label: 'Route vs context comparison' }, { id: 'unscorable', label: 'Unscorable' }],
    stateParams: { default: { trip: 't20261105-1745' }, calibrating: { trip: 't20260929-0738' }, routeVsContextComparison: { trip: 't20261002-0745' }, unscorable: { trip: 't20261106-1210' } },
    backTo: 'S-TRP-01',
    render(ctx) {
      const { ui, data, icons } = ctx, esc = ui.esc;
      const t = tripOf(ctx, 't20261105-1745'), s = t.smoothness;
      const navO = { title: 'Smoothness', subtitle: `${t.partOfDay} · ${t.label}`, trailing: [ui.iconButton('info', { nav: 'S-SCO-02', label: 'How scores work' })] };
      if (!s) {
        const body = `<div class="sco"><div class="ui-pad">${ui.card(`<div class="sco-hero">${ui.ring(null, 150, { unscorable: true })}<b>Not scored</b><p>${esc(t.quality.reason)}. When the phone moves freely, we can’t tell its movement from the car’s, so any score would be a guess.</p></div>`, { hero: true })}</div>${ui.section('What would help', ui.list([ui.row({ icon: 'phone', title: 'Put the phone in a fixed spot', sub: 'A mount, cupholder or tray', nav: 'S-DQ-02/fromLimitedTrip', params: { trip: t.id } }), ui.row({ lead: `<span class="ui-row-ic is-neutral">${icons.quality('Unscorable', { size: 18 })}</span>`, title: 'See the data quality', sub: ui.qualityDetail(t), nav: 'S-DQ-01/unscorable', params: { trip: t.id } })]), { style: 'caption', footer: 'Unscored drives don’t count toward your baseline, routes or weekly average.' })}</div>`;
        return ui.page(body, { nav: navO });
      }
      const rec = s.record && s.record.isNew ? s.record : null, route = t.routeId && data.route(t.routeId);
      const cal = s.calibrating;
      const aria = `Smoothness ${s.score}. ${s.delta ? `${Math.abs(s.delta)} ${s.delta > 0 ? 'above' : 'below'} your usual${s.basis === 'route' ? ` on ${s.routeName}` : ''}.` : s.compareText}`;
      const basisLine = cal ? `Calibrating your baseline · ${cal.k} of ${cal.of}. We compare each drive with your own usual once we’ve seen three, and make no trend claims before then.` : s.basis === 'route' ? `Compared with your usual on ${s.routeName}: ${s.baseline}.` : `Compared with your usual ${t.context} drives: ${s.baseline}.`;
      const hero = ui.card(`<div class="sco-hero" aria-label="${esc(aria)}">${ui.ring(s.score, 168, { base: cal ? null : s.baseline, rec: rec ? rec.previous.score : null, recNew: !!rec, stroke: 13, aria })}${ui.deltaChip(s, { record: false })}${rec ? `<span class="sco-rec">${icons.svg('rosette', { size: 15 })}New route record · beat ${rec.previous.score} from ${data.date(rec.previous.date)}</span>` : ''}<p>${esc(basisLine)}</p></div>`, { hero: true });
      // basis: route vs context (F-SCO-01.2)
      const opt = (on, title, sub, value) => `<div class="sco-opt ${on ? 'is-on' : ''}"><span class="ui-radio ${on ? 'is-on' : ''}"></span><span><b>${esc(title)}</b><small>${esc(sub)}</small></span>${value ? `<em>${value}</em>` : ''}</div>`;
      const firstRun = ctx.state === 'routeVsContextComparison' || (route && route.createdOn === t.start.slice(0, 10));
      const basis = cal ? '' : ui.section('Compared with', ui.card(`<div class="sco-basis">${s.basis === 'route'
        ? opt(true, `Your runs of ${s.routeName}`, `This drive matched a route, so it’s compared on that route`, `usual ${s.baseline}`) + opt(false, `Your usual ${t.context} drives`, 'Used when a drive isn’t on a route', '')
        : opt(true, `Your usual ${t.context} drives`, firstRun && route ? `This was the drive that made ${route.name} a route` : 'This drive didn’t match a route', `usual ${s.baseline}`) + opt(false, route ? route.name : 'A route', firstRun && route ? 'From the next run, it has its own baseline' : 'Drive the same trip twice and it becomes a route', '')}</div>`), { style: 'caption', footer: 'Every comparison is against your own drives. Never anyone else’s.' });
      const open = ctx.store.open ?? (s.contributors.find((c) => c.eventIds.length) || {}).id;
      const contrib = s.contributors.map((c) => {
        const b = BAND[c.band], has = c.eventIds.length, isOpen = open === c.id && has;
        const evs = isOpen ? `<div class="sco-evs">${c.eventIds.map((id) => evOf(t, id)).filter(Boolean).map((e) => ui.eventRow(e, { tripId: t.id, time: tod(ctx, t, e.t) })).join('')}</div>` : '';
        return `<div class="sco-c ${isOpen ? 'is-open' : ''}"><div class="sco-ch ${has ? 'ui-press' : ''}" ${has ? ui.act({ action: 'open', arg: c.id, haptic: 'tap', label: `${c.label}, ${b[2]}. ${c.reason}. ${isOpen ? 'Hide' : 'Show'} events` }) : `aria-label="${esc(`${c.label}, ${b[2]}. ${c.reason}.`)}"`}><span class="sco-cl"><b>${esc(c.label)}</b>${ui.chip('', { kind: b[0], html: `<b aria-hidden="true">${b[1]}</b>${b[2]}` })}</span><span class="sco-cr">${esc(c.reason)}</span>${has ? `<i class="sco-cv">${ui.icon(isOpen ? 'up' : 'down', 16)}</i>` : ''}</div>${evs}</div>`;
      }).join('');
      const runs = routeRuns(ctx, t);
      const trend = runs.length >= 3 ? ui.section(`Your last ${runs.length} runs of ${s.routeName}`, ui.card(`<div class="sco-trend">${ui.sparkline(runs.map((r) => r.smoothness.score), { w: ctx.size.w - 64, h: 64, color: 'var(--dp-accent)', area: true, peak: runs.length - 1, label: `Smoothness on your last ${runs.length} runs: ${runs.map((r) => r.smoothness.score).join(', ')}` })}<div class="sco-tr-ax"><span>${data.date(runs[0].start)}</span><span>This drive · ${s.score}</span></div></div>`), { style: 'caption' }) : '';
      const body = `<div class="sco">${ctx.state === 'routeVsContextComparison' ? `<div class="ui-pad">${ui.banner({ kind: 'info', icon: 'routes', title: `${route ? route.name : 'A new route'} started here`, text: 'A drive is compared on its route once that route exists. Before then, it’s compared with your usual drives of the same kind.' })}</div>` : ''}<div class="ui-pad">${hero}</div>${basis}${ui.section('What moved it', `<div class="ui-card is-flush sco-cs">${contrib}</div>`, { style: 'caption', footer: 'Each contributor is compared with your usual. Tap one with events to see the evidence.' })}${trend}${ui.section('', ui.list([ui.row({ icon: 'info', title: 'How scores work', sub: 'Baseline, contributors and fairness', nav: 'S-SCO-02' }), ui.row({ icon: 'check', title: 'Braked for a good reason?', sub: 'Open the event and choose “Was it needed?”. It won’t count.' })]), { style: 'caption' })}</div>`;
      return ui.page(body, { nav: navO });
    },
    mount(el, ctx) { ctx.ui.anim.rings(el); ctx.ui.anim.rise(el, '.sco-c', { stagger: 30, delay: 300 }); },
    actions: { open: (ctx, arg) => { const t = tripOf(ctx, 't20261105-1745'); const cur = ctx.store.open ?? (t.smoothness.contributors.find((c) => c.eventIds.length) || {}).id; ctx.store.open = cur === arg ? '' : arg; ctx.update(); return false; } },
    spec: {
      purpose: 'Show which inputs moved the score, against the user’s own baseline (F-SCO-01/02/03): four contributors with evidence-linked reasons, and what the drive is compared with.',
      layout: ['Push (Drives tab), title “Smoothness” + part of day · label, ⓘ → S-SCO-02.', 'Hero card: ring 168 (stroke 13, baseline notch, record notch) + comparison chip + record line (gold rosette) + basis sentence (“Compared with your usual on The Commute home: 83.”). Calibrating: dots chip + “We compare each drive with your own usual once we’ve seen three…”.', '“Compared with”: radio-style card with two options — the route’s runs vs usual city/highway/mixed drives — the used one selected with its usual value; footer “Every comparison is against your own drives.”', '“What moved it”: Braking · Acceleration · Cornering · Consistency rows (label 15/600, band chip ▲ Smoother than usual / ● Usual / ▼ Rougher than usual, reason in meta) with a chevron when there are events; open rows list their event rows → S-RPL-02.', '“Your last n runs of {route}”: area sparkline (Glide) ending at this drive.', 'Links: How scores work · Braked for a good reason? (hint).', 'Unscorable: dashed ring “—” + reason + “What would help” (S-DQ-02, S-DQ-01).'],
      components: ['navBar', 'card', 'ring', 'deltaChip', 'chip', 'section', 'eventRow', 'sparkline', 'list', 'row', 'banner', 'iconButton'],
      interactions: ['Contributor with events → expands its event rows (haptic tap); event row → S-RPL-02 (trip, event).', 'ⓘ / How scores work → S-SCO-02 · placement → S-DQ-02 · quality → S-DQ-01.'],
      motion: ['Hero: ring reveal (motion.md §3.4), 1400 ms with baseline notch; contributor rows rise 8 px (30 ms stagger) after 300 ms; expansion uses spring.default. RM: static ring, instant expand.'],
      a11y: ['Ring: “Smoothness 85. 2 above your usual on The Commute home.” Contributor rows: “Braking, rougher than usual. 2 abrupt stops on the Gardiner and Kerr St. Show events.”', 'Bands carry ▲/●/▼ and words; “rougher” is neutral grey, never red.', 'XL: contributor label and chip stack; sparkline keeps its text summary.'],
      platformDiffs: { ios: 'Inline title; hero card radius 32; inset-grouped contributor card.', android: 'MediumFlexibleTopAppBar; ring on Cookie9Sided; contributor rows as M3E segmented ListItems with expand icons.' },
      analytics: ['score_viewed {surface: breakdown}', 'contributor_tapped {contributor}'],
      copy: { Basis: 'Compared with your usual on {route}: {baseline}. | Compared with your usual {context} drives: {baseline}.', Calibrating: 'Calibrating your baseline · k of 3. We compare each drive with your own usual once we’ve seen three, and make no trend claims before then.', 'Route vs context': '{route} started here. A drive is compared on its route once that route exists. Before then, it’s compared with your usual drives of the same kind.', Footer: 'Every comparison is against your own drives. Never anyone else’s.', Unscorable: 'Not scored. {reason}. When the phone moves freely, we can’t tell its movement from the car’s, so any score would be a guess.' },
      freePro: 'Free on every trip, forever.',
      data: 'trip.smoothness (score, baseline, delta, basis, routeName, calibrating, record, contributors[].eventIds). States: default t20261105-1745 · calibrating t20260929-0738 (2 of 3) · routeVsContextComparison t20261002-0745 (basis context; r_commute created that day) · unscorable t20261106-1210. The runs sparkline is derived from trips on the same routeId up to this drive.',
      icons: ['info', 'rosette', 'routes', 'phone', 'check', 'up', 'down'],
      assets: [],
    },
  });

  /* ---------- S-SCO-02 · How scores work ---------- */
  screen('S-SCO-02', {
    name: 'How scores work',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'push', tab: 'drives',
    states: [{ id: 'default', label: 'Def' }], variants: ['xl'],
    render(ctx) {
      const { ui, icons, D } = ctx;
      const th = D.meta.thresholdsG, alg = (D.trips.find((x) => x.smoothness) || {}).smoothness.algorithm.split(' ')[0];
      const para = (...p) => `<div class="how-p">${p.map((x) => `<p>${x}</p>`).join('')}</div>`;
      const sec = (title, html, o) => ui.section(title, `<div class="ui-pad">${ui.card(html)}</div>`, { style: 'caption', ...o });
      const hero = ui.card(`<div class="how-hero">${ui.ring(85, 132, { base: 83, stroke: 11, aria: 'Example: Smoothness 85 with your usual at 83' })}<div><b>Smoothness is how gently you drove</b><p>A score from 0 to 100 for how smoothly you braked, accelerated and turned. It compares you with you, never with anyone else.</p><span class="how-k"><i class="how-notch"></i>The small notch is your usual</span></div></div>`, { hero: true });
      const pins = ['brake', 'accel', 'corner'].map((ty) => `<div class="how-pin">${icons.pin(ty, { size: 28 })}<span><b>${evLabel(ctx, ty)}</b><small>${ty === 'brake' ? `${Math.abs(th.brake).toFixed(2)} g or more` : ty === 'accel' ? `${th.accel.toFixed(2)} g or more` : `${th.corner.toFixed(2)} g or more sideways`}</small></span></div>`).join('');
      const qual = ['Good', 'Usable', 'Limited', 'Unscorable'].map((b) => `<span class="how-q">${icons.quality(b, { size: 18 })}${b}</span>`).join('');
      const prov = ['observed', 'derived', 'estimated'].map((k) => ui.row({ lead: `<span class="dq-pvl">${icons.provenance(k, { size: 18 })}</span>`, title: cap1(k), sub: D.provenance.legend[k] }));
      const body = `<div class="how"><div class="ui-pad">${hero}</div>
        ${sec('Your usual', para('Your first three scorable drives set your baseline. After that, each drive is compared with your recent usual on the same route, or with similar city, highway or mixed drives.', 'Passenger rides and unscored drives never count toward it.'))}
        ${sec('What moves it', para('Four contributors: <b>Braking</b>, <b>Acceleration</b>, <b>Cornering</b> and <b>Consistency</b>. Each is smoother, usual or rougher than your own usual, with the moments that caused it.'))}
        ${sec('Events', `<div class="how-pins">${pins}</div><p class="how-small">G is how hard the car pushed you. Thresholds are placeholders while we tune them; a change ships as a new scoring version.</p>`)}
        ${sec('Was it needed?', para('Braked hard for a good reason, like a light changing or someone stepping out? Mark it as needed and it won’t count. You can change your mind any time.'))}
        ${sec('Fair by design', para('A full, gentle stop always scores at least as well as a rolling one. Speed never adds points; only how abruptly you changed it. Drives at an unusual pace can’t set route records.'))}
        ${sec('Data quality', `<div class="how-qs">${qual}</div><p class="how-small">Every drive gets one badge. When the phone moved or GPS dropped, we leave that part out instead of guessing.</p>`)}
        ${ui.section('Where numbers come from', ui.list(prov), { style: 'caption', footer: 'Long-press any number to see which one it is.' })}
        ${sec('Efficiency habits', para('A habit rating from stop-starts, abrupt starts and how steady your speed was. It’s not a fuel or energy measurement, so we never show litres, kWh or money.'))}
        ${sec('What it isn’t', para('A smooth score is not a safety certificate. DrivePerf can’t see the road, the weather or other drivers.'))}
        <p class="how-ver">Scoring version ${alg} · updated ${ctx.data.date(D.meta.generated, { day: 'numeric', month: 'long', year: 'numeric' })}</p></div>`;
      return ui.page(body, { nav: { title: 'How scores work' } });
    },
    mount(el, ctx) { ctx.ui.anim.rings(el, { haptics: false }); ctx.ui.anim.rise(el, '.ui-sec', { stagger: 30, delay: 200 }); },
    spec: {
      purpose: 'A plain explanation of Smoothness, the baseline, contributors, events and thresholds, provenance, data quality, efficiency as a proxy, and that a smooth score isn’t a safety certificate (F-SCO-08, F-SCO-07, F-DQ-05).',
      layout: ['Push, title “How scores work”.', 'Hero card: example ring 132 (85 with the usual notch at 83) + “Smoothness is how gently you drove” + one paragraph + notch key.', 'Caption sections, each one short card: Your usual · What moves it · Events (three pins by shape with thresholds from meta.thresholdsG) · Was it needed? · Fair by design · Data quality (four glyph chips) · Where numbers come from (provenance list) · Efficiency habits · What it isn’t.', 'Footer: “Scoring version smooth-v0.3 · updated 6 November 2026”.'],
      components: ['navBar', 'card', 'ring', 'section', 'list', 'row', 'qualityBadge', 'provenance'],
      interactions: ['Back only. (Linked from every ⓘ next to a score and from S-DQ-01.)'],
      motion: ['Hero ring reveal (no ticks); sections rise 8 px. RM: static.'],
      a11y: ['Headings for each section (rotor / headings navigation). XL: the hero stacks ring above text; nothing truncates.', 'First mention glosses G: “G is how hard the car pushed you.”'],
      platformDiffs: { ios: 'Inline title, inset cards.', android: 'MediumFlexibleTopAppBar; cards on surface-container.' },
      analytics: ['scores_explainer_viewed {source}'],
      copy: { Hero: 'Smoothness is how gently you drove. A score from 0 to 100 for how smoothly you braked, accelerated and turned. It compares you with you, never with anyone else.', Usual: 'Your first three scorable drives set your baseline. After that, each drive is compared with your recent usual on the same route, or with similar city, highway or mixed drives. Passenger rides and unscored drives never count toward it.', Fair: 'A full, gentle stop always scores at least as well as a rolling one. Speed never adds points; only how abruptly you changed it. Drives at an unusual pace can’t set route records.', Isnt: 'A smooth score is not a safety certificate. DrivePerf can’t see the road, the weather or other drivers.', Version: 'Scoring version {version} · updated {date}' },
      freePro: 'Free.',
      data: 'Thresholds from DP_DATA.meta.thresholdsG (placeholders, A12). Version from smoothness.algorithm; “updated” uses meta.generated (fixture date, derived).',
      icons: ['info'],
      assets: [],
    },
  });

  /* ---------- S-SCO-03 · “Was it needed?” sheet ---------- */
  screen('S-SCO-03', {
    name: '“Was it needed?” sheet',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'drives', detent: 'auto',
    over: (ctx) => ({ id: 'S-RPL-02', params: { trip: tripOf(ctx, 't20261020-0740').id, event: ctx.params.event || 'e3' } }),
    states: [{ id: 'default', label: 'Def' }, { id: 'markedNeeded', label: 'Marked needed' }, { id: 'countItAgain', label: 'Count it again' }],
    stateParams: { default: { trip: 't20261020-0740', event: 'e3' }, markedNeeded: { trip: 't20261020-0740', event: 'e3' }, countItAgain: { trip: 't20261020-0740', event: 'e3' } },
    render(ctx) {
      const { ui, icons } = ctx, esc = ui.esc;
      const t = tripOf(ctx, 't20261020-0740'), e = evOf(t, ctx.params.event) || t.events[0];
      const st = ctx.state, chip = (needed) => `<div class="wn-ev">${icons.pin(e.type, { size: 30, needed })}<span><b>${esc(evLabel(ctx, e.type))}</b><small>${esc(cap1(e.place))} · ${tod(ctx, t, e.t)}</small></span><em class="${needed ? 'is-struck' : ''}">${e.peakG.toFixed(2)} g</em></div>`;
      if (st === 'markedNeeded') return `<div class="wn is-done">${ui.sheetHeader('Marked as needed', { close: true })}${chip(true)}<div class="wn-score">${ui.ring(t.smoothness.score, 78, { label: false, stroke: 7, aria: `Smoothness ${t.smoothness.score}` })}<span><b>It doesn’t count.</b>Smoothness recomputed without it: ${t.smoothness.score}. Tips won’t be about this stop.</span></div>${ui.button('Done', { kind: 'filled', block: true, back: true })}</div>`;
      if (st === 'countItAgain') return `<div class="wn">${ui.sheetHeader('Count it again?')}${chip(true)}<p class="wn-p">You marked this as needed, so it doesn’t count. Count it again if it wasn’t.</p><div class="wn-acts">${ui.button('Count it again', { kind: 'filled', block: true, action: 'again' })}${ui.button('Keep it as needed', { kind: 'text', block: true, back: true })}</div></div>`;
      return `<div class="wn">${ui.sheetHeader('Was it needed?')}${chip(false)}<p class="wn-p">If you braked for a good reason, like a light changing or someone stepping out, we won’t count it. You can change your mind any time.</p><div class="wn-acts">${ui.button('Yes, it was needed', { kind: 'filled', block: true, state: 'markedNeeded', haptic: 'tap' })}${ui.button('No, count it', { kind: 'text', block: true, back: true })}</div></div>`;
    },
    mount(el, ctx) { if (ctx.state === 'markedNeeded') ctx.ui.anim.rings(el); },
    actions: { again: (ctx) => { ctx.toast('Counted again · Smoothness recomputed', { icon: 'check', action: 'Undo' }); ctx.back(); return false; } },
    spec: {
      purpose: 'Let a driver who braked hard for a good reason mark it as needed so they are never penalised for doing the right thing (F-SCO-05); the score recomputes within 1 s and no further questions are asked.',
      layout: ['Small sheet (iOS 0.32 fraction · Android small ModalBottomSheet), auto height, over the evidence sheet or the Story.', 'Header “Was it needed?” · event chip (pin 30, type, place · time, peak g) · one explanatory paragraph · Yes, it was needed (filled, block) · No, count it (text).', 'markedNeeded: hollow pin, struck-through g, mini ring 78 with the recomputed score + “It doesn’t count. Smoothness recomputed without it: 83. Tips won’t be about this stop.” + Done.', 'countItAgain: “Count it again?” + paragraph + Count it again (filled) · Keep it as needed (text).'],
      components: ['sheetHeader', 'button', 'ring'],
      interactions: ['Yes → markedNeeded (≤ 1 s recompute, haptic tap); Done → back.', 'No, count it → back with nothing changed.', 'Count it again → restored (toast with Undo), back.'],
      motion: ['Recompute: the ring arc tweens to the new value with spring.default and the digits roll (contentTransition numericText). RM: value swaps.'],
      a11y: ['Focus lands on the title; buttons are full width ≥ 50 pt / 56 dp. The needed state is said in words, not only by the hollow pin.'],
      platformDiffs: { ios: 'Small glass sheet; glassProminent primary.', android: 'Small ModalBottomSheet; filled + text buttons.' },
      analytics: ['event_marked_needed {type, source}', 'event_unmarked'],
      copy: { Ask: 'Was it needed? If you braked for a good reason, like a light changing or someone stepping out, we won’t count it. You can change your mind any time.', Buttons: 'Yes, it was needed · No, count it', Done: 'Marked as needed · It doesn’t count. Smoothness recomputed without it: {score}. Tips won’t be about this stop.', Again: 'Count it again? You marked this as needed, so it doesn’t count. Count it again if it wasn’t. · Count it again · Keep it as needed', Toast: 'Counted again · Smoothness recomputed' },
      freePro: 'Free.',
      data: 't20261020-0740 e3 (hard braking at Spadina & King, needed: true in the fixture; the default state shows it before marking). The recomputed score is the fixture score (83), which already excludes the needed event; the pre-exclusion score isn’t in the fixtures, so it isn’t shown.',
      icons: ['check', 'close'],
      assets: [],
    },
  });

  /* ---------- S-SCO-04 · Coaching tip detail ---------- */
  const HOW = {
    braking: ['Lift off a little earlier and let the car slow on its own.', 'Brake gently first, firmer in the middle, then ease off in the last metre.', 'Aim to stop where you planned, without a final nod.'],
    anticipation: ['Look further ahead for brake lights and changing signals.', 'Leave a longer gap so you can lift off instead of braking.', 'Coast up to the queue rather than closing it fast.'],
    acceleration: ['Roll onto the throttle; squeeze rather than step.', 'Build speed over a few seconds, not one.', 'Let the car settle before adding more.'],
    cornering: ['Do your braking in a straight line before the turn.', 'Turn in once, smoothly, and hold one steady arc.', 'Unwind the wheel as you gently add throttle.'],
    consistency: ['Pick a steady speed and hold it.', 'Make small, early adjustments instead of big ones.', 'Match the flow so you change speed less often.'],
  };
  screen('S-SCO-04', {
    name: 'Coaching tip detail',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'push', tab: 'drives',
    states: [{ id: 'default', label: 'Def' }, { id: 'withEvidenceTrace', label: 'With evidence trace' }, { id: 'noTip', label: 'No tip (Unscorable)' }],
    stateParams: { default: { trip: 't20261105-1745' }, withEvidenceTrace: { trip: 't20261027-1728' }, noTip: { trip: 't20261106-1210' } },
    backTo: 'S-TRP-01',
    render(ctx) {
      const { ui, icons } = ctx, esc = ui.esc;
      const t = tripOf(ctx, 't20261105-1745');
      const navO = { title: 'One thing for next time', subtitle: `${t.partOfDay} · ${t.label}` };
      if (!t.tip || ctx.state === 'noTip') {
        return ui.page(`<div class="tip"><div class="ui-pad">${ui.card(ui.empty({ art: `<div class="tip-none">${icons.quality('Unscorable', { size: 44 })}</div>`, title: 'No tip this time.', text: `We couldn’t score this drive fairly (${(t.quality.reason || 'not enough good data').split(':')[0].toLowerCase()}), so there’s nothing fair to coach.`, action: { text: 'Where to put your phone', nav: 'S-DQ-02/fromLimitedTrip', params: { trip: t.id } }, secondary: { text: 'See the data quality', nav: 'S-DQ-01/unscorable', params: { trip: t.id } } }))}</div></div>`, { nav: navO });
      }
      const ref = ctx.params.event ? evOf(t, ctx.params.event) : null;
      const tip = ref ? tipFor(ctx, t, ref) : { text: t.tip.text, mine: true };
      const e = ref || (t.tip.eventId ? evOf(t, t.tip.eventId) : null);
      const cat = ref && !tip.mine ? (ref.type === 'brake' ? 'braking' : ref.type === 'accel' ? 'acceleration' : 'cornering') : t.tip.category;
      const clean = t.cleanest ? ui.card(`<div class="trp-clean"><span>${ui.icon('sparkle', 18)}</span><div><span class="trp-k">Cleanest stretch</span><p>${esc(t.cleanest.text)}</p></div></div>`) : '';
      const hero = ui.card(`<div class="tip-hero"><span class="tip-k">${tip.mine ? 'This drive’s tip' : 'Next time'}</span><p>${esc(tip.text)}</p><span class="tip-place">${ui.icon('pin', 14)}${esc(e ? cap1(e.place) : t.tip.place)}</span></div>`, { hero: true, cls: 'tip-card' });
      const why = e
        ? `<p class="tip-why">It’s the moment that cost the most smoothness${tip.mine ? ' on this drive' : ' here'}: ${esc(evLabel(ctx, e.type).toLowerCase())} at ${esc(e.place)}, peaking at ${e.peakG.toFixed(2)} g from ${e.speedBeforeKmh} to ${e.speedAfterKmh} km/h.</p>${ctx.state === 'withEvidenceTrace' && e.evidence ? `<div class="evd-trace">${traceHtml(ctx, e, ctx.size.w - 88, 118)}<div class="evd-leg"><span><i class="is-sp"></i>Speed</span><span><i class="is-lo"></i>Long G</span><span><i class="is-la"></i>Lat G</span></div></div>` : e.evidence ? `<div class="tip-spark">${ui.sparkline(e.evidence.lonG, { w: ctx.size.w - 88, h: 56, zero: true, peak: 50, label: 'Braking and acceleration around the moment' })}</div>` : ''}${ui.button('See the moment', { kind: 'tonal', block: true, icon: 'replay', nav: tip.mine ? 'S-RPL-02/thisDrivesTip' : 'S-RPL-02', params: { trip: t.id, event: e.id } })}`
        : `<p class="tip-why">No stop crossed the hard-braking line on this drive, so this tip comes from your firmest stop, at ${esc(t.tip.place)}. Small things make the difference at this level.</p>`;
      const steps = `<ol class="tip-steps">${(HOW[cat] || HOW.braking).map((x) => `<li>${esc(x)}</li>`).join('')}</ol>`;
      const body = `<div class="tip"><div class="ui-pad">${clean}</div><div class="ui-pad">${hero}</div>${ui.section('Why this one', `<div class="ui-pad">${ui.card(why)}</div>`, { style: 'caption' })}${ui.section('How to try it', `<div class="ui-pad">${ui.card(steps)}</div>`, { style: 'caption', footer: 'One tip per drive, always after something that went well. Tips never set speed targets.' })}</div>`;
      return ui.page(body, { nav: navO });
    },
    mount(el, ctx) { if (still(ctx, el)) return; ctx.ui.anim.rise(el, '.tip > *', { stagger: 40 }); el.querySelectorAll('.evd-trace .ui-trace path, .tip-spark path').forEach((p) => ctx.motion.drawOn(p, { duration: 900, delay: 300 })); },
    spec: {
      purpose: 'One specific, evidence-backed thing to try next time (F-SCO-06), tied to a place and an event, always after a positive observation.',
      layout: ['Push, title “One thing for next time” + part of day · label.', 'Positive card first: ✦ “Cleanest stretch” + trip.cleanest.text.', 'Hero tip card (accent 9% tint, radius hero): kicker “This drive’s tip” (or “Next time” for another event’s library tip) · tip text (`sectionTitle`) · place chip.', '“Why this one” card: one derived sentence (event type, place, peak g, speed before → after, neutral) + long-G sparkline (default) or the full −10…+10 s evidence trace (withEvidenceTrace) + See the moment (tonal) → S-RPL-02. Sub-threshold tips (no event) explain they come from the firmest stop.', '“How to try it” card: three numbered steps for the tip category; footer about one tip per drive, no speed targets.', 'noTip: empty state with the Unscorable glyph, “No tip this time.”, placement guide (primary) and data quality (secondary).'],
      components: ['navBar', 'card', 'sparkline', 'evidenceTrace', 'button', 'section', 'empty'],
      interactions: ['See the moment → S-RPL-02 (thisDrivesTip) · Where to put your phone → S-DQ-02 · See the data quality → S-DQ-01/unscorable · Back.'],
      motion: ['Blocks rise 8 px (40 ms stagger); the trace/sparkline draws on 900 ms. RM: static.'],
      a11y: ['Tip card reads kicker + tip + place. Steps are an ordered list. Speed is plain text. XL: cards reflow.'],
      platformDiffs: { ios: 'Inline title; hero card radius 32.', android: 'MediumFlexibleTopAppBar; hero card radius 28 surface-container-high.' },
      analytics: ['tip_viewed {tip_id}', 'tip_evidence_opened {tip_id}'],
      copy: { Why: 'It’s the moment that cost the most smoothness on this drive: {type} at {place}, peaking at {g} g from {before} to {after} km/h.', 'Why (no event)': 'No stop crossed the hard-braking line on this drive, so this tip comes from your firmest stop, at {place}. Small things make the difference at this level.', Steps: 'braking · anticipation · acceleration · cornering · consistency: three steps each (see HOW in wp-c.js)', Footer: 'One tip per drive, always after something that went well. Tips never set speed targets.', 'No tip': 'No tip this time. We couldn’t score this drive fairly ({reason}), so there’s nothing fair to coach.' },
      freePro: 'Free.',
      data: 'trip.tip + trip.cleanest + the tip event’s evidence. States: default t20261105-1745 (Kerr St) · withEvidenceTrace t20261027-1728 (Spadina & Lake Shore) · noTip t20261106-1210. “How to try it” steps are coaching copy per tip category (not fixtures); the “why” sentence is derived from the event.',
      icons: ['sparkle', 'pin', 'replay'],
      assets: [],
    },
  });

  /* ---------- S-SCO-05 · Weekly focus detail ---------- */
  const FOCUS_HABITS = [{ id: 'braking', label: 'Braking into stops' }, { id: 'acceleration', label: 'Smoother starts' }, { id: 'cornering', label: 'Steadier cornering' }, { id: 'consistency', label: 'Holding a steady speed' }];
  screen('S-SCO-05', {
    name: 'Weekly focus detail',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'push', tab: 'insights',
    states: [{ id: 'default', label: 'Def' }, { id: 'improving', label: 'Improving' }, { id: 'newFocus', label: 'New focus' }],
    stateParams: { default: { recap: 'w2026-10-26' }, improving: { recap: 'w2026-11-02' }, newFocus: { recap: 'w2026-10-19' } },
    backTo: 'S-STA-01',
    render(ctx) {
      const { ui, data, D } = ctx, esc = ui.esc;
      const r = data.recap(ctx.params.recap) || D.recaps[1];
      const f = r.focus;
      const idx = D.recaps.indexOf(r);
      const weeks = [D.recaps[idx + 2], D.recaps[idx + 1], r];
      const drives = data.trips((t) => t.smoothness && t.type === 'driver' && t.start.slice(0, 10) >= r.weekStart && t.start.slice(0, 10) <= r.weekEnd).reverse();
      const gentle = (t) => t.smoothness.contributors.find((c) => c.id === 'braking').band !== 'rougher';
      const dots = drives.map((t) => `<i class="${gentle(t) ? 'is-on' : ''}" title="${esc(data.weekday(t.start))}"></i>`).join('');
      const meter = weeks.map((w, i) => { const v = w && w.focus ? w.focus.gentler / w.focus.of : null; return `<div class="fc-bar ${i === 2 ? 'is-cur' : ''}"><span class="fc-b"><i style="height:${v == null ? 0 : Math.max(6, v * 100)}%"></i></span><b>${w && w.focus ? `${w.focus.gentler} of ${w.focus.of}` : '—'}</b><small>${w ? data.date(w.weekStart) : ''}</small></div>`; }).join('');
      const prev = D.recaps[idx + 1] && D.recaps[idx + 1].focus;
      const banner = ctx.state === 'improving' && prev ? ui.banner({ kind: 'positive', icon: 'up', title: `Getting gentler: ${f.gentler} of ${f.of} this week`, text: `Up from ${prev.gentler} of ${prev.of} last week. When it stays gentle for three weeks, we’ll suggest a new focus.` })
        : ctx.state === 'newFocus' ? ui.banner({ kind: 'info', icon: 'sparkle', title: `New focus: ${f.habit}`, text: 'We picked it from your recent drives: braking is where your score moved most.' }) : '';
      const hero = ui.card(`<div class="fc-hero" aria-label="${esc(`${f.line}`)}"><span class="fc-k">Your focus · week of ${data.date(r.weekStart)}</span><b class="fc-h">${esc(cap1(f.habit))}</b><div class="fc-n"><b class="ui-num">${f.gentler}<span class="ui-unit">of ${f.of}</span></b><span>drives had gentler stops</span></div><div class="fc-dots" aria-hidden="true">${dots}</div></div>`, { hero: true, tint: 'accent' });
      const rows = drives.map((t) => ui.row({ lead: `<span class="fc-g">${ui.glyph(t.glyph, 30)}</span>`, title: t.label, sub: `${data.weekday(t.start)} · ${t.smoothness.contributors.find((c) => c.id === 'braking').reason}`, value: gentle(t) ? '<span class="fc-v is-on">▲ Gentler</span>' : '<span class="fc-v">● Not this time</span>', nav: 'S-TRP-01', params: { trip: t.id } }));
      const pick = ctx.store.pick ? `<div class="ui-pad">${ui.card(`<b class="fc-pt">Choose a different focus</b>${ui.radios(FOCUS_HABITS, 'braking', { action: 'focus' })}`)}</div>` : '';
      const body = `<div class="fc">${banner ? `<div class="ui-pad">${banner}</div>` : ''}<div class="ui-pad">${hero}</div>${ui.section('The last three weeks', `<div class="ui-pad">${ui.card(`<div class="fc-meter" role="img" aria-label="${esc(weeks.map((w) => (w && w.focus ? `${data.date(w.weekStart)}: ${w.focus.gentler} of ${w.focus.of}` : '')).filter(Boolean).join('. '))}">${meter}</div>`)}</div>`, { style: 'caption', footer: 'Each week counts on its own. Missing a drive or a week never resets anything.' })}${ui.section('Drives this week', ui.list(rows), { style: 'caption' })}${ui.section('How to practise', `<div class="ui-pad">${ui.card(`<ol class="tip-steps">${HOW.braking.map((x) => `<li>${esc(x)}</li>`).join('')}</ol>`)}</div>`, { style: 'caption' })}${pick}${ui.section('', ui.list([ui.row({ icon: 'edit', title: 'Choose a different focus', sub: 'One focus at a time', action: 'pick' })]), { style: 'caption' })}</div>`;
      return ui.page(body, { nav: { title: 'Weekly focus', subtitle: `Week of ${data.date(r.weekStart)}` } });
    },
    mount(el, ctx) { if (still(ctx, el)) return; el.querySelectorAll('.fc-b i').forEach((b, i) => ctx.motion.animate(b, [{ transform: 'scaleY(0)' }, { transform: 'scaleY(1)' }], { preset: 'default', delay: 200 + i * 70 })); el.querySelectorAll('.fc-dots i').forEach((d, i) => ctx.motion.animate(d, [{ transform: 'scale(0)' }, { transform: 'scale(1)' }], { preset: 'fast', delay: 150 + i * 40 })); },
    actions: { pick: (ctx) => { ctx.store.pick = !ctx.store.pick; ctx.update(); return false; }, focus: (ctx, arg) => { const h = FOCUS_HABITS.find((x) => x.id === arg); ctx.store.pick = false; ctx.toast(arg === 'braking' ? 'Keeping braking into stops' : `New focus from your next drive: ${h.label.toLowerCase()}`, { icon: 'check' }); ctx.update(); return false; } },
    spec: {
      purpose: 'One habit to focus on for a few weeks with a check on progress, so coaching has follow-through (F-SCO-10). It is never a streak (D7).',
      layout: ['Push (Insights tab), title “Weekly focus” + “Week of 26 Oct”.', 'State banner: improving (positive, “Getting gentler: 3 of 5 this week · Up from 1 of 5 last week…”) · newFocus (info, “New focus: braking into stops…”).', 'Hero card (accent tint): kicker “Your focus · week of {date}” · habit (sectionTitle) · “3 of 5” (DP Numerals 48, “of 5” as unit) + “drives had gentler stops” · one dot per scored drive in drive order (filled Glide = gentler) — drives, not days, and no chain.', '“The last three weeks”: three bars (height = gentler/of, current week in Glide) with “4 of 6 · 19 Oct” labels; footer “Each week counts on its own. Missing a drive or a week never resets anything.”', '“Drives this week”: each scored drive (glyph, label, weekday · braking reason) with ▲ Gentler / ● Not this time → S-TRP-01.', '“How to practise”: three braking steps.', '“Choose a different focus” → inline radio card (Braking into stops · Smoother starts · Steadier cornering · Holding a steady speed).'],
      components: ['navBar', 'banner', 'card', 'section', 'list', 'row', 'glyph', 'radios'],
      interactions: ['Drive row → S-TRP-01 · Choose a different focus → radios; picking another → toast “New focus from your next drive: …”.'],
      motion: ['Hero: bars grow from the baseline (spring.default, 70 ms stagger) and dots pop (spring.fast, 40 ms stagger). RM: static.'],
      a11y: ['Hero label = recap.focus.line (“Your focus: braking into stops. Gentler in 3 of 5 drives this week.”). Bars have a text summary. ▲/● plus words, never colour alone.', 'No streak, “don’t break it” or consecutive-day language anywhere (D7).'],
      platformDiffs: { ios: 'Inline title; hero card tinted 18% accent.', android: 'MediumFlexibleTopAppBar; tonal hero card; radios as M3 RadioButtons.' },
      analytics: ['focus_viewed {habit}', 'focus_changed {by: user}'],
      copy: { Kicker: 'Your focus · week of {date}', Count: '{gentler} of {of} drives had gentler stops', Improving: 'Getting gentler: {x} of {y} this week. Up from {a} of {b} last week. When it stays gentle for three weeks, we’ll suggest a new focus.', New: 'New focus: braking into stops. We picked it from your recent drives: braking is where your score moved most.', Footer: 'Each week counts on its own. Missing a drive or a week never resets anything.', Change: 'Choose a different focus · One focus at a time' },
      freePro: 'Free.',
      data: 'recaps[].focus (habit, line, gentler, of) for the chosen week and the two before it. States: default w2026-10-26 (1 of 5) · improving w2026-11-02 (3 of 5, up from 1) · newFocus w2026-10-19 (first focus week, 4 of 6). Per-drive “gentler” = that trip’s braking contributor is not “rougher” (derived; it reproduces the fixture counts exactly).',
      icons: ['up', 'sparkle', 'edit'],
      assets: [],
    },
  });

  /* ---------- S-SCO-06 · Efficiency habits detail ---------- */
  const cvWord = (cv) => (cv == null ? 'Too short to tell' : cv < 0.15 ? 'Steady' : cv < 0.2 ? 'Mostly steady' : 'Variable');
  screen('S-SCO-06', {
    name: 'Efficiency habits detail',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'push', tab: 'insights',
    states: [{ id: 'default', label: 'Def' }, { id: 'calibrating', label: 'Calibrating' }, { id: 'evCarVsPetrolCarCopy', label: 'EV car vs petrol car copy' }],
    stateParams: { default: { trip: 't20261105-1745' }, calibrating: { trip: 't20260928-1842' }, evCarVsPetrolCarCopy: { trip: 't20261017-1120' } },
    backTo: 'S-TRP-01',
    render(ctx) {
      const { ui, data, icons } = ctx, esc = ui.esc;
      const t = tripOf(ctx, 't20261105-1745'), ef = t.efficiency, v = data.vehicle(t.vehicleId), ev = v && v.powertrain === 'ev';
      const cal = ctx.state === 'calibrating' || (t.smoothness && t.smoothness.calibrating && t.smoothness.calibrating.k === 1);
      const glyph = cal ? '●' : ef.rating === 'better' ? '▲' : ef.rating === 'usual' ? '●' : '▼';
      const hero = ui.card(`<div class="eff-hero"><span class="eff-k">${icons.provenance('estimated', { size: 13 })}Efficiency habits · estimated</span><b class="eff-h ${ef.rating === 'better' && !cal ? 'is-up' : ''}"><span aria-hidden="true">${glyph}</span>${esc(cal ? 'Learning your usual' : ef.label)}</b><p>${esc(ef.copy)}</p>${v ? ui.carChip(v) : ''}</div>`, { hero: true, tint: v ? `car-${v.colour}` : null });
      const tiles = ui.stats([
        { label: 'Stop-starts', value: ef.stopsPerKm.toFixed(2), unit: '/km', unitSpoken: 'per kilometre', prov: 'estimated', est: true },
        { label: 'Abrupt starts', value: ef.abruptAccels, prov: 'derived' },
        { label: 'Steady speed', value: cvWord(ef.speedConsistencyCv), prov: 'estimated' },
      ], { cols: 3, cls: 'eff-tiles' });
      const other = ctx.D.trips.find((x) => x.efficiency && data.vehicle(x.vehicleId)?.powertrain !== (ev ? 'ev' : 'petrol'));
      const ov = other && data.vehicle(other.vehicleId);
      const compare = ctx.state === 'evCarVsPetrolCarCopy' && ov ? ui.section('Worded for each car', ui.list([ui.row({ lead: ui.carDot(v), title: `${v.nickname} · ${ev ? 'electric' : 'petrol'}`, sub: ef.copy }), ui.row({ lead: ui.carDot(ov), title: `${ov.nickname} · ${ov.powertrain === 'ev' ? 'electric' : 'petrol'}`, sub: other.efficiency.copy })]), { style: 'caption', footer: 'EVs talk about range and the car’s display; petrol cars about fuel use and the trip computer.' }) : '';
      const banner = cal ? `<div class="ui-pad">${ui.banner({ kind: 'info', icon: 'info', title: 'Calibrating · 1 of 3', text: 'After three drives we’ll compare these habits with your usual. Until then, here’s what we saw.' })}</div>` : '';
      const body = `<div class="eff">${banner}<div class="ui-pad">${hero}</div>${ui.section('This drive', `<div class="ui-pad">${tiles}</div>`, { style: 'caption', footer: 'Fewer stop-starts, gentle starts and a steady speed are habits that usually help efficiency.' })}${compare}${ui.section('What this is', `<div class="ui-pad">${ui.card(`<div class="eff-honest">${ui.icon('shield', 20)}<p>A habit rating, not a measurement. DrivePerf can’t see your ${ev ? 'battery' : 'fuel'} use, so it never shows ${ev ? 'kWh' : 'litres'}, money or “% saved”. Check your car’s ${ev ? 'display' : 'trip computer'} for your number.</p></div>`)}</div>`, { style: 'caption' })}${ui.section('', ui.list([ui.row({ icon: 'info', title: 'How scores work', nav: 'S-SCO-02' })]), { style: 'caption' })}</div>`;
      return ui.page(body, { nav: { title: 'Efficiency habits', subtitle: `${t.partOfDay} · ${t.label}` } });
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '.ui-stat', { stagger: 40, delay: 120 }); },
    spec: {
      purpose: 'Show whether the drive’s habits were efficiency-friendly without fake energy numbers (F-SCO-09): a proxy from stop-starts per km, abrupt accelerations and speed consistency.',
      layout: ['Push (Insights tab), title “Efficiency habits” + part of day · label.', 'Calibrating banner (first drives).', 'Hero card tinted with the car colour: kicker “≋ Efficiency habits · estimated” (dashed-ring glyph) · rating with ▲/●/▼ (Better than usual / Usual / Less efficient than usual) · trip.efficiency.copy · car chip.', '“This drive”: three tiles — Stop-starts ≈ 0.05 per km (estimated) · Abrupt starts 0 (derived) · Speed consistency in words (Steady / Mostly steady / Variable, estimated).', 'evCarVsPetrolCarCopy: “Worded for each car” list with the EV and petrol copy side by side.', '“What this is” honesty card (never litres, kWh, money or % saved) · How scores work row.'],
      components: ['navBar', 'banner', 'card', 'carChip', 'stats', 'section', 'list', 'row', 'carDot'],
      interactions: ['How scores work → S-SCO-02 · Back → S-TRP-01 / S-GAR-02.'],
      motion: ['Tiles rise 8 px (40 ms stagger). RM: static.'],
      a11y: ['Rating: “Efficiency habits, estimated. Usual.” Tiles: “Stop-starts, about 0.05 per kilometre. Estimated.” Estimated values use ≈ and the dashed-ring glyph, not colour alone.'],
      platformDiffs: { ios: 'Inline title; hero tinted 18% car colour.', android: 'MediumFlexibleTopAppBar; tonal hero.' },
      analytics: ['efficiency_viewed'],
      copy: { Petrol: 'Smoother habits like these usually help fuel use. Check your car\'s trip computer for your number.', EV: 'Smoother habits like these usually help range. Check your car\'s display to see your number.', Honest: 'A habit rating, not a measurement. DrivePerf can’t see your {fuel|battery} use, so it never shows {litres|kWh}, money or “% saved”. Check your car’s {trip computer|display} for your number.', Calibrating: 'Calibrating · 1 of 3. After three drives we’ll compare these habits with your usual. Until then, here’s what we saw.' },
      freePro: 'Free.',
      data: 'trip.efficiency (rating, label, stopsPerKm, abruptAccels, speedConsistencyCv, copy). States: default t20261105-1745 (GTI, Usual) · calibrating t20260928-1842 (first drive) · evCarVsPetrolCarCopy t20261017-1120 (Ioniq) with the petrol copy from the newest GTI trip. Consistency words are derived from the CV (< 0.15 Steady, < 0.20 Mostly steady, else Variable).',
      icons: ['shield', 'info'],
      assets: [],
    },
  });

  /* ==========================================================================================================
     S-NTF-02 · Missed-trip check sheet                                                          F-DQ-07
     ========================================================================================================== */
  screen('S-NTF-02', {
    name: 'Missed-trip check sheet',
    wp: 'C', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'drives', detent: 'auto', over: 'S-HOME-01',
    states: [{ id: 'default', label: 'Prompt' }, { id: 'thanks', label: 'Thanks' }, { id: 'iWasAPassenger', label: '“I was a passenger”' }],
    render(ctx) {
      const { ui, icons, D } = ctx;
      const n = D.notifications.find((x) => x.type === 'missedTrip');
      const local = !D.user.consent.usageStats;
      const privacy = `<p class="mt-priv">${ui.icon('shield', 14)}${local ? 'Your answer stays on this phone. Nothing about where you went is recorded.' : 'Only your answer is shared, never where you went.'}</p>`;
      if (ctx.state === 'thanks') return `<div class="mt">${ui.sheetHeader('Thanks')}<div class="mt-art is-ok">${ui.icon('check', 30)}</div><p class="mt-p"><b>We’ll use this to tune detection on this phone.</b> A missed drive can’t be recovered, but this helps catch the next one.</p>${ui.list([ui.row({ icon: 'health', title: 'Run Health Check', sub: 'See if anything is slowing auto-detect', nav: 'S-SET-05' })])}${ui.button('Done', { kind: 'filled', block: true, back: true })}</div>`;
      if (ctx.state === 'iWasAPassenger') return `<div class="mt">${ui.sheetHeader('Got it')}<div class="mt-art">${ui.icon('passenger', 30)}</div><p class="mt-p"><b>Rides as a passenger aren’t drives, so nothing’s missing.</b> If DrivePerf ever records one, tap “I was a passenger” and it won’t be scored.</p>${ui.button('Done', { kind: 'filled', block: true, back: true })}</div>`;
      return `<div class="mt">${ui.sheetHeader(n.title, { icon: icons.loop({ size: 26, color: 'var(--dp-accent)', dot: 'var(--dp-text)' }), sub: 'Your phone’s motion history showed time in a vehicle, but no drive was recorded.' })}<p class="mt-p">${ui.esc(n.body)}</p><div class="mt-acts">${ui.button('Yes, I drove', { kind: 'filled', block: true, state: 'thanks', haptic: 'tap' })}${ui.button('I was a passenger', { kind: 'tonal', block: true, icon: 'passenger', state: 'iWasAPassenger' })}${ui.button('No, I didn’t', { kind: 'text', block: true, toast: 'Thanks. Nothing to fix.', back: true })}</div>${privacy}</div>`;
    },
    mount(el, ctx) { const a = el.querySelector('.mt-art'); if (a) ctx.ui.anim.pop(a, 80); },
    spec: {
      purpose: 'An easy way to say “you missed a drive”, so detection improves and the user trusts it (F-DQ-07). Offered at most once a day, held until not driving.',
      layout: ['Medium sheet (auto height) over Home (from the missed-trip notification) or Health Check.', 'Prompt: Loop mark + notification title “Did you drive around 5 pm?” + context line · body “We may have missed it. Tell us and we’ll tune detection.” · Yes, I drove (filled) · I was a passenger (tonal, passenger glyph) · No, I didn’t (text) · privacy line with shield.', 'Thanks: check badge · “We’ll use this to tune detection on this phone.” + Health Check row → S-SET-05 + Done.', 'I was a passenger: passenger badge · explanation + Done.'],
      components: ['sheetHeader', 'button', 'list', 'row'],
      interactions: ['Yes → thanks · I was a passenger → iWasAPassenger · No → toast + back · Health Check → S-SET-05 · Done → back. Answer stored locally; with usage analytics on, sent as missed_trip_report (no location).'],
      motion: ['spring.sheet; the badge pops (spring.default). RM: cross-fade.'],
      a11y: ['Focus on the title; three full-width buttons ≥ 50 pt / 56 dp; the privacy line is plain text.'],
      platformDiffs: { ios: 'Glass medium sheet; glassProminent primary.', android: 'ModalBottomSheet; filled / tonal / text buttons.' },
      analytics: ['missed_trip_prompted', 'missed_trip_report {answer: drove|passenger|no, hour_bucket}'],
      copy: { Prompt: 'Did you drive around 5 pm? · We may have missed it. Tell us and we’ll tune detection.', Buttons: 'Yes, I drove · I was a passenger · No, I didn’t', Privacy: 'Your answer stays on this phone. Nothing about where you went is recorded. (usage analytics off) | Only your answer is shared, never where you went. (on)', Thanks: 'We’ll use this to tune detection on this phone. A missed drive can’t be recovered, but this helps catch the next one.', Passenger: 'Rides as a passenger aren’t drives, so nothing’s missing. If DrivePerf ever records one, tap “I was a passenger” and it won’t be scored.' },
      freePro: 'Free.',
      data: 'DP_DATA.notifications n_missed (title, body); DP_DATA.user.consent.usageStats (false) picks the privacy line.',
      icons: ['check', 'passenger', 'health', 'shield'],
      assets: ['logo-a-loop.svg'],
    },
  });
})();
