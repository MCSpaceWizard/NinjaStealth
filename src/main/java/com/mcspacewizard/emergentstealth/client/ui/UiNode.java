package com.mcspacewizard.emergentstealth.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * A node in a Sumi widget tree (design doc 31 §2 Core): a layout box with children, visibility, focus,
 * an optional tooltip, and input hooks. Coordinates are GUI pixels. Containers that move their children
 * (scrolling, paging) report a per-child offset, which hit testing and input use to map the pointer into
 * the child's space; rendering applies the same offset with the pose.
 *
 * <p>Input bubbles: the deepest node under the pointer (or the focused node, for keys) gets the event
 * first, then its ancestors until one consumes it. Drags are claimed: the first node from the pressed one
 * upwards that accepts a {@link Gesture} owns the rest of that drag.
 */
public abstract class UiNode {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected @Nullable UiNode parent;
    protected final List<UiNode> children = new ArrayList<>();
    protected boolean visible = true;
    protected int prefWidth = -1;
    protected int prefHeight = -1;
    protected float flex;
    protected @Nullable Supplier<List<Component>> tooltip;
    protected boolean focused;

    // ------------------------------------------------------------------ tree

    public <T extends UiNode> T add(T child) {
        child.parent = this;
        children.add(child);
        return child;
    }

    public void clearChildren() {
        for (UiNode child : children) {
            child.parent = null;
        }
        children.clear();
    }

    public List<UiNode> children() {
        return children;
    }

    public @Nullable UiNode parent() {
        return parent;
    }

    @SuppressWarnings("unchecked")
    public <T extends UiNode> T size(int w, int h) {
        this.prefWidth = w;
        this.prefHeight = h;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends UiNode> T flex(float weight) {
        this.flex = weight;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends UiNode> T tooltip(@Nullable Supplier<List<Component>> lines) {
        this.tooltip = lines;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends UiNode> T tooltip(Component line) {
        this.tooltip = () -> List.of(line);
        return (T) this;
    }

    public @Nullable Supplier<List<Component>> tooltip() {
        return tooltip;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

    // ------------------------------------------------------------------ layout

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public float flexWeight() {
        return flex;
    }

    public int preferredWidth() {
        return Math.max(0, prefWidth);
    }

    public int preferredHeight(int forWidth) {
        return Math.max(0, prefHeight);
    }

    public void layout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        layoutChildren();
    }

    /** Default: children overlay this node's whole box. */
    protected void layoutChildren() {
        for (UiNode child : children) {
            child.layout(x, y, width, height);
        }
    }

    // ------------------------------------------------------------------ rendering

    public final void render(UiContext ctx) {
        if (!visible) {
            return;
        }
        draw(ctx);
        renderChildren(ctx);
        drawOver(ctx);
    }

    /** Draws this node beneath its children. */
    protected void draw(UiContext ctx) {}

    /** Draws this node above its children. */
    protected void drawOver(UiContext ctx) {}

    protected void renderChildren(UiContext ctx) {
        for (UiNode child : children) {
            float ox = childOffsetX(child);
            float oy = childOffsetY(child);
            if (ox != 0.0F || oy != 0.0F) {
                ctx.graphics().pose().pushMatrix();
                ctx.graphics().pose().translate(-ox, -oy);
                child.render(ctx);
                ctx.graphics().pose().popMatrix();
            } else {
                child.render(ctx);
            }
        }
    }

    /** Animation step, once per frame before rendering. */
    public void update(UiContext ctx) {
        for (UiNode child : children) {
            if (child.visible) {
                child.update(ctx);
            }
        }
    }

    // ------------------------------------------------------------------ coordinates

    /** How far a child's space is shifted: pointer x in child space = pointer x here + this. */
    protected float childOffsetX(UiNode child) {
        return 0.0F;
    }

    protected float childOffsetY(UiNode child) {
        return 0.0F;
    }

    /** Whether children outside this node's box are hidden (and not hit). */
    protected boolean clipsChildren() {
        return false;
    }

    /** Whether this child takes part in hit testing right now. */
    protected boolean childHittable(UiNode child) {
        return true;
    }

    public boolean contains(double px, double py) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }

    /** The deepest visible node containing the point (in this node's parent space), or null. */
    public @Nullable UiNode hit(double px, double py) {
        if (!visible) {
            return null;
        }
        boolean inside = contains(px, py);
        if (clipsChildren() && !inside) {
            return null;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            UiNode child = children.get(i);
            if (!childHittable(child)) {
                continue;
            }
            UiNode found = child.hit(px + childOffsetX(child), py + childOffsetY(child));
            if (found != null) {
                return found;
            }
        }
        return inside ? this : null;
    }

    /** Screen point to this node's own space (the space its x/y live in). */
    public double[] toLocal(double sx, double sy) {
        double lx = sx;
        double ly = sy;
        List<UiNode> chain = new ArrayList<>();
        for (UiNode n = this; n.parent != null; n = n.parent) {
            chain.add(n);
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            UiNode child = chain.get(i);
            lx += child.parent.childOffsetX(child);
            ly += child.parent.childOffsetY(child);
        }
        return new double[] {lx, ly};
    }

    /** This node's box on screen (undoing scroll and page offsets). */
    public float[] screenBox() {
        double[] origin = toLocal(0, 0);
        return new float[] {(float) (x - origin[0]), (float) (y - origin[1]), width, height};
    }

    // ------------------------------------------------------------------ input (local coordinates)

    public boolean mouseDown(UiContext ctx, double mx, double my, int button) {
        return false;
    }

    /** The pointer was released after a press this node consumed. {@code inside}: still over this node. */
    public void mouseUp(UiContext ctx, double mx, double my, int button, boolean inside) {}

    /** Offered a drag: return true to own it from now on. */
    public boolean claimDrag(UiContext ctx, Gesture gesture) {
        return false;
    }

    /** A drag this node owns moved by (dx, dy). */
    public void drag(UiContext ctx, double mx, double my, double dx, double dy) {}

    public void dragEnd(UiContext ctx) {}

    /** A press this node consumed was taken over by another node's drag. */
    public void pressCancelled(UiContext ctx) {}

    public boolean scroll(UiContext ctx, double mx, double my, double scrollX, double scrollY) {
        return false;
    }

    public boolean key(UiContext ctx, KeyEvent event) {
        return false;
    }

    public boolean character(UiContext ctx, CharacterEvent event) {
        return false;
    }

    // ------------------------------------------------------------------ focus

    public boolean isFocusable() {
        return false;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    /** All focusable, visible nodes in tree order. */
    public void collectFocusable(List<UiNode> out) {
        if (!visible) {
            return;
        }
        if (isFocusable()) {
            out.add(this);
        }
        for (UiNode child : children) {
            if (childHittable(child)) {
                child.collectFocusable(out);
            }
        }
    }

    /** Asks ancestors (scroll views, pagers) to bring {@code node} into view. */
    public void reveal(UiNode node) {
        if (parent != null) {
            parent.reveal(node);
        }
    }

    /** A drag in progress: where it started and how far it's moved in total. */
    public record Gesture(double startX, double startY, double totalX, double totalY, int button) {
        public double distance() {
            return Math.sqrt(totalX * totalX + totalY * totalY);
        }

        public boolean horizontal() {
            return Math.abs(totalX) > Math.abs(totalY);
        }
    }
}
