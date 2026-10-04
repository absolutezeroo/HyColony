"""The lumberjack's hut block (spec 2026-10-02 hut models, § lumberjack and miner; decoration until the forester's job
is ported), built by a one-off script: a stump split in two by a felling axe driven into its crack, its roots
spreading, a pile of three logs behind, a sapling on its mound, wood chips and a split log on the ground."""

import math

from brushes import coloured, jitter, metal, painted, stone, wood
from composer import Composer
from conditions import FOREST, WORN
from illustration import Illustration
from materials import leaf

MODEL = "Blocks/HyColony/Huts/Lumberjack"
ICON = "Hut_Lumberjack"
WOOD = (198, 158, 104)
PICTURES = frozenset()
# Painted in layers (spec 2026-10-03 blockpaint surfaces): a felling site, hard worked, in the forest.
CONDITION, ENVIRONMENT, SEED = WORN, FOREST, 11
# Illustrated and composed (specs 2026-10-04 blockpaint illustration, composer): the axe's blade the accent, the split
# log next, the mound behind.
IMPORTANCE = {"Axe_Blade": 3, "Axe_Blade_Wide": 3, "Axe_Edge": 3, "Split": 2, "Mound": 0}
ILLUSTRATION, COMPOSER = Illustration(), Composer()
FAMILY = {"rings_b": "wood", "rings_f": "wood", "rings": "wood", "crack": "wood", "bark": "wood"}


def material(name, side):
    """The material of a node's face: rings on the cut faces, the crack between the stump's halves, then by name."""
    if name.startswith("Stump") and side == "top":
        return "rings_" + name[-1].lower()
    if (name, side) in (("Stump_B", "front"), ("Stump_F", "back")):
        return "crack"
    if name.startswith(("Log", "Split")) and side in ("front", "back"):
        return "rings"
    if name.startswith(("Stump", "Root", "Log", "Split")):
        return "bark"
    if name == "Axe_Edge":
        return "edge"
    if name.startswith(("Axe_Eye", "Axe_Blade")):
        return "steel"
    if name.startswith(("Axe", "Sapling")):
        return "handle"
    if name.startswith("Leaves"):
        return "leaf"
    if name == "Mound":
        return "soil"
    return "chip"


def tiles(assets):
    """Material -> its 32 px tile or brush."""
    # The split stump's halves: row h of the back half (one past its last, h - 1) and row -1 of the front half are
    # both centred on the middle of the crack (z -1.5), so their rings share one centre.
    return {
        "rings_b": rings(WOOD, lambda h: h), "rings_f": rings(WOOD, lambda h: -1), "rings": rings(WOOD),
        "crack": crack(), "bark": bark(), "edge": metal((226, 232, 240)), "steel": metal((176, 182, 192)),
        "handle": wood((140, 96, 58), plank=99), "leaf": leaf((86, 148, 58)),
        "soil": stone((92, 66, 46), chunk=(2, 2)), "chip": wood((206, 168, 112), plank=99),
    }


def rings(base, centre=None):
    """A cut log face: light wood in rings round its middle (or round row centre(h), for half a split stump), the bark
    round the edge."""
    def rule(x, y, w, h, side):
        if min(x, y, w - 1 - x, h - 1 - y) == 0:
            return coloured((84, 56, 36), 1 + 0.06 * jitter(x + y, 61))
        r = math.hypot(x - (w - 1) / 2, y - (centre(h) if centre else (h - 1) / 2))
        return coloured(base, (0.86 if round(r) % 2 else 1.04) + 0.03 * jitter(x * 7 + y * 3, 62))
    return painted(rule)


def bark():
    """Bark: dark ridges running up the trunk, lighter in their middle."""
    def rule(x, y, w, h, side):
        return coloured((96, 64, 42), (0.78 if x % 3 == 0 else 1.05) + 0.05 * jitter(x * 13 + y // 3, 63))
    return painted(rule)


def crack():
    """The inside of the split: dark torn heartwood, splinters running down it."""
    def rule(x, y, w, h, side):
        return coloured((70, 46, 30), (1.25 if x % 4 == 1 else 0.9) + 0.08 * jitter(x * 5 + y // 2, 64))
    return painted(rule)
