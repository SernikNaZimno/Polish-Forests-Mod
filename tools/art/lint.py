"""Style lint of the block textures (step V1 of docs/04-pionowy-wycinek.md, rules measured on vanilla 26.3 in
docs/research/wycinek/research-fauna-grafika.md §4.1 and in the round 1 review of V1).

Rules for every texture:
- size 16 x 16;
- 4-9 colors (distinct RGBA values among the visible pixels), each used by at least 4 pixels (a color on 1-3 pixels
  is a lone highlight or speck that repeats as a motif on every block);
- textures tinted in game (leaves, plants for the grass tint, needles with a constant color) are grayscale (R = G = B)
  with a mean brightness (luma of visible pixels) of 0.45-0.65;
- tileable textures (bark, leaves) have no seam: the jump across the wrap-around edge is compared with a robust
  measure of the jumps between neighbouring rows (columns) inside: it may not exceed their 75th percentile + 1.5
  standard deviations + a margin. Opaque textures use the luma jump; textures with transparency use the luma jump of
  the pixels opaque on both sides (compared with all such pixel jumps inside, a 3 sigma test, as a row has only a few
  of them) and, separately, the share of pixels whose alpha differs. A tileable texture
  passes; a cropped, non-tiling noise texture fails in about 4 of 5 cases (probe of 400 each). For sparse leaves the
  measure is weak (it catches gross seams, not every shifted half), so a hand-made leaves override also needs a look
  at the 3 x 3 tiles of tools/art/sheet.py. Variants (Texture.variant_of) also continue into their base texture
  vertically;
- isolated pixels (equal to none of their 4 neighbours) are at most 10% of the visible pixels of bark and 30% of
  leaves (needle tips and leaf highlights are single pixels on purpose), so the texture is built of 2-4 px clusters;
- local contrast (step V1b, checkpoint CP-A: "a bit too sharp"; texlib.local_contrast, tools/art/measure.py): the mean
  and the 90th percentile of the luma steps between neighbouring pixels and the share of pixels in dark one-pixel lines
  stay under caps measured on vanilla 26.3 (CONTRAST: bark about the upper quartile of the natural logs, leaves and
  plants about the median; Texture.expect['contrast'] may set other caps).
Kind rules:
- leaves: 55-70% of the pixels visible (vanilla 56-67%), alpha only 0 or 255; with expect['min_line'] every column and
  row has at least that many visible pixels (vanilla oak 9 of 16; a nearly empty column shows as a gap that repeats at
  every block of a leaf wall, V3 review round 1);
- bark: fully opaque; no horizontal banding at the block period (the standard deviation of the row means of luma at
  most 5 over the full width and at most 9 over the crops that thin trunks of radius 2-5 show in game; vanilla oak,
  spruce and acacia logs 1.6-3.4 and 3.9-7.4), luma standard deviation at most 28 (vanilla logs 9-21), and not read as
  stone: a mean color at least 8 (CIE76 delta E) from the vanilla stones, unless the texture has a clear vertical grain
  (texlib.grain >= 2);
  rings: the four crops a thin trunk of radius 1-7 shows in game (texlib.TRUNK_OFFSETS, as TrunkTemplates.OFFSETS)
  have no single row much darker or lighter than the rows around it (texlib.ring_drop, caps texlib.RING_CAPS: 32 at
  radius 1, 26 above): a cross fissure or a pale flake over the whole width of a narrow crop reads as a ring at every
  block ("bamboo", V2 review); the full-width banding measure does not see it;
- plant: alpha only 0 or 255.
Recipe bounds (Texture.expect): 'luma' mean luma range on 0..255, 'dark' the largest share of dark pixels (luma < 50),
'grain' the smallest |dx| / |dy| (vertical grain of furrowed and plated bark; vanilla oak and spruce logs about 3.3),
'std', 'rows', 'slices' other caps for the bark rules above, 'hue' the range of the mean hue in degrees, 'near'
(vanilla texture, largest delta E) and 'distinct' (another texture, smallest delta E).

Usage: python tools/art/lint.py   (lints the generated textures and the overrides; exit 1 on errors)
"""
import colorsys
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import texlib as t  # noqa: E402

