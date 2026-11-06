#!/usr/bin/env node
// Generates prototypes/app/core/manifest.js (window.DP_MANIFEST) from:
//   docs/product/PRD-v1.md §4            screen inventory (name, epic, features, states, platforms, priority)
//   docs/design/system/screen-map.md §4/§5 work packages, presentation, entry points, leads-to
//   docs/product/jira-map.md             "Screens → stories" + "Stories (PRD features)"
// Usage: node prototypes/tools/gen-manifest.mjs   (no deps; idempotent)
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const read = (p) => readFileSync(join(ROOT, p), 'utf8');
const cells = (line) => line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim());
const ids = (s) => [...new Set((s.match(/S-[A-Z]+-\d+/g) || []))];

// ---------- state names → camelCase ids (screen-map §2.3) ----------
const KEEP = { lock: 'Lock', part: 'Part', err: 'Err', load: 'Load', empty: 'Empty', deny: 'Deny', prev: 'Prev', off: 'Off' };
const VARIANT = { rm: 'rm', xl: 'xl', dark: 'dark', 'screen reader': 'screenReader' };
function camel(text) {
  const words = text.replace(/["“”'’]/g, '').replace(/[^A-Za-z0-9]+/g, ' ').trim().split(/\s+/).filter(Boolean);
  if (!words.length) return '';
  return words.map((w, i) => (i === 0 ? (/^[A-Z0-9]+$/.test(w) && w.length > 1 && !KEEP[w.toLowerCase()] ? w.toLowerCase() : w.charAt(0).toLowerCase() + w.slice(1)) : w.charAt(0).toUpperCase() + w.slice(1))).join('');
}
function parseStates(raw) {
  const states = [], variants = [];
  let defaultLabel = null;
  const parts = raw.replace(/^Variants:\s*/i, '').split(';').map((s) => s.trim()).filter(Boolean);
  for (const label of parts) {
    const bare = label.replace(/\s*\([^)]*\)/g, '').split('→')[0].trim();
    const low = bare.toLowerCase();
    if (VARIANT[low]) { variants.push(VARIANT[low]); continue; }
    if (/^def\b/i.test(bare)) { defaultLabel = label; continue; }
    let id = KEEP[low] || camel(bare);
    if (!id) continue;
    if (/^\d/.test(id)) id = 's' + id;
    if (states.some((s) => s.id === id)) continue;
    states.push({ id, label });
  }
  if (!defaultLabel && states.length) { const first = states.shift(); defaultLabel = first.label; }
  return { states: [{ id: 'default', label: defaultLabel || 'Def' }, ...states], variants };
}

// ---------- PRD §4 ----------
const prd = read('docs/product/PRD-v1.md');
const prdRows = prd.split('\n').filter((l) => /^\| S-[A-Z]+-\d+ \|/.test(l));
const screens = {};
for (const l of prdRows) {
  const [id, name, epic, feats, states, plat, prio] = cells(l);
  if (id.startsWith('S-WEB')) continue;
  const platforms = /ios-only/i.test(plat) ? ['ios'] : /android-only/i.test(plat) ? ['android'] : ['ios', 'android'];
  const st = parseStates(states);
  screens[id] = { id, name, epic, features: feats.split(',').map((s) => s.trim()).filter(Boolean), statesRaw: states, states: st.states, variants: st.variants, platforms, platformNote: plat.replace(/\*\*/g, ''), priority: prio };
}

// ---------- screen-map §4/§5 ----------
const map = read('docs/design/system/screen-map.md');
const PRES = (p) => { const s = p.toLowerCase(); if (s.startsWith('shell') || s.startsWith('root')) return 'root'; if (s.startsWith('push')) return 'push'; if (s.startsWith('sheet') || s.startsWith('popover')) return 'sheet'; if (s.startsWith('full')) return 'full'; if (s.startsWith('dialog')) return 'dialog'; if (s.startsWith('live')) return 'live'; return 'push'; };
const TAB_OF = (id, pres) => {
  const m = /Root \((\w+) tab\)/.exec(pres); if (m) return m[1].toLowerCase();
  if (id === 'S-NAV-01') return 'drives';
  if (/^S-(HOME|HIS|TRP|STY|RPL|ANL|DQ|SCO-0[1-4]|NTF-02)/.test(id)) return 'drives';
  if (/^S-RTE/.test(id)) return 'routes';
  if (/^S-(STA|SCO-0[56]|RCP|WRP)/.test(id)) return 'insights';
  if (/^S-GAR/.test(id)) return 'garage';
  if (/^S-(ACC|SET|PRV|SUB|SHR)/.test(id)) return 'profile';
  return null;
};
let wp = null;
const wpNames = {};
for (const l of map.split('\n')) {
  const h = /^### WP-([A-G]) · (.+?) \(/.exec(l);
  if (h) { wp = h[1]; wpNames[wp] = h[2]; continue; }
  const r = /^\| `(S-[A-Z]+-\d+)` \|/.exec(l);
  if (r && wp && screens[r[1]]) {
    const c = cells(l);
    Object.assign(screens[r[1]], { wp, mapName: c[1], presentation: c[3], pres: PRES(c[3]), entry: c[4], leadsTo: c[5], entryIds: ids(c[4]), leadsToIds: ids(c[5]), tab: TAB_OF(r[1], c[3]) });
  }
}

// ---------- jira-map ----------
const jira = read('docs/product/jira-map.md');
const key = (s) => (s.match(/DRIVE-\d+/g) || []);
const stories = {};
let sec = '';
for (const l of jira.split('\n')) {
  if (l.startsWith('## ')) { sec = l.slice(3).trim(); continue; }
  if (!l.startsWith('| ') || l.startsWith('|---')) continue;
  const c = cells(l);
  if (sec.startsWith('Stories') && /^F-/.test(c[0])) {
    const k = key(c[4])[0];
    if (k) stories[k] = { key: k, feature: c[0], title: c[1], priority: c[2], prdScreens: ids(c[3]), screens: [] };
  }
  if (sec.startsWith('Screens') && /^S-/.test(c[0]) && screens[c[0]]) screens[c[0]].stories = [...new Set(key(c[3]))];
}
for (const s of Object.values(screens)) {
  s.stories ||= [];
  for (const k of s.stories) { stories[k] ||= { key: k, feature: null, title: null, priority: null, prdScreens: [], screens: [] }; if (!stories[k].screens.includes(s.id)) stories[k].screens.push(s.id); }
}
for (const k of Object.keys(stories)) if (!stories[k].screens.length) delete stories[k];
// story platforms = union of its screens' platforms
for (const st of Object.values(stories)) st.platforms = [...new Set(st.screens.flatMap((id) => screens[id].platforms))];

const list = Object.values(screens).map(({ statesRaw, ...s }) => ({ ...s, statesRaw }));
const missingWp = list.filter((s) => !s.wp).map((s) => s.id);
if (missingWp.length) console.warn('No WP in screen-map for:', missingWp.join(', '));
const out = {
  generated: new Date().toISOString().slice(0, 10),
  source: ['docs/product/PRD-v1.md §4', 'docs/design/system/screen-map.md §4–§5', 'docs/product/jira-map.md'],
  wps: Object.fromEntries(Object.entries(wpNames).filter(([k]) => k !== 'G').map(([k, n]) => [k, { name: n, screens: list.filter((s) => s.wp === k).map((s) => s.id) }])),
  screens: list,
  stories,
};
const js = `/* GENERATED by prototypes/tools/gen-manifest.mjs — do not edit by hand. Re-run: node prototypes/tools/gen-manifest.mjs
   ${list.length} app screens · ${Object.keys(stories).length} stories. Sources: ${out.source.join(' · ')} */
window.DP_MANIFEST = ${JSON.stringify(out, null, 1)};
`;
writeFileSync(join(ROOT, 'prototypes/app/core/manifest.js'), js);
console.log(`manifest.js: ${list.length} screens (${list.filter((s) => s.platforms.includes('ios')).length} iOS, ${list.filter((s) => s.platforms.includes('android')).length} Android), ${Object.keys(stories).length} stories, WPs ${Object.keys(out.wps).join('')}`);
