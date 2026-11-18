"""Animations of tools/fauna: gaits and poses of the quadruped archetype, written as Bedrock .animation.json (1.8.0).

Keyframe values are offsets from the rest pose (rotation in degrees with the axis signs of model.py: +X turns the front
of a bone down; position in pixels). Loops are sampled densely (every 1/16 or 1/32 of the cycle) and interpolated
linearly by GeckoLib, so the curves stay smooth without easing functions. Names follow GeckoLib's DefaultAnimations where
one exists (misc.idle, move.walk, move.run, misc.rest); misc.eat and misc.alert are own names of the slice.

Gaits (roe deer; leg phases as fractions of the cycle):
- walk: four-beat lateral sequence (hind left 0, fore left 0.25, hind right 0.5, fore right 0.75), 1.1 s;
- run: the bounding gallop of a fleeing roe deer, fore legs and hind legs nearly in pairs (the hind pair after the fore
  pair), a long reach forward and a short tuck back, the body pitching, 0.55 s.
The body height of both gaits is computed from the kinematics in every keyframe: a hoof touches the ground whenever the
deer is not in the flight of the gallop, and no hoof sinks into it. The grazing and lying poses are solved the same way
(validate.py checks every animation: no vertex under the ground, hooves on the ground, no hoof through another leg).
"""
import math

import numpy as np

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


# ------------------------------------------------------------------ posed vertices (ground contact)


def posed_vertices(model, pose, bones=None):
    """Bone name -> (n x 3) posed model-space corners of its cubes (all bones with cubes, or the given ones)."""
    from model import bone_matrices, face_corners
    ms = bone_matrices(model, pose)
    out = {}
    for b in model.bones:
        if (bones is None or b.name in bones) and b.cubes:
            pts = [p for c in b.cubes for cs in face_corners(c).values() for p, _ in cs]
            out[b.name] = np.array([(ms[b.name] @ np.array([*p, 1.0]))[:3] for p in pts])
    return out


def lowest(model, pose, bones=None):
    """The lowest posed vertex height (pixels) of the cubes of the given bones (all bones by default)."""
    return min(float(v[:, 1].min()) for v in posed_vertices(model, pose, bones).values())


def grounded(model, pose, lift=0.0):
    """The pose with the body moved up or down so that the lowest hoof is lift pixels above the ground."""
    pose = {k: dict(v) for k, v in pose.items()}
    pose.setdefault('body', {}).pop('position', None)
    y = lift - lowest(model, pose, LEGS)
    pose['body']['position'] = (0.0, y, 0.0)
    return pose


def gait(model, length, legs, body_rotation, lift, steps=16):
    """Leg and body tracks of a gait: legs(t) and body_rotation(t) give the angles at the phase t (0..1); the body height
    in every keyframe is computed from the kinematics so that the lowest hoof is lift(t) pixels above the ground (0 when
    a hoof carries the animal, more in the flight of a gallop): the hooves neither sink into the ground nor hang above
    it while they carry the deer."""
    tracks = {b: {} for b in LEGS}
    body_rot, body_pos = {}, {}
    for k in range(steps + 1):
        t = k / steps
        key = f'{length * t:.4f}'
        pose = {b: {'rotation': tuple(r)} for b, r in legs(t).items()}
        pose['body'] = {'rotation': tuple(body_rotation(t))}
        y = lift(t) - lowest(model, pose, LEGS)
        for b in LEGS:
            tracks[b][key] = _r(pose[b]['rotation'])
        body_rot[key] = _r(pose['body']['rotation'])
        body_pos[key] = _r([0, y, 0])
    bones = {b: {'rotation': tr} for b, tr in tracks.items()}
    bones['body'] = {'rotation': body_rot, 'position': body_pos}
    return bones


def walk(model, length=1.1, amp=20.0):
    phases = {'leg_back_left': 0.0, 'leg_front_left': 0.25, 'leg_back_right': 0.5, 'leg_front_right': 0.75}
    bones = gait(model, length, lambda t: {b: leg_swing(t + ph, amp) for b, ph in phases.items()},
                 lambda t: [0, 0, 1.2 * math.sin(2 * math.pi * t)], lambda t: 0.0, steps=32)
    bones['neck'] = {'rotation': sample(length, lambda t: [3.0 * math.sin(4 * math.pi * t + 0.6), 0, 0])}
    bones['head'] = {'rotation': sample(length, lambda t: [-2.0 * math.sin(4 * math.pi * t + 0.6), 0, 0])}
    bones['tail'] = {'rotation': sample(length, lambda t: [0, 0, 4.0 * math.sin(2 * math.pi * t)])}
    return {'loop': True, 'animation_length': length, 'bones': bones}


