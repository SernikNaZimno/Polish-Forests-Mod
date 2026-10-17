"""Recipes of the LITE Scots pine wood set and of the saplings (step V7 of docs/04-pionowy-wycinek.md, decision D1 b).

Wood descriptions (species descriptions in docs/research/06-lasy-polski-fitosocjologia-stl.md and wood anatomy of
Pinus sylvestris; the tones are set by the recipe, no photo palette): Scots pine wood has a pale yellowish-white to
light yellow sapwood and a clearly darker, reddish-brown heartwood that starts at about 30-40 years; the annual rings
are distinct (light early wood, darker late wood) and the knots are dark red-brown. Planks of pine are a warm light
yellow with a faint orange cast, lighter and more yellow than vanilla spruce planks, pinker than birch.

Saplings (16 x 16, cross model, not tinted, like vanilla saplings): a young plant 30-60 cm tall with the leaf shape of
its species: Scots pine (gray-green needle tufts in a whorl around a short leader), small-leaved lime (rounded, heart
shaped leaves), hornbeam (oval, pleated leaves), beech (oval, glossy, wavy-edged leaves, a lighter green), ash
(pinnate leaves: a stalk with pairs of small leaflets) and Norway maple (palmate, lobed leaves on long stalks). Oak,
birch and spruce use the vanilla sapling textures by id (decision D2, no copy).

The kinds have their own contrast caps in lint.py (CONTRAST, measured on vanilla 26.3 with tools/art/measure.py):
'planks' and 'stripped' tile both ways, 'end' (log tops) does not tile, 'sapling' is a cross plant with transparency.
"""
import math

import numpy as np

import texlib as t
from texlib import N


def hexes(cs):
    return [t.hex_to_rgb(c) for c in cs]


def periodic_noise_1d(rng, period, n=N):
    """A smooth periodic 1D noise over n samples with `period` control points, values in about [-1, 1]."""
    pts = [rng.uniform(-1, 1) for _ in range(period)]
    out = np.zeros(n)
    for i in range(n):
        x = i * period / n
        k = int(math.floor(x))
        f = x - k
        f = f * f * (3 - 2 * f)
        out[i] = pts[k % period] * (1 - f) + pts[(k + 1) % period] * f
    return out


def fold_rare(idx, mask, least=4):
    """Tones used by 1 to least - 1 visible pixels take the nearest tone index used by at least `least` pixels."""
    vals, counts = np.unique(idx[mask], return_counts=True)
    keep = [v for v, c in zip(vals, counts) if c >= least]
    for v, c in zip(vals, counts):
        if c < least:
            near = min(keep, key=lambda k: abs(k - v))
            idx[(idx == v) & mask] = near
    return idx


def score_lint(tex):
    """Score of a candidate: the number of lint errors of the texture (the first seed that passes is kept)."""
    def score(img):
        import lint
        return len(lint.lint_texture(tex.name, img, tex)[0])
    return score


def best(tex, build):
    """The first seed of tex.params['tries'] whose texture passes the lint, else the one with the fewest errors."""
    sc = score_lint(tex)
    out = None
    for k in range(tex.params.get('tries', 1)):
        seed = tex.seed if k == 0 else f'{tex.seed}#{k}'
        img = build(t.Rng(seed))
        s = sc(img)
        if out is None or s < out[0]:
            out = (s, img)
        if s == 0:
            break
    return out[1]


# ---------------------------------------------------------------------------------------------------- planks

