# Firebase backend setup: what Keval does in the consoles (DRIVE-76)

**Who does what:** Keval does every console step, accepts every term and signs in themself. The agent writes config, runs emulator tests and never handles credentials. **Nothing in the DRIVE-76 pull request deploys anything.** Related: [firebase-setup.md](firebase-setup.md) (projects, Firestore, CI auth: done), [custom-domain.md](custom-domain.md), [email.md](email.md), [ADR-004](../architecture/adr/ADR-004-backend-v1-and-migration-seam.md), [ADR-007](../architecture/adr/ADR-007-analytics-crash-flags-privacy.md), [r2-trip-files.md](../architecture/r2-trip-files.md).

Projects: `driveperf-dev` (DrivePerf Dev) and `driveperf-prod` (DrivePerf). Do each step in **dev first**, check it, then prod. All Spark steps are free.

## Part A. Spark scope (do now, no billing)

### A1. Authentication

Firebase console → **Build → Authentication → Get started** → **Sign-in method**.

| Provider | Setting |
|---|---|
| **Google** | Enable. Public-facing name for project: `DrivePerf`. Support email: privacy@driveperf.com if it is a project owner or group, otherwise your own address (the console accepts only owner or group addresses). Save |
| **Email/Password** | Enable the **Email/Password** switch, then enable **Email link (passwordless sign-in)** under it. The console ties the email-link option to this provider; DrivePerf never calls password sign-in (ADR-004 "Not used"). See the risk note below |
| Apple | **Leave off.** It needs the paid Apple Developer account (decisions log). Added later with the Services ID, Team ID, key ID and private key; Android uses the web flow |
| Anonymous, Phone, everything else | Off. Phone is billed per SMS |

Then:

1. **Settings → User actions:** turn **Email enumeration protection** on. Leave "Link accounts that use the same email" as it is (one person, one account across Apple, Google and email link).
2. **Settings → Authorized domains:** make sure the list has `driveperf.com`, `www.driveperf.com`, `driveperf-prod.firebaseapp.com`, `driveperf-prod.web.app` (prod) or the dev equivalents. Remove nothing that was added by default.
3. **Templates → Email link sign-in:** sender name `DrivePerf`, reply-to `support@driveperf.com`. The sender address stays Firebase's default until a custom sender domain is set up with the email provider chosen in [email.md](email.md).
4. **Email-link domain for the apps.** Firebase Dynamic Links ended on 25 Aug 2025, so mobile email-link sign-in uses the project's **Firebase Hosting domain** (`linkDomain` in `ActionCodeSettings`; Android SDK 23.2.0 / BoM 33.9.0 or later, iOS SDK 11.8.0 or later). Use the default `driveperf-prod.firebaseapp.com` (dev: `driveperf-dev.firebaseapp.com`). The apps (DRIVE-67, DRIVE-72) declare it as an Android intent filter and an iOS Associated Domain (`applinks:…`). No console switch is needed now; the first end-to-end test happens in DRIVE-67 and DRIVE-72.

**Risk note (password provider).** Because email link needs the Email/Password provider on, anyone holding the public web API key could create an email-and-password user through the Identity Toolkit REST API. Damage is small (a Free signed-in account holds only an account record; Pro backup needs a paid entitlement), but it is real. Mitigations, in order: App Check enforcement for Authentication once both apps send tokens (A3 below), email enumeration protection (above), the API's per-account rate limits, and, if abuse ever appears, Identity Platform blocking functions (needs Blaze). Keval decides whether this is acceptable; the recommendation is yes.

### A2. Register the native apps (project settings)

**Project settings → General → Your apps → Add app.** Only identifiers and fingerprints go here; the downloaded config files are **not committed** (ADR-008: prod config is injected at build time).

| Project | Android package | iOS bundle ID |
|---|---|---|
| `driveperf-dev` | `com.driveperf.app.dev` | `com.driveperf.app.dev` |
| `driveperf-prod` | `com.driveperf.app` | `com.driveperf.app` |

Nickname `DrivePerf Android` / `DrivePerf iOS` (suffix ` Dev` in the dev project). Android needs **SHA-1 and SHA-256 fingerprints**: the debug keystore's for dev, the Play App Signing certificate's (Play Console → App integrity → App signing) for prod. For iOS the App Store ID and Team ID can wait for the paid Apple account. **Never create a Personal Team for these IDs** (AGENTS.md).

### A3. App Check

**Build → App Check → Apps.**

