# Spike 1: iOS capture results (DRIVE-36)

**Status: partial, in progress.** Only setup and signing findings exist so far. No drives have been
recorded, so no PRD capture target is confirmed or changed yet.

This document reports distances, latencies, counts and battery only. Under ADR-008 it holds no addresses
or coordinates.

## Test setup

| Item | Value |
|---|---|
| App | `spikes/ios-capture/`, SwiftUI, deployment target iOS 26 |
| Device | iPhone 17 (iPhone18,3), **iOS 27.0** |
| Toolchain | Xcode 27.2 |
| Benchmark | Mileon 1.7 on the same phone |
| Signing | Free Personal Team, bundle IDs `com.thekeval.spike.capture` and `.widgets` |

**Coverage limit:** A3 §1.5 asks for force-quit behaviour on iOS 26.x and 27.x. Only iOS 27.0 is available
on this phone, so iOS 26.x results will need another device or the beta (A19).

## Install log

Drives during an expired-profile period are not counted as missed trips.

| Install | First launch (UTC) | Profile expires (UTC) |
|---|---|---|
| 1 | 2026-11-09 16:09 | 2026-11-16 16:06 |

The crash fix below was reinstalled at 16:17 UTC on 2026-11-09 under the same profile, so the expiry did not move.

## Findings so far

### Personal Team signing works for everything the spike needs

- The app and the WidgetKit extension (Control Center control and Live Activity) both signed, installed
  and launched under a free Personal Team.
- The spike needs no paid capability. Background location is an Info.plist mode and Live Activities are an
  Info.plist key, so the signed entitlements contain only the development defaults. The spike avoids App
  Groups because a `LiveActivityIntent` runs in the app process.
- It uses 2 of the Personal Team's 10 App IDs (the app and the extension).
- Signing proves only that these parts install. Whether the control starts a recording and whether the
  Live Activity appears are still untested.

### Setup steps a build cannot do alone

1. Signing in to Xcode did not make the Team ID available to `xcodebuild`. Someone had to select the team
   once in Signing & Capabilities, which also created the signing certificate.
2. The first launch failed with "profile has not been explicitly trusted by the user" until the owner
   trusted it in Settings > General > VPN & Device Management.

Both steps are one-time per Apple ID and device, apart from the weekly re-sign.

### `CLMonitor` names must be alphanumeric, or the app crashes

The first build crashed on every launch once location was granted: six crash reports on 2026-11-09, all
an `NSAssertionHandler` failure inside `CLMonitor.init`. The monitor was named `parked-spot`. The
`CLMonitorConfiguration` header says a name "should contain only alpha-numeric characters". Renaming it
to `parkedSpot` stopped the crash. A hyphen is enough to take the app down at launch, before any capture
starts, so the product code should use a fixed, tested alphanumeric name.

This path runs only after location is granted, so a launch check before permissions cannot catch it.

### A launch can deliver a stale significant-change fix

On three launches the Spike received a significant-change event right away, with a location 516, 637 and
859 seconds old. Treated as a wake, each would have opened a false trip candidate. The product code needs
an age check on the event, as the Spike has (300 s).

Also, `applicationState` read in `didFinishLaunching` was `background` on launches that opened the app in
the foreground, so it can't separate background wakes from user launches. The `.location` launch option
is the signal to use.

## Results (pending field runs)

| PRD target | Spike 1 pass bar | DrivePerf spike | Mileon | Verdict |
|---|---|---|---|---|
| Q1 capture success | ≥ 9 of 10 drives, not fewer than Mileon | pending | pending | pending |
| Q2 start latency | median ≤ 800 m and ≤ Mileon + 150 m | pending | pending | pending |
| Q3 manual start | ≤ 10 s | pending | n/a | pending |
| Q4 missed trips | ≤ 1 of 10 | pending | pending | pending |
| Q5 walking and running | 0 from ≥ 10 sessions | pending | pending | pending |
| Q6 cycling | ≤ 1 of 10 rides | pending | pending | pending |
| Q10 trip end | measured | pending | n/a | pending |
| Q11 battery, recording | ≥ 60 min, compared with Mileon | pending | pending | pending |
| Q12 battery, armed idle | 24 h armed run | pending | pending | pending |
| Q13 sensor delivery | ≥ 98% IMU, ≥ 95% GPS | pending | n/a | pending |
