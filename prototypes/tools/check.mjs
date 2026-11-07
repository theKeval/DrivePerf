#!/usr/bin/env node
// Prototype health check (DRIVE-32). Loads every screen × state headlessly on each platform and reports:
//   JS errors (exceptions, console.error, failed local loads) · screens whose render()/mount() throws
//   · registration problems · unregistered screens (placeholders) · manifest states a screen doesn't declare
//   · built screens with an incomplete `spec` · deep links that render a different screen than asked.
// Usage: node prototypes/tools/check.mjs [--platform ios|android] [--wp B] [--screens S-HOME-01,…] [--concurrency 4] [--quiet]
// Exit code 1 when any error is found (placeholders and spec warnings don't fail the run).
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { launch, pool } from './cdp.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const args = process.argv.slice(2);
const opt = (k, d) => { const i = args.indexOf('--' + k); return i < 0 ? d : args[i + 1] && !args[i + 1].startsWith('--') ? args[i + 1] : true; };
const list = (v) => (v && v !== true ? String(v).split(',').map((s) => s.trim()) : null);
const PLATFORMS = list(opt('platform')) || ['ios', 'android'];
const WPS = (list(opt('wp')) || null)?.map((w) => w.toUpperCase()) || null, SCREENS = list(opt('screens'));
const CONC = +opt('concurrency', 4) || 4, QUIET = !!opt('quiet', false);
const SIZE = { ios: [402, 874], android: [412, 915] };
const SPEC_KEYS = ['purpose', 'layout', 'components', 'interactions', 'motion', 'a11y', 'platformDiffs', 'analytics', 'copy'];

const sb = { window: {} };
vm.runInNewContext(readFileSync(join(ROOT, 'prototypes/app/core/manifest.js'), 'utf8'), sb);
const M = sb.window.DP_MANIFEST;
const url = (p, hash, n) => `${pathToFileURL(join(ROOT, 'prototypes', p, 'index.html')).href}?bare=1&shot=1&r=${n}#${hash}`;

const browser = await launch();
const report = { errors: [], warnings: [], placeholders: {}, checked: 0 };
try {
  for (const p of PLATFORMS) {
    const page = await browser.newPage();
    await page.viewport(...SIZE[p], 1);
    await page.goto(url(p, 'S-HOME-01', 0));
    if (!(await page.waitFor('window.DP_READY === true'))) { report.errors.push(`${p}: the app did not boot (${page.errors.map((e) => e.text).join(' | ')})`); continue; }
    for (const e of page.errors) report.errors.push(`${p} boot: ${e.text}`);
    const info = await page.eval(`JSON.stringify({ problems: DP.problems, defs: Object.values(DP.screens).map((d) => ({ id: d.id, platforms: d.platforms, states: d.states.map((s) => s.id), spec: Object.keys(d.spec || {}).filter((k) => { const v = d.spec[k]; return v && (typeof v !== 'object' || Object.keys(v).length); }) })) })`).then(JSON.parse);
    await page.close();
    for (const pr of info.problems) report.errors.push(`${p}: ${pr}`);
    const defs = Object.fromEntries(info.defs.map((d) => [d.id, d]));
    let screens = M.screens.filter((s) => s.platforms.includes(p));
    if (WPS) screens = screens.filter((s) => WPS.includes(s.wp));
    if (SCREENS) screens = screens.filter((s) => SCREENS.includes(s.id));
    report.placeholders[p] = screens.filter((s) => !defs[s.id] || !defs[s.id].platforms.includes(p)).map((s) => s.id);
    const tasks = [];
    for (const s of screens) {
      const d = defs[s.id] && defs[s.id].platforms.includes(p) ? defs[s.id] : null;
      if (d) {
        const missing = s.states.map((x) => x.id).filter((x) => !d.states.includes(x));
        if (missing.length) report.errors.push(`${p} ${s.id}: missing PRD states ${missing.join(', ')} (declare them in states[] or map them with stateAliases)`);
        const absent = SPEC_KEYS.filter((k) => !d.spec.includes(k));
        if (absent.length) report.warnings.push(`${p} ${s.id}: spec is missing ${absent.join(', ')}`);
      }
      for (const st of d ? d.states : ['default']) {
        tasks.push({ p, id: s.id, state: st, built: !!d });
        if (d && st === 'default') { tasks.push({ p, id: s.id, state: st, built: true, q: 'theme=light' }); tasks.push({ p, id: s.id, state: st, built: true, q: 'text=xl' }); }
      }
    }
    let n = 0;
    await pool(browser, tasks, CONC, async (pg, t) => {
      await pg.viewport(...SIZE[p], 1);
      const hash = `${t.id}${t.state === 'default' ? '' : '/' + t.state}${t.q ? '?' + t.q : ''}`;
      await pg.goto(url(p, hash, ++n));
      const ok = await pg.waitFor('window.DP_READY === true', { timeout: 10000 });
      report.checked++;
      const tag = `${p} ${hash}`;
      if (!ok) { report.errors.push(`${tag}: not ready after 10 s ${pg.errors.map((e) => e.text).join(' | ')}`); return; }
      const r = await pg.eval(`JSON.stringify({ errs: DP.router.state.errors, top: DP.router.stack[DP.router.stack.length - 1] && { id: DP.router.stack[DP.router.stack.length - 1].id, state: DP.router.stack[DP.router.stack.length - 1].state }, gate: DP.session.gateReason || null, overflow: (() => { const l = document.querySelector('.dp-layer:last-of-type [data-scroll]'); return l ? l.scrollWidth - l.clientWidth : 0; })() })`).then(JSON.parse);
      for (const e of r.errs) report.errors.push(`${tag}: ${e.phase} threw — ${e.message}`);
      for (const e of pg.errors) report.errors.push(`${tag}: ${e.kind} — ${e.text}`);
      if (r.top && r.top.id !== t.id && !r.gate) report.errors.push(`${tag}: rendered ${r.top.id} instead`);
      if (r.top && t.built && r.top.id === t.id && r.top.state !== t.state) report.errors.push(`${tag}: state resolved to '${r.top.state}'`);
      if (t.built && r.overflow > 1) report.warnings.push(`${tag}: horizontal overflow ${r.overflow}px`);
    });
  }
} finally { await browser.close(); }

const uniq = (a) => [...new Set(a)];
report.errors = uniq(report.errors); report.warnings = uniq(report.warnings);
console.log(`Checked ${report.checked} screen/state loads on ${PLATFORMS.join(' + ')}.`);
for (const p of PLATFORMS) if (report.placeholders[p]) console.log(`${p}: ${report.placeholders[p].length} placeholder${report.placeholders[p].length === 1 ? '' : 's'} (not built yet)${QUIET ? '' : report.placeholders[p].length ? ': ' + report.placeholders[p].join(', ') : ''}`);
if (report.warnings.length) { console.log(`\nWarnings (${report.warnings.length}):`); report.warnings.forEach((w) => console.log('  ⚠ ' + w)); }
if (report.errors.length) { console.log(`\nErrors (${report.errors.length}):`); report.errors.forEach((e) => console.log('  ✕ ' + e)); process.exitCode = 1; }
else console.log('\nNo errors.');
