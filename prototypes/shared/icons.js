/* DrivePerf shared icons (DRIVE-99, A6). Hand-authored; the meaning-carrying shapes (event pins, quality,
   provenance) are identical on iOS and Android. Platform shells may swap UI glyphs (tab icons, chevrons)
   for SF Symbols / Material Symbols lookalikes, but NEVER the pins, quality or provenance glyphs.
   Usage: DP_ICONS.svg('play', {size: 24})  ·  DP_ICONS.pin('brake', {needed: false, size: 28})
          DP_ICONS.quality('Good', {size: 16})  ·  DP_ICONS.provenance('estimated') */
(function () {
  const P = {
    // ---- UI glyphs (24 grid, stroke 1.8, round) ----
    drives: '<path d="M4 17.5c2.5-6 5-6 8-3s5.5 3 8-3"/><circle cx="20" cy="11.5" r="1.6" fill="currentColor" stroke="none"/><path d="M4 21h16" opacity=".35"/>',
    routes: '<path d="M6 19c0-4 3-5 6-6s6-2 6-6"/><circle cx="6" cy="19" r="2"/><circle cx="18" cy="7" r="2"/>',
    insights: '<path d="M4 20V10M10 20V5M16 20v-7M22 20H2"/>',
    garage: '<path d="M3 11l9-6 9 6v9H3z"/><path d="M7 20v-5h10v5"/><path d="M7 17.5h10" opacity=".5"/>',
    profile: '<circle cx="12" cy="8.5" r="3.5"/><path d="M5 20c1.2-3.6 3.9-5.5 7-5.5s5.8 1.9 7 5.5"/>',
    record: '<circle cx="12" cy="12" r="5" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="9"/>',
    stop: '<rect x="7" y="7" width="10" height="10" rx="2.5" fill="currentColor" stroke="none"/>',
    passenger: '<circle cx="12" cy="6.5" r="2.5"/><path d="M8 21v-6.5a4 4 0 0 1 8 0V21"/><path d="M5 13h3M16 13h3"/>',
    play: '<path d="M8 5.5v13l11-6.5z" fill="currentColor" stroke-linejoin="round"/>',
    pause: '<path d="M8 5v14M16 5v14" stroke-width="3"/>',
    share: '<path d="M12 3v12M7.5 7.5L12 3l4.5 4.5"/><path d="M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7"/>',
    lock: '<rect x="5" y="10.5" width="14" height="10" rx="2.5"/><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5.5"/><circle cx="12" cy="7.8" r="1" fill="currentColor" stroke="none"/>',
    chevronRight: '<path d="M9.5 5.5L16 12l-6.5 6.5"/>',
    chevronDown: '<path d="M5.5 9.5L12 16l6.5-6.5"/>',
    close: '<path d="M6 6l12 12M18 6L6 18"/>',
    check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    more: '<circle cx="5.5" cy="12" r="1.6" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none"/><circle cx="18.5" cy="12" r="1.6" fill="currentColor" stroke="none"/>',
    filter: '<path d="M4 6h16M7 12h10M10 18h4"/>',
    layers: '<path d="M12 3l9 5-9 5-9-5z"/><path d="M3 13l9 5 9-5" opacity=".6"/>',
    car: '<path d="M3.5 16.5v-3.2l2-4.6A2 2 0 0 1 7.3 7.5h9.4a2 2 0 0 1 1.8 1.2l2 4.6v3.2"/><path d="M2.5 16.5h19"/><circle cx="7" cy="16.8" r="1.8"/><circle cx="17" cy="16.8" r="1.8"/>',
    bolt: '<path d="M13 2.5L5.5 13.5H11l-1 8 7.5-11H12z" fill="currentColor" stroke-linejoin="round"/>',
    shield: '<path d="M12 3l7.5 3v5.5c0 4.6-3.2 8.2-7.5 9.5-4.3-1.3-7.5-4.9-7.5-9.5V6z"/><path d="M9 12l2.2 2.2L15.5 10"/>',
    heart: '<path d="M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z"/>',
    health: '<path d="M3 12h4l2-5 4 10 2-5h6"/>',
    gear: '<circle cx="12" cy="12" r="3"/><path d="M12 2.8v2.6M12 18.6v2.6M21.2 12h-2.6M5.4 12H2.8M18.5 5.5l-1.8 1.8M7.3 16.7l-1.8 1.8M18.5 18.5l-1.8-1.8M7.3 7.3L5.5 5.5"/>',
    bell: '<path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2H4.5z"/><path d="M10 20.5a2 2 0 0 0 4 0"/>',
    rosette: '<circle cx="12" cy="10" r="5.5"/><path d="M8.6 14.4L7 21l5-2.5 5 2.5-1.6-6.6"/><path d="M12 7.5l.9 1.8 2 .3-1.5 1.4.4 2-1.8-1-1.8 1 .4-2-1.5-1.4 2-.3z" fill="currentColor" stroke="none"/>',
    ghost: '<path d="M4 18c2-5 4.5-7 8-7s6 2 8 7" stroke-dasharray="2 3"/><path d="M4 14c2-5 4.5-7 8-7s6 2 8 7"/>',
    replay: '<path d="M4 12a8 8 0 1 0 2.4-5.7"/><path d="M4 4v4.5h4.5"/><path d="M10.5 9v6l5-3z" fill="currentColor" stroke="none"/>',
    analysis: '<path d="M3 17l4.5-5 3.5 3 5-7 5 4"/><path d="M3 21h18" opacity=".4"/>',
    story: '<rect x="6" y="3" width="12" height="18" rx="3"/><path d="M9 7h6" opacity=".5"/>',
    export: '<path d="M12 15V3M7.5 10.5L12 15l4.5-4.5"/><path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/>',
    edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13.5 6.5l4 4"/>',
    trash: '<path d="M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13"/>',
    cloud: '<path d="M7 18.5h10a4 4 0 0 0 .6-8 5.5 5.5 0 0 0-10.6 1.2A3.5 3.5 0 0 0 7 18.5z"/>',
    gps: '<circle cx="12" cy="12" r="3.5"/><circle cx="12" cy="12" r="8"/><path d="M12 1.5v3M12 19.5v3M1.5 12h3M19.5 12h3"/>',
    battery: '<rect x="2.5" y="7.5" width="17" height="9" rx="2.5"/><path d="M21.5 10.5v3"/><rect x="5" y="10" width="7" height="4" rx="1" fill="currentColor" stroke="none"/>',
    phone: '<rect x="7" y="2.5" width="10" height="19" rx="2.5"/><path d="M10.5 18.5h3"/>',
    speed: '<path d="M4.5 17a8 8 0 1 1 15 0"/><path d="M12 13l3.5-4"/>',
    elevation: '<path d="M2.5 19l6-9 4 5.5 3-4 6 7.5z"/>',
    pinStop: '<path d="M10 8.5v7M14 8.5v7" stroke-width="2.4"/>',
  };
  // ---- pin glyphs (drawn inside a 28 box, centred at 14,14) ----
  const GLYPH = {
    brake: '<path d="M9 10.2l5 4.6 5-4.6" /><path d="M9 18.6h10"/>',
    accel: '<path d="M9 15.4l5-4.6 5 4.6"/><path d="M9 9h10"/>',
    corner: '<path d="M9.5 18.5c0-5 2.6-8.5 7.5-8.5"/><path d="M14.5 7.4l2.9 2.6-2.9 2.6"/>',
  };
  const SHAPE = {
    brake: (f, s, w) => `<rect x="3" y="3" width="22" height="22" rx="6.2" fill="${f}" stroke="${s}" stroke-width="${w}"/>`,
    accel: (f, s, w) => `<circle cx="14" cy="14" r="11.5" fill="${f}" stroke="${s}" stroke-width="${w}"/>`,
    corner: (f, s, w) => `<path d="M14 1.6l10.6 10.6a2.6 2.6 0 0 1 0 3.6L14 26.4 3.4 15.8a2.6 2.6 0 0 1 0-3.6z" fill="${f}" stroke="${s}" stroke-width="${w}" stroke-linejoin="round"/>`,
  };
  const COLOR = { brake: 'var(--dp-data-event-brake)', accel: 'var(--dp-data-event-accel)', corner: 'var(--dp-data-event-corner)' };
  const LABEL = { brake: 'Hard braking', accel: 'Hard acceleration', corner: 'Hard cornering' };
  const wrap = (inner, size, vb = 24, extra = '') =>
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${vb} ${vb}" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" ${extra}>${inner}</svg>`;

  window.DP_ICONS = {
    names: Object.keys(P),
    svg: (name, { size = 24, label } = {}) => wrap(P[name] || P.info, size, 24, label ? `role="img" aria-label="${label}" aria-hidden="false"` : ''),
    /** Event pin. needed = hollow outline + same glyph (excluded from score). selected = larger ring. */
    pin(type, { needed = false, size = 28, selected = false, label } = {}) {
      const f = needed ? 'var(--dp-surface, #131822)' : COLOR[type];
      const s = needed ? COLOR[type] : 'var(--dp-data-pin-ring)';
      const g = needed ? COLOR[type] : 'var(--dp-data-pin-glyph)';
      const ring = selected ? `<circle cx="14" cy="14" r="13.4" fill="none" stroke="${COLOR[type]}" stroke-opacity=".35" stroke-width="2.4"/>` : '';
      const aria = `role="img" aria-label="${label || LABEL[type] + (needed ? ', marked as needed' : '')}"`;
      return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="-1 -1 30 30" ${aria}>${ring}${SHAPE[type](f, s, needed ? 2.2 : 1.8)}<g fill="none" stroke="${g}" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">${GLYPH[type]}</g></svg>`;
    },
    /** Stop pin (capsule, neutral) and max-speed pin (outline triangle, neutral; no animation, no haptic). */
    stopPin: ({ size = 22 } = {}) => `<svg width="${size * 1.3}" height="${size}" viewBox="0 0 29 22" role="img" aria-label="Stop"><rect x="1" y="1" width="27" height="20" rx="10" fill="var(--dp-surface-3)" stroke="var(--dp-data-pin-ring)" stroke-width="1.5"/><path d="M12 7v8M17 7v8" stroke="var(--dp-data-stop)" stroke-width="2.4" stroke-linecap="round"/></svg>`,
    maxPin: ({ size = 22 } = {}) => `<svg width="${size}" height="${size}" viewBox="0 0 22 22" role="img" aria-label="Max speed"><path d="M11 2.5l8.5 15h-17z" fill="var(--dp-surface)" stroke="var(--dp-data-max-speed)" stroke-width="1.8" stroke-linejoin="round"/></svg>`,
    /** Data-quality glyph: Good = filled disc + check · Usable = half disc · Limited = triangle ! · Unscorable = slashed ring */
    quality(badge, { size = 16 } = {}) {
      const c = `var(--dp-data-quality-${badge.toLowerCase()})`;
      const g = {
        Good: `<circle cx="8" cy="8" r="7" fill="${c}"/><path d="M4.8 8.2l2.1 2.1 4.3-4.4" stroke="var(--dp-bg-grouped, #000)" stroke-width="1.8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>`,
        Usable: `<circle cx="8" cy="8" r="6.2" fill="none" stroke="${c}" stroke-width="1.6"/><path d="M8 1.8a6.2 6.2 0 0 1 0 12.4z" fill="${c}"/>`,
        Limited: `<path d="M8 1.6l6.8 12H1.2z" fill="${c}" stroke="${c}" stroke-width="1.2" stroke-linejoin="round"/><path d="M8 6v3.6" stroke="var(--dp-bg-grouped, #000)" stroke-width="1.7" stroke-linecap="round"/><circle cx="8" cy="11.6" r="1" fill="var(--dp-bg-grouped, #000)"/>`,
        Unscorable: `<circle cx="8" cy="8" r="6.2" fill="none" stroke="${c}" stroke-width="1.6"/><path d="M3.6 12.4l8.8-8.8" stroke="${c}" stroke-width="1.6" stroke-linecap="round"/>`,
      }[badge];
      return `<svg width="${size}" height="${size}" viewBox="0 0 16 16" aria-hidden="true">${g}</svg>`;
    },
    /** Provenance glyph: observed = filled dot · derived = ring with dot · estimated = dashed ring */
    provenance(kind, { size = 12 } = {}) {
      const g = {
        observed: '<circle cx="6" cy="6" r="4" fill="currentColor"/>',
        derived: '<circle cx="6" cy="6" r="4.2" fill="none" stroke="currentColor" stroke-width="1.4"/><circle cx="6" cy="6" r="1.6" fill="currentColor"/>',
        estimated: '<circle cx="6" cy="6" r="4.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-dasharray="2 1.8"/>',
      }[kind];
      return `<svg width="${size}" height="${size}" viewBox="0 0 12 12" aria-hidden="true">${g}</svg>`;
    },
    /** The Loop mark (brand). mono = single colour for small sizes. */
    loop({ size = 28, mono = false, color = 'var(--dp-accent)', dot = 'currentColor' } = {}) {
      const r = 9, cx = 14, cy = 14, head = -78, span = 270, ahead = 34;
      const pt = (a) => [cx + r * Math.cos((a * Math.PI) / 180), cy + r * Math.sin((a * Math.PI) / 180)];
      const [x0, y0] = pt(head - span), [x1, y1] = pt(head), [dx, dy] = pt(head + ahead);
      const id = 'lg' + Math.random().toString(36).slice(2, 7);
      const grad = mono ? '' : `<defs><linearGradient id="${id}" x1="${x0}" y1="${y0}" x2="${x1}" y2="${y1}" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="${color}" stop-opacity=".15"/><stop offset="1" stop-color="${color}"/></linearGradient></defs>`;
      return `<svg width="${size}" height="${size}" viewBox="0 0 28 28" aria-hidden="true">${grad}<path d="M${x0.toFixed(2)} ${y0.toFixed(2)} A${r} ${r} 0 1 1 ${x1.toFixed(2)} ${y1.toFixed(2)}" fill="none" stroke="${mono ? color : `url(#${id})`}" stroke-width="3.6" stroke-linecap="round"/><circle cx="${dx.toFixed(2)}" cy="${dy.toFixed(2)}" r="2.3" fill="${dot}"/></svg>`;
    },
    eventLabel: (type) => LABEL[type],
  };
})();
