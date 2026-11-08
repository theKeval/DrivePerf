// Data-driven visuals and stylised device mockups, ported from prototypes/web/js/mockups.js (render half only).
// Rendered to HTML/SVG strings at build time. The animation half lives in src/scripts/motion/*.ts.
import { route, smoothPath, type XY } from './geo';
import { DATA, trip as getTrip, vehicle, HERO_TRIP_ID, NEEDED_TRIP_ID, type Trip } from './data';

export const esc = (s: unknown) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!);

/* ---------- Score ring (components.md §3) ---------- */
let ringN = 0;
export interface RingOpts { score: number; baseline?: number | null; recordPrev?: number | null; isNewRecord?: boolean; label?: string; strokeW?: number }
export function ring({ score, baseline = null, recordPrev = null, isNewRecord = false, label = 'Smoothness', strokeW = 8 }: RingOpts): string {
  const id = 'rg' + ringN++, r = 42, C = 2 * Math.PI * r;
  const notch = (v: number, cls: string, extra = '') => {
    const a = (v / 100) * 2 * Math.PI, x1 = 50 + (r - 7.5) * Math.cos(a), y1 = 50 + (r - 7.5) * Math.sin(a), x2 = 50 + (r + 7.5) * Math.cos(a), y2 = 50 + (r + 7.5) * Math.sin(a);
    return `<line class="${cls}" x1="${x1.toFixed(2)}" y1="${y1.toFixed(2)}" x2="${x2.toFixed(2)}" y2="${y2.toFixed(2)}" ${extra}/>`;
  };
  const recNotch = recordPrev != null
    ? `<g class="ring-record">${notch(recordPrev, 'notch-flip', `stroke="var(--record)" stroke-width="3" stroke-linecap="round" stroke-dasharray="0" data-outline="1"`)}${notch(recordPrev, 'notch-inner', 'stroke="var(--bg, #05070A)" stroke-width="1.2" stroke-linecap="round"')}</g>` : '';
  const aria = `${label} ${score}${baseline != null ? `, ${score - baseline > 0 ? score - baseline + ' above' : 'right on'} your usual` : ''}${isNewRecord ? '. New route record' : ''}.`;
  return `<div class="ring" data-component="ScoreRing" data-score="${score}" data-record-prev="${recordPrev ?? ''}" data-new-record="${isNewRecord}" role="img" aria-label="${aria.trim()}">
      <svg viewBox="0 0 100 100" aria-hidden="true">
        <defs><linearGradient id="${id}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="var(--ring-gradient-c)"/><stop offset=".45" stop-color="var(--ring-gradient-b)"/><stop offset="1" stop-color="var(--ring-gradient-a)"/></linearGradient></defs>
        <circle cx="50" cy="50" r="${r}" fill="none" stroke="var(--fill)" stroke-width="${strokeW + 1.5}"/>
        <circle class="ring-arc" cx="50" cy="50" r="${r}" fill="none" stroke="url(#${id})" stroke-width="${strokeW}" stroke-linecap="round" stroke-dasharray="${C.toFixed(2)}" stroke-dashoffset="${(C * (1 - score / 100)).toFixed(2)}" data-c="${C.toFixed(2)}"/>
        ${baseline != null ? notch(baseline, 'ring-baseline', 'stroke="var(--text-2)" stroke-width="1.6" stroke-linecap="round"') : ''}
        ${recNotch}
      </svg>
      <div class="ring__center"><span class="ring__value num"><span class="ring-num">${score}</span></span>${label ? `<span class="ring__label">${label}</span>` : ''}</div>
    </div>`;
}

