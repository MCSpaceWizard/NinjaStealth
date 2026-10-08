# Changelog

## v0.1.0 "Alpha 1" (2026-10-08)

This is the first tagged build of Emergent Stealth: a deep-stealth mod for NeoForge 26.1.2 (Minecraft 26.1.2, Java 25). It is an internal alpha for the team and early testers. All art is programmer art, and NPCs use vanilla poses until the animation work lands.

**Requirements:** NeoForge 26.1.2.112 or later, Java 25. No other mods are required for this build. Sodium and Iris are supported but optional.

**Verification:** 56 automated GameTests pass. The dedicated server boots. Visual features were checked in a real client.

### Stealth NPCs and the debug view
- Human NPCs defined by datapack archetypes (role, faction, bodies, layered outfits, stats, equipment, perception, behaviour). Six archetypes ship: ashigaru, samurai, taisho, daimyo, labourer, townsfolk.
- `/es npc spawn <archetype>`, `/es npc list`, spawn egg.
- An op-only AI debug view (`/esdebug`, or the Debug Lens) shows:
  - vision cones and sight rays
  - awareness, state, cause and the running behaviour action
  - the last noise heard, paths, search points and attack tokens

### Sight and detection
- Central and peripheral vision cones; nothing is seen from behind.
- Multi-point line of sight with partial cover: glass is clear, leaves are partial, and tall grass hides a crawling player.
- Deep water blocks sight.
- A per-NPC awareness meter with a grace period: sprinting is more visible and darkness much less.
- A perception scheduler with level of detail keeps many NPCs affordable.
- Above-head indicators (meter, ?, !) and alert stingers.

### Light and shadow
- A ray-traced exposure model: block lights cast real shadows, carried torches and burning entities are dynamic lights, and the sun and moon cast directional shadows. Moon phase and weather matter.
- The light gem HUD shows how visible you are.
- Snuff torches, lanterns, campfires and candles with an empty hand, and relight them with fire. Guards fetch torches when searching in the dark.
- An op-only light debug view: exposure heatmap, light rays with shadow results, sky breakdown.
- **Visual lighting:** the rendered world uses the same model, so you see the shadows guards see: block-light shadows, held-light glow, sun and moon shadows, and an optional "dark is dark" mode. Works with vanilla, Sodium and Iris (pauses parts of itself when a shader pack is active).

### Behaviour
- **Data-driven behaviour trees** (JSON) per archetype, with a per-NPC override (`/es npc behaviour`).
- **Alert states:** unaware, curious, suspicious, investigating, hunting, searching, combat, fleeing, and heightened alert, which lasts days.
- **Coordinated search:** searchers split up, prefer dark and covered spots, and search longer in larger groups.
- **Attack tokens:** two guards attack at once; the rest surround the target. On Peaceful, guards surround but never attack, following vanilla's rule.
- **Barks** above heads (lang-driven, so writers can add lines) and **shouts** that bring other guards to the shouter, not to you.

### Navigation, patrols and routines
- Guards open and close doors and fence gates and climb ladders.
- Patrol routes with waits, look directions and lamplighter relighting, authored with the Patrol Baton or `/es patrol`.
- Daily schedules (route, post, wander) with shift changes (`/es routine`).

### Hearing and distractions
- **Sound propagation:** through open space and doorways, muffled by walls (light, solid and heavy materials). Masked by rain, thunder, flowing water and jukeboxes.
- **Footsteps** by stance (sneak, walk, sprint, crawl) and surface, plus landing, door, combat and block noises. Chests, item drops and bows are silent.
- **Throw any item (G):** glass shatters and leaves nothing; sharp items nick.
- Guards investigate noises. Hearing alone never fully detects you.

### Player verbs
- **Crawl** toggle (Z).
- **Takedowns:**
  - rear choke (empty-hand right-click) and rear kill (left-click) from behind an unaware NPC
  - air takedowns by landing on an NPC, with no fall damage
  - elites resist rear takedowns
- **Bodies** stay in the world. Knocked-out NPCs never wake by themselves; colleagues wake them and both search.
- **Drag** (right-click) or **carry** (sneak + right-click) bodies. Any right-click puts the body down.
- **Evidence:** guards notice corpses (alarm and search), knocked-out colleagues, stuck arrows and dropped weapons, using the same sight and light rules.

### Progression foundations
- **Skill trees** as datapack JSON (Path of the Shinobi, Path of the Shogunate), with 15 starter skills and capstones reserved for mastery challenges.
- **Insight** earned by stealth deeds (unseen takedowns, distractions, escaped searches, hidden bodies) becomes skill points.
- **Techniques (X; sneak + X to switch):** Still Breath, Light Step, Feint, Iron Focus.
- **Stealth gear:** the shinobi hood, garb, hakama and tabi carry stealth stats; metal armour is louder.
- `/es skills points|unlock|reset`. The skill tree screen comes with the UI framework.

### Documentation
- Design docs per stage in `docs/design/`, a testing guide (`docs/TESTING.md`), a progress report (`docs/PROGRESS.md`), and a session handoff guide (`docs/HANDOFF.md`).
- **For artists:** a style guide (`docs/art/STYLE_GUIDE.md`) and a prioritised texture list (`docs/art/TEXTURE_LIST.md`).
- **Research notes:** shader packs, shippable skins, animation.

### Known limitations
- Programmer art throughout. NPC archetypes differ mostly by colour (distinct silhouettes are planned).
- No animations yet: crawl, takedowns and bodies use vanilla poses.
- No structures, zones or compounds yet. The beta toolkit (smoke, firecrackers, arrows, darts, locks and more) and the UI framework are on feature branches and arrive in the next release.
- Visual lighting has no real-GPU performance numbers yet. Every part can be turned down or off in the client config.
