"""The quadruped archetype of tools/fauna: a skeleton of 13 bones built from a species description in centimetres.

Bones (names are stable: animations, the renderer and later hand edits in Blockbench refer to them):
    root                    at the ground under the middle of the trunk; parent of everything
      body                  pivot in the middle of the trunk; chest and hindquarters cubes
        neck                pivot at the top front of the chest; one cube pointing forward, raised by neck_angle
          head              pivot at the end of the neck; skull and muzzle cubes; locators eye_left/right, nose
            ear_left/right  pivot at the base of the ear
            antler_left/right  (species with antlers; the renderer hides them for females) beam with tines
        leg_front_left/right, leg_back_left/right   pivot at the top of the leg (shoulder, hip), one cube each
        tail                pivot at the top of the rump
Every bone has a locator at its far end ('<bone>_tip'), the head also 'nose', 'eye_left' and 'eye_right', the body
'body_front' and 'body_back'; GeckoLib looks locators up by name, so the names are unique in the model. The in-game check
of the bone positions (fauna game test) reads them.

Sizes are rounded to whole pixels (1 px = 6.25 cm), so box UV lands on whole texels; positions may be half pixels.
Legs are 2 x 2 px (anatomically 1 px, but 2 px reads at game distances; report research-fauna-grafika §2.3).
"""
from dataclasses import dataclass

from model import Bone, Cube, Model

PX_CM = 6.25


def px(cm, minimum=1):
    return max(minimum, int(round(cm / PX_CM)))


@dataclass(frozen=True)
class Quadruped:
    """A species in centimetres (field measures of adults), plus a few shape choices in degrees."""
    id: str
    withers: float            # height at the withers
    rump: float               # height at the top of the rump (roe deer: higher than the withers)
    trunk_length: float       # chest front to the back of the rump (without neck and head)
    chest_share: float        # share of the trunk length taken by the chest cube
    chest_depth: float        # top of the withers to the bottom of the chest
    flank_depth: float        # top of the rump to the bottom of the belly at the hind legs
    width: float              # trunk width
    neck_length: float
    neck_thickness: float     # height of the neck cube
    neck_width: float         # narrower than the skull, so their sides never lie in one plane
    neck_angle: float         # neck raised above the horizontal, degrees
    head_length: float        # skull (without the muzzle)
    head_height: float
    head_width: float
    muzzle_length: float
    muzzle_height: float
    muzzle_width: float
    head_angle: float         # skull pitched below the horizontal, degrees
    ear_length: float
    ear_width: float
    ear_spread: float         # ears turned outwards, degrees
    leg_width: float
    tail_length: float
    antler_length: float = 0.0   # 0 = no antlers
    hitbox: tuple = (0.7, 1.1)


