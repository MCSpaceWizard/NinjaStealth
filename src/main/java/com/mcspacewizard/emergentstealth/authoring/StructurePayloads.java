package com.mcspacewizard.emergentstealth.authoring;

import java.util.List;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/** Network messages of the structure viewer (design doc 32 §1). Every one is checked on the server. */
public final class StructurePayloads {
    private StructurePayloads() {}

    private static final StreamCodec<ByteBuf, Rotation> ROTATION = ByteBufCodecs.idMapper(i -> Rotation.values()[i], Rotation::ordinal);
    private static final StreamCodec<ByteBuf, Mirror> MIRROR = ByteBufCodecs.idMapper(i -> Mirror.values()[i], Mirror::ordinal);

    /** Client → server: send me the structure list (opens the browser on arrival). */
    public record RequestList() implements CustomPacketPayload {
        public static final Type<RequestList> TYPE = new Type<>(EmergentStealth.id("structure_list_request"));
        public static final StreamCodec<ByteBuf, RequestList> STREAM_CODEC = StreamCodec.unit(new RequestList());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server → client: every structure template id and every compound id, and whether to open the browser now. */
    public record StructureList(List<Identifier> ids, List<Identifier> compounds, boolean open) implements CustomPacketPayload {
        public static final Type<StructureList> TYPE = new Type<>(EmergentStealth.id("structure_list"));
        public static final StreamCodec<ByteBuf, StructureList> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), StructureList::ids,
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), StructureList::compounds,
                ByteBufCodecs.BOOL, StructureList::open,
                StructureList::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: send me this structure's (or compound's, all its structures') blocks for the ghost preview. */
    public record RequestPreview(Identifier id, boolean compound) implements CustomPacketPayload {
        public static final Type<RequestPreview> TYPE = new Type<>(EmergentStealth.id("structure_preview_request"));
        public static final StreamCodec<ByteBuf, RequestPreview> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, RequestPreview::id,
                ByteBufCodecs.BOOL, RequestPreview::compound,
                RequestPreview::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: one part of a structure's preview ({@link StructureCatalog#previewBytes}, compressed NBT
     * without air), split so no packet gets near the payload size limit.
     */
    public record PreviewPart(Identifier id, boolean compound, int part, int parts, byte[] data) implements CustomPacketPayload {
        public static final Type<PreviewPart> TYPE = new Type<>(EmergentStealth.id("structure_preview"));
        public static final StreamCodec<ByteBuf, PreviewPart> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, PreviewPart::id,
                ByteBufCodecs.BOOL, PreviewPart::compound,
                ByteBufCodecs.VAR_INT, PreviewPart::part,
                ByteBufCodecs.VAR_INT, PreviewPart::parts,
                ByteBufCodecs.BYTE_ARRAY, PreviewPart::data,
                PreviewPart::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: place this structure (or compound) with its origin here. */
    public record Place(Identifier id, boolean compound, BlockPos origin, Rotation rotation, Mirror mirror) implements CustomPacketPayload {
        public static final Type<Place> TYPE = new Type<>(EmergentStealth.id("structure_place"));
        public static final StreamCodec<ByteBuf, Place> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Place::id,
                ByteBufCodecs.BOOL, Place::compound,
                BlockPos.STREAM_CODEC, Place::origin,
                ROTATION, Place::rotation,
                MIRROR, Place::mirror,
                Place::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: undo my last placement. */
    public record Undo() implements CustomPacketPayload {
        public static final Type<Undo> TYPE = new Type<>(EmergentStealth.id("structure_undo"));
        public static final StreamCodec<ByteBuf, Undo> STREAM_CODEC = StreamCodec.unit(new Undo());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
