"""The medium quarry's hut block (spec 2026-10-02 hut models, § quarries, plantation and florist; decoration until the
quarrier's job is ported), built by a one-off script: three cut blocks stacked on a pallet and bound with rope, a
sledgehammer standing head down against them, a hollow bucket holding a pick handle and a chisel, rubble."""

from brushes import metal, stone, wood
from materials import BUCKET_INSIDE, CUT_STONE, ROCK, rope

MODEL = "Blocks/HyColony/Huts/Quarry_Medium"
ICON = "Hut_Quarry_Medium"
PICTURES = frozenset()
# Node name stem (before the first '_') -> material.
STEMS = {"Runner": "pallet", "Slat": "pallet", "Block": "cut", "Rope": "rope", "Sledge": "head", "Bucket": "bucket",
         "Band": "iron", "Rubble": "rock", "Chip": "rock"}


def material(name, side):
    """The bucket's inside dark, the handles wooden, the chisel iron, then by the name's stem."""
    if (name, side) in BUCKET_INSIDE:
        return "inside"
    special = {"Sledge_Handle": "handle", "Bucket_Pick": "handle", "Bucket_Chisel": "iron"}
    return special.get(name) or STEMS[name.split("_")[0]]


def tiles(assets):
    """Material -> its 32 px tile or brush."""
    return {
        "cut": stone(CUT_STONE, chunk=(8, 6)), "rock": stone(ROCK), "pallet": wood((150, 112, 72), plank=99),
        "rope": rope(), "iron": metal((96, 98, 106)), "head": metal((76, 78, 86)),
        "handle": wood((150, 108, 66), plank=99), "bucket": wood((128, 90, 56), plank=3),
        "inside": wood((70, 48, 30), plank=3),
    }
