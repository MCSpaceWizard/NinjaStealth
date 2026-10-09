package com.mcspacewizard.emergentstealth.authoring;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A named volume with an access rule (design doc 32 §2): one or more boxes, and optionally the hours the rule
 * applies. Compounds store zones relative to their origin; placed zones are in world coordinates.
 */
public record Zone(String name, Access access, List<BoundingBox> boxes, Optional<Hours> hours) {
    /** Longest zone name the tools accept (placed copies prefix the compound's name). */
    public static final int MAX_NAME_LENGTH = 128;
    /** Most boxes one zone may have over the network. */
    public static final int MAX_BOXES = 64;

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

    /** Access rules, from the most lenient to the strictest (the order matters to {@link Trespass}). */
    /** A box as its two corners on the wire. */
    public static final StreamCodec<ByteBuf, BoundingBox> BOX_STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, box -> new BlockPos(box.minX(), box.minY(), box.minZ()),
            BlockPos.STREAM_CODEC, box -> new BlockPos(box.maxX(), box.maxY(), box.maxZ()),
            BoundingBox::fromCorners);

    public static final StreamCodec<ByteBuf, Zone> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), Zone::name,
            Access.STREAM_CODEC, Zone::access,
            BOX_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_BOXES)), Zone::boxes,
            ByteBufCodecs.optional(Hours.STREAM_CODEC), Zone::hours,
            Zone::new);

    /** Access rules, from the most lenient to the strictest (the order matters to {@link Trespass}). */
    public enum Access implements StringRepresentable {
        /** Anyone may be here. */
        PUBLIC("public"),
        /** Guards who see you here get suspicious much faster and warn you off. */
        RESTRICTED("restricted"),
        /** Being seen here is detection. */
        HOSTILE("hostile");

        public static final Codec<Access> CODEC = StringRepresentable.fromEnum(Access::values);
        public static final StreamCodec<ByteBuf, Access> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Access::ordinal);
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
        public static final StreamCodec<ByteBuf, Hours> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Hours::from,
                ByteBufCodecs.VAR_INT, Hours::to,
                Hours::new);

        public boolean contains(int hour) {
            if (from == to) {
                return true;
            }
            return from < to ? hour >= from && hour < to : hour >= from || hour < to;
        }
    }

    /** Whether the rule holds at this clock hour (always, without hours). */
    public boolean appliesAt(int hour) {
        return hours.isEmpty() || hours.get().contains(hour);
    }

    /** This zone with one more box. */
    public Zone withBox(BoundingBox box) {
        List<BoundingBox> list = new java.util.ArrayList<>(boxes);
        list.add(box);
        return new Zone(name, access, List.copyOf(list), hours);
    }

    /** The box around every box of this zone. */
    public BoundingBox bounds() {
        return BoundingBox.encapsulatingBoxes(boxes).orElseThrow();
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
