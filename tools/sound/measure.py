"""Measures of tools/sound (step FA4): the duration and dominant frequency of a sound (what a recipe must measure, the
`duration` and `band` of its Voice), and the comparison of a decoded OGG Vorbis file with the generator's samples
(checkSound): Vorbis is lossy, so the samples are compared with a tolerance, never byte for byte:

  - the length: the decoded file may be longer by at most one Vorbis block (the decoder's last block), never shorter
    than the generator output minus one block;
  - the level: RMS within LEVEL_DB;
  - the band envelope: log-magnitude spectrogram in BANDS logarithmic bands over FRAME-sample frames; on the cells within
    DYNAMIC_DB of the loudest cell, the mean absolute difference must stay below ENVELOPE_DB and the correlation of the
    two spectrograms above MIN_CORRELATION (a different recipe, variant or seed fails all of these, see selftest.py);
  - the waveform signal-to-noise ratio is reported (Vorbis replaces noisy bands by shaped noise, so it is no criterion).
"""
import numpy as np

from synth import RATE

FRAME = 1024
HOP = 512
BANDS = 32
LOW_HZ = 60.0
DYNAMIC_DB = 45.0
ENVELOPE_DB = 3.0
MIN_CORRELATION = 0.90  # Vorbis at q 0.4: 0.948 (roe deer bark, flat noisy spectrum) and above; another file: at most 0.62
LEVEL_DB = 1.0
BLOCK = 2048


def duration(x):
    return len(x) / RATE


def dominant_frequency(x):
    """The frequency (Hz) of the largest peak of the mean power spectrum over 4096-sample frames (Hann window)."""
    n = 4096
    if len(x) < n:
        x = np.pad(x, (0, n - len(x)))
    w = np.hanning(n)
    acc = np.zeros(n // 2 + 1)
    for start in range(0, len(x) - n + 1, n // 2):
        acc += np.abs(np.fft.rfft(x[start:start + n] * w)) ** 2
    acc[:int(30 * n / RATE)] = 0.0
    return float(np.argmax(acc) * RATE / n)


def _edges():
    return np.geomspace(LOW_HZ, RATE / 2, BANDS + 1)


def band_spectrogram(x):
    """Log-magnitude (dB) spectrogram in BANDS log bands, frames of FRAME samples every HOP."""
    if len(x) < FRAME:
        x = np.pad(x, (0, FRAME - len(x)))
    w = np.hanning(FRAME)
    frames = 1 + (len(x) - FRAME) // HOP
    idx = np.arange(FRAME)[None, :] + HOP * np.arange(frames)[:, None]
    spec = np.abs(np.fft.rfft(x[idx] * w, axis=1)) ** 2
    f = np.fft.rfftfreq(FRAME, 1.0 / RATE)
    edges = _edges()
    out = np.empty((frames, BANDS))
    for b in range(BANDS):
        m = (f >= edges[b]) & (f < edges[b + 1])
        if not m.any():
            m = np.zeros(len(f), bool)
            m[min(len(f) - 1, int(np.searchsorted(f, edges[b])))] = True
        out[:, b] = spec[:, m].sum(axis=1)
    return 10 * np.log10(out + 1e-12)


def rms_db(x):
    return 10 * np.log10(np.mean(x ** 2) + 1e-20)


def compare(generated, decoded):
    """(errors, numbers) of a decoded file against the generator's samples (see the module comment)."""
    errors = []
    g = np.asarray(generated, float)
    d = np.asarray(decoded, float)
    if len(d) < len(g) - BLOCK or len(d) > len(g) + BLOCK:
        errors.append(f'length {len(d)} samples, the generator makes {len(g)} (tolerance one block, {BLOCK})')
    n = min(len(g), len(d))
    g, d = g[:n], d[:n]
    level = rms_db(d) - rms_db(g)
    if abs(level) > LEVEL_DB:
        errors.append(f'level differs by {level:+.2f} dB (tolerance {LEVEL_DB} dB)')
    sg, sd = band_spectrogram(g), band_spectrogram(d)
    mask = sg > sg.max() - DYNAMIC_DB
    diff = float(np.mean(np.abs(sg[mask] - sd[mask]))) if mask.any() else 0.0
    if diff > ENVELOPE_DB:
        errors.append(f'band envelope differs by {diff:.2f} dB on average (tolerance {ENVELOPE_DB} dB)')
    a = sg[mask] - sg[mask].mean()
    b = sd[mask] - sd[mask].mean()
    corr = float(np.sum(a * b) / (np.sqrt(np.sum(a * a) * np.sum(b * b)) + 1e-12)) if mask.any() else 1.0
    if corr < MIN_CORRELATION:
        errors.append(f'band envelope correlation {corr:.3f} (at least {MIN_CORRELATION})')
    snr = 10 * np.log10(np.sum(g ** 2) / (np.sum((g - d) ** 2) + 1e-20))
    return errors, dict(level=level, envelope=diff, correlation=corr, snr=snr, length=len(decoded) - len(generated))
