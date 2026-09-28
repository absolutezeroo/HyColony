"""Assembles one Domum Ornamentum blockstate state into the model's own Minecraft-format geometry
({"textures", "elements"}), oriented so the family's canonical facing=north state faces Hytale's front (-Z).

A DO block is not one static model: its blockstate picks a single "variants" entry or merges several
"multipart" pieces, each carrying its own baked x/y rotation (Minecraft's block model rotation, always a
multiple of 90 degrees around the block's centre). This module reproduces that assembly so downstream
conversion (convert.py, faces.py) sees the block as Minecraft renders it, not one arbitrary model file
(docs/research/domum-ornamentum.md B.9, "cause commune").
"""

import math

import source

PIVOT = 8  # block centre, in Minecraft pixels; every 90-degree model rotation turns around it
_Y_FACES = ("north", "east", "south", "west")  # rotate_y's face cycle, clockwise viewed from above
_X_FACES = ("up", "north", "down", "south")  # rotate_x's face cycle (east/west are untouched)

# Every property's own default value, restricted below to what the block actually declares. Sourced from
# Domum Ornamentum's registerDefaultState calls (commit source.COMMIT): FACING north (every
# HorizontalDirectionalBlock), HALF bottom (vanilla StairBlock/TrapDoorBlock, DO's PanelBlock) or lower
# (vanilla DoorBlock, unchanged by DO's AbstractBlockDoor), HINGE left, OPEN false, SHAPE straight
# (StairBlock), TYPE full (door/trapdoor/panel families, block/vanilla/DoorBlock.java and siblings) or
# plain (block/decorative/PostBlock.java), CONDITIONAL true (the post's UPRIGHT property,
# block/AbstractPostBlock.java). An unlisted property (a fence/pane connection, "in_wall"...) falls back to
# false, then to its lowest value.
_PREFERRED = {
    "facing": ("north",),
    "half": ("bottom", "lower"),
    "hinge": ("left",),
    "open": ("false",),
    "shape": ("straight",),
    "type": ("full", "plain"),
    "conditional": ("true",),
}

# How an element's own inner tilt ("rotation": {"axis": ..., "angle": ...}) is carried through one
# 90-degree turn of the whole model: axis=1 is rotate_y (permutes x/z), axis=0 is rotate_x (permutes y/z).
# Each entry is (new axis name, angle sign): the sign flips when the turn reverses that axis's sense.
_AXIS_MAP = {
    1: {"x": ("z", 1), "y": ("y", 1), "z": ("x", -1)},
    0: {"x": ("x", 1), "y": ("z", -1), "z": ("y", 1)},
}


def parts(state, props):
    """The multipart entries whose "when" matches props, or the single matching "variants" entry, each as
    an apply dict ({"model", "x", "y", "uvlock"}, the optional keys only present when DO's blockstate sets
    them). A property missing from props takes the block's own default value (see _PREFERRED); a "when"
    value may be "a|b" to match either.
    """
    possible = property_values(state)
    resolved = dict(props)
    for key, values in possible.items():
        resolved.setdefault(key, _default_value(key, values))
    if "multipart" in state:
        return [entry["apply"] for entry in state["multipart"] if _when_matches(entry.get("when"), resolved)]
    key = ",".join(f"{name}={resolved[name]}" for name in sorted(possible))
    return [state["variants"][key]]


def property_values(state):
    """Every value each property of a blockstate can take ({property: set}), from its "when" clauses or variant
    keys."""
    values = {}
    if "multipart" in state:
        for entry in state["multipart"]:
            _collect_when(entry.get("when") or {}, values)
        return values
    for key_str in state["variants"]:
        for pair in key_str.split(","):
            if not pair:
                continue
            key, _, value = pair.partition("=")
            values.setdefault(key, set()).add(value)
    return values


def _collect_when(when, values):
    for key, value in when.items():
        if key in ("OR", "AND"):
            for condition in value:
                _collect_when(condition, values)
        else:
            values.setdefault(key, set()).update(str(value).split("|"))


