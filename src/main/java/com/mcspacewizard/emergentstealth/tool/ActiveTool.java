package com.mcspacewizard.emergentstealth.tool;

import com.mojang.serialization.Codec;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The player's active stealth tool (design doc 21 §1): which item quick use (V) uses. An item type, not a slot, so
 * it keeps working as stacks move around the inventory. {@link #NONE} (air) = nothing chosen.
 */
public record ActiveTool(Item item) {
    public static final ActiveTool NONE = new ActiveTool(Items.AIR);

    public static final Codec<ActiveTool> CODEC = BuiltInRegistries.ITEM.byNameCodec().xmap(ActiveTool::new, ActiveTool::item);
    public static final StreamCodec<RegistryFriendlyByteBuf, ActiveTool> STREAM_CODEC =
            ByteBufCodecs.registry(Registries.ITEM).map(ActiveTool::new, ActiveTool::item);

    public boolean isNone() {
        return item == Items.AIR;
    }
}
