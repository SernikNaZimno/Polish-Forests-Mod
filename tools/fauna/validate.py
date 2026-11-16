"""Validator of tools/fauna: model, animation and texture rules that GeckoLib does not check (or checks only in game).

Model (.geo.json): format 1.12.0, one geometry named geometry.<id>, texture size a power of two, unique bone names,
every parent present and no cycles, numbers where numbers belong; every cube with whole-pixel sizes and a UV rectangle
inside the texture, and no texel used by two faces (box UV).
Animations (.animation.json): format 1.8.0, a positive length, loop true / false / "hold_on_last_frame", keyframe times
inside the animation, three numbers per keyframe, only bones of the model; the required animations of a species.
Textures: the size of the model description, every texel of every face opaque and every other texel transparent
(nothing painted outside the UV), no texel darker than DARKEST (no pure black, CP-A: soft textures), and the smoothness
of the coat: mean and 95th percentile of the luminance step between neighbouring texels of a face (SMOOTH_MEAN, SMOOTH_P95),
leaving out the texels the painter marks as details (eyes, nose, lip, ear rims, hooves, antlers, tail); the largest
step is reported, not limited. The coat must not run in rows of one tone (review of V8: a striped blanket): the mean
step along texture rows is at least ANISOTROPY_MIN of the mean step down columns, and on the large faces (at least
LARGE_FACE texels, 4 wide and 3 high) at most SINGLE_TONE_ROWS of the rows have a single tone.
Shape (rest pose): no two faces of different cubes lie in one plane facing the same way with a shared area (they flicker,
decision V8-c), and no ear goes through an antler.
Poses (every animation, sampled at its keyframes and between them): no vertex under the ground (GROUND_TOLERANCE); a hoof
on the ground (within CONTACT px; lying: the trunk or a folded leg) in every frame, except the flight of a gallop (FLIGHT:
the share of frames in contact it needs); no leg through another leg (except the folded legs of a lying animal, FOLDED,
which lie over each other under the trunk); in the middle of misc.eat (grazing) the nose in front of the eyes and the ear and
antler tips above them.
Spawn egg: 16 x 16, transparent outside the egg.
"""
import itertools

import numpy as np

from model import Bone, Cube, Model, bone_matrices, face_corners, face_rects, pivots_and_locators, NORMALS
import animations
import paint
import uvpack

REQUIRED = {'roe_deer': ('misc.idle', 'move.walk', 'move.run', 'misc.eat', 'misc.alert', 'misc.rest')}
DARKEST = 30.0          # luminance 0-255 of the darkest allowed texel
SMOOTH_MEAN = 6.0       # mean luminance step between neighbours inside a face (roe deer 4.4-4.8)
SMOOTH_P95 = 24.0       # (roe deer 21-22; vanilla horse and pig coats about 28, whole textures)
ANISOTROPY_MIN = 0.5    # mean step along rows / down columns (vanilla horse, donkey, pig, camel about 0.8-1.1)
LARGE_FACE = 12
SINGLE_TONE_ROWS = 0.25
GROUND_TOLERANCE = 0.05     # px a vertex may dip under the ground between keyframes
CONTACT = 0.1               # px: a hoof this close to the ground carries the animal
FLIGHT = {'move.run': 0.3}  # animations with a flight: the share of frames that must touch the ground
FOLDED = ('misc.rest',)     # lying: the one-piece legs fold under the trunk over each other (hidden), not checked


def _vec(v, n=3):
    return isinstance(v, list) and len(v) == n and all(isinstance(x, (int, float)) for x in v)


