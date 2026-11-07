#!/usr/bin/env node
/* driveperf.com prototype exporter (DRIVE-132 · A9). Node ≥ 22, no npm dependencies.
   Drives headless Chrome over the DevTools protocol (built-in WebSocket) to take full-page screenshots,
   writes per-story contact sheets (mockup.html) and screenshots them (mockup.png).

   usage:
     node prototypes/web/tools/export-web.mjs                 # everything
     node prototypes/web/tools/export-web.mjs shots S-WEB-01  # only page shots for these screens (into every story that lists them)
     node prototypes/web/tools/export-web.mjs sheets          # only contact sheets + mockup.png
     node prototypes/web/tools/export-web.mjs one <url> <out.png> <width> [scale]   # ad-hoc full-page shot
*/
import { spawn } from 'node:child_process';
import { mkdirSync, writeFileSync, existsSync, rmSync } from 'node:fs';
import { dirname, join, resolve, relative } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { tmpdir } from 'node:os';

const HERE = dirname(fileURLToPath(import.meta.url));
const WEB = resolve(HERE, '..');
const REPO = resolve(WEB, '../..');
const DESIGN = join(REPO, 'docs/design');
const CHROME = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';

/* ---------- What to export ---------- */
export const PAGES = {
  'S-WEB-01': { path: 'index.html', title: 'Home (landing + waitlist)', states: ['variantA', 'variantB', 'formErr', 'submitting', 'duplicate', 'networkErr'] },
  'S-WEB-02': { path: 'pages/waitlist-confirmed.html', title: 'Waitlist confirmation', states: ['default', 'android'] },
  'S-WEB-03': { path: 'pages/privacy.html', title: 'Privacy policy', states: ['default'] },
  'S-WEB-04': { path: 'pages/terms.html', title: 'Terms of use', states: ['default'] },
  'S-WEB-05': { path: 'pages/support.html', title: 'Support and FAQ', states: ['default'] },
  'S-WEB-06': { path: 'pages/how-it-records.html', title: 'How DrivePerf records', states: ['ios', 'android'] },
  'S-WEB-07': { path: 'pages/delete-account.html', title: 'Account deletion request', states: ['form', 'formErr', 'emailSent', 'verified'] },
  'S-WEB-08': { path: 'pages/ride-along.html', title: 'Ride-along landing', states: ['default', 'invalid'] },
  'S-WEB-09': { path: 'pages/email-preferences.html', title: 'Email preferences / unsubscribe', states: ['unsubscribed', 'err'] },
  'S-WEB-10': { path: 'pages/press.html', title: 'Press kit', states: ['default'] },
  'S-WEB-11': { path: '404.html', title: '404', states: ['default'] },
};
/* Story key → screens (jira-map.md "Screens → stories") and which states its sheet shows. The first state is the
   "page" shot (screens/<S-ID>--<w>.png); other states get screens/<S-ID>-<state>--<w>.png. */
export const STORIES = {
  'DRIVE-132': { title: 'DES-31 · Web prototype: driveperf.com pages (11)', screens: Object.keys(PAGES).map((id) => [id, [PAGES[id].states[0]]]) },
  'DRIVE-169': { title: 'F-WEB-01 · Landing page with positioning A/B test', screens: [['S-WEB-01', ['variantA', 'variantB']], ['S-WEB-11', ['default']]] },
  'DRIVE-171': { title: 'F-WEB-02 · Waitlist', screens: [['S-WEB-01', ['variantA', 'formErr', 'submitting', 'duplicate', 'networkErr']], ['S-WEB-02', ['default', 'android']]] },
  'DRIVE-174': { title: 'F-WEB-03 · Legal pages', screens: [['S-WEB-03', ['default']], ['S-WEB-04', ['default']]] },
  'DRIVE-177': { title: 'F-WEB-04 · Account deletion request page', screens: [['S-WEB-07', ['form', 'formErr', 'emailSent', 'verified']]] },
  'DRIVE-69':  { title: 'F-PRV-05 · Delete account and cloud data (web resource)', screens: [['S-WEB-07', ['form', 'emailSent', 'verified']]] },
  'DRIVE-179': { title: 'F-WEB-05 · Support, FAQ and "How DrivePerf records"', screens: [['S-WEB-05', ['default']], ['S-WEB-06', ['ios', 'android']]] },
  'DRIVE-43':  { title: 'F-ONB-03 · Honest capture promise (web explainer)', screens: [['S-WEB-06', ['ios', 'android']]] },
  'DRIVE-181': { title: 'F-WEB-06 · Ride-along landing and app links', screens: [['S-WEB-08', ['default', 'invalid']]] },
  'DRIVE-199': { title: 'F-SHR-05 · Ride-along pass referral (web landing)', screens: [['S-WEB-08', ['default', 'invalid']]] },
  'DRIVE-183': { title: 'F-WEB-07 · Email preferences and unsubscribe', screens: [['S-WEB-09', ['unsubscribed', 'err']]] },
  'DRIVE-188': { title: 'F-WEB-08 · Press kit', screens: [['S-WEB-10', ['default']]] },
};
const WIDTHS = [{ w: 1440, scale: 1, mobile: false }, { w: 390, scale: 2, mobile: true }];

