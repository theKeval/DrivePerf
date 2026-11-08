// A/B positioning test (F-WEB-01). The head boot script picks html[data-variant] before paint (random per
// page load, no cookies or storage); this records it in the form and the title, and emits the event.
import { track } from '../analytics';

const v = document.documentElement.dataset.variant === 'B' ? 'B' : 'A';
const input = document.getElementById('wl-variant') as HTMLInputElement | null;
if (input) input.value = v;
if (v === 'B') document.title = 'DrivePerf: Every drive, decoded.';
track('experiment_assigned', { variant: v });
