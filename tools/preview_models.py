#!/usr/bin/env python3
"""
Renderizador de vista previa (sin Minecraft) para los .geo.json de GeckoLib.
Dibuja cada animatrónico texturizado desde tres ángulos en docs/previews/.
Sirve para revisar proporciones, UV y colores rápidamente.

Uso: python tools/preview_models.py
"""
import json
import math
import os

from PIL import Image, ImageDraw

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(BASE, "src", "main", "resources", "assets", "fnaf_night_shift")
OUT = os.path.join(BASE, "docs", "previews")


def rot_matrix(rx, ry, rz):
    # GeckoLib: X e Y invertidos; orden Z * Y * X
    rx, ry, rz = math.radians(-rx), math.radians(-ry), math.radians(rz)
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mat_mul(Rz, mat_mul(Ry, Rx))


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def transform_point(p, chain):
    """chain: lista de (pivot, matriz) desde el más interno al más externo."""
    for pivot, m in chain:
        q = [p[i] - pivot[i] for i in range(3)]
        q = apply(m, q)
        p = [q[i] + pivot[i] for i in range(3)]
    return p


def faces_of(cube, tex_w):
    ox, oy, oz = cube["origin"]
    w, h, d = cube["size"]
    inf = cube.get("inflate", 0)
    x0, y0, z0 = ox - inf, oy - inf, oz - inf
    x1, y1, z1 = ox + w + inf, oy + h + inf, oz + d + inf
    u, v = cube["uv"]
    W, H, D = math.floor(w), math.floor(h), math.floor(d)
    # (esquinas en orden: arriba-izq, arriba-der, abajo-der, abajo-izq vistas desde fuera), rect UV
    return [
        ([(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)], (u + D, v + D, W, H)),            # norte (frente)
        ([(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)], (u + 2 * D + W, v + D, W, H)),    # sur
        ([(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)], (u, v + D, D, H)),                # este
        ([(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)], (u + D + W, v + D, D, H)),        # oeste
        ([(x1, y1, z1), (x0, y1, z1), (x0, y1, z0), (x1, y1, z0)], (u + D, v, W, D)),                # arriba
        ([(x1, y0, z0), (x0, y0, z0), (x0, y0, z1), (x1, y0, z1)], (u + D + W, v, W, D)),            # abajo
    ]


def render(geo_path, tex_path, yaw, size=260, scale=5.2):
    geo = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    tex = Image.open(tex_path).convert("RGBA")
    tw = geo["description"]["texture_width"]
    s = tex.width / tw
    bones = {b["name"]: b for b in geo["bones"]}

    def chain_for(bone_name):
        chain = []
        b = bones[bone_name]
        while b:
            r = b.get("rotation")
            if r:
                chain.append((b["pivot"], rot_matrix(*r)))
            b = bones.get(b.get("parent")) if b.get("parent") else None
        return chain

    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    polys = []
    for b in geo["bones"]:
        for c in b.get("cubes", []):
            chain = []
            if c.get("rotation"):
                chain.append((c.get("pivot", c["origin"]), rot_matrix(*c["rotation"])))
            chain += chain_for(b["name"])
            for corners, (u, v, w, h) in faces_of(c, tw):
                pts = [transform_point(list(p), chain) for p in corners]
                # vista: girar alrededor de Y y proyectar ortográficamente (la cámara mira hacia +Z)
                view = []
                for (x, y, z) in pts:
                    vx = x * cy - z * sy
                    vz = x * sy + z * cy
                    view.append((vx, y, vz))
                # descartar caras traseras
                ax, ay = view[1][0] - view[0][0], view[1][1] - view[0][1]
                bx, by = view[3][0] - view[0][0], view[3][1] - view[0][1]
                if ax * by - ay * bx < 0:
                    continue
                if w == 0 or h == 0:
                    continue
                steps_u, steps_v = max(1, int(abs(w) * s)), max(1, int(abs(h) * s))
                for i in range(steps_u):
                    for j in range(steps_v):
                        tx = int(u * s + i * (1 if w > 0 else -1))
                        ty = int(v * s + j * (1 if h > 0 else -1))
                        if not (0 <= tx < tex.width and 0 <= ty < tex.height):
                            continue
                        col = tex.getpixel((tx, ty))
                        if col[3] < 128:
                            continue
                        def lerp(a, b, t):
                            return [a[k] + (b[k] - a[k]) * t for k in range(3)]
                        quad = []
                        for (fi, fj) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                            top = lerp(view[0], view[1], fi / steps_u)
                            bot = lerp(view[3], view[2], fi / steps_u)
                            quad.append(lerp(top, bot, fj / steps_v))
                        depth = sum(q[2] for q in quad) / 4
                        polys.append((depth, [(size / 2 - q[0] * scale, size - 12 - q[1] * scale) for q in quad], col[:3]))
    img = Image.new("RGB", (size, size), (34, 36, 40))
    d = ImageDraw.Draw(img)
    for depth, quad, col in sorted(polys, key=lambda p: -p[0]):
        d.polygon(quad, fill=col)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for ident in ("bonnie", "chica", "foxy", "freddy", "golden_freddy"):
        geo = os.path.join(ASSETS, "geo", ident + ".geo.json")
        tex = os.path.join(ASSETS, "textures", "entity", ident + ".png")
        views = [render(geo, tex, yaw) for yaw in (0, 35, 90)]
        sheet = Image.new("RGB", (len(views) * 260, 260))
        for i, v in enumerate(views):
            sheet.paste(v, (i * 260, 0))
        sheet.save(os.path.join(OUT, ident + ".png"))
        print("preview", ident)


if __name__ == "__main__":
    main()
