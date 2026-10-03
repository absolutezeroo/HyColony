"""Checks of blockpaint: python tools/blockpaint/check.py (no assets needed). An AssertionError names the case."""

import math
import unittest
from types import SimpleNamespace

from PIL import Image

import bake
import cull
import glint
import trim
from icons import screen, turned, turned_shade
from models import bounds, box_shape, empty_shape, face_rects, face_span, node, placed, unwrap
from paint import bleed


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


class FaceTest(unittest.TestCase):
    def test_a_box_s_side_faces_span_its_depth_and_a_quad_s_face_its_width_whatever_its_side(self):
        self.assertEqual((4, 3), face_span("left", (2, 3, 4)))
        self.assertEqual((2, 4), face_span("top", (2, 3, 4)))
        quad = shape_at((0, 0, 0))
        quad.update({"type": "quad", "settings": {"size": {"x": 5, "y": 6}},
                     "textureLayout": {"left": {"offset": {"x": 0, "y": 0}}}})
        self.assertEqual([("Quad", 0, 0, 5, 6)], list(face_rects([node("Quad", (0, 0, 0), quad)])))


class UnwrapTest(unittest.TestCase):
    def test_every_face_gets_its_own_island_two_pixels_apart_in_a_texture_of_multiples_of_32(self):
        sides = ("front", "back", "left", "right", "top", "bottom")
        nodes = [node("A", (0, 0, 0), box_shape((6, 4, 3), sides)), node("B", (0, 0, 0), box_shape((2, 9, 2), sides))]
        self.assert_apart(nodes, unwrap(nodes))
        bake.light_map(nodes)

    def test_islands_of_many_sizes_stay_two_pixels_apart_on_every_side(self):
        # Varied sizes leave narrow steps in the skyline: a face set left of an island keeps its gap too.
        sides = ("front", "back", "left", "right", "top", "bottom")
        nodes = [node(f"B{i}", (0, 0, 0), box_shape((i % 5 + 1, i % 7 + 1, i % 3 + 1), sides)) for i in range(40)]
        self.assert_apart(nodes, unwrap(nodes))

    def assert_apart(self, nodes, size):
        width, height = size
        self.assertEqual((0, 0), (width % 32, height % 32))
        rects = [(u0, v0, u1, v1) for _, u0, v0, u1, v1 in face_rects(nodes)]
        for i, a in enumerate(rects):
            self.assertTrue(a[2] <= width and a[3] <= height)
            for b in rects[i + 1:]:
                apart = a[2] + 2 <= b[0] or b[2] + 2 <= a[0] or a[3] + 2 <= b[1] or b[3] + 2 <= a[1]
                self.assertTrue(apart, (a, b))

    def test_a_texture_taller_than_max_side_gives_way_to_a_wider_one_even_of_a_larger_area(self):
        # 14 faces of 26 x 26: 32 x 416 is smaller than 64 x 224, but 416 is over MAX_SIDE.
        nodes = [node(f"P{i}", (0, 0, 0), box_shape((1, 26, 26), ("left", "right"))) for i in range(7)]
        self.assertEqual((64, 224), unwrap(nodes))

    def test_small_islands_stack_beside_a_tall_one_instead_of_starting_a_new_row(self):
        # A 10 x 30 face and six 8 x 8: two columns of three squares fit beside the tall face in a 32 x 32 texture
        # (rows the height of the tall face would need 64).
        nodes = [node("Tall", (0, 0, 0), box_shape((10, 30, 1), ("front",)))]
        nodes += [node(f"S{i}", (0, 0, 0), box_shape((8, 8, 1), ("front",))) for i in range(6)]
        self.assertEqual((32, 32), unwrap(nodes, (32,)))

    def test_faces_set_apart_are_laid_together_below_all_the_others(self):
        sides = ("front", "back", "left", "right", "top", "bottom")
        nodes = [node("Gem", (0, 0, 0), box_shape((2, 2, 2), sides)),
                 node("Block", (0, 0, 0), box_shape((8, 8, 8), sides))]
        unwrap(nodes, (64,), apart=lambda name: name == "Gem")
        rects = {(name, v0) for name, _, v0, _, _ in face_rects(nodes)}
        lowest_block = max(v0 for name, v0 in rects if name == "Block")
        self.assertTrue(all(v0 >= lowest_block + 8 + 2 for name, v0 in rects if name == "Gem"))


