#!/usr/bin/env python3
"""Generate programmer-art placeholder textures for Emergent Stealth.

Usage:  python3 tools/programmer_art/generate.py [--force] [--only PATH ...]

Reads textures.json next to this script and writes 16x16 PNGs into
src/main/resources/assets/emergentstealth/textures/<path>.png.

Existing files are left alone unless --force is passed, so real art placed at the
same path is never clobbered. See docs/assets.md for the swap-in workflow.
"""
import argparse
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SPEC = Path(__file__).resolve().parent / "textures.json"
OUT = ROOT / "src/main/resources/assets/emergentstealth/textures"
SIZE = 16

# 3x5 pixel font for "badge" placeholders (A-Z, 0-9).
FONT = {
    "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110",
    "E": "111100110100111", "F": "111100110100100", "G": "011100101101011", "H": "101101111101101",
    "I": "111010010010111", "J": "001001001101010", "K": "101101110101101", "L": "100100100100111",
    "M": "101111111101101", "N": "110101101101101", "O": "010101101101010", "P": "110101110100100",
    "Q": "010101101110011", "R": "110101110101101", "S": "011100010001110", "T": "111010010010010",
    "U": "101101101101111", "V": "101101101101010", "W": "101101111111101", "X": "101101010101101",
    "Y": "101101010010010", "Z": "111001010100111", "0": "111101101101111", "1": "010110010010111",
    "2": "110001010100111", "3": "110001010001110", "4": "101101111001001", "5": "111100110001110",
    "6": "011100111101111", "7": "111001010010010", "8": "111101111101111", "9": "111101111001110",
}


def rgba(hex_color, alpha=255):
    h = hex_color.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha)


def shade(color, factor):
    r, g, b, a = color
    return (min(255, int(r * factor)), min(255, int(g * factor)), min(255, int(b * factor)), a)


def shape_lens(img, outline, glass, handle):
    """Round lens with a handle: magnifier-style icon."""
    cx, cy, r = 6.5, 6.5, 5.2
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r - 1.2:
                img.putpixel((x, y), shade(glass, 1.25) if x + y < 10 else glass)
            elif d <= r:
                img.putpixel((x, y), outline)
    for i in range(5):
        img.putpixel((10 + i, 10 + i), handle)
        img.putpixel((11 + i, 10 + i) if 11 + i < SIZE else (10 + i, 10 + i), shade(handle, 0.7))


def shape_noise(img, base, accent, _unused=None, seed=0):
    """Block-style noisy fill: good default for placeholder blocks."""
    rnd = random.Random(seed)
    for y in range(SIZE):
        for x in range(SIZE):
            img.putpixel((x, y), shade(base, rnd.uniform(0.85, 1.1)) if rnd.random() > 0.08 else accent)


def shape_badge(img, ink, paper, accent, letter="?"):
    """Paper badge with an ink letter: generic item placeholder."""
    for y in range(1, SIZE - 1):
        for x in range(1, SIZE - 1):
            edge = x in (1, SIZE - 2) or y in (1, SIZE - 2)
            img.putpixel((x, y), ink if edge else paper)
    glyph = FONT.get(letter.upper())
    if glyph:
        for gy in range(5):
            for gx in range(3):
                if glyph[gy * 3 + gx] == "1":
                    for sy in range(2):
                        for sx in range(2):
                            img.putpixel((5 + gx * 2 + sx, 3 + gy * 2 + sy), accent)


SHAPES = {"lens": shape_lens, "noise": shape_noise, "badge": shape_badge}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--force", action="store_true", help="overwrite existing PNGs")
    parser.add_argument("--only", nargs="*", help="only generate these texture paths")
    args = parser.parse_args()

    spec = json.loads(SPEC.read_text())
    palette = spec["palette"]
    written = skipped = 0
    for entry in spec["textures"]:
        path = entry["path"]
        if args.only and path not in args.only:
            continue
        target = OUT / f"{path}.png"
        if target.exists() and not args.force:
            skipped += 1
            continue
        colors = [rgba(palette.get(c, c)) for c in entry.get("colors", ["ink", "paper", "lacquer"])]
        while len(colors) < 3:
            colors.append(colors[-1])
        img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        extra = {k: v for k, v in entry.items() if k in ("letter", "seed")}
        SHAPES[entry["shape"]](img, *colors[:3], **extra)
        target.parent.mkdir(parents=True, exist_ok=True)
        img.save(target)
        written += 1
        print(f"wrote {target.relative_to(ROOT)}")
    print(f"{written} written, {skipped} kept (already exist)")


if __name__ == "__main__":
    main()
