"""Doors and trapdoors: DO's closed shapes on the vanilla Hytale mechanics (states, hitboxes, animations, sounds).

The vanilla door and trapdoor animations (Blocks/Animations/Door/Door_Open_In.blockyanim,
Trapdoor/Trapdoor_Open.blockyanim) turn a node named "Door"; our models put their elements under it, placed on the
hinge. A door is one block drawing both DO halves, recentred in depth and hinged on its -X edge like the vanilla
Crude door (DO's sits against the block's south side); a right-hinged door is the vanilla double-door rule turning
it 180 degrees.

A trapdoor is DO's bottom half hinged on its +Z edge: placed on the floor (no pitch, as stairs), the vanilla
animation opens it up against that side. Turned upside down (UpDownNESW, pitch 180) it is DO's top half hinged on
-Z, which is the vanilla Crude trapdoor, opening down.

Deviation from MC: the flip moves the hinge of a hanging trapdoor to the other side than a floor one's; Minecraft
keeps the side for both halves. The vanilla animation only turns one way, so one of the two must differ.
"""

import copy

import assemble
import convert
import faces
import names
from blocks import common, static
from pack import write_json

# Keys of the vanilla BlockType that make it a door: states (hitboxes, animations, sounds), the double-door rule,
# the interaction and its hint. Material keys (sounds, particles, gathering) come from the template's material.
MECHANICS = ("HitboxType", "Opacity", "State", "ConnectedBlockRuleSet", "IsDoor", "Interactions", "InteractionHint",
             "SoundOcclusionOpacity")
VANILLA = {"door": "Furniture_Crude_Door", "trapdoor": "Furniture_Crude_Trapdoor"}
GROUP = "Door"
# Hinges, Minecraft pixels: DO's door and trapdoor panels are 3 px thick. A door's panel (z 13..16) moves to
# z 6.5..9.5 and turns about its x=0 edge; a floor trapdoor's (y 0..3) turns about its z=16 edge. Decorations
# sticking out of a panel (a waffle's studs, a fancy trapdoor's frame) move neither.
DOOR_SHIFT_Z = -6.5
DOOR_HINGE = (0, 0, 8)
TRAPDOOR_HINGE = (8, 1.5, 14.5)
# The floor trapdoor's own boxes (block units): the vanilla Trapdoor ones fit the hanging trapdoor only.
TRAPDOOR_BOXES = {
    "HyColony_DO_Trapdoor": {"Min": {"X": 0, "Y": 0, "Z": 0}, "Max": {"X": 1, "Y": 0.1875, "Z": 1}},
    "HyColony_DO_Trapdoor_Open": {"Min": {"X": 0, "Y": 0, "Z": 0.8125}, "Max": {"X": 1, "Y": 1, "Z": 1}},
}


def doors(ctx, family):
    """One door template per DO type: both halves, closed and left-hinged, under the hinge node."""
    for parts, props in static.variants(ctx, family):
        state = {"facing": "north", "hinge": "left", "open": "false", **props}
        low = assemble.state_model(ctx.root, family, parts[0], {**state, "half": "lower"})
        high = assemble.state_model(ctx.root, family, parts[0], {**state, "half": "upper"})
        model = _moved(assemble.prefixed(high, "u"), 16, 0)
        model = {"textures": {**low["textures"], **model["textures"]}, "elements": low["elements"] + model["elements"]}
        model = faces.clean(faces.cap_ends(_moved(model, 0, DOOR_SHIFT_Z)))
        hinge = convert.hytale(DOOR_HINGE)
        _template(ctx, family, parts, "door", convert.to_blockymodel(model, family, GROUP, hinge))


def trapdoors(ctx, family):
    """One trapdoor template per DO type: closed on the floor of its block, under the hinge node."""
    for name, box in TRAPDOOR_BOXES.items():
        write_json(ctx.pack / common.HITBOXES / (name + ".json"), {"Boxes": [box]})
    for parts, props in static.variants(ctx, family):
        # DO facing=south turned by family.turn_y: the hinge on +Z.
        state = {"facing": "south", "half": "bottom", "open": "false", **props}
        model = faces.clean(faces.cap_ends(assemble.state_model(ctx.root, family, parts[0], state)))
        hinge = convert.hytale(TRAPDOOR_HINGE)
        _template(ctx, family, parts, "trapdoor", convert.to_blockymodel(model, family, GROUP, hinge))


def _template(ctx, family, parts, kind, blockymodel):
    """Writes the model and registers the template with the vanilla door's or trapdoor's mechanics."""
    shown = parts if len(family.blocks) > 1 else parts[1:]
    ident = names.template_id(family, shown)
    vanilla = ctx.assets.item(VANILLA[kind])["BlockType"]
    rotation = "UpDownNESW" if kind == "trapdoor" else vanilla["VariantRotation"]
    block_type = common.model_block_type(ctx, family, common.write_model(ctx, ident, blockymodel), None, rotation)
    block_type.update({key: copy.deepcopy(vanilla[key]) for key in MECHANICS if key in vanilla})
    if kind == "trapdoor":
        block_type["HitboxType"] = "HyColony_DO_Trapdoor"
        block_type["State"]["Definitions"]["OpenDoorOut"]["HitboxType"] = "HyColony_DO_Trapdoor_Open"
    else:
        block_type["ConnectedBlockRuleSet"]["TemplateShapeBlockPatterns"] = {"Default": ident}
    common.template(ctx, family, ident, shown, block_type)


def _moved(model, dy, dz):
    """A copy of model translated by dy, dz Minecraft pixels (rotation origins included)."""
    moved = copy.deepcopy(model)
    for element in moved["elements"]:
        for point in [element["from"], element["to"]] + ([element["rotation"]["origin"]]
                                                          if "rotation" in element else []):
            point[1] += dy
            point[2] += dz
    return moved
