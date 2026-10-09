package com.mcspacewizard.emergentstealth.authoring;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A structure transform about the origin: mirror first, then rotate, exactly as {@link StructureTemplate} places
 * blocks with the pivot at zero. Compounds use it for their modules and markers (design doc 32 §3), so a marker
 * lands on the same block of a structure whichever way the compound is turned.
 */
public record Transform(Mirror mirror, Rotation rotation) {
    public static final Transform IDENTITY = new Transform(Mirror.NONE, Rotation.NONE);

    public BlockPos apply(BlockPos pos) {
        return StructureTemplate.transform(pos, mirror, rotation, BlockPos.ZERO);
    }

    /** A box's transformed corners, sorted back into a box. */
    public BoundingBox apply(BoundingBox box) {
        BlockPos a = apply(new BlockPos(box.minX(), box.minY(), box.minZ()));
        BlockPos b = apply(new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
        return BoundingBox.fromCorners(a, b);
    }

    /** A facing (Minecraft yaw, 0 = south) turned the same way as the blocks, like {@code Entity.mirror/rotate}. */
    public float yaw(float yaw) {
        float angle = Mth.wrapDegrees(yaw);
        angle = switch (mirror) {
            case FRONT_BACK -> -angle;
            case LEFT_RIGHT -> 180.0F - angle;
            default -> angle;
        };
        return Mth.wrapDegrees(angle + 90.0F * rotation.ordinal());
    }

    /** The single transform that does this one and then {@code outer}. */
    public Transform then(Transform outer) {
        BlockPos x = outer.apply(apply(new BlockPos(1, 0, 0)));
        BlockPos z = outer.apply(apply(new BlockPos(0, 0, 1)));
        for (Mirror m : Mirror.values()) {
            for (Rotation r : Rotation.values()) {
                Transform candidate = new Transform(m, r);
                if (candidate.apply(new BlockPos(1, 0, 0)).equals(x) && candidate.apply(new BlockPos(0, 0, 1)).equals(z)) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("No transform equals " + this + " then " + outer);
    }

    /** The transform that undoes this one. */
    public Transform inverse() {
        for (Mirror m : Mirror.values()) {
            for (Rotation r : Rotation.values()) {
                Transform candidate = new Transform(m, r);
                if (then(candidate).isIdentity()) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("No inverse of " + this);
    }

    /** Mirror-and-rotate pairs can describe the same transform (both mirrors with a half turn are one mirror). */
    public boolean isIdentity() {
        return apply(new BlockPos(1, 0, 0)).equals(new BlockPos(1, 0, 0)) && apply(new BlockPos(0, 0, 1)).equals(new BlockPos(0, 0, 1));
    }
}
