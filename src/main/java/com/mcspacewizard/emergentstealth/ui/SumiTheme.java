package com.mcspacewizard.emergentstealth.ui;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Sumi design tokens (design doc 31 §0): colours, metrics, motion timings and texture settings. Loaded from
 * {@code assets/emergentstealth/ui/theme.json}; every token is optional and falls back to {@link #DEFAULT},
 * so a resource pack can override a single colour. Colours are {@code "#rrggbb"} or {@code "#aarrggbb"}.
 */
public record SumiTheme(Map<String, Integer> colors, Map<String, Float> metrics, Map<String, Float> motion,
                        Map<String, Float> texture) {
    // --- Colour tokens ---
    public static final String PAPER = "paper";
    public static final String PAPER_SHADE = "paper_shade";
    public static final String PAPER_EDGE = "paper_edge";
    public static final String INK = "ink";
    public static final String INK_SOFT = "ink_soft";
    public static final String INK_FAINT = "ink_faint";
    public static final String LACQUER = "lacquer";
    public static final String LACQUER_DARK = "lacquer_dark";
    public static final String GOLD = "gold";
    public static final String JADE = "jade";
    public static final String INDIGO = "indigo";
    public static final String TEXT = "text";
    public static final String TEXT_MUTED = "text_muted";
    public static final String TEXT_ON_INK = "text_on_ink";
    public static final String BACKDROP = "backdrop";
    public static final String SHINOBI = "shinobi";
    public static final String SHOGUNATE = "shogunate";

    // --- Metric tokens (GUI pixels) ---
    public static final String PADDING = "padding";
    public static final String GAP = "gap";
    public static final String CUT = "corner_cut";
    public static final String STROKE = "stroke";
    public static final String ROW_HEIGHT = "row_height";
    public static final String SIDEBAR_WIDTH = "sidebar_width";
    public static final String CONTROL_WIDTH = "control_width";
    public static final String NODE_RADIUS = "node_radius";
    public static final String NODE_SPACING_X = "node_spacing_x";
    public static final String NODE_SPACING_Y = "node_spacing_y";
    public static final String CARD_WIDTH = "card_width";
    public static final String BACKDROP_ALPHA = "backdrop_alpha";

    // --- Motion tokens (milliseconds) ---
    public static final String FAST = "fast";
    public static final String NORMAL = "normal";
    public static final String SLOW = "slow";
    public static final String STAGGER = "stagger";
    public static final String TOOLTIP_DELAY = "tooltip_delay";

    // --- Texture tokens ---
    public static final String GRAIN_SEED = "grain_seed";
    public static final String GRAIN_STRENGTH = "grain_strength";
    public static final String GRAIN_SCALE = "grain_scale";
    public static final String FIBER_DENSITY = "fiber_density";
    public static final String INK_SPECKLE = "ink_speckle";

    /** {@code "#rrggbb"} or {@code "#aarrggbb"} as an ARGB int (alpha defaults to opaque). */
    public static final Codec<Integer> COLOR = Codec.STRING.comapFlatMap(SumiTheme::parseColor, SumiTheme::formatColor);

    public static final Codec<SumiTheme> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, COLOR).optionalFieldOf("colors", Map.of()).forGetter(SumiTheme::colors),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("metrics", Map.of()).forGetter(SumiTheme::metrics),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("motion", Map.of()).forGetter(SumiTheme::motion),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("texture", Map.of()).forGetter(SumiTheme::texture)
    ).apply(i, SumiTheme::new));

    /** The built-in ink-and-paper theme (palette from docs/art/STYLE_GUIDE.md §2). */
    public static final SumiTheme DEFAULT = new SumiTheme(
            ordered(
                    PAPER, 0xFFE8DCC0, PAPER_SHADE, 0xFFD5C5A1, PAPER_EDGE, 0xFFB5A27A,
                    INK, 0xFF1B1A20, INK_SOFT, 0xFF3A3540, INK_FAINT, 0xFF8A7F6C,
                    LACQUER, 0xFFA8322D, LACQUER_DARK, 0xFF5C1C1A, GOLD, 0xFFC9A227, JADE, 0xFF4F8A6B, INDIGO, 0xFF2D3A5C,
                    TEXT, 0xFF1B1A20, TEXT_MUTED, 0xFF6B5F50, TEXT_ON_INK, 0xFFE8DCC0,
                    BACKDROP, 0xFF0D0C10, SHINOBI, 0xFF24232B, SHOGUNATE, 0xFFA8322D),
            orderedF(
                    PADDING, 8, GAP, 6, CUT, 5, STROKE, 2, ROW_HEIGHT, 22, SIDEBAR_WIDTH, 104, CONTROL_WIDTH, 96,
                    NODE_RADIUS, 13, NODE_SPACING_X, 58, NODE_SPACING_Y, 46, CARD_WIDTH, 168, BACKDROP_ALPHA, 0.72F),
            orderedF(FAST, 120, NORMAL, 240, SLOW, 420, STAGGER, 35, TOOLTIP_DELAY, 350),
            orderedF(GRAIN_SEED, 1337, GRAIN_STRENGTH, 0.11F, GRAIN_SCALE, 2, FIBER_DENSITY, 1, INK_SPECKLE, 0.22F));

    public int color(String token) {
        Integer value = colors.get(token);
        return value != null ? value : DEFAULT.colors.getOrDefault(token, 0xFFFF00FF);
    }

    public float metricF(String token) {
        Float value = metrics.get(token);
        return value != null ? value : DEFAULT.metrics.getOrDefault(token, 0.0F);
    }

    public int metric(String token) {
        return Math.round(metricF(token));
    }

    public int ms(String token) {
        Float value = motion.get(token);
        return Math.round(value != null ? value : DEFAULT.motion.getOrDefault(token, 0.0F));
    }

    public float textureF(String token) {
        Float value = texture.get(token);
        return value != null ? value : DEFAULT.texture.getOrDefault(token, 0.0F);
    }

    /** This theme with {@code overlay}'s tokens on top. */
    public SumiTheme with(SumiTheme overlay) {
        return new SumiTheme(merge(colors, overlay.colors), merge(metrics, overlay.metrics), merge(motion, overlay.motion),
                merge(texture, overlay.texture));
    }

    /** Parses theme JSON on top of {@code base}: missing tokens keep the base values. Errors are returned, not thrown. */
    public static DataResult<SumiTheme> parse(JsonElement json, SumiTheme base) {
        return CODEC.parse(JsonOps.INSTANCE, json).map(base::with);
    }

    static DataResult<Integer> parseColor(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        try {
            return switch (hex.length()) {
                case 3 -> DataResult.success(0xFF000000 | expand(hex));
                case 6 -> DataResult.success(0xFF000000 | Integer.parseUnsignedInt(hex, 16));
                case 8 -> DataResult.success(Integer.parseUnsignedInt(hex, 16));
                default -> DataResult.error(() -> "Bad colour (use #rrggbb or #aarrggbb): " + text);
            };
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Bad colour (use #rrggbb or #aarrggbb): " + text);
        }
    }

    private static int expand(String rgb) {
        int r = Character.digit(rgb.charAt(0), 16);
        int g = Character.digit(rgb.charAt(1), 16);
        int b = Character.digit(rgb.charAt(2), 16);
        if (r < 0 || g < 0 || b < 0) {
            throw new NumberFormatException(rgb);
        }
        return (r * 17) << 16 | (g * 17) << 8 | (b * 17);
    }

    static String formatColor(int argb) {
        return (argb >>> 24) == 0xFF ? String.format("#%06x", argb & 0xFFFFFF) : String.format("#%08x", argb);
    }

    private static <T> Map<String, T> merge(Map<String, T> base, Map<String, T> overlay) {
        Map<String, T> out = new LinkedHashMap<>(base);
        out.putAll(overlay);
        return Map.copyOf(out);
    }

    private static Map<String, Integer> ordered(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return Map.copyOf(map);
    }

    private static Map<String, Float> orderedF(Object... pairs) {
        Map<String, Float> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], ((Number) pairs[i + 1]).floatValue());
        }
        return Map.copyOf(map);
    }
}
