package com.mcspacewizard.emergentstealth.ai.behaviour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Nodes that wrap one child. */
public final class Decorators {
    private Decorators() {}

    public static final BtNodeType<Cooldown> COOLDOWN = new BtNodeType<>("cooldown", RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.floatRange(0, 3600).fieldOf("seconds").forGetter(Cooldown::seconds),
            BtNodes.CODEC.fieldOf("child").forGetter(Cooldown::child)
    ).apply(i, Cooldown::new)));
    public static final BtNodeType<Timeout> TIMEOUT = new BtNodeType<>("timeout", RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.floatRange(0, 3600).fieldOf("seconds").forGetter(Timeout::seconds),
            BtNodes.CODEC.fieldOf("child").forGetter(Timeout::child)
    ).apply(i, Timeout::new)));
    public static final BtNodeType<Chance> CHANCE = new BtNodeType<>("chance", RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.floatRange(0, 1).fieldOf("chance").forGetter(Chance::chance),
            BtNodes.CODEC.fieldOf("child").forGetter(Chance::child)
    ).apply(i, Chance::new)));
    public static final BtNodeType<Invert> INVERT = new BtNodeType<>("invert",
            BtNodes.CODEC.fieldOf("child").xmap(Invert::new, Invert::child));
    public static final BtNodeType<Optional> OPTIONAL = new BtNodeType<>("optional",
            BtNodes.CODEC.fieldOf("child").xmap(Optional::new, Optional::child));
    public static final BtNodeType<OncePerState> ONCE_PER_STATE = new BtNodeType<>("once_per_state",
            BtNodes.CODEC.fieldOf("child").xmap(OncePerState::new, OncePerState::child));

    private static final class Tick {
        long value = Long.MIN_VALUE;
    }

    private static final class Roll {
        Boolean passed;
    }

    /** Fails while cooling down; the cooldown starts when the child finishes. Survives state changes. */
    public record Cooldown(float seconds, BtNode child) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return COOLDOWN;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            Tick readyAt = ctx.persistent(this, Tick::new);
            if (ctx.now() < readyAt.value) {
                return BtStatus.FAILURE;
            }
            BtStatus status = child.tick(ctx);
            if (status != BtStatus.RUNNING) {
                readyAt.value = ctx.now() + Math.round(seconds * 20.0F);
            }
            return status;
        }

        @Override
        public void abort(BtContext ctx) {
            child.abort(ctx);
        }
    }

    /** Fails (and interrupts the child) if the child runs for longer than {@code seconds}. */
    public record Timeout(float seconds, BtNode child) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return TIMEOUT;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            Tick started = ctx.state(this, Tick::new);
            if (started.value == Long.MIN_VALUE) {
                started.value = ctx.now();
            }
            if (ctx.now() - started.value > Math.round(seconds * 20.0F)) {
                abort(ctx);
                return BtStatus.FAILURE;
            }
            BtStatus status = child.tick(ctx);
            if (status != BtStatus.RUNNING) {
                ctx.clear(this);
            }
            return status;
        }

        @Override
        public void abort(BtContext ctx) {
            child.abort(ctx);
            ctx.clear(this);
        }
    }

    /** Rolls once when entered: runs the child with the given probability, otherwise fails. */
    public record Chance(float chance, BtNode child) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return CHANCE;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            Roll roll = ctx.state(this, Roll::new);
            if (roll.passed == null) {
                roll.passed = ctx.npc().getRandom().nextFloat() < chance;
            }
            BtStatus status = roll.passed ? child.tick(ctx) : BtStatus.FAILURE;
            if (status != BtStatus.RUNNING) {
                ctx.clear(this);
            }
            return status;
        }

        @Override
        public void abort(BtContext ctx) {
            child.abort(ctx);
            ctx.clear(this);
        }
    }

    public record Invert(BtNode child) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return INVERT;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            return switch (child.tick(ctx)) {
                case SUCCESS -> BtStatus.FAILURE;
                case FAILURE -> BtStatus.SUCCESS;
                case RUNNING -> BtStatus.RUNNING;
            };
        }

        @Override
        public void abort(BtContext ctx) {
            child.abort(ctx);
        }
    }

    /** Runs the child but never fails: "do this if you can", e.g. a shout that may be on cooldown. */
    public record Optional(BtNode child) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return OPTIONAL;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            return child.tick(ctx) == BtStatus.RUNNING ? BtStatus.RUNNING : BtStatus.SUCCESS;
        }

        @Override
        public void abort(BtContext ctx) {
            child.abort(ctx);
        }
    }

    /**
     * Runs the child once per alert-state episode (e.g. one bark on spotting you). Once done it just succeeds,
     * so the sequence it's in carries on.
     */
    public record OncePerState(BtNode child) implements BtNode {
        @Override
        public BtNodeType<?> type() {
            return ONCE_PER_STATE;
        }

        @Override
        public BtStatus tick(BtContext ctx) {
            Tick doneFor = ctx.persistent(this, Tick::new);
            long episode = ctx.brain().stateSince();
            if (doneFor.value == episode) {
                return BtStatus.SUCCESS;
            }
            BtStatus status = child.tick(ctx);
            if (status == BtStatus.RUNNING) {
                return BtStatus.RUNNING;
            }
            doneFor.value = episode;
            return BtStatus.SUCCESS;
        }

        @Override
        public void abort(BtContext ctx) {
            child.abort(ctx);
        }
    }

}
