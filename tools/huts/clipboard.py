"""The clipboard (spec 2026-10-02 hut models, § clipboard): MineColonies' clipboard as a held item (root
"R-Attachment" as Hytale's Items/Consumables/Scrolls/Map, held like it): a red-brown wooden board, a slightly askew
stack of cream paper written with the colony's requests, an iron clip (plate, rolled hinge, raised lever, two brass
rivets) and an ochre pencil held on the right by a leather loop."""

from PIL import ImageDraw

from brushes import as_tile, metal, paper, wood
from conditions import MAINTAINED
from icons import turned
from materials import BRASS, LEATHER, PAPER, leather

MODEL = "Items/HyColony/Clipboard"
ICON = "Clipboard"
# Upright and seen three-quarter from the written side, slightly tilted like Hytale's map icon.
ICON_VIEW = turned(-70, 12, -8)
# The written page is drawn for the paper's front island, never turned.
PICTURES = frozenset({"written"})
# Painted in layers (spec 2026-10-03 blockpaint surfaces): a clipboard carried every day, kept with care.
CONDITION, SEED = MAINTAINED, 11
FAMILY = {"written": "paper", "tip": "wood", "leather": "leather", "brass": "cuprous"}
INK = (112, 92, 78, 255)
TITLE = (150, 64, 50, 255)
TICK = (72, 132, 64, 255)


def material(name, side):
    """The written page on the paper's front (+x, "right"), plain paper elsewhere on it, then by name."""
    if name == "Paper":
        return "written" if side == "right" else "sheets"
    if name.startswith(("Clip_Plate", "Clip_Lever")):
        return "iron"
    return {"Board": "board", "Clip_Hinge": "hinge", "Pencil": "pencil", "Pencil_Tip": "tip",
            "Pencil_Loop": "leather"}.get(name, "brass")


def tiles(assets):
    """Material -> its 32 px tile or brush."""
    return {
        "board": wood((128, 74, 48), plank=99), "sheets": paper((206, 192, 160)), "written": written(),
        "iron": metal((150, 154, 164)), "hinge": metal((112, 116, 126)), "brass": metal(BRASS),
        "pencil": wood((206, 160, 66), plank=99), "tip": tip(),
        "leather": leather(LEATHER, assets.image("Common/BlockTextures/Cloth_Black.png").crop((0, 0, 32, 32))),
    }


def written():
    """The 15 x 19 front sheet: a red title, then the requests, each a box and a few words of faded ink; the first
    two are ticked off."""
    image = as_tile(paper(PAPER))
    draw = ImageDraw.Draw(image)
    draw.line([(4, 2), (10, 2)], fill=TITLE)
    for row, words in enumerate(((3, 4), (2, 3), (4, 2, 2), (3, 3), (2, 4), (4, 3), (3,))):
        y = 5 + 2 * row
        draw.point((2, y), fill=TICK if row < 2 else INK)
        x = 4
        for length in words:
            draw.line([(x, y), (x + length - 1, y)], fill=INK)
            x += length + 1
    return image


def tip():
    """The sharpened end: bare wood, its last row and its point graphite."""
    cut = wood((214, 178, 128), plank=99)

    def brush(w, h, side):
        image = cut(w, h, side)
        lead = (0, 0, w - 1, h - 1) if side == "bottom" else (0, h - 1, w - 1, h - 1)
        ImageDraw.Draw(image).rectangle(lead, fill=(62, 60, 64, 255))
        return image
    return brush
