# Emergent Stealth — Design Questionnaire (Phase 0)

> **Status:** answered 2026-10-06 (answers inline below, lightly typo-fixed). The consolidated decisions and follow-up questions are in [`01-design-decisions.md`](01-design-decisions.md), and the staged plan is in [`02-roadmap.md`](02-roadmap.md).

The answers here feed the staged roadmap (Phase 1), so every question exists to settle a decision we'll need.

## How to answer

- **★ = blocking.** These shape the architecture or the first milestone. If you only have time for some, answer the ★ ones first. There are 54.
- Every question has an ID (e.g. `T-01`). Answer inline under `> A:` or reply in chat using the IDs, whichever is easier.
- Most questions have a **Default** (my recommendation). Writing `default` is a full answer. You can also write `?`, meaning "unsure, talk me through it," or `later`, meaning "not needed for the first stages."
- For wishlists, rate each item **M**ust / **S**hould / **C**ould / **W**on't (MoSCoW).
- Brain-dumps are welcome. Extra ideas in any answer are useful.

### Reference pillars (as briefed)

| Influence | What we're taking |
|---|---|
| **Thief** | Darkness as the core resource; putting out lights; surface/noise awareness; loot-driven infiltration |
| **Dishonored** | A toolkit of traversal/infiltration tools and abilities (claws, stilts, water shoes, smoke bombs, …) |
| **Hitman** | Social stealth, disguises, enforcers, *localised* alert and information propagation, NPC routines |
| **AC Shadows** | A broad weapon/equipment roster; a hideout ("home base") with specialised buildings/workstations; light & shadow, prone in grass |
| **Modern stealth AI** | Perception, suspicion, investigation, search, hunt, patrols, distractions, memory, communication |

---

## 1. Vision & scope

**V-01 ★ Setting & theme.** This decides the weapon list, NPC roster, building palette, disguises and art direction.
- a) Feudal Japan (shinobi/samurai, AC Shadows/Tenchu)
- b) Dark industrial/fantasy city (Dishonored/Thief)
- c) Vanilla-Minecraft-flavoured and setting-agnostic (fits any world)
- d) Mixed/multicultural: a Japan core plus other regions' gear later
- e) Other
- *Default:* (a) for the content, with the *systems* kept setting-agnostic so other content packs are possible later.
> **A:**
> We'll focus on Feudal era for now and then we can eventually move to adding other cultures/regions.

**V-02 ★ Where does the mod live?**
- a) A survival mod that works in any world
- b) A content engine for a bespoke adventure map/modpack
- c) Both: systems work in survival, and the full experience is a map/modpack built on data-driven scenario tools
- *Default:* (c)
> **A:**
> System C seems good, built for specific experiences but also can still be used in worlds normally

**V-03 ★ Supernatural or grounded?** Dishonored has Blink, Dark Vision and Possession. Thief and Hitman are mundane.
- a) Fully grounded (gadgets only)
- b) Grounded gadgets that *imitate* powers (grapple instead of blink, a "listen" stance instead of dark vision)
- c) Light supernatural (a few powers via a resource/charm system)
- d) Full Dishonored-style powers
> **A:**
> A mix of gadgets and powers seems good, for example being able to transfer to the body of a small animal to get into little areas, but not having a teleport and instead needing a grapple hook or similar. Magic should be end-game mostly. Eventually down the line we can do a modern thing too with firearm support and 'military stealth'

**V-04 ★ Mod name & mod ID.** The ID is used everywhere in code and assets and is painful to change later. Is `ninjastealth` OK? Display name? Package (e.g. `com.mcspacewizard.ninjastealth`)?
> **A:**
> com.mcspacewizard.emergentstealth — Emergent Stealth

**V-05 Lethal vs non-lethal.** Are both fully supported? Should there be a consequence system (Dishonored "chaos": more killing → more guards, worse world state)?
> **A:**
> We should implement both non-lethal and lethal, with more lethality increasing alertness in structures or increasing bloodlust (take more damage but deal more damage) whereas less lethality could grant superior healing. Some items/tools/abilities could be restricted to high/low lethality too

**V-06 Sandbox vs authored.** Emergent survival stealth, Hitman-style authored levels with targets and "opportunities," or both?
> **A:**
> Mostly emergent style, one of the main custom structures would be large patrolled camps to stealth through for example

**V-07 Difficulty identity.** Hardcore-sim (Thief Expert: detection is lethal, combat is a failure state) or forgiving action-stealth (AC: detection means a fight you can win)?
> **A:**
> AC style with difficult combat to make stealth more rewarded

**V-08 Other touchstones** to borrow from? Mark of the Ninja (visible noise rings, light/dark readability), Tenchu, Splinter Cell, MGS (alert phases, radio), Aragami, Shadow Tactics (view cones), Sekiro (posture, grapple), Styx, Deus Ex, Hunt: Showdown (sound cues). Which, and what specifically?
> **A:**
> Alert phases definitely, maybe we could do signal fires/bells too. The AI should include advanced things like proper view cones, light/shadows, sound distractions etc

**V-09 Explicit non-goals.** Is there anything you definitely *don't* want (e.g. guns, gore, magic, RPG loot, multiplayer)?
> **A:**
> No guns, no generic tiered loot

**V-10 Team & skills.** Who works on this, and with what skills (Java, Blockbench modelling, pixel art, sound, building/level design, writing)? How many hours a week? This decides how much programmer art and placeholder content I should produce.
> **A:**
> We have no active artist right now, we have 2 people who can build/source builds and/or dialogue/story writing, your primary task is going to be the code with secondary roles on programmer art assets

**V-11 Release intent.** Public release on Modrinth/CurseForge, a private group, or a modpack only? Is there a timeline or a target event?
> **A:**
> Private for now, testing amongst a handful of players

**V-12 License.** Open source (MIT / LGPL-3.0 / GPL-3.0) or All Rights Reserved? *Default:* LGPL-3.0 for code with assets ARR, or MIT if you want maximum openness.
> **A:**
> GPL3 probably but it's private for now so no distribution

---

## 2. Platform & technology

**T-01 ★ Minecraft version.** State as of Oct 2026:
- **26.1** (early 2026) was a big modding turning point. Mojang **removed obfuscation**, so the official names are available, and the game moved to **Java 25**. The NeoForge community has been treating it as the successor to the long-lived 1.21.1 baseline.
- **26.3** (released 15 Sep 2026) is the latest. GeckoLib, Sodium and Iris already have NeoForge builds for it.
- **1.21.1** is the old stable baseline. It has the largest library back-catalogue, but it's obfuscated, ages as we build, and anything we build there will eventually need porting.

Options:
- a) Latest 26.x (26.3 now), pinned for the life of each major stage
- b) 26.1
- c) 1.21.1
- d) Other

*Default:* (a). Unobfuscated code makes the deep hooks a stealth mod needs (mob AI, lighting, rendering, player movement) much easier. The core libraries are already there, and this is a multi-year project, so the ecosystem will only grow around it. We only bump versions between milestones.
> **A:**
> Lets move right to 26.x as our new baseline, I imagine .1 will have more libraries etc?

