"""Texture library of tools/art: procedural 16 x 16 block textures in a style close to vanilla Minecraft.

Everything works on a 16 x 16 torus, so every texture tiles without seams. The building blocks:
- Rng: a small deterministic random generator (SplitMix64), independent of the NumPy version, so the same recipe and
  seed give the same pixels on every machine (checkArt compares decoded pixels);
- value_noise, fbm: tileable value noise and fractal noise on a lattice that divides 16;
- worley: tileable Worley cells (F1, F2, cell id), optionally stretched along an axis (bark plates);
- quantize: a scalar field to ramp indices, by equal-count or by explicit shares (every tone is used);
- majority: a majority filter that removes isolated pixels (vanilla pixel art is built of 2-4 px clusters);
- relief: light from the top-left on a height field (vanilla convention);
- stamp: small sprites (leaves, needle tufts) stamped until a target opacity;
- edge_locked: a variant that shares its top and bottom rows with the base texture, so variants stack in any order;
- ramps: palette colors (from tools/art/palettes.json) spread over n tones with a target luminance range.

Images are NumPy arrays of shape (16, 16, 4), dtype uint8, RGBA.
"""
import math

import numpy as np

N = 16
MASK64 = (1 << 64) - 1


class Rng:
    """SplitMix64: deterministic, platform-independent, seeded with an int or a string."""

    def __init__(self, seed):
        if isinstance(seed, str):
            h = 1469598103934665603
            for b in seed.encode('utf-8'):
                h = ((h ^ b) * 1099511628211) & MASK64
            seed = h
        self.state = seed & MASK64

    def next64(self):
        self.state = (self.state + 0x9E3779B97F4A7C15) & MASK64
        z = self.state
        z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & MASK64
        z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & MASK64
        return z ^ (z >> 31)

    def random(self):
        """Float in [0, 1) with 53 random bits."""
        return (self.next64() >> 11) / float(1 << 53)

    def uniform(self, lo, hi):
        return lo + (hi - lo) * self.random()

    def integers(self, lo, hi):
        """Int in [lo, hi)."""
        return lo + int(self.random() * (hi - lo))

    def choice(self, seq):
        return seq[self.integers(0, len(seq))]

    def array(self, shape):
        n = int(np.prod(shape))
        return np.array([self.random() for _ in range(n)], dtype=np.float64).reshape(shape)

    def fork(self, label):
        """An independent generator derived from this one and a label (stable when other draws are added later)."""
        return Rng(f'{self.state}:{label}')


# ---------------------------------------------------------------------------------------------------- noise fields
def grid():
    """Pixel-centre coordinates (ys, xs), each of shape (16, 16)."""
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    return ys + 0.5, xs + 0.5


def value_noise(rng, period_x, period_y):
    """Tileable value noise on a period_x x period_y lattice (periods should divide 16), smoothstep interpolation."""
    lat = rng.array((period_y, period_x))
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float64)
    fx = xs * period_x / N
    fy = ys * period_y / N
    x0 = np.floor(fx).astype(int)
    y0 = np.floor(fy).astype(int)
    tx = fx - x0
    ty = fy - y0
    tx = tx * tx * (3 - 2 * tx)
    ty = ty * ty * (3 - 2 * ty)
    x1 = (x0 + 1) % period_x
    y1 = (y0 + 1) % period_y
    x0 %= period_x
    y0 %= period_y
    a = lat[y0, x0] * (1 - tx) + lat[y0, x1] * tx
    b = lat[y1, x0] * (1 - tx) + lat[y1, x1] * tx
    return a * (1 - ty) + b * ty


def fbm(rng, octaves, base_x=2, base_y=2, gain=0.5):
    """Fractal sum of value noise octaves, normalized to about [0, 1]."""
    out = np.zeros((N, N))
    amp = 1.0
    total = 0.0
    px, py = base_x, base_y
    for _ in range(octaves):
        out += amp * value_noise(rng, px, py)
        total += amp
        amp *= gain
        px = min(px * 2, N)
        py = min(py * 2, N)
    return out / total


def torus_delta(a, b):
    d = np.abs(a - b)
    return np.minimum(d, N - d)


def worley(rng, n_points, stretch_x=1.0, stretch_y=1.0, points=None):
    """Tileable Worley noise. Returns (f1, f2, cell) where f1 and f2 are the distances to the nearest and second nearest
    point and cell the index of the nearest. stretch_y > 1 makes cells taller (distances along y count less)."""
    if points is None:
        points = np.array([[rng.uniform(0, N), rng.uniform(0, N)] for _ in range(n_points)])
    ys, xs = grid()
    d = np.empty((len(points), N, N))
    for i, (px, py) in enumerate(points):
        dx = torus_delta(xs, px) / stretch_x
        dy = torus_delta(ys, py) / stretch_y
        d[i] = np.sqrt(dx * dx + dy * dy)
    order = np.argsort(d, axis=0, kind='stable')
    f1 = np.take_along_axis(d, order[0:1], 0)[0]
    f2 = np.take_along_axis(d, order[1:2], 0)[0]
    return f1, f2, order[0]


