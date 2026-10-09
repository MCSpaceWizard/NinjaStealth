"""Converts Sponge schematics (.schem, from WorldEdit or Litematica's export) into structure templates (run from the
repo root).

    pip install nbtlib
    python3 tools/structures/import_schematics.py

Reads _incoming/schematics/*.schem (untouched originals) and writes
src/main/resources/data/emergentstealth/structure/cherrygrove/<name>.nbt, dropping the "cherrygrove_" prefix.
Air is kept, like a structure block saves it, so a placed building clears what was in its box. The same block map as
the builder's structures applies (tools/structures/block_map.json), in case a schematic uses modded or valuable blocks.
"""
import json
import os
import re
import sys

import nbtlib
from nbtlib import Compound, Int, List, String

SRC = '_incoming/schematics'
OUT = 'src/main/resources/data/emergentstealth/structure/cherrygrove'
MAP = 'tools/structures/block_map.json'
PREFIX = 'cherrygrove_'

sys.path.insert(0, os.path.dirname(__file__))
from import_structures import apply_rule  # noqa: E402  same mapping rules as the builder's structures


def parse_state(text):
    """'minecraft:oak_stairs[facing=east,half=top]' -> (name, {facing: east, half: top})."""
    match = re.fullmatch(r'([^\[]+)(?:\[(.*)\])?', text)
    name, props = match.group(1), match.group(2)
    return name, dict(p.split('=', 1) for p in props.split(',')) if props else {}


def varints(data):
    out, i = [], 0
    while i < len(data):
        value, shift = 0, 0
        while True:
            b = data[i]
            i += 1
            value |= (b & 0x7F) << shift
            shift += 7
            if not b & 0x80:
                break
        out.append(value)
    return out


def convert(path, block_map):
    root = nbtlib.load(path)
    root = root['Schematic'] if 'Schematic' in root else root
    version = int(root.get('Version', 2))
    width, height, length = int(root['Width']), int(root['Height']), int(root['Length'])
    if version >= 3:
        palette, data = root['Blocks']['Palette'], root['Blocks']['Data']
        block_entities = root['Blocks'].get('BlockEntities', [])
    else:
        palette, data = root['Palette'], root['BlockData']
        block_entities = root.get('BlockEntities', [])
    if len(block_entities) or len(root.get('Entities', [])):
        raise SystemExit(f'{path}: block entities or entities need converting too; extend this script')

    states = {}
    for text, index in palette.items():
        name, props = parse_state(str(text))
        if name in block_map:
            name, props = apply_rule(block_map[name], props)
        elif not name.startswith('minecraft:'):
            raise SystemExit(f'{path}: no stand-in for {name}; add it to {MAP}')
        states[int(index)] = (name, tuple(sorted(props.items())))

    new_palette, palette_index, blocks = [], {}, []
    values = varints(bytes(data))
    assert len(values) == width * height * length, f'{path}: {len(values)} blocks for {width}x{height}x{length}'
    for i, value in enumerate(values):
        key = states[value]
        if key not in palette_index:
            palette_index[key] = len(new_palette)
            entry = Compound({'Name': String(key[0])})
            if key[1]:
                entry['Properties'] = Compound({k: String(v) for k, v in key[1]})
            new_palette.append(entry)
        x = i % width
        z = (i // width) % length
        y = i // (width * length)
        blocks.append(Compound({'pos': List[Int]([Int(x), Int(y), Int(z)]), 'state': Int(palette_index[key])}))

    template = nbtlib.File({
        'DataVersion': Int(int(root.get('DataVersion', 0))),
        'size': List[Int]([Int(width), Int(height), Int(length)]),
        'palette': List[Compound](new_palette),
        'blocks': List[Compound](blocks),
        'entities': List[Compound]([]),
    })
    name = os.path.basename(path)
    name = re.sub(r'[,.]schem$', '', name)  # one original is named "…,schem"
    name = name[len(PREFIX):] if name.startswith(PREFIX) else name
    out = os.path.join(OUT, name + '.nbt')
    template.save(out, gzipped=True)
    return f'{name}: {width}x{height}x{length}, {len(new_palette)} states, {os.path.getsize(out) // 1024} KB'


def main():
    with open(MAP) as f:
        block_map = {k: v for k, v in json.load(f).items() if not k.startswith('_')}
    os.makedirs(OUT, exist_ok=True)
    for name in sorted(os.listdir(SRC)):
        if re.search(r'[,.]schem$', name):
            print(convert(os.path.join(SRC, name), block_map))


if __name__ == '__main__':
    sys.exit(main())
