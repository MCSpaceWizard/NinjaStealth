package com.mcspacewizard.emergentstealth.authoring;

import java.util.List;
import java.util.Optional;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Network messages of the zone tool, the Surveyor's Rope (design doc 32 §2). Edits are checked on the server. */
public final class ZonePayloads {
    private ZonePayloads() {}

    /** Server → a player holding the rope: nearby zones to draw, the rope's zone and its pending corner. */
    public record Sync(String selected, Optional<BlockPos> corner, List<Zone> zones) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(EmergentStealth.id("zone_sync"));
        public static final Sync EMPTY = new Sync("", Optional.empty(), List.of());
        public static final StreamCodec<ByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(Zone.MAX_NAME_LENGTH), Sync::selected,
                ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Sync::corner,
                Zone.STREAM_CODEC.apply(ByteBufCodecs.list(256)), Sync::zones,
                Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server → client: open the zone panel on this zone. */
    public record OpenEditor(Zone zone) implements CustomPacketPayload {
        public static final Type<OpenEditor> TYPE = new Type<>(EmergentStealth.id("zone_editor"));
        public static final StreamCodec<ByteBuf, OpenEditor> STREAM_CODEC = Zone.STREAM_CODEC.map(OpenEditor::new, OpenEditor::zone);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** What the zone panel asks for. */
    public enum Action {
        /** Rename and set the rule and hours. */
        SAVE,
        /** Take the zone's newest box away. */
        REMOVE_LAST_BOX,
        /** Delete the zone. */
        DELETE;

        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Action::ordinal);
    }

    /** Client → server: change the zone called {@code original}. Boxes are never sent; only the server moves them. */
    public record Edit(String original, Action action, String name, Zone.Access access, Optional<Zone.Hours> hours) implements CustomPacketPayload {
        public static final Type<Edit> TYPE = new Type<>(EmergentStealth.id("zone_edit"));
        public static final StreamCodec<ByteBuf, Edit> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(Zone.MAX_NAME_LENGTH), Edit::original,
                Action.STREAM_CODEC, Edit::action,
                ByteBufCodecs.stringUtf8(Zone.MAX_NAME_LENGTH), Edit::name,
                Zone.Access.STREAM_CODEC, Edit::access,
                ByteBufCodecs.optional(Zone.Hours.STREAM_CODEC), Edit::hours,
                Edit::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
