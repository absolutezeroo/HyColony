"""Checks of the maps a surface is built from, and of face seeds and grain (spec 2026-10-03 blockpaint surfaces):
python tools/blockpaint/check_surfaces.py (no assets needed). An AssertionError names the case. Layers and effects:
check_layers.py, check_effects.py."""

import unittest

import bake
import brushes
import maps
import paint
from models import box_shape, node, unwrap

SIDES = ("front", "back", "left", "right", "top", "bottom")


def model(*boxes):
    """Nodes of (name, centre, size) boxes, every face on its own island."""
    nodes = [node(name, at, box_shape(size, SIDES)) for name, at, size in boxes]
    unwrap(nodes)
    return nodes


def by_face(contexts, name, side):
    return [t for t in contexts.values() if t.face == (name, side)]


class SurveyTest(unittest.TestCase):
    def test_survey_gives_the_light_map_and_a_context_for_every_lit_texel(self):
        nodes = model(("Block", (0, 4, 0), (8, 8, 8)))
        values, contexts = bake.survey(nodes, grounded=True)
        self.assertEqual(bake.light_map(nodes, grounded=True), values)
        self.assertEqual(set(values), set(contexts))

    def test_without_wear_the_lit_edges_lose_their_light_chips_and_nothing_else_changes(self):
        nodes = model(("Block", (0, 4, 0), (8, 8, 8)))
        worn, contexts = bake.survey(nodes)
        bare, _ = bake.survey(nodes, wear=0.0)
        changed = [t for t in worn if worn[t] != bare[t]]
        self.assertTrue(changed)
        self.assertTrue(all(worn[t] > bare[t] for t in changed))
        # The chips sit on the outer bevel ring only.
        self.assertTrue(all(contexts[t].edge is not None and contexts[t].edge[1] == bake.BEVEL_RINGS[0]
                            for t in changed))

    def test_without_grime_a_grounded_foot_is_lighter(self):
        nodes = model(("Block", (0, 4, 0), (8, 8, 8)))
        grimy, contexts = bake.survey(nodes, grounded=True)
        clean, _ = bake.survey(nodes, grounded=True, grime=0.0)
        foot = [t for t, c in contexts.items() if c.face[1] == "front" and c.point[1] < 2]
        self.assertTrue(foot)
        self.assertTrue(all(clean[t] > grimy[t] for t in foot))

    def test_height_runs_from_the_model_s_foot_to_its_top(self):
        nodes = model(("Block", (0, 4, 0), (8, 8, 8)))
        _, contexts = bake.survey(nodes, grounded=True)
        self.assertTrue(all(t.height == 0.0 for t in by_face(contexts, "Block", "bottom")))
        self.assertTrue(all(t.height == 1.0 for t in by_face(contexts, "Block", "top")))
        front = by_face(contexts, "Block", "front")
        self.assertTrue(all(0.0 < t.height < 1.0 for t in front))

    def test_height_counts_from_the_model_s_own_foot_not_from_the_ground(self):
        _, contexts = bake.survey(model(("Raised", (0, 8, 0), (8, 8, 8))))
        self.assertTrue(all(t.height == 0.0 for t in by_face(contexts, "Raised", "bottom")))
        self.assertTrue(all(t.height == 1.0 for t in by_face(contexts, "Raised", "top")))

    def test_a_flat_model_has_height_zero(self):
        _, contexts = bake.survey(model(("Sheet", (0, 0, 0), (8, 0, 8))))
        self.assertTrue(all(t.height == 0.0 for t in contexts.values()))

    def test_occlusion_is_stronger_in_an_inner_corner_than_on_an_open_face(self):
        nodes = model(("Floor", (0, 1, 0), (16, 2, 16)), ("Wall", (0, 6, -6), (16, 8, 4)))
        _, contexts = bake.survey(nodes)
        # The wall stands on the floor from z -8 to -4: the corner is just in front of it.
        top = by_face(contexts, "Floor", "top")
        corner = [t for t in top if -4 < t.point[2] < -3 and abs(t.point[0]) < 2]
        open_part = [t for t in top if t.point[2] > 6 and abs(t.point[0]) < 2]
        self.assertTrue(corner and open_part)
        self.assertGreater(min(t.occlusion for t in corner), max(t.occlusion for t in open_part))
        self.assertTrue(all(0.0 <= t.occlusion <= 1.0 for t in contexts.values()))

    def test_edges_lie_on_the_outer_rings_but_not_inside_nor_on_a_seam(self):
        nodes = model(("Left", (-4, 4, 0), (8, 8, 8)), ("Right", (4, 4, 0), (8, 8, 8)))
        _, contexts = bake.survey(nodes)
        front = by_face(contexts, "Left", "front")
        middle = [t for t in front if abs(t.point[0] + 4) < 1 and abs(t.point[1] - 4) < 1]
        # Texel centres lie half a texel in: the outer ring at x -7.5, the ring along the seam at x -0.5.
        outer = [t for t in front if t.point[0] < -7 and 2 < t.point[1] < 6]
        seam = [t for t in front if t.point[0] > -1 and 2 < t.point[1] < 6]
        self.assertTrue(middle and outer and seam)
        self.assertTrue(all(t.edge is None for t in middle))
        self.assertTrue(all(t.edge is not None for t in outer))
        self.assertTrue(all(t.edge is None for t in seam))

    def test_each_context_knows_its_face_its_light_and_the_world_axes_of_its_island(self):
        nodes = model(("Block", (0, 4, 0), (8, 8, 8)))
        _, contexts = bake.survey(nodes)
        top, front = by_face(contexts, "Block", "top")[0], by_face(contexts, "Block", "front")[0]
        bottom = by_face(contexts, "Block", "bottom")[0]
        self.assertEqual((0.0, 1.0, 0.0), top.normal)
        self.assertEqual(("Block", "top"), top.face)
        self.assertAlmostEqual(bake.LIGHT[1], top.light)
        self.assertAlmostEqual(bake.LIGHT[2], front.light)
        self.assertAlmostEqual(-bake.LIGHT[1], bottom.light)
        # As models.face_point lays faces out: u to the right, v down, seen from outside.
        self.assertEqual(((1.0, 0.0, 0.0), (0.0, 0.0, 1.0)), (top.u_dir, top.v_dir))
        self.assertEqual(((1.0, 0.0, 0.0), (0.0, -1.0, 0.0)), (front.u_dir, front.v_dir))


