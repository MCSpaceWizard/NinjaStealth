package com.mcspacewizard.emergentstealth.gametest;

import java.util.List;

import com.google.gson.JsonParser;
import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.progression.SkillPath;
import com.mcspacewizard.emergentstealth.progression.Skills;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.ui.ConfigMapping;
import com.mcspacewizard.emergentstealth.ui.Easing;
import com.mcspacewizard.emergentstealth.ui.InertialScroll;
import com.mcspacewizard.emergentstealth.ui.PaperNoise;
import com.mcspacewizard.emergentstealth.ui.SkillTreeModel;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.SwipePager;
import com.mcspacewizard.emergentstealth.ui.Tween;
import com.mcspacewizard.emergentstealth.ui.Typewriter;
import com.mcspacewizard.emergentstealth.ui.UiLayout;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Pure-logic checks for the Sumi UI framework (design doc 31 §5). Side-neutral code only. */
final class UiTests {
    private UiTests() {}

    private static boolean near(double a, double b, double epsilon) {
        return Math.abs(a - b) <= epsilon;
    }

    /** Easing curves hit their ends; tweens retarget smoothly and snap with no duration. */
    static void easingAndTween(GameTestHelper helper) {
        for (Easing easing : Easing.values()) {
            helper.assertTrue(easing.apply(0.0F) == 0.0F && easing.apply(1.0F) == 1.0F, easing + " must map 0->0 and 1->1");
        }
        helper.assertTrue(Easing.BACK_OUT.apply(0.7F) > 1.0F, "Back-out overshoots");
        Tween tween = new Tween(0.0F);
        tween.animate(100.0F, 1000L, 200, Easing.LINEAR);
        helper.assertTrue(near(tween.value(1100L), 50.0, 0.01), "Halfway is 50, got " + tween.value(1100L));
        tween.animate(0.0F, 1100L, 200, Easing.LINEAR);
        helper.assertTrue(near(tween.value(1100L), 50.0, 0.01), "Retargeting starts from the current value");
        helper.assertTrue(near(tween.value(1300L), 0.0, 0.01) && tween.done(1300L), "Reaches the new target");
        tween.animate(42.0F, 2000L, 0, Easing.CUBIC_OUT);
        helper.assertTrue(tween.value(2000L) == 42.0F, "Zero duration (reduced motion) snaps");
        helper.succeed();
    }

    /** Wheel scrolling glides to its target; a fling coasts and stops inside the range; overdrag springs back. */
    static void inertialScroll(GameTestHelper helper) {
        InertialScroll scroll = new InertialScroll();
        scroll.setMax(300.0F);
        scroll.scrollBy(100.0F);
        scroll.update(0.016F);
        helper.assertTrue(scroll.position() > 0.0F && scroll.position() < 100.0F, "Glides, doesn't jump");
        for (int i = 0; i < 120; i++) {
            scroll.update(0.016F);
        }
        helper.assertTrue(near(scroll.position(), 100.0, 0.06), "Arrives at the wheel target, got " + scroll.position());

        long t = 0L;
        scroll.beginDrag(t);
        for (int i = 0; i < 5; i++) {
            t += 16L;
            scroll.dragBy(12.0F, t);
        }
        float released = scroll.position();
        scroll.endDrag(t);
        for (int i = 0; i < 300; i++) {
            scroll.update(0.016F);
        }
        helper.assertTrue(scroll.position() > released + 5.0F, "A fling keeps moving after release");
        helper.assertTrue(scroll.position() <= 300.0F + 1.0E-3F && scroll.isSettled(), "Comes to rest inside the range, got " + scroll.position());

        scroll.scrollTo(0.0F, true);
        scroll.beginDrag(t);
        t += 500L;
        scroll.dragBy(-40.0F, t);
        helper.assertTrue(scroll.position() < 0.0F && scroll.position() > -40.0F, "Overdrag moves with resistance");
        t += 500L;
        scroll.endDrag(t);
        for (int i = 0; i < 120; i++) {
            scroll.update(0.016F);
        }
        helper.assertTrue(near(scroll.position(), 0.0, 0.06), "Springs back to the start, got " + scroll.position());

        InertialScroll instant = new InertialScroll();
        instant.setInstant(true);
        instant.setMax(50.0F);
        instant.scrollBy(500.0F);
        helper.assertTrue(instant.position() == 50.0F, "Reduced motion: scrolls instantly and clamps");
        helper.succeed();
    }

