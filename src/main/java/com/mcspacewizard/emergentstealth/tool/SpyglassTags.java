package com.mcspacewizard.emergentstealth.tool;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player's spyglass tags (design doc 21 §2, P-11) plus what they're focusing on right now.
 * Held in a player attachment that is synced only to that player.
 *
 * @param tags        tagged NPCs (entity ids), oldest first, at most {@link SpyglassTagging#MAX_TAGS}
 * @param focusEntity the NPC under the scope right now, or -1
 * @param focusStart  game time the focus began (the client draws the progress from it)
 */
public record SpyglassTags(List<Tag> tags, int focusEntity, long focusStart) {
    public static final SpyglassTags EMPTY = new SpyglassTags(List.of(), -1, 0L);

    /** One tagged NPC, outlined for its tagger until {@code expiresAt} (game time). */
    public record Tag(int entityId, long expiresAt) {
        public static final StreamCodec<ByteBuf, Tag> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Tag::entityId,
                ByteBufCodecs.VAR_LONG, Tag::expiresAt,
                Tag::new);
    }

    public static final StreamCodec<ByteBuf, SpyglassTags> STREAM_CODEC = StreamCodec.composite(
            Tag.STREAM_CODEC.apply(ByteBufCodecs.list()), SpyglassTags::tags,
            ByteBufCodecs.VAR_INT, SpyglassTags::focusEntity,
            ByteBufCodecs.VAR_LONG, SpyglassTags::focusStart,
            SpyglassTags::new);

    public boolean isTagged(int entityId, long now) {
        for (Tag tag : tags) {
            if (tag.entityId() == entityId && tag.expiresAt() > now) {
                return true;
            }
        }
        return false;
    }

    /** Live tags only. */
    public List<Tag> active(long now) {
        List<Tag> live = new ArrayList<>();
        for (Tag tag : tags) {
            if (tag.expiresAt() > now) {
                live.add(tag);
            }
        }
        return live;
    }

    public SpyglassTags withFocus(int entityId, long start) {
        return new SpyglassTags(tags, entityId, start);
    }

    public SpyglassTags withTags(List<Tag> newTags) {
        return new SpyglassTags(List.copyOf(newTags), focusEntity, focusStart);
    }
}
