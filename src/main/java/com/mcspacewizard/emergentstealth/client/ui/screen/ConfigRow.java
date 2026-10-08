package com.mcspacewizard.emergentstealth.client.ui.screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiCycle;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiSlider;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiToggle;
import com.mcspacewizard.emergentstealth.ui.ConfigMapping;
import com.mcspacewizard.emergentstealth.ui.Easing;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.Tween;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * One config value in the Sumi config screen (design doc 31 §3.1): its label on the left and the widget
 * its kind maps to on the right. The spec comment, default and any restart or read-only note show as the
 * tooltip. Rows slide in one after another when their tab appears.
 */
final class ConfigRow extends UiNode {
    private final ConfigMapping.Entry entry;
    private final boolean editable;
    private final Runnable onChange;
    private final int index;
    private final LongSupplier shownAt;
    private final Component label;
    private final UiNode control;
    private final Tween hover = new Tween(0.0F);

    ConfigRow(ConfigMapping.Entry entry, boolean editable, Runnable onChange, int index, LongSupplier shownAt) {
        this.entry = entry;
        this.editable = editable;
        this.onChange = onChange;
        this.index = index;
        this.shownAt = shownAt;
        this.label = label(entry);
        this.control = add(buildControl());
        tooltip(this::tooltipLines);
    }

    static Component label(ConfigMapping.Entry entry) {
        if (I18n.exists(entry.langKey())) {
            return Component.translatable(entry.langKey());
        }
        return Component.literal(prettify(entry.name()));
    }

