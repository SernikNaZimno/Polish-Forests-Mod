"""The texture of the common frog (Rana temporaria), step FA4 of the vertical slice (G.62-f, KFA-7): a 48 x 48 texture
painted from scratch for the vanilla frog model, whose box layout (texture offsets and box sizes of FrogModel, the
geometry of the game, read by its numbers) decides where each face lies. No pixel of a vanilla texture is used: the
colors come from field-guide descriptions of the species, the pattern from seeded smooth noise; check.py counts the
texels equal to the vanilla frog textures when the game jar is at hand (expected 0).

The look: olive to warm brown above with dark irregular blotches and lighter dorsolateral folds, the dark temporal patch
behind the eye with a light upper-lip line, dark crossbars on the legs, a cream belly mottled with gray-brown, a pale
throat, golden eyes with a dark horizontal pupil, webbed hind feet.

Faces of a box (u, v, width w, height h, depth d) in Minecraft's cube layout (ModelPart.Cube): top (u+d, v) w x d, rows
from the back to the front; bottom (u+d+w, v) w x d, the same orientation; right side (u, v+d) d x h, the back at the
left edge; front (u+d, v+d) w x h; left side (u+d+w, v+d) d x h, the front at the left edge; back (u+2d+w, v+d) w x h.
"""
import numpy as np

SIZE = 48
SEED = 4041
SOFTEN = 0.6
SOFTEN_PASSES = 3

# FrogModel boxes: name -> (u, v, w, h, d)
BOXES = {
    'body': (3, 1, 7, 3, 9),
    'body_plane': (23, 22, 7, 0, 9),
    'head': (0, 13, 7, 3, 9),
    'head_plane': (23, 13, 7, 0, 9),
    'right_eye': (0, 0, 3, 2, 3),
    'left_eye': (0, 5, 3, 2, 3),
    'croaking_body': (26, 5, 7, 2, 3),
    'tongue': (17, 13, 4, 0, 7),
    'left_arm': (0, 32, 2, 3, 3),
    'right_arm': (0, 38, 2, 3, 3),
    'left_hand': (18, 40, 8, 0, 8),
    'right_hand': (2, 40, 8, 0, 8),
    'left_leg': (14, 25, 3, 3, 4),
    'right_leg': (0, 25, 3, 3, 4),
    'left_foot': (2, 32, 8, 0, 8),
    'right_foot': (18, 32, 8, 0, 8),
}

# Colors (sRGB) from descriptions of the species.
BROWN = np.array([126, 98, 64], float)
WARM = np.array([142, 106, 66], float)
OLIVE = np.array([116, 102, 66], float)
BLOTCH = np.array([70, 52, 38], float)
FOLD = np.array([156, 126, 86], float)
MASK = np.array([62, 44, 32], float)
LIP = np.array([204, 186, 150], float)
CREAM = np.array([214, 200, 166], float)
MOTTLE = np.array([168, 148, 120], float)
THROAT = np.array([222, 212, 184], float)
IRIS = np.array([188, 146, 72], float)
IRIS_DARK = np.array([138, 104, 54], float)
PUPIL = np.array([44, 36, 30], float)
MOUTH = np.array([190, 124, 118], float)
TONGUE = np.array([204, 112, 122], float)
FOOT = np.array([118, 94, 66], float)
WEB = np.array([150, 126, 94], float)
SOLE = np.array([186, 166, 136], float)

# Hind foot and front hand outlines (rows from the back to the front, columns from -X to +X): '#' toe or palm,
# 'w' web, '.' transparent.
FOOT_SHAPE = (
    '...##...',
    '...##...',
    '..####..',
    '.#wwww#.',
    '.#w##w#.',
    '#ww##ww#',
    '#.w#.w.#',
    '...#..#.',
)
HAND_SHAPE = (
    '........',
    '........',
    '...##...',
    '...##...',
    '..####..',
    '.#.##.#.',
    '.#.#.#..',
    '........',
)


