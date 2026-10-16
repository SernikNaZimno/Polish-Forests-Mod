"""Species and test models of tools/fauna.

ROE_DEER: the European roe deer (Capreolus capreolus), adults from docs/research/07-fauna-polski-siedliska-zachowania.md
§3.2 and §13: withers 60-90 cm (75 on average), the rump higher than the withers, trunk length about 1.6 x the withers
height with the head, legs about 55% of the withers height, large ears, antlers of the buck 25 cm with 3 tines each.
Hitbox 0.7 x 1.1 blocks (docs/04 §0.3, C2).

AXIS_GAUGE: the calibration model of the fauna game test (step V8): arms turned by known angles about each axis, a chain
of two turned bones, a bone moved and turned only by an animation, and a marker on the left side (+X), each with a
locator at its tip. It lives only in the game test mod (namespace polishforests_probe).
"""
from model import Bone, Cube, Model
from quadruped import Quadruped, build

ROE_DEER_SHAPE = Quadruped(
    id='roe_deer',
    withers=75, rump=81, trunk_length=80, chest_share=0.55, chest_depth=34, flank_depth=31, width=28,
    neck_length=44, neck_thickness=18, neck_width=12.5, neck_angle=52,
    head_length=19, head_height=16, head_width=18, muzzle_length=12.5, muzzle_height=12.5, muzzle_width=12.5,
    head_angle=20,
    ear_length=17, ear_width=11, ear_spread=14,
    leg_width=12.5, tail_length=2, antler_length=25, hitbox=(0.7, 1.1))


def roe_deer():
    return build(ROE_DEER_SHAPE, (64, 64))


# The pose the axis gauge animation 'calib.pose' holds (the game test checks the bone 'anim' in it).
AXIS_GAUGE_ANIM = {'anim': {'rotation': (25.0, -35.0, 15.0), 'position': (1.0, 2.0, -3.0)}}


def axis_gauge():
    bones = [
        Bone('base', (0, 0, 0), cubes=[Cube((-3, 0, -3), (6, 1, 6), 'base')]),
        # X: the front of the arm (-Z) turns down for a positive angle.
        Bone('pitch', (0, 4, 0), 'base', rotation=(30, 0, 0), cubes=[Cube((-0.5, 3.5, -6), (1, 1, 6), 'pitch')],
             locators={'pitch_tip': (0, 4, -6)}),
        # Y: the front turns towards the right side (-X) for a positive angle.
        Bone('yaw', (0, 8, 0), 'base', rotation=(0, 30, 0), cubes=[Cube((-0.5, 7.5, -6), (1, 1, 6), 'yaw')],
             locators={'yaw_tip': (0, 8, -6)}),
        # Z: the top turns towards the left side (+X) for a positive angle.
        Bone('roll', (0, 12, 0), 'base', rotation=(0, 0, 30), cubes=[Cube((-0.5, 12, -0.5), (1, 6, 1), 'roll')],
             locators={'roll_tip': (0, 18, 0)}),
        # All three axes at once and a turned child: the order of the rotations and the child frames.
        Bone('chain', (0, 16, 4), 'base', rotation=(20, 30, 40), cubes=[Cube((-0.5, 15.5, 4), (1, 1, 6), 'chain')],
             locators={'chain_tip': (0, 16, 10)}),
        Bone('chain_child', (0, 16, 10), 'chain', rotation=(-10, 15, -25), cubes=[Cube((-0.5, 15.5, 10), (1, 1, 5), 'chain_child')],
             locators={'chain_child_tip': (0, 16, 15)}),
        # Turned and moved only by the animation.
        Bone('anim', (6, 4, 0), 'base', cubes=[Cube((5.5, 3.5, -6), (1, 1, 6), 'anim')], locators={'anim_tip': (6, 4, -6)}),
        # The left side of the model (+X); in the world it must be on the left of the entity.
        Bone('left_marker', (8, 0, 0), 'base', cubes=[Cube((7, 0, -1), (2, 3, 2), 'left')], locators={'left_marker_tip': (8, 3, 0)}),
        # The front of the model (-Z); in the world it must point where the entity looks.
        Bone('front_marker', (0, 0, -8), 'base', cubes=[Cube((-1, 0, -9), (2, 2, 2), 'front')], locators={'front_marker_tip': (0, 2, -8)}),
    ]
    return Model('axis_gauge', bones, (64, 64), (0.6, 1.2))
