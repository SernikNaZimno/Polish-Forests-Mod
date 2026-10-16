"""Texture painting of tools/fauna: every texel of every face is painted from its point on the model.

paint_model() walks the box UV faces of all cubes and asks a shader for the color of each texel. The shader gets the
texel's part, face, position inside the face and its point on the model (rest pose, model pixels), so markings that run
over several faces and cubes (the dark back, the light belly, the white rump patch) stay continuous across the seams.

The style follows the user's note at checkpoint CP-A (textures too sharp): soft countershading gradients, a low and
smooth fur variation (value noise with a period of several texels, never single-pixel noise), tones in small steps,
and markings with an in-between tone at their edge; no pure black, the darkest details (eyes, nose, hooves) are dark
browns. smoothness() measures the result (mean and largest neighbour difference inside faces).
"""
import importlib.util
import math
import os

import numpy as np

from model import face_rects, texel_point

HERE = os.path.dirname(os.path.abspath(__file__))


def _load_texlib():
    """tools/art/texlib.py by its path (the deterministic Rng shared with tools/art), without putting tools/art on
    sys.path, where its generate.py and sheet.py would shadow the modules of the same names here."""
    spec = importlib.util.spec_from_file_location('art_texlib', os.path.join(HERE, '..', 'art', 'texlib.py'))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


texlib = _load_texlib()


class Noise3:
    """Smooth value noise in model space (lattice period in pixels), deterministic from a string seed."""

    def __init__(self, seed, period=3.0, size=24):
        rng = texlib.Rng(seed)
        self.grid = rng.array((size, size, size))
        self.period = period
        self.size = size

    def __call__(self, p):
        q = np.asarray(p, float) / self.period + self.size * 4.0
        i = np.floor(q).astype(int)
        f = q - i
        f = f * f * (3 - 2 * f)
        n = self.size
        acc = 0.0
        for dx in (0, 1):
            for dy in (0, 1):
                for dz in (0, 1):
                    w = (f[0] if dx else 1 - f[0]) * (f[1] if dy else 1 - f[1]) * (f[2] if dz else 1 - f[2])
                    acc += w * self.grid[(i[0] + dx) % n, (i[1] + dy) % n, (i[2] + dz) % n]
        return acc * 2.0 - 1.0     # -1 .. 1


def mix(a, b, t):
    t = min(1.0, max(0.0, t))
    return tuple(a[k] * (1 - t) + b[k] * t for k in range(3))


def shade(c, f):
    return tuple(c[k] * f for k in range(3))


def posterize(f, step=0.04):
    """Brightness factor in steps (vanilla-like tone clusters, steps small enough to stay soft)."""
    return round(f / step) * step


class Texel:
    """One texel being painted. A shader sets detail = True for the small marks that are meant to stand out (eyes, nose,
    lip, ear rims, hooves, antlers, tail); the smoothness measure leaves them out."""
    __slots__ = ('bone', 'cube', 'part', 'face', 'i', 'j', 'w', 'h', 'p', 'cube_t', 'detail')

    def __init__(self, bone, cube, face, i, j, w, h, p):
        self.bone, self.cube, self.part, self.face = bone, cube, cube.part, face
        self.i, self.j, self.w, self.h, self.p = i, j, w, h, p
        self.detail = False
        o, s = cube.origin, cube.size
        # position inside the cube, 0..1 per axis (x right->left, y bottom->top, z front->back)
        self.cube_t = tuple((p[k] - o[k]) / s[k] if s[k] else 0.5 for k in range(3))


def paint_model(model, shader, with_details=False):
    """RGBA texture (H x W x 4, uint8) of a model; texels outside every face are transparent. With with_details also the
    mask (H x W bool) of the texels the shader marked as details."""
    tw, th = model.texture_size
    img = np.zeros((th, tw, 4), np.uint8)
    details = np.zeros((th, tw), bool)
    for b in model.bones:
        for c in b.cubes:
            for face, (u, v, w, h) in face_rects(c).items():
                for j in range(h):
                    for i in range(w):
                        p = texel_point(c, face, u + i + 0.5, v + j + 0.5)
                        t = Texel(b.name, c, face, i, j, w, h, p)
                        rgb = shader(t)
                        img[v + j, u + i] = (*[int(round(min(255, max(0, x)))) for x in rgb[:3]], 255)
                        details[v + j, u + i] = t.detail
    return (img, details) if with_details else img


