"""What the composer (composer.py) reads of a whole model: its units (a part and the family of its material: a ledger's
leather and its paper pages are two), their texels in the texture and in the world, their importance, and the value
tools every operation shares: lightness, contrast and saturation over a unit."""

import colorsys
from collections import namedtuple

from critique import lightness
from illustration import scaled
from vectors import near

# A unit: its part, family, importance (0..3), texels ({(texture x, y): bake.Texel}, painted ones only), world points
# and the number of the accent it belongs to (composer_budgets.accents; None: no accent).
Unit = namedtuple("Unit", "part family level texels points accent", defaults=(None,))
# A model: its units, every painted texel ({(x, y): bake.Texel}) and its size in blocks (the volume of its bounds).
Model = namedtuple("Model", "units texels blocks")
# Two units are neighbours when their texels come within TOUCH world units (semantics checks the same).
TOUCH = 0.6
# World units per block.
BLOCK = 32


def model_of(pixels, records, level):
    """The Model of records (surface.layered's), each unit's importance level(part); only painted texels count."""
    grouped = {}
    for part, _, (u, v, _, _), island, _ in records:
        texels = grouped.setdefault((part, island.family), {})
        for (i, j), t in island.texels.items():
            if pixels[u + i, v + j][3]:
                texels[u + i, v + j] = t
    units = [Unit(part, family, level(part), texels, [t.point for t in texels.values()])
             for (part, family), texels in sorted(grouped.items()) if texels]
    every = {cell: t for unit in units for cell, t in unit.texels.items()}
    points = [t.point for t in every.values()]
    extent = [max(p[k] for p in points) - min(p[k] for p in points) for k in range(3)] if points else [0, 0, 0]
    return Model(units, every, max(extent[0], 1) * max(extent[1], 1) * max(extent[2], 1) / BLOCK ** 3)


def neighbours(units):
    """[(a, b)] of the units whose texels come within TOUCH of each other, each pair once."""
    return [(a, b) for n, a in enumerate(units) for b in units[n + 1:] if near(a.points, b.points, TOUCH)]


def mean_colour(pixels, cells):
    """The mean colour (r, g, b) of cells."""
    cells = list(cells)
    return tuple(sum(pixels[c][k] for c in cells) / len(cells) for k in range(3)) if cells else (0.0, 0.0, 0.0)


def mean_light(pixels, cells):
    return lightness(mean_colour(pixels, cells))


def colour_gap(a, b):
    return sum(abs(a[k] - b[k]) for k in range(3))


def scale_unit(pixels, cells, k):
    """Scales the value of cells by k (their hue and contrast kept)."""
    for cell in cells:
        pixels[cell] = scaled(pixels[cell], k)


def contrast_unit(pixels, cells, k):
    """Scales the value contrast of cells round their mean lightness by k: each texel moves by the same amount on r, g
    and b, so rust on dark iron grows lighter rather than a more saturated orange (a contrast round the mean colour
    pushed every patch's chroma too)."""
    cells = list(cells)
    mean = mean_light(pixels, cells)
    for cell in cells:
        here = pixels[cell]
        shift = (lightness(here) - mean) * (k - 1)
        pixels[cell] = (*(max(0, min(255, round(here[c] + shift))) for c in range(3)), here[3])


def saturate_unit(pixels, cells, k):
    """Scales the saturation of cells by k (their hue and value kept)."""
    for cell in cells:
        pixels[cell] = shifted(pixels[cell], 0.0, k)


def shifted(pixel, hue, k):
    """pixel with its hue turned by hue (degrees) and its saturation scaled by k."""
    h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in pixel[:3]))
    rgb = colorsys.hsv_to_rgb((h + hue / 360) % 1.0, min(1.0, s * k), v)
    return (*(max(0, min(255, round(c * 255))) for c in rgb), pixel[3])
