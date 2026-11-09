# DrivePerf pipeline and scoring specification

| | |
|---|---|
| **Jira** | [DRIVE-56](https://thekeval.atlassian.net/browse/DRIVE-56) (prompt A12). Sole input for DRIVE-62 (shared KMP core) |
| **Status** | Decisions D1–D8 accepted by Keval, 9 Nov 2026. The document itself is awaiting Keval's merge approval |
| **Algorithm IDs** | `pipe-1.0.0` (trip pipeline), `smooth-1.0.0` (scoring). Both stay provisional until A19 |
| **Spike 1 status** | The test app is built (DRIVE-36, commit `6faf180`) but no drive results exist yet (`spike-1-ios-capture-results.md` is absent as of 9 Nov 2026). §1.5 and §1.6 deliberately use the same rules the app is testing. Every capture number is provisional, §9 |
| **Binding inputs** | [decisions.md](../product/decisions.md), [ADRs](adr/README.md), [PRD v1](../product/PRD-v1.md), [data-model.md](data-model.md). Where they disagree with prompt A12, they win |

## Decisions

Keval accepted all eight on 9 Nov 2026, as recorded in [decisions.md](../product/decisions.md) (`Keval, DRIVE-56`). The spec below applies them.

| # | Topic | Decision | Alternative not taken |
|---|---|---|---|
| D1 | **What "baseline-relative" means** | **The 0–100 number is absolute and always shown as "±n vs your usual".** A score normalised to each driver would hover around the same value forever, so route records and "you're improving" could not exist. F-STY-03 and PRD §5 ("trip score − baseline") already assume this. | A fully relative score, which would need a redesign of records, trends and recaps. |
| D2 | **R1 rate** | **25 Hz.** ADR-004's R2 line stays as costed (≈ 0.6 MB per driving hour). Jerk is computed after a 1.5 Hz filter, so 50 Hz carries no extra signal. In 40 synthetic drives, 25 Hz and 50 Hz scores differed by at most 0.12 points and detected the same events. A19 confirms on real drives. Resolves OQ-A11-04. | 50 Hz, which roughly doubles the R2 line (≈ 1.2 MB/h) and changes nothing in the score. |
| D3 | **Limited trips** (F-SCO-02) | **Scored and shown ("based on x of y min"), but kept out of baselines and route records.** A partial trip should not move the driver's "usual". | Allowing them in, which makes baselines noisier for people with loose phones. |
| D4 | **"Was it needed?" scope and cap** (resolves OQ-22) | **All three event types, as F-SCO-05 is written. A run with more than 2 "needed" marks cannot set a route record.** The trip score still updates. | Braking-only, which is simpler but penalises a swerve around a hazard. |
| D5 | **Route-record pace band** (F-SCO-07) | **Moving time between 0.75× and 1.50× of the route's median**, so unusually fast runs are ineligible as well as unusually slow ones. Fast runs falling outside the band is what keeps records from rewarding speed. | A slow-only band, which leaves a route record reachable at excessive pace. |
| D6 | **Accelerometer-only scoring of the estimated start** (F-CAP-05 AC3) | **Never scored in v1.** `CMSensorRecorder` has no gyro or attitude, so the segment cannot be put into the vehicle frame. | Scoring it, which would need an orientation estimate that nothing can validate. |
| D7 | **Suspected bus, train or bike trips nobody has answered** | **Scored and shown, but held out of baselines and records until the user answers "No".** A wrong guess then costs nothing. | Counting them, which lets a bus ride move the driver's baseline. |
| D8 | **Barometer for elevation** (F-RPL-02) | **Not in v1.** GNSS altitude with a dead-band is enough for a neutral profile, and the barometer adds a channel for no scoring benefit. Revisit after A19. | Using it, which adds a sensor stream, a fusion step and battery cost. |

---

## 0. Scope and conventions

### 0.1 Where this lives

| ADR-002 location | Contents | Rule |
|---|---|---|
| `:shared:pipeline` | Everything in §1–§7: state machine reducer, DSP, detectors, scoring, tips, route matching | Pure Kotlin in `commonMain`. No I/O, no clocks except injected `Clock`, imports only `foundation` and domain entities |
| `:shared:domain` | Sample contracts and the `LocationSource`, `MotionSource`, `ActivitySource`, `PowerState` ports | Speaks domain types only |
| `:shared:data:local` | R0/R1 codec, SQLDelight writes | Calls the pipeline, does not contain DSP |
| `apps/ios` `DPPlatform`, `apps/android` `platform` | Trigger registration, sample delivery, unit and sign normalisation (§2.1) | **No business logic.** Platform code never decides whether a trip exists, how a sample is filtered or what a score is |

iOS delivers samples to Kotlin in ≈ 1 s batches (A3 §7.3 #1). The state machine runs on one confined dispatcher.

### 0.2 Processing flow

```
LIVE (reducer only, §1):    platform samples → state machine → R0 append (≤ 5 s fsync)
AT TRIP END (batch, §2–§7, while GNSS still keeps the app awake):
  R0 → gate GNSS → detect phone movement → align per mount epoch → resample 25 Hz → write R1
  R1 → filter → speed estimate → metrics → events → ledger → badge → Smoothness
     → route match → baseline and delta → Efficiency → tip → one SQLite transaction
```

**R1 is the seam.** Everything after R1 is computable from R1 alone, because R0 never leaves the phone. A restored phone, a "Was it needed?" tap and a detector-version re-run all work without R0. R0 is retained until the user deletes it (Gate 4), so a full re-run from R0 is possible wherever it still exists.

**Time budget.** Finalisation is O(n) in samples except route matching (§7). Target ≤ 3 s per driving hour on the iPhone reference device, measured by a DRIVE-62 benchmark, to meet Q18.

### 0.3 Rates

| Tier | Rate | Frame | Notes |
|---|---|---|---|
| **R0 capture log** | 50 Hz IMU nominal, every GNSS fix | Normalised device frame (§2.1) | Local only, **not pruned automatically** (Gate 4). IMU may drop to 25 or 10 Hz under F-CAP-16 |
| **R1 analysis file** | **25 Hz** vehicle frame, GNSS 1 Hz | Vehicle frame | Uploaded to **Cloudflare R2** for Pro backup. `motion_rate_hz = 25`. Planning size ≈ 0.6 MB per driving hour, as ADR-004 costs it |

### 0.4 Symbols and units

SI everywhere internally. R0 and R1 store accelerations in g because `data-model.md` §2.4 does. `G = 9.80665 m/s²`. `Δ = 0.04 s` is the R1 sample period. Trip time `t` is seconds from `Trip.startedAt`. Spans are half-open `[t0, t1)`.

Vehicle frame: **longitudinal positive = accelerating**, **lateral positive = turning right**, **yaw rate positive = turning right**, vertical positive = up.

### 0.5 Determinism

F-SCO-01 AC5 requires identical results for identical input.

1. **R0 → R1** uses `atan2`, `sin` and `cos`, whose last bits can differ between JVM and Kotlin/Native. R1 values are therefore quantised (`accel_g_per_unit = 0.001`, `gyro_dps_per_unit = 0.01`). Quantise with round-half-even (`Math.rint`). Golden tolerance: ±0.002 g, ±0.05 °/s.
2. **R1 → scores** may use only `+ − × ÷ √`, comparisons and table constants. These are exact under IEEE 754. No `sin`, `cos`, `pow`, `exp` or `log`. Two exceptions, both rounded so the last bits cannot propagate: haversine distance rounds each segment to 0.01 m, and the route projection uses a `cos(lat0)` rounded to 6 decimals and computed once. Golden tolerance: integers and enums exact, continuous values ±0.02, total distance ±1 m.
3. **Ordering.** Sort events by `(tPeak, type name)`. Process trips by `(startedAt, id)`. Percentiles are nearest-rank on sorted values. An even-sized median is the mean of the two middle values, except where stated.
4. **Rounding.** Scores use round-half-up: `floor(x + 0.5)`.
5. **IDs.** Event UUIDs are not deterministic, so goldens match events on `(type, tPeak ± 0.04 s)`. Feedback survives reprocessing by `(type, tPeak ± 1.0 s)` as `data-model.md` §1.9 says.

### 0.6 Versioning

All thresholds live in one immutable `PipelineConfig` per version. **No threshold can come from Remote Config** (F-ANA-05 AC2). Any change bumps `pipe-x.y.z` or `smooth-x.y.z`, and existing trips keep their stored version until re-scored (F-SCO-04 AC3). A change touching §2 alignment or filtering needs R0. A change touching §3–§7 re-runs from R1. Re-scoring history is opt-in (PRD §7.5).

### 0.7 Binding safety rules, as implementation constraints

| Rule | Enforced by |
|---|---|
| No live score, events or coaching on any surface (SAF-02) | The reducer emits only elapsed time, distance and capture state. Scoring code runs only in `FINISHING`. `:shared:presentation` has no path from a `RECORDING` trip to a score |
| Speed never raises a score (F-SCO-07 AC3) | Speed may reach scoring code only at the six allow-listed points below. A Konsist test forbids other uses, and fixture G-07 proves the property |
| Speed is neutral, and never on share cards, records or leaderboards (SAF-04, SAF-06) | `maxSpeedMps` and speed stats are never inputs to scoring, tips, records or recaps. Route records hold Smoothness only |
| No streaks (SAF-05) | Nothing here counts consecutive days or trips |
| Passenger trips are excluded (EC-PAS) | The scorer rejects `kind ≠ DRIVER`. Baselines, records and recaps filter on `kind` |

**Speed allow-list:** (1) the moving mask, (2) stop relief, (3) the context class, which can only tighten the score as speed rises, (4) speed wobble, which uses absolute m/s and never a ratio to mean speed, (5) stops per km, compared only within a context, (6) the record pace band, which only makes runs ineligible.

### 0.8 Additions to `data-model.md` for DRIVE-62

All additive, inside JSON columns or enums.

| Where | Addition |
|---|---|
| `score.contributors_json` | Each contributor gains `value`. Add a `ledger` object (§5.4) |
| `score.efficiency_json` | Add `speedWobbleMps`, `stopStartCount`. `rating` is nullable while calibrating |
| `Trip` | Add `suspect: NONE/TRANSIT/CYCLING` and `suspectAnswer` (§1.8) |
| `TripSegment.reason` | Add `NOT_ALIGNED`, `LOW_IMU_RATE`, `IMU_GAP` |
| R1 `Motion.quality` | Bit meanings in §2.7 |
| R0 `Chunk.chunk_t0_ms` | May be negative for backfilled samples. R0 keeps the candidate-start origin and R1 is rebased to `Trip.startedAt` |

### 0.9 Conflicts found while writing this

For a follow-up documentation pass. None blocks DRIVE-62.

| Where | Problem | Treated here as |
|---|---|---|
| `data-model.md` §2.3 step 4, §2.6 ("Thirty days covers a release cycle"), `trip_file.expires_at` | Still describes automatic R0 expiry, contradicting Gate 4 and the same section's own table | Gate 4 wins. R0 has no expiry and `expires_at` stays null |
| ADR-003 (`DPTR`) vs `data-model.md` §2.4 (`DPTF`) | File magic differs | `DPTF`. ADR-003 says the data model owns the layout |
| `data-model.md` `speedConsistencyCv` | A ratio to mean speed improves as speed rises, which breaks F-SCO-07 AC3 if scored | Kept for display only. The rating uses `speedWobbleMps` |
| F-SCO-02 ("fewer than 3") vs F-STY-03 (trips 2–3 calibrating) | One reading scores against a baseline on trip 3 | A baseline needs 3 **prior** trips, so it first applies on the 4th |
| A3 §2 (`.xArbitraryCorrectedZVertical` "does not depend on the magnetometer") | Apple's documentation says this frame uses the magnetometer, when available and calibrated, to improve long-term yaw | Harmless: §2.5 does not use absolute attitude yaw |
| Prompt names vs A3 state names | `finishing`/`processed` vs `ending`/`finalised` | Prompt names. Persisted `Trip.status` is mapped in §1.2 |

---

## 1. Trip state machine

### 1.1 Shape

One pure reducer, unit-testable without a device:

```kotlin
fun reduce(state: TripState, event: TripEvent, cfg: PipelineConfig): Transition // (newState, effects)
```

Effects are data (`StartGnss`, `StopGnss`, `AppendR0`, `Schedule(timerId, delayMs)`, `Cancel(timerId)`, `ArmParkedRegion(lat, lng, radiusM)`, `StartFinalisation`, `SetImuRate(hz)`, `UpdateLiveSurface(elapsed, distance)`, …). Timers are monotonic and arrive back as `TimerFired`.

### 1.2 States

```
              ┌───────────────── rejected / timeout ───────────────┐
              ▼                                                    │
  IDLE ──wake──► CANDIDATE ──confirmed──► RECORDING ◄──moved──► PAUSED
    ▲                                          │                    │
    │                                       stop / timer         stop timer
    │                                          ▼                    ▼
    └──── PROCESSED ◄──── finalised ──── FINISHING ◄───────────────┘
  manual Start: IDLE ──► RECORDING. Kill or crash from any active state: §1.7
```

| State | Meaning | GNSS | IMU | `Trip.status` |
|---|---|---|---|---|
| `IDLE` | Armed, or auto-detect off. Includes `dbLocked` before first unlock | Off | Off | none |
| `CANDIDATE` | Something woke us, not yet proven a drive | 1 Hz | 50 Hz, written to R0 | `RECORDING`, not listed in the UI |
| `RECORDING` | Confirmed trip | 1 Hz | 50 Hz (F-CAP-16 back-off) | `RECORDING` |
| `PAUSED` | Vehicle stationary inside a trip | 1 Hz | 50 Hz, then 10 Hz once stationary for 120 s (§1.6) | `RECORDING` |
| `FINISHING` | Batch pipeline running, app kept awake | On until commit | Off | `RECORDING` |
| `PROCESSED` | Committed. Transient, then `IDLE` | Off | Off | `FINALISED`, `INTERRUPTED` or `DISCARDED` |

### 1.3 Platform triggers

Platform code maps its callbacks to one `WakeKind`. The reducer never sees OS types.

| `WakeKind` | iOS | Android |
|---|---|---|
| `PARKED_EXIT` | `CLMonitor` circular condition exit at the last parking spot | Geofence API exit (never an FGS, per the 27 Jan 2027 Play change) |
| `MOVE_SIGNAL` | Significant location change | none |
| `ACTIVITY_VEHICLE` | Not a wake. `CMMotionActivityManager` history is queried on wake | Activity Recognition `IN_VEHICLE` enter, which starts the `location` FGS |
| `MANUAL` | In-app Start, Control Center control, widget (`LiveActivityIntent`) | In-app Start, Quick Settings tile, widget |
| `RELAUNCH` | Core Location relaunch. Sessions are recreated in Swift before Kotlin starts (ADR-002 §5) | FGS restart |

`Visits` confirm parking and recover missed trips (F-DQ-07) but never start a trip. Bluetooth is P2 and absent here.

**Activity confidence.** iOS maps `low/medium/high` directly. Android transitions carry no confidence, so an `IN_VEHICLE` enter maps to `MEDIUM`.

### 1.4 Transition table

Times are monotonic. All values come from `PipelineConfig`, and **P-nn** refers to §9.

| From | Event | Guard | To | Effects and notes |
|---|---|---|---|---|
| `IDLE` | wake (not `MANUAL`) | Auto on, no F-CAP-15 pause, DB open | `CANDIDATE` | `StartGnss`, IMU 50 Hz, create trip and R0, query activity history (15 min, P-14), start `CandidateTimeout` |
| `IDLE` | `MANUAL` | Location granted | `RECORDING` | As above, and live surface starts. `source = MANUAL` |
| `CANDIDATE` | fix | **Confirm rule** (§1.5) | `RECORDING` | Keep R0, post silent F-CAP-11 notification, run backfill (§2.8) |
| `CANDIDATE` | activity or fix | **Reject rule** (§1.5) | `IDLE` | `StopGnss`, status `DISCARDED`, delete samples now. A 24 h sweep is the fallback |
| `CANDIDATE` | `CandidateTimeout` | | `IDLE` | Same as reject, `reason = timeout` |
| `RECORDING` | sample | Stationary entry (§1.6) for 30 s | `PAUSED` | `Mark PAUSED`, start `StopTimer` measured from `stoppingBeganAt`, start `ImuIdleTimer` (120 s) |
| `PAUSED` | `ImuIdleTimer` | | same | IMU 10 Hz |
| `PAUSED` | motion sample | IMU at 10 Hz and `|userAccel| ≥ 0.03 g` | same | IMU back to 50 Hz immediately, so the pull-away is captured at full rate |
| `PAUSED` | fix | Resume condition (§1.6) | `RECORDING` | IMU 50 Hz, `Mark RESUMED`, cancel `StopTimer` and `ImuIdleTimer` |
| `PAUSED` | `StopTimer` | | `FINISHING` | `endedAt = stoppingBeganAt` (F-CAP-03 AC1) |
| `RECORDING`, `PAUSED` | user Stop | Confirmed on S-DRV-02 | `FINISHING` | `endedAt = now` |
| `RECORDING`, `PAUSED` | Passenger, keep | | `FINISHING` | `kind = PASSENGER`, no score, content lock lifted |
| `RECORDING`, `PAUSED` | Passenger, don't save | | `IDLE` | Stop, delete all samples within 1 min |
| `RECORDING`, `PAUSED` | battery ≤ 5 % | | `FINISHING` | `by = battery` (EC-BAT) |
| `RECORDING`, `PAUSED` | free space < 100 MB | | same state | Stop R0 IMU, keep GNSS. The trip scores as Unscorable (EC-STO) |
| `RECORDING` | power change | §2.7 table | same | `SetImuRate`, `Mark` |
| `FINISHING` | pipeline done | Short-trip rule (§1.6) | `PROCESSED` | Commit, stop GNSS, `ArmParkedRegion` at the end point (120 m, P-05) |
| `PROCESSED` | | | `IDLE` | Re-arm triggers, reset the F-CAP-14 watchdog |

No state accepts an interaction requirement while moving (SAF-01).

### 1.5 Confirming or rejecting a candidate

```
humanPowered = the latest activity is walking, running or cycling at ≥ MEDIUM

confirm if goodFixSeen (≥ 1 fix with hAcc ≤ 50 m) and (
    (latest activity is not cycling ≥ MEDIUM
     and speed ≥ 6.94 m/s held continuously for ≥ 20 s)                     // 25 km/h; any slower fix restarts it
    or (latest activity is automotive ≥ MEDIUM and not humanPowered
        and latest speed ≥ 2.78 m/s, from a fix ≤ 5 s old) )                // 10 km/h

reject if humanPowered and ≥ 90 s since the wake and max speed so far < 4.17 m/s   // 15 km/h
```

Confirm is evaluated first, then reject, then the 180 s timeout. Motion alone can never confirm a trip (EC-GPS). A wake while the driver is still walking to the car must not be rejected at once, which is why the reject rule waits 90 s.

### 1.6 Stationary, resume and short trips

| Rule | Value |
|---|---|
| Stationary entry | Speed < 0.56 m/s (2 km/h) on 3 consecutive fixes, or platform stationary signal (iOS `liveUpdates` `stationary`, Android `STILL`) with speed < 0.56 m/s. `stoppingBeganAt` is the first such fix |
| No GNSS (EC-TUN, underground parking) | IMU stillness (§2.4) for the same duration decides |
| Resume | Speed ≥ 2.0 m/s (7.2 km/h) on 3 consecutive fixes and the latest activity is not walking, running or cycling at ≥ MEDIUM, or IMU non-still for ≥ 3 s with automotive activity. The band from 0.56 to 2.0 m/s is the hysteresis, and the activity check stops the walk from the car restarting the timer |
| Stop timer, auto | 240 s (P-06, tuning range 120–480 s). Target Q10 |
| Stop timer, manual, no Stop tapped | 900 s (P-08). The Story says so (F-CAP-01 AC4) |
| Auto trip < 500 m or < 120 s | `DISCARDED` (F-CAP-17) |
| Manual trip < 500 m or < 120 s | Kept, "Short trip", no score |
| Trip > 12 h | At finalisation, split at the longest stop > 30 min (tie: earliest). Without such a stop, no split |

### 1.7 Kill and relaunch (F-CAP-06)

R0 is the recovery record. On `RELAUNCH` or the next launch with a trip still `RECORDING`:

1. Read the valid R0 prefix. A truncated last chunk is dropped.
2. Let `age` be the time since the last R0 sample. If `age ≤ 600 s` (P-09) and the trip is still moving, resume in the same trip. Add a `GAP` span with reason `APP_KILLED`.
3. Otherwise finalise with status `INTERRUPTED`, `interrupted.atS` at the last sample and all data kept. The missing tail is never filled or scored.

"DB unavailable before first unlock" sets `dbLocked`. No trip starts, and iOS records a timestamp-only wake (EC-RBT).

### 1.8 False trips and passengers

**Passengers cannot be detected from phone sensors in v1 (Q8).** There is no passenger detector, and none should be added. Every passenger trip records as a driver trip unless the user marks it, in two taps at most, during the trip (F-CAP-08) or after (F-HIS-02).

**Bus, train and bike (OQ-20).** Finalisation sets `Trip.suspect`. `kind` stays `DRIVER` until the user answers. These heuristics are weak and exist only to ask a question (Q7, Q6).

```
CYCLING if moving-time share with cycling ≥ MEDIUM ≥ 0.30
        or (no automotive ≥ MEDIUM seen and p95 gated speed ≤ 12 m/s)

TRANSIT if ≥ 2 of:
   (a) ≥ 4 stops with dwell 10–60 s and median stop-to-stop distance 200–800 m (bus pattern)
   (b) route length ÷ straight-line distance < 1.05, p95 |lateral| < 0.08 g, trip ≥ 5 km (rail pattern)
   (c) automotive ≥ MEDIUM for < 40 % of moving time
```

CYCLING takes priority over TRANSIT. Unanswered suspects follow D7. A "No" answer is stored. **The only per-user tuning (F-CAP-04 AC4):** after 2 "No" answers on a route, suppress the transit question on that route.

---

## 2. Signal processing

### 2.1 What platform adapters must deliver

```kotlin
data class LocationSample(tMonoNs: Long, wallMs: Long, lat: Double, lng: Double, altM: Double?,
    hAccM: Double?, vAccM: Double?, speedMps: Double?, speedAccMps: Double?,
    courseDeg: Double?, courseAccDeg: Double?)
data class MotionSample(tMonoNs: Long, userAccelG: Vec3, gravityUp: Vec3 /* unit, away from Earth */,
    rotRateDps: Vec3, attitude: Quat?)
```

**Normalised device frame:** right-handed, `x` towards the right edge, `y` towards the top edge, `z` out of the screen. `userAccelG` is the device's own kinematic acceleration in g, so positive `x` means the device is accelerating towards its right edge. `rotRateDps` is positive counter-clockwise about each axis.

iOS and Android differ in units and, at least for gravity, in sign. **Each adapter converts, and a real-device conformance test (shake right, tilt, spin) is mandatory.** Do not assume a convention from documentation alone. Android needs `m/s² ÷ G` and rad/s → °/s. iOS needs rad/s → °/s.

**Sensors.** iOS: `CMDeviceMotion`. Android: linear acceleration, gravity, gyroscope, game rotation vector. No magnetometer dependency. A device with no gyroscope records capability `NO_GYRO`: gravity comes from a 0.3 Hz first-order low-pass, cornering events are off and the badge cannot exceed Limited.

### 2.2 Timestamp alignment

- One monotonic clock per trip. iOS converts `CLLocation.timestamp` to monotonic using `nowMono − (nowWall − fix.timestamp)` at delivery. Android uses `elapsedRealtimeNanos`.
- `t` uses the monotonic clock. Wall time is used only for display (`wall_ms_delta`).
- **GNSS lag.** GNSS speed lags the IMU. Over the first 300 s with valid data, compute the normalised cross-correlation between the 1 Hz IMU longitudinal signal and `dv/dt` for lags −1500…+1500 ms in 40 ms steps. Use the best lag if its correlation is ≥ 0.6, otherwise 0. Ties: smallest |lag|. Used only for alignment (§2.5) and event speed readouts.

### 2.3 GNSS filtering

```
for each fix, in time order:
  reject if hAcc is missing or hAcc > 30 m                                  // P-11, EC-GPS
  reject if speedMps > 90 m/s                                               // no road car in scope
  reject if |speed − lastGoodSpeed| / dt > 12 m/s²                          // F-DQ-03 AC3
  reject if distance(prev, this) / dt > max(1.5 × max(speed, prevSpeed), 20 m/s)   // teleport
  if speedMps is missing: speed = distance over the last 3 fixes / elapsed, mark derived
```

Rejected fixes stay in R0 and are absent from R1's accepted set. A fix sequence is continuous while gaps are ≤ 3 s.

**Speed estimate `v(t)` for internal use.** A two-state Kalman filter at 25 Hz over R1 longitudinal acceleration, with GNSS speed as the measurement. It feeds the moving mask, stop detection and event speeds. **Why not a full GNSS/INS filter:** phone-only data gives no ground truth to validate one, and it would not improve anything a user sees.

```
state v (m/s), b (m/s² bias); P = [[4, 0], [0, 0.01]]; v0 = first gated GNSS speed; b0 = 0
each sample: a = lon_g × G
  v += (a − b) × Δ
  P = F P Fᵀ + diag(0.04 × Δ, 4e−6 × Δ)        where F = [[1, −Δ], [0, 1]]
for each fix with t_fix ≤ t: y = z − v; S = P00 + R; K = [P00/S, P10/S]
  v += K0·y; b += K1·y; P = (I − K·[1,0]) P      where R = max(speedAcc², 0.09)
v = max(v, 0)
```

During a GNSS gap, if IMU stillness holds and the last fix is > 3 s old, set `v = 0`.

**Displayed speed stays GNSS.** Replay "current speed" is Observed GNSS, and speed stats use gated GNSS only. Integrated IMU speed is never shown.

### 2.4 Detecting that the phone moved (F-DQ-02)

Run on 25 Hz device-frame data, before alignment. The vehicle itself rotates about the vertical, so only rotation **out of** the vertical counts.

```
ω⊥ = ω − (ω·u) u                      // u = gravityUp
hit A: |ω⊥| ≥ 45 °/s sustained ≥ 0.25 s       // normal car roll and pitch stay under ≈ 15 °/s
hit B: angle(u(t), mean u over [t−5, t−1]) ≥ 20°
hit C: |userAccel| ≥ 1.2 g and |dv/dt| from GNSS < 0.25 g within ±1 s
merge hits ≤ 5 s apart
span = [firstHit − 2 s, lastHit + 3 s)  →  EXCLUDED, reason PHONE_MOVED
```

Each span ends one **mount epoch** and starts the next. The app never claims the driver touched the phone (F-DQ-02).

**IMU stillness** (over 2 s): std of |userAccel| < 0.008 g and |ω| < 1 °/s.

**Placement** (`quality.placement`, Estimated): `MOUNTED` when there are no `PHONE_MOVED` spans and the gravity direction's std is < 3°. `CUPHOLDER` (shown as "Loose") when spans exist or that std is ≥ 3°, and alignment converged. Otherwise `UNKNOWN`. `POCKET` and `BAG` are reserved: v1 cannot tell them apart.

### 2.5 Phone-to-vehicle alignment (F-DQ-01)

The rotation is treated as **constant per mount epoch**, which keeps working through GNSS gaps. Since finalisation is batch, one estimate covers the whole epoch and is applied retroactively, including its first seconds.

```
1. Up axis:  eU = normalise(mean of gravityUp over the epoch)
2. Horizontal basis:  a = device axis with smallest |axis·eU|
                      h1 = normalise(a − (a·eU) eU);  h2 = eU × h1
3. 1 Hz inputs, GNSS lag applied, using gated fixes with speed ≥ 3 m/s and courseAcc ≤ 20° when present:
      p1 = mean(userAccel·h1), p2 = mean(userAccel·h2)                  // g
      L  = (v[i+1] − v[i−1]) / (t[i+1] − t[i−1]) / G                    // forward, g
      C  = −v[i] × (wrapped Δcourse in radians, clockwise-positive) / Δt / G   // left, g
4. Keep samples with |L| ≥ 0.03 or |C| ≥ 0.03.
      Sc = Σ(p1·L + p2·C);  Ss = Σ(p2·L − p1·C);  φ = atan2(Ss, Sc)
5. eF = cosφ·h1 + sinφ·h2;  eL = eU × eF
6. Coherence ρ = hypot(Sc, Ss) / sqrt(Σ(p1² + p2²) × Σ(L² + C²))
   converged iff kept ≥ 20 and ρ ≥ 0.6 and Σ(L² + C²) ≥ 20 × 0.05²
7. Bias: bLon = median(p·eF − L), bLat = median(p·eL − C).  If |b| > 0.05 g, set that b to 0.
8. Per sample:
      lon_g = userAccel·eF − bLon
      lat_g = −(userAccel·eL − bLat)                      // right is positive
      vert_g = userAccel·eU
      yaw_dps = −ω·eU
```

GNSS speed sign resolves the 180° ambiguity without a special case. Verified on 200 random synthetic mounts: worst yaw error 0.75°.

An epoch that does not converge, or lasts < 20 s, is `EXCLUDED` with reason `NOT_ALIGNED`, which is the "calibrating" state of F-DQ-01 AC2. `alignmentSettledS` is the earliest `t` from which the running estimate meets step 6 and stays within 5° of the final φ.

**Tolerances (F-DQ-01 AC3).** Synthetic mounts: yaw error ≤ 2°, lon/lat RMS error ≤ 0.01 g. Recorded fixtures: ≤ 5°, ≤ 0.03 g against the A19 reference.

### 2.6 Resampling

For `t_k = k × 40 ms`, each channel takes the mean of its samples in `[t_k − 20 ms, t_k + 20 ms)`. This box average is the anti-alias filter. No sample in the window: interpolate linearly if the neighbours are ≤ 200 ms apart and set `INTERPOLATED`, otherwise set `IMU_GAP` and write 0.

R1 stores these values **unfiltered**, so later filter changes need only R1. Each scorable run is filtered separately at use, so an excluded span cannot ring into its neighbours.

### 2.7 Gaps, rates and quality flags

| R1 `Motion.quality` bit | Meaning | Scorable? |
|---|---|---|
| 0 `INTERPOLATED` | Filled across ≤ 200 ms | Yes |
| 1 `ESTIMATED` | Backfill (§2.8) | No |
| 2 `EXCLUDED_PHONE` | `PHONE_MOVED` | No |
| 3 `NOT_ALIGNED` | Epoch never converged | No |
| 4 `IMU_GAP` | > 200 ms without samples | No |
| 5 `LOW_RATE` | Delivered rate in that second < 20 Hz | No |

A sample is **ok** when bits 1–5 are clear.

| Situation | Handling |
|---|---|
| IMU at 25 Hz (LPM, battery ≤ 15 %, thermal serious, steady cruise) | Same pipeline, `Mark`, noted in quality |
| IMU at 10 Hz (thermal critical, or a long stop) | Moving: `LOW_RATE`, excluded with `THERMAL` or `LOW_IMU_RATE`. Stationary: the bit is set but no span is created, because nothing there is scored |
| GNSS gap < 180 s with continued motion (P-12) | `GAP` span, reason `NO_GNSS` or `TUNNEL`. Drawn dashed "Estimated". Chord distance goes into `estimatedDistanceM` if `0.5 ≤ chord ÷ (mean speed × gap) ≤ 1.5` |
| GNSS gap ≥ 180 s | Labelled gap. No distance |
| GNSS gap, IMU ok, epoch aligned | Still scored (EC-TUN). Events listed, not pinned |
| App killed | `GAP`, `APP_KILLED`. Never interpolated or scored |

**"Tunnel"** is a gap of ≥ 5 s with non-still IMU that starts and ends with good fixes.

### 2.8 The estimated start (F-CAP-05)

Only for `AUTO` trips with a known parking spot. After a reboot there is none, so the trip starts at the first fix.

```
tStart  = iOS:     beginning of the automotive activity run in motion history
          Android: timestamp of the IN_VEHICLE transition
d       = haversine(parkingSpot, firstGatedFix)
Reject the estimate if d > 5000 m, or d / (tFirstFix − tStart) > 60 m/s, or tFirstFix − tStart > 900 s.
Otherwise: startedAt = tStart (provenance E), estimatedStartM = d (provenance E),
           span ESTIMATED [0, t(firstGatedFix)), reason ESTIMATED_START
```

The straight-line distance slightly understates the true path, and the "Estimated" label covers that. iOS writes `CMSensorRecorder` accelerometer data for the gap to R0 at negative chunk offsets, kept for later use. Per D6, **the estimated segment produces no events, no pins and no Smoothness**. Android has no IMU for the gap.

### 2.9 Data-quality badge (F-DQ-04)

Definitions: `totalS = durationS`. `excludedS` is the sum of `EXCLUDED`, `ESTIMATED` and interrupted spans. `scoredS = totalS − excludedS`. `f = scoredS ÷ totalS`. `gnss = gnssCoveragePct ÷ 100`, over moving time. `imuDeliv` is delivered ÷ expected samples at the nominal rate in force, while recording. `T` is the ledger's scored moving seconds (§5.2).

Evaluate top to bottom. The first match wins and sets `reason`.

| Badge | Condition |
|---|---|
| **Unscorable** | No IMU data (EC-STO), no precise location, no epoch converged, `f < 0.50`, or `T < 120 s` |
| **Limited** | `f < 0.70`, or `gnss < 0.60`, or `NO_GYRO` |
| **Usable** | `f < 0.85`, or `gnss < 0.85`, or `imuDeliv < 0.90` |
| **Good** | Otherwise |

Only Good and Usable count for WDD (F-DQ-04 AC3). `kind ≠ DRIVER` trips keep a badge but have no score.

---

## 3. Derived metrics

Filters are zero-phase forward-backward biquads with first-sample steady-state initialisation. Coefficients for `fs = 25`:

| Channel | Use | `fc` | b0 | b1 | b2 | a1 | a2 |
|---|---|---|---|---|---|---|---|
| `a3` | Detectors, displayed G, G-G, peak G | 3.0 Hz | 0.09131490043583196 | 0.18262980087166392 | 0.09131490043583196 | −0.9824057931083952 | 0.3476653948517232 |
| `a1.5` | Jerk ledger | 1.5 Hz | 0.027859766117136024 | 0.05571953223427205 | 0.027859766117136024 | −1.475480443592646 | 0.5869195080611902 |

Recursion: `o = b0·x + z1; z1 = b1·x − a1·o + z2; z2 = b2·x − a2·o`, initialised with `z1 = x0(1 − b0)`, `z2 = x0(b2 − a2)`.

Provenance: **O** Observed, **D** Derived, **E** Estimated. "Max decimals" is the F-DQ-05 AC3 display limit.

| Metric | Formula | Unit | Prov. | Max decimals |
|---|---|---|---|---|
| Start / end time | `startedAt` (§2.8), `endedAt` (§1.4) | time | O, E when backfilled | minute |
| Duration | `endedAt − startedAt` | s | D | minute |
| Moving flag | `v` hysteresis: moving at ≥ 1.0 m/s, stopped at < 0.5 m/s, starts stopped | — | D | — |
| Moving / stopped time | Σ flagged samples × Δ | s | D | minute |
| Distance | Σ haversine over consecutive gated fixes while moving, plus `estimatedDistanceM` | m | D, part E | 0.1 km |
| Estimated distance | Start estimate + qualifying gap chords | m | E | 0.1 km |
| Route shape | Gated track simplified to ≤ 400 preview rows (`data-model.md` §2.5). Estimated pieces dashed | — | O, part E | — |
| Current speed | Gated GNSS speed at the marker | m/s | O | 1 km/h or mph |
| Max speed | Max of median-of-3 gated speed, with `speedAcc ≤ 2 m/s` when present, and at least 3 consecutive fixes within 2 m/s of the value. Tie: earliest | m/s | D | 1 km/h or mph |
| Average moving speed | Gated distance ÷ moving time | m/s | D | 1 km/h or mph |
| Stops | Stopped runs ≥ 10 s. Two runs split by < 3 s of motion merge | count, s | D | integer |
| Stop-start count | Stopped runs ≥ 3 s, same merge | count | D | integer |
| Elevation | Fixes with `vAcc ≤ 15 m`, median-of-5, then 10 s mean | m | O | 1 m |
| Elevation gain / loss | Dead-band 5 m: a change counts once the altitude moves ≥ 5 m from the last reference, which then resets | m | D | 1 m |
| Longitudinal, lateral G | `a3` of R1 `lon_g`, `lat_g` | g | D | 0.01 |
| Jerk | `j[k] = (a1.5[k+1] − a1.5[k−1]) ÷ 2Δ`, in m/s² input | m/s³ | D | 0.1 |
| G-G cloud | (`lat`, `lon`) of `a3` on ok samples, every 5th sample | g | D | 0.01 |
| G-G envelope | 36 sectors of 10°. Radius = 99th percentile of `|g|` in the sector, needing ≥ 20 points, else omitted | g | D | 0.01 |
| Speed wobble | Over 1 Hz gated speed, windows of 21 fixes at hop 10 with no gap, `min ≥ 5 m/s` and `|last − first| ≤ 1.5 m/s`. Per-window population std. Value = sorted windows' `[n/2]`. Needs ≥ 3 windows | m/s | D | 0.1 |
| `speedConsistencyCv` | Same windows, std ÷ mean, median. **Display only** | ratio | D | 0.01 |
| Delivered IMU rate | Median per-second sample count | Hz | O | 1 Hz |
| GNSS coverage | Gated fixes ÷ expected at 1 Hz, over moving time | % | D | 1 % |
| Part of day | Local time of start: Morning 05–11, Midday 11–14, Afternoon 14–17, Evening 17–21, Night 21–05 | — | D | — |
| Battery used | OS level delta over the trip, diagnostics only | % | O | 1 % |

**Period and recap metrics**

| Metric | Rule | Prov. |
|---|---|---|
| Weekly or monthly average, trend | Mean of scorable `DRIVER` trips in the period, half-up | D |
| Change claim ("38 % fewer abrupt stops") | Shown only if each period has ≥ 5 trips, the relative change is ≥ 20 % and Welch `|t| ≥ 2.0` on per-trip values | D |
| Focus habit | The contributor rated `ROUGHER` most often over the last 5 trips. Tie order as §6. Needs ≥ 5 scorable trips in the week | D |
| Focus progress ("gentler in 7 of 9") | Trips whose contributor sub-score beats the median at focus start. Never a streak | D |
| Wrapped totals | Sums and modes over `DRIVER` trips | D |
| Wrapped comparison | `round(totalKm ÷ referenceKm, 1)` from A2's vetted list | E |
| Archetype | First match over the period, on Good or Usable trips: **Climber** (Smoothness rose ≥ 5 between halves of the period) → **Glider** (≤ 0.5 events per moving hour) → **Metronome** (bottom quartile of the user's own wobble for ≥ 60 % of trips) → **Urbanist** (`CITY` ≥ 60 % of trips and top quartile for stop-start with Braking `SMOOTHER` or `USUAL`) → **Road-Tripper** (`HIGHWAY` ≥ 50 %) → **Night Owl** (`NIGHT` ≥ 40 %) → Glider | E |

Neither speed nor max G feeds any row in this table.

---

## 4. Event detectors

Three detectors run on `a3`, over ok samples only. They are rule-based, and each carries its evidence.

### 4.1 Thresholds

| Type | Channel | Enter | Exit | Min duration | Speed gate |
|---|---|---|---|---|---|
| `BRAKE` | `−lon` | 0.30 g | 0.20 g | 0.40 s | `v[start] ≥ 2.5 m/s` |
| `ACCEL` | `+lon` | 0.28 g | 0.18 g | 0.40 s | `v[end] ≥ 2.5 m/s`, so hard pull-aways from rest count |
| `CORNER` | `±lat`, each sign separately | 0.36 g | 0.25 g | 0.50 s | `v[start] ≥ 2.5 m/s`, and `|yaw| ≥ 5 °/s` with the same sign as `lat` at the peak |

### 4.2 Algorithm

```
detect(channel c, sign s, type):
  k = 0
  while k < n:
    if s·c[k] ≥ enter and ok[k]:
      start = k;  while start > 0 and s·c[start−1] ≥ exit and ok[start−1]: start−−
      end   = k;  while end < n−1 and s·c[end+1] ≥ exit and ok[end+1]: end++
      emit (start, end);  k = end + 1
    else k++
  merge consecutive intervals with gap ≤ 1.0 s
  for each interval:
    peak = argmax s·c (tie: earliest);  p = s·c[peak];  dur = (end − start + 1) × Δ
    drop if dur < minDuration or the speed gate fails or (CORNER and the yaw gate fails)
    drop if roadImpact (§4.3)
```

### 4.3 Potholes and speed bumps (F-SCO-04)

```
impact[k] = true within ±15 samples of any |vert_g[k]| ≥ 0.40 g
roadImpact(event) = impact[peak] and dur < 0.8 s
                    and max |vert_g| over [start−5, end+5] ≥ 1.5 × p
```

Impact samples are also removed from the jerk ledger and from `T`, because a bump is not a driver input.

### 4.4 Confidence (Estimated)

```
m = (p − enter) / enter
level = HIGH if m ≥ 0.20; MEDIUM if m ≥ 0.07; else LOW
downgrade one level if (BRAKE or ACCEL) and GNSS was valid around the event
     and |Δv_GNSS over [start−1 s, end+1 s]| < 0.5 × |∫ a dt|     // the IMU claims more than the car did
downgrade one level if trip badge is LIMITED or there is no GNSS within the event window
```

### 4.5 What each event stores

`type`, `tStartS`, `tPeakS`, `tEndS`, `durationS`, `peakG` (`p`), `direction` for corners, `lat`/`lng` at peak (null if the nearest gated fix is > 3 s away, which means listed but not pinned), `speedBeforeMps` and `speedAfterMps` (`v` at `start − 1 s` and `end + 1 s`), `confidence`, `algorithmVersion`.

**Evidence window.** Prompt A12 says "stored", but `data-model.md` §1.9 computes it on demand and that wins: the event stores its bounds, and the trace comes from R1 as `[tPeak − 10 s, tPeak + 10 s]` (F-RPL-04). If R1 was removed, the sheet says "Detailed trace not kept".

**Cap:** at most 500 events per trip. Keep the largest `m`, tie earliest.

No event can exist inside an excluded or estimated span, because those samples are not ok.

---

## 5. Scores

### 5.1 Principles

- **Abruptness, not force.** Hard but well-ramped braking costs less than a snatch. Jerk, not G level, drives the score.
- **Time-based exposure only.** Costs are per **moving minute**. A per-km rate would improve with speed.
- **A comfort band.** Jerk inside the band costs nothing, so ordinary gentle driving is not punished for existing.
- **Own baseline.** Compared only with the driver's own trips (F-SCO-01 AC3).

The score is computed only when `kind = DRIVER` and the badge is not Unscorable. Otherwise `score = null`.

### 5.2 Jerk ledger

```
moving[k], ok[k], impact[k]           // §3, §2.7, §4.3
noiseFloor(a) on one axis:
   Q = |j[k]| over k with ok, moving, not impact, |a1.5[k]| ≤ 0.08 g
   if |Q| < 750: return 1.2                       // needs 30 s
   sigma = 1.4826 × Q_sorted[|Q| / 2]
   return min(2.4, max(1.2, 3 × sigma))
Jx = noiseFloor(lon), Jy = noiseFloor(lat)        // m/s³

stop relief: for every stopped run lasting ≥ 1.0 s and not at the start of the trip,
             samples in [stopIndex − 25, stopIndex + 13) get relief

for k in 1 .. n−2, when ok and moving and not impact:
    T += Δ
    band = relief[k] ? 2·Jx : Jx
    if a1.5_lon[k] < 0:  E.brake += max(0, |jx[k]| − band) × Δ
    else:                E.accel += max(0, |jx[k]| − band) × Δ
    E.corner += max(0, |jy[k]| − Jy) × Δ
```

**Why the noise floor.** With a fixed band and a 2 Hz filter, simulated road noise of 0.03 g on both axes cost a clean drive about 50 points, which would penalise the road and not the driver. With the floor and the 1.5 Hz channel, the same noise moved the score by at most 7 points. At 0.05 g the score still loses up to about 20, because the floor is capped so that a rattling phone cannot excuse everything. These are simulated noise levels, and P-18 needs real roads.

**Why stop relief (F-SCO-07 AC1).** A jerk-only score would punish the complete stop, because stopping always has a final transition and a rolling stop can skip it. Relief widens the band only around a real stop. G-06 proves that a complete stop outscores a rolling one with the same inputs. **v1 cannot tell that a junction was there at all**, so the rule compares driving styles and does not identify a rolling stop as such.

### 5.3 Event cost and contributors

```
eventCost = 2.0 × confWeight × (1 + min(1, m))      // m/s²; confWeight HIGH 1.0, MEDIUM 0.7, LOW 0.4
added to E.brake, E.accel or E.corner by type

R_i = 60 × E_i / T      // i in brake, accel, corner; units m/s² per moving minute
wobble = §3
```

### 5.4 Ledger and instant recompute

Each event owns the window `[start − 25, end + 25]` in samples. Where windows overlap, the earlier event (sort order §0.5) keeps the sample, so windows are **disjoint**.

```
ledger = { base: {T, brake, accel, corner},
           perEvent: [{eventId, T, brake, accel, corner}] }   // each includes that event's eventCost
```

Totals are `base + Σ active events`. "Was it needed?" and "Not accurate?" drop that event's row. Re-summing is exact, needs no R1 and meets F-SCO-05's 1 s limit. The noise floors and the badge stay as computed at finalisation.

### 5.5 Context (normalisation)

From gated 1 Hz speed, `h` = share of fixes ≥ 22.2 m/s (80 km/h) among fixes ≥ 1.0 m/s.

| `h` | Context | `c` |
|---|---|---|
| `< 0.15` | `CITY` | 1.00 |
| `0.15 ≤ h < 0.60` | `MIXED` | 0.85 |
| `≥ 0.60` | `HIGHWAY` | 0.70 |

`c` divides each measure, so the same inputs score **lower** at a higher context. The order `CITY ≥ MIXED ≥ HIGHWAY` is what guarantees that more speed cannot raise the score. Highway driving is naturally steadier, so the stricter scale is also the fairer one.

### 5.6 Smoothness

Piecewise-linear curves, flat outside the end knots.

| `R_i ÷ c` | 0 | 1 | 3 | 6 | 12 | 25 |
|---|---|---|---|---|---|---|
| Sub-score (Braking, Acceleration, Cornering) | 100 | 92 | 78 | 60 | 35 | 0 |

| `wobble ÷ c` (m/s) | 0.3 | 0.8 | 1.5 | 3.0 | 5.0 |
|---|---|---|---|---|---|
| Sub-score (Consistency) | 100 | 85 | 60 | 30 | 0 |

| Contributor | Weight |
|---|---|
| Braking | 0.35 |
| Acceleration | 0.25 |
| Cornering | 0.25 |
| Consistency | 0.15 (if wobble is undefined, drop it and renormalise) |

```
S_raw = Σ w_i · sub_i / Σ w_i
Smoothness = floor(S_raw + 0.5)       // 0..100
```

**Exposure rule.** `T` must be ≥ 120 s (§2.9).

### 5.7 Baseline, comparison and the calibrating period

```
eligible(trip) = kind = DRIVER, Smoothness present, badge in {GOOD, USABLE},
                 suspect = NONE or answered "No"

basis:
  ROUTE    if this trip matches a route and ≥ 3 prior eligible runs of it exist
  CONTEXT  else if ≥ 3 prior eligible trips in the same context exist
  none     → calibrating

baseline = median of Smoothness over the last 10 prior eligible trips in the basis,
           ordered by (startedAt desc, id desc); even count → mean of the middle two, half-up
delta    = Smoothness − baseline
```

**Calibrating** (F-SCO-02, F-STY-03). When there is no basis, `calibrating = {k, of: 3}`, where `k` is the prior eligible trips in the context plus one, capped at 3.

- `k = 1` on the user's first scorable trip: "Smoothness {n}: your baseline."
- `k = 2` and `k = 3`: "Calibrating · {k} of 3".
- No delta, no bands and no trend claims while calibrating.
- A new context calibrates separately. The first-trip copy applies only to the first scorable trip ever.

**Contributors (F-SCO-03).** Each contributor's sub-score is compared with the median of the same sub-score over the same baseline trips: `≥ +6` is `SMOOTHER`, `≤ −6` is `ROUGHER`, otherwise `USUAL`. `reasonKey` comes from a fixed list. `eventIds` holds that channel's active events, by cost descending, tie earliest. Consistency has no events.

**Corrections (F-HIS-02).** Changing a trip's kind or an event's feedback recomputes, from stored ledgers, the baselines, deltas, bands and records of that trip and later ones, in `(startedAt, id)` order, within 2 s.

### 5.8 Unscorable

| Case | Result |
|---|---|
| Any Unscorable condition (§2.9) | `score = null`, no tip. Story variant F-STY-04, frames 1–2 and 7 |
| `kind ≠ DRIVER` | `score = null`, no tip, banner (F-RPL-07 AC4) |
| Manual short trip | "Short trip" label, `score = null` |
| Limited | Score shown with "based on {x} of {y} min", where x is `scoredS` and y is `totalS` |

### 5.9 Efficiency habits (proxy, F-SCO-09)

Always labelled Estimated. **Never states energy, fuel, kWh, litres, money or "% saved".** Needs `gnss ≥ 0.60` and the same eligibility as the baseline.

```
x1 = stopStartCount / (distanceM / 1000)                    // per km
x2 = abruptAccels / (T / 60)                                // per moving minute
     abruptAccels: runs of a3_lon ≥ 0.18 g (exit 0.12 g) lasting ≥ 1.0 s
x3 = speedWobbleMps

Bᵢ = median of xᵢ over the last 10 prior eligible trips in the same context; needs ≥ 3
Iᵢ = (xᵢ − Bᵢ) / max(Bᵢ, floorᵢ)      floors: 0.05, 0.10, 0.20
I  = mean of defined Iᵢ

rating = BETTER if I ≤ −0.15; WORSE if I ≥ +0.15; else USUAL; null while calibrating
```

`stopsPerKm` is compared only within a context, so higher speed on a different road class does not count as better.

### 5.10 Guardrails (F-SCO-07)

| Guard | Rule | Proof |
|---|---|---|
| Complete gentle stop ≥ rolling stop | Stop relief | G-06 |
| Speed never raises the score | Allow-list, context order | G-07 |
| Unusual pace sets no record | Pace band, §7.5 | G-11 |
| Every guardrail passes before a version ships | CI | §8 |

---

## 6. Coaching tip

Exactly one tip per scorable trip, deterministic and explainable (F-SCO-06). Copy is owned by A2/A6. This section owns the choice.

### 6.1 Library

Every tip has `tipId`, `category` and the copy keys. Tips never mention speed targets. The launch set has 14:

| Category | `tipId` |
|---|---|
| Braking | `brake_light`, `brake_ease_end`, `brake_longer_ramp` |
| Acceleration | `accel_progressive`, `accel_first_second`, `accel_merge_build` |
| Cornering | `corner_brake_before`, `corner_smooth_entry`, `corner_unwind_exit` |
| Consistency | `consist_hold_steady`, `consist_longer_look` |
| Anticipation | `anticip_read_ahead`, `anticip_coast_red`, `anticip_leave_gap` |

### 6.2 Selection

```
candidates = active events (not "needed", not "inaccurate"), confidence ≠ LOW

category cost: Cat_i = R_i for brake, accel, corner; cost of consistency = (100 − sub_cons) / 10

if candidates exist:
    category = highest-cost category that has a candidate
    anchor   = its event with the largest eventCost and a position
               (tie: earliest; no event with a position → earliest without one)
else if any moving sample has excess jerk:
    anchor span = the 5 s window with the most excess jerk (tie: earliest)
    category    = its channel
else:
    category = ANTICIPATION
    anchor span = the cleanest stretch (§6.3)

Tie order for categories: BRAKING, CORNERING, ACCELERATION, CONSISTENCY, ANTICIPATION

tipId within the category:
    choose by the rule table below, skipping any tipId used in the last 2 scorable trips,
    unless that leaves none; if none remain, take the first in table order.
```

| Category | Rule, first match wins |
|---|---|
| Braking | Anchor brakes to a stop → `brake_light`. Peak in the last 1.5 s of the event → `brake_ease_end`. Otherwise `brake_longer_ramp` |
| Acceleration | From `v < 2 m/s` → `accel_first_second`. `HIGHWAY` context → `accel_merge_build`. Otherwise `accel_progressive` |
| Cornering | Braking event within 3 s before → `corner_brake_before`. Peak in the first half → `corner_smooth_entry`. Otherwise `corner_unwind_exit` |
| Consistency | `HIGHWAY` → `consist_hold_steady`. Otherwise `consist_longer_look` |
| Anticipation | `stopStartCount ≥ 4` → `anticip_coast_red`. `CITY` → `anticip_leave_gap`. Otherwise `anticip_read_ahead` |

The result is stored as `tip {tipId, category, eventId?, placeKey?}`. `placeKey` comes from the anchor event's position, or from the nearest gated fix to the middle of the anchor span, so every tip is tied to a place. Feedback re-runs selection, so a "needed" event can never remain the tip's anchor (F-SCO-05 AC2).

### 6.3 The positive observation first

**Cleanest stretch** (F-SCO-06 AC2): the longest contiguous run of ok, moving samples with no event window and no sample whose excess jerk exceeds 0.5 × the band. Needs ≥ 30 s. Tie: earliest. Stored as `cleanest {distanceM, road?, t0S, t1S}`. If none qualifies, the Story uses its generic opener.

---

## 7. Routes and personal bests

### 7.1 Inputs

Route matching uses **preview tracks**, since `Route.referenceTripId`'s R1 may be gone. Only `DRIVER` trips with `gnss ≥ 0.60` can seed or join a route (EC-GPS, EC-PAS).

Trip start = the parking spot when §2.8 produced an estimate, otherwise the first gated fix. Trip end = the last gated fix.

### 7.2 Endpoint test

`radius = 200 m`, P-20.

```
startOK(trip, route) = distance(trip.start, route.start) ≤ radius
                    or route.start is within radius of the trip's first 1000 m of path
                    or trip.start  is within radius of the reference path's first 1000 m
endOK = distance(trip.end, route.end) ≤ radius
```

The first-kilometre clauses absorb start latency, which varies from trip to trip. **Direction is intrinsic:** A→B and B→A are different routes (F-RTE-01 AC4).

### 7.3 Path similarity

```
project both tracks to a plane: x = (lng − lng0) × 111320 × C, y = (lat − lat0) × 111320
   lat0, lng0 = route start; C = cos(lat0) rounded to 6 decimals, computed once

drop estimated rows and rows in labelled gaps
resample each track by arc length, step = max(50 m, length / 400)
reject if length ratio is outside [0.85, 1.15]

F = discrete Fréchet distance (standard O(nm) dynamic programme, using squared distances, sqrt at the end)
accept if F ≤ 150 m
similarity = 1 − min(1, F / 300)
```

Evaluate only routes passing §7.2. Choose the smallest F. Tie: oldest `createdAt`, then smallest `id`.

### 7.4 Candidates and promotion

```
on finalise, for an eligible trip (not passenger, not suspect, gnss ≥ 0.60):
  if it matches a route → RouteMatch(source = AUTO)
  else compare against unmatched eligible trips (newest 200 by startedAt, endpoints first)
       if a match exists → create Route(state = CANDIDATE, referenceTripId = oldest of the pair),
                           match both, and show the Story merge frame
```

`CANDIDATE` becomes `ROUTE` when named or at 3 runs. Centroids (`start*`, `end*`) are the running mean of matched endpoints. Auto-matching never overrides `source = USER`. A corrected kind removes the match.

### 7.5 Personal best

```
eligibleForRecord =
    kind = DRIVER                           → else ineligibleReason = PASSENGER
    and badge in {GOOD, USABLE}, Smoothness present and no unanswered suspect
                                            → else QUALITY
    and neededCount ≤ 2                     → else EXCLUSIONS
    and pace band passes                    → else PACE_BAND
    (reasons are checked in this order; the first failure is stored)

pace band: M = median movingS over the last 10 prior runs of this route (needs ≥ 3)
           pass iff 0.75 × M ≤ movingS ≤ 1.50 × M
           with fewer than 3 prior runs the band passes
```

A run sets the record only if `Smoothness > record`, strictly. Ties stay with the earlier trip. Records contain Smoothness only: never duration, speed or time (F-RTE-03 AC3). There is never a live pace or record readout (SAF-02). An ineligible pace shows "Unusual pace — not counted for records".

### 7.6 "Where you gained / lost" (F-RTE-04)

Pro, needs R1 for both runs.

```
map each run's moving samples to progress along the record run's path (nearest resampled vertex, monotonic)
split the path into 20 equal-length segments
segment score = same formula as §5.6, using that segment's E and T
ΔSeg = run − record
grey out any segment with < 60 % of its samples mapped or T < 10 s
"gained most" = max ΔSeg ≥ +5 (tie: earliest); "lost most" = min ΔSeg ≤ −5 (tie: earliest)
```

No time delta appears anywhere.

---

## 8. Test strategy

### 8.1 Levels

| Level | What | Where |
|---|---|---|
| Reducer | Table-driven test for every row of §1.4, with injected events and `TestClock`, plus kill-and-resume paths | `:shared:pipeline` `commonTest` |
| Synthetic signals | Golden R1 scenarios (§8.3), mount-rotation tests for §2.5, gap and rate tests | `commonTest`, JVM **and** `iosSimulatorArm64` (ADR-008) |
| Determinism | Each fixture runs twice with identical results. JVM and iOS outputs compared within §0.5 tolerances | CI |
| Recorded drives | Privacy-trimmed real drives from Spike 1 and A19, against goldens | CI |
| Guardrails | G-06, G-07, G-08, G-11, the import allow-list | CI gate. Any failure blocks a version |
| Coverage | ≥ 90 % line coverage on `domain` and `pipeline` (ADR-002 §9) | CI |
| Field validation | §8.5 | A19 |

### 8.2 Fixture format

**ADR-008 forbids real location data in the repo.** Fixtures are CSV so that the existing check applies: **line 1 must be exactly `# privacy-trimmed`**, on synthetic fixtures too. A missing header fails CI.

```
# privacy-trimmed
# dpfx: 1
# id: rec-commute-03
# source: synthetic | recorded-trimmed
# level: R0 | R1
# platform: ios | android | none
# imu_hz: 50
# trim_m: 1000                  (recorded only)
# transform: translate+rotate   (recorded only; the values are never committed)
# note: free text, no places
G,t_ms,lat,lng,alt_m,h_acc_m,v_acc_m,speed_mps,speed_acc_mps,course_deg,course_acc_deg
M,t_ms,ax,ay,az,gx,gy,gz,rx,ry,rz,qw,qx,qy,qz
V,t_ms,lon_g,lat_g,vert_g,yaw_dps,quality
A,t_ms,class,confidence
X,t_ms,mark,detail
```

Rows after the header, in any time order: `G` GNSS, `M` device-frame IMU (R0 fixtures, in the §2.1 frame), `V` vehicle-frame (R1 fixtures), `A` activity, `X` marks. Empty means null.

**Recorded-drive trimming (ADR-008).** Before commit: remove at least the first and last 1000 m, drop wall-clock dates, rotate the whole track by a fixture-specific angle about its centroid, correct `course_deg` by that angle, then translate it into the ocean test box (latitude 0–5°, longitude −30 to −25°). Precise coordinates, addresses and timestamps of day never enter the repo. Synthetic fixtures also live in this box.

**Files:** `<id>.csv` beside `<id>.golden.json`, under `shared/testing` (ADR-002), each ≤ 10 min and ≤ 2 MB. A13 owns the CI check itself. Golden file shape, with illustrative values:

```json
{
  "fixture": "rec-commute-03",
  "pipelineVersion": "pipe-1.0.0",
  "algorithmVersion": "smooth-1.0.0",
  "priorTrips": [],
  "expect": {
    "badge": "GOOD",
    "context": "CITY",
    "smoothness": 84,
    "calibrating": { "k": 1, "of": 3 },
    "ledger": { "T": 512.4, "brake": 11.2, "accel": 6.1, "corner": 5.9 },
    "events": [
      { "type": "BRAKE", "tPeakS": 38.76, "peakG": 0.45, "confidence": "HIGH" }
    ],
    "spans": [ { "kind": "ESTIMATED", "t0S": 0, "t1S": 46.0, "reason": "ESTIMATED_START" } ],
    "tip": { "tipId": "brake_light", "category": "BRAKING" }
  },
  "tolerance": { "ledger": 0.02, "tPeakS": 0.04, "peakG": 0.002 }
}
```

### 8.3 Golden synthetic scenarios (R1 level, `smooth-1.0.0`)

Generator, in `shared/testing`:

```
Gen(fs = 25): lists lon[], lat[], vs[]; v = 0
push(a, y = 0): lon += a; lat += y; v = max(0, v + a·G/fs); vs += v
trap(p, j, tH):   // p in g, j in m/s³; N = rint(|p|·G/j·fs), M = rint(tH·fs)
    i in 0..N−1: push(p·(i+½)/N);  M × push(p);  i in 0..N−1: push(p·(N−i−½)/N)
corner(p, j, tH): same shape on lat, with a = 0
hold(t):          rint(t·fs) × push(0)
toSpeed(vt, peak, j):
    d = vt − v; s = sign(d); P = |peak|·G; tR = P/j; dvr = P·tR
    if |d| < dvr: trap(s·sqrt(|d|·j)/G, j, 0)  else  trap(s·|peak|, j, (|d| − dvr)/P)
stop(peak, j, dwell): toSpeed(0, peak, j); v = 0; hold(dwell)
gnss: for i in 0..floor(len/fs)−1: (t = i, speed = vs[i·fs])
yaw_dps = lat·G / max(v, 1) × 180/π; vert_g = 0; quantise lon and lat with rint(x·1000)/1000

city(j, pa, pb, corner = 0.15, cj = j, rolling = false), for i in 0..7:
    toSpeed(13.9, pa, j); hold(20); corner(±corner, cj, 2) [+ for odd i, − for even i]; hold(10)
    rolling ? { toSpeed(2.0, pb, j); hold(0.5) } : stop(pb, j, 15)
```

Values were produced by a throwaway reference of §4–§5 at the constants in this document, with Good quality and consistent GNSS, so no confidence downgrade applies. DRIVE-62 must reproduce them. **If a constant changes, these goldens are regenerated in the same change.**

| ID | Inputs | Context | T (s) | R brake / accel / corner | Wobble | Events B/A/C | `S_raw` | Score |
|---|---|---|---|---|---|---|---|---|
| G-01 gentle | `city(0.6, .15, .15)` | CITY | 460.2 | 0.00 / 0.00 / 0.00 | 0.00 | 0/0/0 | 100.00 | **100** |
| G-02 ordinary | `city(1.5, .18, .20)` | CITY | 395.2 | 0.38 / 0.36 / 0.50 | 0.00 | 0/0/0 | 97.22 | **97** |
| G-03 brisk | `city(3.0, .25, .28)` | CITY | 351.7 | 2.08 / 1.74 / 1.82 | 0.00 | 0/0/0 | 87.83 | **88** |
| G-04 rough | `city(6.0, .35, .45, corner=.42, cj=6)` | CITY | 326.7 | 10.63 / 7.47 / 11.28 | 0.00 | 8/8/8 | 52.22 | **52** |
| G-05 highway | `toSpeed(30, .15, .6)`; 30 × { `hold(14)`, `corner(±.05, .4, 2)` }; `toSpeed(0, .12, .6)`; `hold(5)` | HIGHWAY | 601.6 | 0.00 / 0.00 / 0.00 | 0.00 | 0/0/0 | 100.00 | **100** |
| G-06a complete | `city(2.5, .20, .25)` | CITY | 370.2 | 1.39 / 1.06 / 1.46 | 0.00 | 0/0/0 | 91.34 | **91** |
| G-06b rolling | as G-06a, `rolling = true` | CITY | 371.8 | 2.74 / 1.97 / 1.45 | 0.00 | 0/0/0 | 86.44 | **86** |
| G-07 speed offset | Start at `v0`; 30 × { `trap(.12,1.5,1.5)`, `hold(4)`, `trap(−.12,1.5,1.5)`, `hold(4)`, `corner(±.1,1.5,1)`, `hold(2)` }. `v0` = 6, 18 m/s | CITY | 555.5 | 0.99 / 0.99 / 0.87 | 1.18 | 0/0/0 | 89.26 | **89** |
| | `v0` = 24, 32 m/s | HIGHWAY | 555.5 | 0.99 / 0.99 / 0.87 | 1.18 | 0/0/0 | 84.51 | **85** |
| G-08 needed | G-04, with its first BRAKE (`tPeak` 38.76 s) marked needed | CITY | 322.6 | 9.42 / 7.57 / 11.42 | 0.00 | 8/8/8 | 53.73 | **54** |
| G-09 speed bump | `v0` = 13.9, `hold(180)`; at sample 1500 for 6 samples `lon = .42`, `vert = .80` | CITY | 178.5 | 0.00 / 0.00 / 0.00 | 0.00 | 0/0/0 | 100.00 | **100** |
| G-10 real pulse | `v0` = 13.9, `hold(180)`; at sample 1500 for 25 samples `lon = −.42`, `vert = 0`; GNSS 13.9 up to 60 s, then 9.9 | CITY | 179.9 | 3.37 / 0.02 / 0.00 | 0.00 | 1/0/0 | 91.48 | **91** |

**Assertions on the table:**

- G-06a `S_raw` ≥ G-06b `S_raw`.
- G-07 `S_raw` is non-increasing as `v0` rises.
- G-09 has no event and G-10 has exactly one.
- G-08 raises the score over G-04.
- G-04's first events are `ACCEL` at 3.92 s (peak 0.352 g, HIGH), `CORNER` at 25.36 s (0.422 g, MEDIUM) and `BRAKE` at 38.76 s (0.452 g, HIGH).

These prove the arithmetic. They say **nothing** about how real drives score. Real-drive scale is set by A19.

**Further required scenarios** (no numeric golden yet):

| ID | Scenario | Expected |
|---|---|---|
| G-11 | One route, 4 prior runs, a run at 0.6× and one at 1.6× median moving time, both with the top score | Neither sets a record. `ineligibleReason = PACE_BAND` |
| G-12 | Mount rotations (flat, portrait, 30° tilt, upside down, 180° yaw) | §2.5 tolerances |
| G-13 | Phone lifted mid-trip | One `PHONE_MOVED` span with margins, a new epoch, no events in the span |
| G-14 | > 50 % excluded | Unscorable, no tip |
| G-15 | First 4 trips | `calibrating` `{1,3}`, `{2,3}`, `{3,3}`, then a baseline |
| G-16 | Passenger trip amid scored trips | No score. Baseline and records unaffected before and after the correction |
| G-17 | 90 s tunnel, IMU good | Dashed span, scored, events listed but unpinned |
| G-18 | 200 s GNSS loss | Labelled gap, no distance added |
| G-19 | Estimated start | Span present, distance flagged E, no events inside |
| G-20 | Route variants: same route with start latency, reverse direction, parallel road 300 m away | Match, no match, no match |
| G-21 | IMU at 25 Hz and at 10 Hz | 25 Hz scores within ±1 of 50 Hz. 10 Hz excluded |

### 8.4 25 Hz check

Run the §4–§5 stage on the same drives decimated to 25 Hz and at 50 Hz (using 50 Hz biquads) in a test. Synthetic result so far, 40 random drives with 0.015 g noise: largest score difference 0.12 points, no event-count differences. A19 repeats this on real drives (P-21).

### 8.5 Field validation (A19)

| Measure | Target | Source |
|---|---|---|
| Event precision per type | ≥ 85 % | Q20, against labelled events |
| Event recall per type | ≥ 75 % | Q20 |
| False events per 100 km, per type and placement | Report. Compare with Q20 | Sep research test matrix |
| Missed events per 100 km | Report | same |
| Score repeatability on the same commute by the same driver | Median absolute difference ≤ 5 points | New. Fails the score's purpose if much larger |
| Device and mount agreement, same drive, two phones | Median difference ≤ 5 points | New |
| Vehicle-frame error against reference | ≤ 0.03 g RMS | F-DQ-01 AC3 |
| Share of trips Unscorable with a mounted phone | ≤ 5 % | New |
| Battery %/h, screen off | Q11 | Spike 1, A19 |
| 25 Hz vs 50 Hz | Median score difference ≤ 1, event concordance ≥ 95 % | P-21 |
| Transit flagged | Q7 ≥ 50 % | A19 |

**Deliberate extreme manoeuvres happen only in controlled environments.** Ordinary-road testing must never ask a driver to create a dangerous event.

---

## 9. Provisional numbers

Every value here can change without changing the design. A change is recorded in `decisions.md` and bumps the version under §0.6. **None of Spike 1's measurements exist yet (9 Nov 2026).** The spike app runs the same confirm and reject rules, a 120 m region, a 240 s auto stop timer and 15 min of activity history, so its drives test these values directly. Where the app differs from this spec, the row says so.

| ID | Value | Used in | Depends on | Settled by |
|---|---|---|---|---|
| P-01 | Confirm: > 25 km/h for 20 s | §1.5 | iOS wake timing, GNSS warm-up | **Spike 1** walks, runs, rides and 10+ drives (Q1, Q5, Q6) |
| P-02 | Confirm: automotive ≥ medium + > 10 km/h on 3 fixes | §1.5 | `CMMotionActivity` latency and confidence on real drives | **Spike 1** (Q2, Q5) · A19 for Android |
| P-03 | Candidate timeout 180 s, early reject after 90 s below 15 km/h | §1.4, §1.5 | Time from wake to confirmation, walk-to-car time | **Spike 1** start-latency log |
| P-04 | Confirm needs a fix ≤ 50 m | §1.5 | First-fix accuracy after wake | **Spike 1** |
| P-05 | Parked region radius 120 m (PRD range 100–150 m) | §1.4 | Region-exit latency, undocumented by Apple (A3 §1.4) | **Spike 1** region-exit vs Mileon (Q2) |
| P-06 | Stop timer 240 s (range 120–480 s) | §1.6 | Trip end delay vs splits | **Spike 1** Q10 · A19 |
| P-07 | Stationary < 2 km/h, resume ≥ 7.2 km/h, `PAUSED` after 30 s | §1.6 | `liveUpdates` stationary behaviour, GNSS speed noise at rest | **Spike 1** (the app ends trips on no movement ≥ 2 m/s) |
| P-08 | Manual stop timer 900 s (F-CAP-01) | §1.6 | User behaviour | A19 (the spike app uses 1800 s for its own purposes) |
| P-09 | Resume window after a kill 600 s | §1.7 | Relaunch time after jetsam, force-quit behaviour on iOS 26/27 | **Spike 1** kill test (Q14) |
| P-10 | IMU back-off rates 25 / 10 Hz, delivery floors, 120 s idle timer, 0.03 g wake-up | §1.4, §2.7 | Delivered rate in the background, how fast a rate change takes effect | **Spike 1** Q13 · A19 |
| P-11 | GNSS gate `hAcc` ≤ 30 m | §2.3 | Real accuracy reporting on iPhone and Android | **Spike 1** fix logs · A19 |
| P-12 | GNSS gap limit 180 s, chord test 0.5–1.5 | §2.7 | Real tunnel and garage behaviour | A19 |
| P-13 | IMU gap limit 200 ms | §2.6 | Callback jitter while backgrounded | **Spike 1** Q13 |
| P-14 | Backfill: 15 min history, 5 km and 900 s limits, 60 m/s plausibility | §2.8 | `CMMotionActivity` start-time accuracy, `CMSensorRecorder` availability | **Spike 1** backfill test |
| P-15 | GNSS lag search ±1500 ms | §2.2 | Real fix latency | **Spike 1** · A19 |
| P-16 | Alignment: kept ≥ 20, ρ ≥ 0.6, excitation floor | §2.5 | Real IMU and GNSS | A19 mount matrix |
| P-17 | Phone moved: 45 °/s, 0.25 s, 20°, 1.2 g, margins −2/+3 s. Stillness 0.008 g, 1 °/s | §2.4 | Real mounts, cupholders, rough roads | A19 placement matrix |
| P-18 | Filters 3.0 and 1.5 Hz. Band 1.2 m/s³, floor multiplier 3, cap 2.4 | §3, §5.2 | Real road noise. Simulation only so far | A19 repeatability |
| P-19 | Event thresholds, min durations, 1.0 s merge. Road impact 0.40 g, 1.5×, 0.8 s | §4 | Vehicle mix, roads. The enter thresholds match the prototype fixtures | A19 labelled set (Q20) |
| P-20 | Route radius 200 m, Fréchet 150 m, length ratio 0.85–1.15, 1000 m start allowance | §7 | Start latency spread, GNSS quality | **Spike 1** latency · A19 |
| P-21 | R1 at 25 Hz | §0.3 | Real signal content above 12.5 Hz | A19 25-vs-50 Hz check (§8.5) |
| P-22 | Curves, weights, event cost 2.0, confidence weights, context factors, context cut-offs | §5 | Real score distribution | A19. Target a typical drive in the mid-80s, as the prototype assumes |
| P-23 | Baseline depth 10, band ±6, efficiency ±0.15, record cap 2, pace band 0.75–1.50 | §5, §7 | Real variance | A19 |
| P-24 | Transit and cycling rules | §1.8 | Real transit rides | **Spike 1** baseline · A19 (Q7) |
| P-25 | Archetype and change-claim rules | §3 | Real period data | Beta |

---

## Sources

Repository: [ADR-001 to ADR-010](adr/README.md), [data-model.md](data-model.md), [PRD v1](../product/PRD-v1.md), [decisions.md](../product/decisions.md), [A3 feasibility](../research/2026-11/A3-platform-feasibility.md), [September feasibility evidence](../research/2026-09/android-feasibility-evidence.md), `android-prototype-v0` scoring code (reference only: its fixed per-event weights and speeding penalties are not carried over).

External (accessed 9 Nov 2026): [Apple, `CMAttitudeReferenceFrame.xArbitraryCorrectedZVertical`](https://developer.apple.com/tutorials/data/documentation/coremotion/cmattitudereferenceframe/xarbitrarycorrectedzvertical.md).
