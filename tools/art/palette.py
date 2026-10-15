"""Palette extraction for tools/art: k-means colors of a crop of a reference photo.

Only the resulting colors and a link to the source (with its license) go into the repository, in tools/art/palettes.json;
the photos themselves are never committed, scaled into textures or traced (docs/04-pionowy-wycinek.md, V1 and ZW-10).
Allowed sources: CC0 or public-domain photos (Wikimedia Commons, ambientCG and similar), listed in CREDITS.md.

Usage (Git Bash, project directory):
    python tools/art/palette.py <photo> --crop 0.2 0.1 0.8 0.9 [--k 6] [--name scots_pine_bark --source <id>]
        prints the colors (dark to light); with --name and --source it also writes the palette into palettes.json
        (the source id must already be listed under "sources" there, with its URL, author and license).
    python tools/art/palette.py <photo1> <photo2> ... --crop ... --crop ...   one crop per photo, pooled into one palette.

The k-means is deterministic: centres start at luma quantiles, 40 Lloyd iterations on at most 40 000 pixels.
"""
import argparse
import json
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import texlib  # noqa: E402

PALETTES = os.path.join(HERE, 'palettes.json')


def pixels(path, crop, max_side=400):
    im = Image.open(path).convert('RGB')
    w, h = im.size
    x0, y0, x1, y1 = crop
    im = im.crop((int(x0 * w), int(y0 * h), int(x1 * w), int(y1 * h)))
    im.thumbnail((max_side, max_side))
    return np.asarray(im, dtype=np.float64).reshape(-1, 3)


def kmeans(px, k, iters=40):
    l = texlib.luma(px)
    order = np.argsort(l, kind='stable')
    if len(px) > 40000:
        order = order[np.linspace(0, len(order) - 1, 40000).astype(int)]
        px = px[np.sort(order)]
        l = texlib.luma(px)
        order = np.argsort(l, kind='stable')
    centres = np.array([px[order[int((i + 0.5) / k * len(order))]] for i in range(k)])
    for _ in range(iters):
        d = ((px[:, None, :] - centres[None, :, :]) ** 2).sum(-1)
        lab = d.argmin(1)
        for i in range(k):
            m = lab == i
            if m.any():
                centres[i] = px[m].mean(0)
    counts = np.bincount(lab, minlength=k) / len(px)
    keep = sorted(range(k), key=lambda i: texlib.luma(centres[i]))
    return [texlib.rgb_to_hex(np.round(centres[i])) for i in keep], [round(float(counts[i]), 3) for i in keep]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('photos', nargs='+')
    ap.add_argument('--crop', nargs=4, type=float, action='append', required=True,
                    metavar=('X0', 'Y0', 'X1', 'Y1'), help='crop as fractions of the width and height, one per photo')
    ap.add_argument('--k', type=int, default=6)
    ap.add_argument('--name', help='palette name to write into palettes.json')
    ap.add_argument('--source', action='append', help='source id(s) in palettes.json "sources", one per photo')
    a = ap.parse_args()
    if len(a.crop) != len(a.photos):
        ap.error('give one --crop per photo')
    px = np.concatenate([pixels(p, c) for p, c in zip(a.photos, a.crop)])
    colors, shares = kmeans(px, a.k)
    print(json.dumps({'colors': colors, 'shares': shares}))
    if a.name:
        if not a.source or len(a.source) != len(a.photos):
            ap.error('--name needs one --source per photo')
        with open(PALETTES, encoding='utf-8') as f:
            data = json.load(f)
        for s in a.source:
            if s not in data['sources']:
                ap.error(f'source {s} is not listed in palettes.json "sources"')
        data['palettes'][a.name] = {
            'colors': colors,
            'shares': shares,
            'from': [{'source': s, 'crop': c} for s, c in zip(a.source, a.crop)],
            'k': a.k,
        }
        with open(PALETTES, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(data, f, ensure_ascii=False, indent='\t')
            f.write('\n')
        print('wrote palette', a.name, 'to', PALETTES)


if __name__ == '__main__':
    main()
