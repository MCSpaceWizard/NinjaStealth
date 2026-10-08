package com.mcspacewizard.emergentstealth.tool;

import com.mcspacewizard.emergentstealth.entity.ThrownItem;
import com.mcspacewizard.emergentstealth.stealth.sound.Throwing;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A stealth tool that is thrown (pebble, smoke bomb, firecracker, caltrops). It flies as a {@link ThrownItem}
 * (the S6 throw physics) and does its own thing on landing. Thrown with right-click from the hand, with the
 * throw key G, or with quick use V from anywhere in the inventory.
 */
public abstract class ThrowableToolItem extends Item implements StealthTool, ThrowableTool {
    /** Right-click from the hotbar: a medium throw. */
    public static final float RIGHT_CLICK_CHARGE = 0.4F;

    protected ThrowableToolItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            ThrownItem thrown = Throwing.throwFrom(serverPlayer, stack, RIGHT_CLICK_CHARGE);
            if (thrown == null) {
                return InteractionResult.FAIL;
            }
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean quickUse(ServerPlayer player, ItemStack stack, float charge) {
        ThrownItem thrown = Throwing.throwFrom(player, stack, charge);
        if (thrown != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return thrown != null;
    }
}
