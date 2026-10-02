"""Vérifications du ruban de chantier : python tools/tape/check.py (sans assets). Une AssertionError nomme le cas."""

import unittest

from shapes import ARM, CENTER, CORNER_POST, SHAPES, elements, turned


def box(element):
    return element["from"], element["to"]


class TurnedTest(unittest.TestCase):
    def test_a_quarter_turn_takes_the_north_post_to_the_east_side(self):
        # MC blockstate y = 90: (x, z) -> (16 - z, x), the north face becomes the east one.
        post = turned(ARM[0], 90)
        self.assertEqual(([15, 0, 6], [16, 16, 10]), box(post))
        self.assertEqual("#post", post["faces"]["east"]["texture"])
        self.assertEqual(set(ARM[0]["faces"]), set(post["faces"]))

    def test_half_and_three_quarter_turns_reach_south_and_west(self):
        self.assertEqual(([6, 0, 15], [10, 16, 16]), box(turned(ARM[0], 180)))
        self.assertEqual(([0, 0, 6], [1, 16, 10]), box(turned(ARM[0], 270)))


class ShapesTest(unittest.TestCase):
    def test_each_shape_has_mcs_arms_the_knot_and_a_post_only_at_a_corner(self):
        # MC's multipart: the centre knot always, one arm per joined side, the centre post for a corner only.
        for shape, sides in SHAPES.items():
            parts = elements(shape)
            self.assertEqual(len(CENTER) + len(ARM) * len(sides) + (len(CORNER_POST) if shape == "Corner" else 0),
                             len(parts), shape)

    def test_a_straight_joins_north_and_south_and_a_corner_north_and_east(self):
        self.assertEqual({"north", "south"}, SHAPES["Straight"])
        self.assertEqual({"north", "east"}, SHAPES["Corner"])
        self.assertEqual({"north", "east", "west"}, SHAPES["T_Junction"])


if __name__ == "__main__":
    unittest.main()
