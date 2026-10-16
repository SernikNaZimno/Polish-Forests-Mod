"""Model data of tools/fauna: bones, cubes and locators in Bedrock model space, box UV, kinematics of GeckoLib 5.

Units and axes (Bedrock geometry, what the .geo.json files store): 1 unit = 1 model pixel = 1/16 block = 6.25 cm; +Y is
up, the animal looks towards -Z, and +X is its LEFT side (GeckoLib mirrors X when it bakes a model, so the left side of
the file is the left side of the animal in the world). The conventions below were read from the GeckoLib 5.5.7 jar
(GeometryBone.bake, GeometryCube box UV, RenderUtil, BoneSnapshot, ActorBoneAnimationKeyframe) and are checked in the
game by the axis gauge of the fauna game test (step V8, docs/04-pionowy-wycinek.md):

- a bone turns about its pivot, first about X, then Y, then Z (GeckoLib: PoseStack Z, Y, X with the X and Y angles
  negated, in a space with X negated). In the Bedrock space of the file this is R = Rz(-rz) . Ry(ry) . Rx(-rx) with
  right-handed rotation matrices, so a positive X angle turns the front of a bone (-Z) DOWN, a positive Y angle turns it
  towards the right side (-X), and a positive Z angle turns its top towards the left side (+X);
- animation rotations add to the rest rotation of the bone, per axis, before the matrix is built; animation positions
  move the bone (and its children) by (x, y, z) pixels of the parent space, before the pivot rotation;
- a child bone's pivot and cubes are given in model space (not relative to the parent), as Blockbench writes them.

Box UV: a cube of size (sx, sy, sz) with uv (u, v) takes the rectangle 2 (sx + sz) x (sy + sz). Top row: the top face at
(u + sz, v) and the bottom face at (u + sz + sx, v); second row (v + sz): the right side (-X) at u, the front (-Z) at
u + sz, the left side (+X) at u + sz + sx, the back (+Z) at u + 2 sz + sx. The texel corners of every face are in
FACE_CORNERS (from the quads GeckoLib bakes); seen from outside every face shows its texture unmirrored, the right side
with the head to the right of the texture, the left side with the head to the left, the top with the back at the top.
"""
import math
from dataclasses import dataclass, field

import numpy as np


@dataclass
class Cube:
    origin: tuple          # (x, y, z) of the minimum corner, model pixels
    size: tuple            # (sx, sy, sz); whole pixels, so box UV lands on whole texels
    part: str              # semantic name for the painter ('body', 'head', 'leg', ...)
    uv: tuple = None       # (u, v) set by the packer
    inflate: float = 0.0

    def box_uv_size(self):
        sx, sy, sz = (int(math.ceil(v)) for v in self.size)
        return 2 * (sx + sz), sy + sz


@dataclass
class Bone:
    name: str
    pivot: tuple
    parent: str = None
    rotation: tuple = (0.0, 0.0, 0.0)     # rest rotation, degrees, Bedrock convention (see module doc)
    cubes: list = field(default_factory=list)
    locators: dict = field(default_factory=dict)   # name -> (x, y, z), model space


@dataclass
class Model:
    id: str                 # e.g. 'roe_deer'
    bones: list
    texture_size: tuple = (64, 64)
    hitbox: tuple = (0.7, 1.1)    # entity width, height in blocks (for the preview frame only)

    def bone(self, name):
        for b in self.bones:
            if b.name == name:
                return b
        raise KeyError(name)

    def cubes(self):
        return [c for b in self.bones for c in b.cubes]


# ------------------------------------------------------------------ box UV faces

FACES = ('up', 'down', 'right', 'front', 'left', 'back')
# Outward normals in Bedrock space (right = -X, left = +X, front = -Z, back = +Z).
NORMALS = {'up': (0, 1, 0), 'down': (0, -1, 0), 'right': (-1, 0, 0), 'left': (1, 0, 0), 'front': (0, 0, -1),
           'back': (0, 0, 1)}


