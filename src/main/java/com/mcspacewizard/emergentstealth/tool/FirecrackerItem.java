package com.mcspacewizard.emergentstealth.tool;

import com.mcspacewizard.emergentstealth.entity.LitFirecracker;
import com.mcspacewizard.emergentstealth.entity.ThrownItem;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Firecracker (design doc 21 §2): lands, fizzes for 2 s, then bangs (noise 24 each) for 3 s. Draws guards away.
 */
public class FirecrackerItem extends ThrowableToolItem {
    public FirecrackerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onImpact(ServerLevel level, ThrownItem thrown, Vec3 at, HitResult hit) {
        LitFirecracker firecracker = LitFirecracker.create(level, at);
        // Keep a little of the throw's momentum, so it skids after hitting the floor.
        firecracker.setDeltaMovement(thrown.getDeltaMovement().multiply(0.2, 0.0, 0.2));
        level.addFreshEntity(firecracker);
    }
}
