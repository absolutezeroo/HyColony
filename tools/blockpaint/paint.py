"""Paints a hand-built model's texture, island by island (docs/research/hytale-models.md): every face of a box gets
its material's tile or brush (brushes.py), then the light baked from the model itself (bake.py)."""

from PIL import Image

from bake import lit
from brushes import average
from models import face_span, walk


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


def paint(nodes, size, tiles, material_of, pictures=frozenset()):
    """The model's unlit texture: each island filled with tiles[material_of(node name, side)]. A tile is an image,
    or a brush: a function (w, h, side) painting the whole island. The materials in pictures carry a drawing laid out
    for their island: never turned."""
    image = Image.new("RGBA", size, (0, 0, 0, 0))
    for name, side, u, v, w, h in islands(nodes):
        material = material_of(name, side)
        tile = tiles[material]
        image.paste(tile(w, h, side) if callable(tile) else fill(tile, w, h, material not in pictures), (u, v))
    return image


def texture(nodes, size, tiles, material_of, values, pictures=frozenset()):
    """The model's finished texture: painted (paint), lit by values (bake.light_map of the model), each island bled
    into its gap (bleed)."""
    return bleed(lit(paint(nodes, size, tiles, material_of, pictures), values), nodes)


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


def fill(tile, w, h, follow_grain=True):
    """The tile repeated over w x h; with follow_grain, turned so its grain (wood planks) runs along the island's
    long side."""
    if follow_grain and h > w:
        tile = tile.transpose(Image.ROTATE_90)
    out = Image.new("RGBA", (w, h))
    for x in range(0, w, tile.width):
        for y in range(0, h, tile.height):
            out.paste(tile, (x, y))
    return out
