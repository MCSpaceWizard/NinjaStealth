# 13 — Light & shadow (Stage 3) ✅ implemented

> **Status (2026-10-06):** implemented as designed and covered by 6 GameTests. The light gem and unlit blocks were checked visually in a real client.

Stage 3 makes light and **cast shadows** the core of visibility (L-01, L-10). The gameplay model runs on the server and is the truth. The **light gem** shows that truth to the player. What vanilla *renders* doesn't match it yet; that's the Track E rendering R&D (Q2, accepted).

## 1. Exposure model (server)

`exposure(point)` is a value from 0 (pitch dark) to 1 (fully lit), combining:

### Block lights, with real shadows
- A **light-source index** caches, per chunk section, the positions and brightness of every light-emitting block. Sections without emitters are skipped by a palette check. The cache is invalidated on block changes and refreshed every 5 s as a safety net.
- For each point, the up to **8 strongest nearby sources** within 15 blocks contribute `(emission − distance) / 15`, the same falloff as vanilla, **times a ray** from the source to the point:
  - opaque blocks cast shadows
  - glass passes light
  - leaves and fences let some through (same tags as sight)
- Sources combine like light does: `1 − Π(1 − Iᵢ)`.
- Result: **a torch behind a pillar leaves you dark on the far side**, even though vanilla's flood-fill light leaks around the corner.

### Dynamic lights
- Any entity **holding a light-emitting item** in either hand: torch 14, lantern 15, soul torch 10, glowstone 15, and so on. The brightness is the item's block's light level, and the light sits at hand height.
- **Burning entities** (15).
- Holding a torch lights *you* up (L-02), and guards carrying torches light their surroundings.

### Sky: sun and moon as directional lights
- **Sky access** comes from vanilla sky light at the point (0 underground / deep indoors, 1 in the open).
- **Day:** direct sunlight only if a ray **toward the sun** (from the 26.1 `sun_angle` environment attribute) is clear. A wall's shadow falls *sideways* in the morning and evening, not straight down.
  - In direct sun: 1.0
  - In shade with sky access: 0.3
- **Night:** a dim base of 0.03, plus moonlight `0.25 × moon brightness` when a ray toward the moon is clear. A full moon is bright; a new moon is pitch dark.
- **Rain** dims direct light by 40%; **thunder** dims it by 70% (L-08).

### Combined
- `exposure = 1 − (1 − blocks)(1 − sky)`
- Perception's light factor is `0.12 + 0.88 × exposure`. Even in total darkness you're not invisible right next to a guard, but you're **~8× slower** to spot.
- Results are cached per tick per quantised point, so many NPCs looking at the same player cost the light maths once.

## 2. Light gem (HUD)

- The server computes your own exposure (body-weighted) every 5 ticks and sends it to you only.
- A gem at the bottom of the screen, above the hotbar:
  - dark, almost black, when you're hidden
  - glowing amber when you're lit
- It's smoothed client-side. Toggle: client config `showLightGem`.
- Programmer art; the sprites are at `textures/gui/sprites/hud/light_gem_*.png`.

## 3. Putting lights out & relighting (L-03)

| Light | Snuff (right-click, **empty hand**) | Relight |
|---|---|---|
| Torch / wall torch / soul torch / copper torch | Becomes an **unlit torch** (keeps placement) | Flint & steel, fire charge, or a lit torch item |
| Lantern / soul lantern (standing or hanging) | Becomes an **unlit lantern** | Same |
| Candles | Vanilla already lets an empty hand put them out | Vanilla (flint & steel) |
| Campfire / soul campfire | Goes out (`lit=false`) | Vanilla (flint & steel) |

- **Powered lights are not snuffable:** redstone lamps, glowstone, sea lanterns, froglights, and so on.
- Snuffing makes a small "fizz". It isn't an alarm (Z-02); it becomes a minor noise when hearing lands in S6.
- Unlit torches and lanterns drop their normal lit item when broken.
- Water arrows and cutting or shooting a lantern's rope come with the toolkit (S12).

## 4. Guards and darkness (L-04)

A guard who is investigating, hunting or searching somewhere dark (exposure at its eyes < 0.25) **takes out a torch** in its off-hand. That torch is a dynamic light, so it lights up the guard *and* anyone nearby. The guard puts it away when it calms down.

## 5. Not in this stage

- **Visual** dynamic lights, a "dark is dark" post-process, and rendered shadows: Track E R&D. Until then, the light gem is the truth. A dynamic-lights mod in the pack helps the visuals.
- Lamplighter routine (S5, Q10).
- Fog-at-dawn visibility and weather noise masking: S6/S9.
