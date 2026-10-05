"""Each fishing rod's own design (tools/angler/rods.py builds them): what sits on the common skeleton, its four hinged
sections (Rod_Blank > Rod_Mid > Rod_Upper > Rod_Tip, rods.SECTIONS) and the tip's ring, which every animation and the
line's tip tracks rely on; the crank's hinge sits on the axle of its own design's reel (the Reel animations only turn
it). The rest, butt, grip, reel, crank, collars and ornaments, is the tier's own, after the language Hytale gives that
tier's bows (Common/Items/Weapons/Bow/<Tier>).

Units: the rod's frame, 64 to a block, y up its length, the guides and the reel on its -z side, the crank on +x. The
sections span y 16-49 (4 wide), 49-83 (3), 83-100 (2) and 100-117 (2); the guides sit at y 26, 44, 62, 80, 98 and 112.
A part is (name, centre, size, material[, (x, y, z) turn in degrees]); a material is a key of rods.tiles_of(tier).
Every name starts with Rod_ and is unique within its rod.
"""

from rod_designs_rare import ADAMANTITE, COBALT, MITHRIL, ONYXIUM
from rod_parts import AXLE_Y, AXLE_Z, ring_plate


# A design's keys: fixed (the parts that stay put), crank_pivot and crank (the parts the Reel animations turn about x),
# deco (each section's extra parts, which bend with it), ferrules (the collar over a section's top joint; absent: none)
# and tip_top.

# A branch cut and trimmed by hand: thick and barked at the bottom, knots and a twig with a leaf left on it, the butt
# cut from a fork, the joints and the grip bound with plant fibre, bone loops for guides. No reel: the line is wound in
# a bundle on a peg from the seat, and a cross of two sticks on its +x side turns about the bundle's axis (x), as the
# other rods' cranks do.
CRUDE = {
    "fixed": [
        ("Rod_Butt_Prong_L", (1.5, -19, 0.5), (2, 6, 2), "blank"),
        ("Rod_Butt_Prong_R", (-1.5, -19, -0.5), (2, 5, 2), "blank"),
        ("Rod_Butt", (0, -15, 0), (5, 3, 5), "blank"),
        ("Rod_Grip", (0, -4, 0), (5, 19, 5), "grip"),
        ("Rod_Grip_Knot", (0, 6, 0), (6, 2, 6), "fitting"),
        ("Rod_Seat", (0, 11.5, 0), (5, 9, 5), "blank"),
        ("Rod_Seat_Lash", (0, 15, 0), (6, 1, 6), "fitting"),
        ("Rod_Reel_Peg", (0, AXLE_Y, -4), (2, 2, 4), "knob"),
        ("Rod_Reel", (0, AXLE_Y, AXLE_Z), (3, 3, 3), "line"),
    ],
    # the winder: two crossed sticks beside the line's bundle, turning about its axis
    "crank_pivot": (2, AXLE_Y, AXLE_Z),
    "crank": [
        ("Rod_Crank_Arm", (2, AXLE_Y, AXLE_Z), (1, 9, 1), "reel"),
        ("Rod_Crank_Knob", (2, AXLE_Y, AXLE_Z), (1, 1, 9), "reel"),
        ("Rod_Crank_Line", (1.5, AXLE_Y, AXLE_Z), (1, 4, 4), "line"),
    ],
    "deco": {
        "Rod_Blank": [
            ("Rod_Blank_Bark", (0.5, 22, 0.5), (5, 11, 5), "blank"),
            ("Rod_Blank_Knot", (2.5, 31, 0.5), (1, 2, 2), "knob"),
            ("Rod_Blank_Twig", (-3, 40, 0), (2, 1, 1), "blank"),
            ("Rod_Blank_Twig_End", (-4.5, 41, 0), (1, 2, 1), "blank"),
            ("Rod_Blank_Leaf", (-5, 42.5, 0.5), (1, 2, 2), "leaf"),
        ],
        "Rod_Mid": [("Rod_Mid_Knot", (1.8, 70, -0.5), (1, 2, 1), "knob"),
                    ("Rod_Mid_Knot_B", (-1.8, 57, 0.5), (1, 1, 1), "knob")],
        "Rod_Upper": [("Rod_Upper_Knot", (-1.3, 92, 0), (1, 1, 1), "knob")],
    },
    "ferrules": {"Rod_Blank": ((5, 2, 5), "fitting"), "Rod_Mid": ((4, 2, 4), "fitting"),
                 "Rod_Upper": ((3, 1, 3), "fitting")},
    "tip_top": ((3, 2, 3), "fitting"),
}

