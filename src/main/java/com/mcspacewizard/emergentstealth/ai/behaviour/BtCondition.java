package com.mcspacewizard.emergentstealth.ai.behaviour;

/**
 * A node that only checks something and never runs over several ticks. Sequences re-check the conditions
 * before their running child every tick, so a branch stops as soon as its condition no longer holds.
 */
public interface BtCondition extends BtNode {
    boolean test(BtContext ctx);

    @Override
    default BtStatus tick(BtContext ctx) {
        return test(ctx) ? BtStatus.SUCCESS : BtStatus.FAILURE;
    }
}
