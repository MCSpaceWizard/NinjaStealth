# 17 — Player verbs I: crawl, takedowns, bodies (Stage 7) ⏳

> **Status (2026-10-09):** server side and animation implemented. 13 GameTests (7 for the verbs, 6 for the animation maths), and the animations checked in the real client (the half-second air takedown only by its outcome). Drop a body by right-clicking again; sneaking doesn't drop it, because sneaking while dragging is common.

The player gets the core stealth verbs: **crawl**, **take guards down** quietly (lethal or not), and **hide the bodies**, which guards now notice. Animation follows [research-animation](research-animation.md) and your answers (2026-10-08):
- **PAL** is a required client dependency.
- Animation is **procedural wherever possible**.
- **Elbows and knees** (Bendable Cuboids), a required client dependency like PAL (2026-10-09: no six-part fallback).
- **Camera turning only**; third person is preferred.

## 1. Crawl (P-08, Q3)

- **Crawl key** (default **Z**) toggles crawling anywhere.
  - Uses vanilla's 0.6-block crawl pose: low camera, fits 1-block gaps.
  - You can't stand up under a low ceiling; you stay crawling until there's room.
- **Speed:** sneak speed. No sprinting while crawling.
- **Noise:** footsteps at loudness 1 (S6).
- **Hiding:** crawling in tall grass and similar foliage hides you (already in S2 perception). Crawling also makes you a smaller target generally, through fewer body sample points.
- **Ends automatically** when you swim, climb a ladder or start flying.
- **State:** server-side in a synced player attachment (`stance`), saved with the player.
- **Animation:** procedural crawl cycle driven by movement (no foot sliding), replacing the swim-stroke arms.

## 2. Bodies (D-06, A-12)

- Knocked-out and dead NPCs **stay in the world as bodies**. They lie down with a low hitbox (0.9 × 0.4) and run no AI.
- **Dying in any way** leaves a corpse, which drops its loot. Hitting a knocked-out NPC kills it. `/kill` still removes NPCs outright.
- **Knocked-out NPCs never wake by themselves.** Another NPC who finds one walks over and wakes it (about 3 s). The woken NPC is on **heightened alert** and searches around the spot.
- **Animation:**
  - knocked out: slack, ragdoll-like pose fitted to the ground
  - dead: limp pose
  - slow breathing on the unconscious, so you can tell them apart

## 3. Takedowns

| Takedown | When | Input | Result | Time | Noise |
|---|---|---|---|---|---|
| **Rear, non-lethal** (choke) | Behind an NPC (>120° from its facing), within 2 blocks, NPC not in combat or hunting **you** | **Right-click** with an empty hand | Knocked out | 2 s, both locked in place | 4 (struggle) |
| **Rear, lethal** | Same | **Left-click** (any weapon or empty hand) | Corpse | 0.75 s | 6 |
| **Air, non-lethal** | Land on an NPC from ≥ 2 blocks, sneaking | Fall onto it | Knocked out; your fall damage is cancelled | instant | 8 |
| **Air, lethal** | Same, not sneaking, holding a weapon | Fall onto it | Corpse; fall damage cancelled | instant | 8 |

- **Prompt:** when a takedown is possible, a small prompt shows above the hotbar: `[RMB] Knock out   [LMB] Kill`.
- **Paired and server-driven:**
  1. The server validates the takedown and snaps the attacker 0.8 blocks behind the victim, facing the same way.
  2. It freezes both, and sets an `action` playback (start tick and shared anchor) on both.
  3. Clients play the matching clips from that start tick, relative to the anchor.
- **Being hit during a choke** breaks it: the victim is alerted and fights.
- **Elites** (role `elite`) shrug off rear takedowns; only air takedowns work on them, per the S13 plan.
- **Data:** takedown kinds live in a datapack registry `emergentstealth/takedown/*.json` (`kind`, `lethal`, `duration`, `impact_tick`, `noise`, `attacker_offset`, `attacker_clip`, `victim_clip`). Map makers can retune them.
- **Camera:** in third person nothing special happens. In first person the camera **turns** with the animation (yaw/pitch only), and mouse look is damped during the action.
- **Clips:** programmer-art keyframe clips for now (attacker via PAL, victim via our NPC model), so artists can replace them later.

