"""Checks of the semantic critique (spec 2026-10-03 blockpaint surfaces § 3.9): python
tools/blockpaint/check_semantics.py (no assets needed). Each question answers yes on the demonstration crate and no on
a crate rigged to fail it. An AssertionError names the case."""

import unittest

from PIL import Image

import conditions
import deposits
import effects
import roles
import semantics
from check_effects import DECLARED, crate_model, crate_module

OUTDOORS = dict(DECLARED, CONDITION=conditions.NEGLECTED, ENVIRONMENT=conditions.TEMPERATE_OUTDOOR)


class SemanticsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.module = crate_module(**OUTDOORS)
        cls.records = semantics.records_of(cls.module, crate_model())

    def declared(self, part):
        return roles.declared(part, self.module.ROLES, self.module.USAGE, self.module.FOCUS)

    def test_the_demonstration_crate_answers_yes_wherever_a_question_applies(self):
        for answer in semantics.answers(self.module, crate_model()):
            self.assertIsNot(answer.ok, False, answer)
        self.assertTrue(semantics.dirt_low(self.records).ok, "the neglected crate outdoors has dirt")

    def fresh(self):
        """Records of their own, for a test to rig."""
        return semantics.records_of(self.module, crate_model())

    def test_dirt_at_the_top_is_seen(self):
        records = self.fresh()
        for record in records:
            record[3].zones.pop("dirt", None)
        lid = next(r for r in records if r[:2] == ("Lid", "top"))
        lid[3].zones["dirt"] = set(lid[3].texels)
        self.assertFalse(semantics.dirt_low(records).ok)

    def test_moss_in_the_driest_places_is_seen_and_in_the_dampest_is_not(self):
        records = self.fresh()
        texels = sorted(((deposits.damp(r[3].texels[c], r[3], *c), id(r), c) for r in records for c in r[3].texels))
        for record in records:
            record[3].zones["moss"] = set()
        by_id = {id(r): r for r in records}
        for _, key, cell in texels[:20]:
            by_id[key][3].zones["moss"].add(cell)
        self.assertFalse(semantics.moss_damp(records).ok)
        for record in records:
            record[3].zones["moss"] = set()
        for _, key, cell in texels[-20:]:
            by_id[key][3].zones["moss"].add(cell)
        self.assertTrue(semantics.moss_damp(records).ok)

    def test_wear_on_untouched_parts_only_is_seen(self):
        # The body is the touched part (DECLARED's usage): all wear moved off it, onto every other part.
        records = self.fresh()
        for part, side, rect, island, image in records:
            island.zones["edge_wear"] = set(island.texels) if part != "Body" else set()
            island.zones["chips"] = set()
        self.assertFalse(semantics.touched_worn(records, self.declared).ok)

    def test_rust_off_bare_iron_is_seen(self):
        records = self.fresh()
        body = next(r for r in records if r[:2] == ("Body", "front"))
        body[3].zones["rust"] = set(body[3].texels)
        self.assertFalse(semantics.rust_on_bare_iron(records).ok)

    def test_grain_across_the_declared_axis_is_seen(self):
        self.assertTrue(semantics.grain_along_axes(self.records, {"Body": "y"}).ok)
        self.assertFalse(semantics.grain_along_axes(self.records, {"Body": "x", "Lid": "z"}).ok)

    def test_a_focus_duller_than_the_rest_is_seen(self):
        records = self.fresh()
        for record in records:
            if record[0] == "Lid":
                record[4].paste((120, 90, 60, 255), (0, 0, *record[4].size))
        self.assertFalse(semantics.focus_dominant(records, ("Lid",)).ok)
        self.assertIsNone(semantics.focus_dominant(records, ()).ok)

    def test_a_zone_too_small_to_read_from_afar_is_seen(self):
        records = self.fresh()
        # Two texels astride four blocks: one each, under half of any block.
        records[0][3].zones["dust"] = {(1, 1), (2, 2)}
        self.assertFalse(semantics.readable(records).ok)
        records[0][3].zones["dust"] = {(1, 1), (2, 2), (3, 3), (4, 4)}
        self.assertTrue(semantics.seen(records[0][3].zones["dust"]))

    def test_rust_on_bare_iron_away_from_the_weather_is_seen(self):
        records = self.fresh()
        bare = [(r, c) for r in records for c in r[3].texels if r[3].bare("ferrous", *c)]
        self.assertTrue(bare, "the crate has bare iron")
        for record in records:
            record[3].zones["rust"] = set()
        sheltered = min(semantics.maps.exposure(r[3].texels[c]) for r, c in bare)
        for record, cell in bare:
            if semantics.maps.exposure(record[3].texels[cell]) == sheltered:
                record[3].zones["rust"].add(cell)
        self.assertFalse(semantics.rust_on_bare_iron(records).ok)

    def test_two_parts_apart_are_not_neighbours(self):
        self.assertTrue(semantics.near([(0, 0, 0), (1, 1, 1)], [(1.5, 0, 0)]))
        self.assertFalse(semantics.near([(0, 0, 0), (1, 1, 1)], [(2, 0, 0)]))

    def test_two_materials_of_one_colour_but_different_grain_are_distinct(self):
        flat = (lambda w, h, side: Image.new("RGBA", (w, h), (130, 100, 70, 255)))

        def striped(w, h, side):
            # Columns 40 apart around flat's colour: the same mean, a far coarser grain.
            image = Image.new("RGBA", (w, h), (110, 80, 50, 255))
            for x in range(0, w, 2):
                for y in range(h):
                    image.putpixel((x, y), (150, 120, 90, 255))
            return image

        same = crate_module(**dict(OUTDOORS, CONDITION=conditions.PRISTINE))
        same.tiles = lambda assets: {"wood": effects.material(flat, family="wood"),
                                     "iron": effects.material(striped, family="ferrous")}
        records = semantics.records_of(same, crate_model())
        self.assertTrue(semantics.materials_distinct(records, same.material).ok)

    def test_two_neighbouring_materials_alike_are_seen(self):
        flat = (lambda w, h, side: Image.new("RGBA", (w, h), (120, 90, 60, 255)))
        # Pristine: no rust nor wear to tell them apart either.
        same = crate_module(**dict(OUTDOORS, CONDITION=conditions.PRISTINE))
        same.tiles = lambda assets: {"wood": effects.material(flat, family="wood"),
                                     "iron": effects.material(flat, family="ferrous")}
        records = semantics.records_of(same, crate_model())
        self.assertFalse(semantics.materials_distinct(records, same.material).ok)
        self.assertTrue(semantics.materials_distinct(self.records, self.module.material).ok)


if __name__ == "__main__":
    unittest.main()
