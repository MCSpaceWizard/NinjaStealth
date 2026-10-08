package com.mcspacewizard.emergentstealth.world.lock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mcspacewizard.emergentstealth.EmergentStealth;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Per-dimension world data: locked positions and the key that opens each (design doc 21 §3). Positions are
 * canonical (a door's lower half); use {@link Locks} rather than this class for queries.
 */
public final class LockData extends SavedData {
    /** One lock: the key name that opens it and how hard it is to pick (1 = easy, 5 = hardest). */
    public record Lock(String key, int difficulty) {
        public static final int MIN_DIFFICULTY = 1;
        public static final int MAX_DIFFICULTY = 5;

        public Lock {
            difficulty = Math.max(MIN_DIFFICULTY, Math.min(MAX_DIFFICULTY, difficulty));
        }
    }

    private record Entry(BlockPos pos, String key, int difficulty) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                Codec.STRING.fieldOf("key").forGetter(Entry::key),
                Codec.INT.optionalFieldOf("difficulty", 1).forGetter(Entry::difficulty)
        ).apply(instance, Entry::new));
    }

    public static final Codec<LockData> CODEC = Entry.CODEC.listOf().xmap(LockData::new, LockData::entries);

    public static final SavedDataType<LockData> TYPE = new SavedDataType<>(EmergentStealth.id("locks"), LockData::new, CODEC);

    private final Long2ObjectOpenHashMap<Lock> locks = new Long2ObjectOpenHashMap<>();

    public LockData() {}

    private LockData(List<Entry> loaded) {
        for (Entry entry : loaded) {
            locks.put(entry.pos().asLong(), new Lock(entry.key(), entry.difficulty()));
        }
    }

    private List<Entry> entries() {
        List<Entry> list = new ArrayList<>(locks.size());
        for (Map.Entry<Long, Lock> entry : locks.long2ObjectEntrySet()) {
            list.add(new Entry(BlockPos.of(entry.getKey()), entry.getValue().key(), entry.getValue().difficulty()));
        }
        return list;
    }

    public static LockData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isEmpty() {
        return locks.isEmpty();
    }

    public @Nullable Lock get(BlockPos canonical) {
        return locks.get(canonical.asLong());
    }

    public void put(BlockPos canonical, Lock lock) {
        locks.put(canonical.asLong(), lock);
        setDirty();
    }

    public boolean remove(BlockPos canonical) {
        boolean removed = locks.remove(canonical.asLong()) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** Every lock, for listing. */
    public List<Map.Entry<BlockPos, Lock>> all() {
        List<Map.Entry<BlockPos, Lock>> list = new ArrayList<>();
        for (Map.Entry<Long, Lock> entry : locks.long2ObjectEntrySet()) {
            list.add(Map.entry(BlockPos.of(entry.getKey()), entry.getValue()));
        }
        return list;
    }

    /** Key names in use, for suggestions and auto-naming. */
    public boolean keyInUse(String key) {
        for (Lock lock : locks.values()) {
            if (lock.key().equals(key)) {
                return true;
            }
        }
        return false;
    }
}
