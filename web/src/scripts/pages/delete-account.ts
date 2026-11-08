// S-WEB-07 Account deletion request. No backend until DRIVE-76, so the request is an email the user sends from
// their own mailbox to privacy@driveperf.com (which also shows they control the address). Never shows a success
// state for something the site didn't do: after submit it says the email app should open and nothing is sent yet.
import { EMAIL_RE, setInvalid, showView, mailto, openMail } from './form';
import { track } from '../analytics';

const TO = 'privacy@driveperf.com';
const SUBJECT = 'Delete my DrivePerf account';
const form = document.getElementById('del-form') as HTMLFormElement | null;
const email = document.getElementById('del-email') as HTMLInputElement | null;
const field = document.getElementById('f-del-email');

function body(addr: string): string {
  return [
    'Please delete my DrivePerf account and everything in cloud backup.',
    '',
    `Account email: ${addr}`,
    '',
    'I understand this deletes:',
    '- my account (sign-in, email, name and account ID)',
    '- everything in cloud backup (trips with their routes, sensor data, scores, routes, cars and settings)',
    '- Ride-along pass records (passes I sent or redeemed)',
    '- my subscription link at RevenueCat, and crash data tied to my installation where the provider allows',
    '',
    'Trips on my phone are not affected. I will cancel any Pro subscription in the App Store or Google Play myself.',
    '',
    'I am sending this from the account email address, or I will confirm a reply sent to it.',
  ].join('\n');
}

if (form && email && field) {
  showView('data-del-view', 'form');
  const validate = (): string | null => {
    const v = email.value.trim();
    const msg = !v ? 'Enter the email address on your DrivePerf account.' : !EMAIL_RE.test(v) ? 'That email doesn’t look right. Check for a typo.' : null;
    setInvalid(field, msg);
    if (msg) email.focus();
    return msg ? null : v;
  };
  form.addEventListener('input', () => setInvalid(field, null));
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const addr = validate();
    if (!addr) return;
    const url = mailto(TO, SUBJECT, body(addr));
    document.querySelectorAll<HTMLElement>('[data-sent-from]').forEach((el) => (el.textContent = addr));
    const again = document.getElementById('del-mail-again') as HTMLAnchorElement | null;
    if (again) again.href = url;
    showView('data-del-view', 'ready');
    (document.getElementById('del-ready-title') as HTMLElement | null)?.focus();
    track('web_deletion_mail_opened');
    openMail(url);
  });
  document.getElementById('del-restart')?.addEventListener('click', () => { showView('data-del-view', 'form'); email.focus(); });
}
