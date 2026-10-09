package com.mcspacewizard.emergentstealth.world.lock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.item.LockpickItem;
import com.mcspacewizard.emergentstealth.network.LockpickPayloads;
import com.mcspacewizard.emergentstealth.progression.SkillPath;
import com.mcspacewizard.emergentstealth.progression.Skills;
import com.mcspacewizard.emergentstealth.progression.StealthStat;
import com.mcspacewizard.emergentstealth.progression.StealthStats;
import com.mcspacewizard.emergentstealth.registry.ESNetwork;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseEvent;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseKind;
import com.mcspacewizard.emergentstealth.stealth.sound.Noises;
import com.mcspacewizard.emergentstealth.tool.ToolEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The timing-ring lockpicking minigame, server side (design doc 21 §2, E-05).
 * <ol>
 *   <li>A lockpick used on a locked block starts a session and opens the ring on the client.</li>
 *   <li>The client reports each click as a hit (inside the moving window) or a miss. The server checks the
 *       session, the rate limit, the distance, that the lock still exists and that a lockpick is still in hand.</li>
 *   <li>{@link #PINS} hits open the block <b>once</b>: a door swings open (it's locked again once closed), a
 *       chest's menu opens. The lock itself stays.</li>
 *   <li>A miss loses a pin, makes a click noise ({@link #MISS_NOISE}, gives the picker away) and has a
 *       {@link #WEAR_CHANCE} chance to wear the pick by one point (durability 8; it breaks at 0).</li>
 * </ol>
 * The ring gets faster and the window narrower with the lock's difficulty (1–5); see {@link #speed} and
 * {@link #window}. The client decides hit or miss, so timing isn't anti-cheat proof; noise, wear and the rate
 * limit are server side.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class Lockpicking {
    private Lockpicking() {}

    public static final int PINS = 3;
    public static final float MISS_NOISE = 4.0F;
    public static final float WEAR_CHANCE = 0.5F;
    /** Minimum ticks between two clicks. */
    public static final int MIN_CLICK_INTERVAL = 4;
    public static final double MAX_DISTANCE = 5.5;
    public static final int TIMEOUT_TICKS = 60 * 20;

    /** Needle speed in degrees per second for a difficulty. */
    public static float speed(int difficulty) {
        return 160.0F + 50.0F * (difficulty - 1);
    }

    /** Window width in degrees for a difficulty. */
    public static float window(int difficulty) {
        return Math.max(18.0F, 64.0F - 11.0F * (difficulty - 1));
    }

    /** The window for this player: skills and gear widen it through {@link StealthStat#LOCKPICK_WINDOW}. */
    public static float window(ServerPlayer player, int difficulty) {
        return Math.min(180.0F, window(difficulty) * StealthStats.get(player, StealthStat.LOCKPICK_WINDOW));
    }

    /** One player picking one lock. */
    public static final class Session {
        final BlockPos pos;
        final ResourceKey<Level> dimension;
        final int difficulty;
        final InteractionHand hand;
        final long startedAt;
        int progress;
        long lastClick = Long.MIN_VALUE / 2; // halved: now - lastClick must not overflow

        Session(BlockPos pos, ResourceKey<Level> dimension, int difficulty, InteractionHand hand, long startedAt) {
            this.pos = pos;
            this.dimension = dimension;
            this.difficulty = difficulty;
            this.hand = hand;
            this.startedAt = startedAt;
        }

        public BlockPos pos() {
            return pos;
        }

        public int progress() {
            return progress;
        }

        public int difficulty() {
            return difficulty;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ESNetwork.PROTOCOL_VERSION);
        registrar.playToClient(LockpickPayloads.Open.TYPE, LockpickPayloads.Open.STREAM_CODEC);
        registrar.playToClient(LockpickPayloads.State.TYPE, LockpickPayloads.State.STREAM_CODEC);
        registrar.playToServer(LockpickPayloads.Action.TYPE, LockpickPayloads.Action.STREAM_CODEC, Lockpicking::handle);
    }

    private static void handle(LockpickPayloads.Action payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        switch (payload.action()) {
            case LockpickPayloads.Action.HIT -> click(player, true);
            case LockpickPayloads.Action.MISS -> click(player, false);
            default -> cancel(player);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    public static @Nullable Session session(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    /** Starts picking the lock at {@code pos} (replacing any session in progress). */
    public static void start(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        ServerLevel level = player.level();
        LockData.Lock lock = Locks.lockAt(level, pos);
        if (lock == null) {
            return;
        }
        Session session = new Session(Locks.canonical(level, pos), level.dimension(), lock.difficulty(), hand, level.getGameTime());
        SESSIONS.put(player.getUUID(), session);
        ESNetwork.sendIfSupported(player, new LockpickPayloads.Open(session.pos, session.difficulty, PINS, window(player, session.difficulty)));
    }

    public static void cancel(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    /**
     * One click. Returns the new progress, or -1 if the click was rejected or ended the session without opening.
     * Ignored (returns the unchanged progress) when it comes faster than {@link #MIN_CLICK_INTERVAL}.
     */
    public static int click(ServerPlayer player, boolean hit) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return -1;
        }
        ServerLevel level = player.level();
        long now = level.getGameTime();
        String problem = validate(player, session, now);
        if (problem != null) {
            end(player, session, LockpickPayloads.State.FAILED, Component.translatable(problem));
            return -1;
        }
        if (now - session.lastClick < MIN_CLICK_INTERVAL) {
            return session.progress;
        }
        session.lastClick = now;
        Vec3 at = Vec3.atCenterOf(session.pos);
        if (hit) {
            session.progress++;
            level.playSound(null, session.pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.6F + session.progress * 0.15F);
            if (session.progress >= PINS) {
                end(player, session, LockpickPayloads.State.SUCCESS, Component.translatable("message.emergentstealth.lockpick.success"));
                Locks.openOnce(level, session.pos, player);
                ToolEffects.pickOpen(level, session.pos);
                Skills.awardInsight(player, SkillPath.SHINOBI, Skills.INSIGHT_LOCKPICK);
                return PINS;
            }
        } else {
            session.progress = Math.max(0, session.progress - 1);
            level.playSound(null, session.pos, SoundEvents.TRIPWIRE_CLICK_OFF, SoundSource.BLOCKS, 0.8F, 0.6F);
            // The scrape of a slipping pick gives the picker away (design doc 16: attributable).
            Noises.emit(level, new NoiseEvent(at, MISS_NOISE, NoiseKind.OTHER, player.getUUID(), null));
            ToolEffects.pickSlip(level, session.pos);
            ItemStack pick = player.getItemInHand(session.hand);
            if (level.getRandom().nextFloat() < WEAR_CHANCE) {
                pick.hurtAndBreak(1, player, session.hand);
            }
            if (!(player.getItemInHand(session.hand).getItem() instanceof LockpickItem)) {
                end(player, session, LockpickPayloads.State.FAILED, Component.translatable("message.emergentstealth.lockpick.broke"));
                return -1;
            }
        }
        ESNetwork.sendIfSupported(player, new LockpickPayloads.State(session.progress, LockpickPayloads.State.CONTINUE));
        return session.progress;
    }

    /** Null if the session may go on, else the message key explaining why not. */
    private static @Nullable String validate(ServerPlayer player, Session session, long now) {
        if (player.level().dimension() != session.dimension || now - session.startedAt > TIMEOUT_TICKS) {
            return "message.emergentstealth.lockpick.gave_up";
        }
        if (player.getEyePosition().distanceTo(Vec3.atCenterOf(session.pos)) > MAX_DISTANCE) {
            return "message.emergentstealth.lockpick.too_far";
        }
        if (!(player.getItemInHand(session.hand).getItem() instanceof LockpickItem)) {
            return "message.emergentstealth.lockpick.no_pick";
        }
        if (Locks.lockAt(player.level(), session.pos) == null) {
            return "message.emergentstealth.lockpick.not_locked";
        }
        return null;
    }

    private static void end(ServerPlayer player, Session session, int outcome, Component message) {
        SESSIONS.remove(player.getUUID());
        player.sendOverlayMessage(message);
        ESNetwork.sendIfSupported(player, new LockpickPayloads.State(session.progress, outcome));
    }
}
