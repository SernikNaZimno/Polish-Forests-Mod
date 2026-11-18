"""The small mammal archetype of tools/fauna (step FA5): a small animal with a bushy tail, built from a species description
in centimetres, at twice the resolution of the quadruped (1 model pixel = 3.125 cm) and drawn by the renderer at half
the size (Model.scale 0.5, GeckoLib's withScale), so its small parts (legs, ears, the ear tufts) keep whole pixels.

Bones (names are stable: animations, the renderer and the entity refer to them):
    root                    at the ground under the middle of the trunk; parent of everything (the climbing poses turn it)
      body                  pivot in the middle of the trunk; chest and hindquarters cubes
        head                pivot at the top front of the chest (no neck); skull and muzzle; locators eye_left/right, nose
          ear_left/right    pivot at the base of the ear (thin plates on top of the skull)
            tuft_left/right the winter ear tufts (the renderer hides them outside the winter coat)
          food_cone         a pine cone held in front of the mouth (shown only while it eats in a conifer forest)
          food_nut          a hazelnut (shown only while it eats elsewhere); turned 45 degrees about Y
        leg_front_left/right   thin front legs
        leg_back_left/right    the hindquarters' large haunches with the long hind feet
        tail_base           pivot at the top back of the rump; the bushy tail in three segments that curl up over the back
          tail_mid
            tail_tip
Every bone has a locator at its far end ('<bone>_tip'), the head also 'nose', 'eye_left' and 'eye_right', the body
'body_front' and 'body_back'. The leg names are those of the quadruped, so the gait solver of animations.py works on them.
No two faces of different cubes lie in one plane with a shared area (decision V8-c): the widths differ by parity or the
parts are turned.
"""
from dataclasses import dataclass

from model import Bone, Cube, Model

PX_CM = 3.125
# The squirrel is modelled at twice the resolution of the other animals and drawn at half the size.
MODEL_SCALE = 0.5


def px(cm, up=2.0, minimum=1):
    """Whole model pixels of a measure in centimetres, enlarged by up (F3: animals under 30 cm × 2)."""
    return max(minimum, int(round(cm * up / PX_CM)))


@dataclass(frozen=True)
class SmallMammal:
    """A species in centimetres (adults, real size; up enlarges it), plus a few shape choices in degrees."""
    id: str
    up: float                 # enlargement (F3, G.3: × 2 under 30 cm)
    trunk_length: float       # chest front to the back of the rump
    chest_depth: float
    hind_depth: float
    chest_width: float
    hind_width: float
    front_height: float       # ground to the bottom of the chest
    head_length: float        # skull without the muzzle
    head_height: float
    head_width: float
    muzzle_length: float
    muzzle_height: float
    muzzle_width: float
    head_angle: float         # skull pitched below the horizontal at rest, degrees
    ear_height: float
    ear_spread: float         # ears turned outwards, degrees
    tuft_height: float        # the winter ear tufts
    tail_segments: tuple      # (length, width) of the base, middle and tip, centimetres
    tail_angles: tuple        # rest pitch of each tail segment, degrees (positive raises a segment that points back)
    hitbox: tuple = (0.4, 0.45)


