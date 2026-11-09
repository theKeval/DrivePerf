# iOS capture spike (DRIVE-36, Spike 1)

Throwaway SwiftUI app that tests iOS background auto-trip capture on a real iPhone, side by side
with Mileon. It is not product code. Design source: `docs/research/2026-11/A3-platform-feasibility.md`
§1.3–1.6. Targets being tested: `docs/product/PRD-v1.md` §2.4.

## Layout

| Path | What it is |
|---|---|
| `CaptureCore/` | Pure Swift package: confirmation rule, trip end, metrics, export. Tested on the Mac with `swift test` |
| `App/` | App target: capture engine, file storage, debug UI |
| `Widgets/` | Extension: Control Center control and Live Activity |
| `Shared/` | Compiled into both targets: activity attributes and the `LiveActivityIntent` |
| `Config/` | Info.plists and xcconfig. The Team ID lives only in the gitignored `Local.xcconfig` |

Bundle IDs are `com.thekeval.spike.capture` and `.widgets`, deliberately outside `com.driveperf.*` so
a free Personal Team never claims the product IDs. The app uses no App Groups or entitlements.

## Privacy rules (the repo is public, ADR-008)

- Exported trips contain real coordinates. They are named `RAW-DO-NOT-COMMIT-*`, and `exports/`,
  `pulled/` and `*.gpx` are gitignored here. Never commit them.
- Any later fixture must be privacy-trimmed and carry the `# privacy-trimmed` header.
- `Documents/summary.json` on the phone holds only times, distances, counts and battery. It has no
  coordinate fields by construction, and a unit test enforces that. Only this file feeds the results doc.
- The debug UI shows no coordinates or map, but check any screenshot before sharing it.

## Build and install

One-time setup: sign in to Xcode with an Apple ID (Settings > Accounts), then copy
`Config/Local.xcconfig.example` to `Config/Local.xcconfig` and set the Personal Team ID.

```bash
xcrun devicectl list devices
```

```bash
xcodebuild -project CaptureSpike.xcodeproj -scheme CaptureSpike -destination "id=$UDID" -derivedDataPath build -allowProvisioningUpdates build
```

```bash
xcrun devicectl device install app --device "$UDID" build/Build/Products/Debug-iphoneos/CaptureSpike.app
```

Install with `devicectl` and launch from the Home Screen. With the Xcode debugger attached the app
is never suspended, so background behaviour would not be real.

**Personal Team profiles expire after 7 days**, and then the app will not launch. The Status tab shows
each install and its expiry, and the app notifies about 24 hours before. Reinstall with the commands
above. A period with an expired profile is not a missed trip.

## Pull the coordinate-free summary

```bash
xcrun devicectl device copy from --device "$UDID" --domain-type appDataContainer --domain-identifier com.thekeval.spike.capture --source Documents/summary.json --destination pulled/summary.json
```

## Field-tuning switch

Status > Experiment > "Legacy GPS keep-alive" (default on) also runs `startUpdatingLocation` beside
`liveUpdates`. Each trip records fixes per stream. Run a few drives with it off to see whether
`liveUpdates` alone keeps a background-woken app alive.
