package com.mcspacewizard.emergentstealth.gametest;

import java.util.function.Consumer;

import com.mcspacewizard.emergentstealth.ai.brain.AlertState;
import com.mcspacewizard.emergentstealth.ai.perception.NpcPerception;
import com.mcspacewizard.emergentstealth.ai.perception.TargetAwareness;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.registry.ESRegistries;
import com.mcspacewizard.emergentstealth.stealth.LightSampler;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Perception GameTests (design doc 12 §7). NPCs are NoAI so the scheduler leaves them alone and each test
 * drives perception by hand. Arena: 9 x 6 x 26, stone floor at y=0. The NPC stands at z=8 facing +z.
 */
final class PerceptionTests {
    private PerceptionTests() {}

    /** Full light everywhere, so these tests isolate geometry from lighting. */
    private static final LightSampler FULL_LIGHT = (level, point) -> 1.0F;
    private static final Vec3 NPC_POS = new Vec3(4.5, 1.0, 8.5);
    private static final Vec3 FRONT = new Vec3(4.5, 1.0, 16.5);
    private static final Vec3 BEHIND = new Vec3(4.5, 1.0, 1.5);

    // ------------------------------------------------------------------------------------------------
    // Helpers

    static StealthNpc npc(GameTestHelper helper, String archetype) {
        StealthNpc npc = helper.spawn(ESEntities.STEALTH_NPC.get(), NPC_POS);
        npc.setArchetype(EmergentStealth.id(archetype), true);
        npc.setNoAi(true);
        npc.setYRot(0.0F);
        npc.setYHeadRot(0.0F);
        npc.setXRot(0.0F);
        return npc;
    }

    static float sight(GameTestHelper helper, StealthNpc npc, ServerPlayer player) {
        return NpcPerception.computeSight(helper.getLevel(), npc.getPerceptionProfile(), npc.getEyePosition(),
                npc.getYHeadRot(), npc.getXRot(), player, true, FULL_LIGHT, null).visibility();
    }

