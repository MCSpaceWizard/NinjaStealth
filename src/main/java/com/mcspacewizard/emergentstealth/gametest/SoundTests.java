package com.mcspacewizard.emergentstealth.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.entity.ThrownItem;
import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.stealth.sound.Footsteps;
import com.mcspacewizard.emergentstealth.stealth.sound.Masking;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseEvent;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseKind;
import com.mcspacewizard.emergentstealth.stealth.sound.NoisePropagation;
import com.mcspacewizard.emergentstealth.stealth.sound.Noises;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Hearing and distractions (design doc 16 §7). Most tests use the pure {@link Noises#hearers} query on NoAI
 * NPCs; the brain's reaction is tested by the behaviour tests. Registers itself, separately from ESGameTests.
 * Arena: 9 x 6 x 26, stone floor at y=0, open air above.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class SoundTests {
    private SoundTests() {}

    private static final Identifier ARENA = EmergentStealth.id("arena");
    private static final int MAX_TICKS = 100;
    private static final Noises.MaskingSource NO_MASKING = npc -> 0.0F;

    private record Spec(Consumer<GameTestHelper> function, boolean rain) {}

    private static final Map<String, Spec> TESTS = new LinkedHashMap<>();

    static {
        TESTS.put("sound/wall_muffles_doorway_carries", new Spec(SoundTests::wallMufflesDoorwayCarries, false));
        TESTS.put("sound/sprint_heard_sneak_not", new Spec(SoundTests::sprintHeardSneakNot, false));
        TESTS.put("sound/rain_masks_footstep", new Spec(SoundTests::rainMasksFootstep, true));
        TESTS.put("sound/thrown_item_heard_behind", new Spec(SoundTests::thrownItemHeardBehind, false));
        TESTS.put("sound/chest_silent_door_heard", new Spec(SoundTests::chestSilentDoorHeard, false));
        TESTS.put("sound/glass_shatters_stone_drops", new Spec(SoundTests::glassShattersStoneDrops, false));
        TESTS.put("sound/budget_defers_flood", new Spec(SoundTests::budgetDefersFlood, false));
        TESTS.put("sound/flood_worst_case", new Spec(SoundTests::floodWorstCase, false));
    }

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        for (Map.Entry<String, Spec> test : TESTS.entrySet()) {
            event.register(Registries.TEST_FUNCTION, EmergentStealth.id(test.getKey()), () -> test.getValue().function());
        }
    }

    @SubscribeEvent
    static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> clear = event.registerEnvironment(EmergentStealth.id("sound"));
        // Its own batch, so the rain can't leak into other tests (batches run one after another).
        Holder<TestEnvironmentDefinition<?>> rain = event.registerEnvironment(EmergentStealth.id("sound_rain"),
                new TestEnvironmentDefinition.Weather(TestEnvironmentDefinition.Weather.Type.RAIN));
        for (Map.Entry<String, Spec> test : TESTS.entrySet()) {
            Identifier id = EmergentStealth.id(test.getKey());
            event.registerTest(id, new FunctionGameTestInstance(
                    ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(test.getValue().rain() ? rain : clear, ARENA, MAX_TICKS, 0, true)));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Helpers

    /** A NoAI guard (statues are listed by the hearers query but never sent noises). */
    static StealthNpc listener(GameTestHelper helper, Vec3 relative, float yaw) {
        StealthNpc npc = helper.spawn(ESEntities.STEALTH_NPC.get(), relative);
        npc.setArchetype(EmergentStealth.id("ashigaru"), true);
        npc.setNoAi(true);
        npc.setYRot(yaw);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        return npc;
    }

    static Noises.Hearer find(List<Noises.Hearer> hearers, StealthNpc npc) {
        for (Noises.Hearer hearer : hearers) {
            if (hearer.npc() == npc) {
                return hearer;
            }
        }
        return null;
    }

    static String describe(List<Noises.Hearer> hearers) {
        StringBuilder text = new StringBuilder("[");
        for (Noises.Hearer hearer : hearers) {
            text.append(String.format(java.util.Locale.ROOT, " i=%.2f c=%.1f", hearer.heard().intensity(), hearer.heard().cost()));
        }
        return text.append(" ]").toString();
    }

    /** Records noises emitted inside this test's area until the test ends. */
    static List<NoiseEvent> capture(GameTestHelper helper, Predicate<NoiseEvent> filter) {
        List<NoiseEvent> captured = new ArrayList<>();
        Vec3 corner = Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO));
        AABB area = new AABB(corner, corner.add(9, 6, 26)).inflate(1.0);
        ServerLevel level = helper.getLevel();
        long end = level.getGameTime() + MAX_TICKS;
        // GameTestHelper has no teardown hook, so the observer removes itself once the test can't be running.
        Noises.Observer observer = new Noises.Observer() {
            @Override
            public void onResolved(ServerLevel l, NoiseEvent noise, List<Noises.Hearer> hearers) {
                if (l.getGameTime() > end) {
                    Noises.removeObserver(this);
                } else if (l == level && area.contains(noise.pos()) && filter.test(noise)) {
                    captured.add(noise);
                }
            }
        };
        Noises.addObserver(observer);
        return captured;
    }

    /** A closed stone box (x 0..8, z 0..13, y 1..4) with a roof, so no sound goes round the outside. */
    static void sealedBox(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 13; z++) {
                helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
                for (int y = 1; y <= 4; y++) {
                    if (x == 0 || x == 8 || z == 0 || z == 13) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // 1. Walls muffle, doorways carry

    static void wallMufflesDoorwayCarries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        sealedBox(helper);
        // A stone wall across the box at z=7.
        for (int x = 1; x <= 7; x++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 7), Blocks.STONE);
            }
        }
        StealthNpc npc = listener(helper, new Vec3(4.5, 1, 10.5), 0.0F);
        NoiseEvent noise = NoiseEvent.of(helper.absoluteVec(new Vec3(4.5, 1.1, 4.5)), 12.0F, NoiseKind.IMPACT);

        List<Noises.Hearer> walled = Noises.hearers(level, noise, NO_MASKING);
        helper.assertTrue(find(walled, npc) == null, "A noise behind a solid stone wall should be muffled " + describe(walled));
        helper.assertTrue(!NoisePropagation.clearLine(level, noise.pos(), npc.getEyePosition()), "Setup: the wall blocks the straight line");

        // An open door at the end of the wall, off the straight line: the sound goes round through it.
        helper.setBlock(new BlockPos(1, 1, 7), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.OPEN, true).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(new BlockPos(1, 2, 7), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.OPEN, true).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        long start = System.nanoTime();
        List<Noises.Hearer> doorway = Noises.hearers(level, noise, NO_MASKING);
        long micros = (System.nanoTime() - start) / 1000;
        Noises.Hearer heard = find(doorway, npc);
        helper.assertTrue(heard != null, "The same noise should be heard through the open doorway " + describe(doorway));
        helper.assertTrue(heard.heard().cost() > noise.pos().distanceTo(npc.getEyePosition()),
                "Going round through the door costs more than the straight line, got " + heard.heard().cost());

        // Perf reference: the same flood a few hundred times (pooled buffers, no budget).
        start = System.nanoTime();
        int runs = 200;
        for (int i = 0; i < runs; i++) {
            Noises.hearers(level, noise, NO_MASKING);
        }
        long perFlood = (System.nanoTime() - start) / 1000 / runs;
        EmergentStealth.LOGGER.info("Sound flood through a doorway: first {} µs, then {} µs per query (intensity {}, cost {})",
                micros, perFlood, heard.heard().intensity(), heard.heard().cost());
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // 2. Sprinting is heard where sneaking isn't

    static void sprintHeardSneakNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StealthNpc npc = listener(helper, new Vec3(4.5, 1, 8.5), 0.0F);
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1, 4.5), 0.0F);
        try {
            player.setShiftKeyDown(true);
            NoiseEvent sneak = Footsteps.footstep(player);
            player.setShiftKeyDown(false);
            NoiseEvent walk = Footsteps.footstep(player);
            player.setSprinting(true);
            NoiseEvent sprint = Footsteps.footstep(player);
            helper.assertTrue(sneak.loudness() == Footsteps.SNEAK && walk.loudness() == Footsteps.WALK && sprint.loudness() == Footsteps.SPRINT,
                    "Footstep loudness sneak/walk/sprint should be 2/6/12 on stone, got " + sneak.loudness() + "/" + walk.loudness() + "/" + sprint.loudness());
            helper.assertTrue(sprint.cause() != null && sprint.cause().equals(player.getUUID()), "Footsteps give the player away");
            helper.assertTrue(find(Noises.hearers(level, sneak, NO_MASKING), npc) == null, "Sneaking 4 blocks away should not be heard");
            List<Noises.Hearer> heard = Noises.hearers(level, sprint, NO_MASKING);
            helper.assertTrue(find(heard, npc) != null, "Sprinting 4 blocks away should be heard, even from behind " + describe(heard));
            helper.assertTrue(Footsteps.surfaceMultiplier(Blocks.GRAVEL.defaultBlockState()) == Footsteps.LOUD_SURFACE
                    && Footsteps.surfaceMultiplier(Blocks.WHITE_WOOL.defaultBlockState()) == Footsteps.QUIET_SURFACE,
                    "Gravel should be a loud surface and wool a quiet one");
            helper.assertTrue(Footsteps.landingLoudness(0.5, false) == 0.0F && Footsteps.landingLoudness(4.0, false) == 10.0F
                    && Footsteps.landingLoudness(4.0, true) == 5.0F && Footsteps.landingLoudness(40.0, false) == Footsteps.MAX_LANDING,
                    "Landing loudness is 4 + 1.5 x fall (max 16), halved when sneaking");
        } finally {
            TestPlayers.remove(player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // 3. Rain masks a footstep that's heard in clear weather

    static void rainMasksFootstep(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StealthNpc npc = listener(helper, new Vec3(4.5, 1, 8.5), 0.0F);
        // A walking footstep about 5 blocks from the ears: heard (barely) in silence.
        NoiseEvent step = new NoiseEvent(helper.absoluteVec(new Vec3(4.5, 1, 3.5)), Footsteps.WALK, NoiseKind.FOOTSTEP, null, null);
        helper.assertTrue(Masking.Sources.NONE.masking() == 0.0F, "No masking in silence");
        helper.assertTrue(Math.abs(new Masking.Sources(true, false, false, false).masking() - 0.25F) < 1e-4, "Rain masks 25%");
        helper.assertTrue(Math.abs(new Masking.Sources(true, true, false, false).masking() - 0.5F) < 1e-4, "Thunder masks 50%");
        helper.assertTrue(Math.abs(new Masking.Sources(true, false, true, true).masking() - (1 - 0.75F * 0.7F * 0.6F)) < 1e-4,
                "Sources combine as 1 - product(1 - m)");
        List<Noises.Hearer> quiet = Noises.hearers(level, step, NO_MASKING);
        helper.assertTrue(find(quiet, npc) != null, "In silence the footstep should be heard " + describe(quiet));
        helper.succeedWhen(() -> {
            helper.assertTrue(level.isRaining(), "Waiting for the rain to start");
            float masking = Masking.at(level, npc.getEyePosition());
            helper.assertTrue(masking >= Masking.RAIN - 1e-4, "Rain should mask at the listener, got " + masking);
            List<Noises.Hearer> rainy = Noises.hearers(level, step, n -> Masking.at(level, n.getEyePosition()));
            helper.assertTrue(find(rainy, npc) == null, "In the rain the same footstep should be masked " + describe(rainy));
        });
    }

    // ------------------------------------------------------------------------------------------------
    // 4. A thrown item landing behind a guard is heard there

    static void thrownItemHeardBehind(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Facing +z; the item lands 5 blocks behind it.
        StealthNpc npc = listener(helper, new Vec3(4.5, 1, 10.5), 0.0F);
        List<NoiseEvent> impacts = capture(helper, noise -> noise.kind() == NoiseKind.IMPACT);
        Vec3 from = helper.absoluteVec(new Vec3(4.5, 3.0, 5.5));
        ThrownItem thrown = new ThrownItem(level, from.x, from.y, from.z, new ItemStack(Items.COBBLESTONE));
        thrown.setDeltaMovement(0.0, -0.3, 0.0);
        level.addFreshEntity(thrown);
        helper.succeedWhen(() -> {
            helper.assertTrue(!impacts.isEmpty(), "Waiting for the impact");
            NoiseEvent impact = impacts.getFirst();
            helper.assertTrue(impact.cause() == null, "An impact points at a spot, not at a player");
            helper.assertTrue(impact.loudness() == ThrownItem.IMPACT_LOUDNESS, "Impact loudness should be 10, got " + impact.loudness());
            List<Noises.Hearer> hearers = Noises.hearers(level, impact, NO_MASKING);
            Noises.Hearer heard = find(hearers, npc);
            helper.assertTrue(heard != null, "The NPC should hear the impact behind it " + describe(hearers));
            helper.assertTrue(heard.heard().intensity() > 0.3F, "A close impact should be clearly heard, got " + heard.heard().intensity());
        });
    }

    // ------------------------------------------------------------------------------------------------
    // 5. A chest is silent; a door is heard

    static void chestSilentDoorHeard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StealthNpc npc = listener(helper, new Vec3(4.5, 1, 12.5), 0.0F);
        BlockPos chestPos = new BlockPos(2, 1, 6);
        BlockPos doorPos = new BlockPos(6, 1, 6);
        helper.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, net.minecraft.core.Direction.SOUTH));
        helper.setBlock(doorPos, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(doorPos.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        List<NoiseEvent> noises = capture(helper, noise -> true);
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1, 4.5), 0.0F);
        try {
            BlockPos absChest = helper.absolutePos(chestPos);
            if (level.getBlockEntity(absChest) instanceof ChestBlockEntity chest) {
                chest.startOpen(player);
                chest.stopOpen(player);
            } else {
                helper.fail("Setup: no chest block entity");
            }
            helper.assertTrue(noises.isEmpty(), "Opening a chest should be silent, got " + noises);

            BlockPos absDoor = helper.absolutePos(doorPos);
            BlockState door = level.getBlockState(absDoor);
            ((DoorBlock) door.getBlock()).setOpen(player, level, door, absDoor, true);
            helper.assertTrue(noises.size() == 1, "Opening a door should make exactly one noise, got " + noises);
            NoiseEvent noise = noises.getFirst();
            helper.assertTrue(noise.kind() == NoiseKind.DOOR && noise.loudness() == 8.0F, "Door noise: kind door, loudness 8, got " + noise);
            helper.assertTrue(player.getUUID().equals(noise.cause()), "A door gives away the player who opened it");
            List<Noises.Hearer> hearers = Noises.hearers(level, noise, NO_MASKING);
            helper.assertTrue(find(hearers, npc) != null, "The NPC should hear the door " + describe(hearers));
        } finally {
            TestPlayers.remove(player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // 6. Glass shatters and leaves nothing; stone drops where it lands

    static void glassShattersStoneDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<NoiseEvent> impacts = capture(helper, noise -> noise.kind() == NoiseKind.IMPACT);
        Vec3 glassFrom = helper.absoluteVec(new Vec3(2.5, 3.0, 8.5));
        Vec3 stoneFrom = helper.absoluteVec(new Vec3(6.5, 3.0, 8.5));
        ThrownItem glass = new ThrownItem(level, glassFrom.x, glassFrom.y, glassFrom.z, new ItemStack(Items.GLASS));
        ThrownItem stone = new ThrownItem(level, stoneFrom.x, stoneFrom.y, stoneFrom.z, new ItemStack(Items.STONE));
        glass.setDeltaMovement(0.0, -0.3, 0.0);
        stone.setDeltaMovement(0.0, -0.3, 0.0);
        level.addFreshEntity(glass);
        level.addFreshEntity(stone);
        helper.succeedWhen(() -> {
            helper.assertTrue(glass.isRemoved() && stone.isRemoved(), "Waiting for both to land");
            List<ItemEntity> nearGlass = level.getEntitiesOfClass(ItemEntity.class, new AABB(glassFrom, glassFrom).inflate(1.5, 4.0, 1.5));
            List<ItemEntity> nearStone = level.getEntitiesOfClass(ItemEntity.class, new AABB(stoneFrom, stoneFrom).inflate(1.5, 4.0, 1.5));
            helper.assertTrue(nearGlass.isEmpty(), "Glass should shatter and leave nothing, found " + nearGlass);
            helper.assertTrue(nearStone.size() == 1 && nearStone.getFirst().getItem().is(Items.STONE),
                    "Stone should drop where it lands, found " + nearStone);
            float glassLoudness = 0.0F;
            for (NoiseEvent impact : impacts) {
                glassLoudness = Math.max(glassLoudness, impact.loudness());
            }
            helper.assertTrue(impacts.size() == 2 && glassLoudness == ThrownItem.GLASS_LOUDNESS,
                    "Two impacts, the glass one louder (14), got " + impacts);
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Budget and performance

    /** Over the node budget, a flood finishes on later ticks and the NPC still hears it (a short delay). */
    static void budgetDefersFlood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        sealedBox(helper);
        for (int x = 2; x <= 7; x++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 7), Blocks.STONE);
            }
        }
        // AI on (emit skips NoAI NPCs), but boxed in so it stays put.
        StealthNpc npc = helper.spawn(ESEntities.STEALTH_NPC.get(), new Vec3(4.5, 1, 10.5));
        npc.setArchetype(EmergentStealth.id("ashigaru"), true);
        List<List<Noises.Hearer>> resolved = new ArrayList<>();
        Vec3 spot = helper.absoluteVec(new Vec3(4.5, 1.1, 4.5));
        long end = level.getGameTime() + MAX_TICKS;
        Noises.addObserver(new Noises.Observer() {
            @Override
            public void onResolved(ServerLevel l, NoiseEvent noise, List<Noises.Hearer> hearers) {
                if (l.getGameTime() > end) {
                    Noises.removeObserver(this);
                } else if (l == level && noise.pos().equals(spot)) {
                    resolved.add(hearers);
                }
            }
        });
        Noises.setNodeBudgetOverride(20);
        long[] emittedAt = new long[1];
        helper.runAfterDelay(2, () -> {
            Noises.emit(level, NoiseEvent.of(spot, 14.0F, NoiseKind.IMPACT));
            emittedAt[0] = level.getGameTime();
            helper.assertTrue(resolved.isEmpty() && Noises.pendingJobs(level) > 0,
                    "With a 20-node budget the flood should be deferred, pending " + Noises.pendingJobs(level));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!resolved.isEmpty(), "Waiting for the deferred flood");
            Noises.setNodeBudgetOverride(-1);
            helper.assertTrue(find(resolved.getFirst(), npc) != null, "The NPC should hear the noise through the gap "
                    + describe(resolved.getFirst()));
            EmergentStealth.LOGGER.info("Sound budget: deferred flood resolved after {} ticks", level.getGameTime() - emittedAt[0]);
        });
    }

    /**
     * Worst case for a sprint footstep: the listener is sealed in a stone shell, so the flood explores everything
     * within the bound without reaching it. Logs nodes and time (a budget reference for doc 16 §2).
     */
    static void floodWorstCase(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StealthNpc npc = listener(helper, new Vec3(4.5, 1, 14.5), 0.0F);
        for (int x = 3; x <= 5; x++) {
            for (int z = 13; z <= 15; z++) {
                for (int y = 1; y <= 3; y++) {
                    if (x != 4 || z != 14 || y == 3) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
        }
        NoiseEvent sprint = new NoiseEvent(helper.absoluteVec(new Vec3(4.5, 1, 8.5)), Footsteps.SPRINT, NoiseKind.FOOTSTEP, null, null);
        List<Noises.Hearer> hearers = Noises.hearers(level, sprint, NO_MASKING);
        int nodes = Noises.lastQueryNodes();
        helper.assertTrue(find(hearers, npc) == null, "A sealed-in NPC 6 blocks away should not hear a sprint " + describe(hearers));
        helper.assertTrue(nodes > 1000, "The flood should have explored the open arena, got " + nodes + " nodes");
        for (int i = 0; i < 50; i++) {
            Noises.hearers(level, sprint, NO_MASKING);
        }
        int runs = 200;
        long start = System.nanoTime();
        for (int i = 0; i < runs; i++) {
            Noises.hearers(level, sprint, NO_MASKING);
        }
        long micros = (System.nanoTime() - start) / 1000 / runs;
        EmergentStealth.LOGGER.info("Sound flood worst case (sprint, unreachable listener): {} nodes, {} µs per query, {} ns per node",
                nodes, micros, nodes == 0 ? 0 : micros * 1000 / nodes);
        helper.succeed();
    }
}
