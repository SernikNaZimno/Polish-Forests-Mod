"""Recipes of the forest sounds (step FA4, decision PW-16, G.62-d): each voice is synthesized from a description of the
call (field-guide terms: note frequencies, lengths, repetitions, glides, pulse rates), never from a recording. A recipe
is a function (numpy Generator, variant number) -> mono float samples; generate.py runs it with a fixed seed per file,
so the files are reproducible. `expect` gives what the sound must measure (duration in seconds, the band of its
dominant frequency in Hz); check.py and selftest.py verify the generator against it.

The voices (docs/04-pionowy-wycinek.md "Projekt FA4" explains the choice from PW-16):
  birds of the broadleaf and mixed forests: chaffinch, blackbird, robin, great tit, cuckoo, great spotted woodpecker
  (drumming and its call), tawny owl (night), jay (screech; also the alarm a running player raises);
  birds of the pine and spruce forests: coal tit and goldcrest (PW-16 names them for the pine forests);
  animals: the roe deer's bark, the wild boar's grunt and snort, the common frog's croak.
"""
from dataclasses import dataclass, field

import numpy as np

import synth as s


@dataclass(frozen=True)
class Voice:
    """A sound event of the mod: its id under assets/polishforests (sounds.json), the files (variants), the recipe and
    what it must measure. The subtitles (en_us, pl_pl) are in pl.polishforests.sound.ModSounds, with the same events."""
    event: str                 # e.g. 'ambient.forest.chaffinch'
    path: str                  # file stem under sounds/, e.g. 'ambient/forest/chaffinch' (+ '_<n>.ogg')
    variants: int
    recipe: object
    duration: tuple            # (least, most) seconds
    band: tuple                # (low, high) Hz of the dominant frequency
    description: str
    seed: int = 0
    extra: dict = field(default_factory=dict)


# ---------------------------------------------------------------------------------------------------------- songbirds

def chaffinch(rng, v):
    """Chaffinch (Fringilla coelebs): a 2-2.7 s descending, accelerating trill of 3 sections and the terminal flourish
    ("chip-chip-chip-tell-tell-cherry-erri-ti-chew-EEo"): notes 6-3 kHz falling, the flourish a 3 -> 5 -> 2.6 kHz glide."""
    out = s.silence(0.05)
    t = 0.04
    top = rng.uniform(6200, 6800)
    sections = [(rng.integers(4, 6), 0.075, top, 0.80), (rng.integers(3, 5), 0.062, top * 0.80, 0.85),
                (rng.integers(3, 5), 0.090, top * 0.62, 0.95)]
    for count, gap, f, amp in sections:
        for i in range(count):
            d = gap * 0.62
            f_hi = f * (1.0 - 0.03 * i)
            n = s.note(d, f_hi * 1.04, f_hi * 0.78, ((1, 1.0), (2, 0.12)), attack=0.003, release=0.012)
            out = s.place(out, n, t, amp)
            t += gap
        t += 0.02
    fl = 0.30 + rng.uniform(0, 0.06)
    flourish = s.note(fl, 0, points=[(0.0, 3000), (0.35, 5200 + 300 * v), (0.7, 3800), (1.0, 2600)],
                      harmonics=((1, 1.0), (2, 0.18)), attack=0.01, release=0.05, smooth=0.03, vib=(38, 0.04))
    out = s.place(out, flourish, t + 0.01, 1.0)
    return s.reverb(out, rng, tau=0.18, wet=0.10)


def blackbird(rng, v):
    """Blackbird (Turdus merula): a mellow, fluting phrase of 4-7 slurred low notes (1.5-2.9 kHz, 0.12-0.3 s each,
    gliding), ending in a short, quieter, squeaky twitter (4-6 kHz chirps)."""
    out = s.silence(0.04)
    t = 0.03
    for i in range(int(rng.integers(4, 8))):
        d = rng.uniform(0.12, 0.30)
        f0 = rng.uniform(1500, 2600)
        f1 = f0 * rng.uniform(0.85, 1.25)
        mid = (f0 + f1) / 2 * rng.uniform(0.95, 1.12)
        n = s.note(d, 0, points=[(0.0, f0), (0.5, mid), (1.0, f1)], harmonics=((1, 1.0), (2, 0.25), (3, 0.06)),
                   attack=0.02, release=0.05, smooth=0.04, vib=(rng.uniform(18, 28), 0.012))
        out = s.place(out, n, t, rng.uniform(0.7, 1.0))
        t += d + rng.uniform(0.01, 0.05)
    t += 0.05
    for i in range(int(rng.integers(3, 7))):
        d = rng.uniform(0.025, 0.05)
        f = rng.uniform(4000, 6000)
        out = s.place(out, s.note(d, f, f * rng.uniform(0.7, 1.3), ((1, 1.0),), 0.002, 0.008), t, 0.35)
        t += d + rng.uniform(0.01, 0.03)
    return s.reverb(out, rng, tau=0.25, wet=0.14)


