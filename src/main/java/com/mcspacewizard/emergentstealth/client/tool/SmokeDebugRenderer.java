package com.mcspacewizard.emergentstealth.client.tool;

import java.util.List;
import java.util.Locale;

import com.mcspacewizard.emergentstealth.client.debug.ClientDebugState;
import com.mcspacewizard.emergentstealth.network.SmokeDebugPayload;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Smoke volumes in the AI debug view (design doc 21): each sight-blocking sphere as three great circles plus
 * horizontal slices, labelled with the seconds it has left. Data only arrives for ops / the singleplayer host.
 */
public class SmokeDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final int COLOR = 0xFFB0B8C8;
    private static final int SEGMENTS = 32;

    private static volatile List<SmokeDebugPayload.Smoke> shown = List.of();
    private static volatile long receivedAt;

    public static void handle(SmokeDebugPayload payload, IPayloadContext context) {
        shown = List.copyOf(payload.volumes());
        receivedAt = System.currentTimeMillis();
    }

    public static void clear() {
        shown = List.of();
    }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess access, Frustum frustum, float partialTicks) {
        if (!ClientDebugState.isEnabled()) {
            return;
        }
        List<SmokeDebugPayload.Smoke> volumes = shown;
        // Updates come twice a second; anything older than 2 s is stale (left the area, lost the subscription).
        long age = System.currentTimeMillis() - receivedAt;
        if (volumes.isEmpty() || age > 2000) {
            return;
        }
        for (SmokeDebugPayload.Smoke smoke : volumes) {
            float secondsLeft = Math.max(0.0F, smoke.ticksLeft() / 20.0F - age / 1000.0F);
            draw(smoke.center(), smoke.radius(), secondsLeft);
        }
    }

    private static void draw(Vec3 c, float r, float secondsLeft) {
        // Horizontal slices (circle gizmos are horizontal).
        for (int i = -2; i <= 2; i++) {
            double dy = r * i / 3.0;
            float sliceR = (float) Math.sqrt(Math.max(0.0, r * r - dy * dy));
            Gizmos.circle(c.add(0.0, dy, 0.0), sliceR, GizmoStyle.stroke(COLOR, i == 0 ? 2.5F : 1.5F));
        }
        // Two vertical great circles.
        ring(c, r, true);
        ring(c, r, false);
        Gizmos.point(c, COLOR, 6.0F);
        Gizmos.billboardText(String.format(Locale.ROOT, "smoke r%.1f  %.1fs", r, secondsLeft), c.add(0.0, r + 0.4, 0.0),
                TextGizmo.Style.forColorAndCentered(COLOR).withScale(0.4F));
    }

    private static void ring(Vec3 c, float r, boolean xy) {
        Vec3 prev = null;
        for (int i = 0; i <= SEGMENTS; i++) {
            double a = Math.PI * 2.0 * i / SEGMENTS;
            double u = Math.cos(a) * r;
            double v = Math.sin(a) * r;
            Vec3 p = xy ? c.add(u, v, 0.0) : c.add(0.0, v, u);
            if (prev != null) {
                Gizmos.line(prev, p, COLOR, 1.5F);
            }
            prev = p;
        }
    }
}
