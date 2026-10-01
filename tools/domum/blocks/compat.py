"""DO's vanilla-compatible blocks (fence, fence gate, wall, stairs, slab): the vanilla Hytale equivalent's rules,
states, hitboxes and interactions, each look drawn by DO's model of the matching shape (Minecraft's templates,
minecraft.py), every vanilla id renamed to ours.

Fences and walls shape by our own Java rule set (RULES, domum/plugin HytaleFenceRules): MC's FenceBlock and
WallBlock rule picks one of our template's (TEMPLATE) shapes from the four neighbours, so a full face joins too, and
vanilla fences, walls and bars by their family (vanilla_fences.py). The template gives the shapes' names and our
blocks' FenceConnection face tags; its neighbour patterns, one per shape but the lone post, are not used by the
rule set (only the blocks its TemplateShapeBlockPatterns name). The vanilla template has no lone post nor end and
keeps a block's old turn when no shape matches.

Deviation from MC: the vanilla fences, walls and bars keep the vanilla rules: they join ours by our face tags, whatever
the family, never a full face, and have no lone post nor end; a gate next to a wall is not lowered (Minecraft's
in_wall). A wall's top follows the block above (tall arms, raised post: wall_tops.py, domum/core WallTop).
"""

import json

import assemble
import convert
import faces
import names
from blocks import common, roof, wall_tops
from families import FAMILIES
from models import walk
from pack import write_json

VANILLA = {"Fence": "Wood_Softwood_Fence", "FenceGate": "Wood_Softwood_Fence_Gate", "Wall": "Rock_Stone_Brick_Wall",
           "Stairs": "Wood_Softwood_Stairs", "Slab": "Wood_Softwood_Planks_Half"}
# Keys of a vanilla BlockType that depend on its material or its own model: the template brings its own.
MATERIAL_KEYS = ("Material", "DrawType", "CustomModel", "CustomModelTexture", "Textures", "Gathering", "Group",
                 "Flags", "BlockParticleSetId", "ParticleColor", "BlockSoundSetId", "PhysicalMaterialId",
                 "TextureComputedColor", "Aliases")
TEMPLATE = "HyDomum_FenceConnectedBlockTemplate"
RULES = "HyDomum_Fence"
# DO's fence and wall join as Minecraft's families (domum/core Joiner): its fence is tagged WOODEN_FENCES, its wall
# WALLS (DO FenceCompatibilityTagProvider, WallCompatibilityTagProvider).
JOINS = {"Fence": "WoodenFence", "Wall": "Wall"}
TAG = "FenceConnection"
DEFAULT = "Post"
# Shape -> the neighbours that make it (common.neighbour_template), turned as the vanilla template's, so the vanilla
# states keep their hitboxes and the blocks already placed their look. The end reaches north, which Hytale's default
# Symmetric flip mirrors right (a prefab flipped along X or Z). The rule set's copy is domum/core ConnectedShape.SHAPES
# (and tools/blueprint/domum.py CONNECTED): change all three together.
SHAPES = {
    "End": {"north"},
    "Straight": {"east", "west"},
    "Corner": {"west", "south"},
    "T_Junction": {"east", "west", "south"},
    "Cross_Junction": {"north", "south", "east", "west"},
}
# Our shapes the vanilla template lacks: new states of the same name.
NEW_STATES = (DEFAULT, "End")
# Gate leaves (Minecraft pixels): each turns about its post like a door leaf; the vanilla door animations turn
# "Door" one way and "Door2" the other, so both leaves open to the same side.
GATE_LEAVES = (("Door", (1, 0, 8), lambda e: 2 <= e["from"][0] and e["to"][0] <= 8),
               ("Door2", (15, 0, 8), lambda e: 8 <= e["from"][0] and e["to"][0] <= 14))


