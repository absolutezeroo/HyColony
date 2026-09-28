"""Checks of the Domum Ornamentum shapes chosen by their neighbours (run by check.py): pillars and paper walls."""

import json

from check_pack import generate_into_temp
from models import walk

TEMPLATES = "Server/Item/CustomConnectedBlockTemplates/"


def template(ctx, name):
    return json.loads((ctx.pack / TEMPLATES / (name + ".json")).read_text(encoding="utf-8"))


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
    shapes = template(ctx, "HyColony_DO_PillarConnectedBlockTemplate")
    assert set(shapes["Shapes"]) == {"Full", "Base", "Base_Inverted", "Middle"} and shapes["DefaultShape"] == "Full"
    for ident in ("HyColony_DO_Pillar_Round", "HyColony_DO_Pillar_Voxel", "HyColony_DO_Pillar_Square"):
        block = ctx.items[ident]["BlockType"]
        assert set(block["State"]["Definitions"]) == {"Base", "Base_Inverted", "Middle"}, ident
        assert block["ConnectedBlockRuleSet"]["TemplateShapeBlockPatterns"]["Full"] == ident
        for suffix in ("", "_Base", "_Base_Inverted", "_Middle"):
            assert top_and_bottom_faces(ctx.models[ident + suffix]), ident + suffix


def paper_wall_connects_to_its_neighbours():
    """A paper wall is a post alone, then an end, straight run, corner, T or cross by its paper wall neighbours."""
    ctx = generate_into_temp()
    shapes = template(ctx, "HyColony_DO_PaneConnectedBlockTemplate")
    assert set(shapes["Shapes"]) == {"Post", "End", "Straight", "Corner", "T_Junction", "Cross_Junction"}
    walls = [i for i in ctx.items if i.startswith("HyColony_DO_PaperWall")]
    assert sorted(walls) == ["HyColony_DO_PaperWall", "HyColony_DO_PaperWall_Tiled"], walls
    for ident in walls:
        rules = ctx.items[ident]["BlockType"]["ConnectedBlockRuleSet"]
        assert rules["TemplateShapeAssetId"] == "HyColony_DO_PaneConnectedBlockTemplate", ident
        assert set(rules["TemplateShapeBlockPatterns"]) == set(shapes["Shapes"]), ident


def run():
    """Runs this module's checks; an AssertionError names the failing case."""
    pillar_has_four_closed_shapes()
    paper_wall_connects_to_its_neighbours()
