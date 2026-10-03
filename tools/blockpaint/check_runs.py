"""Checks of what an effect reaches and of the single run of operations on a face (spec 2026-10-03 blockpaint
surfaces, effects): python tools/blockpaint/check_runs.py (no assets needed). An AssertionError names the case."""

import unittest

import effects
import layers
import maps
import roles
from check_layers import BLUE, STEEL, flat, front_island

# A plain pass: no declared maps, seed 1, no rest zones, no fight.
PLAIN = effects.Pass(roles.Declared(), 1)


def effect(name, moment, mask, act, **fields):
    """An effect for a test: its reads, writes, spread or soft border among fields."""
    return effects.Effect(name, moment, mask, act, **fields)


def dig_to(depth):
    def act(island, amounts):
        island.dig(dict.fromkeys(effects.whole(amounts), depth))
    return act


class ReachTest(unittest.TestCase):
    def test_what_a_degree_reaches_a_higher_one_still_reaches_and_never_outside_the_mask(self):
        island = front_island(flat(STEEL))
        edge_mask = effect("edge", "degrade", lambda t, d, isl, i, j: maps.exposure(t), dig_to(0.5))
        low = effects.reached(edge_mask, island, 0.3, PLAIN)
        high = effects.reached(edge_mask, island, 0.6, PLAIN)
        self.assertTrue(set(low) <= set(high))
        self.assertLess(len(low), len(high))
        inner = [cell for cell, t in island.texels.items() if maps.exposure(t) == 0.0]
        self.assertTrue(inner)
        self.assertFalse(set(inner) & set(effects.reached(edge_mask, island, 1.0, PLAIN)))

    def test_degree_zero_reaches_nothing_and_a_hard_effect_is_full_where_it_is(self):
        island = front_island(flat(STEEL))
        everywhere = effect("all", "degrade", lambda t, d, isl, i, j: 1.0, dig_to(0.5))
        self.assertEqual({}, effects.reached(everywhere, island, 0.0, PLAIN))
        self.assertEqual({1.0}, set(effects.reached(everywhere, island, 0.5, PLAIN).values()))

    def test_a_soft_effect_fades_only_on_the_texels_next_to_its_zone(self):
        island = front_island(flat(STEEL))
        dust = effect("dust", "deposit", lambda t, d, isl, i, j: maps.exposure(t), dig_to(0.5), soft=True)
        amounts = effects.reached(dust, island, 0.5, PLAIN)
        full = set(effects.whole(amounts))
        for (i, j), a in amounts.items():
            if a < 1.0:
                self.assertTrue({(i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)} & full)

    def test_clusters_cut_into_an_even_mask_and_join_up_across_faces(self):
        island = front_island(flat(STEEL))
        even = effect("even", "degrade", lambda *a: 0.8, dig_to(0.5))
        some = effects.reached(even, island, 0.5, PLAIN)
        self.assertTrue(0 < len(some) < len(island.texels))
        here, there = (1.0, 2.0, 3.0), (1.05, 2.0, 3.0)
        self.assertLess(abs(effects.cluster(here, 0.6, 1, "even") - effects.cluster(there, 0.6, 1, "even")), 0.05)

    def test_a_soft_border_never_reaches_past_its_band(self):
        island = front_island(flat(STEEL))
        # Whole on the four left columns, barely there beside them: far below the band.
        halves = effect("halves", "deposit", lambda t, d, isl, i, j: 1.0 if i < 4 else 0.1, dig_to(0.5), spread=0.0,
                        soft=True)
        amounts = effects.reached(halves, island, 0.5, PLAIN)
        self.assertTrue(amounts)
        self.assertTrue(all(i < 4 for i, _ in amounts))

    def test_a_lone_texel_is_never_kept(self):
        island = front_island(flat(STEEL))
        lone = effect("lone", "degrade", lambda t, d, isl, i, j: 1.0 if (i, j) == (4, 4) else 0.0, dig_to(0.5))
        self.assertEqual({}, effects.reached(lone, island, 1.0, PLAIN))


