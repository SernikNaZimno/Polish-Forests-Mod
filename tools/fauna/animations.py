"""Animations of tools/fauna: gaits and poses of the quadruped archetype, written as Bedrock .animation.json (1.8.0).

Keyframe values are offsets from the rest pose (rotation in degrees with the axis signs of model.py: +X turns the front
of a bone down; position in pixels). Loops are sampled densely (every 1/16 of the cycle) and interpolated linearly by
GeckoLib, so the curves stay smooth without easing functions. Names follow GeckoLib's DefaultAnimations where one exists
(misc.idle, move.walk, move.run, misc.rest); misc.eat and misc.alert are own names of the slice.

Gaits (roe deer; leg phases as fractions of the cycle):
- walk: four-beat lateral sequence (hind left 0, fore left 0.25, hind right 0.5, fore right 0.75), 1.1 s;
- run: the bounding gallop of a fleeing roe deer, fore legs and hind legs nearly in pairs, the body pitching and
  rising, 0.55 s.
"""
import math

LEGS = ('leg_front_left', 'leg_front_right', 'leg_back_left', 'leg_back_right')


def _r(v):
    return [round(float(x), 3) + 0.0 for x in v]


def sample(length, fn, steps=16):
    """{time: value} for t = 0 .. length (inclusive) of a periodic function of the phase 0..1."""
    return {f'{length * k / steps:.4f}': _r(fn(k / steps)) for k in range(steps + 1)}


def keys(points):
    """{time: value} from (time, value) pairs."""
    return {f'{t:.4f}': _r(v) for t, v in points}


def leg_swing(phase, amp):
    """Rotation about X of a leg at a phase of its cycle. A leg points down from its pivot, and a positive X angle turns
    the front of a bone down, so it moves the hoof BACK; the leg swings forward (negative angle) in the first half of the
    cycle and pushes back in the second."""
    s = math.sin(2 * math.pi * phase)
    return [-amp * s, 0, 0]


def walk(length=1.1, amp=20.0):
    phases = {'leg_back_left': 0.0, 'leg_front_left': 0.25, 'leg_back_right': 0.5, 'leg_front_right': 0.75}
    bones = {b: {'rotation': sample(length, lambda t, ph=ph: leg_swing(t + ph, amp))} for b, ph in phases.items()}
    bones['body'] = {'position': sample(length, lambda t: [0, 0.18 * abs(math.sin(2 * math.pi * t)), 0]),
                     'rotation': sample(length, lambda t: [0, 0, 1.2 * math.sin(2 * math.pi * t)])}
    bones['neck'] = {'rotation': sample(length, lambda t: [3.0 * math.sin(4 * math.pi * t + 0.6), 0, 0])}
    bones['head'] = {'rotation': sample(length, lambda t: [-2.0 * math.sin(4 * math.pi * t + 0.6), 0, 0])}
    bones['tail'] = {'rotation': sample(length, lambda t: [0, 0, 4.0 * math.sin(2 * math.pi * t)])}
    return {'loop': True, 'animation_length': length, 'bones': bones}


def run(length=0.55):
    # Bound: fore legs reach forward while the hind legs push, then swap; the pairs are slightly offset.
    def fore(t, off):
        return [-42.0 * math.sin(2 * math.pi * (t + off)), 0, 0]

    def hind(t, off):
        return [-38.0 * math.sin(2 * math.pi * (t + off) + math.pi), 0, 0]

    bones = {
        'leg_front_left': {'rotation': sample(length, lambda t: fore(t, 0.0))},
        'leg_front_right': {'rotation': sample(length, lambda t: fore(t, 0.06))},
        'leg_back_left': {'rotation': sample(length, lambda t: hind(t, 0.0))},
        'leg_back_right': {'rotation': sample(length, lambda t: hind(t, 0.06))},
        'body': {'position': sample(length, lambda t: [0, 0.9 + 0.9 * math.sin(2 * math.pi * t), 0]),
                 'rotation': sample(length, lambda t: [-7.0 * math.sin(2 * math.pi * t + 0.5 * math.pi), 0, 0])},
        # neck stretched forward and the head raised against it; ears laid back, the tail (rump hair) up
        'neck': {'rotation': sample(length, lambda t: [6.0 * math.sin(2 * math.pi * t + 0.5 * math.pi) + 10.0, 0, 0])},
        'head': {'rotation': sample(length, lambda t: [-4.0 * math.sin(2 * math.pi * t + 0.5 * math.pi) - 6.0, 0, 0])},
        'ear_left': {'rotation': keys([(0, [-20, 0, -6]), (length, [-20, 0, -6])])},
        'ear_right': {'rotation': keys([(0, [-20, 0, 6]), (length, [-20, 0, 6])])},
        'tail': {'rotation': keys([(0, [35, 0, 0]), (length, [35, 0, 0])])},
    }
    return {'loop': True, 'animation_length': length, 'bones': bones}


