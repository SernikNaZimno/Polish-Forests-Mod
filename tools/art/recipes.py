"""Species recipes of tools/art: one function per texture kind, parameters and palette per texture.

Each Texture says what it is (kind: bark, leaves or plant), whether the game tints it (tint: None, 'foliage', 'grass'
or a constant '#rrggbb' that V7 gives the needles through a color provider; tinted textures are grayscale), which
palette of palettes.json it uses, its seed, and the style bounds that lint.py checks on top of the common rules
(Texture.expect, see lint.py).

Bark descriptions (photos checked on Wikimedia Commons, CC0 and public domain, and species descriptions in
docs/research/06-lasy-polski-fitosocjologia-stl.md):
- Scots pine, lower trunk: gray-brown bark in long plates separated by continuous, deep, dark gray-brown fissures; the
  reddish inner bark shows in a few spots at the bottom of the fissures. The plate ends are slanted and shallower than
  the long fissures (a straight dark cross fissure on a thin trunk reads as a ring at every block). Seen from a
  distance a cool gray-brown, clearly apart from the orange "mirror" (the two-tone trunk of the Scots pine).
- Scots pine, upper trunk ("mirror"): thin bark from red orange to ochre peeling in small papery flakes, some of them
  pale buff, no deep furrows.
- Scots pine, transition zone (two blocks): the plates of the lower bark flaking off, the orange bark showing in the
  fissures first, then under the flaked plates.
- Pedunculate oak: gray-brown, deep, closely spaced vertical furrows; ridges broken into platelets by cross cracks.
- Hornbeam: smooth, slate or steel gray, fluted like muscles, with lighter streaks along the trunk; darker than beech,
  much lighter than vanilla pale oak (round 1 review of V0).
- Small-leaved lime: dark gray with a faint warm cast (not the brown of the oak), a network of shallow longitudinal
  fissures with narrow, flat ridges.
- Ash and Norway maple (one shared substitute bark): pale gray with a net of narrow ridges between fissures.
- Beech: thin, smooth, light silver gray in large faint patches, with greenish algae, irregular pale lichen and
  occasional dark branch-scar 'moustaches' (on about every third block).
The roughness (local contrast) follows the barks: pine and oak > linden and the ash/maple bark > hornbeam and beech
(V1b review round 1; lint rule 'rougher').

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

import ground
import texlib as t
import wood
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


def banding_score(img, caps=(5.0, 9.0), detrend=False):
    full, centre = t.banding(img, detrend=detrend)
    return max(full / caps[0], centre / caps[1])


def bark_score(tex):
    """Score of a bark candidate: its banding (texlib.banding against the lint caps), plus a large penalty for every
    other lint bound of the recipe it breaks (isolated pixels, vertical grain, contrast), so the selection keeps a
    texture that passes the lint when one of the seeds gives it."""
    exp = tex.expect

    def score(img):
        sc = banding_score(img, (exp.get('rows', 5.0), exp.get('slices', 9.0)), 'below' in exp or 'above' in exp)
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
        if p.get('variant_offset'):                    # the variant's own furrows sit between the base's (a new rhythm)
            lines = lines - lines[0].mean() + base['lines'][0].mean() + p['variant_offset'] * N / len(lines)
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
    rows exactly."""
    base_s = base_img = None
    if tex.variant_of:
        base_tex = TEXTURES_BY_NAME[tex.variant_of]
        base_img, base_s = build_plated(base_tex, PLATED_PAINT[base_tex.make], palettes)

    def build(seed):
        s = plated(seed, tex.params, base_s)
        img = paint_fn(tex, s, palettes)
        if base_img is not None:                       # the shared edge rows exactly as in the base texture
            img[0], img[N - 1] = base_img[0], base_img[N - 1]
        return img, s
    return best_of(tex, build)


# ---------------------------------------------------------------------------------------------------- pine, lower
def paint_pine_lower(tex, s, palettes):
    """Scots pine, lower trunk. Tones: 0 fissure, 1 rust (inner bark at the fissure bottom), 2 fissure wall (the shaded
    right edge of a plate, so the fissures read 2 px wide), 3 and 4 the two gray-brown plate faces (plate tone mixed
    with vertical streaks), 5 the lit wall right of a fissure. The cross fissures (plate ends) take p['cross_tone'] (the
    wall tone: shallower than the long fissures).
    Step V1b (CP-A: "a bit too sharp"): the fissure is not a near-black line, and both its walls are tones between it
    and the faces (p['lums']), a soft 3 px groove. V1b review round 1: the groove core is darker again (62, a dark
    gray-brown, no longer the red-brown that made the lower trunk read reddish), so the bark sits between the vanilla
    median and upper quartile of local contrast (texlib.local_contrast) instead of at the minimum."""
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
    """Tones 0-5 of the lower pine bark (paint_pine_lower). V1b review round 1: the fissure and its shaded wall are a
    dark gray-brown of the lower bark palette (a red-brown fissure made the whole lower trunk read reddish from a
    distance, close to the "mirror"); only the rust spots take the red-brown of the "mirror" palette."""
    red = palette_colors(palettes, 'scots_pine_upper_bark')
    gray = palette_colors(palettes, 'scots_pine_bark')
    fissure_lum, rust_lum, wall_lum, face_a, face_b, lit_lum = p['lums']
    fissure, wall = tones_at(gray, [fissure_lum, wall_lum], [p['fissure_sat'], p['wall_sat']],
                             hue_shift=p['hue_shift'] + p['fissure_shift'])
    rust = tones_at(red, [rust_lum], p['rust_sat'], hue_shift=p['red_shift'])
    return [fissure] + rust + [wall] + tones_at(gray, [face_a, face_b, lit_lum], p['sat'], hue_shift=p['hue_shift'],
                                                 hue_drift=p['hue_drift'])


def bark_scots_pine_lower(tex, palettes):
    return build_plated(tex, paint_pine_lower, palettes)[0]


