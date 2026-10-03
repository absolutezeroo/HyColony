"""The build goggles (spec 2026-10-02 hut models, § goggles): architect's goggles built in Blockbench as a head armour
piece (root "Head" as Hytale's Armors/Diving_Crude/Head): a stitched leather band ringing the head (Band*), two
leather eyecups with brass rims and glowing cyan crystal lenses, a brass bridge, a hinged brass loupe flipped up on
the forehead and a brass dial."""

from PIL import ImageDraw

from brushes import crystal, metal
from conditions import MAINTAINED
from materials import BRASS, CYAN, LEATHER, leather

MODEL = "Items/HyColony/Build_Goggles"
ICON = "Build_Goggles"
# The stitches run along the band's 5 high sides: drawn for them, never turned.
PICTURES = frozenset({"stitched"})
# Painted in layers (spec 2026-10-03 blockpaint surfaces): an architect's own tool, kept with care.
CONDITION, SEED = MAINTAINED, 11
FAMILY = {"leather": "leather", "stitched": "leather", "brass": "cuprous"}


def material(name, side):
    """Stitched leather on the band's sides, plain leather for its other faces and the eyecups, crystal for the
    lenses, brass for the rest."""
    if name.startswith("Band"):
        return "stitched" if side not in ("top", "bottom") else "leather"
    if name.startswith("Cup"):
        return "leather"
    if name.startswith(("Lens", "Loupe_Lens")):
        return "crystal"
    return "brass"


def tiles(assets):
    """Material -> its 32 px tile or brush."""
    hide = leather(LEATHER, assets.image("Common/BlockTextures/Cloth_Black.png").crop((0, 0, 32, 32)))
    return {"leather": hide, "stitched": stitched(hide), "brass": metal(BRASS), "crystal": crystal(*CYAN)}


def stitched(hide):
    """Leather with a dashed light seam on rows 1 and 3 of the band's 5 high sides, inside its edges."""
    image = hide.copy()
    draw = ImageDraw.Draw(image)
    for x in range(0, 32, 3):
        for y in (1, 3):
            draw.point((x, y), fill=(150, 112, 76, 255))
    return image
