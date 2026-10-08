package com.mcspacewizard.emergentstealth.stealth.sound;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.entity.ThrownItem;
import com.mcspacewizard.emergentstealth.network.ThrowItemPayload;
import com.mcspacewizard.emergentstealth.network.NoiseDebugPayload;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Throwing any item (design doc 16 §5, S-05). The client's throw key only sends an intent with how long the key
 * was held; the server checks the player, the held item and the cooldown, and spawns the {@link ThrownItem}.
 * Also registers the sound payloads (kept out of the shared ESNetwork so stages merge cleanly).
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class Throwing {
    private Throwing() {}

    public static final int COOLDOWN_TICKS = 10;
    /** A tap lobs the item; a full charge throws it far. */
    public static final float LOB_POWER = 0.55F;
    public static final float FULL_POWER = 1.5F;
    /** Degrees added above the look direction: a lob arcs high, a hard throw flies flatter. */
    public static final float LOB_ARC = 20.0F;
    public static final float FULL_ARC = 4.0F;

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ThrowItemPayload.TYPE, ThrowItemPayload.STREAM_CODEC, Throwing::handle);
        registrar.playToClient(NoiseDebugPayload.TYPE, NoiseDebugPayload.STREAM_CODEC);
    }

    static void handle(ThrowItemPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            tryThrow(player, payload.charge());
        }
    }

    /**
     * Throws one of the main-hand item if allowed. Creative players keep the item.
     *
     * @return the thrown entity, or null if nothing was thrown (empty hand, cooldown, dead, spectator)
     */
    public static ThrownItem tryThrow(ServerPlayer player, float charge) {
        if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return null;
        }
        long now = level.getGameTime();
        Long until = COOLDOWN_UNTIL.get(player.getUUID());
        if (until != null && now < until && until - now <= COOLDOWN_TICKS) {
            return null;
        }
        COOLDOWN_UNTIL.put(player.getUUID(), now + COOLDOWN_TICKS);

        float t = Float.isNaN(charge) ? 0.0F : Mth.clamp(charge, 0.0F, 1.0F);
        ThrownItem thrown = new ThrownItem(level, player, held.copyWithCount(1));
        thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), -Mth.lerp(t, LOB_ARC, FULL_ARC),
                Mth.lerp(t, LOB_POWER, FULL_POWER), 1.0F);
        level.addFreshEntity(thrown);
        if (!player.hasInfiniteMaterials()) {
            held.shrink(1);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS,
                0.4F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        return thrown;
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        COOLDOWN_UNTIL.remove(event.getEntity().getUUID());
    }
}
