package com.mcspacewizard.emergentstealth.authoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Per-dimension record of every compound copy placed (design doc 32 §3): which compound, where and how it was
 * turned, and the routes, zones and NPCs it brought, so a copy can be removed, and later (S9) have its own
 * alert level and command post.
 */
public final class PlacedCompounds extends SavedData {
    public record Copy(int id, Identifier compound, BlockPos origin, Rotation rotation, Mirror mirror,
                       List<String> routes, List<String> zones, List<UUID> npcs) {
        public static final Codec<Copy> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Copy::id),
                Identifier.CODEC.fieldOf("compound").forGetter(Copy::compound),
                BlockPos.CODEC.fieldOf("origin").forGetter(Copy::origin),
                Rotation.CODEC.fieldOf("rotation").forGetter(Copy::rotation),
                Mirror.CODEC.fieldOf("mirror").forGetter(Copy::mirror),
                Codec.STRING.listOf().fieldOf("routes").forGetter(Copy::routes),
                Codec.STRING.listOf().fieldOf("zones").forGetter(Copy::zones),
                UUIDUtil.CODEC.listOf().fieldOf("npcs").forGetter(Copy::npcs)
        ).apply(i, Copy::new));

        /** The name a compound's route or zone gets in this copy: {@code samurai_mini_fort#2/wall_walk}. */
        public static String scoped(Identifier compound, int id, String name) {
            return compound.getPath() + "#" + id + "/" + name;
        }
    }

    public static final Codec<PlacedCompounds> CODEC = Copy.CODEC.listOf().xmap(PlacedCompounds::new, placed -> placed.copies);

    public static final SavedDataType<PlacedCompounds> TYPE =
            new SavedDataType<>(EmergentStealth.id("placed_compounds"), PlacedCompounds::new, CODEC);

    private final List<Copy> copies = new ArrayList<>();

    public PlacedCompounds() {}

    private PlacedCompounds(List<Copy> loaded) {
        copies.addAll(loaded);
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

    /** The next unused copy number (copies are numbered per dimension, from 1). */
    public int nextId() {
        return copies.stream().mapToInt(Copy::id).max().orElse(0) + 1;
    }

    public void add(Copy copy) {
        copies.add(copy);
        setDirty();
    }

    public boolean remove(int id) {
        boolean removed = copies.removeIf(c -> c.id() == id);
        if (removed) {
            setDirty();
        }
        return removed;
    }
}
