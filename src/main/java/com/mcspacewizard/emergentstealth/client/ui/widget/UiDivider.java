package com.mcspacewizard.emergentstealth.client.ui.widget;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;

/** A horizontal sumi brush stroke across the node (design doc 31 §2 Paint). */
public class UiDivider extends UiNode {
    private final long seed;
    private float thickness = 2.5F;
    private String colorToken = SumiTheme.INK;

    public UiDivider(long seed) {
        this.seed = seed;
    }

    public UiDivider thickness(float thickness) {
        this.thickness = thickness;
        return this;
    }

    public UiDivider color(String token) {
        this.colorToken = token;
        return this;
    }

    @Override
    public int preferredHeight(int forWidth) {
        return prefHeight >= 0 ? prefHeight : 6;
    }

    @Override
    protected void draw(UiContext ctx) {
        float cy = y + height / 2.0F;
        Paint.brushLine(ctx.graphics(), x, cy, x + width, cy, thickness, ctx.theme().color(colorToken), seed);
    }
}
