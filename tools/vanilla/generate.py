"""Generates HyVanilla's derived assets from the vanilla assets zip of the pinned Hytale version.

Run once, then commit the outputs (vanilla/plugin/src/main/resources/): the build never runs it. Re-run it after
changing a table below. Needs Python 3.10+ and Pillow.

    python tools/vanilla/generate.py [path/to/Assets.zip]

Writes, for each wool colour, the carpet item and its generated icon, then the flower pots (flower_pots.py):
one item per clay colour with a state per pottable plant, the shared models, one atlas and icon per colour, the
hitbox and the pack's id-map fragment (spec 2026-09-28 carpets and flower pots), then the beds (beds.py, spec
2026-09-29 HyVanilla beds).
"""

import sys
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1] / "common"))
import beds  # noqa: E402
import flower_pots  # noqa: E402
from pack import GRADLE_ASSETS, ICON_SIZE, PACK, Assets, draw_box, save_png, validate_pack, write_json  # noqa: E402
from PIL import Image  # noqa: E402

# The 20 Hytale wool colours: Cloth_Block_Wool_<C>, texture BlockTextures/Cloth_<C>.png.
WOOL_COLOURS = [
    "Black", "Blue", "Blue_Light", "Cyan", "Cyan_Light", "Gray", "Gray_Light", "Green", "Green_Light", "Orange",
    "Orange_Light", "Pink", "Pink_Light", "Purple", "Purple_Light", "Red", "Red_Light", "White", "Yellow",
    "Yellow_Light",
]


def carpets(assets):
    for colour in WOOL_COLOURS:
        item_id = "HyVanilla_Carpet_" + colour
        wool = assets.item("Cloth_Block_Wool_" + colour)
        texture_path = "BlockTextures/Cloth_" + colour + ".png"
        write_json(PACK / "Server/Item/Items/HyVanilla" / (item_id + ".json"), carpet_item(colour, wool, texture_path))
        # The wool's own icon is a cube: a carpet gets its flat rug instead.
        texture = assets.image("Common/" + texture_path)
        icon = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
        faces = {"top": texture, "front": texture.crop((0, 0, 32, 2)), "right": texture.crop((0, 0, 32, 2))}
        draw_box(icon, ((-16, 0, -16), (16, 2, 16)), faces, 1.1, (32, 33))
        save_png(icon, PACK / "Common/Icons/Items/HyVanilla" / ("Carpet_" + colour + ".png"))


def carpet_item(colour, wool, texture_path):
    """A 1/16 wool rug like Minecraft's carpet: on a full face below, 2 wool of its colour give 3."""
    item_id = "HyVanilla_Carpet_" + colour
    block = wool["BlockType"]
    return {
        "TranslationProperties": {"Name": "hyvanilla.item.carpet." + colour.lower() + ".name"},
        "Icon": "Icons/Items/HyVanilla/Carpet_" + colour + ".png",
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
            "CustomModel": "Blocks/HyVanilla/Carpet.blockymodel",
            "CustomModelTexture": [{"Texture": texture_path, "Weight": 1}],
            "HitboxType": "Block_Flat",
            "Gathering": {"Breaking": {"GatherType": "SoftBlocks", "ItemId": item_id}},
            "Support": {"Down": [{"FaceType": "Full"}]},
            "BlockParticleSetId": "Dust",
            "ParticleColor": block["ParticleColor"],
            "BlockSoundSetId": "Cloth",
            "PhysicalMaterialId": "Wool",
            "TextureComputedColor": block["TextureComputedColor"],
        },
        "Tags": {"Type": ["Cloth"]},
        "ItemSoundSetId": "ISS_Items_Cloth",
    }


def main():
    assets = Assets(Path(sys.argv[1]) if len(sys.argv) > 1 else GRADLE_ASSETS)
    carpets(assets)
    flower_pots.generate(assets)
    beds.generate(assets, WOOL_COLOURS)
    validate_pack(assets)


if __name__ == "__main__":
    main()
