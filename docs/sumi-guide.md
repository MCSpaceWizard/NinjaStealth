# Sumi developer guide

How to add to and change Sumi, the mod's ink-and-paper GUI framework. Design and decisions: [doc 31](design/31-ui-framework.md). This page is the practical side: recipes, the widget list and the traps.

**Where things live**

| Package / file | What's there |
|---|---|
| `client/ui/` | `UiScreen`, `UiNode`, `UiContext`, `Sumi` (theme + clock + accessibility), `Paint` (drawing), `Mesh` (raw geometry), `SumiTextures`, `SumiClientEvents` (K key, `/esui`) |
| `client/ui/widget/` | Reusable widgets and `SumiSounds` |
| `client/ui/screen/` | The config screen, skill tree (+ `SkillCanvas`), dialogue preview |
| `ui/` (side-neutral) | `SumiTheme`, `UiLayout`, `Tween`, `Easing`, `InertialScroll`, `SwipePager`, `Typewriter`, `PaperNoise`, `ConfigMapping`, `SkillTreeModel` |
| `assets/emergentstealth/ui/` | `theme.json`, `theme_high_contrast.json` |
| `gametest/UiTests.java` | The `ui/*` GameTests |

Anything that draws or touches `Minecraft` goes in `client/`. Pure maths goes in `ui/` so GameTests (which run on a dedicated server) can check it.

## 1. Mental model

**A retained tree.** A screen is a `UiScreen` holding a tree of `UiNode`s. `build()` creates the whole tree; it runs on open **and again on every resize** (vanilla calls `init()`). So keep state in screen fields, not in widgets. `StructureBrowserScreen` keeps `filter` and `selected` on the screen (and in statics to survive reopening) and pushes them back into the new widgets in `build()`.

**Each frame** (`UiScreen.extractRenderState`):
1. `ctx.hovered = root.hit(mouse)`: the deepest visible node under the pointer.
2. `root.update(ctx)`: animation step. Step your tweens here and call `super.update(ctx)` so children update too.
3. `root.render(ctx)`: per node `draw()` (under children), children, then `drawOver()` (above children).
4. Next stratum: the tooltip card, then `drawOverlay()`.

**Layout** is top-down and only runs when asked: on `init()` and on `relayout()`. A parent calls `child.layout(x, y, w, h)`; the child stores its box and runs `layoutChildren()`. Parents ask children for `preferredWidth()` and `preferredHeight(forWidth)`. `size(w, h)` fixes either; `-1` means "work it out". `flex(weight)` shares leftover space in a `UiFlex`. A plain `UiNode` overlays its children on its own box.

**Coordinates** are GUI pixels. A node's `x/y` live in its parent's content space; `UiScroll` and `UiPager` shift their children through `childOffsetX/Y`. Input hooks receive the pointer already in the node's space. Use `node.screenBox()` when you need real screen coordinates (overlays, cards).

**Pointer input bubbles.** On press, `UiScreen` walks from the hit node to the root calling `mouseDown()`; the first to return `true` is the *pressed* node, gets `mouseUp(..., inside)` on release, and gets focus if `isFocusable()`. `scroll()` bubbles the same way.

**Drags are claimed.** After 3 px of movement, `claimDrag(ctx, gesture)` is offered from the pressed node upwards; the first `true` owns the rest of the drag (`drag()` then `dragEnd()`). If the owner isn't the pressed node, the pressed node gets `pressCancelled()`. That's how a slider keeps a horizontal drag while the `UiScroll` around it takes vertical ones (`gesture.horizontal()`).

**Keys** go to the focused node, then its ancestors (`key()`, `character()`). If none consumes the key: `screenKey()` on the screen, then Esc closes. Tab / Shift+Tab always move focus through `collectFocusable()` order (tree order, visible and hittable only, so hidden pager pages are skipped).

**Keyboard mode vs mouse mode.** `ctx.keyboardMode` turns on with Tab or when a focused node consumes a key, and off on a click or when the mouse moves more than 2 px. It decides two things:
- Focus marks: draw them only when `ctx.showFocus(this)` (focused *and* keyboard mode).
- Tooltips: in mouse mode the hovered node's tooltip follows the pointer; in keyboard mode the focused node's tooltip sits under that node. Either way it walks up to the nearest ancestor with a tooltip, waits `tooltip_delay`, and is hidden while a drag is in progress.

