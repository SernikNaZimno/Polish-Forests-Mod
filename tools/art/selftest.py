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
    ys = np.mgrid[0:N, 0:N][0]
    ramp = np.zeros((N, N, 4), np.uint8)
    ramp[..., :3] = (40 + ys * 10)[..., None]
    ramp[..., 3] = 255
    ramp = np.round(ramp / 30).astype(np.uint8) * 30
    ramp[..., 3] = 255
    check(any('seam' in e for e in errs(ramp, bark)), 'lint: misses a top-bottom seam')
    lv = a['broadleaf_leaves']['img']
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
