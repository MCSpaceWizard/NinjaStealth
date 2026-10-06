package com.mcspacewizard.emergentstealth.stealth.sound;

import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESEntities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * Entry point for noises (design doc 16). Anything that makes a noise calls {@link #emit}; every
 * {@link StealthNpc} that hears it gets {@link StealthNpc#onNoiseHeard}.
 *
 * <p>Placeholder propagation: straight-line distance only, no walls or masking. Stage 6 replaces it.
 */
public final class Noises {
    private Noises() {}

    public static void emit(ServerLevel level, NoiseEvent noise) {
        if (noise.loudness() <= 0.0F) {
            return;
        }
        double r = noise.loudness();
        for (StealthNpc npc : level.getEntities(ESEntities.STEALTH_NPC.get(), AABB.ofSize(noise.pos(), r * 2, r * 2, r * 2), npc -> !npc.isNoAi())) {
            if (npc.getUUID().equals(noise.source())) {
                continue;
            }
            float cost = (float) npc.getEyePosition().distanceTo(noise.pos());
            float intensity = 1.0F - cost / noise.loudness();
            if (intensity > 0.0F) {
                npc.onNoiseHeard(level, new HeardNoise(noise, intensity, cost));
            }
        }
    }
}