**Live text.** Most text-taking widgets accept a `Supplier<Component>` (`UiLabel`, `UiButton`, `UiListRow`, tooltips). The supplier is read every frame, so counters and selections update without rebuilding:

```java
header.add(new UiLabel(() -> Component.translatable("ui.emergentstealth.structures.count", ClientStructures.ids().size()))
        .color(SumiTheme.TEXT_MUTED)).flex(1);
```

**When to call `relayout()`.** Suppliers change what is *drawn*, not the layout. Call `relayout()` (on the screen) when sizes change: you refilled a list (`clearChildren()` + `add()`), toggled `setVisible()`, or wrapped text changed its line count. `StructureBrowserScreen` checks in `tick()` whether the details have loaded and relayouts only when that flips; `DialogueScreen` relayouts after showing a new line. `relayout()` does not call `build()`.

## 2. Recipe: add a screen

1. Subclass `UiScreen` (in a `client` package), pass a translatable title, implement `build()`.
2. Centre a panel with a small anonymous root node (all three Sumi screens do this).
3. Pick the starting focus with `setFocus()` inside `build()`. That works because `init()` clears focus, the pressed node and the drag owner *before* calling `build()`.

```java
public final class LedgerScreen extends UiScreen {
    private boolean showAll; // screen state: build() runs again on resize
    private int count() { return 0; } // stand-in for real data

    public LedgerScreen() {
        super(Component.translatable("ui.emergentstealth.ledger.title"));
    }

    @Override
    protected UiNode build() {
        int pad = Sumi.metric(SumiTheme.PADDING);
        int panelW = Math.min(320, width - 20);
        int panelH = Math.min(200, height - 20);

        UiPanel panel = new UiPanel();
        panel.padding(UiLayout.Insets.of(pad + 2, pad)).gap(4);
        panel.add(new UiLabel(Component.translatable("ui.emergentstealth.ledger.title")).scale(1.5F)).size(-1, 14);
        panel.add(new UiDivider(1234L).thickness(3.0F)).size(-1, 6);
        panel.add(new UiLabel(() -> Component.translatable("ui.emergentstealth.ledger.count", count())).wrap()).flex(1);

        UiFlex footer = panel.add(UiFlex.row().gap(6).center());
        footer.add(new UiLabel(Component.translatable("ui.emergentstealth.ledger.show_all")).color(SumiTheme.TEXT_MUTED)).flex(1);
        footer.add(new UiToggle(() -> showAll, v -> showAll = v, true))
                .tooltip(Component.translatable("ui.emergentstealth.ledger.show_all.tip"));
        UiButton done = footer.add(new UiButton(Component.translatable("gui.done"), UiButton.Style.PRIMARY, this::onClose));
        done.size(64, 20);
        footer.size(-1, 22);

        UiNode root = new UiNode() {
            @Override
            protected void layoutChildren() { // x/y/width/height here are this node's (the full screen)
                panel.layout(x + (width - panelW) / 2, y + (height - panelH) / 2, panelW, panelH);
            }
        };
        root.add(panel);
        setFocus(done);
        return root;
    }
}
```

**Opening it.** Always on the client thread:
- From client code (key, item use, payload handler): `Minecraft.getInstance().setScreen(new LedgerScreen())`. Keys: copy the `SKILLS` pattern in `SumiClientEvents` (register in `onRegisterKeys`, poll `consumeClick()` in `onClientTick`).
- From a chat command: add a `.then(Commands.literal("ledger").executes(ctx -> open(LedgerScreen::new)))` branch to `/esui` in `SumiClientEvents`. `open()` defers to the next tick, because the chat screen is still closing when a command runs. `/esui` is also how the screenshot automation reaches a screen.
- From the server: send a payload and open in its client handler inside `context.enqueueWork(...)` (see `OpenDialoguePreviewPayload`, or `ClientStructures.handleList`).

