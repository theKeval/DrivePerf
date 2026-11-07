/* DrivePerf prototype engine · shell (S-NAV-01 chrome): status bar, Dynamic Island / punch-hole, home indicator,
   tab bar (iOS floating glass + tabViewBottomAccessory capture pill · Android NavigationBar + M3E floating toolbar),
   toasts / snackbars and the haptic annotation. Screens never render these; they declare `chrome` (AUTHORING.md §3). */
(function () {
  const UI = window.DP_UI, I = window.DP_ICONS, D = window.DP_DATA;
  const SIZE = { ios: { w: 402, h: 874, top: 62, bottom: 34 }, android: { w: 412, h: 915, top: 40, bottom: 24 } };
  let vp, chromeEl, sbEl, islandEl, homeEl, toastEl, toastTimer;

  const statusBar = (p) => p === 'ios'
    ? `<span class="dp-sb-t">9:41</span><span class="dp-sb-chip" hidden></span><span class="dp-sb-i"><svg width="18" height="12" viewBox="0 0 18 12" aria-hidden="true"><g fill="currentColor"><rect x="0" y="7.5" width="3" height="4.5" rx="1"/><rect x="5" y="5" width="3" height="7" rx="1"/><rect x="10" y="2.5" width="3" height="9.5" rx="1"/><rect x="15" y="0" width="3" height="12" rx="1"/></g></svg><svg width="16" height="12" viewBox="0 0 16 12" aria-hidden="true"><path d="M8 11.5L5.6 8.9a3.4 3.4 0 0 1 4.8 0zM3.4 6.7a6.5 6.5 0 0 1 9.2 0l-1.3 1.4a4.6 4.6 0 0 0-6.6 0zM1.2 4.4a9.6 9.6 0 0 1 13.6 0l-1.3 1.3a7.8 7.8 0 0 0-11 0z" fill="currentColor"/></svg><span class="dp-sb-bat"><i></i></span></span>`
    : `<span class="dp-sb-t">7:30</span><span class="dp-sb-chip" hidden></span><span class="dp-sb-i"><svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M8 13.5L.6 5.2a10.6 10.6 0 0 1 14.8 0z" fill="currentColor"/></svg><svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M1.5 14.5h13v-13z" fill="currentColor"/></svg><svg width="10" height="16" viewBox="0 0 10 16" aria-hidden="true"><rect x="1" y="2" width="8" height="13" rx="1.6" fill="none" stroke="currentColor" stroke-width="1.4"/><rect x="2.6" y="5.5" width="4.8" height="8" rx=".6" fill="currentColor"/><rect x="3.5" y=".5" width="3" height="1.6" rx=".5" fill="currentColor"/></svg></span>`;

  function build(viewport, platform) {
    vp = viewport;
    vp.insertAdjacentHTML('beforeend', `<div class="dp-chrome" aria-label="App chrome"></div><div class="dp-toast" role="status" aria-live="polite"></div><div class="dp-sb">${statusBar(platform)}</div><div class="dp-hw ${platform === 'ios' ? 'dp-island' : 'dp-punch'}"></div><div class="dp-home"></div>`);
    chromeEl = vp.querySelector('.dp-chrome'); sbEl = vp.querySelector('.dp-sb'); islandEl = vp.querySelector('.dp-hw'); homeEl = vp.querySelector('.dp-home'); toastEl = vp.querySelector('.dp-toast');
  }

  /** Capture status derived from global params (+ screen overrides). */
  function capture(params) {
    const lt = params.live && D.liveTrip.states[params.live];
    const preset = params.health && D.health.presets[params.health];
    const red = preset && Object.values(preset.status || {}).includes('red');
    let mode = 'ready';
    if (lt) mode = 'recording';
    else if (params.capture === 'denied') mode = 'denied';
    else if (red) mode = 'attention';
    else if (params.capture === 'paused' || params.health === 'paused') mode = 'paused';
    else if (params.capture === 'manual') mode = 'manual';
    return { mode, live: lt ? { key: params.live, ...lt } : null, health: params.health || 'allGood', attention: mode === 'attention' || mode === 'denied', lastCheckIn: D.health.presets.allGood.lastCheckIn };
  }

  function accessory(mode, ctx, platform) {
    const data = ctx.data, cap = ctx.capture;
    const ago = data.ago ? data.ago(cap.lastCheckIn) : '18 min ago';
    const start = `<span class="dp-acc-go ui-press" data-start-drive role="button" tabindex="0" aria-label="Start drive">${I.svg('record', { size: 18 })}Start</span>`;
    const M = {
      ready: [`<span class="dp-acc-dot"></span>`, 'Auto-detect on · ready', `Last check-in ${ago}`, start],
      manual: [`<span class="dp-acc-ic">${UI.icon('drives', 18)}</span>`, 'Manual mode', 'Auto-detect is off', start],
      paused: [`<span class="dp-acc-ic">${UI.icon('pause', 16)}</span>`, 'Auto-detect paused', 'Until 8:00 tomorrow', `<span class="dp-acc-go is-tonal ui-press" data-action-shell="resume" role="button" tabindex="0">Resume</span>`],
      attention: [`<span class="dp-acc-ic is-warn">${UI.icon('warn', 18)}</span>`, 'Auto-detect needs attention', 'Location is set to While Using', `<span class="dp-acc-chev">${UI.icon('chevronRight', 18)}</span>`],
      denied: [`<span class="dp-acc-ic is-warn">${UI.icon('gps', 18)}</span>`, 'Automatic trips are off', 'Location needed · manual drives work', start],
      recording: (() => { const l = cap.live || data.live('passenger'); return [`<span class="dp-acc-dot is-live"></span>`, `Recording · ${Math.round(l.elapsedS / 60)} min · ${l.distanceKm} km`, l.passenger ? 'Passenger trip · tap to open' : 'Tap to open', `<span class="dp-acc-chev">${UI.icon('chevronRight', 18)}</span>`]; })(),
      processing: [`<span class="dp-acc-ic">${UI.loader({ size: 20 })}</span>`, 'Decoding your drive…', 'Building the replay', ''],
    }[mode] || [];
    const nav = mode === 'recording' ? 'S-DRV-01' : mode === 'attention' ? 'S-SET-05' : mode === 'denied' ? 'S-ONB-16' : 'S-HOME-02';
    const navParams = mode === 'recording' ? `data-params="live=${cap.live ? cap.live.key : 'passenger'}"` : '';
    return `<div class="dp-acc ${platform === 'ios' ? 'ui-glassy' : ''} is-${mode}" data-nav="${nav}" ${navParams} role="button" tabindex="0" aria-label="${UI.esc(M[1] + '. ' + M[2])}">${M[0]}<span class="dp-acc-tx"><b>${UI.esc(M[1])}</b><small>${UI.esc(M[2])}</small></span>${M[3]}</div>`;
  }

  /** Resolve chrome for the owning (topmost root/push) layer's ctx. */
  function resolveChrome(ctx) {
    if (!ctx) return { tabBar: false };
    const pres = ctx.pres, p = ctx.platform;
    const tab = ctx.tab && ctx.tab !== 'profile' ? ctx.tab : null;
    let c = { tabBar: !!tab && (pres === 'root' || pres === 'push'), tab, accessory: null, minimised: false, island: true, statusBar: true, statusChip: null };
    if (c.tabBar) c.accessory = p === 'ios' || (pres === 'root' && tab === 'drives') ? ctx.capture.mode : null;
    const own = typeof ctx.def?.chrome === 'function' ? ctx.def.chrome(ctx) : ctx.def?.chrome;
    if (own) c = { ...c, ...own };
    if (!c.tabBar) c.accessory = own && own.accessory !== undefined ? own.accessory : null;
    if (c.accessory === true) c.accessory = ctx.capture.mode;
    return c;
  }

  let lastKey = '';
  function updateChrome(c, ctx, z) {
    const p = ctx ? ctx.platform : 'ios';
    vp.dataset.chrome = [c.tabBar && 'tabs', c.accessory && 'acc'].filter(Boolean).join(' ') || 'none';
    const key = JSON.stringify([c.tabBar, c.tab, c.accessory, ctx && ctx.capture.live && ctx.capture.live.key, p, ctx && ctx.theme]);
    chromeEl.style.zIndex = z;
    chromeEl.classList.toggle('is-min', !!c.minimised);
    chromeEl.hidden = !c.tabBar && !c.accessory;
    islandEl.hidden = c.island === false;
    sbEl.hidden = c.statusBar === false;
    const chip = sbEl.querySelector('.dp-sb-chip');
    chip.hidden = !c.statusChip; chip.textContent = c.statusChip || '';
    if (key === lastKey) { const on = chromeEl.querySelector('.ui-tab.is-on'); if (c.tab && on && on.dataset.tab !== c.tab) setTab(c.tab); return; }
    lastKey = key;
    chromeEl.innerHTML = `${c.accessory ? accessory(c.accessory, ctx, p) : ''}${c.tabBar ? UI.tabBar(c.tab) : ''}`;
  }
  function setTab(tab) {
    chromeEl.querySelectorAll('.ui-tab').forEach((t) => { const on = t.dataset.tab === tab; t.classList.toggle('is-on', on); t.setAttribute('aria-selected', on); });
    const lens = chromeEl.querySelector('.ui-tab-lens'); if (lens) lens.style.setProperty('--i', UI.TABS.findIndex((t) => t[0] === tab));
    lastKey = '';
  }
  const setMinimised = (on) => chromeEl && chromeEl.classList.toggle('is-min', on);
  /** 'light' = white status-bar content (dark backgrounds), 'dark' = black content. */
  function updateStatusBar(mode) { vp.dataset.sb = mode; }

  function toast(text, o = {}) {
    clearTimeout(toastTimer);
    toastEl.innerHTML = `<span class="dp-toast-in ${o.kind ? 'is-' + o.kind : ''}">${o.icon ? UI.icon(o.icon, 16) : ''}<span>${UI.esc(text)}</span>${o.action ? `<b data-toast-action>${UI.esc(o.action)}</b>` : ''}</span>`;
    toastEl.classList.remove('is-on'); void toastEl.offsetWidth; toastEl.classList.add('is-on');
    if (!o.sticky) toastTimer = setTimeout(() => toastEl.classList.remove('is-on'), o.duration || 2600);
  }

  window.DP = window.DP || {};
  DP.shell = { SIZE, build, capture, resolveChrome, updateChrome, setTab, setMinimised, updateStatusBar, toast, accessoryHtml: accessory };
})();
