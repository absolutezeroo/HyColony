"""The builder's hut block (spec 2026-10-02 hut models): an architect's drafting table built in Blockbench. Its
materials: painted wood, metal, paper, cloth and clay brushes, a few Hytale cloth tints, plus the painted details only
this model has (the blueprint sheet, rolled paper ends, a quill's feather) and its decals (the sketch on the sheet,
the saw's teeth past its blade)."""

from PIL import Image, ImageDraw

from brushes import as_tile, clay, cloth, metal, paper, wood
from conditions import DRY_INTERIOR, USED
from decals import Decal
from materials import PAPER, feather, tinted

MODEL = "Blocks/HyColony/Huts/Builder"
ICON = "Hut_Builder"
# Materials drawn for their island, never turned with the wood grain.
PICTURES = frozenset({"sheet", "roll_end"})
# Painted in layers (spec 2026-10-03 blockpaint surfaces): a working drafting table, indoors.
CONDITION, ENVIRONMENT, SEED = USED, DRY_INTERIOR, 11
FAMILY = {"sheet": "paper", "blueprint": "paper", "sheet_edge": "paper", "roll_end": "paper", "lead": "stone",
          "ink": "liquid", "shaft": "bone", "feather": "hair"}
SKETCH = (214, 226, 238, 255)
BLUEPRINT = paper((52, 92, 150), aged=(34, 62, 108))
IRON = (150, 156, 166)
# Node name -> material, for the nodes no rule below covers.
NAMED = {"Board": "board", "Lip": "board", "Plan_Roll_2": "blueprint", "Pencil": "pencil", "Pencil_Wood": "handle",
         "Pencil_Lead": "lead", "Ink": "ink", "Ink_Stand": "frame", "Quill": "shaft", "Quill_Vane": "feather",
         "Quill_Tip": "feather"}
# Node name prefix -> material.
PREFIXES = (("Leg", "frame"), ("Rail", "frame"), ("Shelf", "frame"), ("Batten", "frame"), ("Paper_Roll", "paper"),
            ("Plan_Roll", "paper"), ("Plan_Tie", "tie"), ("Hook", "hook"), ("Brick", "brick"),
            ("Hammer_Handle", "handle"), ("Saw_Grip", "handle"))


def material(name, side):
    """The material of a node's face: roll ends show their spiral, the sheet its grid on top (its sketch a decal) and
    a dark edge elsewhere; iron by default (hammer head, saw blade, its teeth a decal)."""
    if "Roll" in name and side in ("left", "right"):
        return "roll_end"
    if name == "Blueprint":
        return "sheet" if side == "top" else "sheet_edge"
    if name in NAMED:
        return NAMED[name]
    return next((m for prefix, m in PREFIXES if name.startswith(prefix)), "iron")


def tiles(assets):
    """Material -> its 32 px tile or brush."""
    def tile(name):
        return assets.image("Common/BlockTextures/" + name + ".png").crop((0, 0, 32, 32))

    return {
        "frame": wood((120, 74, 44)), "board": wood((198, 162, 114)), "handle": wood((206, 170, 118), plank=99),
        "sheet": blueprint(), "blueprint": blueprint(),
        "sheet_edge": tinted((34, 62, 108), tile("Cloth_Blue"), 0.1),
        "paper": paper(PAPER), "roll_end": roll_end(), "tie": cloth((168, 52, 44), folds=0.05),
        "pencil": wood((214, 170, 52), plank=99),
        "lead": tinted((48, 48, 56), tile("Cloth_Black"), 0.1), "ink": tinted((40, 48, 86), tile("Cloth_Black"), 0.2),
        "shaft": tinted((204, 198, 186), tile("Cloth_White"), 0.2), "feather": feather(tile("Cloth_White")),
        "brick": clay((170, 74, 52)), "iron": metal(IRON), "hook": metal((72, 74, 82)),
    }


def blueprint():
    """Blueprint paper (the paper brush in blue, laid out for the 24 x 18 sheet) under a soft grid."""
    image = as_tile(BLUEPRINT)
    image.paste(BLUEPRINT(24, 18, "top"), (0, 0))
    draw = ImageDraw.Draw(image)
    for i in range(0, 32, 4):
        draw.line([(i, 0), (i, 31)], fill=(64, 104, 160, 255))
        draw.line([(0, i), (31, i)], fill=(64, 104, 160, 255))
    return image


def sketch(w, h, side):
    """The sheet's frame and a house drawn on it (DECALS), clear around them: seen from the table's front (+z), u runs
    to the viewer's right and v towards them, so the sheet reads as drawn."""
    sheet = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    pen = ImageDraw.Draw(sheet)
    pen.rectangle([1, 1, 22, 16], outline=(150, 180, 214, 255))
    pen.line([(4, 8), (10, 3), (16, 8)], fill=SKETCH)
    pen.rectangle([5, 8, 15, 14], outline=SKETCH)
    pen.rectangle([9, 10, 11, 14], outline=SKETCH)
    pen.rectangle([13, 9, 14, 10], outline=SKETCH)
    for y in (4, 6, 8, 10):
        pen.line([(18, y), (21, y)], fill=SKETCH)
    return sheet


def roll_end():
    """Paper whose 3 x 3 corner reads as a rolled sheet's end: darker corners, a hollow centre."""
    image = as_tile(paper(PAPER))
    draw = ImageDraw.Draw(image)
    for corner in ((0, 0), (2, 0), (0, 2), (2, 2)):
        draw.point(corner, fill=(176, 160, 130, 255))
    draw.point((1, 1), fill=(120, 104, 80, 255))
    return image


def teeth(mirrored):
    """A saw's teeth (DECALS), on a decal two rows high whose first row lies on the blade's last: a honed edge along
    it, the tips one row past the blade, every other texel; mirrored for the blade's other side, whose u runs the
    other way, so both sides' tips meet."""
    def brush(w, h, side):
        image = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        for x in range(w):
            image.putpixel((x, 0), TEETH_EDGE)
            if (w - 1 - x if mirrored else x) % 2 == 0:
                image.putpixel((x, 1), TEETH_TIP)
        return image
    return brush


# The saw's teeth past the bottom of its 14 x 5 blade, on both its flats, and the house drawn on the 24 x 18 sheet
# (spec 2026-10-03 blockpaint decals).
TEETH_EDGE, TEETH_TIP = (196, 202, 212, 255), (120, 126, 136, 255)
DECALS = {"teeth_r": Decal("Saw_Blade", "right", (0, 4, 14, 2), teeth(False)),
          "teeth_l": Decal("Saw_Blade", "left", (0, 4, 14, 2), teeth(True)),
          "sketch": Decal("Blueprint", "top", (0, 0, 24, 18), sketch)}
