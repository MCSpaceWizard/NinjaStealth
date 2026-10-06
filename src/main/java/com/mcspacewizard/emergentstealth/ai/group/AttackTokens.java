package com.mcspacewizard.emergentstealth.ai.group;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.mcspacewizard.emergentstealth.ai.brain.AlertState;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Attack tokens (design doc 14 §5): only a few guards attack one target at once; the rest hold a ring around
 * it. Tokens and ring places expire when not refreshed, so a guard that stops attacking frees its token.
 */
public final class AttackTokens {
    private static final Map<ServerLevel, AttackTokens> BY_LEVEL = new WeakHashMap<>();
    /** A token or ring place not refreshed for this long is released. */
    private static final long EXPIRY_TICKS = 10;
    /** Badly hurt guards hand their token over when someone else can take it. */
    private static final float HURT_FRACTION = 0.3F;

    private final Map<UUID, Map<UUID, Long>> attackers = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> ring = new HashMap<>();

    public static AttackTokens get(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, l -> new AttackTokens());
    }

    /** Tries to get (or keep) a token to attack {@code target}. Refreshes the token when granted. */
    public boolean request(ServerLevel level, StealthNpc npc, UUID target, long now) {
        Map<UUID, Long> holders = attackers.computeIfAbsent(target, t -> new LinkedHashMap<>());
        prune(level, holders, now);
        prune(level, ring.computeIfAbsent(target, t -> new LinkedHashMap<>()), now);
        UUID id = npc.getUUID();
        boolean badlyHurt = npc.getHealth() < npc.getMaxHealth() * HURT_FRACTION;
        if (badlyHurt && othersWaiting(target, id)) {
            holders.remove(id);
            return false;
        }
        if (holders.containsKey(id) || holders.size() < ESConfig.ATTACKERS_PER_TARGET.get()) {
            holders.put(id, now);
            Map<UUID, Long> waiting = ring.get(target);
            if (waiting != null) {
                waiting.remove(id);
            }
            return true;
        }
        return false;
    }

    public boolean holds(StealthNpc npc, UUID target) {
        Map<UUID, Long> holders = attackers.get(target);
        return holders != null && holders.containsKey(npc.getUUID());
    }

    /** Whether this NPC holds a token for any target (debug view). */
    public boolean holdsAny(StealthNpc npc) {
        for (Map<UUID, Long> holders : attackers.values()) {
            if (holders.containsKey(npc.getUUID())) {
                return true;
            }
        }
        return false;
    }

    public void release(StealthNpc npc) {
        UUID id = npc.getUUID();
        attackers.values().forEach(holders -> holders.remove(id));
        ring.values().forEach(waiting -> waiting.remove(id));
    }

    /**
     * Where a ring holder should stand: spread around the target on the side away from the attackers.
     *
     * @return a point {@code distance} blocks from {@code targetPos}
     */
    public Vec3 ringSlot(ServerLevel level, StealthNpc npc, UUID target, Vec3 targetPos, double distance, long now) {
        Map<UUID, Long> waiting = ring.computeIfAbsent(target, t -> new LinkedHashMap<>());
        waiting.put(npc.getUUID(), now);
        prune(level, waiting, now);
        List<UUID> order = new ArrayList<>(waiting.keySet());
        order.sort(null);
        int index = Math.max(0, order.indexOf(npc.getUUID()));
        int count = Math.max(1, order.size());

        // Average direction from the target to the attackers; the ring sits opposite, spread over a half circle.
        double ax = 0;
        double az = 0;
        Map<UUID, Long> holders = attackers.get(target);
        if (holders != null) {
            for (UUID id : holders.keySet()) {
                Entity attacker = level.getEntity(id);
                if (attacker != null) {
                    ax += attacker.getX() - targetPos.x;
                    az += attacker.getZ() - targetPos.z;
                }
            }
        }
        double base = (ax * ax + az * az) > 1.0E-4 ? Math.atan2(az, ax) + Math.PI
                : Math.atan2(npc.getZ() - targetPos.z, npc.getX() - targetPos.x);
        double spread = count == 1 ? 0.0 : Math.PI / count;
        double angle = base + (index - (count - 1) / 2.0) * spread;
        return new Vec3(targetPos.x + Math.cos(angle) * distance, targetPos.y, targetPos.z + Math.sin(angle) * distance);
    }

    private boolean othersWaiting(UUID target, UUID self) {
        Map<UUID, Long> waiting = ring.get(target);
        return waiting != null && waiting.keySet().stream().anyMatch(id -> !id.equals(self));
    }

    private static void prune(ServerLevel level, Map<UUID, Long> entries, long now) {
        Iterator<Map.Entry<UUID, Long>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            boolean fighting = entity instanceof StealthNpc npc && npc.isAlive()
                    && npc.stealthBrain().state() == AlertState.COMBAT;
            if (!fighting || now - entry.getValue() > EXPIRY_TICKS) {
                it.remove();
            }
        }
    }
}
