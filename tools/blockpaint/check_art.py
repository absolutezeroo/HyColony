"""Checks of the art pass (spec 2026-10-03 blockpaint surfaces, art): python tools/blockpaint/check_art.py (no assets
needed). An AssertionError names the case."""

import unittest

from PIL import Image

import art
import bake
import brushes
import conditions
import critique
import effects
import paint
import roles
import surface
import weathering
from check_layers import STEEL, flat, front_island
from check_surfaces import model
from models import unwrap


def white_noise(w, h, rgb=(150, 104, 62), amount=0.5):
    image = Image.new("RGBA", (w, h))
    for x in range(w):
        for y in range(h):
            image.putpixel((x, y), brushes.coloured(rgb, 1 + amount * brushes.jitter(x * 31 + y * 17, 5)))
    return image


def within(image, kind, detail):
    m = critique.measure(image)
    target = art.TARGETS[detail][kind]
    return m.step <= target.step and m.jumps <= target.jumps


class CalmTest(unittest.TestCase):
    def test_a_noisy_grain_comes_out_within_its_budget(self):
        # ±15 %: as noisy as our huts' wood (a mean step about 10); far stronger jumps would read as drawn lines, which
        # the calm keeps.
        noisy = white_noise(16, 16, amount=0.15)
        self.assertFalse(within(noisy, "warm", "medium"))
        self.assertTrue(within(art.calm(noisy, "medium"), "warm", "medium"))

    def test_a_calm_island_comes_out_as_it_went_in(self):
        # A soft gradient, two levels a texel: well within every budget.
        calm = Image.new("RGBA", (16, 16))
        for x in range(16):
            for y in range(16):
                calm.putpixel((x, y), (120 + 2 * x, 90 + y, 60, 255))
        self.assertTrue(within(calm, critique.measure(calm).kind, "low"))
        self.assertEqual(calm.tobytes(), art.calm(calm, "low").tobytes())

    def test_the_seams_between_planks_stay_darker_than_the_planks(self):
        wood = brushes.wood((150, 104, 62), plank=4)(16, 16, "top")
        calmed = art.calm(wood, "low")

        def row(image, y):
            return sum(sum(image.getpixel((x, y))[:3]) for x in range(16))

        # brushes.wood darkens the last row of each 4 texel plank: rows 3, 7 and 11.
        for seam in (3, 7, 11):
            self.assertLess(row(calmed, seam), row(calmed, seam - 1))
            self.assertLess(row(calmed, seam), row(calmed, seam + 1))

    def test_structure_keeps_its_contrast_while_its_grain_calms(self):
        # A crate's slats: three texel planks with a dark seam (structure), and a soft grain within (noise).
        crate = brushes.wood((168, 126, 80), plank=3)(16, 16, "front")
        calmed = art.calm(crate, "low")

        def row(image, y):
            return sum(sum(image.getpixel((x, y))[:3]) for x in range(16)) / 16

        # At the strongest calm (low detail), each seam keeps three quarters of its contrast at least.
        for seam in (2, 5, 8, 11):
            before = row(crate, seam - 1) - row(crate, seam)
            after = row(calmed, seam - 1) - row(calmed, seam)
            self.assertGreater(after, 0.75 * before, seam)

    def test_a_lone_speck_is_wiped_out_and_a_thin_line_kept(self):
        image = Image.new("RGBA", (12, 12), (150, 104, 62, 255))
        image.putpixel((3, 3), (240, 220, 200, 255))
        for x in range(12):
            image.putpixel((x, 8), (70, 48, 30, 255))
        groups = art.alike(image)
        self.assertTrue(groups[3, 3][1])
        self.assertFalse(groups[5, 8][1])
        calmed = art.averaged(image, groups)
        self.assertEqual((150, 104, 62, 255), calmed.getpixel((3, 3)))
        self.assertEqual((70, 48, 30, 255), calmed.getpixel((5, 8)))

    def test_layered_paint_calms_its_substrate_and_its_focal_parts_least(self):
        noisy = (lambda w, h, side: white_noise(w, h, amount=0.15))
        nodes = model(("Block", (0, 8, 0), (16, 16, 16)), ("Other", (40, 8, 0), (16, 16, 16)))
        size = unwrap(nodes)
        _, contexts = bake.survey(nodes, grounded=True, wear=0.0, grime=0.0)
        look = paint.Look({"wood": effects.material(noisy, family="wood")}, lambda n, s: "wood")
        layered = surface.Surface(contexts, conditions.PRISTINE, focus=("Block",))
        image = paint.paint(nodes, size, look, paint.Painting(surface=layered))

        def step(part):
            u, v, w, h = next(r for r in paint.islands(nodes) if r[:2] == (part, "front"))[2:]
            return critique.measure(image.crop((u, v, u + w, v + h))).step

        self.assertLess(step("Block"), critique.measure(white_noise(16, 16, amount=0.15)).step)
        self.assertLess(step("Other"), step("Block"))

    def test_low_detail_calms_more_than_high(self):
        # A grain both budgets can reach, so each stops at its own.
        noise = white_noise(16, 16, amount=0.15)
        low, high = critique.measure(art.calm(noise, "low")), critique.measure(art.calm(noise, "high"))
        self.assertLess(low.step, high.step)

    def test_alpha_and_size_never_change_and_small_islands_are_left_alone(self):
        noise = white_noise(16, 16)
        calmed = art.calm(noise, "medium")
        self.assertEqual(noise.size, calmed.size)
        self.assertEqual({255}, {calmed.getpixel((x, y))[3] for x in range(16) for y in range(16)})
        strip = white_noise(3, 16)
        self.assertEqual(strip.tobytes(), art.calm(strip, "medium").tobytes())

    def test_focus_raises_the_detail_of_its_parts_and_lowers_the_others(self):
        self.assertEqual("medium", art.detail_of("Plank", art.Art(), ()))
        self.assertEqual("high", art.detail_of("Boss", art.Art(), ("Boss",)))
        self.assertEqual("low", art.detail_of("Plank", art.Art(), ("Boss",)))
        self.assertEqual("high", art.detail_of("Boss", art.Art("high"), ("Boss",)))


