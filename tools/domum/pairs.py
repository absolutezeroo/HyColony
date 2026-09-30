"""The 64x32 texture of a two-material Domum Ornamentum shape: the first material's texture on the left, the
second's on the right, each fitted to one 32x32 block face. Only the templates read these; runtime variants read
the palette through remapped models (plugin VariantPalette), and their icons draw the same pair in memory."""

from PIL import Image

import tags

FACE = 32


def pair_path(first, second):
    """Common asset path of the pair texture."""
    return f"Blocks/HyDomum/Pairs/{first}__{second}.png"


def image(assets, first, second):
    """The pair texture: each material's side texture scaled to FACE x FACE, nearest neighbour, side by side."""
    out = Image.new("RGBA", (2 * FACE, FACE))
    for index, block_id in enumerate((first, second)):
        face = assets.image("Common/" + tags.texture(assets, block_id)).resize((FACE, FACE), Image.NEAREST)
        out.paste(face, (index * FACE, 0))
    return out


def write(pack, assets, first, second):
    """Writes the pair texture under pack/Common and returns its asset path."""
    path = pair_path(first, second)
    target = pack / "Common" / path
    target.parent.mkdir(parents=True, exist_ok=True)
    image(assets, first, second).save(target)
    return path
