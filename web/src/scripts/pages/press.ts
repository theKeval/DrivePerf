// S-WEB-10 Press kit: Story preview frames animate once in view (ring reveal), the friction-circle G-dot replays
// while visible. Reduced motion: final states.
import { onceInView, reduced } from '../env';
import { animateRing, animateGG } from './viz';

const host = document.getElementById('press-story');
if (host) {
  // Start the rings empty (they are far below the fold), so the reveal never jumps back from the final value.
  if (!reduced()) host.querySelectorAll<HTMLElement>('.ring').forEach((r) => {
    const arc = r.querySelector<SVGCircleElement>('.ring-arc'), num = r.querySelector('.ring-num');
    if (arc && num) { arc.style.strokeDashoffset = arc.dataset.c!; num.textContent = '0'; }
  });
  const pts = JSON.parse(host.dataset.gg || '[]') as [number, number][];
  onceInView(host, () => {
    host.querySelectorAll<HTMLElement>('.ring').forEach((r) => animateRing(r));
    host.querySelectorAll<SVGSVGElement>('svg.gg').forEach((s) => animateGG(s, pts));
  }, { threshold: 0.3 });
}
