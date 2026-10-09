# Session handoff

Long sessions get expensive, so work moves to a fresh session regularly. This file is the briefing: a new session reads it (and `CLAUDE.md`) and carries on. **Keep it current at the end of every session.**

## How to start a new session

1. Start a new Claude Code session on this repository.
2. Paste this:

   > Read `CLAUDE.md` and `docs/HANDOFF.md`, then continue the work from "Next up". Develop on a new `claude/...` branch from `main`.

3. If you have new files for Claude (structures, art), commit them to the repo first (see "Incoming files").

## State at handoff (2026-10-09)

- **`main`:** v0.1.0 (Alpha 1) plus PR #1 (toolkit part A, S7 animation framework) and PR #2 (Sumi UI). Two merge breakages on `main` (invalid `en_us.json`, the `[animation]` config nested in `[ui]`) are fixed on `claude/fervent-babbage-jyh6r0`, which also carries the `claude/ui-framework` post-merge fixes.
- **PR #3 merged** (S7 animation finished and checked in the real client).
- **`claude/fervent-babbage-jyh6r0` (PR #4, draft):** the builder's 21 structures and 17 Cherry Grove modules imported (`tools/structures/`), design doc 32 (authoring tools, approved), and the **structure viewer**: Surveyor's Plan → Sumi browser → ghost preview → place / undo, `/es structure list|browse|place|undo`. Checked in the real client. **85 GameTests pass.**
- **Feature branches still to finish** (stopped by usage limits; the last commit is **WIP** and may not compile):

| Branch | What's done | What's left |
|---|---|---|
| `claude/toolkit-b` | Water/fire arrows, blowgun + sleep darts, locks/keys/Locksmith's Kit, lockpicking minigame, spyglass tagging | WIP: GameTests, final tagging fix. Then merge. Also call `Skills.awardInsight(..., INSIGHT_LOCKPICK)` on a successful pick, and read `StealthStats` (`LOCKPICK_WINDOW`, `TAG_COUNT`) |
| `claude/visual-lighting` | Light nuance in progress: sky openness (`SkyCells`), penumbra, smoother falloff | WIP: finish, tests, light gem tiers, docs |

## Next up (the user's priorities, 2026-10-09)

1. **Structures, structure viewer and zone authoring.**
   - ✅ Structures imported; ✅ structure viewer (doc 32 §1).
   - **Next, in doc 32 §5 order:** the compound file format (save and place a multi-module compound with rotation and mirror, per-module ground lines), the zone tool plus the minimal trespass rule, the spawner GUI (Muster Roll), Surveyor's Rope and Compound Ledger, then the first compound: **samurai mini fort**.
   - **Zone tool and compound authoring** (S9 zone design, pulled forward): mark zones (public / restricted / hostile), guard posts and patrol routes (the S5 baton does routes), NPC spawn points with archetypes and schedules, lights, and **save and load a compound as one config file** (datapack JSON + structure).
   - **NPC spawner tool with a GUI** (pick archetype, behaviour, route/post, schedule), built on Sumi.
2. **Toolkit B:** finish and merge (see the table).
3. **Light nuance:** finish and merge.
4. Then **more weapons and armour** for the player, and **NPC work** (distinct placeholder silhouettes per archetype: hats, armour shapes, sashes, banners per [STYLE_GUIDE](art/STYLE_GUIDE.md) §4; extend `tools/programmer_art/generate.py` with part shapes).
5. **Sumi must stay easy to extend and modify** (user, 2026-10-09): keep a short "how to add a screen / widget / theme token" guide current in doc 31, and build new GUIs (tool wheel, spawner) from its widgets rather than one-offs.
6. **Artist support:** keep [TEXTURE_LIST](art/TEXTURE_LIST.md) updated as textures are added.

**Getting files to Claude:** upload a zip in the chat (it lands in the session's uploads) or commit it to `_incoming/`.

## UI direction (user, 2026-10-08)

- **No flat colours** in programmer-art GUI: use texturing (paper grain, brush noise).
- **Or** go fully stylised: a **modern geometric** GUI generated programmatically, with good animation (easing, smooth scrolling, swiping).
- The user is open to either. **Recommendation:** geometric shapes drawn in code, with procedural paper grain, ink accents and an animation system (tweens, inertial scrolling, swipe between tabs). It's easy to generate and theme, and still matches ink-and-paper. Confirm with the user in the UI design pass.
- **Done (Sumi):** went with the recommendation; see [doc 31](design/31-ui-framework.md) §0 and the screenshots in [screenshots/ui](screenshots/ui/). New screens build on `client/ui/` (`UiScreen` + `UiNode` widgets, `Paint` for shapes); side-neutral maths lives in `ui/`.
- **Ghost previews in the world:** filled gizmos (`Gizmos.rect`, the `debug_filled_box` pipeline) cull back faces, so from inside a ghost nothing shows. `GhostPreview` puts the structure in front of you and draws both windings when the camera is inside.
- **Sumi gotchas:** GUI pipelines cull back faces, so `Mesh` normalises quad winding (shapes vanished before). Inside one GUI layer, elements are drawn sorted by pipeline and texture, not in submission order; only overlapping elements are stacked. Keep `en_us.json` valid after merges (the `ui/lang_file_valid` GameTest checks it).

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
- Vanilla only re-fits a changed hitbox after an entity's first tick: test size changes a few ticks after spawning.
- `visual/shadow_matches_gameplay` failed once in about ten runs (2026-10-09); not investigated yet.

**Merging branches:** append-only shared files (`en_us.json`, `textures.json`, `ESConfig`, registries) can merge into something broken without a conflict (a lost comma, a lost `pop()`). After every merge, run the GameTests (`ui/lang_file_valid` parses the lang file strictly) and check `ESConfig` push/pop pairs.

**Real client (animation checks):** `/esphoto <yaw> [pitch]` orbits the third-person camera; `/es npc knockout` makes bodies. Give each concurrent client its own Xvfb display (`XIN_DISPLAY`/`DISPLAY`). A container restart kills Xvfb and the client: check with `pgrep -a Xvfb` before driving it, or the client loops on an early-display y/n prompt and fills the log.

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
| Sumi UI: maths and theme (side-neutral), drawing, widgets, screens | `ui/`, `client/ui/`, `client/ui/widget/`, `client/ui/screen/`; theme `assets/emergentstealth/ui/theme.json` |
| Sound propagation, footsteps, masking, throwing | `stealth/sound/`, `entity/ThrownItem` |
| Player verbs: crawl, takedowns, carrying | `action/` |
| Skills, techniques, stealth stats, gear | `progression/` |
| Commands | `command/` (`/es npc`, `/es patrol`, `/es routine`, `/es skills`, `/esdebug`) |
| Tests | `gametest/` (`ESGameTests` registry + self-registering classes) |
