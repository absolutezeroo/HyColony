"""Isometric icon of a converted model: every face turned towards the viewer, far faces first."""

import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "decorations"))
from models import add, rotate  # noqa: E402
from pack import ICON_SIZE, draw_face, iso  # noqa: E402

# Direction -> (outward normal, the face's top-left corner as signs of the half size, u edge, v edge), the picture
# lying on the face as Minecraft shows it.
FACES = {
    "south": ((0, 0, 1), (-1, 1, 1), (1, 0, 0), (0, -1, 0)),
    "north": ((0, 0, -1), (1, 1, -1), (-1, 0, 0), (0, -1, 0)),
    "east": ((1, 0, 0), (1, 1, 1), (0, 0, -1), (0, -1, 0)),
    "west": ((-1, 0, 0), (-1, 1, -1), (0, 0, 1), (0, -1, 0)),
    "up": ((0, 1, 0), (-1, 1, -1), (1, 0, 0), (0, 0, 1)),
    "down": ((0, -1, 0), (-1, -1, 1), (1, 0, 0), (0, 0, -1)),
}
SHADES = {"up": 1.0, "south": 0.8, "north": 0.8, "east": 0.65, "west": 0.65, "down": 0.5}
VIEW = (1, 1, 1)  # iso() looks along -VIEW
MARGIN = 4


def render(elements):
    """The icon of elements (Converter.convert's third result), fitted into ICON_SIZE."""
    faces = []
    for element in elements:
        for direction, picture in element["faces"].items():
            faces.append(face(element, direction, picture))
    faces = [f for f in faces if dot(f[1], VIEW) > 1e-6 or f[4]]
    points = [p for f in faces for p in f[2]]
    if not points:
        return Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
    flat = [iso(*p, 1, (0, 0)) for p in points]
    low = (min(p[0] for p in flat), min(p[1] for p in flat))
    high = (max(p[0] for p in flat), max(p[1] for p in flat))
    scale = (ICON_SIZE - 2 * MARGIN) / max(high[0] - low[0], high[1] - low[1], 1)
    origin = (ICON_SIZE / 2 - (low[0] + high[0]) / 2 * scale, ICON_SIZE / 2 - (low[1] + high[1]) / 2 * scale)
    icon = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
    for depth, _, (corner, u_end, v_end), direction, _, picture in sorted(faces, key=lambda f: f[0]):
        start = iso(*corner, scale, origin)
        edge_u = sub(iso(*u_end, scale, origin), start)
        edge_v = sub(iso(*v_end, scale, origin), start)
        draw_face(icon, picture, start, edge_u, edge_v, SHADES[direction])
    return icon


def face(element, direction, picture):
    """(depth, world normal, (top-left, top-right, bottom-left corners), direction, flat, picture)."""
    normal, corner, u, v = FACES[direction]
    half = [s / 2 for s in element["size"]]
    q = element["orientation"]

    def world(local):
        return add(element["position"], rotate(q, add(element["offset"], local)))

    start = [c * h for c, h in zip(corner, half)]
    u_end = [s + 2 * e * h for s, e, h in zip(start, u, half)]
    v_end = [s + 2 * e * h for s, e, h in zip(start, v, half)]
    corners = (world(start), world(u_end), world(v_end))
    centre = [(a + b) / 2 for a, b in zip(corners[1], corners[2])]
    flat = any(s <= 0 for s in element["size"])
    return dot(centre, VIEW), rotate(q, normal), corners, direction, flat, picture


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1])
