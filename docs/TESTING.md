# What to test

This is a living list of what's implemented and how to test it. It's updated with every stage. Newest stages are at the top.

**Status legend:**
- 🧪 **Ready to test.** Code is pushed; please build and play.
- ✅ **Verified.** You've confirmed it works.
- 🔧 **Fixing.** Issues were reported and a fix is in progress.
- ⏳ **In progress.** Not ready yet.

**Reporting:** for each item, a quick ✅/❌ plus what you saw is enough. Screenshots help a lot for anything visual. For crashes, send `run/logs/latest.log`.

**Before you start:** build per [GETTING_STARTED.md](GETTING_STARTED.md), run `./gradlew runClient`, and create a **Creative world with cheats on**.

---

## Track E: Visual lighting (shadows you can see, dynamic lights, dark is dark) 🧪

What you **see** now follows the same light model the guards use (design doc [30](design/30-visual-lighting.md)). Screenshots are in `docs/screenshots/visual-lighting/`.

**Automated checks:** 2 more GameTests (29 total). For every air cell of a test room, the light baked for rendering equals the gameplay light:
- a torch with a wall and glass
- a player holding a torch next to a wall

**Verified here in a real client** (software GL):
- vanilla renderer, **Sodium 0.9.2**, and **Sodium + Iris 1.11.4** with a shader pack
- shadows behind walls, a pillar's shadow updating live, held-torch light following the player, an NPC's torch, and dark-is-dark

**Setup:** night (`/time set 18000`; `/time set 114000` for a new moon), creative. Options are in Mods → Emergent Stealth → Config → **Visual Lighting**. Changes apply immediately (chunks rebuild).

| # | Feature | How to test | Expected |
|---|---|---|---|
| V.1 | Shadows behind walls | Place a torch 2 blocks from a 1-block-thick wall. Look at the ground on the far side | The far side is dark. In vanilla (turn off *Shadow-Casting Block Light*), light leaks around the wall's ends and glows along its foot |
| V.2 | Shadows match the light gem | Stand in the dark spot from V.1, in survival | The light gem is dark, and the ground you stand on looks dark too |
| V.3 | Live shadow updates | Place a 3-high pillar right next to a torch, then break it | A shadow wedge appears behind the pillar right away, and disappears when you break it |
| V.4 | Held torch | At night, hold a torch (either hand), lantern, soul torch or glowstone. Walk around | The ground and walls around you light up and the light follows you (in steps of about half a block). Put the torch away → dark again |
| V.5 | Held light casts shadows | Hold a torch next to a wall | The far side of the wall stays dark |
| V.6 | Other carriers | `/es npc spawn emergentstealth:ashigaru`, then `/item replace entity @e[type=emergentstealth:stealth_npc] weapon.offhand with torch`. Also set a mob on fire | Light moves with the NPC; the burning mob glows |
| V.7 | Dropped items (optional) | Turn on *Dropped Items Glow*, then drop a torch | It glows. Off by default, because guards don't count dropped torches |
| V.8 | Dark is dark | Video Settings → Brightness → **Bright**. Go into a dark cave or room | Still genuinely dark. Turn off *Dark Is Dark* (accessibility) → vanilla brightness is back |
| V.9 | Iris shader packs | `./gradlew runClient -PwithIris`, enable a shader pack | Torch shadows and held lights still show. Dark-is-dark steps aside (log: `Iris shader pack active: dark-is-dark paused`) unless *Dark Is Dark With Shader Packs* is on |
| V.10 | Sodium | `./gradlew runClient -PwithSodium`, then repeat V.1–V.5 | Same results as vanilla rendering |
| V.11 | Performance | Walk through a torch-lit area holding a torch, with ~20 torches around | No stutter on a normal GPU/CPU. If chunks lag behind, raise *Dynamic Light Update Interval* |
| V.12 | Dedicated server | `./gradlew runServer` | Starts normally (all visual code is client-only) |

