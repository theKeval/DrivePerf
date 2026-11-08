// ScoreRing reveal and G-dot replay (motion.md §3.4, §3.5), ported from prototypes/web/js/mockups.js.
import { reduced } from '../env';
import { spring, tween, follower } from '../motion';

const POP = 'cubic-bezier(0.32, 0.72, 0, 1)';

/** Keep the number's box at its final (server-rendered) width while it counts up from 0, so "0" → "91" doesn't
 *  re-centre the digits (a layout shift in the prototype). */
export function lockWidth(num: HTMLElement): void {
  if (num.style.minWidth) return;
  const w = num.getBoundingClientRect().width;
  if (w) { num.style.minWidth = `${w}px`; num.style.textAlign = 'center'; }
}

/** Ring reveal: arc sweeps, number locked to the arc, record notch flips gold as it passes.
 *  `spring`: damped count-up (ζ 0.78, k 16; arc may overshoot 1–2 %, number clamps to the score). */
export function animateRing(el: HTMLElement | null, { delay = 0, spring: useSpring = false, onDone }: { delay?: number; spring?: boolean; onDone?: () => void } = {}): void {
  if (!el) return;
  const arc = el.querySelector<SVGCircleElement>('.ring-arc')!, num = el.querySelector<HTMLElement>('.ring-num')!;
  const rec = el.querySelector<SVGElement>('.ring-record .notch-flip'), inner = el.querySelector<SVGElement>('.ring-record .notch-inner');
  const score = +el.dataset.score!, prev = el.dataset.recordPrev ? +el.dataset.recordPrev : null, isNew = el.dataset.newRecord === 'true';
  const C = +arc.dataset.c!;
  const finish = () => { if (inner && isNew) inner.setAttribute('opacity', '0'); onDone?.(); };
  if (reduced()) { arc.style.strokeDashoffset = String(C * (1 - score / 100)); num.textContent = String(score); finish(); return; }
  lockWidth(num);
  arc.style.strokeDashoffset = String(C); num.textContent = '0';
  let flipped = false;
  const flip = (v: number) => {
    if (rec && isNew && prev != null && !flipped && v >= prev) {
      flipped = true; if (inner) inner.setAttribute('opacity', '0');
      rec.style.transformBox = 'fill-box'; rec.style.transformOrigin = 'center';
      rec.animate([{ transform: 'scale(1)' }, { transform: 'scale(1.5)' }, { transform: 'scale(1)' }], { duration: 520, easing: POP });
    }
  };
  if (useSpring) {
    const land = () => {
      num.animate([{ transform: 'scale(1)' }, { transform: 'scale(1.06)' }, { transform: 'scale(1)' }], { duration: 360, easing: POP });
      if (isNew) arc.animate([{ opacity: 1 }, { opacity: 0.72 }, { opacity: 1 }], { duration: 900, easing: 'ease-in-out' });
      finish();
    };
    setTimeout(() => spring({ from: 0, to: score, zeta: 0.78, k: 16, precision: 0.05, onUpdate: (v) => {
      const c = Math.max(0, v); arc.style.strokeDashoffset = String(C * (1 - Math.min(c, 100) / 100));
      num.textContent = String(Math.round(Math.min(c, score))); flip(c);
    }, onDone: land }), delay);
    return;
  }
  setTimeout(() => {
    tween({ from: 0, to: score, duration: 1400, ease: 'countUp', onUpdate: (v) => {
      arc.style.strokeDashoffset = String(C * (1 - v / 100)); num.textContent = String(Math.round(v)); flip(v);
    }, onDone: () => {
      num.animate([{ fontWeight: 600 }, { fontWeight: 760 }, { fontWeight: 600 }], { duration: 240 });
      arc.animate([{ filter: 'drop-shadow(0 0 0 transparent)' }, { filter: 'drop-shadow(0 0 3px var(--record))' }, { filter: 'drop-shadow(0 0 0 transparent)' }], { duration: 900 });
      finish();
    } });
  }, delay);
}

/** G-dot replay: follows the cloud samples in order, compressed into ~3.6 s, spring follower. Returns stop(). */
export function animateGG(svg: SVGSVGElement | null, pts: [number, number][], { loop = true } = {}): () => void {
  if (!svg) return () => {};
  const size = +svg.getAttribute('width')!, c = size / 2, S = (size / 2 - 8) / 0.6;
  const dot = svg.querySelector('.gg-dot'), halo = svg.querySelector('.gg-halo'), trail = svg.querySelector('.gg-trail');
  if (!dot || !halo || !trail || !pts.length) return () => {};
  const put = (x: number, y: number) => { for (const e of [dot, halo]) { e.setAttribute('cx', x.toFixed(1)); e.setAttribute('cy', y.toFixed(1)); } };
  if (reduced()) { const [la, lo] = pts[Math.floor(pts.length * 0.62)]; put(c + la * S, c - lo * S); return () => {}; }
  const f = follower({ zeta: 0.72, k: 260 });
  let raf = 0, t0: number | null = null, last: number | null = null;
  const hist: [number, number][] = [], dur = 3600;
  const tick = (now: number) => {
    t0 ??= now; const dt = last ? (now - last) / 1000 : 0.016; last = now;
    let p = (now - t0) / dur; if (p > 1) { if (!loop) return; t0 = now; p = 0; }
    const i = Math.min(pts.length - 1, Math.floor(p * pts.length));
    f.target(pts[i][0], pts[i][1]); const s = f.step(dt);
    const x = c + s.x * S, y = c - s.y * S;
    put(x, y);
    hist.push([x, y]); if (hist.length > 28) hist.shift();
    trail.setAttribute('d', hist.map((q, k) => `${k ? 'L' : 'M'}${q[0].toFixed(1)} ${q[1].toFixed(1)}`).join(''));
    raf = requestAnimationFrame(tick);
  };
  raf = requestAnimationFrame(tick);
  return () => cancelAnimationFrame(raf);
}
