#!/usr/bin/env python3
"""
FNAF: NIGHT SHIFT - síntesis de sonidos originales (OGG Vorbis, mono).

Todos los sonidos se generan por síntesis (osciladores, ruido filtrado,
modelos de impacto y formantes). No se usa ninguna grabación de terceros.
La caja de música interpreta la "Canción del Toreador" de Bizet (Carmen,
1875), obra de dominio público, con un timbre sintetizado propio.

Uso: python tools/gen_sounds.py
Requiere: numpy, soundfile
"""
import json
import os

import numpy as np
import soundfile as sf

SR = 44100
MOD = "fnaf_night_shift"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", MOD, "sounds")
rng = np.random.default_rng(1987)


def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def env_exp(dur, decay):
    return np.exp(-t_axis(dur) / decay)


def adsr(n, a=0.01, r=0.1):
    e = np.ones(n)
    na, nr = int(SR * a), int(SR * r)
    if na:
        e[:na] = np.linspace(0, 1, na)
    if nr:
        e[-nr:] *= np.linspace(1, 0, nr)
    return e


def lowpass(x, cutoff):
    # filtro de un polo, aplicado dos veces
    a = np.exp(-2 * np.pi * cutoff / SR)
    for _ in range(2):
        y = np.empty_like(x)
        acc = 0.0
        for i in range(len(x)):
            acc = (1 - a) * x[i] + a * acc
            y[i] = acc
        x = y
    return x


def lowpass_fast(x, cutoff):
    # versión vectorizada aproximada mediante convolución con ventana
    n = max(1, int(SR / cutoff / 2))
    k = np.hanning(n * 2 + 1)
    k /= k.sum()
    return np.convolve(x, k, mode="same")


def highpass_fast(x, cutoff):
    return x - lowpass_fast(x, cutoff)


def bandpass(x, lo, hi):
    return lowpass_fast(highpass_fast(x, lo), hi)


def noise(dur):
    return rng.uniform(-1, 1, int(SR * dur))


def saw(freq, dur):
    t = t_axis(dur)
    if np.isscalar(freq):
        phase = freq * t
    else:
        phase = np.cumsum(freq) / SR
    return 2 * (phase % 1.0) - 1


def sine(freq, dur, phase0=0.0):
    t = t_axis(dur)
    if np.isscalar(freq):
        return np.sin(2 * np.pi * freq * t + phase0)
    return np.sin(2 * np.pi * np.cumsum(freq) / SR + phase0)


def square(freq, dur, duty=0.5):
    if np.isscalar(freq):
        phase = freq * t_axis(dur)
    else:
        phase = np.cumsum(freq) / SR
    return np.where((phase % 1.0) < duty, 1.0, -1.0)


def metal_hit(dur, base, decay=0.4, partials=(1.0, 2.32, 3.87, 5.21, 7.13), bright=1.0):
    t = t_axis(dur)
    out = np.zeros_like(t)
    for i, p in enumerate(partials):
        f = base * p * (1 + rng.uniform(-0.01, 0.01))
        out += np.sin(2 * np.pi * f * t) * np.exp(-t / (decay / (1 + i * 0.6 / bright))) / (1 + i * 0.5)
    click = noise(dur) * np.exp(-t / 0.004)
    return out + click * 0.6


def reverb(x, amount=0.3, room=0.08):
    out = np.copy(x)
    for d, g in ((room, 0.5), (room * 1.37, 0.4), (room * 1.83, 0.3), (room * 2.41, 0.22)):
        n = int(SR * d)
        y = np.zeros(len(x) + n * 6)
        y[:len(x)] += x
        for k in range(1, 6):
            y[n * k:n * k + len(x)] += x * (g ** k)
        out = out + amount * y[:len(x)] * 0.5
    return out


def pad(x, before=0.0, after=0.0):
    return np.concatenate([np.zeros(int(SR * before)), x, np.zeros(int(SR * after))])


