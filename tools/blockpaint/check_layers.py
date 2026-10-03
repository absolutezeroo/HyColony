"""Checks of a face's layers, roles, effects and light (spec 2026-10-03 blockpaint surfaces): python
tools/blockpaint/check_layers.py (no assets needed). An AssertionError names the case."""

import colorsys
import unittest

from PIL import Image

import bake
import brushes
import effects
import layers
import marks
import paint
import roles
import shading
from check_surfaces import model

STEEL, PRIMER, BLUE = (120, 122, 128), (150, 60, 50), (60, 96, 150)


def front_island(substrate, coats=(), family="ferrous"):
    """An Island of a lone block's 8 x 8 front face, painted with substrate (a brush) under coats."""
    nodes = model(("Block", (0, 4, 0), (8, 8, 8)))
    _, contexts = bake.survey(nodes)
    name, side, u, v, w, h = next(r for r in paint.islands(nodes) if r[:2] == ("Block", "front"))
    texels = {(i, j): contexts[u + i, v + j] for i in range(w) for j in range(h)}
    return layers.Island(substrate(w, h, side), texels, effects.Material(substrate, family, coats, (), {}), side)


def flat(rgb):
    return lambda w, h, side: Image.new("RGBA", (w, h), (*rgb, 255))


def colours_of(image):
    return {image.getpixel((x, y)) for x in range(image.width) for y in range(image.height)}


class LayersTest(unittest.TestCase):
    def test_without_coats_the_substrate_shows_as_painted(self):
        island = front_island(brushes.wood((150, 104, 62)))
        self.assertEqual(island.substrate.tobytes(), layers.compose(island).tobytes())

    def test_a_covering_coat_hides_the_substrate_and_a_clear_one_lets_it_through(self):
        cover = layers.compose(front_island(flat(STEEL), (layers.Coat(BLUE, "cover"),)))
        self.assertEqual({(*BLUE, 255)}, colours_of(cover))
        clear = layers.compose(front_island(flat(STEEL), (layers.Coat(BLUE, "clear", opacity=0.5),)))
        self.assertEqual({(90, 109, 139, 255)}, colours_of(clear))

    def test_a_tint_takes_the_coat_s_hue_and_keeps_the_grain(self):
        island = front_island(brushes.wood((150, 104, 62)), (layers.Coat(BLUE, "tint"),))
        tinted = layers.compose(island)

        def lightness(p):
            return 0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2]

        pairs = [(island.substrate.getpixel((x, y)), tinted.getpixel((x, y))) for x in range(8) for y in range(8)]
        lightest = max(pairs, key=lambda p: lightness(p[0]))
        darkest = min(pairs, key=lambda p: lightness(p[0]))
        self.assertGreater(lightness(lightest[1]), lightness(darkest[1]))
        self.assertTrue(all(p[1][2] > p[1][0] for p in pairs), "not blue")

    def test_a_scratch_shows_the_primer_then_the_steel_then_digs_into_it(self):
        island = front_island(flat(STEEL), (layers.Coat(PRIMER, "cover"), layers.Coat(BLUE, "cover")))
        island.depth[0, 0], island.depth[1, 0], island.depth[2, 0], island.depth[3, 0] = 1.0, 2.0, 3.0, 0.5
        image = layers.compose(island)
        self.assertEqual((*PRIMER, 255), image.getpixel((0, 0)))
        self.assertEqual((*STEEL, 255), image.getpixel((1, 0)))
        self.assertLess(sum(image.getpixel((2, 0))[:3]), sum(STEEL))
        self.assertEqual((*BLUE, 255), image.getpixel((3, 0)))
        self.assertEqual([False, True, True, False], [island.bare("ferrous", i, 0) for i in range(4)])
        self.assertFalse(island.bare("wood", 1, 0))

    def test_a_coat_worn_to_a_tenth_lets_the_layer_below_through(self):
        island = front_island(flat(STEEL), (layers.Coat(BLUE, "cover"),))
        island.depth[0, 0], island.depth[1, 0] = 0.5, 0.9
        image = layers.compose(island)
        self.assertEqual((*BLUE, 255), image.getpixel((0, 0)))
        self.assertNotIn(image.getpixel((1, 0)), ((*BLUE, 255), (*STEEL, 255)))
        self.assertAlmostEqual(0.5, island.left(0, 0, 0))
        self.assertAlmostEqual(0.1, island.left(0, 1, 0))

    def test_deposits_lie_over_everything_and_a_later_scratch_takes_them_off(self):
        island = front_island(flat(STEEL), (layers.Coat(BLUE, "cover"),))
        island.deposit((90, 70, 50), {(0, 0): 1.0, (1, 0): 1.0})
        island.dig({(1, 0): 0.4})
        image = layers.compose(island)
        self.assertEqual((90, 70, 50, 255), image.getpixel((0, 0)))
        self.assertEqual((*BLUE, 255), image.getpixel((1, 0)))

    def test_the_visible_layer_of_each_texel(self):
        island = front_island(flat(STEEL), (layers.Coat(PRIMER, "cover"), layers.Coat(BLUE, "cover")))
        island.depth[0, 0], island.depth[1, 0] = 1.0, 2.0
        self.assertEqual([1, 0, None], [island.visible(i, 0) for i in (2, 0, 1)])

    def test_alpha_never_changes(self):
        def holed(w, h, side):
            image = Image.new("RGBA", (w, h), (*STEEL, 255))
            image.putpixel((0, 0), (0, 0, 0, 0))
            return image

        island = front_island(holed, (layers.Coat(BLUE, "cover"),))
        island.deposit((90, 70, 50), {(0, 0): 1.0})
        self.assertEqual(0, layers.compose(island).getpixel((0, 0))[3])

    def test_macro_variation_flows_smoothly_across_a_part_and_changes_its_value_hue_and_saturation(self):
        brown = (150, 104, 62)
        swelled = [layers.swell(brown, (x * 0.5, 4.0, 0.0), 3) for x in range(96)]
        self.assertTrue(all(max(abs(a[k] - b[k]) for k in range(3)) <= 2 for a, b in zip(swelled, swelled[1:])))
        hsvs = [colorsys.rgb_to_hsv(*(c / 255 for c in rgb)) for rgb in swelled]
        for k in range(3):
            self.assertGreater(max(h[k] for h in hsvs) - min(h[k] for h in hsvs), 0.005, "hsv"[k])


