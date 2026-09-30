"""Generates one narration clip per line of voiceover/lines.json with Kokoro TTS (Apache 2.0, runs locally).
Needs kokoro-onnx, soundfile, and the model files kokoro-v1.0.onnx + voices-v1.0.bin in the working directory.
Each clip speeds up slightly (to 1.18x at most) if it would run into the next line."""
import json, sys, soundfile as sf, numpy as np
from kokoro_onnx import Kokoro
from kokoro_onnx.config import EspeakConfig
V=sys.argv[1] if len(sys.argv)>1 else "af_heart"
k=Kokoro("kokoro-v1.0.onnx","voices-v1.0.bin",espeak_config=EspeakConfig(lib_path="/usr/lib/libespeak-ng.so.1",data_path="/usr/share/espeak-ng-data"))
lines=json.load(open("/home/arpit/Desktop/hackathon_projects/CueBack/video/voiceover/lines.json"))
out="/tmp/claude-1000/-home-arpit-Desktop-hackathon-projects-CueBack/ae39aa24-f521-45ef-adc0-4135d873bea8/scratchpad/vo"
import os; os.makedirs(out,exist_ok=True)
starts=[l[0] for l in lines]+[234.0]
for i,(t,text) in enumerate(lines):
    window=starts[i+1]-t-0.25
    speed=1.0
    while True:
        a,sr=k.create(text,voice=V,speed=speed,lang="en-us")
        # trim leading/trailing silence
        nz=np.where(np.abs(a)>0.01)[0]
        a=a[max(nz[0]-int(0.03*sr),0):nz[-1]+int(0.12*sr)]
        d=len(a)/sr
        if d<=window or speed>=1.18: break
        speed=round(speed+0.04,2)
    sf.write(f"{out}/{i:02d}.wav",a,sr)
    flag="" if d<=window else "  OVER by %.1fs"%(d-window)
    print(f"{i:02d} start {t:6.1f}  {d:5.2f}s / {window:5.2f}s  speed {speed}{flag}")
