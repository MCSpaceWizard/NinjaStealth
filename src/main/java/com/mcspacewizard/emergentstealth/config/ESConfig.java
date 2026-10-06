package com.mcspacewizard.emergentstealth.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config specs. CLIENT config is per-player (HUD and presentation only, never gameplay).
 * A SERVER spec (per-world gameplay rules and tuning, synced to clients) is added when the first
 * tunable gameplay system lands. Datapacks, not config, carry content (archetypes, outfits, zones, ...).
 */
public final class ESConfig {
    private ESConfig() {}

    public static final ModConfigSpec CLIENT_SPEC;

    public static final ModConfigSpec.BooleanValue SHOW_LIGHT_GEM;

    static {
        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        client.push("hud");
        SHOW_LIGHT_GEM = client
                .comment("Show the light gem (visibility meter) on the HUD. Takes effect from Stage 3.")
                .define("showLightGem", true);
        client.pop();
        CLIENT_SPEC = client.build();
    }
}
