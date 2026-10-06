# Emergent Stealth — Design Decisions (v0)

This is the consolidated outcome of the [Phase 0 questionnaire](00-questionnaire.md). It's the reference every system doc and stage builds on. IDs in brackets point back to the questionnaire answers. **§ Open questions** at the end lists what still needs a decision.

Each entry is **Decided**, **Proposed** (my reading or extension of your answers; push back if it's wrong), or **Open**.

---

## 1. Identity & scope

| | |
|---|---|
| Name | **Emergent Stealth** |
| Mod ID / package | `emergentstealth` / `com.mcspacewizard.emergentstealth` [V-04] |
| Setting | Feudal Japan first. Systems stay culture-agnostic so other regions, and later a modern "military stealth" expansion, can plug in [V-01, V-03] |
| Mode of play | **Emergent, survival-first.** Procedural structures in normal worlds, with map/scenario tooling layered on top [V-02, V-06, C-06] |
| Feel | AC-style: combat is real and satisfying, but hard enough that stealth is clearly the better route. Stealth gets big payoffs (stealth damage, free takedowns) [V-07, P-14] |
| Supernatural | Gadgets first. Powers are end-game and limited (e.g. possess a small animal to reach tight spaces). **No teleport**; traversal is grapple/claws/stilts [V-03] |
| Hard no's | Guns (until a possible modern expansion), generic tiered loot [V-09] |
| Distribution | Private testing group, GPL-3.0 intended [V-11, V-12] |
| Team | Code + programmer art: Claude. Builds, dialogue, story: 2 people. No artist yet [V-10] |

## 2. Platform

- **Decided:** NeoForge only [T-02], Java [T-03], modern 26.x baseline [T-01]. See **Q1** for the exact version.
- **Decided:** SP + small-group co-op. Everything is server-authoritative; no large-server networking optimisation [T-04].
- **Decided:** Heavily data-driven: archetypes, factions, outfits, zones, patrols, AI tuning, loot and missions all live in datapacks [T-07].
- **Decided:** Extra required dependencies are fine [T-05].
- **Decided:** Performance target: **~20 NPCs in full-fidelity perception range**, more beyond that in a reduced "LOD" mode, on mid–high-end PCs [T-08].
- **Decided:** Workflow: one PR per stage, a branch per concurrent feature. GitHub Actions avoided or kept manual-only [T-10]. You playtest every session, multiplayer every few days [T-09].
- **Decided:** One short design doc per system, reviewed by you before its stage goes deep. Subsystems inside an approved doc proceed autonomously [Z-03]. Frequent small stages [Z-04].

**Libraries & integrations** [T-06, C-08]. Availability was checked Oct 2026 and will be re-verified when we pin versions:

| Library / mod | Priority | Role | Status on 26.x |
|---|---|---|---|
| Sodium + Iris | **Must** (compat) | Performance/shaders in the pack; our rendering must not break with them | Available on 26.1–26.3 |
| Shoulder Surfing Reloaded | **Must** (compat) | Third-person camera; our crosshair, raycast and takedown targeting must work with it | Available on 26.1.2–26.3 |
| UI framework | **Must** | HUD, radial wheel, workstation and mission UIs | Own lightweight widget layer on vanilla screens (proposed), unless a NeoForge 26.x UI lib proves worth it |
| Config UI | **Must** | In-game config | NeoForge has a built-in config screen. Use it + Configured if needed |
| EMI | **Must** (dedicated plugin) | Recipe and workstation recipe display | Available on 26.1.2–26.3 |
| GeckoLib | Should | NPC/armor/item animations | Available on 26.1–26.3 |
| Player Animation Library (PAL) | Should | First- and third-person player animations (takedowns, climbing, dragging, crawl fix) | Available on 26.1.x |
| Curios | Should | Disguise slot, tool belt | Stable on 26.1.2; **beta** on 26.3 |
| Jade | Should | Tooltips (NPC state, lock info) | Available on 26.1–26.3 |
| Better Combat | Could (compat) | Optional player melee feel | Available on 26.1–26.3 |
| Epic Fight | Could (compat) | — | **Not available on 26.x** (1.21.1 only). Compat deferred. See Q7 |
| Apotheosis | Could (compat) | Optional legendary/affix support [W-06] | 26.1.2 only |
| Veil | Could | Advanced client rendering (deferred lights, post-processing) | Unverified on 26.x; part of the visual-lighting R&D |
| Dynamic lights | Could | Visual light from held torches | Own implementation or compat; see §4 |
| Waystones | Pack | Fast travel instead of our own [H-06] | Available on 26.1.2–26.3 |
| Sound Physics Remastered | Pack (optional) | Client-side audio realism. **It has no API and runs client-side only, so it can't drive AI hearing.** We build server-side propagation ourselves [S-01] | Available on 26.1.2 and 26.3 |
| KubeJS / FTB Quests / guidebook | Could | Later | Later |

## 3. Player movement & stealth verbs

- **Decided:** Vanilla stances kept. Sprinting is much more detectable than walking [P-01].
- **Proposed:** A **crawl toggle usable anywhere**. It uses vanilla's crawl pose with a proper crawling animation instead of the swim pose. It's needed for "tall grass only when prone" [P-08]. See **Q3**.
- **Decided:** First-person primary. Third-person via Shoulder Surfing compat. No lean for now. Free movement, no cover-snap [P-02, P-03, P-04].
- **Decided:** Ledge hang and shimmy. Basic parkour, including climbing up into gaps in buildings, but **no auto 1-block vaults**. Stamina used only for parkour and climbing [P-05, P-07, P-13].
- **Decided:** Climbing claws climb anything **except** blocks in a blacklist tag (`#emergentstealth:unclimbable`: glass, iron, quartz, obsidian, …) [P-06].
- **Decided:** Hiding spots. Must: crawlspaces/under floors, rafters/beams. Should: tall grass (crawling only), enterable hay bales. Could: barrels/chests, breathing reed (near surface only), curtains, crowds [P-08].
- **Decided:** Bodies are dragged along the floor: slight speed penalty, no noise [P-09].
- **Decided:** Water deeper than ~5 blocks breaks line of sight. Swimming makes no extra noise [P-12].
- **Decided:** Spyglass tagging, balanced so it isn't too strong. **Proposed:** a limited number of tags that last a limited time, spyglass-only, with skills that improve it [P-11].

## 4. Light, darkness & shadow

**Decided** [L-01…L-12]:
- Lighting is a headline system. Hiding in the **cast shadows** of buildings and objects must work properly, not just "the light level of the block I'm standing in."
- Held and carried lights work as you'd expect, for players and NPCs.
- **All non-powered lights can be put out:** torches, wall torches, candles, lanterns, campfires, braziers, and the new feudal lights. Redstone lamps, glowstone, sea lanterns and froglights can't. Methods: hand snuffing, water arrows, cutting or shooting the rope a hanging lantern hangs from. Lights can also be lit (to frame or distract), and flash powder blinds.
- Guards carrying a torch raise it when they go into dark areas.
- Thunder masks noise. Dawn fog and moon phases affect visibility. Daytime stealth relies on foliage, disguises and verticality.
- Zones and structures suppress hostile mob spawns. Elsewhere vanilla spawning is unchanged.
- Feudal-style light sources only, for now.
- A custom **"dark should be dark"** client post-process (L-07 option b).

**Proposed:** a two-part design, because the gameplay truth and what you see are separate problems:

1. **Gameplay exposure model (server, authoritative). This is what the AI uses.**
   - We keep an index of light sources per chunk section: emitting blocks and their extinguished state. Dynamic sources are tracked separately: held torches, NPC lanterns, burning entities.
   - Exposure is computed at several **body sample points** (head, torso, hips, feet; they move with pose). For each point we add up the light from nearby sources, with **a ray from each source to each point**. Opaque blocks cast real shadows; glass, leaves and paper partially attenuate (block tags).
   - Sun and moon are a **directional light** whose angle follows the time of day. A building's shadow therefore falls *sideways* in the evening rather than straight down. Weather, moon phase and fog scale it.
   - A small ambient term (from vanilla sky light) stands in for bounce light.
   - Per-point exposure feeds perception. A guard only "sees" the lit points it has line of sight to, so a lit head poking over a dark wall still counts.
   - Budgeted: nearest N sources per point, cached per tick, LOD for distant NPCs.
2. **What the player sees (client).**
   - The **light gem** (visibility meter) shows the *exact* server value. It's the source of truth, so hiding always feels fair.
   - The "dark is dark" post-process and visual dynamic lights for held torches.
   - Making *rendered* shadows match the gameplay shadows is a separate **R&D track**. Shader packs via Iris already draw sun shadows that roughly match ours. Matching block-light shadows (torch shadows) visually needs a custom renderer (Veil or our own). See **Q2**.

## 5. Sound & noise

- **Decided:** Proper propagation through open space [S-01]. **Proposed:** a server-side flood-fill through air, doorways and openings, muffled by material (cloth < wood < stone), cached per area and invalidated on block change.
- **Decided:** Surface materials change footstep loudness, but only slightly compared with light, sight and movement speed [S-02].
- **Decided:** For QoL, these are **silent**: opening chests, dropping items, bows and arrows. Doors, block breaking, combat and landing make noise [S-03].
- **Decided:** Masking from rain, thunder, flowing water and music [S-04, L-08].
- **Decided:** **You can throw any item.** Some have special effects: glass shatters and leaves no evidence, sharp items deal tiny damage [S-05].
- **Decided:** No visible noise rings for now; sculk left alone [S-06, S-07]. Accessibility toggle for visual audio indicators [U-03].

## 6. Perception & detection

- **Decided:** Each NPC tracks its own awareness of each target, with several stages [D-01].
- **Decided:** Vision is a central cone (fast) plus a peripheral cone (slow). **NPCs can't sense anything behind them**, so sneaking up behind works [D-02].
- **Decided:** Multi-point line of sight with partial cover. Glass is transparent, leaves partial, tall grass hides a crawling player, fences and bars partial, shoji walls show silhouettes [D-03].
- **Decided:** A **short grace period always**, even close up and in front [D-05].
- **Decided:** Above-head detection indicators + alert *sounds* (not voiced lines), configurable [D-04]. Text barks above heads [A-16].
- **Decided:** **No free information**, ever. Hunting works from the last known position [D-08]. In co-op, spotting one player raises the alert but tells NPCs nothing about the others [M-01].
- **Decided:** Evidence NPCs can notice [D-06]:
  - **Must:** bodies, arrows stuck in walls, dropped weapons.
  - **Should:** looted chests, broken blocks, a missing colleague.
  - **Could:** blood, open doors, extinguished lights, footprints.
  - Extinguished lights are **not alarming** in v1 [Z-02].
- **Decided:** No animals as alarms or trackers for now [D-07, N-01].

## 7. AI behaviour

- **Decided:** The full state set:
  - Main chain: Unaware → Curious → Suspicious → Investigating → Searching → Alerted/Hunting → Combat → Lost contact → **Heightened alert** (never fully calm again).
  - Side states: Fleeing, Reporting, Raising alarm, Unconscious, Distracted.
  
  This is all on a layered architecture: perception → knowledge/memory → decision (behaviour trees/utility, JSON-authorable) → actions, plus a group/squad layer [A-01, A-02].
- **Decided:** Information spreads by **line of sight, runners and alarms** (bells, gongs, **signal fires**). It **degrades** as it passes from NPC to NPC [A-03, V-08].
- **Decided:** Alerts are **localised** per compound/zone and cool down over **a few Minecraft days**. Cooldowns keep counting while chunks are unloaded (timestamp-based) [A-04, A-11].
- **Decided:** Patrols use both hand-authored routes (patrol tool) and procedural routes. **Simple routines** so players can learn the routes [A-05, A-06].
- **Decided:** Investigation and coordinated search. Give-up times are short, but **more searchers means a longer search** [A-07, A-08].
- **Decided:** Guards **can't parkour or climb** (ladders and stairs only). Combat AI stays simple: **attack tokens** and **blocking exits**. Advanced combat is the player's domain [A-09].
- **Decided:** Civilians (and **villagers**) run to the nearest guard when they see you trespassing or attacking. Iron golems aren't guards [A-10, X-03].
- **Decided:** Knocked-out NPCs **never wake by themselves**. Another NPC can wake them, and they remember what they saw [A-12].
- **Decided:** Custom pathfinding: doors, locks, ladders, multi-floor buildings [A-13].
- **Decided:** A full **debug overlay**: vision cones, awareness, states, knowledge, sound, paths [A-14].
- **Decided:** Only our own NPCs get the AI for now. Vanilla mobs later. Vanilla mobs aren't suppressed, and fights between them and guards aren't encouraged either [A-15, X-02].
- **Decided:** One AI system, no AI difficulty tiers. Difficulty comes from toggling UI help [K-01].
- **Proposed:** **Lamplighter routine.** At dusk an NPC relights the compound's lights. You get a dark window that closes predictably, and it isn't an alarm (fits Z-02). See **Q10**.

## 8. NPCs & factions

- **Decided:** Archetypes [N-01]:
  - **Must:** civilian, worker, patrol guard, stationary guard, **elite**, **captain**, target/VIP.
  - **Should:** bodyguard.
  - **Could:** merchant, monk, shinobi.
  - **Won't:** dog/handler.
- **Decided:** Humanoid player-shaped NPCs with a **layered outfit system** that powers disguises [N-03].
- **Decided:** Two factions: **Shogunate** and **Shinobi** [N-02]. See **Q5**.
- **Decided:** Named targets with unique behaviour [N-04]. Pickpocketing keys and tools [N-06]. Neutral and trading NPCs much later [N-07].
- **Decided:** Garrisons respawn unless the compound is "destroyed" [N-05]. See **Q6**.

## 9. Social stealth & disguises

- **Decided:** Disguises come from **knocked-out NPCs only (not corpses)**, are found in the world, or are crafted at the tailor [G-01].
- **Decided:** Zones have access rules: public / restricted / hostile, **including time-of-day rules** (e.g. day-only access) [G-02].
- **Decided:** **Enforcers** recognise fake colleagues [G-03]. Climbing or carrying illegal items in the wrong disguise raises suspicion [G-04]. No blending actions [G-05]. No notoriety system [G-08].
- **Decided:** A blown disguise is permanently blown for everyone who **knows**. That knowledge spreads through the propagation system [G-06].
- **Decided:** A dedicated **disguise slot**. Some equipment is "illegal" with a given disguise and raises suspicion [G-07].
- **Decided:** **Drawn vs sheathed weapons.** A sheathed weapon is equipped but not drawn. Drawing it unsettles NPCs unless your disguise allows it [W-08].

## 10. Tools & abilities

**Decided** [E-01…E-07]:
- **Must:** climbing claws, **stilts** (reach a few blocks higher: outer walls, high windows; single-use), grappling hook (expensive), smoke bombs, thrown pebbles, keys.
- **Should:** water shoes (limited), rope arrows, blinding powder, firecrackers, water arrows, fire arrows/starter, caltrops, sleep darts, spyglass tagging, lockpicks.
- **Could:** rope ladder, decoy, shinobi bell, moss arrows, tripwire noisemaker, poison, listening cup, intel scrolls, glass cutter, breathing reed, body sack.
- **Won't:** glider, spring boots, whistle, wind-up toy, snare, mirror, pry bar, rope cutter.
- Access via a **radial wheel / hotkeys** [E-03].
- Resources vary per tool: durability (claws, hook), single-use (stilts), consumables (pebbles, firecrackers) [E-04].
- Lockpicking is a **timing-ring** minigame [E-05].
- Basic tools come from the crafting table; advanced tools and **upgrades** from hideout workstations [E-06, E-07].

## 11. Weapons, combat & lethality

- **Decided:** Weapons [W-01]:
  - **Must:** katana, yari, **hidden blade**, **tessen**.
  - **Should:** wakizashi, kanabo, bō staff, shuriken, blowgun.
  - **Could:** tanto, naginata, bow, teppo (matchlock; no-guns rule, so cosmetic or very limited?).
- **Decided:** Combat depth starts at "mid" (light/heavy, block, parry, dodge, posture) and grows toward full action combat [W-02].
- **Decided:** Takedowns: **rear, air, ledge, water**, and **through shoji walls**. No chains. **Elites resist everything except air takedowns** [W-03]. Non-lethal is fully viable and rewarded [W-04]. Animated in first and third person [W-05].
- **Decided:** Gear is fixed crafted items with upgrades, plus a pool of unique **legendaries**. Apotheosis support is optional [W-06]. No random loot clutter, unless it converts easily to coin [R-05].
- **Decided:** Heavy armor is **louder but not slower**. New armors with set bonuses fill the gaps [W-07]. Materials run iron → netherite and beyond [R-06].
- **Decided:** Blood decals, which also count as evidence and nudge players toward non-lethal [W-09].
- **Decided:** A **lethality system** [V-05, B-02, H-05]:
  - **High lethality**: "bloodlust" (deal *and* take more damage). Compounds come back with more security after their reset. Your hideout gets **raided** if you kill too much too often.
  - **Low lethality**: better healing.
  - Some items and abilities need high or low lethality.
  - Killing a captain disorganises their squads.
- **Decided:** Bodies stay until you leave the area or they're fully hidden (deep water, hay pile) [B-01].

## 12. Hideout, progression & economy

- **Decided:** A claimed hideout (core block) **and** a pre-built hideout map option that sets you up at spawn [H-01].
- **Decided:** Workstations [H-02]:
  - **Must:** forge, tailor, toolmaker, strategy/map room, storehouse.
  - **Should:** stables, shrine/garden.
  - **Could:** apothecary, fletcher, dojo master, allies' quarters.
- **Decided:** **Room recognition** plus single-block or multiblock workstations, depending on the station and its level [H-03].
- **Decided:** Recruits can be sent out as distractions [H-04]. Light passive output [H-05]. Other players can be invited and made officers [H-07].
- **Decided:** **Skill trees: Path of Shinobi (stealth) and Path of Shogunate (combat)**. Mastery challenges unlock capstone skills. A **research tree** for the hideout [R-01].
- **Decided:** Custom **coin** + material costs [R-02]. A contract board (procedural) **and** authored contracts [R-03]. Mission ratings tracked but not important [R-04].

## 13. World & content

- **Decided:** **Procedural compounds** in survival worlds:
  - **Large patrolled camps** are a flagship structure.
  - Also: watchtowers, villages, estates, monasteries, ports, prisons, clan hideouts.
  - **Rare castles** inside compounds.
  - Building layouts and contents are procedural [C-01, V-06].
- **Decided:** A full feudal block palette, plus stealth blocks: shoji (silhouettes, takedowns through them), tatami (quiet), **nightingale floors** (squeak), gravel paths. Roof tiles aren't slippery [C-02].
- **Decided:** Lockable doors and chests. Keys come from captains (pickpocket) or locks get picked [C-03].
- **Decided:** Block breaking/placing is **restricted in some zones and very loud in the rest** [C-04].
- **Decided:** Map-maker tools: zone tool, patrol path tool, NPC spawners + GUI, triggers, and a **mission UI framework with stages** that also carries the narrative later [C-05, C-07].
- **Decided:** New stealth enchantments [X-01].

## 14. UI / presentation

- **Decided:** Pixel art with an **ink-and-paper** accent theme [U-02]. Subtitles always, visual audio cues as an accessibility toggle [U-03]. Guidebook + in-world dojo [U-04].
- **Decided:** **Light gem** always on. Disguise status icon. Objective tracker visible through the spyglass. "In general, indicating UI should require a spyglass or goggles" [U-01]. See **Q4**.
- **Decided:** **Programmer art and basic animations come early and stay swappable**: every texture and model in a predictable path, documented for artists [F-01, Z-01]. Full animation polish comes later; features come first [F-02].
- **Decided:** Post-processing effects to investigate, CC0 sound effects, **dynamic music**, **volumetric-looking smoke** [F-03, F-04, F-05].

---

## Open questions

Answer these with their IDs (Q1, Q2, …), the same way as before. A one-word "agree" is fine where there's a proposal.

**Q1 ★ Exact version: 26.1.2 vs 26.3.** You guessed right that 26.1 has more libraries, specifically **26.1.2**, which the ecosystem has gathered around:
- Curios is stable there (beta on 26.3).
- Apotheosis exists *only* there.
- Everything else on our list exists on both.

My recommendation is **26.1.2**: Java 25, unobfuscated code, the most library support. We can move to a later version at a milestone if the ecosystem shifts. **The Stage 0 scaffold already targets 26.1.2**, built from the official NeoForge MDK, so switching later is a port but not a rewrite. Agree?

**Q2 ★ Visual vs gameplay shadows.** In the proposal in §4:
- The **AI uses true ray-traced shadows** (from sun, moon, torches and lanterns), and the light gem always shows exactly that value.
- Making what you *see* match exactly is a separate rendering track. With Iris shaders, sun shadows will roughly match. Torch shadows won't be drawn until we build a custom renderer later.

Is it OK for the light gem to be the source of truth early on, with visual shadow rendering as a later R&D stage? And should the modpack ship with Iris and a recommended shader pack?

**Q3 Crawl anywhere.** P-01 says to keep vanilla stances, but P-08 needs crawling to hide in grass. Should I add a **crawl toggle key** (vanilla crawl pose, anywhere) with a fixed animation?

**Q4 ★ Which UI needs the spyglass or goggles?** Proposal:
- **Always on:** light gem, above-head detection indicators (configurable), disguise status icon, alert sounds.
- **Spyglass/goggles only:** enemy tags, objective markers, NPC details (archetype/state), zone boundaries and access rules.

Agree?

**Q5 ★ Factions.** Is the player a Shinobi, so Shinobi NPCs are allies, recruits and contract givers? Or are the Shinobi a rival clan you also infiltrate? And is "Path of Shogunate" just a combat playstyle name, or does it mean siding with the Shogunate?

**Q6 What does "destroyed" mean for a compound?** Proposal: each compound has a **command post** (captain + war banner/supply cache). Defeat or KO the captain *and* burn or steal the banner, and the compound is **broken**: no more respawns. Should it be re-occupied after a long time (e.g. tens of in-game days) so the world doesn't empty out?

**Q7 Combat foundation.** Epic Fight isn't available on 26.x. Proposal: **our own combat system** (light/heavy, block, parry, posture, takedowns) for player and NPCs, with optional Better Combat compat for player swings. Agree?

**Q8 Lethality naming.** The working names are "Bloodlust" (high lethality) and "Serenity" (low). Is that fine as a working name, or do you have names in mind?

**Q9 Unranked pillars.** Social stealth/disguises and map-maker tooling weren't ranked. I've placed **social stealth after Movement II**. **Map-maker tools are built bit by bit**: each tool ships with the system it configures (zone tool with zones, patrol tool with patrols), and the full suite comes later. OK?

**Q10 Lamplighter routine.** An NPC relights the compound's lights at dusk, which isn't an alarm. Include it?

**Q11 Teppo.** It's on the Could list, but guns are a hard no. Drop it, or keep it as a loud, slow, very limited siege/alarm weapon for NPCs only?

**Q12 Builds (resolved).** You build locally and report back. Requirements and commands are in the README.

Optionally, if you want me to compile and run GameTests inside cloud sessions too, the environment's network policy needs these hosts allowed:
- `maven.neoforged.net`
- `libraries.minecraft.net`, `piston-meta.mojang.com`, `piston-data.mojang.com`, `launchermeta.mojang.com`, `resources.download.minecraft.net`
- later, dependency mavens (Modrinth/Curse mavens, `maven.theillusivec4.top`, `dl.cloudsmith.io`, `maven.terraformersmc.com`, `maven.blamejared.com`)

The setting is under *Network access* → *Custom* in the environment settings; see <https://code.claude.com/docs/en/cloud-environments#network-access>. It would also need JDK 25 installed via a setup script. Not required.
