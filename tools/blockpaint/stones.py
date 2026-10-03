"""Stones and fired clay (spec 2026-10-03 blockpaint surfaces, substrate and finish): marble with its veins, granite
with its grains, glazed ceramic, their colours after Hytale's where it has them (docs/research/blockpaint-surfaces.md
§ 7: its marble and smooth clay, lightened for props; it has no granite); a stone left natural, rough cut, chiselled,
polished or carved."""

from collections import namedtuple

from brushes import coloured, jitter, mix, painted, smooth2

Stone = namedtuple("Stone", "rgb family")
STONES = {"marble": Stone((214, 212, 200), "stone"), "granite": Stone((152, 144, 138), "stone"),
          "ceramic": Stone((222, 216, 206), "ceramic")}
FINISHES = ("natural", "rough_cut", "chiselled", "polished", "carved")
VEIN, SPECKLES = (150, 146, 140), ((96, 90, 88), (214, 206, 198))


def stone(name, finish="natural"):
    """A brush of the stone name (STONES) left to finish (FINISHES); its family is the stone's."""
    kind = STONES[name]

    def rule(x, y, w, h, side):
        k = 1 + FINISH_RULES[finish](x, y)
        colour = coloured(kind.rgb, k)[:3]
        if name == "marble":
            colour = veined(colour, x, y)
        elif name == "granite":
            colour = speckled(colour, x, y)
        elif name == "ceramic":
            colour = coloured(colour, 1 + 0.05 * (1 - (x + y) / max(w + h - 2, 1)))[:3]
        return (*colour, 255)

    brush = painted(rule)
    brush.family = kind.family
    return brush


def veined(colour, x, y):
    """Marble: soft grey veins wandering across it, two texels wide, along a slow noise's middle line."""
    vein = abs(smooth2(x / 7, y / 9, 90) + 0.3 * smooth2(x / 3, y / 3, 91))
    return mix(colour, VEIN, 0.55 if vein < 0.06 else 0.25 if vein < 0.12 else 0.0)


def speckled(colour, x, y):
    """Granite: dark and light grains in pairs of texels, over a mottle."""
    grain = jitter(x // 2 * 31 + y // 2 * 17, 92)
    if grain > 0.7:
        return mix(colour, SPECKLES[0], 0.5)
    if grain < -0.75:
        return mix(colour, SPECKLES[1], 0.4)
    return colour


def natural(x, y):
    """Natural: broad, uneven patches."""
    return 0.07 * smooth2(x / 5, y / 5, 93) + 0.03 * smooth2(x / 2, y / 2, 94)


def rough_cut(x, y):
    """Rough cut: large flat facets, each its own shade."""
    return 0.06 * jitter((x // 4) * 13 + (y // 4) * 29, 95) + 0.03 * smooth2(x / 3, y / 3, 96)


def chiselled(x, y):
    """Chiselled: parallel chisel strokes on a slant, three texels wide."""
    return (0.03 if (x + y) // 3 % 2 else -0.02) + 0.05 * smooth2(x / 5, y / 5, 97)


def polished(x, y):
    """Polished: very even, a faint broad sheen."""
    return 0.03 * smooth2(x / 8, y / 8, 98)


def carved(x, y):
    """Carved: worked planes of light and shade, as from a sculptor's tool."""
    return 0.07 if smooth2(x / 3, y / 3, 99) > 0 else -0.05


FINISH_RULES = {"natural": natural, "rough_cut": rough_cut, "chiselled": chiselled, "polished": polished,
                "carved": carved}