def face_rects(cube):
    """Face -> (u, v, w, h) texel rectangle of the box UV layout."""
    u, v = cube.uv
    sx, sy, sz = (int(math.ceil(t)) for t in cube.size)
    return {'up': (u + sz, v, sx, sz), 'down': (u + sz + sx, v, sx, sz),
            'right': (u, v + sz, sz, sy), 'front': (u + sz, v + sz, sx, sy),
            'left': (u + sz + sx, v + sz, sz, sy), 'back': (u + 2 * sz + sx, v + sz, sx, sy)}


def face_corners(cube):
    """Face -> four (corner xyz, texel uv) pairs, exactly as GeckoLib 5.5.7 bakes a box-UV cube (read from its quads)."""
    (x0, y0, z0) = (o - cube.inflate for o in cube.origin)
    x1, y1, z1 = (cube.origin[i] + cube.size[i] + cube.inflate for i in range(3))
    u, v = cube.uv
    sx, sy, sz = (int(math.ceil(t)) for t in cube.size)
    return {
        'right': [((x0, y1, z0), (u + sz, v + sz)), ((x0, y1, z1), (u, v + sz)),
                  ((x0, y0, z1), (u, v + sz + sy)), ((x0, y0, z0), (u + sz, v + sz + sy))],
        'front': [((x1, y1, z0), (u + sz + sx, v + sz)), ((x0, y1, z0), (u + sz, v + sz)),
                  ((x0, y0, z0), (u + sz, v + sz + sy)), ((x1, y0, z0), (u + sz + sx, v + sz + sy))],
        'left': [((x1, y1, z1), (u + 2 * sz + sx, v + sz)), ((x1, y1, z0), (u + sz + sx, v + sz)),
                 ((x1, y0, z0), (u + sz + sx, v + sz + sy)), ((x1, y0, z1), (u + 2 * sz + sx, v + sz + sy))],
        'back': [((x0, y1, z1), (u + 2 * sz + 2 * sx, v + sz)), ((x1, y1, z1), (u + 2 * sz + sx, v + sz)),
                 ((x1, y0, z1), (u + 2 * sz + sx, v + sz + sy)), ((x0, y0, z1), (u + 2 * sz + 2 * sx, v + sz + sy))],
        'up': [((x1, y1, z1), (u + sz + sx, v)), ((x0, y1, z1), (u + sz, v)),
               ((x0, y1, z0), (u + sz, v + sz)), ((x1, y1, z0), (u + sz + sx, v + sz))],
        'down': [((x1, y0, z0), (u + sz + 2 * sx, v + sz)), ((x0, y0, z0), (u + sz + sx, v + sz)),
                 ((x0, y0, z1), (u + sz + sx, v)), ((x1, y0, z1), (u + sz + 2 * sx, v))],
    }


def texel_point(cube, face, tu, tv):
    """Model-space point on a face of a cube at the texel-space position (tu, tv) (bilinear in the face rectangle)."""
    cs = face_corners(cube)[face]
    (p0, t0), (p1, t1), (p2, t2), (p3, t3) = cs
    # corners 0..3 go around the face; t0 and t2 are opposite texel corners.
    us = sorted({t0[0], t1[0], t2[0], t3[0]})
    vs = sorted({t0[1], t1[1], t2[1], t3[1]})
    a = (tu - us[0]) / max(1e-9, us[-1] - us[0])
    b = (tv - vs[0]) / max(1e-9, vs[-1] - vs[0])
    # corner at (u min, v min), (u max, v min), (u min, v max), (u max, v max)
    def at(uu, vv):
        for p, t in cs:
            if t[0] == uu and t[1] == vv:
                return np.array(p, float)
        raise AssertionError('corner not found')
    q00, q10, q01, q11 = at(us[0], vs[0]), at(us[-1], vs[0]), at(us[0], vs[-1]), at(us[-1], vs[-1])
    return (1 - a) * (1 - b) * q00 + a * (1 - b) * q10 + (1 - a) * b * q01 + a * b * q11


# ------------------------------------------------------------------ kinematics


def rot_x(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]], float)


def rot_y(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], float)


def rot_z(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], float)


def translate(t):
    m = np.eye(4)
    m[:3, 3] = t
    return m


def bone_rotation(rx, ry, rz):
    """Rotation matrix of a bone in Bedrock space for the file angles (rx, ry, rz) in degrees (see module doc)."""
    return rot_z(-rz) @ rot_y(ry) @ rot_x(-rx)


