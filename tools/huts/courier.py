"""The courier's hut block (spec 2026-10-02 hut models): a post office sorting desk built in Blockbench, two blocks
tall: a sturdy desk on thick legs with a drawer case (two drawers, brass knobs) and a low shelf of parcels; a
pigeonhole cabinet of four by four cells holding letters, a scroll and a small parcel, under a cornice with an
envelope sign; on the desk a leather satchel, two tied parcels, a stamp and its ink pad, a sealed letter."""

from PIL import ImageDraw

from brushes import as_tile, coloured, jitter, metal, painted, paper, wood
from materials import BRASS, LEATHER, leather

MODEL = "Blocks/HyColony/Huts/Courier"
ICON = "Hut_Courier"
KRAFT = (178, 134, 88)
TWINE = (226, 206, 160)
# Materials drawn for their island, never turned.
PICTURES = frozenset({"sign"})
# Node name prefix -> material.
PREFIXES = (("Leg", "frame"), ("Drawer_Knob", "brass"), ("Drawer_Case", "cabinet"), ("Drawer", "frame"),
            ("Desk", "planks"), ("Cab", "cabinet"), ("Shelf", "cabinet"), ("Divider", "cabinet"),
            ("Letters", "letters"), ("Letter_Desk", "letters"), ("Scroll", "scroll"), ("Parcel", "parcel"),
            ("Satchel_Buckle", "brass"), ("Satchel_Strap", "strap"), ("Satchel", "leather"), ("Stamp_Base", "brass"),
            ("Stamp", "handle"), ("Ink_Pad", "pad"), ("Sign", "frame"))


def material(name, side):
    """The material of a node's face: the envelope sign's front, the ink on the pad's top, then by name."""
    if name == "Sign" and side == "front":
        return "sign"
    if name == "Ink_Pad" and side == "top":
        return "ink"
    return next(m for prefix, m in PREFIXES if name.startswith(prefix))


def tiles(assets):
    """Material -> its 32 px tile or brush."""
    hide = leather(LEATHER, assets.image("Common/BlockTextures/Cloth_Black.png").crop((0, 0, 32, 32)))
    return {
        "frame": wood((112, 76, 46)), "planks": wood((156, 114, 70), plank=6), "cabinet": wood((104, 70, 44)),
        "handle": wood((140, 98, 60), plank=99), "brass": metal(BRASS), "letters": letters(),
        "scroll": paper((232, 222, 196)), "parcel": parcel(), "leather": hide,
        "strap": leather((84, 50, 30), hide), "pad": metal((44, 44, 52), streak=0.03),
        "ink": painted(lambda x, y, w, h, side: coloured((150, 34, 40), 1 + 0.06 * jitter(x * 7 + y, 40))),
        "sign": sign(),
    }


def letters():
    """A stack of letters: their edges in thin lines on the sides, the top envelope with its red wax seal."""
    def rule(x, y, w, h, side):
        if side in ("top", "bottom"):
            if (x, y) == (w // 2, h // 2):
                return (168, 40, 40, 255)
            return coloured((236, 228, 206), 1 - (0.08 if x in (0, w - 1) or y in (0, h - 1) else 0))
        return coloured((236, 228, 206), 0.86 if y % 2 else 1.0)
    return painted(rule)


def parcel():
    """Brown kraft paper tied with twine: the string crosses the top and runs down the middle of each side."""
    def rule(x, y, w, h, side):
        on_string = x == w // 2 or (side in ("top", "bottom") and y == h // 2)
        if on_string:
            return coloured(TWINE, 1 + 0.04 * jitter(x + y * 3, 41))
        return coloured(KRAFT, 1 + 0.05 * jitter(x * 13 + y * 7, 42) + (0.04 if (x + y) % 4 == 0 else 0))
    return painted(rule)


def sign():
    """The post sign's 12 x 4 front: a dark wooden board with a cream envelope and its red seal."""
    image = as_tile(wood((92, 60, 38), plank=99))
    draw = ImageDraw.Draw(image)
    draw.rectangle([4, 1, 7, 2], fill=(236, 228, 206, 255))
    draw.point((5, 1), fill=(196, 184, 160, 255))
    draw.point((6, 1), fill=(196, 184, 160, 255))
    draw.point((6, 2), fill=(168, 40, 40, 255))
    return image
