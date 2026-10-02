"""Draws the side tab icons of HyColony's hut and citizen windows in MineColonies' tooled-leather style (mc_stamp.py):
our own drawings in place of MC's modules/*.png, same names, size (20 x 20) and meanings.

Run once, then commit the outputs (plugin/ resources): the build never runs it.

    python tools/ui/tabs.py

Each icon is a silhouette and its tooled grooves, drawn in any colour with pixelstudio calls; keep 1 px free round the
silhouette for its outline to read.
"""

from pathlib import Path

from mc_icon import export
from mc_stamp import stamp

ROOT = Path(__file__).resolve().parents[2]
HYCOLONY = ROOT / "plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/Mc"
INK = "#fff"


def food():
    """An apple: two lobes, a stem and a leaf; a crease under the stem, a shine on the left."""
    def shape(t):
        t.circle(8, 11, 4, INK, fill=True)
        t.circle(11, 11, 4, INK, fill=True)
        t.rect(9, 4, 9, 7, INK)
        t.polygon([(10, 5), (13, 3), (14, 4), (11, 6)], INK)

    def grooves(t):
        t.line(9, 8, 10, 8, INK)
        t.line(6, 10, 6, 12, INK)

    return stamp(shape, grooves)


def fuel():
    """A flame: a central tongue and two side tongues, the heart of the fire tooled in as one curl."""
    def shape(t):
        t.polygon([(9, 3), (12, 7), (13, 5), (15, 9), (15, 13), (13, 16), (6, 16), (4, 13), (4, 9), (6, 6),
                   (7, 8)], INK)

    def grooves(t):
        t.contour([(10, 9), (11, 12), (9, 14)], INK, close=False)

    return stamp(shape, grooves)


def happiness():
    """A heart, its shine tooled in on the upper left lobe."""
    def shape(t):
        t.circle(7, 7, 3, INK, fill=True)
        t.circle(12, 7, 3, INK, fill=True)
        t.polygon([(4, 8), (15, 8), (10, 16), (9, 16)], INK)

    def grooves(t):
        t.line(6, 6, 6, 7, INK)

    return stamp(shape, grooves)


def requests():
    """A scroll: a sheet between its two rolls, lines of writing."""
    def shape(t):
        t.rect(6, 5, 13, 14, INK)
        t.rect(4, 3, 15, 5, INK)
        t.rect(4, 14, 15, 16, INK)

    def grooves(t):
        for y, x1 in ((8, 11), (10, 11), (12, 10)):
            t.line(8, y, x1, y, INK)

    return stamp(shape, grooves)


def stats():
    """A bar chart: three bars rising to the right on one base."""
    def shape(t):
        t.rect(4, 11, 6, 15, INK)
        t.rect(8, 8, 10, 15, INK)
        t.rect(12, 4, 14, 15, INK)
        t.rect(3, 15, 15, 16, INK)

    return stamp(shape)


def field():
    """A sprout: a mound of tilled soil, a stem and two broad leaves."""
    def shape(t):
        t.polygon([(3, 16), (5, 13), (14, 13), (16, 16)], INK)
        t.rect(9, 8, 10, 13, INK)
        t.ellipse(3, 7, 8, 10, INK)
        t.ellipse(11, 5, 16, 8, INK)

    def grooves(t):
        t.line(6, 15, 13, 15, INK)
        t.line(5, 8, 7, 9, INK)
        t.line(12, 7, 14, 6, INK)

    return stamp(shape, grooves)


def entity():
    """A courier: a head over broad shoulders, the arms tooled in."""
    def shape(t):
        t.circle(9, 5, 2, INK, fill=True)
        t.polygon([(5, 16), (5, 11), (7, 9), (12, 9), (14, 11), (14, 16)], INK)

    def grooves(t):
        t.line(7, 12, 7, 15, INK)
        t.line(12, 12, 12, 15, INK)

    return stamp(shape, grooves)


def inventory():
    """A backpack: its carry loop on top, the flap and a front pocket tooled in."""
    def shape(t):
        t.rect(5, 6, 14, 16, INK)
        t.rect(8, 3, 11, 4, INK)
        t.px(8, 5, INK)
        t.px(11, 5, INK)

    def grooves(t):
        t.line(6, 9, 13, 9, INK)
        t.contour([(8, 11), (11, 11), (11, 13), (8, 13)], INK)

    return stamp(shape, grooves)


def main():
    """A hut: a gabled roof over its walls, a door and a window tooled in."""
    def shape(t):
        t.polygon([(3, 9), (9, 3), (10, 3), (16, 9)], INK)
        t.rect(4, 9, 15, 16, INK)

    def grooves(t):
        t.contour([(7, 15), (7, 12), (9, 12), (9, 15)], INK, close=False)
        t.rect(12, 11, 12, 12, INK)
        t.line(5, 9, 14, 9, INK)

    return stamp(shape, grooves)


def crafting():
    """An anvil: the horn on the left, its face, the waist, the foot."""
    def shape(t):
        t.polygon([(2, 6), (15, 6), (15, 9), (6, 9)], INK)
        t.rect(8, 9, 12, 12, INK)
        t.rect(5, 13, 15, 15, INK)

    def grooves(t):
        t.line(7, 7, 14, 7, INK)

    return stamp(shape, grooves)


def settings():
    """A gear centred between pixels: a 12 px body, four 4 px wide teeth on its axes and four on its diagonals,
    notches between them, its 4 px hub cut through."""
    def shape(t):
        t.ellipse(4, 4, 15, 15, INK)
        for x0, y0, x1, y1 in ((8, 2, 11, 4), (8, 15, 11, 17), (2, 8, 4, 11), (15, 8, 17, 11),
                               (3, 3, 5, 5), (14, 3, 16, 5), (3, 14, 5, 16), (14, 14, 16, 16)):
            t.rect(x0, y0, x1, y1, INK)
        t.rect(8, 8, 11, 11, None)  # pixelstudio's ellipse() cannot erase: a 4 px hub, its corners kept
        for x, y in ((8, 8), (11, 8), (8, 11), (11, 11)):
            t.px(x, y, INK)

    return stamp(shape)


def stock():
    """Stacked crates: two below, one on top, a board tooled across each."""
    def shape(t):
        t.rect(2, 10, 8, 16, INK)
        t.rect(11, 10, 17, 16, INK)
        t.rect(6, 3, 13, 8, INK)

    def grooves(t):
        t.line(3, 13, 7, 13, INK)
        t.line(12, 13, 16, 13, INK)
        t.line(7, 5, 12, 5, INK)

    return stamp(shape, grooves)


def info():
    """An open book: two pages on their spine, lines of text tooled in."""
    def shape(t):
        t.rect(2, 5, 9, 15, INK)
        t.rect(10, 5, 17, 15, INK)
        t.rect(9, 16, 10, 16, INK)

    def grooves(t):
        for y in (8, 10, 12):
            t.line(4, y, 7, y, INK)
            t.line(12, y, 15, y, INK)

    return stamp(shape, grooves)


TABS = {f.__name__: f for f in (food, fuel, happiness, requests, stats, field, entity, inventory, main, crafting,
                                settings, stock, info)}

if __name__ == "__main__":
    for name, draw in TABS.items():
        out = HYCOLONY / ("%s@2x.png" % name)
        export(draw(), out)
        print(out.relative_to(ROOT))
