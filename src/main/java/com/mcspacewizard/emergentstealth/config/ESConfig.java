package com.mcspacewizard.emergentstealth.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config specs. SERVER config is per-world and synced to clients (gameplay rules, tuning); CLIENT config
 * is per-player (HUD and presentation only, never gameplay). Datapacks, not config, carry content
 * definitions (archetypes, outfits, perception profiles, ...).
 */
public final class ESConfig {
    private ESConfig() {}

    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    // --- Server: perception ---
    public static final ModConfigSpec.IntValue TIER1_MAX_NPCS;
    public static final ModConfigSpec.DoubleValue TIER1_RANGE;
    public static final ModConfigSpec.DoubleValue TIER2_RANGE;
    public static final ModConfigSpec.IntValue TIER1_INTERVAL;
    public static final ModConfigSpec.IntValue TIER2_INTERVAL;
    public static final ModConfigSpec.IntValue RAYS_PER_TICK_BUDGET;
    public static final ModConfigSpec.BooleanValue IGNORE_CREATIVE;
    public static final ModConfigSpec.DoubleValue GLOBAL_GAIN_MULTIPLIER;

    // --- Server: detection ---
    public static final ModConfigSpec.DoubleValue NOTICED_THRESHOLD;
    public static final ModConfigSpec.DoubleValue SUSPICIOUS_THRESHOLD;
    public static final ModConfigSpec.IntValue SEARCH_SECONDS;

    // --- Client: HUD ---
    public static final ModConfigSpec.BooleanValue SHOW_LIGHT_GEM;
    public static final ModConfigSpec.BooleanValue SHOW_DETECTION_INDICATORS;
    public static final ModConfigSpec.BooleanValue PLAY_ALERT_SOUNDS;

    static {
        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        server.push("perception");
        TIER1_MAX_NPCS = server.comment("Max NPCs running full-fidelity perception at once (closest to players).")
                .defineInRange("tier1MaxNpcs", 20, 1, 200);
        TIER1_RANGE = server.comment("Max distance (blocks) from a player for full-fidelity perception.")
                .defineInRange("tier1Range", 48.0, 8.0, 256.0);
        TIER2_RANGE = server.comment("Max distance (blocks) from a player for reduced perception. Beyond this NPCs don't look at all.")
                .defineInRange("tier2Range", 96.0, 16.0, 512.0);
        TIER1_INTERVAL = server.comment("Ticks between perception updates for tier-1 NPCs.")
                .defineInRange("tier1Interval", 2, 1, 20);
        TIER2_INTERVAL = server.comment("Ticks between perception updates for tier-2 NPCs.")
                .defineInRange("tier2Interval", 10, 1, 100);
        RAYS_PER_TICK_BUDGET = server.comment("Max sight rays per tick per dimension. Work over budget waits for the next tick.")
                .defineInRange("raysPerTickBudget", 600, 10, 20000);
        IGNORE_CREATIVE = server.comment("NPCs can't see players in creative or spectator mode.")
                .define("ignoreCreative", true);
        GLOBAL_GAIN_MULTIPLIER = server.comment("Multiplies how fast every NPC's awareness fills.")
                .defineInRange("globalGainMultiplier", 1.0, 0.05, 10.0);
        server.pop();

        server.push("detection");
        NOTICED_THRESHOLD = server.comment("Awareness at which an NPC notices something (\"huh?\").")
                .defineInRange("noticedThreshold", 0.2, 0.01, 0.99);
        SUSPICIOUS_THRESHOLD = server.comment("Awareness at which an NPC becomes suspicious and investigates.")
                .defineInRange("suspiciousThreshold", 0.5, 0.01, 0.99);
        SEARCH_SECONDS = server.comment("How long alerted NPCs search after losing their target.")
                .defineInRange("searchSeconds", 25, 1, 600);
        server.pop();
        SERVER_SPEC = server.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        client.push("hud");
        SHOW_LIGHT_GEM = client.comment("Show the light gem (visibility meter) on the HUD. Takes effect from Stage 3.")
                .define("showLightGem", true);
        SHOW_DETECTION_INDICATORS = client.comment("Show detection indicators above NPCs that are noticing you.")
                .define("showDetectionIndicators", true);
        PLAY_ALERT_SOUNDS = client.comment("Play a stinger sound when an NPC becomes suspicious of you or detects you.")
                .define("playAlertSounds", true);
        client.pop();
        CLIENT_SPEC = client.build();
    }
}
