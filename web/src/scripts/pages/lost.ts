// S-WEB-11 404: fit the precomputed route to the section, draw the travelled 72 % on (1.8 s), dot the rest.
// Reduced motion: static final state (drawOn fades in over 220 ms). Redraws without animation on resize.
import { smoothPath, type XY } from '../../lib/geo';
import { drawOn } from '../motion';

const host = document.getElementById('lost-route');
const pts = host ? (JSON.parse(host.dataset.route || '[]') as XY[]) : [];

function draw(animate: boolean): void {
  if (!host || pts.length < 2) return;
  const w = host.clientWidth || 1440, h = host.clientHeight || 700;
  const B = w >= 960 ? { x: w * 0.45, y: h * 0.02, w: w * 0.6, h: h * 0.96 } : { x: 0, y: h * 0.45, w, h: h * 0.55 };
  const nx = Math.max(...pts.map((p) => p[0])), ny = Math.max(...pts.map((p) => p[1]));
  const sc = Math.min(B.w / nx, B.h / ny), ox = B.x + (B.w - nx * sc) / 2, oy = B.y + (B.h - ny * sc) / 2;
  const xy = pts.map(([x, y]): XY => [ox + x * sc, oy + y * sc]);
  const cut = Math.floor(xy.length * 0.72), end = xy[cut];
  host.innerHTML = `<svg viewBox="0 0 ${w} ${h}" aria-hidden="true"><path d="${smoothPath(xy)}" fill="none" stroke="var(--line-strong)" stroke-width="2" stroke-dasharray="2 8" stroke-linecap="round"/><path class="lost-line" d="${smoothPath(xy.slice(0, cut))}" fill="none" stroke="var(--accent)" stroke-width="3.5" stroke-linecap="round"/><circle cx="${end[0].toFixed(1)}" cy="${end[1].toFixed(1)}" r="16" fill="var(--accent)" opacity=".18"/><circle cx="${end[0].toFixed(1)}" cy="${end[1].toFixed(1)}" r="6" fill="var(--text)"/></svg>`;
  const line = host.querySelector<SVGPathElement>('.lost-line');
  if (line && animate) drawOn(line, { duration: 1800 });
}

draw(true);
let t = 0, lastW = host?.clientWidth;
addEventListener('resize', () => {
  clearTimeout(t);
  t = window.setTimeout(() => { if (host && host.clientWidth !== lastW) { lastW = host.clientWidth; draw(false); } }, 150);
});
