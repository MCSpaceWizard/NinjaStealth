# 31 — UI framework: "Sumi" (ink and paper) 📄

You asked (2026-10-08) for a **custom GUI framework** so the config, the skill tree, and later menus and dialogue look attractive and consistent. It follows the ink-and-paper accent theme (U-02).

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
