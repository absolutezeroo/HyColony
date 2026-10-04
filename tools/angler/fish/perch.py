"""Throwaway spike: a European perch on Hytale's Bluegill fish rig, painted in model space with blockpaint's bake."""

import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, r"C:/Users/Ctuto/Desktop/HyColony/tools/blockpaint")

from bake import light_map, texels  # noqa: E402
from fanfin import Fan, fan  # noqa: E402
from models import box_shape, empty_shape, placed, quad_shape, unwrap, walk  # noqa: E402
from shading import graded  # noqa: E402
from vectors import rotate, sub  # noqa: E402

OUT = os.path.dirname(os.path.abspath(__file__))
SIDES = ("front", "back", "left", "right", "top", "bottom")


def ry(deg):
    a = math.radians(deg) / 2
    return (0.0, math.sin(a), 0.0, math.cos(a))


def rx(deg):
    a = math.radians(deg) / 2
    return (math.sin(a), 0.0, 0.0, math.cos(a))


def conj(q):
    return (-q[0], -q[1], -q[2], q[3])


ID = [0]


def part(name, pivot, shape=None, center=None, ori=(0, 0, 0, 1), children=()):
    """A node given in absolute model coordinates: its pivot and its shape's centre (None: the pivot)."""
    return {"name": name, "pivot": pivot, "shape": shape or empty_shape(), "center": center or pivot, "ori": ori,
            "children": list(children)}


def build(p, parent_base=(0, 0, 0), parent_rot=(0, 0, 0, 1)):
    """Hytale node from part p: position counts from the parent's pivot plus its shape offset, turned by the parent."""
    ID[0] += 1
    own_rot = p["ori"]
    position = rotate(conj(parent_rot), sub(p["pivot"], parent_base))
    world_rot = _mul(parent_rot, own_rot)
    offset = rotate(conj(world_rot), sub(p["center"], p["pivot"]))
    shape = p["shape"]
    shape["offset"] = {"x": offset[0], "y": offset[1], "z": offset[2]}
    base = p["center"]
    node = {"id": str(ID[0]), "name": p["name"], "position": dict(zip("xyz", position)),
            "orientation": dict(zip("xyzw", own_rot)), "shape": shape, "children": []}
    node["children"] = [build(c, base, world_rot) for c in p["children"]]
    return node


def _mul(a, b):
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return (aw * bx + ax * bw + ay * bz - az * by, aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw, aw * bw - ax * bx - ay * by - az * bz)


def box(size):
    return box_shape(size, SIDES, shading="flat")


def fin(size):
    return quad_shape(size, shading="flat")


L_EYE, R_EYE = ry(90), ry(-90)
SAGITTAL = ry(-90)  # a +Z quad laid in the fish's mid plane, its u running towards the head
# Fin quads (w, h): as large as Hytale's (its pike's dorsal is 28 x 22 on a 43 long body).
FINS = {"Fin-Top": (20, 13), "Fin-Top2": (14, 10), "L-Fin": (9, 9), "R-Fin": (9, 9), "Fin-Bot": (12, 10),
        "Tail3": (20, 28), "L-Pectoral": (12, 10), "R-Pectoral": (12, 10)}

arm_chain = lambda side, x: part(f"{side}-Forearm", (x, 6, 12), children=[  # noqa: E731
    part(f"{side}-Hand", (x, 3, 12), children=[part(f"{side}-Attachment", (x, 2, 12), ori=rx(90))])])

PERCH = part("Origin", (0, 0, 0), children=[
    part("Pelvis", (0, 14, 0), box((16, 26, 34)), children=[
        part("Back", (0, 28, 6), box((12, 4, 20))),
        part("Fin-Top", (0, 28.5, 6), fin(FINS["Fin-Top"]), center=(0, 35, 6), ori=SAGITTAL),
        part("Fin-Top2", (0, 26.5, -11), fin(FINS["Fin-Top2"]), center=(0, 31.5, -11), ori=SAGITTAL),
        part("L-Fin", (5, 2, 10), fin(FINS["L-Fin"]), center=(6.2, -2.5, 5.65), ori=ry(-105)),
        part("R-Fin", (-5, 2, 10), fin(FINS["R-Fin"]), center=(-6.2, -2.5, 5.65), ori=ry(-75)),
        part("Head", (0, 13, 17), children=[
            part("Head2", (0, 13, 17), box((14, 20, 12)), center=(0, 13, 23), children=[
                part("Snout", (0, 10, 29), box((11, 11, 6)), center=(0, 10, 32)),
                part("Jaw", (0, 6, 22), box((12, 3, 13)), center=(0, 5, 29)),
                part("L-Eye", (7.25, 16, 25), fin((7, 7)), ori=L_EYE),
                part("R-Eye", (-7.25, 16, 25), fin((7, 7)), ori=R_EYE),
            ]),
        ]),
        part("Tail", (0, 14, -17), box((12, 20, 12)), center=(0, 14, -22), children=[
            part("Fin-Bot", (0, 4.5, -18), fin(FINS["Fin-Bot"]), center=(0, -0.5, -21), ori=SAGITTAL),
            part("Tail2", (0, 14, -27), box((7, 12, 9)), center=(0, 14, -31), children=[
                part("Tail3", (0, 14, -34), fin(FINS["Tail3"]), center=(0, 14, -44), ori=ry(90)),
            ]),
        ]),
        part("Belly", (0, 4, 12), children=[part("Chest", (0, 8, 12), children=[
            part("L-Arm", (8.25, 10, 12), children=[
                part("L-Pectoral", (8.25, 10, 12), fin(FINS["L-Pectoral"]), center=(10.3, 8, 6.4), ori=ry(-110)),
                arm_chain("L", 9)]),
            part("R-Arm", (-8.25, 10, 12), children=[
                part("R-Pectoral", (-8.25, 10, 12), fin(FINS["R-Pectoral"]), center=(-10.3, 8, 6.4), ori=ry(-70)),
                arm_chain("R", -9)]),
        ])]),
    ]),
])


