package com.mcspacewizard.emergentstealth.ai.behaviour;

import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mcspacewizard.emergentstealth.ai.brain.AlertState;
import com.mcspacewizard.emergentstealth.ai.brain.PoiCause;
import com.mcspacewizard.emergentstealth.ai.group.AttackTokens;
import com.mcspacewizard.emergentstealth.data.Archetype;
import com.mcspacewizard.emergentstealth.data.NpcRole;

import net.minecraft.world.phys.Vec3;

/** Checks (design doc 14 §3). */
public final class Conditions {
    private Conditions() {}

    public static final Codec<AlertState> ALERT_STATE_CODEC = Codec.STRING.comapFlatMap(
            name -> {
                try {
                    return DataResult.success(AlertState.valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown alert state: " + name);
                }
            },
            state -> state.name().toLowerCase(Locale.ROOT));

    public static final BtNodeType<StateIs> STATE = new BtNodeType<>("state",
            ALERT_STATE_CODEC.listOf().fieldOf("states").xmap(StateIs::new, StateIs::states));
    public static final BtNodeType<CauseIs> CAUSE = new BtNodeType<>("cause",
            PoiCause.CODEC.listOf().fieldOf("causes").xmap(CauseIs::new, CauseIs::causes));
    public static final BtNodeType<CanSeeTarget> CAN_SEE_TARGET = new BtNodeType<>("can_see_target", MapCodec.unit(CanSeeTarget::new));
    public static final BtNodeType<HasAttackToken> HAS_ATTACK_TOKEN = new BtNodeType<>("has_attack_token", MapCodec.unit(HasAttackToken::new));
    public static final BtNodeType<NearPoi> NEAR_POI = new BtNodeType<>("near_poi",
            Codec.floatRange(0, 256).fieldOf("distance").xmap(NearPoi::new, NearPoi::distance));
    public static final BtNodeType<RoleIs> ROLE = new BtNodeType<>("role",
            NpcRole.CODEC.listOf().fieldOf("roles").xmap(RoleIs::new, RoleIs::roles));

    /** The NPC's alert state is one of these. */
    public record StateIs(List<AlertState> states) implements BtCondition {
        @Override
        public BtNodeType<?> type() {
            return STATE;
        }

        @Override
        public boolean test(BtContext ctx) {
            return states.contains(ctx.brain().state());
        }
    }

    /** Why the NPC is interested in its point of interest: seen, heard, hurt or shout. */
    public record CauseIs(List<PoiCause> causes) implements BtCondition {
        @Override
        public BtNodeType<?> type() {
            return CAUSE;
        }

        @Override
        public boolean test(BtContext ctx) {
            return causes.contains(ctx.brain().cause());
        }
    }

    public record CanSeeTarget() implements BtCondition {
        @Override
        public BtNodeType<?> type() {
            return CAN_SEE_TARGET;
        }

        @Override
        public boolean test(BtContext ctx) {
            return ctx.brain().canSeeTarget();
        }
    }

    /** Gets (or keeps) one of the limited attack tokens on the alert target (design doc 14 §5). */
    public record HasAttackToken() implements BtCondition {
        @Override
        public BtNodeType<?> type() {
            return HAS_ATTACK_TOKEN;
        }

        @Override
        public boolean test(BtContext ctx) {
            java.util.UUID target = ctx.brain().alertTarget();
            return target != null && AttackTokens.get(ctx.level()).request(ctx.level(), ctx.npc(), target, ctx.now());
        }
    }

    public record NearPoi(float distance) implements BtCondition {
        @Override
        public BtNodeType<?> type() {
            return NEAR_POI;
        }

        @Override
        public boolean test(BtContext ctx) {
            Vec3 poi = ctx.brain().pointOfInterest();
            return poi != null && ctx.npc().position().distanceToSqr(poi) <= distance * distance;
        }
    }

    public record RoleIs(List<NpcRole> roles) implements BtCondition {
        @Override
        public BtNodeType<?> type() {
            return ROLE;
        }

        @Override
        public boolean test(BtContext ctx) {
            return ctx.npc().getArchetype().map(Archetype::role).map(roles::contains).orElse(false);
        }
    }
}
