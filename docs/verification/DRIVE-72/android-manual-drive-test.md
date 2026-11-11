# Android manual drive test (DRIVE-72)

A short script for a real drive with a real phone. It checks the things unit tests and the emulator cannot: GNSS and
sensor delivery in a moving car, the foreground service surviving in the background, and the notification.
Allow about 30 minutes: install, a 15 to 20 minute drive, and the checks afterwards.

**Privacy:** the trip file contains your real route. Analyse it on your own computer and never commit it. Report
distances, counts and percentages only (ADR-008).

## Before you start

- Phone with Android 10 or newer (note the model and Android version; A19 needs a spread of both). Play services must be present.
- Install the dev build: `./gradlew :apps:android:app:installDevDebug` (package `com.driveperf.app.dev`), phone connected with USB debugging.
- Charge level 100% and **unplugged for the drive** if you also want a battery reading (A19). Note the start percentage.
- Do a fresh install (`adb shell pm clear com.driveperf.app.dev`) so onboarding runs.
- A phone mount, if you have one. Note whether the phone is mounted, in a cup holder or on a seat.

## A. Onboarding and permissions (parked, 5 minutes)

| # | Step | Expect |
|---|---|---|
| A1 | Open the app | "Smooth is a skill." page |
| A2 | Continue through the intro | "How recording works" page, then the location page |
| A3 | Tap **Allow**, choose **Precise** and **While using the app** | Moves to "Record automatically" (all-the-time) |
| A4 | Tap **Allow** | Android opens the app's location permission screen. Choose **Allow all the time**, go back | App moves on to "Tell driving from walking" |
| A5 | Allow physical activity, then notifications | Each has its own page, in that order |
| A6 | Battery page | "Open battery settings" opens the system page; **Remind me later** also continues |
| A7 | "Help improve DrivePerf" | Both switches are **off**. Leave them off for this test |
| A8 | Ready page | Says "Location: on" and "Automatic recording: on" |
| A9 | Repeat from a fresh install, but **refuse** location | Page explains recording is unavailable and offers **Open settings**; continuing skips the all-the-time, activity and notification pages |

## B. Drive (15 to 20 minutes)

1. Open the app on the Drives tab and tap the round record button in the floating toolbar.
2. **Expect:** the black drive screen at once, a timer, "GPS signal good" within about 10 seconds, and a "Recording your drive" notification. On Android 16 the status bar shows a chip like "3 min".
3. Press Home and use the phone normally (maps, music) for the whole drive. Do not open DrivePerf again until the end.
4. Mid-drive, pull down the shade once. **Expect:** the notification shows minutes and kilometres, and a **Stop** action. Do not tap it yet.
5. Lock the screen for at least 5 minutes of the drive.
6. If you drive through a tunnel or underground car park, note roughly when.
7. When parked, unlock and open DrivePerf. **Expect:** the drive screen with a timer that kept counting.
8. Tap the red stop button. **Expect:** a confirmation ("End this drive?"). Tap **Keep recording**, then the stop button again, then **End drive**.
9. **Expect:** back on the Drives tab, one new row "Today, hh:mm", duration and distance, a "Recorded" chip. The notification is gone.

## C. Checks on the recorded data (at your computer)

The trip file is `files/trips/<id>.r0` in the app's private storage:

```bash
adb exec-out run-as com.driveperf.app.dev cat files/trips/<id>.r0 > trip.r0   # keep it out of the repo
```

Lines start with `L` (location), `M` (motion) or `A` (activity). Report these figures:

| # | Check | Expect |
|---|---|---|
| C1 | App distance against the car's odometer (note it at start and end) | Within about 3% |
| C2 | Location lines per second while moving | About 1 per second, no gap over 3 s except tunnels |
| C3 | Motion lines per second | About 50 (some devices deliver faster). Timestamps strictly increasing |
| C4 | First line timestamps against the moment you tapped start | Both streams start within about 1 s of the tap |
| C5 | **Conformance (shake right, tilt, spin)**: with the app recording on the table, push the phone to its right, tilt the top away, spin it counter-clockwise | `M` lines: user acceleration x positive on the push to the right; gravity z stays near +1 face up; the z rotation rate positive when spinning counter-clockwise. If any sign is wrong, fix `MotionAssembler`. This check is mandatory (pipeline-and-scoring.md section 2.1) |
| C6 | Battery used per hour of recording (if unplugged) | Record it. Target is set in A19, not here |
| C7 | Index line in `files/trips/index.tsv` | `delivery %` while moving close to 100 |

## D. Edge cases (parked)

| # | Step | Expect |
|---|---|---|
| D1 | Start, then revoke location in system Settings while recording | Android closes the app and recording ends; the notification goes. The partial file stays on the phone but has no row in the list yet (known interim gap: recovery of interrupted trips belongs to the shared core's recorder) |
| D2 | Start, swipe the app away from recents | Recording continues (foreground service). Open the app: the drive screen is back |
| D3 | Start, turn on airplane mode | Recording continues; GPS still works |
| D4 | Start, then tap **Stop** from the notification without opening the app | The drive ends and appears in the list |
| D5 | Start with notifications refused | Recording works; no notification is shown by the system but the service stays alive |
| D6 | Start with Battery saver on | Note whether fixes slow down (report the rate) |

## What to send back

Phone model, Android version, whether mounted, C1 to C7 numbers, D1 to D6 pass or fail, and any crash or odd behaviour. No coordinates, no addresses, no trip files.
