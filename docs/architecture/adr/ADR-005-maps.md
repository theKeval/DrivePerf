# ADR-005: Maps — MapKit on iOS, Google Maps SDK (Maps Compose) on Android

- **Status:** Proposed (for Gate 4)
- **Date:** 2026-11-06
- **Deciders:** Keval Langalia (PO); A10
- **Related:** PRD F-RPL-01…10 (Replay, P0), F-SHR-03 (opt-in map card, P1), F-RTE-01 (route naming), PRD OQ-15; design system `components.md` ("Map (P1, opt-in): stylised basemap, labels off, trimmed ends")

## Context and problem statement

Trip Replay with event pins is P0 on both platforms. Routes need a map; the opt-in map share card (P1) needs a stylised static image. Maps must look native, support the dark "Night Drive" style, cost nothing at our scale, and never send trip data to a server we don't need.

## Decision drivers

Native look and gestures · cost at 20k+ DAU · dark styling · privacy (no route data to third parties beyond tile fetches) · effort (two platforms, one developer).

## Considered options

1. **MapKit (iOS) + Google Maps SDK for Android via Maps Compose.**
2. **MapLibre Native on both** with a vector-tile provider (MapTiler, Stadia, Protomaps/self-hosted PMTiles, OpenFreeMap).
3. **Google Maps SDK on both.**
4. **Mapbox on both.**

## Decision outcome

**Chosen option: 1.** Both SDKs are free for native mobile map loads (Google lists the Maps SDK for Android/iOS as "Unlimited" free; MapKit is included with the Apple Developer Program), both look and behave native, and neither adds a vendor account to manage beyond the Google Cloud project we already have.

### Details

- **iOS:** SwiftUI `Map` with `MapPolyline`/annotations for route, speed-neutral line and event pins (shapes per A6: square braking, circle acceleration, diamond cornering). Dark style via `.mapStyle(.standard(elevation: .flat, pointsOfInterest: .excludingAll))` in dark appearance.
- **Android:** Maps Compose (`com.google.maps.android:maps-compose`) with a **cloud-styled Map ID** for the Night Drive dark style (POIs off). API key restricted to the app's package + signing-certificate SHA-1, stored outside the public repo ([ADR-008](ADR-008-ci-cd-and-environments.md)).
- **Polyline rendering:** the shared `presentation` layer produces simplified, screen-ready polylines and pin positions (Douglas–Peucker per zoom bucket) so both platforms draw identical routes.
- **Elevation (F-RPL-02):** from device GNSS altitude in the trip file. **No paid Elevation, Directions, Roads or Places APIs.**
- **Route names (F-RTE-01):** the user's own names first; suggestions from the OS geocoders (`CLGeocoder`, Android `Geocoder`) with a disclosure in the Privacy Centre, never the paid Geocoding API.
- **Map share card (F-SHR-03, P1):** rendered on device: iOS `MKMapSnapshotter`, Android `GoogleMap.snapshot()` with the label-free Map ID, ends trimmed and privacy zones removed (F-SHR-02, F-PRV-07). **Before shipping**, A20/counsel confirm that sharing these images off-platform is allowed by Apple's and Google's map terms with attribution. If not, the card draws the trimmed route on a plain brand background with no basemap. Static Maps API is not used (it would upload the route to Google).
- **Web:** no maps in v1.
- **Revisit (v1.1+):** MapLibre + self-hosted Protomaps tiles if we need one identical style on both platforms, offline maps, or if Google's terms block the share card.

### Consequences

- Good: $0 map cost at any DAU we expect; native gestures, accessibility and dark mode.
- Good: the only data sent is tile requests to Apple/Google, as with any map app; disclosed in the privacy policy.
- Bad: two map APIs with slightly different styling; accepted (the route and pins are ours and identical).
- Bad: Google Maps on Android requires a billing-enabled Google Cloud project (the same project as Firebase once on Blaze); keep the key restricted.

### Confirmation

Replay renders a 2-hour trip at 60 fps on reference devices (PRD A-2) with pins; billing reports show $0 Maps usage during the beta.

## Pros and cons of the other options

- **MapLibre on both:** one style and offline control; tiles cost money (or self-hosting effort) and gestures/labels feel less native, especially on iOS next to Liquid Glass.
- **Google Maps on both:** one API, but MapKit is the native iOS look and is free without an extra SDK in the binary.
- **Mapbox:** strong styling; per-MAU pricing beyond its free tier and another vendor account.

## More information

Sources (accessed 6 Nov 2026): Google Maps Platform pricing — Maps SDK "Unlimited" free; Static Maps 10,000 free then US$2.00/1,000; Dynamic Maps 10,000 free then US$7.00/1,000; 2D Map Tiles 100,000 free (https://developers.google.com/maps/billing-and-pricing/pricing).
