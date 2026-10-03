"""Checks of the huts' shared brushes (materials.py): python tools/huts/check_materials.py (no assets needed). An
AssertionError names the case."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "blockpaint"))
import materials  # noqa: E402


def red(image, x, y):
    return image.getpixel((x, y))[0]


class StavesTest(unittest.TestCase):
    def test_a_bucket_s_staves_stand_upright_their_joints_darkest(self):
        side = materials.staves(materials.BUCKET_WOOD)(12, 6, "front")
        width = materials.STAVE
        for y in range(6):
            for x in range(width - 1, 12, width):
                self.assertLess(red(side, x, y), red(side, x - 1, y), (x, y))
                if x + 1 < 12:
                    self.assertLess(red(side, x, y), red(side, x + 1, y), (x, y))

    def test_each_stave_is_lit_on_its_left_and_has_its_own_shade(self):
        side = materials.staves(materials.BUCKET_WOOD)(12, 6, "front")
        self.assertGreater(red(side, 0, 0), red(side, 1, 0))
        self.assertGreater(len({red(side, x, 0) for x in range(0, 12, materials.STAVE)}), 1)

    def test_the_staves_end_grain_on_top_is_lighter(self):
        brush = materials.staves(materials.BUCKET_WOOD)
        top, side = brush(12, 2, "top"), brush(12, 2, "front")
        self.assertGreater(sum(red(top, x, 0) for x in range(12)), sum(red(side, x, 0) for x in range(12)))


class FamilyTest(unittest.TestCase):
    def test_the_shared_brushes_carry_their_family(self):
        self.assertEqual(("wood", "textile", "plant"), (materials.staves(materials.BUCKET_WOOD).family,
                                                       materials.rope().family, materials.leaf((86, 148, 58)).family))


if __name__ == "__main__":
    unittest.main()
