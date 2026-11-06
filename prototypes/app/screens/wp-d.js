/* WP-D · History, Insights, Routes & Garage — screen definitions (DRIVE-32). Contract: prototypes/app/AUTHORING.md.
   Register each screen with DP.screen('S-XXX-NN', {...}). Only the WP-D owner edits this file.
   Screens: S-HIS-01/02 · S-TRP-02/03 · S-STA-01/02 · S-RTE-01…05 · S-GAR-01…03 (screen-map.md §5 WP-D). */
(function () {
  const { screen } = window.DP;
  const D = window.DP_DATA, U = D.util, I = window.DP_ICONS;
  const TZ = { timeZone: 'America/Toronto' };

  /* ==========================================================================================================
     Local helpers (listed in the WP-D report as core-primitive candidates)
     ========================================================================================================== */
  const ymd = (iso) => new Date(iso).toLocaleDateString('en-CA', TZ);
  const plural = (n, one, many) => `${n} ${n === 1 ? one : many || one + 's'}`;
  const mean = (a) => (a.length ? a.reduce((x, y) => x + y, 0) / a.length : null);
  const avgInt = (a) => (a.length ? Math.round(mean(a)) : null);
  const isPreview = (ent) => ['preview', 'previewEnding', 'rideAlong'].includes(ent);
  const rosette = (s = 14) => I.svg('rosette', { size: s });
  const longDate = (iso) => U.fmtDate(iso, { day: 'numeric', month: 'long' });
  const DOW1 = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];

  /** Tab-root trailing items: Preview badge (when active) + avatar. */
  function rootTrailing(ctx, extra = [], day) {
    const { ui } = ctx;
    return [...extra, isPreview(ctx.ent) ? ui.previewBadge(ctx.ent, day ? { day } : {}) : '', ui.avatar({ badge: ctx.capture.attention })];
  }
  /** "2–8 Nov" / "26 Oct – 1 Nov" */
  function weekRange(ws, we) {
    const a = U.fmtDate(ws), b = U.fmtDate(we);
    const [ad, am] = a.split(' '), [bd, bm] = b.split(' ');
    return am === bm ? `${ad}–${bd} ${bm}` : `${a} – ${b}`;
  }
  /** Duration as DP Numerals: "2 h 9 min" with both units at 50%. */
  function durHtml(ui, s) {
    const h = Math.floor(s / 3600), m = Math.round((s % 3600) / 60);
    return h ? `${ui.num(h, 'h')} ${ui.num(m, 'min')}` : ui.num(m, 'min');
  }
  /** Stat tile that accepts an HTML value (ui.stat escapes its value). Same .ui-stat anatomy and a11y label. */
  function tile(ui, o) {
    return `<div class="ui-stat${o.est ? ' is-est' : ''}" aria-label="${ui.esc(o.aria)}" data-prov="${o.prov || ''}"><span class="ui-stat-l">${ui.esc(o.label)}</span><span class="ui-stat-v">${o.html}</span>${o.sub ? `<span class="ui-stat-s">${o.sub}</span>` : ''}${o.prov ? `<span class="ui-stat-p" title="${o.prov}">${I.provenance(o.prov, { size: 11 })}</span>` : ''}</div>`;
  }
  /** The layer entry directly under this one (the caller of a sheet/dialog). Read-only in render. */
  function caller(ctx) { const st = DP.router.stack; const i = st.indexOf(ctx.entry); return i > 0 ? st[i - 1] : null; }
  /** Sheet result → caller: patch the caller's store, dismiss, then re-render the caller (engine has no sheet results). */
  function returnTo(ctx, patch) {
    const c = caller(ctx);
    if (c && patch) Object.assign(c.store, patch);
    const p = ctx.back();
    if (c && patch) Promise.resolve(p).then(() => { if (c.el && DP.router.stack.includes(c)) DP.router.refresh(c); });
  }
  /** Scroll the layer's scroller so `target` sits under the top bar (used by "focus" states). */
  function scrollToEl(el, target, offset = 120) {
    const sc = el.querySelector('[data-scroll]');
    if (!sc || !target) return;
    const k = sc.getBoundingClientRect().height / (sc.offsetHeight || 1) || 1;
    sc.scrollTop += (target.getBoundingClientRect().top - sc.getBoundingClientRect().top) / k - offset;
  }
  const still = (ctx, el) => ctx.shot || ctx.motion.reduced(el);
  /** Draw-on for chart/glyph paths (motion.md §3.3); RM/shot: final state. */
  function drawPaths(ctx, el, sel, o = {}) {
    if (still(ctx, el)) return;
    el.querySelectorAll(sel).forEach((p, i) => ctx.motion.drawOn(p, { duration: o.duration || 1100, delay: (o.delay || 0) + i * (o.stagger || 0) }));
  }
  function popAll(ctx, el, sel, o = {}) {
    if (still(ctx, el)) return;
    el.querySelectorAll(sel).forEach((d, i) => ctx.motion.animate(d, [{ opacity: 0, transform: 'scale(0)' }, { opacity: 1, transform: 'scale(1)' }], { preset: 'pinPop', delay: (o.delay || 0) + i * (o.stagger || 30) }));
  }

  /** Smoothness line chart (SVG, width 100%). series [{pts:[{x 0..1, y|null, cls, label}], cls, dash, line, step}] */
  function lineChart(ui, o) {
    const w = o.w || 338, h = o.h || 150, L = o.padL ?? 26, R = o.padR ?? 10, T = o.padT ?? 14, B = o.padB ?? 24;
    const X = (x) => L + x * (w - L - R), Y = (y) => T + (1 - (y - o.lo) / (o.hi - o.lo)) * (h - T - B);
    let g = '';
    if (o.band) g += `<rect x="${L}" y="${Y(o.band[1]).toFixed(1)}" width="${w - L - R}" height="${Math.max(2, Y(o.band[0]) - Y(o.band[1])).toFixed(1)}" rx="5" class="dch-band"/>`;
    for (const v of o.ticks || []) g += `<line x1="${L}" x2="${w - R}" y1="${Y(v).toFixed(1)}" y2="${Y(v).toFixed(1)}" class="dch-grid"/><text x="${L - 6}" y="${(Y(v) + 3.5).toFixed(1)}" text-anchor="end" class="dch-ax">${v}</text>`;
    for (const s of o.series) {
      const pts = s.pts.filter((p) => p.y != null);
      if (s.line !== false && pts.length > 1) {
        let d = '';
        pts.forEach((p, i) => { d += i ? (s.step ? `H${X(p.x).toFixed(1)}V${Y(p.y).toFixed(1)}` : `L${X(p.x).toFixed(1)} ${Y(p.y).toFixed(1)}`) : `M${X(p.x).toFixed(1)} ${Y(p.y).toFixed(1)}`; });
        g += `<path d="${d}" class="dch-line ${s.cls || ''}" ${s.dash ? `stroke-dasharray="${s.dash}"` : ''}/>`;
      }
      for (const p of s.pts) {
        if (p.y == null) { if (p.empty) g += `<circle cx="${X(p.x).toFixed(1)}" cy="${Y(o.emptyAt ?? (o.lo + o.hi) / 2).toFixed(1)}" r="5" class="dch-dot is-empty"/>`; continue; }
        if (s.dots === false && !p.cls) continue;
        g += `<circle cx="${X(p.x).toFixed(1)}" cy="${Y(p.y).toFixed(1)}" r="${p.r || s.r || 4}" class="dch-dot ${s.dotCls || ''} ${p.cls || ''}"/>`;
        if (p.label != null) g += `<text x="${X(p.x).toFixed(1)}" y="${(Y(p.y) - 9).toFixed(1)}" text-anchor="middle" class="dch-val ${p.cls || ''}">${ui.esc(p.label)}</text>`;
      }
    }
    for (const xl of o.xLabels || []) g += `<text x="${X(xl.x).toFixed(1)}" y="${h - 6}" text-anchor="${xl.anchor || 'middle'}" class="dch-ax ${xl.cls || ''}">${ui.esc(xl.text)}</text>`;
    return `<svg class="dch ${o.cls || ''}" viewBox="0 0 ${w} ${h}" role="img" aria-label="${ui.esc(o.label || 'Smoothness trend')}">${g}</svg>`;
  }

  /** Route dot strip (components.md §13): one dot per run, x = date; record gold, latest outlined, selected Glide ring. */
  function dotStrip(ctx, r, o = {}) {
    const { ui } = ctx, runs = r.runs, n = runs.length;
    const ts = runs.map((x) => new Date(x.date + 'T12:00:00').getTime()), t0 = ts[0], span = (ts[n - 1] - t0) || 1;
    const recId = r.record && r.record.tripId;
    const dots = runs.map((x, i) => {
      const left = n === 1 ? 50 : ((ts[i] - t0) / span) * 100;
      const c = ['rte-dot', x.tripId === recId && 'is-rec', i === n - 1 && 'is-last', o.sel === i && 'is-sel'].filter(Boolean).join(' ');
      const lab = `${longDate(x.date)}, ${x.partOfDay}. Smoothness ${x.score}${x.tripId === recId ? '. Route record' : ''}`;
      return `<i class="${c}" style="left:${left.toFixed(2)}%" ${o.action ? ui.act({ action: o.action, arg: i, label: lab, haptic: 'selection' }) : ''}></i>`;
    }).join('');
    const last = runs[n - 1];
    const aria = `${plural(n, 'drive')} on ${r.name || r.placeholder}. ${r.record ? `Record ${r.record.score} on ${longDate(r.record.date)}. ` : 'No record yet. '}Latest ${last.score} on ${longDate(last.date)}.`;
    const axis = o.axis === false ? '' : `<div class="rte-axis" aria-hidden="true"><span>${U.fmtDate(runs[0].date)}</span><span>${ui.esc(o.mid || 'Record is Smoothness only')}</span><span>${U.fmtDate(last.date)}</span></div>`;
    return `<div class="rte-stripwrap ${o.cls || ''}"><div class="rte-strip" role="${o.action ? 'group' : 'img'}" aria-label="${ui.esc(aria)}">${dots}</div>${axis}</div>`;
  }
  const reverseOf = (r) => D.routes.find((x) => x.id !== r.id && x.from === r.to && x.to === r.from) || null;
  const routeName = (r) => r.name || r.placeholder;

  /** Generic car silhouettes by body style (brand.md §7: outlines, no logos). Car colour tints; never data. */
  const BODY = {
    hatch: { b: 'M10 58C10 47 15 41 29 39L60 35C72 23 85 17 104 16H134C145 16 151 21 157 30L163 39C180 41 190 45 190 53V58Z', w: 'M68 35C78 25 89 21 104 21H131C139 21 144 25 149 35Z', p: 'M114 21V35', wh: [48, 152], wy: 58 },
    crossover: { b: 'M10 59C10 47 15 41 28 38L56 33C68 20 82 13 102 12H142C154 12 161 17 167 27L173 37C185 39 190 45 190 53V59Z', w: 'M64 33C75 22 87 17 102 17H140C149 17 155 21 160 33Z', p: 'M114 17V33', wh: [50, 150], wy: 59 },
    sedan: { b: 'M8 57C8 49 12 43 26 41L60 37C72 25 86 20 104 19H126C138 19 147 24 155 34L178 38C188 40 192 46 192 52V57Z', w: 'M68 37C78 27 90 24 104 24H125C134 24 140 28 146 37Z', p: 'M110 24V37', wh: [48, 156], wy: 57 },
  };
  function carArt(v, w = 200, o = {}) {
    const b = BODY[(v && v.silhouette) || o.body] || BODY.hatch;
    const col = v && v.colour ? `var(--dp-car-${v.colour})` : o.colour ? `var(--dp-car-${o.colour})` : 'var(--dp-text-3)';
    const wheels = b.wh.map((x) => `<circle cx="${x}" cy="${b.wy}" r="13" class="gar-art-wh" stroke="${col}"/><circle cx="${x}" cy="${b.wy}" r="4.5" fill="${col}" opacity=".7"/>`).join('');
    return `<svg class="gar-art ${o.cls || ''}" viewBox="0 0 200 80" width="${w}" height="${Math.round(w * 0.4)}" aria-hidden="true"><ellipse cx="100" cy="73" rx="84" ry="4.5" class="gar-art-sh"/><path class="gar-art-b" d="${b.b}" fill="${col}" fill-opacity=".2" stroke="${col}" stroke-width="2.4" stroke-linejoin="round"/><path d="${b.w}" fill="${col}" fill-opacity=".22" stroke="${col}" stroke-width="1.4" stroke-linejoin="round" opacity=".85"/><path d="${b.p}" stroke="${col}" stroke-width="1.4" opacity=".7"/>${wheels}</svg>`;
  }
  /** Per-car stats derived from DP_DATA.trips (driver trips only; passenger trips never count). */
  function carStats(id) {
    const list = D.trips.filter((t) => t.vehicleId === id && t.type === 'driver');
    const scored = list.filter((t) => t.smoothness);
    const eff = scored.filter((t) => t.efficiency);
    return { trips: list, scored, drives: list.length, km: list.reduce((a, t) => a + t.distanceKm, 0), durationS: list.reduce((a, t) => a + t.durationS, 0), avg: avgInt(scored.map((t) => t.smoothness.score)), better: eff.filter((t) => t.efficiency.rating === 'better').length, effN: eff.length, latest: scored[0] };
  }
  const POWER = { petrol: 'Petrol', diesel: 'Diesel', hybrid: 'Hybrid', ev: 'Electric' };
  /** Contributor band counts over a set of scored trips (derived; F-HIS-04 "contributor changes"). */
  function contribCounts(trips) {
    const ids = [['braking', 'Braking'], ['acceleration', 'Acceleration'], ['cornering', 'Cornering'], ['consistency', 'Consistency']];
    return ids.map(([id, label]) => {
      const c = { id, label, up: 0, same: 0, down: 0 };
      trips.forEach((t) => { const x = t.smoothness.contributors.find((k) => k.id === id); if (x) c[x.band === 'smoother' ? 'up' : x.band === 'rougher' ? 'down' : 'same']++; });
      return c;
    });
  }
  function contribRows(ctx, counts, n) {
    const { ui } = ctx;
    return counts.map((c) => {
      const seg = (k, v) => (v ? `<i class="sta-cb-${k}" style="flex:${v}"></i>` : '');
      const aria = `${c.label}: smoother than usual on ${c.up} of ${n} drives, usual on ${c.same}, rougher on ${c.down}.`;
      return `<div class="sta-cb" role="listitem" aria-label="${ui.esc(aria)}"><b>${ui.esc(c.label)}</b><span class="sta-cb-bar" aria-hidden="true">${seg('up', c.up)}${seg('same', c.same)}${seg('down', c.down)}</span><span class="sta-cb-n" aria-hidden="true"><em class="is-up">▲ ${c.up}</em><em>● ${c.same}</em><em>▼ ${c.down}</em></span></div>`;
    }).join('');
  }

  /* ==========================================================================================================
     History: filters + search model (S-HIS-01 / S-HIS-02)
     ========================================================================================================== */
  const EMPTY_F = () => ({ type: [], car: [], route: [], quality: [] });
  const TYPES = [['driver', 'Driver'], ['passenger', 'Passenger'], ['transit', 'Bus or train'], ['taxi', 'Taxi or rideshare'], ['other', 'Not a drive']];
  const QUAL = ['Good', 'Usable', 'Limited', 'Unscorable'];
  const fCount = (f) => (f ? f.type.length + f.car.length + f.route.length + f.quality.length : 0);
  function matchText(t, q) {
    if (!q) return true;
    const v = U.vehicle(t.vehicleId), r = t.routeId && U.route(t.routeId);
    const hay = [t.label, t.from, t.to, t.partOfDay, v && v.nickname, r && routeName(r), t.type === 'passenger' && 'passenger', ...t.events.map((e) => e.road + ' ' + e.place), ...t.stops.map((s) => s.road)].filter(Boolean).join(' ').toLowerCase();
    return hay.includes(q.toLowerCase());
  }
  function filterTrips(f, q, exclude) {
    return D.trips.filter((t) => {
      if (exclude && t.id === exclude) return false;
      if (f.type.length && !f.type.includes(t.type)) return false;
      if (f.car.length && !f.car.includes(t.vehicleId)) return false;
      if (f.route.length && !f.route.includes(t.routeId || 'none')) return false;
      if (f.quality.length && !f.quality.includes(t.quality.badge)) return false;
      return matchText(t, q);
    });
  }
  function fLabel(key, val) {
    if (key === 'type') return (TYPES.find((x) => x[0] === val) || [0, val])[1];
    if (key === 'car') return (U.vehicle(val) || { nickname: val }).nickname;
    if (key === 'route') return val === 'none' ? 'Not on a route' : routeName(U.route(val));
    return `${val} data`;
  }
  const HIS_STATE_F = {
    filtered: () => ({ ...EMPTY_F(), route: ['r_commute'], quality: ['Good'] }),
  };
  function hisFilter(ctx) {
    if (ctx.store.f) return ctx.store.f;
    if (HIS_STATE_F[ctx.state]) return HIS_STATE_F[ctx.state]();
    const f = EMPTY_F();
    if (ctx.params.car) f.car = [ctx.params.car];
    if (ctx.params.route) f.route = [ctx.params.route];
    return f;
  }

  /** Week-grouped trip cards (F-HIS-01.1). Returns section html per week, newest first. */
  function weekGroups(ctx, list, o = {}) {
    const { ui } = ctx;
    const groups = [];
    for (const t of list) {
      const r = U.weekOf(t.start), k = r ? r.weekStart : ymd(t.start);
      let g = groups.find((x) => x.k === k);
      if (!g) groups.push((g = { k, r, trips: [] }));
      g.trips.push(t);
    }
    let lockShown = false;
    return groups.map((g) => {
      const title = g.k === D.recaps[0].weekStart ? 'This week' : g.k === D.recaps[1].weekStart ? 'Last week' : g.r ? weekRange(g.r.weekStart, g.r.weekEnd) : U.fmtDate(g.k);
      const drv = g.trips.filter((t) => t.type === 'driver');
      const a = avgInt(drv.filter((t) => t.smoothness).map((t) => t.smoothness.score));
      const meta = `${plural(drv.length, 'drive')}${a != null ? ` · avg ${a}` : ''}`;
      let pre = '';
      if (!lockShown && !ctx.isPro && g.trips.some((t) => U.isLocked(t, ctx.ent))) {
        lockShown = true;
        pre = `<div class="ui-pad his-lockdiv" data-focus="lock">${ui.banner({ kind: 'info', icon: 'lock', title: 'Older than 30 days', text: 'Summaries, scores and Stories stay free forever. Replay and Analysis for these drives are in Pro.', action: { text: 'See plans', nav: 'S-SUB-03/replayAnalysis30Days' } })}</div>`;
      }
      const rows = g.trips.map((t) => {
        const focus = (o.focus === 'passenger' && t.type === 'passenger') || (o.focus === 'interrupted' && t.interrupted) ? ' data-focus="row"' : '';
        return `<div class="his-rowwrap" data-trip="${t.id}"${focus}>${ui.tripRow(t, { ent: ctx.ent })}${t.interrupted ? `<span class="his-note">${ui.icon('shield', 13)}${ui.esc(ctx.ios ? t.interrupted.reason : 'Android stopped DrivePerf in the background')}. Saved up to ${ctx.data.time(t.interrupted.at)}.</span>` : ''}${t.type === 'passenger' ? `<span class="his-note">${ui.icon('passenger', 13)}Passenger · not scored, not counted</span>` : ''}</div>`;
      }).join('');
      return pre + ui.section(title, `<div class="ui-card is-flush his-week" role="list">${rows}</div>`, { style: 'caption', meta });
    });
  }

  /* ==========================================================================================================
     S-HIS-01 · Drives list
     ========================================================================================================== */
  const SEARCH_SUGGEST = ['Gardiner', 'Niagara', 'Ioniq', 'Gym', 'Passenger'];
  function hisHeaderTools(ctx, f, q, o = {}) {
    const { ui } = ctx;
    const n = fCount(f);
    const search = ctx.store.searching || q
      ? `<div class="his-search is-on ${ctx.ios ? 'ui-glassy' : ''}">${ui.icon('search', 18)}<span class="his-search-q">${q ? ui.esc(q) : '<i>Search places, routes, cars</i>'}</span>${q || ctx.store.searching ? `<span class="his-search-x ui-press" ${ui.act({ action: 'clearSearch', label: 'Clear search' })}>${ui.icon('close', 16)}</span>` : ''}</div>`
      : `<div class="his-search ui-press ${ctx.ios ? 'ui-glassy' : ''}" ${ui.act({ action: 'search', label: 'Search drives', disabled: o.disabled })}>${ui.icon('search', 18)}<span class="his-search-q"><i>Search places, routes, cars</i></span></div>`;
    const sugg = ctx.store.searching && !q ? `<div class="his-sugg"><span class="his-sugg-k">Try</span>${SEARCH_SUGGEST.map((s) => ui.chip(s, { kind: 'outline', action: 'searchFor', arg: s })).join('')}</div>` : '';
    const active = [];
    for (const k of ['type', 'car', 'route', 'quality']) for (const v of f[k]) active.push(ui.chip('', { kind: 'filter', selected: true, html: `${k === 'car' ? ui.carDot(U.vehicle(v)) : ''}${ui.esc(fLabel(k, v))}${ui.icon('close', 13)}`, action: 'unfilter', arg: `${k}:${v}`, label: `Remove filter ${fLabel(k, v)}` }));
    const quick = n ? active.join('') + ui.chip('Clear', { kind: 'plain', action: 'clearFilters', cls: 'his-clear' })
      : [ui.chip('GTI', { kind: 'filter', icon: ui.carDot(U.vehicle('gti')), action: 'quick', arg: 'car:gti' }), ui.chip('Ioniq', { kind: 'filter', icon: ui.carDot(U.vehicle('ioniq')), action: 'quick', arg: 'car:ioniq' }), ui.chip('The Commute', { kind: 'filter', icon: 'routes', action: 'quick', arg: 'route:r_commute' }), ui.chip('Passenger', { kind: 'filter', icon: 'passenger', action: 'quick', arg: 'type:passenger' })].join('');
    const chips = `<div class="his-chips" role="toolbar" aria-label="Filters">${ui.chip(n ? `Filters · ${n}` : 'Filters', { kind: 'filter', selected: !!n, icon: 'filter', nav: 'S-HIS-02', label: n ? `Filters, ${n} applied` : 'Filters', disabled: o.disabled })}${o.disabled ? '' : quick}</div>`;
    return `<div class="his-tools">${search}${sugg}${chips}</div>`;
  }

  function hisRender(ctx) {
    const { ui, state, params } = ctx;
    const f = hisFilter(ctx), q = ctx.store.q || '';
    const filterBtn = ui.iconButton('filter', { nav: 'S-HIS-02', label: fCount(f) ? `Filters, ${fCount(f)} applied` : 'Filters', badge: fCount(f) > 0 });
    const navO = (sub) => ({ title: 'All drives', subtitle: sub, trailing: [filterBtn] });
    if (state === 'Load') {
      const sk = `<div class="his-tools"><div class="his-search ${ctx.ios ? 'ui-glassy' : ''}">${ui.icon('search', 18)}<span class="his-search-q"><i>Search places, routes, cars</i></span></div></div>
        ${['This week', 'Last week'].map((w) => ui.section(w, `<div class="ui-card is-flush his-week">${ui.skeleton('tripRow', 4)}</div>`, { style: 'caption' })).join('')}<p class="his-loadnote">Loading your drives…</p>`;
      return ui.page(`<div class="his" aria-busy="true">${sk}</div>`, { nav: navO('Loading…') });
    }
    if (state === 'Empty') {
      const status = ctx.android ? `<div class="ui-pad"><div class="his-capture">${ui.icon('drives', 20)}<span><b>Auto-detect on · ready</b><small>Last check-in ${ctx.data.ago(D.health.presets.allGood.lastCheckIn)}</small></span><span class="his-capture-go ui-press" data-start-drive role="button" tabindex="0" aria-label="Start drive">${ui.icon('record', 18)}Start</span></div></div>` : '';
      const body = `${hisHeaderTools(ctx, EMPTY_F(), '', { disabled: true })}<div class="ui-pad">${ui.card(ui.empty({ art: `<div class="his-empty-art">${ctx.icons.loop({ size: 64, color: 'var(--dp-accent)', dot: 'var(--dp-text)' })}</div>`, title: 'Your drives will appear here.', text: 'Just drive. DrivePerf notices when you set off and decodes each drive when you park.', secondary: { text: 'How capture works', nav: 'S-HOME-02' } }), { hero: true })}</div>${status}`;
      return ui.page(`<div class="his">${body}</div>`, { nav: { title: 'All drives', subtitle: 'No drives yet', trailing: [ui.iconButton('filter', { label: 'Filters', disabled: true })] } });
    }
    const list = filterTrips(f, q, params.deleted);
    const focus = state === 'passengerTripsGreyed' ? 'passenger' : state === 'interruptedLabel' ? 'interrupted' : null;
    const groups = weekGroups(ctx, list, { focus });
    let results = '';
    if (q || fCount(f)) {
      const drv = list.filter((t) => t.type === 'driver');
      results = `<p class="his-count" role="status">${ui.esc(`${plural(list.length, 'drive')}${q ? ` match “${q}”` : ''}`)}${drv.length ? ` · ${ui.num(ctx.data.km(drv.reduce((a, t) => a + t.distanceKm, 0)), 'km')}` : ''}</p>`;
    }
    const none = !list.length ? `<div class="ui-pad">${ui.card(ui.empty({ icon: 'filter', title: 'No drives match.', text: 'Try fewer filters, or a different place or car.', action: { text: 'Clear filters', kind: 'tonal', action: 'clearFilters' } }))}</div>` : '';
    let extra = '', fixed = '';
    const total = state === 's1000TripScroll' ? 1000 : list.length;
    if (state === 's1000TripScroll') {
      extra = ['Earlier · 21–27 Sep', '14–20 Sep', '7–13 Sep'].map((w) => ui.section(w, `<div class="ui-card is-flush his-week is-window">${ui.skeleton('tripRow', 5)}</div>`, { style: 'caption', meta: 'loading as you scroll' })).join('');
      fixed = `<div class="his-fast" aria-hidden="true"><span class="his-fast-track"></span><span class="his-fast-thumb"><b>Oct 2026</b></span></div>`;
    }
    const foot = list.length ? `<p class="his-foot">${ui.esc(state === 's1000TripScroll' ? 'Showing 1,000 drives. Older weeks load as you scroll.' : `That’s every drive since ${longDate(D.user.installDate)}.`)}</p>` : '';
    const body = `${hisHeaderTools(ctx, f, q)}${results}${none}${groups.map((g) => `<div class="his-b" data-rise>${g}</div>`).join('')}${extra}${foot}`;
    fixed += ui.menu('his-ctx', [{ text: 'Share', icon: 'share', nav: 'S-SHR-01' }, { text: 'Replay', icon: 'replay', nav: 'S-RPL-01' }, { text: 'Edit trip', icon: 'edit', nav: 'S-TRP-02' }, '-', { text: 'Delete drive', icon: 'trash', destructive: true, nav: 'S-TRP-03' }]);
    const sub = `${total.toLocaleString('en-CA')} ${total === 1 ? 'drive' : 'drives'}${fCount(f) || q ? ' · filtered' : ''}`;
    return ui.page(`<div class="his">${body}</div>`, { nav: navO(sub), fixed });
  }

  function hisMount(el, ctx) {
    ctx.ui.anim.rise(el, '.his-b[data-rise]', { stagger: 30, delay: 60 });
    if (!ctx.store.didFocus && ctx.state === 's1000TripScroll') { const sc = el.querySelector('[data-scroll]'); if (sc) sc.scrollTop = 1500; ctx.store.didFocus = true; }
    if (!ctx.store.didFocus) {
      const target = ctx.state === 'lockIndicatorsOnOlderTrips' ? el.querySelector('[data-focus="lock"]') : el.querySelector('[data-focus="row"]');
      if (target) { scrollToEl(el, target, ctx.ios ? 150 : 170); ctx.store.didFocus = true; }
    }
    // Long-press a trip card → contextual actions (Android M3E long-press menu · iOS context menu); locked trips route Replay to S-SUB-03.
    let timer, fired = false;
    const menu = el.querySelector('#his-ctx');
    const down = (e) => {
      const w = e.target.closest('.his-rowwrap'); if (!w || !menu) return;
      fired = false;
      timer = setTimeout(() => {
        fired = true; ctx.haptic('longPress');
        const t = U.trip(w.dataset.trip), locked = U.isLocked(t, ctx.ent);
        menu.querySelectorAll('[data-nav]').forEach((it) => {
          it.dataset.params = `trip=${t.id}`;
          if (it.textContent.includes('Replay')) { it.dataset.nav = locked ? 'S-SUB-03/replayAnalysis30Days' : 'S-RPL-01'; it.querySelector('span').textContent = locked ? 'Replay · Pro' : 'Replay'; }
          if (it.textContent.includes('Share')) it.hidden = t.type === 'passenger';
        });
        const host = el.querySelector('.ui-page'), hr = host.getBoundingClientRect(), wr = w.getBoundingClientRect(), k = hr.height / host.offsetHeight || 1;
        menu.style.top = `${Math.min((wr.bottom - hr.top) / k + 4, host.offsetHeight - 260)}px`;
        menu.hidden = false;
      }, 480);
    };
    const cancel = () => clearTimeout(timer);
    const click = (e) => { if (fired && e.target.closest('.his-rowwrap')) { e.stopPropagation(); e.preventDefault(); fired = false; } };
    el.addEventListener('pointerdown', down); el.addEventListener('pointerup', cancel); el.addEventListener('pointerleave', cancel); el.addEventListener('click', click, true);
    return () => { el.removeEventListener('pointerdown', down); el.removeEventListener('pointerup', cancel); el.removeEventListener('pointerleave', cancel); el.removeEventListener('click', click, true); };
  }
  function toggleIn(f, arg) { const [k, v] = arg.split(':'); const nf = { ...f, [k]: f[k].includes(v) ? f[k].filter((x) => x !== v) : [...f[k], v] }; return nf; }

  screen('S-HIS-01', {
    name: 'Drives list',
    wp: 'D', platforms: ['ios', 'android'], presentation: 'push', tab: 'drives',
    states: [
      { id: 'default', label: 'Def' },
      { id: 'Empty', label: 'Empty' },
      { id: 'Load', label: 'Load' },
      { id: 'filtered', label: 'filtered (The Commute · Good data)' },
      { id: 'passengerTripsGreyed', label: 'passenger trips greyed' },
      { id: 'interruptedLabel', label: 'interrupted label' },
      { id: 'lockIndicatorsOnOlderTrips', label: 'Lock indicators on older trips (Free)' },
      { id: 's1000TripScroll', label: '1,000-trip scroll (simulated windowing)' },
    ],
    stateParams: { lockIndicatorsOnOlderTrips: { ent: 'free' } },
    backTo: 'S-HOME-01',
    render: hisRender,
    mount: hisMount,
    actions: {
      search: (ctx) => { ctx.store.searching = true; ctx.update(); return false; },
      searchFor: (ctx, q) => { ctx.store.q = q; ctx.store.searching = false; ctx.update(); return false; },
      clearSearch: (ctx) => { ctx.store.q = ''; ctx.store.searching = false; ctx.update(); return false; },
      quick: (ctx, arg) => { ctx.store.f = toggleIn(hisFilter(ctx), arg); ctx.update(); return false; },
      unfilter: (ctx, arg) => { ctx.store.f = toggleIn(hisFilter(ctx), arg); ctx.update(); return false; },
      clearFilters: (ctx) => { ctx.store.f = EMPTY_F(); ctx.update(); return false; },
    },
    spec: {
      purpose: 'Every drive in one scrollable list, newest first and grouped by week, so the user can find any trip (F-HIS-01). Summaries are free forever; on Free, drives older than 30 days keep their full summary and only show a small lock for Replay & Analysis (F-HIS-03 “locked, not lost”).',
      layout: [
        'Top bar: iOS inline title “All drives” + subtitle “{n} drives” (· filtered) with glass back and a glass Filter button (badge dot when filters are applied) · Android MediumFlexibleTopAppBar (title in content, collapses on scroll), back arrow, filter icon.',
        'Search: iOS glass search field (radius 18, 44 tall) / Android M3 SearchBar (56 dp, surface-container-high, full radius) “Search places, routes, cars”. Tapping shows “Try” suggestion chips (Gardiner · Niagara · Ioniq · Gym · Passenger); a query shows “{n} drives match “{q}” · {km} km”.',
        'Filter chip row (horizontal scroll, 16 margins): “Filters” chip (→ S-HIS-02) then quick filters (GTI · Ioniq · The Commute · Passenger). Applied filters become selected chips with × plus “Clear”.',
        'Week sections, newest first: caption header “This week” / “Last week” / “19–25 Oct” with meta “{n} drives · avg {score}” (driver drives only), then one card (radius 24 iOS / 16 Android) of trip cards (components.md §12) separated by 0.5 px separators (iOS) or 2 dp gaps (Android).',
        'Trip card: route glyph 48 · label · part of day · date · km · min · quality glyph · car dot (more than one car) · score badge (mini ring iOS / Cookie9Sided Android) · lock glyph after the badge for locked older trips. Interrupted and passenger rows add a one-line note under the card.',
        'Free: an info banner “Older than 30 days” precedes the first week with locked drives (See plans → S-SUB-03 replayAnalysis30Days). Summaries are never blurred.',
        'Footer caption “That’s every drive since 28 September.” · iOS capture accessory + floating tab bar remain (Drives tab).',
        '1,000 drives: list is windowed (rows outside the viewport render as skeleton rows), with a fast-scroll thumb and a month bubble on the trailing edge (iOS section index scrubber · Android fast scroller).',
      ],
      components: ['page', 'navBar', 'iconButton', 'chip', 'carDot', 'section', 'card', 'tripRow', 'scoreBadge', 'qualityBadge', 'banner', 'menu', 'empty', 'skeleton'],
      interactions: [
        'Trip card → S-TRP-01 (iOS zoom / Android container transform from the card). Locked older trips still open S-TRP-01 (summary); Replay/Analysis there open S-SUB-03.',
        'Long-press a trip card (Android M3E long-press · iOS context menu; iOS swipe: leading Share, trailing Delete) → Share (S-SHR-01) · Replay (S-RPL-01, or S-SUB-03/replayAnalysis30Days when locked) · Edit trip (S-TRP-02) · Delete drive (S-TRP-03).',
        'Filter button / “Filters” chip → S-HIS-02 (medium sheet); “Show n drives” returns here with the filter applied as chips. Quick chips toggle a filter in place; chip × removes it; “Clear” resets.',
        'Search field → suggestions; a suggestion runs the search over route label, places, roads, route and car names. × clears.',
        'Empty: “How capture works” → S-HOME-02; Android shows the capture row inline with Start (iOS uses the accessory).',
        'Share/Replay from the long-press menu pass the D1 gate (held on S-DRV-05 while moving).',
      ],
      motion: [
        'Push in from Home “All drives”: iOS push (spring.default), Android shared axis X.',
        'Hero motion: week sections rise 8 px and fade in, 30 ms stagger (spring.default). RM: 220 ms cross-fade, no rise.',
        'Filter change: list cross-fades (180 ms); chip selection uses spring.fast + selection haptic. Long-press menu scales in from the card (spring.fast) with the longPress haptic.',
      ],
      a11y: [
        'Trip card is one button: “Home to Work, Thursday morning, 5 November. 37.9 kilometres, 30 minutes. Smoothness 91. Good data.” + “Replay and Analysis in Pro.” when locked. Custom actions: Share, Replay, Edit, Delete.',
        'Week headers are headings (“This week, 6 drives, average 86”). Filter chips are a toolbar; selected chips announce “Remove filter The Commute”.',
        'Result count is a status region (“8 drives match Gardiner”). The filter button announces “Filters, 2 applied”.',
        'XL: trip cards stack the glyph above the text; meta wraps to two lines; chips wrap onto multiple rows; nothing truncates.',
        'Passenger rows are dimmed to 55% but keep 4.5:1 for their label; the note “Passenger · not scored, not counted” carries the meaning in text.',
      ],
      platformDiffs: {
        ios: 'NavigationStack push; .searchable(placement: .navigationBarDrawer) glass field; trip cards in an inset-grouped List section per week; swipe actions (leading Share, trailing Delete) and .contextMenu; floating glass tab bar + capture accessory stay visible; fast scroll via the section index scrubber.',
        android: 'MediumFlexibleTopAppBar + M3 SearchBar; LazyColumn with sticky week headers and segmented ListItems; combinedClickable long-press → DropdownMenu; FilterChips (selected = secondaryContainer); fast scroller thumb with a month bubble; NavigationBar stays (no capture toolbar on non-root tabs).',
      },
      analytics: ['history_opened {source: home|car_detail}', 'history_filtered {filter: car|route|quality|type, count_bucket}', 'history_searched {result_bucket}', 'trip_context_action {action: share|replay|edit|delete, locked}'],
      copy: {
        Title: 'All drives',
        'Search placeholder': 'Search places, routes, cars',
        'Week meta': '{n} drives · avg {score}',
        'Lock divider': 'Older than 30 days. Summaries, scores and Stories stay free forever. Replay and Analysis for these drives are in Pro. · See plans',
        'Interrupted note': '{iOS ended DrivePerf in the background | Android stopped DrivePerf in the background}. Saved up to {time}.',
        'Passenger note': 'Passenger · not scored, not counted',
        Empty: 'Your drives will appear here. Just drive. DrivePerf notices when you set off and decodes each drive when you park.',
        'No match': 'No drives match. Try fewer filters, or a different place or car. · Clear filters',
        Footer: 'That’s every drive since {install date}.',
      },
      freePro: 'Free: every summary of every age is visible; the 4 drives older than 30 days show a lock glyph after the score and the “Older than 30 days” banner; their Replay/Analysis (long-press menu or trip detail) open S-SUB-03 replayAnalysis30Days. Pro / Preview / Ride-along: no locks.',
      data: 'All 30 DP_DATA.trips, newest first, grouped by DP_DATA.util.weekOf (recap weeks). Week meta is derived (driver drives; average of scored drives). Filtered = route r_commute + Good data (9 drives). Passenger row t20261031-1905; interrupted row t20261013-1742 (the states scroll to them). Lock state = ent free (t20261002-0745, t20260929-*, t20260928-1842 locked). 1,000-trip scroll is simulated: the fixture has 30 drives, older weeks render as skeleton (windowed) rows. Delete from S-TRP-03 returns here with ?deleted=<trip> (the row is removed).',
      icons: ['search', 'filter', 'close', 'routes', 'passenger', 'shield', 'lock', 'share', 'replay', 'edit', 'trash', 'record', 'drives'],
      assets: ['logo-a-loop.svg'],
    },
  });

  /* ==========================================================================================================
     S-HIS-02 · Filter sheet
     ========================================================================================================== */
  screen('S-HIS-02', {
    name: 'Filter sheet',
    wp: 'D', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'drives',
    over: (ctx) => (ctx.state === 'filtersApplied' ? 'S-HIS-01/filtered' : 'S-HIS-01'),
    detent: 'medium',
    states: [{ id: 'default', label: 'Def' }, { id: 'filtersApplied', label: 'filters applied (The Commute · Good data)' }],
    render(ctx) {
      const { ui } = ctx;
      const c = caller(ctx);
      const f = ctx.store.f || (ctx.state === 'filtersApplied' ? HIS_STATE_F.filtered() : c && c.store.f ? c.store.f : c && c.id === 'S-HIS-01' && c.state === 'filtered' ? HIS_STATE_F.filtered() : EMPTY_F());
      const n = filterTrips(f).length;
      const count = (k, v) => filterTrips({ ...EMPTY_F(), [k]: [v] }).length;
      const group = (title, key, opts) => `<section class="hf-g"><h3>${ui.esc(title)}</h3><div class="hf-chips">${opts.map(([v, label, icon]) => { const k = count(key, v); return ui.chip('', { kind: 'filter', selected: f[key].includes(v), html: `${f[key].includes(v) ? ui.icon('check', 14) : icon || ''}${ui.esc(label)}<span class="hf-n">${k}</span>`, action: 'tog', arg: `${key}:${v}`, label: `${label}, ${k} drives${f[key].includes(v) ? ', selected' : ''}`, haptic: 'selection' }); }).join('')}</div></section>`;
      const body = [
        group('Trip type', 'type', TYPES.map(([v, l]) => [v, l, v === 'passenger' ? ui.icon('passenger', 14) : ''])),
        group('Car', 'car', D.vehicles.map((v) => [v.id, v.nickname, ui.carDot(v)])),
        group('Route', 'route', [...D.routes.map((r) => [r.id, routeName(r), '']), ['none', 'Not on a route', '']]),
        group('Data quality', 'quality', QUAL.map((q) => [q, q, I.quality(q, { size: 14 })])),
      ].join('');
      const foot = `<div class="hf-foot">${ui.button('Reset', { kind: ctx.ios ? 'glass' : 'outline', action: 'reset', disabled: !fCount(f) })}${ui.button(n ? `Show ${plural(n, 'drive')}` : 'No drives match', { kind: 'filled', block: true, action: 'apply', disabled: !n, haptic: 'tap' })}</div>`;
      return `${ui.sheetHeader('Filter drives', { sub: fCount(f) ? `${plural(fCount(f), 'filter')} · ${plural(n, 'drive')}` : `All ${plural(D.trips.length, 'drive')}` })}<div class="hf">${body}</div>${foot}`;
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '.hf-g', { stagger: 30 }); },
    actions: {
      tog: (ctx, arg) => {
        const c = caller(ctx);
        const base = ctx.store.f || (ctx.state === 'filtersApplied' ? HIS_STATE_F.filtered() : c && c.store.f ? c.store.f : c && c.state === 'filtered' ? HIS_STATE_F.filtered() : EMPTY_F());
        ctx.store.f = toggleIn(base, arg); ctx.update(); return false;
      },
      reset: (ctx) => { ctx.store.f = EMPTY_F(); ctx.update(); return false; },
      apply: (ctx) => {
        const c = caller(ctx);
        const f = ctx.store.f || (ctx.state === 'filtersApplied' ? HIS_STATE_F.filtered() : (c && c.store.f) || EMPTY_F());
        returnTo(ctx, { f, didFocus: true }); return false;
      },
    },
    spec: {
      purpose: 'Narrow the drives list by trip type, car, route and data quality (F-HIS-01.3). The applied filters come back to S-HIS-01 as chips.',
      layout: [
        'Medium sheet (iOS glass sheet 54% detent, radius 40 inset 8 · Android ModalBottomSheet, surface-container-low, top radius 28, drag handle). Header “Filter drives” + sub “All 30 drives” / “2 filters · 8 drives”; iOS glass close.',
        'Four groups, 18 apart, each a caption title then wrapping chips: Trip type (Driver · Passenger · Bus or train · Taxi or rideshare · Not a drive) · Car (dot + nickname) · Route (named routes, Route 4, Not on a route) · Data quality (quality glyph + Good/Usable/Limited/Unscorable). Each chip shows its drive count in text-3.',
        'Sticky footer (glass on iOS, surface on Android): Reset (glass / outlined) + primary “Show {n} drives” (filled, block). The count updates live; with no match the button reads “No drives match” and is disabled.',
      ],
      components: ['sheetHeader', 'chip', 'carDot', 'qualityBadge', 'button'],
      interactions: [
        'Chip tap toggles that filter (multi-select within a group, AND across groups); selected chips show a check (M3 FilterChip leading check).',
        '“Show n drives” → dismiss and apply to S-HIS-01 (chips + “filtered” subtitle). Reset clears all. Drag down / scrim / close → dismiss without applying.',
      ],
      motion: ['Sheet: spring.sheet up from the bottom; detent snap = selection haptic.', 'Hero motion: groups rise 8 px, 30 ms stagger. Chip select: spring.fast colour/shape change + selection haptic. RM: cross-fades only.'],
      a11y: ['Focus moves to “Filter drives” on open and returns to the filter button on close.', 'Chips read “The Commute, 10 drives, selected” (toggle buttons). Groups are headings.', 'XL: the sheet opens at the large detent; chips wrap; the footer stays pinned and its label wraps.'],
      platformDiffs: { ios: 'presentationDetents([.medium, .large]) glass sheet; chips are glass capsules; selected = accent container; Reset is a glass button.', android: 'ModalBottomSheet with drag handle; M3 FilterChip (8 dp radius, leading check when selected, secondaryContainer); OutlinedButton Reset + filled Show.' },
      analytics: ['history_filter_opened', 'history_filtered {filter, count_bucket}', 'history_filter_reset'],
      copy: { Title: 'Filter drives', Sub: 'All {n} drives / {k} filters · {n} drives', Groups: 'Trip type · Car · Route · Data quality', CTA: 'Show {n} drives / No drives match', Reset: 'Reset' },
      freePro: 'Same for every plan: filtering summaries is free.',
      data: 'Counts are derived live from DP_DATA.trips (e.g. Passenger 1, Ioniq 4, The Commute 10, Limited 1). filtersApplied = route r_commute + Good (9 drives). Bus/train, taxi and “not a drive” have 0 in the fixture.',
      icons: ['check', 'passenger', 'close'],
      assets: [],
    },
  });

  /* ==========================================================================================================
     S-TRP-02 · Edit trip sheet
     ========================================================================================================== */
  const TRIP_TYPES = [
    { id: 'driver', label: 'Driver', sub: 'Scored and counted toward your baseline, routes and recaps.' },
    { id: 'passenger', label: 'Passenger', sub: 'Not scored. Stays in your history.' },
    { id: 'transit', label: 'Bus or train', sub: 'Moved to “Not drives”. Kept 30 days, then deleted.' },
    { id: 'taxi', label: 'Taxi or rideshare', sub: 'Not scored. Stays in your history.' },
    { id: 'other', label: 'Other (not driving)', sub: 'Moved to “Not drives”. Kept 30 days, then deleted.' },
  ];
  function editHeader(ctx, title, o = {}) {
    const { ui } = ctx;
    const lead = o.back ? ui.iconButton('back', { svg: ui.icon(ctx.ios ? 'chevronLeft' : 'arrowBack', 22), state: 'default', label: 'Back to Edit trip', cls: 'ed-x' }) : ui.iconButton('close', { back: true, label: 'Cancel', cls: 'ed-x' });
    const ok = ctx.ios ? `<span class="ed-ok ui-press" ${ui.act({ action: o.action || 'save', label: o.okLabel || 'Save', haptic: 'confirm' })}>${ui.icon('check', 20)}</span>` : ui.button(o.okLabel || 'Save', { kind: 'text', action: o.action || 'save', cls: 'ed-okA' });
    return `<div class="ui-sheet-h ed-h">${lead}<div class="ed-h-t"><h2>${ui.esc(title)}</h2>${o.sub ? `<p>${ui.esc(o.sub)}</p>` : ''}</div>${ok}</div>`;
  }
  const radioLead = (on) => `<span class="ui-radio ${on ? 'is-on' : ''}"></span>`;
  function tripSummary(ctx, t) {
    const { ui, data } = ctx;
    return `<div class="ed-sum">${ui.glyph(t.glyph, 44)}<span class="ed-sum-t"><b>${ui.esc(t.label)}</b><small>${ui.esc(t.partOfDay)} · ${data.date(t.start)}</small><small>${ui.num(t.distanceKm, 'km')} · ${ui.num(Math.round(t.durationS / 60), 'min')}</small></span>${ui.scoreBadge(t)}</div>`;
  }
  /** Distance (km) along the track up to time t (haversine; derived for the split preview). */
  function kmAt(t, until) {
    let d = 0, prev = null;
    for (const r of t.track.rows) { if (r[0] > until) break; if (r[1] == null) { continue; } if (prev) { const R = 6371, dLa = (r[1] - prev[1]) * Math.PI / 180, dLn = (r[2] - prev[2]) * Math.PI / 180, a = Math.sin(dLa / 2) ** 2 + Math.cos(prev[1] * Math.PI / 180) * Math.cos(r[1] * Math.PI / 180) * Math.sin(dLn / 2) ** 2; d += 2 * R * Math.asin(Math.sqrt(a)); } prev = r; }
    return d;
  }

  screen('S-TRP-02', {
    name: 'Edit trip sheet',
    wp: 'D', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'drives',
    detent: 'large',
    states: [
      { id: 'default', label: 'Def' },
      { id: 'typePicker', label: 'type picker (driver, passenger, bus/train, taxi, other)' },
      { id: 'carPicker', label: 'car picker' },
      { id: 'mergeSplit', label: 'merge/split (P2)' },
    ],
    stateParams: { default: { trip: 't20261105-1745' }, typePicker: { trip: 't20261105-1745' }, carPicker: { trip: 't20261105-1745' }, mergeSplit: { trip: 't20261024-1610' } },
    render(ctx) {
      const { ui, data, state } = ctx;
      const t = data.trip(ctx.params.trip) || data.trip('t20261105-1745');
      const type = ctx.store.type || t.type, car = ctx.store.car || t.vehicleId;
      const v = U.vehicle(car);
      const tt = TRIP_TYPES.find((x) => x.id === type);
      const changed = type !== t.type || car !== t.vehicleId;
      if (state === 'typePicker') {
        const rows = TRIP_TYPES.map((x) => ui.row({ lead: radioLead(x.id === type), title: x.label, sub: x.sub, action: 'pickType', arg: x.id, trailing: x.id === t.type ? '<span class="ed-cur">Current</span>' : '', haptic: 'selection' }));
        const note = type !== 'driver' ? ui.banner({ kind: 'passenger', icon: type === 'passenger' ? 'passenger' : 'info', title: `${tt.label}: not a drive you made`, text: 'Your scores, baseline, route records and recaps recompute without it, within 2 seconds.' }) : '';
        return `${editHeader(ctx, 'Trip type', { back: true, sub: t.label })}<div class="ed">${ui.list(rows, { cls: 'ed-list' })}${note}<p class="ed-foot">Correcting a drive teaches DrivePerf what your drives look like. It never leaves your phone.</p></div>`;
      }
      if (state === 'carPicker') {
        const rows = D.vehicles.map((c) => ui.row({ lead: radioLead(c.id === car), titleHtml: `${ui.carDot(c)} ${ui.esc(c.nickname)}${c.default ? ' <span class="ed-cur">Default</span>' : ''}`, sub: `${c.year} ${c.make} ${c.model}`, action: 'pickCar', arg: c.id, trailing: `<span class="ed-art">${carArt(c, 64)}</span>`, haptic: 'selection' }));
        rows.push(ui.row({ icon: 'plus', title: 'Add a car', sub: ctx.isPro ? 'Unlimited cars with Pro' : 'Keep a log for every car with Pro', nav: ctx.isPro ? 'S-GAR-03' : 'S-SUB-03/s2ndCar', trailing: ctx.isPro ? null : 'lock' }));
        const note = car !== t.vehicleId ? `<p class="ed-foot">${ui.esc(`Moves this drive to the ${v.nickname}. Per-car stats and the Story tint update.`)}</p>` : `<p class="ed-foot">Drives go to your default car unless you change them here or on the Story.</p>`;
        return `${editHeader(ctx, 'Car', { back: true, sub: t.label })}<div class="ed">${ui.list(rows, { cls: 'ed-list' })}${note}</div>`;
      }
      if (state === 'mergeSplit') {
        const near = D.trips.filter((x) => x.id !== t.id && (Math.abs(new Date(x.start) - new Date(t.end)) < 1800e3 || Math.abs(new Date(t.start) - new Date(x.end)) < 1800e3));
        const mid = t.stops.filter((x) => x.t > t.durationS * 0.15 && x.t < t.durationS * 0.85);
        const stops = (mid.length ? mid : t.stops).slice().sort((a, b) => b.durationS - a.durationS).slice(0, 3).sort((a, b) => a.t - b.t);
        const centre = stops.reduce((bi, x, i) => (Math.abs(x.t - t.durationS / 2) < Math.abs(stops[bi].t - t.durationS / 2) ? i : bi), 0);
        const pick = ctx.store.split != null ? ctx.store.split : centre;
        const st = stops[pick];
        const f = st.t / t.durationS, k1 = kmAt(t, st.t);
        const bar = `<div class="ed-split" role="img" aria-label="${ui.esc(`Split at ${st.road}: part 1 ${Math.round(st.t / 60)} minutes, part 2 ${Math.round((t.durationS - st.t) / 60)} minutes.`)}"><span style="flex:${f.toFixed(3)}"><b>Part 1</b>${ui.num(data.km(k1), 'km')} · ${ui.num(Math.round(st.t / 60), 'min')}</span><i aria-hidden="true">${ui.icon('pin', 14)}</i><span style="flex:${(1 - f).toFixed(3)}"><b>Part 2</b>${ui.num(data.km(t.distanceKm - k1), 'km')} · ${ui.num(Math.round((t.durationS - st.t) / 60), 'min')}</span></div>`;
        const srows = stops.map((s, i) => ui.row({ lead: radioLead(i === pick), title: `Stop on ${s.road}`, sub: `${Math.round(s.t / 60)} min in · stopped ${s.durationS} s`, action: 'pickSplit', arg: i, trailing: '', haptic: 'selection' }));
        const merge = near.length ? ui.list(near.map((x) => ui.row({ icon: 'layers', title: `Merge with ${x.label}`, sub: `${x.partOfDay} · ${data.date(x.start)}`, toast: 'Merged' }))) : `<div class="ed-none">${ui.icon('info', 18)}<span><b>Nothing to merge</b><small>Merging joins two drives less than 30 minutes apart. No drive is that close to this one.</small></span></div>`;
        return `${editHeader(ctx, 'Merge or split', { back: true, sub: t.label, action: 'saveSplit', okLabel: 'Split' })}<div class="ed">
          <section class="ed-sec"><h3>Split at a stop</h3>${bar}${ui.list(srows, { cls: 'ed-list' })}<p class="ed-foot">Each part gets its own score and Story. The stop becomes the end of part 1 and the start of part 2.</p></section>
          <section class="ed-sec"><h3>Merge</h3>${merge}</section></div>`;
      }
      const rows = [
        ui.row({ icon: type === 'driver' ? 'drives' : type === 'passenger' ? 'passenger' : 'info', title: 'Trip type', value: ui.esc(tt.label), state: 'typePicker', sub: type === 'driver' ? 'Scored' : 'Not scored' }),
        ui.row({ lead: `<span class="ui-row-ic is-neutral">${ui.carDot(v)}</span>`, title: 'Car', value: ui.esc(v.nickname), state: 'carPicker', sub: `${v.year} ${v.make} ${v.model}` }),
        ui.row({ icon: 'layers', title: 'Merge or split', sub: 'Join a drive that was cut in two, or split one at a stop', state: 'mergeSplit' }),
      ];
      const info = changed ? ui.banner({ kind: 'info', icon: 'info', title: 'Save to apply', text: 'Scores, baseline, route records and recaps recompute within 2 seconds.' }) : '';
      return `${editHeader(ctx, 'Edit trip', { sub: `${t.partOfDay} · ${data.date(t.start)}` })}<div class="ed">${tripSummary(ctx, t)}${ui.list(rows, { cls: 'ed-list' })}${info}
        <p class="ed-foot">Fix a drive after the fact: you were a passenger, it was a bus, or it was the other car.</p>
        ${ui.list([ui.row({ icon: 'trash', iconTone: 'critical', title: 'Delete drive', destructive: true, nav: 'S-TRP-03', params: { trip: t.id }, trailing: '' })], { cls: 'ed-list' })}</div>`;
    },
    mount(el, ctx) { ctx.ui.anim.rise(el, '.ed > *', { stagger: 30 }); },
    actions: {
      pickType: (ctx, id) => { ctx.store.type = id; ctx.update(); return false; },
      pickCar: (ctx, id) => { ctx.store.car = id; ctx.update(); return false; },
      pickSplit: (ctx, i) => { ctx.store.split = +i; ctx.update(); return false; },
      saveSplit: (ctx) => { ctx.toast('Split into 2 drives', { icon: 'check' }); returnTo(ctx, {}); return false; },
      save: (ctx) => {
        const t = ctx.data.trip(ctx.params.trip) || ctx.data.trip('t20261105-1745');
        const type = ctx.store.type || t.type, car = ctx.store.car || t.vehicleId;
        const msg = type !== t.type ? (type === 'passenger' ? 'Marked as passenger · scores updated' : `Marked as ${TRIP_TYPES.find((x) => x.id === type).label.toLowerCase()} · scores updated`) : car !== t.vehicleId ? `Moved to the ${U.vehicle(car).nickname} · stats updated` : 'No changes';
        ctx.toast(msg, { icon: 'check', action: type !== t.type || car !== t.vehicleId ? 'Undo' : null });
        ctx.haptic('confirm');
        returnTo(ctx, {}); return false;
      },
    },
    spec: {
      purpose: 'Fix a drive after the fact — I was a passenger, it was a bus, wrong car — or delete it, so history and scores stay accurate (F-HIS-02, F-GAR-02, F-CAP-04). From the Story chips it takes ≤ 2 taps (Q8).',
      layout: [
        'Large sheet (iOS full-height sheet with its own navigation: glass × leading, title + sub centred, glass-prominent ✓ trailing · Android ModalBottomSheet at full height with × leading and a “Save” text button trailing).',
        'Default: trip summary row (glyph 44, label, part of day · date · km · min, score badge) · grouped list: Trip type (value “Driver”, sub Scored/Not scored) › · Car (car dot, value nickname, sub year make model) › · Merge or split › · caption “Fix a drive after the fact…” · separate list with destructive “Delete drive”.',
        'Type picker (pushed inside the sheet, back chevron): 5 radio rows — Driver · Passenger · Bus or train · Taxi or rideshare · Other (not driving) — each with its consequence as supporting text; “Current” tag on the saved type. Choosing a non-driver type shows the passenger banner “not a drive you made… recompute within 2 seconds”.',
        'Car picker: radio rows per car (dot + nickname, Default tag, year make model, 64-wide silhouette in its colour) + “Add a car” (Pro: S-GAR-03 · Free: lock + PRO → S-SUB-03 s2ndCar).',
        'Merge or split (P2): split bar (part 1 / part 2 with km · min, stop pin between) · radio list of the 3 longest stops · caption; Merge section shows candidates < 30 min apart or the “Nothing to merge” note.',
      ],
      components: ['sheetHeader', 'iconButton', 'button', 'list', 'row', 'radios', 'glyph', 'scoreBadge', 'carDot', 'banner', 'toast'],
      interactions: [
        'Trip type / Car / Merge or split rows → in-sheet sub-pages (states typePicker / carPicker / mergeSplit); back chevron returns to the main page.',
        'Radio rows select (selection haptic). ✓ / Save → dismiss to the caller (S-TRP-01, S-STY-01 f1, S-HIS-01) with a toast “Marked as passenger · scores updated · Undo” (confirm haptic). × / drag down discards.',
        'Delete drive → S-TRP-03 dialog. Add a car → S-GAR-03 (Pro) / S-SUB-03 s2ndCar (Free). Split ✓ → toast “Split into 2 drives”.',
        'Story f1 chip entry (S-STY-01) deep-links straight to typePicker or carPicker.',
      ],
      motion: ['Sheet: spring.sheet; iOS large detent recedes the parent. Sub-pages push inside the sheet (spring.default / shared axis X).', 'Hero motion: rows rise 8 px, 30 ms stagger. Radio fill: spring.fast. RM: cross-fades.'],
      a11y: ['Radio rows are single-choice buttons: “Passenger. Not scored. Stays in your history. Not selected.”', 'Save announces the toast text. Focus returns to the invoking control (Edit button / car chip) on close.', 'XL: sub text wraps under titles; the car silhouette hides below 320 pt of text width; split bar labels stack.', 'Meaning never by colour: the passenger banner has the glyph and text; the destructive row is labelled “Delete drive”.'],
      platformDiffs: { ios: 'Sheet with NavigationStack; toolbar ✕ (cancellationAction) and ✓ (confirmationAction, glassProminent); Picker rows as inset-grouped list with checkmark-style radios.', android: 'ModalBottomSheet (skipPartiallyExpanded) with top row: close IconButton, title, TextButton “Save”; RadioButton ListItems; sub-pages use shared axis X inside the sheet.' },
      analytics: ['trip_corrected {to: passenger|transit|taxi|other|driver, source: trip_detail|story|history}', 'trip_car_changed {source}', 'trip_split', 'trips_merged'],
      copy: {
        Title: 'Edit trip', Types: 'Driver · Passenger · Bus or train · Taxi or rideshare · Other (not driving)',
        'Non-driver banner': '{type}: not a drive you made. Your scores, baseline, route records and recaps recompute without it, within 2 seconds.',
        'Transit sub': 'Moved to “Not drives”. Kept 30 days, then deleted.',
        'Car note': 'Moves this drive to the {car}. Per-car stats and the Story tint update.',
        Toast: 'Marked as passenger · scores updated · Undo',
        'Nothing to merge': 'Merging joins two drives less than 30 minutes apart. No drive is that close to this one.',
      },
      freePro: 'Editing is Free. Adding a car from the car picker on Free (1 car) opens S-SUB-03 s2ndCar; cars kept from the Preview stay selectable.',
      data: 'Default trip t20261105-1745 (Work → Home, GTI, 85); mergeSplit uses t20261024-1610 (Niagara-on-the-Lake → Home, 73 min, Ioniq). Split candidates = the 3 longest stops in the middle 70% of that trip (default = the one nearest the middle); part distances are derived from the track (haversine). Merge candidates = trips starting/ending within 30 min of this one (none in the fixture).',
      icons: ['close', 'check', 'chevronLeft', 'drives', 'passenger', 'layers', 'trash', 'plus', 'lock', 'pin', 'info'],
      assets: [],
    },
  });

  /* ==========================================================================================================
     S-TRP-03 · Delete trip confirmation
     ========================================================================================================== */
  screen('S-TRP-03', {
    name: 'Delete trip confirmation',
    wp: 'D', platforms: ['ios', 'android'], presentation: 'dialog', tab: 'drives',
    over: (ctx) => ({ id: 'S-TRP-01', params: { trip: ctx.params.trip || 't20261105-1745' } }),
    states: [{ id: 'default', label: 'Def' }, { id: 'withCloudBackupNote', label: 'with cloud backup note (Pro backup on)' }],
    stateParams: { default: { trip: 't20261105-1745' }, withCloudBackupNote: { trip: 't20261105-1745' } },
    render(ctx) {
      const { ui, data } = ctx;
      const t = data.trip(ctx.params.trip) || data.trip('t20261105-1745');
      const backup = ctx.state === 'withCloudBackupNote';
      const body = `<div class="del-what">${ui.glyph(t.glyph, 30)}<span><b>${ui.esc(t.label)}</b><small>${ui.esc(t.partOfDay)} · ${data.date(t.start)}</small></span></div>${backup ? `<p class="del-cloud">${ui.icon('cloud', 16)}<span>It’s also removed from your cloud backup at the next sync, within 24 hours.</span></p>` : ''}`;
      return ui.dialog({
        title: 'Delete this drive?', icon: 'trash',
        text: 'Its route, score, Story and raw sensor data are removed from this phone. This can’t be undone.',
        body,
        actions: [{ text: 'Cancel', kind: 'cancel', back: true }, { text: 'Delete', kind: 'destructive', action: 'del' }],
      });
    },
    actions: {
      del: (ctx) => {
        const id = ctx.params.trip || 't20261105-1745';
        ctx.toast('Drive deleted', { icon: 'trash' });
        DP.router.open(`#S-HIS-01?deleted=${encodeURIComponent(id)}`, { animate: true });
        return false;
      },
    },
    spec: {
      purpose: 'Confirm a real, irreversible deletion of one drive and its raw data (F-HIS-02.3, F-PRV-04). Deletion is deletion, not hiding.',
      layout: [
        'iOS alert (glass, 300 wide, radius 34): title “Delete this drive?” · message · the drive (glyph 30, label, part of day · date) · cloud note when backup is on · buttons side by side: Cancel (bold) | Delete (destructive red).',
        'Android M3 dialog (312 wide, radius 28, surface-container-high): trash icon (secondary), centred headline, supporting text, the drive row, the cloud note, right-aligned text buttons Cancel · Delete (error colour).',
      ],
      components: ['dialog', 'glyph'],
      interactions: ['Cancel / scrim → back to the caller (S-TRP-02 or S-TRP-01, or the swiped list).', 'Delete → removes the drive (≤ 5 s on device), toast “Drive deleted”, lands on S-HIS-01 without the row.'],
      motion: ['Dialog: iOS scale 1.12 → 1 + fade (spring.fast); Android scale .9 → 1 (spring.fast). RM: fade.', 'Hero motion: the deleted row collapses out of S-HIS-01 (height 0 + fade, spring.default); RM: fade.'],
      a11y: ['Alert dialog role; focus lands on the title, then Cancel (the safe default). Delete is announced “Delete, destructive”.', 'XL: buttons stack vertically; message wraps; the drive row wraps under the glyph.'],
      platformDiffs: { ios: '.alert with a .destructive “Delete” and .cancel role; the drive row is a custom message view (confirmationDialog when started from a swipe).', android: 'AlertDialog with icon, title, text and TextButtons (Delete in colorScheme.error).' },
      analytics: ['trip_deleted {source: edit|swipe|detail, backup_on}'],
      copy: { Title: 'Delete this drive?', Message: 'Its route, score, Story and raw sensor data are removed from this phone. This can’t be undone.', 'Cloud note': 'It’s also removed from your cloud backup at the next sync, within 24 hours.', Buttons: 'Cancel · Delete', Toast: 'Drive deleted' },
      freePro: 'Same for every plan. The cloud note only appears when Pro cloud backup is on.',
      data: 'Trip t20261105-1745. The fixture user has backup off (privacy.backup.state = off); withCloudBackupNote simulates backup = upToDate.',
      icons: ['trash', 'cloud'],
      assets: [],
    },
  });

  /* ==========================================================================================================
     S-STA-01 · Insights (weekly stats)
     ========================================================================================================== */
  function weekStrip(ctx, days, asOfYmd) {
    const { ui } = ctx;
    return `<div class="sta-days" aria-hidden="true">${days.map((d, i) => {
      const drives = d.drives.filter((x) => !asOfYmd || d.date <= asOfYmd);
      const future = asOfYmd ? d.date > asOfYmd : false;
      const sc = drives.filter((x) => x.score != null).map((x) => x.score);
      return `<span class="sta-day ${future ? 'is-future' : ''} ${d.date === asOfYmd ? 'is-today' : ''}"><em>${sc.length ? Math.max(...sc) : ''}</em><span class="sta-day-b">${drives.slice(0, 3).map((x) => `<i class="${x.score == null ? 'is-ns' : ''}"></i>`).join('')}</span><b>${DOW1[i]}</b></span>`;
    }).join('')}</div>`;
  }
  function trend4(ctx, pts, o = {}) {
    const { ui } = ctx;
    const vals = pts.filter((p) => p.avg != null).map((p) => p.avg);
    const lo = Math.min(75, Math.floor((Math.min(...vals) - 3) / 5) * 5), hi = Math.max(95, Math.ceil((Math.max(...vals) + 3) / 5) * 5);
    const n = pts.length;
    const series = [{ pts: pts.map((p, i) => ({ x: n === 1 ? 0.5 : i / (n - 1), y: p.avg, label: p.avg != null ? String(p.avg) : null, cls: i === n - 1 ? 'is-last' : '', empty: p.avg == null })), cls: 'is-accent' }];
    const aria = `Four-week trend of your weekly average Smoothness: ${pts.map((p) => `week of ${longDate(p.weekStart)}, ${p.avg != null ? p.avg : 'no drives'}`).join('; ')}.`;
    return lineChart(ui, { w: 338, h: 132, lo, hi, ticks: [80, 90].filter((v) => v > lo && v < hi), series, xLabels: pts.map((p, i) => ({ x: n === 1 ? 0.5 : i / (n - 1), text: i === n - 1 ? (o.lastLabel || 'This week') : U.fmtDate(p.weekStart), anchor: i === 0 ? 'start' : i === n - 1 ? 'end' : 'middle', cls: i === n - 1 ? 'is-on' : '' })), emptyAt: lo + 4, label: aria, padL: 22, padR: 14 });
  }

  function staRender(ctx) {
    const { ui, data, state } = ctx;
    const free = !ctx.isPro;
    let rc = D.recaps[0], prev = D.recaps[1], weekTrips, asOf = data.today, asOfYmd = '2026-11-06';
    let calTrip = null;
    if (state === 'calibrating') { calTrip = data.trip('t20260929-0738'); rc = D.recaps[5]; prev = null; asOf = new Date(calTrip.end); asOfYmd = '2026-09-29'; }
    const empty = state === 'emptyWeek';
    const ws = data.weekSummary(asOf, rc.weekStart);
    weekTrips = empty ? [] : ws.trips;
    const scored = weekTrips.filter((t) => t.smoothness);
    const avgW = empty ? null : state === 'calibrating' ? avgInt(scored.map((t) => t.smoothness.score)) : rc.avgSmoothness;
    const delta = !empty && prev && avgW != null ? avgW - prev.avgSmoothness : null;
    const range = weekRange(rc.weekStart, rc.weekEnd);
    // ---- period selector ----
    const lockI = free ? 'lock' : null;
    const seg = ui.segmented([{ id: 'week', label: 'Week' }, { id: 'month', label: 'Month', icon: lockI }, { id: 'year', label: 'Year', icon: lockI }], { selected: 'week', action: 'period', cls: 'sta-seg' });
    // ---- hero week card ----
    let chip = '';
    if (state === 'calibrating') chip = ui.deltaChip(calTrip.smoothness);
    else if (delta != null) chip = ui.chip('', { kind: delta > 0 ? 'up' : delta < 0 ? 'down' : 'same', html: `<b aria-hidden="true">${delta > 0 ? '▲' : delta < 0 ? '▼' : '●'}</b>${delta === 0 ? 'Level with last week' : `${data.signed(delta)} vs last week`}`, cls: 'ui-delta' });
    const why = !empty && rc.downExplanation && state !== 'calibrating' ? `<span class="sta-why ui-press" ${ui.act({ nav: 'S-TRP-01', params: { trip: 't20261104-0650' }, label: rc.downExplanation.replace(' →', '') })}>${ui.esc(rc.downExplanation.replace(' →', ''))}${ui.icon('chevronRight', 14)}</span>` : '';
    const heroAria = empty ? `This week, ${range}: no drives.` : `This week, ${range}: ${plural(ws.drives, 'drive')}, ${data.km(ws.km)} kilometres, ${data.min(ws.durationS)}. Average Smoothness ${avgW}${delta ? `, ${Math.abs(delta)} ${delta > 0 ? 'up' : 'down'} on last week` : ''}${state === 'calibrating' ? '. Calibrating your baseline, 2 of 3' : ''}.`;
    const heroTop = empty
      ? `<div class="sta-hero-e">${ui.artMerge(96)}<b>${ui.esc(D.recapExamples.emptyWeek.line)}</b><span>Your averages pick up again with your next drive.</span></div>`
      : `<div class="sta-hero-b"><div><div class="sta-big">${ui.num(avgW ?? '—')}</div><div class="sta-lbl">Average Smoothness</div>${state === 'calibrating' ? '' : `<div class="sta-chip">${chip}</div>`}</div>${weekStrip(ctx, rc.days, asOfYmd)}</div>${state === 'calibrating' ? `<div>${chip}</div>` : ''}${why}`;
    const tiles = `<div class="ui-stats sta-tiles" style="--cols:3">${tile(ui, { label: 'Drives', html: ui.num(empty ? 0 : ws.drives), aria: `Drives, ${empty ? 0 : ws.drives}`, prov: 'observed' })}${tile(ui, { label: 'Distance', html: ui.num(data.km(empty ? 0 : ws.km), 'km'), aria: `Distance, ${data.km(empty ? 0 : ws.km)} kilometres. Derived`, prov: 'derived' })}${tile(ui, { label: 'Time', html: durHtml(ui, empty ? 0 : ws.durationS), aria: `Time, ${data.min(empty ? 0 : ws.durationS)}`, prov: 'observed' })}</div>`;
    const hero = ui.card(`<div class="sta-hero" aria-label="${ui.esc(heroAria)}"><div class="sta-hero-h"><b>This week</b><span>${range}</span></div>${heroTop}${tiles}</div>`, { hero: true, cls: 'sta-herocard' });
    const blocks = [`<div class="ui-pad sta-segwrap">${seg}</div>`, `<div class="ui-pad">${hero}</div>`];
    // ---- 4-week trend ----
    if (state === 'calibrating') {
      blocks.push(ui.section('4-week trend', `<div class="ui-pad">${ui.card(`<div class="sta-cal"><span class="ui-dots3 sta-cal-d">${[0, 1, 2].map((i) => `<i class="${i < calTrip.smoothness.calibrating.k ? 'on' : ''}"></i>`).join('')}</span><div><b>Calibrating your baseline · 2 of 3</b><p>Your trend starts after 3 scored drives. Until then there’s nothing to compare against, so we won’t guess.</p></div></div>`)}</div>`, { style: 'caption' }));
    } else {
      const base = D.recaps[0].trend4w.map((x) => ({ ...x }));
      if (empty) base[base.length - 1].avg = null;
      blocks.push(ui.section('4-week trend', `<div class="ui-pad">${ui.card(`<div class="sta-trend">${trend4(ctx, base)}<p class="sta-cap">Weekly average Smoothness. Passenger and unscored drives don’t count.</p></div>`, { cls: 'sta-trendcard' })}</div>`, { style: 'caption', action: { text: free ? 'More in Pro' : 'Month', nav: free ? 'S-SUB-03/trends' : 'S-STA-02' } }));
    }
    // ---- contributor changes ----
    if (!empty && state !== 'calibrating' && scored.length) {
      const cc = contribCounts(scored);
      blocks.push(ui.section('What shaped this week', `<div class="ui-pad">${ui.card(`<div class="sta-cbs" role="list">${contribRows(ctx, cc, scored.length)}</div><p class="sta-cap">Across ${plural(scored.length, 'scored drive')}. ▲ smoother than usual · ● usual · ▼ rougher.</p>`)}</div>`, { style: 'caption' }));
    }
    // ---- focus ----
    if (!empty && state !== 'calibrating' && rc.focus) {
      const fz = rc.focus;
      const meter = `<span class="sta-meter" aria-hidden="true">${fz.meter3w.map((v, i) => `<span><b class="sta-meter-t"><i style="height:${Math.max(8, v * 100)}%"></i></b><em>${i === 2 ? 'This wk' : `${2 - i} wk ago`}</em></span>`).join('')}</span>`;
      blocks.push(ui.section('Your focus', `<div class="ui-pad">${ui.card(`<div class="sta-focus"><div><span class="sta-k">${ui.icon('sparkle', 15)}One habit at a time</span><b>${ui.esc(fz.habit.charAt(0).toUpperCase() + fz.habit.slice(1))}</b><p>Gentler in ${ui.num(fz.gentler)} of ${ui.num(fz.of)} drives this week.</p></div>${meter}</div>`, { nav: 'S-SCO-05', label: `${fz.line} Opens focus details.` })}</div>`, { style: 'caption' }));
    }
    // ---- recap + wrapped ----
    if (state !== 'calibrating') {
      const lr = data.latestCompleteRecap();
      const recap = ui.card(`<div class="sta-rc"><span class="sta-rc-art">${ui.icon('story', 22)}</span><div><b>${ui.esc(lr.notification.title)}</b><span>${weekRange(lr.weekStart, lr.weekEnd)} · ${plural(lr.drives, 'drive')} · Smoothness ${ui.num(lr.avgSmoothness)} (${lr.deltaText})</span></div>${ui.icon('chevronRight', 16)}</div>`, { nav: 'S-RCP-01', label: `Weekly recap, ${weekRange(lr.weekStart, lr.weekEnd)}. ${lr.drives} drives, Smoothness ${lr.avgSmoothness}, ${lr.deltaText}. Play.`, cls: 'sta-rccard' });
      const wm = D.wrapped.monthly;
      const wrap = ui.card(`<div class="sta-rc"><span class="sta-rc-art is-wrap">${ui.icon('sparkle', 22)}</span><div><b>${ui.esc(wm.notification.title)}</b><span>${ui.esc(wm.notification.body)}</span></div>${ui.icon('chevronRight', 16)}</div>`, { nav: 'S-WRP-01', label: `${wm.notification.title} ${wm.notification.body} Play.`, cls: 'sta-rccard' });
      blocks.push(ui.section('Recaps', `<div class="ui-pad sta-rcs">${recap}${wrap}</div>`, { style: 'caption' }));
    }
    // ---- Pro trends (Free: locked, not lost) ----
    if (state !== 'calibrating') {
      if (free) {
        const oct = D.trips.filter((t) => t.smoothness && t.type === 'driver' && ymd(t.start).startsWith('2026-10'));
        const inner = `<div class="sta-lockin">${lineChart(ui, { w: 320, h: 120, lo: 70, hi: 95, ticks: [80, 90], series: [{ pts: oct.slice().reverse().map((t, i, a) => ({ x: i / (a.length - 1), y: t.smoothness.score })), cls: 'is-accent', r: 3 }], label: 'Your October Smoothness' })}<div class="sta-lockrows"><span>${ui.carDot(U.vehicle('gti'))}GTI · ${carStats('gti').avg}</span><span>${ui.carDot(U.vehicle('ioniq'))}Ioniq · ${carStats('ioniq').avg}</span><span>The Commute · 76 → 91</span></div></div>`;
        const rows = ui.list([
          ui.row({ icon: 'insights', title: 'Month and year', sub: 'Smoothness, contributors, distance and time', trailing: 'lock', nav: 'S-SUB-03/trends' }),
          ui.row({ icon: 'car', title: 'By car', sub: 'GTI and Ioniq side by side', trailing: 'lock', nav: 'S-SUB-03/trends' }),
          ui.row({ icon: 'routes', title: 'By route', sub: 'Every route’s trend and record', trailing: 'lock', nav: 'S-SUB-03/trends' }),
        ]);
        blocks.push(ui.section('Trends', `<div class="ui-pad">${ui.lockPanel(inner, { text: D.entitlement.lockSheets.trends, nav: 'S-SUB-03/trends' })}</div><div style="height:10px"></div>${rows}`, { style: 'caption', footer: 'Your data is all here. Longer trends are part of Pro.' }));
      } else {
        const g = carStats('gti'), io = carStats('ioniq'), rr = U.route('r_commute');
        blocks.push(ui.section('Trends', ui.list([
          ui.row({ icon: 'insights', title: 'Month', sub: `October · ${plural(19, 'drive')} · average ${D.wrapped.monthly.chapters[1].line.replace('Average Smoothness ', '')}`, nav: 'S-STA-02' }),
          ui.row({ icon: 'clock', title: 'Year', sub: '2026 so far · since 28 Sep', nav: 'S-STA-02/year' }),
          ui.row({ icon: 'car', title: 'By car', subHtml: true, sub: `${ui.carDot(U.vehicle('gti'))} GTI ${g.avg} · ${ui.carDot(U.vehicle('ioniq'))} Ioniq ${io.avg}`, nav: 'S-STA-02/perCar' }),
          ui.row({ icon: 'routes', title: 'By route', sub: `${rr.name} ${rr.trend[0].score} → ${rr.trend[rr.trend.length - 1].score}`, nav: 'S-STA-02/perRoute' }),
        ]), { style: 'caption' }));
      }
    }
    const sub = ctx.android ? (state === 'calibrating' ? 'Week of 28 September' : 'Week of 2 November') : null;
    return ui.page(`<div class="sta">${blocks.map((b) => `<div class="sta-b" data-rise>${b}</div>`).join('')}</div>`, { large: { title: 'Insights', subtitle: sub, trailing: rootTrailing(ctx, [], state === 'calibrating' ? 2 : null) } });
  }

  screen('S-STA-01', {
    name: 'Insights (weekly stats)',
    wp: 'D', platforms: ['ios', 'android'], presentation: 'root', tab: 'insights',
    states: [
      { id: 'default', label: 'Def' },
      { id: 'calibrating', label: 'calibrating (Preview day 2, 2 of 3)' },
      { id: 'emptyWeek', label: 'Empty week' },
      { id: 'lockOnMonthYearCarRoute', label: 'Lock on Month/Year/Car/Route (Free)' },
    ],
    stateParams: { calibrating: { ent: 'preview' }, lockOnMonthYearCarRoute: { ent: 'free' } },
    render: staRender,
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '.sta-b[data-rise]', { stagger: 40 });
      drawPaths(ctx, el, '.sta-trendcard .dch-line', { duration: 1100, delay: 200 });
      popAll(ctx, el, '.sta-trendcard .dch-dot', { delay: 700, stagger: 60 });
    },
    actions: {
      period: (ctx, id) => {
        if (id === 'week') return false;
        if (!ctx.isPro) { ctx.nav('S-SUB-03/trends'); return false; }
        ctx.nav(id === 'year' ? 'S-STA-02/year' : 'S-STA-02');
        return false;
      },
    },
    spec: {
      purpose: 'The Insights tab: a calm weekly view of driving and Smoothness — this week vs last, a 4-week line, what shaped the week, the current focus and the recap/Wrapped cards (F-HIS-04). Month, year, per-car and per-route trends are Pro and shown “locked, not lost” on Free (F-HIS-05, Gate 2 OQ-07).',
      layout: [
        'Large title “Insights” (iOS 34 bold · Android LargeFlexibleTopAppBar with “Week of 2 November” subtitle); trailing Preview badge (when active) + avatar.',
        'Period selector: Week · Month · Year (iOS glass segmented, 36 tall · Android connected button group, 40 dp). On Free, Month and Year carry a lock glyph.',
        'Hero card (radius 32 / 28): “This week · 2–8 Nov”; average Smoothness (DP Numerals 48) + “Average Smoothness” + comparison chip (“▼ −1 vs last week”, neutral, never red); 7-day strip (dots per drive, best score above, today outlined, future days hollow); explain-don’t-blame link “Down 1. Wednesday’s drive pulled it down. Here’s why ›”; 3 stat tiles Drives · Distance · Time with provenance glyphs.',
        '“4-week trend” card: weekly averages as a Glide line with value labels, this week as the ringed last point; caption “Weekly average Smoothness. Passenger and unscored drives don’t count.” Section action “Month ›” (Pro) / “More in Pro ›” (Free).',
        '“What shaped this week”: four contributor rows (Braking · Acceleration · Cornering · Consistency), each a 3-part bar (▲ smoother / ● usual / ▼ rougher) with counts in text.',
        '“Your focus” card → S-SCO-05: habit name, “Gentler in 3 of 5 drives this week.”, a 3-week meter (share of gentler drives, not a streak).',
        '“Recaps”: latest weekly recap card → S-RCP-01 and the October Wrapped card → S-WRP-01.',
        '“Trends”: Pro = list rows Month · Year · By car · By route → S-STA-02 states. Free = lock panel with the user’s own October trend, car averages and route trend blurred 14 px + “See how your driving changes month to month with Pro.” + See plans, then the same rows with lock + PRO → S-SUB-03 trends.',
      ],
      components: ['largeTitle', 'avatar', 'previewBadge', 'segmented', 'card', 'chip', 'deltaChip', 'stats', 'section', 'list', 'row', 'lockPanel', 'artMerge', 'carDot'],
      interactions: [
        'Month / Year segment → S-STA-02 (Month) / S-STA-02/year (Pro) · S-SUB-03/trends (Free). Trend rows → S-STA-02 default/year/perCar/perRoute (Pro) or S-SUB-03/trends (Free).',
        '“Here’s why ›” → S-TRP-01 for the drive that pulled the week down (t20261104-0650). Focus card → S-SCO-05. Recap card → S-RCP-01. Wrapped card → S-WRP-01 (Recap/Wrapped pass the D1 gate).',
        'Avatar → S-ACC-01; Preview badge → S-SUB-01. No paywall is ever auto-opened here.',
      ],
      motion: ['Hero motion: the 4-week line draws on (1100 ms, ease.drawOn) and its points pop (shared.pinPop, 60 ms stagger); blocks rise 8 px, 40 ms stagger. RM: line and points appear complete (220 ms fade).', 'Segmented change: selection haptic; tab switch cross-fade (iOS) / fade-through (Android).'],
      a11y: [
        'Hero card is one element: “This week, 2–8 Nov: 6 drives, 132.1 kilometres, 2 h 9 min. Average Smoothness 86, 1 down on last week.”',
        'Trend chart summary: “Four-week trend of your weekly average Smoothness: week of 12 October, 85; … week of 2 November, 86.” (iOS Audio Graph descriptor; Android “View as table”).',
        'Contributor rows: “Braking: smoother than usual on 2 of 5 drives, usual on 1, rougher on 2.” Glyphs ▲ ● ▼ + text carry meaning, never colour alone.',
        'XL: hero stacks the number above the day strip; tiles go to 2 columns; segmented labels stay on one line (icons drop); lock panel text wraps.',
        'No streak or “don’t break it” language anywhere (D7); the focus meter is a share, not a chain.',
      ],
      platformDiffs: { ios: 'Large title + glass toolbar avatar; glass segmented Picker; cards opaque --dp-surface (radius 24/32); lists inset-grouped; floating tab bar + capture accessory.', android: 'LargeFlexibleTopAppBar with subtitle; connected ButtonGroup with selection morph; hero card surface-container-high radius 28; segmented ListItems; NavigationBar (no capture toolbar on this tab).' },
      analytics: ['insights_opened {state}', 'insights_period_tapped {period: month|year, locked}', 'insights_dimension_tapped {dimension: car|route, locked}', 'locked_content_viewed {type: trends}'],
      copy: {
        Title: 'Insights',
        Hero: 'This week · {range} · Average Smoothness · ▼ −1 vs last week',
        'Down explanation': 'Down 1. Wednesday’s drive pulled it down. Here’s why ›',
        'Trend caption': 'Weekly average Smoothness. Passenger and unscored drives don’t count.',
        Calibrating: 'Calibrating your baseline · 2 of 3. Your trend starts after 3 scored drives. Until then there’s nothing to compare against, so we won’t guess.',
        'Empty week': 'No drives this week. Your car got a rest. Your averages pick up again with your next drive.',
        Lock: 'See how your driving changes month to month with Pro. (entitlement.lockSheets.trends) · Your data is all here. Longer trends are part of Pro.',
        Focus: 'One habit at a time · Braking into stops · Gentler in 3 of 5 drives this week.',
      },
      freePro: 'Free: Week view + 4-week trend, contributors, focus, recap and Wrapped cards in full. Month/Year segments, the Trends rows and the section action open S-SUB-03 trends; the Trends panel blurs the user’s own October data. Pro/Preview/Ride-along: everything opens S-STA-02.',
      data: 'Default: recaps[0] (week 2 Nov: 6 drives · 132.1 km · 2 h 9 min · 86, −1 vs recaps[1] 87, trend4w 85/85/87/86, focus 3 of 5); hero numbers via weekSummary (derived, equal to the recap). Contributor counts derived from the 5 scored drives. Calibrating: as of t20260929-0738 (2 of 3), week of 28 Sep, ent preview day 2. Empty week: recapExamples.emptyWeek with this week’s point hollow (simulated). Lock: ent free. Latest recap card = latestCompleteRecap (26 Oct, 87, up 2); Wrapped = wrapped.monthly.',
      icons: ['insights', 'lock', 'chevronRight', 'sparkle', 'story', 'car', 'routes', 'clock'],
      assets: [],
    },
  });

  /* ==========================================================================================================
     S-STA-02 · Trend detail (Pro)
     ========================================================================================================== */
  function quart(a) { const s = a.slice().sort((x, y) => x - y); const q = (p) => s[Math.min(s.length - 1, Math.max(0, Math.round(p * (s.length - 1))))]; return [q(0.25), q(0.75)]; }
  /** Welch two-sample t-test with a small-sample critical value (placeholder for A12's significance rule). */
  function welch(a, b) {
    const ma = mean(a), mb = mean(b), va = a.reduce((s, x) => s + (x - ma) ** 2, 0) / (a.length - 1), vb = b.reduce((s, x) => s + (x - mb) ** 2, 0) / (b.length - 1);
    const se = Math.sqrt(va / a.length + vb / b.length), t = (mb - ma) / se;
    const df = (va / a.length + vb / b.length) ** 2 / ((va / a.length) ** 2 / (a.length - 1) + (vb / b.length) ** 2 / (b.length - 1));
    const crit = 1.96 + 2.4 / df + 3 / (df * df);
    return { ma, mb, t, df, pass: a.length >= 4 && b.length >= 4 && Math.abs(t) > crit };
  }
  const monthTrips = (m) => D.trips.filter((t) => t.type === 'driver' && ymd(t.start).startsWith(m));

  function staMonth(ctx) {
    const { ui, data } = ctx;
    const list = monthTrips('2026-10'), sc = list.filter((t) => t.smoothness).slice().reverse();
    const a = avgInt(sc.map((t) => t.smoothness.score));
    const band = quart(sc.filter((t) => t.smoothness.baseline).map((t) => t.smoothness.baseline));
    const day = (t) => (+ymd(t.start).slice(8) - 1) / 30;
    const wk = D.recaps.filter((r) => r.weekStart >= '2026-10-05' && r.weekStart <= '2026-10-26').reverse();
    const chart = lineChart(ui, { w: 338, h: 170, lo: 70, hi: 95, ticks: [75, 80, 85, 90], band, series: [
      { pts: sc.map((t) => ({ x: day(t), y: t.smoothness.score, cls: t.id === 't20261014-0744' ? 'is-rec' : '' })), line: false, dotCls: 'is-faint', r: 3.5 },
      { pts: wk.map((r) => ({ x: (+r.weekStart.slice(8) + 2) / 30, y: r.avgSmoothness, label: String(r.avgSmoothness) })), cls: 'is-accent' },
    ], xLabels: [{ x: 0, text: '1 Oct', anchor: 'start' }, { x: 14 / 30, text: '15 Oct' }, { x: 1, text: '31 Oct', anchor: 'end' }], label: `October 2026: ${list.length} drives, average Smoothness ${a}. Weekly averages ${wk.map((r) => r.avgSmoothness).join(', ')}. Your usual range is ${band[0]} to ${band[1]}. Smoothest drive 89 on 14 October.` });
    const cc = contribCounts(sc);
    const better = sc.filter((t) => t.efficiency && t.efficiency.rating === 'better').length;
    const dom = D.wrapped.monthly.chapters.find((c) => c.id === 'driveOfMonth');
    const domT = data.trip(dom.tripId);
    return [
      `<div class="ui-pad">${ui.card(`<div class="st2-hero"><div><span class="st2-k">October 2026</span><div class="sta-big">${ui.num(a)}</div><div class="sta-lbl">Average Smoothness · first full month</div></div><div class="st2-legend"><span><i class="is-line"></i>Weekly average</span><span><i class="is-dot"></i>Each drive</span><span><i class="is-band"></i>Your usual range</span></div></div>${chart}`, { hero: true, cls: 'st2-chartcard' })}</div>`,
      `<div class="ui-pad"><div class="ui-stats" style="--cols:3">${tile(ui, { label: 'Drives', html: ui.num(list.length), aria: `Drives, ${list.length}`, prov: 'observed' })}${tile(ui, { label: 'Distance', html: ui.num(Math.round(list.reduce((x, t) => x + t.distanceKm, 0)), 'km'), aria: `Distance, ${Math.round(list.reduce((x, t) => x + t.distanceKm, 0))} kilometres. Derived`, prov: 'derived' })}${tile(ui, { label: 'Time', html: durHtml(ui, list.reduce((x, t) => x + t.durationS, 0)), aria: `Time, ${data.min(list.reduce((x, t) => x + t.durationS, 0))}`, prov: 'observed' })}</div></div>`,
      ui.section('Contributors', `<div class="ui-pad">${ui.card(`<div class="sta-cbs" role="list">${contribRows(ctx, cc, sc.length)}</div><p class="sta-cap">Across ${plural(sc.length, 'scored drive')} in October.</p>`)}</div>`, { style: 'caption' }),
      ui.section('Efficiency habits', ui.list([ui.row({ icon: 'bolt', title: `Better than usual on ${better} of ${sc.length} drives`, subHtml: true, sub: '<span class="dp-est">estimated</span> · from stops, starts and steady speed', nav: 'S-SCO-06', params: { trip: sc[sc.length - 1].id } })]), { style: 'caption', footer: 'Smoother habits like these usually help fuel use and range. Check your car’s display for your number.' }),
      ui.section('Smoothest drive', `<div class="ui-card is-flush st2-trips">${ui.tripRow(domT, { ent: ctx.ent })}</div>`, { style: 'caption' }),
    ];
  }
  function staYear(ctx) {
    const { ui, data } = ctx;
    const M = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const months = M.map((m, i) => { const l = monthTrips(`2026-${String(i + 1).padStart(2, '0')}`); const s = l.filter((t) => t.smoothness); return { m, n: l.length, km: l.reduce((a, t) => a + t.distanceKm, 0), avg: avgInt(s.map((t) => t.smoothness.score)) }; });
    const all = D.trips.filter((t) => t.type === 'driver'), sc = all.filter((t) => t.smoothness);
    const bars = `<div class="st2-year" role="img" aria-label="${ui.esc(`2026 by month. ${months.filter((x) => x.n).map((x) => `${x.m}: ${x.n} drives, average ${x.avg}`).join('. ')}. No drives before you started on 28 September.`)}">${months.map((x, i) => `<span class="st2-mo ${x.n ? '' : 'is-none'} ${i === 10 ? 'is-now' : ''} ${i > 10 ? 'is-future' : ''}"><em>${x.avg ?? ''}</em><span class="st2-mo-b"><i style="height:${x.avg ? Math.max(8, ((x.avg - 70) / 25) * 100) : 0}%"></i></span><b>${x.m.charAt(0)}</b><small>${x.n ? x.n : ''}</small></span>`).join('')}</div><p class="sta-cap">Bars = monthly average Smoothness · numbers under = drives. You started on 28 September; November is so far.</p>`;
    return [
      `<div class="ui-pad">${ui.card(`<div class="st2-hero"><div><span class="st2-k">2026 so far</span><div class="sta-big">${ui.num(avgInt(sc.map((t) => t.smoothness.score)))}</div><div class="sta-lbl">Average Smoothness · since 28 Sep</div></div></div>${bars}`, { hero: true, cls: 'st2-chartcard' })}</div>`,
      `<div class="ui-pad"><div class="ui-stats" style="--cols:3">${tile(ui, { label: 'Drives', html: ui.num(all.length), aria: `Drives, ${all.length}`, prov: 'observed' })}${tile(ui, { label: 'Distance', html: ui.num(Math.round(all.reduce((a, t) => a + t.distanceKm, 0)), 'km'), aria: `Distance, ${Math.round(all.reduce((a, t) => a + t.distanceKm, 0))} kilometres`, prov: 'derived' })}${tile(ui, { label: 'Time', html: durHtml(ui, all.reduce((a, t) => a + t.durationS, 0)), aria: `Time, ${data.min(all.reduce((a, t) => a + t.durationS, 0))}`, prov: 'observed' })}</div></div>`,
      ui.section('Month by month', ui.list(months.filter((x) => x.n).reverse().map((x) => ui.row({ title: `${x.m === 'Sep' ? 'September' : x.m === 'Oct' ? 'October' : 'November'}${x.m === 'Nov' ? ' (so far)' : ''}`, sub: `${plural(x.n, 'drive')} · ${Math.round(x.km)} km`, value: `<b class="ui-num st2-v">${x.avg}</b>`, nav: x.m === 'Oct' ? 'S-STA-02' : null, trailing: x.m === 'Oct' ? null : `<b class="ui-num st2-v">${x.avg}</b>` }))), { style: 'caption', footer: 'September was your calibration month, so it isn’t compared.' }),
    ];
  }
  function staCar(ctx) {
    const { ui, data } = ctx;
    const cars = D.vehicles.map((v) => ({ v, s: carStats(v.id) }));
    const weeks = D.recaps.slice().reverse();
    const series = cars.map(({ v }, k) => ({ pts: weeks.map((r, i) => { const l = D.trips.filter((t) => t.vehicleId === v.id && t.smoothness && t.type === 'driver' && ymd(t.start) >= r.weekStart && ymd(t.start) <= r.weekEnd); return { x: i / (weeks.length - 1), y: avgInt(l.map((t) => t.smoothness.score)) }; }), cls: k ? 'is-alt' : 'is-accent', dash: k ? '5 4' : null, dotCls: k ? 'is-alt' : '' }));
    const chart = lineChart(ui, { w: 338, h: 160, lo: 70, hi: 95, ticks: [75, 80, 85, 90], series, xLabels: weeks.map((r, i) => ({ x: i / (weeks.length - 1), text: U.fmtDate(r.weekStart), anchor: i === 0 ? 'start' : i === weeks.length - 1 ? 'end' : 'middle' })).filter((_, i) => i % 2 === 0 || i === weeks.length - 1), label: `Weekly average Smoothness by car. ${cars.map(({ v, s }) => `${v.nickname}: average ${s.avg} over ${s.drives} drives`).join('. ')}.` });
    const legend = `<div class="st2-legend is-row">${cars.map(({ v }, k) => `<span>${ui.carChip(v)}<i class="${k ? 'is-alt' : 'is-line'}"></i></span>`).join('')}</div>`;
    const cards = cars.map(({ v, s }) => ui.card(`<div class="st2-car">${carArt(v, 92)}<div><b>${ui.esc(v.nickname)}</b><span>${plural(s.drives, 'drive')} · ${ui.num(Math.round(s.km), 'km')} · ${data.min(s.durationS)}</span><span class="dp-est">Efficiency habits better than usual on ${s.better} of ${s.effN}</span></div><span class="st2-car-n">${ui.num(s.avg)}<small>avg</small></span></div>`, { nav: 'S-GAR-02', params: { car: v.id }, tint: `car-${v.colour}`, label: `${v.nickname}: ${s.drives} drives, ${Math.round(s.km)} kilometres, average Smoothness ${s.avg}. Opens car details.` })).join('');
    return [
      `<div class="ui-pad">${ui.card(`<div class="st2-hero"><div><span class="st2-k">By car · since 28 Sep</span><p class="st2-p">Each car is compared with your own usual in it. Car colours tint the cards, never the data.</p></div></div>${legend}${chart}`, { hero: true, cls: 'st2-chartcard' })}</div>`,
      `<div class="ui-pad ui-stack" style="gap:10px">${cards}</div>`,
    ];
  }
  function staRoute(ctx) {
    const { ui } = ctx;
    const r = U.route('r_commute'), rh = U.route('r_commute_home');
    const t0 = new Date('2026-09-29').getTime(), t1 = new Date('2026-11-05').getTime();
    const X = (d) => (new Date(d).getTime() - t0) / (t1 - t0);
    const recIds = new Set(r.recordHistory.map((x) => x.date));
    const chart = lineChart(ui, { w: 338, h: 170, lo: 70, hi: 95, ticks: [75, 80, 85, 90], series: [
      { pts: r.recordHistory.map((x) => ({ x: X(x.date), y: x.score })), cls: 'is-rec', step: true, dots: false },
      { pts: r.trend.map((x) => ({ x: X(x.date), y: x.score, cls: recIds.has(x.date) ? 'is-rec' : '' })), cls: 'is-accent' },
      { pts: rh.trend.map((x) => ({ x: X(x.date), y: x.score })), cls: 'is-alt', dash: '5 4', dotCls: 'is-alt', r: 3 },
    ], xLabels: [{ x: 0, text: '29 Sep', anchor: 'start' }, { x: X('2026-10-16'), text: '16 Oct' }, { x: 1, text: '5 Nov', anchor: 'end' }], label: `Route trends. The Commute: from ${r.trend[0].score} on 29 September to ${r.trend[r.trend.length - 1].score} on 5 November, record ${r.record.score}. The Commute home: from ${rh.trend[0].score} to ${rh.trend[rh.trend.length - 1].score}, record ${rh.record.score}.` });
    const legend = `<div class="st2-legend"><span><i class="is-line"></i>The Commute</span><span><i class="is-alt"></i>The Commute home</span><span><i class="is-rec"></i>Record (Smoothness only)</span></div>`;
    const rows = D.routes.filter((x) => x.named).map((x) => {
      const tr = x.trend.map((p) => p.score);
      const enough = tr.length >= 3;
      return ui.row({ lead: `<span class="st2-gl">${ui.glyph(x.glyph, 36)}</span>`, title: x.name, subHtml: true, sub: enough ? `${tr[0]} → ${tr[tr.length - 1]} · ${plural(x.runCount, 'drive')}` : `${plural(x.runCount, 'drive')} · trend after 3`, trailing: enough ? ui.sparkline(tr, { w: 64, h: 26, color: 'var(--dp-accent)', label: `${x.name} trend` }) + (x.record ? `<span class="st2-rec">${rosette(12)}${x.record.score}</span>` : '') : (x.record ? `<span class="st2-rec">${rosette(12)}${x.record.score}</span>` : ''), nav: 'S-RTE-02', params: { route: x.id } });
    });
    return [
      `<div class="ui-pad">${ui.card(`<div class="st2-hero"><div><span class="st2-k">By route</span><p class="st2-p">Smoothness on each route, run by run. Records are Smoothness only; durations never count.</p></div></div>${legend}${chart}`, { hero: true, cls: 'st2-chartcard' })}</div>`,
      ui.section('Routes', ui.list(rows), { style: 'caption' }),
    ];
  }
  function staClaims(ctx) {
    const { ui } = ctx;
    const r = U.route('r_commute'), s = r.trend.map((x) => x.score);
    const w = welch(s.slice(0, 4), s.slice(-4));
    const sept = monthTrips('2026-09');
    const pass = w.pass ? ui.card(`<div class="st2-claim"><span class="st2-claim-k is-pass">${ui.icon('check', 14)}Holds up</span><b>Smoother on The Commute: your last 4 runs average ${Math.round(w.mb)}, up from ${Math.round(w.ma)} on your first 4.</b><p>The change is bigger than your usual drive-to-drive spread, so we’re confident calling it.</p><span class="trp-link ui-press" ${ui.act({ nav: 'S-SCO-02', label: 'How we decide' })}>${ui.icon('info', 15)}How we decide</span></div>`) : '';
    const held = ui.card(`<div class="st2-claim is-held"><span class="st2-claim-k">${ui.icon('clock', 14)}Too early to say</span><b>Fewer abrupt stops than in September?</b><p>September had only ${plural(sept.length, 'drive')}, so the difference could be chance. We’ll tell you once it’s clear.</p></div>`);
    return ui.section('What changed', `<div class="ui-pad ui-stack" style="gap:10px">${pass}${held}</div>`, { style: 'caption', footer: 'A change is only called out when it passes the significance check. Nothing here is a guess.' });
  }

  screen('S-STA-02', {
    name: 'Trend detail',
    wp: 'D', platforms: ['ios', 'android'], presentation: 'push', tab: 'insights',
    states: [
      { id: 'default', label: 'Month' },
      { id: 'year', label: 'Year' },
      { id: 'perCar', label: 'per car' },
      { id: 'perRoute', label: 'per route' },
      { id: 'significanceGatedClaim', label: 'significance-gated claim (Month)' },
    ],
    backTo: 'S-STA-01',
    render(ctx) {
      const { ui, state } = ctx;
      const sel = state === 'significanceGatedClaim' ? 'default' : state;
      const seg = ui.segmented([{ id: 'default', label: 'Month' }, { id: 'year', label: 'Year' }, { id: 'perCar', label: 'Car' }, { id: 'perRoute', label: 'Route' }].map((o) => ({ ...o, state: o.id })), { selected: sel, cls: 'st2-seg' });
      const sub = { default: 'October 2026', significanceGatedClaim: 'October 2026', year: '2026 so far', perCar: 'By car', perRoute: 'By route' }[state];
      let blocks = state === 'year' ? staYear(ctx) : state === 'perCar' ? staCar(ctx) : state === 'perRoute' ? staRoute(ctx) : staMonth(ctx);
      if (state === 'significanceGatedClaim') blocks = [blocks[0], staClaims(ctx), ...blocks.slice(1)];
      else if (state === 'default') blocks.splice(2, 0, `<p class="st2-nocl">${ui.icon('info', 14)}No change claims for October yet. We only call one out when the data clearly supports it.</p>`);
      let body = `<div class="ui-pad st2-segwrap">${seg}</div>${blocks.map((b) => `<div class="st2-b" data-rise>${b}</div>`).join('')}`;
      if (!ctx.isPro) body = `<div class="ui-pad st2-segwrap">${seg}</div><div class="ui-pad">${ui.lockPanel(blocks.slice(0, 2).join(''), { text: D.entitlement.lockSheets.trends, nav: 'S-SUB-03/trends' })}</div>`;
      return ui.page(`<div class="st2">${body}</div>`, { nav: { title: 'Trends', subtitle: sub } });
    },
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '.st2-b[data-rise]', { stagger: 35 });
      drawPaths(ctx, el, '.st2-chartcard .dch-line', { duration: 1200, delay: 120, stagger: 120 });
      if (!still(ctx, el)) el.querySelectorAll('.st2-mo-b i').forEach((b, i) => ctx.motion.animate(b, [{ transform: 'scaleY(0)' }, { transform: 'scaleY(1)' }], { preset: 'default', delay: 150 + i * 30 }));
    },
    spec: {
      purpose: 'Long-term change for Pro: monthly, yearly, per-car and per-route Smoothness with contributors, efficiency habits, distance and time over the period and the baseline band; change claims appear only when they pass the significance rule (F-HIS-05).',
      layout: [
        'Top bar “Trends” + subtitle (October 2026 / 2026 so far / By car / By route): iOS inline title + glass back · Android MediumFlexibleTopAppBar.',
        'Dimension selector: Month · Year · Car · Route (iOS glass segmented · Android connected button group).',
        'Month: hero chart card — “October 2026”, average 84 (DP Numerals 48), legend (weekly average line · each drive dot · your usual range band); chart 338×170: baseline band (IQR of the month’s baselines, --dp-fill), faint per-drive dots, Glide weekly-average line with value labels, the smoothest drive gold. Then Drives · Distance · Time tiles, Contributors card (▲/●/▼ bars), Efficiency habits row (≈ estimated) → S-SCO-06, Smoothest drive trip card.',
        'Year: 12 monthly bars (Jan–Dec; months before 28 Sep marked empty, December future), average above each bar, drive count below; totals tiles; “Month by month” list.',
        'Car: chart with one line per car (GTI solid Glide, Ioniq dashed neutral — car colours never colour data), car chips as legend; one tinted card per car (silhouette 92, drives · km · time, ≈ efficiency, average) → S-GAR-02.',
        'Route: chart with The Commute (solid), The Commute home (dashed) and the record progression as a gold step line; Routes list (glyph, first → latest, sparkline, record) → S-RTE-02.',
        'Significance-gated claim: “What changed” section under the hero — a “Holds up” claim card (check glyph) and a “Too early to say” card (clock glyph) + footer. Default month shows the quiet “No change claims for October yet” line instead.',
      ],
      components: ['page', 'navBar', 'segmented', 'card', 'stats', 'section', 'list', 'row', 'tripRow', 'carChip', 'sparkline', 'glyph', 'lockPanel'],
      interactions: ['Segments switch state in place (cross-fade, selection haptic).', 'Efficiency row → S-SCO-06 · Smoothest drive → S-TRP-01 · Car card → S-GAR-02 · Route row → S-RTE-02 · “How we decide” → S-SCO-02.', 'Free deep link: content shows as a lock panel (own data blurred) → S-SUB-03 trends.'],
      motion: ['Hero motion: chart lines draw on (1200 ms ease.drawOn, 120 ms between series); year bars grow from the baseline (spring.default, 30 ms stagger). RM: final chart, 220 ms fade.', 'Blocks rise 8 px, 35 ms stagger.'],
      a11y: ['Each chart has a summary sentence (e.g. “October 2026: 19 drives, average Smoothness 84. Weekly averages 81, 85, 85, 87. Your usual range is 79 to 82.”) and a data table on Android / Audio Graph on iOS.', 'Series differ by dash pattern and label, not colour alone; record points are gold with the rosette meaning stated in the legend.', 'Claims read as one element including their status (“Holds up” / “Too early to say”).', 'XL: legends wrap; tiles go to 2 columns; the year chart keeps 12 columns with labels below.'],
      platformDiffs: { ios: 'Swift Charts (LineMark + PointMark + RectangleMark band); glass segmented Picker; inset-grouped lists.', android: 'Vico/Compose Canvas charts; connected ButtonGroup; hero card surface-container-high radius 28; segmented ListItems.' },
      analytics: ['trend_viewed {period: month|year, dimension: none|car|route}', 'trend_claim_shown {claim, significant}'],
      copy: {
        'No claim': 'No change claims for October yet. We only call one out when the data clearly supports it.',
        'Claim (passes)': 'Holds up · Smoother on The Commute: your last 4 runs average 87, up from 80 on your first 4. The change is bigger than your usual drive-to-drive spread, so we’re confident calling it.',
        'Claim (held)': 'Too early to say · Fewer abrupt stops than in September? September had only 3 drives, so the difference could be chance. We’ll tell you once it’s clear.',
        'Claims footer': 'A change is only called out when it passes the significance check. Nothing here is a guess.',
        'Car note': 'Each car is compared with your own usual in it. Car colours tint the cards, never the data.',
        Efficiency: 'Better than usual on {n} of {m} drives · ≈ estimated. Smoother habits like these usually help fuel use and range. Check your car’s display for your number.',
      },
      freePro: 'Pro only (also Preview and Ride-along). Free users reach S-SUB-03 trends from S-STA-01; a direct Free deep link renders the content inside a lock panel (own data blurred).',
      data: 'Month: October driver trips (derived: 19 drives · 760 km · 11.3 h · average 84 = wrapped.monthly), weekly averages from recaps 5–26 Oct, baseline band = IQR of smoothness.baseline. Year: derived per month (Sep 3 drives/78 · Oct 19/84 · Nov 7/87). Car: carStats() per vehicle (GTI 25 drives avg 84 · Ioniq 4 avg 87). Route: routes[].trend + recordHistory. Claim: Welch t-test on r_commute first 4 vs last 4 runs (80 → 87, passes); September vs October abrupt stops held back (3 drives). The significance rule is a placeholder for A12.',
      icons: ['info', 'check', 'clock', 'bolt', 'rosette'],
      assets: [],
    },
  });

  /* ==========================================================================================================
     Routes: route card, list (S-RTE-01), detail (S-RTE-02)
     ========================================================================================================== */
  function recChip(ui, r) {
    return r.record ? ui.chip('', { kind: 'record', html: `${rosette(13)}<b class="ui-num">${r.record.score}</b> · ${U.fmtDate(r.record.date)}`, label: `Route record ${r.record.score}, ${longDate(r.record.date)}` }) : ui.chip('No record yet', { kind: 'outline' });
  }
  function routeCard(ctx, r, o = {}) {
    const { ui } = ctx;
    const linked = r.linkedWith && U.route(r.linkedWith);
    const dir = `${ui.esc(r.from)} ${linked ? '⇄' : '→'} ${ui.esc(r.to)}`;
    const meta = `${dir} · ${plural(r.runCount, 'drive')} · ${ui.num(r.distanceKm, 'km')}`;
    const aria = `${routeName(r)}, ${r.from} to ${r.to}. ${r.runCount} drives. ${r.record ? `Route record ${r.record.score}, ${longDate(r.record.date)}.` : 'No record yet.'}${linked ? ` Linked with ${linked.name}.` : ''}${o.locked ? ' Part of Pro on the Free plan.' : ''}`;
    if (o.locked) {
      const inner = `<div class="rte-lk-in">${ui.glyph(r.glyph, 56)}${dotStrip(ctx, r, { axis: false })}</div>`;
      return ui.card(`<div class="rte-card is-locked"><div class="rte-card-h"><div><b>${ui.esc(routeName(r))}</b></div>${recChip(ui, r)}</div><span class="rte-card-meta">${meta}</span>${ui.lockPanel(inner, { text: D.entitlement.lockSheets.thirdRoute, compact: true, nav: 'S-SUB-03/s3rdRoute', params: { route: r.id } })}<div class="rte-lk-f"><span>Still recorded and matched. Nothing is lost.</span>${ui.button('See plans', { kind: 'tonal', size: 's', nav: 'S-SUB-03/s3rdRoute', params: { route: r.id } })}</div></div>`, { label: aria });
    }
    if (o.hero) {
      return ui.card(`<div class="rte-card is-hero"><div class="rte-card-h"><div><b class="rte-hero-n">${ui.esc(routeName(r))}</b><span>${meta}</span></div>${recChip(ui, r)}</div><div class="rte-hero-g">${ui.glyph(r.glyph, 132, { width: 3.2 })}</div>${dotStrip(ctx, r)}</div>`, { hero: true, nav: 'S-RTE-02', params: { route: r.id }, zoom: true, label: aria, cls: 'rte-herocard' });
    }
    return ui.card(`<div class="rte-card"><span class="rte-card-g">${ui.glyph(r.glyph, 52)}</span><div class="rte-card-m"><div class="rte-card-h"><div><b>${ui.esc(routeName(r))}</b></div>${recChip(ui, r)}</div><span class="rte-card-meta">${meta}</span>${dotStrip(ctx, r, { axis: false, cls: 'is-mini' })}</div></div>`, { nav: 'S-RTE-02', params: { route: r.id }, zoom: true, label: aria });
  }
  function candidateCard(ctx, r, o = {}) {
    const { ui } = ctx;
    const aria = `${r.placeholder}, ${r.from} to ${r.to}. Driven twice. Name this route.`;
    if (o.prompt) {
      const chips = ['Commute', 'Gym', 'School run'].map((c) => ui.chip(c, { kind: 'filter', nav: 'S-RTE-03', params: { route: r.id, preset: c } })).join('') + ui.chip('Custom', { kind: 'filter', icon: 'edit', nav: 'S-RTE-03/custom', params: { route: r.id } });
      return ui.card(`<div class="rte-cand is-prompt"><div class="rte-cand-art">${ui.artMerge(92)}</div><span class="rte-kick"><i></i>Looks like a regular</span><b>${ui.esc(r.from)} → ${ui.esc(r.to)}, driven twice.</b><p>Name this route to see its record and every run.</p><div class="rte-cand-chips">${chips}</div></div>`, { hero: true, label: aria, cls: 'rte-candcard' });
    }
    return ui.card(`<div class="rte-card is-cand"><span class="rte-card-g is-cand">${ui.glyph(r.glyph, 52, { color: 'var(--dp-text-2)' })}</span><div class="rte-card-m"><div class="rte-card-h"><div><b>${ui.esc(r.placeholder)}</b><span>${ui.esc(r.from)} → ${ui.esc(r.to)} · driven twice</span></div>${ui.button('Name it', { kind: 'tonal', size: 's', nav: 'S-RTE-03', params: { route: r.id } })}</div></div></div>`, { label: aria });
  }

  screen('S-RTE-01', {
    name: 'Routes list',
    wp: 'D', platforms: ['ios', 'android'], presentation: 'root', tab: 'routes',
    states: [
      { id: 'default', label: 'Def' },
      { id: 'Empty', label: 'Empty' },
      { id: 'candidateToName', label: 'candidate to name' },
      { id: 'freeLimitWith3rdRouteLocked', label: 'Free limit with 3rd route locked' },
    ],
    stateParams: { freeLimitWith3rdRouteLocked: { ent: 'free' } },
    render(ctx) {
      const { ui, state } = ctx;
      const free = !ctx.isPro;
      const menu = ui.menu('rte1-menu', [
        free ? { text: 'Choose active routes', icon: 'check', nav: 'S-RTE-05/chooseActiveRoutes' } : null,
        { text: 'Rename a route', icon: 'edit', nav: 'S-RTE-05', params: { route: 'r_commute' } },
        { text: 'Merge routes', icon: 'layers', nav: 'S-RTE-05/merge', params: { route: 'r_commute' } },
        { text: 'Link directions', icon: 'routes', nav: 'S-RTE-05/linkDirections', params: { route: 'r_gym' } },
      ]);
      const more = ui.iconButton('more', { svg: ui.icon(ctx.ios ? 'more' : 'moreVert', 22), menu: 'rte1-menu', label: 'Manage routes' });
      const trailing = rootTrailing(ctx, state === 'Empty' ? [] : [more]);
      const sub = ctx.android ? (state === 'Empty' ? 'No routes yet' : `${D.routes.filter((r) => r.named).length} routes`) : null;
      if (state === 'Empty') {
        const body = `<div class="ui-pad" style="margin-top:8px">${ui.card(ui.empty({ art: `<div class="rte-empty-art">${ui.artMerge(120)}</div>`, title: 'Drive the same trip twice and it becomes a route.', text: 'DrivePerf spots repeated drives and groups them, so you can see every run and your record. Records are Smoothness only, never time.' }), { hero: true })}</div>
          <div class="ui-pad rte-how">${[['routes', 'Twice makes a route', 'Same start, same end, a similar path.'], ['rosette', 'A record for each', 'Your smoothest run. Never the fastest.'], ['ghost', 'Ghost comparison', 'See where a run gained or lost smoothness.']].map(([ic, a, b]) => `<div>${ui.icon(ic, 20)}<b>${a}</b><span>${b}</span></div>`).join('')}</div>`;
        return ui.page(`<div class="rte">${body}</div>`, { large: { title: 'Routes', subtitle: sub, trailing } });
      }
      const named = D.routes.filter((r) => r.named);
      const cand = D.routes.find((r) => r.candidate);
      const blocks = [];
      if (state === 'candidateToName') blocks.push(`<div class="ui-pad">${candidateCard(ctx, cand, { prompt: true })}</div>`);
      blocks.push(`<p class="rte-rule">${rosette(13)}Records are Smoothness only, never time.</p>`);
      named.forEach((r, i) => {
        const locked = U.isRouteLocked(r, ctx.ent);
        blocks.push(`<div class="ui-pad">${routeCard(ctx, r, { hero: i === 0, locked })}</div>`);
      });
      if (state !== 'candidateToName') blocks.push(ui.section('New', `<div class="ui-pad">${candidateCard(ctx, cand)}</div>`, { style: 'caption' }));
      if (free) blocks.push(`<div class="ui-pad">${ui.list([ui.row({ icon: 'check', title: '2 of 2 routes active on Free', sub: 'Choose which two get records and comparisons', nav: 'S-RTE-05/chooseActiveRoutes' })])}</div>`);
      return ui.page(`<div class="rte">${blocks.map((b) => `<div class="rte-b" data-rise>${b}</div>`).join('')}</div>`, { large: { title: 'Routes', subtitle: sub, trailing }, fixed: menu });
    },
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '.rte-b[data-rise]', { stagger: 40 });
      drawPaths(ctx, el, '.rte-herocard .ui-glyph path', { duration: 1600, delay: 120 });
      popAll(ctx, el, '.rte-herocard .rte-dot', { delay: 900, stagger: 40 });
    },
    spec: {
      purpose: 'The Routes tab: every route DrivePerf has grouped from repeated drives, with its record and runs at a glance, new candidates to name, and the Free two-route limit shown “locked, not lost” (F-RTE-02, F-RTE-05).',
      layout: [
        'Large title “Routes”; trailing “…” menu (Choose active routes on Free · Rename · Merge · Link directions → S-RTE-05) + Preview badge + avatar. Android subtitle “3 routes”.',
        'Rule line under the title: gold rosette + “Records are Smoothness only, never time.” (meta, text-2).',
        'Hero route card (most-driven route, radius 32 / 28, Glide radial wash): name (section title) · “Home ⇄ Work · 10 drives · 37.9 km” · record chip (rosette, “91 · 5 Nov”, --dp-record on 18%) · route glyph 132 in Glide (share-safe trimmed shape) · dot strip (10 px dots on a 4 px track, x = date; record gold 14 px, latest outlined Glide) · axis “29 Sep · Record is Smoothness only · 5 Nov”.',
        'Route cards (radius 24 / 16): glyph thumbnail 52 on surface-2 · name · direction (⇄ when linked) · drives · km · record chip · mini dot strip.',
        'Candidate: “New” section with “Route 4 · Gym → Home · driven twice” + tonal “Name it”. Candidate state: hero prompt card with the merge illustration, kicker “● Looks like a regular”, “Gym → Home, driven twice.”, chips Commute · Gym · School run · Custom.',
        'Free limit: the third route keeps its name, meta and record visible; its glyph and dot strip are blurred 14 px with a lock and “Track every route you drive with Pro.”; footer “Still recorded and matched. Nothing is lost.” + See plans; a “2 of 2 routes active on Free” row → S-RTE-05 chooseActiveRoutes.',
        'Empty: hero card with the two-lines-merging illustration (artMerge 120), “Drive the same trip twice and it becomes a route.” + 3 how-it-works tiles (Twice makes a route · A record for each · Ghost comparison).',
      ],
      components: ['largeTitle', 'menu', 'iconButton', 'avatar', 'card', 'glyph', 'chip', 'button', 'lockPanel', 'list', 'row', 'section', 'empty', 'artMerge'],
      interactions: [
        'Route card → S-RTE-02 (iOS zoom / Android container transform from the card). A locked route’s card opens S-SUB-03 s3rdRoute via the lock; its page is S-RTE-02/Lock.',
        '“Name it” / prompt chips → S-RTE-03 (preset chip pre-selected; Custom → S-RTE-03/custom).',
        '“…” menu and the Free row → S-RTE-05 (rename / merge / link / choose active routes).',
      ],
      motion: ['Hero motion: the hero route glyph draws on (1600 ms, ease.drawOn), then its run dots pop in date order (shared.pinPop, 40 ms stagger); cards rise 8 px, 40 ms stagger. RM: glyph and dots appear complete (220 ms fade).', 'Card → detail: zoom / container transform (spring.slow).'],
      a11y: ['Route card is one button: “The Commute, Home to Work. 10 drives. Route record 91, 5 November. Linked with The Commute home.”', 'Dot strip summary: “10 drives on The Commute. Record 91 on 5 November. Latest 91 on 5 November.” The glyph is decorative inside the labelled card.', 'Record meaning: rosette + “record” text, never gold alone. Lock: “Part of Pro on the Free plan.”', 'XL: record chips move under the name; dot strips keep full width; how-it-works tiles stack.'],
      platformDiffs: { ios: 'Large title + glass toolbar “…” Menu and avatar; hero card --dp-surface with Glide wash; cards with continuous corners; floating tab bar + capture accessory.', android: 'LargeFlexibleTopAppBar with subtitle and overflow DropdownMenu; cards surface-container (hero surface-container-high, 28 dp); tonal “Name it” button; NavigationBar.' },
      analytics: ['routes_opened {route_count_bucket}', 'route_candidate_prompt_tapped {preset|custom}', 'route_limit_hit', 'locked_content_viewed {type: route}'],
      copy: {
        Title: 'Routes', Rule: 'Records are Smoothness only, never time.',
        Candidate: 'Looks like a regular · {from} → {to}, driven twice. Name this route to see its record and every run.',
        Lock: 'Track every route you drive with Pro. (lockSheets.thirdRoute) · Still recorded and matched. Nothing is lost.',
        'Free row': '2 of 2 routes active on Free · Choose which two get records and comparisons',
        Empty: 'Drive the same trip twice and it becomes a route. DrivePerf spots repeated drives and groups them, so you can see every run and your record. Records are Smoothness only, never time.',
      },
      freePro: 'Free: 2 active routes (entitlement.states.free.activeRouteIds = The Commute, The Commute home); Gym is the locked 3rd route — still recorded and matched, its depth blurred → S-SUB-03 s3rdRoute; the candidate can still be named. Pro/Preview: unlimited routes, no locks.',
      data: 'DP_DATA.routes: r_commute (10 runs, record 91 · 5 Nov, linked) · r_commute_home (6, 88 · 29 Oct, linked) · r_gym (2, 84 · 4 Nov; freeLocked) · r_cand_gym_home (candidate “Route 4”, 2 runs). Empty simulates routes: [].',
      icons: ['routes', 'rosette', 'more', 'edit', 'layers', 'check', 'lock', 'ghost'],
      assets: [],
    },
  });

  /* ---------- S-RTE-02 · Route detail ---------- */
  function runsTable(ctx, r, o = {}) {
    const { ui, data } = ctx;
    const recId = r.record && r.record.tripId;
    const rows = r.runs.slice().reverse().map((x) => {
      const rec = x.tripId === recId;
      const aria = `${longDate(x.date)}, ${x.partOfDay}. ${data.min(x.durationS)}. Smoothness ${x.score}${rec ? '. Route record' : ''}.`;
      return `<div class="rte-run ui-press" role="listitem" ${ui.act({ nav: 'S-TRP-01', params: { trip: x.tripId }, label: aria })}><span class="rte-run-d"><b>${U.fmtDate(x.date)}</b><small>${ui.esc(x.partOfDay.split(' ')[0])}${x.quality !== 'Good' ? ` · ${ui.esc(x.quality)}` : ''}</small></span><span class="rte-run-t">${ui.esc(data.min(x.durationS))}</span><span class="rte-run-s ${rec ? 'is-rec' : ''}">${rec ? rosette(13) : ''}${ui.num(x.score)}</span>${ui.icon('chevronRight', 15)}</div>`;
    }).join('');
    return `<div class="ui-card is-flush rte-runs"><div class="rte-run is-h" aria-hidden="true"><span>Drive</span><span>Duration</span><span>Smoothness</span><i></i></div><div role="list">${rows}</div></div>`;
  }

  screen('S-RTE-02', {
    name: 'Route detail',
    wp: 'D', platforms: ['ios', 'android'], presentation: 'push', tab: 'routes',
    states: [
      { id: 'default', label: 'Def' },
      { id: 'newRecord', label: 'new record' },
      { id: 'fewRuns', label: 'few runs (2, no record yet)' },
      { id: 'Lock', label: 'Lock (inactive route on Free)' },
    ],
    stateParams: { default: { route: 'r_commute' }, newRecord: { route: 'r_commute' }, fewRuns: { route: 'r_cand_gym_home' }, Lock: { route: 'r_gym', ent: 'free' } },
    backTo: 'S-RTE-01',
    render(ctx) {
      const { ui, data, state } = ctx;
      const r = data.route(ctx.params.route) || data.route('r_commute');
      const locked = state === 'Lock' || U.isRouteLocked(r, ctx.ent);
      const n = r.runs.length;
      const recIdx = r.record ? r.runs.findIndex((x) => x.tripId === r.record.tripId) : -1;
      const sel = ctx.store.sel != null ? ctx.store.sel : state === 'newRecord' ? recIdx : n - 1;
      const run = r.runs[sel];
      const linked = r.linkedWith && U.route(r.linkedWith);
      const menu = ui.menu('rte2-menu', [
        { text: 'Rename', icon: 'edit', nav: 'S-RTE-05', params: { route: r.id } },
        { text: 'Merge with another route', icon: 'layers', nav: 'S-RTE-05/merge', params: { route: r.id } },
        { text: linked ? 'Linked directions' : 'Link directions', icon: 'routes', nav: 'S-RTE-05/linkDirections', params: { route: r.id } },
        '-',
        { text: 'Remove route', icon: 'trash', destructive: true, nav: 'S-RTE-05/remove', params: { route: r.id } },
      ]);
      const nav = { title: routeName(r), subtitle: `${r.from} ${linked ? '⇄' : '→'} ${r.to} · ${plural(r.runCount, 'drive')}`, trailing: r.candidate ? [] : [ui.iconButton('more', { svg: ui.icon(ctx.ios ? 'more' : 'moreVert', 22), menu: 'rte2-menu', label: 'Manage route' })] };
      const blocks = [];
      if (state === 'newRecord') {
        const prevRec = r.recordHistory[r.recordHistory.length - 2];
        blocks.push(`<div class="ui-pad"><div class="rte-newrec" role="status">${rosette(26)}<div><b>New route record</b><span>${ui.esc(`${r.record.score} on ${r.name}, beating ${prevRec.score} from ${U.fmtDate(prevRec.date)}.`)}</span></div></div></div>`);
      }
      // hero: record + glyph + dot strip
      const recBlock = r.record
        ? `<div class="rte-recb"><span class="rte-k">${rosette(14)}Route record</span><b class="rte-recn">${ui.num(r.record.score)}</b><span>${ui.esc(`${longDate(r.record.date)} · ${r.runs[recIdx].partOfDay}`)}</span></div>`
        : `<div class="rte-recb is-none"><span class="rte-k">${ui.icon('rosette', 14)}Route record</span><b class="rte-recn is-none">—</b><span>One more drive to set a record.</span></div>`;
      const selLine = `<div class="rte-sel"><span><b>${ui.esc(`${U.fmtDate(run.date)} · ${run.partOfDay}`)}</b><small>${ui.esc(`${data.min(run.durationS)} · ${run.quality} data`)}${sel === recIdx ? ' · record' : ''}</small></span><span class="rte-sel-s ${sel === recIdx ? 'is-rec' : ''}">${ui.num(run.score)}</span>${ui.button('Open', { kind: ctx.ios ? 'glass' : 'tonal', size: 's', nav: 'S-TRP-01', params: { trip: run.tripId } })}</div>`;
      const hero = ui.card(`<div class="rte-dh">${recBlock}<span class="rte-dh-g">${ui.glyph(r.glyph, 118, { width: 3.2, color: r.candidate ? 'var(--dp-text-2)' : null })}</span></div>${dotStrip(ctx, r, { sel, action: 'pickRun', mid: n > 2 ? 'Tap a dot to pick a run' : 'Record is Smoothness only' })}${selLine}`, { hero: true, cls: 'rte-herocard' });
      if (locked) {
        blocks.push(`<div class="ui-pad"><div class="rte-lockh"><b>${ui.esc(routeName(r))} isn’t one of your 2 active routes.</b><span>Its ${plural(r.runCount, 'drive')} are still recorded and matched. Nothing is lost.</span></div></div>`);
        const inner = `${hero}<div style="height:12px"></div>${runsTable(ctx, r)}`;
        blocks.push(`<div class="ui-pad">${ui.lockPanel(inner, { text: D.entitlement.lockSheets.thirdRoute, nav: 'S-SUB-03/s3rdRoute', params: { route: r.id } })}</div>`);
        blocks.push(`<div class="ui-pad">${ui.list([ui.row({ icon: 'check', title: 'Choose active routes', sub: 'Swap which 2 routes are active. No drives are lost.', nav: 'S-RTE-05/chooseActiveRoutes' })])}</div>`);
        return ui.page(`<div class="rte2">${blocks.map((b) => `<div class="rte-b" data-rise>${b}</div>`).join('')}</div>`, { nav: { ...nav, trailing: [] } });
      }
      if (r.candidate) blocks.push(`<div class="ui-pad">${ui.banner({ kind: 'positive', icon: 'routes', title: 'Looks like a regular', text: `${r.from} → ${r.to}, driven twice. Name it to keep its record.`, action: { text: 'Name it', nav: 'S-RTE-03', params: { route: r.id } } })}</div>`);
      blocks.push(`<div class="ui-pad">${hero}</div>`);
      // compare
      if (r.record && n >= 2) {
        const isCommute = r.id === 'r_commute';
        const lockC = !ctx.isPro;
        const cmp = (title, sub, cmpKey, st) => ui.row({ icon: 'ghost', title, sub, trailing: lockC ? 'lock' : null, ...(lockC ? { nav: 'S-SUB-03', params: { route: r.id, lock: 'compare' } } : { nav: st ? `S-RTE-04/${st}` : 'S-RTE-04', params: { route: r.id, cmp: cmpKey } }) });
        const latest = r.runs[n - 1], prevRec = r.recordHistory[r.recordHistory.length - 2];
        const rows = isCommute ? [cmp('Your record vs the previous record', `${U.fmtDate(r.record.date)} · ${r.record.score} vs ${prevRec.score} on ${U.fmtDate(prevRec.date)}`, 'record'), cmp('Your latest run vs your record', `${U.fmtDate(U.trip(D.ghostLatest.runTripId).start)} · ${U.trip(D.ghostLatest.runTripId).smoothness.score} vs ${r.record.score}`, 'latest', 'whereYouGainedLost')]
          : [cmp('Your latest run vs your record', `${U.fmtDate(latest.date)} · ${latest.score} vs ${r.record.score}`, 'latest')];
        blocks.push(ui.section('Compare', ui.list(rows), { style: 'caption', footer: lockC ? ui.esc(D.entitlement.lockSheets.compare) : 'Where you gained and lost smoothness, segment by segment. Durations are shown side by side, never as a difference.' }));
      }
      // trend
      if (r.trend.length >= 3 && !r.candidate) {
        const t0 = new Date(r.trend[0].date).getTime(), t1 = new Date(r.trend[r.trend.length - 1].date).getTime();
        const X = (d) => (new Date(d).getTime() - t0) / ((t1 - t0) || 1);
        const recSet = new Set(r.recordHistory.map((x) => x.date));
        const chart = lineChart(ui, { w: 338, h: 150, lo: 70, hi: 95, ticks: [75, 80, 85, 90], series: [
          { pts: r.recordHistory.map((x) => ({ x: X(x.date), y: x.score })), cls: 'is-rec', step: true, dots: false },
          { pts: r.trend.map((x, i) => ({ x: X(x.date), y: x.score, cls: recSet.has(x.date) ? 'is-rec' : i === r.trend.length - 1 ? 'is-last' : '' })), cls: 'is-accent' },
        ], xLabels: [{ x: 0, text: U.fmtDate(r.trend[0].date), anchor: 'start' }, { x: 1, text: U.fmtDate(r.trend[r.trend.length - 1].date), anchor: 'end' }], label: `Smoothness on ${r.name}, run by run: from ${r.trend[0].score} on ${longDate(r.trend[0].date)} to ${r.trend[r.trend.length - 1].score} on ${longDate(r.trend[r.trend.length - 1].date)}. Record ${r.record.score}. The gold step line is the record over time.` });
        blocks.push(ui.section('Smoothness trend', `<div class="ui-pad">${ui.card(`${chart}<div class="st2-legend is-row"><span><i class="is-line"></i>Each run</span><span><i class="is-rec"></i>Record over time</span></div>`, { cls: 'rte-trendcard' })}</div>`, { style: 'caption' }));
      } else {
        blocks.push(ui.section('Smoothness trend', `<div class="ui-pad">${ui.card(`<div class="sta-cal"><span class="ui-dots3 sta-cal-d">${[0, 1, 2].map((i) => `<i class="${i < n ? 'on' : ''}"></i>`).join('')}</span><div><b>${plural(n, 'run')} so far</b><p>The trend and the record start with the third drive on this route.</p></div></div>`)}</div>`, { style: 'caption' }));
      }
      blocks.push(ui.section(`Runs · ${n}`, `<div class="ui-pad">${runsTable(ctx, r)}</div>`, { style: 'caption', footer: ui.esc(r.recordRule) }));
      if (linked) blocks.push(`<div class="ui-pad" style="margin-top:14px">${ui.list([ui.row({ icon: 'routes', title: `Linked with ${linked.name}`, sub: `${linked.from} → ${linked.to} · ${plural(linked.runCount, 'drive')} · record ${linked.record.score}`, nav: 'S-RTE-02', params: { route: linked.id } })])}</div>`);
      return ui.page(`<div class="rte2">${blocks.map((b) => `<div class="rte-b" data-rise>${b}</div>`).join('')}</div>`, { nav, fixed: menu });
    },
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '.rte-b[data-rise]', { stagger: 35 });
      popAll(ctx, el, '.rte-herocard .rte-dot', { delay: 160, stagger: 45 });
      drawPaths(ctx, el, '.rte-trendcard .dch-line', { duration: 1200, delay: 400, stagger: 150 });
      if (ctx.state === 'newRecord' && !ctx.store.celebrated) {
        ctx.store.celebrated = true;
        const b = el.querySelector('.rte-newrec');
        if (b && !still(ctx, el)) { b.classList.add('is-shimmer'); ctx.motion.animate(b.querySelector('svg'), [{ transform: 'scale(.6) rotate(-20deg)', opacity: 0 }, { transform: 'scale(1) rotate(0)', opacity: 1 }], { preset: 'bouncy', delay: 200 }); }
        setTimeout(() => ctx.haptic('doubleSuccess'), still(ctx, el) ? 0 : 500);
      }
    },
    actions: { pickRun: (ctx, i) => { ctx.store.sel = +i; ctx.update(); return false; } },
    spec: {
      purpose: 'A page per route: every run on a dot strip with the record highlighted, the Smoothness trend for the route, each run’s duration shown neutrally, and the way into the ghost comparison (F-RTE-02, F-RTE-03). Records are Smoothness only — never duration, speed or time.',
      layout: [
        'Top bar: route name + “Home → Work · 10 drives” (⇄ when linked); trailing “…” Menu: Rename · Merge with another route · Link directions · Remove route (destructive) → S-RTE-05.',
        'New record state: gold banner (record container, rosette 26) “New route record · 91 on The Commute, beating 89 from 14 Oct.” with one shimmer.',
        'Hero card (radius 32 / 28): record block (rosette + “Route record”, DP Numerals 48 in --dp-record, date · part of day) + route glyph 118 · interactive dot strip (record gold, latest outlined, selected Glide ring) with axis labels · selected-run line (date · part of day, duration · quality, score, “Open”).',
        '“Compare” list (ghost glyph rows): “Your record vs the previous record” and “Your latest run vs your record” → S-RTE-04. Free: lock + PRO, footer “Keep comparing every run of The Commute with Pro.”',
        '“Smoothness trend” card: runs as a Glide line + dots (records gold), the record progression as a gold step line, legend.',
        '“Runs · 10” table: Drive (date + weekday, quality when not Good) · Duration (neutral text-2, no deltas, never sortable) · Smoothness (record gold with rosette) · chevron; footer = route.recordRule.',
        'Linked direction row → the other route. Few runs: “2 runs so far · The trend and the record start with the third drive” with 3-dot progress; candidate banner “Looks like a regular… Name it”.',
        'Lock (inactive on Free): explanation “Gym isn’t one of your 2 active routes. Its 2 drives are still recorded and matched. Nothing is lost.” · the hero and runs table blurred 14 px in a lock panel “Track every route you drive with Pro.” · “Choose active routes” row.',
      ],
      components: ['page', 'navBar', 'menu', 'card', 'glyph', 'chip', 'button', 'list', 'row', 'section', 'banner', 'lockPanel'],
      interactions: [
        'Dot tap → selects that run (selection haptic) and updates the run line; “Open” / runs table row → S-TRP-01.',
        'Compare rows → S-RTE-04 (cmp=record → default; cmp=latest → whereYouGainedLost). Free → S-SUB-03 (compare variant, own overlay blurred).',
        '“…” items → S-RTE-05 states. Linked row → S-RTE-02 for the other direction. Candidate “Name it” → S-RTE-03. Lock → S-SUB-03 s3rdRoute; “Choose active routes” → S-RTE-05/chooseActiveRoutes.',
        'Entry points: S-RTE-01 card (zoom), Story record chip, “New route record” notification (S-NTF-01).',
      ],
      motion: ['Hero motion: run dots pop in date order (shared.pinPop, 45 ms stagger); trend lines draw on (1200 ms). New record: the banner shimmers once and the rosette springs in (spring.bouncy) with the doubleSuccess haptic — no confetti (F-RTE-03.1). RM: no shimmer, rosette static, haptic kept.'],
      a11y: ['Dot strip is one adjustable element natively: “10 drives on The Commute. Record 91 on 5 November. Latest 91 on 5 November.” Swipe up/down moves between runs (“3 November, Tuesday morning. Smoothness 86.”).', 'Runs table rows: “3 November, Tuesday morning. 32 min. Smoothness 86.” — duration is read as a plain fact, never compared.', 'Record = rosette + “record” text; gold never carries meaning alone.', 'XL: the record block stacks above the glyph; table columns reflow to two lines per run.'],
      platformDiffs: { ios: 'Inline title + subtitle, glass back and “…” Menu; zoom transition from the route card; inset-grouped lists.', android: 'MediumFlexibleTopAppBar + overflow DropdownMenu; container transform; hero card surface-container-high (28 dp); segmented ListItems; tonal “Open”.' },
      analytics: ['route_detail_opened {source: list|story|notification, runs_bucket}', 'route_run_selected', 'ghost_compare_opened {source: route_detail, cmp: record|latest, locked}'],
      copy: {
        'New record': 'New route record · {n} on {route}, beating {m} from {date}.',
        'No record': 'One more drive to set a record.',
        'Few runs': '{n} runs so far · The trend and the record start with the third drive on this route.',
        'Record rule': 'Smoothness only. Never duration. Runs outside your typical pace band cannot set a record.',
        'Compare footer': 'Where you gained and lost smoothness, segment by segment. Durations are shown side by side, never as a difference.',
        Lock: '{route} isn’t one of your 2 active routes. Its {n} drives are still recorded and matched. Nothing is lost. · Track every route you drive with Pro.',
      },
      freePro: 'Free: active routes are fully visible; Compare rows show lock + PRO → S-SUB-03 (compare). Inactive routes (Gym on Free) render the Lock state with their own data blurred. Pro/Preview: everything open.',
      data: 'default / newRecord: r_commute (10 runs, record 91 t20261105-0742 beating 89 from 14 Oct; trend + recordHistory). fewRuns: r_cand_gym_home (2 runs, record null). Lock: r_gym with ent free. Compare uses DP_DATA.ghost (record vs previous) and ghostLatest (3 Nov vs record).',
      icons: ['rosette', 'ghost', 'more', 'edit', 'layers', 'routes', 'trash', 'lock', 'check', 'chevronRight'],
      assets: [],
    },
  });

  /* ---------- S-RTE-03 · Name this route ---------- */
  screen('S-RTE-03', {
    name: 'Name this route sheet',
    wp: 'D', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'routes',
    over: 'S-RTE-01/candidateToName',
    detent: 'auto',
    states: [{ id: 'default', label: 'Preset chips' }, { id: 'custom', label: 'custom' }],
    stateParams: { default: { route: 'r_cand_gym_home' }, custom: { route: 'r_cand_gym_home' } },
    render(ctx) {
      const { ui, state } = ctx;
      const r = ctx.data.route(ctx.params.route) || ctx.data.route('r_cand_gym_home');
      const rev = reverseOf(r);
      const pick = ctx.store.pick || ctx.params.preset || (state === 'custom' ? 'Custom' : null);
      const name = pick === 'Custom' ? (ctx.store.name ?? 'Back from the gym') : pick;
      const link = ctx.store.link ?? false;
      const chips = ['Commute', 'Gym', 'School run'].map((c) => ui.chip(c, { kind: 'filter', selected: pick === c, action: 'pick', arg: c, haptic: 'selection' })).join('') + ui.chip('Custom', { kind: 'filter', selected: pick === 'Custom', icon: 'edit', action: 'pick', arg: 'Custom', haptic: 'selection' });
      const field = pick === 'Custom' ? `<div class="rn-field">${ui.textField({ label: 'Route name', value: name, placeholder: 'e.g. Back from the gym', helper: `${name.length} of 24 · never a street address`, focused: true })}</div>` : '';
      const dupe = pick && D.routes.some((x) => x.name === name) ? `<p class="rn-note">${ui.icon('info', 14)}You already have “${ui.esc(name)}”. Each direction keeps its own record.</p>` : '';
      const linkRow = rev ? ui.list([ui.row({ icon: 'routes', title: `Link with ${rev.name}`, sub: `${rev.from} → ${rev.to} · shows both directions together`, trailing: 'toggle', on: link, action: 'link' })], { cls: 'rn-list' }) : '';
      return `${ui.sheetHeader('Name this route', { sub: `${r.from} → ${r.to} · driven twice` })}<div class="rn">
        <div class="rn-g">${ui.glyph(r.glyph, 64)}<span>${ui.esc(r.placeholder)} until you name it. Names stay on your phone.</span></div>
        <div class="rn-chips" role="radiogroup" aria-label="Name presets">${chips}</div>${field}${dupe}${linkRow}
        ${ui.button(name ? `Save “${name}”` : 'Pick a name', { kind: 'filled', block: true, size: 'l', action: 'save', disabled: !name, haptic: 'confirm' })}
        ${ui.button('Not now', { kind: 'text', block: true, back: true })}</div>`;
    },
    mount(el, ctx) {
      popAll(ctx, el, '.rn-chips .ui-chip', { delay: 120, stagger: 40 });
      const f = el.querySelector('.ui-field-in');
      if (f) f.addEventListener('input', () => { ctx.store.name = f.textContent.slice(0, 24); const b = el.querySelector('[data-action="save"] .ui-btn-l'); if (b) b.textContent = ctx.store.name ? `Save “${ctx.store.name}”` : 'Pick a name'; });
    },
    actions: {
      pick: (ctx, c) => { ctx.store.pick = c; if (c !== 'Custom') ctx.setState('default'); else ctx.update(); return false; },
      link: (ctx) => { ctx.store.link = !(ctx.store.link ?? false); ctx.update(); return false; },
      save: (ctx) => {
        const pick = ctx.store.pick || ctx.params.preset || (ctx.state === 'custom' ? 'Custom' : null);
        const name = pick === 'Custom' ? (ctx.store.name ?? 'Back from the gym') : pick;
        ctx.toast(`Route named “${name}”`, { icon: 'check' });
        returnTo(ctx, {}); return false;
      },
    },
    spec: {
      purpose: 'Name a route candidate in one tap (F-RTE-01): preset chips or a custom name, never a street address, with an option to link it with the opposite direction.',
      layout: [
        'Small sheet sized to content (iOS .height detent ≈ 36% — glass at partial detent · Android ModalBottomSheet with handle). Header “Name this route” + “Gym → Home · driven twice”.',
        'Glyph row: route glyph 64 on surface-2 + “Route 4 until you name it. Names stay on your phone.”',
        'Preset chips (radio group): Commute · Gym · School run · Custom (edit glyph). Custom shows a text field (label “Route name”, helper “17 of 24 · never a street address”, focused).',
        'Duplicate hint when the name exists (“You already have “Gym”. Each direction keeps its own record.”).',
        'Link row with switch: “Link with Gym · Home → Gym · shows both directions together”.',
        'Primary “Save “Gym”” (filled, large, block) + text “Not now”.',
      ],
      components: ['sheetHeader', 'glyph', 'chip', 'textField', 'list', 'row', 'toggle', 'button'],
      interactions: ['Chip → selects the preset (selection haptic); Custom → field focused with the keyboard (prototype: field only).', 'Save → toast “Route named “Gym”” and dismiss to the caller (S-RTE-01, S-STY-01 route-discovered frame or the notification). Not now / drag / scrim → dismiss, candidate stays.', 'Link switch toggles linking (toggleOn/Off haptic).'],
      motion: ['Sheet spring.sheet. Hero motion: preset chips pop in (shared.pinPop, 40 ms stagger). RM: chips appear in place.'],
      a11y: ['Chips are a radio group: “Gym, radio button, 2 of 4, selected”.', 'Text field: “Route name, Back from the gym, 17 of 24 characters”.', 'XL: the sheet opens at the large detent; chips wrap; Save label wraps.'],
      platformDiffs: { ios: 'presentationDetents([.height(320), .medium]); glass sheet; chips are glass capsules; TextField rounded (surface-2, radius 14); Toggle.', android: 'ModalBottomSheet; FilterChips; OutlinedTextField with supporting text counter; Switch with thumb icon.' },
      analytics: ['route_named {preset|custom, linked}', 'route_name_dismissed'],
      copy: { Title: 'Name this route', Sub: '{from} → {to} · driven twice', Hint: 'Route 4 until you name it. Names stay on your phone.', Presets: 'Commute · Gym · School run · Custom', Helper: '{n} of 24 · never a street address', Duplicate: 'You already have “{name}”. Each direction keeps its own record.', CTA: 'Save “{name}” · Not now', Toast: 'Route named “{name}”' },
      freePro: 'Naming is free. On Free a third or later route is still created and named; its depth shows locked on S-RTE-01/02.',
      data: 'r_cand_gym_home (placeholder “Route 4”, Gym → Home, 2 runs). Reverse direction found by from/to = r_gym (“Gym”). The custom value is a prototype example.',
      icons: ['edit', 'routes', 'info', 'close'],
      assets: [],
    },
  });

  /* ---------- S-RTE-04 · Ghost comparison ---------- */
  function cumKm(trip) {
    const out = []; let d = 0, prev = null;
    for (const r of trip.track.rows) { if (r[1] == null) { out.push(null); continue; } if (prev) { const R = 6371, dLa = (r[1] - prev[1]) * Math.PI / 180, dLn = (r[2] - prev[2]) * Math.PI / 180, a = Math.sin(dLa / 2) ** 2 + Math.cos(prev[1] * Math.PI / 180) * Math.cos(r[1] * Math.PI / 180) * Math.sin(dLn / 2) ** 2; d += 2 * R * Math.asin(Math.sqrt(a)); } prev = r; out.push(d); }
    return { at: out, total: d };
  }
  /** Ghost map: today's run (segment-shaded) over the record run as a dashed ghost, plain canvas (© OSM). */
  function ghostMap(ctx, g, o = {}) {
    const { ui } = ctx;
    const run = U.trip(g.runTripId), rec = U.trip(g.recordTripId);
    const w = o.w || 402, h = o.h || 520, it = o.insetTop || 0, ib = o.insetBottom || 0;
    const bbox = [Math.min(run.bbox[0], rec.bbox[0]), Math.min(run.bbox[1], rec.bbox[1]), Math.max(run.bbox[2], rec.bbox[2]), Math.max(run.bbox[3], rec.bbox[3])];
    const P = U.trackPath({ bbox, track: run.track }, w, h - it - ib, o.pad || 34);
    const pr = (la, ln) => { const [x, y] = P.project(la, ln); return [x, y + it]; };
    const pathOf = (rows) => { let d = '', pen = false; for (const r of rows) { if (r[1] == null) { pen = false; continue; } const [x, y] = pr(r[1], r[2]); d += `${pen ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`; pen = true; } return d; };
    const grid = Array.from({ length: Math.ceil(Math.max(w, h) / 36) + 1 }, (_, i) => `M${i * 36} 0V${h}M0 ${i * 36}H${w}`).join('');
    let roads = ''; const seen = new Set([run.routeKey]);
    for (const t of D.trips) { if (seen.has(t.routeKey)) continue; seen.add(t.routeKey); let d = '', pen = false; for (const r of t.track.rows) { if (r[1] == null) { pen = false; continue; } const [x, y] = pr(r[1], r[2]); if (x < -60 || x > w + 60 || y < -60 || y > h + 60) { pen = false; continue; } d += `${pen ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`; pen = true; } if (d) roads += `<path d="${d}"/>`; }
    const ck = cumKm(run);
    const segRows = (s) => run.track.rows.filter((r, i) => ck.at[i] != null && ck.at[i] / ck.total >= s.from - 0.004 && ck.at[i] / ck.total <= s.to + 0.004);
    let segs = '', glow = '', marks = '';
    for (const s of g.segments) {
      const d = pathOf(segRows(s));
      if (!d) continue;
      const band = !s.aligned ? 'unal' : s.band;
      if (band === 'gained') glow += `<path d="${d}" class="gh-glow"/>`;
      segs += `<path d="${d}" class="gh-seg is-${band}${o.hi && o.hi.includes(s.i) ? ' is-hi' : ''}"/>`;
    }
    for (const [k, seg] of [['gain', g.gainedMost.segment], ['lose', g.lostMost.segment]]) {
      if (!o.marks) break;
      const rows = segRows(g.segments[seg]); const m = rows[Math.floor(rows.length / 2)]; if (!m) continue;
      const [x, y] = pr(m[1], m[2]);
      marks += `<g class="gh-mark is-${k}" transform="translate(${x.toFixed(1)} ${y.toFixed(1)})"><circle r="15"/><text y="5" text-anchor="middle">${k === 'gain' ? '▲' : '▼'}</text></g>`;
    }
    const first = run.track.rows.find((r) => r[1] != null), last = [...run.track.rows].reverse().find((r) => r[1] != null);
    const [sx, sy] = pr(first[1], first[2]), [ex, ey] = pr(last[1], last[2]);
    const label = `Map comparing your run on ${U.fmtDate(run.start)} (solid line) with the run on ${U.fmtDate(rec.start)} (dashed ghost line). ${g.segments.filter((s) => s.aligned && s.band === 'gained').length} segments gained smoothness, ${g.segments.filter((s) => s.aligned && s.band === 'lost').length} lost, ${g.segments.filter((s) => !s.aligned).length} could not be aligned.`;
    return `<div class="ui-map gh-map ${o.cls || ''}" style="width:100%;height:${h}px" role="img" aria-label="${ui.esc(label)}"><svg width="100%" height="100%" viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMidYMid slice">
      <rect width="${w}" height="${h}" fill="var(--dp-data-map-land)"/><path d="${grid}" stroke="var(--dp-data-grid)" stroke-width="1" fill="none"/>
      <g fill="none" stroke="var(--dp-data-map-road)" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round">${roads}</g>
      <path d="${pathOf(rec.track.rows)}" class="gh-ghost"/>
      <path d="${pathOf(run.track.rows)}" class="gh-casing"/>${glow}${segs}
      <circle cx="${sx}" cy="${sy}" r="5" fill="var(--dp-data-map-land)" stroke="var(--dp-data-route)" stroke-width="2.4"/><circle cx="${ex}" cy="${ey}" r="6" fill="var(--dp-data-route)" stroke="var(--dp-data-route-casing)" stroke-width="2"/>${marks}
    </svg><span class="ui-map-attr">© OpenStreetMap contributors</span></div>`;
  }
  function segStrip(ctx, g, o = {}) {
    const { ui } = ctx;
    const cells = g.segments.map((s) => { const b = !s.aligned ? 'unal' : s.band; return `<i class="gh-cell is-${b}${o.hi && o.hi.includes(s.i) ? ' is-hi' : ''}" style="flex:${(s.to - s.from).toFixed(3)}">${b === 'gained' ? '▲' : b === 'lost' ? '▼' : b === 'unal' ? '' : ''}</i>`; }).join('');
    const roads = []; g.segments.forEach((s) => { const l = roads[roads.length - 1]; if (l && l.road === s.road) l.to = s.to; else roads.push({ road: s.road, from: s.from, to: s.to }); });
    const aria = `Segments from start to end: ${g.segments.map((s) => `${s.road} ${!s.aligned ? 'not aligned' : s.band === 'same' ? 'about the same' : s.band}`).join(', ')}.`;
    return `<div class="gh-strip" role="img" aria-label="${ui.esc(aria)}"><div class="gh-cells">${cells}</div><div class="gh-roads" aria-hidden="true">${roads.map((r) => `<span style="flex:${(r.to - r.from).toFixed(3)}">${ui.esc(r.road)}</span>`).join('')}</div></div>`;
  }

  screen('S-RTE-04', {
    name: 'Ghost comparison',
    wp: 'D', platforms: ['ios', 'android'], presentation: 'push', tab: 'routes',
    states: [
      { id: 'default', label: 'Def (new record vs previous record)' },
      { id: 'whereYouGainedLost', label: 'where you gained/lost (latest run vs record)' },
      { id: 'Part', label: 'Part (unaligned segments)' },
      { id: 'Lock', label: 'Lock (Free)' },
    ],
    stateParams: { default: { route: 'r_commute', cmp: 'record' }, whereYouGainedLost: { route: 'r_commute', cmp: 'latest' }, Part: { route: 'r_commute', cmp: 'record' }, Lock: { route: 'r_commute', cmp: 'latest', ent: 'free' } },
    backTo: 'S-RTE-02',
    chrome: { tabBar: false, accessory: false },
    render(ctx) {
      const { ui, data, state } = ctx;
      const g = ctx.params.cmp === 'latest' ? D.ghostLatest : D.ghost;
      const run = data.trip(g.runTripId), rec = data.trip(g.recordTripId);
      const r = data.route(g.routeId);
      const locked = state === 'Lock' || !ctx.isPro;
      const expanded = state === 'whereYouGainedLost' || ctx.store.open;
      const part = state === 'Part';
      const unal = g.segments.filter((s) => !s.aligned).map((s) => s.i);
      const hi = expanded ? [g.gainedMost.segment, g.lostMost.segment] : part ? unal : null;
      const H = ctx.size.h;
      const mapH = Math.round(H * (expanded ? 0.5 : 0.62));
      const isRecRun = run.id === r.record.tripId;
      const top = `<div class="gh-top"><span class="gh-title ${ctx.ios ? 'ui-glassy' : ''}"><b>${ui.esc(r.name)}</b><small>${ui.esc(g.label)}</small></span>
        <div class="gh-legend ${ctx.ios ? 'ui-glassy' : ''}" aria-hidden="true"><span><i class="is-run"></i>${U.fmtDate(run.start)}${isRecRun ? ' · record' : ''}</span><span><i class="is-ghost"></i>${U.fmtDate(rec.start)}${!isRecRun ? ' · record' : ' · previous'}</span></div></div>`;
      const map = ghostMap(ctx, g, { h: mapH, insetTop: ctx.size.top + 96, insetBottom: 30, marks: expanded, hi, w: ctx.size.w });
      const sc = (t, k) => `<div class="gh-run"><span class="gh-run-k"><i class="${k}"></i>${ui.esc(`${U.fmtDate(t.start)} · ${t.partOfDay.split(' ')[0]}`)}${t.id === r.record.tripId ? ` <b class="gh-rec">${rosette(12)}record</b>` : ''}</span><b class="gh-run-s">${ui.num(t.smoothness.score)}</b><span class="gh-run-d">${ui.esc(data.min(t.durationS))}</span></div>`;
      const pair = `<div class="gh-pair" aria-label="${ui.esc(`This run: Smoothness ${run.smoothness.score}, ${data.min(run.durationS)}. Other run: Smoothness ${rec.smoothness.score}, ${data.min(rec.durationS)}.`)}">${sc(run, 'is-run')}${sc(rec, 'is-ghost')}</div><p class="gh-dnote">Durations are shown side by side, never as a difference.</p>`;
      const evid = (seg, txt, k) => {
        const s = g.segments[seg];
        const ev = run.events.find((e) => e.road && s.road && (e.road.includes(s.road) || s.road.includes(e.road)));
        const link = ev ? ui.act({ nav: 'S-RPL-02', params: { trip: run.id, event: ev.id }, label: `See the evidence: ${ctx.icons.eventLabel(ev.type)} at ${ev.place}` }) : ui.act({ nav: 'S-RPL-01', params: { trip: run.id }, label: `Replay this stretch of ${s.road}` });
        return `<div class="gh-call is-${k}"><span class="gh-call-i" aria-hidden="true">${k === 'gain' ? '▲' : '▼'}</span><div><b>${ui.esc(txt.split(':')[0])}</b><p>${ui.esc(txt.split(':').slice(1).join(':').trim())}</p><span class="gh-call-a ui-press" ${link}>${ev ? 'See the evidence' : 'Replay this stretch'}${ui.icon('chevronRight', 14)}</span></div></div>`;
      };
      const calls = expanded ? `<div class="gh-calls">${evid(g.gainedMost.segment, g.gainedMost.text, 'gain')}${evid(g.lostMost.segment, g.lostMost.text, 'lose')}</div>` : ui.button('Where you gained and lost', { kind: ctx.ios ? 'glass' : 'tonal', block: true, icon: 'ghost', state: 'whereYouGainedLost', cls: 'gh-more' });
      const partNote = part || expanded ? `<div class="gh-unal">${ui.icon('info', 15)}<span>${ui.esc(g.unalignedNote)} It’s left out of the comparison.</span></div>` : '';
      const legend = `<div class="gh-key" aria-hidden="true"><span><i class="is-gained"></i>Gained</span><span><i class="is-lost"></i>Lost</span><span><i class="is-same"></i>About the same</span><span><i class="is-unal"></i>Not aligned</span></div>`;
      const panel = `<div class="gh-panel ${ctx.ios ? 'ui-glassy' : ''}">${pair}${segStrip(ctx, g, { hi })}${legend}${partNote}${calls}</div>`;
      if (locked) {
        const inner = `<div class="gh-lockin">${ghostMap(ctx, g, { h: 300, w: 360, pad: 22, marks: false })}${segStrip(ctx, g)}</div>`;
        const body = `<div class="gh-lockpage"><div class="ui-pad">${ui.lockPanel(inner, { text: D.entitlement.lockSheets.compare, nav: 'S-SUB-03', params: { route: r.id, lock: 'compare' } })}</div>
          <div class="ui-pad">${ui.card(`<div class="gh-pair is-plain">${sc(run, 'is-run')}${sc(rec, 'is-ghost')}</div><p class="gh-dnote">Your scores and durations for both runs are free. The overlay and “where you gained” are part of Pro.</p>`)}</div></div>`;
        return ui.page(body, { nav: { title: 'Compare', subtitle: `${r.name} · ${U.fmtDate(run.start)} vs ${U.fmtDate(rec.start)}` } });
      }
      return `<div class="ui-page gh-page">${map}<div class="gh-chrome">${ui.backButton()}${top}</div>${panel}</div>`;
    },
    statusBar: (ctx) => (ctx.theme === 'light' ? 'dark' : 'light'),
    mount(el, ctx) {
      if (still(ctx, el)) return;
      el.querySelectorAll('.gh-page .gh-seg').forEach((p, i) => ctx.motion.drawOn(p, { duration: 260, delay: 300 + i * 70 }));
      el.querySelectorAll('.gh-page .gh-glow').forEach((p) => ctx.motion.animate(p, [{ opacity: 0 }, { opacity: 1 }], { duration: 400, delay: 1200 }));
      const ghost = el.querySelector('.gh-page .gh-ghost'); if (ghost) ctx.motion.animate(ghost, [{ opacity: 0 }, { opacity: 1 }], { duration: 500, delay: 100 });
      popAll(ctx, el, '.gh-page .gh-cell', { delay: 500, stagger: 50 });
      popAll(ctx, el, '.gh-page .gh-mark', { delay: 1300, stagger: 120 });
    },
    spec: {
      purpose: 'Overlay a run with another run of the same route (the record, or the previous record) and show, segment by segment, where smoothness was gained or lost, with evidence links — never a time delta (F-RTE-04). Pro.',
      layout: [
        'Immersive push (no tab bar, no accessory): the map fills the top 62% (50% when the callouts are open), plain canvas with © OpenStreetMap contributors. Floating glass back button + glass title capsule (“The Commute” / “New record (5 Nov) vs previous record (14 Oct)”) and a glass legend capsule (solid = this run, dashed = ghost).',
        'Map: the ghost run in --dp-data-ghost 3 px dashed 2/4; this run distance-aligned into 12 segments: gained = Glide 4 px over a Glide glow 12 px at 35%, lost = neutral text-2 6 px, same = Glide 4 px, not aligned = grey dotted 1/6. Gained-most / lost-most markers (▲ / ▼ in 30 px circles) when open.',
        'Bottom panel (iOS glass card inset 8, radius 32 · Android standard bottom sheet surface-container-high, top radius 28): two run columns (line swatch, date · weekday, record tag, Smoothness DP Numerals 28, duration in neutral text) + “Durations are shown side by side, never as a difference.” · 12-cell segment strip with ▲/▼ glyphs and road labels (Speers Rd · QEW · Gardiner) · key (Gained · Lost · About the same · Not aligned).',
        'Closed: “Where you gained and lost” button (glass / tonal). Open (whereYouGainedLost): two callouts “Where you gained most / Closest to your record” and “Where you lost most”, each with an evidence link; the unaligned note.',
        'Part: the unaligned segment is highlighted in the strip and the note “The last segment could not be aligned (different lane into Spadina). It’s left out of the comparison.” appears.',
        'Lock (Free): standard top bar “Compare”; the user’s own overlay and strip blurred 14 px in a lock panel “Keep comparing every run of The Commute with Pro.” + See plans; below, both runs’ scores and durations stay free.',
      ],
      components: ['map', 'backButton', 'button', 'lockPanel', 'card'],
      interactions: ['Back → S-RTE-02. “Where you gained and lost” → expands (state whereYouGainedLost).', '“See the evidence” → S-RPL-02 with the event in that segment (ghostLatest: Gardiner braking e1); with no event there → “Replay this stretch” → S-RPL-01 (both pass the D1 gate).', 'Lock → S-SUB-03 (compare variant; the user’s own overlay blurred).'],
      motion: ['Hero motion: the ghost fades in (500 ms), then this run’s segments draw on start → end (260 ms each, 70 ms apart, ease.drawOn), segment cells pop in sync, callout markers pop last (shared.pinPop). RM: everything appears complete (220 ms fade).', 'Expand: panel grows (spring.default), map height eases from 62% to 50%.'],
      a11y: ['Map summary: “Map comparing your run on 5 Nov (solid line) with the run on 14 Oct (dashed ghost line). 4 segments gained smoothness, 1 lost, 1 could not be aligned.”', 'Segment strip is one element listing each segment (“Speers Rd lost, QEW about the same, … Gardiner not aligned”).', 'Run pair: “This run: Smoothness 91, 30 min. Other run: Smoothness 89, 32 min.” — no difference is computed or read out.', 'Gained/lost use ▲/▼ glyphs, line weight and dash, not colour alone. XL: panel scrolls; run columns stack.'],
      platformDiffs: { ios: 'Full-bleed MapKit with glass back, title and legend capsules (.glassEffect(.clear) over the map); bottom panel = glass card; buttons .glass.', android: 'Full-bleed map with tonal floating icon button (48 dp) and chips; bottom panel = standard BottomSheetScaffold sheet (peek = run pair + strip), tonal button.' },
      analytics: ['ghost_compare_opened {source, cmp: record|latest, locked}', 'ghost_callouts_opened', 'ghost_evidence_tapped {kind: event|replay}'],
      copy: {
        'Duration note': 'Durations are shown side by side, never as a difference.',
        'Gained (ghost)': 'Where you gained most: the Gardiner. You braked earlier and lighter than on the other run.',
        'Lost (ghost)': 'Where you lost most: Speers Rd. More small corrections than on the other run.',
        'Gained (latest)': 'Closest to your record: the Gardiner, within 1 point.',
        'Lost (latest)': 'Where you lost most: the Gardiner. 1 abrupt input there that the other run didn’t have.',
        Unaligned: 'The last segment could not be aligned (different lane into Spadina). It’s left out of the comparison.',
        Lock: 'Keep comparing every run of The Commute with Pro. (lockSheets.compare) · Your scores and durations for both runs are free. The overlay and “where you gained” are part of Pro.',
        Key: 'Gained · Lost · About the same · Not aligned',
      },
      freePro: 'Pro (and Preview / Ride-along). Free after the Preview: Lock state — the overlay is blurred, the two scores and durations stay visible; See plans → S-SUB-03.',
      data: 'default / Part: DP_DATA.ghost (run t20261105-0742 91 · 30 min vs t20261014-0744 89 · 32 min; 12 segments, #11 unaligned). whereYouGainedLost / Lock: DP_DATA.ghostLatest (t20261103-0737 86 vs record t20261105-0742). Segment geometry: fractions of distance mapped onto the run track by cumulative haversine distance (derived).',
      icons: ['ghost', 'rosette', 'info', 'chevronRight', 'chevronLeft'],
      assets: [],
    },
  });

  /* ---------- S-RTE-05 · Route management sheet ---------- */
  screen('S-RTE-05', {
    name: 'Route management sheet',
    wp: 'D', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'routes',
    over: (ctx) => (ctx.state === 'chooseActiveRoutes' ? 'S-RTE-01/freeLimitWith3rdRouteLocked' : { id: 'S-RTE-02', params: { route: ctx.params.route || 'r_commute' } }),
    detent: 'large',
    states: [
      { id: 'default', label: 'Rename' },
      { id: 'merge', label: 'merge' },
      { id: 'linkDirections', label: 'link directions' },
      { id: 'remove', label: 'remove' },
      { id: 'chooseActiveRoutes', label: 'choose active routes (Free)' },
    ],
    stateParams: { default: { route: 'r_commute' }, merge: { route: 'r_commute' }, linkDirections: { route: 'r_gym' }, remove: { route: 'r_gym' }, chooseActiveRoutes: { ent: 'free' } },
    render(ctx) {
      const { ui, data, state } = ctx;
      if (state === 'chooseActiveRoutes') {
        const chosen = ctx.store.active || D.entitlement.states.free.activeRouteIds;
        const named = D.routes.filter((r) => r.named).sort((a, b) => b.runCount - a.runCount);
        const rows = named.map((r) => { const on = chosen.includes(r.id); const dis = !on && chosen.length >= 2; return `<div class="rm-pick ui-press ${on ? 'is-on' : ''} ${dis ? 'is-dim' : ''}" role="checkbox" aria-checked="${on}" tabindex="0" ${ui.act({ action: 'toggleActive', arg: r.id, haptic: 'selection', noRole: true, label: `${r.name}, ${r.runCount} drives${on ? ', active' : ''}` })}><span class="rm-check">${on ? ui.icon('check', 16) : ''}</span>${ui.glyph(r.glyph, 40)}<span class="rm-pick-t"><b>${ui.esc(r.name)}</b><small>${ui.esc(`${r.from} → ${r.to} · ${plural(r.runCount, 'drive')}${r.record ? ` · record ${r.record.score}` : ''}`)}</small></span></div>`; }).join('');
        return `${ui.sheetHeader('Choose your 2 routes', { sub: `${chosen.length} of 2 chosen · Free plan` })}<div class="rm">
          <p class="rm-p">Active routes get records, trends and their full page. The others are still recorded and matched, so nothing is lost if you swap later.</p>
          <div class="rm-picks" role="group" aria-label="Active routes">${rows}</div>
          ${ui.button(chosen.length === 2 ? 'Save active routes' : `Choose ${2 - chosen.length} more`, { kind: 'filled', block: true, size: 'l', action: 'saveActive', disabled: chosen.length !== 2 })}
          <p class="rm-foot">Want every route? <span class="rm-link ui-press" ${ui.act({ nav: 'S-SUB-03/s3rdRoute' })}>Track every route you drive with Pro</span></p></div>`;
      }
      const r = data.route(ctx.params.route) || data.route('r_commute');
      const seg = ui.segmented([{ id: 'default', label: 'Rename' }, { id: 'merge', label: 'Merge' }, { id: 'linkDirections', label: 'Link' }, { id: 'remove', label: 'Remove' }].map((o) => ({ ...o, state: o.id })), { selected: state, cls: 'rm-seg' });
      let body = '';
      if (state === 'default') {
        body = `<div class="rm-field">${ui.textField({ label: 'Route name', value: ctx.store.name ?? r.name, helper: `${(ctx.store.name ?? r.name).length} of 24 · never a street address`, focused: true })}</div>
          <p class="rm-p">Renaming keeps every drive, the record and the trend.</p>${ui.button('Save name', { kind: 'filled', block: true, size: 'l', action: 'done', arg: 'Route renamed' })}`;
      } else if (state === 'merge') {
        const others = D.routes.filter((x) => x.id !== r.id);
        const pick = ctx.store.merge;
        const p = pick && U.route(pick);
        body = `<p class="rm-p">Merge a route that is really the same drive into ${ui.esc(r.name)}. Every drive is kept and the record is recomputed.</p>
          <div class="rm-picks" role="radiogroup">${others.map((x) => `<div class="rm-pick ui-press ${pick === x.id ? 'is-on' : ''}" role="radio" aria-checked="${pick === x.id}" tabindex="0" ${ui.act({ action: 'pickMerge', arg: x.id, haptic: 'selection', noRole: true, label: `${routeName(x)}, ${x.runCount} drives` })}><span class="ui-radio ${pick === x.id ? 'is-on' : ''}"></span>${ui.glyph(x.glyph, 40)}<span class="rm-pick-t"><b>${ui.esc(routeName(x))}</b><small>${ui.esc(`${x.from} → ${x.to} · ${plural(x.runCount, 'drive')}`)}</small></span></div>`).join('')}</div>
          ${p ? `<div class="rm-preview">${ui.icon('layers', 18)}<span><b>${ui.esc(`${r.name} · ${r.runCount + p.runCount} drives after merging`)}</b><small>${ui.esc(p.from !== r.from || p.to !== r.to ? `${routeName(p)} runs the other way (${p.from} → ${p.to}). Linking may be what you want.` : 'The record and trend recompute within 2 seconds.')}</small></span></div>` : ''}
          ${ui.button(p ? `Merge into ${r.name}` : 'Choose a route to merge', { kind: 'filled', block: true, size: 'l', action: 'done', arg: 'Routes merged · record recomputed', disabled: !p })}`;
      } else if (state === 'linkDirections') {
        const rev = r.linkedWith ? U.route(r.linkedWith) : reverseOf(r);
        const on = ctx.store.link ?? !!r.linkedWith;
        body = rev ? `<div class="rm-link2"><div>${ui.glyph(r.glyph, 52)}<b>${ui.esc(routeName(r))}</b><small>${ui.esc(`${r.from} → ${r.to}`)}</small></div><span class="rm-link2-i" aria-hidden="true">⇄</span><div>${ui.glyph(rev.glyph, 52)}<b>${ui.esc(routeName(rev))}</b><small>${ui.esc(`${rev.from} → ${rev.to}`)}</small></div></div>
          ${ui.list([ui.row({ icon: 'routes', title: 'Link both directions', sub: 'Shown together on one page', trailing: 'toggle', on, action: 'link' })], { cls: 'rm-list' })}
          <p class="rm-p">Linked routes share a page and a combined trend. Each direction keeps its own record, because the drives are different.</p>${ui.button('Done', { kind: 'filled', block: true, size: 'l', action: 'done', arg: on ? 'Directions linked' : 'Directions unlinked' })}`
          : `<p class="rm-p">No route runs the other way yet. Drive ${ui.esc(r.to)} → ${ui.esc(r.from)} twice and you can link them.</p>`;
      } else if (state === 'remove') {
        body = `<div class="rm-rem">${ui.glyph(r.glyph, 56)}<div><b>${ui.esc(`Remove ${routeName(r)}?`)}</b><p>${ui.esc(`Your ${plural(r.runCount, 'drive')} stay in your history. They’re no longer grouped, and the route record is cleared.`)}</p></div></div>
          ${ui.button('Remove route', { kind: 'destructive', block: true, size: 'l', action: 'done', arg: 'Route removed · drives kept' })}
          ${ui.section('Or unlink a single drive', ui.list(r.runs.slice().reverse().map((x) => ui.row({ title: `${U.fmtDate(x.date)} · ${x.partOfDay}`, sub: `Smoothness ${x.score}`, trailing: ui.button('Unlink', { kind: 'text', size: 's', toast: 'Drive unlinked · record recomputed' }) })), { cls: 'rm-list' }), { style: 'caption' })}`;
      }
      return `${ui.sheetHeader(`Manage “${routeName(r)}”`, { sub: `${r.from} → ${r.to} · ${plural(r.runCount, 'drive')}` })}<div class="rm">${seg}${body}</div>`;
    },
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '.rm > *', { stagger: 30 });
      const f = el.querySelector('.ui-field-in'); if (f) f.addEventListener('input', () => { ctx.store.name = f.textContent.slice(0, 24); });
    },
    actions: {
      toggleActive: (ctx, id) => { const cur = (ctx.store.active || D.entitlement.states.free.activeRouteIds).slice(); const i = cur.indexOf(id); if (i >= 0) cur.splice(i, 1); else if (cur.length < 2) cur.push(id); else return false; ctx.store.active = cur; ctx.update(); return false; },
      saveActive: (ctx) => { ctx.toast('Active routes updated · no drives lost', { icon: 'check' }); returnTo(ctx, {}); return false; },
      pickMerge: (ctx, id) => { ctx.store.merge = id; ctx.update(); return false; },
      link: (ctx) => { const r = ctx.data.route(ctx.params.route) || ctx.data.route('r_commute'); ctx.store.link = !(ctx.store.link ?? !!r.linkedWith); ctx.update(); return false; },
      done: (ctx, msg) => { ctx.toast(msg || 'Saved', { icon: 'check' }); returnTo(ctx, {}); return false; },
    },
    spec: {
      purpose: 'Manage a route — rename, merge, link directions, remove it or unlink a single drive — and, on Free, choose which 2 routes are active (F-RTE-05, F-RTE-06). Records and trends recompute within 2 s; no trip is ever deleted.',
      layout: [
        'Large sheet. Header “Manage “The Commute”” + “Home → Work · 10 drives”. Mode selector: Rename · Merge · Link · Remove (iOS glass segmented · Android connected button group).',
        'Rename: text field (focused, helper “11 of 24 · never a street address”) · note · “Save name” (filled, large).',
        'Merge: note · radio cards (glyph 40, name, direction · drives) · preview line (“The Commute · 12 drives after merging” or the “runs the other way… Linking may be what you want.” hint) · “Merge into The Commute”.',
        'Link: two glyph tiles with ⇄ between · “Link both directions” switch row · note “Each direction keeps its own record” · Done.',
        'Remove: glyph + “Remove Gym?” + “Your 2 drives stay in your history…” · destructive “Remove route” · “Or unlink a single drive” list with Unlink text buttons.',
        'Choose active routes (Free, from S-SUB-07 or the Routes tab): header “Choose your 2 routes · 2 of 2 chosen · Free plan”, explanation, check cards sorted by runs (a third card dims once 2 are chosen), “Save active routes”, footer link “Track every route you drive with Pro”.',
      ],
      components: ['sheetHeader', 'segmented', 'textField', 'glyph', 'list', 'row', 'toggle', 'button', 'section'],
      interactions: ['Mode segments switch state in place (selection haptic). Save / Merge / Done / Remove → toast + dismiss to S-RTE-02 (or S-RTE-01).', 'Unlink → toast “Drive unlinked · record recomputed”.', 'Choose: tapping a card toggles it; a third tap is ignored while 2 are chosen (the dimmed card says why). Save → “Active routes updated · no drives lost”. The Pro link → S-SUB-03 s3rdRoute.'],
      motion: ['Sheet spring.sheet (large detent recedes the parent on iOS). Hero motion: content rises 8 px, 30 ms stagger; check/radio fill spring.fast. RM: cross-fades.'],
      a11y: ['Check cards are checkboxes: “The Commute, 10 drives, active”. Radio cards in a radio group.', 'Destructive button reads “Remove route”; the consequence text precedes it.', 'XL: segmented labels shorten to icons + labels on two lines; cards wrap text; buttons stay full width.'],
      platformDiffs: { ios: 'Sheet with large detent; glass segmented Picker; TextField; Toggle; destructive role button.', android: 'ModalBottomSheet full height; connected ButtonGroup; OutlinedTextField; Switch; error-coloured FilledTonalButton for Remove; Checkbox cards.' },
      analytics: ['route_managed {action: rename|merge|link|unlink|remove}', 'route_active_swapped'],
      copy: {
        Rename: 'Renaming keeps every drive, the record and the trend.',
        Merge: 'Merge a route that is really the same drive into {route}. Every drive is kept and the record is recomputed.',
        Link: 'Linked routes share a page and a combined trend. Each direction keeps its own record, because the drives are different.',
        Remove: 'Remove {route}? Your {n} drives stay in your history. They’re no longer grouped, and the route record is cleared.',
        Choose: 'Active routes get records, trends and their full page. The others are still recorded and matched, so nothing is lost if you swap later.',
        'Choose CTA': 'Save active routes / Choose {n} more',
        'Pro link': 'Want every route? Track every route you drive with Pro',
      },
      freePro: 'Rename / merge / link / remove are Free. chooseActiveRoutes is the Free-only chooser (entitlement.states.free.activeRouteIds); default = the 2 routes with the most runs (F-RTE-05.3).',
      data: 'Rename/merge: r_commute. Link/remove: r_gym (reverse = r_cand_gym_home, Gym → Home). Choose: The Commute (10), The Commute home (6), Gym (2) with ent free.',
      icons: ['check', 'layers', 'routes', 'trash', 'close'],
      assets: [],
    },
  });

  /* ==========================================================================================================
     Garage (S-GAR-01 · S-GAR-02 · S-GAR-03)
     ========================================================================================================== */
  function carCard(ctx, v, o = {}) {
    const { ui, data } = ctx;
    const s = carStats(v.id);
    const inactive = o.inactive;
    const tags = [v.default && !inactive ? ui.chip('Default', { kind: 'pro', cls: 'gar-def' }) : '', inactive ? ui.chip('Inactive on Free', { kind: 'outline' }) : '', ui.chip(POWER[v.powertrain] || v.powertrain, { kind: 'plain', icon: v.powertrain === 'ev' ? 'bolt' : 'car' })].join('');
    const aria = `${v.nickname}, ${v.year} ${v.make} ${v.model}, ${v.colourName}. ${s.drives} drives, ${Math.round(s.km)} kilometres, average Smoothness ${s.avg}.${v.default ? ' Default car.' : ''}${inactive ? ' Inactive on the Free plan.' : ''}`;
    const statsRow = `<div class="gar-nums"><span>${ui.num(s.drives)}<small>drives</small></span><span>${ui.num(Math.round(s.km), 'km')}<small>distance</small></span><span>${ui.num(s.avg ?? '—')}<small>avg Smoothness</small></span></div>`;
    const extra = inactive ? `<div class="gar-inact"><span>Added during your Pro Preview. Its ${plural(s.drives, 'drive')} stay here. On Free, one car is active at a time.</span>${ui.button('Make active', { kind: ctx.ios ? 'glass' : 'tonal', size: 's', action: 'swapActive', arg: v.id })}</div>` : '';
    return ui.card(`<div class="gar-card ${inactive ? 'is-inactive' : ''}"><div class="gar-card-art">${carArt(v, o.hero ? 300 : 230)}</div><div class="gar-card-h"><div><b class="gar-name">${ui.esc(v.nickname)}</b><span>${ui.esc(`${v.year} ${v.make} ${v.model}`)} · <i class="gar-swatch" style="background:var(--dp-car-${v.colour})"></i>${ui.esc(v.colourName)}</span></div>${ui.icon('chevronRight', 18)}</div><div class="gar-tags">${tags}</div>${statsRow}${extra}</div>`, { hero: true, tint: `car-${v.colour}`, nav: 'S-GAR-02', params: { car: v.id }, zoom: true, label: aria, cls: 'gar-carcard' });
  }

  screen('S-GAR-01', {
    name: 'Garage',
    wp: 'D', platforms: ['ios', 'android'], presentation: 'root', tab: 'garage',
    states: [
      { id: 'default', label: 'Def (1 car)' },
      { id: 'multipleCars', label: 'multiple cars' },
      { id: 'Lock', label: 'Lock (add 2nd car on Free)' },
      { id: 'Empty', label: 'Empty' },
    ],
    stateParams: { Lock: { ent: 'free' } },
    render(ctx) {
      const { ui, state } = ctx;
      const free = !ctx.isPro;
      const cars = state === 'Empty' ? [] : state === 'default' ? D.vehicles.slice(0, 1) : D.vehicles.slice();
      const active = ctx.store.active || D.entitlement.states.free.activeVehicleId;
      const addNav = free && cars.length ? { nav: 'S-SUB-03/s2ndCar' } : { nav: 'S-GAR-03' };
      const plus = ui.iconButton('plus', { label: free && cars.length ? 'Add a car, Pro' : 'Add a car', ...addNav });
      const trailing = rootTrailing(ctx, [plus]);
      const sub = ctx.android ? (cars.length ? plural(cars.length, 'car') : 'No cars yet') : null;
      if (!cars.length) {
        const art = `<div class="gar-empty-art">${carArt(null, 200, { colour: 'glide' })}</div>`;
        const body = `<div class="ui-pad" style="margin-top:8px">${ui.card(ui.empty({ art, title: 'Add the car you drive most.', text: 'Its colour tints your Stories and share cards, and it keeps its own stats. No VIN or plate, and it stays on your phone.', action: { text: 'Add a car', nav: 'S-GAR-03', icon: 'plus' } }), { hero: true })}</div><p class="gar-note">Until then, drives are saved under “My car”.</p>`;
        return ui.page(`<div class="gar">${body}</div>`, { large: { title: 'Garage', subtitle: sub, trailing } });
      }
      const blocks = cars.map((v) => `<div class="ui-pad">${carCard(ctx, v, { hero: cars.length === 1, inactive: free && v.id !== active })}</div>`);
      const addCard = free
        ? `<div class="ui-pad"><div class="gar-add is-locked ui-press" ${ui.act({ nav: 'S-SUB-03/s2ndCar', label: `Add a car. ${D.entitlement.lockSheets.secondCar}` })}><span class="gar-add-i">${ui.icon('lock', 20)}</span><span><b>Add a car ${ui.proBadge()}</b><small>${ui.esc(D.entitlement.lockSheets.secondCar)}</small></span>${ui.icon('chevronRight', 16)}</div></div>`
        : `<div class="ui-pad"><div class="gar-add ui-press" ${ui.act({ nav: 'S-GAR-03', label: 'Add a car' })}><span class="gar-add-i">${ui.icon('plus', 22)}</span><span><b>Add a car</b><small>${cars.length > 1 ? 'Each car keeps its own stats and colour.' : 'A second car? Each one keeps its own stats and colour.'}</small></span>${ui.icon('chevronRight', 16)}</div></div>`;
      blocks.push(addCard);
      if (cars.length > 1) {
        const def = cars.find((v) => v.default);
        blocks.push(ui.section('', ui.list([ui.row({ icon: 'car', title: 'Default car', sub: 'New drives are assigned to it. Change any drive later.', value: ui.esc(def.nickname), nav: 'S-GAR-03/edit', params: { car: def.id } })]), { style: 'caption' }));
      }
      return ui.page(`<div class="gar">${blocks.map((b) => `<div class="gar-b" data-rise>${b}</div>`).join('')}</div>`, { large: { title: 'Garage', subtitle: sub, trailing } });
    },
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '.gar-b[data-rise]', { stagger: 50 });
      drawPaths(ctx, el, '.gar-art-b', { duration: 1300, delay: 120, stagger: 180 });
    },
    actions: {
      swapActive: (ctx, id) => { ctx.store.active = id; ctx.toast(`${U.vehicle(id).nickname} is now your active car`, { icon: 'check' }); ctx.update(); return false; },
    },
    spec: {
      purpose: 'The Garage tab: the user’s cars as identity — nickname, colour and silhouette tint their Stories and share cards — with per-car stats one tap away (F-GAR-01, F-GAR-02). Free has 1 active car; Pro is unlimited.',
      layout: [
        'Large title “Garage”; trailing “+” (Add a car; Free with a car → S-SUB-03 s2ndCar) + Preview badge + avatar. Android subtitle “2 cars”.',
        'Car card per car (hero radius 32 / 28, tinted 18% with the car colour): generic body-style silhouette (hatch/crossover, outline in the car colour at 20% fill, 230–300 wide) · nickname (DP display 28) · “2022 Volkswagen Golf GTI · ● Kings Red” · tags Default (accent container) / Inactive on Free / powertrain · three numbers: drives · km · avg Smoothness (DP Numerals).',
        '“Add a car” dashed card (+ glyph, “A second car? Each one keeps its own stats and colour.”). Free: lock + PRO badge + “Keep a log for every car in your garage with Pro.”',
        'Multiple cars: a “Default car” row (value GTI) → S-GAR-03/edit.',
        'Lock (Free after the Preview): the Ioniq card shows “Inactive on Free” with “Added during your Pro Preview. Its 4 drives stay here. On Free, one car is active at a time.” + “Make active”.',
        'Empty: hero card with an outline silhouette, “Add the car you drive most.” + filled “Add a car”; note “Until then, drives are saved under “My car”.”',
      ],
      components: ['largeTitle', 'iconButton', 'avatar', 'card', 'chip', 'button', 'proBadge', 'list', 'row', 'empty'],
      interactions: ['Car card → S-GAR-02 (iOS zoom / Android container transform). “+” / Add card → S-GAR-03 (add) or S-SUB-03 s2ndCar (Free).', '“Make active” → swaps the active car on Free (toast); no trips move or disappear.', 'Default car row → S-GAR-03/edit.'],
      motion: ['Hero motion: each car silhouette draws its outline (1300 ms ease.drawOn, 180 ms stagger) as the cards rise 8 px (50 ms stagger). RM: silhouettes appear complete.', 'Card → detail: zoom / container transform (spring.slow).'],
      a11y: ['Car card is one button: “GTI, 2022 Volkswagen Golf GTI, Kings Red. 25 drives, 794 kilometres, average Smoothness 84. Default car.”', 'The silhouette is decorative (its colour is also named in text). Lock card: “Add a car. Keep a log for every car in your garage with Pro.”', 'XL: numbers wrap to two rows; tags wrap; silhouette scales to the card width.'],
      platformDiffs: { ios: 'Large title + glass “+” toolbar button and avatar; car cards opaque surface with the car-colour wash; floating tab bar + accessory.', android: 'LargeFlexibleTopAppBar with subtitle; car cards surface-container-high (28 dp) with wash; tonal “Make active”; NavigationBar.' },
      analytics: ['garage_opened {car_count}', 'car_detail_opened {source: garage}', 'car_add_tapped {locked}', 'car_active_swapped'],
      copy: { Title: 'Garage', Add: 'Add a car · A second car? Each one keeps its own stats and colour.', 'Add (Free)': 'Add a car PRO · Keep a log for every car in your garage with Pro.', Inactive: 'Added during your Pro Preview. Its {n} drives stay here. On Free, one car is active at a time. · Make active', Empty: 'Add the car you drive most. Its colour tints your Stories and share cards, and it keeps its own stats. No VIN or plate, and it stays on your phone.', 'Empty note': 'Until then, drives are saved under “My car”.' },
      freePro: 'Free: 1 active car (entitlement.states.free.activeVehicleId = gti); cars added during the Preview stay visible with their trips and the user picks the active one; Add → S-SUB-03 s2ndCar. Pro/Preview: unlimited cars.',
      data: 'Default simulates a one-car garage with vehicles[0] (GTI). multipleCars/Lock: both vehicles. Per-car numbers derived with carStats (driver trips only): GTI 25 drives · 794 km · 84; Ioniq 4 · 197 km · 87.',
      icons: ['plus', 'lock', 'car', 'bolt', 'chevronRight'],
      assets: [],
    },
  });

  /* ---------- S-GAR-02 · Car detail ---------- */
  screen('S-GAR-02', {
    name: 'Car detail',
    wp: 'D', platforms: ['ios', 'android'], presentation: 'push', tab: 'garage',
    states: [{ id: 'default', label: 'Def' }, { id: 'ev', label: 'EV' }, { id: 'noTripsYet', label: 'no trips yet' }],
    stateParams: { default: { car: 'gti' }, ev: { car: 'ioniq' }, noTripsYet: { car: 'ioniq' } },
    backTo: 'S-GAR-01',
    render(ctx) {
      const { ui, data, state } = ctx;
      const v = data.vehicle(ctx.params.car) || data.vehicle('gti');
      const none = state === 'noTripsYet';
      const s = none ? { drives: 0, km: 0, durationS: 0, avg: null, better: 0, effN: 0, trips: [], scored: [] } : carStats(v.id);
      const ev = v.powertrain === 'ev';
      const nav = { title: v.nickname, subtitle: `${v.year} ${v.make} ${v.model}`, trailing: [ui.iconButton('edit', { nav: 'S-GAR-03/edit', params: { car: v.id }, label: `Edit ${v.nickname}` })] };
      const hero = ui.card(`<div class="gd-hero"><div class="gd-art">${carArt(v, 320)}</div><div class="gar-tags">${v.default ? ui.chip('Default car', { kind: 'pro' }) : ui.chip('Make default', { kind: 'outline', icon: 'check', toast: `${v.nickname} is now your default car` })}${ui.chip(POWER[v.powertrain], { kind: 'plain', icon: ev ? 'bolt' : 'car' })}${ui.chip(v.transmission === 'manual' ? 'Manual' : v.transmission === 'single-speed' ? 'Single-speed' : 'Automatic', { kind: 'plain' })}<span class="gd-col"><i class="gar-swatch" style="background:var(--dp-car-${v.colour})"></i>${ui.esc(v.colourName)}</span></div><p class="gd-tint">${ui.icon('story', 14)}Tints its Stories and share cards</p></div>`, { hero: true, tint: `car-${v.colour}`, cls: 'gd-herocard' });
      const blocks = [`<div class="ui-pad">${hero}</div>`];
      if (none) {
        blocks.push(`<div class="ui-pad">${ui.card(ui.empty({ icon: 'drives', title: `No drives in the ${v.nickname} yet.`, text: `New drives go to your default car (${D.vehicles.find((x) => x.default).nickname}). Make the ${v.nickname} the default before you set off, or switch the car on a drive afterwards.`, action: { text: 'Make default', kind: 'tonal', toast: `${v.nickname} is now your default car` } }))}</div>`);
      } else {
        blocks.push(`<div class="ui-pad"><div class="ui-stats" style="--cols:2">${tile(ui, { label: 'Drives', html: ui.num(s.drives), aria: `Drives, ${s.drives}`, prov: 'observed' })}${tile(ui, { label: 'Average Smoothness', html: ui.num(s.avg), aria: `Average Smoothness, ${s.avg}. Derived`, prov: 'derived' })}${tile(ui, { label: 'Distance', html: ui.num(Math.round(s.km), 'km'), aria: `Distance, ${Math.round(s.km)} kilometres. Derived`, prov: 'derived' })}${tile(ui, { label: 'Time', html: durHtml(ui, s.durationS), aria: `Time, ${data.min(s.durationS)}`, prov: 'observed' })}</div></div>`);
        const effCopy = (s.latest && s.latest.efficiency && s.latest.efficiency.copy) || '';
        blocks.push(ui.section('Efficiency habits', `<div class="ui-pad">${ui.card(`<div class="gd-eff"><span class="gd-eff-i">${ui.icon(ev ? 'bolt' : 'car', 20)}</span><div><b>Better than usual on ${ui.num(s.better)} of ${ui.num(s.effN)} drives</b><span class="dp-est">estimated from stops, starts and steady speed</span><p>${ui.esc(effCopy)}</p></div>${ui.icon('chevronRight', 16)}</div>`, { nav: ev ? 'S-SCO-06/evCarVsPetrolCarCopy' : 'S-SCO-06', params: { trip: s.latest.id }, label: `Efficiency habits: better than usual on ${s.better} of ${s.effN} drives, estimated. ${effCopy}` })}</div>`, { style: 'caption' }));
        // per-car trend: Pro → S-STA-02/perCar; Free → locked own data
        const weeks = D.recaps.slice().reverse();
        const vals = weeks.map((r) => avgInt(s.scored.filter((t) => ymd(t.start) >= r.weekStart && ymd(t.start) <= r.weekEnd).map((t) => t.smoothness.score))).filter((x) => x != null);
        const spark = vals.length > 1 ? ui.sparkline(vals, { w: 300, h: 56, color: 'var(--dp-accent)', area: true, label: `${v.nickname} weekly average Smoothness: ${vals.join(', ')}` }) : '';
        const trendInner = `<div class="gd-trend"><div><b>Trend in the ${ui.esc(v.nickname)}</b><span>${vals.length > 1 ? `${vals[0]} → ${vals[vals.length - 1]} over ${plural(vals.length, 'week')}` : 'More weeks needed'}</span></div>${spark}</div>`;
        blocks.push(ui.section('Trend', `<div class="ui-pad">${ctx.isPro ? ui.card(trendInner + `<span class="trp-link">${ui.icon('insights', 15)}Compare your cars</span>`, { nav: 'S-STA-02/perCar', params: { car: v.id }, label: `Trend in the ${v.nickname}. Opens trends by car.` }) : ui.lockPanel(ui.card(trendInner), { text: D.entitlement.lockSheets.trends, nav: 'S-SUB-03/trends' })}</div>`, { style: 'caption' }));
        blocks.push(ui.section(`Recent drives in the ${v.nickname}`, `<div class="ui-pad"><div class="ui-card is-flush gd-trips" role="list">${s.trips.slice(0, 4).map((t) => ui.tripRow(t, { ent: ctx.ent, cars: false })).join('')}</div></div>`, { style: 'caption', action: { text: 'All', nav: 'S-HIS-01', params: { car: v.id } } }));
      }
      blocks.push(ui.section('Details', ui.list([
        ui.row({ title: 'Make and model', value: ui.esc(`${v.make} ${v.model}`) }),
        ui.row({ title: 'Year', value: String(v.year) }),
        ui.row({ title: 'Powertrain', value: ui.esc(POWER[v.powertrain]) }),
        ui.row({ title: 'Added', value: ui.esc(longDate(v.addedOn)) }),
        ui.row({ icon: 'edit', title: 'Edit car', nav: 'S-GAR-03/edit', params: { car: v.id } }),
      ]), { style: 'caption', footer: 'No VIN or plate. Car details stay on this phone.' }));
      return ui.page(`<div class="gd">${blocks.map((b) => `<div class="gar-b" data-rise>${b}</div>`).join('')}</div>`, { nav });
    },
    mount(el, ctx) {
      ctx.ui.anim.rise(el, '.gar-b[data-rise]', { stagger: 35, delay: 60 });
      drawPaths(ctx, el, '.gd-art .gar-art-b', { duration: 1300, delay: 100 });
    },
    spec: {
      purpose: 'One car’s identity and stats: drives, distance, time, average Smoothness and efficiency habits for that car, recent drives in it, and the way into per-car Pro trends (F-GAR-01, F-GAR-03).',
      layout: [
        'Top bar: nickname + “2022 Volkswagen Golf GTI”, glass back, trailing Edit (pencil) → S-GAR-03/edit.',
        'Hero card (radius 32 / 28, car-colour wash 18%): silhouette 320 wide drawn in the car colour · tags: Default car / Make default · powertrain (bolt for EV) · transmission · colour swatch + colour name · “Tints its Stories and share cards”.',
        'Stat tiles 2×2: Drives · Average Smoothness · Distance · Time (provenance glyphs).',
        '“Efficiency habits” card → S-SCO-06: “Better than usual on 12 of 25 drives”, “≈ estimated from stops, starts and steady speed” (dashed underline, 60%), the fixture copy (petrol: “…help fuel use. Check your car’s trip computer…”; EV: “…help range. Check your car’s display…”). Never energy, litres or kWh.',
        '“Trend” card: weekly sparkline (Glide area) “84 → 86 over 6 weeks” + “Compare your cars” (Pro → S-STA-02/perCar) · Free: the same card blurred in a lock panel.',
        '“Recent drives in the GTI” (4 trip cards) + “All ›” → S-HIS-01 filtered by car. “Details” list (make and model, year, powertrain, added) + “Edit car”; footer “No VIN or plate. Car details stay on this phone.”',
        'No trips yet: empty card “No drives in the Ioniq yet.” with the default-car explanation and “Make default”.',
      ],
      components: ['page', 'navBar', 'iconButton', 'card', 'chip', 'stats', 'section', 'list', 'row', 'tripRow', 'sparkline', 'lockPanel', 'empty'],
      interactions: ['Edit / “Edit car” → S-GAR-03/edit (sheet). Efficiency card → S-SCO-06 (EV: evCarVsPetrolCarCopy). Trend → S-STA-02/perCar (Pro) / S-SUB-03 trends (Free). Trip card → S-TRP-01. “All” → S-HIS-01 with car filter.', '“Make default” → toast; new drives go to this car.'],
      motion: ['Enter: zoom / container transform from the Garage card. Hero motion: the silhouette outline draws on (1300 ms ease.drawOn); blocks rise 8 px, 35 ms stagger. RM: static silhouette, cross-fade.'],
      a11y: ['Stat tiles: “Average Smoothness, 84. Derived.” Efficiency card: “Efficiency habits: better than usual on 12 of 25 drives, estimated.”', 'Sparkline summary: “GTI weekly average Smoothness: 78, 81, 85, 85, 87, 86.”', 'Colour swatch is paired with the colour name; the silhouette is decorative.', 'XL: tiles go to 1 column at AX5; tags wrap; the silhouette shrinks to the card width.'],
      platformDiffs: { ios: 'Inline title + subtitle, glass back and Edit; zoom transition; inset-grouped Details list.', android: 'MediumFlexibleTopAppBar with Edit action; container transform; segmented ListItems; tonal chips.' },
      analytics: ['car_detail_opened {powertrain, trips_bucket}', 'efficiency_viewed {source: car}', 'trend_viewed {dimension: car, locked}'],
      copy: { 'Efficiency (petrol)': 'Smoother habits like these usually help fuel use. Check your car’s trip computer for your number.', 'Efficiency (EV)': 'Smoother habits like these usually help range. Check your car’s display to see your number.', 'Efficiency line': 'Better than usual on {n} of {m} drives · ≈ estimated from stops, starts and steady speed', 'No trips': 'No drives in the {car} yet. New drives go to your default car ({default}). Make the {car} the default before you set off, or switch the car on a drive afterwards.', Footer: 'No VIN or plate. Car details stay on this phone.', Tint: 'Tints its Stories and share cards' },
      freePro: 'Free: everything except the per-car trend, which shows the user’s own sparkline blurred → S-SUB-03 trends. Pro: trend card → S-STA-02/perCar.',
      data: 'default: gti (carStats: 25 drives · 794 km · 12 h 15 min · 84 · 12 of 25 better). ev: ioniq (4 drives · 197 km · 87 · 2 of 3; copy from t20261024-1610.efficiency). noTripsYet: the Ioniq as it was 4–16 Oct (added 4 Oct, first drive 17 Oct). Weekly sparkline derived per recap week.',
      icons: ['edit', 'bolt', 'car', 'story', 'insights', 'check', 'chevronRight', 'drives'],
      assets: [],
    },
  });

  /* ---------- S-GAR-03 · Add / edit car ---------- */
  const COLOURS = ['carmine', 'ember', 'sodium', 'lime', 'glide', 'glacier', 'lucid', 'cobalt', 'violet', 'graphite', 'pearl', 'onyx'];
  const BODIES = [['hatch', 'Hatch'], ['sedan', 'Sedan'], ['crossover', 'Crossover'], ['suv', 'SUV'], ['coupe', 'Coupe'], ['wagon', 'Wagon'], ['pickup', 'Pickup']];
  function garForm(ctx) {
    const v = ctx.state === 'default' ? null : ctx.data.vehicle(ctx.params.car) || ctx.data.vehicle('gti');
    const s = ctx.store;
    return {
      v,
      name: s.name ?? (v ? v.nickname : ''),
      make: v ? v.make : '', model: v ? v.model : '', year: v ? String(v.year) : '',
      colour: s.colour || (v ? v.colour : 'cobalt'),
      power: s.power || (v ? v.powertrain : 'petrol'),
      body: s.body || (v ? v.silhouette : 'hatch'),
      isDefault: s.isDefault ?? (v ? v.default : false),
    };
  }
  screen('S-GAR-03', {
    name: 'Add / edit car',
    wp: 'D', platforms: ['ios', 'android'], presentation: 'sheet', tab: 'garage',
    over: (ctx) => (ctx.state === 'default' ? 'S-GAR-01/multipleCars' : { id: 'S-GAR-02', params: { car: ctx.params.car || 'gti' } }),
    detent: 'large',
    states: [{ id: 'default', label: 'Add' }, { id: 'edit', label: 'edit' }, { id: 'deleteConfirm', label: 'delete confirm' }, { id: 'bluetoothLink', label: 'Bluetooth link (P2)' }],
    stateParams: { edit: { car: 'gti' }, deleteConfirm: { car: 'gti' }, bluetoothLink: { car: 'gti' } },
    render(ctx) {
      const { ui, state } = ctx;
      const f = garForm(ctx);
      const add = state === 'default';
      const preview = { nickname: f.name || 'Your car', colour: f.colour, silhouette: BODY[f.body] ? f.body : 'hatch' };
      const pv = `<div class="gf-prev" style="--ui-tint:var(--dp-car-${f.colour})"><div class="gf-prev-art">${carArt(preview, 240)}</div><b>${ui.esc(preview.nickname)}</b><span>${ui.esc([f.year, f.make, f.model].filter(Boolean).join(' ') || 'Make and model are optional')}</span></div>`;
      const swatches = `<div class="gf-sw" role="radiogroup" aria-label="Colour">${COLOURS.map((c) => `<span class="gf-swi ui-press ${c === f.colour ? 'is-on' : ''}" role="radio" aria-checked="${c === f.colour}" tabindex="0" ${ui.act({ action: 'colour', arg: c, label: c.charAt(0).toUpperCase() + c.slice(1), haptic: 'selection', noRole: true })} style="--c:var(--dp-car-${c})">${c === f.colour ? ui.icon('check', 16) : ''}</span>`).join('')}</div>`;
      const power = ui.segmented([['petrol', 'Petrol'], ['diesel', 'Diesel'], ['hybrid', 'Hybrid'], ['ev', 'EV']].map(([id, label]) => ({ id, label })), { selected: f.power, action: 'power', cls: 'gf-seg' });
      const bodies = `<div class="gf-chips">${BODIES.map(([id, l]) => ui.chip(l, { kind: 'filter', selected: f.body === id, action: 'body', arg: id, haptic: 'selection' })).join('')}</div>`;
      const fields = `<div class="gf-fields">${ui.textField({ label: 'Nickname', value: f.name, placeholder: 'e.g. GTI, Daily, The Bean', helper: f.name ? `${f.name.length} of 24` : 'Required · up to 24 characters', focused: add })}
        <div class="gf-2">${ui.textField({ label: 'Make', value: f.make, placeholder: 'Optional' })}${ui.textField({ label: 'Model', value: f.model, placeholder: 'Optional' })}</div>
        ${ui.textField({ label: 'Year', value: f.year, placeholder: 'Optional' })}</div>`;
      const g = (t, html) => `<section class="gf-g"><h3>${ui.esc(t)}</h3>${html}</section>`;
      const bt = state === 'bluetoothLink'
        ? g('Bluetooth', `${ui.list([
          ui.row({ lead: radioLead(true), title: 'Car Multimedia', sub: 'Paired with this phone · connected now', trailing: `<span class="gf-bt">${ui.icon('check', 16)}</span>` }),
          ui.row({ lead: radioLead(false), title: 'Headphones', sub: 'Paired with this phone' }),
          ctx.android ? ui.row({ icon: 'record', title: 'Start a drive when it connects', sub: 'Begins a trip candidate; auto-detect confirms it', trailing: 'toggle', on: ctx.store.btStart ?? true, action: 'btStart' }) : '',
        ], { cls: 'gf-list' })}<p class="gf-foot">${ui.esc(ctx.ios ? 'When your iPhone connects to Car Multimedia, drives are assigned to the GTI. iPhone only shares car audio connections, so this assigns drives but can’t start them.' : 'When your phone connects to Car Multimedia, drives are assigned to the GTI. DrivePerf stores only the device name, on this phone.')}</p>`)
        : g('Bluetooth', ui.list([ui.row({ icon: 'gear', title: 'Link the car’s Bluetooth', sub: 'Assign drives to this car automatically', state: add ? null : 'bluetoothLink', toast: add ? 'Save the car first' : null })], { cls: 'gf-list' }));
      const del = !add ? ui.list([ui.row({ icon: 'trash', iconTone: 'critical', title: `Delete ${f.v.nickname}`, destructive: true, state: 'deleteConfirm', trailing: '' })], { cls: 'gf-list' }) : '';
      const header = editHeader(ctx, add ? 'Add a car' : 'Edit car', { sub: add ? null : f.v.nickname, okLabel: add ? 'Add' : 'Save' });
      return `${header}<div class="gf">${pv}${fields}${g('Colour', swatches)}${g('Powertrain', power)}${g('Body style', bodies)}
        ${g('', ui.list([ui.row({ icon: 'check', title: 'Default car', sub: 'New drives are assigned to it', trailing: 'toggle', on: f.isDefault, action: 'def' }), ui.row({ icon: 'story', title: 'Add a photo', sub: 'Optional · stays on this phone', toast: 'Photo picker (system)' })], { cls: 'gf-list' }))}
        <div data-focus="bt">${bt}</div>${del}<p class="gf-foot">No VIN or plate. Car details stay on this phone${ctx.isPro ? ' and in your Pro backup' : ''}.</p></div>`;
    },
    mount(el, ctx) {
      const f = el.querySelector('.gf-fields .ui-field-in');
      if (f) f.addEventListener('input', () => { ctx.store.name = f.textContent.slice(0, 24); const b = el.querySelector('.gf-prev b'); if (b) b.textContent = ctx.store.name || 'Your car'; });
      if (ctx.state === 'deleteConfirm') {
        const v = garForm(ctx).v, s = carStats(v.id);
        el.insertAdjacentHTML('beforeend', `<div class="gf-dlg"><div class="dp-scrim" data-set-state="edit"></div><div class="dp-dialog-wrap">${ctx.ui.dialog({ title: `Delete the ${v.nickname}?`, icon: 'trash', text: `Its ${plural(s.drives, 'drive')} stay in your history as “Unknown car”. Its per-car stats are removed. This can’t be undone.`, actions: [{ text: 'Cancel', kind: 'cancel', state: 'edit' }, { text: 'Delete car', kind: 'destructive', action: 'deleteCar' }] })}</div></div>`);
        if (!still(ctx, el)) { const d = el.querySelector('.gf-dlg .ui-dialog'); ctx.motion.animate(d, ctx.ios ? [{ opacity: 0, transform: 'scale(1.12)' }, { opacity: 1, transform: 'scale(1)' }] : [{ opacity: 0, transform: 'scale(.9)' }, { opacity: 1, transform: 'scale(1)' }], { preset: 'fast' }); }
        return () => { const d = el.querySelector('.gf-dlg'); if (d) d.remove(); };
      }
      if (ctx.state === 'bluetoothLink' && !ctx.store.didFocus) { ctx.store.didFocus = true; scrollToEl(el, el.querySelector('[data-focus="bt"]'), 140); }
      const prev = el.querySelector('.gf-prev-art .gar-art-b');
      if (prev && !ctx.store.drawn) { ctx.store.drawn = true; drawPaths(ctx, el, '.gf-prev-art .gar-art-b', { duration: 1100, delay: 200 }); }
    },
    actions: {
      colour: (ctx, c) => { ctx.store.colour = c; ctx.update(); return false; },
      power: (ctx, p) => { ctx.store.power = p; ctx.update(); return false; },
      body: (ctx, b) => { ctx.store.body = b; ctx.update(); return false; },
      def: (ctx) => { ctx.store.isDefault = !garForm(ctx).isDefault; ctx.update(); return false; },
      btStart: (ctx) => { ctx.store.btStart = !(ctx.store.btStart ?? true); ctx.update(); return false; },
      save: (ctx) => {
        const f = garForm(ctx);
        if (!f.name) { ctx.toast('Add a nickname first', { icon: 'info' }); ctx.haptic('warning'); return false; }
        ctx.toast(ctx.state === 'default' ? `${f.name} added to your garage` : `${f.name} saved`, { icon: 'check' });
        ctx.haptic('success');
        returnTo(ctx, {}); return false;
      },
      deleteCar: (ctx) => {
        const v = garForm(ctx).v;
        ctx.toast(`${v.nickname} deleted · drives kept as Unknown car`, { icon: 'trash' });
        DP.router.open('#S-GAR-01/multipleCars', { animate: true });
        return false;
      },
    },
    spec: {
      purpose: 'Add a car or edit one: nickname (required), optional make, model and year, powertrain, body style and colour — the colour and silhouette become the car’s identity across Stories and cards (F-GAR-01, F-ONB-09). Also delete a car (trips kept as “Unknown car”) and, P2, link its Bluetooth (F-GAR-04).',
      layout: [
        'Large sheet with its own toolbar: × (Cancel) · “Add a car” / “Edit car” + sub · ✓ (iOS glass prominent) / “Add”/“Save” text button (Android).',
        'Live preview card (radius 24, the chosen car colour wash): silhouette 240 for the chosen body style in the chosen colour · nickname (“Your car” until typed) · “2022 Volkswagen Golf GTI” / “Make and model are optional”.',
        'Fields: Nickname (focused on Add; helper “Required · up to 24 characters” → “3 of 24”) · Make + Model side by side (stack at XL) · Year. iOS rounded fields (surface-2, radius 14) / Android OutlinedTextField.',
        'Colour: 12 car-colour swatches (36, ring + check when selected). Powertrain: Petrol · Diesel · Hybrid · EV segmented. Body style chips: Hatch · Sedan · Crossover · SUV · Coupe · Wagon · Pickup.',
        'List: Default car (switch) · Add a photo (optional, stays on this phone). Bluetooth row “Link the car’s Bluetooth” (P2). Edit only: destructive “Delete GTI”. Footer “No VIN or plate. Car details stay on this phone and in your Pro backup.”',
        'Delete confirm: alert / M3 dialog over the sheet — “Delete the GTI?” “Its 25 drives stay in your history as “Unknown car”. Its per-car stats are removed. This can’t be undone.” Cancel · Delete car.',
        'Bluetooth link (P2): paired devices as radio rows (Car Multimedia · connected now ✓, Headphones); Android adds “Start a drive when it connects” (switch); platform footnote (iOS assigns only, from car audio connections).',
      ],
      components: ['sheetHeader', 'iconButton', 'button', 'textField', 'segmented', 'chip', 'list', 'row', 'toggle', 'dialog'],
      interactions: ['Swatch / powertrain / body style → updates the preview live (selection haptic). Nickname typing updates the preview name.', '✓ / Save: empty nickname → toast “Add a nickname first” (warning haptic); otherwise toast “GTI saved” / “Daily added to your garage” (success haptic) and dismiss to S-GAR-01/02.', 'Delete → dialog; Delete car → toast “GTI deleted · drives kept as Unknown car” → S-GAR-01. Cancel / scrim → back to the edit form.', 'Bluetooth row → bluetoothLink state (edit only). Photo → system photo picker (mock toast).'],
      motion: ['Sheet spring.sheet (iOS large detent recedes the parent). Hero motion: the preview silhouette draws on when the sheet opens (1100 ms), and its colour wash cross-fades on each swatch change (spring.default via CSS transition). RM: instant colour change.', 'Delete dialog scales in (spring.fast).'],
      a11y: ['Swatches are a radio group with colour names (“Carmine, selected”); the chosen colour also shows a check.', 'Nickname field announces “Nickname, required, up to 24 characters”.', 'Dialog: focus to title, Cancel first. Delete car is destructive.', 'XL: Make/Model stack; swatches wrap to 3 rows; segmented labels stay legible (two lines allowed).'],
      platformDiffs: { ios: 'Sheet (large) with NavigationStack toolbar (✕ / ✓ glassProminent); Form-style sections; ColorPicker-like swatch grid; .alert for delete.', android: 'ModalBottomSheet full height with top row (close, title, TextButton Save); OutlinedTextFields; connected ButtonGroup; FilterChips; AlertDialog for delete; BLUETOOTH_CONNECT permission requested on first link.' },
      analytics: ['car_added {source: garage|onboarding|trip_edit, powertrain}', 'car_edited', 'car_deleted {trips_bucket}', 'bt_car_linked'],
      copy: { Title: 'Add a car / Edit car', 'Nickname helper': 'Required · up to 24 characters', Placeholder: 'e.g. GTI, Daily, The Bean', 'Empty nickname': 'Add a nickname first', Footer: 'No VIN or plate. Car details stay on this phone (and in your Pro backup).', 'Delete title': 'Delete the {car}?', 'Delete text': 'Its {n} drives stay in your history as “Unknown car”. Its per-car stats are removed. This can’t be undone.', 'BT iOS': 'When your iPhone connects to {device}, drives are assigned to the {car}. iPhone only shares car audio connections, so this assigns drives but can’t start them.', 'BT Android': 'When your phone connects to {device}, drives are assigned to the {car}. DrivePerf stores only the device name, on this phone.' },
      freePro: 'Editing is free. Adding a second car on Free is gated before this sheet (S-SUB-03 s2ndCar from S-GAR-01); S-ONB-14 reuses this form for the first car.',
      data: 'Add: empty form, colour cobalt, petrol, hatch (defaults). Edit / delete / Bluetooth: vehicles gti (Kings Red → carmine, hatch, petrol, manual). Delete count from carStats (25 drives). Bluetooth device names are prototype examples.',
      icons: ['close', 'check', 'trash', 'gear', 'story', 'record'],
      assets: [],
    },
  });
})();
