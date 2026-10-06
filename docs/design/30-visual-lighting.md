# 30 — Visual lighting: dynamic lights, baked shadows, "dark is dark" (Track E) 📄

> **Status (2026-10-06):** implemented on `claude/visual-lighting` as designed.
> - Checked in a real client with vanilla rendering, Sodium 0.9.2, and Sodium + Iris 1.11.4 with a shader pack (screenshots below).
> - 2 new GameTests; all 23 pass.
> - The dedicated server still boots.

Track E R&D (Q2, L-01, L-02, L-07 b, L-10). The server's `ExposureModel` (doc 13) is the gameplay truth: it ray-traces cast shadows from every light source and counts held lights. Vanilla *rendering* disagrees in three ways, and this doc fixes each:

| Problem | Fix |
|---|---|
| Held torches and burning mobs light nothing | **Visual dynamic lights** for every entity carrying a light |
| Flood-fill block light leaks around walls, so gameplay-dark spots look lit | **Shadowed block light**: the block light used when meshing comes from the same ray-traced model as the server |
| A high brightness slider makes darkness pointless | **"Dark is dark"**: caps the brightness slider and the ambient floor |

All three are client-only presentation. They never feed back into gameplay. Each has its own client config toggle.

## 1. The hook: one function for vanilla *and* Sodium

Everything that bakes block light into a mesh ends up in `LevelRenderer.BrightnessGetter.DEFAULT` (the lambda `lambda$static$0`, which returns packed `block << 4 | sky << 20`):

- vanilla chunk meshing (`BlockModelLighter`, `FluidRenderer`, through `LevelRenderer.getLightCoords`)
- block entities and particles (`LevelRenderer.getLightCoords(level, pos)`)
- **Sodium 0.9.2**: `LightDataAccess.compute` calls `LevelRenderer.getLightCoords(BrightnessGetter.DEFAULT, slice, state, pos)`. Checked in the `mc26.1.2-0.9.2` source.

So a single `@ModifyReturnValue` mixin on that lambda covers both renderers, with no Sodium-specific mixin. LambDynamicLights 4.11 uses the same hook on 26.1. Entities are lit separately (`EntityRenderer.getBlockLightLevel` reads `level.getBrightness` directly), so they get a second small mixin.

**Why mixins:** NeoForge has no event for per-block light during meshing, entity light probes, the lightmap inputs, or client-side block changes. Each mixin carries a comment saying this.

| Mixin (client) | Target | Purpose |
|---|---|---|
| `BrightnessGetterMixin` | `LevelRenderer$BrightnessGetter.lambda$static$0` | Replace the block-light nibble with the shadowed + dynamic value |
| `EntityRendererMixin` | `EntityRenderer.getBlockLightLevel` | The same value for entities (and your first-person hand) |
| `LevelRendererMixin` | `LevelRenderer.blockChanged` (tail) | A block change can move a shadow up to 15 blocks away, so mark those sections dirty |
| `LightmapRenderStateExtractorMixin` | `LightmapRenderStateExtractor.extract` (tail) | "Dark is dark": clamp `brightness` and scale `ambientColor` |

## 2. Shared math (client = server)

Behaviour-preserving refactors, so client and server run the same code:

- `SightRay.transmittance` takes a `BlockGetter` instead of a `Level`, plus an overload with a second skipped block (the target cell). Server call sites are unchanged.
- **New `stealth/light/LightTransport`**: the candidate/potential/shadow-combine core taken out of `ExposureModel.blockExposure`:
  - `potential = (emission − distance) / 15`
  - sort the candidates, trace the 8 strongest
  - combine with `1 − Π(1 − pᵢ·tᵢ)`
  
  `ExposureModel` now calls it. Same order, same arithmetic.
- `LightSourceIndex.scanSection(section, sx, sy, sz)` becomes a public pure function. The server index and the client index both use it.
- `DynamicLights.lightPosition(entity)` is now public. `emission`/`itemEmission` were already public. The client puts each light at exactly the spot the server uses.

Two GameTests check that, for every air cell of a test room, `round(15 × ExposureModel.blockExposureUncached(p))` equals the visual light level computed by the client's path:
- `visual/shadow_matches_gameplay`: a torch, a wall and glass
- `visual/held_light_matches_gameplay`: a player holding a torch next to a wall

