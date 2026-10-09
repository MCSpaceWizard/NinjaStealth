package com.mcspacewizard.emergentstealth.command;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mcspacewizard.emergentstealth.authoring.CompoundDrafts;
import com.mcspacewizard.emergentstealth.authoring.StructureCatalog;
import com.mcspacewizard.emergentstealth.authoring.StructurePlacement;
import com.mcspacewizard.emergentstealth.authoring.StructureViewer;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.TemplateMirrorArgument;
import net.minecraft.commands.arguments.TemplateRotationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * {@code /es structure list|browse|place|undo} (design doc 32 §1): the structure viewer for scripts, tests and
 * the console. Game masters only.
 */
final class StructureCommands {
    private StructureCommands() {}

    private static final int LIST_LIMIT = 40;
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(
            id -> Component.translatable("message.emergentstealth.structure.unknown", String.valueOf(id)));
    private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, builder) -> SharedSuggestionProvider.suggest(
            StructureCatalog.ids(ctx.getSource().getServer()).stream().map(Identifier::toString), builder);

    static ArgumentBuilder<CommandSourceStack, ?> structure() {
        return Commands.literal("structure")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx, ""))
                        .then(Commands.argument("filter", StringArgumentType.greedyString())
                                .executes(ctx -> list(ctx, StringArgumentType.getString(ctx, "filter")))))
                .then(Commands.literal("browse").executes(ctx -> {
                    StructureViewer.sendList(ctx.getSource().getPlayerOrException(), true);
                    return 1;
                }))
                .then(Commands.literal("place").then(Commands.argument("id", IdentifierArgument.id()).suggests(IDS)
                        .executes(ctx -> place(ctx, BlockPos.containing(ctx.getSource().getPosition()), Rotation.NONE, Mirror.NONE))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> place(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"), Rotation.NONE, Mirror.NONE))
                                .then(Commands.argument("rotation", TemplateRotationArgument.templateRotation())
                                        .executes(ctx -> place(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                TemplateRotationArgument.getRotation(ctx, "rotation"), Mirror.NONE))
                                        .then(Commands.argument("mirror", TemplateMirrorArgument.templateMirror())
                                                .executes(ctx -> place(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                        TemplateRotationArgument.getRotation(ctx, "rotation"),
                                                        TemplateMirrorArgument.getMirror(ctx, "mirror"))))))))
                .then(Commands.literal("undo").executes(StructureCommands::undo));
    }

    private static UUID author(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null ? player.getUUID() : StructurePlacement.CONSOLE;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, String filter) {
        String needle = filter.toLowerCase(Locale.ROOT);
        List<Identifier> ids = StructureCatalog.ids(ctx.getSource().getServer()).stream()
                .filter(id -> id.toString().contains(needle)).toList();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.structure.list", ids.size()), false);
        ids.stream().limit(LIST_LIMIT).forEach(id -> ctx.getSource().sendSuccess(() -> Component.literal("  " + id), false));
        if (ids.size() > LIST_LIMIT) {
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.structure.list_more", ids.size() - LIST_LIMIT), false);
        }
        return ids.size();
    }

    private static int place(CommandContext<CommandSourceStack> ctx, BlockPos origin, Rotation rotation, Mirror mirror) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "id");
        BoundingBox box = StructurePlacement.place(ctx.getSource().getLevel(), author(ctx.getSource()), id, origin, rotation, mirror);
        if (box == null) {
            throw UNKNOWN.create(id);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.emergentstealth.structure.placed", id.toString(),
                box.minX(), box.minY(), box.minZ()), true);
        CompoundDrafts.joinDraft(author(ctx.getSource()), message -> ctx.getSource().sendSuccess(() -> message, false));
        return 1;
    }

    private static int undo(CommandContext<CommandSourceStack> ctx) {
        BoundingBox box = StructurePlacement.undo(author(ctx.getSource()));
        ctx.getSource().sendSuccess(() -> box == null ? Component.translatable("message.emergentstealth.structure.nothing_to_undo")
                : Component.translatable("message.emergentstealth.structure.undone", box.minX(), box.minY(), box.minZ()), true);
        return box == null ? 0 : 1;
    }
}
