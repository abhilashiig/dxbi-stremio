"""Matinee's start-up sound ("Projector"), synthesized from scratch (no samples).

An old film projector clatters up to 24 frames a second and winds down, then a vibraphone plays the hook on
the rhythm of the name, "Ma-ti-NEE" (D, A, then F# over D), landing on a clean round thump and a smooth chord.

    python3 intro_sound.py out.wav
    ffmpeg -y -i out.wav -c:a libvorbis -q:a 6 ../../tv/app/src/main/res/raw/intro.ogg

Timing, which ui/Intro.kt follows: projector 0-0.85 s, "Ma" 0.45 s, "ti" 0.63 s, "NEE" (the hit) 0.87 s.
"""
import sys

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, fftconvolve, sosfilt

SR = 48_000
t = np.arange(int(SR * 4.0)) / SR
noise = np.random.default_rng(7)
motor_noise = np.random.default_rng(11)

START = 0.35
MA, TI, NEE = START + 0.10, START + 0.28, START + 0.52


def note(name):
    semis = {"C": -9, "D": -7, "E": -5, "F": -4, "G": -2, "A": 0, "B": 2}[name[0]] + ("#" in name) + 12 * (int(name[-1]) - 4)
    return 440 * 2 ** (semis / 12)


def lowpass(x, hz, order=2):
    return sosfilt(butter(order, hz / (SR / 2), output="sos"), x)


def highpass(x, hz, order=2):
    return sosfilt(butter(order, hz / (SR / 2), btype="high", output="sos"), x)


def env(at, attack, decay):
    tt = np.clip(t - at, 0, None)
    return (1 - np.exp(-tt / attack)) * np.exp(-tt / decay) * (t >= at)


def click(at):
    tt = np.clip(t - at, 0, None)
    return highpass(lowpass(noise.standard_normal(len(t)), 3500), 800) * np.exp(-tt / 0.004) * (t >= at)


def projector():
    """Clatter speeding up to 24 frames a second, gone just before the last note."""
    spin = np.clip(t / START, 0, 1)
    phase = np.cumsum(8 + 16 * spin) / SR
    ticks = np.flatnonzero(np.diff(np.floor(phase)) > 0) / SR
    clicks = 0.35 * sum(click(c) for c in ticks if c < NEE - 0.03)
    motor = 0.03 * lowpass(motor_noise.standard_normal(len(t)), 900) * spin
    return (clicks + motor) * np.clip((NEE - 0.02 - t) / 0.15, 0, 1)


def vibraphone(at, name, amp, decay, tremolo=True):
    f = note(name)
    x = np.sin(2 * np.pi * f * t) * env(at, 0.002, decay) + 0.3 * np.sin(2 * np.pi * 4 * f * t) * env(at, 0.001, decay / 5)
    return amp * x * ((1 - 0.25 * (0.5 + 0.5 * np.sin(2 * np.pi * 5.2 * t))) if tremolo else 1)


def thump(at, freq, decay, amp):
    """A clean round hit: soft attack, a small pitch fall."""
    tt = np.clip(t - at, 0, None)
    pitch = freq + 0.25 * freq * np.exp(-tt / 0.04)
    return amp * np.sin(2 * np.pi * np.cumsum(pitch) / SR) * env(at, 0.008, decay)


def chord(at, names, attack, decay, amp):
    """Pure sines with a gentle chorus: smooth, nothing buzzy."""
    x = sum(np.sin(2 * np.pi * note(n) * d * t) for n in names for d in (0.998, 1.0, 1.002))
    return amp * env(at, attack, decay) * x / (3 * len(names))


def hall(x, seconds, seed):
    r = np.random.default_rng(seed)
    n = int(SR * seconds)
    tt = np.arange(n) / SR
    ir = r.standard_normal(n) * np.exp(-6.9 * tt / seconds)
    ir = 0.5 * lowpass(ir, 3200) + 0.5 * lowpass(ir, 800)
    ir = np.concatenate([np.zeros(int(SR * 0.018)), ir])
    return fftconvolve(x, ir / np.sqrt(np.sum(ir ** 2)))[: len(x)]


hook = (vibraphone(MA, "D5", 0.5, 0.6) + vibraphone(TI, "A5", 0.5, 0.6)
        + vibraphone(NEE, "F#5", 0.6, 2.0, tremolo=False) + vibraphone(NEE, "D5", 0.4, 2.0, tremolo=False))
low = thump(NEE, 52, 0.8, 0.85) + chord(NEE, ["D3", "A3", "F#4"], 0.06, 1.6, 0.35)
dry = projector() + hook + low
left, right = dry + 0.1 * hook, dry - 0.1 * hook

out = np.stack([left + 0.35 * hall(left, 2.4, 1), right + 0.35 * hall(right, 2.4, 2)], axis=1)
out = np.tanh(0.9 * out) / np.tanh(0.9)
out *= np.clip((len(t) - np.arange(len(t))) / (SR * 0.8), 0, 1)[:, None]
out = highpass(out.T, 25).T
out = 0.85 * out / np.max(np.abs(out))
wavfile.write(sys.argv[1] if len(sys.argv) > 1 else "intro.wav", SR, (out * 32767).astype(np.int16))
