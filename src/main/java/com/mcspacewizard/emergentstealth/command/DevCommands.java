package com.mcspacewizard.emergentstealth.command;

import java.util.Collection;
import java.util.List;

import com.mcspacewizard.emergentstealth.network.OpenDialoguePreviewPayload;
import com.mcspacewizard.emergentstealth.registry.ESNetwork;
import com.mojang.brigadier.builder.ArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /es dev ...} (op): developer previews. {@code dialogue [players]} opens the Sumi dialogue preview (doc 31 §3.3). */
public final class DevCommands {
    private DevCommands() {}

    static ArgumentBuilder<CommandSourceStack, ?> dev() {
        return Commands.literal("dev")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("dialogue")
                        .executes(ctx -> dialogue(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> dialogue(ctx.getSource(), EntityArgument.getPlayers(ctx, "players")))));
    }

    private static int dialogue(CommandSourceStack source, Collection<ServerPlayer> players) {
        int sent = openDialoguePreview(players);
        if (sent == 0) {
            source.sendFailure(Component.translatable("commands.emergentstealth.dev.dialogue.unsupported"));
        }
        return sent;
    }

    /** Sends the preview to each player whose client has the mod; returns how many got it. */
    public static int openDialoguePreview(Collection<ServerPlayer> players) {
        int sent = 0;
        for (ServerPlayer player : players) {
            if (player.connection != null && player.connection.hasChannel(OpenDialoguePreviewPayload.TYPE)) {
                ESNetwork.sendIfSupported(player, OpenDialoguePreviewPayload.INSTANCE);
                sent++;
            }
        }
        return sent;
    }
}
