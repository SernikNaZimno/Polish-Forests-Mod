"""Contact sheets of the textures for review (step V1, checkpoint CP-A): docs/wycinek/grafika/.

- arkusz_8x.png: every texture at 8 x, next to a 3 x 3 tile at 4 x, tinted the way the game would tint it (foliage
  and grass colors of the oak-hornbeam and fresh pine forests, the proposed constant needle color);
- kafle_3x3.png: 3 x 3 tiles at 8 x (seams and repetition are easiest to see here);
- pnie.png: trunk columns as the game will stack them: the lower pine bark with its two variants in a random order,
  then the transition bark (step V1b), then the "mirror", and one column per broadleaf bark (variants in a random
  order); on a 16 px wide and on a 6 px wide (thin trunk) face;
- pnie_cienkie.png: thin trunks of radius 1, 2 and 4 px, the four faces of each with the crops the game shows
  (TrunkTemplates.OFFSETS) and the game's face shading: rings at every block ("bamboo") and repeated motifs show here;
- dystans.png: the trunk columns averaged down like the game's mipmaps at 4 and 8 blocks per pixel step (levels 2
  and 3, box filter), how a trunk reads from a distance.

Usage: python tools/art/sheet.py [<out dir>]   (default docs/wycinek/grafika)
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import generate  # noqa: E402
import texlib as t  # noqa: E402

# biome colors of src/main/generated (oak_hornbeam_forest, fresh_pine_forest)
FOLIAGE = '#59ae30'
GRASS = '#89bd5e'
BG = (118, 150, 186, 255)
INK = (232, 232, 232, 255)
PANEL = (38, 40, 44, 255)


def tinted(img, tint):
    if not tint:
        return img
    col = {'foliage': FOLIAGE, 'grass': GRASS}.get(tint, tint)
    c = np.array(t.hex_to_rgb(col), dtype=np.float64)
    out = img.astype(np.float64)
    out[..., :3] = out[..., :3] * c / 255.0
    return np.round(out).astype(np.uint8)


def on_bg(img):
    bg = Image.new('RGBA', (img.shape[1], img.shape[0]), BG)
    bg.alpha_composite(Image.fromarray(img, 'RGBA'))
    return bg


def tile(img, n=3):
    return np.tile(img, (n, n, 1))


def scale(im, k):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


def contact(built, path):
    cell_w, cell_h = 8 + 128 + 12 + 192 + 16, 128 + 66
    cols = 2
    rows = (len(built) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * cell_w + 8, rows * cell_h + 34), PANEL)
    d = ImageDraw.Draw(sheet)
    d.text((8, 8), 'Polish Forests - tools/art textures (8x and 3x3 tile at 4x, tinted as in game)', fill=INK)
    for i, (name, b) in enumerate(built.items()):
        img = tinted(b['img'], b['tex'].tint)
        x0 = (i % cols) * cell_w + 8
        y0 = (i // cols) * cell_h + 34
        sheet.paste(scale(on_bg(img), 8), (x0, y0 + 14))
        sheet.paste(scale(on_bg(tile(img)), 4), (x0 + 140, y0 + 14))
        label = name + (f'  (tint {b["tex"].tint})' if b['tex'].tint else '')
        d.text((x0, y0), label, fill=INK)
    sheet.convert('RGB').save(path, optimize=True)


def tiles(built, path):
    k = 8
    size = 48 * k
    cols = 5
    rows = (len(built) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * (size + 12) + 12, rows * (size + 30) + 12), PANEL)
    d = ImageDraw.Draw(sheet)
    for i, (name, b) in enumerate(built.items()):
        img = tinted(b['img'], b['tex'].tint)
        x0 = (i % cols) * (size + 12) + 12
        y0 = (i // cols) * (size + 30) + 12
        d.text((x0, y0), name, fill=INK)
        sheet.paste(scale(on_bg(tile(img)), k), (x0, y0 + 16))
    sheet.convert('RGB').save(path, optimize=True)


def column(built, name, blocks, rng):
    """A trunk of `blocks` blocks of one bark: its variants (textures whose variant_of is `name`) drawn at random
    per block, as V2 will draw them."""
    family = [name] + [n for n, b in built.items() if b['tex'].variant_of == name]
    return np.concatenate([built[family[rng.integers(0, len(family))]]['img'] for _ in range(blocks)], axis=0)


def trunk_columns(built):
    """(label, RGBA column) for the pine (4 blocks of "mirror" over 2 of transition bark over 6 of lower bark, step V1b)
    and each broadleaf bark."""
    rng = t.Rng('sheet/trunks')
    pine = np.concatenate([column(built, 'scots_pine_upper_bark', 4, rng),
                           column(built, 'scots_pine_transition_bark', 2, rng), column(built, 'scots_pine_bark', 6, rng)])
    out = [('scots_pine', pine)]
    for name in ('oak_bark', 'hornbeam_bark', 'linden_bark', 'beech_bark', 'broadleaf_bark'):
        out.append((name, column(built, name, 12, rng)))
    return out


def trunks(built, path):
    """Trunk columns: 12 blocks high. Pine: 6 lower blocks (variants in a fixed pseudo-random order), 2 transition
    blocks, 4 mirror blocks."""
    k = 3
    columns = trunk_columns(built)
    h = 12 * 16 * k
    w_full, w_thin = 16 * k, 6 * k
    sheet = Image.new('RGBA', (len(columns) * (w_full + w_thin + 40) + 20, h + 40), BG)
    d = ImageDraw.Draw(sheet)
    for i, (name, col) in enumerate(columns):
        x0 = 20 + i * (w_full + w_thin + 40)
        im = scale(Image.fromarray(col, 'RGBA'), k)
        sheet.paste(im, (x0, 30))
        thin = col[:, 5:11]                      # a 6 px wide face: the side of a thin trunk (radius 3)
        sheet.paste(scale(Image.fromarray(np.ascontiguousarray(thin), 'RGBA'), k), (x0 + w_full + 8, 30))
        d.text((x0, 8), name, fill=(20, 20, 20, 255))
    sheet.convert('RGB').save(path, optimize=True)


def shade(img, f):
    out = img.copy()
    out[..., :3] = np.round(img[..., :3].astype(np.float64) * f).astype(np.uint8)
    return out


def thin_trunks(built, path):
    """Thin trunks of radius 1, 2 and 4 px, 8 blocks high, scale 4: the four faces (north, west, south, east) with the
    crops the game shows (texlib.TRUNK_OFFSETS, as TrunkTemplates.OFFSETS) and the game's face shading (0.8 north and
    south, 0.6 west and east). Rings at the block period ("bamboo", lint rule rings) and repeated motifs show here."""
    k = 4
    columns = [(n, c[:8 * 16]) for n, c in trunk_columns(built)]
    radii = (1, 2, 4)
    cell = sum(4 * 2 * r * k + 12 for r in radii) + 20
    sheet = Image.new('RGBA', (len(columns) * cell + 20, 8 * 16 * k + 50), BG)
    d = ImageDraw.Draw(sheet)
    for i, (name, col) in enumerate(columns):
        x = 20 + i * cell
        d.text((x, 6), name, fill=(20, 20, 20, 255))
        for r in radii:
            faces = []
            for u, f in zip(t.TRUNK_OFFSETS[r - 1], (0.8, 0.6, 0.8, 0.6)):
                faces.append(shade(np.ascontiguousarray(col[:, [(u + j) % t.N for j in range(2 * r)]]), f))
            sheet.paste(scale(Image.fromarray(np.concatenate(faces, axis=1), 'RGBA'), k), (x, 36))
            d.text((x, 22), f'r={r}', fill=(20, 20, 20, 255))
            x += 4 * 2 * r * k + 12
    sheet.convert('RGB').save(path, optimize=True)


def mip(img, level):
    """Box-filtered mipmap level (2 ** level pixels per step), as an RGBA array."""
    f = 2 ** level
    h, w = img.shape[0] // f, img.shape[1] // f
    a = img[:h * f, :w * f].astype(np.float64).reshape(h, f, w, f, 4).mean(axis=(1, 3))
    return np.round(a).astype(np.uint8)


def distance(built, path):
    """Trunk columns (16 px faces, 12 blocks) at mipmap levels 2 and 3, each pixel step shown 8 x 8."""
    k = 8
    columns = trunk_columns(built)
    sheet = Image.new('RGBA', (len(columns) * (4 * k + 2 * k + 40) + 20, 12 * 4 * k + 40), BG)
    d = ImageDraw.Draw(sheet)
    for i, (name, col) in enumerate(columns):
        x = 20 + i * (4 * k + 2 * k + 40)
        d.text((x, 6), name[:12], fill=(20, 20, 20, 255))
        sheet.paste(scale(Image.fromarray(mip(shade(col, 0.8), 2), 'RGBA'), k), (x, 26))
        sheet.paste(scale(Image.fromarray(mip(shade(col, 0.8), 3), 'RGBA'), k), (x + 4 * k + 10, 26))
    sheet.convert('RGB').save(path, optimize=True)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(generate.ROOT, 'docs', 'wycinek', 'grafika')
    os.makedirs(out, exist_ok=True)
    built = generate.build_all()
    contact(built, os.path.join(out, 'arkusz_8x.png'))
    tiles(built, os.path.join(out, 'kafle_3x3.png'))
    trunks(built, os.path.join(out, 'pnie.png'))
    thin_trunks(built, os.path.join(out, 'pnie_cienkie.png'))
    distance(built, os.path.join(out, 'dystans.png'))
    print('sheet: wrote arkusz_8x.png, kafle_3x3.png, pnie.png, pnie_cienkie.png and dystans.png to', out)


if __name__ == '__main__':
    main()
