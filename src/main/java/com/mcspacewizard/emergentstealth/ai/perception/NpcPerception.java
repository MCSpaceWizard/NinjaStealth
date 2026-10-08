package com.mcspacewizard.emergentstealth.ai.perception;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.stealth.BodySample;
import com.mcspacewizard.emergentstealth.stealth.LightSampler;
import com.mcspacewizard.emergentstealth.stealth.SightRay;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * An NPC's senses (sight for now; hearing in S6) and what it knows about each target.
 * Driven by {@link PerceptionScheduler}, which decides how often and how precisely each NPC looks.
 */
public final class NpcPerception {
    /** Entries nobody has perceived for this long, with zero awareness, are forgotten. */
    private static final long FORGET_TICKS = 20 * 60;
    /** Visibility multiplier for invisible targets (armour and outline still give them away a little). */
    private static final float INVISIBLE_FACTOR = 0.15F;

    private final StealthNpc npc;
    private final Map<UUID, TargetAwareness> targets = new HashMap<>();
    private long lastUpdateTick = -1;
    private int tier = 3;
    private int raysLastUpdate;
    private final List<RayResult> debugRays = new ArrayList<>();
    /** Evidence (design doc 17 §5): how far along noticing each piece is, and what's already been noticed. */
    private final Map<UUID, Float> evidenceProgress = new HashMap<>();
    private final java.util.Set<UUID> noticedEvidence = new java.util.HashSet<>();
    private long lastEvidenceScan = -1;
    /** Seconds to notice evidence in good light, centre of view (darkness and distance slow it a lot). */
    private static final float EVIDENCE_GAIN = 1.2F;
    private static final int EVIDENCE_INTERVAL = 10;

    /** One sight ray's outcome, kept for the debug view. */
    public record RayResult(Vec3 point, float transmittance) {}

    public NpcPerception(StealthNpc npc) {
        this.npc = npc;
    }

    public int tier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public int raysLastUpdate() {
        return raysLastUpdate;
    }

    public List<RayResult> debugRays() {
        return debugRays;
    }

    public @Nullable TargetAwareness get(UUID target) {
        return targets.get(target);
    }

    public TargetAwareness getOrCreate(UUID target) {
        return targets.computeIfAbsent(target, id -> new TargetAwareness());
    }

    public Map<UUID, TargetAwareness> all() {
        return targets;
    }

