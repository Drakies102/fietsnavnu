# Claude Code prompt — build FietsRouten in React Native

Copy everything in the fenced block below and paste it as your first message to **Claude Code, run from the root of your `fietsnavnu` repo**, with this `design_handoff_fietsrouten_rn/` folder present in the repo. Claude Code can create the branch and write the actual RN + Kotlin files — this design environment cannot touch your local repo directly.

> Prereqs: commit/stash current work first. Have Node 18+, JDK 17, Android SDK, and (for iOS) Xcode + CocoaPods installed.

```
You are building a cross-platform React Native rebuild of an existing native-Kotlin
Dutch bike-navigation app ("FietsRouten"). Everything you need is in the folder
design_handoff_fietsrouten_rn/ at the repo root:
  - README.md         → product spec + all 8 screens, exact layout/components
  - ARCHITECTURE.md   → RN + Kotlin architecture, backend contracts, Kotlin→RN
                        migration map, TS models, NavigationEngine port, native
                        modules, libraries, milestones, folder structure
  - DESIGN_TOKENS.md  → exact light (Groen) + dark (Ink) tokens, fonts, radii
  - FietsRouten Redesign.dc.html → high-fidelity visual mock (open in a browser)

The existing Kotlin app is in app/app/src/main/kotlin/com/fietsrouten/ — treat it as
the source of truth for behavior. Read these before writing code:
  - Config.kt (backend URLs + map styles)
  - data/model/Models.kt (port 1:1 to TS)
  - navigation/NavigationEngine.kt (port faithfully to TS, with unit tests)
  - data/repository/*.kt (endpoint shapes)
  - ui/map/MapViewModel.kt (the state to port to Zustand)

Goal & principles:
- React Native owns ALL UI and logic. Kotlin shrinks to a THIN native layer:
  LocationModule (fused GPS/speed/bearing), a nav foreground service (background
  location), and TTS/keep-awake/permissions only.
- Use react-native-navigation (Wix), @maplibre/maplibre-react-native,
  @gorhom/bottom-sheet + reanimated, zustand, @tanstack/react-query, axios,
  react-native-mmkv, react-native-svg, react-native-tts. Reuse the existing
  backend URLs UNCHANGED.
- Ship both themes as a light(Groen)/dark(Ink) toggle via a semantic token system;
  switch the MapLibre style URL with the theme. Follow DESIGN_TOKENS.md exactly.
- Recreate the mock high-fidelity — do not paste HTML; build real RN components.

Do this:
1. Create and check out a new branch:  git checkout -b feat/react-native-rebuild
2. Scaffold the RN app (TypeScript) alongside the existing Android project, using
   the folder structure in ARCHITECTURE.md. Wire react-native-navigation with a
   native bottom-tab layout: Kaart / Ritten / Profiel.
3. Implement in milestone order (M0→M8 in ARCHITECTURE.md). After each milestone,
   run the app, verify against the mock, and commit with a clear message. Start with
   M0–M2 (foundation, map+location, search+routing) and pause for my review before
   M4 (turn-by-turn), which is the highest-risk part.
4. Port NavigationEngine.kt → src/navigation/engine.ts with jest unit tests using a
   real GraphHopper sample response.
5. Implement the Kotlin LocationModule as a TurboModule and the nav foreground
   service under android/app/src/main/java/com/fietsrouten/.
6. Keep a running CHANGELOG in the PR description mapping each milestone to commits.

Confirm your understanding and the milestone plan, list the exact dependencies and
versions you'll add, then begin with M0.
```

## After Claude Code finishes a pass
- Review the branch, run on a device/emulator, compare each screen to `FietsRouten Redesign.dc.html`.
- Pay special attention to **M4** (background location + off-route recalculation + ETA) and **battery** during navigation.
- iOS parity (M8): MapLibre + location background modes need the iOS equivalents; most RN/JS code is shared.
