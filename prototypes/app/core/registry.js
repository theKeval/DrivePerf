/* DrivePerf prototype engine · registry (DRIVE-32). Screen registration + manifest lookup + data helpers.
   Contract: prototypes/app/AUTHORING.md. Classic script (works from file://). Load after manifest.js. */
(function () {
  const M = window.DP_MANIFEST || { screens: [], stories: {}, wps: {} };
  const META = Object.fromEntries(M.screens.map((s) => [s.id, s]));
  const DEFS = {};
  const PRESENTATIONS = ['root', 'push', 'sheet', 'full', 'dialog', 'sys', 'live'];
  const problems = [];

  function normStates(list) {
    return (list || []).map((s) => (typeof s === 'string' ? { id: s, label: s } : { id: s.id, label: s.label || s.id }));
  }

  /** Register a screen. See AUTHORING.md §2 for every field. */
  function screen(id, def) {
    const meta = META[id];
    if (!meta) problems.push(`DP.screen('${id}'): not in the manifest (PRD §4). Check the ID.`);
    if (DEFS[id]) problems.push(`DP.screen('${id}'): registered twice; the later definition wins.`);
    if (typeof def.render !== 'function') problems.push(`DP.screen('${id}'): render(ctx) is required.`);
    const pres = def.presentation || meta?.pres || 'push';
    for (const p of typeof pres === 'object' ? Object.values(pres) : [pres]) if (!PRESENTATIONS.includes(p)) problems.push(`DP.screen('${id}'): unknown presentation '${p}'.`);
    const states = normStates(def.states && def.states.length ? def.states : meta?.states);
    if (!states.some((s) => s.id === 'default')) states.unshift({ id: 'default', label: 'Def' });
    DEFS[id] = {
      id,
      name: def.name || meta?.name || id,
      wp: def.wp || meta?.wp,
      platforms: def.platforms || meta?.platforms || ['ios', 'android'],
      presentation: pres,
      tab: def.tab !== undefined ? def.tab : meta?.tab || null,
      over: def.over || null,
      detent: def.detent || 'auto',
      chrome: def.chrome,
      statusBar: def.statusBar,
      states,
      stateParams: def.stateParams || {},
      stateAliases: def.stateAliases || {},
      render: def.render,
      mount: def.mount,
      actions: def.actions || {},
      spec: def.spec || {},
      variants: def.variants || meta?.variants || [],
      backTo: def.backTo || null,
      dismissible: def.dismissible !== false,
      raw: def,
    };
    return DEFS[id];
  }

  const presOf = (id, platform) => {
    const d = DEFS[id], m = META[id];
    const p = d ? d.presentation : m?.pres || 'push';
    return typeof p === 'object' ? p[platform] || p.ios || 'push' : p;
  };
  const statesOf = (id) => (DEFS[id] ? DEFS[id].states : normStates(META[id]?.states || [{ id: 'default' }]));
  const resolveState = (id, state) => {
    const st = statesOf(id), d = DEFS[id];
    if (!state || state === 'default') return 'default';
    if (st.some((s) => s.id === state)) return state;
    if (d?.stateAliases[state]) return d.stateAliases[state];
    const ci = st.find((s) => s.id.toLowerCase() === state.toLowerCase());
    return ci ? ci.id : 'default';
  };

  // ---------------- data helpers (ctx.data) ----------------
  const D = window.DP_DATA, U = D && D.util;
  const TODAY = D ? new Date(D.meta.today) : new Date();
  const tz = { timeZone: 'America/Toronto' };
  const data = D ? {
    D, util: U, today: TODAY,
    trips: (filter) => (filter ? D.trips.filter(filter) : D.trips.slice()),
    trip: (id) => U.trip(id),
    newestTrip: (filter = () => true) => D.trips.find(filter),
    newestScored: () => D.trips.find((t) => t.smoothness),
    route: (id) => U.route(id),
    routes: () => D.routes.slice(),
    vehicle: (id) => U.vehicle(id),
    cars: () => D.vehicles.slice(),
    recap: (id) => (id ? D.recaps.find((r) => r.id === id) : D.recaps[0]),
    latestCompleteRecap: () => D.recaps.find((r) => r.complete),
    entitlement: (ent) => ({ key: ent, ...(D.entitlement.states[ent] || D.entitlement.states[D.entitlement.current]) }),
    /** Health items for a platform with a preset applied: [{id,label,fix,status:'green'|'amber'|'red',detail}] */
    health(platform, preset = 'allGood') {
      const p = D.health.presets[preset] || D.health.presets.allGood;
      return { preset, summary: p.summary || D.health.presets.allGood.summary, promise: D.health.presets.allGood.promise, lastCheckIn: p.lastCheckIn || D.health.presets.allGood.lastCheckIn, lastTrip: p.lastTrip || D.health.presets.allGood.lastTrip,
        items: D.health[platform].items.map((it) => ({ ...it, status: (p.status || {})[it.id] || 'green', detail: (p.detail || {})[it.id] || null })) };
    },
    live: (state = 'manual') => ({ key: state, ...(D.liveTrip.states[state] || D.liveTrip.states.manual) }),
    isLocked: (trip, ent) => U.isLocked(trip, ent),
    // formatting (copy rules: brand.md §6)
    km: (n) => (n == null ? '—' : (Math.round(n * 10) / 10).toFixed(n >= 100 ? 0 : 1)),
    min: (s) => U.fmtDuration(s),
    clock: (s) => U.fmtClock(s),
    date: (iso, opts) => U.fmtDate(iso, opts),
    signed: (n) => U.signed(n),
    time: (iso) => new Date(iso).toLocaleTimeString('en-CA', { ...tz, hour: 'numeric', minute: '2-digit' }).replace('a.m.', 'am').replace('p.m.', 'pm'),
    weekday: (iso) => new Date(iso).toLocaleDateString('en-GB', { ...tz, weekday: 'long' }),
    ago(iso) { const m = Math.round((TODAY - new Date(iso)) / 60000); return m < 1 ? 'just now' : m < 60 ? `${m} min ago` : m < 1440 ? `${Math.round(m / 60)} h ago` : `${Math.round(m / 1440)} d ago`; },
    /** Week summary computed from the trips (driver, scored) in [weekStart, asOf]. Derived: note it in the spec. */
    weekSummary(asOf = TODAY, weekStartIso) {
      const end = new Date(asOf);
      const ws = weekStartIso ? new Date(weekStartIso + 'T00:00:00-05:00') : (() => { const d = new Date(end); const day = (d.getDay() + 6) % 7; d.setHours(0, 0, 0, 0); d.setDate(d.getDate() - day); return d; })();
      const list = D.trips.filter((t) => t.type === 'driver' && new Date(t.start) >= ws && new Date(t.start) <= end);
      const scored = list.filter((t) => t.smoothness);
      const avg = scored.length ? Math.round(scored.reduce((a, t) => a + t.smoothness.score, 0) / scored.length) : null;
      return { drives: list.length, km: list.reduce((a, t) => a + t.distanceKm, 0), durationS: list.reduce((a, t) => a + t.durationS, 0), avg, trips: list, weekStart: ws };
    },
  } : {};

  window.DP = Object.assign(window.DP || {}, {
    manifest: M,
    meta: (id) => META[id] || null,
    def: (id) => DEFS[id] || null,
    screens: DEFS,
    screen,
    presOf,
    statesOf,
    resolveState,
    problems,
    data,
    PRESENTATIONS,
    /** Built (registered) vs placeholder coverage for a platform. */
    coverage(platform) {
      const all = M.screens.filter((s) => s.platforms.includes(platform));
      const built = all.filter((s) => DEFS[s.id] && (DEFS[s.id].platforms || []).includes(platform));
      return { total: all.length, built: built.length, placeholders: all.filter((s) => !built.includes(s)).map((s) => s.id) };
    },
  });
})();
