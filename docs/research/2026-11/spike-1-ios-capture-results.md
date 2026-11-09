# Spike 1: iOS capture results (DRIVE-36)

**Status: partial, in progress.** Setup, signing and crash findings exist so far. Two trips were driven on
2026-11-09, but a bug in the spike lost both (below), so no usable trip data exists yet.

**Scope (changed by Keval, 9 Nov 2026):** two or three trips, to confirm location and motion data reach the
app completely and accurately, and to list what the product capture code needs. The side-by-side benchmark and
the walk, run, force-quit, reboot and Low Power Mode runs are no longer part of this spike. The PRD §2.4 capture
targets stay provisional until the A19 field test.

This document reports distances, latencies, counts and battery only. Under ADR-008 it holds no addresses
or coordinates.

## Test setup

| Item | Value |
|---|---|
| App | `spikes/ios-capture/`, SwiftUI, deployment target iOS 26 |
| Device | iPhone 17 (iPhone18,3), **iOS 27.0** |
| Toolchain | Xcode 27.2 |
| Signing | Free Personal Team, bundle IDs `com.thekeval.spike.capture` and `.widgets` |

These results cover iOS 27.0 only. A19 covers iOS 26.x and other devices.

## Install log

Trips during an expired-profile period would not count as missed.

| Install | First launch (UTC) | Profile expires (UTC) |
|---|---|---|
| 1 | 2026-11-09 16:09 | 2026-11-16 16:06 |

The crash fix below was reinstalled at 16:17 UTC on 2026-11-09 under the same profile, so the expiry did not move.

## Trip results (pending)

| Trip | Started by | Trigger | Odometer (km) | App distance (km) | Difference | Parking spot to first point (m) | First fix after trigger (s) | Trip end after last movement (s) |
|---|---|---|---|---|---|---|---|---|
| 1 | automatic | pending | pending | pending | pending | pending | pending | pending |
| 2 | automatic | pending | pending | pending | pending | pending | pending | pending |
| 3 | Control Center | pending | pending | pending | pending | pending | pending | pending |

## Is the data complete and accurate? (pending)

| Check | Good result | Result |
|---|---|---|
| GPS delivery | ≥ 95% of 1 Hz fixes outside tunnels (Q13) | pending |
| GPS horizontal accuracy | median ≤ 20 m | pending |
| App distance against odometer | within about 3%, after allowing for the uncaptured start | pending |
| GPS speed against speed derived from positions | agree, apart from short noise | pending |
| IMU delivery | ≥ 98% of 50 Hz samples (Q13), measured without the debugger | pending |
| IMU sample interval | about 20 ms, timestamps always increasing | pending |
| Gravity vector magnitude | about 1 g | pending |
| Attitude quaternion | unit length | pending |
| Backfill | about 50 Hz accelerometer data across the start window | pending |
| Control Center start and Live Activity | recording starts, first fix ≤ 10 s (Q3) | pending |

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
to `parkedSpot` stopped the crash.

This path runs only after location is granted, so a launch check before permissions cannot catch it.

### The first two trips were lost to a spike bug, and iOS did wake the app

Both trips on 2026-11-09 showed "process died while confirming". The app woke correctly, once from a Visit
departure and once from a significant-change event with a 0-second-old location, each while suspended in the
background. It then crashed on its first motion sample, 0 seconds into the trip, with two identical crash
reports (`EXC_BREAKPOINT` in `_dispatch_assert_queue_fail`).

The closure passed to `startDeviceMotionUpdates` was written inside a `@MainActor` method and Core Motion's
handler type is not `@Sendable`, so Swift 6 made the closure main-actor isolated. Core Motion called it on the
writer queue and the runtime's isolation check aborted the app. Marking the closure `@Sendable` fixed it.

A self-test from the Mac then ran the full path on the phone without a crash, over 61 stationary seconds: 50.03 Hz
motion at 100% delivery with no gaps, 1.00 Hz GPS with no gaps, and 29,854 backfilled accelerometer samples over
600 s (49.8 Hz). This shows the pipeline runs. It says nothing about accuracy on a moving car.

### A locked phone blocked capture on a background launch

`UIApplication.isProtectedDataAvailable` is false every time the phone locks, not only before the first unlock
after a reboot. The spike used it to decide whether to start, so a background launch on a locked phone did nothing.
This happened at 19:49:47 UTC, and the app stayed idle until the next unlock about 11 minutes later. The spike now
reads a sentinel file with `completeUntilFirstUserAuthentication` protection instead. The fix is not yet proven
on a real locked-phone launch.

That same launch had no `.location` launch option, even though it happened on a locked phone mid-drive, shortly
after the crash. So the option does not identify every location-driven background launch.

### A Visit can deliver the parking spot before `CLMonitor` is ready

On a cold launch the Visit callback arrived before the asynchronous `CLMonitor` setup finished, so the spot was saved
but the parked region was never armed. The spike now arms the region as soon as the monitor exists.

### A launch can deliver a stale significant-change fix

On three launches the Spike received a significant-change event right away, with a location 516, 637 and
859 seconds old. Treated as a wake, each would have opened a false trip candidate.

`applicationState` read in `didFinishLaunching` was also `background` on launches that opened the app in
the foreground, so it can't separate background wakes from user launches.

The self-test's first fix also predated the wake by 0.29 s, showing that a stream's first fix can be cached. An older
cached fix would be taken while still parked and make the start distance look perfect, so the spike now drops
fixes older than 5 s before the wake.

## Changes the product capture code needs

Added to as the trips are analysed.

1. Use a fixed, unit-tested alphanumeric `CLMonitor` name.
2. Check the age of significant-change events before treating one as a wake. The spike ignores events older than 300 s.
3. Don't rely on `applicationState` or the `.location` launch option to identify a background location launch.
   Start capture on the location event itself.
4. Test launch with permissions granted. The capture path only starts once location is authorised.
5. Mark sensor callbacks that run off the main actor as `@Sendable`, or set them up from non-isolated code. KMP
   or Swift 6 wrappers around Core Motion hit the same trap.
6. Decide whether to start from file access, not `isProtectedDataAvailable`. Start capture on a locked phone once
   it has been unlocked since boot.
7. Arm the parked region once the monitor exists, whichever order launch events arrive in.
8. Drop location fixes older than the wake before using them for the start point.
9. Resume a candidate trip after a process death, instead of rejecting it, if iOS relaunches the app mid-confirmation.
10. Include a self-test that runs the full trip path on a device. Each of these bugs was invisible to unit tests.
