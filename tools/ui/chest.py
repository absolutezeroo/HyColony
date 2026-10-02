"""Draws the hut window's inventory summary button (MC "allinventory", textures/gui/chest.png, 25 x 25): a parchment
card like MC's, holding Hytale's crude chest (Furniture_Crude_Chest_Small) seen from the front, not MC's chest.

Run once, then commit the output (plugin/ resources): the build never runs it.

    python tools/ui/chest.py
"""

from pathlib import Path

from mc_icon import Sprite, export

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/Mc/chest@2x.png"
SIZE = 25

# The card, in MC chest.png's colours: cream parchment, a tan border lit on the top/left.
CREAM, CREAM_LIGHT, BORDER_LIT, BORDER_DARK = "#fff1c4", "#fffcdf", "#c09762", "#ac845a"
# Hytale's crude chest, read on Icons/ItemsGenerated/Furniture_Crude_Chest_Small.png: the browns quantised to 8
# colours, the seams and the brass close to its pixels; the nail grey is ours (the icon has no grey).
LID, LID_LIGHT = "#593d24", "#6b532e"
FRONT, FRONT_SEAM = "#3d2818", "#291a11"
POST, LID_GAP = "#312014", "#19100b"
BRASS, NAIL = "#8a7d3a", "#8a857a"


def card(s):
    """The parchment card: cream inside, a 1 px border, its four corners cut (MC cuts three)."""
    s.rect(0, 0, SIZE - 1, SIZE - 1, CREAM)
    s.rect(1, 1, SIZE - 2, 1, CREAM_LIGHT)
    s.rect(0, 0, SIZE - 1, 0, BORDER_LIT)
    s.rect(0, 0, 0, SIZE - 1, BORDER_LIT)
    s.rect(0, SIZE - 1, SIZE - 1, SIZE - 1, BORDER_DARK)
    s.rect(SIZE - 1, 0, SIZE - 1, SIZE - 1, BORDER_DARK)
    for x, y in ((0, 0), (SIZE - 1, 0), (0, SIZE - 1), (SIZE - 1, SIZE - 1)):
        s.px(x, y, None)


def chest(s):
    """The crude chest seen from the front, as MC lays its chest out: the lighter lid with one plank seam, a dark seam
    under it, the planked body, darker corner posts nailed at their ends and under the lid, the brass handle hanging
    in the middle."""
    s.rect(4, 6, 20, 9, LID_LIGHT)
    s.line(4, 7, 20, 7, LID)
    s.rect(4, 10, 20, 19, FRONT)
    s.line(4, 10, 20, 10, LID_GAP)
    s.line(4, 14, 20, 14, FRONT_SEAM)
    s.line(4, 17, 20, 17, FRONT_SEAM)
    s.rect(4, 6, 4, 19, POST)
    s.rect(20, 6, 20, 19, POST)
    for x, y in ((4, 6), (20, 6), (4, 10), (20, 10), (4, 19), (20, 19)):
        s.px(x, y, NAIL)
    for x, y in ((10, 12), (10, 13), (11, 14), (12, 14), (13, 14), (14, 13), (14, 12)):
        s.px(x, y, BRASS)


def summary_button():
    """The button: the card with the chest on it."""
    s = Sprite(SIZE, SIZE)
    card(s)
    chest(s)
    return s


if __name__ == "__main__":
    export(summary_button(), OUT)
    print(OUT.relative_to(ROOT))
