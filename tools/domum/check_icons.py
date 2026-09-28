"""Checks of the Domum Ornamentum icon maps and pack cross-checks (run by check.py)."""

import copy
import dataclasses

from PIL import Image

import icon
import iconmap
import validate
from blocks.common import DEFAULT_ICON, ICON_MAPS
from check_pack import generate_into_temp
from models import empty_shape, node

SHADES = {round(shade * 255) for shade in (1.0, 0.85, 0.7, 0.5)}


def pixels(image):
    """Every pixel of image, row by row."""
    return [image.getpixel((x, y)) for y in range(image.size[1]) for x in range(image.size[0])]


def icon_map_stays_inside_its_layout():
    """Every shape has an icon map; each covered pixel codes a texel of its layout and a known shade."""
    ctx = generate_into_temp()
    for shape in ctx.shapes:
        icon_map = Image.open(ctx.resources / ICON_MAPS / (shape["id"] + ".png"))
        assert icon_map.size == (64, 64) and icon_map.mode == "RGBA", shape["id"]
        covered = [p for p in pixels(icon_map) if p[3]]
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
    both = sum(1 for a, b in zip(pixels(ours), pixels(theirs)) if a and b)
    either = sum(1 for a, b in zip(pixels(ours), pixels(theirs)) if a or b)
    assert both / either > 0.9, both / either


QUADRANTS = {(0, 0): (255, 0, 0, 255), (1, 0): (0, 255, 0, 255), (0, 1): (0, 0, 255, 255), (1, 1): (255, 255, 0, 255)}


def faces_read_their_texture_the_right_way_up():
    """A cube reading a four-colour texture: on its front face (+Z) and its top face the corner of the texture each
    point reads is the one the face's layout rule puts there (texture x along +X, y down the front, y toward +Z on
    the top)."""
    layout = Image.new("RGBA", (32, 32))
    for (qx, qy), colour in QUADRANTS.items():
        layout.paste(colour, (qx * 16, qy * 16, qx * 16 + 16, qy * 16 + 16))
    shape = empty_shape()
    shape.update({"type": "box", "settings": {"size": {"x": 32, "y": 32, "z": 32}},
                  "textureLayout": {side: {"offset": {"x": 0, "y": 0}, "mirror": {}, "angle": 0}
                                    for side in ("front", "right", "top")}})
    model = {"nodes": [node("Cube", (0, 16, 0), shape)]}
    painted = icon.from_map(iconmap.render(model, (32, 32), DEFAULT_ICON), layout)
    # (world point, texture quadrant it must read)
    for point, quadrant in (((-10, 26, 16), (0, 0)), ((10, 6, 16), (1, 1)), ((-10, 32, -10), (0, 0)),
                            ((10, 32, 10), (1, 1)), ((10, 32, -10), (1, 0))):
        x, y = screen(point)
        colour = painted.getpixel((x, y))
        expected = QUADRANTS[quadrant]
        assert max(colour[:3]) and all((c > 0) == (e > 0) for c, e in zip(colour[:3], expected[:3])), (
            point, colour, expected)


def screen(point):
    """The icon pixel of a world point with the default camera (no recentring: a cube fits)."""
    p = iconmap._apply(iconmap._view(DEFAULT_ICON["Rotation"]), point)
    scale = iconmap.PIXELS_PER_UNIT * DEFAULT_ICON["Scale"]
    shift = DEFAULT_ICON["Translation"]
    return int(32 + (p[0] + shift[0]) * scale), int(32 - (p[1] + shift[1]) * scale)


def rejected(ctx, change):
    """The message validate.pack fails with once change(items) altered a copy of ctx's items; None if it passes."""
    items = copy.deepcopy(ctx.items)
    change(items)
    try:
        validate.pack(dataclasses.replace(ctx, items=items))
    except AssertionError as error:
        return str(error)
    return None


def validation_rejects_broken_templates():
    """A foreign id, a missing state, a missing own hitbox, an untranslated name or a missing template each fail."""
    ctx = generate_into_temp()
    door = "HyColony_DO_Door_Full"
    slab = "HyColony_DO_ShingleSlab"

    def foreign(items):
        rules = items[door]["BlockType"]["ConnectedBlockRuleSet"]
        rules["TemplateShapeBlockPatterns"]["Default"] = "Furniture_Crude_Door"

    def state(items):
        del items[slab]["BlockType"]["State"]["Definitions"]["Curved"]

    def hitbox(items):
        items["HyColony_DO_Panel_Full"]["BlockType"]["HitboxType"] = "HyColony_DO_Nothing"

    def untranslated(items):
        items[door]["TranslationProperties"]["Name"] = "hycolony.item.do.nothing.name"

    def template(items):
        items[slab]["BlockType"]["ConnectedBlockRuleSet"]["TemplateShapeAssetId"] = "HyColony_DO_Nothing"

    for change, word in ((foreign, "names"), (state, "Curved"), (hitbox, "missing hitbox"),
                         (untranslated, "untranslated"), (template, "template")):
        message = rejected(ctx, change)
        assert message and word in message, (change.__name__, message)
    assert rejected(ctx, lambda items: None) is None


def run():
    """Runs this module's checks; an AssertionError names the failing case."""
    icon_map_stays_inside_its_layout()
    icon_reads_the_coded_texel()
    cube_icon_matches_the_vanilla_camera()
    faces_read_their_texture_the_right_way_up()
    validation_rejects_broken_templates()
