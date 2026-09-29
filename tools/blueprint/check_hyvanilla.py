"""Vérifications de hyvanilla.py : python -m blueprint.check_hyvanilla (depuis tools/).

Une AssertionError nomme le cas.
"""
from __future__ import annotations

from collections import Counter
from pathlib import Path

from . import hyvanilla
from .blueprint import Blueprint
from .converter import Converter
from .validation import load_known_ids, unknown_targets

MC_COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
             "purple", "blue", "brown", "green", "red", "black"]


def _targets(names: list, props: dict | None = None) -> list:
    grid = {(i, 0, 0): {"Name": n, "Properties": props or {}} for i, n in enumerate(names)}
    bp = Blueprint(Path("t.blueprint"), (len(names), 1, 1), grid, [], (0, 0, 0), "test")
    return [c.mapping for c in sorted(Converter().convert(bp).cells, key=lambda c: c.pos)]


def every_carpet_is_a_real_hyvanilla_carpet():
    items = hyvanilla.item_ids()
    for color in MC_COLORS:
        (m,) = _targets([f"minecraft:{color}_carpet"])
        assert m.target in items, (color, m.target)


def an_empty_flower_pot_is_the_terracotta_pot():
    (m,) = _targets(["minecraft:flower_pot"])
    assert m.target == "HyVanilla_Flower_Pot_Orange", m


def a_potted_flower_keeps_its_flower():
    (m,) = _targets(["minecraft:potted_poppy"])
    assert m.target == "*HyVanilla_Flower_Pot_Orange_State_Definitions_Plant_Flower_Common_Red", m


def potted_saplings_ferns_and_mushrooms_keep_their_plant():
    # The MC plants the converter has no block rule for, but HyVanilla has a pot state for: all of them exist.
    for name, plant in hyvanilla.POTTED_PLANTS.items():
        (m,) = _targets([f"minecraft:potted_{name}"])
        assert m.target == f"*HyVanilla_Flower_Pot_Orange_State_Definitions_{plant}", (name, m)


def a_potted_plant_without_equivalent_is_an_empty_pot():
    (m,) = _targets(["minecraft:potted_crimson_fungus"])
    assert m.target == "HyVanilla_Flower_Pot_Orange" and any("non conservée" in n for n in m.notes), m


def a_bed_is_placed_on_its_head_cell_turned_like_minecraft():
    # HyVanilla's bed at rotation 0: head on the origin cell, body towards +Z (Hitboxes/HyVanilla/HyVanilla_Bed.json),
    # i.e. MC facing=north (the head is on the facing side of the foot); the yaw then turns it (Rotation.rotateY).
    for facing, yaw in (("north", 0), ("west", 1), ("south", 2), ("east", 3)):
        (m,) = _targets(["minecraft:red_bed"], {"part": "head", "facing": facing})
        assert (m.target, m.rotation) == ("HyVanilla_Bed_Red", yaw), (facing, m)


def a_bed_foot_is_left_to_the_head():
    (m,) = _targets(["minecraft:light_blue_bed"], {"part": "foot", "facing": "north"})
    assert m.skip and m.target is None, m
    (head,) = _targets(["minecraft:light_blue_bed"], {"part": "head", "facing": "north"})
    assert head.target == "HyVanilla_Bed_Blue_Light", head


def every_hyvanilla_target_is_known_to_the_validation():
    names = [f"minecraft:{c}_{k}" for c in MC_COLORS for k in ("carpet", "bed")]
    names += ["minecraft:flower_pot", "minecraft:potted_poppy"]
    known, _ = load_known_ids()
    targets = Counter(m.target for m in _targets(names, {"part": "head"}) if m.target)
    assert not unknown_targets(targets, known), unknown_targets(targets, known)


def run():
    every_carpet_is_a_real_hyvanilla_carpet()
    an_empty_flower_pot_is_the_terracotta_pot()
    a_potted_flower_keeps_its_flower()
    potted_saplings_ferns_and_mushrooms_keep_their_plant()
    a_potted_plant_without_equivalent_is_an_empty_pot()
    a_bed_is_placed_on_its_head_cell_turned_like_minecraft()
    a_bed_foot_is_left_to_the_head()
    every_hyvanilla_target_is_known_to_the_validation()
    print("blueprint hyvanilla check: OK")


if __name__ == "__main__":
    run()
