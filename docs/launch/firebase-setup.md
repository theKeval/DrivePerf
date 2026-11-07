# Firebase setup for driveperf.com (DRIVE-79)

**Who does what:** Keval does every console step, accepts every term and signs in themself. The agent never handles passwords, keys or DNS credentials. It writes the config, runs the checks and verifies the results. **Related:** [ADR-004](../architecture/adr/ADR-004-backend-v1-and-migration-seam.md) (region), [ADR-008](../architecture/adr/ADR-008-ci-cd-and-environments.md) (projects, CI auth), [ADR-009](../architecture/adr/ADR-009-web-stack.md) (site), [custom-domain.md](custom-domain.md), [email.md](email.md).

| | Production | Dev / previews |
|---|---|---|
| Display name | **DrivePerf** | **DrivePerf Dev** |
| Project ID (permanent) | `driveperf-prod` (or the nearest free ID) | `driveperf-dev` |
| Plan | Spark (free) | Spark (free) |
| Google Analytics | **Off** (the site uses cookie-less Cloudflare Web Analytics, ADR-007) | Off |
| Firestore | Native mode, Standard edition, **`us-central1`** (ADR-004; permanent) | same |
| Hosting | `driveperf.com` + `www` (live channel) | PR preview channels |

The repo already holds the config: `backend/firebase/firebase.json` (Hosting + headers + Firestore), `.firebaserc` (aliases `prod` and `dev`), `firestore.rules` and the web config module `web/src/config/site.ts`.

## 1. CLI login (done)

`firebase --version` is 15.35.0 and the CLI is signed in as keval.langalia@gmail.com (`firebase login:list`). If it ever needs signing in again, run `firebase login` yourself in a terminal. It is interactive.

## Status (7 Nov 2026)

§2–§4 are done in both projects, verified with `firebase projects:list` and `firestore:databases:get`.
- **Prod:** Keval created `driveperf-prod` ("DrivePerf") in the console. At Keval's request, the agent then unlinked Google Analytics (property 563092806 still exists in the GA account; delete it there if unused), created Firestore and registered "DrivePerf Web".
- **Dev:** the agent created `driveperf-dev` ("DrivePerf Dev") with `firebase projects:create`, which adds no Analytics. It registered "DrivePerf Web" with `firebase apps:create` and created Firestore in the console.
- **Both:** Spark plan, no Analytics, Firestore Native / Standard / `us-central1`. The web configs are in `web/src/config/site.ts`.
- **§5 is also done.** At Keval's request, the agent ran it with Keval's signed-in `gcloud` and `gh`:
  - Each project has a `github` pool, a `driveperf-repo` provider and a `github-web-deploy` account with the four roles. Each account is bound to its own GitHub Environment.
  - GitHub Environment `dev` has its two variables.
  - GitHub Environment `prod` has its two variables, required reviewer theKeval, and deployment branch `main` only.

## 2. Create the two projects (console)

1. Go to <https://console.firebase.google.com> → **Create a project** (or **Add project**).
2. Name it **DrivePerf**. Under the name, click the project ID and set it to **`driveperf-prod`**. If that ID is taken, use the closest free ID and tell the agent the exact ID.
3. **Google Analytics: turn the switch off.** Then **Create project**. You'll be asked to accept the Firebase terms, which only you can do.
4. Repeat for **DrivePerf Dev** with the ID **`driveperf-dev`**.
5. Both projects start on **Spark**. Don't add billing.

Then tell the agent both project IDs. It updates `.firebaserc` if they differ and runs `firebase projects:list` to check.

## 3. Firestore database in each project (console)

**Location confirmed by Keval (7 Nov 2026): `us-central1` (ADR-004). It can never be changed.**

Firebase console → *Build* → **Firestore Database** → **Create database**:
- Edition **Standard**, database ID **(default)**.
- Location **`us-central1` (Iowa)**. It's a regional location; don't pick `nam5`.
- Start in **production mode**, which denies everything. The agent then deploys `firestore.rules`.

