# 14 — Behaviour core (Stage 4) 📄

Stage 4a ([14-detection-v0](14-detection-v0.md)) gave NPCs states and hard-coded reactions. Stage 4 turns that into the layered AI from A-01/A-02: guards **remember**, **search together**, **take turns attacking**, **talk**, and react to **sound** (S6). Map makers can **author what each NPC does** as JSON.

## 1. Layers

| Layer | What it does | Where |
|---|---|---|
| Perception | Sight (S2) and hearing (S6) produce *stimuli* | `ai/perception`, `stealth/sound` |
| **Knowledge** | A per-NPC blackboard: targets (awareness, last known position), the latest interesting noise, the current point of interest *and why* (seen / heard / hurt / shout), search group, incident timestamps | `ai/brain/Knowledge` |
| **Alert state machine** | The design rules (A-01, D-08): state transitions stay in code | `StealthBrain` |
| **Behaviour tree** | *What to do* in each state; data-driven, per archetype | `ai/behaviour` + datapack |
| Actions | Move, look, attack, bark, shout, search, flee, routine | BT action nodes |
| Group | Search groups and attack tokens per target | `ai/group` |

**Calm NPCs** (unaware or heightened) keep their routine from S5. The behaviour tree runs while an NPC is in any active state.

## 2. States (changes from v0)

- **Sound drives states.** An unattributed noise (a thrown pebble, a shout) makes an NPC **curious**: it turns to look. A loud or repeated noise makes a guard **investigate** the spot.
- **Player noises** (footsteps, doors, landing) raise awareness of that player, but **only up to 0.65**. They set the last known position to *where the noise was*. You can be heard and hunted down, but only being seen detects you (D-05, D-08).
- **Lost contact** is the existing `hunting` → `searching` chain.
- **Distracted** (A-01) is `curious` or `investigating` with the cause "noise". The debug label shows the cause.
- Unconscious (S7), reporting and raising the alarm (S9) arrive with those stages.

## 3. Behaviour trees (data)

- **Datapack registry:** `data/<ns>/emergentstealth/behaviour/<id>.json`.
- **Archetypes:** optional `"behaviour": "<id>"`. Defaults by role: guards use `emergentstealth:guard`; civilians, workers and targets use `emergentstealth:civilian`.
- **Ticking:** the tree is ticked every server tick for active NPCs. Nodes return `success`, `failure` or `running`. A running node resumes next tick unless a higher-priority branch takes over.

| Kind | Nodes |
|---|---|
| Composites | `sequence`, `selector`, `random` (weighted) |
| Decorators | `cooldown` (seconds), `timeout` (seconds), `chance`, `invert`, `once_per_state` |
| Conditions | `state` (one of), `cause` (seen/heard/hurt/shout), `can_see_target`, `has_attack_token`, `near_poi` (distance), `role` |
| Actions | `move_to` (poi / search_point / home / away_from_poi; speed), `look_at_poi`, `look_around` (seconds), `wait`, `attack`, `hold_ring` (surround the target), `bark` (situation), `shout`, `routine`, `draw_torch` |

Example (the core of `guard.json`):
```json
{ "type": "selector", "children": [
  { "type": "sequence", "children": [
    { "type": "state", "states": ["combat"] },
    { "type": "once_per_state", "child": { "type": "bark", "situation": "spotted" } },
    { "type": "cooldown", "seconds": 4, "child": { "type": "shout" } },
    { "type": "selector", "children": [
      { "type": "sequence", "children": [ { "type": "has_attack_token" }, { "type": "attack" } ] },
      { "type": "hold_ring", "min": 4, "max": 6 } ] } ] },
  { "type": "sequence", "children": [
    { "type": "state", "states": ["searching"] },
    { "type": "move_to", "target": "search_point", "speed": 0.9 },
    { "type": "look_around", "seconds": 2 } ] }
] }
```

The two default trees reproduce today's v0 behaviour plus the new group, bark and sound features. A broken file logs an error and the NPC falls back to the default tree.

## 4. Coordinated search (A-07, A-08)

- Guards searching for the same target, or the same spot within 12 blocks, form a **search group**.
- **Search points:**
  - The group generates points around the last known position: up to 12, within 10 blocks, reachable by path.
  - Points are **biased toward hiding places**: dark spots (exposure < 0.25, from the S3 model), behind cover and inside bushes. Guards check the shadows.
- **Splitting up:** each guard claims the nearest unclaimed point. At a point it looks around, then claims the next one.
- **Duration:** `searchSeconds × (1 + 0.3 × (guards − 1))`, capped at 2×. More searchers means a longer search (A-08).
- **Ending:** a sighting ends the search for the whole group; the guard that sees you goes to combat. Its **shout** (below) brings the others.

## 5. Combat: attack tokens and holding exits (A-09)

- At most **2 guards attack a target at once** (`attackersPerTarget`, server config).
- **The others hold a ring** 4–6 blocks from the target, spread out on the side away from the attackers and facing it. This is v1 of "blocking exits"; real exit-finding comes with zones in S9.
- **Token hand-over:** tokens pass on when an attacker loses sight of the target, is hurt badly, or dies.
- Melee stays vanilla. The combat system itself is S13.

## 6. Barks and shouts

- **Barks** (A-16): short text above the head, e.g. "Hm? Who's there?", "Must have been the wind…", "Intruder!". There's a set per situation:
  - `curious`, `suspicious`, `investigate`, `spotted`, `lost`, `search`, `give_up`, `heard`, `flee`
  - Lines are lang keys `bark.emergentstealth.<situation>.<n>`, so writers can add lines in the lang file.
  - Sent to players within 16 blocks and shown for about 3 seconds.
  - Toggle: client config `showBarks`.
- **Shouts** are **noises** (S6, loudness 24). NPCs who hear one come to the *shouter's* position; nothing about you is shared (D-08).
  - Guards shout on spotting you and every ~4 s in combat.
  - Fleeing civilians scream.
  - Full alarms, runners and information decay are S9.

## 7. Debug view

The AI debug view now also shows:
- the label lines `cause:` and `bt:` (the running action path, e.g. `combat > ring`)
- search points: a claimed point in the NPC's colour, unclaimed points grey
- a small crown on attack-token holders

## 8. Tests (GameTests)

1. A custom behaviour JSON loads and drives an NPC (its tree barks on curiosity).
2. Two guards searching the same spot claim different points, and the search lasts longer than for one guard.
3. With three guards on one target, at most two attack; the third holds the ring.
4. A noise makes an unaware guard curious; a loud one makes it investigate the spot.
5. A guard's shout brings a second guard who couldn't see anything.

## 9. Not in this stage

- Zones, alarms (bells, gongs, signal fires), runners and information decay (S9).
- Knocked-out and unconscious NPCs, waking them up, bodies as evidence (S7).
- Combat moves (S13).
- A visual behaviour-tree editor (S19).
