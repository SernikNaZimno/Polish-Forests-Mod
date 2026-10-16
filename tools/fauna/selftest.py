"""Self-test of tools/fauna, run by check.py (and so by checkArt).

1. Kinematics against GeckoLib itself: the corners of two turned cubes (a bone turned by (20, 30, 40) and its child turned
   by (-10, 15, -25), pivots in model space) as GeckoLib 5.5.7 bakes and poses them (GeometryBone.bake and
   RenderUtil.prepMatrixForBoneAndUpdateListeners run outside the game on 2026-10-16, numbers below) must come out of
   model.bone_matrices within 1e-3 px.
2. The validator finds what it must: UV rectangles of two cubes on the same texels, a face outside the texture, a missing
   parent, a repeated locator name, an animation keyframe after the end, a texel painted outside the faces, a black texel,
   a coat in rows of one tone, two faces in one plane, a leg under the ground, an animal floating above it, two legs
   through each other.
Usage: python tools/fauna/selftest.py
"""
import copy
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import uvpack  # noqa: E402
import validate  # noqa: E402
from model import Bone, Cube, Model, apply, bone_matrices, geo_json  # noqa: E402

# Bedrock-space corners of the two unit cubes, posed by GeckoLib (x of GeckoLib's model space negated back).
GECKOLIB_POSED = {
    ('p', (1, 1, 1)): (-0.6326, 1.2502, 1.6686),
    ('p', (0, 1, 1)): (-1.2960, 1.8069, 2.1686),
    ('p', (1, 0, 0)): (-1.6854, 0.4605, 1.1510),
    ('p', (0, 0, 0)): (-2.3488, 1.0172, 1.6510),
    ('c', (5, 6, 1)): (3.4020, 4.7632, -0.8454),
    ('c', (4, 6, 1)): (2.7782, 4.9015, -0.0762),
    ('c', (5, 5, 1)): (3.1226, 3.8046, -0.8996),
    ('c', (4, 5, 0)): (1.7689, 4.1915, -0.7670),
}


def kinematics_errors():
    m = Model('t', [Bone('p', (1, 2, 3), rotation=(20, 30, 40), cubes=[Cube((0, 0, 0), (1, 1, 1), 'p')]),
                    Bone('c', (4, 5, 6), parent='p', rotation=(-10, 15, -25), cubes=[Cube((4, 5, 0), (1, 1, 1), 'c')])])
    ms = bone_matrices(m)
    errs = []
    for (bone, corner), want in GECKOLIB_POSED.items():
        got = apply(ms[bone], corner)
        if np.abs(got - np.array(want)).max() > 1e-3:
            errs.append(f'kinematics: {bone} corner {corner} -> {np.round(got, 4).tolist()}, GeckoLib gives {list(want)}')
    return errs


def validator_errors():
    errs = []
    base = Model('v', [Bone('a', (0, 0, 0), cubes=[Cube((0, 0, 0), (2, 2, 2), 'a')], locators={'tip': (0, 2, 0)}),
                       Bone('b', (0, 0, 0), parent='a', cubes=[Cube((0, 2, 0), (2, 2, 2), 'b')], locators={'end': (0, 4, 0)})],
                 (32, 16))
    uvpack.pack(base)
    if uvpack.occupancy(base)[1]:
        errs.append('validator: a packed model reports UV problems')
    overlap = copy.deepcopy(base)
    overlap.bones[1].cubes[0].uv = overlap.bones[0].cubes[0].uv
    if not any('more than one face' in p for p in uvpack.occupancy(overlap)[1]):
        errs.append('validator: overlapping UV not found')
    outside = copy.deepcopy(base)
    outside.bones[1].cubes[0].uv = (30, 14)
    if not any('outside' in p for p in uvpack.occupancy(outside)[1]):
        errs.append('validator: UV outside the texture not found')
    geo = geo_json(base)
    bad_parent = copy.deepcopy(geo)
    bad_parent['minecraft:geometry'][0]['bones'][1]['parent'] = 'nope'
    repeated = copy.deepcopy(geo)
    repeated['minecraft:geometry'][0]['bones'][1]['locators'] = {'tip': [0, 4, 0]}
    for name, g, word in (('missing parent', bad_parent, 'parent'), ('repeated locator', repeated, 'repeated')):
        try:
            validate.model_from_geo(g)
            errs.append(f'validator: {name} not found')
        except ValueError as e:
            if not any(word in m for m in e.args[0]):
                errs.append(f'validator: {name} reported as {e.args[0]}')
    anims = {'format_version': '1.8.0', 'animations': {'x': {'loop': True, 'animation_length': 1.0,
                                                               'bones': {'a': {'rotation': {'1.5': [0, 0, 0]}}}}}}
    if not any('outside' in m for m in validate.check_animations(anims, base, 'v')):
        errs.append('validator: keyframe after the end not found')
    img = np.zeros((16, 32, 4), np.uint8)
    occ, _ = uvpack.occupancy(base)
    img[occ > 0] = (120, 100, 80, 255)
    stray = img.copy()
    stray[15, 31] = (1, 2, 3, 255)
    if not any('outside every face' in m for m in validate.texture_report(base, stray)[0]):
        errs.append('validator: texel painted outside the faces not found')
    black = img.copy()
    ys, xs = np.nonzero(occ)
    black[ys[0], xs[0]] = (0, 0, 0, 255)
    if not any('pure black' in m for m in validate.texture_report(base, black)[0]):
        errs.append('validator: black texel not found')
    errs += shape_and_pose_errors()
    return errs


