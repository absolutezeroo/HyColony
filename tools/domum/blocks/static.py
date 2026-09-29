"""Families without states: one template per DO block and per DO "type" value (timber frames, posts, panels),
placed turned toward the player."""

import assemble
import names
import source
from blocks import common

# Family -> VariantRotation. A post points along any axis (DO facing: 6 directions), like a vanilla pipe. A panel
# lies on the floor, under the ceiling or stands against a wall (AbstractPanelBlockTrapdoor): Hytale's half-block
# rotation (DoublePipe: none, pitch 180, pitch 90 in 4 yaws) gives those six.
# Deviation from MC: DO also turns a floor or ceiling panel toward the player and a standing post about its axis
# (FACING); no Hytale rotation set has both the wall placements and those turns, and DO's click zones (0.2 / 0.8)
# that pick the half are not reproduced.
ROTATIONS = {"Post": "Pipe", "Panel": "DoublePipe"}
# Timber frames whose pattern points somewhere (DO TimberFrameBlock: FACING, 6 ways, the model drawn facing up):
# the same six through DoublePipe, from the up-facing model. DO draws the others the same whatever the facing.
DIRECTED_FRAMES = ("side_framed", "up_gated", "down_gated", "side_framed_horizontal")


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
        rotation, props = _placement(family, parts[0], props)
        model, blockymodel = common.convert_state(ctx, family, parts[0], props)
        model_path = common.write_model(ctx, ident, blockymodel)
        hitbox_id = common.hitbox(ctx, ident, model)
        block_type = common.model_block_type(ctx, family, model_path, hitbox_id, rotation)
        if hitbox_id is None:  # a full block (timber frame): it holds up what is placed against it
            block_type["Supporting"] = common.full_supporting()
        common.template(ctx, family, ident, shown, block_type)


def _placement(family, block, props):
    """(VariantRotation, DO state props) of a template: a timber frame is unturned, or facing up when directed."""
    if family.name != "TimberFrame":
        return ROTATIONS.get(family.name, "NESW"), props
    if block in DIRECTED_FRAMES:
        return "DoublePipe", {**props, "facing": "up"}
    return "None", props