def generate(ctx, family):
    """The family's one template: the vanilla mechanics with every look replaced by a DO model."""
    ident = names.template_id(family, ())
    vanilla_item = _renamed(ctx.assets.item(VANILLA[family.name]))
    block_type = {k: v for k, v in vanilla_item["BlockType"].items() if k not in MATERIAL_KEYS}
    default, states = LOOKS[family.name](ctx, family, ident, block_type)
    skeleton = common.model_block_type(ctx, family, default, None, block_type["VariantRotation"])
    del skeleton["Opacity"]  # the vanilla block's own, when it has one
    block_type = {**skeleton, **block_type}
    for state, look in states.items():
        block_type["State"]["Definitions"].setdefault(state, {}).update(look)
    item = common.template(ctx, family, ident, (), block_type, vanilla_item.get("IconProperties"))
    # The item's own vanilla keys: its interactions (a slab's merge into a full block) and hand animations.
    item.update({k: vanilla_item[k] for k in ("Interactions", "PlayerAnimationsId") if k in vanilla_item})
    write_json(ctx.pack / common.ITEMS / (ident + ".json"), item)


def _renamed(vanilla):
    """A copy of a vanilla item with every vanilla compat id renamed to our template's; longest first, so the
    gate's id is renamed before the fence's it starts with."""
    ours = {f.name: names.template_id(f, ()) for f in FAMILIES if f.name in VANILLA}
    text = json.dumps(vanilla)
    for name, vanilla_id in sorted(VANILLA.items(), key=lambda item: -len(item[1])):
        text = text.replace(vanilla_id, ours[name])
    return json.loads(text)


def _model(ctx, family, name, props):
    """Writes the DO model of one state under name; returns its Common path."""
    _, blockymodel = common.convert_state(ctx, family, family.blocks[0], props)
    return common.write_model(ctx, name, blockymodel)


def _connected(ctx, family, ident, block_type, arms):
    """Default and state looks of a fence or wall, shaped by our rule set as the family (its Joins) over our template:
    each shape's neighbours become DO arms.
    The vanilla shapes keep their states; the lone post and the end are new states, with their own hitbox and the
    vanilla states' other keys (a wall state's gathering). The vanilla gate pattern goes: a gate's sides are read
    from its own rules."""
    rules = block_type["ConnectedBlockRuleSet"]
    prefix = f"*{ident}_State_Definitions_"
    patterns = {shape: prefix + shape for shape in NEW_STATES}
    patterns.update({shape: target for shape, target in rules["TemplateShapeBlockPatterns"].items() if shape in SHAPES})
    rules.update(Type=RULES, TemplateShapeAssetId=TEMPLATE, TemplateShapeBlockPatterns=patterns,
                 Joins=JOINS[family.name])
    write_json(ctx.pack / common.TEMPLATES / (TEMPLATE + ".json"), common.neighbour_template(TAG, DEFAULT, SHAPES))
    shared = {k: v for k, v in block_type["State"]["Definitions"]["Cross"].items()
              if k not in ("CustomModel", "HitboxType", "FlipType")}
    default, states = None, {}
    for shape, target in patterns.items():
        sides = SHAPES.get(shape, set())
        if target == ident:
            default = _model(ctx, family, ident, arms(sides))
            continue
        state = target[len(prefix):]
        name = ident + "_" + state
        if state in NEW_STATES:
            states[state] = {**shared, **common.look(ctx, family, name, family.blocks[0], arms(sides))}
        elif "HitboxType" in block_type["State"]["Definitions"].get(state, {}):
            states[state] = {"CustomModel": _model(ctx, family, name, arms(sides))}
        else:
            # The vanilla T and cross inherit the straight run's hitbox: theirs is their model's bounding box (a block
            # above a wall reads it, WallTop; each test band reaches a block's edge, so the box reads as the arms).
            states[state] = common.look(ctx, family, name, family.blocks[0], arms(sides))
    return default, states


def fence(ctx, family, ident, block_type):
    """A fence's arms reach the sides of each template shape."""
    return _connected(ctx, family, ident, block_type,
                      lambda sides: {s: "true" if s in sides else "false" for s in common.SIDES})


