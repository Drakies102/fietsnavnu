# Architecture — FietsRouten React Native

React Native owns the whole UI and app logic. Kotlin shrinks to a thin set of native modules for what JS can't do accurately. The self-hosted backend and public tile/overlay services are reused unchanged.

```
┌─────────────────────────────────────────────────────────────┐
│  React Native UI            8 screens · RN Navigation        │
│                             @gorhom/bottom-sheet · Reanimated │
│                             Zustand store (ex-MapViewModel)   │
├─────────────────────────────────────────────────────────────┤
│  Cross-platform JS          MapLibre RN (map render)         │
│                             NavigationEngine.ts (ported)     │
│                             axios + React Query · MMKV       │
├─────────────────────────────────────────────────────────────┤
│  Kotlin native (THIN)       LocationModule (GPS/speed/bearing)│
│                             NavForegroundService (bg location)│
│                             TTS / keep-awake / permissions   │
├─────────────────────────────────────────────────────────────┤
│  Backend (REUSED, unchanged)                                 │
│    GraphHopper · Nominatim · POI service                     │
│    OpenFreeMap tiles · Overpass overlays                     │
└─────────────────────────────────────────────────────────────┘
```

## Existing backend contracts (do not change)
From `fietsnavnu/app/app/src/main/kotlin/com/fietsrouten/Config.kt`:

| Service | Base URL | Used for |
|---|---|---|
| GraphHopper | `https://fietsnav-routing.learndelingo.nl` | routing |
| Nominatim | `https://fietsnav-geocoding.learndelingo.nl` | geocoding |
| Overpass | `https://overpass-api.de/` | cycling-route / knooppunten overlays |
| POI service | `https://fietsnav-pois.learndelingo.nl` | food/drink along route |
| Map style (light) | `https://tiles.openfreemap.org/styles/bright` | base map |
| Map style (dark) | `https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json` | dark base map |

### Endpoints
- **Nominatim search:** `GET /search?q={query}&format=json&limit=…` → `NominatimResult[]` (`place_id`, `display_name`, `lat`, `lon`).
- **Nominatim reverse:** `GET /reverse?lat=&lon=&format=json`. Shorten `display_name` to first 2 comma parts.
- **GraphHopper route:** `POST /route` with body
  ```json
  { "points": [[lon,lat],[lon,lat]], "profile": "bike",
    "points_encoded": false, "locale": "nl",
    "instructions": true, "elevation": true }
  ```
  ⚠ GraphHopper uses **[longitude, latitude]** order. Response `paths[0]`: `points.coordinates` (`[lon,lat,ele]`), `distance` (m), `time` (ms), `instructions[]` (`text`, `distance`, `time`, `sign`, `interval:[i,j]`).
  Profiles: `bike`, `mtb`, `racingbike`.
- **POI:** `GET /api/pois?south=&west=&north=&east=&types=cafe,restaurant,fast_food,bar,bakery,ice_cream,pub` → GeoJSON FeatureCollection. Filter client-side to ≤500 m from the route line (haversine).
- **Overpass:** POST QL queries for LF-routes / knooppunten in viewport (see `OverpassRepository`).

## Kotlin → React Native migration map

| Current (Kotlin) | Becomes (React Native) |
|---|---|
| `MapFragment` / `activity_main.xml` | RN Navigation screens + native bottom-tab bar |
| `MapViewModel` (`StateFlow`) | Zustand store + React Query for async |
| MapLibre Android SDK | `@maplibre/maplibre-react-native` (same style URLs) |
| Retrofit APIs (`GraphHopperApi`, `NominatimApi`, `PoiApi`, `OverpassApi`) | `axios` clients, identical endpoints & models |
| Gson `data class` models (`Models.kt`) | TS interfaces (1:1 — see below) |
| `NavigationEngine.kt` (snap / off-route / ETA) | `NavigationEngine.ts` — pure JS port, unit-tested |
| `ElevationChartView` (custom Canvas view) | RN component (SVG bars or `react-native-svg`) |
| `RouteRepository` / `PoiServiceRepository` / `OverpassRepository` | TS service modules |
| **FusedLocation + speed + bearing** | **STAYS NATIVE** — Kotlin `LocationModule` (TurboModule) |
| **Background location during nav** | **STAYS NATIVE** — Kotlin foreground service |
| `TextToSpeech` voice guidance | `react-native-tts` (thin native fallback if needed) |
| SharedPreferences | `react-native-mmkv` |
| Material DayNight theme | RN theme context (light=Groen, dark=Ink) — see `DESIGN_TOKENS.md` |

### TS models (port `Models.kt` 1:1)
```ts
interface NominatimResult { place_id: number; display_name: string; lat: string; lon: string }
interface RouteInstruction { text: string; distance: number; time: number; sign: number; interval: [number, number] }
interface RouteResult {
  coordinates: number[][];        // [lon, lat, ele?]
  distanceMeters: number;
  durationMs: number;
  instructions: RouteInstruction[];
  elevationProfile: number[];
}
interface Poi { id: number; name: string; amenity: string; lat: number; lon: number }
interface Knooppunt { id: number; lat: number; lon: number; ref: string }
interface NavigationSession {
  currentInstructionIndex: number; currentInstruction: RouteInstruction;
  nextInstruction: RouteInstruction | null;
  distanceToTurnMeters: number; remainingDistanceMeters: number; remainingDurationMs: number;
  isOffRoute: boolean; snappedLatLng: { lat: number; lon: number }; bearing: number;
}
```

