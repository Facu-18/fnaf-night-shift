#!/usr/bin/env python3
"""
FNAF: NIGHT SHIFT - generador de animatrónicos.

Genera para cada animatrónico:
  * geometría GeckoLib/Bedrock  -> assets/fnaf_night_shift/geo/<id>.geo.json
  * textura final 512x512 (UV 128x128, densidad 4x) -> textures/entity/<id>.png
  * máscara de brillo (ojos)    -> textures/entity/<id>_glowmask.png
  * animaciones                 -> animations/<id>.animation.json

Los .geo.json se pueden abrir y editar en Blockbench (File > Open Model,
formato "GeckoLib Animated Model" / Bedrock). Las texturas están pintadas
proceduralmente con desgaste: pintura descascarada, suciedad, arañazos,
óxido, tornillos y uniones mecánicas.

Uso:  python tools/gen_animatronics.py
Requiere: Pillow + numpy (la pintura final se aplica con rework_materials.py)
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "fnaf_night_shift")
UV_SIZE = 128          # espacio UV declarado en el .geo.json
SCALE = 2              # píxeles de textura por unidad UV (PNG = 256x256)


def clamp(v, a=0, b=255):
    return max(a, min(b, int(v)))


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(c, f):
    return tuple(clamp(x * f) for x in c)


def mix(a, b, t):
    return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


# ----------------------------------------------------------------------------
# Modelo
# ----------------------------------------------------------------------------

class Cube:
    def __init__(self, bone, origin, size, mat, rotation=None, pivot=None, inflate=0.0, detail=None):
        self.bone = bone
        self.origin = origin
        self.size = [int(s) for s in size]
        assert all(s >= 1 for s in self.size), size
        self.mat = mat
        self.rotation = rotation
        self.pivot = pivot
        self.inflate = inflate
        self.detail = detail
        self.uv = None

    def uv_w(self):
        w, h, d = self.size
        return 2 * (d + w)

    def uv_h(self):
        w, h, d = self.size
        return d + h


class Model:
    def __init__(self, ident):
        self.ident = ident
        self.bones = []
        self.bone_map = {}
        self.cubes = []

    def bone(self, name, parent, pivot, rotation=None):
        b = {"name": name, "pivot": list(pivot)}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = list(rotation)
        self.bones.append(b)
        self.bone_map[name] = b
        return name

    def cube(self, bone, origin, size, mat, **kw):
        c = Cube(bone, list(origin), size, mat, **kw)
        self.cubes.append(c)
        return c

    # simetría: x -> -x
    def mirror_cube(self, bone, origin, size, mat, **kw):
        o = [-(origin[0] + size[0]), origin[1], origin[2]]
        rot = kw.pop("rotation", None)
        piv = kw.pop("pivot", None)
        if rot:
            rot = [rot[0], -rot[1], -rot[2]]
        if piv:
            piv = [-piv[0], piv[1], piv[2]]
        return self.cube(bone, o, size, mat, rotation=rot, pivot=piv, **kw)

    def pack(self):
        # empaquetado por estantes, de mayor a menor altura
        order = sorted(self.cubes, key=lambda c: (-c.uv_h(), -c.uv_w()))
        x, y, shelf_h = 0, 0, 0
        for c in order:
            w, h = c.uv_w(), c.uv_h()
            if x + w > UV_SIZE:
                x = 0
                y += shelf_h
                shelf_h = 0
            if y + h > UV_SIZE:
                raise RuntimeError("UV overflow in %s" % self.ident)
            c.uv = (x, y)
            x += w
            shelf_h = max(shelf_h, h)

    def to_geo(self):
        bones = []
        for b in self.bones:
            out = dict(b)
            cubes = []
            for c in self.cubes:
                if c.bone != b["name"]:
                    continue
                cd = {"origin": [round(v, 3) for v in c.origin], "size": c.size, "uv": list(c.uv)}
                if c.inflate:
                    cd["inflate"] = c.inflate
                if c.rotation:
                    cd["rotation"] = c.rotation
                    cd["pivot"] = c.pivot or c.origin
                cubes.append(cd)
            if cubes:
                out["cubes"] = cubes
            bones.append(out)
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": "geometry." + self.ident,
                    "texture_width": UV_SIZE,
                    "texture_height": UV_SIZE,
                    "visible_bounds_width": 4,
                    "visible_bounds_height": 4.5,
                    "visible_bounds_offset": [0, 1.75, 0],
                },
                "bones": bones,
            }],
        }


# ----------------------------------------------------------------------------
# Pintura procedural
# ----------------------------------------------------------------------------

class Painter:
    def __init__(self, ident, palette, seed):
        self.img = Image.new("RGBA", (UV_SIZE * SCALE, UV_SIZE * SCALE), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (UV_SIZE * SCALE, UV_SIZE * SCALE), (0, 0, 0, 0))
        self.px = self.img.load()
        self.gpx = self.glow.load()
        self.pal = palette
        self.rng = random.Random(seed)

    def faces(self, c):
        """Rectángulos (x, y, w, h) en píxeles para cada cara del cubo."""
        u, v = c.uv
        w, h, d = c.size
        S = SCALE
        r = lambda a, b, ww, hh: (a * S, b * S, ww * S, hh * S)
        return {
            "top": r(u + d, v, w, d),
            "bottom": r(u + d + w, v, w, d),
            "side_a": r(u, v + d, d, h),
            "front": r(u + d, v + d, w, h),
            "side_b": r(u + d + w, v + d, d, h),
            "back": r(u + 2 * d + w, v + d, w, h),
        }

    def put(self, x, y, col, a=255):
        if 0 <= x < self.img.width and 0 <= y < self.img.height:
            self.px[x, y] = (col[0], col[1], col[2], a)

    def get(self, x, y):
        return self.px[x, y][:3]

    # --- primitivas ---
    def fill_noise(self, rect, base, amount=10, streak=0.0):
        x0, y0, w, h = rect
        col_offsets = [self.rng.uniform(-streak, streak) for _ in range(w)]
        for x in range(w):
            for y in range(h):
                n = self.rng.uniform(-amount, amount) + col_offsets[x]
                self.put(x0 + x, y0 + y, tuple(clamp(c + n) for c in base))

    def edge_shade(self, rect, f=0.78):
        x0, y0, w, h = rect
        for x in range(w):
            for y in range(h):
                if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                    self.put(x0 + x, y0 + y, shade(self.get(x0 + x, y0 + y), f))

    def blotch(self, rect, color, count, rmin=1, rmax=3, strength=0.55):
        x0, y0, w, h = rect
        if w < 2 or h < 2:
            return
        for _ in range(count):
            cx = self.rng.randrange(w)
            cy = self.rng.randrange(h)
            r = self.rng.uniform(rmin, rmax)
            for x in range(max(0, int(cx - r - 1)), min(w, int(cx + r + 2))):
                for y in range(max(0, int(cy - r - 1)), min(h, int(cy + r + 2))):
                    dd = math.hypot(x - cx, y - cy)
                    if dd <= r + self.rng.uniform(-0.6, 0.6):
                        t = strength * (1 - dd / (r + 1))
                        self.put(x0 + x, y0 + y, mix(self.get(x0 + x, y0 + y), color, t))

    def scratches(self, rect, count, light=True):
        x0, y0, w, h = rect
        if w < 4 or h < 4:
            return
        for _ in range(count):
            x = self.rng.uniform(0, w)
            y = self.rng.uniform(0, h)
            ang = self.rng.uniform(0, math.pi)
            ln = self.rng.uniform(2, max(3, min(w, h) * 0.6))
            for i in range(int(ln)):
                px = int(x + math.cos(ang) * i)
                py = int(y + math.sin(ang) * i)
                if 0 <= px < w and 0 <= py < h:
                    c = self.get(x0 + px, y0 + py)
                    self.put(x0 + px, y0 + py, shade(c, 1.35) if light else shade(c, 0.6))

    def chips(self, rect, count, under):
        """Pintura descascarada: manchas irregulares que dejan ver el metal."""
        x0, y0, w, h = rect
        if w < 4 or h < 4:
            return
        for _ in range(count):
            cx = self.rng.randrange(w)
            cy = self.rng.randrange(h)
            pts = set()
            n = self.rng.randint(3, 9)
            x, y = cx, cy
            for _ in range(n):
                pts.add((x, y))
                x += self.rng.choice((-1, 0, 1))
                y += self.rng.choice((-1, 0, 1))
            for (x, y) in pts:
                if 0 <= x < w and 0 <= y < h:
                    self.put(x0 + x, y0 + y, tuple(clamp(c + self.rng.uniform(-10, 10)) for c in under))
            # borde oscuro del desconchón
            for (x, y) in pts:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    q = (x + dx, y + dy)
                    if q not in pts and 0 <= q[0] < w and 0 <= q[1] < h:
                        self.put(x0 + q[0], y0 + q[1], shade(self.get(x0 + q[0], y0 + q[1]), 0.7))

    def bolts(self, rect, spacing=6):
        x0, y0, w, h = rect
        if w < 6 or h < 6:
            return
        pos = [(2, 2), (w - 4, 2), (2, h - 4), (w - 4, h - 4)]
        if w > 16:
            pos += [(w // 2 - 1, 2), (w // 2 - 1, h - 4)]
        for (x, y) in pos:
            self.put(x0 + x, y0 + y, (205, 205, 200))
            self.put(x0 + x + 1, y0 + y, (170, 170, 165))
            self.put(x0 + x, y0 + y + 1, (150, 150, 145))
            self.put(x0 + x + 1, y0 + y + 1, (60, 60, 58))

    def seam(self, rect, vertical=True, dashed=True):
        x0, y0, w, h = rect
        if vertical:
            x = w // 2
            for y in range(h):
                if not dashed or (y // 2) % 2 == 0:
                    self.put(x0 + x, y0 + y, shade(self.get(x0 + x, y0 + y), 0.6))
        else:
            y = h // 2
            for x in range(w):
                if not dashed or (x // 2) % 2 == 0:
                    self.put(x0 + x, y0 + y, shade(self.get(x0 + x, y0 + y), 0.6))

    def tear(self, rect, inner):
        """Rotura en la carcasa que deja ver el endoesqueleto."""
        x0, y0, w, h = rect
        if w < 8 or h < 8:
            return
        cx, cy = w // 2 + self.rng.randint(-2, 2), h // 2 + self.rng.randint(-2, 2)
        rx, ry = w * 0.22, h * 0.25
        for x in range(w):
            for y in range(h):
                dd = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
                if dd < 1 + self.rng.uniform(-0.25, 0.25):
                    c = inner if (x + y) % 5 else shade(inner, 0.7)
                    self.put(x0 + x, y0 + y, tuple(clamp(v + self.rng.uniform(-12, 12)) for v in c))
                elif dd < 1.35:
                    self.put(x0 + x, y0 + y, shade(self.get(x0 + x, y0 + y), 0.55))
        # cables
        for i in range(3):
            yy = cy - 2 + i * 2
            for x in range(int(cx - rx + 2), int(cx + rx - 2)):
                if 0 <= yy < h and self.rng.random() < 0.8:
                    self.put(x0 + x, y0 + yy, [(180, 40, 40), (40, 80, 180), (200, 170, 40)][i])

    def torn_bottom(self, rect):
        """Bordes inferiores deshilachados (transparentes)."""
        x0, y0, w, h = rect
        for x in range(w):
            depth = self.rng.choice((0, 0, 1, 2, 3))
            for y in range(h - depth, h):
                self.px[x0 + x, y0 + y] = (0, 0, 0, 0)

    def glow_rect(self, rect, color):
        x0, y0, w, h = rect
        for x in range(w):
            for y in range(h):
                d = min(x, y, w - 1 - x, h - 1 - y)
                c = color if d >= 1 or min(w, h) <= 2 else shade(color, 0.8)
                self.gpx[x0 + x, y0 + y] = (c[0], c[1], c[2], 255)

    # --- materiales ---
    def paint(self, c):
        p = self.pal
        f = self.faces(c)
        mat = c.mat
        metal = hexrgb(p.get("metal", "#7d7f80"))
        for name, rect in f.items():
            if rect[2] <= 0 or rect[3] <= 0:
                continue
            big = rect[2] * rect[3]
            if mat in ("suit", "suit_light", "suit_alt", "belly", "beak", "cloth", "gold"):
                base = hexrgb(p[mat])
                lum = {"top": 1.08, "bottom": 0.7, "side_a": 0.9, "side_b": 0.9, "front": 1.0, "back": 0.86}[name]
                self.fill_noise(rect, shade(base, lum), amount=9, streak=6)
                self.edge_shade(rect, 0.8)
                self.blotch(rect, hexrgb(p.get("dirt", "#3b2f22")), max(1, big // 90), 1, 4, 0.45)
                self.scratches(rect, max(1, big // 120), light=True)
                if mat != "beak":
                    self.chips(rect, max(0, big // 160) + (1 if self.rng.random() < 0.4 else 0), shade(metal, 0.9))
                if c.detail == "seam" and name in ("front", "back"):
                    self.seam(rect)
                if c.detail == "tear" and name == "front":
                    self.tear(rect, metal)
                if c.detail == "torn" and name not in ("top", "bottom"):
                    self.torn_bottom(rect)
                if c.detail == "bib" and name == "front":
                    self.confetti(rect)
            elif mat in ("metal", "joint", "hook"):
                base = metal if mat != "joint" else shade(metal, 0.62)
                if mat == "hook":
                    base = shade(metal, 1.15)
                lum = {"top": 1.12, "bottom": 0.72, "side_a": 0.92, "side_b": 0.92, "front": 1.0, "back": 0.85}[name]
                self.fill_noise(rect, shade(base, lum), amount=7)
                # cepillado horizontal
                x0, y0, w, h = rect
                for y in range(0, h, 3):
                    for x in range(w):
                        if self.rng.random() < 0.5:
                            self.put(x0 + x, y0 + y, shade(self.get(x0 + x, y0 + y), 1.08))
                self.edge_shade(rect, 0.7)
                self.blotch(rect, hexrgb("#8a4a1c"), max(1, big // 70), 1, 3, 0.6)   # óxido
                self.blotch(rect, hexrgb("#5a2e12"), max(0, big // 140), 1, 2, 0.5)
                if mat == "metal":
                    self.bolts(rect)
            elif mat == "black":
                base = hexrgb(p.get("black", "#161616"))
                self.fill_noise(rect, base, amount=5)
                self.edge_shade(rect, 1.6)
                self.scratches(rect, max(0, big // 150), light=True)
            elif mat == "red":
                base = hexrgb(p.get("red", "#a01a24"))
                self.fill_noise(rect, base, amount=8)
                self.edge_shade(rect, 0.7)
                self.blotch(rect, hexrgb("#3b2f22"), 1, 1, 2, 0.4)
            elif mat == "teeth":
                base = hexrgb("#d8d2bc")
                self.fill_noise(rect, base, amount=8)
                x0, y0, w, h = rect
                for x in range(SCALE * 1, w, SCALE * 1):
                    for y in range(h):
                        self.put(x0 + x, y0 + y, shade(self.get(x0 + x, y0 + y), 0.55))
                self.blotch(rect, hexrgb("#9a8a50"), max(1, big // 40), 1, 2, 0.5)
            elif mat == "eye":
                base = hexrgb(p.get("eye", "#e8e8e0"))
                self.fill_noise(rect, base, amount=6)
                self.edge_shade(rect, 0.75)
            elif mat == "pupil":
                col = hexrgb(p["pupil"])
                self.fill_noise(rect, col, amount=10)
                if name == "front":
                    self.glow_rect(rect, col)
            elif mat == "nose":
                base = hexrgb(p.get("nose", "#1d1a18"))
                self.fill_noise(rect, base, amount=6)
                x0, y0, w, h = rect
                if w > 2 and h > 2:
                    self.put(x0 + 1, y0 + 1, shade(base, 3.0))
            elif mat == "pink":
                base = hexrgb(p.get("pink", "#e07aa8"))
                self.fill_noise(rect, base, amount=8)
                self.edge_shade(rect, 0.8)
                self.blotch(rect, hexrgb("#ffffff"), 1, 1, 1, 0.6)
            elif mat == "led":
                col = hexrgb(p.get("led", "#ff3030"))
                self.fill_noise(rect, col, amount=5)
                self.glow_rect(rect, col)
            else:
                raise ValueError(mat)

    def confetti(self, rect):
        x0, y0, w, h = rect
        colors = [(220, 60, 60), (60, 120, 220), (60, 180, 80), (230, 180, 40), (180, 80, 200)]
        for _ in range(max(4, w * h // 18)):
            x = self.rng.randrange(1, max(2, w - 2))
            y = self.rng.randrange(1, max(2, h - 2))
            c = self.rng.choice(colors)
            self.put(x0 + x, y0 + y, c)
            self.put(x0 + x + 1, y0 + y, shade(c, 0.85))

    def save(self, path, glow_path):
        self.img.save(path)
        self.glow.save(glow_path)


# ----------------------------------------------------------------------------
# Esqueleto humanoide común
# ----------------------------------------------------------------------------

def humanoid(m, opt):
    """Construye el cuerpo base. opt controla proporciones y materiales."""
    leg_mat = opt.get("leg_mat", "suit")
    shin_mat = opt.get("shin_mat", "suit")
    chest_w = opt.get("chest_w", 13)
    hip_w = opt.get("hip_w", 11)
    hw = chest_w // 2

    m.bone("root", None, [0, 0, 0])
    m.bone("waist", "root", [0, 16, 0])
    m.bone("torso", "waist", [0, 19, 0])
    m.bone("neck", "torso", [0, 31, 0])
    m.bone("head", "neck", [0, 33, -1])
    m.bone("jaw", "head", [0, 33, -3])

    # Piernas
    for side, sx in (("right", -1), ("left", 1)):
        leg = m.bone(side + "_leg", "waist", [sx * 3, 16, 0])
        shin = m.bone(side + "_shin", leg, [sx * 3, 10, 0])
        x_out = -5 if sx < 0 else 1
        m.cube(leg, [x_out, 10, -2], [4, 6, 4], leg_mat, detail=opt.get("thigh_detail"))
        m.cube(leg, [x_out + 0.5, 8.5, -1.5], [3, 2, 3], "joint")                    # rodilla
        if shin_mat == "metal":
            m.cube(shin, [x_out + 1, 3, -1], [2, 6, 2], "metal")                      # endoesqueleto expuesto
            m.cube(shin, [x_out + 0.5, 6, -1.5], [3, 1, 3], "joint")
        else:
            m.cube(shin, [x_out + 0.5, 3, -2], [3, 6, 4], shin_mat)
        m.cube(shin, [x_out + 1, 2, -1], [2, 1, 2], "joint")                          # tobillo
        m.cube(shin, [x_out, 0, -4.5], [4, 2, 6], opt.get("foot_mat", "suit"))       # pie
        m.cube(shin, [x_out + 0.5, 0, -5.5], [3, 1, 1], "joint")                     # dedos mecánicos

    # Pelvis y columna visible
    m.cube("waist", [-hip_w / 2, 14, -3.5], [hip_w, 4, 7], opt.get("pelvis_mat", "suit"), detail=opt.get("pelvis_detail"))
    m.cube("waist", [-1.5, 18, -1.5], [3, 2, 3], "metal")

    # Torso
    m.cube("torso", [-chest_w / 2, 19.5, -4.5], [chest_w, 11, 9], "suit", detail=opt.get("chest_detail", "seam"))
    m.cube("torso", [-hw + 1.5, 21, -5], [chest_w - 4, 7, 1], opt.get("belly_mat", "belly"), detail=opt.get("belly_detail"))
    m.cube("torso", [-hw - 1, 27.5, -2], [2, 2, 4], "joint")                           # hombros
    m.cube("torso", [hw - 1, 27.5, -2], [2, 2, 4], "joint")
    m.cube("torso", [-2, 30.5, -2], [4, 1, 4], "metal")                               # placa del cuello

    # Cuello (endoesqueleto)
    m.cube("neck", [-1, 31, -1], [2, 2, 2], "metal")

    # Brazos
    for side, sx in (("right", -1), ("left", 1)):
        arm = m.bone(side + "_arm", "torso", [sx * (hw + 1.5), 29, 0])
        fore = m.bone(side + "_forearm", arm, [sx * (hw + 1.5), 22, 0])
        x_out = -(hw + 3) if sx < 0 else hw
        m.cube(arm, [x_out, 22.5, -2], [3, 6, 4], "suit")
        m.cube(arm, [x_out + 0.5, 21, -1.5], [2, 2, 3], "joint")                       # codo
        m.cube(fore, [x_out, 15, -2], [3, 6, 4], "suit")
        m.cube(fore, [x_out + 0.5, 14, -1.5], [2, 1, 3], "joint")                      # muñeca
        if not (opt.get("hook") and sx < 0):
            m.cube(fore, [x_out - 0.5, 11, -2.5], [4, 3, 5], opt.get("hand_mat", "suit_light"))
            for i in range(3):
                m.cube(fore, [x_out - 0.5 + (0 if sx < 0 else 3), 9.5, -2.5 + i * 1.8], [1, 2, 1], "joint")

    return hw


def head_common(m, opt):
    sk = opt.get("skull", [10, 8, 9])
    w, h, d = sk
    m.cube("head", [-w / 2, 33, -5], [w, h, d], "suit", detail=opt.get("skull_detail"))
    # ojos
    ey = opt.get("eye_y", 37)
    ex = opt.get("eye_x", 3.5)
    eye_mat = opt.get("eye_mat", "eye")
    m.cube("head", [-ex, ey, -5.4], [2, 2, 1], eye_mat)
    m.cube("head", [ex - 2, ey, -5.4], [2, 2, 1], eye_mat)
    m.cube("head", [-ex + 0.5, ey + 0.5, -5.65], [1, 1, 1], "pupil")
    if not opt.get("patch"):
        m.cube("head", [ex - 1.5, ey + 0.5, -5.65], [1, 1, 1], "pupil")
    # cejas / párpados
    m.cube("head", [-ex - 0.5, ey + 2.2, -5.5], [3, 1, 1], opt.get("brow_mat", "black"), rotation=[0, 0, -8], pivot=[-ex + 1, ey + 2.7, -5])
    m.cube("head", [ex - 2.5, ey + 2.2, -5.5], [3, 1, 1], opt.get("brow_mat", "black"), rotation=[0, 0, 8], pivot=[ex - 1, ey + 2.7, -5])
    # placa metálica trasera (acceso de mantenimiento)
    m.cube("head", [-3, 35, 3.6], [6, 4, 1], "metal")


def muzzle_common(m, opt):
    mw = opt.get("muzzle_w", 8)
    ml = opt.get("muzzle_l", 4)
    m.cube("head", [-mw / 2, 33, -5 - ml + 1], [mw, 3, ml], opt.get("muzzle_mat", "suit_light"))
    m.cube("head", [-1.5, 35, -5 - ml + 0.4], [3, 2, 1], "nose")
    # dientes superiores
    m.cube("head", [-mw / 2 + 0.5, 32, -5 - ml + 0.6], [mw - 1, 1, 1], "teeth")
    # mandíbula articulada
    m.cube("jaw", [-mw / 2 + 0.5, 30.5, -5 - ml + 1.5], [mw - 1, 2, ml + 1], opt.get("jaw_mat", "suit_light"))
    m.cube("jaw", [-mw / 2 + 1, 32.4, -5 - ml + 1.7], [mw - 2, 1, 1], "teeth")
    m.cube("jaw", [-1, 30.5, -1], [2, 2, 2], "joint")


# ----------------------------------------------------------------------------
# Personajes
# ----------------------------------------------------------------------------

def build_bonnie():
    m = Model("bonnie")
    humanoid(m, {"chest_w": 13, "hip_w": 11})
    head_common(m, {"eye_y": 37})
    muzzle_common(m, {"muzzle_w": 8, "muzzle_l": 4})
    # orejas largas (una doblada)
    m.bone("right_ear", "head", [-2.5, 41, -0.5])
    m.bone("left_ear", "head", [2.5, 41, -0.5])
    m.bone("left_ear_tip", "left_ear", [2.5, 46, -0.5], rotation=[-35, 0, 0])
    m.cube("right_ear", [-4, 41, -1.5], [3, 9, 2], "suit", rotation=[0, 0, 8], pivot=[-2.5, 41, -0.5])
    m.cube("right_ear", [-3.5, 42, -1.8], [2, 7, 1], "pink", rotation=[0, 0, 8], pivot=[-2.5, 41, -0.5])
    m.cube("left_ear", [1, 41, -1.5], [3, 5, 2], "suit")
    m.cube("left_ear", [1.5, 42, -1.8], [2, 4, 1], "pink")
    m.cube("left_ear_tip", [1, 46, -1.5], [3, 4, 2], "suit")
    m.cube("left_ear_tip", [1.5, 46, -1.8], [2, 3, 1], "pink")
    m.cube("head", [-3, 41, -1], [6, 1, 2], "joint")                         # base metálica de las orejas
    # pajarita roja
    m.cube("torso", [-3, 29, -5.6], [2, 2, 1], "red")
    m.cube("torso", [1, 29, -5.6], [2, 2, 1], "red")
    m.cube("torso", [-1, 29.5, -5.8], [2, 1, 1], "red")
    pal = {"suit": "#57449a", "suit_light": "#9b8fd3", "belly": "#a99ee0", "pupil": "#ff2a5a", "red": "#b3132a",
           "pink": "#d98ac0", "metal": "#7c7f82", "dirt": "#2c2433", "nose": "#a8142c"}
    return m, pal


def build_chica():
    m = Model("chica")
    humanoid(m, {"chest_w": 13, "hip_w": 12, "belly_mat": "suit_alt", "belly_detail": "bib"})
    head_common(m, {"eye_y": 37, "skull": [10, 8, 9], "brow_mat": "black"})
    # pico
    m.cube("head", [-4, 33.5, -9.5], [8, 3, 5], "beak")
    m.cube("head", [-3.5, 33, -9.2], [7, 1, 1], "teeth")
    m.cube("jaw", [-3.5, 31, -9], [7, 2, 5], "beak")
    m.cube("jaw", [-3, 32.6, -8.8], [6, 1, 1], "teeth")
    m.cube("jaw", [-1, 31, -1], [2, 2, 2], "joint")
    # mejillas
    m.cube("head", [-5.5, 33.5, -4], [1, 3, 4], "suit")
    m.cube("head", [4.5, 33.5, -4], [1, 3, 4], "suit")
    # plumas en la cabeza
    m.cube("head", [-1, 41, -2], [1, 3, 1], "suit", rotation=[0, 0, -15], pivot=[-0.5, 41, -1.5])
    m.cube("head", [0, 41, -1], [1, 4, 1], "suit", rotation=[0, 0, 10], pivot=[0.5, 41, -0.5])
    m.cube("head", [-0.5, 41, 0.5], [1, 2, 1], "suit")
    # magdalena en la mano izquierda
    m.bone("cupcake", "left_forearm", [9, 11, -3])
    m.cube("cupcake", [8, 8, -5], [3, 2, 3], "pink")
    m.cube("cupcake", [8.5, 10, -4.5], [2, 1, 2], "pink")
    m.cube("cupcake", [9, 11, -4], [1, 1, 1], "led")
    pal = {"suit": "#e3bd2f", "suit_light": "#f3dc84", "belly": "#f4e3a0", "suit_alt": "#efefe8", "beak": "#ef8a22",
           "pupil": "#b89cff", "metal": "#7b7c7a", "dirt": "#4a3b1a", "pink": "#e8849c", "led": "#ffe36a"}
    return m, pal


def build_foxy():
    m = Model("foxy")
    humanoid(m, {"chest_w": 12, "hip_w": 10, "leg_mat": "cloth", "shin_mat": "metal", "thigh_detail": "torn",
                 "pelvis_mat": "cloth", "pelvis_detail": "torn", "chest_detail": "seam", "belly_detail": "tear", "hook": True, "foot_mat": "suit"})
    head_common(m, {"eye_y": 37, "patch": True, "skull": [10, 8, 9]})
    muzzle_common(m, {"muzzle_w": 6, "muzzle_l": 6, "muzzle_mat": "suit_light"})
    # dientes afilados extra
    m.cube("head", [-2.5, 31.5, -10.4], [1, 1, 1], "teeth")
    m.cube("head", [1.5, 31.5, -10.4], [1, 1, 1], "teeth")
    # parche
    m.cube("head", [1, 36.5, -5.75], [3, 3, 1], "black")
    m.cube("head", [-5.5, 39.6, -5.5], [11, 1, 10], "black", inflate=0.05)
    # orejas puntiagudas
    m.cube("head", [-5, 41, -1.5], [3, 4, 2], "suit", rotation=[0, 0, 12], pivot=[-3.5, 41, -0.5])
    m.cube("head", [2, 41, -1.5], [3, 4, 2], "suit", rotation=[0, 0, -12], pivot=[3.5, 41, -0.5])
    m.cube("head", [-4.5, 41.5, -1.8], [2, 2, 1], "suit_light", rotation=[0, 0, 12], pivot=[-3.5, 41, -0.5])
    m.cube("head", [2.5, 41.5, -1.8], [2, 2, 1], "suit_light", rotation=[0, 0, -12], pivot=[3.5, 41, -0.5])
    # garfio en el antebrazo derecho
    m.bone("hook", "right_forearm", [-7.5, 14, 0])
    m.cube("hook", [-8.5, 12, -1.5], [2, 2, 3], "joint")
    m.cube("hook", [-8, 8, -0.5], [1, 4, 1], "hook")
    m.cube("hook", [-8, 7, -3.5], [1, 1, 4], "hook")
    m.cube("hook", [-8, 8, -3.5], [1, 2, 1], "hook")
    # cola desgastada
    m.bone("tail", "waist", [0, 15, 3.5])
    m.cube("tail", [-1, 13, 3.5], [2, 2, 4], "suit", rotation=[-30, 0, 0], pivot=[0, 15, 3.5])
    pal = {"suit": "#a63c26", "suit_light": "#d6a07a", "belly": "#cf9a72", "cloth": "#5e4128", "pupil": "#ffd34c",
           "metal": "#77797b", "dirt": "#3a2014", "black": "#141414", "nose": "#1b1514"}
    return m, pal


def build_freddy(gold=False):
    ident = "golden_freddy" if gold else "freddy"
    m = Model(ident)
    humanoid(m, {"chest_w": 14, "hip_w": 12})
    head_common(m, {"eye_y": 37, "skull": [11, 8, 9], "eye_mat": "black" if gold else "eye"})
    muzzle_common(m, {"muzzle_w": 9, "muzzle_l": 4})
    # orejas redondas
    m.cube("head", [-6, 40, -1], [3, 3, 2], "suit")
    m.cube("head", [3, 40, -1], [3, 3, 2], "suit")
    m.cube("head", [-5.5, 40.5, -1.3], [2, 2, 1], "suit_light")
    m.cube("head", [3.5, 40.5, -1.3], [2, 2, 1], "suit_light")
    # sombrero de copa
    m.bone("hat", "head", [0, 41, -0.5])
    m.cube("hat", [-3, 41, -3.5], [6, 1, 6], "black")
    m.cube("hat", [-2, 42, -2.5], [4, 4, 4], "black")
    m.cube("hat", [-2, 42.5, -2.6], [4, 1, 1], "red" if not gold else "black")
    # pajarita
    m.cube("torso", [-3, 29, -5.6], [2, 2, 1], "black")
    m.cube("torso", [1, 29, -5.6], [2, 2, 1], "black")
    m.cube("torso", [-1, 29.5, -5.8], [2, 1, 1], "black")
    # botones del pecho
    m.cube("torso", [-0.5, 26, -5.4], [1, 1, 1], "black")
    m.cube("torso", [-0.5, 24, -5.4], [1, 1, 1], "black")
    if not gold:
        # micrófono en la mano derecha
        m.bone("microphone", "right_forearm", [-9, 11, -2])
        m.cube("microphone", [-9.5, 8, -4.5], [1, 4, 1], "black")
        m.cube("microphone", [-10, 12, -5], [2, 2, 2], "metal")
        pal = {"suit": "#6b4226", "suit_light": "#c79c74", "belly": "#c9a27a", "pupil": "#78d4ff", "metal": "#7d7f80",
               "dirt": "#2a1a10", "black": "#151313", "red": "#7a1420", "nose": "#141010"}
    else:
        pal = {"suit": "#c39a22", "suit_light": "#e2c163", "belly": "#d9b84a", "pupil": "#f2f2f2", "metal": "#6c6a62",
               "dirt": "#3b2a08", "black": "#0c0b0a", "red": "#0c0b0a", "nose": "#140f08", "eye": "#0b0b0b"}
    return m, pal


# ----------------------------------------------------------------------------
# Animaciones
# ----------------------------------------------------------------------------

def kf(times_values):
    return {("%.4f" % t).rstrip("0").rstrip(".") if t else "0.0": [round(v[0], 2), round(v[1], 2), round(v[2], 2)]
            for t, v in times_values}


def cycle(length, samples, fn):
    """Muestra fn(fase 0..1) -> (x,y,z) en 'samples' puntos de un ciclo."""
    out = []
    for i in range(samples + 1):
        t = length * i / samples
        out.append((t, fn(i / samples)))
    return kf(out)


def sin(p, k=1.0, off=0.0):
    return math.sin((p * k + off) * 2 * math.pi)


def animations(ident, extras):
    A = {}
    S = 12  # muestras por ciclo
    # --- reposo ---
    idle_len = 3.0
    idle = {
        "torso": {"rotation": cycle(idle_len, S, lambda p: (1.5 * sin(p), 0, 0.8 * sin(p, 1, 0.25)))},
        "head": {"rotation": cycle(idle_len, S, lambda p: (2 * sin(p, 1, 0.1), 6 * sin(p, 0.5), 3 * sin(p, 1, 0.3)))},
        "jaw": {"rotation": kf([(0, (0, 0, 0)), (2.2, (0, 0, 0)), (2.35, (8, 0, 0)), (2.5, (0, 0, 0)), (3.0, (0, 0, 0))])},
        "right_arm": {"rotation": cycle(idle_len, S, lambda p: (-3 + 2 * sin(p), 0, 3))},
        "left_arm": {"rotation": cycle(idle_len, S, lambda p: (-3 - 2 * sin(p), 0, -3))},
        "right_forearm": {"rotation": cycle(idle_len, S, lambda p: (-8 + 2 * sin(p, 1, 0.2), 0, 0))},
        "left_forearm": {"rotation": cycle(idle_len, S, lambda p: (-8 - 2 * sin(p, 1, 0.2), 0, 0))},
    }
    if ident == "golden_freddy":
        idle = {
            "torso": {"rotation": cycle(idle_len, S, lambda p: (24 + 1.0 * sin(p), 0, 6))},
            "head": {"rotation": cycle(idle_len, S, lambda p: (28, 0, 22 + 2 * sin(p, 0.5)))},
            "jaw": {"rotation": cycle(idle_len, S, lambda p: (22 + 3 * sin(p), 0, 0))},
            "right_arm": {"rotation": kf([(0, (-12, 0, 6)), (3.0, (-12, 0, 6))])},
            "left_arm": {"rotation": kf([(0, (-12, 0, -6)), (3.0, (-12, 0, -6))])},
            "right_forearm": {"rotation": kf([(0, (-5, 0, 0)), (3.0, (-5, 0, 0))])},
            "left_forearm": {"rotation": kf([(0, (-5, 0, 0)), (3.0, (-5, 0, 0))])},
        }
    A["idle"] = {"loop": True, "animation_length": idle_len, "bones": idle}

    # --- caminar: zancada mecánica y rígida ---
    wl = 1.2
    A["walk"] = {"loop": True, "animation_length": wl, "bones": {
        "right_leg": {"rotation": cycle(wl, S, lambda p: (-24 * sin(p), 0, 0))},
        "left_leg": {"rotation": cycle(wl, S, lambda p: (24 * sin(p), 0, 0))},
        "right_shin": {"rotation": cycle(wl, S, lambda p: (max(0, 28 * sin(p, 1, 0.25)), 0, 0))},
        "left_shin": {"rotation": cycle(wl, S, lambda p: (max(0, 28 * sin(p, 1, 0.75)), 0, 0))},
        "right_arm": {"rotation": cycle(wl, S, lambda p: (18 * sin(p), 0, 3))},
        "left_arm": {"rotation": cycle(wl, S, lambda p: (-18 * sin(p), 0, -3))},
        "right_forearm": {"rotation": cycle(wl, S, lambda p: (-12 + 6 * sin(p), 0, 0))},
        "left_forearm": {"rotation": cycle(wl, S, lambda p: (-12 - 6 * sin(p), 0, 0))},
        "torso": {"rotation": cycle(wl, S, lambda p: (3, 4 * sin(p), 2 * sin(p))),
                  "position": cycle(wl, S, lambda p: (0, 0.4 * abs(sin(p)), 0))},
        "head": {"rotation": cycle(wl, S, lambda p: (2 * sin(p, 2), -3 * sin(p), 0))},
        "waist": {"position": cycle(wl, S, lambda p: (0, -0.5 * abs(sin(p, 1, 0.25)), 0))},
    }}

    # --- correr: inclinado, brazos hacia delante, zancada amplia ---
    rl = 0.6 if ident != "foxy" else 0.45
    A["run"] = {"loop": True, "animation_length": rl, "bones": {
        "right_leg": {"rotation": cycle(rl, S, lambda p: (-48 * sin(p), 0, 0))},
        "left_leg": {"rotation": cycle(rl, S, lambda p: (48 * sin(p), 0, 0))},
        "right_shin": {"rotation": cycle(rl, S, lambda p: (max(0, 55 * sin(p, 1, 0.25)), 0, 0))},
        "left_shin": {"rotation": cycle(rl, S, lambda p: (max(0, 55 * sin(p, 1, 0.75)), 0, 0))},
        "right_arm": {"rotation": cycle(rl, S, lambda p: (-35 + 40 * sin(p), 0, 8))},
        "left_arm": {"rotation": cycle(rl, S, lambda p: (-35 - 40 * sin(p), 0, -8))},
        "right_forearm": {"rotation": cycle(rl, S, lambda p: (-40, 0, 0))},
        "left_forearm": {"rotation": cycle(rl, S, lambda p: (-40, 0, 0))},
        "torso": {"rotation": cycle(rl, S, lambda p: (14 + 2 * sin(p, 2), 6 * sin(p), 0))},
        "head": {"rotation": cycle(rl, S, lambda p: (-10 + 3 * sin(p, 2), -4 * sin(p), 0))},
        "jaw": {"rotation": cycle(rl, S, lambda p: (12 + 6 * sin(p, 2), 0, 0))},
        "waist": {"position": cycle(rl, S, lambda p: (0, -1 + 1.2 * abs(sin(p)), 0))},
    }}

    # --- mirar alrededor (búsqueda) ---
    ll = 4.0
    A["look"] = {"loop": True, "animation_length": ll, "bones": {
        "head": {"rotation": kf([(0, (0, 0, 0)), (0.6, (4, 55, 0)), (1.4, (4, 55, 6)), (2.0, (-4, 0, 0)), (2.6, (4, -55, 0)),
                                  (3.4, (4, -55, -6)), (4.0, (0, 0, 0))])},
        "torso": {"rotation": kf([(0, (2, 0, 0)), (1.0, (2, 12, 0)), (2.0, (2, 0, 0)), (3.0, (2, -12, 0)), (4.0, (2, 0, 0))])},
        "jaw": {"rotation": kf([(0, (0, 0, 0)), (1.0, (6, 0, 0)), (1.2, (0, 0, 0)), (4.0, (0, 0, 0))])},
        "right_arm": {"rotation": kf([(0, (-5, 0, 4)), (4.0, (-5, 0, 4))])},
        "left_arm": {"rotation": kf([(0, (-5, 0, -4)), (4.0, (-5, 0, -4))])},
    }}

    # --- detectar al jugador: giro brusco, mandíbula abierta ---
    A["detect"] = {"loop": False, "animation_length": 0.8, "bones": {
        "head": {"rotation": kf([(0, (0, 0, 0)), (0.08, (-12, 0, 18)), (0.2, (-6, 0, -6)), (0.35, (-10, 0, 0)), (0.8, (0, 0, 0))])},
        "jaw": {"rotation": kf([(0, (0, 0, 0)), (0.1, (30, 0, 0)), (0.5, (30, 0, 0)), (0.8, (0, 0, 0))])},
        "torso": {"rotation": kf([(0, (0, 0, 0)), (0.12, (-10, 0, 0)), (0.5, (6, 0, 0)), (0.8, (0, 0, 0))])},
        "right_arm": {"rotation": kf([(0, (0, 0, 0)), (0.15, (-30, 0, 12)), (0.8, (0, 0, 0))])},
        "left_arm": {"rotation": kf([(0, (0, 0, 0)), (0.15, (-30, 0, -12)), (0.8, (0, 0, 0))])},
    }}

    # --- atacar: embestida con los brazos hacia delante ---
    A["attack"] = {"loop": False, "animation_length": 0.9, "bones": {
        "right_arm": {"rotation": kf([(0, (0, 0, 0)), (0.2, (-150, 0, 20)), (0.4, (-95, 0, 8)), (0.65, (-95, 0, 8)), (0.9, (0, 0, 0))])},
        "left_arm": {"rotation": kf([(0, (0, 0, 0)), (0.2, (-150, 0, -20)), (0.4, (-95, 0, -8)), (0.65, (-95, 0, -8)), (0.9, (0, 0, 0))])},
        "right_forearm": {"rotation": kf([(0, (0, 0, 0)), (0.2, (-30, 0, 0)), (0.4, (-10, 0, 0)), (0.9, (0, 0, 0))])},
        "left_forearm": {"rotation": kf([(0, (0, 0, 0)), (0.2, (-30, 0, 0)), (0.4, (-10, 0, 0)), (0.9, (0, 0, 0))])},
        "torso": {"rotation": kf([(0, (0, 0, 0)), (0.2, (-12, 0, 0)), (0.4, (22, 0, 0)), (0.65, (22, 0, 0)), (0.9, (0, 0, 0))])},
        "head": {"rotation": kf([(0, (0, 0, 0)), (0.2, (-15, 0, 0)), (0.4, (12, 0, 0)), (0.9, (0, 0, 0))])},
        "jaw": {"rotation": kf([(0, (0, 0, 0)), (0.25, (40, 0, 0)), (0.6, (40, 0, 0)), (0.9, (0, 0, 0))])},
        "waist": {"position": kf([(0, (0, 0, 0)), (0.4, (0, -1, -3)), (0.65, (0, -1, -3)), (0.9, (0, 0, 0))])},
    }}

    # --- recibir daño ---
    A["hurt"] = {"loop": False, "animation_length": 0.5, "bones": {
        "torso": {"rotation": kf([(0, (0, 0, 0)), (0.08, (-16, 0, 6)), (0.25, (4, 0, -3)), (0.5, (0, 0, 0))])},
        "head": {"rotation": kf([(0, (0, 0, 0)), (0.06, (-10, 20, 18)), (0.2, (5, -10, -8)), (0.5, (0, 0, 0))])},
        "jaw": {"rotation": kf([(0, (0, 0, 0)), (0.08, (22, 0, 0)), (0.3, (0, 0, 0)), (0.5, (0, 0, 0))])},
        "right_arm": {"rotation": kf([(0, (0, 0, 0)), (0.1, (12, 0, 25)), (0.5, (0, 0, 0))])},
        "left_arm": {"rotation": kf([(0, (0, 0, 0)), (0.1, (12, 0, -25)), (0.5, (0, 0, 0))])},
    }}

    # --- evento especial (propio de cada animatrónico) ---
    A["special"] = extras
    return {"format_version": "1.8.0",
            "animations": {"animation.%s.%s" % (ident, k): v for k, v in A.items()}}


def special_for(ident):
    if ident == "bonnie":   # orejas que se agitan y cabeza curiosa
        return {"loop": False, "animation_length": 1.6, "bones": {
            "right_ear": {"rotation": kf([(0, (0, 0, 0)), (0.2, (-20, 0, -15)), (0.4, (5, 0, 5)), (0.6, (-20, 0, -15)), (0.9, (0, 0, 0)), (1.6, (0, 0, 0))])},
            "left_ear": {"rotation": kf([(0, (0, 0, 0)), (0.3, (-25, 0, 15)), (0.5, (5, 0, -5)), (0.8, (-15, 0, 10)), (1.1, (0, 0, 0)), (1.6, (0, 0, 0))])},
            "head": {"rotation": kf([(0, (0, 0, 0)), (0.4, (6, 0, 24)), (1.0, (6, 0, 24)), (1.6, (0, 0, 0))])},
            "jaw": {"rotation": kf([(0, (0, 0, 0)), (0.5, (14, 0, 0)), (0.7, (0, 0, 0)), (1.6, (0, 0, 0))])},
        }}
    if ident == "chica":    # castañeteo de pico
        jaw = [(0, (0, 0, 0))]
        t = 0.0
        while t < 1.4:
            jaw += [(t + 0.07, (22, 0, 0)), (t + 0.14, (0, 0, 0))]
            t += 0.14
        jaw.append((1.6, (0, 0, 0)))
        return {"loop": False, "animation_length": 1.6, "bones": {
            "jaw": {"rotation": kf(jaw)},
            "head": {"rotation": kf([(0, (0, 0, 0)), (0.3, (-8, 15, 8)), (0.8, (-8, -15, -8)), (1.3, (-8, 10, 4)), (1.6, (0, 0, 0))])},
            "left_arm": {"rotation": kf([(0, (0, 0, 0)), (0.4, (-50, 0, -8)), (1.2, (-50, 0, -8)), (1.6, (0, 0, 0))])},
        }}
    if ident == "foxy":     # se asoma, levanta el garfio
        return {"loop": False, "animation_length": 1.5, "bones": {
            "right_arm": {"rotation": kf([(0, (0, 0, 0)), (0.3, (-140, 0, 10)), (0.9, (-140, 0, 10)), (1.5, (0, 0, 0))])},
            "right_forearm": {"rotation": kf([(0, (0, 0, 0)), (0.3, (-25, 0, 0)), (0.6, (-5, 0, 0)), (0.9, (-25, 0, 0)), (1.5, (0, 0, 0))])},
            "head": {"rotation": kf([(0, (0, 0, 0)), (0.3, (-12, 0, -20)), (1.0, (-12, 0, -20)), (1.5, (0, 0, 0))])},
            "jaw": {"rotation": kf([(0, (0, 0, 0)), (0.3, (35, 0, 0)), (1.0, (35, 0, 0)), (1.5, (0, 0, 0))])},
            "torso": {"rotation": kf([(0, (0, 0, 0)), (0.3, (10, 0, 0)), (1.0, (10, 0, 0)), (1.5, (0, 0, 0))])},
        }}
    if ident == "freddy":   # risa: mandíbula y sombrero
        jaw = [(0, (0, 0, 0))]
        for i in range(4):
            jaw += [(0.15 + i * 0.3, (18, 0, 0)), (0.3 + i * 0.3, (4, 0, 0))]
        jaw.append((1.6, (0, 0, 0)))
        return {"loop": False, "animation_length": 1.6, "bones": {
            "jaw": {"rotation": kf(jaw)},
            "head": {"rotation": kf([(0, (0, 0, 0)), (0.3, (-14, 0, 0)), (1.2, (-14, 0, 0)), (1.6, (0, 0, 0))])},
            "hat": {"rotation": kf([(0, (0, 0, 0)), (0.3, (-10, 0, 8)), (0.6, (0, 0, 0)), (1.6, (0, 0, 0))])},
            "torso": {"rotation": kf([(0, (0, 0, 0)), (0.4, (-6, 0, 0)), (1.2, (-6, 0, 0)), (1.6, (0, 0, 0))])},
        }}
    # golden freddy: inclina la cabeza y deja caer la mandíbula
    return {"loop": False, "animation_length": 2.0, "bones": {
        "head": {"rotation": kf([(0, (28, 0, 22)), (0.4, (0, 0, -30)), (1.6, (0, 0, -30)), (2.0, (28, 0, 22))])},
        "jaw": {"rotation": kf([(0, (22, 0, 0)), (0.4, (45, 0, 0)), (1.6, (45, 0, 0)), (2.0, (22, 0, 0))])},
    }}


# ----------------------------------------------------------------------------

def main():
    os.makedirs(os.path.join(ROOT, "geo"), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "animations"), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "textures", "entity"), exist_ok=True)
    builders = [build_bonnie, build_chica, build_foxy, build_freddy, lambda: build_freddy(True)]
    for i, b in enumerate(builders):
        m, pal = b()
        m.pack()
        geo_path = os.path.join(ROOT, "geo", m.ident + ".geo.json")
        with open(geo_path, "w", encoding="utf-8") as f:
            json.dump(m.to_geo(), f, indent=1)
        painter = Painter(m.ident, pal, seed=1000 + i * 77)
        for c in m.cubes:
            painter.paint(c)
        painter.save(os.path.join(ROOT, "textures", "entity", m.ident + ".png"),
                     os.path.join(ROOT, "textures", "entity", m.ident + "_glowmask.png"))
        anim = animations(m.ident, special_for(m.ident))
        with open(os.path.join(ROOT, "animations", m.ident + ".animation.json"), "w", encoding="utf-8") as f:
            json.dump(anim, f, indent=1)
        print("%-14s %3d huesos, %3d cubos" % (m.ident, len(m.bones), len(m.cubes)))
    # Final surfaces use the same packed UVs, but a 512px material-aware atlas.
    import rework_materials
    for ident, builder in rework_materials.builders():
        tex, glow = rework_materials.paint_entity(ident, builder)
        tex.save(os.path.join(ROOT, "textures", "entity", ident + ".png"))
        glow.save(os.path.join(ROOT, "textures", "entity", ident + "_glowmask.png"))


if __name__ == "__main__":
    main()
