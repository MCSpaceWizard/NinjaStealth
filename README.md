# Emergent Stealth

A deep-stealth Minecraft mod (NeoForge 26.1.2):
- light and cast shadows you can put out (Thief)
- a shinobi toolkit (Dishonored)
- social stealth, disguises and localised alerts (Hitman)
- feudal weapons and a hideout with specialised workstations (AC Shadows)

All of it is driven by a custom stealth AI: perception, suspicion, investigation, search, hunting, patrols and alert propagation.

## Status

Stage 0, foundations. See the design docs:
- [Questionnaire & answers](docs/design/00-questionnaire.md)
- [Design decisions + open questions](docs/design/01-design-decisions.md)
- [Staged roadmap](docs/design/02-roadmap.md)
- [Architecture](docs/design/10-architecture.md)
- [Assets & programmer art](docs/assets.md)

## Building & running

Requirements: **JDK 25** (Gradle can auto-provision it via the foojay toolchain resolver if it's missing) and an internet connection for the first build.

```
./gradlew build             # compile + jar → build/libs/emergentstealth-<version>.jar
./gradlew runClient         # dev client
./gradlew runServer         # dedicated server (also checks nothing client-only leaks into common code)
./gradlew runClient2        # second client "Dev2", for local co-op testing against runServer
./gradlew runData           # data generation → src/generated/resources
./gradlew runGameTestServer # headless GameTests (once tests exist; it errors when there are none)
```

On Windows use `gradlew.bat`. In IntelliJ, open the folder as a Gradle project; run configurations are generated on sync.

## In-game (dev) quick reference

- `/es npc spawn <archetype>` spawns a stealth NPC (op). `/es npc list` counts them. `/es` is short for `/emergentstealth`.
- `/esdebug [on|off]` or right-clicking the **Debug Lens** toggles the AI debug view. Only ops and the singleplayer host receive debug data.
- Archetypes and outfits are datapack JSON under `data/<namespace>/emergentstealth/{archetype,outfit}/`.

When reporting a build problem, paste the first error block from the Gradle output (or `build/reports/problems/problems-report.html`).
