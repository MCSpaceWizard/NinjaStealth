package com.mcspacewizard.emergentstealth.authoring;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mojang.serialization.Codec;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Per-dimension world data holding every zone in effect by name (design doc 32 §2), beside the patrol routes. */
public final class Zones extends SavedData {
    public static final Codec<Zones> CODEC = Codec.unboundedMap(Codec.STRING, Zone.CODEC).xmap(Zones::new, zones -> zones.zones);

    public static final SavedDataType<Zones> TYPE = new SavedDataType<>(EmergentStealth.id("zones"), Zones::new, CODEC);

    private final Map<String, Zone> zones = new LinkedHashMap<>();

    public Zones() {}

    private Zones(Map<String, Zone> loaded) {
        zones.putAll(loaded);
    }

    public static Zones get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Zone> get(String name) {
        return Optional.ofNullable(zones.get(name));
    }

    public Collection<Zone> all() {
        return zones.values();
    }

    public void put(Zone zone) {
        zones.put(zone.name(), zone);
        setDirty();
    }

    public boolean remove(String name) {
        boolean removed = zones.remove(name) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** The first free {@code zone_<n>} name. */
    public String nextName() {
        int i = 1;
        while (zones.containsKey("zone_" + i)) {
            i++;
        }
        return "zone_" + i;
    }
}
