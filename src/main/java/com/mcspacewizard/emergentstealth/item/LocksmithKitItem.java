package com.mcspacewizard.emergentstealth.item;

import java.util.Locale;
import java.util.function.Consumer;

import com.mcspacewizard.emergentstealth.world.lock.LockData;
import com.mcspacewizard.emergentstealth.world.lock.Locks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Locksmith's Kit, a map-maker tool (design doc 21 §2). Creative or op only.
 * <ul>
 *   <li>Right-click a door, trapdoor, gate, chest or barrel: locks it and hands you its key.
 *       The key name is the key in your off hand (several doors, one key), else the kit's anvil name, else a
 *       fresh {@code key_N}.</li>
 *   <li>Sneak + right-click: unlocks it.</li>
 * </ul>
 * Runs before the block's own use, so doors don't swing while you lock them.
 */
public class LocksmithKitItem extends Item {
    public LocksmithKitItem(Properties properties) {
        super(properties);
    }

    public static boolean mayUse(Player player) {
        return player.isCreative() || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || !Locks.isLockable(level.getBlockState(pos))) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!mayUse(player)) {
            player.sendOverlayMessage(Component.translatable("message.emergentstealth.locksmith.not_allowed"));
            return InteractionResult.FAIL;
        }
        if (player.isSecondaryUseActive()) {
            boolean removed = Locks.unlock(serverLevel, pos);
            player.sendOverlayMessage(Component.translatable(removed ? "message.emergentstealth.locksmith.unlocked"
                    : "message.emergentstealth.locksmith.not_locked"));
            if (removed) {
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.6F);
            }
            return InteractionResult.SUCCESS;
        }
        String offhandKey = KeyItem.keyId(player.getOffhandItem());
        String key = offhandKey != null ? offhandKey : keyName(serverLevel, stack);
        Locks.lock(serverLevel, pos, key, LockData.Lock.MIN_DIFFICULTY);
        if (offhandKey == null) {
            ItemStack keyStack = KeyItem.create(key);
            if (!player.getInventory().add(keyStack)) {
                player.drop(keyStack, false);
            }
        }
        player.sendOverlayMessage(Component.translatable("message.emergentstealth.locksmith.locked", key));
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 1.6F);
        return InteractionResult.SUCCESS;
    }

    /** The kit's anvil name (lower case, spaces to underscores), or the next unused {@code key_N}. */
    private static String keyName(ServerLevel level, ItemStack kit) {
        Component custom = kit.getCustomName();
        if (custom != null && !custom.getString().isBlank()) {
            return custom.getString().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        }
        LockData data = LockData.get(level);
        int i = 1;
        while (data.keyInUse("key_" + i)) {
            i++;
        }
        return "key_" + i;
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        builder.accept(Component.translatable("item.emergentstealth.locksmiths_kit.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