def bone_matrices(model, pose=None):
    """Bone name -> 4x4 matrix from the bone's model space to posed model space (Bedrock pixels).

    pose: bone name -> dict(rotation=(x, y, z) degrees, position=(x, y, z) pixels), as an animation sets them.
    """
    pose = pose or {}
    by = {b.name: b for b in model.bones}
    out = {}

    def mat(name):
        if name in out:
            return out[name]
        b = by[name]
        parent = mat(b.parent) if b.parent else np.eye(4)
        p = pose.get(name, {})
        r = [b.rotation[i] + p.get('rotation', (0, 0, 0))[i] for i in range(3)]
        t = p.get('position', (0, 0, 0))
        local = translate(t) @ translate(b.pivot) @ bone_rotation(*r) @ translate([-v for v in b.pivot])
        out[name] = parent @ local
        return out[name]

    for b in model.bones:
        mat(b.name)
    return out


def apply(m, p):
    return (m @ np.array([p[0], p[1], p[2], 1.0]))[:3]


def pivots_and_locators(model, pose=None):
    """Posed model-space positions: 'bone:<name>' -> pivot (as GeckoLib's bone position listener reports it, i.e. the
    pivot moved by the parents and the bone's own animation position), 'locator:<name>' -> locator (names are unique in
    a model)."""
    ms = bone_matrices(model, pose)
    by = {b.name: b for b in model.bones}
    pose = pose or {}
    out = {}
    for b in model.bones:
        parent = ms[b.parent] if b.parent else np.eye(4)
        t = pose.get(b.name, {}).get('position', (0, 0, 0))
        out['bone:' + b.name] = apply(parent @ translate(t), b.pivot)
        for ln, lp in b.locators.items():
            out[f'locator:{ln}'] = apply(ms[b.name], lp)
    return out


# GeckoLib's entity renderer lifts every model by 0.01 block after turning it (GeoEntityRenderer.adjustRenderPose),
# measured by the fauna game test as a constant 0.0100 block offset of every bone.
ENTITY_LIFT = 0.01


def to_world(p, entity_pos, yaw, lift=ENTITY_LIFT):
    """Model point (pixels) of an entity at entity_pos (blocks) with body yaw in degrees (Minecraft: 0 = south, +Z;
    90 = west) -> world position in blocks. GeckoLib mirrors X, the entity renderer turns the model by 180 - yaw and
    lifts it by ENTITY_LIFT."""
    xj, y, z = -p[0] / 16.0, p[1] / 16.0, p[2] / 16.0
    a = math.radians(180.0 - yaw)
    c, s = math.cos(a), math.sin(a)
    return np.array([entity_pos[0] + xj * c + z * s, entity_pos[1] + y + lift, entity_pos[2] - xj * s + z * c])


# ------------------------------------------------------------------ export


def _num(v):
    v = float(v)
    return int(v) if v == int(v) else round(v, 4)


def geo_json(model):
    """The .geo.json of a model, format 1.12.0 with box UV."""
    bones = []
    for b in model.bones:
        jb = {'name': b.name}
        if b.parent:
            jb['parent'] = b.parent
        jb['pivot'] = [_num(v) for v in b.pivot]
        if any(b.rotation):
            jb['rotation'] = [_num(v) for v in b.rotation]
        if b.cubes:
            cubes = []
            for c in b.cubes:
                jc = {'origin': [_num(v) for v in c.origin], 'size': [_num(v) for v in c.size], 'uv': [int(c.uv[0]), int(c.uv[1])]}
                if c.inflate:
                    jc['inflate'] = _num(c.inflate)
                cubes.append(jc)
            jb['cubes'] = cubes
        if b.locators:
            jb['locators'] = {k: [_num(v) for v in p] for k, p in b.locators.items()}
        bones.append(jb)
    w, h = model.texture_size
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.{model.id}', 'texture_width': w, 'texture_height': h,
                        'visible_bounds_width': 3, 'visible_bounds_height': 2.5, 'visible_bounds_offset': [0, 1, 0]},
        'bones': bones}]}
