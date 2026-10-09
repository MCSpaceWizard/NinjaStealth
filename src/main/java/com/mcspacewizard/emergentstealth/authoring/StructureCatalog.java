package com.mcspacewizard.emergentstealth.authoring;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The structures the viewer can browse (design doc 32 §1): every template the server knows, ours first, and a
 * compact copy of each for the client's ghost preview.
 */
public final class StructureCatalog {
    private StructureCatalog() {}

    /** Preview parts stay well under the 1 MiB custom payload limit. */
    public static final int PART_BYTES = 512 * 1024;
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void");
    private static final int CACHE_SIZE = 8;

    /** Recently sent previews (the browser asks again when you flick back and forth). */
    private static final Map<Identifier, byte[]> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Identifier, byte[]> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /** Every template id, ours first, then by namespace and path. Our GameTest arena is left out. */
    public static List<Identifier> ids(MinecraftServer server) {
        return server.getStructureManager().listTemplates()
                .filter(id -> !id.equals(EmergentStealth.id("arena")))
                .sorted(Comparator.comparing((Identifier id) -> !id.getNamespace().equals(EmergentStealth.MODID))
                        .thenComparing(Identifier::getNamespace).thenComparing(Identifier::getPath))
                .toList();
    }

    /**
     * The template as compressed NBT for the ghost preview: size, palette and solid blocks only (no air, no block
     * entity data, no entities). Null if the template doesn't exist.
     */
    public static synchronized byte @Nullable [] previewBytes(MinecraftServer server, Identifier id) {
        byte[] cached = CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        StructureTemplate template = server.getStructureManager().get(id).orElse(null);
        if (template == null) {
            return null;
        }
        CompoundTag full = template.save(new CompoundTag());
        CompoundTag preview = new CompoundTag();
        preview.put("size", full.getListOrEmpty("size"));
        ListTag palette = full.contains("palette") ? full.getListOrEmpty("palette")
                : full.getListOrEmpty("palettes").getListOrEmpty(0);
        preview.put("palette", palette);
        ListTag blocks = new ListTag();
        ListTag source = full.getListOrEmpty("blocks");
        for (int i = 0; i < source.size(); i++) {
            CompoundTag block = source.getCompoundOrEmpty(i);
            int state = block.getIntOr("state", 0);
            if (AIR.contains(palette.getCompoundOrEmpty(state).getStringOr("Name", "minecraft:air"))) {
                continue;
            }
            CompoundTag slim = new CompoundTag();
            slim.put("pos", block.getListOrEmpty("pos"));
            slim.putInt("state", state);
            blocks.add(slim);
        }
        preview.put("blocks", blocks);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(preview, out);
            byte[] bytes = out.toByteArray();
            CACHE.put(id, bytes);
            return bytes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Splits bytes into parts of at most {@code partBytes} (at least one part, even when empty). */
    public static List<byte[]> split(byte[] bytes, int partBytes) {
        List<byte[]> parts = new ArrayList<>();
        for (int start = 0; start < bytes.length || parts.isEmpty(); start += partBytes) {
            parts.add(Arrays.copyOfRange(bytes, start, Math.min(bytes.length, start + partBytes)));
        }
        return parts;
    }

    /** Joins parts back together, or null if any is missing. */
    public static byte @Nullable [] join(byte[][] parts) {
        int length = 0;
        for (byte[] part : parts) {
            if (part == null) {
                return null;
            }
            length += part.length;
        }
        byte[] out = new byte[length];
        int at = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }

    /** Forgets cached previews (after a datapack reload). */
    public static synchronized void clearCache() {
        CACHE.clear();
    }
}