# ---------------------------------------------------------------------------------------------------- pine, transition
def bark_pine_transition(tex, palettes):
    return pine_transition_built(tex, palettes)[0]


@once
def pine_transition_built(tex, palettes):
    """Scots pine, transition zone between the lower bark and the "mirror" (step V1b, choice V1-i; V1b review round 1):
    two blocks, the lower stage (p['stage'] 0) over the lower bark and the upper stage (1) under the "mirror". The two
    stages are the halves of one zone 32 px high in which the gray-brown plates of the lower bark flake off and the
    orange "mirror" shows through: every pixel is the pixel of the lower bark (p['lower']) or of the "mirror"
    (p['upper']) at the same place, the "mirror" where the flaking field f is below a share that rises linearly from 0
    in the bottom rows of the lower stage to 1 in the top rows of the upper stage (p['z0'], p['z1']: rows of the zone
    from its bottom). f is the tone of the lower bark's plate (a plate flakes off from its top down) with a little
    noise, lower in the fissures and their walls (p['fissure_lead']: the thin orange bark shows in the fissures
    first), as ranks 0..1 within each row, so the share of "mirror" pixels in every row follows the share. Both source
    barks tile vertically, so the bottom row of the lower stage continues into the lower bark below it, the top row of
    the upper stage into the "mirror" above it, and the two stages into each other as the source barks do: no straight
    seam at any of the three block edges (lint rules 'below' and 'above'). The upper base stage takes the field of the
    lower one (p['field_of']; both on the same lower bark), so it is one field across the edge between them.
    Colors (at most 9): both stages fold the same tones of the source barks into others (p['lower_map'],
    p['upper_map']), so a pixel keeps its color across every block edge of the zone; tones on 1-3 pixels then take the
    nearest used tone. A variant uses the variants of the source barks, its own field in the middle rows
    (texlib.edge_locked, the best of p['tries'] seeds) and the base's top and bottom rows exactly."""
    p = tex.params
    band = p.get('band', 3)
    lower_tex, upper_tex = TEXTURES_BY_NAME[p['lower']], TEXTURES_BY_NAME[p['upper']]
    l_img, l_s = build_plated(lower_tex, paint_pine_lower, palettes)
    m_img = bark_scots_pine_upper_built(upper_tex, palettes)[0]
    base_img, base = variant_base(tex, pine_transition_built, palettes)
    l_cols = pine_lower_colors(palettes, lower_tex.params)
    m_cols = upper_colors(palettes, upper_tex.params)
    l_idx, m_idx = color_index(l_img, l_cols), color_index(m_img, m_cols)
    l_idx = np.vectorize(lambda k: p['lower_map'].get(int(k), int(k)))(l_idx)
    m_idx = np.vectorize(lambda k: p['upper_map'].get(int(k), int(k)))(m_idx)
    colors = list(l_cols) + list(m_cols)
    z = p['stage'] * N + (N - 1 - np.arange(N)) + 0.5  # row of the zone counted from its bottom
    share = np.clip((z - p['z0']) / (p['z1'] - p['z0']), 0.0, 1.0)
    groove = l_s['furrow'] | l_s['cross']
    groove |= roll(l_s['furrow'], 0, 1) | roll(l_s['furrow'], 0, -1)  # with its walls: a fissure flakes as a whole

    shared = None
    if base is None and 'field_of' in p:               # the upper base stage takes the lower one's field
        shared = pine_transition_built(TEXTURES_BY_NAME[p['field_of']], palettes)[1]['f']

    def build(seed):
        rng = t.Rng(seed)
        plate = np.where(groove, 0.5 - p['fissure_lead'], np.nan_to_num(l_s['tone'], nan=0.5))
        f = (1 - p['noise']) * plate + p['noise'] * (0.5 * t.value_noise(rng, 4, 4) + 0.5 * t.value_noise(rng, 8, 2))
        for c in l_s['cols']:                          # a fissure and its two walls flake together
            for y in range(N):
                f[y, [(c[y] - 1) % N, (c[y] + 1) % N]] = f[y, c[y]]
        f = np.stack([[((row < v).sum() + 0.5 * (row == v).sum()) / N for v in row] for row in f])  # ranks in a row
        if shared is not None:
            f = shared
        if base is not None:
            f = t.edge_locked(base['f'], f, band)
        use_m = t.majority((f < share[:, None]).astype(int), passes=1) == 1   # no lone pixel of either bark
        idx = np.where(use_m, 6 + m_idx, l_idx)
        idx = fold_rare_nearest(idx, colors)
        img = t.paint(idx, colors)
        if base_img is not None:                       # the shared edge rows exactly as in the base texture
            img[0], img[N - 1] = base_img[0], base_img[N - 1]
        return img, dict(f=f)
    return best_of(tex, build)


def color_index(img, colors):
    """Index map of an image painted with `colors` (the first matching tone where two are equal)."""
    idx = np.zeros((N, N), int)
    for k in range(len(colors) - 1, -1, -1):
        idx[np.all(img[..., :3] == np.array(colors[k]), axis=2)] = k
    return idx


def fold_rare_nearest(idx, colors, least=4):
    """Tones used by 1 to least - 1 pixels take the nearest (CIE76) tone used by at least `least` pixels."""
    out = idx.copy()
    counts = np.bincount(out.ravel(), minlength=len(colors))
    common = [k for k in range(len(colors)) if counts[k] >= least]
    for k in range(len(colors)):
        if 0 < counts[k] < least:
            out[out == k] = min(common, key=lambda c: t.delta_e(colors[k], colors[c]))
    return out


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


