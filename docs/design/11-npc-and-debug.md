# 11 — Stealth NPC base & debug framework (Stage 1) 📄

Stage 1 adds a player-shaped NPC whose look and stats come from data, plus the debug overlay every later AI system reports into. There's no stealth AI yet. NPCs idle, wander and look at players until the S4 brain replaces those placeholder goals.

## 1. The `StealthNpc` entity

- `emergentstealth:stealth_npc` is one entity type for every human NPC. The **archetype** decides the role (guard, civilian, …), not the Java class. Later stages add behaviour by reading the archetype, so we don't need an entity class per role.
- It extends `PathfinderMob`: vanilla navigation, equipment slots, goal selector. Custom navigation (S5) and the AI brain (S4) plug into it.
- Persistent: it never despawns by distance. Garrison respawn rules come in S15.
- **Synced to clients:** archetype id (string) and body variant (int). The client looks the archetype up in its synced copy of the datapack registry to know what to draw.
- **Saved:** archetype id, body variant.
- Spawning:
  - `/emergentstealth npc spawn <archetype>` (op)
  - the spawn egg (spawns the default archetype, `ashigaru`)
  - `/summon emergentstealth:stealth_npc ~ ~ ~ {Archetype:"emergentstealth:ashigaru"}`

## 2. Data model (datapack registries)

Both registries are **synced to clients** (needed for rendering) and live under `data/<namespace>/emergentstealth/<registry>/<name>.json`.

### `emergentstealth:outfit`: what an NPC (and later a disguise) looks like

```json
{
  "layers": [
    "emergentstealth:textures/entity/npc/outfit/ashigaru_under.png",
    "emergentstealth:textures/entity/npc/outfit/ashigaru_armor.png"
  ],
  "disguise_group": "emergentstealth:shogunate_soldier"
}
```
- `layers`: 64×64 player-skin-layout textures, drawn in order over the body. They use the player model's outer layer too (jacket/hat/sleeves/pants), so armour and hats can have depth.
- `disguise_group` (optional): reserved for S11. Enforcers recognise colleagues by group.

### `emergentstealth:archetype`: who an NPC is

```json
{
  "role": "patrol_guard",
  "faction": "emergentstealth:shogunate",
  "bodies": ["emergentstealth:textures/entity/npc/body/body_1.png", "..."],
  "outfit": "emergentstealth:ashigaru",
  "stats": { "max_health": 24.0, "movement_speed": 0.28, "attack_damage": 4.0 },
  "equipment": { "mainhand": { "id": "minecraft:iron_sword" } }
}
```
- `role`: `civilian | worker | merchant | patrol_guard | stationary_guard | elite | captain | target | bodyguard | monk | shinobi` (N-01). Behaviour stages key off this.
- `faction`: an id. A faction registry arrives with S9 (Q5 open). Until then it's a plain id.
- `bodies`: base skin textures; one is picked per NPC at spawn (the body variant).
- `equipment`: equipment slot → item stack template (26.1 `ItemStackTemplate` JSON).

**Shipped archetypes** (placeholder art):
- `ashigaru` (patrol guard)
- `samurai` (elite)
- `taisho` (captain)
- `townsfolk` (civilian)
- `labourer` (worker)
- `daimyo` (target)

Map makers add or override any of these with a datapack.

## 3. Rendering

- `HumanoidMobRenderer` with a **player-shaped model** (`PlayerModel` mesh, 64×64, wide arms; slim arms later). Item-in-hand and vanilla armour layers work out of the box.
- An **outfit layer** draws each `layers` texture in order over the body texture. A missing texture falls back to nothing, not the purple checkerboard, so partial art packs don't break.
- Programmer art comes from `tools/programmer_art`, using a new `skin` shape: it paints body regions of the standard skin layout (head, torso, arms, legs, and the outer layers) in palette colours. **Real art is just a normal Minecraft skin PNG** dropped in at the same path. Any skin editor works.

## 4. Debug framework

Built on vanilla's 26.1 **debug subscription** system:
- The server only sends debug data to **operators or the singleplayer host**. That satisfies the op-gating rule without any code of ours, so the old `debugToolsRequireOp` config entry is removed.
- `StealthNpc` publishes an `NpcDebugInfo` record every tick it changes: archetype, role, faction, a state label ("idle" for now), and later awareness, knowledge and paths.
- On the client, the subscription is requested only while **debug view is toggled on**:
  - right-click with the **Debug Lens**, or
  - `/esdebug [on|off]` (client command, so no op needed to *request*; the server still only answers ops)
- The debug renderer draws with vanilla **gizmos**:
  - a text label above each NPC (archetype/role/state)
  - a facing line and a **placeholder vision cone outline**, which S2 replaces with the real cone parameters
- Each later system adds its own subscription and drawing (awareness meters in S2, sound in S6, paths in S5). They all sit behind the same toggle.

## 5. Commands (`/emergentstealth`, alias `/es`)

| Command | Side | Permission |
|---|---|---|
| `npc spawn <archetype>` | server | gamemaster (op 2) |
| `npc list` | server | gamemaster |
| `/esdebug [on\|off]` (separate client command) | client | – (the data itself is op-gated) |

## 6. Not in this stage

- No perception or AI states (S2/S4).
- No custom navigation (S5).
- No disguises (S11).
- No factions registry (S9 / Q5).
- No GeckoLib animations (Track E).

## 7. How to test (playtest checklist)

1. `./gradlew runClient`, then create a creative world with cheats on.
2. Open the **Emergent Stealth** creative tab: it should have the Debug Lens and the Stealth NPC spawn egg.
3. `/es npc spawn emergentstealth:` + Tab to see suggestions for all six archetypes. Spawn each one. They should look different (placeholder art), hold their gear, wander and look at you.
4. Right-click the **Debug Lens**. An action-bar message appears, and each NPC shows a 3-line label plus a yellow vision-cone outline and a red facing line that follows its head.
5. `/esdebug off` hides it again.
6. Save, quit and reload: NPCs keep their archetype, body variant and gear.
7. Co-op: `runServer` + `runClient` + `runClient2`. A non-op player toggling debug should see **nothing**; an op should see the overlay.
8. Datapack override: copy `data/emergentstealth/emergentstealth/archetype/ashigaru.json` into a world datapack, change `max_health`/`outfit`, then reopen the world (datapack registries load with the world, not on `/reload`) and spawn a new one.
