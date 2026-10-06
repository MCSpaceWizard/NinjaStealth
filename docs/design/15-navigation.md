# 15 — Navigation, patrols & routines (Stage 5) ✅ implemented

> **Status (2026-10-06):** implemented as designed and covered by 6 GameTests. The commands ended up as `/es patrol …` for routes and `/es routine …` for per-NPC schedules (listed below).

This stage gets guards moving through real buildings, walking **learnable patrol routes**, and keeping simple **daily routines**. It also gives map makers a **patrol tool** to author all of it in-game.

## 1. Pathfinding

The pathfinder extends vanilla's ground navigation (which already handles stairs, slabs, wooden doors and avoiding hazards). It adds:

| Feature | How |
|---|---|
| **Wooden doors**: open, walk through, **close behind** | Vanilla door handling plus a door goal set to close doors after passing. Guards leave a compound as they found it (doors left open become evidence later, D-06) |
| **Fence gates**: open/close like doors | Closed gates count as passable "wood doors" for planning; a gate goal opens and closes them |
| **Ladders** (and vines/scaffolding: `#minecraft:climbable`) | The pathfinder adds up/down steps along climbable blocks; movement climbs while the next path step is above |
| Iron doors, locked doors | Treated as walls for now. **Keys and locks** arrive with the toolkit (S12) |
| **No parkour/wall climbing** for guards (A-09) | Unchanged: ladders and stairs only. Rooftops stay a refuge (archers come later) |

**Debug:** the current path (up to 24 steps) is drawn in the AI debug view as a cyan line.

## 2. Patrol routes (A-05)

- **Routes are per-dimension world data:** a name, an ordered list of waypoints, and a mode (`loop` or `pingpong`).
- **A waypoint** is a position plus optional extras:
  - `wait` (seconds to stand there)
  - `look` (a yaw to face while waiting; otherwise it looks around)
  - `relight`: re-lights unlit torches and lanterns within 4 blocks. This is the **lamplighter** (Q10): give a guard a dusk route through a courtyard and the dark windows you made close again.
- **Walking pace:** patrols walk at 0.6 speed (a little brisker when heightened).
- **After any alert,** a guard returns to its route at the nearest waypoint.

### Patrol Baton (tool)

| Action | Effect |
|---|---|
| Right-click a block | Adds a waypoint on top of that block to the baton's route (created on first use, auto-named `route_1`, `route_2`, …) |
| Sneak + right-click a block | Removes the route's last waypoint |
| Right-click an NPC | Assigns the baton's route to that NPC (all day) |
| Holding the baton | Shows nearby routes: lines, numbered waypoints, wait/look markers |

### Commands (`/es patrol …`, op)

- `list`
- `select <route>` (binds the held baton)
- `remove <route>`
- `mode <route> loop|pingpong`
- `wait <route> <index> <seconds>`
- `look <route> <index> <yaw>`
- `relight <route> <index> on|off`
- `assign <npcs> <route> [<fromHour> <toHour>]` (no hours = all day and replaces the schedule; with hours it adds or replaces that window)
- `look <route> <index>` with no yaw uses your facing; `look <route> <index> clear` removes it

Routines (`/es routine …`, op):
- `post <npcs>` (stand guard here, facing your direction, all day)
- `wander <npcs> <radius>`
- `clear <npcs>` (back to the role default)
- `show <npcs>`

## 3. Routines (A-06, "light")

Each NPC can have a **schedule**: up to 8 entries, each a time window in Minecraft hours (0–24) with an activity:
- **`route <name>`**: walk a patrol route.
- **`post`**: stand at a position, facing a direction, glancing around (stationary guards and sentries).
- **`wander <radius>`**: stroll near home (civilians and the default).

Overlapping windows: the first match wins, and commands add new windows at the front, so the newest takes priority (a night route added on top of an all-day route takes over at night). If no entry matches, the NPC stays at its post or wanders. Windows give **shift changes** naturally, e.g. a day route plus a night route with fewer guards, and a dusk lamplighter route. The design target is "learnable": the same NPC walks the same route at the same times every day.

**Defaults when an NPC has no schedule:**
- `stationary_guard` / `captain` / `target`: post at their spawn spot, facing their spawn direction.
- Everyone else: wander within 8 blocks of home (spawn spot).

## 4. Data, persistence, multiplayer

- Routes are world data, so they survive restarts and are shared by every NPC in the dimension.
- Schedules, home and post positions are saved on each NPC.
- Route visualisation is sent only to players **holding a baton** (op).

## 5. Tests (GameTests)

1. A guard walks through a closed wooden door to a target, and the door ends up closed again.
2. Same through a fence gate.
3. A guard reaches a second floor by ladder.
4. A guard on a 3-waypoint loop visits the waypoints in order and comes back to the first.
5. A schedule picks the right activity for the time of day.
6. The `relight` waypoint relights an unlit torch.

## 6. Not in this stage

- Locked doors and keys (S12).
- Zones and alarm-driven lockdowns (S9).
- Procedural patrols generated with worldgen compounds (S15; they'll use these same routes).
- Squads and group search split (S4).
