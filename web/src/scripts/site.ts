// Shared site runtime (every page): header menu, Loop trace, theme toggle, reveal-on-scroll, analytics beacon.
// Ported from prototypes/web/js/site.js; the prototype's state router and panel are not part of the site.
import { reduced, cssEase } from './env';
import { icon } from '../lib/icons';
import { loadBeacon } from './analytics';

const doc = document, html = doc.documentElement;

/* ---------- Header: mobile disclosure menu (Esc closes), scrolled state ---------- */
function bindHeader(): void {
  const header = doc.querySelector<HTMLElement>('.site-header');
  const btn = header?.querySelector<HTMLButtonElement>('.menu-btn');
  const menu = doc.getElementById('mobile-menu');
  if (!header || !btn || !menu) return;
  traceLoop(header.querySelector('.brand svg'));
  btn.addEventListener('click', () => {
    const open = btn.getAttribute('aria-expanded') !== 'true';
    btn.setAttribute('aria-expanded', String(open));
    btn.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
    btn.innerHTML = icon(open ? 'close' : 'menu');
    menu.hidden = !open;
    menu.classList.toggle('is-open', open);
  });
  menu.addEventListener('click', (e) => { if ((e.target as Element).closest('a')) btn.click(); });
  doc.addEventListener('keydown', (e) => { if (e.key === 'Escape' && btn.getAttribute('aria-expanded') === 'true') { btn.click(); btn.focus(); } });
  const onScroll = () => header.classList.toggle('is-scrolled', scrollY > 8);
  addEventListener('scroll', onScroll, { passive: true });
  onScroll();
}

/* LoopTrace (motion v2): the header Loop draws its six trail sub-arcs in sequence, then the G-dot pops
   (pin-pop spring). Load, once. Reduced motion: static mark. */
export function traceLoop(svg: SVGSVGElement | null, { delay = 120, total = 620 } = {}): void {
  if (!svg || reduced() || !svg.animate) return;
  const arcs = [...svg.querySelectorAll('path')], dots = [...svg.querySelectorAll('circle')];
  const per = total / arcs.length;
  arcs.forEach((p, i) => {
    const L = p.getTotalLength();
    p.style.strokeDasharray = `${L} ${L}`;
    p.animate([{ strokeDashoffset: L }, { strokeDashoffset: 0 }], { duration: per + 20, delay: delay + i * per, easing: i === arcs.length - 1 ? 'cubic-bezier(0.16, 1, 0.3, 1)' : 'linear', fill: 'both' });
  });
  const pop = cssEase('--spring-pin-pop');
  dots.forEach((c, i) => {
    c.style.transformBox = 'fill-box'; c.style.transformOrigin = 'center';
    c.animate([{ transform: 'scale(0)', opacity: 0 }, { transform: 'scale(1)', opacity: 1 }], { duration: 540, delay: delay + total - 40 + i * 160, easing: pop, fill: 'both' });
  });
}

/* ---------- Footer Night/Day toggle (sessionStorage only: no cookies) ---------- */
function bindTheme(): void {
  const btns = doc.querySelectorAll<HTMLButtonElement>('[data-theme-set]');
  const sync = () => btns.forEach((x) => x.setAttribute('aria-pressed', String((html.dataset.theme || 'dark') === x.dataset.themeSet)));
  btns.forEach((b) => b.addEventListener('click', () => {
    html.dataset.theme = b.dataset.themeSet;
    try { sessionStorage.setItem('dp-theme', b.dataset.themeSet!); } catch { /* storage unavailable: theme still applies */ }
    sync();
  }));
  sync();
}

/* ---------- Reveal on scroll ---------- */
function bindReveal(): void {
  const els = doc.querySelectorAll('[data-reveal]');
  if (reduced() || !('IntersectionObserver' in window)) { els.forEach((e) => e.classList.add('is-in')); return; }
  const io = new IntersectionObserver((entries) => entries.forEach((en) => { if (en.isIntersecting) { en.target.classList.add('is-in'); io.unobserve(en.target); } }), { rootMargin: '0px 0px -8% 0px', threshold: 0.12 });
  els.forEach((e) => io.observe(e));
}

/* Status-page Loop glyphs ([data-loop-trace]) draw on like the header mark. */
function bindLoopTraces(): void {
  doc.querySelectorAll<HTMLElement>('[data-loop-trace]').forEach((el) => traceLoop(el.querySelector('svg'), { delay: 200, total: 900 }));
}

bindHeader();
bindTheme();
bindReveal();
bindLoopTraces();
loadBeacon();
