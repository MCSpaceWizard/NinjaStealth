# What to test

This is a living list of what's implemented and how to test it. It's updated with every stage. Newest stages are at the top.

**Status legend:**
- 🧪 **Ready to test.** Code is pushed; please build and play.
- ✅ **Verified.** You've confirmed it works.
- 🔧 **Fixing.** Issues were reported and a fix is in progress.
- ⏳ **In progress.** Not ready yet.

**Reporting:** for each item, a quick ✅/❌ plus what you saw is enough. Screenshots help a lot for anything visual. For crashes, send `run/logs/latest.log`.

**Before you start:** build per [GETTING_STARTED.md](GETTING_STARTED.md), run `./gradlew runClient`, and create a **Creative world with cheats on**.

---

## Stage 1: Stealth NPCs & debug view 🧪

**Compile status:** not yet compiled. The first build is the first real check, so please send any build errors.

| # | Feature | How to test | Expected |
|---|---|---|---|
| 1.1 | Creative tab | Open the creative inventory and find the **Emergent Stealth** tab | Contains the **Debug Lens** (magnifier icon) and the **Stealth NPC Spawn Egg** |
| 1.2 | Spawn egg | Use the spawn egg on the ground | An NPC in red-lacquer ashigaru armour, holding an iron sword, appears |
| 1.3 | Spawn command + archetypes | `/es npc spawn emergentstealth:` then press **Tab** | Suggests the six archetypes: `ashigaru`, `samurai`, `taisho`, `townsfolk`, `labourer`, `daimyo` |
| 1.4 | Archetype looks | Spawn one of each | Visibly different outfits (placeholder art): dark samurai armour, red/gold taisho, beige townsfolk kimono, straw-hatted labourer, purple daimyo. Skin tones vary between NPCs of the same type |
| 1.5 | Archetype stats | Hit a samurai and an ashigaru with the same weapon | The samurai takes more hits (40 hp vs 24 hp) |
| 1.6 | Placeholder behaviour | Stand near them | They wander slowly, look at you when close, and look around. No hostility yet (comes in Stage 4) |
| 1.7 | List command | `/es npc list` | A total count plus a count per archetype |
| 1.8 | Debug view toggle | Right-click the **Debug Lens** (or type `/esdebug`) | Action-bar message "debug view: ON" |
| 1.9 | Debug labels | With debug on, look at NPCs | Above each NPC, 3 lines: `archetype [role]`, faction, `state: idle` |
| 1.10 | Debug vision cone | With debug on | A red facing line and a yellow cone outline in front of each NPC that **follows its head** as it looks around |
| 1.11 | Debug off | `/esdebug off` or right-click the lens again | Overlays disappear |
| 1.12 | Persistence | Spawn a few NPCs, then save & quit and reload the world | Same NPCs, same outfits, skin tones and gear. They don't despawn when you walk far away and come back |
| 1.13 | Multiplayer: debug is op-only | `runServer` + `runClient` + `runClient2`. Op only one player (`/op Dev` in the server console). Both toggle debug | The op sees the overlay; the non-op sees **nothing** even with it toggled on |
| 1.14 | Multiplayer: NPCs sync | Same setup, both players near the NPCs | Both see the same outfits and movement |
| 1.15 | Datapack override (optional) | Copy `src/main/resources/data/emergentstealth/emergentstealth/archetype/ashigaru.json` into a world datapack at the same path (`<world>/datapacks/test/data/emergentstealth/emergentstealth/archetype/ashigaru.json`, plus a `pack.mcmeta`). Change `max_health` or `outfit`, then **save & quit and reopen the world** (archetype/outfit files load with the world; `/reload` doesn't pick them up) and spawn a new ashigaru | The new NPC uses the changed values |
| 1.16 | Texture swap (optional) | Replace `textures/entity/npc/outfit/ashigaru_armor.png` with any 64×64 skin PNG, then **F3+T** | The ashigaru armour layer shows the new texture |

## Stage 0: Project scaffold 🧪

| # | Feature | How to test | Expected |
|---|---|---|---|
| 0.1 | Build | `./gradlew build` | `BUILD SUCCESSFUL` |
| 0.2 | Mod loads | `./gradlew runClient` → Mods screen | "Emergent Stealth" is listed |
| 0.3 | Config screen | Mods → Emergent Stealth → Config | Opens; has a HUD section with "Show Light Gem" (does nothing until Stage 3) |