# Hytale's copper bow: wood held by square copper collars standing proud, copper end caps. Here a stepped copper
# pommel, a banded leather grip, a wooden seat between copper rings, and an open copper reel with round flanges
# wider than its spool; square collars at every joint, thin bands between them.
COPPER = {
    "fixed": [
        ("Rod_Butt_Cap", (0, -19.5, 0), (4, 1, 4), "fitting"),
        ("Rod_Butt", (0, -17, 0), (6, 4, 6), "fitting"),
        ("Rod_Grip_Band_Low", (0, -14.5, 0), (6, 1, 6), "fitting"),
        ("Rod_Grip", (0, -4, 0), (5, 20, 5), "grip"),
        ("Rod_Grip_Band_High", (0, 6.5, 0), (6, 1, 6), "fitting"),
        ("Rod_Seat", (0, 11.5, 0), (4, 9, 4), "blank"),
        ("Rod_Seat_Ring_Low", (0, 7.5, 0), (5, 1, 5), "fitting"),
        ("Rod_Seat_Ring_High", (0, 15.5, 0), (5, 1, 5), "fitting"),
        ("Rod_Reel_Foot", (0, AXLE_Y, -3), (2, 4, 2), "fitting"),
        ("Rod_Reel", (0, AXLE_Y, AXLE_Z), (5, 6, 6), "line"),
        *ring_plate("Rod_Reel_Plate_L", 3, 10, "reel"),
        *ring_plate("Rod_Reel_Plate_R", -3, 10, "reel"),
        ("Rod_Reel_Hub_R", (-4, AXLE_Y, AXLE_Z), (1, 3, 3), "reel"),
    ],
    "crank_pivot": (3.5, AXLE_Y, AXLE_Z),
    "crank": [
        ("Rod_Crank_Hub", (4, AXLE_Y, AXLE_Z), (1, 3, 3), "reel"),
        ("Rod_Crank_Arm", (4.5, 15, AXLE_Z), (1, 5, 1), "reel"),
        ("Rod_Crank_Knob", (6, 17, AXLE_Z), (2, 2, 2), "knob"),
    ],
    "deco": {
        "Rod_Blank": [("Rod_Blank_Band", (0, 36, 0), (5, 1, 5), "fitting")],
        "Rod_Mid": [("Rod_Mid_Band", (0, 70, 0), (4, 1, 4), "fitting")],
        "Rod_Tip": [("Rod_Tip_Band", (0, 109, 0), (3, 1, 3), "fitting")],
    },
    "ferrules": {"Rod_Blank": ((7, 3, 7), "fitting"), "Rod_Mid": ((6, 3, 6), "fitting"),
                 "Rod_Upper": ((4, 2, 4), "fitting")},
    "tip_top": ((3, 2, 3), "fitting"),
}

# Hytale's iron bow: wood shod with riveted iron plates. Here a heavy iron butt weight, a leather grip under an iron
# cap, an iron reel seat sleeve, a closed iron drum reel with its outer flange riveted and a long crank; iron plates
# clasp the two lower joints on both sides, riveted on the lowest.
IRON = {
    "fixed": [
        ("Rod_Butt_Weight", (0, -18, 0), (7, 4, 7), "fitting"),
        ("Rod_Butt", (0, -15, 0), (6, 2, 6), "fitting"),
        ("Rod_Grip", (0, -4, 0), (5, 20, 5), "grip"),
        ("Rod_Grip_Cap", (0, 6.5, 0), (6, 1, 6), "fitting"),
        ("Rod_Seat", (0, 12, 0), (5, 10, 5), "fitting"),
        ("Rod_Reel_Foot", (0, AXLE_Y, -3.5), (2, 3, 2), "fitting"),
        ("Rod_Reel", (0, AXLE_Y, AXLE_Z), (6, 7, 7), "reel"),
        ("Rod_Reel_Plate_L", (3.5, AXLE_Y, AXLE_Z), (1, 8, 8), "fitting"),
        ("Rod_Reel_Plate_R", (-3.5, AXLE_Y, AXLE_Z), (1, 8, 8), "fitting"),
        ("Rod_Reel_Rivet_A", (-4, 15, AXLE_Z - 3), (1, 1, 1), "rivet"),
        ("Rod_Reel_Rivet_B", (-4, 9, AXLE_Z + 3), (1, 1, 1), "rivet"),
        ("Rod_Reel_Rivet_C", (-4, 15, AXLE_Z + 3), (1, 1, 1), "rivet"),
        ("Rod_Reel_Rivet_D", (-4, 9, AXLE_Z - 3), (1, 1, 1), "rivet"),
    ],
    "crank_pivot": (4, AXLE_Y, AXLE_Z),
    "crank": [
        ("Rod_Crank_Hub", (4.5, AXLE_Y, AXLE_Z), (1, 3, 3), "reel"),
        ("Rod_Crank_Arm", (5, 15, AXLE_Z), (1, 6, 1), "reel"),
        ("Rod_Crank_Knob", (6.5, 18, AXLE_Z), (2, 3, 2), "knob"),
    ],
    "deco": {
        "Rod_Blank": [
            ("Rod_Blank_Plate_L", (2.5, 45.5, 0), (1, 7, 3), "fitting"),
            ("Rod_Blank_Plate_R", (-2.5, 45.5, 0), (1, 7, 3), "fitting"),
            ("Rod_Blank_Rivet_L", (3, 44, 0), (1, 1, 1), "rivet"),
            ("Rod_Blank_Rivet_R", (-3, 44, 0), (1, 1, 1), "rivet"),
            ("Rod_Blank_Band", (0, 20, 0), (5, 2, 5), "fitting"),
        ],
        "Rod_Mid": [
            ("Rod_Mid_Plate_L", (2, 79.5, 0), (1, 7, 2), "fitting"),
            ("Rod_Mid_Plate_R", (-2, 79.5, 0), (1, 7, 2), "fitting"),
        ],
        "Rod_Upper": [("Rod_Upper_Band", (0, 90, 0), (3, 1, 3), "fitting")],
    },
    "ferrules": {"Rod_Blank": ((6, 2, 6), "fitting"), "Rod_Mid": ((5, 2, 5), "fitting"),
                 "Rod_Upper": ((3, 2, 3), "fitting")},
    "tip_top": ((3, 3, 3), "fitting"),
}

