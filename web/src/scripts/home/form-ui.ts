// Form presentation helpers (ported from prototypes/web/js/site.js): field error state, busy button, banner.
import { icon } from '../../lib/icons';

export function setInvalid(fieldEl: HTMLElement | null, msg?: string | null): void {
  if (!fieldEl) return;
  const err = fieldEl.querySelector('.field-error');
  if (msg) {
    fieldEl.setAttribute('data-invalid', '');
    if (err) err.textContent = msg;
    fieldEl.querySelectorAll('input').forEach((i) => i.setAttribute('aria-invalid', 'true'));
  } else {
    fieldEl.removeAttribute('data-invalid');
    if (err) err.textContent = '';
    fieldEl.querySelectorAll('input').forEach((i) => i.removeAttribute('aria-invalid'));
  }
}

export function setBusy(btn: HTMLButtonElement | null, busy: boolean, label = 'Working…'): void {
  if (!btn) return;
  if (busy) {
    btn.dataset.label = btn.dataset.label || btn.innerHTML;
    btn.setAttribute('aria-disabled', 'true');
    btn.innerHTML = `<span class="spinner" aria-hidden="true"></span>${label}`;
  } else {
    btn.removeAttribute('aria-disabled');
    if (btn.dataset.label) btn.innerHTML = btn.dataset.label;
  }
}

export type BannerKind = 'info' | 'error' | 'warn' | null;
/** `title` and `body` are plain text (escaped); body renders on a new line. */
export function banner(el: HTMLElement | null, kind: BannerKind, title = '', body = ''): void {
  if (!el) return;
  if (!kind) { el.className = 'form-banner'; el.innerHTML = ''; return; }
  const ic = ({ info: 'info', error: 'alert', warn: 'alert' } as const)[kind];
  el.className = `form-banner form-banner--${kind} is-shown`;
  const wrap = document.createElement('div');
  const b = document.createElement('b'); b.textContent = title; wrap.appendChild(b);
  if (body) { wrap.appendChild(document.createElement('br')); wrap.appendChild(document.createTextNode(body)); }
  el.innerHTML = icon(ic);
  el.appendChild(wrap);
}
