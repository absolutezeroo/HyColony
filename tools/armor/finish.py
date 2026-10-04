"""The plate armor's and the knight's weapons' materials and brushes (spec 2026-10-02 plate armor): plates framed as
Hytale's steel armour paints them, overlapping lames, dark mail, leather, the red tabard, brass, feathers, MC's T slit
on the visor; the sword's blade, red grip and gems, the shield's red enamel face."""

from brushes import cloth, coloured, crystal, jitter, painted
from composer import Composer
from conditions import MAINTAINED
from illustration import Illustration
from materials import leather
from PIL import ImageDraw

# Painted in layers (spec 2026-10-03 blockpaint surfaces, catalog.model_texture): a knight's kit, kept with care, worn
# anywhere (no environment); worn, not standing on a floor. Its "gold" is the brass of its rims, buckles and guard (a
# gilt alloy, not gold), hence cuprous.
CONDITION, SEED = MAINTAINED, 11
# Illustrated and composed (specs 2026-10-04 blockpaint illustration, composer), every piece as important as the next.
ILLUSTRATION, COMPOSER = Illustration(), Composer()
FAMILY = {"plate": "ferrous", "trim": "ferrous", "gold": "cuprous", "lames": "ferrous", "mail": "ferrous",
          "tabard_1": "textile", "tabard_2": "textile", "leather": "leather", "plume": "hair", "plume_white": "hair",
          "visor_l": "ferrous", "visor_r": "ferrous", "ridge": "ferrous", "grip": "leather", "blade": "ferrous",
          "edge": "ferrous", "shield": "paint_film", "shield_top": "paint_film"}

STEEL = (156, 164, 178)
HONED = (226, 232, 240)
DARK = (74, 78, 92)
RED = (150, 30, 38)
GOLD = (214, 168, 70)
SLIT = (22, 22, 28, 255)
# Materials drawn for their island, never turned.
PICTURES = frozenset({"visor_l", "visor_r", "ridge", "tabard_1", "tabard_2"})
# Node name stem -> material, after the cases of material().
STEMS = {"Hips": "mail", "Rerebrace": "mail", "Chausse": "mail", "Belt": "leather", "Strap": "leather",
         "Fingers": "leather", "Rim": "gold", "Pivot": "gold", "Buckle": "gold", "Emblem": "gold", "Cuff": "gold",
         "Socket": "gold", "Crest": "trim", "Gorget": "trim", "Knuckles": "trim", "Toe": "trim", "Shin": "trim"}


def material(name, side):
    """The material of a node's face, from its name (its stem before the first '_') and side."""
    stem = name.split("_")[0]
    if name.endswith("_Gem"):
        return "gem"
    if name.endswith("_Ring"):
        return "gold"
    if name == "Face_1" and side == "right":
        return "shield_top"
    if stem in ARMS_STEMS:
        return ARMS_STEMS[stem](side)
    if stem == "Visor" and side == "front":
        return "visor_" + name[-1].lower()
    if name == "Ridge" and side == "front":
        return "ridge"
    if stem == "Sabaton":
        return "lames" if side == "top" else "plate"
    if name.endswith(("_Cloth_1", "_Cloth_2")):
        return ("tabard_" + name[-1]) if side in ("front", "back") else "cloth"
    if name.startswith(("Hair-B", "Plume_C")):
        return "plume"
    if name.startswith(("Hair-", "Plume_")):
        return "plume_white"
    if name.endswith("Guard"):
        return "gold"
    if stem in ("Vambrace", "Gauntlet") and side not in ("top", "bottom"):
        return "lames"
    return STEMS.get(stem, "plate")


# The sword's and shield's nodes (arms.py): stem -> side -> material. The blade's flats are its x faces, its edges
# the z ones; the shield's face looks to +x. Looked up before the armour's cases: no armour node has these stems
# (check.py's material test would catch one turning to a weapon's material).
ARMS_STEMS = {
    "Pommel": lambda side: "gold", "Guard": lambda side: "gold", "Quillon": lambda side: "gold",
    "Cross": lambda side: "gold", "Boss": lambda side: "gold", "Bracket": lambda side: "gold",
    "Grip": lambda side: "grip", "Ricasso": lambda side: "blade" if side in ("left", "right") else "edge",
    "Blade": lambda side: "blade" if side in ("left", "right") else "edge",
    "Face": lambda side: "shield" if side == "right" else "trim",
}


def tiles(assets):
    """Material -> its brush."""
    smooth = framed(STEEL, False)
    red = cloth(RED, folds=0.08)
    return {
        "plate": framed(STEEL), "trim": framed(DARK), "gold": framed(GOLD, False), "lames": lames(STEEL),
        "mail": mail(), "cloth": red, "tabard_1": tabard(red, 0), "tabard_2": tabard(red, 1),
        "leather": leather((84, 52, 32), assets.image("Common/BlockTextures/Cloth_Black.png").crop((0, 0, 32, 32))),
        "plume": feather((176, 30, 38), (236, 196, 170)), "plume_white": feather((222, 220, 214), (250, 246, 236)),
        "visor_l": visor(smooth, "l"), "visor_r": visor(smooth, "r"), "ridge": ridge(smooth),
        "grip": leather((110, 34, 36), assets.image("Common/BlockTextures/Cloth_Black.png").crop((0, 0, 32, 32))),
        "gem": crystal((255, 170, 150), (214, 30, 44), (96, 8, 22)), "blade": blade(), "edge": framed(HONED, False),
        "shield": shield_face(False), "shield_top": shield_face(True),
    }


