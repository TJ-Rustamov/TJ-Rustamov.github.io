# CamDect: offline speed-camera alerts for Android

GPS-only speed-camera warnings that use the **Neoline X-COP 7500s** database
(`X-COP_7500s_Baza_GPS.db`). It works fully offline, runs in the background,
and supports Android 5.0 (2014) up to current phones.

## Install
1. Open the latest **Android app (CamDect)** run in GitHub Actions and download the `camdect-apk` artifact.
2. Install `app-debug.apk` (or `app-release.apk`) on the phone. Allow "install unknown apps" when asked.
3. Open CamDect, then tap the top panel (or use **⋮ → Import camera database**) and pick `X-COP_7500s_Baza_GPS.db`.
   The app checks the file and keeps its own copy, so you only do this once.
4. Tap **Start**. Allow location (and notifications on Android 13+).
   Accept the battery prompt so alerts keep working with the screen off.

## How it works
- `core/`: plain Kotlin with no Android dependencies, unit tested on the JVM.
  - `NeolineDbParser`: decodes the binary file. 512-byte header, then 24-byte XOR-obfuscated records holding latitude, longitude, heading and speed limit.
  - `CameraIndex`: a grid index for "cameras near me" lookups.
  - `AlertEngine`: alerts only for cameras **ahead of you** that face **your direction of travel**. Alert distance grows with speed, and alerts step up at about 400 m and 150 m.
- `app/`:
  - `RadarService`: a foreground service. It reads the phone's GPS chip directly through `LocationManager`, so it needs neither Google Play services nor internet.
  - Alerts are spoken by the phone's offline text-to-speech engine (Russian or English), with a beep and vibration.
  - The map uses osmdroid with OpenStreetMap tiles. Every tile you view is cached and kept for a year, so the areas you've browsed or driven work offline.
  - For a full offline map, import an `.mbtiles` or `.sqlite` tile archive with **⋮ → Import offline map**.
  - Camera alerts never need the map or internet.

## Build
```
./gradlew :core:test                    # parser + alert logic tests
./gradlew :core:test -PrealDb=/path/X-COP_7500s_Baza_GPS.db   # also check the real file
./gradlew :app:assembleDebug            # needs the Android SDK
```
The database is not committed: it is Neoline's file. To bundle it into a local build instead of importing it, copy it to `app/src/main/assets/cameras.db`.

## Not done yet
- Neoline's type codes (byte 0, 41 values) aren't mapped yet. Points are labelled by speed limit; tap a camera to see its raw type code.
- Database updates (downloading new Neoline databases).
