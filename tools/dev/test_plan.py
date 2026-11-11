#!/usr/bin/env python3
"""Chooses the tests a change needs from the files it touches (the test map, docs/mapa-testow.md).

Usage: python tools/dev/test_plan.py [base] [--end] [--list]
  base    commit to compare with (default HEAD: only the uncommitted changes); the working tree and untracked files
          always count, so 'test_plan.py <commit before the step>' covers the whole step so far
  --end   the plan for the end of a step (full 'test' once instead of 'fastTest', the mod-pack determinism run)
  --list  print the map itself and exit

The map is coarse on purpose: a file matches every rule whose prefix it starts with, and an unknown file under src/
selects everything (safe side). Indirect effects (a shared salt or random source, registry order, a mixin touching
generation) are what the full 'test' and the determinism run at the end of the step are for.
"""
import subprocess
import sys

# (path prefix, areas). Areas are keys of TESTS.
MAP = [
    ('docs/', []),
    ('tools/art/', ['art']),
    ('tools/', []),
    ('.dev-handoff/', []),
    ('src/main/java/pl/polishforests/worldgen/landscape/', ['unit', 'terrain', 'tatra', 'generation']),
    ('src/main/java/pl/polishforests/worldgen/chunk/', ['unit', 'terrain', 'tatra', 'generation', 'vanilla_world']),
    ('src/main/java/pl/polishforests/worldgen/surface/', ['unit', 'terrain', 'tatra', 'generation']),
    ('src/main/java/pl/polishforests/worldgen/cave/', ['unit', 'terrain', 'generation', 'caves']),
    ('src/main/java/pl/polishforests/climate/', ['unit', 'terrain', 'generation', 'seasons']),
    ('src/main/resources/data/polishforests/worldgen/', ['unit', 'terrain', 'tatra', 'generation']),
    ('src/main/resources/data/polishforests/dimension_type/', ['unit', 'terrain', 'generation']),
    ('src/main/java/pl/polishforests/worldgen/feature/', ['unit', 'generation', 'norms', 'vanilla_world']),
    ('src/main/java/pl/polishforests/worldgen/vanilla/', ['unit', 'generation', 'vanilla_world']),
    ('src/main/java/pl/polishforests/worldgen/tree/', ['unit', 'generation', 'norms']),
    ('src/main/java/pl/polishforests/worldgen/habitat/', ['unit', 'generation', 'norms']),
    ('src/main/java/pl/polishforests/worldgen/norms/', ['unit', 'norms']),
    ('src/main/java/pl/polishforests/worldgen/', ['unit', 'terrain', 'generation', 'norms']),
    ('src/main/generated/data/polishforests/worldgen/', ['unit', 'generation', 'norms']),
    ('src/main/java/pl/polishforests/block/', ['unit', 'blocks']),
    ('src/main/java/pl/polishforests/entity/', ['unit', 'blocks']),
    ('src/main/generated/', ['unit', 'blocks', 'datagen']),
    ('src/main/java/pl/polishforests/season/', ['unit', 'seasons']),
    ('src/main/java/pl/polishforests/command/', ['unit', 'ui']),
    ('src/main/java/pl/polishforests/mixin/', ['unit', 'mixins', 'generation', 'vanilla_world']),
    ('src/main/java/pl/polishforests/compat/', ['unit', 'mixins']),
    ('src/main/resources/assets/', ['render']),
    ('src/client/', ['unit', 'render']),
    ('src/gametest/', ['gametest_code']),
    ('src/gametest/java/pl/polishforests/gametest/TatraClientGameTest', ['gametest_code', 'tatra']),
    ('src/test/', ['unit']),
    # Step TT1: the Tatra tests and the golden Tatra file.
    ('src/test/java/pl/polishforests/worldgen/landscape/GoldenTatra', ['unit', 'tatra']),
    ('src/test/java/pl/polishforests/worldgen/landscape/Tatra', ['unit', 'tatra']),
    ('src/test/resources/golden_terrain_tatra', ['unit', 'tatra']),
    ('src/main/resources/', ['unit', 'mixins']),
    ('build.gradle', ['unit', 'mixins']),
    ('settings.gradle', ['unit', 'mixins']),
    ('gradle', ['unit', 'mixins']),
]

