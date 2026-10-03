"""Paints a catalog of models, each described by a module, and draws their icons. A block model (under Blocks/) is
first trimmed (trim.py: bottoms closed, hidden faces dropped, faces laid out) and rewritten in pack.write_json's
layout; an item model keeps its layout. The texture goes next to the model (<MODEL>.png: paint.texture, the module's
brushes and the light baked from the model), the icon to the catalog's icon folder.

A model's module declares:
- MODEL: its path under Common, without extension; a block's (under Blocks/) stands on a floor that shades its foot;
- ICON: its icon's name;
- PICTURES: the materials whose tile carries a drawing laid out for its island, never turned;
- material(name, side): the material of a node's face, from the node's name (without Blockbench's '--C<n>');
- tiles(assets): material -> a 32 px tile or a brush (brushes.py);
- ICON_VIEW, optional: the icon's view (icons.turned) instead of the isometric one of blocks;
- animation(nodes), optional: the model's looping blockyanim, written next to it (<MODEL>.blockyanim);
- SEE_THROUGH, optional: the nodes lit but casting no baked shadow (shown only part of the time by the animation);
- GLINT, optional: the name prefix of the crystal nodes that breathe and glint (glint.py), instead of animation;
- BREATHE, optional: False for crystals that only glint."""

import json
import math

import glint
from bake import light_map
from icons import ICON_SIZE, draw_model, frame
from pack import save_png, write_json
from paint import islands, texture
from PIL import Image
from trim import faces, trim


def paint(modules, common, icons, assets):
    """Paints and draws the icon of every model of modules, trimming the blocks (trim.trim) first: their files under
    common (a pack's Common folder), their icons in icons."""
    for module in modules:
        model = common / (module.MODEL + ".blockymodel")
        data = json.loads(model.read_text(encoding="utf-8"))
        nodes = data["nodes"]
        if module.MODEL.startswith("Blocks/"):
            read = faces(nodes)
            size = trim(nodes, module)
            write_json(model, data)
            print(f"{model.stem}: {read} faces read, {faces(nodes)} kept, texture {size[0]} x {size[1]}")
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
        save_png(icon, icons / (module.ICON + ".png"))


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
