package com.mcspacewizard.emergentstealth.command;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoute;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoutes;
import com.mcspacewizard.emergentstealth.authoring.Compound;
import com.mcspacewizard.emergentstealth.authoring.CompoundDrafts;
import com.mcspacewizard.emergentstealth.authoring.CompoundPlacer;
import com.mcspacewizard.emergentstealth.authoring.CompoundSaver;
import com.mcspacewizard.emergentstealth.authoring.Ledger;
import com.mcspacewizard.emergentstealth.authoring.Compounds;
import com.mcspacewizard.emergentstealth.authoring.PlacedCompounds;
import com.mcspacewizard.emergentstealth.authoring.StructurePlacement;
import com.mcspacewizard.emergentstealth.authoring.Transform;
import com.mcspacewizard.emergentstealth.authoring.Zone;
import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.TemplateMirrorArgument;
import net.minecraft.commands.arguments.TemplateRotationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * {@code /es compound ...} (design doc 32 §3): author a compound (start, add markers, save), and place, list,
 * export or remove compounds. Game masters only. The Compound Ledger item will drive the same steps.
 *
 * <p>Compound names without a namespace are ours: {@code samurai_mini_fort} means
 * {@code emergentstealth:samurai_mini_fort}.
 */
final class CompoundCommands {
    private CompoundCommands() {}

    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.emergentstealth.compound.unknown", String.valueOf(id)));
    private static final SimpleCommandExceptionType NO_DRAFT = new SimpleCommandExceptionType(
            Component.translatable("commands.emergentstealth.compound.no_draft"));
    private static final SimpleCommandExceptionType NOTHING_PLACED = new SimpleCommandExceptionType(
            Component.translatable("commands.emergentstealth.compound.nothing_placed"));
    private static final DynamicCommandExceptionType UNKNOWN_ROUTE = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.emergentstealth.compound.unknown_route", String.valueOf(name)));
    private static final DynamicCommandExceptionType UNKNOWN_COPY = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.emergentstealth.compound.unknown_copy", String.valueOf(id)));
    private static final DynamicCommandExceptionType OTHER_DIMENSION = new DynamicCommandExceptionType(
            id -> Component.translatable("message.emergentstealth.compound.other_dimension", String.valueOf(id)));
    private static final DynamicCommandExceptionType SAVE_FAILED = new DynamicCommandExceptionType(
            error -> Component.translatable("commands.emergentstealth.compound.save_failed", String.valueOf(error)));

