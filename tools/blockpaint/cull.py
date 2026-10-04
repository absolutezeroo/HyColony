"""Drops the box faces no one can see: those wholly pressed against or buried in other boxes of the model (spec
2026-10-02 hut models, § painting). Each face kept costs an island in the texture and two triangles in game.

Exact and conservative: a face is hidden when the boxes lying just in front of it, square with it, cover its whole
rectangle with no gap. A box turned against the face, an open box (one missing a face that neither lies wholly on the
floor nor is closed by other sealed boxes, in front of it or plugging it from inside) or an invisible one hides
nothing: one could see past its edge or into it. A face an earlier pass dropped keeps sealing its box, so a second
pass drops the same faces. A mirrored box (a negative stretch) is judged by where its faces are drawn. Moving nodes
(animated in their model's blockyanim, and their children) neither lose faces nor hide others: their motion uncovers
what they cover at rest. cull never adds a face back."""

from models import FACE_NORMALS, add, placed, rotate, walk

# How far in front of a face a covering box must reach, in model units.
DEPTH = 1e-3
# Gaps narrower than this, in model units, are float noise, not openings.
SLIVER = 1e-4
# Model units of the floor's height, for a box standing on it without its bottom.
FLOOR = 1e-3


def cull(nodes, moving=frozenset()):
    """Removes the hidden faces from the textureLayout of the model's boxes, the nodes named in moving and their
    children excepted; returns the (node name, side) dropped, in model order."""
    still = still_boxes(nodes, moving)
    covering = sealed(still)
    dropped = []
    for box in still:
        for side in list(box["node"]["shape"]["textureLayout"]):
            if covered(box, side, [other for other in covering if other is not box]):
                del box["node"]["shape"]["textureLayout"][side]
                dropped.append((box["node"]["name"], side))
    return dropped


def still_boxes(nodes, moving):
    """The model's boxes outside the moving subtrees: their node, placement, half extents (stretch applied) and the
    sides they showed before culling."""
    skipped = {id(n) for top in walk(nodes) if top["name"] in moving for n in walk([top])}
    out = []
    for n, position, rotation in placed(nodes):
        shape = n["shape"]
        if shape["type"] != "box" or id(n) in skipped:
            continue
        stretch = tuple(shape["stretch"][a] for a in "xyz")
        half = tuple(abs(shape["settings"]["size"][a] * shape["stretch"][a]) / 2 for a in "xyz")
        out.append({"node": n, "position": position, "rotation": rotation, "half": half, "stretch": stretch,
                    "offset": tuple(shape["offset"][a] for a in "xyz"), "sides": frozenset(shape["textureLayout"]),
                    "visible": shape.get("visible", True)})
    return out


def drawn_at(box, side):
    """(axis, sign): where the face side is drawn about the box's centre. A negative stretch (Blockbench's mirror)
    draws it on the other side, as icons.py and bake.py place its points; None for a flat box (a zero stretch)."""
    axis = next(i for i, c in enumerate(FACE_NORMALS[side]) if c)
    if box["stretch"][axis] == 0:
        return None
    return axis, FACE_NORMALS[side][axis] * (1 if box["stretch"][axis] > 0 else -1)


def sealed(boxes):
    """The boxes one cannot see into: visible, each missing side lying wholly on the floor (a bottom) or closed by
    sealed boxes, in front of it (as an earlier pass left a face others hide) or plugging it from inside (a pot's stew
    flush with its open top, a rim round the border). Starts from every visible box and drops those with an open side
    until none is left (two boxes pressed together seal each other)."""
    inside = [box for box in boxes if box["visible"]]
    while True:
        still_sealed = [box for box in inside if all(
            on_floor(box, side) or covered(box, side, [other for other in inside if other is not box], True)
            for side in set(FACE_NORMALS) - box["sides"])]
        if len(still_sealed) == len(inside):
            return inside
        inside = still_sealed


def on_floor(box, side):
    """Whether the face side is a bottom lying wholly on the floor."""
    drawn = drawn_at(box, side)
    if side != "bottom" or drawn is None:
        return False
    axis, sign = drawn
    return all(to_world(box, corner)[1] <= FLOOR for corner in corners(box) if corner[axis] * sign > 0)


def covered(box, side, others, plugged=False):
    """Whether the boxes others, square with the box and reaching DEPTH in front of its face side (or, when plugged,
    DEPTH behind it too: what fills an opening from inside), cover all of it; never for a face of a flat box."""
    if 0 in box["stretch"] or drawn_at(box, side) is None:
        return False
    axis, sign = drawn_at(box, side)
    depths = (sign * (box["half"][axis] + DEPTH), sign * (box["half"][axis] - DEPTH))[:2 if plugged else 1]
    across = [i for i in range(3) if i != axis]
    rects = []
    for other in others:
        low, high = extent_in(box, other)
        if low is not None and any(low[axis] < depth < high[axis] for depth in depths):
            rects.append(tuple((low[i], high[i]) for i in across))
    face = tuple((-box["half"][i], box["half"][i]) for i in across)
    return filled(face, rects)


def extent_in(box, other):
    """(low, high) of the box other in box's own frame, or (None, None) when other is not square with it."""
    points = [to_frame(box, to_world(other, corner)) for corner in corners(other)]
    low = tuple(min(p[i] for p in points) for i in range(3))
    high = tuple(max(p[i] for p in points) for i in range(3))
    # Square with the frame exactly when the frame's bounding box is no bigger than the box itself.
    volume = 8 * other["half"][0] * other["half"][1] * other["half"][2]
    bounding = (high[0] - low[0]) * (high[1] - low[1]) * (high[2] - low[2])
    return (low, high) if bounding <= volume * (1 + 1e-6) + 1e-9 else (None, None)


def filled(face, rects):
    """Whether the rectangles rects ((u0, u1), (v0, v1)) cover the rectangle face with no gap wider than SLIVER: every
    cell of the grid their edges cut the face into lies in one of them."""
    (u0, u1), (v0, v1) = face
    us = sorted({u0, u1, *(min(max(c, u0), u1) for r in rects for c in r[0])})
    vs = sorted({v0, v1, *(min(max(c, v0), v1) for r in rects for c in r[1])})
    for a, b in zip(us, us[1:]):
        for c, d in zip(vs, vs[1:]):
            if b - a < SLIVER or d - c < SLIVER:
                continue
            u, v = (a + b) / 2, (c + d) / 2
            if not any(r[0][0] < u < r[0][1] and r[1][0] < v < r[1][1] for r in rects):
                return False
    return True


def corners(box):
    return [(sx * box["half"][0], sy * box["half"][1], sz * box["half"][2])
            for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)]


def to_world(box, local):
    """A point given about the box's centre (stretch applied), in model space."""
    return add(box["position"], rotate(box["rotation"], add(box["offset"], local)))


def to_frame(box, point):
    """A model-space point about the box's centre, in the box's axes."""
    x, y, z, w = box["rotation"]
    relative = tuple(p - q for p, q in zip(point, box["position"]))
    return tuple(a - b for a, b in zip(rotate((-x, -y, -z, w), relative), box["offset"]))
