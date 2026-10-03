"""The cook's hut block (spec 2026-10-02 hut models): a cast iron stove built in Blockbench, embers glowing behind
the barred firebox door, an oven door with a brass handle, a brass rail with a checked tea towel, a stovepipe hung with
a braid of garlic, four burners (Burner_<A..D>) with a pot of stew and its ladle on one, a backsplash under a warming
shelf holding plates and a bowl, a salt crock and a pepper mill. Two blocks tall, as Hytale's cooking bench. The pot
steams (the item's Particles, on the empty node Steam)."""

from PIL import ImageDraw

from brushes import as_tile, cloth, coloured, embers, jitter, metal, painted, paper, smooth, terracotta, wood
from conditions import INDUSTRIAL, USED
from materials import BRASS

MODEL = "Blocks/HyColony/Huts/Cook"
ICON = "Hut_Cook"
IRON = (62, 60, 66)
# The pot rim's top island (Pot_Rim, 9 x 1 x 9 in the Blockbench model): the stew is drawn for it.
RIM = 9
# Materials drawn for their island, never turned.
PICTURES = frozenset({"stew"})
# Painted in layers (spec 2026-10-03 blockpaint surfaces): a stove in use, its soot and grease (a fire's workplace).
CONDITION, ENVIRONMENT, SEED = USED, INDUSTRIAL, 11
FAMILY = {"crock": "ceramic", "stew": "liquid", "towel": "textile", "brass": "cuprous", "garlic": "plant",
          "plate": "ceramic"}
# Node name prefix -> material.
PREFIXES = (("Glow", "embers"), ("Oven_Handle", "brass"), ("Rail", "brass"), ("Towel", "towel"), ("Ladle", "steel"),
            ("Garlic_Rope", "rope"), ("Garlic", "garlic"), ("Crock_Lid", "lid"), ("Crock_Knob", "lid"),
            ("Crock", "crock"), ("Mill_Knob", "brass"), ("Mill", "mill"), ("Plate", "plate"), ("Bowl", "bowl"))


def material(name, side):
    """The material of a node's face: the stew in the pot, each of the four burners (Burner_<A..D>) in lighter iron
    under a brass cap and a dark pan grate, then by name; cast iron for the rest of the stove."""
    if name == "Pot_Rim" and side == "top":
        return "stew"
    if name.startswith("Burner"):
        return "brass" if name.endswith("_Cap") else "iron" if "_Grate" in name else "burner"
    return next((m for prefix, m in PREFIXES if name.startswith(prefix)), "iron")


def tiles(_assets):
    """Material -> its 32 px tile or brush."""
    return {
        "iron": metal(IRON, streak=0.04), "burner": metal((108, 106, 114), streak=0.04),
        "steel": metal((176, 180, 188)), "brass": metal(BRASS),
        "embers": embers(), "rope": cloth((150, 116, 72), folds=0.03), "garlic": paper((230, 222, 204)),
        "crock": crock(), "lid": wood((150, 108, 64), plank=99), "mill": wood((92, 56, 34), plank=99),
        "stew": stew(), "towel": towel(), "plate": paper((236, 230, 214), aged=(196, 206, 222)),
        "bowl": terracotta((150, 88, 56)),
    }


def crock():
    """The salt crock's glazed stoneware: cream, with a blue band round its sides and a faint glaze sheen."""
    def rule(x, y, w, h, side):
        if side not in ("top", "bottom") and y == 1:
            return coloured((64, 98, 160), 1 + 0.04 * jitter(x, 35))
        return coloured((228, 218, 194), 1 + 0.03 * smooth(x / 2 + y, 36) + (0.06 if (x + y) % 5 == 0 else 0))
    return painted(rule)


def towel():
    """A red and cream checked tea towel: 2 px checks, the red ones darker where they cross, a soft weave."""
    def rule(x, y, w, h, side):
        red_row, red_column = (y // 2) % 2 == 0, (x // 2) % 2 == 0
        if red_row and red_column:
            rgb = (150, 40, 42)
        elif red_row or red_column:
            rgb = (196, 92, 84)
        else:
            rgb = (232, 222, 200)
        return coloured(rgb, 1 + 0.03 * (1 if (x + y) % 2 else -1))
    return painted(rule)


def stew(size=RIM):
    """The pot's size x size rim, laid out for its island: a one pixel iron rim round a thick stew dotted with carrot
    and herbs."""
    image = as_tile(metal(IRON, streak=0.04))
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 1, size - 2, size - 2], fill=(142, 82, 42, 255))
    for x, y, colour in ((2, 2, (226, 128, 48)), (5, 3, (226, 128, 48)), (3, 5, (96, 140, 60)), (6, 6, (96, 140, 60)),
                         (2, 6, (176, 112, 64)), (6, 2, (176, 112, 64))):
        draw.point((x, y), fill=(*colour, 255))
    return image
