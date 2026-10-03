"""The brushes of HyColony's hand-built models (the huts set of blockpaint): shared colours, tile tints and the
brushes several models use (the general brushes are in tools/blockpaint/brushes.py; a drawing of one model's own,
such as the builder's blueprint, stays in that model's module)."""

from PIL import Image, ImageDraw

from brushes import coloured, family, jitter, painted, smooth
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


@family("textile")
def rope():
    """Twisted hemp rope: light and dark strands in turn along a diagonal."""
    return painted(lambda x, y, w, h, side: coloured((206, 180, 128), 0.85 if (x + y) % 2 else 1.05))


# Cut stone (the quarries' blocks) and rough rock.
CUT_STONE = (178, 170, 154)
ROCK = (128, 124, 116)
# A hollow wooden bucket's faces turned inwards, painted dark (quarry_medium.py, florist.py).
BUCKET_INSIDE = frozenset({("Bucket_Bottom", "top"), ("Bucket_F", "back"), ("Bucket_B", "front"),
                           ("Bucket_L", "right"), ("Bucket_R", "left")})
# A bucket's staves (staves): each STAVE texels wide, its joint included; how dark the joint, how much one stave's
# shade differs from the next (Hytale's bucket: 61 to 105 in red across its staves) and how much lighter their end
# grain on top. BUCKET_WOOD: the colour of Hytale's bucket staves (69, 39, 21), lightened as our props are (their
# light is baked): both measured in docs/research/blockpaint-surfaces.md § 7.
STAVE, STAVE_JOINT, STAVE_SHADE, STAVE_END = 3, 0.58, 0.1, 0.12
BUCKET_WOOD = (112, 62, 34)


@family("wood")
def staves(rgb, width=STAVE):
    """A coopered bucket's wood, as Hytale's bucket paints it: upright staves width texels wide, each lit on its left
    and a little darker on its right, its own shade, a faint grain up its length, a dark joint between two; on top,
    the staves' light end grain."""
    def rule(x, y, w, h, side):
        stave, place = divmod(x, width)
        if place == width - 1:
            return coloured(rgb, STAVE_JOINT)
        k = 1 + STAVE_SHADE * jitter(stave, 61) + (0.05 if place == 0 else -0.04)
        if side == "top":
            return coloured(rgb, k + STAVE_END)
        return coloured(rgb, k + 0.03 * smooth(y / 3 + stave * 1.7, 62))
    return painted(rule)


@family("plant")
def leaf(rgb):
    """Young leaves: a mottled green, lighter towards the top of side faces, with a few light veins."""
    def rule(x, y, w, h, side):
        k = 1 + 0.08 * smooth(x / 2 + y * 0.7, 31) + 0.04 * jitter(x * 17 + y * 5, 32)
        if side not in ("top", "bottom") and h > 1:
            k += 0.12 * (1 - y / (h - 1))
        if jitter(x * 23 + y * 41, 33) > 0.8:
            k += 0.15
        return coloured(rgb, k)
    return painted(rule)
