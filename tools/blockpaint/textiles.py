"""Textiles (spec 2026-10-03 blockpaint surfaces, substrate and structure): linen, cotton, wool, silk and canvas, each
with its weave, dyed any colour (cotton and silk after Hytale's white and red cloth, the others undyed beside them:
docs/research/blockpaint-surfaces.md § 7). The weave is the structure, kept at two texels and more so that it reads as
cloth rather than noise."""

from brushes import along, coloured, family, jitter, painted, smooth, smooth2

DEFAULTS = {"linen": (222, 210, 186), "cotton": (236, 234, 228), "wool": (196, 188, 176), "silk": (186, 60, 72),
            "canvas": (196, 178, 140)}


@family("textile")
def linen(rgb=DEFAULTS["linen"]):
    """Linen: an uneven plain weave, here and there a thicker, lighter thread (a slub) along the cloth."""
    def rule(x, y, w, h, side):
        k = 1 + 0.03 * (1 if (x // 2 + y // 2) % 2 else -1) + 0.05 * smooth2(x / 5, y / 5, 80)
        if jitter(y // 2, 81) > 0.75:
            k += 0.05
        return coloured(rgb, k)
    return painted(rule)


@family("textile")
def cotton(rgb=DEFAULTS["cotton"]):
    """Cotton: an even, soft weave under gentle folds."""
    def rule(x, y, w, h, side):
        return coloured(rgb, 1 + 0.02 * (1 if (x // 2 + y // 2) % 2 else -1) + 0.05 * smooth2(x / 6, y / 4, 82))
    return painted(rule)


@family("textile")
def wool(rgb=DEFAULTS["wool"]):
    """Wool: thick and diffuse, knitted in soft V rows, a little fuzzy."""
    def rule(x, y, w, h, side):
        stitch = (x // 2 + (y // 3) % 2) % 2
        return coloured(rgb, 1 + (0.04 if stitch else -0.03) + 0.06 * smooth2(x / 3, y / 3, 83))
    return painted(rule)


@family("textile")
def silk(rgb=DEFAULTS["silk"]):
    """Silk: smooth, a soft sheen running across it on a slant, darker in its folds."""
    def rule(x, y, w, h, side):
        a, b, length = along(x, y, w, h)
        sheen = max(0.0, 1 - abs((a + b) / max(length, 1) - 0.6) * 2.5)
        return coloured(rgb, 1 + 0.14 * sheen - 0.06 * max(0.0, smooth(b / 3, 84)))
    return painted(rule)


@family("textile")
def canvas(rgb=DEFAULTS["canvas"]):
    """Canvas: a strong, coarse weave, each thread its own shade."""
    def rule(x, y, w, h, side):
        over = (x // 2 + y // 2) % 2
        thread = jitter(x // 2 if over else y // 2 + 97, 85)
        return coloured(rgb, 1 + (0.03 if over else -0.03) + 0.02 * thread + 0.05 * smooth2(x / 6, y / 6, 86))
    return painted(rule)


TEXTILES = {"linen": linen, "cotton": cotton, "wool": wool, "silk": silk, "canvas": canvas}
