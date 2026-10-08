# Emergent Stealth — Staged Roadmap (v0)

Built from the [design decisions](01-design-decisions.md) and your pillar ranking [Z-01]:
1. Perception
2. Light
3. Behaviour
4. Movement
5. Sound
6. Tools
7. Combat
8. Worldgen
9. Hideout
10. Polish

Programmer art and basic animations are a **standing requirement of every stage** rather than a stage of their own.

## How stages work

- **Each stage = one or more small PRs.** Concurrent features get their own branches [T-10, Z-04].
- **📄 = a short design doc you review first** [Z-03]. Work inside an approved doc goes ahead without another sign-off.
- **Definition of done** for every stage:
  1. It compiles and runs (you build locally and report back).
  2. It works in multiplayer: server-authoritative, synced to clients.
  3. Its config and datapack hooks are documented.
  4. Placeholder art and lang entries are in place.
  5. Debug overlay support, for AI-facing systems.
  6. GameTests where the behaviour can be checked automatically.
- **Playtest loop:** you play after each session and multiplayer every few days [T-09]. Feedback goes in GitHub issues or chat.

---

## Track A: Foundations → Vertical slice (milestone M1)

The goal of M1 is the **vertical slice** you approved [Z-02]: one guard type that sees, reads light and hears; full alert states; patrols; the light gem; torches you can put out (not alarming); a pebble and a smoke bomb; a takedown; the debug overlay; and a test compound.

| Stage | Contents | Doc |
|---|---|---|
| **S0 Foundations** ✅ | NeoForge 26.1.2 / Java 25 / ModDevGradle scaffold, mod ID `emergentstealth`, server + client config, creative tab, programmer-art pipeline (`tools/programmer_art`), second dev client for co-op testing, docs | 📄 [10-architecture](10-architecture.md) |
| **S1 NPC base + debug framework** ✅ | Humanoid `StealthNpc` entity (player-shaped, **layered outfit renderer**, placeholder skins); archetypes as datapack JSON; spawn command/egg; the **debug overlay** framework (server → client debug data, op-gated, toggled by the Debug Lens); Shoulder Surfing compat check | 📄 11-npc-and-debug |
| **S2 Perception: sight** 🧪 *(+ S4a detection v0: states, reactions, HUD; see [14](14-detection-v0.md))* | Central + peripheral vision cones (**nothing behind**), multi-point line-of-sight rays with partial cover (glass, leaves, grass, bars via block tags), deep water (>5) blocks sight, per-NPC per-target **awareness meter** with stages and a short grace period, sprint/stance modifiers, **perception scheduler + LOD** (~20 full-fidelity NPCs, the rest reduced), first GameTests | 📄 12-perception |
| **S3 Light & shadow (gameplay)** 🧪 | Light-source index per chunk section, dynamic lights (held torches, NPC lanterns), **ray-traced exposure** at body sample points (block lights + **directional sun/moon shadows**, weather/moon/fog), the **light gem** HUD, **extinguishing** (hand snuff; non-powered lights only; unlit variants for vanilla lights), relighting, guards raising torches in the dark | 📄 13-light-and-shadow (the biggest R&D doc) |
| **S4 Behaviour core** 🧪 | Knowledge/memory blackboard, **behaviour-tree runtime (JSON-authorable)**, the full state machine through Heightened alert, investigate, coordinated search (longer with more searchers), hunting from the **last known position only**, attack tokens and blocking exits, **above-head indicators + alert sounds**, text barks, basic NPC melee | 📄 [14-ai-behaviour](14-ai-behaviour.md) |
| **S5 Navigation & patrols** 🧪 | Custom pathfinding (doors open/close/lock, ladders, stairs, multi-floor; **no climbing for guards**), **patrol tool** (waypoints, waits, look directions), procedural patrol routes, simple routines | 📄 [15-navigation](15-navigation.md) (procedural routes moved to S15) |
| **S6 Hearing v1 & distractions** 🧪 | Server-side **sound propagation** (flood-fill through openings, muffled by material, cached), quiet/loud surfaces (minor), sprint/landing/door/combat noise, silent chests/drops/bows (QoL), masking (rain, **thunder**, water, music), **throw any item** (glass leaves no trace, sharp items nick) | 📄 [16-sound](16-sound.md) |
| **S7 Player verbs I** ⏳ | **Crawl toggle** (Q3) with a proper animation, sprint detectability, **rear + air takedowns** (lethal and non-lethal), **knockouts** (no natural waking; can be woken by others, who remember), **body dragging**, evidence: bodies, stuck arrows, dropped weapons | 📄 [17-takedowns-bodies](17-takedowns-bodies.md) |
| **S8 Slice assembly** | **Smoke bomb** (server-side sight-blocking volume + placeholder visual), **test compound** structure, slice tuning pass | — |

**→ M1 playtest: "one compound, one guard type, all core loops."**

**→ Beta 1 (2026-10-08 plan):** M1 plus the core toolkit pulled forward from S12: wheel, pebbles, smoke, firecrackers, blinding powder, caltrops, water/fire arrows, blowgun and sleep darts, keys, locks, lockpicks, spyglass tagging. Shipped with a test compound and a tester guide. See [21-toolkit](21-toolkit.md) §5.

