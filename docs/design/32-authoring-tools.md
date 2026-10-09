# 32 — Authoring tools: structure viewer, zones, compounds ⏳ approved, in progress

> **2026-10-09.** The user's top priority: browse the builder's structures and turn them into playable compounds. This pulls the zone tool forward from S9 and the NPC spawner GUI forward from S19. Zone *gameplay* (alert levels, propagation, bells) stays in S9 (doc 18); this doc adds the tools, the data and a minimal trespass rule.

**Progress:** §5 steps 1 (structure viewer), 2 (compound format, save, place), 3 (zone tool and trespass rule) and 4 (the Muster Roll spawner and the Compound Ledger) are done; step 5, the first real compound, is in progress.

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

> **Implemented (2026-10-09):**
> - **Rope:** right-click a block for the first corner (a white box follows the crosshair), then the opposite corner: a new **restricted** zone `zone_<n>`, which the rope then edits. Sneak on the second corner to add the box to the rope's zone instead. Use in the air opens the Sumi **zone panel** (name, access, "only at hours" with From/To, Undo box, Delete, Save); sneak-use in the air drops a marked corner. Boxes are capped at 256 blocks an edge and 64 per zone. Names are `a-z 0-9 _ . -`; a placed compound's zones (`<compound>#<n>/<name>`) keep their names.
> - **Drawing:** while holding the rope, zones within 96 blocks are tinted volumes (public blue, restricted amber, hostile red), the rope's zone brighter and labelled with its rule and hours; inside a zone the tint is drawn both ways round.
> - **Commands:** `/es zone list | add <name> <access> <from> <to> [hours] | set <zone> <access> [hours] | remove <zone> | select <zone> | at [pos]`; `/es compound add zone <zone>` copies a rope zone into the open compound draft.
> - **Rule:** where zones overlap, the strictest whose hours hold wins (`Trespass`). Restricted multiplies awareness gain **and** the grace-period cap by 2, so detection really is twice as fast, and a guard entering curious or suspicious about someone it sees there barks a `trespass` warning. Hostile: once awareness reaches the "noticed" threshold, it jumps to detection, so darkness and cover still matter.
> - Code: `authoring/Zone`, `Zones`, `Trespass`, `ZoneTool`, `ZonePayloads`, `item/SurveyorsRopeItem`, `client/authoring/ZoneRenderer`, `ZoneEditorScreen`, `command/ZoneCommands`. Tests: `zones/*`.

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
  "routes": [{ "name": "wall_walk", "mode": "loop", "waypoints": [{ "pos": [4, 5, 4], "wait_ticks": 40, "look_yaw": 90 }] }],
  "spawns": [{ "archetype": "emergentstealth:ashigaru", "pos": [10, 1, 12], "facing": 180,
               "schedule": [{ "from": 6, "to": 18, "activity": { "type": "route", "route": "wall_walk" } },
                            { "from": 18, "to": 6, "activity": { "type": "post", "pos": [10, 1, 12], "yaw": 180 } }] }],
  "lights": { "relight": "all" }
}
```

> **Implemented (2026-10-09):** `structures`, `zones`, `routes` (the patrol route format: `wait_ticks`, `look_yaw`, `relight`) and `spawns` (plus optional `behaviour` and `count`). `lights` comes with the lamplighter in step 5. Every field but a module's `template` is optional; `offset`, `rotation`, `mirror` and `ground` default to none. Rotations are `none`, `clockwise_90`, `180`, `counterclockwise_90`; mirrors are `none`, `left_right`, `front_back`. Saved files write positions as `[x, y, z]` on one line. Code: `authoring/Compound`, `Compounds`, `CompoundPlacer`, `Transform`.

**Authoring flow** (built with commands first, 2026-10-09; the Compound Ledger item will drive the same steps):
- `/es compound start <name> [origin]` opens a draft at your last placed structure (it becomes the first module) or at `origin`. Every structure you place while the draft is open joins it.
- `/es compound add route <route>` copies a patrol route made with the baton; `add npcs <targets>` records NPCs as they stand (archetype, place, facing, behaviour, schedule); `add zone <zone>` copies a zone made with the Surveyor's Rope, and `add zone <name> <access> <from> <to> [hours]` adds a box zone.
- `/es compound info`, `save`, `cancel`. Then `place <name> [pos] [rotation] [mirror]`, `list`, `copies`, `export <name>`, `remove <copy>`. Undo (`/es structure undo`) takes back a whole placed copy.
- Not yet: re-saving edited templates on save, `reset`, automatic ground lines, and the browser listing compounds.

> **Compound Ledger (implemented 2026-10-09):** right-click with the ledger.
> - **No draft open:** a panel names and starts one. Used on the structure you last placed, the draft starts from it (its origin, and it becomes the first module); anywhere else, at that block. A bare name means `emergentstealth:<name>`.
> - **Draft open:** the panel lists the draft's zones and routes beside rope zones and baton routes within 96 blocks, each with **Add** or **Take out**, and the draft's spawns with **Take out** (their NPCs stay). **Save** writes the file (the ledger stays open); **Discard** (click twice) drops the draft.
> - **Linked markers:** zones and routes copied from the world (by the ledger, `add zone <zone>`, `add route`, or a muster's schedule) are copied again on every save, so later edits with the rope or baton are kept. A box zone typed with `add zone <name> <access> …` isn't linked.
> - **No double records:** NPCs a draft has recorded (mustered, or `add npcs`) are remembered, so adding them again does nothing.
> - Code: `authoring/Ledger`, `LedgerPayloads`, `item/CompoundLedgerItem`, `client/authoring/LedgerScreen`. Test: `authoring/ledger_flow`.

The planned flow:
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

> **Implemented (2026-10-09):** right-click a block with the Muster Roll; the NPCs stand on the block above the clicked face.
> - **Panel:** Archetype (cycle; the tooltip shows its role and faction), Behaviour (the archetype's own or any tree), Facing (eight directions, starting from where you look), Count (1-16), and a schedule of up to 8 windows. Each window has From and To hours (equal hours mean all day) and an activity: **Post here**, **Wander 4/8/16/32**, or **Route <name>** (the draft's routes first, then the 64 nearest world routes). The first window that holds the hour wins.
> - **Muster** always spawns the NPCs, so you see them working. With a compound draft open in that dimension, they also become one spawn marker (positions in compound space). Routes the schedule walks are copied into the draft and linked. Without a draft they're just NPCs (no "compound under the cursor": placed copies aren't edited).
> - **Server checks:** creative + operator, within 32 blocks, the archetype and tree exist, the count, at most 8 windows, posts within 32 blocks, and routes that exist.
> - Not done: the archetype colour swatch (archetypes have no colour yet; the tooltip shows the role instead).
> - Code: `authoring/MusterRoll`, `MusterPayloads`, `item/MusterRollItem`, `client/authoring/MusterRollScreen`. Tests: `authoring/muster_spawns`, `authoring/muster_joins_draft`.

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
