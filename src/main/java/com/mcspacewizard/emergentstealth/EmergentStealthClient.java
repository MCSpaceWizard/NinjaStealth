package com.mcspacewizard.emergentstealth;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point. Never class-loaded on a dedicated server. */
@Mod(value = EmergentStealth.MODID, dist = Dist.CLIENT)
public final class EmergentStealthClient {
    public EmergentStealthClient(ModContainer container) {
        // Mods screen > Emergent Stealth > Config
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
