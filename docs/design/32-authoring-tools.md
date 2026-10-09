# 32 — Authoring tools: structure viewer, zones, compounds ⏳ approved, in progress

> **2026-10-09.** The user's top priority: browse the builder's structures and turn them into playable compounds. This pulls the zone tool forward from S9 and the NPC spawner GUI forward from S19. Zone *gameplay* (alert levels, propagation, bells) stays in S9 (doc 18); this doc adds the tools, the data and a minimal trespass rule.

**Goal:** place any structure, mark it up (zones, posts, patrol routes, NPC spawns, lights), **save it as one compound file**, and re-place it anywhere, rotated or mirrored, fully working.

**Building material (2026-10-09):** the builder's 21 structures (`emergentstealth:edo/…`, vanilla stand-ins for their modded blocks) and 17 **Cherry Grove modules** (`emergentstealth:cherrygrove/…`, vanilla). The modules are designed to be pieced together into large compounds, so a compound can hold several templates, each with its own offset, rotation and **ground line** (each module brings its own ground and water, which must sit at terrain level). The same modules feed S15's compound generator. See [tools/structures](../../tools/structures/README.md).

## 1. Structure viewer

- **Browser (Sumi screen):** opened by the **Surveyor's Plan** item or `/es structure browse`.
  - Every structure template the server knows, grouped by folder (`emergentstealth:edo/…`, vanilla, datapacks), with search.
  - For each one: size, block count, and its most common blocks as swatches.
- **Ghost preview:** pick one and a translucent copy follows the crosshair, snapped to the ground.
  - Controls: **scroll** rotates, **M** mirrors, **PgUp/PgDn** raises or lowers it, **right-click** places it, **Esc** cancels.
  - The server sends the template's blocks to the client in chunks. Structures over about 20,000 blocks show the outline plus one horizontal slice that you scroll through, so the preview stays fast.
- **Placing** happens on the server: ops or creative only, and the server checks the request. **Undo** (`/es structure undo`) restores the blocks your last placement replaced, one level deep.
- **Commands** for scripts and tests: `/es structure list [filter]` and `/es structure place <id> [rotation] [mirror]`.

## 2. Zones

- **A zone** is a named volume, one or more boxes, with an **access rule**:
  - `public`, `restricted` or `hostile`
  - an optional **time window** (day only, night only, or hours)
  - later (S11) the outfits that may enter
