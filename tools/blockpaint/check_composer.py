"""Checks of the value and focus composer (spec 2026-10-04 blockpaint composer): python
tools/blockpaint/check_composer.py (no assets needed). The demonstration crate (check_effects), painted in layers,
composed one operation at a time. An AssertionError names the case."""

import colorsys
import contextlib
import io
import unittest
from unittest import mock

from PIL import Image

import catalog
import composer
import composer_budgets
import composer_units
import composer_values
import conditions
import effects
import illustration
import semantics
from check_effects import DECLARED, crate_model, crate_module
from check_illustration import painted, unlit
from critique import lightness


def composed(records, image, steps, importance=None, focus=()):
    """(image composed by steps only, its report)."""
    report = []

    def level(part):
        return illustration.importance_of(part, importance or {}, focus)

    out = composer.compose(image.copy(), records, composer.Composer({}, report, steps), level)
    return out, report


def cells(records, part):
    """[(texture x, y)] of every texel of part."""
    return [(r[2][0] + i, r[2][1] + j) for r in records if r[0] == part for i, j in r[3].texels]


def light_of(image, where):
    return sum(lightness(image.getpixel(c)) for c in where) / len(where)


def spread(image, where):
    mean = light_of(image, where)
    return sum(abs(lightness(image.getpixel(c)) - mean) for c in where) / len(where)


def saturation(image, where):
    return sum(colorsys.rgb_to_hsv(*(v / 255 for v in image.getpixel(c)[:3]))[1] for c in where) / len(where)


def flat_crate(wood, bands, band_family="leather"):
    """(records, unlit texture) of a pristine crate painted in two flat colours: its wood, its bands'."""
    def flat(rgb):
        return lambda w, h, side: Image.new("RGBA", (w, h), (*rgb, 255))

    nodes = crate_model()
    module = crate_module(**dict(DECLARED, CONDITION=conditions.PRISTINE, HISTORY=()))
    module.tiles = lambda assets: {"wood": effects.material(flat(wood), family="wood"),
                                   "iron": effects.material(flat(bands), family=band_family)}
    records = semantics.records_of(module, nodes)
    return module, records, unlit(records, catalog.texture_size(nodes))


class ValuesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.records, cls.image = painted()

    def test_value_groups_spread_the_model_a_calm_part_darker_and_an_important_one_lighter(self):
        out, report = composed(self.records, self.image, ("groups",), {"Body": 0, "Lid": 2})
        self.assertLess(light_of(out, cells(self.records, "Body")), light_of(self.image, cells(self.records, "Body")))
        self.assertGreater(light_of(out, cells(self.records, "Lid")), light_of(self.image, cells(self.records, "Lid")))
        self.assertTrue(any("groupes de valeur" in line for line in report), report)

    def test_normal_units_spread_away_from_the_middle_so_the_model_widens(self):
        texels = composer_units.model_of(self.image.load(), self.records, lambda part: 1).texels

        def width(image):
            values = sorted(lightness(image.getpixel(c)) for c in texels)
            return values[int(0.9 * (len(values) - 1))] - values[int(0.1 * (len(values) - 1))]

        out, _ = composed(self.records, self.image, ("groups",))
        self.assertGreater(width(out), width(self.image) * 1.1)

    def test_a_unit_routed_by_several_neighbours_moves_once(self):
        _, records, image = flat_crate((120, 90, 60), (110, 84, 56), "ferrous")
        out, report = composed(records, image, ("routing",), {"Lid": 2})
        lid = cells(records, "Lid")
        self.assertGreater(len(report), 1, "the lid merges with several neighbours")
        self.assertAlmostEqual(light_of(out, lid), light_of(image, lid) * (1 + composer_values.ROUTE), delta=1.5)

    def test_paper_is_at_least_light_and_a_named_group_wins(self):
        model = composer_units.model_of(self.image.load(), self.records, lambda part: 1)
        unit = model.units[0]
        self.assertEqual("light", composer_values.group_of(unit._replace(family="paper"), {}))
        self.assertEqual("accent", composer_values.group_of(unit._replace(family="paper", level=3), {}))
        self.assertEqual("deep", composer_values.group_of(unit, {unit.part: "deep"}))
        self.assertIsNone(composer_values.group_of(unit, {}), "a normal unit follows its own rank")

    def test_a_light_group_never_darkens_and_a_dark_one_never_lightens(self):
        self.assertEqual(1.0, composer_values.factor("light", 237, 130), "a white sheet already lighter stays white")
        self.assertEqual(1.0, composer_values.factor("dark", 40, 90))
        self.assertGreater(composer_values.factor("light", 90, 200), 1.0)
        self.assertLess(composer_values.factor(None, 130, 90), 1.0)

    def test_a_group_never_turns_a_material_into_another(self):
        # The large quarry's light ground stone, in the background (dark group) of a dark model: darkened by a quarter
        # at most, never to coal; a dark wood made an accent lightened by a third at most, never to pale pine.
        self.assertGreaterEqual(composer_values.factor("dark", 150, 30), 0.75)
        self.assertLessEqual(composer_values.factor("accent", 75, 230), 4 / 3 + 1e-9)

    def test_a_large_white_sheet_on_a_dark_frame_stays_white_and_the_model_never_narrows(self):
        # Two masses: dark walnut bands and a large white body, as a bed's frame and its mattress.
        _, records, image = flat_crate((236, 232, 224), (60, 44, 32), "ferrous")
        out, report = composed(records, image, ("groups",), {"Body": 2})
        body = cells(records, "Body")
        self.assertGreaterEqual(light_of(out, body), light_of(image, body) - 1)
        every = list(composer_units.model_of(image.load(), records, lambda part: 1).texels)
        self.assertGreaterEqual(composer_values.width(out.load(), every), composer_values.width(image.load(), every))
        self.assertTrue(report)

    def test_the_background_steps_back_and_an_accent_steps_forward(self):
        out, report = composed(self.records, self.image, ("background", "focal"), {"Body": 0, "Lid": 3})
        body, lid = cells(self.records, "Body"), cells(self.records, "Lid")
        self.assertLess(spread(out, body), spread(self.image, body))

        def mean_saturation(picture):
            # The mean colour's: a lower contrast keeps it, only the saturation step lowers it.
            return colorsys.rgb_to_hsv(*(v / 255 for v in composer_units.mean_colour(picture.load(), body)))[1]

        self.assertLess(mean_saturation(out), mean_saturation(self.image) * 0.9)
        self.assertGreater(spread(out, lid), spread(self.image, lid))
        self.assertGreater(saturation(out, lid), saturation(self.image, lid))
        self.assertTrue(any("fond" in line for line in report) and any("en avant" in line for line in report))

    def test_two_merging_units_part_the_more_important_lighter(self):
        _, records, image = flat_crate((120, 90, 60), (90, 90, 90), "ferrous")
        out, report = composed(records, image, ("routing",), {"Lid": 2})
        self.assertGreater(light_of(out, cells(records, "Lid")), light_of(image, cells(records, "Lid")))
        self.assertLess(light_of(out, cells(records, "Body")), light_of(image, cells(records, "Body")))
        self.assertTrue(report)


class BudgetsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.records, cls.image = painted()

    def model(self, importance):
        return composer_units.model_of(self.image.load(), self.records,
                                       lambda part: illustration.importance_of(part, importance, ()))

    def test_a_small_model_keeps_two_accents_and_touching_units_of_one_family_are_one(self):
        model = self.model({"Lid": 3, "Body": 2, "Band0": 2, "Band1": 2})
        self.assertEqual("small", composer_budgets.size_of(model))
        kept, lines = composer_budgets.accents(model)
        levels = {u.part: u.level for u in kept.units}
        self.assertEqual((3, 2), (levels["Lid"], levels["Body"]), "the lid and the body (wood, touching) are one")
        self.assertEqual(1, sorted((levels["Band0"], levels["Band1"]))[0], "past the budget, a band falls back")
        self.assertTrue(any("budget dépassé" in line for line in lines), lines)

    def test_a_part_touching_an_accent_of_its_family_joins_it_rather_than_taking_a_place_of_its_own(self):
        # Small: two accents. The foot (wood) touches the body (wood): one accent, kept whole; the bands are two more.
        kept, _ = composer_budgets.accents(self.model({"Foot0": 2, "Body": 2, "Band0": 2, "Band1": 2}))
        levels = {u.part: u.level for u in kept.units}
        self.assertEqual((2, 2), (levels["Foot0"], levels["Body"]))
        self.assertEqual([1, 2], sorted((levels["Band0"], levels["Band1"])))

    def test_the_deepest_darks_are_few_the_most_enclosed_and_never_black(self):
        out, report = composed(self.records, self.image, ("darks",))
        texels = composer_units.model_of(self.image.load(), self.records, lambda part: 1).texels
        darker = [c for c in texels if lightness(out.getpixel(c)) < lightness(self.image.getpixel(c))]
        self.assertTrue(darker)
        self.assertLessEqual(len(darker), composer_budgets.DARK_SHARE * len(texels) + 1)
        left = [t.occlusion for c, t in texels.items() if c not in darker and t.occlusion >= 0.45]
        self.assertGreaterEqual(min(texels[c].occlusion for c in darker), max(left, default=0))
        self.assertTrue(report)

    def test_a_dark_model_s_deepest_darks_stop_at_the_floor(self):
        _, records, image = flat_crate((34, 28, 24), (34, 28, 24), "wood")
        out, _ = composed(records, image, ("darks",))
        texels = composer_units.model_of(image.load(), records, lambda part: 1).texels
        changed = [c for c in texels if out.getpixel(c) != image.getpixel(c)]
        self.assertTrue(changed)
        for cell in changed:
            self.assertGreaterEqual(lightness(out.getpixel(cell)), composer_budgets.FLOOR - 1)

    def lit_edges(self, part):
        return [(r[2][0] + i, r[2][1] + j) for r in self.records if r[0] == part
                for (i, j), t in r[3].texels.items() if illustration.lit_edge(t)]

    def test_primary_lights_go_to_an_accent_secondary_to_an_important_part_and_none_to_the_background(self):
        out, report = composed(self.records, self.image, ("lights",), {"Lid": 3, "Body": 0})
        lid, body = self.lit_edges("Lid"), self.lit_edges("Body")
        self.assertGreater(light_of(out, lid), light_of(self.image, lid))
        self.assertFalse(any(lightness(out.getpixel(c)) > lightness(self.image.getpixel(c)) for c in body))
        self.assertTrue(any(lightness(out.getpixel(c)) < lightness(self.image.getpixel(c)) for c in body),
                        "the background's edge lights pulled back")
        secondary, _ = composed(self.records, self.image, ("lights",), {"Lid": 2})
        self.assertGreater(light_of(out, lid), light_of(secondary, lid) + 2)
        self.assertTrue(report)

    def test_the_distance_check_says_whether_each_accent_reads(self):
        _, report = composed(self.records, self.image, ("distance",), {"Lid": 3})
        self.assertTrue(any("Lid" in line and "25 %" in line for line in report), report)
        # A foot is too small to fill a 4 x 4 block of the texture: it cannot read from afar.
        _, report = composed(self.records, self.image, ("distance",), {"Foot0": 3})
        self.assertTrue(any("Foot0" in line and "ne se lit plus" in line for line in report), report)


