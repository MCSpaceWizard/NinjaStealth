# 31 — UI framework: "Sumi" (ink and paper) ✅ first pass implemented

> **Status (2026-10-09):** implemented and merged (PR #2): the Sumi core, the config screen, the skill tree on **K** and the dialogue preview, with 11 `ui/*` GameTests. Screenshots: [docs/screenshots/ui](../screenshots/ui/). §0 lists the decisions to review. Since then: `UiTextField` and `UiListRow` (minimal; the structure browser uses them). Not yet: a dropdown, a gamepad mapping, a custom brush font.

> **Extending Sumi** (new screens, widgets, theme tokens): see the developer guide, [docs/sumi-guide.md](../sumi-guide.md).

You asked (2026-10-08) for a **custom GUI framework** so the config, the skill tree, and later menus and dialogue look attractive and consistent. It follows the ink-and-paper accent theme (U-02).

## 0. Direction and design pass (2026-10-08) 📄 for review (implemented)

**Direction (your request, going with the recommendation from HANDOFF):**
- No flat colours anywhere in the GUI.
- **Modern geometric shapes drawn in code** (cut-corner panels, circles, rings, diamonds, hexagons, tapered brush strokes), with **procedural paper grain** and **ink accents**.
- An **animation system**: eased tweens, inertial (smooth) scrolling, and swiping between tabs.
- Easy to generate and to theme, and still clearly ink-and-paper.

**How it's built (decisions to review):**

| Topic | Decision |
|---|---|
| Shapes | Drawn as real vertex meshes through NeoForge's custom GUI element hook (`submitGuiElementRenderState`), not as pixel textures. Edges stay crisp at every GUI scale, and shapes can rotate and scale smoothly. |
| Textures | Paper grain and ink speckle are **generated in code** at runtime (tileable noise, seeded from the theme). No PNGs ship, so nothing needs painting. A resource pack *may* drop `textures/gui/sumi/paper.png` or `ink.png` and Sumi uses it instead. |
| Brush strokes | Geometry too: a polyline whose width tapers at both ends and wobbles with noise, so dividers, frames and the skill tree's prerequisite lines look painted. |
| Theme | `assets/emergentstealth/ui/theme.json` holds **tokens**: colours, metrics (padding, gap, corner cut, stroke), motion timings and grain settings. Missing keys fall back to built-in defaults, so a pack can override one colour. `ui/theme_high_contrast.json` is layered on top when the high-contrast option is on. |
| Motion | Tweens with easing curves (cubic, quart, expo, back), driven by real time. Inertial scroll: the wheel glides to a target, drags fling and coast with friction, edges rubber-band. Tabs are pages that swipe with a drag (or a horizontal trackpad scroll) and snap. **Reduced motion** turns all of it into instant changes. |
| Pure logic | Easing, tweens, inertial scroll, the swipe pager, layout maths, the theme codec, the config-to-widget mapping, the skill tree model and the typewriter live in a side-neutral package `ui/` (no client classes) so GameTests can check them. Everything that draws lives in `client/ui/`. |
| New client options | `ui.highContrast`, `ui.reducedMotion`, `ui.typewriterSpeed`. |
| Keys | **K** opens the skill tree (rebindable). In the tree: drag to pan, wheel to zoom, Q/E or PageUp/PageDown switch paths, arrows + Enter work too. |
| Dev commands | `/es dev dialogue` (op) opens the dialogue preview from the server. The client command `/esui config|skills|dialogue` opens each screen directly, which the screenshot automation uses. |

**Not in this pass:** text fields, dropdowns and lists (no screen needs them yet), a gamepad mapping (focus order is ready for it), a custom brush font.

## 1. Goals

- **One look everywhere:** washi-paper panels, sumi-ink brush borders, vermilion seal (hanko) accents, subtle motion.
- **Data-driven theming:** a `theme.json` in the resource pack defines colours, nine-slice textures, padding, fonts and timings. Artists can restyle everything without code, and resource packs can reskin it.
- **Built on vanilla 26.1 screens:** `Screen` and the new GUI extraction/rendering API. No external GUI library, so it stays light and survives updates.
- **Accessible:**
  - follows the vanilla GUI scale
  - a high-contrast theme toggle
  - keyboard navigation (and gamepad-ready focus order)
  - tooltips on everything
  - reduced-motion option

## 2. Building blocks (`client/ui/`)

| Layer | Contents |
|---|---|
| **Core** | `UiNode` tree (layout box, children, visibility, focus), `UiScreen` (hosts a tree, routes input, animates), `Theme`/`Style` loaded from `assets/<ns>/ui/theme.json` |
| **Layout** | `Row`, `Column`, `Stack`, `Grid`, `Scroll` (smooth, with an ink scrollbar), anchors, padding and gap |
| **Paint** | Nine-slice paper panels, brush-stroke frames, ink dividers, seal badges, icon and item rendering, text with wrap and shadow, clipping |
| **Widgets** | `Button` (ink-stamp hover), `Toggle`, `Slider`, `Cycle`/`Dropdown`, `TextField`, `Tabs`, `Tooltip`, `List`, `Panel`, `ProgressBar` |
| **Motion** | Easing tweens (fade, slide, ink-bleed reveal), driven by real time, with reduced-motion respected |
| **Canvas** | `NodeGraphCanvas` with pan and zoom, nodes, brush-stroke edges, hover cards (for the skill tree) |
| **Dialogue** | `DialogueBox`: portrait slot, speaker name seal, typewriter text (skippable), up to 4 choices with key hints (for missions and NPC talk later) |

## 3. First screens

1. **Config screen.** It replaces NeoForge's default list for Emergent Stealth.
   - Built automatically from our `ModConfigSpec`s: sections become tabs, booleans toggles, ranges sliders, enums cycles.
   - Comments show as tooltips. Server values are shown read-only unless you're op in singleplayer.
2. **Skill tree screen** (doc 26):
   - two path tabs (Shinobi ink-black, Shogunate vermilion)
   - pan and zoom canvas; nodes as seals
   - prerequisite brush lines; a node glows when it can be bought
   - a hover card with effects and cost; click to unlock (server-validated)
   - the Insight bar and points counter
   - opened with **K** (rebindable)
3. **Dialogue preview:** a `/es dev dialogue` command (op) shows a sample conversation, so the look can be reviewed before missions exist.

## 4. Art

- Programmer art first, via `tools/programmer_art`: paper grain, brush frames, seal stamps, all nine-slice friendly.
- Textures live in `textures/gui/sumi/` with sizes documented in `docs/assets.md`, so artists can paint over them.
- Font: vanilla to start. A custom pixel brush font is optional later; the theme can name it.

## 5. Tests

- **Pure-logic unit tests** as GameTests:
  - layout maths (row/column/grid sizes)
  - theme JSON parsing and fallbacks
  - config spec → widget mapping
- **Visual checks:** client screenshots of each screen.
