/* Page behaviours for S-WEB-02 … S-WEB-11 (DRIVE-132 · A9). Each block keys off <body data-screen>. */
(function () {
  'use strict';
  const W = window.DPW, $ = (id) => document.getElementById(id);
  const screen = document.body.dataset.screen;
  const params = new URLSearchParams(location.search);
  const on = (id, fn) => { if (screen === id) W.onReady(fn); };

  /* Table of contents: highlight the section in view (legal pages, explainer) */
  W.onReady(() => {
    const toc = document.querySelector('.toc');
    if (!toc || !('IntersectionObserver' in window)) return;
    const links = [...toc.querySelectorAll('a[href^="#"]')];
    const map = new Map(links.map((a) => [a.getAttribute('href').slice(1), a]));
    const io = new IntersectionObserver((en) => en.forEach((e) => { if (e.isIntersecting) { links.forEach((l) => l.classList.remove('is-active')); map.get(e.target.id)?.classList.add('is-active'); } }), { rootMargin: '-20% 0px -70% 0px' });
    map.forEach((_, id) => { const s = document.getElementById(id); if (s) io.observe(s); });
  });

  /* Animated Loop glyph (draw-on) used on status pages */
  function drawLoop(host) {
    if (!host || W.reduced()) return;
    host.querySelectorAll('path').forEach((p, i) => { const L = p.getTotalLength(); p.style.strokeDasharray = `${L} ${L}`; p.animate([{ strokeDashoffset: L }, { strokeDashoffset: 0 }], { duration: 160, delay: 200 + i * 140, fill: 'both', easing: 'linear' }); });
    host.querySelectorAll('circle').forEach((c, i) => c.animate([{ opacity: 0, transform: 'scale(.4)' }, { opacity: 1, transform: 'none' }], { duration: 520, delay: 1050 + i * 120, fill: 'both', easing: 'cubic-bezier(0.32, 0.72, 0, 1)' }));
  }

  /* ---------- S-WEB-02 Waitlist confirmation ---------- */
  on('S-WEB-02', () => {
    drawLoop(document.querySelector('.status__icon'));
    const set = (pl) => {
      const android = pl === 'android';
      document.querySelectorAll('[data-pl]').forEach((el) => (el.textContent = android ? el.dataset.android : el.dataset.ios));
    };
    document.addEventListener('dpw:state', (e) => set(e.detail.state === 'android' ? 'android' : (e.detail.explicit ? 'ios' : params.get('platform') || 'ios')));
    const copy = $('copy-link');
    copy?.addEventListener('click', () => { copy.innerHTML = W.icon('check') + 'Link copied'; W.track('waitlist_link_copied'); setTimeout(() => (copy.innerHTML = W.icon('copy') + 'Copy link to driveperf.com'), 2200); });
  });

  /* ---------- S-WEB-06 How DrivePerf records: platform tabs ---------- */
  on('S-WEB-06', () => {
    const tabs = [...document.querySelectorAll('[role="tab"]')];
    const select = (pl, focus) => {
      tabs.forEach((t) => { const sel = t.dataset.pl === pl; t.setAttribute('aria-selected', String(sel)); t.tabIndex = sel ? 0 : -1; if (sel && focus) t.focus(); });
      document.querySelectorAll('[role="tabpanel"]').forEach((p) => (p.hidden = p.dataset.pl !== pl));
      document.querySelectorAll('[data-pl-text]').forEach((el) => (el.textContent = el.dataset[pl]));
    };
    tabs.forEach((t, i) => {
      t.addEventListener('click', () => select(t.dataset.pl));
      t.addEventListener('keydown', (e) => { if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') { e.preventDefault(); const n = tabs[(i + (e.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length]; select(n.dataset.pl, true); } });
    });
    document.addEventListener('dpw:state', (e) => select(e.detail.state === 'android' ? 'android' : e.detail.explicit ? 'ios' : (/android/i.test(navigator.userAgent) ? 'android' : 'ios')));
  });

  /* ---------- S-WEB-05 Support: FAQ filter ---------- */
  on('S-WEB-05', () => {
    const input = $('faq-search'), count = $('faq-count');
    if (!input) return;
    input.addEventListener('input', () => {
      const q = input.value.trim().toLowerCase(); let n = 0;
      document.querySelectorAll('.faq details').forEach((d) => { const hit = !q || d.textContent.toLowerCase().includes(q); d.hidden = !hit; if (hit) n++; if (q && hit) d.open = true; });
      document.querySelectorAll('.faq-group').forEach((g) => (g.hidden = !g.querySelector('details:not([hidden])')));
      count.textContent = q ? `${n} ${n === 1 ? 'answer' : 'answers'} for “${input.value.trim()}”` : '';
      $('faq-empty').hidden = n > 0;
    });
  });

  /* ---------- S-WEB-07 Account deletion request ---------- */
  on('S-WEB-07', () => {
    const form = $('del-form'), email = $('del-email'), field = $('f-del-email'), btn = $('del-submit');
    const show = (st) => { document.querySelectorAll('[data-del-view]').forEach((v) => v.classList.toggle('is-on', v.dataset.delView.split(' ').includes(st))); };
    const validate = () => {
      const v = email.value.trim();
      const msg = !v ? 'Enter the email address on your DrivePerf account.' : !W.EMAIL_RE.test(v) ? 'That email doesn’t look right. Check for a typo.' : '';
      W.setInvalid(field, msg); if (msg) email.focus(); return !msg;
    };
    form.addEventListener('input', () => W.setInvalid(field, null));
    form.addEventListener('submit', (e) => {
      e.preventDefault(); if (!validate()) return;
      W.setBusy(btn, true, 'Sending…');
      setTimeout(() => { W.setBusy(btn, false); W.track('web_deletion_requested'); $('sent-to').textContent = email.value.trim(); location.hash = '#S-WEB-07/emailSent'; }, 800);
    });
    document.addEventListener('dpw:state', (e) => {
      const st = e.detail.state;
      if (st === 'formErr') { email.value = 'marcus@example'; show('form'); validate(); email.blur(); }
      else { W.setInvalid(field, null); show(st); }
      if (st === 'emailSent' && !$('sent-to').textContent) $('sent-to').textContent = 'marcus@example.com';
    });
  });

  /* ---------- S-WEB-08 Ride-along landing ---------- */
  on('S-WEB-08', () => {
    const pass = window.DP_DATA?.entitlement?.states?.rideAlong || { from: 'Priya', of: 30 };
    document.querySelectorAll('[data-from]').forEach((el) => (el.textContent = pass.from));
    document.querySelectorAll('[data-initial]').forEach((el) => (el.textContent = pass.from[0]));
    const qr = $('qr'); if (qr) qr.innerHTML = qrSvg('https://driveperf.com/r/7KQ2-M9XD');
    const apply = (st) => {
      const desktop = st === 'desktop' || (st === 'default' && innerWidth >= 900);
      const mode = st === 'invalid' ? 'invalid' : desktop ? 'desktop' : 'mobile';
      document.querySelectorAll('[data-ra-view]').forEach((v) => v.classList.toggle('is-on', v.dataset.raView.split(' ').includes(mode)));
      document.body.dataset.raMode = mode;
      W.track('ridealong_link_opened', { has_app: false, mode });
    };
    document.addEventListener('dpw:state', (e) => apply(e.detail.state));
    addEventListener('resize', () => { if (document.body.dataset.state === 'default') apply('default'); });
  });

  /* Deterministic QR-style pattern (prototype only: not a scannable code). Production uses a real QR encoder at build time. */
  function qrSvg(text) {
    const n = 29; let seed = 0; for (const c of text) seed = (seed * 31 + c.charCodeAt(0)) >>> 0;
    const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296);
    const finder = (x, y) => (r, c) => r >= y && r < y + 7 && c >= x && c < x + 7;
    const F = [finder(0, 0), finder(n - 7, 0), finder(0, n - 7)];
    let cells = '';
    for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) {
      if (F.some((f) => f(r, c)) || F.some((f) => f(r - 1, c) || f(r + 1, c) || f(r, c - 1) || f(r, c + 1))) continue;
      if (r === 6 || c === 6 ? (r + c) % 2 === 0 : rnd() < 0.48) cells += `<rect x="${c}" y="${r}" width="1.02" height="1.02"/>`;
    }
    const fd = (x, y) => `<rect x="${x}" y="${y}" width="7" height="7" rx="1.2"/><rect x="${x + 1}" y="${y + 1}" width="5" height="5" rx=".8" fill="#fff"/><rect x="${x + 2}" y="${y + 2}" width="3" height="3" rx=".6"/>`;
    return `<svg viewBox="0 0 ${n} ${n}" role="img" aria-label="QR code that opens this Ride-along pass on your phone" fill="#0B0E13">${cells}${fd(0, 0)}${fd(n - 7, 0)}${fd(0, n - 7)}<rect x="${n / 2 - 3}" y="${n / 2 - 3}" width="6" height="6" rx="1.4" fill="#fff"/><circle cx="${n / 2}" cy="${n / 2}" r="2" fill="none" stroke="#079777" stroke-width="1"/><circle cx="${n / 2 + 1.4}" cy="${n / 2 - 1.4}" r=".7" fill="#0B0E13"/></svg>`;
  }

  /* ---------- S-WEB-09 Email preferences ---------- */
  on('S-WEB-09', () => {
    drawLoop(document.querySelector('.status__icon'));
    const re = $('resub');
    re?.addEventListener('click', () => { re.innerHTML = W.icon('check') + 'You’re back on the list'; re.setAttribute('aria-disabled', 'true'); W.track('email_resubscribed'); });
    const f = $('unsub-form');
    f?.addEventListener('submit', (e) => {
      e.preventDefault(); const em = $('unsub-email'), fld = $('f-unsub');
      if (!W.EMAIL_RE.test(em.value.trim())) { W.setInvalid(fld, 'Enter the email address the message was sent to.'); em.focus(); return; }
      W.setInvalid(fld, null); W.track('email_unsubscribed', { via: 'form' }); location.hash = '#S-WEB-09/unsubscribed';
    });
    document.addEventListener('dpw:state', (e) => { if (e.detail.state === 'unsubscribed') W.track('email_unsubscribed', { via: 'link' }); });
  });

  /* ---------- S-WEB-10 Press kit: Story preview loop ---------- */
  on('S-WEB-10', () => {
    const M = window.DPW_MOCK; if (!M) return;
    const host = $('press-story');
    if (host) {
      const frames = M.storyFrames();
      host.innerHTML = frames.slice(1, 5).map((f) => `<div class="stack__card press-frame" style="position:relative;left:auto;top:auto;margin:0;--w:150px">${f.html}</div>`).join('');
      host.querySelectorAll('.ring').forEach((r) => M.animateRing(r));
      host.querySelectorAll('svg.gg').forEach((s) => M.animateGG(s, M.HERO_TRIP));
    }
    const shots = $('press-shots');
    if (shots) shots.innerHTML = M.device('ios', M.screenStoryRing(), { width: 150 }) + M.device('android', M.screenRecap(), { width: 140 }) + M.device('ios', M.screenReplay(), { width: 150 });
  });

  /* ---------- S-WEB-11 404 ---------- */
  on('S-WEB-11', () => {
    const M = window.DPW_MOCK, host = $('lost-route');
    if (!M || !host) return;
    const t = window.DP_DATA.util.trip('t20261010-1015'); // the twisty drive to Belfountain
    const w = host.clientWidth || 1440, h = host.clientHeight || 700;
    const r = M.route(t, w, h, { pad: 0, rotate: 64, box: w >= 960 ? { x: w * 0.45, y: h * 0.02, w: w * 0.6, h: h * 0.96 } : { x: 0, y: h * 0.45, w, h: h * 0.55 } });
    const end = r.xy[Math.floor(r.xy.length * 0.72)];
    const part = M.smoothPath(r.xy.slice(0, Math.floor(r.xy.length * 0.72)));
    host.innerHTML = `<svg viewBox="0 0 ${w} ${h}" aria-hidden="true"><path d="${r.d}" fill="none" stroke="var(--line-strong)" stroke-width="2" stroke-dasharray="2 8" stroke-linecap="round"/><path class="lost-line" d="${part}" fill="none" stroke="var(--accent)" stroke-width="3.5" stroke-linecap="round"/><circle cx="${end[0]}" cy="${end[1]}" r="16" fill="var(--accent)" opacity=".18"/><circle cx="${end[0]}" cy="${end[1]}" r="6" fill="var(--text)"/></svg>`;
    const p = host.querySelector('.lost-line'); if (p) window.DP_MOTION.drawOn(p, { duration: 1800 });
  });
})();
