"""One Minecraft block model (Blockbench cuboids) -> blockymodel nodes and the model's own atlas.

Axes and faces follow Blockbench's Hytale exporter (JannisX11/hytale-blockbench-plugin, src/blockymodel.ts): the same
x/y/z as Minecraft, north -> back, south -> front, west -> left, east -> right, up -> top, down -> bottom. A block is
x, z in [-16, 16] and y in [0, 32] (vanilla Carpet/Slope models), 2 units per Minecraft pixel. Each face's picture is
baked into the atlas already cropped, flipped and turned as Minecraft shows it, so every face reads its atlas cell
with no mirror and angle 0.
"""

import math
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "decorations"))
from models import empty_shape, face_rects, node, walk, xyz  # noqa: E402

UNITS = 2  # blockymodel units per Minecraft pixel
TEXELS = 2  # plank texture pixels per Minecraft uv unit (a 32 px texture over uv 0..16)
FACES = {"north": "back", "south": "front", "west": "left", "east": "right", "up": "top", "down": "bottom"}
AXES = {"x": (1, 0, 0), "y": (0, 1, 0), "z": (0, 0, 1)}
# The frame component of every two-material DO block, and the only material of the others, is textured
# block/oak_planks in the source models: it becomes Darkwood, every other slot Lightwood.
FRAME_TEXTURES = ("block/oak_planks", "minecraft:block/oak_planks")
# Minecraft's clockwise uv rotation -> PIL transpose.
TURNS = {90: Image.Transpose.ROTATE_270, 180: Image.Transpose.ROTATE_180, 270: Image.Transpose.ROTATE_90}


def material(textures, ref):
    """'dark' for the frame slot or the frame's texture, 'light' for anything else (unresolved slots included)."""
    slot, seen = ref.lstrip("#"), set()
    while slot in textures and textures[slot].startswith("#") and slot not in seen:
        seen.add(slot)
        slot = textures[slot].lstrip("#")
    return "dark" if slot == "frame" or textures.get(slot) in FRAME_TEXTURES else "light"


def face_size(direction, size):
    """(width, height) of a face in units, as its texture lies on it."""
    sx, sy, sz = size
    return {"north": (sx, sy), "south": (sx, sy), "west": (sz, sy), "east": (sz, sy),
            "up": (sx, sz), "down": (sx, sz)}[direction]


def default_uv(direction, low, high):
    """Minecraft's uv for a face that declares none: the element's own position on the face."""
    (x1, y1, z1), (x2, y2, z2) = low, high
    return {"north": (16 - x2, 16 - y2, 16 - x1, 16 - y1), "south": (x1, 16 - y2, x2, 16 - y1),
            "west": (z1, 16 - y2, z2, 16 - y1), "east": (16 - z2, 16 - y2, 16 - z1, 16 - y1),
            "up": (x1, z1, x2, z2), "down": (x1, 16 - z2, x2, 16 - z1)}[direction]


def face_image(tiled, uv, rotation, width, height):
    """The face's picture, width x height units rounded up: uv of the texture (tiled 3 x 3 so that uv past 0..16
    wraps), flipped when uv runs backwards, then turned clockwise by rotation."""
    turned = rotation in (90, 270)
    span_w, span_h = (height, width) if turned else (width, height)
    out = (max(1, math.ceil(span_w - 1e-6)), max(1, math.ceil(span_h - 1e-6)))
    side = tiled.size[0] // 3
    u0, v0, u1, v1 = (c * TEXELS + side for c in uv)
    data = ((u1 - u0) / span_w, 0, u0, 0, (v1 - v0) / span_h, v0)
    image = tiled.transform(out, Image.Transform.AFFINE, data, resample=Image.Resampling.NEAREST)
    return image.transpose(TURNS[rotation]) if rotation in TURNS else image


def tile(texture):
    tiled = Image.new("RGBA", (texture.width * 3, texture.height * 3))
    for i in range(3):
        for j in range(3):
            tiled.paste(texture, (i * texture.width, j * texture.height))
    return tiled


def hytale(point):
    """Minecraft pixel position -> blockymodel units."""
    return ((point[0] - 8) * UNITS, point[1] * UNITS, (point[2] - 8) * UNITS)


def orientation(rotation):
    if not rotation or not rotation.get("angle"):
        return (0.0, 0.0, 0.0, 1.0)
    half = math.radians(rotation["angle"]) / 2
    axis = AXES[rotation["axis"]]
    return (axis[0] * math.sin(half), axis[1] * math.sin(half), axis[2] * math.sin(half), math.cos(half))