**T-02 ★ Mod loader.**
- a) NeoForge only
- b) Fabric only
- c) Multi-loader (Architectury/MultiLoader template; roughly 20–30% extra overhead on every feature)
- *Default:* (a). Its event, data-attachment and registry APIs fit a big, systemic content mod, and modpacks of this kind usually run on it.
> **A:**
> NeoForge only

**T-03 Language.** Java 25 or Kotlin? *Default:* Java (widest contributor pool, no extra runtime dependency).
> **A:**
> Java

**T-04 ★ Multiplayer scope.**
- a) Singleplayer only
- b) SP + co-op
- c) Full servers including PvP
- *Default:* build **server-authoritative from day one** whatever you pick. Detection, light and sound are all computed on the server, and retrofitting that later is very painful. Whether co-op gets polish is a separate decision.
> **A:**
> SP + co-op/multiplayer integration is good - it doesn't need to have hyper efficient networking for large servers though as it'll just be played privately amongst friends

**T-05 Hard dependencies.** Is it OK for players to install required libraries (GeckoLib etc.)? *Default:* yes. It's normal, and a modpack bundles them anyway.
> **A:**
> Yes, any additional required mods for users is fine

**T-06 Library wishlist.** Rate M/S/C/W. I'll verify each one's availability on the chosen version before committing to it.
- GeckoLib (NPC, item and armor animations)
- A player animation library (takedowns, climbing, hanging)
- Curios or Accessories (tool belt, disguise and charm slots)
- Veil (Foundry): post-processing, shaders, dynamic lights
- Dynamic lights for handheld torches/lanterns, own implementation or compat with an existing mod (see L-03)
- Sodium + Iris compatibility (strongly recommended to be required-compatible)
- A UI framework (or vanilla screens + our own widgets)
- Config UI (Configured / Cloth Config / YACL)
- JEI / EMI recipe integration
- Jade / WTHIT tooltips (show "NPC state: Suspicious")
- Guidebook (Patchouli / Lavender / own)
- KubeJS integration (pack makers script NPCs, missions)
- FTB Quests integration (modpack questlines)
- Other:
> **A:**
> - GeckoLib: S
> - Player animation: S
> - Curios: S
> - Veil: C
> - Dynamic Lights: C
> - Sodium/Iris compat: M
> - UI framework: M
> - Config UI: M
> - JEI/EMI integration: M (dedicated for EMI)
> - Jade: S
> - Guidebook: C
> - KubeJS: C
> - FTB Quests: C
> - Other: Some sort of better third person mod integration (e.g. Shoulder Surfing Reloaded): M

**T-07 ★ Data-driven design.** Should NPC archetypes, factions, outfits/disguises, loadouts, alert zones, patrol routes, missions and AI tuning be defined in **datapack JSON** so map and pack makers can change them without code? *Default:* yes, aggressively. It's the backbone for the map/modpack.
> **A:**
> Yep, data driven systems are incredibly powerful

**T-08 ★ AI performance budget.** How many "stealth-aware" NPCs should be fully active at once near a player: ~10, ~50, ~150+? This decides how much we need level-of-detail AI, staggered perception ticks and cached sound propagation. Also, what's the minimum target hardware?
> **A:**
> We want to be able to have a lot of NPCs so we can have large structures (20-30, maybe we have a dynamic system where we can have ~20 in a range that could directly detect us but if they are further out they could have a slightly more dumbed down version). Hardware is mid-high tier on anyone using it probably

**T-09 Testing reality check.** I can compile, run unit tests, and run headless servers and GameTests (automated in-world tests, very good for AI) here. I **can't meaningfully playtest visually**, so someone needs to play builds and report. Who will playtest, and how often?
> **A:**
> I can playtest regularly after or during all sessions, multiplayer testing can be done every few days

**T-10 Workflow.** Push to a branch per stage and open a PR for review, or push straight to the dev branch? GitHub Issues/Projects for tracking? CI via GitHub Actions? *Default:* PR per stage, Issues for backlog, CI on every PR.
> **A:**
> We can PR each stage, branching each concurrently worked on feature. Avoid GitHub Actions where/when they require large usage/cost

---

## 3. Player movement & stealth verbs

**P-01 ★ Stances.** Vanilla gives sneak and crawl (only under 1-block gaps). Which do you want?
- Sneak (vanilla, slower and quieter)
- **Prone/crawl anywhere** (hide in tall grass/crops, AC Shadows-style)
- Sprint (loud)
- Slow "creep" walk modifier
- Crouch-sprint (fast but noisy)
> **A:**
> We'll keep the vanilla stances, though maybe changing the "crawl" animation to not look like weird swimming? Sprinting should be more detectable than just walking/running though.

**P-02 ★ Camera.** First-person only, or an optional **third-person stealth camera** (over-the-shoulder, corner peeking, cover)? Third-person makes cover and peeking far better but is a big chunk of work. *Default:* first-person + lean in v1; evaluate a third-person stealth cam later.
> **A:**
> (See T-06 for info on third person) 1st person as primary is good.

**P-03 Lean & peek.** Thief-style lean left/right (Q/E) to peek around corners with the head offset (AI sees less of you)? Peek over ledges?
> **A:**
> Leaning sounds like a great idea but probably not necessary for the time being due to 3rd person

**P-04 Cover/wall-hug.** Stick-to-wall mechanics, or keep movement free?
> **A:**
> Keep movement free

**P-05 Ledge hang & shimmy.** Hang below a block edge to hide, shimmy sideways, pull up, drop onto targets (air assassination)?
> **A:**
> A takedown system that includes air takedowns would be great. Hanging on edges etc and shimmying would be great

**P-06 Climbing.** Climb anywhere with tools (claws) only, or certain blocks without tools (vines, lattices, rough stone)? Stamina limit?
> **A:**
> Climbing claws should have a block blacklist that it can't climb, with "smooth" blocks on there such as iron block, glass, quartz, obsidian, etc. This way, rather than it being a whitelist of climbables (we can also use tags maybe)

**P-07 Parkour.** Mantle/vault 1–2 block ledges, wall-jump, wall-run, roll on landing (reduces damage and noise), slide?
> **A:**
> Basic parkour elements sound good, with the ability to climb up gaps in buildings etc. but not 1 block vaults as it gets annoying

**P-08 ★ Hiding spots.** Rate M/S/C/W:
- Tall grass, crops, bushes (prone)
- Haystacks/hay bales
- Barrels, closets, wardrobes, chests (enter a container block)
- Under floors/crawlspaces
- Rafters/beams (high in the dark)
- Underwater with a breathing reed
- Behind hanging cloth/curtains
- Inside crowds (social, see §9)
> **A:**
> - Tall grass: only when prone, S
> - Haystacks/hay bales: modified to be able to go inside them?: S
> - Barrels/chests: C
> - Under floors/crawlspaces: M
> - Rafters/beams: M
> - Underwater with breathing reed: requires staying near surface: C
> - Behind cloth/curtains: C
> - Inside crowds: C

**P-09 Bodies.** Carry, drag, or carry over the shoulder? Hide bodies in containers/bushes/water? Speed and noise penalties while carrying?
> **A:**
> Drag bodies along floor to hide, slight speed penalty but not noise

