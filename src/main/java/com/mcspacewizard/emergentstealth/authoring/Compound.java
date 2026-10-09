package com.mcspacewizard.emergentstealth.authoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoute;
import com.mcspacewizard.emergentstealth.ai.routine.Schedule;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * A compound (design doc 32 §3): structure modules plus the markers that make it a guarded place (zones, patrol
 * routes, NPC spawns), all relative to the compound's origin. Stored as datapack JSON under
 * {@code data/<namespace>/emergentstealth/compound/}; see {@link Compounds}.
 *
 * <p>Routes use the patrol route format (doc 15 §2) with relative waypoints. A spawn's schedule refers to the
 * compound's routes by their names here; placing renames them per copy.
 */
public record Compound(List<Module> structures, List<Zone> zones, List<PatrolRoute> routes, List<Spawn> spawns) {
    public static final Codec<Compound> CODEC = RecordCodecBuilder.create(i -> i.group(
            Module.CODEC.listOf().optionalFieldOf("structures", List.of()).forGetter(Compound::structures),
            Zone.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(Compound::zones),
            PatrolRoute.CODEC.listOf().optionalFieldOf("routes", List.of()).forGetter(Compound::routes),
            Spawn.CODEC.listOf().optionalFieldOf("spawns", List.of()).forGetter(Compound::spawns)
    ).apply(i, Compound::new));

    public static final Compound EMPTY = new Compound(List.of(), List.of(), List.of(), List.of());

    /**
     * One structure template in the compound.
     *
     * @param offset where the template's origin goes, relative to the compound's
     * @param ground the template layer (from its bottom) that sits at ground level, for lining modules up
     */
    public record Module(Identifier template, BlockPos offset, Rotation rotation, Mirror mirror, int ground) {
        public static final Codec<Module> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("template").forGetter(Module::template),
                BlockPos.CODEC.optionalFieldOf("offset", BlockPos.ZERO).forGetter(Module::offset),
                Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(Module::rotation),
                Mirror.CODEC.optionalFieldOf("mirror", Mirror.NONE).forGetter(Module::mirror),
                Codec.INT.optionalFieldOf("ground", 0).forGetter(Module::ground)
        ).apply(i, Module::new));

        public Transform transform() {
            return new Transform(mirror, rotation);
        }
    }

    /**
     * An NPC placed with the compound (doc 32 §4).
     *
     * @param behaviour a behaviour tree to use instead of the archetype's
     * @param count     how many stand at this spot (a group)
     */
    public record Spawn(Identifier archetype, BlockPos pos, float facing, Optional<Identifier> behaviour, Schedule schedule, int count) {
        public static final Codec<Spawn> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("archetype").forGetter(Spawn::archetype),
                BlockPos.CODEC.fieldOf("pos").forGetter(Spawn::pos),
                Codec.FLOAT.optionalFieldOf("facing", 0.0F).forGetter(Spawn::facing),
                Identifier.CODEC.optionalFieldOf("behaviour").forGetter(Spawn::behaviour),
                Schedule.CODEC.optionalFieldOf("schedule", Schedule.EMPTY).forGetter(Spawn::schedule),
                Codec.intRange(1, 16).optionalFieldOf("count", 1).forGetter(Spawn::count)
        ).apply(i, Spawn::new));
    }

    public Compound withModule(Module module) {
        return new Compound(append(structures, module), zones, routes, spawns);
    }

    public Compound withZone(Zone zone) {
        List<Zone> list = new ArrayList<>(zones);
        list.removeIf(z -> z.name().equals(zone.name()));
        list.add(zone);
        return new Compound(structures, List.copyOf(list), routes, spawns);
    }

    public Compound withRoute(PatrolRoute route) {
        List<PatrolRoute> list = new ArrayList<>(routes);
        list.removeIf(r -> r.name().equals(route.name()));
        list.add(route);
        return new Compound(structures, zones, List.copyOf(list), spawns);
    }

    public Compound withSpawn(Spawn spawn) {
        return new Compound(structures, zones, routes, append(spawns, spawn));
    }

    private static <T> List<T> append(List<T> list, T item) {
        List<T> copy = new ArrayList<>(list);
        copy.add(item);
        return List.copyOf(copy);
    }

    // --- Moving markers between compound space and the world ---

    /** A route moved by a transform and origin (positions and look directions), and renamed. */
    public static PatrolRoute moveRoute(PatrolRoute route, String newName, Transform transform, BlockPos origin) {
        List<PatrolRoute.Waypoint> waypoints = route.waypoints().stream()
                .map(w -> new PatrolRoute.Waypoint(transform.apply(w.pos()).offset(origin), w.waitTicks(), w.lookYaw().map(transform::yaw), w.relight()))
                .toList();
        return new PatrolRoute(newName, waypoints, route.mode());
    }

    /** A schedule moved by a transform and origin: posts move and turn, routes are renamed by {@code routeNames}. */
    public static Schedule moveSchedule(Schedule schedule, java.util.function.UnaryOperator<String> routeNames, Transform transform, BlockPos origin) {
        List<Schedule.Entry> entries = schedule.entries().stream().map(entry -> new Schedule.Entry(entry.fromHour(), entry.toHour(),
                switch (entry.activity()) {
                    case Schedule.Post post -> new Schedule.Post(transform.apply(post.pos()).offset(origin), transform.yaw(post.yaw()));
                    case Schedule.Route r -> new Schedule.Route(routeNames.apply(r.route()));
                    case Schedule.Wander wander -> wander;
                })).toList();
        return new Schedule(entries);
    }
}
