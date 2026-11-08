// StoryStack (motion v2), ported from prototypes/web/js/home.js. Each frame dwells 3.4 s while its progress
// segment fills, then flies up and out; the next card springs forward. Plays only while ≥ 45 % in view and the tab
// is visible; pauses on hover and with the Pause button (WCAG 2.2.2). Steps are buttons that jump to a frame.
// Reduced motion: static row of frames (CSS decides it before paint; this sets [data-static] to match).
import { reduced } from '../env';
import { drawOn } from '../motion';
import { icon } from '../../lib/icons';
import { animateRing, animateGG } from './ring';

const sec = document.querySelector<HTMLElement>('.story-scroll');
const stack = document.getElementById('stack');
const stat = document.getElementById('stack-static');
const ggEl = document.getElementById('story-gg');

if (sec && stack && stat && ggEl) {
  const gg: [number, number][] = JSON.parse(ggEl.textContent || '[]');
  const steps = [...document.querySelectorAll<HTMLLIElement>('#story-steps li')];
  const toggle = document.getElementById('story-toggle') as HTMLButtonElement | null;

  if (reduced()) {
    sec.setAttribute('data-static', '');
    steps.forEach((s) => { s.classList.add('is-active'); s.querySelector('button')?.setAttribute('tabindex', '-1'); });
    if (toggle) toggle.hidden = true;
    stat.querySelectorAll<HTMLElement>('[data-ring-host] .ring').forEach((r) => animateRing(r));
    stat.querySelectorAll<SVGSVGElement>('[data-gg] svg').forEach((s) => animateGG(s, gg));
  } else {
    const cards = [...stack.children] as HTMLElement[], n = cards.length, DWELL = 3400;
    let idx = 0, timer = 0, fill: Animation | null = null, stopGG: (() => void) | null = null;
    let playing = true, visible = false, hover = false, entered = -1;
    const place = () => cards.forEach((c, i) => {
      const d = (i - idx + n) % n; let tf: string, op: number;
      if (d === 0) { tf = 'none'; op = 1; }
      else if (d === n - 1) { tf = 'translate3d(-6%, -112%, 0) rotate(-8deg)'; op = 0; }
      else if (d <= 3) { tf = `translate3d(${d * 26}px, ${d * 14}px, ${-d * 70}px) rotate(${d * 3.2}deg)`; op = 1 - d * 0.2; }
      else { tf = 'translate3d(104px, 56px, -280px) rotate(12.8deg)'; op = 0; }
      c.style.transform = tf; c.style.opacity = String(op); c.style.zIndex = String(20 - d);
      c.style.filter = d > 0 && d < n - 1 ? `brightness(${1 - d * 0.18})` : '';
    });
    const running = () => playing && visible && !hover && !document.hidden;
    const ggSvg = () => cards[2]?.querySelector<SVGSVGElement>('svg.gg') ?? null;
    const effects = () => {
      if (stopGG) { stopGG(); stopGG = null; }
      const c = cards[idx];
      if (entered !== idx) {
        entered = idx;
        if (idx === 1) { const p = c.querySelector<SVGPathElement>('.sf-draw'); if (p) drawOn(p, { duration: 1800, delay: 200 }); }
        if (idx === 3) animateRing(c.querySelector<HTMLElement>('.ring'), { delay: 250, spring: true });
      }
      if (idx === 2 && running()) stopGG = animateGG(ggSvg(), gg);
    };
    const arm = () => {
      clearTimeout(timer);
      if (!running()) { fill?.pause(); if (stopGG) { stopGG(); stopGG = null; } return; }
      fill?.play();
      if (idx === 2 && !stopGG) stopGG = animateGG(ggSvg(), gg);
      const left = fill ? Math.max(0, DWELL - Number(fill.currentTime || 0)) : DWELL;
      timer = window.setTimeout(() => go(idx + 1), left);
    };
    const go = (i: number) => {
      idx = (i + n) % n; place();
      steps.forEach((s, k) => { s.classList.toggle('is-active', k === idx); s.querySelector('button')?.setAttribute('aria-current', k === idx ? 'step' : 'false'); });
      if (idx === 0) entered = -1;
      fill?.cancel();
      const b = cards[idx].querySelector<HTMLElement>('.story-progress .cur b');
      fill = b ? b.animate([{ transform: 'scaleX(0)' }, { transform: 'scaleX(1)' }], { duration: DWELL, easing: 'linear', fill: 'forwards' }) : null;
      effects(); arm();
    };
    steps.forEach((s, k) => s.querySelector('button')?.addEventListener('click', () => go(k)));
    stack.addEventListener('pointerenter', () => { hover = true; arm(); });
    stack.addEventListener('pointerleave', () => { hover = false; arm(); });
    toggle?.addEventListener('click', () => {
      playing = !playing; toggle.setAttribute('aria-pressed', String(!playing));
      toggle.innerHTML = playing ? `${icon('pause')}<span>Pause</span>` : `${icon('play')}<span>Play</span>`;
      arm();
    });
    document.addEventListener('visibilitychange', arm);
    if ('IntersectionObserver' in window) new IntersectionObserver((en) => { visible = en[0].isIntersecting; arm(); }, { threshold: 0.45 }).observe(stack);
    else visible = true;
    place(); go(0);
  }
}