    /** Swipes past the snap fraction or flung fast change page; small slow drags snap back; ends clamp. */
    static void swipePager(GameTestHelper helper) {
        SwipePager pager = new SwipePager(3);
        pager.setPageWidth(200.0F);
        long t = 0L;
        pager.beginDrag(t);
        t += 400L;
        pager.drag(-30.0F, t);
        t += 400L;
        pager.release(t, 0);
        helper.assertTrue(pager.index() == 0, "A small slow drag stays on the page");
        pager.beginDrag(t);
        t += 300L;
        pager.drag(-70.0F, t);
        t += 300L;
        pager.release(t, 0);
        helper.assertTrue(pager.index() == 1, "Past the snap fraction: next page");
        pager.beginDrag(t);
        for (int i = 0; i < 3; i++) {
            t += 10L;
            pager.drag(-8.0F, t);
        }
        pager.release(t, 0);
        helper.assertTrue(pager.index() == 2, "A quick flick changes page");
        pager.beginDrag(t);
        t += 300L;
        pager.drag(-150.0F, t);
        helper.assertTrue(pager.position(t) < 2.3F, "Resistance past the last page");
        t += 300L;
        pager.release(t, 0);
        helper.assertTrue(pager.index() == 2 && pager.position(t) == 2.0F, "Clamps to the last page");
        pager.select(0, t, 0);
        helper.assertTrue(pager.index() == 0 && pager.position(t) == 0.0F, "Select jumps with zero duration");
        helper.succeed();
    }

    /** Flex sizes add up and respect weights; offsets and grids line up. */
    static void layoutMath(GameTestHelper helper) {
        int[] sizes = UiLayout.flex(200, 4, new int[] {50, 0, 0}, new float[] {0, 1, 3});
        helper.assertTrue(sizes[0] == 50, "Fixed child keeps its size");
        helper.assertTrue(sizes[0] + sizes[1] + sizes[2] + 8 == 200, "Sizes plus gaps fill the space");
        helper.assertTrue(near(sizes[2], sizes[1] * 3, 3), "Weights split the rest 1:3, got " + sizes[1] + "/" + sizes[2]);
        int[] offsets = UiLayout.offsets(10, 4, sizes);
        helper.assertTrue(offsets[1] == 10 + 50 + 4 && offsets[2] == offsets[1] + sizes[1] + 4, "Offsets follow sizes and gaps");
        helper.assertTrue(UiLayout.total(4, new int[] {10, 20}) == 34, "Total includes gaps");
        int[][] grid = UiLayout.grid(5, 2, 30, 10, 2, 3);
        helper.assertTrue(grid[3][0] == 32 && grid[3][1] == 13 && grid[4][0] == 0 && grid[4][1] == 26, "Grid fills row by row");
        helper.assertTrue(UiLayout.columnsThatFit(100, 30, 5) == 3 && UiLayout.columnsThatFit(99, 30, 5) == 2 && UiLayout.columnsThatFit(5, 30, 5) == 1, "Column fit");
        int[] squeezed = UiLayout.flex(20, 4, new int[] {50, 0}, new float[] {0, 1});
        helper.assertTrue(squeezed[1] == 0, "Weighted children never go negative");
        helper.succeed();
    }

    /** Theme JSON overrides single tokens, falls back for the rest, and reports bad colours. */
    static void themeParsing(GameTestHelper helper) {
        var json = JsonParser.parseString("{\"colors\": {\"paper\": \"#ffffff\", \"ink\": \"#80102030\", \"jade\": \"#0f0\"},"
                + " \"metrics\": {\"padding\": 12}, \"motion\": {\"fast\": 50}}");
        SumiTheme theme = SumiTheme.parse(json, SumiTheme.DEFAULT).getOrThrow();
        helper.assertTrue(theme.color(SumiTheme.PAPER) == 0xFFFFFFFF, "Overridden colour");
        helper.assertTrue(theme.color(SumiTheme.INK) == 0x80102030, "ARGB colour keeps its alpha");
        helper.assertTrue(theme.color(SumiTheme.JADE) == 0xFF00FF00, "#rgb shorthand");
        helper.assertTrue(theme.color(SumiTheme.LACQUER) == SumiTheme.DEFAULT.color(SumiTheme.LACQUER), "Missing colour falls back");
        helper.assertTrue(theme.metric(SumiTheme.PADDING) == 12 && theme.metric(SumiTheme.GAP) == SumiTheme.DEFAULT.metric(SumiTheme.GAP),
                "Metrics override and fall back");
        helper.assertTrue(theme.ms(SumiTheme.FAST) == 50 && theme.ms(SumiTheme.SLOW) == SumiTheme.DEFAULT.ms(SumiTheme.SLOW), "Motion tokens");
        helper.assertTrue(SumiTheme.parse(JsonParser.parseString("{\"colors\": {\"paper\": \"nope\"}}"), SumiTheme.DEFAULT).isError(),
                "A bad colour is an error, not a crash");
        helper.assertTrue(SumiTheme.parse(JsonParser.parseString("{}"), SumiTheme.DEFAULT).getOrThrow().colors().equals(SumiTheme.DEFAULT.colors()),
                "An empty file is the default theme");
        SumiTheme layered = SumiTheme.DEFAULT.with(theme);
        helper.assertTrue(layered.color(SumiTheme.PAPER) == 0xFFFFFFFF && layered.color(SumiTheme.GOLD) == SumiTheme.DEFAULT.color(SumiTheme.GOLD),
                "Layering keeps both");
        helper.succeed();
    }