**P-10 Keyhole/door peek.** Peek through keyholes and doors before entering (Dishonored)?
> **A:**
> No need to peek doors

**P-11 Observation/marking.** Observe through a spyglass to **tag** enemies (outline through walls for a while, AC/Far Cry)? Or no tagging (pure Thief)? Configurable?
> **A:**
> Tagging with spyglass sounds very good but maybe too powerful

**P-12 Swimming.** Hard to see underwater? Dive to break line of sight? Swimming noise?
> **A:**
> Underwater should break line of sight when in deeper water (e.g. >5 blocks), no additional noise when swimming.

**P-13 Stamina.** Add a stamina resource (climbing, sprinting, holding a hang) or use hunger only?
> **A:**
> Stamina used for parkour/climbing elements only

**P-14 Combat fallback.** When caught, should fighting be a viable path (AC) or mostly a failure state where running and hiding is the answer (Thief)?
> **A:**
> Combat should be a real and viable aspect, with it feeling nice but stealth should be massively rewarded (e.g. huge stealth damage/free takedowns etc)

---

## 4. Light & darkness (Thief pillar)

**L-01 ★ Visibility model.** Proposed: the server computes the player's **visibility** from Minecraft's block-light and sky-light values, sampled at several body points (feet, torso, head), plus time of day, moon phase, weather, stance, movement, worn gear and held light sources. Agree?
> **A:**
> Lighting will need to be highly advanced, with proper dynamic shadows/lighting baked in somehow such that hiding in the shadows of objects/buildings works without it just being straight down shadowing; we need proper "viewing" from guards/the AI so that hiding feels fair and enjoyable. All those things should continue to affect it

**L-02 ★ Handheld/dynamic light.** Vanilla has **no dynamic light**: a held torch doesn't light anything.
- Should a player holding a torch become visible (logic)? Should guards carry lanterns/torches that light their surroundings and reveal you (logic)?
- Should that also render visually (client)?
- *Default:* yes to all. Server-side "virtual light sources" drive the AI, and client rendering goes through our own system or compat with a dynamic-lights mod/Veil.
> **A:**
> Held torches/light sources should properly work as you'd expect.

**L-03 ★ What can be extinguished, and how?** Rate M/S/C/W:
- Targets: wall/standing torches, candles (have a lit state in vanilla), lanterns, campfires, braziers, redstone lamps (switches), paper lanterns (new), chandeliers (new)
- Methods: pinch/snuff by hand up close, **water arrows**, thrown water flask, sand/dirt bombs, cutting the rope a lantern hangs from (it falls, makes noise and starts a fire?), shooting it
> **A:**
> All non-powered lights should be extinguishable (i.e. not redstone lamps, glowstone, sea lanterns and froglights). Hand snuffing, water arrows and rope cutting/shooting sounds good

**L-04 ★ Guards and lights.** Should NPCs **notice lights going out** (suspicion), go to **relight** them, carry torches when searching dark areas, and treat a dark corridor as worth checking?
> **A:**
> Guards with a torch should use it when going into the dark

**L-05 Lighting as a weapon.** Can the player light things to *frame* or distract (light a fire elsewhere), or blind guards with flash powder?
> **A:**
> Yes those features sound good

**L-06 Moss arrows / quieting floors.** Thief's moss arrow turns a loud floor quiet. Want it, or an equivalent?
> **A:**
> Yes

**L-07 Light fairness (client).** Detection is server-side, so shaders and brightness don't affect the AI, but a player at 100% gamma, or using fullbright/shaders, *sees* everything in the dark. Options:
- a) Don't care
- b) Our own darkness post-process that makes dark areas actually dark (configurable)
- c) Modpack-level gamma cap
- d) Night-vision as an earned tool (owl-eye goggles) with a trade-off
- *Default:* (b) + (d), configurable
> **A:**
> B sounds good, we'll need our own custom, highly advanced lighting engine anyway

**L-08 Weather & time.** Rain/thunder reduces visual range and masks noise? Fog at dawn? New-moon nights darker than full moon?
> **A:**
> Thunder should mask noise etc, fog at dawn and moon phases also good

**L-09 Daytime stealth.** At noon there's no darkness. Should the daytime toolkit be social stealth, foliage, rooftops and smoke? *Default:* yes. Day = social/foliage, night = darkness.
> **A:**
> Yes

**L-10 Voxel shadow limitation.** Minecraft light is per block (0–15). There are no true cast shadows, so a block beside a torch is lit even if a pillar is "in the way" visually. Accept voxel lighting as the truth for gameplay? (Recommended. Cast-shadow simulation is expensive and the player can't see it reliably anyway.)
> **A:**
> We need to investigate and develop a custom shadow lighting system to make it work properly

**L-11 Mob spawning in darkness.** In survival, darkening an area spawns hostile mobs. Is that fine, or should our structures/zones suppress vanilla spawns?
> **A:**
> Structures/zones should suppress spawns but otherwise they can work as normal.

**L-12 New light blocks.** Paper lanterns, wall sconces, oil lamps (oil runs out?), lamp posts, braziers, chandeliers, light switches/levers wired to lamps (Dishonored arc lights)? Wishlist?
> **A:**
> Lets stick with feudal-like lighting options for now and we can dive into other styles later

---

## 5. Sound & noise

**S-01 ★ Sound propagation model.**
- a) Simple radius per sound
- b) **Propagation through open space**: sound flows through air/doorways and is muffled by walls depending on material, so a closed door blocks a lot and stone blocks more than wood
- *Default:* (b), cached and budgeted
> **A:**
> We can investigate existing realistic audio mods to see if they have an API we could hook into as we'd want proper open space propagation

**S-02 ★ Surface-based footsteps.** Thief-style loud metal/stone/gravel vs quiet carpet/moss/grass/wool, modified by speed, stance and armor weight. Agree? Should new blocks exist mainly for sound (e.g. **nightingale floors** that squeak, gravel paths, wind chimes)?
> **A:**
> Different sound levels per material sounds good though it should be somewhat minor compared to other stealth elements.

**S-03 Noise sources.** Rate: footsteps, landing, breaking/placing blocks, doors/trapdoors/gates, chests, item drops, combat, bow draw/arrow impacts, eating, armor clank, explosions, sculk-style vibrations. Should vanilla's **GameEvent/vibration** system feed into ours too? *Default:* yes.
> **A:**
> Some things shouldn't cause noise just as QOL, such as chests, item drops/bow & arrow, other stuff can though.

**S-04 Ambient masking.** Rain, waterfalls, rivers, forges, crowds and music mask player noise in their radius?
> **A:**
> Yes ambient masking from rain, flowing water, weather, music sounds good

**S-05 Thrown distractions.** Pebbles, coins, bottles (break noise), firecrackers? Can players throw any item?
> **A:**
> Being able to throw any item sounds good with certain items having special effects (e.g. glass leaving no signs, sharp items dealing tiny amount of damage)

**S-06 Noise visualisation.** Mark of the Ninja-style **visible noise rings** for the player's own sounds? Toggleable?
> **A:**
> Don't worry about sound indication for now

