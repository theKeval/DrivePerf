// S-WEB-08 Ride-along: read the optional pass code (/r/{code} or ?code=), show the invalid state only for a
// malformed code, point the QR and short URL at the pass link. Codes are opaque: 4+4 letters/digits ("7KQ2-M9XD").
import { track } from '../analytics';

const CODE_RE = /^[A-Z0-9]{4}-[A-Z0-9]{4}$/;
const root = document.querySelector<HTMLElement>('.ra');
const fromPath = /^\/r\/([^/?#]+)\/?$/.exec(location.pathname)?.[1];
const raw = fromPath ? decodeURIComponent(fromPath) : new URLSearchParams(location.search).get('code');
const code = raw?.trim().toUpperCase() ?? null;
const desktop = () => matchMedia('(min-width: 900px)').matches;

if (root) {
  let mode: 'mobile' | 'desktop' | 'invalid' = desktop() ? 'desktop' : 'mobile';
  if (code !== null && !CODE_RE.test(code)) {
    mode = 'invalid';
    root.dataset.raMode = 'invalid';
    document.title = 'This pass link doesn’t work · DrivePerf';
  } else if (code) {
    const url = `https://driveperf.com/r/${code}`;
    document.querySelectorAll<HTMLElement>('[data-ra-short]').forEach((el) => (el.textContent = `driveperf.com/r/${code}`));
    document.querySelectorAll<HTMLAnchorElement>('[data-ra-link]').forEach((a) => (a.href = url));
    document.querySelectorAll<HTMLElement>('[data-ra-open]').forEach((el) => (el.hidden = false));
    const qr = document.getElementById('qr');
    // The QR library loads only when a desktop visitor has a pass code to encode.
    const encode = async () => {
      if (!qr || qr.dataset.code === code) return;
      const { qrSvg } = await import('../../components/pages/qr');
      qr.innerHTML = await qrSvg(url);
      qr.dataset.code = code;
    };
    if (desktop()) void encode();
    else matchMedia('(min-width: 900px)').addEventListener('change', (e) => { if (e.matches) void encode(); });
  }
  track('ridealong_link_opened', { has_app: false, mode });
}
