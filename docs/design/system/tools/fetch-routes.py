#!/usr/bin/env python3
"""Fetches real road geometry (OSRM demo server) and elevation (Open-Meteo) for the fixture
routes and caches them in tools/cache/. Run once; gen-fixtures.py then works offline.
usage: python3 docs/design/system/tools/fetch-routes.py"""
import json, os, urllib.request, time

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, 'cache')
os.makedirs(CACHE, exist_ok=True)

P = {
    'home': (-79.679924, 43.45128),        # Riverside Dr, Oakville (user-named "Home")
    'work': (-79.395018, 43.645196),       # Spadina Ave at King St W, Toronto (user-named "Work")
    'gym': (-79.687008, 43.463302),        # Oakville, Trafalgar Rd area
    'grocery': (-79.694852, 43.455608),    # Kerr Village
    'bronte': (-79.710588, 43.392571),     # Bronte Harbour
    'portcredit': (-79.584979, 43.552009), # Port Credit
    'lorne': (-79.6105, 43.5158),          # Lakeshore Rd W, Lorne Park (forces Lakeshore)
    'clarkson': (-79.6370, 43.4975),       # Lakeshore Rd W, Clarkson
    'lse': (-79.628704, 43.482331),        # Lakeshore Rd E, east Oakville
    'notl': (-79.07148, 43.255021),        # Niagara-on-the-Lake, Queen St
    'fotc_e': (-79.975221, 43.816999),     # Forks of the Credit Rd, east end
    'fotc_m': (-79.990567, 43.807133),
    'fotc_w': (-80.000082, 43.80179),
    'belfountain': (-80.008428, 43.794047),
    'terracotta': (-79.9573, 43.7173),
    'rockside': (-79.958075, 43.74442),     # Rockside Rd (start of the Escarpment roads)
    'baseline': (-79.96875, 43.752267),     # Olde Baseline Rd
    'mclaughlin': (-79.926184, 43.791086),  # McLaughlin Rd
    'burlington': (-79.797608, 43.324574), # Spencer Smith Park
}
ROUTES = {
    'commute_am': ['home', 'work'],
    'commute_pm': ['work', 'home'],
    'gym': ['home', 'gym'],
    'gym_back': ['gym', 'home'],
    'grocery': ['home', 'grocery'],
    'grocery_back': ['grocery', 'home'],
    'bronte': ['home', 'bronte'],
    'lakeshore': ['home', 'lse', 'portcredit'],
    'lakeshore_back': ['portcredit', 'home'],
    'notl': ['home', 'notl'],
    'notl_back': ['notl', 'home'],
    'twisty': ['home', 'rockside', 'baseline', 'mclaughlin', 'fotc_e', 'fotc_m', 'fotc_w', 'belfountain'],
    'twisty_back': ['belfountain', 'home'],
    'burlington': ['home', 'burlington'],
}

def get(url):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=30) as r:
                return json.load(r)
        except Exception as e:
            print('retry', e); time.sleep(15 + attempt * 20)
    raise SystemExit('failed ' + url)

for name, wps in ROUTES.items():
    out = os.path.join(CACHE, f'osrm-{name}.json')
    if os.path.exists(out):
        continue
    coords = ';'.join(f'{P[w][0]},{P[w][1]}' for w in wps)
    url = (f'https://router.project-osrm.org/route/v1/driving/{coords}'
           '?overview=full&geometries=geojson&steps=true&annotations=distance,duration,speed&continue_straight=true')
    d = get(url)
    r = d['routes'][0]
    roads = []
    for leg in r['legs']:
        for st in leg['steps']:
            if st['name'] and (not roads or roads[-1] != st['name']):
                roads.append(st['name'])
    json.dump(d, open(out, 'w'))
    print(f"{name}: {r['distance']/1000:.1f} km {r['duration']/60:.0f} min · " + ' → '.join(roads[:14]))
    time.sleep(1)

# elevation: sample every ~150 m along each route
import math
def hav(a, b):
    R = 6371000
    la1, la2 = math.radians(a[1]), math.radians(b[1])
    dla, dlo = la2 - la1, math.radians(b[0] - a[0])
    h = math.sin(dla / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin(dlo / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))
for name in ROUTES:
    out = os.path.join(CACHE, f'elev-{name}.json')
    if os.path.exists(out):
        continue
    g = json.load(open(os.path.join(CACHE, f'osrm-{name}.json')))['routes'][0]['geometry']['coordinates']
    samples, acc, last = [g[0]], 0, g[0]
    for c in g[1:]:
        acc += hav(last, c); last = c
        if acc >= 150:
            samples.append(c); acc = 0
    samples.append(g[-1])
    elev = []
    for i in range(0, len(samples), 90):
        chunk = samples[i:i + 90]
        url = ('https://api.open-meteo.com/v1/elevation?latitude=' + ','.join(f'{c[1]:.5f}' for c in chunk)
               + '&longitude=' + ','.join(f'{c[0]:.5f}' for c in chunk))
        elev += get(url)['elevation']
        time.sleep(4)
    json.dump({'points': samples, 'elevation': elev}, open(out, 'w'))
    print(f'elev {name}: {len(samples)} samples, {min(elev):.0f}–{max(elev):.0f} m')
