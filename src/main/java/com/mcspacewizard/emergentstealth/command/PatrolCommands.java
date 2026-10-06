package com.mcspacewizard.emergentstealth.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoute;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoutes;
import com.mcspacewizard.emergentstealth.ai.routine.Schedule;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESDataComponents;
import com.mcspacewizard.emergentstealth.registry.ESItems;
import com.mcspacewizard.emergentstealth.registry.ESRegistries;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** {@code /es patrol ...} and {@code /es routine ...} (design doc 15). */
final class PatrolCommands {
    private PatrolCommands() {}

    private static final DynamicCommandExceptionType UNKNOWN_ROUTE = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.emergentstealth.patrol.unknown", String.valueOf(name)));
    private static final DynamicCommandExceptionType BAD_INDEX = new DynamicCommandExceptionType(
            index -> Component.translatable("commands.emergentstealth.patrol.bad_index", String.valueOf(index)));

    private static final DynamicCommandExceptionType UNKNOWN_TREE = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.emergentstealth.npc.behaviour.unknown", String.valueOf(id)));

    private static final SuggestionProvider<CommandSourceStack> ROUTES = (ctx, builder) -> SharedSuggestionProvider.suggest(
            PatrolRoutes.get(ctx.getSource().getLevel()).all().stream().map(PatrolRoute::name), builder);

