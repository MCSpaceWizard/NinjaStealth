#!/usr/bin/env python3
"""Generate the GameTest arena structure: a stone floor with open air above.

Usage: python3 tools/structures/make_arena.py
Writes src/main/resources/data/emergentstealth/structure/arena.nbt (gzipped NBT, structure format).
"""
import gzip
import struct
from pathlib import Path

DATA_VERSION = 4790  # SharedConstants.WORLD_VERSION for Minecraft 26.1.2
SIZE = (9, 6, 26)    # x, y, z
OUT = Path(__file__).resolve().parents[2] / "src/main/resources/data/emergentstealth/structure/arena.nbt"

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def name(n):
    b = n.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def tag_int(n, v):
    return bytes([TAG_INT]) + name(n) + struct.pack(">i", v)


def tag_string(n, v):
    return bytes([TAG_STRING]) + name(n) + name(v)


def payload_compound(items):
    return b"".join(items) + bytes([TAG_END])


def tag_compound(n, items):
    return bytes([TAG_COMPOUND]) + name(n) + payload_compound(items)


def tag_list(n, elem_type, payloads):
    return bytes([TAG_LIST]) + name(n) + bytes([elem_type]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def main():
    palette = [payload_compound([tag_string("Name", "minecraft:stone")]),
               payload_compound([tag_string("Name", "minecraft:air")])]
    blocks = []
    sx, sy, sz = SIZE
    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                pos = tag_list("pos", TAG_INT, [struct.pack(">i", c) for c in (x, y, z)])
                blocks.append(payload_compound([pos, tag_int("state", 0 if y == 0 else 1)]))
    root = tag_compound("", [
        tag_int("DataVersion", DATA_VERSION),
        tag_list("size", TAG_INT, [struct.pack(">i", c) for c in SIZE]),
        tag_list("palette", TAG_COMPOUND, palette),
        tag_list("blocks", TAG_COMPOUND, blocks),
        tag_list("entities", TAG_END, []),
    ])
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(OUT, "wb") as f:
        f.write(root)
    print(f"wrote {OUT} ({len(blocks)} blocks)")


if __name__ == "__main__":
    main()