def planks(tex, palettes):
    """Planks: four boards of four rows (as vanilla), each with a slightly lighter top row, a darker bottom row (the
    gap to the next board, a mid tone so it is not a hard line) and one butt joint at its own column; the grain runs
    along the board as a smooth periodic noise per row; one board has a small knot."""
    p = tex.params

    def build(rng):
        idx = np.zeros((N, N), int)
        joints = [rng.integers(0, N) for _ in range(4)]
        for b in range(4):
            y0 = 4 * b
            base = periodic_noise_1d(rng, 4)
            for r in range(4):
                y = y0 + r
                g = base + 0.55 * periodic_noise_1d(rng, 8)
                for x in range(N):
                    v = g[x]
                    tone = 2 if v < -0.35 else (3 if v < 0.35 else 4)
                    if r == 0 and tone < 4 and rng.random() < 0.5:
                        tone += 1
                    if r == 3:
                        tone = 1 if v < 0.2 else 2
                    idx[y, x] = tone
            j = joints[b]
            for r in range(3):
                idx[y0 + r, j] = 1
        kb = rng.integers(0, 4)
        kx = rng.integers(0, N)
        for dx, dy in ((0, 1), (1, 1), (0, 2), (1, 2)):
            idx[4 * kb + dy, (kx + dx) % N] = 0 if (dx, dy) == (0, 1) else 1
        idx = fold_rare(idx, np.ones((N, N), bool))
        return t.paint(idx, hexes(p['colors']))
    return best(tex, build)


# ---------------------------------------------------------------------------------------------------- stripped log

def stripped_side(tex, palettes):
    """Side of a stripped log: fine vertical grain of light early wood and slightly darker late wood, a faint resin
    streak; a column value with a slow vertical wobble, low contrast (vanilla stripped logs are the smoothest wood)."""
    p = tex.params

    def build(rng):
        col = periodic_noise_1d(rng, 8)
        wob = [periodic_noise_1d(rng, 4) for _ in range(N)]
        idx = np.zeros((N, N), int)
        for x in range(N):
            for y in range(N):
                v = col[x] + 0.45 * wob[x][y]
                idx[y, x] = 0 if v < -0.55 else (1 if v < -0.05 else (2 if v < 0.5 else 3))
        sx = rng.integers(0, N)
        y0 = rng.integers(0, N)
        for k in range(p['streak']):
            idx[(y0 + k) % N, sx] = 0
        idx = fold_rare(idx, np.ones((N, N), bool))
        return t.paint(idx, hexes(p['colors']))
    return best(tex, build)


# ---------------------------------------------------------------------------------------------------- log ends

def log_end(tex, palettes):
    """Top of a log: annual rings around a pith a little off the center, squarish like vanilla log tops (a blend of the
    Chebyshev and the Euclidean radius), with a wobble; the reddish heartwood inside p['heart'] px, the pale sapwood
    outside it; with p['bark'] a 1 px border of bark tones (from the lower pine bark palette), else a border of the
    stripped side tones."""
    p = tex.params

    def build(rng):
        cx = 7.5 + rng.uniform(-0.6, 0.6)
        cy = 7.5 + rng.uniform(-0.6, 0.6)
        ang = periodic_noise_1d(rng, 6, 64)
        idx = np.zeros((N, N), int)
        for y in range(N):
            for x in range(N):
                dx, dy = x - cx, y - cy
                cheb = max(abs(dx), abs(dy))
                euc = math.hypot(dx, dy)
                a = int((math.atan2(dy, dx) / (2 * math.pi) + 0.5) * 64) % 64
                r = 0.55 * cheb + 0.45 * euc + 0.45 * ang[a]
                ring = (r / p['ring']) % 1.0
                late = ring > p['late']
                if r < 1.0:
                    tone = 0                                    # pith
                elif r < p['heart']:
                    tone = 1 if late else 2                     # heartwood
                else:
                    tone = 3 if late else 4                     # sapwood
                idx[y, x] = tone
        edge = np.zeros((N, N), bool)
        edge[0, :] = edge[-1, :] = edge[:, 0] = edge[:, -1] = True
        idx[edge] = 5
        if p.get('bark'):
            corners = [(0, 0), (0, N - 1), (N - 1, 0), (N - 1, N - 1)]
            # Bark: two tones in short runs along the border.
            run = periodic_noise_1d(rng, 9, 4 * N)
            k = 0
            for x in range(N):
                for (yy, xx) in ((0, x), (N - 1, N - 1 - x)):
                    idx[yy, xx] = 5 if run[k % (4 * N)] < 0.1 else 6
                    k += 1
            for y in range(1, N - 1):
                for (yy, xx) in ((y, 0), (N - 1 - y, N - 1)):
                    idx[yy, xx] = 5 if run[k % (4 * N)] < 0.1 else 6
                    k += 1
            for c in corners:
                idx[c] = 5
        idx = fold_rare(idx, np.ones((N, N), bool))
        return t.paint(idx, hexes(p['colors']))
    return best(tex, build)


