// Website analytics (ADR-007, Gate 4): Cloudflare Web Analytics page views, cookie-less, loaded only in
// production builds served on driveperf.com, and only when the browser sends neither Global Privacy Control nor
// Do Not Track.
// Cloudflare's beacon counts page views only. Product events (waitlist_submitted, …) from the design specs are
// emitted as DOM events for now; a first-party POST /v1/web/events can consume them once the API exists.
import { SITE } from '../config/site';
import { optedOut } from './env';

// Only on the real host: the prod build is also served on localhost by Lighthouse CI, where Cloudflare rejects the
// beacon's report (CORS console error) and the visit would not be real traffic anyway.
const onSiteHost = (): boolean => location.hostname === new URL(SITE.origin).hostname;

export function loadBeacon(): void {
  if (SITE.env !== 'prod' || !SITE.cloudflareBeaconToken || !onSiteHost() || optedOut()) return;
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
