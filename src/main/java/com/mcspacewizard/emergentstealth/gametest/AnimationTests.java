package com.mcspacewizard.emergentstealth.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.action.ActionPlayback;
import com.mcspacewizard.emergentstealth.anim.ActionClock;
import com.mcspacewizard.emergentstealth.anim.BodyPoses;
import com.mcspacewizard.emergentstealth.anim.Bone;
import com.mcspacewizard.emergentstealth.anim.CrawlGait;
import com.mcspacewizard.emergentstealth.anim.HumanoidPose;
import com.mcspacewizard.emergentstealth.anim.LimbIk;
import com.mcspacewizard.emergentstealth.anim.VerletChain;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * The shared animation maths (design doc 17 §8): everything the client poses are built from that can run without
 * a client. Registers itself, separately from ESGameTests. None of these touch the world.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class AnimationTests {
    private AnimationTests() {}

    private static final Identifier ARENA = EmergentStealth.id("arena");
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();

    static {
        TESTS.put("anim/ik_reaches_target", AnimationTests::ikReachesTarget);
        TESTS.put("anim/crawl_hands_stay_planted", AnimationTests::crawlHandsStayPlanted);
        TESTS.put("anim/verlet_keeps_shape", AnimationTests::verletKeepsShape);
        TESTS.put("anim/action_clock_seeks", AnimationTests::actionClockSeeks);
        TESTS.put("anim/pose_blending", AnimationTests::poseBlending);
        TESTS.put("anim/body_poses_stable", AnimationTests::bodyPosesStable);
    }

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        for (Map.Entry<String, Consumer<GameTestHelper>> test : TESTS.entrySet()) {
            event.register(Registries.TEST_FUNCTION, EmergentStealth.id(test.getKey()), test::getValue);
        }
    }

    @SubscribeEvent
    static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(EmergentStealth.id("animation"));
        for (String name : TESTS.keySet()) {
            Identifier id = EmergentStealth.id(name);
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(environment, ARENA, 20, 0, true)));
        }
    }

    private static boolean near(double a, double b, double tolerance) {
        return Math.abs(a - b) <= tolerance;
    }

    // ------------------------------------------------------------------------------------------------

    /** Two-bone IK puts the end of the limb on any reachable target, elbows and knees bending their own way. */
    static void ikReachesTarget(GameTestHelper helper) {
        float[] solved = new float[2];
        float[] end = new float[2];
        for (float bendSign : new float[] {-1.0F, 1.0F}) {
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI * 2.0 / 24.0;
                for (float distance : new float[] {3.0F, 6.0F, 9.5F}) {
                    float y = (float) (Math.cos(angle) * distance);
                    float z = (float) (Math.sin(angle) * distance);
                    LimbIk.solve(y, z, LimbIk.ARM_UPPER, LimbIk.ARM_LOWER, bendSign, solved);
                    LimbIk.endPoint(solved[0], solved[1], LimbIk.ARM_UPPER, LimbIk.ARM_LOWER, end);
                    helper.assertTrue(near(end[0], y, 0.05) && near(end[1], z, 0.05),
                            String.format("IK should reach (%.2f, %.2f), got (%.2f, %.2f)", y, z, end[0], end[1]));
                    helper.assertTrue(solved[1] * bendSign >= -1.0E-4F, "The joint bends the wrong way for sign " + bendSign);
                }
            }
        }
        // Out of reach: fully stretched towards the target.
        LimbIk.solve(20.0F, 0.0F, LimbIk.ARM_UPPER, LimbIk.ARM_LOWER, -1.0F, solved);
        helper.assertTrue(near(solved[0], 0.0, 0.05) && near(solved[1], 0.0, 0.05), "An unreachable target stretches the limb");
        helper.succeed();
    }

    /** While a hand is planted, the body moves past it: hand + travelled distance stays put, and the cycle is seamless. */
    static void crawlHandsStayPlanted(GameTestHelper helper) {
        float[] hand = new float[2];
        float strideBlocks = CrawlGait.STRIDE;
        for (int i = 0; i <= 49; i++) {
            float distance = strideBlocks * 0.5F * i / 50.0F; // within one stance half
            CrawlGait.hand(CrawlGait.phase(distance), hand);
            float world = hand[0] + distance * 16.0F;
            helper.assertTrue(near(world, CrawlGait.REACH, 0.01), String.format("A planted hand slid: %.3f px at %.3f blocks", world, distance));
            helper.assertTrue(hand[1] == 0.0F, "A planted hand is on the ground");
        }
        float[] a = new float[2];
        float[] b = new float[2];
        for (float boundary : new float[] {0.5F, 1.0F}) {
            CrawlGait.hand(boundary - 1.0E-4F, a);
            CrawlGait.hand(boundary + 1.0E-4F, b);
            helper.assertTrue(near(a[0], b[0], 0.05) && near(a[1], b[1], 0.05), "The hand jumps at phase " + boundary);
        }
        helper.assertTrue(near(CrawlGait.push(0.0F), 1.0F, 1.0E-4) && near(CrawlGait.push(0.4999F), 0.0F, 0.01), "Knee push extends through stance");
        helper.succeed();
    }

    /** A dragged chain keeps its segment lengths and stays on the floor while its pin wanders. */
    static void verletKeepsShape(GameTestHelper helper) {
        double radius = 0.12;
        VerletChain chain = new VerletChain(radius, 0.62, 0.75, 0.75);
        chain.place(0.0, 0.6, 0.0, 1.0, -0.4, 0.0);
        VerletChain.Ground floor = (x, y, z) -> 0.0;
        for (int tick = 0; tick < 200; tick++) {
            double t = tick * 0.05;
            chain.step(Math.sin(t) * 3.0, 0.62, Math.cos(t * 0.7) * 2.0, floor, 0.9, 6);
        }
        for (int i = 0; i < chain.size() - 1; i++) {
            double ratio = chain.segmentLength(i) / chain.restLength(i);
            helper.assertTrue(ratio > 0.95 && ratio < 1.05, String.format("Segment %d stretched to %.2f of its length", i, ratio));
        }
        for (int i = 1; i < chain.size(); i++) {
            helper.assertTrue(chain.y(i) >= radius - 1.0E-6, String.format("Point %d sank to y=%.3f", i, chain.y(i)));
        }
        helper.assertTrue(chain.y(chain.size() - 1) < 0.2, "The feet should drag along the floor");
        helper.succeed();
    }

    /** Clients never count their own time: late viewers seek in, and the clock holds the last frame. */
    static void actionClockSeeks(GameTestHelper helper) {
        ActionPlayback action = new ActionPlayback(EmergentStealth.id("takedown/rear_nonlethal"), ActionPlayback.Role.VICTIM, 1000L, 40,
                Vec3.ZERO, 0.0F);
        helper.assertTrue(near(ActionClock.ticks(action, 1010L, 0.5F), 10.5, 1.0E-4), "10.5 ticks in");
        helper.assertTrue(near(ActionClock.seekTicks(action, 1025L), 25.0, 1.0E-4), "A late viewer seeks 25 ticks in");
        helper.assertTrue(ActionClock.ticks(action, 990L, 0.0F) == 0.0F, "Before the start: 0");
        helper.assertTrue(ActionClock.ticks(action, 2000L, 0.0F) == 40.0F, "After the end: holds the last frame");
        helper.assertTrue(near(ActionClock.progress(action, 1020L, 0.0F), 0.5, 1.0E-4), "Halfway");
        helper.assertTrue(ActionClock.playing(action, 1039L, 0.9F) && !ActionClock.playing(action, 1040L, 0.0F), "Plays [start, start + length)");
        helper.assertTrue(!ActionClock.playing(ActionPlayback.NONE, 0L, 0.0F), "No action never plays");
        helper.assertTrue(ActionClock.clipId(action, "victim").equals(EmergentStealth.id("takedown/rear_nonlethal.victim")),
                "Clip ids add the role");
        helper.succeed();
    }

    /** Overrides blend towards their targets by weight, additive layers add, untouched channels pass the base through. */
    static void poseBlending(GameTestHelper helper) {
        HumanoidPose pose = new HumanoidPose();
        helper.assertTrue(pose.isIdentity() && pose.apply(Bone.HEAD, HumanoidPose.ROT_X, 0.7F) == 0.7F, "An empty pose keeps the base");
        pose.setRot(Bone.RIGHT_ARM, 1.0F, 0.0F, 0.0F, 1.0F);
        helper.assertTrue(near(pose.apply(Bone.RIGHT_ARM, HumanoidPose.ROT_X, 0.3F), 1.0, 1.0E-5), "A full override replaces the base");
        helper.assertTrue(near(pose.apply(Bone.RIGHT_ARM, HumanoidPose.POS_Y, 2.0F), 2.0, 1.0E-5), "Overriding rotation leaves position alone");
        pose.setRot(Bone.RIGHT_ARM, 0.0F, 0.0F, 0.0F, 0.5F);
        helper.assertTrue(near(pose.apply(Bone.RIGHT_ARM, HumanoidPose.ROT_X, 0.3F), 0.5, 1.0E-5), "A half override goes halfway");
        pose.addRot(Bone.RIGHT_ARM, 0.2F, 0.0F, 0.0F, 0.5F);
        helper.assertTrue(near(pose.apply(Bone.RIGHT_ARM, HumanoidPose.ROT_X, 0.3F), 0.6, 1.0E-5), "Additive layers add");
        pose.setRot(Bone.LEFT_LEG, 1.0F, 0.0F, 0.0F, 0.5F);
        helper.assertTrue(near(pose.apply(Bone.LEFT_LEG, HumanoidPose.ROT_X, 0.4F), 0.7, 1.0E-5), "Half override over a base of 0.4");
        helper.assertTrue(near(pose.baseWeight(Bone.LEFT_LEG, HumanoidPose.ROT_X), 0.5, 1.0E-5), "Half the base survives");
        helper.assertTrue(!pose.touched(Bone.HEAD) && pose.touched(Bone.LEFT_LEG), "Only written bones are touched");
        helper.succeed();
    }

    /** Body poses vary per body but never between frames, clients or relogs. */
    static void bodyPosesStable(GameTestHelper helper) {
        HumanoidPose a = new HumanoidPose();
        HumanoidPose b = new HumanoidPose();
        BodyPoses.knockedOut(a, 42, true, 1.0F);
        BodyPoses.knockedOut(b, 42, true, 1.0F);
        for (Bone bone : Bone.VALUES) {
            for (int channel = 0; channel < HumanoidPose.CHANNELS; channel++) {
                helper.assertTrue(a.apply(bone, channel, 0.0F) == b.apply(bone, channel, 0.0F), "Same body, same pose");
            }
        }
        int differing = 0;
        for (int id = 0; id < 20; id++) {
            HumanoidPose other = new HumanoidPose();
            BodyPoses.knockedOut(other, id, true, 1.0F);
            if (other.apply(Bone.HEAD, HumanoidPose.ROT_Y, 0.0F) != a.apply(Bone.HEAD, HumanoidPose.ROT_Y, 0.0F)) {
                differing++;
            }
        }
        helper.assertTrue(differing >= 15, "Bodies should vary, only " + differing + " of 20 differ");
        float yaw = BodyPoses.yawOffset(7);
        helper.assertTrue(yaw == BodyPoses.yawOffset(7) && Math.abs(yaw) <= 35.0F, "Stable yaw twist within ±35°");
        helper.succeed();
    }
}
