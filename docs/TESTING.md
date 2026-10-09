# What to test

This is a living list of what's implemented and how to test it. It's updated with every stage. Newest stages are at the top.

**Status legend:**
- 🧪 **Ready to test.** Code is pushed; please build and play.
- ✅ **Verified.** You've confirmed it works.
- 🔧 **Fixing.** Issues were reported and a fix is in progress.
- ⏳ **In progress.** Not ready yet.

**Reporting:** for each item, a quick ✅/❌ plus what you saw is enough. Screenshots help a lot for anything visual. For crashes, send `run/logs/latest.log`.

**Overview:** see [PROGRESS.md](PROGRESS.md) for where things stand.

**Before you start:** build per [GETTING_STARTED.md](GETTING_STARTED.md), run `./gradlew runClient`, and create a **Creative world with cheats on**.

---

## Sumi UI framework: config screen, skill tree (K), dialogue preview 🧪

Design: [doc 31](design/31-ui-framework.md) §0. Geometric shapes drawn in code, procedural paper grain and brushed ink, eased animation, inertial scrolling and swipeable tabs. Screenshots: [docs/screenshots/ui/](screenshots/ui/).

**Automated checks (already passing):** 11 GameTests (`ui/*`, 75 total):
- easing curves and tweens (retargeting mid-flight, zero duration snaps)
- inertial scroll (wheel glide, fling, rubber band, instant with reduced motion)
- swipe pager (snap distance, flick, edge resistance)
- layout maths (flex, offsets, grid)
- theme JSON parsing and fallbacks (single-token overrides, bad colours, layering)
- config spec to widget mapping (tabs, toggles, sliders, slider rounding)
- the skill tree model agrees with the server's unlock rules; keyboard neighbours
- typewriter timing, paper noise tiling
- `/es dev dialogue` is registered and only messages modded clients
- `en_us.json` parses strictly (a merge once broke it and every mod text showed as a raw key)

| # | Feature | How to test | Expected |
|---|---|---|---|
| U.1 | Config screen | Mods → Emergent Stealth → Config (or `/esui config` in a world) | A paper sheet with a vermilion seal. Sidebar tabs grouped **Client** (HUD, Visual Lighting, Interface) and **Server** (Perception, Detection, Behaviour, Sound). Toggles, sliders and values for every option |
| U.2 | Motion | Click between tabs; drag a page sideways; scroll a long tab with the wheel and by dragging | Pages slide and snap; the ink marker glides along the sidebar. Scrolling glides, a quick drag flings and coasts, the ends rubber-band. Rows slide in one after another |
| U.3 | Editing | Change a toggle and a slider (drag, click, or focus with Tab and use the arrows; Shift = ×10) | The value changes at once; it's saved when you close (check `run/config/emergentstealth-client.toml`). **Reset tab** restores defaults. Hovering a row shows its comment, default and range |
| U.4 | Server values | Open the config in your own singleplayer world; then on a server or a LAN-opened world | Editable in singleplayer. On a server (or LAN) server tabs are read-only, with a note in the footer. From the main menu: "Join a world to see these settings" |
| U.5 | Classic list | Footer **Classic list** | NeoForge's standard config screen (fallback) |
| U.6 | Skill tree | Press **K** (Controls → Emergent Stealth). `/es skills points @s shinobi 3` first | Two paths, **Shinobi** (ink) and **Shogunate** (vermilion). Seals at their grid places with brush-stroke prerequisites; dashed lines for missing ones. Learned seals are inked; buyable ones glow gold with a red ring; capstones are gold hexagons. The red seal top-right shows unspent points; the bar shows Insight towards the next point |
| U.7 | Hover card | Hover a seal | A paper card: name, description, effects (e.g. "Footsteps -15%"), cost, prerequisites, and a status line (Learned / Click to learn / Not enough points / Learn a prerequisite first / Mastery challenge) |
| U.8 | Unlock | Click a glowing seal | A spinner while the server answers, then the seal is stamped in ink with a sound and the point counter drops. Clicking a seal you can't buy shakes it. With the server rejecting (e.g. no points) nothing changes |
| U.9 | Navigation | Wheel to zoom (around the cursor), drag to pan when zoomed in; drag sideways or Q/E to switch path; Tab, then arrows + Enter | Smooth zoom and pan; the path tabs follow the swipe. Keyboard focus shows a rotating dashed ring and the card |
| U.10 | Dialogue preview | `/es dev dialogue` (op) or `/esui dialogue` | A dialogue box at the bottom: a guard portrait, his name on a seal, typewriter text. Click/Space finishes the line; then up to 4 choices with number keys 1-4 (or click, or arrows + Enter). Choice 3 leads to a second exchange |
| U.11 | Accessibility | Config → Interface: **High Contrast**, **Reduced Motion**, **Dialogue Text Speed** (0 = instant). Reopen a screen | High contrast: brighter paper, black text, stronger lines. Reduced motion: no slides, glides or flings. GUI scale follows vanilla |
| U.12 | Theming | Resource pack with `assets/emergentstealth/ui/theme.json` overriding e.g. `"colors": {"paper": "#f4efe2"}`, or `textures/gui/sumi/paper.png` | Only that token changes; everything else keeps the defaults. A paper PNG replaces the generated grain |
| U.13 | Dedicated server | `./gradlew runServer` | Starts normally (no client classes on the server) |