def build(q, texture_size=(64, 64)):
    """The bones and cubes of a quadruped (cubes without UV; tools/fauna/uvpack.py places them)."""
    W = px(q.width)
    if W % 2 == 0:
        W += 1            # odd width: the legs (2 px) stand at the sides with a 1 px gap under the middle
    withers, rump = px(q.withers), px(q.rump)
    chest_d, flank_d = px(q.chest_depth), px(q.flank_depth)
    L = px(q.trunk_length)
    Lc = max(2, int(round(L * q.chest_share)))
    Lh = L - Lc
    z_front, z_back = -L / 2.0, L / 2.0
    z_mid = z_front + Lc
    hw = W / 2.0
    lw = px(q.leg_width, 2)

    bones = [Bone('root', (0, 0, 0))]
    chest = Cube((-hw, withers - chest_d, z_front), (W, chest_d, Lc), 'chest')
    hind = Cube((-hw, rump - flank_d, z_mid), (W, flank_d, Lh), 'hind')
    body_pivot = (0, (withers + rump) / 2.0 - chest_d / 2.0, 0)
    bones.append(Bone('body', body_pivot, 'root', cubes=[chest, hind],
                      locators={'body_front': (0, withers - chest_d / 2.0, z_front), 'body_back': (0, rump - flank_d / 2.0, z_back)}))

    # Neck: a square cube pointing forward (-Z) from the top front of the chest, raised by neck_angle.
    nt, nw = px(q.neck_thickness), px(q.neck_width)
    nl = px(q.neck_length)
    neck_pivot = (0, withers - nt / 2.0, z_front + nt / 2.0)
    neck = Cube((-nw / 2.0, neck_pivot[1] - nt / 2.0, neck_pivot[2] - nl), (nw, nt, nl), 'neck')
    neck_tip = (0, neck_pivot[1], neck_pivot[2] - nl + nt / 2.0)
    bones.append(Bone('neck', neck_pivot, 'body', rotation=(-q.neck_angle, 0, 0), cubes=[neck],
                      locators={'neck_tip': (0, neck_pivot[1], neck_pivot[2] - nl)}))

    # Head: skull and muzzle in front of the neck tip, in the unrotated neck frame; the head bone turns them so the
    # skull points head_angle below the horizontal (neck angle + head angle in the bone).
    hl, hh, hwid = px(q.head_length), px(q.head_height), px(q.head_width)
    ml, mh, mw = px(q.muzzle_length), px(q.muzzle_height), px(q.muzzle_width)
    hp = neck_tip
    skull = Cube((-hwid / 2.0, hp[1] - hh / 2.0, hp[2] - hl), (hwid, hh, hl), 'skull')
    muzzle = Cube((-mw / 2.0, hp[1] - hh / 2.0, hp[2] - hl - ml), (mw, mh, ml), 'muzzle')
    nose = (0, hp[1] - hh / 2.0 + mh - 0.5, hp[2] - hl - ml)
    eye_y = hp[1] + hh / 2.0 - 1.5
    eye_z = hp[2] - hl + 1.5
    bones.append(Bone('head', hp, 'neck', rotation=(q.neck_angle + q.head_angle, 0, 0), cubes=[skull, muzzle],
                      locators={'nose': nose, 'eye_left': (hwid / 2.0, eye_y, eye_z),
                                'eye_right': (-hwid / 2.0, eye_y, eye_z)}))

    # Ears at the back top corners of the skull (the back column, behind the eye and the antlers), thin (1 px) plates
    # turned outwards.
    el, ew = px(q.ear_length), px(q.ear_width)
    top = hp[1] + hh / 2.0
    for side, sx in (('left', 1), ('right', -1)):
        x_in = sx * (hwid / 2.0 - 0.25)
        pivot = (x_in, top, hp[2] - 0.5)
        x0 = x_in if sx > 0 else x_in - ew
        ear = Cube((x0, top, hp[2] - 1.0), (ew, el, 1), 'ear')
        bones.append(Bone(f'ear_{side}', pivot, 'head', rotation=(-10, 0, sx * q.ear_spread), cubes=[ear],
                          locators={f'ear_{side}_tip': (x_in + sx * ew / 2.0, top + el, hp[2] - 0.5)}))

    # Antlers (pedicles on the frontal bone above the eyes, just in front of the ears: the middle column of the skull).
    # A roe buck's six-pointer: the front tine about 2 px above the burr, the back tine about 3 px, the beam ending
    # above it.
    if q.antler_length > 0:
        al = px(q.antler_length)
        front_y = max(1, int(round(al * 0.4)))
        back_y = max(front_y + 1, al - 2)
        for side, sx in (('left', 1), ('right', -1)):
            x0 = 0.25 if sx > 0 else -1.25
            base = (x0 + 0.5, top, hp[2] - 1.5)
            beam = Cube((x0, top, hp[2] - 2.0), (1, al, 1), 'antler')
            front_tine = Cube((x0, top + front_y, hp[2] - 3.0), (1, 1, 1), 'antler_tine')
            back_tine = Cube((x0, top + back_y, hp[2] - 1.0), (1, 1, 1), 'antler_tine')
            bones.append(Bone(f'antler_{side}', base, 'head', rotation=(-12, 0, sx * 8),
                              cubes=[beam, front_tine, back_tine], locators={f'antler_{side}_tip': (x0 + 0.5, top + al, hp[2] - 1.5)}))

    # Legs: one cube each from the ground to 1 px inside the trunk, at the sides of the trunk (+X = left), turning about
    # a pivot 0.5 px inside the trunk: no gap opens at the shoulder or hip when a leg swings, and no face of the leg lies
    # in a plane of a trunk face (coplanar faces flicker).
    front_h = withers - chest_d
    hind_h = rump - flank_d
    zf = z_front + lw / 2.0 + 1.0
    zb = z_back - lw / 2.0 - 1.0
    for name, sx, zc, h, part in (('leg_front_left', 1, zf, front_h, 'leg_front'), ('leg_front_right', -1, zf, front_h, 'leg_front'),
                                  ('leg_back_left', 1, zb, hind_h, 'leg_back'), ('leg_back_right', -1, zb, hind_h, 'leg_back')):
        xc = sx * (hw - lw / 2.0 - 0.25)      # 0.25 px inside the trunk's side, off its plane
        leg = Cube((xc - lw / 2.0, 0, zc - lw / 2.0), (lw, h + 1, lw), part)
        bones.append(Bone(name, (xc, h + 0.5, zc), 'body', cubes=[leg], locators={f'{name}_tip': (xc, 0, zc)}))

    # Tail: 1-2 cm, practically invisible (report 07 §3.2): a 1 px cube sunk into the top of the rump patch, 0.5 px out
    # of the rump and 0.25 px under its top (off the planes of the hindquarters); turning about its top front edge lifts
    # it above the rump (misc.alert).
    tl = max(1, px(q.tail_length))
    tail = Cube((-0.5, rump - 0.25 - tl, z_back - 0.5), (1, tl, 1), 'tail')
    bones.append(Bone('tail', (0, rump - 0.25, z_back - 0.5), 'body', cubes=[tail],
                      locators={'tail_tip': (0, rump - 0.25 - tl, z_back + 0.5)}))
    return Model(q.id, bones, texture_size, q.hitbox)
