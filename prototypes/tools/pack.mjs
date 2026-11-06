#!/usr/bin/env node
// Pack exported design artifacts for git (DRIVE-32).
// export.mjs / export-web.mjs write full-resolution PNGs into every story folder, so a screen used by
// five stories is stored five times (about 2 GB in total). This script:
//   1. moves each docs/design/<KEY>/<platform>/screens/<name>.png into ONE shared gallery,
//      docs/design/screens/<platform>/<name>.webp. Default-state app screens keep full device
//      resolution (iOS 1206×2622, Android 1082×2402); other app states are stored at 2× (804 / 824 px
//      wide); web shots keep their size;
//   2. converts mockup.png (the contact sheet) to mockup.webp;
//   3. rewrites the links in design-spec.md and mockup.html, then deletes the PNGs.
// Run it after every export:  node prototypes/tools/export.mjs && node prototypes/tools/pack.mjs
// Needs cwebp (brew install webp). Idempotent: an existing gallery file is reused, not re-encoded,
// unless you pass --force.
import { readdirSync, existsSync, mkdirSync, readFileSync, writeFileSync, rmSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const DESIGN = join(ROOT, 'docs', 'design');
const GALLERY = join(DESIGN, 'screens');
const FORCE = process.argv.includes('--force');
const STATE_W = { ios: 804, android: 824 };
const MAX = 16383; // WebP's limit on either dimension; tall full-page web shots are scaled down to fit
const dims = (src) => { const o = execFileSync('sips', ['-g', 'pixelWidth', '-g', 'pixelHeight', src]).toString(); return [+o.match(/pixelWidth: (\d+)/)[1], +o.match(/pixelHeight: (\d+)/)[1]]; };
const cwebp = (src, out, q, width) => {
  const [w, h] = dims(src);
  let tw = width || 0;
  const scale = (tw || w) / w;
  if (h * scale > MAX) tw = Math.floor((w * MAX) / h);
  execFileSync('cwebp', ['-quiet', '-q', String(q), '-m', '6', ...(tw ? ['-resize', String(tw), '0'] : []), src, '-o', out]);
};

let moved = 0, reused = 0, sheets = 0, before = 0, after = 0;
for (const key of readdirSync(DESIGN).filter((d) => /^DRIVE-\d+$/.test(d)).sort()) {
  for (const platform of ['ios', 'android', 'web']) {
    const dir = join(DESIGN, key, platform);
    if (!existsSync(dir)) continue;
    const sdir = join(dir, 'screens');
    if (existsSync(sdir)) {
      mkdirSync(join(GALLERY, platform), { recursive: true });
      for (const f of readdirSync(sdir).filter((n) => n.endsWith('.png'))) {
        const src = join(sdir, f), out = join(GALLERY, platform, f.replace(/\.png$/, '.webp'));
        before += statSync(src).size;
        if (FORCE || !existsSync(out)) {
          const isDefault = !f.includes('--');
          if (platform === 'web') cwebp(src, out, 80);
          else cwebp(src, out, isDefault ? 85 : 80, isDefault ? 0 : STATE_W[platform]);
          moved++;
        } else reused++;
        rmSync(src);
      }
      if (!readdirSync(sdir).length) rmSync(sdir, { recursive: true });
    }
    const png = join(dir, 'mockup.png'), webp = join(dir, 'mockup.webp');
    if (existsSync(png)) { before += statSync(png).size; cwebp(png, webp, 80); rmSync(png); sheets++; }
    if (existsSync(webp)) after += statSync(webp).size;
    for (const doc of ['design-spec.md', 'mockup.html']) {
      const p = join(dir, doc);
      if (!existsSync(p)) continue;
      const src = readFileSync(p, 'utf8');
      const out = src
        .replace(/(?<![\w/.-])screens\/([\w.-]+?)\.png/g, `../../screens/${platform}/$1.webp`)
        .replace(/(?<![\w/.-])mockup\.png/g, 'mockup.webp');
      if (out !== src) writeFileSync(p, out);
    }
  }
}
const gal = (p) => (existsSync(p) ? readdirSync(p).reduce((s, f) => s + statSync(join(p, f)).size, 0) : 0);
after += ['ios', 'android', 'web'].reduce((s, p) => s + gal(join(GALLERY, p)), 0);
console.log(`Encoded ${moved} gallery images, reused ${reused}, converted ${sheets} contact sheets.`);
console.log(`PNG removed: ${(before / 1e6).toFixed(0)} MB → gallery + sheets now ${(after / 1e6).toFixed(0)} MB.`);