    private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, builder) -> SharedSuggestionProvider.suggest(
            Compounds.ids().stream().map(Identifier::toString), builder);
    private static final SuggestionProvider<CommandSourceStack> ROUTES = (ctx, builder) -> SharedSuggestionProvider.suggest(
            PatrolRoutes.get(ctx.getSource().getLevel()).all().stream().map(PatrolRoute::name), builder);
    private static final SuggestionProvider<CommandSourceStack> COPIES = (ctx, builder) -> SharedSuggestionProvider.suggest(
            PlacedCompounds.get(ctx.getSource().getLevel()).all().stream().map(c -> String.valueOf(c.id())), builder);
    private static final SuggestionProvider<CommandSourceStack> ACCESS = (ctx, builder) -> SharedSuggestionProvider.suggest(
            java.util.Arrays.stream(Zone.Access.values()).map(Zone.Access::getSerializedName), builder);

    static ArgumentBuilder<CommandSourceStack, ?> compound() {
        return Commands.literal("compound")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("start").then(Commands.argument("name", IdentifierArgument.id())
                        .executes(ctx -> start(ctx, null))
                        .then(Commands.argument("origin", BlockPosArgument.blockPos())
                                .executes(ctx -> start(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "origin"))))))
                .then(Commands.literal("add")
                        .then(Commands.literal("route").then(Commands.argument("route", StringArgumentType.word()).suggests(ROUTES)
                                .executes(CompoundCommands::addRoute)))
                        .then(Commands.literal("npcs").then(Commands.argument("targets", EntityArgument.entities())
                                .executes(CompoundCommands::addNpcs)))
                        .then(Commands.literal("zone").then(Commands.argument("zone", StringArgumentType.word()).suggests(ZoneCommands.ZONES)
                                .executes(CompoundCommands::copyZone)
                                .then(Commands.argument("access", StringArgumentType.word()).suggests(ACCESS)
                                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                        .executes(ctx -> addZone(ctx, Optional.empty()))
                                                        .then(Commands.argument("hours_from", IntegerArgumentType.integer(0, 24))
                                                                .then(Commands.argument("hours_to", IntegerArgumentType.integer(0, 24))
                                                                        .executes(ctx -> addZone(ctx, Optional.of(new Zone.Hours(
                                                                                IntegerArgumentType.getInteger(ctx, "hours_from"),
                                                                                IntegerArgumentType.getInteger(ctx, "hours_to")))))))))))))
                .then(Commands.literal("info").executes(CompoundCommands::info))
                .then(Commands.literal("save").executes(CompoundCommands::save))
                .then(Commands.literal("lights").then(Commands.literal("all").executes(ctx -> lightRule(ctx, Compound.Relight.ALL)))
                        .then(Commands.literal("none").executes(ctx -> lightRule(ctx, Compound.Relight.NONE))))
                .then(Commands.literal("light").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(CompoundCommands::toggleLight)))
                .then(Commands.literal("ground").then(Commands.argument("module", IntegerArgumentType.integer(1))
                        .then(Commands.argument("layer", IntegerArgumentType.integer(0, 512))
                                .executes(CompoundCommands::ground))))
                .then(Commands.literal("cancel").executes(CompoundCommands::cancel))
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
                .then(Commands.literal("list").executes(CompoundCommands::list))
                .then(Commands.literal("copies").executes(CompoundCommands::copies))
                .then(Commands.literal("export").then(Commands.argument("id", IdentifierArgument.id()).suggests(IDS)
                        .executes(CompoundCommands::export)))
                .then(Commands.literal("reset").then(Commands.argument("copy", IntegerArgumentType.integer(1)).suggests(COPIES)
                        .executes(CompoundCommands::reset)))
                .then(Commands.literal("remove").then(Commands.argument("copy", IntegerArgumentType.integer(1)).suggests(COPIES)
                        .executes(CompoundCommands::remove)));
    }

    /** {@code minecraft:x} (what a bare name parses as) means {@code emergentstealth:x}. */
    private static Identifier compoundId(CommandContext<CommandSourceStack> ctx, String argument) {
        Identifier id = IdentifierArgument.getId(ctx, argument);
        return id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? EmergentStealth.id(id.getPath()) : id;
    }

    private static UUID author(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null ? player.getUUID() : StructurePlacement.CONSOLE;
    }

    private static CompoundDrafts.Draft draft(CommandSourceStack source) throws CommandSyntaxException {
        CompoundDrafts.Draft draft = CompoundDrafts.get(author(source));
        if (draft == null) {
            throw NO_DRAFT.create();
        }
        return draft;
    }

    /** The author's draft, which must be in the dimension they're adding markers from. */
    private static CompoundDrafts.Draft draftHere(CommandSourceStack source) throws CommandSyntaxException {
        CompoundDrafts.Draft draft = draft(source);
        if (!draft.dimension().equals(source.getLevel().dimension())) {
            throw OTHER_DIMENSION.create(draft.id());
        }
        return draft;
    }

    /** Starts a draft at {@code origin}, or at the author's last placed structure (which becomes its first module). */
    private static int start(CommandContext<CommandSourceStack> ctx, @org.jspecify.annotations.Nullable BlockPos origin) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Identifier id = compoundId(ctx, "name");
        UUID author = author(source);
        CompoundDrafts.Draft draft;
        if (origin != null) {
            draft = CompoundDrafts.start(author, id, source.getLevel().dimension(), origin, Compound.EMPTY);
        } else {
            StructurePlacement.Placed last = StructurePlacement.last(author);
            if (last == null) {
                throw NOTHING_PLACED.create();
            }
            draft = CompoundDrafts.start(author, id, last.dimension(), last.origin(), Compound.EMPTY);
            draft = CompoundDrafts.onPlaced(source.getServer(), author, last);
        }
        BlockPos at = draft.origin();
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.started", id.toString(),
                at.getX(), at.getY(), at.getZ()), false);
        return 1;
    }

    private static int addRoute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        CompoundDrafts.Draft draft = draftHere(source);
        String name = StringArgumentType.getString(ctx, "route");
        PatrolRoute route = PatrolRoutes.get(source.getLevel()).get(name).orElseThrow(() -> UNKNOWN_ROUTE.create(name));
        CompoundDrafts.addRoute(author(source), draft, route);
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.added_route", name, route.waypoints().size()), false);
        return 1;
    }

    private static int addNpcs(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        CompoundDrafts.Draft draft = draftHere(source);
        int before = draft.compound().spawns().size();
        for (Entity entity : EntityArgument.getEntities(ctx, "targets")) {
            if (entity instanceof StealthNpc npc && npc.level().dimension().equals(draft.dimension())) {
                draft = CompoundDrafts.addNpc(author(source), draft, npc);
            }
        }
        int added = draft.compound().spawns().size() - before;
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.added_npcs", added), false);
        return added;
    }

    private static int addZone(CommandContext<CommandSourceStack> ctx, Optional<Zone.Hours> hours) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        CompoundDrafts.Draft draft = draftHere(source);
        String name = StringArgumentType.getString(ctx, "zone");
        String accessName = StringArgumentType.getString(ctx, "access");
        Zone.Access access = java.util.Arrays.stream(Zone.Access.values()).filter(a -> a.getSerializedName().equals(accessName)).findFirst()
                .orElseThrow(() -> new SimpleCommandExceptionType(Component.translatable("commands.emergentstealth.compound.bad_access", accessName)).create());
        BoundingBox box = BoundingBox.fromCorners(BlockPosArgument.getLoadedBlockPos(ctx, "from"), BlockPosArgument.getLoadedBlockPos(ctx, "to"));
        CompoundDrafts.addZone(author(source), draft, name, access, box, hours);
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.added_zone", name, accessName), false);
        return 1;
    }

    private static int copyZone(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        CompoundDrafts.Draft draft = draftHere(source);
        Zone zone = ZoneCommands.zone(ctx, "zone");
        CompoundDrafts.addZone(author(source), draft, zone);
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.added_zone", zone.name(),
                zone.access().getSerializedName()), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CompoundDrafts.Draft draft = draft(ctx.getSource());
        Compound c = draft.compound();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.info", draft.id().toString(),
                draft.origin().getX(), draft.origin().getY(), draft.origin().getZ(),
                c.structures().size(), c.routes().size(), c.spawns().size(), c.zones().size()), false);
        for (int i = 0; i < c.structures().size(); i++) {
            Compound.Module module = c.structures().get(i);
            int number = i + 1;
            ctx.getSource().sendSuccess(() -> Component.literal("  " + number + ". " + module.template() + " @ " + module.offset().toShortString()
                    + " " + module.rotation().getSerializedName() + ", ground " + module.ground()), false);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.lights_info",
                c.lights().relight().getSerializedName(), c.lights().except().size()), false);
        return 1;
    }

    private static int save(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        try {
            CompoundSaver.Saved saved = CompoundSaver.save(source.getServer(), author(source), draft(source));
            Ledger.savedMessages(saved).forEach(message -> source.sendSuccess(() -> message, true));
        } catch (IOException e) {
            throw SAVE_FAILED.create(e.getMessage());
        }
        return 1;
    }

    private static int lightRule(CommandContext<CommandSourceStack> ctx, Compound.Relight relight) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        CompoundDrafts.Draft draft = draft(source);
        CompoundDrafts.setLights(author(source), draft, new Compound.Lights(relight, java.util.List.of()));
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.lights_rule", relight.getSerializedName()), false);
        return 1;
    }

    private static int toggleLight(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        CompoundDrafts.Draft draft = draftHere(source);
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        Compound.Lights lights = draft.compound().lights().toggled(draft.local(pos));
        CompoundDrafts.setLights(author(source), draft, lights);
        boolean relit = lights.relights(draft.local(pos));
        source.sendSuccess(() -> Component.translatable(relit ? "commands.emergentstealth.compound.light_relit" : "commands.emergentstealth.compound.light_dark",
                pos.getX(), pos.getY(), pos.getZ()), false);
        return 1;
    }

    private static int ground(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        CompoundDrafts.Draft draft = draft(source);
        int module = IntegerArgumentType.getInteger(ctx, "module");
        int layer = IntegerArgumentType.getInteger(ctx, "layer");
        if (module > draft.compound().structures().size()) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.emergentstealth.compound.no_module", module)).create();
        }
        CompoundDrafts.setGround(author(source), draft, module - 1, layer);
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.ground_set", module, layer), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        int id = IntegerArgumentType.getInteger(ctx, "copy");
        PlacedCompounds.Copy copy = PlacedCompounds.get(source.getLevel()).get(id).orElseThrow(() -> UNKNOWN_COPY.create(id));
        Compound compound = Compounds.get(copy.compound());
        if (compound == null) {
            throw UNKNOWN.create(copy.compound());
        }
        CompoundPlacer.ResetResult result = CompoundPlacer.reset(source.getLevel(), copy, compound);
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.reset", id, result.kept(), result.respawned(),
                result.cleared()), true);
        if (result.skipped() > 0) {
            source.sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.reset_skipped", result.skipped()), false);
        }
        return result.respawned();
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CompoundDrafts.Draft draft = CompoundDrafts.close(author(ctx.getSource()));
        if (draft == null) {
            throw NO_DRAFT.create();
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.cancelled", draft.id().toString()), false);
        return 1;
    }

    private static int place(CommandContext<CommandSourceStack> ctx, BlockPos origin, Rotation rotation, Mirror mirror) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Identifier id = compoundId(ctx, "id");
        Compound compound = Compounds.get(id);
        if (compound == null) {
            throw UNKNOWN.create(id);
        }
        CompoundPlacer.Result result = CompoundPlacer.place(source.getLevel(), author(source), id, compound, origin, new Transform(mirror, rotation));
        if (result.copy() == null) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.emergentstealth.compound.missing_templates",
                    result.missing().toString())).create();
        }
        PlacedCompounds.Copy copy = result.copy();
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.placed", id.toString(), copy.id(),
                origin.getX(), origin.getY(), origin.getZ(), copy.npcs().size(), copy.routes().size(), copy.zones().size()), true);
        return copy.id();
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var ids = Compounds.ids();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.list", ids.size()), false);
        ids.forEach(id -> ctx.getSource().sendSuccess(() -> Component.literal("  " + id), false));
        return ids.size();
    }

    private static int copies(CommandContext<CommandSourceStack> ctx) {
        var copies = PlacedCompounds.get(ctx.getSource().getLevel()).all();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.copies", copies.size()), false);
        copies.forEach(c -> ctx.getSource().sendSuccess(() -> Component.literal("  #" + c.id() + " " + c.compound() + " @ "
                + c.origin().toShortString() + " " + c.rotation().getSerializedName() + (c.mirror() == Mirror.NONE ? "" : " mirrored")), false));
        return copies.size();
    }

    private static int export(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Identifier id = compoundId(ctx, "id");
        try {
            Compounds.Exported exported = Compounds.export(ctx.getSource().getServer(), id);
            if (exported == null) {
                throw UNKNOWN.create(id);
            }
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.exported", id.toString(),
                    exported.file().toString(), exported.templates()), false);
        } catch (IOException e) {
            throw SAVE_FAILED.create(e.getMessage());
        }
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int copy = IntegerArgumentType.getInteger(ctx, "copy");
        if (!CompoundPlacer.remove(ctx.getSource().getLevel(), copy)) {
            throw UNKNOWN_COPY.create(copy);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.compound.removed", copy), true);
        return 1;
    }
}