## 4. Moving bodies (P-09)

- **Drag:** right-click a body. It slides along the floor behind you.
  - You move at ×0.75 speed and can't sprint.
  - Dragging is **silent**.
  - Right-click again to drop it.
- **Carry:** sneak + right-click a body. It goes over your shoulder.
  - You move at ×0.6 speed, but the body is off the floor and can go up ladders and stairs.
- **What clients see:** the body follows you on the server; clients animate it.
  - A procedural limp chain for a dragged body (arms trailing toward your hand, legs dragging).
  - A draped pose for a carried body.

## 5. Evidence (D-06)

NPCs now perceive evidence with the same sight model as you: cones, rays and light. Dark spots hide bodies too.

| Evidence | Reaction |
|---|---|
| A **corpse** | Alarmed: shouts and searches around the body (an alerted search with no known target) |
| A **knocked-out** colleague | Goes to wake it, then both search |
| An **arrow stuck in a block** | Curious, then investigates it once |
| A **dropped weapon** on the ground | Curious, then investigates |

- Each piece of evidence is only noticed once per NPC.
- Hiding bodies in the dark, in tall grass, or out of patrol paths matters.
- **Debug view:** evidence an NPC noticed is drawn as a marker.

## 6. Tests (GameTests)

1. A rear takedown works from behind, but not from the front or on an alerted NPC.
2. A knocked-out NPC stays down. A guard who sees it wakes it, and the woken guard is on heightened alert.
3. A guard who sees a corpse becomes alerted and searches.
4. A corpse in the dark (or behind a wall) isn't noticed.
5. A dragged body follows the player and slows them.
6. Crawling: the stance persists, you can't stand under a 1-block ceiling, and footsteps are quieter.
7. An air takedown cancels fall damage and knocks the NPC out.

## 7. Not in this stage

- Hay bales and crawlspaces as body hiding spots (S14).
- Water takedowns and ledge takedowns (S10).
- Disguises from knocked-out NPCs (S11).
- Corpse cleanup: bodies persist for now; a despawn timer comes with zones (S9).
- Blood decals as evidence (S13).

## 8. Animation (implemented)

One procedural **pose graph** drives NPCs and players alike. Common maths lives in `anim/` (testable on the server); the rest is client code in `client/anim/`.

| Piece | What it does |
|---|---|
| `HumanoidPose` | Every channel is an affine function of whatever the base pose is (vanilla walk for NPCs, PAL's bone for players), so layers blend without knowing the base |
| Layers (`client/anim/layer`) | `CrawlLayer` (elbow crawl), `CarrierLayer` (dragging and carrying arms, lean), `BodyLayer` (lying, dragged, carried limbs), `BreathingLayer`, `LeanLayer` |
| Sims (`client/anim/sim`) | Stepped at 20 Hz, never in `setupAnim`: distance and lean springs, breathing phase, the body's fall and ground fit, the drag chain (verlet, pinned to the dragger's hand, pulled towards the server's body position) |
| NPCs | `NpcRenderer` evaluates the graph at extraction; `NpcModel` (body and armour, with bends) applies it. `BodyPlacement` lays bodies down about their middle, along the drag chain or across the carrier's shoulders |
| Players | `PalPoseAnimation`, one PAL layer evaluating the same graph; `TakedownClipController`, a PAL controller whose clip time is pinned to the server's action clock every frame |
| Takedown clips | Attacker: PAL Bedrock JSON. Victim: NeoForge entity animation JSON. Both are stretched over `ActionPlayback.length`, so takedown-speed skills shorten them. Programmer art from `tools/programmer_art/takedown_clips.py`; artists replace the files |
| Camera | First person: angle-only turn per takedown kind (`TakedownCamera`), damped mouse look, no zoom while held still |

**Decisions made while building it:**
- Clips never keep their own time. Every frame evaluates `gameTime − start + partialTick`, so late viewers seek in and both sides stay aligned.
- The drag chain is visual only; the server's body position wins (evidence, hiding), and the chain is pulled towards it.
- A carried body is hidden in your own first-person view.
- Bodies beyond the animation range aren't simulated; they simply lie flat.

**Not yet:** NPC-side keyframe clips other than takedown victims (combat, climbing), first-person arm visibility tuning during takedowns, and real (artist) clips.
