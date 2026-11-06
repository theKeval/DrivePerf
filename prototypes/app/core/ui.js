/* DrivePerf prototype engine · DP_UI: platform-aware primitives (components.md). Every function returns an HTML
   string; styles live in app.css, scoped by [data-platform] and [data-theme] on the device. Interactive options
   (`nav`, `action`, `back`, …) become data-attributes that the router handles (AUTHORING.md §4).
   Load after shared/{tokens,data,icons,motion}.js and core/registry.js. */
(function () {
  const I = window.DP_ICONS, D = window.DP_DATA, U = D && D.util;
  const plat = () => (window.DP && DP.platform) || document.documentElement.dataset.platform || 'ios';
  const isIOS = () => plat() === 'ios';
  const esc = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const cls = (...c) => c.filter(Boolean).join(' ');
  let uid = 0;
  const nid = (p = 'u') => `${p}${(++uid).toString(36)}`;

  // ---------------- extra UI glyphs (platform shells may swap UI glyphs; never pins/quality/provenance) -------------
  const G = {
    chevronLeft: '<path d="M14.5 5.5L8 12l6.5 6.5"/>',
    arrowBack: '<path d="M19 12H5.5M11 5.5L4.5 12l6.5 6.5"/>',
    moreVert: '<circle cx="12" cy="5.5" r="1.6" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none"/><circle cx="12" cy="18.5" r="1.6" fill="currentColor" stroke="none"/>',
    search: '<circle cx="11" cy="11" r="6.5"/><path d="M20 20l-4.2-4.2"/>',
    signal: '', minus: '<path d="M5 12h14"/>', up: '<path d="M6 14l6-6 6 6"/>', down: '<path d="M6 10l6 6 6-6"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>', pin: '<path d="M12 21s-6.5-6.2-6.5-11a6.5 6.5 0 0 1 13 0c0 4.8-6.5 11-6.5 11z"/><circle cx="12" cy="10" r="2.3"/>',
    warn: '<path d="M12 3.5l9.5 16.5h-19z"/><path d="M12 10v4.5"/><circle cx="12" cy="17.3" r="1" fill="currentColor" stroke="none"/>',
    refresh: '<path d="M20 12a8 8 0 1 1-2.4-5.7"/><path d="M20 4v4.5h-4.5"/>', sparkle: '<path d="M12 3l1.8 5.4L19 10l-5.2 1.6L12 17l-1.8-5.4L5 10l5.2-1.6z"/>',
  };
  function icon(name, size = 22, label) {
    if (G[name] !== undefined) return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" ${label ? `role="img" aria-label="${esc(label)}"` : 'aria-hidden="true"'}>${G[name]}</svg>`;
    return I.svg(name, { size, label });
  }
  const backGlyph = (size) => icon(isIOS() ? 'chevronLeft' : 'arrowBack', size || (isIOS() ? 22 : 24));
  const moreGlyph = (size) => icon(isIOS() ? 'more' : 'moreVert', size || 22);

  // ---------------- interaction attributes ----------------
  const qs = (p) => (p ? Object.entries(p).filter(([, v]) => v != null && v !== '').map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&') : '');
  /** {nav:'S-ID/state', params:{}, back, action, tab, state, toast, haptic, zoom, label, disabled} → attribute string */
  function act(o = {}) {
    const a = [];
    if (o.nav) a.push(`data-nav="${esc(o.nav)}"`);
    if (o.params) a.push(`data-params="${esc(qs(o.params))}"`);
    if (o.back) a.push('data-back');
    if (o.action) a.push(`data-action="${esc(o.action)}"`);
    if (o.arg != null) a.push(`data-arg="${esc(o.arg)}"`);
    if (o.tab) a.push(`data-tab="${esc(o.tab)}"`);
    if (o.state) a.push(`data-set-state="${esc(o.state)}"`);
    if (o.toast) a.push(`data-toast="${esc(o.toast)}"`);
    if (o.haptic) a.push(`data-haptic="${esc(o.haptic)}"`);
    if (o.zoom) a.push('data-zoom');
    if (o.label) a.push(`aria-label="${esc(o.label)}"`);
    if (o.disabled) a.push('aria-disabled="true"');
    const interactive = o.nav || o.back || o.action || o.tab || o.state || o.toast;
    if (interactive && !o.noRole) a.push('role="button" tabindex="0"');
    return a.join(' ');
  }
  const isAct = (o) => !!(o && (o.nav || o.back || o.action || o.tab || o.state || o.toast));

  // ---------------- numbers ----------------
  /** DP Numerals with the unit at 50% (brand.md §6). num(37.9,'km') · num('≈ 0.4','km',{est:true}) */
  const num = (v, unit, { est, cls: c, style } = {}) => `<span class="${cls('ui-num', est && 'dp-est', c)}"${style ? ` style="${style}"` : ''}>${esc(v)}${unit ? `<span class="ui-unit">${esc(unit)}</span>` : ''}</span>`;
  const deltaGlyph = (d) => (d > 0 ? '▲' : d < 0 ? '▼' : '●');

  // ---------------- buttons, icon buttons, chips ----------------
  /** kind: filled|tonal|glass|text|outline|destructive · size: l|m|s · block · icon */
  function button(label, o = {}) {
    const kind = o.kind || 'filled', size = o.size || 'm';
    return `<span class="${cls('ui-btn', `ui-btn--${kind}`, `ui-btn--${size}`, o.block && 'ui-btn--block', o.disabled && 'is-disabled', 'ui-press', o.cls)}" ${act(o)}${o.style ? ` style="${o.style}"` : ''}>${o.icon ? icon(o.icon, size === 's' ? 16 : 18) : ''}<span class="ui-btn-l">${esc(label)}</span>${o.trailingIcon ? icon(o.trailingIcon, 16) : ''}</span>`;
  }
  /** Stacked action button (icon above label), used for action rows (Replay · Analysis · Story · Share). */
  function actionButton(label, o = {}) {
    return `<span class="${cls('ui-actbtn', o.primary && 'is-primary', o.locked && 'is-locked', o.disabled && 'is-disabled', 'ui-press')}" ${act({ ...o, label: o.label || label + (o.locked ? ', Pro' : '') })}><i>${icon(o.icon || 'info', 22)}${o.locked ? `<b class="ui-actbtn-lock">${icon('lock', 11)}</b>` : ''}</i><span>${esc(label)}</span></span>`;
  }
  function iconButton(name, o = {}) {
    return `<span class="${cls('ui-iconbtn', o.glass !== false && 'ui-glassy', o.tonal && 'ui-iconbtn--tonal', 'ui-press', o.cls)}" ${act({ ...o, label: o.label || name })}${o.menu ? ` data-menu="${esc(o.menu)}" aria-haspopup="menu"${isAct(o) ? '' : ' role="button" tabindex="0"'}` : ''}>${o.svg || icon(name, o.size || (isIOS() ? 20 : 24))}${o.badge ? '<b class="ui-dotbadge"></b>' : ''}</span>`;
  }
  const backButton = (o = {}) => iconButton('back', { glass: true, back: true, label: 'Back', ...o, svg: backGlyph() });
  /** kind: up|down|same|record|quality|outline|filter|assist|glass|pro|plain */
  function chip(label, o = {}) {
    return `<span class="${cls('ui-chip', o.kind && `ui-chip--${o.kind}`, o.selected && 'is-selected', isAct(o) && 'ui-press', o.cls)}" ${act(o)}>${o.icon ? (o.icon.startsWith('<') ? o.icon : icon(o.icon, 14)) : ''}${o.html || esc(label)}</span>`;
  }
  /** Comparison chip (components.md §4) from a smoothness object or a number. */
  function deltaChip(s, o = {}) {
    if (!s) return '';
    if (typeof s === 'number') s = { delta: s, compareText: `${U.signed(s)} vs your usual` };
    if (s.calibrating) return chip('', { kind: 'same', html: `${esc(s.compareText)} <span class="ui-dots3">${[0, 1, 2].map((i) => `<i class="${i < s.calibrating.k ? 'on' : ''}"></i>`).join('')}</span>`, cls: 'ui-delta' });
    if (s.record && s.record.isNew && o.record !== false) return chip('New route record', { kind: 'record', icon: I.svg('rosette', { size: 14 }), cls: 'ui-delta' });
    if (s.delta == null) return chip(s.compareText || '', { kind: 'same', cls: 'ui-delta' });
    const kind = s.delta > 0 ? 'up' : s.delta < 0 ? 'down' : 'same';
    const text = o.short ? `${U.signed(s.delta)} vs usual` : s.compareText;
    return chip('', { kind, html: `<b aria-hidden="true">${deltaGlyph(s.delta)}</b>${esc(text)}`, cls: 'ui-delta' });
  }
  const proBadge = (t = 'PRO') => `<span class="ui-probadge">${esc(t)}</span>`;
  const previewBadge = (ent, o = {}) => {
    const s = { ...(D.entitlement.states[ent] || {}), ...(o.day ? { day: o.day } : {}) };
    const t = s.plan === 'ridealong' ? `RIDE-ALONG · DAY ${s.day}` : `PRO PREVIEW · DAY ${s.day || 9}`;
    delete o.day;
    return `<span class="${cls('ui-prevbadge', 'ui-glassy', 'ui-press')}" ${act({ nav: 'S-SUB-01', label: s.label || t, ...o })}>${esc(t)}</span>`;
  };
  const avatar = (o = {}) => `<span class="${cls('ui-avatar', 'ui-glassy', 'ui-press')}" ${act({ nav: 'S-ACC-01', label: 'Profile' + (o.badge ? ', needs attention' : ''), ...o })}><span>${esc(o.initial || D.user.initials)}</span>${o.badge ? '<b class="ui-dotbadge"></b>' : ''}</span>`;
  const carDot = (v) => `<span class="ui-cardot" style="background:var(--dp-car-${v.colour})"></span>`;
  function carChip(v, o = {}) {
    if (v === 'passenger') return chip('Passenger', { icon: 'passenger', kind: 'outline', ...o });
    if (!v) return chip('Unknown car', { kind: 'outline', cls: 'is-dashed', ...o });
    return chip(v.nickname, { icon: carDot(v), kind: o.kind || 'plain', ...o });
  }

  // ---------------- segmented / button group ----------------
  /** options: ['Time','Distance'] or [{id,label}] · selected id · action (receives arg = id) */
  function segmented(options, o = {}) {
    const opts = options.map((x) => (typeof x === 'string' ? { id: x, label: x } : x));
    const sel = o.selected ?? opts[0].id;
    return `<div class="${cls('ui-seg', o.cls)}" role="tablist"${o.style ? ` style="${o.style}"` : ''}>${opts.map((x) => `<span role="tab" aria-selected="${x.id === sel}" class="${cls('ui-seg-i', x.id === sel && 'is-on', 'ui-press')}" ${act({ action: o.action, arg: x.id, haptic: 'selection', state: x.state, noRole: true })}>${x.icon ? icon(x.icon, 16) : ''}${esc(x.label)}</span>`).join('')}</div>`;
  }

  // ---------------- headers / top bars ----------------
  /** Tab-root header. iOS large title + glass trailing items; Android LargeFlexibleTopAppBar. Collapses on scroll. */
  function largeTitle(title, o = {}) {
    const trailing = (o.trailing || []).join('');
    return `<header class="ui-lt" data-collapse-title="${esc(title)}"><div class="ui-lt-row"><div class="ui-lt-tt"><h1>${esc(title)}</h1>${o.subtitle ? `<p>${esc(o.subtitle)}</p>` : ''}</div><div class="ui-lt-trail">${trailing}</div></div></header>`;
  }
  /** Detail-screen bar: iOS inline title + subtitle with glass back/more; Android MediumFlexibleTopAppBar.
      Render it as the FIRST child of page(); it is fixed at the top of the layer. */
  function navBar(o = {}) {
    const trailing = (o.trailing || []).join('');
    const back = o.back === false ? '' : o.close ? iconButton('close', { back: true, label: 'Close' }) : backButton();
    if (isIOS()) return `<div class="ui-navbar ${o.transparent ? 'is-transparent' : ''}"><div class="ui-navbar-l">${back}</div><div class="ui-navbar-c"><b>${esc(o.title || '')}</b>${o.subtitle ? `<span>${esc(o.subtitle)}</span>` : ''}</div><div class="ui-navbar-r">${trailing}</div></div>`;
    return `<div class="ui-navbar ${o.transparent ? 'is-transparent' : ''}"><div class="ui-navbar-l">${back}</div><div class="ui-navbar-c"><b>${esc(o.title || '')}</b>${o.subtitle ? `<span>${esc(o.subtitle)}</span>` : ''}</div><div class="ui-navbar-r">${trailing}</div></div>`;
  }
  /** Android medium-flexible title block placed in content under navBar (iOS: nothing; the bar has the title). */
  const mediumTitle = (title, sub) => (isIOS() ? '' : `<div class="ui-mt"><h1>${esc(title)}</h1>${sub ? `<p>${esc(sub)}</p>` : ''}</div>`);

  // ---------------- page / layout ----------------
  /** The scroll container for every non-overlay screen (root/push/full).
      o.large = {title, subtitle, trailing:[html]}  → tab-root large title (collapses to a compact bar on scroll)
      o.nav   = {title, subtitle, trailing:[html], back:false, close:true, transparent, medium:false} → fixed detail bar
                (Android also gets the MediumFlexibleTopAppBar title block in content unless medium:false)
      o.fixed / o.overlay = extra html outside the scroller (floating controls, sys mocks) · o.bg · o.cls */
  function page(content, o = {}) {
    let fixed = o.fixed || '', pre = '';
    if (o.nav) { fixed = navBar(o.nav) + fixed; if (o.nav.title && o.nav.medium !== false) pre += mediumTitle(o.nav.title, o.nav.subtitle); }
    if (o.large) pre += largeTitle(o.large.title, o.large);
    const cbar = o.large ? `<div class="ui-cbar" aria-hidden="true"><b>${esc(o.large.title)}</b></div>` : '';
    return `<div class="${cls('ui-page', o.cls, o.nav && 'has-navbar')}"${o.bg ? ` style="background:${o.bg}"` : ''}>${cbar}${fixed}<div class="ui-scroll" data-scroll>${pre}${content}<div class="ui-bottom-space"></div></div>${o.overlay || ''}</div>`;
  }
  const pad = (c, o = {}) => `<div class="ui-pad"${o.style ? ` style="${o.style}"` : ''}>${c}</div>`;
  const stack = (items, gap = 12) => `<div class="ui-stack" style="gap:${gap}px">${items.filter(Boolean).join('')}</div>`;
  const spacer = (h = 12) => `<div style="height:${h}px"></div>`;
  /** Section with a header (style 'title' = big header on tab roots, 'caption' = list header). */
  function section(title, content, o = {}) {
    const action = o.action ? `<span class="ui-sec-a ui-press" ${act(o.action)}>${esc(o.action.text)}${icon('chevronRight', 14)}</span>` : '';
    return `<section class="${cls('ui-sec', `ui-sec--${o.style || 'title'}`)}">${title ? `<div class="ui-sec-h"><h2>${esc(title)}</h2>${o.meta ? `<span class="ui-sec-m">${esc(o.meta)}</span>` : ''}${action}</div>` : ''}${content}${o.footer ? `<p class="ui-sec-f">${o.footer}</p>` : ''}</section>`;
  }
  /** Content card. o: {hero, tint:'car-carmine'|css colour, pad (default true), nav…} */
  function card(content, o = {}) {
    const tint = o.tint ? `--ui-tint:${o.tint.startsWith('var') || o.tint.startsWith('#') ? o.tint : `var(--dp-${o.tint})`};` : '';
    return `<div class="${cls('ui-card', o.hero && 'ui-card--hero', o.tint && 'ui-card--tint', o.pad === false && 'is-flush', isAct(o) && 'ui-press', o.cls)}" ${act(o)} style="${tint}${o.style || ''}">${content}</div>`;
  }

  // ---------------- lists ----------------
  /** iOS inset-grouped list / Android ListItems in a surfaceContainer section. */
  const list = (rows, o = {}) => `<div class="${cls('ui-list', o.cls)}" role="list">${rows.filter(Boolean).join('')}</div>`;
  /** {icon|lead(html), title, sub, value, trailing:'chevron'|'toggle'|'lock'|html, on (toggle), destructive, nav…} */
  function row(o = {}) {
    const lead = o.lead || (o.icon ? `<span class="ui-row-ic${o.iconTone ? ` is-${o.iconTone}` : ''}">${icon(o.icon, 20)}</span>` : '');
    let trail = '';
    if (o.trailing === 'toggle') trail = toggle(o.on, { action: o.action, arg: o.arg, label: o.title });
    else if (o.trailing === 'lock') trail = `<span class="ui-row-tr">${proBadge()}${icon('lock', 14)}</span>`;
    else if (o.trailing === 'chevron' || (isAct(o) && o.trailing == null)) trail = `<span class="ui-row-tr">${o.value != null ? `<span class="ui-row-v">${o.value}</span>` : ''}${icon('chevronRight', 16)}</span>`;
    else if (o.trailing) trail = `<span class="ui-row-tr">${o.trailing}</span>`;
    else if (o.value != null) trail = `<span class="ui-row-tr"><span class="ui-row-v">${o.value}</span></span>`;
    const a = o.trailing === 'toggle' ? '' : act(o);
    return `<div role="listitem" class="${cls('ui-row', o.destructive && 'is-destructive', o.sub && 'has-sub', isAct(o) && o.trailing !== 'toggle' && 'ui-press', o.cls)}" ${a}>${lead}<div class="ui-row-t"><b>${o.titleHtml || esc(o.title)}</b>${o.sub ? `<span>${o.subHtml ? o.sub : esc(o.sub)}</span>` : ''}</div>${trail}</div>`;
  }
  function toggle(on, o = {}) {
    return `<span class="${cls('ui-toggle', on && 'is-on', 'ui-press')}" role="switch" aria-checked="${!!on}" aria-label="${esc(o.label || '')}" tabindex="0" ${act({ action: o.action || 'toggle', arg: o.arg, haptic: on ? 'toggleOff' : 'toggleOn', noRole: true })}><i>${isIOS() ? '' : icon(on ? 'check' : 'close', 14)}</i></span>`;
  }
  /** Text field: iOS rounded field / Android OutlinedTextField. {label, value, placeholder, helper, error, type} */
  function textField(o = {}) {
    const v = o.value ?? '';
    return `<label class="${cls('ui-field', o.error && 'is-error', v && 'has-value', o.focused && 'is-focused')}"><span class="ui-field-l">${esc(o.label || '')}</span><span class="ui-field-in" contenteditable="${o.readonly ? 'false' : 'true'}" data-placeholder="${esc(o.placeholder || '')}" role="textbox" aria-label="${esc(o.label || o.placeholder || '')}">${esc(v)}</span>${o.error || o.helper ? `<span class="ui-field-h">${esc(o.error || o.helper)}</span>` : ''}</label>`;
  }
  function stepper(value, o = {}) {
    return `<span class="ui-stepper"><span class="ui-press" ${act({ action: o.action || 'step', arg: -1, label: 'Decrease' })}>${icon('minus', 18)}</span><b class="ui-num">${esc(value)}${o.unit ? `<span class="ui-unit">${esc(o.unit)}</span>` : ''}</b><span class="ui-press" ${act({ action: o.action || 'step', arg: 1, label: 'Increase' })}>${icon('plus', 18)}</span></span>`;
  }
  /** Radio list rows (single choice) — used by pickers. options [{id,label,sub}] */
  const radios = (options, sel, o = {}) => list(options.map((x) => row({ title: x.label, sub: x.sub, lead: `<span class="ui-radio ${x.id === sel ? 'is-on' : ''}"></span>`, action: o.action, arg: x.id, trailing: '' })), o);

  // ---------------- stats ----------------
  /** Stat tile (components.md §5). {label, value, unit, prov:'observed'|'derived'|'estimated', sub, est, unavailable} */
  function stat(o) {
    const v = o.unavailable ? '—' : o.value;
    return `<div class="${cls('ui-stat', o.est && 'is-est', o.locked && 'is-locked')}" aria-label="${esc(`${o.label}, ${v} ${o.unitSpoken || o.unit || ''}${o.prov ? '. ' + o.prov.charAt(0).toUpperCase() + o.prov.slice(1) : ''}`)}" data-prov="${o.prov || ''}"><span class="ui-stat-l">${esc(o.label)}</span><span class="ui-stat-v">${num(v, o.unavailable ? '' : o.unit, { est: o.est })}</span>${o.sub ? `<span class="ui-stat-s">${o.sub}</span>` : ''}${o.prov ? `<span class="ui-stat-p" title="${esc(o.prov)}">${I.provenance(o.prov, { size: 11 })}</span>` : ''}</div>`;
  }
  const stats = (items, o = {}) => `<div class="${cls('ui-stats', o.cls)}" style="--cols:${o.cols || 3}">${items.filter(Boolean).map(stat).join('')}</div>`;

  // ---------------- Android M3E shapes ----------------
  function cookie(n, R, cx, cy, amp = 0.075) {
    let d = '';
    for (let i = 0; i <= 180; i++) { const a = (i / 180) * Math.PI * 2; const r = R * (1 - amp + amp * Math.cos(n * a)); d += `${i ? 'L' : 'M'}${(cx + r * Math.cos(a - Math.PI / 2)).toFixed(2)} ${(cy + r * Math.sin(a - Math.PI / 2)).toFixed(2)}`; }
    return d + 'Z';
  }

  // ---------------- score ring (components.md §3) ----------------
  /** ring(score, size, {base, rec (old record), recNew, stroke, label, calibrating, unscorable, loading, animate}) */
  function ring(score, size = 120, o = {}) {
    const stroke = o.stroke || Math.max(6, Math.round(size * 0.085));
    const label = o.label ?? size >= 120;
    const r = (size - stroke) / 2 - 3, c = size / 2, C = 2 * Math.PI * r;
    const id = nid('rg');
    const f = o.unscorable || o.loading || score == null ? 0 : score / 100;
    const notch = (v, col, w, k) => { const a = (v / 100) * 2 * Math.PI - Math.PI / 2, len = stroke + 7; return `<line class="ui-ring-${k}" x1="${(c + (r - len / 2) * Math.cos(a)).toFixed(2)}" y1="${(c + (r - len / 2) * Math.sin(a)).toFixed(2)}" x2="${(c + (r + len / 2) * Math.cos(a)).toFixed(2)}" y2="${(c + (r + len / 2) * Math.sin(a)).toFixed(2)}" stroke="${col}" stroke-width="${w}" stroke-linecap="round"/>`; };
    const fs = size >= 160 ? size * 0.34 : size * 0.33;
    const txt = o.unscorable ? '—' : o.loading ? '··' : score;
    const lblTxt = o.unscorable ? 'NOT SCORED' : 'SMOOTHNESS';
    const andShape = !isIOS() && o.shape !== false ? `<svg class="ui-ring-shape" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" aria-hidden="true"><path d="${cookie(9, size / 2, c, c, 0.06)}" fill="var(--md-sys-color-primary-container)" opacity=".42"/></svg>` : '';
    const recNotch = o.rec != null ? (o.recNew ? notch(o.rec, 'var(--dp-record)', 4, 'rec is-new') : notch(o.rec, 'var(--dp-record)', 3.5, 'rec')) : '';
    return `<div class="${cls('ui-ring', o.unscorable && 'is-unscorable', o.loading && 'is-loading')}" style="width:${size}px;height:${size}px" role="img" aria-label="${esc(o.aria || (o.unscorable ? 'Not scored' : `Smoothness ${score}`))}" data-score="${score ?? ''}" data-c="${C.toFixed(2)}">${andShape}<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
      <defs><linearGradient id="${id}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="var(--dp-ref-glide-400)"/><stop offset=".6" stop-color="var(--dp-accent)"/><stop offset="1" stop-color="var(--dp-ref-glide-100)"/></linearGradient></defs>
      <circle class="ui-ring-track" cx="${c}" cy="${c}" r="${r}" fill="none" stroke="var(--dp-fill-strong)" stroke-width="${stroke}" ${o.unscorable ? 'stroke-dasharray="3 6"' : ''}/>
      ${f ? `<circle class="ui-ring-arc" cx="${c}" cy="${c}" r="${r}" fill="none" stroke="url(#${id})" stroke-width="${stroke}" stroke-linecap="round" stroke-dasharray="${(C * f).toFixed(2)} ${C.toFixed(2)}" transform="rotate(-90 ${c} ${c})"/>` : ''}
      ${o.base != null && !o.calibrating ? notch(o.base, 'var(--dp-text-2)', 2.5, 'base') : ''}${recNotch}
      <text class="ui-ring-num" x="${c}" y="${(c + fs * 0.35 - (label ? size * 0.045 : 0)).toFixed(1)}" text-anchor="middle" fill="var(--dp-text)" style="font: 650 ${fs.toFixed(1)}px var(--dp-family-numeric); font-stretch: var(--dp-num-stretch); font-variation-settings: var(--dp-num-variation); font-variant-numeric: tabular-nums; letter-spacing:-1px">${txt}</text>
      ${label ? `<text x="${c}" y="${(c + fs * 0.35 + size * 0.095).toFixed(1)}" text-anchor="middle" fill="var(--dp-text-2)" style="font: 600 ${Math.max(9, size * 0.068).toFixed(1)}px var(--dp-family-text); letter-spacing:.5px">${lblTxt}</text>` : ''}
    </svg></div>`;
  }
  /** Small score badge for trip rows (iOS: mini ring · Android: Cookie9Sided). */
  function scoreBadge(t) {
    if (!t.smoothness) return `<span class="ui-scorebadge is-none">${t.type === 'passenger' ? chip('Passenger', { icon: 'passenger', kind: 'outline' }) : `<span class="ui-ns">Not scored</span>`}</span>`;
    const s = t.smoothness, rec = s.record && s.record.isNew;
    const shape = isIOS() ? `<svg width="46" height="46" viewBox="0 0 46 46" aria-hidden="true"><circle cx="23" cy="23" r="20.5" fill="none" stroke="var(--dp-fill-strong)" stroke-width="3"/><circle cx="23" cy="23" r="20.5" fill="none" stroke="var(--dp-accent)" stroke-width="3" stroke-linecap="round" stroke-dasharray="${((s.score / 100) * 128.8).toFixed(1)} 128.8" transform="rotate(-90 23 23)"/></svg>`
      : `<svg width="46" height="46" viewBox="0 0 46 46" aria-hidden="true"><path d="${cookie(9, 23, 23, 23)}" fill="var(--md-sys-color-primary-container)"/></svg>`;
    const d = s.delta != null && !s.calibrating ? `<span class="ui-sb-d ${s.delta > 0 ? 'is-up' : ''}">${deltaGlyph(s.delta)}${Math.abs(s.delta)}</span>` : '';
    return `<span class="ui-scorebadge" aria-label="Smoothness ${s.score}${s.delta ? `, ${Math.abs(s.delta)} ${s.delta > 0 ? 'above' : 'below'} your usual` : ''}${rec ? '. New route record' : ''}">${shape}<b class="ui-num">${s.score}</b>${rec ? `<i class="ui-sb-rec">${I.svg('rosette', { size: 14 })}</i>` : ''}${d}</span>`;
  }

  // ---------------- quality / provenance ----------------
  /** Data-quality badge (F-DQ-04). size 'compact' (glyph+label) | 'full' (glyph+label+detail) | 'glyph'. */
  function qualityBadge(badge, o = {}) {
    if (o.size === 'glyph') return `<span class="ui-qglyph" aria-label="${badge} data">${I.quality(badge, { size: o.px || 13 })}</span>`;
    const detail = o.size === 'full' && o.detail ? ` <span class="ui-q-d">· ${esc(o.detail)}</span>` : '';
    return `<span class="${cls('ui-chip', 'ui-chip--q', o.glass && 'ui-glassy', isAct(o) && 'ui-press')}" ${act({ label: `${badge} data${o.detail ? '. ' + o.detail : ''}`, ...o })}>${I.quality(badge, { size: 14 })}${esc(o.text || (o.size === 'full' ? `${badge} data` : badge))}${detail}</span>`;
  }
  const qualityDetail = (t) => { const q = t.quality; return q.reason ? q.reason.replace(/:.*/, '').toLowerCase() : [q.phone === 'mounted' ? 'phone mounted' : `phone ${q.phone}`, q.imuHz ? `${q.imuHz} Hz` : null].filter(Boolean).join(' · '); };
  const provenance = (kind, size = 11) => `<span class="ui-prov" title="${esc(kind)}">${I.provenance(kind, { size })}</span>`;

  // ---------------- route glyph, trip row ----------------
  const glyph = (pts, s = 40, o = {}) => `<svg class="ui-glyph" width="${s}" height="${s}" viewBox="0 0 ${s} ${s}" aria-hidden="true"><path d="${U.glyphPath(pts, s, s, s * (o.pad ?? 0.14))}" fill="none" stroke="${o.color || 'var(--dp-data-route)'}" stroke-width="${o.width || Math.max(2, s / 18)}" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
  /** Trip card row (components.md §12). o: {ent, cars (bool, show car dot), isNew, nav (default S-TRP-01)} */
  function tripRow(t, o = {}) {
    const v = U.vehicle(t.vehicleId), ent = o.ent || D.entitlement.current;
    const locked = U.isLocked(t, ent);
    const passenger = t.type === 'passenger';
    const tags = [];
    if (t.interrupted) tags.push('<span class="ui-tag">Interrupted</span>');
    if (o.isNew) tags.push('<span class="ui-tag is-new"><i></i>New</span>');
    const aria = `${t.label.replace('→', 'to')}, ${t.partOfDay}, ${U.fmtDate(t.start, { day: 'numeric', month: 'long' })}. ${t.distanceKm} kilometres, ${Math.round(t.durationS / 60)} minutes. ${t.smoothness ? `Smoothness ${t.smoothness.score}.` : passenger ? 'Passenger, not scored.' : 'Not scored.'} ${t.quality.badge} data.${locked ? ' Replay and Analysis in Pro.' : ''}`;
    return `<div class="${cls('ui-trip', passenger && 'is-passenger', 'ui-press')}" ${act({ nav: o.nav || 'S-TRP-01', params: { trip: t.id }, zoom: true, label: aria, haptic: 'tap' })}>
      <span class="ui-trip-g">${glyph(t.glyph, 40)}</span>
      <span class="ui-trip-m"><span class="ui-trip-t">${esc(t.label)}${tags.join('')}</span><span class="ui-trip-s">${esc(t.partOfDay)} · ${U.fmtDate(t.start)}</span>
        <span class="ui-trip-n">${num(t.distanceKm, 'km')}<i>·</i>${num(Math.round(t.durationS / 60), 'min')}${qualityBadge(t.quality.badge, { size: 'glyph' })}${o.cars !== false && D.vehicles.length > 1 && v ? carDot(v) : ''}</span></span>
      ${scoreBadge(t)}${locked ? `<span class="ui-trip-lock" aria-hidden="true">${icon('lock', 13)}</span>` : ''}</div>`;
  }

  // ---------------- map canvas (plain-canvas map from DP_DATA; © OpenStreetMap contributors) ----------------
  /** map(trip, {w, h, pins, stops, maxPin, marker (t seconds), others, est, startEnd, select (event id), dim, attribution, radius, drawOn, label}) */
  function map(trip, o = {}) {
    const w = o.w || 370, h = o.h || 220, p = o.pad ?? 28, it = o.insetTop || 0, ib = o.insetBottom || 0;
    const pr0 = U.trackPath(trip, w, h - it - ib, p);
    const pr = { project: (la, ln) => { const [x, y] = pr0.project(la, ln); return [x, y + it]; } };
    const rows = trip.track.rows;
    let roads = '';
    if (o.others !== false) {
      const seen = new Set();
      for (const t of D.trips) {
        if (t.id === trip.id || seen.has(t.routeKey)) continue; seen.add(t.routeKey);
        let d = '', pen = false;
        for (const r of t.track.rows) { if (r[1] == null) { pen = false; continue; } const [x, y] = pr.project(r[1], r[2]); if (x < -80 || x > w + 80 || y < -80 || y > h + 80) { pen = false; continue; } d += `${pen ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`; pen = true; }
        if (d) roads += `<path d="${d}"/>`;
      }
    }
    const grid = Array.from({ length: Math.ceil(Math.max(w, h) / 36) + 1 }, (_, i) => `M${i * 36} 0V${h}M0 ${i * 36}H${w}`).join('');
    let main = '', est = '', exc = '', gaps = '', pen = false, last = null, prevQ = null;
    rows.forEach((r, i) => {
      if (r[1] == null) { pen = false; prevQ = 2; return; }
      const [x, y] = pr.project(r[1], r[2]); const pt = `${x.toFixed(1)} ${y.toFixed(1)}`;
      if (prevQ === 2 && last) gaps += `M${last}L${pt}`;
      if (r[7] === 1) est += `${est ? 'L' : 'M'}${pt}`;
      if (r[7] === 3) exc += `${prevQ === 3 ? 'L' : 'M'}${pt}`;
      if (r[7] !== 1 || (rows[i + 1] && rows[i + 1][7] !== 1)) { main += `${pen ? 'L' : 'M'}${pt}`; pen = true; }
      last = pt; prevQ = r[7];
    });
    let pins = '';
    if (o.stops !== false && o.pins !== false) for (const s of trip.stops.slice(0, 6)) { const [x, y] = pr.project(s.lat, s.lng); pins += `<g class="ui-mpin is-stop" transform="translate(${(x - 9).toFixed(1)} ${(y - 7).toFixed(1)})">${I.stopPin({ size: 14 })}</g>`; }
    if (o.maxPin !== false && o.pins !== false && trip.maxAt && trip.maxAt.lat) { const [x, y] = pr.project(trip.maxAt.lat, trip.maxAt.lng); pins += `<g class="ui-mpin is-max" transform="translate(${(x - 9).toFixed(1)} ${(y - 17).toFixed(1)})">${I.maxPin({ size: 18 })}</g>`; }
    if (o.pins !== false) trip.events.forEach((e, i) => { const [x, y] = pr.project(e.lat, e.lng); const ps = o.pinSize || 26; pins += `<g class="ui-mpin is-event${o.select && o.select !== e.id ? ' is-dim' : ''}" data-i="${i}" style="transform-origin:${x.toFixed(1)}px ${y.toFixed(1)}px" ${o.pinAction ? `data-action="${o.pinAction}" data-arg="${e.id}"` : ''}><g transform="translate(${(x - ps / 2).toFixed(1)} ${(y - ps / 2).toFixed(1)})">${I.pin(e.type, { size: ps, needed: e.needed, selected: o.select === e.id })}</g></g>`; });
    let se = '';
    if (o.startEnd !== false) {
      const first = rows.find((r) => r[1] != null), lastR = [...rows].reverse().find((r) => r[1] != null);
      const [sx, sy] = pr.project(first[1], first[2]), [ex, ey] = pr.project(lastR[1], lastR[2]);
      se = `<circle cx="${sx}" cy="${sy}" r="5" fill="var(--dp-data-map-land)" stroke="var(--dp-data-route)" stroke-width="2.4" opacity=".8"/><circle cx="${ex}" cy="${ey}" r="6" fill="var(--dp-data-route)" stroke="var(--dp-data-route-casing)" stroke-width="2"/>`;
    }
    let mk = '';
    if (o.marker != null) { const s = U.sampleAt(trip, o.marker); if (s.lat != null) { const [x, y] = pr.project(s.lat, s.lng); mk = `<g class="ui-marker" transform="translate(${x.toFixed(1)} ${y.toFixed(1)})"><circle r="17" fill="var(--dp-accent)" opacity=".18"/><circle r="8" fill="var(--dp-data-marker)" stroke="var(--dp-accent)" stroke-width="3"/></g>`; } }
    const label = o.label || `Map of your drive, ${trip.distanceKm} kilometres from ${trip.from} to ${trip.to}. ${trip.events.length} events, ${trip.stops.length} stops.`;
    return `<div class="${cls('ui-map', o.cls)}" style="width:${o.fluid ? '100%' : w + 'px'};height:${h}px;${o.radius != null ? `border-radius:${o.radius}px;` : ''}${o.style || ''}" role="img" aria-label="${esc(label)}" ${o.nav ? act({ nav: o.nav, params: o.params, noRole: true }) : ''}>
      <svg width="100%" height="100%" viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMidYMid slice">
        <rect width="${w}" height="${h}" fill="var(--dp-data-map-land)"/><path d="${grid}" stroke="var(--dp-data-grid)" stroke-width="1" fill="none"/>
        <g fill="none" stroke="var(--dp-data-map-road)" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round">${roads}</g>
        <g class="ui-map-route${o.dim ? ' is-dim' : ''}">
          <path d="${main}" fill="none" stroke="var(--dp-data-route-casing)" stroke-width="7.5" stroke-linecap="round" stroke-linejoin="round" opacity=".75"/>
          <path class="ui-route" d="${main}" fill="none" stroke="var(--dp-data-route)" stroke-width="3.8" stroke-linecap="round" stroke-linejoin="round"/>
          ${o.est !== false && est ? `<path class="ui-route-est" d="${est}" fill="none" stroke="var(--dp-data-route)" stroke-width="3.6" stroke-dasharray="4 4" opacity=".6" stroke-linecap="round"/>` : ''}
          ${exc ? `<path d="${exc}" fill="none" stroke="var(--dp-data-gap-stroke)" stroke-width="4" stroke-dasharray="2 4" stroke-linecap="round"/>` : ''}
          ${gaps ? `<path class="ui-route-gap" d="${gaps}" fill="none" stroke="var(--dp-data-gap-stroke)" stroke-width="2.4" stroke-dasharray="1 6" stroke-linecap="round"/>` : ''}
          ${se}</g>
        <g class="ui-map-pins">${pins}</g>${mk}
      </svg>${o.overlay || ''}${o.attribution !== false ? `<span class="ui-map-attr">© OpenStreetMap contributors</span>` : ''}</div>`;
  }

  // ---------------- charts ----------------
  function sparkline(values, o = {}) {
    const w = o.w || 120, h = o.h || 36, lo = o.lo ?? Math.min(...values), hi = o.hi ?? Math.max(...values);
    const x = (i) => (i / (values.length - 1)) * w, y = (v) => h - 3 - ((v - lo) / (hi - lo || 1)) * (h - 6);
    const d = values.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${y(v).toFixed(1)}`).join('');
    const pk = o.peak != null ? `<circle cx="${x(o.peak).toFixed(1)}" cy="${y(values[o.peak]).toFixed(1)}" r="3" fill="${o.color || 'var(--dp-data-long-g)'}"/>` : '';
    const zero = o.zero ? `<line x1="0" x2="${w}" y1="${y(0).toFixed(1)}" y2="${y(0).toFixed(1)}" stroke="var(--dp-data-axis)"/>` : '';
    return `<svg class="ui-spark" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-label="${esc(o.label || 'Trend')}">${zero}${o.area ? `<path d="${d}L${w} ${h}L0 ${h}Z" fill="${o.color || 'var(--dp-accent)'}" opacity=".14"/>` : ''}<path d="${d}" fill="none" stroke="${o.color || 'var(--dp-data-long-g)'}" stroke-width="${o.stroke || 2}" stroke-linecap="round" stroke-linejoin="round" ${o.dash ? `stroke-dasharray="${o.dash}"` : ''}/>${pk}</svg>`;
  }
  /** Evidence trace (−10 s … +10 s) for an event (components.md §9.3). */
  function evidenceTrace(e, w = 340, h = 110) {
    if (!e.evidence) return `<div class="ui-trace-removed">Detailed trace not kept</div>`;
    const n = e.evidence.kmh.length, x = (i) => (i / (n - 1)) * w;
    const sp = e.evidence.kmh.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${(32 - (v / 110) * 28).toFixed(1)}`).join('');
    const gy = (v) => 44 + (h - 58) / 2 - (v / 0.5) * ((h - 58) / 2);
    const lo = e.evidence.lonG.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${gy(v).toFixed(1)}`).join('');
    const la = e.evidence.latG.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${gy(v).toFixed(1)}`).join('');
    const m = e.evidence.markers, mx = (t) => x(((t + 10) / 20) * (n - 1));
    const col = `var(--dp-data-event-${e.type})`;
    const lab = (t, l, anchor) => `<text x="${mx(t).toFixed(1)}" y="${h - 1}" text-anchor="${anchor}" fill="var(--dp-text-3)" style="font:600 9px var(--dp-family-text)">${l}</text>`;
    return `<svg class="ui-trace" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-label="${esc(`Speed and G from 10 seconds before to 10 seconds after. Speed went from ${e.speedBeforeKmh} to ${e.speedAfterKmh} kilometres per hour. Peak ${e.peakG} g.`)}"><line x1="0" x2="${w}" y1="${gy(0)}" y2="${gy(0)}" stroke="var(--dp-data-axis)"/>
      <rect x="${mx(m.start).toFixed(1)}" y="0" width="${Math.max(2, mx(m.end) - mx(m.start)).toFixed(1)}" height="${h - 13}" fill="${col}" opacity=".14" rx="3"/><line x1="${mx(0)}" x2="${mx(0)}" y1="0" y2="${h - 13}" stroke="${col}" stroke-width="1.2" stroke-dasharray="2 3"/>
      ${lab(-10, '−10 s', 'start')}${lab(m.start, 'Start', 'end')}${lab(0, 'Peak', 'middle')}${lab(m.end, 'End', 'start')}${lab(10, '+10 s', 'end')}
      <path d="${sp}" fill="none" stroke="var(--dp-data-speed)" stroke-width="1.6"/><path d="${la}" fill="none" stroke="var(--dp-data-lat-g)" stroke-width="1.8" stroke-dasharray="6 3"/><path d="${lo}" fill="none" stroke="var(--dp-data-long-g)" stroke-width="2"/></svg>`;
  }
  function lanePath(rows, idx, w, y0, h, lo, hi, t0, t1, T) {
    let d = '', pen = false;
    for (const r of rows) { if (r[0] < t0 || r[0] > t1) continue; if (r[idx] == null) { pen = false; continue; } const x = (r[0] / T) * w; const y = y0 + h - ((Math.max(lo, Math.min(hi, r[idx])) - lo) / (hi - lo)) * h; d += `${pen ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`; pen = true; }
    return d;
  }
  /** Synced timeline (components.md §7): Speed / Long G / Lat G lanes + event lane + cursor (0..1). */
  function timeline(trip, w = 360, o = {}) {
    const rows = trip.track.rows, T = trip.durationS, k = o.xl ? 1.5 : 1;
    const lanes = [{ k: 'Speed', idx: 3, lo: 0, hi: 120, h: 40 * k, col: 'var(--dp-data-speed)', dash: '', r: '0–120 km/h' },
      { k: 'Longitudinal G', idx: 4, lo: -0.6, hi: 0.6, h: 60 * k, col: 'var(--dp-data-long-g)', dash: '', zero: true, r: '±0.6 g' },
      { k: 'Lateral G', idx: 5, lo: -0.6, hi: 0.6, h: 60 * k, col: 'var(--dp-data-lat-g)', dash: '6 3', zero: true, r: '±0.6 g' }];
    const tx = (t) => (t / T) * w, q = trip.quality.spans;
    let y = 0, out = '', labels = '';
    const H = lanes.reduce((a, l) => a + l.h + 18, 0);
    for (const sp of q.excluded) out += `<rect x="${tx(sp[0])}" y="0" width="${tx(sp[1]) - tx(sp[0])}" height="${H}" fill="url(#ui-hatch)"/>`;
    for (const sp of q.gap) out += `<rect x="${tx(sp[0])}" y="0" width="${Math.max(3, tx(sp[1]) - tx(sp[0]))}" height="${H}" fill="var(--dp-fill)"/>`;
    for (const l of lanes) {
      const top = y + 14;
      labels += `<text x="0" y="${y + 10}" fill="var(--dp-text-2)" style="font:600 10px var(--dp-family-text)">${l.k}</text><text x="${w}" y="${y + 10}" text-anchor="end" fill="var(--dp-text-3)" style="font:600 9px var(--dp-family-numeric)">${l.r}</text>`;
      if (l.zero) out += `<line x1="0" x2="${w}" y1="${top + l.h / 2}" y2="${top + l.h / 2}" stroke="var(--dp-data-axis)"/>`;
      out += `<line x1="0" x2="${w}" y1="${top + l.h}" y2="${top + l.h}" stroke="var(--dp-data-grid)"/>`;
      const est = q.estimated[0];
      out += `<path d="${lanePath(rows, l.idx, w, top, l.h, l.lo, l.hi, est ? est[1] : 0, T, T)}" fill="none" stroke="${l.col}" stroke-width="${l.idx === 3 ? 1.6 : 1.4}" stroke-dasharray="${l.dash}" stroke-linejoin="round"/>`;
      if (est) out += `<path d="${lanePath(rows, l.idx, w, top, l.h, l.lo, l.hi, 0, est[1] + 6, T)}" fill="none" stroke="${l.col}" stroke-width="1.6" stroke-dasharray="4 4" opacity=".6"/>`;
      y += l.h + 18;
    }
    for (const e of trip.events) out += `<g transform="translate(${tx(e.t) - 7} ${y - 2})">${I.pin(e.type, { size: 14, needed: e.needed })}</g>`;
    const cx = (o.cursor ?? 0.4) * w;
    out += `<g class="ui-tl-cursor" transform="translate(${cx.toFixed(1)} 0)"><line x1="0" x2="0" y1="0" y2="${y + 12}" stroke="var(--dp-data-cursor)" stroke-width="1.5"/><circle cx="0" cy="${y + 14}" r="4" fill="var(--dp-data-cursor)"/></g>`;
    return `<svg class="ui-timeline" width="${w}" height="${y + 20}" viewBox="0 0 ${w} ${y + 20}" style="overflow:visible" role="img" aria-label="${esc(o.label || `Timeline, ${Math.round(T / 60)} minutes. Speed peaked at ${trip.maxKmh} kilometres per hour. ${trip.events.length} events.`)}"><defs><pattern id="ui-hatch" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="2" height="6" fill="var(--dp-data-excluded-hatch)"/></pattern></defs>${out}${labels}</svg>`;
  }
  /** Friction circle (components.md §6). o: {dot (t seconds), filter:'brake'|'accel'|'corner', rm} */
  function gg(trip, s = 160, o = {}) {
    const c = s / 2, R = s / 2 - 12, k = R / 0.6;
    let out = '';
    for (let g = 0.1; g <= 0.61; g += 0.1) out += `<circle cx="${c}" cy="${c}" r="${(g * k).toFixed(1)}" fill="none" stroke="var(--dp-data-gg-ring)" stroke-width="${Math.abs(g - 0.3) < 0.01 || Math.abs(g - 0.5) < 0.01 ? 1.2 : 0.8}"/>`;
    out += `<line x1="${c - R}" x2="${c + R}" y1="${c}" y2="${c}" stroke="var(--dp-data-axis)"/><line y1="${c - R}" y2="${c + R}" x1="${c}" x2="${c}" stroke="var(--dp-data-axis)"/>`;
    if (!trip.gg) return `<svg class="ui-gg" width="${s}" height="${s}" viewBox="0 0 ${s} ${s}" role="img" aria-label="No scorable motion">${out}<text x="${c}" y="${c - 6}" text-anchor="middle" fill="var(--dp-text-2)" style="font:600 11px var(--dp-family-text)">No scorable motion</text></svg>`;
    for (const [la, lo] of trip.gg.points) out += `<circle cx="${(c + la * k).toFixed(1)}" cy="${(c - lo * k).toFixed(1)}" r="1.6" fill="var(--dp-data-gg-cloud)" opacity=".32"/>`;
    let d = ''; trip.gg.envelope.radiiG.forEach((r, i) => { const a = ((i * 15 + 7.5) * Math.PI) / 180; d += `${i ? 'L' : 'M'}${(c + Math.cos(a) * r * k).toFixed(1)} ${(c - Math.sin(a) * r * k).toFixed(1)}`; });
    out += `<path class="ui-gg-env" d="${d}Z" fill="none" stroke="var(--dp-data-gg-envelope)" stroke-width="1.5" stroke-linejoin="round"/>`;
    if (o.dot !== false) { const p = U.sampleAt(trip, o.dot ?? (trip.events[0] ? trip.events[0].t : trip.durationS / 3)); const dx = c + (p.latG || 0) * k, dy = c - (p.lonG || 0) * k; out += `<g class="ui-gg-dot" transform="translate(${dx.toFixed(1)} ${dy.toFixed(1)})"><circle r="8" fill="var(--dp-data-gg-dot)" opacity=".2"/><circle r="4.5" fill="var(--dp-data-gg-dot)"/></g>`; }
    out += `<text x="${c + 0.5 * k * 0.72}" y="${c - 0.5 * k * 0.72}" fill="var(--dp-text-3)" style="font:600 9px var(--dp-family-numeric)">0.5 g</text><text x="${c}" y="10" text-anchor="middle" fill="var(--dp-text-3)" style="font:600 9px var(--dp-family-text)">ACCEL</text><text x="${c}" y="${s - 2}" text-anchor="middle" fill="var(--dp-text-3)" style="font:600 9px var(--dp-family-text)">BRAKE</text>`;
    return `<svg class="ui-gg" width="${s}" height="${s}" viewBox="0 0 ${s} ${s}" role="img" aria-label="${esc(o.label || 'Friction circle. Most of your inputs stayed within 0.2 g.')}">${out}</svg>`;
  }
  /** Replay scrubber with event ticks, stops, elevation mini-profile (components.md §8). frac 0..1 */
  function scrubber(trip, w = 340, frac = 0) {
    const T = trip.durationS, rows = trip.track.rows, el = rows.map((r) => r[6]), lo = Math.min(...el), hi = Math.max(...el);
    let ed = 'M0 34'; rows.forEach((r) => { ed += `L${((r[0] / T) * w).toFixed(1)} ${(34 - ((r[6] - lo) / (hi - lo || 1)) * 14).toFixed(1)}`; }); ed += `L${w} 34Z`;
    const ticks = trip.events.map((e) => `<g transform="translate(${((e.t / T) * w - 6).toFixed(1)} -15)">${I.pin(e.type, { size: 12, needed: e.needed })}</g>`).join('');
    const stops = trip.stops.map((st) => `<rect x="${((st.t / T) * w - 3).toFixed(1)}" y="-1" width="6" height="6" rx="3" fill="var(--dp-text-3)"/>`).join('');
    const gaps = trip.quality.spans.gap.map((g) => `<rect x="${(g[0] / T) * w}" y="0" width="${Math.max(3, ((g[1] - g[0]) / T) * w)}" height="4" fill="var(--dp-bg-grouped)"/>`).join('');
    const est = trip.quality.spans.estimated.map((g) => `<line x1="0" x2="${((g[1] / T) * w).toFixed(1)}" y1="2" y2="2" stroke="var(--dp-accent)" stroke-width="4" stroke-dasharray="3 3"/>`).join('');
    return `<svg class="ui-scrubber" width="${w}" height="70" viewBox="0 -18 ${w} 70" style="overflow:visible;display:block" role="slider" aria-label="Replay position" aria-valuenow="${Math.round(frac * T)}" aria-valuemax="${T}"><rect x="0" y="0" width="${w}" height="4" rx="2" fill="var(--dp-fill-strong)"/><rect class="ui-scr-played" x="0" y="0" width="${(w * frac).toFixed(1)}" height="4" rx="2" fill="var(--dp-accent)"/>${est}${gaps}${stops}${ticks}
      <circle class="ui-scr-thumb" cx="${(w * frac).toFixed(1)}" cy="2" r="8" fill="var(--dp-data-marker)" stroke="var(--dp-accent)" stroke-width="2.5"/>
      <g transform="translate(0 14)"><path d="${ed}" fill="var(--dp-data-elevation-fill)" stroke="var(--dp-data-elevation)" stroke-width="1.2"/></g>
      <text x="0" y="50" fill="var(--dp-text-3)" style="font:600 9px var(--dp-family-numeric)">0:00</text><text x="${w}" y="50" text-anchor="end" fill="var(--dp-text-3)" style="font:600 9px var(--dp-family-numeric)">${U.fmtClock(T)} · ↗ ${trip.elevGainM} m ↘ ${trip.elevLossM} m</text></svg>`;
  }

  // ---------------- banners, states ----------------
  /** kind: info|attention|critical|passenger|positive · {icon, title, text, action:{text, nav…}} */
  function banner(o = {}) {
    const ic = o.icon || { info: 'info', attention: 'warn', critical: 'warn', passenger: 'passenger', positive: 'check' }[o.kind || 'info'];
    const a = o.action ? `<span class="ui-banner-a ui-press" ${act(o.action)}>${esc(o.action.text)}</span>` : '';
    return `<div class="${cls('ui-banner', `ui-banner--${o.kind || 'info'}`, o.cls)}" role="status">${ic.startsWith('<') ? ic : icon(ic, 20)}<div class="ui-banner-t">${o.title ? `<b>${esc(o.title)}</b>` : ''}${o.text ? `<span>${o.html ? o.text : esc(o.text)}</span>` : ''}</div>${a}</div>`;
  }
  /** Empty state (components.md §21). {art (html) | icon, title, text, action (button opts with .text)} */
  function empty(o = {}) {
    const art = o.art || `<div class="ui-empty-ic">${icon(o.icon || 'drives', 40)}</div>`;
    return `<div class="ui-empty">${art}<h3>${esc(o.title || '')}</h3>${o.text ? `<p>${esc(o.text)}</p>` : ''}${o.action ? button(o.action.text, { kind: 'filled', ...o.action }) : ''}${o.secondary ? button(o.secondary.text, { kind: 'text', ...o.secondary }) : ''}</div>`;
  }
  /** Two lines merging: the empty-state illustration for routes/drives. */
  const artMerge = (s = 120) => `<svg width="${s}" height="${s * 0.62}" viewBox="0 0 120 74" aria-hidden="true"><path d="M6 60C30 60 34 22 60 22s30 30 54 30" fill="none" stroke="var(--dp-accent)" stroke-width="3" stroke-linecap="round" opacity=".45"/><path d="M6 68C34 66 38 30 62 30s28 22 52 22" fill="none" stroke="var(--dp-accent)" stroke-width="3" stroke-linecap="round"/><circle cx="114" cy="52" r="5" fill="var(--dp-accent)"/></svg>`;
  /** Skeleton blocks. kind: 'lines' | 'tripRow' | 'card' | 'stats' | number of lines */
  function skeleton(kind = 'lines', n = 3) {
    if (kind === 'tripRow') return Array.from({ length: n }, () => `<div class="ui-trip is-skel"><span class="ui-skel" style="width:48px;height:48px;border-radius:12px"></span><span style="flex:1;display:grid;gap:7px"><span class="ui-skel" style="width:62%"></span><span class="ui-skel" style="width:40%"></span><span class="ui-skel" style="width:52%"></span></span><span class="ui-skel" style="width:44px;height:44px;border-radius:50%"></span></div>`).join('');
    if (kind === 'stats') return `<div class="ui-stats" style="--cols:3">${Array.from({ length: n }, () => '<div class="ui-stat"><span class="ui-skel" style="width:50%"></span><span class="ui-skel" style="width:70%;height:20px;margin-top:8px"></span></div>').join('')}</div>`;
    if (kind === 'card') return `<div class="ui-card"><span class="ui-skel" style="width:40%"></span><span class="ui-skel" style="width:90%;margin-top:10px"></span><span class="ui-skel" style="width:70%;margin-top:8px"></span></div>`;
    if (kind === 'block') return `<span class="ui-skel" style="display:block;height:${n}px;border-radius:var(--dp-radius-card)"></span>`;
    return `<div style="display:grid;gap:8px">${Array.from({ length: typeof kind === 'number' ? kind : n }, (_, i) => `<span class="ui-skel" style="width:${[78, 92, 64, 84][i % 4]}%"></span>`).join('')}</div>`;
  }
  function error(o = {}) {
    return `<div class="ui-errstate"><span class="ui-errstate-ic">${icon('warn', 26)}</span><h3>${esc(o.title || 'Something went wrong.')}</h3>${o.text ? `<p>${esc(o.text)}</p>` : ''}${button(o.actionText || 'Try again', { kind: 'tonal', icon: 'refresh', ...(o.action || { toast: 'Retrying…' }) })}</div>`;
  }
  /** Loader: iOS = the Loop (dot orbits) · Android = M3E shape-morph loading indicator. */
  function loader(o = {}) {
    const s = o.size || 48;
    if (isIOS()) return `<span class="ui-loader ui-loader--loop" style="width:${s}px;height:${s}px" role="progressbar" aria-label="${esc(o.label || 'Loading')}">${I.loop({ size: s, color: 'var(--dp-accent)', dot: 'var(--dp-text)' })}</span>`;
    return `<span class="ui-loader ui-loader--m3" style="width:${s}px;height:${s}px" role="progressbar" aria-label="${esc(o.label || 'Loading')}"><svg viewBox="0 0 48 48" width="${s}" height="${s}"><path d="${cookie(7, 17, 24, 24, 0.12)}" fill="var(--md-sys-color-primary)"/></svg></span>`;
  }
  /** Locked panel: the user's OWN content blurred (14 px) + lock + value sentence + See plans (components.md §15.2). */
  function lockPanel(inner, o = {}) {
    return `<div class="${cls('ui-lock', o.compact && 'is-compact', o.cls)}" ${act({ nav: o.nav || 'S-SUB-03', params: o.params, label: o.text })}><div class="ui-lock-in dp-locked" aria-hidden="true">${inner}</div><div class="ui-lock-over">${icon('lock', o.compact ? 16 : 20)}<b>${esc(o.text || 'Keep exploring every drive with Pro.')}</b>${o.compact ? '' : button(o.cta || 'See plans', { kind: 'filled', size: 's', nav: o.nav || 'S-SUB-03', params: o.params })}</div></div>`;
  }
  /** Coaching tip card (components.md §18). {text, event, label} */
  function tipCard(o = {}) {
    const e = o.event;
    const spark = e && e.evidence ? sparkline(e.evidence.lonG, { w: 96, h: 40, zero: true, peak: 50, color: 'var(--dp-data-long-g)', label: 'Braking trace around the moment' }) : '';
    return `<div class="ui-tip">${o.label !== false ? `<span class="ui-tip-k">${esc(o.label || 'One thing for next time')}</span>` : ''}<div class="ui-tip-b"><p>${esc(o.text)}</p>${spark}</div>${e ? `<span class="ui-tip-a ui-press" ${act({ nav: 'S-RPL-02', params: { trip: o.tripId, event: e.id } })}>See the moment ${icon('chevronRight', 14)}</span>` : ''}</div>`;
  }
  /** Event chip row (components.md §9.2). */
  function eventRow(e, o = {}) {
    const meta = `${e.peakG.toFixed(2)} g · ${e.durationS} s`;
    return `<div class="${cls('ui-event', e.needed && 'is-needed', 'ui-press')}" ${act({ nav: 'S-RPL-02', params: { trip: o.tripId, event: e.id }, haptic: 'tap', label: `${I.eventLabel(e.type)} at ${e.place}. Peak ${e.peakG} g.${e.needed ? ' Marked as needed, not counted.' : ''}` })}>${I.pin(e.type, { size: 28, needed: e.needed })}<span class="ui-event-t"><b>${I.eventLabel(e.type)}${e.needed ? '<span class="ui-tag">Needed</span>' : ''}${e.confidence === 'Low' ? '<span class="ui-tag is-q">Low confidence</span>' : ''}</b><span>${esc(e.place.charAt(0).toUpperCase() + e.place.slice(1))}${o.time ? ` · ${esc(o.time)}` : ''}</span></span><span class="ui-event-m ${e.needed ? 'is-struck' : ''}">${meta}</span>${icon('chevronRight', 16)}</div>`;
  }

  // ---------------- overlays (the engine wraps sheet/dialog presentations; these build the content) -------------
  /** Sheet header: title + optional close/done. Use as the first thing a sheet screen renders. */
  function sheetHeader(title, o = {}) {
    const close = o.close === false ? '' : iconButton('close', { back: true, label: 'Close', glass: true, cls: 'ui-sheet-x' });
    return `<div class="ui-sheet-h">${o.leading || ''}<h2>${o.icon ? o.icon : ''}${esc(title)}</h2>${o.sub ? `<p>${esc(o.sub)}</p>` : ''}${close}</div>`;
  }
  /** Alert / M3 dialog box. {title, text, icon, actions:[{text, kind:'default'|'cancel'|'destructive'|'primary', …act}]} */
  function dialog(o = {}) {
    const acts = (o.actions || []).map((a) => `<span class="${cls('ui-dlg-b', `is-${a.kind || 'default'}`, 'ui-press')}" ${act(a)}>${esc(a.text)}</span>`).join('');
    return `<div class="ui-dialog" role="alertdialog" aria-label="${esc(o.title || '')}">${o.icon && !isIOS() ? `<span class="ui-dlg-ic">${icon(o.icon, 24)}</span>` : ''}<h2>${esc(o.title || '')}</h2>${o.text ? `<p>${esc(o.text)}</p>` : ''}${o.body || ''}<div class="ui-dlg-acts ${(o.actions || []).length > 2 || o.stacked ? 'is-stacked' : ''}">${acts}</div></div>`;
  }

  /** Menu (iOS glass Menu / M3E DropdownMenu). Toggle it with iconButton('more', {menu:'id'}).
      items: [{text, icon, nav, params, action, destructive, trailing (html)}] */
  function menu(id, items, o = {}) {
    return `<div class="ui-menu" id="${esc(id)}" role="menu" hidden style="${o.style || ''}">${items.filter(Boolean).map((it) => it === '-' ? '<i class="ui-menu-sep"></i>' : `<span role="menuitem" class="${cls('ui-menu-i', it.destructive && 'is-destructive', 'ui-press')}" ${act({ ...it, noRole: true })}>${it.icon ? icon(it.icon, 18) : ''}<span>${esc(it.text)}</span>${it.trailing || ''}</span>`).join('')}</div>`;
  }

  // ---------------- system mocks (non-interactive frames, PRD §4 note) ----------------
  const SYS = {
    ios: {
      location: { title: 'Allow “DrivePerf” to use your location?', text: 'DrivePerf uses your location to record the route of your drives.', map: true, buttons: ['Allow Once', 'Allow While Using App', 'Don’t Allow'] },
      always: { title: 'Allow “DrivePerf” to also use your location even when you are not using the app?', text: 'DrivePerf uses your location in the background only to record the route of drives it detects.', map: true, buttons: ['Keep Only While Using', 'Change to Always Allow'] },
      motion: { title: '“DrivePerf” Would Like to Access Your Motion & Fitness Activity', text: 'DrivePerf uses motion data to tell when you start and stop driving.', buttons: ['Don’t Allow', 'Allow'], row: true },
      notifications: { title: '“DrivePerf” Would Like to Send You Notifications', text: 'Notifications may include alerts, sounds and icon badges. These can be configured in Settings.', buttons: ['Don’t Allow', 'Allow'], row: true },
    },
    android: {
      location: { icon: 'pin', title: 'Allow DrivePerf to access this device’s location?', choice: true, buttons: ['While using the app', 'Only this time', 'Don’t allow'] },
      motion: { icon: 'drives', title: 'Allow DrivePerf to access your physical activity?', buttons: ['Allow', 'Don’t allow'] },
      notifications: { icon: 'bell', title: 'Allow DrivePerf to send you notifications?', buttons: ['Allow', 'Don’t allow'] },
    },
  };
  /** System permission prompt mock over a dimmed screen (non-interactive). kind: location|always|motion|notifications.
      Render it as the LAST child of the primer's page() (it positions itself over the whole screen). */
  function sysPermission(kind, o = {}) {
    const p = plat(), s = { ...(SYS[p][kind] || SYS[p].location), ...o };
    const hi = o.highlight;
    if (p === 'ios') {
      const mapArt = s.map ? `<div class="ui-sys-map"><svg width="100%" height="100%" viewBox="0 0 240 120" preserveAspectRatio="xMidYMid slice"><rect width="240" height="120" fill="#cfd8cf"/><path d="M0 80C60 70 80 30 140 40s80 50 100 40" stroke="#fff" stroke-width="8" fill="none"/><path d="M90 0L110 120M0 30H240" stroke="#ffffffb0" stroke-width="4"/><circle cx="128" cy="58" r="16" fill="#0a84ff33"/><circle cx="128" cy="58" r="6" fill="#0a84ff" stroke="#fff" stroke-width="2"/></svg><span>Precise: On</span></div>` : '';
      return `<div class="ui-sys" aria-hidden="true"><div class="ui-sys-alert"><b>${esc(s.title)}</b><p>${esc(s.text)}</p>${mapArt}<div class="ui-sys-btns ${s.row ? 'is-row' : ''}">${s.buttons.map((b, i) => `<span class="${hi === i ? 'is-hi' : ''}">${esc(b)}</span>`).join('')}</div></div><span class="ui-sys-tag">System prompt · mock</span></div>`;
    }
    const choice = s.choice ? `<div class="ui-sys-choice"><div class="is-on"><svg viewBox="0 0 64 64" width="64" height="64"><circle cx="32" cy="32" r="30" fill="var(--md-sys-color-primary-container)"/><circle cx="32" cy="32" r="7" fill="var(--md-sys-color-primary)"/></svg><span>Precise</span></div><div><svg viewBox="0 0 64 64" width="64" height="64"><circle cx="32" cy="32" r="30" fill="var(--md-sys-color-surface-container-highest)"/><circle cx="32" cy="32" r="18" fill="none" stroke="var(--md-sys-color-outline)" stroke-dasharray="3 3"/></svg><span>Approximate</span></div></div>` : '';
    return `<div class="ui-sys" aria-hidden="true"><div class="ui-sys-dlg"><span class="ui-sys-ic">${icon(s.icon || 'info', 26)}</span><b>${esc(s.title)}</b>${choice}<div class="ui-sys-btns">${s.buttons.map((b, i) => `<span class="${hi === i ? 'is-hi' : ''}">${esc(b)}</span>`).join('')}</div></div><span class="ui-sys-tag">System dialog · mock</span></div>`;
  }
  /** OS Settings page mock (non-interactive). {title, back, groups:[{header, rows:[{label, value, radio:bool, on, toggle, hi}] , footer}]} */
  function sysSettings(o = {}) {
    const groups = (o.groups || []).map((g) => `${g.header ? `<div class="ui-syss-h">${esc(g.header)}</div>` : ''}<div class="ui-syss-g">${g.rows.map((r) => `<div class="ui-syss-r ${r.hi ? 'is-hi' : ''}">${r.radio ? `<i class="ui-radio ${r.on ? 'is-on' : ''}"></i>` : ''}<span>${esc(r.label)}${r.sub ? `<small>${esc(r.sub)}</small>` : ''}</span>${r.toggle != null ? `<i class="ui-toggle ${r.toggle ? 'is-on' : ''}"><i></i></i>` : r.value ? `<em>${esc(r.value)}</em>` : ''}${r.check ? icon('check', 18) : ''}</div>`).join('')}</div>${g.footer ? `<div class="ui-syss-f">${esc(g.footer)}</div>` : ''}`).join('');
    return `<div class="ui-syss" aria-hidden="true"><div class="ui-syss-bar">${backGlyph()}<span>${esc(o.back || (isIOS() ? 'Settings' : ''))}</span></div><h1>${esc(o.title || 'Location')}</h1>${groups}<span class="ui-sys-tag">${isIOS() ? 'iOS Settings' : 'Android Settings'} · mock</span></div>`;
  }

  // ---------------- OS surfaces (presentation 'live') ----------------
  /** Lock screen with wallpaper, clock and stacked content (Live Activity, notifications). */
  function lockScreen(content, o = {}) {
    const ios = isIOS();
    return `<div class="ui-os ui-lock-screen"><div class="ui-wall"></div><div class="ui-ls-top">${ios ? `<span class="ui-ls-lock">${icon('lock', 14)}</span>` : ''}<span class="ui-ls-date">${esc(o.date || 'Friday 6 November')}</span><span class="ui-ls-time">${esc(o.time || (ios ? '9:41' : '7:30'))}</span></div><div class="ui-ls-stack">${content}</div>${ios ? '<div class="ui-ls-foot"><span>' + icon('bolt', 18) + '</span><span>' + icon('gps', 18) + '</span></div>' : ''}</div>`;
  }
  /** Home screen with app icon grid; content = widgets html (placed first). */
  function homeScreen(content, o = {}) {
    const icons = Array.from({ length: o.icons ?? 8 }, (_, i) => `<span class="ui-hs-app" style="--h:${(i * 47) % 360}"></span>`).join('');
    return `<div class="ui-os ui-home-screen"><div class="ui-wall"></div><div class="ui-hs-grid">${content}${icons}</div><div class="ui-hs-dock">${Array.from({ length: 4 }, (_, i) => `<span class="ui-hs-app" style="--h:${i * 90 + 20}"></span>`).join('')}</div></div>`;
  }
  /** Notification. {title, body, time, app, actions:[], chip (Android status chip text), held, expanded} */
  function notification(o = {}) {
    if (isIOS()) return `<div class="ui-notif ${o.expanded ? 'is-expanded' : ''}"><span class="ui-notif-app">${I.loop({ size: 22, color: '#5BE7C4', dot: '#fff' })}</span><div class="ui-notif-b"><div class="ui-notif-h"><b>${esc(o.title)}</b><span>${esc(o.time || 'now')}</span></div><p>${esc(o.body || '')}</p>${o.held ? '<span class="ui-notif-held">Held until parked</span>' : ''}</div>${o.actions && o.expanded ? `<div class="ui-notif-acts">${o.actions.map((a) => `<span>${esc(a)}</span>`).join('')}</div>` : ''}</div>`;
    return `<div class="ui-notif ${o.expanded ? 'is-expanded' : ''}"><div class="ui-notif-h"><span class="ui-notif-app">${I.loop({ size: 14, mono: true, color: 'currentColor' })}</span><span>DrivePerf${o.channel ? ` · ${esc(o.channel)}` : ''} · ${esc(o.time || 'now')}</span>${o.chip ? `<em>${esc(o.chip)}</em>` : ''}</div><b>${esc(o.title)}</b><p>${esc(o.body || '')}</p>${o.held ? '<span class="ui-notif-held">Held until parked</span>' : ''}${o.actions ? `<div class="ui-notif-acts">${o.actions.map((a) => `<span>${esc(a)}</span>`).join('')}</div>` : ''}</div>`;
  }
  /** iOS Live Activity on the Lock Screen (components.md §20.1). live = ctx.data.live(state). mode: recording|gps|ending|cap */
  function liveActivity(live, o = {}) {
    const v = live.vehicleId ? U.vehicle(live.vehicleId) : null;
    const mode = o.mode || 'recording';
    if (mode === 'ending') return `<div class="ui-la"><div class="ui-la-h">${I.loop({ size: 22, color: '#5BE7C4', dot: '#fff' })}<b>Drive decoded.</b></div><p>Open to see your Story.</p></div>`;
    if (mode === 'cap') return `<div class="ui-la"><div class="ui-la-h">${I.loop({ size: 22, color: '#5BE7C4', dot: '#fff' })}<b>Still recording.</b></div><p>Open DrivePerf to see it.</p></div>`;
    return `<div class="ui-la" aria-label="DrivePerf recording, ${Math.round(live.elapsedS / 60)} minutes, ${live.distanceKm} kilometres."><div class="ui-la-h">${I.loop({ size: 22, color: '#5BE7C4', dot: '#fff' })}<b>Recording${v ? ` · ${esc(v.nickname)}` : ''}</b><span class="ui-la-stop">${I.svg('stop', { size: 14 })}Stop</span></div><div class="ui-la-n"><b class="ui-num">${U.fmtClock(live.elapsedS)}</b>${mode === 'gps' ? '<span>Finding GPS…</span>' : `<b class="ui-num">${live.distanceKm}<span class="ui-unit">km</span></b>`}</div></div>`;
  }
  /** Dynamic Island (S-LIVE-02). mode: compact|minimal|expanded|ending. Positioned over the hardware island. */
  function island(live, mode = 'compact') {
    const v = live && live.vehicleId ? U.vehicle(live.vehicleId) : null;
    const loop = I.loop({ size: 20, color: '#5BE7C4', dot: '#fff' });
    if (mode === 'minimal') return `<div class="ui-island is-minimal"><span class="ui-island-pill"></span><span class="ui-island-min">${loop}</span></div>`;
    if (mode === 'expanded') return `<div class="ui-island is-expanded"><div class="ui-island-row"><span>${loop}<b>Recording</b>${v ? `<small>${esc(v.nickname)}</small>` : ''}</span><b class="ui-num">${U.fmtClock(live.elapsedS)}</b></div><div class="ui-island-row"><b class="ui-num">${live.distanceKm}<span class="ui-unit">km</span></b><span class="ui-la-stop">${I.svg('stop', { size: 14 })}Stop</span></div></div>`;
    if (mode === 'ending') return `<div class="ui-island is-compact"><span>${loop}</span><b style="font-size:13px">Drive decoded ${icon('check', 14)}</b></div>`;
    return `<div class="ui-island is-compact"><span>${loop}</span><b class="ui-num">${U.fmtClock(live.elapsedS)}</b></div>`;
  }
  /** Widget. kind: small|medium|circular|rectangular (iOS) · glance (Android). content html. */
  const widget = (content, o = {}) => `<div class="ui-widget ui-widget--${o.kind || 'small'}" role="group" aria-label="${esc(o.label || 'DrivePerf widget')}">${content}</div>`;
  /** Android Quick Settings panel + tile. state: idle|recording|unavailable */
  function qsTile(o = {}) {
    const st = o.state || 'idle';
    return `<span class="ui-qs ui-qs--${st}">${icon(st === 'unavailable' ? 'gps' : 'record', 22)}<span><b>${esc(o.label || (st === 'recording' ? 'Recording' : 'Start drive'))}</b>${o.sub ? `<small>${esc(o.sub)}</small>` : ''}</span></span>`;
  }
  function shade(content, o = {}) {
    const tiles = ['Internet', 'Bluetooth', 'Flashlight', 'Do not disturb'].map((t) => `<span class="ui-qs ui-qs--off"><span><b>${t}</b></span></span>`);
    return `<div class="ui-os ui-shade"><div class="ui-wall"></div><div class="ui-shade-p"><div class="ui-shade-time">${esc(o.time || '7:30')}<small>Fri, 6 Nov</small></div><div class="ui-shade-tiles">${o.tile || ''}${tiles.slice(0, o.tile ? 3 : 4).join('')}</div><div class="ui-shade-n">${content}</div></div></div>`;
  }

  // ---------------- shell pieces (used by shell.js) ----------------
  const TABS = [['drives', 'Drives', 'S-HOME-01'], ['routes', 'Routes', 'S-RTE-01'], ['insights', 'Insights', 'S-STA-01'], ['garage', 'Garage', 'S-GAR-01']];
  function tabBar(active, o = {}) {
    if (isIOS()) return `<nav class="ui-tabbar ui-glassy" aria-label="Tabs"><span class="ui-tab-lens" style="--i:${Math.max(0, TABS.findIndex((t) => t[0] === active))}"></span>${TABS.map(([id, l]) => `<span class="${cls('ui-tab', id === active && 'is-on')}" ${act({ tab: id, label: l, haptic: 'selection' })} aria-selected="${id === active}">${icon(id, 24)}<span>${l}</span></span>`).join('')}</nav>`;
    return `<nav class="ui-tabbar" aria-label="Navigation">${TABS.map(([id, l]) => `<span class="${cls('ui-tab', id === active && 'is-on')}" ${act({ tab: id, label: l, haptic: 'selection' })} aria-selected="${id === active}"><i>${icon(id, 24)}</i><span>${l}</span></span>`).join('')}</nav>`;
  }

  // ---------------- motion helpers (mount-time; all respect RM + export "shot" mode) ----------------
  const M = window.DP_MOTION;
  const still = (el) => (window.DP && DP.shot) || M.reduced(el);
  const anim = {
    /** Smoothness ring reveal (motion.md §3.4) on every .ui-ring inside root. */
    rings(root, { haptics = true } = {}) {
      root.querySelectorAll('.ui-ring').forEach((rg) => {
        const arc = rg.querySelector('.ui-ring-arc'), numEl = rg.querySelector('.ui-ring-num'), score = +rg.dataset.score;
        if (!arc || !score) return;
        if (still(rg)) { rg.animate([{ opacity: 0 }, { opacity: 1 }], { duration: DP.shot ? 0 : 220 }); return; }
        const C = +rg.dataset.c, full = (score / 100) * C;
        const base = rg.querySelector('.ui-ring-base'); if (base) base.animate([{ opacity: 0, transform: 'scale(.6)' }, { opacity: 1, transform: 'scale(1)' }], { duration: 320, delay: 400, fill: 'both', easing: 'cubic-bezier(.2,0,0,1)', transformOrigin: 'center' });
        let lastTick = 0;
        M.tween({ from: 0, to: 1, duration: 1400, ease: 'countUp', el: rg, onUpdate: (p) => { arc.setAttribute('stroke-dasharray', `${(full * p).toFixed(2)} ${C}`); const v = Math.round(score * p); numEl.textContent = v; if (haptics && Math.floor(v / 10) > lastTick) { lastTick = Math.floor(v / 10); } }, onDone: () => { numEl.style.fontWeight = 700; setTimeout(() => (numEl.style.fontWeight = ''), 120); haptics && M.haptic('tap', { el: rg }); const rec = rg.querySelector('.ui-ring-rec.is-new'); if (rec) { rec.animate([{ opacity: 0.3 }, { opacity: 1 }], { duration: 400 }); M.haptic('doubleSuccess', { el: rg }); } } });
        const shape = rg.querySelector('.ui-ring-shape'); if (shape) shape.animate([{ transform: 'rotate(0deg) scale(.92)' }, { transform: 'rotate(40deg) scale(1)' }], { duration: 1400, easing: 'cubic-bezier(.16,1,.3,1)', fill: 'both' });
      });
    },
    /** Route draw-on (motion.md §3.3) + staggered pin pop (§3.6) on every .ui-map inside root. */
    maps(root) {
      root.querySelectorAll('.ui-map').forEach((m) => {
        const route = m.querySelector('.ui-route'), pins = m.querySelectorAll('.ui-mpin.is-event');
        if (!route) return;
        if (still(m)) { if (!DP.shot) m.querySelector('.ui-map-route').animate([{ opacity: 0 }, { opacity: 1 }], { duration: 220 }); return; }
        M.drawOn(route, { duration: 1800 });
        m.querySelectorAll('.ui-route-est,.ui-route-gap,.ui-mpin.is-stop,.ui-mpin.is-max').forEach((x) => x.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 400, delay: 1500, fill: 'both' }));
        pins.forEach((p, i) => M.animate(p, [{ transform: 'translateY(6px) scale(0)' }, { transform: 'translateY(0) scale(1)' }], { preset: 'pinPop', delay: 1600 + i * 40 }));
      });
    },
    /** Rise-in stagger for direct children matching sel (8 px rise, list stagger 30 ms). */
    rise(root, sel = '[data-rise]', { stagger = 30, delay = 0 } = {}) {
      if (still(root)) return;
      root.querySelectorAll(sel).forEach((el, i) => M.animate(el, [{ opacity: 0, transform: 'translateY(8px)' }, { opacity: 1, transform: 'translateY(0)' }], { preset: 'default', delay: delay + i * stagger }));
    },
    pop(el, delay = 0) { if (!el || still(el)) return; M.animate(el, [{ opacity: 0, transform: 'scale(.86)' }, { opacity: 1, transform: 'scale(1)' }], { preset: 'default', delay }); },
    countUp(el, to, o) { return M.countUp(el, to, o); },
  };

  window.DP_UI = {
    esc, cls, act, qs, icon, num, nid,
    button, actionButton, iconButton, backButton, chip, deltaChip, proBadge, previewBadge, avatar, carDot, carChip, segmented,
    largeTitle, navBar, mediumTitle, page, pad, stack, spacer, section, card, list, row, toggle, textField, stepper, radios,
    stat, stats, ring, scoreBadge, cookie, qualityBadge, qualityDetail, provenance, glyph, tripRow, map,
    sparkline, evidenceTrace, timeline, gg, scrubber,
    banner, empty, artMerge, skeleton, error, loader, lockPanel, tipCard, eventRow,
    sheetHeader, dialog, menu, sysPermission, sysSettings,
    lockScreen, homeScreen, notification, liveActivity, island, widget, qsTile, shade,
    tabBar, TABS, anim,
    /** component → tokens & spec references, used by export to write design-spec.md (keep names = function names). */
    TOKENS: {
      page: ['--dp-bg-grouped (iOS) / --md-sys-color-surface (Android)', '--dp-screen-margin 16'],
      largeTitle: ['--dp-font-screen-title (iOS 34/41 bold · Android 32/40)', 'components.md §2'],
      navBar: ['--dp-font-card-title', '--dp-font-meta', 'glass: --dp-glass-fill + --dp-glass-blur', 'components.md §2'],
      button: ['--dp-accent / --dp-on-accent (filled)', '--dp-fill-strong · --md-sys-color-secondary-container (tonal)', '--dp-radius-button', 'height iOS 50/44/34 · Android 56/40/32', '--dp-font-button', 'components.md §0'],
      actionButton: ['--dp-surface-2 · --md-sys-color-surface-container-high', '--dp-radius-lg', 'min 44 pt / 48 dp'],
      chip: ['--dp-radius-chip (iOS capsule · Android 8)', 'height 28', '--dp-font-caption'],
      deltaChip: ['better: --dp-accent-text on --dp-accent 18%', 'lower: --dp-text-2 on --dp-fill (never red)', 'record: --dp-record on --dp-record 18%', 'components.md §4'],
      card: ['--dp-surface / --md-sys-color-surface-container', '--dp-radius-card (24 iOS · 16 Android)', 'hero: --dp-radius-card-hero (32 · 28)', 'light iOS: --dp-elev-1'],
      section: ['--dp-font-section-title', '--dp-layout-section-gap 28'],
      list: ['iOS inset grouped radius --dp-radius-card, rows 52 pt, separators --dp-separator inset 16', 'Android ListItem 56/72 dp in --md-sys-color-surface-container'],
      row: ['--dp-font-body', '--dp-font-meta (supporting)', '--dp-layout-list-row-min'],
      toggle: ['iOS 51×31 tint --dp-accent', 'Android M3 Switch 52×32 --md-sys-color-primary'],
      stats: ['--dp-surface-2 / --md-sys-color-surface-container-high', 'radius 14 iOS / 12 Android', 'value --dp-num-m (DP Numerals)', 'components.md §5'],
      ring: ['track --dp-fill-strong', 'arc --dp-gradient-ring (glide-400 → accent → glide-100)', 'baseline notch --dp-text-2', 'record notch --dp-record', 'Android container Cookie9Sided --md-sys-color-primary-container 42%', 'components.md §3'],
      scoreBadge: ['iOS mini ring 46 · Android Cookie9Sided 46', '--dp-num-s'],
      tripRow: ['glyph 40 on --dp-surface-2', '--dp-font-card-title', '--dp-font-meta', 'components.md §12'],
      qualityBadge: ['--dp-data-quality-{good|usable|limited|unscorable}', 'components.md §10'],
      map: ['--dp-data-map-land/road/grid', 'route --dp-data-route 3.8 + casing --dp-data-route-casing 7.5', 'estimated dashed 4/4 at 60%', 'gap --dp-data-gap-stroke dotted 1/6', 'pins DP_ICONS.pin 26', 'attribution “© OpenStreetMap contributors”'],
      banner: ['radius 12 (--dp-radius-sm+)', 'info --dp-fill · attention --dp-warning-container · critical --dp-critical-container', 'components.md §22'],
      tipCard: ['--dp-accent 10% over --dp-surface', 'sparkline --dp-data-long-g', 'components.md §18'],
      eventRow: ['pin 28 (shape = meaning)', 'meta --dp-num-xs', 'min height 44', 'components.md §9.2'],
      lockPanel: ['blur --dp-opacity-locked-blur 14px', 'dim --dp-opacity-locked-dim 35%', 'components.md §15.2'],
      tabBar: ['iOS floating glass: inset 16, bottom 21, height 62, lens --dp-fill-strong', 'Android NavigationBar 64 dp + indicator 56×32 --md-sys-color-secondary-container', 'components.md §1'],
      accessory: ['iOS tabViewBottomAccessory glass 48 pt, 8 above tab bar', 'Android floating toolbar 64 dp --md-sys-color-primary-container, 16 above nav bar', 'Start: --dp-accent'],
      sheet: ['iOS glass --dp-glass-fill-strong + --dp-glass-prominent-blur, radius --dp-radius-sheet 38', 'Android ModalBottomSheet --md-sys-color-surface-container-low, top radius 28, drag handle 32×4', 'motion spring.sheet'],
      dialog: ['iOS alert 270 glass', 'Android M3 dialog radius 28 --md-sys-color-surface-container-high'],
      empty: ['illustration 120 in --dp-accent', '--dp-font-section-title', 'components.md §21'],
      skeleton: ['--dp-fill-strong, breathe 1.2 s (dp-skeleton)'],
      timeline: ['--dp-data-speed · --dp-data-long-g · --dp-data-lat-g (dashed 6/3)', 'cursor --dp-data-cursor', 'components.md §7'],
      gg: ['--dp-data-gg-ring/cloud/envelope/dot', 'fixed ±0.6 g', 'components.md §6'],
      liveActivity: ['background black 55%', 'Glide accent', 'components.md §20'],
      notification: ['components.md §20.6'],
      menu: ['iOS glass Menu: --dp-glass-fill-strong, radius 26', 'Android DropdownMenu: --md-sys-color-surface-container, radius 16'],
    },
  };
})();
