"""Blockymodel geometry shared by the model tools (HyVanilla, HyColony's huts, items and construction tape, HyDomum):
nodes, their placement, faces, bounds and uniform scaling (32 units per block); the arithmetic is vectors.py's (add,
multiply and rotate still read from here by the model tools)."""

import copy
import math

from vectors import add, multiply, rotate

FACE_NORMALS = {"front": (0, 0, 1), "back": (0, 0, -1), "right": (1, 0, 0), "left": (-1, 0, 0), "top": (0, 1, 0),
                "bottom": (0, -1, 0)}


# The box axes a face's island runs along: (its u, its v), as face_point lays it out.
FACE_AXES = {"front": ("x", "y"), "back": ("x", "y"), "right": ("z", "y"), "left": ("z", "y"), "top": ("x", "z"),
             "bottom": ("x", "z")}


def face_span(side, size):
    """(width, height) of a box face of size (x, y, z), in texels as Blockbench lays it out."""
    return {"front": (size[0], size[1]), "back": (size[0], size[1]), "right": (size[2], size[1]),
            "left": (size[2], size[1]), "top": (size[0], size[2]), "bottom": (size[0], size[2])}[side]


def face_point(side, size, s, t):
    """The point about the box's centre at texel coordinates (s, t) of a face, as Blockbench lays faces out: u to the
    right and v down, seen from outside the face."""
    hx, hy, hz = (c / 2 for c in size)
    return {"front": (-hx + s, hy - t, hz), "back": (hx - s, hy - t, -hz), "right": (hx, hy - t, hz - s),
            "left": (-hx, hy - t, -hz + s), "top": (-hx + s, hy, -hz + t), "bottom": (-hx + s, -hy, hz - t)}[side]


def face_size(shape, side):
    """(width, height) in texels of the face side of a box or quad shape: a quad's one face spans its x and y."""
    size = shape["settings"]["size"]
    if quad(shape):
        return size["x"], size["y"]
    return face_span(side, (size["x"], size["y"], size["z"]))


def face_local(shape, side, s, t):
    """The point about the shape's centre at texel coordinates (s, t) of its face side: a quad's face is a box's
    front laid on its plane (z 0), u to the right and v down."""
    size = shape["settings"]["size"]
    if quad(shape):
        return -size["x"] / 2 + s, size["y"] / 2 - t, 0.0
    return face_point(side, (size["x"], size["y"], size["z"]), s, t)


def face_normal(shape, side):
    """The outward normal of the face side of a box or quad shape, about the shape (unturned)."""
    return (0, 0, 1) if quad(shape) else FACE_NORMALS[side]


def quad(shape):
    """Whether shape is a quad the model tools paint: facing +Z (Hytale's default and most used normal); fails on a
    quad facing another way, which its node's orientation turns instead."""
    if shape["type"] != "quad":
        return False
    if shape["settings"].get("normal", "+Z") != "+Z":
        raise SystemExit(f"a quad facing {shape['settings']['normal']}: the model tools paint +Z quads, turned by "
                         "their node's orientation")
    return True


def node(name, position, shape, children=()):
    return {"id": "0", "name": name, "children": list(children), "position": xyz(position),
            "orientation": {"x": 0, "y": 0, "z": 0, "w": 1}, "shape": shape}


def empty_shape():
    return {"type": "none", "offset": xyz((0, 0, 0)), "stretch": xyz((1, 1, 1)), "settings": {"isPiece": False},
            "visible": True, "doubleSided": False, "shadingMode": "flat", "unwrapMode": "custom",
            "textureLayout": {}}


def box_shape(size, sides, offset=(0, 0, 0), shading="standard"):
    """A box shape of size (x, y, z), its centre offset from its node, showing only the faces sides (each on its own
    island once unwrap lays them out)."""
    shape = empty_shape()
    shape.update({"type": "box", "offset": xyz(offset), "settings": {"isPiece": False, "size": xyz(size)},
                  "shadingMode": shading, "textureLayout": {
                      side: {"offset": {"x": 0, "y": 0}, "mirror": {"x": False, "y": False}, "angle": 0}
                      for side in sides}})
    return shape