def largest_region(mask):
    """Pixels of the largest 4-connected region of a mask (on the torus)."""
    lab = t.label4(mask)
    return int(np.bincount(lab[lab >= 0]).max()) if (lab >= 0).any() else 0


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

    dark_flake = np.array(upper_colors(palettes, p)[1])

    def score(img):
        l = t.luma(img[..., :3])
        _, counts = np.unique(l, return_counts=True)
        dominant = counts.max() / l.size
        blob = largest_region(np.all(img[..., :3] == dark_flake, axis=2))
        return (bark_score(tex)(img) + (0 if dominant <= p['dominant'] else 10 * dominant)
                + (0 if blob <= p['blob'] else 10 + blob / p['blob']))
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
    """A variant takes the top and bottom `band` rows of its base's index map, so the two stack in any order (V1b
    review round 1: hornbeam and beech lock only the outermost row, p['lock'] = 1; their fields are already blended
    into the base's in the `band` rows, so the rows next to it continue it)."""
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
    between, p['variant_wobble'] times stronger and moved by p['variant_shift'] px), draws its streaks and fissures inside the locked rows and takes the
    base's outermost p['lock'] rows exactly (V1b review round 1: 1 row instead of 3, the variant differed from the base
    on only 16% of the pixels)."""
    p = tex.params
    band = p.get('band', 3)
    lock = p.get('lock', band)
    base_img, base = variant_base(tex, bark_hornbeam_built, palettes)

    def build(seed):
        rng = t.Rng(seed)
        b0 = rng.uniform(0, N)
        w0 = rng.uniform(*p['flute'])
        wob = [2 * p['wobble'] * (t.noise_1d(rng, 4) - 0.5) for _ in range(2)]
        if base is not None:
            wob = [p.get('variant_wobble', 1.0) * v + p.get('variant_shift', 0.0) for v in wob]
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
        lo, hi = (0, N) if base is None else (lock, N - lock)
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
        idx = lock_edge_rows(idx, None if base is None else base['idx'], lock)
        img = t.paint(idx, tones_at(None, p['lums'], p['sat'], hue=p['hue']))
        return img, dict(b0=b0, w0=w0, wob=wob, idx=idx)
    return best_of(tex, build)


# ---------------------------------------------------------------------------------------------------- beech
MOUSTACHE = [(0, 0), (0, 1), (0, -1), (1, -2), (1, -3), (2, -4), (0, 2), (1, 3), (1, 4), (2, 5)]


def bark_beech(tex, palettes):
    return bark_beech_built(tex, palettes)[0]


@once
def bark_beech_built(tex, palettes):
    """Beech: large faint patches of silver gray with a faint vertical streak, a greenish algae patch, irregular pale
    lichen and p['scars'] dark branch scars ('moustache') with a light lip under each. A variant (step V1b) blends its
    patch and algae fields into the base's at the top and bottom `band` rows (texlib.edge_locked), puts its lichen and
    scars inside the locked rows and takes the base's outermost p['lock'] rows exactly. V1b review round 1: the
    variants have no scar, so a column shows a moustache on about every third block, not on every block."""
    p = tex.params
    band = p.get('band', 3)
    lock = p.get('lock', band)
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
        lo, hi = (0, N) if base is None else (lock + 1, N - lock - 2)
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
            y = rng.integers(2, N - 3) if base is None else rng.integers(lock + 1, N - lock - 3)
            x = rng.integers(0, N)
            for dy, dx in MOUSTACHE:
                idx[(y + dy) % N, (x + dx) % N] = 0
            for dy, dx in MOUSTACHE:
                yy, xx = (y + dy + 1) % N, (x + dx) % N
                if idx[yy, xx] != 0:
                    idx[yy, xx] = 3
        idx = lock_edge_rows(idx, None if base is None else base['idx'], lock)
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


def pine_twig(rng, val, p, twigs=None):
    """A 'bottle brush' twig tip of Scots pine: a short dark twig with pairs of 2-3 px needles fanning forward on
    both sides. Needles facing the top-left light are lighter, their tips catch the light. twigs: a boolean map that
    collects the twig pixels (leaves_needles draws them over the needles at the end)."""
    x0, y0 = rng.uniform(0, N), rng.uniform(0, N)
    a = rng.uniform(-math.pi * 0.95, -math.pi * 0.05)          # twigs point mostly upward (screen y grows down)
    dx, dy = math.cos(a), math.sin(a)
    length = rng.integers(*p['twig'])
    for s in range(length + 1):
        x, y = x0 + dx * s, y0 + dy * s
        put(val, math.floor(x), math.floor(y), 1)               # twig: darkest tone
        if twigs is not None and s < length:                    # the tip itself stays a needle tuft
            twigs[int(math.floor(y)) % N, int(math.floor(x)) % N] = True
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
    best of p['tries'] seeds by leaves_score, so the tile has no dense dark clumps and no empty holes that repeat.
    V1b review round 1: with p['twig_axes'] the twig axes are drawn over the needles in a dark mid gray (p['grays'][0];
    the smoothing had hidden them and the needles read as an even mosaic)."""
    p = tex.params

    def build(seed):
        rng = t.Rng(seed)
        val = np.zeros((N, N), int)
        twigs = np.zeros((N, N), bool)
        tries = 0
        while (val > 0).mean() < p['opacity'] and tries < 500:
            tries += 1
            best = None
            for _ in range(p.get('candidates', 1)):
                cand = val.copy()
                tw = np.zeros((N, N), bool)
                pine_twig(rng, cand, p, tw)
                filled = val > 0
                cols, rows = filled.sum(0), filled.sum(1)
                ys, xs = np.nonzero((cand > 0) & ~filled)
                gain = int(sum(2 * N - cols[x] - rows[y] for y, x in zip(ys, xs)))
                if best is None or gain > best[0]:
                    best = (gain, cand, tw)
            val = best[1]
            twigs |= best[2]
        if p.get('twig_axes'):                          # V1b review round 1: the twig axes show through the needles
            val[twigs] = 1
        alpha = np.where(val > 0, 255, 0).astype(np.uint8)
        idx = np.clip(val - 1, 0, 4)
        grays = [(g,) * 3 for g in p['grays']] if 'grays' in p else t.gray_ramp(5, *p['gray'])
        return t.paint(idx, grays, alpha), None
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

