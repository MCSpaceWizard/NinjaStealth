"""Writes the example compounds (design doc 32 §3) and checks every marker against the structure templates.

    pip install nbtlib
    python3 tools/compounds/make_examples.py          # check, then write data/.../compound/examples/*.json
    python3 tools/compounds/make_examples.py --check  # check only

Markers are written here in each module's own block coordinates (what you see with a structure block), and the
script moves them into compound space with the module's offset. Every waypoint, post and spawn must be a place an
NPC can stand (floor below, two free blocks), and every route leg, and the walk from each spawn to its post or
route, must be walkable through the templates. A failed check stops the script before anything is written.

Ground lines: a module's `ground` is the template layer that is the terrain surface. Its offset y is `-1 - ground`,
so `/es compound place <id>` where you stand sinks that layer into the block under your feet.
"""
import collections
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
from walk import Template, is_floor, is_passable  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), '..', '..'))
STRUCTURES = os.path.join(ROOT, 'src/main/resources/data/emergentstealth/structure')
OUT = os.path.join(ROOT, 'src/main/resources/data/emergentstealth/emergentstealth/compound/examples')

# Minecraft yaw: 0 faces south (+z), 90 west (-x), 180 north (-z), -90 east (+x).
SOUTH, WEST, NORTH, EAST = 0, 90, 180, -90


class Module:
    def __init__(self, template, x, z, ground, level=0):
        """`level` raises the module's ground line above the compound's (onto a terrace, say)."""
        self.template_id = template
        self.ground = ground
        self.offset = (x, level - 1 - ground, z)
        self.template = Template(os.path.join(STRUCTURES, template.split(':')[1] + '.nbt'))

    def at(self, x, y, z):
        """A block of this module in compound space."""
        return (x + self.offset[0], y + self.offset[1], z + self.offset[2])

    def box(self, a, b):
        return [list(self.at(*a)), list(self.at(*b))]

    def json(self):
        return {'template': self.template_id, 'offset': list(self.offset), 'ground': self.ground}


class World:
    """
    The compound's modules as one block lookup (unrotated modules only). Modules are placed in order, so where
    they overlap a later module's blocks win; a block a template doesn't store (structure void) leaves what was
    there, so a building on a site keeps the site's ground around it.
    """

    def __init__(self, modules):
        self.modules = modules
        self.blocks = {}
        for m in modules:
            for local, state in m.template.g.items():
                self.blocks[m.at(*local)] = (m.template.names[state], m.template.props[state])
        self._stands = None

    def block(self, p):
        return self.blocks.get(tuple(p), ('air', {}))

    def passable(self, p):
        return is_passable(*self.block(p))

    def can_stand(self, p):
        x, y, z = p
        return is_floor(*self.block((x, y - 1, z))) and self.passable(p) and self.passable((x, y + 1, z))

    def describe(self, p):
        x, y, z = p
        owners = [m.template_id for m in self.modules
                  if all(0 <= p[i] - m.offset[i] < m.template.size[i] for i in range(3))]
        return '%s: below %s, feet %s, head %s' % (owners[-1] if owners else 'outside every module',
                                                    self.block((x, y - 1, z))[0], self.block(p)[0],
                                                    self.block((x, y + 1, z))[0])

    def stands(self):
        if self._stands is None:
            self._stands = {(x, y + 1, z) for (x, y, z), b in self.blocks.items()
                            if is_floor(*b) and self.can_stand((x, y + 1, z))}
        return self._stands

    def path(self, a, b):
        """Walking distance between two stand positions, or None. Routes may cross modules."""
        stands = self.stands()
        a, b = tuple(a), tuple(b)
        prev = {a: None}
        queue = collections.deque([a])
        while queue:
            p = queue.popleft()
            if p == b:
                n = 0
                while prev[p] is not None:
                    p = prev[p]
                    n += 1
                return n
            x, y, z = p
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for dy in (1, 0, -1, -2, -3):
                    q = (x + dx, y + dy, z + dz)
                    if q in stands:
                        # Climbing needs headroom above where you are; dropping needs the way down clear.
                        if dy == 1 and not self.passable((x, y + 2, z)):
                            break
                        if dy < 0 and any(not self.passable((x + dx, y + k, z + dz)) for k in range(dy + 1, 1)):
                            break
                        if q not in prev:
                            prev[q] = p
                            queue.append(q)
                        break
        return None


