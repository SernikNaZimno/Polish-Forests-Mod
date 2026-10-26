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
    """A flower bed of a spring geophyte (wood anemone or liverleaf): in each 8 x 8 quadrant (one segment of the bed)
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
