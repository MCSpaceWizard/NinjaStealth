package com.mcspacewizard.emergentstealth.tool;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The toolbelt (see {@link Toolbelt}). Use it to open its slots. */
public class ToolbeltItem extends Item {
    public ToolbeltItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            int slot = hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : player.getInventory().getSelectedSlot();
            open(serverPlayer, slot);
        }
        return InteractionResult.SUCCESS;
    }

    /** Opens the belt in inventory slot {@code slot}. */
    public static void open(ServerPlayer player, int slot) {
        Component title = player.getInventory().getItem(slot).getHoverName();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ToolbeltMenu(id, inventory, slot), title),
                buf -> buf.writeVarInt(slot));
    }
}