# Palette (Perca fluviatilis): never pure black or white, cool shadows.
BACK = (62, 80, 44)
FLANK = (150, 158, 66)
BELLY = (226, 218, 184)
BAR = (44, 58, 34)
ORANGE = (222, 96, 42)
SPINY = (128, 134, 96)
SOFT = (156, 150, 78)
IRIS, PUPIL = (226, 160, 46), (26, 28, 34)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(x + (y - x) * t for x, y in zip(a, b))


def noise(x, y, z, cell=3.0):
    def h(i, j, k):
        n = math.sin(i * 127.1 + j * 311.7 + k * 74.7) * 43758.5453
        return n - math.floor(n)
    fx, fy, fz = x / cell, y / cell, z / cell
    i, j, k = math.floor(fx), math.floor(fy), math.floor(fz)
    u, v, w = fx - i, fy - j, fz - k
    total = 0.0
    for di in (0, 1):
        for dj in (0, 1):
            for dk in (0, 1):
                weight = (u if di else 1 - u) * (v if dj else 1 - v) * (w if dk else 1 - w)
                total += weight * h(i + di, j + dj, k + dk)
    return total


# Bars: (centre z, half width, how low they reach as a fraction of depth from the back).
BARS = [(13.5, 1.8, 0.45), (6.5, 2.3, 0.62), (-0.5, 2.3, 0.66), (-7.5, 2.1, 0.62), (-14.5, 2.0, 0.6),
        (-21.5, 1.8, 0.55), (-28.5, 1.5, 0.5)]


def body_colour(p, name, normal=None):
    x, y, z = p
    top = 30 if z > -4 and z < 16 else 27
    height = (y - 1) / (top - 1)  # 0 belly .. 1 back
    if name in ("Tail", "Tail2"):
        height = (y - 4) / 20 if name == "Tail" else (y - 8) / 12
    if name in ("Head2", "Snout", "Jaw"):
        height = (y - 3) / 20
    c = mix(BELLY, FLANK, (height - 0.18) / 0.32)
    c = mix(c, BACK, (height - 0.62) / 0.33)
    # brush-like value variation
    n = noise(x, y, z, 2.6) - 0.5
    c = tuple(v * (1 + 0.10 * n) for v in c)
    if name in ("Pelvis", "Back", "Tail", "Tail2"):
        for bz, hw, reach in BARS:
            wob = 0.7 * math.sin(y * 0.55 + bz)
            d = abs(z - bz - wob)
            if d < hw + 0.8 and height > 1 - reach:
                fade = min(1.0, (height - (1 - reach)) / 0.18)
                edge = max(0.0, min(1.0, hw + 0.8 - d))
                c = mix(c, BAR, 0.82 * fade * edge * (0.85 + 0.3 * noise(x, y, z, 1.7)))
    if name in ("Head2",) and abs(x) > 6.9:
        # gill cover: a dark crescent behind the cheek, and its small spot
        gz, gy = 19.0, 12.0
        r = math.hypot((z - gz) * 0.9, (y - gy) * 0.55)
        if 4.6 < r < 6.0 and z > gz:
            c = mix(c, (60, 70, 42), 0.55)
        if math.hypot(z - 18.5, y - 17) < 1.3:
            c = mix(c, BAR, 0.7)
    if name == "Jaw" and y < 5:
        c = mix(c, BELLY, 0.6)
    if name in ("Snout",) and y > 13:
        c = mix(c, BACK, 0.4)
    return c


def blotch(c, x, y, n, along):
    """The perch's black blotch at the rear of its first dorsal fin."""
    return mix(c, (34, 36, 32), 0.85 * max(0.0, 1 - n / 0.3) * min(1.0, along * 3))


def orange_lobe(c, x, y, n, along):
    """The perch's orange lower tail lobe."""
    return mix(c, ORANGE, 0.8 * max(0.0, (y - 15) / 9))


ORANGE_TONES = ((150, 52, 24), ORANGE, (246, 172, 112))
OLIVE_TONES = ((80, 82, 42), (150, 146, 78), (214, 206, 150))

