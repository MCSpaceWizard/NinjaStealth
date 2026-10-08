package com.mcspacewizard.emergentstealth.client.ui.screen;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.client.ui.UiScreen;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiDialogueBox;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The dialogue preview (design doc 31 §3.3): a short sample conversation with a gate guard, so the look of
 * the dialogue box can be reviewed before missions exist. Opened with {@code /es dev dialogue} (op) or the
 * client command {@code /esui dialogue}. The world stays visible behind it.
 */
public final class DialogueScreen extends UiScreen {
    /** A step in the sample script: lang keys for the text and choices, and where each choice leads. */
    private record Step(String text, List<String> choices, List<@Nullable String> next) {}

    private static final String PREFIX = "ui.emergentstealth.dialogue.preview.";
    private static final Map<String, Step> SCRIPT = Map.of(
            "gate", new Step("gate", List.of("rice", "passing", "lantern", "leave"), java.util.Arrays.asList("rice", "passing", "lantern", null)),
            "rice", new Step("rice", List.of(), List.of()),
            "passing", new Step("passing", List.of(), List.of()),
            "lantern", new Step("lantern", List.of("help", "wait"), java.util.Arrays.asList("help", "wait")),
            "help", new Step("help", List.of(), List.of()),
            "wait", new Step("wait", List.of(), List.of()));

    private String step = "gate";
    private @Nullable UiDialogueBox box;

    public DialogueScreen() {
        super(Component.translatable("ui.emergentstealth.dialogue.title"));
    }

    @Override
    protected UiNode build() {
        UiDialogueBox dialogue = new UiDialogueBox(this::choose, this::onClose);
        this.box = dialogue;
        dialogue.show(line(step), Sumi.now());
        int w = Math.min(440, width - 24);
        UiNode root = new UiNode() {
            @Override
            protected void layoutChildren() {
                int h = dialogue.preferredHeight(w);
                dialogue.layout(x + (width - w) / 2, y + height - h - 16, w, h);
            }
        };
        root.add(dialogue);
        return root;
    }

    @Override
    protected void init() {
        super.init();
        if (box != null) {
            setFocus(box);
        }
    }

    private UiDialogueBox.Line line(String id) {
        Step s = SCRIPT.get(id);
        List<Component> choices = s.choices().stream().map(c -> (Component) Component.translatable(PREFIX + id + ".choice." + c)).toList();
        return new UiDialogueBox.Line(Component.translatable(PREFIX + "speaker"), Component.translatable(PREFIX + s.text()), choices,
                DialogueScreen::guardPortrait);
    }

    private void choose(int index) {
        Step s = SCRIPT.get(step);
        String next = index < s.next().size() ? s.next().get(index) : null;
        if (next == null) {
            onClose();
            return;
        }
        step = next;
        if (box != null) {
            box.show(line(step), Sumi.now());
            relayout();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the world visible: only an ink wash rising from the bottom, behind the box.
        int backdrop = Sumi.color(SumiTheme.BACKDROP);
        float t = openProgress();
        Paint.gradientV(graphics, 0, height * 0.45F, width, height * 0.55F, Paint.fade(backdrop, 0.0F), Paint.fade(backdrop, 0.75F * t));
    }

    /**
     * A geometric portrait of an ashigaru: indigo shoulders, a face, and the flat conical jingasa hat with a
     * vermilion crest. It breathes a little.
     */
    static void guardPortrait(GuiGraphicsExtractor g, float x, float y, float size, long now) {
        SumiTheme theme = Sumi.theme();
        float s = size / 56.0F;
        float breathe = Sumi.reducedMotion() ? 0.0F : (float) Math.sin(now / 900.0) * 0.8F;
        float cx = x + size / 2.0F;
        Paint.glow(g, cx, y + size * 0.45F, size * 0.6F, Paint.fade(theme.color(SumiTheme.GOLD), 0.35F), Paint.fade(theme.color(SumiTheme.GOLD), 0.0F));
        // Shoulders and chest plate.
        float sy = y + 40 * s + breathe;
        Paint.polygon(g, new float[] {cx - 24 * s, y + size + 2, cx - 17 * s, sy, cx + 17 * s, sy, cx + 24 * s, y + size + 2}, 0xFF2D3A5C);
        Paint.polygon(g, new float[] {cx - 10 * s, y + size + 2, cx - 8 * s, sy + 3 * s, cx + 8 * s, sy + 3 * s, cx + 10 * s, y + size + 2}, 0xFF3B3F46);
        Paint.line(g, cx - 8 * s, sy + 8 * s, cx + 8 * s, sy + 8 * s, 1.5F * s, theme.color(SumiTheme.LACQUER));
        // Neck and face.
        Paint.rect(g, cx - 4 * s, y + 33 * s + breathe, 8 * s, 8 * s, 0xFFC08D64);
        Paint.circle(g, cx, y + 28 * s + breathe, 9.5F * s, 0xFFDCAE86);
        Paint.line(g, cx - 5 * s, y + 28 * s + breathe, cx - 2 * s, y + 28.5F * s + breathe, 1.2F * s, theme.color(SumiTheme.INK));
        Paint.line(g, cx + 2 * s, y + 28.5F * s + breathe, cx + 5 * s, y + 28 * s + breathe, 1.2F * s, theme.color(SumiTheme.INK));
        // Jingasa: a wide, flat cone.
        float hy = y + 21 * s + breathe;
        Paint.polygon(g, new float[] {cx - 22 * s, hy, cx, hy - 10 * s, cx + 22 * s, hy, cx, hy + 2.5F * s}, theme.color(SumiTheme.INK));
        Paint.line(g, cx - 21 * s, hy, cx + 21 * s, hy, 1.2F * s, Paint.fade(theme.color(SumiTheme.PAPER_EDGE), 0.6F));
        Paint.circle(g, cx, hy - 4 * s, 2.2F * s, theme.color(SumiTheme.LACQUER));
    }
}