class ComposerTest(unittest.TestCase):
    def test_the_composer_changes_no_layer(self):
        records, image = painted()

        def layers_of(island):
            return (island.substrate.tobytes(), island.base.tobytes(), repr(sorted(island.zones)),
                    dict(island.depth), repr(island.deposits))

        snapshot = [layers_of(r[3]) for r in records]
        composed(records, image, composer.STEPS, {"Lid": 3, "Body": 0})
        self.assertEqual(snapshot, [layers_of(r[3]) for r in records])

    def test_without_a_composer_catalog_paints_as_before_and_with_one_prints_its_report(self):
        nodes = crate_model()
        plain = catalog.model_texture(crate_module(**DECLARED), nodes, None)
        module = crate_module(**dict(DECLARED, IMPORTANCE={"Lid": 3}, COMPOSER=composer.Composer()))
        printed = io.StringIO()
        with contextlib.redirect_stdout(printed):
            drawn = catalog.model_texture(module, nodes, None)
        self.assertNotEqual(plain.tobytes(), drawn.tobytes())
        self.assertIn("budget d'accents", printed.getvalue())

    def test_the_illustration_pass_runs_before_the_composer(self):
        order = []
        with mock.patch("paint.illustrate", lambda image, *rest: order.append("illustrate") or image), \
                mock.patch("paint.compose", lambda image, *rest: order.append("compose") or image):
            module = crate_module(**dict(DECLARED, ILLUSTRATION=illustration.Illustration(),
                                         COMPOSER=composer.Composer()))
            catalog.model_texture(module, crate_model(), None)
        self.assertEqual(["illustrate", "compose"], order)

    def test_an_importance_or_a_group_naming_no_part_or_out_of_its_range_is_refused(self):
        for declared, named in (({"IMPORTANCE": {"Lidd": 3}}, "Lidd"), ({"IMPORTANCE": {"Lid": 4}}, "Lid 4"),
                                ({"COMPOSER": composer.Composer({"Bodyy": "dark"})}, "Bodyy"),
                                ({"COMPOSER": composer.Composer({"Body": "darkest"})}, "darkest")):
            module = crate_module(**dict(DECLARED, **dict({"COMPOSER": composer.Composer()}, **declared)))
            with self.assertRaisesRegex(SystemExit, named):
                catalog.model_texture(module, crate_model(), None)

    def test_a_composer_without_a_layered_condition_is_refused(self):
        with self.assertRaises(SystemExit):
            catalog.model_texture(crate_module(COMPOSER=composer.Composer()), crate_model(), None)


if __name__ == "__main__":
    unittest.main()
