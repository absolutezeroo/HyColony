"""Brushes of HyColony's hand-built models, one per kind of material. A brush paints a whole island,
(w, h, side) -> image, as a painter would (strokes along a plank, folds down a hanging cloth, chunks of stone, a
gradient across a crystal face); bake.light then lights the result from the model (tools/vanilla/bake.py)."""

from PIL import Image

from materials import coloured, jitter, mix, smooth


def painted(rule):
    """A brush from rule(x, y, w, h, side) -> colour, applied to every texel of the island."""
    def brush(w, h, side):
        image = Image.new("RGBA", (w, h))
        pixels = image.load()
        for x in range(w):
            for y in range(h):
                pixels[x, y] = rule(x, y, w, h, side)
        return image
    return brush


def as_tile(brush):
    """A 32 px tile painted by brush, for materials that draw over a base (blueprints, pages, banners)."""
    return brush(32, 32, "front")


def metal(rgb, streak=0.07, band=0.13):
    """Brushed metal: streaks along the island's long side, a soft highlight band a third down side faces, sparse
    bright flecks."""
    def rule(x, y, w, h, side):
        k = 1 + streak * jitter(y if w >= h else x, 7) + 0.02 * jitter(x * 31 + y, 3)
        if side not in ("top", "bottom") and h > 2:
            k += band * max(0.0, 1 - abs(y / (h - 1) - 0.3) * 4)
        if jitter(x * 13 + y * 7, 11) > 0.93:
            k += 0.12
        return coloured(rgb, k)
    return painted(rule)


def wood(rgb, plank=8):
    """Painted wood, as Hytale's props: long grain strokes along the island's long side, broken now and then, a
    darker seam every plank width across wide faces, an odd knot, the colour drifting softly along each plank."""
    def rule(x, y, w, h, side):
        a, b, width = (x, y, h) if w >= h else (y, x, w)
        board = b // plank
        k = 1 + 0.06 * jitter(board, 5) + 0.05 * smooth(a / 6 + board * 3.1, board)
        grain = jitter(b * 7 + board, 9)
        if grain > 0.55 and jitter((a // 3) * 11 + b, 4) > -0.6:
            k -= 0.11 if grain > 0.8 else 0.06
        elif grain < -0.85:
            k += 0.05
        if width > plank and b % plank == plank - 1:
            k -= 0.22
        if jitter(a // 2 * 13 + b // 2 * 29 + board, 21) > 0.985:
            k -= 0.25
        return coloured(rgb, k)
    return painted(rule)


def crystal(light, middle, deep):
    """A cut crystal face: light at the top left fading to deep at the bottom right, a lighter facet ridge along the
    anti-diagonal, a white glint near the lit corner."""
    def rule(x, y, w, h, side):
        if w >= 3 and h >= 3 and (x, y) == (1, 1):
            return (236, 252, 255, 255)
        if w >= 5 and h >= 5 and (x, y) in ((2, 1), (1, 2)):
            return (*mix(light, (236, 252, 255), 0.5), 255)
        u, v = x / max(w - 1, 1), y / max(h - 1, 1)
        t = (u + v) / 2
        colour = mix(light, middle, t * 2) if t < 0.5 else mix(middle, deep, (t - 0.5) * 2)
        if abs(u - (1 - v)) < 0.5 / max(w, h, 1) + 0.12:
            colour = mix(colour, light, 0.35)
        return (*colour, 255)
    return painted(rule)


def paper(rgb, aged=(176, 150, 104)):
    """Paper: fine fibres, a slightly lighter middle and an aged, yellowed rim."""
    def rule(x, y, w, h, side):
        k = 1 + 0.03 * jitter(x * 31 + y * 17, 2) + 0.03 * smooth(x / 5 + y * 0.7, y // 4)
        centre = 1 - max(abs(2 * x / max(w - 1, 1) - 1), abs(2 * y / max(h - 1, 1) - 1))
        colour = coloured(rgb, k + 0.04 * centre)
        if min(x, y, w - 1 - x, h - 1 - y) == 0:
            colour = (*mix(colour[:3], aged, 0.3), 255)
        return colour
    return painted(rule)


def cloth(rgb, folds=0.10):
    """Woven cloth: a fine weave, soft vertical folds down side faces (hanging fabric), a gentler ripple on top."""
    def rule(x, y, w, h, side):
        k = 1 + 0.035 * (1 if (x + y) % 2 else -1) + 0.02 * jitter(x * 7 + y * 13, 6)
        if side in ("top", "bottom"):
            k += 0.4 * folds * smooth((x + y) / 4, 3)
        else:
            k += folds * smooth(x / 3, 8) + 0.3 * folds * smooth(y / 5, 9)
        return coloured(rgb, k)
    return painted(rule)


def stone(rgb, chunk=(4, 3)):
    """Stone: irregular chunks of their own tone, dark cracks between them, light and dark specks."""
    cw, ch = chunk

    def rule(x, y, w, h, side):
        row = y // ch
        shift = (row % 2) * (cw // 2)
        cell = ((x + shift) // cw, row)
        k = 1 + 0.10 * jitter(cell[0] * 37 + cell[1] * 101, 4) + 0.05 * jitter(x * 19 + y * 23, 5)
        on_crack = (x + shift) % cw == 0 or y % ch == 0
        if on_crack and jitter(x * 3 + y * 5, 12) > -0.4:
            k -= 0.2
        if jitter(x * 41 + y * 7, 14) > 0.95:
            k += 0.15
        return coloured(rgb, k)
    return painted(rule)


def ore(rock, nugget, density=0.15):
    """Ore: stone with 2 x 2 nuggets scattered on a 4 px grid, each lit at its top left and shaded at its bottom
    right."""
    base = stone(rock)

    def brush(w, h, side):
        image = base(w, h, side)
        pixels = image.load()
        for gx in range(0, w, 4):
            for gy in range(0, h, 4):
                if jitter(gx * 53 + gy * 97, 15) < 1 - 2 * density:
                    continue
                ox, oy = gx + (jitter(gx + gy, 16) > 0), gy + (jitter(gx - gy, 17) > 0)
                for dx, dy, k in ((0, 0, 1.25), (1, 0, 1.0), (0, 1, 1.0), (1, 1, 0.72)):
                    if ox + dx < w and oy + dy < h:
                        pixels[ox + dx, oy + dy] = coloured(nugget, k)
        return image
    return brush


def clay(rgb):
    """Fired clay (bricks, pots): a soft mottle and scattered dark pores."""
    def rule(x, y, w, h, side):
        k = 1 + 0.07 * smooth(x / 3 + y * 0.5, y // 3) + 0.03 * jitter(x * 29 + y * 11, 18)
        if jitter(x * 61 + y * 43, 19) > 0.9:
            k -= 0.16
        return coloured(rgb, k)
    return painted(rule)