## Structure viewer: browse, preview, place, undo 🧪

Design doc [32](design/32-authoring-tools.md) §1. Browse every structure (ours first, then vanilla), see a translucent ghost of it in the world, and place it. Needs **creative mode with cheats** (the structure block rule). Screenshots: [docs/screenshots/structures](screenshots/structures) (`viewer_*.png`).

**Automated checks (already passing):** `structures/place_and_undo` (a rotated placement covers the right box, undo restores every block and block entity, takes back water that flowed out and drops no items, one level only), `structures/preview_data` (the preview has no air and splits into parts that join back exactly).

| # | Feature | How to test | Expected |
|---|---|---|---|
| V.1 | Open the browser | `/give @s emergentstealth:surveyors_plan`, then use it (or `/es structure browse`) | The Structures panel: a count, a search box (already focused), structures grouped by folder |
| V.2 | Search and details | Type `teahouse`, press Enter | The row is selected; the right shows its name, folder, size, solid block count and its 5 main blocks |
| V.3 | Ghost | Press Preview (or double-click the row) and look at the ground | A translucent copy coloured by block, with an outline box, starting at the block you look at and running away from you. The hint above the hotbar shows the name, rotation, mirror, height and origin |
| V.4 | Adjust | Scroll the mouse wheel, press M, press Page Up / Page Down (Shift for 5) | It turns 90° per notch, mirrors, rises and sinks; the hint follows |
| V.5 | Place | Use (right-click) | The structure appears exactly where the ghost was; chat says where |
| V.6 | Undo | `/es structure undo` or "Undo last" in the browser | Everything is back as it was, including water that flowed out of the structure. No items left lying around. A second undo says there's nothing to undo |
| V.7 | Cancel | Start a preview, then attack (left-click) | The ghost goes away and nothing is placed |
| V.8 | Commands | `/es structure list cherry`, `/es structure place emergentstealth:edo/kofun ~ ~ ~ clockwise_90` | Lists matching ids; places rotated (undo works for it too) |
| V.9 | Permission | In survival, use the plan | "Authoring tools need creative mode and operator permission", nothing opens |

## Builder structures and Cherry Grove modules (imported) 🧪

The builder's 21 structures, imported with vanilla stand-ins for their modded blocks, and 17 Cherry Grove modules (vanilla, from schematics) (see [tools/structures](../tools/structures/README.md)). Placement only: they don't generate in new worlds yet. Screenshots: [docs/screenshots/structures](screenshots/structures).

**Automated check (already passing):** `structures/imports_load`: all 38 load, no block turned into air (it names any that do), no spawners, every chest's loot table exists.

