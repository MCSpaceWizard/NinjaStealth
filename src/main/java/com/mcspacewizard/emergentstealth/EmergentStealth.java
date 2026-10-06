package com.mcspacewizard.emergentstealth;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.registry.ESCreativeTabs;
import com.mcspacewizard.emergentstealth.registry.ESItems;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Common entry point. Runs on both the physical client and the dedicated server.
 * Client-only setup lives in {@link EmergentStealthClient}.
 */
@Mod(EmergentStealth.MODID)
public final class EmergentStealth {
    public static final String MODID = "emergentstealth";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EmergentStealth(IEventBus modEventBus, ModContainer modContainer) {
        ESItems.ITEMS.register(modEventBus);
        ESCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, ESConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ESConfig.CLIENT_SPEC);
    }

    /** Shorthand for an identifier in the mod's namespace. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
