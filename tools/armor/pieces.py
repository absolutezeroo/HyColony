"""The plate armor's four pieces (spec 2026-10-02 plate armor, MC ItemPlateArmor): each built on the bone nodes of
Hytale's Cobalt armor (same names, positions, orientations and offsets, so that it sits where a Cobalt piece does), its
plates placed where the Cobalt places its own. The left side mirrors the right (x negated, rotations mirrored), with no
negative stretch."""

import math

from models import add, box_shape, empty_shape, multiply, node, rotate

ALL = ("front", "back", "left", "right", "top", "bottom")
SIDES = ((-1, "R"), (1, "L"))
# The plume's segments from its socket: (length, width, angle from forward toward up, in degrees).
PLUME = ((8, 7, 92), (8, 10, 115), (8, 11, 140), (8, 10, 165), (7, 8, 192), (7, 6, 218), (6, 3, 242))
# The plume's feathers fanned from the socket: (name, sideways tilt in degrees, length scale, x shift of its planes).
FEATHERS = (("Plume_C", 0, 1.0, 0), ("Plume_R", 20, 0.8, -0.25), ("Plume_L", -20, 0.8, 0.25))
# The player's animations swing nodes of these names in whatever worn model has them (hair, and the cloth of
# Hytale's armours; 2,340 animations): a feather's 4th and 5th segments (SWING_FROM) and the tabard's two halves
# take them.
SWINGING = {"Plume_C": ("Hair-B", "Hair-B2"), "Plume_R": ("Hair-R", "Hair-R2"), "Plume_L": ("Hair-L", "Hair-L2")}
# The first plume segment taking a hair name: the first that points back past the helm, as a lock of hair hangs
# (Hytale's hair hangs along its local +y, Characters/Haircuts/Long.blockymodel, a +x turn lifting it back): there
# walking and running lift the plume back, where on the rising segments the same turn would tip it forward, against
# the wind.
SWING_FROM = 4
# Where the feathers leave the socket, in the head bone's frame.
SOCKET = (0, 24, -6)
# The Cobalt's bone rotations, right side (the left mirrors them).
ARM = (0.006381, -0.060714, -0.104333, 0.992667)
HAND = (0.00266, -0.06099, -0.04354, 0.99719)
THIGH = (0, 0.017452, 0, -0.999848)
FOOT = (0, -0.019197, 0, 0.999816)


def about(axis, degrees):
    """The quaternion of a rotation of degrees about axis."""
    half = math.radians(degrees) / 2
    return tuple(c * math.sin(half) for c in axis) + (math.cos(half),)


def mirrored(rotation):
    """The rotation mirrored across the x = 0 plane."""
    x, y, z, w = rotation
    return (x, -y, -z, w)


def plate(name, at, size, rotation=None, children=(), sides=ALL):
    """A flat-shaded box (Hytale's armours are flat), centred at at, turned by rotation."""
    n = node(name, at, box_shape(size, sides, shading="flat"), children)
    if rotation:
        n["orientation"] = dict(zip("xyzw", rotation))
    return n


def bone(name, at, offset=(0, 0, 0), rotation=(0, 0, 0, 1), children=()):
    """A bone node as the Cobalt's: no shape, marked isPiece (the client attaches it to the player's bone of the same
    name; without it the node stays at the entity's origin, at its feet, seen in game 2026-10-02), its children
    counted from at plus offset."""
    n = node(name, at, empty_shape(), children)
    n["shape"]["offset"] = dict(zip("xyz", offset))
    n["shape"]["settings"]["isPiece"] = True
    n["orientation"] = dict(zip("xyzw", rotation))
    return n


def head():
    """A great helm with a crest, a prow visor (two plates angled 12 degrees back from a nose ridge, MC's T slit
    painted on), a brass rim and visor pivots, and a plume."""
    return [bone("Head", (0, -12, -2), (0, 15, 3), children=[
        plate("Helm", (0, 1, 0), (35, 30, 30)),
        plate("Helm_Top", (0, 17, 0), (31, 2, 26), sides=("front", "back", "left", "right", "top")),
        plate("Crest", (0, 19.5, 0), (2, 3, 26), sides=("front", "back", "left", "right", "top")),
        plate("Visor_R", (-7.34, 0, 15.94), (15, 22, 2), about((0, 1, 0), -12)),
        plate("Visor_L", (7.34, 0, 15.94), (15, 22, 2), about((0, 1, 0), 12)),
        plate("Ridge", (0, 0, 17), (3, 24, 2)),
        plate("Rim", (0, -13, 0), (36, 3, 31)),
        plate("Pivot_R", (-18, 1, 6), (2, 5, 5)),
        plate("Pivot_L", (18, 1, 6), (2, 5, 5)),
        plate("Socket", (0, 22.5, -6), (4, 3, 4)),
        *plume(),
    ])]


