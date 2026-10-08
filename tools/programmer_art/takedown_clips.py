"""Programmer-art takedown clips (design doc 17 §3). Run from the repo root: python3 tools/programmer_art/takedown_clips.py

Attacker: PAL Bedrock JSON (degrees, absolute vanilla part values; bends as [deg, 0, 0]). Victim: NeoForge entity
animation JSON (degrees, added to the rest pose). Existing files are never overwritten (they may be real animation)
unless --force is given."""
import json, os, sys
FORCE = '--force' in sys.argv


def write(path, data):
    if os.path.exists(path) and not FORCE:
        print('kept', path)
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')
    print('wrote', path)

ROOT = 'src/main/resources/assets/emergentstealth'

# --- Attacker clips (PAL). Each bone: {channel: [(time, value)]}; rotation [x,y,z] deg, bend deg, position px (y up).
attacker = {
  # Choke from behind, 2.0 s at base speed: wrap the right arm round the neck, left hand behind the head,
  # lean back and sway through the struggle, ease the victim down at the end.
  'takedown/rear_nonlethal.attacker': (2.0, {
    'right_arm': {'rotation': [(0.0, [-30, 0, 0]), (0.25, [-82, -32, 4]), (0.9, [-86, -30, 6]), (1.5, [-80, -34, 2]),
                               (1.8, [-70, -28, 4]), (2.0, [-45, -12, 6])],
                  'bend': [(0.0, -10), (0.25, -78), (1.8, -72), (2.0, -35)]},
    'left_arm': {'rotation': [(0.0, [-30, 0, 0]), (0.3, [-112, 38, -6]), (1.8, [-108, 34, -4]), (2.0, [-60, 15, -8])],
                 'bend': [(0.0, -10), (0.3, -100), (1.8, -96), (2.0, -40)]},
    'torso': {'rotation': [(0.0, [0, 0, 0]), (0.3, [-7, 0, 0]), (1.8, [-6, 0, 0]), (2.0, [4, 0, 0])]},
    'right_leg': {'rotation': [(0.0, [0, 0, 0]), (0.3, [-14, 0, 7]), (1.8, [-14, 0, 7]), (2.0, [-6, 0, 4])],
                  'bend': [(0.0, 0), (0.3, 22), (1.8, 22), (2.0, 10)]},
    'left_leg': {'rotation': [(0.0, [0, 0, 0]), (0.3, [10, 0, -6]), (1.8, [10, 0, -6]), (2.0, [4, 0, -3])],
                 'bend': [(0.0, 0), (0.3, 12), (1.8, 12), (2.0, 6)]},
    'body': {'rotation': [(0.0, [0, 0, 0]), (0.5, [0, 0, 3]), (0.8, [0, 0, -3]), (1.1, [0, 0, 2.5]), (1.4, [0, 0, -2]),
                          (1.7, [0, 0, 1]), (2.0, [0, 0, 0])],
             'position': [(0.0, [0, 0, 0]), (0.3, [0, -1.5, 0]), (1.8, [0, -1.5, 0]), (2.0, [0, -2.5, 0])]},
  }),
  # Grab and strike, 0.75 s: the left hand grabs the face, the right hand draws back and drives in (impact 0.4 s).
  'takedown/rear_lethal.attacker': (0.75, {
    'left_arm': {'rotation': [(0.0, [-30, 0, 0]), (0.15, [-105, 35, -4]), (0.6, [-100, 30, -4]), (0.75, [-40, 10, -6])],
                 'bend': [(0.0, -10), (0.15, -70), (0.6, -70), (0.75, -25)]},
    'right_arm': {'rotation': [(0.0, [-30, 0, 0]), (0.2, [-125, 8, 18]), (0.4, [-78, -26, 2]), (0.6, [-74, -24, 2]),
                               (0.75, [-35, -6, 6])],
                  'bend': [(0.0, -10), (0.2, -85), (0.4, -6), (0.75, -15)]},
    'torso': {'rotation': [(0.0, [0, 0, 0]), (0.2, [-4, 8, 0]), (0.4, [8, -6, 0]), (0.75, [2, 0, 0])]},
    'right_leg': {'rotation': [(0.0, [0, 0, 0]), (0.4, [-16, 0, 5]), (0.75, [-6, 0, 3])],
                  'bend': [(0.0, 0), (0.4, 24), (0.75, 8)]},
    'left_leg': {'rotation': [(0.0, [0, 0, 0]), (0.4, [12, 0, -4]), (0.75, [4, 0, -2])]},
  }),
  # Land on the victim, 0.5 s: deep crouch on impact, then rise. Non-lethal pins with both hands.
  'takedown/air_nonlethal.attacker': (0.5, {
    'body': {'position': [(0.0, [0, -4, 0]), (0.1, [0, -6, 0]), (0.5, [0, 0, 0])]},
    'torso': {'rotation': [(0.0, [28, 0, 0]), (0.1, [32, 0, 0]), (0.5, [0, 0, 0])]},
    'right_arm': {'rotation': [(0.0, [-75, -12, 0]), (0.1, [-60, -10, 0]), (0.5, [-10, 0, 0])],
                  'bend': [(0.0, -30), (0.1, -20), (0.5, -5)]},
    'left_arm': {'rotation': [(0.0, [-75, 12, 0]), (0.1, [-60, 10, 0]), (0.5, [-10, 0, 0])],
                 'bend': [(0.0, -30), (0.1, -20), (0.5, -5)]},
    'right_leg': {'rotation': [(0.0, [-50, 0, 6]), (0.1, [-58, 0, 6]), (0.5, [0, 0, 0])],
                  'bend': [(0.0, 85), (0.1, 95), (0.5, 0)]},
    'left_leg': {'rotation': [(0.0, [-30, 0, -6]), (0.1, [-38, 0, -6]), (0.5, [0, 0, 0])],
                 'bend': [(0.0, 70), (0.1, 80), (0.5, 0)]},
  }),
  # Lethal: the same landing, the right hand driving down.
  'takedown/air_lethal.attacker': (0.5, {
    'body': {'position': [(0.0, [0, -4, 0]), (0.1, [0, -6, 0]), (0.5, [0, 0, 0])]},
    'torso': {'rotation': [(0.0, [30, 0, 0]), (0.1, [34, 0, 0]), (0.5, [0, 0, 0])]},
    'right_arm': {'rotation': [(0.0, [-150, 0, 10]), (0.1, [-55, -10, 0]), (0.5, [-15, 0, 0])],
                  'bend': [(0.0, -40), (0.1, -5), (0.5, -5)]},
    'left_arm': {'rotation': [(0.0, [-40, 0, -25]), (0.1, [-30, 0, -20]), (0.5, [-5, 0, 0])]},
    'right_leg': {'rotation': [(0.0, [-50, 0, 6]), (0.1, [-58, 0, 6]), (0.5, [0, 0, 0])],
                  'bend': [(0.0, 85), (0.1, 95), (0.5, 0)]},
    'left_leg': {'rotation': [(0.0, [-30, 0, -6]), (0.1, [-38, 0, -6]), (0.5, [0, 0, 0])],
                 'bend': [(0.0, 70), (0.1, 80), (0.5, 0)]},
  }),
}