class LayerToolsTest(unittest.TestCase):
    def test_a_tint_on_the_visible_layer_changes_the_coat_where_it_shows_and_the_substrate_where_it_is_bare(self):
        island = front_island(flat(STEEL), (layers.Coat(BLUE, "cover"),))
        island.depth[1, 0] = 2.0
        island.tint([(0, 0), (1, 0)], (200, 0, 0), 1.0, layer="visible")
        image = layers.compose(island)
        self.assertEqual((200, 0, 0, 255), image.getpixel((0, 0)))
        self.assertEqual((200, 0, 0, 255), island.substrate.getpixel((1, 0)))
        self.assertEqual((*STEEL, 255), island.substrate.getpixel((0, 0)))

    def test_a_pressed_texel_shows_its_relief_through_a_covering_coat_and_under_the_deposits(self):
        island = front_island(flat(STEEL), (layers.Coat(BLUE, "cover"),))
        island.press([(0, 0), (1, 0)], 0.5)
        island.deposit((90, 70, 50), {(1, 0): 1.0})
        image = layers.compose(island)
        self.assertEqual((30, 48, 75, 255), image.getpixel((0, 0)))
        self.assertEqual((90, 70, 50, 255), image.getpixel((1, 0)))

    def test_a_dent_reads_as_a_hollow_its_upper_left_wall_in_shadow_and_its_lower_right_lip_lit(self):
        island = front_island(flat(STEEL), (layers.Coat(BLUE, "cover"),))
        marks.MARKS["dent"].act(island, {(i, j): 1.0 for i in range(2, 6) for j in range(2, 6)})
        image = layers.compose(island)

        def value(i, j):
            return sum(image.getpixel((i, j))[:3])

        self.assertLess(value(2, 2), value(3, 3))
        self.assertLess(value(3, 3), value(0, 0))
        self.assertGreater(value(5, 5), value(0, 0))

    def test_two_tints_on_a_coat_add_up_as_on_the_substrate(self):
        painted = front_island(flat(STEEL), (layers.Coat(STEEL, "cover"),))
        bare = front_island(flat(STEEL))
        for island in (painted, bare):
            island.tint([(0, 0)], (40, 40, 40), 0.5, layer="visible")
            island.tint([(0, 0)], (0, 0, 0), 0.5, layer="visible")
        self.assertEqual(layers.compose(bare).getpixel((0, 0)), layers.compose(painted).getpixel((0, 0)))
        self.assertEqual((40, 40, 42, 255), layers.compose(bare).getpixel((0, 0)))

    def test_a_patch_s_seam_shows_under_a_covering_coat(self):
        island = front_island(flat(STEEL), (layers.Coat(BLUE, "cover"),))
        marks.MARKS["patch"].act(island, {(i, j): 1.0 for i in range(2, 6) for j in range(2, 6)})
        image = layers.compose(island)
        self.assertLess(sum(image.getpixel((2, 2))[:3]), sum(image.getpixel((0, 0))[:3]))
        self.assertNotEqual(image.getpixel((3, 3)), image.getpixel((0, 0)))

    def test_the_hytale_light_reaches_lit(self):
        image = flat((150, 104, 62))(1, 1, "front")
        legacy = bake.lit(image.copy(), {(0, 0): 0.6}).getpixel((0, 0))
        hytale = bake.lit(image.copy(), {(0, 0): 0.6}, "hytale").getpixel((0, 0))
        self.assertNotEqual(legacy, hytale)

    def test_brushes_carry_their_family(self):
        self.assertEqual("wood", brushes.wood((150, 104, 62)).family)
        self.assertEqual("ferrous", brushes.metal((120, 120, 120)).family)
        self.assertEqual(brushes.wood((150, 104, 62))(4, 4, "front").tobytes(),
                         brushes.wood.__wrapped__((150, 104, 62))(4, 4, "front").tobytes())


