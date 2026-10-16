"""checkArt (rule ZW-9 of docs/04-pionowy-wycinek.md): the committed textures must be exactly what tools/art makes.

Checks, in order:
1. every texture of the recipes (or its override in tools/art/overrides/) exists in
   src/main/resources/assets/polishforests/textures/block/ with the same decoded RGBA pixels (PNG bytes may differ:
   compression depends on the Pillow and zlib versions) and the same .mcmeta content (parsed JSON);
2. no other PNG or .mcmeta lies under assets/polishforests/textures/ (no hand-edited or stray files), and every PNG in
   tools/art/overrides/ is named after a texture of the recipes (a misnamed correction would never ship);
3. the style lint (lint.py) passes;
4. CREDITS.md is up to date;
5. the self-test of the library and the lint (selftest.py) passes.
Exit status 0 when everything passes, 1 otherwise; the fix is usually 'python tools/art/generate.py' (or
'./gradlew generateArt'), which rewrites the textures and CREDITS.md.

Usage: python tools/art/check.py   (or ./gradlew checkArt, which also finds the Python interpreter)
"""
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import credits  # noqa: E402
import generate  # noqa: E402
import lint  # noqa: E402
import selftest  # noqa: E402


def rel(path):
    return os.path.relpath(path, generate.ROOT).replace('\\', '/')


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
            if (f.endswith('.png') or f.endswith('.mcmeta')) and p not in expected:
                errors.append(f'{rel(os.path.join(dirpath, f))} is not made by tools/art (no recipe or override)')
    if os.path.isdir(generate.OVERRIDES):
        for f in sorted(os.listdir(generate.OVERRIDES)):
            if f.lower().endswith('.png') and f[:-4] not in built:
                errors.append(f'tools/art/overrides/{f} matches no texture of tools/art/recipes.py (names: '
                              f'<texture name>.png, e.g. oak_bark.png)')
    lint_errors, report = lint.lint_all(built)
    errors += ['lint: ' + e for e in lint_errors]
    if not credits.is_current():
        errors.append('CREDITS.md is out of date')
    errors += ['selftest: ' + f for f in selftest.run()]
    print(lint.format_report(report))
    if errors:
        print(f'checkArt: FAILED, {len(errors)} problem(s):')
        for e in errors:
            print('  ' + e)
        print('Regenerate with: python tools/art/generate.py   (or ./gradlew generateArt), then look at the diff.')
        sys.exit(1)
    overrides = sum(1 for b in built.values() if b['override'])
    print(f'checkArt: {len(built)} textures match tools/art ({overrides} from overrides), lint passes, '
          f'CREDITS.md is up to date, self-test passes')


if __name__ == '__main__':
    main()
