"""Checks of the first batch of effects and of layered materials painted through paint and catalog (spec 2026-10-03
blockpaint surfaces): python tools/blockpaint/check_effects.py (no assets needed). An AssertionError names the
case."""

import unittest
from types import SimpleNamespace

from PIL import Image

import art
import bake
import brushes
import catalog
import coatings
import conditions
import effects
import history
import layers
import paint
import roles
import surface
import weathering
from check_surfaces import model
from models import unwrap


def crate_model():
    """A crate: body, lid, two iron bands, four feet."""
    return model(("Body", (0, 8, 0), (16, 12, 12)), ("Lid", (0, 15, 0), (18, 2, 14)),
                 ("Band0", (-5, 8, 0), (2, 12.5, 12.5)), ("Band1", (5, 8, 0), (2, 12.5, 12.5)),
                 *((f"Foot{k}", (sx * 6, 1, sz * 4), (3, 2, 3)) for k, (sx, sz) in
                   enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1)))))


def iron():
    return brushes.metal((118, 120, 126))


WOOD = brushes.wood((150, 104, 62))


class ContentTest(unittest.TestCase):
    def setUp(self):
        self.nodes = crate_model()
        unwrap(self.nodes)
        _, self.contexts = bake.survey(self.nodes, grounded=True, wear=0.0, grime=0.0)

    def island(self, name, side, brush, family, coats=()):
        u, v, w, h = next(r for r in paint.islands(self.nodes) if r[:2] == (name, side))[2:]
        texels = {(i, j): self.contexts[u + i, v + j] for i in range(w) for j in range(h)}
        return layers.Island(brush(w, h, side), texels, effects.Material(brush, family, coats, (), {}), side)

    def amounts(self, name, island, degree, declared=roles.Declared()):
        return effects.reached(weathering.EFFECTS[name], island, degree, effects.Pass(declared, 3))

    def test_every_effect_is_steady_grows_with_its_degree_and_reaches_nothing_at_zero(self):
        for name in weathering.EFFECTS:
            island = self.island("Lid", "top", WOOD, "ferrous")
            island.depth.update(dict.fromkeys(island.depth, 5.0))
            self.assertEqual({}, self.amounts(name, island, 0.0), name)
            low, high = self.amounts(name, island, 0.4), self.amounts(name, island, 0.8)
            self.assertEqual(low, self.amounts(name, island, 0.4), name)
            self.assertTrue({c for c, a in low.items() if a == 1.0} <= {c for c, a in high.items() if a == 1.0}, name)

    def test_edge_wear_keeps_to_the_open_borders(self):
        island = self.island("Body", "front", WOOD, "wood")
        worn = self.amounts("edge_wear", island, 1.0)
        self.assertTrue(worn)
        self.assertTrue(all(island.texels[c].rim < 2 for c in worn))

    def test_chips_gather_near_the_borders_rather_than_in_the_middle(self):
        island = self.island("Lid", "top", WOOD, "wood")
        chipped = self.amounts("chips", island, 0.5)
        self.assertTrue(chipped)
        mean_rim = sum(island.texels[c].rim for c in chipped) / len(chipped)
        self.assertLess(mean_rim, sum(t.rim for t in island.texels.values()) / len(island.texels))

    def test_dust_lies_on_top_faces_rather_than_sides(self):
        self.assertTrue(self.amounts("dust", self.island("Lid", "top", WOOD, "wood"), 0.6))
        self.assertEqual({}, self.amounts("dust", self.island("Body", "front", WOOD, "wood"), 0.6))

    def test_dust_settles_on_a_top_face_under_cover_too(self):
        # A worktop under a hutch's shelves sees no sky, yet dust settles on it all the same.
        island = self.island("Lid", "top", WOOD, "wood")
        island.texels.update({cell: t._replace(occlusion=0.7) for cell, t in island.texels.items()})
        self.assertTrue(self.amounts("dust", island, 0.6))

    def test_dirt_rises_from_the_ground(self):
        # The body stands on its feet, 2 units up: dirt reaches it only at a high degree, and only low down.
        island = self.island("Body", "front", WOOD, "wood")
        dirty = self.amounts("dirt", island, 0.9)
        self.assertTrue(dirty)
        self.assertLess(max(island.texels[c].point[1] for c in dirty), 8)

    def test_grime_gathers_in_hollows(self):
        island = self.island("Body", "front", WOOD, "wood")
        grimy = self.amounts("grime", island, 0.6)
        self.assertTrue(grimy)
        mean_grimy = sum(island.texels[c].occlusion for c in grimy) / len(grimy)
        self.assertGreater(mean_grimy, sum(t.occlusion for t in island.texels.values()) / len(island.texels))

    def test_rust_comes_only_on_bare_iron(self):
        painted = self.island("Band0", "front", iron(), "ferrous", (coatings.paint_coat((60, 96, 150)),))
        self.assertEqual({}, self.amounts("rust", painted, 1.0))
        # A 2 x 2 patch: a lone bare texel would be a speck, which no effect keeps.
        patch = {(0, 0), (1, 0), (0, 1), (1, 1)}
        painted.depth.update(dict.fromkeys(patch, 2.0))
        self.assertEqual(patch, set(self.amounts("rust", painted, 1.0)))
        self.assertEqual({}, self.amounts("rust", self.island("Body", "front", WOOD, "wood"), 1.0))

    def test_every_effect_is_whole_where_it_is_keeps_to_its_mask_and_never_changes_alpha(self):
        for name, effect in weathering.EFFECTS.items():
            island = self.island("Lid", "top", iron(), "ferrous")
            island.depth.update(dict.fromkeys(island.depth, 5.0))
            amounts = self.amounts(name, island, 0.7)
            allowed = {1.0, effects.SOFT_AMOUNT} if effect.soft else {1.0}
            self.assertTrue(set(amounts.values()) <= allowed, name)
            outside = {c for c, t in island.texels.items() if effect.mask(t, roles.Declared(), island, *c) == 0}
            self.assertFalse(outside & set(amounts), name)
            effect.act(island, amounts)
            self.assertEqual({255}, {p[3] for p in layers.compose(island).get_flattened_data()}, name)

    def test_edge_wear_wears_through_the_coats_or_polishes_bare_wood(self):
        painted = self.island("Body", "front", WOOD, "wood", (coatings.paint_coat((60, 96, 150)),))
        effects.run(painted, [(weathering.EFFECTS["edge_wear"], 1.0)], effects.Pass(roles.Declared(), 3))
        self.assertTrue(any(painted.bare("wood", *c) for c in painted.depth))
        bare = self.island("Body", "front", WOOD, "wood")
        before = bare.substrate.copy()
        worn = self.amounts("edge_wear", bare, 1.0)
        weathering.wear_edges(bare, worn)
        cell = next(iter(worn))
        self.assertGreater(sum(bare.substrate.getpixel(cell)[:3]), sum(before.getpixel(cell)[:3]))

    def test_a_chip_bites_into_the_substrate_past_every_coat(self):
        island = self.island("Lid", "top", WOOD, "wood", (coatings.paint_coat((60, 96, 150)),))
        chipped = self.amounts("chips", island, 0.6)
        weathering.chip(island, chipped)
        self.assertTrue(all(island.depth[c] > island.total() for c in chipped))

    def test_dirt_climbs_a_part_declared_near_the_ground(self):
        island = self.island("Body", "front", WOOD, "wood")
        plain = self.amounts("dirt", island, 0.5)
        grounded = self.amounts("dirt", island, 0.5, roles.Declared(ground=1.0))
        self.assertGreater(len(grounded), len(plain))

    def test_rust_takes_more_beside_damage(self):
        # The middle of an iron side: bare, but neither exposed nor damaged yet.
        island = self.island("Body", "front", iron(), "ferrous")
        t = island.texels[8, 6]
        calm = weathering.rust_mask(t, island, 8, 6)
        island.signals["damage"] = {(8, 7)}
        self.assertGreater(weathering.rust_mask(t, island, 8, 6), calm)

    def test_a_chip_through_paint_and_varnish_bares_the_wood(self):
        island = self.island("Lid", "top", WOOD, "wood",
                             (coatings.paint_coat((60, 96, 150)), coatings.varnish_coat()))
        effects.run(island, [(weathering.EFFECTS["chips"], 0.6)], effects.Pass(roles.Declared(), 3))
        self.assertTrue(any(island.bare("wood", *cell) for cell in island.depth))

    def test_a_varnish_shines_on_a_lit_face(self):
        plain = self.island("Lid", "top", brushes.metal((120, 120, 120)), "wood")
        varnished = self.island("Lid", "top", brushes.metal((120, 120, 120)), "wood",
                                (coatings.varnish_coat(opacity=0.0),))
        self.assertGreater(sum(layers.compose(varnished).getpixel((4, 4))[:3]),
                           sum(layers.compose(plain).getpixel((4, 4))[:3]))


