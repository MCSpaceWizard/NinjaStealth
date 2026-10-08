package com.mcspacewizard.emergentstealth.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.progression.PlayerProgression;
import com.mcspacewizard.emergentstealth.progression.SkillDefinition;
import com.mcspacewizard.emergentstealth.progression.SkillPath;
import com.mcspacewizard.emergentstealth.progression.Skills;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * What the skill tree screen shows for one path (design docs 26 and 31 §3.2): nodes at their grid
 * positions with a state each, and prerequisite edges. States come from {@link Skills#blocker}, the same
 * check the server runs, so the screen never disagrees with the server about what can be bought.
 */
public final class SkillTreeModel {
    private SkillTreeModel() {}

    public enum NodeState {
        /** Learned. */
        OWNED,
        /** Can be bought right now. */
        AVAILABLE,
        /** Prerequisites met, not enough points. */
        NEEDS_POINTS,
        /** Prerequisites missing. */
        LOCKED,
        /** Needs a mastery challenge (not obtainable yet). */
        CAPSTONE
    }

    public record Node(Identifier id, SkillDefinition skill, NodeState state, @Nullable String blocker) {
        public int column() {
            return skill.column();
        }

        public int row() {
            return skill.row();
        }
    }

    /**
     * A prerequisite line from {@code from} (required) to {@code to}.
     *
     * @param satisfied the required skill is owned
     * @param anyOf     the target needs any one of its prerequisites, not all
     */
    public record Edge(Identifier from, Identifier to, boolean satisfied, boolean anyOf) {}

    public record PathTree(SkillPath path, List<Node> nodes, List<Edge> edges, int minColumn, int maxColumn, int maxRow,
                           int points, int insight) {
        public @Nullable Node node(Identifier id) {
            for (Node node : nodes) {
                if (node.id.equals(id)) {
                    return node;
                }
            }
            return null;
        }

        public boolean isEmpty() {
            return nodes.isEmpty();
        }
    }

    public static PathTree build(Player player, SkillPath path) {
        Registry<SkillDefinition> registry = Skills.registry(player);
        PlayerProgression progression = Skills.progression(player);
        List<Node> nodes = new ArrayList<>();
        Map<Identifier, SkillDefinition> inPath = new HashMap<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<SkillDefinition>, SkillDefinition> e : registry.entrySet()) {
            SkillDefinition skill = e.getValue();
            if (skill.path() != path) {
                continue;
            }
            Identifier id = e.getKey().identifier();
            inPath.put(id, skill);
            String blocker = Skills.blocker(player, id);
            nodes.add(new Node(id, skill, stateOf(blocker), blocker));
        }
        nodes.sort(Comparator.comparingInt(Node::row).thenComparingInt(Node::column).thenComparing(n -> n.id().toString()));
        List<Edge> edges = new ArrayList<>();
        int minColumn = Integer.MAX_VALUE;
        int maxColumn = Integer.MIN_VALUE;
        int maxRow = 0;
        for (Node node : nodes) {
            minColumn = Math.min(minColumn, node.column());
            maxColumn = Math.max(maxColumn, node.column());
            maxRow = Math.max(maxRow, node.row());
            for (Identifier required : node.skill().requires()) {
                if (inPath.containsKey(required)) {
                    edges.add(new Edge(required, node.id(), progression.has(required),
                            !node.skill().requireAll() && node.skill().requires().size() > 1));
                }
            }
        }
        if (nodes.isEmpty()) {
            minColumn = 0;
            maxColumn = 0;
        }
        return new PathTree(path, List.copyOf(nodes), List.copyOf(edges), minColumn, maxColumn, maxRow,
                progression.points(path), progression.insight(path));
    }

    /** The screen state for a {@link Skills#blocker} result. */
    public static NodeState stateOf(@Nullable String blocker) {
        if (blocker == null) {
            return NodeState.AVAILABLE;
        }
        return switch (blocker) {
            case "owned" -> NodeState.OWNED;
            case "points" -> NodeState.NEEDS_POINTS;
            case "capstone" -> NodeState.CAPSTONE;
            default -> NodeState.LOCKED;
        };
    }

    /**
     * Keyboard navigation: the nearest node from {@code from} in grid direction ({@code dx}, {@code dy}),
     * preferring the next row (up/down) or the same row (left/right). Null if there's none that way.
     */
    public static @Nullable Node neighbour(List<Node> nodes, Node from, int dx, int dy) {
        Node best = null;
        double bestScore = Double.MAX_VALUE;
        for (Node node : nodes) {
            if (node == from) {
                continue;
            }
            int ox = node.column() - from.column();
            int oy = node.row() - from.row();
            int along = ox * dx + oy * dy;
            if (along <= 0) {
                continue;
            }
            int across = Math.abs(ox * dy) + Math.abs(oy * dx);
            // Up/down: the nearest row first. Left/right: the same row first.
            double score = dy != 0 ? along * 10 + across : across * 10 + along;
            if (score < bestScore) {
                bestScore = score;
                best = node;
            }
        }
        return best;
    }
}
