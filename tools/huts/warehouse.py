"""The warehouse's hut block (spec 2026-10-02 hut models): a stockroom built in Blockbench, two blocks tall: a three
level wooden rack at the back (sacks of grain and a box below, labelled boxes in the middle, bolts of red and blue
cloth and a jug on top); in front, a big labelled crate with the open stock ledger on it, and a hooped barrel holding a
brass scale with a stack of gold coins."""

from PIL import ImageDraw

from brushes import as_tile, cloth, coloured, jitter, metal, painted, terracotta, wood
from materials import BRASS

MODEL = "Blocks/HyColony/Huts/Warehouse"
ICON = "Hut_Warehouse"
CRATE = (168, 126, 80)
STAVE = (138, 92, 56)
# Materials drawn for their island, never turned.
PICTURES = frozenset({"ledger"})
# Node name prefix -> material.
PREFIXES = (("Post", "frame"), ("Rack_Top", "frame"), ("Rack_Shelf", "planks"), ("Rack_Back", "boards"),
            ("Sack", "burlap"), ("Box", "crate"), ("Big_Crate", "crate"), ("Ledger", "leather"),
            ("Barrel", "barrel"), ("Scale_String", "string"), ("Scale", "brass"), ("Coins", "gold"),
            ("Roll_1", "red_cloth"), ("Roll_2", "blue_cloth"), ("Jug", "clay"))


def material(name, side):
    """The material of a node's face: a label on the front of every crate and box, the barrel's lid on its top, the
    open pages on the ledger's top, then by name."""
    if name.startswith(("Box", "Big_Crate")) and side == "front":
        return "label"
    if name == "Barrel" and side == "top":
        return "lid"
    if name == "Ledger" and side == "top":
        return "ledger"
    return next(m for prefix, m in PREFIXES if name.startswith(prefix))


def tiles(_assets):
    """Material -> its 32 px tile or brush."""
    return {
        "frame": wood((96, 64, 40)), "planks": wood((140, 102, 64), plank=6), "boards": wood((118, 82, 52), plank=4),
        "burlap": cloth((176, 146, 98), folds=0.06), "crate": wood(CRATE, plank=3), "label": label(),
        "leather": wood((96, 42, 34), plank=99), "ledger": ledger(), "barrel": barrel(), "lid": wood(STAVE, plank=2),
        "brass": metal(BRASS), "gold": metal((226, 184, 74)), "string": cloth((150, 116, 72), folds=0.02),
        "red_cloth": cloth((150, 40, 42)), "blue_cloth": cloth((52, 82, 150)), "clay": terracotta((158, 96, 62)),
    }


def label():
    """A crate's front: its slats, with a cream paper label in the middle written in two short dark lines."""
    slats = wood(CRATE, plank=3)

    def brush(w, h, side):
        image = slats(w, h, side)
        lw, lh = max(2, w // 2), max(2, h * 2 // 5)
        x0, y0 = (w - lw) // 2, (h - lh) // 2
        draw = ImageDraw.Draw(image)
        draw.rectangle([x0, y0, x0 + lw - 1, y0 + lh - 1], fill=(230, 220, 190, 255))
        for y in range(y0 + 1, y0 + lh - 1, 2):
            draw.line([(x0 + 1, y), (x0 + lw - 2, y)], fill=(96, 74, 56, 255))
        return image
    return brush


def barrel():
    """Barrel staves: vertical boards of slightly different tones, girded by two dark iron hoops on the sides."""
    def rule(x, y, w, h, side):
        if side not in ("top", "bottom") and h > 4 and y in (h // 5, h - 1 - h // 5):
            return coloured((58, 56, 60), 1 + 0.05 * jitter(x, 43))
        k = 1 + 0.08 * jitter(x // 2, 44) + 0.03 * jitter(x * 7 + y * 3, 45) - (0.12 if x % 2 == 0 else 0)
        return coloured(STAVE, k)
    return painted(rule)


def ledger():
    """The open stock ledger's 9 x 7 top: a leather rim round two cream pages written in lines, a dark fold between."""
    image = as_tile(wood((96, 42, 34), plank=99))
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 1, 7, 5], fill=(232, 222, 196, 255))
    draw.line([(4, 1), (4, 5)], fill=(150, 128, 100, 255))
    for y in (2, 4):
        draw.line([(2, y), (3, y)], fill=(110, 90, 70, 255))
        draw.line([(5, y), (6, y)], fill=(110, 90, 70, 255))
    return image
