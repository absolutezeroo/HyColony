"""Readies a block model's faces before painting: gives every box its bottom back, drops the faces other boxes hide
(cull.py) and lays the faces out again (models.unwrap, a glinting model's crystals in a band of their own for
glint.frames). Nodes the model animates keep their faces.

It gives back no face but bottoms: a dropped side or top is gone from the model Blockbench reopens too, so an edit
that uncovers one enables it again on its box in Blockbench before saving."""

import glint
from cull import cull
from models import MAX_SIDE, WIDTHS, face_rects, unwrap, walk

# The blockyanim tracks that move a node; a UV offset (a glint) does not.
MOTION = ("position", "orientation", "shapeStretch", "shapeVisible")


def trim(nodes, module):
    """Closes the model's bottoms, culls its hidden faces and lays it out; returns the texture size (glint frames
    included)."""
    close_bottoms(nodes)
    cull(nodes, moving(module, nodes))
    return laid_out(nodes, getattr(module, "GLINT", None))


def close_bottoms(nodes):
    """Gives every box of the model its bottom face back (the user's rule: seen from below, a hut is never hollow);
    cull then drops those that other boxes hide."""
    for n in walk(nodes):
        layout = n["shape"].get("textureLayout")
        if n["shape"]["type"] == "box" and "bottom" not in layout:
            layout["bottom"] = {"offset": {"x": 0, "y": 0}, "mirror": {"x": False, "y": False}, "angle": 0}


def moving(module, nodes):
    """The names of the nodes the model's animation moves, computed from its module as catalog.paint writes it (a
    glint, breathing unless BREATHE is False, or animation(nodes)), never read from a blockyanim that may be stale."""
    if hasattr(module, "GLINT"):
        tracks = glint.animation(nodes, module.GLINT, 0, getattr(module, "BREATHE", True))["nodeAnimations"]
    elif hasattr(module, "animation"):
        tracks = module.animation(nodes)["nodeAnimations"]
    else:
        return frozenset()
    return frozenset(name for name, track in tracks.items() if any(track.get(key) for key in MOTION))


def laid_out(nodes, prefix):
    """Lays the model's faces out (models.unwrap); a glinting model's crystals (nodes named prefix*) in a band of their
    own, at the width whose texture is smallest once glint.frames has added its frames, within MAX_SIDE if it can.
    Returns the texture size, frames included."""
    if prefix is None:
        return unwrap(nodes)

    def apart(name):
        return name.startswith(prefix)

    def grown(width):
        return glint.grown_size(nodes, prefix, unwrap(nodes, (width,), apart))

    widest = max(u1 - u0 for _, u0, _, u1, _ in face_rects(nodes))
    sizes = [(grown(width), width) for width in WIDTHS if width >= widest]
    _, width = min(sizes, key=lambda s: (max(*s[0], MAX_SIDE), s[0][0] * s[0][1]))
    return grown(width)


def faces(nodes):
    """How many faces the model's shapes show."""
    return sum(len(n["shape"].get("textureLayout", {})) for n in walk(nodes))