**S-07 Sculk & Warden.** Should our noise system interact with sculk sensors/shriekers (they're already sound-based stealth)? Could be a fun synergy.
> **A:**
> Sculk is already complete in and of itself so no worry

---

## 6. Detection & perception

**D-01 ★ Detection meter shape.** Proposed: each NPC keeps a per-target **awareness value** that fills based on visibility (light) × distance × view angle × movement × stance × exposure (how much of your body is visible) × disguise suspicion × NPC traits (alertness, fatigue, current state). It decays when you break contact. Thresholds trigger state changes. Agree with the general shape?
> **A:**
> Each NPC has its own awareness of each target yes, with several stages

**D-02 Vision.** A central sharp cone (fast fill), a wider peripheral cone (slow fill, triggers "huh?"), and a very short-range sense of anything right behind or touching. Should archetypes differ (archers see further, dogs smell)?
> **A:**
> Sounds good except there shouldn't be inherent behind detection, to make sneaking up on things easier.

**D-03 Line of sight & partial cover.** Multiple raycasts to body points. Glass = transparent, leaves = partial, tall grass hides prone players, iron bars/fences partial, shoji paper walls show **silhouettes** of lit players? Want those nuances?
> **A:**
> Those all sound good

**D-04 ★ Feedback to the player.**
- a) Above-head indicators per NPC (eye/chevron filling white → yellow → red)
- b) Screen-edge directional indicators for off-screen threats
- c) Audio stingers ("Huh?", "Who's there?")
- d) None (hardcore)
- *Default:* a + b + c, each toggleable; difficulty presets can disable them
> **A:**
> Above head detection, with audio stingers (alert sounds not dialogue), configurable

**D-05 Instant detection.** Close and in front, in full light → instant detection? Or always a short grace period?
> **A:**
> Always a very short grace period

**D-06 ★ Environmental evidence.** Which things can NPCs *notice*? Rate M/S/C/W:
- Bodies (dead/unconscious)
- Blood
- Doors left open that should be closed
- Extinguished lights
- Missing valuables / looted chests
- Broken glass/blocks
- Footprints (snow/sand/mud)
- Arrows stuck in walls
- Dropped weapons
- A colleague missing from their post
> **A:**
> - Bodies: M
> - Blood: C
> - Doors left open: C
> - Extinguished lights: C
> - Missing/looted chests: S
> - Broken blocks: S
> - Footprints: C
> - Arrows stuck in wall: M
> - Dropped weapons: M
> - Missing colleague: S

**D-07 Animals.** Guard dogs (smell, tracking along your path), geese/chickens that panic and make noise, crows that scatter (AC/Hitman)?
> **A:**
> No animals for now

**D-08 Fairness.** NPCs never get "free" knowledge of where the player is. Hunting works off **last known position** and predicted movement. Agree? Any exceptions (e.g. high-alert "sweep" behaviour)?
> **A:**
> No free information is a must

---

## 7. AI behaviour

**A-01 ★ State machine (high level).** Proposed states:
`Unaware → Curious ("huh?") → Suspicious → Investigating → Searching → Alerted/Hunting → Combat → Lost contact (search) → Heightened alert (never fully returns to calm)`, plus `Fleeing`, `Reporting`, `Raising alarm`, `Unconscious`, `Distracted`. Add, merge or remove?
> **A:**
> Those states all sound good

**A-02 ★ AI architecture.** Vanilla mobs use a GoalSelector (simple) or a Brain/Activity system (villagers/piglins). I propose our **own layered AI**:
1. **Perception** (sight, hearing, evidence → stimuli)
2. **Memory/knowledge** (facts with position, time, confidence, source)
3. **Decision**: behaviour trees or utility AI over a blackboard
4. **Actions**: navigation, animations, barks
5. A **group/squad layer** for coordinated search, flanking and attack tokens

Behaviour trees would be authorable in JSON so maps can customise. Any preferences or familiarity? *Default:* as above, with an adapter so vanilla mobs can later use the perception/knowledge layers.
> **A:**
> That all sounds great and suitably advanced for proper stealth mechanics

**A-03 ★ Alert propagation (Hitman "localised").** Proposed: information spreads as **knowledge objects** (what, where, when, suspect description, confidence) through:
- a) Voice range: a shout, muffled by walls like other sounds
- b) Line of sight: gestures/whistles
- c) Runners: an NPC physically goes to tell others or a captain
- d) **Alarm infrastructure** (bells, gongs, horns, signal fires) that alerts a whole zone and can be **sabotaged** in advance
- e) Information degrades as it spreads ("someone in a guard uniform" vs "*that* guard")

Which? Should killing the runner stop the spread?
> **A:**
> Line of sight, runners and alarms are all the major ones, information degrading sounds interesting

**A-04 ★ Zones & alert levels.** Per-compound/area **alert level** (Calm → Cautious → Alert → Lockdown) that changes routines: more patrols, doors locked, torches lit, reinforcements. How long before it cools down? Does it ever fully reset?
> **A:**
> Alerts should be localised, with compounds cooling down after a few Minecraft days

**A-05 ★ Patrols.** Authored routes (placed with a **patrol tool**: click waypoints, set waits and look-directions), procedural routes for worldgen structures, or both? *Default:* both.
> **A:**
> Both

**A-06 Routines & schedules (Hitman).** NPCs with daily schedules (shift changes, meals, sleep, chatting in pairs, smoke breaks, relieving themselves). Sleeping guards at night? How deep: light (2–3 activities) or deep (full timetable)?
> **A:**
> There could be light routines for the NPCs so that routes could be learned

**A-07 Investigation behaviour.** Go to the noise source, look around, check nearby hiding spots (poke bushes with spears, open barrels/closets), call a buddy, report to a superior if they find something. Anything to add?
> **A:**
> Sounds good

**A-08 Search behaviour.** Coordinated sweeps that split nearby hiding spots between searchers, search radius growing over time, lighting torches, bringing dogs. How long before giving up? What's the "last-straw" behaviour?
> **A:**
> Those sound good, sans the animals. Give-up times shouldn't be too long but more people investigating could last longer.

**A-09 Hunt/combat behaviour.** Flanking, cutting off exits, archers taking high ground, **attack tokens** (only N attack at once), shields/blocking/parrying, retreat when injured, surrender. Can guards climb after you or use ladders, or are rooftops a safe zone (counter: rooftop archers)?
> **A:**
> Guards cannot parkour climb and shouldn't use super advanced combat mechanics other than maybe blocking exits and attack tokens (investigate Epic Fight-like compat?)

**A-10 Civilians/witnesses.** Flee, scream, report to the nearest guard (Hitman). Can they be bribed, intimidated or knocked out? Do they recognise you later?
> **A:**
> Civilians should run to nearest guard if they see you in restricted space or attacking someone

**A-11 Memory persistence.** Should NPC knowledge and zone alert levels survive chunk unload and world save/reload? *Default:* yes.
> **A:**
> Yes, a chunk unloading should continue to tick down its cooldowns etc

