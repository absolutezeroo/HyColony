"""The farmer's hut block (spec 2026-10-02 hut models): a potting bench built in Blockbench, a seedling tray and a
copper watering can on its top, three potted sprouts on its high shelf, a sack of seeds and a crate holding a pumpkin
and carrots on its low shelf, a pitchfork leaning against it. Its sprouts sway in a light breeze and a drop of water
beads under the can's rose, then falls (animation)."""

import math

from PIL import ImageDraw

from brushes import as_tile, cloth, coloured, crystal, jitter, metal, painted, smooth, stone, terracotta, wood
from models import multiply, walk

MODEL = "Blocks/HyColony/Huts/Farmer"
COPPER = (186, 108, 64)
# Where the sprouts stand on a soil island of a given size (u, v pixels): the tray's two rows of three (the Blockbench
# model's Sprout_Tray_*); a pot's single sprout stands at its centre.
WET_SPOTS = {(14, 12): tuple((u, v) for v in (3.5, 8.5) for u in (3, 7, 11))}
WET_RADIUS = 2.6
# The breeze: one sway every SWAY_TICKS (1/60 s, the blockyanim time unit), SWAY_DEGREES each way.
SWAY_TICKS, SWAY_DEGREES = 180, 6
# The drop (node Drop, a 1 unit box hanging from the rose's lowest edge): it appears at DROP_SHOWN as a small bead
# (DROP_BEAD of its size), grows until DROP_FULL, falls DROP_FALL units onto the bench top until DROP_LANDED, then
# vanishes and goes back up unseen. Hytale stretches a shape about its centre: the bead is raised by half of what it
# lacks, so that it keeps hanging from the rose while it grows.
DROP_SHOWN, DROP_FULL, DROP_LANDED, DROP_BEAD, DROP_FALL = 70, 140, 152, 0.3, 3.0
ICON = "Hut_Farmer"
# Materials drawn for their island, never turned with the wood grain.
PICTURES = frozenset({"can_top"})
# The drop is shown only part of the time: it casts no baked shadow.
SEE_THROUGH = frozenset({"Drop"})
# Node name prefix -> material, for the faces material's rules leave.
PREFIXES = (("Drop", "water"), ("Leg", "frame"), ("Back_Board", "frame"), ("Bench_Top", "planks"),
            ("Upper_Shelf", "planks"), ("Shelf", "planks"), ("Sprout", "leaf"), ("Can", "copper"), ("Sack", "burlap"),
            ("Crate", "slats"), ("Pumpkin_Stem", "stem"), ("Pumpkin", "pumpkin"), ("Carrot", "carrot"),
            ("Fork_", "iron"), ("Fork", "handle"))


def material(name, side):
    """The material of a node's face: soil on top of the tray and the pots, seeds in the open sack, then by name."""
    if side == "top" and name.startswith(("Tray", "Pot")):
        return "soil"
    if name == "Tray":
        return "slats"
    if name.startswith("Pot"):
        return "clay"
    if name == "Sack_Rim" and side == "top":
        return "seeds"
    if name == "Can" and side == "top":
        return "can_top"
    return next(m for prefix, m in PREFIXES if name.startswith(prefix))


def tiles(_assets):
    """Material -> its 32 px tile or brush."""
    return {
        "frame": wood((116, 80, 48)), "planks": wood((160, 118, 72), plank=6), "slats": wood((134, 96, 58), plank=3),
        "handle": wood((150, 112, 70), plank=99), "soil": wet_soil(stone((88, 62, 44), chunk=(2, 2))),
        "clay": terracotta((166, 96, 60)), "leaf": leaf((86, 148, 58)), "copper": metal(COPPER), "can_top": can_top(),
        "water": crystal((168, 214, 244), (86, 150, 210), (40, 92, 160)),
        "burlap": cloth((176, 146, 98), folds=0.06), "seeds": stone((196, 164, 96), chunk=(1, 1)),
        "pumpkin": wood((214, 120, 44), plank=2), "stem": wood((104, 110, 52), plank=99),
        "carrot": wood((224, 120, 40), plank=99), "iron": metal((84, 86, 94)),
    }


