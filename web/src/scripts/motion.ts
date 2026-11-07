// Motion runtime for the website islands, ported from prototypes/shared/motion.js (DP_MOTION; spec:
// docs/design/system/motion.md). Website subset: spring, tween, follower, drawOn, easings. No haptics.
import { reduced } from './env';

function cubic(x1: number, y1: number, x2: number, y2: number, t: number): number { // solve bezier for x = t
  let u = t;
  for (let i = 0; i < 6; i++) {
    const x = 3 * (1 - u) ** 2 * u * x1 + 3 * (1 - u) * u * u * x2 + u ** 3 - t;
    const dx = 3 * (1 - u) ** 2 * x1 + 6 * (1 - u) * u * (x2 - x1) + 3 * u * u * (1 - x2);
    if (Math.abs(dx) < 1e-6) break;
    u -= x / dx;
  }
  u = Math.min(1, Math.max(0, u));
  return 3 * (1 - u) ** 2 * u * y1 + 3 * (1 - u) * u * u * y2 + u ** 3;
}

export const EASE = {
  standard: (t: number) => cubic(0.2, 0, 0, 1, t),
  glide: (t: number) => cubic(0.32, 0.72, 0, 1, t),
  drawOn: (t: number) => cubic(0.65, 0, 0.35, 1, t),
  countUp: (t: number) => cubic(0.16, 1, 0.3, 1, t),
  emphasizedDecelerate: (t: number) => cubic(0.05, 0.7, 0.1, 1, t),
  linear: (t: number) => t,
};
export type EaseName = keyof typeof EASE;

export interface Stoppable { stop(): void }

/** Damped spring (semi-implicit Euler, 1/240 s substeps). Reduced motion: jumps to `to`. */
export function spring({ from = 0, to = 1, velocity = 0, zeta = 0.78, k = 16, precision = 0.001, onUpdate, onDone }: {
  from?: number; to?: number; velocity?: number; zeta?: number; k?: number; precision?: number;
  onUpdate?: (x: number, v: number) => void; onDone?: () => void;
}): Stoppable {
  if (reduced()) { onUpdate?.(to, 0); onDone?.(); return { stop() {} }; }
  let x = from, v = velocity, raf = 0, last = performance.now();
  const c = 2 * zeta * Math.sqrt(k);
  const tick = (now: number) => {
    let dt = Math.min(0.064, (now - last) / 1000); last = now;
    while (dt > 0) { const h = Math.min(dt, 1 / 240); const a = -k * (x - to) - c * v; v += a * h; x += v * h; dt -= h; }
    onUpdate?.(x, v);
    if (Math.abs(x - to) < precision && Math.abs(v) < precision * 10) { onUpdate?.(to, 0); raf = 0; onDone?.(); return; }
    raf = requestAnimationFrame(tick);
  };
  raf = requestAnimationFrame((n) => { last = n; tick(n); });
  return { stop() { cancelAnimationFrame(raf); raf = 0; } };
}

export function tween({ from = 0, to = 1, duration = 320, ease = 'standard', onUpdate, onDone }: {
  from?: number; to?: number; duration?: number; ease?: EaseName | ((t: number) => number);
  onUpdate?: (v: number) => void; onDone?: () => void;
}): Stoppable {
  if (reduced()) { onUpdate?.(to); onDone?.(); return { stop() {} }; }
  const f = typeof ease === 'function' ? ease : EASE[ease] || EASE.standard;
  let raf = 0, t0: number | undefined;
  const tick = (n: number) => { t0 ??= n; const p = Math.min(1, (n - t0) / duration); onUpdate?.(from + (to - from) * f(p)); if (p < 1) raf = requestAnimationFrame(tick); else onDone?.(); };
  raf = requestAnimationFrame(tick);
  return { stop: () => cancelAnimationFrame(raf) };
}

/** G-dot physics: .target(x,y) then .step(dt) → {x,y}. */
export function follower({ zeta, k }: { zeta: number; k: number }) {
  const c = 2 * zeta * Math.sqrt(k);
  const s = { x: 0, y: 0, vx: 0, vy: 0, tx: 0, ty: 0 };
  return {
    target(x: number, y: number) { s.tx = x; s.ty = y; },
    jump(x: number, y: number) { s.x = s.tx = x; s.y = s.ty = y; s.vx = s.vy = 0; },
    step(dt: number) {
      if (reduced()) { s.x = s.tx; s.y = s.ty; return s; }
      while (dt > 0) { const h = Math.min(dt, 1 / 240); s.vx += (-k * (s.x - s.tx) - c * s.vx) * h; s.vy += (-k * (s.y - s.ty) - c * s.vy) * h; s.x += s.vx * h; s.y += s.vy * h; dt -= h; }
      return s;
    },
    state: s,
  };
}

/** Route draw-on (stroke-dash). Reduced motion: 220 ms fade in. */
export function drawOn(path: SVGGeometryElement, { duration = 1800, delay = 0 } = {}): Animation {
  const len = path.getTotalLength();
  if (reduced()) return path.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 220, fill: 'both' });
  path.style.strokeDasharray = `${len} ${len}`;
  return path.animate([{ strokeDashoffset: len }, { strokeDashoffset: 0 }], { duration, delay, easing: 'cubic-bezier(0.65, 0, 0.35, 1)', fill: 'both' });
}
