# Spike 1: iOS capture results (DRIVE-36)

**Status: complete for the reduced scope.** On 9–10 Nov 2026 the spike recorded 7 trips (34 km, 1.7 h) on an
iPhone: 6 started automatically and 1 from the Control Center control. It also rejected 1 false wake.
**Location and motion data arrived complete and internally consistent.** Ten changes for the product capture
code are listed at the end. No car-odometer readings were taken, so distance has no independent check yet.

**Scope (changed by Keval, 9 Nov 2026):** a few trips, to confirm that location and motion data reach the app
completely and accurately, and to list what the product capture code needs. The side-by-side benchmark and the
walk, run, force-quit, reboot and Low Power Mode runs are out of scope. The PRD §2.4 capture targets stay
provisional until the A19 field test. The numbers below are indicative only (one phone, seven trips).

This document reports distances, latencies, counts and rates only. Under ADR-008 it holds no addresses or
coordinates. The raw trip files were analysed on the Mac in a gitignored folder and are not committed.

## Test setup

| Item | Value |
|---|---|
| App | `spikes/ios-capture/`, SwiftUI, deployment target iOS 26 |
| Device | iPhone 17 (iPhone18,3), **iOS 27.0** |
| Toolchain | Xcode 27.2 |
| Signing | Free Personal Team, bundle IDs `com.thekeval.spike.capture` and `.widgets` |
| Streams | `liveUpdates(.automotiveNavigation)` plus legacy `startUpdatingLocation` (keep-alive on for every trip); `CMDeviceMotion` at 50 Hz; `CMSensorRecorder` backfill |

These results cover iOS 27.0 only. A19 covers iOS 26.x and other devices.

## Install log

| Install | First launch (UTC) | Profile expires (UTC) |
|---|---|---|
| 1 | 2026-11-09 16:09 | 2026-11-16 16:06 |

Three crash fixes (below) were reinstalled under the same profile on 2026-11-09, so the expiry did not move. All
seven trips ran on the final build, with no crash reports.

## Trips

Times are UTC. "Parking spot to first point" is the PRD Q2 measure: the straight line from the previous trip's
end to the first fix with accuracy ≤ 20 m.

| # | Start | Started by | Launch | Confirmed after (s) | App distance (km) | Parking spot to first point (m) | First fix after trigger (s) | Ended after last movement (s) |
|---|---|---|---|---|---|---|---|---|
| 1 | 11-09 20:15 | parked-region exit | suspended app | 0.04 | 1.42 | 180 | −0.11 | 240 |
| 2 | 11-09 20:35 | parked-region exit | suspended app | 0.03 | 5.51 | 179 | −0.12 | 241 |
| 3 | 11-09 22:22 | Control Center control | suspended app | manual | 11.59 | 5 | −0.24 | 55 (stopped by tap) |
| 4 | 11-09 23:18 | parked-region exit | **cold launch** | 35.2 | 1.30 | 163 | −0.22 | 241 |
| 5 | 11-09 23:30 | parked-region exit | suspended app | 0.03 | 0.67 | 172 | −0.10 | 240 |
| 6 | 11-10 13:48 | parked-region exit | **cold launch, phone locked** | 79.0 | 0.73 | 170 | −0.21 | 240 |
| 7 | 11-10 14:00 | significant change (region exit 3.9 s later) | suspended app | 0.06 | 12.72 | 145 | −0.13 | 241 |

One more wake, at 11-10 14:48 from a region exit, was **correctly rejected**: no movement in 180 s, so no trip was
created. It cost 3 minutes of GPS.

A negative "first fix after trigger" means the first fix is the one that caused the wake, timestamped up to 0.25 s
earlier. It is not an old cached fix.

## Is the data complete and accurate?

**Location: yes.**

