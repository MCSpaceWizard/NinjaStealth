package com.mcspacewizard.emergentstealth.ai.behaviour;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** All behaviour-tree node types, keyed by their JSON {@code "type"}. */
public final class BtNodes {
    private BtNodes() {}

    /** Any node, dispatched on {@code "type"}. Lazy because composites contain nodes. */
    public static final Codec<BtNode> CODEC = Codec.lazyInitialized(() -> BtNodes.TYPE_CODEC.dispatch("type", BtNode::type, BtNodeType::codec));
    public static final Codec<List<BtNode>> LIST_CODEC = Codec.lazyInitialized(() -> CODEC.listOf());

    private static final Map<String, BtNodeType<?>> TYPES = new LinkedHashMap<>();

    private static final Codec<BtNodeType<?>> TYPE_CODEC = Codec.STRING.comapFlatMap(
            name -> {
                BtNodeType<?> type = TYPES.get(name);
                return type != null ? DataResult.success(type) : DataResult.error(() -> "Unknown behaviour node type: " + name);
            },
            BtNodeType::name);

    static {
        for (BtNodeType<?> type : List.of(
                Composites.SEQUENCE, Composites.SELECTOR, Composites.RANDOM,
                Decorators.COOLDOWN, Decorators.TIMEOUT, Decorators.CHANCE, Decorators.INVERT, Decorators.OPTIONAL, Decorators.ONCE_PER_STATE,
                Conditions.STATE, Conditions.CAUSE, Conditions.CAN_SEE_TARGET, Conditions.HAS_ATTACK_TOKEN,
                Conditions.NEAR_POI, Conditions.ROLE,
                Actions.MOVE_TO, Actions.LOOK_AT_POI, Actions.LOOK_AROUND, Actions.WAIT, Actions.STOP, Actions.ATTACK,
                Actions.HOLD_RING, Actions.SEARCH, Actions.BARK, Actions.SHOUT)) {
            TYPES.put(type.name(), type);
        }
    }

    public static java.util.Set<String> typeNames() {
        return TYPES.keySet();
    }
}
