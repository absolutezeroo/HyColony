"""The build tool (spec 2026-10-02 hut models, § build tool): an architect's hammer built in Blockbench as a held item
(root "R-Attachment" as Hytale's Items/Tools/Hammer and Items/Weapons/Wand): a wooden handle with a brass pommel,
a leather-wrapped grip and a brass collar, a brushed iron head with a light steel striking face, a stepped peen and
two brass bands, and a cut cyan crystal set through the head in a brass bezel."""

from PIL import ImageDraw

from brushes import crystal, metal, wood
from materials import BRASS, CYAN, LEATHER, leather
from icons import turned

MODEL = "Items/HyColony/Build_Tool"
ICON = "Build_Tool"
# As Hytale's tool icons (Icons/ItemsGenerated/Tool_Hammer_*): laid diagonally, head top left, seen three-quarter
# from the gem's side.
ICON_VIEW = turned(70, 15, 45)
PICTURES = frozenset()


def material(name, side):
    """Wood for the handle, wrapped leather for the grip, iron for the head and peen, steel for the striking face,
    crystal for the gem, brass for the rest."""
    if name.startswith(("Head", "Peen")):
        return "iron"
    if name.startswith("Gem"):
        return "crystal"
    return {"Handle": "wood", "Grip": "wrap", "Face": "steel"}.get(name, "brass")


def tiles(assets):
    """Material -> its 32 px tile or brush."""
    def tile(path):
        return assets.image("Common/" + path).crop((0, 0, 32, 32))

    return {
        "wood": wood((132, 84, 50), plank=99),
        "wrap": wrap(leather(LEATHER, tile("BlockTextures/Cloth_Black.png"))),
        "iron": metal((122, 128, 140)), "steel": metal((196, 202, 210), streak=0.05),
        "brass": metal(BRASS), "crystal": crystal(*CYAN),
    }


def wrap(hide):
    """Leather wound round the grip: darker diagonal seams every third pixel."""
    image = hide.copy()
    draw = ImageDraw.Draw(image)
    for x in range(-32, 32, 3):
        draw.line([(x, 31), (x + 31, 0)], fill=(70, 42, 26, 255))
    return image
