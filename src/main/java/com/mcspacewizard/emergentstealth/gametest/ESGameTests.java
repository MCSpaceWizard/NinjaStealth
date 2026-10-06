package com.mcspacewizard.emergentstealth.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the mod's GameTests. Run them all headlessly with {@code ./gradlew runGameTestServer}, or in a
 * dev client with {@code /test runall emergentstealth}.
 */
public final class ESGameTests {
    private ESGameTests() {}

    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, EmergentStealth.MODID);

    private static final Identifier ARENA = EmergentStealth.id("arena");
    private static final int MAX_TICKS = 100;
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();

    static {
        test("data/profiles_loaded", PerceptionTests::profilesLoaded);
        test("perception/front_visible", PerceptionTests::frontVisible);
        test("perception/behind_invisible", PerceptionTests::behindInvisible);
        test("perception/stone_wall_blocks", PerceptionTests::stoneWallBlocks);
        test("perception/glass_wall_clear", PerceptionTests::glassWallClear);
        test("perception/leaves_partial", PerceptionTests::leavesPartial);
        test("perception/crawl_in_grass_hidden", PerceptionTests::crawlInGrassHidden);
        test("perception/peripheral_weaker", PerceptionTests::peripheralWeaker);
        test("perception/deep_water_blocks", PerceptionTests::deepWaterBlocks);
        test("perception/grace_period", PerceptionTests::gracePeriod);
        test("perception/decay_delay", PerceptionTests::decayDelay);
        test("brain/melee_hit_starts_combat", PerceptionTests::meleeHitStartsCombat);
        test("brain/civilian_hit_flees", PerceptionTests::civilianHitFlees);
        test("brain/unseen_stays_unaware", PerceptionTests::unseenStaysUnaware);
    }

    private static void test(String name, Consumer<GameTestHelper> function) {
        TESTS.put(name, function);
        TEST_FUNCTIONS.register(name, () -> function);
    }

    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(EmergentStealth.id("default"));
        for (String name : TESTS.keySet()) {
            Identifier id = EmergentStealth.id(name);
            event.registerTest(id, new FunctionGameTestInstance(
                    ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(environment, ARENA, MAX_TICKS, 0, true)));
        }
    }
}
