package com.mcspacewizard.emergentstealth.ai.behaviour;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Nodes with several children. */
public final class Composites {
    private Composites() {}

    public static final BtNodeType<Sequence> SEQUENCE = new BtNodeType<>("sequence",
            BtNodes.LIST_CODEC.fieldOf("children").xmap(Sequence::new, Sequence::children));
    public static final BtNodeType<Selector> SELECTOR = new BtNodeType<>("selector",
            BtNodes.LIST_CODEC.fieldOf("children").xmap(Selector::new, Selector::children));
    public static final BtNodeType<Random> RANDOM = new BtNodeType<>("random", RecordCodecBuilder.mapCodec(i -> i.group(
            BtNodes.LIST_CODEC.fieldOf("children").forGetter(Random::children),
            Codec.floatRange(0, 1000).listOf().optionalFieldOf("weights").forGetter(Random::weights)
    ).apply(i, Random::new)));

    /** Mutable cursor: which child is running (-1 = none). */
    private static final class Cursor {
        int index = -1;
    }

    /**
     * Runs children in order until one fails. Remembers the running child, but re-checks the
     * {@link BtCondition}s before it every tick.
     */
    public record Sequence(List<BtNode> children) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return SEQUENCE;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            Cursor cursor = ctx.state(this, Cursor::new);
            int start = Math.max(0, cursor.index);
            for (int i = 0; i < start; i++) {
                if (children.get(i) instanceof BtCondition condition && !condition.test(ctx)) {
                    abort(ctx);
                    return BtStatus.FAILURE;
                }
            }
            for (int i = start; i < children.size(); i++) {
                BtStatus status = children.get(i).tick(ctx);
                if (status == BtStatus.RUNNING) {
                    cursor.index = i;
                    return BtStatus.RUNNING;
                }
                if (status == BtStatus.FAILURE) {
                    ctx.clear(this);
                    return BtStatus.FAILURE;
                }
            }
            ctx.clear(this);
            return BtStatus.SUCCESS;
        }

        @Override
        public void abort(BtContext ctx) {
            Cursor cursor = ctx.state(this, Cursor::new);
            if (cursor.index >= 0 && cursor.index < children.size()) {
                children.get(cursor.index).abort(ctx);
            }
            ctx.clear(this);
        }
    }

    /**
     * Priority selector: tries children from the first every tick and runs the first that doesn't fail. A
     * higher-priority child taking over interrupts the one that was running.
     */
    public record Selector(List<BtNode> children) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return SELECTOR;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            Cursor cursor = ctx.state(this, Cursor::new);
            for (int i = 0; i < children.size(); i++) {
                BtStatus status = children.get(i).tick(ctx);
                if (status == BtStatus.FAILURE) {
                    continue;
                }
                if (cursor.index >= 0 && cursor.index != i && cursor.index < children.size()) {
                    children.get(cursor.index).abort(ctx);
                }
                cursor.index = status == BtStatus.RUNNING ? i : -1;
                return status;
            }
            cursor.index = -1;
            return BtStatus.FAILURE;
        }

        @Override
        public void abort(BtContext ctx) {
            Cursor cursor = ctx.state(this, Cursor::new);
            if (cursor.index >= 0 && cursor.index < children.size()) {
                children.get(cursor.index).abort(ctx);
            }
            ctx.clear(this);
        }
    }

    /** Picks one child at random (optionally weighted) and runs it to completion. */
    public record Random(List<BtNode> children, Optional<List<Float>> weights) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return RANDOM;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            if (children.isEmpty()) {
                return BtStatus.FAILURE;
            }
            Cursor cursor = ctx.state(this, Cursor::new);
            if (cursor.index < 0) {
                cursor.index = pick(ctx);
            }
            BtStatus status = children.get(cursor.index).tick(ctx);
            if (status != BtStatus.RUNNING) {
                ctx.clear(this);
            }
            return status;
        }

        private int pick(BtContext ctx) {
            List<Float> w = weights.orElse(List.of());
            float total = 0;
            for (int i = 0; i < children.size(); i++) {
                total += i < w.size() ? w.get(i) : 1.0F;
            }
            float roll = ctx.npc().getRandom().nextFloat() * total;
            for (int i = 0; i < children.size(); i++) {
                roll -= i < w.size() ? w.get(i) : 1.0F;
                if (roll < 0) {
                    return i;
                }
            }
            return children.size() - 1;
        }

        @Override
        public void abort(BtContext ctx) {
            Cursor cursor = ctx.state(this, Cursor::new);
            if (cursor.index >= 0) {
                children.get(cursor.index).abort(ctx);
            }
            ctx.clear(this);
        }
    }
}