    static void wall(GameTestHelper helper, int z, Block block) {
        for (int x = 0; x < 9; x++) {
            for (int y = 1; y < 5; y++) {
                helper.setBlock(new BlockPos(x, y, z), block == Blocks.OAK_LEAVES
                        ? Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)
                        : block.defaultBlockState());
            }
        }
    }

    /** Runs a check with a test player, always removing the player afterwards. */
    static void withPlayer(GameTestHelper helper, Vec3 pos, Consumer<ServerPlayer> check) {
        ServerPlayer player = TestPlayers.spawn(helper, pos, 180.0F);
        try {
            check.accept(player);
        } finally {
            TestPlayers.remove(player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Data

    static void profilesLoaded(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().lookupOrThrow(ESRegistries.PERCEPTION_PROFILE);
        for (String name : new String[] {"default", "guard", "sentry", "elite", "captain"}) {
            helper.assertTrue(registry.containsKey(EmergentStealth.id(name)), "Missing perception profile " + name);
        }
        StealthNpc npc = npc(helper, "ashigaru");
        helper.assertTrue(npc.getPerceptionProfile().central().range() == 24.0F,
                "Ashigaru should use the guard profile (24-block central range)");
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Sight geometry

    static void frontVisible(GameTestHelper helper) {
        StealthNpc npc = npc(helper, "ashigaru");
        withPlayer(helper, FRONT, player -> {
            float v = sight(helper, npc, player);
            helper.assertTrue(v > 0.5F, "Player in front in full light should be clearly visible, got " + v);
        });
    }

    static void behindInvisible(GameTestHelper helper) {
        StealthNpc npc = npc(helper, "ashigaru");
        withPlayer(helper, BEHIND, player -> {
            float v = sight(helper, npc, player);
            helper.assertTrue(v == 0.0F, "Player behind the NPC must be invisible (no rear sense), got " + v);
        });
    }

    static void stoneWallBlocks(GameTestHelper helper) {
        StealthNpc npc = npc(helper, "ashigaru");
        wall(helper, 12, Blocks.STONE);
        withPlayer(helper, FRONT, player -> {
            float v = sight(helper, npc, player);
            helper.assertTrue(v == 0.0F, "A stone wall must block sight, got " + v);
        });
    }

    static void glassWallClear(GameTestHelper helper) {
        StealthNpc npc = npc(helper, "ashigaru");
        wall(helper, 12, Blocks.GLASS);
        withPlayer(helper, FRONT, player -> {
            float v = sight(helper, npc, player);
            helper.assertTrue(v > 0.5F, "Glass must not block sight, got " + v);
        });
    }

    static void leavesPartial(GameTestHelper helper) {
        StealthNpc npc = npc(helper, "ashigaru");
        withPlayer(helper, FRONT, player -> {
            float open = sight(helper, npc, player);
            wall(helper, 12, Blocks.OAK_LEAVES);
            float covered = sight(helper, npc, player);
            helper.assertTrue(covered > 0.0F && covered < open * 0.5F,
                    "Leaves should partially block sight: open " + open + ", through leaves " + covered);
        });
    }

    static void crawlInGrassHidden(GameTestHelper helper) {
        StealthNpc npc = npc(helper, "ashigaru");
        // Vegetation needs soil underneath, or block updates remove it.
        for (int x = 2; x <= 6; x++) {
            for (int z = 14; z <= 18; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.SHORT_GRASS);
            }
        }
        helper.assertBlockPresent(Blocks.SHORT_GRASS, new BlockPos(4, 1, 16));
        withPlayer(helper, FRONT, player -> {
            float standing = sight(helper, npc, player);
            player.setPose(Pose.SWIMMING);
            float crawling = sight(helper, npc, player);
            helper.assertTrue(standing > 0.0F, "Standing in tall grass should be visible, got " + standing);
            helper.assertTrue(crawling == 0.0F, "Crawling in tall grass should be hidden, got " + crawling);
        });
    }

    static void peripheralWeaker(GameTestHelper helper) {
        StealthNpc npc = npc(helper, "ashigaru");
        // ~4 blocks away: straight ahead vs ~60 degrees to the side (outside the 35 degree central cone).
        withPlayer(helper, new Vec3(4.5, 1.0, 12.5), ahead -> {
            float central = sight(helper, npc, ahead);
            withPlayerInner(helper, new Vec3(7.96, 1.0, 10.5), side -> {
                float peripheral = sight(helper, npc, side);
                helper.assertTrue(peripheral > 0.0F && peripheral < central * 0.5F,
                        "Peripheral vision should be much weaker: central " + central + ", peripheral " + peripheral);
            });
        });
    }

    static void deepWaterBlocks(GameTestHelper helper) {
        StealthNpc npc = npc(helper, "ashigaru");
        withPlayer(helper, FRONT, player -> {
            fillWater(helper, 10, 12);
            float shallow = sight(helper, npc, player);
            fillWater(helper, 10, 16);
            float deep = sight(helper, npc, player);
            helper.assertTrue(shallow > 0.0F, "3 blocks of water shouldn't block sight, got " + shallow);
            helper.assertTrue(deep == 0.0F, "7 blocks of water should block sight, got " + deep);
        });
    }

    private static void fillWater(GameTestHelper helper, int fromZ, int toZ) {
        for (int x = 2; x <= 6; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = fromZ; z <= toZ; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Awareness over time

    static void gracePeriod(GameTestHelper helper) {
        StealthNpc npc = npc(helper, "ashigaru");
        withPlayer(helper, new Vec3(4.5, 1.0, 11.5), player -> {
            player.setSprinting(true);
            long now = helper.getLevel().getGameTime();
            float minSeconds = npc.getPerceptionProfile().minDetectionSeconds();
            // Simulate updates every 2 ticks for (minDetectionSeconds - 0.1s): must not be fully detected yet.
            int updates = (int) ((minSeconds - 0.1F) * 10.0F);
            for (int i = 0; i <= updates; i++) {
                npc.perception().update(helper.getLevel(), now + i * 2L, 1, FULL_LIGHT);
            }
            TargetAwareness awareness = npc.perception().get(player.getUUID());
            helper.assertTrue(awareness != null && awareness.awareness() > 0.0F, "Awareness should be rising");
            helper.assertTrue(awareness.awareness() < 1.0F,
                    "Detection faster than the grace period (" + minSeconds + "s): " + awareness.awareness());
            for (int i = updates + 1; i <= updates + 20; i++) {
                npc.perception().update(helper.getLevel(), now + i * 2L, 1, FULL_LIGHT);
            }
            helper.assertTrue(awareness.awareness() >= 1.0F, "Should be detected after a while, got " + awareness.awareness());
        });
    }

    static void decayDelay(GameTestHelper helper) {
        StealthNpc npc = npc(helper, "ashigaru");
        withPlayer(helper, FRONT, player -> {
            long now = helper.getLevel().getGameTime();
            for (int i = 0; i < 10; i++) {
                npc.perception().update(helper.getLevel(), now + i * 2L, 1, FULL_LIGHT);
            }
            TargetAwareness awareness = npc.perception().get(player.getUUID());
            helper.assertTrue(awareness != null && awareness.awareness() > 0.1F, "Awareness should have risen");
            float peak = awareness.awareness();
            Vec3 lastKnown = awareness.lastKnownPos();
            wall(helper, 12, Blocks.STONE);
            long hidden = now + 20;
            npc.perception().update(helper.getLevel(), hidden + 20, 1, FULL_LIGHT);      // 1s later
            helper.assertTrue(awareness.awareness() == peak, "Awareness must hold during the decay delay");
            npc.perception().update(helper.getLevel(), hidden + 20 * 8, 1, FULL_LIGHT);  // 8s later
            helper.assertTrue(awareness.awareness() < peak, "Awareness should decay after the delay");
            helper.assertTrue(lastKnown != null && lastKnown.equals(awareness.lastKnownPos()),
                    "Last known position must not update while unseen (no free information)");
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Brain reactions

    static void meleeHitStartsCombat(GameTestHelper helper) {
        StealthNpc npc = npc(helper, "ashigaru");
        withPlayer(helper, BEHIND, player -> {
            npc.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1.0F);
            npc.stealthBrain().tick(helper.getLevel());
            helper.assertTrue(npc.stealthBrain().state() == AlertState.COMBAT,
                    "A guard hit in melee should fight back, state " + npc.stealthBrain().state());
        });
    }

    static void civilianHitFlees(GameTestHelper helper) {
        StealthNpc npc = npc(helper, "townsfolk");
        withPlayer(helper, BEHIND, player -> {
            npc.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1.0F);
            npc.stealthBrain().tick(helper.getLevel());
            helper.assertTrue(npc.stealthBrain().state() == AlertState.FLEEING,
                    "A civilian hit in melee should flee, state " + npc.stealthBrain().state());
        });
    }

    static void unseenStaysUnaware(GameTestHelper helper) {
        StealthNpc npc = npc(helper, "ashigaru");
        withPlayer(helper, BEHIND, player -> {
            long now = helper.getLevel().getGameTime();
            for (int i = 0; i < 40; i++) {
                npc.perception().update(helper.getLevel(), now + i * 2L, 1, FULL_LIGHT);
                npc.stealthBrain().tick(helper.getLevel());
            }
            helper.assertTrue(npc.stealthBrain().state() == AlertState.UNAWARE,
                    "A player behind the NPC must not alert it, state " + npc.stealthBrain().state());
        });
    }

    /** Like withPlayer but without succeeding (for nesting). */
    private static void withPlayerInner(GameTestHelper helper, Vec3 pos, Consumer<ServerPlayer> check) {
        ServerPlayer player = TestPlayers.spawn(helper, pos, 180.0F);
        try {
            check.accept(player);
        } finally {
            TestPlayers.remove(player);
        }
    }
}