/* ---------- G-G circle (components.md §6), personal visual: no numbers on marketing surfaces ---------- */
export function gg(t: Trip, { size = 200, dot = true } = {}): string {
  const c = size / 2, S = (size / 2 - 8) / 0.6; // fixed ±0.6 g
  const pts = t.gg!.points;
  const rings = [0.1, 0.2, 0.3, 0.4, 0.5].map((g) => `<circle cx="${c}" cy="${c}" r="${(g * S).toFixed(1)}" fill="none" stroke="var(--gg-ring, #FFFFFF1F)" stroke-width="${g === 0.3 || g === 0.5 ? 1 : 0.6}"/>`).join('');
  const cloud = pts.map(([lat, lon]) => `<circle cx="${(c + lat * S).toFixed(1)}" cy="${(c - lon * S).toFixed(1)}" r="1.5"/>`).join('');
  const env = t.gg!.envelope.radiiG.map((g, i): XY => { const a = (i * 15 * Math.PI) / 180; return [c + Math.cos(a) * g * S, c - Math.sin(a) * g * S]; });
  const envD = smoothPath([...env, env[0], env[1]], 0.6);
  return `<svg class="gg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" role="img" aria-label="Friction circle: a cloud of every brake and turn on this drive. Most inputs stayed close to the centre.">
      ${rings}<path d="M${c} 6V${size - 6}M6 ${c}H${size - 6}" stroke="var(--gg-ring, #FFFFFF1F)" stroke-width=".8"/>
      <g fill="var(--gg-cloud, #5BE7C4)" opacity=".32">${cloud}</g>
      <path d="${envD}" fill="none" stroke="var(--gg-cloud, #5BE7C4)" stroke-opacity=".75" stroke-width="1.5"/>
      ${dot ? `<path class="gg-trail" fill="none" stroke="#fff" stroke-opacity=".5" stroke-width="2" stroke-linecap="round"/><circle class="gg-halo" cx="${c}" cy="${c}" r="9" fill="#fff" opacity=".18"/><circle class="gg-dot" cx="${c}" cy="${c}" r="4.5" fill="#fff"/>` : ''}
    </svg>`;
}
/** G-dot replay samples for the client island (rounded; the cloud itself is static SVG). */
export const ggSamples = (t: Trip): XY[] => t.gg!.points.map(([a, b]) => [+a.toFixed(3), +b.toFixed(3)]);

/* ---------- Devices ---------- */
const status = (platform: string) => platform === 'android'
  ? `<div class="device__status"><span>7:52</span><i><svg width="14" height="10" viewBox="0 0 14 10" aria-hidden="true"><path d="M7 9.5L.5 3a9.5 9.5 0 0 1 13 0z" fill="#F2F4F7"/></svg><b></b></i></div>`
  : `<div class="device__status"><span>7:52</span><i><svg width="16" height="10" viewBox="0 0 16 10" aria-hidden="true"><rect x="0" y="6" width="3" height="4" rx="1" fill="#F2F4F7"/><rect x="4.3" y="4" width="3" height="6" rx="1" fill="#F2F4F7"/><rect x="8.6" y="2" width="3" height="8" rx="1" fill="#F2F4F7"/><rect x="12.9" y="0" width="3" height="10" rx="1" fill="#F2F4F7"/></svg><b></b></i></div>`;
export function device(platform: 'ios' | 'android', inner: string, { width = 300, label = '', cls = '', style = '' } = {}): string {
  return `<div class="device device--${platform} ${cls}" style="--w:${width}px;${style}" data-component="DevicePhone" data-platform="${platform}">
      <div class="device__screen">${status(platform)}<div class="device__island"></div>${inner}<div class="device__home"></div></div>
      ${label ? `<span class="device__label">${label}</span>` : ''}
    </div>`;
}

/* ---------- App screens used in the mockups ---------- */
export const HERO_TRIP = () => getTrip(HERO_TRIP_ID);
export const NEEDED_TRIP = () => getTrip(NEEDED_TRIP_ID);
export const RECAP = () => DATA.recap; // week of 26 Oct: 5 drives, average 87, up 2

const progress = (n: number, of = 7) => `<div class="story-progress" aria-hidden="true">${Array.from({ length: of }, (_, i) => (i < n - 1 ? '<i class="on"></i>' : i === n - 1 ? '<i class="cur"><b></b></i>' : '<i></i>')).join('')}</div>`;
const storyTop = (t: Trip) => { const v = vehicle(t.vehicleId!); return `<div class="story-top"><span class="story-car"><i style="background:var(--car-${v.colour}, ${v.colour === 'lucid' ? '#7FA7D9' : '#E5484D'})"></i>${esc(v.nickname)}</span><span class="story-close" aria-hidden="true">✕</span></div>`; };

