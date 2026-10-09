package com.mcspacewizard.emergentstealth.client.authoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.routine.Schedule;
import com.mcspacewizard.emergentstealth.authoring.MusterPayloads;
import com.mcspacewizard.emergentstealth.authoring.MusterRoll;
import com.mcspacewizard.emergentstealth.client.ui.Sumi;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.client.ui.UiScreen;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiButton;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiCycle;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiDivider;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiFlex;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiLabel;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiPanel;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiScroll;
import com.mcspacewizard.emergentstealth.client.ui.widget.UiSlider;
import com.mcspacewizard.emergentstealth.data.Archetype;
import com.mcspacewizard.emergentstealth.registry.ESRegistries;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.UiLayout;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The Muster Roll panel (design doc 32 §4): archetype, behaviour tree, facing, count and a schedule of hour
 * windows, each a post on this spot, a patrol route or wandering. The server checks and carries out the request.
 */
public final class MusterRollScreen extends UiScreen {
    private static final int ROW_H = 20;
    private static final int LABEL_W = 64;
    private static final int[] WANDER_RADII = {4, 8, 16, 32};

    /** One schedule window as the panel edits it. */
    private static final class Window {
        int from;
        int to;
        Choice choice;

        Window(int from, int to, Choice choice) {
            this.from = from;
            this.to = to;
            this.choice = choice;
        }
    }

    /** What a window does: stand here, wander, or walk a route. */
    private sealed interface Choice permits PostHere, Wander, Route {}

    private record PostHere() implements Choice {}

    private record Wander(int radius) implements Choice {}

    private record Route(String name) implements Choice {}

    private final MusterPayloads.Open open;
    private final List<Choice> choices = new ArrayList<>();
    private final List<Optional<Identifier>> behaviours = new ArrayList<>();
    // Screen state: build() runs again on resize and when windows are added or removed.
    private Identifier archetype;
    private Optional<Identifier> behaviour = Optional.empty();
    private int facingStep;
    private int count = 1;
    private final List<Window> windows = new ArrayList<>();

    public MusterRollScreen(MusterPayloads.Open open) {
        super(Component.translatable("ui.emergentstealth.muster.title"));
        this.open = open;
        this.archetype = open.archetypes().contains(EmergentStealth.id("ashigaru")) || open.archetypes().isEmpty()
                ? EmergentStealth.id("ashigaru") : open.archetypes().getFirst();
        this.facingStep = Math.round(open.facing() / 45.0F) % 8;
        behaviours.add(Optional.empty());
        open.behaviours().forEach(id -> behaviours.add(Optional.of(id)));
        choices.add(new PostHere());
        for (int radius : WANDER_RADII) {
            choices.add(new Wander(radius));
        }
        open.routes().forEach(name -> choices.add(new Route(name)));
        windows.add(new Window(0, 0, new PostHere()));
    }

