"""Pillars: a column's look chosen by the pillars above and below it (DO PillarBlock's column property), through a
copy of the vanilla pillar connection template plus DO's lone pillar."""

import copy

import names
from blocks import common
from pack import write_json

TEMPLATE = "HyColony_DO_PillarConnectedBlockTemplate"
VANILLA_TEMPLATE = "Server/Item/CustomConnectedBlockTemplates/PillarConnectedBlockTemplate.json"
# Hytale shape -> DO column value. Base: a pillar above only (the stack's foot); Base_Inverted: below only (its
# capital); Middle: both; Full: neither, the template's default.
COLUMNS = {"Full": "full_pillar", "Base": "pillar_base", "Base_Inverted": "pillar_capital", "Middle": "pillar_column"}


def generate(ctx, family):
    """One template per DO pillar block, with a state per stacked shape, and the connection template."""
    for block in family.blocks:
        ident = names.template_id(family, (block,))
        looks = {shape: common.look(ctx, family, ident + ("" if shape == "Full" else "_" + shape), block,
                                    {"column": column}) for shape, column in COLUMNS.items()}
        full = looks.pop("Full")
        block_type = common.model_block_type(ctx, family, full["CustomModel"], full.get("HitboxType"), "None")
        block_type.update({"Opacity": "Transparent", **common.connected(ident, TEMPLATE, "Full", looks)})
        common.template(ctx, family, ident, (block,), block_type)
    write_json(ctx.pack / common.TEMPLATES / (TEMPLATE + ".json"), connection_template(ctx.assets))


def connection_template(assets):
    """The vanilla pillar template (Base, Base_Inverted, Middle, joined by "PillarConnection" faces) with a Full
    shape, matched by nothing, as its default."""
    template = copy.deepcopy(assets.json(VANILLA_TEMPLATE))
    template["Shapes"]["Full"] = {"FaceTags": copy.deepcopy(template["Shapes"]["Middle"]["FaceTags"]),
                                  "PatternsToMatchAnyOf": []}
    template["DefaultShape"] = "Full"
    return template
