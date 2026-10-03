"""Paints a hand-built model's texture, island by island (docs/research/hytale-models.md): every face of a box gets
its material's tile, brush (brushes.py) or layered material (effects.material, painted by surface.layered: spec
2026-10-03 blockpaint surfaces), then the light baked from the model itself (bake.py)."""

import zlib
from collections import namedtuple

from PIL import Image

import brushes
from bake import lit
from brushes import average
from effects import Material
from models import FACE_AXES, face_span, walk
from surface import layered

# What paints each island: tiles ({material: an image, a brush (w, h, side) -> image, or a layered material}),
# material_of(node name, side) and the pictures (materials whose tile carries a drawing laid out for its island,
# never turned).
Look = namedtuple("Look", "tiles material_of pictures", defaults=(frozenset(),))
# How a model is painted beyond its look: its seed (each face draws its own pattern, face_seed; None: as always),
# axes ({node name before Blockbench's '--C<n>': box axis}, the grain of its faces: grain_of), the surface.Surface of
# its layered materials and the light's mode (shading.graded).
Painting = namedtuple("Painting", "seed axes surface light", defaults=(None, None, None, "legacy"))


def islands(nodes):
    """(node name, side, u, v, w, h) of every box face, once per texture rectangle (shared faces paint once)."""
    seen = set()
    for n in walk(nodes):
        shape = n["shape"]
        if shape["type"] != "box":
            continue
        size = tuple(shape["settings"]["size"][a] for a in "xyz")
        for side, face in shape.get("textureLayout", {}).items():
            if face.get("angle", 0) or any(face.get("mirror", {}).values()):
                raise SystemExit(f"{n['name']} {side}: rotated or mirrored faces are not painted")
            w, h = (int(c) for c in face_span(side, size))
            rect = (int(face["offset"]["x"]), int(face["offset"]["y"]), w, h)
            if rect not in seen:
                seen.add(rect)
                yield n["name"], side, *rect


def paint(nodes, size, look, painting=Painting()):
    """The model's unlit texture: each island filled with its tile in look (Look): an image repeated (fill), a
    brush painting the whole island, or a layered material painted by surface.layered with painting.surface; each
    face with its seed and grain (Painting)."""
    image = Image.new("RGBA", size, (0, 0, 0, 0))
    for name, side, u, v, w, h in islands(nodes):
        material = look.material_of(name, side)
        tile = look.tiles[material]
        part = name.split("--")[0]
        face, direction = face_seed(name, side, painting.seed), grain_of(side, (painting.axes or {}).get(part))
        with brushes.seeded(face), brushes.grain(direction):
            if isinstance(tile, Material):
                island = layered(tile, painting.surface, (part, side, (u, v, w, h)), tile.substrate(w, h, side))
            elif callable(tile):
                island = tile(w, h, side)
            else:
                island = fill(tile, (w, h), material not in look.pictures, direction, face)
        image.paste(island, (u, v))
    return image


def face_seed(name, side, seed):
    """The seed of one face of a model seeded with seed: 0 without a model seed, else a stable number of the node,
    the side and the seed (zlib.crc32: Python's hash() changes from one run to the next)."""
    return 0 if seed is None else zlib.crc32(f"{name}/{side}/{seed}".encode())


def grain_of(side, axis):
    """The island axis ("u", "v") along which a face of a box shows its grain when it runs along the box axis axis
    ("x", "y", "z"); None without an axis or when it leaves the face (end grain: the brush picks the long side)."""
    if axis is None:
        return None
    u, v = FACE_AXES[side]
    return "u" if axis == u else "v" if axis == v else None


def texture(nodes, size, look, values, painting=Painting()):
    """The model's finished texture: painted (paint), lit by values (bake.light_map of the model) in painting's light
    mode (shading.graded), each island bled into its gap (bleed)."""
    return bleed(lit(paint(nodes, size, look, painting), values, painting.light), nodes)


def bleed(image, nodes):
    """Copies each island's border one pixel out into still transparent texels, so a face whose texture lookup
    lands a fraction outside its island (thin faces, mipmaps) reads its own colour, not the next island's. Needs the
    islands laid out 2 pixels apart: with 1, the pixel between two islands takes the first one's border only."""
    pixels = image.load()
    for _, _, u, v, w, h in islands(nodes):
        for x in range(u - 1, u + w + 1):
            for y in range(v - 1, v + h + 1):
                inside = (min(max(x, u), u + w - 1), min(max(y, v), v + h - 1))
                if (x, y) != inside and 0 <= x < image.width and 0 <= y < image.height and pixels[x, y][3] == 0:
                    pixels[x, y] = pixels[inside]
    return image


def softened(tile, keep):
    """The tile with its grain kept at the fraction keep, the rest pulled to its mean colour (no noisy cloth)."""
    flat = Image.new("RGBA", tile.size, (*average(tile), 255))
    flat.putalpha(tile.getchannel("A"))
    return Image.blend(flat, tile, keep)


def fill(tile, size, follow_grain=True, grain=None, seed=0):
    """The tile repeated over size (w, h); with follow_grain, turned so its grain (wood planks) runs along the
    island's grain ("u", "v"; None: its long side). A face seed shifts the tile, so each seeded face shows another
    window."""
    w, h = size
    if follow_grain and (grain == "v" if grain else h > w):
        tile = tile.transpose(Image.ROTATE_90)
    dx, dy = seed % tile.width, seed // tile.width % tile.height
    out = Image.new("RGBA", (w, h))
    for x in range(-dx, w, tile.width):
        for y in range(-dy, h, tile.height):
            out.paste(tile, (x, y))
    return out