| Check | Good result | Result |
|---|---|---|
| GPS delivery while moving | ≥ 95% (Q13) | **100%** on every trip. All 37 gaps over 3 s happened with the car stopped (speed < 7 km/h), mostly in the 4-minute wait after parking |
| GPS delivery, whole trip | — | 100% on trips 1–3, 99.8% on 6, 95.3% on 7, 86.6% on 4, 35.8% on 5 (a 77-second drive followed by 4 stationary minutes) |
| Timestamps | always increasing | 0 out-of-order fixes; median interval 1.00 s |
| Horizontal accuracy | median ≤ 20 m | median 2.8–8.0 m per trip; ≥ 97% of fixes ≤ 20 m on trips 1–3, 6 and 7, 94.5% on 5, 85.3% on 4 (worst values while parked) |
| GPS speed against speed derived from positions | agree | median difference −0.14 to +0.07 km/h on the five longer trips (−1.3 km/h on 77-second trip 5); 95% of pairs within 1.5–4.7 km/h |
| App distance against the raw GPS path | close | 0.3–1.4% shorter on trips over 5 km, because the app drops steps under 5 m |
| App distance against the car odometer | within about 3% | **not done:** no odometer readings were taken |

**Motion: yes.**

| Check | Good result | Result |
|---|---|---|
| IMU delivery | ≥ 98% of 50 Hz (Q13), no debugger | **100%** on all 7 trips: 305,573 samples, 50.03–50.04 Hz, 0 gaps |
| IMU sample interval | about 20 ms, always increasing | 20.0 ms from the 1st to the 99th percentile, worst 21 ms, 0 out of order |
| Gravity and attitude | unit vectors | gravity 1.0000 g, quaternion norm within 2 × 10⁻⁷ of 1. Core Motion normalises both, so this shows clean data, not sensor accuracy |
| Linear acceleration | plausible for driving | median 0.01–0.08 g, 99th percentile 0.27–0.62 g |
| IMU start | aligned with GPS | first IMU sample 0.10–0.24 s after the first fix |
| Backfill (`CMSensorRecorder`) | about 50 Hz across the start window | 50.04 Hz on all 6 automatic trips; raw acceleration averages 0.997–1.002 g (a real calibration check); it ends 1.2–3.9 s before live IMU starts, so the start is covered almost gap-free |

## What the trips show about capture

- **The parked-location region is the trigger that matters.** It fired first on 5 of the 6 automatic trips and
  3.9 s after the significant-change event on the sixth. Significant change followed 61–129 s later, and Visit
  departure 57–264 s later. With a 120 m region, the first good fix was 145–180 m from the parking spot (median
  171 m). That is well inside the PRD Q2 iOS median of ≤ 400 m, but n = 6.
- **The app records from the wake, so nothing is lost after the trigger.** GPS and IMU start within 0.25 s of the
  wake, including on a cold launch with the phone locked (trip 6).
- **Confirmation is fast.** It took under 0.1 s on 4 trips, where motion history already said automotive at speed,
  and 35 s and 79 s on the two cold launches.
- **Manual start works and is instant.** The Control Center control started trip 3 in the background through the
  `LiveActivityIntent`, its Live Activity appeared, and the first fix came 0.24 s before the tap was processed,
  5 m from the start point (Q3: ≤ 10 s).
- **Automatic trips cannot start a Live Activity.** All 6 attempts failed with "Target is not foreground", which
  confirms A3 §3 and the DTS statement.
- **Trip end works as designed.** Automatic trips ended 240–241 s after the last movement (PRD Q10: ≤ 5 min).
- **`CLBackgroundActivitySession` created during a background wake reports `insufficientlyInUse`** (3 of 6
  automatic trips). Both streams still delivered at 1 Hz, but the legacy keep-alive ran on every trip. Whether
  `liveUpdates` alone keeps a background-woken app running remains **untested**.
- **Motion start is unreliable when trips are close together.** On trips 2, 4 and 7 the automotive start from
  motion history hit the 15-minute query limit. The seconds-based start latency and backfill window on those
  trips are overstated, so they are not reported. The parking-spot distance above is not affected.
- **Battery was not measurable.** The phone gained charge on 4 trips (it was charging in the car), and
  `UIDevice.batteryLevel` moves in 5% steps. Q11 and Q12 need a dedicated not-charging run in A19.

## Bugs found and fixed during the spike

### Personal Team signing works for everything the spike needs

