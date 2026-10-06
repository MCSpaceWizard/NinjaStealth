package com.mcspacewizard.emergentstealth.ai.behaviour;

import com.mojang.serialization.MapCodec;

/** A node type: its JSON {@code "type"} name and codec. */
public record BtNodeType<T extends BtNode>(String name, MapCodec<T> codec) {
}
