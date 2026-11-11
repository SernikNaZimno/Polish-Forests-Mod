"""Recipes of the herb layer and the litter of the vertical slice (step V12 of docs/04-pionowy-wycinek.md, decision PW-6:
bilberry, wood anemone, liverleaf, pine needle litter and bracken). Every texture is 16 x 16, alpha 0 or 255, and smooth
(checkpoint CP-A: the luma steps between neighbouring pixels at or under the vanilla plant median, lint.CONTRAST):
neighbouring tones differ by small steps, edges of a flower or a needle are a mid tone between it and what lies around.

Species descriptions (docs/research/06-lasy-polski-fitosocjologia-stl.md and photos on Wikimedia Commons, CC0 and public
domain, only looked at):
- Wood anemone (Anemone nemorosa): a whorl of three deeply divided leaves on a 10-25 cm stem and one white flower of six
  tepals, 2-4 cm across, often pinkish beneath, with a yellow centre; forms carpets in the oak-hornbeam forest in April.
  Seen from above at 1 px = 6.25 cm a flower is a soft white cross of 5 px with a warm centre, the leaves dark green
  divided lobes around it.
- Liverleaf (Hepatica nobilis): leathery three-lobed leaves, dark green, the old ones of last year purplish brown, and
  blue-violet flowers of 6-10 tepals, 1.5-2.5 cm, with pale stamens; in tufts, March-April.
- Scots pine needle litter: needles 3-7 cm (1 px), first rust brown, later gray-brown, in a loose mat over the humus.
- Bracken (Pteridium aquilinum): a tall erect stipe and a broad triangular, bipinnate frond held nearly horizontal,
  0.5-2 m (two blocks), yellowish green; brown from autumn (step V13 shows the phases).
- Bilberry fruit: blue-black berries with a frosty blue bloom, 5-8 mm, under the leaves (the berries state of the
  bilberry block, overlay of its cross model).

The flower beds follow the vanilla flowerbed models (by id): the up face of segment k shows one 8 x 8 quadrant of the
texture (1: top left, 2: bottom left, 3: bottom right, 4: top right), the stem texture is read in column 0, rows 4-7.
The litter follows the vanilla leaf litter templates (by id): quadrants as above, segment 2 the whole left half.
"""
import numpy as np

import texlib as t
from texlib import N

# A flower seen from above: '1' tepal, '2' tepal in shade (the side away from the top-left light), '3' centre.
ANEMONE_FLOWERS = [
    ['010', '131', '020'],
    ['0110', '1312', '0220'],
    ['011', '132', '020'],
]
HEPATICA_FLOWERS = [
    ['010', '132', '020'],
    ['11', '32'],
    ['0110', '1322', '0200'],
]
# A leaf whorl or tuft seen from above: '1' leaf, '2' leaf in light, '3' leaf in shade (wood anemone: three divided
# leaves; liverleaf: three-lobed leaves, '4' an old purplish leaf).
ANEMONE_LEAVES = [
    ['01010', '11211', '03130', '13031', '00100'],
    ['0101', '1121', '3113', '0310'],
    ['01100', '12110', '01313', '00310'],
]
HEPATICA_LEAVES = [
    ['0110', '1221', '1131', '0330'],
    ['01100', '12210', '41131', '04430'],
    ['041', '121', '134'],
]
# Step F1 (the herb layer of the lowland beech forest, docs/04 §3 "Kroki F"): sweet woodruff (whorls of six to eight
# narrow leaves, a star from above; loose clusters of tiny white four-petalled flowers), hairy wood-rush (tufts of
# narrow grass-like leaves, the tips often dry; small brown flower clusters with pale stamens), wood sorrel (trefoils of
# three heart-shaped leaflets; white flowers with lilac veins and a yellow centre) and coralroot (Cardamine bulbifera:
# pinnate leaves along a stem; pale lilac four-petalled flowers).
WOODRUFF_FLOWERS = [
    ['11', '21'],
    ['010', '121', '020'],
    ['11', '12'],
]
WOODRUFF_LEAVES = [
    ['01110', '13211', '12121', '11231', '01110'],
    ['0110', '1321', '1231', '0110'],
    ['0110', '1211', '1131', '0130'],
]
WOOD_RUSH_FLOWERS = [
    ['11', '23'],
    ['010', '132', '020'],
    ['12', '21'],
]
WOOD_RUSH_LEAVES = [
    ['41014', '11211', '01310', '11211', '10101'],
    ['4114', '1221', '0331', '1111'],
    ['01410', '11211', '01310', '11011'],
]
SORREL_FLOWERS = [
    ['010', '131', '020'],
    ['0110', '1312', '0220'],
    ['11', '32'],
]
SORREL_LEAVES = [
    ['11011', '12121', '11311', '01310', '00100'],
    ['1101', '1221', '1131', '0110'],
    ['0110', '1211', '1311', '0330'],
]
CORALROOT_FLOWERS = [
    ['010', '131', '010'],
    ['11', '32'],
    ['010', '132', '020'],
]
CORALROOT_LEAVES = [
    ['11011', '12121', '11311', '12121', '01310'],
    ['1121', '1231', '1321', '0131'],
    ['121', '131', '121'],
]

