/* DrivePerf motion runtime for the HTML prototypes (DRIVE-99, A6). Spec: docs/design/system/motion.md.
   Reads spring presets from window.DP_TOKENS (tokens.js) and the platform from the nearest [data-platform].
   API
     DP_MOTION.reduced(el?)                        -> true when reduced motion is on (OS or [data-reduced-motion="true"])
     DP_MOTION.spring({from,to,velocity,preset|zeta,k, onUpdate, onDone, el}) -> {stop, retarget(to, v?)}
     DP_MOTION.tween({from,to,duration,ease,onUpdate,onDone})               -> {stop}
     DP_MOTION.animate(el, keyframes, {preset|duration, easing})            -> WAAPI Animation (spring → linear())
     DP_MOTION.drawOn(pathEl, {duration, delay})    route draw-on (stroke-dash)    · RM: fade in
     DP_MOTION.countUp(el, to, {from, duration, tickEvery, onTick})                · RM: set final value
     DP_MOTION.follower({zeta, k})                  G-dot physics: .target(x,y) .step(dt) -> {x,y}
     DP_MOTION.haptic(name, {el})                   annotates a haptic (event 'dp:haptic'); never while a trip is active
     DP_MOTION.setDriving(bool)                     shells call this when entering/leaving S-DRV-01 (suppresses haptics) */
