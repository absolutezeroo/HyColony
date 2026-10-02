"""The builder's hut block (spec 2026-10-02 hut models): an architect's drafting table built in Blockbench. Its
materials: painted wood, metal, paper, cloth and clay brushes, a few Hytale cloth tints, plus the painted details only
this model has (the blueprint sheet and its sketch, rolled paper ends, saw teeth, a quill's feather)."""

from PIL import Image, ImageDraw

from brushes import as_tile, clay, cloth, metal, paper, wood
from materials import PAPER, feather, tinted

MODEL = "Blocks/HyColony/Huts/Builder"
ICON = "Hut_Builder"
# Materials drawn for their island, never turned with the wood grain.
PICTURES = frozenset({"sheet", "roll_end", "saw"})
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
    """The material of a node's face: roll ends show their spiral, the saw blade its teeth, the sheet its sketch on
    top and a dark edge elsewhere; iron by default (hammer head, saw blade)."""
    if "Roll" in name and side in ("left", "right"):
        return "roll_end"
    if name == "Saw_Blade" and side not in ("top", "bottom"):
        return "saw"
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
        "sheet": blueprint(True), "blueprint": blueprint(False),
        "sheet_edge": tinted((34, 62, 108), tile("Cloth_Blue"), 0.1),
        "paper": paper(PAPER), "roll_end": roll_end(), "tie": cloth((168, 52, 44), folds=0.05),
        "pencil": wood((214, 170, 52), plank=99),
        "lead": tinted((48, 48, 56), tile("Cloth_Black"), 0.1), "ink": tinted((40, 48, 86), tile("Cloth_Black"), 0.2),
        "shaft": tinted((204, 198, 186), tile("Cloth_White"), 0.2), "feather": feather(tile("Cloth_White")),
        "brick": clay((170, 74, 52)), "iron": metal(IRON), "hook": metal((72, 74, 82)), "saw": saw(),
    }


def blueprint(sketch):
    """Blueprint paper (the paper brush in blue, laid out for the 24 x 18 sheet): a soft grid; with sketch, the
    sheet's frame and a house drawing: seen from the table's front (+z), u runs to the viewer's right and v towards
    them, so the sheet reads as drawn."""
    image = as_tile(BLUEPRINT)
    image.paste(BLUEPRINT(24, 18, "top"), (0, 0))
    draw = ImageDraw.Draw(image)
    for i in range(0, 32, 4):
        draw.line([(i, 0), (i, 31)], fill=(64, 104, 160, 255))
        draw.line([(0, i), (31, i)], fill=(64, 104, 160, 255))
    if sketch:
        sheet = Image.new("RGBA", (24, 18), (0, 0, 0, 0))
        pen = ImageDraw.Draw(sheet)
        pen.rectangle([1, 1, 22, 16], outline=(150, 180, 214, 255))
        pen.line([(4, 8), (10, 3), (16, 8)], fill=SKETCH)
        pen.rectangle([5, 8, 15, 14], outline=SKETCH)
        pen.rectangle([9, 10, 11, 14], outline=SKETCH)
        pen.rectangle([13, 9, 14, 10], outline=SKETCH)
        for y in (4, 6, 8, 10):
            pen.line([(18, y), (21, y)], fill=SKETCH)
        image.alpha_composite(sheet)
    return image


def roll_end():
    """Paper whose 3 x 3 corner reads as a rolled sheet's end: darker corners, a hollow centre."""
    image = as_tile(paper(PAPER))
    draw = ImageDraw.Draw(image)
    for corner in ((0, 0), (2, 0), (0, 2), (2, 2)):
        draw.point(corner, fill=(176, 160, 130, 255))
    draw.point((1, 1), fill=(120, 104, 80, 255))
    return image


def saw():
    """Brushed iron with a row of teeth on its fourth and fifth rows: the bottom edge of the 5 high blade (its
    highlight band laid out for those 5 rows, over a full brushed tile)."""
    image = as_tile(metal(IRON))
    image.paste(metal(IRON)(32, 5, "front"), (0, 0))
    draw = ImageDraw.Draw(image)
    for x in range(0, 32, 2):
        draw.point((x, 4), fill=(70, 74, 82, 255))
        draw.point((x + 1, 3), fill=(70, 74, 82, 255))
    return image
