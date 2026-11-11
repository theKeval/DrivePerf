# Manual test: a real drive (DRIVE-67 iOS shell)

About 30 minutes, two short drives. Nothing here records or shares a location: write down times, distances and
pass/fail only. Do not commit trip files, exports or screenshots that show a map or coordinates.

**Before you go**

1. Install the Debug build with the local override (README "Your iPhone") and launch DrivePerf **from the Home
   Screen**.
2. Complete onboarding: confirm 18+, allow location (Precise on), allow Motion & Fitness, turn **Record drives
   automatically** on and choose **Change to Always Allow**, allow notifications, choose your consent toggles.
   - Pass: the Ready screen lists Automatic trips On, Location Always, Motion & Fitness On, Notifications On.
3. Profile (top-right) › Diagnostics › **Run self-test**, phone face up near a window.
   - Pass: "All checks passed", including "Shared core ports" ("Wake triggers" may say "No parking spot yet" before
     the first trip).
4. Drives tab shows "Your drives will appear here." and the accessory reads **Auto-detect on · ready**.

**Drive 1: manual trip (at least 3 minutes, over 500 m)**

| # | Step | Pass when |
|---|---|---|
| 1 | Parked, tap **Start** in the accessory | The in-drive screen appears: "Recording", "Started by you · time", elapsed counting |
| 2 | Before moving, glance at Distance | Shows 0.0 km, then counts up once you drive |
| 3 | Lock the phone | A Live Activity shows "Recording", the timer and distance; the Dynamic Island shows the Loop and timer |
| 4 | Drive. Do not touch the phone | Nothing to do: the app only shows status |
| 5 | Park. Unlock, tap **Stop** | "End this drive?" sheet; **Keep recording** returns to the in-drive screen |
| 6 | Tap **Stop**, then **End drive** | "Drive saved" with km and minutes; the Live Activity changes to "Drive decoded." |
| 7 | **Back to Drives** | The trip is listed under Latest drives ("<weekday> <part of day>", km, min, "Started by you"); **All drives** groups it under "This week" |
| 8 | Optional: start another manual trip and stop it from the **Live Activity's Stop** on the Lock Screen | The trip is saved without unlocking |

**Drive 2: automatic trip (leave from where drive 1 ended)**

| # | Step | Pass when |
|---|---|---|
| 1 | Leave DrivePerf in the background (do not swipe it away: force-quitting pauses auto-detect) and lock the phone | — |
| 2 | Drive off and keep driving at least 3 minutes | A passive "Recording your drive" notification appears in Notification Centre within a minute or two of leaving the parking spot. No Live Activity (expected) |
| 3 | Open DrivePerf while still in the car (passenger or parked) | The in-drive screen shows "Started automatically" |
| 4 | Park and wait 5 minutes without driving | The trip ends on its own (stop timer 240 s); the notification goes away |
| 5 | Open DrivePerf | The trip is listed, starting at the wake, not when you first opened the app |
| 6 | Self-test again | "Wake triggers: Parked region armed" |

**Also check**

- Walk for 2 minutes with the phone (no car): no trip is listed afterwards (a walking wake is rejected).
- Restart the phone, do not unlock it, then unlock: the app does not record before the first unlock, and arms again after.

**Report**

Note for each row: pass/fail, the time it happened, and anything odd. File problems against DRIVE-67 with times and
counts only.