TINT_LUMA = (0.45, 0.65)
LEAF_OPACITY = (0.55, 0.70)
COLORS = (4, 9)
MIN_COLOR_PIXELS = 4
ISOLATED_MAX = {'bark': 0.10, 'leaves': 0.30, 'plant': 1.0}
DARK = 50
# Local contrast (step V1b, checkpoint CP-A: "the textures look a bit too sharp"; texlib.local_contrast): the largest
# mean and 90th percentile of the luma steps between neighbouring pixels and the largest share of dark one-pixel lines.
# Measured with tools/art/measure.py on vanilla 26.3 (client jar unpacked outside the repository, only the numbers
# kept): bark = the sides of the 9 natural logs (acacia, cherry, dark oak, jungle, mangrove, oak, pale oak, poplar,
# spruce; birch's black lenticels are lines on purpose): mean 7.2-19.8 (quartiles 8.1 / 9.1 / 13.1), p90 15.7-48.5
# (19.6 / 21.8 / 32.6), lines 0-21% (0.4 / 1.2 / 3.1%): the caps are about the upper quartile. Leaves (the 7 grayscale
# vanilla leaves, before the tint): mean 19.9-43.1 (median 28.3), p90 42.9-74.4 (median 60.9), lines 1.4-25.7% (median
# 16.2%); plants (bush, fern, short grass, sweet berry bush 1-2): mean 18.0-27.2 (median 22.3), p90 41.0-53.7 (q3
# 49.9), lines 3.6-13.1% (median 6.7%): the caps are about the median (the third quartile for the plant p90).
# Before V1b our furrowed and plated barks had a mean of 18-24, a p90 of 54-67 and 17-26% lines, the needles a p90 of 110.
CONTRAST = {'bark': (13.0, 33.0, 0.04), 'leaves': (29.0, 61.0, 0.16), 'plant': (23.0, 50.0, 0.07)}
BANDING = (5.0, 9.0)
BARK_STD = 28.0
STONE_DELTA_E = 8.0
STONE_GRAIN = 2.0
# Mean sRGB of vanilla 26.3 block textures, measured from the client jar (only these numbers are kept, no texture).
VANILLA_MEAN = {
    'oak_log': (109.1, 85.1, 50.7),
    'stone': (125.5, 125.5, 125.5),
    'smooth_stone': (158.7, 158.7, 158.7),
    'andesite': (136.2, 136.3, 136.8),
    'polished_andesite': (132.3, 134.8, 134.0),
    'diorite': (188.7, 188.5, 189.0),
    'calcite': (223.5, 224.5, 220.7),
    'tuff': (108.1, 109.1, 102.6),
}
STONES = ('stone', 'smooth_stone', 'andesite', 'polished_andesite', 'diorite', 'calcite', 'tuff')


def color_ids(img):
    flat = img.reshape(-1, 4).astype(np.int64)
    key = (flat[:, 0] << 24) | (flat[:, 1] << 16) | (flat[:, 2] << 8) | flat[:, 3]
    return key.reshape(img.shape[:2])


def robust_limit(inside, margin):
    return float(np.percentile(inside, 75) + 1.5 * inside.std() + margin)


def seam_check(img, axis):
    """Seams across the wrap-around edge between the last and the first column (axis 1) or row (axis 0).
    Returns a list of (measure, wrap value, limit) that exceed their limit (empty when the texture tiles)."""
    if axis == 1:
        img = img.transpose(1, 0, 2)
    l = t.luma(img[..., :3])
    a = img[..., 3] > 0
    opaque = bool(a.all())
    lum, alpha = [], []
    for k in range(t.N):
        k2 = (k + 1) % t.N
        both = a[k] & a[k2]
        lum.append(np.abs(l[k] - l[k2])[both])
        alpha.append(float(np.mean(a[k] != a[k2])))
    out = []
    if opaque:
        inside = np.array([j.mean() for j in lum[:-1]])
        wrap, lim = float(lum[-1].mean()), robust_limit(inside, 3.0)
    else:
        # few opaque pairs per row: compare the wrap mean with the pooled pixel jumps inside (z-test, 3 sigma)
        pooled = np.concatenate(lum[:-1])
        n = max(len(lum[-1]), 1)
        wrap = float(lum[-1].mean()) if len(lum[-1]) else 0.0
        lim = float(pooled.mean() + 3 * pooled.std() / np.sqrt(n) + 6.0)
    if wrap > lim:
        out.append(('luma jump', wrap, lim))
    if not opaque:
        lim = robust_limit(np.array(alpha[:-1]), 0.05)
        if alpha[-1] > lim:
            out.append(('alpha mismatch', alpha[-1], lim))
    return out


