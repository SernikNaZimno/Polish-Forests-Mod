"""Check of tools/fauna (part of checkArt, rule ZW-9 of docs/04-pionowy-wycinek.md): the committed animal assets must be
exactly what tools/fauna/generate.py makes, and pass the validator.

Checks, in order:
1. every file of the generator exists with the same content: JSON compared parsed, textures by decoded RGBA pixels
   (PNG bytes depend on the Pillow and zlib versions), silhouettes as black and white masks;
2. no other file lies in the directories the generator owns (assets/polishforests/geckolib/, textures/entity/,
   textures/item/*_spawn_egg.png, the probe assets of the game test mod, src/test/resources/fauna/);
3. the validator (validate.py): models from the committed .geo.json files (bones, parents, UV inside the texture, no
   texel shared), animations, textures (opaque faces, transparent rest, no pure black, smoothness), spawn eggs, the
   expected positions of the game test;
4. the self-test (selftest.py): the kinematics against corners posed by GeckoLib itself, and the validator against
   broken examples.
Exit status 0 when everything passes, 1 otherwise; the fix is usually 'python tools/fauna/generate.py' (or
'./gradlew generateArt').

Usage: python tools/fauna/check.py   (./gradlew checkArt runs it after the block textures of tools/art)
"""
import fnmatch
import json
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import generate  # noqa: E402
import selftest  # noqa: E402
import validate  # noqa: E402

# Directories (relative to the project) the generator owns, with the file patterns it owns there.
OWNED = (
    (f'{generate.ASSETS}/geckolib', '*'),
    (f'{generate.ASSETS}/textures/entity', '*'),
    (f'{generate.ASSETS}/textures/item', '*_spawn_egg.png'),
    (f'{generate.PROBE_ASSETS}/geckolib', '*'),
    (f'{generate.PROBE_ASSETS}/textures/entity', '*'),
    (generate.EXPECTED, '*'),
)


def load_png(path):
    with Image.open(path) as im:
        return np.array(im.convert('RGBA'), dtype=np.uint8)


def compare(files):
    errors = []
    for rel, (kind, data) in files.items():
        path = os.path.join(generate.ROOT, rel)
        if not os.path.exists(path):
            errors.append(f'{rel} is missing')
            continue
        if kind == 'json':
            with open(path, encoding='utf-8') as f:
                if json.load(f) != json.loads(json.dumps(data)):
                    errors.append(f'{rel} differs from the generator')
        elif kind == 'mask':
            have = load_png(path)[..., 0] > 127
            if have.shape != data.shape:
                errors.append(f'{rel}: size {have.shape[::-1]} differs from {data.shape[::-1]}')
            elif (have != data).any():
                errors.append(f'{rel}: {int((have != data).sum())} pixels differ from the generator')
        else:
            have = load_png(path)
            if have.shape != data.shape:
                errors.append(f'{rel}: size {have.shape[1]} x {have.shape[0]} differs from {data.shape[1]} x {data.shape[0]}')
            else:
                diff = int(np.any(have != data, axis=2).sum())
                if diff:
                    errors.append(f'{rel}: {diff} of {data.shape[0] * data.shape[1]} pixels differ from the generator')
    return errors


def strays(files):
    errors = []
    want = {os.path.normcase(os.path.normpath(os.path.join(generate.ROOT, r))) for r in files}
    for d, pattern in OWNED:
        root = os.path.join(generate.ROOT, d)
        for dirpath, _, names in os.walk(root):
            for n in names:
                p = os.path.normcase(os.path.normpath(os.path.join(dirpath, n)))
                if fnmatch.fnmatch(n, pattern) and p not in want:
                    errors.append(f'{os.path.relpath(p, generate.ROOT)} is not made by tools/fauna')
    return errors


def validation(files, details):
    errors, report = [], []
    committed = {}
    for rel, (kind, _) in files.items():
        path = os.path.join(generate.ROOT, rel)
        if os.path.exists(path):
            if kind == 'json':
                with open(path, encoding='utf-8') as f:
                    committed[rel] = json.load(f)
            elif kind == 'png':
                committed[rel] = load_png(path)
    expected = committed.get(f'{generate.EXPECTED}/fauna_expected.json', {})
    for base in (generate.ASSETS, generate.PROBE_ASSETS):
        geo_dir = f'{base}/geckolib/models/entity/'
        for rel, geo in sorted(committed.items()):
            if not rel.startswith(geo_dir):
                continue
            mid = rel[len(geo_dir):-len('.geo.json')]
            try:
                model = validate.model_from_geo(geo)
            except ValueError as e:
                errors += [f'{rel}: {m}' for m in e.args[0]]
                continue
            errors += [f'{rel}: {m}' for m in validate.check_model(model)]
            errors += [f'{rel}: {m}' for m in validate.check_shape(model)]
            anim = committed.get(f'{base}/geckolib/animations/entity/{mid}.animation.json')
            if anim is None:
                errors.append(f'{mid}: no animation file')
            else:
                errors += [f'{mid} animations: {m}' for m in validate.check_animations(anim, model, mid)]
                errors += [f'{mid} poses: {m}' for m in validate.check_poses(anim, model)]
            textures = {r: img for r, img in committed.items()
                        if isinstance(img, np.ndarray) and (r.startswith(f'{base}/textures/entity/{mid}/')
                                                            or r == f'{base}/textures/entity/{mid}.png')}
            if not textures:
                errors.append(f'{mid}: no texture')
            for r, img in sorted(textures.items()):
                errs, stats = validate.texture_report(model, img, details.get(r))
                errors += [f'{r}: {m}' for m in errs]
                coat = ''
                if base == generate.ASSETS:     # animal coats (not the flat colors of the axis gauge)
                    cerrs, (ratio, single) = validate.coat_report(model, img, details.get(r))
                    errors += [f'{r}: {m}' for m in cerrs]
                    coat = f'; along / down {ratio:.2f}, single-tone rows {single:.0%}'
                if stats:
                    report.append(f'  {os.path.basename(r):24s} neighbour step mean {stats[0]:4.1f}, p95 {stats[1]:4.1f}, '
                                  f'largest {stats[2]:5.1f}; darkest texel {stats[3]:5.1f}{coat}')
            errors += validate.check_expected(expected, model)
            report.append(f'  {mid}: {len(model.bones)} bones, {len(model.cubes())} cubes, {validate.face_count(model)} faces, '
                          f'texture {model.texture_size[0]} x {model.texture_size[1]}')
    for rel, img in committed.items():
        if rel.endswith('_spawn_egg.png'):
            errors += [f'{rel}: {m}' for m in validate.check_spawn_egg(img)]
    return errors, report


def run():
    """(errors, report lines) of the whole check."""
    files, models = generate.build_all()
    errors = compare(files) + strays(files)
    verrs, report = validation(files, models['details'])
    return errors + verrs + ['selftest: ' + e for e in selftest.run()], report


def main():
    errors, report = run()
    print('tools/fauna:')
    print('\n'.join(report))
    if errors:
        print(f'fauna check: FAILED, {len(errors)} problem(s):')
        for e in errors:
            print('  ' + e)
        print('Regenerate with: python tools/fauna/generate.py   (or ./gradlew generateArt), then look at the diff.')
        sys.exit(1)
    print('fauna check: the animal assets match tools/fauna and pass the validator')


if __name__ == '__main__':
    main()
