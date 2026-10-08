package com.mcspacewizard.emergentstealth.registry;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.action.ActionPlayback;
import com.mcspacewizard.emergentstealth.action.CarryLink;
import com.mcspacewizard.emergentstealth.action.Stance;
import com.mcspacewizard.emergentstealth.network.BarkPayload;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Synced entity attachments for player verbs (design doc 17). The server sets them; clients animate them. */
public final class ESAttachments {
    private ESAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, EmergentStealth.MODID);

    /** Player stance: standing (incl. vanilla sneaking) or crawling. Saved with the player. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Stance>> STANCE = ATTACHMENT_TYPES.register("stance",
            () -> AttachmentType.builder(() -> Stance.STANDING).serialize(Stance.CODEC.fieldOf("stance"))
                    .sync(ESAttachments::hasMod, Stance.STREAM_CODEC).build());

    /** A timed action being played (takedowns). Not saved: actions are short. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ActionPlayback>> ACTION = ATTACHMENT_TYPES.register("action",
            () -> AttachmentType.builder(() -> ActionPlayback.NONE).sync(ESAttachments::hasMod, ActionPlayback.STREAM_CODEC).build());

    /** Body carrying: on the player, the body; on the body, the player. Not saved (dropped on logout/unload). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CarryLink>> CARRY = ATTACHMENT_TYPES.register("carry",
            () -> AttachmentType.builder(() -> CarryLink.NONE).sync(ESAttachments::hasMod, CarryLink.STREAM_CODEC).build());

    /** Only players with the mod's channels get attachment syncs (GameTest players have none). */
    private static boolean hasMod(IAttachmentHolder holder, ServerPlayer to) {
        return to.connection != null && to.connection.hasChannel(BarkPayload.TYPE);
    }

    // --- Beta toolkit, part B (design doc 21) ---

    /** Spyglass tags and the current focus. Synced only to the tagging player; not saved (tags last 60 s). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.mcspacewizard.emergentstealth.tool.SpyglassTags>> SPYGLASS_TAGS =
            ATTACHMENT_TYPES.register("spyglass_tags", () -> AttachmentType.builder(() -> com.mcspacewizard.emergentstealth.tool.SpyglassTags.EMPTY)
                    .sync((holder, to) -> holder == to && hasMod(holder, to), com.mcspacewizard.emergentstealth.tool.SpyglassTags.STREAM_CODEC).build());

    /** A sleep dart's stagger on an NPC (game-time timestamps). Saved, so a reload mid-stagger still knocks out. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.mcspacewizard.emergentstealth.tool.Stagger>> STAGGER =
            ATTACHMENT_TYPES.register("stagger", () -> AttachmentType.builder(() -> com.mcspacewizard.emergentstealth.tool.Stagger.NONE)
                    .serialize(com.mcspacewizard.emergentstealth.tool.Stagger.CODEC.fieldOf("stagger")).build());

    /** Extra key ids an NPC carries (besides key items in its equipment), set by {@code /es key npc}. Saved. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<java.util.List<String>>> NPC_KEYS =
            ATTACHMENT_TYPES.register("npc_keys", () -> AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of())
                    .serialize(com.mojang.serialization.Codec.STRING.listOf().fieldOf("keys")).build());
}
