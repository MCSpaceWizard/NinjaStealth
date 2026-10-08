# Emergent Stealth: art style guide

This guide covers items, blocks, entities and UI, so that everyone's art reads as one mod. When in doubt, match vanilla Minecraft's readability first and our ink-and-paper accent second.

## 1. The look in one paragraph

Feudal Japan at night, seen through **ink on washi paper**.
- **Shapes:** sturdy, readable silhouettes in Minecraft's pixel style.
- **Colours:** muted and earthy, with one strong accent: **vermilion lacquer** (seals, cords, important marks).
- **Materials:** cloth, wood, iron, paper and lacquer.
- **Avoid:** glossy fantasy metals, neon and pure saturated colours (except light sources).
- **Darkness is gameplay:** items and outfits should look good in low light, so give them clear value contrast and don't rely on hue alone.

## 2. Palette

The programmer art uses these named colours (`tools/programmer_art/textures.json`). Treat them as the anchor palette; you can add shades in between.

| Name | Hex | Use |
|---|---|---|
| ink | `#1b1a20` | Darkest values, shinobi cloth, outlines inside shapes |
| charcoal | `#2b2420` | Soot, burnt tips |
| paper | `#e8dcc0` | Washi, labels, light cloth |
| lacquer | `#a8322d` | The accent: seals, cords, armour lacing |
| lacquer_dark | `#5c1c1a` | Lacquer shadows |
| indigo / indigo_light | `#2d3a5c` / `#4a5d8a` | Commoner cloth, ashigaru |
| steel / dark_steel | `#9aa3ad` / `#3b3f46` | Blades, iron fittings |
| wood | `#7a5534` | Handles, shafts, frames |
| hemp | `#a8956b` | Rope, straw, labourers |
| straw | `#c8ad6a` | Kasa hats, mino capes |
| jade | `#4f8a6b` | Rare accents (debug lens) |
| gold | `#c9a227` | High rank only (daimyo, taisho) |
| gem_lit / gem_lit_hi | `#f2a93b` / `#ffe9a8` | Firelight, the lit light gem |

## 3. Pixel rules

- **Sizes:**
  - Items and blocks are **16×16**. Higher resolutions (32/64) are fine only if the whole set moves up together.
  - Entities and GUI use the sizes in the texture list.
- **No anti-aliasing and no semi-transparent edges** on items: hard pixel edges. Transparency is only for real holes.
- **Light comes from the top-left.** Highlight the top-left edges, shade the bottom-right.
- **3 to 5 values per material:** highlight, light, mid, shadow, deep shadow. Keep each item to about 12 colours or fewer.
- **Outline:** vanilla style. No uniform black outline; use the darkest shade of each material on its outer edge. Ink-black only for ink-coloured materials.
- **Readability at 16 px:** the silhouette alone should say what the item is. Test it as a solid black shape.
- **Hand-held tools** sit on the diagonal like vanilla tools, with the handle bottom-left and the business end top-right.
- **Throwables** (pebble, smoke bomb, firecracker, blinding powder) are small and centred, using about 10×10 of the 16×16 canvas, so they look throwable.

## 4. Entities (NPCs)

- **Bodies** use the **64×64 player skin layout** (classic 4 px arms). See `docs/assets.md` for the UV map.
- **Outfits are separate overlay layers** on the same 64×64 layout: under-layer cloth, then armour. They are drawn over the body, so leave everything they don't cover transparent.
- **Each archetype must be recognisable at 20 blocks in the dark.** Differentiate by **silhouette and value**, not just hue:

  | Archetype | Role | Read |
  |---|---|---|
  | Ashigaru | Foot soldier | Jingasa (flat conical hat), simple chest plate, indigo |
  | Samurai | Elite | Full lacquered armour, kabuto with crest, red lacing |
  | Taisho | Captain | Samurai plus a sashimono banner and gold trim |
  | Daimyo | Lord / target | Rich layered kimono, no armour, gold and deep colours |
  | Labourer | Worker | Hemp clothes, headband, rolled sleeves, straw |
  | Townsfolk | Civilian | Plain kimono and obi, varied soft colours |

- **Armour layers** for player gear use vanilla's **64×32** humanoid equipment layout (`textures/entity/equipment/humanoid/` and `humanoid_leggings/`).

## 5. UI ("Sumi")

- The UI is **ink and paper**:
  - washi-paper panels with visible grain (never flat fills)
  - sumi-ink brush strokes for frames and dividers
  - vermilion seals (hanko) for important buttons and states
- **Nine-slice friendly:** panel and frame textures must stretch cleanly. The texture list gives each one's sizes and insets.
- **Motion is part of the style:** ink-bleed reveals, eased slides, smooth scrolling. Static art should leave room for it, e.g. a seal that can "stamp" in.
- **HUD elements** (light gem, prompts, technique readout) must read against both bright day and pitch dark. Use paper-coloured text with a soft ink shadow.

## 6. Files and delivery

- **Format:** PNG, RGBA, sRGB. Names are `snake_case` and must match the paths in [TEXTURE_LIST.md](TEXTURE_LIST.md) exactly. Dropping your file over the placeholder at the same path is all it takes, with no code change.
- **Animated textures:** put a `<name>.png.mcmeta` next to the PNG (standard Minecraft format), with frames stacked vertically.
- **Source files** (`.aseprite`, `.psd`, `.bbmodel`) go in `art_source/` with the same relative path, so the next artist can edit them.
- **Preview in game:** run the dev client, put the PNG in place, and press **F3+T** to reload textures.
- **Licensing:** only your own work, or CC0 references transformed into your own work. See [research-skins](../design/research-skins.md) for what we can and can't use.