| # | Feature | How to test | Expected |
|---|---|---|---|
| B.1 | Place one | `/place template emergentstealth:edo/mini_castle ~ ~ ~` (also `buddhist_temple`, `samurai_mini_fort`, `onsen`, `kofun`, `small_shrine_v1`…`v6`, the statues) | It appears complete: dark wood, deepslate-tile roofs, stone bases, white plaster, red shrine timbers, lanterns lit |
| B.2 | Jigsaw version | `/place structure emergentstealth:edo/small_shrine_v3` | Placed on the surface with the terrain blended round it |
| B.3 | Contents | Open chests in a shrine and a fort | Placeholder loot (paper, candles, food; rarely smoke bombs, caltrops or tabi). No illagers in the mini fort, no spawners in the kofun or cemetery |
| B.4 | Look | Walk round a few | Report stand-ins that look wrong; `tools/structures/block_map.json` is the table to change |
| B.5 | Cherry Grove | `/place template emergentstealth:cherrygrove/pagoda ~ ~ ~` (also `teahouse`, `gatehouse`, `grandestate`, `twinpavilion`, …) | Complete buildings with chains, lanterns and cherry trees. Each brings its own ground and water, which stands above flat land when placed at ground level |

## Beta toolkit, part A: pebble, smoke bomb, firecracker, blinding powder, caltrops 🧪

**Automated checks (already passing):** 8 more GameTests (64 total):
- smoke between a guard and a player blocks sight completely, and a guard inside the cloud is blinded
- smoke stops blocking once it expires
- Thick Smoke (skill) makes clouds last 15 s instead of 10
- a firecracker draws a guard to it by sound alone
- blinding powder blinds an NPC in its cone but not one outside it
- caltrops slow and hurt
- quick use (V) keeps the held item
- a pebble makes a noise at the landing spot and leaves no trace

All five are in the creative tab and the `#emergentstealth:tools` tag. The cheapest way to try them is in creative: put a stack of each in your inventory.

| # | Feature | How to test | Expected |
|---|---|---|---|
| A.1 | Tool wheel | Hold **R** with some tools in your inventory, point at one, release | A radial wheel of the tools you carry, with counts. The chosen one shows as the active tool icon near the hotbar. With no tools: "No stealth tools in your inventory" |
| A.2 | Quick use | With a sword in hand and an active tool, **tap V** (lob) or **hold V** and release (charged throw) | The tool is thrown/used; you still hold the sword. When you run out: "No *tool* left" |
| A.3 | Pebble | Throw one past a calm guard | A clack where it lands; the guard investigates **the landing spot**, not you. Nothing is left behind |
| A.4 | Smoke bomb | Throw one between you and a guard who can see you | A pop, then a grey cloud (3-block radius) for 10 s. The guard loses sight of you completely. A guard standing in the cloud is blinded. `/esdebug` shows the smoke volume |
| A.5 | Firecracker | Throw one far from you, behind a guard | 2 s of fizzing, then 3 s of bangs. Guards come to the firecracker, not to you |
| A.6 | Blinding powder | Use it facing a guard within 4 blocks | Guards in the 60° cone are blinded for 6 s: they stagger, slow down and turn at random, but still hear you. A guard beside the cone isn't affected |
| A.7 | Caltrops | Throw them on a path a guard walks | A 2×2 patch for 60 s. Stepping in: 1 damage and Slowness. Guards path around them when they can |
| A.8 | Recipes | Check the recipe book | Pebble ×4: gravel. Smoke bomb: gunpowder + paper + charcoal. Firecracker: paper + gunpowder. Blinding powder: glowstone dust + bone meal + paper. Caltrops: 3 iron nuggets |
| A.9 | Thick Smoke | `/es skills unlock @s emergentstealth:thick_smoke`, then throw a smoke bomb | The cloud lasts 15 s |

## Progression foundations: skills, techniques, stealth gear 🧪

**Automated checks (already passing):** 4 more GameTests (56 total):
- skills and gear stack on stealth stats (and metal armour is louder)
- unlock rules (points, prerequisites, capstones)
- Insight becomes skill points
- Still Breath: works only crouched and still, with cooldown

The skill tree **screen** is on **K** (see the Sumi section above). The commands below still work for testing.