def idle(length=6.0):
    """Standing: slow breathing of the body, a look to the side, ear flicks and a tail flick."""
    bones = {
        'body': {'position': sample(length, lambda t: [0, 0.06 * math.sin(2 * math.pi * 3 * t), 0], steps=24)},
        'neck': {'rotation': keys([(0, [0, 0, 0]), (1.6, [-3, 0, 0]), (2.4, [-3, 0, 0]), (3.4, [0, 0, 0]), (length, [0, 0, 0])])},
        'head': {'rotation': keys([(0, [0, 0, 0]), (1.6, [0, 14, 0]), (2.6, [2, 14, 0]), (3.4, [0, 0, 0]), (length, [0, 0, 0])])},
        'ear_left': {'rotation': keys([(0, [0, 0, 0]), (4.3, [0, 0, 0]), (4.4, [-12, 0, 18]), (4.55, [0, 0, 0]), (length, [0, 0, 0])])},
        'ear_right': {'rotation': keys([(0, [0, 0, 0]), (1.0, [0, 0, 0]), (1.1, [-12, 0, -18]), (1.25, [0, 0, 0]),
                                        (5.0, [0, 0, 0]), (5.1, [-8, 0, -12]), (5.25, [0, 0, 0]), (length, [0, 0, 0])])},
        'tail': {'rotation': keys([(0, [0, 0, 0]), (3.0, [0, 0, 0]), (3.1, [0, 0, 18]), (3.2, [0, 0, -18]), (3.3, [0, 0, 0]),
                                   (length, [0, 0, 0])])},
    }
    return {'loop': True, 'animation_length': length, 'bones': bones}


# Grazing: the trunk tips forward by this angle (the legs turned back against it keep the hooves within 0.3 px of the
# ground) and the head is turned down by EAT_HEAD; the neck angle comes from grazing_neck_angle().
EAT_BODY = 3.0
EAT_HEAD = 20.0
EAT_NOSE = 3.0


def eat_pose(neck_down):
    return {'body': {'rotation': (EAT_BODY, 0, 0)}, 'neck': {'rotation': (neck_down, 0, 0)},
            'head': {'rotation': (EAT_HEAD, 0, 0)},
            'leg_front_left': {'rotation': (-EAT_BODY, 0, -3)}, 'leg_front_right': {'rotation': (-EAT_BODY, 0, 3)},
            'leg_back_left': {'rotation': (-EAT_BODY, 0, 0)}, 'leg_back_right': {'rotation': (-EAT_BODY, 0, 0)}}


def eat(neck_down, length=3.2):
    """Grazing: the neck goes down to the grass, the head nips and chews, then the cycle repeats from the low pose."""
    down = 0.5
    low = eat_pose(neck_down)
    bones = {}
    for b, p in low.items():
        r = list(p['rotation'])
        if b == 'head':
            bones[b] = {'rotation': keys([(0, [0, 0, 0]), (down, r)]
                                         + [(down + 0.35 * k, [r[0] + (5 if k % 2 else 0), 0, 0]) for k in range(1, 7)]
                                         + [(length, r)])}
        else:
            bones[b] = {'rotation': keys([(0, [0, 0, 0]), (down, r), (length, r)])}
    bones['ear_left'] = {'rotation': keys([(0, [0, 0, 0]), (2.2, [0, 0, 0]), (2.3, [-10, 0, 14]), (2.45, [0, 0, 0]),
                                           (length, [0, 0, 0])])}
    bones['tail'] = {'rotation': keys([(0, [0, 0, 0]), (1.4, [0, 0, 14]), (1.5, [0, 0, -14]), (1.6, [0, 0, 0]),
                                       (length, [0, 0, 0])])}
    return {'loop': True, 'animation_length': length, 'bones': bones}


def alert(length=0.45):
    """Alarm: neck up, head level and turned a little, ears forward and up, the rump hair raised (tail up), one fore
    leg lifted; held until the controller changes the animation."""
    bones = {
        'neck': {'rotation': keys([(0, [0, 0, 0]), (length, [-16, 0, 0])])},
        'head': {'rotation': keys([(0, [0, 0, 0]), (length, [-8, 6, 0])])},
        'ear_left': {'rotation': keys([(0, [0, 0, 0]), (length, [10, 0, -12])])},
        'ear_right': {'rotation': keys([(0, [0, 0, 0]), (length, [10, 0, 12])])},
        'tail': {'rotation': keys([(0, [0, 0, 0]), (length, [60, 0, 0])])},
        'leg_front_left': {'rotation': keys([(0, [0, 0, 0]), (length, [-38, 0, 0])])},
        'body': {'rotation': keys([(0, [0, 0, 0]), (length, [-2, 0, 0])])},
    }
    return {'loop': 'hold_on_last_frame', 'animation_length': length, 'bones': bones}


