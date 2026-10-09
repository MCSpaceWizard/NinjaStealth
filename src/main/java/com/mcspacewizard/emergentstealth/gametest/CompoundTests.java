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
    private static final Identifier SHRINE = EmergentStealth.id("edo/small_shrine_v1");
    private static final Identifier GUARD = EmergentStealth.id("ashigaru");

    private static final Map<Identifier, Consumer<GameTestHelper>> TESTS = Map.of(
            EmergentStealth.id("compounds/transform_algebra"), CompoundTests::transformAlgebra,
            EmergentStealth.id("compounds/file_format"), CompoundTests::fileFormat,
            EmergentStealth.id("compounds/round_trip"), CompoundTests::roundTrip);

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

    /** Composition, inverse, and facings turning with the blocks, for every mirror and rotation. */
    static void transformAlgebra(GameTestHelper helper) {
        BlockPos[] probes = {new BlockPos(3, 1, -2), new BlockPos(-5, 0, 7)};
        for (Mirror m1 : Mirror.values()) {
            for (Rotation r1 : Rotation.values()) {
                Transform a = new Transform(m1, r1);
                helper.assertTrue(a.then(a.inverse()).isIdentity(), a + " then its inverse");
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
    private static Compound sample(BlockPos chest) {
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

        for (Transform t : all()) {
            CompoundPlacer.Result result = CompoundPlacer.place(level, author, id, compound, origin, t);
            PlacedCompounds.Copy copy = result.copy();
            helper.assertTrue(copy != null, "Placed " + t + ", missing " + result.missing());
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
}
