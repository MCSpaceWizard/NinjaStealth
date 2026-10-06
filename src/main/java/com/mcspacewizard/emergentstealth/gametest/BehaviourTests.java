package com.mcspacewizard.emergentstealth.gametest;

import java.util.List;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.behaviour.BehaviourTree;
import com.mcspacewizard.emergentstealth.ai.brain.AlertState;
import com.mcspacewizard.emergentstealth.ai.brain.PoiCause;
import com.mcspacewizard.emergentstealth.ai.group.AttackTokens;
import com.mcspacewizard.emergentstealth.ai.group.SearchGroups;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.registry.ESRegistries;
import com.mcspacewizard.emergentstealth.stealth.sound.HeardNoise;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseEvent;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseKind;
import com.mcspacewizard.emergentstealth.stealth.sound.Noises;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Behaviour core (design doc 14 §8): behaviour trees, sound reactions, shouts, search groups, attack tokens. */
final class BehaviourTests {
    private BehaviourTests() {}

    private static StealthNpc guard(GameTestHelper helper, Vec3 relative, float yaw, boolean ai) {
        StealthNpc npc = helper.spawn(ESEntities.STEALTH_NPC.get(), relative);
        npc.setArchetype(EmergentStealth.id("ashigaru"), true);
        npc.setNoAi(!ai);
        npc.setYRot(yaw);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setHome(npc.blockPosition(), yaw);
        return npc;
    }

    private static String describe(StealthNpc npc) {
        return " [state " + npc.stealthBrain().state() + ", cause " + npc.stealthBrain().cause()
                + ", bt " + npc.stealthBrain().activeBehaviour() + "]";
    }

    /** 1. The default trees load, and a custom JSON tree decodes and drives an NPC. */
    static void customTreeDrivesNpc(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().lookupOrThrow(ESRegistries.BEHAVIOUR);
        helper.assertTrue(registry.getValue(StealthNpc.GUARD_BEHAVIOUR) != null, "guard behaviour tree missing");
        helper.assertTrue(registry.getValue(StealthNpc.CIVILIAN_BEHAVIOUR) != null, "civilian behaviour tree missing");

        String json = """
                { "type": "sequence", "children": [
                  { "type": "state", "states": ["curious"] },
                  { "type": "bark", "situation": "custom_test" },
                  { "type": "look_at_poi" } ] }""";
        BehaviourTree tree = BehaviourTree.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        StealthNpc npc = guard(helper, new Vec3(4.5, 1, 8.5), 0.0F, false);
        npc.stealthBrain().setTreeOverride(tree);
        ServerLevel level = helper.getLevel();
        npc.stealthBrain().onNoise(level, new HeardNoise(NoiseEvent.of(helper.absoluteVec(new Vec3(4.5, 1, 4.5)), 8, NoiseKind.IMPACT), 0.2F, 6.0F));
        npc.stealthBrain().tick(level);
        helper.assertTrue(npc.stealthBrain().state() == AlertState.CURIOUS, "A quiet noise should make the NPC curious" + describe(npc));
        helper.assertTrue("custom_test".equals(npc.stealthBrain().lastBark()), "The custom tree should have barked" + describe(npc));
        helper.succeed();
    }

