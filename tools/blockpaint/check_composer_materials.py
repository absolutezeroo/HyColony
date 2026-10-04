"""Checks of the composer's single-axis parting of alike materials and of the value tools its operations share (spec
2026-10-04 blockpaint composer § 3 (8), § 7): python tools/blockpaint/check_composer_materials.py (no assets needed).
The demonstration crate (check_composer.flat_crate) and small hand-made models. An AssertionError names the case."""

import colorsys
import unittest
from types import SimpleNamespace

import composer_budgets
import composer_units
import composer_values
import semantics
from check_composer import cells, composed, flat_crate
from check_illustration import gap
from critique import lightness


def made(*units):
    """(pixels, Model) of units (part, family, level, colour, point[, texels]): four texels each unless said, in a
    row of their own, all at point in the world (units within composer_units.TOUCH of each other are neighbours)."""
    pixels, built = {}, []
    for row, (part, family, level, colour, point, *count) in enumerate(units):
        texels = dict.fromkeys(((i, row) for i in range(count[0] if count else 4)))
        pixels.update(dict.fromkeys(texels, (*colour, 255)))
        built.append(composer_units.Unit(part, family, level, texels, [point]))
    return pixels, composer_units.Model(built, {c: None for u in built for c in u.texels}, 1)


def parted(*units):
    """(pixels after composer_values.materials on made(units), its report)."""
    pixels, model = made(*units)
    return pixels, composer_values.materials(pixels, model, None)


def hue(pixel):
    return colorsys.rgb_to_hsv(*(c / 255 for c in pixel[:3]))[0]


class MaterialsTest(unittest.TestCase):
    def test_alike_materials_part_on_value_alone_when_they_differ_by_nothing(self):
        module, records, image = flat_crate((120, 90, 60), (120, 90, 60))
        self.assertFalse(semantics.materials_distinct(records, module.material).ok)
        out, report = composed(records, image, ("materials",))
        moved = [(*r[:4], out.crop((r[2][0], r[2][1], r[2][0] + r[2][2], r[2][1] + r[2][3]))) for r in records]
        self.assertTrue(semantics.materials_distinct(moved, module.material).ok)
        hues = [hue(out.getpixel(cells(records, p)[0])) for p in ("Body", "Band0")]
        self.assertAlmostEqual(hues[0], hues[1], delta=0.01, msg="the hue left alone")
        self.assertTrue(any("valeur" in line for line in report), report)

    def test_alike_materials_part_on_temperature_when_their_hues_differ_the_most(self):
        # The same value and saturation, six degrees of hue apart.
        _, records, image = flat_crate((150, 110, 60), (150, 100, 60))
        out, report = composed(records, image, ("materials",))
        body, band = cells(records, "Body"), cells(records, "Band0")

        def value(picture):
            return max(composer_units.mean_colour(picture.load(), body))

        self.assertAlmostEqual(value(out), value(image), delta=2, msg="the value (hsv) left alone")
        self.assertGreaterEqual(gap(composer_units.mean_colour(out.load(), body),
                                    composer_units.mean_colour(out.load(), band)), composer_values.DISTINCT_COLOUR)
        self.assertTrue(any("température" in line for line in report), report)

    def test_temperature_turns_the_hue_ahead_on_the_wheel_further_on_and_the_other_back(self):
        # 33 degrees (yellower) and 27 (redder): each turns further from the other, never past it.
        ahead, behind = (150, 110, 60), (150, 100, 60)
        pixels, report = parted(("A", "wood", 1, ahead, (0, 0, 0)), ("B", "leather", 1, behind, (0.5, 0, 0)))
        self.assertTrue(any("température" in line for line in report), report)
        self.assertGreater(hue(pixels[0, 0]), hue(ahead))
        self.assertLess(hue(pixels[0, 1]), hue(behind))

    def test_two_dark_greys_part_on_value_not_on_their_noisy_hues(self):
        module, records, image = flat_crate((60, 58, 62), (66, 62, 58))
        out, report = composed(records, image, ("materials",))
        moved = [(*r[:4], out.crop((r[2][0], r[2][1], r[2][0] + r[2][2], r[2][1] + r[2][3]))) for r in records]
        self.assertTrue(semantics.materials_distinct(moved, module.material).ok, report)
        self.assertFalse(any("température" in line for line in report), report)

    def test_greys_are_never_offered_the_temperature_axis(self):
        pixels, model = made(("A", "wood", 1, (60, 58, 62), (0, 0, 0)), ("B", "stone", 1, (66, 62, 58), (0.5, 0, 0)))
        axes = [axis for axis, _ in composer_values.axes_of(pixels, *model.units)]
        self.assertNotIn("température", axes)

    def test_each_try_starts_from_the_unit_s_own_colours_so_dark_ones_part_too(self):
        # Dark browns move a texel by less than one step at a time: only a try from their own colours gets there.
        dark = (40, 30, 20)
        pixels, report = parted(("A", "wood", 1, dark, (0, 0, 0)), ("B", "leather", 1, dark, (0.5, 0, 0)))
        self.assertTrue(any("séparés par la valeur" in line for line in report), report)
        tries = {composer_values.nudged((*dark, 255), "valeur", n / composer_values.SEPARATE_STEPS)
                 for n in range(1, composer_values.SEPARATE_STEPS + 1)}
        self.assertIn(pixels[0, 0], tries)

    def test_when_no_axis_suffices_the_widest_moves_at_the_most_allowed_and_says_so(self):
        darker, lighter = (10, 10, 10), (12, 12, 12)
        pixels, report = parted(("A", "wood", 1, darker, (0, 0, 0)), ("B", "stone", 1, lighter, (0.5, 0, 0)))
        self.assertTrue(any("aucun axe ne suffit" in line for line in report), report)
        self.assertEqual(composer_values.nudged((*darker, 255), "valeur", -1.0), pixels[0, 0])
        self.assertEqual(composer_values.nudged((*lighter, 255), "valeur", 1.0), pixels[0, 1])

    def test_a_small_unit_moves_alone_beside_a_far_larger_one(self):
        # A candle holder on a worktop: the worktop is the setting, only the candle holder parts from it.
        alike = (150, 110, 60)
        large = composer_values.LARGER * 4 + 1
        pixels, report = parted(("Worktop", "wood", 1, alike, (0, 0, 0), large),
                                ("Holder", "cuprous", 1, alike, (0.5, 0, 0)))
        self.assertEqual((*alike, 255), pixels[0, 0], "the worktop left alone")
        self.assertNotEqual((*alike, 255), pixels[0, 1])
        self.assertEqual(1, len(report), report)

    def test_a_unit_just_larger_times_its_partner_still_moves(self):
        # More than LARGER times is the setting; LARGER times exactly is a partner like any other.
        alike = (150, 110, 60)
        pixels, _ = parted(("Worktop", "wood", 1, alike, (0, 0, 0), composer_values.LARGER * 4),
                           ("Holder", "cuprous", 1, alike, (0.5, 0, 0)))
        self.assertNotEqual((*alike, 255), pixels[0, 0])
        self.assertNotEqual((*alike, 255), pixels[0, 1])

    def test_a_unit_too_close_to_two_neighbours_moves_once_and_the_second_moves_alone(self):
        alike = (120, 90, 60)
        a, b = ("A", "wood", 1, alike, (0, 0, 0)), ("B", "leather", 1, alike, (0.5, 0, 0))
        pair, _ = parted(a, b)
        trio, report = parted(a, b, ("C", "textile", 1, alike, (-0.5, 0, 0)))
        self.assertEqual(pair[0, 0], trio[0, 0], "A moved for B only")
        self.assertNotEqual((*alike, 255), trio[0, 2], "C moved alone")
        self.assertEqual(2, len(report), report)


