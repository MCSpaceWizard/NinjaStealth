package com.mcspacewizard.emergentstealth.client.debug;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Locale;

import com.mcspacewizard.emergentstealth.network.NoiseDebugPayload;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Noise view of the AI debug overlay (design doc 16 §6): a ring at each noise sized by its loudness, fading over
 * 2 seconds, and a line to every NPC that heard it, coloured by intensity (red = loud and clear, blue = barely),
 * labelled with the kind and propagation cost. Data only arrives for ops / the singleplayer host.
 */
public class NoiseDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final long SHOW_MILLIS = 2000;
    private static final int MAX_SHOWN = 256;
    private static final int RING_ATTRIBUTED = 0xFFFF5050;
    private static final int RING_ANONYMOUS = 0xFFFFD040;

    private record Shown(NoiseDebugPayload.Noise noise, long receivedAt) {}

    private static final ArrayDeque<Shown> SHOWN = new ArrayDeque<>();

    public static void handle(NoiseDebugPayload payload, IPayloadContext context) {
        long now = System.currentTimeMillis();
        synchronized (SHOWN) {
            for (NoiseDebugPayload.Noise noise : payload.noises()) {
                SHOWN.addLast(new Shown(noise, now));
                if (SHOWN.size() > MAX_SHOWN) {
                    SHOWN.removeFirst();
                }
            }
        }
    }

    public static void clear() {
        synchronized (SHOWN) {
            SHOWN.clear();
        }
    }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess access, Frustum frustum, float partialTicks) {
        if (!ClientDebugState.isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (SHOWN) {
            Iterator<Shown> it = SHOWN.iterator();
            while (it.hasNext()) {
                Shown shown = it.next();
                long age = now - shown.receivedAt();
                if (age > SHOW_MILLIS) {
                    it.remove();
                    continue;
                }
                draw(shown.noise(), 1.0F - (float) age / SHOW_MILLIS);
            }
        }
    }

    private static void draw(NoiseDebugPayload.Noise noise, float fade) {
        int alpha = Mth.clamp((int) (255 * fade), 30, 255);
        int ringColor = ARGB.color(alpha, noise.attributed() ? RING_ATTRIBUTED : RING_ANONYMOUS);
        Vec3 pos = noise.pos();
        Gizmos.circle(pos.add(0.0, 0.05, 0.0), noise.loudness(), GizmoStyle.stroke(ringColor, 2.0F));
        Gizmos.circle(pos.add(0.0, 0.05, 0.0), 0.3F, GizmoStyle.stroke(ringColor, 3.0F));
        Gizmos.billboardText(String.format(Locale.ROOT, "%s %.0f%s", noise.kind(), noise.loudness(), noise.attributed() ? " (player)" : ""),
                pos.add(0.0, 0.6, 0.0), TextGizmo.Style.forColorAndCentered(ringColor).withScale(0.3F));
        for (NoiseDebugPayload.Heard heard : noise.heard()) {
            int color = ARGB.color(alpha, intensityColor(heard.intensity()));
            Gizmos.line(pos, heard.ear(), color, 2.0F);
            Vec3 mid = pos.add(heard.ear()).scale(0.5).add(0.0, 0.3, 0.0);
            Gizmos.billboardText(String.format(Locale.ROOT, "%s %.2f  cost %.1f", noise.kind(), heard.intensity(), heard.cost()),
                    mid, TextGizmo.Style.forColorAndCentered(color).withScale(0.25F));
        }
    }

    /** Blue (barely heard) → green → red (loud and clear). */
    static int intensityColor(float intensity) {
        float t = Mth.clamp(intensity, 0.0F, 1.0F);
        int r;
        int g;
        int b;
        if (t < 0.5F) {
            float k = t / 0.5F;
            r = 0;
            g = (int) (255 * k);
            b = (int) (255 * (1.0F - k));
        } else {
            float k = (t - 0.5F) / 0.5F;
            r = (int) (255 * k);
            g = (int) (255 * (1.0F - k));
            b = 0;
        }
        return ARGB.color(255, r, g, b);
    }
}
