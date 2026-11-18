# Build notes

This Android starter is built with Gradle 8.11.1 by the GitHub Actions workflow.
The workflow creates a debug APK artifact for testing only.

Current app features in source:
- MapLibre map with demo style.
- Runtime location permission and last-known location.
- Device Geocoder place search.
- OSRM public demo route request and route line rendering.

Not included yet:
- Turn-by-turn voice navigation.
- Automatic rerouting and continuous location tracking.
- Offline maps or verified live traffic.
- Production map/routing services, account system, or backend integration.

Do not use this prototype as a production navigation or traffic source.