def plume():
    """Three feathers fanned from the socket (a red one in the middle, two shorter white ones tilted out), each arching
    up over the helm and down behind it, tapering to its tip: a chain of segments, each hinged at its base on the end
    of the one before (sunk one unit into it), so that the player's animations swing the 4th and 5th (SWINGING,
    SWING_FROM) and the segments after them follow; thicknesses alternate so that no two segments share a face
    plane."""
    out = []
    for name, tilt, scale, shift in FEATHERS:
        tip, previous, before = None, None, 0
        for i, (length, width, angle) in enumerate(PLUME, 1):
            length = max(3, round(length * scale))
            names = SWINGING[name]
            label = names[i - SWING_FROM] if 0 <= i - SWING_FROM < len(names) else f"{name}{i}"
            if previous is None:
                at, turn = add(SOCKET, (shift, 0, 0)), multiply(about((0, 0, 1), tilt), about((1, 0, 0), -angle))
            else:
                at, turn = (0, 0, previous / 2 - 1), about((1, 0, 0), -(angle - before))
            segment = plate(label, at, (2 if i % 2 else 3, width, length), turn)
            # The box hangs from its hinge along its own z; its children count from its middle.
            segment["shape"]["offset"] = {"x": 0, "y": 0, "z": length / 2}
            (out if tip is None else tip["children"]).append(segment)
            tip, previous, before = segment, length, angle
    return out


def tabard(side):
    """The red tabard hung from the belt over the thighs (side +1 in front, -1 behind), in two halves hinged at the
    belt and at mid-height and named as Hytale's armour cloth, so that the player's animations swing them."""
    half = "Front" if side > 0 else "Back"
    lower = plate(f"{half}_Cloth_2", (0, -6, 0), (19, 12, 1))
    lower["shape"]["offset"] = {"x": 0, "y": -6, "z": 0}
    upper = plate(f"{half}_Cloth_1", (0, 6, side * 10.5), (19, 12, 1), about((1, 0, 0), -side * 8), [lower])
    upper["shape"]["offset"] = {"x": 0, "y": -6, "z": 0}
    return upper


def pauldron(s, tag):
    """Three lames on the shoulder, each lower one stepping out, and a guard standing up at the neck's side."""
    lames = [plate(f"Pauldron_{tag}_2", (s * 1, -5, 0), (15, 6, 21)),
             plate(f"Pauldron_{tag}_3", (s * 2, -9.5, 0), (14, 5, 20)),
             plate(f"Pauldron_{tag}_Guard", (-s * 6, 6.5, 0), (2, 6, 18))]
    return plate(f"Pauldron_{tag}_1", (s * 4.2, 5, 0), (16, 9, 22), about((0, 0, 1), -s * 20.3), lames)