def wall(ctx, family, ident, block_type):
    """A wall's arms are low; its post rises when alone or when an arm lacks its opposite (Minecraft's
    WallBlock.shouldRaisePost, no block above): not on a straight run or a cross. Then a state per top the block
    above gives it (wall_tops.py: tall arms, a raised optional post), found by its name in the rule set's patterns."""
    def arms(sides):
        up = not sides or ("north" in sides) != ("south" in sides) or ("east" in sides) != ("west" in sides)
        return {"up": "true" if up else "false", **{s: "low" if s in sides else "none" for s in common.SIDES}}

    default, states = _connected(ctx, family, ident, block_type, arms)
    patterns = block_type["ConnectedBlockRuleSet"]["TemplateShapeBlockPatterns"]
    shared = {k: v for k, v in states[DEFAULT].items() if k not in ("CustomModel", "HitboxType")}
    for state, sides, tall, post in wall_tops.looks({DEFAULT: set(), **SHAPES}):
        patterns[state] = f"*{ident}_State_Definitions_{state}"
        look = common.look(ctx, family, f"{ident}_{state}", family.blocks[0], wall_tops.props(sides, tall, post))
        states[state] = {**shared, **look}
    return default, states


def stairs(ctx, family, ident, block_type):
    """Straight stairs, and the vanilla corner states drawn by DO's outer and inner stairs (roof.CORNERS)."""
    base = {"facing": "north", "half": "bottom"}
    default = _model(ctx, family, ident, {**base, "shape": "straight"})
    return default, {state: {"CustomModel": _model(ctx, family, ident + "_" + state, {**base, "shape": shape})}
                     for state, shape in roof.CORNERS.items()}


def slab(ctx, family, ident, block_type):
    """A bottom slab; its vanilla "Block" state (two slabs merged) becomes DO's double slab, a model like the rest,
    gathered as the default material, giving two slabs back."""
    block = block_type["State"]["Definitions"]["Block"]
    block.pop("Textures", None)
    block["DrawType"] = "Model"
    material = ctx.assets.item(common.defaults(ctx, family)[0])["BlockType"]
    breaking = block["Gathering"]["Breaking"]
    breaking["GatherType"] = material.get("Gathering", {}).get("Breaking", {}).get("GatherType", breaking["GatherType"])
    # Two slabs back as ItemId + Quantity (vanilla roofs do the same), not the vanilla contained DropList: a runtime
    # variant renames a plain ItemId to its own item, it cannot rewrite a contained asset.
    del breaking["DropList"]
    breaking.update({"ItemId": ident, "Quantity": 2})
    default = _model(ctx, family, ident, {"type": "bottom"})
    return default, {"Block": {"CustomModel": _model(ctx, family, ident + "_Block", {"type": "double"})}}


def gate(ctx, family, ident, block_type):
    """One closed gate for every state (the vanilla animations open it): posts fixed, each leaf under the node its
    animation turns, hinged on its post."""
    props = {"facing": "north", "in_wall": "false", "open": "false"}
    model = faces.clean(assemble.state_model(ctx.root, family, family.blocks[0], props))
    ctx.sources.append((f"{family.blocks[0]} {props}", model))
    parts = []
    for group, hinge, inside in GATE_LEAVES:
        leaf = [e for e in model["elements"] if inside(e)]
        parts.append(convert.to_blockymodel({**model, "elements": leaf}, family, group, convert.hytale(hinge)))
    leaves = {id(e) for group in GATE_LEAVES for e in model["elements"] if group[2](e)}
    posts = [e for e in model["elements"] if id(e) not in leaves]
    parts.append(convert.to_blockymodel({**model, "elements": posts}, family))
    blockymodel = parts.pop()
    root = blockymodel["nodes"][0]
    for part in parts:
        root["children"].extend(part["nodes"][0]["children"])
    for index, n in enumerate(walk([root]), start=1):
        n["id"] = str(index)
        if n.get("shape", {}).get("type") != "none":
            n["name"] = "E" + str(index)
    return common.write_model(ctx, ident, blockymodel), {}


LOOKS = {"Fence": fence, "FenceGate": gate, "Wall": wall, "Stairs": stairs, "Slab": slab}
