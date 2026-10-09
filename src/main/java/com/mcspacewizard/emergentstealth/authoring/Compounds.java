package com.mcspacewizard.emergentstealth.authoring;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.FileUtil;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Where compounds come from (design doc 32 §3): datapacks ({@code data/<namespace>/emergentstealth/compound/}, reloaded
 * with {@code /reload}) and compounds saved in this world ({@code <world>/generated/<namespace>/compounds/}, beside the
 * structure templates the game saves there). A world's own compound wins over a datapack one with the same id.
 * {@code /es compound export} copies one to {@code <server dir>/compounds/} for adding to the mod.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class Compounds {
    private Compounds() {}

    public static final FileToIdConverter LISTER = FileToIdConverter.json("emergentstealth/compound");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Map<Identifier, Compound> datapack = Map.of();
    private static final Map<Identifier, Compound> WORLD = new HashMap<>();

    public static @Nullable Compound get(Identifier id) {
        Compound compound = WORLD.get(id);
        return compound != null ? compound : datapack.get(id);
    }

    /** Every known compound id, sorted. */
    public static List<Identifier> ids() {
        List<Identifier> ids = new ArrayList<>(datapack.keySet());
        WORLD.keySet().stream().filter(id -> !datapack.containsKey(id)).forEach(ids::add);
        ids.sort(null);
        return ids;
    }

    /** Saves a compound in this world (and makes it available at once). */
    public static Path saveToWorld(MinecraftServer server, Identifier id, Compound compound) throws IOException {
        Path file = worldFile(server, id);
        write(file, compound);
        WORLD.put(id, compound);
        return file;
    }

    /** Copies a compound into {@code compounds/<namespace>/<path>.json} under the server directory. */
    public static @Nullable Path export(MinecraftServer server, Identifier id) throws IOException {
        Compound compound = get(id);
        if (compound == null) {
            return null;
        }
        Path file = resolve(server.getServerDirectory().resolve("compounds"), id, null);
        write(file, compound);
        return file;
    }

    /** Arrays of plain numbers (positions) on one line; Gson's pretty printing gives each number its own. */
    private static final java.util.regex.Pattern NUMBER_ARRAY = java.util.regex.Pattern.compile("\\[\\s*(-?[0-9.]+(?:,\\s*-?[0-9.]+)*)\\s*]");

    public static String toJson(Compound compound) {
        JsonElement json = Compound.CODEC.encodeStart(JsonOps.INSTANCE, compound).getOrThrow();
        return NUMBER_ARRAY.matcher(GSON.toJson(json)).replaceAll(match -> "[" + match.group(1).replaceAll(",\\s*", ", ") + "]");
    }

    private static void write(Path file, Compound compound) throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file)) {
            writer.write(toJson(compound));
        }
    }

    private static Path worldRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.GENERATED_DIR).normalize();
    }

    private static Path worldFile(MinecraftServer server, Identifier id) throws IOException {
        return resolve(worldRoot(server), id, "compounds");
    }

    /**
     * {@code root/<namespace>[/dir]/<path>.json}, refusing ids that would leave {@code root} ({@code ..} segments are
     * valid in an id), the way the game guards structure template files.
     */
    private static Path resolve(Path root, Identifier id, @Nullable String dir) throws IOException {
        if (!FileUtil.isValidPathSegment(id.getNamespace())) {
            throw new IOException("Invalid compound name " + id);
        }
        List<String> segments = FileUtil.decomposePath(id.getPath() + ".json").getOrThrow(IOException::new);
        Path folder = dir == null ? root.resolve(id.getNamespace()) : root.resolve(id.getNamespace()).resolve(dir);
        return FileUtil.resolvePath(folder, segments);
    }

    /** Reads the world's saved compounds: {@code generated/<namespace>/compounds/<path>.json}. */
    private static void loadWorld(MinecraftServer server) {
        WORLD.clear();
        Path root = worldRoot(server);
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> namespaces = Files.list(root)) {
            for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                Path dir = namespace.resolve("compounds");
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(dir)) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                        String path = dir.relativize(file).toString().replace('\\', '/');
                        Identifier id = Identifier.tryBuild(namespace.getFileName().toString(), path.substring(0, path.length() - ".json".length()));
                        if (id != null) {
                            readWorldFile(id, file);
                        }
                    }
                }
            }
        } catch (IOException e) {
            EmergentStealth.LOGGER.warn("Couldn't list the world's compounds", e);
        }
    }

    private static void readWorldFile(Identifier id, Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            Compound.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .resultOrPartial(error -> EmergentStealth.LOGGER.warn("Compound {} ({}): {}", id, file, error))
                    .ifPresent(compound -> WORLD.put(id, compound));
        } catch (Exception e) {
            EmergentStealth.LOGGER.warn("Couldn't read compound {} ({})", id, file, e);
        }
    }

    @SubscribeEvent
    static void onServerStarting(ServerStartingEvent event) {
        loadWorld(event.getServer());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        WORLD.clear();
        StructurePlacement.clear();
        CompoundDrafts.clear();
    }

    @SubscribeEvent
    static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(EmergentStealth.id("compounds"), new Listener());
    }

    /** Loads the datapack compounds. */
    private static final class Listener extends SimpleJsonResourceReloadListener<Compound> {
        Listener() {
            super(Compound.CODEC, LISTER);
        }

        @Override
        protected void apply(Map<Identifier, Compound> loaded, ResourceManager manager, ProfilerFiller profiler) {
            datapack = Map.copyOf(loaded);
        }
    }
}
