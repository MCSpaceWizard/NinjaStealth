"""Walkability over a structure template: stand positions, BFS reachability."""
import nbtlib, collections
AIR = {'air','cave_air','void_air'}
SOFT = ('fern','grass','petals','flower','poppy','dandelion','tulip','orchid','allium','bluet','daisy','cornflower','lilac','rose_bush','peony','sapling','torch','sign','banner','button','pressure_plate','carpet','door','gate','vine','sugar_cane','short_','tall_grass','tall_seagrass','leaf_litter','dead_bush','rail','ladder','snow')
def is_passable(nm, pr):
    if nm in AIR or nm == 'light': return True
    if nm.endswith('_wall') or nm.endswith('fence') or 'sweet_berry' in nm or 'cobweb' in nm: return False
    if nm.endswith('trapdoor'): return pr.get('open','false')=='true'  # what the game's pathfinding says
    if nm.endswith('_fence_gate'): return True  # NPCs open gates
    if nm.endswith('_door'): return True
    if nm=='water' or nm.endswith('_block') or nm.endswith('_planks'): return False
    return any(w in nm for w in SOFT)
def is_floor(nm, pr):
    if nm in AIR or nm=='water' or nm=='lava': return False
    if 'leaves' in nm or nm.endswith('fence') or nm.endswith('_wall') or 'fence_gate' in nm or 'pane' in nm or 'bars' in nm: return False
    if nm.endswith('_door') : return False
    if nm.endswith('trapdoor'): return pr.get('open','false')=='false'
    if nm.endswith('_slab') and pr.get('type')=='bottom': return True
    if is_passable(nm, pr): return False
    return True
class Template:
    def __init__(self, path):
        n = nbtlib.load(path)
        self.size = [int(v) for v in n['size']]
        pal = n['palette'] if 'palette' in n else n['palettes'][0]
        self.names = [str(p['Name']).split(':')[1] for p in pal]
        self.props = [{str(k):str(v) for k,v in dict(p.get('Properties',{})).items()} for p in pal]
        self.g = {}
        for b in n['blocks']:
            self.g[tuple(int(v) for v in b['pos'])] = int(b['state'])
        self._stand = None
    def block(self, p):
        s = self.g.get(tuple(p)); return ('air',{}) if s is None else (self.names[s], self.props[s])
    def name(self, p): return self.block(p)[0]
    def passable(self, p):
        x,y,z=p; sx,sy,sz=self.size
        if not (0<=x<sx and 0<=z<sz) or y>=sy: return True
        return is_passable(*self.block(p))
    def can_stand(self, p):
        x,y,z = p
        return is_floor(*self.block((x,y-1,z))) and self.passable((x,y,z)) and self.passable((x,y+1,z))
    def stands(self):
        if self._stand is None:
            sx,sy,sz=self.size; s=set()
            for x in range(sx):
                for z in range(sz):
                    for y in range(1,sy+1):
                        if self.can_stand((x,y,z)): s.add((x,y,z))
            self._stand=s
        return self._stand
    def neighbours(self, p):
        st=self.stands(); x,y,z=p
        for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
            for dy in (1,0,-1,-2,-3):
                q=(x+dx,y+dy,z+dz)
                if q in st:
                    if dy==1 and not self.passable((x,y+2,z)): continue
                    if dy<0 and any(not self.passable((x+dx,y+k,z+dz)) for k in range(dy+1,1)): continue
                    yield q; break
    def path(self, a, b):
        a,b=tuple(a),tuple(b); prev={a:None}; dq=collections.deque([a])
        while dq:
            p=dq.popleft()
            if p==b:
                n=0
                while prev[p] is not None: p=prev[p]; n+=1
                return n
            for q in self.neighbours(p):
                if q not in prev: prev[q]=p; dq.append(q)
        return None
