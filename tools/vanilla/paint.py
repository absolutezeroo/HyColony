"""Paints a hand-built model's texture, island by island (docs/research/hytale-models.md): every face of a box gets
its material's tile, then the baked light Hytale's own props carry (contact shadow at the foot of side faces, a lit
upper edge, a darker rim, cool-tinted shadows, never pure black or white). The engine's standard shading adds the
light direction itself, so faces are not darkened by orientation here."""

from PIL import Image

from models import walk

# Side name -> the box size axes the face spans (width, height), as Blockbench lays faces out (u right, v down).
SPANS = {"front": ("x", "y"), "back": ("x", "y"), "left": ("z", "y"), "right": ("z", "y"),
         "top": ("x", "z"), "bottom": ("x", "z")}
# The hue a darkened pixel drifts to (per channel, at full shadow): the article's "add colour in shadows".
SHADOW_TINT = (0.93, 0.95, 1.08)
# No pure black or white (they break Hytale's lighting).
LOW, HIGH = 14, 242
# Contact shadow at the bottom of a side face, lit top row, darker island rim, top faces lighter in their middle.
FOOT_SHADOW, TOP_LIGHT, RIM, DOME = 0.72, 1.12, 0.84, 0.10


def islands(nodes):
    """(node name, side, u, v, w, h) of every box face, once per texture rectangle (shared faces paint once)."""
    seen = set()
    for n in walk(nodes):
        shape = n["shape"]
        if shape["type"] != "box":
            continue
        size = shape["settings"]["size"]
        for side, face in shape.get("textureLayout", {}).items():
            if face.get("angle", 0) or any(face.get("mirror", {}).values()):
                raise SystemExit(f"{n['name']} {side}: rotated or mirrored faces are not painted")
            w, h = (int(size[axis]) for axis in SPANS[side])
            rect = (int(face["offset"]["x"]), int(face["offset"]["y"]), w, h)
            if rect not in seen:
                seen.add(rect)
                yield n["name"], side, *rect


def paint(nodes, size, tiles, material_of, side_rims=True, pictures=frozenset()):
    """The model's texture: each island filled with tiles[material_of(node name, side)], then shaded. Without
    side_rims, side faces keep their left and right columns unshaded: walls split into several boxes then join
    without a seam. The materials in pictures carry a drawing laid out for their island: never turned."""
    image = Image.new("RGBA", size, (0, 0, 0, 0))
    for name, side, u, v, w, h in islands(nodes):
        material = material_of(name, side)
        island = fill(tiles[material], w, h, material not in pictures)
        image.paste(shaded(island, side, side_rims), (u, v))
    return image


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


def darker(tile, light):
    """The tile darkened to light (0..1), its shadow tinted as the painted shadows are."""
    out = tile.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            pixels[x, y] = lit(pixels[x, y], light)
    return out


def softened(tile, keep):
    """The tile with its grain kept at the fraction keep, the rest pulled to its mean colour (no noisy cloth)."""
    mean = tile.convert("RGB").resize((1, 1), Image.BOX).getpixel((0, 0))
    flat = Image.new("RGBA", tile.size, (*mean, 255))
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


def shaded(island, side, side_rims):
    """The island with baked light: side faces darken towards their foot and catch light on their top row; every
    island darkens on its 1-pixel rim (a side face's left and right columns only with side_rims)."""
    w, h = island.size
    pixels = island.load()
    is_side = side not in ("top", "bottom")
    for y in range(h):
        for x in range(w):
            light = 1.0
            if is_side:
                light *= 1.0 - (1.0 - FOOT_SHADOW) * (y / max(h - 1, 1)) ** 1.5
                if y == 0 and h > 2:
                    light *= TOP_LIGHT
            else:
                edge = max(abs(2 * x / max(w - 1, 1) - 1), abs(2 * y / max(h - 1, 1) - 1))
                light *= 1.0 + DOME * (0.5 - edge**2)
            columns = x in (0, w - 1) and (side_rims or not is_side)
            if w > 2 and h > 2 and (columns or y == h - 1 or (not is_side and y == 0)):
                light *= RIM
            pixels[x, y] = lit(pixels[x, y], light)
    return island


def lit(pixel, light):
    r, g, b, a = pixel
    shadow = max(0.0, 1.0 - light)
    channels = (c * light * (1.0 + (t - 1.0) * shadow * 2) for c, t in zip((r, g, b), SHADOW_TINT))
    return (*(min(HIGH, max(LOW, round(c))) for c in channels), a)
