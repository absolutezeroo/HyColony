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

    def test_a_box_open_on_top_but_plugged_by_a_lid_inside_and_a_rim_above_hides_the_lid_s_buried_faces(self):
        # A pot open on top, its stew flush with the opening inside it and a rim one unit above round its border:
        # one cannot see into the pot, so the stew's sides and bottom, buried in it, go and its top stays.
        open_top = tuple(s for s in SIDES if s != "top")
        nodes = [node("Pot", (0, 3, 0), box_shape((8, 6, 8), open_top)),
                 node("Stew", (0, 5.5, 0), box_shape((6, 1, 6), SIDES)),
                 node("Rim_F", (0, 6.5, 3.5), box_shape((8, 1, 1), SIDES)),
                 node("Rim_B", (0, 6.5, -3.5), box_shape((8, 1, 1), SIDES)),
                 node("Rim_L", (-3.5, 6.5, 0), box_shape((1, 1, 6), SIDES)),
                 node("Rim_R", (3.5, 6.5, 0), box_shape((1, 1, 6), SIDES))]
        cull.cull(nodes)
        self.assertEqual({"top"}, sides_of(nodes)["Stew"])

    def test_a_box_open_on_top_and_plugged_only_in_its_middle_hides_nothing(self):
        # Without the rim, the opening's border shows the inside of the pot, and the stew's sides in it.
        nodes = [node("Pot", (0, 3, 0), box_shape((8, 6, 8), tuple(s for s in SIDES if s != "top"))),
                 node("Stew", (0, 5.5, 0), box_shape((6, 1, 6), SIDES))]
        cull.cull(nodes)
        self.assertEqual(set(SIDES), sides_of(nodes)["Stew"])

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