def model_from_geo(geo):
    """A Model (with UV) from a parsed .geo.json, or raises ValueError with the problems."""
    errs = []
    if geo.get('format_version') != '1.12.0':
        errs.append(f'format_version {geo.get("format_version")!r}, expected "1.12.0"')
    geos = geo.get('minecraft:geometry') or []
    if len(geos) != 1:
        raise ValueError(errs + [f'{len(geos)} geometries, expected 1'])
    g = geos[0]
    d = g.get('description', {})
    ident = d.get('identifier', '')
    if not ident.startswith('geometry.'):
        errs.append(f'identifier {ident!r} does not start with "geometry."')
    tw, th = d.get('texture_width'), d.get('texture_height')
    for n, v in (('texture_width', tw), ('texture_height', th)):
        if not isinstance(v, int) or v < 16 or v & (v - 1):
            errs.append(f'{n} {v!r} is not a power of two >= 16')
    bones, names, locator_names = [], set(), set()
    for jb in g.get('bones', []):
        name = jb.get('name')
        if not name or name in names:
            errs.append(f'bone name {name!r} missing or repeated')
            continue
        names.add(name)
        if not _vec(jb.get('pivot')):
            errs.append(f'{name}: pivot is not three numbers')
            continue
        if 'rotation' in jb and not _vec(jb['rotation']):
            errs.append(f'{name}: rotation is not three numbers')
        cubes = []
        for k, jc in enumerate(jb.get('cubes', [])):
            if not (_vec(jc.get('origin')) and _vec(jc.get('size')) and _vec(jc.get('uv'), 2)):
                errs.append(f'{name} cube {k}: origin, size or uv malformed (box UV expected)')
                continue
            if any(s < 0 or s != int(s) for s in jc['size']):
                errs.append(f'{name} cube {k}: size {jc["size"]} is not whole non-negative pixels')
            if any(u != int(u) for u in jc['uv']):
                errs.append(f'{name} cube {k}: uv {jc["uv"]} is not whole texels')
            cubes.append(Cube(tuple(jc['origin']), tuple(jc['size']), f'{name}#{k}', tuple(jc['uv']), jc.get('inflate', 0.0)))
        locs = {}
        for ln, lp in (jb.get('locators') or {}).items():
            if not _vec(lp):
                errs.append(f'{name}: locator {ln} is not three numbers')
            elif ln in locator_names:
                errs.append(f'{name}: locator name {ln} repeated (GeckoLib looks locators up by name)')
            else:
                locator_names.add(ln)
                locs[ln] = tuple(lp)
        bones.append(Bone(name, tuple(jb['pivot']), jb.get('parent'), tuple(jb.get('rotation', (0, 0, 0))), cubes, locs))
    by = {b.name: b for b in bones}
    for b in bones:
        seen, p = {b.name}, b.parent
        while p is not None:
            if p not in by:
                errs.append(f'{b.name}: parent {p!r} does not exist')
                break
            if p in seen:
                errs.append(f'{b.name}: parent cycle through {p!r}')
                break
            seen.add(p)
            p = by[p].parent
    if errs:
        raise ValueError(errs)
    return Model(ident[len('geometry.'):], bones, (tw, th))


def check_model(model):
    """Problems of the UV layout of a model."""
    return uvpack.occupancy(model)[1]


def check_animations(anims, model, species_id=None):
    errs = []
    if anims.get('format_version') != '1.8.0':
        errs.append(f'animation format_version {anims.get("format_version")!r}, expected "1.8.0"')
    names = {b.name for b in model.bones}
    all_anims = anims.get('animations', {})
    for req in REQUIRED.get(species_id or model.id, ()):
        if req not in all_anims:
            errs.append(f'animation {req} is missing')
    for an, a in all_anims.items():
        length = a.get('animation_length')
        if not isinstance(length, (int, float)) or length <= 0:
            errs.append(f'{an}: animation_length {length!r}')
            continue
        if a.get('loop') not in (True, False, 'hold_on_last_frame'):
            errs.append(f'{an}: loop {a.get("loop")!r}')
        for bn, tracks in a.get('bones', {}).items():
            if bn not in names:
                errs.append(f'{an}: bone {bn} is not in the model')
            for tn, track in tracks.items():
                if tn not in ('rotation', 'position', 'scale'):
                    errs.append(f'{an}/{bn}: unknown channel {tn}')
                    continue
                for t, v in track.items():
                    try:
                        tt = float(t)
                    except ValueError:
                        errs.append(f'{an}/{bn}/{tn}: time {t!r}')
                        continue
                    if tt < -1e-9 or tt > length + 1e-9:
                        errs.append(f'{an}/{bn}/{tn}: keyframe {t} outside 0..{length}')
                    if not _vec(v):
                        errs.append(f'{an}/{bn}/{tn} at {t}: value is not three numbers')
    return errs


