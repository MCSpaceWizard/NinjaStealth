package com.mcspacewizard.emergentstealth.tool;

import com.mcspacewizard.emergentstealth.entity.ThrownItem;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An item with its own landing effect when thrown (with G, V or right-click). {@link ThrownItem} calls this
 * instead of its default noise-and-drop; the thrown entity is discarded afterwards.
 */
public interface ThrowableTool {
    /**
     * @param at the impact point, nudged out of the block face that was hit (so it's in the air on the thrower's side)
     */
    void onImpact(ServerLevel level, ThrownItem thrown, Vec3 at, HitResult hit);
}
