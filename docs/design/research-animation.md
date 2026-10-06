# Research: animation for S7 (and later), procedural vs keyframed

> **2026-10-06, research only.** Library versions were checked against the Modrinth API and maven metadata on that date. This is input for the S7 design doc (17-takedowns-bodies).

## Recommendation

- **Players: [Player Animation Library (PAL)](https://modrinth.com/mod/player-animation-library)**, `1.2.8+mc.26.1`, MIT.
  - It is now the shared player-animation layer on 26.1.2: Emotecraft and Better Combat both require it.
  - It supports code-driven layers (`IAnimation.get3DTransform`), first-person arms (`THIRD_PERSON_MODEL`) and Bedrock/GeckoLib-format clips.
  - It has no networking; we sync ourselves.
- **NPCs: vanilla `KeyframeAnimation`** loaded by NeoForge's JSON animation loader (`assets/<ns>/neoforge/animations/entity/*.json`, reloadable with F3+T), **plus an in-house procedural pose layer**.
  - We own `NpcRenderer`, so NPCs need no mixins.
  - **GeckoLib isn't needed for NPCs.** This changes Track E's "GeckoLib NPC animations" line.
- **Procedural is very feasible.** Blocky limbs are single segments, so "IK" is just aiming a bone.
  - Procedural first: crawl cycle, knocked-out pose, dragged/carried bodies, breathing, lean.
  - Artist keyframes: takedowns, climbing, combat.

## Libraries on 26.1.2 NeoForge

| Option | Version | Licence | Animates | Notes |
|---|---|---|---|---|
| **PAL** (ZigyTheBird) | 1.2.8+mc.26.1 | MIT | Players (`Avatar`) | Layer stack with priorities, modifiers (fade, mirror, speed, adjustment), first-person arms. No sync |
| KosmX playerAnimator | ends at 1.21.7 | MIT | — | Superseded by PAL |
| GeckoLib | 5.5.2 (`com.geckolib:geckolib-neoforge-26.1.2`) | MIT | Custom Geo entities, items, armour; **not the vanilla player** | Not needed for our humanoids |
| Emotecraft | 3.3.0 beta | GPL-3.0 | Players via PAL | Give our layers higher priority than emotes |
| Better Combat | 3.2.2 | ARR | Player swings via PAL | |
| AzureLib | no 26.1.2 build | MIT | — | |
| Bendable Cuboids | 2.0.2 beta | ? | Elbows and knees | Optional, later |

## Things to know about 26.1

- **`setupAnim` is deferred and runs several times per frame** (main pass, layers, armour, outlines, the Iris shadow pass).
  - The pose must be a **pure function of the render state**.
  - Springs and verlet simulations step in client tick or `extractRenderState`, never in `setupAnim`.
- **Armour models are separate `HumanoidModel`s.** NPC armour must be baked with our own `NpcModel` so it follows animations. Currently `ESModelLayers` bakes armour as `HumanoidModel::new`.
- **Crawling needs no mixins:**
  - NeoForge's `Player.setForcedPose(Pose.SWIMMING)` gives the 0.6-block hitbox and low camera, on both sides, driven by a synced attachment.
  - We check stand-up clearance ourselves.
  - The vanilla body pitch already works; only the limb motion gets replaced (procedural).
- **Useful mixin-free hooks:**
  - `RegisterRenderStateModifiersEvent`
  - `ViewportEvent.ComputeCameraAngles` (takedown camera)
  - `CalculatePlayerTurnEvent` (damp mouse look)
  - `MovementInputUpdateEvent` (freeze input)
  - synced data attachments
  - `PacketDistributor.sendToPlayersTrackingEntityAndSelf`

## Procedural pose layer (client, `client/anim`)

```java
final class HumanoidPose { BonePose root, head, body, rightArm, leftArm, rightLeg, leftLeg; BitSet touched; }
interface PoseLayer { int priority(); BlendMode mode(); float weight(PoseContext c); void apply(PoseContext c, HumanoidPose p); }
record PoseContext(int entityId, float ageInTicks, float partialTick, float walkPos, float walkSpeed, Vec3 velocity,
                   float bodyYaw, float headYaw, float headPitch, @Nullable ActionPlayback action, ProceduralState sim, BlockGetter level) {}
interface ProceduralSim { void tick(LivingEntity e, ClientLevel level); }      // springs, verlet chains, 20 Hz
final class PoseGraph { void evaluate(PoseContext c, HumanoidPose out); }
record ActionPlayback(Identifier action, Role role, long startGameTime, Vec3 anchor, float anchorYaw) {}
```

**Where it hooks in:**
- **NPCs:** `NpcRenderer.extractRenderState` evaluates the graph into the render state. A new `NpcModel.setupAnim` applies it after the vanilla walk.
- **Players:** a single PAL adapter (`PalPoseAnimation implements IAnimation`) evaluates the same graph. Only that adapter touches PAL's API.

**Example layers:**
- `CrawlCycleLayer`
- `BreathingLayer`
- `LeanLayer`
- `KnockedOutLayer`
- `DraggedBodyLayer` (verlet chain)
- `CarriedBodyLayer`
- `ReachIkLayer` (S10 ledges)

## Paired takedowns: sync

The design is server-driven with a start tick.
1. The client sends an intent.
2. The server validates it (behind or above the victim, victim unaware, in range, line of sight) and picks a datapack `TakedownDefinition`.
3. The server **snaps both bodies to an anchor** and freezes the victim's AI and the attacker's input.
4. The server stores an `ActionPlayback` (action, role, start tick, anchor) in synced state, so late joiners can seek in.
5. Clients evaluate `gameTime − start + partialTick` and **render relative to the anchor**, not the lerped entity position.
6. The outcome (knockout or kill, noise) happens on the server at the impact tick.

Only a 2–3-tick attacker "lunge" wind-up is predicted on the client, to hide one round trip.

## Artist pipeline

- **Deliverables:** one `.bbmodel` per action, with animations named `<action>.attacker` and `<action>.victim`.
- **Player clips:** exported as Bedrock/GeckoLib JSON, which PAL loads from `assets/<ns>/player_animations/`.
- **NPC clips:** exported with Blockbench's *Animation to JSON Converter* (NeoForge format).
  - Bake with the *Bakery* plugin first, because vanilla has only linear and catmull-rom interpolation.
  - No Molang.
- **Helpful plugins:** *PAL Bend Player Tools* and *Root Motion Extractor* (for snap offsets).

## Compatibility to verify

| Mod | Check |
|---|---|
| Shoulder Surfing | Fine (ordinary third-person rendering) |
| First-person Model | Hide our first-person arms when it's present |
| Real Camera (beta) | Verify PAL poses show |
| Not Enough Animations | PAL already orders its mixins after it |
| Emotecraft | Cancel emotes on takedown/crawl/drag |
| Sodium, Iris | Unaffected (CPU-side bone transforms) |

## S7 animation order

1. Pose framework and `NpcModel` (armour baked with it).
2. Knocked-out NPCs: synced flag, flat hitbox, slack pose.
3. PAL integration through the adapter.
4. Crawl toggle (forced pose, procedural cycle).
5. Drag and carry: the body as a passenger, verlet trailing.
6. Paired takedown core: registry, validation, snap, sync, programmer-art clips.
7. First-person takedowns.
8. Polish: breathing and lean, GameTests, screenshots.

## Open questions (for the S7 doc)

1. Is a required client-side PAL dependency OK, or should it be bundled (jar-in-jar)?
2. Should GeckoLib be dropped from the NPC plan?
3. Is this procedural vs keyframed split right?
4. One authoring format (Bedrock JSON for both, evaluated by PAL core for NPCs too), or two exports from the same `.bbmodel`?
5. Elbows and knees (Bendable Cuboids, beta), or strictly six-bone?
6. Is angle-only first-person camera motion enough, or should the camera follow the head bone (needs a mixin)?
7. Is a square knocked-out hitbox plus a custom pick shape acceptable?
8. Is predicting only the wind-up, with a fixed variant per approach angle, acceptable?
