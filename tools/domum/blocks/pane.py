"""Paper walls: a pane whose arms reach the paper walls around it (DO PaperWallBlock, a Minecraft pane), through
our own connection template: one shape per set of neighbours, turned in the four directions.

Deviation from MC: a paper wall joins other paper walls only; Minecraft panes also join any solid block face,
which a Hytale connection template cannot express.
"""

import names
from blocks import common
from pack import write_json

TEMPLATE = "HyColony_DO_PaneConnectedBlockTemplate"
TAG = "HyColonyDoPane"
# Shape -> the neighbours that make it with the pattern facing north (common.neighbour_template).
SHAPES = {
    "End": {"north"},
    "Straight": {"north", "south"},
    "Corner": {"north", "east"},
    "T_Junction": {"north", "east", "west"},
    "Cross_Junction": {"north", "south", "east", "west"},
}


def generate(ctx, family):
    """One template per DO paper wall block, a lone post by default with a state per connected shape, and the
    connection template."""
    for block in family.blocks:
        ident = names.template_id(family, (block,))
        post = common.look(ctx, family, ident, block, _arms(set()))
        looks = {shape: common.look(ctx, family, ident + "_" + shape, block, _arms(sides))
                 for shape, sides in SHAPES.items()}
        block_type = common.model_block_type(ctx, family, post["CustomModel"], post.get("HitboxType"), "NESW")
        block_type.update(common.connected(ident, TEMPLATE, "Post", looks))
        common.template(ctx, family, ident, (block,), block_type)
    write_json(ctx.pack / common.TEMPLATES / (TEMPLATE + ".json"), common.neighbour_template(TAG, "Post", SHAPES))


def _arms(sides):
    """DO's multipart props: an arm toward each of sides, none elsewhere."""
    return {side: "true" if side in sides else "false" for side in common.SIDES}