class RunTest(unittest.TestCase):
    def test_rust_reads_the_iron_a_chip_has_just_bared_and_comes_nowhere_else(self):
        island = front_island(flat(STEEL), (layers.Coat(BLUE, "cover"),))
        chips = effect("chips", "degrade", lambda t, d, isl, i, j: 1.0 if i < 3 else 0.0, dig_to(1.5),
                       writes=("bare_ferrous",))
        rusted = set()

        def rust_act(isl, amounts):
            rusted.update(effects.whole(amounts))

        rust = effect("rust", "degrade", lambda t, d, isl, i, j: 1.0 if isl.bare("ferrous", i, j) else 0.0, rust_act,
                      reads=("bare_ferrous",))
        effects.run(island, [(rust, 1.0), (chips, 1.0)], PLAIN)
        self.assertTrue(rusted)
        self.assertTrue(all(i < 3 for i, _ in rusted))

    def test_effects_run_by_moment_then_by_what_they_read(self):
        order = []

        def note(name):
            return lambda isl, amounts: order.append(name)

        dust = effect("dust", "deposit", lambda *a: 1.0, note("dust"))
        rust = effect("rust", "degrade", lambda *a: 1.0, note("rust"), reads=("bare_ferrous",))
        chips = effect("chips", "degrade", lambda *a: 1.0, note("chips"), writes=("bare_ferrous",))
        effects.run(front_island(flat(STEEL)), [(dust, 1.0), (rust, 1.0), (chips, 1.0)], PLAIN)
        self.assertEqual(["chips", "rust", "dust"], order)

    def test_a_cycle_of_signals_is_refused(self):
        a = effect("a", "degrade", lambda *x: 1.0, dig_to(0.1), reads=("b",), writes=("a",))
        b = effect("b", "degrade", lambda *x: 1.0, dig_to(0.1), reads=("a",), writes=("b",))
        with self.assertRaises(SystemExit):
            effects.run(front_island(flat(STEEL)), [(a, 1.0), (b, 1.0)], PLAIN)

    def test_an_effect_skips_the_layers_where_it_is_impossible(self):
        rust = effect("rust", "degrade", lambda *a: 1.0, dig_to(0.2))
        wood = front_island(flat(STEEL), family="wood")
        self.assertEqual({}, effects.reached(rust, wood, 1.0, PLAIN))
        painted = front_island(flat(STEEL), (layers.Coat(BLUE, "cover"),))
        self.assertEqual({}, effects.reached(rust, painted, 1.0, PLAIN))
        bare = front_island(flat(STEEL))
        self.assertTrue(effects.reached(rust, bare, 1.0, PLAIN))

    def test_asking_a_material_for_an_impossible_effect_is_refused(self):
        rust = effect("rust", "degrade", lambda *a: 1.0, dig_to(0.2))
        with self.assertRaises(SystemExit):
            effects.material(flat(STEEL), family="wood", effects=((rust, 0.5),))
        with self.assertRaises(SystemExit):
            effects.material(flat(STEEL), family="ferrous", effects=((rust, 0.5),), weights={"rust": 0.0})

    def test_a_material_s_own_weight_forbids_an_effect_on_it(self):
        rust = effect("rust", "degrade", lambda *a: 1.0, dig_to(0.2))
        stainless = front_island(flat(STEEL))
        stainless.weights = {"rust": 0.0}
        self.assertEqual({}, effects.reached(rust, stainless, 1.0, PLAIN))

    def test_an_unknown_family_is_refused(self):
        with self.assertRaises(SystemExit):
            effects.material(flat(STEEL), family="ferous")

    def test_written_signals_are_kept_for_the_effects_after(self):
        island = front_island(flat(STEEL))
        chips = effect("chips", "degrade", lambda t, d, isl, i, j: 1.0 if i < 3 else 0.0, dig_to(0.5),
                       writes=("damage",))
        effects.run(island, [(chips, 1.0)], PLAIN)
        self.assertTrue(island.signal("damage"))
        self.assertTrue(all(i < 3 for i, _ in island.signal("damage")))

    def test_a_misspelt_signal_is_refused(self):
        rust = effect("rust", "degrade", lambda *a: 1.0, dig_to(0.2), reads=("damaged",))
        with self.assertRaises(SystemExit):
            effects.run(front_island(flat(STEEL)), [(rust, 1.0)], PLAIN)

    def test_a_signal_written_that_none_reads_is_refused(self):
        chips = effect("chips", "degrade", lambda *a: 1.0, dig_to(0.2), writes=("chipped",))
        with self.assertRaises(SystemExit):
            effects.run(front_island(flat(STEEL)), [(chips, 1.0)], PLAIN)


if __name__ == "__main__":
    unittest.main()