# The bounding gallop: a leg reaches far forward (RUN_REACH degrees) but folds back only a little (RUN_TUCK), so in the
# gathered phase the fore and hind hooves stay apart under the belly; the hind pair runs RUN_HIND_LAG of the cycle after
# the fore pair (the fore legs land, then the hind legs), each pair split by RUN_PAIR_LAG between its left and right leg.
RUN_REACH = 40.0
RUN_TUCK = 22.0
RUN_HIND_LAG = 0.15
RUN_PAIR_LAG = 0.06


def run_legs(t):
    def fore(ph):
        s = math.sin(2 * math.pi * ph)
        return [-(RUN_REACH if s > 0 else RUN_TUCK) * s, 0, 0]

    def hind(ph):
        s = math.sin(2 * math.pi * ph + math.pi)       # forward (s > 0) is the gathering swing, back is the push
        return [-(RUN_TUCK if s > 0 else RUN_REACH) * s, 0, 0]

    return {'leg_front_left': fore(t), 'leg_front_right': fore(t + RUN_PAIR_LAG),
            'leg_back_left': hind(t + RUN_HIND_LAG), 'leg_back_right': hind(t + RUN_HIND_LAG + RUN_PAIR_LAG)}


def run_flight(t):
    """Hoof clearance (pixels) of the run: the stretched flight after the push of the hind legs (around t = 0.3) and a
    short gathered one (around t = 0.8); a hoof carries the deer in between."""
    s = math.sin(2 * math.pi * (t - 0.05))
    return 1.4 * s * s if s > 0.3 else (0.5 * s ** 6 if s < -0.6 else 0.0)


def run(model, length=0.55):
    bones = gait(model, length, run_legs, lambda t: [-7.0 * math.cos(2 * math.pi * t), 0, 0], run_flight)
    # neck stretched forward and the head raised against it; ears laid back, the tail (rump hair) up
    bones['neck'] = {'rotation': sample(length, lambda t: [6.0 * math.cos(2 * math.pi * t) + 10.0, 0, 0])}
    bones['head'] = {'rotation': sample(length, lambda t: [-4.0 * math.cos(2 * math.pi * t) - 6.0, 0, 0])}
    bones['ear_left'] = {'rotation': keys([(0, [-20, 0, -6]), (length, [-20, 0, -6])])}
    bones['ear_right'] = {'rotation': keys([(0, [-20, 0, 6]), (length, [-20, 0, 6])])}
    bones['tail'] = {'rotation': keys([(0, [35, 0, 0]), (length, [35, 0, 0])])}
    return {'loop': True, 'animation_length': length, 'bones': bones}


def idle(length=6.0):
    """Standing: slow breathing of the body (up from the rest pose only, so the hooves stay on the ground), a look to the
    side, ear flicks and a tail flick."""
    bones = {
        'body': {'position': sample(length, lambda t: [0, 0.03 + 0.03 * math.sin(2 * math.pi * 3 * t), 0], steps=24)},
        'neck': {'rotation': keys([(0, [0, 0, 0]), (1.6, [-3, 0, 0]), (2.4, [-3, 0, 0]), (3.4, [0, 0, 0]), (length, [0, 0, 0])])},
        'head': {'rotation': keys([(0, [0, 0, 0]), (1.6, [0, 14, 0]), (2.6, [2, 14, 0]), (3.4, [0, 0, 0]), (length, [0, 0, 0])])},
        'ear_left': {'rotation': keys([(0, [0, 0, 0]), (4.3, [0, 0, 0]), (4.4, [-12, 0, 18]), (4.55, [0, 0, 0]), (length, [0, 0, 0])])},
        'ear_right': {'rotation': keys([(0, [0, 0, 0]), (1.0, [0, 0, 0]), (1.1, [-12, 0, -18]), (1.25, [0, 0, 0]),
                                        (5.0, [0, 0, 0]), (5.1, [-8, 0, -12]), (5.25, [0, 0, 0]), (length, [0, 0, 0])])},
        'tail': {'rotation': keys([(0, [0, 0, 0]), (3.0, [0, 0, 0]), (3.1, [0, 0, 18]), (3.2, [0, 0, -18]), (3.3, [0, 0, 0]),
                                   (length, [0, 0, 0])])},
    }
    return {'loop': True, 'animation_length': length, 'bones': bones}


