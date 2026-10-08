# Emergent Stealth — notes for Claude

**New session? Read `docs/HANDOFF.md` first:** current state, unfinished branches, next tasks and hard-won gotchas.

- NeoForge **26.1.2**, Java 25, ModDevGradle. Unobfuscated Mojang names (`Identifier`, not `ResourceLocation`). Mod ID `emergentstealth`, package `com.mcspacewizard.emergentstealth`.
- Design source of truth: `docs/design/01-design-decisions.md` (decisions + open questions) and `docs/design/02-roadmap.md` (stages). Architecture rules: `docs/design/10-architecture.md`. Read them before starting a stage.
- Every stage starts with a short design doc in `docs/design/` that the user reviews (Z-03). Subsystems inside an approved doc can proceed autonomously.
- Server-authoritative gameplay. The client only renders and sends intents. Keep client classes in `.client` packages.
- Data-driven first (datapack registries + tags). Timers are game-time timestamps.
- Every new texture: add a spec to `tools/programmer_art/textures.json` and run the generator. Never overwrite existing PNGs (they may be real art).
- **Keep `docs/TESTING.md` updated with every stage** (feature table, test steps, status) and `docs/GETTING_STARTED.md` when build or setup changes.
- Cloud sessions: `bash scripts/cloud-setup.sh` installs JDK 25. Building needs the network allowlist in `docs/GETTING_STARTED.md` §6; without it cloud sessions **cannot build** (NeoForge/Mojang mavens are blocked). The user builds locally and reports errors. To check APIs, shallow-clone `https://github.com/neoforged/NeoForge` (branch `26.1.x`) and the MDK `https://github.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle` into the scratchpad.
- Visual checks: `tools/client_automation/README.md` runs the real client on Xvfb with typed commands + screenshots. GameTests: `./gradlew runGameTestServer` (all must pass before pushing).
- Avoid GitHub Actions usage (cost). One PR per stage; a branch per concurrent feature.
