"""Checks of the material brushes (organic.py, masonry.py, metals.py, misc.py): python tools/blockpaint/check_brushes.py
(no assets needed). An AssertionError names the case."""

import unittest

import brushes
import compat
import critique
import masonry
import metals
import misc
import organic

BRUSHES = {
    "leather": organic.leather((122, 80, 50)), "skin": organic.skin((224, 178, 142)),
    "hair": organic.hair((96, 62, 40)),
    "fur": organic.fur((152, 120, 88)), "bone": organic.bone(), "horn": organic.horn((104, 92, 78)),
    "scales": organic.scales((72, 124, 82)), "feathers": organic.feathers((184, 64, 52)),
    "brick": masonry.brick((162, 82, 62)), "plaster": masonry.plaster((226, 218, 200)),
    "concrete": masonry.concrete((150, 148, 142)), "sand": masonry.sand((214, 190, 136)),
    "mud": masonry.mud((112, 82, 56)), "mossy_stone": masonry.mossy_stone((128, 124, 116)),
    "gold": metals.gold(), "bronze": metals.bronze(), "copper": metals.copper(),
    "painted_metal": metals.painted_metal((60, 96, 150)), "rusty_metal": metals.rusty_metal(),
    "glass": misc.glass((150, 196, 210)), "rubber": misc.rubber((52, 50, 54)), "wax": misc.wax((232, 214, 170)),
    "charred_wood": misc.charred_wood((130, 92, 58)), "rope": misc.rope((206, 180, 128)),
}
SIDES = ("front", "top", "left", "bottom")
# The colour each brush was given, for those whose face keeps to it on average: brick, feathers, mossy stone and
# charred wood are pulled away by mortar, shaft, moss and char; gold, bronze, copper and rusty metal take no single
# colour.
GIVEN = {"painted_metal": (60, 96, 150), "leather": (122, 80, 50), "skin": (224, 178, 142), "hair": (96, 62, 40),
         "fur": (152, 120, 88), "bone": (226, 214, 182), "horn": (104, 92, 78), "scales": (72, 124, 82),
         "plaster": (226, 218, 200), "concrete": (150, 148, 142), "sand": (214, 190, 136), "mud": (112, 82, 56),
         "glass": (150, 196, 210), "rubber": (52, 50, 54), "wax": (232, 214, 170), "rope": (206, 180, 128)}


def colours(image):
    return [image.getpixel((x, y)) for x in range(image.width) for y in range(image.height)]


def mean(pixels):
    return tuple(sum(p[i] for p in pixels) / len(pixels) for i in range(3))


