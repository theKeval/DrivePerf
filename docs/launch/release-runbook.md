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

1. Sign up at sentry.io and create the organisation. **Choose the EU data region (Frankfurt) at sign-up**: it can't be
   changed later. The free Developer plan is enough for the beta.
2. Organisation **Settings → Security & Privacy**: turn on **Prevent storing of IP addresses**, keep **Data Scrubber**
   and **Use Default Scrubbers** on, and add an advanced data-scrubbing rule: *Replace* with `[location removed]`,
   matching the regex `-?\d{1,3}\.\d{4,}\s*[,;/ ]\s*-?\d{1,3}\.\d{4,}` on all values (the same pattern the apps use).
3. Create two projects: `driveperf-ios` (platform Apple iOS) and `driveperf-android` (Android). Skip the SDK setup
   screens; the apps are already wired.
4. In each project: **Settings → Client Keys (DSN)** → the default key → **Rate limiting**: for example 500 events per
   hour, so a crash loop can't use up the month's quota. **Settings → Spike Protection** stays on.

### F2. The DSNs (they turn crash reporting on in the builds)

A DSN is not secret (every app binary contains it), but it is never committed. Copy each project's DSN from
**Settings → Client Keys (DSN)**:

```bash
gh variable set SENTRY_DSN_IOS --repo theKeval/DrivePerf --body "<iOS DSN>"
```

```bash
gh variable set SENTRY_DSN_ANDROID --repo theKeval/DrivePerf --body "<Android DSN>"
```

The release lanes put them into the builds (`DP_SENTRY_DSN` on iOS, `DRIVEPERF_SENTRY_DSN` on Android). For a
local device build, put `DP_SENTRY_DSN = <iOS DSN>` in the gitignored `apps/ios/Config/Local.xcconfig`, or run Gradle with
`DRIVEPERF_SENTRY_DSN=<Android DSN>`. Leaving either empty turns crash reporting off for that build.

### F3. Symbol uploads (dSYMs and the R8 mapping)

Sentry → Settings → **Developer Settings → Organization Tokens** → create `DrivePerf CI` (it carries the `org:ci` scope
that symbol uploads need), then:

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

1. Install the first build that has the DSN, allow crash reports in onboarding (S-ONB-13) or Profile (S-PRV-07).
2. Trigger a test crash on a test build and reopen the app, so the report is sent on the next launch. In Sentry: the
   iOS crash, including one from shared Kotlin code, shows symbolicated frames and the tags `in_trip`, `capture_mode`,
   `trip_state` and `app_build`, and nothing else; the Android crash shows de-obfuscated frames.
3. Turn crash reports off in Profile: nothing more arrives.

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