(function () {
  const T = window.DP_TOKENS;
  const platformOf = (el) => (el && el.closest && el.closest('[data-platform]')?.dataset.platform) || document.documentElement.dataset.platform || 'ios';
  const physOf = (preset, el) => {
    const p = platformOf(el);
    const sp = T?.motion.spring;
    if (sp?.shared?.[preset]) return { zeta: sp.shared[preset].dampingRatio, k: sp.shared[preset].stiffness };
    const s = sp?.[p]?.[preset] || sp?.ios?.default || { duration: 0.42, bounce: 0.15 };
    if ('duration' in s) return { zeta: 1 - s.bounce, k: (2 * Math.PI / s.duration) ** 2 };
    return { zeta: s.dampingRatio, k: s.stiffness };
  };
  let driving = false;
  const reduced = (el) => {
    const host = (el && el.closest && el.closest('[data-reduced-motion]')) || document.documentElement;
    if (host.dataset.reducedMotion === 'true') return true;
    if (host.dataset.reducedMotion === 'false') return false;
    return matchMedia('(prefers-reduced-motion: reduce)').matches;
  };
  // analytic-free semi-implicit Euler with 1/240 s substeps (stable for k ≤ 4000)
  function spring({ from = 0, to = 1, velocity = 0, preset = 'default', zeta, k, onUpdate, onDone, el, precision = 0.001 } = {}) {
    const ph = zeta != null ? { zeta, k } : physOf(preset, el);
    if (reduced(el) && preset !== 'gMeter') { onUpdate?.(to, 0); onDone?.(); return { stop() {}, retarget(t) { onUpdate?.(t, 0); } }; }
    let x = from, v = velocity, target = to, raf = 0, last = performance.now();
    const c = 2 * ph.zeta * Math.sqrt(ph.k);
    const tick = (now) => {
      let dt = Math.min(0.064, (now - last) / 1000); last = now;
      while (dt > 0) { const h = Math.min(dt, 1 / 240); const a = -ph.k * (x - target) - c * v; v += a * h; x += v * h; dt -= h; }
      onUpdate?.(x, v);
      if (Math.abs(x - target) < precision && Math.abs(v) < precision * 10) { onUpdate?.(target, 0); raf = 0; onDone?.(); return; }
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame((n) => { last = n; tick(n); });
    return { stop() { cancelAnimationFrame(raf); raf = 0; }, retarget(t, nv) { target = t; if (nv != null) v = nv; if (!raf) { last = performance.now(); raf = requestAnimationFrame(tick); } } };
  }
  const EASE = {
    standard: (t) => cubic(0.2, 0, 0, 1, t), glide: (t) => cubic(0.32, 0.72, 0, 1, t), drawOn: (t) => cubic(0.65, 0, 0.35, 1, t),
    countUp: (t) => cubic(0.16, 1, 0.3, 1, t), emphasizedDecelerate: (t) => cubic(0.05, 0.7, 0.1, 1, t), linear: (t) => t,
  };
  function cubic(x1, y1, x2, y2, t) { // solve bezier for x = t
    let u = t;
    for (let i = 0; i < 6; i++) { const x = 3 * (1 - u) ** 2 * u * x1 + 3 * (1 - u) * u * u * x2 + u ** 3 - t; const dx = 3 * (1 - u) ** 2 * x1 + 6 * (1 - u) * u * (x2 - x1) + 3 * u * u * (1 - x2); if (Math.abs(dx) < 1e-6) break; u -= x / dx; }
    u = Math.min(1, Math.max(0, u));
    return 3 * (1 - u) ** 2 * u * y1 + 3 * (1 - u) * u * u * y2 + u ** 3;
  }
  function tween({ from = 0, to = 1, duration = 320, ease = 'standard', onUpdate, onDone, el } = {}) {
    if (reduced(el)) { onUpdate?.(to); onDone?.(); return { stop() {} }; }
    const f = typeof ease === 'function' ? ease : EASE[ease] || EASE.standard;
    let raf, t0;
    const tick = (n) => { t0 ??= n; const p = Math.min(1, (n - t0) / duration); onUpdate?.(from + (to - from) * f(p)); if (p < 1) raf = requestAnimationFrame(tick); else onDone?.(); };
    raf = requestAnimationFrame(tick);
    return { stop: () => cancelAnimationFrame(raf) };
  }
  function cssVar(el, name) { return getComputedStyle(el).getPropertyValue(name).trim(); }
  function animate(el, keyframes, { preset, duration, easing, delay = 0, fill = 'both' } = {}) {
    if (reduced(el)) {
      const last = Array.isArray(keyframes) ? keyframes[keyframes.length - 1] : keyframes;
      const first = Array.isArray(keyframes) ? keyframes[0] : {};
      const fade = 'opacity' in last || 'opacity' in first ? [{ opacity: first.opacity ?? 0 }, { opacity: last.opacity ?? 1 }] : [{ opacity: 0.6 }, { opacity: 1 }];
      return el.animate(fade, { duration: T?.motion.reducedMotion.crossfade ?? 220, easing: 'ease', fill, delay: 0 });
    }
    if (preset) {
      easing = cssVar(el, `--dp-spring-${preset.replace(/[A-Z]/g, (m) => '-' + m.toLowerCase())}`) || 'ease';
      duration = parseFloat(cssVar(el, `--dp-spring-${preset.replace(/[A-Z]/g, (m) => '-' + m.toLowerCase())}-duration`)) || 400;
    }
    return el.animate(keyframes, { duration: duration ?? 320, easing: easing ?? 'ease', delay, fill });
  }
  function drawOn(path, { duration = T?.motion.duration.routeDrawOn ?? 1800, delay = 0 } = {}) {
    const len = path.getTotalLength();
    if (reduced(path)) return path.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 220, fill: 'both' });
    path.style.strokeDasharray = `${len} ${len}`;
    return path.animate([{ strokeDashoffset: len }, { strokeDashoffset: 0 }], { duration, delay, easing: 'cubic-bezier(0.65, 0, 0.35, 1)', fill: 'both' });
  }
  function countUp(el, to, { from = 0, duration = T?.motion.duration.countUp ?? 1200, tickEvery = 10, onTick, format = (v) => Math.round(v) } = {}) {
    if (reduced(el)) { el.textContent = format(to); return { stop() {} }; }
    let lastTick = Math.floor(from / tickEvery);
    return tween({ from, to, duration, ease: 'countUp', el, onUpdate: (v) => { el.textContent = format(v); const k = Math.floor(v / tickEvery); if (k !== lastTick) { lastTick = k; onTick?.(k * tickEvery); } } });
  }
  function follower({ zeta, k, el } = {}) {
    const ph = zeta != null ? { zeta, k } : physOf('gMeter', el);
    const c = 2 * ph.zeta * Math.sqrt(ph.k);
    const s = { x: 0, y: 0, vx: 0, vy: 0, tx: 0, ty: 0 };
    return {
      target(x, y) { s.tx = x; s.ty = y; },
      jump(x, y) { s.x = s.tx = x; s.y = s.ty = y; s.vx = s.vy = 0; },
      step(dt) {
        if (reduced(el)) { s.x = s.tx; s.y = s.ty; return s; }
        while (dt > 0) { const h = Math.min(dt, 1 / 240); s.vx += (-ph.k * (s.x - s.tx) - c * s.vx) * h; s.vy += (-ph.k * (s.y - s.ty) - c * s.vy) * h; s.x += s.vx * h; s.y += s.vy * h; dt -= h; }
        return s;
      },
      state: s,
    };
  }
  function haptic(name, { el } = {}) {
    if (driving && !['confirm'].includes(name)) return; // SAF: no haptics while a driver trip is active (Stop/Passenger confirm only)
    const map = T?.haptics?.[name];
    const detail = { name, ios: map?.ios, android: map?.android, platform: platformOf(el) };
    document.dispatchEvent(new CustomEvent('dp:haptic', { detail }));
    if (navigator.vibrate) navigator.vibrate({ selection: 6, tick: 4, tap: 8, confirm: 14, success: [10, 40, 16], doubleSuccess: [12, 90, 12], warning: [20, 60, 20], error: [24, 40, 24, 40, 24] }[name] || 6);
    return detail;
  }
  window.DP_MOTION = { reduced, spring, tween, animate, drawOn, countUp, follower, haptic, ease: EASE, setDriving: (b) => (driving = !!b), isDriving: () => driving, platformOf };
})();
