"""Checks of the plate armor that need no assets: python tools/armor/check.py. An AssertionError names the case."""

import math
import re
import sys
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path += [str(TOOLS / "common"), str(TOOLS / "huts")]
from arms import ARMS  # noqa: E402
from finish import PICTURES, STEMS, material  # noqa: E402
from models import bounds, multiply, placed, rotate, walk  # noqa: E402
from pieces import ALL, PIECES, about  # noqa: E402

ID = (0, 0, 0, 1)
# The bone nodes each piece copies, as Hytale's Cobalt armour has them (Common/Items/Armors/Cobalt/*.blockymodel):
# name -> (position, shape offset, orientation). The legs use the Cobalt's nested Pelvis > Thigh > Calf and its feet.
COBALT_BONES = {
    "Head": {"Head": ((0, -12, -2), (0, 15, 3), ID)},
    "Chest": {
        "Pelvis": ((0, -12, 0), (0, 0, 0), ID), "Belly": ((0, 8, 0), (0, 4, 0), ID),
        "Chest": ((0, 5, 0), (0, 11, 0), ID),
        "R-Arm": ((-13, 43, -1), (0, -7, 0), (0.006381, -0.060714, -0.104333, 0.992667)),
        "L-Arm": ((13, 43, -1), (0, -7, 0), (0.006381, 0.060714, 0.104333, 0.992667)),
    },
    "Hands": {
        "R-Forearm": ((-12, 12.51058, -1), (0, 0, 0), ID), "L-Forearm": ((12, 12.51058, -1), (0, 0, 0), ID),
        "R-Hand": ((0.27673, -7.52961, 0), (0, -5, 0), (0.00266, -0.06099, -0.04354, 0.99719)),
        "L-Hand": ((-0.27673, -7.52961, 0), (0, -5, 0), (0.00266, 0.06099, 0.04354, 0.99719)),
    },
    "Legs": {
        "Pelvis": ((0, 51, 0), (0, 0, 0), ID),
        "R-Thigh": ((-8, -1, 1), (0, -9, 0), (0, 0.017452, 0, -0.999848)),
        "L-Thigh": ((8, -1, 1), (0, -9, 0), (0, -0.017452, 0, -0.999848)),
        "R-Calf": ((0, -12, 3), (0, -12, -3), ID), "L-Calf": ((0, -12, 3), (0, -12, -3), ID),
        "R-Foot": ((-7.846449, 6.999998, -2.997052), (0, -3, 6), (0, -0.019197, 0, 0.999816)),
        "L-Foot": ((7.846449, 6.999998, -2.997052), (0, -3, 6), (0, 0.019197, 0, 0.999816)),
    },
}
MATERIALS = set(STEMS.values()) | PICTURES | {"plate", "lames", "cloth", "plume", "plume_white", "grip", "gem",
                                               "blade", "edge", "shield", "shield_top"}
# The node chain of Hytale's iron sword and shield (Common/Items/Weapons/{Sword,Shield}/Iron.blockymodel; their Handle
# is a box, ours a shapeless node at its place):
# name -> (position, shape offset, orientation, isPiece).
IRON_CHAINS = {
    "Sword": {
        "R-Attachment": ((0, 13, 0), (0.000068, 0, 0), ID, True),
        "Origin_Projectile": ((-0.000068, 64, 0), (0, 0, 0), ID, True),
        "Origin_Blade": ((0, -22, 0), (0, 0, 0), ID, False),
        "Origin_Item": ((0, -10, 0), (0, 0, 0), ID, False),
        "Handle": ((0.000068, -32, 0), (-2.000477, 0, 0), (-0.5, -0.5, 0.5, 0.5), False),
    },
    "Shield": {
        "L-Attachment": ((0, 34, 0), (0, 0, 0), ID, True),
        "Origin_Projectile": ((3, 26, 14), (0, 0, 0), (0.258819, 0, 0, 0.965926), True),
        "Origin_Item": ((1, -29.51666, 0.875644), (0, 0, 0), (-0.258819, 0, 0, 0.965926), False),
        "Handle": ((-4, 0, 0), (0, 0, 0), (-0.430459, -0.560986, 0.430459, 0.560986), False),
    },
}
# Where the iron weapons' Origin_Item and Handle fall in the model's frame (models.placed of the iron models):
# a node moved elsewhere in the chain changes them.
IRON_WORLD = {"Sword": {"Origin_Item": (0, 45, 0), "Handle": (0, 13, 0)},
              "Shield": {"Origin_Item": (4, 34, 0), "Handle": (0, 34, 0)}}