## Track B: Infiltration sandbox (milestone M2)

| Stage | Contents | Doc |
|---|---|---|
| **S9 Zones, alerts & propagation** | **Zone tool** + zone volumes (public/restricted/hostile, **time-of-day rules**), **localised alert levels** that cool down over a few MC days (timestamp-based, keep counting while unloaded), propagation by **line of sight, shouts, runners**, **bells/gongs/signal fires** (sabotageable), **information decay**, spawn suppression, block break/place restrictions or loudness in zones, civilians and **villagers** run to guards | 📄 18-zones-and-propagation |
| **S10 Movement II** | **Climbing claws** (`#unclimbable` blacklist tag), **ledge hang + shimmy**, climbing up into gaps (no auto 1-block vault), stamina (parkour/climb only), **stilts**, **grappling hook**, **ledge + water takedowns** | 📄 19-traversal |
| **S11 Social stealth** | Outfits from **knocked-out NPCs only**/world/crafting, **disguise slot**, zone access by outfit, **enforcers**, suspicious actions (climbing, illegal items), **drawn vs sheathed weapons**, blown disguises spread through propagation, disguise status icon | 📄 20-disguises |
| **S12 Toolkit I** | **Tool belt + radial wheel/hotkeys**, firecrackers, blinding powder, water/fire/rope arrows, caltrops, sleep darts/blowgun, **spyglass tagging** (limited), **keys + lockable doors/chests**, **timing-ring lockpicking**, pickpocketing (keys/tools), water shoes | 📄 21-toolkit |
| **S13 Combat** | Weapon framework (Q7): light/heavy, block, parry, dodge, posture; **katana, yari, hidden blade, tessen** first; stealth damage bonuses; elites (resist everything but air takedowns); heavy armor **louder, not slower**; **lethality system** (Bloodlust/Serenity, Q8); blood decals as evidence; captain death disorganises squads | 📄 22-combat-and-lethality |

**→ M2 playtest: "a full infiltration sandbox in a hand-placed test map."**

## Track C: The world (milestone M3, survival-playable)

| Stage | Contents | Doc |
|---|---|---|
| **S14 Feudal palette** | Shoji (silhouettes, takedowns through them), tatami (quiet), **nightingale floors**, gravel, roof tiles (not slippery), feudal lights (paper lanterns, braziers, sconces, candles), hay bales you can hide in, crawlspace/rafter-friendly blocks, stealth enchantments | 📄 23-blocks |
| **S15 Procedural compounds** | A compound generator (procedural layout from building modules). **Large patrolled camps first**, then watchtowers, villages, estates, monasteries, ports, prisons, clan hideouts, **rare castles inside compounds**. Garrisons and respawns (Q6), auto zones/patrols/lights | 📄 24-worldgen |
| **S16 Hideout** | Hideout core, **room recognition** + single-block/multiblock workstations: **forge, tailor, toolmaker, strategy room, storehouse** first. Research tree, light passive output, **raids at high lethality**, members/officers, pre-built hideout map option, EMI plugin | 📄 25-hideout |

**→ M3 playtest: "survival world, find a camp, infiltrate, come home."**

## Track D: Progression & content (milestone M4)

| Stage | Contents | Doc |
|---|---|---|
| **S17 Progression** ⏳ (foundations pulled forward: [26](26-progression.md)) | **Path of Shinobi / Path of Shogunate** skill trees, mastery challenges → capstones, coin currency, legendary pool (optional Apotheosis compat) | 📄 26-progression |
| **S18 Contracts & missions** | Contract board (procedural + authored), **mission UI/HUD framework with stages** (spyglass-gated objective tracker), ratings tracked | 📄 27-missions |
| **S19 Map-maker suite** | NPC spawner blocks + **GUI editor**, trigger volumes, mission scripting, scenario packaging (zone and patrol tools already exist from S5/S9) | 📄 28-map-tools |

## Track E: Presentation (runs alongside from M1 on; polish lands after M4)

- **Visual light & shadow R&D** (Q2): "dark is dark" post-process, visual dynamic lights, a custom shadow-rendering investigation (Veil or our own), Iris coexistence. 🧪 First pass merged ([doc 30](30-visual-lighting.md)): baked block-light shadows from the gameplay model, dynamic lights, dark is dark; works with Sodium and Iris. 🧪 Sun & moon shadows baked into sky light on `claude/visual-lighting` (doc 30 §10).
- **Animation:** PAL player animations (takedowns, climbing, dragging, crawl); NPCs use vanilla keyframes plus an in-house procedural pose layer (no GeckoLib; see [research-animation](research-animation.md)).
- **Smoke:** volumetric-looking smoke.
- **Music and sound:** dynamic music by alert state; CC0 sound effects; accessibility visual sound cues.
- **UI framework "Sumi"** (ink and paper; [31](31-ui-framework.md)): config screen, skill tree, dialogue. Pulled forward 2026-10-08.
- **Onboarding and integrations:** guidebook, dojo training course, Jade/EMI polish.

## Later / backlog

- End-game powers (possessing small animals).
- Better AI for vanilla mobs.
- Other cultures and regions.
- Modern "military stealth" expansion.
- KubeJS and FTB Quests integrations.
- Neutral and trading NPCs.
- Remaining "Could" tools and weapons.
