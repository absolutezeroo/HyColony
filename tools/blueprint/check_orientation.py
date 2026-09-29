"""Vérifications d'orientation (coffres, bibliothèques) : python -m blueprint.check_orientation (depuis tools/).

Une AssertionError nomme le cas.
"""
from __future__ import annotations

from pathlib import Path

from .blueprint import Blueprint
from .converter import Converter


def _mappings(grid: dict, anchor=(0, 0, 0)) -> dict:
    bp = Blueprint(Path("t.blueprint"), (9, 3, 9), grid, [], anchor, "test")
    return {c.pos: c.mapping for c in Converter().convert(bp).cells}


def a_single_chest_faces_like_minecraft():
    # Furniture_Crude_Chest_Small is built like the large chest calibrated in game: at yaw r its front looks away
    # from r (detectors.LARGE_CHEST_YAW_OFFSET), so an MC chest facing north needs rotation 2.
    for facing, rotation in (("north", 2), ("west", 3), ("south", 0), ("east", 1)):
        m = _mappings({(4, 0, 4): {"Name": "minecraft:chest", "Properties": {"facing": facing, "type": "single"}}})
        assert (m[(4, 0, 4)].target, m[(4, 0, 4)].rotation) == ("Furniture_Crude_Chest_Small", rotation), (facing, m)


def a_bookshelf_wall_opens_towards_the_inside():
    # Three bookshelves form the north wall of a room whose hut (the anchor) is south of them: air on both sides,
    # so the open side is the one facing the hut. Village_Bookcase at yaw 0 has its back to the north.
    grid = {(x, 0, 2): {"Name": "minecraft:bookshelf"} for x in (3, 4, 5)}
    grid[(4, 0, 6)] = {"Name": "minecolonies:blockhutbuilder"}
    m = _mappings(grid, anchor=(4, 0, 6))
    assert [m[(x, 0, 2)].rotation for x in (3, 4, 5)] == [0, 0, 0], [m[(x, 0, 2)] for x in (3, 4, 5)]
    grid = {(x, 0, 6): {"Name": "minecraft:bookshelf"} for x in (3, 4, 5)}
    grid[(4, 0, 2)] = {"Name": "minecolonies:blockhutbuilder"}
    m = _mappings(grid, anchor=(4, 0, 2))
    assert [m[(x, 0, 6)].rotation for x in (3, 4, 5)] == [2, 2, 2], [m[(x, 0, 6)] for x in (3, 4, 5)]


def a_lone_rack_faces_like_a_chest():
    # A MineColonies rack faces its placer like a chest (BlockMinecoloniesRack.getStateForPlacement): same offset.
    rack = {"Name": "minecolonies:blockminecoloniesrack",
            "Properties": {"facing": "north", "variant": "blockrackempty"}}
    m = _mappings({(4, 0, 4): rack})
    assert (m[(4, 0, 4)].target, m[(4, 0, 4)].rotation) == ("Furniture_Crude_Chest_Small", 2), m[(4, 0, 4)]


def pumpkins_and_mossy_walls_of_the_farmer_are_converted():
    # A carved pumpkin's face is its model's "front" face, +Z (south) at rotation 0: an MC face north needs 2.
    for name, target in (("jack_o_lantern", "Deco_Halloween_Pumpkin_Scary"),
                         ("carved_pumpkin", "Deco_Halloween_Pumpkin_Cute")):
        cell = {"Name": f"minecraft:{name}", "Properties": {"facing": "north"}}
        m = _mappings({(4, 0, 4): cell})[(4, 0, 4)]
        assert (m.target, m.rotation) == (target, 2), (name, m)
    # Plant_Crop_Pumpkin_Block is a growing crop (seeds, needs soil); the pumpkin itself is the Item's block.
    m = _mappings({(4, 0, 4): {"Name": "minecraft:pumpkin"}})[(4, 0, 4)]
    assert m.target == "Plant_Crop_Pumpkin_Item", m
    m = _mappings({(4, 0, 4): {"Name": "minecraft:melon"}})[(4, 0, 4)]  # no melon in Hytale: the pumpkin, not a crop
    assert m.target == "Plant_Crop_Pumpkin_Item", m
    m = _mappings({(4, 0, 4): {"Name": "minecraft:mossy_stone_brick_wall"}})[(4, 0, 4)]
    assert m.rule == "wall" and m.target.startswith("Rock_Stone_Brick_"), m  # an isolated post becomes a beam
    m = _mappings({(4, 0, 4): {"Name": "minecraft:mossy_cobblestone_wall"}})[(4, 0, 4)]
    assert m.rule == "wall" and m.target.startswith("Rock_Stone_Cobble_"), m


def tools_are_not_blocks():
    from .validation import load_known_ids
    known, _ = load_known_ids()
    assert "HyColony_Build_Tool" not in known and "HyColony_Placeholder_Solid" in known


def run():
    a_single_chest_faces_like_minecraft()
    a_bookshelf_wall_opens_towards_the_inside()
    a_lone_rack_faces_like_a_chest()
    pumpkins_and_mossy_walls_of_the_farmer_are_converted()
    tools_are_not_blocks()
    print("blueprint orientation check: OK")


if __name__ == "__main__":
    run()
