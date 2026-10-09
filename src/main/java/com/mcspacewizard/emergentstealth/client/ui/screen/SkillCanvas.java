package com.mcspacewizard.emergentstealth.client.ui.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.client.ui.widget.SumiSounds;
import com.mcspacewizard.emergentstealth.progression.PlayerProgression;
import com.mcspacewizard.emergentstealth.progression.SkillDefinition;
import com.mcspacewizard.emergentstealth.progression.SkillEffect;
import com.mcspacewizard.emergentstealth.progression.SkillPath;
import com.mcspacewizard.emergentstealth.progression.Skills;
import com.mcspacewizard.emergentstealth.progression.UnlockSkillPayload;
import com.mcspacewizard.emergentstealth.ui.Easing;
import com.mcspacewizard.emergentstealth.ui.InertialScroll;
import com.mcspacewizard.emergentstealth.ui.SkillTreeModel;
import com.mcspacewizard.emergentstealth.ui.SkillTreeModel.Node;
import com.mcspacewizard.emergentstealth.ui.SkillTreeModel.NodeState;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.Tween;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * One path of the skill tree as a pan-and-zoom canvas (design doc 31 §3.2, the {@code NodeGraphCanvas}):
 * skills are seals at their grid positions, prerequisites are brush strokes, a buyable seal glows, and a
 * learned one is stamped in ink. Clicking a glowing seal asks the server to unlock it; the stamp lands when
 * the server's answer arrives (the server stays authoritative).
 */
final class SkillCanvas extends UiNode {
    private static final float MIN_ZOOM = 0.45F;
    private static final float MAX_ZOOM = 2.0F;
    private static final long PENDING_TIMEOUT_MS = 2500L;

    private final SkillPath path;
    private final SkillTreeScreen screen;
    private SkillTreeModel.PathTree tree;
    private @Nullable PlayerProgression lastProgression;
    private final Set<Identifier> owned = new HashSet<>();
    private final InertialScroll panX = new InertialScroll();
    private final InertialScroll panY = new InertialScroll();
    private float zoom = -1.0F;
    private float zoomTarget = 1.0F;
    private boolean anchored;
    private float anchorWorldX;
    private float anchorWorldY;
    private float anchorScreenX;
    private float anchorScreenY;
    private final Map<Identifier, Tween> hover = new HashMap<>();
    private final Map<Identifier, Long> stampedAt = new HashMap<>();
    private final Map<Identifier, Long> pending = new HashMap<>();
    private final Map<Identifier, Long> refusedAt = new HashMap<>();
    private final Map<Identifier, ItemStack> icons = new HashMap<>();
    private @Nullable Identifier hovered;
    private @Nullable Identifier focusedSkill;
    private @Nullable Identifier pressedSkill;
    private long revealAt = -1L;

    SkillCanvas(SkillPath path, SkillTreeScreen screen) {
        this.path = path;
        this.screen = screen;
        this.tree = SkillTreeModel.build(player(), path);
        for (Node node : tree.nodes()) {
            if (node.state() == NodeState.OWNED) {
                owned.add(node.id());
            }
        }
        lastProgression = Skills.progression(player());
    }

    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    SkillTreeModel.PathTree tree() {
        return tree;
    }

    SkillPath path() {
        return path;
    }

    int accent() {
        return Sumi.color(path == SkillPath.SHINOBI ? SumiTheme.SHINOBI : SumiTheme.SHOGUNATE);
    }

    // ------------------------------------------------------------------ geometry

    private float spacingX() {
        return Sumi.theme().metricF(SumiTheme.NODE_SPACING_X);
    }

    private float spacingY() {
        return Sumi.theme().metricF(SumiTheme.NODE_SPACING_Y);
    }

    private float radius() {
        return Sumi.theme().metricF(SumiTheme.NODE_RADIUS);
    }

    /** Room around the content for seal radii and names; scales with zoom so small views still fit. */
    private float margin() {
        return radius() * 1.6F * Math.max(zoom, MIN_ZOOM) + 8.0F;
    }

    private float contentW() {
        return Math.max(0, tree.maxColumn() - tree.minColumn()) * spacingX();
    }

