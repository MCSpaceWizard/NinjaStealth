package com.mcspacewizard.emergentstealth.client.ui.screen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.client.ui.UiScreen;
import com.mcspacewizard.emergentstealth.client.ui.widget.SumiSounds;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiButton;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiDivider;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiFlex;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiLabel;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiPager;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiPanel;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiScroll;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiTabBar;
import com.mcspacewizard.emergentstealth.config.ESConfig;
import com.mcspacewizard.emergentstealth.ui.ConfigMapping;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.UiLayout;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mod's config screen in Sumi (design doc 31 §3.1). Built from our {@link ModConfigSpec}s: each
 * section is a tab in the sidebar (Client, then Server), and the tabs are swipeable pages of inertial
 * scrolling rows. Server values are read-only unless you're in your own unpublished singleplayer world.
 * Changes apply at once and are saved when the screen closes.
 */
public final class SumiConfigScreen extends UiScreen {
    private record Page(ModConfigSpec spec, ConfigMapping.Section section, boolean editable, boolean loaded) {}

    private final @Nullable Screen parent;
    private final @Nullable ModContainer container;
    private final Set<ModConfigSpec> dirty = new HashSet<>();
    private final List<Page> pages = new ArrayList<>();
    private final List<List<ConfigRow>> rows = new ArrayList<>();
    private int page;
    private long pageShownAt;
    private @Nullable UiPager pager;

    public SumiConfigScreen(@Nullable Screen parent, @Nullable ModContainer container) {
        super(Component.translatable("emergentstealth.configuration.title"));
        this.parent = parent;
        this.container = container;
    }

    private boolean serverEditable() {
        return minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null
                && !minecraft.getSingleplayerServer().isPublished();
    }

    private void collectPages() {
        pages.clear();
        for (ConfigMapping.Section section : ConfigMapping.sections(ESConfig.CLIENT_SPEC)) {
            pages.add(new Page(ESConfig.CLIENT_SPEC, section, ESConfig.CLIENT_SPEC.isLoaded(), ESConfig.CLIENT_SPEC.isLoaded()));
        }
        boolean serverLoaded = ESConfig.SERVER_SPEC.isLoaded();
        for (ConfigMapping.Section section : ConfigMapping.sections(ESConfig.SERVER_SPEC)) {
            pages.add(new Page(ESConfig.SERVER_SPEC, section, serverLoaded && serverEditable(), serverLoaded));
        }
    }

    private static Component sectionName(ConfigMapping.Section section) {
        return I18n.exists(section.langKey()) ? Component.translatable(section.langKey()) : Component.literal(ConfigRow.prettify(section.name()));
    }

    @Override
    protected UiNode build() {
        collectPages();
        rows.clear();
        SumiTheme theme = Sumi.theme();
        int pad = theme.metric(SumiTheme.PADDING);
        int panelW = Math.min(480, width - 20);
        int panelH = Math.min(320, height - 20);

        UiPanel panel = new UiPanel();
        panel.padding(UiLayout.Insets.of(pad + 2, pad)).gap(4);

        // Header: seal, title, subtitle.
        UiFlex header = panel.add(UiFlex.row().gap(8).center());
        header.add(new Seal()).size(18, 18);
        header.add(new UiLabel(Component.translatable("ui.emergentstealth.config.title")).scale(1.5F)).size(-1, 14);
        header.add(new UiLabel(Component.translatable("ui.emergentstealth.config.subtitle")).color(SumiTheme.TEXT_MUTED)).flex(1);
        header.size(-1, 20);
        panel.add(new UiDivider(4242L).thickness(3.0F)).size(-1, 6);

        // Body: sidebar tabs and swipeable pages.
        UiFlex body = panel.add(UiFlex.row().gap(6)).flex(1);
        UiPager pages = new UiPager(this.pages.size(), Math.min(page, Math.max(0, this.pages.size() - 1)));
        this.pager = pages;
        pages.onChange(i -> {
            page = i;
            pageShownAt = Sumi.now();
            SumiSounds.page();
        });
        UiTabBar tabs = new UiTabBar(UiTabBar.Orientation.VERTICAL, pages::position, pages::select);
        boolean serverHeading = false;
        tabs.heading(Component.translatable("ui.emergentstealth.config.client"));
        for (int i = 0; i < this.pages.size(); i++) {
            Page p = this.pages.get(i);
            if (p.spec == ESConfig.SERVER_SPEC && !serverHeading) {
                tabs.heading(Component.translatable("ui.emergentstealth.config.server"));
                serverHeading = true;
            }
            tabs.tab(sectionName(p.section), i);
        }
        int sidebar = panelW < 340 ? 84 : theme.metric(SumiTheme.SIDEBAR_WIDTH);
        if (panelH < 260) {
            tabs.rowHeight(16);
        }
        // The sidebar scrolls too, for small windows and big GUI scales.
        body.add(new UiScroll(tabs)).size(sidebar, -1);
        body.add(new VerticalRule()).size(4, -1);
        body.add(pages).flex(1);
        for (int i = 0; i < this.pages.size(); i++) {
            pages.add(buildPage(this.pages.get(i)));
        }

        // Footer.
        panel.add(new UiDivider(77L).thickness(1.5F).color(SumiTheme.INK_FAINT)).size(-1, 5);
        UiFlex footer = panel.add(UiFlex.row().gap(6).center());
        footer.add(new UiLabel(this::footerHint).color(SumiTheme.TEXT_MUTED)).flex(1);
        if (container != null) {
            footer.add(new UiButton(Component.translatable("ui.emergentstealth.config.classic"), UiButton.Style.GHOST,
                    () -> minecraft.setScreen(new ConfigurationScreen(container, this))));
        }
        footer.add(new UiButton(Component.translatable("ui.emergentstealth.config.reset"), UiButton.Style.SECONDARY, this::resetPage)
                .enabledWhen(() -> currentPage() != null && currentPage().editable));
        footer.add(new UiButton(Component.translatable("gui.done"), UiButton.Style.PRIMARY, this::onClose)).size(64, 20);
        footer.size(-1, 22);

        UiNode centred = new UiNode() {
            @Override
            protected void layoutChildren() {
                panel.layout(x + (width - panelW) / 2, y + (height - panelH) / 2, panelW, panelH);
            }
        };
        centred.add(panel);
        return centred;
    }