def smoothness(model, img, details=None):
    """Mean, 95th percentile and largest luminance step (0-255) between 4-neighbour texels of the same face, over all
    faces, leaving out the pairs with a detail texel (details: H x W bool, or None)."""
    lum = img[..., :3].astype(float) @ np.array([0.2126, 0.7152, 0.0722])
    keep = ~details if details is not None else np.ones(lum.shape, bool)
    diffs = []
    for b in model.bones:
        for c in b.cubes:
            for face, (u, v, w, h) in face_rects(c).items():
                f = lum[v:v + h, u:u + w]
                k = keep[v:v + h, u:u + w]
                if w > 1:
                    diffs.extend(np.abs(np.diff(f, axis=1))[k[:, 1:] & k[:, :-1]].ravel())
                if h > 1:
                    diffs.extend(np.abs(np.diff(f, axis=0))[k[1:, :] & k[:-1, :]].ravel())
    d = np.array(diffs) if diffs else np.zeros(1)
    return float(d.mean()), float(np.percentile(d, 95)), float(d.max())


# ------------------------------------------------------------------ roe deer

# Coat colors (report 07 §13: summer (175, 100, 55), winter (125, 115, 100), rump patch (240, 235, 225), black nose and
# white "moustache"), spread into back / flank / belly tones.
ROE_COATS = {
    'summer': dict(back=(160, 92, 52), flank=(192, 112, 62), belly=(218, 172, 124), face=(164, 116, 82),
                   muzzle=(134, 106, 88), patch=(230, 214, 180), throat=(206, 160, 112), leg=(182, 112, 70),
                   ear_out=(152, 110, 84), ear_in=(214, 194, 168)),
    'winter': dict(back=(114, 104, 92), flank=(140, 128, 112), belly=(186, 176, 160), face=(124, 112, 100),
                   muzzle=(108, 98, 90), patch=(238, 234, 224), throat=(218, 210, 196), leg=(134, 122, 108),
                   ear_out=(116, 106, 96), ear_in=(204, 194, 180)),
}
ROE_DARK = dict(eye=(48, 37, 32), eye_rim=(92, 74, 62), nose=(56, 47, 43), lip=(222, 216, 204), hoof=(70, 60, 54),
                ear_tip=(84, 72, 64))
ANTLERS = {'summer': dict(beam=(118, 94, 70), tip=(212, 198, 168)),     # polished antlers (V-X)
           'winter': dict(beam=(128, 112, 96), tip=(156, 140, 120))}    # in velvet (scypuł, XII-IV)


