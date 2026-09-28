"""Shingles and shingle slabs: shapes chosen by the neighbours, as in DO.

A shingle is a vanilla roof: rule set "Roof" (Regular only, no Hollow or Topper, B.10 § 3) choosing the straight
block or one of four corner states, each drawn with DO's matching stair shape, and turned upside down by its
VariantRotation (DO's half=top). The rules name states only, never blocks, so a runtime variant resolves its own.

A shingle slab takes one of six shapes from how many shingle slabs surround it and where
(DO ShingleSlabBlock.getSlabShape): our own connected-block template, one pattern per shape, rotated by Hytale.
"""

import hitboxes
import names
from blocks import common
from pack import write_json

MATERIAL_NAME = "HyColonyDoShingle"
# Hytale roof corner state -> DO stair shape drawing it (a vanilla roof "Corner" is an outer corner).
CORNERS = {
    "Corner_Left": "outer_left",
    "Corner_Right": "outer_right",
    "Inverted_Corner_Left": "inner_left",
    "Inverted_Corner_Right": "inner_right",
}
FLIPS = {"Corner_Left": "Orthogonal", "Corner_Right": "OrthogonalInverse",
         "Inverted_Corner_Left": "Orthogonal", "Inverted_Corner_Right": "OrthogonalInverse"}
SLAB_TEMPLATE = "HyColony_DO_ShingleSlabConnectedBlockTemplate"
SLAB_TAG = "HyColonyDoShingleSlab"
# Shape -> the neighbours (north, south, east, west) that make it with DO's facing=north, the state we draw
# (ShingleSlabBlock.getSlabShape: one_way faces its neighbour; two_way north+south; curved south+east faces north;
# three_way north+east+west). Hytale turns each pattern and the block together (IsCardinallyRotatable).
SLAB_SHAPES = {
    "One_Way": ("one_way", {"north"}),
    "Two_Way": ("two_way", {"north", "south"}),
    "Curved": ("curved", {"south", "east"}),
    "Three_Way": ("three_way", {"north", "east", "west"}),
    "Four_Way": ("four_way", {"north", "south", "east", "west"}),
}


def shingles(ctx, family):
    """One template per DO slope: straight block plus its four corner states, each colliding as its own model
    (a DO slope fits one block; Hytale's shallow and steep roof hitboxes span two)."""
    for block in family.blocks:
        ident = names.template_id(family, (block,))
        straight = _look(ctx, family, ident, block, "straight")
        definitions = {state: {**_look(ctx, family, ident + "_" + state, block, shape), "FlipType": FLIPS[state],
                               "Supporting": {"Down": [{}]}}
                       for state, shape in CORNERS.items()}
        block_type = common.model_block_type(ctx, family, straight["CustomModel"], straight["HitboxType"],
                                             "UpDownNESW")
        block_type.update({
            "Supporting": {"Down": [{}], "North": [{}]},
            "ConnectedBlockRuleSet": {
                "Type": "Roof",
                "Regular": {"Straight": {"State": "default"}, **{s: {"State": s} for s in CORNERS}},
                # One name for every slope: DO forms a corner between any two shingles (DOStairBlock.isStairs is
                # any DOStairBlock), whatever their pitch.
                "MaterialName": MATERIAL_NAME,
            },
            "State": {"Definitions": definitions},
        })
        common.template(ctx, family, ident, (block,), block_type)


def _look(ctx, family, name, block, shape):
    """The CustomModel and stepped HitboxType of one slope shape (facing north, bottom half), both written."""
    model, blockymodel = common.convert_state(ctx, family, block, {"facing": "north", "half": "bottom", "shape": shape})
    write_json(ctx.pack / common.HITBOXES / (name + ".json"), {"Boxes": hitboxes.stepped(model)})
    return {"CustomModel": common.write_model(ctx, name, blockymodel), "HitboxType": name}


def shingle_slab(ctx, family):
    """The shingle slab template, shaped "Single" by default (DO's "top": no neighbour; renamed because pack
    validators read a "Top" key as a cube texture) with a state per connected shape, and its connection template."""
    block = family.blocks[0]
    ident = names.template_id(family, ())
    top = _model(ctx, family, ident, "", block, {"shape": "top"})
    definitions = {shape: {"CustomModel": _model(ctx, family, ident, "_" + shape, block, {"shape": do_shape})}
                   for shape, (do_shape, _) in SLAB_SHAPES.items()}
    block_type = common.model_block_type(ctx, family, top, None, "NESW")
    block_type.update({"HitboxType": "Block_Half", "Opacity": "Transparent",
                       **common.connected(ident, SLAB_TEMPLATE, "Single", definitions)})
    common.template(ctx, family, ident, (), block_type)
    shapes = {shape: present for shape, (_, present) in SLAB_SHAPES.items()}
    write_json(ctx.pack / common.TEMPLATES / (SLAB_TEMPLATE + ".json"),
               common.neighbour_template(SLAB_TAG, "Single", shapes))


def _model(ctx, family, ident, suffix, block, props):
    """Writes the model of one state (facing north, bottom half) and returns its Common path."""
    _, blockymodel = common.convert_state(ctx, family, block, {"facing": "north", "half": "bottom", **props})
    return common.write_model(ctx, ident + suffix, blockymodel)