class Converter:
    """Converts one model: collects each face's picture, then packs them into its atlas."""

    def __init__(self, planks):
        self.planks = {k: tile(v) for k, v in planks.items()}
        self.images = []  # (image, [texture layouts reading it])

    def convert(self, model):
        """(nodes, atlas, faces): the blockymodel nodes, their atlas and, per element, its box (position,
        orientation, offset, size) and {direction: picture} for the icon."""
        nodes, faces = [], []
        for index, element in enumerate(model["elements"]):
            converted = self.element(model["textures"], element, "E" + str(index))
            if converted:
                nodes.append(converted[0])
                faces.append(converted[1])
        atlas = self.pack()
        return nodes, atlas, faces

    def element(self, textures, element, name):
        low, high = element["from"], element["to"]
        size = tuple((high[i] - low[i]) * UNITS for i in range(3))
        pictures = {}
        for direction, face in element["faces"].items():
            width, height = face_size(direction, size)
            if width <= 0 or height <= 0:
                continue
            uv = face.get("uv") or default_uv(direction, low, high)
            texture = self.planks[material(textures, face.get("texture", ""))]
            pictures[direction] = face_image(texture, uv, face.get("rotation", 0), width, height)
        if not pictures or sum(1 for s in size if s <= 0) > 1:
            return None
        centre = hytale([(low[i] + high[i]) / 2 for i in range(3)])
        rotation = element.get("rotation")
        pivot = hytale(rotation["origin"]) if rotation else centre
        offset = tuple(c - p for c, p in zip(centre, pivot))
        shape = self.shape(size, pictures)
        shape["offset"] = xyz(offset)
        result = node(name, pivot, shape)
        q = orientation(rotation)
        result["orientation"] = {"x": q[0], "y": q[1], "z": q[2], "w": q[3]}
        return result, {"position": pivot, "orientation": q, "offset": offset, "size": size, "faces": pictures}

    def shape(self, size, pictures):
        """A box, or a quad for a flat element (Blockbench's exporter does the same): facing the side it shows,
        double-sided when Minecraft textures both of its sides."""
        shape = empty_shape()
        shape.update({"type": "box", "settings": {"size": xyz(size)}, "shadingMode": "standard"})
        flat = next((axis for axis, s in zip("xyz", size) if s <= 0), None)
        if flat is None:
            shape["textureLayout"] = {FACES[d]: self.layout(p) for d, p in pictures.items()}
            return shape
        positive, negative = {"x": ("east", "west"), "y": ("up", "down"), "z": ("south", "north")}[flat]
        shown = positive if positive in pictures else negative
        sx, sy, sz = size
        shape["type"] = "quad"
        shape["settings"] = {"size": {"x": sz if flat == "x" else sx, "y": sz if flat == "y" else sy},
                             "normal": ("+" if shown == positive else "-") + flat.upper()}
        shape["doubleSided"] = positive in pictures and negative in pictures
        shape["textureLayout"] = {"front": self.layout(pictures[shown])}
        return shape

    def layout(self, picture):
        face = {"offset": {"x": 0, "y": 0}, "mirror": {"x": False, "y": False}, "angle": 0}
        for image, users in self.images:
            if image.size == picture.size and image.tobytes() == picture.tobytes():
                users.append(face)
                return face
        self.images.append((picture, [face]))
        return face

    def pack(self):
        """Shelf-packs the distinct pictures, tallest first, into a power-of-two atlas; points each face at its
        picture."""
        area = sum(i.width * i.height for i, _ in self.images)
        width = power_of_two(max([32, math.isqrt(area) + 1] + [i.width for i, _ in self.images]))
        x = y = shelf = 0
        placed = []
        for image, users in sorted(self.images, key=lambda e: (-e[0].height, -e[0].width)):
            if x + image.width > width:
                x, y, shelf = 0, y + shelf, 0
            placed.append((image, x, y))
            for face in users:
                face["offset"] = {"x": x, "y": y}
            x += image.width
            shelf = max(shelf, image.height)
        atlas = Image.new("RGBA", (width, power_of_two(max(1, y + shelf))), (0, 0, 0, 0))
        for image, px, py in placed:
            atlas.paste(image, (px, py))
        return atlas


def power_of_two(n):
    return 1 << (n - 1).bit_length()


def check_atlas(name, nodes, atlas):
    """Fails on a face reading outside its atlas: Hytale would show another face's picture, or nothing."""
    for node_name, u0, v0, u1, v1 in face_rects(nodes):
        if u0 < 0 or v0 < 0 or u1 > atlas.width or v1 > atlas.height:
            raise SystemExit(f"{name}: face of {node_name} reads ({u0}, {v0})-({u1}, {v1}) outside {atlas.size}")


def model_root(nodes):
    """The model file's content: an Origin group holding the nodes, ids numbered from 1."""
    root = node("Origin", (0, 0, 0), empty_shape(), nodes)
    for index, n in enumerate(walk([root]), start=1):
        n["id"] = str(index)
    return {"lod": "auto", "nodes": [root]}
