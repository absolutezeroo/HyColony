"""Draws the red wax seals of HyColony's and HyLens' windows in MineColonies' wax style (mc_icon.py): our own symbols
in place of MC's seal textures, same names, sizes and meanings, plus HyLens' seals MC has no equivalent for.

Run once, then commit the outputs (plugin/ and hylens/plugin/ resources): the build never runs it.

    python tools/ui/seals.py

A symbol is drawn in any colour on a scratch sprite (pixelstudio calls), then pressed into the seal; a shape erased
with None stays raised wax inside the recess. Drawing tips: broad filled shapes read better than thin rings; a 45-degree
stroke reads best 2 px wide (rows of 2); keep 3 px from the edge (press drops anything closer).
"""

from pathlib import Path

from mc_icon import export, seal

ROOT = Path(__file__).resolve().parents[2]
HYCOLONY = ROOT / "plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/Mc"
HYLENS = ROOT / "hylens/plugin/src/main/resources/Common/UI/Custom/Pages/HyLens/Mc"
INK = "#fff"
# colonist_wax_*_smaller, the gender seal of MC's citizen window.
COLONIST_SEAL_SIZE = 30


def home(t):
    """A house: roof and walls pressed in, the door left raised."""
    t.polygon([(8, 3), (13, 8), (12, 8), (12, 13), (4, 13), (4, 8), (3, 8)], INK)
    t.rect(7, 10, 9, 13, None)


def citizens(t):
    """A citizen's bust, as MC's seal: a round head over broad shoulders."""
    t.circle(8, 5, 2, INK, fill=True)
    t.polygon([(4, 13), (4, 10), (6, 8), (10, 8), (12, 10), (12, 13)], INK)


def information(t):
    """A bold question mark, as MC's help seal: the hook's top and its curl to the right, the stem, the dot."""
    t.rect(6, 3, 10, 4, INK)
    t.rect(5, 4, 6, 6, INK)
    t.rect(10, 4, 11, 7, INK)
    t.rect(8, 7, 10, 8, INK)
    t.rect(8, 9, 9, 10, INK)
    t.rect(8, 12, 9, 13, INK)


def permissions(t):
    """A key: a round bow with its hole left raised, the shaft to the right, two teeth."""
    t.circle(5, 7, 2, INK, fill=True)
    t.px(5, 7, None)
    t.rect(7, 6, 13, 7, INK)
    t.rect(10, 8, 10, 9, INK)
    t.rect(12, 8, 12, 10, INK)


def settings(t):
    """A gear: a round body, four broad teeth and four small diagonal ones, its hub left raised."""
    t.circle(8, 8, 3, INK, fill=True)
    for x0, y0, x1, y1 in ((7, 3, 9, 4), (7, 12, 9, 13), (3, 7, 4, 9), (12, 7, 13, 9),
                           (4, 4, 5, 5), (11, 4, 12, 5), (4, 11, 5, 12), (11, 11, 12, 12)):
        t.rect(x0, y0, x1, y1, INK)
    t.circle(8, 8, 1, None, fill=True)


def stats(t):
    """Three bars rising to the right."""
    t.rect(4, 10, 5, 13, INK)
    t.rect(7, 7, 8, 13, INK)
    t.rect(10, 4, 11, 13, INK)


def work_orders(t):
    """A hammer tilted at 45 degrees: a 2 px handle from the bottom left, the head across its top."""
    for i in range(5):
        t.rect(4 + i, 12 - i, 5 + i, 12 - i, INK)
    t.polygon([(7, 5), (10, 2), (14, 6), (11, 9)], INK)


def colonies(t):
    """HyLens: a colony flag, a pole on a foot, its banner flying right with a swallowtail."""
    t.rect(4, 3, 5, 12, INK)
    t.rect(3, 13, 7, 13, INK)
    t.polygon([(6, 3), (12, 3), (10, 5), (12, 7), (6, 7)], INK)


def lens(t):
    """HyLens: a magnifying glass, the whole glass pressed in and a handle down to the right."""
    t.circle(7, 7, 3, INK, fill=True)
    for i in range(3):
        t.rect(9 + i, 9 + i, 11 + i, 10 + i, INK)


def male(t):
    """The male sign on a 30 px seal: a ring, its middle left raised, an arrow up to the right."""
    t.circle(12, 17, 6, INK, fill=True)
    t.circle(12, 17, 3, None, fill=True)
    for i in range(6):
        t.rect(16 + i, 11 - i, 17 + i, 12 - i, INK)
    t.rect(18, 6, 23, 7, INK)
    t.rect(22, 6, 23, 11, INK)


def female(t):
    """The female sign on a 30 px seal: a ring, its middle left raised, a cross below."""
    t.circle(15, 11, 6, INK, fill=True)
    t.circle(15, 11, 3, None, fill=True)
    t.rect(14, 17, 16, 25, INK)
    t.rect(11, 21, 19, 22, INK)


SEALS = [
    (HYCOLONY / "red_wax_home@2x.png", home, 17),
    (HYCOLONY / "red_wax_citizens@2x.png", citizens, 17),
    (HYCOLONY / "red_wax_information@2x.png", information, 17),
    (HYCOLONY / "red_wax_permissions@2x.png", permissions, 17),
    (HYCOLONY / "red_wax_settings@2x.png", settings, 17),
    (HYCOLONY / "red_wax_stats@2x.png", stats, 17),
    (HYCOLONY / "red_wax_work_orders@2x.png", work_orders, 17),
    (HYCOLONY / "colonist_wax_male_smaller@2x.png", male, COLONIST_SEAL_SIZE),
    (HYCOLONY / "colonist_wax_female_smaller@2x.png", female, COLONIST_SEAL_SIZE),
    (HYLENS / "red_wax_colonies@2x.png", colonies, 17),
    (HYLENS / "red_wax_citizens@2x.png", citizens, 17),
    (HYLENS / "red_wax_lens@2x.png", lens, 17),
]

if __name__ == "__main__":
    for out, draw, size in SEALS:
        export(seal(draw, size), out)
        print(out.relative_to(ROOT))
