package com.mcspacewizard.emergentstealth.network;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Payloads for the timing-ring lockpicking minigame (design doc 21 §2). */
public final class LockpickPayloads {
    private LockpickPayloads() {}

    /**
     * Server → client: open the minigame for the lock at {@code pos}. {@code window} is the timing window in
     * degrees, already scaled by the picker's {@code lockpick_window} stat.
     */
    public record Open(BlockPos pos, int difficulty, int pins, float window) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(EmergentStealth.id("lockpick_open"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Open::pos,
                ByteBufCodecs.VAR_INT, Open::difficulty,
                ByteBufCodecs.VAR_INT, Open::pins,
                ByteBufCodecs.FLOAT, Open::window,
                Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: one click (hit or miss), or giving up. The server validates and rate-limits. */
    public record Action(int action) implements CustomPacketPayload {
        public static final int MISS = 0;
        public static final int HIT = 1;
        public static final int CANCEL = 2;

        public static final Type<Action> TYPE = new Type<>(EmergentStealth.id("lockpick_action"));
        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Action::new, Action::action);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server → client: the authoritative progress, and whether the minigame is over. */
    public record State(int progress, int outcome) implements CustomPacketPayload {
        public static final int CONTINUE = 0;
        public static final int SUCCESS = 1;
        public static final int FAILED = 2;

        public static final Type<State> TYPE = new Type<>(EmergentStealth.id("lockpick_state"));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, State::progress,
                ByteBufCodecs.VAR_INT, State::outcome,
                State::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
