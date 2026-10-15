"""Style lint of the block textures (step V1 of docs/04-pionowy-wycinek.md, rules measured on vanilla 26.3 in
docs/research/wycinek/research-fauna-grafika.md §4.1).

Rules for every texture:
- size 16 x 16;
- 4-9 colors (distinct RGBA values among the visible pixels);
- textures tinted in game (leaves, plants for the grass tint, needles with a constant color) are grayscale (R = G = B)
  with a mean brightness (luma of visible pixels) of 0.45-0.65;
- tileable textures (bark, leaves) have no seam: the jump across the wrap-around edge is not larger than the largest
  jump between two neighbouring rows or columns inside; variants (Texture.variant_of) also continue into their base
  texture vertically;
- isolated pixels (equal to none of their 4 neighbours) are at most 10% of the visible pixels of bark and 30% of
  leaves (needle tips and leaf highlights are single pixels on purpose), so the texture is built of 2-4 px clusters.
Kind rules:
- leaves: 55-70% of the pixels visible (vanilla 56-67%), alpha only 0 or 255;
- bark: fully opaque;
- plant: alpha only 0 or 255.
Recipe bounds (Texture.expect): mean luma range on 0..255 and the largest share of dark pixels (luma < 50), so that the
lower pine bark keeps readable plates instead of dark furrows (the round 2 prototype was too dark).

Usage: python tools/art/lint.py   (lints the generated textures and the overrides; exit 1 on errors)
"""
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import texlib as t  # noqa: E402

TINT_LUMA = (0.45, 0.65)
LEAF_OPACITY = (0.55, 0.70)
COLORS = (4, 9)
ISOLATED_MAX = {'bark': 0.10, 'leaves': 0.30, 'plant': 1.0}
DARK = 50


def color_ids(img):
    flat = img.reshape(-1, 4).astype(np.int64)
    key = (flat[:, 0] << 24) | (flat[:, 1] << 16) | (flat[:, 2] << 8) | flat[:, 3]
    return key.reshape(img.shape[:2])


def seam_ratio(img, axis):
    """Jump across the wrap-around edge and the largest jump between two neighbouring columns (axis 1) or rows
    (axis 0) inside the texture, as mean absolute differences of luma plus alpha. Returns (wrap, inside_max).
    A seam is a wrap jump larger than every jump inside: a single long furrow on the edge column is not one."""
    l = t.luma(img[..., :3]) + img[..., 3].astype(np.float64)
    d = np.abs(np.diff(l, axis=axis))
    inside = float(d.mean(axis=1 - axis).max())
    if axis == 1:
        wrap = float(np.abs(l[:, 0] - l[:, -1]).mean())
    else:
        wrap = float(np.abs(l[0, :] - l[-1, :]).mean())
    return wrap, inside


def stack_seam(top, bottom):
    """Jump between the bottom row of `top` and the top row of `bottom` (a block stacked under another)."""
    lt = t.luma(top[..., :3])
    lb = t.luma(bottom[..., :3])
    return float(np.abs(lt[-1, :] - lb[0, :]).mean())


def lint_texture(name, img, tex, others=None):
    """List of error strings for one texture (empty when it passes) and a dict of measured values."""
    err = []
    m = {}
    if img.shape[:2] != (16, 16):
        return [f'{name}: size {img.shape[1]} x {img.shape[0]}, expected 16 x 16'], m
    a = img[..., 3]
    vis = a > 0
    m['opacity'] = float(vis.mean())
    if not vis.any():
        return [f'{name}: no visible pixels'], m
    ids = color_ids(img)
    m['colors'] = int(len(np.unique(ids[vis])))
    if not COLORS[0] <= m['colors'] <= COLORS[1]:
        err.append(f'{name}: {m["colors"]} colors, allowed {COLORS[0]}-{COLORS[1]}')
    if not np.isin(a, (0, 255)).all():
        err.append(f'{name}: semi-transparent pixels (alpha must be 0 or 255)')
    l = t.luma(img[..., :3])
    m['luma'] = float(l[vis].mean())
    m['dark'] = float((l[vis] < DARK).mean())
    if tex.tint:
        rgb = img[vis][:, :3].astype(int)
        if not ((rgb[:, 0] == rgb[:, 1]) & (rgb[:, 1] == rgb[:, 2])).all():
            err.append(f'{name}: tinted texture ({tex.tint}) is not grayscale')
        lo, hi = TINT_LUMA
        if not lo <= m['luma'] / 255 <= hi:
            err.append(f'{name}: mean brightness {m["luma"] / 255:.3f} of a tinted texture, allowed {lo}-{hi}')
    if tex.kind == 'leaves':
        lo, hi = LEAF_OPACITY
        if not lo <= m['opacity'] <= hi:
            err.append(f'{name}: {m["opacity"]:.0%} of the pixels visible, leaves need {lo:.0%}-{hi:.0%}')
    if tex.kind == 'bark' and not vis.all():
        err.append(f'{name}: bark must be fully opaque')
    m['isolated'] = t.isolated_fraction(ids, a)
    if m['isolated'] > ISOLATED_MAX[tex.kind]:
        err.append(f'{name}: {m["isolated"]:.0%} isolated pixels, at most {ISOLATED_MAX[tex.kind]:.0%} for {tex.kind}')
    if tex.kind in ('bark', 'leaves'):
        for axis, label in ((1, 'left-right'), (0, 'top-bottom')):
            wrap, inside = seam_ratio(img, axis)
            m[f'seam_{label}'] = (round(wrap, 1), round(inside, 1))
            if wrap > 1.15 * inside + 4:
                err.append(f'{name}: visible {label} seam when tiled (edge jump {wrap:.1f}, at most {inside:.1f} inside)')
    if tex.variant_of and others is not None and tex.variant_of in others:
        base = others[tex.variant_of]
        inside = seam_ratio(img, 0)[1]
        for top, bottom, label in ((base, img, f'{tex.variant_of} above {name}'), (img, base, f'{name} above {tex.variant_of}')):
            j = stack_seam(top, bottom)
            if j > 1.15 * inside + 4:
                err.append(f'{name}: visible seam with {label} (edge jump {j:.1f}, at most {inside:.1f} inside)')
    exp = tex.expect or {}
    if 'luma' in exp:
        lo, hi = exp['luma']
        if not lo <= m['luma'] <= hi:
            err.append(f'{name}: mean luma {m["luma"]:.0f}, recipe expects {lo}-{hi}')
    if 'dark' in exp and m['dark'] > exp['dark']:
        err.append(f'{name}: {m["dark"]:.0%} dark pixels (luma < {DARK}), recipe allows at most {exp["dark"]:.0%}')
    return err, m


def lint_all(built):
    errors = []
    report = {}
    imgs = {n: b['img'] for n, b in built.items()}
    for name, b in built.items():
        e, m = lint_texture(name, b['img'], b['tex'], imgs)
        errors += e
        report[name] = m
    return errors, report


def format_report(report):
    lines = []
    for name, m in report.items():
        lines.append(f'  {name:24s} colors {m.get("colors", "?"):>2}  visible {m.get("opacity", 0):4.0%}  '
                     f'luma {m.get("luma", 0):5.1f}  dark {m.get("dark", 0):4.0%}  isolated {m.get("isolated", 0):4.0%}')
    return '\n'.join(lines)


def main():
    import generate
    built = generate.build_all()
    errors, report = lint_all(built)
    print(format_report(report))
    if errors:
        print('lint: FAILED')
        for e in errors:
            print('  ' + e)
        sys.exit(1)
    print(f'lint: {len(built)} textures pass the style rules')


if __name__ == '__main__':
    main()
