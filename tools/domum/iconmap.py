"""Icon maps: a shape's inventory icon drawn once at build time, every pixel recording where to read the shape's
texture layout instead of a colour, so the runtime paints any material's icon without a 3D renderer.

Pixel code: R = floor(u * 256 / layout width), G = floor(v * 256 / layout height), B = shade * 255 (1 top, 0.85 and
0.7 sides, 0.5 bottom), A = 255 where the model covers the pixel, else 0.

Camera: the vanilla icon camera of IconProperties (Rotation [x, y, z] degrees, Scale, Translation), fitted on the
vanilla generated icons of a cube, a half block and stairs (silhouette overlap 0.96): the model turns by z, then x,
then y, is seen orthographically from +Z, 2 pixels per unit times Scale, shifted by Translation units.
"""

import math
import sys
from pathlib import Path

from PIL import Image

sys.path.append(str(Path(__file__).resolve().parents[1] / "blockpaint"))
from models import TURNS, add, placed, rotate  # noqa: E402

SIZE = 64
PIXELS_PER_UNIT = 2  # at Scale 1
# Side -> (normal, top-left corner as signs of the half size, texture x axis, texture y axis) in the shape's space.
FRAMES = {
    "front": ((0, 0, 1), (-1, 1, 1), (1, 0, 0), (0, -1, 0)),
    "back": ((0, 0, -1), (1, 1, -1), (-1, 0, 0), (0, -1, 0)),
    "right": ((1, 0, 0), (1, 1, 1), (0, 0, -1), (0, -1, 0)),
    "left": ((-1, 0, 0), (-1, 1, -1), (0, 0, 1), (0, -1, 0)),
    "top": ((0, 1, 0), (-1, 1, -1), (1, 0, 0), (0, 0, 1)),
    "bottom": ((0, -1, 0), (-1, -1, 1), (1, 0, 0), (0, 0, -1)),
}
QUAD_SIDES = {"+Z": "front", "-Z": "back", "+X": "right", "-X": "left", "+Y": "top", "-Y": "bottom"}
OPPOSITE = {"front": "back", "back": "front", "right": "left", "left": "right", "top": "bottom", "bottom": "top"}


def render(blockymodel, layout_size, properties):
    """The icon map (SIZE x SIZE RGBA) of blockymodel reading a layout_size texture, seen with properties."""
    view = _view(properties["Rotation"])
    scale = PIXELS_PER_UNIT * properties["Scale"]
    faces = [f for f in _faces(blockymodel["nodes"]) if _apply(view, f[2])[2] > 1e-6]
    corners = [c for f in faces for c in f[0] + [add(f[0][1], [b - a for a, b in zip(f[0][0], f[0][2])])]]
    seen = [_apply(view, c) for c in corners]
    span = max([max(p[i] for p in seen) - min(p[i] for p in seen) for i in (0, 1)], default=0)
    scale = min(scale, (SIZE - 2) / span) if span else scale  # a model wider than the icon shrinks to fit
    shift = _framed(properties.get("Translation", [0, 0]), seen, scale)

    def screen(point):
        p = _apply(view, point)
        return (SIZE / 2 + (p[0] + shift[0]) * scale, SIZE / 2 - (p[1] + shift[1]) * scale, p[2])

    depth = [[-math.inf] * SIZE for _ in range(SIZE)]
    icon = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    pixels = icon.load()
    for points, texels, normal in faces:
        code = (layout_size, _shade(normal))
        _fill(pixels, depth, [screen(p) for p in points], texels, code)
    return icon


def _framed(translation, points, scale):
    """translation, or, when the model would leave the icon with it, the one centring the model's view bounds
    (the vanilla trapdoor camera frames a trapdoor at the top of its block, ours lies on the floor)."""
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    inside = all(0 <= SIZE / 2 + (x + translation[0]) * scale <= SIZE for x in xs) and all(
        0 <= SIZE / 2 - (y + translation[1]) * scale <= SIZE for y in ys)
    if inside or not points:
        return translation
    return [-(min(xs) + max(xs)) / 2, -(min(ys) + max(ys)) / 2]


