"""Renders a fish module's fins (FANS, FINS) alone at 8x on a blue ground, like fins.py does for Hytale's."""

import importlib
import os
import sys

from PIL import Image

from fanfin import fan

BASE = os.path.dirname(os.path.abspath(__file__))
mod = importlib.import_module(sys.argv[1])
names = [n for n in mod.FANS if not n.startswith("R-")]
tiles = []
for name in names:
    w, h = mod.FINS[name]
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for i in range(w):
        for j in range(h):
            p = fan(mod.FANS[name], (i + 0.5) / w, (j + 0.5) / h, w, h)
            if p:
                img.putpixel((i, j), (*p[0], p[1]))
    tiles.append(img.resize((w * 8, h * 8), Image.NEAREST))
sheet = Image.new("RGBA", (sum(t.width for t in tiles) + 8 * len(tiles), max(t.height for t in tiles)), (40, 60, 90, 255))
x = 0
for t in tiles:
    sheet.alpha_composite(t, (x, 0))
    x += t.width + 8
sheet.save(os.path.join(BASE, f"finsheet_{sys.argv[1]}.png"))
print(names)
