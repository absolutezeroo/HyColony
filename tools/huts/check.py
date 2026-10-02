"""Checks of the hut tools: python tools/huts/check.py (no assets needed). An AssertionError names the case."""

import math
import sys
import unittest
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1] / "vanilla"))
from models import empty_shape, node  # noqa: E402
from pack import placed  # noqa: E402


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


if __name__ == "__main__":
    unittest.main()
