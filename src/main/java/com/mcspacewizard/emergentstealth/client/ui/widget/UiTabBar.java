package com.mcspacewizard.emergentstealth.client.ui.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;

import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Tabs (design doc 31 §2 Widgets): a vertical sidebar or a horizontal strip. The ink marker follows the
 * pager's continuous position, so it glides while you swipe. Headings group tabs in the sidebar.
 */
public class UiTabBar extends UiNode {
    public enum Orientation { VERTICAL, HORIZONTAL }

    private record Item(Component label, int page, boolean heading, int accent) {}

    private static final int HEADING_HEIGHT = 16;
    private final Orientation orientation;
    private final List<Item> items = new ArrayList<>();
    private final DoubleSupplier position;
    private final IntConsumer select;
    private int rowHeight = 20;
    private int[] starts = new int[0];
    private int[] sizes = new int[0];

    public UiTabBar(Orientation orientation, DoubleSupplier position, IntConsumer select) {
        this.orientation = orientation;
        this.position = position;
        this.select = select;
    }

    public UiTabBar heading(Component label) {
        items.add(new Item(label, -1, true, 0));
        return this;
    }

    public UiTabBar tab(Component label, int page) {
        return tab(label, page, 0);
    }

    /** A tab with its own accent colour (0 = the theme's ink). */
    public UiTabBar tab(Component label, int page, int accent) {
        items.add(new Item(label, page, false, accent));
        return this;
    }

    private int pageCount() {
        int max = -1;
        for (Item item : items) {
            max = Math.max(max, item.page);
        }
        return max + 1;
    }

    @Override
    public int preferredWidth() {
        if (prefWidth >= 0) {
            return prefWidth;
        }
        int w = 0;
        for (Item item : items) {
            int iw = font().width(item.label) + 16;
            w = orientation == Orientation.VERTICAL ? Math.max(w, iw + 8) : w + iw;
        }
        return w;
    }

    @Override
    public int preferredHeight(int forWidth) {
        if (prefHeight >= 0) {
            return prefHeight;
        }
        if (orientation == Orientation.HORIZONTAL) {
            return 20;
        }
        int h = 0;
        for (Item item : items) {
            h += item.heading ? HEADING_HEIGHT : rowHeight();
        }
        return h;
    }

    private int rowHeight() {
        return rowHeight;
    }

    /** Height of each tab in a vertical bar (default 20). */
    public UiTabBar rowHeight(int rowHeight) {
        this.rowHeight = rowHeight;
        return this;
    }

    private static net.minecraft.client.gui.Font font() {
        return net.minecraft.client.Minecraft.getInstance().font;
    }

