"""Paints HyColony's hand-built models (spec 2026-10-02 hut models: hut blocks, build goggles, build tool, clipboard)
and draws their icons, with the shared model tools of tools/common.

Each model is built in Blockbench (docs/research/hytale-models.md; the clipboard, lumberjack and miner by one-off
scripts) and saved to plugin/src/main/resources/Common/<MODEL>.blockymodel; this script never writes it. It paints
the model's texture next to it (<MODEL>.png: paint.texture, the materials' brushes and the light baked from the model)
and draws the item icon (Icons/Items/HyColony/<ICON>.png). Run once after changing a model or its materials (a hut
block's model first through trim.py, which drops its hidden faces and lays it out), then commit the outputs. Needs
Python 3.10+ and Pillow.

A model's module (builder.py, town_hall.py, residence.py, farmer.py, cook.py, courier.py, warehouse.py, lumberjack.py,
miner.py, goggles.py, build_tool.py, clipboard.py; listed in MODELS) declares:
- MODEL: its path under Common, without extension; a block's (under Blocks/) stands on a floor that shades its foot;
- ICON: its icon's name;
- PICTURES: the materials whose tile carries a drawing laid out for its island, never turned;
- material(name, side): the material of a node's face, from the node's name (without Blockbench's '--C<n>');
- tiles(assets): material -> a 32 px tile or a brush (tools/common/brushes.py);
- ICON_VIEW, optional: the icon's view (icons.turned) instead of the isometric one of blocks;
- animation(nodes), optional: the model's looping blockyanim, written next to it (<MODEL>.blockyanim);
- SEE_THROUGH, optional: the nodes lit but casting no baked shadow (shown only part of the time by the animation);
- GLINT, optional: the name prefix of the crystal nodes that breathe and glint (glint.py), instead of animation;
- BREATHE, optional: False for crystals that only glint.

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
import clipboard  # noqa: E402
import cook  # noqa: E402
import courier  # noqa: E402
import farmer  # noqa: E402
import glint  # noqa: E402
import goggles  # noqa: E402
import lumberjack  # noqa: E402
import miner  # noqa: E402
import residence  # noqa: E402
import town_hall  # noqa: E402
import warehouse  # noqa: E402
from bake import light_map  # noqa: E402
from icons import ICON_SIZE, draw_model, frame  # noqa: E402
from pack import GRADLE_ASSETS, ROOT, Assets, save_png, write_json  # noqa: E402
from pack_rules import validate_pack  # noqa: E402
from paint import islands, texture  # noqa: E402
from PIL import Image  # noqa: E402

RESOURCES = ROOT / "plugin" / "src" / "main" / "resources"
MODELS = (builder, town_hall, residence, farmer, cook, courier, warehouse, lumberjack, miner, goggles, build_tool,
          clipboard)


def main():
    assets = Assets(Path(sys.argv[1]) if len(sys.argv) > 1 else GRADLE_ASSETS)
    for module in MODELS:
        model = RESOURCES / "Common" / (module.MODEL + ".blockymodel")
        nodes = json.loads(model.read_text(encoding="utf-8"))["nodes"]
        image = model_texture(module, nodes, assets)
        if hasattr(module, "GLINT"):
            # The glint frames go below the texture; the icon reads only the islands above them.
            image, step = glint.frames(image, nodes, module.GLINT)
            write_json(model.with_suffix(".blockyanim"),
                       glint.animation(nodes, module.GLINT, step, getattr(module, "BREATHE", True)))
        elif hasattr(module, "animation"):
            write_json(model.with_suffix(".blockyanim"), module.animation(nodes))
        save_png(image, model.with_suffix(".png"))
        icon = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
        view = getattr(module, "ICON_VIEW", None)
        draw_model(icon, nodes, image, *frame(nodes, view), view)
        save_png(icon, RESOURCES / "Common/Icons/Items/HyColony" / (module.ICON + ".png"))
    validate_pack(assets, RESOURCES)


def model_texture(module, nodes, assets):
    """The model's finished texture (paint.texture) from its module's materials and tiles."""
    # Blockbench names a group's later cubes '<cube>--C<n>': the materials read the name before it.
    return texture(nodes, texture_size(nodes), module.tiles(assets),
                   lambda name, side: module.material(name.split("--")[0], side),
                   light_map(nodes, module.MODEL.startswith("Blocks/"), getattr(module, "SEE_THROUGH", frozenset())),
                   module.PICTURES)


def texture_size(nodes):
    """The smallest texture holding every island, each side a multiple of 32 (Hytale's rule)."""
    rects = list(islands(nodes))
    return tuple(32 * math.ceil(max(r[i] + r[i + 2] for r in rects) / 32) for i in (2, 3))


if __name__ == "__main__":
    main()