# Grazing: the trunk stays level (EAT_BODY; a trunk tipped forward sinks the fore hooves, and fore legs turned forward far
# enough to lift them again stand like a sawhorse), the fore legs reach EAT_FORE degrees forward and a little apart, the
# skull hangs EAT_SKULL degrees below the horizontal in the world (the muzzle points down and a little forward, so the
# nose is in front of the eyes), the ears turn back and up, and the neck goes down until the nose locator (top front of
# the muzzle) is EAT_NOSE pixels above the ground. The body height keeps the lowest hoof on the ground in every keyframe.
EAT_BODY = 0.0
EAT_FORE = 4.0
EAT_SKULL = 80.0
EAT_NOSE = 2.0
EAT_EARS = (-30.0, 0.0, 6.0)
EAT_DOWN = 0.5          # seconds from the rest pose to the grazing pose
EAT_DOWN_STEPS = 8      # keyframes of the body height while the legs move


def rest_skull_pitch(model):
    """Skull pitch below the horizontal in the rest pose (degrees): the X angles of the neck and the head."""
    return model.bone('neck').rotation[0] + model.bone('head').rotation[0]


def eat_pose(model, neck_down, skull=EAT_SKULL):
    head = skull - EAT_BODY - neck_down - rest_skull_pitch(model)
    sx = EAT_EARS[2]
    pose = {'body': {'rotation': (EAT_BODY, 0, 0)}, 'neck': {'rotation': (neck_down, 0, 0)},
            'head': {'rotation': (head, 0, 0)},
            'ear_left': {'rotation': (EAT_EARS[0], 0, sx)}, 'ear_right': {'rotation': (EAT_EARS[0], 0, -sx)},
            'leg_front_left': {'rotation': (-EAT_BODY - EAT_FORE, 0, -3)},
            'leg_front_right': {'rotation': (-EAT_BODY - EAT_FORE, 0, 3)},
            'leg_back_left': {'rotation': (-EAT_BODY, 0, 0)}, 'leg_back_right': {'rotation': (-EAT_BODY, 0, 0)}}
    return grounded(model, pose)


def eat(model, length=3.2, skull=EAT_SKULL):
    """Grazing: the neck goes down to the grass, the head nips and chews, then the cycle repeats from the low pose (skull:
    its pitch below the horizontal in the world; step FA3: the boar's deep skull hangs less, BOAR_SKULL)."""
    down = EAT_DOWN
    low = eat_pose(model, grazing_neck_angle(model, skull=skull), skull)
    bones = {}
    for b, p in low.items():
        r = list(p['rotation'])
        if b == 'head':
            track = {'rotation': keys([(0, [0, 0, 0]), (down, r)]
                                      + [(down + 0.35 * k, [r[0] + (5 if k % 2 else 0), 0, 0]) for k in range(1, 7)]
                                      + [(length, r)])}
        elif b == 'ear_left':
            flick = [r[0] - 10, 0, r[2] + 14]
            track = {'rotation': keys([(0, [0, 0, 0]), (down, r), (2.2, r), (2.3, flick), (2.45, r), (length, r)])}
        else:
            track = {'rotation': keys([(0, [0, 0, 0]), (down, r), (length, r)])}
        bones[b] = track
    # the body height while the legs and the trunk turn: the lowest hoof stays on the ground
    heights = []
    for k in range(EAT_DOWN_STEPS + 1):
        f = k / EAT_DOWN_STEPS
        pose = {b: {'rotation': tuple(f * v for v in low[b]['rotation'])} for b in LEGS + ('body',)}
        heights.append((down * f, [0, -lowest(model, pose, LEGS), 0]))
    bones['body']['position'] = keys(heights + [(length, heights[-1][1])])
    bones['tail'] = {'rotation': keys([(0, [0, 0, 0]), (1.4, [0, 0, 14]), (1.5, [0, 0, -14]), (1.6, [0, 0, 0]),
                                       (length, [0, 0, 0])])}
    return {'loop': True, 'animation_length': length, 'bones': bones}


def alert(model, length=0.45):
    """Alarm: neck up, the muzzle still about 20 degrees below the horizontal and turned a little, ears forward and up,
    the rump hair raised (tail up), one fore leg lifted, the trunk raised in front (and lifted so the hind hooves stay on
    the ground); held until the controller changes the animation."""
    held = {
        'neck': (-16, 0, 0), 'head': (0, 6, 0), 'ear_left': (10, 0, -12), 'ear_right': (10, 0, 12), 'tail': (60, 0, 0),
        'leg_front_left': (-38, 0, 0), 'body': (-2, 0, 0),
    }
    pose = grounded(model, {b: {'rotation': r} for b, r in held.items()})
    bones = {b: {'rotation': keys([(0, [0, 0, 0]), (length, list(r))])} for b, r in held.items()}
    bones['body']['position'] = keys([(0, [0, 0, 0]), (length, list(pose['body']['position']))])
    return {'loop': 'hold_on_last_frame', 'animation_length': length, 'bones': bones}