def shape_and_pose_errors():
    """The checks added after the review of V8: a coat in rows of one tone, faces in one plane, a leg under the ground,
    an animal floating above it, legs through each other."""
    errs = []
    flat = Model('f', [Bone('a', (0, 0, 0), cubes=[Cube((0, 0, 0), (8, 4, 2), 'a')])], (32, 16))
    uvpack.pack(flat)
    occ, _ = uvpack.occupancy(flat)
    striped = np.zeros((16, 32, 4), np.uint8)
    for v in range(16):
        striped[v, :] = (60 + 12 * v, 40 + 10 * v, 30, 255)
    striped[occ == 0] = 0
    if not any('one tone' in m for m in validate.coat_report(flat, striped)[0]):
        errs.append('validator: a coat in rows of one tone not found')
    twins = Model('c', [Bone('a', (0, 0, 0), cubes=[Cube((0, 0, 0), (2, 2, 2), 'a')]),
                        Bone('b', (0, 0, 0), cubes=[Cube((1, 0, 1), (2, 2, 2), 'b')])], (32, 16))
    uvpack.pack(twins)
    if not validate.coplanar_faces(twins):
        errs.append('validator: faces in one plane not found')
    legs = Model('l', [Bone('body', (0, 4, 0), cubes=[Cube((-2, 4, -3), (4, 2, 6), 'body')]),
                       Bone('leg_front_left', (1, 4, -2), 'body', cubes=[Cube((0.5, 0, -2.5), (1, 5, 1), 'leg')]),
                       Bone('leg_back_left', (1, 4, 2), 'body', cubes=[Cube((0.5, 0, 1.5), (1, 5, 1), 'leg')])], (32, 16))
    uvpack.pack(legs)

    def anim(bones):
        return {'animations': {'misc.test': {'loop': True, 'animation_length': 1.0, 'bones': bones}}}
    sunk = anim({'body': {'position': {'0.0': [0, -0.5, 0], '1.0': [0, -0.5, 0]}}})
    if not any('under the ground' in m for m in validate.check_poses(sunk, legs)):
        errs.append('validator: a leg under the ground not found')
    floating = anim({'body': {'position': {'0.0': [0, 0.5, 0], '1.0': [0, 0.5, 0]}}})
    if not any('floats' in m for m in validate.check_poses(floating, legs)):
        errs.append('validator: a floating animal not found')
    crossed = anim({'leg_front_left': {'rotation': {'0.0': [60, 0, 0], '1.0': [60, 0, 0]}},
                    'leg_back_left': {'rotation': {'0.0': [-60, 0, 0], '1.0': [-60, 0, 0]}}})
    if not any('goes through' in m for m in validate.check_poses(crossed, legs)):
        errs.append('validator: legs through each other not found')
    return errs


def run():
    return kinematics_errors() + validator_errors()


if __name__ == '__main__':
    problems = run()
    for p in problems:
        print(p)
    print('selftest:', 'FAILED' if problems else 'passes')
    sys.exit(1 if problems else 0)
