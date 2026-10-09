package com.mcspacewizard.emergentstealth.tool;

import com.mcspacewizard.emergentstealth.registry.ESParticles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Tool effects (design doc 34 §2): particles and small sounds that make each tool read in the world. Visual only:
 * nothing here emits a noise event or changes the world. Server side calls send particles to nearby players;
 * the {@code trail} methods run on the client from a projectile's own tick.
 */
public final class ToolEffects {
    private ToolEffects() {}

    /** Firecracker crackle colours: vermilion, gold, pale. */
    private static final int[] CRACKLE = {0xFFE0482E, 0xFFF2C14E, 0xFFFFF1D6};

    /** A light put out by water: a hiss and a puff of steam. */
    public static void steam(ServerLevel level, BlockPos pos) {
        Vec3 at = Vec3.atCenterOf(pos);
        level.sendParticles(ESParticles.STEAM, at.x, at.y + 0.1, at.z, 7, 0.12, 0.08, 0.12, 0.01);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.35F, 1.7F + level.getRandom().nextFloat() * 0.2F);
    }

    /** A water arrow bursting: drips falling round the splash. */
    public static void splash(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.FALLING_WATER, at.x, at.y, at.z, 12, 0.35, 0.2, 0.35, 0.0);
    }

    /** A fire arrow striking: a burst of embers. */
    public static void embers(ServerLevel level, Vec3 at, int count) {
        level.sendParticles(ESParticles.EMBER, at.x, at.y, at.z, count, 0.08, 0.08, 0.08, 0.12);
    }

    /** A light relit by fire: a short flare and embers. */
    public static void flare(ServerLevel level, BlockPos pos) {
        Vec3 at = Vec3.atCenterOf(pos);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.1, at.z, 5, 0.08, 0.1, 0.08, 0.02);
        embers(level, at.add(0.0, 0.2, 0.0), 6);
    }

    /**
     * Stars circling {@code entity}'s head for {@code ticks}. Sent with count 0, so each star's data travels in
     * the velocity fields (see {@link ESParticles#DIZZY_STAR}).
     */
    public static void dizzy(ServerLevel level, Entity entity, int ticks) {
        int stars = 3;
        for (int i = 0; i < stars; i++) {
            double phase = Math.PI * 2.0 * i / stars;
            level.sendParticles(ESParticles.DIZZY_STAR, entity.getX(), entity.getEyeY() + 0.45, entity.getZ(), 0,
                    entity.getId(), phase, ticks, 1.0);
        }
    }

    /** A drowsy "z" rising over the head. */
    public static void drowsy(ServerLevel level, Entity entity) {
        level.sendParticles(ESParticles.DROWSY, entity.getX(), entity.getEyeY() + 0.35, entity.getZ(), 1, 0.15, 0.05, 0.15, 0.0);
    }

    /** Falling asleep: a slow exhale. */
    public static void exhale(ServerLevel level, Entity entity) {
        level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getEyeY(), entity.getZ(), 4, 0.15, 0.05, 0.15, 0.01);
    }

    /** Glints round the eyes of a blinded NPC. */
    public static void blindedEyes(ServerLevel level, Entity entity) {
        level.sendParticles(ESParticles.GLINT, entity.getX(), entity.getEyeY(), entity.getZ(), 2, 0.25, 0.12, 0.25, 0.0);
    }

    /** A pick slipping: a few sparks at the lock. */
    public static void pickSlip(ServerLevel level, BlockPos pos) {
        Vec3 at = Vec3.atCenterOf(pos);
        level.sendParticles(ESParticles.EMBER, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.06);
    }

    /** A lock giving way: a puff of dust. */
    public static void pickOpen(ServerLevel level, BlockPos pos) {
        Vec3 at = Vec3.atCenterOf(pos);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 3, 0.15, 0.15, 0.15, 0.01);
    }

    /** A ring round a newly tagged NPC, for the tagger only. */
    public static void tagPing(ServerPlayer tagger, Entity target) {
        tagger.level().sendParticles(tagger, ESParticles.TAG_PING, true, true, target.getX(), target.getY(0.6), target.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);
    }

    /** One firecracker bang: a flash of coloured crackle. */
    public static void crackle(ServerLevel level, Vec3 at, RandomSource random) {
        for (int i = 0; i < 3; i++) {
            int color = CRACKLE[random.nextInt(CRACKLE.length)];
            level.sendParticles(new DustParticleOptions(color, 0.6F + random.nextFloat() * 0.5F), at.x, at.y, at.z, 6, 0.25, 0.2, 0.25, 0.0);
        }
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE6B0), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    // ------------------------------------------------------------------ client-side trails

    /** Water arrow in flight: droplets. Call on the client only. */
    public static void waterTrail(Level level, Entity arrow) {
        if (arrow.tickCount % 2 == 0) {
            level.addParticle(ParticleTypes.FALLING_WATER, arrow.getX(), arrow.getY(), arrow.getZ(), 0.0, 0.0, 0.0);
        }
    }

    /** Fire arrow in flight: smoke and the odd ember. Call on the client only. */
    public static void fireTrail(Level level, Entity arrow) {
        RandomSource random = arrow.getRandom();
        level.addParticle(ParticleTypes.SMOKE, arrow.getX(), arrow.getY(), arrow.getZ(), 0.0, 0.01, 0.0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ESParticles.EMBER, arrow.getX(), arrow.getY(), arrow.getZ(),
                    Mth.nextDouble(random, -0.03, 0.03), 0.02, Mth.nextDouble(random, -0.03, 0.03));
        }
    }

    /** Sleep dart in flight: a faint pale trail. Call on the client only. */
    public static void dartTrail(Level level, Entity dart) {
        if (dart.tickCount % 2 == 0) {
            level.addParticle(ParticleTypes.WHITE_ASH, dart.getX(), dart.getY(), dart.getZ(), 0.0, 0.0, 0.0);
        }
    }

    /** A firecracker's fuse sputtering: embers. Call on the client only. */
    public static void fuseSparks(Level level, Entity firecracker) {
        RandomSource random = firecracker.getRandom();
        level.addParticle(ESParticles.EMBER, firecracker.getX(), firecracker.getY() + 0.25, firecracker.getZ(),
                Mth.nextDouble(random, -0.05, 0.05), 0.06 + random.nextDouble() * 0.05, Mth.nextDouble(random, -0.05, 0.05));
    }
}