export function screenStoryRing(t: Trip = HERO_TRIP()): string {
  const f = t.story.frames, sm = t.smoothness;
  return `<div class="app app--story">${progress(4)}${storyTop(t)}
      <div class="story-title">${esc(f.f1.title)}</div>
      <div class="story-stage"><div style="width:78%;aspect-ratio:1;font-size:calc(var(--w) * .2)">${ring({ score: sm.score, baseline: sm.baseline, recordPrev: sm.record?.previous?.score, isNewRecord: sm.record?.isNew })}</div></div>
      <div class="story-copy"><p class="sh">New route record.</p><p>91 on The Commute, beating 89 from 14&nbsp;Oct.</p>
      <span class="story-chip story-chip--record"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="9.5" r="5.5"/><path d="M8.6 13.8L7 21l5-2.6 5 2.6-1.6-7.2"/></svg>Route record</span></div>
    </div>`;
}

export function screenRecap(rc = RECAP()): string {
  const days = rc.days.map((d: any) => `<div>${d.drives.length ? d.drives.map(() => '<span></span>').join('') : '<span class="none"></span>'}${d.dow.slice(0, 1)}</div>`).join('');
  const tr: number[] = rc.trend4w.map((t: any) => t.avg), mn = 76, mx = 90;
  const pts = tr.map((v, i): XY => [8 + i * (184 / (tr.length - 1)), 52 - ((v - mn) / (mx - mn)) * 44]);
  return `<div class="app app--recap">
      <div class="m3-top"><span class="m3-title">Insights</span><span class="m3-avatar">M</span></div>
      <div class="m3-card m3-card--hi"><div class="m3-label">Your week, decoded</div>
        <div style="display:flex;align-items:baseline;gap:.4em;margin-top:.3em"><span class="app-num" style="font-size:2.6em;line-height:1">${rc.avgSmoothness}</span><span style="font-weight:600">Smoothness · ▲ +2</span></div>
        <div style="font-size:.85em;margin-top:.2em;opacity:.85">${rc.drives} drives · ${rc.km.toFixed(0)} km · ${rc.durationText}</div>
      </div>
      <div class="m3-card"><div class="m3-label">This week</div><div class="week-strip">${days}</div></div>
      <div class="m3-card"><div class="m3-label">4-week trend</div>
        <svg viewBox="0 0 200 60" style="width:100%;height:auto;margin-top:.4em" aria-hidden="true"><path d="${smoothPath(pts)}" fill="none" stroke="#5BE7C4" stroke-width="2.5" stroke-linecap="round"/>${pts.map((p, i) => `<circle cx="${p[0]}" cy="${p[1]}" r="${i === pts.length - 1 ? 4.5 : 3}" fill="${i === pts.length - 1 ? '#5BE7C4' : '#0E1217'}" stroke="#5BE7C4" stroke-width="2"/>`).join('')}</svg>
      </div>
      <div class="m3-card"><div class="m3-label">Drive of the week</div><div style="display:flex;justify-content:space-between;align-items:center;margin-top:.3em"><span style="font-weight:600">${esc(rc.driveOfWeek.partOfDay)}<br><span style="color:#A3AAB6;font-weight:400;font-size:.88em">${esc(rc.driveOfWeek.label)}</span></span><span class="app-num" style="font-size:1.6em">${rc.driveOfWeek.score}</span></div></div>
      <nav class="m3-nav" aria-hidden="true">
        <div><i><svg viewBox="0 0 24 24"><path d="M4 17.5c2.5-6 5-6 8-3s5.5 3 8-3"/></svg></i>Drives</div>
        <div><i><svg viewBox="0 0 24 24"><path d="M6 19c0-4 3-5 6-6s6-2 6-6"/><circle cx="6" cy="19" r="2"/><circle cx="18" cy="7" r="2"/></svg></i>Routes</div>
        <div class="on"><i><svg viewBox="0 0 24 24"><path d="M4 20V10M10 20V5M16 20v-7M22 20H2"/></svg></i>Insights</div>
        <div><i><svg viewBox="0 0 24 24"><path d="M3 11l9-6 9 6v9H3z"/></svg></i>Garage</div>
      </nav>
    </div>`;
}

