# 10 — Architecture (Stage 0) 📄 for review

This doc covers how the codebase is organised so every later system slots in the same way. It's deliberately short: each system gets its own doc when its stage starts.

## Platform

- **Versions:** NeoForge **26.1.2**, Java 25, ModDevGradle 2.0.148, Gradle 9.2.1 (wrapper). All pinned in `gradle.properties` / `build.gradle`.
- **Unobfuscated code:** Minecraft 26.1+ ships without obfuscation, so code uses Mojang's real names. No mappings setup is needed.
- **Mixins:** used only where NeoForge has no event or hook, each one documented in a comment explaining why. Access transformers are preferred for plain visibility changes.

## Side model

**The server owns everything that affects gameplay**: perception, light exposure, sound propagation, AI, zones, alerts, disguises, lethality. The client only:
- renders (HUD, indicators, debug overlay, post-processing, animations)
- sends input intents (crawl toggle, radial selection, takedown request), which the server checks

The client never decides whether someone is seen. That's what keeps co-op fair, and it's also why the light gem shows a value **sent from the server**.

**Physical-side safety:** client classes live under `.client` packages. They're only reached from `EmergentStealthClient` (`dist = CLIENT`) or `@EventBusSubscriber(value = Dist.CLIENT)` classes. `./gradlew runServer` is the check that nothing client-only leaks into common code.

## Package layout (grows stage by stage)

```
com.mcspacewizard.emergentstealth
├── EmergentStealth / EmergentStealthClient   entry points
├── config/        ModConfigSpec (SERVER = rules/tuning, CLIENT = presentation only)
├── registry/      DeferredRegisters (items, blocks, entities, attachments, data registries, sounds, …)
├── network/       payload records + handlers (one record per message, StreamCodec-based)
├── data/          datapack registries & JSON codecs (archetypes, outfits, zones, BTs, …)
├── stealth/       shared gameplay math, no AI: exposure/visibility model, noise events, evidence
│   ├── light/     light-source index, exposure ray tracing           (S3)
│   └── sound/     propagation, materials, masking                    (S6)
├── ai/            the NPC brain
│   ├── perception/   sight, hearing, evidence → stimuli              (S2, S6)
│   ├── knowledge/    facts, memory, confidence, sharing              (S4, S9)
│   ├── behavior/     behaviour-tree runtime + nodes                  (S4)
│   ├── squad/        group search, attack tokens, alarms             (S4, S9)
│   ├── nav/          custom pathfinding                              (S5)
│   └── scheduler/    tick budgeting & LOD                            (S2)
├── entity/        StealthNpc and friends                             (S1)
├── player/        player state: stance, stamina, disguise, lethality, tool belt
├── world/         zones, alert levels, compounds (SavedData), worldgen
├── item/ block/   content
├── compat/        optional integrations, each isolated (curios/, emi/, jade/, shouldersurfing/, …)
└── client/        renderers, HUD, screens, debug overlay, post-processing
```

## Core patterns

- **Data-driven first** [T-07]. Archetypes, outfits, zones' default rules, behaviour trees, noise and light tuning, and loot are **datapack registries** with Codecs. Code defines *kinds* of things; data defines the actual *instances*. Map makers override files under `data/emergentstealth/...`.
- **Tags for block behaviour.** `#emergentstealth:unclimbable`, `#…:see_through`, `#…:partial_cover`, `#…:extinguishable`, `#…:quiet_surface`, `#…:loud_surface`, and so on. Packs can retune blocks from other mods without any code.
- **State storage:**
  - Per-entity and per-player state goes in **NeoForge data attachments** (serialised and synced where needed).
  - Per-world state (zones, alert levels, compound status, light-source index) goes in **SavedData**.
  - Timers store **game-time timestamps**, not countdowns. Cooldowns then "keep ticking" while chunks are unloaded [A-11].
- **Tick budget & LOD** [T-08]. A central scheduler spreads perception work across ticks:
  - **Tier 1:** near a player, up to ~20 NPCs. Full rays, every few ticks.
  - **Tier 2:** further away. Fewer rays, lower frequency, still reacts to alarms.
  - **Tier 3:** far or unloaded. Routine-only, simulated by timestamps.
  - Budgets are server config values and are visible in the debug overlay.
- **Events over coupling.** Systems talk through small internal events (`NoiseEmitted`, `StimulusPerceived`, `KnowledgeShared`, `LightChanged`, …). Light, sound and AI then stay independently testable.
- **Debug as a first-class feature** [A-14]. Every AI-facing system publishes to the debug channel, which is op-gated by `debugToolsRequireOp`.

## Assets

- Every texture and model sits at its final path from day one. Placeholders come from `tools/programmer_art` and are never overwritten. See [`docs/assets.md`](../assets.md).
- Sounds: CC0 sources, credited in `docs/credits.md` (created when the first sound lands).

## Testing

- **GameTests** (`./gradlew runGameTestServer`) for deterministic behaviour: line of sight through glass, awareness rates, noise through a closed door, pathing through a locked door.
- **Unit tests** for pure math (exposure, propagation, awareness curves), kept free of Minecraft classes where possible.
- **Manual playtest checklist** per stage, in the PR description.
- **Co-op:** `runServer` + `runClient` + `runClient2` (second dev account "Dev2").

## Things to decide in later docs (not now)

- A UI widget layer vs. a library (S4 for the HUD, S12 for the radial wheel).
- How the behaviour-tree JSON format is written (S4).
- The approach for the visual shadow renderer (Track E R&D).
