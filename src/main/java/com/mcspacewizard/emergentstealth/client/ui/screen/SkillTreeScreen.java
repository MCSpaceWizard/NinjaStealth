package com.mcspacewizard.emergentstealth.client.ui.screen;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.client.ui.UiScreen;
import com.mcspacewizard.emergentstealth.client.ui.widget.SumiSounds;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiDivider;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiFlex;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiLabel;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiPager;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiPanel;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiProgressBar;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiTabBar;
import com.mcspacewizard.emergentstealth.progression.PlayerProgression;
import com.mcspacewizard.emergentstealth.progression.SkillPath;
import com.mcspacewizard.emergentstealth.progression.Skills;
import com.mcspacewizard.emergentstealth.ui.Easing;
import com.mcspacewizard.emergentstealth.ui.SkillTreeModel;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.Tween;
import com.mcspacewizard.emergentstealth.ui.UiLayout;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * The skill tree (design docs 26 and 31 §3.2), opened with K. Two paths, Shinobi (ink) and Shogunate
 * (vermilion), as swipeable pages of pan-and-zoom canvases. The header shows the path tabs, unspent points
 * as a seal, and the Insight bar towards the next point. Unlocking goes through the server.
 */
public final class SkillTreeScreen extends UiScreen {
    private static int lastPath;
    private final @Nullable KeyMapping closeKey;
    private @Nullable UiPager pager;
    private final List<SkillCanvas> canvases = new ArrayList<>();
    private SkillTreeModel.@Nullable Node cardFor;
    private final Tween cardFade = new Tween(0.0F);

    public SkillTreeScreen(@Nullable KeyMapping closeKey) {
        super(Component.translatable("ui.emergentstealth.skills.title"));
        this.closeKey = closeKey;
    }

    private SkillPath currentPath() {
        return pager == null ? SkillPath.values()[lastPath] : SkillPath.values()[Math.clamp(pager.index(), 0, SkillPath.values().length - 1)];
    }

    private PlayerProgression progression() {
        return Skills.progression(minecraft.player);
    }

    @Override
    protected UiNode build() {
        SumiTheme theme = Sumi.theme();
        int pad = theme.metric(SumiTheme.PADDING);
        canvases.clear();
        UiPanel sheet = new UiPanel();
        sheet.padding(UiLayout.Insets.of(pad + 2, pad)).gap(4);

        UiPager pages = new UiPager(SkillPath.values().length, lastPath);
        this.pager = pages;
        pages.onChange(i -> {
            lastPath = i;
            SumiSounds.page();
        });

        // Header: title, path tabs, points and Insight.
        UiFlex header = sheet.add(UiFlex.row().gap(10).center());
        header.size(-1, 28);
        header.add(new UiLabel(Component.translatable("ui.emergentstealth.skills.title")).scale(1.5F)).size(-1, 14);
        UiTabBar tabs = new UiTabBar(UiTabBar.Orientation.HORIZONTAL, pages::position, pages::select);
        tabs.tab(Component.translatable("ui.emergentstealth.skills.tab.shinobi"), 0, theme.color(SumiTheme.SHINOBI));
        tabs.tab(Component.translatable("ui.emergentstealth.skills.tab.shogunate"), 1, theme.color(SumiTheme.SHOGUNATE));
        header.add(tabs).flex(1);
        header.add(new PointsSeal()).size(22, 22);
        UiFlex insight = header.add(UiFlex.column().gap(2));
        insight.size(Math.min(110, width / 5), 22);
        insight.add(new UiLabel(() -> Component.translatable("ui.emergentstealth.skills.insight", progression().insight(currentPath()),
                PlayerProgression.INSIGHT_PER_POINT)).color(SumiTheme.TEXT_MUTED)).size(-1, 10);
        insight.add(new UiProgressBar(() -> progression().insight(currentPath()) / (double) PlayerProgression.INSIGHT_PER_POINT,
                () -> canvases.isEmpty() ? theme.color(SumiTheme.INK) : canvases.get(Math.clamp(pages.index(), 0, canvases.size() - 1)).accent()))
                .size(-1, 6);
        sheet.add(new UiDivider(909L).thickness(3.0F)).size(-1, 6);

        for (SkillPath path : SkillPath.values()) {
            SkillCanvas canvas = new SkillCanvas(path, this);
            canvases.add(canvas);
            pages.add(canvas);
        }
        sheet.add(pages).flex(1);
        sheet.add(new UiLabel(Component.translatable("ui.emergentstealth.skills.hint")).color(SumiTheme.TEXT_MUTED)
                .align(UiLabel.Align.CENTER)).size(-1, 10);

        int margin = Math.max(6, Math.min(24, width / 30));
        UiNode root = new UiNode() {
            @Override
            protected void layoutChildren() {
                sheet.layout(x + margin, y + margin, width - margin * 2, height - margin * 2);
            }
        };
        root.add(sheet);
        return root;
    }

