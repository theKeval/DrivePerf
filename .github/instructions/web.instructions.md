---
applyTo: "web/**"
---

# Website rules (DrivePerf `web/`, driveperf.com)

Astro static site, TypeScript strict ([ADR-009](../../docs/architecture/adr/ADR-009-web-stack.md)); read `web/README.md`.

- **Merging to `main` deploys the live site** to production (`.github/workflows/web.yml`). Every PR gets a Hosting
  preview channel: check it before asking for review.
- **Backend seam:** pages call `backend.joinWaitlist()` from `src/lib/backend/` only; never import Firebase elsewhere.
  Config values (public) live in `src/config/site.ts`.
- **Privacy baseline:** no cookies, no web fonts, no Google Analytics or reCAPTCHA; Cloudflare Web Analytics only in
  the prod build and only without Global Privacy Control or Do Not Track. `scripts/check-dist.mjs` fails the build on
  violations: never loosen it.
- **Quality bars:** semantic HTML, keyboard accessibility, WCAG AA contrast, Lighthouse budgets (`lighthouserc.cjs`),
  the 500 KB page budget and the CSP (`script-src 'self'` plus one pinned hash). Components follow the prototype in
  `prototypes/web/`; design tokens come from `docs/design/system/`.
- **npm on Keval's Mac:** `node /opt/homebrew/lib/node_modules/npm/bin/npm-cli.js <args>`; use the project's own
  `package-lock.json` (`npm ci`).