**Other hooks on `UiScreen`:**
- `screenKey(KeyEvent)`: screen-wide shortcuts (skill tree Q/E, config `[`/`]`).
- `extractBackground(...)`: override to keep the world visible (`DialogueScreen`).
- `drawOverlay(...)` and the public `drawCard(...)`: hover cards above everything (`SkillTreeScreen`).
- `openProgress()`: 0..1 of the open animation.
- `isPauseScreen()`: `false` by default; the config screen returns `true`.
- `removed()` / `onClose()`: save or return to a parent screen (`SumiConfigScreen`).

**Lang keys.** Use `ui.emergentstealth.<screen>.<key>` in `src/main/resources/assets/emergentstealth/lang/en_us.json`. Never ship a literal English string in a widget (`Component.literal` is for data such as IDs and numbers). Keep the JSON valid: the `ui/lang_file_valid` GameTest parses it strictly.

**Config options need no UI work.** `SumiConfigScreen` builds rows from the `ModConfigSpec`s through `ConfigMapping`: a new option shows up as a toggle, slider or cycle, with its comment as the tooltip. Add its `emergentstealth.configuration.<name>` lang key.

## 3. Recipe: add a widget

Subclass `UiNode` in `client/ui/widget/`. A small, complete example: a chip that toggles, with a hover tween, a focus mark, a sound and keyboard support.

```java
public class UiChip extends UiNode {
    private final Component label;
    private final BooleanSupplier on;
    private final Runnable toggle;
    private final Tween hover = new Tween(0.0F);

    public UiChip(Component label, BooleanSupplier on, Runnable toggle) {
        this.label = label;
        this.on = on;
        this.toggle = toggle;
    }

    @Override
    public int preferredWidth() { // honour size() first; there's no ctx here, so use the client font
        return prefWidth >= 0 ? prefWidth : Minecraft.getInstance().font.width(label) + 16;
    }

    @Override
    public int preferredHeight(int forWidth) {
        return prefHeight >= 0 ? prefHeight : 16;
    }

    @Override
    public void update(UiContext ctx) {
        boolean active = ctx.isHovered(this) || ctx.showFocus(this);
        hover.animate(active ? 1.0F : 0.0F, ctx.now(), ctx.duration(SumiTheme.FAST), Easing.CUBIC_OUT);
        super.update(ctx);
    }

    @Override
    protected void draw(UiContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        SumiTheme theme = ctx.theme();
        float[] shape = Paint.cutRectPoints(x, y, width, height, theme.metricF(SumiTheme.CUT) - 2);
        if (on.getAsBoolean()) {
            Paint.inkPolygon(g, shape, theme.color(SumiTheme.INK));
        } else {
            float h = hover.value(ctx.now());
            Paint.paperPolygon(g, shape, Paint.mix(theme.color(SumiTheme.PAPER_SHADE), theme.color(SumiTheme.PAPER), h));
        }
        Paint.outline(g, shape, 1.0F, theme.color(SumiTheme.INK_SOFT));
        int text = theme.color(on.getAsBoolean() ? SumiTheme.TEXT_ON_INK : SumiTheme.TEXT);
        Paint.textCentered(g, ctx.font(), label, x + width / 2.0F, y + (height - 8) / 2.0F, text);
        if (ctx.showFocus(this)) {
            Paint.brushLine(g, x + 3, y + height + 2, x + width - 3, y + height + 2, 2.0F, theme.color(SumiTheme.LACQUER), 7L);
        }
    }

    @Override
    public boolean mouseDown(UiContext ctx, double mx, double my, int button) {
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT; // true: we get mouseUp, and focus
    }

    @Override
    public void mouseUp(UiContext ctx, double mx, double my, int button, boolean inside) {
        if (inside) {
            SumiSounds.tick();
            toggle.run();
        }
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public boolean key(UiContext ctx, KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_SPACE) {
            SumiSounds.tick();
            toggle.run();
            return true;
        }
        return false;
    }
}
```

