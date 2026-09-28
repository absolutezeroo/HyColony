"""Cleans Domum Ornamentum geometry that renders badly in Hytale: faces of the same direction lying in (or
almost in) the same plane and overlapping z-fight, and pillar ends that DO leaves open because a neighbour
hides them (docs/research/domum-ornamentum.md B.9, points 1 and 4). Works on assembled MC-format models
({"textures", "elements"}); elements with an inner tilt are left alone, their faces are not axis-aligned.
"""

import copy

MIN_GAP_PX = 0.05  # Minecraft pixels between two parallel overlapping faces (0.1 Hytale unit)
EPSILON = 1e-6

# direction -> (normal axis, the element bound holding its plane: 0 = from, 1 = to, outward sign, rect axes)
_PLANES = {
    "north": (2, 0, -1, (0, 1)),
    "south": (2, 1, 1, (0, 1)),
    "west": (0, 0, -1, (2, 1)),
    "east": (0, 1, 1, (2, 1)),
    "down": (1, 0, -1, (0, 2)),
    "up": (1, 1, 1, (0, 2)),
}


def overlapping_pairs(model):
    """Every pair (element index, other index, direction) of same-direction faces of axis-aligned elements whose
    planes are closer than MIN_GAP_PX and whose rectangles overlap with a positive area."""
    faces = _faces(model["elements"])
    pairs = []
    for a in range(len(faces)):
        for b in range(a + 1, len(faces)):
            fa, fb = faces[a], faces[b]
            if fa[1] == fb[1] and abs(fa[2] - fb[2]) < MIN_GAP_PX - EPSILON and _overlap(fa[3], fb[3]) > EPSILON:
                pairs.append((fa[0], fb[0], fa[1]))
    return pairs


def clean(model):
    """model with every overlapping pair resolved: the face behind (the larger one when both share a plane, a
    detail usually sits on a backing) is removed when the front face hides it entirely, otherwise moved back to
    MIN_GAP_PX by shrinking its element on that side. Repeats until no pair is left (bounded by the face count)."""
    result = copy.deepcopy(model)
    elements = result["elements"]
    for _ in range(sum(len(e["faces"]) for e in elements) + 1):
        pairs = overlapping_pairs(result)
        if not pairs:
            return result
        _resolve(elements, *pairs[0])
    raise ValueError("faces.clean did not converge")


def _resolve(elements, i, j, direction):
    axis, bound, outward, rect_axes = _PLANES[direction]
    plane_i, plane_j = elements[i][_key(bound)][axis], elements[j][_key(bound)][axis]
    rect_i, rect_j = _rect(elements[i], rect_axes), _rect(elements[j], rect_axes)
    if abs(plane_i - plane_j) < EPSILON:
        back, front = (i, j) if _area(rect_i) >= _area(rect_j) else (j, i)
    else:
        back, front = (i, j) if (plane_i - plane_j) * outward < 0 else (j, i)
    if _covers(_rect(elements[front], rect_axes), _rect(elements[back], rect_axes)):
        del elements[back]["faces"][direction]
        return
    element = elements[back]
    target = elements[front][_key(bound)][axis] - outward * MIN_GAP_PX
    opposite = element["to" if bound == 0 else "from"][axis]
    if (target - opposite) * outward <= EPSILON:  # too thin to move back: the face goes instead
        del element["faces"][direction]
        return
    element[_key(bound)] = list(element[_key(bound)])
    element[_key(bound)][axis] = target


def cap_ends(model):
    """model with the up/down face added to every axis-aligned element touching the block's top (y = 16) or
    bottom (y = 0) without one, textured like its first side face, at Minecraft's default uv. Added caps that
    overlap in one plane (blockpillar's eight blades) become a single lid over their combined bounds."""
    result = copy.deepcopy(model)
    lids = {}
    for index, element in enumerate(result["elements"]):
        if element.get("rotation", {}).get("angle") or not element["faces"]:
            continue
        side = next(iter(element["faces"].values()))
        for direction, y_bound, y in (("up", "to", 16), ("down", "from", 0)):
            if element[y_bound][1] == y and direction not in element["faces"]:
                lids.setdefault(direction, []).append((index, side["texture"]))
    for direction, caps in lids.items():
        _add_caps(result["elements"], direction, caps)
    return result


def _add_caps(elements, direction, caps):
    rects = [_rect(elements[i], (0, 2)) for i, _ in caps]
    shared = len(caps) > 1 and all(_overlap(a, b) > EPSILON for a in rects for b in rects if a is not b)
    if not shared:
        for index, texture in caps:
            elements[index]["faces"][direction] = {"texture": texture}
        return
    y = 16 if direction == "up" else 0
    low = [min(r[0] for r in rects), y, min(r[1] for r in rects)]
    high = [max(r[2] for r in rects), y, max(r[3] for r in rects)]
    elements.append({"from": low, "to": high, "faces": {direction: {"texture": caps[0][1]}}})


def _faces(elements):
    faces = []
    for index, element in enumerate(elements):
        if element.get("rotation", {}).get("angle"):
            continue
        for direction in element["faces"]:
            axis, bound, _, rect_axes = _PLANES[direction]
            faces.append((index, direction, element[_key(bound)][axis], _rect(element, rect_axes)))
    return faces


def _key(bound):
    return "from" if bound == 0 else "to"


def _rect(element, axes):
    a, b = axes
    return (element["from"][a], element["from"][b], element["to"][a], element["to"][b])


def _overlap(r, s):
    width = min(r[2], s[2]) - max(r[0], s[0])
    height = min(r[3], s[3]) - max(r[1], s[1])
    return width * height if width > 0 and height > 0 else 0


def _area(r):
    return (r[2] - r[0]) * (r[3] - r[1])


def _covers(outer, inner):
    return (outer[0] <= inner[0] + EPSILON and outer[1] <= inner[1] + EPSILON
            and outer[2] >= inner[2] - EPSILON and outer[3] >= inner[3] - EPSILON)
