package com.mcspacewizard.emergentstealth.item;

import com.mcspacewizard.emergentstealth.authoring.MusterRoll;
import com.mcspacewizard.emergentstealth.authoring.StructureViewer;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The NPC spawner (design doc 32 §4). Creative and game masters only. Right-click a block to open the Muster Roll
 * panel for NPCs standing on it; with a compound draft open, they join it as a spawn marker.
 */
public class MusterRollItem extends Item {
    public MusterRollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!StructureViewer.mayAuthor(player)) {
            return InteractionResult.FAIL;
        }
        MusterRoll.open(player, context.getClickedPos().relative(context.getClickedFace()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer) {
            player.sendOverlayMessage(Component.translatable("message.emergentstealth.muster.click_block"));
        }
        return InteractionResult.PASS;
    }
}
