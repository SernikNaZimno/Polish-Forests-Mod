"""Local contrast of block textures (step V1b of docs/04-pionowy-wycinek.md): the measures of texlib.local_contrast
(mean and 90th percentile of the luma differences of neighbouring pixels, share of dark one-pixel lines, share of lone
pixels) and the luma standard deviation, for our textures or for any PNG files, e.g. vanilla textures unpacked from the
client jar OUTSIDE the repository (only the numbers may go into docs/ or into the lint thresholds, never the files).

Usage (Git Bash, project directory):
    python tools/art/measure.py                    our generated textures (tools/art recipes and overrides)
    python tools/art/measure.py <png or dir> ...   PNG files (the first 16 x 16 frame of each), e.g. a directory with
                                                   vanilla *_log.png and *_leaves.png
"""
import glob
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import texlib as t  # noqa: E402


def load(path):
    with Image.open(path) as im:
        a = np.array(im.convert('RGBA'), dtype=np.uint8)
    return a[:t.N, :t.N]                              # the first frame of an animated texture


def row(name, img):
    m = t.local_contrast(img)
    vis = img[..., 3] > 0
    std = float(t.luma(img[..., :3])[vis].std())
    return (f'  {name:28s} mean {m["mean"]:5.1f}  p90 {m["p90"]:5.1f}  lines {m["lines"]:5.1%}  '
            f'isolated {m["isolated"]:5.1%}  std {std:5.1f}'), m


def summary(label, ms):
    if not ms:
        return
    a = {k: np.array([m[k] for m in ms]) for k in ('mean', 'p90', 'lines')}
    q = lambda v: '/'.join(f'{x:.1f}' for x in np.percentile(v, (0, 25, 50, 75, 100)))
    print(f'  {label}: min/q1/median/q3/max  mean {q(a["mean"])}  p90 {q(a["p90"])}  '
          f'lines {"/".join(f"{x:.1%}" for x in np.percentile(a["lines"], (0, 25, 50, 75, 100)))}')


def main():
    if len(sys.argv) == 1:
        import generate
        ms = []
        for name, b in generate.build_all().items():
            line, m = row(name, b['img'])
            print(line)
            if b['tex'].kind == 'bark':
                ms.append(m)
        summary('bark', ms)
        return
    paths = []
    for arg in sys.argv[1:]:
        paths += sorted(glob.glob(os.path.join(arg, '*.png'))) if os.path.isdir(arg) else [arg]
    ms = []
    for p in paths:
        line, m = row(os.path.basename(p)[:-4], load(p))
        print(line)
        ms.append(m)
    summary('all', ms)


if __name__ == '__main__':
    main()
