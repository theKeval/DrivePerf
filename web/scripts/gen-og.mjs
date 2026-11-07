// Generates the social image and icon set from the brand assets (docs/design/system/assets) and the shared
// fixtures, into web/public. Run once after a brand change (`npm run og`); the outputs are committed.
//   public/og/home-1200x630.png      Open Graph / Twitter card (1200×630)
//   public/apple-touch-icon.png      180×180 (iOS icon, Default appearance)
//   public/icons/icon-{192,512}.png  web manifest icons; icon-maskable-512.png has the safe-zone padding
//   public/favicon.ico               32×32 PNG-in-ICO for browsers that ignore the SVG favicon
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';
import { route } from '../src/lib/geo.ts';

const here = (p) => fileURLToPath(new URL(p, import.meta.url));
const assets = here('../../docs/design/system/assets/');
const pub = here('../public/');
fs.mkdirSync(pub + 'og', { recursive: true });
fs.mkdirSync(pub + 'icons', { recursive: true });

const fixtures = JSON.parse(fs.readFileSync(here('../src/data/fixtures.json'), 'utf8'));
const hero = fixtures.trips['t20261105-0742'];
const roads = ['t20261105-1745', 't20261101-1020', 't20261024-0930', 't20261013-1742', 't20261017-1120', 't20261104-0650'].map((id) => fixtures.trips[id]);

/* ---------- Loop mark geometry (brand.md §3.1; same construction as src/lib/loop.ts) ---------- */
function loopSvg({ x, y, size, color = '#5BE7C4', dot = '#F2F4F7' }) {
  const cx = 32, cy = 32, r = 18, sw = 7, a0 = -348, a1 = -78, n = 6;
  const P = (a) => [cx + r * Math.cos((a * Math.PI) / 180), cy + r * Math.sin((a * Math.PI) / 180)];
  let defs = '', arcs = '';
  for (let i = 0; i < n; i++) {
    const s = a0 + ((a1 - a0) * i) / n, e = a0 + ((a1 - a0) * (i + 1)) / n;
    const o0 = 0.03 + 0.97 * (i / n) ** 1.5, o1 = 0.03 + 0.97 * ((i + 1) / n) ** 1.5;
    const [x0, y0] = P(s), [x1, y1] = P(e), [ex, ey] = P(e + (i < n - 1 ? 0.6 : 0));
    defs += `<linearGradient id="og${i}" gradientUnits="userSpaceOnUse" x1="${x0}" y1="${y0}" x2="${x1}" y2="${y1}"><stop offset="0" stop-color="${color}" stop-opacity="${o0}"/><stop offset="1" stop-color="${color}" stop-opacity="${o1}"/></linearGradient>`;
    arcs += `<path d="M${x0} ${y0} A${r} ${r} 0 0 1 ${ex} ${ey}" stroke="url(#og${i})" stroke-width="${sw}" fill="none"/>`;
  }
  const [hx, hy] = P(a1), [dx, dy] = P(a1 + 34);
  return { defs, body: `<g transform="translate(${x} ${y}) scale(${size / 64})">${arcs}<circle cx="${hx}" cy="${hy}" r="${sw / 2}" fill="${color}"/><circle cx="${dx}" cy="${dy}" r="${(sw * 1.22) / 2}" fill="${dot}"/></g>` };
}