    /** The target this NPC is most aware of, or null. */
    public @Nullable UUID focus() {
        UUID best = null;
        float bestAwareness = 0.0F;
        for (Map.Entry<UUID, TargetAwareness> entry : targets.entrySet()) {
            if (entry.getValue().awareness > bestAwareness) {
                bestAwareness = entry.getValue().awareness;
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * Looks for targets and updates awareness. Returns the number of sight rays cast (for the budget).
     */
    public int update(ServerLevel level, long now, int tier, LightSampler light) {
        if (npc.isBlinded(now)) {
            // Blinded (design doc 21): sees nothing; awareness only decays.
            this.tier = tier;
            decayOnly(now);
            return 0;
        }
        this.tier = tier;
        float dt = lastUpdateTick < 0 ? 0.05F : Math.min(5.0F, (now - lastUpdateTick) / 20.0F);
        lastUpdateTick = now;

        PerceptionProfile profile = npc.getPerceptionProfile();
        Vec3 eye = npc.getEyePosition();
        float yaw = npc.getYHeadRot();
        float pitch = npc.getXRot();
        float stateFactor = npc.stealthBrain().perceptionMultiplier();
        float globalGain = ESConfig.GLOBAL_GAIN_MULTIPLIER.get().floatValue();
        boolean full = tier == 1;
        double reach = profile.maxRange() + 2.0;

        int rays = 0;
        Set<UUID> visited = new HashSet<>();
        UUID focus = focus();
        debugRays.clear();

        for (ServerPlayer player : level.players()) {
            if (!isTargetable(player) || player.distanceToSqr(eye) > reach * reach) {
                continue;
            }
            UUID id = player.getUUID();
            visited.add(id);
            boolean collectDebug = focus == null || focus.equals(id);
            Sight sight = computeSight(level, profile, eye, yaw, pitch, player, full, light, collectDebug ? debugRays : null);
            rays += sight.rays();
            float visibility = sight.visibility();
            boolean anyRay = sight.anyClearRay();

            TargetAwareness awareness = targets.get(id);
            if (!anyRay || visibility <= 0.0F) {
                if (awareness != null) {
                    awareness.seenNow = false;
                    awareness.lastVisibility = 0.0F;
                    decay(awareness, profile, now, dt);
                }
                continue;
            }

            if (awareness == null) {
                awareness = getOrCreate(id);
            }
            float gain = profile.gainPerSecond() * visibility * movementFactor(player) * stateFactor * globalGain * dt;
            // Grace period (D-05): never go from 0 to fully detected faster than minDetectionSeconds.
            gain = Math.min(gain, dt / profile.minDetectionSeconds());
            awareness.awareness = Math.min(1.0F, awareness.awareness + gain);
            awareness.seenNow = true;
            awareness.lastVisibility = visibility;
            awareness.lastKnownPos = player.position();
            awareness.lastPerceivedTick = now;
        }

        // Targets out of range or gone: not seen, decay.
        Iterator<Map.Entry<UUID, TargetAwareness>> it = targets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TargetAwareness> entry = it.next();
            if (visited.contains(entry.getKey())) {
                continue;
            }
            TargetAwareness awareness = entry.getValue();
            awareness.seenNow = false;
            awareness.lastVisibility = 0.0F;
            decay(awareness, profile, now, dt);
            if (awareness.awareness <= 0.0F && now - awareness.lastClueTick() > FORGET_TICKS) {
                it.remove();
            }
        }

        if (lastEvidenceScan < 0 || now - lastEvidenceScan >= EVIDENCE_INTERVAL) {
            float evidenceDt = lastEvidenceScan < 0 ? EVIDENCE_INTERVAL / 20.0F : Math.min(2.0F, (now - lastEvidenceScan) / 20.0F);
            lastEvidenceScan = now;
            rays += scanEvidence(level, profile, eye, yaw, pitch, evidenceDt * stateFactor, light);
        }

        raysLastUpdate = rays;
        return rays;
    }

    /** What counts as evidence (D-06): bodies, arrows stuck in blocks, dropped weapons. */
    public static net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity asEvidence(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof StealthNpc body && body.isBody()) {
            return entity;
        }
        // Arrows at rest are stuck in a block (isInGround() isn't public).
        if (entity instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow && arrow.tickCount > 5
                && arrow.getDeltaMovement().lengthSqr() < 1.0E-4) {
            return entity;
        }
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity item
                && (item.getItem().is(net.minecraft.tags.ItemTags.SWORDS) || item.getItem().is(net.minecraft.tags.ItemTags.AXES))) {
            return entity;
        }
        return null;
    }

    /** Looks for evidence with the same cones, rays and light as for players. Returns rays cast. */
    private int scanEvidence(ServerLevel level, PerceptionProfile profile, Vec3 eye, float yaw, float pitch, float dt, LightSampler light) {
        double range = profile.maxRange();
        int rays = 0;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(eye, eye).inflate(range);
        for (net.minecraft.world.entity.Entity entity : level.getEntities((net.minecraft.world.entity.Entity) null, box,
                e -> e != npc && !noticedEvidence.contains(e.getUUID()) && asEvidence(e) != null)) {
            Vec3 point = entity instanceof StealthNpc ? entity.position().add(0.0, 0.25, 0.0) : entity.position().add(0.0, 0.1, 0.0);
            float cone = coneFactor(profile, eye, yaw, pitch, point);
            if (cone <= 0.0F) {
                continue;
            }
            rays++;
            // Pull the end point a little toward the eye so arrows sunk into a block don't block their own ray.
            Vec3 end = point.add(eye.subtract(point).normalize().scale(0.15));
            float transmittance = SightRay.transmittance(level, eye, end, false);
            if (transmittance <= 0.0F) {
                continue;
            }
            float visibility = cone * transmittance * light.lightFactor(level, point);
            float progress = evidenceProgress.getOrDefault(entity.getUUID(), 0.0F) + visibility * EVIDENCE_GAIN * dt;
            if (progress >= 1.0F) {
                evidenceProgress.remove(entity.getUUID());
                if (noticedEvidence.size() > 256) {
                    noticedEvidence.clear();
                }
                noticedEvidence.add(entity.getUUID());
                npc.stealthBrain().onEvidence(level, entity, point);
            } else {
                evidenceProgress.put(entity.getUUID(), progress);
            }
        }
        return rays;
    }

    /** Forget having noticed this evidence (e.g. a body that was moved: it's new evidence where it is now). */
    public void forgetEvidence(UUID id) {
        noticedEvidence.remove(id);
    }

    /** Result of looking at one target: summed visibility, whether any ray got through, rays cast. */
    public record Sight(float visibility, boolean anyClearRay, int rays) {}