def roe_deer_shader(model, sex, season, seed='roe_deer'):
    """The shader of one texture variant: sex 'doe' or 'buck', season 'summer' or 'winter'."""
    C = ROE_COATS[season]
    A = ANTLERS[season]
    fur = Noise3(f'{seed}/{season}/fur', period=3.0)
    fine = Noise3(f'{seed}/{season}/fine', period=1.6)
    body = model.bone('body').cubes
    trunk_y0 = min(c.origin[1] for c in body)
    trunk_y1 = max(c.origin[1] + c.size[1] for c in body)
    trunk_z1 = max(c.origin[2] + c.size[2] for c in body)
    # The rump patch (lustro): large and white in winter, smaller and creamy in summer, when it shows mostly in flight.
    rump_patch = 2.4 if season == 'winter' else 1.9     # half width on the back of the rump, px
    patch_strength = 1.0 if season == 'winter' else 0.7

    def variation(p, amount=0.05):
        return 1.0 + amount * (0.75 * fur(p) + 0.25 * fine(p))

    def coat(t, p, side_light=1.0):
        """Countershading by height t (0 belly .. 1 back)."""
        if t < 0.5:
            c = mix(C['belly'], C['flank'], (t - 0.08) / 0.42)
        else:
            c = mix(C['flank'], C['back'], (t - 0.55) / 0.45)
        return shade(c, posterize(variation(p) * side_light))

    def patch_weight(p, cube):
        """0..1 share of the rump patch (lustro) at a point on the hindquarters: an ellipse on the back of the rump that
        wraps a little over the sides; the doe's (heart-shaped, winter) reaches lower in the middle (anal tuft), the
        buck's (kidney-shaped) has a notch at the top."""
        top = cube.origin[1] + cube.size[1]
        cy = top - 2.6
        dz = trunk_z1 - p[2]
        if dz > 1.2:
            return 0.0
        dx = abs(p[0]) / rump_patch
        dy = (p[1] - cy) / (2.2 if season == 'winter' else 1.6)
        if sex == 'doe' and season == 'winter' and dy < 0 and abs(p[0]) < 0.8:
            dy *= 0.6     # the tuft
        if sex == 'buck' and dy > 0.4 and abs(p[0]) < 0.6:
            dy += 0.5     # the notch
        r = math.hypot(dx, dy) + 0.6 * dz
        return patch_strength * min(1.0, max(0.0, (1.15 - r) / 0.45))

    def shader(t):
        part, face, p = t.part, t.face, t.p
        if part in ('chest', 'hind'):
            h = (p[1] - trunk_y0) / (trunk_y1 - trunk_y0)
            if face == 'up':
                h = 1.0
            elif face == 'down':
                h = 0.0
            col = coat(h, p)
            if part == 'hind':
                w = patch_weight(p, t.cube)
                if w > 0:
                    col = mix(col, shade(C['patch'], posterize(variation(p, 0.025))), w)
            if part == 'chest' and face == 'front':
                # chest front a little lighter below (towards the throat)
                col = mix(col, C['throat'], 0.35 * (1 - t.j / max(1, t.h - 1)))
            return col
        if part == 'neck':
            # the neck cube's top (mane line) is darker, its underside is the throat
            if face == 'up':
                col = coat(0.95, p)
            elif face == 'down':
                col = shade(C['throat'], posterize(variation(p, 0.03)))
                if season == 'winter' and 1 <= t.j <= 2:
                    col = mix(col, C['patch'], 0.55)      # the pale winter throat patch
            else:
                col = coat(0.85 - 0.6 * t.j / max(1, t.h - 1), p)
            return col
        if part == 'skull':
            base = C['face'] if not (sex == 'buck' and face == 'up') else shade(C['face'], 0.82)  # buck: darker forehead
            col = shade(base, posterize(variation(p, 0.04)))
            if face in ('left', 'right'):
                # eye: one dark texel in the front column of the upper row, with a soft rim tone in front of and above it
                front_col = t.w - 2 if face == 'right' else 1     # the right side shows the head to the right
                if t.j == 0 and t.i == front_col:
                    t.detail = True
                    return ROE_DARK['eye']
                if (t.j == 0 and abs(t.i - front_col) == 1) or (t.j == 1 and t.i == front_col):
                    t.detail = True
                    col = mix(col, ROE_DARK['eye_rim'], 0.45)
                if t.j == t.h - 1:
                    col = mix(col, C['belly'], 0.35)        # lighter cheek and jaw
            if face == 'down':
                col = mix(col, C['belly'], 0.5)
            if face == 'front':
                col = shade(col, 0.92)
            return col
        if part == 'muzzle':
            t.detail = face != 'up'           # nose, lip and chin
            col = shade(C['muzzle'], posterize(variation(p, 0.03)))
            if face == 'front':
                # nose on the upper row, the white band below it
                return ROE_DARK['nose'] if t.j == 0 else mix(ROE_DARK['lip'], C['muzzle'], 0.15)
            if face == 'down':
                return mix(ROE_DARK['lip'], col, 0.1)            # white chin
            if face in ('left', 'right'):
                front = (t.i == t.w - 1) if face == 'right' else (t.i == 0)
                if t.j == t.h - 1:
                    return mix(ROE_DARK['lip'], col, 0.35 if not front else 0.15)   # white lip line
                if front:
                    return mix(col, ROE_DARK['nose'], 0.55)       # nose wraps the front corner
            if face == 'up':
                col = mix(col, C['face'], 0.3)
            return col
        if part == 'ear':
            if face == 'front':                                  # inner side: pale, a soft darker middle
                col = shade(C['ear_in'], posterize(variation(p, 0.03)))
                if 0 < t.i < t.w - 1 or t.w <= 2:
                    col = mix(col, C['ear_out'], 0.18 + 0.12 * (t.j / max(1, t.h - 1)))
            else:
                col = shade(C['ear_out'], posterize(variation(p, 0.04)))
            ears_top = t.cube.origin[1] + t.cube.size[1]
            if p[1] > ears_top - 0.75 or face == 'up':
                t.detail = True
                col = mix(col, ROE_DARK['ear_tip'], 0.55)        # dark rims at the tip
            return col
        if part in ('antler', 'antler_tine'):
            t.detail = True
            top = t.cube.origin[1] + t.cube.size[1]
            col = shade(A['beam'], posterize(variation(p, 0.05)))
            if part == 'antler_tine' or p[1] > top - 1.0 or face == 'up':
                col = mix(col, A['tip'], 0.7)
            elif p[1] < t.cube.origin[1] + 1.0:
                col = shade(col, 0.88)                            # burr at the base
            return col
        if part in ('leg_front', 'leg_back'):
            h = (p[1] - 0) / t.cube.size[1]
            inner = (face == 'right' and p[0] > 0) or (face == 'left' and p[0] < 0)
            col = mix(C['leg'], C['back'], 0.25 * h)
            if inner:
                col = mix(col, C['belly'], 0.35)
            if part == 'leg_back' and face == 'back' and h > 0.6:
                col = mix(col, C['patch'], 0.25 if season == 'winter' else 0.1)
            col = shade(col, posterize(variation(p, 0.04)))
            if face == 'down' or p[1] < 1.0:
                t.detail = True
                return ROE_DARK['hoof']
            if p[1] < 2.0:
                t.detail = True
                col = mix(col, ROE_DARK['hoof'], 0.3)             # pastern
            return col
        if part == 'tail':
            t.detail = True
            return shade(C['patch'], posterize(variation(p, 0.02)) * (0.94 if face == 'down' else 1.0))
        raise ValueError(f'roe deer: no paint for part {part}')

    return shader


