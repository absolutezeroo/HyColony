"""Item icons drawn from models: the isometric view of blocks (seen from +x +z, their front) and the turned
orthographic view of held tools (laid diagonally, as Hytale's tool icons)."""

import math

from PIL import Image, ImageChops, ImageDraw

from models import FACE_NORMALS, add, corners, face_point, face_span, multiply, placed, rotate

ICON_SIZE = 64
# Pixels left free around a framed model (frame).
ICON_MARGIN = 3


def frame(nodes, view=None):
    """(scale, origin) fitting the model's view (screen) in the icon, ICON_MARGIN pixels from its edges."""
    points = []
    for n, position, rotation in placed(nodes):
        shape = n["shape"]
        offset, stretch = (tuple(shape[key][a] for a in "xyz") for key in ("offset", "stretch"))
        for corner in corners(shape):
            point = add(position, rotate(rotation, tuple(o + c * s for o, c, s in zip(offset, corner, stretch))))
            points.append(screen(point, 1.0, (0, 0), view))
    low = [min(s[i] for s in points) for i in (0, 1)]
    high = [max(s[i] for s in points) for i in (0, 1)]
    scale = (ICON_SIZE - 2 * ICON_MARGIN) / max(high[0] - low[0], high[1] - low[1])
    origin = tuple(ICON_SIZE / 2 - (low[i] + high[i]) / 2 * scale for i in (0, 1))
    return scale, origin


def iso(x, y, z, scale, origin):
    """Screen position of a model point (units) in the icon's isometric view, seen from +x +z."""
    return (origin[0] + (x - z) * scale * 0.866, origin[1] + (x + z) * scale * 0.5 - y * scale)


def draw_face(icon, texture, corner, edge_u, edge_v, shade):
    """Draws texture onto the screen parallelogram corner + s*edge_u + t*edge_v (s, t in [0, 1])."""
    w, h = texture.size
    det = edge_u[0] * edge_v[1] - edge_u[1] * edge_v[0]
    if abs(det) < 1e-6:
        return
    # Inverse affine map screen -> texture pixel, as Image.transform expects.
    a, b = edge_v[1] / det * w, -edge_v[0] / det * w
    d, e = -edge_u[1] / det * h, edge_u[0] / det * h
    c = -(a * corner[0] + b * corner[1])
    f = -(d * corner[0] + e * corner[1])
    face = texture.transform(icon.size, Image.AFFINE, (a, b, c, d, e, f), resample=Image.NEAREST)
    if shade != 1.0:
        rgb = face.convert("RGB").point(lambda p: int(p * shade))
        face = Image.merge("RGBA", (*rgb.split(), face.getchannel("A")))
    mask = Image.new("L", icon.size, 0)
    points = [corner, (corner[0] + edge_u[0], corner[1] + edge_u[1]),
              (corner[0] + edge_u[0] + edge_v[0], corner[1] + edge_u[1] + edge_v[1]),
              (corner[0] + edge_v[0], corner[1] + edge_v[1])]
    ImageDraw.Draw(mask).polygon(points, fill=255)
    face.putalpha(ImageChops.multiply(face.getchannel("A"), mask))
    icon.alpha_composite(face)


def draw_box(icon, box, textures, scale, origin):
    """Draws an axis-aligned box ((x0, y0, z0), (x1, y1, z1)) with its +x, +z and top faces textured."""
    (x0, y0, z0), (x1, y1, z1) = box

    def p(x, y, z):
        return iso(x, y, z, scale, origin)

    def edge(a, b):
        return (b[0] - a[0], b[1] - a[1])

    front = p(x0, y1, z1)
    draw_face(icon, textures["front"], front, edge(front, p(x1, y1, z1)), edge(front, p(x0, y0, z1)), 0.8)
    right = p(x1, y1, z1)
    draw_face(icon, textures["right"], right, edge(right, p(x1, y1, z0)), edge(right, p(x1, y0, z1)), 0.65)
    top = p(x0, y1, z0)
    draw_face(icon, textures["top"], top, edge(top, p(x1, y1, z0)), edge(top, p(x0, y1, z1)), 1.0)