def num(v):
    return round(v, 4)

def pal_channel(frames):
    out = {}
    for t, v in frames:
        out[str(float(t))] = [num(x) for x in v] if isinstance(v, list) else [num(v), 0, 0]  # bends: [deg, 0, 0]
    return out

anims = {}
for name, (length, bones) in attacker.items():
    anims[name] = {'animation_length': length,
                   'bones': {bone: {ch: pal_channel(fr) for ch, fr in chans.items()} for bone, chans in bones.items()}}
write(f'{ROOT}/player_animations/takedowns.json', {'format_version': '1.8.0', 'animations': anims})

# --- Victim clips (NeoForge). Rotation [x,y,z] deg added to rest; position px (y up).
victim = {
  # Grabbed from behind: head pulled back, hands claw at the arm round the throat, legs kick, then limp (1.8 s).
  'takedown/rear_nonlethal.victim': (2.0, {
    'head': {'rotation': [(0.0, [0, 0, 0]), (0.25, [-28, 0, 0]), (1.0, [-24, 8, 0]), (1.6, [-20, -6, 0]), (1.8, [-10, 0, 0]),
                          (2.0, [25, 10, 0])]},
    'right_arm': {'rotation': [(0.0, [0, 0, 0]), (0.3, [-130, 25, -10]), (0.7, [-120, 30, -20]), (1.1, [-135, 20, -8]),
                               (1.5, [-110, 25, -15]), (1.8, [-60, 10, 10]), (2.0, [-5, 0, 8])]},
    'left_arm': {'rotation': [(0.0, [0, 0, 0]), (0.3, [-125, -25, 10]), (0.8, [-135, -20, 15]), (1.2, [-115, -30, 8]),
                              (1.5, [-125, -25, 12]), (1.8, [-50, -10, -10]), (2.0, [-5, 0, -8])]},
    'right_leg': {'rotation': [(0.0, [0, 0, 0]), (0.5, [-25, 0, 0]), (0.8, [10, 0, 0]), (1.2, [-30, 0, 0]), (1.5, [5, 0, 0]),
                               (1.8, [-5, 0, 4]), (2.0, [-15, 0, 6])]},
    'left_leg': {'rotation': [(0.0, [0, 0, 0]), (0.6, [12, 0, 0]), (1.0, [-28, 0, 0]), (1.4, [8, 0, 0]), (1.8, [-5, 0, -4]),
                              (2.0, [-12, 0, -6])]},
    'body': {'rotation': [(0.0, [0, 0, 0]), (0.3, [-10, 0, 0]), (1.8, [-8, 0, 0]), (2.0, [8, 0, 0])]},
  }),
  # Grabbed and struck (impact 0.4 s): arms jerk up, then everything goes slack.
  'takedown/rear_lethal.victim': (0.75, {
    'head': {'rotation': [(0.0, [0, 0, 0]), (0.15, [-30, 0, 0]), (0.4, [-35, 0, 0]), (0.75, [20, 0, 0])]},
    'right_arm': {'rotation': [(0.0, [0, 0, 0]), (0.2, [-70, 0, 30]), (0.4, [-90, 0, 40]), (0.75, [-10, 0, 10])]},
    'left_arm': {'rotation': [(0.0, [0, 0, 0]), (0.2, [-70, 0, -30]), (0.4, [-90, 0, -40]), (0.75, [-10, 0, -10])]},
    'body': {'rotation': [(0.0, [0, 0, 0]), (0.4, [-14, 0, 0]), (0.75, [10, 0, 0])]},
    'right_leg': {'rotation': [(0.0, [0, 0, 0]), (0.4, [5, 0, 3]), (0.75, [-15, 0, 4])]},
    'left_leg': {'rotation': [(0.0, [0, 0, 0]), (0.4, [5, 0, -3]), (0.75, [-10, 0, -4])]},
  }),
  # Landed on: crushed down at once.
  'takedown/air_nonlethal.victim': (0.5, {
    'head': {'rotation': [(0.0, [30, 0, 0]), (0.5, [40, 0, 0])]},
    'body': {'rotation': [(0.0, [25, 0, 0]), (0.5, [30, 0, 0])], 'position': [(0.0, [0, -3, 0]), (0.5, [0, -4, 0])]},
    'right_arm': {'rotation': [(0.0, [-40, 0, 35]), (0.5, [-20, 0, 25])]},
    'left_arm': {'rotation': [(0.0, [-40, 0, -35]), (0.5, [-20, 0, -25])]},
    'right_leg': {'rotation': [(0.0, [-30, 0, 8]), (0.5, [-40, 0, 10])]},
    'left_leg': {'rotation': [(0.0, [-20, 0, -8]), (0.5, [-30, 0, -10])]},
  }),
}
victim['takedown/air_lethal.victim'] = victim['takedown/air_nonlethal.victim']

def neo_channel(bone, target, frames):
    return {'bone': bone, 'target': f'minecraft:{target}',
            'keyframes': [{'timestamp': t, 'target': [num(x) for x in v], 'interpolation': 'minecraft:catmullrom'} for t, v in frames]}

for name, (length, bones) in victim.items():
    channels = [neo_channel(bone, ch, fr) for bone, chans in bones.items() for ch, fr in chans.items()]
    write(f'{ROOT}/neoforge/animations/entity/{name}.json', {'length': length, 'animations': channels})