def quad_shape(size, offset=(0, 0, 0), shading="standard"):
    """A quad shape of size (x, y) facing +Z, its centre offset from its node, seen from both sides as Hytale's
    decals are (doubleSided), its one face (front) on its own island once unwrap lays it out."""
    shape = empty_shape()
    shape.update({"type": "quad", "offset": xyz(offset), "doubleSided": True, "shadingMode": shading,
                  "settings": {"isPiece": False, "size": {"x": size[0], "y": size[1]}, "normal": "+Z"},
                  "textureLayout": {"front": {"offset": {"x": 0, "y": 0}, "mirror": {"x": False, "y": False},
                                              "angle": 0}}})
    return shape


# The longest texture side unwrap picks when it can: Hytale's block textures reach 352, ours 384 (shown in game);
# longer strips (the warehouse's smallest layout is 32 x 672) are untried in the client's block atlas. Not a client
# limit: HyDomum's 576 x 544 board shows in game.
MAX_SIDE = 384


# Pixels between two islands (paint.bleed fills them).
GAP = 2
# The texture widths unwrap tries.
WIDTHS = (32, 64, 96, 128, 160, 192, 256)


def unwrap(nodes, widths=WIDTHS, apart=lambda name: False):
    """Lays every box and quad face of the model on its own UV island, GAP pixels apart, at the texture width of
    widths giving the smallest texture with no side over MAX_SIDE, else the one of the shortest longest side; the faces
    of the nodes named apart(name) are laid together in a band below all the others (glint.py copies only that band).
    Returns the texture size, sides multiples of 32; changes nodes in place."""
    groups = ([], [])
    for n in walk(nodes):
        shape = n["shape"]
        if shape["type"] not in ("box", "quad"):
            continue
        for side, face in shape["textureLayout"].items():
            w, h = (int(c) for c in face_size(shape, side))
            groups[bool(apart(n["name"]))].append((h, w, n["name"] + " " + side, face))
    for faces in groups:
        faces.sort(key=lambda f: (-f[0], -f[1], f[2]))

    def laid(width):
        spots, top = skyline(groups[0], width, 0)
        if spots is None:
            return None
        band, bottom = skyline(groups[1], width, top + GAP if groups[1] and spots else top)
        if band is None:
            return None
        return spots + band, 32 * math.ceil(bottom / 32)

    fits = [(width, *layout) for width in widths if (layout := laid(width))]
    width, spots, height = min(fits, key=lambda f: (max(f[0], f[2], MAX_SIDE), f[0] * f[2]))
    for face, x, y in spots:
        face["offset"] = {"x": x, "y": y}
    return width, height


def skyline(faces, width, floor):
    """([(face, x, y)], bottom): the faces, in the order given, each set at the smallest row y (from row floor) where it
    fits above the islands already laid in a texture of width, leftmost on a tie; bottom is the largest row any island
    reaches. (None, 0) when a face is wider than the texture."""
    tops = [floor] * width
    spots, bottom = [], floor
    for h, w, _, face in faces:
        if w > width:
            return None, 0
        # A face holds its own columns and the gap after it: the next island beside it stays GAP away.
        y, x = min((max(tops[x:x + w + GAP]), x) for x in range(width - w + 1))
        spots.append((face, x, y))
        tops[x:x + w + GAP] = [y + h + GAP] * len(tops[x:x + w + GAP])
        bottom = max(bottom, y + h)
    return spots, bottom


def xyz(values):
    return {"x": values[0], "y": values[1], "z": values[2]}


def scaled(nodes, factor, floor):
    """Copies of nodes, uniformly scaled by factor about the origin, under a group standing at height floor."""
    plant = [copy.deepcopy(n) for n in nodes]
    for n in walk(plant):
        n["position"] = {k: v * factor for k, v in n["position"].items()}
        shape = n["shape"]
        shape["offset"] = {k: v * factor for k, v in shape["offset"].items()}
        shape["stretch"] = {k: v * factor for k, v in shape["stretch"].items()}
    return node("Plant", (0, floor, 0), empty_shape(), plant)


def shift_uvs(nodes, du, dv):
    """Moves every face's texture offset by (du, dv): the texture now sits there in an atlas."""
    for n in walk(nodes):
        for face in n["shape"].get("textureLayout", {}).values():
            face["offset"] = {"x": face["offset"]["x"] + du, "y": face["offset"]["y"] + dv}