**A-12 Unconscious NPCs.** Wake up after time? Can be woken by others (who then become alerted)? Can you tie them up/gag them?
> **A:**
> Don't wake up naturally but can be woken up, and if they saw you will remember and be alert.

**A-13 ★ Navigation.** Vanilla pathfinding is weak (doors, ladders, gaps, multi-level buildings). OK to build **custom navigation**: door use (open, close, **lock**), ladders, stairs, jumping gaps, maybe climbing for agile archetypes? *Default:* yes, staged. Doors and ladders come first.
> **A:**
> Custom pathfinding almost definitely necessary

**A-14 ★ Debug tooling.** I strongly recommend building an in-game **AI debug overlay** early (vision cones, awareness meters, state labels, heard sounds, knowledge entries, patrol routes, nav paths). Agree?
> **A:**
> We'll definitely want debug tooling for seeing paths, detection info, vision etc etc

**A-15 Vanilla mobs.** Later extension: which first (pillagers/vindicators as guards, villagers as civilians, iron golems, zombies)? Opt-in config?
> **A:**
> We can integrate better AI to vanilla mobs later but for now we'll just have our custom ones

**A-16 Barks & dialogue.** Text subtitles, voice lines (who records?), or mumble sounds? Ambient NPC conversations that reveal intel/opportunities (Hitman)?
> **A:**
> We could have text above head maybe

---

## 8. NPCs, factions & archetypes

**N-01 ★ Archetype roster.** Rate M/S/C/W:
- Civilian
- Worker/servant (their outfit gives access to service areas)
- Merchant
- Patrol guard
- Stationary sentry
- Archer/watchtower lookout
- **Elite** (samurai-type; can't be insta-assassinated from the front, needs a fight or special technique)
- Captain/officer (sees through disguises, coordinates search)
- Dog handler + dog
- Monk/priest
- Shinobi rival (counter-ninja who checks rooftops/shadows)
- Target/VIP
- Bodyguards
> **A:**
> - Civilian: M
> - Worker: M
> - Merchant: C
> - Patrol guard: M
> - Stationary guard: M
> - Elite: M
> - Captain: M
> - Dog handler + dog: W
> - Monk/priest: C
> - Shinobi: C
> - Target/VIP: M
> - Bodyguard: S

**N-02 Factions.** Multiple factions with relationships (rivals fighting each other, which you can provoke)? Player reputation per faction?
> **A:**
> Factions, 2 factions could exist, Shogunate and Shinobi

**N-03 ★ NPC appearance.** Proposed: humanoid NPCs with a **layered outfit system** (base body/skin + clothing + headgear + held items), defined in data. This directly powers disguises (§9). Player-model style or custom proportions (GeckoLib models)?
> **A:**
> Humanoid sounds good and makes most sense for compatibility etc

**N-04 Named targets/bosses.** Unique NPCs with special routines, bodyguard formations and multiple "opportunities" to reach them?
> **A:**
> Yes

**N-05 Spawning.** In survival, our structures spawn their garrisons. In maps, makers place them via spawner/marker blocks and commands. Should garrisons respawn (rate?) or stay dead?
> **A:**
> Garrisons should respawn unless destroyed

**N-06 Pickpocketing.** Steal keys, coins, documents and poison from NPCs (Thief/Dishonored)?
> **A:**
> Pickpocketing for keys/tools sounds good

**N-07 Non-hostile interaction.** Trade, dialogue, quests and hiring with friendly or neutral NPCs?
> **A:**
> Neutral NPCs can exist but will be way down the line

---

## 9. Social stealth & disguises (Hitman pillar)

**G-01 ★ Getting disguises.** Take the outfit from a knocked-out/killed NPC (Hitman), craft at the tailor, find in wardrobes, buy? Which?
> **A:**
> Disguises can be taken from knocked out NPCs only (not killed) or found loose in the world/crafted

**G-02 ★ Access zones.** Each area has rules: *public*, *trespassing* (escorted out / warned), *hostile* (attacked). Each outfit grants access to some zones. Define zones with a **zone tool** (in-game volume selection, saved to the world/datapack) plus automatic zones in worldgen structures. Agree?
> **A:**
> Those zones sound good but maybe with more, for example some zones only being accessible in the day

**G-03 ★ Enforcers.** NPCs who know their colleagues (same uniform, or officers) become suspicious when you're close in their outfit (Hitman's white dots). Want this?
> **A:**
> Yes

**G-04 Suspicious actions in disguise.** Rate: running, sneaking, climbing walls, visible weapon, carrying a body, being in the wrong zone, loitering near a target, trespassing at the wrong time of day, picking locks, staring.
> **A:**
> Climbing or carrying illegal items in the wrong disguise breaking it sounds good

**G-05 Blending.** Crowds, sitting on benches, performing a task (sweep, carry a crate, tend a garden, pray at a shrine), joining a group of the same outfit, hiding behind hood/hat/straw hat? Which?
> **A:**
> Blending not necessary

**G-06 Disguise "blown" scope.** Blown only for those who witnessed it plus those told (via propagation)? How long is the outfit compromised, and for whom?
> **A:**
> A blown disguise is permanently blown for anyone who knows, but it should still use the propagation system

**G-07 Disguise implementation.** Changes the player's rendered model to the outfit (other players see it too). Does it replace armor? Proposed: a dedicated **disguise slot** that overrides visuals; wearing real armor over it may "break" the disguise (an armored servant is suspicious).
> **A:**
> Dedicated disguise slot with some items being "illegal" to equip for a disguise (higher suspicion)

**G-08 Notoriety/wanted level.** AC-style notoriety that rises with public crimes; reduce it by tearing posters, bribing, changing outfits, lying low?
> **A:**
> No notoriety system

---

## 10. Tools & abilities (Dishonored pillar)

**E-01 ★ Toolkit wishlist.** Rate M/S/C/W. Add your own.

*Traversal*
- Climbing claws (tekko-kagi / shuko): climb walls
- **Stilts**: what's the intent? Seeing over walls, crossing traps/caltrops, wading quietly, reaching high windows? (see E-02)
- **Water shoes** (mizugumo): walk on water
- Grappling hook (kaginawa): pull up to ledges / swing
- Rope arrows (Thief): climbable ropes in wood
- Rope ladder / collapsible ladder
- Glider/kite
- Spring/leaping boots

*Distraction & escape*
- Smoke bombs (block AI vision server-side, plus visual smoke)
- Blinding powder (metsubushi)
- Firecrackers / noisemakers
- Thrown pebbles/coins
- Decoy (straw dummy, clothes on a stick)
- Bird-call whistle / lure
- Wind-up toy noisemaker
- Shinobi bell (AC: thrown bell as a distraction)

*Light control*
- Water arrows / water flask
- Moss arrows (quiet floor)
- Snuffer tool (silent extinguish at range)
- Fire arrows / firestarter

*Traps & non-lethal*
- Caltrops (makibishi)
- Tripwire noisemaker
- Sleep darts / blowgun
- Poison (food/drink, blade)
- Snare traps

*Recon*
- Spyglass with tagging
- Listening cup/stethoscope (hear through walls, see NPCs as silhouettes)
- Mirror peek
- Map/intel scrolls revealing patrols

