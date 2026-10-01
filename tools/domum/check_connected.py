"""Checks of the Domum Ornamentum shapes chosen by their neighbours (run by check.py): pillars, paper walls and the
vanilla-compatible families."""

import json

from blocks.common import HITBOXES, TEMPLATES
from check_pack import generate_into_temp
from models import bounds, walk


def template(ctx, name):
    """A connection template the run wrote, by id."""
    return json.loads((ctx.pack / TEMPLATES / (name + ".json")).read_text(encoding="utf-8"))


def hitbox(ctx, name):
    """The boxes of a hitbox id: one the run wrote, or the full block."""
    if name == "Full":
        return [{"Min": {"X": 0, "Y": 0, "Z": 0}, "Max": {"X": 1, "Y": 1, "Z": 1}}]
    return json.loads((ctx.pack / HITBOXES / (name + ".json")).read_text(encoding="utf-8"))["Boxes"]


def every_state_declares_its_hitbox(ctx, ident):
    """A state inherits its block's HitboxType when it names none: every state of ident names its own."""
    for state, look in ctx.items[ident]["BlockType"]["State"]["Definitions"].items():
        assert "HitboxType" in look, (ident, state)


def top_and_bottom_faces(model):
    """Whether the blockymodel shows a face up at the block's top and a face down at its bottom: a box's top or
    bottom face, or a cap (a quad facing +Y or -Y, faces.cap_ends)."""
    found = set()
    for n in walk(model["nodes"]):
        shape = n.get("shape", {})
        centre = n["position"]["y"] + shape.get("offset", {}).get("y", 0)
        if shape.get("type") == "quad":
            normal = shape["settings"]["normal"]
            found.update({"top"} if normal == "+Y" and abs(centre - 32) < 1e-3 else set())
            found.update({"bottom"} if normal == "-Y" and abs(centre) < 1e-3 else set())
        elif shape.get("type") == "box":
            half = shape["settings"]["size"]["y"] * shape["stretch"]["y"] / 2
            found.update({"top"} if "top" in shape["textureLayout"] and abs(centre + half - 32) < 1e-3 else set())
            found.update({"bottom"} if "bottom" in shape["textureLayout"] and abs(centre - half) < 1e-3 else set())
    return found == {"top", "bottom"}


def pillar_has_four_closed_shapes():
    """A pillar is alone (Full), the base or capital of a stack, or a column in between, like DO's column
    property; every shape, the open-ended column included, is closed at both ends."""
    ctx = generate_into_temp()
    tags = set()
    for ident in ("HyDomum_Pillar_Round", "HyDomum_Pillar_Voxel", "HyDomum_Pillar_Square"):
        block = ctx.items[ident]["BlockType"]
        rules = block["ConnectedBlockRuleSet"]
        shapes = template(ctx, rules["TemplateShapeAssetId"])
        assert set(shapes["Shapes"]) == {"Full", "Base", "Base_Inverted", "Middle"} and shapes["DefaultShape"] == "Full"
        tags |= {tag for shape in shapes["Shapes"].values() for side in shape["FaceTags"].values() for tag in side}
        assert set(block["State"]["Definitions"]) == {"Base", "Base_Inverted", "Middle"}, ident
        assert rules["TemplateShapeBlockPatterns"]["Full"] == ident
        every_state_declares_its_hitbox(ctx, ident)
        for suffix in ("", "_Base", "_Base_Inverted", "_Middle"):
            assert top_and_bottom_faces(ctx.models[ident + suffix]), ident + suffix
        # Base: the stack's foot, its plinth at the bottom (DO pillar_base); Base_Inverted: its capital on top.
        assert widest_height(ctx.models[ident + "_Base"]) < 16 < widest_height(ctx.models[ident + "_Base_Inverted"])
    # DO joins a pillar to the same pillar block only (PillarBlock: getBlock() == this).
    assert len(tags) == 3, tags


def widest_height(model):
    """The height (units) of the centre of model's widest box."""
    boxes = [n for n in walk(model["nodes"]) if n.get("shape", {}).get("type") == "box"]
    widest = max(boxes, key=lambda n: n["shape"]["settings"]["size"]["x"] * n["shape"]["stretch"]["x"])
    return widest["position"]["y"] + widest["shape"]["offset"]["y"]


