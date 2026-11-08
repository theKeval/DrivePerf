// Post-build guard for dist/ (runs in `npm run build`, so CI fails on any breach):
//  1. every inline executable <script> is pinned by hash in the CSP of backend/firebase/firebase.json
//  2. privacy baseline: no Google Fonts, Google Analytics/gtag, reCAPTCHA or cookie writes in the shipped files
//  3. every page has one <h1>, a <title>, a meta description and a canonical link
//  4. every S-WEB route, the app-link files, sitemap.xml and robots.txt exist
//  5. first-load budget (ADR-009): HTML + CSS + JS loaded by a page ≤ 500 KB (uncompressed, lazy chunks excluded)
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const dist = fileURLToPath(new URL('../dist/', import.meta.url));
const firebaseJson = fileURLToPath(new URL('../../backend/firebase/firebase.json', import.meta.url));
const errors = [];
const fail = (m) => errors.push(m);

const walk = (d) => fs.readdirSync(d, { withFileTypes: true }).flatMap((e) => (e.isDirectory() ? walk(path.join(d, e.name)) : [path.join(d, e.name)]));
const files = walk(dist);
const rel = (f) => path.relative(dist, f);
const html = files.filter((f) => f.endsWith('.html'));

// 1. CSP hashes
const csp = JSON.parse(fs.readFileSync(firebaseJson, 'utf8')).hosting.headers
  .flatMap((h) => h.headers).find((h) => h.key === 'Content-Security-Policy')?.value ?? '';
if (!csp) fail('no Content-Security-Policy in backend/firebase/firebase.json');
const inlineRe = /<script(?![^>]*\bsrc=)([^>]*)>([\s\S]*?)<\/script>/g;
for (const f of html) {
  const s = fs.readFileSync(f, 'utf8');
  for (const [, attrs, body] of s.matchAll(inlineRe)) {
    if (/type="application\/(ld\+)?json"/.test(attrs)) continue; // data blocks are not executed
    const h = `'sha256-${crypto.createHash('sha256').update(body).digest('base64')}'`;
    if (!csp.includes(h)) fail(`${rel(f)}: inline script ${h} is not in the CSP (pin it in firebase.json or make it external)`);
  }
}

// 2. Privacy baseline
const banned = [/fonts\.googleapis\.com/, /fonts\.gstatic\.com/, /googletagmanager\.com/, /google-analytics\.com/, /gtag\(/, /recaptcha/i];
for (const f of files.filter((x) => /\.(html|js|css)$/.test(x))) {
  const s = fs.readFileSync(f, 'utf8');
  for (const re of banned) if (re.test(s)) fail(`${rel(f)}: matches banned pattern ${re}`);
  if (/\.cookie\s*=/.test(s) && !rel(f).startsWith('_astro/')) fail(`${rel(f)}: writes document.cookie`);
}
// Our own modules must never write cookies (vendor SDK chunks are checked by the browser test instead).
for (const f of files.filter((x) => x.endsWith('.js'))) {
  const s = fs.readFileSync(f, 'utf8');
  if (/document\.cookie\s*=/.test(s)) fail(`${rel(f)}: writes document.cookie`);
}

// 3. Page basics
for (const f of html) {
  const s = fs.readFileSync(f, 'utf8');
  const h1 = (s.match(/<h1[\s>]/g) || []).length;
  if (h1 !== 1) fail(`${rel(f)}: ${h1} <h1> elements (expected 1)`);
  if (!/<title>[^<]+<\/title>/.test(s)) fail(`${rel(f)}: missing <title>`);
  if (!/<meta name="description" content="[^"]+"/.test(s)) fail(`${rel(f)}: missing meta description`);
  if (!/<link rel="canonical" href="https:\/\/driveperf\.com[^"]*"/.test(s)) fail(`${rel(f)}: missing canonical`);
}

// 4. Routes and files
const expected = ['index.html', 'waitlist/confirmed.html', 'privacy.html', 'terms.html', 'support.html', 'how-it-records.html',
  'delete-account.html', 'ride-along.html', 'email-preferences.html', 'press.html', '404.html', 'sitemap.xml', 'robots.txt',
  '.well-known/apple-app-site-association', '.well-known/assetlinks.json', 'manifest.webmanifest', 'favicon.svg', 'favicon.ico',
  'apple-touch-icon.png', 'og/home-1200x630.png'];
for (const e of expected) if (!fs.existsSync(path.join(dist, e))) fail(`missing dist/${e}`);
for (const j of ['.well-known/apple-app-site-association', '.well-known/assetlinks.json', 'manifest.webmanifest']) {
  try { JSON.parse(fs.readFileSync(path.join(dist, j), 'utf8')); } catch { if (fs.existsSync(path.join(dist, j))) fail(`dist/${j} is not valid JSON`); }
}

// 5. First-load budget: the page, its stylesheets and module scripts plus their static imports
const BUDGET = 500 * 1024;
const size = (p) => { try { return fs.statSync(path.join(dist, p)).size; } catch { return 0; } };
const staticImports = (p, seen) => {
  if (seen.has(p)) return; seen.add(p);
  const src = fs.readFileSync(path.join(dist, p), 'utf8');
  for (const [, dep] of src.matchAll(/(?:^|[;\s}])import\s*(?:[\w${},*\s]+from\s*)?["']([^"']+\.js)["']/g)) {
    staticImports(path.posix.join(path.posix.dirname(p), dep).replace(/^\/+/, ''), seen);
  }
};
const report = [];
for (const f of html) {
  const s = fs.readFileSync(f, 'utf8');
  const css = [...s.matchAll(/<link rel="stylesheet" href="\/([^"]+)"/g)].map((m) => m[1]);
  const js = new Set();
  for (const [, src] of s.matchAll(/<script[^>]*\bsrc="\/([^"]+)"/g)) staticImports(src, js);
  const total = fs.statSync(f).size + css.reduce((a, c) => a + size(c), 0) + [...js].reduce((a, c) => a + size(c), 0);
  report.push(`${rel(f).padEnd(28)} ${(total / 1024).toFixed(0).padStart(4)} KB`);
  if (total > BUDGET) fail(`${rel(f)}: first-load ${(total / 1024).toFixed(0)} KB exceeds the 500 KB budget`);
}

console.log('check-dist: first-load size (HTML + CSS + JS, uncompressed)\n  ' + report.join('\n  '));
if (errors.length) {
  console.error(`check-dist: ${errors.length} problem(s)\n  - ` + errors.join('\n  - '));
  process.exit(1);
}
console.log('check-dist: OK');
