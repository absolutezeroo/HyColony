"""Generated pack checks of the Domum Ornamentum generator (run by check.py): the templates, the manifest and the
creative tab of a full generation into a temporary folder."""

import json
import tempfile
from pathlib import Path

import generate
import tags

_RUN = {}


def generate_into_temp():
    """One full generation into a temporary pack and resources folder, shared by every check of the run."""
    if "ctx" not in _RUN:
        tmp = Path(tempfile.mkdtemp(prefix="domum-check-"))
        (tmp / "pack").mkdir()
        _RUN["ctx"] = generate.run(tmp / "pack", tmp / "resources", tags.open_assets())
    return _RUN["ctx"]


def manifest_lists_every_template_with_its_slots():
    ctx = generate_into_temp()
    manifest = json.loads((ctx.resources / "hycolony/ornament/shapes.json").read_text(encoding="utf-8"))
    assert manifest["schemaVersion"] == 1 and manifest["shapes"]
    for shape in manifest["shapes"]:
        assert shape["template"] in ctx.items, shape
        assert shape["slots"] and all(tag in tags.TAG_GROUPS for tag in shape["slots"]), shape


def static_templates_live_in_the_do_tab():
    """Every template sits in the DO tab only; an open shape (a panel) has its own hitbox and is Transparent, a full
    block (a timber frame) keeps Hytale's full box and is Solid."""
    ctx = generate_into_temp()
    assert all(item["Categories"][0].startswith("DomumOrnamentum.") for item in ctx.items.values())
    panel = ctx.items["HyColony_DO_Panel_Full"]["BlockType"]
    assert panel["HitboxType"] == "HyColony_DO_Panel_Full" and panel["Opacity"] == "Transparent"
    frame = ctx.items["HyColony_DO_TimberFrame_Plain"]["BlockType"]
    assert "HitboxType" not in frame and frame["Opacity"] == "Solid", frame
    assert len([i for i in ctx.items if i.startswith("HyColony_DO_TimberFrame_")]) == 10


def two_material_templates_read_their_default_pair():
    ctx = generate_into_temp()
    texture = ctx.items["HyColony_DO_TimberFrame_Framed"]["BlockType"]["CustomModelTexture"][0]["Texture"]
    assert texture == "Blocks/HyColony/DO/Pairs/Wood_Hardwood_Planks__Wood_Darkwood_Planks.png", texture
    assert (ctx.pack / "Common" / texture).exists()


def every_category_has_both_icons():
    ctx = generate_into_temp()
    assert ctx.tab["Children"]
    for child in ctx.tab["Children"]:
        for suffix in ("", "Active"):
            assert (ctx.pack / "Common" / child["Icon"].replace(".png", suffix + ".png")).exists(), child


def every_name_is_in_both_languages():
    ctx = generate_into_temp()
    keys = {language: {line.split(" = ")[0] for line in lines} for language, lines in ctx.lang.items()}
    assert keys["en-US"] == keys["fr-FR"]
    for item in ctx.items.values():
        assert item["TranslationProperties"]["Name"].removeprefix("hycolony.") in keys["en-US"], item


def run():
    """Runs this module's checks; an AssertionError names the failing case."""
    manifest_lists_every_template_with_its_slots()
    static_templates_live_in_the_do_tab()
    two_material_templates_read_their_default_pair()
    every_category_has_both_icons()
    every_name_is_in_both_languages()