def smooth_noise(rng, shape, period):
    """Value noise in [0, 1]: a random grid every `period` texels, bilinear between them (smooth, CP-A)."""
    h, w = shape
    gh, gw = h // period + 2, w // period + 2
    grid = rng.random((gh, gw))
    ys = np.arange(h) / period
    xs = np.arange(w) / period
    y0 = np.floor(ys).astype(int)
    x0 = np.floor(xs).astype(int)
    fy = (ys - y0)[:, None]
    fx = (xs - x0)[None, :]
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    a = grid[y0][:, x0]
    b = grid[y0][:, x0 + 1]
    c = grid[y0 + 1][:, x0]
    d = grid[y0 + 1][:, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def faces(u, v, w, h, d):
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
    }


class Canvas:
    def __init__(self, rng):
        self.rgb = np.zeros((SIZE, SIZE, 3), float)
        self.alpha = np.zeros((SIZE, SIZE), np.uint8)
        self.detail = np.zeros((SIZE, SIZE), bool)          # texels kept sharp (the pupils)
        self.tone = smooth_noise(rng, (SIZE, SIZE), 3)        # warm / olive hue drift
        self.spots = smooth_noise(rng, (SIZE, SIZE), 2)       # blotches
        self.fine = smooth_noise(rng, (SIZE, SIZE), 1)        # small mottling

    def fill(self, rect, fn):
        u, v, w, h = rect
        for y in range(v, v + h):
            for x in range(u, u + w):
                self.rgb[y, x] = fn(x - u, y - v, w, h, x, y)
                self.alpha[y, x] = 255

    def put(self, x, y, color):
        self.rgb[y, x] = color
        self.alpha[y, x] = 255

    def back_color(self, x, y, blotches=True):
        t = self.tone[y, x]
        base = BROWN * (1 - abs(t - 0.5)) + (WARM if t > 0.5 else OLIVE) * abs(t - 0.5)
        base = base * (0.94 + 0.12 * self.fine[y, x])
        if blotches and self.spots[y, x] > 0.62:
            k = min(1.0, (self.spots[y, x] - 0.62) / 0.2)
            k = k * k * (3 - 2 * k)
            base = base * (1 - 0.6 * k) + BLOTCH * 0.6 * k
        return base

    def belly_color(self, x, y, base=CREAM):
        m = self.spots[y, x]
        k = max(0.0, min(1.0, (m - 0.55) / 0.2))
        return base * (1 - 0.6 * k) + MOTTLE * 0.6 * k


