package com.mcspacewizard.emergentstealth.client.anim.pal;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.action.ActionPlayback;
import com.mcspacewizard.emergentstealth.anim.ActionClock;
import com.mcspacewizard.emergentstealth.registry.ESAttachments;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;

/**
 * Plays the attacker's side of a paired action from a PAL clip (design doc 17 §3), e.g.
 * {@code assets/emergentstealth/player_animations/takedowns.json} → {@code emergentstealth:takedown/rear_nonlethal.attacker}.
 *
 * <p>The clip never keeps its own time: every frame it is pinned to the server's action clock
 * ({@code gameTime - startTick + partialTick}) and stretched over the action's length, which takedown-speed
 * skills shorten. So a client that sees the takedown late seeks straight in, and attacker and victim stay aligned.
 */
public final class TakedownClipController extends PlayerAnimationController {
    private @Nullable Identifier playing;
    private long playingStart = Long.MIN_VALUE;

    public TakedownClipController(Avatar avatar) {
        super(avatar, (controller, data, setter) -> PlayState.CONTINUE);
        // In first person, show the model's own arms doing the takedown.
        setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
    }

    @Override
    public void tick(AnimationData state) {
        sync();
        super.tick(state);
    }

    /** Starts the clip for a new action, stops it when the action ends. */
    private void sync() {
        Avatar avatar = getAvatar();
        if (avatar.level() == null) {
            return;
        }
        ActionPlayback action = avatar.getData(ESAttachments.ACTION);
        long now = avatar.level().getGameTime();
        boolean wanted = action.role() == ActionPlayback.Role.ATTACKER && ActionClock.playing(action, now, 0.0F);
        Identifier clip = wanted ? ActionClock.clipId(action, "attacker") : null;
        if (clip != null && PlayerAnimResources.hasAnimation(clip)) {
            if (!clip.equals(playing) || action.startTick() != playingStart) {
                triggerAnimation(clip, 0.0F);
                playing = clip;
                playingStart = action.startTick();
            }
        } else if (playing != null) {
            stop();
            playing = null;
            playingStart = Long.MIN_VALUE;
        }
    }

    /** Pins the clip time to the action clock before PAL samples it. */
    @Override
    public void process(AnimationData state) {
        Animation animation = playing == null ? null : PlayerAnimResources.getAnimation(playing);
        if (animation != null && getAvatar().level() != null) {
            ActionPlayback action = getAvatar().getData(ESAttachments.ACTION);
            float progress = ActionClock.progress(action, getAvatar().level().getGameTime(), state.getPartialTick());
            this.tick = 0;
            this.startAnimFrom = Math.min(progress, 0.999F) * animation.length() - state.getPartialTick();
        }
        super.process(state);
    }

    /** The clip being played, or null. */
    public @Nullable Identifier playing() {
        return playing;
    }
}
