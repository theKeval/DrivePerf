/* driveperf.com prototype — shared runtime (DRIVE-132 · DES-31 · A9).
   One file, no dependencies, works from file://. Astro port: each render* function below is a component
   (see README.md → Components); the state router and prototype panel are prototype-only.

   URL conventions (screen-map.md §2.3, adapted for web):
     <page>.html#<S-WEB-ID>[/<state>]     e.g. index.html#S-WEB-01/variantB, pages/delete-account.html#S-WEB-07/emailSent
     index.html#S-WEB-07/verified         any S-WEB ID on index.html forwards to its page
     ?theme=light   Day Drive theme       ?rm=1   force reduced motion       ?shot=1   static, final-state render for screenshots
*/
(function () {
  'use strict';
  const doc = document, html = doc.documentElement;
  const SCRIPT = doc.currentScript;
  const ROOT = new URL('..', SCRIPT.src).href;           // prototypes/web/
  const q = new URLSearchParams(location.search);

  /* ---------- Screens and states (PRD §4 rows S-WEB-01 … 11) ---------- */
  const SCREENS = {
    'S-WEB-01': { path: 'index.html', title: 'Home', states: ['default', 'variantA', 'variantB', 'formErr', 'submitting', 'duplicate', 'networkErr'] },
    'S-WEB-02': { path: 'pages/waitlist-confirmed.html', title: 'Waitlist confirmation', states: ['default', 'android'] },
    'S-WEB-03': { path: 'pages/privacy.html', title: 'Privacy policy', states: ['default'] },
    'S-WEB-04': { path: 'pages/terms.html', title: 'Terms of use', states: ['default'] },
    'S-WEB-05': { path: 'pages/support.html', title: 'Support and FAQ', states: ['default'] },
    'S-WEB-06': { path: 'pages/how-it-records.html', title: 'How DrivePerf records', states: ['default', 'ios', 'android'] },
    'S-WEB-07': { path: 'pages/delete-account.html', title: 'Account deletion request', states: ['form', 'formErr', 'emailSent', 'verified'] },
    'S-WEB-08': { path: 'pages/ride-along.html', title: 'Ride-along landing', states: ['default', 'mobile', 'desktop', 'invalid'] },
    'S-WEB-09': { path: 'pages/email-preferences.html', title: 'Email preferences / unsubscribe', states: ['unsubscribed', 'err'] },
    'S-WEB-10': { path: 'pages/press.html', title: 'Press kit', states: ['default'] },
    'S-WEB-11': { path: '404.html', title: '404', states: ['default'] },
  };
  const url = (p) => ROOT + p;

  /* ---------- Environment flags ---------- */
  const shot = q.get('shot') === '1';
  if (shot) html.dataset.shot = '1';
  const storedTheme = (() => { try { return sessionStorage.getItem('dpw-theme'); } catch (e) { return null; } })();
  html.dataset.theme = q.get('theme') || storedTheme || 'dark';
  if (q.get('rm') === '1') html.dataset.rm = 'true';
  const reduced = () => html.dataset.rm === 'true' || shot || matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ---------- Icons (24 grid, stroke 1.8). Brand-meaningful glyphs come from DP_ICONS when loaded. ---------- */
  const I = {
    menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
    close: '<path d="M6 6l12 12M18 6L6 18"/>',
    check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    shield: '<path d="M12 3l7.5 3v5.5c0 4.6-3.2 8.2-7.5 9.5-4.3-1.3-7.5-4.9-7.5-9.5V6z"/><path d="M9 12l2.2 2.2L15.5 10"/>',
    phone: '<rect x="6.5" y="2.5" width="11" height="19" rx="3"/><path d="M10.5 18.5h3"/>',
    cloud: '<path d="M7 18.5h10a4 4 0 0 0 .6-7.95A5.5 5.5 0 0 0 7.1 9.3 4.6 4.6 0 0 0 7 18.5z"/>',
    download: '<path d="M12 4v11M7.5 10.5L12 15l4.5-4.5"/><path d="M5 19.5h14"/>',
    trash: '<path d="M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13"/>',
    noentry: '<circle cx="12" cy="12" r="8.5"/><path d="M6 6l12 12"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5.5"/><circle cx="12" cy="7.8" r="1" fill="currentColor" stroke="none"/>',
    alert: '<path d="M12 3.5l9.5 16.5h-19z"/><path d="M12 10v4.5"/><circle cx="12" cy="17.2" r="1" fill="currentColor" stroke="none"/>',
    mail: '<rect x="3" y="5.5" width="18" height="13" rx="2.5"/><path d="M3.5 7l8.5 6 8.5-6"/>',
    store: '<rect x="5" y="2.5" width="14" height="19" rx="3.5"/><path d="M9 6.5h6" opacity=".5"/><path d="M12 10.5v6M9.5 14l2.5 2.5 2.5-2.5"/>',
    play: '<path d="M6 4.5l12.5 7.5L6 19.5z"/>',
    battery: '<rect x="2.5" y="7.5" width="17" height="9" rx="2.5"/><path d="M21.5 10.5v3"/><path d="M5.5 10.5h5v3h-5z" fill="currentColor" stroke="none"/>',
    route: '<path d="M6 19c0-4 3-5 6-6s6-2 6-6"/><circle cx="6" cy="19" r="2"/><circle cx="18" cy="7" r="2"/>',
    sparkle: '<path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5L18 18M6 18l2.5-2.5M15.5 8.5L18 6"/>',
    eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>',
    lock: '<rect x="5" y="10.5" width="14" height="10" rx="2.5"/><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/>',
    user: '<circle cx="12" cy="8.5" r="3.5"/><path d="M5 20c1.2-3.6 3.9-5.5 7-5.5s5.8 1.9 7 5.5"/>',
    gift: '<rect x="3.5" y="9" width="17" height="11.5" rx="2"/><path d="M12 9v11.5M3.5 13h17"/><path d="M12 9c-1.5-3-5-4-5.5-2S9 9 12 9zm0 0c1.5-3 5-4 5.5-2S15 9 12 9z"/>',
    qr: '<rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><path d="M14 14h2v2h-2zM18 18h2v2h-2zM14 18h2M18 14h2"/>',
    rosette: '<circle cx="12" cy="9.5" r="5.5"/><path d="M8.6 13.8L7 21l5-2.6 5 2.6-1.6-7.2"/>',
    ring: '<circle cx="12" cy="12" r="8.5" opacity=".35"/><path d="M12 3.5a8.5 8.5 0 1 1-7.9 5.4"/>',
    replay: '<path d="M4 12a8 8 0 1 0 2.3-5.6"/><path d="M4 4.5v4h4"/><path d="M10.5 9v6l4.5-3z" fill="currentColor" stroke="none"/>',
    calendar: '<rect x="3.5" y="5" width="17" height="15.5" rx="2.5"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
    car: '<path d="M3.5 16.5v-3.2l2-4.6A2 2 0 0 1 7.3 7.5h9.4a2 2 0 0 1 1.8 1.2l2 4.6v3.2"/><path d="M2.5 16.5h19"/><circle cx="7" cy="16.8" r="1.8"/><circle cx="17" cy="16.8" r="1.8"/>',
    globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3z"/>',
    gauge0: '<path d="M4 17a8 8 0 1 1 16 0"/><path d="M8 17h8"/>',
    bolt: '<path d="M13 2.5L5.5 13.5H11l-1 8 7.5-11H12z"/>',
    pause: '<rect x="6.5" y="5" width="3.5" height="14" rx="1"/><rect x="14" y="5" width="3.5" height="14" rx="1"/>',
    passenger: '<circle cx="12" cy="6.5" r="2.5"/><path d="M8 21v-6.5a4 4 0 0 1 8 0V21"/><path d="M5 13h3M16 13h3"/>',
    file: '<path d="M6.5 2.5h7l4 4v15h-11z"/><path d="M13.5 2.5v4h4"/>',
    copy: '<rect x="8" y="8" width="12" height="12" rx="2.5"/><path d="M16 8V5.5A1.5 1.5 0 0 0 14.5 4h-9A1.5 1.5 0 0 0 4 5.5v9A1.5 1.5 0 0 0 5.5 16H8"/>',
    arrowUpRight: '<path d="M7 17L17 7M9 7h8v8"/>',
    brake: '<path d="M6.5 6.5l5.5 6 5.5-6"/><path d="M6 17.5h12" stroke-width="2.6"/>',
  };
  const icon = (name, attrs = '') => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" ${attrs}>${I[name] || ''}</svg>`;

  /* ---------- The Loop mark (brand.md §3.1: 270° fading trail + round head + G-dot 34° ahead) ---------- */
  let loopN = 0;
  function loop({ size = 34, color = 'var(--accent)', dot = 'var(--text)', title = '', glow = false } = {}) {
    const cx = 32, cy = 32, r = 18, sw = 7, a0 = -348, a1 = -78, n = 6, id = 'dpl' + (loopN++);
    const P = (a) => [cx + r * Math.cos(a * Math.PI / 180), cy + r * Math.sin(a * Math.PI / 180)];
    let defs = '', arcs = '';
    for (let i = 0; i < n; i++) {
      const s = a0 + (a1 - a0) * i / n, e = a0 + (a1 - a0) * (i + 1) / n;
      const u0 = i / n, u1 = (i + 1) / n, o0 = 0.03 + 0.97 * u0 ** 1.5, o1 = 0.03 + 0.97 * u1 ** 1.5;
      const [x0, y0] = P(s), [x1, y1] = P(e);
      defs += `<linearGradient id="${id}g${i}" gradientUnits="userSpaceOnUse" x1="${x0.toFixed(2)}" y1="${y0.toFixed(2)}" x2="${x1.toFixed(2)}" y2="${y1.toFixed(2)}"><stop offset="0" stop-color="${color}" stop-opacity="${o0.toFixed(3)}"/><stop offset="1" stop-color="${color}" stop-opacity="${o1.toFixed(3)}"/></linearGradient>`;
      const [ex, ey] = P(e + (i < n - 1 ? 0.6 : 0));
      arcs += `<path d="M${x0.toFixed(2)} ${y0.toFixed(2)} A${r} ${r} 0 0 1 ${ex.toFixed(2)} ${ey.toFixed(2)}" stroke="url(#${id}g${i})" stroke-width="${sw}" fill="none"/>`;
    }
    const [hx, hy] = P(a1), [dx, dy] = P(a1 + 34);
    const f = glow ? `<filter id="${id}f" x="-40%" y="-40%" width="180%" height="180%"><feGaussianBlur stdDeviation="2.2" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>` : '';
    return `<svg viewBox="0 0 64 64" width="${size}" height="${size}" ${title ? `role="img" aria-label="${title}"` : 'aria-hidden="true"'}><defs>${defs}${f}</defs><g ${glow ? `filter="url(#${id}f)"` : ''}>${arcs}<circle cx="${hx.toFixed(2)}" cy="${hy.toFixed(2)}" r="${sw / 2}" fill="${color}"/><circle cx="${dx.toFixed(2)}" cy="${dy.toFixed(2)}" r="${(sw * 1.22 / 2).toFixed(2)}" fill="${dot}"/></g></svg>`;
  }

  /* ---------- Partials: header, footer, store badges ---------- */
  const NAV = [
    ['How it works', 'index.html#how'],
    ['Pricing', 'index.html#pricing'],
    ['Privacy', 'index.html#privacy'],
    ['Support', 'pages/support.html'],
  ];
  function renderHeader(el) {
    const solid = el.hasAttribute('data-solid');
    el.outerHTML = `
<header class="site-header${solid ? ' is-solid' : ''}" data-component="SiteHeader">
  <div class="wrap site-header__inner">
    <a class="brand" href="${url('index.html')}" aria-label="DrivePerf home">${loop({ size: 34 })}<span class="brand__word">DrivePerf</span></a>
    <nav class="site-nav" aria-label="Main">
      ${NAV.map(([t, h]) => `<a href="${url(h)}">${t}</a>`).join('')}
      <a class="btn btn--sm header-cta" href="${url('index.html#waitlist')}">Join the waitlist</a>
    </nav>
    <button class="menu-btn" type="button" aria-expanded="false" aria-controls="mobile-menu" aria-label="Open menu">${icon('menu')}</button>
  </div>
</header>
<div class="mobile-menu" id="mobile-menu" hidden>
  ${NAV.map(([t, h]) => `<a href="${url(h)}">${t}<span aria-hidden="true">→</span></a>`).join('')}
  <a href="${url('pages/how-it-records.html')}">How DrivePerf records<span aria-hidden="true">→</span></a>
  <a class="btn btn--lg" href="${url('index.html#waitlist')}">Join the waitlist</a>
</div>`;
    const header = doc.querySelector('.site-header'), btn = header.querySelector('.menu-btn'), menu = doc.getElementById('mobile-menu');
    btn.addEventListener('click', () => {
      const open = btn.getAttribute('aria-expanded') !== 'true';
      btn.setAttribute('aria-expanded', String(open));
      btn.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
      btn.innerHTML = icon(open ? 'close' : 'menu');
      menu.hidden = !open; menu.classList.toggle('is-open', open);
    });
    menu.addEventListener('click', (e) => { if (e.target.closest('a')) btn.click(); });
    doc.addEventListener('keydown', (e) => { if (e.key === 'Escape' && btn.getAttribute('aria-expanded') === 'true') { btn.click(); btn.focus(); } });
    const onScroll = () => header.classList.toggle('is-scrolled', scrollY > 8);
    addEventListener('scroll', onScroll, { passive: true }); onScroll();
  }

  function storeBadges({ compact = false } = {}) {
    // Neutral placeholders: no Apple or Google marks until the listings exist (store badge guidelines).
    return `<div class="badges" data-component="StoreBadges">
      <span class="store-badge" role="img" aria-label="Coming to the App Store">${icon('store')}<span><small>Coming to the</small><b>App Store</b></span></span>
      <span class="store-badge" role="img" aria-label="Coming to Google Play">${icon('phone')}<span><small>Coming to</small><b>Google Play</b></span></span>
    </div>`;
  }

  function renderFooter(el) {
    el.outerHTML = `
<footer class="site-footer" data-component="SiteFooter">
  <div class="wrap">
    <div class="footer-grid">
      <div class="footer-brand">
        <a class="brand" href="${url('index.html')}" aria-label="DrivePerf home">${loop({ size: 36 })}<span class="brand__word">DrivePerf</span></a>
        <p class="footer-tagline">Smooth is a skill.</p>
        <p>Automatic drive recording, a Smoothness score against your own baseline, and recaps worth sharing. For iPhone and Android in Canada and the US.</p>
        ${storeBadges()}
      </div>
      <div class="footer-col"><h2>Product</h2><ul>
        <li><a href="${url('index.html#how')}">How it works</a></li>
        <li><a href="${url('pages/how-it-records.html')}">How DrivePerf records</a></li>
        <li><a href="${url('index.html#pricing')}">Free and Pro</a></li>
        <li><a href="${url('pages/ride-along.html')}">Ride-along pass</a></li>
        <li><a href="${url('pages/press.html')}">Press kit</a></li>
      </ul></div>
      <div class="footer-col"><h2>Help</h2><ul>
        <li><a href="${url('pages/support.html')}">Support and FAQ</a></li>
        <li><a href="${url('pages/delete-account.html')}">Delete your account</a></li>
        <li><a href="${url('pages/email-preferences.html')}">Email preferences</a></li>
        <li><a href="mailto:support@driveperf.com">support@driveperf.com</a></li>
      </ul></div>
      <div class="footer-col"><h2>Legal</h2><ul>
        <li><a href="${url('pages/privacy.html')}">Privacy policy</a></li>
        <li><a href="${url('pages/terms.html')}">Terms of use</a></li>
        <li><a href="${url('pages/privacy.html#rights')}">Your privacy rights</a></li>
      </ul></div>
    </div>
    <div class="footer-bottom">
      <p class="privacy-pill">${icon('shield')}No cookies. No trackers. We honour Global Privacy Control.</p>
      <p>© 2026 DrivePerf · Oakville, Ontario, Canada · For drivers 18+</p>
      <div class="theme-toggle" role="group" aria-label="Theme">
        <button type="button" data-theme-set="dark" aria-pressed="${html.dataset.theme !== 'light'}">Night</button>
        <button type="button" data-theme-set="light" aria-pressed="${html.dataset.theme === 'light'}">Day</button>
      </div>
    </div>
  </div>
</footer>`;
    doc.querySelectorAll('[data-theme-set]').forEach((b) => b.addEventListener('click', () => {
      html.dataset.theme = b.dataset.themeSet;
      try { sessionStorage.setItem('dpw-theme', b.dataset.themeSet); } catch (e) { /* storage unavailable: theme still applies */ }
      doc.querySelectorAll('[data-theme-set]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
    }));
  }

  /* ---------- State router ---------- */
  const screenId = doc.body.dataset.screen;
  function parseHash() {
    const m = /^#(S-WEB-\d\d)(?:\/([A-Za-z0-9]+))?/.exec(location.hash);
    return m ? { id: m[1], state: m[2] || null } : null;
  }
  function applyState() {
    const h = parseHash();
    if (h && h.id !== screenId && SCREENS[h.id]) { location.replace(url(SCREENS[h.id].path) + location.search + location.hash); return false; }
    const states = SCREENS[screenId]?.states || ['default'];
    const st = h && h.state && states.includes(h.state) ? h.state : (h && h.state ? states[0] : null);
    doc.body.dataset.state = st || states[0];
    doc.body.dataset.stateExplicit = st ? '1' : '';
    doc.dispatchEvent(new CustomEvent('dpw:state', { detail: { screen: screenId, state: doc.body.dataset.state, explicit: !!st } }));
    renderProto();
    return true;
  }
  addEventListener('hashchange', () => { if (parseHash()) applyState(); });

  /* Show one [data-state-view] block per state (pages with mutually exclusive views). */
  function bindStateViews() {
    doc.addEventListener('dpw:state', (e) => {
      const st = e.detail.state;
      const views = [...doc.querySelectorAll('[data-state-view]')];
      if (!views.length) return;
      views.forEach((v) => v.classList.toggle('is-on', v.dataset.stateView.split(' ').includes(st)));
    });
  }

  /* ---------- Prototype panel (prototype only; hidden with ?shot=1) ---------- */
  function renderProto() {
    if (shot || !screenId) return;
    let el = doc.querySelector('.proto');
    const wasOpen = el?.classList.contains('is-open');
    if (!el) { el = doc.createElement('div'); el.className = 'proto'; doc.body.appendChild(el); }
    const cur = doc.body.dataset.state;
    const s = SCREENS[screenId];
    const variant = doc.body.dataset.variant && !/^variant/.test(cur) ? ` · variant ${doc.body.dataset.variant}` : '';
    el.innerHTML = `
      <div class="proto__panel" id="proto-panel" role="dialog" aria-label="Prototype states">
        <h2>${screenId} · ${s.title}</h2>
        ${s.states.map((st) => `<a href="#${screenId}/${st}" aria-current="${st === cur}"><span>${st}</span><small>#${screenId}/${st}</small></a>`).join('')}
        <h2>Display</h2>
        <div class="row">
          <button type="button" data-p="theme" aria-pressed="${html.dataset.theme === 'light'}">Day theme</button>
          <button type="button" data-p="rm" aria-pressed="${html.dataset.rm === 'true'}">Reduced motion</button>
        </div>
        <h2>All pages</h2>
        ${Object.entries(SCREENS).map(([id, x]) => `<a href="${url(x.path)}#${id}"><span>${x.title}</span><small>${id}</small></a>`).join('')}
        <p class="proto__note">Prototype controls. Not part of driveperf.com.</p>
      </div>
      <button class="proto__btn" type="button" aria-expanded="${!!wasOpen}" aria-controls="proto-panel"><i></i>Prototype · ${cur}${variant}</button>`;
    el.classList.toggle('is-open', !!wasOpen);
    el.querySelector('.proto__btn').addEventListener('click', () => { const o = !el.classList.contains('is-open'); el.classList.toggle('is-open', o); el.querySelector('.proto__btn').setAttribute('aria-expanded', String(o)); });
    el.querySelector('[data-p="theme"]').addEventListener('click', () => { html.dataset.theme = html.dataset.theme === 'light' ? 'dark' : 'light'; renderProto(); });
    el.querySelector('[data-p="rm"]').addEventListener('click', () => { html.dataset.rm = html.dataset.rm === 'true' ? 'false' : 'true'; location.reload(); });
  }

  /* ---------- Reveal on scroll ---------- */
  function bindReveal() {
    const els = doc.querySelectorAll('[data-reveal]');
    if (reduced() || !('IntersectionObserver' in window)) { els.forEach((e) => e.classList.add('is-in')); return; }
    const io = new IntersectionObserver((entries) => entries.forEach((en) => { if (en.isIntersecting) { en.target.classList.add('is-in'); io.unobserve(en.target); } }), { rootMargin: '0px 0px -8% 0px', threshold: 0.12 });
    els.forEach((e) => io.observe(e));
  }

  /* ---------- Cookie-less analytics stub (F-WEB-01: honour GPC and DNT) ---------- */
  const events = (window.dpwEvents = []);
  function track(name, props = {}) {
    const optedOut = navigator.globalPrivacyControl === true || navigator.doNotTrack === '1';
    const ev = { name, props, screen: screenId, suppressed: optedOut };
    events.push(ev);
    console.info('[analytics' + (optedOut ? ' suppressed: GPC/DNT' : '') + ']', name, props);
  }

  /* ---------- Form helpers (all forms are non-submitting mocks) ---------- */
  const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
  function setInvalid(fieldEl, msg) {
    if (!fieldEl) return;
    const input = fieldEl.querySelector('input:not([type=radio]):not([type=checkbox])') || fieldEl.querySelector('input');
    const err = fieldEl.querySelector('.field-error');
    if (msg) {
      fieldEl.setAttribute('data-invalid', '');
      if (err) err.textContent = msg;
      fieldEl.querySelectorAll('input').forEach((i) => i.setAttribute('aria-invalid', 'true'));
    } else {
      fieldEl.removeAttribute('data-invalid');
      fieldEl.querySelectorAll('input').forEach((i) => i.removeAttribute('aria-invalid'));
    }
    return input;
  }
  function setBusy(btn, busy, label) {
    if (!btn) return;
    if (busy) { btn.dataset.label = btn.dataset.label || btn.innerHTML; btn.setAttribute('aria-disabled', 'true'); btn.innerHTML = `<span class="spinner" aria-hidden="true"></span>${label || 'Working…'}`; }
    else { btn.removeAttribute('aria-disabled'); if (btn.dataset.label) btn.innerHTML = btn.dataset.label; }
  }
  function banner(el, kind, title, body) {
    if (!el) return;
    if (!kind) { el.className = 'form-banner'; el.innerHTML = ''; return; }
    const ic = { info: 'info', error: 'alert', warn: 'alert' }[kind];
    el.className = `form-banner form-banner--${kind} is-shown`;
    el.innerHTML = `${icon(ic)}<div><b>${title}</b>${body || ''}</div>`;
  }

  /* ---------- Boot ---------- */
  function boot() {
    doc.querySelectorAll('[data-partial="header"]').forEach(renderHeader);
    doc.querySelectorAll('[data-partial="footer"]').forEach(renderFooter);
    doc.querySelectorAll('[data-partial="badges"]').forEach((el) => (el.outerHTML = storeBadges()));
    doc.querySelectorAll('[data-icon]').forEach((el) => (el.innerHTML = icon(el.dataset.icon)));
    doc.querySelectorAll('[data-loop]').forEach((el) => (el.innerHTML = loop({ size: +el.dataset.loop || 64, glow: el.hasAttribute('data-glow') })));
    bindStateViews();
    window.DPW.ready.forEach((fn) => fn());
    if (applyState() === false) return;
    bindReveal();
    track('page_view', { path: location.pathname.split('/prototypes/web/')[1] || location.pathname });
  }

  window.DPW = Object.assign(window.DPW || {}, { ROOT, url, SCREENS, icon, loop, storeBadges, reduced, shot, track, setInvalid, setBusy, banner, EMAIL_RE, ready: (window.DPW && window.DPW.ready) || [] });
  window.DPW.onReady = (fn) => window.DPW.ready.push(fn);
  if (doc.readyState === 'loading') doc.addEventListener('DOMContentLoaded', boot); else queueMicrotask(boot);
})();