def robin(rng, v):
    """Robin (Erithacus rubecula): a liquid, varied warble of thin high notes (2.5-7.5 kHz): quick runs of short notes,
    a trill and a long thin whistle, in 2-3 phrases with small pauses (about 2 s)."""
    out = s.silence(0.03)
    t = 0.02
    for phrase in range(int(rng.integers(2, 4))):
        kind = rng.integers(0, 3)
        if kind == 0:       # run of short notes
            for i in range(int(rng.integers(4, 9))):
                d = rng.uniform(0.03, 0.07)
                f = rng.uniform(3000, 7000)
                out = s.place(out, s.note(d, f, f * rng.uniform(0.75, 1.3), ((1, 1.0), (2, 0.08)), 0.003, 0.01), t,
                              rng.uniform(0.6, 1.0))
                t += d + rng.uniform(0.005, 0.02)
        elif kind == 1:     # trill
            d = rng.uniform(0.25, 0.45)
            f = rng.uniform(3500, 6000)
            fr = s.vibrato(s.contour([(0, f), (1, f * rng.uniform(0.85, 1.15))], d), rng.uniform(25, 40), 0.12)
            out = s.place(out, s.osc(fr, ((1, 1.0), (2, 0.06))) * s.envelope(d, 0.01, 0.04), t, 0.8)
            t += d
        else:               # thin whistle
            d = rng.uniform(0.25, 0.5)
            f = rng.uniform(5500, 7500)
            out = s.place(out, s.note(d, f, f * rng.uniform(0.9, 1.05), ((1, 1.0),), 0.03, 0.08, smooth=0.03), t, 0.7)
            t += d
        t += rng.uniform(0.12, 0.3)
    return s.reverb(out, rng, tau=0.2, wet=0.12)


def tit_song(rng, hi, lo, d_hi, d_lo, reps, gap, amp_lo=0.85):
    """Two-note song of a tit ("tea-cher tea-cher"): a high note and a lower one, repeated."""
    out = s.silence(0.03)
    t = 0.02
    for i in range(reps):
        a = s.note(d_hi, hi * 1.02, hi * 0.97, ((1, 1.0), (2, 0.1)), 0.004, 0.015)
        b = s.note(d_lo, lo * 1.04, lo * 0.94, ((1, 1.0), (2, 0.15)), 0.004, 0.02)
        out = s.place(out, a, t, 1.0)
        out = s.place(out, b, t + d_hi + 0.015, amp_lo)
        t += d_hi + d_lo + 0.015 + gap
    return out


def great_tit(rng, v):
    """Great tit (Parus major): the "tea-cher" song, a high note (5-6.5 kHz, 0.1 s) and a lower one (3.2-4.2 kHz,
    0.12 s), 4-7 times; the variants differ in the pitch pair and the tempo."""
    hi = rng.uniform(5000, 6400)
    lo = rng.uniform(3200, 4200)
    out = tit_song(rng, hi, lo, rng.uniform(0.08, 0.11), rng.uniform(0.10, 0.13), int(rng.integers(4, 8)),
                   rng.uniform(0.06, 0.12))
    return s.reverb(out, rng, tau=0.18, wet=0.10)


def coal_tit(rng, v):
    """Coal tit (Periparus ater): a faster, thinner "pitchu-pitchu" than the great tit, the high note 6.5-7.5 kHz and
    the low one 4.6-5.4 kHz, 3-6 times."""
    out = tit_song(rng, rng.uniform(6500, 7500), rng.uniform(4600, 5400), rng.uniform(0.05, 0.07), rng.uniform(0.07, 0.09),
                   int(rng.integers(3, 7)), rng.uniform(0.03, 0.06), amp_lo=0.95)
    return s.reverb(out, rng, tau=0.16, wet=0.10)


