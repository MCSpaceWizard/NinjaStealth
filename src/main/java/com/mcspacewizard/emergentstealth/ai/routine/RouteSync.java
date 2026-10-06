package com.mcspacewizard.emergentstealth.ai.routine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mcspacewizard.emergentstealth.network.RouteSyncPayload;
import com.mcspacewizard.emergentstealth.registry.ESDataComponents;
import com.mcspacewizard.emergentstealth.registry.ESItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Sends nearby patrol routes to game masters holding a Patrol Baton, so they can see what they author. */
public final class RouteSync {
    private RouteSync() {}

    private static final int INTERVAL = 10;
    private static final double RANGE = 96.0;
    private static final Map<ServerPlayer, Boolean> WAS_HOLDING = new WeakHashMap<>();

    public static void tick(ServerLevel level, List<ServerPlayer> players, long now) {
        if (now % INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : players) {
            ItemStack baton = heldBaton(player);
            boolean holding = baton != null && player.canUseGameMasterBlocks();
            if (holding) {
                com.mcspacewizard.emergentstealth.registry.ESNetwork.sendIfSupported(player, build(level, player, baton));
            } else if (WAS_HOLDING.getOrDefault(player, false)) {
                com.mcspacewizard.emergentstealth.registry.ESNetwork.sendIfSupported(player, new RouteSyncPayload("", List.of()));
            }
            WAS_HOLDING.put(player, holding);
        }
    }

    private static ItemStack heldBaton(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(ESItems.PATROL_BATON.get())) {
                return stack;
            }
        }
        return null;
    }

    private static RouteSyncPayload build(ServerLevel level, ServerPlayer player, ItemStack baton) {
        List<RouteSyncPayload.Route> routes = new ArrayList<>();
        for (PatrolRoute route : PatrolRoutes.get(level).all()) {
            boolean near = route.waypoints().stream()
                    .anyMatch(w -> w.pos().distToCenterSqr(player.position()) <= RANGE * RANGE);
            if (!near) {
                continue;
            }
            List<RouteSyncPayload.Point> points = new ArrayList<>();
            for (PatrolRoute.Waypoint w : route.waypoints()) {
                points.add(new RouteSyncPayload.Point(w.pos(), w.waitTicks(), w.lookYaw().isPresent(),
                        w.lookYaw().orElse(0.0F), w.relight()));
            }
            routes.add(new RouteSyncPayload.Route(route.name(), route.mode() == PatrolRoute.Mode.LOOP, points));
        }
        String selected = baton.getOrDefault(ESDataComponents.PATROL_ROUTE.get(), "");
        return new RouteSyncPayload(selected, routes);
    }
}