def wet_soil(soil):
    """Soil just watered round its sprouts: around each stem (WET_SPOTS) a slightly darker damp halo fading over
    WET_RADIUS pixels, and a few wet glints beside it."""
    def brush(w, h, side):
        image = soil(w, h, side)
        pixels = image.load()
        spots = WET_SPOTS.get((w, h), ((w / 2, h / 2),))
        for x in range(w):
            for y in range(h):
                near = min(math.dist((x + 0.5, y + 0.5), spot) for spot in spots)
                if near < WET_RADIUS:
                    damp = 1 - 0.2 * (1 - near / WET_RADIUS)
                    if 1 < near < 2 and jitter(x * 37 + y * 11, 34) > 0.55:
                        damp = 1.3
                    r, g, b, a = pixels[x, y]
                    pixels[x, y] = (min(255, round(r * damp)), min(255, round(g * damp)),
                                    min(255, round(b * damp * 1.06)), a)
        return image
    return brush


def can_top():
    """The watering can's open top, laid out for its 6 x 5 island: a copper rim round the water, which catches two
    glints."""
    image = as_tile(metal(COPPER))
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 1, 4, 3], fill=(58, 116, 172, 255))
    draw.point((2, 1), fill=(150, 202, 236, 255))
    draw.point((3, 2), fill=(112, 168, 214, 255))
    return image


def animation(nodes):
    """The looping blockyanim: each sprout (a node Sprout_*, pivoting at its base) sways side to side and to and fro
    a quarter turn apart, starting a quarter cycle after the previous sprout so that they never move as one; the drop
    beads and falls (drop)."""
    sprouts = [n["name"] for n in walk(nodes) if n["name"].startswith("Sprout") and n.get("children")]
    side = (0, SWAY_DEGREES, 0, -SWAY_DEGREES)
    fro = (SWAY_DEGREES / 2, 0, -SWAY_DEGREES / 2, 0)
    tracks = {}
    for index, name in enumerate(sprouts):
        k = index % 4
        keys = [tilt(fro[(k + i) % 4], side[(k + i) % 4]) for i in range(5)]
        tracks[name] = {"position": [], "shapeStretch": [], "shapeVisible": [], "shapeUvOffset": [], "orientation": [
            {"time": i * SWAY_TICKS // 4, "delta": dict(zip("xyzw", q)), "interpolationType": "smooth"}
            for i, q in enumerate(keys)]}
    tracks["Drop"] = drop()
    return {"formatVersion": 1, "duration": SWAY_TICKS, "holdLastKeyframe": False, "nodeAnimations": tracks}


def drop():
    """The drop's tracks over one SWAY_TICKS cycle: hidden, beading and growing, falling, hidden again."""
    def stretch(time, k):
        return {"time": time, "delta": {"x": k, "y": k, "z": k}, "interpolationType": "smooth"}

    def fall(time, dy, interpolation="linear"):
        return {"time": time, "delta": {"x": 0, "y": dy, "z": 0}, "interpolationType": interpolation}

    hang = (1 - DROP_BEAD) / 2
    return {
        "orientation": [], "shapeUvOffset": [],
        "shapeVisible": [{"time": 0, "delta": False}, {"time": DROP_SHOWN, "delta": True},
                         {"time": DROP_LANDED, "delta": False}],
        "shapeStretch": [stretch(0, DROP_BEAD), stretch(DROP_SHOWN, DROP_BEAD), stretch(DROP_FULL, 1),
                         stretch(DROP_LANDED, 1), stretch(SWAY_TICKS, DROP_BEAD)],
        "position": [fall(0, hang), fall(DROP_SHOWN, hang, "smooth"), fall(DROP_FULL, 0),
                     fall(DROP_LANDED, -DROP_FALL), fall(SWAY_TICKS, hang)],
    }


def tilt(about_x, about_z):
    """The quaternion leaning a node about_x degrees about x, then about_z about z."""
    def about(axis, degrees):
        half = math.radians(degrees) / 2
        return tuple(c * math.sin(half) for c in axis) + (math.cos(half),)

    return multiply(about((0, 0, 1), about_z), about((1, 0, 0), about_x))


def leaf(rgb):
    """Young leaves: a mottled green, lighter towards the top of side faces, with a few light veins."""
    def rule(x, y, w, h, side):
        k = 1 + 0.08 * smooth(x / 2 + y * 0.7, 31) + 0.04 * jitter(x * 17 + y * 5, 32)
        if side not in ("top", "bottom") and h > 1:
            k += 0.12 * (1 - y / (h - 1))
        if jitter(x * 23 + y * 41, 33) > 0.8:
            k += 0.15
        return coloured(rgb, k)
    return painted(rule)