The app and the WidgetKit extension (Control Center control and Live Activity) signed, installed and ran under a free
Personal Team. No paid capability is needed: background location is an Info.plist mode and Live Activities are an
Info.plist key. The spike avoids App Groups because a `LiveActivityIntent` runs in the app process. It uses 2 of
the Personal Team's 10 App IDs.

Two setup steps need a person: selecting the team once in Xcode's Signing & Capabilities (signing in alone did not
give `xcodebuild` a Team ID), and trusting the developer profile on the phone.

### `CLMonitor` names must be alphanumeric

The first build crashed on every launch once location was granted: an `NSAssertionHandler` failure in
`CLMonitor.init`. The monitor was named `parked-spot`, and the `CLMonitorConfiguration` header says a name "should
contain only alpha-numeric characters". Renaming it to `parkedSpot` fixed it.

### A device-motion callback crashed every trip

The first two field trips were lost: iOS woke the app correctly, from a Visit departure and from a significant
change, and it then crashed on the first motion sample. The closure passed to `startDeviceMotionUpdates` was written
inside a `@MainActor` method. Core Motion's handler type is not `@Sendable`, so Swift 6 made the closure main-actor
isolated, and the runtime's isolation check aborted the app when Core Motion called it on the writer queue
(`EXC_BREAKPOINT` in `_dispatch_assert_queue_fail`). Marking the closure `@Sendable` fixed it.

### A locked phone blocked capture on a cold launch

`UIApplication.isProtectedDataAvailable` is false every time the phone locks, not only before the first unlock after
a reboot. The spike used it to decide whether to start, so a cold background launch on a locked phone did nothing
until the next unlock. The spike now reads a sentinel file protected with `completeUntilFirstUserAuthentication`.
Trip 6 confirms the fix: a cold launch with the phone locked, recorded completely.

That relaunch, and the two cold launches on trips 4 and 6, had no `.location` launch option, so the option does not
identify location-driven launches.

### Smaller issues

- A Visit could deliver the parking spot before the asynchronous `CLMonitor` setup finished, so the region was never
  armed. The spike now arms it as soon as the monitor exists.
- Every cold launch delivered a cached significant-change fix 504–52,106 s old. Treated as a wake, each would have
  opened a false trip candidate. The spike ignores events older than 300 s.
- `applicationState` in `didFinishLaunching` read `background` even on launches that opened the app in the foreground.

## Changes the product capture code needs

1. **Make the parked-location region the primary wake trigger** and re-arm it at every trip end and on every launch,
   whichever event arrives first. Keep significant change and Visits as fallbacks: on these trips they arrived 1–4
   minutes later.
2. **Start recording at the wake and keep the candidate's samples.** Confirmation can take up to 79 s, and the samples
   from that period are part of the trip.
3. **Bound the motion-start search** by the previous trip's end or the parked event, and persist activity history.
   Never treat the query window's edge as the motion start.
4. **Compute GPS delivery and gaps only while moving.** iOS drops to about one fix every 6 s when the car is stopped,
   even with automatic pausing off.
5. **Decide whether to start from file access, not `isProtectedDataAvailable`.** Capture must start on a locked phone
   once it has been unlocked since boot.
6. **Mark sensor callbacks that run off the main actor as `@Sendable`**, or create them in non-isolated code. Any
   Swift 6 or KMP wrapper around Core Motion hits the same trap.
7. **Use only alphanumeric `CLMonitor` names**, fixed and unit-tested.
8. **Drop stale wake events and cached fixes.** Ignore significant-change events older than a few minutes, and fixes
   timestamped well before the wake.
9. **Don't start a Live Activity for automatic trips locally**, because it fails in the background. Use the
   notification or the Control Center control, as A3 §3 option (a) or (c).
10. **Include an on-device self-test of the full trip path** (wake, confirm, record, backfill, end). Three of the
    spike's four bugs were invisible to unit tests and only appeared on the phone.

## Not covered (A19)

- Distance against the car's odometer.
- Battery while recording and while armed, measured off the charger.
- Whether `liveUpdates` alone, without the legacy keep-alive, keeps a background-woken app recording.
- Force-quit, reboot, Low Power Mode, walking, running, cycling and transit.
- iOS 26.x, other iPhones, and a larger sample for the PRD Q1, Q2 and Q4 rates.
