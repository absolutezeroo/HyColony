"""Domum Ornamentum's material tags transposed to Hytale blocks: which vanilla blocks a DO material slot accepts,
and each material's texture. Written into the DO pack's id-map fragment (ornamentTags) for the runtime.

DO builds its slot tags as unions of block groups (DO-gen: data/domum_ornamentum/tags/blocks/*.json, pinned
source.COMMIT): #domum_ornamentum:default (91 building blocks: stones, bricks, terracotta, concrete, wool...),
#minecraft:planks, #forge:stone, #forge:sandstone, #minecraft:dirt, #minecraft:leaves... The same groups are
defined here over Hytale's own full-cube blocks, and each tag is the same union as DO's.
"""

import os
import re
import sys
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1] / "decorations"))
from pack import ROOT, Assets  # noqa: E402

GRADLE_ASSETS = Path.home() / ".gradle" / "caches" / "hytale-assets" / "release-0.6.8-Assets.zip"

# Hytale block groups standing for DO's tag groups, as regular expressions over vanilla item ids.
GROUPS = {
    "planks": r"Wood_[A-Za-z]+_Planks",
    "stone": r"Rock_(?!Crystal|Ice|Bedrock)[A-Za-z]+(_Cobble)?",
    "sandstone": r"Rock_Sandstone[A-Za-z_]*",
    "dirt": r"Soil_(Dirt|Grass)(_[A-Za-z]+)*",
    "leaves": r"Soil_Leaves(_Full)?",
    "clay": r"Soil_Clay",
    # #domum_ornamentum:default: worked building blocks of every kind.
    "default": r"Rock_(?!Crystal|Ice|Bedrock)[A-Za-z_]+|Soil_Clay[A-Za-z_0-9]*|Cloth_Block_Wool_[A-Za-z_]+"
               r"|Metal_[A-Za-z_]+|Wood_[A-Za-z]+_(Decorative|Ornate)",
}

# DO tag -> the groups whose union it is (DO-gen tags/blocks/<tag>.json). Kept as DO lists them even where a group
# adds nothing today ("stone" and "sandstone" fall inside "default" for 0.6.8's blocks).
TAG_GROUPS = {
    "timber_frames_frame": ("default", "planks", "stone"),
    "timber_frames_center": ("default", "planks", "stone", "sandstone", "dirt"),
    "shingles_roof": ("default", "planks", "dirt", "leaves", "clay"),
    "shingles_support": ("default", "planks"),
    "paper_wall_frame": ("default", "planks"),
    "paper_wall_center": ("default", "planks", "stone"),
    "pillar_materials": ("default", "planks"),
    "post_materials": ("default", "planks"),
    "trapdoors_materials": ("default", "planks"),
    "doors_materials": ("default", "planks"),
    "fancy_doors_materials": ("default", "planks"),
    "fancy_trapdoors_materials": ("default", "planks"),
    # Deviation from MC: DO's fence tags hold #domum_ornamentum:default only, which lacks the fence's own default
    # material (oak planks, FenceBlock.COMPONENTS); planks are added so the default fence is valid.
    "fence_materials": ("default", "planks"),
    "fence_gate_materials": ("default", "planks"),
    "wall_materials": ("default", "stone", "sandstone"),
    "stairs_materials": ("default", "stone", "sandstone"),
    "slab_materials": ("default", "stone", "sandstone"),
    "all_brick_materials": ("default", "stone", "sandstone"),
}

# DO component placeholder texture -> the Hytale block standing for DO's default material in that slot.
DEFAULT_MATERIALS = {
    "block/oak_planks": "Wood_Hardwood_Planks",
    "block/dark_oak_planks": "Wood_Darkwood_Planks",
    "block/acacia_planks": "Wood_Redwood_Planks",
    "block/clay": "Soil_Clay",
}


def open_assets(path=None):
    """The vanilla assets zip: path, else $HYTALE_ASSETS, else server/Assets.zip, else the Gradle cache copy."""
    for candidate in (path, os.environ.get("HYTALE_ASSETS"), ROOT / "server" / "Assets.zip", GRADLE_ASSETS):
        if candidate and Path(candidate).exists():
            return Assets(candidate)
    raise FileNotFoundError("Hytale 0.6.8 assets zip not found")


def texture(assets, block_id):
    """block_id's side texture (BlockTextures/...png) when it is a full cube without states or connections;
    ValueError otherwise."""
    block_type = assets.item(block_id).get("BlockType") or {}
    textures = block_type.get("Textures")
    if block_type.get("DrawType", "Cube") != "Cube" or not textures:
        raise ValueError(f"{block_id} is not a textured cube")
    if block_type.get("State") or block_type.get("ConnectedBlockRuleSet"):
        raise ValueError(f"{block_id} has states or connections")
    first = textures[0]
    sides = {first.get(side) for side in ("North", "South", "East", "West")} - {None}
    if len(sides) > 1:
        raise ValueError(f"{block_id} has different side textures (a corner or ornate block, not a material)")
    path = sides.pop() if sides else first.get("Sides") or first.get("All")
    if not path or not path.startswith("BlockTextures/"):
        raise ValueError(f"{block_id} has no side texture")
    return path


_CUBES = {}  # assets zip path -> cubes(), which reads every vanilla item (about a second)


def cubes(assets):
    """Every vanilla item id whose block is a plain textured cube, with its side texture (cached per zip)."""
    key = assets.zip.filename
    if key not in _CUBES:
        _CUBES[key] = _find_cubes(assets)
    return _CUBES[key]


def _find_cubes(assets):
    found = {}
    for name in assets.names:
        if name.startswith("Server/Item/Items/") and name.endswith(".json"):
            block_id = name.rsplit("/", 1)[1][:-5]
            try:
                found[block_id] = texture(assets, block_id)
            except (ValueError, KeyError):
                continue
    return found


def build(assets):
    """DO tag -> sorted Hytale block ids, from the vanilla cubes matching each tag's groups."""
    ids = sorted(cubes(assets))
    groups = {name: {i for i in ids if re.fullmatch(pattern, i)} for name, pattern in GROUPS.items()}
    return {tag: sorted(set().union(*(groups[g] for g in names))) for tag, names in TAG_GROUPS.items()}


# Default material of DO's stone-only compat tags, whose models draw with an oak placeholder they do not accept.
STONE_DEFAULT = "Rock_Stone_Brick"
STONE_ONLY_TAGS = ("wall_materials", "stairs_materials", "slab_materials", "all_brick_materials")


def default_material(tag, component, built):
    """The Hytale block standing for DO's default material of a slot: its placeholder texture's block when the
    slot's tag (built by build()) accepts it, STONE_DEFAULT for a stone-only tag; ValueError otherwise (a tag that
    lost its own default is a broken group, not a case to paper over)."""
    block = DEFAULT_MATERIALS[component]
    if block in built[tag]:
        return block
    if tag in STONE_ONLY_TAGS:
        return STONE_DEFAULT
    raise ValueError(f"{tag} does not accept its default {block}")
