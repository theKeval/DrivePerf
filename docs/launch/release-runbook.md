# Release runbook: TestFlight and Play internal testing (DRIVE-218)

How a beta build reaches testers, what Keval sets up once, and how to cut a release. The lanes are in
`fastlane/Fastfile`; the workflows are `.github/workflows/release-ios.yml` and `release-android.yml`; the design is
[ADR-008](../architecture/adr/ADR-008-ci-cd-and-environments.md); the status is in [ci-cd.md](../architecture/ci-cd.md).

**Who does what:** Keval creates every key, keystore, password, token and service account, and adds them to GitHub
themselves. No credential ever passes through an agent, the repository or a chat. The agent writes lanes, workflows and
these steps.

## What the lanes do

| Workflow | Fastlane lane | Steps |
|---|---|---|
| `release-ios.yml` (macOS) | `ios beta` | temporary keychain → App Store Connect API key → `match appstore` **read-only** → signs the `DrivePerf` and `DrivePerfWidgets` targets with the match profiles, in the CI checkout only → `gym` archives `com.driveperf.app` (Release) → dSYMs to Sentry with `sentry-cli` (skipped with a warning until `SENTRY_AUTH_TOKEN` exists) → `pilot` uploads to TestFlight. The dSYM zip is kept as a run artifact for 90 days |
| `release-android.yml` (Ubuntu) | `android internal` | `bundleProdRelease` signed with the upload key → checks the signature → keeps the AAB and `mapping.txt` as a run artifact for 90 days → the R8 mapping to Sentry (`android sentry_mapping`, skipped with a warning until `SENTRY_AUTH_TOKEN` exists) → `supply` uploads the AAB and its R8 mapping to the **internal** testing track. Without `PLAY_SERVICE_ACCOUNT_JSON` it stops after the artifact (C4) |

- **Triggers:** a tag `v1.2.3`, or a manual run with a version input. Both jobs also need the repository variable
  `RELEASE_IOS_ENABLED` / `RELEASE_ANDROID_ENABLED` set to `true`; otherwise the run is skipped.
- **Approval:** both jobs run in the GitHub environment `prod`, which waits for Keval's approval on every run. Its
  deployment policy allows the branch `main` and the tag pattern `v*`; without the tag rule a tag push is refused before
  it reaches the approval. To add it once:

  ```bash
  gh api -X POST repos/theKeval/DrivePerf/environments/prod/deployment-branch-policies -f name='v*' -f type=tag
  ```

- **`gh` and a session token:** if your shell exports a `GITHUB_TOKEN` that cannot write secrets or variables, `gh`
  fails with HTTP 403. Prefix the `gh` commands in this runbook with `env -u GITHUB_TOKEN` to use your own login.
- **Versions:** the marketing version comes from the tag (`v0.2.0` → `0.2.0`) or the input. The build number (iOS
  `CFBundleVersion`) and version code (Android) are the workflow's run number, so each lane counts up on its own.
  Pull-request runs of the same workflow count too, so numbers skip (the first beta builds were 0.1.0 (3) on both
  platforms). Re-running a failed run reuses its number: fine if the upload had not happened yet; otherwise start a
  new run.
