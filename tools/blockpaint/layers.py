"""One face's layers while it is painted (spec 2026-10-03 blockpaint surfaces, coats and the single run of
operations): the substrate a brush painted, the coats laid over it from the bottom up, how deep each texel was dug
(a scratch takes the top coat, a deeper one goes down to the substrate and into it), the deposits lying on top, and
the composition that lays them all down once every effect has run."""

import colorsys
from collections import namedtuple

from bake import BEVEL_RINGS, NOISE_CELL, value_noise
from brushes import mix

# A coat: its colour (rgb, or a brush (w, h, side) -> image), how it lies (cover: hides what is under it; clear: lets
# it through at 1 - opacity; tint: takes the coat's colour and keeps the grain under it), its thickness (how deep a
# scratch must go to take it off), its opacity (clear coats), its gloss (a light sheen on lit faces and edges) and its
# family for the effects (compat.py: a paint film does not rust).
Coat = namedtuple("Coat", "colour mode thickness opacity gloss family", defaults=(1.0, 1.0, 0.0, "paint_film"))
# A coat worn down to less than this share of its thickness lets the layer below through, in proportion.
THIN = 0.25
# How much a dig past every coat darkens the substrate (the hollow of a chip), and over what depth it reaches that.
HOLLOW, HOLLOW_DEPTH = 0.2, 1.0
# The sheen of a glossy coat at full gloss, on faces turned to the light and on the bevel's outer ring.
SHEEN = 0.18
# Macro variation: world units per noise cell (a part-wide swell), and its amplitude in value, hue (degrees) and
# saturation.
MACRO_CELL, MACRO, MACRO_HUE, MACRO_SAT = 12.0, 0.08, 6.0, 0.08


class Island:
    """A face's layers: substrate (an RGBA image), texels ({(i, j): bake.Texel}), the material's family, coats from
    the bottom up and own effect weights (effects.Material), the coats' tints ({(i, j): [(rgb, amount)]} per coat, in
    the order laid), depth ({(i, j): how deep it was dug}), relief ({(i, j): factor}, press), deposits ([(rgb,
    {(i, j): amount})]), and what the effects left: their zones ({effect: texels}, for the critique) and signals
    ({signal: texels}, for the effects after them)."""

    def __init__(self, substrate, texels, material, side):
        self.substrate = substrate
        self.texels = texels
        self.family = material.family
        self.coats = list(material.coats)
        self.coat_tints = [{} for _ in self.coats]
        self.depth = {cell: 0.0 for cell in texels}
        self.relief = {}
        self.deposits = []
        self.weights = dict(material.weights)
        self.zones = {}
        self.signals = {}
        w, h = substrate.size
        self.coat_images = [coat.colour(w, h, side) if callable(coat.colour) else None for coat in self.coats]

    def signal(self, name):
        """The texels a signal holds (empty when no effect wrote it)."""
        return self.signals.get(name, set())

    def total(self):
        """The thickness of the whole stack of coats."""
        return sum(coat.thickness for coat in self.coats)

    def left(self, k, i, j):
        """How much of coat k is left at (i, j): the depth counts from the top of the stack down."""
        above = sum(coat.thickness for coat in self.coats[k + 1:])
        return min(self.coats[k].thickness, max(0.0, self.coats[k].thickness - (self.depth[i, j] - above)))

    def bare(self, family, i, j):
        """Whether the substrate of family lies bare at (i, j): dug through every coat (readable at any time)."""
        return self.family == family and self.depth[i, j] >= self.total()

    def visible(self, i, j):
        """The index of the topmost coat left at (i, j), None when the substrate shows."""
        for k in reversed(range(len(self.coats))):
            if self.left(k, i, j) > 0:
                return k
        return None

    def family_at(self, i, j):
        """The family of the layer showing at (i, j): its topmost coat's left, else the substrate's."""
        k = self.visible(i, j)
        return self.family if k is None else self.coats[k].family

    def dig(self, depths):
        """Digs each texel of depths ({(i, j): depth}) at least that deep, taking off the deposits lying there."""
        for cell, depth in depths.items():
            self.depth[cell] = max(self.depth[cell], depth)
            for _, amounts in self.deposits:
                amounts.pop(cell, None)

    def shade(self, cells, k):
        """Scales the substrate's colour at cells by k (a polished edge, a burnt patch)."""
        pixels = self.substrate.load()
        for i, j in cells:
            r, g, b, a = pixels[i, j]
            pixels[i, j] = (*(max(0, min(255, round(c * k))) for c in (r, g, b)), a)

    def press(self, cells, k):
        """Scales whatever shows at cells by k once the coats are laid, under the deposits: the surface's own shape (a
        dent's shadowed wall, its lip catching the light), which no coat hides."""
        for cell in cells:
            self.relief[cell] = self.relief.get(cell, 1.0) * k

    def tint(self, cells, rgb, amount, layer="substrate"):
        """Turns a layer's colour at cells towards rgb by amount: the substrate's (rust on iron), coat number layer's
        (a faded paint), or "visible": whichever shows at each texel."""
        pixels = self.substrate.load()
        for i, j in cells:
            k = self.visible(i, j) if layer == "visible" else None if layer == "substrate" else layer
            if k is None:
                pixels[i, j] = (*mix(pixels[i, j][:3], rgb, amount), pixels[i, j][3])
            else:
                self.coat_tints[k].setdefault((i, j), []).append((rgb, amount))

    def deposit(self, rgb, amounts):
        """Lays a deposit of colour rgb, {(i, j): amount from 0 to 1}, over everything already there."""
        self.deposits.append((rgb, dict(amounts)))