def texture_report(model, img, details=None):
    """(problems, (mean step, p95 step, largest step, darkest luminance)) of a texture of a model."""
    errs = []
    tw, th = model.texture_size
    if img.shape[:2] != (th, tw):
        return [f'texture {img.shape[1]} x {img.shape[0]}, model expects {tw} x {th}'], None
    occ, _ = uvpack.occupancy(model)
    alpha = img[..., 3]
    if (alpha[occ > 0] != 255).any():
        errs.append(f'{int((alpha[occ > 0] != 255).sum())} texels of faces are not opaque')
    if (alpha[occ == 0] != 0).any():
        errs.append(f'{int((alpha[occ == 0] != 0).sum())} texels outside every face are painted')
    lum = img[..., :3].astype(float) @ np.array([0.2126, 0.7152, 0.0722])
    darkest = float(lum[occ > 0].min()) if (occ > 0).any() else 255.0
    if darkest < DARKEST:
        errs.append(f'darkest texel luminance {darkest:.0f} < {DARKEST:.0f} (no pure black)')
    mean, p95, top = paint.smoothness(model, img, details)
    if mean > SMOOTH_MEAN:
        errs.append(f'mean neighbour step {mean:.1f} > {SMOOTH_MEAN} (texture too sharp, CP-A)')
    if p95 > SMOOTH_P95:
        errs.append(f'95th percentile neighbour step {p95:.1f} > {SMOOTH_P95} (texture too sharp, CP-A)')
    return errs, (mean, p95, top, darkest)


def coat_report(model, img, details=None):
    """(problems, (anisotropy, share of single-tone rows)) of a texture: steps along rows against steps down columns
    over all faces (without detail texels), and the rows of one tone on the large faces."""
    lum = img[..., :3].astype(float) @ np.array([0.2126, 0.7152, 0.0722])
    keep = ~details if details is not None else np.ones(lum.shape, bool)
    across, down, rows, single = [], [], 0, 0
    for c in model.cubes():
        for face, (u, v, w, h) in face_rects(c).items():
            f, k = lum[v:v + h, u:u + w], keep[v:v + h, u:u + w]
            if w > 1:
                across.extend(np.abs(np.diff(f, axis=1))[k[:, 1:] & k[:, :-1]].ravel())
            if h > 1:
                down.extend(np.abs(np.diff(f, axis=0))[k[1:, :] & k[:-1, :]].ravel())
            if w >= 4 and h >= 3 and w * h >= LARGE_FACE:
                for j in range(h):
                    row = [tuple(img[v + j, u + i, :3]) for i in range(w) if k[j, i]]
                    if len(row) >= 4:
                        rows += 1
                        single += len(set(row)) == 1
    ratio = float(np.mean(across)) / max(1e-9, float(np.mean(down))) if across and down else 1.0
    share = single / rows if rows else 0.0
    errs = []
    if ratio < ANISOTROPY_MIN:
        errs.append(f'coat steps along rows {ratio:.2f} of those down columns < {ANISOTROPY_MIN} (rows of one tone, '
                    f'a striped coat)')
    if share > SINGLE_TONE_ROWS:
        errs.append(f'{share:.0%} of the rows of the large faces have a single tone (> {SINGLE_TONE_ROWS:.0%})')
    return errs, (ratio, share)


# ------------------------------------------------------------------ shape and poses


def _boxes(model, pose, bones):
    """(bone, centre, axes 3x3 (columns), half sizes) of the posed cubes of the given bones."""
    ms = bone_matrices(model, pose)
    out = []
    for b in model.bones:
        if b.name not in bones:
            continue
        m = ms[b.name]
        for c in b.cubes:
            o, sz = np.array(c.origin, float), np.array(c.size, float)
            centre = (m @ np.array([*(o + sz / 2), 1.0]))[:3]
            out.append((b.name, centre, m[:3, :3], sz / 2))
    return out


