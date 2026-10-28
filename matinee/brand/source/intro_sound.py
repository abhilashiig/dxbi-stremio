"""Matinee's start-up sound: a "ta-DUM" thump with a hall tail, synthesized from scratch (no samples).

    python3 intro_sound.py out.wav
    ffmpeg -y -i out.wav -c:a libvorbis -q:a 6 ../../tv/app/src/main/res/raw/intro.ogg

The two hits line up with the intro animation (ui/Intro.kt): "ta" at 0 ms as the M starts drawing,
"DUM" at 450 ms as it lands.
"""
import sys

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter

SR = 48_000
t = np.arange(int(SR * 3.4)) / SR


def hit(at, f_start, f_end, pitch_tau, decay, amp):
    # A sine whose pitch falls fast, like a struck drum skin
    tt = np.clip(t - at, 0, None)
    freq = f_end + (f_start - f_end) * np.exp(-tt / pitch_tau)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    env = np.exp(-tt / decay) * (1 - np.exp(-tt / 0.002)) * (t >= at)
    return amp * np.sin(phase) * env


def lowpassed_noise(at, decay, cutoff, amp, seed):
    rng = np.random.default_rng(seed)
    b, a = butter(2, cutoff / (SR / 2))
    tt = np.clip(t - at, 0, None)
    return amp * lfilter(b, a, rng.standard_normal(len(t))) * np.exp(-tt / decay) * (t >= at)


def swell(at, freqs, attack, decay, amp):
    # A warm chord that blooms under the boom, the "light comes up" moment
    tt = np.clip(t - at, 0, None)
    env = (1 - np.exp(-tt / attack)) * np.exp(-tt / decay) * (t >= at)
    return amp * env * sum(np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * f * 1.003 * t) for f in freqs)


TA, DUM = 0.03, 0.48
dry = (
    hit(TA, 120, 62, 0.03, 0.11, 0.5) + lowpassed_noise(TA, 0.012, 2500, 0.18, 1)
    # Sub for soundbars and cinema-style subs, body an octave up so TV speakers still feel it
    + hit(DUM, 78, 38, 0.09, 0.95, 0.95) + hit(DUM, 160, 82, 0.05, 0.38, 0.45)
    + lowpassed_noise(DUM, 0.16, 380, 0.5, 2)
    + swell(DUM, [110, 164.8, 220, 277.2, 329.6], 0.22, 1.4, 0.035)
)
dry = np.tanh(1.5 * dry) / np.tanh(1.5)


def comb(x, ms, feedback, damp):
    d = int(SR * ms / 1000)
    y = np.zeros_like(x)
    lp = 0.0
    for i in range(d, len(x)):
        lp = (1 - damp) * y[i - d] + damp * lp
        y[i] = x[i] + feedback * lp
    return y


def allpass(x, ms, g):
    d = int(SR * ms / 1000)
    y = np.zeros_like(x)
    for i in range(len(x)):
        xd = x[i - d] if i >= d else 0.0
        yd = y[i - d] if i >= d else 0.0
        y[i] = -g * x[i] + xd + g * yd
    return y


# A dark hall: Schroeder reverb with damped combs
wet = sum(comb(dry, ms, 0.84, 0.45) for ms in (29.7, 37.1, 41.1, 43.7)) / 4
wet = allpass(allpass(wet, 5.0, 0.7), 1.7, 0.7)
b, a = butter(2, 2200 / (SR / 2))
wet = lfilter(b, a, wet)

out = dry + 0.42 * wet
out *= np.clip((t[-1] - t) / 0.5, 0, 1)
out = 0.89 * out / np.max(np.abs(out))
wavfile.write(sys.argv[1] if len(sys.argv) > 1 else "intro.wav", SR, (out * 32767).astype(np.int16))
