#!/usr/bin/env node
// Generates prototypes/shared/tokens.css from docs/design/system/tokens.json.
// usage: node docs/design/system/tools/build-tokens.mjs
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const T = JSON.parse(readFileSync(resolve(here, '../tokens.json'), 'utf8'));
const OUT = resolve(here, '../../../../prototypes/shared/tokens.css');

const kebab = (s) => String(s).replace(/\./g, '-').replace(/([a-z0-9])([A-Z])/g, '$1-$2').replace(/([a-zA-Z])([0-9])/g, '$1-$2').toLowerCase();
const px = (n) => (typeof n === 'number' ? `${n}px` : n);
const strip = (o) => Object.fromEntries(Object.entries(o).filter(([k]) => !k.startsWith('$')));

// ---------- springs → CSS linear() ----------
function springCurve(zeta, k) {
  const w0 = Math.sqrt(k);
  const x = (t) => {
    if (zeta < 1) {
      const wd = w0 * Math.sqrt(1 - zeta * zeta);
      return 1 - Math.exp(-zeta * w0 * t) * (Math.cos(wd * t) + ((zeta * w0) / wd) * Math.sin(wd * t));
    }
    return 1 - Math.exp(-w0 * t) * (1 + w0 * t);
  };
  // settle: last time |x-1| > 0.002, searched over 6 s
  let settle = 0;
  for (let t = 0; t < 6; t += 0.001) if (Math.abs(x(t) - 1) > 0.002) settle = t;
  settle = Math.max(settle, 0.12);
  const N = 40;
  const pts = [];
  for (let i = 0; i <= N; i++) {
    const v = i === N ? 1 : x((settle * i) / N);
    pts.push(+v.toFixed(4));
  }
  return { css: `linear(${pts.join(', ')})`, ms: Math.round(settle * 1000), overshoot: Math.max(...pts) - 1 };
}
const iosSpring = ({ duration, bounce }) => ({ zeta: 1 - bounce, k: (2 * Math.PI / duration) ** 2 });

// ---------- type helpers ----------
const font = ([size, line, weight], family, scaleVar = '--dp-text-scale') =>
  `${weight} calc(${size}px * var(${scaleVar})) / calc(${line}px * var(${scaleVar})) var(${family})`;

const L = [];
const emit = (s = '') => L.push(s);
const block = (sel, vars) => {
  emit(`${sel} {`);
  for (const [k, v] of vars) emit(`  ${k}: ${v};`);
  emit('}');
  emit();
};

emit('/* DrivePerf tokens.css — GENERATED from docs/design/system/tokens.json by docs/design/system/tools/build-tokens.mjs.');
emit('   Do not edit by hand. Scopes: [data-platform="ios"|"android"] × [data-theme="dark"|"light"] (same element or nested).');
emit('   Extras: [data-text-size="l"|"xl"], [data-reduced-motion="true"], [data-reduced-transparency="true"]. */');
emit("@import url('https://fonts.googleapis.com/css2?family=Google+Sans+Flex:opsz,wdth,wght,ROND@6..144,25..151,1..1000,0..100&family=Roboto+Flex:opsz,wdth,wght@8..144,25..151,100..1000&display=swap');");
emit();

