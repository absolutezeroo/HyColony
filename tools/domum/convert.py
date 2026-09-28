"""One assembled Domum Ornamentum state (Minecraft cuboids) -> a blockymodel reading the shape's material layout.

Like DO, which retextures each component's placeholder sprite with the chosen material (A.2), every face reads its
component's tile of a fixed layout: 32x32 for a one-material shape (the material's own texture), 64x32 for two
(component 1 in x 0..32, component 2 in x 32..64). No picture is baked: the runtime swaps the texture.

Axes follow Blockbench's Hytale exporter (JannisX11/hytale-blockbench-plugin, src/blockymodel.ts): Minecraft's x/y/z,
north -> back, south -> front, west -> left, east -> right, up -> top, down -> bottom. A block is x, z in [-16, 16] and
y in [0, 32], 2 units per Minecraft pixel; a material texture has 32 texels per block, so a face reads one texel per
unit. A face wider than a tile would read the next one: its box is built at most 32 units per axis and stretched
back (shape "stretch", as vanilla models change texel density). Texture offsets follow the layout rule verified on
vanilla models (tools/vanilla/models.py face_rects): the offset is the pivot, the mirror flips the face's
rectangle over it, then the angle turns it about it.

Deviation from MC: Minecraft stretches a face's uv rectangle over the face; here a face reads one texel per unit
from its uv origin, so a uv span that differs from the face's size (a timber beam's uv 16..0 over a 12 px face) shows
the material at its natural scale instead. For a uniform material texture this only shifts the pattern.
"""

import math
import sys
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1] / "vanilla"))
from models import TURNS, empty_shape, node, walk, xyz  # noqa: E402

from assemble import default_uv  # noqa: E402

UNITS = 2  # blockymodel units per Minecraft pixel
TILE = 32  # texels of one material tile (one block face)
FACES = {"north": "back", "south": "front", "west": "left", "east": "right", "up": "top", "down": "bottom"}
AXES = {"x": (1, 0, 0), "y": (0, 1, 0), "z": (0, 0, 1)}


def layout_size(family):
    """(width, height) of the texture the family's models read: one tile per material slot."""
    return (TILE * len(family.components), TILE)


def component_index(family, textures, ref):
    """0 or 1: the material slot of a face's texture reference, followed through the model's "#name" aliases to
    its placeholder texture (with or without the minecraft: namespace). A one-material family always reads tile 0;
    an unknown placeholder of a two-material family reads the second slot, DO's centre."""
    if len(family.components) == 1:
        return 0
    value, seen = ref, set()
    while value.startswith("#") and value not in seen:
        seen.add(value)
        value = textures.get(value[1:], "")
    value = value.removeprefix("minecraft:")
    known = [c.removeprefix("minecraft:") for c in family.components]
    return known.index(value) if value in known else 1


def to_blockymodel(model, family, group=None, pivot=(0, 0, 0)):
    """The .blockymodel content of model: one node per element with a positive area face, each face reading its
    component's tile; the nodes sit under a node named group standing at pivot (units) when given, the node a
    vanilla door animation turns about its hinge."""
    nodes = [n for i, e in enumerate(model["elements"])
             if (n := _element(model["textures"], e, "E" + str(i), family)) is not None]
    if group:
        for n in nodes:
            n["position"] = {k: v - p for (k, v), p in zip(n["position"].items(), pivot)}
        nodes = [node(group, pivot, empty_shape(), nodes)]
    root = node("Origin", (0, 0, 0), empty_shape(), nodes)
    for index, n in enumerate(walk([root]), start=1):
        n["id"] = str(index)
    return {"lod": "auto", "nodes": [root]}


def face_size(direction, size):
    """(width, height) of a face in units, as its texture lies on it."""
    sx, sy, sz = size
    return {"north": (sx, sy), "south": (sx, sy), "west": (sz, sy), "east": (sz, sy),
            "up": (sx, sz), "down": (sx, sz)}[direction]


def hytale(point):
    """Minecraft pixel position -> blockymodel units."""
    return ((point[0] - 8) * UNITS, point[1] * UNITS, (point[2] - 8) * UNITS)


def orientation(rotation):
    if not rotation or not rotation.get("angle"):
        return (0.0, 0.0, 0.0, 1.0)
    half = math.radians(rotation["angle"]) / 2
    axis = AXES[rotation["axis"]]
    return (axis[0] * math.sin(half), axis[1] * math.sin(half), axis[2] * math.sin(half), math.cos(half))