`VisualLightTests` registers them itself, so `ESGameTests` is unchanged.

## 3. Shadowed block light (baked)

For a position `P` being meshed (`client/light/ShadowedBlockLight`):
1. If `P` is an opaque full cube, keep vanilla's value. Otherwise ambient occlusion would pull light through walls.
2. Candidates:
   - static emitters from a **client source index** (`ClientLightSources`: per-section emitter lists from the shared scan, cached in a `ConcurrentHashMap`, invalidated on block change and chunk load/unload, 5 s expiry like the server)
   - the current **dynamic light snapshot**
3. `level = round(15 × LightTransport.shadowed(level, center(P), candidates, skip = P))`.
4. The result is `max(level, round(vanilla × shadowBounce))`. `shadowBounce` (default **0.15**) keeps a hint of bounce light so geometry stays readable. 0 means pure gameplay truth.

**Threads.** Meshing runs on worker threads, and Sodium's `LevelSlice` only copies ±2 blocks around a section. So rays and source scans read the live `ClientLevel`, the same way vanilla meshing already reads the live light engine:
- Chunk storage is an `AtomicReferenceArray`.
- `PalettedContainer.get` reads one `data` snapshot.
- Any exception falls back to the vanilla value for that position.

**Caches.**
- Each thread caches results per position. The cache is stamped with a block-change generation and a dynamic-light version, so it survives across section compiles and clears itself when anything relevant changes.
- Each thread also caches the source union for the 3×3×3 sections around the current one.

**Invalidation.** On a client block change:
- the source index entry is dropped
- every source (static or dynamic) whose light sphere contains the change marks the sections in its sphere dirty (deduplicated, flushed once per tick)

A light placed or snuffed rebuilds its own sphere.

**Sky light is untouched.** Sun and moon shadows change with time of day. Baking them would mean rebuilding every section continuously. With Iris, shader packs already draw sun shadows that roughly match ours (Q2). Vanilla's sky light remains the stand-in.

## 4. Visual dynamic lights

