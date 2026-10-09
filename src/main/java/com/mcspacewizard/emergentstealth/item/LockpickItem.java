package com.mcspacewizard.emergentstealth.item;

import com.mcspacewizard.emergentstealth.world.lock.Lockpicking;
import com.mcspacewizard.emergentstealth.world.lock.Locks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Lockpicks (design doc 21 §2, durability 8). Used on a locked door or chest, they open the timing-ring
 * minigame ({@link Lockpicking}). On anything unlocked they do nothing special: the block is used as normal.
 */
public class LockpickItem extends Item {
    public LockpickItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (!Locks.isLockable(context.getLevel().getBlockState(context.getClickedPos()))) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            // The client can't know about locks: don't predict a door swing, let the server decide.
            return InteractionResult.SUCCESS;
        }
        if (!Locks.isLocked(level, context.getClickedPos())) {
            return InteractionResult.PASS;
        }
        Lockpicking.start(player, context.getClickedPos(), context.getHand());
        return InteractionResult.SUCCESS;
    }
}
