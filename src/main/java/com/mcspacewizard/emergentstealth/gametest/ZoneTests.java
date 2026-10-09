package com.mcspacewizard.emergentstealth.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.perception.TargetAwareness;
import com.mcspacewizard.emergentstealth.authoring.Trespass;
import com.mcspacewizard.emergentstealth.authoring.Zone;
import com.mcspacewizard.emergentstealth.authoring.ZonePayloads;
import com.mcspacewizard.emergentstealth.authoring.ZoneTool;
import com.mcspacewizard.emergentstealth.authoring.Zones;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.item.SurveyorsRopeItem;
import com.mcspacewizard.emergentstealth.registry.ESDataComponents;
import com.mcspacewizard.emergentstealth.registry.ESItems;
import com.mcspacewizard.emergentstealth.stealth.LightSampler;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Zones and the minimal trespass rule (design doc 32 §2, §6): restricted doubles awareness gain, hostile turns
 * notice into detection, time windows follow the clock, and the Surveyor's Rope and its panel edit zones.
 * The perception tests reuse {@link PerceptionTests}' arena and NoAI guard, driven by hand.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class ZoneTests {
    private ZoneTests() {}

    private static final Identifier ARENA = EmergentStealth.id("arena");
    private static final LightSampler FULL_LIGHT = (level, point) -> 1.0F;
    /** Where the intruder stands: in front of the guard, 10 blocks away. */
    private static final Vec3 INTRUDER = new Vec3(4.5, 1.0, 18.5);
    private static final int UPDATES = 40;

    private static final Map<Identifier, Consumer<GameTestHelper>> TESTS = Map.of(
            EmergentStealth.id("zones/restricted_doubles_gain"), ZoneTests::restrictedDoublesGain,
            EmergentStealth.id("zones/hostile_is_detection"), ZoneTests::hostileIsDetection,
            EmergentStealth.id("zones/time_window"), ZoneTests::timeWindow,
            EmergentStealth.id("zones/rope_and_panel"), ZoneTests::ropeAndPanel);

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        TESTS.forEach((id, function) -> event.register(Registries.TEST_FUNCTION, id, () -> function));
    }

    @SubscribeEvent
    static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(EmergentStealth.id("zones"));
        for (Identifier id : TESTS.keySet()) {
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(environment, ARENA, 40, 0, true)));
        }
    }

    /** A test-unique zone name (tests of a batch share the level's zones). */
    private static String name(GameTestHelper helper, String what) {
        BlockPos at = helper.absolutePos(BlockPos.ZERO);
        return "test_" + what + "_" + at.getX() + "_" + at.getZ();
    }

    /** A zone box round the intruder's spot, in world coordinates. */
    private static BoundingBox aroundIntruder(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(BlockPos.containing(INTRUDER));
        return new BoundingBox(at.getX() - 1, at.getY() - 1, at.getZ() - 1, at.getX() + 1, at.getY() + 2, at.getZ() + 1);
    }

    /** Awareness of {@code player} after each of {@link #UPDATES} perception updates, 2 ticks apart, by a fresh guard. */
    private static List<Float> watch(GameTestHelper helper, ServerPlayer player) {
        StealthNpc npc = PerceptionTests.npc(helper, "ashigaru");
        long now = helper.getLevel().getGameTime();
        List<Float> trace = new ArrayList<>();
        for (int i = 0; i < UPDATES; i++) {
            npc.perception().update(helper.getLevel(), now + i * 2L, 1, FULL_LIGHT);
            TargetAwareness awareness = npc.perception().get(player.getUUID());
            trace.add(awareness == null ? 0.0F : awareness.awareness());
        }
        npc.discard();
        return trace;
    }

    static void restrictedDoublesGain(GameTestHelper helper) {
        Zones zones = Zones.get(helper.getLevel());
        String name = name(helper, "restricted");
        PerceptionTests.withPlayer(helper, INTRUDER, player -> {
            try {
                List<Float> open = watch(helper, player);
                zones.put(new Zone(name, Zone.Access.RESTRICTED, List.of(aroundIntruder(helper)), Optional.empty()));
                helper.assertTrue(Trespass.accessAt(helper.getLevel(), player.blockPosition()) == Zone.Access.RESTRICTED,
                        "The intruder stands in the restricted zone");
                List<Float> restricted = watch(helper, player);
                // Compare early, while neither has reached full detection.
                int i = 3;
                helper.assertTrue(open.get(i) > 0.0F && restricted.get(i) < 1.0F, "Awareness is rising but not full: " + open + " / " + restricted);
                float ratio = restricted.get(i) / open.get(i);
                helper.assertTrue(ratio > 1.9F && ratio < 2.1F, "Restricted ground doubles awareness gain, got x" + ratio
                        + " (" + open.get(i) + " -> " + restricted.get(i) + ")");
            } finally {
                zones.remove(name);
            }
        });
    }

    static void hostileIsDetection(GameTestHelper helper) {
        Zones zones = Zones.get(helper.getLevel());
        String name = name(helper, "hostile");
        PerceptionTests.withPlayer(helper, INTRUDER, player -> {
            try {
                List<Float> open = watch(helper, player);
                zones.put(new Zone(name, Zone.Access.HOSTILE, List.of(aroundIntruder(helper)), Optional.empty()));
                List<Float> hostile = watch(helper, player);
                int detected = hostile.indexOf(1.0F);
                helper.assertTrue(detected >= 0, "Seen on hostile ground means detection: " + hostile);
                helper.assertTrue(open.get(detected) < 1.0F, "Without the zone the guard would still be unsure at that point: " + open);
                float noticed = com.mcspacewizard.emergentstealth.config.ESConfig.NOTICED_THRESHOLD.get().floatValue();
                helper.assertTrue(detected == 0 || hostile.get(detected - 1) < noticed,
                        "Detection comes as soon as the guard notices: " + hostile);
            } finally {
                zones.remove(name);
            }
        });
    }

    /** Hours wrap past midnight, outside them the zone is public, and the strictest zone wins. */
    static void timeWindow(GameTestHelper helper) {
        BlockPos pos = new BlockPos(5, 64, 5);
        Zone night = new Zone("night", Zone.Access.HOSTILE, List.of(new BoundingBox(0, 60, 0, 10, 70, 10)), Optional.of(new Zone.Hours(20, 4)));
        Zone yard = new Zone("yard", Zone.Access.RESTRICTED, List.of(new BoundingBox(0, 60, 0, 20, 70, 20)), Optional.empty());
        Zone day = new Zone("day", Zone.Access.RESTRICTED, List.of(new BoundingBox(0, 60, 0, 10, 70, 10)), Optional.of(new Zone.Hours(6, 18)));
        helper.assertTrue(Trespass.accessAt(List.of(night), pos, 22) == Zone.Access.HOSTILE, "22:00 is inside 20-4");
        helper.assertTrue(Trespass.accessAt(List.of(night), pos, 2) == Zone.Access.HOSTILE, "02:00 is inside 20-4 (wraps)");
        helper.assertTrue(Trespass.accessAt(List.of(night), pos, 4) == Zone.Access.PUBLIC, "04:00 is past the window's end");
        helper.assertTrue(Trespass.accessAt(List.of(night), pos, 12) == Zone.Access.PUBLIC, "Outside the hours the zone is public");
        helper.assertTrue(Trespass.accessAt(List.of(day), pos, 12) == Zone.Access.RESTRICTED && Trespass.accessAt(List.of(day), pos, 19) == Zone.Access.PUBLIC,
                "A day zone holds by day only");
        helper.assertTrue(Trespass.accessAt(List.of(yard, night), pos, 23) == Zone.Access.HOSTILE, "The strictest rule wins");
        helper.assertTrue(Trespass.accessAt(List.of(yard, night), pos, 12) == Zone.Access.RESTRICTED, "At noon only the yard's rule holds");
        helper.assertTrue(Trespass.accessAt(List.of(yard, night), new BlockPos(15, 64, 15), 23) == Zone.Access.RESTRICTED,
                "Outside the night zone's box");
        helper.assertTrue(Trespass.accessAt(List.of(yard), new BlockPos(25, 64, 25), 23) == Zone.Access.PUBLIC, "Outside every zone");
        helper.succeed();
    }

    /** Two rope clicks make a zone, a sneaking pair extends it, and the panel's edits are checked and applied. */
    static void ropeAndPanel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zones zones = Zones.get(level);
        ItemStack rope = new ItemStack(ESItems.SURVEYORS_ROPE.get());
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        String renamed = name(helper, "rope");
        String created = null;
        try {
            SurveyorsRopeItem.addBox(level, rope, BoundingBox.fromCorners(o.offset(1, 1, 1), o.offset(3, 2, 4)), false);
            created = rope.get(ESDataComponents.ROPE_ZONE.get());
            Zone zone = created == null ? null : zones.get(created).orElse(null);
            helper.assertTrue(zone != null && zone.access() == Zone.Access.RESTRICTED && zone.boxes().size() == 1,
                    "Two corners make a new restricted zone the rope then edits: " + zone);
            SurveyorsRopeItem.addBox(level, rope, BoundingBox.fromCorners(o.offset(5, 1, 1), o.offset(6, 2, 2)), true);
            helper.assertTrue(zones.get(created).orElseThrow().boxes().size() == 2 && created.equals(rope.get(ESDataComponents.ROPE_ZONE.get())),
                    "Sneaking on the second corner adds a box to the rope's zone");
            helper.assertTrue(zones.get(created).orElseThrow().contains(o.offset(6, 2, 2)) && !zones.get(created).orElseThrow().contains(o.offset(4, 1, 1)),
                    "The zone holds both boxes and nothing between them");

            ZoneTool.apply(level, new ZonePayloads.Edit(created, ZonePayloads.Action.SAVE, "Bad Name!", Zone.Access.HOSTILE, Optional.empty()));
            helper.assertTrue(zones.get(created).orElseThrow().access() == Zone.Access.RESTRICTED, "A bad name refuses the whole edit");

            ZoneTool.apply(level, new ZonePayloads.Edit(created, ZonePayloads.Action.SAVE, renamed, Zone.Access.HOSTILE,
                    Optional.of(new Zone.Hours(18, 6))));
            Zone saved = zones.get(renamed).orElse(null);
            helper.assertTrue(zones.get(created).isEmpty() && saved != null && saved.access() == Zone.Access.HOSTILE
                    && saved.hours().equals(Optional.of(new Zone.Hours(18, 6))) && saved.boxes().size() == 2,
                    "Save renames and sets the rule and hours, keeping the boxes: " + saved);

            ZoneTool.apply(level, new ZonePayloads.Edit(renamed, ZonePayloads.Action.REMOVE_LAST_BOX, renamed, Zone.Access.HOSTILE, Optional.empty()));
            helper.assertTrue(zones.get(renamed).orElseThrow().boxes().size() == 1, "Undo box removes the newest box");
            ZoneTool.apply(level, new ZonePayloads.Edit(renamed, ZonePayloads.Action.REMOVE_LAST_BOX, renamed, Zone.Access.HOSTILE, Optional.empty()));
            helper.assertTrue(zones.get(renamed).orElseThrow().boxes().size() == 1, "The only box stays");

            ZoneTool.apply(level, new ZonePayloads.Edit(renamed, ZonePayloads.Action.DELETE, renamed, Zone.Access.HOSTILE, Optional.empty()));
            helper.assertTrue(zones.get(renamed).isEmpty(), "Delete removes the zone");
        } finally {
            if (created != null) {
                zones.remove(created);
            }
            zones.remove(renamed);
        }
        helper.succeed();
    }
}