| App | Provider | When |
|---|---|---|
| iOS | **App Attest** (falls back to DeviceCheck) | needs the paid Apple account; until then the apps use the **Debug provider**: in the iOS Simulator or a dev build, copy the debug token printed in the Xcode console and add it under the app's **⋮ → Manage debug tokens** |
| Android | **Play Integrity** | Play Console → Setup → App integrity → link the Firebase project's Google Cloud project; then register here |
| Web ("DrivePerf Web") | **Not registered.** | see "Waitlist and App Check" below |

**Metrics first, enforcement later.** The API (Phase 6) verifies the `X-Firebase-AppCheck` token itself against the App Check JWKS, so no console enforcement is needed for it. The only console enforcement that matters now is **Authentication**: after both apps have shipped with App Check for about a week and **App Check → APIs → Authentication** shows nearly all requests verified, press **Enforce**. Do not enforce Firestore.

**Waitlist and App Check (decision needed).** The prompt for this ticket asked for the waitlist to be "validated by rules, with App Check enforced". Two facts prevent doing exactly that now: (1) Firestore security rules cannot read App Check tokens; enforcement is a console switch per product, and (2) the live website sends no App Check token, so **Firestore enforcement would block every driveperf.com sign-up**. ADR-007 (resolving PRD OQ-13) also decided against reCAPTCHA at launch because it can set Google cookies, and the current protection is the honeypot, a minimum time-to-submit and strict rules (deployed by DRIVE-79 and unchanged here). The escalation path already exists in [firebase-setup.md §8](firebase-setup.md): reCAPTCHA Enterprise provider, website change, CSP and privacy-note change, then **App Check → Firestore → Enforce** a day after the site is sending tokens. Recommendation: keep ADR-007; enforce only if spam appears.

### A4. Firestore rules and indexes

- **Rules:** unchanged by DRIVE-76 (clients can only create validated waitlist entries; everything else is denied). Rules deploy through `web.yml` as before.
- **Indexes:** `backend/firebase/firestore.indexes.json` now holds the four composite indexes and the field exemptions of [data-model.md §3.5](../architecture/data-model.md). `web.yml` does **not** deploy indexes. After the PR is merged, run once per project (needs your own CLI login):

```bash
cd backend/firebase
node /opt/homebrew/lib/node_modules/npm/bin/npx-cli.js firebase deploy --only firestore:indexes --project dev
# check in the console (Firestore → Indexes), then:
node /opt/homebrew/lib/node_modules/npm/bin/npx-cli.js firebase deploy --only firestore:indexes --project prod
```

### A5. Hosting

Nothing to do for driveperf.com: the live Hosting config (DRIVE-79) is untouched. The prepared config for `api.driveperf.com` (`backend/firebase/api-hosting/firebase.json`, a **separate Hosting target**, rewriting every path to Cloud Run service `api` in `us-central1`) is used only in Phase 6 (Part B).

### A6. Local development against the Emulator Suite

No login and no cloud project are needed (project ID `demo-driveperf`):

```bash
cd backend/firebase
node /opt/homebrew/lib/node_modules/npm/bin/npm-cli.js ci
node /opt/homebrew/lib/node_modules/npm/bin/npm-cli.js ci --prefix ../../web   # the rules tests import the website's waitlist builder
node /opt/homebrew/lib/node_modules/npm/bin/npm-cli.js test                    # Firestore emulator + rules tests
node /opt/homebrew/lib/node_modules/npm/bin/npm-cli.js run emulators           # Auth (9099) and Firestore (8080), for the API and apps
```

## Part B. Blaze scope (Phase 6, only after Keval says so)

Nothing below is done by DRIVE-76; the code is prepared (see the PR). Order matters.

### B1. Upgrade and cap the blast radius

1. Firebase console → **Upgrade → Blaze** on `driveperf-dev` first. Choose the billing account you want to use.
2. Google Cloud console → **Billing → Budgets & alerts → Create budget**: scope the project, amount **US$50**, alert thresholds at **10 %, 50 %, 100 %** (US$5, 25, 50), email to Keval. **A budget alert does not cap spend.** The real limits are Cloud Run `max-instances 3`, the Pro-only upload check and App Check.
3. Repeat for `driveperf-prod` when production needs it.

### B2. Google Cloud (per project)

APIs: `run.googleapis.com`, `artifactregistry.googleapis.com`, `secretmanager.googleapis.com`, `cloudscheduler.googleapis.com`, `identitytoolkit.googleapis.com`.

