// WaitlistForm island (F-WEB-02, S-WEB-01 → S-WEB-02), ported from prototypes/web/js/waitlist.js.
// States: formErr (summary banner + focus first invalid field), submitting ("Joining…", aria-busy), duplicate
// (info banner, idempotent), network error (error banner, values kept), success → /waitlist/confirmed.
// Spam (ADR-007): honeypot `company` + minimum 2 s from page load to submit; either one drops silently.
import { backend, validateWaitlist, BackendUnavailableError, type WaitlistErrors, type WaitlistField } from '../../lib/backend';
import { SITE } from '../../config/site';
import { track } from '../analytics';
import { setInvalid, setBusy, banner } from './form-ui';

const MIN_FILL_MS = 2000;
const form = document.getElementById('waitlist') as HTMLFormElement | null;

if (form) {
  const $ = (id: string) => document.getElementById(id);
  const email = $('wl-email') as HTMLInputElement, btn = $('wl-submit') as HTMLButtonElement, bannerEl = $('wl-banner');
  const fields: Record<WaitlistField, HTMLElement | null> = { email: $('f-email'), platform: $('f-platform'), country: $('f-country'), consent: $('f-consent') };
  const radio = (name: string) => (form.querySelector<HTMLInputElement>(`input[name="${name}"]:checked`)?.value ?? '');
  const consent = form.querySelector<HTMLInputElement>('input[name="consent"]')!;
  const honeypot = form.querySelector<HTMLInputElement>('input[name="company"]')!;
  const variantInput = form.querySelector<HTMLInputElement>('input[name="variant"]')!;
  let busy = false;

  btn.disabled = false; // the button ships disabled so a no-JS visit can't post the form anywhere

  form.addEventListener('focusin', () => backend.warmUp(), { once: true });

  function show(errs: WaitlistErrors, { focus = true } = {}): boolean {
    (Object.keys(fields) as WaitlistField[]).forEach((k) => setInvalid(fields[k], errs[k]));
    const keys = Object.keys(errs) as WaitlistField[];
    if (keys.length) {
      banner(bannerEl, 'error', `Check ${keys.length === 1 ? 'one thing' : keys.length + ' things'} below.`);
      if (focus) fields[keys[0]]?.querySelector('input')?.focus();
      return false;
    }
    banner(bannerEl, null);
    return true;
  }

  (['input', 'change'] as const).forEach((ev) => form.addEventListener(ev, (e) => {
    const f = (e.target as Element).closest<HTMLElement>('.field');
    if (f && f.hasAttribute('data-invalid')) setInvalid(f, null);
    if (!form.querySelector('[data-invalid]') && bannerEl?.classList.contains('form-banner--error')) banner(bannerEl, null);
  }));

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (busy) return;
    if (honeypot.value || performance.now() < MIN_FILL_MS) return; // bots: silently dropped, nothing written
    const input = { email: email.value, platform: radio('platform'), country: radio('country'), consent: consent.checked };
    if (!show(validateWaitlist(input))) { track('waitlist_validation_error'); return; }
    const variant = document.documentElement.dataset.variant === 'B' ? 'B' : 'A';
    variantInput.value = variant;
    busy = true;
    setBusy(btn, true, 'Joining…');
    form.setAttribute('aria-busy', 'true');
    try {
      const res = await backend.joinWaitlist({ ...input, variant, sourcePage: 'S-WEB-01', consentTextVersion: SITE.waitlistConsentVersion });
      if (res === 'duplicate') {
        banner(bannerEl, 'info', 'You’re already on the list.', 'Nothing else to do. We’ll email you when the beta opens.');
      } else {
        track('waitlist_submitted', { variant, platform: input.platform, country: input.country });
        const q = new URLSearchParams({ platform: input.platform, country: input.country });
        location.href = `/waitlist/confirmed?${q}`;
        return; // keep the busy state while the confirmation page loads
      }
    } catch (err) {
      if (!(err instanceof BackendUnavailableError)) console.error(err);
      banner(bannerEl, 'error', 'We couldn’t reach the list.', 'Your details weren’t saved. Check your connection and try again.');
    }
    busy = false;
    form.removeAttribute('aria-busy');
    setBusy(btn, false);
  });
}
