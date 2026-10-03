"""Checks of the coats (spec 2026-10-03 blockpaint surfaces § 3.2): python tools/blockpaint/check_coats.py (no assets
needed). An AssertionError names the case."""

import unittest

import coatings
import compat
import layers
from check_layers import STEEL, flat, front_island


def coats():
    """Every coat of the catalogue, made with its default colour (or a colour for those that need one)."""
    out = {}
    for name, make in coatings.COATS.items():
        try:
            out[name] = make()
        except TypeError:
            out[name] = make((90, 120, 160))
    return out


class CoatsTest(unittest.TestCase):
    def test_every_coat_lies_as_its_mode_says(self):
        for name, coat in coats().items():
            island = front_island(flat(STEEL), (coat,))
            colour = layers.compose(island).getpixel((4, 4))[:3]
            if coat.mode == "cover" and not coat.gloss:
                self.assertEqual(coat.colour, colour, name)
            elif coat.mode == "clear":
                self.assertNotEqual(STEEL, colour, name)
            self.assertEqual(255, layers.compose(island).getpixel((4, 4))[3], name)

    def test_thin_coats_wear_off_before_thick_ones(self):
        c = coats()
        self.assertLess(c["gilding"].thickness, c["plating"].thickness)
        self.assertLess(c["plating"].thickness, c["paint"].thickness)
        self.assertLess(c["paint"].thickness, c["enamel"].thickness)
        dig = 0.5
        gilded = front_island(flat(STEEL), (c["gilding"],))
        painted = front_island(flat(STEEL), (c["paint"],))
        for island in (gilded, painted):
            island.depth[4, 4] = dig
        self.assertTrue(gilded.bare("ferrous", 4, 4))
        self.assertFalse(painted.bare("ferrous", 4, 4))

    def test_every_coat_has_a_known_film(self):
        for name, coat in coats().items():
            self.assertIn(coat.family, compat.FAMILIES, name)

    def test_metal_films_never_rust_but_conversions_on_iron_can(self):
        c = coats()
        for name in ("plating", "gilding", "silvering"):
            self.assertEqual(0.0, compat.weight("rust", c[name].family), name)
        for name in ("blackening", "bluing", "browning", "heat"):
            self.assertEqual(1.0, compat.weight("rust", c[name].family), name)

    def test_an_oil_on_leather_stays_leather(self):
        self.assertEqual("wood", coatings.oil_coat().family)
        self.assertEqual("leather", coatings.oil_coat(family="leather").family)
        self.assertEqual(compat.POSSIBLE, compat.weight("fraying", coatings.oil_coat(family="leather").family))

    def test_a_dye_keeps_the_weave_and_takes_its_family(self):
        self.assertEqual("leather", coatings.dye_coat((120, 40, 30), family="leather").family)
        self.assertEqual("tint", coatings.dye_coat((120, 40, 30)).mode)


if __name__ == "__main__":
    unittest.main()
