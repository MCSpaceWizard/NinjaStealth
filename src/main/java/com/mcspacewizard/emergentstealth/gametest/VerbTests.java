package com.mcspacewizard.emergentstealth.gametest;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.action.ActionPlayback;
import com.mcspacewizard.emergentstealth.action.BodyCarrying;
import com.mcspacewizard.emergentstealth.action.BodyState;
import com.mcspacewizard.emergentstealth.action.CarryLink;
import com.mcspacewizard.emergentstealth.action.Crawling;
import com.mcspacewizard.emergentstealth.action.Takedowns;
import com.mcspacewizard.emergentstealth.ai.brain.AlertState;
import com.mcspacewizard.emergentstealth.ai.brain.PoiCause;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.registry.ESEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Player verbs (design doc 17 §6): takedowns, bodies and evidence, dragging, crawling. */
final class VerbTests {
    private VerbTests() {}

    private static StealthNpc npc(GameTestHelper helper, Vec3 relative, float yaw, boolean ai, String archetype) {
        StealthNpc npc = helper.spawn(ESEntities.STEALTH_NPC.get(), relative);
        npc.setArchetype(EmergentStealth.id(archetype), true);
        npc.setNoAi(!ai);
        npc.setYRot(yaw);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setHome(npc.blockPosition(), yaw);
        return npc;
    }

