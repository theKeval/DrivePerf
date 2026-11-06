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
        <circle class="hr-head" r="${desktop ? 18 : 13}" fill="url(#hhead)" opacity="0"/>
        <circle class="hr-dot" r="${desktop ? 6 : 5}" fill="#fff" opacity="0"/>`;
      const main = svg.querySelector('.hr-main'), glow = svg.querySelector('.hr-glow'), head = svg.querySelector('.hr-head'), dot = svg.querySelector('.hr-dot'), cl = svg.querySelector('.hr-clean');
      const L = main.getTotalLength(), end = main.getPointAtLength(L);
      const settle = () => {
        dot.setAttribute('cx', end.x); dot.setAttribute('cy', end.y); dot.setAttribute('opacity', '1');
        head.setAttribute('cx', end.x); head.setAttribute('cy', end.y); head.setAttribute('opacity', '.55');
        cl.setAttribute('opacity', '.9');
      };
      if (!animate || reduced) { settle(); if (animate) [main, glow].forEach((p) => p.animate([{ opacity: 0 }, { opacity: getComputedStyle(p).opacity }], { duration: 220 })); return; }
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
          dot.animate([{ transform: 'scale(0)', transformOrigin: `${end.x}px ${end.y}px` }, { transform: 'scale(1)', transformOrigin: `${end.x}px ${end.y}px` }], { duration: 520, easing: 'cubic-bezier(0.32, 0.72, 0, 1)' });
        }
      };
      requestAnimationFrame(tick);
    }
    layoutDevices(); drawHero(true);
    let rz; addEventListener('resize', () => { clearTimeout(rz); rz = setTimeout(() => { layoutDevices(); drawHero(false); }, 150); });

    /* ---------- Story stack (scroll-driven) ---------- */
    const frames = M.storyFrames();
    const stack = $('stack'), stat = $('stack-static'), scrollSec = document.querySelector('.story-scroll'), track = $('story-track');
    const steps = [...document.querySelectorAll('#story-steps li')];
    const card = (f) => `<div class="stack__card" data-frame="${f.n}" role="listitem" aria-label="Story frame ${f.n}: ${f.label}">${f.html}</div>`;
    stack.innerHTML = frames.map(card).join('');
    stat.innerHTML = M.storyFrames().map(card).join(''); // separate render: SVG gradient ids must be unique
    const isStatic = reduced || W.shot;
    if (isStatic) {
      scrollSec.setAttribute('data-static', '');
      steps.forEach((s) => s.classList.add('is-active'));
      stat.querySelectorAll('[data-ring-host] .ring').forEach((r) => M.animateRing(r));
      stat.querySelectorAll('[data-gg] svg').forEach((s) => M.animateGG(s, M.HERO_TRIP));
    } else {
      const cards = [...stack.children]; let active = -1, stopGG = null;
      const enter = (i) => {
        const c = cards[i];
        if (stopGG) { stopGG(); stopGG = null; }
        if (i === 1) { const p = c.querySelector('.sf-draw'); if (p) window.DP_MOTION.drawOn(p, { duration: 1800 }); }
        if (i === 2) stopGG = M.animateGG(c.querySelector('svg.gg'), M.HERO_TRIP);
        if (i === 3) M.animateRing(c.querySelector('.ring'), { delay: 200 });
      };
      const layout = () => {
        const rect = track.getBoundingClientRect(), span = rect.height - innerHeight;
        const p = Math.max(0, Math.min(0.9999, -rect.top / span));
        const idx = Math.floor(p * cards.length);
        cards.forEach((c, i) => {
          const d = i - idx;
          let tf, op;
          if (d < 0) { tf = `translate3d(-8%, -118%, 0) rotate(-9deg)`; op = 0; }
          else if (d === 0) { tf = 'none'; op = 1; }
          else { tf = `translate3d(${d * 26}px, ${d * 14}px, ${-d * 70}px) rotate(${d * 3.2}deg)`; op = d > 3 ? 0 : 1 - d * 0.2; }
          c.style.transform = tf; c.style.opacity = op; c.style.zIndex = 20 - Math.abs(d); c.style.filter = d > 0 ? `brightness(${1 - d * 0.18})` : '';
        });
        if (idx !== active) { active = idx; steps.forEach((s, i) => s.classList.toggle('is-active', i === idx)); enter(idx); }
      };
      addEventListener('scroll', () => requestAnimationFrame(layout), { passive: true });
      addEventListener('resize', layout); layout();
    }

    /* ---------- Smoothness ring section ---------- */
    const sm = M.HERO_TRIP.smoothness;
    $('score-ring').innerHTML = `<div style="width:min(420px,78vw);aspect-ratio:1;font-size:min(140px,27vw)">${M.ring({ score: sm.score, baseline: sm.baseline, recordPrev: sm.record.previous.score, isNewRecord: true, strokeW: 7 })}</div>`;
    const bigRing = $('score-ring').querySelector('.ring');
    if (!reduced) { bigRing.querySelector('.ring-arc').style.strokeDashoffset = bigRing.querySelector('.ring-arc').dataset.c; bigRing.querySelector('.ring-num').textContent = '0'; }
    io(bigRing, () => M.animateRing(bigRing));
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

    /* ---------- Route glyph + dot strip ---------- */
    (function routes() {
      const rt = U.route('r_commute');
      $('route-glyph').innerHTML = `<path d="${U.glyphPath(rt.glyph, 120, 96, 8)}" fill="none" stroke="var(--accent)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>`;
      const runs = rt.runs, t0 = +new Date(runs[0].date), t1 = +new Date(runs[runs.length - 1].date);
      const strip = $('dotstrip');
      strip.innerHTML = runs.map((x, i) => {
        const left = 2 + ((+new Date(x.date) - t0) / (t1 - t0)) * 96, rec = x.tripId === rt.record.tripId;
        return `<i class="${rec ? 'rec' : ''}" style="left:${left}%;${reduced ? '' : 'transform:scale(0);'}transition-delay:${i * 60}ms" title="${U.fmtDate(x.date)} · ${x.score}"></i>`;
      }).join('');
      io(strip, () => strip.querySelectorAll('i').forEach((d) => (d.style.transform = 'scale(1)')));
    })();

    /* ---------- Weekly recap strip ---------- */
    (function recap() {
      const rc = M.RECAP, host = $('recap-strip');
      host.innerHTML = rc.days.map((d, di) => {
        const dots = d.drives.length ? d.drives.map((x, k) => { const s = Math.round(Math.min(34, 10 + Math.sqrt(x.km) * 4)); return `<span style="width:${s}px;height:${s}px;--s:${reduced ? 1 : 0};--d:${di * 90 + k * 60}ms"></span>`; }).join('') : '<span class="empty"></span>';
        return `<div>${dots}<em style="font-style:normal">${d.dow}</em></div>`;
      }).join('');
      io(host, () => host.querySelectorAll('span:not(.empty)').forEach((s) => s.style.setProperty('--s', '1')));
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