    private float contentH() {
        return tree.maxRow() * spacingY();
    }

    private float originX() {
        float scaled = contentW() * zoom;
        if (scaled + 2 * margin() <= width) {
            return x + (width - scaled) / 2.0F;
        }
        return x + margin() - panX.position();
    }

    private float originY() {
        float scaled = contentH() * zoom;
        if (scaled + 2 * margin() <= height) {
            return y + (height - scaled) / 2.0F;
        }
        return y + margin() - panY.position();
    }

    float nodeX(Node node) {
        return originX() + (node.column() - tree.minColumn()) * spacingX() * zoom;
    }

    float nodeY(Node node) {
        return originY() + node.row() * spacingY() * zoom;
    }

    float nodeRadius() {
        return radius() * zoom;
    }

    private void updateRanges() {
        panX.setMax(contentW() * zoom + 2 * margin() - width);
        panY.setMax(contentH() * zoom + 2 * margin() - height);
    }

    private float fitZoom() {
        // Margin depends on zoom, so solve from both sides: size * z + 2 * (1.6 r z + 8) = view.
        float edge = radius() * 3.2F;
        float fx = contentW() <= 0 ? MAX_ZOOM : (width - 16) / (contentW() + edge);
        float fy = contentH() <= 0 ? MAX_ZOOM : (height - 16) / (contentH() + edge);
        return Math.clamp(Math.min(1.3F, Math.min(fx, fy)), MIN_ZOOM, MAX_ZOOM);
    }

    @Override
    protected void layoutChildren() {
        if (zoom < 0) {
            zoom = fitZoom();
            zoomTarget = zoom;
        }
        updateRanges();
    }

