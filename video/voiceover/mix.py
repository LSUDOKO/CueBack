"""Mixes the narration (voiceover/lines.json timings) over the score, ducking the music under the voice.

Writes public/audio/mix.wav. Voice clips are expected as <VO>/NN_44.wav (44.1 kHz mono), one per line.
Usage (from video/): python3 voiceover/mix.py <folder with the voice clips>
"""
import json
import sys
import wave

import numpy as np

VO = sys.argv[1]
SR = 44100
TOTAL = 234.1
N = int(TOTAL * SR)


def readwav(p):
    w = wave.open(p)
    a = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
    ch = w.getnchannels()
    return a.reshape(-1, ch) if ch > 1 else a[:, None]


music = readwav("public/audio/score.wav")
music = music[:N] if len(music) >= N else np.pad(music, ((0, N - len(music)), (0, 0)))
voice = np.zeros(N, dtype=np.float32)
active = np.zeros(N, dtype=np.float32)
for i, (t, _) in enumerate(json.load(open("voiceover/lines.json"))):
    v = readwav(f"{VO}/{i:02d}_44.wav")[:, 0]
    v = v * (10 ** (-19 / 20) / (np.sqrt(np.mean(v ** 2)) + 1e-9))  # same loudness for every line
    s = int(t * SR)
    e = min(s + len(v), N)
    voice[s:e] += v[: e - s]
    active[s:e] = 1

# Moving average via a cumulative sum: the music dips ~0.35 s before speech and recovers ~0.6 s after.
width = int(0.95 * SR)
c = np.concatenate([[0.0], np.cumsum(active, dtype=np.float64)])
idx = np.arange(N)
lo = np.clip(idx - int(0.6 * SR), 0, N)
hi = np.clip(idx + int(0.35 * SR), 0, N)
duck = np.clip((c[hi] - c[lo]) / width * 3, 0, 1).astype(np.float32)
gain = 0.62 - 0.44 * duck  # music at 0.62 alone, 0.18 under the voice
mix = music * gain[:, None] + voice[:, None] * 0.95
peak = np.abs(mix).max()
if peak > 0.89:
    mix *= 0.89 / peak
pcm = (np.clip(mix, -1, 1) * 32767).astype(np.int16)
out = wave.open("public/audio/mix.wav", "wb")
out.setnchannels(2)
out.setsampwidth(2)
out.setframerate(SR)
out.writeframes(pcm.tobytes())
out.close()
print("mix written")