def _view(rotation):
    """The camera rotation matrix: y(rotation[1]) . x(rotation[0]) . z(rotation[2])."""
    matrix = _axis("y", rotation[1])
    for axis, degrees in (("x", rotation[0]), ("z", rotation[2])):
        turn = _axis(axis, degrees)
        matrix = [[sum(matrix[i][k] * turn[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return matrix


def _axis(axis, degrees):
    """The rotation matrix of degrees about axis ("x", "y" or "z")."""
    c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
    return {"x": [[1, 0, 0], [0, c, -s], [0, s, c]], "y": [[c, 0, s], [0, 1, 0], [-s, 0, c]],
            "z": [[c, -s, 0], [s, c, 0], [0, 0, 1]]}[axis]


def _apply(matrix, v):
    """matrix times the vector v."""
    return tuple(sum(matrix[i][k] * v[k] for k in range(3)) for i in range(3))


def _shade(normal):
    """The light of a face by its world normal: top 1, bottom 0.5, sides facing z 0.85, sides facing x 0.7."""
    if normal[1] > 0.7:
        return 1.0
    if normal[1] < -0.7:
        return 0.5
    return 0.85 if abs(normal[2]) >= abs(normal[0]) else 0.7


def _faces(nodes):
    """Every face of the model: (world corners top-left, +texture x, +texture y; their texels; world normal)."""
    for n, at, turn in placed(nodes):
        shape = n["shape"]
        for side, box, layout in _sides(shape):
            normal, corner, axis_u, axis_v = FRAMES[side]
            half = [s / 2 for s in box]
            width, height = _face_size(side, box)
            local = [[c * h for c, h in zip(corner, half)]]
            local.append(add(local[0], [a * width for a in axis_u]))
            local.append(add(local[0], [a * height for a in axis_v]))
            stretch = [shape["stretch"][k] for k in "xyz"]
            offset = [shape["offset"][k] for k in "xyz"]
            points = [add(at, rotate(turn, add(offset, [c * s for c, s in zip(q, stretch)]))) for q in local]
            yield points, _texels(layout, width, height), rotate(turn, normal)


def _sides(shape):
    """(side, box size, layout) of every textured side: a box's own, or a quad's shown side (both when
    double-sided) as the side of a flat box."""
    size = shape.get("settings", {}).get("size")
    if shape["type"] == "box":
        box = (size["x"], size["y"], size["z"])
        return [(side, box, layout) for side, layout in shape["textureLayout"].items()]
    if shape["type"] != "quad":
        return []
    side = QUAD_SIDES[shape["settings"]["normal"]]
    box = {"front": (size["x"], size["y"], 0), "right": (0, size["y"], size["x"]), "top": (size["x"], 0, size["y"])}
    flat = box.get(side) or box[OPPOSITE[side]]
    layout = shape["textureLayout"]["front"]
    return [(side, flat, layout)] + ([(OPPOSITE[side], flat, layout)] if shape.get("doubleSided") else [])


def _face_size(side, box):
    """(width, height) of a side in texels, as its texture lies on it."""
    sx, sy, sz = box
    return {"front": (sx, sy), "back": (sx, sy), "left": (sz, sy), "right": (sz, sy), "top": (sx, sz),
            "bottom": (sx, sz)}[side]


def _texels(layout, width, height):
    """The layout points read at the face's top-left, +width and +height corners (offset = pivot, mirror, angle)."""
    mirror = layout.get("mirror", {})
    sx, sy = (-1 if mirror.get("x") else 1), (-1 if mirror.get("y") else 1)
    turn = TURNS[layout.get("angle", 0) % 360]
    u, v = layout["offset"]["x"], layout["offset"]["y"]
    return [(u + a, v + b) for a, b in (turn(0, 0), turn(sx * width, 0), turn(0, sy * height))]


def _fill(pixels, depth, corners, texels, code):
    """Writes the face's texel codes into every pixel whose centre it covers and where it is the nearest face."""
    (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = corners
    ex, ey, fx, fy = x1 - x0, y1 - y0, x2 - x0, y2 - y0
    det = ex * fy - ey * fx
    if abs(det) < 1e-9:
        return
    (layout_w, layout_h), shade = code
    xs, ys = [x0, x1, x2, x1 + fx], [y0, y1, y2, y1 + fy]
    for py in range(max(0, math.floor(min(ys))), min(SIZE, math.ceil(max(ys)))):
        for px in range(max(0, math.floor(min(xs))), min(SIZE, math.ceil(max(xs)))):
            dx, dy = px + 0.5 - x0, py + 0.5 - y0
            a, b = (dx * fy - dy * fx) / det, (ex * dy - ey * dx) / det
            if not (-1e-9 <= a <= 1 + 1e-9 and -1e-9 <= b <= 1 + 1e-9):
                continue
            z = z0 + a * (z1 - z0) + b * (z2 - z0)
            if z <= depth[py][px]:
                continue
            depth[py][px] = z
            u = texels[0][0] + a * (texels[1][0] - texels[0][0]) + b * (texels[2][0] - texels[0][0])
            v = texels[0][1] + a * (texels[1][1] - texels[0][1]) + b * (texels[2][1] - texels[0][1])
            pixels[px, py] = (_code(u, layout_w), _code(v, layout_h), round(shade * 255), 255)


def _code(value, span):
    """A layout coordinate as a byte: floor(value * 256 / span); the far edge (value == span) codes 255. A value
    outside the layout is a layout bug and fails, instead of silently reading the edge."""
    assert -1e-6 <= value <= span + 1e-6, ("layout read outside its texture", value, span)
    return max(0, min(255, math.floor(value * 256 / span)))
