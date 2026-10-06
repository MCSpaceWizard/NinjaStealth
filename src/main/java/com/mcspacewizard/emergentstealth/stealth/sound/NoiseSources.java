package com.mcspacewizard.emergentstealth.stealth.sound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.perception.NpcPerception;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESRegistries;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Turns vanilla game events into noises through the {@code emergentstealth:noise_source} datapack registry
 * (design doc 16 §1). Anything not listed is silent (S-03): containers, item drops, bows and arrows, eating and
 * drinking. Player steps and landings are handled by {@link Footsteps} instead of vanilla's {@code step} /
 * {@code hit_ground}, so they're never counted twice.
 *
 * <p>Attribution ({@code attributable: true}): the noise gives away the player who caused it, when that is a
 * player — the one opening the door, the one getting hurt, or the one landing a melee hit. Ranged and thrown
 * hits only point at the spot.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class NoiseSources {
    private NoiseSources() {}

    private static @Nullable Registry<NoiseSource> indexed;
    private static Map<Identifier, NoiseSource> index = Map.of();

    /** The melee attacker of the entity being hurt right now (vanilla's damage game event only names the victim). */
    private static @Nullable LivingEntity pendingVictim;
    private static @Nullable Player pendingAttacker;
    private static long pendingTick = Long.MIN_VALUE;

    /** The noise source mapped to a game event in this level's datapacks, or null if it's silent. */
    public static @Nullable NoiseSource lookup(ServerLevel level, Holder<GameEvent> gameEvent) {
        Registry<NoiseSource> registry = level.registryAccess().lookupOrThrow(ESRegistries.NOISE_SOURCE);
        if (registry != indexed) {
            Map<Identifier, NoiseSource> map = new HashMap<>();
            for (NoiseSource source : registry) {
                NoiseSource previous = map.put(source.gameEvent(), source);
                if (previous != null) {
                    EmergentStealth.LOGGER.warn("Two noise sources for game event {}; using the louder", source.gameEvent());
                    if (previous.loudness() > source.loudness()) {
                        map.put(source.gameEvent(), previous);
                    }
                }
            }
            index = map;
            indexed = registry;
        }
        return gameEvent.unwrapKey().map(ResourceKey::identifier).map(index::get).orElse(null);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onGameEvent(VanillaGameEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Holder<GameEvent> gameEvent = event.getVanillaEvent();
        Entity entity = event.getCause();
        if (entity instanceof Player player) {
            if (gameEvent.is(GameEvent.STEP.key())) {
                return;
            }
            if (gameEvent.is(GameEvent.HIT_GROUND.key())) {
                Footsteps.onLanding(level, player);
                return;
            }
        }
        NoiseSource source = lookup(level, gameEvent);
        if (source == null || source.loudness() <= 0.0F) {
            return;
        }
        if (entity instanceof StealthNpc && !source.fromNpcs()) {
            return;
        }
        UUID cause = null;
        if (source.attributable()) {
            Player player = responsiblePlayer(level, entity);
            if (player != null) {
                if (!NpcPerception.isTargetable(player)) {
                    // Creative / spectator players don't give themselves away, same as for sight.
                    return;
                }
                cause = player.getUUID();
            }
        }
        Noises.emit(level, new NoiseEvent(event.getEventPosition(), source.loudness(), source.kind(), cause,
                entity == null ? null : entity.getUUID()));
    }

    /** Remembers melee attackers so the damage noise that follows can name them. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.getDirectEntity() instanceof Player player && source.getEntity() == player) {
            pendingVictim = event.getEntity();
            pendingAttacker = player;
            pendingTick = level.getGameTime();
        }
    }

    private static @Nullable Player responsiblePlayer(ServerLevel level, @Nullable Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof LivingEntity victim && victim == pendingVictim && pendingTick == level.getGameTime()) {
            return pendingAttacker;
        }
        if (entity instanceof TraceableEntity traceable && traceable.getOwner() instanceof Player owner
                && !(entity instanceof net.minecraft.world.entity.projectile.Projectile)) {
            return owner;
        }
        return null;
    }
}
