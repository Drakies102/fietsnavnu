# Handoff: FietsRouten — React Native rebuild

## Overview
FietsRouten is a Dutch bike-navigation app currently shipping as a **native Android app in Kotlin**. This handoff covers rebuilding it as a **cross-platform React Native app** where:

- **React Native** owns the entire UI and app logic (all 8 screens).
- **Kotlin shrinks to a thin native layer** — only the things JS can't do accurately: fused GPS location, speed, heading, and background location during navigation.
- **React Native Navigation** (Wix `react-native-navigation`) provides native navigation (native stack + bottom tabs).
- The **self-hosted backend is reused unchanged** (GraphHopper routing, Nominatim geocoding, POI service) plus OpenFreeMap tiles and Overpass overlays.

The redesign refreshes the look into a modern Dutch-cycling aesthetic with **two themes shipped as a light/dark toggle**:
- **Groen** = the **light** theme (warm cream, fresh cycle-green, Dutch amber).
- **Ink** = the **dark** theme (map-first black, electric cobalt, signal-lime).

## About the design files
The files in this bundle are **design references created in HTML** (`FietsRouten Redesign.dc.html`) — a prototype showing the intended look, layout, and behavior. **They are not production code to copy.** The task is to **recreate these designs in React Native** using the target stack's real components and libraries (see `ARCHITECTURE.md`), wiring them to the existing backend endpoints.

The current Kotlin app in `fietsnavnu/app/` is the **source of truth for behavior** — its data models, API contracts, and the `NavigationEngine` algorithm must be ported faithfully. `ARCHITECTURE.md` maps every existing Kotlin piece to its React Native replacement.

## Fidelity
**High-fidelity.** Colors, typography, spacing, and radii are final and specified exactly in `DESIGN_TOKENS.md`. Recreate the UI to match, using React Native components (`View`, `Pressable`, `@gorhom/bottom-sheet`, MapLibre, etc.). The schematic maps in the mock are placeholders for live MapLibre tiles.

## Screens / views
The app has **8 screens** plus a persistent **bottom tab bar** (Kaart · Ritten · Profiel). All measurements below are for a 360–390 px logical-width phone. Exact hex/typography live in `DESIGN_TOKENS.md`; every value referenced as a token (e.g. `surface`, `primary`) resolves per active theme.

### 1. Home / Map (`MapScreen`)
- **Purpose:** Default screen. Full-bleed map; start a trip by searching a destination or planning knooppunten.
- **Layout:** Full-screen MapLibre map. Floating search card pinned top (12 px insets, below status bar). FAB column pinned right (layers, recenter). Draggable bottom sheet ("Recente ritten") at rest height ~120 px.
- **Components:**
  - **Search card** — `surface` bg, radius 19–20, shadow. Left: magnifier icon (`primary` stroke). Placeholder text "Waar wil je heen?" (light) / "Zoek bestemming" (dark), `textMuted`, 14 px/500.
  - **Quick chips row** — pills below search: "🏠 Thuis", "💼 Werk" (surface pills), "Knooppunten" (filled `ink`/`primary`). 11.5 px/600.
  - **FABs** — 42×42, radius 12–14. Layers (rotated-square/diamond outline icon), Recenter (filled `primary`, ring-dot icon, glow in dark).
  - **Bottom sheet** — 36×4 grab handle. Section label "RECENTE RITTEN" (mono, 10–11 px, `textMuted`, uppercase, letter-spacing .08em). One recent-trip row: 40×40 rounded icon tile, title 14/700, subtitle "18,4 km · via LF-routes" 11.5 px, right-aligned duration "1u 12m" in mono `primary`.

