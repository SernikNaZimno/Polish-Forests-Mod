"""Check of tools/sound (Gradle checkSound, run by checkArt; step FA4, rule KFA-7, decision G.62-d): the committed sounds
must be what tools/sound/generate.py makes.

Checks, in order:
1. sounds.json and the license manifest are exactly the generator's (parsed JSON);
2. every file of the generator exists as OGG Vorbis, mono, at the generator's rate, and no other file lies in
   assets/polishforests/sounds/ (the directory the generator owns, besides manifest.json);
3. every manifest entry names a source, an author and a license (CC BY 4.0 for the synthesized files; a recording, if
   one ever comes from the user, would carry its own CC0 or CC BY without NC), and every file has one entry;
4. the decoded samples (JOrbis, Gradle decodeSound) match a fresh run of the generator with the tolerance of measure.py
   (length, level, band envelope); the numbers are printed for each file;
5. each sound measures what its recipe promises (duration and the band of the dominant frequency);
6. the self-test (selftest.py): the comparison fails for another recipe, variant or seed, and the recipes are
   deterministic.
Exit status 0 when everything passes, 1 otherwise; the fix is usually './gradlew generateSound'.

Usage: python tools/sound/check.py --decoded <dir of SoundCodec decode>   (./gradlew checkSound)
"""
import argparse
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import generate  # noqa: E402
import measure  # noqa: E402
import recipes  # noqa: E402
import selftest  # noqa: E402

ALLOWED_LICENSES = ('CC BY 4.0', 'CC0 1.0')


def read_json(rel):
    path = os.path.join(generate.ROOT, rel)
    if not os.path.exists(path):
        return None
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def read_decoded(decoded, rel):
    base = os.path.join(decoded, rel)
    if not os.path.exists(base + '.f32') or not os.path.exists(base + '.txt'):
        return None, None
    info = {}
    with open(base + '.txt', encoding='utf-8') as f:
        for line in f:
            k, v = line.split()
            info[k] = int(v)
    return np.fromfile(base + '.f32', dtype='<f4').astype(float), info


def run(decoded):
    errors, report = [], []
    for rel, want in ((generate.SOUNDS_JSON, generate.sounds_json()), (generate.MANIFEST, generate.manifest())):
        have = read_json(rel)
        if have is None:
            errors.append(f'{rel} is missing')
        elif have != json.loads(json.dumps(want)):
            errors.append(f'{rel} differs from the generator')
    manifest = read_json(generate.MANIFEST) or {'files': []}
    entries = {e.get('file'): e for e in manifest.get('files', [])}
    files = recipes.files()
    want_files = {f'{rel}.ogg' for _, _, rel, _ in files}
    for name, e in entries.items():
        for key in ('source', 'author', 'license'):
            if not e.get(key):
                errors.append(f'manifest: {name} has no {key}')
        if e.get('license') not in ALLOWED_LICENSES:
            errors.append(f'manifest: {name} has the license {e.get("license")!r} (allowed {ALLOWED_LICENSES})')
    for name in want_files - set(entries):
        errors.append(f'manifest: no entry for {name}')
    sounds_root = os.path.join(generate.ROOT, generate.SOUNDS_DIR)
    for dirpath, _, names in os.walk(sounds_root):
        for n in names:
            rel = os.path.relpath(os.path.join(dirpath, n), sounds_root).replace('\\', '/')
            if rel != 'manifest.json' and rel not in want_files:
                errors.append(f'{generate.SOUNDS_DIR}/{rel} is not made by tools/sound')
    worst = dict(envelope=0.0, correlation=1.0, level=0.0)
    for voice, n, rel, seed in files:
        x = recipes.render(voice, n, seed)
        d = measure.duration(x)
        f = measure.dominant_frequency(x)
        if not voice.duration[0] <= d <= voice.duration[1]:
            errors.append(f'{rel}: {d:.2f} s, the recipe promises {voice.duration[0]}-{voice.duration[1]} s')
        if not voice.band[0] <= f <= voice.band[1]:
            errors.append(f'{rel}: dominant frequency {f:.0f} Hz, the recipe promises {voice.band[0]}-{voice.band[1]} Hz')
        ogg = os.path.join(sounds_root, rel + '.ogg')
        if not os.path.exists(ogg):
            errors.append(f'{generate.SOUNDS_DIR}/{rel}.ogg is missing')
            continue
        y, info = read_decoded(decoded, rel)
        if y is None:
            errors.append(f'{rel}: no decoded samples (run ./gradlew checkSound, which decodes first)')
            continue
        if info.get('channels') != 1 or info.get('rate') != generate.synth.RATE:
            errors.append(f'{rel}.ogg: {info.get("channels")} channels at {info.get("rate")} Hz (mono at {generate.synth.RATE})')
        reference = generate.synth.to_pcm16(x).astype(float) / 32768.0
        errs, m = measure.compare(reference, y)
        errors += [f'{rel}.ogg: {e}' for e in errs]
        worst['envelope'] = max(worst['envelope'], m['envelope'])
        worst['correlation'] = min(worst['correlation'], m['correlation'])
        worst['level'] = max(worst['level'], abs(m['level']))
        report.append(f'  {rel + ".ogg":42s} {d:5.2f} s {f:6.0f} Hz {os.path.getsize(ogg) / 1024:5.1f} KiB; decoded: level '
                      f'{m["level"]:+.2f} dB, envelope {m["envelope"]:.2f} dB, correlation {m["correlation"]:.4f}, '
                      f'SNR {m["snr"]:5.1f} dB, length {m["length"]:+d}')
    report.append(f'  worst: envelope {worst["envelope"]:.2f} dB (tolerance {measure.ENVELOPE_DB}), correlation '
                  f'{worst["correlation"]:.4f} (at least {measure.MIN_CORRELATION}), level {worst["level"]:.2f} dB '
                  f'(tolerance {measure.LEVEL_DB})')
    errors += ['selftest: ' + e for e in selftest.run()]
    return errors, report


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--decoded', required=True)
    a = ap.parse_args()
    errors, report = run(a.decoded)
    print(f'tools/sound: {len(recipes.files())} files of {len(recipes.VOICES)} sound events')
    print('\n'.join(report))
    if errors:
        print(f'sound check: FAILED, {len(errors)} problem(s):')
        for e in errors:
            print('  ' + e)
        print('Regenerate with: ./gradlew generateSound, then look at the diff.')
        sys.exit(1)
    print('sound check: the sounds match tools/sound (decoded samples within the tolerance), every file has a license')


if __name__ == '__main__':
    main()
