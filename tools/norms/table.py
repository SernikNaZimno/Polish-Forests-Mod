"""Tables of the norms card (step V4 of docs/04-pionowy-wycinek.md, section 6) from the report of the in-game census
(build/norms/karta-norm.csv, written by ./gradlew runClientGameTest -Pgametest=slice): one table per forest type with
the norms of the natural and the managed column and the value of every plot (scale, mode, site) with its verdict, then
the transects (order of the indicators, the stretches and the widths of the transitions). The output is Markdown with
Polish headings for docs/.

Usage (Git Bash, project directory):
    python tools/norms/table.py [<karta-norm.csv>] [--out <file.md>]
The default input is build/norms/karta-norm.csv; without --out the tables go to the standard output.
"""
import csv
import io
import sys

MARK = {'in': '✓', 'tolerance': '≈', 'out': '✗', 'none': ''}
SCALE = {'realistic': 'REAL', 'gameplay': 'GAME'}
MODE = {'natural': 'N', 'present_day': 'D'}
FORESTS = [('oak_hornbeam', 'Grąd', 'oak_hornbeam_natural', 'oak_hornbeam_managed'),
           ('fresh_pine', 'Bór świeży', 'fresh_pine_natural', 'fresh_pine_managed')]
SEQUENCE = {'fresh_pine_forest': 'Bśw', 'mixed_pine_forest': 'BMśw', 'mixed_forest': 'LMśw', 'oak_hornbeam_forest': 'grąd'}


PLACE = {'natural_1': 'nat1', 'natural_2': 'nat2', 'managed_1': 'gosp1', 'managed_2': 'gosp2',
         'managed_pole_stage': 'drąg', 'managed_mature': 'dojrz', 'managed_old_growth': 'stary',
         'natural_pole_stage': 'nat-drąg'}


def label(place, forest):
    """Short name of a place: a site of V4 by its number with the column of the card by its stand (step V9), a sample
    plot by stand (step V9) by its kind."""
    for prefix, _, _, _ in FORESTS:
        if place.startswith(prefix + '_'):
            rest = place[len(prefix) + 1:]
            if rest in PLACE:
                return PLACE[rest]
            return 'm' + rest + (' (nat)' if forest.endswith('natural') else ' (gosp)')
    return place


def cell(row):
    mark = MARK[row['verdict']]
    return row['value'] + (' ' + mark if mark else '') if row['value'] else '—'


def plots(rows, out):
    for prefix, title, natural, managed in FORESTS:
        mine = [r for r in rows if r['place'].startswith(prefix) and not r['part']]
        if not mine:
            continue
        columns = []
        for r in mine:
            key = (r['scale'], r['mode'], r['place'])
            if key not in columns:
                columns.append(key)
        metrics = []
        for r in mine:
            if r['metric'] not in metrics:
                metrics.append(r['metric'])
        by = {(r['scale'], r['mode'], r['place'], r['metric']): r for r in mine}
        norm = {}
        for r in mine:
            if r['norm']:
                norm[(r['forest'], r['metric'])] = r['norm']
        forest = {(r['scale'], r['mode'], r['place']): r['forest'] for r in mine}
        heads = [f"{SCALE[s]} {MODE[m]} {label(p, forest[(s, m, p)])}" for s, m, p in columns]
        out.write(f"\n**{title}** (norma nat.: kolumna lasu naturalnego, norma gosp.: gospodarczego; od V9 kolumna każdej "
                  f"powierzchni według wydzielenia w jej środku: m1, m2 to miejsca V4, nat1, nat2, gosp1, gosp2, drąg, nat-drąg, dojrz, "
                  f"stary to powierzchnie według wydzieleń; ✓ w normie, ≈ w tolerancji, ✗ poza nią)\n\n")
        out.write('| Miara | Norma nat. | Norma gosp. | ' + ' | '.join(heads) + ' |\n')
        out.write('|---|---|---|' + '---|' * len(heads) + '\n')
        for m in metrics:
            values = [cell(by[c + (m,)]) if c + (m,) in by else '' for c in columns]
            out.write(f"| `{m}` | {norm.get((natural, m), '')} | {norm.get((managed, m), '')} | " + ' | '.join(values) + ' |\n')


def transects(rows, out):
    mine = [r for r in rows if r['place'] == 'transect']
    if not mine:
        return
    worlds = []
    for r in mine:
        if (r['scale'], r['mode']) not in worlds:
            worlds.append((r['scale'], r['mode']))
    out.write('\n**Transekt Bśw → BMśw → LMśw → grąd** (pas ±8 bloków; szerokość przejścia 10–90% krzywej logistycznej '
              'wskaźnika „mchy − ściółka liściasta”, m·k)\n\n')
    out.write('| Świat | Kolejność | Przejścia | ' + ' | '.join(f'{v} (iglaste / mchy / ściółka %)' for v in SEQUENCE.values()) + ' |\n')
    out.write('|---|---|---|' + '---|' * len(SEQUENCE) + '\n')
    for s, m in worlds:
        rs = [r for r in mine if r['scale'] == s and r['mode'] == m]
        order = next((cell(r) for r in rs if r['metric'] == 'N15.order'), '')
        widths = ', '.join(f"{'→'.join(SEQUENCE[p] for p in r['part'].split('>'))} {cell(r)}"
                           for r in rs if r['metric'] == 'N15.transition_width')
        stretch = []
        for part in SEQUENCE:
            v = {r['metric']: r['value'] for r in rs if r['part'] == part}
            stretch.append(f"{v.get('N15.conifers', '')} / {v.get('N9.moss', '')} / {v.get('N8.litter', '')}")
        out.write(f"| {SCALE[s]} {MODE[m]} | {order} | {widths} | " + ' | '.join(stretch) + ' |\n')


def main(argv):
    path = 'build/norms/karta-norm.csv'
    target = None
    args = list(argv)
    if '--out' in args:
        i = args.index('--out')
        target = args[i + 1]
        del args[i:i + 2]
    if args:
        path = args[0]
    with open(path, encoding='utf-8', newline='') as f:
        rows = list(csv.DictReader(f))
    buf = io.StringIO()
    seasons = sorted({r['season'] for r in rows})
    buf.write(f"Pora roku przy pomiarze: {', '.join(seasons)}.\n")
    plots(rows, buf)
    transects(rows, buf)
    if target:
        with open(target, 'w', encoding='utf-8', newline='\n') as f:
            f.write(buf.getvalue())
    else:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stdout.write(buf.getvalue())


if __name__ == '__main__':
    main(sys.argv[1:])