def waypoint(pos, wait=0, look=None, relight=False):
    w = {'pos': list(pos)}
    if wait:
        w['wait_ticks'] = wait
    if look is not None:
        w['look_yaw'] = look
    if relight:
        w['relight'] = True
    return w


def route(name, mode, *waypoints):
    return {'name': name, 'mode': mode, 'waypoints': list(waypoints)}


def post(pos, yaw):
    return {'type': 'post', 'pos': list(pos), 'yaw': yaw}


def on_route(name):
    return {'type': 'route', 'route': name}


def wander(radius):
    return {'type': 'wander', 'radius': radius}


def spawn(archetype, pos, facing, *schedule, count=1):
    s = {'archetype': 'emergentstealth:' + archetype, 'pos': list(pos), 'facing': facing,
         'schedule': [{'from': f, 'to': t, 'activity': a} for f, t, a in schedule]}
    if count > 1:
        s['count'] = count
    return s


def zone(name, access, *boxes, hours=None):
    z = {'name': name, 'access': access, 'boxes': list(boxes)}
    if hours:
        z['hours'] = {'from': hours[0], 'to': hours[1]}
    return z


# --- The examples -----------------------------------------------------------------------------------------------

def samurai_fort():
    """The builder's samurai mini fort with a full garrison (doc 32 §5 step 5's first compound)."""
    fort = Module('emergentstealth:edo/samurai_mini_fort', 0, 0, ground=0)
    a = fort.at
    return [fort], {
        'zones': [
            zone('grounds', 'restricted', fort.box((2, 0, 2), (43, 15, 43))),
            zone('residence', 'hostile', fort.box((8, 1, 8), (18, 10, 18))),
            zone('storehouse', 'hostile', fort.box((30, 0, 8), (36, 8, 14)), hours=(18, 6)),
        ],
        'routes': [
            route('yard', 'loop',
                  waypoint(a(38, 1, 31), 60, EAST), waypoint(a(28, 1, 29)), waypoint(a(19, 1, 22)),
                  waypoint(a(19, 1, 9), 40, NORTH), waypoint(a(28, 1, 8)), waypoint(a(37, 1, 16), 40, EAST)),
            route('rampart', 'pingpong',
                  waypoint(a(5, 3, 20), 60, WEST), waypoint(a(5, 3, 5), 20),
                  waypoint(a(22, 3, 5), 40, NORTH), waypoint(a(39, 3, 5), 20), waypoint(a(39, 3, 20), 60, EAST)),
            route('lanterns', 'loop',
                  waypoint(a(19, 1, 9), 20, relight=True), waypoint(a(27, 1, 13), 20, relight=True),
                  waypoint(a(30, 1, 18), 20, relight=True), waypoint(a(31, 1, 27), 20, relight=True),
                  waypoint(a(23, 1, 28), 20, relight=True), waypoint(a(18, 1, 30), 20, relight=True),
                  waypoint(a(20, 1, 19), 20, relight=True)),
        ],
        'spawns': [
            # Two gate guards by day; at night one walks the yard and the other the rampart.
            spawn('ashigaru', a(37, 1, 30), EAST, (6, 18, post(a(37, 1, 30), EAST)), (18, 6, on_route('yard'))),
            spawn('ashigaru', a(37, 1, 32), EAST, (6, 18, post(a(37, 1, 32), EAST)), (18, 6, on_route('rampart'))),
            # Day patrols, swapping at dusk.
            spawn('ashigaru', a(28, 1, 29), NORTH, (6, 18, on_route('yard')), (18, 6, post(a(19, 1, 22), SOUTH))),
            spawn('ashigaru', a(5, 3, 20), WEST, (6, 18, on_route('rampart')), (18, 6, post(a(28, 1, 8), SOUTH))),
            # A samurai on the gate tower all day and night.
            spawn('samurai', a(40, 5, 31), EAST, (0, 0, post(a(40, 5, 31), EAST))),
            # The residence: a samurai at the door, the daimyo by the hearth, to bed at night.
            spawn('samurai', a(13, 2, 15), SOUTH, (0, 0, post(a(13, 2, 15), NORTH))),
            spawn('daimyo', a(13, 2, 10), SOUTH, (6, 20, post(a(13, 2, 10), SOUTH)), (20, 6, post(a(22, 2, 13), WEST))),
            # The lamplighter: odd jobs in the yard by day, the lantern round at dusk, asleep by the storehouse.
            spawn('labourer', a(28, 1, 25), SOUTH, (6, 17, wander(8)), (17, 23, on_route('lanterns')),
                  (23, 6, post(a(33, 1, 15), NORTH))),
        ],
    }


