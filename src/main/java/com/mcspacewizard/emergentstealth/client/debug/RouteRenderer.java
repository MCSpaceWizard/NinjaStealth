package com.mcspacewizard.emergentstealth.client.debug;

import java.util.List;

import com.mcspacewizard.emergentstealth.network.RouteSyncPayload;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Draws patrol routes while you hold a Patrol Baton: route lines (the baton's route bright, others dim),
 * numbered waypoints, look directions and markers for waits and relighting.
 */
public class RouteRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final int COLOR_SELECTED = 0xFF40FFB0;
    private static final int COLOR_OTHER = 0x9060A0A0;
    private static final int COLOR_LOOK = 0xFFFFE060;
    private static final int COLOR_RELIGHT = 0xFFFF9030;

    private static volatile RouteSyncPayload latest = new RouteSyncPayload("", List.of());

    public static void handle(RouteSyncPayload payload, IPayloadContext context) {
        latest = payload;
    }

    public static void clear() {
        latest = new RouteSyncPayload("", List.of());
    }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess access, Frustum frustum, float partialTicks) {
        RouteSyncPayload data = latest;
        for (RouteSyncPayload.Route route : data.routes()) {
            boolean selected = route.name().equals(data.selected());
            int color = selected ? COLOR_SELECTED : COLOR_OTHER;
            List<RouteSyncPayload.Point> points = route.points();
            for (int i = 0; i < points.size(); i++) {
                RouteSyncPayload.Point point = points.get(i);
                Vec3 at = Vec3.atBottomCenterOf(point.pos()).add(0.0, 0.15, 0.0);
                if (i + 1 < points.size()) {
                    Gizmos.arrow(at, Vec3.atBottomCenterOf(points.get(i + 1).pos()).add(0.0, 0.15, 0.0), color, selected ? 2.5F : 1.5F);
                } else if (route.loop() && points.size() > 2) {
                    Gizmos.arrow(at, Vec3.atBottomCenterOf(points.getFirst().pos()).add(0.0, 0.15, 0.0), color, selected ? 2.5F : 1.5F);
                }
                Gizmos.cuboid(new AABB(at.subtract(0.2, 0.15, 0.2), at.add(0.2, 0.25, 0.2)),
                        GizmoStyle.stroke(point.relight() ? COLOR_RELIGHT : color, 2.0F));
                StringBuilder label = new StringBuilder(selected ? route.name() + " #" + i : "#" + i);
                if (point.waitTicks() > 0) {
                    label.append("  wait ").append(point.waitTicks() / 20).append("s");
                }
                if (point.relight()) {
                    label.append("  relight");
                }
                Gizmos.billboardText(label.toString(), at.add(0.0, 0.6, 0.0), TextGizmo.Style.forColorAndCentered(color).withScale(0.3F));
                if (point.hasLook()) {
                    double rad = Math.toRadians(point.lookYaw());
                    Vec3 eye = at.add(0.0, 1.45, 0.0);
                    Gizmos.arrow(eye, eye.add(-Math.sin(rad) * 1.5, 0.0, Math.cos(rad) * 1.5), COLOR_LOOK, 2.0F);
                }
            }
        }
    }
}
