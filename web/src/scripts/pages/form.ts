// Small form helpers shared by the mailto request forms (S-WEB-07, S-WEB-09), ported from site.js.
export { EMAIL_RE } from '../../lib/backend/waitlist';

/** Mark a .field invalid with a message (icon + text styled by site.css), or clear it. */
export function setInvalid(fieldEl: HTMLElement | null, msg: string | null): void {
  if (!fieldEl) return;
  const err = fieldEl.querySelector('.field-error');
  const inputs = fieldEl.querySelectorAll('input');
  if (msg) {
    fieldEl.setAttribute('data-invalid', '');
    if (err) err.textContent = msg;
    inputs.forEach((i) => i.setAttribute('aria-invalid', 'true'));
  } else {
    fieldEl.removeAttribute('data-invalid');
    if (err) err.textContent = '';
    inputs.forEach((i) => i.removeAttribute('aria-invalid'));
  }
}

/** Show the [data-view] blocks whose space-separated list includes `state` (state-view fade per motion spec). */
export function showView(attr: string, state: string): void {
  document.querySelectorAll<HTMLElement>(`[${attr}]`).forEach((v) => v.classList.toggle('is-on', (v.getAttribute(attr) || '').split(' ').includes(state)));
}

/** Build a mailto: URL with an encoded subject and body. */
export const mailto = (to: string, subject: string, body: string) =>
  `mailto:${to}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;

/** Open the user's email app with a prefilled message. Nothing is sent until the user presses Send there. */
export function openMail(url: string): void {
  window.location.href = url;
}
