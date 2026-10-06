package com.mcspacewizard.emergentstealth.client.debug;

import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.world.phys.Vec3;

/** A single debug line. Colour is ARGB. */
public record DebugLineGizmo(Vec3 from, Vec3 to, int argb, float width) implements Gizmo {
    @Override
    public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * alphaMultiplier);
        primitives.addLine(from, to, (alpha << 24) | (argb & 0x00FFFFFF), width);
    }
}