export function screenReplay(t: Trip = NEEDED_TRIP()): string {
  const W = 300, H = 400, r = route(t.rows, W, H, { pad: 40, rotate: 0, trimStart: 650, trimEnd: 520 });
  const ev = t.events!.find((e) => e.needed);
  const pin = ({ e, p }: { e: any; p: XY }) => {
    const col = ({ brake: '#FF7A66', accel: '#E58BD0', corner: '#EAD54E' } as Record<string, string>)[e.type];
    if (e.needed) return `<rect x="${p[0] - 7}" y="${p[1] - 7}" width="14" height="14" rx="4" fill="#05070A" stroke="#C9CED6" stroke-width="2"/>`;
    if (e.type === 'brake') return `<rect x="${p[0] - 7}" y="${p[1] - 7}" width="14" height="14" rx="4" fill="${col}" stroke="#fff" stroke-width="2"/>`;
    if (e.type === 'accel') return `<circle cx="${p[0]}" cy="${p[1]}" r="7.5" fill="${col}" stroke="#fff" stroke-width="2"/>`;
    return `<rect x="${p[0] - 6}" y="${p[1] - 6}" width="12" height="12" transform="rotate(45 ${p[0]} ${p[1]})" fill="${col}" stroke="#fff" stroke-width="2"/>`;
  };
  const P = t.events!.map((e) => ({ e, p: r.at(e.t) }));
  return `<div class="app app--replay">
      <div class="replay-map"><svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
        <path d="${r.d}" fill="none" stroke="#04241D" stroke-width="9" stroke-linecap="round"/>
        <path d="${r.d}" fill="none" stroke="#5BE7C4" stroke-width="4.5" stroke-linecap="round"/>
        ${P.map(pin).join('')}
      </svg><span class="map-attrib">© OpenStreetMap contributors</span></div>
      <div class="replay-sheet">
        <div class="title"><svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true"><rect x="1.5" y="1.5" width="15" height="15" rx="4" fill="#05070A" stroke="#C9CED6" stroke-width="2"/></svg>Hard stop at ${esc(ev.place)}</div>
        <div class="sub">Was it needed? You said yes, so it's left out of your score.</div>
        <div class="ios-btns"><span class="pri">Yes, it was needed</span><span>Not quite</span></div>
      </div>
    </div>`;
}

