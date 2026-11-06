/* WaitlistForm (S-WEB-01 → S-WEB-02) — F-WEB-02. Non-submitting mock.
   Production: POST to a create-only Firestore collection {email, platform, country, consent:{given, textVersion, at, sourcePage}, variant};
   honeypot + rate limit; duplicate email is idempotent ("You're already on the list").
   Prototype triggers: marcus@example.com → duplicate · error@example.com → network error. */
(function () {
  'use strict';
  const CONSENT_TEXT_VERSION = 'wl-consent-v1 (6 Nov 2026)';
  window.DPW.onReady(() => {
    const form = document.getElementById('waitlist');
    if (!form) return;
    const $ = (id) => document.getElementById(id);
    const email = $('wl-email'), btn = $('wl-submit'), bannerEl = $('wl-banner');
    const fields = { email: $('f-email'), platform: $('f-platform'), country: $('f-country'), consent: $('f-consent') };
    const { setInvalid, setBusy, banner, EMAIL_RE, track } = window.DPW;

    function validate({ focus = true } = {}) {
      const errs = {};
      const v = email.value.trim();
      if (!v) errs.email = 'Enter your email address.';
      else if (!EMAIL_RE.test(v)) errs.email = 'That email doesn’t look right. Check for a typo, like a missing “.com”.';
      if (!form.platform.value) errs.platform = 'Choose iPhone or Android, so we invite you to the right beta.';
      if (!form.country.value) errs.country = 'Choose your country.';
      if (!form.consent.checked) errs.consent = 'Tick the box so we can email you. That’s the only thing we use your email for.';
      Object.entries(fields).forEach(([k, el]) => setInvalid(el, errs[k]));
      const first = Object.keys(errs)[0];
      if (first) {
        banner(bannerEl, 'error', `Check ${Object.keys(errs).length === 1 ? 'one thing' : Object.keys(errs).length + ' things'} below.`, '');
        if (focus) (fields[first].querySelector('input')).focus({ preventScroll: false });
      } else banner(bannerEl, null);
      return !first;
    }
    ['input', 'change'].forEach((ev) => form.addEventListener(ev, (e) => {
      const f = e.target.closest('.field');
      if (f && f.hasAttribute('data-invalid')) setInvalid(f, null);
      if (!form.querySelector('[data-invalid]') && bannerEl.classList.contains('form-banner--error')) banner(bannerEl, null);
    }));

    function submit({ simulate } = {}) {
      if (form.company.value) return; // honeypot: silently drop bots
      if (!validate()) { track('waitlist_validation_error', {}); return; }
      const addr = email.value.trim().toLowerCase();
      setBusy(btn, true, 'Joining…');
      form.setAttribute('aria-busy', 'true');
      if (simulate === 'hold') return; // "submitting" state: stays busy
      setTimeout(() => {
        form.removeAttribute('aria-busy');
        setBusy(btn, false);
        if (addr === 'error@example.com' || simulate === 'networkErr') {
          banner(bannerEl, 'error', 'We couldn’t reach the list.', '<br>Your details weren’t saved. Check your connection and try again.');
          return;
        }
        if (addr === 'marcus@example.com' || simulate === 'duplicate') {
          banner(bannerEl, 'info', 'You’re already on the list.', '<br>Nothing else to do. We’ll email you when the beta opens.');
          return;
        }
        const payload = { platform: form.platform.value, country: form.country.value, variant: form.variant.value, consentTextVersion: CONSENT_TEXT_VERSION, sourcePage: 'S-WEB-01' };
        track('waitlist_submitted', { variant: payload.variant, platform: payload.platform, country: payload.country });
        location.href = window.DPW.url('pages/waitlist-confirmed.html') + `?platform=${payload.platform}&country=${payload.country}` + (location.search.includes('theme=light') ? '&theme=light' : '') + '#S-WEB-02';
      }, simulate ? 0 : 900);
    }
    form.addEventListener('submit', (e) => { e.preventDefault(); submit(); });

    // Prototype states
    const fill = (mail, platform = 'ios', country = 'CA', consent = true) => {
      email.value = mail;
      form.querySelectorAll('input[name=platform]').forEach((i) => (i.checked = i.value === platform));
      form.querySelectorAll('input[name=country]').forEach((i) => (i.checked = i.value === country));
      form.consent.checked = consent;
    };
    document.addEventListener('dpw:state', (e) => {
      const st = e.detail.state;
      Object.values(fields).forEach((f) => setInvalid(f, null)); banner(bannerEl, null); setBusy(btn, false);
      if (st === 'formErr') { fill('marcus@example', '', '', false); validate({ focus: false }); }
      else if (st === 'submitting') { fill('marcus.chen@example.com'); submit({ simulate: 'hold' }); }
      else if (st === 'duplicate') { fill('marcus@example.com'); submit({ simulate: 'duplicate' }); }
      else if (st === 'networkErr') { fill('marcus.chen@example.com'); submit({ simulate: 'networkErr' }); }
      else if (e.detail.explicit) fill('', '', '', false);
    });
  });
})();