    /**
     * Pure sight computation: how visible {@code target} is from an eye position and head rotation.
     * Sums, over the target's body sample points inside the vision cones, weight x ray transmittance x cone
     * factor x light. Invisible targets are mostly hidden.
     *
     * @param debugOut if non-null, receives each ray's outcome
     */
    public static Sight computeSight(ServerLevel level, PerceptionProfile profile, Vec3 eye, float yaw, float pitch,
                                     Player target, boolean full, LightSampler light, @Nullable List<RayResult> debugOut) {
        float visibility = 0.0F;
        boolean anyRay = false;
        int rays = 0;
        boolean crawling = target.isVisuallyCrawling() || target.isVisuallySwimming();
        for (BodySample sample : BodySample.of(target, full)) {
            float cone = coneFactor(profile, eye, yaw, pitch, sample.position());
            if (cone <= 0.0F) {
                continue;
            }
            rays++;
            float transmittance = SightRay.transmittance(level, eye, sample.position(), crawling);
            if (debugOut != null) {
                debugOut.add(new RayResult(sample.position(), transmittance));
            }
            if (transmittance <= 0.0F) {
                continue;
            }
            anyRay = true;
            visibility += sample.weight() * transmittance * cone * light.lightFactor(level, sample.position());
        }
        if (target.isInvisible()) {
            visibility *= INVISIBLE_FACTOR;
        }
        return new Sight(visibility, anyRay, rays);
    }

    /** Cheap update for NPCs too far from any player to look: only decay. */
    public void decayOnly(long now) {
        float dt = lastUpdateTick < 0 ? 0.0F : Math.min(60.0F, (now - lastUpdateTick) / 20.0F);
        lastUpdateTick = now;
        PerceptionProfile profile = npc.getPerceptionProfile();
        for (TargetAwareness awareness : targets.values()) {
            awareness.seenNow = false;
            decay(awareness, profile, now, dt);
        }
    }

    private static void decay(TargetAwareness awareness, PerceptionProfile profile, long now, float dt) {
        long sinceClue = awareness.lastClueTick() < 0 ? Long.MAX_VALUE : now - awareness.lastClueTick();
        if (sinceClue > profile.decayDelaySeconds() * 20.0F) {
            awareness.awareness = Math.max(0.0F, awareness.awareness - profile.decayPerSecond() * dt);
        }
    }

    public static boolean isTargetable(Player player) {
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }
        return !(player.isCreative() && ESConfig.IGNORE_CREATIVE.get());
    }

    /**
     * How well a point is seen from the cones alone: 1 in the central cone, the peripheral rate in the
     * peripheral cone, 0 outside (including everything behind), times a distance falloff.
     */
    public static float coneFactor(PerceptionProfile profile, Vec3 eye, float yaw, float pitch, Vec3 point) {
        Vec3 delta = point.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double distance = delta.length();
        if (distance < 1.0E-3) {
            return 1.0F;
        }

        // Minecraft yaw: 0 = +Z (south), increasing clockwise when seen from above.
        float pointYaw = (float) (Mth.atan2(delta.z, delta.x) * (180.0 / Math.PI)) - 90.0F;
        float yawOffset = Math.abs(Mth.wrapDegrees(pointYaw - yaw));
        // Minecraft pitch: positive looks down.
        float pointPitch = (float) (-(Mth.atan2(delta.y, horizontal) * (180.0 / Math.PI)));
        float pitchOffset = Math.abs(pointPitch - pitch);
        if (pitchOffset > profile.verticalHalfAngle()) {
            return 0.0F;
        }

        PerceptionProfile.Cone central = profile.central();
        PerceptionProfile.Cone peripheral = profile.peripheral();
        if (yawOffset <= central.halfAngle() && distance <= central.range()) {
            return distanceFalloff(distance, central.range());
        }
        if (yawOffset <= peripheral.halfAngle() && distance <= peripheral.range()) {
            return profile.peripheralRate() * distanceFalloff(distance, peripheral.range());
        }
        return 0.0F;
    }

    /** Full strength out to 30% of the range, then linear down to 0 at the range. */
    static float distanceFalloff(double distance, float range) {
        double start = range * 0.3;
        if (distance <= start) {
            return 1.0F;
        }
        return (float) Mth.clamp(1.0 - (distance - start) / (range - start), 0.0, 1.0);
    }

    /** Moving fast is far more visible than standing still (P-01). */
    static float movementFactor(Player player) {
        if (player.isVisuallyCrawling() || player.isVisuallySwimming()) {
            return 0.4F;
        }
        if (player.isCrouching()) {
            return 0.6F;
        }
        if (player.isSprinting()) {
            return 2.0F;
        }
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        float base = (dx * dx + dz * dz) < 1.0E-4 ? 0.5F : 1.0F;
        return player.swinging ? Math.max(base, 1.5F) : base;
    }
}
