"""Families without states: one template per DO block and per DO "type" value (timber frames, posts, panels),
placed turned toward the player."""

import assemble
import names
import source
from blocks import common

# Family -> VariantRotation. A post points along any axis (DO facing: 6 directions), like a vanilla pipe; the rest
# turn to face the player like vanilla roofs and trapdoors.
ROTATIONS = {"Post": "Pipe"}


def variants(ctx, family):
    """(parts, props) of every template of the family: its DO blocks, times their "type" values when they have
    several (posts, panels)."""
    found = []
    for block in family.blocks:
        types = sorted(assemble.property_values(source.blockstate(ctx.root, block)).get("type", ()))
        if len(types) > 1:
            found.extend(((block, t), {"type": t}) for t in types)
        else:
            found.append(((block,), {}))
    return found


def generate(ctx, family):
    """Writes the family's templates: model, hitbox, item and BlockType."""
    for parts, props in variants(ctx, family):
        shown = parts if len(family.blocks) > 1 else parts[1:]
        ident = names.template_id(family, shown)
        model, blockymodel = common.convert_state(ctx, family, parts[0], props)
        model_path = common.write_model(ctx, ident, blockymodel)
        hitbox_id = common.hitbox(ctx, ident, model)
        rotation = ROTATIONS.get(family.name, "NESW")
        block_type = common.model_block_type(ctx, family, model_path, hitbox_id, rotation)
        common.template(ctx, family, ident, shown, block_type)
