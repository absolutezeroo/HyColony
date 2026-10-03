"""Vérifications du ruban de chantier : python tools/tape/check.py (sans assets). Une AssertionError nomme le cas."""

import sys
import unittest
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1] / "blockpaint"))
from models import bounds  # noqa: E402
from shapes import SHAPES, parts  # noqa: E402


def ropes(shape):
    """The bounds of each rope of the shape (top-level nodes): name -> (lowest corner, highest corner) in model
    units."""
    return {n["name"]: bounds([n]) for n in parts(shape) if n["name"].startswith("Rope_")}


class ShapesTest(unittest.TestCase):
    def test_each_shape_has_the_stake_and_one_rope_per_joined_side(self):
        for shape, sides in SHAPES.items():
            names = [n["name"] for n in parts(shape)]
            self.assertEqual(5 + len(sides), len(names), shape)
            expected = {"Rope_" + side.capitalize() for side in sides}
            self.assertEqual(expected, {n for n in names if n.startswith("Rope_")})

    def test_a_straight_joins_north_and_south_and_a_corner_north_and_east(self):
        self.assertEqual({"north", "south"}, SHAPES["Straight"])
        self.assertEqual({"north", "east"}, SHAPES["Corner"])
        self.assertEqual({"north", "east", "west"}, SHAPES["T_Junction"])

    def test_every_rope_reaches_the_edge_of_its_side_and_sags_to_it(self):
        low, high = bounds(parts("Cross_Junction"))
        self.assertTrue(low[0] <= -16 and high[0] >= 16 and low[2] <= -16 and high[2] >= 16)
        for name, (rope_low, rope_high) in ropes("Cross_Junction").items():
            self.assertLess(rope_low[1], 17, name)
            self.assertGreater(rope_high[1], 20, name)


if __name__ == "__main__":
    unittest.main()
