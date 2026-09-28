"""Assembles one Domum Ornamentum blockstate state into the model's own Minecraft-format geometry
({"textures", "elements"}), oriented so the family's canonical facing=north state faces Hytale's front (-Z).

A DO block is not one static model: its blockstate picks a single "variants" entry or merges several
"multipart" pieces, each carrying its own baked x/y rotation (Minecraft's block model rotation, always a
multiple of 90 degrees around the block's centre). This module reproduces that assembly so downstream
conversion (convert.py, faces.py) sees the block as Minecraft renders it, not one arbitrary model file
(docs/research/domum-ornamentum.md B.9, "cause commune").
"""

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
    possible = _possible_values(state)
    resolved = dict(props)
    for key, values in possible.items():
        resolved.setdefault(key, _default_value(key, values))
    if "multipart" in state:
        return [entry["apply"] for entry in state["multipart"] if _when_matches(entry.get("when"), resolved)]
    key = ",".join(f"{name}={resolved[name]}" for name in sorted(possible))
    return [state["variants"][key]]


def _possible_values(state):
    """Every value seen for each property across this blockstate's "when" clauses or variant keys."""
    values = {}
    if "multipart" in state:
        for entry in state["multipart"]:
            for key, value in (entry.get("when") or {}).items():
                values.setdefault(key, set()).update(value.split("|"))
        return values
    for key_str in state["variants"]:
        for pair in key_str.split(","):
            if not pair:
                continue
            key, _, value = pair.partition("=")
            values.setdefault(key, set()).add(value)
    return values


def _default_value(key, possible):
    for candidate in _PREFERRED.get(key, ()):
        if candidate in possible:
            return candidate
    return "false" if "false" in possible else min(possible)


def _when_matches(when, props):
    return not when or all(props.get(key) in value.split("|") for key, value in when.items())


def rotate_y(model, degrees):
    """Turns every element (and its own inner tilt) by degrees (a multiple of 90) around the block's
    vertical axis through PIVOT: Minecraft's "y" model rotation, clockwise viewed from above
    (north -> east). Face keys follow the turn; up/down faces and every uv are unchanged."""
    return _rotate(model, degrees, axis=1)


def rotate_x(model, degrees):
    """Turns every element the same way as rotate_y, around the block's east-west axis (Minecraft's "x"
    model rotation): up -> north -> down -> south at +90. East/west faces and every uv are unchanged."""
    return _rotate(model, degrees, axis=0)


def _rotate(model, degrees, axis):
    k = (degrees // 90) % 4
    cycle = _Y_FACES if axis == 1 else _X_FACES
    face_map = {cycle[i]: cycle[(i + k) % 4] for i in range(4)}
    elements = [_rotate_element(e, k, axis, face_map) for e in model["elements"]]
    return {"textures": model["textures"], "elements": elements}


def _rotate_element(element, k, axis, face_map):
    low = _rotate_point(element["from"], k, axis)
    high = _rotate_point(element["to"], k, axis)
    result = {
        **element,
        "from": [min(low[i], high[i]) for i in range(3)],
        "to": [max(low[i], high[i]) for i in range(3)],
        "faces": {face_map.get(d, d): face for d, face in element["faces"].items()},
    }
    if "rotation" in element:
        result["rotation"] = _rotate_inner(element["rotation"], k, axis)
    return result


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
    multipart parts, each rotated by its own baked x then y around PIVOT, then turned by -family.base_y so
    the family's own facing=north reference lines up with Hytale's front (-Z). A property missing from
    props takes the block's own default value (see parts()).

    Raises ValueError if a selected part needs uvlock (Minecraft's texture-locked rotation): DO's own
    blockstates use it for a few families (e.g. trapdoors, panels) whose rotation this module does not
    yet reproduce.
    """
    state = source.blockstate(root, block_id)
    merged = {"textures": {}, "elements": []}
    for index, apply in enumerate(parts(state, props)):
        if apply.get("uvlock"):
            raise ValueError(f"uvlock not supported: {block_id}")
        name = apply["model"].removeprefix(source.DO_PARENT)
        model = rotate_y(rotate_x(source.load(root, name), apply.get("x", 0)), apply.get("y", 0))
        part = _prefixed(model, f"e{index}_")
        merged["textures"].update(part["textures"])
        merged["elements"].extend(part["elements"])
    return rotate_y(merged, -family.base_y)


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
