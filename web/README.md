# driveperf.com (web/)

Astro static site ([ADR-009](../docs/architecture/adr/ADR-009-web-stack.md)), ported from the approved prototype `prototypes/web/` (S-WEB-01 … 11). Hosted on Firebase Hosting now, and portable to any static server later ([ADR-010](../docs/architecture/adr/ADR-010-firebase-now-vs-vps.md)). Jira: DRIVE-79.

## Commands

npm on this machine: `node /opt/homebrew/lib/node_modules/npm/bin/npm-cli.js …` (plain `npm` elsewhere).

```bash
npm ci
npm run dev                      # local, PUBLIC_DP_ENV=local: waitlist writes go to the Firestore emulator
npm run build                    # dist/ + scripts/check-dist.mjs (CSP hash, privacy baseline, routes, 500 KB budget)
PUBLIC_DP_ENV=prod npm run build # what CI ships to driveperf-prod; `dev` is what preview channels get
npm run check                    # astro check (TypeScript)
npx lhci autorun --config=lighthouserc.cjs   # Lighthouse CI on dist/ (mobile)
npm run fixtures                 # re-extract src/data/fixtures.json from prototypes/shared/data.js
npm run og                       # regenerate the OG image, favicons and manifest icons from docs/design/system/assets
```

Waitlist end to end on your machine: `cd ../backend/firebase && npx firebase emulators:start --only firestore --project demo-driveperf`, then `npm run dev` here.

## Layout

| Path | What |
|---|---|
| `src/pages/` | one page per S-WEB route; `build.format: 'file'` → `privacy.html`, served at `/privacy` |
| `src/layouts/Base.astro` | head (meta, OG, JSON-LD), header, footer, shared runtime |
| `src/components/site|home|…` | one component per prototype section (README component map in `prototypes/web/`) |
| `src/lib/` | build-time renderers ported from the prototype (icons, Loop, geometry, mockups) and `boot.ts` (the only inline script) |
| `src/lib/backend/` | **the only backend seam.** Pages call `backend.joinWaitlist()`; today that's Firestore Lite (`firestore-waitlist.ts`). Moving to `POST https://api.driveperf.com/v1/waitlist` changes this folder only |
| `src/config/site.ts` | the only config module: Firebase web config per environment and the Cloudflare beacon token (public values) |
| `src/scripts/` | client islands (external modules; the CSP is `script-src 'self'` plus one pinned hash) |
| `src/data/fixtures.json` | the slice of the shared design fixtures the site draws (build-time only) |

## Privacy baseline

No cookies. No web fonts: system font stacks only, no Google Fonts request. No Google Analytics and no reCAPTCHA. Page views go to Cloudflare Web Analytics, only in the prod build, and only when the browser sends neither Global Privacy Control nor Do Not Track. Theme choice is kept in `sessionStorage`, for the tab only. `check-dist.mjs` fails the build if Google Fonts, gtag, reCAPTCHA or a cookie write appears.

## Portability (ADR-010)

`dist/` is plain files. To serve it from Caddy on a VPS, reproduce `backend/firebase/firebase.json`:

```caddyfile
driveperf.com {
  root * /srv/driveperf/dist
  encode zstd gzip
  try_files {path} {path}.html
  rewrite /r/* /ride-along.html
  header {
    Strict-Transport-Security "max-age=31536000; includeSubDomains"
    Content-Security-Policy "<same value as firebase.json>"
    X-Content-Type-Options nosniff
    X-Frame-Options DENY
    Referrer-Policy strict-origin-when-cross-origin
    Permissions-Policy "camera=(), microphone=(), geolocation=(), payment=(), usb=(), browsing-topics=()"
    Cross-Origin-Opener-Policy same-origin
    Cache-Control "public, max-age=300, must-revalidate"
  }
  header /_astro/* Cache-Control "public, max-age=31536000, immutable"
  header /.well-known/apple-app-site-association Content-Type application/json
  handle_errors { rewrite * /404.html
    file_server }
  file_server
}
www.driveperf.com { redir https://driveperf.com{uri} permanent }
```

## Known follow-ups (not in DRIVE-79)

- **Double opt-in and confirmation emails** need a backend (Blaze Cloud Functions or the API/VPS, DRIVE-76) and a transactional email provider (decisions.md: ZeptoMail, SES or Resend). Until then, the copy only promises "we'll email you when the beta opens and at launch".
- **Account deletion (S-WEB-07) and email preferences (S-WEB-09)** open a prefilled `mailto:privacy@driveperf.com`. Processing happens by hand until the API exists (DRIVE-76).
- **Product analytics events** (`waitlist_submitted`, …) are only dispatched as DOM `dp:track` events. Cloudflare Web Analytics counts page views only. A first-party `POST /v1/web/events` can consume them later (ADR-007).
- **No-JS waitlist sign-up** waits for the API (ADR-009); until then a `<noscript>` mailto fallback is shown.
