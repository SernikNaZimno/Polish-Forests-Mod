"""Self-test of tools/art (run by check.py, so also by checkArt): the generator is deterministic, the noise tiles, the
variants stack, and the style lint catches the faults it is meant to catch.

Usage: python tools/art/selftest.py
"""
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import generate  # noqa: E402
import lint  # noqa: E402
import recipes  # noqa: E402
import texlib as t  # noqa: E402

N = t.N


def wrap_jump(a, axis):
    d = np.abs(np.diff(a, axis=axis)).mean()
    w = np.abs(np.take(a, 0, axis=axis) - np.take(a, -1, axis=axis)).mean()
    return w, d


def run():
    failures = []

    def check(cond, msg):
        if not cond:
            failures.append(msg)

    # 1. the random generator is fixed (SplitMix64): the same numbers on every machine and NumPy version
    check(t.Rng('polishforests').next64() == 15182256966461542315, 'Rng: string seed gives other numbers')
    check(abs(t.Rng(42).random() - 0.7415648787718233) < 1e-15, 'Rng: int seed gives other numbers')

    # 2. noise fields tile: the jump across the edge is like the jumps inside
    rng = t.Rng('selftest')
    for name, field in (('value_noise', t.value_noise(rng, 4, 8)), ('fbm', t.fbm(rng, 3, 2, 2)),
                        ('worley', t.worley(rng, 9, stretch_y=2.5)[1])):
        for axis in (0, 1):
            w, d = wrap_jump(field, axis)
            check(w < 2.5 * d + 1e-9, f'{name}: does not tile along axis {axis} (edge {w:.3f}, inside {d:.3f})')

    # 3. building the textures twice gives the same pixels
    a = generate.build_all()
    b = generate.build_all()
    for name in a:
        check(np.array_equal(a[name]['img'], b[name]['img']), f'{name}: two runs differ')

    # 4. variants share the top and bottom row with their base texture (they stack in any order)
    for name, x in a.items():
        base = x['tex'].variant_of
        if base:
            check(np.array_equal(x['img'][0], a[base]['img'][0]) and np.array_equal(x['img'][-1], a[base]['img'][-1]),
                  f'{name}: edge rows differ from {base}')

    # 5. the lint catches what it should
    bark = recipes.Texture('test_bark', 'bark', None, 'x')
    leaves = recipes.Texture('test_leaves', 'leaves', None, 'x', tint='foliage')
    good = a['oak_bark']['img']

    def errs(img, tex):
        return lint.lint_texture(tex.name, img, tex)[0]

    check(not errs(good, bark), 'lint: a generated bark texture fails')
    check(any('size' in e for e in errs(np.concatenate([good, good], axis=0), bark)), 'lint: misses a 16 x 32 texture')
    many = good.copy()
    many[0, :12, :3] = np.arange(12)[:, None] * 7 + 3
    check(any('colors' in e for e in errs(many, bark)), 'lint: misses too many colors')
    ys, xs = np.mgrid[0:N, 0:N]
    ramp = np.zeros((N, N, 4), np.uint8)
    ramp[..., :3] = (40 + ys * 10)[..., None]
    ramp[..., 3] = 255
    ramp = np.round(ramp / 30).astype(np.uint8) * 30
    ramp[..., 3] = 255
    check(any('seam' in e for e in errs(ramp, bark)), 'lint: misses a top-bottom seam')
    # a smooth texture that does not tile: periods 23 and 19 px do not divide 16 (like a crop of a larger photo)
    crop = 0.5 + 0.25 * np.sin(2 * np.pi * (xs + 3) / 23) + 0.25 * np.cos(2 * np.pi * (ys + 5) / 19 + 0.6 * np.sin(xs / 5))
    q = t.quantize(crop, 6)
    noise = t.paint(q, [(70 + 22 * k, 62 + 20 * k, 50 + 16 * k) for k in range(6)])
    check(any('seam' in e for e in errs(noise, bark)), 'lint: misses the seam of a cropped, non-tiling noise bark')
    lv = a['broadleaf_leaves']['img']
    cut = lv.copy()                                     # leaves cut at the wrap: last row full, first row empty
    cut[N - 1, :, :3], cut[N - 1, :, 3] = lv[N // 2, :, :3].max(axis=0), 255
    cut[0, :, 3] = 0
    cut[0, :, :3] = 0
    check(any('seam' in e for e in errs(cut, leaves)), 'lint: misses the seam of a non-tileable leaves texture')
    check(not any('seam' in e for e in errs(lv, leaves)), 'lint: a seam in the generated broadleaf leaves')
    # the core rules for tinted textures and leaves (V1 plan): grayscale, 55-70% visible, alpha 0/255, brightness
    colored = lv.copy()
    colored[..., 0] = np.where(colored[..., 3] > 0, np.minimum(colored[..., 0].astype(int) + 20, 255), 0)
    check(any('grayscale' in e for e in errs(colored, leaves)), 'lint: misses a colored texture under a tint')
    sparse = lv.copy()
    sparse[:8, :, 3] = 0
    check(any('visible' in e for e in errs(sparse, leaves)), 'lint: misses too sparse leaves')
    soft = lv.copy()
    soft[0, 0, 3] = 128
    check(any('semi-transparent' in e for e in errs(soft, leaves)), 'lint: misses semi-transparent pixels')
    dark = (lv.astype(int) // 3).astype(np.uint8)
    dark[..., 3] = lv[..., 3]
    check(any('brightness' in e for e in errs(dark, leaves)), 'lint: misses a too dark tinted texture')
    # banding: a light band across rows 3-4 (as the first "mirror" had) and a dark crack row on every block
    band = good.copy()
    band[3:5, :, :3] = np.minimum(band[3:5, :, :3].astype(int) + 45, 255)
    check(any('banding' in e for e in errs(band, bark)), 'lint: misses horizontal banding at the block period')
    ring = good.copy()                                  # a dark cross fissure over one face of a radius 1 trunk
    ring[9, 4:6, :3] = 30
    check(any('rings' in e or 'ring at every block' in e for e in errs(ring, bark)),
          'lint: misses a ring at every block on a thin trunk (bamboo)')
    loud = good.copy()                                  # too much contrast: every other column near black
    loud[:, ::2, :3] = 20
    check(any('standard deviation' in e for e in errs(loud, bark)), 'lint: misses a too contrasted bark')
    speck = good.copy()                                 # a lone bright spot of 2 px repeats on every block
    speck[7, 7:9, :3] = (250, 170, 110)
    check(any('fewer than' in e for e in errs(speck, bark)), 'lint: misses a color used by 1-3 pixels')
    flat = np.zeros((N, N, 4), np.uint8)                # an andesite-like gray mottle without grain
    flat[..., :3] = (126 + 12 * t.quantize(t.value_noise(t.Rng('selftest/stone'), 4, 4), 4))[..., None]
    flat[..., 3] = 255
    check(any('reads as' in e for e in errs(flat, bark)), 'lint: misses a bark that reads as stone')
    plated = recipes.Texture('test_plated', 'bark', None, 'x', expect={'grain': 2.0})
    check(any('grain' in e for e in errs(np.ascontiguousarray(good.transpose(1, 0, 2)), plated)),
          'lint: misses a plated bark without vertical grain')

    # 6. local contrast (step V1b): the measure, and the lint catches the sharp style of V1 (near-black one-pixel
    # fissures between light plates) and too sharp leaves
    flat_c = t.local_contrast(np.full((N, N, 4), 128, np.uint8))
    check(flat_c['mean'] == 0 and flat_c['p90'] == 0 and flat_c['lines'] == 0, 'local_contrast: a flat texture is not 0')
    checker = np.zeros((N, N, 4), np.uint8)
    checker[..., :3] = (((ys + xs) % 2) * 100 + 50)[..., None]
    checker[..., 3] = 255
    cc = t.local_contrast(checker)
    check(abs(cc['mean'] - 100) < 1e-9 and abs(cc['lines'] - 0.5) < 1e-9,
          f'local_contrast: a 50/150 checkerboard gives mean {cc["mean"]:.1f} and lines {cc["lines"]:.2f}, not 100 and 0.5')
    sharp = good.copy()                                 # V1 style: a near-black fissure every 4 columns
    sharp[:, ::4, :3] = (40, 30, 22)
    check(any('one-pixel lines' in e for e in errs(sharp, bark)), 'lint: misses dark one-pixel fissures')
    check(any('too sharp' in e for e in errs(sharp, bark)), 'lint: misses a too sharp bark (local contrast)')
    hard = lv.copy()                                    # leaves with black and white pixels side by side
    vis = hard[..., 3] > 0
    hard[..., :3] = np.where((((ys + xs) % 2 == 0) & vis)[..., None], 40, 220)
    check(any('too sharp' in e for e in errs(hard, leaves)), 'lint: misses too sharp leaves')

    # 7. the pine transition zone (V1b review round 1): the lint catches a straight seam between two different barks of
    # one trunk (the "mirror" right on the lower bark, the seam the transition zone exists for), the generated zone has
    # none, and the lower stage's bottom row is the lower bark's (it continues into the lower bark below it)
    mirror = recipes.Texture('test_mirror', 'bark', None, 'x', expect={'below': ('scots_pine_bark',)})
    check(any('straight seam' in e for e in lint.lint_texture('test_mirror', a['scots_pine_upper_bark']['img'], mirror,
                                                                {n: x['img'] for n, x in a.items()})[0]),
          'lint: misses the straight seam of the "mirror" right on the lower pine bark')
    imgs = {n: x['img'] for n, x in a.items()}
    for name in ('scots_pine_transition_bark', 'scots_pine_transition_upper_bark'):
        e = lint.lint_texture(name, imgs[name], a[name]['tex'], imgs)[0]
        check(not any('straight seam' in x for x in e), f'{name}: a straight seam in the transition zone: {e}')
    trans, lower = imgs['scots_pine_transition_bark'], imgs['scots_pine_bark']
    check(np.mean(np.any(trans[N - 1] != lower[N - 1], axis=1)) <= 0.25,
          'scots_pine_transition_bark: the bottom row is not the one of the lower bark (it must continue into it)')
    # 8. roughness order (V1b review round 1): the lint catches a linden smoother than the hornbeam
    smooth = np.zeros((N, N, 4), np.uint8)
    smooth[..., :3] = 90
    smooth[..., 3] = 255
    linden = recipes.TEXTURES_BY_NAME['linden_bark']
    check(any('not rougher' in e for e in lint.lint_texture('linden_bark', smooth, linden, imgs)[0]),
          'lint: misses a linden bark smoother than the hornbeam')
    return failures


def main():
    failures = run()
    if failures:
        print('selftest: FAILED')
        for f in failures:
            print('  ' + f)
        sys.exit(1)
    print('selftest: tools/art passes its self-test')


if __name__ == '__main__':
    main()
