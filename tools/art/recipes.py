"""Species recipes of tools/art: one function per texture kind, parameters and palette per texture.

Each Texture says what it is (kind: bark, leaves or plant), whether the game tints it (tint: None, 'foliage', 'grass'
or a constant '#rrggbb' that V7 gives the needles through a color provider; tinted textures are grayscale), which
palette of palettes.json it uses, its seed, and the style bounds that lint.py checks on top of the common rules
(Texture.expect, see lint.py).

Bark descriptions (photos checked on Wikimedia Commons, CC0 and public domain, and species descriptions in
docs/research/06-lasy-polski-fitosocjologia-stl.md):
- Scots pine, lower trunk: gray-brown to red-brown bark in long plates separated by continuous, deep, dark red-brown
  fissures; the reddish inner bark shows in patches at the bottom of the fissures. The plate ends are slanted and
  shallower than the long fissures (a straight dark cross fissure on a thin trunk reads as a ring at every block).
- Scots pine, upper trunk ("mirror"): thin bark from red orange to ochre peeling in small papery flakes, some of them
  pale gray-orange, no deep furrows.
- Pedunculate oak: gray-brown, deep, closely spaced vertical furrows; ridges broken into platelets by cross cracks.
- Hornbeam: smooth, slate or steel gray, fluted like muscles, with lighter streaks along the trunk; darker than beech,
  much lighter than vanilla pale oak (round 1 review of V0).
- Small-leaved lime: gray-brown, a network of shallow longitudinal fissures with narrow, flat ridges.
- Beech: thin, smooth, light warm silver gray in large faint patches, with greenish algae, irregular pale lichen and
  occasional dark branch-scar 'moustaches'.

Scale: a block is 1 m, so 1 px is 6.25 cm. Trunks are thin (radius 1-7 px, decision D8), and a trunk side shows a
slice of the texture only a few pixels wide: structures are 2-4 px wide (bark plates, ridges, flakes).

Selection by measure: a bark recipe builds its texture for the seeds '<seed>', '<seed>#1', ... '<seed>#<tries - 1>' and
keeps the one with the least horizontal banding and the smallest rings (texlib.banding: row means over the full width
and over the crops of thin trunks; texlib.rings: single dark or light rows in those crops), among those that pass the
lint, so a stacked trunk shows no light and dark ring at every block. It is deterministic.
"""
import math
from dataclasses import dataclass, field

import numpy as np

import texlib as t
from texlib import N


@dataclass
class Texture:
    name: str
    kind: str                      # 'bark' | 'leaves' | 'plant'
    make: object                   # function(Texture, palettes) -> RGBA array
    seed: str
    palette: str = None            # key in palettes.json "palettes"
    tint: str = None               # None | 'foliage' | 'grass' | '#rrggbb' (constant color provider)
    variant_of: str = None         # base texture of a variant (stacked vertically in any order with it)
    expect: dict = field(default_factory=dict)
    mcmeta: dict = None
    params: dict = field(default_factory=dict)
    note: str = ''
    palette_use: str = None        # how the palette is used, when not for the colors (CREDITS.md)


# ---------------------------------------------------------------------------------------------------- helpers
def palette_colors(palettes, name, skip=0):
    return palettes['palettes'][name]['colors'][skip:]


def seeds(tex):
    tries = tex.params.get('tries', 1)
    return [tex.seed] + [f'{tex.seed}#{k}' for k in range(1, tries)]


def banding_score(img, caps=(5.0, 9.0)):
    full, centre = t.banding(img)
    return max(full / caps[0], centre / caps[1])


def bark_score(tex):
    """Score of a bark candidate: its banding (texlib.banding against the lint caps), plus a large penalty for every
    other lint bound of the recipe it breaks (isolated pixels, vertical grain, contrast), so the selection keeps a
    texture that passes the lint when one of the seeds gives it."""
    exp = tex.expect

    def score(img):
        sc = banding_score(img, (exp.get('rows', 5.0), exp.get('slices', 9.0)))
        ring = max(v / exp.get('rings', t.RING_CAPS)[r] for r, v in t.rings(img).items())
        sc = max(sc, ring) + (10 if ring > 1 else 0)
        l = t.luma(img[..., :3])
        if t.isolated_fraction(l) > 0.095:
            sc += 10
        if 'grain' in exp and t.grain(img) < exp['grain']:
            sc += 10
        if l.std() > exp.get('std', 28.0):
            sc += 10
        import lint                                    # every other lint rule of the texture (seams, colors, ...)
        sc += 10 * len(lint.lint_texture(tex.name, img, tex)[0])
        return sc
    return score


_BUILT = {}


def once(fn):
    """Memoizes fn(tex, palettes, ...) per texture and palettes object: a variant rebuilds its base texture (and a base
    may serve several variants), which is deterministic, so one build per base is enough. The cache keeps a reference
    to the palettes object, so its id is not reused while the entry lives; a new palettes object (selftest's second
    build) builds everything again."""
    def wrapper(tex, palettes, *args):
        key = (fn.__name__, tex.name, id(palettes))
        hit = _BUILT.get(key)
        if hit is None:
            hit = _BUILT[key] = (palettes, fn(tex, palettes, *args))
        return hit[1]
    wrapper.__name__ = fn.__name__
    wrapper.__doc__ = fn.__doc__
    return wrapper


def best_of(tex, build, score=None):
    """Builds the texture for every seed of seeds(tex) and keeps the lowest score (the first on a tie; default score:
    bark_score). build(seed) -> (img, state); returns (img, state) of the best."""
    score = score or bark_score(tex)
    best = None
    for s in seeds(tex):
        img, state = build(s)
        sc = score(img)
        if best is None or sc < best[0]:
            best = (sc, img, state)
    return best[1], best[2]


def roll(a, dy, dx):
    return t.roll(a, dy, dx)


def tones_at(colors, lums, sat, hue_shift=0.0, hue_drift=0.0, hue=None):
    """Tones of the palette's mean hue (texlib.ramp_hue) at the given lumas, in the given order (step V1b: the recipes
    set every tone's luma, so the steps between neighbouring tones stay small). sat is one saturation or one per tone;
    hue_drift (degrees) shifts the lightest tone by +half and the darkest by -half."""
    lo, hi = min(lums), max(lums)
    sats = sat if isinstance(sat, (list, tuple)) else [sat] * len(lums)
    out = []
    for lum, sa in zip(lums, sats):
        f = (lum - lo) / (hi - lo) if hi > lo else 0.5
        drift = (f - 0.5) * hue_drift
        if hue is not None:
            out += t.ramp_hue(None, 1, lum, lum, sat=sa, hue=hue + drift)
        else:
            out += t.ramp_hue(colors, 1, lum, lum, sat=sa, hue_shift=hue_shift + drift)
    return out


