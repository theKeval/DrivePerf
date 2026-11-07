// Icon set (24 grid, stroke 1.8), ported from prototypes/web/js/site.js. Rendered to inline SVG strings at build time.
const I = {
    menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
    close: '<path d="M6 6l12 12M18 6L6 18"/>',
    check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    shield: '<path d="M12 3l7.5 3v5.5c0 4.6-3.2 8.2-7.5 9.5-4.3-1.3-7.5-4.9-7.5-9.5V6z"/><path d="M9 12l2.2 2.2L15.5 10"/>',
    phone: '<rect x="6.5" y="2.5" width="11" height="19" rx="3"/><path d="M10.5 18.5h3"/>',
    cloud: '<path d="M7 18.5h10a4 4 0 0 0 .6-7.95A5.5 5.5 0 0 0 7.1 9.3 4.6 4.6 0 0 0 7 18.5z"/>',
    download: '<path d="M12 4v11M7.5 10.5L12 15l4.5-4.5"/><path d="M5 19.5h14"/>',
    trash: '<path d="M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13"/>',
    noentry: '<circle cx="12" cy="12" r="8.5"/><path d="M6 6l12 12"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5.5"/><circle cx="12" cy="7.8" r="1" fill="currentColor" stroke="none"/>',
    alert: '<path d="M12 3.5l9.5 16.5h-19z"/><path d="M12 10v4.5"/><circle cx="12" cy="17.2" r="1" fill="currentColor" stroke="none"/>',
    mail: '<rect x="3" y="5.5" width="18" height="13" rx="2.5"/><path d="M3.5 7l8.5 6 8.5-6"/>',
    store: '<rect x="5" y="2.5" width="14" height="19" rx="3.5"/><path d="M9 6.5h6" opacity=".5"/><path d="M12 10.5v6M9.5 14l2.5 2.5 2.5-2.5"/>',
    play: '<path d="M6 4.5l12.5 7.5L6 19.5z"/>',
    battery: '<rect x="2.5" y="7.5" width="17" height="9" rx="2.5"/><path d="M21.5 10.5v3"/><path d="M5.5 10.5h5v3h-5z" fill="currentColor" stroke="none"/>',
    route: '<path d="M6 19c0-4 3-5 6-6s6-2 6-6"/><circle cx="6" cy="19" r="2"/><circle cx="18" cy="7" r="2"/>',
    sparkle: '<path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5L18 18M6 18l2.5-2.5M15.5 8.5L18 6"/>',
    eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>',
    lock: '<rect x="5" y="10.5" width="14" height="10" rx="2.5"/><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/>',
    user: '<circle cx="12" cy="8.5" r="3.5"/><path d="M5 20c1.2-3.6 3.9-5.5 7-5.5s5.8 1.9 7 5.5"/>',
    gift: '<rect x="3.5" y="9" width="17" height="11.5" rx="2"/><path d="M12 9v11.5M3.5 13h17"/><path d="M12 9c-1.5-3-5-4-5.5-2S9 9 12 9zm0 0c1.5-3 5-4 5.5-2S15 9 12 9z"/>',
    qr: '<rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><path d="M14 14h2v2h-2zM18 18h2v2h-2zM14 18h2M18 14h2"/>',
    rosette: '<circle cx="12" cy="9.5" r="5.5"/><path d="M8.6 13.8L7 21l5-2.6 5 2.6-1.6-7.2"/>',
    ring: '<circle cx="12" cy="12" r="8.5" opacity=".35"/><path d="M12 3.5a8.5 8.5 0 1 1-7.9 5.4"/>',
    replay: '<path d="M4 12a8 8 0 1 0 2.3-5.6"/><path d="M4 4.5v4h4"/><path d="M10.5 9v6l4.5-3z" fill="currentColor" stroke="none"/>',
    calendar: '<rect x="3.5" y="5" width="17" height="15.5" rx="2.5"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
    car: '<path d="M3.5 16.5v-3.2l2-4.6A2 2 0 0 1 7.3 7.5h9.4a2 2 0 0 1 1.8 1.2l2 4.6v3.2"/><path d="M2.5 16.5h19"/><circle cx="7" cy="16.8" r="1.8"/><circle cx="17" cy="16.8" r="1.8"/>',
    globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3z"/>',
    gauge0: '<path d="M4 17a8 8 0 1 1 16 0"/><path d="M8 17h8"/>',
    bolt: '<path d="M13 2.5L5.5 13.5H11l-1 8 7.5-11H12z"/>',
    pause: '<rect x="6.5" y="5" width="3.5" height="14" rx="1"/><rect x="14" y="5" width="3.5" height="14" rx="1"/>',
    passenger: '<circle cx="12" cy="6.5" r="2.5"/><path d="M8 21v-6.5a4 4 0 0 1 8 0V21"/><path d="M5 13h3M16 13h3"/>',
    file: '<path d="M6.5 2.5h7l4 4v15h-11z"/><path d="M13.5 2.5v4h4"/>',
    copy: '<rect x="8" y="8" width="12" height="12" rx="2.5"/><path d="M16 8V5.5A1.5 1.5 0 0 0 14.5 4h-9A1.5 1.5 0 0 0 4 5.5v9A1.5 1.5 0 0 0 5.5 16H8"/>',
    arrowUpRight: '<path d="M7 17L17 7M9 7h8v8"/>',
    brake: '<path d="M6.5 6.5l5.5 6 5.5-6"/><path d="M6 17.5h12" stroke-width="2.6"/>',
} as const;

export type IconName = keyof typeof I;

export const icon = (name: IconName, attrs = ''): string =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" ${attrs}>${I[name] ?? ''}</svg>`;
