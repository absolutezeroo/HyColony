"""Pillars: a column's look chosen by the same pillars above and below it (DO PillarBlock's column property: a
pillar joins its own block only, any material), through a copy of the vanilla pillar connection template, tagged
per pillar block, plus DO's lone pillar."""

import copy
import json

import names
from blocks import common
from pack import write_json

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
        template = ident + "ConnectedBlockTemplate"
        block_type = common.model_block_type(ctx, family, full["CustomModel"], full["HitboxType"], "None")
        block_type.update({"Opacity": "Transparent", **common.connected(ident, template, "Full", looks)})
        common.template(ctx, family, ident, (block,), block_type)
        write_json(ctx.pack / common.TEMPLATES / (template + ".json"), connection_template(ctx.assets, ident))


def connection_template(assets, ident):
    """The vanilla pillar template (Base, Base_Inverted, Middle), its "PillarConnection" faces renamed after ident
    so only the same pillar block joins, with a Full shape, matched by nothing, as its default."""
    text = json.dumps(assets.json(VANILLA_TEMPLATE)).replace('"PillarConnection"', json.dumps(ident))
    template = json.loads(text)
    template["Shapes"]["Full"] = {"FaceTags": copy.deepcopy(template["Shapes"]["Middle"]["FaceTags"]),
                                  "PatternsToMatchAnyOf": []}
    template["DefaultShape"] = "Full"
    return template