// ---------- :root (platform/theme invariant) ----------
const root = [];
for (const [pal, steps] of Object.entries(T.color.ref)) for (const [s, v] of Object.entries(steps)) root.push([`--dp-ref-${kebab(pal)}-${s}`, v]);
for (const [k, v] of Object.entries(strip(T.color.car))) root.push([`--dp-car-${kebab(k)}`, v]);
for (const [k, v] of Object.entries(T.color.gradient)) root.push([`--dp-gradient-${kebab(k)}`, v]);
for (const [k, v] of Object.entries(T.space)) root.push([`--dp-space-${String(k).replace('.', '_')}`, px(v)]);
for (const [k, v] of Object.entries(T.motion.duration)) root.push([`--dp-dur-${kebab(k)}`, `${v}ms`]);
for (const [k, v] of Object.entries(T.motion.easing)) root.push([`--dp-ease-${kebab(k)}`, v]);
for (const [k, v] of Object.entries(T.motion.stagger)) root.push([`--dp-stagger-${kebab(k)}`, `${v}ms`]);
for (const [k, s] of Object.entries(strip(T.motion.spring.shared))) {
  const c = springCurve(s.dampingRatio, s.stiffness);
  root.push([`--dp-spring-${kebab(k)}`, c.css], [`--dp-spring-${kebab(k)}-duration`, `${c.ms}ms`]);
}
for (const [k, v] of Object.entries(T.zIndex)) root.push([`--dp-z-${kebab(k)}`, v]);
for (const [k, v] of Object.entries(T.opacity)) root.push([`--dp-opacity-${kebab(k)}`, k === 'lockedBlur' ? `${v}px` : v]);
root.push(
  ['--dp-est-dash', T.provenance.estimated.dash],
  ['--dp-est-opacity', T.provenance.estimated.opacity],
  ['--dp-gap-dash', T.provenance.gap.dash],
  ['--dp-hatch-spacing', `${T.provenance.excluded.hatchSpacing}px`],
  ['--dp-hatch-width', `${T.provenance.excluded.hatchWidth}px`],
  ['--dp-text-scale', 1],
  ['--dp-num-scale', 1],
  ['--dp-motion', 1],
  ['--dp-crossfade', `${T.motion.reducedMotion.crossfade}ms`],
);
block(':root', root);

// ---------- platform scopes ----------
function platformVars(p) {
  const v = [];
  const fam = T.type.family[p];
  v.push(
    ['--dp-family-text', fam.text], ['--dp-family-display', fam.display], ['--dp-family-rounded', fam.rounded],
    ['--dp-family-numeric', fam.numeric], ['--dp-family-mono', fam.mono],
    ['--dp-num-stretch', fam.numericStretch], ['--dp-num-variation', fam.numericVariation], ['--dp-num-features', T.type.numeric.features],
  );
  // native type scale
  const scale = strip(T.type[p]);
  const prefix = p === 'ios' ? '--ios-font' : '--md-sys-typescale';
  for (const [name, spec] of Object.entries(scale)) {
    const famVar = (p === 'ios' && spec[0] >= 20) || (p === 'android' && spec[0] >= 22) ? '--dp-family-display' : '--dp-family-text';
    v.push([`${prefix}-${kebab(name)}`, font(spec, famVar)], [`${prefix}-${kebab(name)}-tracking`, `${spec[3]}px`]);
  }
  // shared roles
  for (const [role, map] of Object.entries(strip(T.type.roles))) {
    const spec = scale[map[p]];
    const famVar = spec[0] >= 20 ? '--dp-family-display' : '--dp-family-text';
    v.push([`--dp-font-${kebab(role)}`, font(spec, famVar)], [`--dp-track-${kebab(role)}`, `${spec[3]}px`]);
  }
  // numerals
  for (const name of ['hero', 'xl', 'l', 'm', 's', 'xs']) {
    const spec = T.type.numeric[name];
    v.push([`--dp-num-${name}`, font(spec, '--dp-family-numeric', '--dp-num-scale')], [`--dp-num-${name}-tracking`, `${spec[3]}px`]);
  }
  // radii
  for (const [k, r] of Object.entries(T.radius[p])) if (typeof r === 'number') v.push([`--dp-radius-${kebab(k)}`, px(r)]);
  // layout
  const lay = T.layout[p];
  const flat = (obj, pre) => {
    for (const [k, val] of Object.entries(obj)) {
      if (typeof val === 'object') flat(val, `${pre}-${kebab(k)}`);
      else if (typeof val === 'number') v.push([`${pre}-${kebab(k)}`, val > 0 && val < 1 ? val : px(val)]);
    }
  };
  flat(lay, '--dp-layout');
  v.push(['--dp-screen-margin', px(lay.screenMargin)], ['--dp-touch-min', px(lay.touchMin)], ['--dp-touch-drive', px(lay.touchDrive)]);
  // elevation
  for (const [k, e] of Object.entries(T.elevation[p])) v.push([`--dp-elev-${kebab(k)}`, typeof e === 'string' ? e : e.shadow]);
  // springs
  for (const [k, s] of Object.entries(T.motion.spring[p])) {
    if (k.startsWith('$')) continue;
    const phys = p === 'ios' ? iosSpring(s) : { zeta: s.dampingRatio, k: s.stiffness };
    const c = springCurve(phys.zeta, phys.k);
    v.push([`--dp-spring-${kebab(k)}`, c.css], [`--dp-spring-${kebab(k)}-duration`, `${c.ms}ms`]);
  }
  // glass
  const g = T.glass;
  if (p === 'ios') {
    v.push(
      ['--dp-glass-blur', `blur(${g.regular.blur}px) saturate(${g.regular.saturate}) brightness(${g.regular.brightness})`],
      ['--dp-glass-clear-blur', `blur(${g.clear.blur}px) saturate(${g.clear.saturate}) brightness(${g.clear.brightness})`],
      ['--dp-glass-prominent-blur', `blur(${g.prominent.blur}px) saturate(${g.prominent.saturate})`],
    );
  } else {
    v.push(['--dp-glass-blur', 'none'], ['--dp-glass-clear-blur', 'none'], ['--dp-glass-prominent-blur', 'none']);
  }
  if (p === 'android') {
    for (const [k, r] of Object.entries(T.radius.android)) if (typeof r === 'number') v.push([`--md-sys-shape-corner-${kebab(k)}`, px(r)]);
    for (const [k, s] of Object.entries(T.motion.spring.android)) {
      if (k.startsWith('$')) continue;
      v.push([`--md-sys-motion-spring-${kebab(k)}-damping`, s.dampingRatio], [`--md-sys-motion-spring-${kebab(k)}-stiffness`, s.stiffness]);
    }
  }
  return v;
}
block(':root, [data-platform="ios"]', platformVars('ios'));
block('[data-platform="android"]', platformVars('android'));

