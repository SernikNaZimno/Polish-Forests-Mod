"""Generates the block textures of the mod from the recipes of tools/art, and CREDITS.md.

Usage (Git Bash, project directory; Python 3.10+ with Pillow and NumPy):
    python tools/art/generate.py          writes src/main/resources/assets/polishforests/textures/block/*.png
                                          (and .mcmeta where a recipe has one) and CREDITS.md
    python tools/art/generate.py --out <dir>   writes the textures to another directory (CREDITS.md untouched)
    ./gradlew generateArt                 the same through Gradle; also runs tools/fauna/generate.py (the animal models,
                                          animations and textures, step V8)

Hand-made corrections (decision D5): a PNG in tools/art/overrides/<name>.png replaces the generated texture of the same
name; it is copied as it is and only has to pass the style lint (lint.py). checkArt (check.py) then compares the
committed file with the override instead of the generator.
"""
import argparse
import json
import os
import subprocess
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import credits  # noqa: E402
import recipes  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
TEXTURES_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'polishforests', 'textures')
BLOCK_DIR = os.path.join(TEXTURES_DIR, 'block')
OVERRIDES = os.path.join(HERE, 'overrides')
PALETTES = os.path.join(HERE, 'palettes.json')


def load_palettes():
    with open(PALETTES, encoding='utf-8') as f:
        return json.load(f)


def load_rgba(path):
    with Image.open(path) as im:
        return np.array(im.convert('RGBA'), dtype=np.uint8)


def build_all():
    """name -> dict(img=RGBA array, mcmeta=dict or None, tex=Texture, override=path or None), in recipe order."""
    pal = load_palettes()
    out = {}
    for tex in recipes.TEXTURES:
        ov = os.path.join(OVERRIDES, tex.name + '.png')
        if os.path.exists(ov):
            img, src = load_rgba(ov), ov
        else:
            img, src = recipes.make(tex, pal), None
        out[tex.name] = dict(img=img, mcmeta=tex.mcmeta, tex=tex, override=src)
    return out


def save_png(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    opaque = bool((img[..., 3] == 255).all())
    im = Image.fromarray(img[..., :3] if opaque else img, 'RGB' if opaque else 'RGBA')
    im.save(path, optimize=True)


def write_all(out_dir):
    built = build_all()
    for name, b in built.items():
        save_png(b['img'], os.path.join(out_dir, name + '.png'))
        meta = os.path.join(out_dir, name + '.png.mcmeta')
        if b['mcmeta'] is not None:
            with open(meta, 'w', encoding='utf-8', newline='\n') as f:
                json.dump(b['mcmeta'], f, indent=2)
                f.write('\n')
        elif os.path.exists(meta):
            os.remove(meta)
    return built


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--out', help='output directory (default: the mod resources)')
    a = ap.parse_args()
    out_dir = a.out or BLOCK_DIR
    built = write_all(out_dir)
    print(f'generate: wrote {len(built)} textures to {out_dir}')
    if not a.out:
        credits.write()
        print('generate: wrote', credits.CREDITS)
        # The animal assets (tools/fauna) in their own process: their modules have the same names as these.
        env = dict(os.environ, PYTHONIOENCODING='utf-8')
        r = subprocess.run([sys.executable, os.path.join(ROOT, 'tools', 'fauna', 'generate.py')], cwd=ROOT, env=env)
        if r.returncode:
            sys.exit(r.returncode)


if __name__ == '__main__':
    main()