Do this in both projects.

## 4. Register the web app in each project (console)

Project overview → **Add app** → **Web** (`</>`). Nickname **DrivePerf Web**. **Don't** tick "Also set up Firebase Hosting" (the repo already has the Hosting config) → **Register app**. Copy the `firebaseConfig` values shown (`apiKey`, `authDomain`, `projectId`, `appId`) and paste them to the agent. They are public identifiers, not secrets; access is enforced by the Firestore rules. The agent puts them in `web/src/config/site.ts`, which is the only config module.

Optional hardening: Google Cloud console → *APIs & Services → Credentials* → the "Browser key (auto created by Firebase)" → **Application restrictions: Websites** with `driveperf.com/*`, `www.driveperf.com/*`, `driveperf-prod.web.app/*` (prod key) or `driveperf-dev--*.web.app/*` and `driveperf-dev.web.app/*` (dev key).

## 5. CI deploy credentials: Workload Identity Federation (ADR-008)

ADR-008 forbids long-lived cloud keys in this public repository, and Keval chose the keyless setup (7 Nov 2026). So CI does **not** use a `FIREBASE_SERVICE_ACCOUNT_*` JSON secret or `firebase init hosting:github`, which creates such a key, or `FirebaseExtended/action-hosting-deploy`, which accepts only a key. Instead, GitHub's OIDC token is exchanged for short-lived credentials of a deploy service account, with `google-github-actions/auth`.

Run these yourself, once per project (`gcloud auth login` first; `gcloud` is installed at `/opt/homebrew/bin/gcloud`). For the dev project, use `PROJECT=driveperf-dev` and `ENV=dev`. For prod, use `PROJECT=driveperf-prod` and `ENV=prod`.

```bash
PROJECT=driveperf-dev; ENV=dev
NUMBER=$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')
gcloud services enable iamcredentials.googleapis.com sts.googleapis.com firebasehosting.googleapis.com firebaserules.googleapis.com --project "$PROJECT"
gcloud iam workload-identity-pools create github --project "$PROJECT" --location global --display-name "GitHub Actions"
gcloud iam workload-identity-pools providers create-oidc driveperf-repo --project "$PROJECT" --location global \
  --workload-identity-pool github --display-name "theKeval/DrivePerf" \
  --issuer-uri "https://token.actions.githubusercontent.com" \
  --attribute-mapping "google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.environment=assertion.environment" \
  --attribute-condition "assertion.repository=='theKeval/DrivePerf'"
gcloud iam service-accounts create github-web-deploy --project "$PROJECT" --display-name "GitHub Actions: web deploy"
SA="github-web-deploy@$PROJECT.iam.gserviceaccount.com"
for ROLE in roles/firebasehosting.admin roles/firebaserules.admin roles/datastore.viewer roles/serviceusage.serviceUsageConsumer; do
  gcloud projects add-iam-policy-binding "$PROJECT" --member "serviceAccount:$SA" --role "$ROLE" --condition None
done
# Only jobs running in the matching GitHub Environment (dev or prod) of theKeval/DrivePerf may use this account:
gcloud iam service-accounts add-iam-policy-binding "$SA" --project "$PROJECT" --role roles/iam.workloadIdentityUser \
  --member "principalSet://iam.googleapis.com/projects/$NUMBER/locations/global/workloadIdentityPools/github/attribute.environment/$ENV"
echo "GCP_WIF_PROVIDER=projects/$NUMBER/locations/global/workloadIdentityPools/github/providers/driveperf-repo"
echo "GCP_DEPLOY_SA=$SA"
```

Then go to GitHub → `theKeval/DrivePerf` → **Settings → Environments**:
- Create **`dev`**. Add the **variables** (not secrets; neither is sensitive) `GCP_WIF_PROVIDER` and `GCP_DEPLOY_SA` with the two values printed for `driveperf-dev`.
- Create **`prod`**. Add the same two variables with the values printed for `driveperf-prod`. Turn on **Required reviewers: theKeval** and **Deployment branches: `main` only**. This approval gate is the "deploy live only after Keval says so" rule.

