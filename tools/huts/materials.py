"""Shared colours, tile tints and helpers of HyColony's hand-built models (the brushes are in brushes.py)."""

from PIL import Image, ImageDraw

from paint import softened

LEATHER = (104, 64, 38)
BRASS = (196, 150, 64)
PAPER = (226, 214, 186)
# The cyan crystal of the goggles' lenses and the build tool: lit corner, body, depth.
CYAN = ((176, 240, 255), (64, 188, 228), (18, 86, 146))


def tinted(rgb, tile, keep):
    """The tile's grain kept at keep, pulled halfway to the colour rgb."""
    return Image.blend(softened(tile, keep), Image.new("RGBA", tile.size, (*rgb, 255)), 0.55)


def feather(cloth):
    """White feather with slanted grey barbs."""
    image = tinted((238, 236, 230), cloth, 0.2)
    draw = ImageDraw.Draw(image)
    for x in range(0, 32, 2):
        draw.line([(x, 0), (x + 3, 31)], fill=(206, 204, 200, 255))
    return image


def leather(rgb, tile):
    """Mottled leather: the tint, darker patches and a few light scuffs."""
    base = tinted(rgb, tile, 0.3)
    pixels = base.load()
    for x in range(32):
        for y in range(32):
            n = jitter(x // 2 * 17 + y // 2 * 5, 13)
            r, g, b, a = pixels[x, y]
            k = 0.9 if n < -0.6 else 1.08 if n > 0.85 else 1.0
            pixels[x, y] = (min(255, round(r * k)), min(255, round(g * k)), min(255, round(b * k)), a)
    return base


def jitter(i, salt):
    """Deterministic pseudo-random value in [-1, 1] for an integer."""
    h = (i * 2654435761 + salt * 40503) & 0xFFFFFFFF
    h = ((h ^ (h >> 15)) * 2246822519) & 0xFFFFFFFF
    return ((h ^ (h >> 13)) & 0xFFFF) / 32767.5 - 1.0


def smooth(x, salt):
    """Smooth 1D noise in [-1, 1]."""
    i = int(x // 1)
    f = x - i
    f = f * f * (3 - 2 * f)
    return jitter(i, salt) * (1 - f) + jitter(i + 1, salt) * f


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def coloured(rgb, k):
    return (*(max(0, min(255, round(c * k))) for c in rgb), 255)