    private @Nullable Page currentPage() {
        return pager == null || pages.isEmpty() ? null : pages.get(Math.clamp(pager.index(), 0, pages.size() - 1));
    }

    private Component footerHint() {
        Page p = currentPage();
        if (p == null) {
            return Component.empty();
        }
        if (!p.loaded) {
            return Component.translatable("ui.emergentstealth.config.not_loaded");
        }
        if (p.spec == ESConfig.SERVER_SPEC && !p.editable) {
            return Component.translatable("ui.emergentstealth.config.server_read_only");
        }
        return Component.translatable("ui.emergentstealth.config.saves_on_close");
    }

    private UiNode buildPage(Page p) {
        List<ConfigRow> pageRows = new ArrayList<>();
        rows.add(pageRows);
        UiFlex column = UiFlex.column().gap(1).padding(UiLayout.Insets.of(0, 2));
        UiLabel title = column.add(new UiLabel(sectionName(p.section)).scale(1.25F).color(SumiTheme.TEXT));
        title.size(-1, 16);
        if (!p.loaded) {
            column.add(new UiLabel(Component.translatable("ui.emergentstealth.config.not_loaded")).color(SumiTheme.TEXT_MUTED).wrap());
            return new UiScroll(column);
        }
        int i = 0;
        for (ConfigMapping.Entry entry : p.section.entries()) {
            ConfigRow row = column.add(new ConfigRow(entry, p.editable, () -> dirty.add(p.spec), i++, () -> pageShownAt));
            pageRows.add(row);
        }
        return new UiScroll(column);
    }

    private void resetPage() {
        if (pager == null || pager.index() >= rows.size()) {
            return;
        }
        for (ConfigRow row : rows.get(pager.index())) {
            row.reset();
        }
    }

    @Override
    protected void init() {
        if (pageShownAt == 0) {
            pageShownAt = Sumi.now() + 80L;
        }
        super.init();
    }

    @Override
    protected boolean screenKey(KeyEvent event) {
        if (pager == null) {
            return false;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN && event.hasControlDown() || event.key() == GLFW.GLFW_KEY_RIGHT_BRACKET) {
            pager.select(pager.index() + 1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP && event.hasControlDown() || event.key() == GLFW.GLFW_KEY_LEFT_BRACKET) {
            pager.select(pager.index() - 1);
            return true;
        }
        return false;
    }

    @Override
    public void removed() {
        for (ModConfigSpec spec : dirty) {
            if (spec.isLoaded()) {
                spec.save();
            }
        }
        dirty.clear();
        super.removed();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    /** The mod's seal: a vermilion hanko with the shinobi character. */
    private static final class Seal extends UiNode {
        @Override
        protected void draw(UiContext ctx) {
            SumiTheme theme = ctx.theme();
            float cx = x + width / 2.0F;
            float cy = y + height / 2.0F;
            float r = Math.min(width, height) / 2.0F;
            Paint.inkPolygon(ctx.graphics(), Paint.cutRectPoints(cx - r, cy - r, r * 2, r * 2, 2.5F), theme.color(SumiTheme.LACQUER));
            Paint.outline(ctx.graphics(), Paint.cutRectPoints(cx - r + 2, cy - r + 2, r * 2 - 4, r * 2 - 4, 1.5F), 1.0F,
                    Paint.fade(theme.color(SumiTheme.PAPER), 0.6F));
            Paint.textCentered(ctx.graphics(), ctx.font(), Component.literal("忍"), cx + 0.5F, cy - 4, theme.color(SumiTheme.PAPER));
        }
    }

    /** A thin vertical brush stroke between the sidebar and the pages. */
    private static final class VerticalRule extends UiNode {
        @Override
        protected void draw(UiContext ctx) {
            Paint.brushLine(ctx.graphics(), x + width / 2.0F, y + 2, x + width / 2.0F, y + height - 2, 1.5F,
                    Paint.fade(ctx.theme().color(SumiTheme.INK_FAINT), 0.7F), 31L);
        }
    }
}
