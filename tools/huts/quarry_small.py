"""The simple quarry's hut block (spec 2026-10-02 hut models, § quarries, plantation and florist; decoration until the
quarrier's job is ported), built by a one-off script: a boulder split in two by a row of iron wedges, a mallet and a
chisel on it, a first cut block, chips and rubble at its foot."""

from brushes import coloured, jitter, metal, painted, stone, wood
from composer import Composer
from conditions import TEMPERATE_OUTDOOR, WORN
from illustration import Illustration
from materials import CUT_STONE, ROCK

MODEL = "Blocks/HyColony/Huts/Quarry_Small"
ICON = "Hut_Quarry_Small"
PICTURES = frozenset()
# Painted in layers (spec 2026-10-03 blockpaint surfaces): a quarry face, hard worked, outdoors.
CONDITION, ENVIRONMENT, SEED = WORN, TEMPERATE_OUTDOOR, 11
# Illustrated and composed (specs 2026-10-04 blockpaint illustration, composer): the cut stone the accent, the mallet
# and wedges next, the rocks' feet behind.
IMPORTANCE = {"Cut": 3, "Mallet_Head": 2, "Wedge_1": 2, "Wedge_2": 2, "Wedge_3": 2, "Rock_L_Foot": 0,
              "Rock_R_Foot": 0}
ILLUSTRATION, COMPOSER = Illustration(), Composer()
FAMILY = {"crack": "stone"}


def material(name, side):
    """The split's inner faces, iron for the wedges and the chisel's blade, wood for the mallet, then stone."""
    if (name, side) in (("Rock_L", "right"), ("Rock_R", "left")):
        return "crack"
    if name.startswith(("Wedge", "Chisel_Blade")):
        return "iron"
    if name == "Mallet_Head":
        return "head"
    if name.startswith(("Mallet", "Chisel")):
        return "handle"
    return "cut" if name == "Cut" else "rock"


def tiles(assets):
    """Material -> its 32 px tile or brush."""
    return {
        "rock": stone(ROCK), "crack": crack(), "cut": stone(CUT_STONE, chunk=(8, 6)), "iron": metal((96, 98, 106)),
        "head": wood((112, 76, 46), plank=99), "handle": wood((150, 108, 66), plank=99),
    }


def crack():
    """The inside of a split: dark rough faces, lighter grains."""
    def rule(x, y, w, h, side):
        return coloured((70, 66, 62), (1.2 if (x + 2 * y) % 5 == 0 else 0.92) + 0.08 * jitter(x * 5 + y * 3, 71))
    return painted(rule)