def shrine_watch():
    """The large shrine: open to visitors by day, watched and lit at night."""
    shrine = Module('emergentstealth:edo/large_shrine', 0, 0, ground=1)
    a = shrine.at
    return [shrine], {
        'zones': [
            zone('precinct', 'restricted', shrine.box((2, 0, 1), (32, 14, 41)), hours=(20, 6)),
            zone('honden', 'restricted', shrine.box((9, 2, 8), (25, 14, 18))),
        ],
        'routes': [
            route('night_round', 'loop',
                  waypoint(a(17, 1, 40), 60, SOUTH), waypoint(a(7, 2, 22)), waypoint(a(7, 2, 6), 40, NORTH),
                  waypoint(a(27, 2, 6), 40, NORTH), waypoint(a(28, 2, 22))),
            route('lanterns', 'loop',
                  waypoint(a(7, 2, 5), 20, relight=True), waypoint(a(17, 2, 5), 20, relight=True),
                  waypoint(a(28, 2, 5), 20, relight=True), waypoint(a(28, 2, 14), 20, relight=True),
                  waypoint(a(28, 2, 22), 20, relight=True), waypoint(a(6, 2, 22), 20, relight=True),
                  waypoint(a(6, 2, 13), 20, relight=True)),
        ],
        'spawns': [
            spawn('ashigaru', a(17, 1, 40), SOUTH, (6, 20, post(a(17, 1, 40), SOUTH)), (20, 6, on_route('night_round'))),
            spawn('samurai', a(17, 2, 20), SOUTH, (0, 0, post(a(17, 2, 20), SOUTH))),
            spawn('townsfolk', a(12, 2, 21), SOUTH, (6, 20, wander(6)), (20, 6, post(a(12, 2, 21), NORTH)), count=2),
            spawn('labourer', a(17, 2, 5), SOUTH, (6, 18, wander(6)), (18, 22, on_route('lanterns')),
                  (22, 6, post(a(17, 2, 5), SOUTH))),
        ],
    }


def cherry_grove_estate():
    """Two Cherry Grove modules pieced together: the teahouse behind the gatehouse, sharing one ground line."""
    gate = Module('emergentstealth:cherrygrove/gatehouse', 0, 0, ground=7)
    tea = Module('emergentstealth:cherrygrove/teahouse', -2, -35, ground=7)
    g, t = gate.at, tea.at
    return [gate, tea], {
        'zones': [
            zone('teahouse', 'restricted', tea.box((8, 7, 8), (32, 28, 24))),
            zone('gatehouse', 'restricted', gate.box((9, 7, 4), (33, 32, 32)), hours=(18, 6)),
        ],
        'routes': [
            route('garden', 'loop',
                  waypoint(g(5, 8, 33), 40, SOUTH), waypoint(g(5, 8, 3)), waypoint(t(5, 8, 30), 40, NORTH),
                  waypoint(t(38, 8, 29)), waypoint(t(40, 8, 12), 40, EAST), waypoint(t(38, 8, 28)),
                  waypoint(g(28, 8, 4)), waypoint(g(30, 8, 34), 40, SOUTH)),
            route('front', 'pingpong',
                  waypoint(g(1, 8, 35), 60, SOUTH), waypoint(g(20, 8, 33), 40, SOUTH), waypoint(g(31, 8, 35), 60, SOUTH)),
        ],
        'spawns': [
            spawn('ashigaru', g(20, 8, 33), SOUTH, (0, 0, on_route('front'))),
            spawn('ashigaru', g(5, 8, 33), NORTH, (6, 18, on_route('garden')), (18, 6, post(g(5, 8, 33), SOUTH))),
            spawn('ashigaru', t(38, 8, 29), NORTH, (18, 6, on_route('garden')), (6, 18, post(t(40, 8, 12), EAST))),
            spawn('taisho', t(20, 10, 20), SOUTH, (0, 0, post(t(20, 10, 20), SOUTH))),
            spawn('townsfolk', t(10, 8, 30), SOUTH, (0, 0, wander(8)), count=2),
        ],
    }