// ---------- data colours (theme only) ----------
const dataVars = (theme) => Object.entries(T.color.data[theme]).map(([k, val]) => [`--dp-data-${kebab(k)}`, val]);
block(':root, [data-theme="dark"]', dataVars('dark'));
block('[data-theme="light"]', dataVars('light'));

// ---------- semantic (platform × theme) ----------
const sel = (p, t, extra = []) =>
  [...extra, `[data-platform="${p}"][data-theme="${t}"]`, `[data-platform="${p}"] [data-theme="${t}"]`, `[data-theme="${t}"] [data-platform="${p}"]`].join(',\n');
const semVars = (p, t) => {
  const v = Object.entries(T.color.semantic[p][t]).map(([k, val]) => [`--dp-${kebab(k)}`, val]);
  v.push(['color-scheme', t]);
  if (p === 'android') for (const [k, val] of Object.entries(T.color.m3[t])) v.push([`--md-sys-color-${kebab(k)}`, val]);
  if (p === 'ios') v.push(['--dp-elev-glass-current', t === 'dark' ? 'var(--dp-elev-glass)' : 'var(--dp-elev-glass-light)']);
  return v;
};
block(sel('ios', 'dark', [':root', '[data-platform="ios"]']), semVars('ios', 'dark'));
block(sel('android', 'dark', ['[data-platform="android"]']), semVars('android', 'dark'));
block(sel('ios', 'light', [':root[data-theme="light"]:not([data-platform="android"])']), semVars('ios', 'light'));
block(sel('android', 'light'), semVars('android', 'light'));