**The pieces:**
- **Size:** override `preferredWidth()` / `preferredHeight(forWidth)` and return `prefWidth`/`prefHeight` when they're `>= 0`, so `size()` still works.
- **Containers:** override `layoutChildren()`. For scrolling or paging also override `childOffsetX/Y`, `clipsChildren()`, `childHittable()` and `renderChildren()`, and `reveal()` so keyboard focus scrolls into view (see `UiScroll`, `UiPager`).
- **Drawing:** `draw()` under children, `drawOver()` above them. `ctx.graphics()` only works during rendering; it throws in `update()` and input hooks.
- **Input:** `mouseDown` (return `true` to take the press), `mouseUp`, `claimDrag`/`drag`/`dragEnd`, `pressCancelled`, `scroll`, `key`, `character` (typed text; `UiTextField` shows how). Return `false` to let the event bubble.
- **Focus:** `isFocusable()`; `isFocused()`; override `setFocused()` to react (call `super`). Draw focus with `ctx.showFocus(this)`.
- **Tooltips:** callers use `.tooltip(Component)` or `.tooltip(Supplier<List<Component>>)`; the first line is the heading. A widget can set its own in the constructor (`PointsSeal` in `SkillTreeScreen`).
- **State helpers on `UiContext`:** `isHovered`, `isHoveredWithin`, `isPressed`, `showFocus`, `mouseIn(node)`, `now()`, `dt()`, `font()`, `theme()`, `screen()`, `duration(token)`.
- **Sounds:** `SumiSounds.click()` (buttons), `tick()` (small selections), `page()` (page turns), `stamp()`. Add new ones as static methods there.
- **Colours and sizes:** always theme tokens: `ctx.theme().color(SumiTheme.X)`, `metric()` / `metricF()`. If a widget takes a colour from its caller, take a token name (as `UiLabel.color(String)` and `UiDivider.color(String)` do).
- **Animation:** keep a `Tween` per animated value; `animate(target, now, ms, easing)` retargets smoothly from the current value, `restart(...)` forces a start value, `snap(v)` jumps. Durations come from `ctx.duration(SumiTheme.FAST | NORMAL | SLOW)`, which is 0 under reduced motion. Constant motion (breathing, spinners) should check `Sumi.reducedMotion()`. Easing curves: `LINEAR`, `QUAD_OUT`, `CUBIC_OUT`, `CUBIC_IN_OUT`, `QUART_OUT`, `EXPO_OUT`, `BACK_OUT`, `SINE_IN_OUT`.

**`Paint` helpers** (all take the `GuiGraphicsExtractor` first, float GUI pixels):
- Shapes: `rect`, `gradientV`, `gradientH`, `polygon` (convex), `outline`, `cutRect` / `cutRectPoints`, `circle`, `regular` / `regularPoints` / `regularRing` (diamonds, hexagons), `ring`, `arc`, `glow`, `line`, `dashed`, `bezier` (points).
- Ink and paper: `paperPolygon`, `inkPolygon`, `paperArc` / `inkArc` (ring sectors, for wedges), `panel` (a full paper card), `brushLine`, `brush` (tapered polyline, with a `progress` for reveals). Brushes take a fixed `seed` so the stroke doesn't change between frames.
- Text: `text`, `textCentered`, `textScaled`, `wrapped` (returns height), `wrappedHeight`.
- Colour: `fade`, `mix`, and `pushAlpha` / `popAlpha` (a global alpha multiplier for fading whole subtrees; always pop with the value push returned).
- Transforms and clipping come from vanilla: `g.pose().pushMatrix()/translate/scale/rotate/popMatrix()`, `g.enableScissor(...)`/`disableScissor()`. Items: `g.item(stack, x, y)` (see `SkillCanvas`).
- For shapes `Paint` lacks, build quads with `Mesh.colored()` or `Mesh.textured(tex.setup())` and `submit(g)`.

If a widget needs non-trivial maths (hit areas, layout, state machines), put it in `ui/` and add a `ui/*` GameTest.

## 4. Recipe: theme tokens

Tokens are defined in `ui/SumiTheme.java` in four maps: `colors` (ARGB), `metrics` (GUI pixels), `motion` (ms) and `texture` (grain settings).

