package com.mcspacewizard.emergentstealth.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.mcspacewizard.emergentstealth.EmergentStealth;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Turns a {@link ModConfigSpec} into the Sumi config screen's tabs and widgets (design doc 31 §3.1):
 * top-level sections become tabs, booleans toggles, ranged numbers sliders, enums cycles. Comments become
 * tooltips. Pure data, so a GameTest can check the mapping.
 */
public final class ConfigMapping {
    private ConfigMapping() {}

    public enum Kind {
        TOGGLE,
        INT_SLIDER,
        DOUBLE_SLIDER,
        CYCLE,
        TEXT,
        /** Lists and other values the screen can't edit yet; shown read-only. */
        UNSUPPORTED
    }

    /**
     * One editable value.
     *
     * @param name     the value's key (last path element)
     * @param langKey  translation key for its label
     * @param comment  the spec comment (tooltip), or null
     * @param min      slider minimum (sliders only)
     * @param max      slider maximum (sliders only)
     */
    public record Entry(String name, ModConfigSpec.ConfigValue<?> value, Kind kind, String langKey, @Nullable String comment,
                        double min, double max, ModConfigSpec.RestartType restart) {}

    /** One tab: a top-level section. Values outside any section go in {@value #GENERAL}. */
    public record Section(String name, String langKey, List<Entry> entries) {}

    public static final String GENERAL = "general";

    public static List<Section> sections(ModConfigSpec spec) {
        List<Section> sections = new ArrayList<>();
        List<Entry> general = new ArrayList<>();
        for (Map.Entry<String, Object> e : spec.getValues().valueMap().entrySet()) {
            if (e.getValue() instanceof ModConfigSpec.ConfigValue<?> value) {
                general.add(entry(value));
            } else if (e.getValue() instanceof UnmodifiableConfig inner) {
                List<Entry> entries = new ArrayList<>();
                collect(inner, entries);
                sections.add(new Section(e.getKey(), langKey(e.getKey()), List.copyOf(entries)));
            }
        }
        if (!general.isEmpty()) {
            sections.addFirst(new Section(GENERAL, langKey(GENERAL), List.copyOf(general)));
        }
        return List.copyOf(sections);
    }

    private static void collect(UnmodifiableConfig config, List<Entry> out) {
        for (Object value : config.valueMap().values()) {
            if (value instanceof ModConfigSpec.ConfigValue<?> configValue) {
                out.add(entry(configValue));
            } else if (value instanceof UnmodifiableConfig inner) {
                collect(inner, out); // deeper levels are flattened into the tab
            }
        }
    }

    public static Entry entry(ModConfigSpec.ConfigValue<?> value) {
        ModConfigSpec.ValueSpec spec = value.getSpec();
        List<String> path = value.getPath();
        String name = path.getLast();
        String key = spec.getTranslationKey() != null ? spec.getTranslationKey() : langKey(name);
        double[] range = range(spec);
        return new Entry(name, value, kindOf(value), key, spec.getComment(), range[0], range[1], spec.restartType());
    }

    public static Kind kindOf(ModConfigSpec.ConfigValue<?> value) {
        if (value instanceof ModConfigSpec.BooleanValue) {
            return Kind.TOGGLE;
        }
        if (value instanceof ModConfigSpec.EnumValue<?>) {
            return Kind.CYCLE;
        }
        boolean ranged = value.getSpec().getRange() != null;
        if (value instanceof ModConfigSpec.IntValue || value instanceof ModConfigSpec.LongValue) {
            return ranged ? Kind.INT_SLIDER : Kind.TEXT;
        }
        if (value instanceof ModConfigSpec.DoubleValue) {
            return ranged ? Kind.DOUBLE_SLIDER : Kind.TEXT;
        }
        Object def = value.getDefault();
        if (def instanceof String) {
            return Kind.TEXT;
        }
        return Kind.UNSUPPORTED;
    }

    @SuppressWarnings({"rawtypes"})
    private static double[] range(ModConfigSpec.ValueSpec spec) {
        ModConfigSpec.Range range = spec.getRange();
        if (range != null && range.getMin() instanceof Number min && range.getMax() instanceof Number max) {
            return new double[] {min.doubleValue(), max.doubleValue()};
        }
        return new double[] {0.0, 0.0};
    }

    public static String langKey(String name) {
        return EmergentStealth.MODID + ".configuration." + name;
    }

    /** Where {@code value} sits on a slider from {@code min} to {@code max}, 0..1. */
    public static double fraction(double value, double min, double max) {
        return max <= min ? 0.0 : Math.clamp((value - min) / (max - min), 0.0, 1.0);
    }

    /** The value at slider fraction {@code t}, rounded to a whole number or to a tidy step for the range. */
    public static double valueAt(double t, double min, double max, boolean integer) {
        double raw = min + Math.clamp(t, 0.0, 1.0) * (max - min);
        if (integer) {
            return Math.clamp(Math.round(raw), (long) Math.ceil(min), (long) Math.floor(max));
        }
        double step = step(min, max);
        return Math.clamp(Math.round(raw / step) * step, min, max);
    }

    /** A tidy step for a double slider: about 1/100 of the range, as a power of ten. */
    public static double step(double min, double max) {
        double range = Math.max(1.0E-6, max - min);
        return Math.pow(10.0, Math.floor(Math.log10(range)) - 2.0);
    }

    /** Formats a double slider value with as many decimals as its step needs. */
    public static String format(double value, double min, double max) {
        int decimals = (int) Math.max(0, -Math.floor(Math.log10(step(min, max))));
        return String.format(java.util.Locale.ROOT, "%." + decimals + "f", value);
    }
}