# Mid-plane fins: s 0 back .. 1 head, t 0 top .. 1 bottom; side fins: s 1 at the base (front); Tail3 bound like
# Bluegill's: s 0 at the base. Angles: 0 up the texture, -90 to its left (the back), 180 down (fanfin.Fan).
FANS = {
    "Fin-Top": Fan((11, 24), -50, 20, lambda n: 14 + 10 * math.sin(math.pi * (0.1 + 0.75 * n)), 11, 7, 3.5,
                   (58, 64, 44), (128, 134, 96), (210, 206, 166), extra=blotch, sharp=0.35),
    "Fin-Top2": Fan((6, 17), -45, 30, lambda n: 11 + 6 * math.sin(math.pi * (0.25 + 0.6 * n)), 7, 5, 1.4,
                    *OLIVE_TONES, bend=0.05),
    "Fin-Bot": Fan((12, -12), -125, -160, lambda n: 22 + n, 12, 5, 1.4, *ORANGE_TONES),
    "L-Fin": Fan((10, -1), -100, -150, lambda n: 10 - 2.5 * n, 1, 4, 1.3, *ORANGE_TONES),
    "R-Fin": Fan((10, -1), -100, -150, lambda n: 10 - 2.5 * n, 1, 4, 1.3, *ORANGE_TONES),
    "Tail3": Fan((-1, 14), 50, 130, lambda n: 14 + 9 * abs(2 * n - 1) ** 0.75, 1, 6, 1.4, *OLIVE_TONES,
                 extra=orange_lobe),
    "L-Pectoral": Fan((12.5, 3), -58, -130, lambda n: 11 + 1.5 * math.sin(math.pi * n), 1, 5, 1.3,
                      (120, 100, 50), (196, 164, 96), (232, 214, 160)),
    "R-Pectoral": Fan((12.5, 3), -58, -130, lambda n: 11 + 1.5 * math.sin(math.pi * n), 1, 5, 1.3,
                      (120, 100, 50), (196, 164, 96), (232, 214, 160)),
}


def fin_paint(name, s, t):
    """(colour, alpha) of a fin or eye texel at (s, t) in 0..1 of its quad; None where it is transparent."""
    if name in FANS:
        return fan(FANS[name], s, t, *FINS[name])
    return paint_eye(name, s, t)


def paint_eye(name, s, t):
    """(colour, alpha) of an eye texel: a golden iris ring around the pupil, a small highlight."""
    if name in ("L-Eye", "R-Eye"):
        r = math.hypot(s - 0.5, t - 0.5)
        if r > 0.5:
            return None
        if math.hypot(s - 0.36, t - 0.34) < 0.11:
            return (238, 236, 220), 255
        if r < 0.24:
            return PUPIL, 255
        if r < 0.42:
            return mix(IRIS, (196, 110, 30), (r - 0.24) / 0.18), 255
        return (70, 74, 50), 255
    raise KeyError(name)


def main():
    render("Perch", PERCH, body_colour, fin_paint)


def render(name, tree, body_colour, fin_paint):
    """Builds tree, paints it (body_colour(point, node) on boxes, fin_paint(node, s, t) on quads) and writes
    <name>.blockymodel, <name>.png and size.json."""
    ID[0] = 0
    root = build(tree)
    nodes = [root]
    size = unwrap(nodes, widths=(64, 96, 128, 160))
    values = light_map(nodes)
    image = Image.new("RGBA", size, (0, 0, 0, 0))
    px = image.load()
    for n, position, rotation in placed(nodes):
        shape = n["shape"]
        if shape["type"] not in ("box", "quad"):
            continue
        for side, face in shape["textureLayout"].items():
            u0, v0 = int(face["offset"]["x"]), int(face["offset"]["y"])
            w, h = int(shape["settings"]["size"]["x"]), int(shape["settings"]["size"]["y"])
            for (i, j), point, normal, _ in texels(shape, side, position, rotation):
                texel = (u0 + i, v0 + j)
                if shape["type"] == "quad":
                    painted = fin_paint(n["name"], (i + 0.5) / w, (j + 0.5) / h)
                    if painted is None:
                        continue
                    colour, alpha = painted
                else:
                    colour, alpha = body_colour(point, n["name"], normal), 255
                rgba = (*[int(max(8, min(247, c))) for c in colour], alpha)
                px[texel] = graded(rgba, values.get(texel, 1.0), "legacy") if shape["type"] == "box" else rgba
    model = {"nodes": nodes, "lod": "auto"}
    with open(os.path.join(OUT, f"{name}.blockymodel"), "w", encoding="utf-8") as f:
        json.dump(model, f, indent=2)
    image.save(os.path.join(OUT, f"{name}.png"))
    with open(os.path.join(OUT, "size.json"), "w") as f:
        json.dump(size, f)
    image.resize((size[0] * 4, size[1] * 4), Image.NEAREST).save(os.path.join(OUT, f"{name}_x4.png"))
    print("texture", size, "nodes", sum(1 for _ in walk(nodes)))


if __name__ == "__main__":
    main()