def _element(textures, element, name, family):
    low, high = element["from"], element["to"]
    real = tuple((high[i] - low[i]) * UNITS for i in range(3))
    size = tuple(min(s, TILE) for s in real)
    stretch = tuple(r / s if s > 0 else 1 for r, s in zip(real, size))
    faces = {d: f for d, f in element["faces"].items() if all(v > 0 for v in face_size(d, real))}
    if not faces or sum(1 for s in real if s <= 0) > 1:
        return None
    layouts = {d: _layout((textures, f), d, (low, high), size, family) for d, f in faces.items()}
    centre = hytale([(low[i] + high[i]) / 2 for i in range(3)])
    rotation = element.get("rotation")
    pivot = hytale(rotation["origin"]) if rotation else centre
    shape = _shape(size, layouts)
    shape["offset"] = xyz(tuple(c - p for c, p in zip(centre, pivot)))
    shape["stretch"] = xyz(stretch)
    result = node(name, pivot, shape)
    q = orientation(rotation)
    result["orientation"] = {"x": q[0], "y": q[1], "z": q[2], "w": q[3]}
    return result


def _shape(size, layouts):
    """A box, or a quad for a flat element (Blockbench's exporter does the same): facing the side it shows,
    double-sided when Minecraft textures both of its sides."""
    shape = empty_shape()
    shape.update({"type": "box", "settings": {"size": xyz(size)}, "shadingMode": "standard"})
    flat = next((axis for axis, s in zip("xyz", size) if s <= 0), None)
    if flat is None:
        shape["textureLayout"] = {FACES[d]: layout for d, layout in layouts.items()}
        return shape
    positive, negative = {"x": ("east", "west"), "y": ("up", "down"), "z": ("south", "north")}[flat]
    shown = positive if positive in layouts else negative
    sx, sy, sz = size
    shape["type"] = "quad"
    shape["settings"] = {"size": {"x": sz if flat == "x" else sx, "y": sz if flat == "y" else sy},
                         "normal": ("+" if shown == positive else "-") + flat.upper()}
    shape["doubleSided"] = positive in layouts and negative in layouts
    shape["textureLayout"] = {"front": layouts[shown]}
    return shape


def _layout(reference, direction, bounds, size, family):
    """The face's textureLayout entry: Minecraft's uv origin (mirrored when the uv runs backwards, turned by the
    face's rotation) read inside the component's tile, moved inward when it would cross the tile's edge.
    reference = (the model's textures, the face), bounds = the element's (from, to)."""
    textures, face = reference
    u0, v0, u1, v1 = face.get("uv") or default_uv(direction, *bounds)
    angle = face.get("rotation", 0) % 360
    mirror_x, mirror_y = u1 < u0, v1 < v0
    width, height = face_size(direction, size)
    w, h = (-width if mirror_x else width), (-height if mirror_y else height)
    corners = [TURNS[angle](x, y) for x in (0, w) for y in (0, h)]
    min_x, min_y = min(c[0] for c in corners), min(c[1] for c in corners)
    span_x, span_y = max(c[0] for c in corners) - min_x, max(c[1] for c in corners) - min_y
    left = _inside(min(u0, u1) * UNITS % TILE, span_x)
    top = _inside(min(v0, v1) * UNITS % TILE, span_y)
    tile_x = TILE * component_index(family, textures, face.get("texture", ""))
    return {"offset": {"x": _snap(tile_x + left - min_x, min_x, span_x, tile_x),
                       "y": _snap(top - min_y, min_y, span_y, 0)},
            "mirror": {"x": mirror_x, "y": mirror_y}, "angle": angle}


def _snap(offset, low, span, tile_start):
    """offset rounded to a whole texel, then nudged so the rectangle it reads (offset + low .. + span, fractional
    when DO's 0.001 px nudges make a size fractional) stays inside its tile."""
    snapped = round(offset)
    if snapped + low < tile_start:
        snapped += math.ceil(tile_start - (snapped + low))
    if snapped + low + span > tile_start + TILE:
        snapped -= math.ceil(snapped + low + span - tile_start - TILE)
    return snapped


def _inside(start, span):
    """start, a whole texel, moved back so start..start+span stays within one tile (span is at most TILE)."""
    return max(0, min(math.floor(start), TILE - math.ceil(span)))