def xyz(d):
    return tuple(d[k] for k in "xyz")


def plume_front(nodes):
    """The plume's foremost z: the highest z of any corner of its boxes, placed as Hytale places them."""
    front = -math.inf
    for n, pos, rot in placed(nodes):
        if n["name"].startswith(("Hair-", "Plume_")):
            alone = dict(n, children=[], position=dict(zip("xyz", pos)), orientation=dict(zip("xyzw", rot)))
            front = max(front, bounds([alone])[1][2])
    return front


def twin(name):
    """The left twin of a right-hand node's name (…_R…, …R at the end of a part, Hair-R…), else None."""
    left = re.sub(r"(?<=[_-])R(?=\d|_|$)|(?<=_[A-Z])R$", "L", name)
    return left if left != name else None


class PiecesTest(unittest.TestCase):
    def test_each_piece_copies_the_cobalt_bones_exactly(self):
        for piece, build in PIECES.items():
            bones = {n["name"]: n for n in walk(build()) if n["shape"]["type"] == "none"}
            self.assertEqual(set(COBALT_BONES[piece]), set(bones), piece)
            for name, (position, offset, orientation) in COBALT_BONES[piece].items():
                n = bones[name]
                for got, want in ((xyz(n["position"]), position), (xyz(n["shape"]["offset"]), offset),
                                  (tuple(n["orientation"][k] for k in "xyzw"), orientation)):
                    for g, w in zip(got, want):
                        self.assertAlmostEqual(g, w, 4, (piece, name))

    def test_only_bones_are_shapeless_and_every_bone_is_a_piece(self):
        # Hytale attaches an isPiece node to the player's bone of its name; others stay at the entity's feet.
        for piece, build in PIECES.items():
            for n in walk(build()):
                piece_flag = n["shape"]["settings"].get("isPiece", False)
                self.assertEqual(n["shape"]["type"] == "none", piece_flag, (piece, n["name"]))

    def test_the_plume_and_tabard_carry_the_names_the_player_animations_swing(self):
        head = {n["name"] for n in walk(PIECES["Head"]())}
        chest = {n["name"] for n in walk(PIECES["Chest"]())}
        self.assertLessEqual({"Hair-B", "Hair-B2", "Hair-L", "Hair-L2", "Hair-R", "Hair-R2"}, head)
        self.assertLessEqual({"Front_Cloth_1", "Front_Cloth_2", "Back_Cloth_1", "Back_Cloth_2"}, chest)

    def test_the_plume_and_tabard_are_chains_hinged_end_to_end(self):
        # Each swinging node's children must follow it: a segment is the child of the one before, its hinge (its node,
        # its shape offset running from there) on the end of its parent, within the one unit they sink into it.
        for piece in ("Head", "Chest"):
            nodes = PIECES[piece]()
            world = {n["name"]: (pos, rot) for n, pos, rot in placed(nodes)}
            for parent in walk(nodes):
                if not parent["name"].startswith(("Hair-", "Plume_", "Front_Cloth", "Back_Cloth")):
                    continue
                for child in parent["children"]:
                    size = parent["shape"]["settings"]["size"]
                    pos, rot = world[parent["name"]]
                    end = rotate(rot, (0, -size["y"], 0) if "_Cloth" in parent["name"] else (0, 0, size["z"]))
                    end = tuple(a + b for a, b in zip(pos, end))
                    gap = max(abs(a - b) for a, b in zip(world[child["name"]][0], end))
                    self.assertLessEqual(gap, 1.0 + 1e-6, (parent["name"], child["name"]))
            names = {n["name"]: n for n in walk(nodes)}
            if piece == "Head":
                for side in "BLR":
                    self.assertIn(names[f"Hair-{side}2"], names[f"Hair-{side}"]["children"])
                    tail = names[f"Hair-{side}"]
                    while tail["children"]:
                        tail = tail["children"][0]
                    self.assertTrue(tail["name"].startswith("Plume_"), side)
                # The right feather leans to the player's right, -x, as the skeleton's R- bones.
                self.assertLess(world["Plume_R7"][0][0], 0)
            else:
                for half, side in (("Front", 1), ("Back", -1)):
                    self.assertIn(names[f"{half}_Cloth_2"], names[f"{half}_Cloth_1"]["children"])
                    pivot, rot = world[f"{half}_Cloth_1"]
                    hem = rotate(rot, (0, -12, 0))
                    self.assertGreater(side * hem[2], 0, half)

    def test_running_lifts_the_plume_back_not_forward(self):
        # The run animation turns the hair nodes up to +45 degrees in x (Default/Run): no corner of the plume may come
        # forward of where it rests (on the rising segments the same turn tips it forward, against the wind).
        nodes = PIECES["Head"]()
        rest = plume_front(nodes)
        for n in walk(nodes):
            if n["name"] in ("Hair-B", "Hair-L", "Hair-R"):
                o = n["orientation"]
                turned = multiply((o["x"], o["y"], o["z"], o["w"]), about((1, 0, 0), 45))
                n["orientation"] = dict(zip("xyzw", turned))
        self.assertLessEqual(plume_front(nodes), rest + 1e-6)

    def test_the_hilt_ring_is_gold(self):
        self.assertEqual("gold", material("Grip_Ring", "front"))

    def test_the_left_side_mirrors_the_right(self):
        for piece, build in PIECES.items():
            world = {n["name"]: (pos, rot) for n, pos, rot in placed(build())}
            pairs = [(name, twin(name)) for name in world if twin(name)]
            self.assertTrue(pairs, piece)
            for right, left in pairs:
                (p, q), (p2, q2) = world[right], world[left]
                self.assertEqual(len(p), 3)
                for g, w in zip(p2, (-p[0], p[1], p[2])):
                    self.assertAlmostEqual(g, w, 5, (piece, right))
                mirror = (q[0], -q[1], -q[2], q[3])
                sign = 1 if sum(a * b for a, b in zip(q2, mirror)) >= 0 else -1
                for g, w in zip(q2, mirror):
                    self.assertAlmostEqual(g, sign * w, 5, (piece, right))

    def test_the_sword_and_shield_keep_the_iron_chains(self):
        for weapon, build in ARMS.items():
            markers = {n["name"]: n for n in walk(build()) if n["shape"]["type"] == "none"}
            self.assertEqual(set(IRON_CHAINS[weapon]), set(markers), weapon)
            for name, (position, offset, orientation, piece) in IRON_CHAINS[weapon].items():
                n = markers[name]
                self.assertEqual(piece, n["shape"]["settings"]["isPiece"], (weapon, name))
                for got, want in ((xyz(n["position"]), position), (xyz(n["shape"]["offset"]), offset),
                                  (tuple(n["orientation"][k] for k in "xyzw"), orientation)):
                    for g, w in zip(got, want):
                        self.assertAlmostEqual(g, w, 5, (weapon, name))
            world = {n["name"]: pos for n, pos, _ in placed(build())}
            for name, want in IRON_WORLD[weapon].items():
                for g, w in zip(world[name], want):
                    self.assertAlmostEqual(g, w, 3, (weapon, name))

    def test_every_face_has_a_known_material(self):
        for piece, build in {**PIECES, **ARMS}.items():
            for n in walk(build()):
                if n["shape"]["type"] == "box":
                    for side in ALL:
                        self.assertIn(material(n["name"], side), MATERIALS, (piece, n["name"], side))


if __name__ == "__main__":
    unittest.main()
