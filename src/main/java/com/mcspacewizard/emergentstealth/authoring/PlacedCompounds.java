package com.mcspacewizard.emergentstealth.authoring;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Per-dimension record of every compound copy placed (design doc 32 §3): which compound, where and how it was
 * turned, and the routes, zones and NPCs it brought, so a copy can be reset or removed, and later (S9) have its
 * own alert level and command post.
 */
public final class PlacedCompounds extends SavedData {
    /**
     * @param npcs      the NPCs placed or respawned for this copy
     * @param npcSpawns for each NPC, the index of the compound spawn it stands for (empty in copies placed before
     *                  this was recorded: then the NPCs follow the spawns in order)
     * @param box       the blocks the copy covers (empty in older copies)
     * @param lights    the compound's light rule, moved into the world: which lights in {@code box} are relit
     */
    public record Copy(int id, Identifier compound, BlockPos origin, Rotation rotation, Mirror mirror,
                       List<String> routes, List<String> zones, List<UUID> npcs, List<Integer> npcSpawns,
                       Optional<BoundingBox> box, Compound.Lights lights) {
        public static final Codec<Copy> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Copy::id),
                Identifier.CODEC.fieldOf("compound").forGetter(Copy::compound),
                BlockPos.CODEC.fieldOf("origin").forGetter(Copy::origin),
                Rotation.CODEC.fieldOf("rotation").forGetter(Copy::rotation),
                Mirror.CODEC.fieldOf("mirror").forGetter(Copy::mirror),
                Codec.STRING.listOf().fieldOf("routes").forGetter(Copy::routes),
                Codec.STRING.listOf().fieldOf("zones").forGetter(Copy::zones),
                UUIDUtil.CODEC.listOf().fieldOf("npcs").forGetter(Copy::npcs),
                Codec.INT.listOf().optionalFieldOf("npc_spawns", List.of()).forGetter(Copy::npcSpawns),
                BoundingBox.CODEC.optionalFieldOf("box").forGetter(Copy::box),
                Compound.Lights.CODEC.optionalFieldOf("lights", Compound.Lights.ALL).forGetter(Copy::lights)
        ).apply(i, Copy::new));

        /** The name a compound's route or zone gets in this copy: {@code samurai_mini_fort#2/wall_walk}. */
        public static String scoped(Identifier compound, int id, String name) {
            return compound.getPath() + "#" + id + "/" + name;
        }

        public Transform transform() {
            return new Transform(mirror, rotation);
        }

        /** Whether the lamplighter may relight the light at {@code pos} as far as this copy goes. */
        public boolean relights(BlockPos pos) {
            return box.isEmpty() || !box.get().isInside(pos) || lights.relights(pos);
        }

        /** The spawn index of each NPC, worked out from the spawn counts when the copy didn't record them. */
        public List<Integer> spawnOfEachNpc(Compound compound) {
            if (npcSpawns.size() == npcs.size()) {
                return npcSpawns;
            }
            List<Integer> order = new ArrayList<>();
            for (int s = 0; s < compound.spawns().size(); s++) {
                for (int n = 0; n < compound.spawns().get(s).count(); n++) {
                    order.add(s);
                }
            }
            return order.subList(0, Math.min(order.size(), npcs.size()));
        }
    }

    public static final Codec<PlacedCompounds> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("last_id", 0).forGetter(placed -> placed.lastId),
            Copy.CODEC.listOf().fieldOf("copies").forGetter(placed -> placed.copies),
            UUIDUtil.CODEC.listOf().optionalFieldOf("retired", List.of()).forGetter(placed -> List.copyOf(placed.retired))
    ).apply(i, PlacedCompounds::new));

    public static final SavedDataType<PlacedCompounds> TYPE =
            new SavedDataType<>(EmergentStealth.id("placed_compounds"), PlacedCompounds::new, CODEC);

    private final List<Copy> copies = new ArrayList<>();
    /** The highest copy number ever given out here: numbers aren't reused, so a removed copy's names stay its own. */
    private int lastId;
    /**
     * NPCs a reset gave up on because they weren't loaded (their stand-ins were spawned): if one loads later it
     * is removed, so a reset never doubles a garrison.
     */
    private final Set<UUID> retired = new LinkedHashSet<>();

    public PlacedCompounds() {}

    private PlacedCompounds(int lastId, List<Copy> loaded, List<UUID> retired) {
        copies.addAll(loaded);
        this.retired.addAll(retired);
        this.lastId = Math.max(lastId, loaded.stream().mapToInt(Copy::id).max().orElse(0));
    }

    public static PlacedCompounds get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Copy> all() {
        return List.copyOf(copies);
    }

    public Optional<Copy> get(int id) {
        return copies.stream().filter(c -> c.id() == id).findFirst();
    }

    /** Takes the next copy number (copies are numbered per dimension, from 1, never reused). */
    public int nextId() {
        lastId++;
        setDirty();
        return lastId;
    }

    public void add(Copy copy) {
        copies.add(copy);
        setDirty();
    }

    /** Puts a changed copy in place of the one with its id. */
    public void replace(Copy copy) {
        copies.replaceAll(c -> c.id() == copy.id() ? copy : c);
        setDirty();
    }

    /** Whether the lamplighter may relight the light at {@code pos} (false inside a copy that leaves it dark). */
    public boolean mayRelight(BlockPos pos) {
        for (Copy copy : copies) {
            if (!copy.relights(pos)) {
                return false;
            }
        }
        return true;
    }

    public void retire(UUID npc) {
        if (retired.add(npc)) {
            setDirty();
        }
    }

    /** Whether {@code npc} was retired by a reset; forgets it either way (it is removed on the spot). */
    public boolean takeRetired(UUID npc) {
        boolean was = retired.remove(npc);
        if (was) {
            setDirty();
        }
        return was;
    }

    public boolean remove(int id) {
        boolean removed = copies.removeIf(c -> c.id() == id);
        if (removed) {
            setDirty();
        }
        return removed;
    }
}
