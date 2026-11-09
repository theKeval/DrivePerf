# Spike 1 drive log

The app records its own side automatically. This log is only for what it can't see: Mileon, and whether
a trip was real. **No addresses, place names or street names.** Use "home", "work" or nothing.

You can paste a row into the chat after each drive, in any rough format.

## Drives

| # | Date | Pulled away (time) | ≥ 1 km? | Condition | Spike recorded? | Mileon recorded? | Mileon route starts how far from where you parked (m, by eye) | Mileon start time shown | Notes |
|---|---|---|---|---|---|---|---|---|---|
| 1 |  |  |  |  |  |  |  |  |  |

**Condition** is one of: `normal`, `low-power`, `force-quit` (swiped the Spike away before driving),
`reboot` (restarted the phone before driving), `keep-alive-off`, `manual`.

## Walks, runs, rides, transit

Anything that isn't you driving a car.

| # | Date | Type (walk, run, cycle, bus, train, passenger) | Minutes | Spike created a trip? | Mileon created a trip? | Notes |
|---|---|---|---|---|---|---|
| 1 |  |  |  |  |  |  |

## Battery runs

Run one app at a time for the recording test, because battery drain on a shared phone can't be split
by eye. The per-app share comes from Settings > Battery.

| Test | Date | Start % | End % | Hours | Spike share (Settings > Battery) | Mileon share | Notes |
|---|---|---|---|---|---|---|---|
| Recording, screen off, ≥ 60 min, not charging |  |  |  |  |  |  |  |
| Armed idle, 24 h, no drives if possible |  |  |  |  |  |  |  |

## What the benchmark needs

| Test | Count | PRD target it checks |
|---|---|---|
| Normal drives, both apps running | at least 10 | Q1, Q2, Q4, Q10 |
| Walks and runs | at least 10 in total | Q5 (pass bar: 0 trips) |
| Bike rides, if you ride | up to 10 | Q6 (pass bar: at most 1) |
| Bus, train or streetcar, if convenient | a few | Q7 (baseline only) |
| Manual start from Control Center | 2 or 3 | Q3 |
| Force-quit the Spike, then drive | 2 | A3 §1.5 |
| Reboot, unlock, then drive | 2 | A3 §1.5 |
| Low Power Mode on, then drive | 2 | A3 §1.5 |
| "Legacy GPS keep-alive" off, then drive | 3 | A3 §1.1 |
| Recording battery run | 1 | Q11 |
| Armed idle battery run | 1 | Q12 |

Keep the phone off the charger when you can, and don't launch the Spike from Xcode, because the debugger
stops iOS from suspending it. Force-quit, reboot, Low Power Mode and keep-alive-off drives count separately
and aren't part of the 10 normal drives.