def _default_value(key, possible):
    for candidate in _PREFERRED.get(key, ()):
        if candidate in possible:
            return candidate
    return "false" if "false" in possible else min(possible)


def _when_matches(when, props):
    """Minecraft's multipart condition (MC MultiPart / KeyValueCondition): no condition matches everything; an
    "OR" or "AND" key holds a list of conditions; otherwise every property must take one of its "a|b" values."""
    if not when:
        return True
    if "OR" in when:
        return any(_when_matches(c, props) for c in when["OR"])
    if "AND" in when:
        return all(_when_matches(c, props) for c in when["AND"])
    return all(props.get(key) in str(value).split("|") for key, value in when.items())


def rotate_y(model, degrees, uvlock=False):
    """Turns every element (and its own inner tilt) by degrees (a multiple of 90) around the block's
    vertical axis through PIVOT: Minecraft's "y" model rotation, clockwise viewed from above
    (north -> east). Face keys follow the turn; up/down faces are unchanged. uv is unchanged unless
    uvlock and degrees actually rotate the model, in which case every face's uv is recomputed (see
    _rotate_element) so its texture stays aligned to the world instead of spinning with the block."""
    return _rotate(model, degrees, axis=1, uvlock=uvlock)


def rotate_x(model, degrees, uvlock=False):
    """Turns every element the same way as rotate_y, around the block's east-west axis (Minecraft's "x"
    model rotation): up -> north -> down -> south at +90. East/west faces are unchanged; uvlock behaves
    as in rotate_y."""
    return _rotate(model, degrees, axis=0, uvlock=uvlock)


