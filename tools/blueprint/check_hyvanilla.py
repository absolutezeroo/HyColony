"""Vérifications de hyvanilla.py : python -m blueprint.check_hyvanilla (depuis tools/). Une AssertionError nomme le cas."""
from __future__ import annotations

from collections import Counter
from pathlib import Path

from . import hyvanilla
from .blueprint import Blueprint
from .converter import Converter
from .validation import load_known_ids, unknown_targets

MC_COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
             "purple", "blue", "brown", "green", "red", "black"]


def _targets(names: list[str]) -> list:
    bp = Blueprint(Path("t.blueprint"), (len(names), 1, 1), {(i, 0, 0): {"Name": n} for i, n in enumerate(names)},
                   [], (0, 0, 0), "test")
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


def a_potted_plant_without_equivalent_is_an_empty_pot():
    (m,) = _targets(["minecraft:potted_crimson_fungus"])
    assert m.target == "HyVanilla_Flower_Pot_Orange" and any("non conservée" in n for n in m.notes), m


def every_hyvanilla_target_is_known_to_the_validation():
    names = [f"minecraft:{c}_carpet" for c in MC_COLORS] + ["minecraft:flower_pot", "minecraft:potted_poppy"]
    known, _ = load_known_ids()
    targets = Counter(m.target for m in _targets(names))
    assert not unknown_targets(targets, known), unknown_targets(targets, known)


def run():
    every_carpet_is_a_real_hyvanilla_carpet()
    an_empty_flower_pot_is_the_terracotta_pot()
    a_potted_flower_keeps_its_flower()
    a_potted_plant_without_equivalent_is_an_empty_pot()
    every_hyvanilla_target_is_known_to_the_validation()
    print("blueprint hyvanilla check: OK")


if __name__ == "__main__":
    run()
