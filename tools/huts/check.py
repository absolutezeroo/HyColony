"""Checks of the hut tools: python tools/huts/check.py (no assets needed). An AssertionError names the case."""

import math
import sys
import unittest
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1] / "vanilla"))
from models import empty_shape, node  # noqa: E402
from pack import placed  # noqa: E402
from paint import bleed  # noqa: E402
from PIL import Image  # noqa: E402


def shape_at(offset):
    shape = empty_shape()
    shape["offset"] = {"x": offset[0], "y": offset[1], "z": offset[2]}
    return shape


def positions(nodes):
    return {n["name"]: tuple(round(c, 6) for c in at) for n, at, _ in placed(nodes)}


class PlacedTest(unittest.TestCase):
    def test_a_child_counts_from_its_parents_position_plus_the_parents_shape_offset(self):
        # Hytale's Decorative_Sets/Crude/Chest_Small: the lid pivots on its back hinge (z -13) and its shape is moved
        # back over the chest (offset z +13); its lock then sits on the front seam (BlockyModelBoundsParser).
        lock = node("Lock-Hinge", (0, -6, 13.5), empty_shape())
        lid = node("Lid", (0, 7.5, -13), shape_at((0, 7, 13)), [lock])
        chest = node("Block", (0, 10, 0), empty_shape(), [lid])
        self.assertEqual((0, 18.5, 13.5), positions([chest])["Lock-Hinge"])

    def test_a_turned_parent_turns_its_offset_for_its_children(self):
        half = math.sqrt(0.5)
        child = node("Child", (0, 0, 0), empty_shape())
        parent = node("Parent", (0, 0, 0), shape_at((0, 0, 2)), [child])
        parent["orientation"] = {"x": 0, "y": half, "z": 0, "w": half}
        self.assertEqual((2, 0, 0), positions([parent])["Child"])


def two_islands():
    """A 1 x 1 x 1 box whose front reads (0, 0) and back (3, 0): islands 2 pixels apart on one row."""
    shape = shape_at((0, 0, 0))
    shape.update({"type": "box", "settings": {"size": {"x": 1, "y": 1, "z": 1}}, "textureLayout": {
        "front": {"offset": {"x": 0, "y": 0}}, "back": {"offset": {"x": 3, "y": 0}}}})
    return [node("Box", (0, 0, 0), shape)]


class BleedTest(unittest.TestCase):
    def test_each_island_extends_into_its_own_pixel_of_the_gap_and_keeps_its_inside(self):
        image = Image.new("RGBA", (5, 1), (0, 0, 0, 0))
        image.putpixel((0, 0), (255, 0, 0, 255))
        image.putpixel((3, 0), (0, 0, 255, 255))
        bleed(image, two_islands())
        self.assertEqual([(255, 0, 0, 255), (255, 0, 0, 255), (0, 0, 255, 255), (0, 0, 255, 255), (0, 0, 255, 255)],
                         [image.getpixel((x, 0)) for x in range(5)])


if __name__ == "__main__":
    unittest.main()
