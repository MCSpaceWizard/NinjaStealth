package com.mcspacewizard.emergentstealth.authoring;

import java.util.List;
import java.util.Optional;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.routine.Schedule;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Network messages of the NPC spawner, the Muster Roll (design doc 32 §4). Requests are checked on the server. */
public final class MusterPayloads {
    private MusterPayloads() {}

    /** Longest list a message carries. */
    public static final int MAX_LIST = 256;
    /** Longest route or compound name a message carries. */
    public static final int MAX_NAME = 256;
    private static final StreamCodec<ByteBuf, Schedule> SCHEDULE = ByteBufCodecs.fromCodec(Schedule.CODEC);

    /**
     * Server → client: open the Muster Roll panel for a spawn standing on {@code pos}.
     *
     * @param facing      the suggested facing (where the author looks, to the nearest 45°)
     * @param archetypes  every archetype the server knows
     * @param behaviours  every behaviour tree the server knows
     * @param routes      patrol routes a schedule may use: the open draft's, then the world's nearest
     * @param draft       the compound draft the spawn will join, or empty when it only spawns NPCs
     */
    public record Open(BlockPos pos, float facing, List<Identifier> archetypes, List<Identifier> behaviours, List<String> routes, String draft)
            implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(EmergentStealth.id("muster_open"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Open::pos,
                ByteBufCodecs.FLOAT, Open::facing,
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LIST)), Open::archetypes,
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LIST)), Open::behaviours,
                ByteBufCodecs.stringUtf8(MAX_NAME).apply(ByteBufCodecs.list(MAX_LIST)), Open::routes,
                ByteBufCodecs.stringUtf8(MAX_NAME), Open::draft,
                Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: muster {@code count} NPCs on {@code pos}. */
    public record Request(BlockPos pos, float facing, Identifier archetype, Optional<Identifier> behaviour, Schedule schedule, int count)
            implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(EmergentStealth.id("muster_request"));
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Request::pos,
                ByteBufCodecs.FLOAT, Request::facing,
                Identifier.STREAM_CODEC, Request::archetype,
                ByteBufCodecs.optional(Identifier.STREAM_CODEC), Request::behaviour,
                SCHEDULE, Request::schedule,
                ByteBufCodecs.VAR_INT, Request::count,
                Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
