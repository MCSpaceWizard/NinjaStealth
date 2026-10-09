package com.mcspacewizard.emergentstealth.gametest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Imported structures: the builder's ({@code tools/structures/import_structures.py}) and the Cherry Grove modules
 * ({@code import_schematics.py}). Every one loads, the game's
 * data fixer turned no block into air (which would mean an unknown stand-in in {@code block_map.json}), the
 * spawners are gone, and every chest's loot table exists.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class StructureTests {
    private StructureTests() {}

    private static final Identifier ARENA = EmergentStealth.id("arena");
    private static final Identifier TEST = EmergentStealth.id("structures/imports_load");
    /** Imported folders and how many structures each should hold (2026-10-09). */
    private static final Map<String, Integer> EXPECTED = Map.of("structure/edo", 21, "structure/cherrygrove", 17);
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, TEST, () -> StructureTests::importsLoad);
    }

    @SubscribeEvent
    static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(EmergentStealth.id("structures"));
        event.registerTest(TEST, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, TEST),
                new TestData<>(environment, ARENA, 20, 0, true)));
    }

    static void importsLoad(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<String> problems = new ArrayList<>();
        Map<Identifier, Resource> files = new java.util.HashMap<>();
        EXPECTED.forEach((folder, expected) -> {
            Map<Identifier, Resource> found = server.getResourceManager().listResources(folder, path -> path.getPath().endsWith(".nbt"));
            if (found.size() != expected) {
                problems.add("expected " + expected + " structures in " + folder + ", found " + found.size());
            }
            files.putAll(found);
        });
        for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
            String path = file.getKey().getPath();
            Identifier id = file.getKey().withPath(path.substring("structure/".length(), path.length() - ".nbt".length()));
            CompoundTag raw;
            try (InputStream in = file.getValue().open()) {
                raw = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            } catch (Exception e) {
                problems.add(id + ": unreadable (" + e.getMessage() + ")");
                continue;
            }
            StructureTemplate template = server.getStructureManager().get(id).orElse(null);
            if (template == null) {
                problems.add(id + ": doesn't load");
                continue;
            }
            // Air before and after the data fixer: any extra air is a block the game doesn't know.
            ListTag palette = raw.getListOrEmpty("palette");
            ListTag blocks = raw.getListOrEmpty("blocks");
            Map<BlockPos, String> rawNames = new java.util.HashMap<>();
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag block = blocks.getCompoundOrEmpty(i);
                String name = palette.getCompoundOrEmpty(block.getIntOr("state", 0)).getStringOr("Name", "");
                ListTag pos = block.getListOrEmpty("pos");
                rawNames.put(new BlockPos(pos.getInt(0).orElse(0), pos.getInt(1).orElse(0), pos.getInt(2).orElse(0)), name);
                String table = block.getCompoundOrEmpty("nbt").getStringOr("LootTable", "");
                if (!table.isEmpty() && server.reloadableRegistries().getLootTable(
                        ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(table))) == LootTable.EMPTY) {
                    problems.add(id + ": missing loot table " + table);
                }
            }
            // Blocks that are air after loading but weren't in the file: names the game doesn't know.
            StructurePlaceSettings settings = new StructurePlaceSettings();
            java.util.TreeMap<String, Integer> lost = new java.util.TreeMap<>();
            for (var air : List.of(Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR)) {
                for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, air)) {
                    String name = rawNames.getOrDefault(info.pos(), "?");
                    if (!AIR.contains(name)) {
                        lost.merge(name, 1, Integer::sum);
                    }
                }
            }
            if (!lost.isEmpty()) {
                problems.add(id + ": blocks became air " + lost);
            }
            if (!template.filterBlocks(BlockPos.ZERO, settings, Blocks.SPAWNER).isEmpty()) {
                problems.add(id + ": still has spawners");
            }
        }
        helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
        helper.succeed();
    }
}
