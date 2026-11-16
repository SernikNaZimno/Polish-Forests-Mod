"""checkArt (rule ZW-9 of docs/04-pionowy-wycinek.md): the committed textures must be exactly what tools/art makes.

Checks, in order:
1. every texture of the recipes (or its override in tools/art/overrides/) exists in
   src/main/resources/assets/polishforests/textures/block/ with the same decoded RGBA pixels (PNG bytes may differ:
   compression depends on the Pillow and zlib versions) and the same .mcmeta content (parsed JSON);
2. no other PNG or .mcmeta lies under assets/polishforests/textures/ (no hand-edited or stray files), and every PNG in
   tools/art/overrides/ is named after a texture of the recipes (a misnamed correction would never ship);
3. the style lint (lint.py) passes;
4. CREDITS.md is up to date;
5. the self-test of the library and the lint (selftest.py) passes;
6. texlib.TRUNK_OFFSETS, the crops of thin trunks the lint measures, equals TrunkTemplates.OFFSETS of the game;
7. the animal assets (step V8): tools/fauna/check.py, run as a separate process (its modules have the same names as
   these); the textures it owns (textures/entity/, textures/item/*_spawn_egg.png) are left out of point 2 here.
Exit status 0 when everything passes, 1 otherwise; the fix is usually 'python tools/art/generate.py' (or
'./gradlew generateArt'), which rewrites the textures, the animal assets and CREDITS.md.

Usage: python tools/art/check.py   (or ./gradlew checkArt, which also finds the Python interpreter)
"""
import fnmatch
import json
import os
import re
import subprocess
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import credits  # noqa: E402
import generate  # noqa: E402
import lint  # noqa: E402
import selftest  # noqa: E402
import texlib as t  # noqa: E402


# Textures made by tools/fauna (checked by tools/fauna/check.py): directory relative to the textures root, file pattern.
FAUNA_OWNED = (('entity', '*'), ('item', '*_spawn_egg.png'))


def fauna_owned(path):
    r = os.path.relpath(path, generate.TEXTURES_DIR).replace(os.sep, '/')
    return any(r.startswith(d + '/') and fnmatch.fnmatch(os.path.basename(r), p) for d, p in FAUNA_OWNED)


def fauna_errors():
    """Runs tools/fauna/check.py in its own process; its output goes to the console, the result is a list of problems."""
    script = os.path.join(generate.ROOT, 'tools', 'fauna', 'check.py')
    env = dict(os.environ, PYTHONIOENCODING='utf-8')
    r = subprocess.run([sys.executable, script], cwd=generate.ROOT, env=env)
    return [] if r.returncode == 0 else [f'tools/fauna/check.py failed (exit {r.returncode}), see its messages above']


def rel(path):
    return os.path.relpath(path, generate.ROOT).replace('\\', '/')


def offsets_errors():
    """texlib.TRUNK_OFFSETS (the crops the lint measures) must equal TrunkTemplates.OFFSETS (the crops the game shows)."""
    java = os.path.join(generate.ROOT, 'src', 'client', 'java', 'pl', 'polishforests', 'client', 'datagen',
                        'TrunkTemplates.java')
    try:
        with open(java, encoding='utf-8') as f:
            src = f.read()
    except OSError as e:
        return [f'cannot read {rel(java)}: {e}']
    m = re.search(r'OFFSETS\s*=\s*\{(.*?)\};', src, re.S)
    rows = [tuple(int(v) for v in r.split(',')) for r in re.findall(r'\{\s*([\d\s,]+?)\s*\}', m.group(1))] if m else []
    if rows != [tuple(r) for r in t.TRUNK_OFFSETS]:
        return [f'texlib.TRUNK_OFFSETS {list(t.TRUNK_OFFSETS)} differs from TrunkTemplates.OFFSETS {rows}']
    return []


def main():
    errors = []
    built = generate.build_all()
    expected = set()
    for name, b in built.items():
        png = os.path.join(generate.BLOCK_DIR, name + '.png')
        meta = png + '.mcmeta'
        expected.add(os.path.normcase(os.path.abspath(png)))
        src = 'override ' + rel(b['override']) if b['override'] else 'generator'
        if not os.path.exists(png):
            errors.append(f'{rel(png)} is missing ({src})')
        else:
            have = generate.load_rgba(png)
            want = b['img']
            if have.shape != want.shape:
                errors.append(f'{rel(png)}: size {have.shape[1]} x {have.shape[0]} differs from the {src} '
                              f'({want.shape[1]} x {want.shape[0]})')
            else:
                diff = int(np.any(have != want, axis=2).sum())
                if diff:
                    errors.append(f'{rel(png)}: {diff} of {want.shape[0] * want.shape[1]} pixels differ from the {src}')
        if b['mcmeta'] is not None:
            expected.add(os.path.normcase(os.path.abspath(meta)))
            if not os.path.exists(meta):
                errors.append(f'{rel(meta)} is missing')
            else:
                with open(meta, encoding='utf-8') as f:
                    if json.load(f) != b['mcmeta']:
                        errors.append(f'{rel(meta)} differs from the recipe')
    for dirpath, _, files in os.walk(generate.TEXTURES_DIR):
        for f in files:
            p = os.path.normcase(os.path.abspath(os.path.join(dirpath, f)))
            if (f.endswith('.png') or f.endswith('.mcmeta')) and p not in expected and not fauna_owned(os.path.join(dirpath, f)):
                errors.append(f'{rel(os.path.join(dirpath, f))} is not made by tools/art (no recipe or override)')
    if os.path.isdir(generate.OVERRIDES):
        for f in sorted(os.listdir(generate.OVERRIDES)):
            if f.lower().endswith('.png') and f[:-4] not in built:
                errors.append(f'tools/art/overrides/{f} matches no texture of tools/art/recipes.py (names: '
                              f'<texture name>.png, e.g. oak_bark.png)')
    errors += offsets_errors()
    lint_errors, report = lint.lint_all(built)
    errors += ['lint: ' + e for e in lint_errors]
    if not credits.is_current():
        errors.append('CREDITS.md is out of date')
    errors += ['selftest: ' + f for f in selftest.run()]
    print(lint.format_report(report))
    errors += fauna_errors()
    if errors:
        print(f'checkArt: FAILED, {len(errors)} problem(s):')
        for e in errors:
            print('  ' + e)
        print('Regenerate with: python tools/art/generate.py   (or ./gradlew generateArt), then look at the diff.')
        sys.exit(1)
    overrides = sum(1 for b in built.values() if b['override'])
    print(f'checkArt: {len(built)} textures match tools/art ({overrides} from overrides), lint passes, '
          f'CREDITS.md is up to date, self-test passes, the animal assets match tools/fauna')


if __name__ == '__main__':
    main()
