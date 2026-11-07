/* WP-E · Sharing, account, privacy & subscription — screen definitions (DRIVE-32). Contract: prototypes/app/AUTHORING.md.
   Register each screen with DP.screen('S-XXX-NN', {...}). Only the WP-E owner edits this file.
   Screens: S-SHR-01…04 · S-ACC-01…07 · S-PRV-01…07 · S-SUB-01…07.
   Binding rules honoured here: no speed / max G / times / places on share cards (F-SHR-01.2); no streaks; paywalls never
   during a trip (the D1 gate holds S-SHR-* and S-SUB-02/03/04) or after a worse-than-usual Story; Monthly and Annual have
   equal weight, and "Continue with Free" has the same weight as the purchase button (F-SUB-03.2). */
(function () {
  const { screen } = window.DP;
  const D = window.DP_DATA, U = D.util;

  /* ==========================================================================================================
     Local helpers (candidates for core primitives; listed in the WP-E report)
     ========================================================================================================== */
  const still = (ctx, el) => ctx.shot || ctx.motion.reduced(el);
  const PRICES = D.entitlement.prices;
  const regionOf = (ctx) => (ctx.params.region === 'US' ? 'US' : ctx.params.region === 'CA' ? 'CA' : D.user.storefront === 'US' ? 'US' : 'CA');
  const storeName = (ctx) => (ctx.ios ? 'App Store' : 'Google Play');
  const dateLong = (iso) => U.fmtDate(iso, { day: 'numeric', month: 'long', year: 'numeric' });

  /** Illustration bubble (components.md §21 empty art, 96–120 px): an icon or html on a Glide halo (Android: M3E Arch). */
  function art(ctx, inner, o = {}) {
    const body = inner.startsWith('<') ? inner : ctx.ui.icon(inner, o.icon || 40);
    return `<div class="e-art ${o.tone ? 'is-' + o.tone : ''} ${o.cls || ''}" aria-hidden="true">${body}</div>`;
  }
  /** ✓ / ✕ / lock checklist (components.md §16 "what you get / what we never do"). items: [{kind:'ok'|'no'|'lock'|'dot', text, sub}] */
  function checks(ctx, items, o = {}) {
    const g = { ok: 'check', no: 'close', lock: 'lock', dot: 'info' };
    return `<ul class="e-checks ${o.cls || ''}">${items.filter(Boolean).map((it) => `<li class="is-${it.kind || 'ok'}"><span class="e-checks-g">${ctx.ui.icon(g[it.kind || 'ok'], 16)}</span><span><b>${ctx.ui.esc(it.text)}</b>${it.sub ? `<small>${ctx.ui.esc(it.sub)}</small>` : ''}</span></li>`).join('')}</ul>`;
  }
  /** Determinate progress bar with a spoken label. */
  function progress(ctx, frac, o = {}) {
    const pct = Math.round(Math.max(0, Math.min(1, frac)) * 100);
    return `<div class="e-prog ${o.cls || ''}" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${pct}" aria-label="${ctx.ui.esc(o.label || pct + ' percent')}"><i style="width:${pct}%"></i></div>`;
  }
  /** Numbered steps (how a flow works). */
  const steps = (ctx, items) => `<ol class="e-steps">${items.map((s, i) => `<li><span class="e-steps-n">${i + 1}</span><span><b>${ctx.ui.esc(s.title)}</b>${s.text ? `<small>${ctx.ui.esc(s.text)}</small>` : ''}</span></li>`).join('')}</ol>`;
  /** Step indicator for multi-step flows ("Step 2 of 3"). */
  const stepper3 = (ctx, n, of, label) => `<div class="e-stepdots" aria-label="Step ${n} of ${of}: ${ctx.ui.esc(label)}">${Array.from({ length: of }, (_, i) => `<i class="${i < n ? 'on' : ''}"></i>`).join('')}<span>Step ${n} of ${of} · ${ctx.ui.esc(label)}</span></div>`;
  /** Fixed bottom action dock (glass on iOS, surface on Android) for full-screen builders and flows. */
  const dock = (html, o = {}) => `<div class="e-dock ${o.cls || ''}">${html}</div>`;
  /** Two equal-weight stacked buttons (F-SUB-03.2): same size, same type style, the secondary is not greyed. */
  function equalPair(ctx, a, b) {
    const { ui } = ctx;
    return `<div class="e-pair">${ui.button(a.text, { kind: 'filled', size: 'l', block: true, cls: 'e-eq', ...a })}${ui.button(b.text, { kind: 'tonal', size: 'l', block: true, cls: 'e-eq e-eq--free', ...b })}</div>`;
  }

  /** System share sheet mock (non-interactive OS frame; one tap anywhere closes it). */
  function osShare(ctx, o = {}) {
    const { ui } = ctx;
    const apps = ctx.ios ? ['AirDrop', 'Messages', 'Mail', 'Notes'] : ['Quick Share', 'Messages', 'Gmail', 'Drive'];
    const rows = ctx.ios ? ['Copy', 'Save Image', 'Save to Files'] : [];
    const thumb = `<span class="e-os-thumb">${o.thumb || ctx.icons.loop({ size: 26, color: 'var(--dp-accent)', dot: 'var(--dp-text)' })}</span>`;
    return `<div class="e-os" data-action="${o.close || 'closeShare'}" aria-hidden="true"><div class="e-os-sheet ${ctx.ios ? 'is-ios' : 'is-android'}">
      ${ctx.android ? '<i class="e-os-grab"></i>' : ''}
      <div class="e-os-h">${thumb}<span><b>${ui.esc(o.title || 'DrivePerf')}</b><small>${ui.esc(o.sub || '')}</small></span>${ctx.ios ? `<span class="e-os-x">${ui.icon('close', 16)}</span>` : ''}</div>
      ${ctx.android ? `<div class="e-os-quick"><span>${ui.icon('layers', 18)}Copy</span><span>${ui.icon('share', 18)}Nearby</span></div>` : ''}
      <div class="e-os-apps">${apps.map((a, i) => `<span><i style="--h:${[200, 140, 10, 45][i]}"></i>${a}</span>`).join('')}</div>
      ${rows.length ? `<div class="e-os-rows">${rows.map((r) => `<span>${r}${ui.icon(r === 'Copy' ? 'layers' : r === 'Save Image' ? 'export' : 'garage', 18)}</span>`).join('')}</div>` : ''}
    </div><span class="ui-sys-tag">${ctx.ios ? 'iOS share sheet' : 'Android Sharesheet'} · mock</span></div>`;
  }

  /** Store purchase sheet mock (StoreKit / Play Billing; non-interactive OS frame). Tap simulates a completed purchase. */
  function osStore(ctx, o = {}) {
    const { ui } = ctx;
    const icon = `<span class="e-st-icon">${ctx.icons.loop({ size: 34, color: '#5BE7C4', dot: '#fff' })}</span>`;
    const body = ctx.ios
      ? `<div class="e-st-h"><b>App Store</b><span class="e-os-x">${ui.icon('close', 16)}</span></div>
         <div class="e-st-p">${icon}<span><b>DrivePerf Pro</b><small>${ui.esc(o.planName)} · DrivePerf</small><small>Subscription</small></span></div>
         <div class="e-st-rows"><span><em>Price</em>${ui.esc(o.priceLine)}</span><span><em>Renews</em>${ui.esc(o.renewLine)}</span><span><em>Account</em>Your Apple Account</span></div>
         <div class="e-st-side"><b>Double-click to subscribe</b><i></i></div>`
      : `<i class="e-os-grab"></i><div class="e-st-h is-g"><b>Google Play</b></div>
         <div class="e-st-p">${icon}<span><b>DrivePerf Pro</b><small>${ui.esc(o.planName)} · DrivePerf</small></span></div>
         <div class="e-st-rows"><span><em>${ui.esc(o.priceLine)}</em></span><span><em>${ui.esc(o.renewLine)}</em></span><span><em>Payment method on your Google account</em></span></div>
         <span class="e-st-buy">Subscribe</span>`;
    return `<div class="e-os is-store" data-action="${o.action || 'bought'}" aria-hidden="true"><div class="e-os-sheet e-st ${ctx.ios ? 'is-ios' : 'is-android'}">${body}</div><span class="ui-sys-tag">${ctx.ios ? 'StoreKit sheet' : 'Play Billing sheet'} · mock · tap to simulate a purchase</span></div>`;
  }

  /* ---------------- share card (components.md §14; fixtures.md §8: glyph, score, record, car, part of day only) ------------- */
  const SIL = {
    hatch: 'M6 33l3-9c2-5 6-8 13-9l18-7c7-3 15-3 24-2l15 2c7 1 12 4 16 9l8 3c6 2 9 5 9 10v3',
    crossover: 'M5 34l2-10c1-5 5-8 11-9l20-8c7-3 16-3 26-2l14 2c7 1 12 4 15 9l7 2c6 2 9 6 9 11v5',
  };
  /** Generic car silhouette by body style (brand.md §7: no logos or badges). */
  const silhouette = (v, w = 120) => `<svg class="shc-sil" width="${w}" height="${(w * 44) / 120}" viewBox="0 0 120 44" aria-hidden="true"><path d="${SIL[v && v.silhouette] || SIL.hatch}" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/><path d="M4 34h112" stroke="currentColor" stroke-width="1.4" opacity=".35"/><circle cx="29" cy="34" r="7" fill="var(--shc-bg)" stroke="currentColor" stroke-width="2.2"/><circle cx="93" cy="34" r="7" fill="var(--shc-bg)" stroke="currentColor" stroke-width="2.2"/></svg>`;

  const hav = (a, b) => { const R = 6371000, r = Math.PI / 180; const dLa = (b[0] - a[0]) * r, dLo = (b[1] - a[1]) * r; const h = Math.sin(dLa / 2) ** 2 + Math.cos(a[0] * r) * Math.cos(b[0] * r) * Math.sin(dLo / 2) ** 2; return 2 * R * Math.asin(Math.sqrt(h)); };
  /** Deterministic "random" 400–800 m trims for the prototype (the app re-rolls them on every share, F-SHR-02.1). */
  const trims = (t) => { let h = 0; for (const c of t.id) h = (h * 31 + c.charCodeAt(0)) >>> 0; return [400 + (h % 401), 400 + ((h >>> 9) % 401)]; };
  /** Stylised, label-free basemap with the route trimmed at both ends and inside privacy zones (F-SHR-03.2). */
  function mapArt(t, w, h) {
    const pr = U.trackPath(t, w, h, 26);
    const rows = t.track.rows.filter((r) => r[1] != null);
    const cum = [0]; for (let i = 1; i < rows.length; i++) cum.push(cum[i - 1] + hav([rows[i - 1][1], rows[i - 1][2]], [rows[i][1], rows[i][2]]));
    const total = cum[cum.length - 1], [a, b] = trims(t);
    const zones = D.user.privacyZones || [];
    let d = '', pen = false;
    rows.forEach((r, i) => {
      const inZone = zones.some((z) => hav([z.lat, z.lng], [r[1], r[2]]) < z.radiusM);
      if (cum[i] < a || cum[i] > total - b || inZone) { pen = false; return; }
      const [x, y] = pr.project(r[1], r[2]); d += `${pen ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`; pen = true;
    });
    let roads = ''; const seen = new Set([t.routeKey]);
    for (const o of D.trips) {
      if (seen.has(o.routeKey)) continue; seen.add(o.routeKey);
      let p = '', on = false;
      for (const r of o.track.rows) { if (r[1] == null) { on = false; continue; } const [x, y] = pr.project(r[1], r[2]); if (x < -60 || x > w + 60 || y < -60 || y > h + 60) { on = false; continue; } p += `${on ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`; on = true; }
      if (p) roads += `<path d="${p}"/>`;
    }
    const grid = Array.from({ length: Math.ceil(Math.max(w, h) / 22) + 1 }, (_, i) => `M${i * 22} 0V${h}M0 ${i * 22}H${w}`).join('');
    return `<svg class="shc-map" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" aria-hidden="true"><rect width="${w}" height="${h}" fill="var(--dp-data-map-land)"/><path d="${grid}" stroke="var(--dp-data-grid)" stroke-width=".8" fill="none"/><g fill="none" stroke="var(--dp-data-map-road)" stroke-width="2.6" stroke-linecap="round">${roads}</g><path d="${d}" fill="none" stroke="var(--dp-data-route-casing)" stroke-width="7" stroke-linecap="round" stroke-linejoin="round" opacity=".8"/><path class="shc-draw" d="${d}" fill="none" stroke="var(--dp-data-route)" stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
  }

  /** Alt text offered for copy (F-SHR-01.3). Only share-safe fields. */
  function altText(style, t, v) {
    const wk = D.recaps.find((r) => r.complete), arch = D.wrapped.monthly.chapters.find((c) => c.id === 'archetype'), tot = D.wrapped.monthly.chapters.find((c) => c.id === 'totals');
    const pod = t ? t.partOfDay.replace(/^./, (c) => c.toLowerCase()) : '';
    const car = v ? `my ${v.nickname}` : 'my car';
    const s = t && t.smoothness, rec = s && s.record && s.record.isNew ? ' New route record.' : '';
    if (style === 'week') return `My week in DrivePerf: ${wk.drives} drives, average Smoothness ${wk.avgSmoothness}, ${wk.deltaText} on last week.`;
    if (style === 'archetype') return `My October in DrivePerf: ${D.wrapped.archetypes.find((x) => x.id === arch.archetype).name}. ${tot.line.split(' · ')[0]}, average Smoothness ${D.wrapped.monthly.chapters.find((c) => c.id === 'trend').line.replace(/\D+/g, '')}.`;
    if (style === 'ring') return `Smoothness ${s ? s.score : '—'} on a ${pod} drive in ${car}.${rec}`;
    if (style === 'map') return `Map of a ${pod} drive in ${car}, with the start and end hidden.${s ? ` Smoothness ${s.score}.` : ''}`;
    return `Route shape of a ${pod} drive in ${car}.${s ? ` Smoothness ${s.score}.` : ''}${rec}`;
  }

  /** The card itself (always the dark "night" art, data-theme dark). o: {style, ratio:'916'|'45', w, trip, carWash, minimal, building} */
  function shareCard(ctx, o) {
    const { ui } = ctx;
    const W = o.w || 216, H = Math.round(o.ratio === '45' ? W * 1.25 : (W * 16) / 9), k = (W / 270).toFixed(3);
    const t = o.trip, v = t ? U.vehicle(t.vehicleId) : U.vehicle('gti');
    const s = t && t.smoothness, rec = !!(s && s.record && s.record.isNew);
    const is45 = o.ratio === '45';
    const kick = (txt) => `<span class="shc-k">${ui.esc(txt)}</span>`;
    const recLine = rec ? `<span class="shc-rec">${ctx.icons.svg('rosette', { size: 14 })}New route record</span>` : '';
    const ring = (sz) => (s ? ui.ring(s.score, sz, { stroke: Math.round(sz * 0.085), label: sz >= 96, shape: false, aria: `Smoothness ${s.score}` }) : '');
    let body = '';
    if (o.building) {
      body = `<div class="shc-build">${ui.loader({ size: Math.round(W * 0.2), label: 'Building your card' })}<span class="shc-k">Building your card…</span><span class="shc-skel"></span><span class="shc-skel is-s"></span></div>`;
    } else if (o.style === 'ring') {
      body = `<div class="shc-col">${kick(`${t.partOfDay} · ${v ? v.nickname : ''}`)}<div class="shc-hero">${ring(Math.round(W * (is45 ? 0.5 : 0.62)))}</div>${recLine}<span class="shc-car">${silhouette(v, Math.round(W * (is45 ? 0.36 : 0.46)))}</span></div>`;
    } else if (o.style === 'week') {
      const wk = D.recaps.find((r) => r.complete);
      const days = wk.days.map((d) => `<span class="shc-day"><span>${d.drives.map(() => '<i></i>').join('')}</span><b>${d.dow.charAt(0)}</b></span>`).join('');
      body = `<div class="shc-col">${kick('My week, decoded.')}<div class="shc-week"><span class="shc-big ui-num">${wk.avgSmoothness}</span><span class="shc-sub">Average Smoothness</span><span class="shc-trend"><b aria-hidden="true">${wk.delta > 0 ? '▲' : wk.delta < 0 ? '▼' : '●'}</b>${ui.esc(U.signed(wk.delta))} vs last week</span></div><div class="shc-days">${days}</div><span class="shc-sub">${wk.drives} drives · ${Math.round(wk.km)} km</span></div>`;
    } else if (o.style === 'archetype') {
      const ch = D.wrapped.monthly.chapters, a = D.wrapped.archetypes.find((x) => x.id === ch.find((c) => c.id === 'archetype').archetype);
      const ticks = Array.from({ length: 9 }, (_, i) => `<path d="M${14 + i * 14} ${i % 2 ? 30 : 26}V${i % 2 ? 50 : 54}"/>`).join('');
      body = `<div class="shc-col">${kick('October, wrapped.')}<svg class="shc-arch" width="${Math.round(W * 0.6)}" height="${Math.round(W * 0.34)}" viewBox="0 0 140 80" aria-hidden="true"><path d="M6 40H134" stroke="var(--dp-accent)" stroke-opacity=".3" stroke-width="2"/><g stroke="var(--dp-accent)" stroke-width="3" stroke-linecap="round">${ticks}</g><circle cx="126" cy="40" r="5" fill="var(--dp-text)"/></svg><b class="shc-name">${ui.esc(a.name)}</b><span class="shc-sub">${ui.esc(a.line)}</span><div class="shc-two"><span><b class="ui-num">${ch.find((c) => c.id === 'totals').line.split(' ')[0]}</b><small>drives</small></span><span><b class="ui-num">${ch.find((c) => c.id === 'trend').line.replace(/\D+/g, '')}</b><small>avg Smoothness</small></span></div></div>`;
    } else if (o.style === 'map') {
      const mh = Math.round(H * (is45 ? 0.5 : 0.56));
      body = `<div class="shc-mapwrap" style="height:${mh}px">${mapArt(t, W, mh)}<span class="shc-attr">© OpenStreetMap contributors</span></div><div class="shc-col is-map">${kick(`${t.partOfDay} · ${v ? v.nickname : ''}`)}${ring(Math.round(W * (is45 ? 0.3 : 0.36)))}${recLine}</div>`;
    } else {
      const gs = Math.round(W * (is45 ? 0.44 : 0.74));
      body = `<div class="shc-col">${kick(`${t.partOfDay} · ${v ? v.nickname : ''}`)}<div class="shc-glyph">${ui.glyph(t.glyph, gs, { color: 'var(--dp-accent)', width: Math.max(3, gs / 40), pad: 0.1 })}</div>${s ? `<div class="shc-ringrow">${ring(Math.round(W * (is45 ? 0.24 : 0.32)))}${recLine}</div>` : ''}</div>`;
    }
    const mark = o.minimal ? `<span class="shc-mark is-min">${ctx.icons.loop({ size: Math.round(16 * k), color: 'var(--dp-accent)', dot: 'var(--dp-text)' })}</span>`
      : `<span class="shc-mark">${ctx.icons.loop({ size: Math.round(20 * k), color: 'var(--dp-accent)', dot: 'var(--dp-text)' })}<b>DrivePerf</b></span>`;
    const alt = o.building ? 'Building your share card' : altText(o.style, t, v);
    return `<div class="shc shc--${o.style}${is45 ? ' is-45' : ''}${o.carWash ? ' is-wash' : ''}" data-theme="dark" style="--k:${k};--car:var(--dp-car-${v ? v.colour : 'glide'});width:${W}px;height:${H}px" role="img" aria-label="${ui.esc(alt)}">${body}${mark}</div>`;
  }

  /* ==========================================================================================================
     S-SHR-01 · Share card builder (full) → system share sheet
     ========================================================================================================== */
  const SHR_STYLE = { default: 'glyph', ring: 'ring', weekStrip: 'week', archetype: 'archetype', map: 'map', s916And45: 'glyph', shortTrip: 'ring', proStyles: 'glyph', Load: 'glyph' };
  const SHR_STATE = { glyph: 'default', ring: 'ring', week: 'weekStrip', archetype: 'archetype', map: 'map' };
  const SHR_STYLES = [['glyph', 'Route shape', 'routes'], ['ring', 'Ring', 'record'], ['week', 'Week', 'insights'], ['archetype', 'Archetype', 'sparkle'], ['map', 'Map', 'pin']];

  function shrRender(ctx) {
    const { ui, data, params, state } = ctx;
    const t = data.trip(params.trip) || data.trip('t20261105-0742');
    const v = data.vehicle(t.vehicleId);
    if (t.type === 'passenger') {
      return ui.page(`<div class="ui-pad">${ui.card(ui.empty({ icon: 'passenger', title: 'Passenger trips aren’t shared.', text: 'This drive is marked as passenger, so it has no score and no card. Your weekly card is still here.', action: { text: 'Share my week', nav: 'S-SHR-01/weekStrip' } }))}</div>`, { nav: { title: 'Share', close: true, medium: false } });
    }
    const short = state === 'shortTrip' || t.distanceKm < 2;
    const unscorable = !t.smoothness;
    let style = SHR_STYLE[state] || 'glyph';
    if (short && (style === 'glyph' || style === 'map')) style = 'ring';
    if (unscorable && style === 'ring') style = 'glyph';
    const ratio = ctx.store.ratio || '916';
    const wash = state === 'proStyles' ? ctx.store.wash !== false : !!ctx.store.wash;
    const minimal = state === 'proStyles' ? ctx.store.minimal !== false : !!ctx.store.minimal;
    const building = state === 'Load';
    const card = (w, r) => shareCard(ctx, { style, ratio: r, w, trip: t, carWash: wash && ctx.isPro, minimal: minimal && ctx.isPro, building });
    const stage = state === 's916And45'
      ? `<div class="shr-both"><figure>${card(158, '916')}<figcaption>9:16 · Stories</figcaption></figure><figure>${card(196, '45')}<figcaption>4:5 · Feed posts</figcaption></figure></div>`
      : `<div class="shr-one">${card(ratio === '45' ? 250 : 212, ratio)}</div>`;
    const chips = SHR_STYLES.map(([id, label, ic]) => {
      const off = ((id === 'glyph' || id === 'map') && short) || (id === 'ring' && unscorable);
      return ui.chip(label, { kind: 'filter', icon: ic, selected: style === id, action: off ? null : id === 'map' ? 'askMap' : 'style', arg: id, disabled: off, cls: off ? 'e-dis' : '', label: `${label} style${style === id ? ', selected' : ''}${off ? ', unavailable for this drive' : ''}` });
    }).join('');
    const proLocked = !ctx.isPro;
    const proRow = ui.list([
      ui.row({ icon: 'layers', title: 'Car colour background', sub: `Full ${v ? v.colourName : ''} wash`, trailing: proLocked ? 'lock' : 'toggle', on: wash, action: proLocked ? null : 'wash', ...(proLocked ? { nav: 'S-SUB-04', params: { from: 'share' } } : {}) }),
      ui.row({ icon: 'sparkle', title: 'Minimal mark', sub: 'Just the Loop, smaller. Never no mark.', trailing: proLocked ? 'lock' : 'toggle', on: minimal, action: proLocked ? null : 'minimal', ...(proLocked ? { nav: 'S-SUB-04', params: { from: 'share' } } : {}) }),
    ], { cls: 'shr-pro' });
    const note = short && state === 'shortTrip'
      ? ui.banner({ kind: 'info', icon: 'shield', title: 'This drive shares as a Ring', text: 'It’s too short to share its shape: trimming the ends would leave almost nothing. The Ring card shares no route at all.' })
      : style === 'map'
        ? ui.banner({ kind: 'attention', icon: 'pin', title: 'This card shows where you drove', text: 'Place labels are off and both ends are trimmed. People who know the area may still recognise the roads.', action: { text: 'Change', action: 'style', arg: 'glyph' } })
        : style === 'glyph'
          ? ui.banner({ kind: 'info', icon: 'shield', title: 'Ends trimmed, no map, no location data', text: 'Distinctive route shapes can still be recognised. The Ring card shares no route at all.', action: { text: 'Zones', nav: 'S-PRV-02/withZones' } })
          : '';
    const alt = altText(style, t, v);
    const controls = [
      ui.segmented([{ id: '916', label: '9:16 Story' }, { id: '45', label: '4:5 Post' }], { selected: state === 's916And45' ? '916' : ratio, action: 'ratio', cls: 'shr-seg' }),
      `<div class="shr-chips" role="group" aria-label="Card style">${chips}</div>`,
      note || '',
      ui.section(ctx.isPro ? 'Pro styles' : 'Pro styles', proRow, { style: 'caption', meta: proLocked ? 'Part of Pro' : null }),
      ui.section('Alt text', `<div class="ui-pad"><div class="shr-alt ui-press" ${ui.act({ action: 'copyAlt', label: `Alt text: ${alt}. Copy` })}><p>${ui.esc(alt)}</p><span>${ui.icon('layers', 16)}Copy</span></div></div>`, { style: 'caption', footer: 'Offered when you share, for screen readers on the other side.' }),
      `<div class="ui-pad"><div class="shr-ride ui-press" ${ui.act({ nav: 'S-SHR-03', label: 'Give a friend 30 days of Pro with a Ride-along pass' })}>${ui.icon('heart', 18)}<span><b>Give a friend 30 days of Pro</b><small>Send a Ride-along pass with your card</small></span>${ui.icon('chevronRight', 16)}</div></div>`,
    ];
    const share = dock(ui.button(building ? 'Building…' : ctx.ios ? 'Share…' : 'Share', { kind: 'filled', size: 'l', block: true, icon: 'share', disabled: building, action: 'share', label: 'Share card' }));
    const sheet = ctx.store.sheet ? osShare(ctx, { title: 'DrivePerf card', sub: 'PNG · no location data · alt text included', thumb: `<span class="e-os-mini">${shareCard(ctx, { style, ratio: '916', w: 34, trip: t, minimal: true })}</span>` }) : '';
    return ui.page(`<div class="shr" data-style="${style}"><div class="shr-stage" data-rise>${stage}</div><div class="shr-ctl">${controls.filter(Boolean).map((c) => `<div data-rise>${c}</div>`).join('')}</div><div style="height:84px"></div></div>`,
      { nav: { title: 'Share', subtitle: style === 'week' ? 'Your week' : style === 'archetype' ? 'October, wrapped' : `${t.partOfDay} · ${t.label}`, close: true, medium: false }, fixed: share, overlay: sheet });
  }

  function shrMount(el, ctx) {
    const c = el.querySelector('.shr-stage .shc');
    if (!c || still(ctx, el)) return;
    const M = ctx.motion;
    M.animate(c, [{ opacity: 0, transform: 'scale(.94)' }, { opacity: 1, transform: 'scale(1)' }], { preset: 'default' });
    if (ctx.state === 'Load') return;
    el.querySelectorAll('.shr-stage .ui-glyph path, .shr-stage .shc-draw').forEach((p) => M.drawOn(p, { duration: 600, delay: 90 }));
    ctx.ui.anim.rings(c, { haptics: false });
    c.querySelectorAll('.shc-k, .shc-rec, .shc-car, .shc-sub').forEach((x, i) => M.animate(x, [{ opacity: 0, transform: 'translateY(8px)' }, { opacity: 1, transform: 'translateY(0)' }], { preset: 'default', delay: 180 + i * 40 }));
    const mk = c.querySelector('.shc-mark');
    if (mk) M.animate(mk, [{ opacity: 0 }, { opacity: 1 }], { preset: 'effects', delay: 360 });
    const tm = setTimeout(() => ctx.haptic('success'), 900);
    ctx.ui.anim.rise(el, '.shr-ctl > [data-rise]', { stagger: 30, delay: 120 });
    return () => clearTimeout(tm);
  }

  screen('S-SHR-01', {
    name: 'Share card builder',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'full', tab: 'profile',
    states: [
      { id: 'default', label: 'Route glyph' }, { id: 'ring', label: 'Ring' }, { id: 'weekStrip', label: 'Week strip' }, { id: 'archetype', label: 'Archetype' },
      { id: 'map', label: 'Map (opt-in, after S-SHR-02)' }, { id: 's916And45', label: '9:16 and 4:5' }, { id: 'shortTrip', label: 'short trip (Ring only)' },
      { id: 'proStyles', label: 'Pro styles' }, { id: 'Load', label: 'Load (building)' },
    ],
    stateParams: { default: { trip: 't20261105-0742' }, ring: { trip: 't20261105-0742' }, map: { trip: 't20261105-0742' }, shortTrip: { trip: 't20261104-0755' }, proStyles: { trip: 't20261105-0742' } },
    render: shrRender, mount: shrMount,
    actions: {
      style: (ctx, id) => { ctx.haptic('selection'); ctx.setState(SHR_STATE[id] || 'default'); return false; },
      askMap: (ctx) => { ctx.nav('S-SHR-02', { trip: ctx.params.trip || 't20261105-0742' }); return false; },
      ratio: (ctx, r) => { ctx.store.ratio = r; if (ctx.state === 's916And45') { ctx.setState('default'); return false; } ctx.update(); return false; },
      wash: (ctx) => { ctx.store.wash = !(ctx.state === 'proStyles' ? ctx.store.wash !== false : ctx.store.wash); ctx.update(); return false; },
      minimal: (ctx) => { ctx.store.minimal = !(ctx.state === 'proStyles' ? ctx.store.minimal !== false : ctx.store.minimal); ctx.update(); return false; },
      copyAlt: (ctx) => { ctx.toast('Alt text copied', { icon: 'check' }); return false; },
      share: (ctx) => { ctx.store.sheet = true; ctx.update(); return false; },
      closeShare: (ctx) => { ctx.store.sheet = false; ctx.update(); ctx.toast('Card shared. Nothing was uploaded by DrivePerf.', { icon: 'check' }); return false; },
    },
    spec: {
      purpose: 'Make a beautiful, privacy-safe image of a drive, a week or a month to post anywhere, without exposing where the user lives (F-SHR-01…04, F-PRV-07). The card is generated on the device and shows only the route shape (ends trimmed 400–800 m, privacy zones removed), the Smoothness ring and the car. Never speed, G, duration, times, dates finer than part of day, start/end points or place names.',
      layout: [
        'Full-screen builder. Top bar: close (X) + “Share” + subtitle (part of day · route label, or “Your week” / “October, wrapped”). iOS inline glass bar; Android full-screen dialog bar (close leading).',
        'Stage: the card preview scaled to fit, centred on a soft Glide halo (9:16 → 212 × 377 pt; 4:5 → 250 × 313 pt). 9:16 and 4:5 state shows both side by side with captions “9:16 · Stories” / “4:5 · Feed posts”.',
        'Card art (export 1080 × 1920 / 1080 × 1350, always the dark night theme): night gradient + car-colour wash 12% (Pro “Car colour background”: 30%); kicker “THURSDAY MORNING · GTI”; style body; bottom-centre mark: Loop + “DrivePerf” wordmark (Pro minimal: Loop only, smaller; never none). All card type is fixed to the card size (it is an image, it does not follow Dynamic Type).',
        'Style bodies: Route shape = trimmed glyph (74% width) + ring 32% + gold rosette “New route record” · Ring = ring 62% + rosette + generic car silhouette (hatch/crossover, no logos) · Week = average Smoothness (DP Numerals), “▲ +2 vs last week”, 7-day dot strip M–S, “5 drives · 171 km” · Archetype = line-study art, “The Metronome”, its line, two stats (drives, avg Smoothness) · Map = label-free stylised basemap (56% height) with the trimmed route, “© OpenStreetMap contributors”, ring 36%.',
        'Controls under the stage: format segmented control (iOS glass segmented / Android connected button group) “9:16 Story · 4:5 Post”; style chips (filter chips, wrap): Route shape · Ring · Week · Archetype · Map; context banner (privacy note / map warning / short-trip note); “Pro styles” list (Car colour background, Minimal mark: toggles for Pro, PRO + lock for Free); Alt text card with Copy; Ride-along row (→ S-SHR-03).',
        'Bottom dock (fixed, 16 margins, 12 above the home indicator): large filled “Share…” (iOS ShareLink) / “Share” (Android share intent) button, full width.',
        'Load: the card shows the Loop loader + “Building your card…” + two skeleton lines; Share is disabled (“Building…”).',
      ],
      components: ['page', 'navBar', 'segmented', 'chip', 'banner', 'list', 'row', 'toggle', 'section', 'ring', 'glyph', 'button', 'loader', 'empty'],
      interactions: [
        'Style chip → that style (cross-fade + ±6° tilt, haptic selection). Map chip → S-SHR-02 warning sheet **every time**; “Use map card” there → map state. Banner “Change” on the map state → Route shape.',
        'Format control → re-lays out the card inside a morphing container (spring.default). On the “9:16 and 4:5” state, choosing a format returns to a single preview.',
        'Pro styles toggles (Pro/Preview/Ride-along) update the card live. Free: the rows show PRO + lock and open S-SUB-04 (from = share). Not a paywall moment after a worse-than-usual Story: the builder never opens a paywall by itself.',
        'Alt text card → copies the generated alt text (toast “Alt text copied”).',
        'Share → system share sheet (iOS ShareLink with the PNG + alt text; Android ACTION_SEND image/png with EXTRA_TEXT alt text). The PNG has no EXIF/XMP/GPS metadata (F-SHR-02.3). Destination is not collected. Prototype: tap the mocked sheet to close (toast).',
        'Ride-along row → S-SHR-03. Close (X) → back to the source (Story f7, trip detail, recap W5, Wrapped last frame).',
        'Gate: S-SHR-* is “content” for the D1 gate. During a driver trip the app shows S-DRV-01; while still moving after a trip the request waits on S-DRV-05 (F-SHR-01.4).',
      ],
      motion: [
        'Card build (motion.md §3.7, total ≈ 900 ms “shareBuild”): 0 ms surface scales 0.94 → 1 + fade (spring.default) · 90 ms glyph/route draws on (600 ms ease.drawOn) or the ring sweeps · 180 ms kicker, record and car rise 8 px (40 ms stagger) · 270 ms score counts up · 360 ms the mark fades in (spring.effects) · haptic success when ready.',
        'Controls rise 8 px, 30 ms stagger. Style switch: cross-dissolve + ±6° rotateY; format switch: container aspect morph (spring.default).',
        'RM: cross-fades only; the card renders complete. Load: the Loop loader spins (static in RM).',
      ],
      a11y: [
        'Card preview is one image with the generated alt text, e.g. “Route shape of a thursday morning drive in my GTI. Smoothness 91. New route record.” (the same text is offered for copy).',
        'Style chips: “Route shape style, selected” / “Map style, unavailable for this drive”. Format control is a tablist: “9:16 Story, tab, 1 of 2, selected”.',
        'Focus order: close → title → card → format → styles → note → Pro styles → alt text → Ride-along → Share.',
        'XL: controls reflow; the card keeps its size (it is an image); chips wrap; the dock button grows in height.',
        'No meaning by colour: the record uses the rosette + text, deltas on the week card use ▲/▼/● + signed numbers.',
      ],
      platformDiffs: {
        ios: 'Full-screen cover; inline glass top bar; glass segmented control; filter chips as glass-tinted capsules; ShareLink presents the system share sheet with the image preview and alt text; the dock sits on a glass gradient.',
        android: 'Full-screen dialog (close leading, title in the bar); connected button group for the format; M3 FilterChips with secondaryContainer selection; Sharesheet (ACTION_SEND, image/png, ClipData preview); the dock is a surface bar with the filled button (radius full → morph on press).',
      },
      analytics: ['share_opened {source: story|trip|recap|wrapped}', 'share_style_selected {style: glyph|ring|week|archetype|map}', 'share_map_confirmed', 'share_completed {style, format} (destination not collected)'],
      copy: {
        Title: 'Share',
        'Privacy note': 'Ends trimmed, no map, no location data. Distinctive route shapes can still be recognised. The Ring card shares no route at all.',
        'Map warning': 'This card shows where you drove. Place labels are off and both ends are trimmed. People who know the area may still recognise the roads.',
        'Short trip': 'This drive shares as a Ring. It’s too short to share its shape: trimming the ends would leave almost nothing. The Ring card shares no route at all.',
        'Pro styles': 'Car colour background · Minimal mark (Just the Loop, smaller. Never no mark.)',
        'Alt text': 'Route shape of a {part of day} drive in my {car}. Smoothness {n}. [New route record.]',
        Load: 'Building your card…',
        Passenger: 'Passenger trips aren’t shared. This drive is marked as passenger, so it has no score and no card.',
        'Ride-along': 'Give a friend 30 days of Pro · Send a Ride-along pass with your card',
      },
      freePro: 'All privacy-safe styles (Route shape, Ring, Week, Archetype, Map) are Free. Pro (and Preview / Ride-along) adds the extra card styles (car-colour background) and the minimal mark. Free users see the Pro rows with PRO + lock → S-SUB-04.',
      data: 'Fixtures: default/ring/map/proStyles t20261105-0742 (GTI, Thursday morning, 91, new route record) · shortTrip t20261104-0755 · week = latest complete recap w2026-10-26 (5 drives, 171 km, 87, +2) · archetype = wrapped.monthly (The Metronome, 19 drives, average 84). Card fields are limited to fixtures.md §8 (glyph, score, record flag, car nickname/colour/silhouette, part of day). Map card trims are deterministic per trip for the prototype (400–800 m, the app re-rolls per share) and the Home privacy zone (400 m) is removed. No fixture trip is under 2 km, so the shortTrip state forces the short-trip rule on the shortest trip (3.6 km) for illustration.',
      icons: ['routes', 'record', 'insights', 'sparkle', 'pin', 'layers', 'share', 'shield', 'heart', 'rosette', 'lock', 'close', 'passenger'],
      assets: ['logo-a-loop.svg', 'wordmark.svg'],
    },
  });

  /* ==========================================================================================================
     S-SHR-02 · Map card warning sheet (small) — every time, no "don't ask again" (F-SHR-03.1)
     ========================================================================================================== */
  screen('S-SHR-02', {
    name: 'Map card warning sheet',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'profile', detent: 'auto',
    over: (ctx) => ({ id: 'S-SHR-01', state: 'default', params: { trip: ctx.params.trip || 't20261105-0742' } }),
    states: [{ id: 'default', label: 'Def' }],
    render(ctx) {
      const { ui } = ctx;
      const z = D.user.privacyZones;
      return `<div class="mw">${ui.sheetHeader('This shows where you drove', { icon: `<span class="mw-ic">${ui.icon('pin', 20)}</span>` })}
        <p class="e-lead">A map card puts your route on real roads. People who know the area may recognise it, even with the ends hidden.</p>
        ${checks(ctx, [
          { kind: 'ok', text: 'Place labels are off', sub: 'No street, town or place names on the image' },
          { kind: 'ok', text: 'Both ends trimmed by 400–800 m', sub: 'A new random amount every time you share' },
          { kind: 'ok', text: `${z.length} privacy zone${z.length === 1 ? '' : 's'} removed`, sub: z.map((x) => `${x.name} · ${x.radiusM} m`).join(' · ') },
          { kind: 'no', text: 'Never speed, times or your start and end' },
        ])}
        <div class="mw-acts">${ui.button('Use map card', { kind: 'filled', size: 'l', block: true, action: 'useMap', haptic: 'confirm' })}${ui.button('Keep route shape', { kind: 'tonal', size: 'l', block: true, back: true })}</div>
        <p class="e-foot">We’ll ask every time you choose a map card. <span class="e-link ui-press" ${ui.act({ nav: 'S-PRV-02/withZones', label: 'Manage privacy zones' })}>Privacy zones</span></p></div>`;
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '.e-checks li', { stagger: 40, delay: 120 }); },
    actions: {
      useMap: (ctx) => {
        const r = DP.router;
        Promise.resolve(ctx.back()).then(() => { const top = r.stack[r.stack.length - 1]; if (top && top.id === 'S-SHR-01') r.setState('map'); });
        return false;
      },
    },
    spec: {
      purpose: 'An explicit, per-share confirmation before a map card is made (F-SHR-03.1): it explains that a map shows real roads, what is still protected, and has no “don’t ask again”.',
      layout: [
        'Sheet fitted to content (iOS glass sheet at a partial detent, radius 40, 8 inset · Android ModalBottomSheet, top radius 28, drag handle).',
        'Header: pin glyph in a warning-tinted 36 circle + title “This shows where you drove” (sheetTitle) + close (iOS only).',
        'Lead (body-secondary, text-2), then a 4-item checklist: ✓ labels off · ✓ ends trimmed 400–800 m · ✓ privacy zones removed (names + radii) · ✕ never speed, times or start/end.',
        'Stacked large buttons, equal width: “Use map card” (filled) and “Keep route shape” (tonal). Footnote with a “Privacy zones” link.',
      ],
      components: ['sheetHeader', 'button', 'sheet'],
      interactions: ['Use map card → dismiss, S-SHR-01 switches to the Map style (haptic confirm, analytics share_map_confirmed).', 'Keep route shape / drag down / scrim / X → dismiss, the style stays as it was.', 'Privacy zones → S-PRV-02 (with zones).', 'Offline (EC-NET): S-SHR-01 disables the Map chip with “Map cards need a connection.”'],
      motion: ['Sheet rises with spring.sheet; checklist items rise 8 px, 40 ms stagger. RM: fade.'],
      a11y: ['Focus moves to the title on open; “Use map card” is the first button. The list reads as “Place labels are off. No street, town or place names on the image.” etc.', 'XL: the sheet opens at the large detent; buttons stay full width and grow in height.'],
      platformDiffs: { ios: 'Glass sheet, partial detent, X in the header, buttons glassProminent / glass.', android: 'ModalBottomSheet with drag handle (no X), filled + tonal buttons.' },
      analytics: ['share_map_confirmed', 'share_map_declined'],
      copy: { Title: 'This shows where you drove', Lead: 'A map card puts your route on real roads. People who know the area may recognise it, even with the ends hidden.', Confirm: 'Use map card', Decline: 'Keep route shape', Footnote: 'We’ll ask every time you choose a map card.' },
      freePro: 'Free (map cards are a Free style).',
      data: 'Privacy zones from DP_DATA.user.privacyZones (Home · 400 m).',
      icons: ['pin', 'check', 'close'],
      assets: [],
    },
  });

  /* ==========================================================================================================
     S-SHR-03 · Ride-along pass invite (sheet, large) — the referral (F-SHR-05)
     ========================================================================================================== */
  function passCard(ctx, o = {}) {
    const { ui } = ctx;
    return `<div class="ra-pass ${o.cls || ''}" data-theme="dark" role="img" aria-label="${ui.esc(o.aria || 'Ride-along pass: 30 days of DrivePerf Pro')}">
      <div class="ra-pass-t"><span class="ra-pass-k">Ride-along pass</span>${ctx.icons.loop({ size: 26, color: 'var(--dp-accent)', dot: 'var(--dp-text)' })}</div>
      <b class="ra-pass-n"><span class="ui-num">30</span> days of Pro</b>
      <span class="ra-pass-s">${ui.esc(o.from ? `From ${o.from}` : 'From Marcus')} · no card needed</span>
      <svg class="ra-pass-line" viewBox="0 0 300 40" preserveAspectRatio="none" aria-hidden="true"><path d="M0 30C60 30 70 10 130 12s90 22 170 8" fill="none" stroke="var(--dp-accent)" stroke-width="2.4" stroke-linecap="round"/><circle cx="296" cy="21" r="4" fill="var(--dp-text)"/></svg>
      <i class="ra-pass-notch is-l"></i><i class="ra-pass-notch is-r"></i></div>`;
  }
  const RA_REWARDS = { default: 0, rewardsEarned: 2, capReached: 6 };
  screen('S-SHR-03', {
    name: 'Ride-along pass invite',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'profile', detent: 'large',
    over: 'S-ACC-01',
    states: [{ id: 'default', label: 'Def' }, { id: 'rewardsEarned', label: 'rewards earned' }, { id: 'capReached', label: 'cap reached' }],
    render(ctx) {
      const { ui, state } = ctx;
      const n = RA_REWARDS[state] || 0;
      const meter = `<div class="ra-meter" role="img" aria-label="${n} of 6 reward months earned this year">${Array.from({ length: 6 }, (_, i) => `<i class="${i < n ? 'on' : ''}"></i>`).join('')}</div>`;
      const rewards = n ? ui.section('Your rewards this year', `${ui.card(`<div class="ra-earn"><div><b class="ui-num">${n}</b><span>of 6 months earned</span></div>${meter}</div>`)}
        ${ui.list(Array.from({ length: Math.min(n, 3) }, (_, i) => ui.row({ icon: 'check', title: 'A friend is set up', sub: ['1 month of Pro added · 30 Oct', '1 month of Pro added · 19 Oct', '1 month of Pro added · 14 Oct'][i], trailing: '' })).concat(n > 3 ? [ui.row({ icon: 'more', iconTone: 'neutral', title: `${n - 3} more`, sub: 'Earlier this year', trailing: '' })] : []), { cls: 'ra-list' })}`, { style: 'caption' }) : '';
      const cap = state === 'capReached' ? ui.banner({ kind: 'info', icon: 'info', title: 'You’ve reached this year’s 6 months', text: 'You can keep sending passes and friends still get 30 days. Your own rewards start again on 1 January.' }) : '';
      return `<div class="ra">${ui.sheetHeader('Give a Ride-along pass', { sub: 'A month of Pro for you, 30 days for a friend' })}
        <div class="ra-hero" data-rise>${passCard(ctx)}</div>
        ${cap ? `<div class="ra-b" data-rise>${cap}</div>` : ''}
        <div class="ra-b" data-rise>${steps(ctx, [
          { title: 'Send your link', text: 'Your friend gets 30 days of Pro instead of the 14-day Preview.' },
          { title: 'They start recording', text: 'Automatic trips on, and three Stories watched.' },
          { title: 'You get a month of Pro', text: 'Up to 6 months a year. No account needed to send.' },
        ])}</div>
        ${rewards ? `<div class="ra-b" data-rise>${rewards}</div>` : ''}
        <div class="ra-b" data-rise>${checks(ctx, [
          { kind: 'ok', text: 'We only tell you “Your friend is set up”', sub: 'Nothing about their drives, ever' },
          { kind: 'no', text: 'Rewards never depend on distance or number of drives' },
        ])}</div>
        <div class="ra-acts">${ui.button('Share pass link', { kind: 'filled', size: 'l', block: true, icon: 'share', action: 'sharePass' })}<span class="ra-link">driveperf.com/ride/${ui.esc(D.user.id.split('_').pop())}</span></div></div>`;
    },
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '[data-rise]', { stagger: 40 });
      const p = el.querySelector('.ra-pass-line path');
      if (p && !still(ctx, el)) ctx.motion.drawOn(p, { duration: 900, delay: 200 });
    },
    actions: { sharePass: (ctx) => { ctx.toast(ctx.ios ? 'Opens the share sheet with your pass link' : 'Opens the Sharesheet with your pass link', { icon: 'share' }); return false; } },
    spec: {
      purpose: 'The referral (F-SHR-05): give a friend a 30-day Ride-along pass and earn a month of Pro when they are up and running, capped at 6 months a year. No friends list, no feed, nothing about the friend’s drives.',
      layout: [
        'Large-detent sheet. Header: “Give a Ride-along pass” + sub “A month of Pro for you, 30 days for a friend”.',
        'Hero: the pass card (always dark, radius 24, ticket notches left/right, night gradient + Glide line study): kicker “RIDE-ALONG PASS”, Loop mark, “30 days of Pro” (DP Numerals), “From Marcus · no card needed”.',
        'cap reached: an info banner under the pass.',
        'How it works: 3 numbered steps (Glide numerals in 28 circles).',
        'rewards earned / cap reached: “Your rewards this year” card: big count + “of 6 months earned” + a 6-segment meter (not a streak: it counts rewards, not days), then a list of rewards (“A friend is set up · 1 month of Pro added · date”).',
        'Promises checklist: ✓ we only tell you “Your friend is set up” · ✕ rewards never depend on distance or number of drives.',
        'Bottom: large filled “Share pass link” + the link in caption mono.',
      ],
      components: ['sheetHeader', 'section', 'card', 'list', 'row', 'banner', 'button', 'sheet'],
      interactions: ['Share pass link → system share sheet with the link (no account needed; the sender is identified by the purchase/app user ID).', 'Drag down / scrim / X → back (S-ACC-01 or S-SHR-01).', 'Links open S-SHR-04 in the app, or S-WEB-08 when the app is not installed (deferred link).'],
      motion: ['Blocks rise 8 px, 40 ms stagger; the pass card’s line draws on (900 ms ease.drawOn). RM: static.'],
      a11y: ['Pass card is one image: “Ride-along pass: 30 days of DrivePerf Pro”.', 'Meter: “2 of 6 reward months earned this year”.', 'XL: steps and rows wrap; the link wraps rather than truncating.'],
      platformDiffs: { ios: 'Sheet at the large detent with its own NavigationStack; share via ShareLink.', android: 'ModalBottomSheet expanded; Sharesheet intent.' },
      analytics: ['ridealong_created', 'ridealong_link_shared'],
      copy: {
        Title: 'Give a Ride-along pass', Sub: 'A month of Pro for you, 30 days for a friend',
        Steps: 'Send your link · They start recording (automatic trips on, and three Stories watched) · You get a month of Pro (up to 6 months a year)',
        Privacy: 'We only tell you “Your friend is set up”. Nothing about their drives, ever.',
        Cap: 'You’ve reached this year’s 6 months. You can keep sending passes and friends still get 30 days. Your own rewards start again on 1 January.',
        CTA: 'Share pass link',
      },
      freePro: 'Free to send for every plan. The reward is a RevenueCat promotional entitlement.',
      data: 'No reward fixtures exist: rewards earned (2) and cap reached (6) use prototype values with plausible October dates. The link slug is derived from DP_DATA.user.id (u_local_7f3a → 7f3a).',
      icons: ['share', 'check', 'close', 'info', 'more'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ==========================================================================================================
     S-SHR-04 · Ride-along welcome (redeemed, first open via link) — full
     ========================================================================================================== */
  screen('S-SHR-04', {
    name: 'Ride-along welcome (redeemed)',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'full', tab: 'profile',
    states: [{ id: 'default', label: 'Valid pass (30 days)' }, { id: 'invalidExpired', label: 'invalid/expired' }, { id: 'alreadyUsed', label: 'already used' }],
    render(ctx) {
      const { ui, state } = ctx;
      const from = D.entitlement.states.rideAlong.from;
      const ok = state === 'default';
      const feats = D.entitlement.proFeatures;
      const head = ok
        ? `<h1 class="e-h1">${ui.esc(from)} gave you a Ride-along pass.</h1><p class="e-lead">30 days of DrivePerf Pro, free. No card, nothing to cancel.</p>`
        : state === 'invalidExpired'
          ? `<h1 class="e-h1">This pass has expired.</h1><p class="e-lead">This Ride-along link isn’t valid any more. You still get two weeks of Pro to try everything, no card needed.</p>`
          : `<h1 class="e-h1">This pass has already been used.</h1><p class="e-lead">Each Ride-along pass works once, on one phone. Ask ${ui.esc(from)} for a new one, or start with two weeks of Pro.</p>`;
      const body = ok
        ? `${ui.section('Included for 30 days', `<div class="ui-pad">${checks(ctx, [feats[0], feats[1], feats[2], feats[4]].map((f) => ({ kind: 'ok', text: f })))}</div>`, { style: 'caption' })}
           <p class="e-foot ui-pad">After 30 days you can keep Pro or carry on with Free. Your drives stay yours either way.</p>`
        : `<div class="ui-pad">${ui.card(`<div class="ra-alt">${ui.icon('info', 20)}<span><b>Pro Preview · 14 days</b><small>Every Pro feature, free for two weeks. Recording, Stories and scores stay free after that.</small></span></div>`)}</div>`;
      const acts = ok
        ? ui.button('Start my 30 days', { kind: 'filled', size: 'l', block: true, nav: 'S-ONB-01', params: { ent: 'rideAlong' }, haptic: 'confirm' }) + ui.button('I already use DrivePerf', { kind: 'text', size: 'm', block: true, nav: 'S-HOME-01', params: { ent: 'rideAlong' } })
        : ui.button('Start with the Pro Preview', { kind: 'filled', size: 'l', block: true, nav: 'S-ONB-01', params: { ent: 'preview' } }) + ui.button('Not now', { kind: 'text', size: 'm', block: true, nav: 'S-HOME-01' });
      return ui.page(`<div class="ra-w is-${state}"><div class="ra-w-art" data-rise>${ok ? passCard(ctx, { from, aria: `Ride-along pass from ${from}: 30 days of DrivePerf Pro` }) : passCard(ctx, { from, cls: 'is-void', aria: 'Ride-along pass, not valid' })}${ok ? '' : `<span class="ra-void">${ui.icon(state === 'invalidExpired' ? 'clock' : 'check', 18)}${state === 'invalidExpired' ? 'Expired' : 'Used'}</span>`}</div>
        <div class="ui-pad ra-w-t" data-rise>${head}</div><div data-rise>${body}</div><div style="height:150px"></div></div>`,
        { fixed: `<div class="e-fx-close">${ui.iconButton('close', { nav: 'S-HOME-01', label: 'Close', glass: true })}</div>${dock(acts, { cls: 'is-tall' })}` });
    },
    mount(el, ctx) {
      const p = el.querySelector('.ra-pass');
      if (p && !still(ctx, el)) ctx.motion.animate(p, [{ opacity: 0, transform: 'perspective(600px) rotateX(18deg) translateY(24px) scale(.94)' }, { opacity: 1, transform: 'perspective(600px) rotateX(0) translateY(0) scale(1)' }], { preset: ctx.state === 'default' ? 'bouncy' : 'default' });
      const l = el.querySelector('.ra-pass-line path');
      if (l && ctx.state === 'default' && !still(ctx, el)) ctx.motion.drawOn(l, { duration: 900, delay: 250 });
      ctx.ui.anim.rise(el, '.ra-w > [data-rise]:not(.ra-w-art)', { stagger: 40, delay: 160 });
      if (ctx.state === 'default') { const tm = setTimeout(() => ctx.haptic('success'), 700); return () => clearTimeout(tm); }
    },
    spec: {
      purpose: 'First open from a Ride-along link (F-SHR-05.2): confirm 30 days of Pro instead of the 14-day Preview, or explain kindly why the pass can’t be used and fall back to the Preview.',
      layout: [
        'Full-screen cover, theme-following background. Floating close (glass 44 / tonal 48) top-trailing, visible immediately.',
        'Hero: the pass card (dark, 300 wide, radius 24, ticket notches) “30 days of Pro · From Priya · no card needed”; invalid/used: the card desaturated to 45% with a stamp chip “Expired” (clock) / “Used” (check).',
        'Title (sheetTitle-plus, 28/34 display) + lead (body, text-2).',
        'Valid: “Included for 30 days” checklist (4 Pro features) + footnote. Invalid/used: a card explaining the 14-day Pro Preview.',
        'Bottom dock: large filled primary + text secondary.',
      ],
      components: ['page', 'iconButton', 'section', 'card', 'button'],
      interactions: [
        'Valid: “Start my 30 days” → S-ONB-01 (new user; entitlement rideAlong, 30 days) · “I already use DrivePerf” → S-HOME-01 (existing install: pass applied if never used on this device).',
        'Invalid / used: “Start with the Pro Preview” → S-ONB-01 (14-day Preview) · “Not now” / close → S-HOME-01.',
        'Abuse check: the same device redeeming twice is rejected → “already used”. Redemption retries on EC-NET.',
      ],
      motion: ['Valid: the pass rises and un-tilts (rotateX 18° → 0, spring.bouncy, the rare celebration) and its line draws on; text rises 8 px, 40 ms stagger; haptic success. Invalid: spring.default, no haptic. RM: cross-fade, static pass.'],
      a11y: ['Pass card label: “Ride-along pass from Priya: 30 days of DrivePerf Pro” / “Ride-along pass, not valid”. Stamp text is part of the title, not colour-only.', 'Focus: close → title → lead → list → primary → secondary.', 'XL: the pass scales to the margin width; text reflows; the dock grows.'],
      platformDiffs: { ios: 'fullScreenCover; glass close button; Universal Link entry.', android: 'Full-screen dialog/activity; App Link entry; tonal close.' },
      analytics: ['ridealong_link_opened', 'ridealong_redeemed', 'ridealong_redeem_failed {reason: expired|used}'],
      copy: {
        Valid: '{Priya} gave you a Ride-along pass. 30 days of DrivePerf Pro, free. No card, nothing to cancel.',
        Footnote: 'After 30 days you can keep Pro or carry on with Free. Your drives stay yours either way.',
        Expired: 'This pass has expired. This Ride-along link isn’t valid any more. You still get two weeks of Pro to try everything, no card needed.',
        Used: 'This pass has already been used. Each Ride-along pass works once, on one phone. Ask {Priya} for a new one, or start with two weeks of Pro.',
        'CTA valid': 'Start my 30 days · I already use DrivePerf', 'CTA invalid': 'Start with the Pro Preview · Not now',
      },
      freePro: 'Ride-along = Pro for 30 days (entitlement.states.rideAlong), then Free unless the user subscribes. No price on this screen (first open is onboarding-adjacent, F-SUB-08.1).',
      data: 'Sender name from entitlement.states.rideAlong.from (Priya). Included list = proFeatures[0, 1, 2, 4].',
      icons: ['close', 'check', 'clock', 'info'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ==========================================================================================================
     ACCOUNT · local-first, optional sign-in only for cloud backup and a second phone (Gate 1 #7, F-ACC-01…05)
     ========================================================================================================== */
  const providerOf = (ctx) => (ctx.ios ? { name: 'Apple', sub: 'Hide My Email address' } : { name: 'Google', sub: 'Google account' });
  const APPLE = '<svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M16.4 12.6c0-2.4 2-3.6 2.1-3.7-1.2-1.7-3-1.9-3.6-2-1.5-.2-3 .9-3.8.9-.8 0-2-.9-3.3-.9-1.7 0-3.3 1-4.2 2.6-1.8 3.1-.5 7.7 1.3 10.2.9 1.2 1.9 2.6 3.2 2.6 1.3-.1 1.8-.8 3.3-.8 1.6 0 2 .8 3.3.8 1.4 0 2.3-1.3 3.1-2.5 1-1.4 1.4-2.8 1.4-2.9 0 0-2.8-1.1-2.8-4.3zM13.9 5.3c.7-.8 1.2-2 1-3.1-1 0-2.2.7-2.9 1.5-.6.7-1.2 1.9-1 3 1.1.1 2.2-.6 2.9-1.4z"/></svg>';
  const GOOGLE = '<svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"><path d="M21.6 12.2c0-.7-.1-1.4-.2-2H12v3.8h5.4a4.6 4.6 0 0 1-2 3v2.5h3.2c1.9-1.7 3-4.3 3-7.3z" fill="#4285F4"/><path d="M12 22c2.7 0 5-.9 6.6-2.4l-3.2-2.5c-.9.6-2 1-3.4 1-2.6 0-4.8-1.8-5.6-4.1H3.1v2.6A10 10 0 0 0 12 22z" fill="#34A853"/><path d="M6.4 14c-.2-.6-.3-1.3-.3-2s.1-1.4.3-2V7.4H3.1a10 10 0 0 0 0 9.2z" fill="#FBBC05"/><path d="M12 6c1.5 0 2.8.5 3.8 1.5l2.9-2.9A10 10 0 0 0 3.1 7.4L6.4 10C7.2 7.8 9.4 6 12 6z" fill="#EA4335"/></svg>';
  const MAIL = '<svg width="46" height="46" viewBox="0 0 48 48" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linejoin="round" stroke-linecap="round"><rect x="6" y="11" width="36" height="26" rx="6"/><path d="M8 14l16 12 16-12"/><circle cx="38" cy="12" r="6" fill="var(--dp-accent)" stroke="none"/></svg>';
  const MAIL_S = '<svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" stroke-linecap="round"><rect x="3" y="5.5" width="18" height="13" rx="3"/><path d="M4 7.5l8 6 8-6"/></svg>';
  const CLOUD = (ok) => `<svg width="46" height="46" viewBox="0 0 48 48" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linejoin="round" stroke-linecap="round"><path d="M14 36h20a8 8 0 0 0 1.5-15.9A11 11 0 0 0 14.3 18 9 9 0 0 0 14 36z"/>${ok === 'ok' ? '<path d="M18.5 27.5l4 4 7.5-8"/>' : ok === 'off' ? '<path d="M10 10l28 28"/>' : ok === 'up' ? '<path d="M24 32V22M19.5 26.5L24 22l4.5 4.5"/>' : ok === 'warn' ? '<path d="M24 22v6"/><circle cx="24" cy="32" r="1" fill="currentColor"/>' : ''}</svg>`;

  /** Large profile avatar: iOS circle on surface-3 · Android Cookie12Sided secondaryContainer. */
  function bigAvatar(ctx) {
    const { ui } = ctx;
    if (ctx.android) return `<span class="acc-av is-m3"><svg width="64" height="64" viewBox="0 0 64 64" aria-hidden="true"><path d="${ui.cookie(12, 32, 32, 32, 0.06)}" fill="var(--md-sys-color-secondary-container)"/></svg><b>${ui.esc(D.user.initials)}</b></span>`;
    return `<span class="acc-av"><b>${ui.esc(D.user.initials)}</b></span>`;
  }

  const ACC1_STATES = [{ id: 'default', label: 'Local mode' }, { id: 'signedInFree', label: 'signed in Free' }, { id: 'signedInProWithBackup', label: 'signed in Pro with backup' }, { id: 'Prev', label: 'Prev (Pro Preview day 9)' }];
  function acc1Body(ctx) {
    const { ui, data, state } = ctx;
    const signed = state === 'signedInFree' || state === 'signedInProWithBackup';
    const prov = providerOf(ctx);
    const es = ctx.entState;
    const h = data.health(ctx.platform, ctx.params.health || 'allGood');
    const red = h.items.some((i) => i.status === 'red');
    const id = `<div class="acc-id">${bigAvatar(ctx)}<div class="acc-id-t"><b>${ui.esc(D.user.displayName)}</b>${signed
      ? `<span>${ui.icon('check', 14)}Signed in with ${prov.name} · ${prov.sub}</span>`
      : `<span>${ui.icon('phone', 14)}Local mode · no account</span>`}</div></div>`;
    let plan;
    if (es.plan === 'free') plan = ui.card(`<div class="acc-plan"><div class="acc-plan-t"><span class="acc-k">Your plan</span><b>Free</b><p>Recording, Stories, scores and summaries, forever. Replay and Analysis for the last 30 days.</p></div>${ui.button('See what Pro adds', { kind: 'tonal', size: 's', nav: 'S-SUB-04', params: { from: 'profile' } })}</div>`, { nav: 'S-SUB-04', params: { from: 'profile' }, label: 'Your plan: Free. See what Pro adds' });
    else if (es.plan === 'preview' || es.plan === 'ridealong') plan = ui.card(`<div class="acc-plan"><div class="acc-plan-t"><span class="acc-k">Your plan</span>${ui.previewBadge(ctx.ent)}<b>${ui.esc(es.label)}</b><p>${es.plan === 'ridealong' ? `Every Pro feature, from ${ui.esc(es.from)}’s Ride-along pass. No card.` : 'Every Pro feature, free for 14 days. No card, nothing to cancel.'}</p></div>${progress(ctx, es.day / es.of, { label: `Day ${es.day} of ${es.of}` })}</div>`, { hero: true, tint: 'accent', nav: 'S-SUB-01', label: `${es.label}. Opens Preview status` });
    else plan = ui.card(`<div class="acc-plan"><div class="acc-plan-t"><span class="acc-k">Your plan</span><b>DrivePerf Pro ${es.founding ? '<span class="acc-found">Founding member</span>' : ''}</b><p>${es.plan === 'pro_monthly' ? 'Monthly' : 'Annual'} · renews ${ui.esc(dateLong(es.renews))}</p></div>${ui.icon('chevronRight', 18)}</div>`, { hero: true, tint: 'accent', nav: 'S-SUB-06', params: { }, label: `Your plan: DrivePerf Pro${es.founding ? ', founding member' : ''}. Renews ${dateLong(es.renews)}. Manage subscription` });
    const osBackup = ctx.ios ? 'Your iPhone backup (iCloud or your computer) also includes DrivePerf, encrypted by iOS.' : 'Android backup also includes DrivePerf, encrypted by Android, if it’s on for this phone.';
    let account;
    if (!signed) account = ui.card(`<div class="acc-local"><span class="acc-local-ic">${ui.icon('phone', 22)}</span><div><b>Local mode: your data is on this phone.</b><p>${ui.esc(osBackup)}</p></div></div>
        <div class="acc-local-a">${ui.button('Sign in for cloud backup', { kind: 'tonal', size: 'm', block: true, icon: 'cloud', nav: 'S-ACC-02' })}<p>Optional. Only needed for cloud backup or a second phone.</p></div>`);
    else if (state === 'signedInFree') account = ui.list([
      ui.row({ icon: 'cloud', title: 'Cloud backup', sub: 'Part of Pro. On Free, no trip data leaves this phone.', trailing: 'lock', nav: 'S-ACC-04/Lock', params: { ent: 'free' } }),
      ui.row({ icon: 'phone', title: 'This phone', sub: `${D.privacy.dataSize.trips} drives · ${D.privacy.dataSize.mb} MB`, nav: 'S-SET-07' }),
    ]);
    else account = ui.list([
      ui.row({ lead: `<span class="ui-row-ic is-pos">${ui.icon('cloud', 20)}</span>`, title: 'Cloud backup', sub: `Up to date · ${D.privacy.dataSize.trips} drives · ${D.privacy.dataSize.mb} MB`, value: 'On', nav: 'S-ACC-04/onAndUpToDate' }),
      ui.row({ icon: 'phone', title: 'Restore on another phone', sub: 'Sign in there with the same account', nav: 'S-ACC-06/done' }),
    ]);
    const dataRows = ui.list([
      ui.row({ icon: 'shield', title: 'Privacy Centre', sub: 'What’s stored where · download · delete', nav: signed && state !== 'signedInFree' ? 'S-PRV-01/withBackup' : 'S-PRV-01' }),
      ui.row({ lead: `<span class="ui-row-ic${red ? ' is-warn' : ''}">${ui.icon(red ? 'warn' : 'health', 20)}</span>`, title: 'Health Check', sub: h.summary, nav: 'S-SET-05', params: ctx.params.health ? { health: ctx.params.health } : null }),
      ui.row({ icon: 'gear', title: 'Settings', sub: 'Capture, notifications, units, appearance', nav: 'S-SET-01' }),
    ]);
    const proRows = ui.list([
      es.plan === 'free' ? ui.row({ icon: 'sparkle', title: 'Plans', sub: 'Monthly or annual', nav: 'S-SUB-04', params: { from: 'profile' } }) : ui.row({ icon: 'sparkle', title: 'Manage subscription', sub: es.plan === 'preview' ? 'Pro Preview · no card' : es.plan === 'ridealong' ? 'Ride-along pass' : `${storeName(ctx)} · ${es.price}`, nav: 'S-SUB-06', params: { ent: ctx.ent } }),
      ui.row({ icon: 'refresh', title: 'Restore purchases', sub: `Bought Pro on another phone? Restore it from ${storeName(ctx)}.`, nav: 'S-SUB-06/restoreResult' }),
      ui.row({ icon: 'heart', title: 'Give a Ride-along pass', sub: '30 days of Pro for a friend, a month for you', nav: 'S-SHR-03' }),
    ]);
    const out = [id, plan, ui.section('Account', account, { style: 'caption' }), ui.section('Your data', dataRows, { style: 'caption' }), ui.section('Pro and passes', proRows, { style: 'caption' })];
    if (signed) out.push(ui.section('', ui.list([ui.row({ icon: 'export', iconTone: 'neutral', title: 'Sign out', sub: 'Choose what stays on this phone', nav: 'S-ACC-07' }), ui.row({ icon: 'trash', iconTone: 'critical', title: 'Delete account', destructive: true, nav: 'S-PRV-06', trailing: '' })]), { style: 'caption' }));
    out.push(`<p class="acc-foot">${ctx.icons.loop({ size: 16, color: 'var(--dp-accent)', dot: 'var(--dp-text-2)' })}DrivePerf 1.0 · Never sold, never shared with insurers.</p>`);
    return out;
  }
  screen('S-ACC-01', {
    name: 'Profile',
    wp: 'E', platforms: ['ios', 'android'], presentation: { ios: 'sheet', android: 'push' }, tab: 'profile', detent: 'large',
    over: 'S-HOME-01', backTo: 'S-HOME-01',
    states: ACC1_STATES,
    stateParams: { signedInFree: { ent: 'free' }, signedInProWithBackup: { ent: 'pro' }, Prev: { ent: 'preview' } },
    render(ctx) {
      const { ui } = ctx;
      const body = acc1Body(ctx).map((b) => `<div class="acc-b" data-rise>${b}</div>`).join('');
      if (ctx.ios) return `<div class="acc">${ui.sheetHeader('Profile')}${body}</div>`;
      return ui.page(`<div class="acc">${body}</div>`, { nav: { title: 'Profile' } });
    },
    mount(el, ctx) {
      ctx.ui.anim.pop(el.querySelector('.acc-av'), 60);
      ctx.ui.anim.rise(el, '.acc-b[data-rise]', { stagger: 30, delay: 40 });
    },
    spec: {
      purpose: 'Account, plan and data controls in one calm place, opened from the avatar on every tab root (screen-map §1). Local mode is the default: every Free feature and the Pro Preview work without an account (F-ACC-01); sign-in is optional and only for cloud backup and a second phone.',
      layout: [
        'iOS: sheet at the large detent with its own NavigationStack; header “Profile” + glass close. Android: full-screen destination (MediumFlexibleTopAppBar “Profile”, back arrow, predictive back).',
        'Identity row: 64 avatar (iOS circle on surface-3 · Android Cookie12Sided secondaryContainer) + name (cardTitle 20) + account line with glyph: “Local mode · no account” or “Signed in with Apple · Hide My Email address” (Android: Google).',
        'Plan card (hero, Glide tint 18%): kicker “YOUR PLAN”; Pro: “DrivePerf Pro” + gold “Founding member” capsule (Sodium is allowed for the founding badge) + “Annual · renews 13 October 2027” + chevron → S-SUB-06 · Free: “Free” + what’s free + tonal “See what Pro adds” → S-SUB-04 · Preview: Preview badge + “Pro Preview · day 9 of 14” + day progress bar → S-SUB-01.',
        'Account section: local = card “Local mode: your data is on this phone.” + the OS backup note (iCloud/computer backup on iOS, Android backup) + tonal “Sign in for cloud backup” + caption “Optional. Only needed for cloud backup or a second phone.” · signed in Free = Cloud backup row (PRO + lock, “On Free, no trip data leaves this phone.”) + This phone (30 drives · 186 MB) · signed in Pro = Cloud backup (On, up to date) + Restore on another phone.',
        'Your data: Privacy Centre · Health Check (summary; warning glyph when red) · Settings. Pro and passes: Manage subscription / Plans · Restore purchases · Give a Ride-along pass.',
        'Signed in only: Sign out · Delete account (destructive). Footer: Loop + “DrivePerf 1.0 · Never sold, never shared with insurers.”',
      ],
      components: ['sheetHeader', 'navBar', 'card', 'previewBadge', 'button', 'list', 'row', 'section'],
      interactions: [
        'Plan card → S-SUB-06 (Pro) · S-SUB-04 (Free) · S-SUB-01 (Preview / Ride-along).',
        'Sign in for cloud backup → S-ACC-02 · Cloud backup row → S-ACC-04 (Lock on Free) · Restore on another phone → S-ACC-06.',
        'Privacy Centre → S-PRV-01 · Health Check → S-SET-05 · Settings → S-SET-01 · Manage subscription → S-SUB-06 · Restore purchases → S-SUB-06 restore result · Give a Ride-along pass → S-SHR-03.',
        'Sign out → S-ACC-07 · Delete account → S-PRV-06. iOS: pushes happen inside the sheet’s NavigationStack (the prototype pushes full-screen). Drag down / close → back to the tab.',
        'Allowed for passengers; during a driver trip the avatar is unreachable (S-DRV-01 only).',
      ],
      motion: ['Avatar pops in (scale .86 → 1, spring.default); blocks rise 8 px, 30 ms stagger. Sheet: spring.sheet (iOS large detent recedes the tab behind). Android: shared axis X. RM: cross-fade.'],
      a11y: [
        'Sheet title “Profile” gets focus on open. Plan card is one button: “Your plan: DrivePerf Pro, founding member. Renews 13 October 2027. Manage subscription”.',
        'Health Check row reads its summary (“Automatic trips are ready.”); the warning state adds a triangle glyph and “needs attention” text.',
        'XL: rows wrap to two lines, the plan card button moves under the text, the avatar shrinks to 56.',
      ],
      platformDiffs: {
        ios: 'Avatar toolbar item → .sheet(.large) with NavigationStack; inset-grouped lists on the sheet’s elevated background; glass close.',
        android: 'Avatar → full-screen destination (no tab bar), MediumFlexibleTopAppBar, M3E segmented ListItems on surfaceContainer, Cookie12Sided avatar.',
      },
      analytics: ['profile_opened {signed_in, plan_state}', 'sign_in_started {source: profile}', 'ridealong_invite_opened {source: profile}'],
      copy: {
        'Local mode': 'Local mode: your data is on this phone.',
        'OS backup (iOS)': 'Your iPhone backup (iCloud or your computer) also includes DrivePerf, encrypted by iOS.',
        'OS backup (Android)': 'Android backup also includes DrivePerf, encrypted by Android, if it’s on for this phone.',
        'Sign-in note': 'Optional. Only needed for cloud backup or a second phone.',
        'Free backup row': 'Cloud backup · Part of Pro. On Free, no trip data leaves this phone.',
        Footer: 'DrivePerf 1.0 · Never sold, never shared with insurers.',
      },
      freePro: 'Free: plan card “Free” + See what Pro adds; backup row locked. Pro: founding/annual or monthly details. Preview / Ride-along: badge + day progress (a countdown of the Preview, not a streak).',
      data: 'User Marcus (DP_DATA.user, local mode). Default = entitlement.current pro (founding annual, renews 2027-10-13) bought without an account (F-ACC-01.3). Storage from privacy.dataSize (30 drives · 186 MB). Health summary from health.presets (allGood unless the health param is set). Signed-in provider/email are prototype values (Apple Hide My Email on iOS, Google on Android): DP_DATA.user.account has none.',
      icons: ['phone', 'cloud', 'shield', 'health', 'warn', 'gear', 'sparkle', 'refresh', 'heart', 'export', 'trash', 'check', 'chevronRight'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ---------------- S-ACC-02 · Sign-in sheet ---------------- */
  function signInButtons(ctx, o = {}) {
    const { ui } = ctx;
    const dis = o.disabled;
    const apple = `<span class="acc-sso is-apple ui-press${dis ? ' e-dis' : ''}" ${ui.act({ action: o.loading ? null : 'sso', arg: 'apple', label: ctx.ios ? 'Sign in with Apple' : 'Continue with Apple', disabled: dis })}>${o.loading === 'apple' ? ui.loader({ size: 20, label: 'Signing in' }) : APPLE}<span>${o.loading === 'apple' ? 'Signing in…' : ctx.ios ? 'Sign in with Apple' : 'Continue with Apple'}</span></span>`;
    const google = `<span class="acc-sso is-google ui-press${dis ? ' e-dis' : ''}" ${ui.act({ action: o.loading ? null : 'sso', arg: 'google', label: 'Continue with Google', disabled: dis })}>${o.loading === 'google' ? ui.loader({ size: 20, label: 'Signing in' }) : GOOGLE}<span>${o.loading === 'google' ? 'Signing in…' : 'Continue with Google'}</span></span>`;
    const email = `<span class="acc-sso is-email ui-press${dis ? ' e-dis' : ''}" ${ui.act({ action: o.loading ? null : 'email', label: 'Continue with email', disabled: dis })}>${MAIL_S}<span>Continue with email</span></span>`;
    return `<div class="acc-ssos">${ctx.ios ? apple + google : google + apple}${email}</div>`;
  }
  screen('S-ACC-02', {
    name: 'Sign-in sheet',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'profile', detent: 'auto', over: 'S-ACC-01',
    states: [{ id: 'default', label: 'Def' }, { id: 'Load', label: 'Load (signing in)' }, { id: 'Err', label: 'Err' }, { id: 'Off', label: 'Off (offline)' }, { id: 'emailEntry', label: 'email entry (extra)' }],
    render(ctx) {
      const { ui, state, params } = ctx;
      const reauth = params.reauth === '1', backup = params.next === 'backup';
      const title = reauth ? 'Confirm it’s you' : backup ? 'Sign in to back up' : 'Sign in';
      const lead = reauth ? 'Sign in again to delete your account. This keeps anyone else from deleting it.' : 'Sign in only if you want cloud backup or to use a second phone. Everything else works without an account.';
      const banner = state === 'Err' ? ui.banner({ kind: 'critical', title: 'Sign-in didn’t finish', text: 'Nothing changed on this phone. Try again, or use another way to sign in.' })
        : state === 'Off' ? ui.banner({ kind: 'info', icon: 'gps', title: 'You’re offline', text: 'Signing in needs a connection. Everything else keeps working on this phone.' }) : '';
      const email = state === 'emailEntry' || ctx.store.email;
      const emailBlock = email ? `<div class="acc-email">${ui.textField({ label: 'Email', value: 'marcus@example.com', focused: true, helper: 'We’ll email you a link. No password.' })}${ui.button('Send link', { kind: 'filled', size: 'l', block: true, action: 'sendLink' })}</div>` : '';
      return `<div class="acc-si">${ui.sheetHeader(title, { icon: `<span class="acc-si-ic">${ctx.icons.loop({ size: 26, color: 'var(--dp-accent)', dot: 'var(--dp-text)' })}</span>` })}<p class="e-lead">${ui.esc(lead)}</p>
        ${banner ? `<div class="acc-si-b">${banner}</div>` : ''}
        ${email ? emailBlock : signInButtons(ctx, { loading: state === 'Load' ? (ctx.ios ? 'apple' : 'google') : null, disabled: state === 'Off' || state === 'Load' })}
        ${state === 'Err' ? `<div class="acc-si-retry">${ui.button('Try again', { kind: 'text', size: 's', icon: 'refresh', state: 'default' })}</div>` : ''}
        ${checks(ctx, [{ kind: 'ok', text: 'On Free, signing in uploads no trip data' }, { kind: 'ok', text: 'We keep your name (optional), email and an account ID' }, { kind: 'no', text: 'No ads, no tracking, never sold' }], { cls: 'acc-si-ck' })}
        <p class="e-foot">By signing in you agree to the <span class="e-link">Terms</span> and <span class="e-link">Privacy Policy</span>.</p></div>`;
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '.acc-sso, .acc-email', { stagger: 40, delay: 60 }); },
    actions: {
      sso: (ctx, which) => {
        const p = ctx.params, name = which === 'apple' ? 'Apple' : 'Google';
        ctx.setState('Load');
        setTimeout(() => {
          if (p.next === 'backup') return ctx.nav('S-ACC-05', {}, { replace: true });
          if (p.reauth === '1') { const r = DP.router; return Promise.resolve(ctx.back()).then(() => { const top = r.stack[r.stack.length - 1]; if (top && top.id === 'S-PRV-06') r.setState('summaryOfWhatIsDeleted'); }); }
          ctx.back(); ctx.toast(`Signed in with ${name}. No trip data is uploaded unless you turn on backup.`, { icon: 'check', duration: 3400 });
        }, 1100);
        return false;
      },
      email: (ctx) => { ctx.store.email = true; ctx.update(); return false; },
      sendLink: (ctx) => { ctx.nav('S-ACC-03', {}, { replace: true }); return false; },
    },
    spec: {
      purpose: 'Optional sign-in with Apple, Google or an email link (F-ACC-02), only for cloud backup and a second phone. It says plainly that a Free sign-in uploads no trip data. Also used for re-authentication before account deletion (S-PRV-06).',
      layout: [
        'Sheet fitted to content (iOS partial detent, glass · Android ModalBottomSheet). Header: Loop mark in a 40 tile + title “Sign in” (“Sign in to back up” from S-ACC-04 · “Confirm it’s you” for re-auth) + close (iOS).',
        'Lead (body-secondary, text-2). Optional banner (Err critical / Off info).',
        'Provider buttons, 52 pt / 56 dp tall, radius capsule (iOS) / full (Android), full width, 10 apart: iOS order Apple (white on dark, black on light, per HIG) · Google (surface with the four-colour G) · email (tonal, envelope). Android order Google · Apple (web flow) · email.',
        'Email entry (after “Continue with email”): text field “Email” (iOS rounded field · Android OutlinedTextField) with helper “We’ll email you a link. No password.” + filled “Send link”.',
        'Checklist: ✓ On Free, signing in uploads no trip data · ✓ We keep your name (optional), email and an account ID · ✕ No ads, no tracking, never sold. Footnote with Terms and Privacy Policy links.',
        'Load: the tapped provider button shows the loader + “Signing in…”, the rest are disabled (38%).',
      ],
      components: ['sheetHeader', 'banner', 'textField', 'button', 'loader', 'sheet'],
      interactions: [
        'Sign in with Apple → native ASAuthorization sheet (iOS) / web flow (Android) → signed in: back to the caller + toast “Signed in with Apple. No trip data is uploaded unless you turn on backup.” From S-ACC-04 (next = backup) → S-ACC-05 consent. Re-auth → back to S-PRV-06 summary.',
        'Continue with Google → Credential Manager / Google Sign-In → same as above.',
        'Continue with email → email field → Send link → S-ACC-03 (pushed inside the sheet).',
        'Err: “Try again” → default. Off: buttons disabled; the sheet stays open.',
        'On success the purchase identity is linked (RevenueCat app user ID = Firebase UID) so Pro follows the account.',
      ],
      motion: ['Provider buttons rise 8 px, 40 ms stagger, spring.default. Loader: iOS Loop / Android M3E shape morph. RM: fades; static loader.'],
      a11y: ['Buttons are labelled exactly as shown (“Sign in with Apple”). Loading announces “Signing in”. The banner is a status region.', 'XL: the sheet opens at the large detent; button labels wrap to two lines rather than truncating.'],
      platformDiffs: { ios: 'SignInWithAppleButton (style white on dark / black on light) first, then Google; glass sheet at a partial detent.', android: 'Credential Manager “Sign in with Google” first; Apple through the web flow (Custom Tab); ModalBottomSheet.' },
      analytics: ['sign_in_started {method}', 'sign_in_completed {method}', 'sign_in_failed {method, reason}'],
      copy: {
        Title: 'Sign in · Sign in to back up · Confirm it’s you',
        Lead: 'Sign in only if you want cloud backup or to use a second phone. Everything else works without an account.',
        'Re-auth lead': 'Sign in again to delete your account. This keeps anyone else from deleting it.',
        Err: 'Sign-in didn’t finish. Nothing changed on this phone. Try again, or use another way to sign in.',
        Off: 'You’re offline. Signing in needs a connection. Everything else keeps working on this phone.',
        Promise: 'On Free, signing in uploads no trip data · We keep your name (optional), email and an account ID · No ads, no tracking, never sold',
      },
      freePro: 'Free for everyone. Pro users can then turn on backup (S-ACC-05). Free users who sign in upload nothing.',
      data: 'No account fixture exists; the email field uses the test value marcus@example.com.',
      icons: ['bell', 'refresh', 'check', 'close', 'gps'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ---------------- S-ACC-03 · Check your email (pushed inside the sign-in sheet) ---------------- */
  screen('S-ACC-03', {
    name: 'Check your email (email link)',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'profile', detent: 'auto', over: 'S-ACC-01',
    states: [{ id: 'default', label: 'Sent' }, { id: 'linkExpired', label: 'link expired' }],
    render(ctx) {
      const { ui, state } = ctx;
      const exp = state === 'linkExpired';
      const back = `<span class="acc-back ui-press" ${ui.act({ action: 'toSignIn', label: 'Back to sign in' })}>${ui.icon(ctx.ios ? 'chevronLeft' : 'arrowBack', 22)}</span>`;
      return `<div class="acc-mail">${ui.sheetHeader('', { leading: back })}
        <div class="acc-mail-art" data-rise>${art(ctx, exp ? ui.icon('clock', 44) : MAIL, { tone: exp ? 'warn' : '' })}</div>
        <div class="acc-mail-t" data-rise><h2 class="e-h1">${exp ? 'This link has expired' : 'Check your email'}</h2>
          <p class="e-lead">${exp ? 'Sign-in links work once and only for a limited time. We can send you a new one.' : 'We sent a sign-in link to <b>marcus@example.com</b>. Open it on this phone to finish signing in.'}</p></div>
        <div class="e-sheetacts" data-rise>${exp
          ? ui.button('Send a new link', { kind: 'filled', size: 'l', block: true, action: 'resend', state: 'default' })
          : ui.button(ctx.ios ? 'Open Mail' : 'Open email app', { kind: 'filled', size: 'l', block: true, toast: ctx.ios ? 'Opens Mail' : 'Opens your email app' })}
          ${ui.button('Use a different email', { kind: 'tonal', size: 'l', block: true, action: 'toSignIn' })}</div>
        ${exp ? '' : `<p class="e-foot acc-mail-f">Didn’t get it? Check your spam folder, or <span class="e-link ui-press" ${ui.act({ action: 'resend', label: 'Send the link again' })}>send it again</span>.</p>`}</div>`;
    },
    mount(el, ctx) { ctx.ui.anim.pop(el.querySelector('.e-art'), 40); ctx.ui.anim.rise(el, '[data-rise]:not(.acc-mail-art)', { stagger: 40, delay: 80 }); },
    actions: {
      toSignIn: (ctx) => { ctx.nav('S-ACC-02/emailEntry', {}, { replace: true }); return false; },
      resend: (ctx) => { ctx.toast('New link sent to marcus@example.com', { icon: 'check' }); if (ctx.state !== 'default') ctx.setState('default'); return false; },
    },
    spec: {
      purpose: 'Confirms that an email sign-in link was sent and that it signs the user in on this same phone (F-ACC-02.2), with recovery when the link has expired.',
      layout: [
        'Pushed inside the sign-in sheet (NavigationStack in the sheet on iOS; in-sheet navigation on Android). The prototype renders it as the sheet’s next page: leading back chevron (iOS) / arrow (Android), no title in the bar.',
        'Illustration 104 (envelope with a Glide dot; expired = clock on a warning halo). Title (28/34 display) + lead with the address in bold.',
        'Buttons stacked, full width, 52 pt / 56 dp: “Open Mail” / “Open email app” (filled) · “Use a different email” (tonal). Expired: “Send a new link” (filled) · “Use a different email”.',
        'Footnote: “Didn’t get it? Check your spam folder, or send it again.”',
      ],
      components: ['sheetHeader', 'button', 'sheet'],
      interactions: ['Back / “Use a different email” → S-ACC-02 with the email field open.', 'Open Mail → the mail app (mailto: / ACTION_MAIN CATEGORY_APP_EMAIL).', 'Send it again / Send a new link → toast “New link sent to …”.', 'Opening the link (Universal Link / App Link on the Firebase Hosting domain) signs in and returns to the caller.'],
      motion: ['Envelope pops (scale .86 → 1); text and buttons rise 8 px, 40 ms stagger. RM: fade.'],
      a11y: ['Focus lands on the title. The address is read in full. XL: the sheet opens large; buttons wrap.'],
      platformDiffs: { ios: 'Pushed in the sheet’s NavigationStack with a back chevron; “Open Mail”.', android: 'In-sheet destination with the back arrow and predictive back; “Open email app”.' },
      analytics: ['sign_in_link_sent', 'sign_in_link_resent', 'sign_in_link_expired'],
      copy: { Sent: 'Check your email. We sent a sign-in link to {email}. Open it on this phone to finish signing in.', Expired: 'This link has expired. Sign-in links work once and only for a limited time. We can send you a new one.', Footnote: 'Didn’t get it? Check your spam folder, or send it again.' },
      freePro: 'Free.',
      data: 'Test email marcus@example.com (no account fixture). Link lifetime is set by A17 (Firebase email-link settings); the copy avoids a number until it is confirmed.',
      icons: ['chevronLeft', 'arrowBack', 'bell', 'clock'],
      assets: [],
    },
  });

  /* ---------------- S-ACC-04 · Cloud backup settings (push) ---------------- */
  const PENDING = ['t20261106-1210', 't20261105-1745'];
  screen('S-ACC-04', {
    name: 'Cloud backup settings',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile',
    states: [
      { id: 'default', label: 'Off' }, { id: 'onAndUpToDate', label: 'on and up to date' }, { id: 'pending', label: 'pending' }, { id: 'wiFiOnly', label: 'Wi-Fi only (waiting)' },
      { id: 'Err', label: 'Err' }, { id: 'Lock', label: 'Lock (Free)' }, { id: 'lapsedProWarning', label: 'lapsed Pro warning' },
    ],
    stateParams: { Lock: { ent: 'free' }, lapsedProWarning: { ent: 'free' } },
    render(ctx) {
      const { ui, data, state } = ctx;
      const ds = D.privacy.dataSize;
      const on = ['onAndUpToDate', 'pending', 'wiFiOnly', 'Err'].includes(state);
      const cell = (st) => ({
        default: ['off', '', 'Backup is off', 'Your drives are only on this phone. Turn on backup to keep a copy in the cloud and restore it on any phone.'],
        onAndUpToDate: ['ok', 'positive', 'Up to date', `Last backup today, 2:05 pm · ${ds.trips} drives · ${ds.mb} MB`],
        pending: ['up', '', `${PENDING.length} drives waiting`, 'They’ll upload on Wi-Fi when you’re not driving.'],
        wiFiOnly: ['up', '', 'Waiting for Wi-Fi', `${PENDING.length} drives will upload when this phone is on Wi-Fi. Backup doesn’t use mobile data.`],
        Err: ['warn', 'critical', 'Backup didn’t finish', 'Your drives are safe on this phone. We’ll try again on Wi-Fi.'],
        lapsedProWarning: ['off', 'warn', 'Backup has stopped', 'Your Pro plan ended, so nothing new is backed up.'],
      })[st];
      const blocks = [];
      if (state === 'Lock') {
        const inner = `<div class="acc-st is-lock">${art(ctx, CLOUD('ok'), { tone: 'positive' })}<div><b>Up to date</b><p>${ds.trips} drives · ${ds.mb} MB backed up</p></div></div>${ui.stats([{ label: 'Drives', value: ds.trips }, { label: 'Storage', value: ds.mb, unit: 'MB' }, { label: 'Pending', value: 0 }])}`;
        blocks.push(ui.pad(ui.lockPanel(ui.card(inner), { text: D.entitlement.lockSheets.backup, nav: 'S-SUB-03/backup' })));
        blocks.push(ui.pad(checks(ctx, [{ kind: 'ok', text: 'On Free, your drives stay on this phone', sub: 'Nothing is deleted because of your plan' }, { kind: 'ok', text: 'Download all your data any time, free', sub: 'Privacy Centre → Download all my data' }])));
        blocks.push(ui.section('', ui.list([ui.row({ icon: 'export', title: 'Download all my data', nav: 'S-PRV-03' })]), { style: 'caption' }));
      } else {
        const [cl, tone, title, sub] = cell(state);
        blocks.push(ui.pad(ui.card(`<div class="acc-st">${art(ctx, CLOUD(cl), { tone })}<div><b>${ui.esc(title)}</b><p>${ui.esc(sub)}</p></div></div>${state === 'Err' ? `<div class="acc-st-a">${ui.button('Try again', { kind: 'tonal', size: 's', icon: 'refresh', toast: 'Backing up…' })}</div>` : ''}`, { hero: true })));
        if (state === 'lapsedProWarning') blocks.push(ui.pad(ui.banner({ kind: 'attention', icon: 'clock', title: 'Your cloud copy is kept until 6 December 2026', text: 'Then it’s deleted. Drives on this phone aren’t affected. We’ll remind you 30, 7 and 1 day before.', action: { text: 'Keep it', nav: 'S-SUB-04', params: { from: 'backup' } } })));
        if (state === 'pending' || state === 'wiFiOnly') blocks.push(ui.section('Waiting to upload', `<div class="ui-card is-flush acc-pend" role="list">${PENDING.map((id) => ui.tripRow(data.trip(id), { ent: ctx.ent })).join('')}</div>`, { style: 'caption' }));
        if (state === 'lapsedProWarning') blocks.push(ui.section('Your cloud copy', ui.list([
          ui.row({ icon: 'export', title: 'Download all my data', sub: 'Free, from this phone, any time', nav: 'S-PRV-03' }),
          ui.row({ icon: 'trash', iconTone: 'critical', title: 'Delete the cloud copy now', destructive: true, nav: 'S-PRV-05', trailing: '' }),
        ]), { style: 'caption' }));
        else blocks.push(ui.section('', ui.list([ui.row({ icon: 'cloud', title: 'Back up my drives', sub: on ? 'New drives upload within a day' : 'Off', trailing: 'toggle', on, action: on ? 'turnOff' : 'turnOn' })]), { style: 'caption' }));
        if (on) blocks.push(ui.section('Options', ui.list([
          ui.row({ icon: 'gps', title: 'Also use mobile data', sub: state === 'wiFiOnly' ? 'Off: Wi-Fi only' : 'Off by default', trailing: 'toggle', on: !!ctx.store.cell, action: 'cell' }),
          ui.row({ icon: 'analysis', title: 'Include raw sensor data', sub: `${ds.rawMb} MB of the ${ds.mb} MB. Needed to restore Analysis.`, trailing: 'toggle', on: ctx.store.raw !== false, action: 'raw' }),
          ui.row({ icon: 'battery', iconTone: 'neutral', title: 'Only when it’s easy on the battery', sub: 'Charging or above 50%, never during a trip', trailing: '' }),
        ]), { style: 'caption' }));
        blocks.push(ui.section('Your backup', ui.list([
          ui.row({ icon: 'refresh', title: 'Restore from backup', sub: 'Bring your drives back on this phone', nav: 'S-ACC-06' }),
          ui.row({ icon: 'info', title: 'What’s backed up', sub: 'Routes with precise location, sensor data, scores, cars', nav: 'S-ACC-05' }),
          ui.row({ icon: 'export', iconTone: 'neutral', title: 'Sign out', nav: 'S-ACC-07' }),
        ]), { style: 'caption' }));
        blocks.push(ui.pad(`<div class="ui-card">${`<div class="e-kv"><div><span>Stored in</span><b>Google Firebase, US region</b></div><div><span>Encryption</span><b>In transit and at rest</b></div><div><span>When</span><b>Wi-Fi, not during a trip</b></div><div><span>Delete</span><b>Any time, in Privacy Centre</b></div></div>`}</div>`));
      }
      return ui.page(`<div class="acc4">${blocks.map((b) => `<div class="acc-b" data-rise>${b}</div>`).join('')}</div>`, { nav: { title: 'Cloud backup', subtitle: state === 'Lock' ? 'Pro' : null } });
    },
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '.acc-b[data-rise]', { stagger: 30 });
      const a = el.querySelector('.acc-st .e-art svg');
      if (a && !still(ctx, el) && ['pending', 'wiFiOnly'].includes(ctx.state)) ctx.motion.animate(a, [{ transform: 'translateY(3px)' }, { transform: 'translateY(-3px)' }, { transform: 'translateY(3px)' }], { duration: 2400, easing: 'ease-in-out' });
    },
    actions: {
      turnOn: (ctx) => { ctx.nav('S-ACC-05'); return false; },
      turnOff: (ctx) => { ctx.toast('Backup off. Your cloud copy stays until you delete it.', { icon: 'cloud' }); ctx.setState('default'); return false; },
      cell: (ctx) => { ctx.store.cell = !ctx.store.cell; ctx.update(); return false; },
      raw: (ctx) => { ctx.store.raw = ctx.store.raw === false; ctx.update(); return false; },
    },
    spec: {
      purpose: 'Turn Pro cloud backup on or off and see its honest status: last backup, what is waiting, storage used, errors, and what happens if Pro lapses (F-ACC-03). Nothing ever uploads during a trip.',
      layout: [
        'Push with title “Cloud backup” (iOS inline + glass back · Android MediumFlexibleTopAppBar).',
        'Status hero card (radius 32/28): cloud illustration 104 (off = slashed, up to date = check on a positive halo, waiting = up arrow, error = “!” on a critical halo) + title (cardTitle) + one line. Err adds a tonal “Try again”.',
        'lapsed Pro warning: attention banner “Your cloud copy is kept until 6 December 2026 …” + “Keep it” → S-SUB-04.',
        'pending / Wi-Fi only: “Waiting to upload” trip cards (the two newest drives).',
        'Toggle row “Back up my drives”. When on: Options (Also use mobile data · Include raw sensor data 151 MB of 186 MB · battery rule, informational) and Your backup (Restore · What’s backed up · Sign out), then a facts card (Stored in Google Firebase, US region · Encryption · When · Delete).',
        'Lock (Free): the user’s own backup status blurred (14 px, 35% dim) in a lock panel: “Back up your drives and restore them on any phone with Pro.” + See plans; then reassurance checklist and “Download all my data”.',
      ],
      components: ['navBar', 'card', 'banner', 'tripRow', 'list', 'row', 'toggle', 'stats', 'lockPanel', 'button', 'section'],
      interactions: [
        'Toggle on (first time) → S-ACC-05 consent; if not signed in → S-ACC-02 (next = backup) first. Toggle off → toast “Backup off. Your cloud copy stays until you delete it.”',
        'Restore from backup → S-ACC-06 · What’s backed up → S-ACC-05 · Sign out → S-ACC-07 · Keep it → S-SUB-04 · Lock panel / See plans → S-SUB-03 backup.',
        'Try again → retries (toast). Mobile data / raw data toggles update in place (haptic toggleOn/Off).',
      ],
      motion: ['Blocks rise 8 px, 30 ms stagger. Waiting states: the cloud glyph bobs 3 px once (2400 ms ease-in-out). RM: static.'],
      a11y: ['Status card is read as title + line (“Up to date. Last backup today, 2:05 pm, 30 drives, 186 megabytes.”).', 'Toggle rows are switches with their titles as labels. The lock panel reads “Back up your drives and restore them on any phone with Pro. See plans.”', 'XL: facts card stacks label over value; rows wrap.'],
      platformDiffs: { ios: 'BGProcessingTask (requires external power optional; Wi-Fi by default); inset-grouped lists; Toggle with Glide tint.', android: 'WorkManager (unmetered network constraint by default, battery not low); M3 Switch with thumb icon; segmented ListItems.' },
      analytics: ['backup_enabled', 'backup_disabled', 'backup_run {result, items_bucket}', 'lock_viewed {feature: backup}'],
      copy: {
        Off: 'Backup is off. Your drives are only on this phone. Turn on backup to keep a copy in the cloud and restore it on any phone.',
        'Up to date': 'Up to date · Last backup today, 2:05 pm · 30 drives · 186 MB',
        Pending: '2 drives waiting. They’ll upload on Wi-Fi when you’re not driving.',
        'Wi-Fi only': 'Waiting for Wi-Fi. 2 drives will upload when this phone is on Wi-Fi. Backup doesn’t use mobile data.',
        Err: 'Backup didn’t finish. Your drives are safe on this phone. We’ll try again on Wi-Fi.',
        Lapsed: 'Backup has stopped. Your Pro plan ended, so nothing new is backed up. Your cloud copy is kept until 6 December 2026. Then it’s deleted. Drives on this phone aren’t affected. We’ll remind you 30, 7 and 1 day before.',
        Lock: 'Back up your drives and restore them on any phone with Pro. (entitlement.lockSheets.backup)',
      },
      freePro: 'Pro (and Preview / Ride-along). Free: Lock state with the user’s own backup preview blurred. Lapsed: backup stops, cloud copy kept 30 days (OQ-09, decided 6 Nov), warnings at 30/7/1 days).',
      data: 'Sizes from privacy.dataSize (30 drives, 186 MB, raw 151 MB). Pending = the two newest drives (t20261106-1210, t20261105-1745). “Last backup today, 2:05 pm” and the 6 Dec 2026 retention date (today + 30 days, OQ-09 decision) are prototype values.',
      icons: ['cloud', 'refresh', 'gps', 'analysis', 'battery', 'info', 'export', 'clock', 'lock'],
      assets: [],
    },
  });

  /* ---------------- S-ACC-05 · Backup consent (sheet, large) ---------------- */
  screen('S-ACC-05', {
    name: 'Backup consent',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'profile', detent: 'large', over: 'S-ACC-04',
    states: [{ id: 'default', label: 'Def' }],
    render(ctx) {
      const { ui } = ctx;
      const what = [['pin', 'Your routes, with precise location', 'Every drive’s GPS track'], ['analysis', 'Sensor data from each drive', 'Motion and G, for Replay and Analysis'], ['story', 'Scores, Stories and records', 'Plus routes, events and tips'], ['car', 'Cars and trip settings', 'Names, colours, which car you drove']];
      return `<div class="acc5">${ui.sheetHeader('Before you turn on backup', { sub: 'Here’s exactly what leaves this phone.' })}
        <div class="acc5-what" data-rise>${what.map(([ic, t, s]) => `<div class="acc5-w"><span class="ui-row-ic">${ui.icon(ic, 20)}</span><span><b>${t}</b><small>${s}</small></span></div>`).join('')}</div>
        ${ui.section('Where it goes', `<div class="ui-card" data-rise><div class="e-kv"><div><span>Stored by</span><b>Google Firebase</b></div><div><span>Region</span><b>United States (outside Canada)</b></div><div><span>Protection</span><b>Encrypted in transit and at rest</b></div><div><span>Kept</span><b>Until you delete it, or 30 days after Pro ends</b></div></div></div>`, { style: 'caption' })}
        <div data-rise>${checks(ctx, [{ kind: 'ok', text: 'Turn it off or delete the cloud copy any time', sub: 'Privacy Centre → Delete data' }, { kind: 'ok', text: 'Nothing uploads during a trip' }, { kind: 'no', text: 'Never sold, never shared with insurers, never used for ads' }])}</div>
        <div class="e-sheetacts">${ui.button('Turn on backup', { kind: 'filled', size: 'l', block: true, action: 'agree', haptic: 'confirm' })}${ui.button('Not now', { kind: 'tonal', size: 'l', block: true, back: true })}</div></div>`;
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '[data-rise], .acc5-w', { stagger: 35 }); },
    actions: {
      agree: (ctx) => { const r = DP.router; Promise.resolve(ctx.back()).then(() => { const top = r.stack[r.stack.length - 1]; if (top && top.id === 'S-ACC-04') r.setState('pending'); ctx.toast('Backup on. Your first backup starts on Wi-Fi.', { icon: 'cloud' }); }); return false; },
    },
    spec: {
      purpose: 'Explicit, informed consent before any trip data leaves the phone (F-ACC-03.1; Apple 5.1.5, PIPEDA meaningful consent, Law 25 transfer outside Quebec): what is uploaded, where it is stored, how it is protected, and that it can be deleted any time.',
      layout: [
        'Large-detent sheet. Header “Before you turn on backup” + sub “Here’s exactly what leaves this phone.”',
        'What: 4 rows on a card (icon tile 30 · title · sub): routes with precise location · sensor data · scores, Stories and records · cars and trip settings.',
        'Where it goes (caption section): facts card — Stored by Google Firebase · Region United States (outside Canada) · Encrypted in transit and at rest · Kept until you delete it, or 30 days after Pro ends.',
        'Checklist: ✓ turn it off or delete any time · ✓ nothing uploads during a trip · ✕ never sold, never shared with insurers, never used for ads.',
        'Stacked large buttons: “Turn on backup” (filled) · “Not now” (tonal). No pre-ticked boxes.',
      ],
      components: ['sheetHeader', 'card', 'section', 'button', 'sheet'],
      interactions: ['Turn on backup → consent recorded (timestamp + version) → back to S-ACC-04 in the pending state + toast “Backup on. Your first backup starts on Wi-Fi.” (haptic confirm).', 'Not now / drag down / close → back, backup stays off.'],
      motion: ['Rows rise 8 px, 35 ms stagger; spring.sheet presentation. RM: fades.'],
      a11y: ['Focus on the title. Each “what” row is one element (“Your routes, with precise location. Every drive’s GPS track.”).', 'XL: facts stack; buttons wrap.'],
      platformDiffs: { ios: 'Sheet .large with glass close; buttons glassProminent / glass.', android: 'Expanded ModalBottomSheet; filled + tonal buttons.' },
      analytics: ['backup_consent_viewed', 'backup_enabled'],
      copy: { Title: 'Before you turn on backup', Sub: 'Here’s exactly what leaves this phone.', What: 'Your routes, with precise location · Sensor data from each drive · Scores, Stories and records · Cars and trip settings', Where: 'Google Firebase · United States (outside Canada) · Encrypted in transit and at rest · Until you delete it, or 30 days after Pro ends', Never: 'Never sold, never shared with insurers, never used for ads' },
      freePro: 'Pro only (reached from S-ACC-04).',
      data: 'Retention “30 days after Pro ends” is the OQ-09 decision (6 Nov 2026); region per F-ACC-03.1.',
      icons: ['pin', 'analysis', 'story', 'car', 'check', 'close'],
      assets: [],
    },
  });

  /* ---------------- S-ACC-06 · Restore from backup (full) ---------------- */
  function ringProgress(ctx, frac, inner, o = {}) {
    const s = o.size || 148, r = s / 2 - 8, C = 2 * Math.PI * r;
    return `<div class="acc-rp" style="width:${s}px;height:${s}px" role="progressbar" aria-valuenow="${Math.round(frac * 100)}" aria-valuemin="0" aria-valuemax="100" aria-label="${ctx.ui.esc(o.label || '')}"><svg width="${s}" height="${s}" viewBox="0 0 ${s} ${s}" aria-hidden="true"><circle cx="${s / 2}" cy="${s / 2}" r="${r}" fill="none" stroke="var(--dp-fill-strong)" stroke-width="8"/><circle class="acc-rp-arc" cx="${s / 2}" cy="${s / 2}" r="${r}" fill="none" stroke="${o.color || 'var(--dp-accent)'}" stroke-width="8" stroke-linecap="round" stroke-dasharray="${(C * frac).toFixed(1)} ${C.toFixed(1)}" data-len="${(C * frac).toFixed(1)}" data-c="${C.toFixed(1)}" transform="rotate(-90 ${s / 2} ${s / 2})"/></svg><span>${inner}</span></div>`;
  }
  screen('S-ACC-06', {
    name: 'Restore from backup',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'full', tab: 'profile',
    states: [{ id: 'default', label: 'In progress' }, { id: 'done', label: 'done' }, { id: 'Err', label: 'Err' }, { id: 'Off', label: 'Off (offline)' }, { id: 'lowStorage', label: 'low storage' }],
    render(ctx) {
      const { ui, state } = ctx;
      const ds = D.privacy.dataSize, got = 12, mbGot = 74;
      const files = got / ds.trips;
      const T = {
        default: ['Restoring your drives', 'Summaries first, so your history is back right away. Trip files follow in the background.', ringProgress(ctx, files, `<b class="ui-num">${got}</b><small>of ${ds.trips}</small>`, { label: `${got} of ${ds.trips} trip files restored` })],
        done: ['Your drives are back', `${ds.trips} drives, ${D.routes.length} routes and ${D.vehicles.length} cars are on this phone again.`, art(ctx, 'check', { tone: 'positive', icon: 48 })],
        Err: ['Restore paused', 'Something went wrong on our side. Nothing was lost, and we’ll pick up exactly where we left off.', ringProgress(ctx, files, `${ui.icon('warn', 30)}`, { color: 'var(--dp-warning)', label: 'Restore paused' })],
        Off: ['You’re offline', `Restore carries on when you’re back online. ${got} of ${ds.trips} drives are already on this phone.`, ringProgress(ctx, files, `${ui.icon('gps', 30)}`, { color: 'var(--dp-text-3)', label: 'Restore waiting for a connection' })],
        lowStorage: ['Not enough space for every trip file', `Trip files need ${ds.mb} MB and this phone has 120 MB free. Your summaries, scores and Stories are already back.`, art(ctx, 'warn', { tone: 'warn', icon: 44 })],
      }[state];
      const rows = state === 'done'
        ? ui.stats([{ label: 'Drives', value: ds.trips }, { label: 'Routes', value: D.routes.length }, { label: 'Cars', value: D.vehicles.length }], { cols: 3 })
        : ui.card(`<div class="acc6-rows"><div class="acc6-r"><span class="acc6-ic is-done">${ui.icon('check', 16)}</span><span><b>Summaries</b><small>${ds.trips} of ${ds.trips} · scores, Stories, routes</small></span></div>
            <div class="acc6-r"><span class="acc6-ic">${ui.icon(state === 'lowStorage' ? 'warn' : 'layers', 16)}</span><span><b>Trip files</b><small>${got} of ${ds.trips} · ${mbGot} of ${ds.mb} MB</small>${progress(ctx, files, { label: `Trip files ${got} of ${ds.trips}` })}</span></div></div>`);
      const acts = {
        default: ui.button('Use DrivePerf while it finishes', { kind: 'filled', size: 'l', block: true, nav: 'S-HOME-01' }),
        done: ui.button('Open DrivePerf', { kind: 'filled', size: 'l', block: true, nav: 'S-HOME-01', haptic: 'success' }),
        Err: ui.button('Try again', { kind: 'filled', size: 'l', block: true, icon: 'refresh', state: 'default' }) + ui.button('Use DrivePerf meanwhile', { kind: 'text', size: 'm', block: true, nav: 'S-HOME-01' }),
        Off: ui.button('Use DrivePerf meanwhile', { kind: 'filled', size: 'l', block: true, nav: 'S-HOME-01' }),
        lowStorage: ui.button('Restore summaries only', { kind: 'filled', size: 'l', block: true, state: 'done' }) + ui.button(ctx.ios ? 'Open iPhone Storage' : 'Open storage settings', { kind: 'tonal', size: 'l', block: true, toast: ctx.ios ? 'Opens Settings → General → iPhone Storage' : 'Opens Settings → Storage' }),
      }[state];
      const note = state === 'default' ? '<p class="e-foot acc6-note">Keep DrivePerf on Wi-Fi for the fastest restore. Recording works as normal meanwhile.</p>' : state === 'lowStorage' ? '<p class="e-foot acc6-note">Restore the rest later from Cloud backup. Analysis and Replay need the trip files.</p>' : '';
      return ui.page(`<div class="acc6 is-${state}"><div class="acc6-art" data-rise>${T[2]}</div><div class="acc6-t ui-pad" data-rise><h1 class="e-h1">${ui.esc(T[0])}</h1><p class="e-lead">${ui.esc(T[1])}</p></div><div class="ui-pad" data-rise>${rows}</div>${note ? `<div class="ui-pad" data-rise>${note}</div>` : ''}<div style="height:150px"></div></div>`,
        { fixed: dock(acts, { cls: 'is-tall' }) });
    },
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '.acc6 > [data-rise]', { stagger: 40 });
      const arc = el.querySelector('.acc-rp-arc');
      if (arc && !still(ctx, el)) { const L = +arc.dataset.len, C = +arc.dataset.c; ctx.motion.tween({ from: 0, to: 1, duration: 900, ease: 'countUp', el, onUpdate: (p) => arc.setAttribute('stroke-dasharray', `${(L * p).toFixed(1)} ${C}`) }); }
      if (ctx.state === 'done') ctx.ui.anim.pop(el.querySelector('.e-art'), 60);
    },
    spec: {
      purpose: 'Bring a backed-up history onto this phone (F-ACC-04): summaries within 60 s, trip files in the background with progress, resumable without duplicates, and honest about offline, errors and low storage.',
      layout: [
        'Full-screen cover (no tab bar). Centre: 148 progress ring (8 stroke, Glide; paused = warning, offline = text-3) with “12 of 30” in DP Numerals, or a 104 illustration (done = check on positive halo, low storage = warning).',
        'Title (28/34) + lead. Progress card: Summaries (✓ 30 of 30 · scores, Stories, routes) and Trip files (12 of 30 · 74 of 186 MB + 6/8 px bar). Done: 3 stat tiles (Drives 30 · Routes 4 · Cars 2).',
        'Caption note. Bottom dock: in progress “Use DrivePerf while it finishes” · done “Open DrivePerf” · Err “Try again” + “Use DrivePerf meanwhile” · Off “Use DrivePerf meanwhile” · low storage “Restore summaries only” + “Open iPhone Storage / storage settings”.',
      ],
      components: ['page', 'card', 'stats', 'button'],
      interactions: ['Use DrivePerf… / Open DrivePerf → S-HOME-01 (the restore keeps running in the background; Home shows the drives as they arrive).', 'Try again → resumes (idempotent trip IDs, no duplicates).', 'Restore summaries only → done state with summaries; trip files can be fetched later from S-ACC-04. Open storage → OS settings.', 'Warn before download when storage is low (EC-STO).'],
      motion: ['Progress ring sweeps to its value (900 ms ease.countUp); done: the check pops (spring.default) + haptic success on “Open DrivePerf”. Blocks rise 8 px, 40 ms stagger. RM: final values, fades.'],
      a11y: ['Ring is a progressbar: “12 of 30 trip files restored”. Progress updates are announced at most every 10% (polite).', 'XL: the ring shrinks to 120; rows wrap; the dock grows.'],
      platformDiffs: { ios: 'fullScreenCover; downloads in a background URLSession; “Open iPhone Storage”.', android: 'Full-screen destination; WorkManager with a progress notification; “Open storage settings”.' },
      analytics: ['restore_started', 'restore_completed {trips_bucket, ms_bucket}', 'restore_failed {reason}'],
      copy: {
        'In progress': 'Restoring your drives. Summaries first, so your history is back right away. Trip files follow in the background.',
        Done: 'Your drives are back. 30 drives, 4 routes and 2 cars are on this phone again.',
        Err: 'Restore paused. Something went wrong on our side. Nothing was lost, and we’ll pick up exactly where we left off.',
        Off: 'You’re offline. Restore carries on when you’re back online. 12 of 30 drives are already on this phone.',
        'Low storage': 'Not enough space for every trip file. Trip files need 186 MB and this phone has 120 MB free. Your summaries, scores and Stories are already back.',
      },
      freePro: 'Pro (a signed-in account with a backup).',
      data: 'Totals from privacy.dataSize (30 drives, 186 MB), routes (4) and vehicles (2). Progress (12 of 30, 74 MB) and free space (120 MB) are prototype values.',
      icons: ['check', 'warn', 'gps', 'layers', 'refresh'],
      assets: [],
    },
  });

  /* ---------------- S-ACC-07 · Sign out sheet (medium) ---------------- */
  screen('S-ACC-07', {
    name: 'Sign out sheet',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'profile', detent: 'auto', over: 'S-ACC-01/signedInProWithBackup',
    states: [{ id: 'default', label: 'Keep local' }, { id: 'removeLocal', label: 'remove local' }, { id: 'pendingUploadsWarning', label: 'pending uploads warning' }],
    render(ctx) {
      const { ui, state } = ctx;
      const sel = state === 'removeLocal' || ctx.store.pick === 'remove' ? 'remove' : 'keep';
      const pend = state === 'pendingUploadsWarning';
      return `<div class="acc7">${ui.sheetHeader('Sign out?', { sub: 'Your cloud backup stays exactly as it is.' })}
        ${pend ? `<div class="acc7-b">${ui.banner({ kind: 'attention', title: `${PENDING.length} drives haven’t backed up yet`, text: 'If you sign out now they’ll only be on this phone. Remove them from this phone too and they’re gone.', action: { text: 'Back up now', toast: 'Backing up 2 drives…' } })}</div>` : ''}
        ${ui.radios([{ id: 'keep', label: 'Keep my drives on this phone', sub: 'You’ll be in local mode. Nothing changes here.' }, { id: 'remove', label: 'Remove my drives from this phone', sub: 'They stay in your cloud backup. Sign in again to restore them.' }], sel, { action: 'pick', cls: 'acc7-r' })}
        <div class="e-sheetacts">${ui.button(sel === 'remove' ? 'Sign out and remove' : 'Sign out', { kind: sel === 'remove' ? 'destructive' : 'filled', size: 'l', block: true, action: 'signOut', haptic: sel === 'remove' ? 'warning' : 'confirm', cls: sel === 'remove' ? 'acc7-destr' : '' })}${ui.button('Cancel', { kind: 'tonal', size: 'l', block: true, back: true })}</div></div>`;
    },
    actions: {
      pick: (ctx, id) => { ctx.store.pick = id; if (ctx.state === 'removeLocal' && id === 'keep') { ctx.setState('default'); return false; } ctx.update(); return false; },
      signOut: (ctx) => { const remove = ctx.state === 'removeLocal' || ctx.store.pick === 'remove'; ctx.back(); ctx.toast(remove ? 'Signed out. Drives removed from this phone; your backup is untouched.' : 'Signed out. You’re in local mode.', { icon: 'check', duration: 3200 }); return false; },
    },
    spec: {
      purpose: 'Sign out and choose what happens to the drives on this phone (F-ACC-05). Cloud data is never touched by signing out.',
      layout: [
        'Sheet fitted to content. Header “Sign out?” + sub “Your cloud backup stays exactly as it is.”',
        'pending uploads warning: attention banner “2 drives haven’t backed up yet …” + “Back up now”.',
        'Two radio rows (list): “Keep my drives on this phone” (default; “You’ll be in local mode. Nothing changes here.”) · “Remove my drives from this phone” (“They stay in your cloud backup. Sign in again to restore them.”).',
        'Stacked large buttons: “Sign out” (filled) — becomes “Sign out and remove” (destructive) when Remove is selected — and “Cancel” (tonal).',
      ],
      components: ['sheetHeader', 'banner', 'radios', 'button', 'sheet'],
      interactions: ['Radio → selects (haptic selection). Sign out → signs out, back to S-ACC-01 in local mode + toast. Remove → local trips deleted after sign-out (warning haptic). Back up now → starts the pending uploads first.', 'Cancel / drag / scrim → back.'],
      motion: ['spring.sheet; the destructive button label cross-fades when the choice changes. RM: fade.'],
      a11y: ['Radios are a radio group named “What happens to drives on this phone”. The destructive button reads “Sign out and remove drives from this phone”.', 'XL: the sheet opens large; rows wrap.'],
      platformDiffs: { ios: 'Glass sheet, partial detent; destructive button uses the .destructive role styling.', android: 'ModalBottomSheet; RadioButton ListItems; error-coloured text button for destructive.' },
      analytics: ['signed_out {keep_local}'],
      copy: { Title: 'Sign out?', Sub: 'Your cloud backup stays exactly as it is.', Keep: 'Keep my drives on this phone · You’ll be in local mode. Nothing changes here.', Remove: 'Remove my drives from this phone · They stay in your cloud backup. Sign in again to restore them.', Pending: '2 drives haven’t backed up yet. If you sign out now they’ll only be on this phone. Remove them from this phone too and they’re gone.' },
      freePro: 'Free.',
      data: 'Pending drives = t20261106-1210, t20261105-1745 (the two newest, as in S-ACC-04).',
      icons: ['warn'],
      assets: [],
    },
  });

  /* ==========================================================================================================
     PRIVACY CENTRE · privacy basics (download, delete) are never paywalled (A2 §5.3, F-PRV-01…07)
     ========================================================================================================== */
  const NEVER = (ctx) => `<div class="prv-never" role="group" aria-label="Never sold, never shared with insurers, never used for ads"><span class="prv-never-k">${ctx.ui.icon('shield', 16)}Never</span><div>${['Sold', 'Shared with insurers', 'Used for ads'].map((x) => `<span>${ctx.ui.icon('close', 14)}${x}</span>`).join('')}</div></div>`;
  const ROUTES_N = D.routes.length, CARS_N = D.vehicles.length;
  const prvNav = (ctx, title, sub) => ({ title, subtitle: sub || null });

  screen('S-PRV-01', {
    name: 'Privacy Centre',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile',
    states: [{ id: 'default', label: 'Local only' }, { id: 'withBackup', label: 'with backup (signed in)' }, { id: 'analyticsOnOff', label: 'analytics on/off' }],
    render(ctx) {
      const { ui, state } = ctx;
      const ds = D.privacy.dataSize, backup = state === 'withBackup';
      const usage = ctx.store.usage ?? (state === 'analyticsOnOff' ? true : D.user.consent.usageStats);
      const crash = ctx.store.crash ?? (state === 'analyticsOnOff' ? false : D.user.consent.crashReports);
      const z = D.user.privacyZones;
      const blocks = [
        `<div class="prv-hero ui-pad">${art(ctx, 'shield', { icon: 44 })}<div><h1 class="e-h1">${backup ? 'Your drives live on this phone, with a backup you control.' : 'Your drives live on this phone.'}</h1><p class="e-lead">Here’s everything DrivePerf keeps, where it is, and how to change it.</p></div></div>`,
        ui.pad(NEVER(ctx)),
        ui.section('Where your data is', ui.list([
          ui.row({ icon: 'phone', title: 'On this phone', sub: `${ds.trips} drives, ${ROUTES_N} routes, ${CARS_N} cars and their scores · ${ds.mb} MB`, nav: 'S-SET-07' }),
          ui.row({ lead: `<span class="ui-row-ic${backup ? ' is-pos' : ' is-neutral'}">${ui.icon('cloud', 20)}</span>`, title: 'In the cloud', sub: backup ? `Backup on · ${ds.trips} drives · Google Firebase, US region` : 'Nothing. Cloud backup is off.', value: backup ? 'On' : 'Off', nav: backup ? 'S-ACC-04/onAndUpToDate' : 'S-ACC-04' }),
          ui.row({ icon: 'insights', title: 'Usage statistics', sub: 'Screens and features used. No locations, routes, speeds or car names.', trailing: 'toggle', on: usage, action: 'usage' }),
          ui.row({ icon: 'info', title: 'Crash reports', sub: 'Technical details when the app crashes. No locations.', trailing: 'toggle', on: crash, action: 'crash' }),
          ui.row({ icon: 'pin', title: 'Privacy zones', sub: z.length ? `${z.length} zone · ${z.map((x) => `${x.name}, ${x.radiusM} m`).join(' · ')} · removed from share cards` : 'None yet', nav: z.length ? 'S-PRV-02/withZones' : 'S-PRV-02' }),
        ]), { style: 'caption', footer: 'Usage statistics and crash reports change straight away, in this session.' }),
        ui.section('Your controls', ui.list([
          ui.row({ icon: 'export', title: 'Download all my data', sub: 'Everything, in open formats. Free, always.', nav: 'S-PRV-03' }),
          ui.row({ icon: 'routes', title: 'Export a drive (GPX / CSV)', sub: 'For your own tools', trailing: ctx.isPro ? null : 'lock', nav: ctx.isPro ? 'S-PRV-04/dateRange' : 'S-SUB-03/export' }),
          ui.row({ icon: 'trash', iconTone: 'critical', title: 'Delete data', sub: 'Older drives, or everything', nav: 'S-PRV-05' }),
          backup ? ui.row({ icon: 'profile', iconTone: 'critical', title: 'Delete account and cloud data', nav: 'S-PRV-06' }) : '',
          ui.row({ icon: 'insights', title: 'Analytics and diagnostics', sub: 'Details, and reset your analytics ID', nav: 'S-PRV-07' }),
        ]), { style: 'caption' }),
        ui.section('Questions', ui.list([
          ui.row({ icon: 'bell', title: 'Privacy contact', sub: 'Keval Langalia · privacy@driveperf.com · we answer within 30 days', toast: 'Opens a new email to privacy@driveperf.com' }),
          ui.row({ icon: 'info', title: 'Privacy policy', sub: 'driveperf.com/privacy', toast: 'Opens driveperf.com/privacy' }),
        ]), { style: 'caption' }),
      ];
      return ui.page(`<div class="prv1">${blocks.map((b) => `<div class="acc-b" data-rise>${b}</div>`).join('')}</div>`, { nav: prvNav(ctx, 'Privacy Centre') });
    },
    mount(el, ctx) { ctx.ui.anim.pop(el.querySelector('.e-art'), 30); ctx.ui.anim.rise(el, '.acc-b[data-rise]', { stagger: 30, delay: 60 }); },
    actions: {
      usage: (ctx) => { const on = !(ctx.store.usage ?? (ctx.state === 'analyticsOnOff' ? true : D.user.consent.usageStats)); ctx.store.usage = on; ctx.update(); ctx.toast(on ? 'Usage statistics on' : 'Usage statistics off. Nothing more is sent.', { icon: 'check' }); return false; },
      crash: (ctx) => { const on = !(ctx.store.crash ?? (ctx.state === 'analyticsOnOff' ? false : D.user.consent.crashReports)); ctx.store.crash = on; ctx.update(); ctx.toast(on ? 'Crash reports on' : 'Crash reports off', { icon: 'check' }); return false; },
    },
    spec: {
      purpose: 'One place that shows what is stored where and lets the user control it (F-PRV-01): a plain data map (phone, cloud, usage statistics, crash reports, privacy zones), the “never” promise, links to every control, and the privacy contact, Keval Langalia (PIPEDA, Law 25).',
      layout: [
        'Push, title “Privacy Centre”. Hero: shield illustration 104 + headline (28/34) “Your drives live on this phone.” (with backup: “…, with a backup you control.”) + lead.',
        '“Never” card (radius 18, Glide-tinted outline): kicker “NEVER” with shield + three ✕ items: Sold · Shared with insurers · Used for ads.',
        'Where your data is (list): On this phone (30 drives, 4 routes, 2 cars and their scores · 186 MB) · In the cloud (Off / On + region) · Usage statistics (toggle) · Crash reports (toggle) · Privacy zones (1 zone · Home, 400 m). Footer: changes apply straight away.',
        'Your controls (list): Download all my data (Free, always) · Export a drive (GPX/CSV; PRO + lock on Free) · Delete data · Delete account and cloud data (signed in only) · Analytics and diagnostics.',
        'Questions (list): Privacy contact, Keval Langalia (email, response time) · Privacy policy (web).',
      ],
      components: ['navBar', 'list', 'row', 'toggle', 'section'],
      interactions: [
        'On this phone → S-SET-07 storage · In the cloud → S-ACC-04 · toggles flip usage/crash consent immediately (toast; haptic toggleOn/Off) · Privacy zones → S-PRV-02.',
        'Download → S-PRV-03 · Export → S-PRV-04 (date range) or S-SUB-03 export (Free) · Delete data → S-PRV-05 · Delete account → S-PRV-06 · Analytics and diagnostics → S-PRV-07.',
        'Privacy contact → mailto: · Privacy policy → in-app browser (S-WEB-03).',
      ],
      motion: ['Shield pops in, blocks rise 8 px with a 30 ms stagger. RM: fade.'],
      a11y: ['The never card is one group: “Never sold, never shared with insurers, never used for ads”.', 'Toggles are switches labelled “Usage statistics” / “Crash reports”, with the sub line as their hint.', 'XL: rows wrap; the hero stacks.'],
      platformDiffs: { ios: 'Inset-grouped lists, Toggle with Glide tint, inline title + glass back.', android: 'MediumFlexibleTopAppBar, M3E segmented ListItems, M3 Switch with thumb icons.' },
      analytics: ['privacy_centre_opened', 'analytics_consent_set {usage, crash} (sent only if usage is on)'],
      copy: {
        Headline: 'Your drives live on this phone. · Your drives live on this phone, with a backup you control.',
        Lead: 'Here’s everything DrivePerf keeps, where it is, and how to change it.',
        Never: 'Never: Sold · Shared with insurers · Used for ads',
        Cloud: 'Nothing. Cloud backup is off. · Backup on · 30 drives · Google Firebase, US region',
        Usage: 'Screens and features used. No locations, routes, speeds or car names.',
        Crash: 'Technical details when the app crashes. No locations.',
        Officer: 'Privacy contact · Keval Langalia · privacy@driveperf.com · we answer within 30 days',
      },
      freePro: 'Everything here is Free, including the full download and every deletion. Only per-trip GPX/CSV export is Pro (lock → S-SUB-03 export).',
      data: 'Consent defaults from DP_DATA.user.consent (usage off, crash on); analytics on/off flips both. Sizes from privacy.dataSize; counts from routes (4) and vehicles (2); zones from user.privacyZones. The privacy contact is Keval Langalia (decided 6 Nov 2026); the 30-day response time is a placeholder pending legal (no fixture).',
      icons: ['shield', 'close', 'phone', 'cloud', 'insights', 'info', 'pin', 'export', 'routes', 'trash', 'profile', 'bell'],
      assets: [],
    },
  });

  /* ---------------- S-PRV-02 · Privacy zones ---------------- */
  /** Zone map: equirectangular, centred on the zone, ~2.4 km wide; roads from every fixture trip (© OSM). */
  function zoneMap(ctx, c, radiusM, o = {}) {
    const w = 370, h = o.h || 230, mpp = 2600 / w, k = Math.cos((c.lat * Math.PI) / 180) * 111320;
    const P = (la, ln) => [w / 2 + ((ln - c.lng) * k) / mpp, h / 2 - ((la - c.lat) * 111320) / mpp];
    let roads = '';
    for (const t of D.trips) {
      let d = '', on = false;
      for (const r of t.track.rows) { if (r[1] == null) { on = false; continue; } const [x, y] = P(r[1], r[2]); if (x < -40 || x > w + 40 || y < -40 || y > h + 40) { on = false; continue; } d += `${on ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`; on = true; }
      if (d) roads += `<path d="${d}"/>`;
    }
    const R = radiusM / mpp, id = ctx.ui.nid('hz');
    return `<div class="prv-map" role="img" aria-label="${ctx.ui.esc(`Map of the ${c.name} privacy zone, ${radiusM} metres around the pin`)}"><svg width="100%" height="${h}" viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
      <defs><pattern id="${id}" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><path d="M0 0V7" stroke="var(--dp-accent)" stroke-width="1.6" opacity=".35"/></pattern></defs>
      <rect width="${w}" height="${h}" fill="var(--dp-data-map-land)"/><g fill="none" stroke="var(--dp-data-map-road)" stroke-width="4" stroke-linecap="round" stroke-linejoin="round">${roads}</g>
      <circle class="prv-zone" cx="${w / 2}" cy="${h / 2}" r="${R.toFixed(1)}" fill="url(#${id})" stroke="var(--dp-accent)" stroke-width="2" stroke-dasharray="6 5"/>
      <g transform="translate(${w / 2 - 14} ${h / 2 - 30})"><path d="M14 30s-11-10.5-11-18.5a11 11 0 0 1 22 0C25 19.5 14 30 14 30z" fill="var(--dp-accent)" stroke="var(--dp-bg-grouped)" stroke-width="2"/><circle cx="14" cy="11.5" r="4" fill="var(--dp-on-accent)"/></g></svg>
      <span class="prv-map-chip ui-glassy">${ctx.ui.esc(c.name)} · <b class="ui-num">${radiusM}</b> m</span>${o.drag ? `<span class="prv-map-hint ui-glassy">${ctx.ui.icon('pin', 14)}Drag the map to move the pin</span>` : ''}<span class="ui-map-attr">© OpenStreetMap contributors</span></div>`;
  }
  screen('S-PRV-02', {
    name: 'Privacy zones',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile', backTo: 'S-PRV-01',
    states: [{ id: 'default', label: 'Empty' }, { id: 'withZones', label: 'with zones' }, { id: 'adding', label: 'adding (map pin + radius)' }],
    render(ctx) {
      const { ui, state, params } = ctx;
      const places = D.user.places, zones = D.user.privacyZones;
      let body;
      if (state === 'adding') {
        const p = places.find((x) => x.name === (ctx.store.place || params.place || 'Work')) || places[1];
        const r = +(ctx.store.r || 400);
        body = [
          ui.pad(zoneMap(ctx, p, r, { drag: true, h: 260 })),
          ui.section('Start from a recent parking spot', `<div class="prv-chips">${places.map((x) => ui.chip(x.name, { kind: 'filter', icon: x.kind === 'home' ? 'garage' : 'pin', selected: x.name === p.name, action: 'place', arg: x.name })).join('')}</div>`, { style: 'caption' }),
          ui.section('Radius', ui.pad(ui.segmented([200, 400, 800].map((m) => ({ id: String(m), label: `${m} m` })), { selected: String(r), action: 'radius' })), { style: 'caption', footer: 'Any part of a shared route inside the circle is removed, on top of the usual 400–800 m trimming.' }),
          ui.pad(ui.textField({ label: 'Name', value: p.name, helper: 'Only you see this name.' })),
          ui.pad(`<div class="e-sheetacts">${ui.button('Save zone', { kind: 'filled', size: 'l', block: true, action: 'save', haptic: 'success' })}</div><p class="e-foot">Zones are saved on this phone and, if backup is on, in your backup. They’re never included in analytics.</p>`),
        ];
      } else if (state === 'withZones') {
        const z = zones[0];
        body = [
          ui.pad(zoneMap(ctx, z, z.radiusM)),
          ui.section('Your zones', ui.list([
            ...zones.map((x) => ui.row({ icon: 'garage', title: x.name, sub: `${x.radiusM} m around the pin · removed from every card`, value: `${x.radiusM} m`, nav: 'S-PRV-02/adding', params: { place: x.name } })),
            ui.row({ icon: 'plus', title: 'Add a zone', sub: 'Work, the gym, family…', nav: 'S-PRV-02/adding', params: { place: 'Work' } }),
          ]), { style: 'caption' }),
          ui.pad(ui.banner({ kind: 'info', icon: 'shield', title: 'Less exposure, not invisibility', text: 'Zones and trimming hide where your drives start and end. A distinctive route shape can still be recognised. The Ring card shares no route at all.' })),
        ];
      } else {
        body = [
          ui.pad(ui.card(ui.empty({ art: art(ctx, 'pin', { icon: 44 }), title: 'No privacy zones yet', text: 'Add a zone around home or work, and share cards will never show that area, on top of the usual trimming.', action: { text: 'Add a zone', nav: 'S-PRV-02/adding', params: { place: 'Home' }, icon: 'plus' } }))),
          ui.section('Recent parking spots', ui.list(places.map((x) => ui.row({ icon: x.kind === 'home' ? 'garage' : 'pin', title: x.name, sub: x.kind === 'home' ? 'Where most drives start' : x.kind === 'work' ? 'Weekday mornings' : 'A few times a week', trailing: `<span class="prv-add">${ui.icon('plus', 16)}Add</span>`, nav: 'S-PRV-02/adding', params: { place: x.name } }))), { style: 'caption', footer: 'Zones stay on this phone and are never included in analytics.' }),
        ];
      }
      return ui.page(`<div class="prv2">${body.map((b) => `<div class="acc-b" data-rise>${b}</div>`).join('')}</div>`, { nav: prvNav(ctx, state === 'adding' ? 'Add a zone' : 'Privacy zones', state === 'adding' ? 'Shared cards skip this area' : null) });
    },
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '.acc-b[data-rise]', { stagger: 30 });
      const z = el.querySelector('.prv-zone');
      if (z && !still(ctx, el)) ctx.motion.animate(z, [{ transform: 'scale(.6)', opacity: 0 }, { transform: 'scale(1)', opacity: 1 }], { preset: 'default' });
      if (z) z.style.transformOrigin = 'center', z.style.transformBox = 'fill-box';
    },
    actions: {
      place: (ctx, n) => { ctx.store.place = n; ctx.update(); return false; },
      radius: (ctx, r) => { ctx.store.r = r; ctx.update(); return false; },
      save: (ctx) => { ctx.toast(`Zone saved · ${ctx.store.r || 400} m`, { icon: 'check' }); ctx.setState('withZones'); return false; },
    },
    spec: {
      purpose: 'Mark home, work or other places as private, so any part of a shared route inside them is removed in addition to the random 400–800 m trimming (F-PRV-07, F-SHR-02). Zones are kept on the device and in Pro backup (Gate 4), never in analytics.',
      layout: [
        'Push, title “Privacy zones” (adding: “Add a zone”, subtitle “Shared cards skip this area”).',
        'Empty: card with a pin illustration, “No privacy zones yet”, one sentence, filled “Add a zone”; then “Recent parking spots” (Home · Work · Gym from places) with an “+ Add” trailing.',
        'With zones: zone map (370 × 230, radius = card): plain-canvas roads, the zone as a dashed Glide circle with a 45° hatch fill, the pin at its centre, a glass chip “Home · 400 m”, “© OpenStreetMap contributors”; “Your zones” list (name, radius) + “Add a zone”; info banner “Less exposure, not invisibility”.',
        'Adding: map 260 tall with “Drag the map to move the pin” glass hint; “Start from a recent parking spot” filter chips; Radius segmented 200 · 400 · 800 m (live circle); Name field; filled “Save zone”; footnote “Zones are saved on this phone and, if backup is on, in your backup…”.',
      ],
      components: ['navBar', 'card', 'empty', 'list', 'row', 'chip', 'segmented', 'textField', 'banner', 'button', 'map'],
      interactions: ['Add / parking spot / zone row → adding (prefilled). Chip → moves the pin to that spot. Radius → resizes the circle (haptic selection). Drag the map → moves the pin (native). Save zone → with zones + toast (haptic success).', 'Swipe a zone row (iOS) / long-press (Android) → Delete zone.'],
      motion: ['The zone circle scales 0.6 → 1 (spring.default) when it appears or the radius changes. Blocks rise 8 px. RM: fade.'],
      a11y: ['Map label: “Map of the Home privacy zone, 400 metres around the pin”. The pin is adjustable with the rotor / custom actions “Move north/south/east/west 50 metres”.', 'Radius is a segmented control with “200 metres” etc. XL: chips wrap; the map keeps 230/260 height.'],
      platformDiffs: { ios: 'MapKit with labels muted, overlay MKCircle with hatch renderer; glass chips; segmented Picker.', android: 'Map SDK with a custom style, Circle overlay; connected button group for radius; OutlinedTextField.' },
      analytics: ['privacy_zone_added {radius} (no location)', 'privacy_zone_removed'],
      copy: { Empty: 'No privacy zones yet. Add a zone around home or work, and share cards will never show that area, on top of the usual trimming.', Radius: 'Any part of a shared route inside the circle is removed, on top of the usual 400–800 m trimming.', Footnote: 'Zones are saved on this phone and, if backup is on, in your backup. They’re never included in analytics.', Banner: 'Less exposure, not invisibility. Zones and trimming hide where your drives start and end. A distinctive route shape can still be recognised. The Ring card shares no route at all.' },
      freePro: 'Free.',
      data: 'Zones from user.privacyZones (Home, 400 m); recent spots from user.places (Home, Work, Gym). The empty state ignores the fixture zone to show the first-run case. Roads drawn from every fixture trip’s track.',
      icons: ['pin', 'garage', 'plus', 'shield'],
      assets: [],
    },
  });

  /* ---------------- S-PRV-03 · Download all my data ---------------- */
  screen('S-PRV-03', {
    name: 'Download all my data',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile', backTo: 'S-PRV-01',
    states: [{ id: 'default', label: 'Def' }, { id: 'generating', label: 'generating (progress)' }, { id: 'done', label: 'done' }, { id: 'Err', label: 'Err' }, { id: 'lowStorage', label: 'low storage' }],
    render(ctx) {
      const { ui, state } = ctx;
      const ds = D.privacy.dataSize, raw = state === 'lowStorage' ? true : ctx.store.raw ?? false;
      const size = raw ? ds.mb : ds.mb - ds.rawMb;
      const fname = 'DrivePerf-data-2026-11-06.zip';
      const contents = ui.list([
        ui.row({ icon: 'drives', title: 'Drives', sub: `${ds.trips} trips as JSON: summaries, events, scores, tips`, value: ds.trips, trailing: '' }),
        ui.row({ icon: 'routes', title: 'Routes, records and cars', sub: `${ROUTES_N} routes · ${CARS_N} cars`, trailing: '' }),
        ui.row({ icon: 'gear', title: 'Settings and consents', sub: 'Including when you gave each choice', trailing: '' }),
        ui.row({ icon: 'info', title: 'README', sub: 'Every field and unit, explained', trailing: '' }),
        ui.row({ icon: 'analysis', title: 'Include raw sensor data', sub: `Portable trip files · +${ds.rawMb} MB`, trailing: 'toggle', on: raw, action: 'raw' }),
      ]);
      let hero, acts, extra = '';
      if (state === 'generating') {
        hero = `<div class="prv3-gen">${ui.loader({ size: 44, label: 'Creating your ZIP' })}<div><b>Creating your ZIP…</b><small>18 of ${ds.trips} drives · about 10 seconds left</small></div></div>${progress(ctx, 18 / ds.trips, { label: `18 of ${ds.trips} drives packed` })}<p class="e-foot">You can leave this screen. We’ll keep going in the background and let you know.</p>`;
        acts = ui.button('Cancel', { kind: 'tonal', size: 'l', block: true, state: 'default' });
        extra = ui.section('Packing', ui.list([
          ui.row({ lead: `<span class="ui-row-ic">${ui.loader({ size: 20, label: 'Packing drives' })}</span>`, title: 'Drives', sub: `18 of ${ds.trips}`, trailing: '' }),
          ui.row({ lead: `<span class="ui-row-ic is-pos">${ui.icon('check', 18)}</span>`, title: 'Routes, records and cars', sub: 'Done', trailing: '' }),
          ui.row({ lead: `<span class="ui-row-ic is-pos">${ui.icon('check', 18)}</span>`, title: 'Settings and consents', sub: 'Done', trailing: '' }),
          ui.row({ lead: `<span class="ui-row-ic is-neutral">${ui.icon('info', 18)}</span>`, title: 'README', sub: 'Waiting', trailing: '' }),
        ]), { style: 'caption' });
      } else if (state === 'done') {
        hero = `<div class="prv3-file">${art(ctx, 'check', { tone: 'positive', icon: 40 })}<div><b>Your data is ready</b><small class="prv3-fn">${fname}</small><small>${size} MB · ${ds.trips} drives · made on this phone</small></div></div>`;
        acts = ctx.ios ? ui.button('Save to Files', { kind: 'filled', size: 'l', block: true, icon: 'export', toast: 'Opens Files to choose a folder' }) + ui.button('Share…', { kind: 'tonal', size: 'l', block: true, icon: 'share', toast: 'Opens the share sheet' })
          : ui.button('Save to…', { kind: 'filled', size: 'l', block: true, icon: 'export', toast: 'Opens the system file picker' }) + ui.button('Share', { kind: 'tonal', size: 'l', block: true, icon: 'share', toast: 'Opens the Sharesheet' });
      } else {
        hero = `<div class="prv3-hero">${art(ctx, state === 'Err' ? 'warn' : 'export', { tone: state === 'Err' ? 'warn' : '', icon: 42 })}<div><h1 class="e-h1">${state === 'Err' ? 'We couldn’t finish the ZIP' : 'Everything you’ve recorded, in open formats'}</h1><p class="e-lead">${state === 'Err' ? 'Nothing was changed or deleted. Try again; it starts from the beginning.' : 'Yours to keep, move or analyse. Free, always.'}</p></div></div>`;
        acts = ui.button(state === 'Err' ? 'Try again' : 'Create ZIP', { kind: 'filled', size: 'l', block: true, icon: state === 'Err' ? 'refresh' : 'export', state: 'generating', disabled: state === 'lowStorage' && raw });
        if (state === 'lowStorage') extra = ui.pad(ui.banner({ kind: 'attention', title: 'Not enough space', text: `The ZIP needs ${ds.mb} MB with raw sensor data and this phone has 120 MB free. Turn off raw data (${ds.mb - ds.rawMb} MB) or free up space.`, action: { text: 'Turn off', action: 'raw' } }));
      }
      const blocks = [ui.pad(state === 'generating' || state === 'done' ? ui.card(hero, { hero: true }) : hero), extra, state === 'generating' ? '' : ui.section('What’s inside', contents, { style: 'caption', footer: `About ${size} MB${raw ? ' with raw sensor data' : ' without raw sensor data'}.` })].filter(Boolean);
      return ui.page(`<div class="prv3">${blocks.map((b) => `<div class="acc-b" data-rise>${b}</div>`).join('')}<div style="height:150px"></div></div>`, { nav: prvNav(ctx, 'Download all my data'), fixed: dock(acts, { cls: 'is-tall' }) });
    },
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '.acc-b[data-rise]', { stagger: 30 });
      const bar = el.querySelector('.e-prog > i');
      if (bar && !still(ctx, el)) ctx.motion.animate(bar, [{ transform: 'scaleX(.4)' }, { transform: 'scaleX(1)' }], { duration: 900, easing: 'cubic-bezier(.16,1,.3,1)' });
      if (ctx.state === 'done') { ctx.ui.anim.pop(el.querySelector('.e-art'), 40); const tm = setTimeout(() => ctx.haptic('success'), 300); return () => clearTimeout(tm); }
    },
    actions: { raw: (ctx) => { if (ctx.state === 'lowStorage') { ctx.store.raw = false; ctx.setState('default'); return false; } ctx.store.raw = !(ctx.store.raw ?? false); ctx.update(); return false; } },
    spec: {
      purpose: 'Give the user all of their data in open formats, free, so it is truly theirs and they can leave (F-PRV-02; Law 25 portability, US access rights).',
      layout: [
        'Push, title “Download all my data”. Hero: export illustration 104 + headline “Everything you’ve recorded, in open formats” + lead “Yours to keep, move or analyse. Free, always.”',
        'What’s inside (list): Drives (30 trips as JSON) · Routes, records and cars · Settings and consents · README · toggle “Include raw sensor data · +151 MB”. Footer “About 35 MB without raw sensor data.” (186 MB with).',
        'generating: hero card with loader + “Creating your ZIP… 18 of 30 drives · about 10 seconds left” + progress bar + “You can leave this screen…”; dock Cancel.',
        'done: hero card — check illustration, “Your data is ready”, file name in mono, size; dock “Save to Files” + “Share…” (iOS) / “Save to…” + “Share” (Android).',
        'Err: warning illustration, “We couldn’t finish the ZIP”, “Nothing was changed or deleted…”; dock “Try again”. low storage: attention banner with “Turn off” (raw data), Create disabled while raw is on.',
      ],
      components: ['navBar', 'list', 'row', 'toggle', 'card', 'loader', 'banner', 'button', 'section'],
      interactions: ['Create ZIP → generating (built on the device; iOS BGContinuedProcessingTask, Android foreground work with a progress notification) → done.', 'Save to Files / Save to… → iOS document picker / Android Storage Access Framework (ACTION_CREATE_DOCUMENT). Share → share sheet.', 'Cancel → stops and deletes the partial file. Turn off (low storage) → raw off, back to default. Signed in: account data (profile, consents, entitlement history) is included.'],
      motion: ['Blocks rise 8 px; progress bar eases forward (900 ms); done: check pops + haptic success. RM: static bar, fade.'],
      a11y: ['Progress is a progressbar “18 of 30 drives packed”, announced politely every 10%. File name read in full.', 'XL: dock buttons stack and grow; rows wrap.'],
      platformDiffs: { ios: 'UIDocumentPickerViewController (export) and ShareLink; BGContinuedProcessingTask where available.', android: 'SAF ACTION_CREATE_DOCUMENT (application/zip); foreground service notification while generating.' },
      analytics: ['data_download_started {raw}', 'data_download_completed {size_bucket}', 'data_download_failed {reason}'],
      copy: { Headline: 'Everything you’ve recorded, in open formats', Lead: 'Yours to keep, move or analyse. Free, always.', Generating: 'Creating your ZIP… 18 of 30 drives · about 10 seconds left. You can leave this screen. We’ll keep going in the background and let you know.', Done: 'Your data is ready · DrivePerf-data-2026-11-06.zip', Err: 'We couldn’t finish the ZIP. Nothing was changed or deleted. Try again; it starts from the beginning.', 'Low storage': 'Not enough space. The ZIP needs 186 MB with raw sensor data and this phone has 120 MB free. Turn off raw data (35 MB) or free up space.' },
      freePro: 'Free for everyone (Gate 1 #6). Never locked.',
      data: 'Sizes from privacy.dataSize: 186 MB total, 151 MB raw → 35 MB without raw (derived). Progress 18/30, “about 10 seconds” and 120 MB free are prototype values.',
      icons: ['export', 'drives', 'routes', 'gear', 'info', 'analysis', 'check', 'warn', 'refresh', 'share'],
      assets: [],
    },
  });

  /* ---------------- S-PRV-04 · Trip export (GPX / CSV) — sheet, large ---------------- */
  function csvPreview(ctx, t, gnssOnly) {
    const rows = t.track.rows.filter((r) => r[1] != null).slice(1, 4);
    const ts = (r) => new Date(new Date(t.start).getTime() + r[0] * 1000).toISOString().replace('.000', '');
    const head = gnssOnly ? 'timestamp_utc,lat,lon,speed_kmh,quality,provenance' : 'timestamp_utc,lat,lon,speed_kmh,long_g,lat_g,quality,provenance';
    const Q = ['observed', 'estimated', 'gap', 'excluded'];
    const lines = rows.map((r) => [ts(r), r[1].toFixed(5), r[2].toFixed(5), Math.round(r[3]), ...(gnssOnly ? [] : [r[4].toFixed(2), r[5].toFixed(2)]), Q[r[7]] === 'estimated' ? 'est' : 'ok', Q[r[7]]].join(','));
    return `<pre class="prv4-csv" aria-label="Preview of the first rows of the CSV file">${ctx.ui.esc([head, ...lines, '…'].join('\n'))}</pre>`;
  }
  screen('S-PRV-04', {
    name: 'Trip export (GPX/CSV)',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'profile', detent: 'large',
    over: (ctx) => ({ id: 'S-TRP-01', params: { trip: ctx.params.trip || 't20261105-1745' } }),
    states: [{ id: 'default', label: 'Single trip' }, { id: 'dateRange', label: 'date range' }, { id: 'Lock', label: 'Lock (Free)' }, { id: 'gnssOnly', label: 'GNSS-only (raw removed)' }],
    stateParams: { default: { trip: 't20261105-1745' }, Lock: { ent: 'free', trip: 't20261105-1745' }, gnssOnly: { trip: 't20261002-0745' } },
    render(ctx) {
      const { ui, data, state, params } = ctx;
      const t = data.trip(params.trip) || data.trip('t20261105-1745');
      const range = state === 'dateRange', gnss = state === 'gnssOnly';
      const fmt = ctx.store.fmt || 'both';
      const head = ui.sheetHeader('Export', { sub: range ? 'Several drives, one ZIP' : `${t.label} · ${t.partOfDay}` });
      if (state === 'Lock' || !ctx.isPro) {
        return `<div class="prv4">${head}${ui.lockPanel(ui.card(`<div class="prv4-lk">${ui.map(t, { w: 330, h: 112, fluid: true, pad: 14, pins: false, attribution: false, others: false, radius: 14 })}<div class="prv4-files"><span>${ui.icon('routes', 16)}${ui.esc(t.id)}.gpx</span><span>${ui.icon('layers', 16)}${ui.esc(t.id)}.csv</span></div>${csvPreview(ctx, t, false)}</div>`), { text: D.entitlement.lockSheets.export, nav: 'S-SUB-03/export', params: { trip: t.id } })}
          <p class="e-foot">Your summary, Story and score stay free forever. To take all of your data with you for free, use <span class="e-link ui-press" ${ui.act({ nav: 'S-PRV-03' })}>Download all my data</span>.</p>
          <div class="e-sheetacts">${ui.button('See plans', { kind: 'filled', size: 'l', block: true, nav: 'S-SUB-03/export', params: { trip: t.id } })}${ui.button('Not now', { kind: 'tonal', size: 'l', block: true, back: true })}</div></div>`;
      }
      const scope = ui.segmented([{ id: 'one', label: 'This drive' }, { id: 'range', label: 'Date range' }], { selected: range ? 'range' : 'one', action: 'scope' });
      const fmts = ui.segmented([{ id: 'gpx', label: 'GPX' }, { id: 'csv', label: 'CSV' }, { id: 'both', label: 'Both' }], { selected: fmt, action: 'fmt' });
      const rangeBlock = range ? ui.list([
        ui.row({ icon: 'clock', title: 'From', value: '9 Oct 2026', trailing: 'chevron', toast: 'Opens a date picker' }),
        ui.row({ icon: 'clock', title: 'To', value: 'Today', trailing: 'chevron', toast: 'Opens a date picker' }),
        ui.row({ icon: 'layers', iconTone: 'neutral', title: '26 drives', sub: 'One file per drive in a single ZIP', trailing: '' }),
      ]) : '';
      const what = fmt === 'gpx' ? 'GPX 1.1 with time, elevation and speed for every point.' : fmt === 'csv' ? 'CSV with timestamp (UTC), position, accuracy, speed, long-G, lat-G, quality and provenance, plus a units header.' : 'A GPX 1.1 track and a CSV of every channel, side by side.';
      return `<div class="prv4">${head}
        <div class="prv4-b">${scope}</div>${rangeBlock ? `<div class="prv4-b">${rangeBlock}</div>` : ''}
        ${gnss ? `<div class="prv4-b">${ui.banner({ kind: 'info', icon: 'layers', title: 'GPS only for this drive', text: 'You removed its raw sensor data, so the export has position and speed but no G columns. The file is flagged gnss_only.' })}</div>` : ''}
        ${ui.section('Format', `<div class="prv4-b">${fmts}<p class="prv4-what">${ui.esc(what)}</p></div>`, { style: 'caption' })}
        ${range ? '' : ui.section('Preview', csvPreview(ctx, t, gnss), { style: 'caption' })}
        <div class="prv4-b">${ui.banner({ kind: 'attention', icon: 'pin', title: 'These files contain your precise location', text: 'Including where drives start and end. Share them only with people and tools you trust.' })}</div>
        <div class="e-sheetacts">${ui.button(range ? 'Export 26 drives' : 'Export', { kind: 'filled', size: 'l', block: true, icon: 'export', toast: ctx.ios ? 'Opens the share sheet with the files' : 'Opens the system file picker' })}</div></div>`;
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '.prv4 > *:not(.ui-sheet-h)', { stagger: 30 }); },
    actions: {
      scope: (ctx, id) => { ctx.setState(id === 'range' ? 'dateRange' : 'default'); return false; },
      fmt: (ctx, id) => { ctx.store.fmt = id; ctx.update(); return false; },
    },
    spec: {
      purpose: 'Export one drive, or a date range, as GPX and/or CSV with every channel and its provenance, for the data tinkerer’s own tools (F-PRV-03, F-DQ-05). Pro. The sheet says plainly that the files contain precise location.',
      layout: [
        'Large-detent sheet over the trip. Header “Export” + sub “Work → Home · Thursday evening” (range: “Several drives, one ZIP”).',
        'Scope segmented control: This drive · Date range. Range: From / To rows (date pickers) + “26 drives · one file per drive in a single ZIP”.',
        'GNSS-only: info banner “GPS only for this drive … flagged gnss_only”.',
        'Format: segmented GPX · CSV · Both + one line describing the file.',
        'Preview: the first CSV rows in mono (12/16, surface-2, radius 14, horizontally scrollable): timestamp_utc, lat, lon, speed_kmh, long_g, lat_g, quality, provenance (GNSS-only drops the G columns).',
        'Attention banner “These files contain your precise location”. Filled “Export” / “Export 26 drives”.',
        'Lock (Free): the user’s own CSV preview blurred in a lock panel + “Export any drive as GPX or CSV with Pro.” + footnote pointing to the free full download + See plans / Not now.',
      ],
      components: ['sheetHeader', 'segmented', 'list', 'row', 'banner', 'lockPanel', 'button', 'sheet', 'section'],
      interactions: ['Scope → switches single / range. Format → GPX / CSV / Both. Export → iOS share sheet / Files; Android SAF (one file, or a ZIP for a range).', 'Lock: See plans → S-SUB-03 export (then S-SUB-04); Not now → back to the trip. “Download all my data” → S-PRV-03.'],
      motion: ['Sections rise 8 px, 30 ms stagger; spring.sheet. RM: fade.'],
      a11y: ['Preview is labelled “Preview of the first rows of the CSV file”; screen readers get the column list rather than raw rows.', 'XL: the preview scrolls horizontally inside its box only; the rest reflows.'],
      platformDiffs: { ios: 'Sheet .large; Transferable files through ShareLink / fileExporter.', android: 'ModalBottomSheet expanded; ACTION_CREATE_DOCUMENT (single file) or a ZIP.' },
      analytics: ['trip_exported {format, batch}', 'lock_viewed {feature: export}'],
      copy: { Location: 'These files contain your precise location. Including where drives start and end. Share them only with people and tools you trust.', GNSS: 'GPS only for this drive. You removed its raw sensor data, so the export has position and speed but no G columns. The file is flagged gnss_only.', Lock: 'Export any drive as GPX or CSV with Pro. (entitlement.lockSheets.export)', 'Lock footnote': 'Your summary, Story and score stay free forever. To take all of your data with you for free, use Download all my data.' },
      freePro: 'Pro (Gate 1 #6). Free → Lock state; the full data download stays free.',
      data: 'Single trip t20261105-1745 · GNSS-only t20261002-0745 (detailed data removed) · range = 26 drives from 9 Oct to today (fixtures.md §1). CSV preview rows are real track rows (timestamp = start + t, UTC). GNSS accuracy isn’t in the fixtures, so the preview omits that column.',
      icons: ['export', 'clock', 'layers', 'pin', 'lock'],
      assets: [],
    },
  });

  /* ---------------- S-PRV-05 · Delete data options ---------------- */
  screen('S-PRV-05', {
    name: 'Delete data options',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile', backTo: 'S-PRV-01',
    states: [{ id: 'default', label: 'Delete all (typed confirm)' }, { id: 'deleteOlderThan', label: 'delete older than…' }, { id: 'done', label: 'done' }],
    render(ctx) {
      const { ui, state } = ctx;
      const ds = D.privacy.dataSize;
      if (state === 'done') {
        return ui.page(`<div class="prv5 is-done"><div class="prv5-done" data-rise>${art(ctx, 'check', { tone: 'positive', icon: 44 })}<h1 class="e-h1">Deleted</h1><p class="e-lead">${ds.trips} drives, ${ROUTES_N} routes with their records, Stories, scores and ${ds.mb} MB of trip files were removed from this phone. This can’t be undone.</p></div>
          <div class="ui-pad" data-rise>${ui.card(checks(ctx, [{ kind: 'ok', text: 'Your cars and settings are kept' }, { kind: 'ok', text: 'Recording keeps working', sub: 'Your next drive starts a fresh baseline' }, { kind: 'dot', text: 'Cloud backup: nothing to remove', sub: 'Backup was off' }]))}</div><div style="height:140px"></div></div>`,
          { nav: prvNav(ctx, 'Delete data'), fixed: dock(ui.button('Done', { kind: 'filled', size: 'l', block: true, nav: 'S-PRV-01' })) });
      }
      const older = state === 'deleteOlderThan';
      const months = ctx.store.m || '3';
      const opts = ui.radios([{ id: 'older', label: 'Delete drives older than…', sub: 'Keep recent drives and your routes' }, { id: 'all', label: 'Delete everything', sub: 'Every drive, route, record and trip file' }], older ? 'older' : 'all', { action: 'mode', cls: 'prv5-r' });
      let detail, act;
      if (older) {
        detail = `${ui.section('Older than', ui.pad(ui.segmented(['3', '6', '12'].map((m) => ({ id: m, label: `${m} months` })), { selected: months, action: 'months' })), { style: 'caption' })}
          <div class="ui-pad">${ui.card(`<div class="prv5-match"><b class="ui-num">0</b><span><b>drives match</b><small>Your oldest drive is from 28 September 2026, so there’s nothing older than ${months} months yet.</small></span></div>`)}</div>
          <p class="e-foot ui-pad">Baselines and route records recompute after deleting. Your cars and settings are kept.</p>`;
        act = ui.button('Delete 0 drives', { kind: 'destructive', size: 'l', block: true, disabled: true });
      } else {
        const typed = ctx.store.typed ?? 'DELETE';
        detail = `${ui.section('This removes from this phone', ui.pad(ui.card(checks(ctx, [
          { kind: 'no', text: `${ds.trips} drives and their Stories`, sub: 'Summaries, scores, events, tips' },
          { kind: 'no', text: `${ROUTES_N} routes and their records` },
          { kind: 'no', text: `${ds.mb} MB of trip files`, sub: 'Including raw sensor data' },
          { kind: 'ok', text: 'Cars and settings stay' },
        ]))), { style: 'caption', footer: 'If cloud backup is on, the cloud copy is removed within 24 hours too.' })}
          <div class="ui-pad prv5-type">${ui.textField({ label: 'Type DELETE to confirm', value: typed, focused: true, helper: 'This can’t be undone.' })}</div>`;
        act = ui.button('Delete everything', { kind: 'destructive', size: 'l', block: true, haptic: 'warning', state: 'done', cls: 'prv5-destr' });
      }
      return ui.page(`<div class="prv5"><div class="acc-b" data-rise>${ui.pad(ui.banner({ kind: 'info', icon: 'export', title: 'Want a copy first?', text: 'Download all your data before you delete it. It’s free.', action: { text: 'Download', nav: 'S-PRV-03' } }))}</div><div class="acc-b" data-rise>${ui.section('What to delete', opts, { style: 'caption' })}</div><div class="acc-b" data-rise>${detail}</div><div style="height:140px"></div></div>`,
        { nav: prvNav(ctx, 'Delete data'), fixed: dock(act) });
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '[data-rise]', { stagger: 30 }); if (ctx.state === 'done') ctx.ui.anim.pop(el.querySelector('.e-art'), 60); },
    actions: {
      mode: (ctx, id) => { ctx.setState(id === 'older' ? 'deleteOlderThan' : 'default'); return false; },
      months: (ctx, m) => { ctx.store.m = m; ctx.update(); return false; },
    },
    spec: {
      purpose: 'Real deletion the user controls (F-PRV-04): delete drives older than 3/6/12 months, or everything with a typed “DELETE” confirmation; a confirmation states what was deleted and that it can’t be undone. Free, never paywalled.',
      layout: [
        'Push, title “Delete data”. Info banner “Want a copy first?” + Download → S-PRV-03.',
        'What to delete: radio list — Delete drives older than… · Delete everything.',
        'Everything: “This removes from this phone” card (✕ 30 drives and their Stories · ✕ 4 routes and records · ✕ 186 MB of trip files · ✓ cars and settings stay) + footer about the cloud copy (24 h); text field “Type DELETE to confirm” (iOS rounded field / Android OutlinedTextField) with helper “This can’t be undone.”; dock destructive “Delete everything” (enabled only when the field reads DELETE).',
        'Older than: segmented 3 · 6 · 12 months + match card (“0 drives match. Your oldest drive is from 28 September 2026…”) + recompute note; dock “Delete 0 drives” disabled.',
        'Done: positive check illustration, “Deleted”, what was removed and “This can’t be undone.”, a checklist of what stays; dock “Done”.',
      ],
      components: ['navBar', 'banner', 'radios', 'segmented', 'card', 'textField', 'button', 'section'],
      interactions: ['Radio → switches mode. Months → recounts matches. Delete everything (typed DELETE) → haptic warning → deletes local data within 30 s, cancels pending uploads first, cloud within 24 h → done. Done → S-PRV-01.', 'The prototype pre-fills DELETE to show the enabled state.'],
      motion: ['Blocks rise 8 px, 30 ms stagger; done: check pops. No celebratory motion. RM: fade.'],
      a11y: ['The destructive button reads “Delete everything. This can’t be undone.” and is disabled (“dimmed”) until the field matches. The field is labelled “Type DELETE to confirm”.', 'XL: radio rows wrap, dock grows.'],
      platformDiffs: { ios: 'Destructive glass button with critical label; the typed field uses .textInputAutocapitalization(.characters).', android: 'Error-container filled button; OutlinedTextField with error colours when the text doesn’t match.' },
      analytics: ['data_deleted {scope: older_3m|older_6m|older_12m|all}'],
      copy: { Banner: 'Want a copy first? Download all your data before you delete it. It’s free.', Confirm: 'Type DELETE to confirm · This can’t be undone.', 'Older none': '0 drives match. Your oldest drive is from 28 September 2026, so there’s nothing older than {n} months yet.', Done: 'Deleted. 30 drives, 4 routes with their records, Stories, scores and 186 MB of trip files were removed from this phone. This can’t be undone.' },
      freePro: 'Free for everyone.',
      data: 'Counts from privacy.dataSize (30 drives, 186 MB) and routes (4). Older-than matches are computed from the trips: the oldest is t20260928-1842 (28 Sep 2026), so 3/6/12 months all match 0 drives today.',
      icons: ['export', 'close', 'check', 'trash'],
      assets: [],
    },
  });

  /* ---------------- S-PRV-06 · Delete account flow (push, 3 steps) ---------------- */
  screen('S-PRV-06', {
    name: 'Delete account flow',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile', backTo: 'S-PRV-01',
    states: [{ id: 'default', label: 'Re-auth' }, { id: 'summaryOfWhatIsDeleted', label: 'summary of what is deleted' }, { id: 'activeSubscriptionNote', label: 'active-subscription note' }, { id: 'done', label: 'done' }, { id: 'Off', label: 'Off (queued)' }],
    render(ctx) {
      const { ui, state } = ctx;
      const prov = providerOf(ctx), ds = D.privacy.dataSize, pro = D.entitlement.states.pro;
      const keep = (ctx.store.keep ?? 'keep') === 'keep';
      let body, act;
      if (state === 'default') {
        body = `<div class="prv6-h">${stepper3(ctx, 1, 3, 'Confirm it’s you')}${art(ctx, 'profile', { tone: 'critical', icon: 42 })}<h1 class="e-h1">Delete your DrivePerf account</h1><p class="e-lead">First, sign in again so we know it’s you. Nothing is deleted until you confirm on the next step.</p></div>`;
        act = `<span class="acc-sso ${ctx.ios ? 'is-apple' : 'is-google'} ui-press" ${ui.act({ action: 'reauth', label: `Continue with ${prov.name}` })}>${ctx.ios ? APPLE : GOOGLE}<span>Continue with ${prov.name}</span></span>${ui.button('Use another way', { kind: 'text', size: 'm', block: true, nav: 'S-ACC-02', params: { reauth: '1' } })}`;
      } else if (state === 'done') {
        body = `<div class="prv6-h">${art(ctx, 'check', { tone: 'positive', icon: 44 })}<h1 class="e-h1">Your account is deleted</h1><p class="e-lead">Your cloud copy, backup files and Ride-along records are removed within 24 hours. ${keep ? `You’re in local mode, and your ${ds.trips} drives are still on this phone.` : 'Your drives were removed from this phone too.'}</p></div>`;
        act = ui.button('Done', { kind: 'filled', size: 'l', block: true, nav: 'S-HOME-01' });
      } else if (state === 'Off') {
        body = `<div class="prv6-h">${art(ctx, 'clock', { icon: 42 })}<h1 class="e-h1">Deletion is queued</h1><p class="e-lead">You’re offline. We’ll delete your account as soon as this phone is back online, and let you know when it’s done.</p></div>
          <div class="ui-pad">${ui.card(`<div class="e-kv"><div><span>Status</span><b>Waiting for a connection</b></div><div><span>Requested</span><b>Today, 7:30 pm</b></div><div><span>Signed in</span><b>Until it’s deleted</b></div></div>`)}</div>`;
        act = ui.button('Done', { kind: 'filled', size: 'l', block: true, nav: 'S-HOME-01' }) + ui.button('Cancel deletion', { kind: 'text', size: 'm', block: true, state: 'summaryOfWhatIsDeleted' });
      } else {
        const sub = state === 'activeSubscriptionNote';
        body = `<div class="prv6-h">${stepper3(ctx, 2, 3, 'Check what’s deleted')}<h1 class="e-h1">This deletes</h1></div>
          ${sub ? ui.pad(ui.banner({ kind: 'attention', icon: 'sparkle', title: 'Your Pro subscription keeps renewing', text: `Deleting your account doesn’t cancel it. Cancel it in ${storeName(ctx)} first: it renews ${dateLong(pro.renews)}.`, action: { text: 'Manage', toast: ctx.ios ? 'Opens App Store subscriptions' : 'Opens Google Play subscriptions' } })) : ''}
          ${ui.pad(ui.card(checks(ctx, [
            { kind: 'no', text: `Your account (signed in with ${prov.name})` },
            { kind: 'no', text: `Your cloud backup · ${ds.trips} drives, ${ds.mb} MB` },
            { kind: 'no', text: 'Backup files and sync history' },
            { kind: 'no', text: 'Ride-along pass records' },
          ])))}
          ${ui.section('Drives on this phone', ui.radios([{ id: 'keep', label: 'Keep them on this phone', sub: 'You’ll carry on in local mode' }, { id: 'remove', label: 'Remove them too', sub: `${ds.trips} drives, ${ds.mb} MB` }], keep ? 'keep' : 'remove', { action: 'keep' }), { style: 'caption', footer: 'Your purchases stay with your Apple or Google account and can be restored later.' })}`;
        act = ui.button('Delete account', { kind: 'destructive', size: 'l', block: true, haptic: 'warning', state: 'done', cls: 'prv5-destr' }) + ui.button('Cancel', { kind: 'text', size: 'm', block: true, nav: 'S-PRV-01' });
      }
      return ui.page(`<div class="prv6 is-${state}"><div data-rise>${body}</div><div style="height:170px"></div></div>`, { nav: prvNav(ctx, 'Delete account'), fixed: dock(act, { cls: 'is-tall' }) });
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '.prv6 [data-rise], .prv6 .ui-card, .prv6 .ui-sec', { stagger: 30 }); ctx.ui.anim.pop(el.querySelector('.e-art'), 40); },
    actions: {
      reauth: (ctx) => { ctx.toast('Signed in again', { icon: 'check' }); ctx.setState('summaryOfWhatIsDeleted'); return false; },
      keep: (ctx, id) => { ctx.store.keep = id; ctx.update(); return false; },
    },
    spec: {
      purpose: 'Delete the account and all cloud data from inside the app (F-PRV-05; Apple 5.1.1(v), Google Play account deletion): re-authenticate, see exactly what is deleted, choose what happens to local drives, be told that a store subscription must be cancelled in the store, and get a confirmation. Offline requests are queued.',
      layout: [
        'Push flow, title “Delete account”, step indicator (3 segments + “Step n of 3 · …”).',
        'Step 1 Re-auth: critical-tinted profile illustration, “Delete your DrivePerf account”, lead; dock: provider button (Sign in with Apple style on iOS / Google on Android, 52/56) + “Use another way” → S-ACC-02 re-auth.',
        'Step 2 Summary: “This deletes” card (✕ account · ✕ cloud backup 30 drives, 186 MB · ✕ backup files and sync history · ✕ Ride-along pass records); “Drives on this phone” radios (Keep them / Remove them too); footer about purchases; dock destructive “Delete account” + “Cancel”.',
        'active-subscription note: attention banner above the card “Your Pro subscription keeps renewing … renews 13 October 2027” + Manage.',
        'Done: positive check, “Your account is deleted”, what happens within 24 h, local-mode line; dock Done → S-HOME-01. Off (queued): clock illustration, “Deletion is queued”, facts card; dock Done + “Cancel deletion”.',
      ],
      components: ['navBar', 'banner', 'card', 'radios', 'button', 'section'],
      interactions: ['Continue with Apple/Google → re-auth (prototype: toast) → summary. Use another way → S-ACC-02 (reauth = 1) → back to the summary.', 'Manage → StoreKit showManageSubscriptions / Play subscriptions deep link.', 'Delete account (haptic warning) → Auth user, Firestore docs and Storage files deleted within 24 h (Cloud Functions cascade, incl. RevenueCat customer and Crashlytics by installation ID where possible) → done (local mode). Offline → Off (queued, retried).'],
      motion: ['Blocks rise 8 px, 30 ms stagger; illustration pops. No celebration on done. RM: fade.'],
      a11y: ['Step indicator reads “Step 2 of 3: Check what’s deleted”. Destructive button: “Delete account. This deletes your account and cloud data.”', 'XL: dock buttons stack; the summary card wraps.'],
      platformDiffs: { ios: 'Sign in with Apple re-auth (ASAuthorization with the existing credential); destructive role styling.', android: 'Credential Manager re-auth; error-container destructive button; predictive back returns one step.' },
      analytics: ['account_deletion_requested', 'account_deleted (anonymous count)', 'account_deletion_queued'],
      copy: {
        'Step 1': 'Delete your DrivePerf account. First, sign in again so we know it’s you. Nothing is deleted until you confirm on the next step.',
        Deletes: 'Your account (signed in with Apple) · Your cloud backup · 30 drives, 186 MB · Backup files and sync history · Ride-along pass records',
        Subscription: 'Your Pro subscription keeps renewing. Deleting your account doesn’t cancel it. Cancel it in App Store first: it renews 13 October 2027.',
        Done: 'Your account is deleted. Your cloud copy, backup files and Ride-along records are removed within 24 hours. You’re in local mode, and your 30 drives are still on this phone.',
        Queued: 'Deletion is queued. You’re offline. We’ll delete your account as soon as this phone is back online, and let you know when it’s done.',
      },
      freePro: 'Free. The subscription note appears when a store subscription is active (pro renews 2027-10-13).',
      data: 'Signed-in account is a prototype value (Apple on iOS, Google on Android). Sizes from privacy.dataSize; renewal from entitlement.states.pro.renews.',
      icons: ['profile', 'check', 'clock', 'sparkle', 'close'],
      assets: [],
    },
  });

  /* ---------------- S-PRV-07 · Analytics and diagnostics ---------------- */
  screen('S-PRV-07', {
    name: 'Analytics and diagnostics',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile', backTo: 'S-PRV-01',
    states: [{ id: 'default', label: 'Def (your choices)' }, { id: 'bothOff', label: 'both off' }, { id: 'resetIDDone', label: 'reset ID done' }],
    render(ctx) {
      const { ui, state } = ctx;
      const usage = ctx.store.usage ?? (state === 'bothOff' ? false : D.user.consent.usageStats);
      const crash = ctx.store.crash ?? (state === 'bothOff' ? false : D.user.consent.crashReports);
      const card = (k, title, on, text, eg) => ui.card(`<div class="prv7-c"><div class="prv7-h"><b>${title}</b>${ui.toggle(on, { action: k, label: title })}</div><p>${ui.esc(text)}</p><span class="prv7-eg">${ui.esc(eg)}</span><span class="prv7-st ${on ? 'is-on' : ''}">${ui.icon(on ? 'check' : 'close', 14)}${on ? 'On' : 'Off'}</span></div>`);
      const blocks = [
        state === 'resetIDDone' ? ui.pad(ui.banner({ kind: 'positive', title: 'Analytics ID reset', text: 'The old ID is deleted. A new random one is used from now on, with no link to the old one.' })) : '',
        state === 'bothOff' ? ui.pad(ui.banner({ kind: 'info', icon: 'shield', title: 'Nothing is being sent', text: 'With both off, DrivePerf makes no analytics or crash-report requests at all.' })) : '',
        ui.pad(card('usage', 'Share usage statistics', usage, 'Which screens you open and which features you use. It helps us decide what to improve.', 'For example: “Story opened”, “Replay played”.')),
        ui.pad(card('crash', 'Send crash reports', crash, 'Technical details when the app crashes or freezes, so we can fix it fast.', 'For example: the app version, phone model and the code that failed.')),
        ui.section('Never included', ui.pad(ui.card(checks(ctx, ['Locations, addresses or coordinates', 'Route or car names', 'Speeds or G values', 'Anything you type', 'An advertising ID'].map((t) => ({ kind: 'no', text: t }))))), { style: 'caption', footer: 'Changes take effect straight away. If usage statistics are off, nothing is sent, ever.' }),
        ui.section('', ui.list([ui.row({ icon: 'refresh', title: 'Reset analytics ID', sub: state === 'resetIDDone' ? 'Reset just now' : 'Deletes the random ID linked to your usage', action: 'reset', trailing: state === 'resetIDDone' ? ui.icon('check', 18) : '' })]), { style: 'caption' }),
      ].filter(Boolean);
      return ui.page(`<div class="prv7">${blocks.map((b) => `<div class="acc-b" data-rise>${b}</div>`).join('')}</div>`, { nav: prvNav(ctx, 'Analytics and diagnostics') });
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '.acc-b[data-rise]', { stagger: 30 }); },
    actions: {
      usage: (ctx) => { ctx.store.usage = !(ctx.store.usage ?? (ctx.state === 'bothOff' ? false : D.user.consent.usageStats)); ctx.update(); return false; },
      crash: (ctx) => { ctx.store.crash = !(ctx.store.crash ?? (ctx.state === 'bothOff' ? false : D.user.consent.crashReports)); ctx.update(); return false; },
      reset: (ctx) => { ctx.haptic('success'); ctx.setState('resetIDDone'); return false; },
    },
    spec: {
      purpose: 'Change usage-statistics and crash-report choices any time, with what each sends and what is never included, and reset the pseudonymous analytics ID (F-PRV-06, F-ONB-08, F-ANA-01/03).',
      layout: [
        'Push, title “Analytics and diagnostics”.',
        'Two choice cards (radius = card): title + switch; one sentence of what it does; an example line (caption, text-3); a status pill “✓ On” / “✕ Off” (glyph + text).',
        'Never included (card): ✕ Locations, addresses or coordinates · ✕ Route or car names · ✕ Speeds or G values · ✕ Anything you type · ✕ An advertising ID. Footer “Changes take effect straight away…”.',
        'Reset analytics ID row (refresh glyph). reset ID done: positive banner at the top + a check on the row. both off: info banner “Nothing is being sent”.',
      ],
      components: ['navBar', 'card', 'toggle', 'banner', 'list', 'row', 'section'],
      interactions: ['Switches → setAnalyticsCollectionEnabled / setCrashlyticsCollectionEnabled within the session (haptic toggleOn/Off). Reset analytics ID → deletes the ID (resetAnalyticsData) → reset ID done (haptic success).'],
      motion: ['Cards rise 8 px; switch thumbs use spring.fast. RM: fade.'],
      a11y: ['Each card’s switch is labelled by its title, with the sentence as the hint. The status pill is hidden from screen readers (the switch state is read).', 'XL: switch drops under the title when it doesn’t fit.'],
      platformDiffs: { ios: 'Toggle with Glide tint; inset cards.', android: 'M3 Switch with check/close thumb icons; tonal cards.' },
      analytics: ['analytics_consent_set {usage, crash} (only when usage is on)'],
      copy: { Usage: 'Share usage statistics. Which screens you open and which features you use. It helps us decide what to improve.', Crash: 'Send crash reports. Technical details when the app crashes or freezes, so we can fix it fast.', Never: 'Locations, addresses or coordinates · Route or car names · Speeds or G values · Anything you type · An advertising ID', Reset: 'Analytics ID reset. The old ID is deleted. A new random one is used from now on, with no link to the old one.', 'Both off': 'Nothing is being sent. With both off, DrivePerf makes no analytics or crash-report requests at all.' },
      freePro: 'Free.',
      data: 'Defaults from user.consent (usage off, crash reports on), as chosen in S-ONB-13.',
      icons: ['refresh', 'check', 'close', 'shield'],
      assets: [],
    },
  });

  /* ==========================================================================================================
     SUBSCRIPTION · prices exactly as PRD §3.13 (from the store in the app; never hard-coded there), no weekly or lifetime
     plan, no countdowns, no strikethrough, close visible immediately, equal weight for Monthly/Annual and for Free.
     ========================================================================================================== */
  const PRO4 = [['replay', 0], ['routes', 1], ['insights', 2], ['cloud', 4]];
  const valueList = (ctx) => `<ul class="sub-vals">${PRO4.map(([ic, i]) => `<li><span>${ctx.ui.icon(ic, 18)}</span>${ctx.ui.esc(D.entitlement.proFeatures[i])}</li>`).join('')}</ul>`;
  const money = (s) => { const m = String(s).match(/^([A-Z]{2}\$)(.*)$/); return m ? `<span class="sub-cur">${m[1]}</span><b class="ui-num">${m[2]}</b>` : `<b class="ui-num">${s}</b>`; };
  const planInfo = (region, founding, plan) => {
    const p = PRICES[region];
    if (plan === 'monthly') return { name: 'Monthly', btn: `Start Pro: ${p.monthly}/month`, priceLine: `${p.monthly}/month`, renewLine: 'Renews monthly until cancelled' };
    return founding ? { name: 'Annual · founding', btn: `Start Pro: ${p.founding} first year`, priceLine: `${p.founding} for the first year, then ${p.annual}/year`, renewLine: 'Renews 6 November 2027, then yearly' }
      : { name: 'Annual', btn: `Start Pro: ${p.annual}/year`, priceLine: `${p.annual}/year`, renewLine: 'Renews 6 November 2027, then yearly' };
  };
  /** Plan cards: Annual + Monthly, identical size and type; only the selection ring differs (components.md §15.1). */
  function planCards(ctx, o) {
    const { ui } = ctx, p = PRICES[o.region];
    if (o.loading) return `<div class="sub-plans" aria-busy="true">${[0, 1].map(() => `<div class="sub-plan is-skel"><span class="ui-skel" style="width:46%"></span><span class="ui-skel" style="width:70%;height:22px;margin-top:12px"></span><span class="ui-skel" style="width:60%;margin-top:10px"></span></div>`).join('')}</div>`;
    const card = (id, label, price, per, sub, badge) => `<div class="sub-plan ${o.sel === id ? 'is-on' : ''} ${o.off ? 'e-dis' : ''} ui-press" role="radio" aria-checked="${o.sel === id}" tabindex="0" ${ui.act({ action: 'plan', arg: id, haptic: 'selection', noRole: true, label: `${label}, ${price} ${per}. ${sub}${badge ? '. ' + badge : ''}` })}>
        <span class="sub-plan-h"><b>${label}</b><i class="sub-radio" aria-hidden="true"></i></span>
        <span class="sub-plan-p">${money(price)}<small>${per}</small></span>
        <span class="sub-plan-s">${ui.esc(sub)}</span>${badge ? `<span class="sub-found">${ui.esc(badge)}</span>` : '<span class="sub-found is-empty" aria-hidden="true"></span>'}</div>`;
    return `<div class="sub-plans" role="radiogroup" aria-label="Choose a plan">${o.founding ? card('annual', 'Annual', p.founding, 'first year', `Then ${p.annual}/year`, 'Founding price') : card('annual', 'Annual', p.annual, '/year', `${p.annualPerMonth}/month, billed yearly`)}${card('monthly', 'Monthly', p.monthly, '/month', 'Billed monthly, cancel any time')}</div>`;
  }
  function legal(ctx, region, founding, plan) {
    const p = PRICES[region];
    const price = plan === 'monthly' ? `${p.monthly}/month` : founding ? `${p.annual}/year after your first year at ${p.founding}` : `${p.annual}/year`;
    const where = ctx.ios ? 'Payment is charged to your Apple Account. Cancel any time in Settings → your name → Subscriptions.' : 'Payment is charged to your Google Play account. Cancel any time in Google Play → Payments and subscriptions.';
    return `<p class="sub-legal">Pro renews automatically at ${ctx.ui.esc(price)} unless you cancel at least 24 hours before the renewal date. ${where}</p>
      <div class="sub-links"><span class="ui-press" ${ctx.ui.act({ toast: 'Opens driveperf.com/terms' })}>Terms</span><i>·</i><span class="ui-press" ${ctx.ui.act({ toast: 'Opens driveperf.com/privacy' })}>Privacy</span><i>·</i><span class="ui-press" ${ctx.ui.act({ action: 'restore', label: 'Restore purchases' })}>Restore purchases</span></div>`;
  }
  const subActions = {
    plan: (ctx, id) => { ctx.store.plan = id; ctx.update(); return false; },
    buy: (ctx) => { ctx.store.buy = true; ctx.update(); return false; },
    bought: (ctx) => { ctx.store.buy = false; ctx.haptic('success'); ctx.nav('S-SUB-05', { ent: 'pro' }, { replace: true }); return false; },
    restore: (ctx) => { ctx.toast(`Checking ${storeName(ctx)}… no other purchases found for this account.`, { icon: 'refresh', duration: 3200 }); return false; },
  };

  /* ---------------- S-SUB-01 · Pro Preview status sheet ---------------- */
  screen('S-SUB-01', {
    name: 'Pro Preview status sheet',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'profile', detent: 'auto', over: 'S-HOME-01',
    states: [{ id: 'default', label: 'Day 1' }, { id: 'day13', label: 'day 13 (trial-ending notice)' }, { id: 'rideAlong30DayVariant', label: 'Ride-along 30-day variant' }],
    stateParams: { default: { ent: 'preview' }, day13: { ent: 'previewEnding' }, rideAlong30DayVariant: { ent: 'rideAlong' } },
    render(ctx) {
      const { ui, state } = ctx;
      const ride = state === 'rideAlong30DayVariant';
      const es = ride ? D.entitlement.states.rideAlong : state === 'day13' ? D.entitlement.states.previewEnding : { ...D.entitlement.states.preview, day: 1 };
      const left = es.of - es.day + 1;
      const meter = `<div class="sub1-m"><div class="sub1-m-t"><b><span class="ui-num">${es.day}</span> of ${es.of}</b><span>${left} day${left === 1 ? '' : 's'} left, including today</span></div>${progress(ctx, es.day / es.of, { label: `Day ${es.day} of ${es.of}` })}</div>`;
      return `<div class="sub1">${ui.sheetHeader(ride ? 'Ride-along pass' : 'Pro Preview', { sub: ride ? `From ${es.from}. 30 days of Pro, free.` : 'Everything in Pro, free for 14 days. No card.' })}
        <div data-rise>${ui.card(meter)}</div>
        ${state === 'day13' ? `<div data-rise>${ui.banner({ kind: 'info', icon: 'clock', title: D.entitlement.states.previewEnding.notice.split('. ')[0] + '.', text: 'Your trips stay yours either way. On day 15 you’ll see what Pro showed you, and choose.' })}</div>` : ''}
        ${ui.section('Included', `<div data-rise>${valueList(ctx)}</div>`, { style: 'caption' })}
        ${ui.section('Afterwards', `<div data-rise>${checks(ctx, [{ kind: 'ok', text: 'Drives, Stories, scores and summaries stay free' }, { kind: 'ok', text: 'Replay and Analysis for the last 30 days' }, { kind: 'lock', text: 'Older Replay, extra routes and cars pause', sub: 'Locked, not lost. Nothing is deleted.' }])}</div>`, { style: 'caption' })}
        <div class="e-sheetacts">${ui.button('See plans', { kind: 'tonal', size: 'l', block: true, nav: 'S-SUB-04', params: { from: 'preview', region: 'CA', founding: '1' } })}${ui.button('Done', { kind: 'text', size: 'm', block: true, back: true })}</div></div>`;
    },
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '[data-rise]', { stagger: 35 });
      const bar = el.querySelector('.e-prog > i');
      if (bar && !still(ctx, el)) ctx.motion.animate(bar, [{ transform: 'scaleX(0)' }, { transform: 'scaleX(1)' }], { duration: 700, easing: 'cubic-bezier(.16,1,.3,1)', delay: 120 });
    },
    spec: {
      purpose: 'Show where the user is in the 14-day no-card Pro Preview (or the 30-day Ride-along pass) and what is included, from the badge on Home and Profile (F-SUB-02.2). On day 13 it carries the one allowed trial-ending notice. No prices here.',
      layout: [
        'Sheet fitted to content (iOS partial detent / Android ModalBottomSheet). Header “Pro Preview” + “Everything in Pro, free for 14 days. No card.” (Ride-along: “Ride-along pass” + “From Priya. 30 days of Pro, free.”).',
        'Day card: “1 of 14” (DP Numerals) + “14 days left, including today” + a 6/8 px progress bar. This is the Preview’s length, not a streak; it never counts consecutive driving days.',
        'day 13: info banner “Your Pro Preview ends in 2 days.” + “Your trips stay yours either way. On day 15 you’ll see what Pro showed you, and choose.”',
        'Included: 4 Pro features with glyphs (replay, routes, insights, cloud). Afterwards: ✓ drives, Stories, scores, summaries stay free · ✓ 30-day Replay and Analysis · lock “Older Replay, extra routes and cars pause — Locked, not lost.”',
        'Buttons: tonal “See plans” (no price on this sheet) + text “Done”.',
      ],
      components: ['sheetHeader', 'card', 'banner', 'section', 'button', 'sheet'],
      interactions: ['See plans → S-SUB-04. Done / drag / scrim → back. Gate: never shown during a trip (badge unreachable on S-DRV-01).', 'The day-13 notification (n_preview, “Your Pro Preview ends in 2 days.”) opens this sheet in the day 13 state.'],
      motion: ['Blocks rise 8 px, 35 ms stagger; the day bar grows from 0 (700 ms). RM: static.'],
      a11y: ['Day card: “Day 1 of 14. 14 days left, including today.” (progressbar). XL: opens at the large detent.'],
      platformDiffs: { ios: 'Glass sheet, partial detent, X in the header.', android: 'ModalBottomSheet, M3 LinearProgressIndicator (8 dp, rounded).' },
      analytics: ['preview_status_viewed {day}', 'paywall_viewed {source: preview_sheet} (on See plans)'],
      copy: { Preview: 'Pro Preview · Everything in Pro, free for 14 days. No card.', 'Ride-along': 'Ride-along pass · From {Priya}. 30 days of Pro, free.', 'Day 13 notice': 'Your Pro Preview ends in 2 days. Your trips stay yours either way. On day 15 you’ll see what Pro showed you, and choose.', Afterwards: 'Drives, Stories, scores and summaries stay free · Replay and Analysis for the last 30 days · Older Replay, extra routes and cars pause. Locked, not lost. Nothing is deleted.' },
      freePro: 'Preview / Ride-along only. No price or upgrade copy (F-SUB-08.1/.4); the notice is the one allowed day-13 message.',
      data: 'entitlement.states.preview (day forced to 1 for Day 1), previewEnding (day 13, notice), rideAlong (day 4 of 30, from Priya). Included = proFeatures[0, 1, 2, 4].',
      icons: ['replay', 'routes', 'insights', 'cloud', 'clock', 'check', 'lock'],
      assets: [],
    },
  });

  /* ---------------- S-SUB-02 · Day-15 "Your first two weeks, decoded." (full cover) ---------------- */
  screen('S-SUB-02', {
    name: 'Day-15 “Your first two weeks, decoded”',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'full', tab: 'profile', statusBar: 'light',
    states: [{ id: 'default', label: 'Def (with founding price)' }, { id: 'notFoundingEligible', label: 'not founding-eligible' }],
    stateParams: { default: { ent: 'free' }, notFoundingEligible: { ent: 'free' } },
    render(ctx) {
      const { ui, data, state } = ctx;
      const R = D.previewRecap, region = regionOf(ctx), founding = state !== 'notFoundingEligible';
      const best = data.trip(R.smoothest.tripId), plan = ctx.store.plan || 'annual';
      const closing = founding ? R.closingLine[region] : R.closingLine[region].replace(/ Your first year is .*$/, '');
      const pi = planInfo(region, founding, plan);
      const frames = `<div class="sub2-frames"><div class="sub2-seg" aria-hidden="true"><i></i><i></i><i></i></div>
        <div class="sub2-f is-a" data-frame><span class="sub2-k">Drives decoded</span><b class="ui-num">${R.drives}</b><small>${ui.num(Math.round(R.km), 'km')} in two weeks</small></div>
        <div class="sub2-f is-c" data-frame><span class="sub2-k">Pro found</span><b class="ui-num">${R.routesFound}</b><small>routes · ${R.comparisons} comparisons</small></div>
        <div class="sub2-f is-b" data-frame>${ui.ring(best.smoothness.score, 84, { stroke: 8, label: false, shape: false, aria: `Smoothest drive, Smoothness ${best.smoothness.score}` })}<div><span class="sub2-k">Smoothest drive</span><b class="sub2-t">${ui.esc(best.label)}</b><small>${ui.esc(best.partOfDay)} · ${ui.esc(best.smoothness.compareText)}</small></div></div></div>`;
      const body = `<div class="sub2" data-theme="dark">
        <div class="sub2-top"><span class="sub2-kick">${ctx.icons.loop({ size: 18, color: 'var(--dp-accent)', dot: 'var(--dp-text)' })}Pro Preview · ${U.fmtDate(R.period.from)} – ${U.fmtDate(R.period.to)}</span><h1>${ui.esc(R.title)}</h1></div>
        ${frames}
        <p class="sub2-close">${ui.esc(closing)}</p>
        <div class="sub2-plans" data-plans>${planCards(ctx, { region, founding, sel: plan })}</div>
        <div class="sub2-acts" data-plans>${equalPair(ctx, { text: pi.btn, action: 'buy' }, { text: 'Continue with Free', action: 'free' })}</div>
        <div class="sub2-legal" data-plans>${legal(ctx, region, founding, plan)}<span class="sub2-more ui-press" ${ui.act({ nav: 'S-SUB-04', params: { region, founding: founding ? '1' : '' } })}>Compare plans in detail</span></div>
        <div style="height:24px"></div></div>`;
      return ui.page(body, { bg: 'var(--dp-ref-ink-50)', cls: 'sub2-page', fixed: `<div class="e-fx-close" data-theme="dark">${ui.iconButton('close', { action: 'free', label: 'Close. Continue with Free' })}</div>`,
        overlay: ctx.store.buy ? `<div data-theme="dark">${osStore(ctx, { planName: pi.name, priceLine: pi.priceLine, renewLine: pi.renewLine })}</div>` : '' });
    },
    mount(el, ctx) {
      if (still(ctx, el)) return;
      const M = ctx.motion;
      el.querySelectorAll('[data-frame]').forEach((f, i) => M.animate(f, [{ opacity: 0, transform: 'translateY(12px)' }, { opacity: 1, transform: 'translateY(0)' }], { preset: 'default', delay: 200 + i * 420 }));
      el.querySelectorAll('.sub2-seg i').forEach((s, i) => s.animate([{ transform: 'scaleX(0)' }, { transform: 'scaleX(1)' }], { duration: 420, delay: 200 + i * 420, fill: 'both', easing: 'linear' }));
      ctx.ui.anim.rings(el, { haptics: false });
      const plans = [...el.querySelectorAll('.sub-plan')], acts = el.querySelector('.sub2-acts'), lg = el.querySelector('.sub2-legal');
      plans.forEach((p, i) => M.animate(p, [{ opacity: 0, transform: 'translateY(10px)' }, { opacity: 1, transform: 'translateY(0)' }], { preset: 'default', delay: 1500 + i * 70 }));
      // equal weight: both buttons enter together with identical motion (F-SUB-03.2)
      if (acts) M.animate(acts, [{ opacity: 0, transform: 'translateY(10px)' }, { opacity: 1, transform: 'translateY(0)' }], { preset: 'default', delay: 1640 });
      if (lg) M.animate(lg, [{ opacity: 0 }, { opacity: 1 }], { preset: 'effects', delay: 1700 });
    },
    actions: { ...subActions, free: (ctx) => { ctx.nav('S-SUB-07', { ent: 'free' }, { replace: true }); return false; } },
    spec: {
      purpose: 'The day-15 moment (F-SUB-03/05/08): a short recap of what Pro showed the user in their own first two weeks, then a fair choice between Pro and Free with equal weight. Shown once, on the first parked foreground open after day 14 with ≥ 3 driver trips; never during a trip, never after a worse-than-usual Story.',
      layout: [
        'Full-screen cover, always the dark night art (status bar light). Floating close (glass 44 / tonal 48) top-trailing, visible immediately (= Continue with Free).',
        'Kicker: Loop + “Pro Preview · 28 Sep – 12 Oct”. Headline “Your first two weeks, decoded.” (34/40 display bold).',
        'Recap frames (Story-player mini frames with 3 segment bars): “Drives decoded 8 · 316 km in two weeks” and “Pro found 2 routes · 11 comparisons” side by side (radius 22, surface over night), then “Smoothest drive” full width with an 84 ring (84), “Belfountain → Home”, “Saturday midday · +6 vs your usual mixed drives”.',
        'Closing line (body, text): previewRecap.closingLine[CA|US]; not founding-eligible drops the founding sentence.',
        'Plan cards side by side, identical size and type (Annual pre-selected with a 2 px Glide ring / Android primaryContainer + shape morph): Annual “CA$39.99 first year · Then CA$49.99/year · FOUNDING PRICE” (non-founding: “CA$49.99 /year · CA$4.17/month, billed yearly”) · Monthly “CA$8.99 /month · Billed monthly, cancel any time”.',
        'Equal-weight pair, stacked, same height (52/56) and type: “Start Pro: CA$39.99 first year” (filled) and “Continue with Free” (glass on iOS / secondaryContainer on Android; not greyed, same weight).',
        'Legal: auto-renewal terms, where payment is charged, how to cancel; Terms · Privacy · Restore purchases; “Compare plans in detail” → S-SUB-04.',
      ],
      components: ['page', 'ring', 'button', 'iconButton'],
      interactions: [
        'Plan card → selects (selection ring slides, haptic selection); the primary label follows the plan (“Start Pro: CA$8.99/month”).',
        'Start Pro → StoreKit / Play Billing sheet (mocked; tap = purchase) → S-SUB-05 (haptic success). Purchase failure → toast + error haptic, the cover stays.',
        'Continue with Free / close → S-SUB-07 (and S-SUB-02 is never shown again automatically, F-SUB-03.4). Restore purchases → store restore.',
        'Gate: the D1 gate holds S-SUB-02 while a trip is active or the car is moving (S-DRV-05); it then waits for the next parked open (F-SUB-08.2).',
      ],
      motion: [
        'Present: slide up with spring.sheet (paywallPresent ≈ 520 ms).',
        'Recap: three mini frames rise 12 px one after another (420 ms each; segment bars fill linearly); the smoothest-drive ring sweeps (no haptics on a paywall).',
        'Then the plans rise (stagger 70 ms, spring.default) with Annual’s selection ring already drawn; the Start Pro and Continue with Free buttons enter together with identical motion. Never a pulse, shimmer, countdown or delayed close.',
        'RM: everything is shown at once with a 220 ms cross-fade; the ring is static.',
      ],
      a11y: [
        'The frames are read in order: “Drives decoded, 8. 316 kilometres in two weeks.” · “Pro found 2 routes, 11 comparisons.” · “Smoothest drive, Smoothness 84, Belfountain to Home, Saturday midday, 6 above your usual mixed drives.”',
        'Plan cards are a radio group (“Annual, CA$39.99 first year. Then CA$49.99/year. Founding price, selected”). Close is labelled “Close. Continue with Free”.',
        'XL: plan cards stack (Annual first), legal text stays fully visible and scrolls, never shrinks.',
      ],
      platformDiffs: { ios: 'fullScreenCover; glass plan cards with a Glide ring; Continue with Free uses .glass at the same size as .glassProminent; StoreKit purchase sheet.', android: 'Full-screen dialog; plan cards surfaceContainerHigh, the selected one primaryContainer morphing to extraLargeIncreased; Continue with Free is a FilledTonalButton at the same size; Play Billing sheet.' },
      analytics: ['day15_paywall_viewed', 'plan_selected {plan}', 'day15_paywall_choice {purchase|free}', 'purchase_started', 'purchase_completed {plan, offer}'],
      copy: {
        Title: 'Your first two weeks, decoded.',
        'Closing (CA, founding)': D.previewRecap.closingLine.CA,
        'Closing (US, founding)': D.previewRecap.closingLine.US,
        'Closing (not eligible)': 'Pro showed you 2 routes and 11 comparisons. Keep it for CA$49.99/yr.',
        Buttons: 'Start Pro: CA$39.99 first year · Continue with Free',
        Legal: 'Pro renews automatically at CA$49.99/year after your first year at CA$39.99 unless you cancel at least 24 hours before the renewal date. Payment is charged to your Apple Account. Cancel any time in Settings → your name → Subscriptions.',
      },
      freePro: 'Shown to Preview users at the end of day 14 (≥ 3 trips). Founding price only when eligible (F-SUB-05); otherwise the standard annual. < 3 trips → S-SUB-07 low-usage variant instead.',
      data: 'previewRecap: 8 drives, 316.2 km, smoothest t20261010-1340 (84, +6), 2 routes, 11 comparisons, period 28 Sep–12 Oct, closing lines CA/US. Prices from entitlement.prices (CA storefront for Marcus; region=US shows US$). Renewal date in the store mock = today + 1 year.',
      icons: ['close', 'refresh'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ---------------- S-SUB-03 · Contextual lock sheet (the user's own data, blurred) ---------------- */
  const LOCK = {
    default: { key: 'compare', title: 'Ghost comparison', over: { id: 'S-RTE-02', params: { route: 'r_commute', ent: 'free' } } },
    replayAnalysis30Days: { key: 'oldAnalysis', title: 'Replay and Analysis', over: { id: 'S-TRP-01', state: 'Lock', params: { trip: 't20261002-0745', ent: 'free' } } },
    s3rdRoute: { key: 'thirdRoute', title: 'Your third route', over: { id: 'S-RTE-01', params: { ent: 'free' } } },
    s2ndCar: { key: 'secondCar', title: 'Your second car', over: { id: 'S-GAR-01', params: { ent: 'free' } } },
    export: { key: 'export', title: 'GPX and CSV export', over: { id: 'S-TRP-01', params: { trip: 't20261105-1745', ent: 'free' } } },
    trends: { key: 'trends', title: 'Month and year trends', over: { id: 'S-STA-01', params: { ent: 'free' } } },
    backup: { key: 'backup', title: 'Cloud backup', over: { id: 'S-ACC-04', state: 'Lock', params: { ent: 'free' } } },
    noDataYet: { key: 'trends', title: 'Month and year trends', over: { id: 'S-STA-01', params: { ent: 'free' } } },
  };
  function lockPreview(ctx, state) {
    const { ui, data } = ctx;
    if (state === 'replayAnalysis30Days') { const t = data.trip(ctx.params.trip) || data.trip('t20261002-0745'); return `<div class="sub3-p">${ui.map(t, { w: 330, h: 120, fluid: true, pad: 14, others: false, stops: false, attribution: false, radius: 14 })}${ui.timeline(t, 330, { cursor: 0.42 })}</div>`; }
    if (state === 's3rdRoute') { const r = data.route('r_gym'); return `<div class="sub3-p sub3-route">${ui.glyph(r.glyph, 64)}<div><b>${ui.esc(r.name)}</b><small>${r.runCount} drives · record ${r.record.score}</small><span class="sub3-dots">${r.runs.map((x) => `<i style="--s:${x.score}"></i>`).join('')}</span></div>${ui.scoreBadge(data.trip(r.record.tripId))}</div>`; }
    if (state === 's2ndCar') {
      const v = data.vehicle('ioniq'), ts = D.trips.filter((t) => t.vehicleId === 'ioniq' && t.smoothness), avg = Math.round(ts.reduce((a, t) => a + t.smoothness.score, 0) / ts.length);
      return `<div class="sub3-p sub3-car" style="--car:var(--dp-car-${v.colour})"><span class="sub3-sil">${silhouette(v, 120)}</span><div><b>${ui.esc(v.nickname)}</b><small>${v.year} ${ui.esc(v.make)} ${ui.esc(v.model)}</small></div>${ui.stats([{ label: 'Drives', value: ts.length }, { label: 'Average', value: avg }, { label: 'km', value: Math.round(ts.reduce((a, t) => a + t.distanceKm, 0)) }])}</div>`;
    }
    if (state === 'export') return `<div class="sub3-p">${csvPreview(ctx, data.trip('t20261105-1745'), false)}</div>`;
    if (state === 'trends') { const w = D.wrapped.monthly.chapters.find((c) => c.id === 'trend').weeks; return `<div class="sub3-p sub3-tr"><div class="sub3-tr-h"><b class="ui-num">${w[w.length - 1]}</b><small>October · ▲ ${w[w.length - 1] - w[0]} since the start of the month</small></div>${ui.sparkline(w, { w: 300, h: 70, color: 'var(--dp-accent)', area: true, stroke: 3, label: 'October trend' })}<div class="sub3-tabs">${['Month', 'Year', 'Per car', 'Per route'].map((x, i) => `<span class="${i ? '' : 'on'}">${x}</span>`).join('')}</div></div>`; }
    if (state === 'backup') return `<div class="sub3-p sub3-bk">${art(ctx, CLOUD('ok'), { tone: 'positive' })}<div><b>Up to date</b><small>${D.privacy.dataSize.trips} drives · ${D.privacy.dataSize.mb} MB</small></div></div>`;
    const r = data.route('r_commute'), g = D.ghost;
    return `<div class="sub3-p sub3-ghost"><svg viewBox="0 0 120 80" width="100%" height="104" aria-hidden="true"><path d="${U.glyphPath(r.glyph, 120, 80, 8)}" fill="none" stroke="var(--dp-data-ghost)" stroke-width="6" stroke-linecap="round" stroke-dasharray="1 7"/><path d="${U.glyphPath(r.glyph, 120, 80, 8)}" fill="none" stroke="var(--dp-accent)" stroke-width="3" stroke-linecap="round" transform="translate(2 -2)"/></svg><div class="sub3-seg">${g.segments.map((s) => `<i class="is-${s.band}"></i>`).join('')}</div><small>New record 91 vs your previous record 89</small></div>`;
  }
  screen('S-SUB-03', {
    name: 'Contextual lock sheet',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'profile', detent: 'auto',
    over: (ctx) => (LOCK[ctx.state] || LOCK.default).over,
    states: [
      { id: 'default', label: 'compare with record' }, { id: 'replayAnalysis30Days', label: 'Replay/Analysis > 30 days' }, { id: 's3rdRoute', label: '3rd route' }, { id: 's2ndCar', label: '2nd car' },
      { id: 'export', label: 'export' }, { id: 'trends', label: 'trends' }, { id: 'backup', label: 'backup' }, { id: 'noDataYet', label: 'no data yet' },
    ],
    stateParams: Object.fromEntries(['default', 'replayAnalysis30Days', 's3rdRoute', 's2ndCar', 'export', 'trends', 'backup', 'noDataYet'].map((s) => [s, { ent: 'free' }])),
    render(ctx) {
      const { ui, state } = ctx;
      const L = LOCK[state] || LOCK.default;
      const text = state === 'noDataYet' ? 'Month and year trends appear after your first full month of drives. With Pro, you’ll see how your driving changes month to month.' : D.entitlement.lockSheets[L.key];
      const top = state === 'noDataYet'
        ? `<div class="sub3-nd">${art(ctx, 'insights', { icon: 44 })}<span class="sub3-k">${ui.icon('lock', 14)}${ui.esc(L.title)} · Pro</span></div>`
        : `<div class="sub3-blur"><div class="sub3-in dp-locked" aria-hidden="true">${lockPreview(ctx, state)}</div><span class="sub3-lk">${ui.icon('lock', 22)}</span></div>`;
      return `<div class="sub3">${top}
        ${state === 'noDataYet' ? '' : `<span class="sub3-k">${ui.icon('lock', 14)}${ui.esc(L.title)} · Pro</span>`}
        <h2 class="sub3-t">${ui.esc(text)}</h2>
        <p class="e-foot sub3-f">Your summaries, Stories and scores stay free forever. Nothing is deleted on Free.</p>
        <div class="e-sheetacts">${ui.button('See plans', { kind: 'filled', size: 'l', block: true, nav: 'S-SUB-04', params: { from: L.key, region: 'CA', founding: '1' } })}${ui.button('Not now', { kind: 'tonal', size: 'l', block: true, back: true })}</div></div>`;
    },
    mount(el, ctx) {
      const b = el.querySelector('.sub3-in');
      if (b && !still(ctx, el)) b.animate([{ filter: 'blur(0px) saturate(1)', opacity: 1 }, { filter: 'blur(14px) saturate(.8)', opacity: 0.85 }], { duration: 320, easing: 'cubic-bezier(.2,0,0,1)', fill: 'both', delay: 120 });
      ctx.ui.anim.pop(el.querySelector('.sub3-lk, .sub3-nd .e-art'), 360);
    },
    spec: {
      purpose: 'When a Free user taps a Pro feature, show what Pro would show on their own data — blurred, never a stock screenshot — with one sentence of value, “See plans” and “Not now” (F-SUB-04, F-HIS-03 “locked, not lost”). Not now returns exactly where they were.',
      layout: [
        'Sheet fitted to content (spec: medium). Top: the user’s own content for that feature, blurred 14 px + 35% dim, radius 20, with a 52 lock disc centred (iOS glass, Android tonal).',
        'Variants (own data): compare = The Commute ghost vs record + 12 gained/lost segments · Replay/Analysis > 30 days = that trip’s map + synced timeline · 3rd route = Gym card (glyph, 2 drives, record 84, run dots) · 2nd car = Ioniq silhouette + drives/average/km tiles · export = that drive’s CSV rows · trends = October trend sparkline + Month/Year/Per car/Per route tabs · backup = backup status · no data yet = illustration instead of a blur.',
        'Kicker “🔒 Ghost comparison · Pro”, then the value sentence (sheetTitle-size, 20/26 semibold): entitlement.lockSheets.*, footnote “Your summaries, Stories and scores stay free forever. Nothing is deleted on Free.”',
        'Stacked large buttons: “See plans” (filled) · “Not now” (tonal).',
      ],
      components: ['sheet', 'lockPanel', 'map', 'timeline', 'glyph', 'stats', 'sparkline', 'scoreBadge', 'button'],
      interactions: ['See plans → S-SUB-04 (from = feature). Not now / drag / scrim → back to exactly where the user was (scroll position kept).', 'Gate: never during a trip or on a worse-than-usual Story’s end card (F-SUB-08.3); the D1 gate holds it while moving.'],
      motion: ['The user’s content blurs from 0 → 14 px over 320 ms (spring.effects) while the sheet rises at the medium detent; the lock disc pops at 360 ms. RM: shown already blurred, fade.'],
      a11y: ['The blurred preview is hidden from screen readers; the sheet reads “Ghost comparison, Pro. Keep comparing every run of The Commute with Pro.” then the buttons. XL: opens large; the preview keeps its height.'],
      platformDiffs: { ios: 'Glass sheet at a partial detent; lock disc in glass.', android: 'ModalBottomSheet; lock disc on surfaceContainerHigh; RenderEffect blur (API 31+) or a pre-blurred bitmap.' },
      analytics: ['lock_viewed {feature}', 'lock_cta {see_plans|not_now}'],
      copy: Object.assign({}, D.entitlement.lockSheets, { noDataYet: 'Month and year trends appear after your first full month of drives. With Pro, you’ll see how your driving changes month to month.', Footnote: 'Your summaries, Stories and scores stay free forever. Nothing is deleted on Free.' }),
      freePro: 'Free users only (ent = free). Preview/Ride-along/Pro never see it.',
      data: 'compare: route r_commute + ghost (91 vs 89) · replay: t20261002-0745 · 3rd route: r_gym · 2nd car: Ioniq (derived: its scored trips, average and km) · export: t20261105-1745 · trends: wrapped.monthly trend weeks [81, 85, 85, 87] · backup: privacy.dataSize. Parent screens rendered underneath on a deep link follow the variant (S-RTE-02, S-TRP-01 Lock, S-RTE-01, S-GAR-01, S-STA-01, S-ACC-04 Lock).',
      icons: ['lock', 'insights', 'cloud'],
      assets: [],
    },
  });

  /* ---------------- S-SUB-04 · Plans (paywall): iOS sheet (large) / Android full-screen dialog ---------------- */
  screen('S-SUB-04', {
    name: 'Plans (paywall)',
    wp: 'E', platforms: ['ios', 'android'], presentation: { ios: 'sheet', android: 'full' }, tab: 'profile', detent: 'large', over: 'S-ACC-01',
    states: [
      { id: 'default', label: 'US prices' }, { id: 'caPrices', label: 'CA prices' }, { id: 'foundingOffer', label: 'founding offer' }, { id: 'Load', label: 'Load' },
      { id: 'Err', label: 'Err' }, { id: 'Off', label: 'Off (offline)' }, { id: 'pendingPurchase', label: 'pending purchase (Ask to Buy)' }, { id: 'storeSheet', label: 'store sheet mock (extra)' },
    ],
    stateParams: { default: { region: 'US' }, caPrices: { region: 'CA' }, foundingOffer: { region: 'CA', founding: '1' }, Load: { region: 'CA', founding: '1' }, Err: { region: 'CA', founding: '1' }, Off: { region: 'CA', founding: '1' }, pendingPurchase: { region: 'CA', founding: '1' }, storeSheet: { region: 'CA', founding: '1' } },
    render(ctx) {
      const { ui, state, params } = ctx;
      const region = regionOf(ctx), founding = params.founding === '1', plan = ctx.store.plan || 'annual';
      const pi = planInfo(region, founding, plan);
      const off = state === 'Off', load = state === 'Load', err = state === 'Err', pend = state === 'pendingPurchase';
      const head = `<div class="sub4-h">${art(ctx, ctx.icons.loop({ size: 54, color: 'var(--dp-accent)', dot: 'var(--dp-text)' }), { cls: 'sub4-art' })}<h1 class="e-h1">DrivePerf Pro</h1><p class="e-lead">Every drive, decoded, in full.</p></div>`;
      let middle;
      if (err) middle = `<div class="sub4-err">${ui.error({ title: 'The store isn’t responding.', text: 'Your Preview and data aren’t affected. Try again in a moment.', actionText: 'Try again', action: { state: founding ? 'foundingOffer' : 'caPrices' } })}</div>`;
      else if (pend) middle = ui.card(`<div class="sub4-pend">${art(ctx, 'clock', { icon: 36 })}<div><b>${ctx.ios ? 'Waiting for approval' : 'Your purchase is pending'}</b><p>${ctx.ios ? 'We’ve asked your family organiser to approve DrivePerf Pro (Annual). You’ll get Pro as soon as they do, and we’ll let you know.' : 'Google Play will finish it as soon as your payment goes through. You’ll get Pro then, and we’ll let you know.'}</p></div></div>`, { hero: true });
      else middle = `${off ? `<div class="sub4-b">${ui.banner({ kind: 'info', icon: 'gps', title: 'You’re offline', text: 'Purchases need a connection. Everything else keeps working.' })}</div>` : ''}${planCards(ctx, { region, founding, sel: plan, loading: load, off })}`;
      const acts = pend ? ui.button('Keep using DrivePerf', { kind: 'filled', size: 'l', block: true, back: true })
        : equalPair(ctx, { text: load ? 'Start Pro' : pi.btn, action: off || load || err ? null : 'buy', disabled: off || load || err }, { text: 'Continue with Free', back: true });
      const body = `<div class="sub4">${head}<div class="sub4-b">${valueList(ctx)}</div>${middle}<div class="sub4-acts">${acts}</div>${pend ? '' : legal(ctx, region, founding, plan)}</div>`;
      const store = state === 'storeSheet' || ctx.store.buy ? osStore(ctx, { planName: pi.name, priceLine: pi.priceLine, renewLine: pi.renewLine }) : '';
      if (ctx.ios) return `<div class="sub4-x">${ui.iconButton('close', { back: true, label: 'Close', glass: true })}</div>${body}${store}`;
      return ui.page(body, { fixed: `<div class="sub4-bar">${ui.iconButton('close', { back: true, label: 'Close' })}</div>`, overlay: store });
    },
    mount(el, ctx) {
      ctx.ui.anim.pop(el.querySelector('.sub4-art'), 60);
      if (still(ctx, el)) return;
      el.querySelectorAll('.sub-plan, .sub-vals li').forEach((p, i) => ctx.motion.animate(p, [{ opacity: 0, transform: 'translateY(8px)' }, { opacity: 1, transform: 'translateY(0)' }], { preset: 'default', delay: 120 + i * 50 }));
      const a = el.querySelector('.sub4-acts'); if (a) ctx.motion.animate(a, [{ opacity: 0, transform: 'translateY(8px)' }, { opacity: 1, transform: 'translateY(0)' }], { preset: 'default', delay: 420 });
    },
    actions: subActions,
    spec: {
      purpose: 'Clear monthly and annual plans at fair local prices with every required disclosure (F-SUB-01, F-SUB-05; Apple 3.1.2, Play subscriptions policy): Annual pre-selected, Monthly equal in size, “Continue with Free” equal in weight to the purchase button, close visible immediately, no weekly or lifetime plan, no countdown, no strikethrough.',
      layout: [
        'iOS: sheet at the large detent, glass close top-trailing. Android: full-screen dialog with close leading.',
        'Header: Loop in a 104 illustration tile, “DrivePerf Pro” (28/34), “Every drive, decoded, in full.”',
        'Value list: 4 Pro features with glyphs (replay, routes, insights, cloud) from entitlement.proFeatures.',
        'Plan cards side by side, identical (radius 20 iOS / 16 → selected 28 Android, min height 148): label + radio, price in DP Numerals with the currency at 50% (“US$ 39.99 /year”), sub line, optional gold “FOUNDING PRICE” capsule. US: Annual US$39.99/year · US$3.33/month, billed yearly · Monthly US$6.99/month. CA: CA$49.99 · CA$4.17 · CA$8.99. Founding: Annual “CA$39.99 first year · Then CA$49.99/year”.',
        'Equal-weight pair: “Start Pro: US$39.99/year” (filled) + “Continue with Free” (glass / secondaryContainer), same size and type, stacked.',
        'Legal (caption, text-2, never truncated): auto-renewal at the plan price, 24-hour rule, where it is charged and how to cancel; Terms · Privacy · Restore purchases.',
        'Load: plan-card skeletons, Start Pro disabled. Err: error state “The store isn’t responding. Your Preview and data aren’t affected.” + Try again. Off: info banner “You’re offline. Purchases need a connection.” + cards and Start Pro disabled (Continue with Free stays). pending: “Waiting for approval” card (Ask to Buy) / “Your purchase is pending” (Play) + “Keep using DrivePerf”. Store sheet: StoreKit / Play Billing mock over the paywall.',
      ],
      components: ['sheet', 'iconButton', 'button', 'banner', 'error', 'skeleton', 'card'],
      interactions: [
        'Plan card → select (ring slides between cards, spring.fast, haptic selection); the primary label and legal text follow the plan.',
        'Start Pro → StoreKit purchase sheet / Play Billing flow (mocked: tap = purchase) → entitlement active within 5 s → S-SUB-05. Failure → toast + error haptic. Ask to Buy / pending → pending state.',
        'Continue with Free / close / drag down → back to where the user was. Restore purchases → store restore (toast with the result).',
        'Gate: S-SUB-04 is never reachable during a trip or while moving (D1 → S-DRV-05) and never auto-opens.',
      ],
      motion: ['Present: spring.sheet (iOS) / slide-up full-screen dialog + scrim fade (Android), ≈ 520 ms. Illustration pops; value rows and plan cards rise 8 px, 50 ms stagger; the button pair rises as one block. No pulsing or shimmering CTA. RM: fade.'],
      a11y: ['Plan cards are a radio group: “Annual, US$39.99 /year. US$3.33/month, billed yearly, selected”. Close is the first focusable element.', 'XL: plan cards stack (Annual first), the legal block stays fully visible and scrolls.', 'Prices are read as “39 dollars 99 US” by the system from the store’s localized string.'],
      platformDiffs: { ios: 'Sheet .large; glass plan cards with a Glide selection ring; .glassProminent + .glass buttons at equal size; StoreKit 2 purchase + Ask to Buy (.pending).', android: 'Full-screen dialog; plan cards surfaceContainerHigh → selected primaryContainer with shape morph to extraLargeIncreased; filled + FilledTonal buttons at equal size; Play Billing (PENDING purchase state).' },
      analytics: ['paywall_viewed {source}', 'plan_selected {plan}', 'purchase_started', 'purchase_completed {plan, offer}', 'purchase_failed {reason}', 'offer_shown {offer: founding}'],
      copy: {
        Header: 'DrivePerf Pro · Every drive, decoded, in full.',
        'US plans': 'Annual US$39.99/year (US$3.33/month, billed yearly) · Monthly US$6.99/month (Billed monthly, cancel any time)',
        'CA plans': 'Annual CA$49.99/year (CA$4.17/month, billed yearly) · Monthly CA$8.99/month',
        Founding: 'Annual CA$39.99 first year · Then CA$49.99/year · Founding price (US: US$29.99 then US$39.99/yr)',
        Err: 'The store isn’t responding. Your Preview and data aren’t affected. Try again in a moment.',
        Off: 'You’re offline. Purchases need a connection. Everything else keeps working.',
        Pending: 'Waiting for approval. We’ve asked your family organiser to approve DrivePerf Pro (Annual). You’ll get Pro as soon as they do, and we’ll let you know.',
        Legal: 'Pro renews automatically at {price} unless you cancel at least 24 hours before the renewal date. Payment is charged to your Apple Account / Google Play account. Cancel any time in Settings → your name → Subscriptions / Google Play → Payments and subscriptions.',
      },
      freePro: 'Shown to Free and Preview users. Prices come from the store at runtime (RevenueCat offerings); the prototype uses entitlement.prices. One subscription group “DrivePerf Pro”; no weekly, no lifetime product.',
      data: 'entitlement.prices.US / CA (annualPerMonth derived by the fixture). Founding = user.founding; the default state shows standard US prices per the PRD state list. Renewal in the store mock = today + 1 year.',
      icons: ['replay', 'routes', 'insights', 'cloud', 'close', 'clock', 'gps', 'refresh'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ---------------- S-SUB-05 · Welcome to Pro (the only time the Loop closes) ---------------- */
  screen('S-SUB-05', {
    name: 'Welcome to Pro',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'full', tab: 'profile',
    states: [{ id: 'default', label: 'Def' }],
    stateParams: { default: { ent: 'pro' } },
    render(ctx) {
      const { ui } = ctx;
      const p = PRICES.CA;
      const locked = D.trips.filter((t) => U.isLocked(t, 'free')).length;
      const s = 168, r = 62, C = 2 * Math.PI * r;
      const loop = `<svg class="sub5-loop" width="${s}" height="${s}" viewBox="0 0 ${s} ${s}" aria-hidden="true"><circle cx="${s / 2}" cy="${s / 2}" r="${r}" fill="none" stroke="var(--dp-fill-strong)" stroke-width="10"/><circle class="sub5-arc" cx="${s / 2}" cy="${s / 2}" r="${r}" fill="none" stroke="var(--dp-accent)" stroke-width="10" stroke-linecap="round" stroke-dasharray="${C.toFixed(1)} ${C.toFixed(1)}" data-c="${C.toFixed(1)}" transform="rotate(-78 ${s / 2} ${s / 2})"/><circle class="sub5-dot" cx="${s / 2 + r}" cy="${s / 2}" r="9" fill="var(--dp-text)" transform="rotate(-78 ${s / 2} ${s / 2})"/></svg>`;
      const items = [
        { icon: 'replay', t: `Replay and Analysis for all ${D.trips.length} drives`, s: `${locked} older drives are unlocked again` },
        { icon: 'routes', t: 'Every route, with ghost comparisons', s: 'Gym is an active route again' },
        { icon: 'garage', t: 'Every car in your garage', s: 'The Ioniq’s log is back on' },
        { icon: 'insights', t: 'Month, year, per-car and per-route trends', s: 'Plus GPX/CSV export and cloud backup' },
      ];
      return ui.page(`<div class="sub5"><div class="sub5-art" data-rise>${loop}</div>
        <div class="sub5-t ui-pad" data-rise><h1 class="e-h1">Welcome to Pro.</h1><p class="e-lead">Everything is unlocked, including every drive you’ve already recorded.</p></div>
        <div class="ui-pad" data-rise>${ui.list(items.map((x) => ui.row({ icon: x.icon, title: x.t, sub: x.s, trailing: '' })), { cls: 'sub5-l' })}</div>
        <div class="ui-pad" data-rise><p class="sub5-plan"><span class="acc-found">Founding member</span>${ui.esc(`${p.founding} for your first year, then ${p.annual}/yr. Renews ${dateLong('2027-11-06')}.`)}</p></div><div style="height:160px"></div></div>`,
        { fixed: dock(ui.button('Back to your drive', { kind: 'filled', size: 'l', block: true, back: true, haptic: 'tap' }) + ui.button('Turn on cloud backup', { kind: 'tonal', size: 'l', block: true, nav: 'S-ACC-04' }), { cls: 'is-tall' }) });
    },
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '.sub5 > [data-rise]:not(.sub5-art)', { stagger: 50, delay: 500 });
      const arc = el.querySelector('.sub5-arc'), dot = el.querySelector('.sub5-dot');
      if (!arc || still(ctx, el)) return;
      const C = +arc.dataset.c, open = 0.75, s = 168, c = s / 2;
      const set = (f) => { arc.setAttribute('stroke-dasharray', `${(C * f).toFixed(1)} ${C}`); dot.setAttribute('transform', `rotate(${-78 + 360 * f + (f < 1 ? 22 : 0)} ${c} ${c})`); };
      set(open);
      const M = ctx.motion; let t2;
      M.tween({ from: open, to: 1, duration: 700, ease: 'glide', el, onUpdate: set, onDone: () => { ctx.haptic('success'); t2 = setTimeout(() => M.tween({ from: 1, to: open, duration: 600, ease: 'standard', el, onUpdate: set }), 400); } });
      return () => clearTimeout(t2);
    },
    spec: {
      purpose: 'Confirm a successful purchase and what it unlocked, in the user’s own terms (F-SUB-01.3), then send them straight back to the feature they wanted. Calm pride, no confetti.',
      layout: [
        'Full-screen cover (theme-following). Centre: the Loop at 168 (10 stroke track + Glide arc + G-dot) — the only screen where the Loop closes.',
        'Title “Welcome to Pro.” (28/34) + lead “Everything is unlocked, including every drive you’ve already recorded.”',
        'Unlocked list (4 rows, icon tiles): Replay and Analysis for all 30 drives (4 older drives unlocked) · Every route, with ghost comparisons (Gym active again) · Every car (the Ioniq’s log is back on) · Trends, plus export and backup.',
        'Plan line: gold “FOUNDING MEMBER” capsule + “CA$39.99 for your first year, then CA$49.99/yr. Renews 6 November 2027.”',
        'Dock: “Back to your drive” (filled) · “Turn on cloud backup” (tonal).',
      ],
      components: ['page', 'list', 'row', 'button'],
      interactions: ['Back to your drive → dismiss back to the unlocked feature (e.g. the trip whose Replay was locked). Turn on cloud backup → S-ACC-04.'],
      motion: ['Loop close (motion.md §3.9): the G-dot travels across the gap and the ring completes (700 ms, ease.glide ≈ spring.slow), holds 400 ms, then reopens as the dot moves ahead (600 ms); haptic success when it closes. Text and list rise 8 px from 500 ms (50 ms stagger). RM / export shot: the Loop is shown closed, then open, with no travel.'],
      a11y: ['Focus lands on “Welcome to Pro.”; the Loop is decorative. List rows are static text. XL: rows wrap; the Loop shrinks to 132.'],
      platformDiffs: { ios: 'fullScreenCover; .sensoryFeedback(.success).', android: 'Full-screen destination; HapticFeedbackConstants.CONFIRM.' },
      analytics: ['purchase_completed {plan, offer}', 'welcome_pro_viewed', 'welcome_pro_action {back|backup}'],
      copy: { Title: 'Welcome to Pro.', Lead: 'Everything is unlocked, including every drive you’ve already recorded.', Plan: 'Founding member · CA$39.99 for your first year, then CA$49.99/yr. Renews {date}.', Buttons: 'Back to your drive · Turn on cloud backup' },
      freePro: 'Shown once after a purchase. Monthly purchase: the plan line reads “CA$8.99/month. Renews {date}.” without the founding badge.',
      data: 'Unlock counts derived from the fixtures: 30 trips, 4 archive trips locked on Free (util.isLocked), r_gym (3rd route), Ioniq (2nd car). Prices from entitlement.prices.CA; renewal = purchase day + 1 year (prototype: today).',
      icons: ['replay', 'routes', 'garage', 'insights'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ---------------- S-SUB-06 · Manage subscription ---------------- */
  const SUB6 = {
    default: { ent: 'proMonthly' }, proAnnual: { ent: 'pro' }, rideAlong: { ent: 'rideAlong' }, preview: { ent: 'preview' }, restoreResult: { ent: 'pro' }, testerPro: { ent: 'pro', tester: '1' },
  };
  screen('S-SUB-06', {
    name: 'Manage subscription',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'push', tab: 'profile',
    states: [{ id: 'default', label: 'Pro monthly' }, { id: 'proAnnual', label: 'Pro annual' }, { id: 'rideAlong', label: 'Ride-along' }, { id: 'preview', label: 'Preview' }, { id: 'restoreResult', label: 'restore result' }, { id: 'testerPro', label: 'tester / extended Pro (extra)' }],
    stateParams: SUB6,
    render(ctx) {
      const { ui, state } = ctx;
      const st = D.entitlement.states, store = storeName(ctx);
      const tester = state === 'testerPro';
      const es = ctx.entState;
      let name, price, rows, note, ctas;
      if (tester) {
        name = 'DrivePerf Pro · Founding Drivers beta'; price = 'Included while you test';
        rows = [['Source', 'Promotional (beta)'], ['Started', dateLong('2026-10-13')], ['Ends', 'When testing ends. We’ll tell you first.'], ['Charge', 'None']];
        note = 'No card, nothing to cancel. When the beta ends you keep Free, or choose a plan at the founding price.';
        ctas = [ui.row({ icon: 'sparkle', title: 'See plans', sub: 'For when testing ends', nav: 'S-SUB-04', params: { region: 'CA', founding: '1' } })];
      } else if (es.plan === 'preview' || es.plan === 'ridealong') {
        const ride = es.plan === 'ridealong';
        name = ride ? 'Ride-along pass' : 'Pro Preview'; price = ride ? `From ${es.from} · no charge` : 'No card · no charge';
        rows = [['Source', ride ? `Ride-along pass from ${es.from}` : 'Pro Preview (every new user)'], ['Progress', `Day ${es.day} of ${es.of}`], ['Then', 'Free, unless you choose a plan'], ['Charge', 'None']];
        note = 'Nothing renews automatically. Your drives stay yours either way.';
        ctas = [ui.row({ icon: 'sparkle', title: 'See plans', sub: 'Monthly or annual', nav: 'S-SUB-04', params: { region: 'CA', founding: '1' } })];
      } else {
        const m = es.plan === 'pro_monthly';
        name = `DrivePerf Pro · ${m ? 'Monthly' : 'Annual'}`; price = m ? st.proMonthly.price : st.pro.price;
        rows = [['Source', store], ['Since', dateLong(es.since)], ['Renews', dateLong(es.renews)], ['Price', price]];
        note = `If you cancel, Pro stays on until ${dateLong(es.renews)}. Your drives stay yours either way.`;
        ctas = [ui.row({ icon: 'export', title: `Manage in ${store}`, sub: 'Change plan or cancel', toast: ctx.ios ? 'Opens App Store subscription settings' : 'Opens Google Play subscriptions' }), m ? ui.row({ icon: 'sparkle', title: 'See plans', sub: 'Compare monthly and annual', nav: 'S-SUB-04', params: { region: 'CA' } }) : ''];
      }
      const hero = ui.card(`<div class="sub6-h"><span class="sub6-ic">${ctx.icons.loop({ size: 30, color: 'var(--dp-accent)', dot: 'var(--dp-text)' })}</span><div><b>${ui.esc(name)}${es.founding && !tester ? ' <span class="acc-found">Founding</span>' : ''}</b><small>${ui.esc(price)}</small></div></div>${es.day ? progress(ctx, es.day / es.of, { label: `Day ${es.day} of ${es.of}` }) : ''}<div class="e-kv">${rows.map(([k, v]) => `<div><span>${ui.esc(k)}</span><b>${ui.esc(v)}</b></div>`).join('')}</div>`, { hero: true, tint: 'accent' });
      const blocks = [
        state === 'restoreResult' ? ui.pad(ui.banner({ kind: 'positive', title: 'Purchases restored', text: `DrivePerf Pro (Annual) from ${store} is active on this phone.` })) : '',
        ui.pad(hero),
        ui.section('', ui.list([...ctas, ui.row({ icon: 'refresh', title: 'Restore purchases', sub: `Bought Pro with this ${ctx.ios ? 'Apple Account' : 'Google account'} on another phone?`, action: 'restore6' })].filter(Boolean)), { style: 'caption', footer: ui.esc(note) }),
      ].filter(Boolean);
      return ui.page(`<div class="sub6">${blocks.map((b) => `<div class="acc-b" data-rise>${b}</div>`).join('')}</div>`, { nav: { title: 'Subscription' } });
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '.acc-b[data-rise]', { stagger: 40 }); },
    actions: { restore6: (ctx) => { ctx.toast(`Checking ${storeName(ctx)}…`, { icon: 'refresh' }); setTimeout(() => ctx.setState('restoreResult'), 700); return false; } },
    spec: {
      purpose: 'See the plan, price, renewal date and source (Preview, Ride-along, store, or a tester promotion), manage it in the store and restore purchases (F-SUB-06). Includes the beta/internal “free or extended Pro” promotional entitlement (Gate 2 OQ-08).',
      layout: [
        'Push, title “Subscription”. restore result: positive banner “Purchases restored. DrivePerf Pro (Annual) from App Store is active on this phone.”',
        'Plan hero card (Glide tint): Loop tile + plan name (+ gold “FOUNDING” capsule for the founding annual) + price line; Preview/Ride-along add a day progress bar; facts (Source · Since/Progress · Renews/Then · Price/Charge).',
        'Actions list: Manage in App Store / Google Play (change plan or cancel) · See plans (monthly, Preview, Ride-along, tester) · Restore purchases. Footer: what happens if they cancel, or that nothing renews.',
      ],
      components: ['navBar', 'banner', 'card', 'list', 'row', 'section'],
      interactions: ['Manage in App Store → StoreKit showManageSubscriptions · Google Play → the Play subscriptions deep link. See plans → S-SUB-04. Restore purchases → restores within 10 s → restore result (toast while checking).'],
      motion: ['Blocks rise 8 px, 40 ms stagger. RM: fade.'],
      a11y: ['Hero card reads plan, price and each fact as label–value pairs. XL: facts stack.'],
      platformDiffs: { ios: '“Manage in App Store” → showManageSubscriptions(in:) sheet; “Apple Account”.', android: '“Manage in Google Play” → https://play.google.com/store/account/subscriptions?sku=…; “Google account”.' },
      analytics: ['subscription_managed', 'restore_purchases {result}'],
      copy: {
        Monthly: 'DrivePerf Pro · Monthly · CA$8.99/month · Renews 13 December 2026. If you cancel, Pro stays on until 13 December 2026. Your drives stay yours either way.',
        Annual: 'DrivePerf Pro · Annual · Founding · CA$39.99 first year, then CA$49.99/yr · Renews 13 October 2027',
        'Ride-along': 'Ride-along pass · From Priya · no charge · Day 4 of 30 · Then Free, unless you choose a plan. Nothing renews automatically.',
        Preview: 'Pro Preview · No card · no charge · Day 9 of 14',
        Tester: 'DrivePerf Pro · Founding Drivers beta · Included while you test · Source Promotional (beta) · No card, nothing to cancel.',
        Restored: 'Purchases restored. DrivePerf Pro (Annual) from App Store is active on this phone.',
      },
      freePro: 'Pro monthly / annual (store), Preview, Ride-along and tester promotional Pro (RevenueCat promotional entitlement). Free users reach S-SUB-04 instead.',
      data: 'entitlement.states.proMonthly (since 13 Oct, renews 13 Dec 2026, CA$8.99/month), pro (founding, renews 13 Oct 2027, “CA$39.99 first year, then CA$49.99/yr”), rideAlong (Priya, 4/30), preview (9/14). Tester start date (13 Oct) is a prototype value.',
      icons: ['export', 'sparkle', 'refresh'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ---------------- S-SUB-07 · Preview ended / "You're on Free" ---------------- */
  screen('S-SUB-07', {
    name: 'Preview ended / “You’re on Free”',
    wp: 'E', platforms: ['ios', 'android'], presentation: 'full', tab: 'profile',
    states: [{ id: 'default', label: 'Def' }, { id: 'chooseActiveRoutesCar', label: 'choose active routes/car' }, { id: 'lowUsageVariant', label: 'low-usage variant (< 3 trips)' }],
    stateParams: { default: { ent: 'free' }, chooseActiveRoutesCar: { ent: 'free' }, lowUsageVariant: { ent: 'free' } },
    render(ctx) {
      const { ui, data, state } = ctx;
      const low = state === 'lowUsageVariant', choose = state === 'chooseActiveRoutesCar';
      const stays = [{ kind: 'ok', text: 'Every drive’s summary, forever' }, { kind: 'ok', text: 'Stories, Smoothness, events and tips' }, { kind: 'ok', text: 'Weekly recaps and Drive Wrapped' }, { kind: 'ok', text: 'Replay and Analysis for the last 30 days' }, { kind: 'ok', text: '2 routes and 1 car' }, { kind: 'ok', text: 'Download all your data, any time' }];
      const pauses = [{ kind: 'lock', text: 'Replay and Analysis older than 30 days' }, { kind: 'lock', text: 'Extra routes and cars, and ghost comparisons' }, { kind: 'lock', text: 'Month and year trends, export and backup' }];
      const head = low
        ? `<h1 class="e-h1">Your Pro Preview has ended.</h1><p class="e-lead">It ended before DrivePerf had 3 drives to decode, so there’s no two-week recap yet. Free keeps recording every drive.</p>`
        : `<h1 class="e-h1">You’re on Free. Here’s what stays.</h1><p class="e-lead">Your Pro Preview has ended. Nothing you recorded is deleted.</p>`;
      const blocks = [`<div class="sub7-h ui-pad">${art(ctx, low ? 'drives' : 'check', { tone: low ? '' : 'positive', icon: 44 })}${head}</div>`];
      if (low) blocks.push(ui.pad(ui.card(`<div class="sub7-hc">${ui.icon('health', 22)}<div><b>Make sure automatic trips are on</b><small>A quick Health Check shows anything that’s stopping drives from recording.</small></div></div>`, { nav: 'S-SET-05', label: 'Run Health Check' })));
      if (choose) {
        const sel = ctx.store.routes || D.entitlement.states.free.activeRouteIds.slice();
        const car = ctx.store.car || D.entitlement.states.free.activeVehicleId;
        const named = D.routes.filter((r) => r.named);
        blocks.push(ui.section(`Choose 2 active routes · ${sel.length} of 2`, ui.list(named.map((r) => ui.row({ lead: `<span class="sub7-cb ${sel.includes(r.id) ? 'is-on' : ''}" role="checkbox" aria-checked="${sel.includes(r.id)}">${sel.includes(r.id) ? ui.icon('check', 14) : ''}</span>`, title: r.name, sub: `${r.runCount} drives · record ${r.record ? r.record.score : '—'}${sel.includes(r.id) ? '' : ' · paused, not deleted'}`, action: 'route', arg: r.id, trailing: ui.glyph(r.glyph, 32) }))), { style: 'caption', footer: 'Paused routes keep their drives and records. Switch any time in Routes.' }));
        blocks.push(ui.section('Choose your active car', ui.radios(data.cars().map((v) => ({ id: v.id, label: v.nickname, sub: `${v.year} ${v.make} ${v.model}${v.id === car ? '' : ' · its drives stay, new ones need Pro'}` })), car, { action: 'car' }), { style: 'caption', footer: 'You can change this in Garage.' }));
        blocks.push(ui.section('', ui.list([ui.row({ icon: 'routes', title: 'More route options', nav: 'S-RTE-05' }), ui.row({ icon: 'garage', title: 'Open Garage', nav: 'S-GAR-01' })]), { style: 'caption' }));
      }
      blocks.push(ui.section('Stays free', ui.pad(ui.card(checks(ctx, stays))), { style: 'caption' }));
      blocks.push(ui.section('Pauses · locked, not lost', ui.pad(ui.card(checks(ctx, pauses))), { style: 'caption', footer: 'Everything paused comes back the moment you choose Pro.' }));
      const dk = dock(ui.button(choose ? 'Keep these' : 'Continue', { kind: 'filled', size: 'l', block: true, nav: 'S-HOME-01', params: { ent: 'free' } }) + ui.button('See plans', { kind: 'text', size: 'm', block: true, nav: 'S-SUB-04', params: { region: 'CA', founding: '1' } }), { cls: 'is-tall' });
      return ui.page(`<div class="sub7">${blocks.map((b) => `<div class="acc-b" data-rise>${b}</div>`).join('')}<div style="height:150px"></div></div>`, { fixed: dk });
    },
    mount(el, ctx) { ctx.ui.anim.pop(el.querySelector('.e-art'), 40); ctx.ui.anim.rise(el, '.acc-b[data-rise]', { stagger: 40, delay: 80 }); },
    actions: {
      route: (ctx, id) => { const cur = (ctx.store.routes || D.entitlement.states.free.activeRouteIds.slice()); let next = cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id]; if (next.length > 2) { ctx.toast('Free keeps 2 active routes. Untick one first.', { icon: 'info' }); return false; } ctx.store.routes = next; ctx.update(); return false; },
      car: (ctx, id) => { ctx.store.car = id; ctx.update(); return false; },
    },
    spec: {
      purpose: 'When the Preview ends (or after “Continue with Free”), say exactly what stays and what pauses so nothing feels taken (F-SUB-07), let the user pick which routes and car stay active, and handle the low-usage case kindly (F-SUB-03.3).',
      layout: [
        'Full-screen cover (theme-following). Header: positive check illustration (low usage: drives glyph) + “You’re on Free. Here’s what stays.” + “Your Pro Preview has ended. Nothing you recorded is deleted.”',
        'low usage: “Your Pro Preview has ended.” + “It ended before DrivePerf had 3 drives to decode…” + a Health Check card → S-SET-05.',
        'choose active routes/car: “Choose 2 active routes · 2 of 2” checklist rows (checkbox, name, runs, record, glyph) + “Choose your active car” radios (GTI / Ioniq) + links to S-RTE-05 and S-GAR-01.',
        'Stays free card (✓ ×6) and “Pauses · locked, not lost” card (lock ×3) + footer “Everything paused comes back the moment you choose Pro.”',
        'Dock: filled “Continue” (“Keep these”) + text “See plans”.',
      ],
      components: ['page', 'card', 'list', 'row', 'radios', 'section', 'button'],
      interactions: ['Route rows toggle (max 2; a third shows a toast). Car radios select. Continue / Keep these → S-HOME-01 on Free. See plans → S-SUB-04. Health Check card → S-SET-05. More route options → S-RTE-05 · Open Garage → S-GAR-01.'],
      motion: ['Illustration pops; blocks rise 8 px, 40 ms stagger. RM: fade.'],
      a11y: ['Checkbox rows: “The Commute, 10 drives, record 91, checkbox, checked”. The section title announces “2 of 2”. XL: rows wrap, glyphs hide behind the text.'],
      platformDiffs: { ios: 'fullScreenCover; inset-grouped lists; checkmark circles.', android: 'Full-screen destination; M3 Checkbox ListItems and RadioButtons.' },
      analytics: ['preview_ended {trips_bucket}', 'free_active_routes_set', 'free_active_car_set'],
      copy: {
        Title: 'You’re on Free. Here’s what stays.', Lead: 'Your Pro Preview has ended. Nothing you recorded is deleted.',
        'Low usage': 'Your Pro Preview has ended. It ended before DrivePerf had 3 drives to decode, so there’s no two-week recap yet. Free keeps recording every drive.',
        Stays: 'Every drive’s summary, forever · Stories, Smoothness, events and tips · Weekly recaps and Drive Wrapped · Replay and Analysis for the last 30 days · 2 routes and 1 car · Download all your data, any time',
        Pauses: 'Replay and Analysis older than 30 days · Extra routes and cars, and ghost comparisons · Month and year trends, export and backup',
        Footer: 'Everything paused comes back the moment you choose Pro.',
      },
      freePro: 'Free (after Preview). Defaults per F-RTE-05: entitlement.states.free.activeRouteIds (The Commute, The Commute home) and activeVehicleId (GTI).',
      data: 'Named routes r_commute, r_commute_home, r_gym (the unnamed candidate isn’t offered); cars GTI, Ioniq. The low-usage variant uses no trip count from the fixtures (Marcus had 8), so the copy avoids a number.',
      icons: ['check', 'lock', 'drives', 'health', 'routes', 'garage'],
      assets: [],
    },
  });

  /* @@WP-E-MORE@@ */
})();
