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
  - The app speaks O'zbekcha (lotin), Ўзбекча (кирилл), Русский and English; you choose in Settings. Alerts use the phone's offline text-to-speech engine, with a beep and vibration. Phones without an Uzbek voice say the Uzbek phrases in Cyrillic with the Russian voice.
  - The map is MapLibre with OpenFreeMap vector tiles, so street names stay upright when the map turns. Up to 500 MB of the areas you view are kept for offline use.
  - For a full offline map of an area, use **⋮ → Offline maps** to download what is on screen.
  - The **Kamera ma'lumotlari** tab lists the camera types with short descriptions.
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
