package com.mcspacewizard.emergentstealth.client.anim;

import java.util.List;

import com.mcspacewizard.emergentstealth.client.anim.sim.BodySim;
import com.mcspacewizard.emergentstealth.client.anim.sim.DragChainSim;
import com.mcspacewizard.emergentstealth.client.anim.sim.MotionSim;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Owns every entity's {@link ProceduralState} (design doc 17 §8): created on first use, stepped by the
 * {@link ProceduralSim}s once per client tick for animated entities near the camera, dropped a couple of
 * seconds after an entity was last stepped (unloaded, out of range or gone) and cleared on logout.
 */
public final class ProceduralStates {
    private ProceduralStates() {}

    private static final List<ProceduralSim> SIMS = List.of(new MotionSim(), new BodySim(), new DragChainSim());
    /** Ticks after the last step before a state is forgotten. */
    private static final int STALE_TICKS = 40;

    private static final Int2ObjectOpenHashMap<ProceduralState> STATES = new Int2ObjectOpenHashMap<>();
    private static long tick;

    /** The state for an entity, created if needed. Render code only reads it. */
    public static ProceduralState get(Entity entity) {
        return STATES.computeIfAbsent(entity.getId(), ProceduralState::new);
    }

    /** Entities that get procedural animation: our NPCs and players. */
    public static boolean animates(Entity entity) {
        return entity instanceof StealthNpc || entity instanceof Player;
    }

    public static void tick(ClientLevel level) {
        tick++;
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        double range = ESConfig.ANIMATION_RANGE.get();
        double rangeSq = range * range;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !animates(entity) || entity.isRemoved()) {
                continue;
            }
            if (entity.distanceToSqr(camera) > rangeSq) {
                continue;
            }
            ProceduralState state = get(entity);
            for (ProceduralSim sim : SIMS) {
                sim.tick(living, level, state);
            }
            state.lastStepped = tick;
            state.fresh = false;
        }
        STATES.values().removeIf(state -> tick - state.lastStepped > STALE_TICKS && state.lastStepped != 0
                || state.lastStepped == 0 && state.fresh && tick % STALE_TICKS == 0);
    }

    public static void clear() {
        STATES.clear();
    }

    public static int size() {
        return STATES.size();
    }
}