# area -> (during a step, at the end of the step). Commands are run from the repository root.
TESTS = {
    'unit': (['tools/dev/run-tests --reuse fastTest'], ['tools/dev/run-tests test   # full set, once']),
    'terrain': (["./gradlew test --tests '*GoldenTerrainTest*'   # terrain unchanged unless the step says so"], []),
    'tatra': (["./gradlew test --tests '*GoldenTatraTest*' --tests '*TatraIsolationTest*' --tests '*TatraReliefTest*' --tests '*TatraRiverTest*'   # Tatra golden file, KT-1 isolation, relief and rivers (step TT1)"],
              ['./gradlew runClientGameTest -Pgametest=tatra   # build/tatra/report.txt']),
    'generation': (['./gradlew runClientGameTest -Pgametest=determinism   # same seed = same blocks, any chunk order'],
                   ['./gradlew runClientGameTest -Pgametest=determinism -Poptimization -PdeterminismReference=<copy>/plain.txt   # C2ME, see BRIEF']),
    'norms': (['./gradlew runClientGameTest -Pgametest=slice -Psites=none   # norms card, about 2 min'], []),
    'caves': (['./gradlew runClientGameTest -Pgametest=caves'], ['./gradlew runClientGameTest -Pgametest=cave_biomes']),
    'vanilla_world': (['./gradlew runClientGameTest -Pgametest=vanilla_forests   # world type Default + Polish Forests'], []),
    'blocks': (["./gradlew checkDatagen", "./gradlew test --tests '*RegistrySnapshotTest*'",
                '# plus one game test of the changed mechanic only (e.g. slice -Psites=samples for trunks)'], []),
    'datagen': (['./gradlew checkDatagen'], []),
    'seasons': (["./gradlew test --tests '*season*'"], ['./gradlew runClientGameTest -Pgametest=slice -Psites=seasons   # only if the step changes seasons']),
    'ui': (['./gradlew runClientGameTest -Pgametest=ui'], []),
    'mixins': (["./gradlew test --tests '*MixinTargetsTest*'"], ['./gradlew runClientGameTest -Pgametest=probes   # only for render mixins']),
    'render': (['# screenshots of the affected board only, once per round that changes rendering; never to check repeatability'], []),
    'art': (['./gradlew checkArt', 'python tools/art/lint.py'], []),
    'gametest_code': (['./gradlew compileGametestJava   # plus the changed game test itself'], []),
}
ORDER = list(TESTS)


def changed(base):
    out = subprocess.run(['git', 'diff', '--name-only', base], capture_output=True, text=True, check=True).stdout
    untracked = subprocess.run(['git', 'ls-files', '--others', '--exclude-standard'],
                               capture_output=True, text=True, check=True).stdout
    return sorted(set(out.split()) | set(untracked.split()))


def areas_of(path):
    # The longest matching prefix wins (so worldgen/feature/ does not also pick the worldgen/ catch-all).
    hits = [(prefix, areas) for prefix, areas in MAP if path.startswith(prefix)]
    if hits:
        return set(max(hits, key=lambda hit: len(hit[0]))[1])
    return set(ORDER) - {'render', 'art', 'gametest_code'} if path.startswith('src/') else set()


def main(argv):
    if '--list' in argv:
        for prefix, areas in MAP:
            print(f'{prefix:60} {", ".join(areas) or "-"}')
        print()
        for area in ORDER:
            during, end = TESTS[area]
            print(f'[{area}]')
            for c in during:
                print(f'  during: {c}')
            for c in end:
                print(f'  end:    {c}')
        return 0
    end = '--end' in argv
    args = [a for a in argv if not a.startswith('--')]
    base = args[0] if args else 'HEAD'
    files = changed(base)
    selected = {}
    for f in files:
        for area in areas_of(f):
            selected.setdefault(area, []).append(f)
    if not selected:
        print(f'{len(files)} changed file(s) since {base}: no tests needed')
        return 0
    print(f'{len(files)} changed file(s) since {base}; areas: {", ".join(a for a in ORDER if a in selected)}')
    for area in ORDER:
        if area not in selected:
            continue
        during, at_end = TESTS[area]
        cmds = list(during)
        if end:
            if area == 'unit':
                cmds = at_end
            else:
                cmds += at_end
        print(f'\n[{area}] e.g. {selected[area][0]}' + (f' (+{len(selected[area]) - 1})' if len(selected[area]) > 1 else ''))
        for c in cmds:
            print(f'  {c}')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
