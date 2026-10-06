package com.mcspacewizard.emergentstealth;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mcspacewizard.emergentstealth.command.ESCommands;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.gametest.ESGameTests;
import com.mcspacewizard.emergentstealth.registry.ESBlocks;
import com.mcspacewizard.emergentstealth.registry.ESCreativeTabs;
import com.mcspacewizard.emergentstealth.registry.ESDebugSubscriptions;
import com.mcspacewizard.emergentstealth.registry.ESEntities;
import com.mcspacewizard.emergentstealth.registry.ESItems;
import com.mcspacewizard.emergentstealth.registry.ESNetwork;
import com.mcspacewizard.emergentstealth.registry.ESRegistries;
import com.mcspacewizard.emergentstealth.registry.ESSounds;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Common entry point. Runs on both the physical client and the dedicated server.
 * Client-only setup lives in {@link EmergentStealthClient}.
 */
@Mod(EmergentStealth.MODID)
public final class EmergentStealth {
    public static final String MODID = "emergentstealth";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EmergentStealth(IEventBus modEventBus, ModContainer modContainer) {
        ESBlocks.BLOCKS.register(modEventBus);
        ESEntities.ENTITY_TYPES.register(modEventBus);
        ESItems.ITEMS.register(modEventBus);
        ESCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ESDebugSubscriptions.DEBUG_SUBSCRIPTIONS.register(modEventBus);
        ESSounds.SOUND_EVENTS.register(modEventBus);
        ESGameTests.TEST_FUNCTIONS.register(modEventBus);

        modEventBus.addListener(ESRegistries::onNewDataPackRegistries);
        modEventBus.addListener(ESEntities::onAttributeCreation);
        modEventBus.addListener(ESNetwork::onRegisterPayloads);
        modEventBus.addListener(ESGameTests::onRegisterGameTests);

        NeoForge.EVENT_BUS.addListener(ESCommands::onRegisterCommands);

        modContainer.registerConfig(ModConfig.Type.SERVER, ESConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ESConfig.CLIENT_SPEC);
    }

    /** Shorthand for an identifier in the mod's namespace. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