def framed(rgb, inset=True):
    """A plate face as Hytale's steel armour paints it: rounded by a light middle fading to the sides, a bright rim on
    its top and left edges and a dark one on the others, and (inset) a groove two pixels in and a rivet in each corner
    on faces large enough."""
    def rule(x, y, w, h, side):
        across = abs(2 * x / max(w - 1, 1) - 1)
        k = 1.06 - 0.2 * across * across + 0.03 * jitter(x * 7 + y * 13, 92)
        if x == 0 or y == 0:
            return coloured(rgb, 1.32)
        if x == w - 1 or y == h - 1:
            return coloured(rgb, 0.6)
        big = inset and w >= 9 and h >= 9
        if big and (x, y) in ((3, 3), (w - 4, 3), (3, h - 4), (w - 4, h - 4)):
            return coloured(rgb, 1.4)
        if big and ((x in (2, w - 3) and 2 <= y <= h - 3) or (y in (2, h - 3) and 2 <= x <= w - 3)):
            return coloured(rgb, 0.74 * k)
        return coloured(rgb, k)
    return painted(rule)


def blade():
    """A blade's flat: a honed bright edge, a darker fuller down the middle with a gold inlaid line in it."""
    def rule(x, y, w, h, side):
        edge = min(x, w - 1 - x)
        if edge == 0:
            return coloured(HONED, 1 + 0.03 * jitter(y, 96))
        if edge == 1:
            return coloured(HONED, 0.88)
        middle = abs(x - (w - 1) / 2)
        if w >= 7 and middle < 0.6:
            return coloured(GOLD, 0.9 + 0.1 * jitter(y, 97))
        if w >= 7 and middle < 1.6:
            return coloured(DARK, 1.1)
        return coloured(STEEL, 1.04 - 0.12 * (1 - middle / (w / 2)) + 0.03 * jitter(x * 7 + y * 3, 98))
    return painted(rule)


def shield_face(top):
    """A band of the shield's face: red enamel rounded by a light middle, a steel rim two pixels wide down its sides
    and, on the top band, along its top: the rim follows the shield's outline, not the bands."""
    def rule(x, y, w, h, side):
        if min(x, w - 1 - x) < 2 or (top and y < 2):
            return coloured(STEEL, 1.3 if x == 0 or (top and y == 0) else 0.9)
        across = abs(2 * x / max(w - 1, 1) - 1)
        return coloured(RED, 1.1 - 0.25 * across * across + 0.03 * jitter(x * 5 + y * 7, 99))
    return painted(rule)


def lames(rgb):
    """Overlapping lames every fifth row: a bright lip on top, the plate rounded across, a dark shadow under the next
    lame."""
    def rule(x, y, w, h, side):
        across = abs(2 * x / max(w - 1, 1) - 1)
        row = y % 5
        if row == 0:
            return coloured(rgb, 1.3)
        if row == 4:
            return coloured(rgb, 0.55)
        return coloured(rgb, 1.04 - 0.2 * across * across - 0.04 * row + 0.03 * jitter(x * 5 + y * 3, 93))
    return painted(rule)


def mail():
    """Dark mail between the plates: rings in staggered rows, each lit on top and shadowed below."""
    def rule(x, y, w, h, side):
        ring = (x + (y // 2) % 2) % 2
        k = (1.25 if y % 2 == 0 else 0.8) if ring else 0.55
        return coloured((92, 96, 108), k)
    return painted(rule)


def tabard(cloth_brush, half):
    """One half (0 upper, 1 lower) of the tabard's 19 x 24 face, each 19 x 12: red cloth, a gold hem one pixel in, a
    gold cross in the middle, a fringe below."""
    def brush(w, h, side):
        whole = cloth_brush(w, 2 * h, side)
        draw = ImageDraw.Draw(whole)
        draw.rectangle([1, 1, w - 2, 2 * h - 3], outline=(*GOLD, 255))
        draw.rectangle([w // 2 - 1, 5, w // 2 + 1, 2 * h - 7], fill=(*GOLD, 255))
        draw.rectangle([5, 8, w - 6, 10], fill=(*GOLD, 255))
        for x in range(0, w, 2):
            draw.point((x, 2 * h - 1), fill=(*GOLD, 255))
        return whole.crop((0, half * h, w, (half + 1) * h))
    return brush


def feather(rgb, quill):
    """A feather of colour rgb: a quill line along the middle of each side, barbs slanting back from it, darker toward
    the edges."""
    def rule(x, y, w, h, side):
        if side in ("left", "right") and h >= 3:
            off = abs(y - (h - 1) / 2)
            if off < 0.6:
                return coloured(quill, 1)
            k = (1.1 if (x + round(off)) % 3 else 0.82) - 0.1 * off / (h / 2)
            return coloured(rgb, k + 0.04 * jitter(x * 7 + y * 11, 94))
        return coloured(rgb, 0.85 + 0.05 * jitter(x * 3 + y * 5, 95))
    return painted(rule)


def visor(plate_brush, half):
    """A visor half (15 x 22): its share of MC's T slit (the eye slit) and, on the left half, the breaths."""
    def brush(w, h, side):
        image = plate_brush(w, h, side)
        draw = ImageDraw.Draw(image)
        draw.rectangle([1, 8, w - 2, 9], fill=SLIT)
        if half == "l":
            for y in range(13, 19, 2):
                for x in (w - 6, w - 4):
                    draw.point((x, y), fill=SLIT)
        return image
    return brush


def ridge(plate_brush):
    """The nose ridge (3 x 24): the T's slot down from the eye slit."""
    def brush(w, h, side):
        image = plate_brush(w, h, side)
        ImageDraw.Draw(image).rectangle([1, 9, 1, 19], fill=SLIT)
        return image
    return brush