REST_LEGS = {'leg_front_left': (88, 0, -6), 'leg_front_right': (84, 0, 8), 'leg_back_left': (-84, 0, -10),
             'leg_back_right': (-80, 0, 12)}
REST_BREATH = 0.04


def rest_drop(model):
    """How far the body comes down when the deer lies: the lowest vertex of the folded legs and the trunk touches the
    ground at the bottom of the breathing."""
    pose = {b: {'rotation': r} for b, r in REST_LEGS.items()}
    return lowest(model, pose) - REST_BREATH


def rest(model, length=8.0):
    """Lying: the body lowered to the ground with the legs folded under it, neck upright, slow breathing and an ear
    flick; loops in place."""
    drop = rest_drop(model)
    bones = {
        'body': {'position': sample(length, lambda t: [0, -drop + REST_BREATH * math.sin(2 * math.pi * 4 * t), 0], steps=32)},
        'neck': {'rotation': keys([(0, [-10, 0, 0]), (length, [-10, 0, 0])])},
        'head': {'rotation': keys([(0, [6, 0, 0]), (3.0, [6, -10, 0]), (5.0, [6, -10, 0]), (6.0, [6, 0, 0]), (length, [6, 0, 0])])},
        'ear_right': {'rotation': keys([(0, [0, 0, 0]), (6.5, [0, 0, 0]), (6.6, [-12, 0, -16]), (6.75, [0, 0, 0]), (length, [0, 0, 0])])},
    }
    for b, r in REST_LEGS.items():
        bones[b] = {'rotation': keys([(0, list(r)), (length, list(r))])}
    return {'loop': True, 'animation_length': length, 'bones': bones}


# Rooting (step FA3, buchtowanie of the wild boar): the grazing pose with the snout in the litter (ROOT_NOSE px above the
# ground), the head shoving forward and up and the neck nodding in short strokes, the body swaying a little.
ROOT_NOSE = 2.5
ROOT_STROKE = 0.4       # seconds of one stroke of the snout
# The pitch of the boar's skull in the world while it grazes or roots: its skull is deep (the eyes high on it), so at the
# deer's 80 degrees the eyes came in front of the snout (the head tucked under).
BOAR_SKULL = 62.0