class BrushTest(unittest.TestCase):
    def test_every_brush_paints_its_island_opaque_and_the_same_every_time(self):
        for name, brush in BRUSHES.items():
            for side in SIDES:
                for w, h in ((7, 11), (16, 16), (1, 5), (5, 1)):
                    image = brush(w, h, side)
                    self.assertEqual((w, h), image.size, name)
                    self.assertTrue(all(p[3] == 255 for p in colours(image)), f"{name} not opaque")
                    self.assertEqual(image.tobytes(), brush(w, h, side).tobytes(), f"{name} not deterministic")

    def test_every_material_brush_carries_a_known_family(self):
        core = {"metal": brushes.metal((150, 154, 162)), "wood": brushes.wood((150, 104, 62)),
                "crystal": brushes.crystal((200, 240, 255), (120, 180, 220), (60, 90, 140)),
                "paper": brushes.paper((236, 226, 200)), "cloth": brushes.cloth((150, 60, 50)),
                "stone": brushes.stone((128, 124, 116)), "ore": brushes.ore((110, 106, 100), (200, 160, 60)),
                "terracotta": brushes.terracotta((180, 100, 70)), "embers": brushes.embers(),
                "clay": brushes.clay((160, 120, 90))}
        for name, brush in {**BRUSHES, **core}.items():
            self.assertIn(getattr(brush, "family", None), compat.FAMILIES, name)

    def test_glowing_brushes_are_drawings_their_glow_never_calmed(self):
        self.assertTrue(brushes.embers().drawn)
        self.assertTrue(brushes.crystal((200, 240, 255), (120, 180, 220), (60, 90, 140)).drawn)
        self.assertEqual("glass", brushes.crystal((200, 240, 255), (120, 180, 220), (60, 90, 140)).family)
        self.assertFalse(getattr(brushes.stone((128, 124, 116)), "drawn", False))

    def test_every_brush_varies_across_a_face_but_keeps_to_its_colour(self):
        for name, brush in BRUSHES.items():
            pixels = colours(brush(16, 16, "front"))
            self.assertGreater(len(set(pixels)), 3, f"{name} paints a flat colour")
        for name, rgb in GIVEN.items():
            average = mean(colours(BRUSHES[name](16, 16, "front")))
            for k in range(3):
                self.assertLess(abs(average[k] / rgb[k] - 1), 0.2, f"{name} strays from its colour")

    def test_no_brush_paints_decorative_noise(self):
        # Spec 2026-10-03 blockpaint surfaces § 2.1, measured by critique.py against Hytale's furniture. Brick's one
        # texel mortar lines read as micro, as Hytale's own brick blocks do (micro share 1.07 to 1.27): structure,
        # not noise.
        for name, brush in BRUSHES.items():
            found = set(critique.verdicts(critique.measure(brush(16, 16, "front"))))
            allowed = {"micro dominant"} if name == "brick" else set()
            self.assertFalse(found & {"trop bruité", "bruit uniforme", "micro dominant"} - allowed, name)

    def test_wood_reads_as_boards_a_light_bevel_beside_each_dark_seam(self):
        # Ten boards of 6: a row's fibres even out over them, the seam (a quarter darker) and the bevel stand out
        # against the texel row next to them far more than the board's rounding does (a twenty-fifth a row).
        image = brushes.wood((150, 104, 62), plank=6)(60, 60, "front")

        def row(y):
            return sum(sum(image.getpixel((x, y))[:3]) for x in range(60)) / 60

        seams = [row(6 * k + 4) - row(6 * k + 5) for k in range(10)]
        bevels = [row(6 * k) - row(6 * k + 1) for k in range(1, 10)]
        self.assertGreater(sum(seams) / len(seams), 40, "a dark seam ends each board")
        self.assertGreater(sum(bevels) / len(bevels), 20, "a light bevel starts the next")

    def test_an_island_no_wider_than_a_board_is_one_board_rounded_across(self):
        # Posts 5 and 6 wide, their grain up their length: rounded across their whole width, the left edge lighter than
        # the right by the whole of BOARD_ROUND (a rounding spread over a full plank would stop part of the way).
        rgb = (150, 104, 62)
        for width in (5, 6):
            image = brushes.wood(rgb)(width, 24, "front")

            def column(x):
                return sum(sum(image.getpixel((x, y))[:3]) for y in range(24)) / 24

            self.assertGreater((column(0) - column(width - 1)) / sum(rgb), 0.9 * brushes.BOARD_ROUND, width)

    def test_a_stone_crack_breaks_in_runs_not_texel_by_texel(self):
        image = brushes.stone((128, 124, 116))(96, 96, "front")
        whole = total = 0
        # Every third row is a crack row (chunk height 3): a cracked texel is darker than the one under it, in its
        # chunk. A crack is decided for each run of three texels, so a run is cracked whole or not at all: about 0.8 of
        # the runs agree here (the texels' own noise blurs some), 0.43 when decided texel by texel.
        for y in range(0, 93, 3):
            cracked = [sum(image.getpixel((x, y))[:3]) < sum(image.getpixel((x, y + 1))[:3]) - 40 for x in range(96)]
            for start in range(0, 96, 3):
                # A vertical crack's texel (every 4th column, the rows shifted by 2 in turn) darkens the row under too.
                flags = [cracked[x] for x in range(start, start + 3) if (x + (y // 3 % 2) * 2) % 4]
                if len(flags) > 1:
                    total += 1
                    whole += len(set(flags)) == 1
        self.assertGreaterEqual(whole / total, 0.65)

    def test_the_common_brushes_paint_in_touches_neither_specks_nor_flat(self):
        # micro / (meso + macro) over 16 x 16 windows of Hytale (docs/research/blockpaint-surfaces.md § 8): its
        # furniture's 90th centile 0.90, its grey rock's 1.45. A brush painting texel by texel stood at 1.2 to 1.9; a
        # smooth one falls under critique's floor for its kind (too flat).
        limits = {"stone": 1.45}
        common = {"metal": brushes.metal((150, 154, 162)), "stone": brushes.stone((128, 124, 116)),
                  "cloth": brushes.cloth((150, 60, 50)), "paper": brushes.paper((236, 226, 200)),
                  "wood": brushes.wood((150, 104, 62)), "clay": brushes.clay((160, 120, 90)),
                  "terracotta": brushes.terracotta((180, 100, 70))}
        for name, brush in common.items():
            for (w, h), side in (((16, 16), "front"), ((16, 16), "top"), ((32, 32), "front"), ((8, 24), "front")):
                m = critique.measure(brush(w, h, side))
                case = (name, w, h, side, round(m.micro_share, 2), round(m.step, 1))
                self.assertLess(m.micro_share, limits.get(name, 0.90), case)
                self.assertGreaterEqual(m.step, critique.BUDGETS[m.kind].flat, case)

    def test_skin_never_jumps_from_one_texel_to_the_next(self):
        image = BRUSHES["skin"](16, 16, "front")
        for x in range(15):
            for y in range(16):
                a, b = image.getpixel((x, y)), image.getpixel((x + 1, y))
                self.assertLess(max(abs(a[i] - b[i]) for i in range(3)), 12, (x, y))

    def test_brick_mortar_runs_along_every_course_and_between_bricks_shifted_half_a_brick(self):
        brick, mortar_colour = (162, 82, 62), (196, 188, 170)
        image = masonry.brick(brick, mortar_colour, size=(8, 4))(16, 8, "front")
        # Halfway between the brick's and the mortar's brightness.
        threshold = (sum(brick) + sum(mortar_colour)) / 2

        def mortar(box):
            return all(sum(p[:3]) > threshold for p in colours(image.crop(box)))

        self.assertTrue(mortar((0, 3, 16, 4)), "no mortar under the first course")
        self.assertTrue(mortar((7, 0, 8, 3)), "no joint between the bricks of the first course")
        self.assertTrue(mortar((3, 4, 4, 7)), "the second course is not shifted half a brick")
        self.assertFalse(mortar((7, 4, 8, 7)), "the second course has its joint where the first has")

    def test_moss_grows_more_low_on_side_faces_and_on_top_than_below(self):
        def green(image, box):
            return sum(1 for p in colours(image.crop(box)) if p[1] > p[0] + 15)

        low = high = 0
        for w, h in ((16, 32), (32, 64), (48, 64)):
            image = BRUSHES["mossy_stone"](w, h, "front")
            low += green(image, (0, 3 * h // 4, w, h))
            high += green(image, (0, 0, w, h // 4))
        self.assertGreater(low, 2 * high)
        top, bottom = (green(BRUSHES["mossy_stone"](32, 32, side), (0, 0, 32, 32)) for side in ("top", "bottom"))
        self.assertGreater(top, bottom)

    def test_the_root_of_a_horn_is_darker_than_its_tip(self):
        image = BRUSHES["horn"](24, 6, "front")
        self.assertLess(sum(mean(colours(image.crop((20, 0, 24, 6))))), sum(mean(colours(image.crop((0, 0, 4, 6))))))

    def test_edge_counts_texels_from_the_island_rim(self):
        self.assertEqual([0, 1, 2, 1, 0], [brushes.edge(x, 2, 5, 5) for x in range(5)])
        self.assertEqual(0, brushes.edge(2, 0, 5, 5))

    def test_only_some_bones_are_cracked(self):
        def cracked(length, across):
            # A crack runs half the island's length, so it always reaches its middle half, where the ends do not
            # darken the bone (pores stay above 550).
            image = organic.bone()(length, across, "front")
            middle = range(length // 4, 3 * length // 4)
            return any(sum(image.getpixel((x, y))[:3]) < 550 for x in middle for y in range(across))

        # From 5 across, the crack's wander (2 texels off the middle at most) always stays on the island.
        sizes = [(length, across) for length in range(8, 21) for across in range(5, 8)]
        count = sum(cracked(*size) for size in sizes)
        self.assertTrue(0 < count < len(sizes), count)

    def test_charred_wood_burns_from_below_into_blocks_split_by_cracks(self):
        image = BRUSHES["charred_wood"](16, 32, "front")
        lower, upper = mean(colours(image.crop((0, 16, 16, 32)))), mean(colours(image.crop((0, 0, 16, 16))))
        self.assertLess(sum(lower), sum(upper))
        crack = misc.coloured((40, 32, 30), 0.55)
        cracks = [(x, y) for x in range(16) for y in range(32) if image.getpixel((x, y)) == crack]
        self.assertTrue(cracks, "no cracks")
        for x, y in cracks:
            self.assertTrue(y % 3 == 2 or (x + (y // 3 % 2) * 2) % 4 == 3, (x, y))

    def test_rusty_metal_shows_both_rust_and_bare_metal(self):
        pixels = colours(BRUSHES["rusty_metal"](16, 16, "front"))
        self.assertTrue(any(p[0] > p[2] + 40 for p in pixels), "no rust")
        self.assertTrue(any(abs(p[0] - p[2]) < 15 for p in pixels), "no bare metal")

    def test_gold_bronze_and_copper_are_told_apart(self):
        means = [mean(colours(BRUSHES[name](16, 16, "front"))) for name in ("gold", "bronze", "copper")]
        for i, a in enumerate(means):
            for b in means[i + 1:]:
                self.assertGreater(sum(abs(a[k] - b[k]) for k in range(3)), 40)


if __name__ == "__main__":
    unittest.main()
