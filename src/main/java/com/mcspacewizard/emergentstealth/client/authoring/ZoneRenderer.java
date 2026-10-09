package com.mcspacewizard.emergentstealth.client.authoring;

import com.mcspacewizard.emergentstealth.authoring.Zone;
import com.mcspacewizard.emergentstealth.authoring.ZonePayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Draws zones while you hold a Surveyor's Rope (design doc 32 §2): each box as a tinted volume coloured by its
 * rule (public green, restricted amber, hostile red), the rope's zone brighter and labelled, and the box being
 * marked from the first corner to the block under the crosshair.
 */
public final class ZoneRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final int PUBLIC = 0x60A0D0;
    private static final int RESTRICTED = 0xFFB040;
    private static final int HOSTILE = 0xFF4040;
    private static final int MARKING = 0xFFFFFFFF;

    private static volatile ZonePayloads.Sync latest = ZonePayloads.Sync.EMPTY;

    public static void handle(ZonePayloads.Sync payload, IPayloadContext context) {
        latest = payload;
    }

    public static void handleOpen(ZonePayloads.OpenEditor payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new ZoneEditorScreen(payload.zone())));
    }

    public static void clear() {
        latest = ZonePayloads.Sync.EMPTY;
    }

    private static int rgb(Zone.Access access) {
        return switch (access) {
            case PUBLIC -> PUBLIC;
            case RESTRICTED -> RESTRICTED;
            case HOSTILE -> HOSTILE;
        };
    }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess access, Frustum frustum, float partialTicks) {
        ZonePayloads.Sync data = latest;
        for (Zone zone : data.zones()) {
            boolean selected = zone.name().equals(data.selected());
            int rgb = rgb(zone.access());
            int fill = (selected ? 0x38 : 0x1C) << 24 | rgb;
            int line = (selected ? 0xFF : 0x90) << 24 | rgb;
            for (BoundingBox box : zone.boxes()) {
                AABB aabb = AABB.of(box);
                if (!frustum.isVisible(aabb)) {
                    continue;
                }
                // Filled gizmos cull back faces: from inside a box, draw each face both ways round.
                boolean inside = aabb.contains(camX, camY, camZ);
                Gizmos.cuboid(aabb, GizmoStyle.strokeAndFill(line, selected ? 2.5F : 1.5F, fill));
                if (inside) {
                    insideFaces(aabb, fill);
                }
            }
            BoundingBox bounds = zone.bounds();
            Vec3 top = new Vec3((bounds.minX() + bounds.maxX() + 1) / 2.0, bounds.maxY() + 1.4, (bounds.minZ() + bounds.maxZ() + 1) / 2.0);
            String label = zone.name() + "  " + zone.access().getSerializedName()
                    + zone.hours().map(h -> "  " + h.from() + "-" + h.to() + "h").orElse("");
            Gizmos.billboardText(label, top, TextGizmo.Style.forColorAndCentered(line).withScale(selected ? 0.5F : 0.35F));
        }
        data.corner().ifPresent(corner -> {
            BlockPos other = corner;
            HitResult hit = Minecraft.getInstance().hitResult;
            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                other = blockHit.getBlockPos();
            }
            Gizmos.cuboid(AABB.of(BoundingBox.fromCorners(corner, other)), GizmoStyle.stroke(MARKING, 2.0F));
            Gizmos.cuboid(new AABB(corner).inflate(0.02), GizmoStyle.stroke(0xFF40FFB0, 3.0F));
        });
    }

    /** The six faces of a box wound inwards, so they show from inside it. */
    private static void insideFaces(AABB b, int color) {
        GizmoStyle style = GizmoStyle.fill(color);
        Vec3 a = new Vec3(b.minX, b.minY, b.minZ);
        Vec3 bx = new Vec3(b.maxX, b.minY, b.minZ);
        Vec3 by = new Vec3(b.minX, b.maxY, b.minZ);
        Vec3 bz = new Vec3(b.minX, b.minY, b.maxZ);
        Vec3 xy = new Vec3(b.maxX, b.maxY, b.minZ);
        Vec3 xz = new Vec3(b.maxX, b.minY, b.maxZ);
        Vec3 yz = new Vec3(b.minX, b.maxY, b.maxZ);
        Vec3 xyz = new Vec3(b.maxX, b.maxY, b.maxZ);
        Gizmos.rect(a, bx, xz, bz, style);
        Gizmos.rect(a, bz, xz, bx, style);
        Gizmos.rect(by, xy, xyz, yz, style);
        Gizmos.rect(by, yz, xyz, xy, style);
        Gizmos.rect(a, by, xy, bx, style);
        Gizmos.rect(a, bx, xy, by, style);
        Gizmos.rect(bz, xz, xyz, yz, style);
        Gizmos.rect(bz, yz, xyz, xz, style);
        Gizmos.rect(a, bz, yz, by, style);
        Gizmos.rect(a, by, yz, bz, style);
        Gizmos.rect(bx, xy, xyz, xz, style);
        Gizmos.rect(bx, xz, xyz, xy, style);
    }
}
