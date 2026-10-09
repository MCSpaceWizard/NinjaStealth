# Session handoff

Long sessions get expensive, so work moves to a fresh session regularly. This file is the briefing: a new session reads it (and `CLAUDE.md`) and carries on. **Keep it current at the end of every session.**

## How to start a new session

1. Start a new Claude Code session on this repository.
2. Paste this:

   > Read `CLAUDE.md` and `docs/HANDOFF.md`, then continue the work from "Next up". Develop on a new `claude/...` branch from `main`.

3. If you have new files for Claude (structures, art), commit them to the repo first (see "Incoming files").

## State at handoff (2026-10-09)

- **`main`** (after PR #4): v0.1.0 (Alpha 1), toolkit part A, S7 animation (crawl, bodies, drag, carry, takedowns), the Sumi UI, the builder's structures and the Cherry Grove modules, the **structure viewer** and **compounds** (doc 32 §1 and §3, `/es structure ...`, `/es compound ...`). All checked in the real client. **88 GameTests pass**; the dedicated server starts clean.
- **`claude/project-thread-2f8uv4`** (not merged yet): the **zone tool and trespass rule** (doc 32 §2, §5 step 3). The Surveyor's Rope item (two corners make a zone, sneak adds a box, use in the air opens a Sumi zone panel), tinted zone volumes while held, `/es zone ...`, `/es compound add zone <zone>`, and the minimal rule: restricted doubles awareness gain and guards bark a warning, hostile turns notice into detection, hours respected. 4 new `zones/*` GameTests; see TESTING.md "Zones". Not yet checked in the real client (Z.1–Z.11 there).
- **`claude/project-thread-8nzno9`** (on top of the zone branch, not merged yet): the **Muster Roll** (NPC spawner panel, doc 32 §4) and the **Compound Ledger** (compound authoring by item, §3). Zones and routes copied into a draft stay linked and are re-copied on save; NPCs a draft recorded aren't recorded twice. 3 new `authoring/*` GameTests (95 pass); both panels checked in the real client (screenshots in `docs/screenshots/authoring/`). TESTING.md rows M.1–M.5, L.1–L.7.
- **`claude/project-thread-5a6xgb`** (from main after PR #8, not merged yet): **toolkit B finished** (ported from `claude/toolkit-b` onto current main; Shinobi Insight on a successful pick, Nimble Fingers widens the lockpick window, Keen Eye adds a spyglass tag), the **toolbelt** (8-slot item the wheel and quick use draw from; `tool/Toolbelt`, `ToolbeltMenu`, `client/tool/ToolbeltScreen`), the **tool wheel on Sumi** (`UiRadial` widget, maths in `ui/RadialMenu`), the lockpick ring, active-tool icon and spyglass ring on Sumi, and **tool effects** (doc 34: steam, embers, dizzy stars, drowsy marks, smoke core, crackle, glints, tag ping). 107 GameTests pass; checked in the real client (screenshots in `docs/screenshots/tools/`). TESTING.md rows B.1–B.8, W.1–W.7, E.1–E.9, T.1–T.10.
- **Q5 answered (2026-10-09):** the player belongs to neither faction; most Shinobi and Shogunate are enemies, some of each may help (doc 01 §8).
- **Docs:** [docs/sumi-guide.md](sumi-guide.md) (extending Sumi), [doc 32](design/32-authoring-tools.md) (authoring tools; progress at the top). PROGRESS.md is out of date (2026-10-08).
- **Feature branches still to finish** (stopped by usage limits; the last commit is **WIP** and may not compile):

| Branch | What's done | What's left |
|---|---|---|
| `claude/visual-lighting` | Light nuance in progress: sky openness (`SkyCells`), penumbra, smoother falloff | WIP: finish, tests, light gem tiers, docs |

## Next up (the user's priorities, 2026-10-09)

1. **Structures, structure viewer and zone authoring.**
   - ✅ Structures imported; ✅ structure viewer (doc 32 §1); ✅ compound format, save and place (§3, by command); ✅ zone tool (Surveyor's Rope) and minimal trespass rule (§2, branch `claude/project-thread-2f8uv4`: get it checked in the real client and merged).
   - ✅ Muster Roll (spawner GUI) and Compound Ledger (branch `claude/project-thread-8nzno9`). The first compound, **samurai mini fort**, is being built on another thread.
   - **Next (the user, 2026-10-09: authoring comes before more items):** compound leftovers: re-save edited templates on save, `/es compound reset`, automatic ground lines, compounds listed in the structure browser, `lights` / lamplighter relight in the file. Muster Roll leftovers: an archetype colour swatch (archetypes have no colour field), editing a *placed* copy's spawns.
   - Zone leftovers: zones have no outfit rules yet (S11); the trespass bark has no cooldown beyond the usual 2 s bark gap.
   - **Zone tool and compound authoring** (S9 zone design, pulled forward): mark zones (public / restricted / hostile), guard posts and patrol routes (the S5 baton does routes), NPC spawn points with archetypes and schedules, lights, and **save and load a compound as one config file** (datapack JSON + structure).
   - **NPC spawner tool with a GUI** (pick archetype, behaviour, route/post, schedule), built on Sumi.
2. **Toolkit B:** ✅ finished on `claude/project-thread-5a6xgb` with the toolbelt, the Sumi tool wheel and tool effects (doc 34); `claude/toolkit-b` is superseded. Left: check it in the real client and merge.
3. **Light nuance:** finish and merge.
4. Then **more weapons and armour** for the player, and **NPC work** (distinct placeholder silhouettes per archetype: hats, armour shapes, sashes, banners per [STYLE_GUIDE](art/STYLE_GUIDE.md) §4; extend `tools/programmer_art/generate.py` with part shapes).
5. **Sumi must stay easy to extend and modify** (user, 2026-10-09): keep [docs/sumi-guide.md](sumi-guide.md) (how to add a screen, widget or theme token) current, and build new GUIs (tool wheel, spawner) from its widgets rather than one-offs. Cleanups found while writing the guide, worth doing when touching Sumi:
   - a panel-centring helper (every screen writes its own anonymous `UiNode`)
   - builder chaining: `size`/`flex`/`tooltip` return an unchecked `T`, `UiFlex.gap/padding/center` return `UiFlex`
   - consistency: `enabledWhen` on every input widget (toggle, slider and cycle take a fixed `editable`), theme tokens rather than raw ARGB (`UiProgressBar`, `UiTabBar` accents, `DialogueScreen.guardPortrait`), widget heights from theme metrics
   - `SumiTextures.invalidate()` is never called, so F3+T probably keeps the cached paper and ink
   - `UiTextField` has no caret movement, selection or paste
6. **Artist support:** keep [TEXTURE_LIST](art/TEXTURE_LIST.md) updated as textures are added.

**Getting files to Claude:** upload a zip in the chat (it lands in the session's uploads) or commit it to `_incoming/`.

## UI direction (user, 2026-10-08)

- **No flat colours** in programmer-art GUI: use texturing (paper grain, brush noise).
- **Or** go fully stylised: a **modern geometric** GUI generated programmatically, with good animation (easing, smooth scrolling, swiping).
- The user is open to either. **Recommendation:** geometric shapes drawn in code, with procedural paper grain, ink accents and an animation system (tweens, inertial scrolling, swipe between tabs). It's easy to generate and theme, and still matches ink-and-paper. Confirm with the user in the UI design pass.
- **Done (Sumi):** went with the recommendation; see [doc 31](design/31-ui-framework.md) §0 and the screenshots in [screenshots/ui](screenshots/ui/). New screens build on `client/ui/` (`UiScreen` + `UiNode` widgets, `Paint` for shapes); side-neutral maths lives in `ui/`.
- **Compounds:** `Transform` (mirror, then rotate, pivot at zero) is the one place turning happens: modules compose with `then`, and waypoints, posts, facings and zone boxes go through it too. Vanilla spells the half turn `180`, not `clockwise_180`.
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
- `verbs/knocked_out_gets_woken` failed once in about fifteen runs (2026-10-09, the guard was still investigating evidence at tick 602); not investigated yet.
- `toolkit/firecracker_draws_guard` failed once in five runs (2026-10-09: the guard had no HEARD cause at tick 402, after the bangs); it does not touch the toolbelt or quick use. Not investigated yet.
- A fresh test player's **head** yaw is random and `snapTo` only sets the body: tests that use `getViewVector` must `setYHeadRot` too (the spyglass test failed about one run in three until it did).

**Merging branches:** append-only shared files (`en_us.json`, `textures.json`, `ESConfig`, registries) can merge into something broken without a conflict (a lost comma, a lost `pop()`). After every merge, run the GameTests (`ui/lang_file_valid` parses the lang file strictly) and check `ESConfig` push/pop pairs.

**Real client (animation checks):** `/esphoto <yaw> [pitch]` orbits the third-person camera; `/es npc knockout` makes bodies. Give each concurrent client its own Xvfb display (`XIN_DISPLAY`/`DISPLAY`). A container restart kills Xvfb and the client: check with `pgrep -a Xvfb` before driving it, or the client loops on an early-display y/n prompt and fills the log.

**Zones:** `Zone.Access` is ordered from lenient to strict and `Trespass` relies on that order. Zones are per-dimension world data shared by every GameTest in a batch, so tests use unique names (`ZoneTests.name`) and remove them in `finally`. Perception looks up the zone at the *player's* block on every sighting; `Trespass.accessAt` returns early when a level has no zones.

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
| Authoring: structures, compounds, zones and the trespass rule | `authoring/` (server), `client/authoring/` (browser, ghost preview, zone drawing and panel, Muster Roll and Ledger panels), `item/SurveyorsRopeItem`, `MusterRollItem`, `CompoundLedgerItem` |
| Commands | `command/` (`/es npc`, `/es patrol`, `/es routine`, `/es skills`, `/es structure`, `/es compound`, `/es zone`, `/esdebug`) |
| Tests | `gametest/` (`ESGameTests` registry + self-registering classes) |
