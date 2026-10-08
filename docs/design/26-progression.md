# 26 — Progression foundations: skills, abilities, stealth gear (S17, pulled forward) 📄

You asked (2026-10-08) for **equipment, tools and abilities** plus the **foundations of the skill trees**. Tools are in [21-toolkit](21-toolkit.md). This doc covers the rest:
- the **data-driven skill system**
- **active techniques** (abilities)
- **stealth gear** (equipment with stealth stats)

The look of the skill tree comes from the new UI framework ([31-ui-framework](31-ui-framework.md)).

## 1. Skill trees (R-01)

- **Two paths:** **Path of the Shinobi** (stealth) and **Path of the Shogunate** (combat).
- **Data-driven:** skills live in the datapack registry `emergentstealth/skill/<id>.json`, so map makers and modpacks can add or rebalance them. Each skill has:

  | Field | Meaning |
  |---|---|
  | `path` | `shinobi` or `shogunate` |
  | `position` | Grid position in the tree screen (column, row) |
  | `cost` | Skill points |
  | `requires` | Prerequisite skills (any or all) |
  | `effects` | A list of effects (below) |
  | `icon` | An item or a sprite |
  | `capstone` | Needs a mastery challenge (later) |

- **Effects** are typed and pluggable:

  | Type | Example |
  |---|---|
  | `stealth_stat` | `footstep_loudness ×0.85`, `visibility ×0.9`, `takedown_speed ×1.25`, `drag_speed ×1.15`, `lockpick_window ×1.3`, `tag_count +1`, `smoke_duration ×1.5` |
  | `attribute` | Vanilla attribute modifiers (sneak speed, max health…) |
  | `unlock_technique` | Grants an active technique (§3) |
  | `unlock_recipe` | Lets you craft an advanced tool |

- **One stat pipeline:** every system reads stealth stats from one place, `StealthStats.get(player, STAT)`. Skills, gear and later disguises all feed it, so nothing is hard-coded per skill.

## 2. Earning skill points: Insight

- You earn **Insight** for stealth deeds. Every 100 Insight is a skill point.

  | Deed | Insight |
  |---|---|
  | Knock out a guard unseen | 15 |
  | Kill a guard unseen | 10 |
  | Hide a body (no one finds it for 5 minutes) | 10 |
  | Pick a lock | 5 |
  | Distract a guard (it investigates your noise) | 3 |
  | Escape a search (the search ends without finding you) | 20 |

  Shogunate deeds (combat wins, parries) arrive with S13.
- **Two pools:** Insight is tracked per path. Shinobi deeds fund Shinobi points; combat deeds fund Shogunate points.
- **Respec** at the hideout shrine (S16). For now there's an op command, `/es skills reset`.
- **Persistence:** saved on the player, synced to that player only.

## 3. Techniques: active abilities (grounded, no magic)

- **Ability key** (default **X**): uses the selected technique. **Hold X** to pick from your unlocked techniques (it uses the same radial wheel component as tools).
- **Starter set:**

  | Technique | Path | Effect | Cooldown |
  |---|---|---|---|
  | **Still Breath** | Shinobi | While crouched and not moving, your visibility drops by 40% for up to 8 s | 20 s |
  | **Light Step** | Shinobi | 6 s of silent footsteps (landing noise halved) | 30 s |
  | **Feint** | Shinobi | Throw your voice: a noise at the block you look at (≤16 blocks), like a pebble but no item | 25 s |
  | **Iron Focus** | Shogunate | 5 s: guards attacking you are slowed (placeholder until S13 combat) | 40 s |

- **Feedback:** cooldowns show next to the light gem. All techniques are server-validated.

## 4. Stealth gear (equipment)

- **Mechanism:** armour and items get stealth stats through a **data component** `emergentstealth:stealth_stats`, for example `{"visibility": 0.9, "footstep_loudness": 0.8}`. Pack makers can add it to any item. Default **item tags** cover vanilla armour:
  - Heavy metal armour is louder, not slower (S13 plan): footsteps ×1.2 for iron/diamond/netherite pieces.
  - Leather is neutral.
- **First stealth gear:**

  | Item | Slot | Stats |
  |---|---|---|
  | **Shinobi hood** | Head | Visibility ×0.95 |
  | **Shinobi garb** | Chest | Visibility ×0.9 (dark cloth) |
  | **Hakama trousers** | Legs | Footstep loudness ×0.95 |
  | **Tabi** | Feet | Footstep loudness ×0.8 |

  The set is crafted from black wool, leather and string, with low armour values.
- **Disguises** (S11) will use the same stat pipeline later.

## 5. Not in this stage

- Mastery challenges and capstones (they need missions, S18).
- Coin and the economy, hideout workstations and research (S16).
- Shogunate combat skills beyond placeholders (S13).

## 6. Tests

1. A skill's `stealth_stat` changes the stat (footsteps quieter) and stacks with gear.
2. Unlocking needs points and prerequisites; points are spent and persist.
3. Insight is awarded for an unseen knockout; 100 Insight gives a point.
4. Still Breath lowers visibility only while still; its cooldown is enforced.
5. Tabi lower footstep loudness; iron boots raise it.
