package com.mcspacewizard.emergentstealth.authoring;

import java.util.List;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Network messages of the Compound Ledger (design doc 32 §3). Actions are checked on the server. */
public final class LedgerPayloads {
    private LedgerPayloads() {}

    private static final int MAX_LIST = 256;

    /**
     * A zone or route the ledger can show.
     *
     * @param detail  what to show beside the name (a zone's rule, a route's waypoint count)
     * @param inDraft whether the open draft holds it
     * @param inWorld whether the world has one of that name (so it can be added)
     */
    public record Entry(String name, String detail, boolean inDraft, boolean inWorld) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), Entry::name,
                ByteBufCodecs.stringUtf8(256), Entry::detail,
                ByteBufCodecs.BOOL, Entry::inDraft,
                ByteBufCodecs.BOOL, Entry::inWorld,
                Entry::new);
    }

    /**
     * Server → client: what the ledger shows, opening the panel or refreshing it.
     *
     * @param draft      the open draft's id, or empty when there is none
     * @param origin     the draft's origin, or where a new one would start
     * @param structures how many modules the draft has
     * @param startFrom  the template a new draft would start from (the author's last placed structure, when the
     *                   ledger was used on it), or empty
     * @param spawns     one line per spawn marker
     * @param relightAll the draft's light rule: the lamplighter relights all lights but the exceptions (or none but them)
     * @param lights     the lights found in the draft's structures: name is the position, {@code inDraft} whether it is relit
     * @param open       open the panel if it isn't (false: only refresh an open one)
     */
    public record State(String draft, BlockPos origin, int structures, String startFrom, List<Entry> zones, List<Entry> routes, List<String> spawns,
                        boolean relightAll, List<Entry> lights, boolean open)
            implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(EmergentStealth.id("ledger_state"));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), State::draft,
                BlockPos.STREAM_CODEC, State::origin,
                ByteBufCodecs.VAR_INT, State::structures,
                ByteBufCodecs.stringUtf8(256), State::startFrom,
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LIST)), State::zones,
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LIST)), State::routes,
                ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(MAX_LIST)), State::spawns,
                ByteBufCodecs.BOOL, State::relightAll,
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LIST)), State::lights,
                ByteBufCodecs.BOOL, State::open,
                State::new);

        public boolean hasDraft() {
            return !draft.isEmpty();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** What the ledger panel asks for. */
    public enum Kind {
        /** Start a draft named {@code text} at {@code pos}. */
        START,
        /** Add the world zone {@code text} to the draft, or take it out. */
        TOGGLE_ZONE,
        /** Add the world route {@code text} to the draft, or take it out. */
        TOGGLE_ROUTE,
        /** Take the spawn marker {@code index} out of the draft. */
        REMOVE_SPAWN,
        /** Switch the light at {@code text} ("x y z") between relit and left dark. */
        TOGGLE_LIGHT,
        /** Switch the draft's light rule between relighting all and none. */
        LIGHT_RULE,
        /** Save the draft. */
        SAVE,
        /** Throw the draft away. */
        DISCARD;

        public static final StreamCodec<ByteBuf, Kind> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Kind::ordinal);
    }

    /** Client → server: one ledger action. {@code pos} is where the ledger was used (for START). */
    public record Action(Kind kind, String text, int index, BlockPos pos) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(EmergentStealth.id("ledger_action"));
        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                Kind.STREAM_CODEC, Action::kind,
                ByteBufCodecs.stringUtf8(256), Action::text,
                ByteBufCodecs.VAR_INT, Action::index,
                BlockPos.STREAM_CODEC, Action::pos,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
