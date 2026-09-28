"""Cross-checks of a Domum Ornamentum generation (generate.run calls pack() last): what a template names exists and
is ours, every file it needs was written, every name is translated, and no two faces fight."""

import faces
from blocks import common

PREFIX = "HyColony_DO_"
STATE = "_State_Definitions_"


def pack(ctx):
    """Raises AssertionError naming the first broken template, file, translation or overlapping face pair."""
    keys = {language: {line.split(" = ")[0] for line in lines} for language, lines in ctx.lang.items()}
    for ident, item in ctx.items.items():
        block_type = item["BlockType"]
        _patterns(ctx, ident, block_type.get("ConnectedBlockRuleSet", {}))
        _rule_states(ident, block_type)
        _files(ctx, ident, item)
        name = item["TranslationProperties"]["Name"].removeprefix("hycolony.")
        assert all(name in known for known in keys.values()), (ident, "untranslated", name)
    for child in ctx.tab.get("Children", []):
        for suffix in ("", "Active"):
            assert (ctx.pack / "Common" / child["Icon"].replace(".png", suffix + ".png")).exists(), child
    for label, model in ctx.sources:
        assert not faces.overlapping_pairs(model), (label, "overlapping faces")


def _patterns(ctx, ident, rules):
    """Every block a connection template's patterns name is one of our templates, every state one it defines."""
    for shape, targets in rules.get("TemplateShapeBlockPatterns", {}).items():
        for target in targets.split(","):
            block, _, state = target.removeprefix("*").partition(STATE)
            assert block.startswith(PREFIX) and block in ctx.items, (ident, shape, "names", target)
            if state:
                assert state in ctx.items[block]["BlockType"]["State"]["Definitions"], (ident, shape, target)


def _rule_states(ident, block_type):
    """Every state a Roof or Stair rule names ("default" is the block itself) is defined."""
    rules = block_type.get("ConnectedBlockRuleSet", {})
    defined = set(block_type.get("State", {}).get("Definitions", {})) | {"default"}
    for key, value in rules.items():
        options = value.values() if key in ("Regular", "Hollow", "Topper") else [value]
        for option in options:
            if isinstance(option, dict) and "State" in option:
                assert option["State"] in defined, (ident, key, option)


def _files(ctx, ident, item):
    """The icon, every model and texture, and every hitbox of our own the template and its states name exist."""
    block_type = item["BlockType"]
    looks = [block_type] + list(block_type.get("State", {}).get("Definitions", {}).values())
    paths = [item["Icon"]] + [look["CustomModel"] for look in looks if "CustomModel" in look]
    paths += [t["Texture"] for look in looks for t in look.get("CustomModelTexture", [])]
    for path in paths:
        assert (ctx.pack / "Common" / path).exists() or ctx.assets.has("Common/" + path), (ident, "missing", path)
    for look in looks:
        hitbox = look.get("HitboxType", "")
        if hitbox.startswith(PREFIX):
            assert (ctx.pack / common.HITBOXES / (hitbox + ".json")).exists(), (ident, "missing hitbox", hitbox)
