"""Throwaway spike: a northern pike (Esox lucius) on Hytale's Bluegill fish rig, bind orientations as Bluegill's."""

import math

from fanfin import Fan, fan
from perch import box, fin, mix, noise, part, render, rx, ry
from vectors import add, rotate

SAGITTAL = ry(-90)  # mid-plane fin, u towards the head
TAIL = ry(90)  # Bluegill's Tail3 bind orientation: u from the base back to the tip
JAW_PIVOT = (0, 6.5, 30)
JAW_OPEN = rx(18)  # the lower jaw hangs a little open at rest: the teeth show
FINS = {"Fin-Top": (16, 13), "Fin-Bot": (15, 11), "Tail3": (22, 28), "L-Fin": (10, 8), "R-Fin": (10, 8),
        "L-Pectoral": (14, 10), "R-Pectoral": (14, 10)}


def jaw(rel):
    """An absolute point of the open jaw, rel given about its pivot with the jaw closed."""
    return add(JAW_PIVOT, rotate(JAW_OPEN, rel))


def pectoral(side, x):
    sign = 1 if x > 0 else -1
    return part(f"{side}-Arm", (x * 7.25, 6, 19), children=[
        part(f"{side}-Pectoral", (x * 7.25, 6, 19), fin(FINS["L-Pectoral"]), center=(x * 9.64, 4.5, 12.4),
             ori=ry(-110 if sign > 0 else -70)),
        part(f"{side}-Forearm", (x * 8, 3, 19), children=[
            part(f"{side}-Hand", (x * 8, 1, 19), children=[part(f"{side}-Attachment", (x * 8, 0, 19), ori=rx(90))])]),
    ])


PIKE = part("Origin", (0, 0, 0), children=[
    part("Pelvis", (0, 12, 0), box((14, 18, 44)), children=[
        part("L-Fin", (4.5, 3.5, -6), fin(FINS["L-Fin"]), center=(5.8, -0.5, -10.8), ori=ry(-105)),
        part("R-Fin", (-4.5, 3.5, -6), fin(FINS["R-Fin"]), center=(-5.8, -0.5, -10.8), ori=ry(-75)),
        part("Head", (0, 12, 22), children=[
            part("Head2", (0, 12, 22), box((13, 15, 14)), center=(0, 12, 29), children=[
                part("Snout", (0, 10, 36), box((9, 6, 14)), center=(0, 10, 43), children=[
                    part("L-Teeth", (4.6, 7, 43), fin((13, 4)), center=(4.6, 5, 43), ori=ry(90)),
                    part("R-Teeth", (-4.6, 7, 43), fin((13, 4)), center=(-4.6, 5, 43), ori=ry(-90)),
                    part("Front-Teeth", (0, 7, 50.05), fin((8, 4)), center=(0, 5, 50.05)),
                ]),
                part("Gullet", (0, 7, 31), fin((16, 6)), center=(0, 4, 39), ori=SAGITTAL),
                part("Jaw", JAW_PIVOT, box((9, 3, 21)), center=jaw((0, -1, 10.5)), ori=JAW_OPEN, children=[
                    part("L-Teeth-Low", jaw((4.6, 0.5, 11)), fin((16, 4)), center=jaw((4.6, 2.5, 11)), ori=ry(90)),
                    part("R-Teeth-Low", jaw((-4.6, 0.5, 11)), fin((16, 4)), center=jaw((-4.6, 2.5, 11)),
                         ori=ry(-90)),
                    part("Front-Teeth-Low", jaw((0, 0.5, 20.55)), fin((8, 4)), center=jaw((0, 2.5, 20.55))),
                ]),
                part("L-Eye", (6.75, 15, 33), fin((6, 6)), ori=ry(90)),
                part("R-Eye", (-6.75, 15, 33), fin((6, 6)), ori=ry(-90)),
            ]),
        ]),
        part("Tail", (0, 12, -22), box((12, 16, 18)), center=(0, 12, -30), children=[
            part("Fin-Top", (0, 19.5, -30), fin(FINS["Fin-Top"]), center=(0, 26, -31), ori=SAGITTAL),
            part("Fin-Bot", (0, 4.5, -30), fin(FINS["Fin-Bot"]), center=(0, -1, -31), ori=SAGITTAL),
            part("Tail2", (0, 12, -38), box((8, 11, 12)), center=(0, 12, -43), children=[
                part("Tail3", (0, 12, -48), fin(FINS["Tail3"]), center=(0, 12, -59), ori=TAIL),
            ]),
        ]),
        part("Belly", (0, 4, 19), children=[part("Chest", (0, 6, 19), children=[pectoral("L", 1), pectoral("R", -1)])]),
    ]),
])

