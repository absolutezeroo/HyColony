"""Checks of the degradations and deposits beyond the first batch (spec 2026-10-03 blockpaint surfaces § 3.5): python
tools/blockpaint/check_weathering.py (no assets needed). Each effect lands where it is plausible, on the families it
suits, and ties to the effects after it through its signals. An AssertionError names the case."""

import unittest

import bake
import brushes
import coatings
import degradations
import deposits
import effects
import layers
import paint
import roles
import weathering
from check_effects import crate_model
from models import unwrap

PAINT = coatings.paint_coat((60, 96, 150))
EVERY = {**degradations.EFFECTS, **deposits.EFFECTS}


class WeatheringTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.nodes = crate_model()
        unwrap(cls.nodes)
        _, cls.contexts = bake.survey(cls.nodes, grounded=True, wear=0.0, grime=0.0)

    def island(self, name, side, brush=None, family="ferrous", coats=()):
        brush = brush or brushes.metal((120, 120, 120))
        u, v, w, h = next(r for r in paint.islands(self.nodes) if r[:2] == (name, side))[2:]
        texels = {(i, j): self.contexts[u + i, v + j] for i in range(w) for j in range(h)}
        return layers.Island(brush(w, h, side), texels, effects.Material(brush, family, coats, (), {}), side)

    def reached(self, name, island, degree=0.7, declared=roles.Declared()):
        return effects.reached(EVERY[name], island, degree, effects.Pass(declared, 4))

    def test_every_effect_is_steady_grows_with_its_degree_and_never_changes_alpha(self):
        for name, effect in EVERY.items():
            island = self.island("Lid", "top", brushes.wood((150, 104, 62)), "wood")
            low, high = self.reached(name, island, 0.4), self.reached(name, island, 0.8)
            self.assertEqual(low, self.reached(name, island, 0.4), name)
            self.assertTrue({c for c, a in low.items() if a == 1.0} <= {c for c, a in high.items() if a == 1.0}, name)
            effect.act(island, high)
            self.assertEqual({255}, {p[3] for p in layers.compose(island).get_flattened_data()}, name)

    def test_paint_ages_only_on_paint_and_metal_ages_only_on_metal(self):
        wood = self.island("Body", "front", brushes.wood((150, 104, 62)), "wood")
        painted = self.island("Body", "front", brushes.wood((150, 104, 62)), "wood", (PAINT,))
        for name in ("chalking", "peeling", "blistering", "craquelure"):
            self.assertEqual({}, self.reached(name, wood, 1.0), name)
            self.assertTrue(self.reached(name, painted, 1.0), name)
        iron = self.island("Band0", "front")
        for name in ("oxidation", "deep_rust"):
            self.assertEqual({}, self.reached(name, wood, 1.0), name)
        self.assertTrue(self.reached("oxidation", iron, 1.0))

    def test_scratches_follow_their_lines(self):
        island = self.island("Body", "front", coats=(PAINT,))
        lines = self.reached("scratches", island, 1.0, roles.Declared(contact=1.0))
        self.assertTrue(lines)
        self.assertLess(len(lines), len(island.texels) / 2)

    def test_cracks_hold_water_and_moss_grows_beside_them(self):
        stone = self.island("Lid", "top", brushes.stone((128, 124, 116)), "stone")
        effects.run(stone, [(EVERY["cracks"], 0.8)], effects.Pass(roles.Declared(), 4))
        self.assertTrue(stone.signal("water_retention"))
        cell = next(iter(stone.signal("water_retention")))
        dry = self.island("Lid", "top", brushes.stone((128, 124, 116)), "stone")
        t = stone.texels[cell]
        self.assertGreater(deposits.damp(t, stone, *cell), deposits.damp(t, dry, *cell))

    def test_rust_runs_down_below_rust(self):
        island = self.island("Body", "front")
        island.signals["rust"] = {(i, 2) for i in range(4, 8)}
        streaked = set(self.reached("rust_streaks", island, 1.0))
        self.assertTrue(streaked)
        self.assertTrue(all(2 < j <= 2 + degradations.STREAK_RUN for _, j in streaked))

    def test_snow_and_ash_lie_on_top_and_mud_and_sand_low(self):
        top = self.island("Lid", "top", brushes.wood((150, 104, 62)), "wood")
        side = self.island("Body", "front", brushes.wood((150, 104, 62)), "wood")
        for name in ("snow", "ash"):
            self.assertTrue(self.reached(name, top, 0.8), name)
            self.assertEqual({}, self.reached(name, side, 0.8), name)
        feet = self.island("Foot0", "front", brushes.wood((150, 104, 62)), "wood")
        lid = self.island("Lid", "front", brushes.wood((150, 104, 62)), "wood")
        for name in ("mud", "sand"):
            self.assertGreater(len(self.reached(name, feet, 0.8)) / len(feet.texels),
                               len(self.reached(name, lid, 0.8)) / len(lid.texels), name)

    def test_grease_makes_dust_cling_even_when_dust_is_listed_first(self):
        # On a side face, which gathers little dust, dust clings where the grease is: grease runs first, as dust reads
        # what it writes, whatever the order of the uses.
        touched = effects.Pass(roles.Declared(contact=1.0), 4)
        dust = (weathering.EFFECTS["dust"], 0.8)
        alone = self.island("Body", "front", brushes.wood((150, 104, 62)), "wood")
        effects.run(alone, [dust], touched)
        greased = self.island("Body", "front", brushes.wood((150, 104, 62)), "wood")
        effects.run(greased, [dust, (EVERY["grease"], 0.8)], touched)
        self.assertTrue(greased.signal("sticky"))
        self.assertGreater(len(greased.zones["dust"]), len(alone.zones["dust"]))

    def test_rust_tells_its_pits_where_to_go(self):
        island = self.island("Band0", "front")
        island.depth.update(dict.fromkeys(island.depth, 5.0))
        effects.run(island, [(EVERY["rust_pits"], 1.0), (weathering.EFFECTS["rust"], 1.0)],
                    effects.Pass(roles.Declared(), 4))
        self.assertTrue(island.zones["rust"])
        self.assertTrue(island.zones["rust_pits"])
        self.assertLessEqual(island.zones["rust_pits"], island.zones["rust"])


if __name__ == "__main__":
    unittest.main()
