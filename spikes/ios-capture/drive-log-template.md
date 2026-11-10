# Spike 1 trip log

Two or three short trips, to confirm location and motion data reach the app completely and accurately.
The app records its own numbers. This log only holds what it can't know.

**No addresses, place names or street names.** Paste a row into the chat after each trip, in any rough format.

| Trip | Date | Pulled away (time) | Parked (time) | Odometer distance (km) | Started by | Anything odd |
|---|---|---|---|---|---|---|
| 1 |  |  |  |  | automatic |  |
| 2 |  |  |  |  | automatic |  |
| 3 |  |  |  |  | Control Center |  |

## How to run a trip

1. While parked, reset the car's trip odometer.
2. Drive at least 5 km with the phone anywhere it won't slide around. Don't touch it while driving.
3. Park, then wait about 5 minutes before opening the app. A trip ends after 4 minutes without vehicle movement.
4. Note the odometer distance.

For trip 3, tap the **Spike drive** control in Control Center while still parked, and tap it again after you park.
Don't swipe the app away between trips, and don't launch it from Xcode.

## What to look for in the app (Trips tab, tap the trip)

| Field | Good result |
|---|---|
| Status | **Finished**, one row per trip |
| Distance | Close to the odometer. The app starts counting when it wakes, so automatic trips read a little short |
| Parking spot → first point | A few hundred metres at most on trips 2 and 3. It may be blank on trip 1 |
| IMU delivery | At least 98% |
| GPS delivery | At least 95%, unless you drove through a tunnel |
| IMU and GPS gaps | Few, and short |
| Backfill samples | Above 0 on automatic trips, if the window has a value |
| Notes | Tell me about anything here that mentions an error or `insufficientlyInUse` |
