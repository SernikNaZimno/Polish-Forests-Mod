"""The bird archetype of tools/fauna (step FA6): a perching bird with a body, a head with a crest and a bill, two wings in
two segments (the arm and the hand with the primaries), a tail and two legs with feet, built from a species description
in centimetres at four times the resolution of the quadruped (1 model pixel = 1.5625 cm) and drawn by the renderer at a
quarter of the size (Model.scale 0.25, GeckoLib's withScale), so the small parts (the bill, the legs, the crest) keep
whole pixels. The bird is at real size (the F3 enlargement concerns animals under 30 cm; the jay is about 34 cm long).

Bones (names are stable: animations, the renderer and the entity refer to them):
    root                    at the ground under the middle of the body; parent of everything
      body                  pivot in the middle of the body; one cube
        head                pivot at the top front of the body (no neck); skull and bill; locators eye_left/right, nose
          crest             the crown feathers on the skull (raised in the alarm), pivot at their back
          food_acorn        an acorn held in the bill (shown only while the jay carries one to its cache)
        wing_left/right     the folded wing on the side of the body (a plate 1 px thick), pivot at the shoulder (top front);
                            the flight poses turn it by (90, 0, +-90): its length then runs outwards and its height becomes
                            the chord; the flap is the Z angle about that pose (the axis of the body)
          wing_tip_left/right   the primaries, a narrower plate behind the arm (over the tail when folded, the outer part
                            of the spread wing)
        tail                pivot at the top back of the body; a flat cube straight back
        leg_left/right      the tarsus from the belly down, with the toes (a wider flat foot) at its bottom
Every bone has a locator at its far end ('<bone>_tip'), the head also 'nose', 'eye_left' and 'eye_right', the body
'body_front' and 'body_back'. No two faces of different cubes lie in one plane with a shared area (decision V8-c): the
widths differ or the cubes are offset by a fraction of a pixel.
"""
from dataclasses import dataclass

from model import Bone, Cube, Model

PX_CM = 1.5625
# The bird is modelled at four times the resolution of the quadruped and drawn at a quarter of the size.
MODEL_SCALE = 0.25
# The spread wing: the rest rotation the flight poses add to a wing (left; the right one mirrors Y and Z).
SPREAD = (90.0, 0.0, 90.0)


def px(cm, minimum=1):
    """Whole model pixels of a measure in centimetres."""
    return max(minimum, int(round(cm / PX_CM)))


@dataclass(frozen=True)
class Bird:
    """A species in centimetres (adults, real size)."""
    id: str
    body_length: float        # the trunk from the breast to the vent, without the head and the tail
    body_depth: float
    body_width: float
    leg_height: float         # the visible tarsus, ground to the belly
    head_length: float        # the skull without the bill
    head_height: float
    head_width: float
    bill_length: float
    crest_length: float
    wing_length: float        # the folded wing from the shoulder to the tips of the tertials (the arm segment)
    wing_height: float        # the folded wing from its top edge down (the chord of the arm when spread)
    primaries_length: float   # the folded primaries behind the arm
    primaries_height: float
    tail_length: float
    tail_width: float
    hitbox: tuple = (0.4, 0.3)


