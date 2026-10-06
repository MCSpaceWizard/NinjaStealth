# Research: our own shader pack so shader visuals match our lighting 📄

Research only, nothing implemented. The question: would shipping our own Iris shader pack make shader visuals match the gameplay light model (docs 13, 30)? Findings were checked against Iris's `26.1` branch source and Modrinth listings on 2026-10-06.

## 1. Feasibility on 26.1.2 / NeoForge

| Loader | 26.1.2 NeoForge? | Notes |
|---|---|---|
| **Iris** | ✅ official NeoForge builds (`1.11.4+26.1`) | Needs Sodium. Already run here with `-PwithIris` and the MakeUp Ultra Fast pack |
| Oculus (old Forge port) | ❌ stops at 1.20.1 | Superseded by official Iris NeoForge |
| OptiFine | ❌ not on NeoForge | — |
| Veil (Foundry) | ❓ no 26.1 build found on Modrinth | A separate pipeline rather than a pack loader; it doesn't run OptiFine/Iris packs |
| Distant Horizons | ✅ 3.3.3 for 26.1.2 | Relevant only as an example of how Iris integrates other mods (below) |

So an Iris-format pack (GLSL + `shaders.properties`) is the only realistic shader-pack target.

## 2. Getting our data into a pack

| Channel | How | Works with | Verdict |
|---|---|---|---|
| **Lightmap coords (`lmcoord`)** | Already done (doc 30): our shadowed and dynamic block light is baked into the vertex light every pack reads | **Every pack** | ✅ Keep. Verified: MakeUp shows our torch shadows |
| **Mod-owned texture** | Register a `DynamicTexture` at e.g. `emergentstealth:dynamic/stealth_data` (dynamic-light list, player exposure, settings). Pack binds it with `texture.<stage>.<sampler>=emergentstealth:dynamic/stealth_data` | Our pack, or packs that opt in | ✅ Best extra channel. Iris looks `ns:path` textures up in Minecraft's `TextureManager` on every bind (`CustomTextureManager`, `ResourceData`), so no Iris API or mixin is needed |
| Custom uniforms | Iris has **no public API** for mods to add uniforms. `uniform.*` in `shaders.properties` only combines built-in variables | — | ❌ Would need mixins into Iris internals (`CommonUniforms`), which break every Iris update |
| SSBOs (`bufferObject.N`) | Packs declare them and shaders write them; nothing lets a mod upload into them | — | ❌ Internals again |
| Vertex attributes | Sodium/Iris own the vertex format | — | ❌ |
| Iris public API v0 | `isShaderPackInUse`, `IrisItemLightProvider` (held-item light value/colour for the **local player only**), shadow-pass hooks | All packs | ⚠️ Useful only for colour and held-light tweaks |

Precedents:
- Distant Horizons and Flywheel/Colorwheel compat work because **Iris itself** ships integration code (DH's API jar is bundled in Iris). That isn't available to us.
- LambDynamicLights uses the same `lmcoord` route we do.

## 3. What our pack would need

| Feature | Needs | Difficulty |
|---|---|---|
| "Dark is dark" that matches gameplay | The pack's own tonemap/ambient floor, tuned to our lightmap | Low |
| Torch (block-light) shadows | Already in `lmcoord`. Truly per-pixel shadows would need voxel data in the shader (shadow-pass voxelisation tricks) | Baked: done. Per-pixel: **high** |
| Dynamic/held lights per pixel | The data texture's light list, plus a per-pixel falloff in `gbuffers_*`. Shadows for them come from our bake | Medium |
| Sun/moon shadows like our model | Standard shadow map (every pack has one). Roughly matches our sun ray (Q2) | Low (stock) |
| Light gem ↔ visuals consistency | Both read the same model. The pack can also read player exposure from the data texture for a vignette/tint | Low–medium |

**A small own pack vs an add-on on top of popular packs:**
- Iris has **no way to layer** a pack on top of another; each pack is monolithic GLSL. "Add-on to Complementary/BSL" would mean patching their source.
- Licences:

| Pack | Licence | Can we fork or redistribute? |
|---|---|---|
| Complementary Reimagined | custom licence | Not without the authors' permission |
| BSL | All Rights Reserved | No |
| MakeUp Ultra Fast | **LGPL-3.0** | Yes, as a base for a small "Stealth" pack (keep LGPL for the pack) |

- The realistic options are:
  - a **small own pack**, forked from an LGPL pack or written fresh
  - a published **data-texture contract** that popular-pack authors can opt into

## 4. Own shader pack vs our own pipeline (doc 30)

| | Own Iris pack | Own pipeline (current: baked light + lightmap tweak, no shader loader) |
|---|---|---|
| Effort | Weeks for a polished pack, plus upkeep for each Iris/pack-format change | Done (first pass). Small increments from here |
| Players' favourite packs | Replaced by ours while it's active | ✅ Kept. Our baked shadows and lights show through any pack that uses `lmcoord` |
| Performance | Pack-dependent. Per-pixel lights are cheap; voxel shadows are expensive | CPU cost on meshing threads (doc 30 §7); no GPU cost |
| Multiplayer | Client-side only. No extra sync: the data texture is built from entities the client already sees and the light-gem value | Same |
| Fairness | We control darkness under shaders | Under third-party packs, darkness is up to the pack (dark-is-dark steps aside) |
| Needs Sodium + Iris | Yes | No (works vanilla, Sodium, and Iris) |

## 5. Recommendation and staged plan

**Don't make a shader pack the primary solution.** The baked approach already carries our shadows and dynamic lights into *every* pack, and it works without shaders. Treat a pack as an optional "Emergent Stealth look" add-on.

1. **Now (done, doc 30):**
   - `lmcoord` bake
   - dark-is-dark that pauses under packs
2. **Next, small:**
   - test with Complementary and BSL. **Complementary r5.9.3: done.** It loads, its sun shadows match ours in direction and length, and our sky bake and dark-is-dark pause. BSL isn't freely downloadable (All Rights Reserved), so it's untested
   - ~~add a toggle for the local player's held light~~ **done:** `heldLightWithShaders` (default off) skips only your own held light under a pack, since packs add their own `heldItemLightValue` glow
   - document recommended packs
3. **Optional, medium:**
   - publish the `emergentstealth:dynamic/stealth_data` texture contract (dynamic lights, player exposure, dark-is-dark settings), versioned
   - write a ~200-line proof-of-concept patch to a fork of LGPL MakeUp that reads it for per-pixel held lights and a matching dark tonemap
4. **Optional, large (only if players want the look):**
   - ship "Emergent Stealth Shaders" as a separate LGPL pack: our tonemap, stock sun shadow map, data-texture lights
   - per-pixel block-light shadows stay baked; voxel shadows are out of scope

Open question for you: is a bundled pack wanted at all, or is "works with your favourite pack" (stages 1–2) enough?
