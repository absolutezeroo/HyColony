"""Vérifications des blocs de dev Structurize : python -m blueprint.check_placeholders (depuis tools/).

Voir docs/research/structurize-placeholders.md et la spec 2026-09-29-hycolony-blueprint-placeholders-design.md.
Une AssertionError nomme le cas.
"""
from __future__ import annotations

from pathlib import Path

from .blueprint import Blueprint, _find_anchor, anchor_warnings
from .converter import Converter, Options

SOLID = "HyColony_Placeholder_Solid"
FLUID = "HyColony_Placeholder_Fluid"


def _warnings(root: dict, grid: dict) -> list[str]:
    return anchor_warnings(grid, _find_anchor(root, grid)[0])


def _bp(grid: dict, anchor=(0, 0, 0)) -> Blueprint:
    return Blueprint(Path("test.blueprint"), (4, 4, 4), grid, [], anchor, "test")


def _convert(names: dict) -> tuple[dict, dict]:
    """Position -> (cible, fluide) pour une grille {pos: nom}, et le prefab."""
    bp = _bp({pos: {"Name": name} for pos, name in names.items()})
    result = Converter().convert(bp)
    return {c.pos: (c.mapping.target, c.mapping.fluid) for c in result.cells}, result.prefab()


def solid_substitution_becomes_hycolonys_solid_placeholder():
    cells, _ = _convert({(1, 0, 0): "structurize:blocksolidsubstitution"})
    assert cells[(1, 0, 0)] == (SOLID, None), cells


def fluid_substitution_becomes_hycolonys_fluid_placeholder():
    cells, _ = _convert({(1, 0, 0): "structurize:blockfluidsubstitution"})
    assert cells[(1, 0, 0)] == (FLUID, None), cells


def plain_substitution_writes_nothing():
    cells, prefab = _convert({(1, 0, 0): "structurize:blocksubstitution"})
    assert cells[(1, 0, 0)][0] is None and not prefab["blocks"], (cells, prefab)


def tag_substitution_without_replacement_is_air():
    cells, _ = _convert({(1, 0, 0): "structurize:blocktagsubstitution"})
    assert cells[(1, 0, 0)] == ("Empty", None), cells


def water_and_lava_go_to_the_fluids_array():
    cells, prefab = _convert({(1, 0, 0): "minecraft:water", (2, 0, 0): "minecraft:lava"})
    assert cells[(1, 0, 0)] == (None, "Water_Source") and cells[(2, 0, 0)] == (None, "Lava_Source"), cells
    assert not any(b["name"].startswith("Fluid_") for b in prefab["blocks"]), prefab["blocks"]
    assert {(f["x"], f["name"], f["level"]) for f in prefab["fluids"]} == {(1, "Water_Source", 1),
                                                                          (2, "Lava_Source", 1)}, prefab


def flowing_water_is_not_a_source():
    # A source only where MC has one (level 0): a flowing cell becomes a permanent source that floods (the review
    # found 12 on the edge of small_crossroads3); Hytale's flow rebuilds it from the sources.
    bp = _bp({(1, 0, 0): {"Name": "minecraft:water", "Properties": {"level": "3"}},
              (2, 0, 0): {"Name": "minecraft:water", "Properties": {"level": "0"}}})
    prefab = Converter().convert(bp).prefab()
    assert [(f["x"], f["name"]) for f in prefab.get("fluids", [])] == [(2, "Water_Source")], prefab


def flowing_water_is_cleared_like_air():
    # The flowing cell was water in MC, not the terrain it now stands on: force it empty.
    bp = _bp({(1, 0, 0): {"Name": "minecraft:water", "Properties": {"level": "3"}}})
    cells = {c.pos: c.mapping.target for c in Converter().convert(bp).cells}
    assert cells[(1, 0, 0)] == "Empty", cells


def without_editor_blocks_every_substitution_is_left_out():
    names = ["structurize:blocksolidsubstitution", "structurize:blockfluidsubstitution",
             "structurize:blocktagsubstitution"]
    bp = _bp({(i, 0, 0): {"Name": n} for i, n in enumerate(names)})
    result = Converter(Options(editor_blocks=False)).convert(bp)
    assert not result.prefab()["blocks"] and not result.unmapped(), result.unmapped()


def an_anchor_that_is_not_the_hut_is_reported():
    grid = {(0, 0, 0): {"Name": "minecraft:stone"}, (2, 0, 0): {"Name": "minecolonies:blockhutbuilder"}}
    root = {"optional_data": {"structurize": {"primary_offset": {"x": 0, "y": 0, "z": 0}}}}
    anchor, _ = _find_anchor(root, grid)
    assert anchor == (0, 0, 0)
    assert any("n'est pas un bloc de hutte" in w for w in _warnings(root, grid)), _warnings(root, grid)


def the_trace_keeps_written_placeholders():
    # Air is left out: its rule is always "air -> Empty", and it would be most of the trace.
    bp = _bp({(1, 0, 0): {"Name": "structurize:blockfluidsubstitution"}, (2, 0, 0): {"Name": "minecraft:air"}})
    assert [t["cible"] for t in Converter().convert(bp).trace()] == [FLUID]


def the_anchor_is_structurizes_primary_offset():
    # barracks: the first colonybuilding tile entity is a tower, the primary offset is the barracks hut.
    root = {"tile_entities": [{"id": "minecolonies:colonybuilding", "x": 5, "y": 1, "z": 5}],
            "optional_data": {"structurize": {"primary_offset": {"x": 2, "y": 1, "z": 3}}}}
    anchor, method = _find_anchor(root, {})
    assert anchor == (2, 1, 3) and "primary_offset" in method, (anchor, method)


def run():
    solid_substitution_becomes_hycolonys_solid_placeholder()
    fluid_substitution_becomes_hycolonys_fluid_placeholder()
    plain_substitution_writes_nothing()
    tag_substitution_without_replacement_is_air()
    water_and_lava_go_to_the_fluids_array()
    flowing_water_is_not_a_source()
    flowing_water_is_cleared_like_air()
    without_editor_blocks_every_substitution_is_left_out()
    an_anchor_that_is_not_the_hut_is_reported()
    the_trace_keeps_written_placeholders()
    the_anchor_is_structurizes_primary_offset()
    print("blueprint placeholders check: OK")


if __name__ == "__main__":
    run()