def paint():
    """The 48 x 48 RGBA texture (uint8)."""
    rng = np.random.default_rng(SEED)
    c = Canvas(rng)
    back = lambda i, j, w, h, x, y: c.back_color(x, y)  # noqa: E731
    belly = lambda i, j, w, h, x, y: c.belly_color(x, y)  # noqa: E731

    # Body: back with the folds, flanks fading to the belly, cream belly.
    f = faces(*BOXES['body'])

    def body_top(i, j, w, h, x, y):
        col = c.back_color(x, y)
        if i in (1, w - 2):
            col = col * 0.45 + FOLD * 0.55
        return col

    def flank(i, j, w, h, x, y):
        if j == h - 1:
            return c.belly_color(x, y) * 0.7 + c.back_color(x, y, False) * 0.3
        return c.back_color(x, y) if j == 0 else c.back_color(x, y) * 0.85 + MOTTLE * 0.15

    c.fill(f['top'], body_top)
    c.fill(f['bottom'], belly)
    for side in ('right', 'left', 'front', 'back'):
        c.fill(f[side], flank)

    # Head: back color on top, the temporal patch behind the eye and the light upper lip on the sides, pale under the jaw.
    f = faces(*BOXES['head'])
    c.fill(f['top'], back)
    c.fill(f['bottom'], lambda i, j, w, h, x, y: c.belly_color(x, y, THROAT))

    def head_side(i, j, w, h, x, y):
        if j == h - 1:
            return LIP * (0.95 + 0.08 * c.fine[y, x])
        if 3 <= i <= 5:
            return MASK * (0.92 + 0.12 * c.fine[y, x])
        return c.back_color(x, y)

    c.fill(f['right'], head_side)
    c.fill(f['left'], head_side)
    c.fill(f['front'], lambda i, j, w, h, x, y: LIP * (0.95 + 0.08 * c.fine[y, x]) if j == h - 1 else c.back_color(x, y, False))
    c.fill(f['back'], back)

    # Mouth planes, the croaking throat and the tongue.
    for name in ('head_plane', 'body_plane'):
        f = faces(*BOXES[name])
        for side in ('top', 'bottom'):
            c.fill(f[side], lambda i, j, w, h, x, y: MOUTH * (0.92 + 0.12 * c.fine[y, x]))
    f = faces(*BOXES['croaking_body'])
    for side in f:
        c.fill(f[side], lambda i, j, w, h, x, y: c.belly_color(x, y, THROAT))
    f = faces(*BOXES['tongue'])
    for side in ('top', 'bottom'):
        c.fill(f[side], lambda i, j, w, h, x, y: TONGUE * (0.94 + 0.1 * c.fine[y, x]))

    # Eyes: golden iris, a dark horizontal pupil across the upper row of the sides and the front, a brown lid on top.
    for name in ('right_eye', 'left_eye'):
        f = faces(*BOXES[name])
        c.fill(f['top'], lambda i, j, w, h, x, y: c.back_color(x, y, False) * 0.6 + IRIS_DARK * 0.4)
        c.fill(f['bottom'], lambda i, j, w, h, x, y: IRIS_DARK)

        def eye_side(i, j, w, h, x, y):
            if j == 0 and i == w // 2:
                c.detail[y, x] = True
                return PUPIL
            return IRIS if j == 0 else IRIS * 0.6 + IRIS_DARK * 0.4

        for side in ('right', 'front', 'left', 'back'):
            c.fill(f[side], eye_side)

    # Legs and arms: back color with dark crossbars, pale underneath.
    for name in ('left_leg', 'right_leg'):
        f = faces(*BOXES[name])
        c.fill(f['top'], lambda i, j, w, h, x, y: BLOTCH * 1.05 if j in (1, 3) else c.back_color(x, y, False))
        c.fill(f['bottom'], belly)
        for side in ('right', 'left'):
            c.fill(f[side], lambda i, j, w, h, x, y: (BLOTCH * 1.05 if i in (1, 3) and j < h - 1 else
                                                      (c.belly_color(x, y) if j == h - 1 else c.back_color(x, y, False))))
        for side in ('front', 'back'):
            c.fill(f[side], lambda i, j, w, h, x, y: c.belly_color(x, y) if j == h - 1 else c.back_color(x, y, False))
    for name in ('left_arm', 'right_arm'):
        f = faces(*BOXES[name])
        c.fill(f['top'], lambda i, j, w, h, x, y: c.back_color(x, y, False))
        c.fill(f['bottom'], belly)
        for side in ('right', 'front', 'left', 'back'):
            c.fill(f[side], lambda i, j, w, h, x, y: BLOTCH * 1.05 if j == 1 else
                   (c.belly_color(x, y) if j == h - 1 and side in ('right', 'left') else c.back_color(x, y, False)))

    # Feet and hands: shapes on the planes (the same on top and below, the toes to the front), the rest transparent.
    def shape(rect, rows, top):
        u, v, w, h = rect
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == '.':
                    continue
                if top:
                    col = (FOOT if ch == '#' else WEB) * (0.94 + 0.12 * c.fine[v + j, u + i])
                else:
                    col = SOLE * (0.95 + 0.08 * c.fine[v + j, u + i]) * (1.0 if ch == '#' else 1.04)
                c.put(u + i, v + j, col)

    for name, rows in (('left_foot', FOOT_SHAPE), ('right_foot', FOOT_SHAPE), ('left_hand', HAND_SHAPE), ('right_hand', HAND_SHAPE)):
        f = faces(*BOXES[name])
        shape(f['top'], rows, True)
        shape(f['bottom'], rows, False)

    for box in BOXES.values():
        for rect in faces(*box).values():
            soften(c, rect)
    out = np.zeros((SIZE, SIZE, 4), np.uint8)
    out[..., :3] = np.clip(np.round(c.rgb), 0, 255).astype(np.uint8)
    out[..., 3] = c.alpha
    out[c.alpha == 0, :3] = 0
    return out