# Step F2: marsh marigold (Caltha palustris): rosettes of round, glossy, kidney-shaped leaves; golden yellow flowers of
# five petals, 2-4 cm (one 3 x 3 px cross from above).
MARIGOLD_FLOWERS = [
    ['010', '131', '020'],
    ['11', '32'],
    ['0110', '1312', '0220'],
]
MARIGOLD_LEAVES = [
    ['01110', '12211', '12111', '11131', '01310'],
    ['0110', '1221', '1131', '0130'],
    ['011', '121', '131'],
]


# Step F6: alpine coltsfoot (Homogyne alpina: small kidney-shaped leathery leaves, glossy dark green, the older ones
# dull, in flat rosettes; the purple flower heads drawn with the liverleaf's flower sprites).
COLTSFOOT_LEAVES = [
    ['01110', '12211', '12131', '13331', '04440'],
    ['0110', '1241', '1331', '0440'],
    ['041', '121', '134'],
]


# Step F3: glandular toothwort (Dentaria glandulosa: whorls of three-parted, toothed leaves high on the stem; nodding
# purple-violet four-petalled flowers in a short raceme) and white wood-rush (Luzula luzuloides: tufts of narrow, hairy
# grass-like leaves; loose clusters of small whitish flowers on thin stalks).
TOOTHWORT_FLOWERS = [
    ['010', '132', '010'],
    ['11', '23'],
    ['0110', '1321', '0110'],
]
TOOTHWORT_LEAVES = [
    ['10101', '12121', '01310', '11211', '10001'],
    ['1011', '1231', '0131', '1101'],
    ['101', '131', '121'],
]
WHITE_WOOD_RUSH_FLOWERS = [
    ['101', '020', '101'],
    ['11', '21'],
    ['010', '121', '010'],
]
WHITE_WOOD_RUSH_LEAVES = [
    ['10101', '11211', '01310', '11211', '01010'],
    ['1011', '1221', '0131', '1101'],
    ['01010', '11211', '01310', '10101'],
]


# Step F4: heather (Calluna vulgaris: dense twigs of tiny scale-like leaves, dark bronze-green, and short spikes of small
# pink-purple bells in late summer) and crowberry (Empetrum nigrum: prostrate mats of needle-like rolled leaves, dark
# glossy green, and round black berries from July).
HEATHER_FLOWERS = [
    ['01', '13', '12'],
    ['010', '131', '020'],
    ['11', '32'],
]
HEATHER_LEAVES = [
    ['10101', '01210', '11311', '01210', '10101'],
    ['1010', '0121', '1131', '0110'],
    ['101', '131', '010'],
]
CROWBERRY_BERRIES = [
    ['13', '12'],
    ['31', '21'],
]
CROWBERRY_LEAVES = [
    ['01110', '11211', '12311', '11211', '01110'],
    ['0110', '1211', '1131', '0110'],
    ['111', '121', '131'],
]

