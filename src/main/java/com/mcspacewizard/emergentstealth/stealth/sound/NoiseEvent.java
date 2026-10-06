package com.mcspacewizard.emergentstealth.stealth.sound;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.Vec3;

/**
 * A noise made somewhere in the world (design doc 16 §1).
 *
 * @param pos      where the noise is
 * @param loudness how far it carries in open air, in blocks
 * @param kind     what made it
 * @param cause    the player the noise gives away (footsteps, doors, combat), or null when it only points at a
 *                 spot (thrown items, shouts)
 * @param source   the entity that made the noise, if any; it never hears itself
 */
public record NoiseEvent(Vec3 pos, float loudness, NoiseKind kind, @Nullable UUID cause, @Nullable UUID source) {
    public static NoiseEvent of(Vec3 pos, float loudness, NoiseKind kind) {
        return new NoiseEvent(pos, loudness, kind, null, null);
    }
}
