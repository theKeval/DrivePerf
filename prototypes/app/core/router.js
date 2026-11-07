/* DrivePerf prototype engine · router: deep links (screen-map §2.3), back stack, tabs, layered presentations
   (root · push · sheet · full · dialog · sys · live), transitions (motion.md §3.10), the ctx object and delegated
   interactions. Contract: prototypes/app/AUTHORING.md.
   Hash: #<S-ID>[/<state>][?trip=…&route=…&car=…&ent=…&health=…&live=…&theme=…&text=xl&rm=1&rt=1&bare=1] */
(function () {
  const UI = window.DP_UI, M = window.DP_MOTION, D = window.DP_DATA;
  const GLOBAL_KEYS = ['theme', 'text', 'rm', 'rt', 'ent', 'live', 'health', 'moving', 'capture', 'bare', 'shot', 'hap'];
  const OVERLAY = ['sheet', 'dialog', 'sys'];
  const ROOT_OF = Object.fromEntries(UI.TABS.map(([t, , id]) => [t, id]));
  const R = { stack: [], globals: {}, platform: 'ios', busy: [], errors: [] };
  let vp, uidN = 0;
  const uid = () => 'e' + (++uidN);

  // ---------------- hash ----------------
  function parseTarget(target) {
    const [path, query] = String(target || '').replace(/^#/, '').split('?');
    const [id, state] = path.split('/');
    const params = {};
    new URLSearchParams(query || '').forEach((v, k) => (params[k] = v));
    return { id: id || 'S-HOME-01', state: state || 'default', params };
  }
  function splitGlobals(params) {
    const g = {}, p = {};
    for (const [k, v] of Object.entries(params)) (GLOBAL_KEYS.includes(k) ? g : p)[k] = v;
    return { g, p };
  }
  function hashOf(entry) {
    const q = { ...entry.params };
    for (const k of GLOBAL_KEYS) if (R.globals[k] != null && R.globals[k] !== '') q[k] = R.globals[k];
    const s = UI.qs(q);
    return `#${entry.id}${entry.state && entry.state !== 'default' ? '/' + entry.state : ''}${s ? '?' + s : ''}`;
  }

  // ---------------- ctx ----------------
  const presOf = (id) => DP.presOf(id, R.platform);
  const isOverlay = (id) => OVERLAY.includes(presOf(id));
  function makeCtx(entry) {
    const def = DP.def(entry.id), meta = DP.meta(entry.id);
    const sp = (def && def.stateParams[entry.state]) || {};
    const params = { ...R.globals, ...sp, ...entry.params };
    const ent = params.ent || D.entitlement.current;
    const entState = D.entitlement.states[ent] || D.entitlement.states[D.entitlement.current];
    const ctx = {
      id: entry.id, state: entry.state, entry, def, meta, params,
      name: def ? def.name : meta ? meta.name : entry.id,
      platform: R.platform, ios: R.platform === 'ios', android: R.platform === 'android',
      theme: params.theme === 'light' ? 'light' : 'dark', textSize: params.text || 'm', xl: params.text === 'xl', rm: params.rm === '1' || M.reduced(vp), shot: !!DP.shot,
      ent, entState, isPro: !!entState.isPro,
      pres: presOf(entry.id), tab: (def ? def.tab : meta && meta.tab) || null,
      ui: UI, icons: window.DP_ICONS, motion: M, data: DP.data, D,
      size: DP.shell.SIZE[R.platform], store: entry.store,
      nav: (t, p, o) => nav(t, p, o), back: () => back(), goTab: (t) => tab(t), openSheet: (id, state, p) => nav(state ? `${id}/${state}` : id, p),
      setState: (s) => setState(s), update: () => refresh(entry), toast: (t, o) => DP.shell.toast(t, o), haptic: (n) => M.haptic(n, { el: vp }),
      setGlobal: (k, v) => setGlobal(k, v),
    };
    ctx.capture = DP.shell.capture(params);
    ctx.tabRoot = ROOT_OF[ctx.tab] || 'S-HOME-01';
    return ctx;
  }

  // ---------------- rendering ----------------
  function placeholder(ctx) {
    const m = ctx.meta || { id: ctx.id, name: ctx.id, states: [] };
    const body = `<div class="dp-ph"><span class="dp-ph-k">Not built yet</span><h2>${UI.esc(m.id)} · ${UI.esc(m.name)}</h2>
      <dl>${m.wp ? `<dt>Work package</dt><dd>WP-${m.wp}</dd>` : ''}<dt>Presentation</dt><dd>${UI.esc(m.presentation || ctx.pres)}</dd><dt>States</dt><dd>${(m.states || []).map((s) => `<span class="${s.id === ctx.state ? 'is-on' : ''}">${UI.esc(s.id)}</span>`).join(' ')}</dd>${m.entry ? `<dt>Entry points</dt><dd>${UI.esc(m.entry)}</dd>` : ''}${m.leadsTo ? `<dt>Leads to</dt><dd>${UI.esc(m.leadsTo)}</dd>` : ''}<dt>Platforms</dt><dd>${UI.esc(m.platformNote || '')}</dd></dl></div>`;
    if (OVERLAY.includes(ctx.pres)) return ctx.pres === 'dialog' ? UI.dialog({ title: `${m.id} · ${m.name}`, text: 'Not built yet', actions: [{ text: 'OK', back: true }] }) : `${UI.sheetHeader(m.name)}${body}`;
    if (ctx.pres === 'root') return UI.page(body, { large: { title: m.name, trailing: [UI.avatar()] } });
    return UI.page(body, { nav: { title: m.name, close: ctx.pres === 'full' || ctx.pres === 'live' } });
  }
  function errorCard(ctx, e) {
    return UI.page(`<div class="dp-ph is-error"><span class="dp-ph-k">Render error</span><h2>${UI.esc(ctx.id)} / ${UI.esc(ctx.state)}</h2><pre>${UI.esc(e && (e.stack || e.message || e))}</pre></div>`);
  }
  function fill(layer, entry) {
    if (layer._cleanup) { try { layer._cleanup(); } catch (e) { /* ignore */ } layer._cleanup = null; }
    const ctx = makeCtx(entry);
    layer._ctx = ctx; layer._entry = entry;
    let html;
    try { html = ctx.def ? ctx.def.render(ctx) : placeholder(ctx); }
    catch (e) { R.errors.push({ id: entry.id, state: entry.state, phase: 'render', message: String(e && e.message), stack: e && e.stack }); console.error(e); html = errorCard(ctx, e); }
    if (html instanceof Node) { const w = document.createElement('div'); w.appendChild(html); html = w.innerHTML; }
    const pres = ctx.pres, def = ctx.def;
    layer.dataset.pres = pres; layer.dataset.screen = entry.id; layer.dataset.state = entry.state;
    layer.classList.toggle('is-placeholder', !def);
    if (pres === 'sheet') {
      const detent = (def && (typeof def.detent === 'function' ? def.detent(ctx) : def.detent)) || 'auto';
      layer.innerHTML = `<div class="dp-scrim" ${def?.dismissible === false ? '' : 'data-dismiss'}></div><div class="ui-sheet" data-detent="${ctx.xl && detent !== 'auto' ? 'large' : detent}" role="dialog" aria-modal="true"><div class="ui-grabber" data-grab aria-hidden="true"></div><div class="ui-sheet-body" data-scroll>${html}</div></div>`;
    } else if (pres === 'dialog' || pres === 'sys') {
      layer.innerHTML = `<div class="dp-scrim" ${def?.dismissible === false || pres === 'sys' ? '' : 'data-dismiss'}></div><div class="dp-dialog-wrap">${html}</div>`;
    } else layer.innerHTML = html;
    if (def && def.mount) { try { const c = def.mount(layer, ctx); if (typeof c === 'function') layer._cleanup = c; } catch (e) { R.errors.push({ id: entry.id, state: entry.state, phase: 'mount', message: String(e && e.message), stack: e && e.stack }); console.error(e); } }
    wireScroll(layer, ctx);
    return layer;
  }
  function wireScroll(layer, ctx) {
    const sc = layer.querySelector('[data-scroll]');
    if (!sc) return;
    const lt = layer.querySelector('.ui-lt, .ui-mt');
    let lastY = 0;
    const onScroll = () => {
      const y = sc.scrollTop;
      layer.classList.toggle('is-scrolled', y > (lt ? lt.offsetHeight - 12 : 8));
      if (isTopOwner(layer)) { if (y > lastY + 6 && y > 80) DP.shell.setMinimised(true); else if (y < lastY - 6 || y < 40) DP.shell.setMinimised(!!(layer._chrome && layer._chrome.minimised) && y > 40); }
      lastY = y;
    };
    sc.addEventListener('scroll', onScroll, { passive: true });
    if (layer._entry.store.scrollTop) sc.scrollTop = layer._entry.store.scrollTop;
    requestAnimationFrame(() => { const t = ctx.def && ctx.def.raw.initialScroll; if (t != null) { sc.scrollTop = typeof t === 'function' ? t(ctx) : t; } onScroll(); });
  }
  const isTopOwner = (layer) => { const i = R.stack.indexOf(layer._entry); return i === ownerIndex(); };
  function newLayer(entry, idx) {
    const el = document.createElement('section');
    el.className = 'dp-layer';
    el.style.zIndex = 10 + idx * 10;
    entry.el = el;
    vp.querySelector('.dp-layers').appendChild(el);
    fill(el, entry);
    return el;
  }
  const ownerIndex = () => { for (let i = R.stack.length - 1; i >= 0; i--) if (['root', 'push'].includes(presOf(R.stack[i].id))) return i; return -1; };
  const visibleFrom = () => { for (let i = R.stack.length - 1; i >= 0; i--) if (!isOverlay(R.stack[i].id)) return i; return 0; };

  /** After any change: z-order, visibility, chrome, status bar, receded parent, hash. */
  function settle({ hideBelow = true } = {}) {
    const vf = visibleFrom();
    R.stack.forEach((e, i) => {
      if (!e.el) newLayer(e, i);
      e.el.style.zIndex = 10 + i * 10;
      const vis = i >= vf;
      if (hideBelow) e.el.hidden = !vis;
      e.el.inert = i < R.stack.length - 1;
      e.el.classList.remove('is-receded');
    });
    const top = R.stack[R.stack.length - 1];
    if (top && R.platform === 'ios' && presOf(top.id) === 'sheet' && top.el.querySelector('.ui-sheet[data-detent="large"]')) { const under = R.stack[R.stack.length - 2]; if (under && under.el) under.el.classList.add('is-receded'); }
    updateChrome();
    const topCtx = top && top.el && top.el._ctx;
    const sbDef = topCtx && topCtx.def && (typeof topCtx.def.statusBar === 'function' ? topCtx.def.statusBar(topCtx) : topCtx.def.statusBar);
    const base = R.stack[vf] && R.stack[vf].el && R.stack[vf].el._ctx;
    const baseSb = base && base.def && (typeof base.def.statusBar === 'function' ? base.def.statusBar(base) : base.def.statusBar);
    DP.shell.updateStatusBar(sbDef || baseSb || (topCtx && ['live', 'full'].includes(topCtx.pres) && !topCtx.def ? (R.globals.theme === 'light' ? 'dark' : 'light') : R.globals.theme === 'light' ? 'dark' : 'light'));
    document.documentElement.dataset.screen = top ? top.id : '';
    window.DP_READY = true;
    document.dispatchEvent(new CustomEvent('dp:screen', { detail: top ? { id: top.id, state: top.state, params: top.el._ctx.params } : null }));
    if (window.parent !== window) try { window.parent.postMessage({ dp: 'ready', hash: location.hash, id: top && top.id, state: top && top.state }, '*'); } catch (e) { /* cross-origin */ }
  }
  function updateChrome() {
    const oi = ownerIndex(), vf = visibleFrom();
    const owner = oi >= 0 ? R.stack[oi] : null;
    const ctx = owner && owner.el ? owner.el._ctx : null;
    const c = ctx && oi >= vf - (isOverlay(R.stack[R.stack.length - 1].id) ? 0 : 0) ? DP.shell.resolveChrome(ctx) : { tabBar: false };
    if (owner && owner.el) owner.el._chrome = c;
    if (oi < vf && presOf(R.stack[vf].id) !== 'root' && presOf(R.stack[vf].id) !== 'push') { c.tabBar = false; c.accessory = null; }
    const topCtx = R.stack.length && R.stack[R.stack.length - 1].el && R.stack[R.stack.length - 1].el._ctx;
    const topOwn = topCtx && topCtx !== ctx && topCtx.def && (typeof topCtx.def.chrome === 'function' ? topCtx.def.chrome(topCtx) : topCtx.def.chrome);
    if (topOwn) { if (topOwn.island === false) c.island = false; if (topOwn.statusBar === false) c.statusBar = false; if (topOwn.statusChip) c.statusChip = topOwn.statusChip; }
    DP.shell.updateChrome(c, ctx || topCtx, 15 + Math.max(0, oi) * 10);
  }

  // ---------------- transitions ----------------
  function sp(name) {
    const cs = getComputedStyle(vp);
    const k = name.replace(/[A-Z]/g, (m) => '-' + m.toLowerCase());
    return { easing: cs.getPropertyValue(`--dp-spring-${k}`).trim() || 'cubic-bezier(.2,0,0,1)', duration: parseFloat(cs.getPropertyValue(`--dp-spring-${k}-duration`)) || 420 };
  }
  const reduced = () => R.globals.rm === '1' || M.reduced(vp);
  function run(el, kf, o) {
    if (!el) return Promise.resolve();
    if (DP.shot) return Promise.resolve();
    const a = el.animate(kf, { fill: 'both', ...o });
    R.busy.push(a);
    return a.finished.catch(() => {}).then(() => { a.cancel(); R.busy = R.busy.filter((x) => x !== a); });
  }
  const fade = (el, from = 0, to = 1, d = 220) => run(el, [{ opacity: from }, { opacity: to }], { duration: d, easing: 'ease' });
  function finishAll() { R.busy.slice().forEach((a) => { try { a.finish(); } catch (e) { /* */ } }); R.busy = []; }
  const W = () => vp.clientWidth, H = () => vp.clientHeight;
  function rectIn(el) { const a = el.getBoundingClientRect(), b = vp.getBoundingClientRect(), s = b.width / vp.offsetWidth || 1; return { top: (a.top - b.top) / s, left: (a.left - b.left) / s, right: (a.right - b.left) / s, bottom: (a.bottom - b.top) / s, r: parseFloat(getComputedStyle(el).borderRadius) || 16 }; }
  const clipOf = (r) => `inset(${r.top.toFixed(1)}px ${(W() - r.right).toFixed(1)}px ${(H() - r.bottom).toFixed(1)}px ${r.left.toFixed(1)}px round ${r.r}px)`;

  function animateIn(entry, prev, kind, from) {
    const el = entry.el, p = R.platform, pres = presOf(entry.id);
    if (reduced()) return fade(el);
    if (kind === 'tab') return p === 'ios' ? fade(el, 0, 1, 160) : run(el, [{ opacity: 0, transform: 'scale(.96)' }, { opacity: 1, transform: 'scale(1)' }], { duration: 260, easing: 'cubic-bezier(.05,.7,.1,1)', delay: 60 });
    if (kind === 'state') return fade(el.firstElementChild || el, 0.35, 1, 180);
    if (pres === 'sheet') { const s = sp('sheet'); return Promise.all([fade(el.querySelector('.dp-scrim'), 0, 1, 260), run(el.querySelector('.ui-sheet'), [{ transform: 'translateY(100%)' }, { transform: 'translateY(0)' }], s)]); }
    if (pres === 'dialog' || pres === 'sys') { const s = sp('fast'); return Promise.all([fade(el.querySelector('.dp-scrim'), 0, 1, 200), run(el.querySelector('.dp-dialog-wrap'), p === 'ios' ? [{ opacity: 0, transform: 'scale(1.12)' }, { opacity: 1, transform: 'scale(1)' }] : [{ opacity: 0, transform: 'scale(.9)' }, { opacity: 1, transform: 'scale(1)' }], s)]); }
    if (from) {
      entry.zoomRect = from;
      const s = sp('slow');
      const inner = el.firstElementChild;
      const outPrev = prev && prev.el ? run(prev.el, p === 'ios' ? [{ transform: 'scale(1)', opacity: 1 }, { transform: 'scale(.94)', opacity: 0.55 }] : [{ opacity: 1 }, { opacity: 0 }], { duration: s.duration, easing: s.easing }) : null;
      return Promise.all([run(el, [{ clipPath: clipOf(from) }, { clipPath: `inset(0px 0px 0px 0px round ${p === 'ios' ? 0 : 0}px)` }], s), inner ? run(inner, [{ opacity: p === 'ios' ? 0.2 : 0 }, { opacity: 1, offset: p === 'ios' ? 0.35 : 0.5 }, { opacity: 1 }], { duration: s.duration, easing: 'linear' }) : null, outPrev]);
    }
    if (pres === 'full' || pres === 'live') {
      const s = sp('sheet');
      return p === 'ios' ? run(el, [{ transform: 'translateY(100%)' }, { transform: 'translateY(0)' }], s) : run(el, [{ opacity: 0, transform: 'translateY(8%)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: 420, easing: 'cubic-bezier(.05,.7,.1,1)' });
    }
    const s = sp('default');
    if (p === 'ios') return Promise.all([run(el, [{ transform: `translateX(${W()}px)`, boxShadow: '-20px 0 40px #0000' }, { transform: 'translateX(0)', boxShadow: '-20px 0 40px #0006' }], s), prev && prev.el ? run(prev.el, [{ transform: 'translateX(0)', opacity: 1 }, { transform: `translateX(${-W() * 0.3}px)`, opacity: 0.6 }], s) : null]);
    return Promise.all([run(el, [{ transform: 'translateX(30px)', opacity: 0 }, { transform: 'translateX(0)', opacity: 1 }], s), prev && prev.el ? run(prev.el, [{ transform: 'translateX(0)', opacity: 1 }, { transform: 'translateX(-30px)', opacity: 0 }], s) : null]);
  }
  function animateOut(entry, under) {
    const el = entry.el, p = R.platform, pres = presOf(entry.id);
    if (reduced()) return fade(el, 1, 0);
    if (pres === 'sheet') { const s = sp('sheet'); const sh = el.querySelector('.ui-sheet'); const cur = sh.style.transform; return Promise.all([fade(el.querySelector('.dp-scrim'), 1, 0, 220), run(sh, [{ transform: cur || 'translateY(0)' }, { transform: 'translateY(100%)' }], { ...s, duration: s.duration * 0.8 })]); }
    if (pres === 'dialog' || pres === 'sys') return fade(el, 1, 0, 160);
    if (entry.zoomRect && under && under.el) {
      const s = sp('default');
      return Promise.all([run(el, [{ clipPath: 'inset(0px 0px 0px 0px round 0px)', opacity: 1 }, { clipPath: clipOf(entry.zoomRect), opacity: 0.4 }], s), run(under.el, p === 'ios' ? [{ transform: 'scale(.94)', opacity: 0.55 }, { transform: 'scale(1)', opacity: 1 }] : [{ opacity: 0 }, { opacity: 1 }], s)]);
    }
    if (pres === 'full' || pres === 'live') return run(el, p === 'ios' ? [{ transform: 'translateY(0)' }, { transform: 'translateY(100%)' }] : [{ opacity: 1, transform: 'translateY(0)' }, { opacity: 0, transform: 'translateY(8%)' }], sp('sheet'));
    const s = sp('default');
    if (p === 'ios') return Promise.all([run(el, [{ transform: 'translateX(0)' }, { transform: `translateX(${W()}px)` }], s), under && under.el ? run(under.el, [{ transform: `translateX(${-W() * 0.3}px)`, opacity: 0.6 }, { transform: 'translateX(0)', opacity: 1 }], s) : null]);
    return Promise.all([run(el, [{ transform: 'translateX(0)', opacity: 1 }, { transform: 'translateX(30px)', opacity: 0 }], s), under && under.el ? run(under.el, [{ transform: 'translateX(-30px)', opacity: 0 }, { transform: 'translateX(0)', opacity: 1 }], s) : null]);
  }

  // ---------------- operations ----------------
  function makeEntry(id, state, params) { return { uid: uid(), id, state: DP.resolveState(id, state), params: params || {}, store: {} }; }
  function applyGlobals(g) {
    let changed = false;
    for (const [k, v] of Object.entries(g)) { const nv = v === '' || v == null || v === '0' ? undefined : v; if (R.globals[k] !== nv) { changed = true; if (nv == null) delete R.globals[k]; else R.globals[k] = nv; } }
    if (changed) applyDeviceAttrs();
    return changed;
  }
  function applyDeviceAttrs() {
    const dev = vp.closest('.dp-device') || vp;
    dev.dataset.theme = R.globals.theme === 'light' ? 'light' : 'dark';
    if (R.globals.text) dev.dataset.textSize = R.globals.text; else delete dev.dataset.textSize;
    dev.dataset.reducedMotion = R.globals.rm === '1' ? 'true' : 'false';
    if (R.globals.rt === '1') dev.dataset.reducedTransparency = 'true'; else delete dev.dataset.reducedTransparency;
    M.setDriving(!!(R.globals.live && D.liveTrip.states[R.globals.live] && !D.liveTrip.states[R.globals.live].passenger));
  }
  function writeHash(push, entry) {
    const h = hashOf(entry || R.stack[R.stack.length - 1]);
    const st = { dp: (entry || R.stack[R.stack.length - 1]).uid };
    if (push) history.pushState(st, '', h); else history.replaceState(st, '', h);
    R.lastHash = h;
  }
  /** Navigate. target 'S-ID' or 'S-ID/state'; params may include globals (ent, live, theme…). opts {from: Element|rect, replace} */
  function nav(target, params = {}, opts = {}) {
    finishAll();
    const t = parseTarget(target);
    const { g, p } = splitGlobals({ ...t.params, ...params });
    const gChanged = applyGlobals(g);
    const res = DP.gate.resolve({ id: t.id, state: t.state, params: { ...R.globals, ...p } }, R.platform);
    if (res.redirected) { DP.session.pending = res.pending; DP.session.gateReason = res.reason; }
    const entryParams = res.redirected ? {} : p;
    const entry = makeEntry(res.id, res.state, entryParams);
    const pres = presOf(entry.id);
    const prevTop = R.stack[R.stack.length - 1];
    if (gChanged) R.stack.forEach((e) => e.el && fill(e.el, e));
    if (pres === 'root' && !OVERLAY.includes(pres)) return tab(DP.def(entry.id)?.tab || DP.meta(entry.id)?.tab || 'drives', entry);
    if (prevTop && prevTop.id === entry.id && JSON.stringify(prevTop.params) === JSON.stringify(entry.params) && !opts.push) return setState(entry.state);
    if (opts.replace && prevTop) { R.stack.pop(); prevTop.el && prevTop.el.remove(); }
    R.stack.push(entry);
    const from = opts.from ? (opts.from.getBoundingClientRect ? rectIn(opts.from) : opts.from) : null;
    newLayer(entry, R.stack.length - 1);
    updateChrome();
    writeHash(!opts.replace, entry);
    const under = R.stack[R.stack.length - 2];
    settle({ hideBelow: false });
    const fromOk = from && pres === 'push' || from && pres === 'full';
    return animateIn(entry, isOverlay(entry.id) ? null : under, null, fromOk ? from : null).then(() => settle());
  }
  function back() {
    finishAll();
    const top = R.stack[R.stack.length - 1];
    if (!top) return;
    if (R.stack.length === 1) {
      const def = DP.def(top.id), meta = DP.meta(top.id);
      const pres = presOf(top.id);
      if (pres === 'root') return;
      const fb = (def && def.backTo) || (pres === 'push' ? (ROOT_OF[(def && def.tab) || (meta && meta.tab)] || 'S-HOME-01') : (meta && meta.entryIds && meta.entryIds[0]) || 'S-HOME-01');
      const pe = makeEntry(parseTarget(fb).id, parseTarget(fb).state, {});
      R.stack.unshift(pe); newLayer(pe, 0); pe.el.hidden = false;
      R.stack.forEach((e, i) => (e.el.style.zIndex = 10 + i * 10));
    }
    const under = R.stack[R.stack.length - 2];
    if (under.el) { under.el.hidden = false; under.el.inert = false; }
    R.stack.pop();
    updateChrome();
    writeHash(false, under);
    return animateOut(top, isOverlay(top.id) ? null : under).then(() => { top.el.remove(); settle(); });
  }
  function tab(name, entry) {
    finishAll();
    const rootId = ROOT_OF[name] || 'S-HOME-01';
    const top = R.stack[R.stack.length - 1];
    if (!entry && R.stack.length === 1 && top.id === rootId) { const sc = top.el.querySelector('[data-scroll]'); if (sc) sc.scrollTo({ top: 0, behavior: reduced() ? 'auto' : 'smooth' }); return; }
    const e = entry || makeEntry(rootId, 'default', {});
    const old = R.stack.slice();
    R.stack = [e];
    newLayer(e, 0);
    old.forEach((o) => o.el && (o.el.style.zIndex = 5));
    DP.shell.setTab(name);
    writeHash(true, e);
    settle({ hideBelow: false });
    return animateIn(e, null, 'tab').then(() => { old.forEach((o) => o.el && o.el.remove()); settle(); });
  }
  function setState(state) {
    const top = R.stack[R.stack.length - 1];
    if (!top) return;
    top.state = DP.resolveState(top.id, state);
    const sc = top.el.querySelector('[data-scroll]'); top.store.scrollTop = 0;
    fill(top.el, top);
    writeHash(false, top);
    settle();
    return animateIn(top, null, 'state');
  }
  function refresh(entry) { const sc = entry.el && entry.el.querySelector('[data-scroll]'); entry.store.scrollTop = sc ? sc.scrollTop : 0; fill(entry.el, entry); settle(); }
  function setGlobal(k, v) { if (applyGlobals({ [k]: v })) { R.stack.forEach((e) => e.el && refresh(e)); writeHash(false); } }

  /** Open from a hash (deep link, catalog link, reload): rebuild the stack, adding parents for overlays. */
  function open(hash, { animate = false } = {}) {
    finishAll();
    const t = parseTarget(hash || '#S-HOME-01');
    const { g, p } = splitGlobals(t.params);
    for (const k of GLOBAL_KEYS) if (!(k in g) && !['bare', 'shot', 'hap'].includes(k)) delete R.globals[k];
    applyGlobals(g); applyDeviceAttrs();
    const res = DP.gate.resolve({ id: DP.meta(t.id) || DP.def(t.id) ? t.id : 'S-HOME-01', state: t.state, params: { ...R.globals, ...p } }, R.platform);
    if (res.redirected) { DP.session.pending = res.pending; DP.session.gateReason = res.reason; }
    const entry = makeEntry(res.id, res.state, res.redirected ? {} : p);
    const chain = [entry];
    for (let guard = 0; guard < 4 && isOverlay(chain[0].id); guard++) {
      const def = DP.def(chain[0].id), meta = DP.meta(chain[0].id);
      let over = def && def.over;
      if (typeof over === 'function') over = over(makeCtx(chain[0]));
      over = over || (meta && meta.entryIds && meta.entryIds.find((x) => DP.meta(x) && DP.meta(x).platforms.includes(R.platform))) || 'S-HOME-01';
      const o = typeof over === 'string' ? parseTarget(over) : over;
      const carry = {}; for (const k of ['trip', 'route', 'car', 'event']) if (p[k]) carry[k] = p[k];
      chain.unshift(makeEntry(o.id, o.state || 'default', { ...carry, ...(o.params || {}) }));
    }
    vp.querySelectorAll('.dp-layer').forEach((l) => l.remove());
    R.stack = chain;
    chain.forEach((e, i) => newLayer(e, i));
    writeHash(false, entry);
    settle();
    if (animate) return fade(R.stack[R.stack.length - 1].el, 0.4, 1, 180);
  }

  // ---------------- interactions (delegated) ----------------
  function onClick(ev) {
    const layer = ev.target.closest('.dp-layer');
    const ctx = layer && layer._ctx;
    const startBtn = ev.target.closest('[data-start-drive]');
    if (startBtn) { ev.stopPropagation(); M.haptic('confirm', { el: vp }); return nav('S-DRV-01', { live: 'manual' }, { from: ev.target.closest('.dp-acc') || startBtn }); }
    const shellAct = ev.target.closest('[data-action-shell]');
    if (shellAct) { ev.stopPropagation(); if (shellAct.dataset.actionShell === 'resume') { setGlobal('capture', ''); setGlobal('health', ''); DP.shell.toast('Auto-detect resumed'); } return; }
    const mt = ev.target.closest('[data-menu]');
    const openMenus = vp.querySelectorAll('.ui-menu:not([hidden])');
    if (mt && layer) { const m = layer.querySelector('#' + CSS.escape(mt.dataset.menu)); if (m) { const show = m.hidden; openMenus.forEach((x) => (x.hidden = true)); m.hidden = !show; if (show && !reduced() && !DP.shot) m.animate([{ opacity: 0, transform: 'scale(.9)' }, { opacity: 1, transform: 'scale(1)' }], { ...sp('fast') }); M.haptic('tap', { el: vp }); } return; }
    openMenus.forEach((x) => (x.hidden = true));
    const el = ev.target.closest('[data-action],[data-nav],[data-back],[data-tab],[data-set-state],[data-toast],[data-dismiss],[data-haptic]');
    if (!el || el.getAttribute('aria-disabled') === 'true') return;
    const d = el.dataset;
    if (d.haptic) M.haptic(d.haptic, { el });
    if (d.action != null && ctx && ctx.def && ctx.def.actions[d.action]) {
      const r = ctx.def.actions[d.action](ctx, d.arg, el, ev);
      if (r === false || !(d.nav || d.back != null || d.tab || d.setState)) return;
    } else if (el.matches('[role="switch"]')) { el.classList.toggle('is-on'); el.setAttribute('aria-checked', el.classList.contains('is-on')); return; }
    if (d.toast) DP.shell.toast(d.toast);
    if (d.dismiss != null) return back();
    if (d.back != null) return back();
    if (d.tab) return tab(d.tab);
    if (d.setState) return setState(d.setState);
    if (d.nav) { const params = {}; new URLSearchParams(d.params || '').forEach((v, k) => (params[k] = v)); return nav(d.nav, params, { from: d.zoom != null ? el : null }); }
  }
  function onKey(ev) { if ((ev.key === 'Enter' || ev.key === ' ') && ev.target.matches('[role="button"],[role="switch"],[role="tab"]')) { ev.preventDefault(); ev.target.click(); } }
  /** Sheet drag-to-dismiss (grabber + header area), spring back otherwise. */
  function onPointerDown(ev) {
    const grab = ev.target.closest('.ui-grabber, .ui-sheet-h');
    const sheet = grab && grab.closest('.ui-sheet');
    if (!sheet) return;
    const layer = sheet.closest('.dp-layer'); if (layer._ctx.def && layer._ctx.def.dismissible === false) return;
    const y0 = ev.clientY, t0 = performance.now(); let dy = 0;
    const scale = vp.getBoundingClientRect().height / vp.offsetHeight || 1;
    const move = (e) => { dy = Math.max(0, (e.clientY - y0) / scale); sheet.style.transform = `translateY(${dy}px)`; };
    const up = () => {
      window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up);
      const v = dy / Math.max(1, performance.now() - t0);
      if (dy > 120 || v > 0.6) { M.haptic('gestureEnd', { el: vp }); back(); }
      else { const s = sp('sheet'); sheet.animate([{ transform: `translateY(${dy}px)` }, { transform: 'translateY(0)' }], s); sheet.style.transform = ''; }
    };
    window.addEventListener('pointermove', move); window.addEventListener('pointerup', up);
  }

  function init(viewport, platform) {
    vp = viewport; R.platform = platform; DP.platform = platform;
    vp.insertAdjacentHTML('afterbegin', '<div class="dp-layers"></div>');
    DP.shell.build(vp, platform);
    vp.addEventListener('click', onClick);
    vp.addEventListener('keydown', onKey);
    vp.addEventListener('pointerdown', onPointerDown);
    window.addEventListener('popstate', (e) => {
      const st = e.state && e.state.dp;
      if (!st) return;
      const i = R.stack.findIndex((x) => x.uid === st);
      R.popHandled = location.hash;
      if (i >= 0 && i === R.stack.length - 2) back();
      else if (i < 0) open(location.hash);
    });
    window.addEventListener('hashchange', () => { if (R.popHandled === location.hash || R.lastHash === location.hash) { R.popHandled = null; return; } open(location.hash, { animate: true }); });
  }

  window.DP = window.DP || {};
  DP.session = DP.session || {};
  DP.router = { init, open, nav, back, tab, setState, setGlobal, refresh, makeCtx, parseTarget, hashOf, GLOBAL_KEYS, state: R, get globals() { return R.globals; }, get stack() { return R.stack; } };
})();