PINE_LOWER = dict(lines=3, amp=1.6, wave=2, jitter=0.6, plate=(7, 12), cross='diag', cross_tone=2, band=3, light_share=0.55,
                  rust=3, streak=0.45, lums=(62, 84, 80, 102, 119, 90), sat=0.3, hue_drift=14, hue_shift=-3,
                  fissure_sat=0.22, wall_sat=0.26, fissure_shift=0, rust_sat=0.5, red_shift=6, tries=48)
# V1b review round 1: soft but deep fissures (no near-black pixels, luma std at most 22; V1 had 25% dark pixels and a
# std of 28-29), rougher than linden, clearly apart from the "mirror" (the two-tone trunk of the Scots pine).
PINE_EXPECT = {'luma': (86, 102), 'dark': 0.02, 'grain': 2.0, 'std': 22, 'rougher': (('linden_bark', None),),
               'distinct': (('scots_pine_upper_bark', 28),)}
OAK = dict(lines=4, amp=1.0, wave=4, jitter=0.6, plate=(3, 6), cross='full', band=3, furrow_tone=0, deep=0,
           face_split=0.5, lums=(60, 78, 82, 97, 106, 108), right_tone=2, sat=0.42, hue_shift=-12, tries=48)
OAK_EXPECT = {'luma': (78, 100), 'dark': 0.05, 'grain': 2.0, 'near': ('oak_log', 10), 'rougher': (('linden_bark', None),)}
LINDEN = dict(lines=5, amp=1.4, wave=2, jitter=0.5, plate=(4, 8), cross='diag', band=3, furrow_tone=1, deep=3,
              face_split=0.45, lums=(58, 70, 78, 88, 98, 104), sat=0.07, hue=45, tries=48)
LINDEN_EXPECT = {'luma': (76, 92), 'dark': 0.05, 'grain': 2.0, 'distinct': (('oak_bark', 12), ('scots_pine_bark', 10)),
                 'rougher': (('hornbeam_bark', None), ('beech_bark', None))}
# Shared substitute bark of the broad-crowned archetype for ash and Norway maple (V2, docs/04 §1.1): a pale warm gray
# (linden is a dark gray), a net of narrow ridges between fissures joined by diagonal cracks (old ash bark is
# reticulate, Norway maple finely ridged; V1b review round 1: rougher than the smooth hornbeam, not a gray of its kind).
BROADLEAF_BARK = dict(lines=5, amp=1.4, wave=2, jitter=0.5, plate=(3, 6), cross='diag', band=3, furrow_tone=0, deep=0,
                      face_split=0.5, lums=(122, 130, 133, 144, 152, 153), right_tone=3, sat=0.12, hue_shift=-14,
                      tries=48)
BROADLEAF_BARK_EXPECT = {'luma': (122, 142), 'dark': 0.05, 'grain': 2.0,
                         'distinct': (('linden_bark', 15), ('hornbeam_bark', 7)), 'rougher': (('hornbeam_bark', 3.0),)}

# Scots pine "mirror" (round 1 review of V2: 3 flake tones instead of one on 80% of the pixels, pale flakes, a variant).
PINE_UPPER = dict(flakes=22, stretch_x=1.4, tones=(0.3, 0.4, 0.3), seam=(2, 4), row_max=2, seam_px=12, curl=0.6,
                  pale=5, band=3, seam_lum=96, flake_lum=(110, 117, 124), flake_hue=(-5, 0, 8), curl_lum=134,
                  pale_lum=140, pale_sat=0.42, pale_hue=6, sat=0.62, hue_shift=8.5, dominant=0.45, blob=18, tries=96)
PINE_UPPER_EXPECT = {'luma': (105, 135), 'dark': 0.02, 'hue': (18, 28)}
# Scots pine transition zone (step V1b, V1-i; V1b review round 1): two stages, each a mix of the lower bark and the
# "mirror" (pine_transition_built). lower_map / upper_map: tones of the source barks folded into others to stay at 9
# colors (lower bark: rust -> wall; "mirror": seam -> red-orange flake, curled edge -> ochre flake).
PINE_TRANSITION = dict(stage=0, lower='scots_pine_bark', upper='scots_pine_upper_bark', z0=2.0, z1=30.0, noise=0.7,
                       fissure_lead=0.6, lower_map={1: 2}, upper_map={0: 1, 4: 3}, band=3, tries=24)
PINE_TRANSITION_UPPER = dict(PINE_TRANSITION, stage=1, field_of='scots_pine_transition_bark', tries=1)
PINE_TRANSITION_EXPECT = {'luma': (90, 112), 'dark': 0.02, 'hue': (22, 32),
                          'below': ('scots_pine_bark', 'scots_pine_bark_2'),
                          'above': ('scots_pine_transition_upper_bark', 'scots_pine_transition_upper_bark_2')}
PINE_TRANSITION_UPPER_EXPECT = {'luma': (100, 122), 'dark': 0.02, 'hue': (20, 30),
                                'above': ('scots_pine_upper_bark', 'scots_pine_upper_bark_2')}
HORNBEAM = dict(flute=(7, 9.5), wobble=1.0, lit=0.3, shade=0.3, groove=0.6, streaks=5, fissures=2, band=3,
                lums=(98, 106, 117, 128, 136), sat=0.07, hue=80, tries=24)
HORNBEAM_EXPECT = {'luma': (115, 132), 'dark': 0.02, 'grain': 2.0, 'distinct': ('beech_bark', 10)}
BEECH = dict(lichen=2, scars=1, algae=0.06, lums=(146, 156, 165, 174, 186), sat=0.09, hue_shift=2, algae_lum=150,
             algae_sat=0.13, algae_hue_shift=28, streak=0.5, band=3, tries=48)