class PaintSurfaceTest(unittest.TestCase):
    def test_a_layered_material_without_contexts_is_refused(self):
        nodes = model(("Block", (0, 4, 0), (8, 8, 8)))
        size = unwrap(nodes)
        tiles = {"wood": effects.material(WOOD, family="wood")}
        with self.assertRaises(SystemExit):
            paint.paint(nodes, size, paint.Look(tiles, lambda n, s: "wood"))

    def test_a_layered_crate_paints_every_island_opaque(self):
        nodes = crate_model()
        size = unwrap(nodes)
        values, contexts = bake.survey(nodes, grounded=True, wear=0.0, grime=0.0)
        tiles = {"wood": effects.material(WOOD, family="wood", coats=(coatings.paint_coat((60, 96, 150)),)),
                 "iron": effects.material(iron())}
        look = paint.Look(tiles, lambda n, s: "iron" if n.startswith("Band") else "wood")
        image = paint.texture(nodes, size, look, values,
                              paint.Painting(surface=surface.Surface(contexts, conditions.WORN), light="hytale"))
        for name, side, u, v, w, h in paint.islands(nodes):
            self.assertTrue(all(image.getpixel((u + i, v + j))[3] == 255 for i in range(w) for j in range(h)))

    def layered_crate(self, tiles, **surface_fields):
        nodes = crate_model()
        size = unwrap(nodes)
        _, contexts = bake.survey(nodes, grounded=True, wear=0.0, grime=0.0)
        look = paint.Look(tiles, lambda n, s: "iron" if n.startswith("Band") else "wood")
        painting = paint.Painting(surface=surface.Surface(contexts, **surface_fields))
        return paint.paint(nodes, size, look, painting), nodes

    def test_the_macro_variation_swells_even_a_flat_part(self):
        flat = effects.material(lambda w, h, side: Image.new("RGBA", (w, h), (150, 104, 62, 255)), family="wood")
        image, nodes = self.layered_crate({"wood": flat, "iron": flat}, condition=conditions.PRISTINE)
        top = next(r for r in paint.islands(nodes) if r[:2] == ("Lid", "top"))
        u, v, w, h = top[2:]
        self.assertGreater(len(set(image.crop((u, v, u + w, v + h)).get_flattened_data())), 1)

    def test_a_neglected_model_is_dulled_all_over_and_a_worn_one_is_not(self):
        plain = {"wood": effects.material(WOOD, family="wood"), "iron": effects.material(iron())}
        clean, _ = self.layered_crate(plain, condition=conditions.PRISTINE)
        neglected, _ = self.layered_crate(plain, condition=conditions.PRISTINE._replace(show=2))

        def saturation(image):
            hsv = image.convert("RGB").convert("HSV").get_flattened_data()
            return sum(p[1] for p in hsv) / len(hsv)

        self.assertLess(saturation(neglected), saturation(clean) * 0.85)
        # Worn (show 1) wears without going dull yet: its materials stay as far apart as a used one's.
        worn, _ = self.layered_crate(plain, condition=conditions.PRISTINE._replace(show=conditions.WORN.show))
        self.assertEqual(clean.tobytes(), worn.tobytes())

    def test_a_material_s_own_effects_and_the_usage_reach_the_paint(self):
        plain = {"wood": effects.material(WOOD, family="wood"), "iron": effects.material(iron())}
        dusty = dict(plain, wood=effects.material(WOOD, family="wood", effects=((weathering.EFFECTS["dust"], 1.0),)))
        base, _ = self.layered_crate(plain, condition=conditions.PRISTINE)
        self.assertNotEqual(base.tobytes(), self.layered_crate(dusty, condition=conditions.PRISTINE)[0].tobytes())
        worn, _ = self.layered_crate(plain, condition=conditions.WORN)
        touched, _ = self.layered_crate(plain, condition=conditions.WORN, usage={"Body": roles.contact(1.0)})
        self.assertNotEqual(worn.tobytes(), touched.tobytes())

    def test_a_material_s_own_effect_adds_to_what_the_condition_plans_weighed_by_the_role(self):
        own = [(weathering.EFFECTS["dust"], 0.5), (weathering.EFFECTS["rust"], 0.3)]
        merged = dict((e.name, d) for e, d in surface.merged([(weathering.EFFECTS["dust"], 0.2)], own, "Lid", {}))
        self.assertEqual({"dust": 0.5, "rust": 0.3}, merged)
        # A roof halves its dust: the material's own dust is weighed by the role, as the planned one already is.
        roofed = dict((e.name, d) for e, d in surface.merged([], own, "Lid", {"Lid": "roof"}))
        self.assertEqual(0.25, roofed["dust"])


