"""Paints HyColony's hand-built models (spec 2026-10-02 hut models: hut blocks, build goggles, build tool) and draws
their icons.

Each model is built in Blockbench (docs/research/hytale-models.md) and exported to
plugin/src/main/resources/Common/<MODEL>.blockymodel, MODEL being declared by its module; this script never writes
it. It paints the model's texture next to it (<MODEL>.png), island by island (tools/vanilla/paint.py), with the
module's materials and brushes, bakes the model's light into it (tools/vanilla/bake.py), and draws the item icon
(Icons/Items/HyColony/<ICON>.png) from the model, seen from +x +z (a block's front, +z, faces the player who placed
it), or from the module's ICON_VIEW (a held tool, laid diagonally as Hytale's tool icons). Run once after changing a
model or its materials, then commit the outputs. Needs Python 3.10+ and Pillow.

    python tools/huts/generate.py [path/to/Assets.zip]
"""

import json
import math
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.append(str(TOOLS / "common"))
import build_tool  # noqa: E402
import builder  # noqa: E402
import goggles  # noqa: E402
import town_hall  # noqa: E402
from bake import light  # noqa: E402
from models import add, corners, placed, rotate  # noqa: E402
from pack import GRADLE_ASSETS, ICON_SIZE, ROOT, Assets, draw_model, save_png, screen, validate_pack  # noqa: E402
from paint import bleed, islands, paint  # noqa: E402
from PIL import Image  # noqa: E402

RESOURCES = ROOT / "plugin" / "src" / "main" / "resources"
MODELS = (builder, town_hall, goggles, build_tool)
ICON_MARGIN = 3


def main():
    assets = Assets(Path(sys.argv[1]) if len(sys.argv) > 1 else GRADLE_ASSETS)
    for module in MODELS:
        model = RESOURCES / "Common" / (module.MODEL + ".blockymodel")
        nodes = json.loads(model.read_text(encoding="utf-8"))["nodes"]
        image = texture(module, nodes, assets)
        save_png(image, model.with_suffix(".png"))
        icon = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
        view = getattr(module, "ICON_VIEW", None)
        draw_model(icon, nodes, image, *icon_frame(nodes, view), view)
        save_png(icon, RESOURCES / "Common/Icons/Items/HyColony" / (module.ICON + ".png"))
    validate_pack(assets, RESOURCES)


def texture(module, nodes, assets):
    """The model's texture: its materials painted island by island, its light baked from the model (bake.light),
    each island's border bled into its gap."""
    # Blockbench names a group's later cubes '<cube>--C<n>': the materials read the name before it.
    painted = paint(nodes, texture_size(nodes), module.tiles(assets),
                    lambda name, side: module.material(name.split("--")[0], side), pictures=module.PICTURES)
    return bleed(light(painted, nodes, grounded=module.MODEL.startswith("Blocks/")), nodes)


def texture_size(nodes):
    """The smallest texture holding every island, each side a multiple of 32 (Hytale's rule)."""
    rects = list(islands(nodes))
    return tuple(32 * math.ceil(max(r[i] + r[i + 2] for r in rects) / 32) for i in (2, 3))


def icon_frame(nodes, view=None):
    """(scale, origin) fitting the model's view (pack.screen) in the icon, ICON_MARGIN pixels from its edges."""
    points = []
    for n, position, rotation in placed(nodes):
        shape = n["shape"]
        offset, stretch = (tuple(shape[key][a] for a in "xyz") for key in ("offset", "stretch"))
        for corner in corners(shape):
            point = add(position, rotate(rotation, tuple(o + c * s for o, c, s in zip(offset, corner, stretch))))
            points.append(screen(point, 1.0, (0, 0), view))
    low = [min(s[i] for s in points) for i in (0, 1)]
    high = [max(s[i] for s in points) for i in (0, 1)]
    scale = (ICON_SIZE - 2 * ICON_MARGIN) / max(high[0] - low[0], high[1] - low[1])
    origin = tuple(ICON_SIZE / 2 - (low[i] + high[i]) / 2 * scale for i in (0, 1))
    return scale, origin


if __name__ == "__main__":
    main()
