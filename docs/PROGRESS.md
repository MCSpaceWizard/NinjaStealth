# Progress report: 2026-10-08

Where Emergent Stealth stands, what's verified, and what comes next.

## At a glance

| | |
|---|---|
| Code | 122 Java classes, about 12,400 lines |
| Automated tests | **45 GameTests**, all passing, covering perception, light, visual lighting, navigation, routines, behaviour and sound |
| Branch | `claude/pensive-euler-7o41g4`, with everything below merged |
| Milestone M1 (vertical slice) | Stages S0–S6 are built. **S7 (player verbs) and S8 (slice assembly) remain** |

## Stage status

| Stage | What it gives you | Status |
|---|---|---|
| S0 Foundations | Build, configs, programmer-art pipeline, docs | ✅ verified by you |
| S1 NPCs & debug | Archetypes as data, layered outfits, debug lens | ✅ verified by you |
| S2 Sight & detection | Vision cones, partial cover, awareness meter, indicators, stingers | 🧪 built, waiting for your playtest |
| S3 Light & shadow | Ray-traced exposure, sun/moon shadows, light gem, snuffing, guards with torches | 🧪 built |
| S4 Behaviour core | JSON behaviour trees, search groups, attack tokens, barks, reactions to sound | 🧪 built |
| S5 Navigation & patrols | Doors, gates, ladders, Patrol Baton, routes, daily routines, lamplighter | 🧪 built |
| S6 Hearing & distractions | Sound through doorways and muffled by walls, footsteps by stance, masking, **throw any item (G)** | 🧪 built |
| Track E: visual lighting | What you see matches the guards' model: block-light shadows, held lights, sun/moon shadows, dark is dark; works with Sodium and Iris | 🧪 built |

**Checked in a real client here:**
- light gem and lit/unlit blocks
- patrol route drawing and patrolling
- light debug view
- visual shadows (vanilla, Sodium, Iris with Complementary)
- throwing and a guard investigating the impact
- barks, and guards surrounding and attacking

**Waiting on you:**
- playtests of S2–S6 and Track E (see [TESTING.md](TESTING.md))
- real-GPU FPS numbers for the visual lighting (rows V.11 and V.16)

## Fixed this round

- **Guards froze in combat on Peaceful.** Vanilla doesn't let mobs target players on Peaceful. Guards now just surround you there, and fight normally on Easy and above.
- **Server crash risk.** Sending our data to a player without the mod's network channel crashed the server. All payloads are now guarded.
- **GameTest players** were secretly invulnerable, which hid the bug above. They are now real survival players.

## Research done (decisions for you)

| Topic | Recommendation | Doc |
|---|---|---|
| Own shader pack | Not as the main fix: our lighting already shows through any pack. Maybe later as an optional look | [research-shader-pack](design/research-shader-pack.md) |
| NPC skins | No legally clean feudal skins exist. Commission samurai, taisho and daimyo; use CC0 cloth and Kenney designs for the rest | [research-skins](design/research-skins.md) |
| Animation (S7) | Player Animation Library for players; vanilla keyframes plus our own procedural pose layer for NPCs; no GeckoLib. Crawl, knockouts and dragging can be procedural | [research-animation](design/research-animation.md) |

## Next stages

### Recommended order

1. **S7 Player verbs I** (doc 17, next). Each item comes with its animation:
   - crawl toggle (procedural)
   - knockouts (never wake by themselves; woken by others, who remember)
   - rear and air takedowns (paired, keyframed)
   - body dragging and carrying (procedural)
   - evidence: bodies, stuck arrows, dropped weapons. Guards that find a body raise their alert.

   **Suggested split:**
   - A subagent builds the animation framework (pose layer, `NpcModel`, PAL adapter) on its own branch.
   - I build the server side: knockout state, takedown validation and sync, dragging, evidence perception, GameTests.
2. **S8 Slice assembly:**
   - smoke bomb (sight-blocking volume)
   - a pebble throwable
   - a test compound structure with patrols, lights and a lamplighter
   - a tuning pass

   That completes **M1: one compound, all core loops**.
3. Then M2, starting with **S9 zones and alarms**. Bells, runners and alerts that cool down over days build on S4's shouts and S6's sound.

### Questions to answer before S7

From the animation research:
1. Is PAL OK as a required client-side dependency (bundled jar-in-jar or separate)?
2. Is this split right: procedural for crawl, knockouts, dragging, breathing; artist keyframes for takedowns, climbing and combat?
3. Elbows and knees (a beta library), or strictly six-part blocky bodies?
4. First-person takedown camera: angle-only (no mixin) or following the head (needs a mixin)?
5. One animation export format for players and NPCs, or two exports from the same Blockbench file?

Still open from before: **Q5, factions** (is the player Shinobi, or are the Shinobi a rival clan?). Not needed until S9/S11.

## Risks and notes

- **Performance on real hardware is unmeasured.** Visual lighting re-bakes chunk sections when lights move or the sun steps. The settings in `visual_lighting` can turn each part down or off.
- **Art.** Everything is programmer art. Final skins need commissioning (see the skins research).
- **Conflicts:** LambDynamicLights hooks the same place as our dynamic lights, so don't run both. Shader packs with their own held-item glow can double up; `heldLightWithShaders` handles that.
