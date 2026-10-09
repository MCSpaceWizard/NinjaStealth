package com.mcspacewizard.emergentstealth.authoring;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoute;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoutes;
import com.mcspacewizard.emergentstealth.ai.routine.Schedule;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Compounds being authored (design doc 32 §3 "Authoring flow"), one per author. A draft has an origin in the
 * world; everything added is stored relative to it, unturned. While a draft is open, each structure its author
 * places joins it as a module. Drafts live in memory until saved.
 *
 * <p>Zones and routes copied from the world stay <em>linked</em> by name: saving copies them again, so edits made
 * with the rope or the baton after adding them are kept. NPCs already recorded (by {@code add npcs} or the Muster
 * Roll) are remembered, so they are never recorded twice.
 */
public final class CompoundDrafts {
    private CompoundDrafts() {}

    /**
     * @param linkedZones  world zones copied in, re-copied on save
     * @param linkedRoutes world patrol routes copied in, re-copied on save
     * @param recorded     NPCs already recorded as spawns
     */
    public record Draft(Identifier id, ResourceKey<Level> dimension, BlockPos origin, Compound compound,
                        Set<String> linkedZones, Set<String> linkedRoutes, Set<UUID> recorded) {
        Draft with(Compound newCompound) {
            return new Draft(id, dimension, origin, newCompound, linkedZones, linkedRoutes, recorded);
        }

        private Draft linking(@Nullable String zone, @Nullable String route, List<UUID> npcs) {
            Set<String> zones = new LinkedHashSet<>(linkedZones);
            Set<String> routes = new LinkedHashSet<>(linkedRoutes);
            Set<UUID> npcSet = new LinkedHashSet<>(recorded);
            if (zone != null) {
                zones.add(zone);
            }
            if (route != null) {
                routes.add(route);
            }
            npcSet.addAll(npcs);
            return new Draft(id, dimension, origin, compound, Set.copyOf(zones), Set.copyOf(routes), Set.copyOf(npcSet));
        }

        private Draft unlinking(@Nullable String zone, @Nullable String route) {
            Set<String> zones = new LinkedHashSet<>(linkedZones);
            Set<String> routes = new LinkedHashSet<>(linkedRoutes);
            zones.remove(zone);
            routes.remove(route);
            return new Draft(id, dimension, origin, compound, Set.copyOf(zones), Set.copyOf(routes), recorded);
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

    public static Draft start(UUID author, Identifier id, ResourceKey<Level> dimension, BlockPos origin, Compound compound) {
        Draft draft = new Draft(id, dimension, origin, compound, Set.of(), Set.of(), Set.of());
        DRAFTS.put(author, draft);
        return draft;
    }

    public static void clear() {
        DRAFTS.clear();
    }

    public static @Nullable Draft close(UUID author) {
        return DRAFTS.remove(author);
    }

    /** Adds a placed structure to the author's draft, if they have one in that dimension. Returns the draft, or null. */
    public static @Nullable Draft onPlaced(UUID author, StructurePlacement.Placed placed) {
        Draft draft = DRAFTS.get(author);
        if (draft == null || !draft.dimension().equals(placed.dimension())) {
            return null;
        }
        return update(author, draft.compound().withModule(module(draft, placed)));
    }

    /**
     * After the author placed a structure (viewer or command): adds it to their open draft and tells them, or tells
     * them why not.
     */
    public static void joinDraft(UUID author, Consumer<Component> tell) {
        StructurePlacement.Placed placed = StructurePlacement.last(author);
        Draft open = DRAFTS.get(author);
        if (placed == null || open == null) {
            return;
        }
        Draft draft = onPlaced(author, placed);
        tell.accept(draft == null
                ? Component.translatable("message.emergentstealth.compound.other_dimension", open.id().toString())
                : Component.translatable("message.emergentstealth.compound.module_added", draft.id().toString(), draft.compound().structures().size()));
    }

    private static Compound.Module module(Draft draft, StructurePlacement.Placed placed) {
        return new Compound.Module(placed.template(), draft.local(placed.origin()), placed.rotation(), placed.mirror(), 0);
    }

    /** Copies a world patrol route into the draft (keeping its name) and links it, so saving copies it again. */
    public static Draft addRoute(UUID author, Draft draft, PatrolRoute route) {
        Draft added = update(author, draft.compound().withRoute(local(draft, route)));
        return put(author, added.linking(null, route.name(), List.of()));
    }

    private static PatrolRoute local(Draft draft, PatrolRoute worldRoute) {
        return Compound.moveRoute(worldRoute, worldRoute.name(), Transform.IDENTITY, BlockPos.ZERO.subtract(draft.origin()));
    }

    /**
     * Records an NPC as it stands (archetype, place, facing, behaviour and schedule) as a spawn in the draft.
     * An NPC the draft already recorded is skipped (returns the draft unchanged).
     */
    public static Draft addNpc(UUID author, Draft draft, StealthNpc npc) {
        if (draft.recorded().contains(npc.getUUID())) {
            return draft;
        }
        Compound.Spawn spawn = new Compound.Spawn(npc.getArchetypeId(), draft.local(npc.blockPosition()), npc.getYRot(),
                Optional.ofNullable(npc.getBehaviourOverride()), localSchedule(draft, npc.getSchedule()), 1);
        return addSpawn(author, draft, spawn, List.of(npc.getUUID()));
    }

    /** Adds a spawn (already in compound space), remembering the NPCs it stands for in the world. */
    public static Draft addSpawn(UUID author, Draft draft, Compound.Spawn spawn, List<UUID> npcs) {
        Draft added = update(author, draft.compound().withSpawn(spawn));
        return put(author, added.linking(null, null, npcs));
    }

    /** A world schedule in the draft's space (posts move; route names stay). */
    public static Schedule localSchedule(Draft draft, Schedule schedule) {
        return Compound.moveSchedule(schedule, name -> name, Transform.IDENTITY, BlockPos.ZERO.subtract(draft.origin()));
    }

    /** Takes a zone out of the draft (and unlinks it). */
    public static Draft removeZone(UUID author, Draft draft, String name) {
        return put(author, draft.with(draft.compound().withoutZone(name)).unlinking(name, null));
    }

    /** Takes a route out of the draft (and unlinks it). Spawns that walk it keep the name. */
    public static Draft removeRoute(UUID author, Draft draft, String name) {
        return put(author, draft.with(draft.compound().withoutRoute(name)).unlinking(null, name));
    }

    /** Takes the spawn at {@code index} out of the draft. Its NPCs stay in the world. */
    public static Draft removeSpawn(UUID author, Draft draft, int index) {
        return put(author, draft.with(draft.compound().withoutSpawn(index)));
    }

    /**
     * Copies the draft's linked zones and routes again from the world, so the saved compound has their latest
     * shape. Ones deleted from the world keep their last copy. Returns the refreshed draft.
     */
    public static Draft refreshLinks(MinecraftServer server, UUID author, Draft draft) {
        ServerLevel level = server.getLevel(draft.dimension());
        return level == null ? draft : refreshLinks(author, draft, level);
    }

    /** {@link #refreshLinks(MinecraftServer, UUID, Draft)} with the draft's level. */
    public static Draft refreshLinks(UUID author, Draft draft, ServerLevel level) {
        Compound compound = draft.compound();
        Zones zones = Zones.get(level);
        for (String name : draft.linkedZones()) {
            Optional<Zone> zone = zones.get(name);
            if (zone.isPresent()) {
                compound = compound.withZone(zone.get().placed(name, Transform.IDENTITY, BlockPos.ZERO.subtract(draft.origin())));
            }
        }
        PatrolRoutes routes = PatrolRoutes.get(level);
        for (String name : draft.linkedRoutes()) {
            Optional<PatrolRoute> route = routes.get(name);
            if (route.isPresent()) {
                compound = compound.withRoute(local(draft, route.get()));
            }
        }
        return put(author, draft.with(compound));
    }

    /** Adds (or replaces) a zone made of one world box. */
    public static Draft addZone(UUID author, Draft draft, String name, Zone.Access access, BoundingBox worldBox, Optional<Zone.Hours> hours) {
        BoundingBox local = worldBox.moved(-draft.origin().getX(), -draft.origin().getY(), -draft.origin().getZ());
        // A box typed by hand replaces any linked world zone of that name.
        return put(author, update(author, draft.compound().withZone(new Zone(name, access, List.of(local), hours))).unlinking(name, null));
    }

    /** Copies a world zone (all its boxes, made with the Surveyor's Rope) into the draft, keeping its name, and links it. */
    public static Draft addZone(UUID author, Draft draft, Zone worldZone) {
        Draft added = update(author, draft.compound().withZone(worldZone.placed(worldZone.name(), Transform.IDENTITY, BlockPos.ZERO.subtract(draft.origin()))));
        return put(author, added.linking(worldZone.name(), null, List.of()));
    }

    private static Draft update(UUID author, Compound compound) {
        return put(author, DRAFTS.get(author).with(compound));
    }

    private static Draft put(UUID author, Draft draft) {
        DRAFTS.put(author, draft);
        return draft;
    }
}
