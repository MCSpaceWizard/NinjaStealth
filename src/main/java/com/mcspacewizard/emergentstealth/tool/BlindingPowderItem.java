package com.mcspacewizard.emergentstealth.tool;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Blinding powder (design doc 21 §2): a short-range puff. See {@link Blinding}.
 */
public class BlindingPowderItem extends Item implements StealthTool {
    public static final int COOLDOWN_TICKS = 20;

    public BlindingPowderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            player.swing(hand, true);
            return puff(serverPlayer, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean quickUse(ServerPlayer player, ItemStack stack, float charge) {
        if (puff(player, stack)) {
            player.swing(InteractionHand.MAIN_HAND, true);
            return true;
        }
        return false;
    }

    private boolean puff(ServerPlayer player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel level) || player.getCooldowns().isOnCooldown(stack)) {
            return false;
        }
        Blinding.puff(level, player);
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        return true;
    }
}