def goldcrest(rng, v):
    """Goldcrest (Regulus regulus): very high, thin cyclic notes (7-8.4 kHz) "zee-da zee-da" 4-6 times, ending in a quick
    flourish; quiet."""
    out = s.silence(0.03)
    t = 0.02
    for i in range(int(rng.integers(4, 7))):
        a = s.note(0.06, 8200, 7600, ((1, 1.0),), 0.006, 0.015)
        b = s.note(0.05, 7200, 7500, ((1, 1.0),), 0.006, 0.015)
        out = s.place(out, a, t, 0.9)
        out = s.place(out, b, t + 0.07, 0.75)
        t += 0.15
    for i in range(int(rng.integers(3, 6))):
        f = rng.uniform(6500, 8400)
        out = s.place(out, s.note(0.035, f, f * rng.uniform(0.85, 1.1), ((1, 1.0),), 0.003, 0.01), t, 0.8)
        t += 0.045
    return s.reverb(out, rng, tau=0.15, wet=0.08)


def cuckoo(rng, v):
    """Common cuckoo (Cuculus canorus): the hollow "cu-coo", two notes a minor or major third apart (about 650 and
    540 Hz, 0.2 and 0.3 s), 2-4 times about 0.75 s apart."""
    out = s.silence(0.03)
    t = 0.02
    hi = rng.uniform(620, 700)
    lo = hi / rng.choice([1.19, 1.26])
    for i in range(int(rng.integers(2, 5))):
        a = s.note(0.20, hi * 1.01, hi * 0.98, ((1, 1.0), (2, 0.10), (3, 0.03)), 0.03, 0.06, smooth=0.02)
        b = s.note(0.32, lo * 1.01, lo * 0.96, ((1, 1.0), (2, 0.08), (3, 0.02)), 0.04, 0.14, smooth=0.02)
        out = s.place(out, a, t, 1.0)
        out = s.place(out, b, t + 0.26, 0.9)
        t += rng.uniform(0.70, 0.82)
    return s.reverb(out, rng, tau=0.3, wet=0.16, hi=3000)


# ---------------------------------------------------------------------------------------------------------- woodpecker

def woodpecker_drum(rng, v):
    """Great spotted woodpecker (Dendrocopos major) drumming on a dead branch: a 0.45-0.75 s roll of 10-16 strikes at
    16-22 per second, slowing a little and fading at the end; each strike a short knock with the resonance of the wood
    (200-1500 Hz)."""
    rate = rng.uniform(17, 22)
    count = int(rng.integers(10, 17))
    out = s.silence(0.02)
    t = 0.01
    body = rng.uniform(260, 420)
    for i in range(count):
        n = s.noise(0.05, rng) * s.decay(0.05, 0.006)
        knock = s.resonate(n, [(body, 6, 1.0), (body * 2.6, 5, 0.6), (1300, 2, 0.35)])
        amp = 1.0 if i < count - 4 else 1.0 - 0.15 * (i - count + 5)
        out = s.place(out, knock, t, amp)
        t += 1.0 / (rate * (1.0 - 0.012 * i))
    return s.reverb(out, rng, tau=0.2, wet=0.2, hi=4000)


def woodpecker_call(rng, v):
    """Great spotted woodpecker call: a sharp "kix" (40-60 ms, energy 1.5-4 kHz), once to three times."""
    out = s.silence(0.02)
    t = 0.01
    for i in range(int(rng.integers(1, 4))):
        d = rng.uniform(0.04, 0.06)
        f = rng.uniform(1800, 2600)
        tone = s.note(d, f * 1.3, f, ((1, 1.0), (2, 0.5), (3, 0.25)), 0.002, 0.02)
        rasp = s.bandpass(s.noise(d, rng), 1500, 4500) * s.decay(d, 0.015) * 0.25
        out = s.place(out, tone + rasp, t, 1.0)
        t += rng.uniform(0.35, 0.7)
    return s.reverb(out, rng, tau=0.3, wet=0.15)


# ---------------------------------------------------------------------------------------------------------- night

