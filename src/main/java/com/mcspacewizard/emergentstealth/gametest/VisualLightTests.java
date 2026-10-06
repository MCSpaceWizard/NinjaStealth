package com.mcspacewizard.emergentstealth.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.stealth.light.DynamicLights;
import com.mcspacewizard.emergentstealth.stealth.light.ExposureModel;
import com.mcspacewizard.emergentstealth.stealth.light.LightSourceIndex;
import com.mcspacewizard.emergentstealth.stealth.light.LightTransport;

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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Track E (doc 30): the light baked for rendering must equal the gameplay model. These tests build the
 * per-cell value exactly as the client does ({@link LightTransport#cellLevel} over the same sources) on the
 * server and compare it with {@link ExposureModel}, so a change to either side that breaks "what you see is
 * what guards see" fails here. Registers itself, separately from {@link ESGameTests}.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class VisualLightTests {
    private VisualLightTests() {}

    private static final Identifier ARENA = EmergentStealth.id("arena");
    private static final int MAX_TICKS = 100;
    private static final int DELAY = 10;
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();

    static {
        TESTS.put("visual/shadow_matches_gameplay", VisualLightTests::shadowMatchesGameplay);
        TESTS.put("visual/held_light_matches_gameplay", VisualLightTests::heldLightMatchesGameplay);
    }

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        for (Map.Entry<String, Consumer<GameTestHelper>> test : TESTS.entrySet()) {
            event.register(Registries.TEST_FUNCTION, EmergentStealth.id(test.getKey()), test::getValue);
        }
    }

    @SubscribeEvent
    static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(EmergentStealth.id("visual_lighting"));
        for (String name : TESTS.keySet()) {
            Identifier id = EmergentStealth.id(name);
            event.registerTest(id, new FunctionGameTestInstance(
                    ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(environment, ARENA, MAX_TICKS, 0, true)));
        }
    }

    /** The visual light level of an (absolute) cell, built the way the client builds it. */
    static int visualLevel(ServerLevel level, BlockPos cell) {
        Vec3 point = Vec3.atCenterOf(cell);
        List<LightTransport.Candidate> candidates = new ArrayList<>();
        for (LightSourceIndex.Source source : LightSourceIndex.sourcesNear(level, cell, LightTransport.SOURCE_RADIUS)) {
            LightTransport.offer(candidates, Vec3.atCenterOf(source.pos()), source.emission(), source.pos(), point);
        }
        for (DynamicLights.Light light : DynamicLights.near(level, point)) {
            LightTransport.offer(candidates, light.pos(), light.emission(), BlockPos.containing(light.pos()), point);
        }
        return LightTransport.cellLevel(level, cell, candidates);
    }

    static int gameplayLevel(ServerLevel level, BlockPos cell) {
        // Block term only: sky light is left to vanilla (doc 30 §3), and this keeps the test independent of
        // how fast vanilla's light engine darkens the freshly sealed box.
        return Math.round(15.0F * ExposureModel.blockExposureUncached(level, Vec3.atCenterOf(cell)));
    }

    /** Every air cell of the sealed box: visual level == gameplay level. */
    private static void assertBoxMatches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long nanos = 0;
        int cells = 0;
        for (int x = 1; x <= 7; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 1; z <= 12; z++) {
                    BlockPos cell = helper.absolutePos(new BlockPos(x, y, z));
                    if (!level.getBlockState(cell).isAir()) {
                        continue;
                    }
                    long start = System.nanoTime();
                    int visual = visualLevel(level, cell);
                    nanos += System.nanoTime() - start;
                    cells++;
                    int gameplay = gameplayLevel(level, cell);
                    helper.assertTrue(visual == gameplay,
                            "Visual light " + visual + " != gameplay " + gameplay + " at " + x + "," + y + "," + z);
                }
            }
        }
        // Uncached cost of one baked cell (the client caches per cell); a budget reference for doc 30 §7.
        EmergentStealth.LOGGER.info("Visual light: {} cells, {} µs per cell (uncached)", cells, cells == 0 ? 0 : nanos / 1000 / cells);
    }

    /** A torch behind a wall: the far side renders dark (vanilla leaks light there), matching gameplay. */
    static void shadowMatchesGameplay(GameTestHelper helper) {
        LightTests.sealedBox(helper);
        helper.setBlock(new BlockPos(2, 1, 6), Blocks.TORCH);
        for (int z = 4; z <= 8; z++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(6, 1, 10), Blocks.GLASS);
        // Retried every tick until it holds: the vanilla-leak sanity check waits on vanilla's light engine.
        helper.succeedWhen(() -> {
            ServerLevel level = helper.getLevel();
            BlockPos hidden = helper.absolutePos(new BlockPos(5, 1, 6));
            BlockPos lit = helper.absolutePos(new BlockPos(2, 1, 9));
            int vanillaHidden = level.getBrightness(LightLayer.BLOCK, hidden);
            helper.assertTrue(vanillaHidden >= 3, "Test setup: vanilla light should leak around the wall, got " + vanillaHidden);
            helper.assertTrue(visualLevel(level, hidden) == 0, "Behind the wall should render dark, got " + visualLevel(level, hidden));
            helper.assertTrue(visualLevel(level, lit) >= 8, "In view of the torch should render lit, got " + visualLevel(level, lit));
            assertBoxMatches(helper);
        });
    }

    /** A held torch is a dynamic light with shadows: lit in front, dark behind the wall, same as gameplay. */
    static void heldLightMatchesGameplay(GameTestHelper helper) {
        LightTests.sealedBox(helper);
        for (int z = 4; z <= 8; z++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
            }
        }
        helper.runAfterDelay(DELAY, () -> {
            ServerPlayer player = TestPlayers.spawn(helper, new Vec3(2.5, 1.0, 6.5), 0.0F);
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
                ServerLevel level = helper.getLevel();
                int near = visualLevel(level, helper.absolutePos(new BlockPos(2, 1, 9)));
                int behind = visualLevel(level, helper.absolutePos(new BlockPos(6, 1, 6)));
                helper.assertTrue(near >= 8, "A held torch should light the floor nearby, got " + near);
                helper.assertTrue(behind == 0, "A held torch must not light behind the wall, got " + behind);
                assertBoxMatches(helper);
            } finally {
                TestPlayers.remove(player);
            }
            helper.succeed();
        });
    }
}
