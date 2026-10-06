package com.mcspacewizard.emergentstealth.client.light;

import java.util.ArrayList;
import java.util.List;

import com.mcspacewizard.emergentstealth.stealth.light.DynamicLights;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Visual dynamic lights (doc 30 §4): every nearby entity carrying a light, by the same rule and at the same
 * spot as the server's {@link DynamicLights}. Each tick it decides which lights moved enough to re-bake the
 * terrain around them (throttled), and publishes an immutable snapshot of the lights as baked, which meshing
 * threads and entity lighting read, so terrain and entities always agree.
 */
final class ClientDynamicLights {
    private ClientDynamicLights() {}

    /** A baked dynamic light: position, emission (1-15) and the block it sits in (ignored by its rays). */
    record Light(Vec3 pos, int emission, BlockPos block) {}

    record Snapshot(List<Light> lights, long version) {}

    /** Moving less than this (blocks) doesn't re-bake: light only changes per block anyway. */
    private static final double MOVE_THRESHOLD_SQR = 0.5 * 0.5;

    private static final class Tracked {
        Vec3 bakedPos;
        int bakedEmission;
        long lastBakeTick = Long.MIN_VALUE / 2;
        boolean seen;
    }

    private static final Int2ObjectMap<Tracked> TRACKED = new Int2ObjectOpenHashMap<>();
    private static volatile Snapshot snapshot = new Snapshot(List.of(), 0);
    private static long tickCounter;

    static Snapshot snapshot() {
        return snapshot;
    }

    /** Main thread, once per client tick. */
    static void tick(ClientLevel level, Vec3 camera, VisualSettings settings) {
        tickCounter++;
        boolean changed = false;
        if (settings.dynamicLights()) {
            double rangeSqr = (double) settings.dynamicLightRange() * settings.dynamicLightRange();
            for (Entity entity : level.entitiesForRendering()) {
                if (entity.isRemoved() || entity.isSpectator() || entity.distanceToSqr(camera) > rangeSqr) {
                    continue;
                }
                int emission = emission(entity, settings);
                if (emission <= 0) {
                    continue;
                }
                Vec3 pos = DynamicLights.lightPosition(entity);
                Tracked tracked = TRACKED.computeIfAbsent(entity.getId(), id -> new Tracked());
                tracked.seen = true;
                boolean moved = tracked.bakedPos == null || tracked.bakedEmission != emission
                        || tracked.bakedPos.distanceToSqr(pos) >= MOVE_THRESHOLD_SQR;
                if (moved && tickCounter - tracked.lastBakeTick >= settings.dynamicLightInterval()) {
                    if (tracked.bakedPos != null) {
                        SectionRebuilds.markSphere(tracked.bakedPos, tracked.bakedEmission);
                    }
                    SectionRebuilds.markSphere(pos, emission);
                    tracked.bakedPos = pos;
                    tracked.bakedEmission = emission;
                    tracked.lastBakeTick = tickCounter;
                    changed = true;
                }
            }
        }
        for (ObjectIterator<Int2ObjectMap.Entry<Tracked>> it = TRACKED.int2ObjectEntrySet().iterator(); it.hasNext(); ) {
            Tracked tracked = it.next().getValue();
            if (!tracked.seen) {
                // Light went out, was put away, or left range: bake the darkness back in.
                if (tracked.bakedPos != null) {
                    SectionRebuilds.markSphere(tracked.bakedPos, tracked.bakedEmission);
                }
                it.remove();
                changed = true;
            } else {
                tracked.seen = false;
            }
        }
        if (changed) {
            List<Light> lights = new ArrayList<>(TRACKED.size());
            for (Tracked tracked : TRACKED.values()) {
                if (tracked.bakedPos != null) {
                    lights.add(new Light(tracked.bakedPos, tracked.bakedEmission, BlockPos.containing(tracked.bakedPos)));
                }
            }
            snapshot = new Snapshot(List.copyOf(lights), snapshot.version() + 1);
        }
    }

    /** Same rule as the server, plus (optionally, visual only) dropped light items. */
    private static int emission(Entity entity, VisualSettings settings) {
        int emission = DynamicLights.emission(entity);
        if (settings.itemEntityLights() && entity instanceof ItemEntity item) {
            emission = Math.max(emission, DynamicLights.itemEmission(item.getItem()));
        }
        return emission;
    }

    static void clear() {
        TRACKED.clear();
        snapshot = new Snapshot(List.of(), snapshot.version() + 1);
    }
}
