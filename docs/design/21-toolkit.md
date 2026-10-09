# 21 — Toolkit I: the beta tool set (Stage 12, pulled forward) 📄

> **Status (2026-10-09):** part A (pebble, smoke, firecracker, blinding powder, caltrops) and part B (water/fire arrows, blowgun and sleep darts, keys, locks, lockpicking, spyglass tagging) are built, with 16 GameTests. The wheel, the lockpick ring and the tool effects were redone in [doc 34](34-tool-wheel-and-effects.md), and the wheel and quick use now draw from a **toolbelt** (doc 34 §3) instead of the whole inventory.

You asked (2026-10-08) for tools early, so testers get a **beta stealth experience**. This doc pulls the core of S12 forward and merges in S8's smoke bomb and pebbles. Every tool is server-authoritative, works in survival and multiplayer, and has a crafting recipe (basic tools at the crafting table, E-06), programmer art and a GameTest.

## 1. Tool wheel and quick use (E-03)

- **Hold R:** a radial wheel lists the **stealth tools in your inventory**, meaning items in the tag `#emergentstealth:tools`, with counts. Release on one to make it your **active tool**. The active tool shows as a small icon next to the light gem.
- **Press V:** uses the active tool **without changing what's in your hand**. Throwables are lobbed; hold V to charge a longer throw, like the S6 throw key. Ranged tools (blowgun, special arrows) are the exception: you equip them in your hand and use them like a bow.
- Tools also work normally from the hotbar (right-click).
- The active tool is a saved player attachment. Keys are rebindable.

## 2. The beta tools

| Tool | Use | Effect | Recipe (crafting table) |
|---|---|---|---|
| **Pebble** (stack 16) | Throw | Impact noise 10 at the landing spot, no trace (S-05); the classic distraction | 1 gravel → 4 pebbles |
| **Smoke bomb** (stack 8) | Throw | A 3-block-radius cloud for 10 s. **Blocks sight completely** (sight rays through smoke are blocked); NPCs inside are blinded. Loud pop (noise 12) | gunpowder + paper + charcoal |
| **Firecracker** (stack 8) | Throw | A 2 s fuse, then a string of bangs (noise 24) for 3 s. Draws guards away | paper + gunpowder |
| **Blinding powder** (stack 8) | Puff (short range) | A 4-block, 60° cone. NPCs in it are **blinded for 6 s**: they see nothing and stagger, but still hear | glowstone dust + bone meal + paper |
| **Caltrops** (stack 8) | Throw | Scatters a 2×2 patch for 60 s. Anyone stepping in takes 1 damage and Slowness II for 3 s; guards chasing you through it slow down | iron nugget ×3 |
| **Water arrow** | Bow | **Puts out lights** within 1.5 blocks of the impact (torches, lanterns, campfires, candles), and fire. Splash noise 2 | arrow + water bottle → 4 |
| **Fire arrow** | Bow | Relights unlit lights at the impact and ignites flammable blocks | arrow + flint + coal → 4 |
| **Blowgun + sleep darts** | Blowgun (bow-like, short draw) | A dart hit knocks an NPC out after a **3 s stagger**; NPCs in combat only stagger. Silent | bamboo ×3 + string; darts: 2 feathers + flint + spider eye → 4 |
| **Keys** | Carry | Open doors and chests locked to that key. Guards carry keys (archetype equipment) and can path through doors they hold keys for | — (found or pickpocketed later) |
| **Lockpicks** (durability 8) | Use on a locked door or chest | A **timing-ring** minigame (E-05): three clicks inside a moving window. A miss makes a click noise (4) and can break a pick | iron nugget ×2 + stick |
| **Spyglass tagging** | Spyglass | Look at an NPC through a spyglass for 1 s to **tag** it. Tagged NPCs are outlined through walls for 60 s, at most 3 tags at once (P-11) | vanilla spyglass |

**Map-maker tools (creative):** a **Locksmith's Kit** locks or unlocks a door or chest and hands out its key. `/es lock …` does the same by command.

## 3. Locks

- **Locked doors and chests** are per-position world data: a SavedData of locked positions and key ids, so any vanilla door, trapdoor, gate or chest can be locked. No new block types needed.
- **Keys:** a key item with a key-id data component, named "Key: <name>".
- **Guards:** a guard with a matching key in its inventory or equipment opens locked doors on its path. Guards without one treat locked doors as walls.

## 4. Tests (GameTests)

1. Smoke between a guard and a player blocks sight completely.
2. A firecracker draws a guard to it.
3. Blinding powder blinds an NPC in the cone but not one outside it.
4. A water arrow puts out a torch.
5. A sleep dart knocks an NPC out after its delay.
6. A locked door won't open without the key and opens with it. A guard with the key walks through; one without doesn't.
7. A caltrop slows and hurts whoever steps on it.
8. The tool wheel's quick use throws the active tool without changing the held item (server-side intent test).

## 5. Beta 1 milestone ("testers can play")

| Area | Content |
|---|---|
| Verbs | Crawl, takedowns, bodies, dragging (S7) |
| Senses | Sight, light, sound (S2, S3, S6) |
| AI | Behaviour trees, search, combat ring, barks, patrols, routines (S4, S5) |
| Tools | The table above |
| Map | A **test compound** (S8): walled camp, gate, two buildings with locked rooms, towers, torches and lanterns, a lamplighter route, day and night patrols, a target to reach, and a starter chest with tools |
| Docs | A one-page **tester guide** (keys, goals, what feedback we want) |

## 6. Later (S12 proper and beyond)

- Rope arrows, grappling hook, climbing claws, stilts (S10 traversal).
- Pickpocketing, water shoes, the "Could" list (decoys, tripwires, listening cup, …).
- Upgrades and hideout crafting (S16).