| Item | Value |
|---|---|
| Artifact Registry | Docker repository `api`, region `us-central1` |
| Runtime service account | `driveperf-api` with `roles/datastore.user`, `roles/firebaseauth.admin`, `roles/secretmanager.secretAccessor` |
| Scheduler service account | `driveperf-scheduler`; granted `roles/run.invoker` on the `api` service |
| Cloud Run service `api` | region `us-central1`, 512 MiB, **min instances 0, max instances 3**, request timeout 60 s, startup CPU boost, allow unauthenticated invocations (the app code checks every token) |
| Cloud Scheduler jobs | `daily`: `17 3 * * *` UTC and `deletion`: `0 * * * *`; HTTP POST to `https://api.driveperf.com/v1/jobs/daily` and `/deletion`, **OIDC token** from `driveperf-scheduler`, audience `https://api.driveperf.com` |
| Hosting site for the API | Hosting → **Add another site**, ID e.g. `driveperf-prod-api`; then `firebase target:apply hosting api <siteId>` and connect the custom domain `api.driveperf.com` (the DNS records come from the console; same procedure as [custom-domain.md](custom-domain.md)). Deploy with `firebase deploy --only hosting:api --config api-hosting/firebase.json` |
| Cloud Run deploy via GitHub | the `github-web-deploy` account of [firebase-setup.md §5](firebase-setup.md) needs `roles/run.admin`, `roles/artifactregistry.writer`, `roles/iam.serviceAccountUser` (on `driveperf-api`); or create a second account `github-api-deploy` bound the same way |

### B3. Cloudflare R2

1. Cloudflare dashboard → **R2 Object Storage → Purchase** (a payment method is required even inside the free tier).
2. Create buckets `driveperf-trips-dev` and `driveperf-trips-prod`; location hint **Eastern North America (ENAM)**; storage class **Standard**; **leave public access off** (no `r2.dev` URL, no custom domain).
3. Bucket **Settings → Object lifecycle rules:** abort incomplete multipart uploads after 1 day; delete objects with prefix `x/` after 7 days.
4. **R2 → Manage API tokens → Create API token:** permission **Object Read & Write**, **specific bucket** only (one token per environment), no TTL. Copy the Access Key ID and Secret Access Key **once**, straight into Secret Manager as `R2_ACCESS_KEY_ID` and `R2_SECRET_ACCESS_KEY`. Do not paste them into chat, a file, or GitHub.
5. Note the Cloudflare **account ID** (R2 overview page): it becomes the plain variable `R2_ACCOUNT_ID`.
6. Add Cloudflare to the privacy policy as a second processor (decisions log) and to the Law 25 assessment.

### B4. Secrets and variables

| Name | Kind | Where | Value / source |
|---|---|---|---|
| `GCP_WIF_PROVIDER`, `GCP_DEPLOY_SA` | variables | GitHub Environments `dev`, `prod` | already set ([firebase-setup.md §5](firebase-setup.md)) |
| `GCP_PROJECT_ID` | variable | GitHub Environments `dev`, `prod` | `driveperf-dev` / `driveperf-prod` |
| `GCP_REGION` | variable | GitHub Environments `dev`, `prod` | `us-central1` |
| `CLOUD_RUN_DEPLOY_ENABLED` | repository variable | GitHub → Settings → Variables | `true` only when B1 to B3 are done (it is unset today, so `backend.yml` deploy jobs stay inert) |
| `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` | secrets | Secret Manager, mounted into Cloud Run | from B3 step 4 |
| `REVENUECAT_WEBHOOK_SECRET`, `REVENUECAT_API_KEY` | secrets | Secret Manager | from the RevenueCat dashboard (Phase 6, ADR-006) |
| `R2_ACCOUNT_ID`, `R2_BUCKET` | plain env vars | Cloud Run service | B3 steps 5 and 2 |
| Trusted token issuer, App Check project number | plain env vars | Cloud Run service | `https://securetoken.google.com/<projectId>`; the project number from Project settings |

No long-lived cloud key is ever created (ADR-008): GitHub reaches Google through Workload Identity Federation, and Cloud Run reaches Secret Manager through its service account.

### B5. First end-to-end check (agent, after B1 to B4, against `driveperf-dev` only)

Deploy `api` to dev, call `GET /healthz`, run `blobs:prepare` with a real dev token and **record in [r2-trip-files.md](../architecture/r2-trip-files.md) §3 whether R2 accepted the signed `content-length` and `x-amz-meta-*` headers**, upload and commit a test file, run the deletion job on a throwaway account, and confirm the bucket prefix is empty.