# Step F5: the herb layer of the floodplain forests: the lesser celandine (Ficaria verna: rosettes of heart-shaped glossy
# dark green leaves; glossy golden yellow star flowers of 8-12 petals, 2-3 cm, March-April) and the wild garlic (Allium
# ursinum: two broad elliptic bright green leaves from the ground; white star flowers in a loose umbel, April-May).
CELANDINE_FLOWERS = [
    ['010', '131', '020'],
    ['0110', '1312', '0220'],
    ['11', '32'],
]
CELANDINE_LEAVES = [
    ['01010', '11211', '12111', '11131', '01310'],
    ['1010', '1211', '1131', '0130'],
    ['101', '121', '131'],
]
GARLIC_FLOWERS = [
    ['121', '232', '121'],
    ['0120', '1312', '0210'],
    ['12', '23'],
]
GARLIC_LEAVES = [
    ['00110', '01211', '12110', '11310', '01300'],
    ['01100', '11210', '01311', '00131'],
    ['0110', '1211', '1131', '0310'],
]


def _place(val, sprite, x0, y0, mapping, box):
    """Stamps a sprite (rows of digits) at (x0, y0) inside box = (x_lo, y_lo, x_hi, y_hi) (exclusive), writing
    mapping[ch] where the sprite has ch != '0'; returns the pixels written."""
    xl, yl, xh, yh = box
    n = 0
    for dy, row in enumerate(sprite):
        for dx, ch in enumerate(row):
            if ch == '0':
                continue
            x, y = x0 + dx, y0 + dy
            if xl <= x < xh and yl <= y < yh:
                val[y, x] = mapping[ch]
                n += 1
    return n


def flower_bed(tex, palettes):
    """A flower bed of a herb of the forest floor (wood anemone, liverleaf; step F1: sweet woodruff, hairy wood-rush, wood
    sorrel, coralroot): in each 8 x 8 quadrant (one segment of the bed)
    leaf whorls until p['leaf_cover'] of it is green and p['flowers'] flowers over them; the colors p['colors'] are
    0-2 leaves (shade, body, light), 3 an old leaf (liverleaf), 4-5 tepals (shade, body), 6 the centre, 7 the pale lit
    leaf right around a flower. Flowers keep
    a pixel from the quadrant edge (the segment's edge cuts straight in game)."""
    p = tex.params
    rng = t.Rng(tex.seed)
    leaves = p['leaves']
    flowers = p['flower_sprites']
    val = np.full((N, N), -1, int)
    leaf_map = {'1': 1, '2': 2, '3': 0, '4': 3}
    flower_map = {'1': 5, '2': 4, '3': 6}
    for qx, qy in ((0, 0), (0, 8), (8, 8), (8, 0)):
        box = (qx, qy, qx + 8, qy + 8)
        tries = 0
        while (val[qy:qy + 8, qx:qx + 8] >= 0).mean() < p['leaf_cover'] and tries < 200:
            tries += 1
            spr = leaves[rng.integers(0, len(leaves))]
            _place(val, spr, qx + rng.integers(-1, 7), qy + rng.integers(-1, 7), leaf_map, box)
        placed, tries = 0, 0
        centres = []
        while placed < p['flowers'] and tries < 200:
            tries += 1
            spr = flowers[rng.integers(0, len(flowers))]
            x0, y0 = qx + rng.integers(1, 8 - len(spr[0])), qy + rng.integers(1, 8 - len(spr))
            if any(abs(x0 - cx) < 3 and abs(y0 - cy) < 3 for cx, cy in centres):
                continue
            # Rings of lit leaf around the flower (CP-A: smooth; no white dot straight on the dark leaves): p['halo'] is
            # a list of (ring, color), the outer ring first, e.g. color 7 right next to the flower.
            for ring, tone in p['halo']:
                for dy in range(-ring, len(spr) + ring):
                    for dx in range(-ring, len(spr[0]) + ring):
                        x, y = x0 + dx, y0 + dy
                        if qx <= x < qx + 8 and qy <= y < qy + 8 and val[y, x] in (0, 1, 2, 3):
                            val[y, x] = tone
            _place(val, spr, x0, y0, flower_map, (qx + 1, qy + 1, qx + 7, qy + 7))
            centres.append((x0, y0))
            placed += 1
    if p.get('drop_flowers'):
        # Step V13: the leaves without flowers, same layout: the tepals take the leaf body tones, the centre the lit leaf.
        val = np.where(val == 4, 1, np.where(val == 5, 2, np.where(val == 6, 7, val)))
    alpha = np.where(val >= 0, 255, 0).astype(np.uint8)
    colors = [t.hex_to_rgb(c) for c in p['colors']]
    return t.paint(np.clip(val, 0, len(colors) - 1), colors, alpha)


