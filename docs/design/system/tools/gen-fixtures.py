#!/usr/bin/env python3
"""Builds prototypes/shared/data.js (window.DP_DATA) from cached real road geometry.
Kinematic simulation: road-class target speeds, curvature limits, lights/stops, traffic waves and
injected abrupt-input zones -> 10 Hz speed / longitudinal G / lateral G -> event detection with
evidence windows -> 1 Hz track simplified to <= 400 rows. Deterministic (seeded).
usage: python3 docs/design/system/tools/gen-fixtures.py   (needs tools/cache from fetch-routes.py)"""
import json, math, os, random, statistics
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, 'cache')
OUT = os.path.join(HERE, '..', '..', '..', '..', 'prototypes', 'shared', 'data.js')
TZ = ZoneInfo('America/Toronto')
G = 9.80665
TODAY = datetime(2026, 11, 6, 19, 30, tzinfo=TZ)
THRESH = {'brake': -0.30, 'accel': 0.28, 'corner': 0.36}

# ------------------------------------------------------------------ helpers
def gauss_smooth(a, sigma):
    if sigma <= 0: return a[:]
    r = int(sigma * 3) + 1
    w = [math.exp(-(i * i) / (2 * sigma * sigma)) for i in range(-r, r + 1)]
    out, n = [], len(a)
    for i in range(n):
        s = ws = 0.0
        for k, wk in enumerate(w):
            j = i + k - r
            if 0 <= j < n:
                s += a[j] * wk; ws += wk
        out.append(s / ws)
    return out

def short_road(n):
    if not n: return ''
    rep = [('Queen Elizabeth Way', 'QEW'), ('Gardiner Expressway', 'Gardiner'), ('Highway ', 'Hwy '), (' Avenue', ' Ave'),
           (' Street', ' St'), (' Road', ' Rd'), (' Drive', ' Dr'), (' Boulevard', ' Blvd'), (' West', ' W'), (' East', ' E'),
           (' North', ' N'), (' South', ' S'), ('Forks of the Credit Rd', 'Forks of the Credit')]
    for a, b in rep: n = n.replace(a, b)
    return n

def cross_name(n):  # "Spadina Ave" -> "Spadina"
    n = short_road(n)
    changed = True
    while changed:
        changed = False
        for suf in [' Ave', ' St', ' Rd', ' Dr', ' Blvd', ' W', ' E', ' N', ' S']:
            if n.endswith(suf):
                n = n[: -len(suf)]; changed = True
    return n.strip()

CROSS = {  # known cross streets by latitude (south -> north) for mid-block lights
    'Spadina Avenue': [(43.6398, 'Lake Shore'), (43.6416, 'Bremner'), (43.6436, 'Front'), (43.6452, 'King')],
}
def cross_at(road, lat):
    c = CROSS.get(road)
    if not c: return None
    return min(c, key=lambda q: abs(q[0] - lat))[1]

HIGHWAY = ('Queen Elizabeth Way', 'Gardiner Expressway', 'Highway 403', 'Highway 407', 'Highway 401')
def road_class(name, mtype):
    if any(h in name for h in HIGHWAY): return 'highway'
    if not name and mtype in ('on ramp', 'off ramp', 'fork', 'merge'): return 'ramp'
    if name in ('Forks of the Credit Road', 'Olde Baseline Road', 'Rockside Road', 'Ballinafad Road', 'McLaughlin Road',
                'Old Main Street', 'Mississauga Road', 'Heritage Road', 'Embleton Road', 'Tenth Line', 'Ninth Line',
                'Mayfield Road', 'Winston Churchill Boulevard', 'East & West Line', 'Niagara Stone Road', '10 Side Road'):
        return 'rural'
    return 'city'

TARGET = {  # cruise targets km/h (flow speed, not limits)
    'Queen Elizabeth Way': 103, 'Gardiner Expressway': 88, 'Highway 403': 106, 'Highway 407': 108, 'Highway 401': 104,
    'Forks of the Credit Road': 52, 'Olde Baseline Road': 66, 'Rockside Road': 62, 'Ballinafad Road': 72, 'McLaughlin Road': 74,
    'Winston Churchill Boulevard': 76, 'Mississauga Road': 66, 'Spadina Avenue': 38, 'Lakeshore Road West': 50,
    'Lakeshore Road East': 50, 'River Side Drive': 34, 'Old Main Street': 38, 'Mayfield Road': 74, 'Heritage Road': 70,
    'Embleton Road': 70, 'Tenth Line': 70, 'Ninth Line': 66, 'Niagara Stone Road': 72, 'East & West Line': 70,
}

def proj(lng, lat, lat0, lng0):
    return ((lng - lng0) * math.cos(math.radians(lat0)) * 111320.0, (lat - lat0) * 110540.0)

# ------------------------------------------------------------------ route loading
def load_route(key):
    d = json.load(open(os.path.join(CACHE, f'osrm-{key}.json')))['routes'][0]
    pts, names, mans = [], [], []
    stop_all = False
    for leg in d['legs']:
        steps = leg['steps']
        for si, st in enumerate(steps):
            m = st['maneuver']
            if m.get('modifier') == 'uturn' or stop_all:
                stop_all = True; break
            prev_name = mans[-1]['name'] if mans else ''
            mans.append({'idx': max(0, len(pts) - 1), 'type': m['type'], 'mod': m.get('modifier'), 'name': st['name'], 'from': prev_name})
            for j, c in enumerate(st['geometry']['coordinates']):
                if pts and c == pts[-1]: continue
                pts.append(c); names.append(st['name'] or '')
    ev = json.load(open(os.path.join(CACHE, f'elev-{key}.json')))
    return pts, names, mans, ev

