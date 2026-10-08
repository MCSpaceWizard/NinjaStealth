package com.mcspacewizard.emergentstealth.progression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.brain.AlertState;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseEvent;
import com.mcspacewizard.emergentstealth.stealth.sound.NoiseKind;
import com.mcspacewizard.emergentstealth.stealth.sound.Noises;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Techniques: grounded active abilities unlocked by skills (design doc 26 §3). X uses the selected one;
 * sneak + X selects the next. Server-validated, with cooldowns scaled by {@link StealthStat#TECHNIQUE_COOLDOWN}.
 */
public final class Techniques {
    private Techniques() {}

    /** A technique: how long its effect lasts and its cooldown, in ticks. */
    public record Technique(Identifier id, int activeTicks, int cooldownTicks) {}

    public static final Technique STILL_BREATH = new Technique(EmergentStealth.id("still_breath"), 20 * 8, 20 * 20);
    public static final Technique LIGHT_STEP = new Technique(EmergentStealth.id("light_step"), 20 * 6, 20 * 30);
    public static final Technique FEINT = new Technique(EmergentStealth.id("feint"), 0, 20 * 25);
    public static final Technique IRON_FOCUS = new Technique(EmergentStealth.id("iron_focus"), 20 * 5, 20 * 40);

    public static final Map<Identifier, Technique> ALL = new LinkedHashMap<>();

    static {
        for (Technique t : List.of(STILL_BREATH, LIGHT_STEP, FEINT, IRON_FOCUS)) {
            ALL.put(t.id(), t);
        }
    }

    /** Visibility multiplier for Still Breath: 0.6 while active, crouched and not moving. */
    public static final float STILL_BREATH_VISIBILITY = 0.6F;
    private static final double FEINT_RANGE = 16.0;
    private static final float FEINT_LOUDNESS = 10.0F;
    private static final double FOCUS_RADIUS = 6.0;

    public static TechniqueState state(Player player) {
        return player.getData(ESAttachments.TECHNIQUES);
    }

    public static boolean active(Player player, Technique technique) {
        return state(player).active(technique.id(), player.level().getGameTime());
    }

    /** Techniques the player's skills unlock, in a stable order. */
    public static List<Technique> unlocked(Player player) {
        List<Technique> result = new ArrayList<>();
        var registry = Skills.registry(player);
        for (Identifier skillId : Skills.progression(player).unlocked()) {
            SkillDefinition skill = registry.getValue(skillId);
            if (skill == null) {
                continue;
            }
            for (SkillEffect effect : skill.effects()) {
                if (effect instanceof SkillEffect.UnlockTechnique unlock && ALL.containsKey(unlock.technique())
                        && !result.contains(ALL.get(unlock.technique()))) {
                    result.add(ALL.get(unlock.technique()));
                }
            }
        }
        result.sort((a, b) -> List.copyOf(ALL.values()).indexOf(a) - List.copyOf(ALL.values()).indexOf(b));
        return result;
    }

    public static @Nullable Technique selected(Player player) {
        List<Technique> unlocked = unlocked(player);
        Identifier id = Skills.progression(player).selectedTechnique();
        for (Technique technique : unlocked) {
            if (technique.id().equals(id)) {
                return technique;
            }
        }
        return unlocked.isEmpty() ? null : unlocked.getFirst();
    }

    public static void handleUse(UseTechniquePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            if (payload.cycle()) {
                cycle(player);
            } else {
                use(player);
            }
        }
    }

    public static void cycle(ServerPlayer player) {
        List<Technique> unlocked = unlocked(player);
        if (unlocked.isEmpty()) {
            return;
        }
        Technique current = selected(player);
        Technique next = unlocked.get((unlocked.indexOf(current) + 1) % unlocked.size());
        player.setData(ESAttachments.PROGRESSION, Skills.progression(player).withTechnique(next.id()));
        player.sendOverlayMessage(Component.translatable("technique." + next.id().getNamespace() + "." + next.id().getPath()));
    }

    /** Uses the selected technique if it's off cooldown. Returns whether it was used. */
    public static boolean use(ServerPlayer player) {
        Technique technique = selected(player);
        if (technique == null || player.isSpectator()) {
            return false;
        }
        ServerLevel level = player.level();
        long now = level.getGameTime();
        TechniqueState state = state(player);
        if (state.cooldownLeft(technique.id(), now) > 0) {
            return false;
        }
        if (technique == FEINT && !feint(player)) {
            return false;
        }
        if (technique == IRON_FOCUS) {
            ironFocus(player);
        }
        long cooldown = Math.round(technique.cooldownTicks() * StealthStats.get(player, StealthStat.TECHNIQUE_COOLDOWN));
        player.setData(ESAttachments.TECHNIQUES, state.used(technique.id(), now, technique.activeTicks(), cooldown));
        StealthStats.invalidate(player);
        return true;
    }

    /** Throws your voice: a noise at the block you look at. It points at a spot, not at you. */
    private static boolean feint(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(FEINT_RANGE));
        var hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        Noises.emit(player.level(), new NoiseEvent(hit.getLocation(), FEINT_LOUDNESS, NoiseKind.IMPACT, null, player.getUUID()));
        return true;
    }

    /** Placeholder until S13 combat: guards fighting you nearby are slowed. */
    private static void ironFocus(ServerPlayer player) {
        for (StealthNpc npc : player.level().getEntitiesOfClass(StealthNpc.class, player.getBoundingBox().inflate(FOCUS_RADIUS),
                n -> n.stealthBrain().state() == AlertState.COMBAT && player.getUUID().equals(n.stealthBrain().alertTarget()))) {
            npc.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, IRON_FOCUS.activeTicks(), 1));
        }
    }

    /** Still Breath works while crouched and not moving. */
    public static boolean stillBreathing(Player player) {
        return active(player, STILL_BREATH) && player.isShiftKeyDown()
                && player.getKnownMovement().horizontalDistanceSqr() < 1.0E-4;
    }
}
