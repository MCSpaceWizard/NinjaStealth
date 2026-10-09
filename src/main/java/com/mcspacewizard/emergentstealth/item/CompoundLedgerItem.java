package com.mcspacewizard.emergentstealth.item;

import com.mcspacewizard.emergentstealth.authoring.Ledger;
import com.mcspacewizard.emergentstealth.authoring.StructureViewer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The compound authoring item (design doc 32 §3). Creative and game masters only. Use it on a structure you just
 * placed to start a compound from it (anywhere else, the compound starts at that block); with a compound open,
 * it opens the panel that adds zones and routes, takes markers out, saves and discards.
 */
public class CompoundLedgerItem extends Item {
    public CompoundLedgerItem(Properties properties) {
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
        Ledger.open(player, context.getClickedPos());
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!StructureViewer.mayAuthor(serverPlayer)) {
            return InteractionResult.FAIL;
        }
        Ledger.open(serverPlayer, serverPlayer.blockPosition());
        return InteractionResult.SUCCESS;
    }
}
