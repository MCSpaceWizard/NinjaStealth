# 33 — Compound terrain: ground to stand on, and fitting real worlds ⏳ approved, in progress

> **2026-10-09.** The user: large compounds need natural terrain to rest on, and we need to know how they'll meet the real terrain of a world once they're in worldgen. This note sets out what exists, the options, and a recommendation. Built so far: the **site** in §2, the nature pass and first site pieces in §6 (all in the Takamori castle example), and the **terrain pass** (§3B, §4.2: `fit`, `replace`, `exact`). The user answered §5 the same day.

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
- **Against:** worldgen only (no help for command placement or the authoring tools), and it shapes boxes, not terraces. Checked in the 26.1 source: `Beardifier` collects every piece of a structure whose `terrain_adaptation` isn't `none`. A piece that implements NeoForge's `PieceBeardifierModifier` supplies its own box, adaptation and **ground level delta** (jigsaw pieces pass the template's ground level this way); any other piece gets its box with delta 0. So our own structure type's pieces can carry each module's `ground` as the delta and get the same beard as jigsaw pieces. The beard shapes under and round each piece's box only: terraces inside a site still need the site's own foundation, and the outer ring is a smooth vanilla blend rather than §3B's 1:1 slope.

### D. Terrain-aware layout (S15)
The compound generator picks *where* and *how*: sample the surface across the footprint and reject spots that are too steep or wet; set the ground line to the median height; for terraced compounds, choose each terrace's level from the hill's contours, so a hill castle (yamajiro) climbs a real hill instead of bringing its own.
- **For:** the best-looking castles and villages.
- **Against:** belongs with the procedural generator (S15); depends on B or C for the edges anyway.

## 4. Recommendation

1. **Now:** sites (A) for authored large compounds, as in the castle. Done on this branch.
2. **Done:** the terrain pass (B) in `CompoundPlacer` (`authoring/TerrainFit`), with a `terrain` block in the compound JSON and a mode on `/es compound place <id> [pos] [rotation] [mirror] [fit|replace|exact]`:
   - `fit` (default): place **alongside** the terrain. Before the templates: the footprint (every module's box) is cleared above the ground line (hills, trees, water) and filled below it, at most `depth` blocks down. After: each ring column within `blend` blocks is sloped from the ground line towards its natural height, at most one block up or down per block out (an embankment or a cutting); columns already that close are left alone. Where the ground is further off than the ring is wide, a step remains at the ring's outer edge.
   - `replace`: **replace** the terrain. The footprint and the ring are flattened to the ground line, so the compound brings a level stretch of its own ground with it.
   - `exact`: the templates only, as before.
   - New surface keeps the world's own top block (grass, sand, snow block, gravel, stone); fill is dirt for 3 blocks, then stone. Blocks are set without neighbour updates, like templates.
   - JSON: `"terrain": {"mode": "fit", "blend": 16, "depth": 24}`, all optional (defaults shown; `blend` 0 to 32, `depth` 0 to 64). The command's mode overrides the file's.
   - The undo snapshot covers the footprint, the ring and every height touched, so `/es structure undo` restores the hills (the undo limit rose to 8 million blocks for this). GameTest `compounds/terrain` places on a built hillside in each mode.
   - Seen in the real client: the castle on a snowy hillside cuts in with stepped 1:1 slopes ([screenshot](../screenshots/structures/takamori_fit_hills.png); the three modes side by side against the untouched hills: [from the south-east](../screenshots/structures/terrain_modes_se.png), [from the south-west](../screenshots/structures/terrain_modes_sw.png); undo put the hills back exactly). Known: snow layers on reshaped columns are removed, and the site's own green grass shows in snowy biomes.
3. **Worldgen (S15):** our own structure type, `emergentstealth:compound`, that places a compound like the command does:
   - choose the spot by sampling the heightmap over the footprint (reject steep or underwater spots);
   - use the terrain adaptation in C (pieces carry each module's ground as their delta) where it looks right, and the same `TerrainFit` pass for the rest;
   - register routes and zones and spawn the garrison when the chunk first loads near a player (structure generation can't touch saved data or spawn our NPCs safely).
4. **Later:** terrain-aware terraces (D) with the compound generator.

## 5. Decisions (user, 2026-10-09)

1. **Placing by command** fits to the terrain by default, with an option to **replace** the terrain instead (§4.2: `fit`, `replace`, `exact`).
2. **Worldgen** starts with fairly flat spots only; castles that follow real hills come later.
3. **Moats** become an optional site piece.
4. The wall design is liked; its plaster-and-timber top was too short. Now: two blocks of plaster with posts every 8, loopholes at eye height every 4, a timber band, a tile cap and a tiled eave over the walk (guards see out through the loopholes, and a crouching player behind the parapet is hidden).
5. Sites should look less dead and flat: natural noise (grass, flowers, leaves, trees) and a list of small site pieces to place procedurally or have the builder make (§6).

## 6. Nature and site pieces

### 6.1 The nature pass (built)
`Site.nature` dresses a grass surface after everything else is placed. Smooth value noise (`smooth`) makes patches rather than salt-and-pepper:
- **Grass and ferns** in patches, tall grass now and then, a few blades anywhere.
- **Flower clusters**, one kind per cluster (poppy, dandelion, cornflower, bluet, daisy, allium, lily of the valley).
- **Under trees:** pink petals round cherries, leaf litter round spruce and oak.
- **Worn ground:** coarse dirt and moss in a few spots; **ragged path edges** (grass creeping into the gravel, the odd cobble).
- **Shrubs** (azalea, some flowering) in clumps along the inside foot of the curtain wall, kept clear of gates and stairs.
Plants are only placed on grass with air above, so paths, stairs and markers stay clear; the marker checker counts plants as passable, like the game.

### 6.2 Site pieces (points of interest)
Small set pieces that give a site landmarks, cover and life. Generated ones are methods on `Site` (one call each, with the surface height they stand on); detailed ones are better hand-built by the builder and placed as modules.

| Piece | Status | Gameplay |
|---|---|---|
| Stone lantern (toro) | ✅ generated | A light the lamplighter relights; snuff it for shadow |
| Garden pond (stone rim, lily pads) | ✅ generated | Blocks a straight line; water to hide in later |
| Well (roof, chain) | ✅ generated | A landmark; later a water source and a hiding spot |
| Wayside shrine (hokora) | ✅ generated | A small light and an offering; a meeting point for townsfolk |
| Bamboo stand | ✅ generated | Dense cover and a sound mask when walking through it (S6 masking) |
| Boulder | ✅ generated | Low cover |
| Woodpile, hay bales | ✅ generated | Cover; hay later softens a drop |
| Trees (spruce, oak, cherry) | ✅ generated (simple) | Cover and climbing later; the builder's trees would look better |
| Moat (decided, §5.3) | to do | A ring of water outside the wall, with a bridge at the gate |
| Rock garden (raked gravel, set stones) | to do | Open ground: noisy gravel, no cover |
| Training ground (straw targets, weapon racks) | to do | Where off-duty guards gather by day |
| Stable, cart, sacks and crates | to do | Cover in the bailey; carts block lanes |
| Watch fire / brazier | to do | A big light that guards tend |
| Notice board, bell frame | to do | The bell for S9's alarms |
| Bridges (arched, plank) | to do | Over ponds and moats |
| Stepping stones, stone stairs on slopes | to do | Paths over uneven ground (for `fit`) |

**Already in the mod as modules:** the builder's small shrines (`edo/small_shrine_v1`…`v6`), statues (buddha, kitsune, frog, villager, illager), the cemetery (`mini_cemetary`), the burial mound (`kofun`) and the onsen. A compound can place these on a site today.

### 6.3 Placing them procedurally (next)
Today the castle places its pieces by hand in `sites.py`. The next step is a **scatter rule** per site region: which pieces, how many per 100 blocks², how far from paths, markers, stairs and each other, and which region (bailey, terrace, garden) they belong to. The same rules feed S15's compound generator. Later they can move to data (a `site_piece` list in JSON) so map-makers can add their own.

**Builder or generator?** Recommendation: keep generating the simple pieces in code (they're quick, and size and material are parameters), and ask the builder for the detailed hero pieces where a procedural one looks plain: trees (pines, maples, cherries), a stable, a training ground, a bridge, a bell frame, and a rock garden.