def compose(island):
    """The face's image: the substrate (darker in the hollow of a dig past every coat), the coats left over it from
    the bottom up, each by its mode, their sheen, the relief (press), then the deposits. Alpha stays the
    substrate's."""
    image = island.substrate.copy()
    pixels, source = image.load(), island.substrate.load()
    total = island.total()
    lightness = mean_lightness(island.substrate)
    for (i, j), texel in island.texels.items():
        r, g, b, a = source[i, j]
        if not a:
            continue
        colour = (r, g, b)
        if island.depth[i, j] > total:
            colour = tuple(round(c * (1 - HOLLOW * min(1.0, (island.depth[i, j] - total) / HOLLOW_DEPTH)))
                           for c in colour)
        for k, coat in enumerate(island.coats):
            left = island.left(k, i, j)
            if left <= 0:
                continue
            own = island.coat_images[k].getpixel((i, j))[:3] if island.coat_images[k] else coat.colour
            laid = laid_colour(coat, own, colour, (r, g, b), lightness)
            for rgb, amount in island.coat_tints[k].get((i, j), ()):
                laid = mix(laid, rgb, amount)
            colour = mix(colour, laid, min(1.0, left / (coat.thickness * THIN)))
            if coat.gloss:
                colour = sheen(colour, coat.gloss, texel)
        if (i, j) in island.relief:
            colour = tuple(max(0, min(255, round(c * island.relief[i, j]))) for c in colour)
        for rgb, amounts in island.deposits:
            if (i, j) in amounts:
                colour = mix(colour, rgb, amounts[i, j])
        pixels[i, j] = (*colour, a)
    return image


def laid_colour(coat, rgb, under, substrate, lightness):
    """The colour coat (its own colour rgb at this texel) lays over under: rgb (cover), under seen through it
    (clear), or rgb at the substrate's lightness there against the island's mean lightness (tint: the grain shows
    through)."""
    if coat.mode == "cover":
        return rgb
    if coat.mode == "clear":
        return mix(under, rgb, coat.opacity)
    k = (0.3 * substrate[0] + 0.59 * substrate[1] + 0.11 * substrate[2]) / lightness if lightness else 1.0
    return tuple(max(0, min(255, round(c * k))) for c in rgb)


def sheen(colour, gloss, texel):
    """colour lit by a glossy coat's sheen: on faces turned to the light and on the bevel's outer ring."""
    lit = max(0.0, texel.light) + (0.5 if texel.edge is not None and texel.edge[1] == BEVEL_RINGS[0] else 0.0)
    return mix(colour, (255, 250, 236), SHEEN * gloss * min(1.0, lit))


def mean_lightness(image):
    """The mean lightness of the opaque texels of image (0 when none)."""
    values = [0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2] for p in image.get_flattened_data() if p[3]]
    return sum(values) / len(values) if values else 0.0


def macro(point, seed, channel=0):
    """A part-wide swell at a world point, around 0 (within about ±1): slow, so the faces of one part join up; seed
    picks another swell, channel another one at the same seed (value, hue, saturation)."""
    shifted = tuple(c / MACRO_CELL * NOISE_CELL + seed * 7.13 + channel * 31.7 for c in point)
    return value_noise(shifted)


def swell(rgb, point, seed):
    """rgb under the part-wide macro variation at point: its value by up to MACRO, its hue by up to MACRO_HUE degrees,
    its saturation by up to MACRO_SAT (spec 2026-10-03 blockpaint surfaces § 2.2)."""
    h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in rgb))
    h = (h + MACRO_HUE / 360 * macro(point, seed, 1)) % 1.0
    s = min(1.0, max(0.0, s * (1 + MACRO_SAT * macro(point, seed, 2))))
    v = v * (1 + MACRO * macro(point, seed))
    return tuple(max(0, min(255, round(c * 255))) for c in colorsys.hsv_to_rgb(h, s, v))
