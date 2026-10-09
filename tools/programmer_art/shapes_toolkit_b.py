"""Extra placeholder shapes for the beta toolkit, part B (design doc 21): arrows, darts, blowgun, keys, picks.

Imported by generate.py, which merges SHAPES into its own table. Every shape takes three colours
(as RGBA tuples) like the built-in shapes.
"""

SIZE = 16


def _shade(color, factor):
    r, g, b, a = color
    return (min(255, int(r * factor)), min(255, int(g * factor)), min(255, int(b * factor)), a)


def _put(img, x, y, color):
    w, h = img.size
    if 0 <= x < w and 0 <= y < h:
        img.putpixel((x, y), color)


def shape_arrow_item(img, shaft, head, fletch):
    """Item icon: an arrow from bottom-left (fletching) to top-right (head), like the vanilla arrow."""
    for i in range(3, 12):
        _put(img, i, 15 - i, shaft if i % 3 else _shade(shaft, 0.8))
    # Head: a small arrowhead at the top-right.
    for (x, y) in ((12, 3), (13, 2), (11, 2), (12, 2), (13, 3), (13, 4), (12, 1), (14, 3), (13, 1), (14, 2)):
        _put(img, x, y, head)
    _put(img, 14, 1, _shade(head, 1.25))
    _put(img, 12, 4, _shade(head, 0.75))
    # Fletching at the bottom-left.
    for (x, y) in ((1, 13), (2, 13), (2, 14), (2, 12), (3, 13), (3, 14), (1, 12), (4, 13), (3, 12), (2, 11), (4, 14)):
        _put(img, x, y, fletch)
    _put(img, 1, 14, _shade(fletch, 0.75))


def shape_dart_item(img, shaft, tip, fletch):
    """Item icon: a short slim dart with a fluff of fletching."""
    for i in range(5, 11):
        _put(img, i, 15 - i, shaft)
    for (x, y) in ((11, 4), (12, 3), (13, 2)):
        _put(img, x, y, tip)
    _put(img, 13, 2, _shade(tip, 1.3))
    for (x, y) in ((3, 11), (4, 11), (3, 12), (4, 12), (5, 11), (4, 10), (3, 10), (2, 12)):
        _put(img, x, y, fletch)


def shape_blowgun(img, tube, band, mouth):
    """Item icon: a long bamboo tube, 2 px thick, with node bands."""
    for i in range(1, 15):
        x, y = i, 15 - i
        _put(img, x, y, _shade(tube, 1.1))
        _put(img, x + 1, y, _shade(tube, 0.85))
        if i % 4 == 0:
            _put(img, x, y, band)
            _put(img, x + 1, y, band)
    _put(img, 1, 14, mouth)
    _put(img, 2, 14, mouth)
    _put(img, 14, 1, _shade(tube, 0.6))
    _put(img, 15, 1, _shade(tube, 0.6))


def shape_key(img, metal, dark, accent):
    """Item icon: a ring bow at the top-left, a diagonal shank and a toothed bit at the bottom-right."""
    cx, cy = 4.5, 4.5
    for y in range(0, 10):
        for x in range(0, 10):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 2.0 <= d <= 3.6:
                _put(img, x, y, metal if x + y < 9 else _shade(metal, 0.8))
    _put(img, 3, 3, accent)
    for i in range(7, 14):
        _put(img, i, i, metal)
        _put(img, i + 1, i, _shade(metal, 0.7))
    for (x, y) in ((11, 13), (12, 14), (13, 12), (14, 13), (10, 12)):
        _put(img, x, y, dark)


def shape_lockpick(img, metal, dark, handle):
    """Item icon: two thin picks crossing, wrapped handles at the bottom."""
    for i in range(2, 13):
        _put(img, i, 15 - i, metal)
    _put(img, 13, 2, metal)
    _put(img, 13, 1, metal)
    _put(img, 14, 1, metal)
    for i in range(3, 11):
        _put(img, i + 2, 15 - i, dark)
    _put(img, 13, 5, dark)
    _put(img, 13, 4, dark)
    for (x, y) in ((2, 13), (3, 12), (1, 14), (2, 14), (3, 13)):
        _put(img, x, y, handle)
    for (x, y) in ((12, 13), (13, 12), (13, 13), (12, 12)):
        _put(img, x, y, _shade(handle, 0.8))


def shape_kit(img, case, trim, key):
    """Item icon: a lacquered locksmith's case with a key on its lid."""
    for y in range(4, 14):
        for x in range(1, 15):
            edge = x in (1, 14) or y in (4, 13)
            _put(img, x, y, trim if edge else (case if y > 6 else _shade(case, 1.2)))
    for x in range(5, 11):
        _put(img, x, 2, trim)
    _put(img, 5, 3, trim)
    _put(img, 10, 3, trim)
    for (x, y) in ((4, 8), (5, 8), (4, 9), (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (10, 10), (8, 10)):
        _put(img, x, y, key)


# Vanilla arrow entity texture layout (32x32): rows 0-4 are the two crossed quads (fletching at both ends,
# the head in the middle, the shaft along row 2), rows 5-9 two 5x5 end caps. '#' = painted.
ARROW_ENTITY_MASK = [
    "###..........................###",
    ".###.........##..##.........###.",
    "################################",
    ".###.........##..##.........###.",
    "###..........................###",
    "..#....#........................",
    "..#....#........................",
    "##########......................",
    "..#....#........................",
    "..#....#........................",
]


def shape_arrow_entity(img, shaft, head, fletch):
    """The flying-arrow texture in the vanilla 32x32 layout, recoloured."""
    for y, row in enumerate(ARROW_ENTITY_MASK):
        for x, c in enumerate(row):
            if c != "#":
                continue
            if y < 5:
                if x <= 3 or x >= 28:
                    color = fletch if y != 2 else _shade(shaft, 0.85)
                    if y in (1, 3) or (y in (0, 4)):
                        color = fletch if (x + y) % 2 else _shade(fletch, 0.85)
                elif 13 <= x <= 18:
                    color = head if y != 2 else _shade(head, 1.15)
                else:
                    color = shaft if x % 4 else _shade(shaft, 0.85)
            else:
                color = shaft if (x in (2, 7) and y == 7) else fletch
            img.putpixel((x, y), color)


SHAPES = {
    "arrow_item": shape_arrow_item,
    "dart_item": shape_dart_item,
    "blowgun": shape_blowgun,
    "key": shape_key,
    "lockpick": shape_lockpick,
    "kit": shape_kit,
    "arrow_entity": shape_arrow_entity,
}