    /** Config sections become tabs; booleans toggles, ranged numbers sliders; slider maths rounds tidily. */
    static void configMapping(GameTestHelper helper) {
        List<ConfigMapping.Section> client = ConfigMapping.sections(ESConfig.CLIENT_SPEC);
        List<String> names = client.stream().map(ConfigMapping.Section::name).toList();
        helper.assertTrue(names.equals(List.of("hud", "visual_lighting", "ui")), "Client tabs in spec order, got " + names);
        ConfigMapping.Entry lightGem = client.getFirst().entries().getFirst();
        helper.assertTrue(lightGem.name().equals("showLightGem") && lightGem.kind() == ConfigMapping.Kind.TOGGLE, "Booleans are toggles");
        helper.assertTrue(lightGem.langKey().equals("emergentstealth.configuration.showLightGem"), "Label lang key");
        helper.assertTrue(lightGem.comment() != null && !lightGem.comment().isBlank(), "Comment becomes the tooltip");
        ConfigMapping.Entry range = ConfigMapping.entry(ESConfig.DYNAMIC_LIGHT_RANGE);
        helper.assertTrue(range.kind() == ConfigMapping.Kind.INT_SLIDER && range.min() == 8 && range.max() == 256, "Ranged ints are sliders");
        helper.assertTrue(ConfigMapping.entry(ESConfig.SHADOW_BOUNCE).kind() == ConfigMapping.Kind.DOUBLE_SLIDER, "Ranged doubles are sliders");
        List<ConfigMapping.Section> server = ConfigMapping.sections(ESConfig.SERVER_SPEC);
        helper.assertTrue(server.size() == 4 && server.getFirst().name().equals("perception"), "Server tabs");
        helper.assertTrue(ConfigMapping.valueAt(0.5, 8, 256, true) == 132, "Integer slider midpoint");
        helper.assertTrue(near(ConfigMapping.valueAt(0.333, 0.0, 1.0, false), 0.33, 1.0E-9), "Double slider rounds to the step");
        helper.assertTrue(ConfigMapping.fraction(132, 8, 256) == 0.5, "Fraction inverts value");
        helper.assertTrue(ConfigMapping.format(0.15, 0.0, 1.0).equals("0.15") && ConfigMapping.format(2.0, 0.5, 15.0).equals("2.0"),
                "Formatting uses the step's decimals");
        helper.succeed();
    }