def stack_seam(top, bottom):
    """Jump between the bottom row of `top` and the top row of `bottom` (a block stacked under another)."""
    lt = t.luma(top[..., :3])
    lb = t.luma(bottom[..., :3])
    return float(np.abs(lt[-1, :] - lb[0, :]).mean())


def mean_hue(img):
    return colorsys.rgb_to_hsv(*(t.mean_rgb(img) / 255.0))[0] * 360.0


def lint_texture(name, img, tex, others=None):
    """List of error strings for one texture (empty when it passes) and a dict of measured values.
    others: name -> image of the other textures (variants and 'distinct')."""
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
    vals, counts = np.unique(ids[vis], return_counts=True)
    m['colors'] = int(len(vals))
    if not COLORS[0] <= m['colors'] <= COLORS[1]:
        err.append(f'{name}: {m["colors"]} colors, allowed {COLORS[0]}-{COLORS[1]}')
    if counts.min() < MIN_COLOR_PIXELS:
        rare = ', '.join(f'#{int(v) >> 8:06x} ({c} px)' for v, c in zip(vals, counts) if c < MIN_COLOR_PIXELS)
        err.append(f'{name}: colors used by fewer than {MIN_COLOR_PIXELS} pixels (a lone spot repeats on every block): '
                   f'{rare}')
    if not np.isin(a, (0, 255)).all():
        err.append(f'{name}: semi-transparent pixels (alpha must be 0 or 255)')
    l = t.luma(img[..., :3])
    m['luma'] = float(l[vis].mean())
    m['dark'] = float((l[vis] < DARK).mean())
    m['std'] = float(l[vis].std())
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
        m['cols_min'], m['rows_min'], _, _ = t.line_cover(a)
        need = (tex.expect or {}).get('min_line')
        if need and min(m['cols_min'], m['rows_min']) < need:
            err.append(f'{name}: a column or row with {min(m["cols_min"], m["rows_min"])} of {t.N} pixels visible, at least '
                       f'{need} (an empty line repeats at every block of a leaf wall)')
    m['isolated'] = t.isolated_fraction(ids, a)
    if m['isolated'] > ISOLATED_MAX[tex.kind]:
        err.append(f'{name}: {m["isolated"]:.0%} isolated pixels, at most {ISOLATED_MAX[tex.kind]:.0%} for {tex.kind}')
    lc = t.local_contrast(img)
    m['contrast'] = lc
    cap_mean, cap_p90, cap_lines = (tex.expect or {}).get('contrast', CONTRAST[tex.kind])
    if lc['mean'] > cap_mean or lc['p90'] > cap_p90:
        err.append(f'{name}: too sharp: luma steps between neighbouring pixels mean {lc["mean"]:.1f}, 90th percentile '
                   f'{lc["p90"]:.1f}; at most {cap_mean} and {cap_p90} for {tex.kind} (vanilla 26.3, CP-A)')
    if lc['lines'] > cap_lines:
        err.append(f'{name}: {lc["lines"]:.1%} of the pixels in dark one-pixel lines ({t.LINE_STEP:.0f} darker than both '
                   f'neighbours across), at most {cap_lines:.0%} for {tex.kind}: use a mid tone for fissures and twigs')
    if tex.kind in ('bark', 'leaves'):
        for axis, label in ((1, 'left-right'), (0, 'top-bottom')):
            for what, wrap, lim in seam_check(img, axis):
                err.append(f'{name}: visible {label} seam when tiled ({what} {wrap:.2f} across the edge, at most '
                           f'{lim:.2f} from the jumps inside)')
    exp = tex.expect or {}
    m['grain'] = t.grain(img)
    if tex.kind == 'bark':
        if not vis.all():
            err.append(f'{name}: bark must be fully opaque')
        m['rows'], m['slices'] = t.banding(img)
        cap_rows, cap_slices = exp.get('rows', BANDING[0]), exp.get('slices', BANDING[1])
        if m['rows'] > cap_rows or m['slices'] > cap_slices:
            err.append(f'{name}: horizontal banding at the block period (std of the row means {m["rows"]:.1f} over the '
                       f'full width, {m["slices"]:.1f} over thin-trunk slices; at most {cap_rows} and {cap_slices})')
        m['rings'] = t.rings(img)
        caps = exp.get('rings', t.RING_CAPS)
        bad = [f'radius {r} {v:.0f} > {caps[r]:.0f}' for r, v in m['rings'].items() if v > caps[r]]
        if bad:
            err.append(f'{name}: dark or light rows across the crops of thin trunks, a ring at every block ("bamboo"; the '
                       f'largest single-row step of the crops of TrunkTemplates: {", ".join(bad)})')
        cap = exp.get('std', BARK_STD)
        if m['std'] > cap:
            err.append(f'{name}: luma standard deviation {m["std"]:.1f}, at most {cap} (too much contrast)')
        mean = t.mean_rgb(img)
        if m['grain'] < STONE_GRAIN:
            for stone in STONES:
                de = t.delta_e(mean, VANILLA_MEAN[stone])
                if de < STONE_DELTA_E:
                    err.append(f'{name}: reads as {stone} (mean color delta E {de:.1f} < {STONE_DELTA_E} and no '
                               f'vertical grain, |dx|/|dy| {m["grain"]:.2f} < {STONE_GRAIN})')
    if tex.variant_of and others is not None and tex.variant_of in others:
        base = others[tex.variant_of]
        lv = t.luma(img[..., :3])
        inside = np.abs(np.diff(lv, axis=0)).mean(axis=1)
        lim = robust_limit(inside, 3.0)
        for top, bottom, label in ((base, img, f'{tex.variant_of} above {name}'), (img, base, f'{name} above {tex.variant_of}')):
            j = stack_seam(top, bottom)
            if j > lim:
                err.append(f'{name}: visible seam with {label} (edge jump {j:.1f}, at most {lim:.1f})')
    if 'luma' in exp:
        lo, hi = exp['luma']
        if not lo <= m['luma'] <= hi:
            err.append(f'{name}: mean luma {m["luma"]:.0f}, recipe expects {lo}-{hi}')
    if 'dark' in exp and m['dark'] > exp['dark']:
        err.append(f'{name}: {m["dark"]:.0%} dark pixels (luma < {DARK}), recipe allows at most {exp["dark"]:.0%}')
    if 'grain' in exp and m['grain'] < exp['grain']:
        err.append(f'{name}: vertical grain |dx|/|dy| {m["grain"]:.2f}, recipe expects at least {exp["grain"]}')
    if 'hue' in exp:
        lo, hi = exp['hue']
        m['hue'] = mean_hue(img)
        if not lo <= m['hue'] <= hi:
            err.append(f'{name}: mean hue {m["hue"]:.0f} degrees, recipe expects {lo}-{hi}')
    if 'near' in exp:
        ref, most = exp['near']
        de = t.delta_e(t.mean_rgb(img), VANILLA_MEAN[ref])
        m['near'] = de
        if de > most:
            err.append(f'{name}: mean color delta E {de:.1f} from vanilla {ref}, recipe allows at most {most}')
    if 'distinct' in exp and others is not None and exp['distinct'][0] in others:
        other, least = exp['distinct']
        de = t.delta_e(t.mean_rgb(img), t.mean_rgb(others[other]))
        m['distinct'] = de
        if de < least:
            err.append(f'{name}: mean color delta E {de:.1f} from {other}, recipe needs at least {least}')
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
        extra = ''
        if 'rows' in m:
            extra = (f'  std {m["std"]:4.1f}  bands {m["rows"]:3.1f}/{m["slices"]:3.1f}  grain {m["grain"]:4.2f}'
                     f'  rings r1 {m["rings"][1]:4.1f} r2 {m["rings"][2]:4.1f} '
                     f'r3+ {max(list(m["rings"].values())[2:]):4.1f}')
        lc = m.get('contrast', {})
        contrast = (f'  steps {lc["mean"]:4.1f}/{lc["p90"]:4.1f}  lines {lc["lines"]:4.0%}' if lc else '')
        lines.append(f'  {name:24s} colors {m.get("colors", "?"):>2}  visible {m.get("opacity", 0):4.0%}  '
                     f'luma {m.get("luma", 0):5.1f}  dark {m.get("dark", 0):4.0%}  isolated {m.get("isolated", 0):4.0%}'
                     + contrast + extra)
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