def mixdown(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def place(buf, x, at):
    i = int(SR * at)
    end = min(len(buf), i + len(x))
    buf[i:end] += x[:end - i]


def normalize(x, peak=0.9):
    m = np.max(np.abs(x)) or 1
    return x / m * peak


def fade(x, fin=0.01, fout=0.05):
    n_in, n_out = int(SR * fin), int(SR * fout)
    if n_in:
        x[:n_in] *= np.linspace(0, 1, n_in)
    if n_out:
        x[-n_out:] *= np.linspace(1, 0, n_out)
    return x


# Procesado final para sonidos con mucha energía grave (inaudibles en parlantes pequeños):
# ruta -> (cantidad de "excitador" armónico, RMS objetivo en dBFS)
PRESENCE = {
    "ambient/hum": (2.0, -18), "ambient/distant1": (0.8, -17), "ambient/distant2": (0.8, -17),
    "step/metal1": (1.0, -15), "step/metal2": (1.0, -15), "step/metal3": (1.0, -15),
    "repair/fail": (0.8, -12), "alert/danger": (1.0, -13), "flashlight/click": (0.0, -18),
    "flashlight/dead": (0.8, -17), "entity/foxy/run": (1.2, -13), "player/heartbeat": (2.5, -12),
    "door/bang": (1.0, -11), "ambient/fnaf_fallback": (2.0, -20), "door/slam": (0.6, -13),
    "repair/ratchet1": (0.0, -16), "repair/ratchet2": (0.0, -16), "camera/switch": (0.0, -17),
    "vent/fail": (0.4, -16), "vent/reset": (0.4, -16), "player/breath": (0.0, -16),
    "entity/chica/idle": (0.0, -16), "block/crate/open": (0.0, -16), "repair/spark1": (0.0, -17),
    "repair/spark2": (0.0, -17), "entity/animatronic/lurk": (0.5, -15),
}


def excite(x, amount):
    """Añade armónicos audibles (180 Hz - 4 kHz) generados por saturación de los graves."""
    if amount <= 0:
        return x
    sat = np.tanh(x * 6.0)
    harm = bandpass(sat, 180, 4000)
    # el sub-grave (< 90 Hz) no lo reproducen parlantes ni auriculares comunes: se atenúa
    return highpass_fast(x, 90) * 0.7 + x * 0.3 + amount * harm


def to_rms(x, target_db, ceiling=0.97):
    rms = np.sqrt(np.mean(x ** 2)) or 1e-9
    y = x * (10 ** (target_db / 20) / rms)
    # limitador suave para no recortar
    return np.tanh(y / ceiling) * ceiling


def save(path, x, peak=0.9):
    x = np.asarray(x, dtype=np.float64)
    if path in PRESENCE:
        amount, target = PRESENCE[path]
        x = to_rms(excite(normalize(x, 0.9), amount), target)
        x = fade(x)
    else:
        x = fade(normalize(x, peak))
    full = os.path.join(OUT, path + ".ogg")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    write_ogg(full, x)


def write_ogg(path, x):
    """Escribe OGG Vorbis por bloques (libsndfile se desborda con buffers largos)."""
    data = np.asarray(x, dtype=np.float32)
    with sf.SoundFile(path, "w", SR, 1, format="OGG", subtype="VORBIS") as f:
        for i in range(0, len(data), SR // 2):
            f.write(data[i:i + SR // 2])


# ----------------------------------------------------------------------------

def gen_hum():
    d = 8.0
    t = t_axis(d)
    x = 0.5 * np.sin(2 * np.pi * 50 * t) + 0.35 * np.sin(2 * np.pi * 100 * t + 0.4) + 0.2 * np.sin(2 * np.pi * 150 * t)
    x *= 1 + 0.25 * np.sin(2 * np.pi * 0.31 * t)
    rumble = lowpass_fast(noise(d), 120) * 3.0
    hiss = bandpass(noise(d), 2000, 6000) * 0.05
    x = x + rumble + hiss
    for at in rng.uniform(0.5, 7.5, 5):
        place(x, metal_hit(0.3, rng.uniform(800, 1600), 0.05) * 0.15, at)
    x = adsr(len(x), 1.5, 1.5) * x
    save("ambient/hum", x, 0.6)


def gen_distant():
    for i in range(2):
        x = metal_hit(2.5, 140 + i * 37, decay=0.9, partials=(1.0, 2.76, 5.40, 8.93, 13.3))
        x = lowpass_fast(x, 1800)
        x = reverb(x, 0.8, 0.11)
        save("ambient/distant%d" % (i + 1), x, 0.7)


def gen_steps():
    for i in range(3):
        d = 0.35
        t = t_axis(d)
        thump = np.sin(2 * np.pi * (70 + i * 6) * t) * np.exp(-t / 0.06)
        clank = metal_hit(d, 900 + i * 230, decay=0.06)
        servo = sine(400 + 120 * np.sin(np.linspace(0, 3, len(t))), d) * np.exp(-t / 0.1) * 0.15
        save("step/metal%d" % (i + 1), thump * 1.2 + clank * 0.5 + servo, 0.85)


def gen_servo():
    for i in range(2):
        d = 0.8
        n = int(SR * d)
        curve = np.concatenate([np.linspace(300, 520, n // 2), np.linspace(520, 380 + i * 60, n - n // 2)])
        x = 0.6 * square(curve, d, 0.3) + 0.4 * saw(curve * 2.01, d)
        x = bandpass(x, 250, 3000)
        x *= adsr(n, 0.05, 0.15) * (1 + 0.3 * np.sin(2 * np.pi * 35 * t_axis(d)))
        save("mech/servo%d" % (i + 1), x * 0.5 + noise(d) * 0.02, 0.6)


def gen_door():
    d = 1.0
    t = t_axis(d)
    impact = lowpass_fast(noise(d), 900) * np.exp(-t / 0.03) * 4
    thud = np.sin(2 * np.pi * 55 * t) * np.exp(-t / 0.15)
    ring = metal_hit(d, 210, decay=0.45) * 0.4
    save("door/slam", reverb(impact + thud + ring, 0.4, 0.05), 0.9)


def gen_ratchet():
    for v in range(2):
        d = 0.5
        x = np.zeros(int(SR * d))
        for k in range(6):
            place(x, metal_hit(0.06, 2400 + v * 300 + rng.uniform(-200, 200), 0.012), 0.02 + k * 0.075)
        save("repair/ratchet%d" % (v + 1), x, 0.7)


def gen_spark():
    for v in range(2):
        d = 0.6
        x = np.zeros(int(SR * d))
        for _ in range(40):
            at = rng.uniform(0, d - 0.03)
            burst = highpass_fast(noise(0.02), 2500) * np.exp(-t_axis(0.02) / 0.003)
            place(x, burst * rng.uniform(0.3, 1.0), at)
        hum = sine(120, d) * 0.1 * np.exp(-t_axis(d) / 0.3)
        save("repair/spark%d" % (v + 1), x + hum, 0.7)


def gen_success():
    d = 0.6
    x = np.zeros(int(SR * d))
    for k, f in enumerate((660, 880, 1320)):
        tone = (square(f, 0.13, 0.25) * 0.3 + sine(f, 0.13)) * adsr(int(SR * 0.13), 0.005, 0.04)
        place(x, tone, k * 0.14)
    save("repair/success", lowpass_fast(x, 5000), 0.7)


def gen_fail():
    d = 1.0
    t = t_axis(d)
    x = square(110, d) * 0.5 + square(116.5, d) * 0.5
    x *= (np.sin(2 * np.pi * 4 * t) > 0) * 0.8 + 0.2
    x = lowpass_fast(x, 2500)
    save("repair/fail", x * adsr(len(x), 0.01, 0.1), 0.85)


def gen_danger():
    d = 1.4
    t = t_axis(d)
    cluster = sum(saw(f * (1 + 0.04 * t), d) for f in (220, 233, 311, 330, 466)) / 5
    swell = np.minimum(1, t / 0.5) * np.exp(-np.maximum(0, t - 0.5) / 0.4)
    hit = lowpass_fast(noise(d), 400) * np.exp(-np.maximum(0, t - 0.5) / 0.05) * (t > 0.5) * 3
    boom = np.sin(2 * np.pi * 45 * t) * np.exp(-np.maximum(0, t - 0.5) / 0.3) * (t > 0.5)
    save("alert/danger", lowpass_fast(cluster * swell, 4000) + hit + boom, 0.85)


def bell(f, d, decay=1.6):
    t = t_axis(d)
    x = np.zeros_like(t)
    for p, a in ((0.5, 0.4), (1.0, 1.0), (1.19, 0.5), (1.56, 0.4), (2.0, 0.6), (2.74, 0.3), (3.76, 0.15)):
        x += a * np.sin(2 * np.pi * f * p * t) * np.exp(-t / (decay / (p ** 0.5)))
    return x


def gen_chimes():
    save("alert/night_start", reverb(bell(392, 3.0), 0.4, 0.09), 0.75)
    d = 5.0
    x = np.zeros(int(SR * d))
    for k in range(3):
        place(x, bell(523.25, 2.5) * 0.8, k * 0.9)
    arp = (523.25, 659.25, 783.99, 1046.5)
    for k, f in enumerate(arp):
        place(x, music_box_note(f, 1.2) * 0.6, 2.8 + k * 0.18)
    save("alert/six_am", reverb(x, 0.4, 0.1), 0.8)


def gen_power_out():
    d = 1.8
    n = int(SR * d)
    f = np.geomspace(420, 35, n)
    x = 0.6 * saw(f, d) + 0.4 * sine(f * 0.5, d)
    x = lowpass_fast(x, 1500) * np.linspace(1, 0.2, n)
    clunk = np.zeros(n)
    place(clunk, lowpass_fast(noise(0.2), 300) * np.exp(-t_axis(0.2) / 0.04) * 3, 1.4)
    save("alert/power_out", x + clunk, 0.85)


def gen_flashlight():
    d = 0.08
    t = t_axis(d)
    x = highpass_fast(noise(d), 1500) * np.exp(-t / 0.006) + np.sin(2 * np.pi * 3200 * t) * np.exp(-t / 0.01) * 0.3
    save("flashlight/click", x, 0.6)
    d = 0.6
    t = t_axis(d)
    buzz = square(90 * np.linspace(1, 0.6, len(t)), d) * np.exp(-t / 0.2) * 0.4
    y = np.zeros(len(t))
    place(y, x, 0.0)
    save("flashlight/dead", y + lowpass_fast(buzz, 1200), 0.6)


def fm_groan(base, d, idx=3.0, rate=1.5):
    t = t_axis(d)
    mod = np.sin(2 * np.pi * base * 1.5 * t) * idx * (1 + 0.5 * np.sin(2 * np.pi * rate * t))
    return np.sin(2 * np.pi * base * t + mod)


def gen_entities():
    # Bonnie: gruñido electrónico grave con distorsión
    d = 1.4
    x = fm_groan(95, d, 4.0) + 0.3 * fm_groan(190, d, 2.0)
    x = np.tanh(x * 2.0) * adsr(int(SR * d), 0.1, 0.3)
    save("entity/bonnie/idle", lowpass_fast(x, 2200) + noise(d) * 0.02, 0.65)
    # Chica: castañeteo del pico
    d = 1.0
    x = np.zeros(int(SR * d))
    for k in range(9):
        place(x, mixdown(metal_hit(0.08, 700 + rng.uniform(-80, 80), 0.02), lowpass_fast(noise(0.05), 1500) * 0.5), 0.05 + k * 0.1)
    place(x, sine(np.linspace(300, 450, int(SR * 0.6)), 0.6) * 0.15, 0.2)
    save("entity/chica/idle", x, 0.7)
    # Foxy: carrera metálica rápida
    d = 1.2
    x = np.zeros(int(SR * d))
    for k in range(10):
        tt = t_axis(0.15)
        step = np.sin(2 * np.pi * 75 * tt) * np.exp(-tt / 0.04) + metal_hit(0.15, 1300 + rng.uniform(-200, 200), 0.03) * 0.4
        place(x, step, k * 0.11)
    save("entity/foxy/run", x, 0.8)
    # Freddy: risa grave ("jo jo jo") con formantes
    d = 2.0
    x = np.zeros(int(SR * d))
    for k, f0 in enumerate((115, 105, 95)):
        n = 0.38
        tt = t_axis(n)
        src = saw(f0 * (1 + 0.03 * np.sin(2 * np.pi * 6 * tt)), n)
        vowel = bandpass(src, 350, 650) * 1.4 + bandpass(src, 800, 1100) * 0.6
        vowel *= np.sin(np.pi * tt / n) ** 0.7
        place(x, vowel, 0.1 + k * 0.5)
    x = np.tanh(x * 3) * 0.7
    save("entity/freddy/laugh", reverb(x, 0.35, 0.06), 0.75)
    # Golden Freddy: susurro distorsionado
    d = 2.2
    t = t_axis(d)
    w = bandpass(noise(d), 600, 3500) * (0.5 + 0.5 * np.sin(2 * np.pi * 2.3 * t) ** 2)
    w += bandpass(noise(d), 200, 500) * 0.5 * np.sin(np.pi * t / d)
    w = w[::-1] * np.minimum(1, t / 0.4)
    save("entity/golden_freddy/whisper", reverb(w, 0.6, 0.12), 0.6)
    # golpe metálico (daño)
    save("entity/animatronic/hurt", metal_hit(0.6, 320, 0.2) + lowpass_fast(noise(0.6), 500) * np.exp(-t_axis(0.6) / 0.02) * 2, 0.85)
    # susto: chillido con barridos desafinados y distorsión
    d = 1.5
    t = t_axis(d)
    sweep = 380 + 900 * (1 - np.exp(-t / 0.15))
    scream = sum(saw(sweep * r, d) for r in (1.0, 1.013, 0.987, 1.5, 2.02)) / 5
    scream += bandpass(noise(d), 1500, 7000) * 0.8
    scream = np.tanh(scream * 4) * adsr(len(t), 0.005, 0.4)
    save("entity/animatronic/jumpscare", scream, 0.95)


def music_box_note(f, d):
    t = t_axis(d)
    x = np.sin(2 * np.pi * f * t) * np.exp(-t / 0.9)
    x += 0.35 * np.sin(2 * np.pi * f * 2.0 * t) * np.exp(-t / 0.35)
    x += 0.15 * np.sin(2 * np.pi * f * 3.98 * t) * np.exp(-t / 0.12)
    x += np.sin(2 * np.pi * f * 6.1 * t) * np.exp(-t / 0.02) * 0.2
    return x


def gen_music_box():
    N = {"C4": 261.63, "D4": 293.66, "E4": 329.63, "F4": 349.23, "G4": 392.0, "A4": 440.0, "Bb4": 466.16,
         "C5": 523.25, "D5": 587.33, "E5": 659.25, "F5": 698.46}
    # Estribillo de la "Canción del Toreador" (Bizet, dominio público), simplificado
    melody = [("C5", 1), ("D5", .5), ("C5", .5), ("A4", 1.5), ("A4", .5), ("A4", .5), ("G4", .5), ("A4", .5), ("Bb4", .5), ("A4", 2),
              ("Bb4", 1), ("G4", .5), ("C5", .5), ("A4", 1.5), ("F4", .5), ("D4", .5), ("G4", .5), ("C4", 2),
              ("C5", 1), ("D5", .5), ("C5", .5), ("A4", 1.5), ("A4", .5), ("A4", .5), ("G4", .5), ("A4", .5), ("Bb4", .5), ("A4", 2)]
    beat = 0.36
    total = sum(b for _, b in melody) * beat + 2.0
    x = np.zeros(int(SR * total))
    tpos = 0.0
    for i, (n, b) in enumerate(melody):
        # la cuerda se va acabando: ligero retraso al final
        slow = 1.0 + max(0, i - len(melody) + 6) * 0.05
        place(x, music_box_note(N[n], 1.6), tpos)
        tpos += b * beat * slow
    save("music/box", reverb(x, 0.35, 0.07), 0.6)


def gen_crate():
    d = 0.7
    t = t_axis(d)
    creak = saw(np.linspace(180, 140, len(t)) * (1 + 0.1 * np.sin(2 * np.pi * 23 * t)), d)
    creak = bandpass(creak, 300, 2500) * np.exp(-t / 0.3) * 0.4
    rummage = np.zeros(len(t))
    for _ in range(8):
        place(rummage, bandpass(noise(0.05), 800, 4000) * np.exp(-t_axis(0.05) / 0.01), rng.uniform(0.15, 0.6))
    save("block/crate/open", creak + rummage, 0.7)


# ----------------------------------------------------------------------------
# Actualización 1.1
# ----------------------------------------------------------------------------

def gen_v11():
    # cambio de cámara: clic + ráfaga breve de estática
    d = 0.35
    t = t_axis(d)
    x = highpass_fast(noise(d), 1200) * np.exp(-t / 0.08) * 0.6 + np.sin(2 * np.pi * 1800 * t) * np.exp(-t / 0.02) * 0.5
    save("camera/switch", x, 0.6)
    # estática de cámara
    d = 0.9
    x = bandpass(noise(d), 300, 6000) * (0.6 + 0.4 * (rng.random(int(SR * d)) > 0.7))
    save("camera/static", x * adsr(len(x), 0.02, 0.2), 0.5)
    # aviso de ventilación: doble pitido
    d = 0.8
    x = np.zeros(int(SR * d))
    for k in range(2):
        place(x, (square(880, 0.15, 0.5) * 0.4 + sine(880, 0.15)) * adsr(int(SR * 0.15), 0.005, 0.03), k * 0.3)
    save("vent/warning", lowpass_fast(x, 4000), 0.7)
    # ventilación averiada: ventilador que se ahoga + traqueteo
    d = 2.5
    t = t_axis(d)
    rate = 18 * np.linspace(1, 0.4, len(t))
    fan = bandpass(noise(d), 80, 900) * (0.6 + 0.4 * np.sin(2 * np.pi * np.cumsum(rate) / SR))
    rattle = np.zeros(len(t))
    for at in np.arange(0.05, d - 0.1, 0.13):
        place(rattle, metal_hit(0.06, 900 + rng.uniform(-100, 100), 0.01) * 0.3, at)
    save("vent/fail", fan + rattle, 0.6)
    # reinicio: subida de motor + clic
    d = 1.4
    t = t_axis(d)
    f = np.linspace(60, 240, len(t))
    x = (0.6 * saw(f, d) + 0.4 * sine(f, d)) * np.minimum(1, t / 0.6)
    x = lowpass_fast(x, 1200) + bandpass(noise(d), 200, 2000) * 0.2 * np.minimum(1, t / 0.6)
    place(x, metal_hit(0.1, 1500, 0.02), 0.0)
    save("vent/reset", x, 0.7)
    # latido
    d = 0.6
    x = np.zeros(int(SR * d))
    for at, a in ((0.0, 1.0), (0.18, 0.7)):
        tt = t_axis(0.15)
        thump = np.sin(2 * np.pi * 55 * tt) * np.exp(-tt / 0.04) + 0.6 * np.sin(2 * np.pi * 140 * tt) * np.exp(-tt / 0.03)
        thump += bandpass(noise(0.15), 150, 900) * np.exp(-tt / 0.012) * 0.8
        place(x, thump * a, at)
    save("player/heartbeat", lowpass_fast(x, 1200), 0.9)
    # respiración agitada
    d = 2.0
    t = t_axis(d)
    env = np.abs(np.sin(np.pi * t / 0.5)) ** 2
    save("player/breath", bandpass(noise(d), 400, 2500) * env, 0.5)
    # golpes en la puerta metálica
    d = 0.9
    t = t_axis(d)
    x = lowpass_fast(noise(d), 600) * np.exp(-t / 0.03) * 4 + metal_hit(d, 140, 0.35) * 0.6 + np.sin(2 * np.pi * 50 * t) * np.exp(-t / 0.12)
    save("door/bang", reverb(x, 0.4, 0.04), 0.95)
    # respiración robótica tras la puerta
    d = 2.4
    t = t_axis(d)
    breath = bandpass(noise(d), 200, 1200) * np.abs(np.sin(np.pi * t / 1.2)) ** 3
    servo = sine(260 + 40 * np.sin(2 * np.pi * 0.7 * t), d) * 0.08
    save("entity/animatronic/lurk", breath + servo, 0.55)
    # alarma de avería
    d = 1.6
    t = t_axis(d)
    f = 600 + 300 * (np.sin(2 * np.pi * 2.5 * t) > 0)
    save("alert/malfunction", lowpass_fast(square(f, d, 0.4) * 0.5, 3000) * adsr(len(t), 0.01, 0.2), 0.6)
    # interferencia breve
    d = 0.5
    save("ambient/static", bandpass(noise(d), 500, 7000) * adsr(int(SR * d), 0.01, 0.15), 0.5)
    # ambiente de respaldo (si no se usa el paquete de sonidos de FNAF): dron oscuro de 40 s
    d = 40.0
    t = t_axis(d)
    x = 0.4 * np.sin(2 * np.pi * 41 * t) + 0.25 * np.sin(2 * np.pi * 61.7 * t + 1) + 0.15 * np.sin(2 * np.pi * 82.4 * t)
    x *= 0.7 + 0.3 * np.sin(2 * np.pi * 0.05 * t)
    x += lowpass_fast(noise(d), 150) * 2.5
    for at in rng.uniform(2, d - 3, 6):
        place(x, metal_hit(1.5, rng.uniform(90, 200), 0.7) * 0.2, at)
    save("ambient/fnaf_fallback", adsr(len(x), 3, 4) * x, 0.5)
    # sustos de respaldo para Foxy y Golden Freddy
    for name, base in (("foxy", 520), ("golden_freddy", 300)):
        d = 1.5
        t = t_axis(d)
        sweep = base + 1100 * (1 - np.exp(-t / 0.12))
        x = sum(saw(sweep * r, d) for r in (1.0, 1.02, 0.98, 1.49)) / 4 + bandpass(noise(d), 1500, 7000) * 0.9
        save("entity/%s/jumpscare_fallback" % name, np.tanh(x * 4) * adsr(len(t), 0.005, 0.4), 0.95)
    # casillero metálico
    d = 0.7
    t = t_axis(d)
    creak = bandpass(saw(np.linspace(220, 170, len(t)), d), 400, 3000) * np.exp(-t / 0.25) * 0.3
    save("block/locker/open", creak + metal_hit(d, 420, 0.25) * 0.6, 0.7)


def write_sounds_json():
    S = {}

    def add(event, files, subtitle=None):
        S[event] = {"subtitle": "subtitles.%s.%s" % (MOD, event), "sounds": ["%s:%s" % (MOD, f) for f in files]}

    add("ambient.hum", ["ambient/hum"])
    add("ambient.distant", ["ambient/distant1", "ambient/distant2"])
    add("step.metal", ["step/metal1", "step/metal2", "step/metal3"])
    add("mech.servo", ["mech/servo1", "mech/servo2"])
    add("door.slam", ["door/slam"])
    add("repair.ratchet", ["repair/ratchet1", "repair/ratchet2"])
    add("repair.spark", ["repair/spark1", "repair/spark2"])
    add("repair.success", ["repair/success"])
    add("repair.fail", ["repair/fail"])
    add("alert.danger", ["alert/danger"])
    add("alert.night_start", ["alert/night_start"])
    add("alert.six_am", ["alert/six_am"])
    add("alert.power_out", ["alert/power_out"])
    add("flashlight.click", ["flashlight/click"])
    add("flashlight.dead", ["flashlight/dead"])
    add("entity.bonnie.idle", ["entity/bonnie/idle"])
    add("entity.chica.idle", ["entity/chica/idle"])
    add("entity.foxy.run", ["entity/foxy/run"])
    add("entity.freddy.laugh", ["entity/freddy/laugh"])
    add("entity.golden_freddy.whisper", ["entity/golden_freddy/whisper"])
    add("entity.animatronic.hurt", ["entity/animatronic/hurt"])
    add("entity.animatronic.jumpscare", ["entity/animatronic/jumpscare"])
    add("music.box", ["music/box"])
    add("block.crate.open", ["block/crate/open"])
    add("camera.switch", ["camera/switch"])
    add("camera.static", ["camera/static"])
    add("vent.warning", ["vent/warning"])
    add("vent.fail", ["vent/fail"])
    add("vent.reset", ["vent/reset"])
    add("player.heartbeat", ["player/heartbeat"])
    add("player.breath", ["player/breath"])
    add("door.bang", ["door/bang"])
    add("entity.animatronic.lurk", ["entity/animatronic/lurk"])
    add("alert.malfunction", ["alert/malfunction"])
    add("ambient.static", ["ambient/static"])
    add("ambient.fnaf", ["ambient/fnaf_fallback"])
    add("entity.foxy.jumpscare", ["entity/foxy/jumpscare_fallback"])
    add("entity.golden_freddy.jumpscare", ["entity/golden_freddy/jumpscare_fallback"])
    add("block.locker.open", ["block/locker/open"])
    path = os.path.join(OUT, "..", "sounds.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(S, f, indent=2)


def main():
    gen_hum()
    gen_distant()
    gen_steps()
    gen_servo()
    gen_door()
    gen_ratchet()
    gen_spark()
    gen_success()
    gen_fail()
    gen_danger()
    gen_chimes()
    gen_power_out()
    gen_flashlight()
    gen_entities()
    gen_music_box()
    gen_crate()
    gen_v11()
    write_sounds_json()
    print("sonidos generados")


if __name__ == "__main__":
    main()
