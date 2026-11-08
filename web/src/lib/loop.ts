// The Loop mark (brand.md §3.1: 270° fading trail + round head + G-dot 34° ahead), ported from site.js `loop()`.
let loopN = 0;

export interface LoopOptions { size?: number; color?: string; dot?: string; title?: string; glow?: boolean; id?: string }

export function loop({ size = 34, color = 'var(--accent)', dot = 'var(--text)', title = '', glow = false, id: idIn }: LoopOptions = {}): string {
  const cx = 32, cy = 32, r = 18, sw = 7, a0 = -348, a1 = -78, n = 6, id = idIn ?? 'dpl' + loopN++;
  const P = (a: number): [number, number] => [cx + r * Math.cos((a * Math.PI) / 180), cy + r * Math.sin((a * Math.PI) / 180)];
  let defs = '', arcs = '';
  for (let i = 0; i < n; i++) {
    const s = a0 + ((a1 - a0) * i) / n, e = a0 + ((a1 - a0) * (i + 1)) / n;
    const u0 = i / n, u1 = (i + 1) / n, o0 = 0.03 + 0.97 * u0 ** 1.5, o1 = 0.03 + 0.97 * u1 ** 1.5;
    const [x0, y0] = P(s), [x1, y1] = P(e);
    defs += `<linearGradient id="${id}g${i}" gradientUnits="userSpaceOnUse" x1="${x0.toFixed(2)}" y1="${y0.toFixed(2)}" x2="${x1.toFixed(2)}" y2="${y1.toFixed(2)}"><stop offset="0" stop-color="${color}" stop-opacity="${o0.toFixed(3)}"/><stop offset="1" stop-color="${color}" stop-opacity="${o1.toFixed(3)}"/></linearGradient>`;
    const [ex, ey] = P(e + (i < n - 1 ? 0.6 : 0));
    arcs += `<path d="M${x0.toFixed(2)} ${y0.toFixed(2)} A${r} ${r} 0 0 1 ${ex.toFixed(2)} ${ey.toFixed(2)}" stroke="url(#${id}g${i})" stroke-width="${sw}" fill="none"/>`;
  }
  const [hx, hy] = P(a1), [dx, dy] = P(a1 + 34);
  const f = glow ? `<filter id="${id}f" x="-40%" y="-40%" width="180%" height="180%"><feGaussianBlur stdDeviation="2.2" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>` : '';
  return `<svg viewBox="0 0 64 64" width="${size}" height="${size}" ${title ? `role="img" aria-label="${title}"` : 'aria-hidden="true"'}><defs>${defs}${f}</defs><g ${glow ? `filter="url(#${id}f)"` : ''}>${arcs}<circle cx="${hx.toFixed(2)}" cy="${hy.toFixed(2)}" r="${sw / 2}" fill="${color}"/><circle cx="${dx.toFixed(2)}" cy="${dy.toFixed(2)}" r="${((sw * 1.22) / 2).toFixed(2)}" fill="${dot}"/></g></svg>`;
}