# ------------------------------------------------------------------ axis gauge

GAUGE_COLORS = {'base': (150, 150, 150), 'pitch': (220, 60, 50), 'yaw': (60, 190, 70), 'roll': (60, 90, 220),
                'chain': (230, 200, 40), 'chain_child': (240, 140, 30), 'anim': (210, 60, 210), 'left': (245, 245, 245),
                'front': (80, 80, 80)}


def gauge_shader(t):
    c = GAUGE_COLORS[t.part]
    light = {'up': 1.0, 'down': 0.55, 'front': 0.85, 'back': 0.75, 'left': 0.7, 'right': 0.65}[t.face]
    return shade(c, light)


# ------------------------------------------------------------------ spawn egg (16 x 16 item)


def spawn_egg(base, spots):
    """A 16 x 16 egg: an own ellipse (not traced from vanilla), shaded from the top left, with a darker rim and soft
    spots of the second color."""
    img = np.zeros((16, 16, 4), np.uint8)
    cx, cy, rx, ry = 7.5, 8.4, 5.6, 7.1
    rim = shade(base, 0.62)
    # spots in fixed places (round, 2-3 px), like the white flecks of a fawn
    spot_pts = [(5.6, 6.2), (9.6, 5.0), (8.2, 9.6), (4.9, 11.4), (10.6, 12.2)]
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            # egg: narrower at the top
            k = 1.0 - 0.18 * (cy - py) / ry if py < cy else 1.0
            d = math.hypot((px - cx) / (rx * k), (py - cy) / ry)
            if d > 1.0:
                continue
            light = 1.08 - 0.28 * ((px - cx) / rx + (py - cy) / ry) * 0.5 - 0.18 * d * d
            col = shade(base, posterize(light, 0.06))
            sd = min(math.hypot(px - sx, py - sy) for sx, sy in spot_pts)
            if sd < 1.25:
                col = mix(shade(spots, posterize(light, 0.06)), col, max(0.0, (sd - 0.75) / 0.5))
            if d > 0.86:
                col = mix(col, rim, 0.75)
            img[y, x] = (*[int(round(min(255, max(0, v)))) for v in col], 255)
    return img