class RimTest(unittest.TestCase):
    def test_rim_is_zero_on_an_open_border_and_grows_inwards(self):
        _, contexts = bake.survey(model(("Block", (0, 4, 0), (8, 8, 8))))
        front = by_face(contexts, "Block", "front")
        self.assertEqual(0, min(t.rim for t in front))
        centre = [t for t in front if abs(t.point[0]) < 1 and abs(t.point[1] - 4) < 1]
        self.assertTrue(all(t.rim == 3 for t in centre))

    def test_a_seam_is_no_border(self):
        nodes = model(("Left", (-4, 4, 0), (8, 8, 8)), ("Right", (4, 4, 0), (8, 8, 8)))
        _, contexts = bake.survey(nodes)
        seam = [t for t in by_face(contexts, "Left", "front") if t.point[0] > -1 and 2 < t.point[1] < 6]
        self.assertTrue(seam)
        self.assertTrue(all(t.rim > 0 for t in seam))

    def test_rim_stops_at_its_cap(self):
        _, contexts = bake.survey(model(("Slab", (0, 20, 0), (40, 40, 40))))
        self.assertEqual(bake.RIM_CAP, max(t.rim for t in contexts.values()))


class MapsTest(unittest.TestCase):
    def test_up_follows_the_normal(self):
        _, contexts = bake.survey(model(("Block", (0, 4, 0), (8, 8, 8))))
        top, bottom = by_face(contexts, "Block", "top")[0], by_face(contexts, "Block", "bottom")[0]
        side = by_face(contexts, "Block", "front")[0]
        self.assertEqual((1.0, 0.0, 0.0), (maps.up(top), maps.up(bottom), maps.up(side)))

    def test_an_open_top_sees_more_sky_than_an_inner_corner(self):
        nodes = model(("Floor", (0, 1, 0), (16, 2, 16)), ("Wall", (0, 6, -6), (16, 8, 4)))
        _, contexts = bake.survey(nodes)
        top = by_face(contexts, "Floor", "top")
        corner = [t for t in top if -4 < t.point[2] < -3 and abs(t.point[0]) < 2]
        open_part = [t for t in top if t.point[2] > 6 and abs(t.point[0]) < 2]
        self.assertGreater(min(maps.sky(t) for t in open_part), max(maps.sky(t) for t in corner))
        self.assertGreater(min(maps.water(t) for t in corner), max(maps.water(t) for t in open_part))

    def test_exposure_is_highest_on_open_borders_and_the_sky(self):
        _, contexts = bake.survey(model(("Block", (0, 4, 0), (8, 8, 8))))
        front = by_face(contexts, "Block", "front")
        rim = [t for t in front if t.rim == 0]
        inner = [t for t in front if t.rim == 3]
        self.assertTrue(all(maps.exposure(t) == 1.0 for t in rim))
        self.assertTrue(all(maps.exposure(t) == 0.0 for t in inner))
        self.assertTrue(all(maps.exposure(t) > 0.9 for t in by_face(contexts, "Block", "top")))

    def test_ground_is_strongest_at_a_grounded_foot_and_gone_high_up(self):
        _, contexts = bake.survey(model(("Tower", (0, 16, 0), (8, 32, 8))), grounded=True)
        front = by_face(contexts, "Tower", "front")
        self.assertGreater(min(maps.ground(t) for t in front if t.point[1] < 1), 0.9)
        self.assertEqual(0.0, max(maps.ground(t) for t in front if t.point[1] > bake.GRIME_HEIGHT))

    def test_a_model_standing_on_no_floor_is_near_no_ground(self):
        # A held blade centred on the origin: half of it lies below y 0, none of it on a floor.
        _, contexts = bake.survey(model(("Blade", (0, 0, 0), (2, 16, 1))))
        self.assertEqual({0.0}, {maps.ground(t) for t in contexts.values()})

    def test_water_stays_off_side_faces(self):
        _, contexts = bake.survey(model(("Block", (0, 4, 0), (8, 8, 8))), grounded=True)
        self.assertTrue(all(maps.water(t) == 0.0 for t in by_face(contexts, "Block", "front")))


