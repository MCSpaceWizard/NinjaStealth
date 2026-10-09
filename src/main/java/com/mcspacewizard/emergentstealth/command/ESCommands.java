package com.mcspacewizard.emergentstealth.command;

import java.util.Map;
import java.util.TreeMap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.registry.ESRegistries;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Server commands: {@code /emergentstealth ...}, aliased as {@code /es}. */
public final class ESCommands {
    private ESCommands() {}

    private static final DynamicCommandExceptionType UNKNOWN_ARCHETYPE = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.emergentstealth.npc.unknown_archetype", String.valueOf(id)));

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("emergentstealth")
                .then(Commands.literal("npc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("archetype", IdentifierArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                ctx.getSource().registryAccess().lookupOrThrow(ESRegistries.ARCHETYPE)
                                                        .keySet().stream().map(Identifier::toString), builder))
                                        .executes(ESCommands::spawnNpc)))
                        .then(Commands.literal("list")
                                .executes(ESCommands::listNpcs))
                        .then(Commands.literal("knockout")
                                .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.entities())
                                        .executes(ESCommands::knockOut)))
                        .then(PatrolCommands.behaviour()))
                .then(SkillCommands.skills())
                .then(PatrolCommands.patrol())
                .then(PatrolCommands.routine())
                .then(DevCommands.dev())
                .then(StructureCommands.structure()));
        dispatcher.register(Commands.literal("es").redirect(root));
    }

    /** Knocks NPCs out on the spot (for testing bodies and waking without a takedown). */
    private static int knockOut(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int count = 0;
        for (net.minecraft.world.entity.Entity entity : net.minecraft.commands.arguments.EntityArgument.getEntities(ctx, "targets")) {
            if (entity instanceof StealthNpc npc && npc.knockOut(ctx.getSource().getLevel(), null)) {
                count++;
            }
        }
        int knocked = count;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.npc.knocked_out", knocked), true);
        return count;
    }

    private static int spawnNpc(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Identifier archetype = IdentifierArgument.getId(ctx, "archetype");
        if (!source.registryAccess().lookupOrThrow(ESRegistries.ARCHETYPE).containsKey(archetype)) {
            throw UNKNOWN_ARCHETYPE.create(archetype);
        }

        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        StealthNpc npc = ESEntities.STEALTH_NPC.get().create(level, EntitySpawnReason.COMMAND);
        if (npc == null) {
            return 0;
        }
        npc.snapTo(pos, source.getRotation().y, 0.0F);
        npc.setArchetypeId(archetype);
        EventHooks.finalizeMobSpawn(npc, level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.COMMAND, null);
        level.addFreshEntityWithPassengers(npc);

        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.npc.spawned", archetype.toString()), true);
        return 1;
    }

    private static int listNpcs(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Map<String, Integer> counts = new TreeMap<>();
        for (StealthNpc npc : source.getLevel().getEntities(ESEntities.STEALTH_NPC.get(), npc -> true)) {
            counts.merge(npc.getArchetypeId().toString(), 1, Integer::sum);
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.npc.list", total), false);
        counts.forEach((archetype, count) -> source.sendSuccess(() -> Component.literal("  " + archetype + ": " + count), false));
        return total;
    }
}