# A face's angle -> how it turns a texture-space point about the face's pivot (vanilla textureLayout rule).
TURNS = {0: lambda x, y: (x, y), 90: lambda x, y: (-y, x), 180: lambda x, y: (-x, -y), 270: lambda x, y: (y, -x)}


def face_rects(nodes):
    """(node name, u0, v0, u1, v1): the texture area each face reads. The offset is the pivot: mirroring flips the
    face's rectangle over it, then the angle turns it about it (as vanilla models lay out rotated faces)."""
    for n in walk(nodes):
        shape = n["shape"]
        size = shape.get("settings", {}).get("size")
        for side, face in shape.get("textureLayout", {}).items():
            # A quad's one face spans its x and y, whatever side it is named; a quad has no z. Not face_size: this reads
            # Hytale's own models too, whose quads face any way (face_size takes only the +Z quads we paint).
            if shape["type"] == "quad":
                w, h = size["x"], size["y"]
            else:
                w, h = face_span(side, (size["x"], size["y"], size["z"]))
            mirror = face.get("mirror", {})
            w = -w if mirror.get("x") else w
            h = -h if mirror.get("y") else h
            turn = TURNS[face.get("angle", 0) % 360]
            corners = [turn(x, y) for x in (0, w) for y in (0, h)]
            u, v = face["offset"]["x"], face["offset"]["y"]
            yield (n.get("name"), u + min(c[0] for c in corners), v + min(c[1] for c in corners),
                   u + max(c[0] for c in corners), v + max(c[1] for c in corners))


def check_uvs(owner, nodes, size):
    """Fails on a face reading outside its texture of size: in an atlas it would read the neighbouring texture."""
    for name, u0, v0, u1, v1 in face_rects(nodes):
        if u0 < 0 or v0 < 0 or u1 > size[0] or v1 > size[1]:
            raise SystemExit(f"{owner}: face of {name} reads ({u0}, {v0})-({u1}, {v1}) outside its {size} texture")


def walk(nodes):
    for n in nodes:
        yield n
        yield from walk(n.get("children", []))


def bounds(nodes):
    """Lowest and highest corner, in model units, of every shape of the model."""
    points = []
    for n, position, rotation in placed(nodes):
        shape = n["shape"]
        offset, stretch = (tuple(shape[key][a] for a in "xyz") for key in ("offset", "stretch"))
        for corner in corners(shape):
            points.append(add(position, rotate(rotation, add(offset, tuple(c * s for c, s in zip(corner, stretch))))))
    if not points:
        return (0, 0, 0), (0, 0, 0)
    return tuple(min(p[i] for p in points) for i in range(3)), tuple(max(p[i] for p in points) for i in range(3))


def placed(nodes, position=(0.0, 0.0, 0.0), rotation=(0.0, 0.0, 0.0, 1.0)):
    """(node, world position, world rotation) of every node. A child's position counts from its parent's position
    plus the parent's shape offset, turned by the parent (Hytale's BlockyModelBoundsParser.accumulateNodeBounds)."""
    for n in nodes:
        o, p, offset = n["orientation"], n["position"], n["shape"]["offset"]
        own = multiply(rotation, (o["x"], o["y"], o["z"], o["w"]))
        at = add(position, rotate(rotation, (p["x"], p["y"], p["z"])))
        yield n, at, own
        yield from placed(n.get("children", []), add(at, rotate(own, (offset["x"], offset["y"], offset["z"]))), own)


def corners(shape):
    size = shape.get("settings", {}).get("size")
    if shape["type"] == "box":
        hx, hy, hz = size["x"] / 2, size["y"] / 2, size["z"] / 2
        return [(sx * hx, sy * hy, sz * hz) for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)]
    if shape["type"] == "quad":
        hx, hy = size["x"] / 2, size["y"] / 2
        if shape["settings"].get("normal", "+Z").endswith("Y"):
            return [(sx * hx, 0, sy * hy) for sx in (-1, 1) for sy in (-1, 1)]
        return [(sx * hx, sy * hy, 0) for sx in (-1, 1) for sy in (-1, 1)]
    return []