def build(q, texture_size=(64, 64)):
    """The bones and cubes of a bird (cubes without UV; uvpack.py places them)."""
    L, D, W = px(q.body_length), px(q.body_depth), px(q.body_width)
    lh = px(q.leg_height)
    bottom, top = lh, lh + D
    z_front, z_back = -L / 2.0, L / 2.0
    bones = [Bone('root', (0, 0, 0))]
    body = Cube((-W / 2.0, bottom, z_front), (W, D, L), 'body')
    bones.append(Bone('body', (0, bottom + D / 2.0, 0), 'root', cubes=[body],
                      locators={'body_front': (0, bottom + D / 2.0, z_front), 'body_back': (0, bottom + D / 2.0, z_back)}))

    # Head high on the front of the body, sunk 1 px into it; the bill from the middle of its front face.
    hl, hh, hw = px(q.head_length), px(q.head_height), px(q.head_width)
    if hw % 2 == W % 2 and hw >= W:
        hw -= 1
    hz1 = z_front + 1.0                  # the back face of the skull (inside the body)
    hz0 = hz1 - hl
    hy0 = top - 1.5
    skull = Cube((-hw / 2.0, hy0, hz0), (hw, hh, hl), 'skull')
    bl = px(q.bill_length)
    by0 = hy0 + 1.0
    bill = Cube((-0.5, by0, hz0 - bl), (1, 1, bl), 'bill')
    nose = (0, by0 + 0.5, hz0 - bl)
    eye_y = hy0 + hh - 1.5
    eye_z = hz0 + 1.5
    head_locs = {'nose': nose, 'eye_left': (hw / 2.0, eye_y, eye_z), 'eye_right': (-hw / 2.0, eye_y, eye_z),
                 'head_tip': (0, hy0 + hh, hz0)}
    head_pivot = (0, top - 0.5, z_front + 0.5)
    bones.append(Bone('head', head_pivot, 'body', cubes=[skull, bill], locators=head_locs))

    # The crest: the crown feathers, 2 px wide (the skull is odd), half a pixel sunk into the skull top.
    cl = px(q.crest_length)
    sk_top = hy0 + hh
    crest = Cube((-1.0, sk_top - 0.5, hz0 + 0.5), (2, 1, cl), 'crest')
    bones.append(Bone('crest', (0, sk_top, hz0 + 0.5 + cl), 'head', cubes=[crest],
                      locators={'crest_tip': (0, sk_top + 0.5, hz0 + 0.5)}))

    # The acorn in the bill (hidden unless the jay carries one): 2 px, its top inside the bill.
    acorn = Cube((-1.0, by0 - 1.5, hz0 - bl - 0.5), (2, 2, 2), 'acorn')
    bones.append(Bone('food_acorn', (0, by0, hz0 - bl + 0.5), 'head', cubes=[acorn],
                      locators={'food_acorn_tip': (0, by0 - 1.5, hz0 - bl + 0.5)}))

    # Wings: plates 1 px thick on the sides of the body, their top half a pixel under the back; the primaries behind them,
    # 0.1 px further out (no shared plane with the arm) and sunk 0.5 px into its back end.
    wl, wh = px(q.wing_length), px(q.wing_height)
    pl, ph = px(q.primaries_length), px(q.primaries_height)
    wy1 = top - 0.5
    wz0 = z_front + 1.0
    for side, sx in (('left', 1), ('right', -1)):
        x0 = W / 2.0 if sx > 0 else -W / 2.0 - 1.0
        pivot = (sx * W / 2.0, wy1, wz0)
        wing = Cube((x0, wy1 - wh, wz0), (1, wh, wl), 'wing')
        tip = (sx * (W / 2.0 + 0.5), wy1 - wh / 2.0, wz0 + wl)
        bones.append(Bone(f'wing_{side}', pivot, 'body', cubes=[wing], locators={f'wing_{side}_tip': tip}))
        px0 = x0 + sx * 0.1
        pz0 = wz0 + wl - 0.5
        py0 = wy1 - 1.0 - ph
        prim = Cube((px0, py0, pz0), (1, ph, pl), 'primaries')
        bones.append(Bone(f'wing_tip_{side}', (sx * W / 2.0, wy1, pz0), f'wing_{side}', cubes=[prim],
                          locators={f'wing_tip_{side}_tip': (px0 + 0.5, py0 + ph / 2.0, pz0 + pl)}))

    # The tail: a flat cube from the top back of the body, sunk 0.5 px into it, held a little down.
    tl, tw = px(q.tail_length), px(q.tail_width)
    if tw % 2 == W % 2 and tw >= W:
        tw -= 1
    ty = top - 2.5
    tail = Cube((-tw / 2.0, ty, z_back - 0.5), (tw, 1, tl), 'tail')
    bones.append(Bone('tail', (0, ty + 0.5, z_back - 0.5), 'body', rotation=(-6.0, 0, 0), cubes=[tail],
                      locators={'tail_tip': (0, ty + 0.5, z_back - 0.5 + tl)}))

    # Legs: the tarsus 1 px square from half a pixel above the ground into the belly; the foot a 2 x 1 x 2 plate under it
    # (wider than the tarsus and a quarter pixel in front of its back face, so their faces never share a plane), its
    # toes forward.
    for side, sx in (('left', 1), ('right', -1)):
        xc = sx * 1.25
        leg = Cube((xc - 0.5, 0.5, -0.5), (1, bottom, 1), 'leg')
        foot = Cube((xc - 1.0, 0, -1.75), (2, 1, 2), 'foot')
        bones.append(Bone(f'leg_{side}', (xc, bottom + 0.25, 0), 'body', cubes=[leg, foot],
                          locators={f'leg_{side}_tip': (xc, 0, 0)}))
    return Model(q.id, bones, texture_size, q.hitbox, MODEL_SCALE)
