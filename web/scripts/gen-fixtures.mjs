// Extracts the small subset of the shared design fixtures (prototypes/shared/data.js, 888 KB) that the website
// draws, into src/data/fixtures.json. Build-time input only: pages render SVG strings from it, and the client
// receives only precomputed geometry. Re-run after the fixtures change: `npm run fixtures`.
import vm from 'node:vm';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

const src = fileURLToPath(new URL('../../prototypes/shared/data.js', import.meta.url));
const out = fileURLToPath(new URL('../src/data/fixtures.json', import.meta.url));
const ctx = { window: {} };
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(src, 'utf8'), ctx);
const D = ctx.window.DP_DATA;

const HERO = 't20261105-0742';
const NEEDED = 't20261020-0740';
const ROADS = ['t20261105-1745', 't20261101-1020', 't20261024-0930', 't20261013-1742', 't20261017-1120', 't20261104-0650'];
const LOST = 't20261010-1015'; // S-WEB-11 404 background: the twisty drive to Belfountain

// Track rows reduced to [t, lat, lng, lonG, latG]; coordinates as in the fixtures (already synthetic/privacy-trimmed).
const slimTrack = (trip) => {
  const f = trip.track.fields;
  const ix = ['t', 'lat', 'lng', 'lonG', 'latG'].map((k) => f.indexOf(k));
  return trip.track.rows.map((r) => ix.map((i) => r[i]));
};
const trip = (id, full) => {
  const t = D.util.trip(id);
  const base = { id: t.id, rows: slimTrack(t) };
  if (!full) return base;
  return {
    ...base,
    vehicleId: t.vehicleId, durationS: t.durationS, cleanest: t.cleanest, glyph: t.glyph, smoothness: t.smoothness,
    tip: t.tip, story: t.story, gg: t.gg, events: t.events,
  };
};

const fixtures = {
  generatedFrom: 'prototypes/shared/data.js (' + D.meta.version + ', ' + D.meta.generated + ')',
  trips: Object.fromEntries([
    [HERO, trip(HERO, true)],
    [NEEDED, trip(NEEDED, true)],
    ...ROADS.map((id) => [id, trip(id, false)]),
    [LOST, trip(LOST, false)],
  ]),
  vehicles: D.vehicles.map(({ id, nickname, colour }) => ({ id, nickname, colour })),
  recap: D.recaps.find((r) => r.complete),
  routeCommute: (({ id, glyph, runs, record }) => ({ id, glyph, runs, record }))(D.util.route('r_commute')),
  prices: D.entitlement.prices,
};
fs.writeFileSync(out, JSON.stringify(fixtures));
console.log(`wrote ${out} (${(fs.statSync(out).size / 1024).toFixed(1)} KB)`);
