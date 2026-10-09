package com.mcspacewizard.emergentstealth.client.authoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.authoring.StructurePayloads;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.client.ui.UiScreen;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiButton;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiDivider;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiFlex;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiLabel;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiListRow;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiPanel;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiScroll;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiTextField;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.UiLayout;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The structure browser (design doc 32 §1), opened by the Surveyor's Plan or {@code /es structure browse}: a
 * searchable list of every compound and structure, grouped by folder (compounds first), and the selected one's
 * size and main blocks (and what a compound brings). "Preview" closes the screen and hands it to the ghost
 * preview for placing; a compound places as a whole, with its guards, routes and zones.
 */
public final class StructureBrowserScreen extends UiScreen {
    private static ClientStructures.@Nullable Key lastSelected;
    private static String lastFilter = "";
    /** How many of the commonest blocks the details list (they have to fit beside the list at small GUI sizes). */
    private static final int MAIN_BLOCKS = 5;
    /** Fewer for a compound: what it brings takes two lines. */
    private static final int MAIN_BLOCKS_COMPOUND = 2;

    private ClientStructures.@Nullable Key selected = lastSelected;
    private String filter = lastFilter;
    private @Nullable UiFlex list;
    private @Nullable UiScroll scroll;
    /** Whether the selected structure's preview was here at the last layout (its text changes the heights). */
    private boolean laidOutLoaded;

    public StructureBrowserScreen() {
        super(Component.translatable("ui.emergentstealth.structures.title"));
    }

    @Override
    protected UiNode build() {
        SumiTheme theme = Sumi.theme();
        int pad = theme.metric(SumiTheme.PADDING);
        int panelW = Math.min(560, width - 20);
        int panelH = Math.min(340, height - 20);

        UiPanel panel = new UiPanel();
        panel.padding(UiLayout.Insets.of(pad + 2, pad)).gap(4);

        UiFlex header = panel.add(UiFlex.row().gap(8).center());
        header.add(new UiLabel(Component.translatable("ui.emergentstealth.structures.title")).scale(1.5F)).size(-1, 14);
        header.add(new UiLabel(() -> Component.translatable("ui.emergentstealth.structures.count_with_compounds", ClientStructures.ids().size(),
                ClientStructures.compounds().size()))
                .color(SumiTheme.TEXT_MUTED)).flex(1);
        header.size(-1, 20);
        panel.add(new UiDivider(5151L).thickness(3.0F)).size(-1, 6);

        UiFlex body = panel.add(UiFlex.row().gap(8)).flex(1);
        UiFlex left = body.add(UiFlex.column().gap(4));
        left.size(Math.min(220, panelW / 2 - 10), -1);
        UiTextField search = left.add(new UiTextField(Component.translatable("ui.emergentstealth.structures.search")));
        search.setText(filter);
        search.onChange(text -> {
            filter = text;
            lastFilter = text;
            fillList();
        });
        // Enter selects the first match; Enter again previews it.
        search.onSubmit(text -> {
            List<ClientStructures.Key> shown = filtered();
            if (shown.isEmpty()) {
                return;
            }
            if (shown.getFirst().equals(selected)) {
                startPreview();
            } else {
                select(shown.getFirst());
            }
        });
        list = UiFlex.column().gap(0);
        scroll = left.add(new UiScroll(list));
        scroll.flex(1);
        fillList();

        UiFlex right = body.add(UiFlex.column().gap(4)).flex(1);
        right.add(new UiLabel(this::titleText).scale(1.25F).color(SumiTheme.TEXT)).size(-1, 14);
        right.add(new UiLabel(this::folderText).color(SumiTheme.INK_SOFT)).size(-1, 11);
        right.add(new UiLabel(this::sizeText).color(SumiTheme.TEXT_MUTED)).size(-1, 11);
        right.add(new UiLabel(this::blocksText).color(SumiTheme.TEXT_MUTED).wrap()).flex(1);
        UiFlex actions = right.add(UiFlex.row().gap(6));
        actions.add(new UiButton(Component.translatable("ui.emergentstealth.structures.preview"), UiButton.Style.PRIMARY, this::startPreview)
                .enabledWhen(() -> selected != null && ClientStructures.preview(selected) != null)
                .tooltip(Component.translatable("ui.emergentstealth.structures.controls"))).size(96, 20);
        actions.add(new UiButton(Component.translatable("ui.emergentstealth.structures.undo"), UiButton.Style.SECONDARY,
                () -> ClientPacketDistributor.sendToServer(new StructurePayloads.Undo())));
        actions.size(-1, 22);

        panel.add(new UiDivider(78L).thickness(1.5F).color(SumiTheme.INK_FAINT)).size(-1, 5);
        UiFlex footer = panel.add(UiFlex.row().gap(6).center());
        footer.add(new UiLabel(Component.translatable("ui.emergentstealth.structures.hint")).color(SumiTheme.TEXT_MUTED)).flex(1);
        footer.add(new UiButton(Component.translatable("gui.done"), UiButton.Style.SECONDARY, this::onClose)).size(64, 20);
        footer.size(-1, 22);

        if (selected != null) {
            ClientStructures.request(selected);
        }
        UiNode centred = new UiNode() {
            @Override
            protected void layoutChildren() {
                panel.layout(x + (width - panelW) / 2, y + (height - panelH) / 2, panelW, panelH);
            }
        };
        centred.add(panel);
        setFocus(search);
        return centred;
    }

