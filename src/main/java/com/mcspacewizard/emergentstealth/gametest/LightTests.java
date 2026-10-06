package com.mcspacewizard.emergentstealth.gametest;

import com.mcspacewizard.emergentstealth.ai.perception.NpcPerception;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESBlocks;
import com.mcspacewizard.emergentstealth.stealth.light.ExposureModel;
import com.mcspacewizard.emergentstealth.stealth.light.LightGemSync;
import com.mcspacewizard.emergentstealth.stealth.light.Snuffing;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Light & shadow GameTests (design doc 13). Each test builds a sealed stone box in the arena so neither
 * the sky nor neighbouring tests' lights interfere. Checks run after a short delay so vanilla's light
 * engine has caught up (only needed where a test compares against vanilla light).
 */
final class LightTests {
    private LightTests() {}

    private static final int DELAY = 10;

    /** Stone box: walls at x=0/8 and z=0/13, roof at y=5. Inside: x 1-7, y 1-4, z 1-12. */
    static void sealedBox(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 13; z++) {
                helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
                for (int y = 1; y <= 4; y++) {
                    if (x == 0 || x == 8 || z == 0 || z == 13) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
        }
    }

    static float exposure(GameTestHelper helper, Vec3 relative) {
        return ExposureModel.exposureUncached(helper.getLevel(), helper.absoluteVec(relative));
    }

    /** A torch behind a wall leaves the far side dark, even though vanilla light leaks around the corner. */
    static void wallCastsShadow(GameTestHelper helper) {
        sealedBox(helper);
        helper.setBlock(new BlockPos(2, 1, 6), Blocks.TORCH);
        // Wall segment between the torch and the "hidden" spot: x=4, z=4..8, full height.
        for (int z = 4; z <= 8; z++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
            }
        }
        helper.runAfterDelay(DELAY, () -> {
            Vec3 hidden = new Vec3(5.5, 1.5, 6.5);
            Vec3 lit = new Vec3(2.5, 1.5, 9.5);
            float hiddenExposure = exposure(helper, hidden);
            float litExposure = exposure(helper, lit);
            int vanillaHidden = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(BlockPos.containing(hidden)));
            helper.assertTrue(vanillaHidden >= 3, "Test setup: vanilla light should leak around the wall, got " + vanillaHidden);
            helper.assertTrue(hiddenExposure < 0.05F, "Behind the wall should be in shadow, exposure " + hiddenExposure);
            helper.assertTrue(litExposure > 0.4F, "In view of the torch should be lit, exposure " + litExposure);
            helper.succeed();
        });
    }

    static void glassLetsLightThrough(GameTestHelper helper) {
        sealedBox(helper);
        helper.setBlock(new BlockPos(2, 1, 6), Blocks.TORCH);
        for (int z = 4; z <= 8; z++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(4, y, z), Blocks.GLASS);
            }
        }
        float behindGlass = exposure(helper, new Vec3(5.5, 1.5, 6.5));
        helper.assertTrue(behindGlass > 0.3F, "Light should pass through glass, exposure " + behindGlass);
        helper.succeed();
    }

    static void darknessIsDark(GameTestHelper helper) {
        sealedBox(helper);
        helper.runAfterDelay(DELAY, () -> {
            float dark = exposure(helper, new Vec3(4.5, 1.5, 6.5));
            helper.assertTrue(dark < 0.02F, "A sealed box with no lights should be dark, exposure " + dark);
            helper.succeed();
        });
    }

    static void snuffAndRelight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(4, 1, 6);
        BlockPos pos = helper.absolutePos(rel);
        helper.setBlock(rel, Blocks.TORCH);
        helper.assertTrue(Snuffing.snuff(level, pos, null), "Torch should be snuffable");
        helper.assertBlockPresent(ESBlocks.UNLIT_TORCH.get(), rel);
        helper.assertTrue(level.getBlockState(pos).getLightEmission() == 0, "Unlit torch must not emit light");
        helper.assertTrue(Snuffing.relight(level, pos, null), "Unlit torch should relight");
        helper.assertBlockPresent(Blocks.TORCH, rel);

        BlockPos lampRel = new BlockPos(5, 1, 6);
        helper.setBlock(lampRel, Blocks.GLOWSTONE);
        helper.assertFalse(Snuffing.snuff(level, helper.absolutePos(lampRel), null), "Glowstone (powered/mineral light) must not be snuffable");
        helper.succeed();
    }

    static void heldTorchLightsHolder(GameTestHelper helper) {
        sealedBox(helper);
        helper.runAfterDelay(DELAY, () -> {
            ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1.0, 6.5), 0.0F);
            try {
                float dark = LightGemSync.playerExposure(helper.getLevel(), player);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
                float lit = ExposureModel.exposureUncached(helper.getLevel(), player.position().add(0, 1.0, 0));
                helper.assertTrue(dark < 0.05F, "Empty-handed in the dark should be dark, got " + dark);
                helper.assertTrue(lit > 0.5F, "Holding a torch should light you up, got " + lit);
            } finally {
                TestPlayers.remove(player);
            }
            helper.succeed();
        });
    }

    static void darknessSlowsDetection(GameTestHelper helper) {
        sealedBox(helper);
        helper.runAfterDelay(DELAY, () -> {
            StealthNpc npc = PerceptionTests.npc(helper, "ashigaru");
            ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1.0, 11.5), 180.0F);
            try {
                float dark = NpcPerception.computeSight(helper.getLevel(), npc.getPerceptionProfile(), npc.getEyePosition(),
                        npc.getYHeadRot(), npc.getXRot(), player, true, ExposureModel.INSTANCE, null).visibility();
                helper.setBlock(new BlockPos(4, 1, 10), Blocks.GLOWSTONE);
                float lit = NpcPerception.computeSight(helper.getLevel(), npc.getPerceptionProfile(), npc.getEyePosition(),
                        npc.getYHeadRot(), npc.getXRot(), player, true, (level, point) -> ExposureModel.FLOOR
                                + (1.0F - ExposureModel.FLOOR) * ExposureModel.exposureUncached(level, point), null).visibility();
                helper.assertTrue(dark > 0.0F, "Darkness shouldn't make you invisible, got " + dark);
                helper.assertTrue(dark < lit * 0.3F, "Darkness should make you much harder to see: dark " + dark + ", lit " + lit);
            } finally {
                TestPlayers.remove(player);
            }
            helper.succeed();
        });
    }
}
