"""Vérifications de domum.py : python -m blueprint.check_domum (depuis tools/). Une AssertionError nomme le cas."""
from __future__ import annotations

import json
import tempfile
from pathlib import Path

from . import domum
from . import families as fam
from .geometry import rot_index


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


def directed_timber_frame_points_its_pattern():
    # DoublePipe from the model drawn facing up: pitch 90 points it south at yaw 0 (Rotation.rotateX), yaw turns it.
    expected = {"up": 0, "down": rot_index(0, 2, 0), "south": rot_index(0, 1, 0), "east": rot_index(1, 1, 0),
                "north": rot_index(2, 1, 0), "west": rot_index(3, 1, 0)}
    for facing, rotation in expected.items():
        m = _rule("domum_ornamentum:side_framed", {"facing": facing})
        assert m.target == "HyDomum_TimberFrame_SideFramed" and m.rotation == rotation, (facing, m)
    assert _rule("domum_ornamentum:plain", {"facing": "west"}).rotation == 0


def floor_trapdoor_keeps_dos_hinge_side():
    # Template at rotation 0 = DO facing=north, hinged on +Z (tools/domum/blocks/door.py).
    m = _rule("domum_ornamentum:fancy_trapdoors", {"type": "full", "facing": "east", "half": "bottom"})
    assert m.target == "HyDomum_FancyTrapdoor_Full" and m.rotation == rot_index(3, 0, 0), m


def ceiling_trapdoor_is_flipped_and_turned_back():
    # Pitch 180 moves the hinge to -Z: yaw 180 puts it back on DO's side, as for stairs.
    m = _rule("domum_ornamentum:vanilla_trapdoors_compat",
              {"type": "horizontally_squiggly_striped", "facing": "north", "half": "top"})
    assert m.rotation == rot_index(2, 2, 0), m
    opened = _rule("domum_ornamentum:vanilla_trapdoors_compat",
                   {"type": "horizontally_squiggly_striped", "facing": "north", "half": "top", "open": "true"})
    assert opened.target.endswith("_State_Definitions_OpenDoorOut"), opened


def isolated_fence_post_is_not_called_a_beam():
    m = _rule("domum_ornamentum:vanilla_fence_compat", {})
    assert m.target == "HyDomum_Fence" and not any("poutre" in n for n in m.notes), m


def every_state_emitted_exists_in_its_template():
    items = domum.RESOURCES.parent / "Server" / "Item" / "Items" / "HyDomum"

    def states(template):
        block = json.loads((items / (template + ".json")).read_text(encoding="utf-8"))["BlockType"]
        return set(block.get("State", {}).get("Definitions", {}))

    for shape in domum.shapes().values():
        sid, have = shape["id"], states(shape["template"])
        wanted = set()
        if sid == "Stairs" or (sid.startswith("Shingle") and not sid.startswith("ShingleSlab")):
            wanted = set(fam._STAIR_STATE.values()) | set(fam._STAIR_STATE_FLIP.values())
        elif sid == "ShingleSlab":
            wanted = set(domum.SHINGLE_SLAB_STATES.values())
        elif sid == "Slab":
            wanted = {"Block"}
        elif sid.startswith("Pillar_"):
            wanted = set(domum.PILLAR_STATES.values())
        elif sid == "FenceGate" or sid.startswith(("Trapdoor_", "FancyTrapdoor_")):
            wanted = {"OpenDoorOut"}
        elif sid in ("Fence", "Wall"):
            wanted = {"Corner"}
        assert wanted <= have, (sid, wanted - have)


def variant_ids_name_shape_and_materials():
    # HyDomum saves a variant as VariantKey.id(): "<shape id>|<material 1>|<material 2>".
    target = "*HyDomum_TimberFrame_Plain__Wood_Hardwood_Planks__Soil_Clay_Smooth_White_State_Definitions_X"
    assert domum.variant_ids([target, "HyDomum_Stairs", "Rock_Stone"]) == {
        "TimberFrame_Plain|Wood_Hardwood_Planks|Soil_Clay_Smooth_White"}


def registering_variants_keeps_the_saved_ones(tmp: Path):
    path = tmp / "variants.json"
    path.write_text(json.dumps({"schemaVersion": 1, "variants": ["Stairs|Rock_Stone"]}), encoding="utf-8")
    added = domum.register_variants(path, {"Slab|Rock_Stone", "Stairs|Rock_Stone"})
    assert added == 1, added
    assert json.loads(path.read_text(encoding="utf-8"))["variants"] == ["Stairs|Rock_Stone", "Slab|Rock_Stone"]
    fresh = tmp / "new" / "variants.json"
    assert domum.register_variants(fresh, {"Slab|Rock_Stone"}) == 1
    assert json.loads(fresh.read_text(encoding="utf-8")) == {"schemaVersion": 1, "variants": ["Slab|Rock_Stone"]}


def run():
    shingle_follows_the_stairs_adapter()
    directed_timber_frame_points_its_pattern()
    floor_trapdoor_keeps_dos_hinge_side()
    ceiling_trapdoor_is_flipped_and_turned_back()
    isolated_fence_post_is_not_called_a_beam()
    every_state_emitted_exists_in_its_template()
    timber_frame_takes_its_two_materials()
    template_mode_keeps_the_default_materials()
    an_old_component_key_takes_the_remaining_entry()
    a_material_without_equivalent_keeps_the_template()
    door_upper_half_is_left_to_the_lower_one()
    pillar_base_takes_its_state()
    a_domum_brick_block_becomes_its_hytale_block()
    every_material_is_accepted_somewhere()
    variant_ids_name_shape_and_materials()
    with tempfile.TemporaryDirectory() as tmp:
        registering_variants_keeps_the_saved_ones(Path(tmp))
    print("blueprint domum check: OK")


if __name__ == "__main__":
    run()