No key file is created, so there is nothing to store or rotate. The roles are the minimum for `hosting:channel:deploy`, `deploy --only hosting` and `deploy --only firestore:rules`. If the first CI deploy fails with a permission error, the agent will name the missing role.

## 6. What the workflow does (`.github/workflows/web.yml`)

- **Every PR touching `web/**` or `backend/firebase/**`:** `npm ci`, Firestore rules tests in the emulator, `astro check`, a prod-config build with the dist checks (CSP hash, privacy baseline, page basics, routes, 500 KB budget), and Lighthouse CI on every page (Performance ≥ 95, Accessibility 100, Best Practices 100, SEO 100 on indexable pages, CLS 0).
- **Then, for PRs from this repo (not forks):** a dev-config build is deployed to the preview channel `pr-<number>` on `driveperf-dev`, which expires after 7 days. It deploys the Firestore rules to dev first. A bot comment links to the preview.
- **On merge to `main`:** after approval in the `prod` environment, Hosting goes live and the Firestore rules are deployed to `driveperf-prod`.

## 7. First deploy and checks (agent, after §2–§5)

```bash
cd backend/firebase
npx firebase deploy --only firestore:rules --project dev
(cd ../../web && PUBLIC_DP_ENV=dev npm run build)
npx firebase hosting:channel:deploy pr-first --project dev --expires 7d
```

Firebase won't deploy a `public` folder outside `backend/firebase`. So the Hosting `predeploy` hook in `firebase.json` copies `web/dist` to `backend/firebase/public`, which is git-ignored, before every deploy. `web/dist` stays the single, host-neutral build output.

The agent then checks the preview URL: every page and state, the links, the motion, a real waitlist write (it shows in the dev Firestore, and a repeat shows "You're already on the list"), and the headers:

```bash
curl -sI https://<preview-url>/ | grep -i -E 'content-security|strict-transport|x-content-type|referrer|permissions|cache-control'
curl -sI https://<preview-url>/_astro/<file>.css | grep -i cache-control      # immutable
curl -sI https://<preview-url>/.well-known/assetlinks.json | grep -i content-type   # application/json, 200, no redirect
curl -sI https://<preview-url>/.well-known/apple-app-site-association | grep -i content-type
curl -sI https://<preview-url>/does-not-exist | head -1                          # 404 with the custom page
```

**Live (`driveperf-prod`) is deployed only after Keval says so:** either by merging the PR, which needs Keval's approval in the `prod` environment, or by the agent running `npx firebase deploy --only hosting,firestore:rules --project prod` once Keval says "deploy live".

## 8. Spam escalation (only if spam appears; ADR-007)

The launch protection is a honeypot field, a 2 s minimum time-to-submit and the Firestore field and size rules. There is no reCAPTCHA, because it can set Google cookies. If spam entries appear in `waitlist`:

1. Google Cloud console (project `driveperf-prod`) → **Security → reCAPTCHA** → **Create key**: type **Website**, domains `driveperf.com` and `www.driveperf.com`, **no checkbox challenge** (score-based). Copy the **site key**. It is public.
2. Firebase console → *Build* → **App Check** → *Apps* → **DrivePerf Web** → **reCAPTCHA Enterprise** → paste the site key → Save.
3. The agent adds `initializeAppCheck(app, { provider: new ReCaptchaEnterpriseProvider(siteKey) })` in `web/src/lib/backend/firestore-waitlist.ts` and adds `https://www.google.com/recaptcha/`, `https://www.gstatic.com/recaptcha/` (script/frame) and `https://content-firebaseappcheck.googleapis.com` (connect) to the CSP. It also updates the privacy page's cookie note, because reCAPTCHA can set Google cookies.
4. Firebase console → App Check → **Firestore → Enforce**, after the new site has been live for a day and the metrics show verified requests.