class BoundsTest(unittest.TestCase):
    def test_bounds_place_a_child_from_its_parents_shape_offset(self):
        child = shape_at((0, 0, 0))
        child.update({"type": "box", "settings": {"size": {"x": 2, "y": 2, "z": 2}}})
        parent = node("Parent", (0, 0, 0), shape_at((0, 0, 10)), [node("Child", (0, 0, 0), child)])
        self.assertEqual(((-1, -1, 9), (1, 1, 11)), bounds([parent]))


class IconViewTest(unittest.TestCase):
    def test_a_rolled_view_lays_a_tool_s_handle_towards_the_top_left_and_keeps_its_near_side_nearer(self):
        view = turned(0, 0, 45)
        x, y, _ = screen((0.0, 1.0, 0.0), 10.0, (0.0, 0.0), view)
        self.assertAlmostEqual(-10 * math.sqrt(0.5), x)
        self.assertAlmostEqual(-10 * math.sqrt(0.5), y)
        self.assertGreater(screen((0.0, 0.0, 1.0), 1.0, (0.0, 0.0), view)[2], 0)

    def test_a_turned_view_applies_yaw_before_pitch(self):
        x, y, depth = screen((1.0, 0.0, 0.0), 1.0, (0.0, 0.0), turned(90, 90, 0))
        self.assertAlmostEqual(0.0, x)
        self.assertAlmostEqual(-1.0, y)
        self.assertAlmostEqual(0.0, depth)

    def test_a_face_turned_away_from_the_camera_is_not_drawn(self):
        self.assertIsNone(turned_shade((0.0, 0.0, -1.0)))
        self.assertIsNotNone(turned_shade((0.0, 0.0, 1.0)))


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

    def test_a_side_edge_turned_from_the_light_is_not_darkened(self):
        point, front = (0.0, 0.0, 0.0), (0.0, 0.0, 1.0)
        plain = bake.brightness(point, front, None, [], "standard")
        self.assertEqual(plain, bake.brightness(point, front, ((1.0, 0.0, 0.0), 1.0), [], "standard"))

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

    def test_a_seam_between_boxes_side_by_side_gets_no_bevel_and_the_outer_corner_keeps_it(self):
        left, right = box("Left", (-1, 0, 0), (2, 6, 2)), box("Right", (1, 0, 0), (2, 6, 2))
        boxes = list(bake.world_boxes([left, right]))
        edges = {at: edge for at, _, _, edge in bake.texels(left["shape"], "front", (-1.0, 0.0, 0.0),
                                                              (0.0, 0.0, 0.0, 1.0), boxes)}
        self.assertIsNone(edges[(1, 3)])
        self.assertEqual(((-1.0, 0.0, 0.0), bake.BEVEL_RINGS[0]), edges[(0, 3)])

    def test_both_border_rings_agree_under_a_thin_plank_flush_on_top_and_under_one_a_unit_above(self):
        def top_rings(plank_y):
            leg = box("Leg", (0, 0, 0), (6, 6, 6))
            boxes = list(bake.world_boxes([leg, box("Plank", (0, plank_y, 0), (6, 1, 6))]))
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
                                                              (0.0, 0.0, 0.0, 1.0), list(bake.world_boxes([tall])))}
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


SIDES = ("front", "back", "left", "right", "top", "bottom")


def sides_of(nodes):
    return {n["name"]: set(n["shape"]["textureLayout"]) for n in nodes}


