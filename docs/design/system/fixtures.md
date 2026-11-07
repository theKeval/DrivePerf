# Shared prototype fixtures: `window.DP_DATA`

**Jira:** DRIVE-99 · A6 · 6 Nov 2026. **File:** [`prototypes/shared/data.js`](../../../prototypes/shared/data.js) (≈ 880 KB, generated; don't edit it by hand). **Generator:** [`tools/gen-fixtures.py`](tools/gen-fixtures.py), which works offline from `tools/cache/`; refresh the cache with `tools/fetch-routes.py`. The helpers in `DP_DATA.util` come from [`tools/data-util.js`](tools/data-util.js).

Both prototypes load the **same file**. Don't fork it: if you need a new state, add it to the generator and regenerate.

```html
<link rel="stylesheet" href="../shared/tokens.css">
<script src="../shared/tokens.js"></script>   <!-- window.DP_TOKENS (tokens.json) -->
<script src="../shared/data.js"></script>     <!-- window.DP_DATA -->
<script src="../shared/icons.js"></script>    <!-- window.DP_ICONS -->
<script src="../shared/motion.js"></script>   <!-- window.DP_MOTION -->
```

## 1. The world

- **User:** Marcus (persona E1), Oakville ON (home: Riverside Dr), works at King & Spadina, Toronto. Metric units, `en-CA`, CA storefront, timezone America/Toronto (DST ended 1 Nov 2026: offsets switch from −04:00 to −05:00). Installed **28 Sep 2026**; the Pro Preview ran 28 Sep–12 Oct; he bought founding Pro annual on 13 Oct (day 15). "Today" is **Fri 6 Nov 2026, 19:30**.
- **Cars:** `gti` (2022 VW Golf GTI, manual, petrol, Kings Red → `carmine`, default) and `ioniq` (2024 Hyundai Ioniq 5, EV, Lucid Blue → `lucid`).
- **Trips:** **30** in total. **26 fall in the four weeks Oct 9–Nov 6**, and 4 "archive" trips (Sep 28–Oct 2) are older than 30 days, so they are **locked on Free** ("locked, not lost"). They are on real GTA roads: the commute via **QEW + Gardiner** (both directions), Oakville errands (gym, Kerr Village), the Lakeshore Rd scenic run to Port Credit, a **highway trip** to Niagara-on-the-Lake (Ioniq), the **twisty drive** via Rockside, Olde Baseline and **Forks of the Credit** to Belfountain, and a **passenger** trip to Burlington.
- **Geometry and elevation are real:** © OpenStreetMap contributors (ODbL), routed with the OSRM demo server; elevation from Open-Meteo (CC BY 4.0). **Speed and G are simulated** by a kinematic model (lights, traffic waves on the Gardiner, curvature limits) with injected abrupt inputs. They are realistic, not measured. Event thresholds are placeholders (`meta.thresholdsG`; A12 owns the real ones).

## 2. Top-level keys

| Key | Type | What it is |
|---|---|---|
| `meta` | object | Version, `today`, timezone, units, `thresholdsG {brake −0.30, accel 0.28, corner 0.36}`, attribution notes |
| `user` | object | Profile, places (Home, Work, Gym), capture mode, consent, notification prefs and quiet hours, privacy zones, `founding: true` |
| `vehicles[]` | array | `{id, nickname, make, model, year, powertrain: petrol|ev, transmission, colour (car token), colourName, default, active, silhouette}` |
| `trips[]` | array | **Newest first.** §3 |
| `routes[]` | array | §4 |
| `ghost`, `ghostLatest` | object | Ghost comparisons for S-RTE-04 (§4) |
| `recaps[]` | array | Weekly recaps, newest first (§5). `recapExamples` = `emptyWeek`, `trendDown` |
| `wrapped` | object | `monthly` (October 2026), `annual` (a 2026 sample), `archetypes[]` |
| `previewRecap` | object | S-SUB-02 "Your first two weeks, decoded" content + closing lines for CA and US |
| `entitlement` | object | `current: "pro"`, `states {preview, previewEnding, rideAlong, free, pro, proMonthly}`, `prices {CA, US}`, `freeLimits`, `proFeatures[]`, `lockSheets{}` (S-SUB-03 copy per gate) |
| `health` | object | `ios.items[]`, `android.items[]`, `presets {allGood, lowPower, alwaysLost, motionOff, notificationsOff, paused, storageLow, oemKiller}`. A preset's `status` overrides items (missing = green) |
| `liveTrip` | object | In-progress trip states for S-DRV-01 / S-LIVE-*: `manual, auto, gpsSearching, lowBattery, passenger, cap8h` |
| `notifications[]` | array | S-NTF-01 templates filled with fixture values |
| `tips[]` | array | The coaching tip library (13 templates, `{place}` / `{road}` slots) |
| `provenance` | object | `metrics {name: observed|derived|estimated}` (PRD §5) and `legend` (one-line meanings) |
| `privacy` | object | Backup state and states list, data size (S-SET-07, S-PRV-03) |
| `util` | functions | §7 |

## 3. Trip object

| Field | Example / type | Notes |
|---|---|---|
| `id` | `"t20261105-0742"` | `t` + local start date and time |
| `routeKey`, `routeId` | `"commute_am"`, `"r_commute"` | `routeId` is null for unmatched, passenger or interrupted trips |
| `vehicleId`, `type` | `"gti"`, `"driver" | "passenger"` | |
| `source`, `sourceDetail` | `"auto" | "manual"`, `"Control Center"` | Auto trips have an **estimated start** |
| `start`, `end` | ISO with offset | |
| `partOfDay`, `label`, `from`, `to` | `"Thursday morning"`, `"Home → Work"` | **Use these on cards and Stories, never timestamps** |
| `context` | `"city" | "highway" | "mixed"` | Baseline context when there is no route |
| `distanceKm`, `durationS`, `movingS`, `stoppedS`, `stopCount`, `avgMovingKmh`, `maxKmh`, `maxAt {t, lat, lng}` | numbers | `maxKmh` is for **Replay, Analysis and trip detail only**, as neutral text |
| `elevGainM`, `elevLossM`, `elevMinM`, `elevMaxM` | numbers | |
| `track` | `{fields, q, rows}` | **≤ 400 rows**, time-ordered, about every 5–12 s, plus extra rows at event peaks and span edges. `fields = [t, lat, lng, kmh, lonG, latG, elevM, q]`. `t` = seconds from start. `q`: 0 observed, 1 estimated (the start segment), 2 gap (**lat, lng, kmh are null**; G is still present), 3 excluded (phone moved) |
| `events[]` | §3.1 | None in estimated or excluded spans |
| `stops[]` | `{t, durationS, lat, lng, road}` | Stops ≥ 10 s (S-RPL-03 pins) |
| `cleanest` | `{km, road, t0, t1, text}` | "13.3 km on the QEW with no abrupt inputs." |
| `quality` | `{badge, reason, phone, imuHz, imuNote, gnssCoveragePct, alignment, estimatedStartM, spans {estimated[[t0,t1]], gap[[t0,t1]], excluded[[t0,t1,label]]}, excludedMin, scoredMin, totalMin}` | S-DQ-01 content |
| `smoothness` | `{score, baseline, delta, basis: route|context, routeName, calibrating {k, of}, compareText, record {isNew, previous {score, tripId, date}}, basedOn, contributors[4], provenance, algorithm}` | `null` for passenger or Unscorable trips. `contributors[] = {id, label, band: smoother|usual|rougher, reason, eventIds}` |
| `tip` | `{id, category, text, eventId, place}` | Exactly one per scorable trip |
| `efficiency` | `{rating, label, stopsPerKm, abruptAccels, speedConsistencyCv, copy, provenance: "estimated"}` | `copy` follows the A2 I1 rule (EV vs petrol wording; never energy) |
| `story` | `{variant, frames}` | `variant ∈ default, firstDrive, calibrating, routeDiscovered, newRecord, degraded, unscorable, passenger`. `frames.f1…f7` hold the **exact copy** (title, distance line, ring line, compare, record line, cleanest, tip, quality); extra frames `fRoute`, `fNeeded`, `fDegraded`, `fUnscorable` |
| `gg` | `{fields: [latG, lonG], points[≤420], envelope {sectorDeg 15, startDeg 0, radiiG[24]}}` | The G-G cloud (2 Hz, scorable spans only). `null` when not scorable. Sector 0 = +lateral (right), counter-clockwise |
| `glyph` | `[[x, y], …]` in 0…1 | **Share-safe route shape**: ends trimmed by a random 400–800 m, simplified, not geo-referenced. Draw with `util.glyphPath` |
| `bbox` | `[minLat, minLng, maxLat, maxLng]` | For map fitting |
| `interrupted` | `{at, reason, note}` | Only on `t20261013-1742` |

### 3.1 Event
`{id, type: brake|accel|corner, t, tStart, tEnd, durationS, peakG, direction (corner: left|right), lat, lng, road, place ("Spadina & Front"), speedBeforeKmh, speedAfterKmh, confidence: High|Medium|Low, needed, neededNote?, provenance, evidence}`
`evidence = {hz: 5, t0: −10, kmh[101], lonG[101], latG[101], markers {start, peak: 0, end}}`, i.e. −10 s … +10 s around the peak (S-RPL-02 trace). G sign conventions: **lonG + = accelerating, − = braking**; **latG + = turning right**.

### 3.2 The trips

| id | Date | Part of day | Label | Car | km | min | Score | Quality | Story variant | Events | Route | Flags |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `t20261106-1210` | 2026-11-06 | Friday midday | Home → Bronte Harbour | Ioniq | 9.8 | 17 | — | Unscorable | unscorable | 0 | — | phone in a bag |
| `t20261105-1745` | 2026-11-05 | Thursday evening | Work → Home | GTI | 39 | 33 | 85 | Good | default | 2 | r_commute_home |  |
| `t20261105-0742` | 2026-11-05 | Thursday morning | Home → Work | GTI | 37.9 | 30 | 91 | Good | newRecord | 0 | r_commute | the hero trip: new route record, beating 89 from 14 Oct; no events |
| `t20261104-0755` | 2026-11-04 | Wednesday morning | Gym → Home | GTI | 3.6 | 8 | 86 | Good | default | 2 | r_cand_gym_home | route candidate |
| `t20261104-0650` | 2026-11-04 | Wednesday morning | Home → Gym | GTI | 3.9 | 9 | 84 | Good | default | 3 | r_gym | the 3rd route (Free lock) |
| `t20261103-0737` | 2026-11-03 | Tuesday morning | Home → Work | GTI | 37.9 | 32 | 86 | Good | default | 2 | r_commute | `ghostLatest` run |
| `t20261101-1020` | 2026-11-01 | Sunday morning | Home → Port Credit | GTI | 17.4 | 25 | 90 | Good | default | 1 | — | manual (Widget); Lakeshore Rd |
| `t20261031-1905` | 2026-10-31 | Saturday evening | Home → Burlington | GTI | 21.2 | 19 | — | Good | passenger | 0 | — | **passenger** |
| `t20261029-1750` | 2026-10-29 | Thursday evening | Work → Home | GTI | 39 | 34 | 88 | Good | newRecord | 2 | r_commute_home |  |
| `t20261029-0743` | 2026-10-29 | Thursday morning | Home → Work | GTI | 37.9 | 37 | 87 | Good | default | 2 | r_commute |  |
| `t20261027-1728` | 2026-10-27 | Tuesday evening | Work → Home | GTI | 39 | 34 | 83 | Limited | degraded | 3 | r_commute_home | 3 min excluded (phone moved) |
| `t20261027-0739` | 2026-10-27 | Tuesday morning | Home → Work | GTI | 37.9 | 34 | 85 | Good | default | 2 | r_commute |  |
| `t20261024-1610` | 2026-10-24 | Saturday afternoon | Niagara-on-the-Lake → Home | Ioniq | 91.2 | 73 | 87 | Good | default | 2 | — | highway, EV |
| `t20261024-0930` | 2026-10-24 | Saturday morning | Home → Niagara-on-the-Lake | Ioniq | 91.6 | 75 | 88 | Usable | default | 1 | — | IMU 25 Hz (battery saver) |
| `t20261022-0805` | 2026-10-22 | Thursday morning | Gym → Home | GTI | 3.6 | 9 | 85 | Good | default | 2 | r_cand_gym_home | low-confidence event |
| `t20261022-0655` | 2026-10-22 | Thursday morning | Home → Gym | GTI | 3.9 | 7 | 82 | Good | default | 3 | r_gym |  |
| `t20261020-1735` | 2026-10-20 | Tuesday evening | Work → Home | GTI | 39 | 34 | 84 | Good | newRecord | 3 | r_commute_home |  |
| `t20261020-0740` | 2026-10-20 | Tuesday morning | Home → Work | GTI | 37.9 | 33 | 83 | Usable | default | 3 | r_commute | **41 s GPS gap** downtown; **needed brake at Spadina & King** |
| `t20261017-1120` | 2026-10-17 | Saturday morning | Home → Kerr Village | Ioniq | 4.6 | 10 | 85 | Good | default | 2 | — | low-confidence event; EV regen tip |
| `t20261014-0744` | 2026-10-14 | Wednesday morning | Home → Work | GTI | 37.9 | 32 | 89 | Good | newRecord | 0 | r_commute | the previous record (`ghost` reference) |
| `t20261013-1742` | 2026-10-13 | Tuesday evening | Work → Mississauga | GTI | 23.7 | 22 | 82 | Good | default | 2 | — | **interrupted** |
| `t20261013-0736` | 2026-10-13 | Tuesday morning | Home → Work | GTI | 37.9 | 35 | 84 | Good | newRecord | 2 | r_commute |  |
| `t20261010-1340` | 2026-10-10 | Saturday midday | Belfountain → Home | GTI | 55.9 | 49 | 84 | Good | default | 1 | — |  |
| `t20261010-1015` | 2026-10-10 | Saturday morning | Home → Belfountain | GTI | 64 | 60 | 78 | Good | default | 5 | — | manual (Control Center); **twisty** (Rockside, Olde Baseline, Forks of the Credit) |
| `t20261009-1731` | 2026-10-09 | Friday evening | Work → Home | GTI | 39 | 33 | 80 | Good | default | 4 | r_commute_home |  |
| `t20261009-0741` | 2026-10-09 | Friday morning | Home → Work | GTI | 37.9 | 33 | 81 | Good | newRecord | 3 | r_commute |  |
| `t20261002-0745` | 2026-10-02 | Friday morning | Home → Work | GTI | 37.9 | 35 | 79 | Good | routeDiscovered | 3 | r_commute | locked on Free (> 30 days) |
| `t20260929-1724` | 2026-09-29 | Tuesday evening | Work → Home | GTI | 39 | 34 | 78 | Good | calibrating (3 of 3) | 5 | r_commute_home | locked on Free (> 30 days) |
| `t20260929-0738` | 2026-09-29 | Tuesday morning | Home → Work | GTI | 37.9 | 32 | 76 | Good | calibrating (2 of 3) | 2 | r_commute | locked on Free (> 30 days) |
| `t20260928-1842` | 2026-09-28 | Monday evening | Home → Kerr Village | GTI | 4.6 | 10 | 80 | Good | firstDrive (1 of 3) | 4 | — | locked on Free (> 30 days) |

## 4. Routes and ghost

`routes[] = {id, name, placeholder?, from, to, named, candidate?, linkedWith, active, freeLocked?, createdOn, runs[{tripId, date, partOfDay, score, durationS, quality, vehicleId, eligible}], runCount, record {score, tripId, date}, recordHistory[], trend[{date, score}], distanceKm, glyph, recordRule}`

| id | Name | Runs | Record | Free state |
|---|---|---|---|---|
| `r_commute` | The Commute (Home → Work), linked with `r_commute_home` | 10 | **91** · 5 Nov | active |
| `r_commute_home` | The Commute home (Work → Home) | 6 | 88 · 29 Oct | active |
| `r_gym` | Gym | 2 | 84 · 4 Nov | **3rd route → locked on Free** (`freeLocked`) |
| `r_cand_gym_home` | *(unnamed, "Route 4")* | 2 | — | **candidate to name** (S-RTE-03) |

`ghost` = the new record (5 Nov) vs the previous record (14 Oct), which shows a "where you gained" case. `ghostLatest` = the latest run (3 Nov) vs the record, which shows a "where you lost" case. Each has `segments[12] {road, from, to (0–1 of distance), delta, band: gained|lost|same, aligned}`, `gainedMost.text`, `lostMost.text`, and `durations` (**neutral, never a delta**).

## 5. Recaps, Wrapped and the Preview recap

- `recaps[]`: `{id, weekStart, weekEnd, deliveredAt (Sun 18:00), complete, calibrating, drives, km, durationS, durationText, avgSmoothness, delta, deltaText, downExplanation, days[7] {date, dow, drives[{tripId, km, score}]}, trend4w[{weekStart, avg}], driveOfWeek, focus {habit, line, gentler, of, meter3w[3]} (only with ≥ 5 scorable trips, F-SCO-10), notification, empty}`. The latest **complete** recap is the week of 26 Oct (5 drives, average 87, up 2). The current week (2 Nov) is `complete: false`.
- `wrapped.monthly`: October 2026. Chapters: totals, trend, drive of the month, focus, archetype (**The Metronome**), and a Pro chapter (route deep dive). `carSplit`, `mostDrivenHour`.
- `wrapped.annual`: frames 1–9 of "2026, decoded." (a sample). The comparison is marked `provenance: estimated`; the habit frame is `significant: true`.
- `previewRecap`: drives, km, smoothest trip, `routesFound 2`, `comparisons 11`, `closingLine.CA` / `.US`.

## 6. State hooks (PRD §4 states → fixture)

| State | Use |
|---|---|
| First Story / baseline | `t20260928-1842` (`story.variant = firstDrive`) |
| Calibrating k of 3 | `t20260929-0738` (2 of 3), `t20260929-1724` (3 of 3) |
| Route discovered (merge + name) | `t20261002-0745` (`frames.fRoute`) |
| New route record | `t20261105-0742` (91, beating 89 from 14 Oct) |
| Degraded / Part / Limited | `t20261027-1728` (excluded span, "Score based on 31 of 34 min") |
| GPS gap, estimated start | `t20261020-0740` (gap 1899–1940 s); every auto trip has `spans.estimated` |
| "Was it needed?" / marked needed | `t20261020-0740` event at Spadina & King (`needed: true`, `frames.fNeeded`) |
| Low-confidence event | `t20261017-1120` (acceleration, `Low`), `t20261022-0805` (braking, `Low`); `Medium`: `t20261024-0930`, `t20261104-0755` |
| IMU 25 Hz | `t20261024-0930` |
| Unscorable | `t20261106-1210` |
| Passenger | `t20261031-1905`; live: `liveTrip.states.passenger` |
| Interrupted | `t20261013-1742` |
| No events | `t20261105-0742` (the tip comes from the strongest sub-threshold stop) |
| Locked on Free (> 30 days) | the 4 archive trips: `util.isLocked(trip, 'free')` |
| 3rd route locked / candidate | `r_gym` / `r_cand_gym_home` |
| Second car locked (Free) | `vehicles[1]` with `entitlement.states.free.activeVehicleId = 'gti'` |
| Preview day 1 / 9 / 13, Ride-along | `entitlement.states.preview` (change `day`), `previewEnding`, `rideAlong` |
| Health Check presets | `health.presets.*` |
| In-drive variants | `liveTrip.states.*` |
| Empty states | Use an empty array in the shell (`trips: []`), plus `recapExamples.emptyWeek` |

## 7. `DP_DATA.util`

`trip(id)`, `route(id)`, `vehicle(id)` · `rows(trip)` → row objects with `q` as a string · `sampleAt(trip, t)` → an interpolated sample (display-only) · `isLocked(trip, ent?)`, `isRouteLocked(route, ent?)` · `signed(n)` → "+3" / "−2" / "±0" · `fmtDuration(s)`, `fmtClock(s)`, `fmtDate(iso)` ("5 Nov") · `weekOf(iso)` → recap · `glyphPath(points, w, h, pad)` → an SVG path · `trackPath(trip, w, h, pad)` → `{d, project(lat, lng)}` for a plain-canvas map (the offline state, previews), where gaps break the path.

## 8. Rules for using the fixtures

- **Share cards** may use only `glyph`, `smoothness.score`, `story.frames.f4.record`, the vehicle nickname, colour and silhouette, and `partOfDay`. Never `track`, `maxKmh`, events, G values, `start`/`end` or place labels (F-SHR-01.2).
- Live surfaces may use only `liveTrip.states.*` fields (elapsed time, distance, car, GPS state).
- When a screen needs a number that isn't here, derive it from `track` and say so in that screen's `design-spec.md`. Don't invent a value that contradicts the fixture.