To add one:
1. Add a constant, e.g. `public static final String WARNING = "warning";`, in the right section.
2. Add its default to `SumiTheme.DEFAULT` (the `ordered(...)` / `orderedF(...)` lists). This is the real fallback.
3. Add it to `assets/emergentstealth/ui/theme.json` so artists can see it, and to `theme_high_contrast.json` if it affects legibility.
4. Use it: `ctx.theme().color(SumiTheme.WARNING)`, or `Sumi.color(...)` / `Sumi.metric(...)` outside a node.

**Data-driven parts.** Every key in the JSON files is optional and layers over the defaults, so a resource pack can change one colour. Colours are `#rgb`, `#rrggbb` or `#aarrggbb`. When the client option `ui.highContrast` is on, `theme_high_contrast.json` is layered on top. Changing the palette (`theme.json`) needs no code.

**Reloading.** `UiScreen.init()` calls `Sumi.reloadTheme()`, so the theme is re-read every time a Sumi screen opens or resizes. After editing a pack, press F3+T and reopen the screen. A bad file logs a warning and keeps the previous layer.

**Unknown tokens** don't crash: an unknown colour draws magenta (`0xFFFF00FF`) and an unknown metric is 0. If something is bright pink, check the token name.

## 5. Recipe: textures

Sumi ships **no GUI PNGs**. `SumiTextures` generates the paper grain (256 px) and ink speckle (128 px) at runtime from the `texture` tokens, and rebuilds them when those tokens change. A resource pack can replace them with `textures/gui/sumi/paper.png` or `ink.png`. Use them through `Paint.paperPolygon` / `inkPolygon`, or `SumiTextures.paper()` / `ink()` with `Mesh.textured(...)`.

Prefer drawing in code. If a screen really needs a bitmap (an icon, a sprite):
1. Add a spec to `tools/programmer_art/textures.json` (`path` is relative to `assets/emergentstealth/textures/`, colours come from its `palette`).
2. Run `python3 tools/programmer_art/generate.py --only <path>`.
3. Never pass `--force` and never overwrite an existing PNG: it may be real art.
4. Add the texture to `docs/art/TEXTURE_LIST.md`.

## 6. Widget catalogue

| Widget | Use | Key API |
|---|---|---|
| `UiFlex` | Row or column layout | `UiFlex.row()`, `UiFlex.column()`, `gap(int)`, `padding(int \| Insets)`, `center()`; children use `flex(w)` / `size(w, h)` |
| `UiPanel` | Paper card that is a column | `new UiPanel()`, `paper(token)`, plus the `UiFlex` methods |
| `UiLabel` | One-line or wrapped text | `new UiLabel(Component \| Supplier)`, `color(token)`, `scale(f)`, `wrap()`, `align(Align.LEFT \| CENTER \| RIGHT)`; long single lines end in "..." |
| `UiDivider` | Brush-stroke rule | `new UiDivider(seed)`, `thickness(f)`, `color(token)` |
| `UiButton` | Button, three styles | `new UiButton(Component \| Supplier, Style.PRIMARY \| SECONDARY \| GHOST, Runnable)`, `enabledWhen(BooleanSupplier)` |
| `UiToggle` | On/off switch | `new UiToggle(BooleanSupplier get, Consumer<Boolean> set, boolean editable)` |
| `UiSlider` | Number slider | `new UiSlider(get, set, min, max, integer, DoubleFunction<String> format, editable)` |
| `UiCycle<T>` | ‹ value › picker | `new UiCycle<>(List<T> values, Supplier<T> get, Consumer<T> set, Function<T, Component> name, editable)` |
| `UiProgressBar` | Ink-filled bar | `new UiProgressBar(DoubleSupplier 0..1, IntSupplier argb)` |
| `UiTextField` | One-line text input | `new UiTextField(placeholder)`, `onChange`, `onSubmit`, `maxLength`, `text()`, `setText()` |
| `UiListRow` | Selectable list row | `new UiListRow(Supplier label, BooleanSupplier selected, Runnable onSelect)`, `detail(Supplier)`, `onActivate(Runnable)` (double-click / Enter) |
| `UiScroll` | Inertial vertical scroll | `new UiScroll(content)`, `content()`, `state()` (`InertialScroll`) |
| `UiPager` | Swipeable pages | `new UiPager(pages, initial)`, `add(page)`, `onChange(IntConsumer)`, `select(i)`, `index()`, `position()`, `dragFrom` / `releaseFrom`, `isDragging()` |
| `UiTabBar` | Tabs that follow a pager | `new UiTabBar(Orientation.VERTICAL \| HORIZONTAL, pager::position, pager::select)`, `heading(label)`, `tab(label, page[, accent])`, `rowHeight(int)` |
| `UiRadial` | Radial menu of item wedges round a paper disc (the tool wheel) | `new UiRadial(List<Entry(icon, count, marked)>)`, `centre(IntFunction<List<Component>>)`, `caption(...)`, `onChoose(IntConsumer)`, `selected()`; mouse direction, wheel, arrows, 1–9; an empty icon draws a faint, empty wedge (an empty slot); maths in `ui/RadialMenu` |
| `UiDialogueBox` | Portrait, name seal, typewriter, choices | `new UiDialogueBox(IntConsumer onChoice, Runnable onContinue)`, `show(Line, now)`, `Line(speaker, text, choices, portrait)`, `MAX_CHOICES` |

