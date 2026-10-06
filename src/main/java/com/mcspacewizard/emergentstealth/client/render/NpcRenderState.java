package com.mcspacewizard.emergentstealth.client.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public class NpcRenderState extends HumanoidRenderState {
    /** Base skin texture. */
    public Identifier body = NpcRenderer.DEFAULT_BODY;
    /** Outfit layer textures, drawn in order over the body. */
    public final List<Identifier> outfitLayers = new ArrayList<>();
}
