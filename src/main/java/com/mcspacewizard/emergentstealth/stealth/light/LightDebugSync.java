package com.mcspacewizard.emergentstealth.stealth.light;

import java.util.ArrayList;
import java.util.List;

import com.mcspacewizard.emergentstealth.network.LightDebugPayload;
import com.mcspacewizard.emergentstealth.registry.ESDebugSubscriptions;
import com.mcspacewizard.emergentstealth.stealth.BodySample;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Sends the light debug view's data to players who requested the {@link ESDebugSubscriptions#LIGHT} debug
 * subscription. Vanilla only honours debug subscriptions for ops and the singleplayer host, so this is
 * permission-gated for free.
 */
public final class LightDebugSync {
    private LightDebugSync() {}

    private static final int INTERVAL = 10;
    private static final int RADIUS = 8;
    /** Heatmap samples at roughly hip height above the floor. */
    private static final double SAMPLE_HEIGHT = 0.9;

    public static void tick(ServerLevel level, List<ServerPlayer> players, long now) {
        if (now % INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : players) {
            if (player.debugSubscriptions().contains(ESDebugSubscriptions.LIGHT.get())) {
                com.mcspacewizard.emergentstealth.registry.ESNetwork.sendIfSupported(player, build(level, player));
            }
        }
    }

    static LightDebugPayload build(ServerLevel level, ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        int size = RADIUS * 2 + 1;
        BlockPos origin = feet.offset(-RADIUS, 0, -RADIUS);
        List<Integer> heights = new ArrayList<>(size * size);
        byte[] exposure = new byte[size * size];
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                int index = dz * size + dx;
                int floorY = findFloor(level, origin.getX() + dx, feet.getY(), origin.getZ() + dz);
                heights.add(floorY);
                if (floorY == Integer.MIN_VALUE) {
                    exposure[index] = (byte) LightDebugPayload.NO_DATA;
                } else {
                    Vec3 point = new Vec3(origin.getX() + dx + 0.5, floorY + SAMPLE_HEIGHT, origin.getZ() + dz + 0.5);
                    exposure[index] = (byte) Math.round(ExposureModel.INSTANCE.exposure(level, point) * 250.0F);
                }
            }
        }

        List<LightDebugPayload.Sample> samples = new ArrayList<>();
        Vec3 chest = player.position().add(0.0, player.getBbHeight() * 0.62, 0.0);
        for (BodySample sample : BodySample.of(player, true)) {
            samples.add(new LightDebugPayload.Sample(sample.position(), ExposureModel.INSTANCE.exposure(level, sample.position())));
            if (sample.part() == BodySample.Part.CHEST) {
                chest = sample.position();
            }
        }

        ExposureBreakdown.Result breakdown = ExposureBreakdown.explain(level, chest);
        List<LightDebugPayload.Source> sources = new ArrayList<>();
        for (ExposureBreakdown.Source source : breakdown.sources()) {
            sources.add(new LightDebugPayload.Source(source.pos(), source.potential(), source.transmittance(), source.dynamic()));
        }
        List<Float> stats = List.of(breakdown.blockExposure(), breakdown.skyExposure(), breakdown.skyAccess(),
                breakdown.celestialClear(), breakdown.total());
        return new LightDebugPayload(origin, size, heights, exposure, samples, sources, breakdown.celestialDir(), stats);
    }

    /** Standing floor near {@code nearY}: an air-ish block with something solid below. MIN_VALUE if none. */
    private static int findFloor(ServerLevel level, int x, int nearY, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = nearY + 2; y >= nearY - 3; y--) {
            pos.set(x, y, z);
            BlockState here = level.getBlockState(pos);
            BlockState below = level.getBlockState(pos.below());
            if (here.getCollisionShape(level, pos).isEmpty() && !below.getCollisionShape(level, pos.below()).isEmpty()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }
}
