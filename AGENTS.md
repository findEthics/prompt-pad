# AGENTS.md

- Android launcher: Java/XML, package `me.pompel.elauncher`, single `app` module.
- Build with `. /home/ss/toolchain/env.sh && ./gradlew testDebugUnitTest assembleRelease --no-daemon`.
- Release signing requires `PROMPTPAD_RELEASE_*` environment variables; retrieve them from Bitwarden, never commit secrets.
- Weather is local-only: `Weather.java` reads manual `label, latitude, longitude` from default preferences. It uses MET Norway only while `weather_in_peak_widget` is enabled; keep networking on its worker thread, retain cache on failures, and never add location permissions, workers, or providers.
- `WeatherTest` covers rendering, manual-coordinate validation, and expiry policy.