def crate_module(**declared):
    """A catalog module painting the crate: wood planks and iron bands, layered when it declares a CONDITION other
    than DEFAULT."""
    layered = declared.get("CONDITION") not in (None, conditions.DEFAULT)
    wood = effects.material(WOOD, family="wood", coats=(coatings.paint_coat((60, 96, 150)),)) if layered else WOOD
    metal = effects.material(iron()) if layered else iron()
    return SimpleNamespace(MODEL="Blocks/Test/Crate", PICTURES=frozenset(), tiles=lambda assets: {"wood": wood,
                                                                                                "iron": metal},
                           material=lambda name, side: "iron" if name.startswith("Band") else "wood", **declared)


# A role that shows on its own: a lid is touched, which wears its edges further.
PARTS = {"Lid": "lid"}
# Ancient: rust strong enough to be among the bands' most useful effects (art.VISIBLE), so the age shows.
DECLARED = dict(CONDITION=conditions.WORN, AGE=conditions.ANCIENT, ENVIRONMENT=conditions.DRY_INTERIOR,
                ROLES=PARTS, USAGE={"Body": roles.contact(1.0)}, FOCUS=("Lid",), SEED=3, AXES={"Body": "y"},
                ART=art.Art("high"), HISTORY=(history.fire("Body", (0, -4, 1)),))