/* ---------- Minimal CDP client ---------- */
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function launch() {
  const port = 9300 + Math.floor(Math.random() * 500);
  const prof = join(tmpdir(), 'dpw-chrome-' + process.pid);
  const proc = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${port}`, `--user-data-dir=${prof}`, '--disable-gpu', '--hide-scrollbars', '--allow-file-access-from-files', '--no-first-run', '--no-default-browser-check', '--force-color-profile=srgb', '--font-render-hinting=none', 'about:blank'], { stdio: 'ignore' });
  for (let i = 0; i < 100; i++) { try { const r = await fetch(`http://127.0.0.1:${port}/json/version`); if (r.ok) break; } catch { /* not up yet */ } await sleep(100); }
  return { port, close: () => { proc.kill(); try { rmSync(prof, { recursive: true, force: true }); } catch { /* temp profile */ } } };
}
async function openTab(port) {
  const t = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json();
  const ws = new WebSocket(t.webSocketDebuggerUrl);
  await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
  let id = 0; const pending = new Map(), waiters = [];
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id && pending.has(msg.id)) { const { res, rej } = pending.get(msg.id); pending.delete(msg.id); msg.error ? rej(new Error(msg.error.message)) : res(msg.result); }
    else if (msg.method === 'Runtime.exceptionThrown') console.error('  page error:', msg.params.exceptionDetails?.exception?.description?.split('\n')[0] || msg.params.exceptionDetails?.text);
    else if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') console.error('  console.error:', msg.params.args?.map((a) => a.value ?? a.description).join(' '));
    if (msg.method) waiters.filter((w) => w.method === msg.method).forEach((w) => { waiters.splice(waiters.indexOf(w), 1); w.res(msg.params); });
  };
  const send = (method, params = {}) => new Promise((res, rej) => { const i = ++id; pending.set(i, { res, rej }); ws.send(JSON.stringify({ id: i, method, params })); });
  const once = (method, timeout = 15000) => new Promise((res) => { waiters.push({ method, res }); setTimeout(res, timeout); });
  await send('Page.enable'); await send('Runtime.enable');
  return { send, once, close: async () => { try { await send('Page.close'); } catch { /* closing */ } ws.close(); } };
}
async function shoot(tab, url, out, { w, scale, mobile, height = null, settle = 1400 }) {
  const metrics = (h) => tab.send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: scale, mobile, screenWidth: w, screenHeight: h });
  await metrics(mobile ? 844 : 900);
  await tab.send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'no-preference' }] });
  const loaded = tab.once('Page.loadEventFired');
  await tab.send('Page.navigate', { url });
  await loaded; await sleep(settle);
  let h = height;
  if (!h) {
    for (let k = 0; k < 3; k++) {
      const r = await tab.send('Runtime.evaluate', { expression: 'Math.ceil(Math.max(document.documentElement.scrollHeight, document.body.scrollHeight))', returnByValue: true });
      const nh = r.result.value; if (nh === h) break; h = nh; await metrics(h); await sleep(350);
    }
  } else { await metrics(h); await sleep(300); }
  const shot = await tab.send('Page.captureScreenshot', { format: 'png', fromSurface: true, captureBeyondViewport: false });
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, Buffer.from(shot.data, 'base64'));
  return h;
}
const pageUrl = (id, state, extra = '') => pathToFileURL(join(WEB, PAGES[id].path)).href + `?shot=1${extra}#${id}/${state}`;
const shotName = (id, state, w) => `${id}${state === PAGES[id].states[0] || STATE_IS_PAGE(id, state) ? '' : '-' + state}--${w}.png`;
const STATE_IS_PAGE = (id, state) => false;

