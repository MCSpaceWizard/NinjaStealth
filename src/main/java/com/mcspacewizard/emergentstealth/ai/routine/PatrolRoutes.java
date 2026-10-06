package com.mcspacewizard.emergentstealth.ai.routine;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Per-dimension world data holding every patrol route by name. */
public final class PatrolRoutes extends SavedData {
    public static final Codec<PatrolRoutes> CODEC = Codec.unboundedMap(Codec.STRING, PatrolRoute.CODEC)
            .xmap(PatrolRoutes::new, routes -> routes.routes);

    public static final SavedDataType<PatrolRoutes> TYPE =
            new SavedDataType<>(EmergentStealth.id("patrol_routes"), PatrolRoutes::new, CODEC);

    private final Map<String, PatrolRoute> routes = new LinkedHashMap<>();

    public PatrolRoutes() {}

    private PatrolRoutes(Map<String, PatrolRoute> loaded) {
        routes.putAll(loaded);
    }

    public static PatrolRoutes get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<PatrolRoute> get(String name) {
        return Optional.ofNullable(routes.get(name));
    }

    public Collection<PatrolRoute> all() {
        return routes.values();
    }

    public void put(PatrolRoute route) {
        routes.put(route.name(), route);
        setDirty();
    }

    public boolean remove(String name) {
        boolean removed = routes.remove(name) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** A fresh unused name like {@code route_3}. */
    public String nextName() {
        int i = 1;
        while (routes.containsKey("route_" + i)) {
            i++;
        }
        return "route_" + i;
    }
}