    @Override
    protected void layoutChildren() {
        starts = new int[items.size()];
        sizes = new int[items.size()];
        int at = orientation == Orientation.VERTICAL ? y : x;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int size = orientation == Orientation.VERTICAL ? (item.heading ? HEADING_HEIGHT : rowHeight()) : font().width(item.label) + 16;
            starts[i] = at;
            sizes[i] = size;
            at += size;
        }
        if (orientation == Orientation.HORIZONTAL && at - x < width) {
            // Spread tabs over the full width.
            int extra = (width - (at - x)) / Math.max(1, (int) items.stream().filter(it -> !it.heading).count());
            at = x;
            for (int i = 0; i < items.size(); i++) {
                starts[i] = at;
                if (!items.get(i).heading) {
                    sizes[i] += extra;
                }
                at += sizes[i];
            }
        }
    }

    private int indexOfPage(int page) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).page == page && !items.get(i).heading) {
                return i;
            }
        }
        return -1;
    }

    /** Marker start and size interpolated between the tabs around {@code pos}. */
    private float[] marker(double pos) {
        int lo = (int) Math.floor(pos);
        int hi = Math.min(pageCount() - 1, lo + 1);
        lo = Math.max(0, lo);
        float t = (float) Math.clamp(pos - lo, 0.0, 1.0);
        int a = indexOfPage(lo);
        int b = indexOfPage(hi);
        if (a < 0 || b < 0) {
            return new float[] {0, 0};
        }
        float start = starts[a] + (starts[b] - starts[a]) * t;
        float size = sizes[a] + (sizes[b] - sizes[a]) * t;
        return new float[] {start, size};
    }

    @Override
    protected void draw(UiContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        SumiTheme theme = ctx.theme();
        double pos = position.getAsDouble();
        float[] m = marker(pos);
        int ink = theme.color(SumiTheme.INK);
        int pageA = (int) Math.round(pos);
        int markerAccent = 0;
        for (Item item : items) {
            if (item.page == pageA) {
                markerAccent = item.accent;
            }
        }
        int markColor = markerAccent != 0 ? markerAccent : ink;
        if (orientation == Orientation.VERTICAL) {
            Paint.gradientH(g, x, m[0] + 2, width, m[1] - 4, Paint.fade(markColor, 0.16F), Paint.fade(markColor, 0.0F));
            Paint.brushLine(g, x + 2, m[0] + 3, x + 2, m[0] + m[1] - 3, 3.0F, theme.color(SumiTheme.LACQUER), 11L);
        } else {
            Paint.brushLine(g, m[0] + 6, y + height - 3, m[0] + m[1] - 6, y + height - 3, 3.0F, markColor, 5L);
        }
        double[] mouse = ctx.mouseIn(this);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.heading) {
                int hy = starts[i] + HEADING_HEIGHT - 11;
                Paint.text(g, ctx.font(), item.label.copy().withStyle(s -> s.withBold(true)), x + 6, hy, theme.color(SumiTheme.TEXT_MUTED));
                Paint.line(g, x + 8 + ctx.font().width(item.label) + 4, hy + 4, x + width - 4, hy + 4, 1.0F,
                        Paint.fade(theme.color(SumiTheme.INK_FAINT), 0.6F));
                continue;
            }
            float closeness = 1.0F - (float) Math.min(1.0, Math.abs(pos - item.page));
            boolean hover = ctx.isHovered(this) && inItem(i, mouse[0], mouse[1]);
            int base = theme.color(SumiTheme.TEXT_MUTED);
            int strong = item.accent != 0 ? Paint.mix(item.accent, theme.color(SumiTheme.TEXT), 0.3F) : theme.color(SumiTheme.TEXT);
            int color = Paint.mix(base, strong, Math.max(closeness, hover ? 0.6F : 0.0F));
            if (orientation == Orientation.VERTICAL) {
                Paint.text(g, ctx.font(), item.label, x + 10 + closeness * 2, starts[i] + (sizes[i] - 8) / 2.0F, color);
            } else {
                Paint.textCentered(g, ctx.font(), item.label, starts[i] + sizes[i] / 2.0F, y + (height - 10) / 2.0F, color);
            }
        }
        if (ctx.showFocus(this)) {
            Paint.outline(g, Paint.cutRectPoints(x + 1, y + 1, width - 2, height - 2, 3), 1.0F, theme.color(SumiTheme.LACQUER));
        }
    }

    private boolean inItem(int i, double mx, double my) {
        double along = orientation == Orientation.VERTICAL ? my : mx;
        return along >= starts[i] && along < starts[i] + sizes[i];
    }

    @Override
    public boolean mouseDown(UiContext ctx, double mx, double my, int button) {
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).heading && inItem(i, mx, my)) {
                select.accept(items.get(i).page);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public boolean key(UiContext ctx, KeyEvent event) {
        int current = (int) Math.round(position.getAsDouble());
        boolean vertical = orientation == Orientation.VERTICAL;
        int key = event.key();
        if (key == (vertical ? GLFW.GLFW_KEY_DOWN : GLFW.GLFW_KEY_RIGHT)) {
            select.accept(Math.min(pageCount() - 1, current + 1));
            return true;
        }
        if (key == (vertical ? GLFW.GLFW_KEY_UP : GLFW.GLFW_KEY_LEFT)) {
            select.accept(Math.max(0, current - 1));
            return true;
        }
        return false;
    }
}