### `NavigationEngine.ts` — port faithfully
The Kotlin `NavigationEngine` is the trickiest logic. Port it exactly:
- **Snap** the user point to the nearest route segment (`snapToSegment`, projection with `t` clamped 0–1).
- **findNearestSegment** across all segments → `{index, snappedPoint, snapDistance}`.
- **Current instruction** = the instruction whose `interval[0..1]` contains `nearestIndex` (else last).
- **distanceToTurn** / **remainingDistance** via `distanceAlongRoute` (haversine sum from snapped point).
- **ETA:** if GPS `speed > 0.5 m/s` → `remaining / speed`; else proportional `durationMs * fraction`.
- **Bearing:** only update from GPS when moving (`speed > 0.5` & `hasBearing`), else hold last.
- **Off-route:** `snapDistance > 30 m`.
Add unit tests with a sample GraphHopper response.

## Native modules (the only Kotlin you maintain)

### 1. `LocationModule` (TurboModule)
Wraps `FusedLocationProviderClient`. Emits `{ lat, lon, speed (m/s), bearing, accuracy, timestamp }` via an event emitter at ~1 Hz. Methods: `start(intervalMs)`, `stop()`, `getLastKnown()`, `requestPermissions()`. Speed and bearing must come from the fused provider (not derived in JS) for accurate ETA/heading.

### 2. `NavForegroundService`
A foreground service (with notification) that keeps location updates alive when the app is backgrounded/screen-locked during active navigation. Start on "Start navigatie", stop on "Stop". Handles the Android 10+ background-location + foreground-service-location permissions. iOS equivalent: background location mode + `CLLocationManager` allowsBackgroundLocationUpdates.

### 3. TTS / keep-awake / permissions
Prefer `react-native-tts` and `react-native-keep-awake`; only drop to a native bridge if a gap appears. Centralize runtime permission requests.

## Libraries
- `react-native-navigation` (Wix) — native stack + bottom tabs.
- `@maplibre/maplibre-react-native` — map, style URLs, GeoJSON sources/layers for route, nodes, POIs, puck.
- `@gorhom/bottom-sheet` + `react-native-reanimated` + `react-native-gesture-handler` — sheets & gestures.
- `zustand` — state; `@tanstack/react-query` — async; `axios` — HTTP.
- `react-native-mmkv` — persistence (saved rides, settings, theme).
- `react-native-svg` — icons, elevation chart, markers.
- `react-native-tts`, `react-native-keep-awake`.
- Fonts via `react-native-google-fonts` or bundled TTFs.

## Milestones
| # | Milestone | Est. | Notes |
|---|---|---|---|
| M0 | Foundation | ~1 wk | RN project, RN Navigation shell, CI, theme tokens, TurboModule scaffolding |
| M1 | Map & location | ~1–2 wk | MapLibre + OpenFreeMap style, Kotlin `LocationModule`, live puck & recenter |
| M2 | Search & routing | ~1–2 wk | Nominatim autocomplete, from/to, GraphHopper routing, route drawn |
| M3 | Route summary | ~1 wk | Profiles, distance/time, elevation chart, bottom sheet |
| M4 | **Turn-by-turn nav** | ~2 wk | `NavigationEngine.ts`, foreground service + bg location, off-route recalc, TTS — the hard one |
| M5 | Knooppunten planner | ~1–2 wk | bundled geojson, tap-to-add, leg distances, multi-waypoint route |
| M6 | Layers & POIs | ~1 wk | Overpass overlays, POI along route, toggles, dark map |
| M7 | Saved rides & profile | ~1 wk | MMKV persistence, history, stats, settings — NEW |
| M8 | Polish, offline & store | ~1–2 wk | offline tiles, battery/perf, iOS parity, Play Store + TestFlight |

**~11–14 weeks** to feature parity + iOS · **2 platforms from one codebase** · **~3 native modules** is the only Kotlin you keep maintaining.

## Recommended folder structure
```
src/
  screens/        MapScreen, SearchScreen, RouteSummary, Knooppunten,
                  NavigationScreen, RidesScreen, ProfileScreen
  components/     SearchCard, ProfileSelector, ElevationChart, NodeChip,
                  BottomSheetShell, Fab, SwitchRow, InstructionBar, NavBar
  map/            MapView, layers (route, nodes, pois, puck), styleUrls
  navigation/     engine.ts (ported), engine.test.ts, useNavigation.ts
  services/       graphhopper.ts, nominatim.ts, pois.ts, overpass.ts
  store/          useAppStore.ts (zustand), persistence (mmkv)
  theme/          tokens.ts (light/dark), ThemeProvider, useTheme
  native/         LocationModule.ts (JS side of TurboModule)
  models/         types.ts
android/app/src/main/java/com/fietsrouten/
  LocationModule.kt, NavForegroundService.kt, *Package.kt
```
