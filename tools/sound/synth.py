"""Signal primitives of tools/sound (step FA4 of the vertical slice): oscillators with frequency contours, envelopes,
noise, zero-phase filters in the frequency domain, a short forest reverb and mixing. Pure NumPy, mono float64 signals in
[-1, 1] at RATE samples per second; every random draw comes from a numpy Generator the recipe passes in, so a recipe
with its seed always gives the same samples.
"""
import numpy as np

RATE = 44100


def n_of(seconds):
    """Number of samples of a duration."""
    return max(0, int(round(seconds * RATE)))


def silence(seconds):
    return np.zeros(n_of(seconds))


def contour(points, seconds, smooth=0.0):
    """A value per sample from (time fraction 0-1, value) points, linear between them; optionally smoothed with a
    raised-cosine kernel of `smooth` seconds (glides without corners)."""
    n = n_of(seconds)
    ts = np.array([p[0] for p in points], float)
    vs = np.array([p[1] for p in points], float)
    x = np.interp(np.linspace(0.0, 1.0, n, endpoint=False) if n else np.zeros(0), ts, vs)
    if smooth > 0 and n > 3:
        k = max(3, n_of(smooth) | 1)
        w = 0.5 - 0.5 * np.cos(2 * np.pi * np.arange(k) / (k - 1))
        w /= w.sum()
        pad = k // 2
        x = np.convolve(np.pad(x, pad, mode='edge'), w, mode='valid')[:n]
    return x


def envelope(seconds, attack, release, shape=1.0):
    """Raised-cosine attack and release (seconds) around a flat middle; `shape` > 1 makes the release fall faster."""
    n = n_of(seconds)
    e = np.ones(n)
    a = min(n, n_of(attack))
    r = min(n - a, n_of(release))
    if a > 0:
        e[:a] = 0.5 - 0.5 * np.cos(np.pi * np.arange(a) / a)
    if r > 0:
        tail = 0.5 + 0.5 * np.cos(np.pi * np.arange(1, r + 1) / r)
        e[n - r:] = tail ** shape
    return e


def decay(seconds, tau, attack=0.001):
    """A quick attack and an exponential decay with time constant `tau` (seconds)."""
    n = n_of(seconds)
    t = np.arange(n) / RATE
    e = np.exp(-t / tau)
    a = min(n, n_of(attack))
    if a > 0:
        e[:a] *= np.linspace(0.0, 1.0, a, endpoint=False)
    return e


def osc(freq, harmonics=((1, 1.0),), phase=0.0):
    """Sum of sine harmonics over a per-sample frequency (Hz) array; each harmonic above Nyquist is left out."""
    ph = phase + 2 * np.pi * np.cumsum(freq) / RATE
    out = np.zeros(len(freq))
    for k, a in harmonics:
        h = np.sin(k * ph)
        h[k * freq >= RATE * 0.45] = 0.0
        out += a * h
    return out


def vibrato(freq, rate, depth, phase=0.0):
    """A frequency array with a sinusoidal vibrato of `rate` Hz and relative `depth`."""
    t = np.arange(len(freq)) / RATE
    return freq * (1.0 + depth * np.sin(2 * np.pi * rate * t + phase))


def note(seconds, f0, f1=None, harmonics=((1, 1.0),), attack=0.004, release=0.01, points=None, smooth=0.0,
         vib=None, shape=1.0):
    """A whistled note: a glide f0 -> f1 (or a contour of (fraction, Hz) points), harmonics, an envelope and an optional
    vibrato (rate Hz, depth)."""
    pts = points or [(0.0, f0), (1.0, f1 if f1 is not None else f0)]
    f = contour(pts, seconds, smooth)
    if vib:
        f = vibrato(f, *vib)
    return osc(f, harmonics) * envelope(seconds, attack, release, shape)


def noise(seconds, rng):
    return rng.standard_normal(n_of(seconds))


def _taper(freqs, edge, width, rising):
    """0-1 cosine step around `edge` (Hz) with `width` (Hz)."""
    if width <= 0:
        return (freqs >= edge).astype(float) if rising else (freqs < edge).astype(float)
    x = np.clip((freqs - (edge - width / 2)) / width, 0.0, 1.0)
    up = 0.5 - 0.5 * np.cos(np.pi * x)
    return up if rising else 1.0 - up


