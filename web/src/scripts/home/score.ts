// ScoreRing spring (motion v2): damped count-up when 35 % in view, once.
import { reduced, onceInView } from '../env';
import { animateRing, lockWidth } from './ring';

const bigRing = document.querySelector<HTMLElement>('#score-ring .ring');
if (bigRing) {
  if (!reduced()) {
    const arc = bigRing.querySelector<SVGCircleElement>('.ring-arc')!;
    const num = bigRing.querySelector<HTMLElement>('.ring-num')!;
    lockWidth(num);
    arc.style.strokeDashoffset = arc.dataset.c!;
    num.textContent = '0';
  }
  onceInView(bigRing, () => animateRing(bigRing, { spring: true, delay: 120 }));
}
