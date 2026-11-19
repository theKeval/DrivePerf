# Apple Developer setup: signing, App Store Connect and TestFlight secrets (DRIVE-42)

**Who does what:** Keval signs in, accepts every agreement and creates every key, certificate and password. The agent
registers identifiers in the portal with Keval's OK and writes config. **No key, `.p8`, `.p12`, certificate password or
token ever passes through an agent, the repository or a chat.** Related: [ADR-008](../architecture/adr/ADR-008-ci-cd-and-environments.md)
(Fastlane `match` + `gym` + `pilot`), [ci-cd.md](../architecture/ci-cd.md), [apps/ios/README.md](../../apps/ios/README.md).

## State on 19 Nov 2026

| Item | State |
|---|---|
| Membership | Apple Developer Program, **Individual** (Keval Langalia). Team ID **`N6H3394FM2`**. Renews **12 Nov 2027** (C$119/yr) |
| Apple Developer Program License Agreement | Accepted 12 Nov 2026 |
| Free Apps Agreement (App Store Connect) | **Active**, 12 Nov 2026 – 11 Nov 2027 |
| Paid Apps Agreement | **Not signed** (status "New"). Needed for subscriptions and In-App Purchase, not for TestFlight |
| EU Digital Services Act trader status | **Not declared**. Needed before the app is offered in the EU |
| App Group | `group.com.driveperf.app` |
| App IDs | `com.driveperf.app`, `com.driveperf.app.widgets`, `com.driveperf.app.dev`, `com.driveperf.app.dev.widgets`, each with App Groups linked to the group |
| Devices | Keval's iPhone 17 |
| Certificates | Apple Development (Keval, valid to Nov 2027). No Apple Distribution certificate yet: `match` creates it (B3) |
| Development profiles | Xcode automatic signing, `com.driveperf.app.dev` and `.dev.widgets`, valid to Nov 2027. The signed dev build is installed on Keval's iPhone |
| App Store Connect app record | **DrivePerf**, iOS, bundle ID `com.driveperf.app`, SKU `driveperf-ios`, primary language English (U.S.), Apple ID `6836657071` (created 19 Nov 2026) |
| Old Personal Team App IDs | `com.thekeval.driveperf.dev(.widgets)`, `com.thekeval.driveperf.hello`, `com.thekeval.spike.capture(.widgets)` moved into this team. Harmless; they can stay as the Personal Team fallback IDs or be removed |

Background location and Live Activities are not portal capabilities (`UIBackgroundModes` and `NSSupportsLiveActivities`
in Info.plist; the Live Activity is updated locally, without push). Sign in with Apple and Push Notifications are not
enabled: add them to the App IDs when those features are built (`DP_FEATURE_*` in `apps/ios/Config/DrivePerf.xcconfig`).
Changing a capability invalidates the existing profiles; Xcode and `match` regenerate them.

## Part A. App Store Connect (Keval, in the browser)

### A1. Paid Apps Agreement (before Pro subscriptions, RevenueCat)

App Store Connect → **Business** → Paid Apps Agreement → **View and Agree to Terms**, then add the bank account and the
tax forms (Canada and US forms as asked). Only Keval, the Account Holder, can do this. Not needed for TestFlight.

### A2. EU trader status (before EU distribution)

App Store Connect → **Business** → **Complete Compliance Requirements**. As an individual selling subscriptions, Apple
expects "trader"; the address, phone and email entered are then shown on the EU App Store page. Decide which contact
details to publish (for example support@driveperf.com) before submitting.

### A3. The app record (done)

Created on 19 Nov 2026 with Keval's choices: name **DrivePerf**, SKU `driveperf-ios` (permanent), primary language
English (U.S.), bundle ID `com.driveperf.app`, Full Access. The name can change until the first release.

ADR-008 also puts `com.driveperf.app.dev` on an internal TestFlight group. That needs a second app record (for example
"DrivePerf Dev") if dev builds should go to TestFlight too; decide when the release lane is built (DRIVE-67).

## Part B. Secrets for `release-ios.yml` (Keval, in a terminal and on GitHub)