def paper_wall_connects_to_its_neighbours():
    """A paper wall is a post alone, then an end, straight run, corner, T or cross by its paper wall neighbours;
    an arm reaches toward each neighbour of its pattern, and every shape collides as it looks."""
    ctx = generate_into_temp()
    shapes = template(ctx, "HyDomum_PaneConnectedBlockTemplate")
    assert set(shapes["Shapes"]) == {"Post", "End", "Straight", "Corner", "T_Junction", "Cross_Junction"}
    walls = [i for i in ctx.items if i.startswith("HyDomum_PaperWall")]
    assert sorted(walls) == ["HyDomum_PaperWall", "HyDomum_PaperWall_Tiled"], walls
    for ident in walls:
        rules = ctx.items[ident]["BlockType"]["ConnectedBlockRuleSet"]
        assert rules["TemplateShapeAssetId"] == "HyDomum_PaneConnectedBlockTemplate", ident
        assert set(rules["TemplateShapeBlockPatterns"]) == set(shapes["Shapes"]), ident
        every_state_declares_its_hitbox(ctx, ident)
    end = shapes["Shapes"]["End"]["PatternsToMatchAnyOf"][0]["RulesToMatch"]
    assert [r["Position"] for r in end if r["IncludeOrExclude"] == "Include"] == [{"X": 0, "Y": 0, "Z": -1}]
    low, high = bounds(ctx.models["HyDomum_PaperWall_End"]["nodes"])
    assert low[2] == -16 and high[2] < 4, (low, high)
    cross = ctx.items["HyDomum_PaperWall"]["BlockType"]["State"]["Definitions"]["Cross_Junction"]
    boxes = hitbox(ctx, cross["HitboxType"])
    assert min(b["Min"]["X"] for b in boxes) == 0 and max(b["Max"]["Z"] for b in boxes) == 1, boxes


def compat_uses_do_models_and_vanilla_shapes():
    """Fences, gates, walls, stairs and slabs keep their vanilla equivalent's rule type, states and hitboxes (fences
    and walls take our rule set and add the lone post and the end), draw every state with a DO model and name our
    blocks only; the gate's leaves hang on the nodes the door animation turns."""
    from blocks import compat

    ctx = generate_into_temp()
    for name, vanilla_id in compat.VANILLA.items():
        ours = ctx.items["HyDomum_" + name]["BlockType"]
        theirs = ctx.assets.item(vanilla_id)["BlockType"]
        rule_type = compat.RULES if name in ("Fence", "Wall") else theirs.get("ConnectedBlockRuleSet", {}).get("Type")
        assert ours.get("ConnectedBlockRuleSet", {}).get("Type") == rule_type, name
        assert ours.get("HitboxType") == theirs.get("HitboxType"), name
        assert ours["VariantRotation"] == theirs["VariantRotation"], name
        states = ours.get("State", {}).get("Definitions", {})
        added = set(compat.NEW_STATES) if name in ("Fence", "Wall") else set()
        assert set(states) == set(theirs.get("State", {}).get("Definitions", {})) | added, name
        for look in [ours] + [s for s in states.values() if "CustomModel" in s]:
            assert look["CustomModel"].startswith("Blocks/HyDomum/"), name
        assert not any(v in json.dumps(ours) for v in compat.VANILLA.values()), name
    # Each leaf hangs on its post: the door animations turn "Door" and "Door2" opposite ways, to the same side.
    hinges = {n["name"]: n["position"] for n in walk(ctx.models["HyDomum_FenceGate"]["nodes"])}
    assert hinges["Door"] == {"x": -14, "y": 0, "z": 0} and hinges["Door2"] == {"x": 14, "y": 0, "z": 0}, hinges
    block = ctx.items["HyDomum_Slab"]["BlockType"]["State"]["Definitions"]["Block"]
    assert block["DrawType"] == "Model" and "Textures" not in block
    # Two slabs back, named by ItemId (a string a runtime variant renames), not a contained DropList asset.
    breaking = block["Gathering"]["Breaking"]
    assert breaking["ItemId"] == "HyDomum_Slab" and breaking["Quantity"] == 2 and "DropList" not in breaking
    # A slab clicked on its top becomes the Block state: the item's vanilla interaction, matching our slab.
    merge = ctx.items["HyDomum_Slab"]["Interactions"]["Secondary"]["Interactions"][0]
    assert merge["Parent"] == "Half_Block" and merge["Matchers"][0]["Block"]["Id"] == "HyDomum_Slab", merge


