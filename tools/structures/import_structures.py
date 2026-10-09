"""Imports the builder's structures into the mod (run from the repo root).

    pip install nbtlib
    python3 tools/structures/import_structures.py

Reads the untouched originals in _incoming/structures/*.nbt and writes game-ready copies to
src/main/resources/data/emergentstealth/structure/edo/. For each structure it:
- swaps modded and valuable blocks for stand-ins (tools/structures/block_map.json),
- removes spawners and hostile mobs (guards come from compound authoring instead),
- points chests at our loot tables (emergentstealth:chests/edo/...).

The DataVersion is left as the builder saved it (1.20.1), so the game's own data fixer upgrades vanilla blocks
on load. Rerun after editing block_map.json; the originals are never modified.
"""
import json
import os
import sys

import nbtlib
from nbtlib import Compound, Int, List, String

SRC = '_incoming/structures'
OUT = 'src/main/resources/data/emergentstealth/structure/edo'
MAP = 'tools/structures/block_map.json'

HOSTILE = {'minecraft:evoker', 'minecraft:vindicator', 'minecraft:pillager', 'minecraft:ravager', 'minecraft:illusioner',
           'minecraft:witch', 'minecraft:zombie', 'minecraft:skeleton', 'minecraft:spider', 'minecraft:creeper',
           'minecraft:silverfish', 'minecraft:vex'}
LOOT = {
    'edo_japan:chest/japanese': 'emergentstealth:chests/edo/japanese',
    'edo_japan:chest/japanese_rare': 'emergentstealth:chests/edo/japanese_rare',
    'edo_japan:chest/shrine': 'emergentstealth:chests/edo/shrine',
    'edo_japan:chest/farming': 'emergentstealth:chests/edo/farming',
    'edo_japan:chest/exterior': 'emergentstealth:chests/edo/exterior',
    'ancientstructures:chest/japanese': 'emergentstealth:chests/edo/japanese',
}
SIDES = ('north', 'east', 'south', 'west')


def apply_rule(rule, props):
    """Returns (name, properties) for a source state's properties under one block_map entry."""
    if isinstance(rule, str):
        return rule, {}
    for case in rule.get('cases', []):
        if all(props.get(k) == v for k, v in case['if'].items()):
            return apply_rule(case['then'], props)
    out = {}
    for key in rule.get('copy', []):
        if key in props:
            out[key] = props[key]
    for src, dst in rule.get('rename', {}).items():
        if src in props:
            out[dst] = props[src]
    if rule.get('axis_from_flags'):
        # Beams: axis_x / axis_y / axis_z flags -> one log axis (vertical wins).
        out['axis'] = next((a for a in ('y', 'x', 'z') if props.get('axis_' + a) == 'true'), 'y')
    if rule.get('connections_from_wall'):
        for side in SIDES:
            out[side] = 'false' if props.get(side, 'none') == 'none' else 'true'
    if rule.get('bars_from_facing'):
        facing = props.get('facing', 'north')
        along = ('east', 'west') if facing in ('north', 'south') else ('north', 'south')
        for side in SIDES:
            out[side] = 'true' if side in along else 'false'
    out.update(rule.get('set', {}))
    return rule['name'], out


def convert(path, block_map, report):
    nbt = nbtlib.load(path)
    palette = nbt['palette']
    new_palette = []
    index = {}
    remap = []
    changed_names = []
    for state in palette:
        name = str(state['Name'])
        props = {str(k): str(v) for k, v in state.get('Properties', {}).items()}
        if name in block_map:
            new_name, new_props = apply_rule(block_map[name], props)
        elif not name.startswith('minecraft:'):
            raise SystemExit(f'{path}: no stand-in for {name}; add it to {MAP}')
        else:
            new_name, new_props = name, props
        key = (new_name, tuple(sorted(new_props.items())))
        if key not in index:
            index[key] = len(new_palette)
            entry = Compound({'Name': String(new_name)})
            if new_props:
                entry['Properties'] = Compound({k: String(v) for k, v in new_props.items()})
            new_palette.append(entry)
        remap.append(index[key])
        changed_names.append(new_name != name)

    blocks = []
    removed_spawners = 0
    for block in nbt['blocks']:
        old = int(block['state'])
        block['state'] = Int(remap[old])
        if 'nbt' in block:
            if changed_names[old]:
                del block['nbt']  # a different block now: it makes its own block entity if it needs one
                removed_spawners += str(palette[old]['Name']) == 'minecraft:spawner'
            elif 'LootTable' in block['nbt']:
                table = str(block['nbt']['LootTable'])
                if table not in LOOT:
                    raise SystemExit(f'{path}: unknown loot table {table}')
                block['nbt']['LootTable'] = String(LOOT[table])
        blocks.append(block)
    nbt['blocks'] = List[Compound](blocks)
    nbt['palette'] = List[Compound](new_palette)

    kept = [e for e in nbt.get('entities', []) if str(e['nbt'].get('id', '')) not in HOSTILE]
    stripped = len(nbt.get('entities', [])) - len(kept)
    nbt['entities'] = List[Compound](kept)

    out = os.path.join(OUT, os.path.basename(path))
    nbt.save(out, gzipped=True)
    report.append(f'{os.path.basename(path)}: {len(palette)} -> {len(new_palette)} states, '
                  f'{stripped} mobs and {removed_spawners} spawners removed')


def main():
    with open(MAP) as f:
        block_map = {k: v for k, v in json.load(f).items() if not k.startswith('_')}
    os.makedirs(OUT, exist_ok=True)
    report = []
    for name in sorted(os.listdir(SRC)):
        if name.endswith('.nbt'):
            convert(os.path.join(SRC, name), block_map, report)
    print('\n'.join(report))


if __name__ == '__main__':
    sys.exit(main())