*Infiltration*
- Lockpicks
- Keys
- Pry bar
- Glass cutter
- Rope cutter
- Breathing reed
- Body sack
> **A:**
> Traversal:
> - Climbing claws: M
> - Stilts: allows reaching up higher ledges, M
> - Water shoes: limited water walking, S
> - Grappling hook: expensive, M
> - Rope arrows: S
> - Rope ladder: C
> - Glider: W
> - Spring boots: W
>
> Distraction & escape:
> - Smoke bombs: M
> - Blinding powder: S
> - Firecrackers: S
> - Thrown pebble: M
> - Decoy: C
> - Whistle: W
> - Wind-up toy noisemaker: W
> - Shinobi bell: C
>
> Light control:
> - Water arrows: S
> - Moss arrows: C
> - Fire arrows/starter: S
>
> Traps & non-lethal:
> - Caltrops: S
> - Tripwire noisemaker: C
> - Sleep darts: S
> - Poison: C
> - Snare traps: W
>
> Recon:
> - Spyglass with tagging: S
> - Listening cup: C
> - Mirror peek: W
> - Map/intel scrolls: C
>
> Infiltration:
> - Lockpicks: S
> - Keys: M
> - Pry bar: W
> - Glass cutter: C
> - Rope cutter: W
> - Breathing reed: C
> - Body sack: C

**E-02 Stilts.** Tell me the fantasy you have for these, and the same for anything else unusual on your list.
> **A:**
> Stilts would allow you to reach up a few blocks higher, such as climbing outer walls or reaching high windows

**E-03 ★ Tool access UI.** A dedicated **tool belt** (separate from the hotbar, Curios-style) with a **radial wheel** on a hotkey (Dishonored), or plain hotbar items? *Default:* tool belt + radial.
> **A:**
> Custom access UI such as a radial wheel or hotkeys

**E-04 Resource model.** Consumables (crafted, finite), durability, cooldowns, or a mix per tool?
> **A:**
> Depends on the tool, but there should be durability on things like the climbing hook and claws, others should be one-time use from inventory like stilts, and others should be consumable like the rocks/firecrackers.

**E-05 Lockpicking.** Minigame (which style: Skyrim-angle, Thief-pins, timing ring) or a timed hold with noise? Lock tiers?
> **A:**
> Lockpicking minigame should be a timing ring type minigame

**E-06 ★ Where are tools crafted?** Anywhere in a vanilla crafting table, or only at hideout workstations (§13)? *Default:* basic tools anywhere, advanced tools and upgrades at workstations.
> **A:**
> Custom hideout workstations for the tools, outside of more basic ones which can be made in the crafting table/inventory

**E-07 Tool upgrades.** Upgrade paths per tool (claws: faster climb → silent climb → wall jump)?
> **A:**
> Some tools can have some upgrades yes, we'll probably want to expand to have more materials etc

---

## 11. Weapons & combat (AC Shadows pillar)

**W-01 ★ Weapon classes.** Rate M/S/C/W: katana, tanto, wakizashi, kusarigama, naginata, yari (spear), kanabo (club), nodachi, bo staff, hidden blade, shuriken, kunai, bow (yumi), blowgun, teppo (matchlock gun, very loud), sai, tessen (war fan), fists/gauntlets, others for non-Japanese settings.
> **A:**
> - Katana: M
> - Tanto: C
> - Wakizashi: S
> - Naginata: C
> - Yari: M
> - Kanabo: S
> - Bo staff: S
> - Hidden blade: M
> - Shuriken: S
> - Bow: C
> - Blowgun: S
> - Teppo: C
> - Tessen: M

**W-02 ★ Combat system depth.**
- a) Vanilla+ (unique stats/specials per weapon, vanilla swing timing)
- b) Mid (light/heavy attack, block, **parry**, dodge, a posture/guard meter)
- c) Full action combat (animated combos, posture-break finishers, stances)
- *Default:* (b) to start, designed so it can grow toward (c)
> **A:**
> Starting at B but ideally into C

**W-03 ★ Assassinations/takedowns.** Rate: from behind, from above (air), from ledge, from hiding spot (bush/haystack), from underwater, double assassination, chained assassinations, through shoji walls(!). Can elites resist a frontal or any assassination?
> **A:**
> Rear, air, ledge and water takedowns are a definite, chained probably not, but through shoji walls is great. Elites should resist non-air takedowns

**W-04 Non-lethal.** Chokehold/knockout, blunt takedowns, sleep darts, non-lethal weapon variants. How strongly supported?
> **A:**
> Non-lethal should be highly viable and rewarded with proper takedowns

**W-05 Takedown presentation.** Quick first-person animation, cinematic third-person cut, or a configurable choice?
> **A:**
> We can have fancy animations in both 1st and 3rd person

**W-06 ★ Loot model.** Randomised gear with rarities and perks/engravings (AC), fixed crafted items with upgrades (Thief/Dishonored), or a blend? *Default:* fixed crafted items + workstation upgrades + a small set of found unique "legendary" pieces.
> **A:**
> Fixed crafted items with upgrades, with a pool of unique/legendaries with powerful effects (we could also integrate Apotheosis support optionally)

**W-07 ★ Armor & stealth.** Heavy armor = louder footsteps and clanking, slower, more visible? Should **vanilla armor** follow those rules too (iron/diamond/netherite clank)? Shinobi garb = quiet and dark but weak?
> **A:**
> Heavy armour should be louder but not slower, we can integrate new armours to fill gaps in the system/get armour set bonuses

**W-08 Weapon visibility.** A drawn weapon alarms civilians and breaks disguises? Weapons rendered on the body (back/hip)?
> **A:**
> We should have a system of drawn vs undrawn weapon so we can have a weapon "equipped" but not drawn; then drawing it can cause unsettlement unless appropriately disguised

**W-09 Gore level.** None, vanilla-level, blood particles/decals (which can also be *evidence*, D-06)?
> **A:**
> Blood decals could be effective in promoting non-lethal

**W-10 Playstyles/classes.** AC Shadows has two protagonists (shinobi vs samurai). One flexible playstyle, or class/skill-tree specialisations (Shinobi / Samurai / Onmyōji-esque)?
> **A:**
> (Not answered directly; see R-01: Path of Shinobi / Path of Shogunate skill trees.)

---

## 12. Consequences & bodies

**B-01 Body persistence.** Bodies stay as entities until hidden or discovered? Despawn after X minutes? Persist through save/reload?
> **A:**
> NPC bodies only despawn when leaving the area or are fully hidden (e.g. in water or hay pile)

**B-02 Mission/world consequences.** Killing raises zone security permanently? Assassinating a captain disorganises their squad? Dishonored-style chaos meter?
> **A:**
> Killing can raise a zone's security next time it resets, killing a captain can disorganise squads

---

## 13. Home base / hideout (AC Shadows pillar)

**H-01 ★ Base form.**
- a) A claimed **hideout** area (placed via a "hideout core/shrine" block) inside which workstations work and get bonuses
- b) Free-form: workstation blocks work anywhere
- c) A pre-built hideout you restore over time (map-friendly)
- *Default:* (a), with (c) as the map variant
> **A:**
> A mix of A and C sounds good, the pre-built hideout could be a map option which creates this space on spawn/puts you there.