    @Override
    public void tick() {
        super.tick();
        boolean loaded = selected != null && ClientStructures.preview(selected) != null;
        if (selected != null && !loaded) {
            ClientStructures.request(selected); // again, if the server didn't answer (it may have refused)
        }
        if (loaded != laidOutLoaded) {
            laidOutLoaded = loaded;
            relayout();
        }
    }

    /** Compounds first, then structures, that match the search ("compound" matches every compound). */
    private List<ClientStructures.Key> filtered() {
        String needle = filter.toLowerCase(Locale.ROOT).trim();
        List<ClientStructures.Key> shown = new ArrayList<>();
        for (Identifier id : ClientStructures.compounds()) {
            if (needle.isEmpty() || id.toString().contains(needle) || "compound".contains(needle)) {
                shown.add(new ClientStructures.Key(id, true));
            }
        }
        for (Identifier id : ClientStructures.ids()) {
            if (needle.isEmpty() || id.toString().contains(needle)) {
                shown.add(new ClientStructures.Key(id, false));
            }
        }
        return shown;
    }

    /** "emergentstealth:edo/kofun" is listed under "emergentstealth:edo"; compounds under "Compounds · …". */
    private static Component folder(ClientStructures.Key key) {
        Identifier id = key.id();
        int slash = id.getPath().lastIndexOf('/');
        String folder = slash < 0 ? id.getNamespace() : id.getNamespace() + ":" + id.getPath().substring(0, slash);
        return key.compound() ? Component.translatable("ui.emergentstealth.structures.compound_folder", folder) : Component.literal(folder);
    }

    private static String leaf(ClientStructures.Key key) {
        return key.id().getPath().substring(key.id().getPath().lastIndexOf('/') + 1);
    }

    private void fillList() {
        if (list == null) {
            return;
        }
        list.clearChildren();
        Component lastFolder = null;
        for (ClientStructures.Key key : filtered()) {
            Component folder = folder(key);
            if (!folder.equals(lastFolder)) {
                list.add(new UiLabel(folder).color(key.compound() ? SumiTheme.LACQUER : SumiTheme.INK_SOFT)).size(-1, 13);
                lastFolder = folder;
            }
            list.add(new UiListRow(() -> Component.literal(leaf(key)), () -> key.equals(selected), () -> select(key))
                    .onActivate(this::startPreview));
        }
        if (filtered().isEmpty()) {
            list.add(new UiLabel(Component.translatable("ui.emergentstealth.structures.none")).color(SumiTheme.TEXT_MUTED));
        }
        relayout();
    }

    private void select(ClientStructures.Key key) {
        selected = key;
        lastSelected = key;
        ClientStructures.request(key);
    }

    private void startPreview() {
        ClientStructures.Preview preview = selected == null ? null : ClientStructures.preview(selected);
        if (preview != null) {
            GhostPreview.start(preview);
            onClose();
        }
    }

    private Component titleText() {
        return selected == null ? Component.translatable("ui.emergentstealth.structures.pick") : Component.literal(leaf(selected));
    }

    private Component folderText() {
        return selected == null ? Component.empty() : folder(selected);
    }

    private Component sizeText() {
        if (selected == null) {
            return Component.empty();
        }
        ClientStructures.Preview preview = ClientStructures.preview(selected);
        if (preview == null) {
            return Component.translatable("ui.emergentstealth.structures.loading");
        }
        return Component.translatable("ui.emergentstealth.structures.size", preview.size().getX(), preview.size().getY(),
                preview.size().getZ(), preview.solid());
    }

    private Component blocksText() {
        ClientStructures.Preview preview = selected == null ? null : ClientStructures.preview(selected);
        if (preview == null) {
            return Component.empty();
        }
        Component text = Component.empty();
        ClientStructures.CompoundInfo info = preview.info();
        if (info != null) {
            text = Component.translatable("ui.emergentstealth.structures.compound_info", info.modules(), info.npcs(), info.routes(), info.zones());
            if (info.missing() > 0) {
                text = text.copy().append("\n").append(Component.translatable("ui.emergentstealth.structures.compound_missing", info.missing()));
            }
            if (preview.commonest().isEmpty()) {
                return text;
            }
            text = text.copy().append("\n");
        } else if (preview.commonest().isEmpty()) {
            return text;
        }
        text = text.copy().append(Component.translatable("ui.emergentstealth.structures.main_blocks"));
        for (Map.Entry<Block, Integer> entry : preview.commonest().subList(0, Math.min(info != null ? MAIN_BLOCKS_COMPOUND : MAIN_BLOCKS, preview.commonest().size()))) {
            text = text.copy().append("\n  ").append(entry.getKey().getName()).append(" × " + entry.getValue());
        }
        return text;
    }
}
