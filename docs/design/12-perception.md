# 12 — Perception: sight (Stage 2) 📄 for review

This stage makes NPCs **see**. Sight produces an **awareness** value per NPC per target, plus a remembered **last known position**. The behaviour layer (S4) turns awareness into states (curious, suspicious, …). Light is a placeholder input until S3 swaps in the real exposure model. Hearing (S6) and evidence (S7) later feed the same awareness and memory.

Rules from the questionnaire this design must satisfy:
- Proper view cones (V-08).
- **No sensing behind** (D-02).
- Partial cover: glass, leaves, grass, bars (D-03).
- Deep water (>5 blocks) blocks sight (P-12).
- Sprinting is very visible (P-01).
- **An always-short grace period** (D-05).
- **No free information** (D-08).
- ~20 full-fidelity NPCs plus cheaper distant ones (T-08).

## 1. Perception profiles (datapack)

A new registry, `emergentstealth:perception_profile`. Archetypes reference a profile with `"perception": "emergentstealth:guard"` (default `emergentstealth:default`).

```json
{
  "central":    { "half_angle": 30, "range": 24 },
  "peripheral": { "half_angle": 75, "range": 12, "rate": 0.3 },
  "vertical_half_angle": 50,
  "gain_per_second": 1.6,
  "decay_per_second": 0.25,
  "decay_delay_seconds": 3.0,
  "min_detection_seconds": 0.6
}
```

| Shipped profile | Notes |
|---|---|
| `default` | Civilians/workers: shorter range, slower gain |
| `guard` | Patrol/stationary guards |
| `sentry` | Narrow, long central cone (archers and watchtowers later) |
| `elite` | Faster gain, wider peripheral vision |
| `captain` | Like elite. Disguise-reading comes in S11 |

There is no rear cone, ever. Anything outside the peripheral cone is invisible to sight.

## 2. Is the target visible, and how much?

**Body sample points.** Up to 5 points on the target:
- head, chest, hips, left foot, right foot
- the points move with pose: crouching lowers them, crawling puts them all near the ground

**Rays.** One ray per point, from the NPC's eye. The ray walks the voxel grid and multiplies a **transmittance** per block, which comes from block tags so packs can retune any block:

| Tag | Transmittance | Examples |
|---|---|---|
| (opaque collision/occlusion shape) | 0 | stone, planks, doors (closed) |
| `#emergentstealth:see_through` | 1.0 | glass, panes, open trapdoors |
| `#emergentstealth:partial_cover` | 0.35 | leaves, fences, iron bars, scaffolding |
| `#emergentstealth:light_cover` | 0.7 | chains, cobwebs |
| `#emergentstealth:concealing_foliage` | 0 **if the target is crawling**, else 1 | tall grass, ferns, crops, sugar cane |
| water | 1 for the first 5 blocks of depth along the ray, then 0 | P-12 |

Shoji silhouettes (S14) slot in later as a `silhouette` tag: transmittance 0, but a lit target behind it still counts as "seen as a silhouette" at reduced weight.

**Visibility score.** For each point that's inside the NPC's cones and has transmittance > 0:
`visibility += weight(point) × transmittance × lightFactor(point)`.

- Weights: head 0.3, chest 0.3, hips 0.2, feet 0.1 each.
- `lightFactor` is a **placeholder in S2**: vanilla block/sky light at that point, mapped to 0.15 at level 0 up to 1.0 at level 15. S3 replaces it with the ray-traced exposure, behind the same interface.

## 3. Awareness

Each NPC keeps, per target: `awareness` (0–1), `lastSeenPos`, `lastSeenTick`, and `seenNow`.

While the target is visible:
```
gain = profile.gain_per_second × visibility × distanceFactor × coneFactor × movementFactor × stateFactor
```
- `distanceFactor`: 1 near, falling to 0 at the cone's range (smooth curve).
- `coneFactor`: 1 in the central cone, `peripheral.rate` in the peripheral cone.
- `movementFactor`: still 0.5, crawling 0.4, sneaking 0.6, walking 1.0, **sprinting 2.0**, attacking 1.5.
- `stateFactor`: 1 for now. S4 raises it when the NPC is already suspicious or alerted.
- **Grace period:** the gain is capped so going from 0 to 1 always takes at least `min_detection_seconds`, even at point-blank range in full light.

When not visible: after `decay_delay_seconds`, awareness falls at `decay_per_second`.

**Thresholds**, read by S4 (configurable):
- 0.2 = noticed
- 0.5 = suspicious
- 1.0 = detected

**Memory:** the target's position is only remembered while it's actually seen (`lastSeenPos`). Nothing is ever updated without perception: that's the no-free-info rule. S4 hunts toward `lastSeenPos`.

## 4. Scheduling & LOD

A per-level `PerceptionScheduler` runs each server tick:
- **Tier 1**: the ~20 NPCs (configurable) closest to any player, within 48 blocks. Full perception every 2 ticks, staggered by entity id so they don't all run on the same tick.
- **Tier 2**: other NPCs within 96 blocks. Every 10 ticks, head + chest rays only.
- **Tier 3**: everything else. No sight checks. Awareness decays by elapsed time when the NPC next wakes.
- A **ray budget per tick** (server config) caps the worst case. Any work over budget rolls to the next tick.
- Targets are players in survival/adventure mode (spectators and creative ignored, configurable). Other entity targets (bodies, recruits) come later through the same interface.

## 5. Debug view additions

- The real cone from the NPC's profile replaces the placeholder.
- An **awareness bar** and value per target above the NPC.
- **Rays to the target's sample points**: green = visible, yellow = partially covered, red = blocked.
- A marker at `lastSeenPos`.
- A scheduler tier label (T1/T2/T3) and per-tick ray usage on the F3 screen.

## 6. Config introduced (server)

- `perception.tier1MaxNpcs` (20)
- `perception.tier1Range` (48)
- `perception.tier2Range` (96)
- `perception.raysPerTickBudget` (600)
- `perception.ignoreCreative` (true)
- `perception.globalGainMultiplier` (1.0)

## 7. Tests (GameTests)

Each in a small structure:
1. Target in front in light → detected within the expected time window, never faster than the grace minimum.
2. Same, behind the NPC → awareness stays 0.
3. Glass between → detected. Stone → not. Leaves → slower.
4. Crawling in tall grass → not detected. Standing in it → detected.
5. Peripheral cone → slower than central.
6. Sprinting vs sneaking at the same distance → sprinting fills ≥ 3× faster.
7. Water column > 5 blocks deep → not detected.
8. After losing sight → awareness decays only after the delay, and `lastSeenPos` doesn't follow the target.

## 8. Questions for you

- **P1.** Are the default numbers above a reasonable starting feel (guard: 24-block central range, 0.6 s minimum detection)? They're all datapack and config values, so they're easy to tune after playtesting.
- **P2.** Should a held torch or lantern make the *holder* more visible in S2 already (a crude version), or wait for S3's proper dynamic lights? *Default: wait for S3.*
