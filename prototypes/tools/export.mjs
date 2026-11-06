#!/usr/bin/env node
// Export Jira design artifacts from the prototype engine (DRIVE-32). No npm deps; headless Chrome via CDP.
// For every Jira story in the manifest × each platform it applies to, writes docs/design/<KEY>/<ios|android>/:
//   screens/<S-ID>.png (default, dark) · screens/<S-ID>--light.png · screens/<S-ID>--<state>.png (dark) · screens/<S-ID>--xl.png (XL variant)
//   mockup.html (contact sheet: live iframes into the prototype, bare mode) · mockup.png · design-spec.md · assets/*.svg
// Usage: node prototypes/tools/export.mjs [--platform ios|android] [--wp A..F] [--story DRIVE-85[,DRIVE-90]]
//          [--screens S-HOME-01,S-TRP-01] [--no-png] [--no-mockup-png] [--all-themes] [--concurrency 4] [--out docs/design]
import { readFileSync, writeFileSync, mkdirSync, existsSync, readdirSync, rmSync, copyFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { tmpdir } from 'node:os';
import vm from 'node:vm';
import { launch, pool } from './cdp.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const args = process.argv.slice(2);
const opt = (k, d) => { const i = args.indexOf('--' + k); return i < 0 ? d : args[i + 1] && !args[i + 1].startsWith('--') ? args[i + 1] : true; };
const list = (v) => (v && v !== true ? String(v).split(',').map((s) => s.trim()).filter(Boolean) : null);
const PLATFORMS = list(opt('platform')) || ['ios', 'android'];
const WPS = list(opt('wp'))?.map((w) => w.toUpperCase()) || null;
const STORIES = list(opt('story'));
const SCREENS = list(opt('screens'));
const NO_PNG = !!opt('no-png', false);
const NO_MOCK_PNG = NO_PNG || !!opt('no-mockup-png', false);
const ALL_THEMES = !!opt('all-themes', false);
const CONC = +opt('concurrency', 4) || 4;
const OUT = join(ROOT, opt('out', 'docs/design'));
const DEV = { ios: { w: 402, h: 874, scale: 3, label: 'iOS 26/27 · Liquid Glass · iPhone 17 Pro (402×874 pt @3x = 1206×2622)' }, android: { w: 412, h: 915, scale: 2.625, label: 'Android 16 · Material 3 Expressive · 412×915 dp @2.625 (≈ 1081×2402)' } };
const t0 = Date.now();

// ---------- manifest ----------
const sandbox = { window: {} };
vm.runInNewContext(readFileSync(join(ROOT, 'prototypes/app/core/manifest.js'), 'utf8'), sandbox);
const M = sandbox.window.DP_MANIFEST;
const META = Object.fromEntries(M.screens.map((s) => [s.id, s]));
const appUrl = (p, hash, { bare = true, shot = true, n = 0 } = {}) => `${pathToFileURL(join(ROOT, 'prototypes', p, 'index.html')).href}?${bare ? 'bare=1&' : ''}${shot ? 'shot=1&' : ''}r=${n}#${hash}`;

// ---------- story selection ----------
let stories = Object.values(M.stories);
if (STORIES) stories = stories.filter((s) => STORIES.includes(s.key));
const focus = new Set(SCREENS || (WPS ? M.screens.filter((s) => WPS.includes(s.wp)).map((s) => s.id) : M.screens.map((s) => s.id)));
stories = stories.filter((s) => s.screens.some((id) => focus.has(id)));
if (!stories.length) { console.error('No stories match the filters.'); process.exit(1); }

const browser = await launch();
try {
  // ---------- read registered screen defs + tokens from the running engine, per platform ----------
  const ENGINE = {};
  {
    const page = await browser.newPage();
    for (const p of PLATFORMS) {
      await page.viewport(DEV[p].w, DEV[p].h, 1);
      await page.goto(appUrl(p, 'S-HOME-01'));
      await page.waitFor('window.DP_READY === true');
      ENGINE[p] = await page.eval(`(() => {
        const defs = Object.values(DP.screens).map((d) => ({ id: d.id, name: d.name, wp: d.wp, platforms: d.platforms, presentation: DP.presOf(d.id, '${p}'), rawPresentation: d.presentation, tab: d.tab, detent: typeof d.detent === 'string' ? d.detent : 'auto', states: d.states, stateParams: d.stateParams, variants: d.variants, spec: d.spec }));
        const icons = {}; for (const d of defs) for (const n of (d.spec && d.spec.icons) || []) icons[n] = DP_UI.icon(n, 24);
        const dev = document.querySelector('.dp-device');
        const NAMES = ['--app-bg','--card-bg','--dp-bg-grouped','--dp-surface','--dp-surface-2','--dp-surface-3','--dp-fill','--dp-fill-strong','--dp-text','--dp-text-2','--dp-text-3','--dp-separator','--dp-accent','--dp-accent-text','--dp-on-accent','--dp-accent-container','--dp-record','--dp-positive','--dp-change-down','--dp-warning','--dp-critical','--dp-glass-fill','--dp-glass-fill-strong','--dp-glass-stroke','--md-sys-color-primary','--md-sys-color-on-primary','--md-sys-color-primary-container','--md-sys-color-on-primary-container','--md-sys-color-secondary-container','--md-sys-color-surface','--md-sys-color-surface-container','--md-sys-color-surface-container-high','--md-sys-color-on-surface-variant','--dp-data-route','--dp-data-event-brake','--dp-data-event-accel','--dp-data-event-corner','--dp-data-quality-good','--dp-data-speed','--dp-data-long-g','--dp-data-lat-g'];
        const TYPE = ['--dp-font-screen-title','--dp-font-sheet-title','--dp-font-section-title','--dp-font-card-title','--dp-font-body','--dp-font-body-secondary','--dp-font-meta','--dp-font-caption','--dp-font-button','--dp-num-hero','--dp-num-xl','--dp-num-l','--dp-num-m','--dp-num-s','--dp-num-xs'];
        const LAYOUT = ['--dp-screen-margin','--dp-radius-card','--dp-radius-card-hero','--dp-radius-sheet','--dp-radius-chip','--dp-radius-button','--dp-layout-list-row-min','--dp-layout-touch-min','--safe-top','--safe-bottom'];
        const read = (names) => { const cs = getComputedStyle(dev); return Object.fromEntries(names.map((n) => [n, cs.getPropertyValue(n).trim()])); };
        const dark = read(NAMES); dev.dataset.theme = 'light'; const light = read(NAMES); dev.dataset.theme = 'dark';
        return { defs, icons, tokens: { dark, light, type: read(TYPE), layout: read(LAYOUT) }, uiTokens: DP_UI.TOKENS, problems: DP.problems };
      })()`);
      ENGINE[p].byId = Object.fromEntries(ENGINE[p].defs.map((d) => [d.id, d]));
    }
    await page.close();
  }
  const built = (p, id) => { const d = ENGINE[p].byId[id]; return d && d.platforms.includes(p) ? d : null; };
  const statesOf = (p, id) => (built(p, id) ? built(p, id).states : META[id].states);
  const variantsOf = (p, id) => (built(p, id) ? built(p, id).variants : META[id].variants) || [];

  // ---------- shot list (dedupe across stories) ----------
  const shotKey = (p, id, state, theme, xl) => `${p}|${id}|${state}|${theme}|${xl ? 'xl' : ''}`;
  const fileOf = (id, state, theme, xl) => `${id}${state !== 'default' ? '--' + state : ''}${xl ? '--xl' : ''}${theme === 'light' && (state === 'default' && !xl ? true : ALL_THEMES) ? '--light' : ''}.png`;
  const shots = new Map();
  const placeholders = { ios: new Set(), android: new Set() };
  for (const st of stories) for (const p of PLATFORMS) {
    if (!st.platforms.includes(p)) continue;
    for (const id of st.screens) {
      if (!META[id].platforms.includes(p)) continue;
      if (!built(p, id)) { placeholders[p].add(id); continue; }
      if (!focus.has(id)) continue;
      const add = (state, theme, xl) => shots.set(shotKey(p, id, state, theme, xl), { p, id, state, theme, xl, file: fileOf(id, state, theme, xl) });
      for (const s of statesOf(p, id)) { add(s.id, 'dark', false); if (ALL_THEMES) add(s.id, 'light', false); }
      add('default', 'light', false);
      if (variantsOf(p, id).includes('xl')) add('default', 'dark', true);
    }
  }

  // ---------- render PNGs into a cache ----------
  const CACHE = join(tmpdir(), 'dp-export-cache');
  const renderErrors = [];
  if (!NO_PNG && shots.size) {
    mkdirSync(CACHE, { recursive: true });
    let n = 0, done = 0;
    const tasks = [...shots.values()];
    process.stdout.write(`Rendering ${tasks.length} screen PNGs (${PLATFORMS.join(', ')}) with ${CONC} tabs…\n`);
    await pool(browser, tasks, CONC, async (page, t) => {
      const d = DEV[t.p];
      await page.viewport(d.w, d.h, d.scale);
      const q = new URLSearchParams(); if (t.theme === 'light') q.set('theme', 'light'); if (t.xl) q.set('text', 'xl');
      const hash = `${t.id}${t.state !== 'default' ? '/' + t.state : ''}${q.toString() ? '?' + q : ''}`;
      await page.goto(appUrl(t.p, hash, { n: ++n }));
      const ok = await page.waitFor('window.DP_READY === true', { timeout: 12000 });
      await page.eval('document.fonts.ready.then(() => new Promise((r) => setTimeout(r, 60)))', { timeout: 4000 });
      await new Promise((r) => setTimeout(r, 120));
      const errs = await page.eval('JSON.stringify(DP.router.state.errors)').catch(() => '[]');
      if (!ok || JSON.parse(errs).length || page.errors.length) renderErrors.push({ ...t, ready: ok, errors: [...JSON.parse(errs).map((e) => e.message), ...page.errors.map((e) => e.text)] });
      const dir = join(CACHE, t.p); mkdirSync(dir, { recursive: true });
      writeFileSync(join(dir, t.file), await page.screenshot());
      done++; if (done % 10 === 0 || done === tasks.length) process.stdout.write(`  ${done}/${tasks.length}\r`);
    });
    process.stdout.write('\n');
  }

  // ---------- write story folders ----------
  const written = [];
  const esc = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const md = (s) => String(s ?? '').replace(/\|/g, '\\|').replace(/\n/g, ' ');
  for (const st of stories) for (const p of PLATFORMS) {
    if (!st.platforms.includes(p)) continue;
    const ids = st.screens.filter((id) => META[id].platforms.includes(p));
    if (!ids.length) continue;
    const dir = join(OUT, st.key, p), sdir = join(dir, 'screens'), adir = join(dir, 'assets');
    mkdirSync(sdir, { recursive: true }); mkdirSync(adir, { recursive: true });
    // PNGs
    if (!NO_PNG) for (const id of ids) {
      if (!built(p, id) || !focus.has(id)) continue;
      for (const f of readdirSync(sdir)) if (f === `${id}.png` || f.startsWith(`${id}--`)) rmSync(join(sdir, f));
      for (const s of shots.values()) if (s.p === p && s.id === id && existsSync(join(CACHE, p, s.file))) copyFileSync(join(CACHE, p, s.file), join(sdir, s.file));
    }
    // assets
    const icons = new Set(), assets = new Set();
    for (const id of ids) { const d = built(p, id); if (!d) continue; ((d.spec && d.spec.icons) || []).forEach((n) => icons.add(n)); ((d.spec && d.spec.assets) || []).forEach((a) => assets.add(a)); }
    for (const n of icons) if (ENGINE[p].icons[n]) writeFileSync(join(adir, `icon-${n}.svg`), ENGINE[p].icons[n].replace('aria-hidden="true"', '').replace(/currentColor/g, '#5BE7C4') + '\n');
    for (const a of assets) { const src = join(ROOT, 'docs/design/system/assets', a); if (existsSync(src)) copyFileSync(src, join(adir, a)); }
    if (p === 'android' && ids.some((id) => /^S-(LIVE|NTF)/.test(id))) copyFileSync(join(ROOT, 'docs/design/system/assets/icon-android-notification.svg'), join(adir, 'icon-android-notification.svg'));
    const rel = relative(dir, join(ROOT, 'prototypes', p, 'index.html')).split('\\').join('/');
    // mockup.html
    const tiles = ids.filter((id) => built(p, id)).map((id) => {
      const d = built(p, id), m = META[id];
      const sts = d ? statesOf(p, id) : [{ id: 'default', label: 'Not built yet' }];
      const extra = d ? [{ id: 'default', label: 'Light theme', theme: 'light' }, ...(variantsOf(p, id).includes('xl') ? [{ id: 'default', label: 'Largest text (XL)', xl: true }] : [])] : [];
      const frames = [...sts, ...extra].map((s) => {
        const q = new URLSearchParams(); if (s.theme) q.set('theme', s.theme); if (s.xl) q.set('text', 'xl');
        const hash = `${id}${s.id !== 'default' ? '/' + s.id : ''}${q.toString() ? '?' + q : ''}`;
        const png = d ? `screens/${fileOf(id, s.id, s.theme || 'dark', !!s.xl)}` : '';
        return `<figure><div class="fr"><iframe data-src="${rel}?bare=1&amp;SHOT#${esc(hash)}" title="${esc(id)} ${esc(s.id)}" loading="eager"></iframe></div><figcaption><b>${esc(s.theme ? 'default · light' : s.xl ? 'default · XL text' : s.id)}</b><span>${esc(s.label)}</span><span><a href="${rel}#${esc(hash)}" target="_blank">open interactive ↗</a>${png ? ` · <a href="${png}">PNG</a>` : ''}</span></figcaption></figure>`;
      }).join('');
      return `<section><h2><code>${id}</code> ${esc(d ? d.name : m.name)} <em>${esc(d ? d.presentation : m.pres)} · ${esc(m.priority)} · WP-${esc(m.wp)}${d ? '' : ' · NOT BUILT YET'}</em></h2><div class="grid">${frames}</div></section>`;
    }).join('');
    const todo = ids.filter((id) => !built(p, id));
    const todoHtml = todo.length ? `<section><h2>Not built yet <em>${todo.length} screen${todo.length > 1 ? 's' : ''} in this story still render the engine placeholder</em></h2><div class="grid">${todo.map((id) => `<figure><div class="fr"><iframe data-src="${rel}?bare=1&amp;SHOT#${id}" title="${id}" loading="eager"></iframe></div><figcaption><b>${id}</b><span>${esc(META[id].name)} · WP-${META[id].wp} · ${esc(META[id].priority)}</span><a href="${rel}#${id}" target="_blank">open interactive ↗</a></figcaption></figure>`).join('')}</div></section>` : '';
    const title = `${st.key} · ${st.feature || ''} ${st.title || ''} — ${p === 'ios' ? 'iOS' : 'Android'}`;
    writeFileSync(join(dir, 'mockup.html'), `<!doctype html>
<html lang="en-CA"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${esc(title)}</title>
<!-- GENERATED by prototypes/tools/export.mjs — contact sheet of every screen + state in this story. Frames are live iframes into the prototype (bare mode). -->
<style>
  body { margin: 0; background: #05070A; color: #E4E7EC; font: 400 13px/1.45 -apple-system, BlinkMacSystemFont, system-ui, sans-serif; -webkit-font-smoothing: antialiased; }
  header { padding: 28px 36px 8px; } header h1 { margin: 0 0 4px; font: 650 26px/1.2 system-ui; color: #F2F4F7; } header p { margin: 0; color: #A3AAB6; } header a { color: #5BE7C4; }
  section { padding: 18px 36px 8px; } h2 { font: 600 16px/1.3 system-ui; color: #F2F4F7; margin: 10px 0 14px; display: flex; gap: 10px; align-items: baseline; } h2 code { color: #5BE7C4; font: 600 13px ui-monospace, Menlo, monospace; } h2 em { font-style: normal; color: #7A8392; font-size: 12px; font-weight: 500; }
  .grid { display: flex; flex-wrap: wrap; gap: 22px 18px; }
  figure { margin: 0; width: ${Math.round(DEV[p].w * 0.5)}px; } .fr { width: ${Math.round(DEV[p].w * 0.5)}px; height: ${Math.round(DEV[p].h * 0.5)}px; overflow: hidden; border-radius: ${p === 'ios' ? 28 : 22}px; box-shadow: 0 0 0 1px #ffffff1a, 0 12px 30px -12px #000; background: #0b0f15; }
  iframe { width: ${DEV[p].w}px; height: ${DEV[p].h}px; border: 0; transform: scale(.5); transform-origin: 0 0; display: block; pointer-events: none; }
  figcaption { margin-top: 8px; display: grid; gap: 1px; font-size: 11.5px; color: #7A8392; } figcaption b { color: #E4E7EC; font: 600 12px ui-monospace, Menlo, monospace; } figcaption a { color: #5BE7C4; text-decoration: none; }
  footer { padding: 24px 36px 40px; color: #7A8392; font-size: 12px; }
</style></head><body>
<header><h1>${esc(title)}</h1><p>${esc(DEV[p].label)} · ${ids.length} screen${ids.length > 1 ? 's' : ''} · <a href="design-spec.md">design-spec.md</a> · <a href="${rel}#${ids[0]}" target="_blank">open the interactive prototype ↗</a> · Jira <a href="https://thekeval.atlassian.net/browse/${st.key}">${st.key}</a></p></header>
${tiles}${todoHtml}
<footer>Generated ${new Date().toISOString().slice(0, 10)} by prototypes/tools/export.mjs from the shared prototype engine (prototypes/app). Map geometry © OpenStreetMap contributors.</footer>
<script>
  (function () {
    var shot = /[?&]shot=1/.test(location.search), frames = document.querySelectorAll('iframe'), seen = [];
    frames.forEach(function (f) { f.src = f.dataset.src.replace('SHOT', shot ? 'shot=1' : 'hap=0'); });
    window.addEventListener('message', function (e) { if (e.data && e.data.dp === 'ready' && seen.indexOf(e.source) < 0) { seen.push(e.source); if (seen.length >= frames.length) window.MOCK_READY = true; } });
    setTimeout(function () { window.MOCK_READY = true; }, 20000);
  })();
</script>
</body></html>
`);
    // design-spec.md
    writeFileSync(join(dir, 'design-spec.md'), specMd({ st, p, ids, rel, built, statesOf, variantsOf, fileOf, engine: ENGINE[p], md, sdir }));
    written.push({ key: st.key, p, dir, ids });
  }

  // ---------- mockup.png ----------
  if (!NO_MOCK_PNG && written.length) {
    process.stdout.write(`Shooting ${written.length} contact sheets…\n`);
    let done = 0;
    await pool(browser, written, Math.min(CONC, 3), async (page, w) => {
      await page.viewport(1440, 900, 1);
      await page.goto(pathToFileURL(join(w.dir, 'mockup.html')).href + '?shot=1', { timeout: 30000 });
      await page.waitFor('window.MOCK_READY === true', { timeout: 25000, interval: 150 });
      await new Promise((r) => setTimeout(r, 400));
      const h = await page.eval('document.documentElement.scrollHeight');
      await page.viewport(1440, Math.min(h, 16000), h > 7000 ? 1 : 1.5);
      await new Promise((r) => setTimeout(r, 250));
      writeFileSync(join(w.dir, 'mockup.png'), await page.screenshot({ fullPage: true }));
      done++; process.stdout.write(`  ${done}/${written.length}\r`);
    });
    process.stdout.write('\n');
  }

  // ---------- summary ----------
  const byKey = {};
  for (const w of written) (byKey[w.key] ||= []).push(w.p);
  console.log(`\nWrote ${written.length} story folders under ${relative(ROOT, OUT)}/ (${Object.keys(byKey).length} stories): ${shots.size && !NO_PNG ? shots.size + ' screen PNGs rendered' : 'no PNGs (--no-png)'} · ${((Date.now() - t0) / 1000).toFixed(1)} s`);
  for (const [k, ps] of Object.entries(byKey)) console.log(`  ${k.padEnd(10)} ${ps.join(' + ').padEnd(14)} ${M.stories[k].feature || ''} ${M.stories[k].title || ''}`);
  for (const p of PLATFORMS) if (placeholders[p].size) console.log(`Placeholders on ${p} (not built yet, ${placeholders[p].size}): ${[...placeholders[p]].sort().join(', ')}`);
  for (const p of PLATFORMS) if (ENGINE[p].problems.length) console.log(`Registration problems (${p}):\n  ${ENGINE[p].problems.join('\n  ')}`);
  if (renderErrors.length) { console.log(`\n${renderErrors.length} render(s) reported errors:`); for (const e of renderErrors.slice(0, 30)) console.log(`  ${e.p} ${e.id}/${e.state} ${e.theme}${e.xl ? ' xl' : ''}: ${e.ready ? '' : 'not ready · '}${e.errors.join(' | ')}`); process.exitCode = 1; }
} finally {
  await browser.close();
}

// ======================= design-spec.md =======================
function specMd({ st, p, ids, rel, built, statesOf, variantsOf, fileOf, engine, md, sdir }) {
  const P = p === 'ios' ? 'iOS' : 'Android', other = p === 'ios' ? 'android' : 'ios';
  const L = [];
  const today = new Date().toISOString().slice(0, 10);
  L.push(`# ${st.key} · ${st.feature || ''} ${st.title || ''} — ${P} design spec`, '');
  L.push(`> Generated by \`prototypes/tools/export.mjs\` on ${today} from the shared prototype engine. **Do not edit by hand:** change the screen's \`spec\` in \`prototypes/app/screens/wp-*.js\` (see \`prototypes/app/AUTHORING.md\`) and re-export.`, '');
  L.push(`- **Jira story:** [${st.key}](https://thekeval.atlassian.net/browse/${st.key}) · ${st.feature || '—'} · ${st.priority || '—'}`);
  L.push(`- **Platform:** ${DEV[p].label}`);
  L.push(`- **Interactive prototype:** [${rel}#${ids[0]}](${rel}#${ids[0]}) (catalog panel: every screen and state; deep links \`#S-ID/state?trip=…&ent=…&theme=light&text=xl&rm=1\`)`);
  L.push(`- **Contact sheet:** [mockup.html](mockup.html) · ![mockup](mockup.png)`);
  L.push(`- **Design system (source of truth for values):** [brand](../../system/brand.md) · [components](../../system/components.md) · [motion](../../system/motion.md) · [accessibility](../../system/accessibility.md) · [screen map](../../system/screen-map.md) · [fixtures](../../system/fixtures.md) · tokens \`prototypes/shared/tokens.css\``, '');
  L.push('## Screens in this story', '', '| Screen | Name | Presentation | Priority | States | Status |', '|---|---|---|---|---|---|');
  for (const id of ids) { const d = built(p, id), m = META[id]; L.push(`| \`${id}\` | ${md(d ? d.name : m.name)} | ${md(m.presentation || m.pres)} | ${m.priority} | ${statesOf(p, id).length} | ${d ? 'Prototyped' : '**Not built yet** (placeholder)'} |`); }
  L.push('');
  // tokens
  const tk = engine.tokens;
  L.push('## Tokens used on this platform', '', `Values resolved from \`prototypes/shared/tokens.css\` inside the ${P} device (\`[data-platform="${p}"]\`). Use the token names in code; the hex values are for reference.`, '');
  L.push('| Token | Dark | Light |', '|---|---|---|');
  for (const k of Object.keys(tk.dark)) { if (p === 'ios' && k.startsWith('--md-sys')) continue; if (tk.dark[k] || tk.light[k]) L.push(`| \`${k}\` | \`${tk.dark[k] || '—'}\` | \`${tk.light[k] || '—'}\` |`); }
  L.push('', '| Type / layout token | Value |', '|---|---|');
  for (const [k, v] of Object.entries({ ...tk.type, ...tk.layout })) L.push(`| \`${k}\` | \`${md(v.replace(/calc\(([\d.]+)px \* (?:var\(--dp-(?:text|num)-scale\)|1)\)/g, '$1px'))}\` |`);
  L.push('', 'Text sizes scale with `--dp-text-scale` (XL = 2.0×) and numerals with `--dp-num-scale` (XL = 1.3×). DP Numerals: ' + (p === 'ios' ? 'SF Pro Expanded Semibold, tabular.' : 'Google Sans Flex wdth 120 wght 600, tabular.'), '');
  for (const id of ids) {
    const d = built(p, id), m = META[id];
    L.push('---', '', `## ${id} · ${d ? d.name : m.name}`, '');
    if (!d) { L.push(`**Not built yet.** WP-${m.wp} owns this screen. PRD states: ${m.statesRaw}. Presentation: ${m.presentation}. Entry points: ${m.entry}. Leads to: ${m.leadsTo}.`, ''); continue; }
    const s = d.spec || {};
    const png = `screens/${fileOf(id, 'default', 'dark', false)}`;
    if (existsSync(join(sdir, fileOf(id, 'default', 'dark', false)))) L.push(`<img src="${png}" width="300" alt="${id} default, dark"> <img src="screens/${fileOf(id, 'default', 'light', false)}" width="300" alt="${id} default, light">`, '');
    L.push(`- **Presentation:** ${d.presentation}${d.rawPresentation && typeof d.rawPresentation === 'object' ? ` (iOS ${d.rawPresentation.ios}, Android ${d.rawPresentation.android})` : ''}${d.tab ? ` · tab: ${d.tab}` : ''}${d.presentation === 'sheet' ? ` · detent: ${d.detent}` : ''} — screen map: “${md(m.presentation)}”`);
    L.push(`- **Entry points:** ${md(m.entry)}`, `- **Leads to:** ${md(m.leadsTo)}`, `- **PRD features:** ${m.features.join(', ')} · **Priority:** ${m.priority} · **WP:** ${m.wp}`, `- **Interactive:** [${id}](${rel}#${id})`, '');
    if (s.purpose) L.push(`**Purpose.** ${s.purpose}`, '');
    L.push('### States', '', '| State | PRD label | Fixture params | Screenshot |', '|---|---|---|---|');
    for (const x of statesOf(p, id)) { const f = fileOf(id, x.id, 'dark', false); const sp = d.stateParams && d.stateParams[x.id]; L.push(`| \`${x.id}\` | ${md(x.label)} | ${sp ? Object.entries(sp).map(([k, v]) => `\`${k}=${v}\``).join(' ') : '—'} | ${existsSync(join(sdir, f)) ? `[${f}](screens/${f})` : '—'} · [open](${rel}#${id}${x.id === 'default' ? '' : '/' + x.id}) |`); }
    if (existsSync(join(sdir, fileOf(id, 'default', 'light', false)))) L.push(`| default · light | Light theme | \`theme=light\` | [${fileOf(id, 'default', 'light', false)}](screens/${fileOf(id, 'default', 'light', false)}) |`);
    if (variantsOf(p, id).includes('xl') && existsSync(join(sdir, fileOf(id, 'default', 'dark', true)))) L.push(`| default · XL | Largest text | \`text=xl\` | [${fileOf(id, 'default', 'dark', true)}](screens/${fileOf(id, 'default', 'dark', true)}) |`);
    L.push('', 'Every state also renders in light/dark (`theme=light`), XL text (`text=xl`) and reduced motion (`rm=1`).', '');
    const sec = (title, arr) => { if (arr && arr.length) { L.push(`### ${title}`, ''); (Array.isArray(arr) ? arr : [arr]).forEach((x) => L.push(`- ${x}`)); L.push(''); } };
    sec('Layout', s.layout);
    if (s.components && s.components.length) {
      L.push('### Components & tokens', '', '| Component | Tokens / spec |', '|---|---|');
      for (const c of s.components) L.push(`| \`${c}\` | ${md((engine.uiTokens[c] || ['see components.md']).join(' · '))} |`);
      L.push('');
    }
    sec('Interactions & navigation', s.interactions);
    sec('Motion', s.motion);
    sec('Accessibility', s.a11y);
    if (s.platformDiffs) { L.push(`### Platform notes`, '', `- **${P} (this spec):** ${s.platformDiffs[p] || '—'}`, `- *${other === 'ios' ? 'iOS' : 'Android'} (for reference):* ${s.platformDiffs[other] || '—'}`, ''); }
    sec('Analytics', s.analytics && s.analytics.map((a) => `\`${a}\``));
    if (s.copy && Object.keys(s.copy).length) { L.push('### Copy', '', '| Key | Text |', '|---|---|'); for (const [k, v] of Object.entries(s.copy)) L.push(`| ${md(k)} | ${md(v)} |`); L.push(''); }
    if (s.freePro) L.push('### Free / Pro', '', s.freePro, '');
    if (s.data) L.push('### Data & fixtures', '', s.data, '');
    if ((s.icons && s.icons.length) || (s.assets && s.assets.length)) L.push('### Assets', '', [...(s.icons || []).map((n) => `[icon-${n}.svg](assets/icon-${n}.svg)`), ...(s.assets || []).map((a) => `[${a}](assets/${a})`)].join(' · '), '', 'UI glyphs may be swapped for SF Symbols / Material Symbols Rounded equivalents; event-pin, quality and provenance glyphs must not be (components.md global rules).', '');
    L.push('### Checklist (accessibility.md §9)', '', '- [ ] Light and dark both render, and contrast passes.', '- [ ] The XL text state reflows with no truncation.', '- [ ] The RM state is defined.', '- [ ] Every control has a label; every chart and map has a summary sentence.', '- [ ] Every target ≥ 44 pt / 48 dp (≥ 64 in-drive).', '- [ ] No meaning by colour alone.', '- [ ] Speed is neutral wherever it appears and absent from social or live surfaces.', '- [ ] No streak, leaderboard, speed or “beat your time” language.', '- [ ] Behaviour is defined while a trip is active (D1 gate).', '- [ ] Haptics come from motion.md §6 only.', '');
  }
  L.push('---', '', `Map geometry in the prototype: © OpenStreetMap contributors (ODbL). Speed and G are simulated fixtures (fixtures.md).`, '');
  return L.join('\n');
}
