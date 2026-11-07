/* WP-A · Onboarding & permissions — screen definitions (DRIVE-32). Contract: prototypes/app/AUTHORING.md.
   Register each screen with DP.screen('S-XXX-NN', {...}). Only the WP-A owner edits this file.

   The flow (screen-map §3.1, §5 WP-A): S-ONB-01 Welcome (full-screen cover, first launch) hosts one onboarding
   NavigationStack. Every later step is a push inside it, so step changes read as native pushes (iOS slide, Android
   shared axis X) instead of a new cover sliding up each time. S-ONB-11 and S-ONB-16 are sheets.
   Flow params carried from step to step (entry params, not globals): auto=on|off · car=gti|mycar · loc=off. */
(function () {
  const { screen } = window.DP;
  const UI = window.DP_UI, I = window.DP_ICONS, D = window.DP_DATA, U = D.util;
  const esc = UI.esc;

  // ======================================================================================================
  // Flow plumbing
  // ======================================================================================================
  const STEPS = {
    ios: ['S-ONB-04', 'S-ONB-05', 'S-ONB-06', 'S-ONB-07', 'S-ONB-08', 'S-ONB-10', 'S-ONB-13', 'S-ONB-14', 'S-ONB-15'],
    android: ['S-ONB-04', 'S-ONB-05', 'S-ONB-06', 'S-ONB-07', 'S-ONB-08', 'S-ONB-09', 'S-ONB-10', 'S-ONB-12', 'S-ONB-13', 'S-ONB-14', 'S-ONB-15'],
  };
  /** Flow params to pass to the next step. */
  const carry = (ctx, extra = {}) => { const p = {}; ['auto', 'car', 'loc'].forEach((k) => { if (ctx.params[k]) p[k] = ctx.params[k]; }); return { ...p, ...extra }; };
  /** Android shows the battery step only when automatic trips are on (F-ONB-07.1). */
  const afterNotif = (ctx) => (ctx.android && ctx.params.auto !== 'off' ? 'S-ONB-12' : 'S-ONB-13');
  /** The screen under the current layer (to tell "inside onboarding" from Home / Health Check callers). */
  const underId = () => { const s = window.DP && DP.router && DP.router.stack; return s && s.length > 1 ? s[s.length - 2].id : null; };
  const inOnb = () => /^S-ONB-/.test(underId() || '');
  /** Close the current sheet, then continue the flow. */
  const thenNav = (ctx, target, params) => Promise.resolve(ctx.back()).then(() => ctx.nav(target, params));
  /** Switch state, then open a sheet once the cross-fade has settled. */
  const stateThenSheet = (ctx, state, sheet) => { ctx.setState(state); setTimeout(() => ctx.openSheet(sheet), ctx.shot ? 0 : 260); return false; };
  const still = (ctx, el) => ctx.shot || ctx.motion.reduced(el);
  /** Per-state scratch values in ctx.store (reset when the catalog switches state). */
  function scratch(ctx, key, init) {
    const s = ctx.store[key];
    if (!s || s.state !== ctx.state) ctx.store[key] = { state: ctx.state, ...init };
    return ctx.store[key];
  }

  // ======================================================================================================
  // Geometry + art (local helpers; candidates for core primitives, see the WP-A report)
  // ======================================================================================================
  const PT = (cx, cy, r, a) => [cx + r * Math.cos((a * Math.PI) / 180), cy + r * Math.sin((a * Math.PI) / 180)];
  const f2 = (n) => n.toFixed(2);
  function arcD(cx, cy, r, a0, a1) {
    const [x0, y0] = PT(cx, cy, r, a0), [x1, y1] = PT(cx, cy, r, a1);
    return `M${f2(x0)} ${f2(y0)}A${r} ${r} 0 ${Math.abs(a1 - a0) > 180 ? 1 : 0} 1 ${f2(x1)} ${f2(y1)}`;
  }
  /** The Loop at any size (brand.md §3.1): 270° trail from −348° to −78°, stroke 0.33 r, fade 0.03 → 1 (f^1.5) in
      chord-aligned gradient sub-arcs, round head, G-dot 1.22 × stroke, 34° ahead. Draw-on mask + dot group for motion. */
  function loopSvg(size, o = {}) {
    const r = 30, c = 50, sw = 0.33 * r, head = -78, span = 270, tail = head - span, N = 12;
    const id = UI.nid('onbl'), col = o.color || 'var(--dp-accent)';
    let defs = '', segs = '';
    for (let i = 0; i < N; i++) {
      const a0 = tail + (span * i) / N, a1 = tail + (span * (i + 1)) / N;
      const op = (k) => 0.03 + 0.97 * Math.pow(k / N, 1.5);
      const [x0, y0] = PT(c, c, r, a0), [x1, y1] = PT(c, c, r, a1);
      defs += `<linearGradient id="${id}g${i}" gradientUnits="userSpaceOnUse" x1="${f2(x0)}" y1="${f2(y0)}" x2="${f2(x1)}" y2="${f2(y1)}"><stop offset="0" stop-color="${col}" stop-opacity="${op(i).toFixed(3)}"/><stop offset="1" stop-color="${col}" stop-opacity="${op(i + 1).toFixed(3)}"/></linearGradient>`;
      segs += `<path d="${arcD(c, c, r, a0, a1 + (i < N - 1 ? 0.25 : 0))}" stroke="url(#${id}g${i})"${i === N - 1 ? ' stroke-linecap="round"' : ''}/>`;
    }
    const [dx, dy] = PT(c, c, r, head + 34);
    return `<svg class="onb-loop ${o.cls || ''}" viewBox="0 0 100 100" width="${size}" height="${size}" aria-hidden="true"><defs>${defs}<mask id="${id}m" maskUnits="userSpaceOnUse" x="0" y="0" width="100" height="100"><path class="onb-loop-mask" d="${arcD(c, c, r, tail, head)}" fill="none" stroke="#fff" stroke-width="${f2(sw + 7)}" stroke-linecap="round" pathLength="1" stroke-dasharray="1 1"/></mask></defs>
      <g mask="url(#${id}m)" fill="none" stroke-width="${f2(sw)}">${segs}</g>
      <g class="onb-loop-dot" style="transform-origin:50px 50px;transform-box:view-box"><circle cx="${f2(dx)}" cy="${f2(dy)}" r="${f2((1.22 * sw) / 2)}" fill="${o.dot || 'var(--onb-dot)'}"/></g></svg>`;
  }
  /** Catmull-Rom smoothing of a normalised glyph (0..1 points) fitted into w×h, aspect kept. */
  function smoothPath(pts, w, h, pad = 10, range) {
    const xs = pts.map((p) => p[0]), ys = pts.map((p) => p[1]);
    const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
    const s = Math.min((w - 2 * pad) / (maxX - minX || 1), (h - 2 * pad) / (maxY - minY || 1));
    const ox = (w - (maxX - minX) * s) / 2, oy = (h - (maxY - minY) * s) / 2;
    const P = pts.map((p) => [ox + (p[0] - minX) * s, oy + (p[1] - minY) * s]);
    const [a, b] = range || [0, P.length - 1];
    let d = `M${f2(P[a][0])} ${f2(P[a][1])}`;
    for (let i = a; i < b; i++) {
      const p0 = P[Math.max(0, i - 1)], p1 = P[i], p2 = P[i + 1], p3 = P[Math.min(P.length - 1, i + 2)];
      d += `C${f2(p1[0] + (p2[0] - p0[0]) / 6)} ${f2(p1[1] + (p2[1] - p0[1]) / 6)} ${f2(p2[0] - (p3[0] - p1[0]) / 6)} ${f2(p2[1] - (p3[1] - p1[1]) / 6)} ${f2(p2[0])} ${f2(p2[1])}`;
    }
    return { d, P };
  }
  const COMMUTE = () => U.trip('t20261105-0742');
  /** Example drive used by the intro art: the fixture commute (Home → Work, Thursday morning). */
  function routeArt(w, h, o = {}) {
    const t = COMMUTE(), pts = t.glyph;
    const full = smoothPath(pts, w, h, o.pad ?? 14);
    const k = o.estimated ? 2 : 0;
    const est = o.estimated ? smoothPath(pts, w, h, o.pad ?? 14, [0, k]).d : '';
    const solid = o.estimated ? smoothPath(pts, w, h, o.pad ?? 14, [k, pts.length - 1]).d : full.d;
    const [sx, sy] = full.P[0], [ex, ey] = full.P[full.P.length - 1];
    return `<svg class="onb-route ${o.cls || ''}" viewBox="0 0 ${w} ${h}" width="100%" height="100%" preserveAspectRatio="xMidYMid meet" aria-hidden="true">
      ${o.grid ? `<g class="onb-route-grid">${Array.from({ length: 7 }, (_, i) => `<path d="M${(i * w) / 6} 0V${h}"/>`).join('')}${Array.from({ length: 5 }, (_, i) => `<path d="M0 ${(i * h) / 4}H${w}"/>`).join('')}</g>` : ''}
      <path class="onb-route-case" d="${full.d}"/>
      ${est ? `<path class="onb-route-est" d="${est}"/>` : ''}
      <path class="onb-route-line" d="${solid}"/>
      <circle class="onb-route-head" cx="${f2(ex)}" cy="${f2(ey)}" r="5"/>
      ${o.ends !== false ? `<circle class="onb-route-start" cx="${f2(sx)}" cy="${f2(sy)}" r="5"/><circle class="onb-route-end" cx="${f2(ex)}" cy="${f2(ey)}" r="6.5"/>` : ''}
      ${o.extra || ''}</svg>`;
  }
  /** Route draw-on with a glow head that rides the tip (motion.md §3.3). Returns cleanup. */
  function drawRoute(ctx, root, { duration = 1800, delay = 0 } = {}) {
    const svg = root.querySelector('.onb-route'); if (!svg) return () => {};
    const line = svg.querySelector('.onb-route-line'), est = svg.querySelector('.onb-route-est'), head = svg.querySelector('.onb-route-head');
    const ends = svg.querySelectorAll('.onb-route-start, .onb-route-end, .onb-route-pin');
    if (still(ctx, root)) { if (!ctx.shot) svg.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 220 }); return () => {}; }
    let stop = () => {};
    if (est) ctx.motion.drawOn(est, { duration: duration * 0.18, delay });
    const d2 = est ? delay + duration * 0.16 : delay;
    ctx.motion.drawOn(line, { duration: duration - (d2 - delay), delay: d2 });
    svg.querySelectorAll('.onb-route-case').forEach((c) => ctx.motion.drawOn(c, { duration, delay }));
    ends.forEach((e, i) => e.animate([{ opacity: 0, transform: 'scale(.4)' }, { opacity: 1, transform: 'scale(1)' }], { duration: 360, delay: i ? delay + duration : delay, fill: 'both', easing: 'cubic-bezier(.34,1.56,.64,1)', transformOrigin: 'center', transformBox: 'fill-box' }));
    if (head && line.getTotalLength) {
      const len = line.getTotalLength();
      const timer = setTimeout(() => {
        const tw = ctx.motion.tween({ from: 0, to: 1, duration: duration - (d2 - delay), ease: 'drawOn', el: root, onUpdate: (p) => { const q = line.getPointAtLength(len * p); head.setAttribute('cx', q.x); head.setAttribute('cy', q.y); head.style.opacity = p < 0.98 ? 1 : (1 - p) * 50; }, onDone: () => (head.style.opacity = 0) });
        stop = tw.stop;
      }, d2);
      head.style.opacity = 0;
      return () => { clearTimeout(timer); stop(); };
    }
    return () => {};
  }
  /** Loop draw-on: the trail draws clockwise while the G-dot glides ahead of it, then settles (brand §3, motion §3.9 family). */
  function drawLoop(ctx, root, { duration = 1300, delay = 0 } = {}) {
    root.querySelectorAll('.onb-loop').forEach((svg) => {
      const m = svg.querySelector('.onb-loop-mask'), dot = svg.querySelector('.onb-loop-dot');
      if (still(ctx, root)) { if (!ctx.shot) svg.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 220 }); return; }
      m.animate([{ strokeDashoffset: 1 }, { strokeDashoffset: 0 }], { duration, delay, easing: 'cubic-bezier(.65,0,.35,1)', fill: 'both' });
      dot.animate([{ transform: 'rotate(-270deg) scale(.6)', opacity: 0 }, { opacity: 1, offset: 0.12 }, { transform: 'rotate(8deg) scale(1)', opacity: 1, offset: 0.86 }, { transform: 'rotate(0deg) scale(1)', opacity: 1 }], { duration: duration + 260, delay, easing: 'cubic-bezier(.65,0,.35,1)', fill: 'both' });
    });
  }
  /** Generic car side silhouette (brand §7: outlines by body style, no badges). */
  function carArt(colour, o = {}) {
    const col = colour ? `var(--dp-car-${colour})` : 'var(--dp-text-3)';
    return `<svg class="onb-car" viewBox="0 0 220 84" width="${o.w || 220}" height="${(o.w || 220) * 84 / 220}" aria-hidden="true" style="--car:${col}">
      <path class="onb-car-shadow" d="M18 74h184"/>
      <path class="onb-car-body" d="M12 62c0-9 3-15 12-17l26-5c10-12 22-20 40-21h40c14 0 24 7 34 18l20 4c10 2 16 8 16 17v4c0 3-2 5-5 5H17c-3 0-5-2-5-5z"/>
      <path class="onb-car-glass" d="M58 40c9-10 19-15 32-15h18v15zM114 25h16c11 0 19 5 26 15h-42z"/>
      <circle class="onb-car-wheel" cx="54" cy="66" r="13"/><circle class="onb-car-wheel" cx="168" cy="66" r="13"/>
      <circle class="onb-car-hub" cx="54" cy="66" r="5"/><circle class="onb-car-hub" cx="168" cy="66" r="5"/></svg>`;
  }

  // ======================================================================================================
  // Page scaffold (the onboarding step template · components.md §16 permission primer)
  // ======================================================================================================
  function progress(ctx) {
    const list = STEPS[ctx.platform], i = list.indexOf(ctx.id);
    if (i < 0) return '<span class="onb-prog-sp"></span>';
    return `<span class="onb-prog" role="progressbar" aria-label="Setup, step ${i + 1} of ${list.length}" aria-valuemin="1" aria-valuemax="${list.length}" aria-valuenow="${i + 1}" style="--p:${((i + 1) / list.length).toFixed(3)}"><b></b><i></i></span>`;
  }
  function topBar(ctx, o) {
    const back = o.back === false ? '<span class="onb-top-sp"></span>' : UI.iconButton('back', { back: true, label: 'Back', glass: ctx.ios, cls: 'onb-back', svg: UI.icon(ctx.ios ? 'chevronLeft' : 'arrowBack', ctx.ios ? 22 : 24) });
    const mid = o.dots != null || o.progress === false ? '<span class="onb-prog-sp"></span>' : progress(ctx);
    const right = o.skip ? `<span class="onb-skip ui-press" ${UI.act({ nav: 'S-ONB-04/checkboxUnticked', label: 'Skip the intro', action: 'skip' })}>Skip</span>` : '<span class="onb-top-sp"></span>';
    return `<div class="onb-top ${o.topCls || ''}">${back}${mid}${right}</div>`;
  }
  const dots = (i, n = 3) => `<div class="onb-dots" role="img" aria-label="Page ${i + 1} of ${n}">${Array.from({ length: n }, (_, k) => `<i class="${k === i ? 'is-on' : ''}"></i>`).join('')}</div>`;
  function dock(ctx, o) {
    const p = o.primary ? UI.button(o.primary.text, { kind: 'filled', size: 'l', block: true, haptic: 'tap', ...o.primary }) : '';
    const s = o.secondary ? UI.button(o.secondary.text, { kind: ctx.ios ? 'glass' : 'text', size: 'l', block: true, ...o.secondary, cls: 'onb-sec' }) : '';
    const t = o.tertiary ? UI.button(o.tertiary.text, { kind: 'text', size: 'm', block: true, ...o.tertiary, cls: 'onb-ter' }) : '';
    return `<div class="onb-dock">${o.dots != null ? dots(o.dots) : ''}${p}${s}${t}${o.note ? `<p class="onb-note">${o.note}</p>` : ''}</div>`;
  }
  /** o: {art, title, lead (html), body (html), align:'center'|'start', primary, secondary, tertiary, note, dots, skip, back,
          progress, overlay, fixed, cls, topCls} */
  function onbPage(ctx, o) {
    const main = `<div class="onb-main is-${o.align || 'center'}">${o.art ? `<div class="onb-art" aria-hidden="true">${o.art}</div>` : ''}${o.kicker ? `<span class="onb-kick">${o.kicker}</span>` : ''}<h1 class="onb-h">${o.titleHtml || esc(o.title)}</h1>${o.lead ? `<p class="onb-lead">${o.lead}</p>` : ''}${o.body || ''}</div>`;
    return UI.page(`<div class="onb-wrap ${o.cls || ''}">${main}${dock(ctx, o)}</div>`, { fixed: topBar(ctx, o) + (o.fixed || ''), overlay: o.overlay, cls: 'onb-page' });
  }
  /** What you get / what we never do (components.md §16 ④). kinds: yes · no · est · pause · battery · info · bell · sleep */
  function checks(items, o = {}) {
    const glyph = (k) => ({ yes: UI.icon('check', 16), no: UI.icon('close', 16), est: I.provenance('estimated', { size: 16 }), pause: UI.icon('pause', 16), battery: UI.icon('battery', 17), info: UI.icon('info', 17), bell: UI.icon('bell', 16), sleep: UI.icon('phone', 16), lock: UI.icon('lock', 15), story: UI.icon('story', 16), warn: UI.icon('warn', 16), gps: UI.icon('gps', 16) }[k] || UI.icon('check', 16));
    return `<ul class="onb-checks ${o.cls || ''}" ${o.label ? `aria-label="${esc(o.label)}"` : ''}>${items.filter(Boolean).map((it) => `<li class="is-${it.kind || 'yes'}"><span class="onb-ck" aria-hidden="true">${glyph(it.kind || 'yes')}</span><span class="onb-ck-t"><b>${esc(it.text)}</b>${it.sub ? `<small>${esc(it.sub)}</small>` : ''}</span></li>`).join('')}</ul>`;
  }
  /** Prototype-only control under a mocked system prompt: picks what the user "tapped". Hidden in export shots. */
  function sim(choices) {
    return `<div class="onb-sim" role="group" aria-label="Prototype only: choose what the user taps"><span class="onb-sim-k">Prototype · pick the result</span><div class="onb-sim-b">${choices.map((c) => `<span class="onb-sim-c ui-press ${c.primary ? 'is-on' : ''}" ${UI.act({ ...c, label: `Simulate: ${c.text}` })}>${esc(c.text)}</span>`).join('')}</div></div>`;
  }
  /** An in-screen sheet state (for a sheet that has no screen ID of its own, e.g. "How it works"). Looks like the
      engine sheet but closes by switching state, so drag/scrim never pop the screen underneath. */
  function localSheet(ctx, content, { close = 'default', detent = 'large', label = 'Sheet' } = {}) {
    return `<div class="onb-scrim" ${UI.act({ state: close, label: 'Close', noRole: true })}></div><div class="ui-sheet onb-lsheet" data-detent="${detent}" role="dialog" aria-modal="true" aria-label="${esc(label)}"><div class="onb-grab" aria-hidden="true"></div><div class="ui-sheet-body">${content}</div></div>`;
  }
  /** Non-interactive, screenshot-style Settings guide: a scaled OS Settings mock inside a rounded frame. */
  function settingsShot(ctx, o, { h = 300, scale = 0.56 } = {}) {
    const w = ctx.size.w;
    return `<div class="onb-shot" style="width:${Math.round(w * scale)}px;height:${h}px" aria-hidden="true"><div class="onb-shot-in" style="width:${w}px;height:${Math.round(h / scale)}px;transform:scale(${scale})">${UI.sysSettings(o)}</div></div>`;
  }
  /** System prompt + simulated choices, as the primer's overlay. */
  const sysOverlay = (kind, o, choices) => UI.sysPermission(kind, o) + sim(choices);
  /** A status line under a primer once the system prompt has answered (granted/denied). */
  const resultLine = (kind, text) => `<div class="onb-result is-${kind}" role="status">${kind === 'ok' ? UI.icon('check', 16) : UI.icon(kind === 'off' ? 'close' : 'info', 16)}<span>${esc(text)}</span></div>`;
  /** The mount shared by most steps: one hero motion plus a quiet rise for the text. */
  function stepMount(el, ctx, hero) {
    const cleanups = [];
    if (ctx.motion.reduced(el) && !ctx.shot) { const w = el.querySelector('.onb-wrap'); if (w) w.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 220 }); }
    if (!still(ctx, el)) ctx.ui.anim.rise(el, '.onb-main > :not(.onb-art), .onb-dock', { stagger: 40, delay: 120 });
    const c = hero && hero(el, ctx); if (typeof c === 'function') cleanups.push(c);
    return () => cleanups.forEach((f) => f());
  }
  const popArt = (el, ctx) => { const a = el.querySelector('.onb-art > *'); if (a && !still(ctx, el)) ctx.motion.animate(a, [{ opacity: 0, transform: 'scale(.86)' }, { opacity: 1, transform: 'scale(1)' }], { preset: 'bouncy' }); };

  // Shared copy (one source for primer, sheet and spec)
  const COPY = {
    lead: 'Smooth is a skill.',
    support: 'Every drive, decoded.',
    privacy: 'No account needed. Your trips stay on your phone unless you turn on backup.',
    preview: 'Pro Preview included for 14 days, no card',
    promise: D.health.presets.allGood.promise, // "Auto-detects most drives. The first few hundred metres may be estimated. Force-quitting pauses auto-detect."
    battery: 'About 2–4% battery per hour of driving with the screen off.',
    disclosure: 'DrivePerf collects location data to enable automatic trip recording, even when the app is closed or not in use.',
    ready: 'You’re set. Just drive. Your first Story appears when you park.',
    neverSold: 'Never sold, never shared with insurers',
  };
  const SPEC_A11Y_COMMON = 'Focus order: Back → step progress (“Setup, step n of N”) → title → body → primary → secondary. XL: art shrinks to 60%, title and copy wrap, the dock stays pinned and scrolls content under it; nothing truncates. Every glyph in a checklist is paired with its text (colour is never the only signal).';

  // ======================================================================================================
  // S-ONB-01 · Welcome: "Smooth is a skill."
  // ======================================================================================================
  screen('S-ONB-01', {
    name: 'Welcome: “Smooth is a skill.”',
    wp: 'A', platforms: ['ios', 'android'], presentation: 'full', tab: null,
    states: [{ id: 'default', label: 'Def' }],
    render(ctx) {
      const hero = `<div class="onb-hero"><div class="onb-hero-route">${routeArt(360, 300, { ends: false, pad: 18, cls: 'is-hero' })}</div><div class="onb-hero-loop"><span class="onb-hero-glow"></span>${loopSvg(ctx.xl ? 128 : 172)}</div></div>`;
      return onbPage(ctx, {
        cls: 'onb-welcome', topCls: 'is-clear', back: false, skip: true, dots: 0,
        art: hero,
        titleHtml: `<span class="onb-word">DrivePerf</span><span class="onb-tag">${esc(COPY.lead)}</span>`,
        lead: esc(COPY.support),
        primary: { text: 'Get started', nav: 'S-ONB-02', action: 'start', label: 'Get started' },
      });
    },
    mount(el, ctx) {
      const cleanups = [];
      if (ctx.motion.reduced(el) && !ctx.shot) el.querySelector('.onb-wrap').animate([{ opacity: 0 }, { opacity: 1 }], { duration: 220 });
      cleanups.push(drawRoute(ctx, el, { duration: 1800 }));
      drawLoop(ctx, el, { duration: 1300, delay: 450 });
      if (!still(ctx, el)) {
        ctx.ui.anim.rise(el, '.onb-word, .onb-tag, .onb-lead, .onb-dock', { stagger: 70, delay: 1350 });
        const glow = el.querySelector('.onb-hero-glow'); if (glow) glow.animate([{ opacity: 0, transform: 'scale(.7)' }, { opacity: 1, transform: 'scale(1)' }], { duration: 1600, delay: 400, fill: 'both', easing: 'cubic-bezier(.32,.72,0,1)' });
      }
      return () => cleanups.forEach((f) => f());
    },
    actions: { start: () => true, skip: () => true },
    spec: {
      purpose: 'First launch (F-ONB-01): say what DrivePerf is in one breath — “Smooth is a skill.” / “Every drive, decoded.” — with the brand’s hero motion, and never ask for a permission before S-ONB-05.',
      layout: [
        'Full-screen cover, no navigation bar. Background: night sky (dark: --dp-gradient-night-sky + a Glide glow at 16% behind the mark · light “Day Drive”: Glide 10% wash fading into --app-bg).',
        'Top trailing: “Skip” text button (44 pt / 48 dp target) → S-ONB-04 (the age and terms step can’t be skipped).',
        'Hero (≈ 46% of the height): the example commute (fixture t20261105-0742, Home → Work) as a smoothed route line, Glide 2.4 pt at 55% with a 9 pt casing glow at 10%, full width; The Loop mark 172 pt (XL 128) centred over it, trail fading 0.03 → 1 clockwise, G-dot 34° ahead (brand.md §3.1).',
        'Wordmark “DrivePerf” (wide semibold, +1% tracking, --dp-text-2) → lead “Smooth is a skill.” (iOS 40/44 bold display · Android displaySmall 40/48 emphasized) → support “Every drive, decoded.” (title3 / titleLarge, --dp-text-2). Centred, 32 side margins.',
        'Bottom: page dots (1 of 3; current = 18×6 capsule in Glide, others 6×6 --dp-fill-strong) above “Get started” (iOS glass-prominent capsule 52 pt in a floating glass dock · Android filled 56 dp full width).',
      ],
      components: ['page', 'button', 'icon'],
      interactions: ['“Get started” → S-ONB-02 (push inside the onboarding stack).', '“Skip” → S-ONB-04/checkboxUnticked; logs onboarding_skipped {from_step: welcome}.', 'No permission prompt can appear on this screen (F-ONB-01.1).'],
      motion: [
        'Hero motion (route draw-on + Loop): 0–1800 ms the route draws on (stroke-dashoffset, ease.drawOn) with a 5 pt glow head riding the tip, which dissolves at the end (motion.md §3.3).',
        '450–1750 ms The Loop’s trail draws on clockwise through a mask (ease.drawOn 1300 ms) while the G-dot glides 270° around just ahead of the head, overshoots 8° and settles back to its 34° lead (spring-like keyframe, total 1560 ms).',
        '1350 ms+: wordmark, lead, support and the dock rise 8 px and fade in, 70 ms stagger (spring.default).',
        'Reduced motion / Remove animations: everything is drawn final and the page cross-fades in over 220 ms (F-ONB-01.4). Export shot mode renders the final frame.',
      ],
      a11y: [
        'VoiceOver/TalkBack reads: “DrivePerf. Smooth is a skill. Every drive, decoded.” (one heading), then “Page 1 of 3”, then “Get started, button”. The hero art is decorative (hidden).',
        '“Skip” label: “Skip the intro”. XL: the mark shrinks to 128, the lead wraps to two lines, the dock stays pinned; nothing truncates.',
        'Contrast: lead text on the night sky ≥ 12:1; support line --dp-text-2 ≥ 7:1 (dark) / ≥ 5:1 (light).',
      ],
      platformDiffs: {
        ios: 'Inside a .fullScreenCover; buttons in a floating Liquid Glass dock (inset 8, radius 46) with .buttonStyle(.glassProminent); Skip is a plain toolbar text button; SF Pro Display for the lead.',
        android: 'Activity-level destination, edge to edge; filled Button 56 dp (ButtonDefaults.shapes(), round → square morph on press) with no container behind it; Google Sans Flex displaySmall emphasized; Skip is a TextButton.',
      },
      analytics: ['onboarding_started', 'onboarding_step_viewed {step: welcome}', 'onboarding_skipped {from_step: welcome}'],
      copy: { Wordmark: 'DrivePerf', Lead: COPY.lead, Support: COPY.support, Primary: 'Get started', Skip: 'Skip' },
      freePro: 'Same for everyone. No plan is mentioned until S-ONB-03.',
      data: 'Hero route = DP_DATA trips t20261105-0742 glyph (16 normalised points), smoothed with Catmull-Rom into a cubic path (derived, illustration only, no map or coordinates).',
      icons: [],
      assets: ['logo-a-loop.svg', 'logo-a-loop-on-light.svg', 'wordmark.svg', 'lockup-stacked.svg'],
    },
  });

  // ======================================================================================================
  // S-ONB-02 · Intro: "Every drive, decoded." (Story preview)
  // ======================================================================================================
  function storyPreview(ctx) {
    const t = COMMUTE(), s = t.smoothness, f = t.story.frames, v = U.vehicle(t.vehicleId);
    const segs = Array.from({ length: 7 }, (_, i) => `<i class="${i < 3 ? 'is-done' : i === 3 ? 'is-on' : ''}"></i>`).join('');
    return `<div class="onb-story" style="--car:var(--dp-car-${v.colour})" role="img" aria-label="${esc(`Example Story: ${f.f1.title.replace('→', 'to')}. ${f.f2.line.replace('km', 'kilometres').replace('min', 'minutes')}. Smoothness ${s.score}, ${s.delta} above the usual on ${s.routeName}. New route record.`)}">
      <div class="onb-story-seg" aria-hidden="true">${segs}</div>
      <div class="onb-story-top"><span class="onb-story-k">Example drive</span>${UI.carChip(v)}</div>
      <b class="onb-story-title">${esc(f.f1.title)}</b>
      <div class="onb-story-map">${routeArt(240, 120, { pad: 12 })}</div>
      <div class="onb-story-score">${UI.ring(s.score, 92, { base: s.baseline, rec: s.record.previous.score, recNew: true, stroke: 8, label: false, aria: `Smoothness ${s.score}` })}
        <div class="onb-story-st"><span>Smoothness</span>${UI.chip('New route record', { kind: 'record', icon: I.svg('rosette', { size: 14 }) })}${UI.chip('', { kind: 'up', html: `<b aria-hidden="true">▲</b>${esc(s.compareText)}` })}</div></div>
    </div>`;
  }
  screen('S-ONB-02', {
    name: 'Intro: “Every drive, decoded.” (Story preview)',
    wp: 'A', platforms: ['ios', 'android'], presentation: 'push', tab: null, backTo: 'S-ONB-01',
    states: [{ id: 'default', label: 'Def' }],
    render(ctx) {
      return onbPage(ctx, {
        cls: 'onb-intro', skip: true, dots: 1,
        art: storyPreview(ctx),
        title: COPY.support,
        lead: 'Park, and each drive becomes a short Story: the route, your inputs and a Smoothness score compared with your own usual. Never with anyone else’s.',
        primary: { text: 'Next', nav: 'S-ONB-03' },
      });
    },
    mount(el, ctx) {
      return stepMount(el, ctx, () => {
        const c = drawRoute(ctx, el, { duration: 1400, delay: 200 });
        if (still(ctx, el)) return c;
        const ring = el.querySelector('.onb-story-score');
        ring.style.opacity = 0;
        const tm = setTimeout(() => { ring.style.opacity = ''; ctx.motion.animate(ring, [{ opacity: 0, transform: 'translateY(8px)' }, { opacity: 1, transform: 'none' }], { preset: 'default' }); ctx.ui.anim.rings(ring); }, 1200);
        return () => { clearTimeout(tm); c(); };
      });
    },
    actions: { skip: () => true },
    spec: {
      purpose: 'Show, not tell, what a decoded drive looks like (F-ONB-01): a Story preview built from an example drive, with the baseline-relative promise “compared with your own usual”.',
      layout: [
        'Top bar: Back (iOS glass 44 · Android arrow 48) · Skip (trailing). No step progress on intro pages; page dots sit in the dock (2 of 3).',
        'Story preview card, 280 × ~370, radius 32 (iOS) / 28 (Android), night background with a 16% wash of the car colour (GTI carmine), 7 progress segments at the top (3 played, 4th active), “EXAMPLE DRIVE” kicker + car chip, title “Thursday morning · Home → Work”, the route line on a faint grid, then the Smoothness ring 92 (baseline notch 84, gold record notch 89 → new) with “New route record” and “▲ +7 vs your usual on The Commute” chips.',
        'Title “Every drive, decoded.” (iOS 30/36 bold · Android headlineLarge 32/40) and one lead paragraph (body, --dp-text-2), centred.',
        'Dock: page dots (2 of 3) + “Next”.',
      ],
      components: ['page', 'ring', 'chip', 'carChip', 'button'],
      interactions: ['“Next” → S-ONB-03.', 'Back → S-ONB-01.', '“Skip” → S-ONB-04/checkboxUnticked (onboarding_skipped {from_step: story_preview}).', 'The preview card is not interactive (it is an illustration with a full text label).'],
      motion: [
        'Hero motion: the Story “plays” once — the route draws on (1400 ms, ease.drawOn, glow head) from 200 ms, then at 1200 ms the score block rises and the ring reveal runs (motion.md §3.4: arc + count-up 1400 ms, record notch flips gold as the arc passes 89, doubleSuccess haptic).',
        'Text and dock rise 8 px, 40 ms stagger (spring.default).',
        'RM: the card is drawn final (full route, full ring, static 91); the page cross-fades 220 ms. Haptics stay (one tap).',
      ],
      a11y: [
        'The card is one image element: “Example Story: Thursday morning · Home to Work. 37.9 kilometres · 30 minutes. Smoothness 91, 7 above the usual on The Commute. New route record.”',
        'Record and delta chips carry glyphs (rosette, ▲) and text; gold is never the only signal.',
        SPEC_A11Y_COMMON,
      ],
      platformDiffs: { ios: 'Page inside the onboarding NavigationStack; ring on content; glass chips; glass-prominent Next in a glass dock.', android: 'Ring on a Cookie9Sided primaryContainer shape; AssistChips; filled Next 56 dp.' },
      analytics: ['onboarding_step_viewed {step: story_preview}', 'onboarding_skipped {from_step: story_preview}'],
      copy: { Title: COPY.support, Lead: 'Park, and each drive becomes a short Story: the route, your inputs and a Smoothness score compared with your own usual. Never with anyone else’s.', Kicker: 'Example drive', Primary: 'Next' },
      freePro: 'Free: Stories and Smoothness are free forever, so the preview promises nothing paid.',
      data: 'Example = trips t20261105-0742: story.frames.f1.title, f2.line, smoothness.score 91 / baseline 84 / record.previous 89 / compareText “+7 vs your usual on The Commute”; vehicle gti (carmine). Route = smoothed glyph (derived).',
      icons: ['rosette', 'chevronLeft', 'arrowBack'],
      assets: [],
    },
  });

  // ======================================================================================================
  // S-ONB-03 · Intro: Private by design + Pro Preview included
  // ======================================================================================================
  function privacyArt() {
    return `<div class="onb-priv"><svg class="onb-phone" viewBox="0 0 120 200" width="104" height="174" aria-hidden="true"><rect x="4" y="4" width="112" height="192" rx="24"/><rect class="onb-phone-isl" x="44" y="14" width="32" height="9" rx="4.5"/></svg>
      <div class="onb-priv-route">${routeArt(96, 130, { pad: 8 })}</div>
      <span class="onb-priv-lock">${UI.icon('lock', 20)}</span></div>`;
  }
  screen('S-ONB-03', {
    name: 'Intro: Private by design + Pro Preview included',
    wp: 'A', platforms: ['ios', 'android'], presentation: 'push', tab: null, backTo: 'S-ONB-02',
    states: [{ id: 'default', label: 'Def' }],
    render(ctx) {
      const pips = Array.from({ length: 14 }, (_, i) => `<i class="${i === 0 ? 'is-on' : ''}"></i>`).join('');
      const preview = `<div class="onb-pv" aria-label="${esc(COPY.preview + '. Then choose Pro, or keep Free.')}"><div class="onb-pv-h">${UI.proBadge('PRO PREVIEW')}<b>${esc(COPY.preview)}</b></div><div class="onb-pv-pips" aria-hidden="true">${pips}</div><p>Replay and Analysis for every drive, unlimited routes and trends. Then choose Pro, or keep Free: recording, Stories and scores stay free.</p></div>`;
      return onbPage(ctx, {
        cls: 'onb-intro', dots: 2,
        art: privacyArt(),
        title: 'Private by design',
        lead: esc(COPY.privacy),
        body: checks([
          { kind: 'yes', text: 'Worked out on your phone', sub: 'Scores, routes and Stories are calculated on the device.' },
          { kind: 'no', text: COPY.neverSold },
          { kind: 'yes', text: 'You choose what’s shared', sub: 'Share cards never show your speed. Map cards are opt-in.' },
        ], { label: 'Privacy' }) + preview,
        primary: { text: 'Continue', nav: 'S-ONB-04/checkboxUnticked' },
      });
    },
    mount(el, ctx) { return stepMount(el, ctx, () => { popArt(el, ctx); return drawRoute(ctx, el, { duration: 1200, delay: 250 }); }); },
    spec: {
      purpose: 'Earn trust before any permission (F-ONB-01.2): state “No account needed. Your trips stay on your phone unless you turn on backup.” and “Pro Preview included for 14 days, no card”.',
      layout: [
        'Top bar: Back only (no Skip on the last intro page: Continue goes to the same place).',
        'Art: phone outline 104 × 174 (stroke --dp-text-3) holding the example route in Glide, with a lock badge (40, accent container) on its bottom-trailing corner.',
        'Title “Private by design”, lead = the privacy line (centred).',
        'Checklist card (--card-bg, radius card): ✓ Worked out on your phone · ✕ Never sold, never shared with insurers · ✓ You choose what’s shared. Glyph discs 28: check = Glide container, ✕ = neutral fill.',
        'Pro Preview card: “PRO PREVIEW” badge + “Pro Preview included for 14 days, no card”, a 14-pip day strip (day 1 lit), one line on what Preview unlocks and what stays Free.',
        'Dock: page dots (3 of 3) + “Continue”.',
      ],
      components: ['page', 'proBadge', 'button', 'icon'],
      interactions: ['“Continue” → S-ONB-04/checkboxUnticked.', 'Back → S-ONB-02.'],
      motion: ['Hero motion: the art pops in (spring.bouncy, scale .86 → 1) and the route draws on inside the phone (1200 ms, ease.drawOn) from 250 ms.', 'Text rises 8 px, 40 ms stagger.', 'RM: final art, 220 ms cross-fade.'],
      a11y: ['Checklist is a list: “Worked out on your phone. Scores, routes and Stories are calculated on the device.” etc.', 'Preview card label: “Pro Preview included for 14 days, no card. Then choose Pro, or keep Free.”', SPEC_A11Y_COMMON],
      platformDiffs: { ios: 'Grouped content cards (radius 24) on --dp-bg-grouped; Preview badge Glide on accent container.', android: 'surfaceContainer cards (radius 16 · Preview card 28); M3E filled Continue.' },
      analytics: ['onboarding_step_viewed {step: privacy}'],
      copy: { Title: 'Private by design', Lead: COPY.privacy, Checks: 'Worked out on your phone · ' + COPY.neverSold + ' · You choose what’s shared', Preview: COPY.preview, 'Preview line': 'Replay and Analysis for every drive, unlimited routes and trends. Then choose Pro, or keep Free: recording, Stories and scores stay free.' },
      freePro: 'States the 14-day no-card Pro Preview (Gate 2 OQ-08). No price, no paywall, no countdown here.',
      data: 'Preview length from DP_DATA.entitlement.states.preview.of (14). Free/Pro wording from entitlement.proFeatures (summarised).',
      icons: ['lock', 'check', 'close'],
      assets: [],
    },
  });

  // ======================================================================================================
  // S-ONB-04 · Age (18+) and terms
  // ======================================================================================================
  function ageArt(ctx) {
    const shape = ctx.android ? `<svg viewBox="0 0 120 120" width="112" height="112" aria-hidden="true"><path d="${UI.cookie(9, 56, 60, 60, 0.07)}" fill="var(--onb-art-fill)"/></svg>` : '<span class="onb-age-sq"></span>';
    return `<div class="onb-age">${shape}<b>18+</b></div>`;
  }
  function checkbox(on, label) {
    return `<span class="onb-cb ${on ? 'is-on' : ''} ui-press" role="checkbox" aria-checked="${on}" tabindex="0" data-action="age" aria-label="${esc(label)}"><i aria-hidden="true">${UI.icon('check', 16)}</i><b>${esc(label)}</b></span>`;
  }
  screen('S-ONB-04', {
    name: 'Age (18+) and terms',
    wp: 'A', platforms: ['ios', 'android'], presentation: 'push', tab: null, backTo: 'S-ONB-03',
    states: [
      { id: 'default', label: 'Def (ticked, Continue enabled)' },
      { id: 'checkboxUnticked', label: 'checkbox unticked (Continue disabled)' },
      { id: 'under18Blocked', label: 'under-18 blocked' },
    ],
    render(ctx) {
      if (ctx.state === 'under18Blocked') {
        return onbPage(ctx, {
          back: false, progress: false, art: `<div class="onb-blocked">${loopSvg(96, { color: 'var(--dp-text-3)', dot: 'var(--dp-text-3)' })}</div>`,
          title: 'DrivePerf is for drivers 18 and over for now',
          lead: 'Thanks for checking it out. Nothing about you was saved, apart from a note on this phone so this screen shows next time.',
          tertiary: { text: 'I tapped this by mistake', state: 'checkboxUnticked' },
        });
      }
      const on = ctx.state === 'default';
      const body = `<div class="onb-age-box">${checkbox(on, 'I’m 18 or older')}<p class="onb-terms">By continuing you agree to the <span class="onb-link ui-press" ${UI.act({ toast: 'Opens driveperf.com/terms (v1.0) in an in-app browser', label: 'Terms of Use, opens in browser' })}>Terms of Use</span> and <span class="onb-link ui-press" ${UI.act({ toast: 'Opens driveperf.com/privacy (v1.0) in an in-app browser', label: 'Privacy Policy, opens in browser' })}>Privacy Policy</span>. We save the version you accepted, on this phone.</p></div>`;
      return onbPage(ctx, {
        art: ageArt(ctx), title: 'One quick check', lead: 'DrivePerf is for drivers 18 and over. No date of birth needed: you just confirm it.',
        body,
        primary: { text: 'Continue', nav: 'S-ONB-05', disabled: !on, label: on ? 'Continue' : 'Continue, unavailable until you confirm you’re 18 or older' },
        tertiary: { text: 'I’m under 18', state: 'under18Blocked' },
      });
    },
    mount(el, ctx) { return stepMount(el, ctx, () => { if (ctx.state === 'under18Blocked') drawLoop(ctx, el, { duration: 1100 }); else popArt(el, ctx); }); },
    actions: {
      age: (ctx) => { ctx.haptic(ctx.state === 'default' ? 'toggleOff' : 'toggleOn'); ctx.setState(ctx.state === 'default' ? 'checkboxUnticked' : 'default'); return false; },
    },
    spec: {
      purpose: 'Keep v1 adults-only (F-ONB-02, PRD §6.7): self-attested 18+ with no date of birth, plus terms and privacy acceptance. This step can’t be skipped.',
      layout: [
        'Top bar: Back · step progress (step 1 of 9 iOS / 1 of 11 Android).',
        'Art: “18+” in DP Numerals 34 on a 112 badge (iOS continuous-corner square 34 radius, Glide 14% · Android Cookie9Sided primaryContainer).',
        'Title “One quick check”, lead (centred).',
        'Confirmation card: a checkbox row “I’m 18 or older” (iOS: 26 pt circular check, SF “checkmark.circle.fill” style · Android: M3 Checkbox 18 dp in a 48 dp target) and the terms line with two links (Glide text, underlined).',
        'Dock: “Continue” (disabled until ticked: 38% opacity, not focus-skipped) and a tertiary text button “I’m under 18”.',
        'under18Blocked: no back or progress; a muted (text-3) Loop 96, title “DrivePerf is for drivers 18 and over for now”, one honest line, and a tertiary “I tapped this by mistake”. Onboarding stops here.',
      ],
      components: ['page', 'button', 'cookie', 'icon'],
      interactions: [
        'Checkbox row toggles default ⇄ checkboxUnticked (toggleOn / toggleOff haptic). Arriving from S-ONB-03 lands on checkboxUnticked: it is never pre-ticked.',
        '“Continue” (enabled only when ticked) → S-ONB-05; stores the accepted terms version and date locally (terms_accepted).',
        'Terms of Use / Privacy Policy → driveperf.com/terms and /privacy (S-WEB-03 / S-WEB-04) in an in-app browser (SFSafariViewController / Custom Tab). Prototype: a toast.',
        '“I’m under 18” → under18Blocked (only a local flag is stored, F-ONB-02.2). “I tapped this by mistake” → checkboxUnticked.',
      ],
      motion: ['The 18+ badge pops (spring.bouncy). The checkbox tick draws in with spring.fast; the Continue button fades from 38% to 100% (spring.effects).', 'Blocked state: the muted Loop draws on once (1100 ms).', 'RM: no pop, 220 ms cross-fade; the tick appears without the draw.'],
      a11y: ['Checkbox: “I’m 18 or older, checkbox, not checked”. Disabled Continue label: “Continue, unavailable until you confirm you’re 18 or older”.', 'Links: “Terms of Use, opens in browser”, “Privacy Policy, opens in browser”.', SPEC_A11Y_COMMON],
      platformDiffs: { ios: 'iOS has no native checkbox: a full-row Button with a circular check symbol and .accessibilityAddTraits(.isToggle) / accessibilityValue “checked”.', android: 'M3 Checkbox + clickable row (Modifier.toggleable, Role.Checkbox). Under-18 uses the same full-screen destination with back disabled.' },
      analytics: ['onboarding_step_viewed {step: age}', 'age_gate_passed', 'age_gate_blocked', 'terms_accepted {terms_version}'],
      copy: { Title: 'One quick check', Lead: 'DrivePerf is for drivers 18 and over. No date of birth needed: you just confirm it.', Checkbox: 'I’m 18 or older', Terms: 'By continuing you agree to the Terms of Use and Privacy Policy. We save the version you accepted, on this phone.', Under18: 'I’m under 18', 'Blocked title': 'DrivePerf is for drivers 18 and over for now', 'Blocked text': 'Thanks for checking it out. Nothing about you was saved, apart from a note on this phone so this screen shows next time.' },
      freePro: 'n/a (Free).',
      data: 'No fixture data. user.ageConfirmed (true for Marcus) is the stored flag.',
      icons: ['check'],
      assets: [],
    },
  });

  // ======================================================================================================
  // S-ONB-05 · How capture works (honest promise)
  // ======================================================================================================
  function promiseList(ctx) {
    return checks([
      { kind: 'yes', text: 'Auto-detects most drives.', sub: 'It notices when you set off and decodes the drive when you park.' },
      { kind: 'est', text: 'The first few hundred metres may be estimated.', sub: 'Estimated parts are drawn dashed and marked ≈.' },
      ctx.ios ? { kind: 'pause', text: 'Force-quitting pauses auto-detect.', sub: 'Swiping DrivePerf away stops it noticing drives until you open it again.' }
        : { kind: 'pause', text: 'Force-stopping the app pauses auto-detect.', sub: 'Until you open DrivePerf again.' },
      ctx.android ? { kind: 'sleep', text: 'Some phone brands put apps to sleep.', sub: 'We’ll help you check.' } : null,
      { kind: 'battery', text: COPY.battery },
    ], { cls: 'onb-promise', label: 'What automatic capture can and can’t do' });
  }
  function howItWorks(ctx) {
    const title = `<div class="onb-ls-h"><h2>How it works</h2>${ctx.ios ? UI.iconButton('close', { state: 'default', label: 'Close', glass: true, cls: 'onb-ls-x' }) : ''}</div>`;
    const auto = UI.card(`<div class="onb-hw"><span class="onb-hw-ic">${UI.icon('bolt', 20)}</span><div><b>Automatic trips</b><p>DrivePerf uses motion and location to notice when you start driving, records in the background, and decodes the drive when you park. Needs location ${ctx.ios ? '“Always”' : '“Allow all the time”'}.</p></div></div>`);
    const manual = UI.card(`<div class="onb-hw"><span class="onb-hw-ic">${UI.icon('record', 20)}</span><div><b>Manual trips</b><p>Tap Start before you set off and Stop when you park: in the app, from ${ctx.ios ? 'Control Center' : 'Quick Settings'} or the widget. Needs location only while recording.</p></div></div>`);
    const honest = checks([
      { kind: 'est', text: 'Auto trips can start a little late', sub: 'GPS needs a moment, so the first few hundred metres may be estimated.' },
      { kind: 'pause', text: ctx.ios ? 'Force-quit pauses auto-detect' : 'Force-stop pauses auto-detect', sub: ctx.ios ? 'Low Power Mode can delay starts too.' : 'Battery Saver can delay starts too.' },
      { kind: 'yes', text: 'Nothing moves while you drive', sub: 'No score, no alerts: the screen stays still until you park.' },
    ]);
    return localSheet(ctx, `${title}<p class="onb-ls-lead">Two ways to record. You can switch between them anytime in Settings.</p><div class="ui-stack" style="gap:10px">${auto}${manual}</div><h3 class="onb-ls-sub">Good to know</h3>${honest}<div class="onb-ls-f">${UI.button('More on driveperf.com', { kind: 'text', size: 's', trailingIcon: 'chevronRight', toast: 'Opens driveperf.com/how-it-works in an in-app browser' })}</div>${ctx.android ? UI.button('Done', { kind: 'tonal', size: 'l', block: true, state: 'default' }) : ''}`, { label: 'How it works' });
  }
  screen('S-ONB-05', {
    name: 'How capture works (honest promise)',
    wp: 'A', platforms: ['ios', 'android'], presentation: 'push', tab: null, backTo: 'S-ONB-04',
    states: [{ id: 'default', label: 'Def' }, { id: 'howItWorksSheetOpen', label: '“How it works” sheet open' }],
    render(ctx) {
      return onbPage(ctx, {
        art: `<div class="onb-art-route is-wide">${routeArt(260, 132, { estimated: true, pad: 14, grid: true })}<span class="onb-est-chip">${I.provenance('estimated', { size: 12 })}Estimated start</span></div>`,
        title: 'How capture works', lead: 'Here’s what automatic trips can and can’t do, before you decide.',
        body: promiseList(ctx) + `<div class="onb-inline">${UI.button('How it works', { kind: 'text', size: 's', icon: 'info', state: 'howItWorksSheetOpen', action: 'learn' })}</div>`,
        primary: { text: 'Continue', nav: 'S-ONB-06' },
        overlay: ctx.state === 'howItWorksSheetOpen' ? howItWorks(ctx) : '',
      });
    },
    mount(el, ctx) {
      const c = stepMount(el, ctx, () => drawRoute(ctx, el, { duration: 1600, delay: 150 }));
      const sh = el.querySelector('.onb-lsheet');
      if (sh && !still(ctx, el)) { ctx.motion.animate(sh, [{ transform: 'translateY(100%)' }, { transform: 'none' }], { preset: 'sheet' }); el.querySelector('.onb-scrim').animate([{ opacity: 0 }, { opacity: 1 }], { duration: 260 }); }
      return c;
    },
    actions: { learn: () => true },
    spec: {
      purpose: 'The honest capture promise before any permission (F-ONB-03, Gate 1 #10): what automatic capture can and can’t do, in the binding words, so nobody is surprised later.',
      layout: [
        'Top bar: Back · step progress (2 of 9 / 2 of 11).',
        'Art (260 × 132): the example route on a faint grid, its first segment dashed (--dp-est-dash 4/4 at 60%) with an “≈ Estimated start” chip (provenance estimated glyph), the rest solid Glide.',
        'Title “How capture works”, lead.',
        'Promise card (one card, rows 16 padding, 0.5 separators inset 56): glyph disc 28 + bold line + supporting line: ✓ “Auto-detects most drives.” · ≈ (dashed ring) “The first few hundred metres may be estimated.” · ⏸ “Force-quitting pauses auto-detect.” (Android: “Force-stopping the app pauses auto-detect.”) · Android only: phone glyph “Some phone brands put apps to sleep.” / “We’ll help you check.” · battery “About 2–4% battery per hour of driving with the screen off.”',
        '“ⓘ How it works” text button under the card → the “How it works” sheet.',
        'Dock: “Continue”.',
        'howItWorksSheetOpen: sheet at the large detent (iOS glass with close button · Android ModalBottomSheet with drag handle and a Done button): “Two ways to record” → Automatic trips card · Manual trips card · “Good to know” checklist · “More on driveperf.com ›” (S-WEB-06).',
      ],
      components: ['page', 'button', 'card', 'sheet', 'provenance'],
      interactions: ['“Continue” → S-ONB-06.', '“How it works” → howItWorksSheetOpen (capture_promise_learn_more). Close / Done / scrim tap → default.', '“More on driveperf.com” → S-WEB-06 in an in-app browser (prototype: toast).'],
      motion: ['Hero motion: the dashed estimated start draws first, then the solid route takes over (1600 ms total, ease.drawOn, glow head); the start dot and end pin pop (spring, overshoot).', 'Sheet: slides up with spring.sheet, scrim fades 260 ms.', 'RM: final route; sheet and page cross-fade 220 ms.'],
      a11y: ['The promise card is a list read in order; the estimated row reads “The first few hundred metres may be estimated. Estimated parts are drawn dashed and marked approximately.”', 'Sheet moves focus to “How it works” on open and returns it to the button on close.', SPEC_A11Y_COMMON],
      platformDiffs: { ios: '“Force-quitting pauses auto-detect.” with the swipe-away explanation. Sheet: .presentationDetents([.large]) with a glass close button.', android: '“Force-stopping the app pauses auto-detect.” plus the OEM line “Some phone brands put apps to sleep. We’ll help you check.” (F-ONB-03.2). Sheet: ModalBottomSheet with drag handle and a tonal Done.' },
      analytics: ['capture_promise_viewed', 'capture_promise_learn_more'],
      copy: { Promise: COPY.promise, Battery: COPY.battery, 'Android force-stop': 'Force-stopping the app pauses auto-detect.', 'Android OEM': 'Some phone brands put apps to sleep. We’ll help you check.', 'Sheet lead': 'Two ways to record. You can switch between them anytime in Settings.' },
      freePro: 'n/a (Free).',
      data: 'Promise text = DP_DATA.health.presets.allGood.promise (split into its three sentences). Art = smoothed glyph of t20261105-0742 with the first two segments drawn as the estimated start (illustrative; quality.estimatedStartM is 428 m on that drive).',
      icons: ['check', 'pause', 'battery', 'phone', 'info', 'bolt', 'record', 'close', 'chevronRight'],
      assets: [],
    },
  });

  // ======================================================================================================
  // S-ONB-06 · Location primer (While Using)
  // ======================================================================================================
  function locationArt(off) {
    const pin = `<g class="onb-route-pin"><circle class="onb-pin-halo" cx="158" cy="44" r="16"/><path class="onb-pin" d="M158 62s-12-11.4-12-20.2a12 12 0 0 1 24 0C170 50.6 158 62 158 62z"/><circle class="onb-pin-dot" cx="158" cy="41.6" r="4.2"/></g>`;
    return `<div class="onb-art-route ${off ? 'is-off' : ''}">${routeArt(220, 132, { pad: 16, grid: true, extra: off ? '' : pin })}${off ? `<span class="onb-off-badge">${UI.icon('gps', 22)}<i></i></span>` : ''}</div>`;
  }
  screen('S-ONB-06', {
    name: 'Location primer (While Using)',
    wp: 'A', platforms: ['ios', 'android'], presentation: 'push', tab: null, backTo: 'S-ONB-05',
    states: [{ id: 'default', label: 'Def' }, { id: 'systemPromptMock', label: 'system prompt mock' }, { id: 'denied', label: 'denied → S-ONB-16' }],
    render(ctx) {
      const denied = ctx.state === 'denied';
      const overlay = ctx.state === 'systemPromptMock' ? sysOverlay('location', { highlight: ctx.ios ? 1 : 0 }, [
        { text: ctx.ios ? 'Allow While Using App' : 'While using the app', primary: true, action: 'granted' },
        { text: 'Approximate only', action: 'approx' },
        { text: ctx.ios ? 'Don’t Allow' : 'Don’t allow', action: 'deny' },
      ]) : '';
      return onbPage(ctx, {
        art: locationArt(denied),
        title: denied ? 'Location is off' : 'Draw the route of every drive',
        lead: denied ? 'Without location, DrivePerf can’t record drives, automatic or manual. You can turn it on in Settings anytime.' : 'DrivePerf uses your location while you use the app to record each drive’s route, and to place braking and cornering on the road.',
        body: denied ? resultLine('off', ctx.ios ? 'Location: Never' : 'Location: Don’t allow') : checks([
          { kind: 'yes', text: 'Route, distance and stops for every drive' },
          { kind: 'yes', text: 'Processed on your phone' },
          { kind: 'no', text: COPY.neverSold },
        ], { label: 'What location is used for' }) + `<p class="onb-fine">You choose about automatic trips in two steps.</p>`,
        primary: denied ? { text: ctx.ios ? 'Open Settings' : 'Open settings', nav: 'S-ONB-16/settingsDeepLink' } : { text: 'Continue', state: 'systemPromptMock', label: 'Continue to the location prompt' },
        secondary: denied ? { text: 'Continue without location', nav: 'S-ONB-16' } : { text: 'Not now', nav: 'S-ONB-16' },
        overlay,
      });
    },
    mount(el, ctx) { return stepMount(el, ctx, () => { const c = drawRoute(ctx, el, { duration: 1500, delay: 120 }); const halo = el.querySelector('.onb-pin-halo'); if (halo && !still(ctx, el)) halo.animate([{ transform: 'scale(.4)', opacity: 0.9 }, { transform: 'scale(1.6)', opacity: 0 }], { duration: 900, delay: 1700, easing: 'cubic-bezier(.32,.72,0,1)', transformOrigin: 'center', transformBox: 'fill-box', fill: 'both' }); return c; }); },
    actions: {
      granted: (ctx) => { ctx.haptic('success'); ctx.nav('S-ONB-07', carry(ctx)); return false; },
      approx: (ctx) => stateThenSheet(ctx, 'default', 'S-ONB-11'),
      deny: (ctx) => stateThenSheet(ctx, 'denied', 'S-ONB-16'),
    },
    spec: {
      purpose: 'Progressive location, step 1 (F-ONB-04.1): explain why before the system While-Using / When-In-Use prompt; the primer always shows first.',
      layout: [
        'Top bar: Back · step progress (3 of 9 / 3 of 11).',
        'Art 220 × 132: route on a faint grid with a location pin (Glide, white dot) and its halo.',
        'Title “Draw the route of every drive”, lead with the purpose (PIPEDA meaningful consent: what, why).',
        'Checklist: ✓ Route, distance and stops for every drive · ✓ Processed on your phone · ✕ Never sold, never shared with insurers. Fine print: “You choose about automatic trips in two steps.”',
        'Dock: “Continue” (triggers the system prompt) · secondary “Not now”.',
        'systemPromptMock: the primer dims to 50% and the OS prompt mock sits centred (iOS: “Allow “DrivePerf” to use your location?” with the purpose string, map with “Precise: On”, Allow Once / Allow While Using App (highlighted) / Don’t Allow · Android 16: precise/approximate picker, While using the app (highlighted) / Only this time / Don’t allow). Non-interactive; a “System prompt · mock” tag.',
        'denied: art greys out with a slashed GPS badge, title “Location is off”, honest line, a status row “Location: Never” (Android “Don’t allow”), primary “Open Settings” (→ S-ONB-16 settingsDeepLink), secondary “Continue without location” (→ S-ONB-16).',
      ],
      components: ['page', 'button', 'sysPermission', 'icon'],
      interactions: [
        '“Continue” → systemPromptMock (the real app calls requestWhenInUseAuthorization / ACCESS_FINE_LOCATION + COARSE).',
        'Prompt results (prototype “pick the result” bar, hidden in export shots): While using → S-ONB-07 · Approximate only → S-ONB-11 sheet over this screen · Don’t allow → denied + S-ONB-16 sheet.',
        '“Not now” → S-ONB-16 (location denied). denied: “Open Settings” → S-ONB-16/settingsDeepLink · “Continue without location” → S-ONB-16.',
      ],
      motion: ['Hero motion: route draw-on (1500 ms) then the pin pops and its halo pulses once (scale .4 → 1.6, fade, 900 ms ease.glide).', 'System prompt: system motion (prototype: none).', 'RM: final art, 220 ms cross-fade.'],
      a11y: ['Primary label: “Continue to the location prompt”. The prompt mock is hidden from assistive tech (the real OS prompt is accessible).', 'Denied status row reads “Location: Never”.', SPEC_A11Y_COMMON],
      platformDiffs: { ios: 'requestWhenInUseAuthorization() via CLLocationManager; NSLocationWhenInUseUsageDescription = “DrivePerf uses your location to record the route of your drives.” The precise toggle lives in the prompt; choosing approximate leads to S-ONB-11.', android: 'ActivityResultContracts.RequestMultiplePermissions(FINE + COARSE). The Android 12+ dialog offers Precise / Approximate; Approximate → S-ONB-11. “Not now” never triggers the dialog.' },
      analytics: ['permission_primer_viewed {perm: location_when_in_use}', 'permission_result {perm: location_when_in_use, result: granted|approximate|denied}'],
      copy: { Title: 'Draw the route of every drive', Lead: 'DrivePerf uses your location while you use the app to record each drive’s route, and to place braking and cornering on the road.', 'Denied title': 'Location is off', 'Denied lead': 'Without location, DrivePerf can’t record drives, automatic or manual. You can turn it on in Settings anytime.', Primary: 'Continue', Secondary: 'Not now' },
      freePro: 'Free. Pro never depends on any location grant (Apple 5.1.1).',
      data: 'Art: smoothed glyph of t20261105-0742. System prompt copy: DP_UI.sysPermission(location) defaults.',
      icons: ['check', 'close', 'gps'],
      assets: [],
    },
  });

  // ======================================================================================================
  // S-ONB-07 · Motion & activity primer
  // ======================================================================================================
  function ggArt(off) {
    const t = COMMUTE(), S = 66 / 0.6, c = 80;
    const cloud = t.gg.points.filter((_, i) => i % 2 === 0).slice(0, 220).map(([la, lo]) => `<circle cx="${f2(c + Math.max(-0.6, Math.min(0.6, la)) * S)}" cy="${f2(c - Math.max(-0.6, Math.min(0.6, lo)) * S)}" r="1.5"/>`).join('');
    return `<div class="onb-gg ${off ? 'is-off' : ''}"><svg viewBox="0 0 160 160" width="168" height="168" aria-hidden="true">
      <g class="onb-gg-ring">${[0.2, 0.4, 0.6].map((g) => `<circle cx="${c}" cy="${c}" r="${f2(g * S)}"/>`).join('')}<path d="M${c - 70} ${c}H${c + 70}M${c} ${c - 70}V${c + 70}"/></g>
      <g class="onb-gg-cloud">${cloud}</g>
      <g class="onb-gg-dot"><circle class="onb-gg-halo" cx="${c}" cy="${c}" r="11"/><circle cx="${c}" cy="${c}" r="5.5"/></g></svg>
      <span class="onb-gg-l is-t">Accel</span><span class="onb-gg-l is-b">Brake</span>${off ? `<span class="onb-off-badge">${UI.icon('drives', 22)}<i></i></span>` : ''}</div>`;
  }
  screen('S-ONB-07', {
    name: 'Motion & activity primer',
    wp: 'A', platforms: ['ios', 'android'], presentation: 'push', tab: null, backTo: 'S-ONB-06',
    states: [{ id: 'default', label: 'Def' }, { id: 'systemPromptMock', label: 'system prompt mock' }, { id: 'denied', label: 'denied' }],
    render(ctx) {
      const denied = ctx.state === 'denied', perm = ctx.ios ? 'Motion & Fitness' : 'Physical activity';
      const overlay = ctx.state === 'systemPromptMock' ? sysOverlay('motion', { highlight: ctx.ios ? 1 : 0 }, [
        { text: 'Allow', primary: true, action: 'granted' },
        { text: ctx.ios ? 'Don’t Allow' : 'Don’t allow', action: 'deny' },
      ]) : '';
      return onbPage(ctx, {
        art: ggArt(denied),
        title: denied ? `${perm} is off` : 'Know when you’re driving',
        lead: denied ? 'Automatic trips can still run on location alone, with more false trips and slower starts. You can turn it on in Settings anytime.'
          : `${perm} lets DrivePerf tell driving from walking, so drives start sooner and false trips stay rare.`,
        body: denied ? resultLine('off', `${perm}: ${ctx.ios ? 'Not allowed' : 'Don’t allow'}`) : checks([
          { kind: 'yes', text: 'Fewer false trips', sub: 'Walks, runs, bikes and transit are left out.' },
          { kind: 'yes', text: 'Activity data stays on your phone' },
        ], { label: 'What motion access is used for' }),
        primary: denied ? { text: 'Continue', nav: 'S-ONB-08', params: carry(ctx) } : { text: 'Continue', state: 'systemPromptMock', label: `Continue to the ${perm} prompt` },
        secondary: denied ? { text: ctx.ios ? 'Open Settings' : 'Open settings', toast: ctx.ios ? 'Opens Settings › DrivePerf › Motion & Fitness' : 'Opens App info › Permissions › Physical activity' } : null,
        overlay,
      });
    },
    mount(el, ctx) {
      return stepMount(el, ctx, () => {
        const dot = el.querySelector('.onb-gg-dot');
        if (!dot || still(ctx, el) || ctx.state === 'denied') return;
        const pts = COMMUTE().gg.points.filter((_, i) => i % 6 === 0).slice(0, 36), S = 66 / 0.6;
        const f = ctx.motion.follower({ zeta: 0.55, k: 120 });
        let t0 = 0, last = 0, raf = 0;
        const dur = 2800;
        const step = (now) => {
          if (!t0) { t0 = now; last = now; }
          const dt = Math.min(0.05, (now - last) / 1000); last = now;
          const p = (now - t0 - 300) / dur;
          if (p > 0 && p < 1) { const q = pts[Math.min(pts.length - 1, Math.floor(p * pts.length))]; f.target(q[0], -q[1]); } else f.target(0, 0);
          const s = f.step(dt);
          dot.setAttribute('transform', `translate(${f2(Math.max(-66, Math.min(66, s.x * S)))} ${f2(Math.max(-66, Math.min(66, s.y * S)))})`);
          if (p < 1.5) raf = requestAnimationFrame(step);
        };
        raf = requestAnimationFrame(step);
        return () => cancelAnimationFrame(raf);
      });
    },
    actions: {
      granted: (ctx) => { ctx.haptic('success'); ctx.nav('S-ONB-08', carry(ctx)); return false; },
      deny: (ctx) => { ctx.setState('denied'); return false; },
    },
    spec: {
      purpose: 'Ask for Motion & Fitness (iOS) / Physical activity (Android) with the benefit stated first (F-ONB-05): fewer false trips and faster starts.',
      layout: [
        'Top bar: Back · step progress (4 of 9 / 4 of 11).',
        'Art: the friction circle (components.md §6) 168: rings at 0.2/0.4/0.6 g (--dp-data-gg-ring), axes, the example drive’s G cloud at 30% (--dp-data-gg-cloud) and the G-dot (8 px with halo, --dp-data-gg-dot); “Accel” / “Brake” labels.',
        'Title “Know when you’re driving”, lead names the permission exactly (“Motion & Fitness” on iOS, “Physical activity” on Android).',
        'Checklist: ✓ Fewer false trips (walks, runs, bikes and transit are left out) · ✓ Activity data stays on your phone.',
        'Dock: “Continue” (triggers the prompt). No “Not now”: the OS prompt has its own Don’t Allow.',
        'systemPromptMock: iOS “Would Like to Access Your Motion & Fitness Activity” (Don’t Allow / Allow highlighted) · Android “Allow DrivePerf to access your physical activity?” (Allow highlighted / Don’t allow).',
        'denied: dimmed circle with a slashed badge, title “Motion & Fitness is off” / “Physical activity is off”, the honest consequence (location-only capture: more false trips, slower starts), status row, primary “Continue” → S-ONB-08, secondary “Open Settings”.',
      ],
      components: ['page', 'button', 'sysPermission', 'gg'],
      interactions: ['“Continue” → systemPromptMock (CMMotionActivityManager query / ACTIVITY_RECOGNITION request).', 'Allow → S-ONB-08 · Don’t allow → denied.', 'denied: “Continue” → S-ONB-08 (automatic trips can still be chosen; Health Check will show “Motion access off: more false trips and slower starts”, F-ONB-05.2) · “Open Settings” → app settings (prototype toast).'],
      motion: ['Hero motion: the G-dot replays the example drive’s inputs for 2.8 s on the gMeter spring (ζ 0.55, k 120, motion.md §3.5) and settles in the centre.', 'RM: static plot, the dot rests at the centre, no travel.'],
      a11y: ['Art is decorative. Primary label names the permission: “Continue to the Motion & Fitness prompt”.', 'Denied status row: “Motion & Fitness: Not allowed”.', SPEC_A11Y_COMMON],
      platformDiffs: { ios: 'NSMotionUsageDescription = “DrivePerf uses motion data to tell when you start and stop driving.” (the app crashes without it; CI checks it, F-ONB-05.3). The prompt appears on the first CMMotionActivityManager query.', android: 'API 29+: runtime ACTIVITY_RECOGNITION (“Physical activity”); API ≤ 28 skips the prompt and this screen auto-advances. Activity Recognition Transition API.' },
      analytics: ['permission_primer_viewed {perm: motion}', 'permission_result {perm: motion, result: granted|denied}'],
      copy: { Title: 'Know when you’re driving', 'Lead iOS': 'Motion & Fitness lets DrivePerf tell driving from walking, so drives start sooner and false trips stay rare.', 'Lead Android': 'Physical activity lets DrivePerf tell driving from walking, so drives start sooner and false trips stay rare.', 'Denied lead': 'Automatic trips can still run on location alone, with more false trips and slower starts. You can turn it on in Settings anytime.' },
      freePro: 'n/a (Free).',
      data: 'G cloud = trips t20261105-0742 gg.points (every 2nd, ≤ 220, clamped ±0.6 g); the dot replays every 6th point (derived, illustrative). No max-G is ever shown (SAF-04).',
      icons: ['check', 'drives'],
      assets: [],
    },
  });

  // ======================================================================================================
  // S-ONB-08 · Automatic trips opt-in + background-location disclosure (Android: Play prominent disclosure)
  // ======================================================================================================
  function autoArt(on) {
    const extra = `<g class="onb-auto-tags"><rect class="onb-auto-tag" x="6" y="98" width="70" height="22" rx="11"/><text x="41" y="113">Starts</text><rect class="onb-auto-tag" x="146" y="6" width="68" height="22" rx="11"/><text x="180" y="21">Parked</text></g>`;
    return `<div class="onb-art-route ${on ? '' : 'is-muted'}">${routeArt(220, 126, { pad: 18, extra })}</div>`;
  }
  screen('S-ONB-08', {
    name: 'Automatic trips opt-in + background-location disclosure',
    wp: 'A', platforms: ['ios', 'android'], presentation: 'push', tab: null, backTo: 'S-ONB-07',
    states: [
      { id: 'default', label: 'Def (toggle off)' },
      { id: 'toggleOn', label: 'toggle on → iOS Always prompt mock' },
      { id: 'declined', label: 'declined' },
    ],
    render(ctx) {
      const st = scratch(ctx, 's08', { on: ctx.state === 'toggleOn' });
      const on = st.on, declined = ctx.state === 'declined';
      const disclosure = `<div class="onb-disc" role="group" aria-label="Background location disclosure">
          <span class="onb-disc-k">${UI.icon('gps', 16)}Background location</span>
          <p class="onb-disc-p">${esc(COPY.disclosure)}</p>
          <dl class="onb-disc-dl"><dt>What</dt><dd>Your location and phone motion, during the drives it detects.</dd><dt>Why</dt><dd>To record each drive’s route and score how smooth it was.</dd><dt>Where</dt><dd>On your phone unless you turn on backup. ${esc(COPY.neverSold)}.</dd></dl>
        </div>`;
      const toggleRow = `<div class="onb-toggle ${on ? 'is-on' : ''}">${UI.row({ icon: 'bolt', title: 'Record drives automatically', sub: on ? (ctx.ios ? 'Next: iOS asks to allow location Always' : 'Next: choose “Allow all the time” in settings') : 'Off until you turn it on', trailing: 'toggle', on, action: 'auto' })}</div>`;
      const overlay = ctx.ios && ctx.state === 'toggleOn' ? sysOverlay('always', { highlight: 1 }, [
        { text: 'Change to Always Allow', primary: true, action: 'always' },
        { text: 'Keep Only While Using', action: 'keep' },
      ]) : '';
      return onbPage(ctx, {
        align: 'start',
        art: autoArt(on),
        title: 'Automatic trips',
        lead: 'Record every drive without opening the app. Or keep it manual: tap Start when you set off.',
        body: (declined ? UI.banner({ kind: 'info', icon: 'drives', title: 'Manual trips it is', text: ctx.ios ? 'Location stays “While Using”. Start drives yourself anytime, and switch automatic trips on later in Settings.' : 'Location stays “While using the app”. Start drives yourself anytime, and switch automatic trips on later in Settings.' }) : '') + disclosure + toggleRow
          + `<p class="onb-fine">${ctx.ios ? 'You can change this anytime in Settings › DrivePerf › Location.' : 'Android asks for this as a separate step in settings. You can change it anytime.'}</p>`,
        primary: on ? { text: ctx.android ? 'Continue to settings' : 'Continue', action: 'next', label: ctx.android ? 'Continue to Android location settings' : 'Continue to the Always prompt' }
          : { text: 'Continue with manual trips', nav: 'S-ONB-10', params: carry(ctx, { auto: 'off' }) },
        overlay,
      });
    },
    mount(el, ctx) { return stepMount(el, ctx, () => drawRoute(ctx, el, { duration: 1500, delay: 100 })); },
    actions: {
      auto: (ctx) => { const st = scratch(ctx, 's08', {}); st.on = !st.on; ctx.haptic(st.on ? 'toggleOn' : 'toggleOff'); ctx.update(); return false; },
      next: (ctx) => { if (ctx.ios) ctx.setState('toggleOn'); else ctx.nav('S-ONB-09', carry(ctx, { auto: 'on' })); return false; },
      always: (ctx) => { ctx.haptic('success'); ctx.nav('S-ONB-10', carry(ctx, { auto: 'on' })); return false; },
      keep: (ctx) => stateThenSheet(ctx, 'declined', 'S-ONB-16/alwaysDeclined'),
    },
    spec: {
      purpose: 'The opt-in for automatic trips and the background-location disclosure (F-ONB-04.2–.4, Law 25 off-by-default, Play prominent disclosure, Apple one-time Always request). Automatic trips stay off until the user switches them on.',
      layout: [
        'Top bar: Back · step progress (5 of 9 / 5 of 11). Start-aligned text on this screen (it is a disclosure, read top to bottom).',
        'Art 220 × 126: the example route with “Starts” and “Parked” tags (dimmed until the toggle is on).',
        'Title “Automatic trips” + lead offering both choices.',
        'Disclosure card (--card-bg, radius card, Glide 1 pt inner outline): “BACKGROUND LOCATION” kicker with GPS glyph; the disclosure sentence in body weight: “DrivePerf collects location data to enable automatic trip recording, even when the app is closed or not in use.”; then a What / Why / Where definition list.',
        'Toggle row “Record drives automatically” (bolt glyph), supporting text “Off until you turn it on” → when on: what happens next (iOS Always prompt / Android settings step).',
        'Fine print on where to change it later.',
        'Dock: toggle off → “Continue with manual trips” · toggle on → “Continue” (iOS) / “Continue to settings” (Android).',
        'toggleOn (iOS): the Always prompt mock “Allow “DrivePerf” to also use your location even when you are not using the app?” with the purpose string; “Change to Always Allow” highlighted. Android toggleOn: no OS dialog (Android 11+ sends the user to settings), the primary reads “Continue to settings”.',
        'declined: toggle off and an info banner “Manual trips it is” explaining location stays While Using and how to turn automatic trips on later.',
      ],
      components: ['page', 'row', 'toggle', 'banner', 'button', 'sysPermission'],
      interactions: [
        'Toggle → on/off (toggleOn / toggleOff haptic); auto_trips_toggled {on}.',
        'Toggle off + “Continue with manual trips” → S-ONB-10 (manual mode, auto=off).',
        'iOS: toggle on + “Continue” → toggleOn (requestAlwaysAuthorization, requested exactly once, never again in-app). “Change to Always Allow” → S-ONB-10 · “Keep Only While Using” → declined + S-ONB-16 alwaysDeclined sheet.',
        'Android: toggle on + “Continue to settings” → S-ONB-09.',
      ],
      motion: ['Hero motion: route draw-on (1500 ms) with the glow head; the tags fade in with the end dot. Toggling brightens the art (opacity .45 → 1, spring.effects).', 'RM: static art, 220 ms cross-fade.'],
      a11y: ['The disclosure card is one group labelled “Background location disclosure” and is read before the toggle.', 'Toggle: “Record drives automatically, switch, off. Off until you turn it on.”', 'Primary when on (Android): “Continue to Android location settings”.', SPEC_A11Y_COMMON],
      platformDiffs: {
        ios: 'Same disclosure wording; requestAlwaysAuthorization() after When In Use (A3 §1.2), only once. NSLocationAlwaysAndWhenInUseUsageDescription = “DrivePerf uses your location in the background only to record the route of drives it detects.”',
        android: 'This is the Google Play prominent disclosure: in-app, shown before any background-location request, uses the word “location”, says “even when the app is closed or not in use”, names the feature (automatic trip recording), needs an affirmative action (toggle on + Continue), and must match the Play listing and the declaration video (PRD §6.3). ACCESS_BACKGROUND_LOCATION is then granted in Settings (S-ONB-09).',
      },
      analytics: ['permission_primer_viewed {perm: location_always}', 'auto_trips_toggled {on}', 'permission_result {perm: location_always, result: granted|denied}'],
      copy: { Title: 'Automatic trips', Lead: 'Record every drive without opening the app. Or keep it manual: tap Start when you set off.', Disclosure: COPY.disclosure, What: 'Your location and phone motion, during the drives it detects.', Why: 'To record each drive’s route and score how smooth it was.', Where: 'On your phone unless you turn on backup. ' + COPY.neverSold + '.', Toggle: 'Record drives automatically', 'Toggle sub': 'Off until you turn it on', 'Declined banner': 'Manual trips it is. Location stays “While Using”. Start drives yourself anytime, and switch automatic trips on later in Settings.' },
      freePro: 'Free. No Pro feature depends on Always (Apple 5.1.1).',
      data: 'Art: smoothed glyph of t20261105-0742. Disclosure text is fixed copy (legal review before launch, PRD §6.3).',
      icons: ['gps', 'bolt', 'drives'],
      assets: [],
    },
  });

  // ======================================================================================================
  // S-ONB-09 · "Allow all the time" settings step (Android only)
  // ======================================================================================================
  const androidLocSettings = (granted, hi = true) => ({
    title: 'Location permission', back: '',
    groups: [
      { header: 'Location access for this app', rows: [
        { label: 'Allow all the time', radio: true, on: granted, hi },
        { label: 'Allow only while using the app', radio: true, on: !granted },
        { label: 'Ask every time', radio: true },
        { label: 'Don’t allow', radio: true },
      ] },
      { rows: [{ label: 'Use precise location', sub: 'Lets the app use your exact location', toggle: true }], footer: 'DrivePerf: records the route of drives it detects automatically.' },
    ],
  });
  screen('S-ONB-09', {
    name: '“Allow all the time” settings step',
    wp: 'A', platforms: ['android'], presentation: 'push', tab: null, backTo: 'S-ONB-08',
    states: [
      { id: 'default', label: 'Def' },
      { id: 'settingsOpen', label: 'Settings page (mock)' },
      { id: 'returnedGranted', label: 'returned granted' },
      { id: 'returnedNotGranted', label: 'returned not granted' },
    ],
    render(ctx) {
      const st = ctx.state;
      if (st === 'returnedGranted') {
        return onbPage(ctx, {
          art: `<div class="onb-done">${loopSvg(120)}<span class="onb-done-ck">${UI.icon('check', 26)}</span></div>`,
          title: 'Automatic trips are on', lead: 'Location is set to “Allow all the time”. DrivePerf will notice your next drive on its own.',
          body: resultLine('ok', 'Location: Allow all the time'),
          primary: { text: 'Continue', nav: 'S-ONB-10', params: carry(ctx, { auto: 'on' }) },
        });
      }
      const notGranted = st === 'returnedNotGranted';
      const steps = `<ol class="onb-steps"><li><b>1</b><span>Tap <em>Open settings</em> below.</span></li><li><b>2</b><span>Choose <em>Allow all the time</em>.</span></li><li><b>3</b><span>Come back here. We’ll check right away.</span></li></ol>`;
      return onbPage(ctx, {
        art: `<div class="onb-guide">${settingsShot(ctx, androidLocSettings(false), { h: ctx.xl ? 210 : 250, scale: 0.56 })}<span class="onb-guide-tap" aria-hidden="true"></span></div>`,
        title: notGranted ? 'Location is still “While using the app”' : 'One more step in settings',
        lead: notGranted ? 'Automatic trips stay off for now. Manual trips work, and you can try again anytime.' : 'Android asks for background location in settings. Choose “Allow all the time” so DrivePerf can record drives with the app closed.',
        body: (notGranted ? resultLine('off', 'Location: Allow only while using the app') : '') + steps,
        primary: { text: notGranted ? 'Try again' : 'Open settings', state: 'settingsOpen', icon: 'gear', label: 'Open settings: DrivePerf location permission' },
        secondary: { text: 'Continue with manual trips', nav: 'S-ONB-10', params: carry(ctx, { auto: 'off' }) },
        overlay: st === 'settingsOpen' ? UI.sysSettings(androidLocSettings(false)) + sim([
          { text: 'Allow all the time', primary: true, action: 'granted' },
          { text: 'Back, no change', action: 'notGranted' },
        ]) : '',
      });
    },
    mount(el, ctx) {
      return stepMount(el, ctx, () => {
        if (ctx.state === 'returnedGranted') { drawLoop(ctx, el, { duration: 1100 }); const ck = el.querySelector('.onb-done-ck'); if (ck && !still(ctx, el)) ctx.motion.animate(ck, [{ opacity: 0, transform: 'scale(.5)' }, { opacity: 1, transform: 'scale(1)' }], { preset: 'bouncy', delay: 900 }); return; }
        const tap = el.querySelector('.onb-guide-tap'); if (tap && !still(ctx, el)) tap.animate([{ transform: 'scale(.6)', opacity: 0 }, { transform: 'scale(1)', opacity: 0.9, offset: 0.3 }, { transform: 'scale(1.5)', opacity: 0 }], { duration: 1200, delay: 500, iterations: 2, easing: 'cubic-bezier(.32,.72,0,1)' });
      });
    },
    actions: {
      granted: (ctx) => { ctx.haptic('success'); ctx.setState('returnedGranted'); return false; },
      notGranted: (ctx) => { ctx.setState('returnedNotGranted'); return false; },
    },
    spec: {
      purpose: 'Android 11+ grants background location only in Settings (F-ONB-04.4): explain the separate “Allow all the time” step with a screenshot-style guide, deep-link to the app’s location permission page, and detect the result within 1 s of returning.',
      layout: [
        'Top bar: back arrow · step progress (6 of 11).',
        'Guide art: a 230 × 250 rounded frame (radius 24, outline-variant) holding a scaled mock of the system “Location permission” page with “Allow all the time” highlighted (Glide outline) and a tap ripple on it.',
        'Title “One more step in settings”, lead.',
        'Numbered steps 1–3 (number discs 28, primaryContainer): Tap Open settings · Choose Allow all the time · Come back here.',
        'Dock: filled “Open settings” (gear icon) · TextButton “Continue with manual trips”.',
        'settingsOpen: the Android Settings page mock full-screen (Location permission: Allow all the time / Allow only while using the app (selected) / Ask every time / Don’t allow · Use precise location) — non-interactive, “Android Settings · mock” tag.',
        'returnedGranted: Loop draws on with a check badge, title “Automatic trips are on”, status row, “Continue” (the real app auto-advances after 1 s).',
        'returnedNotGranted: title “Location is still “While using the app””, status row, the same steps; primary “Try again”, secondary “Continue with manual trips”.',
      ],
      components: ['page', 'sysSettings', 'button', 'icon'],
      interactions: [
        '“Open settings” / “Try again” → Settings deep link (Intent ACTION_APPLICATION_DETAILS_SETTINGS → app permission page; on API 30+ requestPermissions(ACCESS_BACKGROUND_LOCATION) opens the same page). Prototype: settingsOpen + “pick the result” bar.',
        'On return (onResume, checked within 1 s): granted → returnedGranted → S-ONB-10 (auto=on) · not granted → returnedNotGranted.',
        '“Continue with manual trips” → S-ONB-10 (auto=off). Back → S-ONB-08.',
      ],
      motion: ['Hero motion: a tap ripple pulses twice on the highlighted option in the guide (scale .6 → 1.5, fade, 1200 ms ease.glide).', 'returnedGranted: the Loop draws on (1100 ms) and the check badge pops (spring.bouncy) at 900 ms; haptic success.', 'RM: static guide; Loop and check shown final.'],
      a11y: ['Guide art is hidden; the numbered steps carry the same information as text.', 'Primary label: “Open settings: DrivePerf location permission”.', 'Result rows: “Location: Allow all the time” / “Location: Allow only while using the app”.', SPEC_A11Y_COMMON],
      platformDiffs: { ios: 'Not shown on iOS (the Always prompt in S-ONB-08 replaces it).', android: 'Android-only. API 29: the background option appears in the runtime dialog instead (this screen is skipped). Permission auto-reset for unused apps is flagged by Health Check later.' },
      analytics: ['permission_primer_viewed {perm: location_background}', 'permission_result {perm: location_background, result: granted|denied}'],
      copy: { Title: 'One more step in settings', Lead: 'Android asks for background location in settings. Choose “Allow all the time” so DrivePerf can record drives with the app closed.', Steps: '1 Tap Open settings below. 2 Choose Allow all the time. 3 Come back here. We’ll check right away.', Granted: 'Automatic trips are on. Location is set to “Allow all the time”. DrivePerf will notice your next drive on its own.', 'Not granted': 'Location is still “While using the app”. Automatic trips stay off for now. Manual trips work, and you can try again anytime.' },
      freePro: 'n/a (Free).',
      data: 'Settings labels follow Android 16 (“Allow all the time”, “Allow only while using the app”, “Ask every time”, “Don’t allow”, “Use precise location”); health.android.items[location].label matches.',
      icons: ['gear', 'check'],
      assets: [],
    },
  });

  // ======================================================================================================
  // S-ONB-10 · Notifications primer
  // ======================================================================================================
  function notifArt(ctx, off) {
    const N = D.notifications, pick = (id) => N.find((n) => n.id === id);
    const items = [pick('n_decoded'), pick('n_capture'), pick('n_week')];
    const loop = I.loop({ size: 26, color: '#5BE7C4', dot: '#fff' });
    return `<div class="onb-ntfs ${off ? 'is-off' : ''}">${items.map((n, i) => `<div class="onb-ntf" style="--i:${i}"><span class="onb-ntf-ic">${loop}</span><div><b>${esc(n.title)}</b><p>${esc(n.body)}</p></div><time>${i === 0 ? 'now' : i === 1 ? '2h ago' : 'Mon'}</time></div>`).join('')}${off ? `<span class="onb-off-badge">${UI.icon('bell', 22)}<i></i></span>` : ''}</div>`;
  }
  screen('S-ONB-10', {
    name: 'Notifications primer',
    wp: 'A', platforms: ['ios', 'android'], presentation: 'push', tab: null, backTo: 'S-ONB-08',
    states: [{ id: 'default', label: 'Def' }, { id: 'systemPromptMock', label: 'system prompt mock' }, { id: 'denied', label: 'denied' }],
    render(ctx) {
      const denied = ctx.state === 'denied';
      const overlay = ctx.state === 'systemPromptMock' ? sysOverlay('notifications', { highlight: ctx.ios ? 1 : 0 }, [
        { text: 'Allow', primary: true, action: 'granted' },
        { text: ctx.ios ? 'Don’t Allow' : 'Don’t allow', action: 'deny' },
      ]) : '';
      const what = checks([
        { kind: 'story', text: 'Drive decoded', sub: 'When you park and your Story is ready.' },
        { kind: 'warn', text: 'Capture problems', sub: 'If auto-detect needs a fix, so you don’t miss drives.' },
        { kind: 'bell', text: 'Weekly recap', sub: 'Once a week.' },
        { kind: 'no', text: 'Never while you drive', sub: 'Anything that arrives mid-drive waits until you park.' },
      ], { label: 'What DrivePerf sends' });
      return onbPage(ctx, {
        art: notifArt(ctx, denied),
        title: denied ? 'Notifications are off' : 'Know when your drive is decoded',
        lead: denied ? 'You won’t hear when a drive is decoded or when capture needs a fix. Your drives still record, and you can turn notifications on in Settings anytime.' : 'Three kinds of notification, and nothing else.',
        body: denied ? resultLine('off', 'Notifications: Off') + (ctx.android ? `<p class="onb-fine">While a drive records, Android still shows the recording notification. It’s required, and it’s how you stop a trip from the shade.</p>` : '') : what,
        primary: denied ? { text: 'Continue', nav: afterNotif(ctx), params: carry(ctx) } : { text: 'Continue', state: 'systemPromptMock', label: 'Continue to the notifications prompt' },
        secondary: denied ? null : { text: 'Not now', nav: afterNotif(ctx), params: carry(ctx) },
        overlay,
      });
    },
    mount(el, ctx) { return stepMount(el, ctx, () => { if (still(ctx, el)) return; el.querySelectorAll('.onb-ntf').forEach((n, i) => ctx.motion.animate(n, [{ opacity: 0, transform: `translateY(${18 + i * 6}px) scale(.96)` }, { opacity: 1, transform: 'none' }], { preset: 'default', delay: 120 + i * 140 })); }); },
    actions: {
      granted: (ctx) => { ctx.haptic('success'); ctx.nav(afterNotif(ctx), carry(ctx)); return false; },
      deny: (ctx) => { ctx.setState('denied'); return false; },
    },
    spec: {
      purpose: 'Ask for notifications by listing exactly what we send (F-ONB-06): drive decoded, capture problems, weekly recap — and “never while you drive”.',
      layout: [
        'Top bar: Back · step progress (6 of 9 / 7 of 11).',
        'Art: three stacked notification previews (radius 22 iOS glass / 16 Android surfaceContainerHigh), Loop app icon, title + body from the fixtures, relative time.',
        'Title “Know when your drive is decoded”, lead “Three kinds of notification, and nothing else.”',
        'Checklist: Story glyph “Drive decoded” · triangle “Capture problems” · bell “Weekly recap” · ✕ “Never while you drive”.',
        'Dock: “Continue” (triggers the prompt) · secondary “Not now” (skips the prompt; ask again later from Settings).',
        'systemPromptMock: iOS “Would Like to Send You Notifications” (Allow highlighted) · Android 13+ “Allow DrivePerf to send you notifications?” (Allow highlighted).',
        'denied: previews dimmed with a slashed bell, honest consequence, status row; Android adds the foreground-service recording-notification note.',
      ],
      components: ['page', 'button', 'sysPermission', 'notification'],
      interactions: ['“Continue” → systemPromptMock (UNUserNotificationCenter.requestAuthorization / POST_NOTIFICATIONS).', 'Allow → next step · Don’t allow → denied · “Not now” → next step without prompting.', 'Next step: Android with automatic trips on → S-ONB-12; otherwise → S-ONB-13.'],
      motion: ['Hero motion: the three previews rise into a stack (translateY 18/24/30 → 0, scale .96 → 1, spring.default, 140 ms stagger).', 'RM: stack shown final, 220 ms cross-fade.'],
      a11y: ['Art is hidden; the checklist states the same three kinds plus “Never while you drive”.', 'Primary label: “Continue to the notifications prompt”.', SPEC_A11Y_COMMON],
      platformDiffs: { ios: 'requestAuthorization([.alert, .sound, .badge]); local notifications only (no push token in v1, §8). Time Sensitive is not requested.', android: 'Android 13+: runtime POST_NOTIFICATIONS. Android ≤ 12: no prompt, this screen is skipped. Denied still shows the foreground-service notification while recording (required). Android 16 Live Update promotion needs POST_PROMOTED_NOTIFICATIONS declared (F-CAP-12).' },
      analytics: ['permission_primer_viewed {perm: notifications}', 'permission_result {perm: notifications, result: granted|denied|skipped}'],
      copy: { Title: 'Know when your drive is decoded', Lead: 'Three kinds of notification, and nothing else.', List: 'Drive decoded · Capture problems · Weekly recap · Never while you drive', 'Denied lead': 'You won’t hear when a drive is decoded or when capture needs a fix. Your drives still record, and you can turn notifications on in Settings anytime.', 'Android FGS note': 'While a drive records, Android still shows the recording notification. It’s required, and it’s how you stop a trip from the shade.' },
      freePro: 'n/a (Free).',
      data: 'Previews = DP_DATA.notifications n_decoded (“Drive decoded.” · “Home → Work · Smoothness 91”), n_capture (“Recording is paused.”), n_week (“Your week, decoded.” · “5 drives · Smoothness 87 (+2)”). Relative times are illustrative.',
      icons: ['story', 'warn', 'bell', 'close'],
      assets: [],
    },
  });

  // ======================================================================================================
  // S-ONB-11 · Precise location sheet
  // ======================================================================================================
  function preciseArt() {
    const t = COMMUTE();
    const fuzzy = `<div class="onb-pc is-approx"><svg viewBox="0 0 120 84" aria-hidden="true"><circle class="onb-pc-blob" cx="60" cy="42" r="30"/><circle class="onb-pc-c" cx="60" cy="42" r="4"/></svg><span>Approximate</span></div>`;
    const sharp = `<div class="onb-pc"><div class="onb-pc-map">${routeArt(120, 84, { pad: 10, ends: false, extra: `<g transform="translate(70 30)">${I.pin('brake', { size: 18 }).replace('<svg', '<svg x="-9" y="-9"')}</g>` })}</div><span>Precise</span></div>`;
    void t;
    return `<div class="onb-pcs">${fuzzy}${sharp}</div>`;
  }
  screen('S-ONB-11', {
    name: 'Precise location sheet',
    wp: 'A', platforms: ['ios', 'android'], presentation: 'sheet', tab: null, over: 'S-ONB-06', detent: 'auto',
    states: [{ id: 'default', label: 'Def' }, { id: 'refused', label: 'refused → “route only” note' }],
    render(ctx) {
      const refused = ctx.state === 'refused';
      const head = UI.sheetHeader(refused ? 'Route only, for now' : 'Turn on precise location?', { icon: `<span class="onb-sh-ic">${UI.icon('gps', 20)}</span>` });
      if (refused) {
        return `${head}<div class="onb-sh">${UI.card(`<div class="onb-ro"><span class="onb-ro-ic">${UI.icon('routes', 22)}</span><div><b>Drives record as “route only, no event placement”.</b><p>Braking, acceleration and cornering are still counted, but not placed on the road.</p></div></div>`)}
          <p class="onb-sh-p">${ctx.ios ? 'Turn on Precise Location anytime in Settings › DrivePerf › Location.' : 'Switch to precise anytime in App info › Permissions › Location.'}</p>
          <div class="onb-sh-acts">${UI.button('Continue', { kind: 'filled', size: 'l', block: true, action: 'done' })}</div></div>`;
      }
      return `${head}<div class="onb-sh"><p class="onb-sh-p is-lead">Precise location places braking and cornering on the road. Approximate location only knows the area you’re in.</p>${preciseArt()}
        <p class="onb-sh-p">Used during drives only, and processed on your phone.</p>
        <div class="onb-sh-acts">${UI.button(ctx.ios ? 'Turn on Precise Location' : 'Use precise location', { kind: 'filled', size: 'l', block: true, action: 'precise' })}${UI.button('Keep approximate', { kind: ctx.ios ? 'glass' : 'text', size: 'l', block: true, state: 'refused' })}</div></div>`;
    },
    mount(el, ctx) { if (!still(ctx, el)) { const b = el.querySelector('.onb-pc-blob'); if (b) b.animate([{ transform: 'scale(.7)', opacity: 0.3 }, { transform: 'scale(1)', opacity: 1 }], { duration: 900, easing: 'cubic-bezier(.32,.72,0,1)', transformOrigin: 'center', transformBox: 'fill-box', fill: 'both' }); } return drawRoute(ctx, el, { duration: 1200, delay: 200 }); },
    actions: {
      precise: (ctx) => { ctx.haptic('success'); ctx.toast('Precise location is on', { icon: 'check' }); if (inOnb()) thenNav(ctx, 'S-ONB-07', carry(ctx)); else ctx.back(); return false; },
      done: (ctx) => { if (inOnb()) thenNav(ctx, 'S-ONB-07', carry(ctx)); else ctx.back(); return false; },
    },
    spec: {
      purpose: 'When the user chose approximate location (F-ONB-04.5): explain “Precise location places braking and cornering on the road”, offer the precise request once, and say plainly what “route only” means if they refuse.',
      layout: [
        'Sheet, auto height (iOS glass at partial height with a close button · Android ModalBottomSheet with drag handle, top radius 28).',
        'Header: GPS glyph (accent container 36) + “Turn on precise location?” (sheetTitle).',
        'Lead (body): the binding sentence + what approximate means.',
        'Comparison art: two 120 × 84 tiles side by side (radius 16): Approximate = a soft blurred circle; Precise = the route on the plain canvas with a hard-braking pin (rounded square) on the road. Captions under each.',
        'Fine print, then actions: primary “Turn on Precise Location” (iOS) / “Use precise location” (Android), secondary “Keep approximate”.',
        'refused: header “Route only, for now”; a card “Drives record as “route only, no event placement”.” + one line; where to change it; “Continue”.',
      ],
      components: ['sheetHeader', 'card', 'button', 'icon'],
      interactions: [
        'Primary → iOS requestTemporaryFullAccuracyAuthorization(purposeKey) via CLServiceSession full-accuracy purpose / Android re-request ACCESS_FINE_LOCATION; on success a toast and the sheet closes.',
        '“Keep approximate” → refused · “Continue” closes the sheet.',
        'Back to caller: from onboarding (S-ONB-06) the flow continues to S-ONB-07; from S-SET-05’s precise row it simply closes.',
      ],
      motion: ['Sheet: spring.sheet slide-up (engine). Art: the approximate blob breathes in (scale .7 → 1, 900 ms ease.glide) while the precise route draws on (1200 ms).', 'RM: static art.'],
      a11y: ['Sheet focus starts on the title. Comparison art is decorative; the lead states the difference in words.', 'Buttons ≥ 50 pt / 56 dp. XL: the sheet opens at the large detent; tiles stack.', 'Refused card is read as one paragraph.'],
      platformDiffs: { ios: 'Precise is a toggle in the system prompt; here we request temporary full accuracy with the NSLocationTemporaryUsageDescriptionDictionary purpose “Precise location places braking and cornering on the road.”', android: 'Android 12+ approximate vs precise: re-requesting FINE shows the system upgrade dialog. Play fine-location declaration (from 27 Jan 2027) uses the same justification (PRD §6.3).' },
      analytics: ['permission_primer_viewed {perm: location_precise}', 'precise_location_result {result: granted|refused}'],
      copy: { Title: 'Turn on precise location?', Lead: 'Precise location places braking and cornering on the road. Approximate location only knows the area you’re in.', Fine: 'Used during drives only, and processed on your phone.', 'Refused title': 'Route only, for now', 'Refused card': 'Drives record as “route only, no event placement”. Braking, acceleration and cornering are still counted, but not placed on the road.' },
      freePro: 'n/a (Free).',
      data: 'Precise tile: smoothed glyph of t20261105-0742 + DP_ICONS.pin(brake) (illustrative placement).',
      icons: ['gps', 'routes', 'close'],
      assets: [],
    },
  });

  // ======================================================================================================
  // S-ONB-12 · Battery and reliability step (Android only)
  // ======================================================================================================
  const OEM = {
    default: { brand: 'Samsung', detected: 'Samsung · One UI', title: 'Keep DrivePerf awake on your Samsung',
      lead: 'Samsung can put apps to sleep to save battery. When it does, drives don’t start on their own. Two settings fix it.',
      steps: [
        { t: 'Add DrivePerf to “Never sleeping apps”', p: 'Settings › Battery › Background usage limits', cta: 'Open sleeping apps', toast: 'Opens Battery › Background usage limits' },
        { t: 'Set app battery to “Unrestricted”', p: 'Settings › Apps › DrivePerf › Battery', cta: 'Open app battery', toast: 'Opens App info › Battery' },
      ] },
    xiaomi: { brand: 'Xiaomi', detected: 'Xiaomi · HyperOS (also Redmi and POCO)', title: 'Keep DrivePerf awake on your Xiaomi',
      lead: 'Xiaomi phones stop background apps to save battery. When that happens, drives don’t start on their own. Three settings fix it.',
      steps: [
        { t: 'Turn on Autostart', p: 'Settings › Apps › Manage apps › DrivePerf', cta: 'Open app settings', toast: 'Opens Manage apps › DrivePerf' },
        { t: 'Set Battery saver to “No restrictions”', p: 'Settings › Apps › Manage apps › DrivePerf › Battery saver', cta: 'Open battery saver', toast: 'Opens Battery saver for DrivePerf' },
        { t: 'Lock DrivePerf in Recents', p: 'Open Recents, then pull DrivePerf down until the lock shows', cta: null },
      ] },
    otherOEM: { brand: 'OnePlus', detected: 'OnePlus · OxygenOS', title: 'Keep DrivePerf running on your OnePlus',
      lead: 'Some phones pause apps in the background to save battery. When that happens, drives don’t start on their own. Two settings fix it.',
      steps: [
        { t: 'Allow background activity', p: 'Settings › Apps › DrivePerf › Battery usage', cta: 'Open app battery', toast: 'Opens App info › Battery usage' },
        { t: 'Turn off battery optimisation for DrivePerf', p: 'Settings › Battery › Battery optimisation', cta: 'Open battery optimisation', toast: 'Opens Battery optimisation' },
      ] },
  };
  function batteryArt() {
    return `<div class="onb-batt"><svg viewBox="0 0 132 80" width="132" height="80" aria-hidden="true"><rect class="onb-batt-b" x="3" y="3" width="116" height="74" rx="20"/><rect class="onb-batt-t" x="122" y="28" width="7" height="24" rx="3.5"/></svg><span class="onb-batt-loop">${loopSvg(60)}</span></div>`;
  }
  screen('S-ONB-12', {
    name: 'Battery and reliability step',
    wp: 'A', platforms: ['android'], presentation: 'push', tab: null, backTo: 'S-ONB-10',
    states: [
      { id: 'default', label: 'Samsung' },
      { id: 'xiaomi', label: 'Xiaomi' },
      { id: 'otherOEM', label: 'other OEM (OnePlus shown)' },
      { id: 'remindLater', label: 'remind later' },
    ],
    render(ctx) {
      if (ctx.state === 'remindLater') {
        const oem = ctx.store.oem || ctx.params.oem || 'default', o = OEM[oem] || OEM.default;
        return onbPage(ctx, {
          art: batteryArt(), title: 'We’ll remind you', lead: `Until then, ${o.brand} may put DrivePerf to sleep and miss a drive. The steps stay in Health Check, and we’ll nudge you after your first drive.`,
          body: resultLine('info', `Health Check: ${o.brand} sleeping apps, needs a look`),
          primary: { text: 'Continue', nav: 'S-ONB-13', params: carry(ctx) },
          secondary: { text: 'Show me the steps now', state: oem },
        });
      }
      const o = OEM[ctx.state] || OEM.default;
      const steps = `<ol class="onb-oem">${o.steps.map((s, i) => `<li><b class="onb-oem-n">${i + 1}</b><div><b>${esc(s.t)}</b><span>${esc(s.p)}</span>${s.cta ? UI.button(s.cta, { kind: 'tonal', size: 's', trailingIcon: 'chevronRight', toast: s.toast, label: `${s.cta}: ${s.t}` }) : ''}</div></li>`).join('')}</ol>`;
      return onbPage(ctx, {
        align: 'start', art: batteryArt(),
        kicker: `${UI.icon('phone', 14)}Detected: ${esc(o.detected)}`,
        title: o.title, lead: o.lead,
        body: steps,
        primary: { text: 'Done', nav: 'S-ONB-13', params: carry(ctx), action: 'done' },
        secondary: { text: 'Remind me later', action: 'later' },
      });
    },
    mount(el, ctx) { return stepMount(el, ctx, () => drawLoop(ctx, el, { duration: 1200, delay: 150 })); },
    actions: {
      done: (ctx) => { ctx.haptic('success'); return true; },
      later: (ctx) => { ctx.store.oem = ctx.state; ctx.setState('remindLater'); return false; },
    },
    spec: {
      purpose: 'On phones whose makers put apps to sleep (F-ONB-07, F-SET-03, EC-OEM), give that brand’s steps with deep links so auto-detect keeps working, with an honest “Remind me later”. Pixel and unlisted brands skip this step.',
      layout: [
        'Top bar: back arrow · step progress (8 of 11). Start-aligned.',
        'Art: a battery outline 132 × 80 (outline, radius 20) with The Loop 60 inside it.',
        'Kicker: phone glyph + “Detected: Samsung · One UI” (labelLarge, --dp-text-2): manufacturer and model are read locally.',
        'Title “Keep DrivePerf awake on your Samsung”, lead (what goes wrong, how many settings fix it).',
        'Numbered step list: number disc 28 (primaryContainer) · bold step · settings path (bodySmall, onSurfaceVariant) · tonal deep-link button (“Open sleeping apps ›”) where the brand has one. Xiaomi has a third step without a link (lock in Recents).',
        'Dock: filled “Done” · TextButton “Remind me later”.',
        'remindLater: battery art, “We’ll remind you”, honest consequence, a status row naming the Health Check item, “Continue” and “Show me the steps now”.',
      ],
      components: ['page', 'button', 'icon'],
      interactions: [
        'Step buttons → OEM deep links (Samsung: com.samsung.android.lool sleeping-apps screen; Xiaomi: com.miui.securitycenter autostart / battery saver; others: ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS or app details). REQUEST_IGNORE_BATTERY_OPTIMIZATIONS only if Play allows it for this use case (OQ-12). Prototype: toasts.',
        '“Done” → S-ONB-13 (reliability_guide_done) · “Remind me later” → remindLater → “Continue” → S-ONB-13 · “Show me the steps now” → the brand state.',
        'Content per brand is bundled and updatable via Remote Config (F-SET-03.3); Oppo/Realme, Vivo, Huawei/Honor use the same template.',
      ],
      motion: ['Hero motion: The Loop draws on inside the battery (1200 ms, dot glides into place).', 'Text rises 8 px, 40 ms stagger. RM: final art, 220 ms cross-fade.'],
      a11y: ['Each step is a list item: “Step 1 of 2. Add DrivePerf to Never sleeping apps. Settings, Battery, Background usage limits.” followed by its button “Open sleeping apps: Add DrivePerf to Never sleeping apps”.', SPEC_A11Y_COMMON],
      platformDiffs: { ios: 'Not shown on iOS. The iOS equivalents (Background App Refresh, Low Power Mode, force-quit, Live Activities) live in S-SET-06.', android: 'Android-only; shown only when automatic trips are on and Build.MANUFACTURER is a listed brand (Samsung, Xiaomi/Redmi/POCO, OnePlus, Oppo, Vivo, Huawei, Honor).' },
      analytics: ['reliability_guide_viewed {oem}', 'reliability_guide_done {oem}', 'onboarding_step_viewed {step: reliability, oem}'],
      copy: Object.fromEntries(Object.entries(OEM).map(([k, o]) => [o.brand, `${o.title}. ${o.lead} Steps: ${o.steps.map((s) => `${s.t} (${s.p})`).join(' · ')}`])),
      freePro: 'n/a (Free).',
      data: 'Brand step content is product copy (not in DP_DATA). health.android.items oemSleep “Samsung: Never sleeping apps” and batteryOpt “Battery: Unrestricted” are the Health Check rows these steps fix.',
      icons: ['phone', 'chevronRight', 'info'],
      assets: [],
    },
  });

  // ======================================================================================================
  // S-ONB-13 · Help improve DrivePerf (usage statistics, crash reports)
  // ======================================================================================================
  screen('S-ONB-13', {
    name: 'Help improve DrivePerf (usage statistics, crash reports)',
    wp: 'A', platforms: ['ios', 'android'], presentation: 'push', tab: null, backTo: 'S-ONB-10',
    states: [{ id: 'default', label: 'Def (usage unselected)' }, { id: 'bothOn', label: 'both on' }, { id: 'bothOff', label: 'both off' }],
    render(ctx) {
      const init = { default: { usage: D.user.consent.usageStats, crash: D.user.consent.crashReports }, bothOn: { usage: true, crash: true }, bothOff: { usage: false, crash: false } }[ctx.state];
      const st = scratch(ctx, 's13', init);
      const choice = (key, icon, title, text, on) => `<div class="onb-choice ${on ? 'is-on' : ''}">${UI.row({ icon, title, sub: text, trailing: 'toggle', on, action: 'flip', arg: key })}</div>`;
      return onbPage(ctx, {
        align: 'start',
        art: `<div class="onb-improve"><span>${UI.icon('insights', 30)}</span><span>${UI.icon('shield', 30)}</span></div>`,
        title: 'Help improve DrivePerf',
        lead: 'Two separate choices. Usage statistics stay off unless you turn them on.',
        body: `<div class="onb-choices">${choice('usage', 'insights', 'Share usage statistics', 'Which screens and features get used. Never your locations, routes, speeds or car names.', st.usage)}${choice('crash', 'shield', 'Send crash reports', 'If DrivePerf crashes, the device, OS and app version help us fix it. Never includes locations.', st.crash)}</div>
          <p class="onb-fine">Change these anytime in Privacy Centre › Analytics and diagnostics. Nothing is sent before you continue.</p>`,
        primary: { text: 'Continue', nav: 'S-ONB-14', params: carry(ctx), action: 'save' },
      });
    },
    mount(el, ctx) { return stepMount(el, ctx, () => popArt(el, ctx)); },
    actions: {
      flip: (ctx, key) => { const st = scratch(ctx, 's13', {}); st[key] = !st[key]; ctx.haptic(st[key] ? 'toggleOn' : 'toggleOff'); ctx.update(); return false; },
      save: (ctx) => { const st = scratch(ctx, 's13', {}); ctx.toast(`Usage statistics ${st.usage ? 'on' : 'off'} · crash reports ${st.crash ? 'on' : 'off'}`, { icon: 'check', duration: 1600 }); return true; },
    },
    spec: {
      purpose: 'Let the user decide whether usage statistics and crash reports leave the phone (F-ONB-08, F-PRV-06, F-ANA-01/03), as two separate choices, usage statistics not pre-selected (OQ-02).',
      layout: [
        'Top bar: Back · step progress (7 of 9 / 9 of 11). Start-aligned.',
        'Art: two 64 glyph tiles (insights, shield) on accent container (iOS radius 20 · Android Cookie-ish 24/12).',
        'Title “Help improve DrivePerf”, lead “Two separate choices. Usage statistics stay off unless you turn them on.”',
        'Two choice cards, each a toggle row with a one-sentence explanation: “Share usage statistics” · “Send crash reports”. A card gets a 1.5 pt Glide outline when on.',
        'Fine print: where to change later and “Nothing is sent before you continue.”',
        'Dock: “Continue” (same label and weight whatever the choices: no “Accept all”).',
      ],
      components: ['page', 'row', 'toggle', 'button'],
      interactions: ['Each toggle flips independently (toggleOn / toggleOff haptic).', '“Continue” → S-ONB-14; saves both choices. Analytics events from before this screen were held in memory and are dropped if usage is off (F-ONB-08.2); analytics_consent_set is sent only if usage = on.'],
      motion: ['Hero motion: the art tiles pop (spring.bouncy). Toggles: spring.fast.', 'RM: 220 ms cross-fade, toggles snap.'],
      a11y: ['Toggle rows: “Share usage statistics, switch, off. Which screens and features get used. Never your locations, routes, speeds or car names.”', SPEC_A11Y_COMMON],
      platformDiffs: { ios: 'Inset-grouped toggle rows (Glide tint). No ATT prompt and no IDFA (no AdSupport linked).', android: 'M3 Switch with thumb icons inside surfaceContainer cards. Firebase Analytics setAnalyticsCollectionEnabled / Sentry init or close() for crash reports; advertising ID collection off.' },
      analytics: ['onboarding_step_viewed {step: analytics} (held until the choice)', 'analytics_consent_set {usage, crash} (sent only if usage = on)'],
      copy: { Title: 'Help improve DrivePerf', Lead: 'Two separate choices. Usage statistics stay off unless you turn them on.', Usage: 'Share usage statistics — Which screens and features get used. Never your locations, routes, speeds or car names.', Crash: 'Send crash reports — If DrivePerf crashes, the device, OS and app version help us fix it. Never includes locations.', Fine: 'Change these anytime in Privacy Centre › Analytics and diagnostics. Nothing is sent before you continue.' },
      freePro: 'n/a (Free).',
      data: 'Default = DP_DATA.user.consent (usageStats false, crashReports true: the crash default is pending OQ-02 / counsel, F-ONB-01 privacy note). bothOn / bothOff override both.',
      icons: ['insights', 'shield'],
      assets: [],
    },
  });

  // ======================================================================================================
  // S-ONB-14 · Add your car
  // ======================================================================================================
  const COLOURS = ['carmine', 'ember', 'sodium', 'lime', 'glide', 'glacier', 'lucid', 'cobalt', 'violet', 'graphite', 'pearl', 'onyx'];
  const POWER = [{ id: 'petrol', label: 'Petrol' }, { id: 'diesel', label: 'Diesel' }, { id: 'hybrid', label: 'Hybrid' }, { id: 'ev', label: 'EV' }];
  const COLOUR_NAME = { carmine: 'Carmine', ember: 'Ember', sodium: 'Sodium', lime: 'Lime', glide: 'Glide', glacier: 'Glacier', lucid: 'Lucid', cobalt: 'Cobalt', violet: 'Violet', graphite: 'Graphite', pearl: 'Pearl', onyx: 'Onyx' };
  screen('S-ONB-14', {
    name: 'Add your car',
    wp: 'A', platforms: ['ios', 'android'], presentation: 'push', tab: null, backTo: 'S-ONB-13',
    states: [{ id: 'default', label: 'Def' }, { id: 'filled', label: 'filled' }, { id: 'skipped', label: 'skipped' }],
    render(ctx) {
      const g = U.vehicle('gti');
      const init = ctx.state === 'filled' ? { nick: g.nickname, make: g.make, model: g.model, year: String(g.year), power: g.powertrain, colour: g.colour }
        : ctx.state === 'skipped' ? { nick: 'My car', make: '', model: '', year: '', power: null, colour: 'graphite' } : { nick: '', make: '', model: '', year: '', power: null, colour: null };
      const st = scratch(ctx, 's14', init);
      const skipped = ctx.state === 'skipped';
      const name = st.nick || 'Your car';
      const subline = [st.year, st.make, st.model].filter(Boolean).join(' ') || (skipped ? 'Rename it anytime in Garage' : 'Make, model and year are optional');
      const preview = `<div class="onb-carcard ${st.colour ? '' : 'is-empty'}" style="--car:${st.colour ? `var(--dp-car-${st.colour})` : 'var(--dp-text-3)'}" role="img" aria-label="${esc(`Car preview: ${name}${st.colour ? ', ' + COLOUR_NAME[st.colour] : ''}`)}">${carArt(st.colour, { w: 200 })}<div class="onb-carcard-t"><b>${esc(name)}</b><span>${esc(subline)}</span></div>${st.colour ? UI.carChip({ nickname: name, colour: st.colour }) : ''}</div>`;
      if (skipped) {
        return onbPage(ctx, {
          align: 'start', art: preview, title: 'We added “My car”',
          lead: 'So your drives have a home. Rename it, pick a colour or add details anytime in Garage.',
          primary: { text: 'Continue', nav: ctx.params.auto === 'off' ? 'S-ONB-15/manualOnly' : 'S-ONB-15', params: carry(ctx, { car: 'mycar' }) },
          tertiary: { text: 'Add details now', state: 'default' },
        });
      }
      const n = st.nick.length;
      const swatches = `<div class="onb-sw" role="radiogroup" aria-label="Colour">${COLOURS.map((c) => `<span class="onb-sw-i ${st.colour === c ? 'is-on' : ''} ui-press" role="radio" aria-checked="${st.colour === c}" aria-label="${COLOUR_NAME[c]}" tabindex="0" data-action="colour" data-arg="${c}" style="--c:var(--dp-car-${c})">${st.colour === c ? UI.icon('check', 16) : ''}</span>`).join('')}</div>`;
      const form = `<div class="onb-form">
          <div data-field="nick">${UI.textField({ label: 'Nickname (required)', value: st.nick, placeholder: 'e.g. GTI', helper: `${n}/24 · shown on your Stories`, focused: ctx.state === 'default' })}</div>
          <div class="onb-form-row"><div>${UI.textField({ label: 'Make', value: st.make, placeholder: 'Optional' })}</div><div>${UI.textField({ label: 'Model', value: st.model, placeholder: 'Optional' })}</div></div>
          <div class="onb-form-year">${UI.textField({ label: 'Year', value: st.year, placeholder: 'Optional' })}</div>
          <div class="onb-form-l">Powertrain</div>${UI.segmented(POWER, { selected: st.power || 'none', action: 'power' })}
          <div class="onb-form-l">Colour</div>${swatches}
        </div>`;
      return onbPage(ctx, {
        align: 'start', art: preview, title: 'Add your car', lead: 'So your first Story feels like yours. No VIN or plate, and it stays on your phone.',
        body: form,
        primary: { text: 'Save car', action: 'save', disabled: !st.nick.trim(), label: st.nick.trim() ? 'Save car' : 'Save car, unavailable until you add a nickname' },
        tertiary: { text: 'Skip for now', action: 'skip' },
      });
    },
    mount(el, ctx) {
      const c = stepMount(el, ctx, () => { const car = el.querySelector('.onb-car'); if (car && !still(ctx, el)) ctx.motion.animate(car, [{ opacity: 0, transform: 'translateX(-28px)' }, { opacity: 1, transform: 'none' }], { preset: 'slow', delay: 80 }); });
      const field = el.querySelector('[data-field="nick"] .ui-field-in');
      if (!field) return c;
      const onInput = () => {
        const st = scratch(ctx, 's14', {}); st.nick = field.textContent.slice(0, 24);
        const ok = !!st.nick.trim();
        const btn = el.querySelector('.onb-dock .ui-btn--filled'); if (btn) { btn.classList.toggle('is-disabled', !ok); btn.setAttribute('aria-disabled', String(!ok)); if (ok) btn.removeAttribute('aria-disabled'); }
        const nm = el.querySelector('.onb-carcard-t b'); if (nm) nm.textContent = st.nick || 'Your car';
        const h = el.querySelector('[data-field="nick"] .ui-field-h'); if (h) h.textContent = `${st.nick.length}/24 · shown on your Stories`;
      };
      field.addEventListener('input', onInput);
      return () => { field.removeEventListener('input', onInput); c(); };
    },
    actions: {
      colour: (ctx, c) => { scratch(ctx, 's14', {}).colour = c; ctx.haptic('selection'); ctx.update(); return false; },
      power: (ctx, p) => { scratch(ctx, 's14', {}).power = p; ctx.update(); return false; },
      save: (ctx) => { const st = scratch(ctx, 's14', {}); ctx.haptic('success'); ctx.toast(`${st.nick || 'GTI'} added to your Garage`, { icon: 'check', duration: 1800 }); ctx.nav(ctx.params.auto === 'off' ? 'S-ONB-15/manualOnly' : 'S-ONB-15', carry(ctx, { car: ctx.state === 'filled' || (st.nick || '').toUpperCase() === 'GTI' ? 'gti' : 'custom' })); return false; },
      skip: (ctx) => { ctx.toast('Added “My car”. Rename it anytime in Garage.', { icon: 'garage', duration: 2000 }); ctx.nav(ctx.params.auto === 'off' ? 'S-ONB-15/manualOnly' : 'S-ONB-15', carry(ctx, { car: 'mycar' })); return false; },
    },
    spec: {
      purpose: 'Add the first car during setup so the first Story feels personal (F-ONB-09, F-GAR-01): nickname required, the rest optional, a colour that tints Stories; Skip silently creates “My car”.',
      layout: [
        'Top bar: Back · step progress (8 of 9 / 10 of 11). Start-aligned.',
        'Live preview card (hero, radius 32/28): generic hatch silhouette 200 wide filled with the chosen car colour (gradient 85% → 55%, glass windows, neutral wheels), nickname (title3 600) + “year make model” line, and the car chip as it will appear on Stories. Empty state: outline silhouette in --dp-text-3, “Your car”.',
        'Title “Add your car”, lead (privacy: no VIN or plate, stays on the phone).',
        'Form: Nickname (required, ≤ 24, counter helper “n/24 · shown on your Stories”) · Make | Model (two columns, one column at XL) · Year · “Powertrain” segmented (Petrol · Diesel · Hybrid · EV: iOS glass segmented / Android connected button group) · “Colour” 12 swatches (36 round, 6 per row; selected = 2 pt ring + check).',
        'Dock: “Save car” (disabled until a nickname exists) · tertiary “Skip for now”.',
        'skipped: preview shows “My car” in graphite, title “We added “My car”” + rename hint; “Continue”, tertiary “Add details now”.',
      ],
      components: ['page', 'textField', 'segmented', 'carChip', 'button'],
      interactions: [
        'Typing the nickname updates the preview and enables Save live (no re-render).',
        'Powertrain segment → selection haptic · colour swatch → preview recolours (selection haptic).',
        '“Save car” → S-ONB-15 (or S-ONB-15/manualOnly when automatic trips are off), the car becomes the default (car_added {source: onboarding, powertrain}).',
        '“Skip for now” → creates “My car” silently and goes to S-ONB-15 (toast in the prototype). skipped → Continue → S-ONB-15.',
      ],
      motion: ['Hero motion: the car silhouette drives in from the left (translateX −28 → 0, spring.slow). Colour changes cross-fade the fill (spring.effects).', 'RM: static car; 220 ms cross-fade.'],
      a11y: ['Preview card label: “Car preview: GTI, Carmine”. Swatches are a radiogroup labelled “Colour”, each “Carmine, radio button, selected”.', 'Nickname field label “Nickname (required)” with the counter as hint. Disabled Save: “Save car, unavailable until you add a nickname”.', 'XL: make/model stack, swatches wrap to 4 per row, segmented labels wrap to two lines.'],
      platformDiffs: { ios: 'Rounded fields (--dp-surface-2, radius 14), glass segmented Picker, swatches as a LazyVGrid; keyboard toolbar “Done”.', android: 'OutlinedTextField (shape extraSmall), SingleChoiceSegmentedButtonRow / connected ButtonGroup, swatches in a FlowRow; IME action Next → Done.' },
      analytics: ['onboarding_step_viewed {step: car}', 'car_added {source: onboarding, powertrain}'],
      copy: { Title: 'Add your car', Lead: 'So your first Story feels like yours. No VIN or plate, and it stays on your phone.', Nickname: 'Nickname (required) · e.g. GTI · n/24 · shown on your Stories', Save: 'Save car', Skip: 'Skip for now', 'Skipped title': 'We added “My car”', 'Skipped lead': 'So your drives have a home. Rename it, pick a colour or add details anytime in Garage.' },
      freePro: 'Free includes 1 car (entitlement.freeLimits.vehicles); during the Preview more can be added later in Garage.',
      data: 'filled = DP_DATA.vehicles gti (GTI · Volkswagen Golf GTI · 2022 · petrol · carmine “Kings Red”). Colour tokens: --dp-car-* (brand.md §5.3).',
      icons: ['check', 'garage'],
      assets: [],
    },
  });

  // ======================================================================================================
  // S-ONB-15 · You're ready (quick-start surfaces)
  // ======================================================================================================
  function quickStart(ctx) {
    const cc = ctx.ios
      ? `<span class="onb-cc" aria-hidden="true"><span class="onb-cc-b">${UI.icon('record', 26)}</span><small>Start drive</small></span>`
      : `<span class="onb-qs" aria-hidden="true">${UI.qsTile({ state: 'idle', label: 'Start drive' })}</span>`;
    const wd = `<span class="onb-wd" aria-hidden="true"><span class="onb-wd-h">${I.loop({ size: 16, color: 'var(--dp-accent)', dot: 'var(--dp-text)' })}<b>DrivePerf</b></span><span class="onb-wd-n">${UI.num('—')}</span><small>Your first drive<br>shows here</small></span>`;
    const item = (art, title, sub, nav) => `<div class="onb-qsi ui-press" ${UI.act({ nav, label: `${title}. ${sub}` })}>${art}<div><b>${esc(title)}</b><span>${esc(sub)}</span><em>Show me how${UI.icon('chevronRight', 14)}</em></div></div>`;
    return `<div class="onb-qsl">${item(cc, ctx.ios ? 'Add “Start drive” to Control Center' : 'Add the Quick Settings tile', 'Start a trip without opening the app.', ctx.ios ? 'S-LIVE-03' : 'S-LIVE-07')}${item(wd, 'Add the DrivePerf widget', 'Your last drive at a glance, one tap to start.', ctx.ios ? 'S-LIVE-04' : 'S-LIVE-08')}</div>`;
  }
  screen('S-ONB-15', {
    name: 'You’re ready (quick-start surfaces)',
    wp: 'A', platforms: ['ios', 'android'], presentation: 'push', tab: null, backTo: 'S-ONB-14',
    states: [{ id: 'default', label: 'Auto on' }, { id: 'manualOnly', label: 'manual only' }],
    render(ctx) {
      const manual = ctx.state === 'manualOnly';
      const car = ctx.params.car === 'mycar' ? 'My car' : ctx.params.car === 'custom' ? 'Added' : U.vehicle('gti').nickname;
      const row = (title, value, ok) => UI.row({ lead: `<span class="onb-sum-ic ${ok ? 'is-ok' : 'is-off'}">${UI.icon(ok ? 'check' : 'minus', 14)}</span>`, title, value: esc(value) });
      const summary = UI.list([
        row('Automatic trips', manual ? 'Off' : 'On', !manual),
        row('Location', manual ? (ctx.ios ? 'While Using' : 'While using the app') : (ctx.ios ? 'Always' : 'All the time'), true),
        row(ctx.ios ? 'Motion & Fitness' : 'Physical activity', 'Allowed', true),
        row('Notifications', 'Allowed', true),
        row('Car', car, true),
      ], { cls: 'onb-sum' });
      return onbPage(ctx, {
        cls: 'onb-ready', progress: false,
        art: `<div class="onb-done">${loopSvg(ctx.xl ? 96 : 128)}</div>`,
        title: manual ? 'You’re set for manual trips.' : 'You’re set.',
        lead: manual ? 'Tap Start when you set off and Stop when you park. Your first Story appears right after.' : 'Just drive. Your first Story appears when you park.',
        body: `${UI.chip('', { kind: 'pro', html: `${UI.icon('sparkle', 14)}Pro Preview · day 1 of 14 · no card`, cls: 'onb-pvchip' })}${summary}${manual ? `<p class="onb-fine is-start">Turn on automatic trips anytime in Settings › Capture.</p>` : ''}<h2 class="onb-sec-h">${manual ? 'Start faster' : 'For drives you start yourself'}</h2>${quickStart(ctx)}`,
        primary: { text: 'Go to Drives', action: 'finish', label: 'Go to Drives' },
      });
    },
    mount(el, ctx) { return stepMount(el, ctx, () => drawLoop(ctx, el, { duration: 1300, delay: 100 })); },
    actions: {
      finish: (ctx) => { ctx.haptic('success'); ctx.nav('S-HOME-01/firstRunEmpty', ctx.state === 'manualOnly' ? { ent: 'preview', capture: 'manual' } : { ent: 'preview' }); return false; },
    },
    spec: {
      purpose: 'Close onboarding with what happens next (F-ONB-10): “You’re set. Just drive. Your first Story appears when you park.”, a summary of what was granted, and the quick-start surfaces for starting a trip without opening the app.',
      layout: [
        'Top bar: Back only (no progress: this is the end). Centred hero.',
        'Art: The Loop 128 (XL 96) drawing on: it bookends the welcome screen.',
        'Title “You’re set.” (manual: “You’re set for manual trips.”) + lead (PRD copy).',
        'Preview chip: sparkle + “Pro Preview · day 1 of 14 · no card” (Pro chip style, Glide on accent container, never gold).',
        'Summary list (inset grouped / segmented ListItems): Automatic trips · Location · Motion & Fitness (Physical activity) · Notifications · Car, each with a status disc (check = on, minus = off) and the value as text.',
        'Section “For drives you start yourself” (manual: “Start faster”): two tappable cards — iOS: a Control Center button mock (64, glass, record glyph, “Start drive”) “Add “Start drive” to Control Center”; Android: a Quick Settings tile mock “Add the Quick Settings tile” — and a small-widget mock “Add the DrivePerf widget”. Each ends with “Show me how ›”.',
        'Dock: “Go to Drives”.',
      ],
      components: ['page', 'list', 'row', 'chip', 'qsTile', 'widget', 'button'],
      interactions: ['“Go to Drives” → S-NAV-01 shell, Drives tab S-HOME-01/firstRunEmpty with ent=preview (Pro Preview day 1); manual only also sets capture=manual (accessory/toolbar shows Manual mode). Onboarding is removed from the back stack.', 'Quick-start cards → iOS S-LIVE-03 (Control Center control) / S-LIVE-04 (widgets) · Android S-LIVE-07 (QS tile) / S-LIVE-08 (widget). In the app they open a short how-to; tiles cannot be added programmatically except via TileService.requestAddTileService (Android 13+), which the Android card uses.'],
      motion: ['Hero motion: The Loop draws on (1300 ms) with the G-dot gliding into its lead. Haptic success on “Go to Drives”; Drives fades through (Android) / cross-fades (iOS).', 'RM: final mark, 220 ms cross-fade.'],
      a11y: ['Summary rows read “Automatic trips, On”. Status discs pair a glyph (check / minus) with the text value.', 'Quick-start cards are buttons: “Add Start drive to Control Center. Start a trip without opening the app.”', SPEC_A11Y_COMMON],
      platformDiffs: { ios: 'Control Center control (ControlWidget “Start drive”, F-CAP-10) + WidgetKit widget tip; Location value “Always”.', android: 'Quick Settings tile (TileService, F-CAP-13) with requestAddTileService on Android 13+, Glance widget tip; Location value “All the time”.' },
      analytics: ['onboarding_completed {auto_on, precise, motion, notifications}'],
      copy: { 'Auto on': COPY.ready, 'Manual only': 'You’re set for manual trips. Tap Start when you set off and Stop when you park. Your first Story appears right after.', Preview: 'Pro Preview · day 1 of 14 · no card', 'Quick start iOS': 'Add “Start drive” to Control Center · Add the DrivePerf widget', 'Quick start Android': 'Add the Quick Settings tile · Add the DrivePerf widget', Primary: 'Go to Drives' },
      freePro: 'Pro Preview starts at install (14 days, no card, Gate 2 OQ-08); the chip states day 1. No paywall here.',
      data: 'Summary values are the onboarding results (default = all granted, car GTI from DP_DATA.vehicles gti; car=mycar → “My car”). Preview day from entitlement.states.preview with day 1.',
      icons: ['check', 'minus', 'record', 'sparkle', 'chevronRight'],
      assets: ['logo-a-loop.svg'],
    },
  });

  // ======================================================================================================
  // S-ONB-16 · Permission denied / manual-mode sheet
  // ======================================================================================================
  const iosLocSettings = (state) => ({
    title: 'Location', back: 'DrivePerf',
    groups: [
      { header: 'Allow location access', rows: [
        { label: 'Never', check: state === 'never' },
        { label: 'Ask Next Time Or When I Share' },
        { label: 'While Using the App', check: state === 'whileUsing', hi: state === 'never' },
        { label: 'Always', hi: state !== 'never' },
      ], footer: 'App explanation: “DrivePerf uses your location in the background only to record the route of drives it detects.”' },
      { rows: [{ label: 'Precise Location', toggle: true }] },
    ],
  });
  screen('S-ONB-16', {
    name: 'Permission denied / manual-mode sheet',
    wp: 'A', platforms: ['ios', 'android'], presentation: 'sheet', tab: null,
    over: (ctx) => (ctx.state === 'alwaysDeclined' ? (ctx.android ? 'S-ONB-09/returnedNotGranted' : 'S-ONB-08/declined') : ctx.state === 'settingsDeepLink' ? 'S-HOME-01/Deny' : 'S-ONB-06/denied'),
    detent: (ctx) => (ctx.state === 'settingsDeepLink' ? 'large' : 'auto'),
    states: [{ id: 'default', label: 'Location denied' }, { id: 'alwaysDeclined', label: 'Always declined' }, { id: 'settingsDeepLink', label: 'Settings deep link' }],
    render(ctx) {
      const st = ctx.state, os = ctx.ios ? 'Settings' : 'settings';
      const ic = (name) => `<span class="onb-sh-ic is-warn">${UI.icon(name, 20)}</span>`;
      if (st === 'settingsDeepLink') {
        const shot = ctx.ios ? settingsShot(ctx, iosLocSettings('whileUsing'), { h: ctx.xl ? 220 : 280, scale: 0.6 }) : settingsShot(ctx, androidLocSettings(false), { h: ctx.xl ? 220 : 280, scale: 0.6 });
        const steps = ctx.ios ? ['Tap Open Settings.', 'Tap Location.', 'Choose Always, and keep Precise Location on.'] : ['Tap Open settings.', 'Tap Permissions › Location.', 'Choose Allow all the time, and keep Use precise location on.'];
        return `${UI.sheetHeader('Turn on automatic trips', { icon: ic('gear'), sub: ctx.ios ? 'Two taps in Settings, then come back.' : 'Two taps in settings, then come back.' })}<div class="onb-sh"><div class="onb-sh-guide">${shot}</div>
          <ol class="onb-steps is-compact">${steps.map((s, i) => `<li><b>${i + 1}</b><span>${esc(s)}</span></li>`).join('')}</ol>
          <div class="onb-sh-acts">${UI.button(ctx.ios ? 'Open Settings' : 'Open settings', { kind: 'filled', size: 'l', block: true, icon: 'gear', action: 'settings', label: ctx.ios ? 'Open Settings, DrivePerf, Location' : 'Open settings: DrivePerf location permission' })}${UI.button('Keep manual trips', { kind: ctx.ios ? 'glass' : 'text', size: 'l', block: true, action: 'manual' })}</div></div>`;
      }
      if (st === 'alwaysDeclined') {
        return `${UI.sheetHeader('Manual trips it is', { icon: `<span class="onb-sh-ic">${UI.icon('record', 20)}</span>` })}<div class="onb-sh">
          <p class="onb-sh-p is-lead">Location is set to ${ctx.ios ? '“While Using”' : '“While using the app”'}, so DrivePerf won’t start drives on its own. Everything else works.</p>
          ${checks([
            { kind: 'yes', text: 'Start drives yourself', sub: ctx.ios ? 'In the app, from Control Center or the widget.' : 'In the app, from Quick Settings or the widget.' },
            { kind: 'yes', text: 'Stories, Replay and scores, as usual' },
            { kind: 'no', text: 'No automatic trips', sub: 'We won’t ask again. Switch it on in Settings whenever you like.' },
          ])}
          <div class="onb-sh-acts">${UI.button('Continue in manual mode', { kind: 'filled', size: 'l', block: true, action: 'manual' })}${UI.button(`Open ${os}`, { kind: ctx.ios ? 'glass' : 'text', size: 'l', block: true, state: 'settingsDeepLink' })}</div></div>`;
      }
      return `${UI.sheetHeader('DrivePerf needs location to record', { icon: ic('gps') })}<div class="onb-sh">
        <p class="onb-sh-p is-lead">Without it, DrivePerf can’t draw a route or measure a drive, so nothing records, not even manual trips.</p>
        ${checks([
          { kind: 'gps', text: ctx.ios ? '“While Using the App” is enough for manual trips' : '“Allow only while using the app” is enough for manual trips' },
          { kind: 'lock', text: 'Used only during drives, processed on your phone' },
          { kind: 'no', text: COPY.neverSold },
        ])}
        <div class="onb-sh-acts">${UI.button(`Open ${os}`, { kind: 'filled', size: 'l', block: true, icon: 'gear', state: 'settingsDeepLink' })}${UI.button('Not now', { kind: ctx.ios ? 'glass' : 'text', size: 'l', block: true, action: 'notNow' })}</div>
        <p class="onb-sh-fine">You can look around and set things up. Recording starts once location is on.</p></div>`;
    },
    mount(el, ctx) { const ic = el.querySelector('.onb-sh-ic'); if (ic && !still(ctx, el)) ctx.motion.animate(ic, [{ transform: 'scale(.6)', opacity: 0 }, { transform: 'none', opacity: 1 }], { preset: 'bouncy', delay: 180 }); },
    actions: {
      settings: (ctx) => { ctx.toast(ctx.ios ? 'Opens Settings › DrivePerf › Location' : 'Opens App info › Permissions › Location', { icon: 'gear' }); return false; },
      manual: (ctx) => { if (inOnb()) thenNav(ctx, 'S-ONB-10', carry(ctx, { auto: 'off' })); else { ctx.toast('Manual mode: tap Start when you set off', { icon: 'record' }); ctx.back(); } return false; },
      notNow: (ctx) => { if (inOnb()) thenNav(ctx, 'S-ONB-10', carry(ctx, { auto: 'off', loc: 'off' })); else ctx.back(); return false; },
    },
    spec: {
      purpose: 'One honest sheet for every denial (F-ONB-04.3/.6, EC-PERM): explain what still works, offer the Settings deep link, never re-request in-app, and continue in manual mode.',
      layout: [
        'Sheet (auto height; settingsDeepLink opens at the large detent). iOS glass with close button · Android ModalBottomSheet with drag handle.',
        'Header: status glyph disc 36 (warning container for denials, accent container for manual) + sheetTitle (+ subtitle in settingsDeepLink).',
        'Location denied (default): “DrivePerf needs location to record”; lead: nothing records, not even manual trips; checklist (While Using is enough for manual trips · used only during drives · never sold); actions “Open Settings” (filled, gear) · “Not now”; fine print.',
        'alwaysDeclined: “Manual trips it is”; lead names the current setting (While Using / While using the app); checklist ✓ Start drives yourself (Control Center / Quick Settings / widget) · ✓ Stories, Replay and scores · ✕ No automatic trips (“We won’t ask again”); actions “Continue in manual mode” · “Open Settings”.',
        'settingsDeepLink: “Turn on automatic trips” + “Two taps in Settings, then come back.”; a screenshot-style guide (the OS Location page scaled 60%, radius 20, target option highlighted in Glide); 3 numbered steps; “Open Settings” · “Keep manual trips”.',
      ],
      components: ['sheetHeader', 'button', 'sysSettings', 'icon'],
      interactions: [
        '“Open Settings” → settingsDeepLink; there, “Open Settings” → UIApplication.openSettingsURLString (iOS) / ACTION_APPLICATION_DETAILS_SETTINGS (Android). Prototype: toast.',
        '“Continue in manual mode” / “Keep manual trips” → inside onboarding: closes and continues at S-ONB-10 (auto=off); from Home (S-HOME-01 Deny) or S-LIVE-04 Deny: closes.',
        '“Not now” → inside onboarding: S-ONB-10 (auto=off, location off); elsewhere: closes. Home then shows “Auto-detect off” with a Fix action (F-ONB-04.6).',
        'The app never shows the iOS Always prompt again after a decline (F-ONB-04.3).',
      ],
      motion: ['Sheet: spring.sheet (engine). The header glyph pops (spring.bouncy, 180 ms delay). RM: no pop.'],
      a11y: ['Focus starts on the sheet title and returns to the invoking control on close.', 'Guide image is decorative; the numbered steps say the same thing.', '“Open Settings” label in settingsDeepLink: “Open Settings, DrivePerf, Location”.', 'XL: opens at the large detent; the guide shrinks to 220 tall; buttons stack full width.'],
      platformDiffs: { ios: 'Settings deep link opens the app’s own Settings page (Location row). Copy uses “While Using” / “Always”.', android: 'Deep link to App info › Permissions; copy uses “Allow only while using the app” / “Allow all the time”. The Android recording notification still works in manual mode.' },
      analytics: ['permission_denied_sheet_viewed {perm, reason: denied|always_declined}', 'settings_deeplink_opened {perm}', 'manual_mode_chosen {source: onboarding|home}'],
      copy: { 'Denied title': 'DrivePerf needs location to record', 'Denied lead': 'Without it, DrivePerf can’t draw a route or measure a drive, so nothing records, not even manual trips.', 'Always declined title': 'Manual trips it is', 'Always declined lead': 'Location is set to “While Using”, so DrivePerf won’t start drives on its own. Everything else works.', 'Settings title': 'Turn on automatic trips', 'Settings sub': 'Two taps in Settings, then come back.', 'Not now fine print': 'You can look around and set things up. Recording starts once location is on.' },
      freePro: 'n/a (Free). Manual mode keeps every Free and Pro feature (Apple 5.1.1).',
      data: 'Settings labels: iOS Location page (Never · Ask Next Time Or When I Share · While Using the App · Always · Precise Location) and Android 16 permission page; purpose string = DP_UI.sysPermission(always) text.',
      icons: ['gps', 'record', 'gear', 'lock', 'check', 'close'],
      assets: [],
    },
  });
})();
