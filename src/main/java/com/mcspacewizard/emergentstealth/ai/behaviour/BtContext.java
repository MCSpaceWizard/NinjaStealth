package com.mcspacewizard.emergentstealth.ai.behaviour;

import java.util.function.Supplier;

import com.mcspacewizard.emergentstealth.ai.brain.StealthBrain;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;

import net.minecraft.server.level.ServerLevel;

/** Everything a node needs during one tick of one NPC's tree. */
public final class BtContext {
    private final StealthNpc npc;
    private final ServerLevel level;
    private final long now;
    private final BehaviourRunner runner;

    BtContext(StealthNpc npc, ServerLevel level, long now, BehaviourRunner runner) {
        this.npc = npc;
        this.level = level;
        this.now = now;
        this.runner = runner;
    }

    public StealthNpc npc() {
        return npc;
    }

    public StealthBrain brain() {
        return npc.stealthBrain();
    }

    public ServerLevel level() {
        return level;
    }

    public long now() {
        return now;
    }

    /** This node's state for this NPC, created on first use. Cleared when the node is aborted or the alert state changes. */
    @SuppressWarnings("unchecked")
    public <T> T state(BtNode node, Supplier<T> create) {
        return (T) runner.states.computeIfAbsent(node, n -> create.get());
    }

    public void clear(BtNode node) {
        runner.states.remove(node);
    }

    /** Like {@link #state} but survives aborts and state changes (cooldowns, once-per-state markers). */
    @SuppressWarnings("unchecked")
    public <T> T persistent(BtNode node, Supplier<T> create) {
        return (T) runner.persistent.computeIfAbsent(node, n -> create.get());
    }

    /** Ask for melee this tick (only the attack action does; see {@link StealthBrain}). */
    public void requestMelee() {
        runner.wantsMelee = true;
    }

    /** Records the running action for the debug view. */
    public void running(String label) {
        runner.activeLabel = label;
    }
}
