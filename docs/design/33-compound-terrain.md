# 33 — Compound terrain: ground to stand on, and fitting real worlds 📄 proposal, for review

> **2026-10-09.** The user: large compounds need natural terrain to rest on, and we need to know how they'll meet the real terrain of a world once they're in worldgen. This note sets out what exists, the options, and a recommendation. Nothing here is built yet except the **site** in §2 (it ships with the Takamori castle example).

## 1. The problem

A compound is templates placed at fixed offsets around an origin (doc 32 §3). Each module brings its own ground (a ground line, `ground` in the JSON), but the world under and around it is left as it was:

- **On a slope or uneven ground**, one side of a module sinks into the hill and the other floats over a drop. Rock bases (the Cherry Grove modules have 8 layers of stone under their ground line) hide some of it, but their square edges show as cliffs.
- **Trees and hills inside the footprint** stay unless a template stores air there. Cherry Grove modules do (they clear their box); the builder's Edo structures store air only around the building, so terrain pokes through elsewhere.
- **Several modules** share one compound origin, so they only line up with each other if the ground is flat. The "slightly odd placement" seen with the examples is mostly this: placed on a real surface, a ground line that is right on superflat is a block or two off on grass with bumps.
- **In worldgen** the same compound has to sit in hills, forests, rivers and beaches without looking dropped in.

## 2. What exists now

| Piece | What it does |
|---|---|
| Module ground lines | `offset y = level - 1 - ground` puts the module's ground layer into the block under the placer's feet. Set by hand per module (automatic detection is still open, doc 32 §7.6). |
| Rock bases | Cherry Grove modules carry 5 to 8 layers of stone and water under their ground line, so a small step down still looks solid. |
| **Sites (new)** | `tools/compounds/sites.py` builds a whole compound's ground as one template: earth and stone **foundation** below the ground line (6 deep), grass on it, terraces with battered stone walls, curtain walls, gates and stairs, and **air stored above the ground** over the whole box so placing it clears trees and hills inside. Buildings are placed on top as ordinary modules. First use: `emergentstealth:sites/takamori_castle`. |
| Vanilla worldgen | The builder's Edo structures already have jigsaw structures (`worldgen/structure/edo/`) with `terrain_adaptation: beard_thin` and `project_start_to_heightmap`. Placement-only; nothing generates naturally yet (S15). |

## 3. Options

### A. Sites with foundations (done, for authored compounds)
The compound carries its own ground: a deep foundation and cleared air.
- **For:** exact and predictable, what the checker validates is what you get, works for `/es compound place` and later for worldgen. Terraces, walls and stairs are generated, so a new layout is a few lines of Python.
- **Against:** rectangular. On a slope the foundation shows as a stone face on the low side, and the high side is cut flat at the box edge. Fixed depth.

### B. A terrain-fitting pass when placing (recommended next)
After the templates are placed, `CompoundPlacer` fits the world to the compound, column by column around its footprint:
1. **Fill down:** under every column of the footprint, fill from the bottom of the compound down to the existing ground, with the module's own bottom material (or earth then stone by depth).
2. **Cut down:** above the ground line inside the footprint, remove terrain the templates didn't clear.
3. **Blend ring:** in a ring of `blend` blocks round the footprint, slope the terrain from the compound's ground line to the world's height, about one block up or down per block out, using the world's own surface block (grass, sand, snow). Trees on cut columns go; fluids are kept back by the same flow margin undo uses.

Configured per compound, defaulting to on for new compounds:
```json
"terrain": { "foundation": "earth", "blend": 6, "clear": true }
```
- **For:** works on any ground, for every compound, with command placement and later in worldgen (the same pass runs in the structure's placement step). Undo already snapshots the box plus a margin, so the margin grows to `blend`.
- **Against:** code (a few hundred lines in `authoring/`, plus a GameTest that places on a slope). A steep mountain still gives a visible cutting or embankment, which is what a real castle has.

### C. Vanilla terrain adaptation in worldgen
Worldgen structures can ask the noise generator to shape terrain round their pieces before any block is placed (`terrain_adaptation`: `beard_thin`, `beard_box`, `bury`, `encapsulate`). Vanilla blends a slope under and around each piece's box, using the piece's ground level (for jigsaw templates, the same idea as our `ground`).
- **For:** the most natural result, free at generation time.
- **Against:** worldgen only (no help for command placement or the authoring tools), and it shapes boxes, not terraces. To be checked in the 26.1 source: whether our own structure type's pieces get the same treatment as jigsaw pieces, and how a per-module ground line passes through.

### D. Terrain-aware layout (S15)
The compound generator picks *where* and *how*: sample the surface across the footprint and reject spots that are too steep or wet; set the ground line to the median height; for terraced compounds, choose each terrace's level from the hill's contours, so a hill castle (yamajiro) climbs a real hill instead of bringing its own.
- **For:** the best-looking castles and villages.
- **Against:** belongs with the procedural generator (S15); depends on B or C for the edges anyway.

## 4. Recommendation

1. **Now:** sites (A) for authored large compounds, as in the castle. Done on this branch.
2. **Next, small:** the terrain-fitting pass (B) in `CompoundPlacer`, with the `terrain` block in the compound JSON and an `exact` option on `/es compound place` to skip it. This also cleans up the odd placement on uneven ground, and the same code serves worldgen.
3. **Worldgen (S15):** our own structure type, `emergentstealth:compound`, that places a compound like the command does:
   - choose the spot by sampling the heightmap over the footprint (reject steep or underwater spots);
   - use the terrain adaptation in C where it works for our pieces, and pass B for the rest;
   - register routes and zones and spawn the garrison when the chunk first loads near a player (structure generation can't touch saved data or spawn our NPCs safely).
4. **Later:** terrain-aware terraces (D) with the compound generator.

## 5. Questions for the user

1. **Placing by command:** fit to the terrain by default (B), with `exact` to skip, or keep placement exact and fit only on request? *Recommendation: fit by default.*
2. **Worldgen first step:** large compounds only on fairly flat ground (spot rejection plus fitting), with terrain-following hill castles later? *Recommendation: yes.*
3. **Water:** should castle sites bring their own moat (needs a ring of extra space and a water level that agrees with the world), or leave moats to compounds placed by rivers? *Recommendation: moats as an optional site piece, used where the site is next to water.*