| # | Feature | How to test | Expected |
|---|---|---|---|
| P.1 | Insight | Survival: knock out or kill guards **unseen**, distract a calm guard with a thrown item, escape a search, hide a body nobody finds for 5 minutes | "New skill point" on the action bar every 100 Insight (knockout 15, kill 10, body hidden 10, distraction 3, escape 20). Creative earns nothing |
| P.2 | Skill commands | `/es skills points @s shinobi 10`, `/es skills unlock @s emergentstealth:soft_soles`, `/es skills reset @s` | Points given; skill unlocked (the unlock command grants the cost for testing); everything cleared |
| P.3 | Skills work | Unlock `soft_soles` and sprint past a guard behind you; compare with before | Heard noticeably less often (footsteps 15% quieter). `quiet_hands`: chokes are faster. `strong_back`: faster dragging. `iron_resolve`: +2 hearts |
| P.4 | Techniques | Unlock `still_breath`, `light_step`, `feint`. Press **X** to use, **sneak + X** to switch | The selected technique and its cooldown show right of the light gem (green while active). Still Breath: crouch and stay still, 40% harder to see. Light Step: 6 s of silent footsteps. Feint: a noise where you look (a guard investigates it) |
| P.5 | Stealth gear | Craft (or take from creative) Shinobi Hood, Garb, Hakama, Tabi | Black/indigo cloth armour. Tooltips show stealth stats (e.g. Footsteps -20%). Iron/diamond/netherite armour shows nothing but makes footsteps 20% louder per piece |

## Stage 7 animation: crawl, bodies, dragging, carrying, takedowns 🧪

Needs **Player Animation Library** and **Bendable Cuboids** on the client (`./gradlew runClient` includes both). Everything is procedural except the takedowns, which play programmer-art clips (`assets/emergentstealth/player_animations/takedowns.json` for you, `neoforge/animations/entity/takedown/*.victim.json` for the victim; `tools/programmer_art/takedown_clips.py` writes them). Screenshots: [docs/screenshots/animation](screenshots/animation).

**Automated checks (already passing):** 6 more GameTests for the shared maths: two-bone IK reaches its targets, crawling hands stay planted, the drag chain keeps its shape on the floor, the action clock seeks for late viewers, pose blending, stable per-body poses.

**Test helpers:** `/es npc knockout <targets>` makes a knocked-out body. `/esphoto <yaw> [pitch]` swings the third-person camera round you (try 90) so you can watch your own animations from the side; `/esphoto off` stops it.

| # | Feature | How to test | Expected |
|---|---|---|---|
| A7.1 | Elbow crawl | Press **Z** and crawl around (third person, `/esphoto 90`) | Elbows and knees bend. Hands stay planted while you pull past them, the opposite knee pushes out to the side. Standing still, the limbs rest |
| A7.2 | Bodies lie down | Kill a guard, and `/es npc knockout` another | Each falls over in about half a second and lies flat on the ground, centred on where it stood, fitted to slopes and steps. Every body lies a little differently. Chokes fall on their backs, kills face down |
| A7.3 | Knocked out vs dead | Watch both bodies closely | The knocked-out one breathes slowly (chest rises, arms ease out); the corpse doesn't move. Bodies show no detection indicator |
| A7.4 | Dragging | Right-click a body, walk around, turn, go up a step | Your right hand reaches back and holds the wrists; you lean into the pull. The body is face up, arms overhead, hips and legs trailing along the floor behind you |
| A7.5 | Carrying | Sneak + right-click a body | The body lies across your shoulders, arms and legs hanging, swaying as you walk; both your hands steady it. In first person it's hidden (it would fill the view) |
| A7.6 | Choke | Rear takedown with an empty hand (third person and first person) | You wrap an arm round the neck and pull back; the victim claws at your arm and kicks, then goes limp and falls on its back. In first person the camera looks down over its shoulder with a struggle shake, the view doesn't zoom, mouse look is damped |
| A7.7 | Rear kill | Left-click from behind | Grab with the left hand, strike with the right (0.75 s); the victim jerks and drops |
| A7.8 | Air takedown | Drop onto a guard from 2+ blocks | A deep landing crouch, the camera dips; the victim is crushed down |
| A7.9 | Skill speed | Unlock `quiet_hands`, then choke | The whole clip plays faster to match the shorter choke, staying in sync |
| A7.10 | Breathing and lean | Stand still, sprint, turn sharply (NPCs too) | Subtle breathing, heavier after sprinting; a slight lean into turns |
| A7.11 | Other players | On a server with a second player | They see your crawl, drag, carry and takedowns the same way (late joiners seek into a running takedown) |
| A7.12 | Settings | The client config, `animation` section (in game or `run/config/emergentstealth-client.toml`) | Each part can be turned off: procedural crawl, bodies, breathing, lean, player idle animation, takedown camera, mouse damping, animation range |

