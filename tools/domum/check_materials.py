"""Material tags and pair textures checks of the Domum Ornamentum generator (run by check.py)."""

import tempfile
from pathlib import Path

import pairs
import tags
from families import FAMILIES
from PIL import Image


def every_tag_lists_real_cubes():
    """Every DO tag gets Hytale blocks, each a plain textured cube (the runtime reads its side texture)."""
    assets = tags.open_assets()
    built = tags.build(assets)
    assert set(built) == set(tags.TAG_GROUPS)
    for tag, blocks in built.items():
        assert blocks, tag
        for block in blocks:
            assert tags.texture(assets, block).startswith("BlockTextures/"), (tag, block)
    assert "Wood_Hardwood_Planks" in built["shingles_support"] and "Soil_Dirt" not in built["shingles_support"]


def every_slot_has_a_tag_and_its_default():
    """Each family's slots name a DO tag, and DO's default material of each component belongs to that tag."""
    built = tags.build(tags.open_assets())
    for family in FAMILIES:
        assert 1 <= len(family.slot_tags) <= 2 and len(family.slot_tags) == len(family.components), family.name
        for tag, component in zip(family.slot_tags, family.components):
            assert tags.default_material(tag, component, built) in built[tag], (family.name, tag, component)


def pair_texture_is_both_materials_side_by_side(tmp):
    assets = tags.open_assets()
    path = pairs.write(tmp, assets, "Wood_Hardwood_Planks", "Rock_Stone_Brick")
    assert path == "Blocks/HyColony/DO/Pairs/Wood_Hardwood_Planks__Rock_Stone_Brick.png"
    image = Image.open(tmp / "Common" / path)
    assert image.size == (64, 32)
    for x0, block in ((0, "Wood_Hardwood_Planks"), (32, "Rock_Stone_Brick")):
        face = assets.image("Common/" + tags.texture(assets, block)).resize((32, 32), Image.NEAREST)
        assert image.getpixel((x0 + 5, 7)) == face.getpixel((5, 7)), block


def run():
    """Runs this module's checks; an AssertionError names the failing case."""
    every_tag_lists_real_cubes()
    every_slot_has_a_tag_and_its_default()
    with tempfile.TemporaryDirectory() as tmp:
        pair_texture_is_both_materials_side_by_side(Path(tmp))
