# Session handoff

Long sessions get expensive, so work moves to a fresh session regularly. This file is the briefing: a new session reads it (and `CLAUDE.md`) and carries on. **Keep it current at the end of every session.**

## How to start a new session

1. Start a new Claude Code session on this repository.
2. Paste this:

   > Read `CLAUDE.md` and `docs/HANDOFF.md`, then continue the work from "Next up". Develop on a new `claude/...` branch from `main`.

3. If you have new files for Claude (structures, art), commit them to the repo first (see "Incoming files").

## State at handoff (2026-10-08)

- **`main` / release `v0.1.0` (Alpha 1):** stages S0–S7 (server side), S6 hearing, progression foundations and visual lighting. **56 GameTests pass.** Release notes: [CHANGELOG.md](../CHANGELOG.md).
- **Unfinished feature branches.** Agents were stopped by API usage limits; each branch's last commit is marked **WIP** and may not compile:

| Branch | What's done | What's left |
|---|---|---|
| `claude/toolkit-a` | **Merged into `claude/fervent-babbage-jyh6r0`** (tests green, 64). Wheel (R), quick use (V), particles, recipes, tests and the Thick Smoke skill hook are in | Play-test (TESTING.md "Beta toolkit, part A"), then a PR to `main` |
| `claude/toolkit-b` | Water/fire arrows, blowgun + sleep darts, locks/keys/Locksmith's Kit, lockpicking minigame, spyglass tagging | WIP: GameTests, final tagging fix. Then merge. Also call `Skills.awardInsight(..., INSIGHT_LOCKPICK)` on a successful pick, and read `StealthStats` (`LOCKPICK_WINDOW`, `TAG_COUNT`) |
| `claude/s7-animation` | Started: PAL dependency, pose framework, `NpcModel` | Most of doc 17 §8 / research-animation: body poses, crawl cycle, drag/carry poses, takedown clips, camera turning. Clip time must scale to `ActionPlayback.length` (takedown speed skills shorten it) |
| `claude/visual-lighting` | Light nuance in progress: sky openness (`SkyCells`), penumbra, smoother falloff | WIP: finish, tests, light gem tiers, docs |
| *(not started)* `claude/ui-framework` | Brief in doc 31 | Whole Sumi framework + config screen + skill tree (K) + dialogue preview. See "UI direction" below |

## Next up (the user's latest requests, in priority order)

