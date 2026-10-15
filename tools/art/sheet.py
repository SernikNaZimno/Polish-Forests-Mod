"""Contact sheets of the textures for review (step V1, checkpoint CP-A): docs/wycinek/grafika/.

- arkusz_8x.png: every texture at 8 x, next to a 3 x 3 tile at 4 x, tinted the way the game would tint it (foliage
  and grass colors of the oak-hornbeam and fresh pine forests, the proposed constant needle color);
- kafle_3x3.png: 3 x 3 tiles at 8 x (seams and repetition are easiest to see here);
- pnie.png: trunk columns as the game will stack them: the lower pine bark with its two variants in a random order,
  then the "mirror", and one column per broadleaf bark; on a 16 px wide and on a 6 px wide (thin trunk) face.

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


def trunks(built, path):
    """Trunk columns: 12 blocks high. Pine: 8 lower blocks (variants in a fixed pseudo-random order), 4 mirror blocks."""
    rng = t.Rng('sheet/trunks')
    k = 3
    columns = []
    pine = [built['scots_pine_bark' if rng.random() < 0.5 else 'scots_pine_bark_2']['img'] for _ in range(8)]
    pine = [built['scots_pine_upper_bark']['img']] * 4 + pine
    columns.append(('scots_pine', np.concatenate(pine, axis=0)))
    for name in ('oak_bark', 'hornbeam_bark', 'linden_bark', 'beech_bark'):
        columns.append((name, np.concatenate([built[name]['img']] * 12, axis=0)))
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


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(generate.ROOT, 'docs', 'wycinek', 'grafika')
    os.makedirs(out, exist_ok=True)
    built = generate.build_all()
    contact(built, os.path.join(out, 'arkusz_8x.png'))
    tiles(built, os.path.join(out, 'kafle_3x3.png'))
    trunks(built, os.path.join(out, 'pnie.png'))
    print('sheet: wrote arkusz_8x.png, kafle_3x3.png and pnie.png to', out)


if __name__ == '__main__':
    main()
