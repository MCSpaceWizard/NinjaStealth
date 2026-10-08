package com.mcspacewizard.emergentstealth.tool;

import com.mcspacewizard.emergentstealth.entity.SmokeCloud;
import com.mcspacewizard.emergentstealth.entity.ThrownItem;
import com.mcspacewizard.emergentstealth.registry.ESParticles;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseEvent;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseKind;
import com.mcspacewizard.emergentstealth.stealth.sound.Noises;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Smoke bomb (design doc 21 §2): pops with a loud noise (12) and leaves a 3-block-radius cloud for 10 s that blocks
 * sight completely and blinds NPCs inside it.
 */
public class SmokeBombItem extends ThrowableToolItem {
    public static final float POP_LOUDNESS = 12.0F;
    /** The cloud's centre sits this far above the landing point, so it covers a standing person. */
    public static final double CENTER_RAISE = 1.0;

    public SmokeBombItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onImpact(ServerLevel level, ThrownItem thrown, Vec3 at, HitResult hit) {
        pop(level, at);
    }

    /** Spawns the cloud at a landing point (also used by tests and commands). */
    public static SmokeCloud pop(ServerLevel level, Vec3 at) {
        Noises.emit(level, new NoiseEvent(at, POP_LOUDNESS, NoiseKind.EXPLOSION, null, null));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.6F, 1.7F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1.0F, 0.6F);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.3, at.z, 20, 0.3, 0.3, 0.3, 0.08);
        level.sendParticles(ESParticles.SMOKE_CLOUD, at.x, at.y + 0.5, at.z, 12, 0.6, 0.4, 0.6, 0.05);
        SmokeCloud cloud = SmokeCloud.create(level, at.add(0.0, CENTER_RAISE, 0.0), SmokeCloud.RADIUS, SmokeCloud.DURATION_TICKS);
        level.addFreshEntity(cloud);
        return cloud;
    }
}
