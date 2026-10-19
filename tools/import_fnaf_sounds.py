#!/usr/bin/env python3
"""
Importa sonidos de FNAF proporcionados para el proyecto y los empaqueta en
el paquete de recursos integrado del mod:

    src/main/resources/resourcepacks/fnaf_sounds/

El paquete se activa por defecto y sustituye a los sonidos sintetizados de
los mismos eventos; se puede desactivar en Opciones > Paquetes de recursos.
Los sonidos sintetizados del mod quedan siempre como respaldo.

AVISO DE LICENCIA: estos audios pertenecen a ScottGames (ver los CREDITS.md
de los repositorios de origen). Úsalos sólo en tu copia personal. Para
publicar el mod, compilá sin ellos:  gradlew build -PnoFnafSounds

Uso:
    python tools/import_fnaf_sounds.py <carpeta PyNightsAtFredbears-main> <carpeta fnaf-jumpscare-extension-main>
"""
import glob
import json
import os
import sys

import numpy as np
import soundfile as sf

MOD = "fnaf_night_shift"
PACK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "resourcepacks", "fnaf_sounds")
OUT = os.path.join(PACK, "assets", MOD, "sounds", "fnaf")


def find(root, pattern):
    hits = glob.glob(os.path.join(root, "**", pattern), recursive=True)
    if not hits:
        raise FileNotFoundError("No se encontró %s en %s" % (pattern, root))
    return hits[0]


def load_mono(path, start=0.0, duration=None):
    data, sr = sf.read(path, dtype="float32", always_2d=True)
    data = data.mean(axis=1)
    a = int(start * sr)
    b = len(data) if duration is None else min(len(data), a + int(duration * sr))
    return data[a:b], sr


def fade(x, sr, fin, fout):
    x = x.copy()
    ni, no = int(sr * fin), int(sr * fout)
    if ni:
        x[:ni] *= np.linspace(0, 1, ni)
    if no:
        x[-no:] *= np.linspace(1, 0, no)
    return x


def write(name, x, sr, peak=None):
    os.makedirs(OUT, exist_ok=True)
    if peak:
        m = float(np.max(np.abs(x))) or 1.0
        x = x / m * peak
    path = os.path.join(OUT, name + ".ogg")
    with sf.SoundFile(path, "w", sr, 1, format="OGG", subtype="VORBIS") as f:
        for i in range(0, len(x), sr // 2):
            f.write(x[i:i + sr // 2].astype(np.float32))
    print("  ", name, "%.1fs" % (len(x) / sr))


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    pynights, jumpscares = sys.argv[1], sys.argv[2]

    # Ambiente: un fragmento de 90 s del ambiente original (se reproduce cada 2-4 minutos, no en bucle)
    x, sr = load_mono(find(pynights, "ambience.mp3"), start=5.0, duration=90.0)
    # el ambiente original es casi todo grave: se añaden armónicos suaves para que se oiga en parlantes pequeños
    sat = np.tanh(x * 6.0)
    k = np.hanning(2 * int(sr / 180) + 1); k /= k.sum()
    hp = sat - np.convolve(sat, k, mode="same")
    k2 = np.hanning(2 * int(sr / 8000) + 1); k2 /= k2.sum()
    x = x + 0.5 * np.convolve(hp, k2, mode="same")
    write("ambience", fade(x, sr, 3.0, 5.0), sr, peak=0.85)
    x, sr = load_mono(find(pynights, "cam_sound.mp3"))
    write("camera_switch", fade(x, sr, 0.0, 0.05), sr, peak=0.8)
    x, sr = load_mono(find(jumpscares, "xscream.ogg"))
    write("jumpscare", fade(x, sr, 0.0, 0.2), sr, peak=0.95)
    x, sr = load_mono(find(jumpscares, "xscream3.ogg"))
    write("jumpscare_foxy", fade(x, sr, 0.0, 0.2), sr, peak=0.95)
    x, sr = load_mono(find(jumpscares, "fredbear_scream.oga"))
    write("jumpscare_golden", fade(x, sr, 0.0, 0.3), sr, peak=0.95)

    def ev(files):
        return {"replace": True, "sounds": ["%s:fnaf/%s" % (MOD, f) for f in files]}

    sounds = {
        "ambient.fnaf": dict(ev(["ambience"]), subtitle="subtitles.%s.ambient.fnaf" % MOD),
        "camera.switch": dict(ev(["camera_switch"]), subtitle="subtitles.%s.camera.switch" % MOD),
        "entity.animatronic.jumpscare": dict(ev(["jumpscare"]), subtitle="subtitles.%s.entity.animatronic.jumpscare" % MOD),
        "entity.foxy.jumpscare": dict(ev(["jumpscare_foxy"]), subtitle="subtitles.%s.entity.foxy.jumpscare" % MOD),
        "entity.golden_freddy.jumpscare": dict(ev(["jumpscare_golden"]), subtitle="subtitles.%s.entity.golden_freddy.jumpscare" % MOD),
    }
    with open(os.path.join(PACK, "assets", MOD, "sounds.json"), "w", encoding="utf-8") as f:
        json.dump(sounds, f, indent=2)
    with open(os.path.join(PACK, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {"pack_format": 15, "description": "Sonidos de Five Nights at Freddy's (ScottGames) para FNAF: Night Shift"}},
                  f, indent=2, ensure_ascii=False)
    with open(os.path.join(PACK, "LEEME.txt"), "w", encoding="utf-8") as f:
        f.write("Sonidos propiedad de ScottGames, proporcionados para uso personal en este proyecto.\n"
                "Origen: repositorios PyNightsAtFredbears y fnaf-jumpscare-extension (ver sus CREDITS/README).\n"
                "No redistribuir. Para compilar sin ellos: gradlew build -PnoFnafSounds\n")
    print("paquete de sonidos de FNAF listo en", os.path.normpath(PACK))


if __name__ == "__main__":
    main()
