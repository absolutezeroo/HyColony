"""The town hall's hut block (spec 2026-10-02 hut models): an official lectern holding the colony's register on a
plush red carpet, the colony's banner on its mast and a bronze bell on its post, built in Blockbench. Its materials:
painted wood, metal (bronze, gold, the iron clapper), cloth and paper brushes, a few Hytale cloth tints, plus the
painted details only this model has (the carpet's tufted pile, written pages, the banner's emblem)."""

from PIL import ImageDraw

from brushes import as_tile, cloth, metal, paper, wood
from materials import feather, tinted

RED = (150, 36, 38)

MODEL = "Blocks/HyColony/Huts/TownHall"
ICON = "Hut_TownHall"
GOLD = (226, 184, 74, 255)
# The banner hangs in pleats: STRIPS 2 wide strips turned alternately (Banner_<i>), each showing its two columns of the
# emblem from the bar down (the painter cuts each to its strip's length; the outer strips, longer, make the forked
# tail). BANNER_ROWS: the longest strip.
STRIPS, BANNER_ROWS = 4, 19
# Materials drawn for their island, never turned with the wood grain.
PICTURES = frozenset({"page"} | {f"banner_{i}" for i in range(STRIPS)})
# Node name -> material, for the nodes no rule below covers.
NAMED = {"Carpet_Pile": "plush", "Book_Cover": "leather", "Ink": "ink", "Quill": "shaft", "Quill_Vane": "feather",
         "Quill_Tip": "feather", "Mast_Finial": "gold", "Bell_Hanger": "rope", "Bell_Clapper": "iron"}
# Node name prefix -> material.
PREFIXES = (("Lectern", "wood"), ("Desk", "wood"), ("Mast", "wood"), ("Bell_Post", "wood"), ("Bell_Arm", "wood"),
            ("Banner_Bar", "wood"), ("Page", "page"), ("Banner", "cloth"), ("Bell", "bronze"),
            ("Carpet_Fringe", "gold"))


def material(name, side):
    """The material of a node's face: each banner strip shows its part of the emblem on its broad faces, plain red
    cloth elsewhere; the carpet's base shows a gold trim around the pile on top, red cloth on its sides."""
    if name.startswith("Banner_") and name[7:].isdigit() and side in ("front", "back"):
        return name.lower()
    if name == "Carpet_Base":
        return "gold" if side == "top" else "cloth"
    if name in NAMED:
        return NAMED[name]
    return next(m for prefix, m in PREFIXES if name.startswith(prefix))


def tiles(assets):
    """Material -> its 32 px tile or brush."""
    def tile(name):
        return assets.image("Common/BlockTextures/" + name + ".png").crop((0, 0, 32, 32))

    red = as_tile(cloth(RED, folds=0.04))
    return {
        "wood": wood((92, 52, 36)), "bronze": metal((184, 124, 60)), "gold": metal(GOLD[:3]),
        "iron": metal((76, 78, 86)),
        "plush": plush(red), "cloth": cloth(RED), **banner_strips(red),
        "leather": tinted((104, 40, 34), tile("Cloth_Red"), 0.2), "page": page(),
        "ink": tinted((40, 48, 86), tile("Cloth_Black"), 0.2),
        "shaft": tinted((204, 198, 186), tile("Cloth_White"), 0.2),
        "feather": feather(tile("Cloth_White")), "rope": cloth((150, 116, 72), folds=0.03),
    }


def plush(red):
    """Red pile in staggered tufts: each a lit tip over a shaded root, every third pixel."""
    image = red.copy()
    draw = ImageDraw.Draw(image)
    for y in range(0, 32, 3):
        for x in range((y // 3) % 3, 32, 3):
            draw.point((x, y), fill=(188, 62, 60, 255))
            draw.point((x, y + 1), fill=(112, 24, 28, 255))
    return image


def page():
    """Cream paper (the paper brush laid out for a page's 6 x 8 island) written in short brown lines (its edges, the
    first row, stay blank)."""
    image = as_tile(paper((230, 220, 194)))
    image.paste(paper((230, 220, 194))(6, 8, "top"), (0, 0))
    draw = ImageDraw.Draw(image)
    for y in range(2, 8, 2):
        draw.line([(1, y), (4, y)], fill=(120, 96, 70, 255))
    return image


def banner_strips(red):
    """banner_<i> -> strip i's tile: its two columns of the whole banner (8 wide, gold trims under the bar and above
    the tails, a gold house, the colony's emblem), from the bar down. Seen from +z, strip 0 is the viewer's left."""
    whole = red.crop((0, 0, 2 * STRIPS, BANNER_ROWS))
    draw = ImageDraw.Draw(whole)
    draw.line([(0, 1), (7, 1)], fill=GOLD)
    draw.line([(0, 13), (7, 13)], fill=GOLD)
    draw.polygon([(1, 7), (3, 4), (4, 4), (6, 7)], fill=GOLD)
    draw.rectangle([2, 7, 5, 10], fill=GOLD)
    draw.rectangle([3, 9, 4, 10], fill=(110, 26, 28, 255))
    strips = {}
    for i in range(STRIPS):
        strip = red.copy()
        strip.paste(whole.crop((2 * i, 0, 2 * i + 2, BANNER_ROWS)), (0, 0))
        strips[f"banner_{i}"] = strip
    return strips
