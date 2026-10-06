package com.mcspacewizard.emergentstealth.client.render;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

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
    /** Where the indicator attaches (the name-tag attachment point). */
    public @Nullable Vec3 indicatorAttachment;
}