Screen-private examples worth copying from: `ConfigRow` (a row composed of a label and a control), `SkillCanvas` (pan/zoom canvas with its own drag, scroll and keyboard handling), `SkillTreeScreen.PointsSeal` and `SumiConfigScreen.Seal` (tiny draw-only nodes).

## 7. Gotchas

- **Back faces are culled** in the GUI pipelines. `Mesh` normalises quad winding, so build shapes in either order, but if you write a new `GuiElementRenderState` yourself, wind it like vanilla or it vanishes.
- **Draw order within a layer is not submission order.** Inside one GUI layer, elements are sorted by pipeline and texture; only overlapping elements are stacked. If something must sit on top regardless, it belongs in a later stratum (`graphics.nextStratum()`, which `UiScreen` uses for tooltips) or in `drawOverlay()`.
- **Builder methods return their own class, `size()`/`flex()`/`tooltip()` are generic.** Call the widget's own setters first and the generic ones last: `UiFlex.row().gap(4).center()` then `.size(...)`. `UiPanel`'s padding and gap return `UiFlex`, so configure a panel in a separate statement (`panel.padding(...).gap(4);`) as the screens do.
- **Resize rebuilds everything.** Tweens, scroll positions and text field contents reset unless the screen restores them in `build()`.
- **Inside the anonymous root node**, `width`/`height`/`x`/`y` are the node's fields, not the screen's. They are the same box because the root is laid out to the full screen.
- **Children added later draw on top and are hit first.**
- **Hidden nodes keep their space until `relayout()`.** `UiFlex` skips invisible children only when it lays out.
- **`UiTextField` swallows printable keys** so screen shortcuts (Q/E, inventory key) don't fire while typing. It has no caret movement, selection or paste yet, and Tab always moves focus.
- **Esc closes the screen** unless the focused node consumes it.
- **Focus on click** only happens when the node returns `true` from `mouseDown()`.
- **Global alpha leaks.** An unbalanced `Paint.pushAlpha` fades everything after it. `Paint.text` skips text that is almost transparent.
- **Fonts outside rendering:** `preferredWidth()` has no `ctx`, so widgets use `Minecraft.getInstance().font`.
- **Server safety:** never reference `client.ui` classes from common code; `./gradlew runServer` catches leaks.
- **Keep `en_us.json` valid after merges.** It has broken on `main` before; `ui/lang_file_valid` catches it.

## 8. Checklist before pushing

1. Every visible string is a lang key in `en_us.json`, and the file is valid JSON.
2. New tokens have defaults in `SumiTheme.DEFAULT` and appear in `theme.json`.
3. New pure logic in `ui/` has a GameTest registered in `ESGameTests` (`test("ui/<name>", UiTests::<method>)`).
4. `./gradlew runGameTestServer` passes (all tests), and `./gradlew runServer` starts.
5. Visual check in the real client: follow `tools/client_automation/README.md`, open the screen (via `/esui` or its key), try mouse, Tab + Enter, a small window, high contrast and reduced motion, and take screenshots for `docs/screenshots/`.
6. Update `docs/TESTING.md` (feature table, test steps, status) and doc 31 if a decision changed. Keep this guide current when the API changes.
