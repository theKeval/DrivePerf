// S-WEB-09 Email preferences. No email provider or signed unsubscribe tokens exist yet (DRIVE-76), so
// unsubscribing is an email the user sends to privacy@driveperf.com from the address itself. No fake "unsubscribed".
import { EMAIL_RE, setInvalid, showView, mailto, openMail } from './form';
import { track } from '../analytics';

const TO = 'privacy@driveperf.com';
const SUBJECT = 'Unsubscribe from DrivePerf emails';
const form = document.getElementById('unsub-form') as HTMLFormElement | null;
const email = document.getElementById('unsub-email') as HTMLInputElement | null;
const field = document.getElementById('f-unsub');

if (form && email && field) {
  showView('data-pref-view', 'form');
  form.addEventListener('input', () => setInvalid(field, null));
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const addr = email.value.trim();
    if (!EMAIL_RE.test(addr)) { setInvalid(field, 'Enter the email address the message was sent to.'); email.focus(); return; }
    setInvalid(field, null);
    const url = mailto(TO, SUBJECT, `Please stop all DrivePerf waitlist, beta and launch emails to: ${addr}\n\nI am sending this from that address.`);
    document.querySelectorAll<HTMLElement>('[data-sent-from]').forEach((el) => (el.textContent = addr));
    const again = document.getElementById('unsub-mail-again') as HTMLAnchorElement | null;
    if (again) again.href = url;
    showView('data-pref-view', 'ready');
    (document.getElementById('pref-ready-title') as HTMLElement | null)?.focus();
    track('email_unsubscribe_mail_opened');
    openMail(url);
  });
  document.getElementById('unsub-restart')?.addEventListener('click', () => { showView('data-pref-view', 'form'); email.focus(); });
}