def rest(drop, length=8.0):
    """Lying: the body lowered to the ground with the legs folded under it, neck upright, slow breathing and an ear
    flick; loops in place."""
    bones = {
        'body': {'position': sample(length, lambda t: [0, -drop + 0.08 * math.sin(2 * math.pi * 4 * t), 0], steps=32)},
        'leg_front_left': {'rotation': keys([(0, [88, 0, -6]), (length, [88, 0, -6])])},
        'leg_front_right': {'rotation': keys([(0, [84, 0, 8]), (length, [84, 0, 8])])},
        'leg_back_left': {'rotation': keys([(0, [-84, 0, -10]), (length, [-84, 0, -10])])},
        'leg_back_right': {'rotation': keys([(0, [-80, 0, 12]), (length, [-80, 0, 12])])},
        'neck': {'rotation': keys([(0, [-10, 0, 0]), (length, [-10, 0, 0])])},
        'head': {'rotation': keys([(0, [6, 0, 0]), (3.0, [6, -10, 0]), (5.0, [6, -10, 0]), (6.0, [6, 0, 0]), (length, [6, 0, 0])])},
        'ear_right': {'rotation': keys([(0, [0, 0, 0]), (6.5, [0, 0, 0]), (6.6, [-12, 0, -16]), (6.75, [0, 0, 0]), (length, [0, 0, 0])])},
    }
    return {'loop': True, 'animation_length': length, 'bones': bones}


def grazing_neck_angle(model):
    """Neck rotation (added to the rest pose) of the grazing pose: the smallest angle that brings the nose locator (top
    front of the muzzle) to EAT_NOSE pixels above the ground, so the lower edge of the muzzle is in the grass; if the
    neck cannot reach that low, the angle of the lowest nose. Searched on the kinematics of model.py."""
    from model import pivots_and_locators
    best = None
    for tenth in range(0, 1501):
        a = tenth / 10.0
        y = pivots_and_locators(model, eat_pose(a))['locator:nose'][1]
        if y <= EAT_NOSE:
            return a
        if best is None or y < best[1]:
            best = (a, y)
    return best[0]


def quadruped_animations(model):
    """All animations of a quadruped model; the grazing and lying poses come from its measures."""
    legs_front = model.bone('leg_front_left').cubes[0]
    eat_neck = grazing_neck_angle(model)
    # lying: the trunk comes down by the length of the fore leg minus 2 px (the folded legs under it)
    drop = legs_front.size[1] - 2.0
    return {
        'format_version': '1.8.0',
        'animations': {
            'misc.idle': idle(),
            'move.walk': walk(),
            'move.run': run(),
            'misc.eat': eat(eat_neck),
            'misc.alert': alert(),
            'misc.rest': rest(drop),
        },
    }


def calibration_animations(pose):
    """The axis gauge: one animation 'calib.pose' that holds the given rotation and position of each bone."""
    bones = {}
    for name, p in pose.items():
        b = {}
        if 'rotation' in p:
            b['rotation'] = keys([(0, p['rotation']), (1.0, p['rotation'])])
        if 'position' in p:
            b['position'] = keys([(0, p['position']), (1.0, p['position'])])
        bones[name] = b
    return {'format_version': '1.8.0', 'animations': {'calib.pose': {'loop': True, 'animation_length': 1.0, 'bones': bones}}}


# ------------------------------------------------------------------ sampling (preview)


def _interp(track, t):
    times = sorted(track, key=float)
    ts = [float(x) for x in times]
    if t <= ts[0]:
        return track[times[0]]
    if t >= ts[-1]:
        return track[times[-1]]
    for k in range(len(ts) - 1):
        if ts[k] <= t <= ts[k + 1]:
            a = (t - ts[k]) / (ts[k + 1] - ts[k]) if ts[k + 1] > ts[k] else 0.0
            v0, v1 = track[times[k]], track[times[k + 1]]
            return [v0[i] * (1 - a) + v1[i] * a for i in range(3)]
    return track[times[-1]]


def pose_at(anim, t):
    """Bone -> dict(rotation, position) of an animation at time t (loops wrap), linear keyframes as GeckoLib's default."""
    length = anim['animation_length']
    if anim.get('loop') is True and length > 0:
        t = t % length
    pose = {}
    for bone, tracks in anim['bones'].items():
        pose[bone] = {k: _interp(tracks[k], t) for k in ('rotation', 'position') if k in tracks}
    return pose
