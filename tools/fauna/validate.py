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
step is reported, not limited.
Spawn egg: 16 x 16, transparent outside the egg.
"""
import numpy as np

from model import Bone, Cube, Model, face_rects
import paint
import uvpack

REQUIRED = {'roe_deer': ('misc.idle', 'move.walk', 'move.run', 'misc.eat', 'misc.alert', 'misc.rest')}
DARKEST = 30.0          # luminance 0-255 of the darkest allowed texel
SMOOTH_MEAN = 6.0       # mean luminance step between neighbours inside a face (roe deer 4.4-4.8)
SMOOTH_P95 = 24.0       # (roe deer 21-22; vanilla horse and pig coats about 28, whole textures)


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