**Known differences from gameplay** (doc 30 §9):
- sky and sun shadows aren't baked; that's vanilla sky light
- lava lakes and very dense light clusters use vanilla light
- light pools are round rather than diamond-shaped

## Stage 5: Navigation, patrols & routines 🧪

**Automated checks (already passing):** 6 more GameTests (27 total):
- a guard walks through a closed wooden door and closes it behind itself
- same through a fence gate
- a guard reaches a second floor by ladder
- a guard on a 3-waypoint loop visits them in order and comes back to the first
- a schedule picks the right activity for the time of day
- a `relight` waypoint relights an unlit torch (the lamplighter)

**Tip:** turn on the AI debug view (`/esdebug on`). Each NPC's label shows its current routine, and its remaining path is drawn as a cyan line.

| # | Feature | How to test | Expected |
|---|---|---|---|
| 5.1 | Default routines | Spawn an `ashigaru` and a `taisho` (captain) | The ashigaru wanders within ~8 blocks of where it spawned. The taisho stands at its spawn spot facing its spawn direction, glancing left and right now and then, and walks back there after any alert |
| 5.2 | Patrol Baton | Creative, op. Take the **Patrol Baton** from the creative tab. Right-click 3–4 blocks around a courtyard | Chat: `route_1: waypoint #0 at …`, `#1`, … While holding the baton you see the route: lines between waypoints and numbered markers |
| 5.3 | Remove a waypoint | Sneak + right-click any block with the baton | The last waypoint disappears from the drawing |
| 5.4 | Assign a route | Right-click an NPC with the baton | `Assigned route route_1 (all day)`. The NPC walks to the nearest waypoint, then follows the route in order at a walking pace, pausing briefly and looking around at each point. It loops back to #0 |
| 5.5 | Doors & gates | Put a wooden door or fence gate on the route (closed) | The guard opens it, walks through and **closes it behind itself** |
| 5.6 | Ladders | Make a waypoint on a platform reachable only by a ladder | The guard climbs up and down the ladder. Iron doors are walls for now |
| 5.7 | Waits & look direction | `/es patrol wait route_1 1 5` then `/es patrol look route_1 1` (faces where you're looking) | At waypoint #1 the guard stands for 5 s facing that direction. The drawing shows the wait and a look arrow |
| 5.8 | Ping-pong | `/es patrol mode route_1 pingpong` | The guard walks 0→1→2→3→2→1→0… instead of looping |
| 5.9 | Lamplighter | `/es patrol relight route_1 2 true`, then snuff the torches near waypoint #2 | When the guard reaches #2 it relights unlit torches and lanterns within 4 blocks |
| 5.10 | Shift changes | `/es patrol assign @e[type=emergentstealth:stealth_npc,limit=1,sort=nearest] route_2 18 6` (a night route; make `route_2` first with a new baton or `/es patrol select`) | Day: the NPC keeps its previous activity (newer windows take priority over older ones). Between 18:00 and 06:00 (`/time set 13000`) it walks route_2 instead. `/es routine show @e[…]` lists its schedule |
| 5.11 | Posts & wander | `/es routine post <npcs>` (stand here, facing your direction), `/es routine wander <npcs> 5`, `/es routine clear <npcs>` | The NPC holds the post / strolls within 5 blocks / goes back to its default |
| 5.12 | Back to the route after an alert | Let a patrolling guard notice you (meter past 50%), then hide until it calms down | It searches, then returns to the **nearest** waypoint and carries on |
| 5.13 | Persistence | Save and quit, reload | Routes (`/es patrol list`) and NPC schedules survive |
| 5.14 | Other commands | `/es patrol list`, `/es patrol select <route>` (binds the held baton, or gives you one), `/es patrol remove <route>` | As described |

**Known:** procedural patrol routes come with worldgen compounds (S15). Locked doors and keys come with the toolkit (S12).

## Stage 3: Light, shadow, light gem & snuffing 🧪

**Automated checks (already passing):** 6 more GameTests (21 total):
- a wall casts a shadow even though vanilla light leaks around it
- glass lets light through
- a sealed room is dark
- snuff/relight works, and glowstone can't be snuffed
- holding a torch lights you up
- darkness makes you much harder to spot

**Verified here in a real client:** lit and unlit torches and lanterns render correctly; the light gem glows near torches and goes dark away from them.

| # | Feature | How to test | Expected |
|---|---|---|---|
| 3.1 | Light gem | Survival. Walk from a lit area (torches, daytime) into darkness (night, caves) | The gem above the hotbar goes from glowing amber to near-black, smoothly |
| 3.2 | Real shadows | At night, put a torch on one side of a 1-block-thick wall and stand on the other side | The gem stays dark behind the wall, even though vanilla shows the ground there as lit (light leaks around corners in vanilla; not in our model) |
| 3.3 | Sun shadows | Daytime, stand right next to a tall wall on its shadow side in the morning/evening, then on the sunny side | Noticeably darker gem in the shade; shadows fall sideways in morning/evening, not just straight down |
| 3.4 | Moon phases | Night in the open on a full moon vs. a new moon (`/time set` across nights) | Full moon: dim but visible. New moon: very dark |
| 3.5 | Holding a torch | At night, hold a torch (either hand) | The gem lights up: you're carrying a light, and guards will spot you much faster |
| 3.6 | Darkness vs guards | At night, approach a guard in full darkness, then do the same in torchlight | In darkness the meter fills much more slowly (roughly 8× in pitch dark); in torchlight it's like daytime |
| 3.7 | Snuffing torches | Right-click a torch (standing, wall, soul or copper) with an **empty hand** | It goes out (fizz). It becomes an unlit torch with a charred tip, and the area goes dark |
| 3.8 | Snuffing lanterns | Empty-hand right-click a lantern (standing or hanging) | It becomes a dark unlit lantern |
| 3.9 | Campfires & candles | Empty-hand right-click a lit campfire; empty-hand right-click a candle | The campfire goes out; the candle goes out (vanilla) |
| 3.10 | Relighting | Right-click an unlit torch or lantern with flint & steel, a fire charge, or a torch item | It relights. Flint & steel takes durability; a fire charge is used up |
| 3.11 | Powered lights stay on | Empty-hand right-click glowstone, a redstone lamp, a sea lantern, a froglight | Nothing happens |
| 3.12 | Unlit drops | Break an unlit torch or lantern | It drops the normal torch or lantern |
| 3.13 | Guards fetch torches | At night, get a guard to investigate or search (let the meter pass 50%, then hide) | In the dark, the guard pulls a torch into its off-hand while searching, lighting itself and the area. It's put away once the guard calms down |
| 3.14 | Gem toggle | Config → HUD → Show Light Gem off | The gem disappears |
| 3.15 | Light debug view | `/esdebug light` (or **sneak + right-click** the Debug Lens), as op / singleplayer host | A floor heatmap around you: dark blue = hidden, purple = dim, yellow = lit. Your body points are coloured dots. A line from every nearby light to your chest: green = clear, yellow = partial, red = blocked (thin lines are carried lights). Above you: total exposure with blocks/sky/sky-access. An arrow toward the sun or moon (yellow = reaching you, grey = you're in its shadow) |
| 3.16 | Shadows in the debug view | Torch on one side of a wall at night, stand on the other side | The heatmap shows a dark "shadow" strip behind the wall while squares around its ends glow; the torch's line to you is red |

**Known:** vanilla doesn't *render* held-torch light or our shadows; the light gem is the truth (agreed in Q2). Visual dynamic lights and shadow rendering come later, in Track E.

## Stage 2: Sight, awareness & detection (+ reactions v0) 🧪

**Automated checks (already passing):** 14 GameTests cover cones, nothing-behind, stone/glass/leaves, crawling in grass, deep water, the grace period, decay, and hit reactions. Run them with `./gradlew runGameTestServer`, or in game with `/test runall emergentstealth`.

**Setup:** **survival** mode (NPCs ignore creative players; switch with `/gamemode survival`). Spawn a guard with `/es npc spawn emergentstealth:ashigaru` and a civilian with `/es npc spawn emergentstealth:townsfolk`. The Debug Lens view helps a lot here.

| # | Feature | How to test | Expected |
|---|---|---|---|
| 2.1 | Nothing behind | Walk up behind a guard (any speed) in daylight | No indicator and no reaction, even right behind it. Turn on the debug view: rays only appear when you're inside its cones |
| 2.2 | Central cone | Stand ~10 blocks in front of a guard in daylight, still | The above-head **"? ■■□□□"** meter fills white → yellow → orange. A **chime** plays at half, then a **red "!"** and a low **bell**, and the guard comes at you |
| 2.3 | Grace period | Sprint straight at a guard from the front | Even at point blank it takes ~0.6 s to go from nothing to "!" |
| 2.4 | Peripheral vision | Stand off to the side (~60°) at ~6–10 blocks | It fills much more slowly than straight ahead; beyond ~75° nothing happens |
| 2.5 | Movement matters | Same spot: stand still vs. walk vs. sneak vs. sprint | Sprint ≫ walk > still ≈ sneak (sneak is slower to notice) |
| 2.6 | Darkness (placeholder) | Repeat 2.2 at night or in a dark room | Much slower to fill (about 6× slower in full darkness). Proper shadows come with Stage 3 |
| 2.7 | Cover | Put glass, leaves, a fence and a stone wall between you and a guard | Glass: like open air. Leaves/fence: slower. Stone: nothing. Debug rays turn green/yellow/red |
| 2.8 | Crawling in grass | Crawl (lie under a trapdoor first; a crawl key comes later) inside tall grass in front of a guard | Hidden. Standing up in the same grass → visible |
| 2.9 | Curious / suspicious | Let the meter reach ~30%, then hide | The guard stops and looks toward where it saw you ("curious"). Past 50% it stares, then **walks over to investigate**, looks around, and gives up after a while |
| 2.10 | Last known position | Get detected ("!"), then break line of sight and move away | The guard runs to where it **last saw** you (debug: magenta box), not to where you are now. Then it searches nearby (orange "?") for ~25 s |
| 2.11 | Heightened alert | After a search ends, approach the same guard again | Its debug state is `heightened`; it notices you noticeably faster than a fresh guard |
| 2.12 | Melee hit | Hit an unaware guard from behind | It turns and fights immediately |
| 2.13 | Arrow hit | Shoot an unaware guard from hiding | It doesn't know exactly where you are: it gets suspicious and walks toward a spot roughly where the arrow came from |
| 2.14 | Civilians flee | Get detected by a townsfolk NPC, or hit one | It runs away instead of fighting |
| 2.15 | Detected → combat → lose them | Get detected, then escape around corners | Combat while it sees you, hunting once you vanish, searching, then back to calm(ish) |
| 2.16 | Debug view | Debug Lens on | Labels show `state`, `tier`, `awareness`. Rays to your body points (green/yellow/red). Magenta box at your last known position |
| 2.17 | HUD toggles | Mods → Emergent Stealth → Config → HUD: turn off detection indicators / alert sounds | Indicators and sounds stop |
| 2.18 | Co-op: no free info | `runServer` + 2 clients. Player A gets detected by a guard while B stays hidden nearby | B sees no indicator for that guard (it's not aware of **B**), though B can watch it chase A |
| 2.19 | Lots of NPCs (perf) | Spawn ~40 guards (`/es npc spawn` repeatedly) around you | No noticeable lag. In debug, the closest ~20 show `tier 1`, the rest `tier 2` |

**Tuning:** everything above is adjustable.
- Server config: `world/serverconfig/emergentstealth-server.toml` (perception tiers and budget, thresholds, search time, a global gain multiplier).
- Datapack perception profiles: `data/emergentstealth/emergentstealth/perception_profile/*.json` (cones, ranges, gain, decay, grace).

Tell me what feels too easy or too hard and I'll retune the defaults.

## Stage 1: Stealth NPCs & debug view ✅

Verified by you on 2026-10-06. Rows 1.6, 1.9 and 1.10 behave differently since Stage 2; see the notes in those rows.

| # | Feature | How to test | Expected |
|---|---|---|---|
| 1.1 | Creative tab | Open the creative inventory and find the **Emergent Stealth** tab | Contains the **Debug Lens** (magnifier icon) and the **Stealth NPC Spawn Egg** |
| 1.2 | Spawn egg | Use the spawn egg on the ground | An NPC in red-lacquer ashigaru armour, holding an iron sword, appears |
| 1.3 | Spawn command + archetypes | `/es npc spawn emergentstealth:` then press **Tab** | Suggests the six archetypes: `ashigaru`, `samurai`, `taisho`, `townsfolk`, `labourer`, `daimyo` |
| 1.4 | Archetype looks | Spawn one of each | Visibly different outfits (placeholder art): dark samurai armour, red/gold taisho, beige townsfolk kimono, straw-hatted labourer, purple daimyo. Skin tones vary between NPCs of the same type |
| 1.5 | Archetype stats | Hit a samurai and an ashigaru with the same weapon | The samurai takes more hits (40 hp vs 24 hp) |
| 1.6 | Idle behaviour | Stand **behind** them | They wander slowly and look around. *(Since Stage 2 they no longer turn to look at you unless they actually perceive you.)* |
| 1.7 | List command | `/es npc list` | A total count plus a count per archetype |
| 1.8 | Debug view toggle | Right-click the **Debug Lens** (or type `/esdebug`) | Action-bar message "debug view: ON" |
| 1.9 | Debug labels | With debug on, look at NPCs | Above each NPC, 3 lines: `archetype [role]`, `state: … tier N`, `awareness: 0.00` *(Stage 2 format)* |
| 1.10 | Debug vision cone | With debug on | Red facing line, yellow central cone, blue-ish flat peripheral fan; all follow the head *(Stage 2: real sizes per NPC type)* |
| 1.11 | Debug off | `/esdebug off` or right-click the lens again | Overlays disappear |
| 1.12 | Persistence | Spawn a few NPCs, then save & quit and reload the world | Same NPCs, same outfits, skin tones and gear. They don't despawn when you walk far away and come back |
| 1.13 | Multiplayer: debug is op-only | `runServer` + `runClient` + `runClient2`. Op only one player (`/op Dev` in the server console). Both toggle debug | The op sees the overlay; the non-op sees **nothing** even with it toggled on |
| 1.14 | Multiplayer: NPCs sync | Same setup, both players near the NPCs | Both see the same outfits and movement |
| 1.15 | Datapack override (optional) | Copy `src/main/resources/data/emergentstealth/emergentstealth/archetype/ashigaru.json` into a world datapack at the same path (`<world>/datapacks/test/data/emergentstealth/emergentstealth/archetype/ashigaru.json`, plus a `pack.mcmeta`). Change `max_health` or `outfit`, then **save & quit and reopen the world** (archetype/outfit files load with the world; `/reload` doesn't pick them up) and spawn a new ashigaru | The new NPC uses the changed values |
| 1.16 | Texture swap (optional) | Replace `textures/entity/npc/outfit/ashigaru_armor.png` with any 64×64 skin PNG, then **F3+T** | The ashigaru armour layer shows the new texture |

## Stage 0: Project scaffold ✅

| # | Feature | How to test | Expected |
|---|---|---|---|
| 0.1 | Build | `./gradlew build` | `BUILD SUCCESSFUL` |
| 0.2 | Mod loads | `./gradlew runClient` → Mods screen | "Emergent Stealth" is listed |
| 0.3 | Config screen | Mods → Emergent Stealth → Config | Opens; has a HUD section with "Show Light Gem" (does nothing until Stage 3) |
