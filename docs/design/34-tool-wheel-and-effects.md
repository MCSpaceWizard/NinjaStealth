# 34 — The toolbelt, the Sumi tool wheel and tool effects 📄 for review (implemented)

> **Status (2026-10-09):** built as described, checked in the real client (screenshots in [docs/screenshots/tools](../screenshots/tools/)). The wheel is `client/tool/ToolWheelScreen` on the new `UiRadial` widget; effects are `tool/ToolEffects` (server) and `client/tool/ToolEffectParticles`. The toolbelt (§3) was added after your note that the wheel should come from a belt you slot tools into. 107 GameTests pass. Not checked on screen yet: the spyglass tag ping (the automation can't hold the use key).

You asked (2026-10-09) to finish toolkit B, then build **the nice radial UI**, then **the effects**. Toolkit B is done (doc 21; TESTING.md T.1–T.10). This doc covers the other two. It builds on doc 21 §1 (wheel and quick use), doc 31 (Sumi) and decision F-03 (volumetric-looking smoke).

## 1. The tool wheel on Sumi

Today's wheel is a vanilla `Screen` with flat squares. The new one is drawn with Sumi's shapes, paper and ink.

| Topic | Decision |
|---|---|
| Shape | A ring of **paper wedges** (ring sectors) around a paper disc. Each wedge holds the tool's icon and its count. The world stays visible behind it; only a soft ink vignette darkens the edges. |
| Pointing | A **brush needle** points from the centre towards the pointer. The pointed-at wedge slides outward, fills with ink, and its icon grows. Inside the centre disc nothing is picked. |
| Centre | The pointed-at tool's name, how many you carry and a one-line hint ("Throw: a clack where it lands", "Bow: puts out lights"). With nothing pointed at: the current active tool. |
| Active tool | Its wedge carries a small vermilion seal (hanko). |
| Motion | Opens with the wedges unfolding clockwise one after another (eased, slight overshoot) and the ring brushed in. Hover moves ease. Closing fades quickly. **Reduced motion** makes all of it instant. |
| Input | Hold **R**, point, release (as now). Also: click a wedge; the mouse wheel steps round the ring; number keys **1–9** pick directly; **Esc** or releasing in the centre keeps the current tool. |
| Many tools | One ring up to 12 wedges; wedges get narrower as you carry more kinds. |
| Sounds | Sumi's `tick` when the pointed wedge changes, `stamp` when a tool is chosen. |
| Logic | The wedge maths (which wedge is under the pointer, wedge angles, the dead zone, key and wheel stepping) lives side-neutral in `ui/RadialMenu` with `ui/*` GameTests. |

**Same pass, smaller:** the active-tool icon next to the light gem gets a paper diamond behind it, the **lockpick ring** moves onto Sumi (ink ring, gold window, brush needle, pins as seals), and the spyglass focus ring is drawn as a smooth arc.

## 2. Tool effects

Each tool should read clearly in the world, at night too. Effects are particles, sounds and short animations; none changes gameplay. They respect the vanilla **Particles** setting (Minimal draws the essentials only).

| Tool | In flight / while active | On impact / outcome |
|---|---|---|
| Water arrow | A trail of droplets | A splash ring, and a **hiss and a puff of steam** at every light it puts out |
| Fire arrow | A small flame and smoke trail; it lights its surroundings dimly (client light) | A burst of embers; a flare at every light it relights |
| Sleep dart | A faint pale trail | While staggering: drowsy "z" marks drift up and a dizzy ring circles the head. When it drops: a slow exhale puff |
| Blinding powder | The existing glittering cone | Blinded NPCs: sparkles round the eyes and a dizzy ring until it wears off |
| Smoke bomb | — | **Thicker, volumetric-looking cloud:** an opening billow, a darker core, soft curling edges, a slow thinning at the end |
| Firecracker | A sputtering spark fountain during the fuse | Each bang: a bright flash and coloured crackle |
| Caltrops | — | An occasional metal glint so a patch is visible at night |
| Lockpicking | — | A small spark at the lock on a miss; a puff and a click when it opens |
| Spyglass tag | — | An ink ring "ping" round the NPC when tagged (for the tagger only) |

**How:** new particle types registered in `ESParticles` with client providers in `client/tool/ToolParticles` (sprites drawn in code or taken from vanilla). Server events that already happen (impact, snuff, stagger, bang) send them with `sendParticles`; flight trails are spawned client side from the projectile's tick. New textures, if any, go through `tools/programmer_art/textures.json`.

## 3. The toolbelt

You asked (2026-10-09) for the wheel to come from **a toolbelt you slot your tools into for quick select**.

| Topic | Decision |
|---|---|
| Item | `emergentstealth:toolbelt`, stacks to 1. Recipe `LIL / S S / LLL` (leather, iron nugget, string). Contents live in the vanilla `minecraft:container` component, so they survive dropping, chests and death. |
| Where it's worn | Anywhere in the inventory (your pick on the card, default "Inventory"): the offhand first, then the hotbar, then the main inventory; the first belt found is the one in use. A dedicated belt slot can come later without changing the belt. |
| Slots | **8**. Right-click the belt to open its panel (paper card, ink belt band with 8 numbered slots, your inventory below). The belt's own slot is locked while it's open. |
| What fits | `#emergentstealth:tools` minus `#emergentstealth:tool_ammo` (arrows, water/fire arrows, sleep darts): a bow or blowgun can't reach into the belt, so ammo stays in the inventory. |
| Wheel | Shows the belt's 8 slots in **fixed places** (slot 1 at the top, clockwise), keys 1–8 pick by slot; empty slots are faint wedges. Without a belt: "No toolbelt" and how to get one. |
| Quick use (V) | Draws from the belt only. Throwables and powder are used straight from the belt. Hand tools (blowgun, lockpick, spyglass, caltrops) come out of the belt into the main hand; the held item swaps into that belt slot if it fits there, else goes to a free inventory slot, else nothing happens ("No room..."). |
| HUD | The active-tool icon counts what's in the belt; a drawn hand tool in your hand isn't shown as "run out". |

## 4. Tests

- `ui/radial_*` GameTests: the hovered wedge for pointer angles (including the wrap at the top), the dead zone, wheel and number-key stepping.
- `toolkit/toolbelt_menu`, `toolkit/toolbelt_hand_tools`, and `toolkit/quick_use_keeps_held_item` (now from the belt).
- The rest is visual: screenshots of the wheel, the belt panel, the lockpick ring and each effect in the real client (`docs/screenshots/tools/`), plus TESTING.md rows.

## 5. Open questions

- None blocking. If "the effects" meant something other than tool visuals (status effects with HUD icons, screen effects when blinded), say so and §2 changes.
- Should the belt get its own equipment slot (like armour) later? It sits in the inventory for now.
