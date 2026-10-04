"""Checks of the art pass (spec 2026-10-03 blockpaint surfaces, art): python tools/blockpaint/check_art.py (no assets
needed). An AssertionError names the case."""

import unittest
from types import SimpleNamespace

from PIL import Image

import art
import bake
import brushes
import catalog
import conditions
import critique
import effects
import layers
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

    def test_a_drawing_is_never_calmed(self):
        noisy = brushes.drawing(lambda w, h, side: white_noise(w, h, amount=0.15))
        nodes = model(("Block", (0, 8, 0), (16, 16, 16)))
        size = unwrap(nodes)
        _, contexts = bake.survey(nodes, grounded=True, wear=0.0, grime=0.0)
        look = paint.Look({"wood": effects.material(noisy, family="wood")}, lambda n, s: "wood")
        image = paint.paint(nodes, size, look, paint.Painting(surface=surface.Surface(contexts, conditions.PRISTINE)))
        u, v, w, h = next(r for r in paint.islands(nodes) if r[:2] == ("Block", "front"))[2:]
        self.assertGreaterEqual(critique.measure(image.crop((u, v, u + w, v + h))).step,
                                0.9 * critique.measure(white_noise(16, 16, amount=0.15)).step)

    def test_a_layered_picture_of_a_catalog_module_is_a_drawing(self):
        nodes = model(("Block", (0, 8, 0), (16, 16, 16)))
        noisy = (lambda w, h, side: white_noise(w, h, amount=0.15))
        module = SimpleNamespace(MODEL="Blocks/Test/Block", PICTURES=frozenset({"sign"}),
                                 CONDITION=conditions.PRISTINE, material=lambda n, s: "sign",
                                 tiles=lambda a: {"sign": effects.material(noisy, family="paper")})
        image = catalog.model_texture(module, nodes, None)
        u, v, w, h = next(r for r in paint.islands(nodes) if r[:2] == ("Block", "front"))[2:]
        self.assertGreaterEqual(critique.measure(image.crop((u, v, u + w, v + h))).step,
                                0.9 * critique.measure(white_noise(16, 16, amount=0.15)).step)

    def test_an_image_tile_laid_as_a_brush_follows_the_face_and_a_picture_stays_as_drawn(self):
        tile = white_noise(32, 32)
        with brushes.seeded(1234):
            drawn = paint.tiled(tile, picture=True)(8, 8, "front")
            self.assertEqual(tile.crop((0, 0, 8, 8)).tobytes(), drawn.tobytes())
            self.assertNotEqual(tile.crop((0, 0, 8, 8)).tobytes(), paint.tiled(tile)(8, 8, "front").tobytes())
        self.assertTrue(paint.tiled(tile, picture=True).drawn)
        self.assertFalse(getattr(paint.tiled(tile), "drawn", False))

    def test_a_drawing_leaves_the_brush_it_draws_with_as_it_was(self):
        shared = brushes.wood((150, 104, 62))
        drawn = brushes.drawing(shared)
        self.assertTrue(drawn.drawn)
        self.assertEqual("wood", drawn.family)
        self.assertFalse(getattr(shared, "drawn", False), "another material sharing the brush stays a substrate")
        self.assertEqual(shared(8, 8, "front").tobytes(), drawn(8, 8, "front").tobytes())

    def test_a_drawing_takes_what_settles_on_it_but_no_wear(self):
        drawn = brushes.drawing(lambda w, h, side: Image.new("RGBA", (w, h), (232, 222, 196, 255)))
        nodes = model(("Block", (0, 8, 0), (16, 16, 16)))
        size = unwrap(nodes)
        _, contexts = bake.survey(nodes, grounded=True, wear=0.0, grime=0.0)
        record = []
        look = paint.Look({"dial": effects.material(drawn)}, lambda n, s: "dial")
        # Only wear, chips and dust planned: all three fit the island's visible count, so only the drawing's rule
        # can leave wear and chips out.
        catalogue = {name: weathering.EFFECTS[name] for name in ("edge_wear", "chips", "dust")}
        layered = surface.Surface(contexts, conditions.NEGLECTED, catalogue=catalogue, record=record)
        paint.paint(nodes, size, look, paint.Painting(surface=layered))
        zones = {name for _, _, _, island, _ in record for name, zone in island.zones.items() if zone}
        self.assertFalse(zones & {"edge_wear", "chips"}, zones)
        self.assertIn("dust", zones)

    def test_a_living_plant_or_a_liquid_takes_what_settles_on_it_but_no_wear(self):
        nodes = model(("Block", (0, 8, 0), (16, 16, 16)))
        size = unwrap(nodes)
        _, contexts = bake.survey(nodes, grounded=True, wear=0.0, grime=0.0)
        catalogue = {name: weathering.EFFECTS[name] for name in ("edge_wear", "chips", "dust")}
        for family in ("plant", "liquid"):
            record = []
            look = paint.Look({"leaf": effects.material(brushes.cloth((86, 148, 58)), family)}, lambda n, s: "leaf")
            layered = surface.Surface(contexts, conditions.NEGLECTED, catalogue=catalogue, record=record)
            paint.paint(nodes, size, look, paint.Painting(surface=layered))
            zones = {name for _, _, _, island, _ in record for name, zone in island.zones.items() if zone}
            self.assertFalse(zones & {"edge_wear", "chips"}, (family, zones))
            self.assertIn("dust", zones, family)

    def test_a_layered_catalog_module_names_every_material_s_family(self):
        nodes = model(("Block", (0, 8, 0), (16, 16, 16)))
        bare = SimpleNamespace(MODEL="Blocks/Test/Block", PICTURES=frozenset(), CONDITION=conditions.USED,
                               material=lambda n, s: "board", tiles=lambda a: {"board": white_noise(32, 32)})
        with self.assertRaises(SystemExit):
            catalog.model_texture(bare, nodes, None)
        named = SimpleNamespace(**vars(bare), FAMILY={"board": "wood"})
        self.assertEqual(catalog.texture_size(nodes), catalog.model_texture(named, nodes, None).size)
        reed = SimpleNamespace(PICTURES=frozenset(), FAMILY={"head": "plant"})
        self.assertEqual("plant", catalog.layered_tiles(reed, {"head": brushes.cloth((112, 72, 44))})["head"].family,
                         "FAMILY names a material's family over its brush's")
        built = {"head": effects.material(brushes.cloth((112, 72, 44)))}
        self.assertEqual("plant", catalog.layered_tiles(reed, built)["head"].family, "and over a built material's")
        with self.assertRaises(SystemExit):
            catalog.layered_tiles(SimpleNamespace(PICTURES=frozenset(), FAMILY={"head": "reed"}), built)
        rusting = {"head": effects.material(brushes.metal((150, 154, 162)), effects=((weathering.EFFECTS["rust"], 1),))}
        with self.assertRaises(SystemExit, msg="a family its own effects are impossible on"):
            catalog.layered_tiles(SimpleNamespace(PICTURES=frozenset(), FAMILY={"head": "wood"}), rusting)

    def test_a_module_s_grounded_overrides_its_model_s_path(self):
        self.assertTrue(catalog.grounded(SimpleNamespace(MODEL="Blocks/Test/Block")))
        self.assertFalse(catalog.grounded(SimpleNamespace(MODEL="Items/Test/Tool")))
        self.assertTrue(catalog.grounded(SimpleNamespace(GROUNDED=True)))
        self.assertFalse(catalog.grounded(SimpleNamespace(PICTURES=frozenset())))

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

    def test_a_neglected_model_s_rest_zones_damp_its_effects_less_and_a_ruined_one_s_not_at_all(self):
        self.assertEqual(art.REST, art.rest_floor(0))
        self.assertGreater(art.rest_floor(2), art.rest_floor(1))
        self.assertEqual(1.0, art.rest_floor(3))
        island = front_island(flat(STEEL))
        everywhere = effects.Effect("everywhere", "degrade", lambda t, d, isl, i, j: 0.8, lambda isl, a: None)
        busy = effects.reached(everywhere, island, 0.5, effects.Pass(roles.Declared(), 1))
        ruined = effects.reached(everywhere, island, 0.5, effects.Pass(roles.Declared(), 1, True, floor=1.0))
        resting = effects.reached(everywhere, island, 0.5, effects.Pass(roles.Declared(), 1, True))
        self.assertEqual(busy, ruined)
        self.assertLess(len(resting), len(ruined))

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

    def test_an_effect_that_cannot_reach_its_island_leaves_its_place_to_the_next(self):
        names = ("dirt", "edge_wear", "chips", "grime")
        uses = [(weathering.EFFECTS[n], d) for n, d in zip(names, (0.9, 0.5, 0.4, 0.3))]
        wood = effects.material(flat(STEEL), family="wood")
        kept = [e.name for e, _ in surface.most_useful(uses, wood, "low", lambda e, d, k: e.name != "dirt")]
        self.assertEqual(["edge_wear", "chips"], kept)

    def test_a_neglected_model_shows_more_effects_on_an_island_than_a_used_one(self):
        self.assertEqual(0, conditions.USED.show)
        self.assertLess(conditions.WORN.show, conditions.NEGLECTED.show)
        self.assertLess(conditions.NEGLECTED.show, conditions.RUINED.show)
        names = ("edge_wear", "chips", "dirt", "grime", "dust", "scuffs")
        uses = [(surface.CATALOGUE[n], 0.5) for n in names]
        wood = effects.material(flat(STEEL), family="wood")
        self.assertEqual(art.VISIBLE["medium"] + conditions.NEGLECTED.show,
                         len(surface.most_useful(uses, wood, "medium", extra=conditions.NEGLECTED.show)))

    def test_dirt_cannot_reach_a_part_high_off_the_ground_but_wear_can(self):
        nodes = model(("Block", (0, 40, 0), (16, 16, 16)))
        unwrap(nodes)
        _, contexts = bake.survey(nodes, grounded=True, wear=0.0, grime=0.0)
        u, v, w, h = next(r for r in paint.islands(nodes) if r[:2] == ("Block", "front"))[2:]
        texels = {(i, j): contexts[u + i, v + j] for i in range(w) for j in range(h)}
        wood = effects.material(lambda w, h, side: Image.new("RGBA", (w, h), (150, 104, 62, 255)), family="wood")
        island = layers.Island(wood.substrate(w, h, "front"), texels, wood, "front")
        how = effects.Pass(roles.Declared(), 3)
        self.assertFalse(surface.reachable(weathering.EFFECTS["dirt"], 0.9, island, how, []))
        self.assertTrue(surface.reachable(weathering.EFFECTS["edge_wear"], 0.6, island, how, []))

    def test_an_effect_reading_a_signal_is_kept_when_a_kept_effect_writes_it(self):
        island = front_island(flat(STEEL))
        how = effects.Pass(roles.Declared(), 3)
        rust = weathering.EFFECTS["rust"]
        writer = next(e for e in weathering.EFFECTS.values() if set(rust.reads) & set(e.writes))
        self.assertTrue(surface.reachable(rust, 0.9, island, how, [(writer, 0.5)]))

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
