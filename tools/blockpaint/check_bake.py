"""Checks of the baked light (bake.py, rays.py): python tools/blockpaint/check_bake.py (no assets needed). An
AssertionError names the case."""

import math
import unittest
from unittest import mock

from PIL import Image

import bake
import rays
from check import shape_at
from models import node


def box(name, at, size, layout=None):
    """A standard-shaded box node of size, its top face on the island at (0, 0) unless layout says otherwise."""
    shape = shape_at((0, 0, 0))
    shape.update({"type": "box", "settings": {"size": dict(zip("xyz", size))}, "shadingMode": "standard",
                  "textureLayout": layout or {"top": {"offset": {"x": 0, "y": 0}}}})
    return node(name, at, shape)


class RayTest(unittest.TestCase):
    def test_a_box_hovering_over_a_texel_darkens_it(self):
        lid = list(rays.world_boxes([box("Lid", (0, 3, 0), (6, 2, 6))]))
        up = (0.0, 1.0, 0.0)
        self.assertEqual(0.0, rays.occlusion((0.0, 0.0, 0.0), up, []))
        self.assertGreater(rays.occlusion((0.0, 0.0, 0.0), up, lid), 0.3)

    def test_a_ray_hits_a_turned_box_ahead_and_ignores_one_behind_or_beside_it(self):
        half = math.sqrt(0.5)
        ahead = box("Ahead", (0, 5, 0), (2, 2, 2))
        ahead["orientation"] = {"x": 0, "y": half, "z": 0, "w": half}
        boxes = list(rays.world_boxes([ahead, box("Behind", (0, -5, 0), (2, 2, 2))]))
        self.assertAlmostEqual(4.0, rays.hit((0.0, 0.0, 0.0), (0.0, 1.0, 0.0), boxes, 10.0))
        self.assertIsNone(rays.hit((0.0, 5.0, 0.0), (0.0, 1.0, 0.0), boxes, 10.0))
        self.assertIsNone(rays.hit((0.0, 0.0, 0.0), (1.0, 0.0, 0.0), boxes, 10.0))
        beam = box("Beam", (0, 0, 0), (8, 2, 2))
        beam["orientation"] = {"x": 0, "y": half, "z": 0, "w": half}
        beams = list(rays.world_boxes([beam]))
        self.assertAlmostEqual(6.0, rays.hit((0.0, 0.0, -10.0), (0.0, 0.0, 1.0), beams, 20.0))

    def test_a_ray_hits_a_long_box_whose_centre_lies_behind_its_start(self):
        sill = list(rays.world_boxes([box("Sill", (-2, 0, 0), (10, 2, 2))]))
        down = (math.sqrt(0.5), -math.sqrt(0.5), 0.0)
        self.assertAlmostEqual(math.sqrt(2), rays.hit((0.5, 2.0, 0.0), down, sill, 10.0))

    def test_the_floor_shades_a_grounded_foot_only(self):
        side = (0.0, 0.0, 1.0)
        self.assertGreater(rays.occlusion((0.0, 0.5, 0.0), side, [rays.FLOOR]), 0.0)
        self.assertEqual(0.0, rays.occlusion((0.0, -0.5, 0.0), side, [rays.FLOOR]))


