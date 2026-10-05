"""The four rarest rods' designs (rod_designs holds the format and the first four): cobalt, adamantite, mithril and
onyxium, after the language of Hytale's bows of those tiers (Common/Items/Weapons/Bow/<Tier>.blockymodel)."""

from rod_parts import AXLE_Y, AXLE_Z, flame, ring_plate


def chevron(name, x, y, mat):
    """A white chevron pointing up the rod on its +x face at height y: a peak and two arms (Hytale's cobalt bow)."""
    return [(f"{name}_Peak", (x, y + 1, 0), (1, 1, 1), mat), (f"{name}_Fore", (x, y, -1), (1, 1, 1), mat),
            (f"{name}_Aft", (x, y, 1), (1, 1, 1), mat)]


# Hytale's cobalt bow: deep blue, slim and angular, white chevrons, dark feathered tips. Here a slim grip over a
# diamond-cut steel butt, chevrons climbing the +x face, a reel whose flanges are set as diamonds, and a dark feather
# tied below the tip.
COBALT = {
    "fixed": [
        ("Rod_Butt_Point", (0, -19, 0), (3, 2, 3), "fitting", (0, 45, 0)),
        ("Rod_Butt", (0, -16, 0), (5, 4, 5), "fitting", (0, 45, 0)),
        ("Rod_Grip", (0, -4, 0), (4, 20, 4), "grip"),
        ("Rod_Grip_Ring_High", (0, 6.5, 0), (5, 1, 5), "fitting"),
        ("Rod_Seat", (0, 11.5, 0), (4, 9, 4), "blank"),
        ("Rod_Reel_Foot", (0, AXLE_Y, -3), (2, 4, 2), "fitting"),
        ("Rod_Reel", (0, AXLE_Y, AXLE_Z), (5, 6, 6), "line"),
        ("Rod_Reel_Plate_L", (3, AXLE_Y, AXLE_Z), (1, 7, 7), "reel", (45, 0, 0)),
        ("Rod_Reel_Plate_R", (-3, AXLE_Y, AXLE_Z), (1, 7, 7), "reel", (45, 0, 0)),
        ("Rod_Reel_Chevron", (-4, AXLE_Y, AXLE_Z), (1, 2, 2), "chevron", (45, 0, 0)),
    ],
    "crank_pivot": (3.5, AXLE_Y, AXLE_Z),
    "crank": [
        ("Rod_Crank_Arm", (4, 14, AXLE_Z), (1, 4, 1), "fitting"),
        ("Rod_Crank_Knob", (5.5, 15.5, AXLE_Z), (2, 2, 2), "knob"),
    ],
    "deco": {
        "Rod_Blank": [*chevron("Rod_Blank_Chevron_A", 2.5, 22, "chevron"),
                      *chevron("Rod_Blank_Chevron_B", 2.5, 33, "chevron")],
        "Rod_Mid": [*chevron("Rod_Mid_Chevron_A", 2, 56, "chevron"), *chevron("Rod_Mid_Chevron_B", 2, 69, "chevron")],
        "Rod_Upper": [*chevron("Rod_Upper_Chevron", 1.5, 90, "chevron")],
        "Rod_Tip": [("Rod_Tip_Feather", (0, 113, 2), (1, 5, 2), "feather", (-15, 0, 0)),
                    ("Rod_Tip_Feather_Tie", (0, 115.5, 1), (1, 1, 1), "fitting")],
    },
    "ferrules": {"Rod_Blank": ((5, 2, 5), "fitting"), "Rod_Mid": ((4, 2, 4), "fitting"),
                 "Rod_Upper": ((3, 1, 3), "fitting")},
    "tip_top": ((3, 2, 3), "fitting"),
}