/* ---------- Contact sheet ---------- */
function sheetHtml(key, story, outDir) {
  const rel = (p) => relative(outDir, p).split('\\').join('/');
  const live = (id, st) => rel(join(WEB, PAGES[id].path)) + `#${id}/${st}`;
  const frame = (id, st) => rel(join(WEB, PAGES[id].path)) + `?shot=1#${id}/${st}`;
  const blocks = story.screens.map(([id, states]) => `
    <section class="scr">
      <header><h2><span>${id}</span> ${PAGES[id].title}</h2><a href="${live(id, states[0])}">Open live page →</a></header>
      ${states.map((st) => `
      <div class="state">
        <h3>${st} <a href="${live(id, st)}"><code>#${id}/${st}</code></a></h3>
        <div class="pair">
          <figure class="desk"><div class="clip"><iframe src="${frame(id, st)}" title="${id} ${st} at 1440 px" loading="eager" tabindex="-1"></iframe></div><figcaption>Desktop 1440 · first viewport</figcaption></figure>
          <figure class="mob"><div class="clip"><iframe src="${frame(id, st)}" title="${id} ${st} at 390 px" loading="eager" tabindex="-1"></iframe></div><figcaption>Mobile 390</figcaption></figure>
          <figure class="full"><a href="screens/${shotName(id, st, 1440)}"><img src="screens/${shotName(id, st, 1440)}" alt="${id} ${st}, full page at 1440 px"></a><figcaption>Full page 1440</figcaption></figure>
          <figure class="full m"><a href="screens/${shotName(id, st, 390)}"><img src="screens/${shotName(id, st, 390)}" alt="${id} ${st}, full page at 390 px"></a><figcaption>Full page 390</figcaption></figure>
        </div>
      </div>`).join('')}
    </section>`).join('');
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${key} · web contact sheet</title>
<link rel="icon" href="${rel(join(WEB, 'assets/brand/favicon.svg'))}">
<style>
  :root { color-scheme: dark; --bg:#05070A; --s:#10151C; --l:#96A0B429; --t:#F2F4F7; --t2:#A3AAB6; --a:#5BE7C4; }
  * { box-sizing: border-box; } body { margin: 0; background: var(--bg); color: var(--t); font: 15px/1.5 -apple-system, BlinkMacSystemFont, "SF Pro Text", system-ui, sans-serif; }
  .top { padding: 40px 48px 24px; border-bottom: 1px solid var(--l); display: flex; gap: 20px; align-items: flex-end; justify-content: space-between; flex-wrap: wrap; }
  .top h1 { margin: 0; font: 700 34px/1.1 -apple-system, "SF Pro Display", system-ui, sans-serif; letter-spacing: -0.02em; } .top p { margin: 6px 0 0; color: var(--t2); }
  .top .k { color: var(--a); font-weight: 650; } a { color: var(--a); }
  .scr { padding: 32px 48px 8px; } .scr > header { display: flex; justify-content: space-between; align-items: baseline; gap: 16px; border-bottom: 1px solid var(--l); padding-bottom: 10px; }
  .scr h2 { margin: 0; font: 650 22px/1.2 -apple-system, "SF Pro Display", system-ui, sans-serif; } .scr h2 span { color: var(--a); font-family: ui-monospace, Menlo, monospace; font-size: 17px; margin-right: 8px; }
  .state { padding: 22px 0 8px; } .state h3 { margin: 0 0 12px; font-size: 15px; font-weight: 650; display: flex; gap: 12px; align-items: baseline; } .state h3 code { font-size: 12px; color: var(--t2); }
  .pair { display: grid; grid-template-columns: 720px 195px 250px 130px; gap: 20px; align-items: start; }
  figure { margin: 0; } figcaption { color: var(--t2); font-size: 12px; margin-top: 6px; }
  .clip { position: relative; overflow: hidden; border-radius: 12px; border: 1px solid var(--l); background: #000; }
  .desk .clip { width: 720px; height: 450px; } .desk iframe { width: 1440px; height: 900px; transform: scale(.5); transform-origin: 0 0; border: 0; pointer-events: none; }
  .mob .clip { width: 195px; height: 422px; border-radius: 22px; } .mob iframe { width: 390px; height: 844px; transform: scale(.5); transform-origin: 0 0; border: 0; pointer-events: none; }
  .full img { width: 100%; height: auto; max-height: 900px; object-fit: cover; object-position: top; display: block; border-radius: 10px; border: 1px solid var(--l); }
  .full.m img { max-height: 900px; }
  .foot { padding: 32px 48px 48px; color: var(--t2); font-size: 13px; }
</style></head>
<body>
  <div class="top"><div><p class="k">${key} · driveperf.com prototype (A9)</p><h1>${story.title}</h1><p>Live iframes render the prototype at 1440 and 390 px (first viewport). Full-page captures are in the shared gallery <code>docs/design/screens/web/</code>. States follow PRD §4; switch them with <code>#S-WEB-ID/state</code>.</p></div>
  <p><a href="design-spec.md">design-spec.md</a> · <a href="${rel(join(WEB, 'README.md'))}">prototype README</a></p></div>
  ${blocks}
  <p class="foot">Generated by prototypes/web/tools/export-web.mjs · Route and map geometry © OpenStreetMap contributors.</p>
</body></html>`;
}

/* ---------- Main ---------- */
async function main() {
  const [mode = 'all', ...args] = process.argv.slice(2);
  const br = await launch();
  const tab = await openTab(br.port);
  try {
    if (mode === 'bands') { // review helper: full page split into bands of bandH css px
      const [u, prefix, w, scale = '1', bandH = '1500'] = args;
      const out = resolve(prefix + '-full.png');
      const h = await shoot(tab, u, out, { w: +w, scale: +scale, mobile: +w < 768 });
      for (let y = 0, i = 0; y < h; y += +bandH, i++) {
        const shot = await tab.send('Page.captureScreenshot', { format: 'png', clip: { x: 0, y, width: +w, height: Math.min(+bandH, h - y), scale: 1 } });
        writeFileSync(resolve(`${prefix}-${String(i).padStart(2, '0')}.png`), Buffer.from(shot.data, 'base64'));
      }
      console.log(prefix, h); return;
    }
    if (mode === 'scroll') { // review helper: live page (no ?shot), viewport capture at scroll positions
      const [u, prefix, w, ...ys] = args;
      const mobile = +w < 768;
      await tab.send('Emulation.setDeviceMetricsOverride', { width: +w, height: mobile ? 844 : 900, deviceScaleFactor: 1, mobile });
      const loaded = tab.once('Page.loadEventFired'); await tab.send('Page.navigate', { url: u }); await loaded; await sleep(3200);
      for (const y of ys) {
        await tab.send('Runtime.evaluate', { expression: `window.scrollTo(0, ${y.startsWith('#') ? `document.querySelector('${y}').getBoundingClientRect().top + scrollY` : y})` });
        await sleep(2600);
        const shot = await tab.send('Page.captureScreenshot', { format: 'png' });
        writeFileSync(resolve(`${prefix}-${y.replace(/[^a-z0-9]/gi, '')}.png`), Buffer.from(shot.data, 'base64'));
      }
      return;
    }
    if (mode === 'timeline') { // motion review: live page, optional scroll target, viewport captures at t ms after it
      const [u, prefix, w, target, ...ts] = args;
      const mobile = +w < 768;
      await tab.send('Emulation.setDeviceMetricsOverride', { width: +w, height: mobile ? 844 : 900, deviceScaleFactor: 1, mobile });
      const loaded = tab.once('Page.loadEventFired'); await tab.send('Page.navigate', { url: u }); await loaded;
      if (target && target !== '0') { await sleep(300); await tab.send('Runtime.evaluate', { expression: `window.scrollTo({top: ${target.startsWith('#') ? `document.querySelector('${target}').getBoundingClientRect().top + scrollY - (innerHeight / 2)` : target}, behavior: 'instant'})` }); }
      let prev = 0;
      for (const t of ts) { await sleep(+t - prev); prev = +t; const shot = await tab.send('Page.captureScreenshot', { format: 'png' }); writeFileSync(resolve(`${prefix}-${t}.png`), Buffer.from(shot.data, 'base64')); }
      return;
    }
    if (mode === 'perf') { // motion review: frame pacing (rAF deltas), layout shifts and long tasks while scrolling through the page
      const [u, w] = args; const mobile = +w < 768;
      await tab.send('Emulation.setDeviceMetricsOverride', { width: +w, height: mobile ? 844 : 900, deviceScaleFactor: mobile ? 2 : 1, mobile });
      await tab.send('Page.addScriptToEvaluateOnNewDocument', { source: `window.__cls=0;window.__lt=0;window.__dt=[];new PerformanceObserver(l=>l.getEntries().forEach(e=>{if(!e.hadRecentInput){window.__cls+=e.value;(window.__src=window.__src||[]).push(e.value.toFixed(3)+':'+Math.round(e.startTime)+':'+(e.sources||[]).map(s=>s.node&&(s.node.className||s.node.nodeName)).join('|'))}})).observe({type:'layout-shift',buffered:true});try{new PerformanceObserver(l=>l.getEntries().forEach(e=>window.__lt++)).observe({type:'longtask',buffered:true})}catch(e){};let __p=0;(function f(t){if(__p)window.__dt.push(t-__p);__p=t;requestAnimationFrame(f)})(0);` });
      const loaded = tab.once('Page.loadEventFired'); await tab.send('Page.navigate', { url: u }); await loaded; await sleep(3500);
      await tab.send('Runtime.evaluate', { expression: `(async()=>{const H=document.documentElement.scrollHeight;for(let y=0;y<H;y+=40){scrollTo(0,y);await new Promise(r=>requestAnimationFrame(r));}})()`, awaitPromise: true });
      await sleep(4000);
      const r = await tab.send('Runtime.evaluate', { expression: `(()=>{const d=window.__dt.slice(5).sort((a,b)=>a-b);const p=(q)=>d[Math.floor(d.length*q)]||0;return JSON.stringify({frames:d.length,p50:p(.5).toFixed(1),p95:p(.95).toFixed(1),over33:d.filter(x=>x>33.4).length,cls:window.__cls.toFixed(4),longTasks:window.__lt,width:[innerWidth,document.documentElement.scrollWidth],src:window.__src})})()`, returnByValue: true });
      console.log(w, r.result.value); return;
    }
    if (mode === 'measure') { // debug: section heights at two viewport heights
      const [u, w] = args;
      for (const vh of [844]) {
        await tab.send('Emulation.setDeviceMetricsOverride', { width: +w, height: vh, deviceScaleFactor: 1, mobile: +w < 768 });
        const loaded = tab.once('Page.loadEventFired'); await tab.send('Page.navigate', { url: u }); await loaded; await sleep(1500);
        const r = await tab.send('Runtime.evaluate', { expression: `JSON.stringify([innerWidth, document.documentElement.scrollWidth, ...[...document.querySelectorAll('main > *, footer')].map(e => e.className.split(' ')[0] + ':' + Math.round(e.getBoundingClientRect().height)), 'WIDE:', ...[...document.querySelectorAll('body *')].filter(e => e.scrollWidth > e.clientWidth + 1 && getComputedStyle(e).overflowX === 'visible').slice(-8).map(e => e) .map(e => e.tagName + '.' + e.className + ' ' + e.scrollWidth + '/' + e.clientWidth + ' ' + (e.textContent||'').trim().slice(0,30))])`, returnByValue: true });
        console.log(vh, r.result.value);
      }
      return;
    }
    if (mode === 'one') {
      const [u, out, w, scale = '1'] = args;
      const h = await shoot(tab, u, resolve(out), { w: +w, scale: +scale, mobile: +w < 768 });
      console.log(out, h); return;
    }
    if (mode === 'all' || mode === 'shots') {
      const only = args.length ? new Set(args) : null;
      const cache = new Map(); // same page+state+width shot once, copied to each story dir
      for (const [key, story] of Object.entries(STORIES)) {
        for (const [id, states] of story.screens) {
          if (only && !only.has(id)) continue;
          for (const st of states) for (const W of WIDTHS) {
            const out = join(DESIGN, key, 'web/screens', shotName(id, st, W.w));
            const ck = `${id}/${st}/${W.w}`;
            if (cache.has(ck)) { mkdirSync(dirname(out), { recursive: true }); writeFileSync(out, cache.get(ck)); continue; }
            await shoot(tab, pageUrl(id, st), out, W);
            const { readFileSync } = await import('node:fs'); cache.set(ck, readFileSync(out));
            console.log('shot', key, id, st, W.w);
          }
        }
      }
    }
    if (mode === 'all' || mode === 'sheets') {
      for (const [key, story] of Object.entries(STORIES)) {
        const dir = join(DESIGN, key, 'web');
        mkdirSync(join(dir, 'assets'), { recursive: true });
        writeFileSync(join(dir, 'mockup.html'), sheetHtml(key, story, dir));
        await shoot(tab, pathToFileURL(join(dir, 'mockup.html')).href, join(dir, 'mockup.png'), { w: 1440, scale: 1, mobile: false, settle: 4500 });
        console.log('sheet', key);
      }
    }
  } finally { await tab.close(); br.close(); }
}
if (import.meta.url === pathToFileURL(process.argv[1]).href) main().catch((e) => { console.error(e); process.exit(1); });
