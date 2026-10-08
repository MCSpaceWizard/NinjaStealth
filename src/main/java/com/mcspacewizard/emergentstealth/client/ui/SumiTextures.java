package com.mcspacewizard.emergentstealth.client.ui;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ui.PaperNoise;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * Sumi's procedural textures (design doc 31 §0), generated in code from the theme's texture tokens:
 * <ul>
 *   <li><b>paper</b>: near-white washi grain with soft clouds and fibres, tinted per panel by vertex colour;</li>
 *   <li><b>ink</b>: white with alpha speckle and horizontal drag marks, so ink fills look brushed.</li>
 * </ul>
 * Both tile seamlessly. A resource pack can replace either by shipping
 * {@code textures/gui/sumi/paper.png} or {@code ink.png}; Sumi then uses that instead.
 */
public final class SumiTextures {
    private SumiTextures() {}

    public static final int PAPER_SIZE = 256;
    public static final int INK_SIZE = 128;
    private static final Identifier PAPER_ID = EmergentStealth.id("sumi/paper_generated");
    private static final Identifier INK_ID = EmergentStealth.id("sumi/ink_generated");
    private static final Identifier PAPER_OVERRIDE = EmergentStealth.id("textures/gui/sumi/paper.png");
    private static final Identifier INK_OVERRIDE = EmergentStealth.id("textures/gui/sumi/ink.png");

    /** A texture ready to draw: its setup and size in texels. */
    public record Tex(TextureSetup setup, int width, int height) {}

    private static @Nullable Tex paper;
    private static @Nullable Tex ink;
    private static @Nullable String builtFor;

    public static Tex paper() {
        ensure();
        return paper;
    }

    public static Tex ink() {
        ensure();
        return ink;
    }

    /** Drops the textures so they're rebuilt (after a theme or resource reload). */
    public static void invalidate() {
        builtFor = null;
    }

    private static void ensure() {
        SumiTheme theme = Sumi.theme();
        String key = theme.texture().toString();
        if (key.equals(builtFor) && paper != null && ink != null) {
            return;
        }
        builtFor = key;
        long seed = (long) theme.textureF(SumiTheme.GRAIN_SEED);
        paper = load(PAPER_OVERRIDE, PAPER_ID, () -> generatePaper(seed, theme.textureF(SumiTheme.GRAIN_STRENGTH),
                theme.textureF(SumiTheme.FIBER_DENSITY)), PAPER_SIZE);
        ink = load(INK_OVERRIDE, INK_ID, () -> generateInk(seed + 1, theme.textureF(SumiTheme.INK_SPECKLE)), INK_SIZE);
    }

    private static Tex load(Identifier override, Identifier generatedId, java.util.function.Supplier<NativeImage> generator, int size) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getResourceManager().getResource(override).isPresent()) {
            AbstractTexture texture = minecraft.getTextureManager().getTexture(override);
            return new Tex(TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler()),
                    texture.getTexture().getWidth(0), texture.getTexture().getHeight(0));
        }
        minecraft.getTextureManager().release(generatedId);
        DynamicTexture texture = new DynamicTexture(generatedId::toString, generator.get());
        minecraft.getTextureManager().register(generatedId, texture);
        return new Tex(TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler()), size, size);
    }

    /** Washi: soft clouds of density, fine tooth, and a scatter of fibres. Greyscale, opaque. */
    static NativeImage generatePaper(long seed, float strength, float fiberDensity) {
        int n = PAPER_SIZE;
        float[] value = new float[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float clouds = PaperNoise.fbm(x, y, 64, n, seed, 4);
                float tooth = PaperNoise.hash(x, y, seed ^ 0x5DEECE66DL);
                value[y * n + x] = 1.0F - strength * (0.65F * clouds + 0.35F * tooth);
            }
        }
        // Fibres: short wandering strands, a little lighter or darker than the sheet.
        RandomSource random = RandomSource.create(seed);
        int fibres = Math.round(260 * fiberDensity);
        for (int i = 0; i < fibres; i++) {
            float x = random.nextFloat() * n;
            float y = random.nextFloat() * n;
            float angle = random.nextFloat() * (float) Math.PI * 2.0F;
            int length = 6 + random.nextInt(18);
            float shade = (random.nextBoolean() ? -1.0F : 0.6F) * strength * (0.35F + random.nextFloat() * 0.5F);
            for (int s = 0; s < length; s++) {
                angle += (random.nextFloat() - 0.5F) * 0.5F;
                x += (float) Math.cos(angle);
                y += (float) Math.sin(angle);
                int px = Math.floorMod((int) x, n);
                int py = Math.floorMod((int) y, n);
                value[py * n + px] = Math.clamp(value[py * n + px] + shade, 0.0F, 1.0F);
            }
        }
        NativeImage image = new NativeImage(n, n, false);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int v = Math.round(Math.clamp(value[y * n + x], 0.0F, 1.0F) * 255.0F);
                image.setPixel(x, y, 0xFF000000 | v << 16 | v << 8 | v);
            }
        }
        return image;
    }

    /** Ink: white with alpha that thins in speckles and horizontal drag marks. */
    static NativeImage generateInk(long seed, float speckle) {
        int n = INK_SIZE;
        NativeImage image = new NativeImage(n, n, false);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                // Stretched noise: brush drag runs horizontally.
                float drag = PaperNoise.fbm(x, y * 6.0F, 32, n, seed, 3);
                float dots = PaperNoise.hash(x, y, seed * 31L);
                float thin = speckle * (0.7F * Math.max(0.0F, drag - 0.35F) * 1.6F + 0.3F * dots);
                int alpha = Math.round(Math.clamp(1.0F - thin, 0.0F, 1.0F) * 255.0F);
                image.setPixel(x, y, alpha << 24 | 0xFFFFFF);
            }
        }
        return image;
    }
}