def normalize(a):
    a = a - a.min()
    m = a.max()
    return a / m if m > 0 else a


def roll(a, dy, dx):
    """Value of the neighbour at (y - dy, x - dx) on the torus, i.e. roll(a, 1, 0) is the pixel above."""
    return np.roll(np.roll(a, dy, axis=0), dx, axis=1)


# ---------------------------------------------------------------------------------------------------- index maps
def quantize(v, levels, shares=None):
    """Field -> int index 0..levels-1 with thresholds at quantiles, so every ramp tone is used.
    shares: optional list of the fraction of pixels per level (darkest first), e.g. a thin fissure tone."""
    flat = np.sort(v.ravel(), kind='stable')
    if shares is None:
        shares = [1.0 / levels] * levels
    assert len(shares) == levels
    cum = np.cumsum(shares)[:-1] / sum(shares)
    cuts = [flat[min(int(len(flat) * c), len(flat) - 1)] for c in cum]
    return np.digitize(v, cuts)


def neighbours4(idx):
    return [roll(idx, 1, 0), roll(idx, -1, 0), roll(idx, 0, 1), roll(idx, 0, -1)]


def majority(idx, passes=1, keep=()):
    """Remove isolated pixels: a pixel equal to none of its 4 neighbours takes their most common value.
    Values in `keep` are never replaced (e.g. thin fissures or highlights drawn on purpose)."""
    for _ in range(passes):
        nb = np.stack(neighbours4(idx))
        out = idx.copy()
        for y in range(N):
            for x in range(N):
                v = idx[y, x]
                if v in keep:
                    continue
                around = nb[:, y, x]
                if v in around:
                    continue
                vals, counts = np.unique(around, return_counts=True)
                best = counts.max()
                # tie: the value closest to the pixel's own, then the smaller one (deterministic)
                cand = sorted(vals[counts == best], key=lambda c: (abs(int(c) - int(v)), int(c)))
                out[y, x] = cand[0]
        idx = out
    return idx


def isolated_fraction(idx, alpha=None):
    """Share of (opaque) pixels that differ from all 4 neighbours."""
    nb = np.stack(neighbours4(idx))
    iso = np.all(nb != idx[None], axis=0)
    if alpha is not None:
        m = alpha > 0
        return float(iso[m].mean()) if m.any() else 0.0
    return float(iso.mean())


def relief(height, strength=1.0):
    """Lighting from the top-left on a height field: > 0 where the surface faces the light (the pixel is higher than
    its upper-left neighbour), < 0 in shadow. Returned in units of the height difference times strength."""
    return strength * (height - roll(height, 1, 1))


def shift_index(idx, delta, lo, hi):
    return np.clip(idx + delta, lo, hi)


def edge_locked(base_field, other_field, band=3):
    """A variant field equal to base_field in the top and bottom `band` rows and to other_field in the middle, with a
    smooth transition: base and variant then continue into each other when stacked vertically in any order (trunks
    draw a random variant per block). Both fields must already be tileable horizontally."""
    y = np.arange(N) + 0.5
    dist = np.minimum(y, N - y)  # distance to the nearest horizontal edge, 0.5 .. 8
    w = np.clip((dist - band) / 2.5, 0.0, 1.0)  # 0 near the edges, 1 in the middle
    w = w * w * (3 - 2 * w)
    return base_field * (1 - w[:, None]) + other_field * w[:, None]


# ---------------------------------------------------------------------------------------------------- stamping
def stamp(rng, sprites, target_opacity, tone_field=None, base_levels=(1, 2), max_tries=4000):
    """Stamp sprites at random torus positions until `target_opacity` of the pixels are filled.
    Sprite rows are strings: '0' empty, '1' body, '2' highlight (lit edge), '3' shadow, '4' dark body.
    Returns an int map: 0 = empty, otherwise shade index + 1 (shades 0..4 for a 5-tone ramp)."""
    val = np.zeros((N, N), int)
    tries = 0
    while (val > 0).mean() < target_opacity and tries < max_tries:
        tries += 1
        spr = sprites[rng.integers(0, len(sprites))]
        x0, y0 = rng.integers(0, N), rng.integers(0, N)
        lo, hi = base_levels
        base = hi if (tone_field is not None and tone_field[y0, x0] > 0.5) else lo
        for dy, row in enumerate(spr):
            for dx, ch in enumerate(row):
                if ch == '0':
                    continue
                y, x = (y0 + dy) % N, (x0 + dx) % N
                shade = {'1': base, '2': base + 2, '3': max(base - 1, 0), '4': max(base - 2, 0)}[ch]
                val[y, x] = min(shade, 4) + 1
    return val


