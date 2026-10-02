"""The knight's sword and shield, matching the plate armor (spec 2026-10-02 plate armor, § 5; MC's knights carry
vanilla Minecraft's): each keeps the node chain of Hytale's iron one (the hand's attachment, marked isPiece, and the
engine's Origin_* markers, settings and all, and its Handle, a box in the iron's, here shapeless at its place) and lays
its boxes under Origin_Item, drawn in the model's frame (y up from the handle's end, as the iron sword's handle from 0
to 22) and shifted by Origin_Item's place."""

from models import empty_shape, node
from pieces import ALL, about, plate

# Origin_Item's place in the model's frame, rotation none (the iron sword's and shield's chains).
SWORD_ITEM = (0, 45, 0)
SHIELD_ITEM = (4, 34, 0)


def marker(name, at, piece, rotation=(0, 0, 0, 1), offset=(0, 0, 0), children=()):
    """A shapeless node at the place of one of the iron weapons' chain nodes, with its isPiece, offset and rotation."""
    n = node(name, at, empty_shape(), children)
    n["shape"]["settings"]["isPiece"] = piece
    n["shape"]["offset"] = dict(zip("xyz", offset))
    n["orientation"] = dict(zip("xyzw", rotation))
    return n


def placed_at(origin, boxes):
    """The boxes, drawn in the model's frame, moved into the frame of a node at origin."""
    for box in boxes:
        box["position"] = {k: box["position"][k] - o for k, o in zip("xyz", origin)}
    return boxes


def sword():
    """A knight's longsword: a gold pommel set with a red gem, a red leather grip with a gold ring, a gold crossguard
    with flared quillons and a red gem through its middle, a short ricasso, then a blade in steps to a diamond point."""
    boxes = [
        plate("Pommel", (0, 2, 0), (7, 6, 7)),
        plate("Pommel_Gem", (0, 2, 0), (8, 3, 3), about((1, 0, 0), 45)),
        plate("Grip", (0, 13.5, 0), (5, 17, 5), sides=("front", "back", "left", "right")),
        plate("Grip_Ring", (0, 13.5, 0), (6, 2, 6)),
        plate("Guard", (0, 24.5, 0), (6, 5, 24)),
        plate("Quillon_R", (0, 25.5, -13.5), (5, 7, 3)),
        plate("Quillon_L", (0, 25.5, 13.5), (5, 7, 3)),
        plate("Guard_Gem", (0, 24.5, 0), (7, 4, 4), about((1, 0, 0), 45)),
        # Thicker than the blade, standing on the guard.
        plate("Ricasso", (0, 29.5, 0), (4, 5, 8), sides=("front", "back", "left", "right", "top")),
        plate("Blade_Base", (0, 45, 0), (3, 26, 12)),
        plate("Blade_Mid", (0, 70.5, 0), (2, 25, 10), sides=("front", "back", "left", "right", "top")),
        plate("Blade_Neck", (0, 85.5, 0), (2, 5, 7), sides=("front", "back", "left", "right", "top")),
        plate("Blade_Tip", (0, 88, 0), (1, 5, 5), about((1, 0, 0), 45), sides=("back", "left", "right", "top")),
    ]
    # Handle: where Hytale's sword effects (swing trails, the signature's particles) attach, along its x; placed as
    # the iron sword's, whose handle box it is.
    handle = marker("Handle", (0.000068, -32, 0), False, (-0.5, -0.5, 0.5, 0.5), (-2.000477, 0, 0))
    item = marker("Origin_Item", (0, -10, 0), False, children=[handle, *placed_at(SWORD_ITEM, boxes)])
    blade = marker("Origin_Blade", (0, -22, 0), False, children=[item])
    projectile = marker("Origin_Projectile", (-0.000068, 64, 0), True, children=[blade])
    return [marker("R-Attachment", (0, 13, 0), True, offset=(0.000068, 0, 0), children=[projectile])]


# The heater shield's bands from the top down: (height, width) in the model's frame, its face 4 thick at x 5 to 9.
BANDS = ((18, 40), (16, 38), (12, 32), (8, 24), (6, 14))


def shield():
    """A knight's heater shield on the left arm: its face in bands narrowing to a diamond point (red, a steel rim
    painted on), a gold cross and a gold boss on the front, and on the back a grip on two brackets."""
    boxes, top = [], 64
    for i, (height, width) in enumerate(BANDS, 1):
        # A band's top lies under the wider band above, save the first's.
        sides = ALL if i == 1 else ("front", "back", "left", "right", "bottom")
        boxes.append(plate(f"Face_{i}", (7, top - height / 2, 0), (4, height, width), sides=sides))
        top -= height
    boxes += [
        # Thinner than the bands, so that no face lies in theirs; its upper faces are in the last band.
        plate("Face_Point", (7, top, 0), (3, 10, 10), about((1, 0, 0), 45),
              sides=("front", "left", "right", "bottom")),
        plate("Cross_Upright", (9.5, 34, 0), (1, 50, 4), sides=("right", "top", "bottom", "front", "back")),
        plate("Cross_Bar", (10, 48, 0), (2, 4, 30), sides=("right", "top", "bottom", "front", "back")),
        plate("Boss", (10.5, 48, 0), (3, 7, 7), about((1, 0, 0), 45), sides=("right", "top", "bottom", "front",
                                                                              "back")),
        plate("Grip", (1.5, 34, 0), (3, 22, 4)),
        plate("Bracket_Top", (3, 46.5, 0), (4, 3, 6), sides=("front", "back", "top", "bottom", "left")),
        plate("Bracket_Bottom", (3, 21.5, 0), (4, 3, 6), sides=("front", "back", "top", "bottom", "left")),
    ]
    # Handle: where Hytale's shield bash effect attaches; placed as the iron shield's, whose handle box it is.
    handle = marker("Handle", (-4, 0, 0), False, (-0.430459, -0.560986, 0.430459, 0.560986))
    item = marker("Origin_Item", (1, -29.51666, 0.875644), False, (-0.258819, 0, 0, 0.965926),
                  children=[handle, *placed_at(SHIELD_ITEM, boxes)])
    projectile = marker("Origin_Projectile", (3, 26, 14), True, (0.258819, 0, 0, 0.965926), children=[item])
    return [marker("L-Attachment", (0, 34, 0), True, children=[projectile])]


# Weapon -> its nodes.
ARMS = {"Sword": sword, "Shield": shield}
