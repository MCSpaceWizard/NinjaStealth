"""Generates site templates: the ground, terraces, walls and gates that a large compound's buildings stand on.

    pip install nbtlib
    python3 tools/compounds/sites.py      # writes data/emergentstealth/structure/sites/*.nbt

A site is one template built here from parametric pieces (stone terraces with battered walls, wall runs with a
wall walk, corner towers, a gate tower, stair flights, stone lanterns, storehouses). The builder's modules are
then placed on top of it by a compound (tools/compounds/make_examples.py). Each site:

- has a **ground line**: layer `GROUND` is the outside terrain surface. Everything below it is foundation (earth
  under the yards, stone under walls and terraces), so on uneven ground the site rests on its own footing instead
  of floating;
- stores air above the ground over its whole box, so placing it clears trees and hills inside the walls.

See docs/design/33-compound-terrain.md for how sites meet real terrain.
"""
import hashlib
import os

import nbtlib
from nbtlib import Compound, Int, List, String

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'src/main/resources/data/emergentstealth/structure/sites')
DATA_VERSION = 4790  # 26.1.2, like the Cherry Grove imports

AIR = ('minecraft:air', ())


def block(name, **props):
    return ('minecraft:' + name, tuple(sorted((k, str(v).lower()) for k, v in props.items())))


def noise(x, y, z, salt=''):
    """A stable 0..1 value per block, so a rerun writes the same template."""
    h = hashlib.md5(('%d,%d,%d,%s' % (x, y, z, salt)).encode()).digest()
    return h[0] / 255.0


def smooth(x, z, scale, salt=''):
    """Value noise: `noise` on a grid `scale` blocks apart, blended between grid points (0..1, clumpy)."""
    gx, gz = x // scale, z // scale
    fx, fz = (x % scale) / scale, (z % scale) / scale
    fx, fz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    a, b = noise(gx, 0, gz, salt), noise(gx + 1, 0, gz, salt)
    c, d = noise(gx, 0, gz + 1, salt), noise(gx + 1, 0, gz + 1, salt)
    return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz


# Minecraft stair `facing` is the side the tall back is on: a flight climbing north uses facing=north.
OPPOSITE = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
STEP = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}

PLASTER = block('white_terracotta')
BEAM = block('stripped_dark_oak_log', axis='y')
FLOOR = block('spruce_planks')
ROOF = block('deepslate_tiles')
ROOF_SLAB = block('deepslate_tile_slab', type='bottom')
PATH = block('gravel')


def roof_stair(facing):
    return block('deepslate_tile_stairs', facing=facing, half='bottom', shape='straight', waterlogged=False)


def stone_stair(facing):
    return block('stone_brick_stairs', facing=facing, half='bottom', shape='straight', waterlogged=False)


def ishigaki(x, y, z):
    """Dry-laid castle stone: mostly stone bricks, some cobble, moss and andesite."""
    n = noise(x, y, z, 'ishigaki')
    if n < 0.45:
        return block('stone_bricks')
    if n < 0.6:
        return block('cracked_stone_bricks')
    if n < 0.75:
        return block('mossy_stone_bricks')
    if n < 0.88:
        return block('cobblestone')
    return block('andesite')


def earth(x, y, z, depth):
    """Ground below the surface: dirt near the top, stone further down."""
    if depth <= 3:
        return block('coarse_dirt') if noise(x, y, z, 'earth') < 0.15 else block('dirt')
    return block('stone') if noise(x, y, z, 'deep') < 0.8 else block('andesite')