- **Zone tool (Surveyor's Rope):**
  - Click two corners to make a box; shift-click adds a box to the selected zone.
  - A Sumi panel sets the name, rule and hours.
  - Zones are drawn as tinted volumes, using the same style as the debug view.
- **Storage:** per world, beside the patrol routes.
- **Minimal gameplay now** (S9 adds the rest):
  - **Restricted:** a guard who sees you there is suspicious much faster (awareness ×2), and barks a warning before hunting.
  - **Hostile:** being seen there is detection.
  - **Public:** unchanged.

## 3. Compounds

A **compound** is one or more structure templates plus markers, all relative to the compound's origin:

| Marker | Contents |
|---|---|
| Zone | boxes and an access rule (§2) |
| Patrol route | waypoints, waits, look directions, relight flags (the S5 route format) |
| Guard post | a position, a facing and an optional schedule |
| NPC spawn | archetype, behaviour, activity (post, route or wander) and schedule, from §4 |
| Light | light blocks the lamplighter relights (found automatically inside the bounds; toggle per light) |
| Command post | the captain's spawn and the banner or supply cache (Q6, used from S9) |

**File:** datapack JSON at `data/<namespace>/emergentstealth/compound/<name>.json`, so map-makers and the mod share one format:

```json
{
  "structures": [{ "template": "emergentstealth:edo/samurai_mini_fort", "offset": [0, 0, 0], "rotation": "none", "ground": 0 },
                 { "template": "emergentstealth:cherrygrove/teahouse", "offset": [52, -3, 6], "rotation": "clockwise_90", "ground": 3 }],
  "zones": [{ "name": "courtyard", "access": "restricted", "boxes": [[[2, 0, 2], [43, 10, 43]]], "hours": null }],
  "routes": [{ "name": "wall_walk", "mode": "loop", "waypoints": [{ "pos": [4, 5, 4], "wait": 40, "look": 90 }] }],
  "spawns": [{ "archetype": "emergentstealth:ashigaru", "pos": [10, 1, 12], "facing": 180,
               "schedule": [{ "from": 6, "to": 18, "activity": { "type": "route", "route": "wall_walk" } },
                            { "from": 18, "to": 6, "activity": { "type": "post", "pos": [10, 1, 12], "yaw": 180 } }] }],
  "lights": { "relight": "all" }
}
```

**Authoring flow:**
1. Place a structure with the viewer.
2. Use the **Compound Ledger** item on it to start a compound. It takes the structure's bounds and origin.
3. Add markers with the tools: the zone rope, the patrol baton (its routes now belong to the compound), and the spawner tool (§4). Lights are found automatically.
4. `/es compound save <name>` writes the JSON. If you edited the blocks, it re-saves the template from the world too. Both go into the world's generated datapack, and `/es compound export <name>` copies them to `run/compounds/` for adding to the mod.

**Placing:** `/es compound place <name> [rotation] [mirror]` (or the browser, which lists compounds beside structures).
- It places the templates, then registers the zones and routes with their positions rotated and mirrored. Route names are scoped to that copy (`<compound>#<n>/wall_walk`).
- It spawns the NPCs with their schedules.
- Each placed copy is recorded (id, origin, rotation), ready for S9's per-compound alert levels and Q6's command posts.
- `/es compound reset <id>` respawns missing NPCs; `/es compound remove <id>` clears the copy's markers.

## 4. NPC spawner GUI (Sumi)

Right-click a block with the **Muster Roll** item to open a panel where you pick:
- **Archetype:** from the registry, with the colour swatch
- **Behaviour tree**
- **Activity:** a post here (facing from where you stand), a route from this compound, or wander with a radius
- **Schedule:** hour ranges, each with its activity (reuses `Schedule`)
- **Count:** for a group at one spawn

Saving adds a spawn marker to the compound under the cursor, or spawns the NPC right away if there's no compound there. Proper spawner blocks and triggers stay in S19.

## 5. Order of work

1. Structure browser, ghost preview, place and undo.
2. Compound file format, save and place with rotation and mirroring. Routes, posts and spawns come first, because they already exist as systems.
3. The zone tool and the minimal trespass rule.
4. The spawner GUI.
5. A first real compound: **samurai_mini_fort** with guards, patrols and a lamplighter. This doubles as the "test compound" the M1 slice (S8) needs.

## 6. Tests

- **Compound round trip:** save, then place at all 4 rotations and with mirroring. Every marker lands on the matching block, a route walks the same path, and zones contain the same blocks.
- **Zone rules:**
  - restricted doubles awareness gain
  - hostile is instant detection
  - time windows respect the clock
- **Viewer:** place and undo restore the world exactly, and template payload chunking reassembles.
- **Spawner:** a saved spawn produces an NPC with the right archetype, schedule and activity.

## 7. Decisions (2026-10-09)

**Answered by the user:**
1. **Tools:** creative/op-only items (Surveyor's Plan, Surveyor's Rope, Compound Ledger, Muster Roll) **plus** matching commands.
2. **Trespass rule now:** yes, the minimal restricted/hostile reaction (§2). S9 builds on it.
3. **First compound:** samurai_mini_fort. It also serves as the M1 test compound.

**Proposals taken as the default** (say if you want them changed):

4. Saved compounds go to the world's generated datapack. `/es compound export` copies them to `run/compounds/` for adding to the mod.
5. Spawned guards are normal saved entities. Missing ones come back only on `/es compound reset` until the garrison rules arrive (Q6, S9).
6. Each module's ground line is found automatically: the highest layer that is mostly ground or water at the edges. A manual override is saved per module.
