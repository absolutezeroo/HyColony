"""Generates the Decorations sub-plugin's derived assets from the vanilla Hytale 0.6.8 assets zip.

Run once, then commit the outputs (plugin/src/subplugins/Decorations/): the build never runs it. Re-run it after
changing a table below. Needs Python 3.10+ and Pillow.

    python tools/decorations/generate.py [path/to/release-0.6.8-Assets.zip]

Writes, for each wool colour, the carpet item and its icon (spec 2026-09-28 carpets and flower pots).
"""

import io
import json
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / "plugin" / "src" / "subplugins" / "Decorations"
DEFAULT_ZIP = Path.home() / ".gradle" / "caches" / "hytale-assets" / "release-0.6.8-Assets.zip"

# The 20 Hytale wool colours: Cloth_Block_Wool_<C>, texture BlockTextures/Cloth_<C>.png.
WOOL_COLOURS = [
    "Black", "Blue", "Blue_Light", "Cyan", "Cyan_Light", "Gray", "Gray_Light", "Green", "Green_Light", "Orange",
    "Orange_Light", "Pink", "Pink_Light", "Purple", "Purple_Light", "Red", "Red_Light", "White", "Yellow",
    "Yellow_Light",
]

ICON_SIZE = 64


class Assets:
    """Read-only view of the vanilla assets zip."""

    def __init__(self, path):
        self.zip = zipfile.ZipFile(path)

    def json(self, name):
        return json.loads(self.zip.read(name).decode("utf-8"))

    def image(self, name):
        return Image.open(io.BytesIO(self.zip.read(name))).convert("RGBA")

    def item(self, item_id):
        """The item JSON with its Parent chain merged (BlockType merged key by key, like the codec's inheritance)."""
        path = next(n for n in self.zip.namelist()
                    if n.startswith("Server/Item/Items/") and n.endswith("/" + item_id + ".json"))
        data = self.json(path)
        parent = data.get("Parent")
        if not parent:
            return data
        merged = self.item(parent)
        for key, value in data.items():
            if key == "BlockType" and isinstance(value, dict):
                merged["BlockType"] = {**merged.get("BlockType", {}), **value}
            else:
                merged[key] = value
        return merged


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def iso(x, y, z, scale, origin):
    """Screen position of a model point (units) in the icon's isometric view, seen from +x +z."""
    return (origin[0] + (x - z) * scale * 0.866, origin[1] + (x + z) * scale * 0.5 - y * scale)


def draw_face(icon, texture, corner, edge_u, edge_v, shade):
    """Draws texture onto the screen parallelogram corner + s*edge_u + t*edge_v (s, t in [0, 1])."""
    w, h = texture.size
    det = edge_u[0] * edge_v[1] - edge_u[1] * edge_v[0]
    if abs(det) < 1e-6:
        return
    # Inverse affine map screen -> texture pixel, as Image.transform expects.
    a, b = edge_v[1] / det * w, -edge_v[0] / det * w
    d, e = -edge_u[1] / det * h, edge_u[0] / det * h
    c = -(a * corner[0] + b * corner[1])
    f = -(d * corner[0] + e * corner[1])
    face = texture.transform(icon.size, Image.AFFINE, (a, b, c, d, e, f), resample=Image.NEAREST)
    if shade != 1.0:
        rgb = face.convert("RGB").point(lambda p: int(p * shade))
        face = Image.merge("RGBA", (*rgb.split(), face.getchannel("A")))
    mask = Image.new("L", icon.size, 0)
    points = [corner, (corner[0] + edge_u[0], corner[1] + edge_u[1]),
              (corner[0] + edge_u[0] + edge_v[0], corner[1] + edge_u[1] + edge_v[1]),
              (corner[0] + edge_v[0], corner[1] + edge_v[1])]
    ImageDraw.Draw(mask).polygon(points, fill=255)
    face.putalpha(ImageChops.multiply(face.getchannel("A"), mask))
    icon.alpha_composite(face)


def draw_box(icon, box, textures, scale, origin):
    """Draws an axis-aligned box ((x0, y0, z0), (x1, y1, z1)) with its +x, +z and top faces textured."""
    (x0, y0, z0), (x1, y1, z1) = box

    def p(x, y, z):
        return iso(x, y, z, scale, origin)

    def edge(a, b):
        return (b[0] - a[0], b[1] - a[1])

    front = p(x0, y1, z1)
    draw_face(icon, textures["front"], front, edge(front, p(x1, y1, z1)), edge(front, p(x0, y0, z1)), 0.8)
    right = p(x1, y1, z1)
    draw_face(icon, textures["right"], right, edge(right, p(x1, y1, z0)), edge(right, p(x1, y0, z1)), 0.65)
    top = p(x0, y1, z0)
    draw_face(icon, textures["top"], top, edge(top, p(x1, y1, z0)), edge(top, p(x0, y1, z1)), 1.0)


def carpets(assets):
    for colour in WOOL_COLOURS:
        item_id = "HyColony_Carpet_" + colour
        wool = assets.item("Cloth_Block_Wool_" + colour)["BlockType"]
        texture_path = "BlockTextures/Cloth_" + colour + ".png"
        write_json(PACK / "Server/Item/Items/HyColony" / (item_id + ".json"), carpet_item(colour, wool, texture_path))
        texture = assets.image("Common/" + texture_path)
        icon = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
        faces = {"top": texture, "front": texture.crop((0, 0, 32, 2)), "right": texture.crop((0, 0, 32, 2))}
        draw_box(icon, ((-16, 0, -16), (16, 2, 16)), faces, 1.1, (32, 33))
        save_png(icon, PACK / "Common/Icons/HyColony" / ("Carpet_" + colour + ".png"))


def carpet_item(colour, wool, texture_path):
    """A 1/16 wool rug like MC's carpet: on a full face below, 2 wool of its colour give 3 (MC recipe)."""
    item_id = "HyColony_Carpet_" + colour
    return {
        "TranslationProperties": {"Name": "hycolony.item.carpet." + colour.lower() + ".name"},
        "Icon": "Icons/HyColony/Carpet_" + colour + ".png",
        "Categories": ["Blocks.Cloth"],
        "Recipe": {
            "Input": [{"ItemId": "Cloth_Block_Wool_" + colour, "Quantity": 2}],
            "OutputQuantity": 3,
            "BenchRequirement": [{"Type": "Crafting", "Id": "Furniture_Bench", "Categories": ["Furniture_Textiles"]}],
        },
        "PlayerAnimationsId": "Block",
        "BlockType": {
            "Material": "Solid",
            "DrawType": "Model",
            "Opacity": "Transparent",
            "CustomModel": "Blocks/HyColony/Carpet.blockymodel",
            "CustomModelTexture": [{"Texture": texture_path, "Weight": 1}],
            "HitboxType": "Block_Flat",
            "Gathering": {"Breaking": {"GatherType": "SoftBlocks", "ItemId": item_id}},
            "Support": {"Down": [{"FaceType": "Full"}]},
            "BlockParticleSetId": "Dust",
            "ParticleColor": wool["ParticleColor"],
            "BlockSoundSetId": "Cloth",
            "PhysicalMaterialId": "Wool",
            "TextureComputedColor": wool["TextureComputedColor"],
        },
        "Tags": {"Type": ["Cloth"]},
        "ItemSoundSetId": "ISS_Items_Cloth",
    }


def save_png(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def main():
    assets = Assets(Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_ZIP)
    carpets(assets)


if __name__ == "__main__":
    main()