BACK = (50, 68, 40)
FLANK = (98, 122, 60)
BELLY = (222, 216, 182)
SPOT = (214, 208, 132)
FIN = (184, 92, 44)
FIN_DARK = (84, 56, 34)
IRIS, PUPIL = (222, 192, 64), (26, 28, 34)
MOUTH = (136, 58, 60)
TOOTH, TOOTH_ROOT = (236, 230, 206), (176, 160, 140)


def teeth(s, t, w, hanging, count):
    """A row of pointed teeth on a w-texel quad: hanging ones root at the top (t 0), standing ones at the bottom;
    big and small teeth alternate as on a pike's jaw."""
    k = s * count
    frac, index = k % 1.0, int(k)
    tall = 1.0 if index % 2 == 0 else 0.55
    reach = t if hanging else 1 - t
    if reach > tall * (1 - abs(frac - 0.5) * 3.0):
        return None
    return mix(TOOTH, TOOTH_ROOT, 0.6 - reach / tall), 255


def spots(z, y, height, salt=0.0):
    """How much a pale bean spot covers (z, y): staggered rows along the flank, longer than tall."""
    row = math.floor((y - 2) / 3.3)
    zz = z + (row % 2) * 3.1 + 7 * noise(row, salt, 0, 1.0)
    cell = math.floor(zz / 5.6)
    jz = noise(cell, row, salt + 3, 1.0) - 0.5
    jy = noise(row, cell, salt + 7, 1.0) - 0.5
    cz = (cell + 0.5) * 5.6 + jz * 1.6
    cy = 2 + (row + 0.5) * 3.3 + jy * 0.8
    d = math.hypot((zz - cz) / 2.3, (y - cy) / 1.15)
    return max(0.0, min(1.0, (1.15 - d) * 2.5)) * max(0.0, min(1.0, (0.88 - abs(height - 0.58) * 1.9)))


def body_colour(p, name, normal=None):
    x, y, z = p
    lo, hi = {"Pelvis": (3, 21), "Tail": (4, 20), "Tail2": (6.5, 17.5), "Head2": (4.5, 19.5), "Snout": (7, 13),
              "Jaw": (4, 7)}[name]
    height = (y - lo) / (hi - lo)
    if name == "Jaw":
        height = 0.1 + 0.25 * height
    c = mix(BELLY, FLANK, (height - 0.22) / 0.3)
    c = mix(c, BACK, (height - 0.66) / 0.3)
    c = tuple(v * (1 + 0.11 * (noise(x, y, z, 2.4) - 0.5)) for v in c)
    if name in ("Pelvis", "Tail", "Tail2") and abs(x) > 3:
        s = spots(z, y, height)
        # towards the tail the spots run together into marbled bars
        if z < -20:
            s = max(s, 0.6 * max(0.0, 1 - abs(((z + 0.6 * y) % 5.5) - 2.75) / 1.2) * (0.3 < height < 0.85))
        c = mix(c, SPOT, 0.92 * s)
    if name in ("Head2", "Snout") and abs(x) > 4:
        # mottled head, gill cover edge, a darker cheek line
        c = mix(c, BACK, 0.35 * (noise(x, y, z, 1.6) > 0.62))
        if name == "Head2" and abs(z - 24.5 - 0.15 * (y - 12)) < 0.8 and 6 < y < 18:
            c = mix(c, (44, 58, 36), 0.6)
    if name in ("Snout",) and y > 12.5:
        c = mix(c, BACK, 0.5)
    # inside the open mouth: palate under the snout, tongue on the jaw, throat at the head's front
    if name == "Snout" and normal[1] < -0.5:
        c = mix(MOUTH, (90, 36, 40), 0.4 * noise(x, y, z, 1.5) + 0.2 * (abs(x) < 2))
    if name == "Jaw" and normal[1] > 0.5:
        c = mix(MOUTH, (176, 92, 86), 0.5 * (abs(x) < 2.5))
    # the throat: only the part of the head's front framed by the open jaws (the snout is 9 wide, the head 13)
    jaw_top = 7 - math.tan(math.radians(18)) * (z - 30)
    if name == "Head2" and normal[2] > 0.5 and abs(x) < 4.4 and jaw_top - 0.5 < y < 7.2:
        c = mix((70, 28, 32), MOUTH, 0.35 * (y - jaw_top) / 2)
    return c