## Stage 7: Crawl, takedowns, bodies & evidence 🧪

**Automated checks (already passing):** 7 more GameTests:
- rear takedown rules (behind yes, front no, not on a guard fighting you) and the choke knocking out
- a knocked-out guard is woken by a colleague and comes back on alert
- a corpse raises the alarm
- a corpse behind a wall goes unnoticed
- a dragged body follows and slows you
- crawl stance (and no standing up under a low ceiling)
- air takedown

Animations for this stage are in the next table (S7 animation).

| # | Feature | How to test | Expected |
|---|---|---|---|
| 7.1 | Crawl | Press **Z** | You drop to vanilla's crawl: low camera, slow, very quiet (footstep loudness 1). Press Z again to stand; under a 1-block ceiling you can't ("No room to stand up"). Swimming, ladders or flying stand you up |
| 7.2 | Crawl in grass | Crawl through tall grass near a guard | Much harder to spot than walking through it |
| 7.3 | Takedown prompt | Sneak up behind an unaware guard (within 2 blocks) | Above the hotbar: `[RMB] Knock out   [LMB] Kill` (with an item in hand only Kill is offered). No prompt from the front or when it's onto you |
| 7.4 | Choke (non-lethal) | Right-click with an empty hand from behind | You're snapped behind it and both of you are held for 2 s, then it drops, knocked out. A faint struggle noise (loudness 4) |
| 7.5 | Rear kill | Left-click from behind | A quick kill (0.75 s); it becomes a corpse and drops its gear |
| 7.6 | Interrupted choke | Have a second guard hit you during a choke | The choke breaks and the victim fights back |
| 7.7 | Air takedown | Drop onto a guard from 2+ blocks (sneaking or empty-handed: knock out; holding a sword or axe, not sneaking: kill) | It goes down, and you take **no fall damage** |
| 7.8 | Elites | Try a rear takedown on a `samurai` | Not offered (only air takedowns work on elites) |
| 7.9 | Bodies stay | Kill or knock out a guard | The body stays on the ground (corpses forever for now). Hitting a knocked-out NPC kills it |
| 7.10 | Drag & carry | Right-click a body: drag. Sneak + right-click: shoulder carry. **Any right-click** while moving a body: put it down | Dragging: the body slides behind you, ×0.75 speed, no sprint, silent. Carrying: ×0.6 speed, the body rides your shoulder, works on ladders |
| 7.11 | Bodies are evidence | Leave a corpse where a patrol will see it (lit area) | The guard barks "A body! Raise the alarm!", shouts (others come), hunts to the body and searches around it. Hide bodies in the dark or out of sight and they aren't found |
| 7.12 | Waking | Leave a knocked-out guard where another will see it | The other walks over ("Hey! Wake up!"), wakes it after a moment, and both search on alert ("Ugh... someone jumped me!") |
| 7.13 | Other evidence | Shoot an arrow into a wall near a patrol, or drop a sword | A guard who sees it becomes curious ("What's this doing here?") and investigates |

## Stage 6: Hearing & distractions 🧪

Guards now **hear** (design doc [16](design/16-sound.md)). Sound goes round corners and through open doors, and walls muffle it. Your footsteps depend on how you move. Rain, thunder, running water and music mask noise, and you can **throw any item** to distract. Screenshots are in `docs/screenshots/sound/`.

**Automated checks (already passing):** 8 more GameTests (44 total):
- a stone wall muffles a noise, while the same noise is heard through an open doorway
- sprinting is heard where sneaking isn't
- rain masks a footstep that's heard in clear weather
- a thrown item landing behind a guard is heard
- opening a chest is silent; opening a door is heard and gives you away
- glass shatters and leaves nothing; stone drops where it lands
- over the per-tick node budget, a noise still arrives a few ticks later
- a worst-case timing check

**Verified here in a real client:** the throw key (tap and hold), creative keeps the item, survival uses one up, glass shatters, the noise rings and hearing lines, and a guard investigating a glass impact behind it ("What was that noise?", `heard: impact 0.64`).

