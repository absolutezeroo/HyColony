"""The miner's hut block (spec 2026-10-02 hut models, § lumberjack and miner; decoration until the miner's job is
ported), built by a one-off script: a timbered mine entrance over a dark gallery with a lantern hanging from its
lintel, a hollow minecart on its rails filled with stone and ore, two ore boulders (one with gold and copper crystals
jutting out, as Hytale's ore blocks), a pick driven into the one behind, on the diagonal towards the cart."""

from brushes import coloured, crystal, metal, painted, stone, wood

MODEL = "Blocks/HyColony/Huts/Miner"
ICON = "Hut_Miner"
PICTURES = frozenset()
# The ore crystals glint now and then, without breathing (glint.py).
GLINT = "Gem"
BREATHE = False
# Node name stem (before the first '_') -> material.
STEMS = {"Post": "frame", "Lintel": "frame", "Brace": "frame", "Void": "void", "Rail": "rail", "Sleeper": "sleeper",
         "Cart": "cart", "Wheel": "iron", "Rock": "rock", "Load": "rock", "Stone": "rock", "Pick": "iron",
         "Chain": "iron", "Lantern": "lantern"}


def material(name, side):
    """The material of a node's face: gold or copper for the crystals, wood for the pick's handle, iron for the
    lantern's cap, then by the name's stem."""
    if name.startswith("Gem"):
        return "gold" if "Gold" in name else "copper"
    return {"Pick_Handle": "handle", "Lantern_Cap": "iron"}.get(name) or STEMS[name.split("_")[0]]


def tiles(assets):
    """Material -> its 32 px tile or brush."""
    return {
        "frame": wood((104, 70, 44), plank=99), "void": painted(lambda x, y, w, h, side: coloured((22, 20, 24), 1)),
        "rail": metal((120, 124, 132)), "sleeper": wood((112, 80, 52), plank=99), "cart": wood((132, 92, 58), plank=3),
        "iron": metal((84, 86, 94)), "rock": stone((122, 118, 112)), "handle": wood((140, 96, 58), plank=99),
        "gold": crystal((255, 236, 150), (232, 182, 58), (150, 96, 20)),
        "copper": crystal((255, 190, 150), (214, 112, 64), (120, 52, 28)),
        "lantern": crystal((255, 236, 170), (246, 172, 64), (160, 84, 24)),
    }
