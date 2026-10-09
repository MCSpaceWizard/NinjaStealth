package com.mcspacewizard.emergentstealth.client.tool;

import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.client.ui.Paint;
import com.mcspacewizard.emergentstealth.client.ui.UiContext;
import com.mcspacewizard.emergentstealth.client.ui.UiNode;
import com.mcspacewizard.emergentstealth.client.ui.UiScreen;
import com.mcspacewizard.emergentstealth.network.LockpickPayloads;
import com.mcspacewizard.emergentstealth.world.lock.Lockpicking;
import com.mcspacewizard.emergentstealth.ui.Easing;
import com.mcspacewizard.emergentstealth.ui.SumiTheme;
import com.mcspacewizard.emergentstealth.ui.Tween;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The timing ring on Sumi (design doc 21 §2, E-05; doc 34 §1): an ink needle sweeps round a ring with one gold
 * window. Click (or press Space) while the needle is inside the window to set a pin; {@link Lockpicking#PINS}
 * pins open the lock. A miss loses a pin. The window moves after every click. The server has the final say on
 * progress; the window width comes from it (the picker's {@code lockpick_window} stat).
 */
public class LockpickScreen extends UiScreen {
    private static final long FLASH_MS = 280;

    private final BlockPos pos;
    private final int difficulty;
    private final int pins;
    private final float speed;
    private final float window;
    private final RandomSource random = RandomSource.create();
    private final long startMs = System.currentTimeMillis();
    private final Tween windowMove = new Tween(0.0F);

    private float windowStart;
    private int progress;
    private long flashUntil;
    private boolean flashHit;
    private boolean finished;

    public LockpickScreen(BlockPos pos, int difficulty, int pins, float window) {
        super(Component.translatable("screen.emergentstealth.lockpick"));
        this.pos = pos;
        this.difficulty = difficulty;
        this.pins = pins;
        this.speed = Lockpicking.speed(difficulty);
        this.window = window;
        moveWindow();
        windowMove.snap(windowStart);
    }

    public BlockPos pos() {
        return pos;
    }

    private void moveWindow() {
        windowStart = random.nextFloat() * 360.0F;
    }

    /** Needle angle in degrees, 0 = up, clockwise. */
    private float needle() {
        return ((System.currentTimeMillis() - startMs) / 1000.0F * speed) % 360.0F;
    }

    private boolean inWindow(float angle) {
        float delta = Mth.positiveModulo(angle - windowStart, 360.0F);
        return delta <= window;
    }

    private void attempt() {
        if (finished) {
            return;
        }
        boolean hit = inWindow(needle());
        flashHit = hit;
        flashUntil = System.currentTimeMillis() + FLASH_MS;
        progress = hit ? Math.min(pins, progress + 1) : Math.max(0, progress - 1);
        send(hit ? LockpickPayloads.Action.HIT : LockpickPayloads.Action.MISS);
        float from = windowMove.value(ctx.now());
        moveWindow();
        // Ease the gold window to its new place along the short way round.
        float to = from + Mth.wrapDegrees(windowStart - from);
        windowMove.restart(from, to, ctx.now(), ctx.duration(SumiTheme.FAST), Easing.CUBIC_OUT);
    }

    /** Authoritative progress and outcome from the server. */
    public void onState(LockpickPayloads.State state) {
        progress = state.progress();
        if (state.outcome() != LockpickPayloads.State.CONTINUE) {
            finished = true;
            if (minecraft != null && minecraft.screen == this) {
                onClose();
            }
        }
    }

    private static void send(int action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null && minecraft.getConnection().hasChannel(LockpickPayloads.Action.TYPE)) {
            ClientPacketDistributor.sendToServer(new LockpickPayloads.Action(action));
        }
    }

    @Override
    protected UiNode build() {
        return new Ring();
    }

    @Override
    protected boolean screenKey(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_SPACE || event.key() == GLFW.GLFW_KEY_ENTER) {
            attempt();
            return true;
        }
        return false;
    }

    @Override
    public void removed() {
        if (!finished) {
            finished = true;
            send(LockpickPayloads.Action.CANCEL);
        }
        super.removed();
    }

    /** Degrees (0 = up, clockwise) to screen radians (0 = right, clockwise). */
    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees - 90.0F);
    }

    /** The whole minigame, drawn round the centre of the screen. Any click is an attempt. */
    private final class Ring extends UiNode {
        @Override
        public boolean mouseDown(UiContext ctx, double mx, double my, int button) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                attempt();
                return true;
            }
            return false;
        }

        @Override
        protected void draw(UiContext ctx) {
            GuiGraphicsExtractor g = ctx.graphics();
            SumiTheme theme = ctx.theme();
            long nowMs = System.currentTimeMillis();
            float cx = x + width / 2.0F;
            float cy = y + height / 2.0F - 6.0F;
            float r = Math.clamp(Math.min(width, height) * 0.2F, 36.0F, 64.0F);
            int pad = theme.metric(SumiTheme.PADDING);

            // A paper card behind it all.
            Component hint = Component.translatable("screen.emergentstealth.lockpick.hint");
            float cardW = Math.max(r * 2.0F + pad * 6.0F, ctx.font().width(hint) + pad * 2.0F);
            float cardH = r * 2.0F + 86.0F;
            Paint.panel(g, cx - cardW / 2.0F, cy - r - 40.0F, cardW, cardH, theme.color(SumiTheme.PAPER));
            Paint.textCentered(g, ctx.font(), title, cx, cy - r - 32.0F, theme.color(SumiTheme.TEXT));
            Paint.textCentered(g, ctx.font(), Component.translatable("screen.emergentstealth.lockpick.difficulty", difficulty),
                    cx, cy - r - 21.0F, theme.color(SumiTheme.TEXT_MUTED));

            // The ring: a faint ink track, the gold window, a darker rim.
            Paint.ring(g, cx, cy, r - 5.0F, r + 5.0F, Paint.fade(theme.color(SumiTheme.INK), 0.12F));
            Paint.ring(g, cx, cy, r - 1.0F, r + 1.0F, theme.color(SumiTheme.INK_FAINT));
            float start = windowMove.value(ctx.now());
            Paint.arc(g, cx, cy, r - 5.0F, r + 5.0F, rad(start), (float) Math.toRadians(window), theme.color(SumiTheme.GOLD));
            Paint.arc(g, cx, cy, r + 4.0F, r + 5.0F, rad(start), (float) Math.toRadians(window), theme.color(SumiTheme.INK_SOFT));

            // The needle: a brush stroke from the hub, green or red for a moment after a click.
            boolean flashing = nowMs < flashUntil;
            int needleColor = flashing ? theme.color(flashHit ? SumiTheme.JADE : SumiTheme.LACQUER) : theme.color(SumiTheme.INK);
            float a = rad(needle());
            Paint.line(g, cx, cy, cx + (float) Math.cos(a) * (r + 7.0F), cy + (float) Math.sin(a) * (r + 7.0F), 2.0F, needleColor);
            Paint.circle(g, cx, cy, 5.0F, theme.color(SumiTheme.INK));
            Paint.circle(g, cx, cy, 2.0F, theme.color(SumiTheme.PAPER));
            if (flashing) {
                float f = (flashUntil - nowMs) / (float) FLASH_MS;
                Paint.ring(g, cx, cy, r + 6.0F, r + 6.0F + 4.0F * f, Paint.fade(needleColor, f));
            }

            // Pins: seals, vermilion once set.
            float seal = 6.0F;
            float spacing = 18.0F;
            float px = cx - (pins - 1) * spacing / 2.0F;
            float py = cy + r + 18.0F;
            for (int i = 0; i < pins; i++) {
                boolean set = i < progress;
                Paint.regular(g, px + i * spacing, py, seal + 1.0F, 4, 0.0F, theme.color(SumiTheme.INK_SOFT));
                Paint.regular(g, px + i * spacing, py, seal, 4, 0.0F, theme.color(set ? SumiTheme.LACQUER : SumiTheme.PAPER_SHADE));
            }
            Paint.textCentered(g, ctx.font(), hint, cx, cy + r + 30.0F,
                    theme.color(SumiTheme.TEXT_MUTED));
        }
    }
}