    static ArgumentBuilder<CommandSourceStack, ?> patrol() {
        return Commands.literal("patrol")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list").executes(PatrolCommands::list))
                .then(Commands.literal("select").then(route().executes(PatrolCommands::select)))
                .then(Commands.literal("remove").then(route().executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "route");
                    if (!PatrolRoutes.get(ctx.getSource().getLevel()).remove(name)) {
                        throw UNKNOWN_ROUTE.create(name);
                    }
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.patrol.removed", name), true);
                    return 1;
                })))
                .then(Commands.literal("mode").then(route()
                        .then(Commands.literal("loop").executes(ctx -> setMode(ctx, PatrolRoute.Mode.LOOP)))
                        .then(Commands.literal("pingpong").executes(ctx -> setMode(ctx, PatrolRoute.Mode.PINGPONG)))))
                .then(Commands.literal("wait").then(route().then(index()
                        .then(Commands.argument("seconds", FloatArgumentType.floatArg(0, 600)).executes(ctx -> editWaypoint(ctx,
                                w -> new PatrolRoute.Waypoint(w.pos(), Math.round(FloatArgumentType.getFloat(ctx, "seconds") * 20.0F), w.lookYaw(), w.relight())))))))
                .then(Commands.literal("look").then(route().then(index()
                        .executes(ctx -> editWaypoint(ctx, w -> new PatrolRoute.Waypoint(w.pos(), w.waitTicks(),
                                Optional.of(ctx.getSource().getRotation().y), w.relight())))
                        .then(Commands.literal("clear").executes(ctx -> editWaypoint(ctx,
                                w -> new PatrolRoute.Waypoint(w.pos(), w.waitTicks(), Optional.empty(), w.relight()))))
                        .then(Commands.argument("yaw", FloatArgumentType.floatArg(-360, 360)).executes(ctx -> editWaypoint(ctx,
                                w -> new PatrolRoute.Waypoint(w.pos(), w.waitTicks(), Optional.of(FloatArgumentType.getFloat(ctx, "yaw")), w.relight())))))))
                .then(Commands.literal("relight").then(route().then(index()
                        .then(Commands.argument("enabled", BoolArgumentType.bool()).executes(ctx -> editWaypoint(ctx,
                                w -> new PatrolRoute.Waypoint(w.pos(), w.waitTicks(), w.lookYaw(), BoolArgumentType.getBool(ctx, "enabled"))))))))
                .then(Commands.literal("assign").then(Commands.argument("npcs", EntityArgument.entities()).then(route()
                        .executes(ctx -> assign(ctx, 0, 0))
                        .then(Commands.argument("from", IntegerArgumentType.integer(0, 24))
                                .then(Commands.argument("to", IntegerArgumentType.integer(0, 24))
                                        .executes(ctx -> assign(ctx, IntegerArgumentType.getInteger(ctx, "from"), IntegerArgumentType.getInteger(ctx, "to"))))))));
    }

    /** {@code /es npc behaviour <npcs> <tree>|reset} (design doc 14 §3). */
    static ArgumentBuilder<CommandSourceStack, ?> behaviour() {
        return Commands.literal("behaviour").then(Commands.argument("npcs", EntityArgument.entities())
                .then(Commands.literal("reset").executes(ctx -> {
                    List<StealthNpc> npcs = npcs(ctx);
                    npcs.forEach(npc -> npc.setBehaviourOverride(null));
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.npc.behaviour.reset", npcs.size()), true);
                    return npcs.size();
                }))
                .then(Commands.argument("tree", IdentifierArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().registryAccess()
                                .lookupOrThrow(ESRegistries.BEHAVIOUR).keySet().stream().map(Identifier::toString), builder))
                        .executes(ctx -> {
                            Identifier id = IdentifierArgument.getId(ctx, "tree");
                            if (ctx.getSource().registryAccess().lookupOrThrow(ESRegistries.BEHAVIOUR).getValue(id) == null) {
                                throw UNKNOWN_TREE.create(id);
                            }
                            List<StealthNpc> npcs = npcs(ctx);
                            npcs.forEach(npc -> npc.setBehaviourOverride(id));
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.npc.behaviour.set", npcs.size(), id.toString()), true);
                            return npcs.size();
                        })));
    }

    static ArgumentBuilder<CommandSourceStack, ?> routine() {
        return Commands.literal("routine")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("post").then(Commands.argument("npcs", EntityArgument.entities()).executes(ctx -> {
                    BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                    float yaw = ctx.getSource().getRotation().y;
                    return applyToNpcs(ctx, npc -> new Schedule(List.of(new Schedule.Entry(0, 0, new Schedule.Post(pos, yaw)))));
                })))
                .then(Commands.literal("wander").then(Commands.argument("npcs", EntityArgument.entities())
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64)).executes(ctx -> {
                            int radius = IntegerArgumentType.getInteger(ctx, "radius");
                            return applyToNpcs(ctx, npc -> new Schedule(List.of(new Schedule.Entry(0, 0, new Schedule.Wander(radius)))));
                        }))))
                .then(Commands.literal("clear").then(Commands.argument("npcs", EntityArgument.entities())
                        .executes(ctx -> applyToNpcs(ctx, npc -> Schedule.EMPTY))))
                .then(Commands.literal("show").then(Commands.argument("npcs", EntityArgument.entities()).executes(ctx -> {
                    for (StealthNpc npc : npcs(ctx)) {
                        Schedule schedule = npc.getSchedule();
                        String text = schedule.entries().isEmpty() ? "(default: " + npc.activeActivity().describe() + ")"
                                : String.join(", ", schedule.entries().stream()
                                        .map(e -> e.fromHour() + "-" + e.toHour() + "h " + e.activity().describe()).toList());
                        ctx.getSource().sendSuccess(() -> Component.literal(npc.getName().getString() + ": " + text), false);
                    }
                    return 1;
                })));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> route() {
        return Commands.argument("route", StringArgumentType.word()).suggests(ROUTES);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> index() {
        return Commands.argument("index", IntegerArgumentType.integer(0));
    }

    private static PatrolRoute route(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "route");
        return PatrolRoutes.get(ctx.getSource().getLevel()).get(name).orElseThrow(() -> UNKNOWN_ROUTE.create(name));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        Collection<PatrolRoute> routes = PatrolRoutes.get(ctx.getSource().getLevel()).all();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.patrol.list", routes.size()), false);
        for (PatrolRoute route : routes) {
            String first = route.waypoints().isEmpty() ? "-" : route.waypoints().getFirst().pos().toShortString();
            ctx.getSource().sendSuccess(() -> Component.literal("  " + route.name() + " (" + route.mode().getSerializedName()
                    + ", " + route.waypoints().size() + " waypoints, starts " + first + ")"), false);
        }
        return routes.size();
    }

    private static int select(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PatrolRoute route = route(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ESItems.PATROL_BATON.get())) {
            stack = new ItemStack(ESItems.PATROL_BATON.get());
            player.getInventory().add(stack);
        }
        stack.set(ESDataComponents.PATROL_ROUTE.get(), route.name());
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.patrol.selected", route.name()), false);
        return 1;
    }

    private static int setMode(CommandContext<CommandSourceStack> ctx, PatrolRoute.Mode mode) throws CommandSyntaxException {
        PatrolRoute route = route(ctx);
        PatrolRoutes.get(ctx.getSource().getLevel()).put(route.withMode(mode));
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.patrol.updated", route.name()), true);
        return 1;
    }

    private static int editWaypoint(CommandContext<CommandSourceStack> ctx, UnaryOperator<PatrolRoute.Waypoint> edit) throws CommandSyntaxException {
        PatrolRoute route = route(ctx);
        int index = IntegerArgumentType.getInteger(ctx, "index");
        if (index >= route.waypoints().size()) {
            throw BAD_INDEX.create(index);
        }
        List<PatrolRoute.Waypoint> waypoints = new ArrayList<>(route.waypoints());
        waypoints.set(index, edit.apply(waypoints.get(index)));
        PatrolRoutes.get(ctx.getSource().getLevel()).put(route.withWaypoints(waypoints));
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.patrol.updated", route.name()), true);
        return 1;
    }

    private static int assign(CommandContext<CommandSourceStack> ctx, int from, int to) throws CommandSyntaxException {
        PatrolRoute route = route(ctx);
        Schedule.Entry entry = new Schedule.Entry(from, to, new Schedule.Route(route.name()));
        boolean allDay = from == to;
        return applyToNpcs(ctx, npc -> allDay ? new Schedule(List.of(entry)) : npc.getSchedule().with(entry));
    }

    private static List<StealthNpc> npcs(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        List<StealthNpc> npcs = new ArrayList<>();
        for (Entity entity : EntityArgument.getEntities(ctx, "npcs")) {
            if (entity instanceof StealthNpc npc) {
                npcs.add(npc);
            }
        }
        return npcs;
    }

    private static int applyToNpcs(CommandContext<CommandSourceStack> ctx, java.util.function.Function<StealthNpc, Schedule> change)
            throws CommandSyntaxException {
        List<StealthNpc> npcs = npcs(ctx);
        for (StealthNpc npc : npcs) {
            npc.setSchedule(change.apply(npc));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.emergentstealth.routine.updated", npcs.size()), true);
        return npcs.size();
    }
}
