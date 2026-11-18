# IRAQ WAZ — Architecture decision record

## Product boundary
Build an independent Iraqi navigation and driver-community product. The Waze APK analysis is used only as a feature-discovery reference (map interactions, location callbacks, route/ETA events, traffic updates, search, fuel, parking, reports, TTS, account and inbox concepts). It does not provide Waze source code, server data, credentials, live traffic, or permission to copy protected assets.

## Platform decision
- Android first: Kotlin + Jetpack Compose.
- Map renderer: MapLibre Native Android (`android-sdk-opengl`) to keep an OpenGL ES renderer option for device compatibility.
- Location: Android runtime permissions + Fused Location Provider for foreground location; active navigation will later require a correctly declared foreground service and persistent notification.
- Local persistence: Room (planned), DataStore (planned).
- Backend: NestJS modular monolith (planned).
- Data: PostgreSQL + PostGIS; Redis for short-lived cache/pub-sub (planned).
- Routing: evaluate OSRM, Valhalla and GraphHopper against Iraq OSM extracts before selecting one.
- Search/geocoding: adapter boundary; self-host Nominatim or a provider with suitable production terms. Do not use the public Nominatim endpoint for production-scale traffic.

## Logical modules
- `map`: map view, camera, style and attribution.
- `location`: permissions, last-known position, foreground location stream.
- `search`: geocoding, suggestions, Arabic normalization and place details.
- `routing`: route request/response, alternatives, restrictions and rerouting.
- `navigation`: trip state, maneuver queue, ETA and voice prompts.
- `reports`: hazards/incidents/closures, votes, expiry and moderation.
- `traffic`: verified community reports and licensed external traffic sources only.
- `places`: fuel, parking and service POIs with source and observed-at timestamps.
- `account`: guest-first profile, optional sign-in and privacy settings.
- `admin`: moderation, audit trail and source-quality tools.

## Data trust levels
1. `official`: supplied by a competent public authority and recorded with source + update time.
2. `provider`: supplied by a licensed third-party provider and recorded with provider + update time.
3. `community_pending`: unverified user report; not shown as confirmed.
4. `community_verified`: passed confirmation/moderation policy; still displays freshness/expiry.
5. `stale`: expired or too old; hidden from active navigation layers.

Never infer a live traffic speed from static OSM road data. Never publish sensitive or non-public security/operational locations. Check local law before enabling any sensitive report category.

## Current implementation status
- Android app shell: source created; not yet built or device-tested in this environment.
- MapLibre: style URL is configurable through Android resources; current URL is demo-only.
- Location: permission request and last-known-location centering only; not continuous tracking.
- Database: initial schema exists; migrations and RLS/authorization policies still need review and tests.
- Backend, routing, search, live traffic, offline navigation and admin: not implemented.

## Release gates
- Validate road coverage and one-way/turn-restriction correctness in Ramadi, Fallujah and intercity routes.
- Benchmark at least two routing engines using the same test routes.
- Confirm map/tile provider license, attribution, quotas, caching and offline rights.
- Test location permission denial, approximate location, GPS loss, network loss and battery usage.
- Verify report expiry, deduplication, vote uniqueness, moderation and anti-spam.
- Security review API, admin roles, data retention, account deletion and location consent.