def fence_and_wall_shape_by_their_neighbours():
    """A fence or wall is a lone post alone, then an end, straight run, corner, T or cross, as Minecraft's: every
    shape but the post has a pattern turned in the four directions, so its turn follows its neighbours (the vanilla
    template's patternless Straight kept a broken corner's turn); the vanilla shapes keep the vanilla sides, so their
    states keep their hitboxes; a gate's sides come from its own rules."""
    from blocks import common, compat

    ctx = generate_into_temp()
    ours = template(ctx, compat.TEMPLATE)
    shapes = ours["Shapes"]
    assert ours["DefaultShape"] == "Post" and set(shapes) == {"Post", *compat.SHAPES}
    assert not shapes["Post"]["PatternsToMatchAnyOf"]
    vanilla = ctx.assets.json(common.TEMPLATES + "WallConnectedBlockTemplate.json")["Shapes"]
    side_at = {offset: side for side, (offset, _) in common.SIDES.items()}
    expected = {"End": {"north"}, **{shape: {face.lower() for face in vanilla[shape]["FaceTags"]}
                                     for shape in compat.SHAPES if shape != "End"}}
    for shape, look in shapes.items():
        assert all(look["FaceTags"][side.capitalize()] == ["FenceConnection"] for side in common.SIDES), shape
        if shape == "Post":
            continue
        (pattern,) = look["PatternsToMatchAnyOf"]
        assert pattern["AllowedPatternTransformations"] == {"IsCardinallyRotatable": True}, shape
        rules = pattern["RulesToMatch"]
        included = {side_at[tuple(r["Position"][axis] for axis in "XYZ")] for r in rules
                    if r["IncludeOrExclude"] == "Include"}
        assert len(rules) == 4 and included == expected[shape], (shape, included)
    for name in ("Fence", "Wall"):
        ident = "HyDomum_" + name
        block = ctx.items[ident]["BlockType"]
        rules = block["ConnectedBlockRuleSet"]
        assert rules["TemplateShapeAssetId"] == compat.TEMPLATE and set(rules["TemplateShapeBlockPatterns"]) == set(
            shapes), name
        # Our Java rule set (domum/plugin HytaleFenceRules) picks the shape by MC's rule for a fence or a wall.
        assert rules["Type"] == "HyDomum_Fence" and rules["Joins"] == compat.JOINS[name], rules
        # The straight run stays the block itself: placed fences and converted blueprints keep their look.
        assert rules["TemplateShapeBlockPatterns"]["Straight"] == ident, name
        states = block["State"]["Definitions"]
        for state in compat.NEW_STATES:
            # A wall's state is gathered as the wall, like the vanilla states (the block's own gathering differs).
            assert "HitboxType" in states[state] and states[state].get("Gathering") == states["Cross"].get(
                "Gathering"), (name, state)
    assert "Gathering" in ctx.items["HyDomum_Wall"]["BlockType"]["State"]["Definitions"]["End"]
    # The fence alone is its post only; its end reaches north, the side its End pattern includes.
    low, high = bounds(ctx.models["HyDomum_Fence_Post"]["nodes"])
    assert -5 < low[0] and high[0] < 5 and -5 < low[2] and high[2] < 5, (low, high)
    low, high = bounds(ctx.models["HyDomum_Fence_End"]["nodes"])
    assert low[2] == -16 and high[2] < 5 and -5 < low[0] and high[0] < 5, (low, high)
    gate = ctx.items["HyDomum_FenceGate"]["BlockType"]["ConnectedBlockRuleSet"]
    assert gate["TemplateShapeAssetId"] == "WallConnectedBlockTemplate", gate
    assert compat.JOINS == {"Fence": "WoodenFence", "Wall": "Wall"}  # DO's WOODEN_FENCES and WALLS tags


def vanilla_fences_are_named_by_family():
    """The id-map names every vanilla fence, wall and bars block (corner blocks of bars included, gates aside) by its
    exact id under its Minecraft family, for our fences and walls to join; nothing else (a torch on a wall)."""
    from blocks import vanilla_fences

    ctx = generate_into_temp()
    families = json.loads((ctx.pack / "hydomum/id-map.json").read_text(encoding="utf-8"))["connections"]
    assert {k: len(v) for k, v in families.items()} == {"WOODEN_FENCE": 11, "FENCE": 12, "WALL": 43, "PANE": 4}
    assert "Wood_Softwood_Fence" in families["WOODEN_FENCE"] and "Metal_Iron_Fence" in families["FENCE"]
    assert "Rock_Stone_Brick_Wall" in families["WALL"] and "Deco_Iron_Bars_Corner" in families["PANE"]
    every = {i for ids in families.values() for i in ids}
    assert vanilla_fences.pattern_blocks("50%A,B") == ["A", "B"]
    assert not {"Wood_Torch_Wall", "Plant_Vine_Wall", "Wood_Softwood_Fence_Gate", "Soil_Hive_Brick_Fence"} & every


def wall_post_rises_like_minecraft():
    """Minecraft's WallBlock.shouldRaisePost: no post on a straight run or a cross, a post alone, on an end, a corner
    or a T."""
    ctx = generate_into_temp()

    def has_post(name):
        low, high = bounds(ctx.models[name]["nodes"])
        return high[1] > 29

    assert not has_post("HyDomum_Wall") and not has_post("HyDomum_Wall_Cross")
    assert has_post("HyDomum_Wall_Corner") and has_post("HyDomum_Wall_T")
    assert has_post("HyDomum_Wall_Post") and has_post("HyDomum_Wall_End")


def run():
    """Runs this module's checks; an AssertionError names the failing case."""
    pillar_has_four_closed_shapes()
    paper_wall_connects_to_its_neighbours()
    compat_uses_do_models_and_vanilla_shapes()
    fence_and_wall_shape_by_their_neighbours()
    vanilla_fences_are_named_by_family()
    wall_post_rises_like_minecraft()