    private @Nullable Node nodeAt(double mx, double my) {
        float r = nodeRadius() * 1.2F;
        for (Node node : tree.nodes()) {
            float dx = (float) mx - nodeX(node);
            float dy = (float) my - nodeY(node);
            if (dx * dx + dy * dy <= r * r) {
                return node;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ state

    @Override
    public void update(UiContext ctx) {
        if (revealAt < 0) {
            revealAt = ctx.now() + 120L;
        }
        PlayerProgression progression = Skills.progression(player());
        if (progression != lastProgression) {
            lastProgression = progression;
            tree = SkillTreeModel.build(player(), path);
            for (Node node : tree.nodes()) {
                if (node.state() == NodeState.OWNED && owned.add(node.id())) {
                    stampedAt.put(node.id(), ctx.now());
                    pending.remove(node.id());
                    SumiSounds.stamp();
                }
            }
            owned.removeIf(id -> tree.node(id) == null || tree.node(id).state() != NodeState.OWNED);
        }
        pending.values().removeIf(at -> ctx.now() - at > PENDING_TIMEOUT_MS);

        // Zoom glides towards its target, keeping the anchored point under the cursor.
        boolean instant = Sumi.reducedMotion();
        panX.setInstant(instant);
        panY.setInstant(instant);
        if (zoom != zoomTarget) {
            zoom = instant ? zoomTarget : zoom + (zoomTarget - zoom) * (1.0F - (float) Math.exp(-14.0F * ctx.dt()));
            if (Math.abs(zoom - zoomTarget) < 0.002F) {
                zoom = zoomTarget;
            }
            updateRanges();
            if (anchored) {
                panX.scrollTo(x + margin() - (anchorScreenX - anchorWorldX * zoom), true);
                panY.scrollTo(y + margin() - (anchorScreenY - anchorWorldY * zoom), true);
            }
        } else {
            anchored = false;
        }
        panX.update(ctx.dt());
        panY.update(ctx.dt());

        double[] mouse = ctx.mouseIn(this);
        Node over = ctx.isHovered(this) ? nodeAt(mouse[0], mouse[1]) : null;
        hovered = over != null ? over.id() : null;
        for (Node node : tree.nodes()) {
            boolean lit = node.id().equals(hovered) || (ctx.showFocus(this) && node.id().equals(focusedSkill));
            hover.computeIfAbsent(node.id(), id -> new Tween(0.0F))
                    .animate(lit ? 1.0F : 0.0F, ctx.now(), ctx.duration(SumiTheme.FAST), Easing.CUBIC_OUT);
        }
        super.update(ctx);
    }

    /** The skill whose card should show: hovered with the mouse, or focused with the keyboard. */
    @Nullable Node cardNode(UiContext ctx) {
        if (ctx.showFocus(this) && focusedSkill != null) {
            return tree.node(focusedSkill);
        }
        return hovered != null ? tree.node(hovered) : null;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void draw(UiContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        SumiTheme theme = ctx.theme();
        int accent = accent();
        long now = ctx.now();

        // Watermark: the path's character, very faint.
        String mark = path == SkillPath.SHINOBI ? "忍" : "武";
        float big = Math.min(width, height) / 9.0F / 1.6F;
        Paint.textScaled(g, ctx.font(), Component.literal(mark), x + width / 2.0F - 4 * big, y + height / 2.0F - 4.5F * big, big,
                Paint.fade(accent, 0.06F));

        if (tree.isEmpty()) {
            Paint.textCentered(g, ctx.font(), Component.translatable("ui.emergentstealth.skills.empty"), x + width / 2.0F, y + height / 2.0F,
                    theme.color(SumiTheme.TEXT_MUTED));
            return;
        }
        float r = nodeRadius();
        // Row guides.
        for (int row = 0; row <= tree.maxRow(); row++) {
            float ry = originY() + row * spacingY() * zoom;
            Paint.dashed(g, new float[] {x + 6, ry, x + width - 6, ry}, 1.0F, 2.0F, 6.0F, Paint.fade(theme.color(SumiTheme.INK_FAINT), 0.18F));
        }
        // Prerequisite strokes, revealed row by row.
        for (SkillTreeModel.Edge edge : tree.edges()) {
            Node from = tree.node(edge.from());
            Node to = tree.node(edge.to());
            if (from == null || to == null) {
                continue;
            }
            float x0 = nodeX(from);
            float y0 = nodeY(from) + r;
            float x1 = nodeX(to);
            float y1 = nodeY(to) - r;
            float[] curve = Paint.bezier(x0, y0, x0, (y0 + y1) / 2.0F, x1, y1, 16);
            float reveal = reveal(now, from.row());
            boolean childOwned = to.state() == NodeState.OWNED;
            if (edge.satisfied()) {
                int color = childOwned ? accent : Paint.fade(accent, 0.75F);
                Paint.brush(g, curve, (childOwned ? 3.5F : 2.5F) * Math.max(0.7F, zoom), color, edge.from().hashCode() * 31L + edge.to().hashCode(), reveal);
            } else if (reveal > 0.99F) {
                Paint.dashed(g, curve, 1.25F, edge.anyOf() ? 2.0F : 4.0F, edge.anyOf() ? 3.0F : 3.0F,
                        Paint.fade(theme.color(SumiTheme.INK_FAINT), 0.75F));
            }
        }
        // Seals.
        for (Node node : tree.nodes()) {
            drawNode(ctx, g, node, now);
        }
        for (Node node : tree.nodes()) {
            drawLabel(ctx, g, node, now);
        }
    }

    private float reveal(long now, int row) {
        int slow = Sumi.duration(SumiTheme.SLOW);
        if (slow <= 0) {
            return 1.0F;
        }
        long start = revealAt + row * (long) Sumi.duration(SumiTheme.STAGGER) * 3;
        return Easing.CUBIC_OUT.apply(Math.clamp((now - start) / (float) slow, 0.0F, 1.0F));
    }

    private void drawNode(UiContext ctx, GuiGraphicsExtractor g, Node node, long now) {
        SumiTheme theme = ctx.theme();
        int accent = accent();
        float appear = reveal(now, node.row());
        if (appear <= 0.01F) {
            return;
        }
        float cx = nodeX(node);
        float cy = nodeY(node);
        float h = hover.containsKey(node.id()) ? hover.get(node.id()).value(now) : 0.0F;
        float stamp = 1.0F;
        Long stampTime = stampedAt.get(node.id());
        int normal = Sumi.duration(SumiTheme.SLOW);
        if (stampTime != null && normal > 0 && now - stampTime < normal * 2L) {
            stamp = Math.clamp((now - stampTime) / (float) (normal * 2), 0.0F, 1.0F);
        }
        float shake = 0.0F;
        Long refused = refusedAt.get(node.id());
        if (refused != null && now - refused < 360L && Sumi.duration(SumiTheme.FAST) > 0) {
            float t = (now - refused) / 360.0F;
            shake = (float) Math.sin(t * Math.PI * 6) * 3.0F * (1.0F - t);
        }
        cx += shake;
        float scale = (0.6F + 0.4F * Easing.BACK_OUT.apply(appear)) * (1.0F + 0.12F * h);
        if (stamp < 1.0F) {
            scale *= 1.0F + 0.5F * (1.0F - Easing.BACK_OUT.apply(Math.min(1.0F, stamp * 2.0F)));
        }
        float r = nodeRadius() * scale;
        float previous = Paint.pushAlpha(appear);
        NodeState state = node.state();
        boolean capstone = node.skill().capstone();
        int sides = capstone ? 6 : Paint.segments(r);
        float rot = capstone ? (float) (Math.PI / 6) : 0.0F;

        // Glow for what you can buy, and the ink splash of a fresh stamp.
        if (state == NodeState.AVAILABLE) {
            float pulse = Sumi.reducedMotion() ? 0.8F : 0.75F + 0.25F * (float) Math.sin(now / 320.0);
            Paint.glow(g, cx, cy, r * 2.1F, Paint.fade(theme.color(SumiTheme.GOLD), 0.42F * pulse), Paint.fade(theme.color(SumiTheme.GOLD), 0.0F));
        }
        if (stamp < 1.0F) {
            Paint.glow(g, cx, cy, r * (1.2F + 2.0F * stamp), Paint.fade(accent, 0.55F * (1.0F - stamp)), Paint.fade(accent, 0.0F));
        }
        // Shadow.
        Paint.regular(g, cx, cy + 1.5F, r, sides, rot, Paint.fade(theme.color(SumiTheme.INK), 0.3F));
        float[] shape = Paint.regularPoints(cx, cy, r, sides, rot);
        switch (state) {
            case OWNED -> {
                Paint.inkPolygon(g, shape, accent);
                Paint.regularRing(g, cx, cy, r - 2.0F, sides, rot, 1.0F, Paint.fade(theme.color(SumiTheme.PAPER), 0.45F));
            }
            case AVAILABLE -> {
                Paint.paperPolygon(g, shape, theme.color(SumiTheme.PAPER));
                Paint.regularRing(g, cx, cy, r, sides, rot, 2.5F, theme.color(SumiTheme.LACQUER));
            }
            case NEEDS_POINTS -> {
                Paint.paperPolygon(g, shape, theme.color(SumiTheme.PAPER));
                Paint.regularRing(g, cx, cy, r, sides, rot, 1.5F, theme.color(SumiTheme.INK_SOFT));
            }
            case LOCKED, CAPSTONE -> {
                Paint.paperPolygon(g, shape, theme.color(SumiTheme.PAPER_SHADE));
                Paint.dashed(g, closed(shape), 1.25F, 3.0F, 2.5F, theme.color(SumiTheme.INK_FAINT));
            }
        }
        if (capstone) {
            Paint.regularRing(g, cx, cy, r + 3.0F, 6, rot, 1.0F, Paint.fade(theme.color(SumiTheme.GOLD), 0.9F));
        }
        if (h > 0.01F) {
            Paint.regularRing(g, cx, cy, r + 2.5F, sides, rot, 1.0F, Paint.fade(theme.color(SumiTheme.INK), 0.6F * h));
        }
        // Icon.
        ItemStack icon = icons.computeIfAbsent(node.id(), id -> iconFor(node.skill()));
        float iconScale = Math.min(1.25F, r / 11.0F);
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(iconScale, iconScale);
        g.pose().translate(-8, -8);
        g.item(icon, 0, 0);
        g.pose().popMatrix();
        if (state == NodeState.LOCKED || state == NodeState.CAPSTONE) {
            Paint.regular(g, cx, cy, r - 1.5F, sides, rot, Paint.fade(theme.color(SumiTheme.PAPER_SHADE), 0.55F));
        }
        // Pending: a spinning arc while the server answers.
        if (pending.containsKey(node.id())) {
            float a = (now % 900L) / 900.0F * (float) (Math.PI * 2);
            Paint.arc(g, cx, cy, r + 3.0F, r + 5.0F, a, (float) Math.PI * 1.2F, theme.color(SumiTheme.LACQUER));
        }
        // Keyboard focus.
        if (ctx.showFocus(this) && node.id().equals(focusedSkill)) {
            Paint.dashed(g, closed(Paint.regularPoints(cx, cy, r + 5.5F, 24, now / 1200.0F)), 1.5F, 3.0F, 3.0F, theme.color(SumiTheme.LACQUER));
        }
        // Cost pip and name.
        if (state != NodeState.OWNED && node.skill().cost() > 0) {
            float px = cx + r * 0.72F;
            float py = cy - r * 0.72F;
            Paint.circle(g, px, py, 5.0F, state == NodeState.AVAILABLE ? theme.color(SumiTheme.LACQUER) : theme.color(SumiTheme.INK_SOFT));
            Paint.textCentered(g, ctx.font(), Component.literal(String.valueOf(node.skill().cost())), px + 0.5F, py - 3.5F,
                    theme.color(SumiTheme.TEXT_ON_INK));
        }
        Paint.popAlpha(previous);
    }

    /** A seal's name under it, on a soft paper wash so strokes passing behind stay readable. */
    private void drawLabel(UiContext ctx, GuiGraphicsExtractor g, Node node, long now) {
        float appear = reveal(now, node.row());
        if (zoom < 0.75F || appear <= 0.01F) {
            return;
        }
        SumiTheme theme = ctx.theme();
        float previous = Paint.pushAlpha(appear);
        Component name = Component.translatable(SkillDefinition.nameKey(node.id()));
        float s = 0.75F;
        float tw = ctx.font().width(name) * s;
        float lx = nodeX(node) - tw / 2.0F;
        float ly = nodeY(node) + nodeRadius() + 4.0F;
        Paint.cutRect(g, lx - 2, ly - 1.5F, tw + 4, 9, 2, Paint.fade(theme.color(SumiTheme.PAPER), 0.85F));
        NodeState state = node.state();
        int color = state == NodeState.OWNED || state == NodeState.AVAILABLE ? theme.color(SumiTheme.TEXT) : theme.color(SumiTheme.TEXT_MUTED);
        Paint.textScaled(g, ctx.font(), name, lx, ly, s, color);
        Paint.popAlpha(previous);
    }

    private static float[] closed(float[] pts) {
        float[] out = new float[pts.length + 2];
        System.arraycopy(pts, 0, out, 0, pts.length);
        out[pts.length] = pts[0];
        out[pts.length + 1] = pts[1];
        return out;
    }

    private static ItemStack iconFor(SkillDefinition skill) {
        var item = BuiltInRegistries.ITEM.getValue(skill.icon());
        return new ItemStack(item == null || item == Items.AIR ? Items.PAPER : item);
    }

    // ------------------------------------------------------------------ the hover card

    List<Component> cardLines(Node node) {
        SumiTheme theme = Sumi.theme();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(SkillDefinition.nameKey(node.id())).withStyle(ChatFormatting.BOLD));
        lines.add(Component.translatable(SkillDefinition.descriptionKey(node.id())));
        for (SkillEffect effect : node.skill().effects()) {
            lines.add(Component.literal("• ").append(describe(effect)));
        }
        LocalPlayer player = player();
        if (node.state() != NodeState.OWNED) {
            lines.add(Component.translatable("ui.emergentstealth.skills.cost", node.skill().cost()));
        }
        if (!node.skill().requires().isEmpty() && node.state() == NodeState.LOCKED) {
            List<Component> names = new ArrayList<>();
            for (Identifier req : node.skill().requires()) {
                boolean has = Skills.progression(player).has(req);
                names.add(Component.translatable(SkillDefinition.nameKey(req)).withStyle(has ? ChatFormatting.STRIKETHROUGH : ChatFormatting.RESET));
            }
            MutableComponent joined = Component.empty();
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    joined.append(Component.translatable(node.skill().requireAll() ? "ui.emergentstealth.skills.and" : "ui.emergentstealth.skills.or"));
                }
                joined.append(names.get(i));
            }
            lines.add(Component.translatable("ui.emergentstealth.skills.requires", joined));
        }
        String status = switch (node.state()) {
            case OWNED -> "ui.emergentstealth.skills.status.owned";
            case AVAILABLE -> "ui.emergentstealth.skills.status.available";
            case NEEDS_POINTS -> "ui.emergentstealth.skills.status.points";
            case LOCKED -> "ui.emergentstealth.skills.status.locked";
            case CAPSTONE -> "ui.emergentstealth.skills.status.capstone";
        };
        int statusColor = switch (node.state()) {
            case OWNED -> theme.color(SumiTheme.JADE);
            case AVAILABLE -> theme.color(SumiTheme.LACQUER);
            case CAPSTONE -> Paint.mix(theme.color(SumiTheme.GOLD), theme.color(SumiTheme.INK), 0.3F);
            default -> theme.color(SumiTheme.TEXT_MUTED);
        };
        if (pending.containsKey(node.id())) {
            status = "ui.emergentstealth.skills.status.pending";
        }
        lines.add(Component.translatable(status).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(statusColor & 0xFFFFFF)).withItalic(true)));
        return lines;
    }

    static Component describe(SkillEffect effect) {
        return switch (effect) {
            case SkillEffect.StatModifier stat -> {
                int percent = Math.round((stat.stat().additive() ? stat.value() : stat.value() - 1.0F) * 100.0F);
                String amount = stat.stat().additive() ? (stat.value() > 0 ? "+" : "") + Math.round(stat.value())
                        : (percent > 0 ? "+" : "") + percent + "%";
                yield Component.translatable("stealth_stat.emergentstealth." + stat.stat().getSerializedName()).append(" " + amount);
            }
            case SkillEffect.AttributeBonus bonus -> {
                Attribute attribute = BuiltInRegistries.ATTRIBUTE.getValue(bonus.attribute());
                Component name = attribute != null ? Component.translatable(attribute.getDescriptionId()) : Component.literal(bonus.attribute().toString());
                String amount = bonus.operation().equals("add_value")
                        ? (bonus.amount() >= 0 ? "+" : "") + trim(bonus.amount())
                        : (bonus.amount() >= 0 ? "+" : "") + Math.round(bonus.amount() * 100) + "%";
                yield Component.empty().append(name).append(" " + amount);
            }
            case SkillEffect.UnlockTechnique technique -> Component.translatable("ui.emergentstealth.skills.technique",
                    Component.translatable("technique." + technique.technique().getNamespace() + "." + technique.technique().getPath()));
            case SkillEffect.UnlockRecipe recipe -> Component.translatable("ui.emergentstealth.skills.recipe", recipe.recipe().toString());
        };
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    // ------------------------------------------------------------------ input

    private void tryUnlock(UiContext ctx, Node node) {
        if (node.state() != NodeState.AVAILABLE || pending.containsKey(node.id())) {
            refusedAt.put(node.id(), ctx.now());
            SumiSounds.tick();
            return;
        }
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(UnlockSkillPayload.TYPE)) {
            refusedAt.put(node.id(), ctx.now());
            return;
        }
        ClientPacketDistributor.sendToServer(new UnlockSkillPayload(node.id()));
        pending.put(node.id(), ctx.now());
        SumiSounds.click();
    }

    @Override
    public boolean mouseDown(UiContext ctx, double mx, double my, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        Node node = nodeAt(mx, my);
        pressedSkill = node != null ? node.id() : null;
        if (node != null) {
            focusedSkill = node.id();
        }
        return node != null;
    }

    @Override
    public void mouseUp(UiContext ctx, double mx, double my, int button, boolean inside) {
        Node node = nodeAt(mx, my);
        if (node != null && node.id().equals(pressedSkill)) {
            tryUnlock(ctx, node);
        }
        pressedSkill = null;
    }

    @Override
    public void pressCancelled(UiContext ctx) {
        pressedSkill = null;
    }

    @Override
    public boolean claimDrag(UiContext ctx, Gesture gesture) {
        // Horizontal drags swipe between paths unless there's room to pan sideways.
        if (gesture.horizontal() && panX.max() <= 0) {
            return false;
        }
        if (!gesture.horizontal() && panY.max() <= 0 && panX.max() <= 0) {
            return false;
        }
        anchored = false;
        panX.beginDrag(ctx.now());
        panY.beginDrag(ctx.now());
        return true;
    }

    @Override
    public void drag(UiContext ctx, double mx, double my, double dx, double dy) {
        panX.dragBy((float) -dx, ctx.now());
        panY.dragBy((float) -dy, ctx.now());
    }

    @Override
    public void dragEnd(UiContext ctx) {
        panX.endDrag(ctx.now());
        panY.endDrag(ctx.now());
    }

    @Override
    public boolean scroll(UiContext ctx, double mx, double my, double scrollX, double scrollY) {
        if (scrollY == 0) {
            return false;
        }
        zoomAround(mx, my, (float) Math.pow(1.15, scrollY));
        return true;
    }

    private void zoomAround(double mx, double my, float factor) {
        float target = Math.clamp(zoomTarget * factor, MIN_ZOOM, MAX_ZOOM);
        if (target == zoomTarget) {
            return;
        }
        anchorScreenX = (float) mx;
        anchorScreenY = (float) my;
        anchorWorldX = ((float) mx - originX()) / zoom;
        anchorWorldY = ((float) my - originY()) / zoom;
        anchored = true;
        zoomTarget = target;
    }

    @Override
    public boolean isFocusable() {
        return !tree.isEmpty();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused && focusedSkill == null && !tree.isEmpty()) {
            focusedSkill = tree.nodes().getFirst().id();
        }
    }

    @Override
    public boolean key(UiContext ctx, KeyEvent event) {
        Node current = focusedSkill != null ? tree.node(focusedSkill) : null;
        int key = event.key();
        int dx = key == GLFW.GLFW_KEY_RIGHT ? 1 : key == GLFW.GLFW_KEY_LEFT ? -1 : 0;
        int dy = key == GLFW.GLFW_KEY_DOWN ? 1 : key == GLFW.GLFW_KEY_UP ? -1 : 0;
        if (dx != 0 || dy != 0) {
            if (current == null) {
                focusedSkill = tree.isEmpty() ? null : tree.nodes().getFirst().id();
                return true;
            }
            Node next = SkillTreeModel.neighbour(tree.nodes(), current, dx, dy);
            if (next != null) {
                focusedSkill = next.id();
                revealNode(next);
                return true;
            }
            return dx != 0 ? false : true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_KP_ENTER) && current != null) {
            tryUnlock(ctx, current);
            return true;
        }
        if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
            zoomAround(x + width / 2.0, y + height / 2.0, 1.2F);
            return true;
        }
        if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
            zoomAround(x + width / 2.0, y + height / 2.0, 1 / 1.2F);
            return true;
        }
        if (key == GLFW.GLFW_KEY_0 || key == GLFW.GLFW_KEY_KP_0) {
            anchored = false;
            zoomTarget = fitZoom();
            panX.scrollTo(0, false);
            panY.scrollTo(0, false);
            return true;
        }
        return false;
    }

    /** Pans so a node is comfortably inside the view. */
    private void revealNode(Node node) {
        float m = margin();
        float nx = nodeX(node);
        float ny = nodeY(node);
        if (nx < x + m) {
            panX.scrollBy(nx - (x + m));
        } else if (nx > x + width - m) {
            panX.scrollBy(nx - (x + width - m));
        }
        if (ny < y + m) {
            panY.scrollBy(ny - (y + m));
        } else if (ny > y + height - m) {
            panY.scrollBy(ny - (y + height - m));
        }
    }

    /** Where the card for {@code node} goes, in this canvas's space. */
    float[] cardAnchor(Node node) {
        return new float[] {nodeX(node) + nodeRadius() + 8, nodeY(node) - nodeRadius()};
    }
}
