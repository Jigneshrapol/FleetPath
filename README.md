# FleetPath — Vehicle Tracking for Android

Live GPS tracking, automatic trip recording, route replay, trip history, route planning with suggestions from your own driving history, driving insights and alerts.

Built with Kotlin and Jetpack Compose. Maps, search and routing use free OpenStreetMap services, so **no API key is needed** to build and test.

## Features

| Screen | What it does |
|---|---|
| Set up | Local profile and first vehicle (data stays on the phone) |
| Permissions | Precise location, "all the time" location, notifications, battery exemption — with live status |
| Live | Vehicle on the map with heading, speed, speed-limit badge, GPS accuracy, address, today's distance/drive time/trips, vehicle switcher, map style, share location |
| Trip recording | Timer, distance, average/max speed, idle time, planned route overlay, destination distance, break reminder after 2 h, stop & save |
| Trips | Day / week / month history with totals, filters (manual, auto, with alerts), route thumbnails, CSV export |
| Trip detail | Route map with over-speed segments in orange, stop markers, **route replay** (1×–8×) with scrubber, speed profile chart, event timeline, GPX export, delete |
| Plan route | Place search, up to 3 driving routes, avoid tolls, frequent destinations, **best time to leave** learned from your past trips, start trip on route, hand-off to Google Maps for turn-by-turn |
| Insights | Week / month / year distance chart, change vs previous period, driving score, over-speeding, harsh braking, idling, moving/idle/parked split, most visited places |
| Alerts | Over-speed (10 s above limit), geofence enter/exit, trip started/saved — with push notifications |
| Settings | Multiple vehicles (with mileage for fuel estimates), auto-detect trips, update interval, battery saver, speed limit, geofences, history retention, export, delete history, sign out |

## Get the APK (no Android Studio needed)

**Shortcut:** double-click `push-to-github.bat` (Windows) or run `./push-to-github.sh` (macOS/Linux). It commits the code, pushes it to GitHub and opens the build page. Or do it by hand:

1. Create an empty repository on GitHub, then push this folder:
   ```bash
   cd FleetPath
   git init
   git add .
   git commit -m "FleetPath Android app"
   git branch -M main
   git remote add origin https://github.com/<your-username>/FleetPath.git
   git push -u origin main
   ```
2. Open the repository's **Actions** tab. The **Build APK** workflow starts automatically and takes about 5 minutes.
3. Open the finished run and download **FleetPath-debug-apk** under *Artifacts*. Unzip it to get `app-debug.apk`.
4. Copy the APK to your phone and open it. Allow "Install unknown apps" for your file manager or browser when Android asks.

Every build is signed with the same key (`app/debug.keystore`), so new builds install over old ones without losing your trips.

## Or build with Android Studio

Open the folder in Android Studio (Koala or newer), let Gradle sync, enable USB debugging on your phone and press **Run**.
Command line: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

## Testing tips

- On first launch, allow **Precise location**, then **Allow all the time** so trips keep recording with the screen off.
- **Manual trip:** Live → *Start trip*, drive (or ride along), then *Stop & save trip*. Trips shorter than 100 m are discarded.
- **Auto-detect:** Settings → *Auto-detect trips*. A trip starts after 3 GPS fixes above 10 km/h and ends after 5 minutes parked. Walking is too slow to trigger it.
- **Over-speed alert:** set the limit low (e.g. 20 km/h) in Settings, then drive above it for 10 seconds.
- **Geofence:** Settings → *Add geofence at current location*, then drive out of the radius.
- On Xiaomi, Oppo, Vivo, Realme and Samsung phones also set the app's battery mode to *No restrictions*, or the system may stop background tracking.

## Architecture

```
app/src/main/java/com/fleetpath/app
├── FleetPathApp.kt          Application: database, settings, tracking engine, map config
├── MainActivity.kt          Compose host, opens a trip from a notification
├── data/                    Room (vehicles, trips, GPS points, alerts, geofences) + DataStore settings
├── tracking/
│   ├── TrackingManager.kt   Live position, trip recording, auto start/stop, over-speed, geofences
│   ├── LocationService.kt   Foreground service (type "location") using Fused Location Provider
│   └── Notifications.kt     Tracking and alert notifications
├── analysis/                Trip analysis (distance, stops, over-speed, harsh braking, score),
│                            insights, frequent places, departure-time suggestions
├── routing/RoutingClient.kt Nominatim search + OSRM routes
├── export/Exporter.kt       GPX and CSV export through FileProvider
└── ui/                      Theme, components (OSM map, charts) and the 10 screens
```

Stack: Kotlin 2.0, Jetpack Compose (Material 3), Navigation Compose, Room, DataStore, Google Play Services Location, osmdroid. Min SDK 26 (Android 8), target SDK 34.

## Limits of this version

- Everything is stored on the phone; there is no server, real login or multi-device fleet view yet.
- Route times come from the public OSRM demo server and don't include live traffic. Nominatim and OSRM demo servers are for light use — switch to your own servers or a paid provider (Google, Mapbox, HERE) before publishing.
- The phone itself is the tracker. Hardware GPS devices in vehicles would need a backend to receive their data.

## Credits

Map data © OpenStreetMap contributors. Routing by OSRM. Geocoding by Nominatim.