def island_of(image, nodes, name, side):
    for n, s, u, v, w, h in paint.islands(nodes):
        if (n, s) == (name, side):
            return image.crop((u, v, u + w, v + h))
    raise KeyError((name, side))


def steps(image, dx, dy):
    """Mean colour step between each texel and its neighbour (dx, dy) away."""
    pairs = [(image.getpixel((x, y)), image.getpixel((x + dx, y + dy)))
             for x in range(image.width - dx) for y in range(image.height - dy)]
    return sum(sum(abs(a[k] - b[k]) for k in range(3)) for a, b in pairs) / len(pairs)


class SeedTest(unittest.TestCase):
    def setUp(self):
        self.nodes = model(("A", (-10, 4, 0), (8, 8, 8)), ("B", (10, 4, 0), (8, 8, 8)))
        self.size = unwrap(self.nodes)
        self.tiles = {"wood": brushes.wood((150, 104, 62))}

    def painted(self, seed=None):
        return paint.paint(self.nodes, self.size, paint.Look(self.tiles, lambda name, side: "wood"),
                           paint.Painting(seed))

    def test_without_a_seed_jitter_keeps_its_values(self):
        # Values of brushes.jitter before seeds existed: a model without a seed paints the same bytes.
        self.assertEqual([-0.1797, -0.7103, 0.0665], [round(brushes.jitter(i, s), 4) for i, s in ((3, 7), (0, 2),
                                                                                                   (41, 33))])

    def test_without_a_seed_two_faces_of_one_size_look_alike_and_with_one_they_differ(self):
        plain, seeded = self.painted(), self.painted(seed=7)
        a, b = island_of(plain, self.nodes, "A", "front"), island_of(plain, self.nodes, "B", "front")
        self.assertEqual(a.tobytes(), b.tobytes())
        a, b = island_of(seeded, self.nodes, "A", "front"), island_of(seeded, self.nodes, "B", "front")
        self.assertNotEqual(a.tobytes(), b.tobytes())

    def test_the_same_seed_paints_the_same_bytes_and_another_seed_others(self):
        self.assertEqual(self.painted(seed=7).tobytes(), self.painted(seed=7).tobytes())
        self.assertNotEqual(self.painted(seed=7).tobytes(), self.painted(seed=8).tobytes())

    def test_face_seeds_are_stable_and_zero_without_a_model_seed(self):
        self.assertEqual(0, paint.face_seed("A", "front", None))
        self.assertEqual(paint.face_seed("A", "front", 7), paint.face_seed("A", "front", 7))
        self.assertNotEqual(paint.face_seed("A", "front", 7), paint.face_seed("A", "back", 7))

    def test_the_seed_is_reset_after_a_brush_fails(self):
        before = brushes.jitter(5, 9)

        def broken(w, h, side):
            raise RuntimeError("broken brush")

        with self.assertRaises(RuntimeError):
            paint.paint(self.nodes, self.size, paint.Look({"wood": broken}, lambda name, side: "wood"),
                        paint.Painting(7))
        self.assertEqual(before, brushes.jitter(5, 9))

    def test_a_nested_seed_or_grain_gives_the_outer_one_back(self):
        with brushes.seeded(7), brushes.grain("u"):
            outer = brushes.jitter(5, 9)
            with brushes.seeded(8), brushes.grain("v"):
                self.assertNotEqual(outer, brushes.jitter(5, 9))
            self.assertEqual(outer, brushes.jitter(5, 9))
            self.assertEqual("u", brushes._face["grain"])

    def test_a_picture_stays_where_it_is_drawn_on_a_seeded_model(self):
        picture = brushes.as_tile(brushes.stone((128, 124, 116)))
        look = paint.Look({"picture": picture}, lambda name, side: "picture", frozenset({"picture"}))
        seeded = paint.paint(self.nodes, self.size, look, paint.Painting(7))
        self.assertEqual(picture.crop((0, 0, 8, 8)).tobytes(), island_of(seeded, self.nodes, "A", "front").tobytes())

    def test_a_tile_image_shows_another_window_on_each_seeded_face(self):
        tile = brushes.as_tile(brushes.stone((128, 124, 116)))
        look = paint.Look({"stone": tile}, lambda name, side: "stone")
        plain = paint.paint(self.nodes, self.size, look)
        seeded = paint.paint(self.nodes, self.size, look, paint.Painting(7))
        a, b = island_of(seeded, self.nodes, "A", "front"), island_of(seeded, self.nodes, "B", "front")
        self.assertNotEqual(a.tobytes(), b.tobytes())
        self.assertEqual(island_of(plain, self.nodes, "A", "front").tobytes(),
                         island_of(plain, self.nodes, "B", "front").tobytes())


