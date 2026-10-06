package com.mcspacewizard.emergentstealth.ai.behaviour;

import com.mojang.serialization.Codec;

/**
 * A behaviour tree from the {@code emergentstealth/behaviour} datapack registry (design doc 14 §3): the JSON
 * file is the root node.
 */
public record BehaviourTree(BtNode root) {
    public static final Codec<BehaviourTree> CODEC = BtNodes.CODEC.xmap(BehaviourTree::new, BehaviourTree::root);

    /** Used when an NPC's tree is missing: does nothing (the alert states still change). */
    public static final BehaviourTree EMPTY = new BehaviourTree(new Composites.Selector(java.util.List.of()));
}
