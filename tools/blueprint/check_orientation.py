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
    assert m[(4, 0, 2)].rotation == 0, m[(4, 0, 2)]
    grid = {(x, 0, 6): {"Name": "minecraft:bookshelf"} for x in (3, 4, 5)}
    grid[(4, 0, 2)] = {"Name": "minecolonies:blockhutbuilder"}
    m = _mappings(grid, anchor=(4, 0, 2))
    assert m[(4, 0, 6)].rotation == 2, m[(4, 0, 6)]


def run():
    a_single_chest_faces_like_minecraft()
    a_bookshelf_wall_opens_towards_the_inside()
    print("blueprint orientation check: OK")


if __name__ == "__main__":
    run()