BEECH_VARIANT = dict(BEECH, scars=0, lichen=3, lock=1)
BEECH_EXPECT = {'luma': (150, 172), 'dark': 0.0, 'grain': 1.2, 'std': 18}
# Step I1 (roots): the bark of the underground roots, one for every species (tree_root). Roots in the soil have a thin,
# smooth brown bark with fine shallow fissures, warmer and smoother than the trunks (CP-A: smooth textures): the
# furrowed recipe with shallow fissures only and small luma steps.
ROOT = dict(lines=5, amp=1.2, wave=3, jitter=0.5, plate=(4, 8), cross='diag', band=3, furrow_tone=1, deep=0,
            face_split=0.5, lums=(68, 79, 86, 93, 99, 103), right_tone=3, sat=0.36, hue=22, tries=48)
ROOT_EXPECT = {'luma': (80, 100), 'dark': 0.02, 'grain': 1.5, 'distinct': (('oak_bark', 6), ('linden_bark', 8))}


# Step V7: LITE pine wood (tones set by the recipe from the wood description in tools/art/wood.py) and saplings.
PINE_PLANKS = dict(colors=['#8c5d36', '#ae8754', '#bc945e', '#c7a068', '#d2ac73'], tries=64)
PINE_STRIPPED = dict(colors=['#bf9763', '#c69f6a', '#cda772', '#d4af79'], streak=5, tries=64)
PINE_LOG_TOP = dict(colors=['#8e5c36', '#a06c41', '#b27f4e', '#c39963', '#d1ab74', '#6c5d4f', '#867868'], ring=2.1,
                    late=0.62, heart=4.4, bark=True, tries=32)
PINE_STRIPPED_TOP = dict(PINE_LOG_TOP, colors=['#8e5c36', '#a06c41', '#b27f4e', '#c39963', '#d1ab74', '#bf9763'],
                         bark=False)
SAPLING_SHAPE = dict(top=4, zigzag=0.2, shoots=3, shoot_gap=3, shoot_len=(2, 4), leaf_from=2, leaf_every=3, tries=64)
SAPLINGS = [
    ('scots_pine', dict(SAPLING_SHAPE, leaf='pine', shoots=0, top=3, zigzag=0.0, tuft_at=5, whorl_at=9, rays=13,
                        ray_len=7, spread=1.15, colors=['#8a5233', '#ad6b3e', '#4f6e45', '#6f915e', '#97b37f']),
     'young Scots pine: an orange-brown stem under a dense fresh gray-green tuft of long needles and a lower whorl '
     '(V7 review round 1: no spruce-like cone)'),
    ('linden', dict(SAPLING_SHAPE, leaf='linden', colors=['#5f4a36', '#806548', '#386428', '#497a33', '#5e8f40']),
     'young small-leaved lime: rounded heart-shaped leaves'),
    ('hornbeam', dict(SAPLING_SHAPE, leaf='hornbeam', leaf_every=2, colors=['#5d554a', '#7c7366', '#3d6a2c', '#4e7f38', '#629446']),
     'young hornbeam: oval pleated leaves on a gray stem'),
    ('beech', dict(SAPLING_SHAPE, leaf='beech', leaf_every=2, colors=['#5f5650', '#837a70', '#3b6a1f', '#5a8f2a', '#86b841']),
     'young beech: oval glossy leaves, a lighter green'),
    ('ash', dict(SAPLING_SHAPE, leaf='ash', shoots=4, shoot_gap=2, colors=['#5e5d54', '#7f7e72', '#2a5a22', '#3f7a2e', '#5f9a45']),
     'young ash: pinnate leaves with pairs of leaflets, darker greens than before (V7 review round 1: the first was pale)'),
    ('norway_maple', dict(SAPLING_SHAPE, leaf='maple', leaf_every=2, colors=['#5f4e3a', '#806a4c', '#386a28', '#4a8034', '#5f9642']),
     'young Norway maple: palmate lobed leaves'),
]



# ---------------------------------------------------------------------------------------------------- bare twigs (V13)
def twig_score(tex):
    """Score of a twigs candidate: the spread of the visible pixels over the columns and rows (as leaves_score), plus a
    large penalty for every lint error."""
    def score(img):
        cmin, rmin, cstd, rstd = t.line_cover(img[..., 3])
        sc = max(cstd, rstd)
        import lint
        sc += 10 * len(lint.lint_texture(tex.name, img, tex)[0])
        return sc
    return score


def _twig_path(rng, val, x, y, angle, length, tone, zigzag):
    """A 4-connected twig from (x, y) along `angle` (any direction: a crown seen from any side has no 'up'), `length`
    steps, bending by up to `zigzag` radians at random kinks (oak and hornbeam twigs zigzag, birch twigs run straight);
    every pixel takes `tone` unless a darker tone is there already. Returns the pixels in order."""
    pts = []
    fx, fy = x + 0.5, y + 0.5
    a = angle
    cx, cy = int(math.floor(fx)), int(math.floor(fy))
    for s in range(length):
        if zigzag and rng.random() < 0.3:
            a += rng.uniform(-zigzag, zigzag)
        fx += math.cos(a)
        fy += math.sin(a)
        nx, ny = int(math.floor(fx)), int(math.floor(fy))
        # walk 4-connected: first along x, then along y, so the line has no diagonal-only steps
        while (cx, cy) != (nx, ny):
            if cx != nx:
                cx += 1 if nx > cx else -1
            else:
                cy += 1 if ny > cy else -1
            px, py = cx % N, cy % N
            if val[py, px] == 0 or val[py, px] > tone:
                val[py, px] = tone
            pts.append((cx, cy))
    return pts


