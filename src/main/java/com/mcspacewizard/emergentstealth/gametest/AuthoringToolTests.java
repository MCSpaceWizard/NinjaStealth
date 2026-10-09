package com.mcspacewizard.emergentstealth.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoute;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoutes;
import com.mcspacewizard.emergentstealth.ai.routine.Schedule;
import com.mcspacewizard.emergentstealth.authoring.Compound;
import com.mcspacewizard.emergentstealth.authoring.CompoundDrafts;
import com.mcspacewizard.emergentstealth.authoring.CompoundPlacer;
import com.mcspacewizard.emergentstealth.authoring.Ledger;
import com.mcspacewizard.emergentstealth.authoring.LedgerPayloads;
import com.mcspacewizard.emergentstealth.authoring.MusterPayloads;
import com.mcspacewizard.emergentstealth.authoring.MusterRoll;
import com.mcspacewizard.emergentstealth.authoring.PlacedCompounds;
import com.mcspacewizard.emergentstealth.authoring.StructurePlacement;
import com.mcspacewizard.emergentstealth.authoring.Transform;
import com.mcspacewizard.emergentstealth.authoring.Zone;
import com.mcspacewizard.emergentstealth.authoring.Zones;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;

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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * The Muster Roll and the Compound Ledger (design doc 32 §3, §4, §6): a muster spawns NPCs with the archetype,
 * behaviour and schedule asked for and refuses bad requests; with a draft open the spawn joins it, brings its
 * route along and produces the same NPC when the compound is placed; the ledger starts a draft from a placed
 * structure, adds and takes out zones (re-copied on save), and discards.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class AuthoringToolTests {
    private AuthoringToolTests() {}

    private static final Identifier ARENA = EmergentStealth.id("arena");
    private static final Identifier SHRINE = EmergentStealth.id("edo/small_shrine_v1");
    private static final Identifier GUARD = EmergentStealth.id("ashigaru");
    private static final Identifier CIVILIAN = EmergentStealth.id("civilian");

    private static final Map<Identifier, Consumer<GameTestHelper>> TESTS = Map.of(
            EmergentStealth.id("authoring/muster_spawns"), AuthoringToolTests::musterSpawns,
            EmergentStealth.id("authoring/muster_joins_draft"), AuthoringToolTests::musterJoinsDraft,
            EmergentStealth.id("authoring/ledger_flow"), AuthoringToolTests::ledgerFlow);

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        TESTS.forEach((id, function) -> event.register(Registries.TEST_FUNCTION, id, () -> function));
    }

    @SubscribeEvent
    static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(EmergentStealth.id("authoring"));
        for (Identifier id : TESTS.keySet()) {
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(environment, ARENA, 40, 0, true)));
        }
    }

    /** A test-unique name (tests of a batch share the level's zones and routes). */
    private static String name(GameTestHelper helper, String what) {
        BlockPos at = helper.absolutePos(BlockPos.ZERO);
        return "test_" + what + "_" + at.getX() + "_" + at.getZ();
    }

    private static PatrolRoute route(String name, BlockPos start) {
        return new PatrolRoute(name, List.of(PatrolRoute.Waypoint.at(start), PatrolRoute.Waypoint.at(start.offset(3, 0, 0))), PatrolRoute.Mode.PINGPONG);
    }

    /** Two guards with a behaviour and a day route / night post; bad requests spawn nothing. */
    static void musterSpawns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID author = UUID.randomUUID();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 1, 5));
        String routeName = name(helper, "muster_route");
        PatrolRoutes.get(level).put(route(routeName, pos));
        try {
            Schedule schedule = new Schedule(List.of(
                    new Schedule.Entry(6, 18, new Schedule.Route(routeName)),
                    new Schedule.Entry(18, 6, new Schedule.Post(pos, 90.0F))));
            MusterRoll.Result result = MusterRoll.muster(level, author,
                    new MusterPayloads.Request(pos, 90.0F, GUARD, Optional.of(CIVILIAN), schedule, 2));
            helper.assertTrue(result.npcs().size() == 2 && !result.recorded(), "Two NPCs, no draft: " + result);
            for (StealthNpc npc : result.npcs()) {
                helper.assertTrue(npc.getArchetypeId().equals(GUARD), "Archetype " + npc.getArchetypeId());
                helper.assertTrue(CIVILIAN.equals(npc.getBehaviourOverride()), "Behaviour " + npc.getBehaviourOverride());
                helper.assertTrue(npc.blockPosition().equals(pos), "Stands on the spot: " + npc.blockPosition());
                helper.assertTrue(npc.getSchedule().activeAt(10).orElseThrow() instanceof Schedule.Route r && r.route().equals(routeName),
                        "Walks the route by day");
                helper.assertTrue(npc.getSchedule().activeAt(22).orElseThrow() instanceof Schedule.Post post && post.pos().equals(pos),
                        "Stands its post at night");
                helper.assertTrue(npc.getHome().equals(pos), "Home is the spot");
            }

            List<MusterPayloads.Request> bad = List.of(
                    new MusterPayloads.Request(pos, 0, EmergentStealth.id("no_such_archetype"), Optional.empty(), Schedule.EMPTY, 1),
                    new MusterPayloads.Request(pos, 0, GUARD, Optional.of(EmergentStealth.id("no_such_tree")), Schedule.EMPTY, 1),
                    new MusterPayloads.Request(pos, 0, GUARD, Optional.empty(), Schedule.EMPTY, MusterRoll.MAX_COUNT + 1),
                    new MusterPayloads.Request(pos, 0, GUARD, Optional.empty(),
                            new Schedule(List.of(new Schedule.Entry(0, 0, new Schedule.Route("no_such_route_here")))), 1),
                    new MusterPayloads.Request(pos, 0, GUARD, Optional.empty(),
                            new Schedule(List.of(new Schedule.Entry(0, 0, new Schedule.Post(pos.offset(200, 0, 0), 0.0F)))), 1));
            for (MusterPayloads.Request request : bad) {
                helper.assertTrue(MusterRoll.muster(level, author, request).npcs().isEmpty(), "Refused: " + request);
            }
            helper.assertTrue(MusterRoll.snapFacing(-30.0F) == 315.0F && MusterRoll.snapFacing(100.0F) == 90.0F, "Facing snaps to 45°");
        } finally {
            PatrolRoutes.get(level).remove(routeName);
        }
        helper.succeed();
    }

    /**
     * With a draft open, a muster becomes a spawn marker in the draft's space, brings its route along, isn't
     * recorded twice, and placing the compound produces a guard with the same archetype, schedule and activity.
     */
    static void musterJoinsDraft(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID author = UUID.randomUUID();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos pos = origin.offset(3, 0, 4);
        String routeName = name(helper, "draft_route");
        PatrolRoutes.get(level).put(route(routeName, pos));
        Identifier id = EmergentStealth.id("test/muster_draft");
        int copyId = -1;
        try {
            CompoundDrafts.start(author, id, level.dimension(), origin, Compound.EMPTY);
            Schedule schedule = new Schedule(List.of(
                    new Schedule.Entry(6, 18, new Schedule.Route(routeName)),
                    new Schedule.Entry(18, 6, new Schedule.Post(pos, 180.0F))));
            MusterRoll.Result result = MusterRoll.muster(level, author, new MusterPayloads.Request(pos, 180.0F, GUARD, Optional.empty(), schedule, 1));
            helper.assertTrue(result.recorded() && result.npcs().size() == 1, "Recorded in the draft: " + result);

            CompoundDrafts.Draft draft = CompoundDrafts.get(author);
            Compound.Spawn spawn = draft.compound().spawns().getFirst();
            BlockPos local = new BlockPos(3, 0, 4);
            helper.assertTrue(spawn.pos().equals(local) && spawn.archetype().equals(GUARD) && spawn.count() == 1, "Spawn marker: " + spawn);
            helper.assertTrue(spawn.schedule().activeAt(22).orElseThrow() instanceof Schedule.Post post && post.pos().equals(local),
                    "The post is in compound space: " + spawn.schedule());
            helper.assertTrue(draft.compound().routes().stream().anyMatch(r -> r.name().equals(routeName)
                    && r.waypoints().getFirst().pos().equals(local)), "The route came along, in compound space");
            helper.assertTrue(draft.linkedRoutes().contains(routeName), "The route is linked");

            draft = CompoundDrafts.addNpc(author, draft, result.npcs().getFirst());
            helper.assertTrue(draft.compound().spawns().size() == 1, "A mustered NPC isn't recorded twice");

            BlockPos elsewhere = origin.offset(12, 0, 12);
            Transform turn = new Transform(Mirror.NONE, Rotation.CLOCKWISE_90);
            CompoundPlacer.Result placed = CompoundPlacer.place(level, author, id, draft.compound(), elsewhere, turn,
                    draft.compound().terrain().withMode(com.mcspacewizard.emergentstealth.authoring.TerrainFit.Mode.EXACT));
            helper.assertTrue(placed.copy() != null && placed.copy().npcs().size() == 1, "Placed with one guard: " + placed);
            copyId = placed.copy().id();
            StealthNpc npc = (StealthNpc) level.getEntity(placed.copy().npcs().getFirst());
            BlockPos expected = turn.apply(local).offset(elsewhere);
            helper.assertTrue(npc != null && npc.getArchetypeId().equals(GUARD) && npc.blockPosition().equals(expected), "The placed guard stands at " + expected);
            String scoped = PlacedCompounds.Copy.scoped(id, copyId, routeName);
            helper.assertTrue(npc.getSchedule().activeAt(10).orElseThrow() instanceof Schedule.Route r && r.route().equals(scoped),
                    "By day it walks the copy's route: " + npc.getSchedule());
            helper.assertTrue(npc.getSchedule().activeAt(22).orElseThrow() instanceof Schedule.Post post && post.pos().equals(expected),
                    "At night it stands its moved post");
        } finally {
            CompoundDrafts.close(author);
            PatrolRoutes.get(level).remove(routeName);
            if (copyId > 0) {
                CompoundPlacer.remove(level, copyId);
            }
        }
        helper.succeed();
    }

    /** Start from a placed structure, add a zone and see its later edits on save, take markers out, discard. */
    static void ledgerFlow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID author = UUID.randomUUID();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 20, 8));
        String zoneName = name(helper, "ledger_zone");
        try {
            BoundingBox placed = StructurePlacement.place(level, author, SHRINE, origin, Rotation.NONE, Mirror.NONE);
            helper.assertTrue(placed != null, "The shrine was placed");
            BlockPos inside = new BlockPos(placed.minX() + 1, placed.minY() + 1, placed.minZ() + 1);

            Ledger.apply(level, author, new LedgerPayloads.Action(LedgerPayloads.Kind.START, "Bad Name!", 0, inside));
            helper.assertTrue(CompoundDrafts.get(author) == null, "A bad name starts nothing");
            Ledger.apply(level, author, new LedgerPayloads.Action(LedgerPayloads.Kind.START, "test/ledger_shrine", 0, inside));
            CompoundDrafts.Draft draft = CompoundDrafts.get(author);
            helper.assertTrue(draft != null && draft.id().equals(EmergentStealth.id("test/ledger_shrine")), "Started: " + draft);
            helper.assertTrue(draft.origin().equals(origin) && draft.compound().structures().size() == 1
                    && draft.compound().structures().getFirst().template().equals(SHRINE), "Started from the shrine: " + draft.compound());

            BoundingBox first = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 2, origin.getY() + 2, origin.getZ() + 2);
            Zones.get(level).put(new Zone(zoneName, Zone.Access.RESTRICTED, List.of(first), Optional.empty()));
            Ledger.apply(level, author, new LedgerPayloads.Action(LedgerPayloads.Kind.TOGGLE_ZONE, zoneName, 0, inside));
            draft = CompoundDrafts.get(author);
            helper.assertTrue(draft.compound().zones().size() == 1 && draft.linkedZones().contains(zoneName), "The zone is in and linked");
            helper.assertTrue(draft.compound().zones().getFirst().contains(BlockPos.ZERO), "The zone is in compound space");

            // Edit the world zone after adding it: saving copies it again.
            Zones.get(level).put(Zones.get(level).get(zoneName).orElseThrow().withBox(first.moved(0, 0, 5)));
            draft = CompoundDrafts.refreshLinks(author, draft, level);
            helper.assertTrue(draft.compound().zones().getFirst().boxes().size() == 2, "Refresh picks up the new box");

            CompoundDrafts.addSpawn(author, draft, new Compound.Spawn(GUARD, BlockPos.ZERO, 0.0F, Optional.empty(), Schedule.EMPTY, 1), List.of());
            Ledger.apply(level, author, new LedgerPayloads.Action(LedgerPayloads.Kind.REMOVE_SPAWN, "", 0, inside));
            Ledger.apply(level, author, new LedgerPayloads.Action(LedgerPayloads.Kind.TOGGLE_ZONE, zoneName, 0, inside));
            draft = CompoundDrafts.get(author);
            helper.assertTrue(draft.compound().spawns().isEmpty() && draft.compound().zones().isEmpty() && draft.linkedZones().isEmpty(),
                    "Spawn and zone taken out: " + draft);

            Ledger.apply(level, author, new LedgerPayloads.Action(LedgerPayloads.Kind.DISCARD, "", 0, inside));
            helper.assertTrue(CompoundDrafts.get(author) == null, "Discarded");
        } finally {
            CompoundDrafts.close(author);
            Zones.get(level).remove(zoneName);
            StructurePlacement.undo(author);
        }
        helper.succeed();
    }
}