def chest():
    """The cuirass with its emblem and gorget, the faulds, belt and red tabard, and the pauldrons over mail."""
    arms = [bone(f"{tag}-Arm", (s * 13, 43, -1), (0, -7, 0), ARM if s < 0 else mirrored(ARM), [
        pauldron(s, tag),
        plate(f"Rerebrace_{tag}", (s * 1, -1, 0), (10, 12, 14), sides=("front", "back", "left", "right", "bottom")),
    ]) for s, tag in SIDES]
    return [bone("Pelvis", (0, -12, 0), children=[
        tabard(1),
        tabard(-1),
        bone("Belly", (0, 8, 0), (0, 4, 0), children=[
            plate("Fauld_1", (0, 4, 0), (29, 5, 23)),
            plate("Fauld_2", (0, 0, 0), (30, 5, 24)),
            plate("Fauld_3", (0, -4, 0), (31, 5, 25)),
            plate("Belt", (0, -8, 0), (30, 3, 22)),
            plate("Buckle", (0, -8, 11.5), (5, 4, 1)),
            bone("Chest", (0, 5, 0), (0, 11, 0), children=[
                plate("Cuirass", (0, 2, 0), (31, 16, 27)),
                # Down to the faulds, as the Cobalt's lower front plate.
                plate("Cuirass_Low", (0, -7, 2), (28, 14, 22), sides=("front", "back", "left", "right", "bottom")),
                plate("Emblem", (0, 3, 14), (7, 7, 1), about((0, 0, 1), 45),
                      sides=("front", "left", "right", "top", "bottom")),
                plate("Gorget", (0, 11, -1), (22, 3, 20), sides=("front", "back", "left", "right", "top")),
                plate("Gorget_2", (0, 9.5, -1), (25, 2, 23), sides=("front", "back", "left", "right", "top")),
            ]),
        ]),
        *arms,
    ])]


def hands():
    """On each forearm a vambrace with its strap, a cuff and a diamond couter; on each hand a gauntlet, its knuckle
    plate and leather fingers."""
    return [bone(f"{tag}-Forearm", (s * 12, 12.51058, -1), children=[
        # From the cuff down into the gauntlet, so that no forearm shows between them.
        plate(f"Vambrace_{tag}", (0, -7.5, 0), (10, 14, 13)),
        plate(f"Strap_V{tag}", (0, -6, 0), (11, 2, 14)),
        plate(f"Cuff_{tag}", (0, 1, 0), (13, 5, 15)),
        plate(f"Couter_{tag}", (0, 1, -8), (7, 7, 3), about((0, 0, 1), -s * 45)),
        bone(f"{tag}-Hand", (-s * 0.27673, -7.52961, 0), (0, -5, 0), HAND if s < 0 else mirrored(HAND), [
            plate(f"Gauntlet_{tag}", (0, 0, 0), (12, 13, 16)),
            plate(f"Knuckles_{tag}", (0, -4, 0), (13, 3, 17)),
            plate(f"Fingers_{tag}", (0, -7, 0.75), (11, 4, 15), sides=("front", "back", "left", "right", "bottom")),
        ]),
    ]) for s, tag in SIDES]


def legs():
    """Mail hips; on each thigh mail chausses under a smooth cuisse plate with its strap and a diamond poleyn; a smooth
    greave with a shin ridge; a sabaton with a toe cap."""
    thighs = [bone(f"{tag}-Thigh", (s * 8, -1, 1), (0, -9, 0), THIGH if s < 0 else mirrored(THIGH), [
        plate(f"Chausse_{tag}", (0, 0, 0), (12, 24, 16)),
        plate(f"Cuisse_{tag}", (0, 1, 6.5), (13, 20, 4), sides=("front", "left", "right", "top", "bottom")),
        plate(f"Strap_C{tag}", (0, 6, 0.5), (14, 2, 18)),
        plate(f"Poleyn_{tag}", (0, -11, 8.5), (8, 8, 3), about((0, 0, 1), -s * 45)),
        bone(f"{tag}-Calf", (0, -12, 3), (0, -12, -3), children=[
            plate(f"Greave_{tag}", (0, 0, 0), (13, 21, 16)),
            plate(f"Shin_{tag}", (0, -1, 8.5), (3, 17, 1), sides=("front", "left", "right", "top", "bottom")),
        ]),
    ]) for s, tag in SIDES]
    feet = [bone(f"{tag}-Foot", (s * 7.846449, 6.999998, -2.997052), (0, -3, 6), FOOT if s < 0 else mirrored(FOOT), [
        plate(f"Sabaton_{tag}", (0, 0, 0), (15, 8, 21)),
        plate(f"Toe_{tag}", (0, -1, 11), (13, 5, 3), sides=("front", "left", "right", "top", "bottom")),
    ]) for s, tag in SIDES]
    return [bone("Pelvis", (0, 51, 0), children=[plate("Hips", (0, 0, 0), (27, 12, 19)), *thighs]), *feet]


# Piece -> its nodes, in Hytale's armour slot order.
PIECES = {"Head": head, "Chest": chest, "Hands": hands, "Legs": legs}