def bandpass(x, lo, hi, width=None):
    """Zero-phase band-pass in the frequency domain with cosine edges (`width` Hz, default a fifth of the band)."""
    if len(x) == 0:
        return x
    w = width if width is not None else max(50.0, (hi - lo) / 5)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / RATE)
    m = _taper(f, lo, w, True) * _taper(f, hi, w, False)
    return np.fft.irfft(X * m, len(x))


def lowpass(x, hi, width=None):
    return bandpass(x, 0.0, hi, width if width is not None else hi / 4)


def highpass(x, lo, width=None):
    return bandpass(x, lo, RATE / 2, width if width is not None else lo / 2)


def resonate(x, peaks):
    """Shapes a signal with resonances: `peaks` is a list of (centre Hz, Q, gain); the magnitude of each is that of a
    two-pole resonator, applied in the frequency domain (zero phase)."""
    if len(x) == 0:
        return x
    X = np.fft.rfft(x)
    f = np.maximum(np.fft.rfftfreq(len(x), 1.0 / RATE), 1.0)
    h = np.zeros(len(f))
    for f0, q, g in peaks:
        h += g / np.sqrt(1.0 + (q * (f / f0 - f0 / f)) ** 2)
    return np.fft.irfft(X * h, len(x))


def pulses(seconds, rate_hz, width, rng=None, jitter=0.0):
    """A train of raised-cosine pulses `rate_hz` per second (a frequency array or a number), each `width` seconds long;
    `jitter` shifts each pulse by a random fraction of its period."""
    n = n_of(seconds)
    rate = np.full(n, float(rate_hz)) if np.isscalar(rate_hz) else np.asarray(rate_hz, float)
    ph = np.cumsum(rate) / RATE
    starts = np.nonzero(np.diff(np.floor(ph), prepend=-1.0) > 0)[0]
    out = np.zeros(n)
    w = max(2, n_of(width))
    p = 0.5 - 0.5 * np.cos(2 * np.pi * np.arange(w) / (w - 1))
    for s in starts:
        if rng is not None and jitter > 0:
            s = int(s + rng.uniform(-jitter, jitter) * RATE / max(rate[min(s, n - 1)], 1e-6))
        if 0 <= s < n:
            e = min(n, s + w)
            out[s:e] += p[:e - s]
    return out


def place(track, clip, at, gain=1.0):
    """Adds `clip` into `track` at `at` seconds (the track grows when needed); returns the track."""
    s = n_of(at)
    end = s + len(clip)
    if end > len(track):
        track = np.concatenate([track, np.zeros(end - len(track))])
    track[s:end] += gain * clip
    return track


def reverb(x, rng, tau=0.25, wet=0.12, hi=6000.0):
    """A short diffuse tail (the forest around the singer): exponentially decaying low-passed noise as an impulse
    response, convolved in the frequency domain; `tau` seconds of decay, `wet` the share of the tail."""
    length = n_of(tau * 5)
    ir = rng.standard_normal(length) * np.exp(-np.arange(length) / (tau * RATE))
    ir = lowpass(ir, hi)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-12
    n = len(x) + length
    size = 1 << int(np.ceil(np.log2(n)))
    tail = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[:n]
    out = np.concatenate([x, np.zeros(length)]) * (1.0 - wet) + tail * wet
    return trim_tail(out)


def trim_tail(x, floor_db=-48.0):
    """Cuts the end of a signal where it stays below `floor_db` of its peak (keeps 20 ms)."""
    peak = np.max(np.abs(x)) if len(x) else 0.0
    if peak <= 0:
        return x
    above = np.nonzero(np.abs(x) > peak * 10 ** (floor_db / 20))[0]
    end = min(len(x), (above[-1] + 1 if len(above) else 0) + n_of(0.02))
    return x[:end]


def finish(x, peak=0.89, fade=0.006):
    """Removes DC, fades both ends and scales to `peak` (the files are normalised; the game sets the loudness)."""
    x = x - np.mean(x)
    f = min(len(x) // 2, n_of(fade))
    if f > 0:
        r = 0.5 - 0.5 * np.cos(np.pi * np.arange(f) / f)
        x[:f] *= r
        x[-f:] *= r[::-1]
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def to_pcm16(x):
    """Float samples to 16-bit integers (rounded, clipped)."""
    return np.clip(np.round(x * 32767.0), -32768, 32767).astype('<i2')