# Hytale's adamantite bow: massive, blood red, riveted plates, orange fins like flames. Here a heavy riveted butt, a
# big reel with round flanges and a flame on top, a long crank with a large knob, and flame fins on both sides of the
# two lower joints.
ADAMANTITE = {
    "fixed": [
        ("Rod_Butt", (0, -17.5, 0), (7, 5, 7), "fitting"),
        ("Rod_Butt_Rivet_L", (4, -17.5, 0), (1, 1, 1), "rivet"),
        ("Rod_Butt_Rivet_R", (-4, -17.5, 0), (1, 1, 1), "rivet"),
        ("Rod_Butt_Plate", (0, -14.5, 0), (6, 1, 6), "blank"),
        ("Rod_Grip", (0, -4, 0), (5, 20, 5), "grip"),
        ("Rod_Grip_Plate", (0, 6, 0), (6, 2, 6), "fitting"),
        ("Rod_Seat", (0, 11.5, 0), (5, 9, 5), "blank"),
        ("Rod_Reel_Foot", (0, AXLE_Y, -3.5), (2, 3, 2), "fitting"),
        ("Rod_Reel", (0, AXLE_Y, AXLE_Z - 1), (6, 7, 7), "line"),
        *ring_plate("Rod_Reel_Plate_L", 3.5, 11, "reel", z=AXLE_Z - 1),
        *ring_plate("Rod_Reel_Plate_R", -3.5, 11, "reel", z=AXLE_Z - 1),
        ("Rod_Reel_Flame", (0, 17, AXLE_Z - 1), (1, 3, 3), "flame"),
        ("Rod_Reel_Flame_Tip", (0, 19.5, AXLE_Z - 1), (1, 2, 1), "ember"),
    ],
    "crank_pivot": (4, AXLE_Y, AXLE_Z - 1),
    "crank": [
        ("Rod_Crank_Hub", (4.5, AXLE_Y, AXLE_Z - 1), (1, 4, 4), "fitting"),
        ("Rod_Crank_Arm", (5.5, 16, AXLE_Z - 1), (1, 7, 1), "reel"),
        ("Rod_Crank_Knob", (7, 19.5, AXLE_Z - 1), (3, 3, 3), "knob"),
    ],
    "deco": {
        "Rod_Blank": [
            *flame("Rod_Blank_Fin_L", 2.5, 47, 1, "flame", "ember"),
            *flame("Rod_Blank_Fin_R", 2.5, 47, -1, "flame", "ember"),
            ("Rod_Blank_Band", (0, 19, 0), (5, 2, 5), "fitting"),
            ("Rod_Blank_Band_Rivet", (2.5, 19, 0), (1, 1, 1), "rivet"),
        ],
        "Rod_Mid": [
            *flame("Rod_Mid_Fin_L", 2, 81, 1, "flame", "ember"),
            *flame("Rod_Mid_Fin_R", 2, 81, -1, "flame", "ember"),
        ],
    },
    "ferrules": {"Rod_Blank": ((6, 3, 6), "fitting"), "Rod_Mid": ((5, 3, 5), "fitting"),
                 "Rod_Upper": ((3, 2, 3), "fitting")},
    "tip_top": ((3, 2, 3), "fitting"),
}

# Hytale's mithril bow: silver white, gold accents, Greek-key patterns. Here a silver rod ringed with gold, a gold
# pommel, a white grip under three gold rings, a Greek key in gold on the reel's outer flange, and a flared gold tip.
MITHRIL = {
    "fixed": [
        ("Rod_Butt_Pommel", (0, -19, 0), (4, 2, 4), "gold"),
        ("Rod_Butt", (0, -16.5, 0), (6, 3, 6), "blank"),
        ("Rod_Grip_Ring_Low", (0, -14.5, 0), (6, 1, 6), "gold"),
        ("Rod_Grip", (0, -4, 0), (5, 20, 5), "grip"),
        ("Rod_Grip_Ring_Mid", (0, -4, 0), (6, 1, 6), "gold"),
        ("Rod_Grip_Ring_High", (0, 6.5, 0), (6, 1, 6), "gold"),
        ("Rod_Seat", (0, 11.5, 0), (4, 9, 4), "blank"),
        ("Rod_Reel_Foot", (0, AXLE_Y, -3), (2, 4, 2), "gold"),
        ("Rod_Reel", (0, AXLE_Y, AXLE_Z), (5, 6, 6), "line"),
        ("Rod_Reel_Plate_L", (3, AXLE_Y, AXLE_Z), (1, 8, 8), "reel"),
        ("Rod_Reel_Plate_R", (-3, AXLE_Y, AXLE_Z), (1, 8, 8), "reel"),
        ("Rod_Reel_Key_Top", (-4, AXLE_Y + 3.5, AXLE_Z), (1, 1, 7), "gold"),
        ("Rod_Reel_Key_Bottom", (-4, AXLE_Y - 3.5, AXLE_Z), (1, 1, 7), "gold"),
        ("Rod_Reel_Key_Fore", (-4, AXLE_Y, AXLE_Z - 3.5), (1, 6, 1), "gold"),
        ("Rod_Reel_Key_Aft", (-4, AXLE_Y, AXLE_Z + 3.5), (1, 6, 1), "gold"),
        ("Rod_Reel_Key_Hook", (-4, AXLE_Y + 1, AXLE_Z - 1.5), (1, 1, 3), "gold"),
        ("Rod_Reel_Key_Stem", (-4, AXLE_Y - 0.5, AXLE_Z - 2.5), (1, 2, 1), "gold"),
    ],
    "crank_pivot": (3.5, AXLE_Y, AXLE_Z),
    "crank": [
        ("Rod_Crank_Arm", (4, 14, AXLE_Z), (1, 4, 1), "reel"),
        ("Rod_Crank_Knob", (5.5, 15.5, AXLE_Z), (2, 2, 2), "knob"),
    ],
    "deco": {
        "Rod_Blank": [("Rod_Blank_Ring_A", (0, 30, 0), (5, 1, 5), "gold"),
                      ("Rod_Blank_Ring_B", (0, 38, 0), (5, 1, 5), "gold")],
        "Rod_Mid": [("Rod_Mid_Ring", (0, 66, 0), (4, 1, 4), "gold")],
        "Rod_Upper": [("Rod_Upper_Ring", (0, 92, 0), (3, 1, 3), "gold")],
        "Rod_Tip": [("Rod_Tip_Crown", (0, 116.5, 0), (3, 1, 3), "gold")],
    },
    "ferrules": {"Rod_Blank": ((5, 2, 5), "gold"), "Rod_Mid": ((4, 2, 4), "gold"), "Rod_Upper": ((3, 1, 3), "gold")},
    "tip_top": ((4, 2, 4), "gold"),
}

