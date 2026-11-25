#!/usr/bin/env python3
"""A/B tables of a collective performance run (step FA10, V17) from the logs of the A/B driver.

The driver (handoff/FA10/run_ab_fa10.sh, pattern of step F7) writes one log per run, filtered to the [performance] and
[fauna] lines, as <part>_r<round>_<build>.log in one directory: build `base` = the M2 baseline (worktree pl-m2-baseline),
`head` = the measured build; the fauna flights as ffly_r<round>_<on|off>.log, the MSPT scenes as mspt_r<round>_head.log.
Parts: load (-PloadOnly -Pcaves=off), caves (-PloadOnly, with caves), stages (-Pgametest=stages -Psites=slice
-Pcaves=off), fly (flights with the pack, with caves).

The output is Markdown in the form PerformanceThresholds.parseMarkdown reads (docs/wycinek/<step>/ab_<step>.md): the
section headings name the metric, a row starts with the scale and the site, the ratio is the 4th column for chunks per
second and FPS, the stages have the milliseconds of the measured build in the 4th and the ratio in the 5th column.
Sections without a threshold (Tatra sites KT-6, MSPT of the fauna, flights with and without the fauna) have headings that
name no metric, so the parser skips them. The verdicts are not computed here: PerformanceThresholdsTest
.theCollectiveRunIsJudged writes them to build/ab/verdicts_v17.md (one source of the thresholds, in Java), and
--verdicts appends that file.

Usage:
  python tools/dev/ab_tables.py <log dir> --out docs/wycinek/v17/ab_v17.md [--raw raw.txt] [--ref docs/wycinek/f7/ab_f7.md]
         [--name FA10] [--header header.md] [--verdicts build/ab/verdicts_v17.md]
"""
import argparse
import collections
import glob
import os
import re
import statistics as st
import sys

L = re.compile(r"\[performance\] (poland\S* / \S+): new terrain.*?, ([\d.]+) chunks/s")
SF = re.compile(r"\[performance\] (poland\S* / slice \S+): stages for 64 chunks.*?features=(\d+) ms")
SL = re.compile(r"\[performance\] (poland\S* / slice \S+): stages for 64 chunks.*?light=(\d+) ms")
SW = re.compile(r"\[performance\] (poland\S* / slice \S+): whole chunk ([\d.]+) ms/chunk")
F = re.compile(r"\[performance\] (poland\S* / \S+ (?:canopy|eye)) flight:.*?FPS ([\d.]+) ")
G = re.compile(r"\[performance\] (poland\S* / \S+ (?:canopy|eye)) flight:.*?GPU time of the level pass per frame ([\d.]+) ms")
AN = re.compile(r"\[performance\] (poland\S* / \S+): animals in the flight area \(\d+ blocks around\): mod (\d+) (\{[^}]*\}), frogs (\d+)")
MS = re.compile(r"\[fauna\] (\S+ \S+) MSPT( [A-Za-z ]+?)?: .*?: ([\d.]+|NaN) ms per animal and tick")
MSB = re.compile(r"server average tick ([\d.]+) ms before, ([\d.]+) ms after")


def med(v):
    return st.median(v)


def f2(x):
    return f"{x:.2f}".replace('.', ',')


def f3(x):
    return f"{x:.3f}".replace('.', ',')


def f4(x):
    return f"{x:.4f}".replace('.', ',')


def name(k):
    """poland_gameplay / slice oak_hornbeam_1 -> GAMEPLAY oak_hornbeam_1; canopy/eye -> okap/oko."""
    k = (k.replace('poland_gameplay_present_day', 'GAMEPLAY D').replace('poland_gameplay', 'GAMEPLAY')
         .replace('poland_present_day', 'REAL D').replace('poland', 'REAL').replace(' / slice ', ' ').replace(' / ', ' '))
    return k.replace(' canopy', ' okap').replace(' eye', ' oko')


def runs(d, part, build, rx):
    """{round: {label: value}} for the logs <part>_r<n>_<build>.log."""
    out = {}
    for f in sorted(glob.glob(os.path.join(d, f"{part}_r*_{build}.log"))):
        m = re.search(rf"{part}_r(\d+)_{build}\.log$", f)
        if not m:
            continue
        vals = {}
        for line in open(f, encoding='utf-8', errors='replace'):
            x = rx.search(line)
            if x:
                vals[x.group(1)] = float(x.group(2))
        out[int(m.group(1))] = vals
    return out


