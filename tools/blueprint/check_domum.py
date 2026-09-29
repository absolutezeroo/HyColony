"""Vérifications de domum.py : python -m blueprint.check_domum (depuis tools/). Une AssertionError nomme le cas."""
from __future__ import annotations

from . import domum
from . import families as fam


class _Bp:
    """Blueprint minimal : une entité de bloc à l'origine."""

    def __init__(self, texture_data: dict | None = None):
        self.te = {"x": 0, "y": 0, "z": 0, "textureData": texture_data} if texture_data is not None else None

    def tile_entity_at(self, pos):
        return self.te


def _rule(name: str, props: dict, texture_data: dict | None = None, with_materials: bool = False):
    return domum.rule(_Bp(texture_data), (0, 0, 0), name, props, with_materials)


def shingle_follows_the_stairs_adapter():
    p = {"facing": "east", "half": "top", "shape": "outer_left"}
    m = _rule("domum_ornamentum:shingle", p)
    assert m.target == fam.stair_target("HyDomum_Shingle", p) and m.rotation == fam.stair_rotation(p), m


def timber_frame_takes_its_two_materials():
    m = _rule("domum_ornamentum:plain", {"facing": "north"},
              {"minecraft:block/oak_planks": "minecraft:oak_planks",
               "minecraft:block/dark_oak_planks": "domum_ornamentum:paper_extra"}, with_materials=True)
    assert m.target == "HyDomum_TimberFrame_Plain__Wood_Hardwood_Planks__Soil_Clay_Smooth_White", m


def template_mode_keeps_the_default_materials():
    m = _rule("domum_ornamentum:plain", {"facing": "north"}, {"minecraft:block/oak_planks": "minecraft:stone"})
    assert m.target == "HyDomum_TimberFrame_Plain", m


def an_old_component_key_takes_the_remaining_entry():
    m = _rule("domum_ornamentum:shingle_slab", {"facing": "north", "shape": "two_way"},
              {"minecraft:block/acacia_planks": "domum_ornamentum:brick_extra",
               "minecraft:block/dark_oak_planks": "minecraft:oak_planks"}, with_materials=True)
    assert m.target == "*HyDomum_ShingleSlab__Soil_Clay_Brick__Wood_Hardwood_Planks_State_Definitions_Two_Way", m


def a_material_without_equivalent_keeps_the_template():
    m = _rule("domum_ornamentum:vanilla_slab_compat", {"type": "bottom"},
              {"minecraft:block/oak_planks": "minecraft:glass"}, with_materials=True)
    assert m.target == "HyDomum_Slab" and any("sans équivalent" in n for n in m.notes), m


def door_upper_half_is_left_to_the_lower_one():
    m = _rule("domum_ornamentum:fancy_door", {"type": "full", "half": "upper", "facing": "north"})
    assert m.skip and m.target is None, m


def pillar_base_takes_its_state():
    m = _rule("domum_ornamentum:squarepillar", {"column": "pillar_base"})
    assert m.target == "*HyDomum_Pillar_Square_State_Definitions_Base", m


def a_domum_brick_block_becomes_its_hytale_block():
    m = _rule("domum_ornamentum:brown_bricks", {})
    assert m.target == "Soil_Clay_Raw_Brick", m


def every_material_is_accepted_somewhere():
    accepted = set().union(*domum.slot_tags().values())
    bad = {mc: hy for mc, hy in domum.material_table().items() if hy and hy not in accepted}
    assert not bad, bad


def run():
    shingle_follows_the_stairs_adapter()
    timber_frame_takes_its_two_materials()
    template_mode_keeps_the_default_materials()
    an_old_component_key_takes_the_remaining_entry()
    a_material_without_equivalent_keeps_the_template()
    door_upper_half_is_left_to_the_lower_one()
    pillar_base_takes_its_state()
    a_domum_brick_block_becomes_its_hytale_block()
    every_material_is_accepted_somewhere()
    print("blueprint domum check: OK")


if __name__ == "__main__":
    run()