class RolesTest(unittest.TestCase):
    def test_a_grip_is_touched_a_lot_without_being_told(self):
        self.assertEqual(1.0, roles.declared("Grip", {"Grip": "grip"}, {}, ()).contact)

    def test_usage_corrects_what_the_role_gives(self):
        maps_of = roles.declared("Grip", {"Grip": "grip"}, {"Grip": roles.contact(0.4)}, ())
        self.assertEqual(0.4, maps_of.contact)

    def test_a_part_without_role_usage_or_focus_has_nothing_declared(self):
        self.assertEqual(roles.Declared(0.0, 0.0, 0.0, 0.0, 0.0), roles.declared("Plank", {}, {}, ()))

    def test_focus_names_a_part(self):
        self.assertEqual(1.0, roles.declared("Boss", {}, {}, ("Boss",)).focus)

    def test_an_unknown_role_is_refused_with_the_known_ones(self):
        with self.assertRaises(SystemExit) as refused:
            roles.declared("Grip", {"Grip": "gripp"}, {}, ())
        self.assertIn("grip", str(refused.exception))

    def test_every_role_of_the_spec_is_known(self):
        for role in ("blade", "handle", "grip", "guard", "boot", "sole", "face", "hair", "armor_plate", "cloth_panel",
                     "strap", "shield_rim", "shield_face", "roof", "wall", "floor", "step", "wheel", "axle", "door",
                     "hinge", "lid", "leg", "rope", "pot", "rim"):
            self.assertIn(role, roles.ROLES)


def hsv(pixel):
    return colorsys.rgb_to_hsv(*(c / 255 for c in pixel[:3]))


class LightTest(unittest.TestCase):
    def test_the_legacy_light_is_unchanged(self):
        # 150 * 0.85 * (1 - 0.10 * 0.15), 120 * 0.85 * (1 - 0.04 * 0.15), 110 * 0.85 * (1 + 0.08 * 0.15).
        self.assertEqual((126, 101, 95, 255), shading.graded((150, 120, 110, 255), 0.85))
        self.assertEqual(shading.graded((150, 120, 110, 255), 0.85),
                         shading.graded((150, 120, 110, 255), 0.85, "legacy"))

    def test_in_hytale_light_a_brown_turns_red_in_shadow_and_a_green_turns_blue(self):
        brown, green = (150, 104, 62, 255), (80, 140, 70, 255)
        self.assertLess(hsv(shading.graded(brown, 0.6, "hytale"))[0], hsv(shading.graded(brown, 1.0, "hytale"))[0])
        self.assertGreater(hsv(shading.graded(green, 0.6, "hytale"))[0], hsv(shading.graded(green, 1.0, "hytale"))[0])

    def test_in_hytale_light_a_lit_blue_keeps_its_hue_rather_than_turning_green(self):
        blue = (64, 104, 132, 255)
        self.assertAlmostEqual(hsv(blue)[0], hsv(shading.graded(blue, 1.3, "hytale"))[0], delta=0.01)

    def test_in_hytale_light_the_saturation_stays_and_the_values_stay_in_bounds(self):
        brown = (150, 104, 62, 255)
        self.assertAlmostEqual(hsv(brown)[1], hsv(shading.graded(brown, 0.7, "hytale"))[1], delta=0.03)
        for value in (0.0, 0.3, 1.0, 1.6):
            graded = shading.graded((250, 5, 128, 255), value, "hytale")
            self.assertTrue(all(shading.LOW <= c <= shading.HIGH for c in graded[:3]))


if __name__ == "__main__":
    unittest.main()
