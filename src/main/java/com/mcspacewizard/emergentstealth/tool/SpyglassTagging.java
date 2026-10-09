package com.mcspacewizard.emergentstealth.tool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.progression.StealthStat;
import com.mcspacewizard.emergentstealth.progression.StealthStats;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Spyglass tagging (design doc 21 §2, P-11), server side. Looking at a stealth NPC through a spyglass for
 * {@link #FOCUS_TICKS} tags it for {@link #TAG_TICKS}; at most {@link #MAX_TAGS} at once (a new tag replaces
 * the oldest). Tags live in the player's {@link ESAttachments#SPYGLASS_TAGS} attachment, which syncs only to
 * that player; the client outlines tagged NPCs through walls for them alone.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class SpyglassTagging {
    private SpyglassTagging() {}

    public static final int FOCUS_TICKS = 20;
    public static final int TAG_TICKS = 60 * 20;
    public static final int MAX_TAGS = 3;
    public static final double RANGE = 96.0;
    /** Hitboxes are this much bigger for the scope ray, so a distant NPC is easy to keep in view. */
    private static final double MARGIN = 0.3;

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    /** One server tick for this player (public for tests: GameTest players aren't ticked). */
    public static void tick(ServerPlayer player) {
        SpyglassTags tags = player.getExistingDataOrNull(ESAttachments.SPYGLASS_TAGS);
        boolean scoping = player.isScoping();
        if (!scoping && (tags == null || (tags.focusEntity() < 0 && tags.tags().isEmpty()))) {
            return;
        }
        if (tags == null) {
            tags = SpyglassTags.EMPTY;
        }
        ServerLevel level = player.level();
        long now = level.getGameTime();
        SpyglassTags updated = tags;

        // Expired or vanished tags go (checked once a second).
        if (now % 20 == 0 && !tags.tags().isEmpty()) {
            List<SpyglassTags.Tag> live = new ArrayList<>();
            for (SpyglassTags.Tag tag : tags.tags()) {
                Entity entity = level.getEntity(tag.entityId());
                if (tag.expiresAt() > now && entity != null && entity.isAlive()) {
                    live.add(tag);
                }
            }
            if (live.size() != tags.tags().size()) {
                updated = updated.withTags(live);
            }
        }

        StealthNpc target = scoping ? lookedAt(player) : null;
        int targetId = target == null ? -1 : target.getId();
        if (targetId != updated.focusEntity()) {
            updated = updated.withFocus(targetId, now);
        } else if (target != null && now - updated.focusStart() >= FOCUS_TICKS && !updated.isTagged(targetId, now)) {
            updated = tag(updated, targetId, now, maxTags(player));
            ToolEffects.tagPing(player, target);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPYGLASS_STOP_USING, SoundSource.PLAYERS, 0.6F, 1.8F);
        }
        if (updated != tags) {
            player.setData(ESAttachments.SPYGLASS_TAGS, updated);
        }
    }

    /** How many tags this player may hold: {@link #MAX_TAGS} plus the {@link StealthStat#TAG_COUNT} stat. */
    public static int maxTags(net.minecraft.world.entity.player.Player player) {
        return MAX_TAGS + Math.max(0, Math.round(StealthStats.get(player, StealthStat.TAG_COUNT)));
    }

    /** Adds a tag, dropping the oldest beyond {@link #MAX_TAGS}. */
    public static SpyglassTags tag(SpyglassTags tags, int entityId, long now) {
        return tag(tags, entityId, now, MAX_TAGS);
    }

    /** Adds a tag, dropping the oldest beyond {@code max}. */
    public static SpyglassTags tag(SpyglassTags tags, int entityId, long now, int max) {
        List<SpyglassTags.Tag> list = new ArrayList<>(tags.active(now));
        list.removeIf(tag -> tag.entityId() == entityId);
        list.add(new SpyglassTags.Tag(entityId, now + TAG_TICKS));
        list.sort(Comparator.comparingLong(SpyglassTags.Tag::expiresAt));
        while (list.size() > max) {
            list.removeFirst();
        }
        return tags.withTags(list);
    }

    /** The live stealth NPC in the middle of the player's view, with nothing solid in between. */
    public static @Nullable StealthNpc lookedAt(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(RANGE));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        AABB search = new AABB(eye, end).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, search,
                entity -> entity instanceof StealthNpc npc && npc.isAlive() && !npc.isBody(), (float) MARGIN);
        return hit != null && hit.getEntity() instanceof StealthNpc npc ? npc : null;
    }

    /** The NPCs this player has tagged right now. */
    public static List<SpyglassTags.Tag> tags(ServerPlayer player) {
        SpyglassTags tags = player.getExistingDataOrNull(ESAttachments.SPYGLASS_TAGS);
        return tags == null ? List.of() : tags.active(player.level().getGameTime());
    }
}