### 2. Search / suggestions (`SearchScreen`)
- **Purpose:** Enter From/To, pick an address from Nominatim autocomplete.
- **Layout:** From/To card top (below status bar). "VOORSTELLEN" label. Suggestion list. Native keyboard occupies bottom (do not hand-draw it — it's the OS keyboard).
- **Components:**
  - **From/To fields** — one `surface` card, radius 18. From row: green ring dot + "Mijn locatie" 14/600 + trailing recenter/target icon. Hairline divider (inset 23 px left). To row: `primary`/amber square dot + typed text with caret.
  - **Suggestion rows** — 34×34 rounded icon tile (dot), title 13.5/600, subtitle 11 px `textMuted`, right distance in mono. Rows ~44 px tall min (hit target).
- **Data:** debounced (~300 ms) `GET {NOMINATIM}/search?q=&format=json&limit=…`; "Mijn locatie" injected as first option from device GPS.

### 3. Route summary (`RouteSummaryScreen` — bottom sheet over map)
- **Purpose:** Review computed route, pick a bike profile, start navigation.
- **Layout:** Map top with route line drawn (origin amber/cobalt dot, destination green/lime dot). Back pill top-left. Bottom sheet with the details.
- **Components:**
  - **Profile segmented control** — 3 segments in a `surfaceSunken` track, radius pill/10: "🚲 Fiets" (active = filled `primary`, white text), "⛰️ MTB", "🏁 Race" (inactive `textMuted`). 12 px/600–700.
  - **Stats row** — Distance "18,4 km" and Time "1:12 u" as 26 px display/mono-600 numerals with muted unit suffixes; right-aligned elevation "↗ 46 m" in accent + "hoogte" caption.
  - **Elevation chart** — 14-bar mini histogram, height ~44 px, bars radius 2 top; peak bars use `primary`/`cobalt`, rest muted. (Ports `ElevationChartView`.)
  - **Start button** — full-width, radius pill/12, `primary` fill, white 15/700, glow shadow. "Start navigatie".

### 4. Knooppunten planner (`KnooppuntenScreen`)
- **Purpose:** Plan a route by tapping numbered junction nodes (fietsknooppunten) on the map.
- **Layout:** Map with node markers. Instruction banner top ("Tik knooppunten aan om je route te plannen"). Bottom sheet with the ordered node chain.
- **Components:**
  - **Node markers** — 22×22 circles, 2 px `surface` border, mono number. Selected = filled `primary`/amber; start/last = `ink`/lime; tappable-unselected = outline. Dashed connector line between chosen nodes.
  - **Chain row** — node circles interleaved with "— 2,1 km —" leg labels (mono, `textMuted`), wrapping.
  - **Total row** — "Totaal" label + big distance in `primary` (19/700).
  - **Actions** — "Wis" (outline pill) + "Bereken route" (filled `primary` pill, flex-1).
- **Data:** nodes come from bundled `assets/knoopunten.geojson`; leg distances via haversine (already in `MapViewModel.getLegDistances`); final route via GraphHopper with all node coords as waypoints.

### 5. Turn-by-turn navigation (`NavigationScreen`)
- **Purpose:** Active guidance. **This is the screen that needs the native layer.**
- **Layout:** Map fills screen, camera follows puck (tilted/heading-up). Instruction bar pinned top; dark control bar pinned bottom. Search UI hidden.
- **Components:**
  - **Instruction bar** — filled `primaryDark` (light theme) / `surface` (dark theme), radius 16–20. Left: large turn arrow (maneuver icon by `sign`). Center: big distance-to-turn "200 m" (30 px display/700) + street line "Ga rechtsaf — Maliebaan" (`onPrimary`/accent, 13/600). Right: small "dan" + next-maneuver icon.
  - **Bottom control bar** — dark (`navBar`). Left: remaining time "1:04" (19/700) + "aankomst 10:45 · 15 km" caption. Center: **speed badge** (rounded, `speedBadge` bg — teal in light, lime in dark) "18 / km/u". Right: 44×44 red round Stop button (white square glyph).
  - **Puck** — heading arrow, `primary`/cobalt, white ring, glow.
- **Behavior:** driven by `NavigationEngine` (ported to TS) fed by native location updates; off-route → recalculate; TTS announces upcoming maneuvers.

### 6. Layers & POI (`LayersSheet` over map)
- **Purpose:** Toggle map overlays and points of interest.
- **Layout:** Bottom sheet titled "Kaartlagen" over the map (POI markers visible on map behind).
- **Components:** toggle rows, each = 34×34 icon tile + title 13.5/600 + subtitle 11 px `textMuted` + iOS-style **switch** (42×24, `primary` when on). Rows: Knooppunten (on), LF-routes (on), Eten & drinken (off), and a 4th (Donkere kaart in light / Satelliet in dark). POI markers on map = 20×20 colored circles with a food emoji.
- **Data:** Overpass for LF/knooppunten overlays; POI service `GET {POI}/api/pois?south=&west=&north=&east=&types=cafe,restaurant,…`, filtered client-side to within 500 m of the route (haversine — port `PoiServiceRepository.filterNearRoute`).

### 7. Saved routes / history (`RidesScreen`) — NEW
- **Purpose:** Saved + recent rides. New in the redesign.
- **Layout:** Header "Mijn ritten" (24 px display/700). Tabs "Opgeslagen" / "Recent". Scrollable list of ride cards. Bottom tab bar.
- **Components:** ride card = `surface`, radius 14–18. Top 74 px mini-map thumbnail (gradient + route polyline). Body: title 14/700 + meta row in mono `textMuted`: "42,3 km · ↗ 180 m · ★ 4,8".
- **Data:** persisted locally (MMKV). Each ride stores name, waypoints, distance, elevation, profile, timestamp.

### 8. Settings / profile (`ProfileScreen`) — NEW
- **Purpose:** Rider profile, stats, app settings. New in the redesign.
- **Layout:** Profile header (56×56 rounded gradient avatar with initials + name + "Sinds 2024 · Utrecht"). Stats card (3 columns: km gereden / ritten / knooppunten). Settings list card.
- **Components:** stats numerals in `primary`/accent display-700. Settings rows: 28×28 icon tile + label 13.5/600 + trailing value "Fiets ›" or a switch. Rows: Standaard fietsprofiel, Offline kaarten, Spraakbegeleiding (switch on), Eenheden.

## Interactions & behavior
- **Navigation flow:** Home → tap search → SearchScreen → pick From/To → route computed → RouteSummary sheet → "Start navigatie" → NavigationScreen → "Stop" → back to Home.
- **Knooppunten flow:** Home → "Knooppunten" chip → KnooppuntenScreen → tap nodes → "Bereken route" → RouteSummary → navigate.
- **Bottom sheets:** `@gorhom/bottom-sheet`, snap points ~[collapsed, mid, expanded]; drag handle at top.
- **Theme toggle:** light (Groen) ↔ dark (Ink); follows system by default, manual override in Profile. Also swaps the MapLibre style URL (bright ↔ dark-matter).
- **Off-route:** when snap distance > 30 m, recalc route from current location (see `NavigationEngine`).
- **Voice:** TTS announces maneuvers at threshold distances; Dutch locale.
- **Loading:** route calc shows a spinner; disable Start until a route exists.
- **Errors:** show inline error text (`error` color) on failed geocode/route.

## State management
Port `MapViewModel` (Kotlin `StateFlow`) to a **Zustand store** + **React Query** for async fetches. Core state:
- `userLocation {lat, lon}`, `fromLocation`, `toLocation`, `from/toSuggestions`
- `plannerMode: 'ADDRESS' | 'KNOOPPUNTEN'`, `selectedNodes: Knooppunt[]`
- `profile: 'bike' | 'mtb' | 'racingbike'`, `routesByProfile`, `activeRoute: RouteResult`
- `navigationSession` (from engine), `isNavigating`
- `layers { knooppunten, lfRoutes, pois, mapStyle }`, `pois: Poi[]`, `cyclingRoutes`
- `theme: 'system' | 'light' | 'dark'`, `savedRides: Ride[]` (persisted)

## Design tokens
See **`DESIGN_TOKENS.md`** — full light (Groen) + dark (Ink) token tables: colors, spacing, type scale, radii, shadows, and the fonts (Space Grotesk / Public Sans / Space Mono for Groen; Familjen Grotesk / Manrope / JetBrains Mono for Ink — or unify on one pairing; see notes).

## Assets
- **Existing (reuse from `fietsnavnu/app/app/src/main/res/drawable/`):** maneuver/turn icons (`ic_nav_arrow`, `ic_nav_arrive`, chevrons), origin/destination markers, layers, POI, swap, location-dot. Port to RN as SVG (`react-native-svg`) or vector assets.
- **Bundled data:** `assets/knoopunten.geojson` (junction nodes) — copy from the current app's assets.
- **Fonts:** Google Fonts (see tokens). No brand logo provided; keep the wordmark "FietsRouten".

## Files in this bundle
- `README.md` — this spec.
- `ARCHITECTURE.md` — RN + Kotlin architecture, the Kotlin→RN migration map, libraries, native modules, and milestones.
- `DESIGN_TOKENS.md` — exact color/type/spacing tokens for both themes.
- `CLAUDE_CODE_PROMPT.md` — a ready-to-paste prompt for Claude Code (run inside your repo) to create the branch and build.
- `FietsRouten Redesign.dc.html` — the interactive high-fidelity mock (open in a browser). Shows both themes across all 8 screens plus the build plan.