# Hytale's onyxium bow: black, limbs like crystal blades, glowing violet and white points, a green grip. Here a black
# crystal rod with slanted shards at the joints, a crystal blade for a butt, a green grip, glowing violet guides, and a
# reel with a shard on top and a glowing stone on its flange.
ONYXIUM = {
    "fixed": [
        ("Rod_Butt_Point", (0, -23.5, 0), (1, 2, 1), "glow", (0, 45, 0)),
        ("Rod_Butt_Blade", (0, -20.5, 0), (2, 5, 2), "shard", (0, 45, 0)),
        ("Rod_Butt", (0, -16, 0), (5, 4, 5), "fitting", (0, 45, 0)),
        ("Rod_Grip", (0, -4, 0), (5, 20, 5), "grip"),
        ("Rod_Grip_Ring_High", (0, 6.5, 0), (6, 1, 6), "fitting"),
        ("Rod_Seat", (0, 11.5, 0), (4, 9, 4), "fitting"),
        ("Rod_Reel_Foot", (0, AXLE_Y, -3), (2, 4, 2), "fitting"),
        ("Rod_Reel", (0, AXLE_Y, AXLE_Z), (5, 6, 6), "line"),
        ("Rod_Reel_Plate_L", (3, AXLE_Y, AXLE_Z), (1, 8, 8), "reel"),
        ("Rod_Reel_Plate_R", (-3, AXLE_Y, AXLE_Z), (1, 8, 8), "reel"),
        ("Rod_Reel_Glow", (-4, AXLE_Y, AXLE_Z), (1, 2, 2), "glow"),
        ("Rod_Reel_Shard", (0, 17, AXLE_Z), (1, 4, 1), "shard", (0, 0, 20)),
    ],
    "crank_pivot": (3.5, AXLE_Y, AXLE_Z),
    "crank": [
        ("Rod_Crank_Arm", (4, 14, AXLE_Z), (1, 4, 1), "fitting"),
        ("Rod_Crank_Knob", (5.5, 15.5, AXLE_Z), (2, 2, 2), "knob"),
    ],
    "deco": {
        "Rod_Blank": [("Rod_Blank_Shard_A", (3, 46, 0), (1, 5, 1), "shard", (0, 0, -25)),
                      ("Rod_Blank_Shard_B", (-3, 45, 0.5), (1, 4, 1), "shard", (0, 0, 30)),
                      ("Rod_Blank_Shard_C", (0, 40, 2.5), (1, 3, 1), "shard", (25, 0, 0))],
        "Rod_Mid": [("Rod_Mid_Shard_A", (2.5, 80, 0), (1, 4, 1), "shard", (0, 0, -25)),
                    ("Rod_Mid_Shard_B", (-2.5, 79, 0), (1, 3, 1), "shard", (0, 0, 25))],
        "Rod_Upper": [("Rod_Upper_Shard", (1.5, 97, 0), (1, 2, 1), "shard", (0, 0, -25))],
    },
    "ferrules": {"Rod_Blank": ((5, 2, 5), "fitting"), "Rod_Mid": ((4, 2, 4), "fitting"),
                 "Rod_Upper": ((3, 1, 3), "fitting")},
    "tip_top": ((2, 3, 2), "glow"),
}