def root(model, length=3.2, skull=BOAR_SKULL):
    """Rooting: down to the grazing pose with the nose ROOT_NOSE px above the ground, then strokes of the snout (the head
    turns up and back down, the neck nods), loops from the low pose."""
    down = EAT_DOWN
    low = eat_pose(model, grazing_neck_angle(model, ROOT_NOSE, skull), skull)
    strokes = int((length - down) // ROOT_STROKE)
    bones = {}
    for b, p in low.items():
        r = list(p['rotation'])
        if b == 'head':
            pts = [(0, [0, 0, 0]), (down, r)]
            for k in range(strokes):
                t0 = down + k * ROOT_STROKE
                pts += [(t0 + 0.45 * ROOT_STROKE, [r[0] - 9, 0, (4 if k % 2 else -4)]), (t0 + ROOT_STROKE, r)]
            track = {'rotation': keys(pts + [(length, r)])}
        elif b == 'neck':
            pts = [(0, [0, 0, 0]), (down, r)]
            for k in range(strokes):
                t0 = down + k * ROOT_STROKE
                pts += [(t0 + 0.45 * ROOT_STROKE, [r[0] - 3, 0, 0]), (t0 + ROOT_STROKE, r)]
            track = {'rotation': keys(pts + [(length, r)])}
        else:
            track = {'rotation': keys([(0, [0, 0, 0]), (down, r), (length, r)])}
        bones[b] = track
    heights = []
    for k in range(EAT_DOWN_STEPS + 1):
        f = k / EAT_DOWN_STEPS
        pose = {b: {'rotation': tuple(f * v for v in low[b]['rotation'])} for b in LEGS + ('body',)}
        heights.append((down * f, [0, -lowest(model, pose, LEGS), 0]))
    bones['body']['position'] = keys(heights + [(length, heights[-1][1])])
    bones['tail'] = {'rotation': sample(length, lambda t: [0, 0, 10.0 * math.sin(2 * math.pi * 4 * t)], steps=32)}
    return {'loop': True, 'animation_length': length, 'bones': bones}


# Wallowing (step FA3, babranie of the wild boar): lying with the legs folded, the trunk rolling from side to side by
# WALLOW_ROLL degrees, the body height computed in every keyframe so that the lowest vertex touches the ground.
WALLOW_ROLL = 24.0


def wallow(model, length=4.0, steps=32):
    bones = {b: {'rotation': {}} for b in REST_LEGS}
    body_rot, body_pos = {}, {}
    for k in range(steps + 1):
        t = k / steps
        key = f'{length * t:.4f}'
        roll = WALLOW_ROLL * math.sin(2 * math.pi * t)
        pose = {b: {'rotation': r} for b, r in REST_LEGS.items()}
        pose['body'] = {'rotation': (0, 0, roll)}
        y = -lowest(model, pose)
        for b, r in REST_LEGS.items():
            bones[b]['rotation'][key] = _r(r)
        body_rot[key] = _r([0, 0, roll])
        body_pos[key] = _r([0, y, 0])
    bones['body'] = {'rotation': body_rot, 'position': body_pos}
    bones['neck'] = {'rotation': keys([(0, [-6, 0, 0]), (length, [-6, 0, 0])])}
    bones['head'] = {'rotation': sample(length, lambda t: [4, 0, -0.5 * WALLOW_ROLL * math.sin(2 * math.pi * t)])}
    return {'loop': True, 'animation_length': length, 'bones': bones}


def grazing_neck_angle(model, nose_target=None, skull=EAT_SKULL):
    """Neck rotation (added to the rest pose) of the grazing pose: the smallest angle that brings the nose locator (top
    front of the muzzle) to EAT_NOSE pixels above the ground, so the muzzle is in the grass; if the neck cannot reach
    that low, the angle of the lowest nose. Searched on the kinematics of model.py. nose_target: another height of the
    nose (step FA3: the rooting snout)."""
    from model import pivots_and_locators
    target = EAT_NOSE if nose_target is None else nose_target
    best = None
    for tenth in range(0, 1501):
        a = tenth / 10.0
        y = pivots_and_locators(model, eat_pose(model, a, skull))['locator:nose'][1]
        if y <= target:
            return a
        if best is None or y < best[1]:
            best = (a, y)
    return best[0]


def quadruped_animations(model):
    """All animations of a quadruped model; the gaits, the grazing and the lying poses come from its measures."""
    return {
        'format_version': '1.8.0',
        'animations': {
            'misc.idle': idle(),
            'move.walk': walk(model),
            'move.run': run(model),
            'misc.eat': eat(model),
            'misc.alert': alert(model),
            'misc.rest': rest(model),
        },
    }


def boar_animations(model):
    """The wild boar (step FA3): the quadruped animations, with misc.alert as the snort (head up, a fore hoof stamped,
    the tail up), plus misc.root (rooting) and misc.wallow (wallowing)."""
    anims = quadruped_animations(model)
    anims['animations']['misc.eat'] = eat(model, skull=BOAR_SKULL)
    anims['animations']['misc.root'] = root(model)
    anims['animations']['misc.wallow'] = wallow(model)
    return anims


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


# ------------------------------------------------------------------ small mammal (step FA5: the red squirrel)

# The climbing poses turn the whole model (the root) by 90 degrees about X: up the trunk the front points up and the belly
# faces the entity's front (the entity faces the trunk), down the trunk the head points down and the belly faces the
# entity's back (the entity faces away from it). The paws then touch a vertical plane CLIMB_PLANE px in front of (behind)
# the feet of the entity, where the entity code puts the bark: half the hitbox width (0.2 blocks) at the render scale 0.5
# is 6.4 px. validate.check_poses checks the plane instead of the ground for these animations.
CLIMB_PLANE = {'red_squirrel': 0.2 * 16 / 0.5}
CLIMBING = {'move.climb': -1, 'move.descend': 1}
SQ_TAIL = ('tail_base', 'tail_mid', 'tail_tip')
SQ_FOOD = ('food_cone', 'food_nut')


def _all_vertices(model, pose, skip=SQ_FOOD):
    v = posed_vertices(model, pose)
    return np.vstack([a for b, a in v.items() if b not in skip])


def sq_walk(model, length=1.0, amp=22.0):
    phases = {'leg_back_left': 0.0, 'leg_front_left': 0.25, 'leg_back_right': 0.5, 'leg_front_right': 0.75}
    bones = gait(model, length, lambda t: {b: leg_swing(t + ph, amp) for b, ph in phases.items()},
                 lambda t: [0, 0, 1.5 * math.sin(2 * math.pi * t)], lambda t: 0.0, steps=32)
    bones['head'] = {'rotation': sample(length, lambda t: [2.0 * math.sin(4 * math.pi * t + 0.6), 0, 0])}
    bones['tail_base'] = {'rotation': sample(length, lambda t: [3.0 * math.sin(2 * math.pi * t), 0, 3.0 * math.sin(2 * math.pi * t)])}
    bones['tail_tip'] = {'rotation': sample(length, lambda t: [4.0 * math.sin(2 * math.pi * t - 1.0), 0, 0])}
    return {'loop': True, 'animation_length': length, 'bones': bones}


def sq_hop_legs(t):
    """The bound of a squirrel: the front legs land together, then the long hind feet come down in front of where the
    front paws were and push off."""
    def fore(ph):
        return [-34.0 * math.sin(2 * math.pi * ph), 0, 0]

    def hind(ph):
        return [-30.0 * math.sin(2 * math.pi * ph + math.pi), 0, 0]

    return {'leg_front_left': fore(t), 'leg_front_right': fore(t + 0.04),
            'leg_back_left': hind(t + 0.12), 'leg_back_right': hind(t + 0.16)}


def sq_hop_flight(t):
    s = math.sin(2 * math.pi * (t - 0.05))
    return 2.0 * s * s if s > 0.35 else 0.0


def sq_hop(model, length=0.5):
    bones = gait(model, length, sq_hop_legs, lambda t: [-9.0 * math.cos(2 * math.pi * t), 0, 0], sq_hop_flight)
    bones['head'] = {'rotation': sample(length, lambda t: [5.0 * math.cos(2 * math.pi * t), 0, 0])}
    # the tail streams back and waves with the bounds
    bones['tail_base'] = {'rotation': sample(length, lambda t: [-25.0 + 6.0 * math.cos(2 * math.pi * t), 0, 0])}
    bones['tail_mid'] = {'rotation': sample(length, lambda t: [-20.0 + 8.0 * math.cos(2 * math.pi * t - 0.8), 0, 0])}
    bones['tail_tip'] = {'rotation': sample(length, lambda t: [-15.0 + 10.0 * math.cos(2 * math.pi * t - 1.6), 0, 0])}
    return {'loop': True, 'animation_length': length, 'bones': bones}


SQ_CLIMB_HEAD = {-1: (-18.0, 0, 0), 1: (-38.0, 0, 0)}
SQ_CLIMB_TAIL = ((-55.0, 0, 0), (-35.0, 0, 0), (-40.0, 0, 0))


def sq_climb_legs(t):
    return {'leg_front_left': [-32.0 * math.sin(2 * math.pi * t), 0, 0],
            'leg_front_right': [-32.0 * math.sin(2 * math.pi * t + 0.4), 0, 0],
            'leg_back_left': [-26.0 * math.sin(2 * math.pi * t + math.pi), 0, 0],
            'leg_back_right': [-26.0 * math.sin(2 * math.pi * t + math.pi + 0.4), 0, 0]}


def sq_climbing(model, sign, length=0.4, steps=16):
    """Up (sign -1) or down (sign +1) a trunk: the front pair and the hind pair grip by turns, the body moved in every
    keyframe so that the nearest paw touches the bark plane and nothing goes through it, and the lowest vertex is at the
    feet of the entity (y 0); the tail trails along the bark."""
    plane = sign * CLIMB_PLANE[model.id]
    tracks = {b: {} for b in LEGS}
    root_pos = {}
    head = SQ_CLIMB_HEAD[sign]
    for k in range(steps + 1):
        t = k / steps
        key = f'{length * t:.4f}'
        legs = sq_climb_legs(t)
        pose = {b: {'rotation': tuple(r)} for b, r in legs.items()}
        pose['root'] = {'rotation': (90.0 * sign, 0, 0)}
        pose['head'] = {'rotation': head}
        for b, r in zip(SQ_TAIL, SQ_CLIMB_TAIL):
            pose[b] = {'rotation': r}
        v = _all_vertices(model, pose)
        y = -float(v[:, 1].min())
        z = plane - (float(v[:, 2].min()) if sign < 0 else float(v[:, 2].max()))
        for b in LEGS:
            tracks[b][key] = _r(legs[b])
        root_pos[key] = _r([0, y, z])
    bones = {b: {'rotation': tr} for b, tr in tracks.items()}
    bones['root'] = {'rotation': keys([(0, [90.0 * sign, 0, 0]), (length, [90.0 * sign, 0, 0])]), 'position': root_pos}
    bones['head'] = {'rotation': keys([(0, list(head)), (length, list(head))])}
    for b, r in zip(SQ_TAIL, SQ_CLIMB_TAIL):
        bones[b] = {'rotation': keys([(0, list(r)), (length, list(r))])}
    return {'loop': True, 'animation_length': length, 'bones': bones}


SQ_JUMP_LIFT = 3.0


def sq_jump(model, length=0.5):
    """A leap from crown to crown: front legs reaching forward, hind legs stretched back, the tail streaming behind as a
    rudder; held a little above the ground (the entity is in the air while it plays)."""
    held = {'leg_front_left': (-70, 0, 0), 'leg_front_right': (-66, 0, 0), 'leg_back_left': (68, 0, 0),
            'leg_back_right': (64, 0, 0), 'head': (-10, 0, 0), 'tail_base': (-45, 0, 0), 'tail_mid': (-30, 0, 0),
            'tail_tip': (-40, 0, 0), 'body': (-6, 0, 0)}
    pose = {b: {'rotation': r} for b, r in held.items()}
    y = SQ_JUMP_LIFT - float(_all_vertices(model, pose)[:, 1].min())
    bones = {b: {'rotation': keys([(0, list(r)), (length, list(r))])} for b, r in held.items()}
    bones['body']['position'] = keys([(0, [0, y, 0]), (length, [0, y, 0])])
    return {'loop': True, 'animation_length': length, 'bones': bones}


SQ_SIT = -52.0      # the body pitched up when it sits on its haunches (eating, watching)
SQ_SIT_STEPS = 32   # keyframes of the body height while it sits up (the lowest vertex moves fast as the body turns)


def _sitting(model, body, front, head, tail, extra=None):
    """A sitting pose: the body pitched up, the haunches kept upright in the world, the given front legs, head and tail;
    the body lowered so the lowest vertex touches the ground."""
    pose = {'body': {'rotation': (body, 0, 0)},
            'leg_back_left': {'rotation': (-body, 0, -4)}, 'leg_back_right': {'rotation': (-body, 0, 4)},
            'leg_front_left': {'rotation': front}, 'leg_front_right': {'rotation': (front[0], -front[1], -front[2])},
            'head': {'rotation': head}}
    for b, r in zip(SQ_TAIL, tail):
        pose[b] = {'rotation': r}
    for b, r in (extra or {}).items():
        pose[b] = {'rotation': r}
    y = -float(_all_vertices(model, pose)[:, 1].min())
    pose['body']['position'] = (0, y, 0)
    return pose


SQ_EAT_FRONT = (-62.0, 0, -6.0)
SQ_EAT_HEAD = (58.0, 0, 0)
SQ_EAT_TAIL = ((-28.0, 0, 0), (12.0, 0, 0), (30.0, 0, 0))


def sq_eat(model, length=3.0):
    """Eating: sitting up on the haunches with the food in the front paws at the mouth, the head nibbling, the tail up
    along the back."""
    low = _sitting(model, SQ_SIT, SQ_EAT_FRONT, SQ_EAT_HEAD, SQ_EAT_TAIL)
    down = 0.35
    bones = {}
    for b, p in low.items():
        r = list(p['rotation'])
        if b == 'head':
            pts = [(0, [0, 0, 0]), (down, r)] + [(down + 0.25 * k, [r[0] + (6 if k % 2 else 0), r[1], r[2] + (3 if k % 4 == 1 else 0)])
                                                 for k in range(1, 10)] + [(length, r)]
            bones[b] = {'rotation': keys(pts)}
        else:
            bones[b] = {'rotation': keys([(0, [0, 0, 0]), (down, r), (length, r)])}
    # the body height while it sits up: the lowest vertex stays on the ground
    heights = []
    for k in range(SQ_SIT_STEPS + 1):
        f = k / SQ_SIT_STEPS
        pose = {b: {'rotation': tuple(f * v for v in low[b]['rotation'])} for b in low}
        heights.append((down * f, [0, -float(_all_vertices(model, pose)[:, 1].min()), 0]))
    bones['body']['position'] = keys(heights + [(length, heights[-1][1])])
    return {'loop': True, 'animation_length': length, 'bones': bones}


def sq_alert(model, length=0.4):
    """Watching (held): sitting bolt upright, the front paws folded to the chest, the head level, the tail up."""
    pose = _sitting(model, -66.0, (-20.0, 0, -4.0), (70.0, 0, 0), ((-22.0, 0, 0), (10.0, 0, 0), (25.0, 0, 0)),
                    {'ear_left': (8, 0, -4), 'ear_right': (8, 0, 4)})
    bones = {b: {'rotation': keys([(0, [0, 0, 0]), (length, list(p['rotation']))])} for b, p in pose.items()}
    heights = []
    for k in range(SQ_SIT_STEPS + 1):
        f = k / SQ_SIT_STEPS
        fp = {b: {'rotation': tuple(f * v for v in pose[b]['rotation'])} for b in pose}
        heights.append((length * f, [0, -float(_all_vertices(model, fp)[:, 1].min()), 0]))
    bones['body']['position'] = keys(heights)
    return {'loop': 'hold_on_last_frame', 'animation_length': length, 'bones': bones}


SQ_REST_LEGS = {'leg_front_left': (80, 0, -8), 'leg_front_right': (76, 0, 8), 'leg_back_left': (-70, 0, -6),
                'leg_back_right': (-66, 0, 6)}
SQ_REST_TAIL = ((28.0, 0, 0), (34.0, 0, 0), (40.0, 0, 0))


def sq_rest(model, length=6.0):
    """Resting (in the drey or on a branch): lying with the legs folded under it, the head down on the paws and the
    bushy tail curled forward over the back; slow breathing."""
    pose = {b: {'rotation': r} for b, r in SQ_REST_LEGS.items()}
    pose['head'] = {'rotation': (14, 0, 0)}
    for b, r in zip(SQ_TAIL, SQ_REST_TAIL):
        pose[b] = {'rotation': r}
    drop = float(_all_vertices(model, pose)[:, 1].min()) - REST_BREATH
    bones = {'body': {'position': sample(length, lambda t: [0, -drop + REST_BREATH * math.sin(2 * math.pi * 3 * t), 0], steps=24)},
             'head': {'rotation': keys([(0, [14, 0, 0]), (length, [14, 0, 0])])}}
    for b, r in SQ_REST_LEGS.items():
        bones[b] = {'rotation': keys([(0, list(r)), (length, list(r))])}
    for b, r in zip(SQ_TAIL, SQ_REST_TAIL):
        bones[b] = {'rotation': keys([(0, list(r)), (length, list(r))])}
    bones['ear_left'] = {'rotation': keys([(0, [0, 0, 0]), (4.0, [0, 0, 0]), (4.1, [-10, 0, 12]), (4.25, [0, 0, 0]), (length, [0, 0, 0])])}
    return {'loop': True, 'animation_length': length, 'bones': bones}


def sq_idle(length=5.0):
    """Standing on all fours: breathing, quick looks to the sides, ear twitches and the flick of the tail."""
    bones = {
        'body': {'position': sample(length, lambda t: [0, 0.03 + 0.03 * math.sin(2 * math.pi * 4 * t), 0], steps=24)},
        'head': {'rotation': keys([(0, [0, 0, 0]), (0.8, [0, 0, 0]), (0.9, [-6, 24, 0]), (1.9, [-6, 24, 0]), (2.0, [0, 0, 0]),
                                   (3.2, [0, 0, 0]), (3.3, [-4, -20, 0]), (4.2, [-4, -20, 0]), (4.3, [0, 0, 0]), (length, [0, 0, 0])])},
        'ear_left': {'rotation': keys([(0, [0, 0, 0]), (2.4, [0, 0, 0]), (2.5, [-12, 0, 14]), (2.65, [0, 0, 0]), (length, [0, 0, 0])])},
        'tail_mid': {'rotation': keys([(0, [0, 0, 0]), (1.2, [0, 0, 0]), (1.3, [-14, 0, 0]), (1.4, [8, 0, 0]), (1.5, [-10, 0, 0]),
                                       (1.6, [0, 0, 0]), (length, [0, 0, 0])])},
        'tail_tip': {'rotation': keys([(0, [0, 0, 0]), (1.25, [0, 0, 0]), (1.35, [-18, 0, 0]), (1.45, [12, 0, 0]), (1.55, [-12, 0, 0]),
                                       (1.65, [0, 0, 0]), (length, [0, 0, 0])])},
    }
    return {'loop': True, 'animation_length': length, 'bones': bones}


def squirrel_animations(model):
    """All animations of the red squirrel (step FA5)."""
    return {
        'format_version': '1.8.0',
        'animations': {
            'misc.idle': sq_idle(),
            'move.walk': sq_walk(model),
            'move.hop': sq_hop(model),
            'move.climb': sq_climbing(model, -1),
            'move.descend': sq_climbing(model, 1),
            'move.jump': sq_jump(model),
            'misc.eat': sq_eat(model),
            'misc.alert': sq_alert(model),
            'misc.rest': sq_rest(model),
        },
    }
