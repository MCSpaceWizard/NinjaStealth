package com.mcspacewizard.emergentstealth.client.debug;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.network.LightDebugPayload;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Light debug view (design doc 13): a floor heatmap of exposure around you (dark blue = hidden, yellow =
 * lit), your body points' exposure, rays from each light reaching your chest (green = clear, yellow =
 * partial, red = shadowed; dashed-looking thin lines = carried/dynamic lights) and an arrow toward the sun
 * or moon (yellow if it reaches you, grey if you're in its shadow).
 */
public class LightDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final int COLOR_DARK = 0xFF1A2050;
    private static final int COLOR_MID = 0xFF7A3FA0;
    private static final int COLOR_LIT = 0xFFFFD84A;
    private static final int HEATMAP_ALPHA = 0x90;
    private static final int COLOR_CLEAR = 0xFF40FF40;
    private static final int COLOR_PARTIAL = 0xFFFFE040;
    private static final int COLOR_BLOCKED = 0xFFFF4040;
    private static final int COLOR_CELESTIAL_LIT = 0xFFFFF080;
    private static final int COLOR_CELESTIAL_SHADOW = 0xFF808080;
    /** Drop data older than this (ms) so the view clears when the server stops sending. */
    private static final long STALE_MS = 2000;

    private static volatile @Nullable LightDebugPayload latest;
    private static volatile long receivedAt;

    public static void handle(LightDebugPayload payload, IPayloadContext context) {
        latest = payload;
        receivedAt = System.currentTimeMillis();
    }

    public static void clear() {
        latest = null;
    }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess access, Frustum frustum, float partialTicks) {
        LightDebugPayload data = latest;
        if (!ClientDebugState.isLightEnabled() || data == null || System.currentTimeMillis() - receivedAt > STALE_MS) {
            return;
        }
        emitHeatmap(data);
        emitSources(data);
        emitSamples(data);
        emitCelestial(data, new Vec3(camX, camY, camZ));
    }

    private static void emitHeatmap(LightDebugPayload data) {
        int size = data.gridSize();
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                int index = dz * size + dx;
                int value = data.gridExposure()[index] & 0xFF;
                if (value == LightDebugPayload.NO_DATA) {
                    continue;
                }
                float exposure = value / 250.0F;
                int y = data.gridHeights().get(index);
                double x0 = data.gridOrigin().getX() + dx + 0.1;
                double z0 = data.gridOrigin().getZ() + dz + 0.1;
                Vec3 a = new Vec3(x0, y + 0.02, z0);
                Vec3 b = new Vec3(x0 + 0.8, y + 0.02, z0 + 0.8);
                Gizmos.rect(a, b, Direction.UP, GizmoStyle.fill(ARGB.color(HEATMAP_ALPHA, heatColor(exposure))));
            }
        }
    }

    private static int heatColor(float exposure) {
        return exposure < 0.5F
                ? ARGB.srgbLerp(exposure * 2.0F, COLOR_DARK, COLOR_MID)
                : ARGB.srgbLerp((exposure - 0.5F) * 2.0F, COLOR_MID, COLOR_LIT);
    }

    private static void emitSources(LightDebugPayload data) {
        Vec3 chest = data.samples().size() > 1 ? data.samples().get(1).pos() : null;
        if (chest == null) {
            return;
        }
        for (LightDebugPayload.Source source : data.sources()) {
            float t = source.transmittance();
            int color = t >= 0.99F ? COLOR_CLEAR : t > 0.0F ? COLOR_PARTIAL : COLOR_BLOCKED;
            Gizmos.line(source.pos(), chest, color, source.dynamic() ? 1.0F : 2.0F);
            Gizmos.billboardText(String.format(java.util.Locale.ROOT, "%.2f x %.2f", source.potential(), t),
                    source.pos().add(0.0, 0.4, 0.0), TextGizmo.Style.forColorAndCentered(color).withScale(0.25F));
        }
    }

    private static void emitSamples(LightDebugPayload data) {
        for (LightDebugPayload.Sample sample : data.samples()) {
            int color = ARGB.opaque(heatColor(sample.exposure()));
            Gizmos.point(sample.pos(), color, 6.0F);
        }
        if (data.stats().size() >= 5 && !data.samples().isEmpty()) {
            Vec3 head = data.samples().get(0).pos().add(0.0, 0.7, 0.0);
            String line = String.format(java.util.Locale.ROOT, "exposure %.2f  (blocks %.2f, sky %.2f, sky access %.2f)",
                    data.stats().get(4), data.stats().get(0), data.stats().get(1), data.stats().get(2));
            Gizmos.billboardText(line, head, TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(0.25F)).setAlwaysOnTop();
        }
    }

    private static void emitCelestial(LightDebugPayload data, Vec3 camera) {
        if (data.stats().size() < 4 || data.celestialDir().y <= 0.05) {
            return;
        }
        boolean clear = data.stats().get(3) > 0.5F;
        Vec3 start = camera.add(data.celestialDir().scale(2.0)).add(0.0, -0.5, 0.0);
        Gizmos.arrow(start, start.add(data.celestialDir().scale(2.0)), clear ? COLOR_CELESTIAL_LIT : COLOR_CELESTIAL_SHADOW, 2.0F);
    }
}
