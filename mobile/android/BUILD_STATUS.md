# Build notes

The Android app is built with Gradle 8.11.1 by GitHub Actions.
The workflow creates a debug APK artifact for device testing.

## Implemented in 0.5.0
- Arabic-first Jetpack Compose interface.
- MapLibre map using a development-only demo style.
- Runtime location permission and last-known location.
- Foreground GPS location updates while the app is open (5-second requested interval).
- Device Geocoder place search.
- OSRM public demo route request and route line rendering.
- Arabic text-to-speech route summary when a route is calculated.
- Basic off-route detection using GPS accuracy and distance from sampled route points.
- Automatic route recalculation after two consecutive off-route GPS samples.

## Not implemented yet
- Turn-by-turn maneuver detection and spoken prompts.
- Offline maps or verified live traffic.
- Production map/routing services, account system, backend integration, hazard-report moderation.
- Physical-device QA across Iraqi cities and network conditions.

Off-route detection is a prototype heuristic (120 m threshold, two accurate GPS samples); it is not safety-certified navigation. The current map tiles and routing endpoint are development/demo services. Do not use this prototype as a production navigation or traffic source. A successful CI build is not proof of field reliability.
