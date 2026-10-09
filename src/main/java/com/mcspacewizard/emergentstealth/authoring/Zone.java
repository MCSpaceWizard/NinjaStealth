package com.mcspacewizard.emergentstealth.authoring;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A named volume with an access rule (design doc 32 §2): one or more boxes, and optionally the hours the rule
 * applies. Compounds store zones relative to their origin; placed zones are in world coordinates.
 */
public record Zone(String name, Access access, List<BoundingBox> boxes, Optional<Hours> hours) {
    /** A box as its two corners, {@code [[x, y, z], [x, y, z]]}. */
    public static final Codec<BoundingBox> BOX_CODEC = BlockPos.CODEC.listOf(2, 2)
            .xmap(corners -> BoundingBox.fromCorners(corners.get(0), corners.get(1)),
                    box -> List.of(new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ())));

    public static final Codec<Zone> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(Zone::name),
            Access.CODEC.fieldOf("access").forGetter(Zone::access),
            BOX_CODEC.listOf().fieldOf("boxes").forGetter(Zone::boxes),
            Hours.CODEC.optionalFieldOf("hours").forGetter(Zone::hours)
    ).apply(i, Zone::new));

    public enum Access implements StringRepresentable {
        /** Anyone may be here. */
        PUBLIC("public"),
        /** Guards who see you here get suspicious much faster and warn you off. */
        RESTRICTED("restricted"),
        /** Being seen here is detection. */
        HOSTILE("hostile");

        public static final Codec<Access> CODEC = StringRepresentable.fromEnum(Access::values);
        private final String name;

        Access(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** Clock hours (0-24, may wrap past midnight) when the rule applies; outside them the zone is public. */
    public record Hours(int from, int to) {
        public static final Codec<Hours> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 24).fieldOf("from").forGetter(Hours::from),
                Codec.intRange(0, 24).fieldOf("to").forGetter(Hours::to)
        ).apply(i, Hours::new));

        public boolean contains(int hour) {
            if (from == to) {
                return true;
            }
            return from < to ? hour >= from && hour < to : hour >= from || hour < to;
        }
    }

    public boolean contains(BlockPos pos) {
        for (BoundingBox box : boxes) {
            if (box.isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    /** This zone moved by a compound's transform and origin, and renamed. */
    public Zone placed(String newName, Transform transform, BlockPos origin) {
        return new Zone(newName, access, boxes.stream().map(box -> transform.apply(box).moved(origin.getX(), origin.getY(), origin.getZ())).toList(), hours);
    }
}
