package com.mcspacewizard.emergentstealth.item;

import java.util.ArrayList;
import java.util.List;

import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoute;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoutes;
import com.mcspacewizard.emergentstealth.ai.routine.Schedule;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Map-maker tool for patrol routes (design doc 15 §2). Requires creative + op ("game master").
 * <ul>
 *   <li>Right-click a block: add a waypoint on top of it (creates the baton's route on first use).</li>
 *   <li>Sneak + right-click a block: remove the route's last waypoint.</li>
 *   <li>Right-click a stealth NPC: give it this route, all day.</li>
 * </ul>
 * While held, nearby routes are drawn in the world. Finer settings (waits, look directions, relighting,
 * time windows) are done with {@code /es patrol} and {@code /es routine} commands.
 */
public class PatrolBatonItem extends Item {
    public PatrolBatonItem(Properties properties) {
        super(properties);
    }

    public static String routeName(ItemStack stack, ServerLevel level) {
        String name = stack.get(ESDataComponents.PATROL_ROUTE.get());
        PatrolRoutes routes = PatrolRoutes.get(level);
        if (name == null || routes.get(name).isEmpty()) {
            if (name == null) {
                name = routes.nextName();
                stack.set(ESDataComponents.PATROL_ROUTE.get(), name);
            }
            routes.put(new PatrolRoute(name, List.of(), PatrolRoute.Mode.LOOP));
        }
        return name;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.canUseGameMasterBlocks()) {
            player.sendOverlayMessage(Component.translatable("message.emergentstealth.baton.no_permission"));
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        String name = routeName(stack, level);
        PatrolRoutes routes = PatrolRoutes.get(level);
        PatrolRoute route = routes.get(name).orElseThrow();
        List<PatrolRoute.Waypoint> waypoints = new ArrayList<>(route.waypoints());

        if (player.isSecondaryUseActive()) {
            if (!waypoints.isEmpty()) {
                waypoints.removeLast();
            }
            routes.put(route.withWaypoints(waypoints));
            player.sendOverlayMessage(Component.translatable("message.emergentstealth.baton.removed", name, waypoints.size()));
        } else {
            BlockPos clicked = context.getClickedPos();
            BlockPos standing = context.getClickedFace() == Direction.UP ? clicked.above() : clicked.relative(context.getClickedFace());
            waypoints.add(PatrolRoute.Waypoint.at(standing));
            routes.put(route.withWaypoints(waypoints));
            player.sendOverlayMessage(Component.translatable("message.emergentstealth.baton.added",
                    name, waypoints.size() - 1, standing.toShortString()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof StealthNpc npc)) {
            return InteractionResult.PASS;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.canUseGameMasterBlocks()) {
            player.sendOverlayMessage(Component.translatable("message.emergentstealth.baton.no_permission"));
            return InteractionResult.FAIL;
        }
        String name = routeName(stack, level);
        npc.setSchedule(new Schedule(List.of(new Schedule.Entry(0, 0, new Schedule.Route(name)))));
        player.sendOverlayMessage(Component.translatable("message.emergentstealth.baton.assigned", name));
        return InteractionResult.SUCCESS;
    }
}
