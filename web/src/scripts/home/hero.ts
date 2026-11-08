// HeroReplay (motion v2), ported from prototypes/web/js/home.js drawHero() + replay.
// Route draw-on → replay marker with a bright trail glides the visible window of the route (computed so it
// never runs behind the copy mask or the phones); entering the cleanest stretch lifts it and shows a chip.
// Loops ≈ 15 s; paused off screen / tab hidden. Reduced motion: static final state, no marker.
// Devices are server-rendered and sized in CSS (no layout shift); this module only animates them.
import { reduced, cssEase } from '../env';
import { EASE } from '../motion';
import { smoothPath, type XY } from '../../lib/geo';
import { animateRing } from './ring';

interface Geo { sx: number; sy: number; hero: [number, number, number][]; roads: [number, number][][]; cleanest: [number, number] }

const hero = document.querySelector<HTMLElement>('.hero');
const svg = document.getElementById('hero-route') as SVGSVGElement | null;
const devs = document.getElementById('hero-devices');
const geoEl = document.getElementById('hero-geo');

if (hero && svg && devs && geoEl) {
  const G: Geo = JSON.parse(geoEl.textContent || '{}');
  const RM = reduced();

  /* Devices: rise in, then the iPhone's ring counts up (ring tween 1.4 s after a 1.5 s delay). */
  const ringEl = devs.querySelector<HTMLElement>('.hero-ios .ring');
  animateRing(ringEl, { delay: RM ? 0 : 1500 });
  if (!RM) {
    devs.querySelectorAll<HTMLElement>('.device').forEach((d, i) => d.animate([{ opacity: 0, transform: 'translateY(40px)' }, { opacity: 1, transform: 'none' }], { duration: 900, delay: 250 + i * 140, easing: 'cubic-bezier(0.32, 0.72, 0, 1)', fill: 'both' }));
  }

  interface ReplayEl { main: SVGPathElement; L: number; c0: number; c1: number; cl: SVGPathElement; win: [number, number]; trail: SVGPathElement; marker: SVGGElement; chip: SVGGElement | null }

  /* Replay marker loop */
  const replay = (() => {
    let el: ReplayEl | null = null, raf = 0, startAt = 0, pausedAt = 0, visible = true, armed = false, wait = 0;
    const SPEED = 1 / 15000, GAP = 1600, TRAIL = 0.07;
    const ss = (a: number, b: number, x: number) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
    const frame = (now: number) => {
      raf = 0;
      if (!el || !visible || document.hidden) return;
      const [ua, ub] = el.win, PERIOD = Math.max(4000, (ub - ua) / SPEED);
      const t = (now - startAt) % (PERIOD + GAP), v = Math.min(1, t / PERIOD), u = ua + (ub - ua) * v;
      const fade = t > PERIOD ? 0 : ss(0, 0.06, v) * (1 - ss(0.92, 1, v));
      const pt = el.main.getPointAtLength(el.L * u);
      el.marker.setAttribute('transform', `translate(${pt.x.toFixed(1)} ${pt.y.toFixed(1)})`);
      el.marker.setAttribute('opacity', fade.toFixed(3));
      const tl = el.L * TRAIL, head = el.L * u;
      el.trail.style.strokeDasharray = `${Math.min(tl, head).toFixed(1)} ${el.L.toFixed(1)}`;
      el.trail.style.strokeDashoffset = (-Math.max(0, head - tl)).toFixed(1);
      el.trail.setAttribute('opacity', (0.85 * fade).toFixed(3));
      const inClean = ss(el.c0 - 0.01, el.c0 + 0.02, u) * (1 - ss(el.c1 + 0.05, el.c1 + 0.12, u));
      el.cl.setAttribute('opacity', (0.55 + 0.45 * inClean).toFixed(3));
      if (el.chip) el.chip.setAttribute('opacity', inClean.toFixed(3));
      raf = requestAnimationFrame(frame);
    };
    const kick = () => { if (!raf && armed && visible && !document.hidden) raf = requestAnimationFrame(frame); };
    if ('IntersectionObserver' in window) new IntersectionObserver((en) => { visible = en[0].isIntersecting; if (visible) { startAt += performance.now() - pausedAt; kick(); } else pausedAt = performance.now(); }, { threshold: 0.05 }).observe(hero);
    document.addEventListener('visibilitychange', () => { if (document.hidden) pausedAt = performance.now(); else { startAt += performance.now() - pausedAt; kick(); } });
    return {
      set(x: ReplayEl) { el = x; },
      start(delay: number) {
        if (RM) return;
        clearTimeout(wait);
        wait = window.setTimeout(() => { armed = true; startAt = performance.now(); pausedAt = startAt; kick(); }, delay);
      },
    };
  })();

  function drawHero(animate: boolean): void {
    const w = hero!.clientWidth, h = hero!.clientHeight, desktop = w >= 960;
    svg!.setAttribute('viewBox', `0 0 ${w} ${h}`);
    const B = desktop ? { x: w * 0.44, y: h * 0.03, w: w * 0.64, h: h * 0.84 } : { x: -w * 0.05, y: h * 0.5, w: w * 1.1, h: h * 0.5 };
    const sc = Math.min(B.w / G.sx, B.h / G.sy), ox = B.x + (B.w - G.sx * sc) / 2, oy = B.y + (B.h - G.sy * sc) / 2;
    const P = (x: number, y: number): XY => [ox + x * sc, oy + y * sc];
    const xy = G.hero.map(([x, y]) => P(x, y)), ts = G.hero.map((p) => p[2]);
    const [t0, t1] = G.cleanest;
    const clean = smoothPath(xy.filter((_, i) => ts[i] >= t0 && ts[i] <= t1));
    const d = smoothPath(xy);
    const roads = G.roads.map((r) => smoothPath(r.map(([x, y]) => P(x, y)))).join(' ');
    const cum = [0]; for (let i = 1; i < xy.length; i++) cum.push(cum[i - 1] + Math.hypot(xy[i][0] - xy[i - 1][0], xy[i][1] - xy[i - 1][1]));
    const tot = cum[cum.length - 1], fr = (t: number) => { const i = ts.findIndex((x) => x >= t); return cum[Math.max(0, i)] / tot; };
    const c0 = fr(t0), c1 = fr(t1);
    const mid = xy[ts.findIndex((x) => x >= (t0 + t1) / 2)];
    svg!.innerHTML = `
        <defs>
          <filter id="hglow" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="${desktop ? 10 : 7}"/></filter>
          <radialGradient id="hhead"><stop offset="0" stop-color="#fff"/><stop offset=".35" stop-color="#C6F8EA" stop-opacity=".9"/><stop offset="1" stop-color="#5BE7C4" stop-opacity="0"/></radialGradient>
          <linearGradient id="hfade" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#5BE7C4" stop-opacity=".35"/><stop offset=".35" stop-color="#5BE7C4"/><stop offset="1" stop-color="#C6F8EA"/></linearGradient>
        </defs>
        <path d="${roads}" fill="none" stroke="var(--line-strong)" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" opacity=".55"/>
        <path d="${d}" fill="none" stroke="var(--line-strong)" stroke-width="2" stroke-linecap="round"/>
        <path class="hr-glow" d="${d}" fill="none" stroke="#5BE7C4" stroke-width="${desktop ? 16 : 12}" stroke-linecap="round" opacity=".28" filter="url(#hglow)"/>
        <path class="hr-main" d="${d}" fill="none" stroke="url(#hfade)" stroke-width="${desktop ? 4 : 3.2}" stroke-linecap="round" stroke-linejoin="round"/>
        <path class="hr-clean" d="${clean}" fill="none" stroke="#C6F8EA" stroke-width="${desktop ? 6 : 5}" stroke-linecap="round" opacity="0"/>
        ${desktop && mid ? `<g class="hr-chip" opacity="0" transform="translate(${Math.max(16, mid[0] - 245).toFixed(1)} ${Math.max(84, mid[1] - 58).toFixed(1)})"><rect width="236" height="34" rx="17" fill="var(--glass)" stroke="var(--glass-stroke)"/><circle cx="18" cy="17" r="4" fill="#5BE7C4"/><text x="31" y="21.5" font-size="12.5" font-weight="600" fill="var(--text)" style="font-family:var(--f-text)">Cleanest stretch · 14.2 km, QEW</text></g>` : ''}
        <path class="hr-trail" d="${d}" fill="none" stroke="#FFFFFF" stroke-width="${desktop ? 3.5 : 3}" stroke-linecap="round" opacity="0"/>
        <g class="hr-marker" opacity="0"><circle r="${desktop ? 20 : 15}" fill="url(#hhead)" opacity=".85"/><circle r="${desktop ? 5.5 : 4.5}" fill="#fff"/></g>
        <circle class="hr-head" r="${desktop ? 18 : 13}" fill="url(#hhead)" opacity="0"/>
        <circle class="hr-dot" r="${desktop ? 6 : 5}" fill="#fff" opacity="0"/>`;
    const q = <T extends Element>(s: string) => svg!.querySelector<T>(s)!;
    const main = q<SVGPathElement>('.hr-main'), glow = q<SVGPathElement>('.hr-glow'), head = q<SVGCircleElement>('.hr-head'), dot = q<SVGCircleElement>('.hr-dot'), cl = q<SVGPathElement>('.hr-clean');
    const L = main.getTotalLength(), end = main.getPointAtLength(L);
    // Run the marker only over the longest stretch of line that isn't hidden by the copy mask or the phones
    const hr = hero!.getBoundingClientRect();
    const devRects = [...devs!.querySelectorAll('.device')].map((dv) => { const b = dv.getBoundingClientRect(); return { l: b.left - hr.left - 8, t: b.top - hr.top - 8, r: b.right - hr.left + 8, b: b.bottom - hr.top + 8 }; });
    const seen = (x: number, y: number) => (desktop ? x > w * 0.52 : y > h * 0.6) && y > 70 && x < w - 8 && !devRects.some((r) => x > r.l && x < r.r && y > r.t && y < r.b);
    let best: [number, number] = [0, 1], run: number | null = null, bestLen = 0;
    for (let k = 0; k <= 240; k++) { const u = k / 240, pt = main.getPointAtLength(L * u); if (seen(pt.x, pt.y)) { if (run === null) run = u; if (u - run > bestLen) { bestLen = u - run; best = [run, u]; } } else run = null; }
    replay.set({ main, L, c0, c1, cl, win: bestLen > 0.12 ? best : [0, 1], trail: q<SVGPathElement>('.hr-trail'), marker: q<SVGGElement>('.hr-marker'), chip: svg!.querySelector<SVGGElement>('.hr-chip') });
    const settle = () => {
      dot.setAttribute('cx', String(end.x)); dot.setAttribute('cy', String(end.y)); dot.setAttribute('opacity', '1');
      head.setAttribute('cx', String(end.x)); head.setAttribute('cy', String(end.y)); head.setAttribute('opacity', '.55');
      cl.setAttribute('opacity', '.9');
    };
    if (!animate || RM) {
      settle();
      if (animate) [main, glow].forEach((p) => p.animate([{ opacity: 0 }, { opacity: getComputedStyle(p).opacity }], { duration: 220 }));
      replay.start(animate ? 2400 : 600);
      return;
    }
    [main, glow].forEach((p) => { p.style.strokeDasharray = `${L} ${L}`; });
    const dur = 2400, delay = 350; let s0: number | undefined;
    const tick = (now: number) => {
      s0 ??= now; const p = Math.max(0, Math.min(1, (now - s0 - delay) / dur)), e = EASE.drawOn(p);
      const off = String(L * (1 - e)); main.style.strokeDashoffset = off; glow.style.strokeDashoffset = off;
      const pt = main.getPointAtLength(L * e);
      head.setAttribute('cx', String(pt.x)); head.setAttribute('cy', String(pt.y)); head.setAttribute('opacity', p > 0 && p < 1 ? '1' : '0');
      if (p < 1) requestAnimationFrame(tick);
      else {
        main.style.strokeDasharray = ''; glow.style.strokeDasharray = '';
        settle();
        cl.animate([{ opacity: 0 }, { opacity: 0.9 }], { duration: 700, easing: 'ease-out' });
        dot.style.transformBox = 'fill-box'; dot.style.transformOrigin = 'center';
        dot.animate([{ transform: 'scale(0)' }, { transform: 'scale(1)' }], { duration: 540, easing: cssEase('--spring-pin-pop', 'ease-out') });
        replay.start(1600);
      }
    };
    requestAnimationFrame(tick);
  }

  drawHero(true);
  let rz = 0, size = `${hero.clientWidth}x${hero.clientHeight}`;
  addEventListener('resize', () => {
    clearTimeout(rz);
    rz = window.setTimeout(() => {
      const now = `${hero.clientWidth}x${hero.clientHeight}`;
      if (now === size) return; // e.g. a mobile URL bar that doesn't change the hero box
      size = now; drawHero(false);
    }, 150);
  });
}
