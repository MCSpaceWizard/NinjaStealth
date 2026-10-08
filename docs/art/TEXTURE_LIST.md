# Texture list, in priority order

**Root path:** every path below is relative to `src/main/resources/assets/emergentstealth/textures/`. Saving a PNG at that path replaces the placeholder; no code change is needed. See [STYLE_GUIDE.md](STYLE_GUIDE.md) for the rules.

**Status column:**

| Status | Meaning |
|---|---|
| **P** | Programmer-art placeholder exists |
| **B** | Placeholder exists on a feature branch (arriving with the beta toolkit) |
| **N** | Not created yet (planned) |

Priorities follow the Beta 1 plan: what testers see most comes first.

## Priority 1: the beta toolkit and core HUD (testers use these constantly)

| # | Path | Size | Status | What it is |
|---|---|---|---|---|
| 1 | `gui/sprites/hud/light_gem_frame.png` | 32×16 | P | Light gem frame (lozenge rim) |
| 2 | `gui/sprites/hud/light_gem_dark.png` | 32×16 | P | Gem fill when hidden (near-black glass) |
| 3 | `gui/sprites/hud/light_gem_lit.png` | 32×16 | P | Gem fill when lit (warm amber), blended over the dark one |
| 4 | `item/pebble.png` | 16×16 | B | Small grey stone, the basic distraction |
| 5 | `item/smoke_bomb.png` | 16×16 | B | Round clay or paper ball with a short fuse |
| 6 | `item/firecracker.png` | 16×16 | B | Red paper tube string, lacquer accent |
| 7 | `item/blinding_powder.png` | 16×16 | B | Small cloth pouch with pale powder |
| 8 | `item/caltrops.png` | 16×16 | B | A handful of iron caltrops |
| 9 | `block/caltrops.png` | 16×16 | B | Caltrops scattered on the ground (cutout, flat) |
| 10 | `item/water_arrow.png` | 16×16 | B | Arrow with a blue water-filled bulb head |
| 11 | `item/fire_arrow.png` | 16×16 | B | Arrow with a cloth-wrapped burning head |
| 12 | `entity/projectiles/water_arrow.png` | 32×32 | B | In-flight water arrow (vanilla arrow layout) |
| 13 | `entity/projectiles/fire_arrow.png` | 32×32 | B | In-flight fire arrow |
| 14 | `item/blowgun.png` | 16×16 | B | Bamboo blowgun (diagonal, like a tool) |
| 15 | `item/sleep_dart.png` | 16×16 | B | Thin dart with a feather tuft |
| 16 | `entity/projectiles/sleep_dart.png` | 32×32 | B | In-flight dart |
| 17 | `item/key.png` | 16×16 | B | Old iron key with a lacquer tag |
| 18 | `item/lockpick.png` | 16×16 | B | Pair of thin picks |
| 19 | `item/locksmiths_kit.png` | 16×16 | B | Map-maker tool: small wooden box (creative) |

## Priority 2: stealth gear and NPC readability

| # | Path | Size | Status | What it is |
|---|---|---|---|---|
| 20 | `item/shinobi_hood.png` | 16×16 | P | Black hood/mask |
| 21 | `item/shinobi_garb.png` | 16×16 | P | Black jacket |
| 22 | `item/hakama.png` | 16×16 | P | Indigo pleated trousers |
| 23 | `item/tabi.png` | 16×16 | P | Split-toe boots |
| 24 | `entity/equipment/humanoid/shinobi.png` | 64×32 | P | Worn shinobi set (head, body, arms, boots) |
| 25 | `entity/equipment/humanoid_leggings/shinobi.png` | 64×32 | P | Worn hakama |
| 26 | `entity/npc/body/body_1.png` … `body_4.png` | 64×64 | P | Four base bodies (skin tones, faces, hair); no clothing |
| 27 | `entity/npc/outfit/ashigaru_under.png` | 64×64 | P | Ashigaru cloth layer |
| 28 | `entity/npc/outfit/ashigaru_armor.png` | 64×64 | P | Ashigaru armour, plus the jingasa hat on the head |
| 29 | `entity/npc/outfit/samurai_under.png` | 64×64 | P | Samurai under-robe |
| 30 | `entity/npc/outfit/samurai_armor.png` | 64×64 | P | Full lacquered armour, kabuto |
| 31 | `entity/npc/outfit/taisho_armor.png` | 64×64 | P | Captain armour with gold trim (and a banner later) |
| 32 | `entity/npc/outfit/kimono_noble.png` | 64×64 | P | Daimyo's rich layered kimono |
| 33 | `entity/npc/outfit/kimono_plain.png` | 64×64 | P | Townsfolk kimono |
| 34 | `entity/npc/outfit/labourer.png` | 64×64 | P | Hemp work clothes, headband |

## Priority 3: world blocks and map-maker tools

| # | Path | Size | Status | What it is |
|---|---|---|---|---|
| 35 | `block/unlit_torch.png` | 16×16 | P | Torch with a charred, smoking-out tip |
| 36 | `block/unlit_soul_torch.png` | 16×16 | P | Same, soul variant |
| 37 | `block/unlit_copper_torch.png` | 16×16 | P | Same, copper variant |
| 38 | `block/unlit_lantern.png` | 16×16 | P | Lantern with dark glass (vanilla lantern layout) |
| 39 | `block/unlit_soul_lantern.png` | 16×16 | P | Same, soul variant |
| 40 | `item/patrol_baton.png` | 16×16 | P | Map-maker tool: lacquered baton with a cord |
| 41 | `item/debug_lens.png` | 16×16 | P | Developer tool: jade lens |
| 42 | `item/stealth_npc_spawn_egg.png` | 16×16 | P | Spawn egg (ink and paper) |

## Priority 4: Sumi UI (with the UI framework, next phase)

Sizes and nine-slice insets are given in `docs/assets.md` when the framework lands. Planned:

| # | Path | Status | What it is |
|---|---|---|---|
| 43 | `gui/sumi/panel_paper.png` | N | Washi panel, nine-slice, visible grain |
| 44 | `gui/sumi/frame_brush.png` | N | Sumi brush frame, nine-slice |
| 45 | `gui/sumi/button.png`, `button_hover.png`, `button_pressed.png` | N | Paper buttons with an ink edge |
| 46 | `gui/sumi/seal.png`, `seal_owned.png`, `seal_locked.png` | N | Skill tree nodes (hanko seals) |
| 47 | `gui/sumi/divider.png`, `scrollbar.png`, `tab.png`, `tab_active.png` | N | Brush divider, ink scrollbar, tabs |
| 48 | `gui/sumi/dialogue_box.png`, `portrait_frame.png`, `name_seal.png` | N | Dialogue UI |
| 49 | `gui/sprites/hud/technique_*.png` | N | One small icon per technique: Still Breath, Light Step, Feint, Iron Focus |
| 50 | `gui/sprites/hud/tool_wheel_*.png` | N | Radial tool wheel ring and segment highlight |

## Priority 5: later stages

| Path | Status | What it is |
|---|---|---|
| `block/` feudal set: shoji, tatami, nightingale floor, paper lantern, brazier, roof tiles, hay bale | N | S14 blocks |
| `item/` climbing claws, grappling hook, rope arrow, stilts | N | S10 traversal tools |
| `entity/npc/outfit/` monk, shinobi (enemy clan), merchant, bodyguard | N | More archetypes |
| Animations | N | Delivered as Blockbench files (see `docs/design/research-animation.md`), not PNGs |
