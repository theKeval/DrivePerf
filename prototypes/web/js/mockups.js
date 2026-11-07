/* driveperf.com prototype — data-driven visuals and stylised device mockups (DRIVE-132 · A9).
   Everything is drawn from window.DP_DATA (prototypes/shared/data.js). Nothing here is a screenshot.
   Astro port: these become <ScoreRing>, <RouteLine>, <GGCircle>, <DevicePhone>, <StoryFrame> components;
   route paths should be precomputed at build time (only the path strings ship, not data.js). */
(function () {
  'use strict';
  const D = window.DP_DATA, U = D.util;
  const NS = 'http://www.w3.org/2000/svg';
  const reduced = () => window.DPW.reduced();
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  /* ---------- Geometry ---------- */
  const R_EARTH = 6371000;
  function hav(a, b) {
    const t = Math.PI / 180, dLat = (b[0] - a[0]) * t, dLng = (b[1] - a[1]) * t;
    const s = Math.sin(dLat / 2) ** 2 + Math.cos(a[0] * t) * Math.cos(b[0] * t) * Math.sin(dLng / 2) ** 2;
    return 2 * R_EARTH * Math.asin(Math.sqrt(s));
  }
  /** Observed track points with cumulative distance; ends trimmed (privacy: never show where a drive starts or ends). */
  function trackPoints(trip, { trimStart = 650, trimEnd = 520 } = {}) {
    const pts = []; let cum = 0, prev = null;
    for (const r of trip.track.rows) {
      if (r[1] == null) continue;
      const p = [r[1], r[2]];
      if (prev) cum += hav(prev, p);
      pts.push({ lat: p[0], lng: p[1], t: r[0], d: cum }); prev = p;
    }
    const total = cum;
    return { pts: pts.filter((p) => p.d >= trimStart && p.d <= total - trimEnd), total };
  }
  /** Build a projector for a w×h box (equirectangular, aspect preserved, optional rotation). Returns fn(lat,lng) → [x,y]. */
  function projector(pts, w, h, { pad = 16, rotate = 0, fit = 'contain', box = null } = {}) {
    const lat0 = pts.reduce((s, p) => s + p.lat, 0) / pts.length, k = Math.cos(lat0 * Math.PI / 180);
    const c = Math.cos(rotate * Math.PI / 180), s = Math.sin(rotate * Math.PI / 180);
    const raw = (lat, lng) => { const x = lng * k, y = -lat; return [x * c - y * s, x * s + y * c]; };
    const xy = pts.map((p) => raw(p.lat, p.lng));
    const xs = xy.map((p) => p[0]), ys = xy.map((p) => p[1]);
    const x0 = Math.min(...xs), x1 = Math.max(...xs), y0 = Math.min(...ys), y1 = Math.max(...ys);
    const B = box || { x: 0, y: 0, w, h };
    const sc = (fit === 'cover' ? Math.max : Math.min)((B.w - 2 * pad) / (x1 - x0), (B.h - 2 * pad) / (y1 - y0));
    const ox = B.x + (B.w - (x1 - x0) * sc) / 2, oy = B.y + (B.h - (y1 - y0) * sc) / 2;
    return (lat, lng) => { const [x, y] = raw(lat, lng); return [ox + (x - x0) * sc, oy + (y - y0) * sc]; };
  }
  function project(pts, w, h, opts = {}) { const P = projector(pts, w, h, opts); return pts.map((p) => P(p.lat, p.lng)); }

  /** Catmull-Rom → cubic Bézier, so a 5–12 s sample track reads as a smooth road line at hero size. */
  function smoothPath(xy, tension = 0.5) {
    if (xy.length < 2) return '';
    let d = `M${xy[0][0].toFixed(1)} ${xy[0][1].toFixed(1)}`;
    for (let i = 0; i < xy.length - 1; i++) {
      const p0 = xy[i - 1] || xy[i], p1 = xy[i], p2 = xy[i + 1], p3 = xy[i + 2] || p2;
      const c1 = [p1[0] + (p2[0] - p0[0]) * tension / 3, p1[1] + (p2[1] - p0[1]) * tension / 3];
      const c2 = [p2[0] - (p3[0] - p1[0]) * tension / 3, p2[1] - (p3[1] - p1[1]) * tension / 3];
      d += ` C${c1[0].toFixed(1)} ${c1[1].toFixed(1)} ${c2[0].toFixed(1)} ${c2[1].toFixed(1)} ${p2[0].toFixed(1)} ${p2[1].toFixed(1)}`;
    }
    return d;
  }
  /** Route as a smooth path. Returns {d, xy, pts, P, segment(t0,t1), at(t)}. Pass opts.P to reuse another route's projection. */
  function route(trip, w, h, opts = {}) {
    const { pts } = trackPoints(trip, opts);
    const P = opts.P || projector(pts, w, h, opts);
    const xy = pts.map((p) => P(p.lat, p.lng));
    const segment = (t0, t1) => smoothPath(xy.filter((_, i) => pts[i].t >= t0 && pts[i].t <= t1));
    const at = (t) => { let i = pts.findIndex((p) => p.t >= t); if (i < 0) i = pts.length - 1; return xy[i]; };
    return { d: smoothPath(xy), xy, pts, P, segment, at };
  }

  /* ---------- Score ring (components.md §3) ---------- */
  let ringN = 0;
  function ring({ score, baseline = null, recordPrev = null, isNewRecord = false, label = 'Smoothness', strokeW = 8 }) {
    const id = 'rg' + (ringN++), r = 42, C = 2 * Math.PI * r;
    const notch = (v, cls, extra = '') => {
      const a = (v / 100) * 2 * Math.PI, x1 = 50 + (r - 7.5) * Math.cos(a), y1 = 50 + (r - 7.5) * Math.sin(a), x2 = 50 + (r + 7.5) * Math.cos(a), y2 = 50 + (r + 7.5) * Math.sin(a);
      return `<line class="${cls}" x1="${x1.toFixed(2)}" y1="${y1.toFixed(2)}" x2="${x2.toFixed(2)}" y2="${y2.toFixed(2)}" ${extra}/>`;
    };
    const recNotch = recordPrev != null
      ? `<g class="ring-record">${notch(recordPrev, 'notch-flip', `stroke="var(--record)" stroke-width="3" stroke-linecap="round" stroke-dasharray="0" data-outline="1"`)}${notch(recordPrev, 'notch-inner', 'stroke="var(--bg, #05070A)" stroke-width="1.2" stroke-linecap="round"')}</g>` : '';
    return `<div class="ring" data-component="ScoreRing" data-score="${score}" data-record-prev="${recordPrev ?? ''}" data-new-record="${isNewRecord}" role="img" aria-label="${label} ${score}${baseline != null ? `, ${score - baseline > 0 ? score - baseline + ' above' : 'right on'} your usual` : ''}${isNewRecord ? '. New route record' : ''}.">
      <svg viewBox="0 0 100 100" aria-hidden="true">
        <defs><linearGradient id="${id}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="var(--ring-gradient-c)"/><stop offset=".45" stop-color="var(--ring-gradient-b)"/><stop offset="1" stop-color="var(--ring-gradient-a)"/></linearGradient></defs>
        <circle cx="50" cy="50" r="${r}" fill="none" stroke="var(--fill)" stroke-width="${strokeW + 1.5}"/>
        <circle class="ring-arc" cx="50" cy="50" r="${r}" fill="none" stroke="url(#${id})" stroke-width="${strokeW}" stroke-linecap="round" stroke-dasharray="${C.toFixed(2)}" stroke-dashoffset="${(C * (1 - score / 100)).toFixed(2)}" data-c="${C.toFixed(2)}"/>
        ${baseline != null ? notch(baseline, 'ring-baseline', 'stroke="var(--text-2)" stroke-width="1.6" stroke-linecap="round"') : ''}
        ${recNotch}
      </svg>
      <div class="ring__center"><span class="ring__value num"><span class="ring-num">${score}</span></span><span class="ring__label">${label}</span></div>
    </div>`;
  }
  /** Ring reveal (motion.md §3.4): arc sweeps, number locked to the arc, record notch flips gold as it passes. */
  function animateRing(el, { delay = 0, onDone, spring = false } = {}) {
    const arc = el.querySelector('.ring-arc'), num = el.querySelector('.ring-num'), rec = el.querySelector('.ring-record .notch-flip'), inner = el.querySelector('.ring-record .notch-inner');
    const score = +el.dataset.score, prev = el.dataset.recordPrev ? +el.dataset.recordPrev : null, isNew = el.dataset.newRecord === 'true';
    const C = +arc.dataset.c;
    const finish = () => { if (inner && isNew) inner.setAttribute('opacity', '0'); onDone && onDone(); };
    if (reduced()) { arc.style.strokeDashoffset = C * (1 - score / 100); num.textContent = score; finish(); return; }
    arc.style.strokeDashoffset = C; num.textContent = '0';
    let flipped = false;
    const flip = (v) => {
      if (rec && isNew && !flipped && v >= prev) {
        flipped = true; if (inner) inner.setAttribute('opacity', '0');
        rec.style.transformBox = 'fill-box'; rec.style.transformOrigin = 'center';
        rec.animate([{ transform: 'scale(1)' }, { transform: 'scale(1.5)' }, { transform: 'scale(1)' }], { duration: 520, easing: 'cubic-bezier(0.32, 0.72, 0, 1)' });
      }
    };
    const land = () => {
      num.animate([{ transform: 'scale(1)' }, { transform: 'scale(1.06)' }, { transform: 'scale(1)' }], { duration: 360, easing: 'cubic-bezier(0.32, 0.72, 0, 1)' });
      if (isNew) arc.animate([{ opacity: 1 }, { opacity: 0.72 }, { opacity: 1 }], { duration: 900, easing: 'ease-in-out' });
      finish();
    };
    if (spring) { // Spring count-up (motion.md §2.1, ios.slow-like: settles in ~1.6 s with a 1–2 % overshoot on the arc only)
      setTimeout(() => window.DP_MOTION.spring({ from: 0, to: score, zeta: 0.78, k: 16, precision: 0.05, el, onUpdate: (v) => {
        const c = Math.max(0, v); arc.style.strokeDashoffset = C * (1 - Math.min(c, 100) / 100);
        num.textContent = Math.round(Math.min(c, score)); flip(c);
      }, onDone: land }), delay);
      return;
    }
    setTimeout(() => {
      window.DP_MOTION.tween({ from: 0, to: score, duration: 1400, ease: 'countUp', onUpdate: (v) => {
        arc.style.strokeDashoffset = C * (1 - v / 100); num.textContent = Math.round(v);
        if (rec && isNew && !flipped && v >= prev) {
          flipped = true; if (inner) inner.setAttribute('opacity', '0');
          rec.style.transformBox = 'fill-box'; rec.style.transformOrigin = 'center';
          rec.animate([{ transform: 'scale(1)' }, { transform: 'scale(1.5)' }, { transform: 'scale(1)' }], { duration: 520, easing: 'cubic-bezier(0.32, 0.72, 0, 1)' });
        }
      }, onDone: () => {
        num.animate([{ fontWeight: 600 }, { fontWeight: 760 }, { fontWeight: 600 }], { duration: 240 });
        arc.animate([{ filter: 'drop-shadow(0 0 0 transparent)' }, { filter: 'drop-shadow(0 0 3px var(--record))' }, { filter: 'drop-shadow(0 0 0 transparent)' }], { duration: 900 });
        finish();
      } });
    }, delay);
  }

  /* ---------- G-G circle (components.md §6), personal visual: no numbers on marketing surfaces ---------- */
  function gg(trip, { size = 200, dot = true } = {}) {
    const c = size / 2, S = (size / 2 - 8) / 0.6; // fixed ±0.6 g
    const pts = trip.gg.points;
    const rings = [0.1, 0.2, 0.3, 0.4, 0.5].map((g) => `<circle cx="${c}" cy="${c}" r="${(g * S).toFixed(1)}" fill="none" stroke="var(--gg-ring, #FFFFFF1F)" stroke-width="${g === 0.3 || g === 0.5 ? 1 : 0.6}"/>`).join('');
    const cloud = pts.map(([lat, lon]) => `<circle cx="${(c + lat * S).toFixed(1)}" cy="${(c - lon * S).toFixed(1)}" r="1.5"/>`).join('');
    const env = trip.gg.envelope.radiiG.map((g, i) => { const a = (i * 15) * Math.PI / 180; return [c + Math.cos(a) * g * S, c - Math.sin(a) * g * S]; });
    const envD = smoothPath([...env, env[0], env[1]], 0.6) ;
    return `<svg class="gg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" role="img" aria-label="Friction circle: a cloud of every brake and turn on this drive. Most inputs stayed close to the centre.">
      ${rings}<path d="M${c} 6V${size - 6}M6 ${c}H${size - 6}" stroke="var(--gg-ring, #FFFFFF1F)" stroke-width=".8"/>
      <g fill="var(--gg-cloud, #5BE7C4)" opacity=".32">${cloud}</g>
      <path d="${envD}" fill="none" stroke="var(--gg-cloud, #5BE7C4)" stroke-opacity=".75" stroke-width="1.5"/>
      ${dot ? `<path class="gg-trail" fill="none" stroke="#fff" stroke-opacity=".5" stroke-width="2" stroke-linecap="round"/><circle class="gg-halo" cx="${c}" cy="${c}" r="9" fill="#fff" opacity=".18"/><circle class="gg-dot" cx="${c}" cy="${c}" r="4.5" fill="#fff"/>` : ''}
    </svg>`;
  }
  /** G-dot replay: follows the cloud samples in order, compressed into ~3 s, spring follower (motion.md §3.5). */
  function animateGG(svg, trip, { loop = true } = {}) {
    const size = +svg.getAttribute('width'), c = size / 2, S = (size / 2 - 8) / 0.6;
    const dot = svg.querySelector('.gg-dot'), halo = svg.querySelector('.gg-halo'), trail = svg.querySelector('.gg-trail');
    if (!dot) return () => {};
    const pts = trip.gg.points;
    if (reduced()) { const [la, lo] = pts[Math.floor(pts.length * 0.62)]; dot.setAttribute('cx', c + la * S); dot.setAttribute('cy', c - lo * S); halo.setAttribute('cx', c + la * S); halo.setAttribute('cy', c - lo * S); return () => {}; }
    const f = window.DP_MOTION.follower({ zeta: 0.72, k: 260 });
    let raf, t0 = null, last = null; const hist = []; const dur = 3600;
    const tick = (now) => {
      t0 ??= now; const dt = last ? (now - last) / 1000 : 0.016; last = now;
      let p = (now - t0) / dur; if (p > 1) { if (!loop) return; t0 = now; p = 0; }
      const i = Math.min(pts.length - 1, Math.floor(p * pts.length));
      f.target(pts[i][0], pts[i][1]); const s = f.step(dt);
      const x = c + s.x * S, y = c - s.y * S;
      dot.setAttribute('cx', x.toFixed(1)); dot.setAttribute('cy', y.toFixed(1)); halo.setAttribute('cx', x.toFixed(1)); halo.setAttribute('cy', y.toFixed(1));
      hist.push([x, y]); if (hist.length > 28) hist.shift();
      trail.setAttribute('d', hist.map((q, k) => `${k ? 'L' : 'M'}${q[0].toFixed(1)} ${q[1].toFixed(1)}`).join(''));
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }

  /* ---------- Devices ---------- */
  const status = (platform) => platform === 'android'
    ? `<div class="device__status"><span>7:52</span><i><svg width="14" height="10" viewBox="0 0 14 10" aria-hidden="true"><path d="M7 9.5L.5 3a9.5 9.5 0 0 1 13 0z" fill="#F2F4F7"/></svg><b></b></i></div>`
    : `<div class="device__status"><span>7:52</span><i><svg width="16" height="10" viewBox="0 0 16 10" aria-hidden="true"><rect x="0" y="6" width="3" height="4" rx="1" fill="#F2F4F7"/><rect x="4.3" y="4" width="3" height="6" rx="1" fill="#F2F4F7"/><rect x="8.6" y="2" width="3" height="8" rx="1" fill="#F2F4F7"/><rect x="12.9" y="0" width="3" height="10" rx="1" fill="#F2F4F7"/></svg><b></b></i></div>`;
  function device(platform, inner, { width = 300, label = '', cls = '', style = '' } = {}) {
    return `<div class="device device--${platform} ${cls}" style="--w:${width}px;${style}" data-component="DevicePhone" data-platform="${platform}">
      <div class="device__screen">${status(platform)}<div class="device__island"></div>${inner}<div class="device__home"></div></div>
      ${label ? `<span class="device__label">${label}</span>` : ''}
    </div>`;
  }

  /* ---------- App screens used in the mockups ---------- */
  const HERO_TRIP = U.trip('t20261105-0742');      // Thursday morning, The Commute: new route record 91 (beats 89 from 14 Oct)
  const RECAP = D.recaps.find((r) => r.complete);  // week of 26 Oct: 5 drives, average 87, up 2
  const NEEDED_TRIP = U.trip('t20261020-0740');    // needed brake at Spadina & King

  const progress = (n, of = 7) => `<div class="story-progress" aria-hidden="true">${Array.from({ length: of }, (_, i) => (i < n - 1 ? '<i class="on"></i>' : i === n - 1 ? '<i class="cur"><b></b></i>' : '<i></i>')).join('')}</div>`;
  const storyTop = (trip) => { const v = U.vehicle(trip.vehicleId); return `<div class="story-top"><span class="story-car"><i style="background:var(--car-${v.colour}, ${v.colour === 'lucid' ? '#7FA7D9' : '#E5484D'})"></i>${esc(v.nickname)}</span><span class="story-close" aria-hidden="true">✕</span></div>`; };

  function screenStoryRing(trip = HERO_TRIP) {
    const f = trip.story.frames, sm = trip.smoothness;
    return `<div class="app app--story">${progress(4)}${storyTop(trip)}
      <div class="story-title">${esc(f.f1.title)}</div>
      <div class="story-stage"><div style="width:78%;aspect-ratio:1;font-size:calc(var(--w) * .2)">${ring({ score: sm.score, baseline: sm.baseline, recordPrev: sm.record?.previous?.score, isNewRecord: sm.record?.isNew })}</div></div>
      <div class="story-copy"><h4>New route record.</h4><p>91 on The Commute, beating 89 from 14&nbsp;Oct.</p>
      <span class="story-chip story-chip--record"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="9.5" r="5.5"/><path d="M8.6 13.8L7 21l5-2.6 5 2.6-1.6-7.2"/></svg>Route record</span></div>
    </div>`;
  }
  function screenRecap(rc = RECAP) {
    const days = rc.days.map((d) => `<div>${d.drives.length ? d.drives.map(() => '<span></span>').join('') : '<span class="none"></span>'}${d.dow.slice(0, 1)}</div>`).join('');
    const tr = rc.trend4w.map((t) => t.avg), mn = 76, mx = 90;
    const pts = tr.map((v, i) => [8 + i * (184 / (tr.length - 1)), 52 - (v - mn) / (mx - mn) * 44]);
    return `<div class="app app--recap">
      <div class="m3-top"><span class="m3-title">Insights</span><span class="m3-avatar">M</span></div>
      <div class="m3-card m3-card--hi"><div class="m3-label">Your week, decoded</div>
        <div style="display:flex;align-items:baseline;gap:.4em;margin-top:.3em"><span class="app-num" style="font-size:2.6em;line-height:1">${rc.avgSmoothness}</span><span style="font-weight:600">Smoothness · ▲ +2</span></div>
        <div style="font-size:.85em;margin-top:.2em;opacity:.85">${rc.drives} drives · ${rc.km.toFixed(0)} km · ${rc.durationText}</div>
      </div>
      <div class="m3-card"><div class="m3-label">This week</div><div class="week-strip">${days}</div></div>
      <div class="m3-card"><div class="m3-label">4-week trend</div>
        <svg viewBox="0 0 200 60" style="width:100%;height:auto;margin-top:.4em" aria-hidden="true"><path d="${smoothPath(pts)}" fill="none" stroke="#5BE7C4" stroke-width="2.5" stroke-linecap="round"/>${pts.map((p, i) => `<circle cx="${p[0]}" cy="${p[1]}" r="${i === pts.length - 1 ? 4.5 : 3}" fill="${i === pts.length - 1 ? '#5BE7C4' : '#0E1217'}" stroke="#5BE7C4" stroke-width="2"/>`).join('')}</svg>
      </div>
      <div class="m3-card"><div class="m3-label">Drive of the week</div><div style="display:flex;justify-content:space-between;align-items:center;margin-top:.3em"><span style="font-weight:600">${esc(rc.driveOfWeek.partOfDay)}<br><span style="color:#A3AAB6;font-weight:400;font-size:.88em">${esc(rc.driveOfWeek.label)}</span></span><span class="app-num" style="font-size:1.6em">${rc.driveOfWeek.score}</span></div></div>
      <nav class="m3-nav" aria-hidden="true">
        <div><i><svg viewBox="0 0 24 24"><path d="M4 17.5c2.5-6 5-6 8-3s5.5 3 8-3"/></svg></i>Drives</div>
        <div><i><svg viewBox="0 0 24 24"><path d="M6 19c0-4 3-5 6-6s6-2 6-6"/><circle cx="6" cy="19" r="2"/><circle cx="18" cy="7" r="2"/></svg></i>Routes</div>
        <div class="on"><i><svg viewBox="0 0 24 24"><path d="M4 20V10M10 20V5M16 20v-7M22 20H2"/></svg></i>Insights</div>
        <div><i><svg viewBox="0 0 24 24"><path d="M3 11l9-6 9 6v9H3z"/></svg></i>Garage</div>
      </nav>
    </div>`;
  }
  function screenReplay(trip = NEEDED_TRIP) {
    const W = 300, H = 400, r = route(trip, W, H, { pad: 40, rotate: 0, trimStart: 650, trimEnd: 520 });
    const ev = trip.events.find((e) => e.needed);
    const P = window.DP_MOTION && trip.events.map((e) => { const p = r.at(e.t); return { e, p }; });
    const pin = ({ e, p }) => {
      const col = { brake: '#FF7A66', accel: '#E58BD0', corner: '#EAD54E' }[e.type];
      if (e.needed) return `<rect x="${p[0] - 7}" y="${p[1] - 7}" width="14" height="14" rx="4" fill="#05070A" stroke="#C9CED6" stroke-width="2"/>`;
      if (e.type === 'brake') return `<rect x="${p[0] - 7}" y="${p[1] - 7}" width="14" height="14" rx="4" fill="${col}" stroke="#fff" stroke-width="2"/>`;
      if (e.type === 'accel') return `<circle cx="${p[0]}" cy="${p[1]}" r="7.5" fill="${col}" stroke="#fff" stroke-width="2"/>`;
      return `<rect x="${p[0] - 6}" y="${p[1] - 6}" width="12" height="12" transform="rotate(45 ${p[0]} ${p[1]})" fill="${col}" stroke="#fff" stroke-width="2"/>`;
    };
    return `<div class="app app--replay">
      <div class="replay-map"><svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
        <path d="${r.d}" fill="none" stroke="#04241D" stroke-width="9" stroke-linecap="round"/>
        <path d="${r.d}" fill="none" stroke="#5BE7C4" stroke-width="4.5" stroke-linecap="round"/>
        ${P.map(pin).join('')}
      </svg><span class="map-attrib">© OpenStreetMap contributors</span></div>
      <div class="replay-sheet">
        <div class="title"><svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true"><rect x="1.5" y="1.5" width="15" height="15" rx="4" fill="#05070A" stroke="#C9CED6" stroke-width="2"/></svg>Hard stop at ${esc(ev.place)}</div>
        <div class="sub">Was it needed? You said yes, so it's left out of your score.</div>
        <div class="ios-btns"><span class="pri">Yes, it was needed</span><span>Not quite</span></div>
      </div>
    </div>`;
  }

  /* Story frames for the scroll-driven card stack (A2 §3.1, copy from trip.story.frames) */
  function storyFrames(trip = HERO_TRIP) {
    const f = trip.story.frames, W = 240, H = 300;
    const r = route(trip, W, H, { pad: 26, rotate: -18 });
    const clean = r.segment(trip.cleanest.t0, trip.cleanest.t1);
    const ev = trip.tip; // no events on the hero trip; the tip comes from the strongest sub-threshold stop
    const routeSvg = (hl) => `<svg viewBox="0 0 ${W} ${H}" style="width:100%;height:100%" aria-hidden="true">
        <path d="${r.d}" fill="none" stroke="#04241D" stroke-width="10" stroke-linecap="round"/>
        <path class="${hl ? '' : 'sf-draw'}" d="${r.d}" fill="none" stroke="${hl ? '#5BE7C459' : '#5BE7C4'}" stroke-width="4.5" stroke-linecap="round"/>
        ${hl ? `<path d="${clean}" fill="none" stroke="#5BE7C4" stroke-width="12" stroke-linecap="round" opacity=".22"/><path d="${clean}" fill="none" stroke="#C6F8EA" stroke-width="4.5" stroke-linecap="round"/>` : ''}
      </svg>`;
    const tipTrace = (() => { // a calm sparkline from the hero trip's longitudinal G near the end of the drive
      const rows = U.rows(trip).filter((x) => x.t > trip.durationS - 260 && x.lonG != null);
      const pts = rows.map((x, i) => [6 + i * (228 / Math.max(1, rows.length - 1)), 30 - x.lonG * 60]);
      return `<svg viewBox="0 0 240 60" style="width:100%;height:auto" aria-hidden="true"><path d="M0 30H240" stroke="#FFFFFF1F"/><path d="${smoothPath(pts)}" fill="none" stroke="#5AB8F5" stroke-width="2" stroke-linecap="round"/><circle cx="${pts[pts.length - 3]?.[0] || 200}" cy="${pts[pts.length - 3]?.[1] || 30}" r="5" fill="none" stroke="#fff" stroke-width="1.5"/></svg>`;
    })();
    return [
      { n: 1, label: 'The drive', html: `<div class="app app--story">${progress(1)}${storyTop(trip)}<div class="story-stage" style="place-items:center"><svg viewBox="0 0 200 90" style="width:86%;opacity:.9" aria-hidden="true"><path d="M14 66h172M24 64c0-8 2-14 8-18l22-16c6-4 12-6 20-6h46c8 0 14 3 20 8l18 14c10 1 20 4 24 10 2 3 2 6 2 8" fill="none" stroke="#E5484D" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/><path d="M60 44l14-11c4-3 8-4 12-4h18v15zM112 29h12c6 0 10 2 14 6l9 9h-35z" fill="#E5484D22" stroke="#E5484D99" stroke-width="1.4" stroke-linejoin="round"/><circle cx="58" cy="66" r="12" fill="#05070A" stroke="#F2F4F7" stroke-width="2.4"/><circle cx="152" cy="66" r="12" fill="#05070A" stroke="#F2F4F7" stroke-width="2.4"/><circle cx="58" cy="66" r="4" fill="#F2F4F7"/><circle cx="152" cy="66" r="4" fill="#F2F4F7"/></svg></div><div class="story-copy"><p style="color:#A3AAB6;font-weight:600;margin-bottom:.5em">Drive decoded</p><h4 style="font-size:2.1em">Thursday<br>morning</h4><p style="font-size:1.1em">Home → Work</p></div></div>` },
      { n: 2, label: 'The route', html: `<div class="app app--story">${progress(2)}${storyTop(trip)}<div class="story-stage">${routeSvg(false)}</div><div class="story-copy"><h4 class="num" style="font-family:inherit">${esc(f.f2.line)}</h4><p>Ends trimmed. The route draws on, start to finish.</p></div></div>` },
      { n: 3, label: 'Your inputs', html: `<div class="app app--story">${progress(3)}${storyTop(trip)}<div class="story-stage" data-gg>${gg(trip, { size: 200 })}</div><div class="story-copy"><h4>${esc(f.f3.line)}</h4><p>${esc(f.f3.sub)}</p></div></div>` },
      { n: 4, label: 'Smoothness', html: `<div class="app app--story">${progress(4)}${storyTop(trip)}<div class="story-stage"><div style="width:74%;aspect-ratio:1;font-size:calc(var(--w) * .19)" data-ring-host>${ring({ score: 91, baseline: 84, recordPrev: 89, isNewRecord: true })}</div></div><div class="story-copy"><h4>Smoothness 91</h4><span class="story-chip story-chip--better">▲ ${esc(f.f4.compare)}</span></div></div>` },
      { n: 5, label: 'Cleanest stretch', html: `<div class="app app--story">${progress(5)}${storyTop(trip)}<div class="story-stage">${routeSvg(true)}</div><div class="story-copy"><h4>Cleanest stretch</h4><p>${esc(trip.cleanest.text)}</p></div></div>` },
      { n: 6, label: 'One tip', html: `<div class="app app--story">${progress(6)}${storyTop(trip)}<div class="story-stage" style="padding:0 4%">${tipTrace}</div><div class="story-copy"><h4>One thing for next time</h4><p>${esc(f.f6.line.replace(/^One thing for next time: /, '').replace(/^./, (c) => c.toUpperCase()))}</p></div></div>` },
    ];
  }

  window.DPW_MOCK = { route, projector, smoothPath, trackPoints, project, ring, animateRing, gg, animateGG, device, screenStoryRing, screenRecap, screenReplay, storyFrames, HERO_TRIP, RECAP, NEEDED_TRIP, esc };
})();
