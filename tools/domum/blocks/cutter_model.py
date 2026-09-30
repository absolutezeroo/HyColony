"""The architect's cutter's own look: Hytale's builder bench (Blocks/Benches/Builder.blockymodel) cleared of its stones
and dressed with miniatures of our own Domum Ornamentum templates: each is the template's generated model scaled down
(positions, offsets and stretch), reading its default-material texture, or the one MINIATURES names, copied at full
resolution into a band added under the bench's texture.

Deviation from MC: DO's cutter has its own model; ours is Hytale's architect bench dressed with DO blocks, so it
reads as Hytale's bench and as DO's at once."""

import itertools
import json
import math

from PIL import Image

import icon
import iconmap
from blocks import common
from flower_pots import check_uvs
from models import scaled, shift_uvs, walk, xyz

MODEL = common.MODELS + "ArchitectsCutter.blockymodel"
TEXTURE = common.MODELS + "ArchitectsCutter_Texture.png"
ICON = common.ICONS + "HyDomum_ArchitectsCutter.png"
# The vanilla stones cleared off the tabletop, by their id in Builder.blockymodel, with their size and position to
# catch a renumbered or changed model (the saw's guard, kept, has the lying slab's size): the lying slab (and the
# pieces it carries) and the slab under the saw (and its cube).
REMOVED = {"10": ((13, 15, 6), (-17, 7, 11)), "15": ((13, 6, 6), (-17, -2, -4.49951))}
# The miniatures, as (template, centre of its base in the tabletop's space, turn about y in degrees, scale, its
# texture or None for the template's default one); the tabletop's top face is at y = 3 and a template spans 32 units.
# The pillar is in chalk: DO's default stone brick melts into Hytale's green tabletop.
MINIATURES = [
    ("HyDomum_TimberFrame_DoubleCrossed", (-8, 3, -9), 0, 0.25, None),
    ("HyDomum_TimberFrame_Framed", (21, 3, -7.5), -15, 0.375, None),
    ("HyDomum_Shingle", (21, 15, -7.5), -15, 0.375, None),
    ("HyDomum_Pillar_Round", (-22, 3, 8), 0, 0.4, "BlockTextures/Rock_Chalk_Brick_Smooth.png"),
    ("HyDomum_Post_Turned", (-12, 3, 9), 0, 0.4, None),
]


def write(ctx, bench):
    """Writes the cutter's model, texture and icon, drawn from the vanilla bench item's model with bench's icon
    camera and the templates ctx already generated; fails when the vanilla model lacks a removed node or its
    tabletop, when one moved or changed size, when a template's face reads outside its texture, or when the
    textures overflow the band."""
    block = bench["BlockType"]
    model = ctx.assets.json("Common/" + block["CustomModel"])
    base = ctx.assets.image("Common/" + block["CustomModelTexture"][0]["Texture"])
    atlas = _Atlas(base)
    seen = set()
    _clear(model["nodes"], seen)
    assert seen == REMOVED.keys(), ("builder bench changed: nodes missing", REMOVED.keys() - seen)
    tabletop = next((node for node in walk(model["nodes"]) if node["name"] == "Tabletop"), None)
    assert tabletop, "builder bench changed: no Tabletop node"
    ids = itertools.count(max(int(node["id"]) for node in walk(model["nodes"])) + 1)
    for entry in MINIATURES:
        tabletop["children"].append(_miniature(ctx, entry, atlas, ids))
    (ctx.pack / "Common" / common.MODELS).mkdir(parents=True, exist_ok=True)
    (ctx.pack / "Common" / MODEL).write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8", newline="\n")
    atlas.texture.save(ctx.pack / "Common" / TEXTURE)
    (ctx.pack / "Common" / common.ICONS).mkdir(parents=True, exist_ok=True)
    icon_map = iconmap.render(model, atlas.texture.size, bench["IconProperties"])
    icon.from_map(icon_map, atlas.texture).save(ctx.pack / "Common" / ICON)


def _clear(nodes, seen):
    """Drops the removed nodes (with their children), checking their vanilla size and position; adds their ids to
    seen."""
    for node in nodes:
        expected = REMOVED.get(node["id"])
        if expected:
            size, at = node["shape"]["settings"]["size"], node["position"]
            found = ((size["x"], size["y"], size["z"]), (at["x"], at["y"], at["z"]))
            assert found == expected, ("builder bench changed", node["id"], found)
            seen.add(node["id"])
    nodes[:] = [node for node in nodes if node["id"] not in REMOVED]
    for node in nodes:
        _clear(node.get("children", []), seen)


def _texture(ctx, ident, path):
    """The texture at path, else the template's default-material one: the pack's own (pair textures), else the
    vanilla one."""
    path = path or ctx.items[ident]["BlockType"]["CustomModelTexture"][0]["Texture"]
    shipped = ctx.pack / "Common" / path
    return Image.open(shipped).convert("RGBA") if shipped.exists() else ctx.assets.image("Common/" + path)


def _miniature(ctx, entry, atlas, ids):
    """A node holding a copy of the entry's template scaled down, turned about y and standing at its base centre,
    reading the entry's texture placed in atlas; every node takes an id from ids."""
    ident, base_centre, turn, scale, path = entry
    image = _texture(ctx, ident, path)
    nodes = ctx.models[ident]["nodes"]
    check_uvs(ident, nodes, image.size)
    holder = scaled(nodes, scale, 0)
    shift_uvs(holder["children"], *atlas.place(image))
    half = math.radians(turn) / 2
    holder.update(name="Mini_" + ident.removeprefix("HyDomum_"), position=xyz(base_centre),
                  orientation={"x": 0, "y": math.sin(half), "z": 0, "w": math.cos(half)})
    for node in walk([holder]):
        node["id"] = str(next(ids))
    return holder


class _Atlas:
    """The vanilla bench texture over a band as high, where the templates' textures are copied once each, left to
    right, row by row."""

    def __init__(self, base):
        self.texture = Image.new("RGBA", (base.width, base.height * 2), (0, 0, 0, 0))
        self.texture.paste(base, (0, 0))
        self.x, self.y, self.row = 0, base.height, 0
        self.placed = {}

    def place(self, image):
        """Where image lies in the atlas: copied there on its first request."""
        key = image.tobytes()
        if key not in self.placed:
            assert image.width <= self.texture.width, "cutter texture narrower than a template's"
            if self.x + image.width > self.texture.width:
                self.x, self.y, self.row = 0, self.y + self.row, 0
            assert self.y + image.height <= self.texture.height, "cutter texture band is full"
            self.texture.paste(image, (self.x, self.y))
            self.placed[key] = (self.x, self.y)
            self.x += image.width
            self.row = max(self.row, image.height)
        return self.placed[key]
