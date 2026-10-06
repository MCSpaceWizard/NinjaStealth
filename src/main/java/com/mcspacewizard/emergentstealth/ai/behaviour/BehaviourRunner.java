package com.mcspacewizard.emergentstealth.ai.behaviour;

import java.util.IdentityHashMap;
import java.util.Map;

import com.mcspacewizard.emergentstealth.entity.StealthNpc;

import net.minecraft.server.level.ServerLevel;

/** One NPC's runtime for its behaviour tree: node state plus what the last tick asked for. */
public final class BehaviourRunner {
    final Map<BtNode, Object> states = new IdentityHashMap<>();
    final Map<BtNode, Object> persistent = new IdentityHashMap<>();
    boolean wantsMelee;
    String activeLabel = "-";
    private BehaviourTree tree = BehaviourTree.EMPTY;

    /** Ticks the tree once. Returns whether an attack action asked for melee this tick. */
    public boolean tick(StealthNpc npc, ServerLevel level, BehaviourTree current, long now) {
        if (current != tree) {
            // Datapack reload or archetype change: start over.
            reset();
            persistent.clear();
            tree = current;
        }
        wantsMelee = false;
        activeLabel = "-";
        BtContext ctx = new BtContext(npc, level, now, this);
        tree.root().tick(ctx);
        return wantsMelee;
    }

    /** Interrupts everything (alert state changed, or the NPC calmed down). */
    public void reset() {
        states.clear();
        activeLabel = "-";
        wantsMelee = false;
    }

    public String activeLabel() {
        return activeLabel;
    }
}