# Hytale's thorium bow: vivid green limbs like crystal, a purple-wrapped grip, white bone spurs. Here green crystal
# sections, bone collars with spurs jutting out at the joints, a bone spike for a butt, a purple grip between thorium
# rings, and a green gem set in the reel's outer flange.
THORIUM = {
    "fixed": [
        ("Rod_Butt_Spike", (0, -20, 0), (2, 3, 2), "bone"),
        ("Rod_Butt", (0, -16.5, 0), (5, 4, 5), "bone"),
        ("Rod_Grip_Ring_Low", (0, -14.5, 0), (6, 1, 6), "fitting"),
        ("Rod_Grip", (0, -4, 0), (5, 20, 5), "grip"),
        ("Rod_Grip_Ring_High", (0, 6.5, 0), (6, 1, 6), "fitting"),
        ("Rod_Seat", (0, 11.5, 0), (4, 9, 4), "fitting"),
        ("Rod_Reel_Foot", (0, AXLE_Y, -3), (2, 4, 2), "fitting"),
        ("Rod_Reel", (0, AXLE_Y, AXLE_Z), (5, 6, 6), "line"),
        ("Rod_Reel_Plate_L", (3, AXLE_Y, AXLE_Z), (1, 8, 8), "reel"),
        ("Rod_Reel_Plate_R", (-3, AXLE_Y, AXLE_Z), (1, 8, 8), "reel"),
        ("Rod_Reel_Gem", (-4, AXLE_Y, AXLE_Z), (1, 3, 3), "gem"),
    ],
    "crank_pivot": (3.5, AXLE_Y, AXLE_Z),
    "crank": [
        ("Rod_Crank_Arm", (4, 14, AXLE_Z), (1, 4, 1), "reel"),
        ("Rod_Crank_Knob", (5.5, 15.5, AXLE_Z), (2, 2, 2), "knob"),
    ],
    "deco": {
        "Rod_Blank": [
            ("Rod_Blank_Spur_L", (3, 47.5, 0), (2, 1, 1), "bone"),
            ("Rod_Blank_Spur_R", (-3, 47.5, 0), (2, 1, 1), "bone"),
            ("Rod_Blank_Spur_Back", (0, 47.5, 3), (1, 1, 2), "bone"),
            ("Rod_Blank_Collar", (0, 17, 0), (5, 2, 5), "bone"),
        ],
        "Rod_Mid": [
            ("Rod_Mid_Spur_L", (2.5, 81.5, 0), (2, 1, 1), "bone"),
            ("Rod_Mid_Spur_R", (-2.5, 81.5, 0), (2, 1, 1), "bone"),
        ],
        "Rod_Upper": [("Rod_Upper_Spur", (0, 99, 1.5), (1, 1, 1), "bone")],
    },
    "ferrules": {"Rod_Blank": ((5, 2, 5), "bone"), "Rod_Mid": ((4, 2, 4), "bone"), "Rod_Upper": ((3, 1, 3), "bone")},
    "tip_top": ((3, 2, 3), "gem"),
}

DESIGNS = {"Crude": CRUDE, "Copper": COPPER, "Iron": IRON, "Thorium": THORIUM, "Cobalt": COBALT,
           "Adamantite": ADAMANTITE, "Mithril": MITHRIL, "Onyxium": ONYXIUM}


def design(tier):
    """The tier's design; stops on a tier without one."""
    if tier not in DESIGNS:
        raise SystemExit(f"no rod design for {tier}")
    return DESIGNS[tier]
