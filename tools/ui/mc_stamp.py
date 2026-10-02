"""MineColonies' module tab icon style (textures/gui/modules/*.png, 20 x 20): a tooled-leather stamp. A flat dark
brown shape, a 1 px light outline, details tooled in: a dark groove with a light lip on its bottom/right side (the
light comes from the top left), as MC's envelope, chest and bar icons. No blur, four browns."""

from mc_icon import Sprite, region

# MC's browns, read on modules/requests.png, inventory.png, stats.png.
OUTLINE, FILL, GROOVE, LIP = "#9e835f", "#63472d", "#543a20", "#c5a67e"
TAB_ICON_SIZE = 20


def border(pixels, x, y):
    """True when (x, y) has an orthogonal neighbour outside pixels."""
    return any(p not in pixels for p in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))


def stamp(shape, grooves=None, size=TAB_ICON_SIZE):
    """A tab icon: shape(sprite) draws the silhouette (filled, light outline); grooves(sprite) the tooled lines (dark,
    a light lip below/right of each), only inside the outline."""
    s = Sprite(size, size)
    body = region(size, shape)
    inside = {p for p in body if not border(body, *p)}
    for x, y in body:
        s.px(x, y, FILL if (x, y) in inside else OUTLINE)
    if grooves:
        cut = region(size, grooves) & inside
        for x, y in cut:
            s.px(x, y, GROOVE)
            for lip in ((x + 1, y), (x, y + 1)):
                if lip in inside and lip not in cut:
                    s.px(*lip, LIP)
    return s