class CullTest(unittest.TestCase):
    def test_faces_pressed_together_are_dropped_and_the_rest_kept(self):
        nodes = [node("A", (0, 0, 0), box_shape((4, 4, 4), SIDES)), node("B", (4, 0, 0), box_shape((4, 4, 4), SIDES))]
        self.assertEqual([("A", "right"), ("B", "left")], cull.cull(nodes))
        self.assertEqual({"A": set(SIDES) - {"right"}, "B": set(SIDES) - {"left"}}, sides_of(nodes))

    def test_a_face_only_partly_covered_is_kept(self):
        nodes = [node("A", (0, 0, 0), box_shape((4, 4, 4), SIDES)), node("B", (3, 0, 0), box_shape((2, 2, 2), SIDES))]
        self.assertEqual([("B", "left")], cull.cull(nodes))

    def test_a_moving_node_neither_loses_faces_nor_hides_others(self):
        nodes = [node("A", (0, 0, 0), box_shape((4, 4, 4), SIDES)), node("B", (4, 0, 0), box_shape((4, 4, 4), SIDES))]
        self.assertEqual([], cull.cull(nodes, frozenset({"B"})))

    def test_the_children_of_a_moving_node_move_with_it(self):
        child = node("Child", (0, 0, 0), box_shape((4, 4, 4), SIDES))
        swing = node("Swing", (4, 0, 0), empty_shape(), [child])
        nodes = [node("A", (0, 0, 0), box_shape((4, 4, 4), SIDES)), swing]
        self.assertEqual([], cull.cull(nodes, frozenset({"Swing"})))

    def test_a_slab_one_unit_short_of_the_edge_leaves_the_face_below_it(self):
        # A 32 cube under a 31 wide slab: its top's last column of texels is open to the sky.
        nodes = [node("A", (0, 0, 0), box_shape((32, 32, 32), SIDES)),
                 node("Slab", (-0.5, 17, 0), box_shape((31, 2, 32), SIDES))]
        self.assertNotIn(("A", "top"), cull.cull(nodes))

    def test_a_groove_between_two_slabs_leaves_the_face_below_them(self):
        nodes = [node("A", (0, 0, 0), box_shape((32, 32, 32), SIDES)),
                 node("Left", (-8.5, 17, 0), box_shape((15, 2, 32), SIDES)),
                 node("Right", (8.5, 17, 0), box_shape((15, 2, 32), SIDES))]
        self.assertNotIn(("A", "top"), cull.cull(nodes))

    def test_a_box_turned_against_the_face_never_hides_it(self):
        turned_box = node("B", (4, 0, 0), box_shape((6, 6, 6), SIDES))
        turned_box["orientation"] = {"x": 0, "y": math.sin(math.radians(5)), "z": 0, "w": math.cos(math.radians(5))}
        nodes = [node("A", (0, 0, 0), box_shape((4, 4, 4), SIDES)), turned_box]
        self.assertNotIn(("A", "right"), cull.cull(nodes))

    def test_a_box_open_on_some_side_hides_nothing(self):
        # B shows only its top: through its missing sides one sees into it, and A's face inside.
        nodes = [node("A", (0, 0, 0), box_shape((4, 4, 4), SIDES)),
                 node("B", (4, 0, 0), box_shape((4, 4, 4), ("top",)))]
        self.assertNotIn(("A", "right"), cull.cull(nodes))

    def test_a_mirrored_box_loses_the_face_drawn_against_its_neighbour(self):
        # Stretched by -1 on x, A draws its right side at x -2 and its left side at x +2, against B.
        a = node("A", (0, 0, 0), box_shape((4, 4, 4), SIDES))
        a["shape"]["stretch"] = {"x": -1, "y": 1, "z": 1}
        dropped = cull.cull([a, node("B", (4, 0, 0), box_shape((4, 4, 4), SIDES))])
        self.assertIn(("A", "left"), dropped)
        self.assertNotIn(("A", "right"), dropped)

    def test_a_stretched_box_covers_as_far_as_it_is_drawn(self):
        # B is 2 wide stretched twice: drawn 4 wide, it covers all of A's right side.
        b = node("B", (4, 0, 0), box_shape((2, 2, 2), SIDES))
        b["shape"]["stretch"] = {"x": 2, "y": 2, "z": 2}
        self.assertIn(("A", "right"), cull.cull([node("A", (0, 0, 0), box_shape((4, 4, 4), SIDES)), b]))

    def test_a_flat_box_keeps_its_faces(self):
        # Stretched to nothing on y, A is a flat sheet: its faces are not boxes' faces any more, so cull leaves them.
        a = node("A", (0, 0, 0), box_shape((4, 4, 4), SIDES))
        a["shape"]["stretch"] = {"x": 1, "y": 0, "z": 1}
        dropped = cull.cull([a, node("B", (4, 0, 0), box_shape((4, 4, 4), SIDES))])
        self.assertEqual([], [d for d in dropped if d[0] == "A"])

    def test_an_invisible_box_hides_nothing(self):
        b = node("B", (4, 0, 0), box_shape((4, 4, 4), SIDES))
        b["shape"]["visible"] = False
        self.assertNotIn(("A", "right"), cull.cull([node("A", (0, 0, 0), box_shape((4, 4, 4), SIDES)), b]))

    def test_a_tilted_post_without_its_bottom_is_open_where_its_bottom_lifts_off_the_floor(self):
        post = node("Post", (0, 4, 0), box_shape((4, 8, 4), tuple(s for s in SIDES if s != "bottom")))
        post["orientation"] = {"x": 0, "y": 0, "z": math.sin(math.radians(15)), "w": math.cos(math.radians(15))}
        # Inside it and turned with it, so that only the open bottom can show it.
        inner = node("Inner", (0, 4, 0), box_shape((1, 1, 1), SIDES))
        inner["orientation"] = dict(post["orientation"])
        self.assertEqual([], [d for d in cull.cull([post, inner]) if d[0] == "Inner"])

    def test_a_face_dropped_before_still_seals_its_box(self):
        # Y on the floor, X on Y: X's bottom and Y's top go. X's bottom given back, Y (missing its top, which X covers)
        # still hides it, so a second pass drops it again.
        nodes = [node("Y", (0, 2, 0), box_shape((4, 4, 4), SIDES)), node("X", (0, 6, 0), box_shape((4, 4, 4), SIDES))]
        self.assertEqual([("Y", "top"), ("X", "bottom")], cull.cull(nodes))
        nodes[1]["shape"]["textureLayout"]["bottom"] = {"offset": {"x": 0, "y": 0}}
        self.assertEqual([("X", "bottom")], cull.cull(nodes))

    def test_a_box_sealed_only_by_an_open_box_hides_nothing(self):
        # Y lacks its top (open to the sky), Z lacks its left (against Y), X is whole. Through Y's top one sees through
        # Y and Z into X: Z is not sealed, so X's left stays; only Z's right, against whole X, goes.
        nodes = [node("Y", (0, 2, 0), box_shape((4, 4, 4), tuple(s for s in SIDES if s != "top"))),
                 node("Z", (4, 2, 0), box_shape((4, 4, 4), tuple(s for s in SIDES if s != "left"))),
                 node("X", (8, 2, 0), box_shape((4, 4, 4), SIDES))]
        self.assertEqual([("Z", "right")], cull.cull(nodes))

    def test_two_boxes_pressed_together_still_hide_others_once_their_shared_faces_are_gone(self):
        nodes = [node("A", (0, 2, 0), box_shape((4, 4, 4), SIDES)), node("B", (4, 2, 0), box_shape((4, 4, 4), SIDES)),
                 node("C", (2, 6, 0), box_shape((4, 4, 4), SIDES))]
        cull.cull(nodes)
        nodes[2]["shape"]["textureLayout"]["bottom"] = {"offset": {"x": 0, "y": 0}}
        self.assertEqual([("C", "bottom")], cull.cull(nodes))

    def test_a_box_standing_on_the_floor_without_its_bottom_still_hides(self):
        nodes = [node("A", (0, 2, 0), box_shape((4, 4, 4), SIDES)),
                 node("B", (4, 2, 0), box_shape((4, 4, 4), tuple(s for s in SIDES if s != "bottom")))]
        self.assertIn(("A", "right"), cull.cull(nodes))


