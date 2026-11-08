// Route geometry, ported from prototypes/web/js/mockups.js. Pure functions: used at build time to render SVG
// strings, and by client islands that must re-project routes to the measured layout (hero).

export type XY = [number, number];
/** Slim fixture track row: [t, lat, lng, lonG, latG] (lat/lng null in a gap). */
export type Row = [number, number | null, number | null, number | null, number | null];
export interface TrackPoint { lat: number; lng: number; t: number; d: number }
export interface Box { x: number; y: number; w: number; h: number }
export interface ProjectOpts { pad?: number; rotate?: number; fit?: 'contain' | 'cover'; box?: Box | null }

const R_EARTH = 6371000;
export function hav(a: XY, b: XY): number {
  const t = Math.PI / 180, dLat = (b[0] - a[0]) * t, dLng = (b[1] - a[1]) * t;
  const s = Math.sin(dLat / 2) ** 2 + Math.cos(a[0] * t) * Math.cos(b[0] * t) * Math.sin(dLng / 2) ** 2;
  return 2 * R_EARTH * Math.asin(Math.sqrt(s));
}

/** Observed track points with cumulative distance; ends trimmed (privacy: never show where a drive starts or ends). */
export function trackPoints(rows: Row[], { trimStart = 650, trimEnd = 520 } = {}): { pts: TrackPoint[]; total: number } {
  const pts: TrackPoint[] = [];
  let cum = 0, prev: XY | null = null;
  for (const r of rows) {
    if (r[1] == null || r[2] == null) continue;
    const p: XY = [r[1], r[2]];
    if (prev) cum += hav(prev, p);
    pts.push({ lat: p[0], lng: p[1], t: r[0], d: cum });
    prev = p;
  }
  return { pts: pts.filter((p) => p.d >= trimStart && p.d <= cum - trimEnd), total: cum };
}

export type Projector = (lat: number, lng: number) => XY;

/** Projector for a w×h box (equirectangular, aspect preserved, optional rotation). */
export function projector(pts: TrackPoint[], w: number, h: number, { pad = 16, rotate = 0, fit = 'contain', box = null }: ProjectOpts = {}): Projector {
  const lat0 = pts.reduce((s, p) => s + p.lat, 0) / pts.length, k = Math.cos((lat0 * Math.PI) / 180);
  const c = Math.cos((rotate * Math.PI) / 180), s = Math.sin((rotate * Math.PI) / 180);
  const raw = (lat: number, lng: number): XY => { const x = lng * k, y = -lat; return [x * c - y * s, x * s + y * c]; };
  const xy = pts.map((p) => raw(p.lat, p.lng));
  const xs = xy.map((p) => p[0]), ys = xy.map((p) => p[1]);
  const x0 = Math.min(...xs), x1 = Math.max(...xs), y0 = Math.min(...ys), y1 = Math.max(...ys);
  const B = box || { x: 0, y: 0, w, h };
  const sc = (fit === 'cover' ? Math.max : Math.min)((B.w - 2 * pad) / (x1 - x0), (B.h - 2 * pad) / (y1 - y0));
  const ox = B.x + (B.w - (x1 - x0) * sc) / 2, oy = B.y + (B.h - (y1 - y0) * sc) / 2;
  return (lat, lng) => { const [x, y] = raw(lat, lng); return [ox + (x - x0) * sc, oy + (y - y0) * sc]; };
}

/** Catmull-Rom → cubic Bézier, so a 5–12 s sample track reads as a smooth road line at hero size. */
export function smoothPath(xy: XY[], tension = 0.5): string {
  if (xy.length < 2) return '';
  let d = `M${xy[0][0].toFixed(1)} ${xy[0][1].toFixed(1)}`;
  for (let i = 0; i < xy.length - 1; i++) {
    const p0 = xy[i - 1] || xy[i], p1 = xy[i], p2 = xy[i + 1], p3 = xy[i + 2] || p2;
    const c1 = [p1[0] + ((p2[0] - p0[0]) * tension) / 3, p1[1] + ((p2[1] - p0[1]) * tension) / 3];
    const c2 = [p2[0] - ((p3[0] - p1[0]) * tension) / 3, p2[1] - ((p3[1] - p1[1]) * tension) / 3];
    d += ` C${c1[0].toFixed(1)} ${c1[1].toFixed(1)} ${c2[0].toFixed(1)} ${c2[1].toFixed(1)} ${p2[0].toFixed(1)} ${p2[1].toFixed(1)}`;
  }
  return d;
}

export interface RouteGeom { d: string; xy: XY[]; pts: TrackPoint[]; P: Projector; segment(t0: number, t1: number): string; at(t: number): XY }

/** Route as a smooth path. Pass opts.P to reuse another route's projection. */
export function route(rows: Row[], w: number, h: number, opts: ProjectOpts & { P?: Projector; trimStart?: number; trimEnd?: number } = {}): RouteGeom {
  const { pts } = trackPoints(rows, opts);
  const P = opts.P || projector(pts, w, h, opts);
  const xy = pts.map((p) => P(p.lat, p.lng));
  const segment = (t0: number, t1: number) => smoothPath(xy.filter((_, i) => pts[i].t >= t0 && pts[i].t <= t1));
  const at = (t: number): XY => { let i = pts.findIndex((p) => p.t >= t); if (i < 0) i = pts.length - 1; return xy[i]; };
  return { d: smoothPath(xy), xy, pts, P, segment, at };
}

/** SVG path for a glyph (normalised 0..1 points) inside a w×h box with padding p (DP_DATA.util.glyphPath). */
export function glyphPath(pts: XY[], w = 100, h = 100, p = 8): string {
  const s = Math.min(w, h) - 2 * p, ox = (w - s) / 2, oy = (h - s) / 2;
  return pts.map((q, i) => `${i ? 'L' : 'M'}${(ox + q[0] * s).toFixed(1)} ${(oy + q[1] * s).toFixed(1)}`).join(' ');
}
