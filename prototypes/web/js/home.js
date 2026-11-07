/* S-WEB-01 Home — hero A/B, route draw-on, Story stack, ring reveal, evidence, routes, recap, pricing (DRIVE-169 · DRIVE-171). */
(function () {
  'use strict';
  const D = window.DP_DATA, U = D.util;
  window.DPW.onReady(() => {
    const M = window.DPW_MOCK, W = window.DPW, $ = (id) => document.getElementById(id);
    const reduced = W.reduced();
    const NS = 'http://www.w3.org/2000/svg';
    const io = (el, fn, opts = { threshold: 0.35 }) => {
      if (!el) return;
      if (reduced || !('IntersectionObserver' in window)) { fn(); return; }
      const o = new IntersectionObserver((en) => { if (en.some((e) => e.isIntersecting)) { o.disconnect(); fn(); } }, opts);
      o.observe(el);
    };
    document.querySelectorAll('[data-yes]').forEach((el) => (el.innerHTML = W.icon('check') + '<span class="sr-only">Included</span>'));

    /* ---------- A/B variant (F-WEB-01: random per visit, no cookies or storage) ---------- */
    document.addEventListener('dpw:state', (e) => {
      const st = e.detail.state;
      let v;
      if (st === 'variantB') v = 'B';
      else if (e.detail.explicit) v = 'A';
      else v = Math.random() < 0.5 ? 'A' : 'B';
      document.body.dataset.variant = v;
      $('wl-variant').value = v;
      document.title = v === 'A' ? 'DrivePerf: Smooth is a skill.' : 'DrivePerf: Every drive, decoded.';
      if (!e.detail.explicit) W.track('experiment_assigned', { variant: v });
      if (st !== 'default' && st !== 'variantA' && st !== 'variantB') {
        setTimeout(() => $('waitlist').scrollIntoView({ block: 'center', behavior: reduced ? 'auto' : 'smooth' }), W.shot ? 0 : 150);
      }
    });

    /* ---------- Hero: devices + route draw-on ---------- */
    const devs = $('hero-devices');
    function layoutDevices() {
      const cw = devs.clientWidth || 360, desktop = innerWidth >= 960;
      const iw = Math.round(Math.min(desktop ? 300 : 250, cw * 0.6)), aw = Math.round(iw * 0.93);
      devs.style.height = Math.round(iw * 2.08 + 56) + 'px';
      devs.innerHTML =
        M.device('android', M.screenRecap(), { width: aw, cls: 'hero-android', style: `position:absolute;top:${Math.round(iw * 0.22)}px;left:calc(50% - ${Math.round(aw * 0.98)}px);` }) +
        M.device('ios', M.screenStoryRing(), { width: iw, cls: 'hero-ios', style: `position:absolute;top:0;left:calc(50% - ${Math.round(iw * 0.12)}px);` });
      const ringEl = devs.querySelector('.hero-ios .ring');
      if (ringEl) M.animateRing(ringEl, { delay: reduced ? 0 : 1500 });
      if (!reduced) {
        devs.querySelectorAll('.device').forEach((d, i) => d.animate([{ opacity: 0, transform: 'translateY(40px)' }, { opacity: 1, transform: 'none' }], { duration: 900, delay: 250 + i * 140, easing: 'cubic-bezier(0.32, 0.72, 0, 1)', fill: 'both' }));
      }
    }

    const hero = document.querySelector('.hero'), svg = $('hero-route');
    const heroTrip = M.HERO_TRIP;
    const others = ['t20261105-1745', 't20261101-1020', 't20261024-0930', 't20261013-1742', 't20261017-1120', 't20261104-0650'].map(U.trip);
    function drawHero(animate) {
      const w = hero.clientWidth, h = hero.clientHeight, desktop = w >= 960;
      svg.setAttribute('viewBox', `0 0 ${w} ${h}`);
      const box = desktop ? { x: w * 0.44, y: h * 0.03, w: w * 0.64, h: h * 0.84 } : { x: -w * 0.05, y: h * 0.50, w: w * 1.1, h: h * 0.5 };
      const r = M.route(heroTrip, w, h, { pad: 0, rotate: -6, box });
      const clean = r.segment(heroTrip.cleanest.t0, heroTrip.cleanest.t1);
      const roads = others.map((t) => M.route(t, w, h, { P: r.P }).d).join(' ');
      // Where the cleanest stretch sits along the line, as a fraction of its length (polyline approximation)
      const cum = [0]; for (let i = 1; i < r.xy.length; i++) cum.push(cum[i - 1] + Math.hypot(r.xy[i][0] - r.xy[i - 1][0], r.xy[i][1] - r.xy[i - 1][1]));
      const tot = cum[cum.length - 1], fr = (t) => { const i = r.pts.findIndex((p) => p.t >= t); return cum[Math.max(0, i)] / tot; };
      const c0 = fr(heroTrip.cleanest.t0), c1 = fr(heroTrip.cleanest.t1);
      const mid = r.xy[r.pts.findIndex((p) => p.t >= (heroTrip.cleanest.t0 + heroTrip.cleanest.t1) / 2)];
      svg.innerHTML = `
        <defs>
          <filter id="hglow" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="${desktop ? 10 : 7}"/></filter>
          <radialGradient id="hhead"><stop offset="0" stop-color="#fff"/><stop offset=".35" stop-color="#C6F8EA" stop-opacity=".9"/><stop offset="1" stop-color="#5BE7C4" stop-opacity="0"/></radialGradient>
          <linearGradient id="hfade" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#5BE7C4" stop-opacity=".35"/><stop offset=".35" stop-color="#5BE7C4"/><stop offset="1" stop-color="#C6F8EA"/></linearGradient>
        </defs>
        <path d="${roads}" fill="none" stroke="var(--line-strong)" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" opacity=".55"/>
        <path d="${r.d}" fill="none" stroke="var(--line-strong)" stroke-width="2" stroke-linecap="round"/>
        <path class="hr-glow" d="${r.d}" fill="none" stroke="#5BE7C4" stroke-width="${desktop ? 16 : 12}" stroke-linecap="round" opacity=".28" filter="url(#hglow)"/>
        <path class="hr-main" d="${r.d}" fill="none" stroke="url(#hfade)" stroke-width="${desktop ? 4 : 3.2}" stroke-linecap="round" stroke-linejoin="round"/>
        <path class="hr-clean" d="${clean}" fill="none" stroke="#C6F8EA" stroke-width="${desktop ? 6 : 5}" stroke-linecap="round" opacity="0"/>
        ${desktop && mid ? `<g class="hr-chip" opacity="0" transform="translate(${Math.max(16, mid[0] - 245).toFixed(1)} ${Math.max(84, mid[1] - 58).toFixed(1)})"><rect width="236" height="34" rx="17" fill="var(--glass)" stroke="var(--glass-stroke)"/><circle cx="18" cy="17" r="4" fill="#5BE7C4"/><text x="31" y="21.5" font-size="12.5" font-weight="600" fill="var(--text)" style="font-family:var(--f-text)">Cleanest stretch · 14.2 km, QEW</text></g>` : ''}
        <path class="hr-trail" d="${r.d}" fill="none" stroke="#FFFFFF" stroke-width="${desktop ? 3.5 : 3}" stroke-linecap="round" opacity="0"/>
        <g class="hr-marker" opacity="0"><circle r="${desktop ? 20 : 15}" fill="url(#hhead)" opacity=".85"/><circle r="${desktop ? 5.5 : 4.5}" fill="#fff"/></g>
        <circle class="hr-head" r="${desktop ? 18 : 13}" fill="url(#hhead)" opacity="0"/>
        <circle class="hr-dot" r="${desktop ? 6 : 5}" fill="#fff" opacity="0"/>`;
      const q = (s) => svg.querySelector(s);
      const main = q('.hr-main'), glow = q('.hr-glow'), head = q('.hr-head'), dot = q('.hr-dot'), cl = q('.hr-clean');
      const L = main.getTotalLength(), end = main.getPointAtLength(L);
      // Run the marker only over the longest stretch of line that isn't hidden by the copy mask or the phones
      const hr = hero.getBoundingClientRect(), devRects = [...devs.querySelectorAll('.device')].map((d) => { const b = d.getBoundingClientRect(); return { l: b.left - hr.left - 8, t: b.top - hr.top - 8, r: b.right - hr.left + 8, b: b.bottom - hr.top + 8 }; });
      const seen = (x, y) => (desktop ? x > w * 0.52 : y > h * 0.6) && y > 70 && x < w - 8 && !devRects.some((q) => x > q.l && x < q.r && y > q.t && y < q.b);
      let best = [0, 1], run = null, bestLen = 0;
      for (let k = 0; k <= 240; k++) { const u = k / 240, pt = main.getPointAtLength(L * u); if (seen(pt.x, pt.y)) { if (run === null) run = u; if (u - run > bestLen) { bestLen = u - run; best = [run, u]; } } else run = null; }
      replay.set({ main, L, c0, c1, cl, win: bestLen > 0.12 ? best : [0, 1], trail: q('.hr-trail'), marker: q('.hr-marker'), chip: q('.hr-chip'), dot, head });
      const settle = () => {
        dot.setAttribute('cx', end.x); dot.setAttribute('cy', end.y); dot.setAttribute('opacity', '1');
        head.setAttribute('cx', end.x); head.setAttribute('cy', end.y); head.setAttribute('opacity', '.55');
        cl.setAttribute('opacity', '.9');
      };
      if (!animate || reduced) { settle(); if (animate && !W.shot) [main, glow].forEach((p) => p.animate([{ opacity: 0 }, { opacity: getComputedStyle(p).opacity }], { duration: 220 })); replay.start(animate ? 2400 : 600); return; }
      [main, glow].forEach((p) => { p.style.strokeDasharray = `${L} ${L}`; });
      const ease = (t) => window.DP_MOTION.ease.drawOn(t);
      const dur = 2400, delay = 350; let t0;
      const tick = (now) => {
        t0 ??= now; const p = Math.max(0, Math.min(1, (now - t0 - delay) / dur)), e = ease(p);
        const off = L * (1 - e); main.style.strokeDashoffset = off; glow.style.strokeDashoffset = off;
        const pt = main.getPointAtLength(L * e);
        head.setAttribute('cx', pt.x); head.setAttribute('cy', pt.y); head.setAttribute('opacity', p > 0 && p < 1 ? '1' : '0');
        if (p < 1) requestAnimationFrame(tick);
        else {
          main.style.strokeDasharray = ''; glow.style.strokeDasharray = '';
          settle();
          cl.animate([{ opacity: 0 }, { opacity: 0.9 }], { duration: 700, easing: 'ease-out' });
          dot.style.transformBox = 'fill-box'; dot.style.transformOrigin = 'center';
          dot.animate([{ transform: 'scale(0)' }, { transform: 'scale(1)' }], { duration: 540, easing: getComputedStyle(document.documentElement).getPropertyValue('--spring-pin-pop').trim() || 'ease-out' });
          replay.start(1600);
        }
      };
      requestAnimationFrame(tick);
    }

    /* Hero replay marker (motion v2): after the draw-on, a marker glides the route like Trip Replay, a bright trail
       follows it, and as it enters the cleanest stretch the segment brightens and a chip pops in. Pauses when the hero
       is off screen or the tab is hidden. Reduced motion / shot: no marker, the static final state above. */
    const replay = (() => {
      let el = null, raf = 0, startAt = 0, pausedAt = 0, visible = true, armed = false, wait = 0;
      const SPEED = 1 / 15000, GAP = 1600, TRAIL = 0.07; // full route in 15 s; the visible window takes its share
      const ss = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
      const frame = (now) => {
        raf = 0;
        if (!el || !visible || document.hidden) return;
        const [ua, ub] = el.win, PERIOD = Math.max(4000, (ub - ua) / SPEED);
        const t = (now - startAt) % (PERIOD + GAP), v = Math.min(1, t / PERIOD), u = ua + (ub - ua) * v;
        const fade = t > PERIOD ? 0 : ss(0, 0.06, v) * (1 - ss(0.92, 1, v));
        const pt = el.main.getPointAtLength(el.L * u);
        el.marker.setAttribute('transform', `translate(${pt.x.toFixed(1)} ${pt.y.toFixed(1)})`);
        el.marker.setAttribute('opacity', fade.toFixed(3));
        const tl = el.L * TRAIL, head = el.L * u;
        el.trail.style.strokeDasharray = `${Math.min(tl, head).toFixed(1)} ${el.L.toFixed(1)}`;
        el.trail.style.strokeDashoffset = (-(Math.max(0, head - tl))).toFixed(1);
        el.trail.setAttribute('opacity', (0.85 * fade).toFixed(3));
        const inClean = ss(el.c0 - 0.01, el.c0 + 0.02, u) * (1 - ss(el.c1 + 0.05, el.c1 + 0.12, u));
        el.cl.setAttribute('opacity', (0.55 + 0.45 * inClean).toFixed(3));
        if (el.chip) { el.chip.setAttribute('opacity', inClean.toFixed(3)); }
        raf = requestAnimationFrame(frame);
      };
      const kick = () => { if (!raf && armed && visible && !document.hidden) raf = requestAnimationFrame(frame); };
      if ('IntersectionObserver' in window) new IntersectionObserver((en) => { visible = en[0].isIntersecting; if (visible) { startAt += performance.now() - pausedAt; kick(); } else pausedAt = performance.now(); }, { threshold: 0.05 }).observe(hero);
      document.addEventListener('visibilitychange', () => { if (document.hidden) pausedAt = performance.now(); else { startAt += performance.now() - pausedAt; kick(); } });
      return {
        set(x) { el = x; },
        start(delay) {
          if (reduced) return;
          clearTimeout(wait);
          wait = setTimeout(() => { armed = true; startAt = performance.now(); pausedAt = startAt; kick(); }, delay);
        },
      };
    })();
    layoutDevices(); drawHero(true);
    let rz; addEventListener('resize', () => { clearTimeout(rz); rz = setTimeout(() => { layoutDevices(); drawHero(false); }, 150); });

    /* ---------- Story stack (motion v2: auto-cycles like the real Story player) ----------
       Each frame dwells 3.4 s while its progress segment fills, then flies up and out; the next card springs forward.
       Plays only while the section is on screen and the tab is visible; pauses on hover and with the Pause button
       (WCAG 2.2.2). Steps are buttons that jump to a frame. Reduced motion / shot: static row of frames. */
    const frames = M.storyFrames();
    const stack = $('stack'), stat = $('stack-static'), storySec = document.querySelector('.story-scroll');
    const steps = [...document.querySelectorAll('#story-steps li')];
    const toggle = $('story-toggle');
    const card = (f) => `<div class="stack__card" data-frame="${f.n}" role="listitem" aria-label="Story frame ${f.n}: ${f.label}">${f.html}</div>`;
    stack.innerHTML = frames.map(card).join('');
    stat.innerHTML = M.storyFrames().map(card).join(''); // separate render: SVG gradient ids must be unique
    if (reduced || W.shot) {
      storySec.setAttribute('data-static', '');
      steps.forEach((s) => { s.classList.add('is-active'); s.querySelector('button')?.setAttribute('tabindex', '-1'); });
      if (toggle) toggle.hidden = true;
      stat.querySelectorAll('[data-ring-host] .ring').forEach((r) => M.animateRing(r));
      stat.querySelectorAll('[data-gg] svg').forEach((s) => M.animateGG(s, M.HERO_TRIP));
    } else {
      const cards = [...stack.children], n = cards.length, DWELL = 3400;
      let idx = 0, timer = 0, fill = null, stopGG = null, playing = true, visible = false, hover = false, entered = -1, remaining = DWELL;
      const place = () => cards.forEach((c, i) => {
        const d = (i - idx + n) % n; let tf, op;
        if (d === 0) { tf = 'none'; op = 1; }
        else if (d === n - 1) { tf = 'translate3d(-6%, -112%, 0) rotate(-8deg)'; op = 0; }
        else if (d <= 3) { tf = `translate3d(${d * 26}px, ${d * 14}px, ${-d * 70}px) rotate(${d * 3.2}deg)`; op = 1 - d * 0.2; }
        else { tf = 'translate3d(104px, 56px, -280px) rotate(12.8deg)'; op = 0; }
        c.style.transform = tf; c.style.opacity = op; c.style.zIndex = String(20 - d);
        c.style.filter = d > 0 && d < n - 1 ? `brightness(${1 - d * 0.18})` : '';
      });
      const running = () => playing && visible && !hover && !document.hidden;
      const effects = () => {
        if (stopGG) { stopGG(); stopGG = null; }
        const c = cards[idx];
        if (entered !== idx) {
          entered = idx;
          if (idx === 1) { const p = c.querySelector('.sf-draw'); if (p) window.DP_MOTION.drawOn(p, { duration: 1800, delay: 200 }); }
          if (idx === 3) M.animateRing(c.querySelector('.ring'), { delay: 250, spring: true });
        }
        if (idx === 2 && running()) stopGG = M.animateGG(c.querySelector('svg.gg'), M.HERO_TRIP);
      };
      const arm = () => {
        clearTimeout(timer);
        if (!running()) { fill?.pause(); if (stopGG) { stopGG(); stopGG = null; } return; }
        fill?.play();
        if (idx === 2 && !stopGG) stopGG = M.animateGG(cards[2].querySelector('svg.gg'), M.HERO_TRIP);
        const left = fill ? Math.max(0, DWELL - (fill.currentTime || 0)) : remaining;
        timer = setTimeout(() => go(idx + 1), left);
      };
      const go = (i) => {
        idx = (i + n) % n; place();
        steps.forEach((s, k) => { s.classList.toggle('is-active', k === idx); s.querySelector('button')?.setAttribute('aria-current', k === idx ? 'step' : 'false'); });
        if (idx === 0) entered = -1;
        fill?.cancel();
        const b = cards[idx].querySelector('.story-progress .cur b');
        fill = b ? b.animate([{ transform: 'scaleX(0)' }, { transform: 'scaleX(1)' }], { duration: DWELL, easing: 'linear', fill: 'forwards' }) : null;
        effects(); arm();
      };
      steps.forEach((s, k) => s.querySelector('button')?.addEventListener('click', () => { go(k); }));
      stack.addEventListener('pointerenter', () => { hover = true; arm(); });
      stack.addEventListener('pointerleave', () => { hover = false; arm(); });
      toggle?.addEventListener('click', () => {
        playing = !playing; toggle.setAttribute('aria-pressed', String(!playing));
        toggle.innerHTML = playing ? `${W.icon('pause')}<span>Pause</span>` : `${W.icon('play')}<span>Play</span>`;
        arm();
      });
      document.addEventListener('visibilitychange', arm);
      if ('IntersectionObserver' in window) new IntersectionObserver((en) => { visible = en[0].isIntersecting; arm(); }, { threshold: 0.45 }).observe(stack);
      place(); go(0);
    }

    /* ---------- Smoothness ring section ---------- */
    const sm = M.HERO_TRIP.smoothness;
    $('score-ring').innerHTML = `<div style="width:min(420px,78vw);aspect-ratio:1;font-size:min(140px,27vw)">${M.ring({ score: sm.score, baseline: sm.baseline, recordPrev: sm.record.previous.score, isNewRecord: true, strokeW: 7 })}</div>`;
    const bigRing = $('score-ring').querySelector('.ring');
    if (!reduced) { bigRing.querySelector('.ring-arc').style.strokeDashoffset = bigRing.querySelector('.ring-arc').dataset.c; bigRing.querySelector('.ring-num').textContent = '0'; }
    io(bigRing, () => M.animateRing(bigRing, { spring: true, delay: 120 }));
    $('contrib').innerHTML = sm.contributors.map((c) => `<li><b>${c.label}</b><span>${c.reason}</span><em class="${c.band === 'smoother' ? 'better' : ''}">${c.band === 'smoother' ? '▲ Smoother' : c.band === 'rougher' ? '▼ Rougher' : '● Usual'}</em></li>`).join('');

    /* ---------- Evidence panel (needed brake, Spadina & King) ---------- */
    (function evidence() {
      const t = M.NEEDED_TRIP, ev = t.events.find((e) => e.needed), host = $('evidence-map');
      const w = 640, h = 400;
      const r = M.route(t, w, h, { pad: 40, rotate: 42 });
      const roads = ['t20261105-1745', 't20261013-1742', 't20261101-1020'].map((id) => M.route(U.trip(id), w, h, { P: r.P }).d).join(' ');
      const pins = t.events.map((e) => {
        const [x, y] = r.at(e.t);
        if (e.needed) return `<g><circle cx="${x}" cy="${y}" r="18" fill="none" stroke="#C9CED6" stroke-width="1.5" opacity=".5" class="pulse"/><rect x="${x - 10}" y="${y - 10}" width="20" height="20" rx="6" fill="#05070A" stroke="#C9CED6" stroke-width="2.4"/></g>`;
        const col = { brake: 'var(--data-brake)', accel: 'var(--data-accel)', corner: 'var(--data-corner)' }[e.type];
        if (e.type === 'brake') return `<rect x="${x - 9}" y="${y - 9}" width="18" height="18" rx="5" fill="${col}" stroke="#fff" stroke-width="2.4"/>`;
        if (e.type === 'accel') return `<circle cx="${x}" cy="${y}" r="9.5" fill="${col}" stroke="#fff" stroke-width="2.4"/>`;
        return `<rect x="${x - 8}" y="${y - 8}" width="16" height="16" transform="rotate(45 ${x} ${y})" fill="${col}" stroke="#fff" stroke-width="2.4"/>`;
      }).join('');
      host.innerHTML = `<svg viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMidYMid slice" role="img" aria-label="Map of a Tuesday morning commute with three event pins. The stop at Spadina and King is marked as needed.">
        <rect width="${w}" height="${h}" fill="var(--map-land)"/>
        <path d="${roads}" fill="none" stroke="var(--map-road)" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="${r.d}" fill="none" stroke="#04241D" stroke-width="10" stroke-linecap="round"/>
        <path d="${r.d}" fill="none" stroke="#5BE7C4" stroke-width="5" stroke-linecap="round"/>
        ${pins}
        <text x="${w - 12}" y="${h - 12}" text-anchor="end" font-size="11" fill="#A3AAB6">© OpenStreetMap contributors</text>
      </svg>`;
      const E = ev.evidence, n = E.lonG.length, tr = $('evidence-trace');
      const X = (i) => (i / (n - 1)) * 600, Y = (g) => 60 - g * 110;
      const line = (arr) => M.smoothPath(arr.map((g, i) => [X(i), Y(g)]));
      const i0 = (ev.tStart - ev.t + 10) * E.hz, i1 = (ev.tEnd - ev.t + 10) * E.hz;
      tr.innerHTML = `<rect x="${X(i0)}" y="0" width="${X(i1) - X(i0)}" height="120" fill="var(--fill)"/>
        <path d="M0 60H600" stroke="var(--line-strong)" stroke-width="1"/>
        <path d="M300 0V120" stroke="var(--text-2)" stroke-width="1" stroke-dasharray="3 4"/>
        <path d="${line(E.latG)}" fill="none" stroke="var(--data-lat-g)" stroke-width="2.2" stroke-dasharray="7 4" vector-effect="non-scaling-stroke"/>
        <path class="tr-lon" d="${line(E.lonG)}" fill="none" stroke="var(--data-long-g)" stroke-width="2.6" vector-effect="non-scaling-stroke"/>`;
      io(tr, () => { const p = tr.querySelector('.tr-lon'); if (p) window.DP_MOTION.drawOn(p, { duration: 1400 }); });
      document.querySelectorAll('.answer-row .chip').forEach((b) => b.addEventListener('click', () => document.querySelectorAll('.answer-row .chip').forEach((x) => x.setAttribute('aria-pressed', String(x === b)))));
    })();

    /* ---------- Route glyph + dot strip (motion v2: the track fills left to right, each run pops in as the fill
       reaches it, and the gold record lands last with one ring pulse). Trigger: 40 % in view, once. ---------- */
    const POP = getComputedStyle(document.documentElement).getPropertyValue('--spring-pin-pop').trim() || 'cubic-bezier(0.32, 0.72, 0, 1)';
    (function routes() {
      const rt = U.route('r_commute');
      $('route-glyph').innerHTML = `<path d="${U.glyphPath(rt.glyph, 120, 96, 8)}" fill="none" stroke="var(--accent)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>`;
      const runs = rt.runs, t0 = +new Date(runs[0].date), t1 = +new Date(runs[runs.length - 1].date);
      const strip = $('dotstrip');
      const pos = runs.map((x) => 2 + ((+new Date(x.date) - t0) / (t1 - t0)) * 96);
      strip.innerHTML = `<b class="fill" style="width:${pos[pos.length - 1]}%"></b>` + runs.map((x, i) => {
        const rec = x.tripId === rt.record.tripId;
        return `<i class="${rec ? 'rec' : ''}" style="left:${pos[i]}%" title="${U.fmtDate(x.date)} · ${x.score}"></i>`;
      }).join('') + `<span class="rec-pulse" style="left:${pos[runs.findIndex((x) => x.tripId === rt.record.tripId)]}%"></span>`;
      if (reduced) return;
      const fillEl = strip.querySelector('.fill'), dots = [...strip.querySelectorAll('i')], pulse = strip.querySelector('.rec-pulse');
      const FILL = 1500, last = pos[pos.length - 1];
      fillEl.style.transform = 'scaleX(0)'; dots.forEach((d) => (d.style.opacity = '0'));
      io(strip, () => {
        fillEl.animate([{ transform: 'scaleX(0)' }, { transform: 'scaleX(1)' }], { duration: FILL, easing: 'cubic-bezier(0.45, 0, 0.25, 1)', fill: 'forwards' });
        dots.forEach((d, i) => {
          const at = Math.round((pos[i] / last) * FILL * 0.92) + 60;
          d.animate([{ transform: 'scale(0)', opacity: 0 }, { transform: 'scale(1)', opacity: 1 }], { duration: 540, delay: at, easing: POP, fill: 'forwards' });
        });
        pulse.animate([{ transform: 'scale(.6)', opacity: 0.7 }, { transform: 'scale(2.6)', opacity: 0 }], { duration: 1100, delay: FILL + 120, easing: 'cubic-bezier(0.32, 0.72, 0, 1)', iterations: 2 });
      }, { threshold: 0.4 });
    })();

    /* ---------- Weekly recap strip (motion v2: one dot per drive lands day by day with the pin-pop spring) ---------- */
    (function recap() {
      const rc = M.RECAP, host = $('recap-strip');
      host.innerHTML = rc.days.map((d) => {
        const dots = d.drives.length ? d.drives.map((x) => { const s = Math.round(Math.min(34, 10 + Math.sqrt(x.km) * 4)); return `<span style="width:${s}px;height:${s}px"></span>`; }).join('') : '<span class="empty"></span>';
        return `<div>${dots}<em style="font-style:normal">${d.dow}</em></div>`;
      }).join('');
      if (reduced) return;
      const spans = [...host.querySelectorAll('span:not(.empty)')];
      spans.forEach((s) => (s.style.opacity = '0'));
      io(host, () => spans.forEach((s, k) => s.animate([{ transform: 'translateY(-28px) scale(.4)', opacity: 0 }, { transform: 'none', opacity: 1 }], { duration: 640, delay: 120 + k * 150, easing: POP, fill: 'forwards' })), { threshold: 0.4 });
    })();

    /* ---------- Share card (route glyph style: no map, no place names, no speed) ---------- */
    (function share() {
      const t = M.HERO_TRIP;
      $('share-card').innerHTML = `<div class="share-card" data-component="ShareCard" style="aspect-ratio:4/5;border-radius:24px;overflow:hidden;position:relative;background:radial-gradient(90% 60% at 50% 0%, #E5484D24, transparent 70%),linear-gradient(180deg,#0A1420,#05070A);color:#F2F4F7;padding:22px;display:flex;flex-direction:column;box-shadow:var(--shadow-lg);border:1px solid #FFFFFF14">
        <div style="font-size:13px;color:#A3AAB6;font-weight:600">Thursday morning · GTI</div>
        <svg viewBox="0 0 200 160" style="flex:1;width:100%;min-height:0" aria-hidden="true"><path d="${U.glyphPath(t.glyph, 200, 160, 14)}" fill="none" stroke="#5BE7C4" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/></svg>
        <div style="display:flex;align-items:center;justify-content:space-between;gap:10px">
          <div style="width:64px;height:64px;font-size:22px">${M.ring({ score: 91, recordPrev: null, strokeW: 9, label: '' })}</div>
          <span class="chip chip--record" style="height:26px;font-size:12px">${W.icon('rosette')}Route record</span>
        </div>
        <div style="display:flex;align-items:center;gap:6px;justify-content:center;margin-top:14px;font:650 13px/1 var(--f-display);font-stretch:112%">${W.loop({ size: 18, dot: '#F2F4F7', color: '#5BE7C4' })}DrivePerf</div>
      </div>`;
      $('share-card').querySelector('.ring__label')?.remove();
    })();

    /* ---------- Pricing region toggle ---------- */
    (function pricing() {
      const P = D.entitlement.prices;
      const set = (rg) => {
        document.querySelectorAll('[data-region]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.region === rg)));
        const p = P[rg];
        document.querySelectorAll('[data-price]').forEach((el) => { const k = el.dataset.price; el.textContent = k === 'free' ? (rg === 'CA' ? 'CA$0' : 'US$0') : p[k]; });
        const o = P[rg === 'CA' ? 'US' : 'CA'];
        $('price-alt').textContent = rg === 'CA'
          ? `In the US: ${o.monthly} a month or ${o.annual} a year, with a founding first year at ${o.founding}.`
          : `In Canada: ${o.monthly} a month or ${o.annual} a year, with a founding first year at ${o.founding}.`;
      };
      document.querySelectorAll('[data-region]').forEach((b) => b.addEventListener('click', () => set(b.dataset.region)));
      set(new URLSearchParams(location.search).get('region') || (navigator.language === 'en-CA' && !W.shot ? 'CA' : 'US'));
    })();
  });
})();