class Site:
    def __init__(self, sx, sy, sz, ground):
        self.size = (sx, sy, sz)
        self.ground = ground
        self.blocks = {}

    def set(self, x, y, z, state):
        if 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]:
            self.blocks[(x, y, z)] = state

    def get(self, x, y, z):
        return self.blocks.get((x, y, z), AIR)

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, state(x, y, z) if callable(state) else state)

    # --- Ground -------------------------------------------------------------------------------------------------

    def lay_ground(self):
        """Grass on the ground line over the whole site, earth and stone below."""
        g = self.ground
        for x in range(self.size[0]):
            for z in range(self.size[2]):
                for y in range(g):
                    self.set(x, y, z, earth(x, y, z, g - y))
                self.set(x, g, z, block('grass_block', snowy=False))

    def path(self, x0, z0, x1, z1, top):
        """A gravel path on the surface whose top block is at `top`."""
        self.fill(x0, top, z0, x1, top, z1, PATH)

    def nature(self, x0, z0, x1, z1, top, trees=()):
        """
        Dresses the grass surface at `top` so it doesn't look mown: patches of grass and ferns, flower clusters
        (one kind per cluster), petals under cherry trees and leaf litter under the rest, coarse dirt and moss
        worn into the ground, and gravel paths with ragged edges. Patches come from smooth noise, so plants
        clump the way they do in vanilla meadows. `trees` are (x, z, kind) to drop petals and litter around.
        """
        flowers = ['poppy', 'dandelion', 'cornflower', 'azure_bluet', 'oxeye_daisy', 'allium', 'lily_of_the_valley']
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                ground = self.get(x, top, z)[0]
                if self.get(x, top + 1, z) != AIR:
                    continue
                if ground == 'minecraft:gravel':
                    # Ragged path edges: grass creeping in, the odd cobble.
                    if self._next_to(x, top, z, 'minecraft:grass_block') and noise(x, top, z, 'edge') < 0.25:
                        self.set(x, top, z, block('coarse_dirt'))
                    elif noise(x, top, z, 'cobble') < 0.05:
                        self.set(x, top, z, block('cobblestone'))
                    continue
                if ground != 'minecraft:grass_block':
                    continue
                near = next((k for tx, tz, k in trees if (tx - x) ** 2 + (tz - z) ** 2 <= 9 and (tx, tz) != (x, z)), None)
                worn = smooth(x, z, 9, 'worn')
                patch = smooth(x, z, 7, 'patch')
                bloom = smooth(x, z, 5, 'bloom')
                n = noise(x, top, z, 'plant')
                if worn > 0.85 and n < 0.5:
                    self.set(x, top, z, block('coarse_dirt') if n < 0.35 else block('moss_block'))
                elif near == 'cherry' and n < 0.55:
                    self.set(x, top + 1, z, block('pink_petals', facing=('north', 'east', 'south', 'west')[int(n * 40) % 4],
                                                  flower_amount=1 + int(n * 7) % 4))
                elif near and n < 0.4:
                    self.set(x, top + 1, z, block('leaf_litter', facing=('north', 'east', 'south', 'west')[int(n * 40) % 4],
                                                  segment_amount=1 + int(n * 9) % 4))
                elif bloom > 0.72 and n < 0.35:
                    kind = flowers[int(smooth(x, z, 11, 'kind') * len(flowers) * 3) % len(flowers)]
                    self.set(x, top + 1, z, block(kind))
                elif patch > 0.55 and n < 0.55:
                    if n < 0.06 and self.get(x, top + 2, z) == AIR:
                        self.set(x, top + 1, z, block('tall_grass', half='lower'))
                        self.set(x, top + 2, z, block('tall_grass', half='upper'))
                    else:
                        self.set(x, top + 1, z, block('fern') if n < 0.12 else block('short_grass'))
                elif n < 0.05:
                    self.set(x, top + 1, z, block('short_grass'))

    def _next_to(self, x, y, z, name):
        return any(self.get(x + dx, y, z + dz)[0] == name for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))

    def shrubs(self, cells, top, kind='azalea'):
        """Knee-high bushes (persistent leaves) on grass cells at `top`, e.g. along the foot of a wall."""
        for (x, z) in cells:
            above = self.get(x, top + 1, z)[0]
            if self.get(x, top, z)[0] == 'minecraft:grass_block' and above in ('minecraft:air', 'minecraft:short_grass',
                                                                              'minecraft:fern'):
                leaves = 'flowering_azalea_leaves' if noise(x, top, z, 'bloom') < 0.3 else kind + '_leaves'
                self.set(x, top + 1, z, block(leaves, distance=1, persistent=True, waterlogged=False))

    # --- Terraces -----------------------------------------------------------------------------------------------

    def terrace(self, x0, z0, x1, z1, base, top, batter=True, parapet=None):
        """
        A raised platform from the surface `base` up to a grass top at `top`, faced with castle stone. With
        `batter` the walls lean back: the face steps out one block every two layers towards the bottom, like
        ishigaki. `parapet` is a list of openings ((x0, z0, x1, z1) boxes) in a low plaster wall along the top edge;
        None means no wall.
        """
        height = top - base
        for y in range(base + 1, top + 1):
            out = (top - y) // 2 if batter else 0
            for x in range(x0 - out, x1 + out + 1):
                for z in range(z0 - out, z1 + out + 1):
                    face = x <= x0 - out or x >= x1 + out or z <= z0 - out or z >= z1 + out
                    if y == top and not face:
                        self.set(x, y, z, block('grass_block', snowy=False))
                    elif face or y >= top - 2:
                        self.set(x, y, z, ishigaki(x, y, z) if face else block('dirt'))
                    else:
                        self.set(x, y, z, block('dirt') if y > base + height // 2 else block('stone'))
        if parapet is not None:
            self.edge_wall(x0, z0, x1, z1, top + 1, parapet)

    def edge_wall(self, x0, z0, x1, z1, y, openings):
        """A knee-high plaster wall with a tile cap along a rectangle's edge, except inside `openings`."""
        def opened(x, z):
            return any(a <= x <= c and b <= z <= d for a, b, c, d in openings)
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x in (x0, x1) or z in (z0, z1)) and not opened(x, z):
                    corner = (x - x0) % 8 == 0 and z in (z0, z1) or (z - z0) % 8 == 0 and x in (x0, x1)
                    self.set(x, y, z, BEAM if corner else PLASTER)
                    self.set(x, y + 1, z, ROOF_SLAB)

    def stairs(self, x0, z0, width_axis, width, facing, bottom, steps, material=stone_stair, solid=None):
        """
        A flight of `steps` stairs climbing towards `facing`, `width` wide along `width_axis` ('x' or 'z'). The
        first step's block is at y = `bottom` + 1 on (x0, z0); each next step is one further and one higher. The
        last block is solid (a landing level with the top). Everything below the flight is filled, and the
        headroom above is cleared.
        """
        dx, dz = STEP[facing]
        for i in range(steps):
            for w in range(width):
                x = x0 + dx * i + (w if width_axis == 'x' else 0)
                z = z0 + dz * i + (w if width_axis == 'z' else 0)
                y = bottom + 1 + i
                for below in range(bottom - 2, y):
                    self.set(x, below, z, ishigaki(x, below, z) if solid is None else solid)
                self.set(x, y, z, material(facing) if i < steps - 1 else block('stone_bricks'))
                for above in range(y + 1, y + 4):
                    self.set(x, above, z, AIR)

    # --- Walls, towers, gates -----------------------------------------------------------------------------------

    def outer_wall(self, x0, z0, x1, z1, base, walk):
        """
        A curtain wall around a rectangle, 4 thick: castle stone up to the wall walk's surface at `walk`, and on
        the outermost ring a plaster parapet (dobei) two blocks above the walk, with dark posts every 8 blocks,
        loopholes at eye height every 4, a timber band and a tile cap, and a tiled eave over the walk.
        """
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                ring = min(x - x0, x1 - x, z - z0, z1 - z)
                if ring > 3:
                    continue
                for y in range(0, walk + 1):
                    self.set(x, y, z, ishigaki(x, y, z) if y > base or ring == 0 else earth(x, y, z, base - y + 1))
                if ring == 0:
                    along = x - x0 if z in (z0, z1) else z - z0
                    beam = along % 8 == 0
                    self.set(x, walk + 1, z, BEAM if beam else PLASTER)
                    # Loopholes (sama) at eye height, two between each pair of posts.
                    self.set(x, walk + 2, z, BEAM if beam else AIR if along % 4 == 2 else PLASTER)
                    self.set(x, walk + 3, z, block('stripped_dark_oak_wood', axis='x' if z in (z0, z1) else 'z'))
                    self.set(x, walk + 4, z, ROOF_SLAB)
                else:
                    self.set(x, walk, z, block('stone_bricks') if ring == 3 or noise(x, walk, z, 'walk') < 0.7
                             else block('polished_andesite'))
                    if ring == 1:
                        # The wall's tiled eave over the walk, sloping down to the inside.
                        side = min((z - z0, 'north'), (z1 - z, 'south'), (x - x0, 'west'), (x1 - x, 'east'))[1]
                        self.set(x, walk + 3, z, roof_stair(side))

    def corner_tower(self, x0, z0, walk, doors):
        """
        A 9x9 corner tower (yagura) on the wall: stone base up to the walk, a plaster storey with beams on the 7x7
        inside it, a hip roof. `doors` are blocks on the storey's wall cut open 2 high (where the walks come in).
        """
        self.fill(x0, 0, z0, x0 + 8, walk, z0 + 8, lambda x, y, z: ishigaki(x, y, z))
        self.fill(x0, walk, z0, x0 + 8, walk, z0 + 8, FLOOR)
        bx0, bz0, bx1, bz1 = x0 + 1, z0 + 1, x0 + 7, z0 + 7
        for y in range(walk + 1, walk + 5):
            for x in range(bx0, bx1 + 1):
                for z in range(bz0, bz1 + 1):
                    edge = x in (bx0, bx1) or z in (bz0, bz1)
                    if not edge:
                        self.set(x, y, z, AIR)
                        continue
                    corner = x in (bx0, bx1) and z in (bz0, bz1)
                    mid = (x - bx0) % 3 == 0 and (z - bz0) % 3 == 0
                    if corner or (mid and y < walk + 4):
                        self.set(x, y, z, BEAM)
                    elif y == walk + 3 and (x - bx0) % 2 == 1 and (z - bz0) % 2 == 1:
                        self.set(x, y, z, block('spruce_trapdoor', facing='north', half='top', open=True,
                                                powered=False, waterlogged=False))
                    elif y == walk + 4:
                        self.set(x, y, z, block('stripped_dark_oak_wood', axis='y'))
                    else:
                        self.set(x, y, z, PLASTER)
        for (x, z) in doors:
            self.set(x, walk + 1, z, AIR)
            self.set(x, walk + 2, z, AIR)
        self.set(x0 + 4, walk + 4, z0 + 4, block('lantern', hanging=True, waterlogged=False))
        self.set(x0 + 4, walk + 5, z0 + 4, FLOOR)
        self.hip_roof(x0, z0, x0 + 8, z0 + 8, walk + 5)

    def hip_roof(self, x0, z0, x1, z1, y):
        """Rings of tile stairs stepping in by one each layer, filled with tiles, capped with slabs."""
        while x1 - x0 >= 0 and z1 - z0 >= 0:
            if x1 - x0 < 2 or z1 - z0 < 2:
                self.fill(x0, y, z0, x1, y, z1, ROOF_SLAB)
                return
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if z == z0:
                        self.set(x, y, z, roof_stair('south'))
                    elif z == z1:
                        self.set(x, y, z, roof_stair('north'))
                    elif x == x0:
                        self.set(x, y, z, roof_stair('east'))
                    elif x == x1:
                        self.set(x, y, z, roof_stair('west'))
                    else:
                        self.set(x, y, z, ROOF)
            for (cx, cz, shape, facing) in ((x0, z0, 'outer_left', 'south'), (x1, z0, 'outer_right', 'south'),
                                            (x0, z1, 'outer_right', 'north'), (x1, z1, 'outer_left', 'north')):
                self.set(cx, y, cz, block('deepslate_tile_stairs', facing=facing, half='bottom', shape=shape,
                                          waterlogged=False))
            x0, z0, x1, z1, y = x0 + 1, z0 + 1, x1 - 1, z1 - 1, y + 1

    def gate_tower(self, x0, z0, x1, z1, passage_x0, passage_x1, base, walk):
        """
        A gate in a north-south passage through an east-west wall: the passage (3 high, gravel) between
        `passage_x0` and `passage_x1`, with a two-room gate house over it on the wall walk, doors east and west on
        to the walk and a hip roof.
        """
        self.fill(x0, base + 1, z0, x1, walk, z1, lambda x, y, z: ishigaki(x, y, z))
        self.fill(x0, walk, z0, x1, walk, z1, FLOOR)
        for x in range(passage_x0, passage_x1 + 1):
            for z in range(z0, z1 + 1):
                self.set(x, base, z, PATH)
                for y in range(base + 1, base + 4):
                    self.set(x, y, z, AIR)
            for z in (z0, z1):
                self.set(x, base + 4, z, block('stripped_dark_oak_log', axis='x'))
        for z in range(z0, z1 + 1):
            for x in (passage_x0 - 1, passage_x1 + 1):
                for y in range(base + 1, base + 4):
                    self.set(x, y, z, BEAM if z in (z0, z1) else block('spruce_planks'))
        bx0, bx1, bz0, bz1 = x0 + 1, x1 - 1, z0, z1
        for y in range(walk + 1, walk + 5):
            for x in range(bx0, bx1 + 1):
                for z in range(bz0, bz1 + 1):
                    edge = x in (bx0, bx1) or z in (bz0, bz1)
                    if not edge:
                        self.set(x, y, z, AIR)
                    elif (x - bx0) % 4 == 0 and (z in (bz0, bz1)) or x in (bx0, bx1) and z in (bz0, bz1):
                        self.set(x, y, z, BEAM)
                    elif y == walk + 3 and (x - bx0) % 2 == 1 and z in (bz0, bz1):
                        self.set(x, y, z, block('spruce_trapdoor', facing='north', half='top', open=True,
                                                powered=False, waterlogged=False))
                    elif y == walk + 4:
                        self.set(x, y, z, block('stripped_dark_oak_wood', axis='y'))
                    else:
                        self.set(x, y, z, PLASTER)
        mid_z = (bz0 + bz1) // 2
        for x in (bx0, bx1):
            for z in (mid_z, mid_z + 1):
                self.set(x, walk + 1, z, AIR)
                self.set(x, walk + 2, z, AIR)
        for x in ((bx0 + bx1) // 2 - 3, (bx0 + bx1) // 2 + 3):
            self.set(x, walk + 4, mid_z, block('lantern', hanging=True, waterlogged=False))
        self.fill(x0, walk + 5, z0, x1, walk + 5, z1, FLOOR)
        self.hip_roof(x0 - 1, z0 - 1, x1 + 1, z1, walk + 5)  # flush with the wall's outer face

    def postern(self, x0, z0, x1, z1, base):
        """A small door-sized tunnel through the wall (2 high), framed in timber."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                self.set(x, base, z, PATH)
                self.set(x, base + 1, z, AIR)
                self.set(x, base + 2, z, AIR)
                self.set(x, base + 3, z, block('stripped_dark_oak_log', axis='z' if x0 == x1 else 'x'))

    # --- Small buildings and props ------------------------------------------------------------------------------

    def kura(self, x0, z0, x1, z1, base, door):
        """
        A plaster storehouse: a stone plinth one high, a plank floor (stand at `base` + 2), plaster walls with dark
        beams, a door gap (2 high) at `door` = (x, z) on the plinth's edge with a step outside, barrels inside and
        a hip roof.
        """
        self.fill(x0, base + 1, z0, x1, base + 1, z1, lambda x, y, z: ishigaki(x, y, z))
        self.fill(x0 + 1, base + 1, z0 + 1, x1 - 1, base + 1, z1 - 1, FLOOR)
        for y in range(base + 2, base + 6):
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    edge = x in (x0, x1) or z in (z0, z1)
                    corner = x in (x0, x1) and z in (z0, z1)
                    if not edge:
                        self.set(x, y, z, AIR)
                    elif corner or y == base + 5:
                        self.set(x, y, z, BEAM if corner else block('stripped_dark_oak_wood', axis='y'))
                    elif y == base + 2:
                        self.set(x, y, z, block('stone_bricks'))
                    else:
                        self.set(x, y, z, PLASTER)
        dx, dz = door
        self.set(dx, base + 1, dz, FLOOR)
        self.set(dx, base + 2, dz, AIR)
        self.set(dx, base + 3, dz, AIR)
        outside = (dx - 1, dz) if dx == x0 else (dx + 1, dz) if dx == x1 else (dx, dz - 1) if dz == z0 else (dx, dz + 1)
        facing = {(-1, 0): 'east', (1, 0): 'west', (0, -1): 'south', (0, 1): 'north'}[(outside[0] - dx, outside[1] - dz)]
        self.set(outside[0], base + 1, outside[1], stone_stair(facing))
        for x in range(x0 + 1, x1):
            for z in (z0 + 1, z1 - 1):
                if (x + z) % 3 and (x, z) != (dx, dz):
                    self.set(x, base + 2, z, block('barrel', facing='up', open=False))
        self.set((x0 + x1) // 2, base + 5, (z0 + z1) // 2, block('lantern', hanging=True, waterlogged=False))
        self.hip_roof(x0 - 1, z0 - 1, x1 + 1, z1 + 1, base + 6)

    def stone_lantern(self, x, z, top):
        """A toro: a stone post with a lantern on it, standing on the surface block at `top`."""
        self.set(x, top + 1, z, block('stone_brick_wall', east='none', north='none', south='none', west='none',
                                      up=True, waterlogged=False))
        self.set(x, top + 2, z, block('lantern', hanging=False, waterlogged=False))

    def tree(self, x, z, top, kind='spruce', height=6):
        """A small tree on the surface block at `top`: a trunk and a rounded crown."""
        self.set(x, top, z, block('dirt'))
        for y in range(top + 1, top + 1 + height):
            self.set(x, y, z, block(kind + '_log', axis='y'))
        for dy in range(height - 2, height + 2):
            r = 2 if dy < height + 1 else 1
            for ax in range(-r, r + 1):
                for az in range(-r, r + 1):
                    if abs(ax) + abs(az) <= r + 1 and (ax, az) != (0, 0) or dy >= height:
                        if self.get(x + ax, top + 1 + dy, z + az) == AIR:
                            self.set(x + ax, top + 1 + dy, z + az,
                                     block(kind + '_leaves', distance=1, persistent=True, waterlogged=False))

    # --- Site pieces (points of interest) -----------------------------------------------------------------------
    # Small set pieces scattered over a site to give it life and give players cover and landmarks. Each takes the
    # surface block height `top` it stands on. Catalogue and plans: docs/design/33-compound-terrain.md §6.

    def pond(self, cx, cz, rx, rz, top):
        """An oval garden pond: water two deep, a ragged stone rim, lily pads, ferns at the edge."""
        for x in range(cx - rx - 1, cx + rx + 2):
            for z in range(cz - rz - 1, cz + rz + 2):
                d = ((x - cx) / (rx + 0.5)) ** 2 + ((z - cz) / (rz + 0.5)) ** 2 + (noise(x, top, z, 'shore') - 0.5) * 0.4
                n = noise(x, top, z, 'pond')
                if d < 0.55:
                    self.set(x, top - 1, z, block('water', level=0))
                    self.set(x, top, z, block('water', level=0))
                    self.set(x, top - 2, z, block('mud') if n < 0.5 else block('clay'))
                    if n < 0.08:
                        self.set(x, top + 1, z, block('lily_pad'))
                    else:
                        self.set(x, top + 1, z, AIR)
                elif d < 1.0:
                    self.set(x, top, z, block('water', level=0))
                    self.set(x, top - 1, z, block('mud'))
                    self.set(x, top + 1, z, AIR)
                elif d < 1.6:
                    rim = block('mossy_cobblestone') if n < 0.4 else block('stone') if n < 0.6 else block('grass_block', snowy=False)
                    self.set(x, top, z, rim)
                    if rim[0] == 'minecraft:grass_block' and n > 0.85:
                        self.set(x, top + 1, z, block('fern'))
                    elif n < 0.1:
                        self.set(x, top + 1, z, block('stone_slab', type='bottom', waterlogged=False))

    def well(self, x, z, top):
        """A 3x3 stone well with water, two posts and a little tiled roof; the bucket hangs on a chain."""
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for y in range(top - 4, top + 1):
                    self.set(x + dx, y, z + dz, block('cobblestone') if (dx, dz) != (0, 0) else block('water', level=0))
                if (dx, dz) != (0, 0):
                    self.set(x + dx, top + 1, z + dz, block('mossy_cobblestone_wall', east='none', north='none',
                                                           south='none', west='none', up=True, waterlogged=False)
                             if dx and dz else block('stone_brick_slab', type='bottom', waterlogged=False))
        self.set(x, top + 1, z, AIR)
        for dx in (-1, 1):
            self.set(x + dx, top + 2, z, block('spruce_fence', east='false', north='false', south='false', west='false',
                                                waterlogged=False))
            self.set(x + dx, top + 3, z, block('spruce_fence', east='false', north='false', south='false', west='false',
                                                waterlogged=False))
            self.set(x + dx, top + 1, z, block('cobblestone'))
        self.set(x, top + 3, z, block('iron_chain', axis='y', waterlogged=False))
        self.fill(x - 1, top + 4, z - 1, x + 1, top + 4, z + 1, ROOF_SLAB)
        self.set(x, top + 4, z, ROOF)

    def hokora(self, x, z, top, facing='south'):
        """A tiny wayside shrine: a stone plinth, a small timber house with a tiled roof, an offering lantern."""
        self.set(x, top + 1, z, block('stone_bricks'))
        self.set(x, top + 2, z, block('spruce_planks'))
        self.set(x, top + 3, z, roof_stair(OPPOSITE[facing]))
        dx, dz = STEP[facing]
        self.set(x + dx, top + 1, z + dz, block('stone_brick_slab', type='bottom', waterlogged=False))
        self.set(x + dx * 2, top + 1, z + dz * 2, block('stone_brick_wall', east='none', north='none', south='none',
                                                        west='none', up=True, waterlogged=False))
        self.set(x + dx * 2, top + 2, z + dz * 2, block('lantern', hanging=False, waterlogged=False))
        for side in (-1, 1):
            sx, sz = (side, 0) if dz else (0, side)
            self.set(x + sx, top + 1, z + sz, block('potted_red_tulip') if side < 0 else block('candle', candles=2,
                                                                                               lit=False, waterlogged=False))

    def bamboo(self, x0, z0, x1, z1, top, density=0.35):
        """A bamboo stand: culms of mixed height on podzol, thickest in the middle of the patch."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if self.get(x, top, z)[0] not in ('minecraft:grass_block', 'minecraft:coarse_dirt', 'minecraft:moss_block'):
                    continue
                self.set(x, top, z, block('podzol', snowy=False))
                self.set(x, top + 1, z, AIR)
                if noise(x, top, z, 'bamboo') >= density:
                    continue
                height = 5 + int(noise(x, top, z, 'tall') * 7)
                for i in range(height):
                    leaves = 'large' if i >= height - 2 else 'small' if i >= height - 4 else 'none'
                    self.set(x, top + 1 + i, z, block('bamboo', age=1, leaves=leaves, stage=0))

    def boulder(self, x, z, top, size=2):
        """A mossy rock half sunk in the ground."""
        for dx in range(-size, size + 1):
            for dz in range(-size, size + 1):
                for dy in range(0, size + 1):
                    if dx * dx + dz * dz + (dy * 1.6) ** 2 <= size * size + noise(x + dx, top + dy, z + dz, 'rock'):
                        n = noise(x + dx, top + dy, z + dz, 'stone')
                        self.set(x + dx, top + dy, z + dz, block('mossy_cobblestone') if n < 0.3 else
                                 block('andesite') if n < 0.6 else block('stone'))

    def woodpile(self, x, z, top, length=4, axis='x'):
        """Split logs stacked two high under a slab roof, with a chopping block."""
        dx, dz = (1, 0) if axis == 'x' else (0, 1)
        for i in range(length):
            for y in (1, 2):
                self.set(x + dx * i, top + y, z + dz * i, block('spruce_log', axis='z' if axis == 'x' else 'x'))
            self.set(x + dx * i, top + 3, z + dz * i, block('spruce_slab', type='bottom', waterlogged=False))
        self.set(x - dz - dx, top + 1, z - dx - dz, block('oak_log', axis='y'))

    def haystacks(self, x, z, top):
        """A few hay bales, one on top."""
        for (dx, dz, dy) in ((0, 0, 1), (1, 0, 1), (0, 1, 1), (0, 0, 2)):
            self.set(x + dx, top + dy, z + dz, block('hay_block', axis='y'))

    # --- Output -------------------------------------------------------------------------------------------------

    def save(self, path):
        """Writes a structure template. Air is written too (above the ground), so placing it clears the box."""
        palette, index, blocks = [], {}, []
        sx, sy, sz = self.size
        for y in range(sy):
            for z in range(sz):
                for x in range(sx):
                    state = self.blocks.get((x, y, z), AIR)
                    if state not in index:
                        index[state] = len(palette)
                        entry = Compound({'Name': String(state[0])})
                        if state[1]:
                            entry['Properties'] = Compound({k: String(v) for k, v in state[1]})
                        palette.append(entry)
                    blocks.append(Compound({'pos': List[Int]([Int(x), Int(y), Int(z)]), 'state': Int(index[state])}))
        nbtlib.File({
            'DataVersion': Int(DATA_VERSION),
            'size': List[Int]([Int(sx), Int(sy), Int(sz)]),
            'palette': List[Compound](palette),
            'blocks': List[Compound](blocks),
            'entities': List[Compound]([]),
        }).save(path, gzipped=True)
        return '%s: %dx%dx%d, %d states, %d KB' % (os.path.basename(path), sx, sy, sz, len(palette),
                                                  os.path.getsize(path) // 1024)


# --- Takamori castle --------------------------------------------------------------------------------------------

class Takamori:
    """
    The site for the Takamori castle compound (150 x 150): an outer bailey inside a curtain wall with four corner
    towers, a gate tower on the south and a postern on the east; a middle terrace (ninomaru, 5 up) over the
    north half; an inner terrace (honmaru, 5 more) in its north-east corner for the keep. Storehouses in the east
    bailey. Coordinates are template blocks: x east, z south, y up; the bailey surface is layer GROUND.
    """
    SIZE = (150, 26, 150)
    GROUND = 6
    WALK = GROUND + 4          # wall walk surface block (stand one above)
    NINOMARU = GROUND + 5      # middle terrace surface block
    HONMARU = GROUND + 10      # inner terrace surface block
    NINOMARU_BOX = (14, 9, 135, 85)
    HONMARU_BOX = (92, 15, 132, 56)
    GATE = (68, 144, 81, 149)  # gate tower footprint; the passage is x 73..76
    MAIN_STAIR_X = 72          # bailey -> ninomaru, x 72..77, climbing north at z 90..86
    WEST_STAIR_Z = 40          # bailey -> ninomaru, z 40..42, climbing east at x 9..13
    HONMARU_STAIR_X = 110      # ninomaru -> honmaru, x 110..114, climbing north at z 61..57
    KURA = [(126, 95, 134, 101), (126, 117, 134, 123), (126, 127, 134, 133)]

    @classmethod
    def build(cls):
        s = Site(*cls.SIZE, ground=cls.GROUND)
        g, walk = cls.GROUND, cls.WALK
        s.lay_ground()

        # Middle and inner terraces.
        nx0, nz0, nx1, nz1 = cls.NINOMARU_BOX
        mx = cls.MAIN_STAIR_X
        s.terrace(nx0, nz0, nx1, nz1, g, cls.NINOMARU, parapet=[
            (mx, nz1, mx + 5, nz1), (nx0, cls.WEST_STAIR_Z, nx0, cls.WEST_STAIR_Z + 2)])
        hx0, hz0, hx1, hz1 = cls.HONMARU_BOX
        hs = cls.HONMARU_STAIR_X
        s.terrace(hx0, hz0, hx1, hz1, cls.NINOMARU, cls.HONMARU, parapet=[(hs, hz1, hs + 4, hz1)])
        s.stairs(mx, nz1 + 5, 'x', 6, 'north', g, 5)
        s.stairs(nx0 - 5, cls.WEST_STAIR_Z, 'z', 3, 'east', g, 5)
        s.stairs(hs, hz1 + 5, 'x', 5, 'north', cls.NINOMARU, 5)

        # Curtain wall, towers, gates.
        s.outer_wall(0, 0, 149, 149, g, walk)
        s.corner_tower(0, 0, walk, doors=[(7, 2), (2, 7)])
        s.corner_tower(141, 0, walk, doors=[(142, 2), (147, 7)])
        s.corner_tower(0, 141, walk, doors=[(7, 147), (2, 142)])
        s.corner_tower(141, 141, walk, doors=[(142, 147), (147, 142)])
        gx0, gz0, gx1, gz1 = cls.GATE
        s.gate_tower(gx0, gz0, gx1, gz1, 73, 76, g, walk)
        s.postern(146, 112, 149, 113, g)
        # Stairs up to the wall walk: either side of the gate (climbing south) and on the east and west walls.
        s.stairs(60, 142, 'x', 2, 'south', g, 4)
        s.stairs(88, 142, 'x', 2, 'south', g, 4)
        s.stairs(142, 90, 'z', 2, 'east', g, 4)
        s.stairs(7, 136, 'z', 2, 'west', g, 4)

        # Paths: gate to the main stair, branches to the storehouses and the postern; terrace walks.
        s.path(73, 91, 76, 143, g)
        s.path(77, 110, 125, 111, g)
        s.path(77, 112, 145, 113, g)
        s.path(122, 96, 124, 133, g)
        s.path(mx, nz1 - 6, mx + 5, nz1 - 1, cls.NINOMARU)
        s.path(78, 62, 80, 84, cls.NINOMARU)
        s.path(78, 62, 114, 64, cls.NINOMARU)
        s.path(hs, hz1 - 4, hs + 4, hz1 - 1, cls.HONMARU)

        for k in cls.KURA:
            s.kura(*k, base=g, door=(k[0], (k[1] + k[3]) // 2))

        # Lanterns along the gate road, at the stair heads and by the storehouses.
        for z in (100, 115, 130):
            s.stone_lantern(72, z, g)
            s.stone_lantern(77, z, g)
        s.stone_lantern(mx - 1, nz1 - 2, cls.NINOMARU)
        s.stone_lantern(mx + 6, nz1 - 2, cls.NINOMARU)
        s.stone_lantern(hs - 1, hz1 - 2, cls.HONMARU)
        s.stone_lantern(hs + 5, hz1 - 2, cls.HONMARU)
        s.stone_lantern(121, 105, g)
        s.stone_lantern(121, 125, g)

        # Site pieces: a garden pond on the ninomaru, a well and wayside shrines in the bailey, a bamboo stand
        # along the west wall, rocks, firewood and hay by the barracks.
        s.pond(102, 74, 6, 4, cls.NINOMARU)
        s.well(99, 125, g)
        s.hokora(8, 90, g, facing='east')
        s.hokora(110, 140, g, facing='north')
        s.bamboo(5, 52, 10, 74, g)
        for (x, z, top, size) in ((30, 140, g, 2), (115, 90, g, 1), (125, 72, cls.NINOMARU, 2),
                                  (110, 80, cls.NINOMARU, 1), (128, 50, cls.HONMARU, 1)):
            s.boulder(x, z, top, size)
        s.woodpile(28, 94, g)
        s.haystacks(54, 94, g)

        # Trees: spruce and oak in the bailey, cherries on the ninomaru.
        trees = [(14, 137, 'spruce'), (100, 138, 'spruce'), (140, 70, 'spruce'), (20, 92, 'spruce'),
                 (60, 92, 'spruce'), (140, 30, 'spruce'), (35, 138, 'oak'), (90, 128, 'oak'), (8, 20, 'oak')]
        for (x, z, kind) in trees:
            s.tree(x, z, g, kind, 7 if kind == 'spruce' else 5)
        blossoms = [(84, 70, 'cherry'), (86, 20, 'cherry'), (20, 75, 'cherry'), (125, 80, 'cherry'), (66, 45, 'cherry')]
        for (x, z, kind) in blossoms:
            s.tree(x, z, cls.NINOMARU, kind, 5)

        # Bushes along the inside foot of the curtain wall, in clumps, kept clear of gates and stairs.
        clear = [(58, 135, 91, 145), (140, 86, 145, 117), (4, 132, 9, 140), (4, 38, 9, 44)]
        foot = [(x, z) for x in range(9, 141) for z in (4, 145)] + [(x, z) for z in range(9, 141) for x in (4, 145)]
        s.shrubs([(x, z) for (x, z) in foot if smooth(x, z, 6, 'hedge') > 0.6
                  and not any(a <= x <= c and b <= z <= d for a, b, c, d in clear)], g)

        s.nature(4, 4, 145, 145, g, trees)
        s.nature(nx0 + 1, nz0 + 1, nx1 - 1, nz1 - 1, cls.NINOMARU, blossoms)
        s.nature(hx0 + 1, hz0 + 1, hx1 - 1, hz1 - 1, cls.HONMARU)
        return s


SITES = {'takamori_castle': Takamori}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, site in SITES.items():
        print(site.build().save(os.path.join(OUT, name + '.nbt')))


if __name__ == '__main__':
    main()
