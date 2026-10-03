"""Checks of critique.py (spec 2026-10-03 blockpaint surfaces, validation): python tools/blockpaint/check_critique.py.
The Hytale cases read the assets archive (pack.GRADLE_ASSETS) and are skipped without it; nothing of Hytale is copied
into the repository."""

import io
import json
import unittest
import zipfile

from PIL import Image

import brushes
import critique
from pack import GRADLE_ASSETS

# Hytale's furniture sets the budgets were measured on.
FURNITURE = "Common/Blocks/Decorative_Sets/"
SETS = ("Ancient", "Feran", "Village", "Crude", "Human", "Christmas", "Lumberjack", "Kweebec", "Temple")


def flat(w, h, rgb):
    return Image.new("RGBA", (w, h), (*rgb, 255))


def white_noise(w, h):
    image = Image.new("RGBA", (w, h))
    for x in range(w):
        for y in range(h):
            k = 1 + 0.5 * brushes.jitter(x * 31 + y * 17, 5)
            image.putpixel((x, y), brushes.coloured((150, 104, 62), k))
    return image


class CritiqueTest(unittest.TestCase):
    def test_a_flat_island_is_too_flat(self):
        self.assertIn("trop plat", critique.verdicts(critique.measure(flat(12, 12, (150, 104, 62)))))

    def test_white_noise_is_noisy_uniform_and_micro_dominant(self):
        found = critique.verdicts(critique.measure(white_noise(12, 12)))
        self.assertIn("trop bruité", found)
        self.assertIn("bruit uniforme", found)
        self.assertIn("micro dominant", found)

    def test_an_island_with_a_transparent_texel_is_not_measured(self):
        image = flat(8, 8, (150, 104, 62))
        image.putpixel((0, 0), (0, 0, 0, 0))
        self.assertIsNone(critique.measure(image))

    def test_shadows_turning_towards_red_pass_and_shadows_turning_blue_do_not(self):
        warm = [(150, 104, 62, 255)] * 10 + [(90, 52, 34, 255)] * 10
        cold = [(150, 104, 62, 255)] * 10 + [(70, 74, 90, 255)] * 10
        self.assertTrue(critique.darker_turns(warm))
        self.assertFalse(critique.darker_turns(cold))

    @unittest.skipUnless(GRADLE_ASSETS.exists(), "no Hytale assets archive")
    def test_each_verdict_falls_on_about_one_hytale_furniture_island_in_ten(self):
        # The budgets come from these islands (docs/research/blockpaint-surfaces.md § 2).
        measures = []
        with zipfile.ZipFile(GRADLE_ASSETS) as z:
            names = set(z.namelist())
            models = [n for n in names if n.endswith(".blockymodel") and any(f"{FURNITURE}{s}/" in n for s in SETS)]
            for name in sorted(models):
                texture = name[:-len(".blockymodel")] + "_Texture.png"
                if texture in names:
                    nodes = json.loads(z.read(name))["nodes"]
                    image = Image.open(io.BytesIO(z.read(texture))).convert("RGBA")
                    measures += [critique.measure(image.crop((u, v, u + w, v + h)))
                                 for _, _, u, v, w, h in critique.islands_of(nodes)
                                 if u + w <= image.width and v + h <= image.height]
        measures = [m for m in measures if m]
        self.assertGreater(len(measures), 1000)
        # Each verdict falls where its centile says: about one island in ten, one in twenty for the shadows (§ 2.1).
        bounds = {"trop plat": 0.07, "trop bruité": 0.07, "bruit uniforme": 0.07, "micro dominant": 0.07,
                  "ombres sans virage": 0.03}
        for verdict, least in bounds.items():
            share = sum(verdict in critique.verdicts(m) for m in measures) / len(measures)
            self.assertTrue(least < share < 0.13, f"{verdict}: {share:.1%}")

    def test_jumps_alone_make_an_island_too_noisy(self):
        # Checks of two shades 13 apart: a mean step under the warm budget, but a jump between every two texels.
        image = flat(12, 12, (150, 104, 62))
        for x in range(12):
            for y in range(12):
                if (x + y) % 2:
                    image.putpixel((x, y), (163, 117, 75, 255))
        m = critique.measure(image)
        self.assertLess(m.step, critique.BUDGETS["warm"].step)
        self.assertIn("trop bruité", critique.verdicts(m))

    def test_an_island_without_neighbours_is_not_measured(self):
        self.assertIsNone(critique.measure(flat(1, 1, (150, 104, 62))))


if __name__ == "__main__":
    unittest.main()