def takamori_castle():
    """
    A large castle pieced together on a generated site (tools/compounds/sites.py): a walled outer bailey with
    four corner towers, a gate tower and a postern; a middle terrace (ninomaru, 5 up) with the lord's residence
    and a shrine; an inner terrace (honmaru, 10 up) with the keep. Barracks and storehouses in the bailey.
    Stand heights: bailey 0, wall walk 4, ninomaru 5, honmaru 10.
    """
    site = Module('emergentstealth:sites/takamori_castle', 0, 0, ground=6)
    keep = Module('emergentstealth:edo/mini_castle', 97, 17, ground=0, level=10)
    residence = Module('emergentstealth:cherrygrove/sakuraresidence', 22, 14, ground=8, level=5)
    shrine = Module('emergentstealth:edo/medium_shrine', 30, 58, ground=0, level=5)
    barracks = Module('emergentstealth:cherrygrove/longhouse', 5, 98, ground=4)
    w = 4   # wall walk
    n = 5   # ninomaru
    h = 10  # honmaru
    return [site, keep, residence, shrine, barracks], {
        'zones': [
            zone('grounds', 'restricted', [[0, -8, 0], [149, 45, 149]]),
            zone('ninomaru', 'hostile', [[12, 3, 7], [137, 45, 87]], hours=(20, 6)),
            zone('honmaru', 'hostile', [[90, 8, 13], [134, 45, 58]]),
            zone('residence', 'hostile', [[22, 3, 14], [58, 30, 53]], hours=(18, 7)),
            zone('storehouses', 'hostile', [[125, -1, 94], [135, 8, 134]]),
        ],
        'routes': [
            # The walls in two halves, each walked back and forth: north and east from the north-west tower to
            # the gate house, and south and west back round.
            route('rampart_east', 'pingpong',
                  waypoint((4, w, 4), 40, NORTH), waypoint((22, w, 2)), waypoint((42, w, 2)), waypoint((62, w, 2)),
                  waypoint((82, w, 2), 40, NORTH), waypoint((102, w, 2)), waypoint((122, w, 2)),
                  waypoint((145, w, 4), 40, NORTH), waypoint((147, w, 22)), waypoint((147, w, 42)),
                  waypoint((147, w, 62), 40, EAST), waypoint((147, w, 82)), waypoint((147, w, 102)),
                  waypoint((147, w, 112), 60, EAST), waypoint((147, w, 132)), waypoint((145, w, 145), 40, SOUTH),
                  waypoint((127, w, 147)), waypoint((107, w, 147)), waypoint((87, w, 147)),
                  waypoint((76, w, 147), 60, SOUTH)),
            route('rampart_west', 'pingpong',
                  waypoint((73, w, 147), 60, SOUTH), waypoint((56, w, 147)), waypoint((36, w, 147)),
                  waypoint((16, w, 147)), waypoint((4, w, 145), 40, WEST), waypoint((2, w, 127)),
                  waypoint((2, w, 107)), waypoint((2, w, 87), 40, WEST), waypoint((2, w, 67)),
                  waypoint((2, w, 47)), waypoint((2, w, 27)), waypoint((4, w, 6), 40, WEST)),
            route('gate_road', 'pingpong',
                  waypoint((74, 0, 140), 40, SOUTH), waypoint((74, 0, 125)), waypoint((74, 0, 111), 20, EAST),
                  waypoint((74, 0, 100)), waypoint((74, 0, 92), 40, NORTH)),
            route('storehouses', 'loop',
                  waypoint((90, 0, 111)), waypoint((106, 0, 111)), waypoint((123, 0, 111)),
                  waypoint((123, 0, 98), 40, EAST), waypoint((123, 0, 111)), waypoint((123, 0, 120), 40, EAST),
                  waypoint((123, 0, 130), 40, EAST), waypoint((123, 0, 113)), waypoint((140, 0, 112), 60, EAST),
                  waypoint((123, 0, 112)), waypoint((106, 0, 112))),
            route('ninomaru', 'loop',
                  waypoint((74, n, 82), 40, SOUTH), waypoint((50, n, 83)), waypoint((25, n, 83)),
                  waypoint((16, n, 70)), waypoint((16, n, 55)), waypoint((16, n, 41), 40, WEST),
                  waypoint((16, n, 26)), waypoint((16, n, 11)), waypoint((28, n, 11)), waypoint((40, n, 11)),
                  waypoint((62, n, 11), 40, NORTH), waypoint((68, n, 21)), waypoint((75, n, 30)),
                  waypoint((78, n, 43)), waypoint((80, n, 55)), waypoint((88, n, 62), 40, EAST),
                  waypoint((78, n, 70)), waypoint((76, n, 78))),
            route('honmaru', 'loop',
                  waypoint((112, h, 55), 40, SOUTH), waypoint((95, h, 52)), waypoint((95, h, 35)),
                  waypoint((95, h, 18), 40, NORTH), waypoint((112, h, 16)), waypoint((129, h, 18), 40, NORTH),
                  waypoint((129, h, 35)), waypoint((129, h, 52), 40, EAST)),
            # Dusk: the gate road, the storehouses, the stair heads.
            route('lanterns', 'pingpong',
                  waypoint((74, 0, 130), 20, relight=True), waypoint((74, 0, 115), 20, relight=True),
                  waypoint((90, 0, 111)), waypoint((106, 0, 111)), waypoint((123, 0, 105), 20, relight=True),
                  waypoint((123, 0, 125), 20, relight=True), waypoint((106, 0, 112)), waypoint((90, 0, 111)),
                  waypoint((74, 0, 100), 20, relight=True), waypoint((74, 0, 92)), waypoint((74, n, 82), 20, relight=True),
                  waypoint((80, n, 70)), waypoint((95, n, 63)), waypoint((112, n, 63)),
                  waypoint((112, h, 53), 20, relight=True)),
        ],
        'spawns': [
            # The gate: two ashigaru in the passage, one walks the gate road at night; a samurai over the gate.
            spawn('ashigaru', (73, 0, 142), SOUTH, (0, 0, post((73, 0, 142), SOUTH))),
            spawn('ashigaru', (76, 0, 142), SOUTH, (6, 20, post((76, 0, 142), SOUTH)), (20, 6, on_route('gate_road'))),
            spawn('samurai', (74, w, 146), SOUTH, (0, 0, post((74, w, 146), SOUTH))),
            # The walls: one half walked all day, the other at night (by day that guard keeps the north-west tower).
            spawn('ashigaru', (4, w, 4), NORTH, (0, 0, on_route('rampart_east'))),
            spawn('ashigaru', (73, w, 147), SOUTH, (18, 6, on_route('rampart_west')), (6, 18, post((4, w, 4), NORTH))),
            # The postern, watched from inside.
            spawn('ashigaru', (144, 0, 112), EAST, (0, 0, post((144, 0, 112), EAST))),
            # Storehouses: a guard by the doors by day, walking them at night.
            spawn('ashigaru', (123, 0, 111), EAST, (6, 18, post((123, 0, 111), EAST)), (18, 6, on_route('storehouses'))),
            # Off-duty ashigaru about the barracks, inside at night.
            spawn('ashigaru', (40, 0, 93), SOUTH, (6, 21, wander(8)), (21, 6, post((36, 1, 113), SOUTH)), count=2),
            # The ninomaru: one patrol by day, one by night; the other keeps a stair head.
            spawn('ashigaru', (74, n, 82), SOUTH, (6, 18, on_route('ninomaru')), (18, 6, post((74, n, 84), SOUTH))),
            spawn('ashigaru', (16, n, 41), WEST, (18, 6, on_route('ninomaru')), (6, 18, post((16, n, 41), WEST))),
            # The honmaru: a samurai patrol round the keep, a samurai on the keep steps, the taisho inside.
            spawn('samurai', (112, h, 55), SOUTH, (0, 0, on_route('honmaru'))),
            spawn('samurai', (112, h, 50), SOUTH, (0, 0, post((112, h, 50), SOUTH))),
            spawn('taisho', (109, 16, 40), SOUTH, (0, 0, post((109, 16, 40), SOUTH))),
            spawn('samurai', (114, 23, 30), SOUTH, (20, 6, post((114, 23, 30), SOUTH)), (6, 20, post((106, 16, 36), SOUTH))),
            # The residence: the daimyo and two servants; a priest at the shrine.
            spawn('daimyo', (39, 7, 34), SOUTH, (7, 21, post((39, 7, 34), SOUTH)), (21, 7, post((41, 8, 25), SOUTH))),
            spawn('townsfolk', (35, n, 22), SOUTH, (6, 20, wander(6)), (20, 6, post((35, n, 22), SOUTH)), count=2),
            spawn('townsfolk', (47, n, 81), NORTH, (6, 19, wander(5)), (19, 6, post((47, n, 81), NORTH))),
            # The lamplighter: errands in the bailey, the lantern round at dusk, the barracks at night.
            spawn('labourer', (100, 0, 111), SOUTH, (6, 17, wander(10)), (17, 23, on_route('lanterns')),
                  (23, 6, post((36, 1, 113), SOUTH))),
        ],
    }


