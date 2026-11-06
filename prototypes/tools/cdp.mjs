// Tiny Chrome DevTools Protocol client (no npm deps; Node ≥ 22 global WebSocket). Used by export.mjs and check.mjs.
// Launches "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new (override: CHROME=/path).
import { spawn } from 'node:child_process';
import { mkdtempSync, readFileSync, existsSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

export const CHROME = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export async function launch() {
  if (!existsSync(CHROME)) throw new Error(`Chrome not found at ${CHROME} (set CHROME=/path/to/chrome)`);
  const dir = mkdtempSync(join(tmpdir(), 'dp-chrome-'));
  const proc = spawn(CHROME, ['--headless=new', '--remote-debugging-port=0', `--user-data-dir=${dir}`, '--no-first-run', '--no-default-browser-check',
    '--disable-gpu', '--hide-scrollbars', '--allow-file-access-from-files', '--disable-background-timer-throttling', '--disable-renderer-backgrounding',
    '--disable-backgrounding-occluded-windows', '--force-color-profile=srgb', '--font-render-hinting=none', '--disable-extensions', 'about:blank'], { stdio: ['ignore', 'ignore', 'ignore'] });
  let port, path;
  for (let i = 0; i < 200 && !port; i++) {
    await sleep(50);
    const f = join(dir, 'DevToolsActivePort');
    if (existsSync(f)) { const [p, q] = readFileSync(f, 'utf8').split('\n'); if (p && q) { port = p.trim(); path = q.trim(); } }
  }
  if (!port) { proc.kill(); throw new Error('Chrome did not expose a DevTools port'); }
  const ws = new WebSocket(`ws://127.0.0.1:${port}${path}`);
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
  let id = 0;
  const pending = new Map(), listeners = new Map();
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id && pending.has(msg.id)) { const { res, rej } = pending.get(msg.id); pending.delete(msg.id); msg.error ? rej(new Error(msg.error.message)) : res(msg.result); return; }
    const key = (msg.sessionId || '') + '|' + msg.method;
    (listeners.get(key) || []).forEach((fn) => fn(msg.params));
  };
  const send = (method, params = {}, sessionId) => new Promise((res, rej) => { const i = ++id; pending.set(i, { res, rej }); ws.send(JSON.stringify({ id: i, method, params, sessionId })); });
  const on = (sessionId, method, fn) => { const k = (sessionId || '') + '|' + method; listeners.set(k, [...(listeners.get(k) || []), fn]); };

  async function newPage() {
    const { targetId } = await send('Target.createTarget', { url: 'about:blank' });
    const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true });
    const s = (m, p) => send(m, p, sessionId);
    await s('Page.enable'); await s('Runtime.enable'); await s('Log.enable');
    // background tabs pause requestAnimationFrame; keep every tab "focused" and active
    await s('Emulation.setFocusEmulationEnabled', { enabled: true }).catch(() => {});
    await s('Page.setWebLifecycleState', { state: 'active' }).catch(() => {});
    const errors = [];
    on(sessionId, 'Runtime.exceptionThrown', (p) => errors.push({ kind: 'exception', text: (p.exceptionDetails.exception && p.exceptionDetails.exception.description) || p.exceptionDetails.text }));
    on(sessionId, 'Runtime.consoleAPICalled', (p) => { if (p.type === 'error' || p.type === 'assert') errors.push({ kind: 'console', text: p.args.map((a) => a.value ?? a.description ?? '').join(' ') }); });
    on(sessionId, 'Log.entryAdded', (p) => { if (p.entry.level === 'error' && !/fonts\.(googleapis|gstatic)/.test(p.entry.url || '') && !/navigator\.vibrate/.test(p.entry.text || '')) errors.push({ kind: 'log', text: `${p.entry.text} ${p.entry.url || ''}`.trim() }); });
    let loadWaiters = [];
    on(sessionId, 'Page.loadEventFired', () => { loadWaiters.forEach((r) => r()); loadWaiters = []; });
    const page = {
      errors,
      send: s,
      async viewport(width, height, scale = 1) { await s('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: scale, mobile: false }); },
      async goto(url, { timeout = 15000 } = {}) {
        errors.length = 0;
        const loaded = new Promise((r) => loadWaiters.push(r));
        await s('Page.navigate', { url });
        await Promise.race([loaded, sleep(timeout)]);
      },
      async eval(expression, { timeout = 8000 } = {}) { const r = await Promise.race([s('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true }), sleep(timeout).then(() => ({ result: { value: undefined }, timedOut: true }))]); if (r.exceptionDetails) throw new Error(r.exceptionDetails.text); return r.result.value; },
      async waitFor(expression, { timeout = 10000, interval = 50 } = {}) { const t0 = Date.now(); while (Date.now() - t0 < timeout) { try { if (await page.eval(expression)) return true; } catch (e) { /* not ready */ } await sleep(interval); } return false; },
      async screenshot({ fullPage = false } = {}) {
        let clip;
        if (fullPage) { const m = await s('Page.getLayoutMetrics'); const c = m.cssContentSize || m.contentSize; clip = { x: 0, y: 0, width: Math.ceil(c.width), height: Math.ceil(c.height), scale: 1 }; }
        const r = await s('Page.captureScreenshot', { format: 'png', captureBeyondViewport: fullPage, ...(clip ? { clip } : {}) });
        return Buffer.from(r.data, 'base64');
      },
      close: () => send('Target.closeTarget', { targetId }),
    };
    return page;
  }
  async function close() { await Promise.race([send('Browser.close').catch(() => {}), sleep(1500)]); try { ws.close(); } catch (e) { /* closed */ } try { proc.kill('SIGKILL'); } catch (e) { /* gone */ } await sleep(150); try { rmSync(dir, { recursive: true, force: true }); } catch (e) { /* locked */ } }
  return { newPage, close };
}

/** Run tasks with N pages in parallel. worker(page, task) */
export async function pool(browser, tasks, n, worker) {
  const pages = await Promise.all(Array.from({ length: Math.max(1, Math.min(n, tasks.length)) }, () => browser.newPage()));
  let i = 0;
  await Promise.all(pages.map(async (p) => { while (i < tasks.length) { const t = tasks[i++]; await worker(p, t); } }));
  await Promise.all(pages.map((p) => p.close().catch(() => {})));
}
