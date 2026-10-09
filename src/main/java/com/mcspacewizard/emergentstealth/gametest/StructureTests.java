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
 * The builder's structures (imported by {@code tools/structures/import_structures.py}). Every one loads, the game's
 * data fixer turned no block into air (which would mean an unknown stand-in in {@code block_map.json}), the
 * spawners are gone, and every chest's loot table exists.
 */
@EventBusSubscriber(modid = EmergentStealth.MODID)
public final class StructureTests {
    private StructureTests() {}

    private static final Identifier ARENA = EmergentStealth.id("arena");
    private static final Identifier TEST = EmergentStealth.id("structures/edo_imports_load");
    /** How many structures the builder delivered (2026-10-09). */
    private static final int EXPECTED = 21;
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, TEST, () -> StructureTests::edoImportsLoad);
    }

    @SubscribeEvent
    static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(EmergentStealth.id("structures"));
        event.registerTest(TEST, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, TEST),
                new TestData<>(environment, ARENA, 20, 0, true)));
    }

    static void edoImportsLoad(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Map<Identifier, Resource> files = server.getResourceManager().listResources("structure/edo", path -> path.getPath().endsWith(".nbt"));
        helper.assertTrue(files.size() == EXPECTED, "Expected " + EXPECTED + " builder structures, found " + files.size());
        List<String> problems = new ArrayList<>();
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
            int rawAir = 0;
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag block = blocks.getCompoundOrEmpty(i);
                String name = palette.getCompoundOrEmpty(block.getIntOr("state", 0)).getStringOr("Name", "");
                if (AIR.contains(name)) {
                    rawAir++;
                }
                String table = block.getCompoundOrEmpty("nbt").getStringOr("LootTable", "");
                if (!table.isEmpty() && server.reloadableRegistries().getLootTable(
                        ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(table))) == LootTable.EMPTY) {
                    problems.add(id + ": missing loot table " + table);
                }
            }
            StructurePlaceSettings settings = new StructurePlaceSettings();
            int loadedAir = template.filterBlocks(BlockPos.ZERO, settings, Blocks.AIR).size()
                    + template.filterBlocks(BlockPos.ZERO, settings, Blocks.CAVE_AIR).size()
                    + template.filterBlocks(BlockPos.ZERO, settings, Blocks.VOID_AIR).size();
            if (loadedAir != rawAir) {
                problems.add(id + ": " + (loadedAir - rawAir) + " blocks became air (unknown block names)");
            }
            if (!template.filterBlocks(BlockPos.ZERO, settings, Blocks.SPAWNER).isEmpty()) {
                problems.add(id + ": still has spawners");
            }
        }
        helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
        helper.succeed();
    }
}