**H-02 ★ Building/workstation list.** Rate M/S/C/W:
- Forge/blacksmith (weapons)
- Armorer
- **Tailor** (disguises, outfits)
- Apothecary/alchemist (poisons, smoke, sleep agents)
- **Tinker/toolmaker** (gadgets)
- Fletcher (arrows: water, moss, rope, fire)
- Dojo/training ground (skills, practice dummies, a stealth training course!)
- Kennel/aviary (companion animals: scout hawk, guard dog)
- Stables
- **Strategy/map room** (contracts, intel, mission planning)
- Allies' quarters (recruits)
- Storehouse
- Shrine/garden (cosmetic or buffs)
- Messenger/pigeon post
> **A:**
> - Forge: M
> - Tailor: M
> - Apothecary: C
> - Toolmaker: M
> - Fletcher: C
> - Dojo master: C
> - Stables: S
> - Strategy/map room: M
> - Allies' quarters: C
> - Storehouse: M
> - Shrine/garden: S

**H-03 ★ Workstation structure.** Single blocks that level up with materials? Multiblock structures? Or "room recognition" (a workstation gets bonuses inside a valid room of the right size/decor, like a light MineColonies)? *Default:* single blocks + upgrade tiers + optional room bonus.
> **A:**
> Room recognition sounds very powerful, maybe a mix of that and multiblocks/single blocks (depending on the workstation and its level)

**H-04 Allies/recruits.** Recruit NPCs (rescued, persuaded) who work at the base and can be called into missions as companions or distractions (AC Shadows allies)?
> **A:**
> NPC recruits sounds good for distractions etc

**H-05 Passive base output.** Resource production over time? Base raids/defence events?
> **A:**
> Light material passive output, with base raids happening when lethality is too high too frequently

**H-06 Fast travel.** Travel to and from the hideout (e.g. via messenger birds or map room)? Or no fast travel?
> **A:**
> No fast travel, we'll probably use Waystones for it

**H-07 Multiplayer bases.** Per-player, per-team, or shared?
> **A:**
> Player bases can have other players invited to them/made officers etc

---

## 14. Progression & economy

**R-01 ★ Progression axes.** Rate: skill tree (XP from stealth actions), gear/tools, workstation upgrades, faction reputation, knowledge scrolls (techniques learned), mastery challenges.
> **A:**
> Skill tree for player skills and a research tree for workstation/home base upgrades. Mastery challenges unlocking special capstone skills of a given tree by performing certain tasks. Player skill trees should include Path of Shinobi for stealth-related skills and Path of Shogunate for direct combat based abilities

**R-02 Currency.** Emeralds, custom coins (mon/ryō), no currency (barter/materials only)?
> **A:**
> Currency would be a custom coin but also material asks

**R-03 ★ Contracts.** A contract board (procedural assassination/theft/rescue/sabotage contracts against worldgen structures), authored missions only, or both?
> **A:**
> Both

**R-04 Mission ratings.** Hitman "Silent Assassin" / Dishonored "Ghost" / "Clean Hands" ratings with rewards?
> **A:**
> Mission rating shouldn't necessarily matter, though could be tracked

**R-05 Loot & theft.** Thief-style valuables (vases, scrolls, jewellery) as a key income source?
> **A:**
> Random loot shouldn't be that significant, to avoid clutter - unless we have it be able to turn easily into coins

**R-06 Vanilla tie-in.** Do weapons tier with vanilla materials (iron → diamond → netherite), or with custom materials (tamahagane steel, etc.)?
> **A:**
> Our stuff should be a full range of materials, mostly being iron-netherite++

---

## 15. World & content

**C-01 ★ Worldgen structures.** Which, and should they generate in survival worlds? Rate: castle, walled compound/estate, guarded village/town district, bandit camp, temple/monastery, watchtowers, warehouse/port, prison, rival ninja clan hideout, merchant caravans (moving!).
> **A:**
> Castles should spawn but be rare and typically surrounded by a compound, there should also be estates, villages, camps, monastery, forward watch towers, ports, prisons, clan hideouts etc. The larger compounds should be procedural with how the buildings are placed/which it has etc

**C-02 ★ Building palette.** New blocks that matter for stealth: shoji (see silhouettes, can be cut through), tatami (quiet), **nightingale floors** (squeak), gravel paths (loud), roof tiles (climbable/slippery?), noren curtains (break LOS), bamboo (climbable, hides). Other decorative blocks? How big should the block catalogue be?
> **A:**
> We should add required blocks for any feudal stuff as well as the aforementioned options you stated (roof tiles not slippery)

**C-03 Doors, locks, keys.** Lockable doors/gates/chests with key items and lock tiers; guards lock up at night?
> **A:**
> Lockable doors/chests etc which can be obtained from captains or lockpicked

**C-04 ★ The trivialisation problem (survival).** Players can tunnel through walls, pillar up, use ender pearls, elytra, invisibility potions, break blocks... How do we handle it?
- a) Map play in Adventure mode only
- b) Structures made of reinforced/protected blocks
- c) Breaking/placing blocks inside zones is very loud and suspicious
- d) Accept it (a sandbox is a sandbox)
- e) A mix, config-driven
- *Default:* (e). Maps use (a), survival uses (c) + some (b), and vanilla tools get stealth-world rules (ender pearl impact noise, invisibility still leaves armor visible / footsteps audible).
> **A:**
> We can restrict block breaking/placing in certain zones, and just make it very loud in general for zones where you can

**C-05 ★ Map-maker tooling.** Which tools are needed? Zone tool, patrol path tool, NPC spawner/marker blocks, an in-game NPC editor GUI, trigger volumes, mission/objective scripting in data, cutscene/camera paths, an "opportunity" (Hitman) system?
> **A:**
> Map making tools would be zone tools, path tools, NPC spawners and GUI, triggers, a custom mission UI framework with staging etc

**C-06 Who builds the map/levels**, and do you already have a concept for the map (region, size, number of levels/hubs)?
> **A:**
> It's going to be freeform into a survival world too so we should have it be procedural on the world gen

**C-07 Story/lore.** Is there a narrative? A protagonist or clan? Should the mod have built-in lore (guidebook, scrolls)?
> **A:**
> There will be a narrative eventually, however for the time being we can tie it into our mission UI/HUD framework

**C-08 Planned modpack list.** Any mods you already know will be in the pack (world gen, performance, other content)? Lets me plan compat early.
> **A:**
> Other planned mods are likely EMI, Jade, potentially something like Epic Fight/Better Combat, a backpack system, MAYBE Create?

---

## 16. Vanilla integration

**X-01 Vanilla stealth hooks.** Vanilla has Swift Sneak (enchant), invisibility, and sculk/Warden sound mechanics. Extend them (e.g. new enchants: *Silent Step*, *Shadowed*, *Featherfall-land*)?
> **A:**
> New enchants sounds good

**X-02 Vanilla mobs in our structures.** Let zombies/creepers spawn around our compounds and fight guards, or suppress them?
> **A:**
> Vanilla mobs shouldn't necessarily be suppressed but their engagement with guards shouldn't be specifically promoted