def _rotate(model, degrees, axis, uvlock):
    k = (degrees // 90) % 4
    cycle = _Y_FACES if axis == 1 else _X_FACES
    face_map = {cycle[i]: cycle[(i + k) % 4] for i in range(4)}
    elements = [_rotate_element(e, k, axis, face_map, uvlock) for e in model["elements"]]
    return {"textures": model["textures"], "elements": elements}


def _rotate_element(element, k, axis, face_map, uvlock):
    """One element turned k times: from/to recomputed as the new bounding corners, faces relabelled by
    face_map. With uvlock and k != 0 (an actual rotation, MC: FaceBakery's uvlock recompute), every face's
    uv is replaced by Minecraft's default uv for its new direction at the new bounds, so a texture that
    would otherwise spin with the block stays aligned to the world; without uvlock (or at k == 0, no
    rotation to compensate for) every uv is left exactly as authored."""
    low = _rotate_point(element["from"], k, axis)
    high = _rotate_point(element["to"], k, axis)
    new_from = [min(low[i], high[i]) for i in range(3)]
    new_to = [max(low[i], high[i]) for i in range(3)]
    faces = {}
    for direction, face in element["faces"].items():
        new_direction = face_map.get(direction, direction)
        if uvlock and k:
            face = {**face, "uv": list(default_uv(new_direction, new_from, new_to))}
        faces[new_direction] = face
    result = {**element, "from": new_from, "to": new_to, "faces": faces}
    if "rotation" in element:
        result["rotation"] = _rotate_inner(element["rotation"], k, axis)
    return result


def default_uv(direction, low, high):
    """Minecraft's uv for a face that declares none: the element's own position on the face (MC
    BlockElement.uvsByFace). Used by uvlock (_rotate_element) to keep a texture world-aligned after a rotation,
    and by convert.py for faces without uv."""
    (x1, y1, z1), (x2, y2, z2) = low, high
    return {
        "north": (16 - x2, 16 - y2, 16 - x1, 16 - y1),
        "south": (x1, 16 - y2, x2, 16 - y1),
        "west": (z1, 16 - y2, z2, 16 - y1),
        "east": (16 - z2, 16 - y2, 16 - z1, 16 - y1),
        "up": (x1, z1, x2, z2),
        "down": (x1, 16 - z2, x2, 16 - z1),
    }[direction]


def _rotate_point(point, k, axis):
    """point turned k times by 90 degrees around PIVOT: the y-axis turn permutes (x, z), the x-axis turn
    permutes (y, z), each step matching rotate_y's/rotate_x's own direction."""
    x, y, z = point[0] - PIVOT, point[1] - PIVOT, point[2] - PIVOT
    for _ in range(k):
        if axis == 1:
            x, z = -z, x
        else:
            y, z = z, -y
    return [x + PIVOT, y + PIVOT, z + PIVOT]


def _rotate_inner(rotation, k, axis):
    new_axis, sign = rotation["axis"], 1
    for _ in range(k):
        new_axis, flip = _AXIS_MAP[axis][new_axis]
        sign *= flip
    return {
        **rotation,
        "origin": _rotate_point(rotation["origin"], k, axis),
        "axis": new_axis,
        "angle": rotation["angle"] * sign,
    }


def state_model(root, family, block_id, props):
    """One DO block's blockstate state assembled into MC-format geometry: the selected variant or merged
    multipart parts, each rotated by its own baked x then y around PIVOT. The state keeps that rotation: a
    facing=north state already faces Hytale's front (-Z), as Minecraft renders it (Minecraft's north is -Z
    too), except for a family whose hinge convention differs from Hytale's: it is then turned by family.turn_y
    (trapdoors and panels, see families.py). A property missing from props takes the block's own default value
    (see parts()).

    A selected part with "uvlock": true (DO uses it for e.g. trapdoors, panels, pillar columns) keeps its
    texture world-aligned: rotate_x/rotate_y then recompute that part's uv instead of carrying the
    authored uv along with the rotated geometry.
    """
    state = source.blockstate(root, block_id)
    merged = {"textures": {}, "elements": []}
    for index, apply in enumerate(parts(state, props)):
        uvlock = bool(apply.get("uvlock"))
        name = apply["model"].removeprefix(source.DO_PARENT)
        raw = source.load(root, name)
        model = rotate_y(rotate_x(raw, apply.get("x", 0), uvlock), apply.get("y", 0), uvlock)
        part = _prefixed(model, f"e{index}_")
        merged["textures"].update(part["textures"])
        merged["elements"].extend(part["elements"])
    return rotate_y(merged, family.turn_y)


def _prefixed(model, prefix):
    """One part's model with every local texture placeholder ("#key") renamed under prefix, so several
    multipart pieces merged by state_model never share a placeholder name."""

    def renamed(ref):
        return "#" + prefix + ref[1:] if ref.startswith("#") else ref

    textures = {prefix + key: renamed(value) for key, value in model["textures"].items()}
    elements = []
    for element in model["elements"]:
        faces = {d: {**face, "texture": renamed(face["texture"])} for d, face in element["faces"].items()}
        elements.append({**element, "faces": faces})
    return {"textures": textures, "elements": elements}


def world_points(element):
    """The 8 corners of element in block space, after its own inner tilt ("rotation": axis, angle, origin) as
    Minecraft applies it; used to measure a model's real shape (a slope's high side, a hitbox)."""
    corners = [[x, y, z] for x in (element["from"][0], element["to"][0])
               for y in (element["from"][1], element["to"][1])
               for z in (element["from"][2], element["to"][2])]
    rotation = element.get("rotation")
    if not rotation or not rotation.get("angle"):
        return corners
    axis = "xyz".index(rotation["axis"])
    j, k = [i for i in range(3) if i != axis]
    angle = math.radians(rotation["angle"])
    # Minecraft turns counter-clockwise about +x and +z but clockwise about +y (FaceBakery.rotateVertexBy).
    sign = -1 if axis == 1 else 1
    cos, sin = math.cos(angle), math.sin(angle) * sign
    origin = rotation["origin"]
    turned = []
    for point in corners:
        u, v = point[j] - origin[j], point[k] - origin[k]
        point = list(point)
        point[j] = origin[j] + u * cos - v * sin
        point[k] = origin[k] + u * sin + v * cos
        turned.append(point)
    return turned