def mottled(c, x, y, n, along):
    """The pike's dark fin mottling: soft round blotches, fewer near the root."""
    spot = noise(x * 1.3, y * 1.3, 11, 2.2)
    return mix(c, FIN_DARK, 0.6 * max(0.0, (spot - 0.6) / 0.15) * min(1.0, along * 2))


PIKE_TONES = ((96, 34, 20), (182, 90, 34), (232, 150, 62))
PALE_TONES = ((120, 70, 34), (206, 140, 70), (236, 196, 130))

# Mid-plane fins: s 0 back .. 1 head, t 0 top .. 1 bottom; side fins: s 1 at the base (front); Tail3 bound like
# Bluegill's: s 0 at the base. Angles: 0 up the texture, -90 to its left (the back), 180 down (fanfin.Fan).
FANS = {
    "Fin-Top": Fan((11, 20), -72, 5, lambda n: 11 + 8 * math.sin(math.pi * (0.2 + 0.55 * (1 - n))), 7, 6, 1.5,
                   *PIKE_TONES, extra=mottled),
    "Fin-Bot": Fan((15, -14), -125, -160, lambda n: 25 + n, 14, 6, 1.5,
                   *PIKE_TONES, extra=mottled),
    "Tail3": Fan((-1, 14), 50, 130, lambda n: 15 + 10 * abs(2 * n - 1) ** 0.75, 1, 6, 1.4, *PIKE_TONES,
                 extra=mottled),
    "L-Fin": Fan((11, -0.5), -100, -150, lambda n: 10.5 - 2.5 * n, 1, 4, 1.3, *PALE_TONES),
    "R-Fin": Fan((11, -0.5), -100, -150, lambda n: 10.5 - 2.5 * n, 1, 4, 1.3, *PALE_TONES),
    "L-Pectoral": Fan((14.5, 3), -60, -128, lambda n: 13 + 1.5 * math.sin(math.pi * n), 1, 5, 1.3, *PALE_TONES),
    "R-Pectoral": Fan((14.5, 3), -60, -128, lambda n: 13 + 1.5 * math.sin(math.pi * n), 1, 5, 1.3, *PALE_TONES),
}


def fin_paint(name, s, t):
    """(colour, alpha) of a fin, tooth, throat or eye texel at (s, t) in 0..1 of its quad; None where transparent."""
    if name in FANS:
        return fan(FANS[name], s, t, *FINS[name])
    return paint_mouth_and_eyes(name, s, t)


def paint_mouth_and_eyes(name, s, t):
    if name in ("L-Teeth", "R-Teeth"):
        return teeth(s, t, 13, True, 4)
    if name == "Front-Teeth":
        return teeth(s, t, 8, True, 2.5)
    if name in ("L-Teeth-Low", "R-Teeth-Low"):
        return teeth(s, t, 16, False, 5)
    if name == "Front-Teeth-Low":
        return teeth(s, t, 8, False, 2.5)
    if name == "Gullet":
        # the dark throat seen between the jaws from the side; s 0 back (z 31) .. 1 front, t top (y 7) .. bottom
        z, y = 31 + 16 * s, 7 - 6 * t
        if y < 7 - math.tan(math.radians(18)) * (z - 30) + 0.4:
            return None
        return mix((70, 28, 32), MOUTH, s * 0.8), 255
    if name in ("L-Eye", "R-Eye"):
        r = math.hypot(s - 0.5, t - 0.5)
        if r > 0.5:
            return None
        if math.hypot(s - 0.36, t - 0.34) < 0.1:
            return (238, 236, 220), 255
        if r < 0.2:
            return PUPIL, 255
        if r < 0.42:
            return mix(IRIS, (176, 140, 40), (r - 0.2) / 0.22), 255
        return (62, 76, 44), 255
    raise KeyError(name)


if __name__ == "__main__":
    render("Pike", PIKE, body_colour, fin_paint)