    @Override
    protected void drawOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (pager == null || canvases.isEmpty()) {
            return;
        }
        SkillCanvas canvas = canvases.get(Math.clamp(pager.index(), 0, canvases.size() - 1));
        SkillTreeModel.Node node = pager.isDragging() ? null : canvas.cardNode(ctx);
        long now = Sumi.now();
        if (node == null || cardFor == null || !node.id().equals(cardFor.id())) {
            cardFade.snap(0.0F);
        }
        cardFor = node;
        if (node == null) {
            return;
        }
        cardFade.animate(1.0F, now, Sumi.duration(SumiTheme.FAST), Easing.CUBIC_OUT);
        float[] anchor = canvas.cardAnchor(node);
        float[] box = canvas.screenBox();
        float ox = box[0] - canvas.x();
        float oy = box[1] - canvas.y();
        float fade = cardFade.value(now);
        float previous = Paint.pushAlpha(fade);
        int cardWidth = Sumi.metric(SumiTheme.CARD_WIDTH);
        int ax = (int) (anchor[0] + ox);
        if (ax + cardWidth + 16 > width) {
            ax = (int) (anchor[0] + ox - canvas.nodeRadius() * 2 - cardWidth - 28);
        }
        drawCard(graphics, canvas.cardLines(node), ax, (int) (anchor[1] + oy), cardWidth, (1.0F - fade) * 4.0F);
        Paint.popAlpha(previous);
    }

    @Override
    protected boolean screenKey(KeyEvent event) {
        if (pager == null) {
            return false;
        }
        int key = event.key();
        if (key == GLFW.GLFW_KEY_Q || key == GLFW.GLFW_KEY_PAGE_UP) {
            pager.select(pager.index() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_E || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            pager.select(pager.index() + 1);
            return true;
        }
        if (closeKey != null && closeKey.matches(event)) {
            onClose();
            return true;
        }
        if (minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    protected void init() {
        super.init();
        if (!canvases.isEmpty() && pager != null) {
            setFocus(canvases.get(pager.index()));
        }
    }

    /** Unspent points on the current path, as a vermilion seal that pulses when there's something to spend. */
    private final class PointsSeal extends UiNode {
        private final Tween bump = new Tween(0.0F);
        private int lastPoints = -1;

        PointsSeal() {
            tooltip(() -> List.of(Component.translatable("ui.emergentstealth.skills.points", progression().points(currentPath())),
                    Component.translatable("ui.emergentstealth.skills.points_hint")));
        }

        @Override
        public void update(UiContext ctx) {
            int points = progression().points(currentPath());
            if (lastPoints >= 0 && points != lastPoints) {
                bump.restart(1.0F, 0.0F, ctx.now(), ctx.duration(SumiTheme.SLOW), Easing.QUAD_OUT);
            }
            lastPoints = points;
            super.update(ctx);
        }

        @Override
        protected void draw(UiContext ctx) {
            SumiTheme theme = ctx.theme();
            int points = progression().points(currentPath());
            float cx = x + width / 2.0F;
            float cy = y + height / 2.0F;
            float r = Math.min(width, height) / 2.0F * (1.0F + 0.25F * bump.value(ctx.now()));
            int color = points > 0 ? theme.color(SumiTheme.LACQUER) : theme.color(SumiTheme.INK_FAINT);
            ctx.graphics().pose().pushMatrix();
            ctx.graphics().pose().translate(cx, cy);
            ctx.graphics().pose().rotate((float) Math.toRadians(-6));
            Paint.inkPolygon(ctx.graphics(), Paint.cutRectPoints(-r, -r, r * 2, r * 2, 3.0F), color);
            Paint.outline(ctx.graphics(), Paint.cutRectPoints(-r + 2, -r + 2, r * 2 - 4, r * 2 - 4, 2.0F), 1.0F,
                    Paint.fade(theme.color(SumiTheme.PAPER), 0.55F));
            ctx.graphics().pose().popMatrix();
            Paint.textCentered(ctx.graphics(), ctx.font(), Component.literal(String.valueOf(points)), cx + 0.5F, cy - 3.5F,
                    theme.color(SumiTheme.TEXT_ON_INK));
        }
    }
}
