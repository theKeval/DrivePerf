/* ---- DP_DATA.util: small read helpers shared by both prototypes (appended by gen-fixtures.py) ---- */
(function (D) {
  const byId = (arr) => Object.fromEntries(arr.map((x) => [x.id, x]));
  const trips = byId(D.trips), routes = byId(D.routes), vehicles = byId(D.vehicles);
  const DAY = 86400000;
  const today = new Date(D.meta.today);
  D.util = {
    trip: (id) => trips[id],
    route: (id) => routes[id],
    vehicle: (id) => vehicles[id],
    /** Track rows as objects: {t, lat, lng, kmh, lonG, latG, elevM, q:'observed'|'estimated'|'gap'|'excluded'} */
    rows(trip) {
      const f = trip.track.fields, Q = trip.track.q;
      return trip.track.rows.map((r) => { const o = {}; f.forEach((k, i) => (o[k] = r[i])); o.q = Q[o.q]; return o; });
    },
    /** Interpolated sample at time t (seconds from start). Display-only interpolation (F-RPL-01). */
    sampleAt(trip, t) {
      const R = trip.track.rows;
      let lo = 0, hi = R.length - 1;
      if (t <= R[0][0]) return D.util._row(R[0]);
      if (t >= R[hi][0]) return D.util._row(R[hi]);
      while (hi - lo > 1) { const m = (lo + hi) >> 1; if (R[m][0] <= t) lo = m; else hi = m; }
      const a = R[lo], b = R[hi], f = (t - a[0]) / (b[0] - a[0] || 1);
      const L = (i) => (a[i] == null || b[i] == null ? null : a[i] + (b[i] - a[i]) * f);
      return { t, lat: L(1), lng: L(2), kmh: L(3), lonG: L(4), latG: L(5), elevM: L(6), q: trip.track.q[f < 0.5 ? a[7] : b[7]] };
    },
    _row: (r) => ({ t: r[0], lat: r[1], lng: r[2], kmh: r[3], lonG: r[4], latG: r[5], elevM: r[6], q: ['observed', 'estimated', 'gap', 'excluded'][r[7]] }),
    /** Is this trip's Replay/Analysis locked for the given entitlement state key? (Free: older than 30 days) */
    isLocked(trip, ent = D.entitlement.current) {
      const s = D.entitlement.states[ent];
      if (s.isPro) return false;
      return today - new Date(trip.start) > D.entitlement.freeLimits.analysisDays * DAY;
    },
    isRouteLocked(route, ent = D.entitlement.current) {
      const s = D.entitlement.states[ent];
      return !s.isPro && !(s.activeRouteIds || []).includes(route.id);
    },
    /** "+3", "−2", "±0" with a real minus sign */
    signed: (n) => (n == null ? '' : n > 0 ? `+${n}` : n < 0 ? `−${Math.abs(n)}` : '±0'),
    fmtDuration(s) { const h = Math.floor(s / 3600), m = Math.round((s % 3600) / 60); return h ? `${h} h ${m} min` : `${m} min`; },
    fmtClock(s) { s = Math.max(0, Math.round(s)); const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60; return (h ? `${h}:${String(m).padStart(2, '0')}` : `${m}`) + `:${String(x).padStart(2, '0')}`; },
    /** "5 Nov" (day-month, as in all DrivePerf copy) */
    fmtDate: (iso, opts = { day: 'numeric', month: 'short' }) => new Date(iso.length === 10 ? iso + 'T12:00:00' : iso).toLocaleDateString('en-GB', { timeZone: 'America/Toronto', ...opts }),
    weekOf: (iso) => D.recaps.find((r) => iso.slice(0, 10) >= r.weekStart && iso.slice(0, 10) <= r.weekEnd),
    /** SVG path for a glyph (normalised 0..1 points) inside a w×h box with padding p */
    glyphPath(pts, w = 100, h = 100, p = 8) {
      const s = Math.min(w, h) - 2 * p, ox = (w - s) / 2, oy = (h - s) / 2;
      return pts.map((q, i) => `${i ? 'L' : 'M'}${(ox + q[0] * s).toFixed(1)} ${(oy + q[1] * s).toFixed(1)}`).join(' ');
    },
    /** Project the track to an SVG path in a w×h box (plain-canvas map / offline state). Gaps break the path. */
    trackPath(trip, w, h, p = 16) {
      const [la0, ln0, la1, ln1] = trip.bbox, k = Math.cos(((la0 + la1) / 2) * Math.PI / 180);
      const sx = (ln1 - ln0) * k, sy = la1 - la0, s = Math.min((w - 2 * p) / sx, (h - 2 * p) / sy);
      const ox = (w - sx * s) / 2, oy = (h - sy * s) / 2;
      const X = (ln) => ox + (ln - ln0) * k * s, Y = (la) => h - oy - (la - la0) * s;
      let d = '', pen = false;
      for (const r of trip.track.rows) {
        if (r[1] == null) { pen = false; continue; }
        d += `${pen ? 'L' : 'M'}${X(r[2]).toFixed(1)} ${Y(r[1]).toFixed(1)} `; pen = true;
      }
      return { d: d.trim(), project: (lat, lng) => [X(lng), Y(lat)] };
    },
  };
})(window.DP_DATA);