def series(r):
    """{label: [values by round]}."""
    s = collections.defaultdict(list)
    for n in sorted(r):
        for k, v in r[n].items():
            s[k].append(v)
    return s


def reference(path):
    """Ratios and ms of a reference A/B file (F7), by section kind and row name: {('load'|'caves'|'FEATURES'|...): {row: value}}."""
    ref = collections.defaultdict(dict)
    if not path or not os.path.exists(path):
        return ref
    kind = None
    for line in open(path, encoding='utf-8'):
        if line.startswith('#'):
            h = line.lower()
            kind = ('cost' if 'koszt kroków' in h else 'fps' if 'fps' in h else 'caves' if ('strażnik' in h or 'z jaskiniami' in h)
                    else 'load' if 'chunki na sekundę' in h else 'FEATURES' if 'features' in h else 'LIGHT' if 'light' in h
                    else 'WHOLE' if 'cały chunk' in h else None)
            continue
        if kind is None or not line.startswith('| ') or not (line.startswith('| REAL') or line.startswith('| GAMEPLAY')
                                                            or line.startswith('| **REAL') or line.startswith('| **GAMEPLAY')):
            continue
        c = [x.replace('*', '').strip() for x in line.split('|')]
        try:
            if kind in ('FEATURES', 'LIGHT', 'WHOLE'):
                ref[kind][c[1]] = (float(c[4].replace(',', '.')), float(c[5].replace(',', '.')))
            else:
                ref[kind][c[1]] = float(c[4].replace(',', '.'))
        except (ValueError, IndexError):
            pass
    return ref


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('logs')
    ap.add_argument('--out', required=True)
    ap.add_argument('--raw')
    ap.add_argument('--ref', default='docs/wycinek/f7/ab_f7.md')
    ap.add_argument('--name', default='FA10')
    ap.add_argument('--header', help='Markdown put before the tables (title and method)')
    ap.add_argument('--verdicts', help='verdict section written by PerformanceThresholdsTest, appended at the end')
    a = ap.parse_args()
    d, N = a.logs, a.name
    ref = reference(a.ref)
    md, raw = [], []
    if a.header:
        md.append(open(a.header, encoding='utf-8').read().rstrip() + '\n')
    else:
        md.append(f"# {N}: tabele A/B (PW-10, G.34, CP-D.5)\n")
    tatra = collections.defaultdict(dict)

    # 1. Forest norm: chunks per second without caves.
    b, h = runs(d, 'load', 'base', L), runs(d, 'load', 'head', L)
    bs, hs = series(b), series(h)
    md.append(f"\n## Chunki na sekundę z C2ME bez jaskiń (`-Pcaves=off`, `-PloadOnly`); próg ≥ 0,7 × M2\n\n"
              f"| miejsce | M2 | {N} | iloraz | ilorazy rund (min–max) | F7 (2026-11-11) |\n|---|---|---|---|---|---|")
    rs = []
    for k in hs:
        if '/ tatra' in k:
            tatra[name(k)]['load'] = med(hs[k])
            continue
        if k not in bs:
            continue
        q = med(hs[k]) / med(bs[k])
        rs.append(q)
        pr = [h[n][k] / b[n][k] for n in sorted(h) if n in b and k in h[n] and k in b[n]]
        md.append(f"| {name(k)} | {f2(med(bs[k]))} | {f2(med(hs[k]))} | {f3(q)} | {f3(min(pr))}–{f3(max(pr))} | "
                  f"{f3(ref['load'][name(k)]) if name(k) in ref['load'] else '—'} |")
        raw.append(f"load {name(k)}: base {' '.join(map(str, bs[k]))}; head {' '.join(map(str, hs[k]))}")
    if rs:
        md.append(f"| **min / mediana / max** | | | {f3(min(rs))} / {f3(med(rs))} / {f3(max(rs))} | | |")
    nc = hs

    # 2. Guard with caves: the world with caves against M2 of the same rounds (M2 has no -Pcaves).
    b, h = runs(d, 'caves', 'base', L), runs(d, 'caves', 'head', L)
    bs, hs = series(b), series(h)
    allbase = collections.defaultdict(list)
    for s_ in (series(runs(d, 'load', 'base', L)), bs):
        for k, v in s_.items():
            allbase[k] += v
    md.append(f"\n## Strażnik z jaskiniami: chunki na sekundę z C2ME (świat z jaskiniami wobec M2 bez jaskiń); próg REAL ≥ 0,4 ×, "
              f"GAMEPLAY ≥ 0,55 ×, lasy górskie REAL ≥ 0,35 ×, GAMEPLAY ≥ 0,45 ×\n\n"
              f"| miejsce | M2 | {N} | iloraz | {N} bez jaskiń | koszt jaskiń {N}, ms na chunk | iloraz wobec M2 ze wszystkich przebiegów |"
              f" F7 |\n|---|---|---|---|---|---|---|---|")
    for k in hs:
        if '/ tatra' in k:
            tatra[name(k)]['caves'] = med(hs[k])
            continue
        if k not in bs:
            continue
        q = med(hs[k]) / med(bs[k])
        cost = 1000 / med(hs[k]) - 1000 / med(nc[k]) if k in nc else float('nan')
        md.append(f"| {name(k)} | {f2(med(bs[k]))} | {f2(med(hs[k]))} | {f3(q)} | {f2(med(nc[k])) if k in nc else '—'} | {f2(cost)} | "
                  f"{f3(med(hs[k]) / med(allbase[k]))} | {f3(ref['caves'][name(k)]) if name(k) in ref['caves'] else '—'} |")
        raw.append(f"caves {name(k)}: base {' '.join(map(str, bs[k]))}; head {' '.join(map(str, hs[k]))}")

    # 3. Stages without caves.
    for title, rx, div, lim, absl, key in (("FEATURES", SF, 64, "≤ 2,0 × M2 i ", 10, 'FEATURES'),
                                           ("LIGHT", SL, 64, "≤ 1,75 × M2 i ", 12, 'LIGHT'),
                                           ("cały chunk", SW, 1, "", 48, 'WHOLE')):
        bs, hs = series(runs(d, 'stages', 'base', rx)), series(runs(d, 'stages', 'head', rx))
        md.append(f"\n## {title}, ms na chunk (`stages -Psites=slice -Pcaves=off`, 20 obszarów miejsc wycinka); próg {lim}≤ {absl} ms\n\n"
                  f"| obszar | M2 | F7 (2026-11-11) | {N} | {N} / M2 | {N} / F7 |\n|---|---|---|---|---|---|")
        for k in bs:
            if k not in hs:
                continue
            mb, mh = med(bs[k]) / div, med(hs[k]) / div
            r7 = ref[key].get(name(k))
            md.append(f"| {name(k)} | {f2(mb)} | {f2(r7[0]) if r7 else '—'} | {f2(mh)} | {f3(mh / mb)} | {f3(mh / r7[0]) if r7 else '—'} |")
            raw.append(f"stages {title} {name(k)}: base {' '.join(map(str, bs[k]))}; head {' '.join(map(str, hs[k]))}")

    # 4. FPS with the pack (world with caves).
    bs, hs = series(runs(d, 'fly', 'base', F)), series(runs(d, 'fly', 'head', F))
    bg, hg = series(runs(d, 'fly', 'base', G)), series(runs(d, 'fly', 'head', G))
    md.append(f"\n## FPS z paczką (`-Poptimization`: Sodium, Nvidium, C2ME; świat z jaskiniami; okap = 42 bloki nad gruntem, oko = "
              f"wysokość oczu); próg ≥ 0,8 × M2, przeloty nad okapem lasów F1–F6 tylko w raporcie\n\n"
              f"| przelot | M2 FPS | {N} FPS | iloraz | M2 GPU ms/klatkę | {N} GPU ms/klatkę | {N} czas klatki − M2, ms | F7 |\n"
              f"|---|---|---|---|---|---|---|---|")
    for k in hs:
        if '/ tatra' in k:
            tatra[name(k)]['fps'] = med(hs[k])
            continue
        if k not in bs:
            continue
        q = med(hs[k]) / med(bs[k])
        md.append(f"| {name(k)} | {med(bs[k]):.0f} | {med(hs[k]):.0f} | {f3(q)} | {f2(med(bg[k])) if k in bg else '—'} | "
                  f"{f2(med(hg[k])) if k in hg else '—'} | {f3(1000 / med(hs[k]) - 1000 / med(bs[k]))} | "
                  f"{f3(ref['fps'][name(k)]) if name(k) in ref['fps'] else '—'} |")
        raw.append(f"fly {name(k)}: base {' '.join(map(str, bs[k]))}; head {' '.join(map(str, hs[k]))}")

    # 5. Tatra sites (KT-6): description only.
    if tatra:
        md.append(f"\n## Miejsca Tatr (KT-6, opis bez progu; M2 nie ma Tatr)\n\n"
                  f"| miejsce | chunki/s bez jaskiń | chunki/s z jaskiniami | koszt jaskiń, ms na chunk |\n|---|---|---|---|")
        for k in sorted(x for x in tatra if not x.endswith(' okap') and not x.endswith(' oko')):
            t = tatra[k]
            cost = 1000 / t['caves'] - 1000 / t['load'] if 'caves' in t and 'load' in t else None
            md.append(f"| {k} | {f2(t['load']) if 'load' in t else '—'} | {f2(t['caves']) if 'caves' in t else '—'} | "
                      f"{f2(cost) if cost is not None else '—'} |")
        flights = sorted(x for x in tatra if x.endswith(' okap') or x.endswith(' oko'))
        if flights:
            md.append("\n| przelot nad Tatrami | FPS z paczką |\n|---|---|")
            for k in flights:
                md.append(f"| {k} | {tatra[k]['fps']:.0f} |")

    # 6. MSPT of the fauna (KFA-6, ZW-8).
    scenes = collections.defaultdict(list)
    ticks = collections.defaultdict(list)
    for f in sorted(glob.glob(os.path.join(d, 'mspt_r*_head.log'))):
        for line in open(f, encoding='utf-8', errors='replace'):
            m = MS.search(line)
            if m:
                what = (m.group(2) or ' roe deer').strip()
                scenes[what].append(float(m.group(3)))
                x = MSB.search(line)
                if x:
                    ticks[what].append((float(x.group(1)), float(x.group(2))))
    if scenes:
        md.append("\n## MSPT fauny (KFA-6, ZW-8; `-Pgametest=fauna -Psites=mspt`, GAMEPLAY N, budżet ≤ 0,05 ms ticku serwera na zwierzę)\n\n"
                  "| scena | ms na zwierzę i tick (przebiegi) | mediana | budżet | werdykt | średni tick serwera przed → po, ms |\n"
                  "|---|---|---|---|---|---|")
        for what in ('roe deer', 'wild boar', 'red squirrel', 'Eurasian jay', 'common frog', 'mixed scene'):
            if what not in scenes:
                continue
            v = scenes[what]
            mv = med(v)
            tk = ', '.join(f"{f2(x)} → {f2(y)}" for x, y in ticks.get(what, [])) or '—'
            md.append(f"| {what} | {' / '.join(f4(x) for x in v)} | {f4(mv)} | 0,05 | {'PASS' if mv <= 0.05 else '**FAIL**'} | {tk} |")
            raw.append(f"mspt {what}: {' '.join(map(str, v))}")

    # 7. Flights with and without the fauna (head only; spawn_mobs on, forest_fauna on/off).
    on, off = series(runs(d, 'ffly', 'on', F)), series(runs(d, 'ffly', 'off', F))
    if on and off:
        ani = collections.defaultdict(list)
        for f in sorted(glob.glob(os.path.join(d, 'ffly_r*_on.log'))):
            for line in open(f, encoding='utf-8', errors='replace'):
                m = AN.search(line)
                if m:
                    ani[name(m.group(1))].append(f"{m.group(2)} {m.group(3)}, żaby {m.group(4)}")
        md.append(f"\n## Przeloty z fauną i bez (reguła `polishforests:forest_fauna`, `-PperfFauna=on|off`, z paczką, bez progu)\n\n"
                  f"| przelot | bez fauny | z fauną | iloraz | czas klatki z fauną − bez, ms | zwierzęta moda w obszarze przelotu |\n"
                  f"|---|---|---|---|---|---|")
        for k in off:
            if k not in on:
                continue
            q = med(on[k]) / med(off[k])
            site = name(k).rsplit(' ', 1)[0]
            md.append(f"| {name(k)} | {med(off[k]):.0f} | {med(on[k]):.0f} | {f3(q)} | {f3(1000 / med(on[k]) - 1000 / med(off[k]))} | "
                      f"{ani[site][0] if ani.get(site) else '—'} |")
            raw.append(f"ffly {name(k)}: off {' '.join(map(str, off[k]))}; on {' '.join(map(str, on[k]))}")

    if a.verdicts:
        md.append('\n' + open(a.verdicts, encoding='utf-8').read().rstrip())
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, 'w', encoding='utf-8', newline='\n') as o:
        o.write('\n'.join(md) + '\n')
    if a.raw:
        with open(a.raw, 'w', encoding='utf-8', newline='\n') as o:
            o.write('\n'.join(raw) + '\n')
    print(f"{a.out}: {len(md)} lines", file=sys.stderr)


if __name__ == '__main__':
    main()
