"""Checks of the Domum Ornamentum icon maps and pack cross-checks (run by check.py)."""

import dataclasses

from PIL import Image

import icon
import validate
from blocks.common import ICON_MAPS
from check_pack import generate_into_temp

SHADES = {round(shade * 255) for shade in (1.0, 0.85, 0.7, 0.5)}


def icon_map_stays_inside_its_layout():
    """Every shape has an icon map; each covered pixel codes a texel of its layout and a known shade."""
    ctx = generate_into_temp()
    for shape in ctx.shapes:
        icon_map = Image.open(ctx.resources / ICON_MAPS / (shape["id"] + ".png"))
        assert icon_map.size == (64, 64) and icon_map.mode == "RGBA", shape["id"]
        covered = [p for p in icon_map.getdata() if p[3]]
        assert covered, shape["id"]
        assert all(p[3] == 255 and p[2] in SHADES for p in covered), shape["id"]


def icon_reads_the_coded_texel():
    """A map pixel coding u = 32 of a 64 wide layout reads the second tile, darkened by its shade."""
    layout = Image.new("RGBA", (64, 32), (10, 20, 30, 255))
    layout.paste((200, 100, 50, 255), (32, 0, 64, 32))
    icon_map = Image.new("RGBA", (2, 1), (0, 0, 0, 0))
    icon_map.putpixel((0, 0), (128, 0, 255, 255))
    icon_map.putpixel((1, 0), (127, 0, 128, 255))
    painted = icon.from_map(icon_map, layout)
    assert painted.getpixel((0, 0)) == (200, 100, 50, 255)
    assert painted.getpixel((1, 0)) == (5, 10, 15, 255)


def cube_icon_matches_the_vanilla_camera():
    """A full block's icon covers the same pixels as a vanilla block icon (same camera and framing)."""
    ctx = generate_into_temp()
    ours = Image.open(ctx.pack / "Common" / ctx.items["HyColony_DO_TimberFrame_Plain"]["Icon"]).getchannel("A")
    theirs = ctx.assets.image("Common/" + ctx.assets.item("Wood_Softwood_Planks")["Icon"]).getchannel("A")
    both = sum(1 for a, b in zip(ours.getdata(), theirs.getdata()) if a and b)
    either = sum(1 for a, b in zip(ours.getdata(), theirs.getdata()) if a or b)
    assert both / either > 0.9, both / either


def validation_rejects_a_foreign_id_in_a_template():
    ctx = generate_into_temp()
    items = {k: dict(v) for k, v in ctx.items.items()}
    door = items["HyColony_DO_Door_Full"]
    foreign = {"Default": "Furniture_Crude_Door"}
    rules = dict(door["BlockType"]["ConnectedBlockRuleSet"], TemplateShapeBlockPatterns=foreign)
    door["BlockType"] = dict(door["BlockType"], ConnectedBlockRuleSet=rules)
    try:
        validate.pack(dataclasses.replace(ctx, items=items))
    except AssertionError:
        return
    raise AssertionError("a foreign block id in a template passed validation")


def run():
    """Runs this module's checks; an AssertionError names the failing case."""
    icon_map_stays_inside_its_layout()
    icon_reads_the_coded_texel()
    cube_icon_matches_the_vanilla_camera()
    validation_rejects_a_foreign_id_in_a_template()
