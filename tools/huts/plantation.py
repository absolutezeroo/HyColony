"""The plantation's hut block (spec 2026-10-02 hut models, § quarries, plantation and florist; decoration until the
planter's job is ported), built by a one-off script: a planter of boards full of soil growing the crops MineColonies'
plantation grows, as Hytale has them (Plant_Reeds_*, bamboo, cactus): reeds with their brown heads, a bundle of bamboo
tied with twine, a cactus in a terracotta pot; a water channel along its front."""

from brushes import cloth, coloured, jitter, painted, stone, terracotta, wood
from materials import leaf, rope

MODEL = "Blocks/HyColony/Huts/Plantation"
ICON = "Hut_Plantation"
PICTURES = frozenset()
# Node name prefix -> material, the first that matches.
PREFIXES = (("Trough", "trough"), ("Channel", "trough"), ("Soil", "soil"), ("Water", "water"),
            ("Reed_Head", "reed_head"), ("Reed_Leaf", "leaf"), ("Reed", "reed"), ("Bamboo_Tie", "twine"),
            ("Bamboo_Leaf", "leaf"), ("Bamboo", "bamboo"), ("Cactus_Pot", "pot"), ("Cactus_Flower", "flower"),
            ("Cactus", "cactus"))


def material(name, side):
    """Soil in the cactus pot's top, then by name."""
    if name == "Cactus_Pot" and side == "top":
        return "soil"
    return next(m for prefix, m in PREFIXES if name.startswith(prefix))


def tiles(assets):
    """Material -> its 32 px tile or brush."""
    return {
        "trough": wood((132, 94, 58), plank=6), "soil": stone((80, 58, 40), chunk=(2, 2)),
        "water": painted(lambda x, y, w, h, side: coloured((64, 128, 196), 1.1 if (x + y) % 7 == 0 else 1)),
        "reed": painted(lambda x, y, w, h, side: coloured((134, 150, 80), 1 + 0.05 * jitter(x + y * 3, 83))),
        "reed_head": cloth((112, 72, 44)), "leaf": leaf((96, 150, 60)), "bamboo": bamboo(), "twine": rope(),
        "pot": terracotta((178, 98, 62)), "cactus": cactus(),
        "flower": painted(lambda x, y, w, h, side: coloured((238, 142, 182), 1.08 if (x + y) % 2 else 0.94)),
    }


def bamboo():
    """Bamboo: green culms with a darker ring at every node, lighter just above it."""
    def rule(x, y, w, h, side):
        if side in ("top", "bottom"):
            return coloured((150, 170, 96), 1)
        k = 0.72 if y % 7 == 0 else 1.1 if y % 7 == 1 else 1 + 0.04 * jitter(x * 5 + y, 81)
        return coloured((112, 156, 62), k)
    return painted(rule)


def cactus():
    """Cactus: green ribs, light spines dotted along them."""
    def rule(x, y, w, h, side):
        if (x + 2 * y) % 5 == 0 and jitter(x * 13 + y * 7, 82) > 0.2:
            return coloured((236, 230, 196), 1)
        return coloured((76, 132, 66), 0.9 if x % 2 else 1.06)
    return painted(rule)
