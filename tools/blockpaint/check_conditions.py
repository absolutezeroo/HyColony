"""Checks of what age, condition and environment plan, and of the material x effect compatibility (spec 2026-10-03
blockpaint surfaces § 3.3, § 3.5.3): python tools/blockpaint/check_conditions.py (no assets needed). An
AssertionError names the case."""

import unittest

import compat
import conditions
import surface
import weathering


def planned(condition, part=None, **fields):
    """{effect name: degree} the plan gives a part of a model in condition, its age, environment, roles and catalogue
    (the first batch when not given) among fields (surface.Surface)."""
    fields.setdefault("catalogue", weathering.EFFECTS)
    return {e.name: d for e, d in conditions.plan(surface.Surface(None, condition, **fields), part)}


class PlanTest(unittest.TestCase):
    def test_an_old_but_maintained_model_rusts_a_little_and_stays_clean(self):
        uses = planned(conditions.MAINTAINED, age=conditions.OLD)
        self.assertGreater(uses["rust"], 0.0)
        self.assertLess(uses["rust"], conditions.OLD.degrees["rust"])
        self.assertLess(uses.get("dirt", 0.0), 0.1)

    def test_a_new_but_worn_model_is_worn_and_chipped_without_rust(self):
        uses = planned(conditions.WORN, age=conditions.NEW)
        self.assertGreater(uses["edge_wear"], 0.3)
        self.assertGreater(uses["chips"], 0.2)
        self.assertNotIn("rust", uses)

    def test_a_dry_interior_gathers_dust_rather_than_dirt(self):
        dry = planned(conditions.USED, environment=conditions.DRY_INTERIOR)
        out = planned(conditions.USED, environment=conditions.TEMPERATE_OUTDOOR)
        self.assertLess(dry["dirt"], out["dirt"])
        self.assertGreater(dry["dust"], out["dust"])

    def test_a_role_weighs_its_part_s_effects(self):
        face = planned(conditions.WORN, "Head", roles={"Head": "face"})
        self.assertLess(face["dirt"], planned(conditions.WORN)["dirt"])

    def test_the_default_condition_plans_nothing(self):
        self.assertEqual({}, planned(conditions.DEFAULT))

    def test_without_an_age_the_condition_brings_its_own(self):
        expected = conditions.MATURE.degrees["rust"] * conditions.WORN.care
        self.assertAlmostEqual(expected, planned(conditions.WORN)["rust"])

    def test_an_effect_of_the_place_shows_only_where_the_environment_names_it(self):
        def every(condition, environment=None):
            return planned(condition, environment=environment, catalogue=surface.CATALOGUE)

        self.assertFalse(conditions.PLACED & set(every(conditions.RUINED)), "moss of an ancient model with no place")
        self.assertIn("moss", every(conditions.RUINED, conditions.FOREST))
        self.assertNotIn("moss", every(conditions.RUINED, conditions.DRY_INTERIOR))
        snowy = every(conditions.NEGLECTED, conditions.SNOW)
        self.assertAlmostEqual(conditions.SNOW.degrees["snow"], snowy["snow"])
        self.assertNotIn("snow", every(conditions.PRISTINE, conditions.SNOW), "care clears the snow")

    def test_every_environment_weighs_and_lays_known_effects(self):
        for environment in conditions.ENVIRONMENTS.values():
            self.assertLessEqual(set(environment.weights) | set(environment.degrees), set(surface.CATALOGUE),
                                 environment.name)
            self.assertLessEqual(set(environment.degrees), set(environment.weights), environment.name)

    def test_a_degree_never_passes_one(self):
        # Neglected dust (0.8) in a dry interior (1.3) would pass 1.
        self.assertEqual(1.0, planned(conditions.NEGLECTED, environment=conditions.DRY_INTERIOR)["dust"])


# Rows of the spec's table (§ 3.5.3): (effect, family, weight).
TABLE = [("rust", "ferrous", 1.0), ("rust", "cuprous", 0.0), ("rust", "wood", 0.0), ("rust", "paint_film", 0.0),
         ("verdigris", "cuprous", 1.0), ("verdigris", "ferrous", 0.0), ("oxidation", "noble", compat.POSSIBLE),
         ("fraying", "textile", 1.0), ("fraying", "leather", compat.POSSIBLE), ("fraying", "stone", 0.0),
         ("cracks", "stone", 1.0), ("cracks", "wood", compat.POSSIBLE), ("cracks", "textile", 0.0),
         ("craquelure", "varnish_film", 1.0), ("chalking", "paint_film", 1.0), ("chalking", "varnish_film", 0.0),
         ("fading", "wood", 1.0), ("fading", "ferrous", 0.0), ("burn", "wood", 1.0), ("burn", "glass", 0.0),
         ("moss", "stone", 1.0), ("moss", "glass", compat.POSSIBLE), ("moss", "noble", 0.0),
         ("mold", "paper", 1.0), ("mold", "ferrous", 0.0), ("edge_wear", "glass", 1.0), ("rust", None, 1.0),
         ("burn", "plant", 1.0), ("mold", "plant", compat.POSSIBLE), ("moss", "liquid", 0.0), ("rust", "plant", 0.0)]


class CompatTest(unittest.TestCase):
    def test_the_compatibility_matrix_names_known_effects_and_families(self):
        for name, (compatible, possible) in compat.MATRIX.items():
            self.assertIn(name, surface.CATALOGUE, name)
            self.assertLessEqual(compatible | possible, set(compat.FAMILIES), name)
            self.assertFalse(compatible & possible, name)

    def test_the_matrix_says_what_the_spec_s_table_says(self):
        for effect, family, weight in TABLE:
            self.assertEqual(weight, compat.weight(effect, family), (effect, family))


if __name__ == "__main__":
    unittest.main()