def twigs(tex, palettes):
    """Bare twigs of a leafless broadleaf crown (step V13, T-D5; V3-f: no 'up' direction, twigs crossing the tile edges,
    grayscale for the twig color of the species, tint index 1 of the leaf block): branches from random points in random
    directions (tone 1, the body of a twig), each with side twigs at p['fork'] (tone 2, lighter: thinner and lit) and
    their tips (tone 3, the last p['tip'] pixels, catching the light), until p['opacity'] of the pixels are twigs;
    p['zigzag'] bends them (oak and hornbeam zigzag, birch and linden straight and fine, ash coarse and sparse). The
    darkest tone 0 marks where twigs cross. The best of p['tries'] seeds by twig_score."""
    p = tex.params

    def build(seed):
        rng = t.Rng(seed)
        val = np.zeros((N, N), int)
        tries = 0
        while (val > 0).mean() < p['opacity'] and tries < 60:
            tries += 1
            x, y = rng.uniform(0, N), rng.uniform(0, N)
            a = rng.uniform(0, 2 * math.pi)
            if tries % 2 == 0:
                # every other branch starts in the emptiest column or row and runs across it, so no empty line repeats
                cols, rows = (val > 0).sum(axis=0), (val > 0).sum(axis=1)
                if cols.min() <= rows.min():
                    x = int(np.argmin(cols)) + 0.5
                    a = rng.choice((0.0, math.pi)) + rng.uniform(-0.6, 0.6)
                else:
                    y = int(np.argmin(rows)) + 0.5
                    a = rng.choice((0.5 * math.pi, 1.5 * math.pi)) + rng.uniform(-0.6, 0.6)
                a += 0.5 * math.pi
            main = _twig_path(rng, val, x, y, a, rng.integers(*p['length']), 1, p['zigzag'])
            for i, (mx, my) in enumerate(main):
                if i < 2 or rng.random() > p['fork']:
                    continue
                side = a + rng.choice((-1, 1)) * rng.uniform(0.5, 1.1)
                pts = _twig_path(rng, val, mx, my, side, rng.integers(*p['side']), 2, p['zigzag'])
                for (sx, sy) in pts[-p['tip']:]:
                    if val[sy % N, sx % N] == 2:
                        val[sy % N, sx % N] = 3
        alpha = np.where(val > 0, 255, 0).astype(np.uint8)
        idx = np.clip(val, 0, 3)
        # crossings: a body pixel with twig pixels on three or four sides takes the darkest tone
        nb = np.stack(t.neighbours4(val))
        idx = np.where((val == 1) & ((nb > 0).sum(axis=0) >= 3), 0, idx)
        return t.paint(idx, t.gray_ramp(4, *p['gray']), alpha), None
    return best_of(tex, build, twig_score(tex))[0]


