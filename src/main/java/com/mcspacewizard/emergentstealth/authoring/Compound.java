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
import net.minecraft.util.StringRepresentable;
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
public record Compound(List<Module> structures, List<Zone> zones, List<PatrolRoute> routes, List<Spawn> spawns, Lights lights) {
    public static final Codec<Compound> CODEC = RecordCodecBuilder.create(i -> i.group(
            Module.CODEC.listOf().optionalFieldOf("structures", List.of()).forGetter(Compound::structures),
            Zone.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(Compound::zones),
            PatrolRoute.CODEC.listOf().optionalFieldOf("routes", List.of()).forGetter(Compound::routes),
            Spawn.CODEC.listOf().optionalFieldOf("spawns", List.of()).forGetter(Compound::spawns),
            Lights.CODEC.optionalFieldOf("lights", Lights.ALL).forGetter(Compound::lights)
    ).apply(i, Compound::new));

    public static final Compound EMPTY = new Compound(List.of(), List.of(), List.of(), List.of());

    /** A compound whose lights are all relit. */
    public Compound(List<Module> structures, List<Zone> zones, List<PatrolRoute> routes, List<Spawn> spawns) {
        this(structures, zones, routes, spawns, Lights.ALL);
    }

    /**
     * One structure template in the compound.
     *
     * @param offset where the template's origin goes, relative to the compound's
     * @param ground the template layer (from its bottom) that sits at ground level. Recorded for lining modules up
     *               (doc 32 decision 6); placing doesn't use it yet: the offset already says where the module goes
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

    /**
     * Which lights inside the compound the lamplighter relights (doc 32 §3): all or none, except the listed ones
     * (positions relative to the origin, like every marker). Only lights a route's {@code relight} waypoint reaches
     * are ever relit; this rule lets some of them stay dark (a lantern left out for the player, a ruined wing).
     */
    public record Lights(Relight relight, List<BlockPos> except) {
        public static final Codec<Lights> CODEC = RecordCodecBuilder.create(i -> i.group(
                Relight.CODEC.optionalFieldOf("relight", Relight.ALL).forGetter(Lights::relight),
                BlockPos.CODEC.listOf().optionalFieldOf("except", List.of()).forGetter(Lights::except)
        ).apply(i, Lights::new));

        public static final Lights ALL = new Lights(Relight.ALL, List.of());

        /** Whether the light at {@code local} (compound space) is relit. */
        public boolean relights(BlockPos local) {
            return (relight == Relight.ALL) != except.contains(local);
        }

        /** The light at {@code local} switched to the other side of the rule. */
        public Lights toggled(BlockPos local) {
            List<BlockPos> list = new ArrayList<>(except);
            if (!list.remove(local)) {
                list.add(local.immutable());
            }
            return new Lights(relight, List.copyOf(list));
        }

        /** The other rule, with no exceptions. */
        public Lights flipped() {
            return new Lights(relight == Relight.ALL ? Relight.NONE : Relight.ALL, List.of());
        }
    }

    public enum Relight implements StringRepresentable {
        ALL("all"),
        NONE("none");

        public static final Codec<Relight> CODEC = StringRepresentable.fromEnum(Relight::values);
        private final String name;

        Relight(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public Compound withModule(Module module) {
        return new Compound(append(structures, module), zones, routes, spawns, lights);
    }

    /** With module {@code index} replaced. */
    public Compound withModule(int index, Module module) {
        List<Module> list = new ArrayList<>(structures);
        list.set(index, module);
        return new Compound(List.copyOf(list), zones, routes, spawns, lights);
    }

    public Compound withLights(Lights newLights) {
        return new Compound(structures, zones, routes, spawns, newLights);
    }

    public Compound withZone(Zone zone) {
        List<Zone> list = new ArrayList<>(zones);
        list.removeIf(z -> z.name().equals(zone.name()));
        list.add(zone);
        return new Compound(structures, List.copyOf(list), routes, spawns, lights);
    }

    public Compound withRoute(PatrolRoute route) {
        List<PatrolRoute> list = new ArrayList<>(routes);
        list.removeIf(r -> r.name().equals(route.name()));
        list.add(route);
        return new Compound(structures, zones, List.copyOf(list), spawns, lights);
    }

    public Compound withSpawn(Spawn spawn) {
        return new Compound(structures, zones, routes, append(spawns, spawn), lights);
    }

    public Compound withoutZone(String name) {
        return new Compound(structures, zones.stream().filter(z -> !z.name().equals(name)).toList(), routes, spawns, lights);
    }

    public Compound withoutRoute(String name) {
        return new Compound(structures, zones, routes.stream().filter(r -> !r.name().equals(name)).toList(), spawns, lights);
    }

    /** Without the spawn at {@code index} (unchanged if there's none). */
    public Compound withoutSpawn(int index) {
        if (index < 0 || index >= spawns.size()) {
            return this;
        }
        List<Spawn> list = new ArrayList<>(spawns);
        list.remove(index);
        return new Compound(structures, zones, routes, List.copyOf(list), lights);
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
