"""Decals (spec 2026-10-03 blockpaint decals § 3.2): a small drawing laid on part of a face, or past its border (a
saw's teeth), as Hytale lays its own: a quad DECAL_GAP in front of the face, turned to face where the face faces, on
its own island with its own transparency, moving with its part: its child, or, when the part is a static box (a cube
of a Blockbench group, which keeps no children), beside it in the same group, a static box itself."""

import math
from collections import namedtuple

from models import face_local, face_normal, node, quad_shape
from vectors import add, multiply, rotate

# A decal: the part (node name before Blockbench's '--C<n>') and the side of the face it lies on, its rect (s, t, w,
# h) in texels of that face (s, t from the face's top left as Blockbench lays it out; it may pass the face's border)
# and its brush (w, h, side) -> RGBA image, clear where the face shows through.
Decal = namedtuple("Decal", "part side rect brush")
# How far in front of its face a decal lies, in model units: the most common gap of Hytale's own decals other than 0
# (which z-fights with the face).
DECAL_GAP = 0.1
# What every decal's node name holds (node_name).
MARK = "_Decal_"
# The node orientation (quaternion) turning a +Z quad to face each side of a box, its u and v along the face's.
_HALF = math.sqrt(0.5)
TURNS = {"front": (0, 0, 0, 1), "back": (0, 1, 0, 0), "right": (0, _HALF, 0, _HALF), "left": (0, -_HALF, 0, _HALF),
         "top": (-_HALF, 0, 0, _HALF), "bottom": (_HALF, 0, 0, _HALF)}


def node_name(part, name):
    """The node of the decal name on part."""
    return f"{part}{MARK}{name}"


def holders(nodes):
    """(siblings, node) of every node: the list it lies in, which a decal beside it joins."""
    for n in nodes:
        yield nodes, n
        yield from holders(n.get("children", []))


def place(nodes, decals):
    """Lays the quads of decals ({name: Decal}) on their parts, in place: every former decal (a node named with MARK)
    taken off first, so placing again changes nothing. Fails on a part the model has not, or has twice, on a part
    stretched, on a side the part does not show and on a rect not in whole texels or empty (its decal's texels would
    not match the face's)."""
    for siblings, n in list(holders(nodes)):
        # A node without children keeps no key: a model without decals is written byte for byte as before.
        if any(MARK in c["name"] for c in n.get("children", [])):
            n["children"][:] = [c for c in n["children"] if MARK not in c["name"]]
    nodes[:] = [n for n in nodes if MARK not in n["name"]]
    for name, decal in decals.items():
        s, t, w, h = decal.rect
        if any(c != int(c) for c in decal.rect) or w < 1 or h < 1:
            raise SystemExit(f"decal {name}: its rect {decal.rect} is not whole texels, at least 1 x 1")
        found = [(siblings, n) for siblings, n in holders(nodes)
                 if n["name"].split("--")[0] == decal.part and n["shape"]["type"] == "box"]
        if len(found) != 1:
            raise SystemExit(f"decal {name}: the model has {len(found)} boxes named {decal.part}, not one")
        siblings, part = found[0]
        if any(part["shape"]["stretch"][a] != 1 for a in "xyz"):
            raise SystemExit(f"decal {name}: {decal.part} is stretched; a decal lies on an unstretched box")
        if decal.side not in part["shape"]["textureLayout"]:
            raise SystemExit(f"decal {name}: {decal.part} shows no {decal.side} face to lie on")
        if part["shape"]["settings"].get("isStaticBox"):
            siblings.append(beside(decal_node(name, decal, part["shape"]), part))
        else:
            part.setdefault("children", []).append(decal_node(name, decal, part["shape"]))


def decal_node(name, decal, box):
    """The decal's quad node as a child of its part: centred over its rect, DECAL_GAP out along the face's normal,
    turned to face the same way. A child's position counts from its parent's shape centre (models.placed)."""
    s, t, w, h = decal.rect
    centre = face_local(box, decal.side, s + w / 2, t + h / 2)
    at = tuple(c + DECAL_GAP * k for c, k in zip(centre, face_normal(box, decal.side)))
    child = node(node_name(decal.part, name), at, quad_shape((w, h), shading=box.get("shadingMode", "standard")))
    x, y, z, q = TURNS[decal.side]
    child["orientation"] = {"x": x, "y": y, "z": z, "w": q}
    return child


def beside(child, part):
    """child (decal_node's node, laid as part's child) moved beside part, in the same parent: its position and
    orientation carried through part's, and marked a static box as the part is."""
    o, p, offset = part["orientation"], part["position"], part["shape"]["offset"]
    turn = (o["x"], o["y"], o["z"], o["w"])
    c = child["orientation"]
    local = add((offset["x"], offset["y"], offset["z"]), tuple(child["position"][a] for a in "xyz"))
    child["position"] = dict(zip("xyz", add((p["x"], p["y"], p["z"]), rotate(turn, local))))
    child["orientation"] = dict(zip("xyzw", multiply(turn, (c["x"], c["y"], c["z"], c["w"]))))
    child["shape"]["settings"]["isStaticBox"] = True
    return child