class CatalogTest(unittest.TestCase):
    def test_a_module_without_condition_or_with_the_default_one_paints_exactly_as_before(self):
        nodes = crate_model()
        size = catalog.texture_size(nodes)
        before = paint.texture(nodes, size, paint.Look(crate_module().tiles(None), crate_module().material),
                               bake.light_map(nodes, True))
        self.assertEqual(before.tobytes(), catalog.model_texture(crate_module(), nodes, None).tobytes())
        default = catalog.model_texture(crate_module(CONDITION=conditions.DEFAULT), nodes, None)
        self.assertEqual(before.tobytes(), default.tobytes())

    def test_a_module_with_a_condition_passes_all_it_declares_and_is_lit_the_hytale_way(self):
        nodes = crate_model()
        module = crate_module(**DECLARED)
        values, contexts = bake.survey(nodes, True, frozenset(), 0.0, 0.0)
        layered = surface.Surface(contexts, conditions.WORN, conditions.ANCIENT, conditions.DRY_INTERIOR, PARTS,
                                  {"Body": roles.contact(1.0)}, ("Lid",), 3, art.Art("high"),
                                  history.resolved(DECLARED["HISTORY"], contexts))
        expected = paint.texture(nodes, catalog.texture_size(nodes), paint.Look(module.tiles(None), module.material),
                                 values, paint.Painting(3, {"Body": "y"}, layered, "hytale"))
        self.assertEqual(expected.tobytes(), catalog.model_texture(module, nodes, None).tobytes())
        # Each declaration counts: leaving any one out paints other bytes.
        for name in DECLARED:
            if name != "CONDITION":
                fewer = crate_module(**{k: v for k, v in DECLARED.items() if k != name})
                same = expected.tobytes() == catalog.model_texture(fewer, nodes, None).tobytes()
                self.assertFalse(same, name)

    def test_an_unknown_role_stops_the_painting_with_the_known_ones(self):
        module = crate_module(CONDITION=conditions.WORN, ROLES={"Lid": "lidd"})
        with self.assertRaises(SystemExit) as refused:
            catalog.model_texture(module, crate_model(), None)
        self.assertIn("lid", str(refused.exception))


if __name__ == "__main__":
    unittest.main()
