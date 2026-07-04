# Design tokens — Groen (light) + Ink (dark)

Ship both as a **single semantic token system** with a light/dark toggle. Component code references *semantic* names (`surface`, `primary`, `text`…), never raw hex. Below: the raw palette, the semantic mapping per theme, then a ready `tokens.ts`.

## Raw palette
**Groen (light)**
| Token | Hex |
|---|---|
| cream (app bg) | `#F4F2EC` |
| surface | `#FFFFFF` |
| green (primary) | `#1B9E57` |
| green-dark | `#0E7A44` |
| amber (accent) | `#EF7C1A` |
| ink | `#15241B` |
| muted text | `#6B7A70` |
| hairline | `#E7E3D8` |
| surface sunken | `#F1EEE4` |
| nav bar (dark) | `#0C1C14` |
| speed badge | `#12291D` / text `#7FD3A4` |

**Ink (dark)**
| Token | Hex |
|---|---|
| canvas / map | `#10141C` |
| surface | `#161B24` |
| surface raised | `#1B212D` |
| cobalt (primary) | `#3D6BFF` |
| lime (accent) | `#C4F542` |
| text | `#EDF1F7` |
| muted text | `#8A94A6` |
| hairline | `#262D3A` |
| surface sunken | `#10141C` |
| nav bar | `#000000` |
| border (raised) | `#2A3342` |

**Shared**
| Token | Hex |
|---|---|
| error / stop | `#D32F2F` |
| white | `#FFFFFF` |
| cycling route NCN | `#FF6B35` · RCN `#2E7D32` · LCN `#9E9E9E` (from current app) |

## Semantic mapping
| Semantic | Light (Groen) | Dark (Ink) |
|---|---|---|
| `canvas` | `#F4F2EC` | `#10141C` |
| `surface` | `#FFFFFF` | `#161B24` |
| `surfaceRaised` | `#FFFFFF` | `#1B212D` |
| `surfaceSunken` | `#F1EEE4` | `#10141C` |
| `primary` | `#1B9E57` | `#3D6BFF` |
| `primaryDark` | `#0E7A44` | `#2A4DCC` |
| `accent` | `#EF7C1A` | `#C4F542` |
| `routeLine` | `#1B9E57` | `#C4F542` |
| `text` | `#15241B` | `#EDF1F7` |
| `textMuted` | `#6B7A70` | `#8A94A6` |
| `hairline` | `#E7E3D8` | `#262D3A` |
| `onPrimary` | `#FFFFFF` | `#FFFFFF` |
| `navBar` | `#0C1C14` | `#000000` |
| `speedBadgeBg` | `#12291D` | `#C4F542` |
| `speedBadgeText` | `#7FD3A4` | `#0C1017` |
| `mapStyleUrl` | `…/styles/bright` | `…/dark-matter-gl-style/style.json` |
| `error` | `#D32F2F` | `#D32F2F` |

> Note: in the dark theme the speed badge is a solid lime chip with dark text; in light it's a dark chip with mint text. The route line flips green→lime.

## Typography
Two pairings are used in the mock (one per theme). **Recommendation:** for a single shipped app, pick ONE pairing and apply it in both themes for consistency — Groen's (Space Grotesk / Public Sans / Space Mono) is the safer general-purpose choice; keep Ink's only if you want the themes to feel distinct.

| Role | Groen | Ink |
|---|---|---|
| Display / headings | **Space Grotesk** 700 | **Familjen Grotesk** 700 |
| Body / UI | **Public Sans** 400–700 | **Manrope** 400–800 |
| Numerals / mono (distance, speed, labels) | **Space Mono** | **JetBrains Mono** |

**Type scale (px):** display 24–32 · stat numerals 26 (mono) · big turn distance 30 · title 14–15/700 · body 13.5–15 · caption 11–12 · overline/mono-label 10–11 (letter-spacing .08–.1em, uppercase).

## Radii
- FAB / icon tile: 10–14
- Cards / sheets: 14 (Ink) / 18–20 (Groen) · sheet top corners 20–24
- Buttons: pill (`999`) in Groen, 12 in Ink
- Node markers / dots: full circle
- Phone-screen container in mock: 28 (not an app value)

## Spacing
4-based scale: 4, 6, 8, 10, 12, 16, 20, 24. Screen edge insets 12–18. Hit targets ≥ 44.

## Shadows
- Light (Groen): soft ink shadows, e.g. card `0 8px 24px -12px rgba(21,36,27,.25)`; button glow `0 12px 24px -10px rgba(27,158,87,.6)`.
- Dark (Ink): minimal shadows; use 1 px `hairline`/`border` strokes instead, plus colored glows on primary CTAs (`0 0 24px -6px rgba(61,107,255,.6)`) and focus dots.

## `tokens.ts` (starter)
```ts
export const light = {
  canvas:'#F4F2EC', surface:'#FFFFFF', surfaceRaised:'#FFFFFF', surfaceSunken:'#F1EEE4',
  primary:'#1B9E57', primaryDark:'#0E7A44', accent:'#EF7C1A', routeLine:'#1B9E57',
  text:'#15241B', textMuted:'#6B7A70', hairline:'#E7E3D8', onPrimary:'#FFFFFF',
  navBar:'#0C1C14', speedBadgeBg:'#12291D', speedBadgeText:'#7FD3A4',
  error:'#D32F2F', mapStyleUrl:'https://tiles.openfreemap.org/styles/bright',
} as const;

export const dark: typeof light = {
  canvas:'#10141C', surface:'#161B24', surfaceRaised:'#1B212D', surfaceSunken:'#10141C',
  primary:'#3D6BFF', primaryDark:'#2A4DCC', accent:'#C4F542', routeLine:'#C4F542',
  text:'#EDF1F7', textMuted:'#8A94A6', hairline:'#262D3A', onPrimary:'#FFFFFF',
  navBar:'#000000', speedBadgeBg:'#C4F542', speedBadgeText:'#0C1017',
  error:'#D32F2F', mapStyleUrl:'https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json',
};

export const radius = { tile:12, card:16, sheet:22, pill:999, button:12 };
export const space  = { xs:4, sm:8, md:12, lg:16, xl:20, xxl:24 };
export type Theme = typeof light;
```

Provide `ThemeProvider` (reads `theme: 'system'|'light'|'dark'` from the store) and a `useTheme()` hook returning the active token object; feed `mapStyleUrl` to MapLibre so the base map switches with the theme.
