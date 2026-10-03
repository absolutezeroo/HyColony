"""Checks of the wood species, metals, textiles and stones with their finishes (spec 2026-10-03 blockpaint surfaces,
substrate and finish): python tools/blockpaint/check_materials.py (no assets needed). An AssertionError names the
case."""

import unittest

import critique
import metalwork
import stones
import textiles
import woods

NOISE = {"trop bruité", "bruit uniforme", "micro dominant"}


def every():
    """(name, brush, family, textile) of every material and finish."""
    out = [(f"{s}/{f}", woods.timber(s, f), "wood", False) for s in woods.SPECIES for f in woods.FINISHES]
    out += [(f"{m}/{f}", metalwork.metal(m, f), metalwork.METALS[m].family, False) for m in metalwork.METALS
            for f in metalwork.FINISHES]
    out += [(n, f(), "textile", True) for n, f in textiles.TEXTILES.items()]
    out += [(f"{s}/{f}", stones.stone(s, f), stones.STONES[s].family, False) for s in stones.STONES
            for f in stones.FINISHES]
    return out


def mean(image):
    px = list(image.get_flattened_data())
    return tuple(sum(p[k] for p in px) / len(px) for k in range(3))


class MaterialsTest(unittest.TestCase):
    def test_every_material_paints_its_island_opaque_steadily_and_carries_its_family(self):
        for name, brush, family, _ in every():
            for w, h in ((16, 16), (5, 1), (1, 7)):
                image = brush(w, h, "front")
                self.assertEqual((w, h), image.size, name)
                self.assertEqual({255}, {p[3] for p in image.get_flattened_data()}, name)
                self.assertEqual(image.tobytes(), brush(w, h, "front").tobytes(), name)
            self.assertEqual(family, brush.family, name)

    def test_no_material_paints_decorative_noise(self):
        # Spec § 2.1, measured by critique.py. A weave reads as micro, as Hytale's own cloth does (micro share 2.2
        # to 2.4 on its cloth blocks): structure, not noise.
        for name, brush, _, textile in every():
            found = set(critique.verdicts(critique.measure(brush(16, 16, "front"))))
            allowed = {"micro dominant"} if textile else set()
            self.assertFalse(found & NOISE - allowed, name)

    def test_each_finish_paints_its_own_surface(self):
        for species in woods.SPECIES:
            looks = {woods.timber(species, f)(16, 16, "front").tobytes() for f in woods.FINISHES}
            self.assertEqual(len(woods.FINISHES), len(looks), species)
        for name in metalwork.METALS:
            looks = {metalwork.metal(name, f)(16, 16, "front").tobytes() for f in metalwork.FINISHES}
            self.assertEqual(len(metalwork.FINISHES), len(looks), name)

    def test_each_material_keeps_to_its_colour(self):
        for species, kind in woods.SPECIES.items():
            self.assertColourNear(mean(woods.timber(species)(16, 16, "front")), kind.rgb, species)
        for name, kind in metalwork.METALS.items():
            self.assertColourNear(mean(metalwork.metal(name)(16, 16, "front")), kind.rgb, name)

    def assertColourNear(self, colour, rgb, name):
        self.assertTrue(all(abs(c / r - 1) < 0.2 for c, r in zip(colour, rgb)), name)

    def test_pine_has_dark_knots_and_split_wood_no_seams(self):
        pine = woods.timber("pine")(40, 8, "front")
        base = sum(woods.SPECIES["pine"].rgb)
        self.assertTrue(any(sum(p[:3]) < 0.85 * base for p in pine.get_flattened_data()), "no knot")
        planed = woods.timber("oak", "planed", plank=4)(16, 16, "front")
        split = woods.timber("oak", "split", plank=4)(16, 16, "front")

        def row(image, y):
            return sum(sum(image.getpixel((x, y))[:3]) for x in range(16))

        self.assertLess(row(planed, 3), row(planed, 2))
        self.assertLess(abs(row(split, 3) - row(split, 2)), abs(row(planed, 3) - row(planed, 2)))

    def test_marble_has_veins_and_granite_grains(self):
        marble = stones.stone("marble")(32, 32, "front")
        self.assertTrue(any(sum(p[:3]) < sum(stones.STONES["marble"].rgb) * 0.9 for p in marble.get_flattened_data()))
        granite = stones.stone("granite")(16, 16, "front")
        self.assertTrue(any(sum(p[:3]) < sum(stones.STONES["granite"].rgb) * 0.8 for p in granite.get_flattened_data()))

    def test_brushed_metal_runs_along_its_long_side(self):
        brushed = metalwork.metal("steel", "brushed")(24, 8, "top")

        def steps(dx, dy):
            pairs = [(brushed.getpixel((x, y)), brushed.getpixel((x + dx, y + dy)))
                     for x in range(24 - dx) for y in range(8 - dy)]
            return sum(sum(abs(a[k] - b[k]) for k in range(3)) for a, b in pairs) / len(pairs)

        self.assertLess(steps(1, 0), steps(0, 1))


if __name__ == "__main__":
    unittest.main()
