package com.mcspacewizard.emergentstealth.client.ui;

import java.util.Arrays;

import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

/**
 * A batch of free-form quads submitted as one GUI element (design doc 31 §0). This is how Sumi draws
 * geometry (circles, rings, polygons, brush strokes) with float vertices, per-vertex colour and optional
 * texture coordinates, through NeoForge's {@code submitGuiElementRenderState} hook. Triangles are quads with
 * the last vertex repeated.
 */
public final class Mesh implements GuiElementRenderState {
    private final RenderPipeline pipeline;
    private final TextureSetup textureSetup;
    private final Matrix3x2f pose;
    private final float[] positions;
    private final float @Nullable [] uvs;
    private final int[] colors;
    private final int vertices;
    private final @Nullable ScreenRectangle scissor;
    private final @Nullable ScreenRectangle bounds;

    private Mesh(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, float[] positions, float @Nullable [] uvs,
                 int[] colors, int vertices, @Nullable ScreenRectangle scissor, @Nullable ScreenRectangle bounds) {
        this.pipeline = pipeline;
        this.textureSetup = textureSetup;
        this.pose = pose;
        this.positions = positions;
        this.uvs = uvs;
        this.colors = colors;
        this.vertices = vertices;
        this.scissor = scissor;
        this.bounds = bounds;
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (int i = 0; i < vertices; i++) {
            VertexConsumer v = consumer.addVertexWith2DPose(pose, positions[i * 2], positions[i * 2 + 1]);
            if (uvs != null) {
                v.setUv(uvs[i * 2], uvs[i * 2 + 1]);
            }
            v.setColor(colors[i]);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return pipeline;
    }

    @Override
    public TextureSetup textureSetup() {
        return textureSetup;
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return scissor;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return bounds;
    }

    public static Builder colored() {
        return new Builder(null);
    }

    public static Builder textured(TextureSetup texture) {
        return new Builder(texture);
    }

    /** Collects quads, then {@link #submit}s them. Reusable after submitting. */
    public static final class Builder {
        private final @Nullable TextureSetup texture;
        private float[] positions = new float[64];
        private float[] uvs;
        private int[] colors = new int[32];
        private int count;
        private float minX;
        private float minY;
        private float maxX;
        private float maxY;

        private Builder(@Nullable TextureSetup texture) {
            this.texture = texture;
            this.uvs = texture != null ? new float[64] : new float[0];
            reset();
        }

        private void reset() {
            count = 0;
            minX = Float.MAX_VALUE;
            minY = Float.MAX_VALUE;
            maxX = -Float.MAX_VALUE;
            maxY = -Float.MAX_VALUE;
        }

        public boolean isEmpty() {
            return count == 0;
        }

        private Builder vertex(float x, float y, float u, float v, int color) {
            if (count * 2 + 2 > positions.length) {
                positions = Arrays.copyOf(positions, positions.length * 2);
                colors = Arrays.copyOf(colors, colors.length * 2);
                if (texture != null) {
                    uvs = Arrays.copyOf(uvs, uvs.length * 2);
                }
            }
            positions[count * 2] = x;
            positions[count * 2 + 1] = y;
            if (texture != null) {
                uvs[count * 2] = u;
                uvs[count * 2 + 1] = v;
            }
            colors[count] = Paint.applyAlpha(color);
            count++;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            return this;
        }

        public Builder quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
            return texQuad(x0, y0, 0, 0, c0, x1, y1, 0, 0, c1, x2, y2, 0, 0, c2, x3, y3, 0, 0, c3);
        }

        /**
         * Emits a quad with the winding the GUI pipelines expect (the same as vanilla's fills and blits),
         * reversing it if needed: back faces are culled, so shapes built either way round still show.
         */
        private Builder texQuad(float x0, float y0, float u0, float v0, int c0, float x1, float y1, float u1, float v1, int c1,
                                float x2, float y2, float u2, float v2, int c2, float x3, float y3, float u3, float v3, int c3) {
            float area = (x0 * y1 - x1 * y0) + (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y0 - x0 * y3);
            vertex(x0, y0, u0, v0, c0);
            if (area > 0.0F) {
                vertex(x3, y3, u3, v3, c3);
                vertex(x2, y2, u2, v2, c2);
                return vertex(x1, y1, u1, v1, c1);
            }
            vertex(x1, y1, u1, v1, c1);
            vertex(x2, y2, u2, v2, c2);
            return vertex(x3, y3, u3, v3, c3);
        }

        public Builder quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
            return quad(x0, y0, color, x1, y1, color, x2, y2, color, x3, y3, color);
        }

        public Builder tri(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2) {
            return quad(x0, y0, c0, x1, y1, c1, x2, y2, c2, x2, y2, c2);
        }

        /** A textured quad; u/v come from the caller. */
        public Builder texQuad(float x0, float y0, float u0, float v0, float x1, float y1, float u1, float v1,
                               float x2, float y2, float u2, float v2, float x3, float y3, float u3, float v3, int color) {
            return texQuad(x0, y0, u0, v0, color, x1, y1, u1, v1, color, x2, y2, u2, v2, color, x3, y3, u3, v3, color);
        }

        public Builder texTri(float x0, float y0, float u0, float v0, float x1, float y1, float u1, float v1,
                              float x2, float y2, float u2, float v2, int color) {
            return texQuad(x0, y0, u0, v0, x1, y1, u1, v1, x2, y2, u2, v2, x2, y2, u2, v2, color);
        }

        public void submit(GuiGraphicsExtractor graphics) {
            if (count == 0) {
                return;
            }
            Matrix3x2f pose = new Matrix3x2f(graphics.pose());
            ScreenRectangle scissor = graphics.peekScissorStack();
            int x0 = (int) Math.floor(minX);
            int y0 = (int) Math.floor(minY);
            ScreenRectangle box = new ScreenRectangle(x0, y0, Math.max(1, (int) Math.ceil(maxX) - x0), Math.max(1, (int) Math.ceil(maxY) - y0))
                    .transformMaxBounds(pose);
            ScreenRectangle bounds = scissor != null ? scissor.intersection(box) : box;
            if (bounds != null) {
                RenderPipeline pipeline = texture != null ? RenderPipelines.GUI_TEXTURED : RenderPipelines.GUI;
                graphics.submitGuiElementRenderState(new Mesh(pipeline, texture != null ? texture : TextureSetup.noTexture(), pose,
                        Arrays.copyOf(positions, count * 2), texture != null ? Arrays.copyOf(uvs, count * 2) : null,
                        Arrays.copyOf(colors, count), count, scissor, bounds));
            }
            reset();
        }
    }
}
