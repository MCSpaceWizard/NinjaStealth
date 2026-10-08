package com.mcspacewizard.emergentstealth.progression;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.registry.ESRegistries;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Skill unlocking and Insight awards (design doc 26). Shared by the server and the skill tree screen. */
public final class Skills {
    private Skills() {}

    public static PlayerProgression progression(Player player) {
        return player.getData(ESAttachments.PROGRESSION);
    }

    public static Registry<SkillDefinition> registry(Player player) {
        return player.level().registryAccess().lookupOrThrow(ESRegistries.SKILL);
    }

    /** Why a skill can't be unlocked right now, or null if it can. Usable on the client for the screen. */
    public static @Nullable String blocker(Player player, Identifier skillId) {
        SkillDefinition skill = registry(player).getValue(skillId);
        if (skill == null) {
            return "unknown";
        }
        PlayerProgression progression = progression(player);
        if (progression.has(skillId)) {
            return "owned";
        }
        if (skill.capstone()) {
            return "capstone";
        }
        if (!skill.requires().isEmpty()) {
            long owned = skill.requires().stream().filter(progression::has).count();
            if (skill.requireAll() ? owned < skill.requires().size() : owned == 0) {
                return "requires";
            }
        }
        if (progression.points(skill.path()) < skill.cost()) {
            return "points";
        }
        return null;
    }

    public static boolean unlock(ServerPlayer player, Identifier skillId) {
        if (blocker(player, skillId) != null) {
            return false;
        }
        SkillDefinition skill = registry(player).getValue(skillId);
        PlayerProgression progression = progression(player);
        progression = progression.withPoints(skill.path(), progression.points(skill.path()) - skill.cost()).withUnlocked(skillId);
        player.setData(ESAttachments.PROGRESSION, progression);
        StealthStats.invalidate(player);
        SkillAttributes.refresh(player);
        return true;
    }

    public static void handleUnlock(UnlockSkillPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            unlock(player, payload.skill());
        }
    }

    /** Insight per deed (design doc 26 §2). */
    public static final int INSIGHT_KNOCKOUT_UNSEEN = 15;
    public static final int INSIGHT_KILL_UNSEEN = 10;
    public static final int INSIGHT_BODY_HIDDEN = 10;
    public static final int INSIGHT_LOCKPICK = 5;
    public static final int INSIGHT_DISTRACTION = 3;
    public static final int INSIGHT_ESCAPE = 20;

    /** Clears all skills, Insight and points (op command; the hideout shrine respec comes with S16). */
    public static void reset(ServerPlayer player) {
        player.setData(ESAttachments.PROGRESSION, PlayerProgression.EMPTY);
        player.setData(ESAttachments.TECHNIQUES, TechniqueState.EMPTY);
        StealthStats.invalidate(player);
        SkillAttributes.refresh(player);
    }

    /** Awards Insight for a stealth deed (design doc 26 §2). */
    public static void awardInsight(ServerPlayer player, SkillPath path, int amount) {
        if (amount <= 0 || player.isCreative() || player.isSpectator()) {
            return;
        }
        PlayerProgression before = progression(player);
        PlayerProgression after = before.withInsight(path, amount);
        player.setData(ESAttachments.PROGRESSION, after);
        if (after.points(path) > before.points(path)) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.emergentstealth.skill_point",
                    net.minecraft.network.chat.Component.translatable("skill_path.emergentstealth." + path.getSerializedName())));
        }
    }
}
