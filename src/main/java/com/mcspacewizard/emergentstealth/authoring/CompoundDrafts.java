package com.mcspacewizard.emergentstealth.authoring;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoute;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Compounds being authored (design doc 32 §3 "Authoring flow"), one per author. A draft has an origin in the
 * world; everything added is stored relative to it, unturned. While a draft is open, each structure its author
 * places joins it as a module. Drafts live in memory until saved.
 */
public final class CompoundDrafts {
    private CompoundDrafts() {}

    public record Draft(Identifier id, BlockPos origin, Compound compound) {
        Draft with(Compound newCompound) {
            return new Draft(id, origin, newCompound);
        }

        /** A world position relative to the draft's origin. */
        public BlockPos local(BlockPos world) {
            return world.subtract(origin);
        }
    }

    private static final Map<UUID, Draft> DRAFTS = new HashMap<>();

    public static @Nullable Draft get(UUID author) {
        return DRAFTS.get(author);
    }

    public static Draft start(UUID author, Identifier id, BlockPos origin, Compound compound) {
        Draft draft = new Draft(id, origin, compound);
        DRAFTS.put(author, draft);
        return draft;
    }

    public static @Nullable Draft close(UUID author) {
        return DRAFTS.remove(author);
    }

    /** Adds a structure the author just placed to their draft, if they have one. Returns the draft, or null. */
    public static @Nullable Draft onPlaced(UUID author, StructurePlacement.Placed placed) {
        Draft draft = DRAFTS.get(author);
        if (draft == null) {
            return null;
        }
        return update(author, draft.compound().withModule(module(draft, placed)));
    }

    public static Compound.Module module(Draft draft, StructurePlacement.Placed placed) {
        return new Compound.Module(placed.template(), draft.local(placed.origin()), placed.rotation(), placed.mirror(), 0);
    }

    /** Copies a world patrol route into the draft (keeping its name). */
    public static Draft addRoute(UUID author, Draft draft, PatrolRoute route) {
        return update(author, draft.compound().withRoute(Compound.moveRoute(route, route.name(), Transform.IDENTITY, BlockPos.ZERO.subtract(draft.origin()))));
    }

    /** Records an NPC as it stands (archetype, place, facing, behaviour and schedule) as a spawn in the draft. */
    public static Draft addNpc(UUID author, Draft draft, StealthNpc npc) {
        Compound.Spawn spawn = new Compound.Spawn(npc.getArchetypeId(), draft.local(npc.blockPosition()), npc.getYRot(),
                java.util.Optional.ofNullable(npc.getBehaviourOverride()),
                Compound.moveSchedule(npc.getSchedule(), name -> name, Transform.IDENTITY, BlockPos.ZERO.subtract(draft.origin())), 1);
        return update(author, draft.compound().withSpawn(spawn));
    }

    /** Adds (or replaces) a zone made of one world box. */
    public static Draft addZone(UUID author, Draft draft, String name, Zone.Access access, BoundingBox worldBox, java.util.Optional<Zone.Hours> hours) {
        BoundingBox local = worldBox.moved(-draft.origin().getX(), -draft.origin().getY(), -draft.origin().getZ());
        return update(author, draft.compound().withZone(new Zone(name, access, java.util.List.of(local), hours)));
    }

    private static Draft update(UUID author, Compound compound) {
        Draft draft = DRAFTS.get(author).with(compound);
        DRAFTS.put(author, draft);
        return draft;
    }
}