def gem_model():
    """A shaft and one gem, unwrapped."""
    nodes = [node("Shaft", (0, 0, 0), box_shape((4, 8, 4), ("front", "back"))),
             node("Gem", (0, 6, 0), box_shape((2, 2, 2), SIDES))]
    return nodes, unwrap(nodes)


class GlintTest(unittest.TestCase):
    def test_frames_copy_only_the_gem_islands_below_with_the_glint_moving_across(self):
        nodes, size = gem_model()
        image = Image.new("RGBA", size, (40, 90, 160, 255))
        out, step = glint.frames(image, nodes, "Gem")
        self.assertEqual(0, out.height % 32)
        self.assertGreaterEqual(step, image.height)
        self.assertEqual(image.tobytes(), out.crop((0, 0, *size)).tobytes())
        u, v, w, h = next((u, v, w, h) for name, side, u, v, w, h in glint.islands(nodes)
                          if name == "Gem" and side == "front")
        lit = [[out.getpixel((u + x, v + y + k * step)) != (40, 90, 160, 255) for x in range(w) for y in range(h)]
               for k in range(1, glint.GLINT_FRAMES + 1)]
        self.assertTrue(all(any(frame) for frame in lit))
        self.assertNotEqual(lit[0], lit[-1])
        for _, _, u, v, w, h in (r for r in glint.islands(nodes) if r[0] == "Shaft"):
            for k in range(1, glint.GLINT_FRAMES + 1):
                below = out.crop((u, v + k * step, u + w, v + h + k * step))
                self.assertEqual((0, 0), below.getextrema()[3], f"Shaft copied in frame {k}")

    def test_the_flipbook_holds_each_frame_and_ends_on_the_plain_islands(self):
        keys = glint.flipbook(10)
        times = [k["time"] for k in keys]
        self.assertEqual(sorted(times), times)
        self.assertEqual((0, glint.DURATION), (times[0], times[-1]))
        self.assertEqual(0, keys[-1]["delta"]["y"])
        for k in range(1, glint.GLINT_FRAMES + 1):
            held = [key["time"] for key in keys if key["delta"]["y"] == -10 * k]
            self.assertEqual(glint.GLINT_HOLD, held[-1] - held[0])

    def test_the_animation_names_every_gem_node_and_no_other(self):
        nodes, _ = gem_model()
        self.assertEqual(["Gem"], list(glint.animation(nodes, "Gem", 10)["nodeAnimations"]))

    def test_gems_breathe_unless_told_not_to(self):
        nodes, _ = gem_model()
        self.assertEqual(glint.breath(), glint.animation(nodes, "Gem", 10)["nodeAnimations"]["Gem"]["shapeStretch"])

    def test_a_still_glint_keeps_the_flipbook_without_the_breath(self):
        nodes, _ = gem_model()
        gem = glint.animation(nodes, "Gem", 10, breathe=False)["nodeAnimations"]["Gem"]
        self.assertEqual([], gem["shapeStretch"])
        self.assertEqual(glint.flipbook(10), gem["shapeUvOffset"])


