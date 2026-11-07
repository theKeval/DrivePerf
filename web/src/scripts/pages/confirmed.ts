// S-WEB-02: platform-specific sentences from ?platform= (ios | android | both), and "Copy link to driveperf.com"
// (plain link, no referral tracking).
import { track } from '../analytics';

const pl = new URLSearchParams(location.search).get('platform');
const key = pl === 'android' ? 'android' : pl === 'both' ? 'both' : 'ios';
document.querySelectorAll<HTMLElement>('[data-pl]').forEach((el) => { const t = el.dataset[key]; if (t) el.textContent = t; });

const btn = document.getElementById('copy-link') as HTMLButtonElement | null;
const status = document.getElementById('copy-status');
let reset = 0;
btn?.addEventListener('click', async () => {
  clearTimeout(reset);
  try {
    await navigator.clipboard.writeText('https://driveperf.com');
    btn.innerHTML = (btn.dataset.iconCheck ?? '') + 'Link copied';
    if (status) status.textContent = '';
    track('waitlist_link_copied');
  } catch {
    // Clipboard blocked (permissions, insecure context): say so and show the link to copy by hand.
    if (status) status.textContent = 'Couldn’t copy automatically. The link is driveperf.com';
  }
  reset = window.setTimeout(() => { btn.innerHTML = (btn.dataset.iconCopy ?? '') + 'Copy link to driveperf.com'; }, 2200);
});
