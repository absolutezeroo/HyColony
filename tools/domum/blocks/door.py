"""Doors and trapdoors: DO's closed shapes on the vanilla Hytale mechanics (states, hitboxes, animations, sounds).

The vanilla door and trapdoor animations (Blocks/Animations/Door/Door_Open_In.blockyanim,
Trapdoor/Trapdoor_Open.blockyanim) turn a node named "Door"; our models put their elements under it, placed on the
hinge where the vanilla Crude models place theirs: a door's -X edge at mid-depth, a trapdoor's -Z edge at
mid-thickness. A door is one block drawing both DO halves, recentred in depth like the vanilla door (DO's sits
against the block's south side); a right-hinged door is the vanilla double-door rule turning it 180 degrees.

Deviation from MC: a trapdoor placed on the floor is the same block turned upside down (UpDownNESW, pitch 180),
so its hinge is on the other side than when hung from above; Minecraft keeps the hinge side for both halves.
Kept because the vanilla animation only opens a flipped trapdoor upward.
"""

import copy

import assemble
import convert
import faces
import names
from blocks import common, static

# Keys of the vanilla BlockType that make it a door: states (hitboxes, animations, sounds), the double-door rule,
# the interaction and its hint. Material keys (sounds, particles, gathering) come from the template's material.
MECHANICS = ("HitboxType", "State", "ConnectedBlockRuleSet", "IsDoor", "Interactions", "InteractionHint",
             "SoundOcclusionOpacity")
VANILLA = {"door": "Furniture_Crude_Door", "trapdoor": "Furniture_Crude_Trapdoor"}
GROUP = "Door"
# Hinges, Minecraft pixels: DO's door and trapdoor panels are 3 px thick; a door stands on its x=0 edge, recentred
# on z=8, a trapdoor closes at y 13..16 and hinges on its z=0 edge (families.py turn_y). Decorations sticking out
# of the panel (a waffle's studs, a fancy trapdoor's frame) do not move the hinge.
DOOR_HINGE = (0, 0, 8)
TRAPDOOR_HINGE = (8, 14.5, 1.5)


def doors(ctx, family):
    """One door template per DO type: both halves, closed and left-hinged, under the hinge node."""
    for parts, props in static.variants(ctx, family):
        state = {"facing": "north", "hinge": "left", "open": "false", **props}
        low = assemble.state_model(ctx.root, family, parts[0], {**state, "half": "lower"})
        high = assemble.state_model(ctx.root, family, parts[0], {**state, "half": "upper"})
        model = _moved(assemble.prefixed(high, "u"), 16, 0)
        model = {"textures": {**low["textures"], **model["textures"]}, "elements": low["elements"] + model["elements"]}
        low_z, high_z = _extent(model, 2)
        model = faces.clean(faces.cap_ends(_moved(model, 0, 8 - (low_z + high_z) / 2)))
        hinge = convert.hytale(DOOR_HINGE)
        _template(ctx, family, parts, "door", convert.to_blockymodel(model, family, GROUP, hinge))


def trapdoors(ctx, family):
    """One trapdoor template per DO type: closed at the top of its block, under the hinge node."""
    for parts, props in static.variants(ctx, family):
        state = {"facing": "north", "half": "top", "open": "false", **props}
        model = faces.clean(faces.cap_ends(assemble.state_model(ctx.root, family, parts[0], state)))
        hinge = convert.hytale(TRAPDOOR_HINGE)
        _template(ctx, family, parts, "trapdoor", convert.to_blockymodel(model, family, GROUP, hinge))


def _template(ctx, family, parts, kind, blockymodel):
    """Writes the model and registers the template with the vanilla door's or trapdoor's mechanics."""
    shown = parts if len(family.blocks) > 1 else parts[1:]
    ident = names.template_id(family, shown)
    vanilla = ctx.assets.item(VANILLA[kind])["BlockType"]
    rotation = "UpDownNESW" if kind == "trapdoor" else vanilla["VariantRotation"]
    block_type = common.model_block_type(ctx, family, common.write_model(ctx, ident, blockymodel),
                                         vanilla["HitboxType"], rotation)
    block_type.update({key: copy.deepcopy(vanilla[key]) for key in MECHANICS if key in vanilla})
    if "ConnectedBlockRuleSet" in block_type:
        block_type["ConnectedBlockRuleSet"]["TemplateShapeBlockPatterns"] = {"Default": ident}
    common.template(ctx, family, ident, shown, block_type)


def _extent(model, axis):
    """(lowest, highest) coordinate of model's elements along axis, in Minecraft pixels, tilts included."""
    values = [p[axis] for e in model["elements"] for p in assemble.world_points(e)]
    return min(values), max(values)


def _moved(model, dy, dz):
    """A copy of model translated by dy, dz Minecraft pixels (rotation origins included)."""
    moved = copy.deepcopy(model)
    for element in moved["elements"]:
        for point in [element["from"], element["to"]] + ([element["rotation"]["origin"]]
                                                          if "rotation" in element else []):
            point[1] += dy
            point[2] += dz
    return moved