// ---------- accessibility scopes ----------
block('[data-text-size="l"]', [['--dp-text-scale', 1.18], ['--dp-num-scale', 1.1]]);
block('[data-text-size="xl"]', [['--dp-text-scale', 2], ['--dp-num-scale', 1.3]]);
emit('@media (prefers-reduced-motion: reduce) { :root { --dp-motion: 0; } }');
block('[data-reduced-motion="true"]', [['--dp-motion', 0]]);
block('[data-reduced-transparency="true"]', [['--dp-glass-fill', 'var(--dp-surface-3)'], ['--dp-glass-fill-strong', 'var(--dp-surface-3)'], ['--dp-glass-blur', 'none'], ['--dp-glass-clear-blur', 'none']]);

// ---------- helper classes ----------
emit(`/* ---- helpers (tiny, shared by both shells) ---- */
.dp-num { font-family: var(--dp-family-numeric); font-variant-numeric: tabular-nums lining-nums; font-feature-settings: var(--dp-num-features); font-stretch: var(--dp-num-stretch); font-variation-settings: var(--dp-num-variation); }
.dp-num .unit, .dp-unit { font-size: 0.5em; font-weight: 500; color: var(--dp-text-2); letter-spacing: 0; margin-left: 0.12em; font-stretch: 100%; }
.dp-est { opacity: var(--dp-est-opacity); }
.dp-est::before { content: "≈\\2009"; }
.dp-glass { background: var(--dp-glass-fill); -webkit-backdrop-filter: var(--dp-glass-blur); backdrop-filter: var(--dp-glass-blur); border: 0.5px solid var(--dp-glass-stroke); box-shadow: var(--dp-elev-glass-current, none); }
.dp-hatch { background-image: repeating-linear-gradient(45deg, var(--dp-data-excluded-hatch) 0 var(--dp-hatch-width), transparent var(--dp-hatch-width) var(--dp-hatch-spacing)); }
.dp-locked { filter: blur(var(--dp-opacity-locked-blur)) saturate(0.8); opacity: 0.85; pointer-events: none; user-select: none; }
.dp-sr-only { position: absolute !important; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0; }
.dp-focus-ring:focus-visible { outline: 3px solid var(--dp-focus); outline-offset: 2px; }
[data-reduced-motion="true"] *, [data-reduced-motion="true"] *::before, [data-reduced-motion="true"] *::after { animation-iteration-count: 1 !important; }
@keyframes dp-live-breathe { 0%, 100% { transform: scale(1); opacity: 0.9; } 50% { transform: scale(1.9); opacity: 0; } }
@keyframes dp-pin-pulse { 0% { transform: scale(1); opacity: 0.7; } 100% { transform: scale(2.4); opacity: 0; } }
@keyframes dp-shimmer { from { background-position: -150% 0; } to { background-position: 250% 0; } }
@keyframes dp-skeleton { 0% { opacity: 0.55; } 50% { opacity: 1; } 100% { opacity: 0.55; } }
`);

writeFileSync(OUT, L.join('\n'));
writeFileSync(resolve(dirname(OUT), 'tokens.js'), `/* GENERATED from docs/design/system/tokens.json by tools/build-tokens.mjs. Do not edit. */\nwindow.DP_TOKENS = ${JSON.stringify(T)};\n`);
console.log(`wrote ${OUT} (${L.length} lines)`);
// print spring summary for docs
for (const p of ['ios', 'android']) for (const [k, s] of Object.entries(T.motion.spring[p])) {
  if (k.startsWith('$')) continue;
  const phys = p === 'ios' ? iosSpring(s) : { zeta: s.dampingRatio, k: s.stiffness };
  const c = springCurve(phys.zeta, phys.k);
  console.log(`${p}.${k}: zeta=${phys.zeta.toFixed(2)} k=${phys.k.toFixed(0)} settle=${c.ms}ms overshoot=${(c.overshoot * 100).toFixed(1)}%`);
}
for (const [k, s] of Object.entries(strip(T.motion.spring.shared))) { const c = springCurve(s.dampingRatio, s.stiffness); console.log(`shared.${k}: settle=${c.ms}ms overshoot=${(c.overshoot*100).toFixed(1)}%`); }