    /**
     * NPCs only perceive near a targetable player (the {@code PerceptionScheduler} tiers), so a test about what a
     * guard notices needs one. This one stands behind a stone wall across z = 2, out of sight of the arena beyond.
     * Remove it when the test ends.
     */
    private static ServerPlayer hiddenObserver(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) {
            for (int y = 1; y < 5; y++) {
                helper.setBlock(new BlockPos(x, y, 2), Blocks.STONE);
            }
        }
        return TestPlayers.spawn(helper, new Vec3(4.5, 1, 0.5), 180.0F);
    }

    private static String describe(StealthNpc npc) {
        return " [body " + npc.getBodyState() + ", state " + npc.stealthBrain().state() + ", cause " + npc.stealthBrain().cause() + "]";
    }

    /** 1. Rear takedowns work from behind, not from the front, not on an NPC fighting you; the choke knocks out. */
    static void rearTakedownRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Yaw 0 faces +Z: "behind" is -Z.
        StealthNpc front = npc(helper, new Vec3(2.5, 1, 8.5), 0.0F, false, "ashigaru");
        StealthNpc target = npc(helper, new Vec3(6.5, 1, 8.5), 0.0F, false, "ashigaru");
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(2.5, 1, 9.8), 180.0F);
        helper.assertTrue("front".equals(Takedowns.rearBlocker(player, front)), "No takedown from the front, got "
                + Takedowns.rearBlocker(player, front));
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 7.3)));
        helper.assertTrue(Takedowns.rearBlocker(player, front) == null, "Takedown from behind should be allowed, got "
                + Takedowns.rearBlocker(player, front));
        front.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
        front.stealthBrain().tick(level);
        helper.assertTrue("alerted".equals(Takedowns.rearBlocker(player, front)), "No takedown on a guard fighting you, got "
                + Takedowns.rearBlocker(player, front) + describe(front));

        player.snapTo(helper.absoluteVec(new Vec3(6.5, 1, 7.3)));
        helper.assertTrue(Takedowns.tryRear(player, target, false), "The choke should start");
        helper.assertTrue(player.getData(ESAttachments.ACTION).role() == ActionPlayback.Role.ATTACKER
                && target.getData(ESAttachments.ACTION).role() == ActionPlayback.Role.VICTIM, "Both should play the action");
        helper.runAfterDelay(45, () -> {
            try {
                helper.assertTrue(target.getBodyState() == BodyState.UNCONSCIOUS, "The choke should knock out" + describe(target));
                helper.assertTrue(player.getData(ESAttachments.ACTION).isNone(), "The action should be over");
                helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.0, "The player should be free again");
            } finally {
                TestPlayers.remove(player);
            }
            helper.succeed();
        });
    }

    /** 2. A knocked-out NPC stays down; a guard who sees it wakes it, and the woken guard is on alert. */
    static void knockedOutGetsWoken(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StealthNpc sleeper = npc(helper, new Vec3(4.5, 1, 14.5), 0.0F, true, "ashigaru");
        StealthNpc guard = npc(helper, new Vec3(4.5, 1, 6.5), 0.0F, true, "ashigaru");
        ServerPlayer observer = hiddenObserver(helper);
        sleeper.knockOut(level, null);
        helper.succeedWhen(() -> {
            helper.assertTrue(sleeper.getBodyState() == BodyState.NONE, "The guard should wake the sleeper" + describe(sleeper)
                    + " guard" + describe(guard));
            helper.assertTrue(sleeper.stealthBrain().state() != AlertState.UNAWARE, "The woken guard should be on alert" + describe(sleeper));
            TestPlayers.remove(observer);
        });
    }

    /** 2b. Falling down never lifts a body off the ground (NoAI bodies would stay floating). */
    static void bodyStaysOnGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StealthNpc knocked = npc(helper, new Vec3(2.5, 1, 4.5), 0.0F, false, "ashigaru");
        StealthNpc killed = npc(helper, new Vec3(6.5, 1, 4.5), 0.0F, false, "ashigaru");
        double floor = knocked.getY();
        // After the spawn tick: vanilla only re-fits a widening hitbox once an entity has ticked.
        helper.runAfterDelay(3, () -> {
            knocked.knockOut(level, null);
            killed.becomeCorpse(level, null);
            helper.assertTrue(knocked.getY() == floor && killed.getY() == floor, "Bodies should stay on the floor, at "
                    + knocked.getY() + " and " + killed.getY() + " instead of " + floor);
            helper.assertTrue(knocked.getBbHeight() < 0.5F, "A body lies flat");
            helper.succeed();
        });
    }

    /** 3. A guard who sees a corpse raises the alarm and searches. */
    static void corpseRaisesAlarm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StealthNpc corpse = npc(helper, new Vec3(4.5, 1, 13.5), 0.0F, false, "ashigaru");
        StealthNpc guard = npc(helper, new Vec3(4.5, 1, 6.5), 0.0F, true, "ashigaru");
        ServerPlayer observer = hiddenObserver(helper);
        corpse.becomeCorpse(level, null);
        helper.succeedWhen(() -> {
            AlertState state = guard.stealthBrain().state();
            helper.assertTrue(state == AlertState.HUNTING || state == AlertState.SEARCHING, "The guard should be alarmed" + describe(guard));
            helper.assertTrue(guard.stealthBrain().cause() == PoiCause.EVIDENCE, "Cause should be evidence" + describe(guard));
            TestPlayers.remove(observer);
        });
    }

    /** 4. A corpse behind a wall isn't noticed. */
    static void hiddenCorpseUnnoticed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < 9; x++) {
            for (int y = 1; y < 5; y++) {
                helper.setBlock(new BlockPos(x, y, 10), Blocks.STONE);
            }
        }
        StealthNpc corpse = npc(helper, new Vec3(4.5, 1, 13.5), 0.0F, false, "ashigaru");
        StealthNpc guard = npc(helper, new Vec3(4.5, 1, 6.5), 0.0F, true, "ashigaru");
        guard.setSchedule(new com.mcspacewizard.emergentstealth.ai.routine.Schedule(java.util.List.of(
                new com.mcspacewizard.emergentstealth.ai.routine.Schedule.Entry(0, 0,
                        new com.mcspacewizard.emergentstealth.ai.routine.Schedule.Post(guard.blockPosition(), 0.0F)))));
        ServerPlayer observer = hiddenObserver(helper);
        corpse.becomeCorpse(level, null);
        helper.runAfterDelay(100, () -> {
            TestPlayers.remove(observer);
            helper.assertTrue(guard.stealthBrain().state() == AlertState.UNAWARE, "A hidden corpse shouldn't be noticed" + describe(guard));
            helper.succeed();
        });
    }

    /** 5. A dragged body follows the player and slows them; dropping restores the speed. */
    static void dragFollows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StealthNpc body = npc(helper, new Vec3(4.5, 1, 6.5), 0.0F, false, "ashigaru");
        body.knockOut(level, null);
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1, 7.5), 0.0F);
        try {
            double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            BodyCarrying.pickUp(player, body, CarryLink.Mode.DRAG);
            helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed * 0.8, "Dragging should slow the player");
            player.snapTo(helper.absoluteVec(new Vec3(4.5, 1, 14.5)));
            for (int i = 0; i < 20; i++) {
                BodyCarrying.tick(player);
            }
            helper.assertTrue(body.distanceTo(player) < 2.0, "The body should follow, distance " + body.distanceTo(player));
            BodyCarrying.drop(player);
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 1.0E-6, "Dropping should restore speed");
            helper.assertTrue(BodyCarrying.link(body).isNone(), "The body should be free");
        } finally {
            TestPlayers.remove(player);
        }
        helper.succeed();
    }

    /** 6. Crawling: the stance is set, the pose forced, and you can't stand up under a low ceiling. */
    static void crawlStance(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1, 8.5), 0.0F);
        try {
            Crawling.toggle(player);
            helper.assertTrue(Crawling.isCrawling(player) && player.getForcedPose() == Pose.SWIMMING, "Should be crawling");
            helper.setBlock(new BlockPos(4, 2, 8), Blocks.STONE);
            Crawling.toggle(player);
            helper.assertTrue(Crawling.isCrawling(player), "No room to stand: still crawling");
            helper.setBlock(new BlockPos(4, 2, 8), Blocks.AIR);
            Crawling.toggle(player);
            helper.assertTrue(!Crawling.isCrawling(player) && player.getForcedPose() == null, "Should stand up with room");
        } finally {
            TestPlayers.remove(player);
        }
        helper.succeed();
    }

    /** 7. Falling onto an NPC knocks it out and cancels the fall. */
    static void airTakedown(GameTestHelper helper) {
        StealthNpc npc = npc(helper, new Vec3(4.5, 1, 8.5), 0.0F, false, "ashigaru");
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 3.2, 8.5), 0.0F);
        try {
            player.fallDistance = 4.0;
            player.setOnGround(false);
            player.setDeltaMovement(0.0, -0.6, 0.0);
            helper.assertTrue(Takedowns.checkAirTakedown(player), "An air takedown should start");
            helper.assertTrue(npc.getBodyState() == BodyState.UNCONSCIOUS, "Empty-handed air takedown knocks out" + describe(npc));
            helper.assertTrue(player.fallDistance == 0.0, "Fall damage should be cancelled");
        } finally {
            TestPlayers.remove(player);
        }
        helper.succeed();
    }
}