    /** The skill tree model matches the server's unlock rules, edges track ownership, arrows find neighbours. */
    static void skillTreeModel(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1, 8.5), 0.0F);
        try {
            SkillTreeModel.PathTree tree = SkillTreeModel.build(player, SkillPath.SHINOBI);
            helper.assertTrue(!tree.isEmpty() && tree.nodes().stream().allMatch(n -> n.skill().path() == SkillPath.SHINOBI), "Only this path");
            var root = tree.node(EmergentStealth.id("shadow_walker"));
            helper.assertTrue(root != null && root.state() == SkillTreeModel.NodeState.NEEDS_POINTS, "Root needs points at first");
            helper.assertTrue(tree.node(EmergentStealth.id("soft_soles")).state() == SkillTreeModel.NodeState.LOCKED, "Child is locked");
            helper.assertTrue(tree.node(EmergentStealth.id("ghost")).state() == SkillTreeModel.NodeState.CAPSTONE
                    || tree.node(EmergentStealth.id("ghost")).state() == SkillTreeModel.NodeState.LOCKED, "Capstone is not buyable");
            for (SkillTreeModel.Node node : tree.nodes()) {
                helper.assertTrue(node.state() == SkillTreeModel.stateOf(Skills.blocker(player, node.id())), "State matches the server rule");
            }
            helper.assertTrue(tree.edges().stream().anyMatch(e -> e.to().equals(EmergentStealth.id("soft_soles")) && !e.satisfied()),
                    "Prerequisite edge, not yet satisfied");
            helper.assertTrue(tree.edges().stream().anyMatch(e -> e.to().equals(EmergentStealth.id("feint")) && e.anyOf()),
                    "Any-of prerequisites are marked");

            player.setData(ESAttachments.PROGRESSION, Skills.progression(player).withPoints(SkillPath.SHINOBI, 2));
            tree = SkillTreeModel.build(player, SkillPath.SHINOBI);
            helper.assertTrue(tree.points() == 2 && tree.node(EmergentStealth.id("shadow_walker")).state() == SkillTreeModel.NodeState.AVAILABLE,
                    "With points the root is available");
            helper.assertTrue(Skills.unlock(player, EmergentStealth.id("shadow_walker")), "Unlock the root");
            tree = SkillTreeModel.build(player, SkillPath.SHINOBI);
            helper.assertTrue(tree.node(EmergentStealth.id("shadow_walker")).state() == SkillTreeModel.NodeState.OWNED, "Owned");
            helper.assertTrue(tree.node(EmergentStealth.id("soft_soles")).state() == SkillTreeModel.NodeState.AVAILABLE, "Child opens up");
            helper.assertTrue(tree.edges().stream().anyMatch(e -> e.to().equals(EmergentStealth.id("soft_soles")) && e.satisfied()),
                    "Edge satisfied");

            var below = SkillTreeModel.neighbour(tree.nodes(), tree.node(EmergentStealth.id("shadow_walker")), 0, 1);
            helper.assertTrue(below != null && below.row() == 1, "Down arrow goes to the next row");
            var right = SkillTreeModel.neighbour(tree.nodes(), tree.node(EmergentStealth.id("soft_soles")), 1, 0);
            helper.assertTrue(right != null && right.id().equals(EmergentStealth.id("quiet_hands")), "Right arrow stays on the row");
            helper.assertTrue(SkillTreeModel.neighbour(tree.nodes(), tree.node(EmergentStealth.id("shadow_walker")), 0, -1) == null,
                    "Nothing above the root");
            helper.assertTrue(SkillTreeModel.build(player, SkillPath.SHOGUNATE).nodes().stream()
                    .noneMatch(n -> n.skill().path() == SkillPath.SHINOBI), "Paths are separate");
        } finally {
            TestPlayers.remove(player);
        }
        helper.succeed();
    }

    /** The typewriter pauses at sentence ends and shows everything after its duration. */
    static void typewriter(GameTestHelper helper) {
        String line = "Halt. Who goes there?";
        helper.assertTrue(Typewriter.visible(line, 0L, 20.0F) == 0, "Nothing at first");
        helper.assertTrue(Typewriter.visible(line, 250L, 20.0F) == 5, "5 characters after 250 ms at 20/s");
        helper.assertTrue(Typewriter.visible(line, 500L, 20.0F) == 5, "Pauses after the full stop");
        long duration = Typewriter.durationMs(line, 20.0F);
        helper.assertTrue(Typewriter.visible(line, duration, 20.0F) == line.length(), "All visible after the duration");
        helper.assertTrue(Typewriter.visible(line, duration - 60L, 20.0F) < line.length(), "Not before");
        helper.assertTrue(Typewriter.visible(line, 10L, 0.0F) == line.length(), "Speed 0 shows the line at once");
        helper.succeed();
    }

    /** Paper noise is deterministic, in range and tiles seamlessly. */
    static void paperNoise(GameTestHelper helper) {
        for (int i = 0; i < 64; i++) {
            float x = i * 3.7F;
            float y = i * 5.3F;
            float v = PaperNoise.fbm(x, y, 32, 128, 7L, 4);
            helper.assertTrue(v >= 0.0F && v < 1.0F, "Noise in [0,1), got " + v);
            helper.assertTrue(near(v, PaperNoise.fbm(x + 128, y, 32, 128, 7L, 4), 1.0E-4)
                    && near(v, PaperNoise.fbm(x, y + 128, 32, 128, 7L, 4), 1.0E-4), "Tiles at the period");
            helper.assertTrue(v == PaperNoise.fbm(x, y, 32, 128, 7L, 4), "Deterministic");
            float w = PaperNoise.wobble(i * 0.37F, 3L);
            helper.assertTrue(w >= -1.0F && w <= 1.0F, "Wobble in [-1,1]");
        }
        helper.succeed();
    }
}
