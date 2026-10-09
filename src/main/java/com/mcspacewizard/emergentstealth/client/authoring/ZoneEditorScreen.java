package com.mcspacewizard.emergentstealth.client.authoring;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.authoring.Zone;
import com.mcspacewizard.emergentstealth.authoring.ZonePayloads;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.client.ui.UiScreen;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiButton;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiCycle;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiDivider;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiFlex;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiLabel;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiPanel;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiSlider;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiTextField;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiToggle;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.UiLayout;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The zone panel of the Surveyor's Rope (design doc 32 §2): name, access rule and hours of the rope's zone, plus
 * removing its newest box or the whole zone. Everything is sent to the server, which checks it.
 */
public final class ZoneEditorScreen extends UiScreen {
    private static final int ROW_H = 20;
    private static final int LABEL_W = 70;

    private final Zone zone;
    // Screen state: build() runs again on resize.
    private String name;
    private Zone.Access access;
    private boolean timed;
    private int from;
    private int to;
    private @Nullable UiFlex hoursRow;

    public ZoneEditorScreen(Zone zone) {
        super(Component.translatable("ui.emergentstealth.zone.title"));
        this.zone = zone;
        this.name = zone.name();
        this.access = zone.access();
        this.timed = zone.hours().isPresent();
        this.from = zone.hours().map(Zone.Hours::from).orElse(18);
        this.to = zone.hours().map(Zone.Hours::to).orElse(6);
    }

    @Override
    protected UiNode build() {
        int pad = Sumi.metric(SumiTheme.PADDING);
        int panelW = Math.min(300, width - 20);
        int panelH = Math.min(214, height - 20);

        UiPanel panel = new UiPanel();
        panel.padding(UiLayout.Insets.of(pad + 2, pad)).gap(4);
        panel.add(new UiLabel(Component.translatable("ui.emergentstealth.zone.title")).scale(1.5F)).size(-1, 14);
        panel.add(new UiLabel(this::summary).color(SumiTheme.TEXT_MUTED)).size(-1, 11);
        panel.add(new UiDivider(3203L).thickness(3.0F)).size(-1, 6);

        UiFlex nameRow = row(panel, "ui.emergentstealth.zone.name");
        UiTextField field = nameRow.add(new UiTextField(Component.translatable("ui.emergentstealth.zone.name")));
        field.maxLength(64);
        field.setText(name);
        field.onChange(text -> name = text);
        field.onSubmit(text -> save());
        field.flex(1);

        UiFlex accessRow = row(panel, "ui.emergentstealth.zone.access");
        accessRow.add(new UiCycle<>(List.of(Zone.Access.values()), () -> access, value -> access = value,
                value -> Component.translatable("ui.emergentstealth.zone.access." + value.getSerializedName()), true)).flex(1)
                .tooltip(() -> List.of(Component.translatable("ui.emergentstealth.zone.access." + access.getSerializedName()),
                        Component.translatable("ui.emergentstealth.zone.access." + access.getSerializedName() + ".tip")));

        UiFlex timedRow = row(panel, "ui.emergentstealth.zone.timed");
        timedRow.add(new UiToggle(() -> timed, value -> {
            timed = value;
            if (hoursRow != null) {
                hoursRow.setVisible(value);
                relayout();
            }
        }, true)).tooltip(Component.translatable("ui.emergentstealth.zone.timed.tip"));
        timedRow.add(new UiLabel(this::hoursText).color(SumiTheme.TEXT_MUTED)).flex(1);

        hoursRow = panel.add(UiFlex.row().gap(6).center());
        hoursRow.add(new UiLabel(Component.translatable("ui.emergentstealth.zone.from")).color(SumiTheme.TEXT_MUTED)).size(30, -1);
        hoursRow.add(new UiSlider(() -> from, v -> from = (int) v, 0, 24, true, v -> (int) v + ":00", true)).flex(1);
        hoursRow.add(new UiLabel(Component.translatable("ui.emergentstealth.zone.to")).color(SumiTheme.TEXT_MUTED)).size(20, -1);
        hoursRow.add(new UiSlider(() -> to, v -> to = (int) v, 0, 24, true, v -> (int) v + ":00", true)).flex(1);
        hoursRow.size(-1, ROW_H);
        hoursRow.setVisible(timed);

        panel.add(new UiNode() {}).flex(1);
        panel.add(new UiDivider(78L).thickness(1.5F).color(SumiTheme.INK_FAINT)).size(-1, 5);
        UiFlex footer = panel.add(UiFlex.row().gap(6).center());
        footer.add(new UiButton(Component.translatable("ui.emergentstealth.zone.delete"), UiButton.Style.GHOST,
                () -> send(ZonePayloads.Action.DELETE))).size(56, 20);
        footer.add(new UiButton(Component.translatable("ui.emergentstealth.zone.remove_box"), UiButton.Style.SECONDARY,
                () -> send(ZonePayloads.Action.REMOVE_LAST_BOX))
                .enabledWhen(() -> zone.boxes().size() > 1)
                .tooltip(Component.translatable("ui.emergentstealth.zone.remove_box.tip"))).size(76, 20);
        footer.add(new UiNode() {}).flex(1);
        UiButton done = footer.add(new UiButton(Component.translatable("ui.emergentstealth.zone.save"), UiButton.Style.PRIMARY, this::save));
        done.size(64, 20);
        footer.size(-1, 22);

        UiNode root = new UiNode() {
            @Override
            protected void layoutChildren() {
                panel.layout(x + (width - panelW) / 2, y + (height - panelH) / 2, panelW, panelH);
            }
        };
        root.add(panel);
        setFocus(field);
        return root;
    }

    private static UiFlex row(UiPanel panel, String labelKey) {
        UiFlex row = panel.add(UiFlex.row().gap(6).center());
        row.add(new UiLabel(Component.translatable(labelKey)).color(SumiTheme.TEXT_MUTED)).size(LABEL_W, -1);
        row.size(-1, ROW_H);
        return row;
    }

    private Component summary() {
        BoundingBox bounds = zone.bounds();
        return Component.translatable("ui.emergentstealth.zone.summary", zone.boxes().size(),
                bounds.getXSpan() + "x" + bounds.getYSpan() + "x" + bounds.getZSpan());
    }

    private Component hoursText() {
        return timed ? Component.translatable("ui.emergentstealth.zone.hours", from, to)
                : Component.translatable("ui.emergentstealth.zone.always");
    }

    private void save() {
        send(ZonePayloads.Action.SAVE);
    }

    private void send(ZonePayloads.Action action) {
        Optional<Zone.Hours> hours = timed ? Optional.of(new Zone.Hours(from, to)) : Optional.empty();
        ClientPacketDistributor.sendToServer(new ZonePayloads.Edit(zone.name(), action, name.trim(), access, hours));
        onClose();
    }
}
