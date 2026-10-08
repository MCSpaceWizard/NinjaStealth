package com.mcspacewizard.emergentstealth.client.ui.widget;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;

/** A paper card holding a column of children (design doc 31 §2 Widgets). */
public class UiPanel extends UiFlex {
    private String paperToken = SumiTheme.PAPER;

    public UiPanel() {
        super(Axis.COLUMN);
    }

    public UiPanel paper(String token) {
        this.paperToken = token;
        return this;
    }

    @Override
    protected void draw(UiContext ctx) {
        Paint.panel(ctx.graphics(), x, y, width, height, ctx.theme().color(paperToken));
    }
}
