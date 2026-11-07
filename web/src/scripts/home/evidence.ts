// Evidence panel: trace draws on when in view; "Was it needed?" chips toggle.
import { onceInView } from '../env';
import { drawOn } from '../motion';

const tr = document.getElementById('evidence-trace');
onceInView(tr, () => { const p = tr?.querySelector<SVGPathElement>('.tr-lon'); if (p) drawOn(p, { duration: 1400 }); });
const chips = document.querySelectorAll<HTMLButtonElement>('.answer-row .chip');
chips.forEach((b) => b.addEventListener('click', () => chips.forEach((x) => x.setAttribute('aria-pressed', String(x === b)))));