# ---------------------------------------------------------------------------------------------------- saplings
# Leaf sprites per species: rows top to bottom; '2' lit edge, '1' leaf, '3' shade, '0' empty.
# Leaf sprites of the saplings, drawn upward from the petiole at the bottom middle ('1' leaf, '2' lit, '3' shade).
# V7 review round 1: larger leaves with the shape of the species (about 85-110 opaque pixels per sapling, as vanilla):
# linden heart-shaped (the notch at the petiole, a pointed apex), Norway maple five-lobed, hornbeam a long oval with
# pleats (lit and plain rows alternating along the veins), beech a plain oval with a lit edge.
SAPLING_LEAVES = {
    'linden': [["00200", "02110", "21111", "11113", "13031"], ["0020", "0211", "2111", "1113", "1301"]],
    'hornbeam': [["020", "212", "111", "212", "113", "010"], ["02", "21", "11", "21", "13", "01"],
                 ["020", "212", "111", "213", "010"]],
    'beech': [["0220", "2211", "2111", "1113", "0130"], ["0220", "2111", "1113", "0130"]],
    'maple': [["00200", "20102", "21112", "31113", "00300"], ["0200", "2012", "2111", "3113", "0030"]],
}


def draw_sprite(val, spr, x, y, flip=False):
    """Stamps a sprite with its bottom-center at (x, y); tones: leaf shade 3, leaf 4, lit 5 (val indices)."""
    h = len(spr)
    w = len(spr[0])
    for dy, row in enumerate(spr):
        for dx, ch in enumerate(row):
            if ch == '0':
                continue
            xx = x - w // 2 + (w - 1 - dx if flip else dx)
            yy = y - h + 1 + dy
            if 0 <= xx < N and 0 <= yy < N:
                val[yy, xx] = {'1': 4, '2': 5, '3': 3}[ch]


def sapling(tex, palettes):
    """A sapling: a stem (tones 1 dark, 2 light) from the bottom middle up to p['top'], a few side shoots, leaves of the
    species at the shoot tips and along the stem (or, for the pine, needle tufts, and for the ash pinnate leaves). The
    plant keeps a 1 px margin at the sides (the edge of a cross quad cuts straight)."""
    p = tex.params
    kind = p['leaf']

    def build(rng):
        val = np.zeros((N, N), int)
        x = 7 + rng.integers(0, 2)
        top = p['top']
        stem = []
        for y in range(N - 1, top - 1, -1):
            stem.append((x, y))
            if y < N - 3 and rng.random() < p['zigzag']:
                x = int(np.clip(x + rng.choice([-1, 1]), 5, 10))
        for (sx, sy) in stem:
            val[sy, sx] = 1 if sy > N - 4 else 2
        tips = [stem[-1]]
        side = rng.choice([-1, 1])
        for k in range(p['shoots']):
            sy = top + 2 + k * p['shoot_gap'] + rng.integers(0, 2)
            if sy >= N - 3:
                break
            sx = [s for s in stem if s[1] == sy][0][0]
            ln = rng.integers(*p['shoot_len'])
            cx, cy = sx, sy
            for s in range(ln):
                cx += side
                if s % 2 == 1:
                    cy -= 1
                if 1 <= cx <= N - 2:
                    val[cy, cx] = 2
            tips.append((cx, cy, side))
            side = -side
        if kind == 'pine':
            pine_fan(rng, val, stem, p)
        elif kind == 'ash':
            ash_leaves(rng, val, tips, p)
        else:
            sprites = SAPLING_LEAVES[kind]
            for tip in tips:
                tx, ty = tip[0], tip[1]
                flip = len(tip) > 2 and tip[2] < 0
                spr = sprites[rng.integers(0, len(sprites))]
                draw_sprite(val, spr, int(np.clip(tx + (tip[2] if len(tip) > 2 else 0), 2, N - 3)), ty, flip)
            for (sx, sy) in stem[p['leaf_from']::p['leaf_every']]:
                if sy <= top + 1:
                    continue
                spr = sprites[rng.integers(0, len(sprites))]
                s = rng.choice([-1, 1])
                draw_sprite(val, spr, int(np.clip(sx + 2 * s, 2, N - 3)), sy, s < 0)
        val[:, 0] = 0
        val[:, N - 1] = 0
        mask = val > 0
        idx = fold_rare(np.clip(val - 1, 0, 9), mask)
        alpha = np.where(mask, 255, 0).astype(np.uint8)
        return t.paint(idx, hexes(p['colors']), alpha)
    return best(tex, build)


