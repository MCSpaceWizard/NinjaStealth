package com.mcspacewizard.emergentstealth.ai.routine;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;

/**
 * A named patrol route (design doc 15 §2): ordered waypoints walked in a loop or back and forth.
 * Immutable; edits produce a new route stored in {@link PatrolRoutes}.
 */
public record PatrolRoute(String name, List<Waypoint> waypoints, Mode mode) {
    public static final Codec<PatrolRoute> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(PatrolRoute::name),
            Waypoint.CODEC.listOf().fieldOf("waypoints").forGetter(PatrolRoute::waypoints),
            Mode.CODEC.optionalFieldOf("mode", Mode.LOOP).forGetter(PatrolRoute::mode)
    ).apply(i, PatrolRoute::new));

    public enum Mode implements StringRepresentable {
        /** 0, 1, 2, 0, 1, 2 ... */
        LOOP("loop"),
        /** 0, 1, 2, 1, 0, 1 ... */
        PINGPONG("pingpong");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * @param pos       standing position (the block the NPC's feet occupy)
     * @param waitTicks how long to stand here before moving on
     * @param lookYaw   direction to face while waiting; empty = look around
     * @param relight   relight unlit torches/lanterns within reach on arrival (the lamplighter, Q10)
     */
    public record Waypoint(BlockPos pos, int waitTicks, Optional<Float> lookYaw, boolean relight) {
        public static final Codec<Waypoint> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Waypoint::pos),
                Codec.INT.optionalFieldOf("wait_ticks", 0).forGetter(Waypoint::waitTicks),
                Codec.FLOAT.optionalFieldOf("look_yaw").forGetter(Waypoint::lookYaw),
                Codec.BOOL.optionalFieldOf("relight", false).forGetter(Waypoint::relight)
        ).apply(i, Waypoint::new));

        public static Waypoint at(BlockPos pos) {
            return new Waypoint(pos, 0, Optional.empty(), false);
        }
    }

    public PatrolRoute withWaypoints(List<Waypoint> newWaypoints) {
        return new PatrolRoute(name, List.copyOf(newWaypoints), mode);
    }

    public PatrolRoute withMode(Mode newMode) {
        return new PatrolRoute(name, waypoints, newMode);
    }

    /** Index after {@code index}, given the travel direction (+1/-1, only meaningful for pingpong). */
    public int next(int index, int direction) {
        int size = waypoints.size();
        if (size <= 1) {
            return 0;
        }
        if (mode == Mode.LOOP) {
            return (index + 1) % size;
        }
        int candidate = index + direction;
        return candidate < 0 || candidate >= size ? index - direction : candidate;
    }
}
