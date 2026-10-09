package com.mcspacewizard.emergentstealth.authoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoute;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoutes;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Places a compound (design doc 32 §3): its templates, each turned by its own transform and then the compound's,
 * then its routes and zones (renamed per copy, {@code <compound>#<n>/<name>}) and its NPCs with their schedules
 * moved to match. The whole copy is one undoable placement, and is recorded in {@link PlacedCompounds}.
 * {@link #reset} brings a copy's markers and garrison back to how they were placed.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class CompoundPlacer {
    private CompoundPlacer() {}

    /** A placed copy, or the templates that couldn't be found (then nothing was placed). */
    public record Result(PlacedCompounds.@Nullable Copy copy, BoundingBox box, List<Identifier> missing) {}

    private record Piece(StructureTemplate template, BlockPos origin, Transform transform) {}

    public static Result place(ServerLevel level, UUID author, Identifier id, Compound compound, BlockPos origin, Transform transform) {
        List<Piece> pieces = new ArrayList<>();
        List<Identifier> missing = new ArrayList<>();
        List<BoundingBox> boxes = new ArrayList<>();
        for (Compound.Module module : compound.structures()) {
            StructureTemplate template = level.getServer().getStructureManager().get(module.template()).orElse(null);
            if (template == null) {
                missing.add(module.template());
                continue;
            }
            Transform turned = module.transform().then(transform);
            BlockPos at = transform.apply(module.offset()).offset(origin);
            pieces.add(new Piece(template, at, turned));
            boxes.add(StructurePlacement.box(template, at, turned.rotation(), turned.mirror()));
        }
        for (Compound.Spawn spawn : compound.spawns()) {
            boxes.add(new BoundingBox(transform.apply(spawn.pos()).offset(origin)));
        }
        BoundingBox box = BoundingBox.encapsulatingBoxes(boxes).orElseGet(() -> new BoundingBox(origin));
        if (!missing.isEmpty()) {
            return new Result(null, box, missing);
        }

        PlacedCompounds placed = PlacedCompounds.get(level);
        int copyId = placed.nextId();
        Map<String, String> routeNames = new HashMap<>();
        compound.routes().forEach(route -> routeNames.put(route.name(), PlacedCompounds.Copy.scoped(id, copyId, route.name())));
        List<String> zoneNames = compound.zones().stream().map(zone -> PlacedCompounds.Copy.scoped(id, copyId, zone.name())).toList();
        List<UUID> npcs = new ArrayList<>();
        List<Integer> npcSpawns = new ArrayList<>();

        StructurePlacement.tracked(level, author, box, () -> {
            for (Piece piece : pieces) {
                StructurePlacement.placeTemplate(level, piece.template(), piece.origin(), piece.transform().rotation(), piece.transform().mirror());
            }
            PatrolRoutes routes = PatrolRoutes.get(level);
            for (PatrolRoute route : compound.routes()) {
                routes.put(Compound.moveRoute(route, routeNames.get(route.name()), transform, origin));
            }
            Zones zones = Zones.get(level);
            for (int i = 0; i < compound.zones().size(); i++) {
                zones.put(compound.zones().get(i).placed(zoneNames.get(i), transform, origin));
            }
            for (int s = 0; s < compound.spawns().size(); s++) {
                Compound.Spawn spawn = compound.spawns().get(s);
                for (int n = 0; n < spawn.count(); n++) {
                    StealthNpc npc = spawnNpc(level, spawn, routeNames, transform, origin);
                    if (npc != null) {
                        npcs.add(npc.getUUID());
                        npcSpawns.add(s);
                    }
                }
            }
        }, () -> remove(level, copyId));

        PlacedCompounds.Copy copy = new PlacedCompounds.Copy(copyId, id, origin, transform.rotation(), transform.mirror(),
                List.copyOf(routeNames.values()), zoneNames, List.copyOf(npcs), List.copyOf(npcSpawns), Optional.of(box),
                worldLights(compound.lights(), transform, origin));
        placed.add(copy);
        return new Result(copy, box, List.of());
    }

    /** Forgets a copy's routes, zones and record (its blocks and NPCs stay). Returns false if there's no such copy. */
    public static boolean remove(ServerLevel level, int copyId) {
        PlacedCompounds placed = PlacedCompounds.get(level);
        PlacedCompounds.Copy copy = placed.get(copyId).orElse(null);
        if (copy == null) {
            return false;
        }
        PatrolRoutes routes = PatrolRoutes.get(level);
        copy.routes().forEach(routes::remove);
        Zones zones = Zones.get(level);
        copy.zones().forEach(zones::remove);
        return placed.remove(copyId);
    }

    /** The light rule's exceptions moved into the world. */
    private static Compound.Lights worldLights(Compound.Lights lights, Transform transform, BlockPos origin) {
        return new Compound.Lights(lights.relight(), lights.except().stream().map(p -> transform.apply(p).offset(origin)).toList());
    }

    /**
     * What a reset did.
     *
     * @param kept      NPCs still up and at their spawn's count
     * @param respawned NPCs spawned in place of missing ones
     * @param cleared   bodies (and extra NPCs) removed
     * @param skipped   NPCs not respawned because their spawn point isn't loaded
     */
    public record ResetResult(int kept, int respawned, int cleared, int skipped) {}

    /**
     * Resets a placed copy (doc 32 §3): its routes and zones are put back as placed, knocked-out and dead NPCs are
     * removed, and every spawn gets back to its count with fresh NPCs. Blocks are left as they are. NPCs that
     * aren't loaded are replaced too, and removed if they ever load ({@link PlacedCompounds#retire}).
     */
    public static ResetResult reset(ServerLevel level, PlacedCompounds.Copy copy, Compound compound) {
        Transform transform = copy.transform();
        BlockPos origin = copy.origin();
        Map<String, String> routeNames = new HashMap<>();
        compound.routes().forEach(route -> routeNames.put(route.name(), PlacedCompounds.Copy.scoped(copy.compound(), copy.id(), route.name())));
        PatrolRoutes routes = PatrolRoutes.get(level);
        for (PatrolRoute route : compound.routes()) {
            routes.put(Compound.moveRoute(route, routeNames.get(route.name()), transform, origin));
        }
        Zones zones = Zones.get(level);
        List<String> zoneNames = new ArrayList<>();
        for (Zone zone : compound.zones()) {
            String name = PlacedCompounds.Copy.scoped(copy.compound(), copy.id(), zone.name());
            zones.put(zone.placed(name, transform, origin));
            zoneNames.add(name);
        }
        // Routes and zones the compound no longer has go.
        copy.routes().stream().filter(name -> !routeNames.containsValue(name)).forEach(routes::remove);
        copy.zones().stream().filter(name -> !zoneNames.contains(name)).forEach(zones::remove);

        PlacedCompounds placed = PlacedCompounds.get(level);
        List<Integer> spawnOf = copy.spawnOfEachNpc(compound);
        int[] have = new int[compound.spawns().size()];
        List<UUID> npcs = new ArrayList<>();
        List<Integer> npcSpawns = new ArrayList<>();
        int kept = 0;
        int cleared = 0;
        for (int i = 0; i < copy.npcs().size(); i++) {
            UUID uuid = copy.npcs().get(i);
            int s = i < spawnOf.size() ? spawnOf.get(i) : -1;
            Entity entity = level.getEntity(uuid);
            if (entity instanceof StealthNpc npc && npc.isAlive() && !npc.isBody()
                    && s >= 0 && s < have.length && have[s] < compound.spawns().get(s).count()) {
                have[s]++;
                kept++;
                npcs.add(uuid);
                npcSpawns.add(s);
            } else if (entity != null) {
                entity.discard();
                cleared++;
            } else {
                placed.retire(uuid);
            }
        }
        int respawned = 0;
        int skipped = 0;
        for (int s = 0; s < have.length; s++) {
            Compound.Spawn spawn = compound.spawns().get(s);
            for (int n = have[s]; n < spawn.count(); n++) {
                if (!level.isLoaded(transform.apply(spawn.pos()).offset(origin))) {
                    skipped++;
                    continue;
                }
                StealthNpc npc = spawnNpc(level, spawn, routeNames, transform, origin);
                if (npc != null) {
                    respawned++;
                    npcs.add(npc.getUUID());
                    npcSpawns.add(s);
                }
            }
        }
        placed.replace(new PlacedCompounds.Copy(copy.id(), copy.compound(), origin, copy.rotation(), copy.mirror(),
                List.copyOf(routeNames.values()), List.copyOf(zoneNames), List.copyOf(npcs), List.copyOf(npcSpawns), copy.box(),
                worldLights(compound.lights(), transform, origin)));
        return new ResetResult(kept, respawned, cleared, skipped);
    }

    /** An NPC a reset replaced while it wasn't loaded is removed when it loads. */
    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof StealthNpc npc && event.getLevel() instanceof ServerLevel level
                && PlacedCompounds.get(level).takeRetired(npc.getUUID())) {
            event.setCanceled(true);
        }
    }

    private static @Nullable StealthNpc spawnNpc(ServerLevel level, Compound.Spawn spawn, Map<String, String> routeNames, Transform transform, BlockPos origin) {
        BlockPos pos = transform.apply(spawn.pos()).offset(origin);
        float yaw = transform.yaw(spawn.facing());
        StealthNpc npc = spawn(level, spawn.archetype(), pos, yaw, EntitySpawnReason.STRUCTURE);
        if (npc == null) {
            return null;
        }
        spawn.behaviour().ifPresent(npc::setBehaviourOverride);
        npc.setSchedule(Compound.moveSchedule(spawn.schedule(), name -> routeNames.getOrDefault(name, name), transform, origin));
        npc.setHome(pos, yaw);
        return npc;
    }

    /** Spawns a stealth NPC of an archetype standing on {@code pos}, facing {@code yaw}. */
    public static @Nullable StealthNpc spawn(ServerLevel level, Identifier archetype, BlockPos pos, float yaw, EntitySpawnReason reason) {
        StealthNpc npc = ESEntities.STEALTH_NPC.get().create(level, reason);
        if (npc == null) {
            return null;
        }
        npc.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setArchetypeId(archetype);
        EventHooks.finalizeMobSpawn(npc, level, level.getCurrentDifficultyAt(pos), reason, null);
        level.addFreshEntityWithPassengers(npc);
        return npc;
    }
}