**Tip:** `/esdebug on` shows the noise view: a ring at each noise, sized by its loudness, for 2 seconds. The ring is red if the noise gives a player away, yellow if it only points at a spot. Lines go to every NPC that heard it, coloured from blue (barely) through green to red (loud and clear), labelled with the kind, the intensity and the propagation cost. Guards ignore creative players, so test footsteps and doors in **survival** (`/difficulty peaceful` keeps mobs away; guards still fight back).

| # | Feature | How to test | Expected |
|---|---|---|---|
| 6.1 | Footsteps by stance | Survival, `/esdebug on`. Walk, sprint and sneak around a guard that faces away | Footstep rings every ~1.5 blocks: sneak 2, walk 6, sprint 12 (×1.25 on gravel/metal/glass, ×0.8 on wool/carpet/moss/snow/grass). A sprint behind it makes it turn and come to check; sneaking right behind it doesn't |
| 6.2 | Landing | Jump down 3–4 blocks near a guard; then the same while sneaking | A landing ring (4 + 1.5 × fall, max 16). Sneaking halves it. Stepping down stairs or slabs makes none |
| 6.3 | Walls and doorways | Build a stone room with a wooden door. Stand a guard inside, then open and close the door, or sprint outside | The noise line bends round through the door with a higher cost. A closed door muffles a little (+3), stone a lot (+8), wool most (+16) |
| 6.4 | Silent actions | Open a chest or barrel, drop items, shoot a bow, eat | No ring at all |
| 6.5 | Doors, blocks, combat | Open a door, break and place blocks, hit a pig, near a guard | Rings: door 8, break 10, place 6, hits and deaths 10. Your own doors and hits are red (they give you away) |
| 6.6 | Throw key | Hold any item and tap **G** (Controls → Emergent Stealth) | A short lob. **Hold** G to charge (a bar shows), release for a long throw. 0.5 s cooldown. Survival uses one item; creative doesn't |
| 6.7 | Distraction | Throw cobblestone behind a guard that faces away | Yellow "impact 10" ring and a line to the guard. It turns, says something like "What was that noise?", and walks over to look (S4) |
| 6.8 | Glass and sharp items | Throw glass, a pane or a glass bottle; then a sword at a pig | Glass shatters with particles and sound and leaves nothing (impact 14). The sword nicks the pig (1 damage), then drops. Everything else drops where it lands |
| 6.9 | Masking | `/weather rain`, then `/weather thunder`; also stand by a waterfall or a playing jukebox | A footstep that was heard at the edge of range no longer is. Rain masks 25%, thunder 50%, flowing water within 6 blocks 30%, a jukebox within 16 blocks 40% (they combine) |
| 6.10 | Snuffing fizz | Snuff a torch (empty hand) right next to a guard | A tiny ring (2): a guard right beside you may glance over, one further away doesn't |
| 6.11 | Datapacks | Copy `data/emergentstealth/emergentstealth/noise_source/block_open.json` into a datapack, set `"loudness": 0`, restart the world | Doors are silent. Tags `emergentstealth:muffles_sound_light/heavy`, `loud_surfaces`, `quiet_surfaces`, `shatters_on_impact` and `sharp_throwables` retune blocks and items |
| 6.12 | Multiplayer | `runServer` + `runClient` + `runClient2`; both players throw and walk near a guard | Throws and noises work for both; only ops see the noise rings |

## Stage 4: Behaviour core (search groups, attack tokens, barks, behaviour trees) 🧪

**Automated checks (already passing):** 5 more GameTests:
- a custom behaviour tree written as JSON drives an NPC
- a quiet noise makes a guard curious; a loud one makes it investigate the spot
- a shout brings a second guard who saw nothing
- two searching guards split up and search longer than one
- with three guards on one target, two attack and the third holds the ring

**Tip:** `/esdebug on`. NPC labels now show `cause:` (seen/heard/hurt/shout), `bt:` (what the behaviour tree is doing) and `heard:`. Search points appear as boxes: the guard's own point is tall and orange, the others small and grey.