    /** {@code tier1MaxNpcs} to {@code Tier 1 max npcs}. */
    static String prettify(String name) {
        String spaced = name.replaceAll("([a-z])([A-Z0-9])", "$1 $2").replace('_', ' ');
        return spaced.isEmpty() ? name : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1).toLowerCase(Locale.ROOT);
    }

    private List<Component> tooltipLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(label);
        if (entry.comment() != null && !entry.comment().isBlank()) {
            lines.add(Component.literal(entry.comment().strip()));
        }
        lines.add(Component.translatable("ui.emergentstealth.config.default", formatValue(entry.value().getDefault()))
                .withStyle(ChatFormatting.ITALIC));
        if (entry.restart() != ModConfigSpec.RestartType.NONE) {
            lines.add(Component.translatable("ui.emergentstealth.config.restart"));
        }
        if (!editable) {
            lines.add(Component.translatable("ui.emergentstealth.config.read_only"));
        }
        return lines;
    }

    private String formatValue(Object value) {
        if (value instanceof Double d) {
            return ConfigMapping.format(d, entry.min(), entry.max());
        }
        if (value instanceof Boolean b) {
            return I18n.get(b ? "options.on" : "options.off");
        }
        return String.valueOf(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private UiNode buildControl() {
        ModConfigSpec.ConfigValue<Object> value = (ModConfigSpec.ConfigValue<Object>) entry.value();
        return switch (entry.kind()) {
            case TOGGLE -> new UiToggle(() -> (Boolean) value.get(), v -> set(value, v), editable);
            case INT_SLIDER -> new UiSlider(() -> ((Number) value.get()).doubleValue(), v -> {
                Object boxed = entry.value() instanceof ModConfigSpec.LongValue ? (Object) Math.round(v) : (Object) (int) Math.round(v);
                set(value, boxed);
            }, entry.min(), entry.max(), true, v -> String.valueOf(Math.round(v)), editable);
            case DOUBLE_SLIDER -> new UiSlider(() -> ((Number) value.get()).doubleValue(), v -> set(value, v), entry.min(), entry.max(), false,
                    v -> ConfigMapping.format(v, entry.min(), entry.max()), editable);
            case CYCLE -> {
                Enum<?> current = (Enum<?>) value.get();
                List<Object> constants = Arrays.asList((Object[]) current.getDeclaringClass().getEnumConstants());
                yield new UiCycle<Object>(constants, value::get, v -> set(value, v),
                        v -> Component.literal(prettify(((Enum) v).name().toLowerCase(Locale.ROOT))), editable);
            }
            case TEXT, UNSUPPORTED -> new ValueText(() -> String.valueOf(value.get()));
        };
    }

    private void set(ModConfigSpec.ConfigValue<Object> value, Object newValue) {
        if (!editable || newValue.equals(value.get())) {
            return;
        }
        value.set(newValue);
        onChange.run();
    }

    /** Resets this value to its default. */
    @SuppressWarnings("unchecked")
    void reset() {
        if (editable && entry.kind() != ConfigMapping.Kind.UNSUPPORTED) {
            set((ModConfigSpec.ConfigValue<Object>) entry.value(), entry.value().getDefault());
        }
    }

    @Override
    public int preferredHeight(int forWidth) {
        return Sumi.metric(SumiTheme.ROW_HEIGHT);
    }

    @Override
    protected void layoutChildren() {
        int cw = Math.min(Sumi.metric(SumiTheme.CONTROL_WIDTH) + 6, width / 2);
        int ch = control.preferredHeight(cw);
        control.layout(x + width - cw - 6, y + (height - ch) / 2, cw, ch);
    }

    private float entrance(long now) {
        long start = shownAt.getAsLong() + (long) index * Sumi.duration(SumiTheme.STAGGER);
        int duration = Sumi.duration(SumiTheme.NORMAL);
        if (duration <= 0) {
            return 1.0F;
        }
        return Easing.CUBIC_OUT.apply(Math.clamp((now - start) / (float) duration, 0.0F, 1.0F));
    }

    @Override
    public void update(UiContext ctx) {
        hover.animate(ctx.isHoveredWithin(this) ? 1.0F : 0.0F, ctx.now(), ctx.duration(SumiTheme.FAST), Easing.CUBIC_OUT);
        super.update(ctx);
    }

    @Override
    protected void draw(UiContext ctx) {
        float e = entrance(ctx.now());
        GuiGraphicsExtractor g = ctx.graphics();
        g.pose().pushMatrix();
        g.pose().translate((1.0F - e) * 10.0F, 0);
        previousAlpha = Paint.pushAlpha(e);
        SumiTheme theme = ctx.theme();
        float h = hover.value(ctx.now());
        if (h > 0.01F) {
            Paint.gradientH(g, x, y + 1, width * 0.8F, height - 2, Paint.fade(theme.color(SumiTheme.INK), 0.09F * h),
                    Paint.fade(theme.color(SumiTheme.INK), 0.0F));
            Paint.rect(g, x, y + 3, 2, height - 6, Paint.fade(theme.color(SumiTheme.LACQUER), h));
        }
        int textColor = editable ? theme.color(SumiTheme.TEXT) : theme.color(SumiTheme.TEXT_MUTED);
        int maxLabel = control.x() - x - 14;
        Component shown = label;
        if (ctx.font().width(shown) > maxLabel) {
            shown = Component.literal(ctx.font().plainSubstrByWidth(label.getString(), maxLabel - 6) + "...");
        }
        Paint.text(g, ctx.font(), shown, x + 8, y + (height - 8) / 2.0F, textColor);
        Paint.line(g, x + 6, y + height - 0.5F, x + width - 6, y + height - 0.5F, 1.0F, Paint.fade(theme.color(SumiTheme.INK_FAINT), 0.18F));
    }

    private float previousAlpha = 1.0F;

    @Override
    protected void drawOver(UiContext ctx) {
        Paint.popAlpha(previousAlpha);
        ctx.graphics().pose().popMatrix();
    }

    /** Read-only text for values the screen can't edit (lists, free text). */
    private static final class ValueText extends UiNode {
        private final java.util.function.Supplier<String> text;

        ValueText(java.util.function.Supplier<String> text) {
            this.text = text;
        }

        @Override
        public int preferredHeight(int forWidth) {
            return 12;
        }

        @Override
        protected void draw(UiContext ctx) {
            String value = ctx.font().plainSubstrByWidth(text.get(), width);
            Paint.text(ctx.graphics(), ctx.font(), Component.literal(value), x + width - ctx.font().width(value), y + 2,
                    ctx.theme().color(SumiTheme.TEXT_MUTED));
        }
    }
}