/* Story frames for the auto-cycling card stack (A2 §3.1, copy from trip.story.frames) */
export interface StoryFrame { n: number; label: string; html: string }
export function storyFrames(t: Trip = HERO_TRIP()): StoryFrame[] {
  const f = t.story.frames, W = 240, H = 300;
  const r = route(t.rows, W, H, { pad: 26, rotate: -18 });
  const clean = r.segment(t.cleanest!.t0, t.cleanest!.t1);
  const routeSvg = (hl: boolean) => `<svg viewBox="0 0 ${W} ${H}" style="width:100%;height:100%" aria-hidden="true">
        <path d="${r.d}" fill="none" stroke="#04241D" stroke-width="10" stroke-linecap="round"/>
        <path class="${hl ? '' : 'sf-draw'}" d="${r.d}" fill="none" stroke="${hl ? '#5BE7C459' : '#5BE7C4'}" stroke-width="4.5" stroke-linecap="round"/>
        ${hl ? `<path d="${clean}" fill="none" stroke="#5BE7C4" stroke-width="12" stroke-linecap="round" opacity=".22"/><path d="${clean}" fill="none" stroke="#C6F8EA" stroke-width="4.5" stroke-linecap="round"/>` : ''}
      </svg>`;
  const tipTrace = (() => { // a calm sparkline from the hero trip's longitudinal G near the end of the drive
    const rows = t.rows.filter((x) => x[0] > t.durationS! - 260 && x[3] != null);
    const pts = rows.map((x, i): XY => [6 + i * (228 / Math.max(1, rows.length - 1)), 30 - x[3]! * 60]);
    const m = pts[pts.length - 3];
    return `<svg viewBox="0 0 240 60" style="width:100%;height:auto" aria-hidden="true"><path d="M0 30H240" stroke="#FFFFFF1F"/><path d="${smoothPath(pts)}" fill="none" stroke="#5AB8F5" stroke-width="2" stroke-linecap="round"/><circle cx="${m?.[0] || 200}" cy="${m?.[1] || 30}" r="5" fill="none" stroke="#fff" stroke-width="1.5"/></svg>`;
  })();
  const tip = String(f.f6.line).replace(/^One thing for next time: /, '').replace(/^./, (c) => c.toUpperCase());
  return [
    { n: 1, label: 'The drive', html: `<div class="app app--story">${progress(1)}${storyTop(t)}<div class="story-stage" style="place-items:center"><svg viewBox="0 0 200 90" style="width:86%;opacity:.9" aria-hidden="true"><path d="M14 66h172M24 64c0-8 2-14 8-18l22-16c6-4 12-6 20-6h46c8 0 14 3 20 8l18 14c10 1 20 4 24 10 2 3 2 6 2 8" fill="none" stroke="#E5484D" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/><path d="M60 44l14-11c4-3 8-4 12-4h18v15zM112 29h12c6 0 10 2 14 6l9 9h-35z" fill="#E5484D22" stroke="#E5484D99" stroke-width="1.4" stroke-linejoin="round"/><circle cx="58" cy="66" r="12" fill="#05070A" stroke="#F2F4F7" stroke-width="2.4"/><circle cx="152" cy="66" r="12" fill="#05070A" stroke="#F2F4F7" stroke-width="2.4"/><circle cx="58" cy="66" r="4" fill="#F2F4F7"/><circle cx="152" cy="66" r="4" fill="#F2F4F7"/></svg></div><div class="story-copy"><p style="color:#A3AAB6;font-weight:600;margin-bottom:.5em">Drive decoded</p><p class="sh" style="font-size:2.1em">Thursday<br>morning</p><p style="font-size:1.1em">Home → Work</p></div></div>` },
    { n: 2, label: 'The route', html: `<div class="app app--story">${progress(2)}${storyTop(t)}<div class="story-stage">${routeSvg(false)}</div><div class="story-copy"><p class="sh num" style="font-family:inherit">${esc(f.f2.line)}</p><p>Ends trimmed. The route draws on, start to finish.</p></div></div>` },
    { n: 3, label: 'Your inputs', html: `<div class="app app--story">${progress(3)}${storyTop(t)}<div class="story-stage" data-gg>${gg(t, { size: 200 })}</div><div class="story-copy"><p class="sh">${esc(f.f3.line)}</p><p>${esc(f.f3.sub)}</p></div></div>` },
    { n: 4, label: 'Smoothness', html: `<div class="app app--story">${progress(4)}${storyTop(t)}<div class="story-stage"><div style="width:74%;aspect-ratio:1;font-size:calc(var(--w) * .19)" data-ring-host>${ring({ score: 91, baseline: 84, recordPrev: 89, isNewRecord: true })}</div></div><div class="story-copy"><p class="sh">Smoothness 91</p><span class="story-chip story-chip--better">▲ ${esc(f.f4.compare)}</span></div></div>` },
    { n: 5, label: 'Cleanest stretch', html: `<div class="app app--story">${progress(5)}${storyTop(t)}<div class="story-stage">${routeSvg(true)}</div><div class="story-copy"><p class="sh">Cleanest stretch</p><p>${esc(t.cleanest!.text)}</p></div></div>` },
    { n: 6, label: 'One tip', html: `<div class="app app--story">${progress(6)}${storyTop(t)}<div class="story-stage" style="padding:0 4%">${tipTrace}</div><div class="story-copy"><p class="sh">One thing for next time</p><p>${esc(tip)}</p></div></div>` },
  ];
}
