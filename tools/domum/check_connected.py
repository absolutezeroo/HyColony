"""Checks of the Domum Ornamentum shapes chosen by their neighbours (run by check.py): pillars and paper walls."""

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


def run():
    """Runs this module's checks; an AssertionError names the failing case."""
    pillar_has_four_closed_shapes()
    paper_wall_connects_to_its_neighbours()