def boxes_intersect(a, b, eps=1e-3):
    """True when two oriented boxes overlap by more than eps (separating axis test; touching boxes do not count)."""
    _, ca, ra, ha = a
    _, cb, rb, hb = b
    axes = [ra[:, i] for i in range(3)] + [rb[:, i] for i in range(3)]
    axes += [np.cross(ra[:, i], rb[:, j]) for i in range(3) for j in range(3)]
    d = cb - ca
    for ax in axes:
        n = np.linalg.norm(ax)
        if n < 1e-9:
            continue
        ax = ax / n
        pa = sum(ha[i] * abs(ra[:, i] @ ax) for i in range(3))
        pb = sum(hb[i] * abs(rb[:, i] @ ax) for i in range(3))
        if abs(d @ ax) >= pa + pb - eps:
            return False
    return True


def _area(pts):
    return 0.5 * abs(sum(pts[i][0] * pts[i - 1][1] - pts[i - 1][0] * pts[i][1] for i in range(len(pts))))


def _ccw(pts):
    signed = sum(pts[i - 1][0] * pts[i][1] - pts[i][0] * pts[i - 1][1] for i in range(len(pts)))
    return pts if signed > 0 else pts[::-1]


def _clip(subject, clip):
    """Sutherland-Hodgman: the part of a convex polygon inside another convex polygon (2D, both counter-clockwise)."""
    out = subject
    for i in range(len(clip)):
        a, b = clip[i - 1], clip[i]
        inp, out = out, []

        def side(p):
            return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])
        for k in range(len(inp)):
            p, q = inp[k - 1], inp[k]
            sp, sq = side(p), side(q)
            if sq >= 0:
                if sp < 0:
                    out.append(_cross_point(p, q, sp, sq))
                out.append(q)
            elif sp >= 0:
                out.append(_cross_point(p, q, sp, sq))
        if not out:
            return []
    return out


def _cross_point(p, q, sp, sq):
    t = sp / (sp - sq)
    return (p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1]))


def coplanar_faces(model, pose=None, tol=1e-4, min_area=1e-4):
    """Pairs of faces of different cubes that lie in one plane, face the same way and share an area (in the pose): they
    flicker in the game (decision V8-c)."""
    ms = bone_matrices(model, pose)
    faces = []
    for b in model.bones:
        for k, c in enumerate(b.cubes):
            for face, corners in face_corners(c).items():
                pts = np.array([(ms[b.name] @ np.array([*p, 1.0]))[:3] for p, _ in corners])
                n = ms[b.name][:3, :3] @ np.array(NORMALS[face], float)
                faces.append((f'{b.name}#{k}', face, pts, n / np.linalg.norm(n)))
    found = []
    for (ca, fa, pa, na), (cb, fb, pb, nb) in itertools.combinations(faces, 2):
        if ca == cb or na @ nb < 1 - 1e-6 or abs((pb[0] - pa[0]) @ na) > tol:
            continue
        e1 = (pa[1] - pa[0]) / np.linalg.norm(pa[1] - pa[0])
        e2 = np.cross(na, e1)

        def flat(pts):
            return [(float((p - pa[0]) @ e1), float((p - pa[0]) @ e2)) for p in pts]
        shared = _clip(_ccw(flat(pb)), _ccw(flat(pa)))
        if len(shared) >= 3 and _area(shared) > min_area:
            found.append(f'{ca} {fa} and {cb} {fb}')
    return found


def check_shape(model):
    errs = [f'faces in one plane (they flicker): {p}' for p in coplanar_faces(model)]
    ears = [b.name for b in model.bones if b.name.startswith('ear_')]
    antlers = [b.name for b in model.bones if b.name.startswith('antler_')]
    for a in _boxes(model, None, ears):
        for b in _boxes(model, None, antlers):
            if boxes_intersect(a, b):
                errs.append(f'{a[0]} goes through {b[0]} at rest')
    return errs


def _frames(anim, per_key=2):
    """Times to sample an animation: every keyframe of every track and per_key - 1 points between neighbours."""
    times = sorted({float(t) for tracks in anim['bones'].values() for tr in tracks.values() for t in tr})
    out = []
    for a, b in zip(times, times[1:]):
        out.extend(a + (b - a) * k / per_key for k in range(per_key))
    return out + times[-1:]


