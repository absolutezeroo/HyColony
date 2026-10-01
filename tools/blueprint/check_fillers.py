"""Vérifications des cases de remplissage des modèles à plusieurs cases : python -m blueprint.check_fillers (depuis
tools/). Une AssertionError nomme le cas.
"""
from __future__ import annotations

from pathlib import Path

from .blueprint import Blueprint
from .converter import Converter, Options
from .fillers import Hitboxes, filler_offsets

# Hitbox du Bench_Farming (X -1..1, Y 0..2) et de l'épouvantail (Y 0..2.3), lues dans les assets du jeu.
BENCH = [{"Min": {"X": -1, "Y": 0, "Z": 0}, "Max": {"X": 1, "Y": 1, "Z": 1}},
         {"Min": {"X": -1, "Y": 1, "Z": 0}, "Max": {"X": 0.1, "Y": 2, "Z": 0.6}}]
SCARECROW = [{"Min": {"X": 0.1, "Y": 0, "Z": 0}, "Max": {"X": 0.9, "Y": 2.3, "Z": 1}}]


def offsets_follow_hytales_rounding_and_yaw():
    # FillerBlockUtil (seuil 0) : une boîte qui dépasse d'un rien prend la case entière.
    assert filler_offsets(SCARECROW, 0) == {(0, 1, 0), (0, 2, 0)}
    assert filler_offsets(BENCH, 0) == {(-1, 0, 0), (-1, 1, 0), (0, 1, 0)}
    # BlockBoundingBoxes.rotate90Y : x' = z, z' = 1 - x.
    assert filler_offsets(BENCH, 1) == {(0, 1, 0), (0, 0, 1), (0, 1, 1)}
    assert filler_offsets(BENCH, 2) == {(1, 0, 0), (1, 1, 0), (0, 1, 0)}
    assert filler_offsets([{"Min": {"X": 0, "Y": 0, "Z": 0}, "Max": {"X": 1, "Y": 1, "Z": 1}}], 3) == set()


def a_state_takes_its_own_hitbox():
    # Each state is its own BlockType (FillerBlockUtil.setFillerBlocksAt reads its HitboxType): an open gate juts out.
    open_out = [{"Min": {"X": 0, "Y": 0, "Z": -0.25}, "Max": {"X": 1, "Y": 1, "Z": 0.65}}]
    unit = [{"Min": {"X": 0, "Y": 0, "Z": 0}, "Max": {"X": 1, "Y": 1, "Z": 1}}]
    hitboxes = Hitboxes({"HyDomum_FenceGate": "Fence_Gate", "HyDomum_FenceGate#OpenDoorOut": "Fence_Gate_Open_Out"},
                        {"Fence_Gate": unit, "Fence_Gate_Open_Out": open_out})
    gate = "*HyDomum_FenceGate__Wood_Hardwood_Planks_State_Definitions_OpenDoorOut"
    assert hitboxes.offsets(gate, 0) == {(0, 0, -1)}, "a negative minimum rounds down"
    assert hitboxes.offsets(gate, 3) == {(1, 0, 0)}
    assert hitboxes.offsets("HyDomum_FenceGate__Wood_Hardwood_Planks", 3) == set()


def an_empty_cell_inside_a_multi_cell_model_is_dropped():
    # Le plan MC a de l'air au-dessus de l'épouvantail : un vide forcé là effacerait le modèle posé.
    hitboxes = Hitboxes({"HyColony_Field": "Scarecrow"}, {"Scarecrow": SCARECROW})
    grid = {(1, 0, 1): {"Name": "minecolonies:blockhutfield", "Properties": {"half": "lower", "facing": "north"}},
            (1, 1, 1): {"Name": "minecraft:air"}, (1, 2, 1): {"Name": "minecraft:air"},
            (1, 3, 1): {"Name": "minecraft:air"}}
    bp = Blueprint(Path("t.blueprint"), (3, 4, 3), grid, [], (0, 0, 0), "test")
    blocks = Converter(Options(hitboxes=hitboxes)).convert(bp).prefab()["blocks"]
    assert [(b["y"], b["name"]) for b in blocks] == [(0, "HyColony_Field"), (3, "Empty")], blocks


def run():
    offsets_follow_hytales_rounding_and_yaw()
    a_state_takes_its_own_hitbox()
    an_empty_cell_inside_a_multi_cell_model_is_dropped()
    print("blueprint fillers check: OK")


if __name__ == "__main__":
    run()
