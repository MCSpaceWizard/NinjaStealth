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
    private static final Map<String, Integer> MAX_TICKS_OVERRIDES = new java.util.HashMap<>();

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
        test("light/wall_casts_shadow", LightTests::wallCastsShadow);
        test("light/glass_lets_light_through", LightTests::glassLetsLightThrough);
        test("light/darkness_is_dark", LightTests::darknessIsDark);
        test("light/snuff_and_relight", LightTests::snuffAndRelight);
        test("light/held_torch_lights_holder", LightTests::heldTorchLightsHolder);
        test("light/darkness_slows_detection", LightTests::darknessSlowsDetection);
        test("nav/walks_through_door_and_closes_it", NavigationTests::walksThroughDoorAndClosesIt, 400);
        test("nav/walks_through_fence_gate", NavigationTests::walksThroughFenceGate, 400);
        test("nav/climbs_ladder", NavigationTests::climbsLadder, 400);
        test("routine/patrols_in_order", NavigationTests::patrolsInOrder, 600);
        test("routine/lamplighter_relights", NavigationTests::lamplighterRelights, 400);
        test("routine/schedule_selection", NavigationTests::scheduleSelection);
        test("brain/melee_hit_starts_combat", PerceptionTests::meleeHitStartsCombat);
        test("brain/civilian_hit_flees", PerceptionTests::civilianHitFlees);
        test("brain/unseen_stays_unaware", PerceptionTests::unseenStaysUnaware);
        test("behaviour/custom_tree_drives_npc", BehaviourTests::customTreeDrivesNpc);
        test("behaviour/noise_curious_then_investigate", BehaviourTests::noiseCuriousThenInvestigate);
        test("behaviour/shout_brings_guard", BehaviourTests::shoutBringsGuard, 400);
        test("behaviour/search_group_splits", BehaviourTests::searchGroupSplits, 200);
        test("behaviour/attack_tokens_limit", BehaviourTests::attackTokensLimit);
        test("behaviour/attacker_closes_in", BehaviourTests::attackerClosesIn, 200);
        test("verbs/rear_takedown_rules", VerbTests::rearTakedownRules, 100);
        test("verbs/knocked_out_gets_woken", VerbTests::knockedOutGetsWoken, 600);
        test("verbs/corpse_raises_alarm", VerbTests::corpseRaisesAlarm, 400);
        test("verbs/hidden_corpse_unnoticed", VerbTests::hiddenCorpseUnnoticed, 150);
        test("verbs/drag_follows", VerbTests::dragFollows);
        test("verbs/crawl_stance", VerbTests::crawlStance);
        test("verbs/air_takedown", VerbTests::airTakedown);
        test("progression/stats_stack", ProgressionTests::statsStack);
        test("progression/unlock_rules", ProgressionTests::unlockRules);
        test("progression/insight_to_points", ProgressionTests::insightToPoints);
        test("progression/still_breath", ProgressionTests::stillBreath);
        test("ui/easing_and_tween", UiTests::easingAndTween);
        test("ui/inertial_scroll", UiTests::inertialScroll);
        test("ui/swipe_pager", UiTests::swipePager);
        test("ui/layout_math", UiTests::layoutMath);
        test("ui/theme_parsing", UiTests::themeParsing);
        test("ui/config_mapping", UiTests::configMapping);
        test("ui/skill_tree_model", UiTests::skillTreeModel);
        test("ui/typewriter", UiTests::typewriter);
        test("ui/paper_noise", UiTests::paperNoise);
        test("ui/dev_dialogue_command", UiTests::devDialogueCommand);
    }

    private static void test(String name, Consumer<GameTestHelper> function, int maxTicks) {
        MAX_TICKS_OVERRIDES.put(name, maxTicks);
        test(name, function);
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
                    new TestData<>(environment, ARENA, MAX_TICKS_OVERRIDES.getOrDefault(name, MAX_TICKS), 0, true)));
        }
    }
}