def check_poses(anims, model):
    """Ground contact, legs and the grazing head of every animation (see the module doc)."""
    errs = []
    legs = [b.name for b in model.bones if b.name.startswith('leg_')]
    if not legs:
        return errs
    for name, anim in sorted(anims.get('animations', {}).items()):
        frames = _frames(anim)
        contact, low_worst, high_worst, crossing = 0, None, None, None
        for t in frames:
            pose = animations.pose_at(anim, t)
            low = animations.lowest(model, pose)
            if low_worst is None or low < low_worst[1]:
                low_worst = (t, low)
            if high_worst is None or low > high_worst[1]:
                high_worst = (t, low)
            contact += low <= CONTACT
            if crossing is None and name not in FOLDED:
                for a, b in itertools.combinations(_boxes(model, pose, legs), 2):
                    if boxes_intersect(a, b, eps=0.02):
                        crossing = f'{name} at {t:.3f} s: {a[0]} goes through {b[0]}'
                        break
        if low_worst[1] < -GROUND_TOLERANCE:
            errs.append(f'{name} at {low_worst[0]:.3f} s: a vertex {-low_worst[1]:.2f} px under the ground')
        if name in FLIGHT:
            if contact < FLIGHT[name] * len(frames):
                errs.append(f'{name}: on the ground in {contact} of {len(frames)} frames (< {FLIGHT[name]:.0%})')
        elif high_worst[1] > CONTACT:
            errs.append(f'{name} at {high_worst[0]:.3f} s: the animal floats {high_worst[1]:.2f} px above the ground')
        if crossing:
            errs.append(crossing)
    eat = anims.get('animations', {}).get('misc.eat')
    if eat is not None:
        pts = pivots_and_locators(model, animations.pose_at(eat, eat['animation_length'] / 2))
        eyes = [pts[k] for k in ('locator:eye_left', 'locator:eye_right') if k in pts]
        if eyes and 'locator:nose' in pts:
            nose = pts['locator:nose']
            if any(nose[2] >= e[2] for e in eyes):
                errs.append(f'misc.eat: the nose (z {nose[2]:.2f}) is not in front of the eyes (z {eyes[0][2]:.2f}): the '
                            f'head is tucked under')
            top_eye = max(e[1] for e in eyes)
            for k, v in sorted(pts.items()):
                tip = k.startswith('locator:ear_') or k.startswith('locator:antler_')
                if tip and k.endswith('_tip') and v[1] <= top_eye:
                    errs.append(f'misc.eat: {k[8:]} ({v[1]:.2f} px) is not above the eyes ({top_eye:.2f} px)')
    return errs


def check_spawn_egg(img):
    errs = []
    if img.shape[:2] != (16, 16):
        errs.append(f'spawn egg {img.shape[1]} x {img.shape[0]}, expected 16 x 16')
    else:
        a = img[..., 3]
        if not ((a == 0) | (a == 255)).all():
            errs.append('spawn egg has semi-transparent texels')
        share = float((a == 255).mean())
        if not 0.3 <= share <= 0.7:
            errs.append(f'spawn egg covers {share:.0%} of the item, expected 30-70%')
        if a[0].any() or a[:, 0].any() or a[:, -1].any():
            errs.append('spawn egg touches the top, left or right edge of the item')
    return errs


def check_expected(expected, model):
    """The game test file must name every bone pivot and locator of the model."""
    errs = []
    entry = expected.get('models', {}).get(model.id)
    if entry is None:
        return [f'fauna_expected.json has no entry for {model.id}']
    want = {f'bone:{b.name}' for b in model.bones} | {f'locator:{ln}' for b in model.bones for ln in b.locators}
    missing = want - set(entry.get('rest', {}))
    if missing:
        errs.append(f'fauna_expected.json: {model.id} lacks {sorted(missing)[:5]}')
    return errs


def face_count(model):
    return sum(len(face_rects(c)) for c in model.cubes())
