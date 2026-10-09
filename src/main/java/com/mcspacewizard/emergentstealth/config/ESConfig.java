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

    // --- Server: behaviour (doc 14) ---
    public static final ModConfigSpec.IntValue ATTACKERS_PER_TARGET;
    public static final ModConfigSpec.DoubleValue HEARING_GAIN;
    public static final ModConfigSpec.DoubleValue HEARING_AWARENESS_CAP;

    // --- Server: sound (doc 16) ---
    public static final ModConfigSpec.IntValue SOUND_NODES_PER_TICK;

    // --- Client: HUD ---
    public static final ModConfigSpec.BooleanValue SHOW_LIGHT_GEM;
    public static final ModConfigSpec.BooleanValue SHOW_DETECTION_INDICATORS;
    public static final ModConfigSpec.BooleanValue PLAY_ALERT_SOUNDS;
    public static final ModConfigSpec.BooleanValue SHOW_BARKS;

    // --- Client: visual lighting (doc 30) ---
    public static final ModConfigSpec.BooleanValue DYNAMIC_LIGHTS;
    public static final ModConfigSpec.IntValue DYNAMIC_LIGHT_RANGE;
    public static final ModConfigSpec.IntValue DYNAMIC_LIGHT_INTERVAL;
    public static final ModConfigSpec.BooleanValue ITEM_ENTITY_LIGHTS;
    public static final ModConfigSpec.BooleanValue SHADOWED_BLOCK_LIGHT;
    public static final ModConfigSpec.DoubleValue SHADOW_BOUNCE;
    public static final ModConfigSpec.BooleanValue DARK_IS_DARK;
    public static final ModConfigSpec.DoubleValue DARK_MAX_BRIGHTNESS;
    public static final ModConfigSpec.DoubleValue DARK_AMBIENT_SCALE;
    public static final ModConfigSpec.BooleanValue DARK_IS_DARK_WITH_SHADERS;
    public static final ModConfigSpec.BooleanValue SKY_SHADOWS;
    public static final ModConfigSpec.IntValue SKY_SHADOW_RADIUS;
    public static final ModConfigSpec.DoubleValue SKY_SHADOW_ANGLE_STEP;
    public static final ModConfigSpec.IntValue SKY_SECTIONS_PER_TICK;
    public static final ModConfigSpec.BooleanValue SKY_SHADOWS_WITH_SHADERS;
    public static final ModConfigSpec.BooleanValue HELD_LIGHT_WITH_SHADERS;

    // --- Client: Sumi UI (doc 31) ---
    public static final ModConfigSpec.BooleanValue UI_HIGH_CONTRAST;
    public static final ModConfigSpec.BooleanValue UI_REDUCED_MOTION;
    public static final ModConfigSpec.IntValue UI_TYPEWRITER_SPEED;
    // --- Client: animation (doc 17 §8) ---
    public static final ModConfigSpec.BooleanValue PROCEDURAL_CRAWL;
    public static final ModConfigSpec.BooleanValue PROCEDURAL_BODIES;
    public static final ModConfigSpec.BooleanValue BREATHING;
    public static final ModConfigSpec.BooleanValue LEAN;
    public static final ModConfigSpec.BooleanValue PLAYER_IDLE_ANIMATION;
    public static final ModConfigSpec.BooleanValue TAKEDOWN_CAMERA;
    public static final ModConfigSpec.DoubleValue ACTION_MOUSE_SCALE;
    public static final ModConfigSpec.IntValue ANIMATION_RANGE;

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

        server.push("behaviour");
        ATTACKERS_PER_TARGET = server.comment("How many guards may attack one target at once. The others surround it.")
                .defineInRange("attackersPerTarget", 2, 1, 16);
        HEARING_GAIN = server.comment("Awareness gained from hearing a player, per unit of heard intensity.")
                .defineInRange("hearingGain", 0.5, 0.0, 5.0);
        HEARING_AWARENESS_CAP = server.comment("Hearing alone never raises awareness above this: only sight detects.")
                .defineInRange("hearingAwarenessCap", 0.65, 0.0, 0.99);
        server.pop();

        server.push("sound");
        SOUND_NODES_PER_TICK = server.comment("Max sound flood-fill nodes per tick per dimension (design doc 16 §2). Work over budget waits for the next tick, which reads as a short sound delay.")
                .defineInRange("soundNodesPerTick", 8000, 500, 1000000);
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
        SHOW_BARKS = client.comment("Show what NPCs say (barks) as text above their heads.")
                .define("showBarks", true);
        client.pop();

        client.push("visual_lighting");
        DYNAMIC_LIGHTS = client.comment("Held light items (torches, lanterns, ...) and burning entities light up the world around them.")
                .define("dynamicLights", true);
        DYNAMIC_LIGHT_RANGE = client.comment("Only entities this close to the camera (blocks) give off dynamic light.")
                .defineInRange("dynamicLightRange", 48, 8, 256);
        DYNAMIC_LIGHT_INTERVAL = client.comment("Minimum ticks between terrain updates for a moving light. Higher = smoother FPS, choppier light.")
                .defineInRange("dynamicLightInterval", 2, 1, 20);
        ITEM_ENTITY_LIGHTS = client.comment("Dropped light items glow too. Visual only: guards don't count dropped items as lights.")
                .define("itemEntityLights", false);
        SHADOWED_BLOCK_LIGHT = client.comment("Block light casts real shadows (the same model guards use), instead of leaking around walls.")
                .define("shadowedBlockLight", true);
        SHADOW_BOUNCE = client.comment("How much of vanilla's leaking light is kept as soft bounce light in shadows. 0 = shadows exactly match gameplay.")
                .defineInRange("shadowBounce", 0.15, 0.0, 1.0);
        DARK_IS_DARK = client.comment("Darkness renders genuinely dark regardless of the brightness slider. Turn off for accessibility.")
                .define("darkIsDark", true);
        DARK_MAX_BRIGHTNESS = client.comment("With darkIsDark: the highest brightness-slider value that takes effect (0 = Moody, 1 = Bright).")
                .defineInRange("darkMaxBrightness", 0.0, 0.0, 1.0);
        DARK_AMBIENT_SCALE = client.comment("With darkIsDark: multiplier on the ambient light floor seen in total darkness.")
                .defineInRange("darkAmbientScale", 0.35, 0.0, 1.0);
        DARK_IS_DARK_WITH_SHADERS = client.comment("Also apply darkIsDark while an Iris shader pack is active (packs usually do their own tonemapping).")
                .define("darkIsDarkWithShaders", false);
        SKY_SHADOWS = client.comment("Sun and moon cast shadows on the terrain (the same model guards use): walls shade sideways in the morning and evening.")
                .define("skyShadows", true);
        SKY_SHADOW_RADIUS = client.comment("Sun/moon shadows are drawn within this many blocks of you; further out the sky light is vanilla.")
                .defineInRange("skyShadowRadius", 64, 16, 256);
        SKY_SHADOW_ANGLE_STEP = client.comment("Sun/moon shadows are re-drawn each time the sun moves this many degrees (1 degree is about 3.3 s of game time).")
                .defineInRange("skyShadowAngleStep", 2.0, 0.5, 15.0);
        SKY_SECTIONS_PER_TICK = client.comment("How many terrain sections are re-drawn per tick when the sun moves. Lower = smoother FPS, slower update.")
                .defineInRange("skySectionsPerTick", 6, 1, 64);
        SKY_SHADOWS_WITH_SHADERS = client.comment("Keep sun/moon shadows while an Iris shader pack is active (packs draw their own sun shadows).")
                .define("skyShadowsWithShaders", false);
        HELD_LIGHT_WITH_SHADERS = client.comment("Keep your own held-light glow while an Iris shader pack is active (most packs add their own held light).")
                .define("heldLightWithShaders", false);
        client.pop();

        client.push("ui");
        UI_HIGH_CONTRAST = client.comment("Mod screens use a high-contrast theme: brighter paper, black text, stronger lines.")
                .define("highContrast", false);
        UI_REDUCED_MOTION = client.comment("Mod screens skip animations: no slides, fades, glides or flings.")
                .define("reducedMotion", false);
        UI_TYPEWRITER_SPEED = client.comment("Dialogue text speed in characters per second. 0 shows each line at once.")
                .defineInRange("typewriterSpeed", 45, 0, 400);
        client.pop();

        client.push("animation");
        PROCEDURAL_CRAWL = client.comment("Crawling players use the procedural elbow crawl instead of vanilla's swimming arms.")
                .define("proceduralCrawl", true);
        PROCEDURAL_BODIES = client.comment("Knocked-out and dead NPCs lie down in slack poses; dragged and carried bodies hang and trail.")
                .define("proceduralBodies", true);
        BREATHING = client.comment("Subtle breathing on NPCs and players; slow, deep breathing on knocked-out NPCs.")
                .define("breathing", true);
        LEAN = client.comment("NPCs and players lean into turns and speed changes.")
                .define("lean", true);
        PLAYER_IDLE_ANIMATION = client.comment("Breathing and lean also animate players (keeps the player animation layer always on).")
                .define("playerIdleAnimation", true);
        TAKEDOWN_CAMERA = client.comment("In first person, the camera turns with takedown animations (angles only; it never moves).")
                .define("takedownCamera", true);
        ACTION_MOUSE_SCALE = client.comment("Mouse look speed during takedowns and other actions (1 = normal, 0 = locked).")
                .defineInRange("actionMouseScale", 0.25, 0.0, 1.0);
        ANIMATION_RANGE = client.comment("Procedural animation (crawl, breathing, dragged bodies) runs for entities within this many blocks.")
                .defineInRange("animationRange", 64, 16, 256);
        client.pop();
        CLIENT_SPEC = client.build();
    }
}
