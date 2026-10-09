package com.mcspacewizard.emergentstealth.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonParser;
import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoute;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoutes;
import com.mcspacewizard.emergentstealth.ai.routine.Schedule;
import com.mcspacewizard.emergentstealth.authoring.Compound;
import com.mcspacewizard.emergentstealth.authoring.TerrainFit;
import com.mcspacewizard.emergentstealth.authoring.CompoundPlacer;
import com.mcspacewizard.emergentstealth.authoring.Compounds;
import com.mcspacewizard.emergentstealth.authoring.PlacedCompounds;
import com.mcspacewizard.emergentstealth.authoring.StructurePlacement;
import com.mcspacewizard.emergentstealth.authoring.Transform;
import com.mcspacewizard.emergentstealth.authoring.Zone;
import com.mcspacewizard.emergentstealth.authoring.Zones;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Compounds (design doc 32 §3, §6): the transform maths, the file format, and the round trip. A compound placed
 * at every rotation, mirrored or not, puts each marker (route waypoint, NPC and its post, zone) on the same block
 * of its structure, and undo takes all of it back.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class CompoundTests {
    private CompoundTests() {}

    private static final Identifier ARENA = EmergentStealth.id("arena");
    static final Identifier SHRINE = EmergentStealth.id("edo/small_shrine_v1");
    private static final Identifier GUARD = EmergentStealth.id("ashigaru");
    /** Templates only: these tests are about markers, and place in the air or inside the test arena. */
    static final Compound.Terrain EXACT = Compound.Terrain.DEFAULT.withMode(TerrainFit.Mode.EXACT);

    private static final Map<Identifier, Consumer<GameTestHelper>> TESTS = Map.of(
            EmergentStealth.id("compounds/transform_algebra"), CompoundTests::transformAlgebra,
            EmergentStealth.id("compounds/file_format"), CompoundTests::fileFormat,
            EmergentStealth.id("compounds/round_trip"), CompoundTests::roundTrip,
            EmergentStealth.id("compounds/examples"), CompoundTests::examples,
            EmergentStealth.id("compounds/terrain"), CompoundTests::terrain);

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        TESTS.forEach((id, function) -> event.register(Registries.TEST_FUNCTION, id, () -> function));
    }

    @SubscribeEvent
    static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(EmergentStealth.id("compounds"));
        for (Identifier id : TESTS.keySet()) {
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(environment, ARENA, 40, 0, true)));
        }
    }

    private static List<Transform> all() {
        List<Transform> list = new java.util.ArrayList<>();
        for (Mirror mirror : List.of(Mirror.NONE, Mirror.FRONT_BACK)) {
            for (Rotation rotation : Rotation.values()) {
                list.add(new Transform(mirror, rotation));
            }
        }
        return list;
    }

    /** The block a facing points at from the origin (yaw 0 = south, 90 = west). */
    private static BlockPos facing(float yaw) {
        return new BlockPos(Math.round(-Mth.sin(yaw * Mth.DEG_TO_RAD)), 0, Math.round(Mth.cos(yaw * Mth.DEG_TO_RAD)));
    }

    /** Composition, and facings turning with the blocks, for every mirror and rotation. */
    static void transformAlgebra(GameTestHelper helper) {
        BlockPos[] probes = {new BlockPos(3, 1, -2), new BlockPos(-5, 0, 7)};
        for (Mirror m1 : Mirror.values()) {
            for (Rotation r1 : Rotation.values()) {
                Transform a = new Transform(m1, r1);
                for (float yaw = 0; yaw < 360; yaw += 90) {
                    helper.assertTrue(facing(a.yaw(yaw)).equals(a.apply(facing(yaw))), a + " turns facing " + yaw + " like a block: "
                            + a.yaw(yaw) + " vs " + a.apply(facing(yaw)));
                }
                for (Mirror m2 : Mirror.values()) {
                    for (Rotation r2 : Rotation.values()) {
                        Transform b = new Transform(m2, r2);
                        Transform ab = a.then(b);
                        for (BlockPos p : probes) {
                            helper.assertTrue(ab.apply(p).equals(b.apply(a.apply(p))), a + " then " + b);
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    /** A compound survives JSON both ways, and the hand-written example in doc 32 parses. */
    static void fileFormat(GameTestHelper helper) {
        Compound compound = sample(new BlockPos(2, 1, 3));
        String json = Compounds.toJson(compound);
        Compound back = Compound.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        helper.assertTrue(back.equals(compound), "JSON round trip:\n" + json + "\nread back as " + back);
        helper.assertTrue(json.contains("\"pos\": [2, 2, 3]"), "Positions are written on one line:\n" + json);

        // Ids may hold ".." segments: saving one must not write outside the world folder.
        for (String path : List.of("../escape", "a/../../escape", "./escape")) {
            Identifier unsafe = Identifier.fromNamespaceAndPath(EmergentStealth.MODID, path);
            try {
                Compounds.saveToWorld(helper.getLevel().getServer(), unsafe, compound);
                helper.fail("Saving " + unsafe + " should be refused");
            } catch (java.io.IOException expected) {
                // refused, as it should be
            }
        }

        String example = """
                { "structures": [{ "template": "emergentstealth:edo/samurai_mini_fort" },
                                 { "template": "emergentstealth:cherrygrove/teahouse", "offset": [52, -3, 6], "rotation": "clockwise_90", "ground": 3 }],
                  "zones": [{ "name": "courtyard", "access": "restricted", "boxes": [[[2, 0, 2], [43, 10, 43]]] }],
                  "routes": [{ "name": "wall_walk", "mode": "loop", "waypoints": [{ "pos": [4, 5, 4], "wait_ticks": 40, "look_yaw": 90 }] }],
                  "spawns": [{ "archetype": "emergentstealth:ashigaru", "pos": [10, 1, 12], "facing": 180,
                               "schedule": [{ "from": 6, "to": 18, "activity": { "type": "route", "route": "wall_walk" } },
                                            { "from": 18, "to": 6, "activity": { "type": "post", "pos": [10, 1, 12], "yaw": 180 } }] }] }
                """;
        Compound parsed = Compound.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(example)).getOrThrow();
        helper.assertTrue(parsed.structures().size() == 2 && parsed.structures().get(1).rotation() == Rotation.CLOCKWISE_90
                && parsed.zones().getFirst().access() == Zone.Access.RESTRICTED && parsed.spawns().getFirst().schedule().entries().size() == 2,
                "Doc example: " + parsed);
        helper.succeed();
    }

    /**
     * The shrine with a route whose first waypoint stands on its chest, a guard there with a post on the same spot,
     * and a zone round the chest.
     */
    static Compound sample(BlockPos chest) {
        BlockPos stand = chest.above();
        PatrolRoute route = new PatrolRoute("shrine_walk",
                List.of(new PatrolRoute.Waypoint(stand, 20, Optional.of(90.0F), false), PatrolRoute.Waypoint.at(stand.offset(2, 0, 0))),
                PatrolRoute.Mode.PINGPONG);
        Schedule schedule = new Schedule(List.of(
                new Schedule.Entry(6, 18, new Schedule.Route("shrine_walk")),
                new Schedule.Entry(18, 6, new Schedule.Post(stand, 90.0F))));
        Compound.Spawn spawn = new Compound.Spawn(GUARD, stand, 90.0F, Optional.empty(), schedule, 1);
        Zone zone = new Zone("chest", Zone.Access.RESTRICTED, List.of(new BoundingBox(chest.getX() - 1, chest.getY(), chest.getZ() - 1,
                chest.getX() + 1, chest.getY() + 1, chest.getZ() + 1)), Optional.empty());
        return new Compound(List.of(new Compound.Module(SHRINE, BlockPos.ZERO, Rotation.NONE, Mirror.NONE, 0)),
                List.of(zone), List.of(route), List.of(spawn));
    }

    /** Every rotation, mirrored or not: markers land on the matching block, and undo takes it all back. */
    static void roundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplate template = level.getServer().getStructureManager().get(SHRINE).orElseThrow();
        BlockPos chest = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST).getFirst().pos();
        Compound compound = sample(chest);
        Identifier id = EmergentStealth.id("test/shrine_guard");
        BlockPos origin = helper.absolutePos(new BlockPos(8, 20, 8));
        UUID author = UUID.randomUUID();
        int lastCopy = 0;

        for (Transform t : all()) {
            CompoundPlacer.Result result = CompoundPlacer.place(level, author, id, compound, origin, t, EXACT);
            PlacedCompounds.Copy copy = result.copy();
            helper.assertTrue(copy != null, "Placed " + t + ", missing " + result.missing());
            helper.assertTrue(copy.id() > lastCopy, t + ": copy numbers are never reused (got #" + copy.id() + " after #" + lastCopy + ")");
            lastCopy = copy.id();
            BlockPos chestAt = t.apply(chest).offset(origin);
            helper.assertTrue(level.getBlockState(chestAt).is(Blocks.CHEST), t + ": the chest should be at " + chestAt);

            String routeName = PlacedCompounds.Copy.scoped(id, copy.id(), "shrine_walk");
            PatrolRoute route = PatrolRoutes.get(level).get(routeName).orElse(null);
            helper.assertTrue(route != null && route.waypoints().getFirst().pos().equals(chestAt.above()),
                    t + ": the route's first waypoint should stand on the chest, is " + route);
            helper.assertTrue(Mth.degreesDifferenceAbs(route.waypoints().getFirst().lookYaw().orElseThrow(), t.yaw(90.0F)) < 0.01F,
                    t + ": the waypoint looks the turned way");

            helper.assertTrue(copy.npcs().size() == 1, t + ": one guard");
            StealthNpc npc = (StealthNpc) level.getEntity(copy.npcs().getFirst());
            helper.assertTrue(npc != null && npc.blockPosition().equals(chestAt.above()), t + ": the guard stands on the chest");
            helper.assertTrue(Mth.degreesDifferenceAbs(npc.getYRot(), t.yaw(90.0F)) < 0.01F, t + ": the guard faces the turned way");
            Schedule.Activity night = npc.getSchedule().activeAt(22).orElseThrow();
            helper.assertTrue(night instanceof Schedule.Post post && post.pos().equals(chestAt.above()), t + ": the night post is on the chest");
            Schedule.Activity day = npc.getSchedule().activeAt(10).orElseThrow();
            helper.assertTrue(day instanceof Schedule.Route r && r.route().equals(routeName), t + ": the day route is this copy's");

            Zone zone = Zones.get(level).get(PlacedCompounds.Copy.scoped(id, copy.id(), "chest")).orElse(null);
            helper.assertTrue(zone != null && zone.contains(chestAt) && zone.contains(chestAt.above()), t + ": the zone holds the chest");

            helper.assertTrue(StructurePlacement.undo(author) != null, t + ": undo");
            helper.assertTrue(level.getBlockState(chestAt).isAir(), t + ": the chest is gone after undo");
            helper.assertTrue(level.getEntity(copy.npcs().getFirst()) == null, t + ": the guard is gone after undo");
            helper.assertTrue(PatrolRoutes.get(level).get(routeName).isEmpty(), t + ": the route is gone after undo");
            helper.assertTrue(Zones.get(level).get(zone.name()).isEmpty(), t + ": the zone is gone after undo");
            helper.assertTrue(PlacedCompounds.get(level).get(copy.id()).isEmpty(), t + ": the copy record is gone after undo");
        }
        helper.succeed();
    }

    /** The example compounds shipped in the mod (made by {@code tools/compounds/make_examples.py}). */
    private static final List<String> EXAMPLES = List.of("samurai_fort", "shrine_watch", "cherry_grove_estate", "takamori_castle");

    /**
     * Each example compound loads, and placed at every rotation, every route waypoint, post and NPC stands on
     * the floor of its structure: a solid block below, and room to stand (doors and gates count, NPCs open them).
     * Placed far from the other tests, then undone.
     */
    static void examples(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID author = UUID.randomUUID();
        for (int i = 0; i < EXAMPLES.size(); i++) {
            Identifier id = EmergentStealth.id("examples/" + EXAMPLES.get(i));
            Compound compound = Compounds.get(id);
            helper.assertTrue(compound != null, id + " should load from the mod's data");
            java.util.Set<String> routeNames = new java.util.HashSet<>();
            compound.routes().forEach(r -> routeNames.add(r.name()));
            for (Compound.Spawn spawn : compound.spawns()) {
                for (Schedule.Entry entry : spawn.schedule().entries()) {
                    helper.assertTrue(!(entry.activity() instanceof Schedule.Route r) || routeNames.contains(r.route()),
                            id + ": a spawn walks an unknown route " + entry.activity());
                }
            }

            BlockPos origin = new BlockPos(20_000 + 300 * i, 120, 20_000);
            for (Rotation rotation : Rotation.values()) {
                Transform t = new Transform(Mirror.NONE, rotation);
                CompoundPlacer.Result result = CompoundPlacer.place(level, author, id, compound, origin, t, EXACT);
                PlacedCompounds.Copy copy = result.copy();
                helper.assertTrue(copy != null, id + " " + t + ": missing templates " + result.missing());
                String where = id + " " + rotation.getSerializedName();
                for (PatrolRoute route : compound.routes()) {
                    for (PatrolRoute.Waypoint waypoint : route.waypoints()) {
                        assertStands(helper, level, t.apply(waypoint.pos()).offset(origin), where + " route " + route.name() + " " + waypoint.pos());
                    }
                }
                int npcs = 0;
                for (Compound.Spawn spawn : compound.spawns()) {
                    npcs += spawn.count();
                    assertStands(helper, level, t.apply(spawn.pos()).offset(origin), where + " spawn " + spawn.archetype() + " " + spawn.pos());
                    for (Schedule.Entry entry : spawn.schedule().entries()) {
                        if (entry.activity() instanceof Schedule.Post post) {
                            assertStands(helper, level, t.apply(post.pos()).offset(origin), where + " post " + post.pos());
                        }
                    }
                }
                helper.assertTrue(copy.npcs().size() == npcs, where + ": " + npcs + " NPCs, got " + copy.npcs().size());
                helper.assertTrue(copy.routes().size() == compound.routes().size() && copy.zones().size() == compound.zones().size(),
                        where + ": every route and zone is registered");
                helper.assertTrue(StructurePlacement.undo(author) != null, where + ": undo");
            }
        }
        helper.succeed();
    }

    /**
     * Placing on a slope (doc 33 §4). A small shrine goes on a hillside rising east, once per terrain mode, and is
     * undone each time: {@code exact} leaves the slope alone; {@code fit} gives the footprint solid ground at the
     * ground line, eases the ring from the ground line to the slope and leaves the slope past the ring alone;
     * {@code replace} flattens the ring to the ground line. Undo restores the hillside every time.
     */
    static void terrain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID author = UUID.randomUUID();
        BlockPos origin = new BlockPos(40_000, 100, 40_000);
        int ground = origin.getY() - 1;
        int blend = 6;
        // The hillside: x from -12 to 24 round the origin, rising 3 in 4 eastwards through the ground line at x = 4.
        java.util.function.IntUnaryOperator slope = x -> ground + Math.round((x - 4) * 0.75F);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -12; x <= 24; x++) {
            for (int z = -10; z <= 20; z++) {
                int h = slope.applyAsInt(x);
                for (int y = ground - 14; y <= ground + 24; y++) {
                    level.setBlock(pos.set(origin.getX() + x, y, origin.getZ() + z), y > h ? Blocks.AIR.defaultBlockState()
                            : y == h ? Blocks.GRASS_BLOCK.defaultBlockState() : y > h - 3 ? Blocks.DIRT.defaultBlockState()
                            : Blocks.STONE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
            }
        }
        java.util.function.IntBinaryOperator surface = (x, z) -> {
            for (int y = ground + 24; y > ground - 14; y--) {
                if (!level.getBlockState(pos.set(origin.getX() + x, y, origin.getZ() + z)).isAir()) {
                    return y;
                }
            }
            return Integer.MIN_VALUE;
        };
        Compound compound = new Compound(List.of(new Compound.Module(SHRINE, new BlockPos(0, -1, 0), Rotation.NONE, Mirror.NONE, 0)),
                List.of(), List.of(), List.of());
        Identifier id = EmergentStealth.id("test/hillside");
        int mid = 4;              // a z through the middle of the shrine
        int east = 9, west = -1;  // the first ring columns either side (the shrine is 9 wide)

        for (TerrainFit.Mode mode : TerrainFit.Mode.values()) {
            CompoundPlacer.Result result = CompoundPlacer.place(level, author, id, compound, origin, Transform.IDENTITY,
                    new Compound.Terrain(mode, blend, 24));
            helper.assertTrue(result.copy() != null, mode + ": placed");
            int beyond = east + blend + 1;
            helper.assertTrue(surface.applyAsInt(beyond, mid) == slope.applyAsInt(beyond), mode + ": the slope past the ring is untouched");
            switch (mode) {
                case EXACT -> {
                    helper.assertTrue(surface.applyAsInt(east, mid) == slope.applyAsInt(east), "exact: the ring is untouched");
                    helper.assertTrue(surface.applyAsInt(west - 3, mid) == slope.applyAsInt(west - 3), "exact: the valley is untouched");
                }
                case FIT -> {
                    for (int x = 0; x < 9; x++) {
                        for (int z = 0; z < 9; z++) {
                            BlockPos at = new BlockPos(origin.getX() + x, ground, origin.getZ() + z);
                            helper.assertTrue(!level.getBlockState(at).isAir(), "fit: solid ground line under the footprint at " + at);
                        }
                    }
                    for (int x : new int[] {east, west}) {
                        int s = surface.applyAsInt(x, mid);
                        helper.assertTrue(Math.abs(s - ground) <= 1, "fit: the ring starts at the ground line (x " + x + ": " + s + ")");
                    }
                    int prev = surface.applyAsInt(east, mid);
                    for (int x = east + 1; x <= east + blend; x++) {
                        int s = surface.applyAsInt(x, mid);
                        helper.assertTrue(s >= prev && s <= slope.applyAsInt(x), "fit: the ring climbs to the hillside (x " + x + ": " + s + ")");
                        prev = s;
                    }
                    int valley = surface.applyAsInt(west - 3, mid);
                    helper.assertTrue(valley < ground && valley > slope.applyAsInt(west - 3), "fit: the valley side is banked up (" + valley + ")");
                }
                case REPLACE -> {
                    for (int x : new int[] {west - blend + 1, west - 2, west, east, east + 2, east + blend - 1}) {
                        helper.assertTrue(surface.applyAsInt(x, mid) == ground, "replace: the ring is flat at the ground line (x " + x + ")");
                    }
                }
            }
            helper.assertTrue(StructurePlacement.undo(author) != null, mode + ": undo");
            for (int x = -10; x <= 22; x += 4) {
                helper.assertTrue(surface.applyAsInt(x, mid) == slope.applyAsInt(x), mode + ": undo restores the hillside at x " + x);
            }
        }
        helper.succeed();
    }

    private static void assertStands(GameTestHelper helper, ServerLevel level, BlockPos pos, String what) {
        boolean floor = !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
        helper.assertTrue(floor && roomAt(level, pos) && roomAt(level, pos.above()),
                what + " at " + pos + " is no place to stand: " + level.getBlockState(pos.below()) + " / " + level.getBlockState(pos)
                        + " / " + level.getBlockState(pos.above()));
    }

    private static boolean roomAt(ServerLevel level, BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        return state.isPathfindable(net.minecraft.world.level.pathfinder.PathComputationType.LAND)
                || state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock;
    }
}
