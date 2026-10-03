"""Checks of the hut tools that need no assets: python tools/huts/check.py. An AssertionError names the case."""

import sys
import unittest
from pathlib import Path

from PIL import Image

sys.path.append(str(Path(__file__).resolve().parents[1] / "common"))
import glint  # noqa: E402
from models import box_shape, node, unwrap  # noqa: E402


def gem_model():
    """A shaft and one gem, unwrapped."""
    nodes = [node("Shaft", (0, 0, 0), box_shape((4, 8, 4), ("front", "back"))),
             node("Gem", (0, 6, 0), box_shape((2, 2, 2), ("front", "back", "left", "right", "top", "bottom")))]
    return nodes, unwrap(nodes)


class GlintTest(unittest.TestCase):
    def test_frames_copy_only_the_gem_islands_below_with_the_glint_moving_across(self):
        nodes, size = gem_model()
        image = Image.new("RGBA", size, (40, 90, 160, 255))
        out, step = glint.frames(image, nodes, "Gem")
        self.assertEqual(0, out.height % 32)
        self.assertGreaterEqual(step, image.height)
        self.assertEqual(image.tobytes(), out.crop((0, 0, *size)).tobytes())
        u, v, w, h = next((u, v, w, h) for name, side, u, v, w, h in glint.islands(nodes)
                          if name == "Gem" and side == "front")
        lit = [[out.getpixel((u + x, v + y + k * step)) != (40, 90, 160, 255) for x in range(w) for y in range(h)]
               for k in range(1, glint.GLINT_FRAMES + 1)]
        self.assertTrue(all(any(frame) for frame in lit))
        self.assertNotEqual(lit[0], lit[-1])
        for _, _, u, v, w, h in (r for r in glint.islands(nodes) if r[0] == "Shaft"):
            for k in range(1, glint.GLINT_FRAMES + 1):
                below = out.crop((u, v + k * step, u + w, v + h + k * step))
                self.assertEqual((0, 0), below.getextrema()[3], f"Shaft copied in frame {k}")

    def test_the_flipbook_holds_each_frame_and_ends_on_the_plain_islands(self):
        keys = glint.flipbook(10)
        times = [k["time"] for k in keys]
        self.assertEqual(sorted(times), times)
        self.assertEqual((0, glint.DURATION), (times[0], times[-1]))
        self.assertEqual(0, keys[-1]["delta"]["y"])
        for k in range(1, glint.GLINT_FRAMES + 1):
            held = [key["time"] for key in keys if key["delta"]["y"] == -10 * k]
            self.assertEqual(glint.GLINT_HOLD, held[-1] - held[0])

    def test_the_animation_names_every_gem_node_and_no_other(self):
        nodes, _ = gem_model()
        self.assertEqual(["Gem"], list(glint.animation(nodes, "Gem", 10)["nodeAnimations"]))

    def test_gems_breathe_unless_told_not_to(self):
        nodes, _ = gem_model()
        self.assertEqual(glint.breath(), glint.animation(nodes, "Gem", 10)["nodeAnimations"]["Gem"]["shapeStretch"])

    def test_a_still_glint_keeps_the_flipbook_without_the_breath(self):
        nodes, _ = gem_model()
        gem = glint.animation(nodes, "Gem", 10, breathe=False)["nodeAnimations"]["Gem"]
        self.assertEqual([], gem["shapeStretch"])
        self.assertEqual(glint.flipbook(10), gem["shapeUvOffset"])


if __name__ == "__main__":
    unittest.main()