def screen(point, scale, origin, view=None):
    """(x, y, depth) of a model point on the icon, the larger depth the nearer: the isometric view from +x +z, or with
    view (a quaternion turning the model in front of the camera, which looks down -z) an orthographic one."""
    if view is None:
        return (*iso(*point, scale, origin), sum(point))
    x, y, z = rotate(view, point)
    return origin[0] + x * scale, origin[1] - y * scale, z


def turned(yaw, pitch, roll):
    """The view (a quaternion for screen) turning a model by yaw about y, then pitch about x, then roll about the
    camera's axis, in degrees: roll 45 lays a tool diagonally, its head top left, as Hytale's tool icons."""
    def about(axis, degrees):
        half = math.radians(degrees) / 2
        return (*(c * math.sin(half) for c in axis), math.cos(half))

    return multiply(about((0, 0, 1), roll), multiply(about((1, 0, 0), pitch), about((0, 1, 0), yaw)))


def draw_model(icon, nodes, texture, scale, origin, view=None):
    """Draws a model's boxes, nested and turned as they are, with a depth buffer (boxes that pass through each other
    hide each other correctly), each face read from its texture island and shaded by how it faces the view: in the
    isometric view from +x +z, which shows a block's front (+z), top 1.0, +z 0.8, +x 0.65 as draw_box; with view,
    which turns the model instead (screen), lit from the top left (turned_shade)."""
    texels, out, nearest = texture.load(), icon.load(), {}
    for n, position, rotation in placed(nodes):
        shape = n["shape"]
        if shape["type"] == "none":
            continue
        if shape["type"] != "box":
            raise SystemExit(f"{n['name']}: draw_model draws boxes only")
        offset, stretch = (tuple(shape[key][a] for a in "xyz") for key in ("offset", "stretch"))
        size = tuple(shape["settings"]["size"][a] for a in "xyz")
        for side, face in shape["textureLayout"].items():
            if face.get("angle", 0) or any(face.get("mirror", {}).values()):
                raise SystemExit(f"{n['name']} {side}: draw_model reads unturned, unmirrored faces")
            normal = rotate(rotation, FACE_NORMALS[side])
            shade = facing_shade(normal) if view is None else turned_shade(rotate(view, normal))
            if shade is None:
                continue
            for local, texel in face_samples(side, size, face, scale * max(abs(s) for s in stretch)):
                stretched = tuple(o + c * s for o, c, s in zip(offset, local, stretch))
                sx, sy, depth = screen(add(position, rotate(rotation, stretched)), scale, origin, view)
                pixel = (int(sx), int(sy))
                colour = texels[texel]
                if colour[3] and 0 <= pixel[0] < icon.width and 0 <= pixel[1] < icon.height \
                        and depth > nearest.get(pixel, float("-inf")):
                    nearest[pixel] = depth
                    out[pixel] = (*(int(c * shade) for c in colour[:3]), 255)


def facing_shade(normal):
    """The shade of a face whose normal is this, seen from +x +y +z; None when it faces away."""
    x, y, z = normal
    if x + y + z <= 1e-6:
        return None
    return y * y * 1.0 + z * z * 0.8 + x * x * 0.65


def turned_shade(normal):
    """The shade of a face whose normal, in the camera's frame, is this (the camera looks down -z), lit from the top
    left like Hytale's item icons; None when it faces away."""
    x, y, z = normal
    if z <= 1e-6:
        return None
    return 0.72 + 0.28 * max(0.0, -0.5 * x + 0.6 * y + 0.62 * z)


def face_samples(side, size, face, density):
    """(point about the box's centre, texel) pairs covering a face, about two samples per icon pixel each way, as
    Blockbench lays faces out (u right, v down)."""
    w, h = face_span(side, size)
    columns, rows = max(1, math.ceil(w * density * 2)), max(1, math.ceil(h * density * 2))
    u0, v0 = face["offset"]["x"], face["offset"]["y"]
    for i in range(columns):
        for j in range(rows):
            s, t = (i + 0.5) / columns * w, (j + 0.5) / rows * h
            yield face_point(side, size, s, t), (int(u0 + s), int(v0 + t))
