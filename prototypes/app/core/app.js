/* DrivePerf prototype engine · boot + catalog panel. Loaded last by prototypes/{ios,android}/index.html.
   Builds the device, reads ?bare=1 / #…&bare=1 (screenshot mode: device only, edge to edge, exact CSS size) and
   ?shot=1 (no animations; export uses it), wires the side catalog (all screens by WP, states, theme, XL, RM,
   entitlement, live trip, Health preset). */
(function () {
  const UI = window.DP_UI;
  const ENTS = ['pro', 'proMonthly', 'preview', 'previewEnding', 'rideAlong', 'free'];
  const LIVES = ['', 'manual', 'auto', 'gpsSearching', 'lowBattery', 'passenger', 'cap8h'];
  const HEALTH = ['', ...Object.keys(window.DP_DATA.health.presets).filter((k) => k !== 'allGood')];

  function flag(name) {
    const q = new URLSearchParams(location.search).get(name);
    const h = new URLSearchParams((location.hash.split('?')[1]) || '').get(name);
    return q || h;
  }

  function boot(opts = {}) {
    const platform = opts.platform || document.documentElement.dataset.platform || 'ios';
    const bare = flag('bare') === '1';
    DP.shot = flag('shot') === '1';
    if (DP.shot) document.documentElement.dataset.shot = '1';
    document.body.classList.add('dp-host');
    if (bare) document.body.classList.add('dp-bare');
    document.body.insertAdjacentHTML('afterbegin', `<svg width="0" height="0" style="position:absolute" aria-hidden="true"><defs><clipPath id="dp-cookie12" clipPathUnits="objectBoundingBox"><path d="${UI.cookie(12, 0.5, 0.5, 0.5, 0.06).replace(/(\d+\.\d+)/g, (m) => (+m).toFixed(3))}"/></clipPath></defs></svg>
      <main class="dp-stage"><div class="dp-stage-in"><div class="dp-device" data-platform="${platform}" data-theme="dark"><div class="dp-viewport" id="dp-viewport"></div></div><div class="dp-caption" id="dp-caption"></div></div><div class="dp-haptic" id="dp-haptic"></div></main>
      ${bare ? '' : '<span class="dp-cat-toggle" id="dp-cat-toggle" role="button">Catalog</span><aside class="dp-catalog" id="dp-catalog" aria-label="Screen catalog"></aside>'}`);
    const vp = document.getElementById('dp-viewport');
    DP.router.init(vp, platform);
    if (DP.problems.length) console.warn('[DP] registration problems:\n' + DP.problems.join('\n'));
    DP.router.open(location.hash || '#S-HOME-01');
    if (!bare) { catalog(platform); fit(); window.addEventListener('resize', fit); }
    document.addEventListener('dp:haptic', (e) => {
      if (bare || DP.router.globals.hap === '0') return;
      const h = document.getElementById('dp-haptic'); const s = document.createElement('span');
      s.textContent = `haptic · ${e.detail.name}`; h.appendChild(s); setTimeout(() => s.remove(), 1700);
    });
    document.addEventListener('dp:screen', (e) => { if (!bare) { caption(e.detail); markCurrent(e.detail); syncControls(); } });
    caption(DP.router.stack.length ? { id: DP.router.stack[DP.router.stack.length - 1].id, state: DP.router.stack[DP.router.stack.length - 1].state } : null);
  }

  function fit() {
    const inner = document.querySelector('.dp-stage-in'), dev = document.querySelector('.dp-device');
    const avail = window.innerHeight - 90;
    const s = Math.min(1, avail / dev.offsetHeight);
    inner.style.transform = s < 1 ? `scale(${s.toFixed(3)})` : '';
    inner.style.marginBottom = s < 1 ? `${-(1 - s) * (dev.offsetHeight + 40)}px` : '';
  }

  function caption(d) {
    const el = document.getElementById('dp-caption'); if (!el || !d) return;
    const m = DP.meta(d.id), def = DP.def(d.id);
    const reason = DP.session.gateReason && d.id.startsWith('S-DRV-') ? ` · <code>gate: ${UI.esc(DP.session.gateReason)}</code>` : '';
    el.innerHTML = `<b>${UI.esc(d.id)}</b> · ${UI.esc(def ? def.name : m ? m.name : '')}${d.state && d.state !== 'default' ? ` · <code>${UI.esc(d.state)}</code>` : ''}${def ? '' : ' · <code>placeholder</code>'}${reason}<br><code>${UI.esc(location.hash)}</code>`;
  }

  // ---------------- catalog ----------------
  function globalsQuery(extra = {}) {
    const g = { ...DP.router.globals, ...extra };
    delete g.bare; delete g.shot;
    return UI.qs(g);
  }
  function go(target) { const q = globalsQuery(); location.hash = '#' + target + (q ? (target.includes('?') ? '&' : '?') + q : ''); }

  function catalog(platform) {
    const el = document.getElementById('dp-catalog');
    const M = DP.manifest, cov = DP.coverage(platform);
    const other = platform === 'ios' ? 'android' : 'ios';
    const wps = Object.entries(M.wps).map(([k, w]) => {
      const items = w.screens.map((id) => DP.meta(id)).filter((m) => m.platforms.includes(platform)).map((m) => {
        const def = DP.def(m.id);
        const states = DP.statesOf(m.id);
        return `<div class="dp-cat-s ${def ? '' : 'is-ph'}" data-id="${m.id}" data-q="${UI.esc((m.id + ' ' + m.name).toLowerCase())}"><a href="#${m.id}" data-go="${m.id}"><code>${m.id}</code><span>${UI.esc(def ? def.name : m.name)}</span><em>${def ? m.priority : 'stub'}</em></a><div class="dp-cat-st">${states.map((s) => `<a href="#${m.id}/${s.id}" data-go="${m.id}${s.id === 'default' ? '' : '/' + s.id}" title="${UI.esc(s.label)}">${UI.esc(s.id)}</a>`).join('')}${(def ? def.variants : m.variants).includes('xl') ? `<a href="#${m.id}?text=xl" data-go="${m.id}" data-xl title="Largest text">XL</a>` : ''}</div></div>`;
      }).join('');
      return `<section class="dp-cat-wp"><h4>WP-${k} · ${UI.esc(w.name)}</h4>${items}</section>`;
    }).join('');
    el.innerHTML = `<div class="dp-cat-h"><b>DrivePerf · ${platform === 'ios' ? 'iOS 26 Liquid Glass' : 'Android 16 M3 Expressive'}</b><a href="../${other}/index.html${location.hash}" id="dp-other">${other === 'ios' ? 'iOS' : 'Android'} →</a></div>
      <div class="dp-cat-ctl">
        <label><span>Theme</span><span class="dp-cat-seg" data-g="theme"><button data-v="">Dark</button><button data-v="light">Light</button></span></label>
        <label><span>Text size</span><span class="dp-cat-seg" data-g="text"><button data-v="">Default</button><button data-v="l">L</button><button data-v="xl">XL</button></span></label>
        <label><span>Reduced motion</span><span class="dp-cat-seg" data-g="rm"><button data-v="">Off</button><button data-v="1">On</button></span></label>
        <label><span>Reduce transparency</span><span class="dp-cat-seg" data-g="rt"><button data-v="">Off</button><button data-v="1">On</button></span></label>
        <label><span>Entitlement</span><select data-g="ent">${ENTS.map((e) => `<option value="${e === 'pro' ? '' : e}">${e}</option>`).join('')}</select></label>
        <label><span>Trip in progress (D1 gate)</span><select data-g="live">${LIVES.map((e) => `<option value="${e}">${e || 'none'}</option>`).join('')}</select></label>
        <label><span>Parked? (held while moving)</span><span class="dp-cat-seg" data-g="moving"><button data-v="">Parked</button><button data-v="1">Moving</button></span></label>
        <label><span>Health Check preset</span><select data-g="health">${HEALTH.map((e) => `<option value="${e}">${e || 'allGood'}</option>`).join('')}</select></label>
        <label><span>Find</span><input type="search" id="dp-cat-find" placeholder="S-TRP, replay, …"></label>
      </div>
      <div class="dp-cat-cov">Built <b>${cov.built}</b> of ${cov.total} ${platform} screens · ${cov.total - cov.built} placeholders · <a href="../index.html" style="color:#5BE7C4">overview</a></div>${wps}`;
    el.addEventListener('click', (e) => {
      const a = e.target.closest('[data-go]');
      if (a) { e.preventDefault(); if (a.hasAttribute('data-xl')) DP.router.globals.text = 'xl'; go(a.dataset.go); return; }
      const b = e.target.closest('.dp-cat-seg button');
      if (b) { DP.router.setGlobal(b.parentElement.dataset.g, b.dataset.v); syncControls(); caption(currentDetail()); }
    });
    el.addEventListener('change', (e) => { const s = e.target.closest('select[data-g]'); if (s) { if (s.dataset.g === 'live' || s.dataset.g === 'moving') { const g = { ...DP.router.globals, [s.dataset.g]: s.value }; const top = DP.router.stack[DP.router.stack.length - 1]; const q = UI.qs(Object.fromEntries(Object.entries({ ...g, ...(top ? top.params : {}) }).filter(([k]) => k !== 'bare'))); location.hash = '#' + (DP.session.pending && !s.value ? DP.session.pending.id : top ? top.id : 'S-HOME-01') + (q ? '?' + q : ''); } else DP.router.setGlobal(s.dataset.g, s.value); syncControls(); } });
    document.getElementById('dp-cat-find').addEventListener('input', (e) => { const q = e.target.value.toLowerCase().trim(); el.querySelectorAll('.dp-cat-s').forEach((s) => (s.hidden = q && !s.dataset.q.includes(q))); });
    document.getElementById('dp-cat-toggle').addEventListener('click', () => document.body.classList.toggle('dp-cat-off'));
    window.addEventListener('keydown', (e) => { if (e.key === 'c' && !e.target.closest('input,[contenteditable="true"]')) document.body.classList.toggle('dp-cat-off'); });
    syncControls();
  }
  const currentDetail = () => { const t = DP.router.stack[DP.router.stack.length - 1]; return t ? { id: t.id, state: t.state } : null; };
  function syncControls() {
    const el = document.getElementById('dp-catalog'); if (!el) return;
    const g = DP.router.globals;
    el.querySelectorAll('.dp-cat-seg').forEach((s) => s.querySelectorAll('button').forEach((b) => b.classList.toggle('on', (g[s.dataset.g] || '') === b.dataset.v)));
    el.querySelectorAll('select[data-g]').forEach((s) => (s.value = g[s.dataset.g] || ''));
    const o = document.getElementById('dp-other'); if (o) o.href = o.href.split('#')[0] + location.hash;
  }
  function markCurrent(d) {
    document.querySelectorAll('.dp-cat-s').forEach((s) => s.classList.toggle('is-cur', d && s.dataset.id === d.id));
    document.querySelectorAll('.dp-cat-st a').forEach((a) => a.classList.toggle('on', d && a.dataset.go === d.id + (d.state === 'default' ? '' : '/' + d.state) && !a.hasAttribute('data-xl')));
  }

  window.DP.boot = boot;
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => boot()); else boot();
})();
