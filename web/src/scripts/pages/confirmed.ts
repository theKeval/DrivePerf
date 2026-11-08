// S-WEB-02: "Copy link to driveperf.com" (plain link, no referral tracking). The platform-specific sentences
// follow ?platform= through PlatformText and html[data-pl], set by the inline boot script before first paint.
import { track } from '../analytics';

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
