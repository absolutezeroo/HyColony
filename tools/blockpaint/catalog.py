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
- BREATHE, optional: False for crystals that only glint;
- SEED, optional: a number that gives each face its own pattern (paint.face_seed); without it, faces of one size and
  material look alike, as they always did;
- AXES, optional: {node name: box axis "x", "y" or "z"} along which a part's grain runs (paint.grain_of); without it,
  along each island's long side;
- CONDITION, optional: the model's condition (conditions.py): its tiles may then be layered materials
  (effects.material: coats and effects), and the model is lit in the hytale light; without it or with
  conditions.DEFAULT, the render of before;
- AGE, ENVIRONMENT, optional with CONDITION: the model's age (else the condition's) and environment (conditions.py);
- ROLES, USAGE, FOCUS, optional with CONDITION: {node name: role} (roles.py), {node name: roles.contact(…) and kin},
  and the nodes the eye goes to;
- ART, optional with CONDITION: the model's art (art.Art: detail budget, rest zones), else art.Art();
- HISTORY, optional with CONDITION: what happened to it, a few events (history.py: impact, fire, water…)."""

import json
import math

import glint
from art import Art
from bake import light_map, survey
from conditions import DEFAULT
from history import resolved
from icons import ICON_SIZE, draw_model, frame
from pack import save_png, write_json
from paint import Look, Painting, islands, texture
from PIL import Image
from surface import Surface
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
    """The model's finished texture (paint.texture) from its module's materials and tiles; layered (surface.py) and
    in the hytale light when the module declares a CONDITION, else exactly as before."""
    condition = getattr(module, "CONDITION", None)
    # DEFAULT is the render of a model that declares nothing: the same bytes.
    if condition is None or condition is DEFAULT:
        grounded, see_through = module.MODEL.startswith("Blocks/"), getattr(module, "SEE_THROUGH", frozenset())
        return module_texture(module, nodes, assets, light_map(nodes, grounded, see_through), None)
    values, contexts = surveyed(module, nodes)
    return module_texture(module, nodes, assets, values, module_surface(module, contexts))


def module_texture(module, nodes, assets, values, surface):
    """The model's texture from its module's tiles, materials, seed and axes, lit by values: layered with surface in
    the hytale light, or as before without one."""
    # Blockbench names a group's later cubes '<cube>--C<n>': the materials read the name before it.
    look = Look(module.tiles(assets), lambda name, side: module.material(name.split("--")[0], side), module.PICTURES)
    light = "legacy" if surface is None else "hytale"
    painting = Painting(getattr(module, "SEED", None), getattr(module, "AXES", None), surface, light)
    return texture(nodes, texture_size(nodes), look, values, painting)


def surveyed(module, nodes):
    """(values, contexts) of bake.survey for the model of a module declaring a CONDITION: a block stands on a floor,
    its SEE_THROUGH nodes cast nothing, the bake's own chips and grime are its condition's."""
    grounded, see_through = module.MODEL.startswith("Blocks/"), getattr(module, "SEE_THROUGH", frozenset())
    return survey(nodes, grounded, see_through, module.CONDITION.wear, module.CONDITION.grime)


def module_surface(module, contexts, record=None):
    """The Surface a module declaring a CONDITION paints its layered materials with, its HISTORY resolved on contexts;
    record, a list, receives each layered island (semantics.painted)."""
    return Surface(contexts, module.CONDITION, getattr(module, "AGE", None), getattr(module, "ENVIRONMENT", None),
                   getattr(module, "ROLES", {}), getattr(module, "USAGE", {}), getattr(module, "FOCUS", ()),
                   getattr(module, "SEED", None), getattr(module, "ART", Art()),
                   resolved(getattr(module, "HISTORY", ()), contexts), record=record)


def texture_size(nodes):
    """The smallest texture holding every island, each side a multiple of 32 (Hytale's rule)."""
    rects = list(islands(nodes))
    return tuple(32 * math.ceil(max(r[i] + r[i + 2] for r in rects) / 32) for i in (2, 3))