def pine_fan(rng, val, stem, p):
    """Young Scots pine (V7 review round 1): a short orange-brown stem (stem tones) ending in a dense fresh tuft of long
    needles fanning upward and outward from the leader top, and a lower whorl of shorter, more level needle rays. A
    needle is darker at its base (tone 3), mid along it (4) and lit at the tip when it faces the top-left light (5). The
    stem above the tuft centre is not drawn (the needles hide it); the side shoots of the generic sapling are ignored."""
    cx, cy = stem[-1][0], stem[-1][1] + p['tuft_at']
    for (sx, sy) in stem:
        if sy < cy:
            val[sy, sx] = 0

    def needle(x, y, tone):
        if 1 <= x <= N - 2 and 0 <= y < N and val[y, x] in (0, 3, 4):
            val[y, x] = max(val[y, x], tone)

    def fan(fx, fy, rays, length, spread, lift):
        for k in range(rays):
            a = -math.pi / 2 + spread * (2 * k / (rays - 1) - 1) + rng.uniform(-0.1, 0.1)
            dx, dy = math.cos(a), math.sin(a) * lift
            ln = length - rng.integers(0, 2) - (1 if abs(dx) > 0.8 else 0)
            for s in range(1, ln + 1):
                x, y = int(round(fx + dx * s)), int(round(fy + dy * s))
                tone = 3 if s == 1 else (5 if s == ln and dx < 0.35 else 4)
                needle(x, y, tone)
    fan(cx, cy, p['rays'], p['ray_len'], p['spread'], 1.0)
    fan(cx, cy + p['whorl_at'] - p['tuft_at'], p['rays'] - 3, p['ray_len'] - 2, p['spread'] + 0.3, 0.6)


def ash_leaves(rng, val, tips, p):
    """Ash: pinnate leaves, a stalk (light stem tone) leaving each tip upward and outward with pairs of 1-2 px
    leaflets and a terminal leaflet."""
    for tip in tips:
        x, y = tip[0], tip[1]
        d = tip[2] if len(tip) > 2 else rng.choice([-1, 1])
        for k in range(4):
            if k % 2 == 0:
                x += d
            y -= 1
            if not (1 <= x <= N - 2 and 0 <= y < N):
                break
            if val[y, x] == 0:
                val[y, x] = 2
            for s, tone in ((-1, 5), (1, 3)):
                for e in (1, 2):
                    xx, yy = x + s * e * (1 if d > 0 else -1), y + (1 if s > 0 else 0)
                    if 1 <= xx <= N - 2 and 0 <= yy < N and val[yy, xx] == 0:
                        val[yy, xx] = (4 if tone == 5 else 3) if e == 2 else (5 if tone == 5 else 4)
        if 0 <= y - 1 < N and 1 <= x <= N - 2:
            val[y - 1, x] = 4
