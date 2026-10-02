"""The residence's hut block (spec 2026-10-02 hut models): a cosy stone hearth built in Blockbench, its fire glowing on
a bed of embers under crossed logs, a copper kettle hanging from an iron crane, a wooden mantel holding a candle, a
family portrait and a clay jar, a stack of firewood and a poker. The flames themselves are particles (the item's
Particles, on the empty node Flame); the embers and the candle's flame glow (fullbright)."""

from PIL import Image, ImageDraw

from brushes import as_tile, crystal, embers, metal, paper, stone, terracotta, wood
from materials import BRASS

MODEL = "Blocks/HyColony/Huts/Residence"
ICON = "Hut_Residence"
# Materials drawn for their island, never turned with the wood grain.
PICTURES = frozenset({"log_end", "portrait", "clock_face"})
BARK = (84, 58, 40)
# The firebox's faces, darkened by soot: node -> its sides inside the fire.
SOOTED = {"Back": ("front",), "Jamb_L": ("right",), "Jamb_R": ("left",), "Mantel_Beam": ("bottom",)}
# Node name prefix -> material, for the nodes the rules of material leave.
PREFIXES = (("Hearth", "flagstone"), ("Jamb", "stone"), ("Back", "stone"), ("Breast", "stone"), ("Chimney", "stone"),
            ("Cap", "stone"), ("Mantel_Beam", "beam"), ("Mantel", "mantel"), ("Embers", "embers"),
            ("Candle_Flame", "flame"), ("Candle_Holder", "brass"), ("Candle", "wax"), ("Firedog", "iron"),
            ("Crane", "iron"), ("Kettle_Bail", "iron"), ("Kettle", "copper"), ("Poker", "iron"), ("Jar", "clay"),
            ("Clock_Pendulum", "brass"), ("Guard", "iron"))


def material(name, side):
    """The material of a node's face: soot inside the firebox, the cut rings on a log's ends and bark around it, the
    painting on the portrait's front and its wooden frame elsewhere, then by name."""
    if side in SOOTED.get(name, ()):
        return "soot"
    # The logs on the fire lie across it (cut ends left and right); the stacked ones show their ends to the player.
    if name.startswith(("Log", "Pile")):
        return "log_end" if side in (("left", "right") if name.startswith("Log") else ("front", "back")) else "bark"
    if name == "Portrait":
        return "portrait" if side == "front" else "frame"
    if name == "Clock":
        return "clock_face" if side == "front" else "beam"
    return next(m for prefix, m in PREFIXES if name.startswith(prefix))


def tiles(_assets):
    """Material -> its 32 px tile or brush."""
    return {
        "stone": stone((150, 140, 128), chunk=(5, 3)), "flagstone": stone((114, 106, 100), chunk=(8, 6)),
        "soot": stone((64, 56, 52), chunk=(5, 3)),
        "beam": wood((92, 58, 36)), "mantel": wood((118, 78, 48)), "frame": wood((150, 104, 56), plank=99),
        "bark": wood(BARK, plank=3), "log_end": log_end(),
        "embers": embers(), "flame": crystal((255, 242, 176), (255, 184, 72), (232, 104, 34)),
        "wax": paper((238, 228, 206), aged=(214, 196, 160)), "brass": metal(BRASS), "iron": metal((72, 72, 80)),
        "copper": metal((184, 104, 62)), "clay": terracotta((156, 92, 60)), "portrait": portrait(),
        "clock_face": clock_face(),
    }


def log_end(size=3):
    """A log's cut end, laid out for its size x size island: a ring of bark round light, freshly cut wood (at 3 x 3,
    one pixel of it)."""
    image = as_tile(wood((206, 168, 118), plank=99))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, size - 1, size - 1], outline=(*BARK, 255))
    return image


def clock_face():
    """The wall clock's 6 x 7 front: a dark wooden case round a cream dial, its hands at ten past ten."""
    image = as_tile(wood((92, 58, 36), plank=99))
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 1, 4, 4], fill=(232, 222, 196, 255))
    draw.point((2, 2), fill=(40, 34, 30, 255))
    draw.point((3, 2), fill=(40, 34, 30, 255))
    draw.point((2, 3), fill=(176, 140, 64, 255))
    draw.line([(1, 6), (4, 6)], fill=(176, 140, 64, 255))
    return image


def portrait():
    """The family portrait laid out for its 8 x 7 front: a gilt frame round a little landscape (sky, a green hill, a
    cottage with a red roof)."""
    image = Image.new("RGBA", (32, 32), (206, 168, 84, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 1, 6, 5], fill=(150, 196, 222, 255))
    draw.rectangle([1, 4, 6, 5], fill=(98, 150, 72, 255))
    draw.point((1, 3), fill=(98, 150, 72, 255))
    draw.rectangle([3, 3, 4, 4], fill=(232, 220, 196, 255))
    draw.line([(3, 2), (4, 2)], fill=(168, 54, 44, 255))
    return image