class TrimTest(unittest.TestCase):
    def test_every_box_gets_its_bottom_back_and_no_other_face(self):
        nodes = [node("Open", (0, 0, 0), box_shape((2, 2, 2), ("front", "top"))),
                 node("Sides", (0, 0, 0), box_shape((2, 2, 2), ("front", "back"))),
                 node("Closed", (0, 0, 0), box_shape((2, 2, 2), ("front", "bottom")))]
        nodes.append(node("Root", (0, 0, 0), empty_shape()))
        trim.close_bottoms(nodes)
        self.assertEqual([{"front", "top", "bottom"}, {"front", "back", "bottom"}, {"front", "bottom"}, set()],
                         [set(n["shape"]["textureLayout"]) for n in nodes])

    def test_trimming_a_trimmed_model_changes_nothing(self):
        # The builder's ink: Ink on Stand, Stand pressed against Wall. A first trim drops Stand's right side and Ink's
        # bottom; Stand, missing a side now, must still hide the bottom the second trim gives back to Ink.
        nodes = [node("Stand", (0, 2, 0), box_shape((4, 4, 4), SIDES)),
                 node("Wall", (4, 2, 0), box_shape((4, 4, 4), SIDES)),
                 node("Ink", (0, 5, 0), box_shape((2, 2, 2), SIDES))]
        trim.close_bottoms(nodes)
        cull.cull(nodes)
        once = [set(n["shape"]["textureLayout"]) for n in nodes]
        trim.close_bottoms(nodes)
        cull.cull(nodes)
        self.assertEqual(once, [set(n["shape"]["textureLayout"]) for n in nodes])

    def test_a_glint_alone_moves_nothing_but_a_breath_moves_the_gems(self):
        nodes, _ = gem_model()
        self.assertEqual(frozenset(), trim.moving(SimpleNamespace(GLINT="Gem", BREATHE=False), nodes))
        self.assertEqual(frozenset({"Gem"}), trim.moving(SimpleNamespace(GLINT="Gem"), nodes))

    def test_the_nodes_a_model_animates_move_whether_or_not_its_blockyanim_was_written(self):
        nodes, _ = gem_model()
        sway = {"nodeAnimations": {"Shaft": {"orientation": [{"time": 0}], "position": []},
                                   "Gem": {"orientation": [], "shapeUvOffset": [{"time": 0}]}}}
        self.assertEqual(frozenset({"Shaft"}), trim.moving(SimpleNamespace(animation=lambda n: sway), nodes))
        self.assertEqual(frozenset(), trim.moving(SimpleNamespace(), nodes))


if __name__ == "__main__":
    unittest.main()
