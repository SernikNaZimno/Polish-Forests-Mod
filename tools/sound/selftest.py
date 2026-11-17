"""Self-test of tools/sound (run by check.py): the comparison of measure.py must tell sounds apart, so that checkSound
cannot pass a file that another recipe, variant or seed made, and the recipes must be deterministic.

Usage: python tools/sound/selftest.py
"""
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import measure  # noqa: E402
import recipes  # noqa: E402
import synth  # noqa: E402


def _voice(event):
    return next(v for v in recipes.VOICES if v.event == event)


def run():
    errors = []
    tit = _voice('ambient.forest.great_tit')
    a = recipes.render(tit, 1, tit.seed * 1000 + 1)
    if not np.array_equal(a, recipes.render(tit, 1, tit.seed * 1000 + 1)):
        errors.append('the great tit recipe is not deterministic')
    # A slight change (a quiet noise floor, as a lossy codec adds) passes.
    rng = np.random.default_rng(7)
    noisy = a + rng.standard_normal(len(a)) * 10 ** (-40 / 20) * np.sqrt(np.mean(a ** 2))
    errs, _ = measure.compare(a, noisy)
    if errs:
        errors.append(f'a copy with noise 40 dB down fails: {errs}')
    pairs = [
        ('another variant', a, recipes.render(tit, 2, tit.seed * 1000 + 2)),
        ('another seed', a, recipes.render(tit, 1, 999)),
        ('another voice', a, recipes.render(_voice('ambient.forest.coal_tit'), 1, 105001)),
        ('a delayed copy', a, np.concatenate([np.zeros(synth.n_of(0.15)), a])[:len(a) + measure.BLOCK]),
        ('half the level', a, a * 0.5),
        ('a low-passed copy', a, synth.lowpass(a, 3000)),
    ]
    for name, x, y in pairs:
        errs, m = measure.compare(x, y)
        if not errs:
            errors.append(f'{name} passes the comparison (envelope {m["envelope"]:.2f} dB, correlation {m["correlation"]:.3f})')
    owl = _voice('ambient.forest.tawny_owl')
    if measure.dominant_frequency(recipes.render(owl, 1, owl.seed * 1000 + 1)) > 600:
        errors.append('the hoot of the tawny owl is not low')
    return errors


if __name__ == '__main__':
    e = run()
    print('sound selftest: ' + ('PASS' if not e else 'FAILED\n  ' + '\n  '.join(e)))
    sys.exit(1 if e else 0)
