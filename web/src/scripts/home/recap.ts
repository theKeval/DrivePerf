// WeekDots (motion v2): one dot per drive drops into its day with the pin-pop spring, 150 ms apart.
// 40 % in view, once. Reduced motion: static (server-rendered).
import { reduced, onceInView, cssEase } from '../env';

const host = document.getElementById('recap-strip');
if (host && !reduced()) {
  const POP = cssEase('--spring-pin-pop');
  const spans = [...host.querySelectorAll<HTMLElement>('span:not(.empty)')];
  spans.forEach((s) => (s.style.opacity = '0'));
  onceInView(host, () => spans.forEach((s, k) => s.animate([{ transform: 'translateY(-28px) scale(.4)', opacity: 0 }, { transform: 'none', opacity: 1 }], { duration: 640, delay: 120 + k * 150, easing: POP, fill: 'forwards' })), { threshold: 0.4 });
}