| # | Feature | How to test | Expected |
|---|---|---|---|
| 4.1 | Barks | Get noticed by a guard, then hide | Short lines above its head: "Hm?", "Someone there?", "Intruder!", "Where did they go?", "Must have been the wind...". Civilians cry "Help! Guards!". Client config `showBarks` turns them off |
| 4.2 | Shouts bring help | Two guards 15–20 blocks apart, one facing away. Get spotted by the first | The first shouts. The second walks over to **where the shout came from**, not to you, and only fights you once it sees you |
| 4.3 | Coordinated search | Get 2–3 guards hunting you, then break line of sight and hide | They spread out to different spots, favouring dark corners, bushes and cover. With more guards the search lasts longer (25 s alone, about 32 s with two, 40 s with three or more) |
| 4.4 | Attack tokens | Fight 3–4 guards at once (survival, **not Peaceful**: like vanilla mobs, guards never attack players on Peaceful and just surround you) | Only two attack at a time; the others stand 4–6 blocks away around you, on the far side, facing you. When an attacker is badly hurt or loses sight of you, someone else steps in |
| 4.5 | Noises draw guards | Throw an item behind a guard (**G**, Stage 6) | Something heard faintly makes it turn and look; a closer or louder impact makes it walk over, look around, and bark "What was that noise?" |
| 4.6 | Heard, not seen | Sprint past behind a guard (it can't see behind) | It turns around and comes to check where it **heard** you (Stage 6 footsteps). Hearing alone never fully detects you |
| 4.7 | Custom behaviour | Copy `data/emergentstealth/emergentstealth/behaviour/guard.json` into a datapack as `mypack:behaviour/x.json`, change it (e.g. remove the `shout`), `/reload`, then `/es npc behaviour @e[type=emergentstealth:stealth_npc,limit=1,sort=nearest] mypack:x` | That NPC follows your tree. `/es npc behaviour <npcs> reset` restores the default |

## Track E: Visual lighting (shadows you can see, dynamic lights, dark is dark) 🧪

What you **see** now follows the same light model the guards use (design doc [30](design/30-visual-lighting.md)). Screenshots are in `docs/screenshots/visual-lighting/`.

**Automated checks:** 4 GameTests (31 total with Stage 5). For every air cell of a test room, the light baked for rendering equals the gameplay light:
- a torch with a wall and glass
- a player holding a torch next to a wall
- sun 30° high in the east and in the west, and overhead (fixed sun positions, the world's time isn't touched)
- a full moon and a new moon

**Verified here in a real client** (software GL):
- vanilla renderer, **Sodium 0.9.2**, and **Sodium + Iris 1.11.4** with a shader pack
- shadows behind walls, a pillar's shadow updating live, held-torch light following the player, an NPC's torch, and dark-is-dark
- sun shadows at morning/noon/evening (vanilla and Sodium), a full-moon shadow, and the light gem agreeing with the shade; with Complementary the pack's sun shadow replaces ours

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
| V.13 | Sun shadows move | Build a tall wall running north–south. `/gamerule advance_time false`, then `/time set 1500` (morning), `6000` (noon), `10500` (evening); wait a few seconds after each | Morning: a soft shadow on the **west** side. Noon: almost none. Evening: on the **east** side. Turn off *Sun & Moon Shadows* → vanilla (no shadow) |
| V.14 | Sun shadow ↔ light gem | Survival, morning: stand in the wall's shadow, then step into the sun | Gem dim in the shadow, lit in the sun; the ground, wall face and your hand look darker in the shadow too |
| V.15 | Moon shadows | Full-moon night (`/time set 14500` on day 0), same wall | Moonlit ground on one side, a darker moon shadow on the other. On a new moon (`/time set 110500`) there's no moon shadow |
| V.16 | Sun moving live | `/gamerule advance_time true`, stand near the wall for a minute | The shadow creeps in small steps (every ~2° of sun, ~7 s); no stutter. Slower PCs: lower *Sun Shadow Sections Per Tick* |
| V.17 | Shader packs + sun | With an Iris pack | The pack's own sun shadows; ours pause (no double shadows). Your own held torch uses the pack's glow (no double glow) unless *Own Held Light With Shader Packs* is on |

**Known differences from gameplay** (doc 30 §9):
- sun and moon shadows step every 2° of sun movement and only within 64 blocks of you (configurable)
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
