package com.mcspacewizard.emergentstealth.client.render;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.anim.HumanoidPose;
import com.mcspacewizard.emergentstealth.client.anim.BodyPlacement;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public class NpcRenderState extends HumanoidRenderState {
    /** Base skin texture. */
    public Identifier body = NpcRenderer.DEFAULT_BODY;
    /** Outfit layer textures, drawn in order over the body. */
    public final List<Identifier> outfitLayers = new ArrayList<>();
    /** Detection indicator above the head (null = none). */
    public @Nullable Component indicator;
    /** What the NPC is saying (null = nothing). */
    public @Nullable Component bark;
    /** Where the indicator attaches (the name-tag attachment point). */
    public @Nullable Vec3 indicatorAttachment;
    /** The procedural pose (design doc 17 §8), evaluated once per frame at extraction. */
    public final HumanoidPose pose = new HumanoidPose();
    /** Where and how the body lies (knocked out, dead, dragged, carried). */
    public final BodyPlacement placement = new BodyPlacement();
    /** A takedown victim clip to play this frame (null = none), and where in it. */
    public @Nullable AnimationDefinition victimClip;
    public long victimClipMillis;
}
