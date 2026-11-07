// Score ring reveal and G-dot replay for build-time rendered mockups (ported from mockups.js animateRing/animateGG).
import { reduced, cssEase } from '../env';
import { tween, follower } from '../motion';

/** Ring reveal (motion.md §3.4): arc sweeps, number locked to the arc, record notch flips gold as it passes. */
export function animateRing(el: HTMLElement, { delay = 0 } = {}): void {
  const arc = el.querySelector<SVGCircleElement>('.ring-arc'), num = el.querySelector<HTMLElement>('.ring-num');
  const rec = el.querySelector<SVGLineElement>('.ring-record .notch-flip'), inner = el.querySelector<SVGLineElement>('.ring-record .notch-inner');
  if (!arc || !num) return;
  const score = +el.dataset.score!, prev = el.dataset.recordPrev ? +el.dataset.recordPrev : null, isNew = el.dataset.newRecord === 'true';
  const C = +arc.dataset.c!;
  const finish = () => { if (inner && isNew) inner.setAttribute('opacity', '0'); };
  if (reduced()) { finish(); return; }
  arc.style.strokeDashoffset = String(C); num.textContent = '0';
  let flipped = false;
  setTimeout(() => tween({
    from: 0, to: score, duration: 1400, ease: 'countUp',
    onUpdate: (v) => {
      arc.style.strokeDashoffset = String(C * (1 - v / 100)); num.textContent = String(Math.round(v));
      if (rec && isNew && prev != null && !flipped && v >= prev) {
        flipped = true; inner?.setAttribute('opacity', '0');
        rec.style.transformBox = 'fill-box'; rec.style.transformOrigin = 'center';
        rec.animate([{ transform: 'scale(1)' }, { transform: 'scale(1.5)' }, { transform: 'scale(1)' }], { duration: 520, easing: cssEase('--spring-pin-pop') });
      }
    },
    onDone: () => {
      num.animate([{ fontWeight: 600 }, { fontWeight: 760 }, { fontWeight: 600 }], { duration: 240 });
      arc.animate([{ filter: 'drop-shadow(0 0 0 transparent)' }, { filter: 'drop-shadow(0 0 3px var(--record))' }, { filter: 'drop-shadow(0 0 0 transparent)' }], { duration: 900 });
      finish();
    },
  }), delay);
}

/** G-dot replay: follows the cloud samples in order, ~3.6 s per loop, spring follower (motion.md §3.5).
 *  Pauses while off screen or the tab is hidden. Reduced motion: dot parked on a representative sample. */
export function animateGG(svg: SVGSVGElement, pts: [number, number][]): void {
  const size = +svg.getAttribute('width')!, c = size / 2, S = (size / 2 - 8) / 0.6;
  const dot = svg.querySelector('.gg-dot'), halo = svg.querySelector('.gg-halo'), trail = svg.querySelector('.gg-trail');
  if (!dot || !halo || !trail || !pts.length) return;
  const place = (x: number, y: number) => { for (const e of [dot, halo]) { e.setAttribute('cx', x.toFixed(1)); e.setAttribute('cy', y.toFixed(1)); } };
  if (reduced()) { const [la, lo] = pts[Math.floor(pts.length * 0.62)]; place(c + la * S, c - lo * S); return; }
  const f = follower({ zeta: 0.72, k: 260 });
  const hist: [number, number][] = [], dur = 3600;
  let raf = 0, t0: number | null = null, last: number | null = null, visible = true, elapsed = 0;
  const tick = (now: number) => {
    raf = 0;
    const dt = last ? (now - last) / 1000 : 0.016; last = now;
    if (t0 === null) t0 = now - elapsed;
    let p = (now - t0) / dur; if (p > 1) { t0 = now; p = 0; }
    elapsed = now - t0;
    const i = Math.min(pts.length - 1, Math.floor(p * pts.length));
    f.target(pts[i][0], pts[i][1]); const s = f.step(Math.min(dt, 0.064));
    const x = c + s.x * S, y = c - s.y * S;
    place(x, y);
    hist.push([x, y]); if (hist.length > 28) hist.shift();
    trail.setAttribute('d', hist.map((q, k) => `${k ? 'L' : 'M'}${q[0].toFixed(1)} ${q[1].toFixed(1)}`).join(''));
    if (visible && !document.hidden) raf = requestAnimationFrame(tick);
  };
  const kick = () => { if (!raf && visible && !document.hidden) { last = null; t0 = null; raf = requestAnimationFrame(tick); } };
  if ('IntersectionObserver' in window) new IntersectionObserver((en) => { visible = en[0].isIntersecting; kick(); }).observe(svg);
  document.addEventListener('visibilitychange', kick);
  kick();
}