def flower_stem(tex, palettes):
    """The stem texture of the flower beds, grayscale for the grass tint. The vanilla models read column 0, rows 4-7
    (a stem 1 px wide); the columns 0-3 carry a vertical stem in four tones so that the texture keeps the lint's color
    rules (the other pixels are never shown)."""
    p = tex.params
    val = np.full((N, N), -1, int)
    for x in range(4):
        for y in range(N):
            val[y, x] = (x + y // 4) % 4
    alpha = np.where(val >= 0, 255, 0).astype(np.uint8)
    return t.paint(np.clip(val, 0, 3), t.gray_ramp(4, *p['gray']), alpha)


def needle_litter(tex, palettes):
    """Scots pine needle litter for the leaf litter templates (not tinted): needles 3-6 px long, straight or slightly
    bent, in all directions, in rust-brown tones (p['colors']: 0 old gray-brown, 1-3 rust brown from dark to light),
    until p['cover'] of each quadrant is covered. A needle is one tone along its length (no dark one-pixel lines inside
    the mat: the tones differ by small steps); light needles lie over darker ones (later stamps cover earlier ones)."""
    p = tex.params
    rng = t.Rng(tex.seed)
    val = np.full((N, N), -1, int)
    dirs = [(1, 0), (0, 1), (1, 1), (1, -1), (2, 1), (1, 2), (2, -1), (1, -2)]
    weights = p['tone_weights']
    for qx, qy in ((0, 0), (0, 8), (8, 8), (8, 0)):
        tries = 0
        while (val[qy:qy + 8, qx:qx + 8] >= 0).mean() < p['cover'] and tries < 400:
            tries += 1
            dx, dy = dirs[rng.integers(0, len(dirs))]
            if rng.random() < 0.5:
                dx, dy = -dx, -dy
            length = rng.integers(*p['length'])
            r = rng.random() * sum(weights)
            tone = 0
            while r >= weights[tone]:
                r -= weights[tone]
                tone += 1
            x, y = qx + rng.integers(0, 8), qy + rng.integers(0, 8)
            fx, fy = float(x), float(y)
            step = max(abs(dx), abs(dy))
            for _ in range(length):
                ix, iy = int(round(fx)), int(round(fy))
                if not (qx <= ix < qx + 8 and qy <= iy < qy + 8):
                    break
                val[iy, ix] = tone
                fx += dx / step
                fy += dy / step
    alpha = np.where(val >= 0, 255, 0).astype(np.uint8)
    colors = [t.hex_to_rgb(c) for c in p['colors']]
    return t.paint(np.clip(val, 0, len(colors) - 1), colors, alpha)


def bracken(tex, palettes):
    """Bracken, one half of a double plant (cross model, grayscale for the grass tint). p['part'] 'bottom': the erect
    stipe from the base (a 1 px stem in the stem tone, slightly off the middle) and, from p['frond_from'] up, the lowest
    pinnae of the frond; 'top': the broad frond held almost level, a rachis bending outwards with pinnae, each pinna a
    row of pinnules that shortens towards its tip, the whole frond widest at its base (a triangle seen from the side),
    the top rows empty above the frond. Tones: 0 shade under the frond, 1 body, 2 lit pinnules, 3 stem."""
    p = tex.params
    rng = t.Rng(tex.seed)
    val = np.full((N, N), -1, int)
    lo, hi = 1, N - 2
    stem_x = 7 + rng.integers(0, 2)
    if p['part'] == 'bottom':
        for y in range(N):
            val[y, stem_x] = 3
        top = p['frond_from']
    else:
        for y in range(p['top'] + 4, N):
            val[y, stem_x] = 3
        top = p['top']
    # Pinnae: from the rachis at rows top..N-1 outwards; the length grows from the tip of the frond downwards.
    rows = list(range(top, N, p['pinna_gap']))
    for k, y in enumerate(rows):
        frac = (y - top) / max(N - 1 - top, 1)
        length = int(round(p['pinna'][0] + (p['pinna'][1] - p['pinna'][0]) * frac))
        for side in (-1, 1):
            if rng.random() < p['skip']:
                continue
            x, yy = stem_x, y
            for s in range(length):
                x += side
                if s % p['droop'] == p['droop'] - 1:
                    yy += 1
                if not (lo <= x <= hi and 0 <= yy < N):
                    break
                val[yy, x] = 1
                # pinnules: one above (lit) and one below (shade), shorter towards the tip of the pinna
                if s < length - 1:
                    if yy - 1 >= 0 and val[yy - 1, x] < 0:
                        val[yy - 1, x] = 2
                    if yy + 1 < N and val[yy + 1, x] < 0 and rng.random() < p['under']:
                        val[yy + 1, x] = 0
    alpha = np.where(val >= 0, 255, 0).astype(np.uint8)
    return t.paint(np.clip(val, 0, 3), [(g, g, g) for g in p['grays']], alpha)


def berries(tex, palettes):
    """Bilberry fruit for the berries overlay of the bilberry's cross model (not tinted): p['count'] berries of 2 x 2
    px (the bloom lit from the top left, the body in two tones and the shade at the bottom right), in the lower part
    of the plant (p['rows']), kept a pixel from the sides; colors 0 shade, 1-2 body, 3 bloom."""
    p = tex.params
    rng = t.Rng(tex.seed)
    val = np.full((N, N), -1, int)
    placed, tries = 0, 0
    spots = []
    while placed < p['count'] and tries < 500:
        tries += 1
        x, y = rng.integers(2, N - 3), rng.integers(*p['rows'])
        if any(abs(x - a) < 3 and abs(y - b) < 3 for a, b in spots):
            continue
        spots.append((x, y))
        val[y, x] = 3
        val[y, x + 1] = 2
        val[y + 1, x] = 1
        val[y + 1, x + 1] = 0
        placed += 1
    alpha = np.where(val >= 0, 255, 0).astype(np.uint8)
    colors = [t.hex_to_rgb(c) for c in p['colors']]
    return t.paint(np.clip(val, 0, 3), colors, alpha)


def bells(tex, palettes):
    """Bilberry flowers for the flowers overlay of the bilberry's cross model (not tinted; review round 1 of V13: the
    earlier 2 x 2 px pink squares on top of the plant read from 15-30 blocks as a pink flowering meadow): p['count'] small
    nodding bells of 1 x 2 px, the green-tinged calyx above (color 2) and the greenish-pink corolla below (color 1, every
    other bell the darker edge color 0), hung on the sides of the plant (columns p['columns']) under the leaves (rows
    p['rows']), kept apart."""
    p = tex.params
    rng = t.Rng(tex.seed)
    val = np.full((N, N), -1, int)
    spots = []
    tries = 0
    while len(spots) < p['count'] and tries < 500:
        tries += 1
        lo, hi = p['columns'][rng.integers(0, len(p['columns']))]
        x, y = rng.integers(lo, hi + 1), rng.integers(*p['rows'])
        if any(abs(x - a) < 4 and abs(y - b) < 3 for a, b in spots):
            continue
        val[y, x] = 2
        val[y + 1, x] = 1 if len(spots) % 2 == 0 else 0
        spots.append((x, y))
    alpha = np.where(val >= 0, 255, 0).astype(np.uint8)
    colors = [t.hex_to_rgb(c) for c in p['colors']]
    return t.paint(np.clip(val, 0, 2), colors, alpha)


def paired_leaves(tex, palettes):
    """Lily of the valley without flowers (the vanilla tinted cross by id, grayscale for the grass tint; review round 1
    of V13): two broad elliptic leaves rising from a common sheath at the bottom middle and leaning a little apart, the
    left one taller (p['leaves']: base column, top column, top row, half width). Light from the top left: the left part
    of a leaf lit (tone 2), the body (tone 1), the right edge in shade (tone 0); the sheath at the base tone 3."""
    p = tex.params
    val = np.full((N, N), -1, int)
    for bx, tx, ty, half in p['leaves']:
        for y in range(ty, N):
            f = (N - 1 - y) / max(N - 1 - ty, 1)
            cx = bx + (tx - bx) * f
            w = half * np.sin(np.pi * min(max(f, 0.0), 1.0)) ** 0.7
            for x in range(N):
                d = x - cx
                if abs(d) <= w + 0.25 and (w >= 0.45 or abs(d) < 0.5):
                    tone = 2 if d < -0.35 * w else 0 if d > 0.6 * w and w > 1.0 else 1
                    if val[y, x] < 0 or tone > val[y, x]:
                        val[y, x] = tone
    for y in range(N - p['sheath'], N):
        for x in p['sheath_columns']:
            val[y, x] = 3
    alpha = np.where(val >= 0, 255, 0).astype(np.uint8)
    return t.paint(np.clip(val, 0, 3), [(g, g, g) for g in p['grays']], alpha)


def forest_moss(tex, palettes):
    """The moss under the dwarf shrubs and the needles of the pine forests (PW-7; the carpet layer of the mossy states):
    feather mosses (Pleurozium, Hylocomium) as a dense, tileable mat of fine tufts in olive greens, duller and greener
    than the vanilla moss block (V0 review: the vanilla moss carpet is too saturated and yellow-green for the pine
    forest floor). A tileable value noise of 8 and 16 cells per block plus fine noise (no large blotch that repeats at every block), cut into p['colors'] tones at equal shares, so
    neighbouring pixels differ by one small step (CP-A: smooth)."""
    p = tex.params
    rng = t.Rng(tex.seed)
    v = t.fbm(rng, 2, 8, 8, 0.6) + p['grain'] * t.value_noise(rng, 16, 16)
    idx = t.quantize(v, len(p['colors']))
    return t.paint(idx, [t.hex_to_rgb(c) for c in p['colors']])


def herb(tex, palettes):
    """A herb of one block of the alder forests (step F2, the cross model of vanilla's flowers, not tinted, the colors
    baked in): seen from the side, the ground at the bottom row. p['form']: 'blades' (yellow iris: a fan of sword leaves
    from the base, the flowers on a stalk among them), 'tuft' (tussock sedge: a dense tuft of narrow arching leaves, the
    brown spikes on thin stalks above it), 'fronds' (marsh fern: a few erect fronds, pinnae in pairs shortening to the
    tip) or 'stems' (stinging nettle: upright stems with opposite pointed leaves, the drooping green flower strands from
    the upper leaf axils). The colors p['colors']: 0-2 leaves (shade, body, light), 3 stem or leaf base, 4-6 the flower
    (shade, body, light; p['flowers'] of them, 0 for the leaves-only texture of the season). Steps between neighbours are
    small: a flower sits in a ring of the lit leaf tone (CP-A)."""
    p = tex.params
    rng = t.Rng(tex.seed)
    val = np.full((N, N), -1, int)
    form = p['form']

    def put(x, y, tone, over=True):
        if 0 <= x < N and 0 <= y < N and (over or val[y, x] < 0):
            val[y, x] = tone

    tops = []
    if form == 'blades':
        k = p['count']
        for i in range(k):
            bx = 6 + (i % 4)
            lean = (i - (k - 1) / 2) * p['spread'] + rng.integers(-1, 2) * 0.4
            ty = p['top'] + rng.integers(0, 4)
            tone = 2 if lean < 0 else 0 if i % 3 == 2 else 1
            for y in range(N - 1, ty - 1, -1):
                f = (N - 1 - y) / max(N - 1 - ty, 1)
                x = int(round(bx + lean * f ** 1.4))
                put(x, y, tone if y > ty else 1)
                if y > N - 4:
                    put(x + (1 if lean >= 0 else -1), y, 3, over=False)
    elif form == 'tuft':
        k = p['count']
        for i in range(k):
            side = -1 if i % 2 == 0 else 1
            reach = p['reach'][0] + rng.integers(0, p['reach'][1] - p['reach'][0] + 1)
            height = p['height'][0] + rng.integers(0, p['height'][1] - p['height'][0] + 1)
            bx = 7 + rng.integers(0, 2)
            tone = 2 if side < 0 else (0 if i % 4 == 3 else 1)
            steps = height + reach
            for s in range(steps + 1):
                f = s / steps
                x = int(round(bx + side * reach * f ** 1.8))
                y = int(round(N - 1 - height * np.sin(f * np.pi * p['arch'])))
                put(x, max(y, 0), tone)
            put(bx, N - 1, 3)
            put(bx + side, N - 1, 3, over=False)
    elif form == 'fronds':
        for i in range(p['count']):
            bx = 5 + 3 * i + rng.integers(0, 2)
            lean = (i - (p['count'] - 1) / 2) * p['spread']
            ty = p['top'] + rng.integers(0, 3)
            for y in range(N - 1, ty - 1, -1):
                f = (N - 1 - y) / max(N - 1 - ty, 1)
                x = int(round(bx + lean * f))
                put(x, y, 3 if y > N - 4 else 1)
                if y <= N - 4 and (y - ty) % 2 == 1:
                    length = max(1, int(round(p['pinna'] * (1 - 0.7 * (1 - f)) * (1.0 - 0.5 * f) + 0.5)))
                    for s in range(1, length + 1):
                        put(x - s, y, 2, over=False)
                        put(x + s, y, 0 if s == length else 1, over=False)
    else:  # stems
        for i in range(p['count']):
            bx = 4 + 4 * i + rng.integers(0, 2)
            ty = p['top'] + rng.integers(0, 3)
            for y in range(N - 1, ty - 1, -1):
                put(bx, y, 3)
            for y in range(ty + 1, N - 2, p['node']):
                f = (y - ty) / max(N - 1 - ty, 1)
                length = 1 + int(round(p['leaf'] * min(1.0, 0.4 + f)))
                for side in (-1, 1):
                    for s in range(1, length + 1):
                        put(bx + side * s, y - (1 if s == length else 0), 2 if side < 0 else 1, over=False)
                        if s < length:
                            put(bx + side * s, y + 1, 0, over=False)
                tops.append((bx, y))
    flowers = p.get('flowers', 0)
    placed = 0
    if form == 'blades':
        for _ in range(flowers):
            x0 = 5 + rng.integers(0, 5)
            y0 = p['top'] - 1 + rng.integers(0, 2)
            for y in range(y0 + 2, N - 3):
                put(x0 + 1, y, 3)
            for dy in range(-1, 4):
                for dx in range(-1, 4):
                    if val[min(max(y0 + dy, 0), N - 1), min(max(x0 + dx, 0), N - 1)] in (0, 1):
                        put(x0 + dx, y0 + dy, 2)
            for dx, dy, tone in ((0, 0, 5), (1, 0, 6), (2, 0, 5), (0, 1, 5), (1, 1, 6), (2, 1, 4), (1, 2, 4)):
                put(x0 + dx, y0 + dy, tone)
            placed += 1
    elif form == 'tuft':
        for _ in range(flowers):
            x0 = 4 + rng.integers(0, 8)
            y0 = 1 + rng.integers(0, 3)
            for y in range(y0 + 3, N - 4):
                put(x0, y, 3)
            for dy, tone in ((0, 6), (1, 6), (2, 5), (3, 4)):
                put(x0, y0 + dy, tone)
                put(x0 + 1, y0 + dy, 4 if dy >= 2 else 5)
            placed += 1
    elif form == 'stems' and tops:
        upper = [tp for tp in tops if tp[1] < N // 2 + 1]
        for _ in range(min(flowers, len(upper))):
            bx, y = upper[rng.integers(0, len(upper))]
            side = rng.choice([-1, 1])
            for s in range(1, 4):
                put(bx + side * s, y + s - 1 if s > 1 else y, 5 if s < 3 else 4)
                put(bx + side * s, y + s, 4, over=False)
            placed += 1
    alpha = np.where(val >= 0, 255, 0).astype(np.uint8)
    colors = [t.hex_to_rgb(c) for c in p['colors']]
    return t.paint(np.clip(val, 0, len(colors) - 1), colors, alpha)


def climber(tex, palettes):
    """The hop of the floodplain forests (step F5, Humulus lupulus; the plane of vanilla's vine model by id, seen from the
    side of the trunk it climbs, not tinted, the colors baked in): p['stems'] stems twining up the whole tile in a slow
    wave (the hop winds clockwise round its support), with opposite three- to five-lobed rough leaves at every
    p['node'] pixels and, with p['cones'] > 0, the pale green cones (2 x 3 px) hanging from the upper leaf axils (August,
    September). The tile repeats up the trunk, so a stem leaves the top where it enters the bottom. The colors
    p['colors']: 0-2 leaves (shade, body, light), 3 stem, 4-6 cone (shade, body, light)."""
    p = tex.params
    rng = t.Rng(tex.seed)
    val = np.full((N, N), -1, int)

    def put(x, y, tone, over=True):
        x, y = x % N, y % N
        if over or val[y, x] < 0:
            val[y, x] = tone

    lobes = [['010', '121', '111', '010'], ['0110', '1211', '1111', '0130'], ['101', '121', '131'],
             ['01010', '11211', '11111', '01310']]
    leaf_map = {'1': 1, '2': 2, '3': 0}
    nodes = []
    for i in range(p['stems']):
        x0 = 3 + i * (N // p['stems']) + rng.integers(0, 2)
        phase = rng.uniform(0, 2 * np.pi)
        last = None
        for y in range(N - 1, -1, -1):
            # one full wave over the tile height, so the stem joins itself across the top and bottom edges
            x = int(round(x0 + p['amp'] * np.sin(2 * np.pi * (N - 1 - y) / N + phase)))
            put(x, y, 3)
            if last is not None and abs(x - last) > 1:
                put((x + last) // 2, y, 3)
            last = x
            if (y + i * 3) % p['node'] == 0:
                nodes.append((x, y))
    for x, y in nodes:
        for side in (-1, 1):
            spr = lobes[rng.integers(0, len(lobes))]
            w = len(spr[0])
            x1 = x + 1 if side > 0 else x - w
            for dy, row in enumerate(spr):
                for dx, ch in enumerate(row):
                    if ch != '0':
                        put(x1 + dx, y - 1 + dy, leaf_map[ch], over=False)
    if p.get('cones', 0):
        upper = [n for n in nodes]
        for _ in range(p['cones']):
            x, y = upper[rng.integers(0, len(upper))]
            side = rng.choice([-1, 1])
            cx = x + 2 * side
            for dx, dy, tone in ((0, 0, 6), (1, 0, 6), (0, 1, 5), (1, 1, 5), (0, 2, 4), (1, 2, 4)):
                put(cx + dx, y + 1 + dy, tone)
    alpha = np.where(val >= 0, 255, 0).astype(np.uint8)
    colors = [t.hex_to_rgb(c) for c in p['colors']]
    return t.paint(np.clip(val, 0, len(colors) - 1), colors, alpha)


def rock(tex, palettes):
    """The rocks of the Tatra massif (step TT1), tileable and opaque, as smooth as the vanilla stones (lint kind 'rock':
    the medians of the 10 natural vanilla 26.3 stones). p['form']: 'granite' (the gray granite of the High Tatras: an even
    light gray mass with sparse dark mica and light feldspar grains of one or two pixels), 'gneiss' (the crystalline core
    of the Western Tatras: darker gray foliation, gently wavy bands along the block), 'limestone' (the regle and the
    Belianske Tatras: a pale cream-gray, almost even, faint cloudy patches) or 'dolomite' (a beige-gray, finer sugary
    grain). The colors p['colors'], darkest first; the grains use the darkest and the lightest tone."""
    p = tex.params
    rng = t.Rng(tex.seed)
    n = len(p['colors'])
    form = p['form']
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    if form == 'gneiss':
        warp = 2.2 * (t.value_noise(rng, 4, 2) - 0.5)
        band = 0.5 + 0.5 * np.sin(2 * np.pi * (ys + warp) * p['bands'] / N)
        v = 0.55 * band + 0.3 * t.fbm(rng, 2, 4, 4, 0.6) + p['grain'] * t.value_noise(rng, 16, 16)
    elif form == 'limestone':
        v = t.fbm(rng, 3, 2, 2, 0.5) + p['grain'] * t.value_noise(rng, 16, 16)
    else:
        v = t.fbm(rng, 2, 4, 4, 0.6) + p['grain'] * t.value_noise(rng, 16, 16)
    # Inner tones only for the mass; the darkest and the lightest tone are the grains (or a thin share of the mass).
    shares = p.get('shares')
    idx = t.quantize(v, n, shares)
    idx = t.majority(idx, 1)
    for tone, count in ((0, p.get('dark', 0)), (n - 1, p.get('light', 0))):
        for _ in range(count):
            x, y = rng.integers(0, N), rng.integers(0, N)
            idx[y, x] = tone
            if rng.random() < 0.4:
                idx[y, (x + 1) % N] = tone
    return t.paint(idx, [t.hex_to_rgb(c) for c in p['colors']])
