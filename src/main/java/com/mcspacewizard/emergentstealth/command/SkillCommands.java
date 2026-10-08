package com.mcspacewizard.emergentstealth.command;

import java.util.Collection;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mcspacewizard.emergentstealth.progression.SkillPath;
import com.mcspacewizard.emergentstealth.progression.Skills;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.registry.ESRegistries;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** {@code /es skills ...} (op): points, Insight, unlock, reset (design doc 26). */
final class SkillCommands {
    private SkillCommands() {}

    static ArgumentBuilder<CommandSourceStack, ?> skills() {
        return Commands.literal("skills")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("points").then(Commands.argument("players", EntityArgument.players())
                        .then(pathLiteral("shinobi", SkillPath.SHINOBI)).then(pathLiteral("shogunate", SkillPath.SHOGUNATE))))
                .then(Commands.literal("reset").then(Commands.argument("players", EntityArgument.players()).executes(ctx -> {
                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
                    players.forEach(Skills::reset);
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.skills.reset", players.size()), true);
                    return players.size();
                })))
                .then(Commands.literal("unlock").then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("skill", IdentifierArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().registryAccess()
                                        .lookupOrThrow(ESRegistries.SKILL).keySet().stream().map(Identifier::toString), builder))
                                .executes(ctx -> {
                                    Identifier skill = IdentifierArgument.getId(ctx, "skill");
                                    int done = 0;
                                    for (ServerPlayer player : EntityArgument.getPlayers(ctx, "players")) {
                                        // Free for testing: grant the points first.
                                        var def = Skills.registry(player).getValue(skill);
                                        if (def != null) {
                                            var progression = Skills.progression(player);
                                            player.setData(ESAttachments.PROGRESSION,
                                                    progression.withPoints(def.path(), progression.points(def.path()) + def.cost()));
                                        }
                                        if (Skills.unlock(player, skill)) {
                                            done++;
                                        }
                                    }
                                    int count = done;
                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.skills.unlocked", skill.toString(), count), true);
                                    return done;
                                }))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> pathLiteral(String name, SkillPath path) {
        return Commands.literal(name).then(Commands.argument("amount", IntegerArgumentType.integer(0, 1000)).executes(ctx -> {
            int amount = IntegerArgumentType.getInteger(ctx, "amount");
            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
            for (ServerPlayer player : players) {
                var progression = Skills.progression(player);
                player.setData(ESAttachments.PROGRESSION, progression.withPoints(path, progression.points(path) + amount));
            }
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.skills.points", amount, players.size()), true);
            return players.size();
        }));
    }
}