/* ---------- OG image ---------- */
const W = 1200, H = 630;
const box = { x: 560, y: 40, w: 680, h: 560 };
const r = route(hero.rows, W, H, { pad: 0, rotate: -6, box });
const roadD = roads.map((t) => route(t.rows, W, H, { P: r.P }).d).join(' ');
const clean = r.segment(hero.cleanest.t0, hero.cleanest.t1);
const end = r.xy[r.xy.length - 1];
const mark = loopSvg({ x: 84, y: 214, size: 104 });
const font = `'Google Sans Flex', 'Roboto Flex', 'SF Pro Display', 'Helvetica Neue', Arial, sans-serif`;
const og = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <defs>
    <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0A1420"/><stop offset=".55" stop-color="#080B10"/><stop offset="1" stop-color="#05070A"/></linearGradient>
    <radialGradient id="halo" cx=".78" cy=".45" r=".55"><stop offset="0" stop-color="#5BE7C4" stop-opacity=".10"/><stop offset="1" stop-color="#5BE7C4" stop-opacity="0"/></radialGradient>
    <linearGradient id="fade" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#5BE7C4" stop-opacity=".35"/><stop offset=".35" stop-color="#5BE7C4"/><stop offset="1" stop-color="#C6F8EA"/></linearGradient>
    <filter id="glow" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="10"/></filter>
    <radialGradient id="head"><stop offset="0" stop-color="#fff"/><stop offset=".35" stop-color="#C6F8EA" stop-opacity=".9"/><stop offset="1" stop-color="#5BE7C4" stop-opacity="0"/></radialGradient>
    ${mark.defs}
  </defs>
  <rect width="${W}" height="${H}" fill="url(#sky)"/>
  <rect width="${W}" height="${H}" fill="url(#halo)"/>
  <path d="${roadD}" fill="none" stroke="#96A0B4" stroke-opacity=".24" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="${r.d}" fill="none" stroke="#5BE7C4" stroke-width="18" stroke-linecap="round" opacity=".28" filter="url(#glow)"/>
  <path d="${r.d}" fill="none" stroke="url(#fade)" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="${clean}" fill="none" stroke="#C6F8EA" stroke-width="7" stroke-linecap="round" opacity=".9"/>
  <circle cx="${end[0]}" cy="${end[1]}" r="20" fill="url(#head)" opacity=".6"/><circle cx="${end[0]}" cy="${end[1]}" r="7" fill="#fff"/>
  ${mark.body}
  <text x="206" y="290" font-family="${font}" font-size="76" font-weight="700" letter-spacing="-1" fill="#F2F4F7">DrivePerf</text>
  <text x="88" y="400" font-family="${font}" font-size="44" font-weight="600" fill="#F2F4F7">Smooth is a skill.</text>
  <text x="88" y="452" font-family="${font}" font-size="28" font-weight="400" fill="#A3AAB6">Every drive, decoded. Private by design.</text>
  <text x="88" y="560" font-family="${font}" font-size="22" font-weight="600" fill="#5BE7C4">driveperf.com</text>
  <text x="1176" y="612" text-anchor="end" font-family="${font}" font-size="13" fill="#8790A0">© OpenStreetMap contributors</text>
</svg>`;
await sharp(Buffer.from(og)).png({ compressionLevel: 9, palette: false }).toFile(pub + 'og/home-1200x630.png');

/* ---------- Icons ---------- */
const iosIcon = fs.readFileSync(assets + 'icon-ios-default.svg');
await sharp(iosIcon, { density: 96 }).resize(180, 180).png().toFile(pub + 'apple-touch-icon.png');
for (const s of [192, 512]) await sharp(iosIcon, { density: 96 }).resize(s, s).png().toFile(pub + `icons/icon-${s}.png`);
// Maskable: the full-bleed icon background already fills the square; scale the art into the 80 % safe zone.
const inner = await sharp(iosIcon, { density: 96 }).resize(410, 410).png().toBuffer();
await sharp({ create: { width: 512, height: 512, channels: 4, background: '#05070A' } })
  .composite([{ input: inner, left: 51, top: 51 }]).png().toFile(pub + 'icons/icon-maskable-512.png');

const fav = fs.readFileSync(assets + 'favicon.svg');
const png32 = await sharp(fav, { density: 96 }).resize(32, 32).png().toBuffer();
// ICO container with one embedded PNG image (supported by every current browser).
const ico = Buffer.alloc(6 + 16);
ico.writeUInt16LE(0, 0); ico.writeUInt16LE(1, 2); ico.writeUInt16LE(1, 4);
ico.writeUInt8(32, 6); ico.writeUInt8(32, 7); ico.writeUInt8(0, 8); ico.writeUInt8(0, 9);
ico.writeUInt16LE(1, 10); ico.writeUInt16LE(32, 12); ico.writeUInt32LE(png32.length, 14); ico.writeUInt32LE(22, 18);
fs.writeFileSync(pub + 'favicon.ico', Buffer.concat([ico, png32]));
fs.copyFileSync(assets + 'favicon.svg', pub + 'favicon.svg');
console.log('wrote og/home-1200x630.png, apple-touch-icon.png, icons/*, favicon.ico, favicon.svg');