EXAMPLES = {
    'samurai_fort': samurai_fort,
    'shrine_watch': shrine_watch,
    'cherry_grove_estate': cherry_grove_estate,
    'takamori_castle': takamori_castle,
}


# --- Checking and writing ---------------------------------------------------------------------------------------

def check(name, modules, markers):
    world = World(modules)
    errors = []
    routes = {r['name']: r for r in markers['routes']}

    def stand(p, what):
        if not world.can_stand(tuple(p)):
            errors.append('%s: %s is not a place to stand (%s)' % (what, p, world.describe(tuple(p))))
            return False
        return True

    def walk(a, b, what):
        if world.path(tuple(a), tuple(b)) is None:
            errors.append('%s: no walk from %s to %s' % (what, a, b))

    for r in markers['routes']:
        points = [w['pos'] for w in r['waypoints']]
        ok = all([stand(p, 'route %s' % r['name']) for p in points])
        if ok:
            legs = list(zip(points, points[1:])) + ([(points[-1], points[0])] if r['mode'] == 'loop' else [])
            for p, q in legs:
                walk(p, q, 'route %s' % r['name'])
    for i, s in enumerate(markers['spawns']):
        what = 'spawn %d (%s)' % (i, s['archetype'])
        if not stand(s['pos'], what):
            continue
        for entry in s['schedule']:
            activity = entry['activity']
            if activity['type'] == 'post':
                if stand(activity['pos'], what + ' post'):
                    walk(s['pos'], activity['pos'], what + ' post')
            elif activity['type'] == 'route':
                r = routes.get(activity['route'])
                if r is None:
                    errors.append('%s: no route %s' % (what, activity['route']))
                elif all(world.can_stand(tuple(w['pos'])) for w in r['waypoints']):
                    walk(s['pos'], r['waypoints'][0]['pos'], what + ' to route ' + r['name'])
    for e in errors:
        print('%s: %s' % (name, e))
    return not errors


def to_json(compound):
    """Pretty JSON with positions on one line, like the mod's own saved compounds."""
    text = json.dumps(compound, indent=2)
    return re.sub(r'\[\s*(-?[0-9.]+(?:,\s*-?[0-9.]+)*)\s*\]',
                  lambda m: '[' + re.sub(r',\s*', ', ', m.group(1)) + ']', text) + '\n'


def main():
    check_only = '--check' in sys.argv
    built = {}
    ok = True
    for name, make in EXAMPLES.items():
        modules, markers = make()
        ok &= check(name, modules, markers)
        built[name] = {'structures': [m.json() for m in modules], **markers}
    if not ok:
        sys.exit('Not written: fix the markers above.')
    print('All markers stand and connect.')
    if check_only:
        return
    os.makedirs(OUT, exist_ok=True)
    for name, compound in built.items():
        with open(os.path.join(OUT, name + '.json'), 'w') as f:
            f.write(to_json(compound))
        print('wrote', os.path.relpath(os.path.join(OUT, name + '.json'), ROOT))


if __name__ == '__main__':
    main()
