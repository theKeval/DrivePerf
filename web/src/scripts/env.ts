// Runtime flags shared by every island (ported from site.js / motion.js).
const html = document.documentElement;

/** Reduced motion: OS preference, or ?rm=1 (sets html[data-rm] in the head script). */
export const reduced = (): boolean => html.dataset.rm === 'true' || matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Global Privacy Control or Do Not Track: analytics never load and events are dropped (ADR-007). */
export const optedOut = (): boolean =>
  (navigator as Navigator & { globalPrivacyControl?: boolean }).globalPrivacyControl === true || navigator.doNotTrack === '1';

/** Spring easings from the stylesheet (--spring-pin-pop etc.), with a cubic fallback. */
export const cssEase = (name: string, fallback = 'cubic-bezier(0.32, 0.72, 0, 1)'): string =>
  getComputedStyle(html).getPropertyValue(name).trim() || fallback;

/** Run fn once when el is in view (immediately under reduced motion or without IntersectionObserver). */
export function onceInView(el: Element | null, fn: () => void, opts: IntersectionObserverInit = { threshold: 0.35 }): void {
  if (!el) return;
  if (reduced() || !('IntersectionObserver' in window)) { fn(); return; }
  const o = new IntersectionObserver((en) => { if (en.some((e) => e.isIntersecting)) { o.disconnect(); fn(); } }, opts);
  o.observe(el);
}
