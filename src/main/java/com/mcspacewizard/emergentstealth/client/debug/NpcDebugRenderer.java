package com.mcspacewizard.emergentstealth.client.debug;

import com.mcspacewizard.emergentstealth.debug.NpcDebugInfo;
import com.mcspacewizard.emergentstealth.registry.ESDebugSubscriptions;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws NPC debug info from the {@link ESDebugSubscriptions#NPC} subscription: a label (archetype, role,
 * faction, state) and the vision cone outline. Later stages add their own renderers next to this one.
 */
public class NpcDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_CONE = 0xC0FFD24A;
    private static final int COLOR_FACING = 0xFFFF5A3C;
    private static final int CONE_SEGMENTS = 8;

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess access, Frustum frustum, float partialTicks) {
        if (!ClientDebugState.isEnabled()) {
            return;
        }
        access.forEachEntity(ESDebugSubscriptions.NPC.get(), (entity, info) -> {
            if (!frustum.isVisible(entity.getBoundingBox().inflate(info.viewRange()))) {
                return;
            }
            emitLabel(entity, info);
            emitVisionCone(entity, info, partialTicks);
        });
    }

    private static void emitLabel(Entity entity, NpcDebugInfo info) {
        BlockPos above = BlockPos.containing(entity.getX(), entity.getY() + entity.getBbHeight() + 0.6, entity.getZ());
        Gizmos.billboardTextOverBlock(info.archetype() + " [" + info.role() + "]", above, 0, COLOR_TEXT, 0.5F);
        Gizmos.billboardTextOverBlock(info.faction(), above, 1, COLOR_TEXT, 0.5F);
        Gizmos.billboardTextOverBlock("state: " + info.state(), above, 2, COLOR_TEXT, 0.5F);
    }

    /** Outline of the central vision cone: facing ray, the four edge rays and the far rim. */
    private static void emitVisionCone(Entity entity, NpcDebugInfo info, float partialTicks) {
        Vec3 eye = entity.getEyePosition(partialTicks);
        float yaw = entity instanceof LivingEntity living ? Mth.rotLerp(partialTicks, living.yHeadRotO, living.yHeadRot) : entity.getYRot();
        float pitch = entity.getXRot();
        float half = info.fovDegrees() / 2.0F;
        float range = info.viewRange();

        Gizmos.addGizmo(new DebugLineGizmo(eye, eye.add(Vec3.directionFromRotation(pitch, yaw).scale(range)), COLOR_FACING, 2.0F));

        // Rim: a ring of points at the cone's edge, connected, plus spokes from the eye.
        Vec3 previous = null;
        Vec3 first = null;
        for (int i = 0; i < CONE_SEGMENTS; i++) {
            double angle = (Math.PI * 2.0 * i) / CONE_SEGMENTS;
            float rimPitch = pitch + (float) (Math.sin(angle) * half);
            float rimYaw = yaw + (float) (Math.cos(angle) * half);
            Vec3 point = eye.add(Vec3.directionFromRotation(rimPitch, rimYaw).scale(range));
            if (i % 2 == 0) {
                Gizmos.addGizmo(new DebugLineGizmo(eye, point, COLOR_CONE, 1.0F));
            }
            if (previous != null) {
                Gizmos.addGizmo(new DebugLineGizmo(previous, point, COLOR_CONE, 1.0F));
            } else {
                first = point;
            }
            previous = point;
        }
        if (previous != null && first != null) {
            Gizmos.addGizmo(new DebugLineGizmo(previous, first, COLOR_CONE, 1.0F));
        }
    }
}
