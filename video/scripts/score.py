"""Writes public/audio/score.wav: the original ambient soundtrack for the demo (265 s, 44.1 kHz stereo)."""
import wave

import numpy as np

SR = 44100
DUR = 265.0
N = int(SR * DUR)
PROG = [[57, 60, 64, 69], [53, 57, 60, 65], [48, 55, 60, 64], [55, 59, 62, 67]]  # Am F C G
BAR = 8.0
BPM = 92
SWELLS = (10.0, 47.4, 96.9)  # title, the return, the purchase


def note(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def env(n, a, r):
    e = np.ones(n)
    a, r = min(int(a * SR), n // 2), min(int(r * SR), n // 2)
    if a:
        e[:a] = np.linspace(0, 1, a)
    if r:
        e[-r:] *= np.linspace(1, 0, r)
    return e


out = np.zeros(N)
for k in range(int(DUR / BAR) + 1):
    s = int(k * BAR * SR)
    n = min(int((BAR + 2.0) * SR), N - s)
    if n <= SR:
        break
    tt = np.arange(n) / SR
    ch = PROG[k % 4]
    pad = np.zeros(n)
    for m in ch[:1] if k == 0 else ch:  # the opening bar is just a drone
        for det in (-0.08, 0.0, 0.07):
            ff = note(m) * 2 ** (det / 12)
            pad += sum(np.sin(2 * np.pi * ff * h * tt + h) / h ** 1.6 for h in range(1, 6))
    out[s:s + n] += pad * env(n, 2.5, 2.5) * 0.018 + np.sin(2 * np.pi * note(ch[0] - 12) * tt) * env(n, 1.2, 2.0) * 0.05

beat = 60 / BPM
for i in range(int(DUR / beat)):
    ts = i * beat
    if ts < 15 or ts > DUR - 10:
        continue
    s, n = int(ts * SR), int(0.35 * SR)
    tt = np.arange(n) / SR
    f = 55 * np.exp(-tt * 18) + 45
    out[s:s + n] += np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 9) * 0.09

for i in range(int(DUR / (beat / 2))):
    ts = i * beat / 2
    if ts < 15 or ts > DUR - 12:
        continue
    m = PROG[int(ts // BAR) % 4][i % 4] + 12
    s, n = int(ts * SR), int(0.9 * SR)
    tt = np.arange(n) / SR
    out[s:s + n] += (np.sin(2 * np.pi * note(m) * tt) + 0.3 * np.sin(2 * np.pi * note(m) * 2 * tt)) * np.exp(-tt * 5.5) * 0.022

rng = np.random.default_rng(1)
for at in SWELLS:
    s, n = int((at - 3) * SR), int(3 * SR)
    tt = np.arange(n) / SR
    out[s:s + n] += np.convolve(rng.standard_normal(n), np.ones(30) / 30, mode="same") * (tt / 3) ** 2 * 0.05

left, right = out.copy(), out.copy()
d1, d2 = int(0.031 * SR), int(0.047 * SR)
left[d1:] += 0.35 * out[:-d1]
right[d2:] += 0.35 * out[:-d2]
fade = np.ones(N)
fade[: 2 * SR] = np.linspace(0, 1, 2 * SR)
fade[-6 * SR:] = np.linspace(1, 0, 6 * SR)
left, right = left * fade, right * fade
g = 0.707 / max(np.abs(left).max(), np.abs(right).max())
pcm = (np.stack([left * g, right * g], axis=1) * 32767).astype(np.int16)
with wave.open("public/audio/score.wav", "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(pcm.tobytes())
