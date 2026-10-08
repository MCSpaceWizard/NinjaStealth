package com.mcspacewizard.emergentstealth.gametest;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.progression.SkillPath;
import com.mcspacewizard.emergentstealth.progression.Skills;
import com.mcspacewizard.emergentstealth.progression.StealthStat;
import com.mcspacewizard.emergentstealth.progression.StealthStats;
import com.mcspacewizard.emergentstealth.progression.Techniques;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.registry.ESItems;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Progression foundations (design doc 26 §6): stats, unlocking, Insight, techniques, gear. */
final class ProgressionTests {
    private ProgressionTests() {}

    private static void withPlayer(GameTestHelper helper, java.util.function.Consumer<ServerPlayer> check) {
        ServerPlayer player = TestPlayers.spawn(helper, new Vec3(4.5, 1, 8.5), 0.0F);
        try {
            check.accept(player);
        } finally {
            TestPlayers.remove(player);
        }
        helper.succeed();
    }

    private static float stat(ServerPlayer player, StealthStat stat) {
        StealthStats.invalidate(player);
        return StealthStats.get(player, stat);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1.0E-4F;
    }

    /** 1 + 5. Skills and gear stack on stealth stats; metal armour is louder. */
    static void statsStack(GameTestHelper helper) {
        withPlayer(helper, player -> {
            helper.assertTrue(near(stat(player, StealthStat.FOOTSTEP_LOUDNESS), 1.0F), "Base footsteps should be 1");
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ESItems.TABI.get()));
            helper.assertTrue(near(stat(player, StealthStat.FOOTSTEP_LOUDNESS), 0.8F), "Tabi: 0.8, got " + stat(player, StealthStat.FOOTSTEP_LOUDNESS));
            player.setData(ESAttachments.PROGRESSION, Skills.progression(player).withPoints(SkillPath.SHINOBI, 2));
            helper.assertTrue(Skills.unlock(player, EmergentStealth.id("shadow_walker")), "Unlock shadow walker");
            helper.assertTrue(Skills.unlock(player, EmergentStealth.id("soft_soles")), "Unlock soft soles");
            helper.assertTrue(near(stat(player, StealthStat.FOOTSTEP_LOUDNESS), 0.8F * 0.85F), "Tabi + Soft Soles: 0.68, got "
                    + stat(player, StealthStat.FOOTSTEP_LOUDNESS));
            helper.assertTrue(near(stat(player, StealthStat.VISIBILITY), 0.95F), "Shadow Walker visibility 0.95");
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            helper.assertTrue(near(stat(player, StealthStat.FOOTSTEP_LOUDNESS), 1.2F * 0.85F), "Iron boots are louder, got "
                    + stat(player, StealthStat.FOOTSTEP_LOUDNESS));
        });
    }

    /** 2. Unlocking needs points and prerequisites; points are spent. */
    static void unlockRules(GameTestHelper helper) {
        withPlayer(helper, player -> {
            helper.assertTrue("points".equals(Skills.blocker(player, EmergentStealth.id("shadow_walker"))), "No points yet");
            player.setData(ESAttachments.PROGRESSION, Skills.progression(player).withPoints(SkillPath.SHINOBI, 3));
            helper.assertTrue("requires".equals(Skills.blocker(player, EmergentStealth.id("soft_soles"))), "Soft Soles needs Shadow Walker");
            helper.assertTrue(Skills.unlock(player, EmergentStealth.id("shadow_walker")), "Unlock root");
            helper.assertTrue(Skills.progression(player).points(SkillPath.SHINOBI) == 2, "One point spent");
            helper.assertTrue("owned".equals(Skills.blocker(player, EmergentStealth.id("shadow_walker"))), "Can't buy twice");
            helper.assertTrue("capstone".equals(Skills.blocker(player, EmergentStealth.id("ghost"))), "Capstones need a mastery challenge");
        });
    }

    /** 3. Insight turns into skill points. */
    static void insightToPoints(GameTestHelper helper) {
        withPlayer(helper, player -> {
            for (int i = 0; i < 7; i++) {
                Skills.awardInsight(player, SkillPath.SHINOBI, Skills.INSIGHT_KNOCKOUT_UNSEEN);
            }
            helper.assertTrue(Skills.progression(player).points(SkillPath.SHINOBI) == 1, "105 Insight is one point");
            helper.assertTrue(Skills.progression(player).insight(SkillPath.SHINOBI) == 5, "5 Insight left over");
            helper.assertTrue(Skills.progression(player).points(SkillPath.SHOGUNATE) == 0, "Paths are separate");
        });
    }

    /** 4. Still Breath lowers visibility only while crouched and still; its cooldown is enforced. */
    static void stillBreath(GameTestHelper helper) {
        withPlayer(helper, player -> {
            player.setData(ESAttachments.PROGRESSION, Skills.progression(player).withPoints(SkillPath.SHINOBI, 10));
            Skills.unlock(player, EmergentStealth.id("shadow_walker"));
            Skills.unlock(player, EmergentStealth.id("soft_soles"));
            Skills.unlock(player, EmergentStealth.id("still_breath"));
            helper.assertTrue(Techniques.selected(player) == Techniques.STILL_BREATH, "Still Breath should be selected");
            helper.assertTrue(Techniques.use(player), "Technique should be usable");
            helper.assertTrue(!Techniques.use(player), "Cooldown should block a second use");
            player.setShiftKeyDown(false);
            helper.assertTrue(near(stat(player, StealthStat.VISIBILITY), 0.95F), "Standing: no Still Breath bonus");
            player.setShiftKeyDown(true);
            helper.assertTrue(near(stat(player, StealthStat.VISIBILITY), 0.95F * Techniques.STILL_BREATH_VISIBILITY),
                    "Crouched and still: Still Breath, got " + stat(player, StealthStat.VISIBILITY));
        });
    }
}
