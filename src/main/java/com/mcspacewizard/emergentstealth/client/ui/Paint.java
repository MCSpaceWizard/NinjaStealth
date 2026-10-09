package com.mcspacewizard.emergentstealth.client.ui;

import java.util.List;

import com.mcspacewizard.emergentstealth.ui.PaperNoise;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Sumi's drawing helpers (design doc 31 §2 Paint): geometric shapes as float meshes, paper and ink fills
 * with the procedural textures, tapered brush strokes, and text. All coordinates are GUI pixels in the
 * current pose, as floats, so shapes stay smooth under animation. A global alpha multiplier
 * ({@link #pushAlpha}) fades whole subtrees.
 */
public final class Paint {
    private Paint() {}

    private static float alpha = 1.0F;
    private static final Mesh.Builder COLORED = Mesh.colored();

    // ------------------------------------------------------------------ colour

    /** Multiplies the global alpha; returns the previous value for {@link #popAlpha}. */
    public static float pushAlpha(float multiplier) {
        float previous = alpha;
        alpha *= Math.clamp(multiplier, 0.0F, 1.0F);
        return previous;
    }

    public static void popAlpha(float previous) {
        alpha = previous;
    }

    public static float alpha() {
        return alpha;
    }

    /** {@code argb} with the global alpha applied. */
    public static int applyAlpha(int argb) {
        if (alpha >= 1.0F) {
            return argb;
        }
        int a = Math.round((argb >>> 24) * alpha);
        return a << 24 | (argb & 0xFFFFFF);
    }

    /** {@code argb} with its alpha replaced by {@code a} (0..1) times its own. */
    public static int fade(int argb, float a) {
        int alphaBits = Math.round((argb >>> 24) * Math.clamp(a, 0.0F, 1.0F));
        return alphaBits << 24 | (argb & 0xFFFFFF);
    }

    public static int mix(int from, int to, float t) {
        t = Math.clamp(t, 0.0F, 1.0F);
        int a = Math.round(((from >>> 24) & 0xFF) + ((((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t));
        int r = Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
        int g = Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
        int b = Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return a << 24 | r << 16 | g << 8 | b;
    }

    // ------------------------------------------------------------------ flat geometry

    public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
        COLORED.quad(x, y, x, y + h, x + w, y + h, x + w, y, color).submit(g);
    }

    public static void gradientV(GuiGraphicsExtractor g, float x, float y, float w, float h, int top, int bottom) {
        COLORED.quad(x, y, top, x, y + h, bottom, x + w, y + h, bottom, x + w, y, top).submit(g);
    }

    public static void gradientH(GuiGraphicsExtractor g, float x, float y, float w, float h, int left, int right) {
        COLORED.quad(x, y, left, x, y + h, left, x + w, y + h, right, x + w, y, right).submit(g);
    }

    /** A convex polygon from {@code xy} pairs, filled as a fan. */
    public static void polygon(GuiGraphicsExtractor g, float[] xy, int color) {
        fan(COLORED, xy, color);
        COLORED.submit(g);
    }

    private static void fan(Mesh.Builder mesh, float[] xy, int color) {
        int n = xy.length / 2;
        for (int i = 1; i + 1 < n; i++) {
            mesh.tri(xy[0], xy[1], color, xy[i * 2], xy[i * 2 + 1], color, xy[i * 2 + 2], xy[i * 2 + 3], color);
        }
    }

    /** Outline of a closed polygon, {@code width} wide, centred on its edges. */
    public static void outline(GuiGraphicsExtractor g, float[] xy, float width, int color) {
        int n = xy.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            lineInto(COLORED, xy[i * 2], xy[i * 2 + 1], xy[j * 2], xy[j * 2 + 1], width, color, width / 2.0F);
        }
        COLORED.submit(g);
    }

    /** The 8 corners of a rectangle with its corners cut at 45 degrees. */
    public static float[] cutRectPoints(float x, float y, float w, float h, float cut) {
        float c = Math.min(cut, Math.min(w, h) / 2.0F);
        return new float[] {
                x + c, y, x + w - c, y, x + w, y + c, x + w, y + h - c,
                x + w - c, y + h, x + c, y + h, x, y + h - c, x, y + c};
    }

    public static void cutRect(GuiGraphicsExtractor g, float x, float y, float w, float h, float cut, int color) {
        polygon(g, cutRectPoints(x, y, w, h, cut), color);
    }

    /** Points of a regular polygon (circle when {@code sides} is large). */
    public static float[] regularPoints(float cx, float cy, float r, int sides, float rotation) {
        float[] xy = new float[sides * 2];
        for (int i = 0; i < sides; i++) {
            double a = rotation + i * Math.PI * 2.0 / sides;
            xy[i * 2] = cx + (float) Math.cos(a) * r;
            xy[i * 2 + 1] = cy + (float) Math.sin(a) * r;
        }
        return xy;
    }

    public static int segments(float r) {
        return Math.clamp(Math.round(r * 1.6F), 12, 64);
    }

    public static void circle(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
        polygon(g, regularPoints(cx, cy, r, segments(r), 0.0F), color);
    }

    /** A regular polygon: 4 = diamond (rotation 0), 6 = hexagon. */
    public static void regular(GuiGraphicsExtractor g, float cx, float cy, float r, int sides, float rotation, int color) {
        polygon(g, regularPoints(cx, cy, r, sides, rotation), color);
    }

    /** A disc that fades from {@code inner} at the centre to {@code outer} at the rim: glows and ink bleeds. */
    public static void glow(GuiGraphicsExtractor g, float cx, float cy, float r, int inner, int outer) {
        int n = segments(r);
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2.0 / n;
            double a1 = (i + 1) * Math.PI * 2.0 / n;
            COLORED.tri(cx, cy, inner, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, outer,
                    cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, outer);
        }
        COLORED.submit(g);
    }

    /** A ring between radii {@code r0} and {@code r1}. */
    public static void ring(GuiGraphicsExtractor g, float cx, float cy, float r0, float r1, int color) {
        arc(g, cx, cy, r0, r1, 0.0F, (float) (Math.PI * 2.0), color);
    }

    /** Part of a ring from angle {@code start}, sweeping {@code sweep} radians clockwise (0 = 3 o'clock). */
    public static void arc(GuiGraphicsExtractor g, float cx, float cy, float r0, float r1, float start, float sweep, int color) {
        int n = Math.max(2, Math.round(segments(r1) * Math.abs(sweep) / (float) (Math.PI * 2.0)));
        for (int i = 0; i < n; i++) {
            double a0 = start + sweep * i / n;
            double a1 = start + sweep * (i + 1) / n;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            COLORED.quad(cx + c0 * r0, cy + s0 * r0, cx + c0 * r1, cy + s0 * r1, cx + c1 * r1, cy + s1 * r1, cx + c1 * r0, cy + s1 * r0, color);
        }
        COLORED.submit(g);
    }

    /** Outline of a regular polygon. */
    public static void regularRing(GuiGraphicsExtractor g, float cx, float cy, float r, int sides, float rotation, float width, int color) {
        float[] outer = regularPoints(cx, cy, r, sides, rotation);
        float[] inner = regularPoints(cx, cy, r - width, sides, rotation);
        for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            COLORED.quad(inner[i * 2], inner[i * 2 + 1], outer[i * 2], outer[i * 2 + 1], outer[j * 2], outer[j * 2 + 1],
                    inner[j * 2], inner[j * 2 + 1], color);
        }
        COLORED.submit(g);
    }

    public static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float width, int color) {
        lineInto(COLORED, x0, y0, x1, y1, width, color, 0.0F);
        COLORED.submit(g);
    }

    private static void lineInto(Mesh.Builder mesh, float x0, float y0, float x1, float y1, float width, int color, float extend) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-4F) {
            return;
        }
        float ux = dx / len;
        float uy = dy / len;
        float nx = -uy * width / 2.0F;
        float ny = ux * width / 2.0F;
        x0 -= ux * extend;
        y0 -= uy * extend;
        x1 += ux * extend;
        y1 += uy * extend;
        mesh.quad(x0 + nx, y0 + ny, x0 - nx, y0 - ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny, color);
    }

    /** A dashed line along a polyline. */
    public static void dashed(GuiGraphicsExtractor g, float[] pts, float width, float dash, float gap, int color) {
        float period = dash + gap;
        float phase = 0.0F;
        for (int i = 0; i + 3 < pts.length; i += 2) {
            float x0 = pts[i];
            float y0 = pts[i + 1];
            float x1 = pts[i + 2];
            float y1 = pts[i + 3];
            float len = (float) Math.hypot(x1 - x0, y1 - y0);
            float at = 0.0F;
            while (at < len - 1.0E-4F) {
                boolean on = phase < dash;
                float step = Math.min(on ? dash - phase : period - phase, len - at);
                if (on) {
                    float t0 = at / len;
                    float t1 = (at + step) / len;
                    lineInto(COLORED, x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0, x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1, width, color, 0.0F);
                }
                at += step;
                phase += step;
                if (phase >= period - 1.0E-4F) {
                    phase = 0.0F;
                }
            }
        }
        COLORED.submit(g);
    }

    // ------------------------------------------------------------------ brush strokes

    /** Points along a quadratic Bezier curve. */
    public static float[] bezier(float x0, float y0, float cx, float cy, float x1, float y1, int segments) {
        float[] pts = new float[(segments + 1) * 2];
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float u = 1.0F - t;
            pts[i * 2] = u * u * x0 + 2 * u * t * cx + t * t * x1;
            pts[i * 2 + 1] = u * u * y0 + 2 * u * t * cy + t * t * y1;
        }
        return pts;
    }

    /** A straight brush stroke. */
    public static void brushLine(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float width, int color, long seed) {
        int segments = Math.max(2, Math.round((float) Math.hypot(x1 - x0, y1 - y0) / 4.0F));
        float[] pts = new float[(segments + 1) * 2];
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            pts[i * 2] = x0 + (x1 - x0) * t;
            pts[i * 2 + 1] = y0 + (y1 - y0) * t;
        }
        brush(g, pts, width, color, seed, 1.0F);
    }

    /**
     * A sumi brush stroke along a polyline: a pressed, blunt start, a body whose width wobbles with noise, and
     * a tail that lifts to a point. Alpha varies a little along the stroke like dry ink. {@code progress}
     * (0..1) draws only the first part, for ink-bleed reveals.
     */
    public static void brush(GuiGraphicsExtractor g, float[] pts, float width, int color, long seed, float progress) {
        int n = pts.length / 2;
        if (n < 2 || progress <= 0.0F) {
            return;
        }
        float[] cumulative = new float[n];
        for (int i = 1; i < n; i++) {
            cumulative[i] = cumulative[i - 1] + (float) Math.hypot(pts[i * 2] - pts[i * 2 - 2], pts[i * 2 + 1] - pts[i * 2 - 1]);
        }
        float total = cumulative[n - 1];
        if (total < 1.0E-3F) {
            return;
        }
        float limit = total * Math.min(1.0F, progress);
        float[] left = new float[n * 2];
        float[] right = new float[n * 2];
        int[] colors = new int[n];
        int last = 0;
        for (int i = 0; i < n; i++) {
            int prev = Math.max(0, i - 1);
            int next = Math.min(n - 1, i + 1);
            float tx = pts[next * 2] - pts[prev * 2];
            float ty = pts[next * 2 + 1] - pts[prev * 2 + 1];
            float tl = (float) Math.sqrt(tx * tx + ty * ty);
            if (tl < 1.0E-5F) {
                tx = 1.0F;
                ty = 0.0F;
                tl = 1.0F;
            }
            float t = cumulative[i] / total;
            float press = 0.55F + 0.45F * smooth(Math.min(1.0F, t / 0.12F));
            float lift = smooth(Math.min(1.0F, (1.0F - t) / 0.28F));
            float wob = 1.0F + 0.22F * PaperNoise.wobble(cumulative[i] / 9.0F, seed);
            float w = width * press * Math.max(0.12F, lift) * wob / 2.0F;
            float nx = -ty / tl * w;
            float ny = tx / tl * w;
            left[i * 2] = pts[i * 2] + nx;
            left[i * 2 + 1] = pts[i * 2 + 1] + ny;
            right[i * 2] = pts[i * 2] - nx;
            right[i * 2 + 1] = pts[i * 2 + 1] - ny;
            float dry = 0.86F + 0.14F * PaperNoise.wobble(cumulative[i] / 5.0F, seed * 7L + 3L);
            colors[i] = fade(color, dry);
            last = i;
            if (cumulative[i] >= limit) {
                break;
            }
        }
        for (int i = 0; i < last; i++) {
            int j = i + 1;
            COLORED.quad(left[i * 2], left[i * 2 + 1], colors[i], right[i * 2], right[i * 2 + 1], colors[i],
                    right[j * 2], right[j * 2 + 1], colors[j], left[j * 2], left[j * 2 + 1], colors[j]);
        }
        COLORED.submit(g);
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    // ------------------------------------------------------------------ paper and ink

    /** A polygon filled with paper grain tinted {@code tint}. */
    public static void paperPolygon(GuiGraphicsExtractor g, float[] xy, int tint) {
        texturedFan(g, SumiTextures.paper(), xy, tint, Sumi.theme().textureF(SumiTheme.GRAIN_SCALE));
    }

    /** A polygon filled with brushed ink in {@code color}. */
    public static void inkPolygon(GuiGraphicsExtractor g, float[] xy, int color) {
        texturedFan(g, SumiTextures.ink(), xy, color, 1.0F);
    }

    /** A ring sector (see {@link #arc}) filled with paper grain tinted {@code tint}: radial-menu wedges. */
    public static void paperArc(GuiGraphicsExtractor g, float cx, float cy, float r0, float r1, float start, float sweep, int tint) {
        texturedArc(g, SumiTextures.paper(), cx, cy, r0, r1, start, sweep, tint, Sumi.theme().textureF(SumiTheme.GRAIN_SCALE));
    }

    /** A ring sector filled with brushed ink in {@code color}. */
    public static void inkArc(GuiGraphicsExtractor g, float cx, float cy, float r0, float r1, float start, float sweep, int color) {
        texturedArc(g, SumiTextures.ink(), cx, cy, r0, r1, start, sweep, color, 1.0F);
    }

    private static void texturedArc(GuiGraphicsExtractor g, SumiTextures.Tex tex, float cx, float cy, float r0, float r1,
                                    float start, float sweep, int tint, float scale) {
        Mesh.Builder mesh = Mesh.textured(tex.setup());
        float su = scale / tex.width();
        float sv = scale / tex.height();
        int n = Math.max(2, Math.round(segments(r1) * Math.abs(sweep) / (float) (Math.PI * 2.0)));
        for (int i = 0; i < n; i++) {
            double a0 = start + sweep * i / n;
            double a1 = start + sweep * (i + 1) / n;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float x0 = cx + c0 * r0, y0 = cy + s0 * r0;
            float x1 = cx + c0 * r1, y1 = cy + s0 * r1;
            float x2 = cx + c1 * r1, y2 = cy + s1 * r1;
            float x3 = cx + c1 * r0, y3 = cy + s1 * r0;
            mesh.texQuad(x0, y0, x0 * su, y0 * sv, x1, y1, x1 * su, y1 * sv, x2, y2, x2 * su, y2 * sv, x3, y3, x3 * su, y3 * sv, tint);
        }
        mesh.submit(g);
    }

    private static void texturedFan(GuiGraphicsExtractor g, SumiTextures.Tex tex, float[] xy, int tint, float scale) {
        Mesh.Builder mesh = Mesh.textured(tex.setup());
        float su = scale / tex.width();
        float sv = scale / tex.height();
        int n = xy.length / 2;
        for (int i = 1; i + 1 < n; i++) {
            mesh.texTri(xy[0], xy[1], xy[0] * su, xy[1] * sv,
                    xy[i * 2], xy[i * 2 + 1], xy[i * 2] * su, xy[i * 2 + 1] * sv,
                    xy[i * 2 + 2], xy[i * 2 + 3], xy[i * 2 + 2] * su, xy[i * 2 + 3] * sv, tint);
        }
        mesh.submit(g);
    }

    /** A paper card: soft ink shadow, grained paper, a fine edge line. */
    public static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, int paper) {
        SumiTheme theme = Sumi.theme();
        float cut = theme.metricF(SumiTheme.CUT);
        int ink = theme.color(SumiTheme.INK);
        cutRect(g, x - 1, y + 2, w + 2, h + 3, cut + 1, fade(ink, 0.18F));
        cutRect(g, x + 1, y + 3, w - 2, h, cut, fade(ink, 0.28F));
        float[] shape = cutRectPoints(x, y, w, h, cut);
        paperPolygon(g, shape, paper);
        gradientV(g, x + cut, y + 1, w - cut * 2, Math.min(10, h / 3), fade(0xFFFFFFFF, 0.10F), 0x00FFFFFF);
        outline(g, shape, 1.0F, fade(theme.color(SumiTheme.PAPER_EDGE), 0.9F));
    }

    // ------------------------------------------------------------------ text

    /** Ink text: no drop shadow, global alpha applied. */
    public static void text(GuiGraphicsExtractor g, Font font, Component text, float x, float y, int color) {
        int c = applyAlpha(color);
        if ((c >>> 24) < 8) {
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.text(font, text, 0, 0, c, false);
        g.pose().popMatrix();
    }

    public static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, float x, float y, int color) {
        int c = applyAlpha(color);
        if ((c >>> 24) < 8) {
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.text(font, text, 0, 0, c, false);
        g.pose().popMatrix();
    }

    public static void textCentered(GuiGraphicsExtractor g, Font font, Component text, float cx, float y, int color) {
        text(g, font, text, cx - font.width(text) / 2.0F, y, color);
    }

    /** Text scaled by {@code scale} (titles). */
    public static void textScaled(GuiGraphicsExtractor g, Font font, Component text, float x, float y, float scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        text(g, font, text, 0, 0, color);
        g.pose().popMatrix();
    }

    /** Wrapped text; returns the height used. */
    public static int wrapped(GuiGraphicsExtractor g, Font font, FormattedText text, float x, float y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(10, width));
        for (int i = 0; i < lines.size(); i++) {
            text(g, font, lines.get(i), x, y + i * (font.lineHeight + 1), color);
        }
        return lines.size() * (font.lineHeight + 1);
    }

    public static int wrappedHeight(Font font, FormattedText text, int width) {
        return font.split(text, Math.max(10, width)).size() * (font.lineHeight + 1);
    }
}
