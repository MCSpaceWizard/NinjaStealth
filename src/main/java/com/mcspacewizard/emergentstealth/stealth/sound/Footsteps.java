package com.mcspacewizard.emergentstealth.stealth.sound;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.perception.NpcPerception;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Player footsteps and landings (design doc 16 §1, S-02). We track these ourselves, server-side, from how far
 * the player actually moved; vanilla's {@code step} and {@code hit_ground} game events are ignored for players
 * (see {@link NoiseSources}), so nothing is counted twice.
 * <ul>
 *   <li>One footstep noise per {@link #STRIDE} blocks walked on the ground: sneaking 2, walking 6, sprinting 12,
 *       crawling 1. ×1.25 on loud surfaces, ×0.8 on quiet ones.</li>
 *   <li>Landing: {@code 4 + 1.5 × fall distance} (max 16), halved when sneaking. Drops under a block are not
 *       landings (stairs, slabs).</li>
 * </ul>
 * Stances added later (S7 crawling, disguises...) plug in with {@link #addStance}.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class Footsteps {
    private Footsteps() {}

    public static final float STRIDE = 1.5F;
    public static final float SNEAK = 2.0F;
    public static final float WALK = 6.0F;
    public static final float SPRINT = 12.0F;
    public static final float CRAWL = 1.0F;
    public static final float LOUD_SURFACE = 1.25F;
    public static final float QUIET_SURFACE = 0.8F;
    public static final float MIN_LANDING_FALL = 1.0F;
    public static final float MAX_LANDING = 16.0F;

    /**
     * A stance that sets footstep loudness. Return {@link Float#NaN} when it doesn't apply, so the next one (and
     * finally the built-in sneak / walk / sprint) decides.
     */
    @FunctionalInterface
    public interface Stance {
        float loudness(Player player);
    }

    private static final List<Stance> STANCES = new CopyOnWriteArrayList<>();
    private static final Map<Player, Track> TRACKS = new WeakHashMap<>();
    /** A move longer than this in one tick is a teleport, not walking. */
    private static final double TELEPORT = 4.0;

    private static final class Track {
        Vec3 last;
        double walked;

        Track(Vec3 last) {
            this.last = last;
        }
    }

    /** Registers a stance checked before the built-in ones, e.g. S7's crawl: {@code p -> crawling(p) ? 1 : NaN}. */
    public static void addStance(Stance stance) {
        STANCES.add(stance);
    }

    /** Footstep loudness for how the player moves right now, before the surface multiplier. */
    public static float stanceLoudness(Player player) {
        for (Stance stance : STANCES) {
            float value = stance.loudness(player);
            if (!Float.isNaN(value)) {
                return value;
            }
        }
        // Vanilla's forced crawl (squeezing under a one-block gap) until S7 adds a real crawl toggle.
        if (player.getPose() == Pose.SWIMMING && !player.isInWater()) {
            return CRAWL;
        }
        if (player.isCrouching() || player.isShiftKeyDown()) {
            return SNEAK;
        }
        return player.isSprinting() ? SPRINT : WALK;
    }

    /** ×1.25 on {@link SoundTags#LOUD_SURFACES}, ×0.8 on {@link SoundTags#QUIET_SURFACES}, else 1. */
    public static float surfaceMultiplier(Player player) {
        // Carpets and snow layers sit in the feet block; everything else is the block underneath.
        BlockState feet = player.level().getBlockState(player.blockPosition());
        float multiplier = surfaceMultiplier(feet);
        if (multiplier != 1.0F) {
            return multiplier;
        }
        return surfaceMultiplier(player.getBlockStateOn());
    }

    public static float surfaceMultiplier(BlockState state) {
        if (state.is(SoundTags.LOUD_SURFACES)) {
            return LOUD_SURFACE;
        }
        if (state.is(SoundTags.QUIET_SURFACES)) {
            return QUIET_SURFACE;
        }
        return 1.0F;
    }

    /** Footstep loudness right now: stance × surface. */
    public static float loudness(Player player) {
        return stanceLoudness(player) * surfaceMultiplier(player);
    }

    /** The footstep noise this player would make right now (at their feet, giving them away). */
    public static NoiseEvent footstep(Player player) {
        return new NoiseEvent(player.position(), loudness(player), NoiseKind.FOOTSTEP, player.getUUID(), player.getUUID());
    }

    /** Landing loudness for a fall of {@code fallDistance} blocks; 0 below {@link #MIN_LANDING_FALL}. */
    public static float landingLoudness(double fallDistance, boolean sneaking) {
        if (fallDistance < MIN_LANDING_FALL) {
            return 0.0F;
        }
        float loudness = Math.min(MAX_LANDING, 4.0F + 1.5F * (float) fallDistance);
        return sneaking ? loudness * 0.5F : loudness;
    }

    /** Called from {@link NoiseSources} when vanilla reports the player hitting the ground. */
    static void onLanding(ServerLevel level, Player player) {
        if (!NpcPerception.isTargetable(player) || player.isInWater()) {
            return;
        }
        float loudness = landingLoudness(player.fallDistance, player.isShiftKeyDown());
        if (loudness > 0.0F) {
            Noises.emit(level, new NoiseEvent(player.position(), loudness, NoiseKind.LANDING, player.getUUID(), player.getUUID()));
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 now = player.position();
        Track track = TRACKS.get(player);
        if (track == null) {
            TRACKS.put(player, new Track(now));
            return;
        }
        double dx = now.x - track.last.x;
        double dz = now.z - track.last.z;
        track.last = now;
        double moved = Math.sqrt(dx * dx + dz * dz);
        if (moved > TELEPORT || !walking(player)) {
            track.walked = 0.0;
            return;
        }
        track.walked += moved;
        if (track.walked >= STRIDE) {
            track.walked = Mth.positiveModulo(track.walked, STRIDE);
            if (NpcPerception.isTargetable(player)) {
                Noises.emit(level, footstep(player));
            }
        }
    }

    /** On the ground, on foot: not flying, riding, swimming or climbing. */
    private static boolean walking(ServerPlayer player) {
        return player.onGround() && !player.isPassenger() && !player.getAbilities().flying
                && !player.isInWater() && !player.isSpectator() && !player.onClimbable();
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TRACKS.remove(event.getEntity());
    }
}
