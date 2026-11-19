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
| `release-android.yml` (Ubuntu) | `android internal` | `bundleProdRelease` signed with the upload key → checks the signature → keeps the AAB and `mapping.txt` as a run artifact for 90 days → `supply` uploads the AAB and its R8 mapping to the **internal** testing track. Without `PLAY_SERVICE_ACCOUNT_JSON` it stops after the artifact (C4) |

- **Triggers:** a tag `v1.2.3`, or a manual run with a version input. Both jobs also need the repository variable
  `RELEASE_IOS_ENABLED` / `RELEASE_ANDROID_ENABLED` set to `true`; otherwise the run is skipped.
- **Approval:** both jobs run in the GitHub environment `prod`, which waits for Keval's approval on every run.
- **Versions:** the marketing version comes from the tag (`v0.2.0` → `0.2.0`) or the input. The build number (iOS
  `CFBundleVersion`) and version code (Android) are the workflow's run number, so each lane counts up on its own.
  Re-running a failed run reuses its number: fine if the upload had not happened yet; otherwise start a new run.
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

The two password commands prompt for the value. Then delete the local `.jks`.

### C2. The Play app and the internal testing track

1. Play Console → **Create app** (if it is not there yet): name DrivePerf, app, free, then accept the declarations.
   The package name is fixed by the first upload: it must be `com.driveperf.app`.
2. **Testing → Internal testing → Testers:** create an email list (for example `DrivePerf team`) with the Google
   accounts of the testers, and save. Copy the **opt-in link**: each tester opens it once to join.
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
4. Add the key, then delete the file (keep a copy in the password manager):

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
2. Download the artifact, unzip it, and in Play Console → **Testing → Internal testing → Create new release** upload
   `app-prod-release.aab`. Play registers the upload key from it. Add release notes, **Save**, **Review release**,
   **Start rollout to Internal testing**.
3. From then on the lane uploads. If a lane upload fails with "Only releases with status draft may be created on
   draft app", set the repository variable `PLAY_RELEASE_STATUS=draft` (each upload then waits for you to roll it out
   in the Play Console) and remove it once the app's first release is out.

Production access later: a personal Play developer account created after November 2023 needs a closed test with at
least 12 testers for 14 days before it can apply for production. Internal testing has no such rule.

## Part D. Switches and Sentry (Keval)

```bash
gh variable set RELEASE_IOS_ENABLED --repo theKeval/DrivePerf --body true
```

```bash
gh variable set RELEASE_ANDROID_ENABLED --repo theKeval/DrivePerf --body true
```

**Sentry dSYM upload** (optional for the first builds; the dSYMs are kept as an artifact either way). Once the Sentry EU
organisation and the iOS project exist (ADR-007): Sentry → Settings → **Developer Settings → Organization Tokens** →
create `DrivePerf CI` (it carries the `org:ci` scope that symbol uploads need), then:

```bash
gh secret set SENTRY_AUTH_TOKEN --env prod --repo theKeval/DrivePerf
```

```bash
gh variable set SENTRY_ORG --repo theKeval/DrivePerf --body <organisation slug>
```

```bash
gh variable set SENTRY_PROJECT_IOS --repo theKeval/DrivePerf --body <iOS project slug>
```

The organisation and project slugs are in the Sentry URL. The DSN that turns crash reporting on in the apps is a
separate step (DRIVE-196).

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
3. iOS: the run ends at the upload (about 30 to 45 minutes). Apple processes the build for 10 to 30 minutes more, then
   it appears in TestFlight for the internal group. Android: the build is on the internal track as soon as the run
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
| `SENTRY_AUTH_TOKEN` | secret (optional) | env `prod` | dSYM upload | D |
| `SENTRY_ORG` | variable (optional) | repository | dSYM upload | D |
| `SENTRY_PROJECT_IOS` | variable (optional) | repository | dSYM upload | D |