`client/light/ClientDynamicLights` runs every client tick on the main thread:
- Every entity within `dynamicLightRange` (default 48) of the camera with `DynamicLights.emission(entity) > 0` is a light. That covers players, our torch-carrying NPCs, burning mobs and anything holding a light item. Dropped light items are added optionally (`itemEntityLights`, **off** by default because the server doesn't count them).
- A light is "rebuilt" when:
  - it has moved ≥ 0.5 blocks, appeared, vanished or changed brightness
  - at most every `dynamicLightInterval` ticks (default 2)
- A rebuild marks dirty the sections that intersect the light's sphere (radius = emission) at both its old and new positions, via `levelRenderer.setSectionDirty`. Sodium redirects that call to its own renderer.
- Meshing and entity lighting read an immutable snapshot (positions as of the last rebuild), so terrain and entities stay consistent.

Dynamic lights cast the same shadows as static ones. A guard's torch doesn't light the room behind the wall.

## 5. "Dark is dark" (L-07 b)

At the tail of `LightmapRenderStateExtractor.extract`, when the lightmap was just updated:
- `brightness = min(brightness, darkMaxBrightness)` (default **0.0**, the "Moody" end of the slider)
- `ambientColor *= darkAmbientScale` (default **0.35**)

The overworld's light-0 floor drops from ~0.04 (up to ~0.15 with the slider maxed) to ~0.014. Night vision is untouched (`max(ambient, nightVision)` in the shader), so a later owl-eye gadget (L-07 d) still works.

- **Accessibility opt-out:** `darkIsDark = false` restores vanilla exactly.
- **Iris:** when a shader pack is active (`IrisApi.isShaderPackInUse()`, called by reflection, so no compile dependency), the tweak is skipped unless `darkIsDarkWithShaders` is on. Packs do their own tonemapping.

## 6. Config (client, section `visual_lighting`)

| Key | Default | |
|---|---|---|
| `dynamicLights` | true | Held/burning lights light the world |
| `dynamicLightRange` | 48 | Blocks from the camera |
| `dynamicLightInterval` | 2 | Min ticks between section rebuilds per light |
| `itemEntityLights` | false | Dropped torches glow (not counted by gameplay) |
| `shadowedBlockLight` | true | Block light uses the shadow-casting model |
| `shadowBounce` | 0.15 | Fraction of vanilla light kept as bounce |
| `darkIsDark` | true | Accessibility opt-out when false |
| `darkMaxBrightness` | 0.0 | Cap on the brightness slider |
| `darkAmbientScale` | 0.35 | Multiplier on the ambient floor |
| `darkIsDarkWithShaders` | false | Also apply with an Iris shader pack |

Toggling a lighting option rebuilds all chunks (`levelRenderer.allChanged()`).

## 7. Performance budget

- **No light sources near a section:** cost ≈ 0. The source union is empty, so vanilla's value passes through.
- **Section near lights:**
  - ≤ 8 rays per position, each ≤ 15 blocks, cached per position per thread
  - **measured:** ~11–14 µs per uncached cell with a warm JIT (GameTest timing log, one torch plus a held torch); ~80–100 µs on a cold JIT
  - only cells within a light's reach pay this. A section with ~1,000 lit cells costs ~10–15 ms on one worker thread the first time, then hits the cache
  - nothing runs on the main thread except entity, block-entity and particle probes, which are cached per cell
- **Dense emitters** (lava lakes, glowstone walls): if more than 256 sources overlap a section's neighbourhood, that section uses vanilla light. Documented delta.
- **Dynamic lights:**
  - ≤ 27 sections per moving light, every `dynamicLightInterval` ticks
  - empty-air sections compile for free
  - with Sodium, rebuilds are cheap
  - the range cap bounds the number of lights; LambDynamicLights accepts the same trade-off

## 8. Compatibility

- **Sodium 0.9.2:** works through the shared hook above; no Sodium classes are referenced. Verified in a dev client with `-PwithSodium`: shadows, held lights, and re-baking after a block change.
- **Iris 1.11.4** (verified with `-PwithIris` and the MakeUp Ultra Fast pack):
  - baked lights and shadows flow into `lmcoord`, which packs read
  - shader detection works; the log says `Iris shader pack active: dark-is-dark paused`
  - packs that add their own held-light glow will double up with our dynamic lights, so set `dynamicLights = false` if that looks wrong
  - "dark is dark" is skipped with packs by default
- **LambDynamicLights:** both mods hook the same lambda. Don't run both; use ours, since it matches the gameplay model.
- **Dedicated server:** every visual class lives in `client.light`/`client.mixin` and is only reached from `Dist.CLIENT` code. Client mixins are in the `client` array.

## 9. Known limitations

- Sky and sun shadows are not baked (§3). The light gem remains the truth for sun shade.
- Light is still per block. Shadow edges are block-sharp, and a shadow cast by a thin object (a fence) through partial cover is averaged per block.
- Dynamic lights update in steps (0.5 blocks / 2 ticks), not per frame.
- Dense emitter fields fall back to vanilla flood fill (§7).
- Euclidean falloff (gameplay) instead of vanilla's diamond. Light pools are rounder and reach a little further diagonally.

## Screenshots

All shots are taken on a new-moon night with the brightness slider at "Bright", in software GL, in `docs/screenshots/visual-lighting/`. The "shadows" panels have dark-is-dark off; the "full" panels have everything on.

| File | Shows (left → right) |
|---|---|
| `torch_wall_vanilla_shadows_full.png` | Torch beside a wall: vanilla, shadows only, full |
| `behind_wall_vanilla_vs_shadows.png` | Far side of the wall: vanilla leaks torchlight along its foot and onto its face; ours doesn't |
| `pillar_sodium_vanilla_shadows_full.png` | **Sodium**: a pillar placed next to a torch casts a shadow wedge, re-baked live |
| `sodium_behind_wall_shadows_vs_vanilla.png` | **Sodium**: far side of the wall, shadows vs vanilla |
| `held_torch_off_vs_on.png` | Player holding a torch: dynamic lights off vs on |
| `sodium_held_torch.png` | **Sodium**: held torch after teleporting; the light follows |
| `npc_carried_torch.png` | A stealth NPC with a torch in its off-hand lights its surroundings |
| `iris_shaderpack_pillar.png` | **Iris** + a shader pack: our baked block-light shadows show through the pack |
