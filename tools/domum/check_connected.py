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
    for ident in ("HyColony_DO_Pillar_Round", "HyColony_DO_Pillar_Voxel", "HyColony_DO_Pillar_Square"):
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
    shapes = template(ctx, "HyColony_DO_PaneConnectedBlockTemplate")
    assert set(shapes["Shapes"]) == {"Post", "End", "Straight", "Corner", "T_Junction", "Cross_Junction"}
    walls = [i for i in ctx.items if i.startswith("HyColony_DO_PaperWall")]
    assert sorted(walls) == ["HyColony_DO_PaperWall", "HyColony_DO_PaperWall_Tiled"], walls
    for ident in walls:
        rules = ctx.items[ident]["BlockType"]["ConnectedBlockRuleSet"]
        assert rules["TemplateShapeAssetId"] == "HyColony_DO_PaneConnectedBlockTemplate", ident
        assert set(rules["TemplateShapeBlockPatterns"]) == set(shapes["Shapes"]), ident
        every_state_declares_its_hitbox(ctx, ident)
    end = shapes["Shapes"]["End"]["PatternsToMatchAnyOf"][0]["RulesToMatch"]
    assert [r["Position"] for r in end if r["IncludeOrExclude"] == "Include"] == [{"X": 0, "Y": 0, "Z": -1}]
    low, high = bounds(ctx.models["HyColony_DO_PaperWall_End"]["nodes"])
    assert low[2] == -16 and high[2] < 4, (low, high)
    cross = ctx.items["HyColony_DO_PaperWall"]["BlockType"]["State"]["Definitions"]["Cross_Junction"]
    boxes = hitbox(ctx, cross["HitboxType"])
    assert min(b["Min"]["X"] for b in boxes) == 0 and max(b["Max"]["Z"] for b in boxes) == 1, boxes


def compat_uses_do_models_and_vanilla_shapes():
    """Fences, gates, walls, stairs and slabs keep their vanilla equivalent's rules, states and hitboxes, draw every
    state with a DO model and name our blocks only; the gate's leaves hang on the nodes the door animation turns."""
    from blocks import compat

    ctx = generate_into_temp()
    for name, vanilla_id in compat.VANILLA.items():
        ours = ctx.items["HyColony_DO_" + name]["BlockType"]
        theirs = ctx.assets.item(vanilla_id)["BlockType"]
        assert ours.get("ConnectedBlockRuleSet", {}).get("Type") == theirs.get("ConnectedBlockRuleSet", {}).get("Type")
        assert ours.get("HitboxType") == theirs.get("HitboxType") and ours["VariantRotation"] == theirs["VariantRotation"]
        states = ours.get("State", {}).get("Definitions", {})
        assert set(states) == set(theirs.get("State", {}).get("Definitions", {})), name
        for look in [ours] + [s for s in states.values() if "CustomModel" in s]:
            assert look["CustomModel"].startswith("Blocks/HyColony/DO/"), name
        assert not any(v in json.dumps(ours) for v in compat.VANILLA.values()), name
    # Each leaf hangs on its post: the door animations turn "Door" and "Door2" opposite ways, to the same side.
    hinges = {n["name"]: n["position"] for n in walk(ctx.models["HyColony_DO_FenceGate"]["nodes"])}
    assert hinges["Door"] == {"x": -14, "y": 0, "z": 0} and hinges["Door2"] == {"x": 14, "y": 0, "z": 0}, hinges
    block = ctx.items["HyColony_DO_Slab"]["BlockType"]["State"]["Definitions"]["Block"]
    assert block["DrawType"] == "Model" and "Textures" not in block
    # A slab clicked on its top becomes the Block state: the item's vanilla interaction, matching our slab.
    merge = ctx.items["HyColony_DO_Slab"]["Interactions"]["Secondary"]["Interactions"][0]
    assert merge["Parent"] == "Half_Block" and merge["Matchers"][0]["Block"]["Id"] == "HyColony_DO_Slab", merge


def wall_post_rises_like_minecraft():
    """Minecraft's WallBlock.shouldRaisePost: no post on a straight run or a cross, a post on a corner or a T."""
    ctx = generate_into_temp()

    def has_post(name):
        low, high = bounds(ctx.models[name]["nodes"])
        return high[1] > 29

    assert not has_post("HyColony_DO_Wall") and not has_post("HyColony_DO_Wall_Cross")
    assert has_post("HyColony_DO_Wall_Corner") and has_post("HyColony_DO_Wall_T")


def run():
    """Runs this module's checks; an AssertionError names the failing case."""
    pillar_has_four_closed_shapes()
    paper_wall_connects_to_its_neighbours()
    compat_uses_do_models_and_vanilla_shapes()
    wall_post_rises_like_minecraft()
