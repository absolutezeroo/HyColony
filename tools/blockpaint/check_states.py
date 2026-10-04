"""Checks of readable states (spec 2026-10-03 blockpaint surfaces § 3.3): a condition's show reaches the effects'
budget, the rest zones' floor, the illustration pass and the dull film past worn; the budget keeps only effects that
can appear. python tools/blockpaint/check_states.py (no assets needed). The demonstration crate (check_effects),
without its history. An AssertionError names the case."""

import colorsys
import unittest
from unittest import mock

from PIL import Image

import art
import catalog
import conditions
import effects
import illustration
import semantics
import surface
from check_effects import DECLARED, crate_model, crate_module

PLAIN = dict(DECLARED, HISTORY=())


def records(condition):
    """The layered records of the crate painted in condition."""
    return semantics.records_of(crate_module(**dict(PLAIN, CONDITION=condition)), crate_model())


def saturation(images):
    """The mean saturation of the opaque texels of images."""
    values = [colorsys.rgb_to_hsv(*(c / 255 for c in p[:3]))[1] for image in images
              for p in image.get_flattened_data() if p[3]]
    return sum(values) / len(values)


class ShowTest(unittest.TestCase):
    def test_each_condition_shows_its_state_from_maintained_to_ruined(self):
        self.assertEqual((0, 0, 1, 2, 3), tuple(c.show for c in (
            conditions.MAINTAINED, conditions.USED, conditions.WORN, conditions.NEGLECTED, conditions.RUINED)))

    def test_each_step_of_show_keeps_more_effects_on_the_crate_s_islands(self):
        # The rest floor held at REST for all, so only the budget tells them apart.
        with mock.patch("art.rest_floor", return_value=art.REST):
            kept = [sum(len(r[3].zones) for r in records(conditions.NEGLECTED._replace(show=show)))
                    for show in (0, 1, 2)]
        self.assertLess(kept[0], kept[1])
        self.assertLess(kept[1], kept[2])

    def test_the_rest_floor_is_raised_by_the_condition_s_show(self):
        with mock.patch("art.rest_floor", wraps=art.rest_floor) as floor:
            records(conditions.NEGLECTED)
        self.assertIn(mock.call(conditions.NEGLECTED.show), floor.call_args_list)

    def test_the_illustration_pass_is_told_the_condition_s_show(self):
        told = []
        for condition in (conditions.NEGLECTED, conditions.RUINED):
            module = crate_module(**dict(PLAIN, CONDITION=condition, ILLUSTRATION=illustration.Illustration()))
            with mock.patch("paint.illustrate", lambda image, *rest: told.append(rest[-1]) or image):
                catalog.model_texture(module, crate_model(), None)
        self.assertEqual([conditions.NEGLECTED.show, conditions.RUINED.show], told)

    def test_effects_that_reach_nothing_leave_their_place_so_dirt_settles_under_the_feet(self):
        # Neglected dirt in a dry interior is weak (0.7 x 0.3): more useful effects that reach nothing on the feet's
        # undersides (dust on a face turned down) would fill the budget before it. Left out, they make room for it.
        self.assertTrue(any(r[3].zones.get("dirt") for r in records(conditions.NEGLECTED)))

    def test_an_effect_reaching_nothing_yet_is_kept_when_a_kept_effect_writes_what_it_reads(self):
        # Rust on metal that wear will lay bare: nothing is bare before the run, wear (kept) makes it so.
        wear = effects.Effect("wear", "degrade", None, None, writes=("bare",))
        rust = effects.Effect("rust", "degrade", None, None, reads=("bare",))
        with mock.patch("effects.reached", return_value={}):
            self.assertTrue(surface.reachable(rust, 0.5, None, None, [(wear, 0.5)]))
            self.assertFalse(surface.reachable(rust, 0.5, None, None, []))


class DullTest(unittest.TestCase):
    def test_the_film_veils_a_grey_towards_its_dust_and_grime(self):
        grey = (200, 200, 200, 255)
        image = surface.dulled(Image.new("RGBA", (1, 1), grey), conditions.NEGLECTED.show)
        pixel = image.getpixel((0, 0))

        def far(p):
            return sum(abs(a - b) for a, b in zip(p[:3], surface.FILM))

        self.assertLess(far(pixel), far(grey))

    def test_a_neglected_island_s_base_is_dulled_too(self):
        # The illustration pass brings rest areas back towards the base: an undulled base would bring their colour back.
        clean = saturation(r[3].base for r in records(conditions.PRISTINE))
        dull = saturation(r[3].base for r in records(conditions.PRISTINE._replace(show=2)))
        self.assertLess(dull, clean * 0.9)

    def test_an_effect_s_texels_keep_their_colour_under_the_film(self):
        image = Image.new("RGBA", (2, 1), (180, 90, 40, 255))
        surface.dulled(image, conditions.NEGLECTED.show, {(0, 0)})
        self.assertEqual((180, 90, 40, 255), image.getpixel((0, 0)))
        self.assertNotEqual((180, 90, 40, 255), image.getpixel((1, 0)))


if __name__ == "__main__":
    unittest.main()
