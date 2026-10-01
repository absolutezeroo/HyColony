"""The vanilla fences, walls and bars our fences and walls join (domum/plugin HytaleNeighbours), as Minecraft's
families (domum/core Joiner): wooden fences (Wood_*_Fence, MC WOODEN_FENCES), other fences (MC nether brick fences),
walls (MC WALLS) and bars (MC IronBarsBlock). Written as exact id lists into the id-map ("connections"): every block
of the vanilla fence template, read from the assets, never an id pattern (a torch on a wall is no wall).

The vanilla blocks keep their vanilla rules (Hytalor patches of them were tried and dropped, 2026-10-01: the lone
posts and ends cut from their models did not look right).
"""

import re

VANILLA_TEMPLATE = "WallConnectedBlockTemplate"
# The id-map keys: domum/core NeighbourKind names (HytaleNeighbours warns on any other).
FAMILIES = ("WOODEN_FENCE", "FENCE", "WALL", "PANE")


def connections(assets):
    """NeighbourKind name -> the sorted vanilla block ids of that family: each straight block and the plain blocks
    its shapes name (a corner block of bars), gates aside."""
    result = {family: set() for family in FAMILIES}
    for block_id, path in blocks(assets).items():
        family = joins(block_id)
        patterns = assets.json(path)["BlockType"]["ConnectedBlockRuleSet"]["TemplateShapeBlockPatterns"]
        result[family].add(block_id)
        targets = (t for shape, pattern in patterns.items() if shape != "Gate" for t in pattern_blocks(pattern))
        result[family].update(t for t in targets if not t.startswith("*"))
    return {family: sorted(ids) for family, ids in result.items()}


def pattern_blocks(pattern):
    """The block ids a pattern names: one or several ("A,B"), each maybe weighted ("50%A", BlockPattern)."""
    return [target.rsplit("%", 1)[-1] for target in pattern.split(",")]


def blocks(assets):
    """Straight block id -> item path of the vanilla items declaring the vanilla fence template as their own straight
    run (a gate names no Straight; a corner block names its straight one's)."""
    result = {}
    for name in assets.names:
        if not (name.startswith("Server/Item/Items/") and name.endswith(".json")):
            continue
        block_id = name.rsplit("/", 1)[1][:-5]
        rules = (assets.json(name).get("BlockType") or {}).get("ConnectedBlockRuleSet") or {}
        straight = rules.get("TemplateShapeBlockPatterns", {}).get("Straight")
        if rules.get("TemplateShapeAssetId") == VANILLA_TEMPLATE and straight == block_id:
            result[block_id] = name
    return result


def joins(block_id):
    """The Minecraft family (NeighbourKind name) a vanilla block joins as; ValueError for an id of no known
    family."""
    if re.fullmatch(r"Wood_[A-Za-z]+_Fence", block_id):
        return "WOODEN_FENCE"
    for suffix, family in (("_Fence", "FENCE"), ("_Wall", "WALL"), ("_Bars", "PANE")):
        if block_id.endswith(suffix):
            return family
    raise ValueError(f"{block_id}: not a fence, wall or bars")
