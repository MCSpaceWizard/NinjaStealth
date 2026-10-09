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

## Worldgen

Each structure also has a jigsaw structure and template pool (`worldgen/structure/edo/`, `worldgen/template_pool/edo/`),
so `/place structure emergentstealth:edo/<name>` works. They are **placement-only** for now: the biome tags
`has_structure/edo_*` are empty and the builder's structure sets stay in `_incoming/worldgen/structure_set/` until the
worldgen stage (S15). `/place template emergentstealth:edo/<name>` places the raw template.

## Adding more structures

1. Put the new `.nbt` files in `_incoming/structures/` (and any worldgen JSON in `_incoming/worldgen/`).
2. Run the import. It stops with "no stand-in for <block>" if a new modded block appears: add it to `block_map.json`.
3. Raise `EXPECTED` in `StructureTests` and run the GameTests.
