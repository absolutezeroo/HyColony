"""Stepped hitboxes that follow a model's top surface, for shapes a single box would fill wrongly (a DO slope is one
block; Hytale's shallow and steep roof hitboxes span two)."""

import math

CELLS = 4  # columns per block side
PIXELS = 16  # Minecraft pixels per block


def stepped(model):
    """Boxes (block units) of model (Minecraft pixels): the block cut into CELLS x CELLS columns, each rising from
    the floor to the model's top over its centre (rounded up to a pixel), columns of one height along x merged."""
    size = PIXELS / CELLS
    boxes = []
    for cz in range(CELLS):
        tops = [_top(model, (cx + 0.5) * size, (cz + 0.5) * size) for cx in range(CELLS)]
        cx = 0
        while cx < CELLS:
            end = cx
            while end + 1 < CELLS and tops[end + 1] == tops[cx]:
                end += 1
            if tops[cx] > 0:
                boxes.append({"Min": {"X": cx / CELLS, "Y": 0, "Z": cz / CELLS},
                              "Max": {"X": (end + 1) / CELLS, "Y": tops[cx] / PIXELS, "Z": (cz + 1) / CELLS}})
            cx = end + 1
    return boxes


def _top(model, x, z):
    """The highest point (whole pixels, 0 to 16) where the vertical line through (x, z) meets model; 0 if none."""
    best = 0.0
    for element in model["elements"]:
        top = _element_top(element, x, z)
        if top is not None:
            best = max(best, top)
    return min(PIXELS, math.ceil(best - 1e-6)) if best > 0 else 0


def _element_top(element, x, z):
    """Where the vertical line through (x, z) leaves element (its inner tilt undone, as assemble.world_points
    applies it), or None when the line misses it."""
    low, high = element["from"], element["to"]
    start, step = [x, 0.0, z], [0.0, 1.0, 0.0]  # the line: start + y * step
    rotation = element.get("rotation")
    if rotation and rotation.get("angle"):
        axis = "xyz".index(rotation["axis"])
        j, k = [i for i in range(3) if i != axis]
        angle = math.radians(rotation["angle"])
        cos, sin = math.cos(angle), math.sin(angle) * (-1 if axis == 1 else 1)
        origin = rotation["origin"]
        u, v = start[j] - origin[j], start[k] - origin[k]
        start[j], start[k] = origin[j] + u * cos + v * sin, origin[k] - u * sin + v * cos
        du, dv = step[j], step[k]
        step[j], step[k] = du * cos + dv * sin, -du * sin + dv * cos
    enter, leave = -math.inf, math.inf
    for i in range(3):
        if abs(step[i]) < 1e-9:
            if not low[i] - 1e-6 <= start[i] <= high[i] + 1e-6:
                return None
            continue
        a, b = (low[i] - start[i]) / step[i], (high[i] - start[i]) / step[i]
        enter, leave = max(enter, min(a, b)), min(leave, max(a, b))
    return leave if enter <= leave else None
