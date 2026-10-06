package com.mcspacewizard.emergentstealth.network;

import java.util.List;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → a player holding a Patrol Baton: nearby patrol routes to draw. */
public record RouteSyncPayload(String selected, List<Route> routes) implements CustomPacketPayload {
    public static final Type<RouteSyncPayload> TYPE = new Type<>(EmergentStealth.id("route_sync"));

    public record Point(BlockPos pos, int waitTicks, boolean hasLook, float lookYaw, boolean relight) {
        public static final StreamCodec<ByteBuf, Point> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Point::pos,
                ByteBufCodecs.VAR_INT, Point::waitTicks,
                ByteBufCodecs.BOOL, Point::hasLook,
                ByteBufCodecs.FLOAT, Point::lookYaw,
                ByteBufCodecs.BOOL, Point::relight,
                Point::new);
    }

    public record Route(String name, boolean loop, List<Point> points) {
        public static final StreamCodec<ByteBuf, Route> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Route::name,
                ByteBufCodecs.BOOL, Route::loop,
                Point.STREAM_CODEC.apply(ByteBufCodecs.list(256)), Route::points,
                Route::new);
    }

    public static final StreamCodec<ByteBuf, RouteSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RouteSyncPayload::selected,
            Route.STREAM_CODEC.apply(ByteBufCodecs.list(128)), RouteSyncPayload::routes,
            RouteSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
