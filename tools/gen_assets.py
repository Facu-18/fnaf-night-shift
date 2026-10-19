#!/usr/bin/env python3
"""
FNAF: NIGHT SHIFT - generador de recursos de bloques y objetos.

Crea (todo original, pintado proceduralmente):
  * texturas 64/128 de bloques y objetos, e icono del mod 128x128
  * modelos de bloque/objeto y blockstates
  * traducciones (es_es, es_ar, es_mx, en_us -> todas en español)

Uso: python tools/gen_assets.py
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

MOD = "fnaf_night_shift"
BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(BASE, "assets", MOD)
TB = os.path.join(ASSETS, "textures", "block")
TI = os.path.join(ASSETS, "textures", "item")
MB = os.path.join(ASSETS, "models", "block")
MI = os.path.join(ASSETS, "models", "item")
BS = os.path.join(ASSETS, "blockstates")

R = random.Random(20261017)


def clamp(v):
    return max(0, min(255, int(v)))


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(c, f):
    return tuple(clamp(x * f) for x in c[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


class Tex:
    def __init__(self, w=16, h=16, bg=None):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0) if bg is None else (*bg, 255))
        self.p = self.img.load()
        self.w, self.h = w, h

    def put(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[x, y] = (*c[:3], a)

    def get(self, x, y):
        return self.p[x, y][:3]

    def noise(self, base, amt=10, x0=0, y0=0, x1=None, y1=None):
        x1 = self.w if x1 is None else x1
        y1 = self.h if y1 is None else y1
        for x in range(x0, x1):
            for y in range(y0, y1):
                n = R.uniform(-amt, amt)
                self.put(x, y, tuple(clamp(c + n) for c in base))
        return self

    def rect(self, x0, y0, x1, y1, c, a=255):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                self.put(x, y, c, a)

    def frame(self, x0, y0, x1, y1, c):
        for x in range(x0, x1 + 1):
            self.put(x, y0, c)
            self.put(x, y1, c)
        for y in range(y0, y1 + 1):
            self.put(x0, y, c)
            self.put(x1, y, c)

    def darken_edges(self, f=0.75):
        for x in range(self.w):
            for y in range(self.h):
                if x in (0, self.w - 1) or y in (0, self.h - 1):
                    if self.p[x, y][3]:
                        self.put(x, y, shade(self.get(x, y), f))

    def spots(self, color, n, r=1.5, t=0.5):
        for _ in range(n):
            cx, cy = R.uniform(0, self.w), R.uniform(0, self.h)
            rr = R.uniform(0.6, r)
            for x in range(self.w):
                for y in range(self.h):
                    d = math.hypot(x - cx, y - cy)
                    if d < rr and self.p[x, y][3]:
                        self.put(x, y, mix(self.get(x, y), color, t * (1 - d / (rr + 0.5))))

    def crack(self, color, length=10):
        x, y = R.randrange(self.w), R.randrange(self.h)
        for _ in range(length):
            self.put(x, y, color)
            x += R.choice((-1, 0, 1))
            y += R.choice((0, 1, 1))
            x %= self.w
            y %= self.h

    def bolt(self, x, y):
        self.put(x, y, (200, 200, 195))
        self.put(x + 1, y + 1, (60, 60, 58))
        self.put(x + 1, y, (150, 150, 145))
        self.put(x, y + 1, (120, 120, 115))

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.img.save(path)


# ----------------------------------------------------------------------------
# Texturas de bloques
# ----------------------------------------------------------------------------

def concrete(cracked=False):
    t = Tex().noise((118, 118, 114), 9)
    t.spots((80, 78, 72), 4, 2.2, 0.35)
    t.spots((150, 150, 145), 3, 1.5, 0.3)
    for _ in range(6):
        t.put(R.randrange(16), R.randrange(16), (90, 90, 86))
    if cracked:
        for _ in range(3):
            t.crack((58, 58, 55), R.randint(8, 14))
        t.spots((70, 90, 60), 2, 1.5, 0.3)  # musgo/humedad
    return t


def metal_plating():
    t = Tex().noise((130, 134, 138), 6)
    for y in range(16):
        for x in range(16):
            if y % 4 == 0:
                t.put(x, y, shade(t.get(x, y), 1.1))
    t.frame(0, 0, 15, 15, (88, 90, 94))
    t.rect(7, 0, 8, 15, (100, 102, 106))
    for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13), (4, 1), (10, 13)):
        t.bolt(x, y)
    t.spots((140, 80, 40), 2, 1.5, 0.45)
    return t


def rusty_metal():
    t = Tex().noise((120, 82, 56), 12)
    t.spots((150, 72, 30), 6, 2.5, 0.6)
    t.spots((70, 50, 40), 4, 1.8, 0.5)
    t.spots((128, 128, 125), 3, 1.6, 0.5)
    for y in range(0, 16, 5):
        for x in range(16):
            t.put(x, y, shade(t.get(x, y), 0.7))
    t.bolt(2, 2)
    t.bolt(12, 12)
    return t


def industrial_wall(stripe=False):
    t = Tex().noise((168, 166, 156), 7)
    for y in range(16):
        if y in (7, 15):
            for x in range(16):
                t.put(x, y, shade(t.get(x, y), 0.75))
    for x in (0, 8):
        for y in range(0, 7):
            t.put(x, y, shade(t.get(x, y), 0.8))
    for x in (4, 12):
        for y in range(8, 15):
            t.put(x, y, shade(t.get(x, y), 0.8))
    t.spots((95, 88, 70), 3, 2.0, 0.35)
    if stripe:
        t.noise((210, 170, 40), 10, 0, 10, 16, 14)
        for x in range(16):
            for y in range(10, 14):
                if (x + y) % 6 < 3:
                    t.put(x, y, (30, 30, 30))
        t.spots((95, 88, 70), 2, 1.5, 0.4)
    return t


def checkered():
    t = Tex()
    for x in range(16):
        for y in range(16):
            black = ((x // 4) + (y // 4)) % 2 == 0
            base = (34, 34, 36) if black else (226, 224, 214)
            t.put(x, y, tuple(clamp(c + R.uniform(-8, 8)) for c in base))
    t.spots((110, 90, 60), 4, 1.8, 0.35)
    t.crack((90, 90, 90), 6)
    return t


def dirty_tiles():
    t = Tex().noise((200, 204, 196), 6)
    for i in range(16):
        for k in (0, 8):
            t.put(i, k, (140, 140, 132))
            t.put(k, i, (140, 140, 132))
    t.spots((120, 110, 70), 5, 2.0, 0.4)
    t.spots((90, 30, 30), 1, 1.2, 0.3)
    return t


def pizzeria_wall():
    t = Tex()
    for x in range(16):
        for y in range(16):
            if y < 7:
                base = (178, 40, 44) if (x // 2 + y // 2) % 2 == 0 else (230, 225, 210)
            elif y < 9:
                base = (40, 40, 40)
            else:
                base = (220, 216, 200) if ((x // 4) + (y // 4)) % 2 == 0 else (32, 32, 34)
            t.put(x, y, tuple(clamp(c + R.uniform(-9, 9)) for c in base))
    t.spots((90, 70, 40), 4, 2.0, 0.35)
    return t


def hazard():
    t = Tex()
    for x in range(16):
        for y in range(16):
            yellow = ((x + y) // 4) % 2 == 0
            base = (222, 178, 32) if yellow else (28, 28, 28)
            t.put(x, y, tuple(clamp(c + R.uniform(-10, 10)) for c in base))
    t.spots((90, 70, 40), 4, 2.0, 0.4)
    t.spots((120, 120, 118), 3, 1.0, 0.6)
    return t


def ceiling_panel():
    t = Tex().noise((70, 72, 76), 6)
    t.frame(0, 0, 15, 15, (40, 42, 44))
    for x in range(2, 14, 3):
        for y in range(2, 14, 3):
            t.put(x, y, (35, 36, 38))
    t.spots((100, 80, 50), 2, 1.5, 0.35)
    return t


def stage_floor():
    t = Tex()
    for y in range(16):
        plank = y // 4
        base = (96, 56, 40) if plank % 2 else (110, 64, 44)
        for x in range(16):
            t.put(x, y, tuple(clamp(c + R.uniform(-8, 8)) for c in base))
        if y % 4 == 3:
            for x in range(16):
                t.put(x, y, (52, 30, 22))
    for plank in range(4):
        x = (plank * 7 + 3) % 16
        for y in range(plank * 4, plank * 4 + 3):
            t.put(x, y, (60, 34, 24))
    t.spots((40, 30, 25), 3, 1.5, 0.4)
    return t


def curtain():
    t = Tex()
    for x in range(16):
        f = 0.75 + 0.25 * math.cos(x / 16 * math.pi * 4)
        for y in range(16):
            base = (110, 30, 120)
            t.put(x, y, tuple(clamp(c * f + R.uniform(-6, 6)) for c in base))
    for x in range(16):
        if R.random() < 0.3:
            t.put(x, 15, (220, 180, 50))
    t.spots((40, 20, 40), 3, 1.5, 0.35)
    return t


def cable_bundle():
    t = Tex().noise((30, 30, 32), 4)
    colors = [(170, 40, 40), (40, 70, 160), (190, 160, 40), (40, 40, 42), (40, 130, 60)]
    for i in range(5):
        y0 = i * 3 + 1
        c = colors[i]
        for x in range(16):
            yy = y0 + (1 if (x // 3 + i) % 3 == 0 else 0)
            t.put(x, yy, c)
            t.put(x, yy + 1, shade(c, 0.6))
    t.rect(6, 0, 7, 15, (90, 90, 94))
    return t


def generator_casing():
    t = Tex().noise((62, 92, 64), 8)
    t.frame(0, 0, 15, 15, (36, 52, 38))
    for y in (4, 7, 10):
        for x in range(3, 13):
            t.put(x, y, (24, 30, 26))
    t.bolt(1, 1)
    t.bolt(13, 1)
    t.bolt(1, 13)
    t.bolt(13, 13)
    t.spots((140, 80, 40), 3, 1.6, 0.5)
    t.rect(5, 12, 10, 13, (200, 170, 40))
    return t


def metal_grate():
    t = Tex()
    for x in range(16):
        for y in range(16):
            solid = x % 4 == 0 or y % 4 == 0
            if solid:
                t.put(x, y, tuple(clamp(c + R.uniform(-10, 10)) for c in (110, 112, 116)))
    t.spots((140, 80, 40), 2, 1.5, 0.5)
    return t


def lamp(lit):
    t = Tex().noise((60, 62, 64), 5)
    t.frame(0, 0, 15, 15, (40, 40, 42))
    inner = (250, 246, 210) if lit else (70, 72, 70)
    t.noise(inner, 6, 2, 2, 14, 14)
    if lit:
        for x in range(2, 14):
            t.put(x, 7, (255, 255, 240))
    for x in range(2, 14, 3):
        for y in range(2, 14):
            t.put(x, y, shade(t.get(x, y), 0.8))
    t.spots((60, 50, 30), 2, 1.2, 0.4)
    return t


def emergency_light():
    t = Tex().noise((50, 50, 52), 5)
    t.frame(0, 0, 15, 15, (30, 30, 30))
    t.noise((230, 40, 30), 10, 3, 3, 13, 13)
    for x in range(4, 12):
        t.put(x, 5, (255, 140, 120))
    return t


def door(top):
    t = Tex().noise((120, 124, 128), 6)
    t.frame(0, 0, 15, 15, (70, 72, 76))
    if top:
        t.rect(3, 3, 12, 9, (30, 40, 44))  # ventanilla
        for x in range(3, 13):
            t.put(x, 6, (80, 90, 96))
        t.frame(2, 2, 13, 10, (60, 62, 66))
        t.rect(2, 12, 13, 13, (200, 160, 40))
    else:
        for y in range(2, 14):
            for x in range(2, 14):
                if (x + y) % 6 < 3:
                    t.put(x, y, (205, 165, 40))
                else:
                    t.put(x, y, (30, 30, 30))
        t.rect(12, 6, 13, 9, (40, 40, 40))  # tirador
    t.spots((140, 80, 40), 3, 1.5, 0.45)
    return t


def vent_grate():
    t = Tex()
    for x in range(16):
        for y in range(16):
            if x in (0, 15) or y in (0, 15) or (y % 3 == 0):
                t.put(x, y, tuple(clamp(c + R.uniform(-8, 8)) for c in (120, 122, 126)))
    t.bolt(1, 1)
    t.bolt(13, 13)
    t.spots((140, 80, 40), 2, 1.2, 0.5)
    return t


def crate_side():
    t = Tex().noise((150, 112, 66), 9)
    for y in range(16):
        if y % 5 == 0:
            for x in range(16):
                t.put(x, y, (90, 64, 36))
    t.frame(0, 0, 15, 15, (80, 56, 30))
    for i in range(16):
        t.put(i, i, (100, 72, 40))
    t.rect(5, 6, 10, 9, (220, 210, 190))
    t.put(7, 7, (200, 40, 40))
    t.put(8, 7, (200, 40, 40))
    t.put(7, 8, (200, 40, 40))
    t.put(8, 8, (200, 40, 40))
    return t


def crate_top(open_):
    t = Tex().noise((150, 112, 66), 9)
    t.frame(0, 0, 15, 15, (80, 56, 30))
    if open_:
        t.noise((40, 30, 20), 5, 2, 2, 14, 14)
    else:
        for x in range(16):
            t.put(x, 5, (90, 64, 36))
            t.put(x, 10, (90, 64, 36))
    return t


def sign(kind):
    t = Tex()
    if kind == "exit":
        t.noise((20, 120, 50), 8)
        draw_text(t, "EXIT", (235, 255, 235), 2, 5)
    elif kind == "danger":
        t.noise((220, 180, 30), 8)
        # triángulo
        for y in range(3, 13):
            half = (y - 3) // 2
            for x in range(8 - half, 8 + half + 1):
                t.put(x, y, (25, 25, 25))
        for y in range(5, 10):
            t.put(8, y, (220, 180, 30))
        t.put(8, 11, (220, 180, 30))
    elif kind == "pizzeria":
        t.noise((160, 30, 36), 8)
        # porción de pizza
        for y in range(3, 13):
            w = (y - 3) // 2
            for x in range(8 - w, 8 + w + 1):
                t.put(x, y, (240, 200, 70))
        for (x, y) in ((7, 8), (9, 10), (8, 6)):
            t.put(x, y, (190, 40, 30))
        for x in range(3, 14):
            t.put(x, 13, (200, 140, 60))
    else:  # staff
        t.noise((40, 60, 140), 8)
        draw_text(t, "STAF", (235, 235, 255), 1, 5)
    t.frame(0, 0, 15, 15, (190, 190, 190))
    t.spots((60, 50, 40), 2, 1.5, 0.35)
    return t


FONT = {
    "E": ["111", "100", "110", "100", "111"],
    "X": ["101", "101", "010", "101", "101"],
    "I": ["111", "010", "010", "010", "111"],
    "T": ["111", "010", "010", "010", "010"],
    "S": ["111", "100", "111", "001", "111"],
    "A": ["010", "101", "111", "101", "101"],
    "F": ["111", "100", "110", "100", "100"],
    "N": ["101", "111", "111", "111", "101"],
    "O": ["111", "101", "101", "101", "111"],
    "C": ["111", "100", "100", "100", "111"],
    "H": ["101", "101", "111", "101", "101"],
}


def draw_text(t, text, color, x, y):
    for ch in text:
        g = FONT.get(ch)
        if g:
            for gy, row in enumerate(g):
                for gx, v in enumerate(row):
                    if v == "1":
                        t.put(x + gx, y + gy, color)
        x += 4


def party_table(top):
    if top:
        t = Tex()
        for x in range(16):
            for y in range(16):
                base = (230, 228, 220) if ((x // 4) + (y // 4)) % 2 else (190, 50, 60)
                t.put(x, y, tuple(clamp(c + R.uniform(-7, 7)) for c in base))
        t.spots((120, 90, 50), 3, 1.6, 0.35)
        # sombrero de fiesta
        for y in range(5, 10):
            for x in range(8 - (y - 5) // 2, 8 + (y - 5) // 2 + 1):
                t.put(x, y, (60, 120, 220))
        return t
    t = Tex().noise((200, 200, 196), 6)
    t.rect(0, 0, 15, 1, (190, 50, 60))
    return t


def shelter_core(top):
    t = Tex().noise((50, 56, 62), 6)
    t.frame(0, 0, 15, 15, (30, 34, 38))
    if top:
        for x in range(16):
            for y in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 5.5:
                    t.put(x, y, mix((60, 220, 240), (255, 255, 255), max(0, 1 - d / 3)))
                elif d < 6.5:
                    t.put(x, y, (30, 120, 140))
    else:
        t.noise((60, 200, 220), 10, 3, 4, 12, 11)
        for x in range(3, 13):
            t.put(x, 7, (220, 255, 255))
        t.bolt(1, 1)
        t.bolt(13, 13)
    return t


def marker(color, symbol):
    t = Tex()
    for x in range(16):
        for y in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d > 6:
                continue
            if d > 5:
                t.put(x, y, shade(color, 0.6))
            else:
                t.put(x, y, tuple(clamp(c + R.uniform(-8, 8)) for c in shade(color, 0.85)))
    for (x, y) in symbol:
        t.put(x, y, (240, 240, 240))
    return t


ARROW = [(7, 4), (8, 4), (6, 5), (9, 5), (5, 6), (10, 6), (7, 5), (8, 5), (7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8),
         (7, 9), (8, 9), (7, 10), (8, 10)]
SKULL = [(6, 5), (7, 5), (8, 5), (9, 5), (5, 6), (10, 6), (5, 7), (7, 7), (8, 7), (10, 7), (5, 8), (10, 8), (6, 9), (9, 9),
         (6, 10), (7, 10), (8, 10), (9, 10)]


def machine_side():
    t = Tex().noise((92, 96, 100), 6)
    t.frame(0, 0, 15, 15, (55, 58, 62))
    for y in range(3, 13, 2):
        for x in range(3, 13):
            t.put(x, y, (50, 52, 56))
    t.bolt(1, 1)
    t.bolt(13, 1)
    t.bolt(1, 13)
    t.bolt(13, 13)
    t.spots((140, 80, 40), 3, 1.6, 0.5)
    return t


def machine_top():
    t = Tex().noise((84, 88, 92), 6)
    t.frame(0, 0, 15, 15, (55, 58, 62))
    t.rect(4, 4, 11, 11, (60, 62, 66))
    t.frame(4, 4, 11, 11, (110, 112, 116))
    t.spots((140, 80, 40), 2, 1.4, 0.5)
    return t


def station_front(kind, broken):
    t = machine_side()
    t.noise((70, 74, 78), 5, 1, 1, 15, 15)
    t.frame(0, 0, 15, 15, (45, 48, 52))
    led = (220, 40, 30) if broken else (60, 230, 80)
    if kind == "generator":
        for y in (3, 5, 7):
            for x in range(3, 13):
                t.put(x, y, (35, 36, 38))
        t.rect(4, 10, 11, 13, (200, 170, 40))
        t.rect(6, 11, 9, 12, (30, 30, 30))
    elif kind == "fuse_box":
        for i in range(4):
            x = 2 + i * 3
            t.rect(x, 3, x + 1, 10, (60, 50, 40) if (broken and i % 2 == 0) else (200, 190, 160))
            t.put(x, 3, (180, 150, 60))
            t.put(x + 1, 10, (180, 150, 60))
    elif kind == "wiring":
        cols = [(200, 40, 40), (40, 80, 200), (220, 190, 40), (40, 170, 60)]
        for i, c in enumerate(cols):
            y = 3 + i * 3
            for x in range(2, 14):
                if broken and 6 <= x <= 8 and i % 2 == 0:
                    continue
                t.put(x, y, c)
    elif kind == "electrical_panel":
        for i in range(6):
            x = 2 + (i % 3) * 4
            y = 3 + (i // 3) * 5
            t.rect(x, y, x + 2, y + 3, (40, 40, 42))
            t.rect(x + 1, y + (0 if (i + broken) % 2 else 2), x + 1, y + (1 if (i + broken) % 2 else 3), (220, 220, 220))
    elif kind == "lighting":
        for i in range(3):
            x = 3 + i * 4
            t.rect(x, 3, x + 2, 6, (250, 240, 170) if not broken or i == 1 else (60, 60, 55))
        t.rect(3, 9, 12, 12, (40, 40, 42))
    elif kind == "auto_door":
        for x in range(2, 14):
            for y in range(3, 13):
                if (x + y) % 6 < 3:
                    t.put(x, y, (200, 160, 40))
        t.rect(6, 6, 9, 9, (40, 40, 42))
    elif kind == "ventilation":
        for x in range(16):
            for y in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 6:
                    a = math.atan2(y - 7.5, x - 7.5)
                    if int((a / math.pi * 4 + d * 0.3) % 2) == 0:
                        t.put(x, y, (40, 42, 44))
                    else:
                        t.put(x, y, (130, 134, 138))
    elif kind == "terminal":
        t.rect(2, 2, 13, 9, (10, 30, 14))
        if not broken:
            for y in (3, 5, 7):
                for x in range(3, 3 + R.randint(5, 9)):
                    t.put(x, y, (80, 230, 100))
        else:
            for _ in range(14):
                t.put(R.randint(2, 13), R.randint(2, 9), (R.randint(60, 200),) * 3)
        t.rect(3, 11, 12, 13, (50, 50, 52))
    elif kind == "security":
        for i in range(4):
            x = 2 + (i % 2) * 6
            y = 2 + (i // 2) * 5
            t.rect(x, y, x + 4, y + 3, (15, 25, 30))
            if not broken or i == 3:
                t.put(x + 1, y + 1, (120, 160, 170))
                t.put(x + 3, y + 2, (90, 120, 130))
    t.rect(13, 13, 14, 14, led)
    if broken:
        t.crack((20, 20, 20), 6)
        t.spots((30, 25, 20), 2, 1.4, 0.6)
    return t


# ----------------------------------------------------------------------------
# Objetos
# ----------------------------------------------------------------------------

def item_canvas():
    return Tex()


def line(t, pts, c):
    for (x, y) in pts:
        t.put(x, y, c)


def flashlight(on):
    t = item_canvas()
    body = (60, 62, 66)
    for i in range(9):
        x, y = 3 + i, 12 - i
        for d in (-1, 0, 1):
            t.put(x + d, y, shade(body, 1.0 + 0.15 * d))
    for d in range(-2, 3):
        t.put(12 + d, 3, (140, 142, 146))
        t.put(12, 3 + d, (140, 142, 146))
    t.put(12, 3, (255, 250, 190) if on else (90, 92, 90))
    t.put(13, 2, (255, 250, 190) if on else (110, 112, 110))
    t.put(11, 4, (255, 240, 160) if on else (100, 100, 100))
    t.put(6, 9, (200, 40, 40))  # botón
    if on:
        for (x, y) in ((14, 1), (15, 0), (13, 0), (15, 2)):
            t.put(x, y, (255, 250, 200), 200)
    return t


def wrench():
    t = item_canvas()
    c = (150, 152, 156)
    for i in range(10):
        t.put(3 + i, 12 - i, c)
        t.put(4 + i, 12 - i, shade(c, 0.75))
    for (x, y) in ((12, 1), (13, 1), (14, 2), (14, 3), (11, 2), (13, 4), (14, 4)):
        t.put(x, y, c)
    for (x, y) in ((2, 12), (2, 13), (3, 14), (4, 14), (1, 13)):
        t.put(x, y, c)
    t.put(9, 6, (140, 80, 40))
    return t


def screwdriver():
    t = item_canvas()
    for i in range(5):
        for d in (0, 1):
            t.put(2 + i + d, 13 - i, (200, 50, 40) if d == 0 else (150, 30, 25))
    for i in range(6):
        t.put(7 + i, 8 - i, (170, 172, 176))
    t.put(13, 2, (220, 220, 225))
    t.put(4, 12, (240, 200, 60))
    return t


def pliers():
    t = item_canvas()
    for i in range(6):
        t.put(2 + i, 13 - i, (40, 90, 200))
        t.put(4 + i, 14 - i, (40, 90, 200))
    t.put(8, 7, (90, 90, 94))
    t.put(9, 8, (90, 90, 94))
    for i in range(4):
        t.put(9 + i, 6 - i, (160, 162, 166))
        t.put(10 + i, 7 - i, (130, 132, 136))
    return t


def fuse():
    t = item_canvas()
    t.rect(6, 3, 9, 12, (220, 215, 200))
    t.rect(6, 2, 9, 3, (180, 150, 60))
    t.rect(6, 12, 9, 13, (180, 150, 60))
    t.rect(7, 4, 8, 11, (240, 236, 220))
    line(t, [(7, 5), (8, 6), (7, 7), (8, 8), (7, 9), (8, 10)], (170, 120, 40))
    return t


def cable():
    t = item_canvas()
    pts = []
    for i in range(30):
        a = i / 30 * math.pi * 2
        pts.append((int(7.5 + math.cos(a) * 5), int(7.5 + math.sin(a) * 4)))
    line(t, pts, (40, 40, 42))
    line(t, [(p[0], p[1] + 1) for p in pts[:15]], (200, 40, 40))
    line(t, [(12, 8), (13, 9), (14, 10), (15, 10)], (200, 150, 60))
    line(t, [(3, 6), (2, 5), (1, 5)], (200, 150, 60))
    return t


def electronic_parts():
    t = item_canvas()
    t.rect(2, 4, 13, 12, (30, 110, 50))
    t.frame(2, 4, 13, 12, (20, 70, 30))
    t.rect(5, 6, 9, 9, (30, 30, 32))
    for x in range(5, 10, 2):
        t.put(x, 5, (200, 200, 200))
        t.put(x, 10, (200, 200, 200))
    t.put(11, 6, (220, 50, 40))
    t.put(11, 9, (240, 200, 60))
    line(t, [(3, 11), (4, 11), (4, 10), (10, 11), (12, 11)], (200, 170, 60))
    return t


def battery():
    t = item_canvas()
    t.rect(4, 3, 11, 13, (40, 40, 44))
    t.rect(6, 2, 9, 2, (180, 180, 180))
    t.rect(5, 4, 10, 7, (240, 200, 40))
    t.rect(5, 8, 10, 12, (60, 62, 66))
    t.put(7, 10, (240, 240, 240))
    t.put(8, 10, (240, 240, 240))
    t.put(7, 9, (240, 240, 240))
    t.put(8, 11, (240, 240, 240))
    return t


def security_door_item():
    t = item_canvas()
    t.rect(4, 1, 11, 14, (120, 124, 128))
    t.frame(4, 1, 11, 14, (70, 72, 76))
    t.rect(5, 2, 10, 5, (30, 40, 44))
    for y in range(8, 14):
        for x in range(5, 11):
            if (x + y) % 4 < 2:
                t.put(x, y, (205, 165, 40))
    t.put(10, 7, (30, 30, 30))
    return t


def icon():
    img = Image.new("RGBA", (128, 128), (12, 10, 14, 255))
    d = ImageDraw.Draw(img)
    # luz de techo y halo
    for r in range(60, 0, -2):
        a = int(70 * (1 - r / 60))
        d.ellipse((64 - r, 18 - r // 2, 64 + r, 18 + r // 2), fill=(255, 230, 160, a))
    # silueta de oso con sombrero y ojos brillantes
    d.rectangle((40, 50, 88, 96), fill=(40, 26, 18))
    d.rectangle((34, 44, 46, 56), fill=(40, 26, 18))
    d.rectangle((82, 44, 94, 56), fill=(40, 26, 18))
    d.rectangle((50, 30, 78, 46), fill=(10, 10, 10))
    d.rectangle((44, 44, 84, 49), fill=(10, 10, 10))
    d.rectangle((50, 82, 78, 92), fill=(80, 60, 44))
    d.rectangle((54, 84, 74, 88), fill=(210, 200, 170))
    for ex in (52, 70):
        d.rectangle((ex, 62, ex + 8, 70), fill=(240, 240, 230))
        d.rectangle((ex + 3, 64, ex + 6, 68), fill=(120, 220, 255))
    d.rectangle((60, 74, 68, 79), fill=(10, 10, 10))
    d.rectangle((0, 104, 127, 127), fill=(20, 16, 18))
    for x in range(0, 128, 16):
        d.rectangle((x, 104, x + 7, 109), fill=(200, 160, 30))
    try:
        d.text((8, 110), "NIGHT SHIFT", fill=(230, 60, 50))
    except Exception:
        pass
    return img


# ----------------------------------------------------------------------------
# JSON helpers
# ----------------------------------------------------------------------------

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


def m(name):
    return "%s:block/%s" % (MOD, name)


def cube_all(name, tex=None):
    write_json(os.path.join(MB, name + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": m(tex or name)}})


def simple_state(name, model=None):
    write_json(os.path.join(BS, name + ".json"), {"variants": {"": {"model": m(model or name)}}})


def block_item(name, model=None):
    write_json(os.path.join(MI, name + ".json"), {"parent": m(model or name)})


def gen_item(name, tex=None, extra=None):
    d = {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (MOD, tex or name)}}
    if extra:
        d.update(extra)
    write_json(os.path.join(MI, name + ".json"), d)


def handheld(name):
    write_json(os.path.join(MI, name + ".json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/%s" % (MOD, name)}})


FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}

STATIONS = ["generator", "fuse_box", "wiring", "electrical_panel", "lighting", "auto_door", "ventilation", "terminal", "security"]
ZONES = {"general": (60, 90, 200), "pizzeria": (200, 60, 70), "service": (220, 170, 40), "technical": (60, 170, 90), "tunnel": (130, 80, 170)}
KINDS = {"any": (120, 120, 120), "bonnie": (91, 63, 153), "chica": (232, 195, 58), "foxy": (163, 64, 42), "freddy": (107, 66, 38)}


def main():
    # --- bloques decorativos de cubo completo ---
    simple = {
        "industrial_concrete": concrete(), "cracked_concrete": concrete(True), "metal_plating": metal_plating(),
        "rusty_metal": rusty_metal(), "industrial_wall": industrial_wall(), "industrial_wall_stripe": industrial_wall(True),
        "checkered_floor": checkered(), "dirty_tiles": dirty_tiles(), "pizzeria_wall": pizzeria_wall(), "hazard_block": hazard(),
        "ceiling_panel": ceiling_panel(), "stage_floor": stage_floor(), "stage_curtain": curtain(), "cable_bundle": cable_bundle(),
        "metal_grate": metal_grate(),
    }
    for name, tex in simple.items():
        tex.save(os.path.join(TB, name + ".png"))
        cube_all(name)
        simple_state(name)
        block_item(name)

    # carcasa de generador
    generator_casing().save(os.path.join(TB, "generator_casing.png"))
    machine_top().save(os.path.join(TB, "machine_top.png"))
    machine_side().save(os.path.join(TB, "machine_side.png"))
    write_json(os.path.join(MB, "generator_casing.json"), {"parent": "minecraft:block/cube_bottom_top",
                                                          "textures": {"top": m("machine_top"), "bottom": m("machine_top"), "side": m("generator_casing")}})
    simple_state("generator_casing")
    block_item("generator_casing")

    # --- instalaciones reparables ---
    for st in STATIONS:
        for broken in (False, True):
            suffix = "_broken" if broken else ""
            station_front(st, broken).save(os.path.join(TB, "station_%s_front%s.png" % (st, suffix)))
            write_json(os.path.join(MB, "station_%s%s.json" % (st, suffix)), {
                "parent": "minecraft:block/orientable",
                "textures": {"top": m("machine_top"), "front": m("station_%s_front%s" % (st, suffix)), "side": m("machine_side")}})
        variants = {}
        for facing, y in FACING_Y.items():
            for broken in ("false", "true"):
                v = {"model": m("station_%s%s" % (st, "_broken" if broken == "true" else ""))}
                if y:
                    v["y"] = y
                variants["broken=%s,facing=%s" % (broken, facing)] = v
        write_json(os.path.join(BS, "station_%s.json" % st), {"variants": variants})
        block_item("station_" + st, "station_%s_broken" % st)

    # --- lámpara de techo ---
    lamp(True).save(os.path.join(TB, "flickering_light_on.png"))
    lamp(False).save(os.path.join(TB, "flickering_light_off.png"))
    for lit in ("on", "off"):
        write_json(os.path.join(MB, "flickering_light_%s.json" % lit), {
            "parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": m("flickering_light_" + lit), "lamp": m("flickering_light_" + lit), "case": m("ceiling_panel")},
            "elements": [
                {"from": [3, 14, 3], "to": [13, 16, 13], "faces": {
                    "down": {"texture": "#lamp", "uv": [3, 3, 13, 13]}, "up": {"texture": "#case"},
                    "north": {"texture": "#case", "uv": [3, 0, 13, 2]}, "south": {"texture": "#case", "uv": [3, 0, 13, 2]},
                    "east": {"texture": "#case", "uv": [3, 0, 13, 2]}, "west": {"texture": "#case", "uv": [3, 0, 13, 2]}}},
                {"from": [4, 13, 4], "to": [12, 14, 12], "shade": False, "faces": {
                    "down": {"texture": "#lamp", "uv": [4, 4, 12, 12]},
                    "north": {"texture": "#lamp", "uv": [4, 7, 12, 8]}, "south": {"texture": "#lamp", "uv": [4, 7, 12, 8]},
                    "east": {"texture": "#lamp", "uv": [4, 7, 12, 8]}, "west": {"texture": "#lamp", "uv": [4, 7, 12, 8]}}},
            ]})
    variants = {}
    for lit in ("true", "false"):
        for faulty in ("true", "false"):
            variants["faulty=%s,lit=%s" % (faulty, lit)] = {"model": m("flickering_light_" + ("on" if lit == "true" else "off"))}
    write_json(os.path.join(BS, "flickering_light.json"), {"variants": variants})
    block_item("flickering_light", "flickering_light_on")

    # --- objetos de pared (luz de emergencia y carteles) ---
    emergency_light().save(os.path.join(TB, "emergency_light.png"))
    write_json(os.path.join(MB, "emergency_light.json"), {
        "parent": "minecraft:block/block", "ambientocclusion": False,
        "textures": {"particle": m("emergency_light"), "front": m("emergency_light"), "side": m("machine_side")},
        "elements": [{"from": [4, 5, 11], "to": [12, 11, 16], "faces": {
            "north": {"texture": "#front"}, "south": {"texture": "#side"},
            "east": {"texture": "#side", "uv": [0, 5, 5, 11]}, "west": {"texture": "#side", "uv": [0, 5, 5, 11]},
            "up": {"texture": "#side", "uv": [4, 0, 12, 5]}, "down": {"texture": "#front", "uv": [4, 0, 12, 5]}}}]})
    wall_state("emergency_light")
    block_item("emergency_light")

    for kind in ("exit", "danger", "pizzeria", "staff"):
        name = "sign_" + kind
        sign(kind).save(os.path.join(TB, name + ".png"))
        write_json(os.path.join(MB, name + ".json"), {
            "parent": "minecraft:block/block",
            "textures": {"particle": m(name), "front": m(name), "side": m("metal_plating")},
            "elements": [{"from": [0, 3, 15], "to": [16, 13, 16], "faces": {
                "north": {"texture": "#front", "uv": [0, 3, 16, 13]}, "south": {"texture": "#side"},
                "east": {"texture": "#side", "uv": [0, 0, 1, 10]}, "west": {"texture": "#side", "uv": [0, 0, 1, 10]},
                "up": {"texture": "#side", "uv": [0, 0, 16, 1]}, "down": {"texture": "#side", "uv": [0, 0, 16, 1]}}}]})
        wall_state(name)
        gen_item(name, tex=None) if False else write_json(os.path.join(MI, name + ".json"),
                                                          {"parent": "minecraft:item/generated", "textures": {"layer0": m(name)}})

    # --- puerta de seguridad ---
    door(True).save(os.path.join(TB, "security_door_top.png"))
    door(False).save(os.path.join(TB, "security_door_bottom.png"))
    for part in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open",
                 "top_left", "top_left_open", "top_right", "top_right_open"):
        write_json(os.path.join(MB, "security_door_%s.json" % part), {
            "parent": "minecraft:block/door_" + part,
            "textures": {"bottom": m("security_door_bottom"), "top": m("security_door_top")}})
    door_rot = {
        "east": {"closed": 0, "left_open": 90, "right_open": 270},
        "north": {"closed": 270, "left_open": 0, "right_open": 180},
        "south": {"closed": 90, "left_open": 180, "right_open": 0},
        "west": {"closed": 180, "left_open": 270, "right_open": 90},
    }
    variants = {}
    for facing, rots in door_rot.items():
        for half, hname in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for opn in ("false", "true"):
                    model = "security_door_%s_%s%s" % (hname, hinge, "_open" if opn == "true" else "")
                    y = rots["closed"] if opn == "false" else rots[hinge + "_open"]
                    v = {"model": m(model)}
                    if y:
                        v["y"] = y
                    variants["facing=%s,half=%s,hinge=%s,open=%s" % (facing, half, hinge, opn)] = v
    write_json(os.path.join(BS, "security_door.json"), {"variants": variants})
    security_door_item().save(os.path.join(TI, "security_door.png"))
    gen_item("security_door")

    # --- rejilla de ventilación (trampilla) ---
    vent_grate().save(os.path.join(TB, "vent_grate.png"))
    for part in ("bottom", "top", "open"):
        write_json(os.path.join(MB, "vent_grate_%s.json" % part), {
            "parent": "minecraft:block/template_trapdoor_" + part, "textures": {"texture": m("vent_grate")}})
    variants = {}
    for facing, y in FACING_Y.items():
        for half in ("bottom", "top"):
            for opn in ("false", "true"):
                for wl in ("false", "true"):
                    for pw in ("false", "true"):
                        if opn == "true":
                            v = {"model": m("vent_grate_open")}
                            if y:
                                v["y"] = y
                            if half == "top":
                                v["x"] = 180
                                v["y"] = (y + 180) % 360
                        else:
                            v = {"model": m("vent_grate_" + half)}
                        variants["facing=%s,half=%s,open=%s,powered=%s,waterlogged=%s" % (facing, half, opn, pw, wl)] = v
    write_json(os.path.join(BS, "vent_grate.json"), {"variants": variants})
    block_item("vent_grate", "vent_grate_bottom")

    # --- caja de suministros ---
    crate_side().save(os.path.join(TB, "supply_crate_side.png"))
    crate_top(False).save(os.path.join(TB, "supply_crate_top.png"))
    crate_top(True).save(os.path.join(TB, "supply_crate_top_open.png"))
    for looted in (False, True):
        write_json(os.path.join(MB, "supply_crate%s.json" % ("_looted" if looted else "")), {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {"top": m("supply_crate_top_open" if looted else "supply_crate_top"), "bottom": m("supply_crate_top"),
                         "side": m("supply_crate_side")}})
    write_json(os.path.join(BS, "supply_crate.json"), {"variants": {
        "looted=false": {"model": m("supply_crate")}, "looted=true": {"model": m("supply_crate_looted")}}})
    block_item("supply_crate")

    # --- mesa de fiesta ---
    party_table(True).save(os.path.join(TB, "party_table_top.png"))
    party_table(False).save(os.path.join(TB, "party_table_side.png"))
    write_json(os.path.join(MB, "party_table.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": m("party_table_top"), "top": m("party_table_top"), "side": m("party_table_side"), "leg": m("metal_plating")},
        "elements": [
            {"from": [0, 12, 0], "to": [16, 13, 16], "faces": {
                "up": {"texture": "#top"}, "down": {"texture": "#side"},
                "north": {"texture": "#side", "uv": [0, 0, 16, 1]}, "south": {"texture": "#side", "uv": [0, 0, 16, 1]},
                "east": {"texture": "#side", "uv": [0, 0, 16, 1]}, "west": {"texture": "#side", "uv": [0, 0, 16, 1]}}},
            {"from": [6, 0, 6], "to": [10, 12, 10], "faces": {
                "north": {"texture": "#leg", "uv": [6, 2, 10, 14]}, "south": {"texture": "#leg", "uv": [6, 2, 10, 14]},
                "east": {"texture": "#leg", "uv": [6, 2, 10, 14]}, "west": {"texture": "#leg", "uv": [6, 2, 10, 14]},
                "down": {"texture": "#leg", "uv": [6, 6, 10, 10]}}},
            {"from": [3, 0, 3], "to": [13, 1, 13], "faces": {
                "up": {"texture": "#leg"}, "down": {"texture": "#leg"},
                "north": {"texture": "#leg", "uv": [3, 0, 13, 1]}, "south": {"texture": "#leg", "uv": [3, 0, 13, 1]},
                "east": {"texture": "#leg", "uv": [3, 0, 13, 1]}, "west": {"texture": "#leg", "uv": [3, 0, 13, 1]}}},
        ]})
    simple_state("party_table")
    block_item("party_table")

    # --- núcleo del refugio ---
    shelter_core(True).save(os.path.join(TB, "shelter_core_top.png"))
    shelter_core(False).save(os.path.join(TB, "shelter_core_side.png"))
    write_json(os.path.join(MB, "shelter_core.json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": m("shelter_core_top"), "bottom": m("metal_plating"), "side": m("shelter_core_side")}})
    simple_state("shelter_core")
    block_item("shelter_core")

    # --- marcadores ---
    def flat_model(name, tex):
        write_json(os.path.join(MB, name + ".json"), {
            "parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": m(tex), "top": m(tex)},
            "elements": [{"from": [1, 0, 1], "to": [15, 1, 15], "faces": {
                "up": {"texture": "#top", "uv": [1, 1, 15, 15]}, "down": {"texture": "#top", "uv": [1, 1, 15, 15]},
                "north": {"texture": "#top", "uv": [1, 7, 15, 8]}, "south": {"texture": "#top", "uv": [1, 7, 15, 8]},
                "east": {"texture": "#top", "uv": [1, 7, 15, 8]}, "west": {"texture": "#top", "uv": [1, 7, 15, 8]}}}]})

    variants = {}
    for zone, col in ZONES.items():
        name = "patrol_node_" + zone
        marker(col, ARROW).save(os.path.join(TB, name + ".png"))
        flat_model(name, name)
        variants["zone=" + zone] = {"model": m(name)}
    write_json(os.path.join(BS, "patrol_node.json"), {"variants": variants})
    block_item("patrol_node", "patrol_node_general")

    variants = {}
    for kind, col in KINDS.items():
        name = "spawn_marker_" + kind
        marker(col, SKULL).save(os.path.join(TB, name + ".png"))
        flat_model(name, name)
        variants["animatronic=" + kind] = {"model": m(name)}
    write_json(os.path.join(BS, "spawn_marker.json"), {"variants": variants})
    block_item("spawn_marker", "spawn_marker_any")

    # --- luz invisible de la linterna ---
    write_json(os.path.join(MB, "flashlight_light.json"), {"textures": {"particle": "minecraft:block/glass"}})
    simple_state("flashlight_light")

    # --- objetos ---
    flashlight(False).save(os.path.join(TI, "flashlight.png"))
    flashlight(True).save(os.path.join(TI, "flashlight_on.png"))
    write_json(os.path.join(MI, "flashlight.json"), {
        "parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/flashlight" % MOD},
        "overrides": [{"predicate": {"%s:on" % MOD: 1}, "model": "%s:item/flashlight_on" % MOD}]})
    handheld("flashlight_on")
    for name, fn in (("wrench", wrench), ("screwdriver", screwdriver), ("pliers", pliers)):
        fn().save(os.path.join(TI, name + ".png"))
        handheld(name)
    for name, fn in (("fuse", fuse), ("cable", cable), ("electronic_parts", electronic_parts), ("battery", battery)):
        fn().save(os.path.join(TI, name + ".png"))
        gen_item(name)
    for egg in ("bonnie", "chica", "foxy", "freddy", "golden_freddy"):
        write_json(os.path.join(MI, egg + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})

    icon().save(os.path.join(ASSETS, "icon.png"))
    write_lang()
    # actualización 1.1 (bloques nuevos y textos): al final, para no alterar las texturas anteriores
    import gen_assets_v11
    gen_assets_v11.gen()
    gen_assets_v11.extend_lang()
    # Aplicar el arte final también al regenerar modelos/traducciones.
    import redesign_textures
    redesign_textures.main()
    print("recursos generados")


def wall_state(name):
    variants = {}
    for facing, y in FACING_Y.items():
        v = {"model": m(name)}
        if y:
            v["y"] = y
        variants["facing=" + facing] = v
    write_json(os.path.join(BS, name + ".json"), {"variants": variants})


# ----------------------------------------------------------------------------
# Traducciones (español)
# ----------------------------------------------------------------------------

def write_lang():
    L = {}
    P = MOD
    L["itemGroup.%s.main" % P] = "FNAF: Night Shift"
    blocks = {
        "industrial_concrete": "Hormigón industrial", "cracked_concrete": "Hormigón agrietado", "metal_plating": "Chapa metálica",
        "rusty_metal": "Metal oxidado", "industrial_wall": "Pared industrial", "industrial_wall_stripe": "Pared industrial con franja",
        "checkered_floor": "Suelo ajedrezado", "dirty_tiles": "Azulejos sucios", "pizzeria_wall": "Pared de pizzería",
        "hazard_block": "Bloque de advertencia", "ceiling_panel": "Panel de techo", "stage_floor": "Suelo de escenario",
        "stage_curtain": "Telón de escenario", "cable_bundle": "Mazo de cables", "generator_casing": "Carcasa de generador",
        "metal_grate": "Rejilla metálica", "flickering_light": "Lámpara industrial", "emergency_light": "Luz de emergencia",
        "security_door": "Puerta de seguridad", "vent_grate": "Rejilla de ventilación", "supply_crate": "Caja de suministros",
        "sign_exit": "Cartel de salida", "sign_danger": "Cartel de peligro", "sign_pizzeria": "Cartel de pizzería",
        "sign_staff": "Cartel de solo personal", "party_table": "Mesa de fiesta", "shelter_core": "Núcleo del refugio",
        "patrol_node": "Nodo de patrulla", "spawn_marker": "Marcador de aparición", "flashlight_light": "Luz de linterna",
        "station_generator": "Generador", "station_fuse_box": "Caja de fusibles", "station_wiring": "Panel de cableado",
        "station_electrical_panel": "Tablero eléctrico", "station_lighting": "Control de iluminación",
        "station_auto_door": "Control de puerta automática", "station_ventilation": "Sistema de ventilación",
        "station_terminal": "Terminal", "station_security": "Equipo de seguridad",
    }
    for k, v in blocks.items():
        L["block.%s.%s" % (P, k)] = v
    items = {
        "flashlight": "Linterna", "wrench": "Llave inglesa", "screwdriver": "Destornillador", "pliers": "Alicate",
        "fuse": "Fusible", "cable": "Cable", "electronic_parts": "Repuestos electrónicos", "battery": "Batería",
        "bonnie_spawn_egg": "Huevo generador de Bonnie", "chica_spawn_egg": "Huevo generador de Chica",
        "foxy_spawn_egg": "Huevo generador de Foxy", "freddy_spawn_egg": "Huevo generador de Freddy",
        "golden_freddy_spawn_egg": "Huevo generador de Golden Freddy",
    }
    for k, v in items.items():
        L["item.%s.%s" % (P, k)] = v
    ents = {"bonnie": "Bonnie", "chica": "Chica", "foxy": "Foxy", "freddy": "Freddy", "golden_freddy": "Golden Freddy"}
    for k, v in ents.items():
        L["entity.%s.%s" % (P, k)] = v
        L["animatronic.%s.%s" % (P, k)] = v
    L["animatronic.%s.any" % P] = "Cualquiera"

    repairs = {"generator": "Generador", "fuse_box": "Caja de fusibles", "wiring": "Cableado", "electrical_panel": "Tablero eléctrico",
               "lighting": "Iluminación", "auto_door": "Puerta automática", "ventilation": "Ventilación", "terminal": "Terminal",
               "security": "Equipo de seguridad"}
    for k, v in repairs.items():
        L["repair.%s.%s" % (P, k)] = v
    zones = {"general": "General", "pizzeria": "Pizzería", "service": "Servicio", "technical": "Técnica", "tunnel": "Túnel"}
    for k, v in zones.items():
        L["zone.%s.%s" % (P, k)] = v
    for k, v in {"facil": "Fácil", "normal": "Normal", "dificil": "Difícil", "pesadilla": "Pesadilla"}.items():
        L["difficulty.%s.%s" % (P, k)] = v
    for k, v in {"none": "Sin noche activa", "repairing": "Reparaciones en curso", "return_to_shelter": "Volver al refugio",
                 "victory": "Noche superada", "defeat": "Noche perdida"}.items():
        L["phase.%s.%s" % (P, k)] = v
    for k, v in {"idle": "Reposo", "patrol": "Patrulla", "investigate": "Investigación", "search": "Búsqueda", "detect": "Detección",
                 "chase": "Persecución", "attack": "Ataque", "reposition": "Reposicionamiento", "stunned": "Aturdido"}.items():
        L["ai_state.%s.%s" % (P, k)] = v

    tips = {
        "fuse": "Repuesto para cajas de fusibles e iluminación.",
        "cable": "Necesario para reparar paneles de cableado.",
        "electronic_parts": "Necesario para los equipos de seguridad.",
        "battery": "Clic derecho: recarga la linterna con menos batería.",
        "wrench": "Generadores, puertas automáticas y ventilación.",
        "screwdriver": "Fusibles, tableros eléctricos y equipos de seguridad.",
        "pliers": "Cableado e iluminación.",
        "tool_stun": "Golpear a un animatrónico lo aturde un instante.",
        "flashlight": "Clic derecho para encender. Ilumina y aturde a Foxy; congela a Freddy.",
        "flashlight_battery": "Batería: %s%%",
    }
    for k, v in tips.items():
        L["tooltip.%s.%s" % (P, k)] = v

    msg = {
        "station_ok": "%s: operativo",
        "caught_cannot_repair": "Fuiste atrapado: no podés reparar hasta la próxima noche.",
        "need_tool": "Necesitás: %s",
        "need_parts": "Necesitás %s × %s",
        "repair_progress": "Reparando %s  %s  %s%%",
        "repair_failed": "¡Fallo! La alarma hizo ruido... algo viene.",
        "repair_done": "%s reparado.",
        "crate_empty": "La caja está vacía.",
        "crate_looted": "Encontraste suministros.",
        "flashlight_empty": "La linterna no tiene batería.",
        "flashlight_died": "¡La linterna se quedó sin batería!",
        "battery_no_target": "No tenés ninguna linterna que recargar.",
        "battery_used": "Linterna recargada.",
        "shelter_set": "Refugio establecido aquí.",
        "node_zone": "Zona del nodo: %s",
        "marker_kind": "Marcador para: %s",
        "no_shelter": "No hay refugio. Colocá un Núcleo del refugio o generá el mapa con /fnaf map generate.",
        "night_locked": "La noche %s todavía está bloqueada.",
        "night_started": "Noche %s iniciada: %s reparaciones, %s minutos. Dificultad: %s.",
        "no_stations": "No hay instalaciones reparables registradas: el objetivo es sobrevivir hasta las 6 AM.",
        "objective_progress": "%s completó una reparación (%s/%s).",
        "player_caught": "¡%s fue atrapado por %s!",
        "animatronics_awake": "Los animatrónicos se han activado...",
        "hour": "%s AM",
        "golden_hint": "No lo mires...",
        "blackout": "Las luces se apagan...",
        "status": "Noche %s · %s · Reparaciones %s/%s · Tiempo %s · Dificultad %s · Desbloqueada hasta %s · Instalaciones %s · Nodos %s · Apariciones %s · Refugio %s",
    }
    for k, v in msg.items():
        L["message.%s.%s" % (P, k)] = v

    tut = {
        "1": "[Tutorial] Bienvenido al turno nocturno. Estás en el refugio: aquí los animatrónicos no pueden entrar.",
        "2": "[Tutorial] Encendé la linterna con clic derecho. Su batería se recarga dentro del refugio.",
        "3": "[Tutorial] Las instalaciones averiadas echan chispas. Mirá los objetivos y la flecha de dirección arriba a la izquierda.",
        "4": "[Tutorial] Usá la herramienta correcta (llave, destornillador o alicate). Las cajas de suministros tienen repuestos.",
        "5": "[Tutorial] Reparar hace ruido. Si un animatrónico te ve, corré, cortá la línea de visión o volvé al refugio.",
    }
    for k, v in tut.items():
        L["tutorial.%s.%s" % (P, k)] = v

    titles = {"night": "NOCHE %s", "survived": "Sobreviviste a la noche", "game_over": "GAME OVER",
              "defeat_caught": "Te atraparon", "defeat_time": "Se acabó el tiempo", "all_repaired": "Todo reparado",
              "return_shelter": "Volvé al refugio"}
    for k, v in titles.items():
        L["title.%s.%s" % (P, k)] = v

    hud = {"night": "NOCHE %s", "time_left": "Quedan %s", "repairs": "Reparaciones: %s/%s", "survive": "Sobreviví hasta las 6 AM",
           "objectives": "Objetivos", "return": "Volver al refugio", "caught": "ATRAPADO - esperá a tus compañeros",
           "grace": "Los animatrónicos despiertan en %s s", "in_shelter": "Refugio: zona segura · recargando linterna",
           "danger": "¡PELIGRO!", "near": "Algo se mueve cerca...", "flashlight_on": "Linterna", "flashlight_off": "Linterna (apagada)"}
    for k, v in hud.items():
        L["hud.%s.%s" % (P, k)] = v

    mg = {"esc": "ESC para abandonar", "wires": "Conectá cada cable con su color", "reset": "Reiniciar",
          "sequence_watch": "Observá la secuencia...", "sequence_repeat": "Repetí la secuencia (%s/%s)",
          "code_memorize": "Memorizá el código", "code_enter": "Introducí el código", "confirm": "Confirmar",
          "replace": "Sustituí las %s piezas quemadas"}
    for k, v in mg.items():
        L["minigame.%s.%s" % (P, k)] = v

    scr = {"victory": "¡Sobreviviste!", "defeat": "Te atraparon", "continue": "Continuar", "night": "Noche %s",
           "repairs": "Reparaciones: %s/%s", "time": "Tiempo: %s", "catches": "Veces atrapado: %s",
           "next_hint": "Clic derecho en el Núcleo del refugio para empezar la siguiente noche.",
           "campaign_done": "¡Completaste las siete noches! Probá la noche 7 personalizada en la configuración.",
           "reason_time": "No terminaste las reparaciones antes de las 6 AM.",
           "reason_caught": "Clic derecho en el Núcleo del refugio para reintentar."}
    for k, v in scr.items():
        L["screen.%s.%s" % (P, k)] = v

    cmd = {"stopped": "Noche detenida.", "restarted": "Noche reiniciada.", "night_set": "Noche actual: %s",
           "time_set": "Tiempo restante actualizado.", "bad_difficulty": "Dificultad desconocida (facil, normal, dificil, pesadilla).",
           "difficulty_set": "Dificultad: %s", "bad_animatronic": "Animatrónico desconocido.",
           "summoned": "%s invocado en %s (entidad de prueba).", "despawned": "%s animatrónicos eliminados.",
           "reset": "Progreso de la campaña restablecido.", "bad_event": "Evento desconocido.",
           "repairs_header": "Instalaciones registradas: %s", "broken": "averiada", "ok": "operativa",
           "repairs_completed": "%s reparaciones completadas.", "config_reloaded": "Configuración recargada.",
           "map_blocked": "No se puede generar aquí: %s bloques no naturales o con datos (primero en %s). Elegí un terreno libre.",
           "map_ok": "Zona libre. El complejo ocupará %s.", "map_generating": "Generando el complejo... (puede tardar unos segundos)",
           "map_done": "Complejo generado: %s bloques, %s instalaciones, %s nodos. Refugio en %s."}
    for k, v in cmd.items():
        L["command.%s.%s" % (P, k)] = v

    subs = {
        "ambient.hum": "Zumbido industrial", "ambient.distant": "Golpe metálico lejano", "step.metal": "Pasos metálicos",
        "mech.servo": "Servomotor", "door.slam": "Puerta metálica", "repair.ratchet": "Herramienta trabajando",
        "repair.spark": "Chispas", "repair.success": "Reparación completada", "repair.fail": "Alarma de fallo",
        "alert.danger": "¡Peligro!", "alert.night_start": "Campanada", "alert.six_am": "Campanadas de las 6 AM",
        "alert.power_out": "Corte de energía", "flashlight.click": "Clic de linterna", "flashlight.dead": "Linterna agotada",
        "entity.bonnie.idle": "Bonnie zumba", "entity.chica.idle": "Chica castañetea", "entity.foxy.run": "Foxy corre",
        "entity.freddy.laugh": "Freddy ríe", "entity.golden_freddy.whisper": "Susurro", "entity.animatronic.hurt": "Golpe metálico",
        "entity.animatronic.jumpscare": "Grito", "music.box": "Caja de música", "block.crate.open": "Caja abierta",
    }
    for k, v in subs.items():
        L["subtitles.%s.%s" % (P, k)] = v

    for lang in ("es_es", "es_ar", "es_mx", "es_cl", "es_uy", "es_ve", "es_ec", "en_us"):
        write_json(os.path.join(ASSETS, "lang", lang + ".json"), dict(sorted(L.items())))


if __name__ == "__main__":
    main()
