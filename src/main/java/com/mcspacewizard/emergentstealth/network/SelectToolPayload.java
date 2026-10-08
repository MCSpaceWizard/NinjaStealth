package com.mcspacewizard.emergentstealth.network;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.tool.ActiveTool;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server intent: make this item the active stealth tool (design doc 21 §1, the tool wheel). The server
 * checks that it's in {@code #emergentstealth:tools} and in the player's inventory. {@link ActiveTool#NONE} clears.
 */
public record SelectToolPayload(ActiveTool tool) implements CustomPacketPayload {
    public static final Type<SelectToolPayload> TYPE = new Type<>(EmergentStealth.id("select_tool"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectToolPayload> STREAM_CODEC =
            ActiveTool.STREAM_CODEC.map(SelectToolPayload::new, SelectToolPayload::tool);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
