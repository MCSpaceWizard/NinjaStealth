# Builder structures

The builder's structures arrive in `_incoming/` (untouched originals, the source of truth) and are imported into the mod
by a script. Never edit the generated files under `src/main/resources/data/emergentstealth/structure/edo/` by hand: change
the originals or the mapping and rerun the import.

```bash
pip install nbtlib
python3 tools/structures/import_structures.py
./gradlew runGameTestServer   # structures/edo_imports_load checks every structure
```

## What the import does

- **Blocks:** the originals use Dawn of Time: Builder Edition, Redden's Stone Lanterns and Amendments, none of which exist for
  26.1 (Dawn of Time's latest is 1.21.11). `block_map.json` maps each of their blocks to a vanilla stand-in, keeping
  orientation (stairs, slabs, logs, fences, doors, beds). When our own feudal blocks exist (doc 01: roof tiles, shoji, tatami,
  timber frames, stone lanterns), point the entries at them and rerun.
- **Valuables:** netherite, diamond, emerald, gold and iron blocks become plain look-alikes.
- **Mobs and spawners:** hostile mobs (the mini fort had 46 illagers) and spawners are removed; infested cobblestone becomes
  cobblestone. Guards come from compound authoring instead. Armour stands and the grand Buddha's salmon stay.
- **Loot:** chests point at `emergentstealth:chests/edo/{japanese, japanese_rare, shrine, farming, exterior}`
  (placeholder contents).
- **Data version:** left at the builder's 1.20.1 (3465); the game upgrades vanilla blocks on load.

## Cherry Grove modules (schematics)

17 modular buildings delivered as Sponge `.schem` files (2026-10-09), all vanilla blocks, saved for 26.1.2. They're meant
to be pieced together into large compounds.

```bash
python3 tools/structures/import_schematics.py   # _incoming/schematics/*.schem -> structure/cherrygrove/<name>.nbt
```

- The `cherrygrove_` prefix is dropped (`cherrygrove_pagoda.schem` becomes `emergentstealth:cherrygrove/pagoda`), and the
  misnamed `cherrygrove_grandestate,schem` is read too.
- Air is kept, like a structure block saves it, so a placed module clears its box.
- **Each module includes its own ground and water** (moats, ponds, stone islands) over its whole footprint. Place it with
  that base sunk to ground level; the compound tools (doc 32) need a per-module ground line.
- The exporter wrote `minecraft:chain` with a 26.1 data version, so the game wouldn't rename it to `iron_chain`;
  `block_map.json` does. The GameTest names any block that turns into air, which is how this was found.
- No block entities or entities, so no loot yet. The script stops if a future schematic has any, until it's extended.

## Sites (generated)

`emergentstealth:sites/…` are not imported: `tools/compounds/sites.py` generates them (ground, foundations, terraces,
walls, gates, stairs) as the base a large compound's modules stand on. Edit the script and rerun it rather than editing
the `.nbt`; see doc 32 and [doc 33](../../docs/design/33-compound-terrain.md).

## Worldgen

Each structure also has a jigsaw structure and template pool (`worldgen/structure/edo/`, `worldgen/template_pool/edo/`),
so `/place structure emergentstealth:edo/<name>` works. They are **placement-only** for now: the biome tags
`has_structure/edo_*` are empty and the builder's structure sets stay in `_incoming/worldgen/structure_set/` until the
worldgen stage (S15). `/place template emergentstealth:edo/<name>` places the raw template.

## Viewing and placing

In game, the **Surveyor's Plan** (or `/es structure browse`) lists every structure with a ghost preview, and
`/es structure place|undo` place one with rotation and mirror (doc 32 §1, `authoring/` and `client/authoring/`). The
ghost and the server use the same template transform, so what you see is what's placed.

## Adding more structures

1. Put the new `.nbt` files in `_incoming/structures/` (and any worldgen JSON in `_incoming/worldgen/`).
2. Run the import. It stops with "no stand-in for <block>" if a new modded block appears: add it to `block_map.json`.
3. Raise the folder's count in `StructureTests.EXPECTED` and run the GameTests.