class ValueToolsTest(unittest.TestCase):
    def test_contrast_moves_value_and_keeps_each_colour_s_saturation(self):
        # Orange rust on grey iron: more contrast makes the rust lighter, never a more saturated orange.
        rust, iron = (200, 120, 60, 255), (90, 90, 90, 255)
        pixels = {(0, 0): rust, (1, 0): iron}
        composer_units.contrast_unit(pixels, list(pixels), 1.5)
        self.assertGreater(lightness(pixels[0, 0]) - lightness(pixels[1, 0]), lightness(rust) - lightness(iron))
        saturation = colorsys.rgb_to_hsv(*(c / 255 for c in pixels[0, 0][:3]))[1]
        self.assertLessEqual(saturation, colorsys.rgb_to_hsv(*(c / 255 for c in rust[:3]))[1])

    def test_value_groups_that_would_narrow_the_model_are_undone_and_said(self):
        # A light background (dark group: may only darken) and a dark important part (light group: may only lighten)
        # would meet in the middle.
        pixels, model = made(("Back", "wood", 0, (200, 200, 200), (0, 0, 0)),
                             ("Jar", "ceramic", 2, (50, 50, 50), (9, 0, 0)))
        before = dict(pixels)
        report = composer_values.groups(pixels, model, SimpleNamespace(groups={}))
        self.assertEqual(before, pixels)
        self.assertTrue(any("annulés" in line for line in report), report)

    def test_an_accent_whole_from_afar_but_merging_with_its_surroundings_does_not_read(self):
        pixels, model = made(("Seal", "wax", 3, (120, 120, 120), (0, 0, 0)),
                             ("Box", "wood", 1, (126, 126, 126), (0.5, 0, 0)))
        # A 4 x 4 block of its own, away from the rows made gives.
        block = dict.fromkeys(((i, 4 + j) for i in range(4) for j in range(4)))
        pixels.update(dict.fromkeys(block, (120, 120, 120, 255)))
        seal = model.units[0]._replace(texels=block, accent=0)
        report = composer_budgets.distance(pixels, model._replace(units=[seal, model.units[1]]))
        self.assertTrue(any("Seal ne se lit plus" in line for line in report), report)


if __name__ == "__main__":
    unittest.main()
