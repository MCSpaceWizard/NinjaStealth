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

    /** The active stealth tool for quick use (design doc 21 §1). Saved, kept on death, synced to its owner only. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.mcspacewizard.emergentstealth.tool.ActiveTool>> ACTIVE_TOOL =
            ATTACHMENT_TYPES.register("active_tool", () -> AttachmentType.builder(() -> com.mcspacewizard.emergentstealth.tool.ActiveTool.NONE)
                    .serialize(com.mcspacewizard.emergentstealth.tool.ActiveTool.CODEC.fieldOf("item"))
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to && hasMod(holder, to), com.mcspacewizard.emergentstealth.tool.ActiveTool.STREAM_CODEC)
                    .build());

    /** Only players with the mod's channels get attachment syncs (GameTest players have none). */
    private static boolean hasMod(IAttachmentHolder holder, ServerPlayer to) {
        return to.connection != null && to.connection.hasChannel(BarkPayload.TYPE);
    }
}
