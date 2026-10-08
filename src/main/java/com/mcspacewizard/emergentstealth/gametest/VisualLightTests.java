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
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.levelgen.Heightmap;
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
        TESTS.put("visual/sun_shadow_matches_gameplay", VisualLightTests::sunShadowMatchesGameplay);
        TESTS.put("visual/moon_shadow_matches_gameplay", VisualLightTests::moonShadowMatchesGameplay);
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
        return LightTransport.levelFor(ExposureModel.blockExposureUncached(level, Vec3.atCenterOf(cell)));
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

    // ------------------------------------------------------------------------------------------------
    // Sun & moon (doc 30 §10). Fixed sky states: the world's time is never changed (other tests share it), and
    // vanilla sky-light propagation is never read, so these don't depend on light-engine timing.

    /** Sun 30° over the western horizon (toward -X) / eastern horizon (+X), overhead; full-moon night. */
    private static final LightTransport.SkyState SUN_WEST = LightTransport.SkyState.at(15.0F, 60.0F, 240.0F, MoonPhase.FULL_MOON);
    private static final LightTransport.SkyState SUN_EAST = LightTransport.SkyState.at(15.0F, -60.0F, 120.0F, MoonPhase.FULL_MOON);
    private static final LightTransport.SkyState NOON = LightTransport.SkyState.at(15.0F, 0.0F, 180.0F, MoonPhase.FULL_MOON);
    private static final LightTransport.SkyState MOON_WEST = LightTransport.SkyState.at(4.0F, 240.0F, 60.0F, MoonPhase.FULL_MOON);
    private static final LightTransport.SkyState NEW_MOON_WEST = LightTransport.SkyState.at(4.0F, 240.0F, 60.0F, MoonPhase.NEW_MOON);

    /** A 4-high north-south wall at x=4 (z 2-10) on the arena floor. */
    private static void wall(GameTestHelper helper) {
        for (int z = 2; z <= 10; z++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
            }
        }
    }

    /**
     * The heightmap ceiling the client uses to cut rays short, built per column (never higher than the client's
     * per-chunk maximum, so if trimming is exact here it is exact there too).
     */
    static int ceiling(ServerLevel level, Vec3 point, LightTransport.SkyState sky) {
        int ceiling = Integer.MIN_VALUE;
        for (Vec3 direction : List.of(sky.sun(), sky.moon())) {
            boolean up = direction == sky.sun() ? sky.sunUp() : sky.moonUp();
            if (!up) {
                continue;
            }
            for (double t = 0.0; t <= LightTransport.SKY_RAY_LENGTH + 1.0; t += 0.25) {
                Vec3 p = point.add(direction.scale(Math.min(t, LightTransport.SKY_RAY_LENGTH)));
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int x = (int) Math.floor(p.x) + dx;
                        int z = (int) Math.floor(p.z) + dz;
                        ceiling = Math.max(ceiling, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
                    }
                }
            }
        }
        return ceiling == Integer.MIN_VALUE ? level.getMaxY() : ceiling;
    }

    /** The baked shade factor at an absolute cell, the way the client computes it (heightmap-trimmed rays). */
    static float visualSkyFactor(ServerLevel level, BlockPos cell, LightTransport.SkyState sky) {
        return LightTransport.cellSkyFactor(level, cell, sky, ceiling(level, Vec3.atCenterOf(cell), sky));
    }

    /** Gameplay's share of the sky's direct light at a cell (full 64-block rays, as ExposureModel traces them). */
    static float gameplaySkyFactor(ServerLevel level, BlockPos cell, LightTransport.SkyState sky) {
        float max = sky.directMax();
        return max <= 0.0F ? 1.0F : ExposureModel.skyDirect(level, Vec3.atCenterOf(cell), sky) / max;
    }

    /** Rendered sky brightness relative to vanilla's full sky, from the baked sky-light level. */
    static float renderedRatio(int bakedLevel) {
        float v = bakedLevel / 15.0F;
        return v / (4.0F - 3.0F * v);
    }

    /** Every air cell around the wall: baked factor == gameplay factor, and the baked level renders at that ratio. */
    private static void assertSkyMatches(GameTestHelper helper, LightTransport.SkyState sky, String label) {
        ServerLevel level = helper.getLevel();
        long nanos = 0;
        int cells = 0;
        for (int x = 1; x <= 7; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 1; z <= 11; z++) {
                    BlockPos cell = helper.absolutePos(new BlockPos(x, y, z));
                    if (!level.getBlockState(cell).isAir()) {
                        continue;
                    }
                    int ceiling = ceiling(level, Vec3.atCenterOf(cell), sky);
                    long start = System.nanoTime();
                    float visual = LightTransport.cellSkyFactor(level, cell, sky, ceiling);
                    nanos += System.nanoTime() - start;
                    cells++;
                    float gameplay = gameplaySkyFactor(level, cell, sky);
                    helper.assertTrue(Math.abs(visual - gameplay) < 1.0E-5F,
                            label + ": baked sky factor " + visual + " != gameplay " + gameplay + " at " + x + "," + y + "," + z);
                    // One sky-light level step is the rendering resolution: the rendered ratio is within it.
                    int baked = LightTransport.skyLevelForFactor(15, visual);
                    float lo = renderedRatio(Math.max(0, baked - 1));
                    float hi = renderedRatio(Math.min(15, baked + 1));
                    helper.assertTrue(gameplay >= lo - 1.0E-4F && gameplay <= hi + 1.0E-4F,
                            label + ": baked level " + baked + " doesn't render at gameplay ratio " + gameplay);
                }
            }
        }
        // Ray cost only; the client's per-chunk ceiling lookup is a few map reads on top.
        EmergentStealth.LOGGER.info("Visual sky ({}): {} cells, {} µs per cell (uncached)", label, cells, cells == 0 ? 0 : nanos / 1000 / cells);
    }

    /** Low sun casts the wall's shadow sideways: west sun shades the east side and vice versa; noon shades neither. */
    static void sunShadowMatchesGameplay(GameTestHelper helper) {
        wall(helper);
        ServerLevel level = helper.getLevel();
        BlockPos east = helper.absolutePos(new BlockPos(5, 1, 6));
        BlockPos west = helper.absolutePos(new BlockPos(3, 1, 6));
        float shade = LightTransport.SHADE_FRACTION;

        helper.assertTrue(Math.abs(visualSkyFactor(level, east, SUN_WEST) - shade) < 1.0E-5F,
                "Evening-style west sun: east of the wall should be in shade, got " + visualSkyFactor(level, east, SUN_WEST));
        helper.assertTrue(Math.abs(visualSkyFactor(level, west, SUN_EAST) - shade) < 1.0E-5F,
                "Morning-style east sun: west of the wall should be in shade, got " + visualSkyFactor(level, west, SUN_EAST));
        // Straight up: only blocks above matter, so this is safe from neighbouring tests.
        for (BlockPos cell : List.of(east, west)) {
            if (level.getHeight(Heightmap.Types.WORLD_SURFACE, cell.getX(), cell.getZ()) < cell.getY()) {
                helper.assertTrue(visualSkyFactor(level, cell, NOON) == 1.0F, "Noon: open ground beside the wall should be in full sun");
            }
        }
        helper.assertTrue(LightTransport.skyLevelForFactor(15, 1.0F) == 15, "Full sun keeps vanilla sky light");
        helper.assertTrue(LightTransport.skyLevelForFactor(15, shade) < 15, "Shade must render darker than full sun");

        assertSkyMatches(helper, SUN_WEST, "west sun");
        assertSkyMatches(helper, SUN_EAST, "east sun");
        assertSkyMatches(helper, NOON, "noon");
        helper.succeed();
    }

    /** At night a full moon casts shadows (moonlit vs moon-shadowed); a new moon casts none. */
    static void moonShadowMatchesGameplay(GameTestHelper helper) {
        wall(helper);
        ServerLevel level = helper.getLevel();
        BlockPos east = helper.absolutePos(new BlockPos(5, 1, 6));
        float moonShadow = LightTransport.NIGHT_BASE / (LightTransport.NIGHT_BASE + LightTransport.MOONLIGHT);
        float shaded = visualSkyFactor(level, east, MOON_WEST);
        helper.assertTrue(Math.abs(shaded - moonShadow) < 1.0E-5F, "Full moon in the west: east of the wall should be in moon shadow, got " + shaded);
        helper.assertTrue(visualSkyFactor(level, east, NEW_MOON_WEST) == 1.0F, "A new moon casts no shadow");
        assertSkyMatches(helper, MOON_WEST, "full moon");
        assertSkyMatches(helper, NEW_MOON_WEST, "new moon");
        helper.succeed();
    }
}
