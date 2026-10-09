package com.mcspacewizard.emergentstealth.entity;

import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.registry.ESParticles;
import com.mcspacewizard.emergentstealth.stealth.SmokeVolumes;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A smoke bomb's cloud (design doc 21 §2). Server side it owns a {@link SmokeVolumes} sphere, so every sight ray
 * through it is blocked, and it blinds NPCs standing inside. Client side it keeps the cloud thick by spawning
 * {@link ESParticles#SMOKE_CLOUD} puffs every tick (no particle traffic over the network).
 */
public class SmokeCloud extends Entity {
    public static final float RADIUS = 3.0F;
    public static final int DURATION_TICKS = 200;
    /** NPCs inside are re-blinded this often, for a little longer than the interval. */
    private static final int BLIND_INTERVAL = 5;
    /** The cloud stops being fed this long before it ends, so it thins out as the sight block runs out. */
    private static final int THIN_OUT_TICKS = 30;

    private static final EntityDataAccessor<Long> DATA_END_TICK = SynchedEntityData.defineId(SmokeCloud.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(SmokeCloud.class, EntityDataSerializers.FLOAT);

    private long volumeId;

    public SmokeCloud(EntityType<? extends SmokeCloud> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** A cloud centred at {@code center} that lasts {@code duration} ticks from now. */
    public static SmokeCloud create(ServerLevel level, Vec3 center, float radius, int duration) {
        SmokeCloud cloud = new SmokeCloud(ESEntities.SMOKE_CLOUD.get(), level);
        cloud.setPos(center);
        cloud.entityData.set(DATA_END_TICK, level.getGameTime() + duration);
        cloud.entityData.set(DATA_RADIUS, radius);
        return cloud;
    }

    public float radius() {
        return this.entityData.get(DATA_RADIUS);
    }

    public long endTick() {
        return this.entityData.get(DATA_END_TICK);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_END_TICK, 0L);
        builder.define(DATA_RADIUS, RADIUS);
    }

    @Override
    public void tick() {
        super.tick();
        long now = this.level().getGameTime();
        if (this.level() instanceof ServerLevel level) {
            if (now >= endTick()) {
                this.discard();
                return;
            }
            if (volumeId == 0) {
                register(level);
            }
            if (this.tickCount % BLIND_INTERVAL == 0) {
                blindInside(level, now);
            }
        } else {
            spawnPuffs(now);
        }
    }

    /** NPCs whose eyes are in the smoke see nothing (they can still hear). */
    private void blindInside(ServerLevel level, long now) {
        float r = radius();
        Vec3 center = this.position();
        for (StealthNpc npc : level.getEntitiesOfClass(StealthNpc.class, new AABB(center, center).inflate(r))) {
            if (npc.getEyePosition().distanceToSqr(center) <= r * r) {
                npc.blind(now + BLIND_INTERVAL + 3);
            }
        }
    }

    private void spawnPuffs(long now) {
        long left = endTick() - now;
        if (left <= THIN_OUT_TICKS) {
            return;
        }
        RandomSource random = this.random;
        float r = radius();
        boolean burst = this.tickCount <= 1;
        // Grows from the pop to full size in about half a second.
        float grow = Mth.clamp((this.tickCount + 3) / 12.0F, 0.25F, 1.0F);
        int count = burst ? 40 : 7;
        for (int i = 0; i < count; i++) {
            // Uniform in the sphere, slightly biased outward so the edge reads as a wall.
            double u = Math.cbrt(0.15 + 0.85 * random.nextDouble());
            double theta = random.nextDouble() * Math.PI * 2.0;
            double phi = Math.acos(2.0 * random.nextDouble() - 1.0);
            double dx = Math.sin(phi) * Math.cos(theta);
            double dy = Math.cos(phi);
            double dz = Math.sin(phi) * Math.sin(theta);
            double dist = u * r * grow * 0.85;
            double x = this.getX() + dx * dist;
            double y = this.getY() + dy * dist * 0.85;
            double z = this.getZ() + dz * dist;
            double out = burst ? 0.12 : (grow < 1.0F ? 0.03 : 0.0);
            // The heart is darker and denser (design doc 34 §2); the edge curls slowly round the cloud.
            boolean core = u < 0.6 && random.nextInt(3) == 0;
            double curl = u > 0.8 ? 0.012 : 0.0;
            this.level().addParticle(core ? ESParticles.SMOKE_CORE : ESParticles.SMOKE_CLOUD, x, y, z,
                    dx * out - dz * curl + (random.nextDouble() - 0.5) * 0.01, dy * out * 0.5 + 0.002,
                    dz * out + dx * curl + (random.nextDouble() - 0.5) * 0.01);
        }
    }

    private void register(ServerLevel level) {
        volumeId = SmokeVolumes.add(level, this.position(), radius(), endTick()).id();
    }

    /** Blocks sight from the moment it's in the level (not a tick later), and again after a chunk reload. */
    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (this.level() instanceof ServerLevel level && volumeId == 0 && level.getGameTime() < endTick()) {
            register(level);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        if (volumeId != 0) {
            SmokeVolumes.remove(this.level(), volumeId);
            volumeId = 0;
        }
        super.onRemovedFromLevel();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.entityData.set(DATA_END_TICK, input.getLongOr("EndTick", 0L));
        this.entityData.set(DATA_RADIUS, input.getFloatOr("Radius", RADIUS));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putLong("EndTick", endTick());
        output.putFloat("Radius", radius());
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    /** Visible from as far as the player could see the cloud itself. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0 * 128.0;
    }
}
