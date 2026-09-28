"""Blockymodel helpers of the HyVanilla generator: nodes, bounds and uniform scaling (32 units per block)."""

import copy
import math


def box_node(name, centre, size, uv):
    face = {"offset": {"x": uv[0], "y": uv[1]}, "mirror": {"x": False, "y": False}, "angle": 0}
    return node(name, [0, 0, 0], {
        "type": "box",
        "offset": xyz(centre),
        "stretch": xyz((1, 1, 1)),
        "settings": {"size": xyz(size)},
        "visible": True,
        "doubleSided": False,
        "shadingMode": "flat",
        "unwrapMode": "custom",
        "textureLayout": {side: copy.deepcopy(face) for side in ("front", "back", "left", "right", "top", "bottom")},
    })


def node(name, position, shape, children=()):
    return {"id": "0", "name": name, "children": list(children), "position": xyz(position),
            "orientation": {"x": 0, "y": 0, "z": 0, "w": 1}, "shape": shape}


def empty_shape():
    return {"type": "none", "offset": xyz((0, 0, 0)), "stretch": xyz((1, 1, 1)), "settings": {"isPiece": False},
            "visible": True, "doubleSided": False, "shadingMode": "flat", "unwrapMode": "custom",
            "textureLayout": {}}


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
            if shape["type"] == "quad" or side in ("front", "back"):
                w, h = size["x"], size["y"]
            elif side in ("left", "right"):
                w, h = size["z"], size["y"]
            else:
                w, h = size["x"], size["z"]
            mirror = face.get("mirror", {})
            w = -w if mirror.get("x") else w
            h = -h if mirror.get("y") else h
            turn = TURNS[face.get("angle", 0) % 360]
            corners = [turn(x, y) for x in (0, w) for y in (0, h)]
            u, v = face["offset"]["x"], face["offset"]["y"]
            yield (n.get("name"), u + min(c[0] for c in corners), v + min(c[1] for c in corners),
                   u + max(c[0] for c in corners), v + max(c[1] for c in corners))


def walk(nodes):
    for n in nodes:
        yield n
        yield from walk(n.get("children", []))


def bounds(nodes):
    """Lowest and highest corner, in model units, of every shape of the model."""
    points = []
    for n in nodes:
        collect(n, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0, 1.0), points)
    if not points:
        return (0, 0, 0), (0, 0, 0)
    return tuple(min(p[i] for p in points) for i in range(3)), tuple(max(p[i] for p in points) for i in range(3))


def collect(n, parent_position, parent_rotation, points):
    o = n["orientation"]
    rotation = multiply(parent_rotation, (o["x"], o["y"], o["z"], o["w"]))
    p = n["position"]
    position = add(parent_position, rotate(parent_rotation, (p["x"], p["y"], p["z"])))
    shape = n["shape"]
    offset = (shape["offset"]["x"], shape["offset"]["y"], shape["offset"]["z"])
    stretch = (shape["stretch"]["x"], shape["stretch"]["y"], shape["stretch"]["z"])
    for corner in corners(shape):
        local = add(offset, tuple(c * s for c, s in zip(corner, stretch)))
        points.append(add(position, rotate(rotation, local)))
    for child in n.get("children", []):
        collect(child, position, rotation, points)


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


def add(a, b):
    return tuple(x + y for x, y in zip(a, b))


def multiply(q, r):
    x1, y1, z1, w1 = q
    x2, y2, z2, w2 = r
    return (w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
            w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
            w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2,
            w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2)


def rotate(q, v):
    norm = math.sqrt(sum(c * c for c in q)) or 1.0
    q = tuple(c / norm for c in q)
    x, y, z, _ = multiply(multiply(q, (v[0], v[1], v[2], 0.0)), (-q[0], -q[1], -q[2], q[3]))
    return (x, y, z)
