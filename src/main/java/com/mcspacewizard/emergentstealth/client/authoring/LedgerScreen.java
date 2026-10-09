package com.mcspacewizard.emergentstealth.client.authoring;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.authoring.LedgerPayloads;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.client.ui.UiScreen;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiButton;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiDivider;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiFlex;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiLabel;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiPanel;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiScroll;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiTextField;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.UiLayout;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The Compound Ledger panel (design doc 32 §3). Without a draft it names and starts one; with a draft it lists the
 * draft's zones, routes and spawns beside nearby rope zones and baton routes, to add or take out, and saves or
 * discards it. Every action goes to the server, which answers with the new state.
 */
public final class LedgerScreen extends UiScreen {
    private static final int ROW_H = 18;

    private LedgerPayloads.State state;
    /** Where the ledger was used: a new draft starts there (or on the structure placed there). */
    private final BlockPos usedAt;
    // Screen state: build() runs again on resize and on every new state.
    private String name;
    private boolean discardArmed;

    public LedgerScreen(LedgerPayloads.State state) {
        super(Component.translatable("ui.emergentstealth.ledger.title"));
        this.state = state;
        this.usedAt = state.origin();
        this.name = suggestedName(state);
    }

    /** A new draft's name suggested from the structure it starts from. */
    private static String suggestedName(LedgerPayloads.State state) {
        String template = state.startFrom();
        return template.isEmpty() ? "" : template.substring(template.lastIndexOf('/') + 1).replace(':', '_');
    }

