package com.mcspacewizard.emergentstealth.stealth.sound;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.perception.PerceptionProfile;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESEntities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Entry point for noises (design doc 16). Anything that makes a noise calls {@link #emit}; every
 * {@link StealthNpc} that hears it gets {@link StealthNpc#onNoiseHeard}.
 *
 * <p>How a noise is resolved:
 * <ol>
 *   <li>Listeners: NPCs within {@code loudness × hearing × (1 − masking)} of the noise (straight-line
 *       distance). If there are none, the noise is dropped right away.</li>
 *   <li>A listener with a {@link NoisePropagation#clearLine clear line} hears at cost = distance, immediately.</li>
 *   <li>The rest share one {@link NoisePropagation.Flood flood fill}, which stops once all of them are reached.
 *       Its work counts against {@code soundNodesPerTick}; anything over budget finishes on a later tick,
 *       which reads as a natural sound delay.</li>
 * </ol>
 * Heard intensity is {@code 1 − cost / effectiveLoudness}; anything above 0 is heard.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class Noises {
    private Noises() {}

    /** One NPC that heard a noise. */
    public record Hearer(StealthNpc npc, HeardNoise heard) {}

    /** Told about every resolved noise, including ones nobody heard (debug view, tests). Server thread. */
    @FunctionalInterface
    public interface Observer {
        void onResolved(ServerLevel level, NoiseEvent noise, List<Hearer> hearers);
    }

    /** Per-listener masking, so tests can force weather without touching the world. */
    @FunctionalInterface
    public interface MaskingSource {
        float masking(StealthNpc npc);
    }

    /** A job waiting this long (budget starvation) is dropped: the moment has passed. */
    private static final int MAX_JOB_AGE_TICKS = 60;
    /** Upper bound for a synchronous {@link #hearers} query. */
    private static final int SYNC_NODE_LIMIT = 2_000_000;

    private static final List<Observer> OBSERVERS = new CopyOnWriteArrayList<>();
    private static final Map<ServerLevel, LevelState> STATES = new WeakHashMap<>();

    private static final class LevelState {
        final ArrayDeque<Job> queue = new ArrayDeque<>();
        long budgetTick = Long.MIN_VALUE;
        int budgetLeft;
        /** Stats for the debug view / perf checks. */
        long nodesTotal;
        long noisesTotal;
        long floodsTotal;
    }

    public static void addObserver(Observer observer) {
        OBSERVERS.add(observer);
    }

    public static void removeObserver(Observer observer) {
        OBSERVERS.remove(observer);
    }

    // ------------------------------------------------------------------------------------------------
    // Public API

    /**
     * Makes a noise. Listeners with a clear line hear it now; the rest once the flood fill is done (usually
     * this tick). NoAI NPCs (statues, props) are skipped.
     */
    public static void emit(ServerLevel level, NoiseEvent noise) {
        if (noise.loudness() <= 0.0F) {
            return;
        }
        LevelState state = state(level);
        state.noisesTotal++;
        Job job = Job.create(level, noise, false, Masking::cached);
        if (job == null) {
            notifyResolved(level, noise, List.of());
            return;
        }
        for (Hearer hearer : job.hearers) {
            deliver(level, hearer);
        }
        if (job.flood == null) {
            notifyResolved(level, noise, job.hearers);
            return;
        }
        state.floodsTotal++;
        job.delivered = job.hearers.size();
        state.queue.add(job);
        process(level, state);
    }

    /**
     * Who would hear this noise right now, with real masking. Pure query: runs the whole propagation
     * synchronously (no budget), delivers nothing, and includes NoAI NPCs.
     */
    public static List<Hearer> hearers(ServerLevel level, NoiseEvent noise) {
        return hearers(level, noise, Masking::cached);
    }

    /** {@link #hearers(ServerLevel, NoiseEvent)} with a given masking per listener. */
    public static List<Hearer> hearers(ServerLevel level, NoiseEvent noise, MaskingSource masking) {
        if (noise.loudness() <= 0.0F) {
            return List.of();
        }
        Job job = Job.create(level, noise, true, masking);
        if (job == null) {
            return List.of();
        }
        if (job.flood != null) {
            job.flood.run(SYNC_NODE_LIMIT);
            job.finish();
        }
        return List.copyOf(job.hearers);
    }

    /** {@code loudness × hearing × (1 − masking)}: how far this noise carries for this NPC. */
    public static float effectiveLoudness(StealthNpc npc, NoiseEvent noise, float masking) {
        return noise.loudness() * npc.getPerceptionProfile().hearing() * (1.0F - masking);
    }

    /** Heard intensity for a propagation cost (0 or less = not heard). */
    public static float intensity(float cost, float effectiveLoudness) {
        return effectiveLoudness <= 0.0F ? 0.0F : 1.0F - cost / effectiveLoudness;
    }

    /** Flood jobs waiting for budget in this level (debug / tests). */
    public static int pendingJobs(ServerLevel level) {
        LevelState state = STATES.get(level);
        return state == null ? 0 : state.queue.size();
    }

    /** "noises / floods / nodes" since start, for the debug command and perf checks. */
    public static String stats(ServerLevel level) {
        LevelState state = STATES.get(level);
        return state == null ? "no noises yet"
                : state.noisesTotal + " noises, " + state.floodsTotal + " floods, " + state.nodesTotal + " nodes, "
                + state.queue.size() + " pending";
    }

    // ------------------------------------------------------------------------------------------------
    // Scheduling

    private static LevelState state(ServerLevel level) {
        LevelState state = STATES.computeIfAbsent(level, l -> new LevelState());
        long now = level.getGameTime();
        if (state.budgetTick != now) {
            state.budgetTick = now;
            state.budgetLeft = ESConfig.SOUND_NODES_PER_TICK.get();
        }
        return state;
    }

    /** Runs queued flood fills, oldest first, while this tick's node budget lasts. */
    private static void process(ServerLevel level, LevelState state) {
        long now = level.getGameTime();
        while (!state.queue.isEmpty() && state.budgetLeft > 0) {
            Job job = state.queue.peek();
            if (now - job.createdAt > MAX_JOB_AGE_TICKS) {
                state.queue.poll();
                job.abandon();
                continue;
            }
            int used = job.flood.run(state.budgetLeft);
            state.budgetLeft -= used;
            state.nodesTotal += used;
            if (!job.flood.isDone()) {
                return;
            }
            state.queue.poll();
            job.finish();
            for (int i = job.delivered; i < job.hearers.size(); i++) {
                deliver(level, job.hearers.get(i));
            }
            notifyResolved(level, job.noise, job.hearers);
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LevelState state = STATES.get(level);
            if (state != null && !state.queue.isEmpty()) {
                process(level, state(level));
            }
            NoiseDebugSync.flush(level);
        }
    }

    @SubscribeEvent
    static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            NoisePropagation.invalidateCostCache();
        }
    }

    private static void deliver(ServerLevel level, Hearer hearer) {
        StealthNpc npc = hearer.npc();
        if (npc.isAlive() && !npc.isRemoved() && npc.level() == level) {
            npc.onNoiseHeard(level, hearer.heard());
        }
    }

    private static void notifyResolved(ServerLevel level, NoiseEvent noise, List<Hearer> hearers) {
        NoiseDebugSync.record(level, noise, hearers);
        for (Observer observer : OBSERVERS) {
            observer.onResolved(level, noise, hearers);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // One noise being resolved

    private static final class Job {
        final NoiseEvent noise;
        final long createdAt;
        /** Heard so far: clear-line listeners first, then flood results once finished. */
        final List<Hearer> hearers = new ArrayList<>();
        /** How many of {@link #hearers} were already delivered. */
        int delivered;
        NoisePropagation.Flood flood;
        private List<Pending> pending;

        private record Pending(StealthNpc npc, int target, float straight, float loudness) {}

        private Job(NoiseEvent noise, long createdAt) {
            this.noise = noise;
            this.createdAt = createdAt;
        }

        /** Null when nobody is in range at all. */
        static Job create(ServerLevel level, NoiseEvent noise, boolean includeNoAi, MaskingSource masking) {
            Vec3 origin = noise.pos();
            double reach = noise.loudness() * PerceptionProfile.MAX_HEARING;
            List<StealthNpc> candidates = level.getEntities(ESEntities.STEALTH_NPC.get(),
                    AABB.ofSize(origin, reach * 2, reach * 2, reach * 2),
                    npc -> npc.isAlive() && (includeNoAi || !npc.isNoAi()) && !npc.getUUID().equals(noise.source()));
            if (candidates.isEmpty()) {
                return null;
            }
            Job job = null;
            NoisePropagation.BlockReader reader = null;
            List<Pending> needFlood = null;
            float bound = 0.0F;
            for (StealthNpc npc : candidates) {
                Vec3 ear = npc.getEyePosition();
                float straight = (float) ear.distanceTo(origin);
                float hearing = npc.getPerceptionProfile().hearing();
                // Cheap reject before computing masking: even unmasked, too far.
                if (straight >= noise.loudness() * hearing) {
                    continue;
                }
                float loudness = effectiveLoudness(npc, noise, masking.masking(npc));
                if (straight >= loudness) {
                    continue;
                }
                if (job == null) {
                    job = new Job(noise, level.getGameTime());
                    reader = new NoisePropagation.BlockReader(level);
                }
                if (NoisePropagation.clearLine(reader, origin, ear)) {
                    job.hearers.add(new Hearer(npc, new HeardNoise(noise, intensity(straight, loudness), straight)));
                } else if (Math.abs(ear.y - origin.y) <= NoisePropagation.VERTICAL_BOUND + 1) {
                    if (needFlood == null) {
                        needFlood = new ArrayList<>();
                    }
                    needFlood.add(new Pending(npc, -1, straight, loudness));
                    bound = Math.max(bound, loudness);
                }
            }
            if (job != null && needFlood != null) {
                job.flood = NoisePropagation.flood(level, origin, bound);
                job.pending = new ArrayList<>(needFlood.size());
                for (Pending p : needFlood) {
                    int target = job.flood.addTarget(p.npc().getEyePosition());
                    if (target >= 0) {
                        job.pending.add(new Pending(p.npc(), target, p.straight(), p.loudness()));
                    }
                }
                if (job.pending.isEmpty()) {
                    job.flood.release();
                    job.flood = null;
                }
            }
            return job;
        }

        /** Reads flood results into {@link #hearers} and releases the flood buffers. */
        void finish() {
            if (flood == null) {
                return;
            }
            for (Pending p : pending) {
                float reached = flood.targetCost(p.target());
                if (Float.isNaN(reached)) {
                    continue;
                }
                float cost = Math.max(reached, p.straight());
                float intensity = intensity(cost, p.loudness());
                if (intensity > 0.0F) {
                    hearers.add(new Hearer(p.npc(), new HeardNoise(noise, intensity, cost)));
                }
            }
            flood.release();
            flood = null;
        }

        void abandon() {
            if (flood != null) {
                flood.release();
                flood = null;
            }
        }
    }
}