`release-ios.yml` is inert today (placeholder step, gated on the repository variable `RELEASE_IOS_ENABLED`). The lane
ADR-008 describes, and DRIVE-67 builds, needs the items below in the GitHub environment **`prod`** (it exists and
requires Keval's approval). Create them now or when DRIVE-67 lands; the secret names below are what the lane will read.

### B1. App Store Connect API key (uploads to TestFlight, read-only `match`)

1. App Store Connect → **Users and Access** → **Integrations** → **App Store Connect API** → **Team Keys**. The first
   time, the Account Holder clicks **Request Access** and accepts.
2. **Generate API Key** → name `DrivePerf CI`, access **App Manager** (enough to upload builds and manage TestFlight).
3. Note the **Issuer ID** (top of the page) and the **Key ID**. **Download** the `AuthKey_<KEYID>.p8` once (Apple never
   shows it again) into a private folder, not the repository.
4. Add the secrets without printing the key (run in that folder, `gh` signed in as Keval):

   ```bash
   gh secret set ASC_KEY_ID --env prod --repo theKeval/DrivePerf
   ```

   ```bash
   gh secret set ASC_ISSUER_ID --env prod --repo theKeval/DrivePerf
   ```

   ```bash
   base64 -i AuthKey_<KEYID>.p8 | gh secret set ASC_KEY_P8_BASE64 --env prod --repo theKeval/DrivePerf
   ```

   The first two prompt for the value. Then move the `.p8` into the password manager and delete the file.

### B2. The `match` certificates repository

1. Create a **private** GitHub repository, for example `theKeval/DrivePerf-certificates`, empty. Never make it public:
   it holds the encrypted distribution certificate and profiles.
2. Create a read-only deploy key for CI: `ssh-keygen -t ed25519 -f driveperf-match -N "" -C "driveperf match ci"`. Add
   `driveperf-match.pub` to that repository → Settings → **Deploy keys** (read access only). Then:

   ```bash
   gh secret set MATCH_GIT_PRIVATE_KEY --env prod --repo theKeval/DrivePerf < driveperf-match
   ```

   ```bash
   gh variable set MATCH_GIT_URL --repo theKeval/DrivePerf --body "git@github.com:theKeval/DrivePerf-certificates.git"
   ```

   Then delete the two key files.
3. Choose a long passphrase for `match` encryption, store it in the password manager, and add it:

   ```bash
   gh secret set MATCH_PASSWORD --env prod --repo theKeval/DrivePerf
   ```

### B3. Create the distribution certificate and App Store profiles (once, on Keval's Mac)

Needs Fastlane (`brew install fastlane`) and the B2 repository. Run in any folder:

```bash
fastlane match appstore --git_url git@github.com:theKeval/DrivePerf-certificates.git --team_id N6H3394FM2 --app_identifier com.driveperf.app,com.driveperf.app.widgets
```

Fastlane asks for Keval's Apple ID sign-in (and two-factor code) and for the `MATCH_PASSWORD` passphrase. It creates the
**Apple Distribution** certificate and the App Store profiles, encrypts them and pushes them to the repository. CI only
ever reads them (`readonly`). An individual account can hold a small number of distribution certificates: never run
`match nuke` without a reason.

### B4. Repository variables (not secrets)

```bash
gh variable set APPLE_TEAM_ID --repo theKeval/DrivePerf --body N6H3394FM2
```

`RELEASE_IOS_ENABLED=true` comes last, after DRIVE-67 replaces the placeholder step. `SENTRY_AUTH_TOKEN` (dSYM upload,
ADR-007) joins `prod` when the Sentry project exists.

| Name | Kind | Where | From |
|---|---|---|---|
| `ASC_KEY_ID` | secret | env `prod` | B1 |
| `ASC_ISSUER_ID` | secret | env `prod` | B1 |
| `ASC_KEY_P8_BASE64` | secret | env `prod` | B1 |
| `MATCH_GIT_PRIVATE_KEY` | secret | env `prod` | B2 |
| `MATCH_PASSWORD` | secret | env `prod` | B2 |
| `MATCH_GIT_URL` | variable | repository | B2 |
| `APPLE_TEAM_ID` | variable | repository | B4 |
| `SENTRY_AUTH_TOKEN` | secret | env `prod` | later (ADR-007) |
| `RELEASE_IOS_ENABLED` | variable | repository | after DRIVE-67 |
