"""Vérifications des règles ajoutées pour les huttes de Medieval Oak : python -m blueprint.check_medievaloak (depuis
tools/). Une AssertionError nomme le cas.
"""
from __future__ import annotations

from pathlib import Path

from .blueprint import Blueprint
from .converter import Converter
from .families import CHEST_YAW_OFFSET, slab_rotation, wall_outward_yaw, yaw_for


def _mappings(grid: dict) -> dict:
    bp = Blueprint(Path("t.blueprint"), (9, 3, 9), grid, [], (0, 0, 0), "test")
    return {c.pos: c.mapping for c in Converter().convert(bp).cells}


def _one(name: str, props: dict | None = None):
    return _mappings({(4, 1, 4): {"Name": name, "Properties": props or {}}})[(4, 1, 4)]


def a_nether_brick_slab_is_a_runic_dark_brick_half():
    for kind in ("bottom", "top"):
        m = _one("minecraft:nether_brick_slab", {"type": kind, "waterlogged": "false"})
        assert (m.target, m.rotation) == ("Rock_Runic_Dark_Brick_Half", slab_rotation({"type": kind})), (kind, m)


def a_rose_bush_is_a_tall_red_flower_on_its_lower_half():
    m = _one("minecraft:rose_bush", {"half": "lower"})
    assert (m.target, m.rotation) == ("Plant_Flower_Tall_Red", 0), m
    # Plant_Flower_Tall_Red fills one cell (hitbox Plant_Full): the upper half is cleared, not left to the terrain.
    m = _one("minecraft:rose_bush", {"half": "upper"})
    assert m.target == "Empty" and not m.unmapped, m


def a_loom_is_a_crate_not_a_two_cell_bench():
    # Bench_Loom is a Crafting bench two cells wide: it would become the hut's bench and overlap its neighbour.
    assert _one("minecraft:loom", {"facing": "east"}).target == "Furniture_Village_Crate"


def a_stash_is_a_small_chest_facing_like_minecraft():
    for facing in ("north", "east", "south", "west"):
        m = _one("minecolonies:blockstash", {"facing": facing})
        expected = (yaw_for({"facing": facing}) + CHEST_YAW_OFFSET) % 4
        assert (m.target, m.rotation) == ("Furniture_Crude_Chest_Small", expected), (facing, m)


def an_end_rod_is_a_candle():
    assert _one("minecraft:end_rod", {"facing": "up"}).target == "Furniture_Crude_Candle"


def a_wall_torch_hangs_on_the_full_back_of_a_stair():
    # A Minecraft stair's full face is its facing side: a torch facing east hangs on a stair west of it facing east.
    torch = {"Name": "minecraft:wall_torch", "Properties": {"facing": "east"}}
    stair = {"Name": "minecraft:oak_stairs", "Properties": {"facing": "east", "half": "bottom", "shape": "straight"}}
    m = _mappings({(4, 1, 4): torch, (3, 1, 4): stair})[(4, 1, 4)]
    assert (m.target, m.rotation) == ("Wood_Torch_Wall", wall_outward_yaw({"facing": "east"})), m
    side = dict(stair, Properties=dict(stair["Properties"], facing="north"))
    m = _mappings({(4, 1, 4): torch, (3, 1, 4): side})[(4, 1, 4)]
    assert m.unmapped, m
    # An outer corner stair's facing side is only half full.
    outer = dict(stair, Properties=dict(stair["Properties"], shape="outer_left"))
    m = _mappings({(4, 1, 4): torch, (3, 1, 4): outer})[(4, 1, 4)]
    assert m.unmapped, m


def a_scarecrow_is_the_field_block_on_its_lower_half():
    # MC places a plan's scarecrow (blockhutfield) and registers it as a field (FieldPlacementHandler): no hut marker.
    for facing in ("north", "east", "south", "west"):
        m = _one("minecolonies:blockhutfield", {"half": "lower", "facing": facing})
        assert (m.target, m.rotation) == ("HyColony_Field", yaw_for({"facing": facing})), (facing, m)
    m = _one("minecolonies:blockhutfield", {"half": "upper", "facing": "east"})
    assert m.target is None and not m.unmapped, m


def run():
    a_scarecrow_is_the_field_block_on_its_lower_half()
    a_nether_brick_slab_is_a_runic_dark_brick_half()
    a_rose_bush_is_a_tall_red_flower_on_its_lower_half()
    a_loom_is_a_crate_not_a_two_cell_bench()
    a_stash_is_a_small_chest_facing_like_minecraft()
    an_end_rod_is_a_candle()
    a_wall_torch_hangs_on_the_full_back_of_a_stair()
    print("blueprint medievaloak check: OK")


if __name__ == "__main__":
    run()
