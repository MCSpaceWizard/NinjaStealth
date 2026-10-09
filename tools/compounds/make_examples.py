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
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
from walk import Template  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), '..', '..'))
STRUCTURES = os.path.join(ROOT, 'src/main/resources/data/emergentstealth/structure')
OUT = os.path.join(ROOT, 'src/main/resources/data/emergentstealth/emergentstealth/compound/examples')

# Minecraft yaw: 0 faces south (+z), 90 west (-x), 180 north (-z), -90 east (+x).
SOUTH, WEST, NORTH, EAST = 0, 90, 180, -90


class Module:
    def __init__(self, template, x, z, ground):
        self.template_id = template
        self.ground = ground
        self.offset = (x, -1 - ground, z)
        self.template = Template(os.path.join(STRUCTURES, template.split(':')[1] + '.nbt'))

    def at(self, x, y, z):
        """A block of this module in compound space."""
        return (x + self.offset[0], y + self.offset[1], z + self.offset[2])

    def box(self, a, b):
        return [list(self.at(*a)), list(self.at(*b))]

    def json(self):
        return {'template': self.template_id, 'offset': list(self.offset), 'ground': self.ground}


class World:
    """The compound's modules as one block lookup (unrotated modules only)."""

    def __init__(self, modules):
        self.modules = modules

    def _find(self, p):
        for m in self.modules:
            local = (p[0] - m.offset[0], p[1] - m.offset[1], p[2] - m.offset[2])
            sx, sy, sz = m.template.size
            if 0 <= local[0] < sx and 0 <= local[2] < sz and 0 <= local[1] < sy:
                return m, local
        return None, None

    def can_stand(self, p):
        m, local = self._find(p)
        return m is not None and m.template.can_stand(local)

    def describe(self, p):
        m, local = self._find(p)
        if m is None:
            return 'outside every module'
        t = m.template
        return '%s %s: below %s, feet %s, head %s' % (m.template_id, local, t.name((local[0], local[1] - 1, local[2])),
                                                       t.name(local), t.name((local[0], local[1] + 1, local[2])))

    def path(self, a, b):
        """Walking distance between two stand positions, or None. Routes may cross modules: search all of them."""
        import collections
        if not hasattr(self, '_stands'):
            self._stands = set()
            for m in self.modules:
                self._stands |= {m.at(*s) for s in m.template.stands()}
        stands = self._stands
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


EXAMPLES = {
    'samurai_fort': samurai_fort,
    'shrine_watch': shrine_watch,
    'cherry_grove_estate': cherry_grove_estate,
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
