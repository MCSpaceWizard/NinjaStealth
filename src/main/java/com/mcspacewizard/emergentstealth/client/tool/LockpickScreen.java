package com.mcspacewizard.emergentstealth.client.tool;

import org.lwjgl.glfw.GLFW;

import com.mcspacewizard.emergentstealth.network.LockpickPayloads;
import com.mcspacewizard.emergentstealth.world.lock.Lockpicking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The timing ring (design doc 21 §2, E-05): a needle sweeps round a ring with one highlighted window. Click
 * (or press Space) while the needle is inside the window to set a pin; {@link Lockpicking#PINS} pins open the
 * lock. A miss loses a pin. The window moves after every click. The server has the final say on progress.
 */
public class LockpickScreen extends Screen {
    private static final int RADIUS = 40;
    private static final int SEGMENTS = 72;
    private static final long FLASH_MS = 250;

    private final BlockPos pos;
    private final int difficulty;
    private final int pins;
    private final float speed;
    private final float window;
    private final RandomSource random = RandomSource.create();
    private final long startMs = System.currentTimeMillis();

    private float windowStart;
    private int progress;
    private long flashUntil;
    private boolean flashHit;
    private boolean finished;

    public LockpickScreen(BlockPos pos, int difficulty, int pins) {
        super(Component.translatable("screen.emergentstealth.lockpick"));
        this.pos = pos;
        this.difficulty = difficulty;
        this.pins = pins;
        this.speed = Lockpicking.speed(difficulty);
        this.window = Lockpicking.window(difficulty);
        moveWindow();
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
        moveWindow();
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT || event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            attempt();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_SPACE || event.key() == GLFW.GLFW_KEY_ENTER) {
            attempt();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        if (!finished) {
            finished = true;
            send(LockpickPayloads.Action.CANCEL);
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int cx = this.width / 2;
        int cy = this.height / 2;
        long now = System.currentTimeMillis();
        boolean flashing = now < flashUntil;

        // Ring: dark dots, the window in gold.
        for (int i = 0; i < SEGMENTS; i++) {
            float angle = i * 360.0F / SEGMENTS;
            boolean lit = inWindow(angle + 360.0F / SEGMENTS * 0.5F);
            int color = lit ? 0xFFE8B23A : 0xFF3A3540;
            dot(graphics, cx, cy, angle, RADIUS, lit ? 3 : 2, color);
        }
        // Needle: a line of dots from the centre, white (green/red flash after a click).
        float needle = needle();
        int needleColor = flashing ? (flashHit ? 0xFF6BE070 : 0xFFE04848) : 0xFFF2EEE6;
        for (int r = 8; r <= RADIUS + 4; r += 3) {
            dot(graphics, cx, cy, needle, r, 2, needleColor);
        }
        graphics.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFF8A8F98);

        // Pins.
        int pinsWidth = pins * 12 - 4;
        for (int i = 0; i < pins; i++) {
            int x = cx - pinsWidth / 2 + i * 12;
            int y = cy + RADIUS + 14;
            graphics.fill(x, y, x + 8, y + 8, i < progress ? 0xFFE8B23A : 0xFF3A3540);
            graphics.outline(x - 1, y - 1, 10, 10, 0xFF1B1A20);
        }
        graphics.centeredText(this.font, this.title, cx, cy - RADIUS - 26, 0xFFF2EEE6);
        graphics.centeredText(this.font, Component.translatable("screen.emergentstealth.lockpick.difficulty", difficulty), cx, cy - RADIUS - 14, 0xFFB8B0A0);
        graphics.centeredText(this.font, Component.translatable("screen.emergentstealth.lockpick.hint"), cx, cy + RADIUS + 30, 0xFFB8B0A0);
    }

    private static void dot(GuiGraphicsExtractor graphics, int cx, int cy, float angleDeg, float radius, int size, int color) {
        double rad = Math.toRadians(angleDeg);
        int x = cx + (int) Math.round(Math.sin(rad) * radius);
        int y = cy - (int) Math.round(Math.cos(rad) * radius);
        int half = size / 2;
        graphics.fill(x - half, y - half, x - half + size, y - half + size, color);
    }
}
