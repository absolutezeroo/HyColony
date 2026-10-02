"""Composes the citizen inventory window's background from MC's textures/gui/citizen_container.png (350 x 350), as
MC WindowCitizenInventory.renderBg draws it for 3 rows: the top (citizen's slots) and bottom (player's inventory)
parts, the entity frame at (172, 22) and the 4 armour slots at (222, 22 + 18 i). The frame's inside is left
transparent: there the server camera shows the citizen itself (spec 2026-10-02 citizen inventory, § 4). Scaled x4 to
nearest neighbour, as every MC texture we copy (CLAUDE.md § 7).

Run once, then commit the output (plugin/ resources): the build never runs it.

    python tools/ui/citizen_inventory.py [path/to/sources]
"""

import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SOURCES = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "sources"
SRC = SOURCES / "minecolonies/src/main/resources/assets/minecolonies/textures/gui/citizen_container.png"
OUT = ROOT / "plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/Mc/citizen_inventory@2x.png"

# MC WindowCitizenInventory constants.
WIDTH, ROWS, SLOT = 245, 3, 18
TOP_HEIGHT = 10 + ROWS * SLOT + 12  # the top part MC blits, from (0, 0)
BOTTOM_Y, BOTTOM_HEIGHT = 130, 96  # TEXTURE_OFFSET, TEXTURE_HEIGHT
FRAME, FRAME_AT, FRAME_SIZE = (0, 227), (172, 22), (49, 72)
ARMOR, ARMOR_X, ARMOR_Y = (0, 300), 222, 22
SCALE = 4


def compose(src):
    """The window at MC's size, drawn as renderBg does."""
    out = Image.new("RGBA", (WIDTH, TOP_HEIGHT + BOTTOM_HEIGHT), (0, 0, 0, 0))
    out.paste(src.crop((0, 0, WIDTH, TOP_HEIGHT)), (0, 0))
    out.paste(src.crop((0, BOTTOM_Y, WIDTH, BOTTOM_Y + BOTTOM_HEIGHT)), (0, TOP_HEIGHT))
    fx, fy = FRAME
    out.paste(src.crop((fx, fy, fx + FRAME_SIZE[0], fy + FRAME_SIZE[1])), FRAME_AT)
    ax, ay = ARMOR
    slot = src.crop((ax, ay, ax + SLOT, ay + SLOT))
    for i in range(4):
        out.paste(slot, (ARMOR_X, ARMOR_Y + i * SLOT))
    return out


def open_frame(img):
    """Clears the frame's inside, keeping its 1 px border: the camera's view shows through."""
    x0, y0 = FRAME_AT
    w, h = FRAME_SIZE
    for x in range(x0 + 1, x0 + w - 1):
        for y in range(y0 + 1, y0 + h - 1):
            img.putpixel((x, y), (0, 0, 0, 0))


def main():
    src = Image.open(SRC).convert("RGBA")
    img = compose(src)
    open_frame(img)
    img.resize((img.width * SCALE, img.height * SCALE), Image.NEAREST).save(OUT)
    print(f"{OUT.name}: {img.width} x {img.height} MC px, page size {img.width * 2} x {img.height * 2}")


if __name__ == "__main__":
    main()
