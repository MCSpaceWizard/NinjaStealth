package com.mcspacewizard.emergentstealth.ai.behaviour;

/**
 * A behaviour-tree node (design doc 14 §3). Nodes are immutable definitions decoded from datapack JSON and
 * shared by every NPC using the tree; per-NPC state lives in the {@link BtContext}.
 */
public interface BtNode {
    BtNodeType<?> type();

    BtStatus tick(BtContext ctx);

    /** Called when a running node is interrupted: stop what it was doing and forget its state. */
    default void abort(BtContext ctx) {
        ctx.clear(this);
    }

    /** Short text for the debug view. */
    default String label() {
        return type().name();
    }
}
