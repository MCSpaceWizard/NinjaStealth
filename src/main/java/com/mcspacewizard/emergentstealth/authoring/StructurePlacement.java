package com.mcspacewizard.emergentstealth.authoring;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * Places structures for the viewer and its commands (design doc 32 §1), remembering what each author's last
 * placement replaced so it can be undone once. Placement uses the template's own transform with the pivot at
 * the origin, exactly like the client's ghost preview.
 */
public final class StructurePlacement {
    private StructurePlacement() {}

    /** Snapshots bigger than this many blocks aren't kept (undo is then unavailable for that placement). */
    public static final int MAX_UNDO_VOLUME = 2_000_000;
    /**
     * How far around the structure the snapshot reaches: water and lava in a structure flow out of its box
     * (up to 7 blocks on the level, further down), and undo has to take that back too.
     */
    public static final int FLOW_MARGIN = 8;
    /** Restoring sets blocks back exactly: no drops, no shape updates popping neighbours, no block entity side effects. */
    private static final int RESTORE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    /** Who placed (a player's UUID, or {@link #CONSOLE} for commands without one) → what to restore. */
    private static final Map<UUID, Snapshot> UNDO = new HashMap<>();
    public static final UUID CONSOLE = new UUID(0L, 0L);

    /** What a box (the placement plus {@link #FLOW_MARGIN}) held before a placement. */
    private record Snapshot(ServerLevel level, BoundingBox placed, BoundingBox box, BlockState[] states, Map<Integer, CompoundTag> blockEntities,
                            Set<UUID> addedEntities) {}

    public static StructurePlaceSettings settings(Rotation rotation, Mirror mirror) {
        return new StructurePlaceSettings().setRotation(rotation).setMirror(mirror);
    }

    /** The box a placement covers. */
    public static BoundingBox box(StructureTemplate template, BlockPos origin, Rotation rotation, Mirror mirror) {
        return template.getBoundingBox(settings(rotation, mirror), origin);
    }

    /**
     * Places a template and remembers the replaced blocks for {@code author}. Returns the box it covers, or null
     * if the template doesn't exist.
     */
    public static @Nullable BoundingBox place(ServerLevel level, UUID author, Identifier id, BlockPos origin, Rotation rotation, Mirror mirror) {
        StructureTemplate template = level.getServer().getStructureManager().get(id).orElse(null);
        if (template == null) {
            return null;
        }
        BoundingBox box = box(template, origin, rotation, mirror);
        BoundingBox around = new BoundingBox(box.minX() - FLOW_MARGIN, Math.max(level.getMinY(), box.minY() - FLOW_MARGIN),
                box.minZ() - FLOW_MARGIN, box.maxX() + FLOW_MARGIN, box.maxY(), box.maxZ() + FLOW_MARGIN);
        long volume = (long) around.getXSpan() * around.getYSpan() * around.getZSpan();
        Snapshot snapshot = volume <= MAX_UNDO_VOLUME ? snapshot(level, box, around) : null;
        Set<UUID> before = entitiesIn(level, around);
        template.placeInWorld(level, origin, origin, settings(rotation, mirror), level.getRandom(), Block.UPDATE_CLIENTS);
        if (snapshot != null) {
            snapshot.addedEntities().addAll(entitiesIn(level, around));
            snapshot.addedEntities().removeAll(before);
            UNDO.put(author, snapshot);
        } else {
            UNDO.remove(author);
        }
        return box;
    }

    /**
     * Restores what the author's last placement replaced (and anything that flowed out of it since). Returns the
     * placement's box, or null if there was nothing.
     */
    public static @Nullable BoundingBox undo(UUID author) {
        Snapshot snapshot = UNDO.remove(author);
        if (snapshot == null) {
            return null;
        }
        ServerLevel level = snapshot.level();
        BoundingBox box = snapshot.box();
        for (UUID added : snapshot.addedEntities()) {
            Entity entity = level.getEntity(added);
            if (entity != null) {
                entity.discard();
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    pos.set(x, y, z);
                    // Clear block entities first so the restored block doesn't keep the placed one's contents.
                    level.removeBlockEntity(pos);
                    level.setBlock(pos, snapshot.states()[i], RESTORE_FLAGS);
                    CompoundTag tag = snapshot.blockEntities().get(i);
                    if (tag != null) {
                        BlockEntity restored = BlockEntity.loadStatic(pos.immutable(), snapshot.states()[i], tag, level.registryAccess());
                        if (restored != null) {
                            level.setBlockEntity(restored);
                        }
                    }
                    i++;
                }
            }
        }
        return snapshot.placed();
    }

    public static boolean canUndo(UUID author) {
        return UNDO.containsKey(author);
    }

    private static Snapshot snapshot(ServerLevel level, BoundingBox placed, BoundingBox box) {
        BlockState[] states = new BlockState[box.getXSpan() * box.getYSpan() * box.getZSpan()];
        Map<Integer, CompoundTag> blockEntities = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    pos.set(x, y, z);
                    states[i] = level.getBlockState(pos);
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity != null) {
                        blockEntities.put(i, blockEntity.saveWithFullMetadata(level.registryAccess()));
                    }
                    i++;
                }
            }
        }
        return new Snapshot(level, placed, box, states, blockEntities, new HashSet<>());
    }

    /** Non-player entities inside a box (a structure's armour stands, fish, …). */
    private static Set<UUID> entitiesIn(ServerLevel level, BoundingBox box) {
        Set<UUID> ids = new HashSet<>();
        for (Entity entity : level.getEntities((Entity) null, AABB.of(box), e -> !(e instanceof Player))) {
            ids.add(entity.getUUID());
        }
        return ids;
    }
}