    /** 4. A quiet noise makes a guard curious; a loud one makes it investigate the spot. */
    static void noiseCuriousThenInvestigate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StealthNpc quiet = guard(helper, new Vec3(2.5, 1, 8.5), 0.0F, false);
        // Noises skip NPCs without AI, so this one has AI (it's checked straight away).
        StealthNpc loud = guard(helper, new Vec3(6.5, 1, 8.5), 0.0F, true);
        Vec3 spot = helper.absoluteVec(new Vec3(6.5, 1, 4.5));
        quiet.stealthBrain().onNoise(level, new HeardNoise(NoiseEvent.of(spot, 10, NoiseKind.IMPACT), 0.3F, 7.0F));
        Noises.emit(level, NoiseEvent.of(spot, 16, NoiseKind.IMPACT));
        quiet.stealthBrain().tick(level);
        loud.stealthBrain().tick(level);
        helper.assertTrue(quiet.stealthBrain().state() == AlertState.CURIOUS, "Quiet noise: curious" + describe(quiet));
        helper.assertTrue(loud.stealthBrain().state() == AlertState.INVESTIGATING, "Loud noise: investigate" + describe(loud));
        Vec3 poi = loud.stealthBrain().pointOfInterest();
        helper.assertTrue(poi != null && poi.distanceTo(spot) < 0.01, "The guard should investigate the noise spot, got " + poi);
        helper.assertTrue(loud.stealthBrain().cause() == PoiCause.HEARD, "Cause should be heard" + describe(loud));
        helper.succeed();
    }

    /** 5. A guard's shout brings a second guard who couldn't see anything. */
    static void shoutBringsGuard(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StealthNpc shouter = guard(helper, new Vec3(4.5, 1, 20.5), 0.0F, false);
        StealthNpc listener = guard(helper, new Vec3(4.5, 1, 6.5), 180.0F, true);
        helper.runAfterDelay(5, () -> Noises.emit(level, new NoiseEvent(shouter.getEyePosition(), 24, NoiseKind.SHOUT, null, shouter.getUUID())));
        helper.succeedWhen(() -> {
            helper.assertTrue(listener.stealthBrain().cause() == PoiCause.SHOUT || listener.distanceTo(shouter) < 4.0,
                    "The listener should react to the shout" + describe(listener));
            helper.assertTrue(listener.distanceTo(shouter) < 4.0, "The listener should come to the shouter, distance "
                    + String.format("%.1f", listener.distanceTo(shouter)) + describe(listener));
        });
    }

    /** 2. Two guards searching for the same target claim different points, and search longer than one guard would. */
    static void searchGroupSplits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StealthNpc first = guard(helper, new Vec3(3.5, 1, 8.5), 0.0F, false);
        StealthNpc second = guard(helper, new Vec3(5.5, 1, 8.5), 0.0F, false);
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1, 10.5), 180.0F);
        for (StealthNpc npc : List.of(first, second)) {
            npc.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
        }
        TestPlayers.remove(player);
        helper.onEachTick(() -> {
            first.stealthBrain().tick(level);
            second.stealthBrain().tick(level);
        });
        helper.succeedWhen(() -> {
            SearchGroups groups = SearchGroups.get(level);
            SearchGroups.Group group = groups.groupOf(first);
            helper.assertTrue(first.stealthBrain().state() == AlertState.SEARCHING && second.stealthBrain().state() == AlertState.SEARCHING,
                    "Both guards should be searching" + describe(first) + describe(second));
            helper.assertTrue(group != null && group == groups.groupOf(second), "They should share one search group");
            SearchGroups.Point a = claimed(group, first);
            SearchGroups.Point b = claimed(group, second);
            helper.assertTrue(a != null && b != null && !a.pos.equals(b.pos), "They should check different points");
            long base = Math.round(com.mcspacewizard.emergentstealth.config.ESConfig.SEARCH_SECONDS.get() * 20.0);
            helper.assertTrue(group.endTick() - group.started() > base, "Two searchers should search longer than one ("
                    + (group.endTick() - group.started()) + " vs " + base + " ticks)");
        });
    }

    private static SearchGroups.@org.jspecify.annotations.Nullable Point claimed(SearchGroups.Group group, StealthNpc npc) {
        for (SearchGroups.Point point : group.points()) {
            if (npc.getUUID().equals(point.claimedBy())) {
                return point;
            }
        }
        return null;
    }

    /** 3. With three guards on one target, at most two attack; the third holds the ring. */
    static void attackTokensLimit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<StealthNpc> guards = List.of(
                guard(helper, new Vec3(2.5, 1, 6.5), 0.0F, false),
                guard(helper, new Vec3(4.5, 1, 6.5), 0.0F, false),
                guard(helper, new Vec3(6.5, 1, 6.5), 0.0F, false));
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1, 9.5), 180.0F);
        for (StealthNpc npc : guards) {
            npc.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
        }
        helper.onEachTick(() -> guards.forEach(npc -> npc.stealthBrain().tick(level)));
        helper.runAfterDelay(5, () -> {
            try {
                long attackers = guards.stream().filter(npc -> AttackTokens.get(level).holds(npc, player.getUUID())).count();
                long ring = guards.stream().filter(npc -> npc.stealthBrain().activeBehaviour().equals("hold_ring")).count();
                helper.assertTrue(guards.stream().allMatch(npc -> npc.stealthBrain().state() == AlertState.COMBAT),
                        "All three should be in combat" + guards.stream().map(BehaviourTests::describe).toList());
                helper.assertTrue(attackers == 2, "Exactly two should hold attack tokens, got " + attackers);
                helper.assertTrue(ring == 1, "The third should hold the ring, got " + ring
                        + guards.stream().map(BehaviourTests::describe).toList());
            } finally {
                TestPlayers.remove(player);
            }
            helper.succeed();
        });
    }
}
