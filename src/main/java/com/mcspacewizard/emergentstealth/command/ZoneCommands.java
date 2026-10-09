package com.mcspacewizard.emergentstealth.command;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.mcspacewizard.emergentstealth.authoring.Trespass;
import com.mcspacewizard.emergentstealth.authoring.Zone;
import com.mcspacewizard.emergentstealth.authoring.ZoneTool;
import com.mcspacewizard.emergentstealth.authoring.Zones;
import com.mcspacewizard.emergentstealth.ai.routine.Schedule;
import com.mcspacewizard.emergentstealth.registry.ESDataComponents;
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
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * {@code /es zone ...}: the command side of the Surveyor's Rope (design doc 32 §2), for scripts, tests and
 * fine edits. Zones live per dimension; every command works on the sender's.
 */
final class ZoneCommands {
    private ZoneCommands() {}

    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(
            name -> Component.translatable("message.emergentstealth.zone.unknown", String.valueOf(name)));
    private static final DynamicCommandExceptionType BAD_ACCESS = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.emergentstealth.compound.bad_access", String.valueOf(name)));
    private static final DynamicCommandExceptionType BAD_NAME = new DynamicCommandExceptionType(
            name -> Component.translatable("message.emergentstealth.zone.bad_name", String.valueOf(name)));
    private static final SimpleCommandExceptionType NO_ROPE = new SimpleCommandExceptionType(
            Component.translatable("commands.emergentstealth.zone.no_rope"));

    static final SuggestionProvider<CommandSourceStack> ZONES = (ctx, builder) -> SharedSuggestionProvider.suggest(
            Zones.get(ctx.getSource().getLevel()).all().stream().map(Zone::name), builder);
    private static final SuggestionProvider<CommandSourceStack> ACCESS = (ctx, builder) -> SharedSuggestionProvider.suggest(
            Arrays.stream(Zone.Access.values()).map(Zone.Access::getSerializedName), builder);

    static ArgumentBuilder<CommandSourceStack, ?> zone() {
        return Commands.literal("zone")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list").executes(ZoneCommands::list))
                .then(Commands.literal("add").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("access", StringArgumentType.word()).suggests(ACCESS)
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(ctx -> add(ctx, Optional.empty()))
                                                .then(Commands.argument("hours_from", IntegerArgumentType.integer(0, 24))
                                                        .then(Commands.argument("hours_to", IntegerArgumentType.integer(0, 24))
                                                                .executes(ctx -> add(ctx, Optional.of(hours(ctx)))))))))))
                .then(Commands.literal("set").then(Commands.argument("zone", StringArgumentType.word()).suggests(ZONES)
                        .then(Commands.argument("access", StringArgumentType.word()).suggests(ACCESS)
                                .executes(ctx -> set(ctx, Optional.empty()))
                                .then(Commands.argument("hours_from", IntegerArgumentType.integer(0, 24))
                                        .then(Commands.argument("hours_to", IntegerArgumentType.integer(0, 24))
                                                .executes(ctx -> set(ctx, Optional.of(hours(ctx)))))))))
                .then(Commands.literal("remove").then(Commands.argument("zone", StringArgumentType.word()).suggests(ZONES)
                        .executes(ZoneCommands::remove)))
                .then(Commands.literal("select").then(Commands.argument("zone", StringArgumentType.word()).suggests(ZONES)
                        .executes(ZoneCommands::select)))
                .then(Commands.literal("at")
                        .executes(ctx -> at(ctx, BlockPos.containing(ctx.getSource().getPosition())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> at(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos")))));
    }

    static Zone zone(CommandContext<CommandSourceStack> ctx, String argument) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, argument);
        return Zones.get(ctx.getSource().getLevel()).get(name).orElseThrow(() -> UNKNOWN.create(name));
    }

    private static Zone.Access access(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "access");
        return Arrays.stream(Zone.Access.values()).filter(a -> a.getSerializedName().equals(name)).findFirst()
                .orElseThrow(() -> BAD_ACCESS.create(name));
    }

    private static Zone.Hours hours(CommandContext<CommandSourceStack> ctx) {
        return new Zone.Hours(IntegerArgumentType.getInteger(ctx, "hours_from"), IntegerArgumentType.getInteger(ctx, "hours_to"));
    }

    private static String describe(Zone zone) {
        BoundingBox b = zone.bounds();
        return zone.name() + ": " + zone.access().getSerializedName()
                + zone.hours().map(h -> " " + h.from() + "-" + h.to() + "h").orElse("")
                + ", " + zone.boxes().size() + " box(es) within " + b.minX() + " " + b.minY() + " " + b.minZ()
                + " .. " + b.maxX() + " " + b.maxY() + " " + b.maxZ();
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<Zone> zones = List.copyOf(Zones.get(source.getLevel()).all());
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.zone.list", zones.size()), false);
        for (Zone zone : zones) {
            source.sendSuccess(() -> Component.literal("  " + describe(zone)), false);
        }
        return zones.size();
    }

    /** Creates the zone, or adds the box to it if it exists (keeping its rule). */
    private static int add(CommandContext<CommandSourceStack> ctx, Optional<Zone.Hours> hours) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        if (!ZoneTool.NAME.matcher(name).matches()) {
            throw BAD_NAME.create(name);
        }
        Zone.Access access = access(ctx);
        BoundingBox box = BoundingBox.fromCorners(BlockPosArgument.getLoadedBlockPos(ctx, "from"), BlockPosArgument.getLoadedBlockPos(ctx, "to"));
        Zones zones = Zones.get(source.getLevel());
        Zone zone = zones.get(name).map(z -> z.withBox(box)).orElseGet(() -> new Zone(name, access, List.of(box), hours));
        zones.put(zone);
        source.sendSuccess(() -> Component.literal(describe(zone)), true);
        return zone.boxes().size();
    }

    private static int set(CommandContext<CommandSourceStack> ctx, Optional<Zone.Hours> hours) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Zone old = zone(ctx, "zone");
        Zone zone = new Zone(old.name(), access(ctx), old.boxes(), hours);
        Zones.get(source.getLevel()).put(zone);
        source.sendSuccess(() -> Component.literal(describe(zone)), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Zone zone = zone(ctx, "zone");
        Zones.get(source.getLevel()).remove(zone.name());
        source.sendSuccess(() -> Component.translatable("message.emergentstealth.zone.deleted", zone.name()), true);
        return 1;
    }

    /** Makes the zone the one the held Surveyor's Rope edits. */
    private static int select(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Zone zone = zone(ctx, "zone");
        ItemStack rope = ZoneTool.heldRope(source.getPlayerOrException());
        if (rope == null) {
            throw NO_ROPE.create();
        }
        rope.set(ESDataComponents.ROPE_ZONE.get(), zone.name());
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.zone.selected", zone.name()), false);
        return 1;
    }

    /** Which rule holds at a block right now, and the zones there. */
    private static int at(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        CommandSourceStack source = ctx.getSource();
        Zone.Access access = Trespass.accessAt(source.getLevel(), pos);
        int hour = Schedule.hourOf(source.getLevel().getDefaultClockTime());
        List<String> here = Zones.get(source.getLevel()).all().stream().filter(z -> z.contains(pos)).map(Zone::name).toList();
        source.sendSuccess(() -> Component.translatable("commands.emergentstealth.zone.at", pos.toShortString(),
                access.getSerializedName(), hour, here.isEmpty() ? "-" : String.join(", ", here)), false);
        return access.ordinal();
    }
}
