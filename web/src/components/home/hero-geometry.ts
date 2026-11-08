// Build-time: compact geometry for the HeroReplay island. The hero route must be re-projected to the measured
// hero box at runtime (desktop and mobile compose it differently), so the page ships the hero trip and six
// background roads as rotated (−6°), normalised x/y (+ t for the hero trip) instead of the 888 KB fixtures.
// Client projection reproduces lib/geo projector(): uniform scale, centred in the box, pad 0.
import { trackPoints, type Row } from '../../lib/geo';
import { trip, HERO_TRIP_ID, ROAD_TRIP_IDS } from '../../lib/data';

export interface HeroGeometry {
  /** span of the hero route in normalised units (max(span) = 1) */
  sx: number; sy: number;
  /** hero points: [x, y, t] */
  hero: [number, number, number][];
  /** background roads: [x, y][] each, simplified */
  roads: [number, number][][];
  cleanest: [number, number];
}

const ROTATE = -6;

function rdp(pts: [number, number][], eps: number): [number, number][] {
  if (pts.length < 3) return pts;
  const [ax, ay] = pts[0], [bx, by] = pts[pts.length - 1];
  const dx = bx - ax, dy = by - ay, L = Math.hypot(dx, dy) || 1e-12;
  let max = 0, idx = 0;
  for (let i = 1; i < pts.length - 1; i++) {
    const d = Math.abs((pts[i][0] - ax) * dy - (pts[i][1] - ay) * dx) / L;
    if (d > max) { max = d; idx = i; }
  }
  if (max <= eps) return [pts[0], pts[pts.length - 1]];
  return [...rdp(pts.slice(0, idx + 1), eps).slice(0, -1), ...rdp(pts.slice(idx), eps)];
}

export function heroGeometry(): HeroGeometry {
  const ht = trip(HERO_TRIP_ID);
  const { pts } = trackPoints(ht.rows as Row[]);
  const lat0 = pts.reduce((s, p) => s + p.lat, 0) / pts.length, k = Math.cos((lat0 * Math.PI) / 180);
  const c = Math.cos((ROTATE * Math.PI) / 180), s = Math.sin((ROTATE * Math.PI) / 180);
  const raw = (lat: number, lng: number): [number, number] => { const x = lng * k, y = -lat; return [x * c - y * s, x * s + y * c]; };
  const xy = pts.map((p) => raw(p.lat, p.lng));
  const x0 = Math.min(...xy.map((p) => p[0])), x1 = Math.max(...xy.map((p) => p[0]));
  const y0 = Math.min(...xy.map((p) => p[1])), y1 = Math.max(...xy.map((p) => p[1]));
  const S = Math.max(x1 - x0, y1 - y0);
  const n = (q: [number, number]): [number, number] => [+((q[0] - x0) / S).toFixed(4), +((q[1] - y0) / S).toFixed(4)];
  const hero = xy.map((q, i) => [...n(q), pts[i].t] as [number, number, number]);
  const roads = ROAD_TRIP_IDS.map((id) => {
    const rp = trackPoints(trip(id).rows as Row[]).pts.map((p) => n(raw(p.lat, p.lng)));
    return rdp(rp, 0.0006).map((q) => [+q[0].toFixed(4), +q[1].toFixed(4)] as [number, number]);
  });
  return { sx: +((x1 - x0) / S).toFixed(5), sy: +((y1 - y0) / S).toFixed(5), hero, roads, cleanest: [ht.cleanest!.t0, ht.cleanest!.t1] };
}
