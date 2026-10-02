"""Checks of the hut tools: python tools/huts/check.py (no assets needed). An AssertionError names the case."""

import math
import sys
import unittest
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1] / "vanilla"))
import bake  # noqa: E402
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


def box(name, at, size, layout=None):
    """A standard-shaded box node of size, its top face on the island at (0, 0) unless layout says otherwise."""
    shape = shape_at((0, 0, 0))
    shape.update({"type": "box", "settings": {"size": dict(zip("xyz", size))}, "shadingMode": "standard",
                  "textureLayout": layout or {"top": {"offset": {"x": 0, "y": 0}}}})
    return node(name, at, shape)


class BakeTest(unittest.TestCase):
    def test_a_box_hovering_over_a_texel_darkens_it(self):
        lid = list(bake.world_boxes([box("Lid", (0, 3, 0), (6, 2, 6))]))
        up = (0.0, 1.0, 0.0)
        self.assertEqual(0.0, bake.occlusion((0.0, 0.0, 0.0), up, []))
        self.assertGreater(bake.occlusion((0.0, 0.0, 0.0), up, lid), 0.3)

    def test_a_ray_hits_a_turned_box_ahead_and_ignores_one_behind_or_beside_it(self):
        half = math.sqrt(0.5)
        ahead = box("Ahead", (0, 5, 0), (2, 2, 2))
        ahead["orientation"] = {"x": 0, "y": half, "z": 0, "w": half}
        boxes = list(bake.world_boxes([ahead, box("Behind", (0, -5, 0), (2, 2, 2))]))
        self.assertAlmostEqual(4.0, bake.hit((0.0, 0.0, 0.0), (0.0, 1.0, 0.0), boxes, 10.0))
        self.assertIsNone(bake.hit((0.0, 5.0, 0.0), (0.0, 1.0, 0.0), boxes, 10.0))
        self.assertIsNone(bake.hit((0.0, 0.0, 0.0), (1.0, 0.0, 0.0), boxes, 10.0))
        beam = box("Beam", (0, 0, 0), (8, 2, 2))
        beam["orientation"] = {"x": 0, "y": half, "z": 0, "w": half}
        beams = list(bake.world_boxes([beam]))
        self.assertAlmostEqual(6.0, bake.hit((0.0, 0.0, -10.0), (0.0, 0.0, 1.0), beams, 20.0))

    def test_a_ray_hits_a_long_box_whose_centre_lies_behind_its_start(self):
        sill = list(bake.world_boxes([box("Sill", (-2, 0, 0), (10, 2, 2))]))
        down = (math.sqrt(0.5), -math.sqrt(0.5), 0.0)
        self.assertAlmostEqual(math.sqrt(2), bake.hit((0.5, 2.0, 0.0), down, sill, 10.0))

    def test_the_bevel_lights_a_front_face_s_top_edge_and_shades_its_bottom_edge(self):
        point, front = (0.0, 0.0, 0.0), (0.0, 0.0, 1.0)
        plain = bake.brightness(point, front, None, [], "standard")
        self.assertGreater(bake.brightness(point, front, ((0.0, 1.0, 0.0), 1.0), [], "standard"), plain)
        self.assertLess(bake.brightness(point, front, ((0.0, -1.0, 0.0), 1.0), [], "standard"), plain)

    def test_the_floor_shades_a_grounded_foot_only(self):
        side = (0.0, 0.0, 1.0)
        self.assertGreater(bake.occlusion((0.0, 0.5, 0.0), side, [bake.FLOOR]), 0.0)
        self.assertEqual(0.0, bake.occlusion((0.0, -0.5, 0.0), side, [bake.FLOOR]))

    def test_border_rings_skip_faces_too_thin_for_them(self):
        u, v = (1.0, 0.0, 0.0), (0.0, -1.0, 0.0)
        self.assertIsNone(bake.edge(0, 2, 1, 6, u, v))
        self.assertIsNone(bake.edge(1, 1, 3, 6, u, v))
        self.assertEqual(bake.BEVEL_RINGS[0], bake.edge(0, 2, 3, 6, u, v)[1])
        self.assertEqual(bake.BEVEL_RINGS[1], bake.edge(1, 2, 4, 6, u, v)[1])

    def test_two_faces_sharing_an_island_are_refused(self):
        shared = {side: {"offset": {"x": 0, "y": 0}} for side in ("front", "back")}
        image = Image.new("RGBA", (4, 4), (200, 200, 200, 255))
        with self.assertRaises(SystemExit):
            bake.light(image, [box("Block", (0, 0, 0), (2, 2, 2), shared)])


if __name__ == "__main__":
    unittest.main()
