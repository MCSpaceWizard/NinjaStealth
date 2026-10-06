package com.mcspacewizard.emergentstealth.client.debug;

import com.mcspacewizard.emergentstealth.debug.NpcDebugInfo;
import com.mcspacewizard.emergentstealth.registry.ESDebugSubscriptions;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws NPC debug info from the {@link ESDebugSubscriptions#NPC} subscription: a label (archetype, role,
 * state, awareness, LOD tier), the central and peripheral vision cones, sight rays to the focus target
 * (green clear / yellow partial / red blocked) and a marker at its last known position.
 */
public class NpcDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_CENTRAL = 0xC0FFD24A;
    private static final int COLOR_PERIPHERAL = 0x808CA0FF;
    private static final int COLOR_FACING = 0xFFFF5A3C;
    private static final int COLOR_RAY_CLEAR = 0xFF40FF40;
    private static final int COLOR_RAY_PARTIAL = 0xFFFFE040;
    private static final int COLOR_RAY_BLOCKED = 0xFFFF4040;
    private static final int COLOR_LAST_KNOWN = 0xFFFF40FF;
    private static final int CONE_SEGMENTS = 12;
    private static final float TEXT_SCALE = 0.4F;

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess access, Frustum frustum, float partialTicks) {
        if (!ClientDebugState.isEnabled()) {
            return;
        }
        access.forEachEntity(ESDebugSubscriptions.NPC.get(), (entity, info) -> {
            float reach = Math.max(info.cones().centralRange(), info.cones().peripheralRange());
            if (!frustum.isVisible(entity.getBoundingBox().inflate(reach))) {
                return;
            }
            emitLabel(entity, info, partialTicks);
            emitCones(entity, info, partialTicks);
            for (NpcDebugInfo.Ray ray : info.rays()) {
                int color = ray.transmittance() >= 0.99F ? COLOR_RAY_CLEAR : ray.transmittance() > 0.0F ? COLOR_RAY_PARTIAL : COLOR_RAY_BLOCKED;
                Gizmos.line(entity.getEyePosition(partialTicks), ray.point(), color, 1.5F);
            }
            info.lastKnown().ifPresent(pos ->
                    Gizmos.cuboid(new AABB(pos.subtract(0.3, 0.0, 0.3), pos.add(0.3, 1.8, 0.3)), GizmoStyle.stroke(COLOR_LAST_KNOWN, 2.0F)));
        });
    }

    private static void emitLabel(Entity entity, NpcDebugInfo info, float partialTicks) {
        Vec3 top = entity.getPosition(partialTicks).add(0.0, entity.getBbHeight() + 0.9, 0.0);
        String[] lines = {
                info.archetype() + " [" + info.role() + "]",
                "state: " + info.state() + "   tier " + info.tier(),
                String.format(java.util.Locale.ROOT, "awareness: %.2f", info.awareness())
        };
        for (int i = 0; i < lines.length; i++) {
            Gizmos.billboardText(lines[i], top.add(0.0, (lines.length - 1 - i) * 0.25, 0.0),
                    TextGizmo.Style.forColorAndCentered(COLOR_TEXT).withScale(TEXT_SCALE)).setAlwaysOnTop();
        }
    }

    /** Central cone as a full outline; peripheral cone as a flat horizontal fan (to keep the view readable). */
    private static void emitCones(Entity entity, NpcDebugInfo info, float partialTicks) {
        Vec3 eye = entity.getEyePosition(partialTicks);
        float yaw = entity instanceof LivingEntity living ? Mth.rotLerp(partialTicks, living.yHeadRotO, living.yHeadRot) : entity.getYRot();
        float pitch = entity.getXRot();
        NpcDebugInfo.Cones cones = info.cones();

        Gizmos.line(eye, eye.add(Vec3.directionFromRotation(pitch, yaw).scale(cones.centralRange())), COLOR_FACING, 2.0F);

        // Central cone: ring at the rim plus spokes.
        float half = cones.centralHalfAngle();
        float vHalf = Math.min(half, cones.verticalHalfAngle());
        Vec3 previous = null;
        Vec3 first = null;
        for (int i = 0; i < CONE_SEGMENTS; i++) {
            double angle = (Math.PI * 2.0 * i) / CONE_SEGMENTS;
            Vec3 point = eye.add(Vec3.directionFromRotation(pitch + (float) (Math.sin(angle) * vHalf), yaw + (float) (Math.cos(angle) * half))
                    .scale(cones.centralRange()));
            if (i % 3 == 0) {
                Gizmos.line(eye, point, COLOR_CENTRAL, 1.0F);
            }
            if (previous != null) {
                Gizmos.line(previous, point, COLOR_CENTRAL, 1.0F);
            } else {
                first = point;
            }
            previous = point;
        }
        if (previous != null) {
            Gizmos.line(previous, first, COLOR_CENTRAL, 1.0F);
        }

        // Peripheral fan (horizontal, at eye height).
        float pHalf = cones.peripheralHalfAngle();
        float range = cones.peripheralRange();
        Vec3 left = eye.add(Vec3.directionFromRotation(0.0F, yaw - pHalf).scale(range));
        Vec3 right = eye.add(Vec3.directionFromRotation(0.0F, yaw + pHalf).scale(range));
        Gizmos.line(eye, left, COLOR_PERIPHERAL, 1.0F);
        Gizmos.line(eye, right, COLOR_PERIPHERAL, 1.0F);
        Vec3 arcPrev = left;
        for (int i = 1; i <= CONE_SEGMENTS; i++) {
            float a = yaw - pHalf + (2.0F * pHalf * i) / CONE_SEGMENTS;
            Vec3 arc = eye.add(Vec3.directionFromRotation(0.0F, a).scale(range));
            Gizmos.line(arcPrev, arc, COLOR_PERIPHERAL, 1.0F);
            arcPrev = arc;
        }
    }
}