def fold_rare(idx, into, least=4):
    """Tones used by fewer than `least` pixels take the tone given in `into` (e.g. {5: 4}): a tone on 1-3 pixels is a
    lone speck that repeats on every block (lint rule)."""
    out = idx.copy()
    for k, v in into.items():
        n = int((out == k).sum())
        if 0 < n < least:
            out[out == k] = v
    return out


def groove_sides(groove, faces):
    """(left, right): the face pixels just left of a groove (its shaded wall) and just right of it (its lit wall)."""
    right = roll(groove, 0, 1) & faces
    left = roll(groove, 0, -1) & faces & ~right
    return left, right


# ---------------------------------------------------------------------------------------------------- plated barks
def gap_cols(cols, i, y):
    """Columns strictly between line i and the next line to the right (with wrap) in row y."""
    a = cols[i][y]
    b = cols[(i + 1) % len(cols)][y]
    w = (b - a) % N - 1
    return [(a + 1 + k) % N for k in range(max(w, 0))]


def cross_pixels(rng, cols, i, y, kind):
    """Pixels of a cross furrow in gap i starting at row y. kind: 'full' (across the whole gap, sometimes with a 1 px
    step), 'part' (a crack from a random side, 1 px short of the far side or across), 'diag' (a slanted fissure
    joining the two lines over 2-3 rows)."""
    g = gap_cols(cols, i, y)
    if not g:
        return []
    if kind == 'full':
        m = rng.integers(1, len(g) + 1) if len(g) > 2 and rng.random() < 0.5 else len(g)
        step = 1 if rng.random() < 0.5 else -1
        g2 = gap_cols(cols, i, (y + step) % N)
        out = [(y, x) for x in g[:m]]
        out += [((y + step) % N, x) for x in g[m:] if x in g2]
        return out
    if kind == 'part':
        if rng.random() < 0.35:
            return [(y, x) for x in g]
        ln = rng.integers(min(2, len(g)), max(len(g), 2))
        return [(y, x) for x in (g[:ln] if rng.random() < 0.5 else g[len(g) - ln:])]
    # 'diag': from just right of line i in row y down-right to just left of line i + 1
    h = rng.integers(2, 4)
    out = []
    for k, x in enumerate(g):
        out.append(((y + (k * h) // max(len(g), 1)) % N, x))
    return out


def place_rows(rng, start, stop, hmin, hmax, fixed):
    """Anchor rows in [start, stop) spaced hmin..hmax apart and at least hmin (cyclically) from every fixed row."""
    rows = []
    y = start + rng.integers(0, hmax - hmin + 1)
    while y < stop:
        if all(min((y - f) % N, (f - y) % N) >= hmin for f in fixed + rows):
            rows.append(y % N)
        y += rng.integers(hmin, hmax + 1)
    return rows


def plated(seed, p, base=None):
    """Bark of long plates or ridges between wavy vertical furrows, split by cross furrows.

    Returns a dict: lines (x per line and row), cols (pixel column per line and row), furrow (vertical furrows),
    cross (cross furrows), conns (cross furrows as dicts of rows and pixels), plate (labels), tone (plate tone 0..1 per
    pixel, nan in furrows), noise (a fine noise field for the faces).
    base: the structure of the base texture for a variant. The variant keeps the base's furrow lines in the top and
    bottom `band` rows (blended into its own in between), the cross furrows that lie wholly in those rows, and the
    tones of the plates that reach those rows, so base and variant continue into each other when stacked in any order.
    """
    rng = t.Rng(seed)
    band = p.get('band', 3)
    lines = t.furrow_lines(rng, p['lines'], p['amp'], p['wave'], p.get('jitter', 0.5))
    noise = 0.6 * t.value_noise(rng, 4, 8) + 0.4 * t.value_noise(rng, 8, 16)
    streak = t.value_noise(rng, 8, 2)                                   # vertical streaks on the plate faces
    if base is not None:
        w = t.edge_weight(band)
        lines = base['lines'] * (1 - w) + lines * w
        noise = t.edge_locked(base['noise'], noise, band)
        streak = t.edge_locked(base['streak'], streak, band)
    cols = [t.raster_line(row) for row in lines]
    furrow = np.zeros((N, N), bool)
    for c in cols:
        for y in range(N):
            furrow[y, c[y]] = True
    edge_rows = set(range(band)) | set(range(N - band, N))
    conns = []
    for i in range(len(cols)):
        kept = [c for c in (base['conns'] if base is not None else []) if c['gap'] == i and c['rows'] <= edge_rows]
        conns += kept
        fixed = [c['anchor'] for c in kept]
        if base is None:
            start = rng.integers(0, N)
            rows = place_rows(rng, start, start + N - p['plate'][0] + 1, p['plate'][0], p['plate'][1], fixed)
        else:
            rows = place_rows(rng, band + 1, N - band - 1, p['plate'][0], p['plate'][1], fixed)
            if not rows:                                   # a plate must end in the middle (no merged edge plates)
                rows = [N // 2 - 1 + rng.integers(0, 2)]
        for y in rows:
            px = [q for q in cross_pixels(rng, cols, i, y, p['cross']) if not furrow[q]]
            if px:
                conns.append(dict(gap=i, anchor=y, rows={q[0] for q in px}, px=px))
    cross = np.zeros((N, N), bool)
    for c in conns:
        for q in c['px']:
            cross[q] = True
    cross &= ~furrow
    plate = t.label4(~furrow & ~cross)
    tone = np.full((N, N), np.nan)
    for k in range(plate.max() + 1):
        m = plate == k
        v = None
        if base is not None:
            for y in [0, N - 1] + sorted(edge_rows - {0, N - 1}):
                hit = m[y] & ~np.isnan(base['tone'][y])
                if hit.any():
                    v = base['tone'][y][hit][0]
                    break
        tone[m] = rng.random() if v is None else v
    return dict(lines=lines, cols=cols, furrow=furrow, cross=cross, conns=conns, plate=plate, tone=tone, noise=noise,
                streak=streak)


def build_plated(tex, paint_fn, palettes):
    return build_plated_once(tex, palettes, paint_fn)


@once
def build_plated_once(tex, palettes, paint_fn):
    """best_of for a plated bark; a variant is built on the (selected) structure of its base texture and takes its edge
    rows exactly. With params['structure_of'] (step V1b, the pine transition bark) the texture is built on the structure
    of that texture the same way (the furrows continue into it across the block edge in both orders), but painted on
    its own, so it does not share the edge rows."""
    base_s = base_img = None
    base_name = tex.variant_of or tex.params.get('structure_of')
    if base_name:
        base_tex = TEXTURES_BY_NAME[base_name]
        base_img, base_s = build_plated(base_tex, PLATED_PAINT[base_tex.make], palettes)
        if not tex.variant_of:
            base_img = None

    def build(seed):
        s = plated(seed, tex.params, base_s)
        img = paint_fn(tex, s, palettes)
        if base_img is not None:                       # the shared edge rows exactly as in the base texture
            img[0], img[N - 1] = base_img[0], base_img[N - 1]
        return img, s
    return best_of(tex, build)


# ---------------------------------------------------------------------------------------------------- pine, lower
def paint_pine_lower(tex, s, palettes):
    """Scots pine, lower trunk. Tones: 0 fissure (red-brown), 1 rust (inner bark at the fissure bottom), 2 fissure wall
    (the shaded right edge of a plate, red-brown, so the fissures read 2 px wide), 3 and 4 the two gray-brown plate faces
    (plate tone mixed with vertical streaks), 5 the lit wall right of a fissure. The cross fissures (plate ends) take
    p['cross_tone'] (the wall tone: shallower than the long fissures).
    Step V1b (CP-A: "a bit too sharp"): the fissure is a mid-dark red-brown, not a near-black line, and both its walls
    are tones between it and the faces (p['lums']), so a fissure reads by its red hue and a soft 3 px groove; the steps
    between neighbouring pixels stay at about the vanilla logs' (texlib.local_contrast)."""
    p = tex.params
    rng = t.Rng(tex.seed + '/paint')
    groove = s['furrow'] | s['cross']
    faces = ~groove
    v = (1 - p['streak']) * np.nan_to_num(s['tone']) + p['streak'] * s['streak']
    idx = np.where(v >= np.quantile(v[faces], 1 - p['light_share']), 4, 3)
    idx = np.where(faces, idx, 0)
    idx = t.majority(idx, passes=1, keep=(0,))
    left, right = groove_sides(s['furrow'], faces)
    idx[left] = 2
    idx[right] = 5
    idx[s['cross']] = p.get('cross_tone', 0)
    # rust: 2-4 px clusters along a fissure (2-3 px of the fissure, sometimes with the shaded face pixel beside it)
    cols = s['cols']
    for _ in range(p['rust']):
        i = rng.integers(0, len(cols))
        y = rng.integers(2, N - 4)
        ln = rng.integers(2, 4)
        for dy in range(ln):
            idx[y + dy, cols[i][y + dy]] = 1
        if rng.random() < 0.5:
            x = (cols[i][y] - 1) % N
            if faces[y, x]:
                idx[y, x] = 1
    return t.paint(idx, pine_lower_colors(palettes, p))


def pine_lower_colors(palettes, p):
    """Tones 0-5 of the lower pine bark (paint_pine_lower): fissure, rust and wall in the red-brown of the "mirror"
    palette, faces and the lit wall in the gray-brown of the lower bark palette."""
    red = palette_colors(palettes, 'scots_pine_upper_bark')
    gray = palette_colors(palettes, 'scots_pine_bark')
    fissure_lum, rust_lum, wall_lum, face_a, face_b, lit_lum = p['lums']
    out = tones_at(red, [fissure_lum, rust_lum, wall_lum], [p['fissure_sat'], p['rust_sat'], p['wall_sat']],
                   hue_shift=p['red_shift'])
    return out + tones_at(gray, [face_a, face_b, lit_lum], p['sat'], hue_shift=p['hue_shift'], hue_drift=p['hue_drift'])


def bark_scots_pine_lower(tex, palettes):
    return build_plated(tex, paint_pine_lower, palettes)[0]


# ---------------------------------------------------------------------------------------------------- pine, transition
def paint_pine_transition(tex, s, palettes):
    """Scots pine, transition between the lower bark and the "mirror" (step V1b, choice V1-i): thinner gray-brown plates
    of the lower bark lying on the orange bark of the upper trunk. The fissures of the lower bark are orange strips
    here (the thin bark shows between the plates), and the plates whose tone is below p['orange_share'] have flaked
    off (orange). Built on the structure of the lower bark (params['structure_of']), so its furrows continue into the
    lower bark below and into itself. Tones: 0 fissure (deep red orange), 1 and 2 orange bark, 3 and 4 plate faces,
    5 pale flake."""
    p = tex.params
    rng = t.Rng(tex.seed + '/paint')
    groove = s['furrow'] | s['cross']
    faces = ~groove
    v = (1 - p['streak']) * np.nan_to_num(s['tone']) + p['streak'] * s['streak']
    idx = np.where(v >= np.quantile(v[faces], 1 - p['light_share']), 4, 3)
    tone = np.nan_to_num(s['tone'], nan=1.0)
    peeled = faces & (tone < p['orange_share'])
    idx = np.where(peeled, np.where(s['noise'] > 0.5, 2, 1), idx)
    idx = np.where(faces, idx, 0)
    idx = t.majority(idx, passes=1, keep=(0,))
    left, right = groove_sides(s['furrow'], faces)
    idx[left & ~peeled] = 1
    idx[s['cross']] = 1
    for _ in range(p['pale']):                       # small pale papery flakes on the orange bark, 2 px
        y, x = rng.integers(0, N), rng.integers(0, N)
        cells = [(y, x), (y, (x + 1) % N)]
        if all(idx[q] in (1, 2) for q in cells):
            for q in cells:
                idx[q] = 5
    idx = fold_rare(idx, {5: 2, 0: 1})
    red = palette_colors(palettes, 'scots_pine_upper_bark')
    gray = palette_colors(palettes, 'scots_pine_bark')
    fissure, orange_a, orange_b, face_a, face_b, pale = p['lums']
    out = tones_at(red, [fissure, orange_a, orange_b], p['orange_sat'], hue_shift=p['orange_shift'])
    out += tones_at(gray, [face_a, face_b], p['sat'], hue_shift=p['hue_shift'])
    out += tones_at(red, [pale], p['pale_sat'], hue_shift=p['orange_shift'] + 8)
    return t.paint(idx, out)


def bark_pine_transition(tex, palettes):
    return build_plated(tex, paint_pine_transition, palettes)[0]


# ---------------------------------------------------------------------------------------------------- oak, linden
def paint_furrowed(tex, s, palettes):
    """Oak and linden. Tones: 0 deep furrow, 1 shallow fissure and crack, 2 ridge shade (left of a furrow), 3-4 ridge
    faces, 5 lit edge (under a crack). Oak: every long furrow is deep (tone 0); linden: the long fissures are shallow
    (tone 1) with deep spots. Step V1b: the tones have their own lumas (p['lums']) with small steps, and the ridge
    right of a furrow takes the darker face tone (p['right_tone']) instead of a bright lit edge next to the darkest
    tone, so a furrow is a soft groove, not a hard dark line."""
    p = tex.params
    rng = t.Rng(tex.seed + '/paint')
    groove = s['furrow'] | s['cross']
    faces = ~groove
    v = 0.65 * np.nan_to_num(s['tone']) + 0.35 * s['noise']
    q = np.digitize(v, [np.quantile(v[faces], p['face_split'])])
    idx = np.where(faces, 3 + q, 0)
    idx = t.majority(idx, passes=1, keep=(0,))
    lit = roll(s['cross'], 1, 0) & faces                # the top edge of a ridge under a crack catches the light
    idx[lit] = np.minimum(idx[lit] + 1, 5)
    left, right = groove_sides(s['furrow'], faces)
    idx[left] = 2
    idx[right] = np.minimum(idx[right], p.get('right_tone', 3))
    idx[s['furrow']] = p['furrow_tone']
    idx[s['cross']] = 1
    idx = fold_rare(idx, {5: 4})                       # a lit edge on 1-3 pixels would repeat as a speck
    if p['deep'] and p['furrow_tone'] != 0:            # a few deep spots in the shallow fissures
        ys, xs = np.nonzero(s['furrow'])
        for k in range(p['deep']):
            j = rng.integers(0, len(ys))
            idx[ys[j], xs[j]] = 0
            idx[(ys[j] + 1) % N, xs[j]] = 0 if s['furrow'][(ys[j] + 1) % N, xs[j]] else idx[(ys[j] + 1) % N, xs[j]]
    c = tones_at(palette_colors(palettes, tex.palette), p['lums'], p['sat'], hue_shift=p.get('hue_shift', 0),
                 hue_drift=p.get('hue_drift', 0), hue=p.get('hue'))
    return t.paint(idx, c)


def bark_furrowed(tex, palettes):
    return build_plated(tex, paint_furrowed, palettes)[0]


# ---------------------------------------------------------------------------------------------------- pine, upper
def upper_cells(rng, p):
    """Flakes of the "mirror": tileable Worley cells stretched along x, each with a tone 1..3 (red orange, orange,
    ochre; p['tones'] are the shares of the flakes, dealt out in a random order)."""
    _, _, cell = t.worley(rng, p['flakes'], stretch_x=p['stretch_x'])
    n = p['flakes']
    counts = [int(round(share * n)) for share in p['tones']]
    counts[1] = n - counts[0] - counts[2]
    tones = [k + 1 for k, c in enumerate(counts) for _ in range(c)]
    order = sorted(range(n), key=lambda _: rng.random())        # flakes in random order take the tones in turn
    return cell, {order[i]: tones[i] for i in range(n)}


def paint_upper(rng, cell, tone, p):
    """Index map of the "mirror" from its flakes: 1-3 flake tones, under part of the lower edge of a flake a short dark
    seam (0; at most p['row_max'] seam pixels in a row), the curled edge above it lit (4), and small pale gray-orange
    papery flakes peeling off (5, 2-3 px)."""
    idx = np.vectorize(lambda c: tone[int(c)])(cell)
    bottom = cell != roll(cell, -1, 0)               # the lowest pixel row of a flake above another flake
    seam = np.zeros((N, N), bool)
    per_row = np.zeros(N, int)
    for k in sorted(tone, key=lambda _: rng.random()):
        ys, xs = np.nonzero(bottom & (cell == k))
        runs = []                                     # horizontal runs of the lower edge (same row, adjacent x)
        for y in sorted(set(ys.tolist())):
            row = sorted(xs[ys == y].tolist())
            start = 0
            for i in range(1, len(row) + 1):
                if i == len(row) or row[i] != row[i - 1] + 1:
                    runs.append((y, row[start:i]))
                    start = i
        runs = [r for r in runs if len(r[1]) >= 2]
        if not runs:
            continue
        y, run = runs[rng.integers(0, len(runs))]
        ln = min(len(run), rng.integers(*p['seam']))
        j = rng.integers(0, len(run) - ln + 1)
        px = [((y + 1) % N, x) for x in run[j:j + ln]]
        if per_row[(y + 1) % N] + len(px) > p['row_max'] or seam.sum() >= p['seam_px']:
            continue
        if any(seam[(q[0] + d) % N, q[1]] for q in px for d in (-1, 1)):
            continue                                  # no seam right above or below another (a dark 2 x 2 hole)
        per_row[(y + 1) % N] += len(px)
        for q in px:
            seam[q] = True                          # the seam lies on the top row of the flake below
    curl = roll(seam, -1, 0) & ~seam                 # the curled lower edge of the flake above
    curl &= rng.array((N, N)) < p['curl']            # lit on part of the seams only
    idx[curl] = 4
    idx[seam] = 0
    for _ in range(p['pale']):                       # pale papery flakes: 2-3 px
        y, x = rng.integers(0, N), rng.integers(0, N)
        cells = [(y, x), (y, (x + 1) % N)] + ([((y + 1) % N, x)] if rng.random() < 0.5 else [])
        if all(idx[q] in (1, 2, 3) for q in cells):
            for q in cells:
                idx[q] = 5
    return idx


def upper_colors(palettes, p):
    """Tones of the "mirror": 0 seam, 1-3 flakes (red orange, orange, ochre: close in brightness, apart in hue, so a
    flake does not read as a dark or light band across a thin trunk), 4 curled edge, 5 pale gray-orange flake."""
    pal = palette_colors(palettes, 'scots_pine_upper_bark')

    def tone(lum, sat, hue):
        return t.ramp_hue(pal, 1, lum, lum, sat=sat, hue_shift=p['hue_shift'] + hue)
    out = tone(p['seam_lum'], p['sat'], -4)
    for lum, hue in zip(p['flake_lum'], p['flake_hue']):
        out += tone(lum, p['sat'], hue)
    return out + tone(p['curl_lum'], p['sat'] - 0.1, p['flake_hue'][2]) + tone(p['pale_lum'], p['pale_sat'], p['pale_hue'])


def bark_scots_pine_upper(tex, palettes):
    """Scots pine "mirror": thin papery flakes lying on each other like roof tiles, in three tones from red orange to
    ochre (no tone on more than about 45% of the pixels), a short dark seam under part of the lower edge of a flake
    (not the whole edge, so no row of the texture turns dark), its curled edge lit, and a few small pale gray-orange
    flakes peeling off. A variant keeps the flakes of its base in the top and bottom `band` rows (its own flakes in
    between) and takes those rows of the base exactly, so the two stack in any order."""
    p = tex.params
    band = p.get('band', 3)
    base_cells = base_img = None
    if tex.variant_of:
        base_tex = TEXTURES_BY_NAME[tex.variant_of]
        base_img, base_cells = bark_scots_pine_upper_built(base_tex, palettes)

    def build(seed):
        rng = t.Rng(seed)
        cell, tone = upper_cells(rng, p)
        if base_cells is not None:
            bc, btone = base_cells
            edge = np.zeros(N, bool)
            edge[:band] = edge[N - band:] = True
            cell = np.where(edge[:, None], bc, cell + 1000)
            tone = {**btone, **{k + 1000: v for k, v in tone.items()}}
        idx = paint_upper(rng, cell, tone, p)
        img = t.paint(idx, upper_colors(palettes, p))
        if base_img is not None:
            img[:band], img[N - band:] = base_img[:band], base_img[N - band:]
        return img, (cell, tone)

    def score(img):
        l = t.luma(img[..., :3])
        _, counts = np.unique(l, return_counts=True)
        dominant = counts.max() / l.size
        return bark_score(tex)(img) + (0 if dominant <= p['dominant'] else 10 * dominant)
    return best_of(tex, build, score)


@once
def bark_scots_pine_upper_built(tex, palettes):
    """(image, (cells, tones)) of a "mirror" texture (the base of a variant)."""
    return bark_scots_pine_upper(tex, palettes)


# ---------------------------------------------------------------------------------------------------- hornbeam
def variant_base(tex, built_fn, palettes):
    """(image, state) of the base texture of a variant (built_fn returns them), or (None, None) for a base texture."""
    if not tex.variant_of:
        return None, None
    return built_fn(TEXTURES_BY_NAME[tex.variant_of], palettes)


def lock_edge_rows(idx, base_idx, band):
    """A variant takes the top and bottom `band` rows of its base's index map, so the two stack in any order."""
    if base_idx is not None:
        idx[:band], idx[N - band:] = base_idx[:band], base_idx[N - band:]
    return idx


def bark_hornbeam(tex, palettes):
    return bark_hornbeam_built(tex, palettes)[0]


@once
def bark_hornbeam_built(tex, palettes):
    """Hornbeam: two broad flutes ('muscles') of uneven width with wavy borders, lit on the left and shaded on the
    right, a faint groove between them, lighter streaks along the trunk and a few short fissures. Cool slate gray.
    A variant (step V1b) keeps the flute borders of its base at the top and bottom `band` rows (its own wobble in
    between), draws its streaks and fissures in the middle rows and takes the base's edge rows exactly."""
    p = tex.params
    band = p.get('band', 3)
    base_img, base = variant_base(tex, bark_hornbeam_built, palettes)

    def build(seed):
        rng = t.Rng(seed)
        b0 = rng.uniform(0, N)
        w0 = rng.uniform(*p['flute'])
        wob = [2 * p['wobble'] * (t.noise_1d(rng, 4) - 0.5) for _ in range(2)]
        if base is not None:
            b0, w0 = base['b0'], base['w0']
            w = t.edge_weight(band)
            wob = [bw * (1 - w) + ow * w for bw, ow in zip(base['wob'], wob)]
        u = np.zeros((N, N))
        for y in range(N):
            a = b0 + wob[0][y]
            b = b0 + w0 + wob[1][y]
            for x in range(N):
                xc = x + 0.5
                da = (xc - a) % N
                wa = (b - a) % N
                u[y, x] = da / wa if da < wa else 1 + (da - wa) / (N - wa)
        f = u % 1.0                                      # position across the own flute, 0 at its left border
        idx = np.full((N, N), 2)
        idx[f < p['lit']] = 3
        idx[f > 1 - p['shade']] = 1
        groove = (np.abs(f - 0.5) > 0.5 - 0.5 / 8) & (rng.array((N, N)) < p['groove'])
        idx[groove] = 1
        idx = t.majority(idx, passes=1)
        lo, hi = (0, N) if base is None else (band, N - band)
        for _ in range(p['streaks']):
            x, y0, ln = rng.integers(0, N), rng.integers(0, N), rng.integers(3, 8)
            if base is not None:
                ln = min(ln, hi - lo - 1)
                y0 = lo + rng.integers(0, hi - lo - ln + 1)
            if idx[y0, x] < 2:
                continue
            for dy in range(ln):
                idx[(y0 + dy) % N, x] = 4
        for _ in range(p['fissures']):
            x, y0, ln = rng.integers(0, N), rng.integers(0, N), rng.integers(2, 4)
            if base is not None:
                y0 = lo + rng.integers(0, hi - lo - ln + 1)
            for dy in range(ln):
                idx[(y0 + dy) % N, x] = 0
        idx = lock_edge_rows(idx, None if base is None else base['idx'], band)
        img = t.paint(idx, tones_at(None, p['lums'], p['sat'], hue=p['hue']))
        return img, dict(b0=b0, w0=w0, wob=wob, idx=idx)
    return best_of(tex, build)


# ---------------------------------------------------------------------------------------------------- beech
MOUSTACHE = [(0, 0), (0, 1), (0, -1), (1, -2), (1, -3), (2, -4), (0, 2), (1, 3), (1, 4), (2, 5)]


def bark_beech(tex, palettes):
    return bark_beech_built(tex, palettes)[0]


@once
def bark_beech_built(tex, palettes):
    """Beech: large faint patches of warm silver gray with a faint vertical streak, a greenish algae patch, irregular
    pale lichen and one dark branch scar ('moustache') with a light lip under it. A variant (step V1b) blends its
    patch and algae fields into the base's at the top and bottom `band` rows (texlib.edge_locked), puts its lichen and
    scars in the middle rows and takes the base's edge rows exactly."""
    p = tex.params
    band = p.get('band', 3)
    base_img, base = variant_base(tex, bark_beech_built, palettes)

    def build(seed):
        rng = t.Rng(seed)
        st = p.get('streak', 0.15)                                         # the faint vertical streak
        patches = (0.85 - st) / 0.85 * (0.55 * t.value_noise(rng, 2, 2) + 0.3 * t.value_noise(rng, 4, 4))
        patches = patches + st * t.value_noise(rng, 8, 1)
        if base is not None:
            patches = t.edge_locked(base['patches'], patches, band)
        idx = 1 + t.quantize(patches, 3, shares=[0.25, 0.5, 0.25])          # tones 1..3
        idx = t.majority(idx, passes=2)
        algae = t.value_noise(rng, 2, 2)
        if base is not None:
            algae = t.edge_locked(base['algae'], algae, band)
        idx[(algae > np.quantile(algae, 1 - p['algae'])) & (idx <= 2)] = 5
        lo, hi = (0, N) if base is None else (band + 1, N - band - 2)
        for _ in range(p['lichen']):                                        # irregular lichen blob, 5-7 px
            blob = [(lo + rng.integers(0, hi - lo), rng.integers(0, N))]
            for _ in range(rng.integers(4, 7)):
                y, x = blob[rng.integers(0, len(blob))]
                d = rng.integers(0, 4)
                q = ((y + (0, 0, 1, -1)[d]) % N, (x + (1, -1, 0, 0)[d]) % N)
                if q not in blob:
                    blob.append(q)
            for q in blob:
                idx[q] = 4
        for _ in range(p['scars']):
            y = rng.integers(2, N - 3) if base is None else rng.integers(band + 1, N - band - 3)
            x = rng.integers(0, N)
            for dy, dx in MOUSTACHE:
                idx[(y + dy) % N, (x + dx) % N] = 0
            for dy, dx in MOUSTACHE:
                yy, xx = (y + dy + 1) % N, (x + dx) % N
                if idx[yy, xx] != 0:
                    idx[yy, xx] = 3
        idx = lock_edge_rows(idx, None if base is None else base['idx'], band)
        silver = tones_at(palette_colors(palettes, 'beech_bark'), p['lums'], p['sat'], hue_shift=p['hue_shift'])
        green = t.ramp_hue(palette_colors(palettes, 'beech_bark'), 1, p['algae_lum'], p['algae_lum'],
                           sat=p['algae_sat'], hue_shift=p['algae_hue_shift'])
        return t.paint(idx, silver + green), dict(patches=patches, algae=algae, idx=idx)
    return best_of(tex, build)


# ---------------------------------------------------------------------------------------------------- foliage
BROADLEAF_SPRITES = [
    # 1 leaf, 2 lit edge (top-left), 3 shadow (bottom-right), 0 empty
    ["2101", "1111", "0113"],
    ["0210", "1111", "1013", "0010"],
    ["21", "11", "13"],
    ["021", "1113", "0130"],
    ["20", "11", "11", "03"],
    ["021", "113", "010"],
    ["0221", "2111", "0113"],
    ["210", "1113", "0011"],
    ["02", "21", "11", "13", "01"],
    ["2210", "1111", "0133"],
    ["021", "211", "113", "03"],
    ["0021", "2113", "130"],
]


def leaves_score(tex):
    """Score of a leaves candidate (V3 review round 1): the spread of the visible pixels over the columns and rows (the
    larger standard deviation of the counts, against vanilla oak's 1.0-1.4 of 16), plus a large penalty for a column or
    row below expect['min_line'] and for every lint error, so a tiled wall shows no gap that repeats at every block."""
    exp = tex.expect

    def score(img):
        cmin, rmin, cstd, rstd = t.line_cover(img[..., 3])
        sc = max(cstd, rstd)
        if min(cmin, rmin) < exp.get('min_line', 0):
            sc += 10
        import lint
        sc += 10 * len(lint.lint_texture(tex.name, img, tex)[0])
        return sc
    return score


def leaves_broadleaf(tex, palettes):
    """Broadleaf leaves for the foliage tint: small leaf clumps (BROADLEAF_SPRITES) stamped over the tile with wrap-around,
    so clumps cross the tile edges, each at the best of p['candidates'] random places (emptiest rows and columns), two base
    tones from a smooth noise field. The best of p['tries'] seeds by leaves_score."""
    p = tex.params

    def build(seed):
        rng = t.Rng(seed)
        tone = t.fbm(rng, 2, 4, 4)
        val = t.stamp(rng, BROADLEAF_SPRITES, p['opacity'], tone_field=tone, candidates=p.get('candidates', 1))
        alpha = np.where(val > 0, 255, 0).astype(np.uint8)
        idx = np.clip(val - 1, 0, 4)
        return t.paint(idx, t.gray_ramp(5, *p['gray']), alpha), None
    return best_of(tex, build, leaves_score(tex))[0]


def put(val, x, y, v):
    x, y = int(x) % N, int(y) % N
    val[y, x] = max(val[y, x], v)


def pine_twig(rng, val, p):
    """A 'bottle brush' twig tip of Scots pine: a short dark twig with pairs of 2-3 px needles fanning forward on
    both sides. Needles facing the top-left light are lighter, their tips catch the light."""
    x0, y0 = rng.uniform(0, N), rng.uniform(0, N)
    a = rng.uniform(-math.pi * 0.95, -math.pi * 0.05)          # twigs point mostly upward (screen y grows down)
    dx, dy = math.cos(a), math.sin(a)
    length = rng.integers(*p['twig'])
    for s in range(length + 1):
        x, y = x0 + dx * s, y0 + dy * s
        put(val, math.floor(x), math.floor(y), 1)               # twig: darkest tone
        if s == 0:
            continue
        for side in (-1, 1):
            if rng.random() > p['fill']:
                continue
            b = a + side * rng.uniform(0.6, 0.9)
            ndx, ndy = math.cos(b), math.sin(b)
            light = -(ndx + ndy) / math.sqrt(2)
            shade = 4 if light > 0.5 else (3 if light > -0.3 else 2)
            ln = rng.integers(*p['needle'])
            for k in range(1, ln + 1):
                put(val, math.floor(x + ndx * k), math.floor(y + ndy * k), shade + (1 if k == ln and shade < 4 else 0))


def leaves_needles(tex, palettes):
    """Scots pine needles: twig tips (pine_twig) until the target opacity. Step V1b (CP-A: "less blotchy needles"):
    each twig goes to the best of p['candidates'] random twigs (the one whose new pixels lie in the emptiest rows and
    columns, as texlib.stamp), the gray ramp is narrower (no near-black twig against the lightest needle tips), and the
    best of p['tries'] seeds by leaves_score, so the tile has no dense dark clumps and no empty holes that repeat."""
    p = tex.params

    def build(seed):
        rng = t.Rng(seed)
        val = np.zeros((N, N), int)
        tries = 0
        while (val > 0).mean() < p['opacity'] and tries < 500:
            tries += 1
            best = None
            for _ in range(p.get('candidates', 1)):
                cand = val.copy()
                pine_twig(rng, cand, p)
                filled = val > 0
                cols, rows = filled.sum(0), filled.sum(1)
                ys, xs = np.nonzero((cand > 0) & ~filled)
                gain = int(sum(2 * N - cols[x] - rows[y] for y, x in zip(ys, xs)))
                if best is None or gain > best[0]:
                    best = (gain, cand)
            val = best[1]
        alpha = np.where(val > 0, 255, 0).astype(np.uint8)
        idx = np.clip(val - 1, 0, 4)
        return t.paint(idx, t.gray_ramp(5, *p['gray']), alpha), None
    return best_of(tex, build, leaves_score(tex))[0]


BILBERRY_LEAVES = [["21", "13"], ["21"], ["2", "1"], ["21", "11"], ["12"]]


def plant_bilberry(tex, palettes):
    """Bilberry (Vaccinium myrtillus): a low, dense dwarf shrub, 15-50 cm. Cross-model texture for the grass tint
    (grayscale): 7-9 green, angular stems rising from the base (mid tone, lighter than the shadowed leaves), small oval
    alternate leaves along the whole height (lit top-left edge, shaded bottom-right), the plant centered with a 1 px
    margin at both sides (the edge of a cross quad cuts straight). The top lies at rows top..top+1 (7-8 px, 44-50 cm).
    Berries and autumn colors are separate states (V12)."""
    p = tex.params
    rng = t.Rng(tex.seed)
    val = np.zeros((N, N), int)
    lo, hi = 1, N - 2

    def leaf(x, y, side):
        spr = BILBERRY_LEAVES[rng.integers(0, len(BILBERRY_LEAVES))]
        for dy, row in enumerate(spr):
            for dx, ch in enumerate(row):
                if ch == '0':
                    continue
                xx = x + (dx if side > 0 else -dx)
                yy = y - len(spr) + 1 + dy
                if lo <= xx <= hi and 0 <= yy < N:
                    val[yy, xx] = max(val[yy, xx], {'1': 4, '2': 5, '3': 3}[ch])   # V1b: shade = stem tone

    stems = p['stems']
    for k, x in enumerate(np.linspace(4, N - 5, stems).round().astype(int)):
        top = p['top'] + rng.integers(0, 2) + (1 if k in (0, stems - 1) else 0)
        lean = -1 if x < N / 2 - 1 else (1 if x > N / 2 else 0)
        y, side = N - 1, rng.choice([-1, 1])
        while y >= top:
            val[y, x] = max(val[y, x], 2 if y >= N - 2 else 3)   # green stem, mid tone, shaded at the base
            if y < N - 1 and rng.random() < p['leafiness']:
                leaf(x + side, y, side)
                side = -side                              # alternate leaves
            y -= 1
            if rng.random() < p['zigzag']:                # angular stems, spreading outward
                x = int(np.clip(x + (lean if rng.random() < 0.7 else -lean or rng.choice([-1, 1])), lo, hi))
        leaf(x, y + 1, rng.choice([-1, 1]))               # young leaves at the tip
    alpha = np.where(val > 0, 255, 0).astype(np.uint8)
    idx = np.clip(val - 1, 0, 4)
    return t.paint(idx, t.gray_ramp(5, *p['gray']), alpha)


# ---------------------------------------------------------------------------------------------------- the list
LEAVES_MCMETA = {'texture': {'mipmap_strategy': 'dark_cutout'}}          # as vanilla *_leaves.png.mcmeta

PINE_LOWER = dict(lines=3, amp=1.6, wave=2, jitter=0.6, plate=(9, 15), cross='diag', cross_tone=2, band=3, light_share=0.55,
                  rust=3, streak=0.45, lums=(78, 92, 88, 102, 113, 106), sat=0.3, hue_drift=14, hue_shift=-3,
                  fissure_sat=0.5, wall_sat=0.4, rust_sat=0.55, red_shift=6, tries=48)
PINE_EXPECT = {'luma': (92, 106), 'dark': 0.25, 'grain': 2.0, 'std': 30}     # deep, continuous fissures
OAK = dict(lines=4, amp=1.0, wave=4, jitter=0.4, plate=(3, 7), cross='part', band=3, furrow_tone=0, deep=0,
           face_split=0.5, lums=(66, 78, 84, 92, 102, 108), sat=0.42, hue_shift=-12, tries=48)
OAK_EXPECT = {'luma': (80, 100), 'dark': 0.05, 'grain': 2.0, 'near': ('oak_log', 10)}
LINDEN = dict(lines=5, amp=1.4, wave=2, jitter=0.5, plate=(4, 8), cross='diag', band=3, furrow_tone=1, deep=3,
              face_split=0.45, lums=(72, 84, 94, 106, 120, 126), sat=0.26, hue_shift=-6, tries=48)
LINDEN_EXPECT = {'luma': (92, 110), 'dark': 0.05, 'grain': 2.0}
# Shared substitute bark of the broad-crowned archetype for ash and Norway maple (V2, docs/04 §1.1): paler and grayer
# than linden, a net of narrow ridges between shallow fissures (old ash bark is reticulate, Norway maple finely ridged).
BROADLEAF_BARK = dict(lines=5, amp=1.2, wave=3, jitter=0.5, plate=(6, 12), cross='part', band=3, furrow_tone=1, deep=2,
                      face_split=0.5, lums=(108, 120, 128, 136, 148, 154), sat=0.07, hue_shift=-10, tries=48)
BROADLEAF_BARK_EXPECT = {'luma': (122, 142), 'dark': 0.05, 'grain': 2.0, 'distinct': ('linden_bark', 15)}

# Scots pine "mirror" (round 1 review of V2: 3 flake tones instead of one on 80% of the pixels, pale flakes, a variant).
PINE_UPPER = dict(flakes=22, stretch_x=1.4, tones=(0.3, 0.4, 0.3), seam=(2, 4), row_max=2, seam_px=12, curl=0.6,
                  pale=5, band=3, seam_lum=90, flake_lum=(104, 116, 128), flake_hue=(-5, 0, 8), curl_lum=138,
                  pale_lum=142, pale_sat=0.3, pale_hue=8, sat=0.58, hue_shift=6.5, dominant=0.45, tries=96)
PINE_UPPER_EXPECT = {'luma': (105, 135), 'dark': 0.02, 'hue': (18, 28)}
# Scots pine transition bark (step V1b, V1-i): on the structure of the lower bark, plates a third peeled to orange.
PINE_TRANSITION = dict(PINE_LOWER, structure_of='scots_pine_bark', light_share=0.5, orange_share=0.25, pale=4,
                       lums=(94, 108, 118, 100, 110, 138), orange_sat=0.55, orange_shift=6.5, sat=0.3, hue_shift=-3,
                       pale_sat=0.3, tries=48)
PINE_TRANSITION_EXPECT = {'luma': (100, 118), 'dark': 0.02, 'grain': 1.5, 'hue': (16, 32)}
HORNBEAM = dict(flute=(7, 9.5), wobble=1.0, lit=0.3, shade=0.3, groove=0.6, streaks=5, fissures=2, band=3,
                lums=(98, 106, 117, 128, 136), sat=0.07, hue=80, tries=24)
HORNBEAM_EXPECT = {'luma': (115, 132), 'dark': 0.02, 'grain': 2.0, 'distinct': ('beech_bark', 10)}
BEECH = dict(lichen=2, scars=1, algae=0.06, lums=(146, 156, 165, 174, 186), sat=0.11, hue_shift=-14, algae_lum=150,
             algae_sat=0.12, algae_hue_shift=14, streak=0.5, band=3, tries=48)
BEECH_EXPECT = {'luma': (150, 172), 'dark': 0.0, 'grain': 1.2, 'std': 18}


def upper_texture(tex, palettes):
    return bark_scots_pine_upper_built(tex, palettes)[0]


TEXTURES = [
    Texture('scots_pine_bark', 'bark', bark_scots_pine_lower, 'scots_pine_bark/5', 'scots_pine_bark',
            params=PINE_LOWER, expect=PINE_EXPECT,
            note='Scots pine, lower trunk: long gray-brown plates, continuous red-brown fissures in soft mid tones, rust '
                 'patches'),
    Texture('scots_pine_bark_2', 'bark', bark_scots_pine_lower, 'scots_pine_bark_2/1', 'scots_pine_bark',
            variant_of='scots_pine_bark', params=PINE_LOWER, expect=PINE_EXPECT,
            note='second variant of the lower pine bark, same edge rows'),
    Texture('scots_pine_upper_bark', 'bark', upper_texture, 'scots_pine_upper_bark/3', 'scots_pine_upper_bark',
            params=PINE_UPPER, expect=PINE_UPPER_EXPECT,
            note='Scots pine "mirror": thin red-orange to ochre bark in papery flakes, pale peeling flakes'),
    Texture('scots_pine_upper_bark_2', 'bark', upper_texture, 'scots_pine_upper_bark_2/1', 'scots_pine_upper_bark',
            variant_of='scots_pine_upper_bark', params=PINE_UPPER, expect=PINE_UPPER_EXPECT,
            note='second variant of the "mirror", same edge rows'),
    Texture('scots_pine_transition_bark', 'bark', bark_pine_transition, 'scots_pine_transition_bark/1',
            'scots_pine_bark', params=PINE_TRANSITION, expect=PINE_TRANSITION_EXPECT,
            palette_use='plate tones',
            note='Scots pine, transition between the lower bark and the "mirror": thinner gray-brown plates on the '
                 'orange bark, orange strips in the fissures (step V1b)'),
    Texture('scots_pine_transition_bark_2', 'bark', bark_pine_transition, 'scots_pine_transition_bark_2/1',
            'scots_pine_bark', variant_of='scots_pine_transition_bark', params=PINE_TRANSITION,
            expect=PINE_TRANSITION_EXPECT, palette_use='plate tones',
            note='second variant of the pine transition bark, same edge rows'),
    Texture('oak_bark', 'bark', bark_furrowed, 'oak_bark/2', 'oak_bark', params=OAK, expect=OAK_EXPECT,
            note='pedunculate oak: deep vertical furrows, ridges broken into platelets'),
    Texture('oak_bark_2', 'bark', bark_furrowed, 'oak_bark_2/1', 'oak_bark', variant_of='oak_bark',
            params=dict(OAK, tries=200),
            expect=OAK_EXPECT, note='second variant of the oak bark, same edge rows'),
    Texture('hornbeam_bark', 'bark', bark_hornbeam, 'hornbeam_bark/2', 'hornbeam_bark', params=HORNBEAM,
            expect=HORNBEAM_EXPECT,
            palette_use='reference photo; the recipe sets a cool gray, the palette hue is a white-balance cast',
            note='hornbeam: smooth slate gray, two uneven flutes, lighter streaks along the trunk'),
    Texture('hornbeam_bark_2', 'bark', bark_hornbeam, 'hornbeam_bark_2/1', 'hornbeam_bark', variant_of='hornbeam_bark',
            params=HORNBEAM, expect=HORNBEAM_EXPECT, palette_use='reference photo, as hornbeam_bark',
            note='second variant of the hornbeam bark, same edge rows (step V1b)'),
    Texture('linden_bark', 'bark', bark_furrowed, 'linden_bark/3', 'linden_bark', params=LINDEN,
            expect=LINDEN_EXPECT, note='small-leaved lime: gray-brown, net of shallow longitudinal fissures'),
    Texture('linden_bark_2', 'bark', bark_furrowed, 'linden_bark_2/1', 'linden_bark', variant_of='linden_bark',
            params=LINDEN, expect=LINDEN_EXPECT, note='second variant of the linden bark, same edge rows'),
    Texture('linden_bark_3', 'bark', bark_furrowed, 'linden_bark_3/9', 'linden_bark', variant_of='linden_bark',
            params=LINDEN, expect=LINDEN_EXPECT, note='third variant of the linden bark, same edge rows'),
    Texture('broadleaf_bark', 'bark', bark_furrowed, 'broadleaf_bark/1', 'linden_bark', params=BROADLEAF_BARK,
            expect=BROADLEAF_BARK_EXPECT,
            palette_use='hue of the linden bark photo, made paler and grayer by the recipe',
            note='shared substitute bark of ash and Norway maple (step V2): pale gray, net of narrow ridges'),
    Texture('beech_bark', 'bark', bark_beech, 'beech_bark/2', 'beech_bark', params=BEECH, expect=BEECH_EXPECT,
            note='beech: smooth warm silver gray in faint patches, algae, lichen, a branch scar'),
    Texture('beech_bark_2', 'bark', bark_beech, 'beech_bark_2/1', 'beech_bark', variant_of='beech_bark', params=BEECH,
            expect=BEECH_EXPECT, note='second variant of the beech bark, same edge rows (step V1b)'),
    Texture('scots_pine_leaves', 'leaves', leaves_needles, 'scots_pine_leaves/1', 'scots_pine_needles',
            tint='#7f9c86', mcmeta=LEAVES_MCMETA,
            params=dict(opacity=0.6, twig=(2, 5), fill=0.7, needle=(3, 5), gray=(104, 184), candidates=6, tries=32),
            expect={'min_line': 6},
            note='Scots pine twig tips with needles, grayscale for a constant needle color (V7); the proposed color '
                 '#7f9c86 is a gray green (hue 135), lighter and grayer than vanilla spruce; the palette photo gave a '
                 'yellow olive (#8e9963, hue 72) in warm light'),
    Texture('broadleaf_leaves', 'leaves', leaves_broadleaf, 'broadleaf_leaves/1', None, tint='foliage',
            mcmeta=LEAVES_MCMETA, params=dict(opacity=0.63, gray=(100, 204), candidates=6, tries=32),
            expect={'min_line': 7},
            note='broadleaf leaves for the foliage tint and the species color curves (B6, V7); clumps spread over '
                 'the rows and columns (every one at least 7 of 16 pixels, V3 review round 1)'),
    Texture('bilberry', 'plant', plant_bilberry, 'bilberry/2', 'bilberry', tint='grass',
            params=dict(stems=8, top=9, leafiness=0.75, zigzag=0.3, gray=(92, 180)),
            note='bilberry dwarf shrub, cross model, grayscale for the grass tint'),
]

TEXTURES_BY_NAME = {tx.name: tx for tx in TEXTURES}
# The painter of each plated bark (a variant or a texture on another's structure rebuilds its base with the base's own
# painter, so it gets the structure the base selected).
PLATED_PAINT = {bark_scots_pine_lower: paint_pine_lower, bark_furrowed: paint_furrowed,
                bark_pine_transition: paint_pine_transition}


def make(tex, palettes):
    img = tex.make(tex, palettes)
    if tex.tint:
        img = t.to_gray_tint(img)
    return img