class BakeTest(unittest.TestCase):
    def test_the_bevel_lights_a_front_face_s_top_edge_and_shades_its_bottom_edge(self):
        point, front = (0.0, 0.0, 0.0), (0.0, 0.0, 1.0)
        plain = bake.brightness(bake.Spot(point, front, None), [], "standard")
        self.assertGreater(bake.brightness(bake.Spot(point, front, ((0.0, 1.0, 0.0), 1.0)), [], "standard"), plain)
        self.assertLess(bake.brightness(bake.Spot(point, front, ((0.0, -1.0, 0.0), 1.0)), [], "standard"), plain)

    def test_a_side_edge_turned_from_the_light_is_not_darkened(self):
        point, front = (0.0, 0.0, 0.0), (0.0, 0.0, 1.0)
        plain = bake.brightness(bake.Spot(point, front, None), [], "standard")
        self.assertEqual(plain, bake.brightness(bake.Spot(point, front, ((1.0, 0.0, 0.0), 1.0)), [], "standard"))

    def test_border_rings_skip_faces_too_thin_for_them(self):
        u, v = (1.0, 0.0, 0.0), (0.0, -1.0, 0.0)
        self.assertIsNone(bake.edge((0, 2), (1, 6), (u, v)))
        self.assertIsNone(bake.edge((1, 1), (3, 6), (u, v)))
        self.assertEqual(bake.BEVEL_RINGS[0], bake.edge((0, 2), (3, 6), (u, v))[1])
        self.assertEqual(bake.BEVEL_RINGS[1], bake.edge((1, 2), (4, 6), (u, v))[1])

    def test_a_seam_between_boxes_side_by_side_gets_no_bevel_and_the_outer_corner_keeps_it(self):
        left, right = box("Left", (-1, 0, 0), (2, 6, 2)), box("Right", (1, 0, 0), (2, 6, 2))
        boxes = list(rays.world_boxes([left, right]))
        edges = {at: edge for at, _, _, edge in bake.texels(left["shape"], "front", (-1.0, 0.0, 0.0),
                                                              (0.0, 0.0, 0.0, 1.0), boxes)}
        self.assertIsNone(edges[(1, 3)])
        self.assertEqual(((-1.0, 0.0, 0.0), bake.BEVEL_RINGS[0]), edges[(0, 3)])

    def test_both_border_rings_agree_under_a_thin_plank_flush_on_top_and_under_one_a_unit_above(self):
        def top_rings(plank_y):
            leg = box("Leg", (0, 0, 0), (6, 6, 6))
            boxes = list(rays.world_boxes([leg, box("Plank", (0, plank_y, 0), (6, 1, 6))]))
            edges = {at: edge for at, _, _, edge in bake.texels(leg["shape"], "front", (0.0, 0.0, 0.0),
                                                                  (0.0, 0.0, 0.0, 1.0), boxes)}
            return edges[(2, 0)], edges[(2, 1)]

        self.assertEqual((None, None), top_rings(3.5))
        up = (0.0, 1.0, 0.0)
        self.assertEqual(((up, bake.BEVEL_RINGS[0]), (up, bake.BEVEL_RINGS[1])), top_rings(4.5))

    def test_a_stretched_box_keeps_both_bevel_rings_on_an_open_border(self):
        tall = box("Tall", (0, 0, 0), (6, 6, 6))
        tall["shape"]["stretch"]["y"] = 2
        edges = {at: edge for at, _, _, edge in bake.texels(tall["shape"], "front", (0.0, 0.0, 0.0),
                                                              (0.0, 0.0, 0.0, 1.0), list(rays.world_boxes([tall])))}
        up = (0.0, 1.0, 0.0)
        self.assertEqual(((up, bake.BEVEL_RINGS[0]), (up, bake.BEVEL_RINGS[1])), (edges[(2, 0)], edges[(2, 1)]))

    def test_lit_grades_painted_texels_and_leaves_transparent_ones(self):
        image = Image.new("RGBA", (2, 1), (100, 100, 100, 255))
        image.putpixel((1, 0), (100, 100, 100, 0))
        bake.lit(image, {(0, 0): 0.5, (1, 0): 0.5, (5, 5): 0.5})
        self.assertLess(image.getpixel((0, 0))[0], 100)
        self.assertEqual((100, 100, 100, 0), image.getpixel((1, 0)))

    def test_a_see_through_node_is_lit_but_shades_nothing(self):
        def top_light(see_through):
            base = box("Base", (0, 0, 0), (6, 2, 6))
            lid = box("Lid", (0, 4, 0), (6, 2, 6), {"top": {"offset": {"x": 10, "y": 0}}})
            values = bake.light_map([base, lid], see_through=see_through)
            return values[(3, 3)], (13, 3) in values

        shaded, _ = top_light(frozenset())
        open_sky, lid_lit = top_light(frozenset({"Lid"}))
        self.assertLess(shaded, open_sky)
        self.assertTrue(lid_lit)

    def test_two_faces_sharing_an_island_are_refused(self):
        shared = {side: {"offset": {"x": 0, "y": 0}} for side in ("front", "back")}
        with self.assertRaises(SystemExit):
            bake.light_map([box("Block", (0, 0, 0), (2, 2, 2), shared)])

    def test_light_map_is_survey_s_light_without_its_contexts(self):
        nodes = [box("Base", (0, 0, 0), (6, 2, 6)), box("Lid", (0, 4, 0), (6, 2, 6),
                                                       {"top": {"offset": {"x": 10, "y": 0}}})]
        values, contexts = bake.survey(nodes, grounded=True)
        self.assertEqual(set(values), set(contexts))
        # The rims serve the contexts only: light_map, which needs none, never measures them.
        with mock.patch.object(bake, "rims", side_effect=AssertionError("light_map measured the rims")):
            self.assertEqual(values, bake.light_map(nodes, grounded=True))


if __name__ == "__main__":
    unittest.main()