    public static void handleState(LedgerPayloads.State payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof LedgerScreen screen) {
                screen.refresh(payload);
            } else if (payload.open()) {
                minecraft.setScreen(new LedgerScreen(payload));
            }
        });
    }

    private void refresh(LedgerPayloads.State next) {
        state = next;
        discardArmed = false;
        if (!next.hasDraft() && name.isBlank()) {
            name = suggestedName(next);
        }
        rebuildWidgets();
    }

    @Override
    protected UiNode build() {
        int pad = Sumi.metric(SumiTheme.PADDING);
        int panelW = Math.min(320, width - 20);
        int panelH = Math.min(state.hasDraft() ? 280 : 150, height - 20);

        UiPanel panel = new UiPanel();
        panel.padding(UiLayout.Insets.of(pad + 2, pad)).gap(4);
        panel.add(new UiLabel(Component.translatable("ui.emergentstealth.ledger.title")).scale(1.5F)).size(-1, 14);
        panel.add(new UiLabel(summary()).color(SumiTheme.TEXT_MUTED)).size(-1, 11);
        panel.add(new UiDivider(5102L).thickness(3.0F)).size(-1, 6);

        UiNode focus = state.hasDraft() ? buildDraft(panel) : buildStart(panel);

        UiNode root = new UiNode() {
            @Override
            protected void layoutChildren() {
                panel.layout(x + (width - panelW) / 2, y + (height - panelH) / 2, panelW, panelH);
            }
        };
        root.add(panel);
        setFocus(focus);
        return root;
    }

    private Component summary() {
        BlockPos o = state.origin();
        String at = o.getX() + " " + o.getY() + " " + o.getZ();
        if (state.hasDraft()) {
            return Component.translatable("ui.emergentstealth.ledger.summary", shortName(state.draft()), at, state.structures());
        }
        return state.startFrom().isEmpty() ? Component.translatable("ui.emergentstealth.ledger.start_at", at)
                : Component.translatable("ui.emergentstealth.ledger.start_from", shortName(state.startFrom()));
    }

    /** Our own ids by path, others in full. */
    private static String shortName(String id) {
        String ours = EmergentStealth.MODID + ":";
        return id.startsWith(ours) ? id.substring(ours.length()) : id;
    }

    /** No draft: a name and Start. */
    private UiNode buildStart(UiPanel panel) {
        UiFlex nameRow = panel.add(UiFlex.row().gap(6).center());
        nameRow.add(new UiLabel(Component.translatable("ui.emergentstealth.ledger.name")).color(SumiTheme.TEXT_MUTED)).size(50, -1);
        UiTextField field = nameRow.add(new UiTextField(Component.translatable("ui.emergentstealth.ledger.name.placeholder")));
        field.maxLength(128);
        field.setText(name);
        field.onChange(text -> name = text);
        field.onSubmit(text -> start());
        field.flex(1);
        nameRow.size(-1, 20);
        panel.add(new UiLabel(Component.translatable("ui.emergentstealth.ledger.start_hint")).color(SumiTheme.TEXT_MUTED).wrap()).flex(1);

        UiFlex footer = footer(panel);
        footer.add(new UiButton(Component.translatable("gui.cancel"), UiButton.Style.GHOST, this::onClose)).size(56, 20);
        footer.add(new UiNode() {}).flex(1);
        footer.add(new UiButton(Component.translatable("ui.emergentstealth.ledger.start"), UiButton.Style.PRIMARY, this::start)
                .enabledWhen(() -> !name.isBlank())).size(64, 20);
        return field;
    }

    /** A draft: its zones, routes and spawns, then Save, Discard and Close. */
    private UiNode buildDraft(UiPanel panel) {
        UiFlex list = UiFlex.column().gap(2);
        section(list, "ui.emergentstealth.ledger.zones", state.zones().isEmpty(), "ui.emergentstealth.ledger.none_near");
        for (LedgerPayloads.Entry entry : state.zones()) {
            entryRow(list, entry, LedgerPayloads.Kind.TOGGLE_ZONE, Component.literal(entry.detail()));
        }
        section(list, "ui.emergentstealth.ledger.routes", state.routes().isEmpty(), "ui.emergentstealth.ledger.none_near");
        for (LedgerPayloads.Entry entry : state.routes()) {
            entryRow(list, entry, LedgerPayloads.Kind.TOGGLE_ROUTE, Component.translatable("ui.emergentstealth.ledger.waypoints", entry.detail()));
        }
        section(list, "ui.emergentstealth.ledger.spawns", state.spawns().isEmpty(), "ui.emergentstealth.ledger.no_spawns");
        for (int i = 0; i < state.spawns().size(); i++) {
            int index = i;
            UiFlex row = list.add(UiFlex.row().gap(6).center());
            row.add(new UiLabel(Component.literal(state.spawns().get(i)))).flex(1);
            row.add(new UiButton(Component.translatable("ui.emergentstealth.ledger.take_out"), UiButton.Style.GHOST,
                    () -> send(LedgerPayloads.Kind.REMOVE_SPAWN, "", index))
                    .tooltip(Component.translatable("ui.emergentstealth.ledger.take_out_spawn.tip"))).size(60, 16);
            row.size(-1, ROW_H);
        }
        UiScroll scroll = panel.add(new UiScroll(list));
        scroll.flex(1);

        UiFlex footer = footer(panel);
        footer.add(new UiButton(() -> Component.translatable(discardArmed ? "ui.emergentstealth.ledger.discard.confirm" : "ui.emergentstealth.ledger.discard"),
                UiButton.Style.GHOST, this::discard).tooltip(Component.translatable("ui.emergentstealth.ledger.discard.tip"))).size(76, 20);
        footer.add(new UiNode() {}).flex(1);
        footer.add(new UiButton(Component.translatable("gui.done"), UiButton.Style.SECONDARY, this::onClose)).size(50, 20);
        UiButton save = footer.add(new UiButton(Component.translatable("ui.emergentstealth.ledger.save"), UiButton.Style.PRIMARY,
                () -> send(LedgerPayloads.Kind.SAVE, "", 0)));
        save.tooltip(Component.translatable("ui.emergentstealth.ledger.save.tip")).size(56, 20);
        return save;
    }

    private static void section(UiFlex list, String key, boolean empty, String emptyKey) {
        UiFlex row = list.add(UiFlex.row().gap(6).center());
        row.add(new UiLabel(Component.translatable(key))).size(70, -1);
        if (empty) {
            row.add(new UiLabel(Component.translatable(emptyKey)).color(SumiTheme.TEXT_MUTED)).flex(1);
        }
        row.size(-1, ROW_H);
    }

    private void entryRow(UiFlex list, LedgerPayloads.Entry entry, LedgerPayloads.Kind kind, Component detail) {
        UiFlex row = list.add(UiFlex.row().gap(6).center());
        row.add(new UiLabel(Component.literal(entry.name()).withStyle(s -> s.withBold(entry.inDraft())))).flex(2);
        row.add(new UiLabel(detail).color(SumiTheme.TEXT_MUTED)).flex(1);
        boolean canAdd = entry.inWorld();
        UiButton button = row.add(new UiButton(Component.translatable(entry.inDraft() ? "ui.emergentstealth.ledger.take_out" : "ui.emergentstealth.ledger.add"),
                entry.inDraft() ? UiButton.Style.GHOST : UiButton.Style.SECONDARY, () -> send(kind, entry.name(), 0)));
        button.enabledWhen(() -> entry.inDraft() || canAdd);
        button.tooltip(Component.translatable(entry.inDraft()
                ? entry.inWorld() ? "ui.emergentstealth.ledger.linked.tip" : "ui.emergentstealth.ledger.unlinked.tip"
                : "ui.emergentstealth.ledger.add.tip")).size(60, 16);
        row.size(-1, ROW_H);
    }

    private static UiFlex footer(UiPanel panel) {
        panel.add(new UiDivider(318L).thickness(1.5F).color(SumiTheme.INK_FAINT)).size(-1, 5);
        UiFlex footer = panel.add(UiFlex.row().gap(6).center());
        footer.size(-1, 22);
        return footer;
    }

    private void start() {
        if (!name.isBlank()) {
            send(LedgerPayloads.Kind.START, name.trim(), 0);
        }
    }

    private void discard() {
        if (!discardArmed) {
            discardArmed = true;
            return;
        }
        send(LedgerPayloads.Kind.DISCARD, "", 0);
    }

    private void send(LedgerPayloads.Kind kind, @Nullable String text, int index) {
        ClientPacketDistributor.sendToServer(new LedgerPayloads.Action(kind, text == null ? "" : text, index, usedAt));
    }
}
