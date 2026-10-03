"""Metals and how they were worked (spec 2026-10-03 blockpaint surfaces, substrate and finish): iron, steel, cast
iron, silver and brass, their colours after Hytale's own where it has them (docs/research/blockpaint-surfaces.md § 7:
its iron and bronze blocks), each forged, hammered, brushed, polished, rough cast or machined. metals.py keeps gold,
bronze and copper."""

from collections import namedtuple

from brushes import along, coloured, edge, jitter, painted, smooth, smooth2

# A metal: its colour and family (compat.py: iron and steel rust, silver tarnishes, brass greens).
Metal = namedtuple("Metal", "rgb family")
METALS = {
    "iron": Metal((134, 142, 141), "ferrous"),
    "steel": Metal((150, 158, 166), "ferrous"),
    "cast_iron": Metal((96, 99, 101), "ferrous"),
    "silver": Metal((200, 204, 210), "noble"),
    "brass": Metal((212, 168, 86), "cuprous"),
}
FINISHES = ("forged", "hammered", "brushed", "polished", "rough_cast", "machined")
# Side faces of every finish catch a lighter band a third of the way down (the light on a curved sheet, as
# brushes.metal), this strong.
BAND = 0.1


def metal(name, finish="forged"):
    """A brush of the metal name (METALS) worked to finish (FINISHES); its family is the metal's."""
    kind = METALS[name]

    def rule(x, y, w, h, side):
        k = 1 + FINISH_RULES[finish](x, y, w, h)
        if side not in ("top", "bottom") and h > 2:
            k += BAND * max(0.0, 1 - abs(y / (h - 1) - 0.3) * 4)
        return coloured(kind.rgb, k)

    brush = painted(rule)
    brush.family = kind.family
    return brush


def forged(x, y, w, h):
    """Forged: broad, uneven patches from the hammer and the fire, a little darker towards the rim."""
    rim = edge(x, y, w, h)
    return 0.08 * smooth2(x / 4, y / 4, 70) + 0.04 * smooth2(x / 2, y / 2, 71) - 0.05 * max(0, 1 - rim / 2)


def hammered(x, y, w, h):
    """Hammered: small facets, each its own shade, lit on its upper left edge and shaded on its lower right."""
    cx, cy = (x + (y // 3) % 2) // 3, y // 3
    k = 0.07 * jitter(cx * 31 + cy * 17, 72)
    fx, fy = (x + (y // 3) % 2) % 3, y % 3
    return k + (0.05 if fx == 0 or fy == 0 else -0.04 if fx == 2 and fy == 2 else 0.0)


def brushed(x, y, w, h):
    """Brushed: fine streaks along the long side, each row its own shade."""
    a, b, _ = along(x, y, w, h)
    return 0.05 * jitter(b, 73) + 0.03 * smooth(a / 4 + b * 2.3, 74)


def polished(x, y, w, h):
    """Polished: smooth, a soft gradient and a bright sheen on the upper left."""
    u, v = x / max(w - 1, 1), y / max(h - 1, 1)
    return 0.08 * (1 - (u + v)) + 0.1 * max(0.0, 1 - ((u - 0.25) ** 2 + (v - 0.25) ** 2) * 8)


def rough_cast(x, y, w, h):
    """Rough cast: grainy, mottled in clumps, small dark pits gathered in clusters."""
    k = 0.07 * smooth2(x / 2.5, y / 2.5, 75)
    pits = smooth2(x / 3, y / 3, 76) > 0.4 and jitter(x * 41 + y * 13, 77) > 0.3
    return k - (0.12 if pits else 0.0)


def machined(x, y, w, h):
    """Machined: regular parallel tool lines along the long side, three texels apart, in passes six texels wide each
    of its own shade, very even."""
    a, b, _ = along(x, y, w, h)
    return (0.04 if b % 3 == 0 else 0.0) + 0.05 * jitter(b // 6, 79) + 0.02 * smooth(a / 8, 78)


FINISH_RULES = {"forged": forged, "hammered": hammered, "brushed": brushed, "polished": polished,
                "rough_cast": rough_cast, "machined": machined}
