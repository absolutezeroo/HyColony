"""The builder's hut block (spec 2026-10-02 hut models): an architect's drafting table built in Blockbench. Its
materials: Hytale wood, iron, brick and cloth tiles, plus the painted details only this model has (the blueprint
sheet and its sketch, rolled paper ends, saw teeth, a quill's feather)."""

from PIL import Image, ImageDraw

from paint import darker, softened

MODEL = "Builder"
# Materials drawn for their island, never turned with the wood grain.
PICTURES = frozenset({"sheet", "roll_end", "saw"})
PAPER = (226, 214, 186)
SKETCH = (214, 226, 238, 255)
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
    """Material -> its 32 px tile."""
    def tile(name):
        return assets.image("Common/BlockTextures/" + name + ".png").crop((0, 0, 32, 32))

    return {
        "frame": tile("Wood_Hardwood_Planks"), "board": tile("Wood_Lightwood_Planks"),
        "handle": tinted((206, 170, 118), tile("Wood_Lightwood_Planks"), 0.5),
        "sheet": blueprint(tile("Cloth_Blue"), True), "blueprint": blueprint(tile("Cloth_Blue"), False),
        "sheet_edge": tinted((34, 62, 108), tile("Cloth_Blue"), 0.1),
        "paper": tinted(PAPER, tile("Cloth_White"), 0.3), "roll_end": roll_end(tile("Cloth_White")),
        "tie": tinted((168, 52, 44), tile("Cloth_Red"), 0.3),
        "pencil": tinted((214, 170, 52), tile("Cloth_Yellow"), 0.2),
        "lead": tinted((48, 48, 56), tile("Cloth_Black"), 0.1), "ink": tinted((40, 48, 86), tile("Cloth_Black"), 0.2),
        "shaft": tinted((204, 198, 186), tile("Cloth_White"), 0.2), "feather": feather(tile("Cloth_White")),
        "brick": tinted((170, 74, 52), tile("Clay_Brick_Raw_Side"), 0.35), "iron": tile("Metal_Iron_Smooth"),
        "hook": darker(tile("Metal_Iron"), 0.45), "saw": saw(tile("Metal_Iron_Smooth")),
    }


def tinted(rgb, tile, keep):
    """The tile's grain kept at keep, pulled halfway to the colour rgb."""
    return Image.blend(softened(tile, keep), Image.new("RGBA", tile.size, (*rgb, 255)), 0.55)


def blueprint(cloth, sketch):
    """Blueprint paper: a soft grid on blue; with sketch, the sheet's frame and a house drawing laid out on its
    22 x 16 island: seen from the table's front (+z), u runs to the viewer's right and v towards them, so the sheet
    reads as drawn."""
    image = tinted((52, 92, 150), cloth, 0.25)
    draw = ImageDraw.Draw(image)
    for i in range(0, 32, 4):
        draw.line([(i, 0), (i, 31)], fill=(64, 104, 160, 255))
        draw.line([(0, i), (31, i)], fill=(64, 104, 160, 255))
    if sketch:
        sheet = Image.new("RGBA", (22, 16), (0, 0, 0, 0))
        pen = ImageDraw.Draw(sheet)
        pen.rectangle([1, 1, 20, 14], outline=(150, 180, 214, 255))
        pen.line([(4, 7), (9, 3), (14, 7)], fill=SKETCH)
        pen.rectangle([5, 7, 13, 12], outline=SKETCH)
        pen.rectangle([8, 9, 10, 12], outline=SKETCH)
        pen.rectangle([11, 8, 12, 9], outline=SKETCH)
        for y in (4, 6, 8):
            pen.line([(16, y), (19, y)], fill=SKETCH)
        image.alpha_composite(sheet)
    return image


def roll_end(cloth):
    """Paper whose 3 x 3 corner reads as a rolled sheet's end: darker corners, a hollow centre."""
    image = tinted(PAPER, cloth, 0.3)
    draw = ImageDraw.Draw(image)
    for corner in ((0, 0), (2, 0), (0, 2), (2, 2)):
        draw.point(corner, fill=(176, 160, 130, 255))
    draw.point((1, 1), fill=(120, 104, 80, 255))
    return image


def feather(cloth):
    """White feather with slanted grey barbs."""
    image = tinted((238, 236, 230), cloth, 0.2)
    draw = ImageDraw.Draw(image)
    for x in range(0, 32, 2):
        draw.line([(x, 0), (x + 3, 31)], fill=(206, 204, 200, 255))
    return image


def saw(iron):
    """Iron with a row of teeth on its fourth and fifth rows: the bottom edge of the 5 high blade."""
    image = iron.copy()
    draw = ImageDraw.Draw(image)
    for x in range(0, 32, 2):
        draw.point((x, 4), fill=(70, 74, 82, 255))
        draw.point((x + 1, 3), fill=(70, 74, 82, 255))
    return image