    public static void handleOpen(MusterPayloads.Open payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new MusterRollScreen(payload)));
    }

    @Override
    protected UiNode build() {
        int pad = Sumi.metric(SumiTheme.PADDING);
        int panelW = Math.min(360, width - 20);
        int panelH = Math.min(300, height - 20);

        UiPanel panel = new UiPanel();
        panel.padding(UiLayout.Insets.of(pad + 2, pad)).gap(4);
        panel.add(new UiLabel(Component.translatable("ui.emergentstealth.muster.title")).scale(1.5F)).size(-1, 14);
        panel.add(new UiLabel(this::summary).color(SumiTheme.TEXT_MUTED)).size(-1, 11);
        panel.add(new UiDivider(4411L).thickness(3.0F)).size(-1, 4);

        UiFlex archetypeRow = row(panel, "ui.emergentstealth.muster.archetype");
        UiCycle<Identifier> archetypes = archetypeRow.add(new UiCycle<>(open.archetypes().isEmpty() ? List.of(archetype) : open.archetypes(),
                () -> archetype, value -> archetype = value, value -> Component.literal(shortName(value)), true));
        archetypes.flex(1);
        archetypes.tooltip(() -> List.of(Component.literal(archetype.toString()), role()));

        UiFlex behaviourRow = row(panel, "ui.emergentstealth.muster.behaviour");
        behaviourRow.add(new UiCycle<>(behaviours, () -> behaviour, value -> behaviour = value,
                value -> value.map(id -> Component.literal(shortName(id))).orElseGet(() -> Component.translatable("ui.emergentstealth.muster.behaviour.default")),
                true)).flex(1)
                .tooltip(Component.translatable("ui.emergentstealth.muster.behaviour.tip"));

        UiFlex facingRow = row(panel, "ui.emergentstealth.muster.facing");
        facingRow.add(new UiSlider(() -> facingStep, v -> facingStep = (int) v, 0, 7, true,
                v -> Component.translatable("ui.emergentstealth.muster.facing." + (int) v).getString(), true)).flex(1);
        facingRow.add(new UiLabel(Component.translatable("ui.emergentstealth.muster.count")).color(SumiTheme.TEXT_MUTED)).size(36, -1);
        facingRow.add(new UiSlider(() -> count, v -> count = (int) v, 1, MusterRoll.MAX_COUNT, true, v -> String.valueOf((int) v), true)).flex(1);

        panel.add(new UiLabel(Component.translatable("ui.emergentstealth.muster.schedule.hint")).color(SumiTheme.TEXT_MUTED)).size(-1, 11);

        UiFlex list = UiFlex.column().gap(3);
        for (Window window : windows) {
            UiFlex line = list.add(UiFlex.row().gap(4).center());
            line.add(new UiSlider(() -> window.from, v -> window.from = (int) v, 0, 24, true, v -> (int) v + ":00", true)).flex(1);
            line.add(new UiSlider(() -> window.to, v -> window.to = (int) v, 0, 24, true, v -> (int) v + ":00", true)).flex(1);
            line.add(new UiCycle<>(choices, () -> window.choice, value -> window.choice = value, MusterRollScreen::describe, true)).flex(2);
            line.add(new UiButton(Component.translatable("ui.emergentstealth.muster.remove_window"), UiButton.Style.GHOST, () -> removeWindow(window))
                    .enabledWhen(() -> windows.size() > 1)).size(16, 18);
            line.size(-1, ROW_H);
            line.tooltip(() -> List.of(window.from == window.to ? Component.translatable("ui.emergentstealth.muster.window.all_day")
                    : Component.translatable("ui.emergentstealth.muster.window.hours", window.from, window.to)));
        }
        panel.add(new UiScroll(list)).flex(1);

        panel.add(new UiDivider(912L).thickness(1.5F).color(SumiTheme.INK_FAINT)).size(-1, 5);
        UiFlex footer = panel.add(UiFlex.row().gap(6).center());
        footer.add(new UiButton(Component.translatable("gui.cancel"), UiButton.Style.GHOST, this::onClose)).size(56, 20);
        footer.add(new UiButton(Component.translatable("ui.emergentstealth.muster.add_window"), UiButton.Style.SECONDARY, this::addWindow)
                .enabledWhen(() -> windows.size() < Schedule.MAX_ENTRIES)
                .tooltip(Component.translatable("ui.emergentstealth.muster.add_window.tip"))).size(76, 20);
        footer.add(new UiNode() {}).flex(1);
        UiButton muster = footer.add(new UiButton(Component.translatable("ui.emergentstealth.muster.muster"), UiButton.Style.PRIMARY, this::muster));
        muster.size(72, 20);
        footer.size(-1, 22);

        UiNode root = new UiNode() {
            @Override
            protected void layoutChildren() {
                panel.layout(x + (width - panelW) / 2, y + (height - panelH) / 2, panelW, panelH);
            }
        };
        root.add(panel);
        setFocus(muster);
        return root;
    }

    private static UiFlex row(UiPanel panel, String labelKey) {
        UiFlex row = panel.add(UiFlex.row().gap(6).center());
        row.add(new UiLabel(Component.translatable(labelKey)).color(SumiTheme.TEXT_MUTED)).size(LABEL_W, -1);
        row.size(-1, ROW_H);
        return row;
    }

    /** Our own ids by path, others in full. */
    private static String shortName(Identifier id) {
        return id.getNamespace().equals(EmergentStealth.MODID) ? id.getPath() : id.toString();
    }

    private static Component describe(Choice choice) {
        return switch (choice) {
            case PostHere p -> Component.translatable("ui.emergentstealth.muster.activity.post");
            case Wander w -> Component.translatable("ui.emergentstealth.muster.activity.wander", w.radius());
            case Route r -> Component.translatable("ui.emergentstealth.muster.activity.route", r.name());
        };
    }

    private Component summary() {
        String at = open.pos().getX() + " " + open.pos().getY() + " " + open.pos().getZ();
        return open.draft().isEmpty() ? Component.translatable("ui.emergentstealth.muster.summary.spawn", at)
                : Component.translatable("ui.emergentstealth.muster.summary.draft", at, open.draft());
    }

    /** The archetype's role, from the synced registry (empty if the client doesn't have it). */
    private Component role() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return Component.empty();
        }
        Archetype found = minecraft.level.registryAccess().lookupOrThrow(ESRegistries.ARCHETYPE).getValue(archetype);
        return found == null ? Component.empty()
                : Component.translatable("ui.emergentstealth.muster.role", found.role().getSerializedName(), found.faction().getPath());
    }

    private void addWindow() {
        Window last = windows.getLast();
        windows.add(new Window(last.to, last.from, new PostHere()));
        rebuildWidgets();
    }

    private void removeWindow(Window window) {
        if (windows.size() > 1) {
            windows.remove(window);
            rebuildWidgets();
        }
    }

    private float facing() {
        return facingStep * 45.0F;
    }

    private void muster() {
        List<Schedule.Entry> entries = new ArrayList<>();
        for (Window window : windows) {
            Schedule.Activity activity = switch (window.choice) {
                case PostHere p -> new Schedule.Post(open.pos(), facing());
                case Wander w -> new Schedule.Wander(w.radius());
                case Route r -> new Schedule.Route(r.name());
            };
            entries.add(new Schedule.Entry(window.from, window.to, activity));
        }
        ClientPacketDistributor.sendToServer(new MusterPayloads.Request(open.pos(), facing(), archetype, behaviour,
                new Schedule(List.copyOf(entries)), count));
        onClose();
    }
}
