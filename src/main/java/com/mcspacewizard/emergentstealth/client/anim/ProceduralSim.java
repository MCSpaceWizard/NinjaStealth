package com.mcspacewizard.emergentstealth.client.anim;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Something with memory that a pose needs: springs, distance-driven phases, verlet chains (design doc 17 §8).
 * Stepped once per client tick (20 Hz) for every animated entity in range, never from {@code setupAnim}.
 */
public interface ProceduralSim {
    void tick(LivingEntity entity, ClientLevel level, ProceduralState state);
}