def soften(c, rect, passes=SOFTEN_PASSES, k=SOFTEN):
    """Blends every opaque texel of a face toward the mean of its opaque 4-neighbours in the same face (the details
    stay), so the pattern reads as soft shading at this low resolution (CP-A)."""
    u, v, w, h = rect
    if w == 0 or h == 0:
        return
    rgb = c.rgb[v:v + h, u:u + w]
    keep = (c.alpha[v:v + h, u:u + w] > 0) & ~c.detail[v:v + h, u:u + w]
    for _ in range(passes):
        acc = np.zeros_like(rgb)
        n = np.zeros((h, w))
        src = c.alpha[v:v + h, u:u + w] > 0
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            ys = slice(max(0, dy), h + min(0, dy))
            yd = slice(max(0, -dy), h + min(0, -dy))
            xs = slice(max(0, dx), w + min(0, dx))
            xd = slice(max(0, -dx), w + min(0, -dx))
            m = src[ys, xs]
            acc[yd, xd] += rgb[ys, xs] * m[..., None]
            n[yd, xd] += m
        mean = np.where(n[..., None] > 0, acc / np.maximum(n, 1)[..., None], rgb)
        rgb = np.where(keep[..., None], rgb * (1 - k) + mean * k, rgb)
    c.rgb[v:v + h, u:u + w] = rgb


def smoothness(img):
    """(mean, 95th percentile, largest) luminance step between opaque 4-neighbours of the same face, the pupils left out;
    validate.py's limits for animal coats apply (SMOOTH_MEAN 6, SMOOTH_P95 24)."""
    lum = img[..., :3].astype(float) @ np.array([0.2126, 0.7152, 0.0722])
    opaque = img[..., 3] > 0
    diffs = []
    for name, box in BOXES.items():
        for side, (u, v, w, h) in faces(*box).items():
            if w == 0 or h == 0:
                continue
            f = lum[v:v + h, u:u + w]
            a = opaque[v:v + h, u:u + w]
            if name.endswith('_eye') and side not in ('top', 'bottom'):
                a = a.copy()
                a[0, w // 2] = False
            if w > 1:
                diffs.extend(np.abs(np.diff(f, axis=1))[a[:, 1:] & a[:, :-1]].ravel())
            if h > 1:
                diffs.extend(np.abs(np.diff(f, axis=0))[a[1:] & a[:-1]].ravel())
    d = np.array(diffs)
    return float(d.mean()), float(np.percentile(d, 95)), float(d.max())


def neighbour_step(img):
    """Mean absolute step between opaque horizontal and vertical neighbours (smoothness, reported by check.py)."""
    rgb = img[..., :3].astype(float)
    a = img[..., 3] > 0
    steps = []
    for dy, dx in ((0, 1), (1, 0)):
        p = rgb[:SIZE - dy, :SIZE - dx]
        q = rgb[dy:, dx:]
        m = a[:SIZE - dy, :SIZE - dx] & a[dy:, dx:]
        steps.append(np.abs(p - q).mean(axis=2)[m])
    s = np.concatenate(steps)
    return float(s.mean()), float(np.percentile(s, 95))
