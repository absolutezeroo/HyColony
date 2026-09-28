"""Shingles and shingle slabs: shapes chosen by the neighbours, as in DO.

A shingle is a vanilla roof: rule set "Roof" (Regular only, no Hollow or Topper, B.10 § 3) choosing the straight
block or one of four corner states, each drawn with DO's matching stair shape, and turned upside down by its
VariantRotation (DO's half=top). The rules name states only, never blocks, so a runtime variant resolves its own.

A shingle slab takes one of six shapes from how many shingle slabs surround it and where
(DO ShingleSlabBlock.getSlabShape): our own connected-block template, one pattern per shape, rotated by Hytale.
"""

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
# DO slope -> the vanilla roof hitboxes of the nearest pitch (Wood_Softwood_Roof, _Roof_Shallow, _Roof_Steep).
_NORMAL = ("Stairs", "Roof_Corner_Left", "Roof_Corner_Right",
           "Stairs_Inverted_Corner_Left", "Stairs_Inverted_Corner_Right")
_SHALLOW = ("Stairs_Shallow", "Roof_Corner_Shallow_Left", "Roof_Corner_Shallow_Right",
            "Roof_Corner_Shallow_Inverted_Left", "Roof_Corner_Shallow_Inverted_Right")
_STEEP = ("Stairs_Steep", "Stairs_Corner_Steep_Left", "Stairs_Corner_Steep_Right",
          "Roof_Corner_Steep_Inverted_Left", "Roof_Corner_Steep_Inverted_Right")
HITBOXES = {"shingle": _NORMAL, "shingle_flat": _SHALLOW, "shingle_flat_lower": _SHALLOW,
            "shingle_steep": _STEEP, "shingle_steep_lower": _STEEP}

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
# Neighbour -> (its offset, the face of it that touches us).
_SIDES = {"north": ((0, 0, -1), "South"), "south": ((0, 0, 1), "North"),
          "east": ((1, 0, 0), "West"), "west": ((-1, 0, 0), "East")}


def shingles(ctx, family):
    """One template per DO slope: straight block plus its four corner states."""
    for block in family.blocks:
        ident = names.template_id(family, (block,))
        hitboxes = dict(zip(("default",) + tuple(CORNERS), HITBOXES[block]))
        straight = _model(ctx, family, ident, "", block, {"shape": "straight"})
        definitions = {state: {"CustomModel": _model(ctx, family, ident, "_" + state, block, {"shape": shape}),
                               "HitboxType": hitboxes[state], "FlipType": FLIPS[state],
                               "Supporting": {"Down": [{}]}}
                       for state, shape in CORNERS.items()}
        block_type = common.model_block_type(ctx, family, straight, None, "UpDownNESW")
        block_type.update({
            "HitboxType": hitboxes["default"],
            "Supporting": {"Down": [{}], "North": [{}]},
            "ConnectedBlockRuleSet": {
                "Type": "Roof",
                "Regular": {"Straight": {"State": "default"}, **{s: {"State": s} for s in CORNERS}},
                "MaterialName": MATERIAL_NAME,
            },
            "State": {"Definitions": definitions},
        })
        common.template(ctx, family, ident, (block,), block_type)


def shingle_slab(ctx, family):
    """The shingle slab template, shaped "Single" by default (DO's "top": no neighbour; renamed because pack
    validators read a "Top" key as a cube texture) with a state per connected shape, and its connection template."""
    block = family.blocks[0]
    ident = names.template_id(family, ())
    top = _model(ctx, family, ident, "", block, {"shape": "top"})
    definitions = {shape: {"CustomModel": _model(ctx, family, ident, "_" + shape, block, {"shape": do_shape})}
                   for shape, (do_shape, _) in SLAB_SHAPES.items()}
    block_type = common.model_block_type(ctx, family, top, None, "NESW")
    block_type.update({
        "HitboxType": "Block_Half",
        "Opacity": "Transparent",
        "ConnectedBlockRuleSet": {
            "Type": "CustomTemplate",
            "TemplateShapeAssetId": SLAB_TEMPLATE,
            "TemplateShapeBlockPatterns": {"Single": ident, **{
                shape: f"*{ident}_State_Definitions_{shape}" for shape in SLAB_SHAPES}},
        },
        "State": {"Definitions": definitions},
    })
    common.template(ctx, family, ident, (), block_type)
    write_json(ctx.pack / "Server/Item/CustomConnectedBlockTemplates" / (SLAB_TEMPLATE + ".json"), slab_template())


def slab_template():
    """The connected-block template: every shape carries the slab tag on its four sides; a shape matches when
    exactly its neighbours (and no other side) are shingle slabs."""
    tags = {side.capitalize(): [SLAB_TAG] for side in _SIDES}
    shapes = {"Single": {"FaceTags": tags, "PatternsToMatchAnyOf": []}}
    for shape, (_, present) in SLAB_SHAPES.items():
        rules = [{"Position": dict(zip("XYZ", offset)), "IncludeOrExclude": "Include" if side in present else "Exclude",
                  "FaceTags": {face: [SLAB_TAG]}} for side, (offset, face) in _SIDES.items()]
        shapes[shape] = {"FaceTags": tags, "PatternsToMatchAnyOf": [{
            "Type": "Custom", "AllowedPatternTransformations": {"IsCardinallyRotatable": True},
            "RulesToMatch": rules}]}
    return {"ConnectsToOtherMaterials": True, "DefaultShape": "Single", "Shapes": shapes}


def _model(ctx, family, ident, suffix, block, props):
    """Writes the model of one state (facing north, bottom half) and returns its Common path."""
    _, blockymodel = common.convert_state(ctx, family, block, {"facing": "north", "half": "bottom", **props})
    return common.write_model(ctx, ident + suffix, blockymodel)
