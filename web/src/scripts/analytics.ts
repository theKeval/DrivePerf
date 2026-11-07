// Website analytics (ADR-007, Gate 4): Cloudflare Web Analytics page views, cookie-less, loaded only in
// production builds and only when the browser sends neither Global Privacy Control nor Do Not Track.
// Cloudflare's beacon counts page views only. Product events (waitlist_submitted, …) from the design specs are
// emitted as DOM events for now; a first-party POST /v1/web/events can consume them once the API exists.
import { SITE } from '../config/site';
import { optedOut } from './env';

export function loadBeacon(): void {
  if (SITE.env !== 'prod' || !SITE.cloudflareBeaconToken || optedOut()) return;
  const s = document.createElement('script');
  s.defer = true;
  s.src = 'https://static.cloudflareinsights.com/beacon.min.js';
  s.dataset.cfBeacon = JSON.stringify({ token: SITE.cloudflareBeaconToken, spa: false });
  document.head.appendChild(s);
}

/** Product event (spec §Analytics). Suppressed under GPC/DNT; otherwise dispatched as `dp:track` on document. */
export function track(name: string, props: Record<string, string | number | boolean> = {}): void {
  if (optedOut()) return;
  document.dispatchEvent(new CustomEvent('dp:track', { detail: { name, props, path: location.pathname } }));
}