def build(q, texture_size=(64, 64)):
    """The bones and cubes of a small mammal (cubes without UV; uvpack.py places them)."""
    up = q.up
    L = px(q.trunk_length, up)
    Lc = L // 2
    cd, hd = px(q.chest_depth, up), px(q.hind_depth, up)
    cw, hw = px(q.chest_width, up), px(q.hind_width, up)
    if cw % 2 == hw % 2:
        hw += 1               # different parity: the sides of chest and hindquarters never share a plane
    fh = px(q.front_height, up)
    top = fh + cd             # the back is level: chest top = rump top
    z_front, z_back = -Lc, L - Lc
    bones = [Bone('root', (0, 0, 0))]
    chest = Cube((-cw / 2.0, fh, z_front), (cw, cd, Lc), 'chest')
    hind = Cube((-hw / 2.0, top - hd, 0), (hw, hd, L - Lc), 'hind')
    bones.append(Bone('body', (0, top - cd / 2.0, 0), 'root', cubes=[chest, hind],
                      locators={'body_front': (0, fh + cd / 2.0, z_front), 'body_back': (0, top - hd / 2.0, z_back)}))

    # Head on the front top of the chest, the skull sunk 0.5 px into it; muzzle at the bottom front of the skull.
    sl, sh, sw = px(q.head_length, up), px(q.head_height, up), px(q.head_width, up)
    ml, mh, mw = px(q.muzzle_length, up), px(q.muzzle_height, up), px(q.muzzle_width, up)
    if sw % 2 == cw % 2:
        sw += 1
    if mw % 2 == sw % 2:
        mw = max(1, mw - 1) if mw > 1 else mw + 1
    hp = (0, top - 0.5, z_front + 0.5)
    sy0 = top - sh + 1.0
    skull = Cube((-sw / 2.0, sy0, z_front + 0.5 - sl), (sw, sh, sl), 'skull')
    mz1 = z_front + 0.5 - sl
    muzzle = Cube((-mw / 2.0, sy0, mz1 - ml), (mw, mh, ml), 'muzzle')
    nose = (0, sy0 + mh - 0.5, mz1 - ml)
    eye_y = sy0 + sh - 1.0
    eye_z = mz1 + 1.0
    head_locs = {'nose': nose, 'eye_left': (sw / 2.0, eye_y, eye_z), 'eye_right': (-sw / 2.0, eye_y, eye_z),
                 'head_tip': (0, sy0 + sh, mz1)}
    bones.append(Bone('head', hp, 'body', rotation=(q.head_angle, 0, 0), cubes=[skull, muzzle], locators=head_locs))

    # Ears: 1 px wide, 1 px thick plates on the back of the skull top, 0.25 px in from its sides and 0.25 px in front
    # of its back face; the tufts stand on them (a child bone with its own small tilt, so no face shares a plane).
    eh, th_ = px(q.ear_height, up), px(q.tuft_height, up)
    skull_top = sy0 + sh
    ez0 = z_front + 0.5 - 1.25
    for side, sx in (('left', 1), ('right', -1)):
        x0 = sx * 0.25 if sx > 0 else -1.25
        pivot = (x0 + 0.5, skull_top - 0.5, ez0 + 0.5)
        ear = Cube((x0, skull_top - 0.5, ez0), (1, eh, 1), 'ear')
        tip = (x0 + 0.5, skull_top - 0.5 + eh, ez0 + 0.5)
        bones.append(Bone(f'ear_{side}', pivot, 'head', rotation=(-6, 0, sx * q.ear_spread), cubes=[ear],
                          locators={f'ear_{side}_tip': tip}))
        tuft = Cube((x0, tip[1], ez0), (1, th_, 1), 'tuft')
        bones.append(Bone(f'tuft_{side}', tip, f'ear_{side}', rotation=(-10, 0, sx * 6), cubes=[tuft],
                          locators={f'tuft_{side}_tip': (tip[0], tip[1] + th_, tip[2])}))

    # The food in front of the mouth (hidden unless it eats): a cone held upright, a nut turned 45 degrees.
    # In front of the muzzle (its back face on the muzzle's front plane, facing the other way), so in the climbing
    # poses, where the front of the head points up, it stays clear of the trunk.
    fz = mz1 - ml - 1.0
    cone = Cube((-1, sy0 - 1.0, fz - 1.0), (2, 3, 2), 'cone')
    bones.append(Bone('food_cone', (0, sy0 + 0.5, fz), 'head', cubes=[cone], locators={'food_cone_tip': (0, sy0 + 2.0, fz)}))
    nut = Cube((-1, sy0 - 0.5, fz - 1.0), (2, 2, 2), 'nut')
    bones.append(Bone('food_nut', (0, sy0 + 0.5, fz), 'head', rotation=(0, 45, 0), cubes=[nut],
                      locators={'food_nut_tip': (0, sy0 + 1.5, fz)}))

    # Legs: thin front legs 1 px square from the ground to 1 px inside the chest; the haunches 2 px wide and 3 px long
    # from the ground to 2 px inside the rump, half out of its sides (the thighs of a squirrel stand out).
    fz_c = z_front + 1.5
    for side, sx in (('left', 1), ('right', -1)):
        xc = sx * (cw / 2.0 - 0.75)
        leg = Cube((xc - 0.5, 0, fz_c - 0.5), (1, fh + 1, 1), 'leg_front')
        bones.append(Bone(f'leg_front_{side}', (xc, fh + 0.5, fz_c), 'body', cubes=[leg], locators={f'leg_front_{side}_tip': (xc, 0, fz_c)}))
    hb = top - hd
    hz_c = z_back - 2.5
    for side, sx in (('left', 1), ('right', -1)):
        xc = sx * (hw / 2.0)
        leg = Cube((xc - 1.0, 0, hz_c - 1.5), (2, hb + 2, 3), 'leg_back')
        bones.append(Bone(f'leg_back_{side}', (xc, hb + 1.0, hz_c), 'body', cubes=[leg], locators={f'leg_back_{side}_tip': (xc, 0, hz_c)}))

    # The tail: three segments straight back from the top back of the rump (model space), each sunk 0.5 px into the one
    # before; their rest angles bend the chain up over the back.
    names = ('tail_base', 'tail_mid', 'tail_tip')
    parent = 'body'
    z = z_back - 0.5
    y = top - 1.0
    prev_w = None
    for k, ((length, width), angle) in enumerate(zip(q.tail_segments, q.tail_angles)):
        tl, tw = px(length, up), px(width, up)
        if prev_w is not None and tw % 2 == prev_w % 2:
            tw += 1
        start = z if k == 0 else z - 0.5
        seg = Cube((-tw / 2.0, y - tw / 2.0, start), (tw, tw, tl), names[k])
        bones.append(Bone(names[k], (0, y, z), parent, rotation=(angle, 0, 0), cubes=[seg],
                          locators={f'{names[k]}_tip': (0, y, start + tl)}))
        parent = names[k]
        z = start + tl
        prev_w = tw
    return Model(q.id, bones, texture_size, q.hitbox, MODEL_SCALE)