class GrainTest(unittest.TestCase):
    def test_a_wide_face_of_a_part_declared_along_its_short_side_runs_its_grain_up(self):
        nodes = model(("Post", (0, 3, 0), (24, 6, 6)))
        size = unwrap(nodes)
        look = paint.Look({"wood": brushes.wood((150, 104, 62))}, lambda n, s: "wood")
        along_x = island_of(paint.paint(nodes, size, look), nodes, "Post", "front")
        along_y = island_of(paint.paint(nodes, size, look, paint.Painting(axes={"Post": "y"})), nodes, "Post", "front")
        # Along the grain the wood changes slowly; across it, from one stroke to the next.
        self.assertLess(steps(along_x, 1, 0), steps(along_x, 0, 1))
        self.assertLess(steps(along_y, 0, 1), steps(along_y, 1, 0))

    def test_grain_runs_along_the_face_axis_matching_the_declared_box_axis(self):
        self.assertEqual("u", paint.grain_of("front", "x"))
        self.assertEqual("v", paint.grain_of("front", "y"))
        self.assertEqual("u", paint.grain_of("left", "z"))
        self.assertEqual("v", paint.grain_of("top", "z"))
        self.assertIsNone(paint.grain_of("top", "y"))


if __name__ == "__main__":
    unittest.main()