def tawny_owl(rng, v):
    """Tawny owl (Strix aluco). Variants 1-2: the male's hoot, a long "hooo" (0.6-0.8 s, 380-460 Hz, rising and
    falling), a pause, a short "hu" and a tremulous "hu-hu-huhooooo" (1.2 s with a tremolo of 9 Hz). Variant 3: the
    female's sharp "ke-wick" (two noisy notes about 1.1-1.8 kHz)."""
    if v == 3:
        out = s.silence(0.03)
        for i in range(int(rng.integers(1, 3))):
            ke = s.note(0.09, 1350, 1150, ((1, 1.0), (2, 0.5), (3, 0.3)), 0.005, 0.03)
            ke += s.bandpass(s.noise(0.09, rng), 900, 3000) * s.envelope(0.09, 0.005, 0.04) * 0.5
            wick = s.note(0.22, 0, points=[(0, 1200), (0.4, 1750), (1, 1450)], harmonics=((1, 1.0), (2, 0.45), (3, 0.2)),
                          attack=0.01, release=0.08, smooth=0.03)
            wick += s.bandpass(s.noise(0.22, rng), 900, 3200) * s.envelope(0.22, 0.01, 0.08) * 0.4
            out = s.place(out, ke, 0.02 + i * 1.1, 0.9)
            out = s.place(out, wick, 0.14 + i * 1.1, 1.0)
        return s.reverb(out, rng, tau=0.35, wet=0.22, hi=4000)
    f = rng.uniform(380, 450)
    hoot_d = rng.uniform(0.6, 0.8)
    hoot = s.note(hoot_d, 0, points=[(0, f * 0.92), (0.25, f * 1.04), (1, f * 0.95)], harmonics=((1, 1.0), (2, 0.18), (3, 0.05)),
                  attack=0.06, release=0.25, smooth=0.05)
    out = s.place(s.silence(0.03), hoot, 0.02, 1.0)
    t = 0.02 + hoot_d + rng.uniform(0.9, 1.4)
    out = s.place(out, s.note(0.12, f * 0.98, f * 0.95, ((1, 1.0), (2, 0.15)), 0.02, 0.06), t, 0.6)
    t += 0.35
    trem_d = rng.uniform(1.1, 1.35)
    fr = s.contour([(0, f * 0.96), (0.3, f * 1.03), (1, f * 0.94)], trem_d, 0.05)
    trem = s.osc(fr, ((1, 1.0), (2, 0.16), (3, 0.04))) * s.envelope(trem_d, 0.03, 0.4)
    am = 0.55 + 0.45 * np.cos(2 * np.pi * 9.0 * np.arange(len(trem)) / s.RATE) ** 2
    am[len(am) * 2 // 5:] = np.maximum(am[len(am) * 2 // 5:], 0.95)
    out = s.place(out, trem * am, t, 0.95)
    return s.reverb(out, rng, tau=0.4, wet=0.25, hi=2500)


# ---------------------------------------------------------------------------------------------------------- jay

def jay(rng, v):
    """Eurasian jay (Garrulus glandarius): the harsh rasping screech "kschaaak" (0.35-0.6 s of noise 0.8-5 kHz with a
    rough amplitude modulation of 40-70 Hz and a falling formant), once to three times."""
    out = s.silence(0.03)
    t = 0.02
    for i in range(int(rng.integers(1, 4))):
        d = rng.uniform(0.35, 0.6)
        n = s.noise(d, rng)
        f0 = rng.uniform(1600, 2200)
        band = s.resonate(n, [(f0, 2.5, 1.0), (f0 * 1.9, 3, 0.6), (f0 * 0.55, 2, 0.35)])
        band = s.bandpass(band, 700, 6000)
        am = 0.45 + 0.55 * np.abs(np.sin(np.pi * np.cumsum(np.full(len(n), rng.uniform(40, 70))) / s.RATE))
        tone = s.note(d, f0 * 1.05, f0 * 0.85, ((1, 0.4), (2, 0.3), (3, 0.2)), 0.01, 0.1)
        sc = (band / (np.max(np.abs(band)) + 1e-9) + tone * 0.35) * am * s.envelope(d, 0.015, d * 0.4, 1.5)
        out = s.place(out, sc, t, rng.uniform(0.8, 1.0))
        t += d + rng.uniform(0.15, 0.4)
    return s.reverb(out, rng, tau=0.3, wet=0.15)


# ---------------------------------------------------------------------------------------------------------- mammals

def roe_deer_bark(rng, v):
    """Roe deer (Capreolus capreolus) alarm bark "bö": a short gruff bark (0.15-0.25 s) with a fundamental of 300-450 Hz
    falling, rough (noisy), formants about 700 and 1400 Hz; one or two barks 0.8-1.2 s apart."""
    out = s.silence(0.03)
    t = 0.02
    for i in range(int(rng.integers(1, 3))):
        d = rng.uniform(0.15, 0.25)
        f = rng.uniform(300, 450)
        fr = s.contour([(0, f * 1.15), (0.3, f), (1, f * 0.75)], d)
        fr = fr * (1 + 0.04 * rng.standard_normal(len(fr)).cumsum() / np.sqrt(np.arange(1, len(fr) + 1)))
        voiced = s.osc(fr, [(k, 1.0 / k) for k in range(1, 12)])
        rough = s.noise(d, rng) * 0.35
        b = s.resonate(voiced + rough, [(720, 4, 1.0), (1400, 5, 0.6), (2600, 6, 0.2)])
        b *= s.envelope(d, 0.008, d * 0.5, 1.5)
        out = s.place(out, b, t, 1.0)
        t += d + rng.uniform(0.8, 1.2)
    return s.reverb(out, rng, tau=0.35, wet=0.15, hi=4000)


def wild_boar_grunt(rng, v):
    """Wild boar (Sus scrofa) contact grunts: 2-5 low grunts (0.15-0.4 s) of glottal pulses at 70-140 Hz through nasal
    formants (250-600 Hz), irregular, with breath noise."""
    out = s.silence(0.03)
    t = 0.02
    for i in range(int(rng.integers(2, 6))):
        d = rng.uniform(0.15, 0.4)
        f = rng.uniform(70, 140)
        rate = s.contour([(0, f * 0.9), (0.4, f * 1.1), (1, f * 0.8)], d) * (1 + 0.08 * rng.standard_normal(s.n_of(d)))
        p = s.pulses(d, rate, 0.004, rng, 0.08)
        breath = s.lowpass(s.noise(d, rng), 1200) * 0.25
        g = s.resonate(p + breath, [(260, 3, 1.0), (520, 4, 0.7), (1100, 5, 0.25)])
        g *= s.envelope(d, 0.02, d * 0.4)
        out = s.place(out, g, t, rng.uniform(0.6, 1.0))
        t += d + rng.uniform(0.08, 0.35)
    return s.reverb(out, rng, tau=0.25, wet=0.1, hi=3000)


def wild_boar_snort(rng, v):
    """Wild boar alarm snort ("wuff"): a forceful nasal blast (0.25-0.4 s) of noise 150-2500 Hz with a falling low
    resonance and a quick decay, sometimes doubled."""
    out = s.silence(0.03)
    t = 0.02
    for i in range(int(rng.integers(1, 3))):
        d = rng.uniform(0.25, 0.4)
        n = s.noise(d, rng)
        f0 = rng.uniform(260, 360)
        blast = s.resonate(n, [(f0, 2.0, 1.0), (f0 * 2.4, 2.5, 0.6), (1500, 1.5, 0.3)])
        blast = s.bandpass(blast, 120, 2800)
        thump = s.note(d, f0 * 0.5, f0 * 0.3, ((1, 1.0), (2, 0.4)), 0.004, d * 0.7, shape=2.0) * 0.6
        sn = (blast / (np.max(np.abs(blast)) + 1e-9) + thump) * s.decay(d, d * 0.35, 0.006)
        out = s.place(out, sn, t, 1.0)
        t += d + rng.uniform(0.25, 0.5)
    return s.reverb(out, rng, tau=0.25, wet=0.1, hi=3000)


def common_frog(rng, v):
    """Common frog (Rana temporaria) advertisement call: a soft, low purring croak "rrroo-rrroo": 2-5 bursts (0.3-0.6 s)
    of pulses at 12-25 per second, each pulse a short note of 380-650 Hz with harmonics; a chorus variant (v 3) overlaps
    three callers."""
    def caller(seed_shift):
        out = s.silence(0.03)
        t = 0.02 + seed_shift
        for i in range(int(rng.integers(2, 6))):
            d = rng.uniform(0.3, 0.6)
            f = rng.uniform(380, 650)
            rate = rng.uniform(12, 25)
            env = s.pulses(d, rate, min(0.045, 0.8 / rate), rng, 0.05)
            fr = s.contour([(0, f), (1, f * rng.uniform(0.9, 1.08))], d)
            tone = s.osc(fr, ((1, 1.0), (2, 0.45), (3, 0.2), (4, 0.08)))
            c = s.resonate(tone, [(f * 1.2, 2, 1.0), (1400, 3, 0.25)]) * env * s.envelope(d, 0.02, 0.08)
            out = s.place(out, c, t, rng.uniform(0.6, 1.0))
            t += d + rng.uniform(0.15, 0.5)
        return out
    out = caller(0.0)
    if v == 3:
        out = s.place(out, caller(0.0), rng.uniform(0.2, 0.5), 0.6)
        out = s.place(out, caller(0.0), rng.uniform(0.5, 0.9), 0.45)
    return s.reverb(out, rng, tau=0.25, wet=0.12, hi=3000)


# ---------------------------------------------------------------------------------------------------------- the voices

AMBIENT = 'ambient/forest'
VOICES = [
    Voice('ambient.forest.chaffinch', f'{AMBIENT}/chaffinch', 3, chaffinch,
          (1.8, 3.4), (2200, 7200), 'chaffinch song, descending trill with the terminal flourish', 101),
    Voice('ambient.forest.blackbird', f'{AMBIENT}/blackbird', 3, blackbird,
          (1.0, 4.0), (1300, 3200), 'blackbird song, fluting low phrase and a squeaky end', 102),
    Voice('ambient.forest.robin', f'{AMBIENT}/robin', 3, robin,
          (0.8, 4.0), (2500, 8000), 'robin song, liquid warble of thin high notes', 103),
    Voice('ambient.forest.great_tit', f'{AMBIENT}/great_tit', 3, great_tit,
          (1.0, 3.2), (3000, 6800), 'great tit "tea-cher" song', 104),
    Voice('ambient.forest.coal_tit', f'{AMBIENT}/coal_tit', 2, coal_tit,
          (0.5, 2.2), (4400, 7800), 'coal tit "pitchu" song', 105),
    Voice('ambient.forest.goldcrest', f'{AMBIENT}/goldcrest', 2, goldcrest,
          (0.7, 1.8), (6400, 8600), 'goldcrest high cyclic song', 106),
    Voice('ambient.forest.cuckoo', f'{AMBIENT}/cuckoo', 2, cuckoo,
          (1.2, 4.5), (450, 750), 'cuckoo "cu-coo" call', 107),
    Voice('ambient.forest.woodpecker_drum', f'{AMBIENT}/woodpecker_drum', 3,
          woodpecker_drum, (0.4, 2.0), (180, 1600), 'great spotted woodpecker drumming roll', 108),
    Voice('ambient.forest.woodpecker_call', f'{AMBIENT}/woodpecker_call', 2,
          woodpecker_call, (0.1, 3.0), (1500, 5500), 'great spotted woodpecker "kix" call', 109),
    Voice('ambient.forest.tawny_owl', f'{AMBIENT}/tawny_owl', 3, tawny_owl,
          (0.8, 5.5), (350, 1900), 'tawny owl hoot of the male; the female "ke-wick"', 110),
    Voice('ambient.forest.jay', f'{AMBIENT}/jay', 3, jay,
          (0.4, 2.6), (700, 4500), 'jay screech', 111),
    Voice('entity.common_frog.croak', 'entity/common_frog/croak', 3,
          common_frog, (0.5, 6.0), (350, 1500), 'common frog purring croak; the third file a small chorus', 112),
    Voice('entity.roe_deer.bark', 'entity/roe_deer/bark', 3, roe_deer_bark,
          (0.15, 3.5), (250, 1600), 'roe deer alarm bark', 113),
    Voice('entity.wild_boar.grunt', 'entity/wild_boar/grunt', 3, wild_boar_grunt,
          (0.4, 4.5), (60, 700), 'wild boar contact grunts', 114),
    Voice('entity.wild_boar.snort', 'entity/wild_boar/snort', 2, wild_boar_snort,
          (0.2, 2.6), (80, 1200), 'wild boar alarm snort', 115),
]


def files():
    """(voice, variant number 1.., relative path under sounds/ without extension, seed) of every file."""
    out = []
    for voice in VOICES:
        for n in range(1, voice.variants + 1):
            out.append((voice, n, f'{voice.path}_{n}', voice.seed * 1000 + n))
    return out


def render(voice, variant, seed):
    """The finished samples of one file."""
    rng = np.random.default_rng(seed)
    return s.finish(voice.recipe(rng, variant))
