package com.mcspacewizard.emergentstealth.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config specs. SERVER config is per-world and synced to clients (gameplay rules, tuning);
 * CLIENT config is per-player (HUD and presentation only, never gameplay).
 * Datapacks, not config, carry content definitions (archetypes, zones, outfits, ...).
 */
public final class ESConfig {
    private ESConfig() {}

    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    // --- Server ---
    public static final ModConfigSpec.BooleanValue DEBUG_TOOLS_REQUIRE_OP;

    // --- Client ---
    public static final ModConfigSpec.BooleanValue SHOW_LIGHT_GEM;

    static {
        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        server.push("debug");
        DEBUG_TOOLS_REQUIRE_OP = server
                .comment("Whether the AI debug overlay and debug commands require operator permissions.")
                .define("debugToolsRequireOp", true);
        server.pop();
        SERVER_SPEC = server.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        client.push("hud");
        SHOW_LIGHT_GEM = client
                .comment("Show the light gem (visibility meter) on the HUD.")
                .define("showLightGem", true);
        client.pop();
        CLIENT_SPEC = client.build();
    }
}