- **Build numbers on test phones:** a TestFlight build must have a higher build number than any Xcode-built
  `com.driveperf.app` already on a tester's iPhone. If the numbers match, TestFlight shows **Open** and leaves the
  local build in place (seen on Keval's iPhone with 0.1.0 (4), DRIVE-218). Delete the local build first, or ship a
  higher number.
- **Pull requests** that touch `fastlane/**` or a release workflow run one job, "Load the Fastlane lanes", which
  installs the bundle and lists the lanes. It has no secrets and no environment.

## What goes to beta

Only the prod app, `com.driveperf.app`, on both platforms: the App Store Connect record (DRIVE-42), the Play listing
and the `bundleProdRelease` row of ADR-008's pipeline table. The dev app (`com.driveperf.app.dev`) stays a device-only
build (Xcode or `adb`). Shipping it to TestFlight and Play too would need a second App Store Connect record, a second
Play app, its own `match` profiles and service-account grant, and a second set of testers, for no test the beta needs.
ADR-008's environment table lists the dev app on the internal tracks; this runbook narrows that for v1 beta. Revisit
if testers need a build that talks to the dev backend.

## Part A. iOS, once (Keval)

1. Do [apple-developer-setup.md](apple-developer-setup.md) **B1 to B4**: the App Store Connect API key
   (`ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8_BASE64`), the private `match` repository with its read-only deploy key
   (`MATCH_GIT_PRIVATE_KEY`, `MATCH_GIT_URL`) and passphrase (`MATCH_PASSWORD`), `match appstore` once on your Mac, and
   `APPLE_TEAM_ID`.
2. **TestFlight internal group.** App Store Connect → Apps → DrivePerf → **TestFlight** → Internal Testing → **+** →
   name it `DrivePerf team`, tick **Enable automatic distribution**, and add yourself (and other team members). The
   lane does not wait for Apple's processing, so automatic distribution is what hands each new build to the group.
3. Install **TestFlight** on your iPhone from the App Store and sign in with the same Apple ID.

## Part C. Android, once (Keval)

### C1. Upload keystore

Run in a private folder, not the repository. Choose a long password and store it in your password manager first:

```bash
keytool -genkeypair -v -storetype PKCS12 -keystore driveperf-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

A PKCS12 keystore uses the store password for the key too, so both password secrets below get the same value. Keep a
copy of `driveperf-upload.jks` in the password manager: Play App Signing holds the key users' installs are signed with,
and a lost upload key can be reset from the Play Console, but only after a wait.

```bash
base64 -i driveperf-upload.jks | gh secret set ANDROID_UPLOAD_KEYSTORE_BASE64 --env prod --repo theKeval/DrivePerf
```

```bash
gh secret set ANDROID_UPLOAD_STORE_PASSWORD --env prod --repo theKeval/DrivePerf
```

```bash
gh secret set ANDROID_UPLOAD_KEY_PASSWORD --env prod --repo theKeval/DrivePerf
```

```bash
gh secret set ANDROID_UPLOAD_KEY_ALIAS --env prod --repo theKeval/DrivePerf --body upload
```

The two password commands prompt for the value. Check that the saved password opens the keystore
(`keytool -list -keystore driveperf-upload.jks` shows one `upload` `PrivateKeyEntry`), back the `.jks` up (a password
manager that holds attachments, or an encrypted disk image), then delete the local copy.

### C2. The Play app and the internal testing track

1. Play Console → **Create app** (if it is not there yet): name DrivePerf, app, free, then accept the declarations.
   The package name is fixed by the first upload: it must be `com.driveperf.app`.
2. **Testing → Internal testing → Testers:** create an email list (for example `DrivePerf team`) with the Google
   accounts of the testers, and save. Copy the **opt-in link**: each tester opens it once to join. Its copy button
   stays disabled until the first release (C4); the link is already shown on hover.
3. Play App Signing is on by default for new apps. Leave it on: Google holds the app signing key, the upload key from C1
   only proves the upload is yours.

### C3. Play service account (for `supply`)

1. Google Cloud console → the project `driveperf-prod` (or a project just for Play) → **APIs & Services → Library** →
   enable **Google Play Android Developer API**.
2. **IAM & Admin → Service Accounts → Create service account**, for example `play-release`. Grant it **no** Google
   Cloud roles. Open it → **Keys → Add key → Create new key → JSON**, and download the file into a private folder. If
   the console says key creation is disabled, the organisation policy
   `iam.disableServiceAccountKeyCreation` is on: an organisation admin can turn it off for this project only.
3. Play Console → **Users and permissions → Invite new users** → the service account's email → **App permissions** →
   DrivePerf → tick **Release apps to testing tracks** and **View app information (read only)** → invite. No
   account-level permissions. The grant can take a while to take effect.
4. **After C4**, add the key, then delete the file (keep a copy in the password manager). Steps 1 to 3 can happen
   before C4; set the secret only once the first release is out, or the C4 run tries the API upload and fails:

   ```bash
   gh secret set PLAY_SERVICE_ACCOUNT_JSON --env prod --repo theKeval/DrivePerf < play-release.json
   ```

This is a long-lived Google key, which ADR-008 otherwise avoids: `supply` signs in with a service-account key and does
not accept the short-lived tokens of Workload Identity Federation. It is limited to testing tracks of this one app, has
no Google Cloud role, sits only in the `prod` environment behind Keval's approval, and is rotated once a year (create a
new key, update the secret, delete the old key).

### C4. The first upload, by hand

Google's API accepts uploads only for an app that already has a bundle. So the first one goes through the Play Console:

1. Leave `PLAY_SERVICE_ACCOUNT_JSON` unset for now (or do C3 later), set `RELEASE_ANDROID_ENABLED=true` (Part D), and
   run the workflow (Part E). It builds and signs the bundle, warns "Play upload skipped" and keeps the AAB as the
   artifact `android-prod-release-<version>-<run>`.
2. Download the artifact (or use the run page → **Artifacts**):

   ```bash
   gh run download <run id> --repo theKeval/DrivePerf --name android-prod-release-<version>-<run> --dir ~/Downloads/driveperf-first-aab
   ```

   It holds `bundle/prodRelease/app-prod-release.aab` and `mapping/prodRelease/mapping.txt`. In Play Console →
   **Testing → Internal testing → Create new release**, keep **Use Google-generated key** if asked about app signing,
   and upload `app-prod-release.aab`. Play registers the upload key from it. Add release notes, **Next**, then **Save
   and publish** / **Start rollout to Internal testing**. A warning about the missing deobfuscation file is fine.
3. From then on the lane uploads. If a lane upload fails with "Only releases with status draft may be created on
   draft app", set the repository variable `PLAY_RELEASE_STATUS=draft` (each upload then waits for you to roll it out
   in the Play Console) and remove it once the app's first release is out.

Production access later: a personal Play developer account created after November 2023 needs a closed test with at
least 12 testers for 14 days before it can apply for production. Internal testing has no such rule.

## Part D. Switches (Keval)

```bash
gh variable set RELEASE_IOS_ENABLED --repo theKeval/DrivePerf --body true
```

```bash
gh variable set RELEASE_ANDROID_ENABLED --repo theKeval/DrivePerf --body true
```

## Part F. Crash reporting on Sentry (Keval, DRIVE-196)

Until these steps are done the apps carry no DSN, so crash reporting does nothing even for users who allowed it
(Profile shows it as not configured), and the lanes skip the symbol uploads with a warning. Nothing else depends on it.

### F1. The Sentry organisation, EU region (ADR-007)

1. Sign up at sentry.io/signup. In the form, **Organization** becomes the organisation slug (`DrivePerf` →
   `driveperf`), and **Data Storage Location** must be **European Union (EU)**, which is Frankfurt: it can't be changed
   later. Sentry emails a verification link that opens `https://<slug>.sentry.io/onboarding/welcome/`. Check the region
   under **Settings → General Settings → Data Storage Region**. A new organisation starts on a 14-day trial and drops
   to the free Developer plan on its own; no card is needed, and the Developer plan is enough for the beta.
2. Organisation **Settings → Security & Privacy**, under **Data Scrubbing**, turn on all three (at organisation level
   they start off): **Require Data Scrubber**, **Require Using Default Scrubbers** and **Prevent Storing of IP
   Addresses**. Optionally also turn on **Enhanced Privacy** (keeps PII and source code out of notifications).
3. Same page, **Advanced Data Scrubbing → Add Rule**: Dataset *Errors, Transactions, Attachments*; Method **Replace**,
   Placeholder `[location removed]`; Data Type **Regex matches**, Regex
   `-?\d{1,3}\.\d{4,}\s*[,;/ ]\s*-?\d{1,3}\.\d{4,}` (the same pattern the apps use); Source `**` (every field).
   Sentry then notes that deep wildcards also apply to log and metric attributes: expected. **Save Rule**; the list
   then reads *[Replace] [the regex] with [[location removed]] from [\*\*]*.
4. Create two projects from **Projects → Create Project → Set up manually**: `driveperf-ios` (platform **iOS**) and
   `driveperf-android` (**Android**), on the default team. Skip the SDK setup screens and the "let your agent set up
   Sentry" prompt: the apps are already wired.
5. In each project: **Settings → Client Keys (DSN)** → **Configure** on the **Default** key → **Rate Limits**: Count
   `500`, Time Window **1 hour**, **Save**, so a crash loop can't use up the month's quota. Spike protection is on by
   default for each project; check it under organisation **Settings → Spike Protection**.

### F2. The DSNs (they turn crash reporting on in the builds)

A DSN is not secret (every app binary contains it), but it is never committed. Copy each project's DSN from
**Settings → Client Keys (DSN)** (an EU DSN contains `ingest.de.sentry.io`) straight into the command, not into a file
or chat:

```bash
gh variable set SENTRY_DSN_IOS --repo theKeval/DrivePerf --body "<iOS DSN>"
```

```bash
gh variable set SENTRY_DSN_ANDROID --repo theKeval/DrivePerf --body "<Android DSN>"
```

`gh variable list --repo theKeval/DrivePerf` shows the start of each value: the part before `@` is the project's key,
so you can check the two are not swapped.

The release lanes put them into the builds (`DP_SENTRY_DSN` on iOS, `DRIVEPERF_SENTRY_DSN` on Android). For a
local device build, put `DP_SENTRY_DSN = <iOS DSN>` in the gitignored `apps/ios/Config/Local.xcconfig`, or run Gradle with
`DRIVEPERF_SENTRY_DSN=<Android DSN>`. Leaving either empty turns crash reporting off for that build.

### F3. Symbol uploads (dSYMs and the R8 mapping)

Organisation **Settings → Organization Tokens** (`https://<slug>.sentry.io/settings/auth-tokens/`) → **Create New
Token** → name `DrivePerf CI` → **Create Token**. Organisation tokens always carry the `org:ci` scope that symbol uploads
need, and they also encode the EU region, so the lanes need no Sentry URL. Sentry shows the token once: copy it, run
the first command below (it asks for the value without echoing it), and close the dialog only after `gh` confirms.

```bash
gh secret set SENTRY_AUTH_TOKEN --env prod --repo theKeval/DrivePerf
```

```bash
gh variable set SENTRY_ORG --repo theKeval/DrivePerf --body <organisation slug>
```

```bash
gh variable set SENTRY_PROJECT_IOS --repo theKeval/DrivePerf --body driveperf-ios
```

```bash
gh variable set SENTRY_PROJECT_ANDROID --repo theKeval/DrivePerf --body driveperf-android
```

The organisation slug is in the Sentry URL. With the token set, `release-ios.yml` uploads the dSYMs (the app's dSYM
covers the shared Kotlin code, which is linked into the app binary) and `release-android.yml` uploads the R8 mapping
under a UUID it also writes into the bundle.

### F4. Check it before the beta (ADR-007 confirmation)

1. Cut a release (Part E). The run's annotations no longer warn that the Sentry upload was skipped, and the files show
   up in Sentry: `driveperf-ios` → **Settings → Debug Files** lists the `DrivePerf` and `DrivePerfWidgets` dSYMs, and `driveperf-android` →
   **Settings → ProGuard** lists the mapping under the run's UUID.
2. Install that build, allow crash reports in onboarding (S-ONB-13) or Profile (S-PRV-07).
3. Crash it and reopen the app, so the report is sent on the next launch. The release builds have no crash button or
   debug hook, so this needs a build that can crash on purpose (a follow-up to DRIVE-196; until then this step waits
   for the device checks in DRIVE-211). In Sentry: the iOS crash, including one from shared Kotlin code, shows
   symbolicated frames; the Android crash shows de-obfuscated frames. Of the custom tags, only `in_trip`,
   `capture_mode`, `trip_state` and `app_build` appear. Sentry's own tags (device, OS, release, level and so on)
   are expected; no coordinates, user or IP address.
4. Turn crash reports off in Profile: nothing more arrives.

## Part G. Google Maps key for Android Replay (Keval, DRIVE-216)

Android Replay (DRIVE-200, [ADR-005](../architecture/adr/ADR-005-maps.md)) draws Google map tiles under the route when
the build has `MAPS_API_KEY`. Without the key, offline, or when tiles fail, it draws the route on the plain canvas.
Gradle reads `MAPS_API_KEY` and `MAPS_MAP_ID` from `local.properties` or the environment, puts the key in the manifest
placeholder and sets `BuildConfig.HAS_MAPS_KEY` (`apps/android/app/build.gradle.kts`). iOS uses MapKit and needs no
key. Set up on 20 Nov 2026.

### G1. Project, billing and API

- **Project:** `driveperf-prod` (DrivePerf). One key serves the dev and prod builds, because Gradle reads a single
  `MAPS_API_KEY` for both flavours.
- **Billing:** Maps SDK for Android needs a billing account on the project even though its map loads are free.
  Linking it also put the prod Firebase project on **Blaze**: Firebase's free quotas still apply, and usage above them
  is billed. A budget, `driveperf-prod monthly` (US$10, email alerts at 10 %, 50 % and 100 %), warns about any spend.
  A budget alert does not cap spend.
- **API:** only **Maps SDK for Android** (`maps-android-backend.googleapis.com`) is enabled for maps.
- **Cost:** Google's pricing lists the "Maps SDK" SKU (native Android and iOS map loads) as free and unlimited.
  Street View (not used) and map loads that use a **Map ID** are billed (G3).

### G2. The API key

Google Cloud console → *APIs & Services → Credentials* → **DrivePerf Android Maps (DRIVE-216)**:

- **Application restrictions: Android apps.** Both packages, `com.driveperf.app` and `com.driveperf.app.dev`, each with
  these SHA-1 certificate fingerprints:

  | Signing certificate | Signs | Where to read its SHA-1 |
  |---|---|---|
  | Keval's local debug keystore (`~/.android/debug.keystore`) | `devDebug` and `prodDebug` builds from this Mac | `./gradlew :apps:android:app:signingReport` |
  | Play **app signing key**, classical | what testers and users install from Play | Play Console → *Test and release → App integrity → App signing* |
  | Play **app signing key**, post-quantum (beta) | the same, if Play ever serves it | same page |
  | **Upload key** (C1) | the bundle CI uploads, if installed directly | same page |

  A SHA-1 certificate fingerprint is not secret: it identifies a public certificate.
- **API restrictions:** Maps SDK for Android only.

The key was created with `gcloud services api-keys create` (`--api-target=service=maps-android-backend.googleapis.com`
and one `--allowed-application=sha1_fingerprint=…,package_name=…` per pair). A new signing certificate (another Mac's
debug keystore, an upload-key reset) needs its SHA-1 added for both packages: in the console, open the key → **Android
apps → Add**. `gcloud services api-keys update --allowed-application` replaces the whole list, so pass every pair.

**Never commit or paste the key.** These commands move it without printing it (`CLOUDSDK_CORE_DISABLE_FILE_LOGGING=1`
keeps it out of gcloud's own logs):

```bash
export CLOUDSDK_CORE_DISABLE_FILE_LOGGING=1
MAPS_KEY_UID=$(gcloud services api-keys list --project driveperf-prod --filter='displayName:DRIVE-216' --format='value(uid)')

# Local builds: append to the gitignored local.properties at the repository root (each checkout or worktree has its own).
k=$(gcloud services api-keys get-key-string "$MAPS_KEY_UID" --project driveperf-prod --format='value(keyString)') \
  && printf 'MAPS_API_KEY=%s\n' "$k" >> local.properties; unset k

# Release builds (Play internal testing): the `prod` environment secret read by release-android.yml.
gcloud services api-keys get-key-string "$MAPS_KEY_UID" --project driveperf-prod --format='value(keyString)' \
  | tr -d '\n' | env -u GITHUB_TOKEN gh secret set MAPS_API_KEY --env prod --repo theKeval/DrivePerf
```

On 20 Nov 2026 Keval asked the agent to run the `local.properties` step on this Mac; the key was piped, never shown.
Keval sets the GitHub secret. Check it by name only: `env -u GITHUB_TOKEN gh secret list --env prod --repo theKeval/DrivePerf`.

**Rotating:** create a new key with the same restrictions, replace it in `local.properties` and the `prod` secret,
cut a release, then delete the old key.

### G3. Night Drive Map ID: not used

ADR-005 planned a cloud-styled Map ID for the Night Drive dark style with POIs off. Google's Map Management page says a
Map ID on the Android or iOS Maps SDK makes each map load billable under the **Dynamic Maps** SKU: 10,000 free loads a
month, then US$7 per 1,000. Without a Map ID the loads stay free and unlimited. Keval chose **no Map ID** (20 Nov 2026,
[decisions.md](../product/decisions.md)). Replay uses the SDK's own dark or light scheme, so POIs stay visible and
`MAPS_MAP_ID` stays unset.

A map style named **Night Drive** is saved under *Google Maps Platform → Map Styles* (land `#0E131A`, water `#0A1A24`,
roads `#1F2733`, labels `#7A8392`, POIs and transit stations hidden). A style costs nothing on its own. To use it
later: *Map Management → Create map ID* (type **Android**), associate the Night Drive style, then set `MAPS_MAP_ID` in
`local.properties` and as a `prod` variable (it is not secret), and pass it to the release build next to `MAPS_API_KEY`.

### G4. Check it

1. `./gradlew :apps:android:app:installDevDebug` with the phone connected.
2. Open a drive → **Replay**. Map tiles appear under the route.
3. The plain canvas instead of tiles means either the build has no key (`BuildConfig.HAS_MAPS_KEY` false: check
   `local.properties`) or Google refused it. In the second case, `adb logcat | grep -i "Authorization failure"` names
   the package and SHA-1 it saw: add that pair to the key (G2). Changes to a key can take a few minutes to apply.

## Halting a rollout on crash-free sessions (F-ANA-03 AC3)

The rule: halt a staged rollout (Play) or pause a phased release (App Store) if a release's crash-free sessions drop
below **99.5 % on at least 1,000 sessions**. Sentry sees only users who allowed crash reports, so check the consent-free
numbers too before deciding:

- **Sentry:** Releases → the release → **Crash Free Sessions**, and the session count. An alert rule (Alerts → Create
  Alert → *Crash Free Session Rate* below 99.5 % for the release) emails Keval.
- **Play Console:** Android vitals → Crashes and ANRs for the release (users who share usage data with developers).
- **Xcode Organizer:** Crashes for the build (users who share analytics with developers).

To halt: Play Console → **Production → Releases → Manage rollout → Halt rollout**; App Store Connect → the version →
**Phased Release → Pause**. Both can resume later. Internal testing and TestFlight have no staged rollout, so this
applies from the first production release (wave 2 onwards).

## Part E. Cut a release

1. On an up-to-date `main`, tag and push (the tag starts both workflows):

   ```bash
   git tag v0.2.0
   ```

   ```bash
   git push origin v0.2.0
   ```

   Or run one workflow by hand, from `main`, with a version:

   ```bash
   gh workflow run release-ios.yml --repo theKeval/DrivePerf --ref main -f version=0.2.0
   ```

2. GitHub → **Actions** → the run → **Review deployments** → `prod` → **Approve and deploy**.
3. iOS: the run ends at the upload (about 30 to 45 minutes; the first run took 10). Apple processes the build for 10 to
   30 minutes more, then it appears in TestFlight for the internal group. Its status then reads **Ready to Submit**:
   that refers to Beta App Review for external groups only, and internal testers can already install it. Android: the build is on the internal track as soon as the run
   ends; testers who joined through the opt-in link update from the Play Store.
4. **Done when** the build installs from TestFlight on Keval's iPhone and from the Play Store (internal testing) on
   Keval's Android phone.

## When a run fails

| Message | Cause and fix |
|---|---|
| `<NAME> is not set. See docs/launch/release-runbook.md.` | The secret or variable is missing from `prod` or the repository: Parts A, C and D |
| match: `Could not decrypt the repo` | `MATCH_PASSWORD` differs from the passphrase used in B3 |
| match: `No matching provisioning profiles found` / `readonly` | B3 was not run, or a capability changed on the App IDs. Re-run `fastlane match appstore` on your Mac (B3); CI never creates profiles |
| `Permission denied (publickey)` | The deploy key is not on the certificates repository, or `MATCH_GIT_PRIVATE_KEY` is not its private half |
| pilot: `The bundle version must be higher` | This run number was already uploaded (a re-run after a successful upload): start a new run |
| supply: `Package not found` | C4 has not happened yet, or the service account has no access to DrivePerf (C3 step 3) |
| supply: `APK specifies a version code that has already been used` | Same as the pilot case: start a new run |
| jarsigner: `is not signed` | The upload-key secrets are wrong (C1) |

## Secrets and variables

| Name | Kind | Where | Used by | From |
|---|---|---|---|---|
| `RELEASE_IOS_ENABLED` | variable | repository | `release-ios.yml` switch | D |
| `RELEASE_ANDROID_ENABLED` | variable | repository | `release-android.yml` switch | D |
| `APPLE_TEAM_ID` | variable | repository | `ios beta` | apple-developer-setup B4 |
| `MATCH_GIT_URL` | variable | repository | `ios beta` | apple-developer-setup B2 |
| `MATCH_GIT_PRIVATE_KEY` | secret | env `prod` | ssh-agent for `match` | apple-developer-setup B2 |
| `MATCH_PASSWORD` | secret | env `prod` | `match` | apple-developer-setup B2 |
| `ASC_KEY_ID` | secret | env `prod` | `match`, `pilot` | apple-developer-setup B1 |
| `ASC_ISSUER_ID` | secret | env `prod` | `match`, `pilot` | apple-developer-setup B1 |
| `ASC_KEY_P8_BASE64` | secret | env `prod` | `match`, `pilot` | apple-developer-setup B1 |
| `ANDROID_UPLOAD_KEYSTORE_BASE64` | secret | env `prod` | Gradle signing | C1 |
| `ANDROID_UPLOAD_STORE_PASSWORD` | secret | env `prod` | Gradle signing | C1 |
| `ANDROID_UPLOAD_KEY_ALIAS` | secret | env `prod` | Gradle signing | C1 |
| `ANDROID_UPLOAD_KEY_PASSWORD` | secret | env `prod` | Gradle signing | C1 |
| `PLAY_SERVICE_ACCOUNT_JSON` | secret | env `prod` | `supply` | C3 |
| `PLAY_RELEASE_STATUS` | variable (optional) | repository | `supply`, `draft` only for C4's case | C4 |
| `SENTRY_DSN_IOS` | variable (optional) | repository | crash reporting in the iOS build | F2 |
| `SENTRY_DSN_ANDROID` | variable (optional) | repository | crash reporting in the Android build | F2 |
| `SENTRY_AUTH_TOKEN` | secret (optional) | env `prod` | dSYM and R8 mapping upload | F3 |
| `SENTRY_ORG` | variable (optional) | repository | dSYM and R8 mapping upload | F3 |
| `SENTRY_PROJECT_IOS` | variable (optional) | repository | dSYM upload | F3 |
| `SENTRY_PROJECT_ANDROID` | variable (optional) | repository | R8 mapping upload | F3 |
| `MAPS_API_KEY` | secret (optional) | env `prod` | Google map tiles in Android Replay; without it Replay uses the plain canvas | G2 |
