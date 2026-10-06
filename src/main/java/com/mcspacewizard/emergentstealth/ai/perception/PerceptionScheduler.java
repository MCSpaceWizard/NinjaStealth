package com.mcspacewizard.emergentstealth.ai.perception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.network.DetectionSync;
import com.mcspacewizard.emergentstealth.stealth.LightSampler;
import com.mcspacewizard.emergentstealth.stealth.light.ExposureModel;
import com.mcspacewizard.emergentstealth.stealth.light.LightGemSync;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Runs NPC perception each server tick with a level-of-detail budget (T-08):
 * <ul>
 *   <li>Tier 1: the closest {@code tier1MaxNpcs} NPCs within {@code tier1Range} of a player. Full body
 *       sampling every {@code tier1Interval} ticks.</li>
 *   <li>Tier 2: other NPCs within {@code tier2Range}. Head + chest only, every {@code tier2Interval} ticks.</li>
 *   <li>Tier 3: everyone else. No looking, awareness only decays.</li>
 * </ul>
 * Updates are staggered by entity id, and a per-tick ray budget caps the worst case.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class PerceptionScheduler {
    private PerceptionScheduler() {}

    private static final Map<ServerLevel, Set<StealthNpc>> NPCS = new WeakHashMap<>();
    private static final int TIER3_DECAY_INTERVAL = 20;
    private static final int SYNC_INTERVAL = 4;

    /** The light model perception uses: the Stage 3 exposure model with cast shadows. */
    private static LightSampler lightSampler = ExposureModel.INSTANCE;

    public static void setLightSampler(LightSampler sampler) {
        lightSampler = sampler;
    }

    public static Set<StealthNpc> trackedNpcs(ServerLevel level) {
        return NPCS.getOrDefault(level, Set.of());
    }

    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof StealthNpc npc) {
            NPCS.computeIfAbsent(level, l -> Collections.newSetFromMap(new java.util.IdentityHashMap<>())).add(npc);
        }
    }

    @SubscribeEvent
    static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof StealthNpc npc) {
            Set<StealthNpc> set = NPCS.get(level);
            if (set != null) {
                set.remove(npc);
            }
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            tick(level);
        }
    }

    static void tick(ServerLevel level) {
        long now = level.getGameTime();
        List<ServerPlayer> players = level.players();
        LightGemSync.tick(level, players, now);

        Set<StealthNpc> npcs = NPCS.get(level);
        if (npcs == null || npcs.isEmpty()) {
            return;
        }

        // Sort NPCs by distance to the nearest player.
        List<Ranked> ranked = new ArrayList<>(npcs.size());
        for (StealthNpc npc : npcs) {
            if (!npc.isAlive() || npc.isNoAi()) {
                // NoAI NPCs (statues, map props, tests driving perception by hand) don't perceive.
                continue;
            }
            double best = Double.MAX_VALUE;
            for (ServerPlayer player : players) {
                if (NpcPerception.isTargetable(player)) {
                    best = Math.min(best, player.distanceToSqr(npc));
                }
            }
            ranked.add(new Ranked(npc, best));
        }
        ranked.sort((a, b) -> Double.compare(a.distanceSq, b.distanceSq));

        int tier1Max = ESConfig.TIER1_MAX_NPCS.get();
        double tier1RangeSq = sq(ESConfig.TIER1_RANGE.get());
        double tier2RangeSq = sq(ESConfig.TIER2_RANGE.get());
        int tier1Interval = ESConfig.TIER1_INTERVAL.get();
        int tier2Interval = ESConfig.TIER2_INTERVAL.get();
        int budget = ESConfig.RAYS_PER_TICK_BUDGET.get();

        int tier1Count = 0;
        for (Ranked r : ranked) {
            StealthNpc npc = r.npc;
            NpcPerception perception = npc.perception();
            int tier;
            if (tier1Count < tier1Max && r.distanceSq <= tier1RangeSq) {
                tier = 1;
                tier1Count++;
            } else if (r.distanceSq <= tier2RangeSq) {
                tier = 2;
            } else {
                tier = 3;
            }

            if (tier == 3) {
                perception.setTier(3);
                if ((now + npc.getId()) % TIER3_DECAY_INTERVAL == 0) {
                    perception.decayOnly(now);
                }
                continue;
            }
            int interval = tier == 1 ? tier1Interval : tier2Interval;
            if ((now + npc.getId()) % interval != 0) {
                perception.setTier(tier);
                continue;
            }
            if (budget <= 0) {
                // Out of budget: this NPC waits; it'll catch up on a later tick (dt accounts for the gap).
                continue;
            }
            budget -= perception.update(level, now, tier, lightSampler);
        }

        if (now % SYNC_INTERVAL == 0) {
            DetectionSync.sendToPlayers(level, npcs, players);
        }
    }

    private static double sq(double value) {
        return value * value;
    }

    private record Ranked(StealthNpc npc, double distanceSq) {}
}
