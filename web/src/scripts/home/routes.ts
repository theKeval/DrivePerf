// DotStripFill (motion v2): the track fills left to right, each run pops in as the fill reaches it, and the gold
// record lands last with a two-beat ring pulse. 40 % in view, once. Reduced motion: static (server-rendered).
import { reduced, onceInView, cssEase } from '../env';

const strip = document.getElementById('dotstrip');
if (strip && !reduced()) {
  const pos: number[] = JSON.parse(strip.dataset.pos || '[]');
  const fillEl = strip.querySelector<HTMLElement>('.fill')!, dots = [...strip.querySelectorAll<HTMLElement>('i')], pulse = strip.querySelector<HTMLElement>('.rec-pulse')!;
  const FILL = 1500, last = pos[pos.length - 1], POP = cssEase('--spring-pin-pop');
  fillEl.style.transform = 'scaleX(0)'; dots.forEach((d) => (d.style.opacity = '0'));
  onceInView(strip, () => {
    fillEl.animate([{ transform: 'scaleX(0)' }, { transform: 'scaleX(1)' }], { duration: FILL, easing: 'cubic-bezier(0.45, 0, 0.25, 1)', fill: 'forwards' });
    dots.forEach((d, i) => {
      const at = Math.round((pos[i] / last) * FILL * 0.92) + 60;
      d.animate([{ transform: 'scale(0)', opacity: 0 }, { transform: 'scale(1)', opacity: 1 }], { duration: 540, delay: at, easing: POP, fill: 'forwards' });
    });
    pulse.animate([{ transform: 'scale(.6)', opacity: 0.7 }, { transform: 'scale(2.6)', opacity: 0 }], { duration: 1100, delay: FILL + 120, easing: 'cubic-bezier(0.32, 0.72, 0, 1)', iterations: 2 });
  }, { threshold: 0.4 });
}
