package com.mcspacewizard.emergentstealth.client.anim;

import com.mcspacewizard.emergentstealth.action.ActionPlayback;
import com.mcspacewizard.emergentstealth.action.BodyState;
import com.mcspacewizard.emergentstealth.action.CarryLink;
import com.mcspacewizard.emergentstealth.action.Stance;
import com.mcspacewizard.emergentstealth.anim.ActionClock;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Everything a {@link PoseLayer} may read for one entity in one frame (design doc 17 §8). Built from the entity
 * and its {@link ProceduralState} at extraction time; layers must not look anywhere else, so the result is a
 * pure function of this record.
 */
public record PoseContext(
        LivingEntity entity,
        int entityId,
        boolean isPlayer,
        float ageInTicks,
        float partialTick,
        float walkPos,
        float walkSpeed,
        float bodyYaw,
        float swimAmount,
        long gameTime,
        ActionPlayback action,
        BodyState bodyState,
        CarryLink carry,
        Stance stance,
        ProceduralState sim) {

    public static PoseContext of(LivingEntity entity, float partialTick, ProceduralState sim) {
        BodyState body = entity instanceof StealthNpc npc ? npc.getBodyState() : BodyState.NONE;
        Stance stance = entity instanceof Player player ? player.getData(ESAttachments.STANCE) : Stance.STANDING;
        return new PoseContext(entity, entity.getId(), entity instanceof Player, entity.tickCount + partialTick, partialTick,
                entity.walkAnimation.position(partialTick), entity.walkAnimation.speed(partialTick),
                entity.getPreciseBodyRotation(partialTick), entity.getSwimAmount(partialTick), entity.level().getGameTime(),
                entity.getData(ESAttachments.ACTION), body, entity.getData(ESAttachments.CARRY), stance, sim);
    }

    /** The action is playing this frame. */
    public boolean actionPlaying() {
        return ActionClock.playing(action, gameTime, partialTick);
    }

    public float actionTicks() {
        return ActionClock.ticks(action, gameTime, partialTick);
    }

    public boolean isBody() {
        return bodyState.isBody();
    }

    /** This entity carries or drags a body (it is the living end of the link). */
    public boolean isCarrier() {
        return !isBody() && !carry.isNone();
    }
}
