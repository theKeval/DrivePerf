# Session prompt: website on Firebase Hosting with driveperf.com (A18, DRIVE-79)

Paste everything below the line into a **new Claude Code session** opened in
`/Users/keval/data/projects/DrivePerf/DrivePerf`.

**Model:** Sonnet 5.5 is enough for the build. Use Opus 5.5 for step 3 if the motion port looks off.

**Before you start:**
- PR [#16](https://github.com/theKeval/DrivePerf/pull/16) (prototypes, Gate 3) is merged into `main`.
- The ADRs in `docs/architecture/adr/` exist (A10 and A11 from Phase 3/4).
- About 30 minutes of your time is free for the console and DNS steps.

---

```
You are a senior web engineer. Jira: DRIVE-79 (FND-11 "Website on Firebase Hosting"). Related
stories: DRIVE-169 (landing + A/B), DRIVE-171 (waitlist), DRIVE-174 (legal), DRIVE-177 (account
deletion), DRIVE-179 (support/how it records), DRIVE-181 (ride-along), DRIVE-183 (email
preferences), DRIVE-188 (press kit). Follow AGENTS.md: the Jira key goes in the branch, every
commit and the PR title. Work on a branch `DRIVE-79-website-hosting` from an up-to-date `main`.
Move DRIVE-79 to In Progress when you start.

## Context (read first)
- The approved website prototype is in `prototypes/web/` (static HTML/CSS/JS, 11 pages
  S-WEB-01..11, restrained motion graphics). Its README has the component map for the port.
  The per-page specs are in `docs/design/<KEY>/web/design-spec.md` (keys above). They are the
  source of truth for copy, states, motion, accessibility and SEO.
- Decisions: `docs/product/decisions.md`. Architecture: `docs/architecture/adr/` (ADR-004
  backend and migration seam, ADR-009 web stack, ADR-010 Firebase now vs VPS) and
  `docs/architecture/data-model.md`. If an ADR contradicts this prompt, the ADR wins; say so.
- Hosting decision (Keval, 6 Nov 2026; ADR-008/009):
  - A NEW Firebase project named "DrivePerf" (ID `driveperf-prod`) on the free Spark plan
    hosts the site, with `driveperf-dev` for previews.
  - Namecheap is only the registrar/DNS for driveperf.com. Its parking/landing page is replaced.
  - Everything must stay portable to a self-managed VPS later (ADR-010). Keep the site a
    plain static build with no Firebase-only rendering features. Put any backend call behind
    one small client module so the endpoint can change.
- Legal identity: developer Keval Langalia (individual, Ontario). Privacy contact: Keval
  Langalia, privacy@driveperf.com.
- Retention:
  - On-device data is kept until the user deletes it.
  - Pro cloud backup is kept until the user deletes it.
  - When Pro ends, the cloud copy is kept 30 days, then deleted.
- npm on this machine: use `node /opt/homebrew/lib/node_modules/npm/bin/npm-cli.js <args>`
  (npx: `.../npx-cli.js`). `npm`/`npx` directly are broken. Never download npm tarballs or
  borrow node_modules.

## Steps
1. **Firebase project (Keval does the console parts; you guide and verify).**
   - Check `firebase --version`. If the CLI is missing, install firebase-tools with the npm
     command above (global or as a devDependency in web/).
   - Ask Keval to run `firebase login` in the terminal themselves. It is interactive; never
     handle their credentials.
   - Ask Keval to create the projects in the console, per ADR-008:
     - **Production**: display name "DrivePerf", project ID `driveperf-prod` (or the nearest
       free ID), on the Spark plan with Google Analytics OFF. The website uses cookie-less
       analytics (ADR-007/009).
     - **Dev**: `driveperf-dev` ("DrivePerf Dev"), for preview and testing.
     - Do NOT accept terms or create projects on their behalf.
   - Record the IDs Keval gets in `.firebaserc` as aliases `prod` and `dev`.
   - Ask Keval to create a Firestore database (Native mode) in each project, in the location
     ADR-004 sets (`us-central1`). The location is permanent, so confirm it with Keval first.
   - Register a Web app in the project. The web config is public, not a secret, but keep it
     in one config module.
2. **Astro port** in `web/` (monorepo layout per ADR-002).
   - Static output only. Use one Astro component per section, following the prototype README
     map, and one page per S-WEB route with clean URLs: `/`, `/waitlist/confirmed`,
     `/privacy`, `/terms`, `/support`, `/how-it-records`, `/delete-account`, `/ride-along`,
     `/email-preferences`, `/press`, and a 404 page.
   - Self-host the fonts or use the system stacks the prototype uses: no Google Fonts call
     (privacy baseline).
   - Pixel and motion parity with the prototype at 390/768/1440. Keep every motion moment,
     prefers-reduced-motion behaviour and the final-state rendering.
   - Lighthouse targets (mobile): Performance ≥ 95, Accessibility 100, Best Practices 100,
     SEO 100. Zero CLS from animations.
   - Add per-page meta/OG tags, an OG image (1200×630, made from the brand assets in
     docs/design/system/assets), `sitemap.xml`, `robots.txt`, favicon set and a web manifest.
   - Analytics must be cookie-less and privacy-preserving, or none at launch if the ADRs
     don't choose a provider. No cookie banner is needed if no cookies are set; verify that
     none are set.
3. **Waitlist (F-WEB-02)** on Spark, with no Cloud Functions.
   - Write from the client to the Firestore collection `waitlist`. Rules allow create only,
     no read/update/delete. Validate fields (email format and length, consent === true,
     variant in [A,B], platform in [ios,android,both], createdAt == request.time). The
     document ID is the SHA-256 of the lowercased email, so duplicates are idempotent.
   - Spam protection: a honeypot field, plus App Check with reCAPTCHA v3 or Enterprise
     enforced on Firestore. Keval creates the reCAPTCHA key in the console; you tell
     Keval exactly where.
   - Implement the prototype's form, submitting, duplicate, network-error and success states.
   - Put the rules in `firebase/firestore.rules` with emulator tests (@firebase/rules-unit-testing).
   - Double opt-in and confirmation emails need a backend (Blaze or the later VPS). For now,
     document this as a follow-up and keep the "we'll email you at launch" copy honest.
4. **Account deletion (F-WEB-04) and email preferences (F-WEB-07).**
   - These need server-side processing. Until the backend exists (DRIVE-76), wire them to
     `mailto:privacy@driveperf.com` with prefilled subject and body, or to a Firestore
     `deletion_requests` create-only collection with the same rules pattern. Pick one and
     note it in the PR.
   - Never show fake success.
5. **Hosting config** in `firebase.json`.
   - cleanUrls, trailingSlash false, the custom 404, and a www → apex 301 (or the reverse,
     per ADR-009).
   - Cache headers: immutable for hashed assets, short for HTML.
   - Security headers: a strict CSP that allows only self plus the Firebase/reCAPTCHA
     endpoints actually used, HSTS (no preload yet), X-Content-Type-Options,
     Referrer-Policy strict-origin-when-cross-origin, and a Permissions-Policy that denies
     camera/mic/geolocation.
   - Add `/.well-known/apple-app-site-association` and `/.well-known/assetlinks.json`
     placeholders with the correct content-type, for future universal/app links.
6. **CI.**
   - Add `.github/workflows/web-deploy.yml` using FirebaseExtended/action-hosting-deploy: a
     preview channel per PR (expires in 7 days) and the live channel on merge to main.
   - Build and run the rules tests first.
   - Keval creates the service account and the secret FIREBASE_SERVICE_ACCOUNT_DRIVEPERF.
     Give Keval the exact `firebase init hosting:github` or console steps. Never print or
     commit keys.
7. **First deploy.** Deploy to a preview channel. Check every page, state, link, motion and
   header there (curl -I for headers), then share the preview URL with Keval. Deploy live
   only after Keval says so.
8. **Custom domain: driveperf.com + www.**
   - Write `docs/launch/custom-domain.md` with the exact steps: Firebase console → Hosting →
     Add custom domain (apex and www, redirect choice per step 5), then the DNS records
     Firebase shows (TXT for ownership, plus A/AAAA or CNAME values). Copy the actual values
     from the console via Keval; don't guess them.
   - Write a table of what Keval changes in Namecheap → Domain List → Manage → Advanced DNS:
     remove the parking-page CNAME/URL-redirect records, then add the Firebase records.
   - Before anything is removed, ask Keval for a screenshot or list of the current Namecheap
     records. KEEP any MX, SPF, DKIM or DMARC records (email).
   - You must NOT change DNS yourself or ask for Namecheap credentials.
   - After Keval saves the records, verify propagation with `dig +short driveperf.com A`,
     `dig TXT`, `dig www.driveperf.com`, and the console status (it can take up to 24 h).
     Then confirm HTTPS (the certificate is auto-provisioned), the www redirect and HSTS.
9. **Email addresses used on the site.** The site uses privacy@, support@ and hello@driveperf.com.
   - Tell Keval to set up free email forwarding in Namecheap (Domain → Redirect Email) to
     their inbox, or a mailbox provider. Add the SPF record that the forwarding needs.
   - Verify the MX records exist with dig before launch.
10. **Done.**
    - Open the PR "DRIVE-79: website on Firebase Hosting with driveperf.com" with a summary,
      screenshots, Lighthouse scores, the preview URL, and the follow-ups (double opt-in
      email, deletion processing, analytics provider, counsel review of privacy and terms).
    - Comment on DRIVE-79 and the F-WEB stories, then move DRIVE-79 to Done after the
      custom domain serves HTTPS.
    - Report to Keval what they still have to do.

Rules:
- Ask before any outward-facing or irreversible step: live deploy, accepting terms,
  creating cloud resources.
- Never handle Keval's passwords, API secrets or DNS credentials.
- Never commit secrets.
- Report honestly what was verified and what wasn't.
```