def resample(key, ds=4.0):
    pts, names, mans, ev = load_route(key)
    lat0 = sum(p[1] for p in pts) / len(pts); lng0 = sum(p[0] for p in pts) / len(pts)
    xy = [proj(p[0], p[1], lat0, lng0) for p in pts]
    cum = [0.0]
    for i in range(1, len(xy)):
        cum.append(cum[-1] + math.dist(xy[i - 1], xy[i]))
    L = cum[-1]
    n = int(L // ds) + 1
    R = {'s': [], 'x': [], 'y': [], 'lat': [], 'lng': [], 'road': [], 'cls': []}
    j = 0
    man_at = {m['idx']: m for m in mans}
    cls_of_pt = []
    cur_type = 'depart'
    for i in range(len(pts)):
        if i in man_at: cur_type = man_at[i]['type']
        cls_of_pt.append(road_class(names[i], cur_type))
    for k in range(n):
        s = k * ds
        while j < len(cum) - 2 and cum[j + 1] < s: j += 1
        seg = cum[j + 1] - cum[j] or 1
        f = min(1, max(0, (s - cum[j]) / seg))
        R['s'].append(s)
        R['x'].append(xy[j][0] + f * (xy[j + 1][0] - xy[j][0]))
        R['y'].append(xy[j][1] + f * (xy[j + 1][1] - xy[j][1]))
        R['lat'].append(pts[j][1] + f * (pts[j + 1][1] - pts[j][1]))
        R['lng'].append(pts[j][0] + f * (pts[j + 1][0] - pts[j][0]))
        R['road'].append(names[j + 1] if not names[j] else names[j])
        R['cls'].append(cls_of_pt[j + 1])
    # curvature (signed, + = left)
    hd = [math.atan2(R['y'][i + 1] - R['y'][i], R['x'][i + 1] - R['x'][i]) for i in range(n - 1)] + [0]
    hd[-1] = hd[-2]
    for i in range(1, n):
        while hd[i] - hd[i - 1] > math.pi: hd[i] -= 2 * math.pi
        while hd[i] - hd[i - 1] < -math.pi: hd[i] += 2 * math.pi
    hs = gauss_smooth(hd, 3.0)
    kap = [0.0] + [(hs[i + 1] - hs[i - 1]) / (2 * ds) for i in range(1, n - 1)] + [0.0]
    kap = [max(-1 / 9, min(1 / 9, k)) for k in kap]
    R['kappa'] = kap
    # maneuver positions in resampled s
    for m in mans:
        m['s'] = cum[min(m['idx'], len(cum) - 1)]
    R['mans'] = mans
    # elevation by distance
    ecum = [0.0]
    ep = ev['points']
    for i in range(1, len(ep)):
        a, b = proj(ep[i - 1][0], ep[i - 1][1], lat0, lng0), proj(ep[i][0], ep[i][1], lat0, lng0)
        ecum.append(ecum[-1] + math.dist(a, b))
    sc = L / (ecum[-1] or 1)
    ecum = [e * sc for e in ecum]
    el = gauss_smooth(ev['elevation'], 1.0)
    elev, j = [], 0
    for s in R['s']:
        while j < len(ecum) - 2 and ecum[j + 1] < s: j += 1
        seg = ecum[j + 1] - ecum[j] or 1
        f = min(1, max(0, (s - ecum[j]) / seg))
        elev.append(el[j] + f * (el[j + 1] - el[j]))
    R['elev'] = elev
    R['L'] = L
    R['ds'] = ds
    return R

# ------------------------------------------------------------------ simulation
def simulate(R, rng, style):
    n, ds = len(R['s']), R['ds']
    s = R['s']
    # target speed by road
    vt = []
    for i in range(n):
        road, cls = R['road'][i], R['cls'][i]
        if road in TARGET: k = TARGET[road]
        elif cls == 'highway': k = 100
        elif cls == 'ramp': k = 70
        elif cls == 'rural': k = 68
        else: k = 46
        vt.append(k / 3.6 * style['pace'])
    # per-trip slow zones (traffic)
    for (a, b, f) in style.get('traffic', []):
        for i in range(n):
            if a <= s[i] / R['L'] <= b:
                vt[i] *= f
    vt_pre = vt[:]
    inj_acc = [False] * n
    # traffic waves
    waves = []
    for (a, b, k_waves, vmin) in style.get('waves', []):
        for w in range(k_waves):
            pos = (a + (b - a) * (w + 0.5) / k_waves + rng.uniform(-0.01, 0.01)) * R['L']
            waves.append((pos, vmin * rng.uniform(0.8, 1.3) / 3.6))
    for pos, vmin in waves:
        for i in range(n):
            dd = abs(s[i] - pos)
            if dd < 300:
                vt[i] = min(vt[i], vmin + (vt[i] - vmin) * (dd / 300) ** 1.5)
    # lateral limit
    alat = [(0.12 if R['cls'][i] == 'highway' else 0.26 if R['cls'][i] == 'rural' else 0.24) * style['lat'] for i in range(n)]
    dlim = [style['dec'] * G] * n
    alim = [style['acc'] * G] * n
    # stops: lights at turns on city roads, stop signs on rural T-junctions
    stops = []
    for m in R['mans'][1:]:
        cls = road_class(m['name'], m['type'])
        prev_cls = road_class(m['from'], 'turn')
        if m['type'] in ('turn', 'end of road') and prev_cls in ('city', 'rural') and cls in ('city', 'rural'):
            if rng.random() < (0.92 if prev_cls == 'rural' else 0.6):
                stops.append({'s': m['s'], 'dwell': rng.uniform(3, 7) if prev_cls == 'rural' else rng.uniform(8, 42), 'at': (m['from'], m['name'])})
        elif m['type'] == 'new name' and prev_cls == 'city' and rng.random() < 0.35:
            stops.append({'s': m['s'], 'dwell': rng.uniform(10, 35), 'at': (m['from'], m['name'])})
    # mid-block lights on long city roads
    for (road, every) in style.get('lights', []):
        idx = [i for i in range(n) if R['road'][i] == road]
        if not idx: continue
        a, b = s[idx[0]], s[idx[-1]]
        p = a + every * rng.uniform(0.6, 1.0)
        while p < b - 120:
            if rng.random() < 0.5:
                stops.append({'s': p, 'dwell': rng.uniform(10, 38), 'at': (road, None)})
            p += every * rng.uniform(0.8, 1.2)
    stops = [st for st in stops if 60 < st['s'] < R['L'] - 40]
    stops.sort(key=lambda z: z['s'])
    # merge stops within 80 m
    merged = []
    for st in stops:
        if merged and st['s'] - merged[-1]['s'] < 80: continue
        merged.append(st)
    stops = merged
    stop_idx = {min(n - 1, int(round(st['s'] / ds))): st for st in stops}

    # inject abrupt zones
    inj = []
    for ev in style.get('inject', []):
        typ = ev['type']
        if typ == 'brake':
            cands = [st for st in stops] if ev.get('at') != 'wave' else [{'s': p, 'wave': True} for p, _ in waves]
            if ev.get('at') == 'end': cands = [{'s': R['L'] - 2}]
            if ev.get('road'):
                cands = [c for c in cands if R['road'][min(n - 1, int(c['s'] / ds))] == ev['road']] or cands
            if not cands: continue
            c = cands[ev.get('pick', rng.randrange(len(cands))) % len(cands)]
            e_s = c['s']
            if c.get('wave'):
                e_s = c['s'] - 70
                vmin_w = min(vt[min(n - 1, int(c['s'] / ds))], 30 / 3.6)
                for i in range(n):
                    if e_s <= s[i] <= c['s']: vt[i] = min(vt[i], vmin_w)
                    elif c['s'] - 340 <= s[i] < e_s: vt[i] = max(vt[i], vt_pre[i] * 0.95)
            for i in range(n):
                if e_s - 160 <= s[i] <= e_s: dlim[i] = ev.get('g', 0.46) * G
            inj.append(('brake', e_s))
        elif typ == 'accel':
            if ev.get('at') == 'ramp':
                ramp = [i for i in range(n) if R['cls'][i] == 'ramp']
                if not ramp: continue
                a0 = s[ramp[0]]
            else:
                cands = stops
                if ev.get('road'):
                    cands = [c for c in stops if R['road'][min(n - 1, int(c['s'] / ds) + 3)] == ev['road']] or stops
                if not cands: continue
                a0 = cands[ev.get('pick', rng.randrange(len(cands))) % len(cands)]['s']
            for i in range(n):
                if a0 <= s[i] <= a0 + 140: alim[i] = ev.get('g', 0.38) * G; inj_acc[i] = True
            inj.append(('accel', a0))
        elif typ == 'corner':
            lo, hi = ev.get('range', (0.0, 1.0))
            best = None
            for i in range(n):
                if lo <= s[i] / R['L'] <= hi and (not ev.get('road') or R['road'][i] == ev['road']):
                    if abs(R['kappa'][i]) > 1 / 80 and (best is None or abs(R['kappa'][i]) > abs(R['kappa'][best])):
                        if all(abs(s[i] - q) > 200 for t, q in inj if t == 'corner'):
                            best = i
            if best is None: continue
            for i in range(n):
                if abs(s[i] - s[best]) < 45:
                    alat[i] = ev.get('g', 0.46)
                    vt[i] = max(vt[i], math.sqrt(ev.get('g', 0.46) * G / max(abs(R['kappa'][best]), 1e-4)))
            inj.append(('corner', s[best]))

    v = [min(vt[i], math.sqrt(alat[i] * G / max(abs(R['kappa'][i]), 1e-5))) for i in range(n)]
    v[0] = 0; v[-1] = 0
    for i in stop_idx: v[i] = 0
    for i in range(n - 2, -1, -1):
        v[i] = min(v[i], math.sqrt(v[i + 1] ** 2 + 2 * dlim[i] * ds))
    for i in range(1, n):
        a = alim[i] * (1.08 if inj_acc[i] else max(0.4, 1 - v[i - 1] / 45))
        v[i] = min(v[i], math.sqrt(v[i - 1] ** 2 + 2 * a * ds))
    # time
    tn, sn, vn = [0.0], [0.0], [0.0]
    t = 0.0
    for i in range(1, n):
        t += 2 * ds / max(v[i - 1] + v[i], 0.6)
        tn.append(t); sn.append(s[i]); vn.append(v[i])
        if i in stop_idx:
            t += stop_idx[i]['dwell']
            tn.append(t); sn.append(s[i]); vn.append(0.0)
    T = tn[-1]
    # 10 Hz
    hz = 10
    N = int(T * hz) + 1
    vs, j = [], 0
    for k in range(N):
        tt = k / hz
        while j < len(tn) - 2 and tn[j + 1] < tt: j += 1
        seg = tn[j + 1] - tn[j] or 1
        f = min(1, max(0, (tt - tn[j]) / seg))
        vs.append(vn[j] + f * (vn[j + 1] - vn[j]))
    vs = gauss_smooth(vs, 6.0 * style.get('smooth', 1.0))
    vs = [max(0.0, x) for x in vs]
    # re-integrate position
    pos = [0.0]
    for k in range(1, N):
        pos.append(pos[-1] + (vs[k] + vs[k - 1]) / 2 / hz)
    scale = R['L'] / pos[-1]
    pos = [p * scale for p in pos]
    vs = [x * scale for x in vs]
    lon = [0.0] + [(vs[k + 1] - vs[k - 1]) * hz / 2 / G for k in range(1, N - 1)] + [0.0]
    lon = gauss_smooth(lon, 2.5)
    # noise (road texture + engine), AR(1)
    nz, prev = [], 0.0
    for k in range(N):
        prev = 0.85 * prev + rng.gauss(0, 0.008 * style.get('noise', 1.0))
        nz.append(prev)
    lon = [lon[k] + nz[k] * (0.4 + min(1, vs[k] / 20)) for k in range(N)]
    idx_of = lambda p: min(n - 1, int(p / ds))
    lat = []
    prev = 0.0
    for k in range(N):
        i = idx_of(pos[k])
        prev = 0.8 * prev + rng.gauss(0, 0.007 * style.get('noise', 1.0))
        lat.append(-(vs[k] ** 2) * R['kappa'][i] / G + prev * min(1, vs[k] / 8))
    lat = gauss_smooth(lat, 3.0)
    return {'hz': hz, 'N': N, 'T': T, 'v': vs, 'pos': pos, 'lon': lon, 'lat': lat, 'stops': stops, 'inj': inj, 'idx_of': idx_of}

def detect(sim):
    out = []
    hz = sim['hz']
    def runs(series, cond, typ, mind):
        k, N = 0, len(series)
        while k < N:
            if cond(series[k]):
                a = k
                while k < N and cond(series[k]): k += 1
                b = k - 1
                if (b - a + 1) / hz >= mind:
                    seg = series[a:b + 1]
                    pk = a + (seg.index(min(seg)) if typ == 'brake' else seg.index(max(seg)) if typ == 'accel' else max(range(len(seg)), key=lambda q: abs(seg[q])))
                    out.append({'type': typ, 'a': a, 'b': b, 'pk': pk, 'peak': series[pk]})
            k += 1
    runs(sim['lon'], lambda g: g <= THRESH['brake'], 'brake', 0.4)
    runs(sim['lon'], lambda g: g >= THRESH['accel'], 'accel', 0.5)
    runs(sim['lat'], lambda g: abs(g) >= THRESH['corner'], 'corner', 0.6)
    out.sort(key=lambda e: e['pk'])
    merged = []
    for e in out:
        if merged and merged[-1]['type'] == e['type'] and e['a'] - merged[-1]['b'] < 2 * hz:
            if abs(e['peak']) > abs(merged[-1]['peak']): merged[-1].update(pk=e['pk'], peak=e['peak'])
            merged[-1]['b'] = e['b']
            continue
        merged.append(e)
    return merged

# ------------------------------------------------------------------ copy
TIPS = {
    'brake_light': {'cat': 'braking', 'text': 'Next time, ease off a little earlier before the {place} light. That stop cost the most smoothness.'},
    'brake_traffic': {'cat': 'anticipation', 'text': 'Next time, leave a longer gap on the {road} so you can lift off instead of braking when traffic bunches up.'},
    'brake_release': {'cat': 'braking', 'text': 'Next time, release the brake a touch in the last metre of your stop at {place}. It takes the nod out of the finish.'},
    'brake_bend': {'cat': 'cornering', 'text': 'Next time, do your braking in a straight line before the bends on {road}, then roll through on a steady throttle.'},
    'accel_merge': {'cat': 'acceleration', 'text': 'Next time, build speed more progressively up the {place} on-ramp. A longer, steadier squeeze merges just as well.'},
    'accel_launch': {'cat': 'acceleration', 'text': 'Next time, roll onto the throttle away from the {place} light instead of stepping in. Your coffee will thank you.'},
    'accel_ev': {'cat': 'acceleration', 'text': 'Next time, feed in the power gently away from {place}. Smoother starts like these usually help range.'},
    'corner_entry': {'cat': 'cornering', 'text': 'Next time, settle your speed before turning in at {place}, then hold one steady arc through.'},
    'corner_bend': {'cat': 'cornering', 'text': 'Next time, carry a little less speed into the tightest bend on {road} and hold a constant line through it.'},
    'consistency_hwy': {'cat': 'consistency', 'text': 'Next time, try holding a steadier speed on the {road}. Small, early adjustments beat big corrections.'},
    'anticipation_lights': {'cat': 'anticipation', 'text': 'Next time, look two lights ahead on {road}. Coasting up to a red is smoother than braking at it.'},
    'stop_go': {'cat': 'consistency', 'text': 'Next time, in stop-and-go on the {road}, aim for one gentle crawl speed instead of chasing the gap ahead.'},
    'regen': {'cat': 'braking', 'text': 'Next time, let regen do the first part of the stop before {place}. Smoother stops like these usually help range.'},
}

def part_of_day(dt):
    h = dt.hour
    p = 'morning' if 5 <= h < 12 else 'midday' if h < 14 else 'afternoon' if h < 17 else 'evening' if h < 21 else 'night'
    return f"{dt.strftime('%A')} {p}"

def rdp(points, eps):
    if len(points) < 3: return points
    a, b = points[0], points[-1]
    dx, dy = b[0] - a[0], b[1] - a[1]
    L = math.hypot(dx, dy) or 1e-9
    dmax, idx = 0, 0
    for i in range(1, len(points) - 1):
        d = abs(dy * points[i][0] - dx * points[i][1] + b[0] * a[1] - b[1] * a[0]) / L
        if d > dmax: dmax, idx = d, i
    if dmax > eps:
        return rdp(points[:idx + 1], eps)[:-1] + rdp(points[idx:], eps)
    return [a, b]

def glyph(R, rng, trim=True):
    xs, ys, s = R['x'], R['y'], R['s']
    a = rng.uniform(400, 800) if trim else 0
    b = R['L'] - (rng.uniform(400, 800) if trim else 0)
    pts = [(xs[i], ys[i]) for i in range(len(s)) if a <= s[i] <= b]
    if len(pts) < 3: pts = list(zip(xs, ys))
    minx, maxx = min(p[0] for p in pts), max(p[0] for p in pts)
    miny, maxy = min(p[1] for p in pts), max(p[1] for p in pts)
    span = max(maxx - minx, maxy - miny) or 1
    pts = rdp(pts, span * 0.006)
    ox = (1 - (maxx - minx) / span) / 2; oy = (1 - (maxy - miny) / span) / 2
    return [[round(ox + (p[0] - minx) / span, 3), round(oy + (maxy - p[1]) / span, 3)] for p in pts]

# ------------------------------------------------------------------ schedule
VEH = {
    'gti': {'id': 'gti', 'nickname': 'GTI', 'make': 'Volkswagen', 'model': 'Golf GTI', 'year': 2022, 'powertrain': 'petrol', 'transmission': 'manual',
            'colour': 'carmine', 'colourName': 'Kings Red', 'default': True, 'active': True, 'addedOn': '2026-09-28', 'silhouette': 'hatch'},
    'ioniq': {'id': 'ioniq', 'nickname': 'Ioniq', 'make': 'Hyundai', 'model': 'Ioniq 5', 'year': 2024, 'powertrain': 'ev', 'transmission': 'single-speed',
              'colour': 'lucid', 'colourName': 'Lucid Blue', 'default': False, 'active': True, 'addedOn': '2026-10-04', 'silhouette': 'crossover'},
}
PLACES = {'home': 'Home', 'work': 'Work', 'gym': 'Gym', 'grocery': 'Kerr Village', 'bronte': 'Bronte Harbour', 'portcredit': 'Port Credit',
          'notl': 'Niagara-on-the-Lake', 'belfountain': 'Belfountain', 'burlington': 'Burlington'}
RKEY = {  # route key -> (from, to, context)
    'commute_am': ('home', 'work', 'mixed'), 'commute_pm': ('work', 'home', 'mixed'), 'gym': ('home', 'gym', 'city'),
    'gym_back': ('gym', 'home', 'city'), 'grocery': ('home', 'grocery', 'city'), 'bronte': ('home', 'bronte', 'city'),
    'lakeshore': ('home', 'portcredit', 'city'), 'notl': ('home', 'notl', 'highway'), 'notl_back': ('notl', 'home', 'highway'),
    'twisty': ('home', 'belfountain', 'mixed'), 'twisty_back': ('belfountain', 'home', 'mixed'), 'burlington': ('home', 'burlington', 'mixed'),
}
AM_TRAFFIC = {'traffic': [(0.62, 0.9, 0.78)], 'waves': [(0.66, 0.9, 4, 22)]}
PM_TRAFFIC = {'traffic': [(0.06, 0.4, 0.8)], 'waves': [(0.08, 0.38, 4, 26)]}
LIGHTS_OAK = [('Speers Road', 650), ('Cornwall Road', 600), ('Trafalgar Road', 550), ('Kerr Street', 400)]
LIGHTS_TOR = [('Spadina Avenue', 300)]

# (local datetime, route, car, score target or None, extra)
S = [
    ('2026-09-28 18:42', 'grocery', 'gti', 80, {'firstDrive': True}),
    ('2026-09-29 07:38', 'commute_am', 'gti', 76, {}),
    ('2026-09-29 17:24', 'commute_pm', 'gti', 78, {}),
    ('2026-10-02 07:45', 'commute_am', 'gti', 79, {'routeDiscovered': True}),
    ('2026-10-09 07:41', 'commute_am', 'gti', 81, {}),
    ('2026-10-09 17:31', 'commute_pm', 'gti', 80, {}),
    ('2026-10-10 10:15', 'twisty', 'gti', 78, {'source': 'manual', 'manualVia': 'Control Center'}),
    ('2026-10-10 13:40', 'twisty_back', 'gti', 84, {}),
    ('2026-10-13 07:36', 'commute_am', 'gti', 84, {}),
    ('2026-10-13 17:42', 'commute_pm', 'gti', 82, {'interrupted': True}),
    ('2026-10-14 07:44', 'commute_am', 'gti', 89, {'record': True}),
    ('2026-10-17 11:20', 'grocery', 'ioniq', 85, {}),
    ('2026-10-20 07:40', 'commute_am', 'gti', 83, {'gap': True, 'neededBrake': True}),
    ('2026-10-20 17:35', 'commute_pm', 'gti', 84, {}),
    ('2026-10-22 06:55', 'gym', 'gti', 82, {}),
    ('2026-10-22 08:05', 'gym_back', 'gti', 85, {}),
    ('2026-10-24 09:30', 'notl', 'ioniq', 88, {'imu25': True}),
    ('2026-10-24 16:10', 'notl_back', 'ioniq', 87, {}),
    ('2026-10-27 07:39', 'commute_am', 'gti', 85, {}),
    ('2026-10-27 17:28', 'commute_pm', 'gti', 83, {'excluded': True}),
    ('2026-10-29 07:43', 'commute_am', 'gti', 87, {}),
    ('2026-10-29 17:50', 'commute_pm', 'gti', 88, {'record': True}),
    ('2026-10-31 19:05', 'burlington', 'gti', None, {'passenger': True}),
    ('2026-11-01 10:20', 'lakeshore', 'gti', 90, {'source': 'manual', 'manualVia': 'Widget'}),
    ('2026-11-03 07:37', 'commute_am', 'gti', 86, {}),
    ('2026-11-04 06:50', 'gym', 'gti', 84, {}),
    ('2026-11-04 07:55', 'gym_back', 'gti', 86, {}),
    ('2026-11-05 07:42', 'commute_am', 'gti', 91, {'record': True}),
    ('2026-11-05 17:45', 'commute_pm', 'gti', 85, {}),
    ('2026-11-06 12:10', 'bronte', 'ioniq', None, {'unscorable': True}),
]

ROUTE_OF = {'commute_am': 'r_commute', 'commute_pm': 'r_commute_home', 'gym': 'r_gym', 'gym_back': 'r_cand_gym_home'}

def style_for(route, score, rng, extra):
    q = (score or 84 - 70) if score else 84
    smooth = max(0.0, min(1.0, ((score or 84) - 72) / 20))  # 0 rough .. 1 smooth
    st = {'pace': rng.uniform(0.97, 1.03), 'lat': 1.0 + (1 - smooth) * 0.15, 'dec': 0.15 + (1 - smooth) * 0.06,
          'acc': 0.12 + (1 - smooth) * 0.05, 'noise': 1.0 + (1 - smooth) * 0.5, 'smooth': 0.8 + smooth * 0.5,
          'lights': LIGHTS_OAK + (LIGHTS_TOR if 'commute' in route else [])}
    if route == 'commute_am': st.update(AM_TRAFFIC)
    if route == 'commute_pm': st.update(PM_TRAFFIC)
    if extra.get('record') and route == 'commute_am': st['waves'] = []; st['traffic'] = [(0.62, 0.9, 0.92)]
    n_ev = 0 if score is None else max(0, round((93 - score) / 3.2))
    inj = []
    if route in ('commute_am', 'commute_pm'):
        pool = [{'type': 'brake', 'at': 'wave', 'g': 0.42}, {'type': 'brake', 'road': 'Spadina Avenue', 'g': 0.47},
                {'type': 'accel', 'at': 'ramp', 'g': 0.36}, {'type': 'brake', 'road': 'Speers Road', 'g': 0.44},
                {'type': 'brake', 'at': 'wave', 'g': 0.4, 'pick': 2}, {'type': 'accel', 'road': 'Trafalgar Road', 'g': 0.37},
                {'type': 'corner', 'range': (0.88, 1.0) if route == 'commute_am' else (0.0, 0.1), 'g': 0.42}]
        if route == 'commute_pm': pool[1] = {'type': 'brake', 'road': 'Kerr Street', 'g': 0.46}
    elif route in ('twisty', 'twisty_back'):
        pool = [{'type': 'corner', 'road': 'Forks of the Credit Road', 'g': 0.5}, {'type': 'corner', 'road': 'Forks of the Credit Road', 'g': 0.46},
                {'type': 'brake', 'road': 'Olde Baseline Road', 'g': 0.45}, {'type': 'corner', 'road': 'Rockside Road', 'g': 0.44},
                {'type': 'accel', 'road': 'McLaughlin Road', 'g': 0.38}, {'type': 'corner', 'range': (0.3, 0.6), 'g': 0.45}]
        if route == 'twisty_back': pool = [{'type': 'corner', 'road': 'Mississauga Road', 'g': 0.43}, {'type': 'brake', 'g': 0.45}, {'type': 'accel', 'at': 'ramp', 'g': 0.36}]
    elif route in ('notl', 'notl_back'):
        pool = [{'type': 'accel', 'at': 'ramp', 'g': 0.35}, {'type': 'brake', 'g': 0.42}, {'type': 'brake', 'g': 0.4}]
        st['waves'] = [(0.2, 0.35, 2, 45)]
    else:
        pool = [{'type': 'brake', 'g': 0.45}, {'type': 'accel', 'g': 0.37}, {'type': 'corner', 'g': 0.41}, {'type': 'brake', 'g': 0.43}]
    st['inject'] = pool[:n_ev]
    if extra.get('neededBrake'):
        st['inject'] = [{'type': 'brake', 'at': 'end', 'g': 0.52}] + st['inject']
    return st

# ------------------------------------------------------------------ build trips
routes_cache = {}
trips = []
rng_master = random.Random(20261106)
for n_i, (when, rkey, car, score, extra) in enumerate(S):
    rng = random.Random(1000 + n_i)
    if rkey not in routes_cache: routes_cache[rkey] = resample(rkey)
    R = routes_cache[rkey]
    st = style_for(rkey, score, rng, extra)
    sim = simulate(R, rng, st)
    events = detect(sim)
    hz, N = sim['hz'], sim['N']
    start = datetime.strptime(when, '%Y-%m-%d %H:%M').replace(tzinfo=TZ) + timedelta(seconds=rng.randint(0, 50))
    source = extra.get('source', 'auto')
    interrupted = extra.get('interrupted', False)
    # trims
    end_k = N - 1
    if interrupted: end_k = int(N * 0.6)
    # quality spans (seconds)
    est_end = 0
    if source == 'auto':
        target = rng.uniform(260, 460)
        k = 0
        while k < N and sim['pos'][k] < target: k += 1
        est_end = k / hz
    gap = None
    if extra.get('gap'):
        k = next(k for k in range(N) if sim['pos'][k] > R['L'] - 1200)
        gap = (k / hz, k / hz + 41)
    excluded = None
    if extra.get('excluded'):
        excluded = (N / hz * 0.52, N / hz * 0.52 + 184)
    in_span = lambda t, sp: sp and sp[0] <= t <= sp[1]
    # drop events in estimated/excluded spans and beyond end
    ev_ok = []
    for e in events:
        tt = e['pk'] / hz
        if tt <= est_end or in_span(tt, excluded) or e['pk'] > end_k: continue
        ev_ok.append(e)
    unscorable = extra.get('unscorable', False)
    passenger = extra.get('passenger', False)
    if unscorable or passenger: ev_ok = []
    # elevation per 10 Hz
    elev10 = [R['elev'][sim['idx_of'](p)] for p in sim['pos']]
    # rows at 1 Hz -> decimated
    T = end_k / hz
    step = max(1, math.ceil(T / 380))
    key_t = set()
    for e in ev_ok: key_t.add(round(e['pk'] / hz))
    for spx in [gap, excluded]:
        if spx: key_t.update([math.floor(spx[0]), math.ceil(spx[1])])
    if est_end: key_t.add(math.ceil(est_end))
    ts = sorted(set(list(range(0, int(T) + 1, step)) + [int(T)] + [t for t in key_t if t <= T]))
    rows = []
    max_v, max_t = 0, 0
    jit = random.Random(n_i)
    for t in ts:
        k = min(end_k, int(t * hz))
        i = sim['idx_of'](sim['pos'][k])
        lat_, lng_ = R['lat'][i], R['lng'][i]
        q = 0
        kmh = sim['v'][k] * 3.6 + jit.gauss(0, 0.3)
        if t <= est_end: q = 1
        if in_span(t, gap): q = 2
        if in_span(t, excluded): q = 3
        lat_ += jit.gauss(0, 1.2) / 110540; lng_ += jit.gauss(0, 1.2) / 80600
        row = [t, round(lat_, 5), round(lng_, 5), round(max(0, kmh), 1), round(sim['lon'][k], 2), round(sim['lat'][k], 2), round(elev10[k]), q]
        if q == 2: row[1] = row[2] = row[3] = None
        if q == 0 and kmh > max_v: max_v, max_t = kmh, t
        rows.append(row)
    # 1 Hz derived stats from 10 Hz
    v1 = [sim['v'][min(end_k, int(t * hz))] for t in range(int(T) + 1)]
    moving = sum(1 for x in v1 if x > 1.0)
    stopped = len(v1) - moving
    dist_km = sim['pos'][end_k] / 1000
    # stops >= 10 s
    stops = []
    k = 0
    while k < len(v1):
        if v1[k] < 0.5 and 5 < k < len(v1) - 5:
            a = k
            while k < len(v1) and v1[k] < 0.5: k += 1
            if k - a >= 10:
                i = sim['idx_of'](sim['pos'][min(end_k, a * hz)])
                stops.append({'t': a, 'durationS': k - a, 'lat': round(R['lat'][i], 5), 'lng': round(R['lng'][i], 5), 'road': short_road(R['road'][i])})
        k += 1
    eg = el = 0.0
    e_s = gauss_smooth([elev10[min(end_k, t * hz)] for t in range(0, int(T) + 1, 5)], 1.5)
    for a, b in zip(e_s, e_s[1:]):
        if b - a > 0.3: eg += b - a
        elif a - b > 0.3: el += a - b
    # events -> objects
    ev_objs = []
    def name_place(typ, k):
        i = sim['idx_of'](sim['pos'][k])
        road = R['road'][i]
        # place: nearest following maneuver within 250 m for brakes, preceding for accel
        s_here = sim['pos'][k]
        nxt_named = [m for m in R['mans'] if m['s'] > s_here and m['name']]
        to_place = PLACES[RKEY[rkey][1]]
        place = short_road(road) if road else (f"the turn onto {short_road(nxt_named[0]['name'])}" if nxt_named else ('the gym car park' if to_place == 'Gym' else f'the way in at {to_place}'))
        cx = cross_at(road, R['lat'][i])
        if cx: place = f"{cross_name(road)} & {cx}"
        if typ == 'brake':
            nxt = [m for m in R['mans'] if 0 <= m['s'] - s_here < 600 and m['name'] and m['name'] != road]
            if nxt and road and road_class(road, '') != 'highway' and not cx:
                a_, b_ = cross_name(road), cross_name(nxt[0]['name'])
                place = short_road(nxt[0]['name']) if (a_ in b_ or b_ in a_) else f"{a_} & {b_}"
            elif road_class(road, '') == 'highway': place = f"the {short_road(road)}"
        elif typ == 'accel':
            prv = [m for m in R['mans'] if 0 <= s_here - m['s'] < 300]
            if R['cls'][i] == 'ramp' or (prv and prv[-1]['type'] == 'on ramp'):
                nxt = [m for m in R['mans'] if m['s'] > s_here and m['name']]
                place = f"{short_road(nxt[0]['name'])} on-ramp" if nxt else 'the on-ramp'
            elif prv and prv[-1]['from'] and prv[-1]['name'] and prv[-1]['from'] != prv[-1]['name']:
                place = f"{cross_name(prv[-1]['from'])} & {cross_name(prv[-1]['name'])}"
        elif typ == 'corner':
            if R['cls'][i] == 'ramp' and rkey == 'commute_am': place = 'the Spadina off-ramp'
            elif R['cls'][i] == 'ramp' and nxt_named: place = f"the {short_road(nxt_named[0]['name'])} ramp"
        return place, road, i
    for j, e in enumerate(ev_ok):
        k = e['pk']
        place, road, i = name_place(e['type'], k)
        lo, hi = max(0, k - 10 * hz), min(end_k, k + 10 * hz)
        ev_hz = 5
        ks = list(range(k - 10 * hz, k + 10 * hz + 1, hz // ev_hz))
        clampk = lambda q: max(0, min(end_k, q))
        conf_margin = abs(e['peak']) - abs(THRESH[e['type']])
        conf = 'High' if conf_margin >= 0.07 else 'Medium' if conf_margin >= 0.03 else 'Low'
        if extra.get('imu25') and conf == 'High': conf = 'Medium'
        ev_objs.append({
            'id': f"e{j + 1}", 'type': e['type'], 't': round(k / hz, 1), 'tStart': round(e['a'] / hz, 1), 'tEnd': round(e['b'] / hz, 1),
            'durationS': round((e['b'] - e['a'] + 1) / hz, 1), 'peakG': round(abs(e['peak']), 2),
            'direction': ('left' if e['peak'] < 0 else 'right') if e['type'] == 'corner' else None,
            'lat': round(R['lat'][i], 5), 'lng': round(R['lng'][i], 5), 'road': short_road(road), 'place': place,
            'speedBeforeKmh': round(sim['v'][clampk(e['a'] - 20)] * 3.6), 'speedAfterKmh': round(sim['v'][clampk(e['b'] + 20)] * 3.6),
            'confidence': conf, 'needed': False, 'provenance': 'derived',
            'evidence': {'hz': ev_hz, 't0': -10, 'kmh': [round(sim['v'][clampk(q)] * 3.6, 1) for q in ks],
                         'lonG': [round(sim['lon'][clampk(q)], 2) for q in ks], 'latG': [round(sim['lat'][clampk(q)], 2) for q in ks],
                         'markers': {'start': round((e['a'] - k) / hz, 1), 'peak': 0, 'end': round((e['b'] - k) / hz, 1)}},
        })
    soft = None
    if not ev_ok and not unscorable and not passenger:
        cand = [k for k in range(int(est_end * hz) + 1, end_k) if sim['lon'][k] < -0.12]
        city = [k for k in cand if R['cls'][sim['idx_of'](sim['pos'][k])] == 'city']
        pool_k = city or cand
        if pool_k:
            kk = min(pool_k, key=lambda q: sim['lon'][q])
            pl, rd, _ = name_place('brake', kk)
            soft = (pl, rd)
    if extra.get('neededBrake'):
        sp = [e for e in ev_objs if e['type'] == 'brake' and 'Spadina' in e['road']]
        sp = sp or [e for e in ev_objs if e['type'] == 'brake' and e['t'] > T - 60]
        if sp:
            sp[0]['needed'] = True; sp[0]['neededNote'] = 'Marked as needed on 20 Oct'
    # cleanest stretch: longest window without abrupt input (|lon|<0.16, |lat|<0.2) and not in excluded
    best = (0, 0, 0)
    a = None
    for k in range(0, end_k, 2):
        ok = abs(sim['lon'][k]) < 0.16 and abs(sim['lat'][k]) < 0.2 and not in_span(k / hz, excluded) and k / hz > est_end and not in_span(k / hz, gap)
        if ok and a is None: a = k
        if (not ok or k >= end_k - 2) and a is not None:
            d = sim['pos'][k] - sim['pos'][a]
            if d > best[0]: best = (d, a, k)
            a = None
    roads = {}
    for k in range(best[1], best[2], 10):
        r = R['road'][sim['idx_of'](sim['pos'][k])]
        roads[r] = roads.get(r, 0) + 1
    top_road = max(roads, key=roads.get) if roads else ''
    cleanest = {'km': round(best[0] / 1000, 1), 'road': short_road(top_road), 't0': round(best[1] / hz), 't1': round(best[2] / hz),
                'text': f"{best[0] / 1000:.1f} km on {('the ' + short_road(top_road)) if road_class(top_road, '') == 'highway' else short_road(top_road)} with no abrupt inputs."}
    # consistency (speed CV on cruise)
    cruise = [x for x in v1 if x > 18]
    cv = statistics.pstdev(cruise) / statistics.mean(cruise) if len(cruise) > 30 else None
    fr, to, ctx = RKEY[rkey]
    if interrupted: to_label = 'Mississauga'
    else: to_label = PLACES[to]
    trip = {
        'id': f"t{start.strftime('%Y%m%d-%H%M')}", 'routeKey': rkey, 'vehicleId': car, 'type': 'passenger' if passenger else 'driver',
        'source': source, 'sourceDetail': extra.get('manualVia'),
        'start': start.isoformat(), 'end': (start + timedelta(seconds=T)).isoformat(),
        'partOfDay': part_of_day(start), 'label': f"{PLACES[fr]} → {to_label}", 'from': PLACES[fr], 'to': to_label,
        'context': ctx, 'distanceKm': round(dist_km, 1), 'durationS': int(T), 'movingS': moving, 'stoppedS': stopped,
        'stopCount': len(stops), 'avgMovingKmh': round(dist_km / (moving / 3600), 1) if moving else 0,
        'maxKmh': round(max_v), 'maxAt': {'t': max_t, 'lat': next((r[1] for r in rows if r[0] == max_t), None), 'lng': next((r[2] for r in rows if r[0] == max_t), None)},
        'elevGainM': round(eg), 'elevLossM': round(el), 'elevMinM': round(min(elev10)), 'elevMaxM': round(max(elev10)),
        '_score': score, '_cv': cv, '_extra': extra, '_soft': soft,
        'events': ev_objs, 'stops': stops, 'cleanest': cleanest,
        'bbox': [min(r[1] for r in rows if r[1]), min(r[2] for r in rows if r[2]), max(r[1] for r in rows if r[1]), max(r[2] for r in rows if r[2])],
        'track': {'fields': ['t', 'lat', 'lng', 'kmh', 'lonG', 'latG', 'elevM', 'q'], 'q': {'0': 'observed', '1': 'estimated', '2': 'gap', '3': 'excluded'}, 'rows': rows},
        'glyph': glyph(R, random.Random(77 + n_i), trim=True),
        '_sim': sim, '_endk': end_k, '_R': R, '_est': est_end, '_gap': gap, '_excl': excluded,
    }
    trips.append(trip)
    print(f"{trip['id']} {rkey:12s} {dist_km:5.1f} km {T/60:4.0f} min ev={[(e['type'], e['peakG'], e['place']) for e in ev_objs]} stops={len(stops)} max={max_v:.0f}")

# ------------------------------------------------------------------ scores, baselines, routes
def scorable(t): return t['type'] == 'driver' and not t['_extra'].get('unscorable')
hist_route, hist_ctx = {}, {}
scored = 0
records = {}
for t in trips:
    ex = t['_extra']
    t['routeId'] = ROUTE_OF.get(t['routeKey']) if not ex.get('interrupted') and t['type'] == 'driver' else None
    q_badge = 'Good'
    q_reason = None
    imu = 50
    if ex.get('excluded') or ex.get('gap'): q_badge = 'Usable'
    if ex.get('excluded'): q_badge = 'Limited'; q_reason = 'Phone moved for about 3 minutes'
    if ex.get('imu25'): q_badge = 'Usable'; imu = 25
    if ex.get('unscorable'): q_badge = 'Unscorable'; q_reason = 'Phone in a bag: orientation never settled'
    if t['type'] == 'passenger': q_badge = 'Good'
    T = t['durationS']
    excl_min = round(184 / 60) if ex.get('excluded') else 0
    t['quality'] = {
        'badge': q_badge, 'reason': q_reason, 'phone': 'loose' if ex.get('unscorable') else ('moved' if ex.get('excluded') else 'mounted'),
        'imuHz': imu, 'imuNote': 'Battery saver: sensors at 25 Hz' if imu == 25 else None,
        'gnssCoveragePct': 97 if ex.get('gap') else (99 if t['source'] == 'manual' else 98),
        'alignment': 'Settled after 40 s' if not ex.get('unscorable') else 'Never settled',
        'estimatedStartM': round(t['_sim']['pos'][int(t['_est'] * 10)]) if t['_est'] else 0,
        'spans': {'estimated': [[0, round(t['_est'])]] if t['_est'] else [], 'gap': [[round(t['_gap'][0]), round(t['_gap'][1])]] if t['_gap'] else [],
                  'excluded': [[round(t['_excl'][0]), round(t['_excl'][1]), 'Phone moved — not scored']] if t['_excl'] else []},
        'excludedMin': excl_min, 'scoredMin': round(T / 60) - excl_min, 'totalMin': round(T / 60),
    }
    if t['_extra'].get('interrupted'):
        t['interrupted'] = {'at': t['end'], 'reason': 'iOS ended DrivePerf in the background', 'note': 'Recording was interrupted. Everything before then is saved.'}
    if not scorable(t):
        t['smoothness'] = None
        continue
    score = t['_score']
    scored += 1
    rid = t['routeId']
    ctx = t['context']
    hr, hc = hist_route.get(rid, []), hist_ctx.get(ctx, [])
    if scored <= 3:
        base, basis, cal = None, None, {'k': scored, 'of': 3}
    elif rid and len(hr) >= 2:
        base, basis, cal = round(statistics.median(hr[-10:])), 'route', None
    elif hc:
        base, basis, cal = round(statistics.median(hc[-10:])), 'context', None
    else:
        all_s = [x for v in hist_ctx.values() for x in v]
        base, basis, cal = round(statistics.median(all_s[-10:])), 'context', None
    rec = None
    if rid:
        prev = records.get(rid)
        if len(hr) >= 2 and (t['_extra'].get('record') or (prev and score > prev['score'])) and t['quality']['badge'] in ('Good', 'Usable'):
            rec = {'isNew': True, 'previous': prev.copy() if prev else None}
            records[rid] = {'score': score, 'tripId': t['id'], 'date': t['start'][:10]}
        elif not prev:
            records[rid] = {'score': score, 'tripId': t['id'], 'date': t['start'][:10]}
    route_name = {'r_commute': 'The Commute', 'r_commute_home': 'The Commute home', 'r_gym': 'Gym'}.get(rid)
    if cal:
        cmp_text = 'Your baseline. Every drive from here is compared to you, not to anyone else.' if scored == 1 else f'Calibrating your baseline · {scored} of 3'
    elif basis == 'route':
        d = score - base
        cmp_text = f"{'+' if d > 0 else '−'}{abs(d)} vs your usual on {route_name}" if d else f"Right on your usual for {route_name}"
    else:
        d = score - base
        cmp_text = f"{'+' if d > 0 else '−'}{abs(d)} vs your usual {ctx} drives" if d else f"Right on your usual for {ctx} drives"
    # contributors
    cnt = {k: sum(1 for e in t['events'] if e['type'] == k and not e['needed']) for k in ('brake', 'accel', 'corner')}
    t['_cnt'] = cnt
    def band(k, typical):
        if cnt[k] < typical: return 'smoother'
        if cnt[k] > typical: return 'rougher'
        return 'usual'
    typical = {'brake': 1, 'accel': 1 if t['routeKey'] in ('commute_am', 'commute_pm') else 0, 'corner': 1 if 'twisty' in t['routeKey'] else 0}
    def reason(k, word):
        es = [e for e in t['events'] if e['type'] == k and not e['needed']]
        if not es: return f'No {word} this drive'
        roads = sorted({e['road'] or e['place'].replace('the ', '') for e in es})
        return f"{len(es)} {word if len(es) > 1 else word.rstrip('s')} on {' and '.join(('the ' + r) if r in ('QEW', 'Gardiner') else r for r in roads[:2])}"
    cons = 'usual'
    if t['_cv'] is not None:
        cons = 'smoother' if score >= (base or score) + 2 else 'rougher' if score <= (base or score) - 3 else 'usual'
    contributors = [
        {'id': 'braking', 'label': 'Braking', 'band': band('brake', typical['brake']), 'reason': reason('brake', 'abrupt stops'), 'eventIds': [e['id'] for e in t['events'] if e['type'] == 'brake']},
        {'id': 'acceleration', 'label': 'Acceleration', 'band': band('accel', typical['accel']), 'reason': reason('accel', 'sharp starts'), 'eventIds': [e['id'] for e in t['events'] if e['type'] == 'accel']},
        {'id': 'cornering', 'label': 'Cornering', 'band': band('corner', typical['corner']), 'reason': reason('corner', 'hard corners'), 'eventIds': [e['id'] for e in t['events'] if e['type'] == 'corner']},
        {'id': 'consistency', 'label': 'Consistency', 'band': cons, 'reason': {'smoother': 'Steadier speed than usual on the open stretches', 'usual': 'Your usual rhythm', 'rougher': 'Speed varied more than usual'}[cons], 'eventIds': []},
    ]
    t['smoothness'] = {'score': score, 'baseline': base, 'delta': (score - base) if base is not None else None, 'basis': basis,
                       'routeName': route_name, 'calibrating': cal, 'compareText': cmp_text, 'record': rec,
                       'basedOn': {'scoredMin': t['quality']['scoredMin'], 'totalMin': t['quality']['totalMin']} if t['quality']['excludedMin'] else None,
                       'contributors': contributors, 'provenance': 'derived', 'algorithm': 'smooth-v0.3 (placeholder, A12)'}
    hist_route.setdefault(rid, []).append(score)
    hist_ctx.setdefault(ctx, []).append(score)
    # tip
    evs = [e for e in t['events'] if not e['needed']]
    ev_t = max(evs, key=lambda e: e['peakG']) if evs else None
    ev_car = VEH[t['vehicleId']]
    if ev_t:
        hw = ev_t['road'] in ('QEW', 'Gardiner') or ev_t['place'].startswith('the QEW') or ev_t['place'].startswith('the Gardiner')
        if ev_t['type'] == 'brake':
            rural = any(road_class(n_, '') == 'rural' and short_road(n_) == ev_t['road'] for n_ in TARGET) or ev_t['road'] in ('Forks of the Credit', 'Olde Baseline Rd', 'Rockside Rd', 'McLaughlin Rd', 'Mississauga Rd', '10 Side Rd')
            tid = 'regen' if ev_car['powertrain'] == 'ev' and not hw else 'brake_traffic' if hw else ('brake_bend' if rural else 'brake_light')
        elif ev_t['type'] == 'accel':
            tid = 'accel_merge' if 'on-ramp' in ev_t['place'] else ('accel_ev' if ev_car['powertrain'] == 'ev' else 'accel_launch')
        else:
            tid = 'corner_bend' if 'twisty' in t['routeKey'] else 'corner_entry'
        road_word = ev_t['road']
        place = ev_t['place'].replace(' on-ramp', '').replace('the ', '') if tid == 'accel_merge' else ev_t['place']
        text = TIPS[tid]['text'].format(place=place, road=road_word if road_word not in ('QEW', 'Gardiner') else road_word)
        text = text.replace('on the the ', 'on the ').replace('the the ', 'the ')
        t['tip'] = {'id': tid, 'category': TIPS[tid]['cat'], 'text': text, 'eventId': ev_t['id'], 'place': ev_t['place']}
    elif t.get('_soft'):
        sp_place, sp_road = t['_soft']
        tid = 'brake_release' if road_class(sp_road, '') != 'highway' else 'consistency_hwy'
        t['tip'] = {'id': tid, 'category': TIPS[tid]['cat'], 'text': TIPS[tid]['text'].format(place=sp_place, road=('the ' + short_road(sp_road)) if road_class(sp_road, '') == 'highway' else short_road(sp_road)).replace('the the', 'the'), 'eventId': None, 'place': sp_place}
    else:
        rd = t['cleanest']['road'] or 'your route'
        tid = 'consistency_hwy' if t['context'] == 'highway' else 'anticipation_lights'
        t['tip'] = {'id': tid, 'category': TIPS[tid]['cat'], 'text': TIPS[tid]['text'].format(road=('the ' + rd) if rd in ('QEW', 'Gardiner') else rd, place=rd).replace('the the', 'the'), 'eventId': None, 'place': rd}
    # efficiency proxy
    stops_km = round(t['stopCount'] / max(t['distanceKm'], 0.1), 2)
    abrupt = cnt['accel']
    eff = 'better' if score >= (base or score) + 3 else 'less' if score <= (base or score) - 4 else 'usual'
    t['efficiency'] = {'rating': eff, 'label': {'better': 'Better than usual', 'usual': 'Usual', 'less': 'Less efficient than usual'}[eff],
                       'stopsPerKm': stops_km, 'abruptAccels': abrupt, 'speedConsistencyCv': round(t['_cv'], 3) if t['_cv'] else None,
                       'copy': ("Smoother habits like these usually help range. Check your car's display to see your number." if ev_car['powertrain'] == 'ev'
                                else "Smoother habits like these usually help fuel use. Check your car's trip computer for your number."),
                       'provenance': 'estimated'}

# story variants + copy
for t in trips:
    ex = t['_extra']
    sm = t.get('smoothness')
    car = VEH[t['vehicleId']]
    if t['type'] == 'passenger':
        variant = 'passenger'
    elif ex.get('unscorable'):
        variant = 'unscorable'
    elif ex.get('firstDrive'):
        variant = 'firstDrive'
    elif sm and sm['calibrating']:
        variant = 'calibrating'
    elif ex.get('routeDiscovered'):
        variant = 'routeDiscovered'
    elif sm and sm['record'] and sm['record']['isNew'] and sm['record']['previous']:
        variant = 'newRecord'
    elif t['quality']['badge'] == 'Limited':
        variant = 'degraded'
    else:
        variant = 'default'
    frames = {'f1': {'title': f"{t['partOfDay']} · {t['label']}", 'car': car['nickname'], 'carColour': car['colour']},
              'f2': {'distanceKm': t['distanceKm'], 'durationMin': round(t['durationS'] / 60), 'line': f"{t['distanceKm']} km · {round(t['durationS'] / 60)} min"},
              'f3': {'line': 'Your inputs, replayed.', 'sub': 'Every brake and turn in 3 seconds.'}}
    if sm:
        f4 = {'score': sm['score'], 'line': f"Smoothness {sm['score']}", 'compare': sm['compareText']}
        if variant == 'newRecord':
            p = sm['record']['previous']
            f4['record'] = f"New route record. {sm['score']} on {sm['routeName']}, beating {p['score']} from {datetime.fromisoformat(p['date']).strftime('%-d %b')}."
        if variant == 'degraded':
            f4['basedOn'] = f"Score based on {t['quality']['scoredMin']} of {t['quality']['totalMin']} min"
            frames['fDegraded'] = {'line': 'Your phone moved around for about 3 minutes, so we left that part out of the score.'}
        frames['f4'] = f4
        frames['f5'] = {'line': f"Cleanest stretch: {t['cleanest']['text']}"}
        frames['f6'] = {'line': f"One thing for next time: {t['tip']['text'][len('Next time, '):][0].lower() + t['tip']['text'][len('Next time, ')+1:]}" if t['tip']['text'].startswith('Next time, ') else t['tip']['text']}
    if variant == 'routeDiscovered':
        frames['fRoute'] = {'line': 'Looks like a regular. Name this route?', 'chips': ['Commute', 'Gym', 'School run', 'Custom…']}
    if variant == 'unscorable':
        frames['fUnscorable'] = {'line': "We couldn't score this one fairly (phone in a bag). Here's the route anyway."}
    nb = [e for e in t['events'] if e['needed']]
    if nb:
        frames['fNeeded'] = {'line': f"Hard stop at {nb[0]['place']}. Was it needed?", 'answer': 'Yes, it was needed', 'eventId': nb[0]['id']}
    frames['f7'] = {'actions': ['Explore the drive', 'Share', 'Done'], 'quality': f"{t['quality']['badge']} data · phone {t['quality']['phone']}"}
    t['story'] = {'variant': variant, 'frames': frames}
    # gg cloud (2 Hz, scorable spans)
    sim, endk = t['_sim'], t['_endk']
    gg = []
    if t['type'] == 'driver' and not ex.get('unscorable'):
        for k in range(0, endk, 5):
            tt = k / 10
            if tt <= t['_est'] or (t['_excl'] and t['_excl'][0] <= tt <= t['_excl'][1]): continue
            if sim['v'][k] < 1: continue
            gg.append([round(sim['lat'][k], 2), round(sim['lon'][k], 2)])
        if len(gg) > 420:
            stepg = len(gg) / 420
            gg = [gg[int(i * stepg)] for i in range(420)]
        env = []
        for sct in range(24):
            ang = sct * 15
            rs = [math.hypot(x, y) for x, y in gg if int(((math.degrees(math.atan2(y, x)) + 360) % 360) // 15) == sct]
            rs.sort()
            env.append(round(rs[int(len(rs) * 0.97)] if rs else 0.05, 2))
        env = [round(0.25 * env[i - 1] + 0.5 * env[i] + 0.25 * env[(i + 1) % 24], 2) for i in range(24)]
        t['gg'] = {'fields': ['latG', 'lonG'], 'points': gg, 'envelope': {'sectorDeg': 15, 'startDeg': 0, 'radiiG': env}}
    else:
        t['gg'] = None

# ------------------------------------------------------------------ routes
by_route = {}
for t in trips:
    if t.get('routeId'): by_route.setdefault(t['routeId'], []).append(t)
ROUTES = []
meta = {
    'r_commute': {'name': 'The Commute', 'from': 'Home', 'to': 'Work', 'named': True, 'linkedWith': 'r_commute_home', 'active': True, 'createdOn': '2026-10-02'},
    'r_commute_home': {'name': 'The Commute home', 'from': 'Work', 'to': 'Home', 'named': True, 'linkedWith': 'r_commute', 'active': True, 'createdOn': '2026-10-09'},
    'r_gym': {'name': 'Gym', 'from': 'Home', 'to': 'Gym', 'named': True, 'linkedWith': None, 'active': False, 'createdOn': '2026-11-04', 'freeLocked': True},
    'r_cand_gym_home': {'name': None, 'placeholder': 'Route 4', 'from': 'Gym', 'to': 'Home', 'named': False, 'candidate': True, 'linkedWith': None, 'active': False, 'createdOn': '2026-11-04'},
}
for rid, ts in by_route.items():
    runs = []
    for t in ts:
        sm = t.get('smoothness')
        runs.append({'tripId': t['id'], 'date': t['start'][:10], 'partOfDay': t['partOfDay'], 'score': sm['score'] if sm else None,
                     'durationS': t['durationS'], 'quality': t['quality']['badge'], 'vehicleId': t['vehicleId'],
                     'eligible': bool(sm) and t['quality']['badge'] in ('Good', 'Usable')})
    elig = [r for r in runs if r['eligible'] and r['score'] is not None]
    rec = max(elig, key=lambda r: (r['score'], r['date'])) if elig else None
    rs = ts[-1]['_R']
    m = meta[rid]
    if m.get('candidate'): rec = None
    ROUTES.append({'id': rid, **m, 'runs': runs, 'runCount': len(runs),
                   'record': {'score': rec['score'], 'tripId': rec['tripId'], 'date': rec['date']} if rec else None,
                   'recordHistory': [{'score': r['score'], 'date': r['date'], 'tripId': r['tripId']} for r in runs if r['eligible'] and r['score'] == max(x['score'] for x in elig if x['date'] <= r['date'])],
                   'trend': [{'date': r['date'], 'score': r['score']} for r in elig],
                   'distanceKm': round(rs['L'] / 1000, 1), 'glyph': glyph(rs, random.Random(hash(rid) % 1000), trim=True),
                   'recordRule': 'Smoothness only. Never duration. Runs outside your typical pace band cannot set a record.'})

# ghost comparison: latest commute run vs record run
def segments(t, nseg=12):
    sim, endk = t['_sim'], t['_endk']
    L = sim['pos'][endk]
    out = []
    for sgi in range(nseg):
        a, b = L * sgi / nseg, L * (sgi + 1) / nseg
        ks = [k for k in range(0, endk, 5) if a <= sim['pos'][k] < b]
        jerk = statistics.mean(abs(sim['lon'][k + 1] - sim['lon'][k]) + abs(sim['lat'][k + 1] - sim['lat'][k]) for k in ks) if ks else 0
        pk = max((abs(sim['lon'][k]) + abs(sim['lat'][k]) for k in ks), default=0)
        out.append(round(100 - jerk * 900 - pk * 40, 1))
    return out
cm = {t['id']: t for t in trips}
rc = next(r for r in ROUTES if r['id'] == 'r_commute')
recordT = cm[rc['record']['tripId']]
def seg_names(t, nseg=12):
    sim, R, endk = t['_sim'], t['_R'], t['_endk']
    L = sim['pos'][endk]
    names = []
    for sgi in range(nseg):
        mid = L * (sgi + 0.5) / nseg
        names.append(short_road(R['road'][min(len(R['road']) - 1, int(mid / R['ds']))]) or 'ramp')
    return names
def build_ghost(latest, recordT, label):
    sa, sb = segments(latest), segments(recordT)
    diffs = [round(a - b, 1) for a, b in zip(sa, sb)]
    names = seg_names(latest)
    gi = max(range(12), key=lambda i: diffs[i]); li = min(range(12), key=lambda i: diffs[i])
    Ltot = latest['distanceKm'] * 1000
    lost_n = sum(1 for e in latest['events'] if li / 12 <= (latest['_sim']['pos'][int(e['t'] * 10)] / Ltot) < (li + 1) / 12)
    nm = lambda n: ('the ' + n) if n in ('QEW', 'Gardiner') else n
    return {'routeId': 'r_commute', 'label': label, 'runTripId': latest['id'], 'recordTripId': recordT['id'], 'segments': [
        {'i': i, 'road': names[i], 'from': round(i / 12, 3), 'to': round((i + 1) / 12, 3), 'delta': diffs[i],
         'band': 'gained' if diffs[i] > 1.5 else 'lost' if diffs[i] < -1.5 else 'same', 'aligned': not (i == 11)} for i in range(12)],
        'gainedMost': {'segment': gi, 'text': (f"Where you gained most: {nm(names[gi])}. You braked earlier and lighter than on the other run." if diffs[gi] > 1.5
                       else (f"Level with your record on {nm(names[gi])}." if abs(diffs[gi]) < 1 else f"Closest to your record: {nm(names[gi])}, within {round(abs(diffs[gi]))} point{'s' if round(abs(diffs[gi])) != 1 else ''}."))},
        'lostMost': {'segment': li, 'text': (f"Where you lost most: {nm(names[li])}. " + (f"{lost_n} abrupt input{'s' if lost_n > 1 else ''} there that the other run didn't have." if lost_n else 'More small corrections than on the other run.')) if diffs[li] < -1.5 else None},
        'durations': {'runS': latest['durationS'], 'recordS': recordT['durationS'], 'note': 'Shown neutrally. Never a delta, never a win.'},
        'unalignedNote': 'The last segment could not be aligned (different lane into Spadina).'}
GHOST = build_ghost(cm['t20261105-0742'] if 't20261105-0742' in cm else recordT, cm[[r for r in rc['recordHistory'] if r['score'] == 89][0]['tripId']], 'New record (5 Nov) vs previous record (14 Oct)')
GHOST_LATEST = build_ghost(cm[rc['runs'][-2]['tripId']], recordT, 'Latest run (3 Nov) vs route record (5 Nov)')

# ------------------------------------------------------------------ weekly recaps
def week_start(d):
    d0 = (d.date() - timedelta(days=d.weekday()))
    return datetime(d0.year, d0.month, d0.day, tzinfo=TZ)
weeks = {}
for t in trips:
    st = datetime.fromisoformat(t['start'])
    weeks.setdefault(week_start(st), []).append(t)
RECAPS = []
prev_avg = None
week_list = sorted(weeks)
avgs = {}
for ws in week_list:
    ts = [t for t in weeks[ws] if t['type'] == 'driver' and t['quality']['badge'] != 'Unscorable']
    sc = [t['smoothness']['score'] for t in ts if t.get('smoothness')]
    avgs[ws] = round(statistics.mean(sc)) if sc else None
for wi, ws in enumerate(week_list):
    allt = weeks[ws]
    ts = [t for t in allt if t['type'] == 'driver']
    sc_t = [t for t in ts if t.get('smoothness')]
    avg = avgs[ws]
    prevw = week_list[wi - 1] if wi else None
    delta = (avg - avgs[prevw]) if prevw and avg is not None and avgs[prevw] is not None else None
    best = max(sc_t, key=lambda t: t['smoothness']['score']) if sc_t else None
    days = []
    for d in range(7):
        day = ws + timedelta(days=d)
        dd = [{'tripId': t['id'], 'km': t['distanceKm'], 'score': t['smoothness']['score'] if t.get('smoothness') else None}
              for t in ts if datetime.fromisoformat(t['start']).date() == day.date()]
        days.append({'date': day.date().isoformat(), 'dow': day.strftime('%a'), 'drives': dd})
    gentler = sum(1 for t in sc_t if t['_cnt']['brake'] <= 1)
    complete = ws + timedelta(days=7) <= TODAY
    calibrating = wi == 0
    trend = [{'weekStart': w.date().isoformat(), 'avg': avgs[w]} for w in week_list[max(0, wi - 3):wi + 1]]
    km = round(sum(t['distanceKm'] for t in ts), 1)
    secs = sum(t['durationS'] for t in ts)
    down_why = None
    if delta is not None and delta < 0 and sc_t:
        worst = min(sc_t, key=lambda t: t['smoothness']['score'])
        down_why = f"Down {abs(delta)}. {datetime.fromisoformat(worst['start']).strftime('%A')}'s drive pulled it down. Here's why →"
    RECAPS.append({
        'id': f"w{ws.date().isoformat()}", 'weekStart': ws.date().isoformat(), 'weekEnd': (ws + timedelta(days=6)).date().isoformat(),
        'deliveredAt': (ws + timedelta(days=6, hours=18)).isoformat() if complete else None, 'complete': complete, 'calibrating': calibrating,
        'drives': len(ts), 'km': km, 'durationS': secs, 'durationText': f"{secs // 3600} h {(secs % 3600) // 60} min",
        'avgSmoothness': avg, 'delta': delta, 'deltaText': (f"up {delta}" if delta and delta > 0 else f"down {abs(delta)}" if delta and delta < 0 else 'level with last week') if delta is not None else None,
        'downExplanation': down_why, 'days': days, 'trend4w': trend,
        'driveOfWeek': {'tripId': best['id'], 'label': best['label'], 'routeName': best['smoothness']['routeName'], 'partOfDay': best['partOfDay'], 'score': best['smoothness']['score']} if best else None,
        'focus': {'habit': 'braking into stops', 'line': f"Your focus: braking into stops. Gentler in {gentler} of {len(sc_t)} drives this week.", 'gentler': gentler, 'of': len(sc_t), 'meter3w': None} if len(sc_t) >= 5 else None,
        'notification': {'title': 'Your week, decoded.', 'body': f"{len(ts)} drives · Smoothness {avg} ({'+' if (delta or 0) > 0 else ''}{delta})" if delta is not None else f"{len(ts)} drives · Smoothness {avg}"},
        'empty': len(ts) == 0,
    })
# 3-week meter for focus
for i, r in enumerate(RECAPS):
    if r['focus']:
        r['focus']['meter3w'] = [x['focus']['gentler'] / max(1, x['focus']['of']) if x.get('focus') else 0 for x in RECAPS[max(0, i - 2):i + 1]]
        r['focus']['meter3w'] = [round(v, 2) for v in r['focus']['meter3w']]
RECAP_EXAMPLES = {
    'emptyWeek': {'drives': 0, 'empty': True, 'line': 'No drives this week. Your car got a rest.'},
    'trendDown': {'avgSmoothness': 80, 'delta': -2, 'downExplanation': "Down 2. Thursday's drive pulled it down. Here's why →"},
}

# ------------------------------------------------------------------ wrapped (October 2026) and annual sample
oct_t = [t for t in trips if t['start'][:7] == '2026-10' and t['type'] == 'driver']
oct_s = [t for t in oct_t if t.get('smoothness')]
km_oct = round(sum(t['distanceKm'] for t in oct_t))
hours = {}
for t in oct_t:
    h = datetime.fromisoformat(t['start']).hour
    hours[h] = hours.get(h, 0) + t['durationS']
top_h = max(hours, key=hours.get)
car_km = {}
for t in oct_t: car_km[t['vehicleId']] = car_km.get(t['vehicleId'], 0) + t['distanceKm']
smoothest = max(oct_s, key=lambda t: t['smoothness']['score'])
commute_oct = [t for t in oct_s if t.get('routeId') == 'r_commute']
first_half = [t['smoothness']['score'] for t in commute_oct[:3]]; second_half = [t['smoothness']['score'] for t in commute_oct[-3:]]
abrupt_sep = sum(1 for t in trips if t['start'][:7] == '2026-09' for e in t['events'] if e['type'] == 'brake') / max(1, sum(1 for t in trips if t['start'][:7] == '2026-09'))
abrupt_oct = sum(1 for t in oct_t for e in t['events'] if e['type'] == 'brake') / max(1, len(oct_t))
WRAPPED_OCT = {
    'id': 'wrap-2026-10', 'kind': 'monthly', 'period': 'October 2026', 'deliveredAt': '2026-11-01T09:00:00-05:00',
    'notification': {'title': 'October, wrapped.', 'body': "You're The Metronome this month."},
    'chapters': [
        {'id': 'totals', 'line': f"{len(oct_t)} drives · {km_oct:,} km", 'sub': f"{round(sum(t['durationS'] for t in oct_t) / 3600, 1)} hours behind the wheel"},
        {'id': 'trend', 'line': f"Average Smoothness {round(statistics.mean(t['smoothness']['score'] for t in oct_s))}", 'weeks': [r['avgSmoothness'] for r in RECAPS if r['weekStart'][:7] == '2026-10']},
        {'id': 'driveOfMonth', 'line': f"Smoothest drive: {datetime.fromisoformat(smoothest['start']).strftime('%-d %b')}, {smoothest['smoothness']['routeName'] or smoothest['label']}, {smoothest['smoothness']['score']}", 'tripId': smoothest['id']},
        {'id': 'focus', 'line': 'Your focus: braking into stops.', 'sub': f"Gentler in {sum(1 for t in oct_s if t['_cnt']['brake'] <= 1)} of {len(oct_s)} drives."},
        {'id': 'archetype', 'archetype': 'metronome', 'line': "You're The Metronome.", 'sub': 'The same calm rhythm, drive after drive.'},
        {'id': 'proRoutes', 'pro': True, 'line': f"The Commute: {min(first_half)} → {max(second_half)}", 'sub': 'Per-route deep dive (Pro chapter)'},
    ],
    'mostDrivenHour': f"{top_h % 12 or 12} {'am' if top_h < 12 else 'pm'}",
    'carSplit': [{'vehicleId': k, 'pct': round(100 * v / sum(car_km.values()))} for k, v in sorted(car_km.items(), key=lambda kv: -kv[1])],
}
all_d = [t for t in trips if t['type'] == 'driver']
best_year = max((t for t in all_d if t.get('smoothness') and t['smoothness']['routeName']), key=lambda t: (t['smoothness']['score'], t['start']))
WRAPPED_YEAR = {
    'id': 'wrap-2026', 'kind': 'annual', 'period': '2026', 'deliveredAt': '2026-12-01T09:00:00-05:00', 'sample': True,
    'note': 'Sample computed from the fixture (Sep 28 – Nov 6). Annual Wrapped ships by 1 Dec 2027 (F-NTF-05, P1).',
    'frames': [
        {'n': 1, 'line': '2026, decoded.'},
        {'n': 2, 'line': f"You drove {round(sum(t['distanceKm'] for t in all_d)):,} km.", 'sub': f"That's about {round(sum(t['distanceKm'] for t in all_d) / 541, 1)}× Toronto to Montréal.", 'comparison': {'ref': 'Toronto–Montréal by road', 'km': 541, 'provenance': 'estimated'}},
        {'n': 3, 'line': f"{round(sum(t['durationS'] for t in all_d) / 3600)} hours behind the wheel.", 'sub': f"Your most-driven hour: {WRAPPED_OCT['mostDrivenHour']}."},
        {'n': 4, 'archetype': 'glider', 'line': "You're The Glider.", 'sub': 'Few abrupt inputs, all year.'},
        {'n': 5, 'line': f"Smoothest drive of the year: {datetime.fromisoformat(best_year['start']).strftime('%-d %b')}, {best_year['smoothness']['routeName']}, {best_year['smoothness']['score']}.", 'tripId': best_year['id']},
        {'n': 6, 'line': 'Most improved: The Commute, 76 → 91 (Sep → Nov).'},
        {'n': 7, 'line': f"The GTI did {next(c['pct'] for c in WRAPPED_OCT['carSplit'] if c['vehicleId'] == 'gti')}% of the driving.", 'sub': 'The Ioniq did the road trip.', 'pro': True},
        {'n': 8, 'line': f"Habit mastered: gentler stops. {max(0, round(100 * (1 - abrupt_oct / max(abrupt_sep, 0.01))))}% fewer abrupt stops than in September.", 'significant': True},
        {'n': 9, 'line': 'Share your year.', 'cards': ['glyph', 'archetype', 'week']},
    ],
}
ARCHETYPES = [
    {'id': 'glider', 'name': 'The Glider', 'line': 'Few abrupt inputs, all year.'},
    {'id': 'metronome', 'name': 'The Metronome', 'line': 'The same calm rhythm, drive after drive.'},
    {'id': 'climber', 'name': 'The Climber', 'line': 'The most improved driver in your own history.'},
    {'id': 'nightowl', 'name': 'The Night Owl', 'line': 'Calm drives after dark.'},
    {'id': 'roadtripper', 'name': 'The Road-Tripper', 'line': 'Long drives, composed all the way.'},
    {'id': 'urbanist', 'name': 'The Urbanist', 'line': 'Stop-start mastery in the city.'},
]

# ------------------------------------------------------------------ assemble
preview_trips = [t for t in trips if t['start'][:10] <= '2026-10-12' and t['type'] == 'driver']
PREVIEW_RECAP = {
    'title': 'Your first two weeks, decoded.', 'period': {'from': '2026-09-28', 'to': '2026-10-12'},
    'drives': len(preview_trips), 'km': round(sum(t['distanceKm'] for t in preview_trips), 1),
    'smoothest': {'tripId': max((t for t in preview_trips if t.get('smoothness')), key=lambda t: t['smoothness']['score'])['id']},
    'routesFound': 2, 'comparisons': 11,
    'closingLine': {'CA': 'Pro showed you 2 routes and 11 comparisons. Keep it for CA$49.99/yr. Your first year is CA$39.99.',
                    'US': 'Pro showed you 2 routes and 11 comparisons. Keep it for US$39.99/yr. Your first year is US$29.99.'},
}

def clean(t):
    return {k: v for k, v in t.items() if not k.startswith('_')}

HEALTH = {
    'ios': {
        'items': [
            {'id': 'location', 'label': 'Location: Always', 'fix': 'Open Settings'},
            {'id': 'precise', 'label': 'Precise Location', 'fix': 'Open Settings'},
            {'id': 'motion', 'label': 'Motion & Fitness', 'fix': 'Open Settings'},
            {'id': 'notifications', 'label': 'Notifications', 'fix': 'Open Settings'},
            {'id': 'bgRefresh', 'label': 'Background App Refresh', 'fix': 'Open Settings'},
            {'id': 'lowPower', 'label': 'Low Power Mode', 'fix': 'How it affects recording'},
            {'id': 'liveActivities', 'label': 'Live Activities', 'fix': 'Open Settings'},
            {'id': 'autoDetect', 'label': 'Auto-detect', 'fix': 'Resume'},
            {'id': 'storage', 'label': 'Storage', 'fix': 'Manage storage'},
        ],
    },
    'android': {
        'items': [
            {'id': 'location', 'label': 'Location: Allow all the time', 'fix': 'Open settings'},
            {'id': 'precise', 'label': 'Precise location', 'fix': 'Open settings'},
            {'id': 'motion', 'label': 'Physical activity', 'fix': 'Open settings'},
            {'id': 'notifications', 'label': 'Notifications', 'fix': 'Open settings'},
            {'id': 'batteryOpt', 'label': 'Battery: Unrestricted', 'fix': 'Open battery settings'},
            {'id': 'oemSleep', 'label': 'Samsung: Never sleeping apps', 'fix': 'Show me how'},
            {'id': 'batterySaver', 'label': 'Battery Saver', 'fix': 'How it affects recording'},
            {'id': 'autoDetect', 'label': 'Auto-detect', 'fix': 'Resume'},
            {'id': 'storage', 'label': 'Storage', 'fix': 'Manage storage'},
        ],
    },
    'presets': {
        'allGood': {'summary': 'Automatic trips are ready.', 'promise': 'Auto-detects most drives. The first few hundred metres may be estimated. Force-quitting pauses auto-detect.',
                    'status': {}, 'lastCheckIn': '2026-11-06T19:12:00-05:00', 'lastTrip': trips[-1]['end']},
        'lowPower': {'summary': 'Recording works, with one thing to know.', 'status': {'lowPower': 'amber', 'batterySaver': 'amber'},
                     'detail': {'lowPower': 'Low Power Mode is on. Drives may start a little later.', 'batterySaver': 'Battery Saver is on. Drives may start a little later.'}},
        'alwaysLost': {'summary': 'Auto-detect needs attention.', 'status': {'location': 'red'},
                       'detail': {'location': 'Location access changed to "While Using". Automatic trips are paused. Two taps to fix it.'}},
        'motionOff': {'summary': 'Auto-detect needs attention.', 'status': {'motion': 'red', 'bgRefresh': 'red'},
                      'detail': {'motion': 'Motion access is off, so drives can\'t be detected.', 'bgRefresh': 'Background App Refresh is off for DrivePerf.'}},
        'notificationsOff': {'summary': 'Recording works. You won\'t hear when a drive is decoded.', 'status': {'notifications': 'amber'}},
        'paused': {'summary': 'Auto-detect is paused until 8:00 tomorrow.', 'status': {'autoDetect': 'amber'}, 'pausedUntil': '2026-11-07T08:00:00-05:00'},
        'storageLow': {'summary': 'Storage is getting low.', 'status': {'storage': 'amber'}, 'detail': {'storage': 'Less than 500 MB free. New drives still record; detailed data may be trimmed.'}},
        'oemKiller': {'summary': 'Auto-detect needs attention.', 'status': {'oemSleep': 'red', 'batteryOpt': 'amber'}, 'platform': 'android',
                      'detail': {'oemSleep': 'Samsung put DrivePerf in "Sleeping apps". Drives won\'t start on their own.', 'batteryOpt': 'Battery is set to Optimized.'}},
    },
}
PROVENANCE = {
    'start': 'observed', 'end': 'observed', 'duration': 'derived', 'movingTime': 'derived', 'stoppedTime': 'derived', 'distance': 'derived',
    'routeShape': 'observed', 'currentSpeed': 'observed', 'maxSpeed': 'derived', 'avgMovingSpeed': 'derived', 'stops': 'derived',
    'elevation': 'observed', 'elevationGainLoss': 'derived', 'longG': 'derived', 'latG': 'derived', 'gg': 'derived', 'eventPeakG': 'derived',
    'events': 'derived', 'eventConfidence': 'estimated', 'smoothness': 'derived', 'smoothnessDelta': 'derived', 'baseline': 'derived',
    'contributors': 'derived', 'efficiency': 'estimated', 'tip': 'derived', 'cleanestStretch': 'derived', 'qualityBadge': 'derived',
    'imuRate': 'observed', 'gnssCoverage': 'derived', 'estimatedStart': 'estimated', 'routeRecord': 'derived', 'whereYouGained': 'derived',
    'weeklyAverage': 'derived', 'changeClaim': 'derived', 'focusProgress': 'derived', 'wrappedTotals': 'derived', 'wrappedComparison': 'estimated',
    'archetype': 'estimated', 'partOfDay': 'derived', 'batteryUsed': 'observed', 'previewDaysLeft': 'observed', 'prices': 'observed',
}
PROV_LEGEND = {
    'observed': 'Measured directly by a sensor or the phone.',
    'derived': 'Calculated from measured data, the same way every time.',
    'estimated': 'Inferred or filled in where data was missing.',
}

DATA = {
    'meta': {'version': '1.0.0', 'generated': '2026-11-06', 'generator': 'docs/design/system/tools/gen-fixtures.py', 'today': TODAY.isoformat(),
             'timezone': 'America/Toronto', 'units': 'metric', 'thresholdsG': THRESH,
             'notes': 'Road geometry © OpenStreetMap contributors (ODbL), routed with the OSRM demo server; elevation from Open-Meteo (CC BY 4.0, Copernicus DEM). Speed and G channels are simulated; event thresholds are placeholders (A12).'},
    'user': {'id': 'u_local_7f3a', 'firstName': 'Marcus', 'displayName': 'Marcus', 'initials': 'M', 'ageConfirmed': True,
             'region': 'CA-ON', 'storefront': 'CA', 'locale': 'en-CA', 'units': {'distance': 'km', 'speed': 'km/h', 'elevation': 'm', 'temperature': 'C'},
             'installDate': '2026-09-28T18:05:00-04:00', 'account': {'mode': 'local', 'signedIn': False, 'email': None, 'provider': None},
             'places': [{'name': 'Home', 'lat': 43.45128, 'lng': -79.679924, 'kind': 'home'}, {'name': 'Work', 'lat': 43.645196, 'lng': -79.395018, 'kind': 'work'},
                        {'name': 'Gym', 'lat': 43.463302, 'lng': -79.687008, 'kind': 'custom'}],
             'capture': {'mode': 'auto', 'autoDetect': 'on', 'defaultVehicleId': 'gti', 'batterySaverRecording': False},
             'consent': {'usageStats': False, 'crashReports': True, 'backup': False},
             'notifications': {'driveDecoded': True, 'routes': True, 'weeklyRecap': True, 'wrapped': True, 'captureProblems': True, 'lifecycle': True, 'quietHours': {'from': '22:00', 'to': '07:00'}},
             'privacyZones': [{'id': 'pz1', 'name': 'Home', 'lat': 43.45128, 'lng': -79.679924, 'radiusM': 400}],
             'appearance': 'system', 'founding': True},
    'vehicles': list(VEH.values()),
    'trips': [clean(t) for t in sorted(trips, key=lambda t: t['start'], reverse=True)],
    'routes': ROUTES,
    'ghost': GHOST,
    'ghostLatest': GHOST_LATEST,
    'recaps': sorted(RECAPS, key=lambda r: r['weekStart'], reverse=True),
    'recapExamples': RECAP_EXAMPLES,
    'wrapped': {'monthly': WRAPPED_OCT, 'annual': WRAPPED_YEAR, 'archetypes': ARCHETYPES},
    'previewRecap': PREVIEW_RECAP,
    'entitlement': {
        'current': 'pro',
        'states': {
            'preview': {'plan': 'preview', 'isPro': True, 'day': 9, 'of': 14, 'endsOn': '2026-10-12', 'label': 'Pro Preview · day 9 of 14'},
            'previewEnding': {'plan': 'preview', 'isPro': True, 'day': 13, 'of': 14, 'endsOn': '2026-10-12', 'label': 'Pro Preview · day 13 of 14', 'notice': 'Your Pro Preview ends in 2 days. Your trips stay yours either way.'},
            'rideAlong': {'plan': 'ridealong', 'isPro': True, 'day': 4, 'of': 30, 'from': 'Priya', 'label': 'Ride-along pass · day 4 of 30'},
            'free': {'plan': 'free', 'isPro': False, 'since': '2026-10-13', 'activeRouteIds': ['r_commute', 'r_commute_home'], 'activeVehicleId': 'gti'},
            'pro': {'plan': 'pro_annual', 'isPro': True, 'founding': True, 'since': '2026-10-13', 'renews': '2027-10-13', 'price': 'CA$39.99 first year, then CA$49.99/yr', 'store': 'App Store / Google Play'},
            'proMonthly': {'plan': 'pro_monthly', 'isPro': True, 'founding': False, 'since': '2026-10-13', 'renews': '2026-12-13', 'price': 'CA$8.99/month'},
        },
        'prices': {'CA': {'monthly': 'CA$8.99', 'annual': 'CA$49.99', 'founding': 'CA$39.99', 'annualPerMonth': 'CA$4.17'},
                   'US': {'monthly': 'US$6.99', 'annual': 'US$39.99', 'founding': 'US$29.99', 'annualPerMonth': 'US$3.33'}},
        'freeLimits': {'analysisDays': 30, 'routes': 2, 'vehicles': 1},
        'proFeatures': ['Replay and Analysis for every drive, not just the last 30 days', 'Unlimited routes, ghost comparison and "where you gained"',
                        'Month, year, per-car and per-route trends', 'Per-trip GPX/CSV export', 'Cloud backup and restore', 'Unlimited cars', 'Extra Wrapped chapters and card styles'],
        'lockSheets': {
            'compare': 'Keep comparing every run of The Commute with Pro.', 'oldAnalysis': 'Keep exploring every drive with Pro.',
            'thirdRoute': 'Track every route you drive with Pro.', 'secondCar': 'Keep a log for every car in your garage with Pro.',
            'export': 'Export any drive as GPX or CSV with Pro.', 'trends': 'See how your driving changes month to month with Pro.',
            'backup': 'Back up your drives and restore them on any phone with Pro.',
        },
    },
    'health': HEALTH,
    'liveTrip': {
        'states': {
            'manual': {'source': 'manual', 'vehicleId': 'gti', 'startedAt': '2026-11-06T17:31:40-05:00', 'elapsedS': 864, 'distanceKm': 9.6, 'gps': 'good'},
            'auto': {'source': 'auto', 'vehicleId': 'gti', 'startedAt': '2026-11-06T17:31:40-05:00', 'elapsedS': 1310, 'distanceKm': 17.2, 'gps': 'good', 'estimatedStartM': 340},
            'gpsSearching': {'source': 'manual', 'vehicleId': 'gti', 'startedAt': '2026-11-06T17:31:40-05:00', 'elapsedS': 22, 'distanceKm': 0.0, 'gps': 'searching'},
            'lowBattery': {'source': 'auto', 'vehicleId': 'ioniq', 'startedAt': '2026-11-06T17:31:40-05:00', 'elapsedS': 2280, 'distanceKm': 31.4, 'gps': 'good', 'batterySaver': True},
            'passenger': {'source': 'auto', 'vehicleId': None, 'startedAt': '2026-11-06T17:31:40-05:00', 'elapsedS': 640, 'distanceKm': 6.1, 'gps': 'good', 'passenger': True},
            'cap8h': {'source': 'manual', 'vehicleId': 'gti', 'elapsedS': 28800, 'distanceKm': 611.0, 'liveActivityEnded': True},
        },
        'rules': 'Recording status only: elapsed time, distance, car. Never speed, score, events or record pace.',
    },
    'notifications': [
        {'id': 'n_decoded', 'type': 'driveDecoded', 'title': 'Drive decoded.', 'body': 'Home → Work · Smoothness 91', 'tripId': None},
        {'id': 'n_first', 'type': 'firstStory', 'title': 'Your first Drive Story is ready.', 'body': 'Two minutes well spent.'},
        {'id': 'n_coalesced', 'type': 'driveDecoded', 'title': '2 drives decoded.', 'body': 'Home → Gym · Gym → Home'},
        {'id': 'n_route', 'type': 'routeDiscovered', 'title': 'Looks like a regular.', 'body': "Name the route you've now driven 3 times?"},
        {'id': 'n_record', 'type': 'routeRecord', 'title': 'New route record on The Commute.', 'body': '91, beating 89 from 14 Oct.'},
        {'id': 'n_week', 'type': 'weeklyRecap', 'title': 'Your week, decoded.', 'body': None},
        {'id': 'n_wrapped', 'type': 'monthlyWrapped', 'title': 'October, wrapped.', 'body': "You're The Metronome this month."},
        {'id': 'n_capture', 'type': 'captureProblem', 'title': 'Recording is paused.', 'body': 'Location access changed to "While Using". Two taps to fix it.'},
        {'id': 'n_missed', 'type': 'missedTrip', 'title': 'Did you drive around 5 pm?', 'body': "We may have missed it. Tell us and we'll tune detection."},
        {'id': 'n_preview', 'type': 'previewEnding', 'title': 'Your Pro Preview ends in 2 days.', 'body': 'Your trips stay yours either way.'},
        {'id': 'n_lapsed', 'type': 'lapsed', 'title': 'Recording is still on.', 'body': "We'll tell you when the next drive is decoded."},
        {'id': 'n_recording', 'type': 'recording', 'title': 'Recording your drive', 'body': 'Tap to open. Stop it any time after you park.', 'silent': True},
        {'id': 'n_watchdog', 'type': 'watchdog', 'title': 'Auto-detect may be paused.', 'body': 'Open DrivePerf once to switch it back on.'},
    ],
    'tips': [{'id': k, 'category': v['cat'], 'template': v['text']} for k, v in TIPS.items()],
    'provenance': {'metrics': PROVENANCE, 'legend': PROV_LEGEND},
    'privacy': {'backup': {'state': 'off', 'states': ['off', 'upToDate', 'pending', 'wifiOnly', 'error', 'lapsed']}, 'dataSize': {'trips': 34, 'mb': 186, 'rawMb': 151}},
}
# fill dynamic notification bodies
latest_trip = DATA['trips'][0]
for nt in DATA['notifications']:
    if nt['id'] == 'n_decoded':
        rec = next(t for t in DATA['trips'] if t['id'].startswith('t20261105-07'))
        nt['tripId'] = rec['id']
    if nt['id'] == 'n_week':
        r = next(r for r in DATA['recaps'] if r['complete'])
        nt['body'] = r['notification']['body']; nt['recapId'] = r['id']
DATA['privacy']['dataSize']['trips'] = len(DATA['trips'])

js = ('/* DrivePerf shared fixtures — GENERATED by docs/design/system/tools/gen-fixtures.py. Do not edit by hand.\n'
      '   Shape documented in docs/design/system/fixtures.md. Used by prototypes/ios and prototypes/android. */\n'
      'window.DP_DATA = ' + json.dumps(DATA, ensure_ascii=False, separators=(',', ':')) + ';\n' + open(os.path.join(HERE, 'data-util.js')).read())
with open(OUT, 'w') as f:
    f.write(js)
print('wrote', OUT, f'{len(js) / 1024:.0f} KB', len(DATA['trips']), 'trips')