1. **Finish and merge** toolkit A and B, then animation, light nuance and UI, each with tests green.
2. **Structures from the builder.** The user's builder made `.nbt` structures and worldgen files, currently on the user's PC (`G:\Coding\EmergentStealthMCMod\_incoming`). **Ask the user to commit them to `_incoming/` in the repo** (a cloud session can't read local drives).
   - Then double-check them: NBT validity, data version, block palette, size, jigsaw/worldgen JSON correctness.
   - Move them into `data/emergentstealth/structure/` and `worldgen/`.
3. **Structure placement tool:** an item or command to preview and place any of our structures (rotation, mirror, ghost preview), for browsing the builder's work.
4. **Zone tool and compound authoring** (S9 zone design, pulled forward). The aim is building full compounds:
   - mark zones (public / restricted / hostile)
   - place guard posts and patrol routes (the S5 baton already does routes)
   - NPC spawn points with archetypes and schedules
   - lights
   - **save and load a compound as one config file** (datapack JSON + structure) so it can be re-placed anywhere
5. **NPC spawner tool with a GUI:** pick an archetype, behaviour, route/post and schedule, then place. Built on Sumi.
6. **Distinct NPC placeholders:** archetypes currently differ only by colour. Give each a distinct silhouette and value in the programmer art (hats, armour shapes, sashes, banners) per [STYLE_GUIDE](art/STYLE_GUIDE.md) §4. Extend `tools/programmer_art/generate.py` with part shapes.
7. **Artist support:** [STYLE_GUIDE](art/STYLE_GUIDE.md) and the ordered [TEXTURE_LIST](art/TEXTURE_LIST.md) exist. Keep the list updated as textures are added.

## UI direction (user, 2026-10-08)

- **No flat colours** in programmer-art GUI: use texturing (paper grain, brush noise).
- **Or** go fully stylised: a **modern geometric** GUI generated programmatically, with good animation (easing, smooth scrolling, swiping).
- The user is open to either. **Recommendation:** geometric shapes drawn in code, with procedural paper grain, ink accents and an animation system (tweens, inertial scrolling, swipe between tabs). It's easy to generate and theme, and still matches ink-and-paper. Confirm with the user in the UI design pass.

## Working conventions (learned the hard way)

**Build and test:**
- **Setup:** `bash scripts/cloud-setup.sh` (JDK 25 at `/opt/jdk25`, plus a Maven Central mirror, since Central returns 429 in the cloud).
- **Before every push:** `./gradlew runGameTestServer` must pass. Run `./gradlew runServer` to check that no client classes leak onto the server.
- **Real client:** follow `tools/client_automation/README.md`.
  - Start Xvfb yourself with `nohup Xvfb :99 ...`. Don't detect it with `pgrep -f "Xvfb :99"`; that pattern matches its own shell.
  - Stop **your own** JVM by PID (check `/proc/<pid>/cwd`), never with `pkill` by pattern.
  - Software rendering is slow: 2–10 minutes to join a world.

**GameTest gotchas:**
- NPCs only perceive near a **targetable player** (`PerceptionScheduler` tiers). A test about what a guard notices on its own needs a player nearby but hidden (see `VerbTests.hiddenObserver`); otherwise it only passes when another test's player happens to be close.
- A batch's tests run side by side, and alarmed guards shout across arenas. Tests that need a calm guard use `isolated(...)` in `ESGameTests` (their own batch).
- The test server is on **Peaceful**, where mobs can't target players. Combat tests set Normal difficulty.
- Test players come from `TestPlayers.spawn`. They aren't ticked by the server, so tick logic must be callable directly (e.g. `BodyCarrying.tick`, `Takedowns.checkAirTakedown`).
- `GameTestHelper.relativePos` is buggy in 26.1.2: subtract `absolutePos(BlockPos.ZERO)` instead.

**Code conventions:**
- **Payloads:** always use `ESNetwork.sendIfSupported`. Attachment syncs use the `hasMod` predicate. Players without our channel would otherwise crash the server.
- **Shared design contracts:** small API stubs are committed first (sound API, S7 attachments, progression data), then parallel agents build against them, each on its own branch, registering from their own classes and appending only to shared files.
- **Agents and usage limits:** tell agents to **commit and push increments**, since usage limits stop them mid-task. To rescue work, commit their worktree as WIP and push.

**Repo rules:**
- Docs: one design doc per stage in `docs/design/` (the user reviews it). Keep `docs/TESTING.md`, `docs/GETTING_STARTED.md` and `docs/PROGRESS.md` updated.
- Never overwrite existing PNGs (they may be real art).
- No model names in the repo.
- Avoid GitHub Actions.
- One PR per stage. The user asked for releases on `main` with full notes and no emoji.

## Map of the code

| Area | Where |
|---|---|
| NPC entity, bodies, behaviour lookup | `entity/StealthNpc` |
| Perception (sight, evidence), scheduler | `ai/perception/` |
| Brain (knowledge, alert states), barks | `ai/brain/` |
| Behaviour trees (JSON) | `ai/behaviour/`, data `behaviour/*.json` |
| Search groups, attack tokens | `ai/group/` |
| Navigation, patrols, routines | `ai/nav/`, `ai/routine/` |
| Light model (server), visual lighting (client) | `stealth/light/`, `client/light/` |
| Sound propagation, footsteps, masking, throwing | `stealth/sound/`, `entity/ThrownItem` |
| Player verbs: crawl, takedowns, carrying | `action/` |
| Skills, techniques, stealth stats, gear | `progression/` |
| Commands | `command/` (`/es npc`, `/es patrol`, `/es routine`, `/es skills`, `/esdebug`) |
| Tests | `gametest/` (`ESGameTests` registry + self-registering classes) |