class RestAndCountTest(unittest.TestCase):
    def test_away_from_borders_contact_and_focus_an_effect_reaches_less(self):
        everywhere = effects.Effect("all", "deposit", lambda t, d, isl, i, j: 0.8, lambda isl, a: None)
        island = front_island(flat(STEEL))
        busy = effects.reached(everywhere, island, 0.5, effects.Pass(roles.Declared(), 1))
        resting = effects.reached(everywhere, island, 0.5, effects.Pass(roles.Declared(), 1, rest=True))
        self.assertLess(len(resting), len(busy))
        touched = effects.reached(everywhere, island, 0.5, effects.Pass(roles.Declared(contact=1.0), 1, rest=True))
        self.assertEqual(len(busy), len(touched))

    def test_an_event_marks_where_it_happened_rest_zones_or_not(self):
        island = front_island(flat(STEEL))
        mark = effects.Effect("mark", "recent", lambda t, d, isl, i, j: 0.8, lambda isl, a: None)
        anywhere = effects.reached(mark, island, 0.5, effects.Pass(roles.Declared(), 1))
        self.assertEqual(anywhere, effects.reached(mark, island, 0.5, effects.Pass(roles.Declared(), 1, rest=True)))

    def test_an_island_keeps_only_its_most_useful_effects(self):
        names = ("edge_wear", "chips", "dirt", "grime", "dust")
        uses = [(weathering.EFFECTS[n], d) for n, d in zip(names, (0.5, 0.4, 0.1, 0.3, 0.2))]
        wood = effects.material(flat(STEEL), family="wood")
        kept = [e.name for e, _ in surface.most_useful(uses, wood, "medium")]
        self.assertEqual(["edge_wear", "chips", "grime"], kept)
        self.assertEqual(2, len(surface.most_useful(uses, wood, "low")))

    def test_an_effect_the_material_forbids_takes_no_place(self):
        uses = [(weathering.EFFECTS["rust"], 0.9), (weathering.EFFECTS["edge_wear"], 0.5),
                (weathering.EFFECTS["chips"], 0.4)]
        stainless = effects.material(flat(STEEL), family="ferrous", weights={"rust": 0.0})
        kept = [e.name for e, _ in surface.most_useful(uses, stainless, "low")]
        self.assertEqual(["edge_wear", "chips"], kept)

    def test_a_deposit_over_a_more_useful_one_is_not_laid(self):
        laid = []

        def lay(name):
            return lambda isl, amounts: laid.append(name)

        first = effects.Effect("first", "deposit", lambda *a: 1.0, lay("first"))
        second = effects.Effect("second", "deposit", lambda *a: 1.0, lay("second"))
        effects.run(front_island(flat(STEEL)), [(first, 0.8), (second, 0.8)],
                    effects.Pass(roles.Declared(), 1, fight=True))
        self.assertEqual(["first"], laid)
        laid.clear()
        effects.run(front_island(flat(STEEL)), [(first, 0.8), (second, 0.8)], effects.Pass(roles.Declared(), 1))
        self.assertEqual(["first", "second"], laid)

    def test_a_zone_keeps_patches_of_four_touching_texels_and_drops_smaller_ones(self):
        self.assertEqual(set(), effects.patches({(0, 0), (1, 0), (2, 0)}))
        four = {(0, 0), (1, 0), (2, 0), (3, 1)}
        self.assertEqual(four, effects.patches(four | {(9, 9)}))

    def test_the_island_keeps_each_effect_s_zone(self):
        island = front_island(flat(STEEL))
        everywhere = effects.Effect("all", "deposit", lambda *a: 1.0, lambda isl, a: None)
        effects.run(island, [(everywhere, 0.8)], effects.Pass(roles.Declared(), 1))
        self.assertTrue(island.zones["all"])

    def test_an_effect_that_comes_twice_keeps_both_zones(self):
        island = front_island(flat(STEEL))
        left = effects.Effect("mark", "recent", lambda t, d, isl, i, j: 1.0 if i < 3 else 0.0, lambda isl, a: None)
        right = left._replace(mask=lambda t, d, isl, i, j: 1.0 if i > 4 else 0.0)
        effects.run(island, [(left, 1.0), (right, 1.0)], effects.Pass(roles.Declared(), 1))
        self.assertTrue(any(i < 3 for i, _ in island.zones["mark"]))
        self.assertTrue(any(i > 4 for i, _ in island.zones["mark"]))


if __name__ == "__main__":
    unittest.main()