# ---------------------------------------------------------------------------------------------------- color
def hex_to_rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def rgb_to_hex(c):
    return '#%02x%02x%02x' % tuple(int(v) for v in c)


def luma(rgb):
    """Rec. 709 luma of an RGB triple or an (..., 3) array, 0..255."""
    a = np.asarray(rgb, dtype=np.float64)
    return 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]


def ramp(colors, n, lum_lo=None, lum_hi=None, sat=1.0):
    """Spread palette colors (any order) over n tones from dark to light. The palette is sorted by luma and linearly
    interpolated; with lum_lo / lum_hi the tones are rescaled to that luma range (photo palettes are darker and flatter
    than vanilla pixel art), keeping each tone's hue; sat scales the saturation around the tone's gray value."""
    cols = sorted((np.array(hex_to_rgb(c), dtype=np.float64) for c in colors), key=lambda c: luma(c))
    cols = np.array(cols)
    t = np.linspace(0, len(cols) - 1, n)
    out = []
    for ti in t:
        i = min(int(math.floor(ti)), len(cols) - 2)
        f = ti - i
        out.append(cols[i] * (1 - f) + cols[i + 1] * f)
    out = np.array(out)
    if lum_lo is not None and lum_hi is not None:
        target = np.linspace(lum_lo, lum_hi, n)
        for k in range(n):
            l = luma(out[k])
            out[k] = out[k] * (target[k] / l) if l > 0 else np.full(3, target[k])
    if sat != 1.0:
        for k in range(n):
            g = luma(out[k])
            out[k] = g + (out[k] - g) * sat
    return [tuple(int(round(v)) for v in np.clip(c, 0, 255)) for c in out]


def mean_hue(colors, weights=None):
    """Circular mean hue (0..1) of hex colors, weighted by saturation (gray colors carry no hue) and `weights`."""
    import colorsys
    sx = sy = 0.0
    for i, c in enumerate(colors):
        h, s, v = colorsys.rgb_to_hsv(*(x / 255 for x in hex_to_rgb(c)))
        w = s * (weights[i] if weights else 1.0)
        sx += w * math.cos(2 * math.pi * h)
        sy += w * math.sin(2 * math.pi * h)
    return (math.atan2(sy, sx) / (2 * math.pi)) % 1.0


def ramp_hue(colors, n, lum_lo, lum_hi, sat, hue_drift=0.0, weights=None):
    """n tones of the palette's mean hue (texlib.mean_hue) with a fixed HSV saturation, from luma lum_lo to lum_hi.
    For palettes whose photo crops are washed out by sunlight or shade (light tones turn gray or pink): the photo
    gives the hue, the recipe gives saturation and brightness. hue_drift (in degrees) shifts the light end of the ramp
    (warmer, more yellow light tones with a positive drift); the dark end is shifted by the opposite half."""
    import colorsys
    h0 = mean_hue(colors, weights)
    out = []
    for k in range(n):
        f = k / (n - 1) if n > 1 else 0.0
        h = (h0 + (f - 0.5) * hue_drift / 360.0) % 1.0
        r, g, b = colorsys.hsv_to_rgb(h, sat, 1.0)
        target = lum_lo + (lum_hi - lum_lo) * f
        base = luma((r * 255, g * 255, b * 255))
        scale_ = target / base
        c = np.array([r, g, b]) * 255 * scale_
        if c.max() > 255:  # too light for this saturation: keep the luma, lose some saturation
            g_ = target
            c = g_ + (c - g_) * min(1.0, (255 - g_) / (c.max() - g_))
        out.append(tuple(int(round(v)) for v in np.clip(c, 0, 255)))
    return out


def gray_ramp(n, lo, hi):
    return [(int(round(v)),) * 3 for v in np.linspace(lo, hi, n)]


def paint(idx, colors, alpha=None):
    """Index map -> RGBA array. alpha: None (opaque) or an array of 0/255."""
    img = np.zeros((N, N, 4), np.uint8)
    for k, c in enumerate(colors):
        img[idx == k, :3] = c
    img[..., 3] = 255 if alpha is None else alpha
    return img


def to_gray_tint(img):
    """Remove any hue: R = G = B = luma (textures tinted in game are grayscale)."""
    out = img.copy()
    l = np.round(luma(img[..., :3])).astype(np.uint8)
    for c in range(3):
        out[..., c] = l
    return out