**X-03 Villages.** Treat vanilla villagers as civilians/witnesses? Iron golems as guards?
> **A:**
> Villagers should be treated like witnesses but not iron golems as guards

---

## 17. UI / HUD

**U-01 ★ HUD elements.** Rate: light gem / visibility meter, noise meter, detection indicators (see D-04), compass bar with markers (AC), objective tracker, tool radial, disguise status (who can see through it), zone status ("Trespassing").
> **A:**
> Light gem/visibility meter is a definite, objective tracker visible through spyglass would be good, a disguise status icon would be helpful too. In general indicating UI should require spyglass or a custom goggles-like item.

**U-02 UI art style.** Vanilla-consistent pixel UI or a custom painted style (ink brush/parchment)? *Default:* pixel-art with an ink-and-paper accent theme.
> **A:**
> Pixel art with ink-and-paper accent theme

**U-03 Accessibility.** Colourblind-safe indicators, subtitles for every stealth-relevant sound, reduced flashing/screen effects, toggle vs hold for stances?
> **A:**
> Subtitles are a definite, with visual audio indicators as an accessibility toggle

**U-04 Onboarding.** Guidebook, in-world tutorial dojo, contextual hints, or all three?
> **A:**
> We can have guidebook and in-world dojo

---

## 18. Visuals, animation & audio

**F-01 ★ Asset pipeline.** Who makes models/textures/sounds? Should I produce **programmer art placeholders** and ship structured asset folders so artists can drop in replacements?
> **A:**
> Please do produce programmer art and wire them up so that textures can easily be swapped in from the artist or me

**F-02 Player animations.** Third-person animations for climbing, hanging, prone, takedowns, carrying bodies: must-have or later? (They need a player animation library.)
> **A:**
> The animations would be massively beneficial, we can leverage any library we want, but feature implementation *is* more important

**F-03 Post-processing.** Desaturation/vignette when hidden in shadow (Splinter Cell), pulse when detected, a "listen" mode effect? Must work with Iris shaders on or off.
> **A:**
> Those options sound good to investigate

**F-04 Audio.** Custom SFX from CC0/licensed libraries? **Dynamic music** that reacts to alert state (calm → tense → combat)?
> **A:**
> We can use CC0 sound effects yeah, dynamic music sounds great and as mentioned in a previous answer we can investigate using existing realistic audio frameworks or make our own.

**F-05 Smoke & particles.** Smoke bombs need both the visual (particles/volumetric) and a server-side **vision-blocking volume** for the AI. Agree on doing both?
> **A:**
> Yep sounds good, ideally it'll look like volumetric smoke rather than just particles

---

## 19. Configuration & difficulty

**K-01 Difficulty presets.** e.g. *Novice* (generous indicators) → *Shinobi* → *Ghost* (no HUD indicators, faster detection), tunable per world?
> **A:**
> We can have config setups that toggle various UI elements for added difficulty but our AI itself for now should just be a single system

**K-02 Pack-maker configurability.** Should every system be toggleable and tunable (server config + datapacks)? *Default:* yes.
> **A:**
> Not necessarily, some core features should be hooked together probably

---

## 20. Multiplayer specifics

**M-01 Co-op stealth.** If one player is spotted, is the whole squad compromised (the zone alert rises) but not individually identified? Shared knowledge of the team's disguises?
> **A:**
> If one player is spotted, alert level should rise but no free info on 2nd player should be given

**M-02 PvP stealth.** Players detecting each other via the same light/noise systems? Special game modes (hide-and-seek, assassin hunt, a "Wanted"-style mode)?
> **A:**
> PvP stealth isn't a thing for now

---

## 21. Process & priorities

**Z-01 ★ Rank the pillars** (1 = most important), so stages are ordered right:
- [ ] AI perception (sight/hearing/evidence)
- [ ] AI behaviour (suspicion/investigation/search/hunt/patrol/propagation)
- [ ] Light & darkness
- [ ] Sound & noise
- [ ] Movement & stealth verbs
- [ ] Social stealth & disguises
- [ ] Tools & gadgets
- [ ] Weapons & combat
- [ ] Hideout & workstations
- [ ] Worldgen structures & content
- [ ] Map-maker tooling
- [ ] Visuals, animations & audio polish
> **A:**
> 1. AI perception
> 2. Light & darkness
> 3. AI behaviour
> 4. Movement & stealth verbs
> 5. Sound & noise
> 6. Tools & gadgets
> 7. Weapons & combat
> 8. Worldgen structures
> 9. Hideout/workstations
> 10. Visuals, animations & audio polish (though note we absolutely do want programmer textures/basic anims as placeholders with a moderately high priority)
>
> (Social stealth & disguises and map-maker tooling were not ranked.)

**Z-02 ★ First playable milestone.** My proposal for the **vertical slice** after scaffolding:
- One guard archetype with sight + hearing + light-based visibility
- Unaware → suspicious → investigate → search → hunt states, with patrol waypoints
- A light-gem HUD + detection indicators
- Extinguishable torches/lanterns (+ the AI noticing lights out)
- One distraction (thrown pebble) and one escape tool (smoke bomb)
- A basic takedown
- The AI debug overlay
- A small test compound (a structure template) to play in

Is that the right first target, or would you rather see something else first?
> **A:**
> All of that sounds good though ensure it matches all other details (e.g. we don't want extinguished lights being alerting)

**Z-03 Design docs vs code.** Short design docs per system (reviewed by you) before each stage's code, or design-as-we-go?
> **A:**
> Short docs per system which I will review before diving too far into a given stage, but subsystems can be worked autonomously with self oversight on system design

**Z-04 Stage size.** Small frequent stages (weekly-ish PRs) or bigger milestones?
> **A:**
> Frequent staging

**Z-05 Anything else** I haven't asked about but you've been imagining?
> **A:**
> (Not answered.)

---

## Appendix: technical realities worth knowing now

These are context, not questions. They're why some questions above exist.

1. **The server is the source of truth.** Light levels, sound propagation, detection and AI all run on the server, so shaders and gamma never change what the AI sees. They only change what the *player* sees (L-07).
2. **Raycasts and sound propagation cost CPU.** Perception will be staggered across ticks, culled by distance, and run at lower resolution for distant NPCs. Your answer to T-08 sets the budget.
3. **Vanilla pathfinding is limited.** Doors, ladders, multi-floor buildings and climbing need custom navigation work (A-13), and that's a real chunk of the AI effort.
4. **Minecraft has no dynamic lights.** Held and carried lights need our own logic layer plus a rendering solution (L-02).
5. **Voxel lighting.** Light is a 0–15 value per block, not cast shadows. Gameplay will be built around that, and level design (light placement) does the rest (L-10).
6. **Sandbox trivialisers.** Block breaking/placing, ender pearls, elytra and invisibility will each get a deliberate answer (C-04).
7. **Version churn.** Mojang now ships several "drops" a year. We pin one version per milestone and port deliberately between milestones (T-01).
8. **Testing.** AI behaviour will get automated GameTests where possible, but feel and readability need human playtesting (T-09).