TWIG_SPECIES = [
    # (species, twig color of the curve = the tint in the sheets, opacity, zigzag, branch length, side length, fork, note)
    ('birch', '#b58b8f', 0.40, 0.15, (9, 15), (3, 6), 0.45,
     'birch: dense, fine, straight purple-brown twigs (the purple haze of a birch crown in winter)'),
    ('linden', '#aa8377', 0.36, 0.25, (8, 14), (3, 6), 0.40, 'small-leaved lime: straight reddish twigs in a dense net'),
    ('norway_maple', '#9d8e80', 0.32, 0.25, (8, 13), (3, 5), 0.35, 'Norway maple: gray-brown twigs, opposite forks'),
    ('ash', '#9c9c91', 0.27, 0.15, (9, 15), (2, 5), 0.25, 'ash: few coarse pale gray twigs (thick shoots, black buds)'),
    ('hornbeam', '#959084', 0.38, 0.55, (8, 13), (3, 6), 0.45, 'hornbeam: dense gray zigzag twigs'),
    ('beech', '#a1897e', 0.36, 0.45, (8, 14), (3, 6), 0.40, 'beech: fine gray zigzag twigs with long pointed buds'),
    ('oak', '#9a8577', 0.32, 0.70, (7, 12), (2, 5), 0.40, 'pedunculate oak: gnarled, strongly zigzagging gray-brown twigs'),
]

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
            note='Scots pine, transition zone, lower block: the gray-brown plates of the lower bark start to flake off '
                 'from the top, orange "mirror" bark in the fissures and under the flaked plates (V1b review round 1)'),
    Texture('scots_pine_transition_bark_2', 'bark', bark_pine_transition, 'scots_pine_transition_bark_2/1',
            'scots_pine_bark', variant_of='scots_pine_transition_bark',
            params=dict(PINE_TRANSITION, lower='scots_pine_bark_2', upper='scots_pine_upper_bark_2'),
            expect=PINE_TRANSITION_EXPECT, palette_use='plate tones',
            note='second variant of the lower transition block, same edge rows'),
    Texture('scots_pine_transition_upper_bark', 'bark', bark_pine_transition, 'scots_pine_transition_upper_bark/1',
            'scots_pine_bark', params=PINE_TRANSITION_UPPER, expect=PINE_TRANSITION_UPPER_EXPECT,
            palette_use='plate tones',
            note='Scots pine, transition zone, upper block: mostly orange "mirror" bark with the last gray-brown plates '
                 'at the bottom (V1b review round 1)'),
    Texture('scots_pine_transition_upper_bark_2', 'bark', bark_pine_transition,
            'scots_pine_transition_upper_bark_2/1', 'scots_pine_bark',
            variant_of='scots_pine_transition_upper_bark',
            params=dict(PINE_TRANSITION_UPPER, lower='scots_pine_bark_2', upper='scots_pine_upper_bark_2', tries=24),
            expect=PINE_TRANSITION_UPPER_EXPECT, palette_use='plate tones',
            note='second variant of the upper transition block, same edge rows'),
    Texture('oak_bark', 'bark', bark_furrowed, 'oak_bark/2', 'oak_bark', params=OAK, expect=OAK_EXPECT,
            note='pedunculate oak: deep vertical furrows, ridges broken into platelets'),
    Texture('oak_bark_2', 'bark', bark_furrowed, 'oak_bark_2/1', 'oak_bark', variant_of='oak_bark',
            params=dict(OAK, variant_offset=0.3, tries=200),
            expect=OAK_EXPECT, note='second variant of the oak bark, same edge rows'),
    Texture('hornbeam_bark', 'bark', bark_hornbeam, 'hornbeam_bark/2', 'hornbeam_bark', params=HORNBEAM,
            expect=HORNBEAM_EXPECT,
            palette_use='reference photo; the recipe sets a cool gray, the palette hue is a white-balance cast',
            note='hornbeam: smooth slate gray, two uneven flutes, lighter streaks along the trunk'),
    Texture('hornbeam_bark_2', 'bark', bark_hornbeam, 'hornbeam_bark_2/1', 'hornbeam_bark', variant_of='hornbeam_bark',
            params=dict(HORNBEAM, lock=1, variant_wobble=1.6, variant_shift=2.0, streaks=7), expect=HORNBEAM_EXPECT, palette_use='reference photo, as hornbeam_bark',
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
            note='beech: smooth silver gray in faint patches, algae, lichen, a branch scar'),
    Texture('beech_bark_2', 'bark', bark_beech, 'beech_bark_2/1', 'beech_bark', variant_of='beech_bark',
            params=BEECH_VARIANT, expect=BEECH_EXPECT,
            note='second variant of the beech bark, same edge rows, no branch scar (step V1b)'),
    Texture('beech_bark_3', 'bark', bark_beech, 'beech_bark_3/1', 'beech_bark', variant_of='beech_bark',
            params=BEECH_VARIANT, expect=BEECH_EXPECT,
            note='third variant of the beech bark, same edge rows, no branch scar (V1b review round 1)'),
    Texture('tree_root_bark', 'bark', bark_furrowed, 'tree_root_bark/1', 'oak_bark', params=ROOT, expect=ROOT_EXPECT,
            palette_use='hue of the oak bark photo, made warmer and smoother by the recipe',
            note='underground roots of every species (step I1): thin smooth brown bark with fine shallow fissures'),
    Texture('scots_pine_leaves', 'leaves', leaves_needles, 'scots_pine_leaves/1', 'scots_pine_needles',
            tint='#7c996d', mcmeta=LEAVES_MCMETA,
            params=dict(opacity=0.6, twig=(3, 6), fill=0.6, needle=(2, 4), grays=(102, 126, 144, 161, 178), twig_axes=True,
                        candidates=6, tries=32),
            expect={'min_line': 6},
            note='Scots pine twig tips with needles, grayscale for the needle color of the curve (V7); the summer '
                 'color #7c996d is a gray green (Lab chroma 27, hue 133; V7 review round 1: less teal, more chroma than '
                 'the first #7f9c86), turning a yellower olive #8a9670 in winter; the palette photo gave a yellow olive '
                 '(#8e9963, hue 72) in warm light'),
    Texture('broadleaf_leaves', 'leaves', leaves_broadleaf, 'broadleaf_leaves/1', None, tint='foliage',
            mcmeta=LEAVES_MCMETA, params=dict(opacity=0.63, gray=(107, 211), candidates=6, tries=32),
            expect={'min_line': 7},
            note='broadleaf leaves for the foliage tint and the species color curves (B6, V7); clumps spread over '
                 'the rows and columns (every one at least 7 of 16 pixels, V3 review round 1); the gray ramp moved '
                 'up by 7 (V7 review round 1: mean as vanilla oak_leaves, the same local contrast)'),
    Texture('bilberry', 'plant', plant_bilberry, 'bilberry/2', 'bilberry', tint='grass',
            params=dict(stems=8, top=9, leafiness=0.75, zigzag=0.3, gray=(92, 180)),
            note='bilberry dwarf shrub, cross model, grayscale for the grass tint'),
    # Step V12 (herb layer and litter, decision PW-6; tools/art/ground.py): the flower beds of the spring geophytes and
    # their shared stem, the pine needle litter, bracken and the bilberry fruit.
    Texture('wood_anemone', 'plant', ground.flower_bed, 'wood_anemone/1', None,
            params=dict(leaves=ground.ANEMONE_LEAVES, flower_sprites=ground.ANEMONE_FLOWERS, leaf_cover=0.6, flowers=2, halo=((1, 7),),
                        colors=['#3f5f2d', '#4c6f35', '#5f8344', '#56603a', '#a5a99b', '#bdbcb4', '#b8ab68', '#7a9860']),
            note='wood anemone flower bed (vanilla flowerbed models by id): whorls of divided dark green leaves, white '
                 'six-tepalled flowers with a lavender shade and a yellow centre'),
    Texture('hepatica', 'plant', ground.flower_bed, 'hepatica/1', None,
            params=dict(leaves=ground.HEPATICA_LEAVES, flower_sprites=ground.HEPATICA_FLOWERS, leaf_cover=0.55, flowers=2, halo=((1, 7),),
                        colors=['#304b28', '#3d5b30', '#4e6c3d', '#5b4a4e', '#6b62a6', '#8079c0', '#a9a4cc', '#5d7a4c']),
            note='liverleaf flower bed: three-lobed leathery leaves, some of last year purplish brown, blue-violet '
                 'flowers with pale stamens'),
    Texture('forest_flower_stem', 'plant', ground.flower_stem, 'forest_flower_stem/1', None, tint='grass',
            params=dict(gray=(118, 150)),
            note='stem of the flower beds (column 0, rows 4-7 in game), grayscale for the grass tint'),
    Texture('needle_litter', 'plant', ground.needle_litter, 'needle_litter/1', None,
            params=dict(cover=0.62, length=(3, 6), tone_weights=(2, 3, 4, 2),
                        colors=['#74624f', '#7a5139', '#875c42', '#946a4d']),
            note='Scots pine needle litter (vanilla leaf litter templates by id, not tinted): rust-brown and older '
                 'gray-brown needles in a loose mat'),
    Texture('bracken_bottom', 'plant', ground.bracken, 'bracken_bottom/1', None, tint='grass',
            params=dict(part='bottom', frond_from=6, top=0, pinna_gap=2, pinna=(3, 6), droop=3, skip=0.15, under=0.5,
                        grays=(108, 130, 148, 122)),
            note='bracken, lower half: the erect stipe and the lowest pinnae, grayscale for the grass tint'),
    Texture('bracken_top', 'plant', ground.bracken, 'bracken_top/1', None, tint='grass',
            params=dict(part='top', frond_from=0, top=5, pinna_gap=2, pinna=(2, 7), droop=3, skip=0.1, under=0.6,
                        grays=(108, 130, 148, 122)),
            note='bracken, upper half: the broad frond held almost level, pinnae with pinnules, grayscale for the '
                 'grass tint'),
    Texture('bilberry_berries', 'plant', ground.berries, 'bilberry_berries/1', None,
            params=dict(count=5, rows=(10, 14), colors=['#272b45', '#2f3656', '#384166', '#454f78']),
            note='bilberry fruit, overlay of the bilberry cross model in its berries state (not tinted): blue-black '
                 'berries with a frosty blue bloom'),
    Texture('forest_moss', 'plant', ground.forest_moss, 'forest_moss/1', None,
            params=dict(grain=2.0, colors=['#3e4f2c', '#465a31', '#4f6436', '#586e3b', '#62783f']),
            note='feather moss mat of the pine forests under the bilberry and the needles (the mossy states, PW-7): '
                 'olive greens, duller and greener than the vanilla moss block, tileable'),
    # Step V13: the seasonal looks of the herb layer (decision PW-8 (a)): the leaves of the flower beds without flowers
    # (the same layout as the flowering texture, the flowers replaced by leaf tones) and the flowers of the bilberry.
    Texture('wood_anemone_leaves', 'plant', ground.flower_bed, 'wood_anemone/1', None,
            params=dict(leaves=ground.ANEMONE_LEAVES, flower_sprites=ground.ANEMONE_FLOWERS, leaf_cover=0.6, flowers=2, halo=((1, 7),),
                        drop_flowers=True,
                        colors=['#4a6431', '#5a7838', '#6f8c45', '#56603a', '#a5a99b', '#bdbcb4', '#b8ab68', '#86a061']),
            note='wood anemone after the flowering (step V13, May to early June): the same whorls of divided leaves, a '
                 'little lighter and yellower, no flowers'),
    Texture('hepatica_leaves', 'plant', ground.flower_bed, 'hepatica/1', None,
            params=dict(leaves=ground.HEPATICA_LEAVES, flower_sprites=ground.HEPATICA_FLOWERS, leaf_cover=0.55, flowers=2, halo=((1, 7),),
                        drop_flowers=True,
                        colors=['#304b28', '#3d5b30', '#4e6c3d', '#5b4a4e', '#6b62a6', '#8079c0', '#a9a4cc', '#5d7a4c']),
            note='liverleaf outside the flowering (step V13): the same three-lobed leathery leaves, no flowers'),
    Texture('bilberry_flowers', 'plant', ground.berries, 'bilberry_flowers/1', None,
            params=dict(count=7, rows=(4, 11), colors=['#c99aa2', '#d3a8ae', '#dbb4b9', '#e3c0c3']),
            note='bilberry flowers (step V13, May): small nodding greenish-pink bells, overlay of the bilberry cross model'),
    Texture('bilberries', 'plant', ground.berries, 'bilberries/1', None,
            params=dict(count=9, rows=(3, 13), colors=['#272b45', '#2f3656', '#384166', '#454f78']),
            note='the bilberry fruit item: a handful of blue-black berries with a frosty blue bloom'),
    # Step V7: the LITE Scots pine wood set (D1 b; the log side is scots_pine_bark) and the saplings (tools/art/wood.py).
    Texture('scots_pine_planks', 'planks', wood.planks, 'scots_pine_planks/1', None, params=PINE_PLANKS,
            expect={'luma': (135, 165), 'hue': (28, 40)}, palette_use=None,
            note='Scots pine planks: four boards of warm light yellow wood, grain along the boards, a knot'),
    Texture('stripped_scots_pine_log', 'stripped', wood.stripped_side, 'stripped_scots_pine_log/1', None,
            params=PINE_STRIPPED, expect={'luma': (145, 170), 'hue': (28, 40)},
            note='stripped Scots pine log: pale yellow wood with a fine vertical grain and a resin streak'),
    Texture('scots_pine_log_top', 'end', wood.log_end, 'scots_pine_log_top/1', None, params=PINE_LOG_TOP,
            expect={'luma': (120, 160)},
            note='Scots pine log top: reddish-brown heartwood, pale sapwood, distinct rings, a gray-brown bark border'),
    Texture('stripped_scots_pine_log_top', 'end', wood.log_end, 'stripped_scots_pine_log_top/1', None,
            params=PINE_STRIPPED_TOP, expect={'luma': (130, 170)},
            note='stripped Scots pine log top: the rings of the log top with a pale wood border'),
] + [Texture(f'{name}_sapling', 'sapling', wood.sapling, f'{name}_sapling/1', None, params=params, note=note)
     for name, params, note in SAPLINGS] + [
    # Step V13 (T-D5): the bare twigs of the leafless broadleaf crowns, grayscale for the twig color of the species.
    Texture(f'{name}_twigs', 'twigs', twigs, f'{name}_twigs/1', None, tint=color, mcmeta=LEAVES_MCMETA,
            params=dict(opacity=opacity, zigzag=zigzag, length=length, side=side, fork=fork, tip=2, gray=(98, 166), tries=24),
            expect={'min_line': 2}, note=note)
    for name, color, opacity, zigzag, length, side, fork, note in TWIG_SPECIES]

TEXTURES_BY_NAME = {tx.name: tx for tx in TEXTURES}
# The painter of each plated bark (a variant rebuilds its base with the base's own painter, so it gets the structure
# the base selected).
PLATED_PAINT = {bark_scots_pine_lower: paint_pine_lower, bark_furrowed: paint_furrowed}


def make(tex, palettes):
    img = tex.make(tex, palettes)
    if tex.tint:
        img = t.to_gray_tint(img)
    return img
