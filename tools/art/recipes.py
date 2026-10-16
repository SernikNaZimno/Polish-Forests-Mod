"""Species recipes of tools/art: one function per texture kind, parameters and palette per texture.

Each Texture says what it is (kind: bark, leaves or plant), whether the game tints it (tint: None, 'foliage', 'grass'
or a constant '#rrggbb' that V7 gives the needles through a color provider; tinted textures are grayscale), which
palette of palettes.json it uses, its seed, and the style bounds that lint.py checks on top of the common rules
(Texture.expect, see lint.py).

Bark descriptions (photos checked on Wikimedia Commons, CC0 and public domain, and species descriptions in
docs/research/06-lasy-polski-fitosocjologia-stl.md):
- Scots pine, lower trunk: gray-brown to red-brown bark in long plates separated by continuous, deep, dark red-brown
  fissures; the reddish inner bark shows in patches at the bottom of the fissures.
- Scots pine, upper trunk ("mirror"): thin rust-orange bark peeling in small papery flakes, no deep furrows.
- Pedunculate oak: gray-brown, deep, closely spaced vertical furrows; ridges broken into platelets by cross cracks.
- Hornbeam: smooth, slate or steel gray, fluted like muscles, with lighter streaks along the trunk; darker than beech,
  much lighter than vanilla pale oak (round 1 review of V0).
- Small-leaved lime: gray-brown, a network of shallow longitudinal fissures with narrow, flat ridges.
- Beech: thin, smooth, light warm silver gray in large faint patches, with greenish algae, irregular pale lichen and
  occasional dark branch-scar 'moustaches'.

Scale: a block is 1 m, so 1 px is 6.25 cm. Trunks are thin (radius 1-7 px, decision D8), and a trunk side shows a
slice of the texture only a few pixels wide: structures are 2-4 px wide (bark plates, ridges, flakes).

Selection by measure: a bark recipe builds its texture for the seeds '<seed>', '<seed>#1', ... '<seed>#<tries - 1>' and
keeps the one with the least horizontal banding (texlib.banding: row means over the full width and over the centre
slices of thin trunks), so a stacked trunk shows no light and dark ring at every block. It is deterministic.
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


def banding_score(img, caps=(5.0, 8.0)):
    full, centre = t.banding(img)
    return max(full / caps[0], centre / caps[1])


def bark_score(tex):
    """Score of a bark candidate: its banding (texlib.banding against the lint caps), plus a large penalty for every
    other lint bound of the recipe it breaks (isolated pixels, vertical grain, contrast), so the selection keeps a
    texture that passes the lint when one of the seeds gives it."""
    exp = tex.expect

    def score(img):
        sc = banding_score(img, (exp.get('rows', 5.0), exp.get('slices', 8.0)))
        l = t.luma(img[..., :3])
        if t.isolated_fraction(l) > 0.095:
            sc += 10
        if 'grain' in exp and t.grain(img) < exp['grain']:
            sc += 10
        if l.std() > exp.get('std', 28.0):
            sc += 10
        return sc
    return score


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


def light_edges(idx, groove, faces, lo, hi, vertical=True):
    """Light from the top-left on the plate faces next to grooves (texlib.edge_light, on face pixels only): +1 tone
    right of a groove (and under one), -1 tone left of a groove (and above one). vertical=False lights only the sides
    of the plates (no bevel at the plate ends, which reads as masonry)."""
    lit = roll(groove, 0, 1) & faces
    shade = roll(groove, 0, -1) & faces
    if vertical:
        lit |= roll(groove, 1, 0) & faces
        shade |= roll(groove, -1, 0) & faces
    shade &= ~lit
    out = idx.copy()
    out[lit] = np.minimum(idx[lit] + 1, hi)
    out[shade] = np.maximum(idx[shade] - 1, lo)
    return out


def build_plated(tex, paint_fn, palettes):
    """best_of for a plated bark; a variant is built on the (selected) structure of its base texture."""
    base_s = base_img = None
    if tex.variant_of:
        base_tex = TEXTURES_BY_NAME[tex.variant_of]
        base_img, base_s = build_plated(base_tex, paint_fn, palettes)

    def build(seed):
        s = plated(seed, tex.params, base_s)
        img = paint_fn(tex, s, palettes)
        if base_img is not None:                       # the shared edge rows exactly as in the base texture
            img[0], img[N - 1] = base_img[0], base_img[N - 1]
        return img, s
    return best_of(tex, build)


# ---------------------------------------------------------------------------------------------------- pine, lower
def paint_pine_lower(tex, s, palettes):
    """Scots pine, lower trunk. Tones: 0 fissure (dark red-brown), 1 rust (inner bark at the fissure bottom),
    2 fissure wall (the shaded right edge of a plate, red-brown, so the fissures read 2 px wide), 3 and 4 the two
    gray-brown plate faces (plate tone mixed with vertical streaks), 5 lit left edge of a plate."""
    p = tex.params
    rng = t.Rng(tex.seed + '/paint')
    groove = s['furrow'] | s['cross']
    faces = ~groove
    v = (1 - p['streak']) * np.nan_to_num(s['tone']) + p['streak'] * s['streak']
    idx = np.where(v >= np.quantile(v[faces], 1 - p['light_share']), 4, 3)
    idx = np.where(faces, idx, 0)
    idx = t.majority(idx, passes=1, keep=(0,))
    idx = light_edges(idx, groove, faces, 2, 5, vertical=p['bevel'])
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
    face = t.ramp_hue(palette_colors(palettes, 'scots_pine_bark'), 4, *p['face_lum'], sat=p['sat'],
                      hue_drift=p['hue_drift'], hue_shift=p['hue_shift'])
    red = palette_colors(palettes, 'scots_pine_upper_bark')
    fissure = t.ramp_hue(red, 1, p['fissure_lum'], p['fissure_lum'], sat=p['fissure_sat'], hue_shift=p['red_shift'])
    rust = t.ramp_hue(red, 1, p['rust_lum'], p['rust_lum'], sat=p['rust_sat'], hue_shift=p['red_shift'])
    wall = t.ramp_hue(red, 1, p['wall_lum'], p['wall_lum'], sat=p['wall_sat'], hue_shift=p['red_shift'])
    return t.paint(idx, fissure + rust + wall + face[1:])


def bark_scots_pine_lower(tex, palettes):
    return build_plated(tex, paint_pine_lower, palettes)[0]


# ---------------------------------------------------------------------------------------------------- oak, linden
def paint_furrowed(tex, s, palettes):
    """Oak and linden. Tones: 0 deep furrow, 1 shallow fissure and crack, 2 ridge shade, 3-4 ridge faces, 5 lit
    edge. Oak: every long furrow is deep (tone 0); linden: the long fissures are shallow (tone 1) with deep spots."""
    p = tex.params
    rng = t.Rng(tex.seed + '/paint')
    groove = s['furrow'] | s['cross']
    faces = ~groove
    v = 0.65 * np.nan_to_num(s['tone']) + 0.35 * s['noise']
    q = np.digitize(v, [np.quantile(v[faces], p['face_split'])])
    idx = np.where(faces, 3 + q, 0)
    idx = t.majority(idx, passes=1, keep=(0,))
    idx = light_edges(idx, groove, faces, 2, 5)
    idx[s['furrow']] = p['furrow_tone']
    idx[s['cross']] = 1
    if p['deep'] and p['furrow_tone'] != 0:            # a few deep spots in the shallow fissures
        ys, xs = np.nonzero(s['furrow'])
        for k in range(p['deep']):
            j = rng.integers(0, len(ys))
            idx[ys[j], xs[j]] = 0
            idx[(ys[j] + 1) % N, xs[j]] = 0 if s['furrow'][(ys[j] + 1) % N, xs[j]] else idx[(ys[j] + 1) % N, xs[j]]
    c = t.ramp_hue(palette_colors(palettes, tex.palette), 6, *p['lum'], sat=p['sat'], hue_shift=p.get('hue_shift', 0),
                   hue_drift=p.get('hue_drift', 0))
    return t.paint(idx, c)


def bark_furrowed(tex, palettes):
    return build_plated(tex, paint_furrowed, palettes)[0]


# ---------------------------------------------------------------------------------------------------- pine, upper
def bark_scots_pine_upper(tex, palettes):
    """Scots pine "mirror": rust-orange papery flakes (2 face tones) lying on each other like roof tiles. Under part of
    the lower edge of a flake a short dark seam (2-5 px, not the whole edge, so no row of the texture turns dark), the
    curled edge above it lit; on the lighter flakes the curled edge is a highlight (spread over several flakes)."""
    p = tex.params

    def build(seed):
        rng = t.Rng(seed)
        _, _, cell = t.worley(rng, p['flakes'], stretch_x=p['stretch_x'])
        light = rng.array(p['flakes'])[cell] < p['light_share']
        idx = np.where(light, 2, 1)                     # flakes: main (2) and darker (1)
        bottom = cell != roll(cell, -1, 0)               # the lowest pixel row of a flake above another flake
        seam = np.zeros((N, N), bool)
        per_row = np.zeros(N, int)                       # seam pixels per row: at most p['row_max'] (no dark rows)
        for k in sorted(range(p['flakes']), key=lambda _: rng.random()):
            ys, xs = np.nonzero(bottom & (cell == k))
            if len(ys) < 2:
                continue
            order = np.argsort(xs, kind='stable')         # along the lower edge of the flake, left to right
            ln = min(len(ys), rng.integers(*p['seam']))
            j = rng.integers(0, len(ys) - ln + 1)
            drop = rng.random() < p['step']               # a stepped seam: its second half one row lower
            px = [((ys[q] + 1 + (1 if drop and 2 * n_ >= ln else 0)) % N, xs[q]) for n_, q in enumerate(order[j:j + ln])]
            rows = np.bincount([q[0] for q in px], minlength=N)
            if (per_row + rows).max() > p['row_max']:
                continue
            per_row += rows
            for q in px:
                seam[q] = True                          # the seam lies on the top row of the flake below
        curl = roll(seam, -1, 0) & ~seam                 # the curled lower edge of the flake above
        idx[curl] = 3
        hi = curl & light
        hi &= roll(hi, 0, 1) | roll(hi, 0, -1)          # highlights at least 2 px long
        idx[hi] = 4
        idx[seam] = 0
        c = t.ramp_hue(palette_colors(palettes, 'scots_pine_upper_bark'), 5, *p['lum'], sat=p['sat'],
                       hue_drift=p['hue_drift'], hue_shift=p['hue_shift'])
        return t.paint(idx, c), None

    def score(img):
        hi = (t.luma(img[..., :3]) >= t.luma(img[..., :3]).max() - 0.5).sum()
        return bark_score(tex)(img) + (0 if 6 <= hi <= 20 else 10)
    return best_of(tex, build, score)[0]


# ---------------------------------------------------------------------------------------------------- hornbeam
def bark_hornbeam(tex, palettes):
    """Hornbeam: two broad flutes ('muscles') of uneven width with wavy borders, lit on the left and shaded on the
    right, a faint groove between them, lighter streaks along the trunk and a few short fissures. Cool slate gray."""
    p = tex.params

    def build(seed):
        rng = t.Rng(seed)
        ys, xs = t.grid()
        b0 = rng.uniform(0, N)
        w0 = rng.uniform(*p['flute'])
        wob = [2 * p['wobble'] * (t.noise_1d(rng, 4) - 0.5) for _ in range(2)]
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
        for _ in range(p['streaks']):
            x, y0, ln = rng.integers(0, N), rng.integers(0, N), rng.integers(3, 8)
            if idx[y0, x] < 2:
                continue
            for dy in range(ln):
                idx[(y0 + dy) % N, x] = 4
        for _ in range(p['fissures']):
            x, y0, ln = rng.integers(0, N), rng.integers(0, N), rng.integers(2, 4)
            for dy in range(ln):
                idx[(y0 + dy) % N, x] = 0
        c = t.ramp_hue(None, 5, *p['lum'], sat=p['sat'], hue=p['hue'])
        return t.paint(idx, c), None
    return best_of(tex, build)[0]


# ---------------------------------------------------------------------------------------------------- beech
MOUSTACHE = [(0, 0), (0, 1), (0, -1), (1, -2), (1, -3), (2, -4), (0, 2), (1, 3), (1, 4), (2, 5)]


def bark_beech(tex, palettes):
    """Beech: large faint patches of warm silver gray with a faint vertical streak, a greenish algae patch, irregular
    pale lichen and one dark branch scar ('moustache') with a light lip under it."""
    p = tex.params

    def build(seed):
        rng = t.Rng(seed)
        patches = 0.55 * t.value_noise(rng, 2, 2) + 0.3 * t.value_noise(rng, 4, 4) + 0.15 * t.value_noise(rng, 8, 1)
        idx = 1 + t.quantize(patches, 3, shares=[0.25, 0.5, 0.25])          # tones 1..3
        idx = t.majority(idx, passes=2)
        algae = t.value_noise(rng, 2, 2)
        idx[(algae > np.quantile(algae, 1 - p['algae'])) & (idx <= 2)] = 5
        for _ in range(p['lichen']):                                        # irregular lichen blob, 5-7 px
            blob = [(rng.integers(0, N), rng.integers(0, N))]
            for _ in range(rng.integers(4, 7)):
                y, x = blob[rng.integers(0, len(blob))]
                d = rng.integers(0, 4)
                q = ((y + (0, 0, 1, -1)[d]) % N, (x + (1, -1, 0, 0)[d]) % N)
                if q not in blob:
                    blob.append(q)
            for q in blob:
                idx[q] = 4
        for _ in range(p['scars']):
            y, x = rng.integers(2, N - 3), rng.integers(0, N)
            for dy, dx in MOUSTACHE:
                idx[(y + dy) % N, (x + dx) % N] = 0
            for dy, dx in MOUSTACHE:
                yy, xx = (y + dy + 1) % N, (x + dx) % N
                if idx[yy, xx] != 0:
                    idx[yy, xx] = 3
        silver = t.ramp_hue(palette_colors(palettes, 'beech_bark'), 5, *p['lum'], sat=p['sat'],
                            hue_shift=p['hue_shift'])
        green = t.ramp_hue(palette_colors(palettes, 'beech_bark'), 1, p['algae_lum'], p['algae_lum'],
                           sat=p['algae_sat'], hue_shift=p['algae_hue_shift'])
        return t.paint(idx, silver + green), None
    return best_of(tex, build)[0]


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


def leaves_broadleaf(tex, palettes):
    p = tex.params
    rng = t.Rng(tex.seed)
    tone = t.fbm(rng, 2, 4, 4)
    val = t.stamp(rng, BROADLEAF_SPRITES, p['opacity'], tone_field=tone)
    alpha = np.where(val > 0, 255, 0).astype(np.uint8)
    idx = np.clip(val - 1, 0, 4)
    return t.paint(idx, t.gray_ramp(5, *p['gray']), alpha)


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
    p = tex.params
    rng = t.Rng(tex.seed)
    val = np.zeros((N, N), int)
    tries = 0
    while (val > 0).mean() < p['opacity'] and tries < 500:
        tries += 1
        pine_twig(rng, val, p)
    alpha = np.where(val > 0, 255, 0).astype(np.uint8)
    idx = np.clip(val - 1, 0, 4)
    return t.paint(idx, t.gray_ramp(5, *p['gray']), alpha)


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
                    val[yy, xx] = max(val[yy, xx], {'1': 4, '2': 5, '3': 2}[ch])

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

PINE_LOWER = dict(lines=3, amp=1.6, wave=2, jitter=0.6, plate=(9, 15), cross='full', band=3, light_share=0.55,
                  rust=3, streak=0.45, bevel=False, face_lum=(88, 132), sat=0.32, hue_drift=14, hue_shift=-6,
                  fissure_lum=46, fissure_sat=0.5, wall_lum=70, wall_sat=0.42, rust_lum=84, rust_sat=0.6, red_shift=6,
                  tries=48)
PINE_EXPECT = {'luma': (92, 106), 'dark': 0.25, 'grain': 2.0, 'std': 30}     # deep, continuous fissures
OAK = dict(lines=4, amp=1.0, wave=4, jitter=0.4, plate=(3, 7), cross='part', band=3, furrow_tone=0, deep=0,
           face_split=0.5, lum=(58, 126), sat=0.42, hue_shift=-12, tries=48)
OAK_EXPECT = {'luma': (80, 100), 'dark': 0.05, 'grain': 2.0, 'near': ('oak_log', 10)}
LINDEN = dict(lines=5, amp=1.4, wave=2, jitter=0.5, plate=(4, 8), cross='diag', band=3, furrow_tone=1, deep=3,
              face_split=0.45, lum=(62, 146), sat=0.3, hue_shift=-6, tries=48)
LINDEN_EXPECT = {'luma': (100, 120), 'dark': 0.05, 'grain': 2.0}

TEXTURES = [
    Texture('scots_pine_bark', 'bark', bark_scots_pine_lower, 'scots_pine_bark/5', 'scots_pine_bark',
            params=PINE_LOWER, expect=PINE_EXPECT,
            note='Scots pine, lower trunk: long gray-brown plates, continuous dark red-brown fissures, rust patches'),
    Texture('scots_pine_bark_2', 'bark', bark_scots_pine_lower, 'scots_pine_bark_2/1', 'scots_pine_bark',
            variant_of='scots_pine_bark', params=PINE_LOWER, expect=PINE_EXPECT,
            note='second variant of the lower pine bark, same edge rows'),
    Texture('scots_pine_upper_bark', 'bark', bark_scots_pine_upper, 'scots_pine_upper_bark/2',
            'scots_pine_upper_bark',
            params=dict(flakes=12, stretch_x=2.0, seam=(3, 7), step=0.3, row_max=3, light_share=0.7, lum=(63, 172), sat=0.7, hue_drift=11,
                        hue_shift=6.5, tries=96),
            expect={'luma': (105, 135), 'dark': 0.02, 'hue': (18, 28)},
            note='Scots pine "mirror": thin rust-orange bark in papery flakes'),
    Texture('oak_bark', 'bark', bark_furrowed, 'oak_bark/2', 'oak_bark', params=OAK, expect=OAK_EXPECT,
            note='pedunculate oak: deep vertical furrows, ridges broken into platelets'),
    Texture('oak_bark_2', 'bark', bark_furrowed, 'oak_bark_2/1', 'oak_bark', variant_of='oak_bark', params=OAK,
            expect=OAK_EXPECT, note='second variant of the oak bark, same edge rows'),
    Texture('hornbeam_bark', 'bark', bark_hornbeam, 'hornbeam_bark/2', 'hornbeam_bark',
            params=dict(flute=(7, 9.5), wobble=1.0, lit=0.3, shade=0.3, groove=0.6, streaks=5, fissures=2,
                        lum=(80, 156), sat=0.11, hue=206, tries=24),
            expect={'luma': (115, 132), 'dark': 0.02, 'grain': 2.0, 'distinct': ('beech_bark', 10)},
            palette_use='reference photo; the recipe sets a cool gray, the palette hue is a white-balance cast',
            note='hornbeam: smooth slate gray, two uneven flutes, lighter streaks along the trunk'),
    Texture('linden_bark', 'bark', bark_furrowed, 'linden_bark/3', 'linden_bark', params=LINDEN,
            expect=LINDEN_EXPECT, note='small-leaved lime: gray-brown, net of shallow longitudinal fissures'),
    Texture('linden_bark_2', 'bark', bark_furrowed, 'linden_bark_2/1', 'linden_bark', variant_of='linden_bark',
            params=LINDEN, expect=LINDEN_EXPECT, note='second variant of the linden bark, same edge rows'),
    Texture('beech_bark', 'bark', bark_beech, 'beech_bark/2', 'beech_bark',
            params=dict(lichen=2, scars=1, algae=0.12, lum=(126, 190), sat=0.13, hue_shift=-14, algae_lum=146,
                        algae_sat=0.2, algae_hue_shift=14, tries=24),
            expect={'luma': (150, 172), 'dark': 0.0, 'grain': 1.2, 'std': 18},
            note='beech: smooth warm silver gray in faint patches, algae, lichen, a branch scar'),
    Texture('scots_pine_leaves', 'leaves', leaves_needles, 'scots_pine_leaves/1', 'scots_pine_needles',
            tint='#7f9c86', mcmeta=LEAVES_MCMETA,
            params=dict(opacity=0.6, twig=(2, 5), fill=0.7, needle=(3, 5), gray=(64, 210)),
            note='Scots pine twig tips with needles, grayscale for a constant needle color (V7); the proposed color '
                 '#7f9c86 is a gray green (hue 135), lighter and grayer than vanilla spruce; the palette photo gave a '
                 'yellow olive (#8e9963, hue 72) in warm light'),
    Texture('broadleaf_leaves', 'leaves', leaves_broadleaf, 'broadleaf_leaves/1', None, tint='foliage',
            mcmeta=LEAVES_MCMETA, params=dict(opacity=0.63, gray=(88, 214)),
            note='broadleaf leaves for the foliage tint and the species color curves (B6, V7)'),
    Texture('bilberry', 'plant', plant_bilberry, 'bilberry/2', 'bilberry', tint='grass',
            params=dict(stems=8, top=9, leafiness=0.75, zigzag=0.3, gray=(52, 194)),
            note='bilberry dwarf shrub, cross model, grayscale for the grass tint'),
]

TEXTURES_BY_NAME = {tx.name: tx for tx in TEXTURES}


def make(tex, palettes):
    img = tex.make(tex, palettes)
    if tex.tint:
        img = t.to_gray_tint(img)
    return img
