"""Species recipes of tools/art: one function per texture kind, parameters and palette per texture.

Each Texture says what it is (kind: bark, leaves or plant), whether the game tints it (tint: None, 'foliage', 'grass'
or a constant '#rrggbb' that V7 gives the needles through a color provider; tinted textures are grayscale), which
palette of palettes.json it uses, its seed, and the style bounds that lint.py checks on top of the common rules
(expect: mean luma range on 0..255 and the largest share of dark pixels, luma < 50).

Bark descriptions (photos checked on Wikimedia Commons, CC0 and public domain, and species descriptions in
docs/research/06-lasy-polski-fitosocjologia-stl.md):
- Scots pine, lower trunk: thick gray-brown bark split by dark furrows into irregular, long, scaly plates; rust-brown
  where the outer scales fell off. Plates must stay readable in game: light plate faces, narrow dark furrows (the
  round 2 prototype of the research report was too dark, furrows dominated).
- Scots pine, upper trunk ("mirror"): thin orange to rust bark peeling in small papery flakes, no deep furrows.
- Pedunculate oak: gray-brown, deep, closely spaced vertical furrows; ridges broken into finger-like platelets.
- Hornbeam: smooth light ash gray, fluted like muscles, with fine lighter vertical streaks; much lighter than
  vanilla pale oak (round 1 review of V0).
- Small-leaved lime: gray-brown, a network of shallow longitudinal fissures with narrow ridges.
- Beech: thin, smooth, light silver gray, mottled, with dark horizontal 'eyebrow' marks and pale lichen spots.

Scale: a block is 1 m, so 1 px is 6.25 cm. Trunks are thin (radius 1-7 px, decision D8), and a trunk side shows a
slice of the texture only a few pixels wide: structures are 2-4 px wide (bark plates, ridges, flakes).
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


# ---------------------------------------------------------------------------------------------------- helpers
def palette_colors(palettes, name, skip=0):
    return palettes['palettes'][name]['colors'][skip:]


def cut_at(ref, share):
    """Threshold of `ref` below which `share` of its pixels lie (taken from the base texture for a variant, so
    the rows a variant shares with its base get the same tones)."""
    flat = np.sort(ref.ravel(), kind='stable')
    return flat[min(int(len(flat) * share), len(flat) - 1)]


def quantize_like(v, ref, levels, shares=None):
    """Like texlib.quantize, but with the thresholds of `ref`."""
    if shares is None:
        shares = [1.0 / levels] * levels
    cum = np.cumsum(shares)[:-1] / sum(shares)
    return np.digitize(v, [cut_at(ref, c) for c in cum])


def variant_fields(tex, fields_fn):
    """(fields, reference fields): for a variant, every field is edge-locked to the base texture's field and the
    tone thresholds come from the base fields."""
    own = fields_fn(t.Rng(tex.seed), tex.params)
    if not tex.variant_of:
        return own, own
    base = fields_fn(t.Rng(TEXTURES_BY_NAME[tex.variant_of].seed), tex.params)
    return {k: t.edge_locked(base[k], own[k]) for k in own}, base


# ---------------------------------------------------------------------------------------------------- pine, lower
def pine_lower_fields(rng, p):
    f1, f2, cell = t.worley(rng, p['plates'], stretch_y=p['stretch'])
    return {
        'edge': f2 - f1,                                   # 0 on the furrow between two plates
        'plate': rng.array(p['plates'])[cell],             # each plate its own tone
        'scale': t.value_noise(rng, 4, 8),                 # scales inside a plate
        'rust': t.value_noise(rng, 8, 8),                  # where the outer scales fell off
    }


def bark_scots_pine_lower(tex, palettes):
    p = tex.params
    idx = pine_lower_index(tex)
    if tex.variant_of:
        # the fields are edge-locked, so the edge rows already match; copying them makes it exact (the majority filter
        # looks one row further), and the variants then stack on each other in any order without a seam
        base = pine_lower_index(TEXTURES_BY_NAME[tex.variant_of])
        idx[0], idx[N - 1] = base[0], base[N - 1]
    c = t.ramp_hue(palette_colors(palettes, 'scots_pine_bark'), 6, *p['lum'], sat=p['sat'])
    rust_c = t.ramp_hue(palette_colors(palettes, 'scots_pine_upper_bark'), 5, *p['rust_lum'], sat=p['rust_sat'])[1]
    return t.paint(idx, c + [rust_c])


def pine_lower_index(tex):
    p = tex.params
    f, ref = variant_fields(tex, pine_lower_fields)
    furrow = f['edge'] <= cut_at(ref['edge'], p['furrow'])
    face = (1 - p['scale_mix']) * f['plate'] + p['scale_mix'] * f['scale']
    ref_face = (1 - p['scale_mix']) * ref['plate'] + p['scale_mix'] * ref['scale']
    idx = 2 + quantize_like(face, ref_face, 4, shares=[0.22, 0.3, 0.3, 0.18])     # plate faces: tones 2..5
    idx = t.edge_light(idx, furrow, 1, 5)
    # rust-brown inner bark shows where the outer scales broke off, mostly along the lower and right edge of a plate
    near = t.roll(furrow, -1, 0) | t.roll(furrow, 0, -1) | t.roll(furrow, -2, 0)
    rust = (f['rust'] > cut_at(ref['rust'], 1 - p['rust'])) & near & ~furrow
    idx[rust] = 6
    idx[furrow] = 0
    return t.majority(idx, passes=1, keep=(0, 6))


# ---------------------------------------------------------------------------------------------------- pine, upper
def bark_scots_pine_upper(tex, palettes):
    p = tex.params
    rng = t.Rng(tex.seed)
    f1, f2, cell = t.worley(rng, p['flakes'], stretch_x=p['stretch_x'])
    edge = f2 - f1
    flake = rng.array(p['flakes'])[cell]
    seam = edge <= cut_at(edge, p['seam'])
    idx = 1 + t.quantize(flake, 3, shares=[0.3, 0.4, 0.3])       # flakes: tones 1..3
    curl = t.roll(seam, 1, 0) & ~seam & (flake > 0.45)           # lit, curled lower edge of the flake above
    curl &= t.roll(curl, 0, 1) | t.roll(curl, 0, -1)               # only curls at least 2 px long
    idx = t.majority(idx, passes=1)
    idx[curl] = 4
    idx[seam] = 0
    c = t.ramp_hue(palette_colors(palettes, 'scots_pine_upper_bark'), 5, *p['lum'], sat=p['sat'],
                   hue_drift=p['hue_drift'])
    return t.paint(idx, c)


# ---------------------------------------------------------------------------------------------------- oak, linden
def bark_furrowed(tex, palettes):
    """Long vertical ridges between furrows (stretched Worley cells), the ridges split by short cross cracks into
    platelets. Oak: deep, dark furrows (tone 0) and wide, rough ridges. Linden: shallow fissures (tone 1, only a few
    deep spots), narrower and lighter ridges."""
    p = tex.params
    rng = t.Rng(tex.seed)
    f1, f2, cell = t.worley(rng, p['ridges'], stretch_y=p['stretch'])
    edge = f2 - f1
    furrow = edge <= cut_at(edge, p['furrow'])
    deep = edge <= cut_at(edge, p['deep'])
    tone = rng.array(p['ridges'])[cell]
    idx = 2 + t.quantize(0.6 * tone + 0.4 * t.value_noise(rng, 4, 8), 4)          # ridge faces: tones 2..5
    # cross cracks split every ridge into finger-like platelets
    ys = np.mgrid[0:N, 0:N][0]
    period = np.array([rng.integers(p['plate_min'], p['plate_max'] + 1) for _ in range(p['ridges'])])[cell]
    offset = np.array([rng.integers(0, N) for _ in range(p['ridges'])])[cell]
    row = (ys + offset) % period == 0
    notch = row & t.roll(furrow, 0, 1) & ~furrow                  # a crack starts at the furrow on its left ...
    crack = notch | (t.roll(notch, 0, 1) & row & ~furrow)         # ... and runs 1-2 px into the ridge
    groove = furrow | crack
    idx = t.edge_light(idx, groove, 1, 5)
    idx[crack] = 1
    idx[furrow] = p['furrow_tone']
    idx[deep] = 0
    idx = t.majority(idx, passes=1, keep=(0,))
    c = t.ramp(palette_colors(palettes, tex.palette), 6, *p['lum'], sat=p['sat'])
    return t.paint(idx, c)


# ---------------------------------------------------------------------------------------------------- hornbeam
def bark_hornbeam(tex, palettes):
    p = tex.params
    rng = t.Rng(tex.seed)
    ys, xs = t.grid()
    wobble = 1.6 * (t.value_noise(rng, 1, 4) - 0.5)
    flute = 0.5 + 0.5 * np.cos(2 * math.pi * (xs + wobble) / p['flute'])     # broad 'muscles' under the bark
    height = 0.7 * flute + 0.3 * t.fbm(rng, 2, 2, 4)
    idx = 1 + t.quantize(height, 4, shares=[0.2, 0.3, 0.3, 0.2])
    slope = height - t.roll(height, 0, 1)                                     # light from the left
    idx[(slope > p['slope']) & (idx < 4)] += 1
    idx[(slope < -p['slope']) & (idx > 1)] -= 1
    idx = t.majority(idx, passes=1)
    for _ in range(p['streaks']):                                             # fine silvery vertical streaks
        x, y0, ln = rng.integers(0, N), rng.integers(0, N), rng.integers(3, 8)
        for dy in range(ln):
            idx[(y0 + dy) % N, x] = 5
    for _ in range(p['fissures']):                                            # a few short, shallow fissures
        x, y0, ln = rng.integers(0, N), rng.integers(0, N), rng.integers(2, 5)
        for dy in range(ln):
            idx[(y0 + dy) % N, x] = 0
    c = t.ramp(palette_colors(palettes, 'hornbeam_bark'), 6, *p['lum'], sat=p['sat'])
    return t.paint(idx, c)


# ---------------------------------------------------------------------------------------------------- beech
def bark_beech(tex, palettes):
    p = tex.params
    rng = t.Rng(tex.seed)
    mottle = 0.6 * t.value_noise(rng, 4, 4) + 0.4 * t.value_noise(rng, 8, 8)
    idx = 1 + t.quantize(mottle, 3, shares=[0.25, 0.5, 0.25])
    idx = t.majority(idx, passes=2)
    for _ in range(p['lichen']):                                              # pale lichen spots, 3-4 px
        y, x = rng.integers(0, N), rng.integers(0, N)
        for dy, dx in ((0, 0), (0, 1), (1, 0), (1, 1))[:rng.integers(3, 5)]:
            idx[(y + dy) % N, (x + dx) % N] = 4
    for _ in range(p['eyebrows']):                                            # dark 'eyebrows', light lip under
        y, x, w = rng.integers(0, N), rng.integers(0, N), rng.integers(2, 5)
        for dx in range(w):
            idx[y, (x + dx) % N] = 0
            if 0 < dx < w - 1 or w == 2:
                idx[(y + 1) % N, (x + dx) % N] = 3
    c = t.ramp(palette_colors(palettes, 'beech_bark'), 5, *p['lum'], sat=p['sat'])
    return t.paint(idx, c)


# ---------------------------------------------------------------------------------------------------- foliage
BROADLEAF_SPRITES = [
    # 1 leaf, 2 lit edge (top-left), 3 shadow (bottom-right), 0 empty
    ["2101", "1111", "0113"],
    ["0210", "1111", "1013", "0010"],
    ["21", "11", "13"],
    ["021", "1113", "0130"],
    ["20", "11", "11", "03"],
    ["021", "113", "010"],
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


BILBERRY_LEAVES = [["21", "13"], ["2", "1"], ["21", "11", "03"], ["12"]]


def plant_bilberry(tex, palettes):
    """Bilberry (Vaccinium myrtillus): a low, dense dwarf shrub of green, angular, branching twigs with small oval
    alternate leaves (lighter green than the twigs). Cross-model texture for the grass tint (grayscale), plant
    9-11 px tall; berries and autumn colors are separate states (V12)."""
    p = tex.params
    rng = t.Rng(tex.seed)
    val = np.zeros((N, N), int)

    def leaf(x, y, side):
        spr = BILBERRY_LEAVES[rng.integers(0, len(BILBERRY_LEAVES))]
        for dy, row in enumerate(spr):
            for dx, ch in enumerate(row):
                if ch == '0':
                    continue
                xx = x + (dx if side > 0 else -dx)
                yy = y - len(spr) + 1 + dy
                if 0 <= xx < N and 0 <= yy < N:
                    val[yy, xx] = max(val[yy, xx], {'1': 4, '2': 5, '3': 3}[ch])

    def twig(x, y, top, lean, depth):
        side = rng.choice([-1, 1])
        while y > top:
            val[y, x] = max(val[y, x], 2)                    # green twig, darker than the leaves
            y -= 1
            if rng.random() < 0.35:                          # angular twigs: zig-zag
                x = int(np.clip(x + (lean or rng.choice([-1, 1])), 0, N - 1))
            if rng.random() < p['leafiness'] and y < N - 2:
                leaf(x + side, y, side)
                side = -side                                 # alternate leaves
            if depth < 2 and rng.random() < p['branching'] and y < N - 3:
                twig(x, y, max(top + rng.integers(1, 3), y - rng.integers(2, 5)), rng.choice([-1, 1]), depth + 1)
        leaf(x, y, rng.choice([-1, 1]))                      # young leaves at the tip

    for x in [int(v) for v in np.linspace(2, N - 3, p['stems']).round()]:
        twig(int(np.clip(x + rng.integers(-1, 2), 1, N - 2)), N - 1, rng.integers(*p['top']), 0, 0)
    alpha = np.where(val > 0, 255, 0).astype(np.uint8)
    idx = np.clip(val - 1, 0, 4)
    return t.paint(idx, t.gray_ramp(5, *p['gray']), alpha)


# ---------------------------------------------------------------------------------------------------- the list
PINE_LOWER = dict(plates=8, stretch=2.8, furrow=0.15, scale_mix=0.1, rust=0.25, lum=(38, 166), sat=0.27,
                  rust_lum=(60, 170), rust_sat=0.5)

TEXTURES = [
    Texture('scots_pine_bark', 'bark', bark_scots_pine_lower, 'scots_pine_bark/3', 'scots_pine_bark',
            params=PINE_LOWER, expect={'luma': (95, 125), 'dark': 0.2},
            note='Scots pine, lower trunk: gray-brown plates, dark furrows, rust where scales fell off'),
    Texture('scots_pine_bark_2', 'bark', bark_scots_pine_lower, 'scots_pine_bark/4', 'scots_pine_bark',
            variant_of='scots_pine_bark', params=PINE_LOWER, expect={'luma': (95, 125), 'dark': 0.2},
            note='second variant of the lower pine bark, same top and bottom rows'),
    Texture('scots_pine_upper_bark', 'bark', bark_scots_pine_upper, 'scots_pine_upper_bark/1',
            'scots_pine_upper_bark', params=dict(flakes=18, stretch_x=1.6, seam=0.14, lum=(84, 180), sat=0.58, hue_drift=16),
            expect={'luma': (120, 160), 'dark': 0.02},
            note='Scots pine "mirror": thin orange bark in papery flakes'),
    Texture('oak_bark', 'bark', bark_furrowed, 'oak_bark/1', 'oak_bark',
            params=dict(ridges=8, stretch=8.0, furrow=0.2, deep=0.2, furrow_tone=0, plate_min=4, plate_max=8,
                        lum=(30, 140), sat=0.8),
            expect={'luma': (72, 100), 'dark': 0.3},
            note='pedunculate oak: deep vertical furrows, ridges broken into platelets'),
    Texture('hornbeam_bark', 'bark', bark_hornbeam, 'hornbeam_bark/1', 'hornbeam_bark',
            params=dict(flute=8, slope=0.08, streaks=6, fissures=3, lum=(98, 188), sat=0.6),
            expect={'luma': (135, 170), 'dark': 0.02},
            note='hornbeam: smooth, light ash gray, fluted, silvery vertical streaks'),
    Texture('linden_bark', 'bark', bark_furrowed, 'linden_bark/2', 'linden_bark',
            params=dict(ridges=9, stretch=8.0, furrow=0.2, deep=0.05, furrow_tone=1, plate_min=4, plate_max=8,
                        lum=(52, 156), sat=0.5),
            expect={'luma': (95, 125), 'dark': 0.1},
            note='small-leaved lime: gray-brown, net of shallow longitudinal fissures'),
    Texture('beech_bark', 'bark', bark_beech, 'beech_bark/1', 'beech_bark',
            params=dict(lichen=4, eyebrows=3, lum=(104, 200), sat=0.35), expect={'luma': (140, 175), 'dark': 0.02},
            note='beech: smooth silver gray, mottled, eyebrow marks, lichen spots'),
    Texture('scots_pine_leaves', 'leaves', leaves_needles, 'scots_pine_leaves/1', 'scots_pine_needles',
            tint='#8e9963', params=dict(opacity=0.6, twig=(2, 5), fill=0.7, needle=(3, 5), gray=(64, 210)),
            note='Scots pine twig tips with needles, grayscale for a constant needle color (V7); the proposed color is '
                 'the lit needle tone of the palette (k-means color 3 of 4)'),
    Texture('broadleaf_leaves', 'leaves', leaves_broadleaf, 'broadleaf_leaves/1', None, tint='foliage',
            params=dict(opacity=0.63, gray=(72, 200)),
            note='broadleaf leaves for the foliage tint and the species color curves (B6, V7)'),
    Texture('bilberry', 'plant', plant_bilberry, 'bilberry/1', 'bilberry', tint='grass',
            params=dict(stems=5, top=(5, 8), leafiness=0.8, branching=0.25, gray=(52, 192)),
            note='bilberry dwarf shrub, cross model, grayscale for the grass tint'),
]

TEXTURES_BY_NAME = {tx.name: tx for tx in TEXTURES}


def make(tex, palettes):
    img = tex.make(tex, palettes)
    if tex.tint:
        img = t.to_gray_tint(img)
    return img
