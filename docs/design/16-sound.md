# 16 — Hearing & distractions (Stage 6) ✅ implemented

> **Status (2026-10-06):** implemented as designed and covered by 8 GameTests (`gametest/SoundTests`). Additions while building it: noise sources take an optional `from_npcs` flag (default off, so guards opening doors don't alarm each other; on for combat), the default sources also cover lightning, note blocks, goat horns and primed TNT, and diagonal steps between two blocks that only touch at an edge are charged as if through the cheaper of them, so diagonal walls still muffle. Performance: a worst-case sprint footstep (listener sealed off, nothing reachable) floods about 1,600 nodes in 0.5–1 ms; `soundNodesPerTick` defaults to 8,000.

Guards can now **hear**. Sound travels through open space and doorways and is muffled by walls (S-01). Footsteps depend on how you move (S-02). Rain, thunder, water and music mask noise (S-04). **You can throw any item** to distract (S-05). All of this is server-side and data-driven.

## 1. Noise events

A noise has:
- a position
- a **loudness**: how far it carries in open air, in blocks
- a **kind**: footstep, landing, door, block, combat, impact, shout or explosion
- an optional **cause**: the player who made it, when the noise gives them away (footsteps, doors, combat). Impacts from thrown items and shouts have no cause; they point at a spot, not at you.

| Source | Loudness | Notes |
|---|---|---|
| Footsteps: sneaking / walking / sprinting | 2 / 6 / 12 | Emitted every ~1.5 blocks moved. Crawling (S7) will be 1 |
| Surface | ×1.25 on loud surfaces (gravel, metal, glass…) / ×0.8 on quiet ones (wool, carpet, moss, snow, grass) | Small on purpose (S-02). Tags `emergentstealth:loud_surfaces` and `quiet_surfaces` |
| Landing | 4 + 1.5 × fall distance (max 16) | Sneaking halves it |
| Doors, gates, trapdoors | 8 | Opening and closing |
| Breaking / placing blocks | 10 / 6 | |
| Melee hits and damage | 10 | Also NPCs fighting |
| Deaths | 10 | |
| Explosions | 48 | |
| Thrown item impact | 10 (glass 14) | See §5 |
| Shouts and screams | 24 | Emitted by NPCs (S4) |
| Snuffing a torch | 2 | Not alarming (Z-02); a guard right next to you may glance over |
| **Silent** | 0 | Chests and containers, dropping items, bows and arrows, eating and drinking (S-03) |

Non-footstep sources come from **vanilla game events** through NeoForge's `VanillaGameEvent`, mapped in a datapack registry `emergentstealth/noise_source/*.json` (`game_event`, `loudness`, `kind`, `attributable`). Packs can retune or add sources without code.

## 2. Propagation (S-01)

- **Clear line to the listener:** if the straight line from the noise to a listener's head passes only through air, the cost is just the distance. This is the common outdoor case and nearly free.
- **Otherwise a flood fill:** a shortest-path search (26 neighbours) from the noise through the blocks around it, bounded by the loudness.
  - Moving through air, open doors and other non-solid blocks costs 1 per block.
  - Passing **through** a solid block costs extra:

    | Blocks | Tag | Extra cost |
    |---|---|---|
    | Glass, panes, doors, trapdoors, leaves, fences | `muffles_sound_light` | 3 |
    | Any other solid block | | 8 |
    | Wool and other dampening blocks | `muffles_sound_heavy` | 16 |

  - So sound **goes round the corner and through the open door** before it goes through the stone wall.
- **Heard intensity:** `1 − cost / effectiveLoudness`. Anything above 0 is heard.
- **Effective loudness:** `effectiveLoudness = loudness × hearing × (1 − masking)`. `hearing` is a new perception-profile field, default 1.0.
- **Performance:**
  - Noises with no NPC in range are dropped immediately.
  - The fill stops once every listener in range is reached, and stays within ±10 blocks vertically.
  - A per-tick node budget (`soundNodesPerTick`, server config) defers the rest to the next tick, which reads as natural sound delay.

## 3. Masking (S-04, L-08)

Masking is computed at the listener and refreshed once a second:

| Source | Masking |
|---|---|
| Rain | 25% |
| Thunderstorm | 50% |
| Flowing water within 6 blocks | 30% |
| Jukebox playing within 16 blocks | 40% |

Several sources combine as `1 − Π(1 − m)`.

## 4. What NPCs do with it (wired into the S4 brain)

- **Heard a player** (attributable noise): awareness of that player rises with the intensity, **capped at 0.65**. The last known position becomes the noise position. Guards investigate; civilians look.
- **Heard something** (no cause):
  - quiet (intensity < 0.5): **curious**, turns to look
  - louder: guards **investigate the spot**
- **Heard a shout:** guards go to the shouter; civilians look.
- **Ears have no blind spot:** hearing works in any direction, so it's how guards notice you behind them.

## 5. Throwing any item (S-05)

- **Throw key** (default **G**): throws one of the held item in an arc. A short tap is a lob; holding the key charges a longer throw. The key is a client intent; the server validates it and spawns the item.
- **On impact:**

  | Item | Impact noise | Afterwards |
  |---|---|---|
  | Glass (blocks, panes, bottles) | 14 | Shatters and leaves no evidence |
  | Sharp items (swords, axes, shears; tag `emergentstealth:sharp_throwables`) | 10 | Deal 1 damage if they hit a mob, then drop |
  | Anything else | 10 | Drops where it lands, and can be picked up again |

- Cooldown: 0.5 s. Works in survival and multiplayer.
- **Pebbles** become a cheap dedicated throwable in S8/S12; for now cobblestone works.

## 6. Debug view

In the AI debug view (ops only; S-06 means no rings for normal players):
- each noise shows as a ring at its source, sized by loudness, for 2 seconds
- lines go to every NPC that heard it, coloured by intensity, with its kind and cost
- NPC labels show `heard: <kind> 0.62`

## 7. Tests (GameTests)

1. A noise behind a solid wall is muffled, while the same noise through an open doorway path is heard.
2. Sprinting is heard where sneaking at the same spot is not.
3. Rain masks a footstep that is heard in clear weather.
4. A thrown item landing behind a guard makes it turn and investigate the impact spot.
5. Opening a chest is silent; opening a door is heard.
6. Glass shatters and leaves nothing; a stone drops at the landing spot.

## 8. Not in this stage

- Visual sound cues for accessibility (U-03, Track E).
- Sculk stays vanilla (S-07).
- Firecrackers and other noise tools (S12).
- Nightingale floors and other feudal blocks (S14).
- Information sharing beyond shouts (S9).
