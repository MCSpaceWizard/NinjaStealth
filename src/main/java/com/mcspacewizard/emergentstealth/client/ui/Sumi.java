package com.mcspacewizard.emergentstealth.client.ui;

import java.io.Reader;
import java.util.Optional;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Util;

/**
 * Sumi's global state (design doc 31): the active theme, the animation clock and the accessibility
 * switches. The theme is re-read from resources whenever a Sumi screen opens, so resource packs and the
 * high-contrast option apply without a restart.
 */
public final class Sumi {
    private Sumi() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Identifier THEME = EmergentStealth.id("ui/theme.json");
    public static final Identifier THEME_HIGH_CONTRAST = EmergentStealth.id("ui/theme_high_contrast.json");

    private static SumiTheme theme = SumiTheme.DEFAULT;

    public static SumiTheme theme() {
        return theme;
    }

    /** Reloads the theme from resources (plus the high-contrast layer when that option is on). */
    public static void reloadTheme() {
        SumiTheme loaded = read(THEME, SumiTheme.DEFAULT);
        if (highContrast()) {
            loaded = read(THEME_HIGH_CONTRAST, loaded);
        }
        theme = loaded;
    }

    private static SumiTheme read(Identifier id, SumiTheme base) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return base;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            return SumiTheme.parse(json, base).resultOrPartial(error -> LOGGER.warn("Sumi theme {}: {}", id, error)).orElse(base);
        } catch (Exception e) {
            LOGGER.warn("Couldn't read Sumi theme {}: {}", id, e.toString());
            return base;
        }
    }

    /** The animation clock: real milliseconds. */
    public static long now() {
        return Util.getMillis();
    }

    public static boolean reducedMotion() {
        return ESConfig.CLIENT_SPEC.isLoaded() && ESConfig.UI_REDUCED_MOTION.get();
    }

    public static boolean highContrast() {
        return ESConfig.CLIENT_SPEC.isLoaded() && ESConfig.UI_HIGH_CONTRAST.get();
    }

    /** A motion token's duration, or 0 with reduced motion. */
    public static int duration(String token) {
        return reducedMotion() ? 0 : theme.ms(token);
    }

    public static int color(String token) {
        return theme.color(token);
    }

    public static int metric(String token) {
        return theme.metric(token);
    }
}
